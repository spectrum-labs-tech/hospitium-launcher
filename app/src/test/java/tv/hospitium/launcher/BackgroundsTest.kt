// SPDX-License-Identifier: Apache-2.0

package tv.hospitium.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.hospitium.launcher.domain.Backgrounds
import tv.hospitium.launcher.protocol.Branding
import java.security.MessageDigest

class BackgroundsTest {
    private val hd = "/v1/launcher/media/" + "a".repeat(64)
    private val uhd = "/v1/launcher/media/" + "b".repeat(64)
    private val both = mapOf(Branding.BACKGROUND_URL to hd, Branding.BACKGROUND_URL_2160 to uhd, Branding.BACKGROUND_SCRIM to "0.62")

    @Test
    fun `1080p interfaces use the 1080p rendition, 4K ones the 4K rendition`() {
        assertEquals(hd, Backgrounds.choose(both, 1920)?.path)
        assertEquals(uhd, Backgrounds.choose(both, 3840)?.path)
        assertEquals(hd, Backgrounds.choose(both - Branding.BACKGROUND_URL_2160, 3840)?.path)
        assertEquals("a".repeat(64), Backgrounds.choose(both, 1920)?.sha256)
        assertEquals(0.62f, Backgrounds.choose(both, 1920)!!.scrim)
    }

    @Test
    fun `only same-origin media paths are ever fetched`() {
        for (bad in listOf(
            "https://evil.test/track.jpg",
            "//evil.test/v1/launcher/media/" + "a".repeat(64),
            "/v1/launcher/config",
            "/v1/launcher/media/" + "A".repeat(64),
            "/v1/launcher/media/../config",
        )) {
            assertNull(bad, Backgrounds.choose(mapOf(Branding.BACKGROUND_URL to bad), 1920))
        }
        assertNull(Backgrounds.choose(emptyMap(), 1920))
    }

    @Test
    fun `scrim is bounded and defaults when missing or garbage`() {
        assertEquals(Backgrounds.DEFAULT_SCRIM, Backgrounds.choose(mapOf(Branding.BACKGROUND_URL to hd), 1920)!!.scrim)
        assertEquals(0.8f, Backgrounds.choose(both + (Branding.BACKGROUND_SCRIM to "9"), 1920)!!.scrim)
        assertEquals(Backgrounds.DEFAULT_SCRIM, Backgrounds.choose(both + (Branding.BACKGROUND_SCRIM to "NaN"), 1920)!!.scrim)
    }

    @Test
    fun `bytes are trusted only if they hash to their address`() {
        val bytes = "a jpeg".toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertTrue(Backgrounds.verified(bytes, sha))
        assertFalse(Backgrounds.verified("tampered".toByteArray(), sha))
    }

    @Test
    fun `decode subsamples to the display, never below it`() {
        assertEquals(2, Backgrounds.sampleSize(3840, 2160, 1920, 1080))
        assertEquals(1, Backgrounds.sampleSize(1920, 1080, 1920, 1080))
        assertEquals(1, Backgrounds.sampleSize(3840, 2160, 3840, 2160))
        assertEquals(1, Backgrounds.sampleSize(3000, 1688, 1920, 1080))
    }
}
