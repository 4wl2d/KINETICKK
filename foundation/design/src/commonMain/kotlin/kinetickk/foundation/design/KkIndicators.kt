// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.isUnspecified
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
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Meters, pips, level badge, info (!) + tooltip, tags, stamps, chips, toasts, separator.

/** Animated meter fills (`.meter.striped` threat/ink, `.meter.striped-v` you/dark). */
enum class KkMeterStripes { NONE, THREAT, YOU }

/**
 * Meter (`.meter`), leaning −12°. [value] and [ghost] are fractions 0..1 of the full meter.
 *
 * - [segments] > 0 splits the meter into that many cells with 3 dp gaps (`.meter.seg`; e.g.
 *   integrity with one cell per 20 points).
 * - [ghost] draws the hit ghost from [value] to [value] + [ghost] in [ghostColor]; the caller
 *   drains it over [KkTime.GhostDrain] ms.
 * - [stripes] replaces the fill with moving diagonal stripes (overheat = THREAT, overdrive
 *   active = YOU); [time] in seconds animates them.
 * - [edge] draws the 3 dp bone value edge.
 * - [threat] marks the fill as a threat so MONO adds the hatch.
 *
 * Allocation-free (cached stripe tiles, no paths).
 */
fun DrawScope.drawKkMeter(
    bounds: Rect,
    value: Float,
    color: Color,
    roles: KkRolePalette,
    background: Color = Color(0x1AF1F0E8),
    segments: Int = 0,
    ghost: Float = 0f,
    ghostColor: Color = roles.threat,
    stripes: KkMeterStripes = KkMeterStripes.NONE,
    time: Float = 0f,
    edge: Boolean = false,
    threat: Boolean = false,
) {
    val v = value.coerceIn(0f, 1f)
    val valueX = bounds.left + bounds.width * v
    val ghostX = bounds.left + bounds.width * (v + ghost.coerceAtLeast(0f)).coerceAtMost(1f)
    withTransform({ kkShear(bounds.center.y) }) {
        if (segments <= 0) {
            drawMeterSpan(bounds, bounds.left, bounds.right, valueX, ghostX, color, background, ghostColor, stripes, roles, time, threat)
        } else {
            val pitch = bounds.width / segments
            val gap = d(3f).coerceAtMost(pitch * 0.5f)
            for (index in 0 until segments) {
                val left = bounds.left + index * pitch
                drawMeterSpan(bounds, left, left + pitch - gap, valueX, ghostX, color, background, ghostColor, stripes, roles, time, threat)
            }
        }
        if (edge && v > 0f) drawRect(Kk.Bone, Offset(valueX - d(3f), bounds.top), Size(d(3f), bounds.height))
    }
}

private fun DrawScope.drawMeterSpan(
    bounds: Rect,
    left: Float,
    right: Float,
    valueX: Float,
    ghostX: Float,
    color: Color,
    background: Color,
    ghostColor: Color,
    stripes: KkMeterStripes,
    roles: KkRolePalette,
    time: Float,
    threat: Boolean,
) {
    if (right <= left) return
    val height = bounds.height
    if (background.alpha > 0f) drawRect(background, Offset(left, bounds.top), Size(right - left, height))
    val fillRight = min(right, valueX)
    if (fillRight > left) {
        when (stripes) {
            KkMeterStripes.NONE -> drawRect(color, Offset(left, bounds.top), Size(fillRight - left, height))
            KkMeterStripes.THREAT, KkMeterStripes.YOU -> {
                val base = if (stripes == KkMeterStripes.THREAT) roles.threat else roles.you
                kkDrawStripes(
                    left, bounds.top, fillRight, bounds.bottom, base,
                    kkMix(base, Kk.Ink, if (stripes == KkMeterStripes.THREAT) 0.45f else 0.35f),
                    time, KkFillKind.METER_STRIPES,
                )
            }
        }
        if (threat && roles.hatchThreats) {
            drawRect(kkFillBrush(KkFillKind.THREAT_HATCH, Kk.Ink), Offset(left, bounds.top), Size(fillRight - left, height))
        }
    }
    val ghostLeft = max(left, valueX)
    val ghostRight = min(right, ghostX)
    if (ghostRight > ghostLeft) drawRect(ghostColor, Offset(ghostLeft, bounds.top), Size(ghostRight - ghostLeft, height))
}

/**
 * Tick ladder (HUD velocity, `.vtick`): [count] sheared ticks with 2 dp gaps inside [bounds].
 * Ticks below [lit] are bone, from [hotFrom] `you`, from [maxFrom] threat; unlit ticks ink-4.
 * Allocation-free.
 */
fun DrawScope.drawKkTickLadder(
    bounds: Rect,
    roles: KkRolePalette,
    count: Int,
    lit: Int,
    hotFrom: Int = count,
    maxFrom: Int = count,
) {
    if (count <= 0) return
    val gap = d(2f)
    val pitch = (bounds.width + gap) / count
    withTransform({ kkShear(bounds.center.y) }) {
        for (index in 0 until count) {
            val color = when {
                index >= lit -> Kk.Ink4
                index >= maxFrom -> roles.threat
                index >= hotFrom -> roles.you
                else -> Kk.Bone
            }
            drawRect(color, Offset(bounds.left + index * pitch, bounds.top), Size(pitch - gap, bounds.height))
        }
    }
}

/**
 * Sheared pips (`.pip`, rank pips, dash charges, shield cells): [count] cells of [sizeDp] square
 * (or [widthDp] × [sizeDp]) from [topLeft]. The first [filled] use [color]; cell [filled] is
 * outlined in [color] when [nextOutlined]; the rest use [emptyColor] (outlined when
 * [emptyOutlined]). Returns the total width. Allocation-free.
 */
fun DrawScope.drawKkPips(
    topLeft: Offset,
    count: Int,
    filled: Int,
    color: Color,
    sizeDp: Float = 10f,
    widthDp: Float = sizeDp,
    gapDp: Float = 3f,
    emptyColor: Color = Kk.Line2,
    nextOutlined: Boolean = false,
    emptyOutlined: Boolean = false,
): Float {
    val w = d(widthDp)
    val h = d(sizeDp)
    val gap = d(gapDp)
    for (index in 0 until count) {
        val left = topLeft.x + index * (w + gap)
        val path = KkPathMemo.sheared(left, topLeft.y, left + w, topLeft.y + h)
        when {
            index < filled -> drawPath(path, color)
            index == filled && nextOutlined -> drawPath(KkPathMemo.sheared(left + d(1f), topLeft.y + d(1f), left + w - d(1f), topLeft.y + h - d(1f)), color, style = kkStroke(d(2f)))
            emptyOutlined -> drawPath(KkPathMemo.sheared(left + d(0.75f), topLeft.y + d(0.75f), left + w - d(0.75f), topLeft.y + h - d(0.75f)), emptyColor, style = kkStroke(d(1.5f)))
            else -> drawPath(path, emptyColor)
        }
    }
    return if (count <= 0) 0f else count * w + (count - 1) * gap
}

private fun badgeLabelStyle(measurer: CanvasTextMeasurer, size: Float) =
    measurer.typography.labelStyle(size, trackingEm = 0.08f)

private fun badgeNumberStyle(measurer: CanvasTextMeasurer, size: Float) =
    measurer.typography.wideStyle(size, tabular = true)

/** Size in px of a level badge (`.lvl`) for [level], without the tier echo/rays overflow. */
fun kkLevelBadgeSize(
    measurer: CanvasTextMeasurer,
    level: Int,
    density: Float,
    label: String = "Lvl",
    labelSize: Float = 12f,
    numberSize: Float = 30f,
): Size {
    val labelLayout = measureKkText(measurer, label, badgeLabelStyle(measurer, labelSize), uppercase = true)
    val numberLayout = measureKkText(measurer, kkIntString(level), badgeNumberStyle(measurer, numberSize))
    return badgeSize(labelLayout, numberLayout, density, numberSize / 30f)
}

private fun badgeAbove(labelLayout: TextLayoutResult, numberLayout: TextLayoutResult): Float =
    max(labelLayout.firstBaseline - labelLayout.kkBoxTop, numberLayout.firstBaseline - numberLayout.kkBoxTop)

private fun badgeSize(labelLayout: TextLayoutResult, numberLayout: TextLayoutResult, density: Float, k: Float): Size {
    val above = badgeAbove(labelLayout, numberLayout)
    val below = max(labelLayout.kkBoxBottom - labelLayout.firstBaseline, numberLayout.kkBoxBottom - numberLayout.firstBaseline)
    val width = (15f + 6f + 18f) * k * density + labelLayout.size.width + numberLayout.size.width
    return Size(width, (6f + 5f) * k * density + above + below)
}

/**
 * Level badge (`.lvl`): small [label] (the caller's localized "Lvl") + wide [level] number; the
 * tier ([KkLevelTier.of]) sets face, echo, sheen, foil and rays. [time] in seconds animates sheen,
 * foil and rays; [slam] 0..1 is the level-up slam progress (1 = settled). Returns the badge face
 * rect. Text is measured per call: keep `level` changes rare (HUD) or cache the badge.
 */
fun DrawScope.drawKkLevelBadge(
    measurer: CanvasTextMeasurer,
    topLeft: Offset,
    level: Int,
    label: String = "Lvl",
    time: Float = 0f,
    slam: Float = 1f,
    labelSize: Float = 12f,
    numberSize: Float = 30f,
): Rect {
    val roles = measurer.roles
    val k = numberSize / 30f
    val labelLayout = measureKkText(measurer, label, badgeLabelStyle(measurer, labelSize), uppercase = true)
    val numberLayout = measureKkText(measurer, kkIntString(level), badgeNumberStyle(measurer, numberSize))
    val size = badgeSize(labelLayout, numberLayout, density, k)
    val bounds = KkRects.of(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height)
    val tier = KkLevelTier.of(level)
    val cut = d(10f) * k
    val alpha = if (slam >= 1f) 1f else KkSlam.alpha(slam)
    val slamScale = if (slam >= 1f) 1f else KkSlam.scale(slam)
    val slamRotation = if (slam >= 1f) 0f else KkSlam.rotation(slam, 0f)
    rotate(slamRotation, bounds.center) {
        scale(slamScale, slamScale, bounds.center) {
            if (tier == KkLevelTier.T5) {
                drawKkRays(bounds.center, d(95f) * k, Kk.RLegend.copy(alpha = 0.5f * alpha), 7f, 22f, 0.3f, 0.8f, time * 360f / (KkTime.AmbientMin / 1000f))
            }
            val echoOffset = when (tier) {
                KkLevelTier.T1 -> 0f
                KkLevelTier.T2 -> d(4f) * k
                KkLevelTier.T3, KkLevelTier.T4 -> d(5f) * k
                KkLevelTier.T5 -> d(6f) * k
            }
            if (echoOffset > 0f) {
                val echoPath = KkPathMemo.slab(bounds.left + echoOffset, bounds.top + echoOffset, bounds.right + echoOffset, bounds.bottom + echoOffset, cut)
                when (tier) {
                    KkLevelTier.T2 -> drawPath(echoPath, roles.you, alpha)
                    KkLevelTier.T3, KkLevelTier.T5 -> drawPath(echoPath, Kk.Bone, alpha)
                    else -> drawPath(echoPath, kkFillBrush(KkFillKind.BADGE_STRIPES, roles.you, Kk.Ink), alpha)
                }
            }
            val face = KkPathMemo.slab(bounds, cut)
            when (tier) {
                KkLevelTier.T1, KkLevelTier.T2 -> drawPath(face, Kk.Bone, alpha)
                KkLevelTier.T3 -> drawPath(face, roles.you, alpha)
                KkLevelTier.T4 -> {
                    drawPath(face, Kk.Ink2, alpha)
                    clipPath(face) {
                        val inset = d(1f)
                        drawRect(roles.you, Offset(bounds.left + inset, bounds.top + inset),
                            Size(bounds.width - inset * 2f, bounds.height - inset * 2f), alpha, kkStroke(d(2f)))
                    }
                }
                KkLevelTier.T5 -> drawKkFoil(face, bounds, time, 2.4f, alpha)
            }
            if (tier >= KkLevelTier.T3) {
                val sheen = if (tier == KkLevelTier.T4) roles.you.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.75f)
                drawKkSheen(face, bounds, time, 2.6f, sheen, alpha)
            }
            val fg = if (tier == KkLevelTier.T4) roles.you else Kk.Ink
            val baseline = bounds.top + d(6f) * k + badgeAbove(labelLayout, numberLayout)
            val labelX = bounds.left + d(15f) * k
            drawKkText(labelLayout, labelX, baseline, fg, valign = KkVAlign.BASELINE, alpha = alpha)
            drawKkText(numberLayout, labelX + labelLayout.size.width + d(6f) * k, baseline, fg, valign = KkVAlign.BASELINE, alpha = alpha)
        }
    }
    return bounds
}

/**
 * Animated foil fill (`kk-foil`): r-legend to #FFF3C8 to r-legend gradient at 100°, 260 % wide,
 * scrolling one tile per [loopSeconds]. Fills [path] whose bounds are [bounds].
 */
fun DrawScope.drawKkFoil(path: Path, bounds: Rect, time: Float, loopSeconds: Float = 2.4f, alpha: Float = 1f) {
    val tile = bounds.width * 2.6f
    if (tile <= 0f) return
    val shift = -kkLoop(time, loopSeconds) * tile
    clipPath(path) {
        translate(bounds.left + shift, 0f) {
            drawRect(KkGradients.foil(tile), Offset(-shift - tile, bounds.top), Size(bounds.width + tile * 2f, bounds.height), alpha)
        }
    }
}

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
 * Ambient loops rotate no faster than one turn per [KkTime.AmbientMin]. Allocation-free after the
 * first draw per geometry.
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
) {
    if (radius <= 0f || color.alpha <= 0f) return
    val path = KkGradients.rays(radius, rayDeg, periodDeg)
    translate(center.x, center.y) {
        rotate(rotationDeg, Offset.Zero) {
            drawPath(path, KkGradients.rayFade(radius, color, innerStop, outerStop))
        }
    }
}

/** Memoized gradient brushes and ray geometry (draw thread only; created on first draw). */
internal object KkGradients {
    private val foilTiles = FloatArray(4) { -1f }
    private val foilBrushes = arrayOfNulls<Brush>(4)
    private var foilNext = 0
    private val sheenColors = LongArray(4)
    private val sheenBrushes = arrayOfNulls<Brush>(4)
    private var sheenNext = 0
    private val rayKeys = FloatArray(4 * 3) { -1f }
    private val rayPaths = arrayOfNulls<Path>(4)
    private var rayNext = 0
    private val fadeKeys = FloatArray(4 * 3) { -1f }
    private val fadeColors = LongArray(4)
    private val fadeBrushes = arrayOfNulls<Brush>(4)
    private var fadeNext = 0

    fun foil(tile: Float): Brush {
        for (index in 0 until 4) if (foilTiles[index] == tile) return foilBrushes[index]!!
        // 100deg: mostly horizontal, dropping ~10° to the right.
        val brush = Brush.linearGradient(
            0f to Kk.RLegend, 0.15f to Kk.RLegend, 0.42f to Kk.RLegendFoil, 0.68f to Kk.RLegend, 1f to Kk.RLegend,
            start = Offset(0f, 0f),
            end = Offset(tile, tile * 0.176f),
            tileMode = TileMode.Repeated,
        )
        foilTiles[foilNext] = tile
        foilBrushes[foilNext] = brush
        foilNext = (foilNext + 1) % 4
        return brush
    }

    fun sheen(color: Color): Brush {
        val key = color.value.toLong()
        for (index in 0 until 4) if (sheenBrushes[index] != null && sheenColors[index] == key) return sheenBrushes[index]!!
        val brush = Brush.horizontalGradient(
            0f to Color.Transparent, 0.5f to color, 1f to Color.Transparent,
            startX = 0f,
            endX = 1f,
        )
        sheenColors[sheenNext] = key
        sheenBrushes[sheenNext] = brush
        sheenNext = (sheenNext + 1) % 4
        return brush
    }

    fun rays(radius: Float, rayDeg: Float, periodDeg: Float): Path {
        for (index in 0 until 4) {
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
        rayNext = (rayNext + 1) % 4
        return path
    }

    fun rayFade(radius: Float, color: Color, innerStop: Float, outerStop: Float): Brush {
        val key = color.value.toLong()
        for (index in 0 until 4) {
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
        fadeNext = (fadeNext + 1) % 4
        return brush
    }
}

/**
 * Info (!) button (`.info`, 24 dp): sheared 1.5 dp outline square with an italic "!", mute, or
 * `you` when [active] (hover/focus/open). Use [KkInfoButton] in composables; Canvas screens draw
 * this and expose a focusable semantics node carrying the explanation.
 */
fun DrawScope.drawKkInfoButton(measurer: CanvasTextMeasurer, bounds: Rect, active: Boolean = false) {
    val color = if (active) measurer.roles.you else Kk.Mute
    val inset = d(0.75f)
    drawPath(KkPathMemo.sheared(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset), color, style = kkStroke(d(1.5f)))
    drawKkText(measurer, "!", measurer.typography.condStyle(15f, lineHeightEm = 1f), bounds.center.x, bounds.center.y, color,
        align = KkAlign.CENTER, valign = KkVAlign.CENTER)
}

/** Where a tooltip sits relative to its anchor (`.info`, `.tip-r`, `.tip-l`, `.tip-b`). */
enum class KkTooltipPlacement { ABOVE, ABOVE_END, ABOVE_START, BELOW }

/** Tooltip body layout: body 500 14 px / 1.35, ink, wrapped to [widthDp] − 26 dp. */
fun measureKkTooltip(measurer: CanvasTextMeasurer, text: String, density: Float, widthDp: Float = 270f): TextLayoutResult =
    measureKkText(measurer, text, measurer.typography.bodyStyle(14f, FontWeight.Medium, lineHeightEm = 1.35f),
        maxWidth = (widthDp - 26f) * density, maxLines = 12)

/**
 * Tooltip rect for [anchor] and [placement] (12 dp gap), flipped below when there is no room
 * above and clamped into [within] with an 8 dp margin.
 */
fun kkTooltipRect(
    anchor: Rect,
    bodyHeight: Float,
    density: Float,
    within: Rect,
    placement: KkTooltipPlacement = KkTooltipPlacement.ABOVE,
    widthDp: Float = 270f,
): Rect {
    val w = widthDp * density
    val h = bodyHeight + 22f * density
    val gap = 12f * density
    val margin = 8f * density
    var left = when (placement) {
        KkTooltipPlacement.ABOVE, KkTooltipPlacement.BELOW -> anchor.center.x - w * 0.5f
        KkTooltipPlacement.ABOVE_END -> anchor.right + 6f * density - w
        KkTooltipPlacement.ABOVE_START -> anchor.left - 6f * density
    }
    var top = if (placement == KkTooltipPlacement.BELOW) anchor.bottom + gap else anchor.top - gap - h
    if (placement != KkTooltipPlacement.BELOW && top < within.top + margin) top = anchor.bottom + gap
    if (top + h > within.bottom - margin) top = max(within.top + margin, anchor.top - gap - h)
    left = left.coerceIn(within.left + margin, max(within.left + margin, within.right - margin - w))
    return Rect(left, top, left + w, top + h)
}

/**
 * Tooltip slip (`.info .tipbox`): bone slip with a 10 dp top-right cut, ink body text 14 px,
 * 270 dp wide, placed around [anchor] (see [kkTooltipRect]). Returns the slip rect.
 */
fun DrawScope.drawKkTooltip(
    measurer: CanvasTextMeasurer,
    anchor: Rect,
    text: String,
    placement: KkTooltipPlacement = KkTooltipPlacement.ABOVE,
    widthDp: Float = 270f,
    alpha: Float = 1f,
    within: Rect = Rect(0f, 0f, size.width, size.height),
): Rect {
    val body = measureKkTooltip(measurer, text, density, widthDp)
    val rect = kkTooltipRect(anchor, body.kkBoxHeight, density, within, placement, widthDp)
    drawPath(KkPathMemo.chamferTopRight(rect, d(10f)), Kk.Bone, alpha)
    drawKkText(body, rect.left + d(13f), rect.top + d(11f), Kk.Ink, alpha = alpha)
    return rect
}

/** Bone slip panel (`.tip`, stat-compare tooltip): top-right corner cut [cutDp]. */
fun DrawScope.drawKkSlip(bounds: Rect, cutDp: Float = 12f, color: Color = Kk.Bone) {
    drawPath(KkPathMemo.chamferTopRight(bounds, d(cutDp)), color)
}

/** Tag plates (`.tag`, `.tag-volt`, `.tag-haz`, `.tag-bone`, `.tag-line`). */
enum class KkTagVariant { DEFAULT, YOU, THREAT, BONE, LINE }

private fun tagStyle(measurer: CanvasTextMeasurer, fontSize: Float) = measurer.typography.labelStyle(fontSize, trackingEm = 0.1f)

/** Size in px of a tag with [text] (height [heightDp], 10 dp padding). */
fun kkTagSize(measurer: CanvasTextMeasurer, text: String, density: Float, heightDp: Float = 22f, fontSize: Float = 13f): Size {
    val layout = measureKkText(measurer, text, tagStyle(measurer, fontSize), uppercase = true)
    return Size(layout.size.width + 20f * density, heightDp * density)
}

/**
 * Tag (`.tag`): small cond 800 label on a sheared plate. [background]/[foreground] (when
 * specified) override the variant (e.g. rarity tags: rarity face, ink text). Returns the plate rect.
 */
fun DrawScope.drawKkTag(
    measurer: CanvasTextMeasurer,
    text: String,
    topLeft: Offset,
    variant: KkTagVariant = KkTagVariant.DEFAULT,
    heightDp: Float = 22f,
    fontSize: Float = 13f,
    background: Color = Color.Unspecified,
    foreground: Color = Color.Unspecified,
    alpha: Float = 1f,
): Rect {
    val roles = measurer.roles
    val layout = measureKkText(measurer, text, tagStyle(measurer, fontSize), uppercase = true)
    val rect = KkRects.of(topLeft.x, topLeft.y, topLeft.x + layout.size.width + d(20f), topLeft.y + d(heightDp))
    val face = if (background.isSpecified) background else when (variant) {
        KkTagVariant.DEFAULT -> Kk.Ink3
        KkTagVariant.YOU -> roles.you
        KkTagVariant.THREAT -> roles.threat
        KkTagVariant.BONE -> Kk.Bone
        KkTagVariant.LINE -> Color.Transparent
    }
    val fg = if (foreground.isSpecified) foreground else when (variant) {
        KkTagVariant.DEFAULT, KkTagVariant.LINE -> Kk.Bone
        else -> Kk.Ink
    }
    val path = KkPathMemo.slab(rect, d(6f))
    if (face.alpha > 0f) drawPath(path, face, alpha)
    if (variant == KkTagVariant.LINE && background.isUnspecified) {
        drawPath(KkPathMemo.slab(rect.left + d(0.75f), rect.top + d(0.75f), rect.right - d(0.75f), rect.bottom - d(0.75f), d(6f)),
            Kk.Line2, alpha, kkStroke(d(1.5f)))
    }
    drawKkText(layout, rect.center.x, rect.center.y, fg, align = KkAlign.CENTER, valign = KkVAlign.CENTER, alpha = alpha)
    return rect
}

/** Stamps (`.stamp`, `.stamp-haz`, `.stamp-bone`, `.stamp-o` outline). */
enum class KkStampVariant { YOU, THREAT, BONE, OUTLINE }

private fun stampStyle(measurer: CanvasTextMeasurer, fontSize: Float) =
    measurer.typography.condStyle(fontSize, trackingEm = 0.06f, lineHeightEm = 1f)

/** Unrotated stamp size in px (padding 5/11/4 dp). */
fun kkStampSize(measurer: CanvasTextMeasurer, text: String, density: Float, fontSize: Float = 17f): Size {
    val layout = measureKkText(measurer, text, stampStyle(measurer, fontSize), uppercase = true)
    val k = fontSize / 17f
    return Size(layout.size.width + 22f * k * density, layout.kkBoxHeight + 9f * k * density)
}

/**
 * Stamp (`.stamp`): rotated cond 900 italic label with a hard ink offset shadow, for events
 * ("New!", "Overheat", "Max"). [slam] 0..1 slams it in (1 = settled). [color] (when specified)
 * overrides the variant face (or outline color). Returns the unrotated rect.
 */
fun DrawScope.drawKkStamp(
    measurer: CanvasTextMeasurer,
    text: String,
    topLeft: Offset,
    variant: KkStampVariant = KkStampVariant.YOU,
    fontSize: Float = 17f,
    rotationDeg: Float = -6f,
    slam: Float = 1f,
    color: Color = Color.Unspecified,
    shadow: Boolean = true,
): Rect {
    val roles = measurer.roles
    val layout = measureKkText(measurer, text, stampStyle(measurer, fontSize), uppercase = true)
    val k = fontSize / 17f
    val rect = KkRects.of(topLeft.x, topLeft.y, topLeft.x + layout.size.width + d(22f) * k, topLeft.y + layout.kkBoxHeight + d(9f) * k)
    val face = if (color.isSpecified) color else when (variant) {
        KkStampVariant.YOU, KkStampVariant.OUTLINE -> roles.you
        KkStampVariant.THREAT -> roles.threat
        KkStampVariant.BONE -> Kk.Bone
    }
    val alpha = if (slam >= 1f) 1f else KkSlam.alpha(slam)
    withKkSlam(slam.coerceIn(0f, 1f), rect.center, rotationDeg) {
        if (variant == KkStampVariant.OUTLINE) {
            val inset = d(1f)
            drawRect(face, Offset(rect.left + inset, rect.top + inset), Size(rect.width - inset * 2f, rect.height - inset * 2f), alpha, kkStroke(d(2f)))
            drawKkText(layout, rect.center.x, rect.center.y + d(0.5f) * k, face, KkAlign.CENTER, KkVAlign.CENTER, alpha)
        } else {
            if (shadow) drawRect(Kk.Ink, Offset(rect.left + d(3f) * k, rect.top + d(3f) * k), rect.size, alpha)
            drawRect(face, rect.topLeft, rect.size, alpha)
            if (variant == KkStampVariant.THREAT && roles.hatchThreats) {
                // MONO: the hatch stays a rim so the label keeps a solid face.
                drawKkThreatHatch(rect, roles, Kk.Ink)
                val rim = d(3f) * k
                drawRect(face, Offset(rect.left + rim, rect.top + rim), Size(rect.width - rim * 2f, rect.height - rim * 2f), alpha)
            }
            drawKkText(layout, rect.center.x, rect.center.y + d(0.5f) * k, Kk.Ink, KkAlign.CENTER, KkVAlign.CENTER, alpha)
        }
    }
    return rect
}

/** Matter gem (`.gem`): a vertical hexagon [sizeDp] tall. Allocation-free. */
fun DrawScope.drawKkGem(center: Offset, color: Color, sizeDp: Float = 14f) {
    drawPath(KkPathMemo.gem(center.x, center.y, d(sizeDp)), color)
}

/**
 * Header chip (`.chip`, 34 dp): gem (default) or [icon], a cond 900 italic tabular [value] and an
 * optional small mono [label]. Returns the chip rect.
 */
fun DrawScope.drawKkChip(
    measurer: CanvasTextMeasurer,
    topLeft: Offset,
    value: String,
    label: String? = null,
    icon: KkIcon? = null,
    iconColor: Color = if (icon == null) measurer.roles.you else Kk.Bone,
    heightDp: Float = 34f,
): Rect {
    val valueLayout = measureKkText(measurer, value, measurer.typography.condStyle(21f, trackingEm = 0.02f, tabular = true, lineHeightEm = 1f), uppercase = true)
    val labelLayout = label?.let { measureKkText(measurer, it, measurer.typography.monoStyle(9f, trackingEm = 0.06f), uppercase = true) }
    val lead = if (icon == null) d(14f) else d(16f)
    val gap = d(8f)
    val width = d(10f) + lead + gap + valueLayout.size.width + (if (labelLayout != null) gap + labelLayout.size.width else 0f) + d(14f)
    val rect = KkRects.of(topLeft.x, topLeft.y, topLeft.x + width, topLeft.y + d(heightDp))
    drawPath(KkPathMemo.slab(rect, d(8f)), Kk.Ink2)
    var x = rect.left + d(10f)
    val cy = rect.center.y
    if (icon == null) drawKkGem(Offset(x + lead * 0.5f, cy), iconColor) else drawKkIcon(icon, Offset(x + lead * 0.5f, cy), lead, iconColor)
    x += lead + gap
    drawKkText(valueLayout, x, cy, Kk.Bone, valign = KkVAlign.CENTER)
    x += valueLayout.size.width
    if (labelLayout != null) drawKkText(labelLayout, x + gap, cy, Kk.Mute, valign = KkVAlign.CENTER)
    return rect
}

/** Toast tone: build-update feed row or threat warning row. */
enum class KkToastTone { NORMAL, WARNING }

/**
 * Toast (`.toast`, 44 dp): ink-2 sheared row with an optional leading gem/[icon], cond 900 italic
 * [title] (20 px) and optional mono [detail]; [KkToastTone.WARNING] uses the threat face with ink
 * text. [alpha] fades it (drift out over [KkTime.Drift]). Returns the row rect.
 */
fun DrawScope.drawKkToast(
    measurer: CanvasTextMeasurer,
    topLeft: Offset,
    title: String,
    detail: String? = null,
    icon: KkIcon? = null,
    gem: Boolean = false,
    iconColor: Color = measurer.roles.you,
    tone: KkToastTone = KkToastTone.NORMAL,
    heightDp: Float = 44f,
    alpha: Float = 1f,
): Rect {
    val roles = measurer.roles
    val titleLayout = measureKkText(measurer, title, measurer.typography.condStyle(20f, lineHeightEm = 1f), uppercase = true)
    val detailLayout = detail?.let { measureKkText(measurer, it, measurer.typography.monoStyle(), uppercase = true) }
    val lead = when {
        icon != null -> d(16f)
        gem -> d(14f)
        else -> 0f
    }
    val gap = d(12f)
    val width = d(12f) + (if (lead > 0f) lead + gap else 0f) + titleLayout.size.width +
        (if (detailLayout != null) gap + detailLayout.size.width else 0f) + d(18f)
    val rect = KkRects.of(topLeft.x, topLeft.y, topLeft.x + width, topLeft.y + d(heightDp))
    val warning = tone == KkToastTone.WARNING
    val path = KkPathMemo.slab(rect, d(10f))
    drawPath(path, if (warning) roles.threat else Kk.Ink2, alpha)
    if (warning && roles.hatchThreats) {
        // MONO: the hatch stays a rim so the title keeps a solid face.
        drawKkThreatHatch(path, roles, Kk.Ink)
        val rim = d(3f)
        drawPath(KkPathMemo.slab(rect.left + rim, rect.top + rim, rect.right - rim, rect.bottom - rim, d(9f)), roles.threat, alpha)
    }
    var x = rect.left + d(12f)
    val cy = rect.center.y
    if (icon != null) drawKkIcon(icon, Offset(x + lead * 0.5f, cy), lead, iconColor, alpha)
    else if (gem) drawKkGem(Offset(x + lead * 0.5f, cy), iconColor.copy(alpha = iconColor.alpha * alpha))
    if (lead > 0f) x += lead + gap
    val fg = if (warning) Kk.Ink else Kk.Bone
    drawKkText(titleLayout, x, cy, fg, valign = KkVAlign.CENTER, alpha = alpha)
    x += titleLayout.size.width
    if (detailLayout != null) drawKkText(detailLayout, x + gap, cy, if (warning) Kk.Ink else Kk.Mute, valign = KkVAlign.CENTER, alpha = alpha)
    return rect
}

/**
 * Thin sheared separator (`.sep`): 2 dp × 0.85 em bar at 40 % opacity for text of
 * [fontSizePx], centered at ([x], [centerY]). Use sparingly. Allocation-free.
 */
fun DrawScope.drawKkSeparator(x: Float, centerY: Float, fontSizePx: Float, color: Color) {
    val h = fontSizePx * 0.85f
    drawPath(KkPathMemo.sheared(x - d(1f), centerY - h * 0.5f, x + d(1f), centerY + h * 0.5f), color, alpha = 0.4f)
}
