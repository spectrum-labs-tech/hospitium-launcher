// SPDX-License-Identifier: Apache-2.0

package tv.hospitium.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.hospitium.launcher.domain.Backgrounds
import tv.hospitium.launcher.protocol.Background
import tv.hospitium.launcher.protocol.BackgroundAttribution
import tv.hospitium.launcher.protocol.BackgroundOrigin
import tv.hospitium.launcher.protocol.BackgroundOverlay
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

    // Protocol v0.8.0: LauncherConfig.background, preferred over the legacy branding keys.
    private val unsplashHd = "https://images.unsplash.com/photo-1?ixid=M3w&w=1920&h=1080&fit=crop"
    private val unsplashUhd = "https://images.unsplash.com/photo-1?ixid=M3w&w=3840&h=2160&fit=crop"
    private val credit = BackgroundAttribution("Photo by Ana Ruiz on Unsplash")
    private val photo = Background(url = unsplashHd, url2160 = unsplashUhd, origin = BackgroundOrigin.UNSPLASH, scrim = 0.6f, attribution = credit)

    @Test
    fun `an Unsplash photo is hotlinked at the display's size, with its credit`() {
        val c = Backgrounds.choose(photo, emptyMap(), 1920)!!
        assertEquals(Backgrounds.Origin.UNSPLASH, c.origin)
        assertEquals(unsplashHd, c.path)
        assertNull("a hotlinked photo has no content address", c.sha256)
        assertEquals(unsplashHd, c.key)
        assertEquals("Photo by Ana Ruiz on Unsplash", c.credit)
        assertEquals(0.6f, c.scrim)
        assertEquals(unsplashUhd, Backgrounds.choose(photo, emptyMap(), 3840)!!.path)
        assertEquals(unsplashHd, Backgrounds.choose(photo.copy(url2160 = ""), emptyMap(), 3840)!!.path)
    }

    @Test
    fun `an Unsplash photo without its credit, or off its CDN, is never shown`() {
        assertNull(Backgrounds.choose(photo.copy(attribution = null), emptyMap(), 1920))
        assertNull(Backgrounds.choose(photo.copy(attribution = BackgroundAttribution(" ")), emptyMap(), 1920))
        for (bad in listOf(
            "http://images.unsplash.com/photo-1",
            "https://images.unsplash.com.evil.test/photo-1",
            "https://evil.test/photo-1",
            "https://user@images.unsplash.com/photo-1",
            "https://images.unsplash.com:8443/photo-1",
            "https://images.unsplash.com/",
            "https://images.unsplash.com/photo-1#frag",
            hd,
        )) {
            assertNull(bad, Backgrounds.choose(photo.copy(url = bad, url2160 = ""), emptyMap(), 1920))
            assertFalse(bad, Backgrounds.hotlinkAllowed(bad))
        }
    }

    @Test
    fun `the new field wins over the legacy keys, and an unusable one falls back to them`() {
        val stored = Background(url = uhd, origin = BackgroundOrigin.CONTROL_PLANE, overlay = BackgroundOverlay.DIM, scrim = 0.4f)
        val c = Backgrounds.choose(stored, both, 1920)!!
        assertEquals(uhd, c.path)
        assertEquals("b".repeat(64), c.sha256)
        assertEquals(Backgrounds.Overlay.DIM, c.overlay)
        assertNull(c.credit)
        // An unknown origin or overlay, or a control-plane URL that isn't a media path: legacy keys.
        for (bad in listOf(stored.copy(origin = "pexels"), stored.copy(overlay = "blur"), stored.copy(url = "https://evil.test/x.jpg"))) {
            assertEquals(hd, Backgrounds.choose(bad, both, 1920)?.path)
        }
        assertNull(Backgrounds.choose(stored.copy(origin = "pexels"), emptyMap(), 1920))
        assertEquals(hd, Backgrounds.choose(null, both, 1920)?.path)
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
