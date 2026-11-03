// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.max

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
