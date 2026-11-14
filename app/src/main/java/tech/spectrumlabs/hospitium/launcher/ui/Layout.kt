// SPDX-License-Identifier: Apache-2.0

package tech.spectrumlabs.hospitium.launcher.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Card and tile sizes for the space actually available. Android already makes every TV resolution
 * a ~960x540dp canvas (density scales with pixels), so this only matters for unusual SHAPES: a 4:3
 * projector is ~720dp wide, an ultrawide panel ~1280dp. Sizes follow the width within bounds, so
 * rows keep the same rhythm instead of showing two giant cards or eight tiny ones. Text is never
 * scaled here; that's the viewer's font-size setting.
 */
data class LayoutSizes(val scale: Float) {
    val infoCardWidth: Dp get() = 240.dp * scale
    val infoCardMinHeight: Dp get() = 120.dp
    val infoCardMaxHeight: Dp get() = 200.dp
    val appTileWidth: Dp get() = 160.dp * scale
    val appTileHeight: Dp get() = appTileWidth * 9f / 16f // banners are 16:9

    companion object {
        const val BASE_WIDTH_DP = 960f
        const val MIN_SCALE = 0.8f
        const val MAX_SCALE = 1.25f

        fun forWidth(widthDp: Float): LayoutSizes =
            LayoutSizes((widthDp / BASE_WIDTH_DP).coerceIn(MIN_SCALE, MAX_SCALE))
    }
}
