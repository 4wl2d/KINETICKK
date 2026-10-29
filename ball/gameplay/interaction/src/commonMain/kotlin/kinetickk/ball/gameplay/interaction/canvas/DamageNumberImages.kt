// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import kinetickk.ball.gameplay.interaction.fx.InteractionFxLimits
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkAlign
import kinetickk.foundation.design.KkVAlign
import kinetickk.foundation.design.drawKkText
import kinetickk.foundation.design.kkBoxHeight
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** The same five paragraph paints used by the uncached damage label. Its anchor is (0, 0). */
internal fun DrawScope.drawDamageNumberInk(face: TextLayoutResult, shadow: TextLayoutResult, color: Color) {
    drawKkText(shadow, 2f, 2f, Kk.Ink, KkAlign.CENTER, KkVAlign.CENTER)
    drawKkText(shadow, -1f, -1f, Kk.Ink, KkAlign.CENTER, KkVAlign.CENTER)
    drawKkText(shadow, 1f, -1f, Kk.Ink, KkAlign.CENTER, KkVAlign.CENTER)
    drawKkText(shadow, -1f, 1f, Kk.Ink, KkAlign.CENTER, KkVAlign.CENTER)
    drawKkText(face, 0f, 0f, color, KkAlign.CENTER, KkVAlign.CENTER)
}

/**
 * A label's five paints rasterized once, then translated/rotated/scaled by its existing animation.
 * Layout identity includes text, font, locale, density, size and text scale; the paint is keyed too.
 * Draw-thread confined, like the world's other derived image caches. Never retains gameplay state.
 */
internal class DamageNumberImageCache(
    private val capacity: Int = InteractionFxLimits.MAX_DAMAGE_NUMBERS,
    private val maximumPixels: Int = 8 * 1_024 * 1_024,
    private val maximumImagePixels: Int = 1_024 * 1_024,
) {
    private val entries = arrayOfNulls<DamageNumberImage>(capacity)
    private val usedThisFrame = BooleanArray(capacity)
    private val lastUsed = LongArray(capacity)
    private var usage = 0L
    internal var retainedPixels: Int = 0
        private set

    init {
        require(capacity > 0 && maximumPixels > 0 && maximumImagePixels > 0)
    }

    /** A current frame's labels cannot evict one another when high-density images fill the budget. */
    fun beginFrame() {
        usedThisFrame.fill(false)
    }

    /** Null keeps the direct paragraph path for an oversized label or a full current-frame budget. */
    fun image(face: TextLayoutResult, shadow: TextLayoutResult, color: Color): DamageNumberImage? {
        for (index in entries.indices) {
            val entry = entries[index] ?: continue
            if (entry.face === face && entry.shadow === shadow && entry.color == color) {
                usedThisFrame[index] = true
                lastUsed[index] = ++usage
                return entry
            }
        }
        // Half an em beyond both line boxes keeps italic overhang, Cyrillic suffixes and the four
        // offset shadows inside the image. The integer origin preserves subpixel text anchoring.
        val facePixels = with(face.layoutInput.density) { face.layoutInput.style.fontSize.toPx() }
        val shadowPixels = with(shadow.layoutInput.density) { shadow.layoutInput.style.fontSize.toPx() }
        val padding = ceil(max(facePixels, shadowPixels) * 0.5f) + 4f
        val halfWidth = max(face.size.width, shadow.size.width) * 0.5f
        val halfHeight = max(face.kkBoxHeight, shadow.kkBoxHeight) * 0.5f
        val left = floor(-halfWidth - padding - 1f)
        val top = floor(-halfHeight - padding - 1f)
        val width = (ceil(halfWidth + padding + 2f) - left).toInt()
        val height = (ceil(halfHeight + padding + 2f) - top).toInt()
        val pixels = width.toLong() * height.toLong()
        if (width <= 0 || height <= 0 || pixels > maximumImagePixels || pixels > maximumPixels) return null
        val slot = availableSlot(pixels.toInt())
        if (slot < 0) return null
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(face.layoutInput.density, face.layoutInput.layoutDirection, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            translate(-left, -top) { drawDamageNumberInk(face, shadow, color) }
        }
        val drawBounds = damageNumberInkBounds(bitmap)
        return DamageNumberImage(face, shadow, color, bitmap, damageNumberImageBrush(bitmap), left, top, drawBounds).also { entry ->
            entries[slot] = entry
            retainedPixels += pixels.toInt()
            usedThisFrame[slot] = true
            lastUsed[slot] = ++usage
        }
    }

    private fun availableSlot(pixels: Int): Int {
        var slot = entries.indexOfFirst { it == null }
        while (slot < 0 || retainedPixels + pixels > maximumPixels) {
            var oldest = -1
            for (index in entries.indices) {
                if (entries[index] != null && !usedThisFrame[index] && (oldest < 0 || lastUsed[index] < lastUsed[oldest])) oldest = index
            }
            if (oldest < 0) return -1
            val retired = checkNotNull(entries[oldest])
            retainedPixels -= retired.bitmap.width * retired.bitmap.height
            entries[oldest] = null
            if (slot < 0) slot = oldest
        }
        return slot
    }
}

internal class DamageNumberImage(
    internal val face: TextLayoutResult,
    internal val shadow: TextLayoutResult,
    internal val color: Color,
    internal val bitmap: ImageBitmap,
    private val brush: Brush,
    internal val left: Float,
    internal val top: Float,
    internal val drawBounds: Rect,
) {
    fun draw(scope: DrawScope, animationScale: Float = 1f) = with(scope) {
        if (drawBounds.isEmpty) return@with
        translate(left, top) {
            // Below half size the two-source-pixel guard becomes less than one device pixel,
            // so the cropped rectangle's antialiased edge could affect the tiny exit glyph.
            if (animationScale < 0.5f) drawRect(brush, Offset.Zero, Size(bitmap.width.toFloat(), bitmap.height.toFloat()))
            else drawRect(brush, drawBounds.topLeft, drawBounds.size)
        }
    }
}

internal val DamageNumberImages = DamageNumberImageCache()

/** Linear sampling keeps animated glyph edges smooth without wrapping a native image per draw. */
internal expect fun damageNumberImageBrush(bitmap: ImageBitmap): Brush


/** Platform pixel access avoids Compose Skia's temporary full-image ByteArray. */
internal expect fun damageNumberInkBounds(bitmap: ImageBitmap): Rect

/**
 * Finds only the four outer occupied rows/columns; the interior need not be read. The generous
 * rasterization padding stays in the bitmap, while two source pixels protect linear filtering.
 */
internal inline fun damageNumberInkBounds(width: Int, height: Int, hasInk: (Int, Int) -> Boolean): Rect {
    var top = 0
    while (top < height) {
        var x = 0
        while (x < width && !hasInk(x, top)) x++
        if (x < width) break
        top++
    }
    if (top == height) return Rect.Zero
    var bottom = height - 1
    while (bottom > top) {
        var x = 0
        while (x < width && !hasInk(x, bottom)) x++
        if (x < width) break
        bottom--
    }
    var left = 0
    while (left < width) {
        var y = top
        while (y <= bottom && !hasInk(left, y)) y++
        if (y <= bottom) break
        left++
    }
    var right = width - 1
    while (right > left) {
        var y = top
        while (y <= bottom && !hasInk(right, y)) y++
        if (y <= bottom) break
        right--
    }
    return Rect(
        (left - 2).coerceAtLeast(0).toFloat(),
        (top - 2).coerceAtLeast(0).toFloat(),
        (right + 3).coerceAtMost(width).toFloat(),
        (bottom + 3).coerceAtMost(height).toFloat(),
    )
}
