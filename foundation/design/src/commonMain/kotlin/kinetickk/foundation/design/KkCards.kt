// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
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

// Card frame (rarity) and face (type), icon plate, relic slot + synergy, weapon slot.

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
 * Card frame and effects (`.card`, `.cfx`, `.crays`, `.band`) in [style], in two channels: the
 * rarity material owns the frame (border, band and label, echo, glow, halo, sheen, halftone, sparks,
 * rays and burst; see [KkCardMaterial]) and the hue owns the face (its pattern, ornament, watermark
 * and seeded variation; see [KkCardStyle]). The default style is the plain one of [rank] 1 (common)
 * .. 5 (legendary) in [color]. [time] in seconds drives every loop; [selected] 0..1 grows the echo
 * (the caller wraps everything in [withKkCardLift]); [burst] 0..1 plays the legendary ring burst when
 * dealt (negative = none). The band shows [bandStart] (rarity name) and [bandEnd] (e.g. stack or
 * "New"). Glows are stacked translucent strokes, no blur.
 *
 * Callers that paint their own art into the face draw [drawKkCardBack], then the art inside
 * [clipKkCardFace], then [drawKkCardFront] and last [drawKkCardFrame] (after anything they lay over
 * the face, so the frame stays whole); this function is exactly those three.
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
    style: KkCardStyle = KkCardStyle.forRank(rank, color, measurer.roles.gold, measurer.roles.spectrum),
) {
    drawKkCardBack(bounds, time, selected, style)
    drawKkCardFront(measurer, bounds, time, selected, bandStart, bandEnd, bandHeightDp, burst, style)
    drawKkCardFrame(bounds, time, style, burst)
}

/**
 * Runs [block] clipped to the card face slab of [bounds] (22 dp cut), for art painted into the face.
 * The slab is a shared cached path, so it is only ever used as this clip, never handed out.
 */
inline fun DrawScope.clipKkCardFace(bounds: Rect, block: DrawScope.() -> Unit) {
    clipPath(kkCardFaceSlab(bounds), block = block)
}

/** The cached face slab behind [clipKkCardFace]; callers must not change it. */
@PublishedApi
internal fun DrawScope.kkCardFaceSlab(bounds: Rect): Path = KkPathMemo.slab(bounds, d(CARD_CUT_DP))

/**
 * The back half of [drawKkCard]: the legendary rays, the material's halo and echo, the face and,
 * inside it, the hue's pattern, the watermark and the material's rising halftone. [textBottom] (px,
 * NaN when unknown) is where the card's text ends: the watermark keeps below it.
 */
fun DrawScope.drawKkCardBack(bounds: Rect, time: Float, selected: Float, style: KkCardStyle, textBottom: Float = Float.NaN) {
    val material = style.material
    val frame = style.frame
    val s = selected.coerceIn(0f, 1.2f)
    val cut = d(CARD_CUT_DP)
    if (material == KkCardMaterial.GOLD && style.raysAlpha > 0f) {
        drawKkRays(
            Offset(bounds.center.x, bounds.top + bounds.height * 0.46f), bounds.height * 0.82f,
            (frame.gold ?: KkGold.Bright).ray.copy(alpha = 0.24f * style.raysAlpha.coerceAtMost(1f)), 5f, 17f, 0.22f, 0.72f,
            time * 360f / (KkTime.AmbientMin / 1000f),
        )
    }
    drawCardHalo(KkPathMemo.slab(bounds, cut), bounds, frame)
    // Echo slab behind the face.
    val echo = d(kkLerp(material.restingEchoDp(), material.selectedEchoDp(), s.coerceAtMost(1f)))
    drawCardEcho(bounds, cut, echo, frame, time)
    val face = KkPathMemo.slab(bounds, cut)
    drawPath(face, if (s > 0.5f) Kk.Ink3 else Kk.Ink2)
    clipPath(face) { drawCardBody(bounds, style, time, textBottom) }
}

/**
 * The front half of [drawKkCard]: the material's inner glow, sparks and facets, the hue's frame
 * ornament, the sheen and the band with its labels. [textLeft] and [textBottom] (px, NaN when
 * unknown) bound the card's text column: the ornaments beside it and the prism facets keep clear of
 * it. [frame] true also draws [drawKkCardFrame] (callers that lay plates over the face pass false
 * and draw it after them).
 */
fun DrawScope.drawKkCardFront(
    measurer: CanvasTextMeasurer,
    bounds: Rect,
    time: Float,
    selected: Float,
    bandStart: String?,
    bandEnd: String?,
    bandHeightDp: Float,
    burst: Float,
    style: KkCardStyle,
    textLeft: Float = Float.NaN,
    textBottom: Float = Float.NaN,
    frame: Boolean = false,
) {
    val material = style.material
    val rarity = style.frame
    val cut = d(CARD_CUT_DP)
    val face = KkPathMemo.slab(bounds, cut)
    val bandBottom = bounds.top + d(bandHeightDp)
    clipPath(face) {
        drawCardGlow(face, bounds, rarity)
        when (material) {
            KkCardMaterial.PRISMATIC -> {
                drawPrismFacets(bounds, bandBottom, style.seed, time, textBottom)
                drawCardSparks(bounds, rarity, time, style.seed)
            }
            KkCardMaterial.GOLD -> if (style.bodyEffects) drawCardSparks(bounds, rarity, time, style.seed)
            else -> Unit
        }
        drawCardOrnament(bounds, cut, bandBottom, style, textLeft)
    }
    // The face sheen of chrome and gold: the frame's own white (its model lists it).
    if (rarity.sheen.isSpecified) {
        drawKkSheen(face, bounds, time, 3.4f, rarity.sheen.copy(alpha = 0.17f), bandFraction = 0.45f, startFraction = -0.55f)
    }
    drawCardBand(measurer, bounds, style, time, bandStart, bandEnd, bandHeightDp)
    if (frame) drawKkCardFrame(bounds, time, style, burst)
}

/**
 * The card's frame, last over everything: the material's border round the whole card and the
 * legendary ring burst ([burst] 0..1 when dealt, negative = none). It never takes the card's hue.
 */
fun DrawScope.drawKkCardFrame(bounds: Rect, time: Float, style: KkCardStyle, burst: Float = -1f) {
    val frame = style.frame
    val cut = d(CARD_CUT_DP)
    drawCardBorder(bounds, cut, frame, time)
    val gold = frame.gold
    if (gold != null && burst >= 0f && burst <= 1f) {
        drawKkRingBurst(bounds.center, bounds.width * 0.8f, burst, gold.base, 4f)
    }
}

private fun DrawScope.drawCardBand(
    measurer: CanvasTextMeasurer,
    bounds: Rect,
    style: KkCardStyle,
    time: Float,
    start: String?,
    end: String?,
    heightDp: Float,
) {
    val band = KkRects.of(bounds.left + d(18f), bounds.top, bounds.right, bounds.top + d(heightDp))
    val path = KkPathMemo.band(band.left, band.top, band.right, band.bottom, d(4f), d(9f))
    drawCardBandMaterial(path, band, style.frame, time)
    val prismatic = style.material == KkCardMaterial.PRISMATIC
    if (start != null) {
        val color = if (prismatic) Kk.Bone else style.startLabelColor()
        // Painted layouts: a band label in another colour on the next card keeps its own paragraph.
        val layout = measureKkTextPainted(measurer, start, measurer.typography.labelStyle(12f), color, uppercase = true, maxWidth = band.width * 0.6f)
        val x = band.left + d(14f)
        if (prismatic) {
            // The word carries the spectrum, sliding with the edge: plain glyphs tinted in a layer, so
            // the shared text layout never changes paint. Its own short tile puts several stops across the word.
            val tile = prismLabelTile(layout.size.width.toFloat())
            val shift = -kkLoop(time, 6f) * tile
            val brush = prismLabelBrush(tile, style.frame.prism ?: KkSpectrum.Full)
            val canvas = drawContext.canvas
            canvas.saveLayer(band, KkLayers.paint)
            drawKkText(layout, x, band.center.y, color, valign = KkVAlign.CENTER)
            translate(x + shift, 0f) {
                drawRect(brush, Offset(band.left - x - shift, band.top), band.size, blendMode = BlendMode.SrcIn)
            }
            canvas.restore()
        } else {
            drawKkText(layout, x, band.center.y, color, valign = KkVAlign.CENTER)
        }
    }
    if (end != null) {
        val color = style.endLabelColor()
        val layout = measureKkTextPainted(measurer, end, measurer.typography.monoStyle(9f, weight = FontWeight.Bold), color, uppercase = true)
        drawKkText(layout, band.right - d(22f), band.center.y, color, align = KkAlign.END, valign = KkVAlign.CENTER)
    }
}

/**
 * Card name (`.cname`): cond 900 italic in bone; epic adds a prismatic glow (rings of faint copies
 * in the preset's [spectrum], or in [glow] when specified), legendary uses the animated foil brush
 * in the preset's [gold]. Returns the text layout.
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
    glow: Color = Color.Unspecified,
    gold: KkGold = measurer.roles.gold,
    spectrum: KkSpectrum = measurer.roles.spectrum,
): TextLayoutResult {
    val layout = measureKkText(measurer, text, measurer.typography.condStyle(fontSize), uppercase = true, maxWidth = maxWidth)
    when (rank.coerceIn(1, 5)) {
        5 -> {
            val tile = layout.size.width * 2.6f
            val shift = -kkLoop(time, 2.4f) * tile
            translate(topLeft.x + shift, 0f) {
                drawKkText(layout, -shift, topLeft.y, color, brush = KkGradients.foil(max(tile, 1f), gold))
            }
        }
        4 -> {
            // Name glow (text-shadow 0 0 18px @55 %) as two rings of faint offset copies, each in its own spectrum colour.
            for (ring in 1..2) {
                val o = d(4f * ring)
                for (index in 0 until 8) {
                    val tint = if (glow.isSpecified) glow else spectrum.stop(index)
                    val a = index * TAU_F / 8f
                    drawKkText(layout, topLeft.x + kotlin.math.cos(a) * o, topLeft.y + kotlin.math.sin(a) * o,
                        tint.copy(alpha = if (ring == 1) 0.11f else 0.06f))
                }
            }
            drawKkText(layout, topLeft.x, topLeft.y, color)
        }
        else -> drawKkText(layout, topLeft.x, topLeft.y, color)
    }
    return layout
}

/**
 * Icon plate (card/offer icon): a slab in [fill] (ink-3 unless the caller tints it) with [cutDp] cut
 * and a centered [icon] in [color]. A specified [rim] edges the plate (its right and bottom edges in
 * [rimSecond] when that is specified too). Allocation-free.
 */
fun DrawScope.drawKkIconPlate(
    bounds: Rect,
    icon: KkIcon?,
    color: Color,
    cutDp: Float = 8f,
    iconSizeDp: Float = 30f,
    rim: Color = Color.Unspecified,
    rimSecond: Color = Color.Unspecified,
    fill: Color = Kk.Ink3,
) {
    val cut = d(cutDp)
    drawPath(KkPathMemo.slab(bounds, cut), fill)
    if (rim.isSpecified) {
        val w = d(1.5f)
        val i = w * 0.5f
        val c = (cut - i * 0.4f).coerceAtLeast(0f)
        drawPath(KkPathMemo.slab(bounds.left + i, bounds.top + i, bounds.right - i, bounds.bottom - i, c), rim, alpha = PLATE_RIM_ALPHA,
            style = kkStroke(w))
        if (rimSecond.isSpecified) {
            // The second hue takes the right and bottom edges.
            drawLine(rimSecond, Offset(bounds.right - i, bounds.top + i), Offset(bounds.right - i - c, bounds.bottom - i), w, alpha = PLATE_RIM_ALPHA)
            drawLine(rimSecond, Offset(bounds.right - i - c, bounds.bottom - i), Offset(bounds.left + i + w, bounds.bottom - i), w, alpha = PLATE_RIM_ALPHA)
        }
    }
    if (icon != null) drawKkIcon(icon, bounds.center, d(iconSizeDp), color)
}

/** The icon plate rim's strength. */
private const val PLATE_RIM_ALPHA = 0.85f

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

internal val SlotGlowWidths = floatArrayOf(2f, 4f, 7f, 10f, 14f)
internal val SlotGlowAlphas = floatArrayOf(0.16f, 0.12f, 0.08f, 0.05f, 0.03f)

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
