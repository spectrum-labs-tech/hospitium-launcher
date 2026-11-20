// SPDX-License-Identifier: Apache-2.0

package tv.hospitium.launcher.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.hospitium.launcher.domain.Backgrounds
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * Loads a hotlinked background (protocol v0.8.0 origin "unsplash") from the provider's CDN. Unsplash's
 * terms require loading its photos from the URLs it returns, so this is the one request the launcher
 * makes off its control plane. It carries no credentials: no device token, no cookies. It follows
 * no redirects, and refuses any URL [allowed] rejects before a request is made. Nothing is written
 * to disk.
 */
class RemoteImages(
    private val userAgent: String,
    private val timeoutMillis: Int = 15_000,
    /** A 3840x2160 JPEG is a few MB; anything far larger is not a background. */
    private val maxBytes: Int = 16 shl 20,
    private val allowed: (String) -> Boolean = Backgrounds::hotlinkAllowed,
) {
    /** The image's bytes, or null if it's refused, unreachable or too large. */
    suspend fun fetch(url: String): ByteArray? {
        if (!allowed(url)) return null
        return withContext(Dispatchers.IO) {
            val conn = try {
                URI(url).toURL().openConnection() as HttpURLConnection
            } catch (_: Exception) {
                return@withContext null
            }
            try {
                conn.connectTimeout = timeoutMillis
                conn.readTimeout = timeoutMillis
                conn.instanceFollowRedirects = false
                conn.useCaches = false
                conn.setRequestProperty("Accept", "image/jpeg")
                conn.setRequestProperty("User-Agent", userAgent)
                if (conn.responseCode != 200 || conn.contentLengthLong > maxBytes) return@withContext null
                conn.inputStream.use { input ->
                    val bytes = input.readNBytesCompat(maxBytes + 1)
                    bytes.takeIf { it.size <= maxBytes }
                }
            } catch (_: IOException) {
                null
            } finally {
                conn.disconnect()
            }
        }
    }
}

/** Reads at most n bytes (InputStream.readNBytes needs API 33). */
private fun java.io.InputStream.readNBytesCompat(n: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    while (out.size() < n) {
        val r = read(buf, 0, minOf(buf.size, n - out.size()))
        if (r < 0) break
        out.write(buf, 0, r)
    }
    return out.toByteArray()
}
