// SPDX-License-Identifier: Apache-2.0

package tech.spectrumlabs.hospitium.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import io.nayuki.qrcodegen.QrCode

/** Modules of white margin around the code; the QR spec asks for four so cameras find the edges. */
private const val QUIET_ZONE = 4

/**
 * A QR code drawn as dark squares on white, at [size]. Always dark-on-white whatever the theme:
 * many phone cameras don't read inverted codes.
 */
@Composable
fun QrCodeImage(text: String, size: Dp, description: String, modifier: Modifier = Modifier) {
    val qr = remember(text) { runCatching { QrCode.encodeText(text, QrCode.Ecc.MEDIUM) }.getOrNull() } ?: return
    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val modules = qr.size + QUIET_ZONE * 2
        val cell = this.size.minDimension / modules
        drawRect(Color.White, size = Size(cell * modules, cell * modules))
        for (y in 0 until qr.size) {
            for (x in 0 until qr.size) {
                if (qr.getModule(x, y)) {
                    // A hair of overlap so neighbouring squares don't show seams when scaled.
                    drawRect(
                        Color.Black,
                        topLeft = Offset((x + QUIET_ZONE) * cell, (y + QUIET_ZONE) * cell),
                        size = Size(cell + 0.5f, cell + 0.5f),
                    )
                }
            }
        }
    }
}
