// SPDX-License-Identifier: Apache-2.0

package tech.spectrumlabs.hospitium.launcher

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.spectrumlabs.hospitium.launcher.protocol.LauncherApp
import tech.spectrumlabs.hospitium.launcher.ui.AppCatalog
import tech.spectrumlabs.hospitium.launcher.ui.TvApp
import tech.spectrumlabs.hospitium.launcher.ui.brandColor
import androidx.compose.ui.graphics.Color

class AppCatalogTest {
    private fun app(pkg: String, label: String) = TvApp(pkg, label, banner = null, hasBanner = false)

    @Test
    fun `featured keeps the server's order and labels, and skips apps not installed`() {
        val installed = listOf(app("a.pkg", "A"), app("b.pkg", "B"), app("c.pkg", "C"))
        val wanted = listOf(LauncherApp("c.pkg", "See"), LauncherApp("missing.pkg", "Nope"), LauncherApp("a.pkg", ""))
        assertEquals(listOf("c.pkg" to "See", "a.pkg" to "A"), AppCatalog.featured(wanted, installed).map { it.packageName to it.label })
    }

    @Test
    fun `brand colors parse strictly`() {
        assertEquals(Color(0xFF224466), brandColor("#224466", Color.Black))
        assertEquals(Color(0xFF224466), brandColor("224466", Color.Black))
        assertEquals(Color.Black, brandColor("red", Color.Black))
        assertEquals(Color.Black, brandColor("#12345", Color.Black))
        assertEquals(Color.Black, brandColor(null, Color.Black))
    }
}
