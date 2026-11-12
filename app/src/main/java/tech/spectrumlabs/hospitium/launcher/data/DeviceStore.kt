// SPDX-License-Identifier: Apache-2.0

package tech.spectrumlabs.hospitium.launcher.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tech.spectrumlabs.hospitium.launcher.protocol.LauncherConfig

/** The last good config and the server-minus-local clock offset measured when it arrived. */
@Serializable
data class CachedConfig(
    val config: LauncherConfig,
    @SerialName("offset_millis") val offsetMillis: Long,
)

/**
 * Everything the launcher persists. The device token is the only secret; the rest is a cache that
 * may be lost at any time (the management agent may reinstall the app). Implementations must make
 * [saveDeviceToken] durable before returning: the server hands the token out exactly once.
 */
interface DeviceStore {
    fun serverUrl(): String?
    fun saveServerUrl(url: String)

    fun deviceToken(): String?
    fun saveDeviceToken(token: String)

    fun cachedConfig(): CachedConfig?
    fun saveCachedConfig(cached: CachedConfig)

    /** Forget the pairing and the cache (token revoked). The server address is kept. */
    fun clearPairing()

    /** Forget everything, including the server address. */
    fun clearAll()
}

/** For tests and previews. */
class InMemoryDeviceStore(
    private var url: String? = null,
    private var token: String? = null,
    private var cached: CachedConfig? = null,
) : DeviceStore {
    override fun serverUrl() = url
    override fun saveServerUrl(url: String) { this.url = url }
    override fun deviceToken() = token
    override fun saveDeviceToken(token: String) { this.token = token }
    override fun cachedConfig() = cached
    override fun saveCachedConfig(cached: CachedConfig) { this.cached = cached }
    override fun clearPairing() { token = null; cached = null }
    override fun clearAll() { clearPairing(); url = null }
}
