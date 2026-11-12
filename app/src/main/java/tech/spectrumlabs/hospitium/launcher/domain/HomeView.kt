// SPDX-License-Identifier: Apache-2.0

package tech.spectrumlabs.hospitium.launcher.domain

import tech.spectrumlabs.hospitium.launcher.protocol.Announcement
import tech.spectrumlabs.hospitium.launcher.protocol.GuestInfo
import tech.spectrumlabs.hospitium.launcher.protocol.LauncherConfig
import java.time.Duration
import java.time.Instant

/**
 * Two clocks, because a TV's clock can be wrong. The server's clock arrives with every config
 * ([LauncherConfig.serverTime]); [offset] is how far it was ahead of the local clock at that moment.
 */
data class Clocks(val local: Instant, val offset: Duration) {
    /** Best estimate of the real time: the local clock corrected by the last known offset. */
    val corrected: Instant get() = local.plus(offset)

    /** The LATER of the two clocks. Guest data hides once either clock says it has expired. */
    val conservative: Instant get() = maxOf(local, corrected)

    companion object {
        fun offsetAt(serverTime: Instant, localAtReceipt: Instant): Duration = Duration.between(localAtReceipt, serverTime)
    }
}

/** What the home screen shows right now, derived from the cached config and the clocks only. */
data class HomeView(
    val guest: GuestInfo?,
    val showCheckoutReminder: Boolean,
    val announcements: List<Announcement>,
)

object HomePolicy {
    /**
     * Applies every expiry locally, including while offline (protocol v1: "Guest fields carry their
     * own expiry which the launcher must enforce locally"). The guest disappears at the EARLIEST
     * moment either clock says it expired, so a slow or rolled-back TV clock can't keep a previous
     * guest's name on screen. Reminders use the corrected clock, the best estimate of real time.
     */
    fun view(config: LauncherConfig, clocks: Clocks): HomeView {
        val now = clocks.conservative
        val guest = config.guest?.takeIf { now.isBefore(it.expiresAt) && now.isBefore(it.checkOut) }
        val reminder = guest != null && !clocks.corrected.isBefore(guest.checkoutReminderAt)
        val announcements = config.announcements.filter { a -> a.expiresAt?.let { now.isBefore(it) } ?: true }
        return HomeView(guest, reminder, announcements)
    }
}

/** Retry timing. Pure so it is tested, and bounded so a broken server can't spin a TV. */
object Backoff {
    val MIN_REFRESH: Duration = Duration.ofSeconds(15)
    val MAX_REFRESH: Duration = Duration.ofHours(1)
    val DEFAULT_REFRESH: Duration = Duration.ofMinutes(5)

    /** The server's suggested refresh interval, clamped; a missing or zero value means the default. */
    fun refresh(suggestedSeconds: Int): Duration =
        if (suggestedSeconds <= 0) DEFAULT_REFRESH
        else Duration.ofSeconds(suggestedSeconds.toLong()).coerceIn(MIN_REFRESH, MAX_REFRESH)

    /** Exponential backoff after [failures] consecutive failures: base, 2x base, ... capped at max. */
    fun afterFailures(failures: Int, base: Duration, max: Duration): Duration {
        val shift = (failures - 1).coerceIn(0, 20)
        val d = base.multipliedBy(1L shl shift)
        return if (d > max) max else d
    }
}
