// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.max

// Rarity card frame + effects, icon plate, relic slot + synergy, weapon slot.

private const val CARD_CUT_DP = 22f

/**
 * Lift of a selected card (`.card.on`: up 22 dp, −1.5°). Wrap the card frame and its content so
 * both move together. [selected] 0..1 (Pull). Allocation-free (inline).
 */
inline fun DrawScope.withKkCardLift(bounds: Rect, selected: Float, block: DrawScope.() -> Unit) {
    val s = selected.coerceIn(0f, 1.2f)
    translate(0f, -22f * density * s) {
        rotate(-1.5f * s, bounds.center) { block() }
    }
}

/**
 * Rarity card frame and effects (`.card`, `.cfx`, `.crays`, `.band`) for [rank] 1 (common) ..
 * 5 (legendary). Rarity reads through effects: common flat; uncommon inner glow; rare + sheen
 * sweep (3.4 s, Snap) + echo 4; epic + halftone rising from the bottom, 5 rising diamond sparks
 * (2.8 s), echo 6 pulsing (1.6 s); legendary + rotating conic rays behind, striped gold echo
 * (animated) and a foil band. [time] in seconds drives every loop; [selected] 0..1 grows the echo
 * (the caller wraps everything in [withKkCardLift]); [burst] 0..1 plays the legendary ring burst
 * when dealt (negative = none). The band shows [bandStart] (rarity name) and [bandEnd] (e.g.
 * stack or "New"). Glows are stacked translucent strokes, no blur.
 */
fun DrawScope.drawKkCard(
    measurer: CanvasTextMeasurer,
    bounds: Rect,
    rank: Int,
    time: Float = 0f,
    selected: Float = 0f,
    bandStart: String? = null,
    bandEnd: String? = null,
    bandHeightDp: Float = 42f,
    burst: Float = -1f,
    color: Color = Kk.rarity(rank),
) {
    val r = rank.coerceIn(1, 5)
    val s = selected.coerceIn(0f, 1.2f)
    val cut = d(CARD_CUT_DP)
    if (r == 5) {
        drawKkRays(
            Offset(bounds.center.x, bounds.top + bounds.height * 0.46f), bounds.height * 0.82f,
            Color(0x3DFFB627), 5f, 17f, 0.22f, 0.72f, time * 360f / (KkTime.AmbientMin / 1000f),
        )
    }
    // Echo slab behind the face.
    val restingEcho = when (r) {
        3 -> 4f
        4 -> 6f
        5 -> 8f
        else -> 0f
    }
    val selectedEcho = if (r >= 4) 12f else 10f
    val echo = d(kkLerp(restingEcho, selectedEcho, s.coerceAtMost(1f)))
    if (echo > 0.25f) {
        val echoPath = KkPathMemo.slab(bounds.left + echo, bounds.top + echo, bounds.right + echo, bounds.bottom + echo, cut)
        when (r) {
            5 -> {
                clipPath(echoPath) {
                    kkDrawStripes(bounds.left + echo, bounds.top + echo, bounds.right + echo, bounds.bottom + echo,
                        Kk.RLegend, Color(0xFFFFE59A), time, KkFillKind.CARD_STRIPES)
                }
            }
            4 -> {
                drawPath(echoPath, color)
                drawPath(echoPath, Color.White, alpha = 0.3f * kkPulse(time, 1.6f, KkEase.Out))
            }
            else -> drawPath(echoPath, color)
        }
    }
    val face = KkPathMemo.slab(bounds, cut)
    drawPath(face, if (s > 0.5f) Kk.Ink3 else Kk.Ink2)
    clipPath(face) {
        if (r >= 4) drawCardHalftone(bounds, color, time)
        if (r >= 2) drawInnerGlow(face, color)
        if (r >= 4) drawCardSparks(bounds, color, time)
    }
    if (r >= 3) drawKkSheen(face, bounds, time, 3.4f, Color.White.copy(alpha = 0.17f), bandFraction = 0.45f, startFraction = -0.55f)
    drawCardBand(measurer, bounds, r, color, time, bandStart, bandEnd, bandHeightDp)
    if (r == 5 && burst >= 0f && burst <= 1f) {
        drawKkRingBurst(bounds.center, bounds.width * 0.8f, burst, Kk.RLegend, 4f)
    }
}

private fun DrawScope.drawInnerGlow(face: Path, color: Color) {
    // Inset glow (46 px blur, rarity color) as stacked strokes; the face clip keeps the inner half.
    for (index in 0 until GlowWidths.size) {
        drawPath(face, color, alpha = GlowAlphas[index], style = kkStroke(d(GlowWidths[index])))
    }
}

// Twelve nested strokes approximate `inset 0 0 46px -14px` (strongest at the rim, gone by ~30 dp).
private val GlowWidths = floatArrayOf(4f, 8f, 12f, 16f, 20f, 24f, 28f, 32f, 38f, 44f, 52f, 60f)
private val GlowAlphas = floatArrayOf(0.05f, 0.045f, 0.04f, 0.035f, 0.03f, 0.026f, 0.022f, 0.018f, 0.014f, 0.011f, 0.008f, 0.006f)

private fun DrawScope.drawCardHalftone(bounds: Rect, color: Color, time: Float) {
    // 9 px dots @30 %, masked by radial(120% 70% at 50% 108%) 15% to 70%, drifting up 36 px / 6 s.
    val shift = -kkLoop(time, 6f) * d(36f)
    val canvas = drawContext.canvas
    canvas.saveLayer(bounds, KkLayers.paint)
    translate(0f, shift) {
        drawRect(kkFillBrush(KkFillKind.HALFTONE, color.copy(alpha = 0.3f)), Offset(bounds.left, bounds.top - shift), bounds.size)
    }
    kkApplyEllipseMask(bounds, bounds.center.x, bounds.top + bounds.height * 1.08f,
        bounds.width * 1.2f * 0.7f, bounds.height * 0.7f * 0.7f, 0.2f)
    canvas.restore()
}

private val SparkLeft = floatArrayOf(0.18f, 0.42f, 0.66f, 0.84f, 0.30f)
private val SparkDelay = floatArrayOf(0.2f, 1.1f, 0.6f, 1.7f, 2.2f)
private val SparkSize = floatArrayOf(7f, 5f, 7f, 5f, 7f)

private fun DrawScope.drawCardSparks(bounds: Rect, color: Color, time: Float) {
    for (index in 0 until 5) {
        val p = kkLoop(time, 2.8f, SparkDelay[index])
        val opacity = if (p < 0.2f) p / 0.2f else 1f - (p - 0.2f) / 0.8f
        val size = d(SparkSize[index])
        val cx = bounds.left + bounds.width * SparkLeft[index] + size * 0.5f
        val cy = bounds.bottom + d(12f) - size * 0.5f - d(120f) * p
        // Moving diamonds as rotated squares: no path rebuild per frame.
        val half = size * 0.5f * 0.7071f
        rotate(45f, Offset(cx, cy)) {
            drawRect(color, Offset(cx - half, cy - half), Size(half * 2f, half * 2f), alpha = opacity.coerceIn(0f, 1f))
        }
    }
}

private fun DrawScope.drawCardBand(
    measurer: CanvasTextMeasurer,
    bounds: Rect,
    rank: Int,
    color: Color,
    time: Float,
    start: String?,
    end: String?,
    heightDp: Float,
) {
    val band = KkRects.of(bounds.left + d(18f), bounds.top, bounds.right, bounds.top + d(heightDp))
    val path = KkPathMemo.band(band.left, band.top, band.right, band.bottom, d(4f), d(9f))
    if (rank == 5) drawKkFoil(path, band, time, 2.2f) else drawPath(path, color)
    if (start != null) {
        drawKkText(measurer, start, measurer.typography.labelStyle(12f), band.left + d(14f), band.center.y, Kk.Ink,
            valign = KkVAlign.CENTER, uppercase = true, maxWidth = band.width * 0.6f)
    }
    if (end != null) {
        drawKkText(measurer, end, measurer.typography.monoStyle(9f, weight = FontWeight.Bold), band.right - d(22f), band.center.y, Kk.Ink,
            align = KkAlign.END, valign = KkVAlign.CENTER, uppercase = true)
    }
}

/**
 * Card name (`.cname`): cond 900 italic in bone; epic adds a stacked violet glow, legendary uses the
 * animated foil brush. Returns the text layout.
 */
fun DrawScope.drawKkCardName(
    measurer: CanvasTextMeasurer,
    text: String,
    topLeft: Offset,
    rank: Int,
    time: Float = 0f,
    fontSize: Float = 24f,
    maxWidth: Float = Float.POSITIVE_INFINITY,
    color: Color = Kk.Bone,
): TextLayoutResult {
    val layout = measureKkText(measurer, text, measurer.typography.condStyle(fontSize), uppercase = true, maxWidth = maxWidth)
    when (rank.coerceIn(1, 5)) {
        5 -> {
            val tile = layout.size.width * 2.6f
            val shift = -kkLoop(time, 2.4f) * tile
            translate(topLeft.x + shift, 0f) {
                drawKkText(layout, -shift, topLeft.y, color, brush = KkGradients.foil(max(tile, 1f)))
            }
        }
        4 -> {
            // Name glow (text-shadow 0 0 18px epic @55 %) as two rings of faint offset copies.
            for (ring in 1..2) {
                val glow = Kk.REpic.copy(alpha = if (ring == 1) 0.09f else 0.05f)
                val o = d(4f * ring)
                for (index in 0 until 8) {
                    val a = index * TAU_F / 8f
                    drawKkText(layout, topLeft.x + kotlin.math.cos(a) * o, topLeft.y + kotlin.math.sin(a) * o, glow)
                }
            }
            drawKkText(layout, topLeft.x, topLeft.y, color)
        }
        else -> drawKkText(layout, topLeft.x, topLeft.y, color)
    }
    return layout
}

/** Icon plate (card/offer icon): ink-3 slab with [cutDp] cut and a centered [icon] in [color]. */
fun DrawScope.drawKkIconPlate(bounds: Rect, icon: KkIcon?, color: Color, cutDp: Float = 8f, iconSizeDp: Float = 30f) {
    drawPath(KkPathMemo.slab(bounds, d(cutDp)), Kk.Ink3)
    if (icon != null) drawKkIcon(icon, bounds.center, d(iconSizeDp), color)
}

/**
 * Relic slot (`.rslot`, 44 dp): diamond with an aspect-colored rim, ink-2 inside and the aspect
 * [icon]; [color] `Color.Unspecified` draws the empty slot (line-2 rim + hatch). [ring] (when
 * specified) draws the selection ring (`you` to bind, threat to replace). Allocation-free.
 */
fun DrawScope.drawKkRelicSlot(
    center: Offset,
    color: Color,
    icon: KkIcon? = null,
    sizeDp: Float = 44f,
    iconSizeDp: Float = sizeDp * 16f / 44f,
    ring: Color = Color.Unspecified,
    alpha: Float = 1f,
) {
    val half = d(sizeDp) * 0.5f
    val empty = color.isUnspecified
    if (ring.isSpecified) {
        drawPath(KkPathMemo.diamond(center.x, center.y, half + d(6f), half + d(6f)), ring, alpha, kkStroke(d(2f)))
    }
    drawPath(KkPathMemo.diamond(center.x, center.y, half, half), if (empty) Kk.Line2 else color, alpha)
    // `inset: 2.5px` on a diamond moves each vertex 2.5 dp inward along both axes.
    val inner = KkPathMemo.diamond(center.x, center.y, half - d(2.5f) * 1.414f, half - d(2.5f) * 1.414f)
    drawPath(inner, Kk.Ink2, alpha)
    if (empty) {
        drawKkHatch(inner)
    } else if (icon != null) {
        drawKkIcon(icon, center, d(iconSizeDp), color, alpha)
    }
}

/**
 * Synergy link bar between two adjacent relic slots (12 × 3 dp with a soft glow, stacked
 * strokes). [from]/[to] are the facing slot tips. Allocation-free.
 */
fun DrawScope.drawKkSynergyLink(from: Offset, to: Offset, color: Color) {
    drawLine(color.copy(alpha = color.alpha * 0.18f), from, to, d(9f))
    drawLine(color.copy(alpha = color.alpha * 0.3f), from, to, d(6f))
    drawLine(color, from, to, d(3f))
}

/**
 * Synergy bracket above linked slots: a straight line from [left] to [right] at [y] with 8 dp
 * legs pointing down; dashed for a preview synergy. Optional [tag] (plate in [color], ink text)
 * sits centered above the line. Returns the tag rect or null.
 */
fun DrawScope.drawKkSynergyBracket(
    measurer: CanvasTextMeasurer,
    left: Float,
    right: Float,
    y: Float,
    color: Color,
    dashed: Boolean = false,
    tag: String? = null,
): Rect? {
    val stroke = if (dashed) KkBracketStrokes.dashed(d(2f), density) else kkStroke(d(2f))
    drawPath(KkPathMemo.bracket(left, right, y, d(8f)), color, style = stroke)
    if (tag == null) return null
    val size = kkTagSize(measurer, tag, density, 20f, 12f)
    val topLeft = Offset((left + right) * 0.5f - size.width * 0.5f, y - d(6f) - size.height)
    return drawKkTag(measurer, tag, topLeft, heightDp = 20f, fontSize = 12f, background = if (dashed) Kk.Ink else color,
        foreground = if (dashed) color else Kk.Ink)
}

private object KkBracketStrokes {
    private var key = -1f
    private var stroke: Stroke? = null

    fun dashed(width: Float, density: Float): Stroke {
        if (key == width && stroke != null) return stroke!!
        key = width
        return Stroke(width, cap = StrokeCap.Butt, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * density, 4f * density)))
            .also { stroke = it }
    }
}

private val SlotGlowWidths = floatArrayOf(2f, 4f, 7f, 10f, 14f)
private val SlotGlowAlphas = floatArrayOf(0.16f, 0.12f, 0.08f, 0.05f, 0.03f)

/** Measures the weapon-slot level text ("Lvl N", mono 700 10 px) once for [drawKkWeaponSlot]. */
fun measureKkWeaponLevel(measurer: CanvasTextMeasurer, text: String): TextLayoutResult =
    measureKkText(measurer, text, measurer.typography.monoStyle(10f, weight = FontWeight.Bold, trackingEm = 0f, lineHeightEm = 1f))

/**
 * Weapon slot (`.wslot`, 62 dp): sheared square, [icon] in `you` (legendary when [maxLevel]),
 * [cooldown] 0..1 remaining as an ink 80 % conic sweep from 12 o'clock, `you` glow when [ready]
 * (stacked strokes), [levelText] ("Lvl N", mono) at the bottom right. `icon == null` with
 * [empty] draws the hatch + plus. Pass a pre-measured [levelLayout] on hot paths.
 */
fun DrawScope.drawKkWeaponSlot(
    measurer: CanvasTextMeasurer,
    bounds: Rect,
    icon: KkIcon?,
    levelText: String? = null,
    cooldown: Float = 0f,
    ready: Boolean = false,
    maxLevel: Boolean = false,
    empty: Boolean = icon == null,
    levelLayout: TextLayoutResult? = null,
    iconSizeDp: Float = 32f,
) {
    val cut = d(9f)
    val face = KkPathMemo.slab(bounds, cut)
    if (empty) {
        drawKkHatch(face)
        drawKkIcon(KkIcon.UI_PLUS, bounds.center, d(16f), Kk.Mute2)
        return
    }
    val accent = if (maxLevel) Kk.RLegend else measurer.roles.you
    if (ready || maxLevel) {
        // drop-shadow(0 0 5px accent @55 %) as stacked strokes; the face covers their inner half.
        for (index in 0 until SlotGlowWidths.size) {
            drawPath(face, accent, alpha = SlotGlowAlphas[index], style = kkStroke(d(SlotGlowWidths[index])))
        }
    }
    drawPath(face, if (ready) Kk.Ink3 else Kk.Ink2)
    if (icon != null) drawKkIcon(icon, bounds.center, d(iconSizeDp), accent)
    val cd = cooldown.coerceIn(0f, 1f)
    if (cd > 0f) {
        clipPath(face) {
            val radius = max(bounds.width, bounds.height)
            drawArc(Kk.Ink.copy(alpha = 0.8f), -90f, 360f * cd, true,
                Offset(bounds.center.x - radius, bounds.center.y - radius), Size(radius * 2f, radius * 2f))
        }
    }
    val layout = levelLayout ?: levelText?.let { measureKkWeaponLevel(measurer, it) }
    if (layout != null) {
        drawKkText(layout, bounds.right - d(9f), bounds.bottom - d(5f), if (maxLevel) Kk.RLegend else Kk.Bone,
            align = KkAlign.END, valign = KkVAlign.BASELINE)
    }
}
