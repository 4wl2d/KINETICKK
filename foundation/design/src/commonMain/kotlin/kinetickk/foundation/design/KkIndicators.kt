// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.isUnspecified
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
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Meters, pips, level badge, info (!) + tooltip, tags, stamps, chips, toasts, separator.

/**
 * Animated meter fills (`.meter.striped` threat/ink, `.meter.striped-v` you/dark, the running Apex in
 * the preset's gold over its seam).
 */
enum class KkMeterStripes { NONE, THREAT, YOU, GOLD }

/**
 * Meter (`.meter`), leaning −12°. [value] and [ghost] are fractions 0..1 of the full meter.
 *
 * - [segments] > 0 splits the meter into that many cells with 3 dp gaps (`.meter.seg`; e.g.
 *   integrity with one cell per 20 points).
 * - [ghost] draws the hit ghost from [value] to [value] + [ghost] in [ghostColor]; the caller
 *   drains it over [KkTime.GhostDrain] ms.
 * - [stripes] replaces the fill with moving diagonal stripes (overheat = THREAT, overdrive
 *   active = YOU, a running Apex = GOLD); [time] in seconds animates them.
 * - [fillBrush] (a cached brush: pattern tile or [kkFoilBrush]) replaces a plain fill, anchored at
 *   the meter's left edge; [fillAlpha] fades a plain or brush fill.
 * - [edge] draws the 3 dp value edge in [edgeColor].
 * - [threat] marks the fill as a threat so MONO adds the hatch.
 * - [fromEnd] anchors the fill at the meter's right edge (a bar that drains from the left): the
 *   value spans the right [value] share and the ghost sits just left of it.
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
    fillBrush: Brush? = null,
    edgeColor: Color = Kk.Bone,
    fillAlpha: Float = 1f,
    fromEnd: Boolean = false,
) {
    val v = value.coerceIn(0f, 1f)
    val reach = (v + ghost.coerceAtLeast(0f)).coerceAtMost(1f)
    // From the left: the fill ends at valueX and the ghost runs on to ghostX. From the end: the fill starts at valueX and
    // the ghost runs back to ghostX, left of it.
    val valueX = if (fromEnd) bounds.right - bounds.width * v else bounds.left + bounds.width * v
    val ghostX = if (fromEnd) bounds.right - bounds.width * reach else bounds.left + bounds.width * reach
    withTransform({ kkShear(bounds.center.y) }) {
        if (segments <= 0) {
            drawMeterSpan(bounds, bounds.left, bounds.right, valueX, ghostX, color, background, ghostColor, stripes, roles, time, threat,
                fillBrush, fillAlpha, fromEnd)
        } else {
            val pitch = bounds.width / segments
            val gap = d(3f).coerceAtMost(pitch * 0.5f)
            for (index in 0 until segments) {
                val left = bounds.left + index * pitch
                drawMeterSpan(bounds, left, left + pitch - gap, valueX, ghostX, color, background, ghostColor, stripes, roles, time, threat,
                    fillBrush, fillAlpha, fromEnd)
            }
        }
        if (edge && v > 0f) drawRect(edgeColor, Offset(if (fromEnd) valueX else valueX - d(3f), bounds.top), Size(d(3f), bounds.height))
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
    fillBrush: Brush?,
    fillAlpha: Float,
    fromEnd: Boolean = false,
) {
    if (right <= left) return
    val height = bounds.height
    if (background.alpha > 0f) drawRect(background, Offset(left, bounds.top), Size(right - left, height))
    val fillLeft = if (fromEnd) max(left, valueX) else left
    val fillRight = if (fromEnd) right else min(right, valueX)
    if (fillRight > fillLeft) {
        when (stripes) {
            KkMeterStripes.NONE -> if (fillBrush != null) {
                // Anchored at the meter's left edge, so the brush does not depend on where the meter sits.
                translate(bounds.left, 0f) {
                    drawRect(fillBrush, Offset(fillLeft - bounds.left, bounds.top), Size(fillRight - fillLeft, height), fillAlpha)
                }
            } else {
                drawRect(color, Offset(fillLeft, bounds.top), Size(fillRight - fillLeft, height), fillAlpha)
            }
            KkMeterStripes.THREAT, KkMeterStripes.YOU, KkMeterStripes.GOLD -> kkDrawStripes(
                fillLeft, bounds.top, fillRight, bounds.bottom, meterStripeColor(stripes, roles), meterStripeBackground(stripes, roles),
                time, KkFillKind.METER_STRIPES,
            )
        }
        if (threat && roles.hatchThreats) {
            drawRect(kkFillBrush(KkFillKind.THREAT_HATCH, Kk.Ink), Offset(fillLeft, bounds.top), Size(fillRight - fillLeft, height))
        }
    }
    val ghostLeft = if (fromEnd) max(left, ghostX) else max(left, valueX)
    val ghostRight = if (fromEnd) min(right, valueX) else min(right, ghostX)
    if (ghostRight > ghostLeft) drawRect(ghostColor, Offset(ghostLeft, bounds.top), Size(ghostRight - ghostLeft, height))
}

/** The bright stripe of a striped meter fill ([KkMeterStripes.NONE] has none: its plain colour applies). */
internal fun meterStripeColor(stripes: KkMeterStripes, roles: KkRolePalette): Color = when (stripes) {
    KkMeterStripes.THREAT -> roles.threat
    KkMeterStripes.YOU, KkMeterStripes.NONE -> roles.you
    KkMeterStripes.GOLD -> roles.gold.base
}

/** The dark stripe between the bright ones. */
internal fun meterStripeBackground(stripes: KkMeterStripes, roles: KkRolePalette): Color = when (stripes) {
    KkMeterStripes.THREAT -> kkMix(roles.threat, Kk.Ink, 0.45f)
    KkMeterStripes.YOU, KkMeterStripes.NONE -> kkMix(roles.you, Kk.Ink, 0.35f)
    KkMeterStripes.GOLD -> roles.gold.seam
}

/**
 * Moving meter stripes ([stripes], [KkFillKind.METER_STRIPES]) filling the plain rect from [left] to
 * [right] (no lean): lines and frames that show a striped meter state. Allocation-free.
 */
fun DrawScope.drawKkMeterStripes(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    stripes: KkMeterStripes,
    roles: KkRolePalette,
    time: Float,
) {
    kkDrawStripes(left, top, right, bottom, meterStripeColor(stripes, roles), meterStripeBackground(stripes, roles), time,
        KkFillKind.METER_STRIPES)
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
 * tier ([KkLevelTier.of] with [maxLevel], no cap by default) sets face, seam, echo, sheen, foil and
 * rays. [time] in seconds animates sheen, foil and rays; [slam] 0..1 is the level-up slam progress
 * (1 = settled). Returns the badge face rect. Text is measured per call: keep `level` changes rare
 * (HUD) or cache the badge.
 *
 * At the cap ([KkLevelTier.T6]) [apex] picks the look (NONE draws as COOLING) and [apexActive] 0..1
 * fades the running activation's echo and rays. One-shots the HUD drives (defaults = settled):
 * [echoGrow] scales the echo offsets (tier change), [slamFrom] and [slamKick] are the slam's start
 * scale and rotation, [kick] scales the whole badge (Apex cast), [shutter] 0..1 sweeps a bone band
 * across the face (1 = none), [flash] lays bone over the face at that alpha, and [raysScale] /
 * [raysAlpha] grow and fade the rays. The face metrics ([kkLevelBadgeSize]) are the same for every
 * tier and state; echoes, rays and glow overflow them. Allocation-free while the level and sizes
 * hold (memoized paths, brushes, strokes and text).
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
    maxLevel: Int = Int.MAX_VALUE,
    apex: KkApexState = KkApexState.NONE,
    apexActive: Float = 0f,
    echoGrow: Float = 1f,
    slamFrom: Float = 1.3f,
    slamKick: Float = 5f,
    kick: Float = 1f,
    shutter: Float = 1f,
    flash: Float = 0f,
    raysScale: Float = 1f,
    raysAlpha: Float = 1f,
): Rect {
    val roles = measurer.roles
    val k = numberSize / 30f
    val labelLayout = measureKkText(measurer, label, badgeLabelStyle(measurer, labelSize), uppercase = true)
    val numberLayout = measureKkText(measurer, kkIntString(level), badgeNumberStyle(measurer, numberSize))
    val size = badgeSize(labelLayout, numberLayout, density, k)
    val bounds = KkRects.of(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height)
    val tier = KkLevelTier.of(level, maxLevel)
    val cut = d(10f) * k
    val alpha = if (slam >= 1f) 1f else KkSlam.alpha(slam)
    val slamScale = (if (slam >= 1f) 1f else KkSlam.scale(slam, slamFrom)) * kick
    val slamRotation = if (slam >= 1f) 0f else KkSlam.rotation(slam, 0f, slamKick)
    val grow = echoGrow.coerceIn(0f, 1f)
    rotate(slamRotation, bounds.center) {
        scale(slamScale, slamScale, bounds.center) {
            val face = KkPathMemo.slab(bounds, cut)
            val baseline = bounds.top + d(6f) * k + badgeAbove(labelLayout, numberLayout)
            val labelX = bounds.left + d(15f) * k
            val numberX = labelX + labelLayout.size.width + d(6f) * k
            if (tier == KkLevelTier.T6) {
                val state = if (apex == KkApexState.NONE) KkApexState.COOLING else apex
                drawApexBadgeBody(roles, face, bounds, cut, k, alpha, grow, time, state, apexActive.coerceIn(0f, 1f), raysScale, raysAlpha)
                // Foil text: the gold scrolls through the glyphs (static while the Apex recharges).
                drawKkFoilText(labelLayout, labelX, numberLayout, numberX, baseline, bounds, time, roles.gold, alpha,
                    scroll = state != KkApexState.COOLING)
                if (state == KkApexState.READY) drawKkSheen(face, bounds, time, 2.6f, roles.gold.light.copy(alpha = 0.6f), alpha)
            } else {
                drawTierBadgeBody(roles, tier, face, bounds, cut, k, alpha, grow, time, raysScale, raysAlpha)
                val fg = if (tier == KkLevelTier.T4) roles.you else Kk.Ink
                drawKkText(labelLayout, labelX, baseline, fg, valign = KkVAlign.BASELINE, alpha = alpha)
                drawKkText(numberLayout, numberX, baseline, fg, valign = KkVAlign.BASELINE, alpha = alpha)
            }
            if (shutter >= 0f && shutter < 1f) {
                // Tier change: a bone band (the sheen's geometry, half the face wide) crosses the face once (Snap).
                drawKkSheen(face, bounds, 0.5f + 0.5f * shutter, 1f, BadgeShutterColor, alpha, bandFraction = 0.5f)
            }
            if (flash > 0f) drawPath(face, Kk.Bone, flash.coerceAtMost(1f) * alpha)
        }
    }
    return bounds
}

/** Bone@0.9: the tier change's shutter band. */
private val BadgeShutterColor = Kk.Bone.copy(alpha = 0.9f)

/** T1–T5: rays (T5), echo, face, seam and sheen, under the text. */
private fun DrawScope.drawTierBadgeBody(
    roles: KkRolePalette,
    tier: KkLevelTier,
    face: Path,
    bounds: Rect,
    cut: Float,
    k: Float,
    alpha: Float,
    echoGrow: Float,
    time: Float,
    raysScale: Float,
    raysAlpha: Float,
) {
    if (tier == KkLevelTier.T5) {
        // The preset's gold (the deep gold where a deficiency sees the bright one as its volt).
        drawKkRays(bounds.center, d(95f) * k, roles.gold.ray, 7f, 22f, 0.3f, 0.8f, time * 360f / (KkTime.AmbientMin / 1000f),
            alpha = 0.5f * alpha * raysAlpha, scale = raysScale)
    }
    val echoOffset = echoGrow * k * when (tier) {
        KkLevelTier.T1, KkLevelTier.T6 -> 0f
        KkLevelTier.T2 -> d(4f)
        KkLevelTier.T3, KkLevelTier.T4 -> d(5f)
        KkLevelTier.T5 -> d(6f)
    }
    if (echoOffset > 0f) {
        val echoPath = KkPathMemo.slab(bounds.left + echoOffset, bounds.top + echoOffset, bounds.right + echoOffset, bounds.bottom + echoOffset, cut)
        when (tier) {
            KkLevelTier.T2 -> drawPath(echoPath, roles.you, alpha)
            KkLevelTier.T3, KkLevelTier.T5 -> drawPath(echoPath, Kk.Bone, alpha)
            else -> drawPath(echoPath, kkFillBrush(KkFillKind.BADGE_STRIPES, roles.you, Kk.Ink), alpha)
        }
    }
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
        KkLevelTier.T5, KkLevelTier.T6 -> drawKkFoil(face, bounds, time, 2.4f, alpha, roles.gold)
    }
    if (tier == KkLevelTier.T2 || tier == KkLevelTier.T3 || tier == KkLevelTier.T5) {
        // The ink seam keeps face and echo apart without hue (Mono sees T2/T3 face and echo 3 ΔE apart).
        drawPath(face, Kk.Ink, alpha, kkStroke(d(1f) * k))
    }
    if (tier >= KkLevelTier.T3) {
        val sheen = if (tier == KkLevelTier.T4) roles.you.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.75f)
        drawKkSheen(face, bounds, time, 2.6f, sheen, alpha)
    }
}

/**
 * T6 (APEX) under the text: rays A (gold) and B (volt, counter-rotating), the volt and the gold-striped
 * echoes, the ready glow, the frame (foil, or moving gold stripes while active) and the ink face inside it.
 */
private fun DrawScope.drawApexBadgeBody(
    roles: KkRolePalette,
    face: Path,
    bounds: Rect,
    cut: Float,
    k: Float,
    alpha: Float,
    echoGrow: Float,
    time: Float,
    state: KkApexState,
    active: Float,
    raysScale: Float,
    raysAlpha: Float,
) {
    val gold = roles.gold
    val turn = time * 360f / (KkTime.AmbientMin / 1000f)
    val raysA = if (state == KkApexState.COOLING) 0.25f else 0.5f
    val raysB = when (state) {
        KkApexState.READY -> 0.22f
        KkApexState.ACTIVE -> 0.22f * active
        else -> 0f
    }
    drawKkRays(bounds.center, d(95f) * k, gold.ray, 7f, 22f, 0.3f, 0.8f, turn, alpha = raysA * alpha * raysAlpha, scale = raysScale)
    drawKkRays(bounds.center, d(120f) * k, roles.you, 3f, 30f, 0.5f, 0.95f, -turn, alpha = raysB * alpha * raysAlpha, scale = raysScale)
    val youEcho = when (state) {
        KkApexState.READY -> 1f
        KkApexState.ACTIVE -> active
        else -> 0f
    }
    if (youEcho > 0f && echoGrow > 0f) {
        val o = d(9f) * k * echoGrow
        drawPath(KkPathMemo.slab(bounds.left + o, bounds.top + o, bounds.right + o, bounds.bottom + o, cut), roles.you, youEcho * alpha)
    }
    if (echoGrow > 0f) {
        val o = d(5f) * k * echoGrow
        drawPath(KkPathMemo.slab(bounds.left + o, bounds.top + o, bounds.right + o, bounds.bottom + o, cut),
            kkFillBrush(KkFillKind.BADGE_STRIPES, gold.base, Kk.Ink), alpha)
    }
    if (state == KkApexState.READY) {
        // drop-shadow glow as stacked strokes (the weapon slot's), pulsing; the frame covers their inner half.
        val pulse = 0.45f + 0.55f * kkPulse(time, 1.2f)
        for (index in 0 until SlotGlowWidths.size) {
            drawPath(face, gold.base, SlotGlowAlphas[index] * pulse * alpha, kkStroke(d(SlotGlowWidths[index])))
        }
    }
    when (state) {
        KkApexState.ACTIVE -> {
            val brush = kkFillBrush(KkFillKind.METER_STRIPES, gold.base, gold.seam)
            val shift = kkLoop(time, KkFillKind.METER_STRIPES.loopSeconds) * kkFillTilePx(KkFillKind.METER_STRIPES)
            clipPath(face) {
                translate(shift, 0f) { drawRect(brush, Offset(bounds.left - shift, bounds.top), bounds.size, alpha) }
            }
        }
        // Cooling: the foil stands still, dimmed.
        KkApexState.COOLING, KkApexState.NONE -> drawKkFoil(face, bounds, 0f, 2.4f, 0.55f * alpha, gold)
        KkApexState.READY -> drawKkFoil(face, bounds, time, 2.4f, alpha, gold)
    }
    val frame = max(d(2.5f) * k, d(2f))
    drawPath(KkPathMemo.slab(bounds.left + frame, bounds.top + frame, bounds.right - frame, bounds.bottom - frame, cut - frame), Kk.Ink, alpha)
}

/**
 * Draws [label] at [labelX] and [number] at [numberX] on [baseline] in foil ([gold], 2.4 s loop over a
 * tile 2.6 × [bounds] wide), scrolling when [scroll]. The glyphs are a mask in a layer the size of
 * [bounds] and the foil is laid over them (SrcIn): a text brush is fixed to its glyphs, so it cannot
 * scroll through them. The mask is ink at full opacity, the colour the other tiers draw the same
 * layouts in, and [alpha] fades the foil instead, so no paragraph is repainted in another colour.
 * Allocation-free (cached layer paint, rect and brush).
 */
private fun DrawScope.drawKkFoilText(
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
 * Tooltip slip (`.info .tipbox`): bone slip with a 10 dp top-right cut and a 2 dp ink edge (so it
 * stays a separate layer over a bone slab under it), ink body text 14 px, 270 dp wide, placed around
 * [anchor] (see [kkTooltipRect]). Returns the slip rect. Allocates its rects per call: fine for
 * menus; in-run screens cache the placement and draw the slip.
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
    val slip = KkPathMemo.chamferTopRight(rect, d(10f))
    drawPath(slip, Kk.Bone, alpha)
    drawPath(slip, Kk.Ink, alpha, style = kkStroke(d(KK_TOOLTIP_EDGE_DP)))
    drawKkText(body, rect.left + d(13f), rect.top + d(11f), Kk.Ink, alpha = alpha)
    return rect
}

/** Width of a tooltip slip's ink edge: half over the slip's rim, half outside it. */
private const val KK_TOOLTIP_EDGE_DP = 2f

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
