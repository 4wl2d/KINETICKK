// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.impl.BufferUtil
import java.lang.ref.Reference

internal actual fun damageNumberImageBrush(bitmap: ImageBitmap): Brush = ShaderBrush(
    bitmap.asSkiaBitmap().makeShader(FilterTileMode.DECAL, FilterTileMode.DECAL, SamplingMode.LINEAR).asComposeShader(),
)

internal actual fun damageNumberInkBounds(bitmap: ImageBitmap): Rect {
    val native = bitmap.asSkiaBitmap()
    val width = native.width
    val height = native.height
    val type = native.colorInfo.colorType
    val rowBytes = native.rowBytes
    // The last row need not own its trailing stride padding. End the view at its last pixel.
    val byteCount = (height - 1L) * rowBytes + width.toLong() * 4
    if (width > 0 && height > 0 && (type == ColorType.BGRA_8888 || type == ColorType.RGBA_8888) &&
        rowBytes.toLong() >= width.toLong() * 4 && byteCount in 1L..Int.MAX_VALUE.toLong()
    ) {
        val pixmap = native.peekPixels()
        if (pixmap != null) {
            try {
                // The same bounded pixel view used by Skiko's Bitmap.toBufferedImage. It is read
                // only here, never retained, and its owning bitmap stays alive through the scan.
                val pixels = BufferUtil.getByteBufferFromPointer(pixmap.addr, byteCount.toInt())
                return damageNumberInkBounds(width, height) { x, y -> pixels.get(y * rowBytes + x * 4 + 3).toInt() != 0 }
            } finally {
                pixmap.close()
                Reference.reachabilityFence(native)
            }
        }
    }
    return damageNumberInkBounds(width, height) { x, y -> native.getAlphaf(x, y) > 0f }
}
