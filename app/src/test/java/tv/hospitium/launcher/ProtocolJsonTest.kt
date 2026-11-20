// SPDX-License-Identifier: Apache-2.0

package tv.hospitium.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.hospitium.launcher.data.CachedConfig
import tv.hospitium.launcher.protocol.LauncherConfig
import tv.hospitium.launcher.protocol.PairingStatusResponse
import tv.hospitium.launcher.protocol.ProtocolJson
import java.time.Instant

class ProtocolJsonTest {
    @Test
    fun `parses what a Go control plane emits, ignoring unknown fields`() {
        val c = ProtocolJson.decodeFromString(LauncherConfig.serializer(), GO_CONFIG_JSON)
        assertEquals("Example Lodge", c.property.name)
        assertEquals("Cabin 3", c.unit?.name)
        assertEquals("correct horse", c.property.wifi?.password)
        assertEquals(Instant.parse("2026-11-14T18:00:00Z"), c.guest?.checkOut)
        assertEquals(Instant.parse("2026-11-14T06:00:00.123456789Z"), c.guest?.checkoutReminderAt)
        assertEquals(Instant.parse("2026-11-12T15:00:00.987654321Z"), c.serverTime)
        assertEquals(2, c.announcements.size)
        assertNull(c.announcements[1].expiresAt)
        assertEquals("com.example.streaming", c.apps.single().packageName)
    }

    @Test
    fun `omitted optional fields take defaults`() {
        val c = ProtocolJson.decodeFromString(
            LauncherConfig.serializer(),
            """{"property":{"name":"P"},"server_time":"2026-11-12T15:00:00Z"}""",
        )
        assertNull(c.guest)
        assertEquals(emptyList<Any>(), c.announcements)
        assertEquals(0, c.refreshAfterSeconds)
        assertEquals("UTC", c.property.timezone)
    }

    @Test
    fun `pairing status without a token`() {
        val s = ProtocolJson.decodeFromString(PairingStatusResponse.serializer(), """{"status":"pending"}""")
        assertEquals("pending", s.status)
        assertNull(s.deviceToken)
    }

    @Test
    fun `the cache round-trips`() {
        val c = ProtocolJson.decodeFromString(LauncherConfig.serializer(), GO_CONFIG_JSON)
        val cached = CachedConfig(c, offsetMillis = -4200)
        val back = ProtocolJson.decodeFromString(CachedConfig.serializer(), ProtocolJson.encodeToString(CachedConfig.serializer(), cached))
        assertEquals(cached, back)
    }

    @Test
    fun `reads the v0_7_0 fields, and their absence`() {
        val c = ProtocolJson.decodeFromString(
            LauncherConfig.serializer(),
            """{"property":{"name":"P"},"server_time":"2026-11-12T15:00:00Z",
              "announcements":[{"id":"7","title":"Welcome!","show_on_wake":true},{"id":"8","title":"Pool"}],
              "popular_apps":["com.netflix.ninja","com.disney.disneyplus"]}""",
        )
        assertEquals(listOf(true, false), c.announcements.map { it.showOnWake })
        assertEquals(listOf("com.netflix.ninja", "com.disney.disneyplus"), c.popularApps)
        val old = ProtocolJson.decodeFromString(LauncherConfig.serializer(), """{"property":{"name":"P"},"server_time":"2026-11-12T15:00:00Z"}""")
        assertEquals(emptyList<String>(), old.popularApps)
    }
}
