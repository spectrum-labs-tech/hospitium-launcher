// SPDX-License-Identifier: Apache-2.0

package tech.spectrumlabs.hospitium.launcher.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import tech.spectrumlabs.hospitium.launcher.protocol.LauncherConfig
import tech.spectrumlabs.hospitium.launcher.protocol.PairingStartResponse
import tech.spectrumlabs.hospitium.launcher.protocol.PairingStatusResponse
import tech.spectrumlabs.hospitium.launcher.protocol.Paths
import tech.spectrumlabs.hospitium.launcher.protocol.ProtocolJson
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.time.DateTimeException

/** Why a call failed. Never carries a token or a response body. */
sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** 401: the token was refused (revoked device, or a poll token that no longer matches). */
    class Unauthorized : ApiException("unauthorized")

    /** Any other non-2xx status, e.g. 429 (rate limited) or 503 (control plane degraded). */
    class Http(val status: Int) : ApiException("http $status")

    /** DNS, TLS, timeout, connection refused: the control plane is unreachable from here. */
    class Network(cause: IOException) : ApiException("network: ${cause.javaClass.simpleName}", cause)

    /** A 2xx response that doesn't parse as the v1 contract. */
    class Malformed(cause: Throwable) : ApiException("malformed response", cause)
}

/** The launcher's three calls (hospitium-protocol v1). The device never needs anything else. */
interface LauncherApi {
    suspend fun startPairing(): PairingStartResponse
    suspend fun pairingStatus(code: String, pollToken: String): PairingStatusResponse
    suspend fun config(deviceToken: String): LauncherConfig
}

/**
 * HttpURLConnection on purpose: the platform's own client, no extra dependency, and the
 * app's network security config (HTTPS only, system trust) applies to it.
 */
class LauncherClient(
    private val baseUrl: String,
    private val userAgent: String,
    private val timeoutMillis: Int = 15_000,
) : LauncherApi {

    override suspend fun startPairing(): PairingStartResponse =
        decode(PairingStartResponse.serializer(), call("POST", Paths.PAIRING_START, null))

    override suspend fun pairingStatus(code: String, pollToken: String): PairingStatusResponse {
        val path = Paths.PAIRING_STATUS + URLEncoder.encode(code, "UTF-8")
        return decode(PairingStatusResponse.serializer(), call("GET", path, pollToken))
    }

    override suspend fun config(deviceToken: String): LauncherConfig =
        decode(LauncherConfig.serializer(), call("GET", Paths.CONFIG, deviceToken))

    private fun <T> decode(serializer: KSerializer<T>, body: String): T = try {
        ProtocolJson.decodeFromString(serializer, body)
    } catch (e: IllegalArgumentException) { // SerializationException is one
        throw ApiException.Malformed(e)
    } catch (e: DateTimeException) {
        throw ApiException.Malformed(e)
    }

    private suspend fun call(method: String, path: String, bearer: String?): String = withContext(Dispatchers.IO) {
        val conn = try {
            URI(baseUrl.trimEnd('/') + path).toURL().openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw ApiException.Network(e)
        }
        try {
            conn.requestMethod = method
            conn.connectTimeout = timeoutMillis
            conn.readTimeout = timeoutMillis
            conn.instanceFollowRedirects = false // a redirect could carry the bearer token elsewhere
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", userAgent)
            if (bearer != null) conn.setRequestProperty("Authorization", "Bearer $bearer")
            if (method == "POST") {
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(0)
                conn.outputStream.close()
            }
            val status = conn.responseCode
            when {
                status == 401 -> throw ApiException.Unauthorized()
                status !in 200..299 -> throw ApiException.Http(status)
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            throw ApiException.Network(e)
        } finally {
            conn.disconnect()
        }
    }
}
