// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Draws [label] at [labelX] and [number] at [numberX] on [baseline] in foil ([gold], 2.4 s loop over a
 * tile 2.6 × [bounds] wide), scrolling when [scroll]. The glyphs are a mask in a layer the size of
 * [bounds] and the foil is laid over them (SrcIn): a text brush is fixed to its glyphs, so it cannot
 * scroll through them. The mask is ink at full opacity, the colour the other tiers draw the same
 * layouts in, and [alpha] fades the foil instead, so no paragraph is repainted in another colour.
 * Allocation-free (cached layer paint, rect and brush).
 */
internal fun DrawScope.drawKkFoilText(
    label: TextLayoutResult,
    labelX: Float,
    number: TextLayoutResult,
    numberX: Float,
    baseline: Float,
    bounds: Rect,
    time: Float,
    gold: KkGold,
    alpha: Float,
    scroll: Boolean,
) {
    val tile = bounds.width * 2.6f
    if (tile <= 0f) return
    val shift = if (scroll) -kkLoop(time, 2.4f) * tile else 0f
    val canvas = drawContext.canvas
    canvas.saveLayer(bounds, KkLayerPaint.plain)
    drawKkText(label, labelX, baseline, Kk.Ink, valign = KkVAlign.BASELINE)
    drawKkText(number, numberX, baseline, Kk.Ink, valign = KkVAlign.BASELINE)
    translate(bounds.left + shift, 0f) {
        drawRect(KkGradients.foil(tile, gold), Offset(-shift - tile, bounds.top), Size(bounds.width + tile * 2f, bounds.height),
            alpha = alpha, blendMode = BlendMode.SrcIn)
    }
    canvas.restore()
}

/** The plain paint of an offscreen layer (draw thread only). */
private object KkLayerPaint {
    val plain: Paint by lazy { Paint() }
}

/**
 * Animated foil fill (`kk-foil`): the [gold]'s base to its light to its base again (r-legend to
 * #FFF3C8 for the bright gold) at 100°, 260 % wide, scrolling one tile per [loopSeconds]. Fills
 * [path] whose bounds are [bounds].
 */
fun DrawScope.drawKkFoil(path: Path, bounds: Rect, time: Float, loopSeconds: Float = 2.4f, alpha: Float = 1f, gold: KkGold = KkGold.Bright) {
    val tile = bounds.width * 2.6f
    if (tile <= 0f) return
    val shift = -kkLoop(time, loopSeconds) * tile
    clipPath(path) {
        translate(bounds.left + shift, 0f) {
            drawRect(KkGradients.foil(tile, gold), Offset(-shift - tile, bounds.top), Size(bounds.width + tile * 2f, bounds.height), alpha)
        }
    }
}

/** Rect overload of [drawKkFoil]: fills [bounds] itself (a clip rect instead of a clip path). */
fun DrawScope.drawKkFoil(bounds: Rect, time: Float, loopSeconds: Float = 2.4f, alpha: Float = 1f, gold: KkGold = KkGold.Bright) {
    val tile = bounds.width * 2.6f
    if (tile <= 0f) return
    val shift = -kkLoop(time, loopSeconds) * tile
    clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) {
        translate(bounds.left + shift, 0f) {
            drawRect(KkGradients.foil(tile, gold), Offset(-shift - tile, bounds.top), Size(bounds.width + tile * 2f, bounds.height), alpha)
        }
    }
}

/**
 * The foil gradient of [drawKkFoil] for a tile [tile] px wide as a cached brush (static foil fills such
 * as meters). It repeats from the origin of the transform it is drawn under.
 */
fun kkFoilBrush(tile: Float, gold: KkGold = KkGold.Bright): Brush = KkGradients.foil(max(tile, 1f), gold)

/**
 * Sheen sweep (`kk-sheen`): a sheared highlight band ([bandFraction] of [bounds] wide, starting
 * at [startFraction]) crossing [path] in the second half of each [loopSeconds] loop (Snap), moving
 * 4.2 band widths. Allocation-free.
 */
fun DrawScope.drawKkSheen(
    path: Path,
    bounds: Rect,
    time: Float,
    loopSeconds: Float,
    color: Color,
    alpha: Float = 1f,
    bandFraction: Float = 0.4f,
    startFraction: Float = -0.6f,
) {
    val t = kkLoop(time, loopSeconds)
    if (t < 0.5f) return
    val bandWidth = bounds.width * bandFraction
    val start = bounds.left + bounds.width * startFraction
    val x = start + KkEase.Snap.transform((t - 0.5f) / 0.5f) * bandWidth * 4.2f
    clipPath(path) {
        withTransform({ kkShear(bounds.center.y) }) {
            translate(x, 0f) {
                scale(bandWidth, 1f, Offset.Zero) {
                    drawRect(KkGradients.sheen(color), Offset(0f, bounds.top), Size(1f, bounds.height), alpha)
                }
            }
        }
    }
}

/**
 * Rotating conic rays (`.lrays`, `.crays`): wedges [rayDeg] wide every [periodDeg] around
 * [center], faded radially from [innerStop] to [outerStop] of [radius], rotated [rotationDeg].
 * Ambient loops rotate no faster than one turn per [KkTime.AmbientMin]. [alpha] fades and [scale]
 * grows the rays without touching their memoized fade brush and path (pass a constant [color] and
 * animate these instead). Allocation-free after the first draw per geometry and colour.
 */
fun DrawScope.drawKkRays(
    center: Offset,
    radius: Float,
    color: Color,
    rayDeg: Float,
    periodDeg: Float,
    innerStop: Float,
    outerStop: Float,
    rotationDeg: Float,
    alpha: Float = 1f,
    scale: Float = 1f,
) {
    if (radius <= 0f || color.alpha <= 0f || alpha <= 0f || scale <= 0f) return
    val path = KkGradients.rays(radius, rayDeg, periodDeg)
    translate(center.x, center.y) {
        rotate(rotationDeg, Offset.Zero) {
            if (scale == 1f) {
                drawPath(path, KkGradients.rayFade(radius, color, innerStop, outerStop), alpha.coerceAtMost(1f))
            } else {
                scale(scale, scale, Offset.Zero) {
                    drawPath(path, KkGradients.rayFade(radius, color, innerStop, outerStop), alpha.coerceAtMost(1f))
                }
            }
        }
    }
}

/** Memoized gradient brushes and ray geometry (draw thread only; created on first draw). */
internal object KkGradients {
    // Badge face, foil text, Data bar and Data line next to card and tile foils: eight tiles at once.
    private const val FOILS = 8
    private val foilTiles = FloatArray(FOILS) { -1f }
    private val foilGolds = arrayOfNulls<KkGold>(FOILS)
    private val foilBrushes = arrayOfNulls<Brush>(FOILS)
    private var foilNext = 0
    // Card sheens, band sheens and glints next to the level badge's sheens: eight colours at once.
    private val sheenColors = LongArray(8)
    private val sheenBrushes = arrayOfNulls<Brush>(8)
    private var sheenNext = 0
    // Ray sets on screen at once: the T5/T6 badge (two at the cap), card and tile rays.
    private const val RAYS = 8
    private val rayKeys = FloatArray(RAYS * 3) { -1f }
    private val rayPaths = arrayOfNulls<Path>(RAYS)
    private var rayNext = 0
    private val fadeKeys = FloatArray(RAYS * 3) { -1f }
    private val fadeColors = LongArray(RAYS)
    private val fadeBrushes = arrayOfNulls<Brush>(RAYS)
    private var fadeNext = 0

    fun foil(tile: Float, gold: KkGold = KkGold.Bright): Brush {
        for (index in 0 until FOILS) if (foilTiles[index] == tile && foilGolds[index] === gold) return foilBrushes[index]!!
        // 100deg: mostly horizontal, dropping ~10° to the right.
        val brush = Brush.linearGradient(
            0f to gold.base, 0.15f to gold.base, 0.42f to gold.light, 0.68f to gold.base, 1f to gold.base,
            start = Offset(0f, 0f),
            end = Offset(tile, tile * 0.176f),
            tileMode = TileMode.Repeated,
        )
        foilTiles[foilNext] = tile
        foilGolds[foilNext] = gold
        foilBrushes[foilNext] = brush
        foilNext = (foilNext + 1) % FOILS
        return brush
    }

    fun sheen(color: Color): Brush {
        val key = color.value.toLong()
        for (index in 0 until 8) if (sheenBrushes[index] != null && sheenColors[index] == key) return sheenBrushes[index]!!
        val brush = Brush.horizontalGradient(
            0f to Color.Transparent, 0.5f to color, 1f to Color.Transparent,
            startX = 0f,
            endX = 1f,
        )
        sheenColors[sheenNext] = key
        sheenBrushes[sheenNext] = brush
        sheenNext = (sheenNext + 1) % 8
        return brush
    }

    fun rays(radius: Float, rayDeg: Float, periodDeg: Float): Path {
        for (index in 0 until RAYS) {
            val base = index * 3
            if (rayKeys[base] == radius && rayKeys[base + 1] == rayDeg && rayKeys[base + 2] == periodDeg) return rayPaths[index]!!
        }
        val path = rayPaths[rayNext] ?: Path().also { rayPaths[rayNext] = it }
        path.rewind()
        val count = (360f / periodDeg).toInt().coerceAtLeast(1)
        for (ray in 0 until count) {
            val a0 = ray * periodDeg * PI_F / 180f
            val a1 = a0 + rayDeg * PI_F / 180f
            path.moveTo(0f, 0f)
            path.lineTo(cos(a0) * radius, sin(a0) * radius)
            path.lineTo(cos(a1) * radius, sin(a1) * radius)
            path.close()
        }
        val base = rayNext * 3
        rayKeys[base] = radius
        rayKeys[base + 1] = rayDeg
        rayKeys[base + 2] = periodDeg
        rayNext = (rayNext + 1) % RAYS
        return path
    }

    fun rayFade(radius: Float, color: Color, innerStop: Float, outerStop: Float): Brush {
        val key = color.value.toLong()
        for (index in 0 until RAYS) {
            val base = index * 3
            if (fadeBrushes[index] != null && fadeColors[index] == key && fadeKeys[base] == radius &&
                fadeKeys[base + 1] == innerStop && fadeKeys[base + 2] == outerStop
            ) return fadeBrushes[index]!!
        }
        val brush = Brush.radialGradient(
            0f to color, innerStop to color, outerStop to color.copy(alpha = 0f), 1f to color.copy(alpha = 0f),
            center = Offset.Zero,
            radius = radius,
        )
        val base = fadeNext * 3
        fadeKeys[base] = radius
        fadeKeys[base + 1] = innerStop
        fadeKeys[base + 2] = outerStop
        fadeColors[fadeNext] = key
        fadeBrushes[fadeNext] = brush
        fadeNext = (fadeNext + 1) % RAYS
        return brush
    }
}
