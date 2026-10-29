// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap

// Android's BitmapShader uses CanvasDrawScope's FilterQuality.Low (filtered Paint).
internal actual fun damageNumberImageBrush(bitmap: ImageBitmap): Brush = imageBrush(bitmap)

internal actual fun damageNumberInkBounds(bitmap: ImageBitmap): Rect {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = DamageAlphaPixels.get(width * height)
    bitmap.readPixels(pixels)
    return damageNumberInkBounds(width, height) { x, y -> pixels[y * width + x] ushr 24 != 0 }
}

/** Android fills the supplied array directly; retain one draw-thread scratch buffer. */
private object DamageAlphaPixels {
    private var pixels = IntArray(0)

    fun get(size: Int): IntArray {
        if (pixels.size < size) pixels = IntArray(size)
        return pixels
    }
}
