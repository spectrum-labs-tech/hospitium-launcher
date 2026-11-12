// SPDX-License-Identifier: Apache-2.0

package tech.spectrumlabs.hospitium.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.spectrumlabs.hospitium.launcher.domain.Backoff
import tech.spectrumlabs.hospitium.launcher.domain.Clocks
import tech.spectrumlabs.hospitium.launcher.domain.HomePolicy
import tech.spectrumlabs.hospitium.launcher.protocol.Announcement
import java.time.Duration
import java.time.Instant

class HomePolicyTest {
    private val checkOut = T0.plus(Duration.ofDays(2))
    private val cfg = config(guest(checkOut = checkOut))

    private fun at(local: Instant, offset: Duration = Duration.ZERO) = HomePolicy.view(cfg, Clocks(local, offset))

    @Test
    fun `guest shows during the stay`() {
        val v = at(T0)
        assertNotNull(v.guest)
        assertFalse(v.showCheckoutReminder)
    }

    @Test
    fun `guest disappears at expiry, offline, with no refresh`() {
        assertNotNull(at(checkOut.minusSeconds(1)).guest)
        assertNull(at(checkOut).guest)
        assertNull(at(checkOut.plus(Duration.ofDays(30))).guest)
    }

    @Test
    fun `a TV clock running behind can't keep a guest on screen`() {
        // Server time is already past checkout, but the TV clock says two hours earlier.
        val v = at(local = checkOut.minus(Duration.ofHours(2)), offset = Duration.ofHours(3))
        assertNull(v.guest)
    }

    @Test
    fun `a TV clock running ahead hides the guest early rather than late`() {
        // The TV clock is past checkout; the server (corrected) says there's an hour left.
        val v = at(local = checkOut.plusSeconds(60), offset = Duration.ofHours(-1))
        assertNull(v.guest)
    }

    @Test
    fun `checkout reminder starts at its time on the corrected clock`() {
        val reminderAt = checkOut.minus(Duration.ofHours(12))
        assertFalse(at(reminderAt.minusSeconds(1)).showCheckoutReminder)
        assertTrue(at(reminderAt).showCheckoutReminder)
        // A TV 10 minutes slow still shows it on time, thanks to the offset.
        assertTrue(at(reminderAt.minus(Duration.ofMinutes(10)), Duration.ofMinutes(10)).showCheckoutReminder)
    }

    @Test
    fun `expired announcements are dropped, open-ended ones stay`() {
        val c = cfg.copy(
            announcements = listOf(
                Announcement("a", "Old", expiresAt = T0.minusSeconds(1)),
                Announcement("b", "Open"),
                Announcement("c", "Soon", expiresAt = T0.plusSeconds(60)),
            ),
        )
        val v = HomePolicy.view(c, Clocks(T0, Duration.ZERO))
        assertEquals(listOf("b", "c"), v.announcements.map { it.id })
    }

    @Test
    fun `offset is server minus local`() {
        assertEquals(Duration.ofSeconds(90), Clocks.offsetAt(serverTime = T0.plusSeconds(90), localAtReceipt = T0))
    }

    @Test
    fun `refresh interval is clamped`() {
        assertEquals(Backoff.DEFAULT_REFRESH, Backoff.refresh(0))
        assertEquals(Backoff.MIN_REFRESH, Backoff.refresh(1))
        assertEquals(Duration.ofSeconds(300), Backoff.refresh(300))
        assertEquals(Backoff.MAX_REFRESH, Backoff.refresh(1_000_000))
    }

    @Test
    fun `backoff doubles up to the cap`() {
        val base = Duration.ofSeconds(15)
        val max = Duration.ofMinutes(10)
        assertEquals(listOf(15L, 30L, 60L, 120L, 240L, 480L, 600L, 600L), (1..8).map { Backoff.afterFailures(it, base, max).seconds })
        assertEquals(max, Backoff.afterFailures(1_000, base, max))
    }
}
