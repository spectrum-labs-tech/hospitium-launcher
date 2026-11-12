// SPDX-License-Identifier: Apache-2.0

package tech.spectrumlabs.hospitium.launcher

import tech.spectrumlabs.hospitium.launcher.protocol.GuestInfo
import tech.spectrumlabs.hospitium.launcher.protocol.LauncherConfig
import tech.spectrumlabs.hospitium.launcher.protocol.PropertyInfo
import java.time.Duration
import java.time.Instant

val T0: Instant = Instant.parse("2026-11-12T15:00:00Z")

fun guest(checkOut: Instant = T0.plus(Duration.ofDays(2)), reminder: Duration = Duration.ofHours(12)) = GuestInfo(
    displayName = "Alex",
    checkIn = T0.minus(Duration.ofDays(1)),
    checkOut = checkOut,
    expiresAt = checkOut,
    checkoutReminderAt = checkOut.minus(reminder),
)

fun config(guest: GuestInfo? = guest(), serverTime: Instant = T0, refresh: Int = 300) = LauncherConfig(
    configVersion = "v1",
    property = PropertyInfo(name = "Example Lodge", timezone = "America/Denver"),
    guest = guest,
    serverTime = serverTime,
    refreshAfterSeconds = refresh,
)

/** What a Go control plane actually emits: nanosecond timestamps, offsets, fields this version doesn't know. */
const val GO_CONFIG_JSON = """
{
  "config_version": "9f2c1a7e0b3d4c5a",
  "property": {
    "name": "Example Lodge",
    "timezone": "America/Denver",
    "branding": {"primary_color": "#224466", "logo_url": "https://cdn.example/logo.png"},
    "wifi": {"ssid": "Lodge-Guest", "password": "correct horse"},
    "amenities": ["Hot tub", "Fireplace"],
    "house_rules": ["No smoking"],
    "future_field": {"nested": true}
  },
  "unit": {"name": "Cabin 3"},
  "guest": {
    "display_name": "Alex",
    "check_in": "2026-11-11T16:00:00-07:00",
    "check_out": "2026-11-14T11:00:00-07:00",
    "expires_at": "2026-11-14T11:00:00-07:00",
    "checkout_reminder_at": "2026-11-13T23:00:00.123456789-07:00"
  },
  "announcements": [
    {"id": "1", "title": "Snow tonight", "body": "Chains required on the pass.", "expires_at": "2026-11-13T06:00:00Z"},
    {"id": "2", "title": "Welcome", "body": "Enjoy your stay.", "expires_at": null}
  ],
  "apps": [{"package_name": "com.example.streaming", "label": "Streaming"}],
  "server_time": "2026-11-12T08:00:00.987654321-07:00",
  "refresh_after_seconds": 300
}
"""
