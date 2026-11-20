// SPDX-License-Identifier: Apache-2.0

package tv.hospitium.launcher.domain

import tv.hospitium.launcher.protocol.Background
import tv.hospitium.launcher.protocol.BackgroundOrigin
import tv.hospitium.launcher.protocol.BackgroundOverlay
import tv.hospitium.launcher.protocol.Branding
import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest

/** Which background image to show, and how to trust it. Pure, so it is unit-tested. */
object Backgrounds {
    /** The only paths the launcher fetches with its token: its own control plane's media. */
    val MEDIA_PATH = Regex("^/v1/launcher/media/([0-9a-f]{64})$")

    /** The only host a hotlinked (Unsplash) background is loaded from, never with credentials. */
    const val UNSPLASH_IMAGE_HOST = "images.unsplash.com"

    const val DEFAULT_SCRIM = 0.55f
    private const val UHD_WIDTH_PX = 3840

    enum class Origin { CONTROL_PLANE, UNSPLASH }
    enum class Overlay { GRADIENT, DIM }

    /**
     * The image to load. [sha256] is the content address of a control-plane image (null for a
     * hotlinked one); [credit] must be shown whenever this image is.
     */
    data class Choice(
        val path: String,
        val origin: Origin,
        val sha256: String?,
        val scrim: Float,
        val overlay: Overlay = Overlay.GRADIENT,
        val credit: String? = null,
    ) {
        /** What identifies the image: its address, or its URL when hotlinked. */
        val key: String get() = sha256 ?: path
    }

    /**
     * The background a config asks for (protocol v0.8.0), or, from a control plane that predates
     * it, the legacy branding keys. A background the protocol's rules refuse is never used.
     */
    fun choose(background: Background?, branding: Map<String, String>, displayWidthPx: Int): Choice? =
        background?.let { fromBackground(it, displayWidthPx) } ?: choose(branding, displayWidthPx)

    /**
     * The legacy branding keys. Picks the 4K rendition only when the display really draws at 4K
     * (most Google TV interfaces draw at 1080p even on a 4K panel, and a 4K bitmap is ~33 MB).
     * Anything that isn't a same-origin media path (an absolute URL included) is ignored.
     */
    fun choose(branding: Map<String, String>, displayWidthPx: Int): Choice? {
        val uhd = branding[Branding.BACKGROUND_URL_2160]?.takeIf { MEDIA_PATH.matches(it) }
        val hd = branding[Branding.BACKGROUND_URL]?.takeIf { MEDIA_PATH.matches(it) }
        val path = (if (displayWidthPx >= UHD_WIDTH_PX) uhd ?: hd else hd ?: uhd) ?: return null
        val sha = MEDIA_PATH.matchEntire(path)!!.groupValues[1]
        val scrim = branding[Branding.BACKGROUND_SCRIM]?.toFloatOrNull()?.let(::boundedScrim) ?: DEFAULT_SCRIM
        return Choice(path, Origin.CONTROL_PLANE, sha, scrim)
    }

    private fun fromBackground(b: Background, displayWidthPx: Int): Choice? {
        val overlay = when (b.overlay) {
            BackgroundOverlay.GRADIENT -> Overlay.GRADIENT
            BackgroundOverlay.DIM -> Overlay.DIM
            else -> return null
        }
        val credit = b.attribution?.text?.trim()
        if (b.attribution != null && credit.isNullOrEmpty()) return null
        val path = (if (displayWidthPx >= UHD_WIDTH_PX) b.url2160.ifEmpty { b.url } else b.url).ifEmpty { return null }
        val scrim = boundedScrim(b.scrim) ?: DEFAULT_SCRIM
        return when (b.origin) {
            BackgroundOrigin.CONTROL_PLANE -> {
                val sha = MEDIA_PATH.matchEntire(path)?.groupValues?.get(1) ?: return null
                Choice(path, Origin.CONTROL_PLANE, sha, scrim, overlay, credit)
            }
            // Unsplash's terms: shown only with its photographer's credit.
            BackgroundOrigin.UNSPLASH -> if (credit != null && hotlinkAllowed(path)) {
                Choice(path, Origin.UNSPLASH, null, scrim, overlay, credit)
            } else {
                null
            }
            else -> null
        }
    }

    /** Whether url is an https URL on [UNSPLASH_IMAGE_HOST]: no other host, port or credentials. */
    fun hotlinkAllowed(url: String): Boolean {
        val u = try {
            URI(url)
        } catch (_: URISyntaxException) {
            return false
        }
        return u.scheme == "https" && u.host == UNSPLASH_IMAGE_HOST && u.port == -1 && u.rawUserInfo == null &&
            u.rawFragment == null && (u.rawPath?.length ?: 0) > 1
    }

    private fun boundedScrim(s: Float): Float? = s.takeIf { !it.isNaN() }?.coerceIn(0.3f, 0.8f)

    /** Media is content-addressed: bytes are trusted only if they hash to their address. */
    fun verified(bytes: ByteArray, sha256: String): Boolean =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } == sha256

    /** The largest power-of-two subsample that still covers the display (BitmapFactory's inSampleSize). */
    fun sampleSize(imageW: Int, imageH: Int, displayW: Int, displayH: Int): Int {
        var s = 1
        while (imageW / (s * 2) >= displayW && imageH / (s * 2) >= displayH) s *= 2
        return s
    }
}
