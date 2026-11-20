// SPDX-License-Identifier: Apache-2.0

package tv.hospitium.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.hospitium.launcher.data.InMemoryDeviceStore
import tv.hospitium.launcher.domain.UsageCounters
import tv.hospitium.launcher.domain.UsageCounts
import tv.hospitium.launcher.domain.UsageRecorder
import tv.hospitium.launcher.protocol.ProtocolJson
import tv.hospitium.launcher.protocol.UsageReport
import java.time.Duration
import java.time.Instant

class UsageTest {
    private val nine = Instant.parse("2026-11-12T09:00:00Z")

    @Test
    fun `cards map to the protocol's counters`() {
        assertEquals("wifi", UsageCounters.forCard("wifi"))
        assertEquals("house_rules", UsageCounters.forCard("rules"))
        assertEquals("amenities", UsageCounters.forCard("amenities"))
        assertEquals("announcement:12", UsageCounters.forCard("a-12"))
        assertNull(UsageCounters.forCard("a-x"))
        assertNull(UsageCounters.forCard("a-0"))
        assertNull(UsageCounters.forCard("something-new"))
        assertEquals("app:com.netflix.ninja", UsageCounters.app("com.netflix.ninja"))
    }

    @Test
    fun `counts are running totals per UTC hour`() {
        val c = UsageCounts()
            .count("home", nine.plusSeconds(60))
            .count("home", nine.plusSeconds(3_000))
            .count("wifi", nine.plusSeconds(3_599))
            .count("home", nine.plusSeconds(3_600)) // the next hour
        val r = c.report()!!
        assertEquals(listOf(nine, nine.plusSeconds(3_600)), r.hours.map { it.hour })
        assertEquals(mapOf("home" to 2, "wifi" to 1), r.hours[0].counts)
        assertEquals(mapOf("home" to 1), r.hours[1].counts)
    }

    @Test
    fun `counts are bounded the way the server bounds them`() {
        var c = UsageCounts()
        repeat(UsageCounts.MAX_COUNTERS + 5) { c = c.count("app:pkg.n$it", nine) }
        assertEquals(UsageCounts.MAX_COUNTERS, c.hours.values.single().size)
        val capped = UsageCounts(mapOf(nine.epochSecond to mapOf("home" to UsageCounts.MAX_COUNT))).count("home", nine)
        assertEquals(UsageCounts.MAX_COUNT, capped.hours.values.single()["home"])
        // A week-old hour is dropped before it would be sent and refused.
        val old = UsageCounts().count("home", nine).count("home", nine.plus(Duration.ofDays(8)))
        assertEquals(1, old.hours.size)
    }

    @Test
    fun `an accepted hour is forgotten only once it is over and unchanged`() {
        val c = UsageCounts().count("home", nine).count("home", nine.plusSeconds(3_600))
        val sent = c.report()!!
        // At 10:30 the 09:00 hour is over; 10:00 is still filling.
        val after = c.acknowledged(sent, nine.plusSeconds(5_400))
        assertEquals(setOf(nine.plusSeconds(3_600).epochSecond), after.hours.keys)
        // Counted again after the report was built: kept, to send the new total.
        val busy = c.count("home", nine.plusSeconds(10)).acknowledged(sent, nine.plusSeconds(7_200))
        assertEquals(2, busy.hours[nine.epochSecond]!!["home"])
    }

    @Test
    fun `the recorder uses the server-corrected clock and survives restarts`() {
        val store = InMemoryDeviceStore()
        val tvClock = nine.minus(Duration.ofHours(5)) // the TV is five hours behind
        val r = UsageRecorder(store, { tvClock }).apply { offset = Duration.ofHours(5) }
        r.record("wifi")
        assertEquals(nine, r.pending()!!.hours.single().hour)
        val again = UsageRecorder(store, { tvClock }).apply { offset = Duration.ofHours(5) }
        assertEquals(mapOf("wifi" to 1), again.pending()!!.hours.single().counts)
    }

    @Test
    fun `reports serialize as the protocol expects`() {
        val json = ProtocolJson.encodeToString(UsageReport.serializer(), UsageCounts().count("home", nine).report()!!)
        assertEquals("""{"hours":[{"hour":"2026-11-12T09:00:00Z","counts":{"home":1}}]}""", json)
    }
}
