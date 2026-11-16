// SPDX-License-Identifier: Apache-2.0

package tech.spectrumlabs.hospitium.launcher.domain

import kotlinx.serialization.Serializable
import tech.spectrumlabs.hospitium.launcher.data.DeviceStore
import tech.spectrumlabs.hospitium.launcher.protocol.UsageHour
import tech.spectrumlabs.hospitium.launcher.protocol.UsageReport
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Guestbook usage counters (hospitium-protocol v0.6.0). The launcher counts which screens are opened
 * and which apps are started, per hour, and nothing else: no text, no finer times, nothing about the
 * guest.
 */
object UsageCounters {
    const val HOME = "home"
    const val WIFI = "wifi"
    const val HOUSE_RULES = "house_rules"
    const val AMENITIES = "amenities"

    fun app(packageName: String) = "app:$packageName"

    /** The counter for a home-screen card, by its key in HomeScreen (null for a card we don't count). */
    fun forCard(key: String): String? = when {
        key == "wifi" -> WIFI
        key == "rules" -> HOUSE_RULES
        key == "amenities" -> AMENITIES
        key.startsWith("a-") -> key.removePrefix("a-").toLongOrNull()?.takeIf { it > 0 }?.let { "announcement:$it" }
        else -> null
    }
}

/**
 * Running totals per UTC hour, keyed by the hour's start in epoch seconds. Immutable, so it is
 * tested without Android. Each report carries an hour's total so far; the server keeps the larger
 * value, so sending an hour again never counts twice (protocol v0.6.0).
 */
@Serializable
data class UsageCounts(val hours: Map<Long, Map<String, Int>> = emptyMap()) {
    /** One more of [counter] in the hour containing [at]. */
    fun count(counter: String, at: Instant): UsageCounts {
        val hour = at.truncatedTo(ChronoUnit.HOURS).epochSecond
        val current = hours[hour].orEmpty()
        if (counter !in current && current.size >= MAX_COUNTERS) return pruned(at)
        val n = minOf((current[counter] ?: 0) + 1, MAX_COUNT)
        return UsageCounts(hours + (hour to current + (counter to n))).pruned(at)
    }

    /** Drops hours the server would no longer accept. */
    fun pruned(now: Instant): UsageCounts {
        val oldest = now.minus(MAX_AGE).epochSecond
        return if (hours.keys.all { it >= oldest }) this else UsageCounts(hours.filterKeys { it >= oldest })
    }

    /** What to send, newest hours first if there are too many; null when there is nothing. */
    fun report(): UsageReport? {
        if (hours.isEmpty()) return null
        val keep = hours.keys.sortedDescending().take(MAX_HOURS).sorted()
        return UsageReport(keep.map { UsageHour(Instant.ofEpochSecond(it), hours.getValue(it)) })
    }

    /**
     * After [sent] was accepted: forgets each hour that is over and unchanged since it was sent.
     * The current hour, or one counted again meanwhile, stays for the next report.
     */
    fun acknowledged(sent: UsageReport, now: Instant): UsageCounts {
        val done = sent.hours.filter { h ->
            val key = h.hour.epochSecond
            !h.hour.plus(Duration.ofHours(1)).isAfter(now) && hours[key] == h.counts
        }.map { it.hour.epochSecond }.toSet()
        return if (done.isEmpty()) this else UsageCounts(hours.filterKeys { it !in done })
    }

    companion object {
        /** The protocol's bounds (v1.MaxUsageHours, MaxUsageCounters, MaxUsageCount). */
        const val MAX_HOURS = 168
        const val MAX_COUNTERS = 64
        const val MAX_COUNT = 10_000

        /** Under the server's 8 days, so an hour is never sent only to be dropped. */
        val MAX_AGE: Duration = Duration.ofDays(7)
    }
}

/**
 * Counts on the UI thread and hands reports to the controller. Times are the TV's clock corrected
 * by the server's ([offset], updated with every config), so hours line up with the control plane's.
 */
class UsageRecorder(private val store: DeviceStore, private val localNow: () -> Instant) {
    @Volatile
    var offset: Duration = Duration.ZERO

    private var counts: UsageCounts = store.usage()

    private fun now(): Instant = localNow().plus(offset)

    @Synchronized
    fun record(counter: String) {
        counts = counts.count(counter, now())
        store.saveUsage(counts)
    }

    @Synchronized
    fun pending(): UsageReport? = counts.pruned(now()).report()

    @Synchronized
    fun acknowledged(sent: UsageReport) {
        val next = counts.acknowledged(sent, now()).pruned(now())
        if (next != counts) {
            counts = next
            store.saveUsage(counts)
        }
    }

    /** The pairing ended: these counts belonged to it. */
    @Synchronized
    fun clear() {
        counts = UsageCounts()
    }
}
