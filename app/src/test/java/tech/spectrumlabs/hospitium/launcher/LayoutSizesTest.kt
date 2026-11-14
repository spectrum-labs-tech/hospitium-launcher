// SPDX-License-Identifier: Apache-2.0

package tech.spectrumlabs.hospitium.launcher

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test
import tech.spectrumlabs.hospitium.launcher.ui.LayoutSizes

class LayoutSizesTest {
    @Test
    fun `every 16 by 9 TV gets the base sizes, whatever its resolution`() {
        // 720p, 1080p and 4K all report ~960dp wide; density absorbs the pixels.
        val s = LayoutSizes.forWidth(960f)
        assertEquals(240.dp, s.infoCardWidth)
        assertEquals(160.dp, s.appTileWidth)
        assertEquals(90.dp, s.appTileHeight)
    }

    @Test
    fun `other shapes scale within bounds`() {
        assertEquals(0.8f, LayoutSizes.forWidth(720f).scale) // 4:3 projector, clamped
        assertEquals(1.25f, LayoutSizes.forWidth(1280f).scale) // ultrawide, clamped
        assertEquals(1.1f, LayoutSizes.forWidth(1056f).scale, 0.0001f)
    }
}
