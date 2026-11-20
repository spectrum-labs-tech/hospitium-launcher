// SPDX-License-Identifier: Apache-2.0

package tv.hospitium.launcher.domain

/**
 * The text of a "join this Wi-Fi" QR code, in the format phone cameras recognize
 * (`WIFI:T:WPA;S:<name>;P:<password>;;`, from the ZXing project's barcode contents spec).
 * Pure, so the escaping is unit-tested.
 */
object WifiQr {
    private val HEX = Regex("^[0-9A-Fa-f]+$")

    /** WPA covers WPA, WPA2 and WPA3 in every common reader; no password means an open network. */
    fun payload(ssid: String, password: String?): String {
        val pass = password?.takeIf { it.isNotEmpty() }
        return if (pass == null) {
            "WIFI:T:nopass;S:${field(ssid)};;"
        } else {
            "WIFI:T:WPA;S:${field(ssid)};P:${field(pass)};;"
        }
    }

    /**
     * Backslash-escapes the format's special characters. A value made only of hex digits is
     * quoted, or a reader may decode it as raw hex bytes.
     */
    private fun field(v: String): String {
        val escaped = buildString {
            for (c in v) {
                if (c in "\\;,:\"") append('\\')
                append(c)
            }
        }
        return if (HEX.matches(v)) "\"$escaped\"" else escaped
    }
}
