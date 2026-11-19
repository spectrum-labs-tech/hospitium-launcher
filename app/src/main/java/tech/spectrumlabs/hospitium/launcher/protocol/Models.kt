// SPDX-License-Identifier: Apache-2.0

// Kotlin mirror of hospitium-protocol v1's launcher contract (v1/launcher.go). Additive within v1:
// unknown fields are ignored, and every field the server may omit has a default.
package tech.spectrumlabs.hospitium.launcher.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.OffsetDateTime

object Paths {
    const val PAIRING_START = "/v1/launcher/pairings"
    const val PAIRING_STATUS = "/v1/launcher/pairings/" // + code
    const val CONFIG = "/v1/launcher/config"
    const val MEDIA = "/v1/launcher/media/" // + lowercase hex SHA-256
    const val USAGE = "/v1/launcher/usage" // added in protocol v0.6.0
}

/** Keys of PropertyInfo.branding (protocol v1 Branding* constants). */
object Branding {
    const val PRIMARY_COLOR = "primary_color"
    const val BACKGROUND_URL = "background_url" // 1920x1080, a same-origin media path
    const val BACKGROUND_URL_2160 = "background_url_2160" // 3840x2160, when one exists
    const val BACKGROUND_SCRIM = "background_scrim" // "0.30".."0.80"
}

object PairingStatus {
    const val PENDING = "pending"
    const val CLAIMED = "claimed"
    const val EXPIRED = "expired"
}

val ProtocolJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/** RFC 3339 timestamps as Go's encoding/json writes them (nanoseconds, any offset). */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Instant = OffsetDateTime.parse(decoder.decodeString()).toInstant()
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
}

typealias Time = @Serializable(with = InstantSerializer::class) Instant

@Serializable
data class PairingStartResponse(
    val code: String,
    @SerialName("poll_token") val pollToken: String,
    @SerialName("expires_at") val expiresAt: Time,
    /**
     * Not on the wire: the server's clock from the HTTP Date header, set by the client. The pairing
     * response carries no server time, and [expiresAt] is on the server's clock, so a TV with a
     * wrong clock needs this to show an honest countdown.
     */
    @Transient val serverDate: Instant? = null,
)

@Serializable
data class PairingStatusResponse(
    val status: String,
    @SerialName("device_token") val deviceToken: String? = null,
    @SerialName("device_id") val deviceId: Long? = null,
)

@Serializable
data class LauncherConfig(
    @SerialName("config_version") val configVersion: String = "",
    val property: PropertyInfo,
    val unit: UnitInfo? = null,
    val guest: GuestInfo? = null,
    val announcements: List<Announcement> = emptyList(),
    val apps: List<LauncherApp> = emptyList(),
    /** Packages most started across the property first (protocol v0.7.0); sorts the app list. */
    @SerialName("popular_apps") val popularApps: List<String> = emptyList(),
    @SerialName("server_time") val serverTime: Time,
    @SerialName("refresh_after_seconds") val refreshAfterSeconds: Int = 0,
)

@Serializable
data class PropertyInfo(
    val name: String,
    val timezone: String = "UTC",
    val branding: Map<String, String> = emptyMap(),
    val wifi: WifiInfo? = null,
    val amenities: List<String> = emptyList(),
    @SerialName("house_rules") val houseRules: List<String> = emptyList(),
)

@Serializable
data class WifiInfo(val ssid: String, val password: String? = null)

@Serializable
data class UnitInfo(val name: String)

/** Deliberately minimal: a display name and stay times, with an expiry the launcher enforces. */
@Serializable
data class GuestInfo(
    @SerialName("display_name") val displayName: String,
    @SerialName("check_in") val checkIn: Time,
    @SerialName("check_out") val checkOut: Time,
    @SerialName("expires_at") val expiresAt: Time,
    @SerialName("checkout_reminder_at") val checkoutReminderAt: Time,
)

@Serializable
data class Announcement(
    val id: String,
    val title: String,
    val body: String = "",
    @SerialName("expires_at") val expiresAt: Time? = null,
    /** Opened when the TV turns on, a message of the day (protocol v0.7.0). */
    @SerialName("show_on_wake") val showOnWake: Boolean = false,
)

/** The intent extra an agent sets when it starts the launcher because the TV woke (protocol v0.7.0). */
const val EXTRA_WAKE = "tech.spectrumlabs.hospitium.launcher.extra.WAKE"

@Serializable
data class LauncherApp(
    @SerialName("package_name") val packageName: String,
    val label: String = "",
)

/** Anonymous hourly usage counts (protocol v0.6.0, v1/usage.go). */
@Serializable
data class UsageReport(val hours: List<UsageHour>)

/** One UTC hour's running totals by counter. */
@Serializable
data class UsageHour(val hour: Time, val counts: Map<String, Int>)
