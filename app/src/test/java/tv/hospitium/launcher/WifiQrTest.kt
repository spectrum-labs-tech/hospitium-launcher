// SPDX-License-Identifier: Apache-2.0

package tv.hospitium.launcher

import io.nayuki.qrcodegen.QrCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.hospitium.launcher.domain.WifiQr

class WifiQrTest {
    @Test
    fun `a WPA network`() {
        assertEquals("WIFI:T:WPA;S:Lodge-Guest;P:fireflies;;", WifiQr.payload("Lodge-Guest", "fireflies"))
    }

    @Test
    fun `an open network has no password field`() {
        assertEquals("WIFI:T:nopass;S:Lodge-Guest;;", WifiQr.payload("Lodge-Guest", null))
        assertEquals("WIFI:T:nopass;S:Lodge-Guest;;", WifiQr.payload("Lodge-Guest", ""))
    }

    @Test
    fun `special characters are escaped`() {
        assertEquals("""WIFI:T:WPA;S:Cabin\;3;P:a\:b\,c\\d\"e;;""", WifiQr.payload("Cabin;3", "a:b,c\\d\"e"))
    }

    @Test
    fun `all-hex values are quoted so readers don't decode them as bytes`() {
        assertEquals("WIFI:T:WPA;S:\"CAFE\";P:\"12345678\";;", WifiQr.payload("CAFE", "12345678"))
    }

    @Test
    fun `the longest legal network and password still encode`() {
        val qr = QrCode.encodeText(WifiQr.payload("n".repeat(32), "p".repeat(63)), QrCode.Ecc.MEDIUM)
        assertTrue("version ${qr.version}", qr.version <= 7) // small enough to scan at 92dp on a TV
    }
}
