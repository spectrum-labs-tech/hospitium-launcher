// SPDX-License-Identifier: Apache-2.0

package tv.hospitium.launcher.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import tv.hospitium.launcher.domain.Backgrounds
import tv.hospitium.launcher.protocol.LauncherConfig
import tv.hospitium.launcher.protocol.PairingStartResponse
import tv.hospitium.launcher.protocol.PairingStatusResponse
import tv.hospitium.launcher.protocol.Paths
import tv.hospitium.launcher.protocol.ProtocolJson
import tv.hospitium.launcher.protocol.UsageReport
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.time.DateTimeException
import java.time.Instant

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

/** The launcher's calls (hospitium-protocol v1). The device never needs anything else. */
interface LauncherApi {
    suspend fun startPairing(): PairingStartResponse
    suspend fun pairingStatus(code: String, pollToken: String): PairingStatusResponse
    suspend fun config(deviceToken: String): LauncherConfig

    /**
     * An image by its media path (Backgrounds.MEDIA_PATH only: anything else is refused before a
     * request is made, so the device token can't be sent to an arbitrary path or host).
     */
    suspend fun media(path: String, deviceToken: String): ByteArray

    /** Anonymous hourly usage counts (protocol v0.6.0). */
    suspend fun usage(deviceToken: String, report: UsageReport)
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

    override suspend fun startPairing(): PairingStartResponse {
        val (body, date) = call("POST", Paths.PAIRING_START, null)
        return decode(PairingStartResponse.serializer(), body).copy(serverDate = date)
    }

    override suspend fun pairingStatus(code: String, pollToken: String): PairingStatusResponse {
        val path = Paths.PAIRING_STATUS + URLEncoder.encode(code, "UTF-8")
        return decode(PairingStatusResponse.serializer(), call("GET", path, pollToken).first)
    }

    override suspend fun config(deviceToken: String): LauncherConfig =
        decode(LauncherConfig.serializer(), call("GET", Paths.CONFIG, deviceToken).first)

    override suspend fun media(path: String, deviceToken: String): ByteArray {
        require(Backgrounds.MEDIA_PATH.matches(path)) { "not a media path" }
        return call("GET", path, deviceToken, accept = "image/jpeg").first
    }

    override suspend fun usage(deviceToken: String, report: UsageReport) {
        val body = ProtocolJson.encodeToString(UsageReport.serializer(), report).toByteArray(Charsets.UTF_8)
        call("POST", Paths.USAGE, deviceToken, body = body)
    }

    private fun <T> decode(serializer: KSerializer<T>, bytes: ByteArray): T = try {
        if (bytes.isEmpty()) throw IllegalArgumentException("empty body")
        ProtocolJson.decodeFromString(serializer, String(bytes, Charsets.UTF_8))
    } catch (e: IllegalArgumentException) { // SerializationException is one
        throw ApiException.Malformed(e)
    } catch (e: DateTimeException) {
        throw ApiException.Malformed(e)
    }

    /** The body and the server's Date header (null if absent). */
    private suspend fun call(
        method: String,
        path: String,
        bearer: String?,
        accept: String = "application/json",
        body: ByteArray? = null,
    ): Pair<ByteArray, Instant?> = withContext(Dispatchers.IO) {
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
            conn.setRequestProperty("Accept", accept)
            conn.setRequestProperty("User-Agent", userAgent)
            if (bearer != null) conn.setRequestProperty("Authorization", "Bearer $bearer")
            if (method == "POST") {
                val bytes = body ?: ByteArray(0)
                conn.doOutput = true
                if (body != null) conn.setRequestProperty("Content-Type", "application/json")
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
            }
            val status = conn.responseCode
            when {
                status == 401 -> throw ApiException.Unauthorized()
                status !in 200..299 -> throw ApiException.Http(status)
            }
            val body = conn.inputStream.use { it.readBytes() }
            body to conn.date.takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        } catch (e: IOException) {
            throw ApiException.Network(e)
        } finally {
            conn.disconnect()
        }
    }
}
