// SPDX-License-Identifier: Apache-2.0

package tv.hospitium.launcher.domain

import tv.hospitium.launcher.protocol.Branding
import java.security.MessageDigest

/** Which background image to show, and how to trust it. Pure, so it is unit-tested. */
object Backgrounds {
    /** The only paths the launcher fetches with its token: its own control plane's media. */
    val MEDIA_PATH = Regex("^/v1/launcher/media/([0-9a-f]{64})$")

    const val DEFAULT_SCRIM = 0.55f
    private const val UHD_WIDTH_PX = 3840

    data class Choice(val path: String, val sha256: String, val scrim: Float)

    /**
     * Picks the 4K rendition only when the display really draws at 4K (most Google TV interfaces
     * draw at 1080p even on a 4K panel, and a 4K bitmap is ~33 MB). Anything that isn't a
     * same-origin media path (an absolute URL included) is ignored.
     */
    fun choose(branding: Map<String, String>, displayWidthPx: Int): Choice? {
        val uhd = branding[Branding.BACKGROUND_URL_2160]?.takeIf { MEDIA_PATH.matches(it) }
        val hd = branding[Branding.BACKGROUND_URL]?.takeIf { MEDIA_PATH.matches(it) }
        val path = (if (displayWidthPx >= UHD_WIDTH_PX) uhd ?: hd else hd ?: uhd) ?: return null
        val sha = MEDIA_PATH.matchEntire(path)!!.groupValues[1]
        val scrim = branding[Branding.BACKGROUND_SCRIM]?.toFloatOrNull()?.takeIf { !it.isNaN() }?.coerceIn(0.3f, 0.8f)
            ?: DEFAULT_SCRIM
        return Choice(path, sha, scrim)
    }

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
