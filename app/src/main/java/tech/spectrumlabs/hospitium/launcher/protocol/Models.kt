// SPDX-License-Identifier: Apache-2.0

// Kotlin mirror of hospitium-protocol v1's launcher contract (v1/launcher.go). Additive within v1:
// unknown fields are ignored, and every field the server may omit has a default.
package tech.spectrumlabs.hospitium.launcher.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
)

@Serializable
data class LauncherApp(
    @SerialName("package_name") val packageName: String,
    val label: String = "",
)
