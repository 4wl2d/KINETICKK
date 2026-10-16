// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Size
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// A card's style: its rarity material (the frame, see KkCardFrame.kt) and its type channel (the
// face: the hue's pattern, a frame ornament, a large faint watermark and seeded variation).

/**
 * Frame ornaments of the type channel, drawn in the card hue at 35–70 % in the margins around the
 * text column, so they mark the card without fighting its text. None traces the rarity frame: each
 * keeps [KK_ORNAMENT_FRAME_CLEARANCE_DP] (board dp) inside the frame's inner seam and stops short of
 * the corners, so the border stays the rarity's alone.
 */
enum class KkCardOrnament {
    NONE,

    /** A heavy bar on the lower left edge (at most 55 % of the face height) with three rivets. */
    BUMPER,

    /** Three parallel slanted bars at the top right of the face, under the band. */
    SLASHES,

    /** A weighted bar across the middle of the bottom (60 % of its width) with link notches. */
    WEIGHT,

    /** A rack of small upright rounds along the right edge. */
    RACK,

    /** Thin reticle brackets in the face corners with ticks at the side edges. */
    RETICLE,

    /** A shield's double rim on the two lower corners. */
    DOUBLE_RIM,

    /** Vent grille slots at the bottom right. */
    VENTS,

    /** Perforated link dots across the middle of the bottom. */
    PERFORATION,

    /** Ticket notches in both side edges with a short perforation beside each. */
    TICKET,

    /** The card's pattern as a patch in the lower left corner, fading out from it. */
    PATTERN_CORNER,
}

/** How far (board dp, scaled with the card) every ornament keeps inside the frame's inner seam. */
const val KK_ORNAMENT_FRAME_CLEARANCE_DP = 6f

/**
 * Everything that makes a card its own, in neutral terms, in two channels that never mix:
 *
 * - the rarity channel is the [material] alone: it owns the frame (border, band and label, echo,
 *   glow, halo, sheen, halftone, sparks, burst) and paints it from [KkCardMaterial.frame], never in
 *   the [hue];
 * - the type channel is the face: the [hue] and its [pattern] (masked from the icon corner, at
 *   [patternBoost] for a hue that tells little apart by colour alone), a frame [ornament], a large
 *   faint [watermark] glyph and a [seed] that varies the watermark angle, size and offset, the
 *   pattern phase, the ornament details and the spark positions.
 *
 * [bodyEffects] off leaves the generic legendary halftone and sparks out (the caller draws its own
 * scene); [raysAlpha] scales the legendary rays behind the card; [ornamentLeft] moves a corner
 * ornament (the vents) to the lower left, when the caller paints art into the face's lower right.
 * [gold] is the preset's gold ([KkRolePalette.gold]): the gold material paints it; [spectrum] is the
 * preset's prismatic spectrum ([KkRolePalette.spectrum]): the prismatic material paints it. The band
 * labels take the material's own readable colours ([bandLabelColors]).
 *
 * Drawing with a style is allocation-free once its brushes exist.
 */
data class KkCardStyle(
    val material: KkCardMaterial,
    val hue: Color,
    val pattern: KkFillKind? = null,
    val ornament: KkCardOrnament = KkCardOrnament.NONE,
    val watermark: KkIcon? = null,
    val seed: Int = 0,
    val bodyEffects: Boolean = true,
    val raysAlpha: Float = 1f,
    val patternBoost: Float = 1f,
    val ornamentLeft: Boolean = false,
    val gold: KkGold = KkGold.Bright,
    val spectrum: KkSpectrum = KkSpectrum.Full,
) {
    /** Everything the rarity channel paints: the [material]'s frame in the preset's [gold] and [spectrum]. */
    val frame: KkCardFrame get() = material.frame(gold, spectrum)

    /** Whether the card draws anything behind its text (pattern, watermark, halftone, sparks, scene). */
    val drawsBehindText: Boolean
        get() = watermark != null || material.rank >= 4 || (pattern != null && material != KkCardMaterial.CALM)

    companion object {
        private const val CAPACITY = 12
        private val rankKeys = IntArray(CAPACITY) { -1 }
        private val colorKeys = LongArray(CAPACITY)
        private val goldKeys = arrayOfNulls<KkGold>(CAPACITY)
        private val spectrumKeys = arrayOfNulls<KkSpectrum>(CAPACITY)
        private val styles = arrayOfNulls<KkCardStyle>(CAPACITY)
        private var next = 0

        /**
         * The plain style of [rank] in [color] (no pattern, ornament or watermark) in the preset's
         * [gold] and [spectrum], memoized so the default argument of [drawKkCard] allocates nothing per frame.
         */
        fun forRank(rank: Int, color: Color = Kk.rarity(rank), gold: KkGold = KkGold.Bright, spectrum: KkSpectrum = KkSpectrum.Full): KkCardStyle {
            val key = color.value.toLong()
            for (index in 0 until CAPACITY) {
                if (rankKeys[index] == rank && colorKeys[index] == key && goldKeys[index] === gold && spectrumKeys[index] === spectrum) return styles[index]!!
            }
            val created = KkCardStyle(KkCardMaterial.ofRank(rank), color, gold = gold, spectrum = spectrum)
            rankKeys[next] = rank
            colorKeys[next] = key
            goldKeys[next] = gold
            spectrumKeys[next] = spectrum
            styles[next] = created
            next = (next + 1) % CAPACITY
            return created
        }
    }
}

/**
 * Every colour the card's rarity channel paints: its frame ([KkCardFrame.colors]), labels and face
 * sheen included. It is the same for every card of the material in one preset, whatever its hue.
 */
fun KkCardStyle.rarityColors(): List<Color> = frame.colors

/** WCAG relative luminance of [color] (alpha ignored). */
fun kkLuminance(color: Color): Float {
    fun channel(c: Float): Float = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)
}

/** WCAG contrast ratio between [a] and [b] (1..21). */
fun kkContrast(a: Color, b: Color): Float {
    val la = kkLuminance(a)
    val lb = kkLuminance(b)
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

/** [color] mixed toward [toward] in small steps until it reaches [ratio] against [background]. */
fun kkLiftContrast(color: Color, background: Color, ratio: Float = KK_CARD_LABEL_CONTRAST, toward: Color = Kk.Bone): Color {
    var step = 0
    var lifted = color
    while (kkContrast(lifted, background) < ratio && step < 20) {
        step++
        lifted = kkMix(color, toward, step / 20f)
    }
    return lifted
}

/**
 * Every colour the band shows behind a label at [fraction] of its width (0 leading, 1 trailing),
 * over the height the glyphs cover: the material's label-zone tones, with its hairlines, sheen or
 * glass streak. A label there must read against all of them.
 */
@Suppress("UNUSED_PARAMETER")
fun KkCardStyle.bandBackgroundsAt(fraction: Float): List<Color> = frame.labelZone

/** The colours the band labels are painted in (the material's): every colour of the leading label, then the trailing label's. */
fun KkCardStyle.bandLabelColors(): Pair<List<Color>, Color> = frame.label to endLabelColor()

/** The leading label colour (the first stop of the prismatic label). Allocation-free. */
internal fun KkCardStyle.startLabelColor(): Color = frame.label[0]

/** The trailing label colour. Allocation-free. */
internal fun KkCardStyle.endLabelColor(): Color = frame.labelEnd

/**
 * The pattern's strength over the face: a little more on richer materials, more again for a boosted
 * hue, up to [KK_PATTERN_MAX_ALPHA] (a pale or greyish hue's pattern, its type's cue where its colour
 * tells little apart from the metal frame).
 */
private fun KkCardStyle.bodyPatternAlpha(): Float =
    ((if (material.rank >= 3) 0.09f else 0.08f) * patternBoost).coerceAtMost(KK_PATTERN_MAX_ALPHA)

/** The strongest a card's body pattern is drawn ([KkCardStyle.patternBoost] reaches it). */
const val KK_PATTERN_MAX_ALPHA = 0.34f

/**
 * The watermark angle step 0..7 (−14° to 14° in 4° steps): the seed's low bits, so seeds that differ
 * by one (neighbouring ids) always turn their watermarks differently.
 */
fun KkCardStyle.watermarkBucket(): Int = seed and 7

/** Seeded fraction 0..1 for [salt] (stable, well spread). */
fun kkSeedFraction(seed: Int, salt: Int): Float {
    var h = seed * -0x61c88647 + salt * 0x27d4eb2d
    h = h xor (h ushr 15)
    h *= 0x2c1b3c6d
    h = h xor (h ushr 12)
    h *= 0x297a2d39
    h = h xor (h ushr 15)
    return (h ushr 8) / 16_777_216f
}

/**
 * Inside the face clip: the type channel (the hue's pattern, masked from the icon corner, and the
 * watermark glyph), then the material's rising halftone (prismatic always, gold with [KkCardStyle.bodyEffects]).
 */
internal fun DrawScope.drawCardBody(bounds: Rect, style: KkCardStyle, time: Float, textBottom: Float = Float.NaN) {
    val pattern = style.pattern
    if (pattern != null && style.material != KkCardMaterial.CALM) {
        drawMaskedPattern(bounds, pattern, style.hue, style.bodyPatternAlpha(), style.seed)
    }
    style.watermark?.let { icon -> drawWatermark(bounds, icon, style, textBottom) }
    when (style.material) {
        KkCardMaterial.PRISMATIC -> drawCardHalftone(bounds, style.frame, time)
        KkCardMaterial.GOLD -> if (style.bodyEffects) drawCardHalftone(bounds, style.frame, time)
        else -> Unit
    }
}

/** The pattern over the face, strongest by the icon plate (upper left) and fading out across the card. */
private fun DrawScope.drawMaskedPattern(bounds: Rect, kind: KkFillKind, color: Color, alpha: Float, seed: Int) {
    val canvas = drawContext.canvas
    canvas.saveLayer(bounds, KkLayers.paint)
    val tile = kkFillTilePx(kind)
    val dx = kkSeedFraction(seed, 3) * tile
    val dy = kkSeedFraction(seed, 4) * tile
    translate(dx, dy) {
        drawRect(kkFillBrush(kind, color), Offset(bounds.left - dx, bounds.top - dy), bounds.size, alpha = alpha)
    }
    kkApplyEllipseMask(bounds, bounds.left + bounds.width * 0.26f, bounds.top + bounds.height * 0.22f,
        bounds.width * 0.95f, bounds.height * 0.62f, 0.15f)
    canvas.restore()
}

private const val WATERMARK_ALPHA = 0.075f

/**
 * Where the watermark sits, from the seed: 0 cropped at the lower right, 1 low on the right edge,
 * 2 cropped at the lower left. All three keep below the text column's usual reach.
 */
fun KkCardStyle.watermarkAnchor(): Int = (kkSeedFraction(seed, 6) * 3f).toInt().coerceIn(0, 2)

/** The watermark size as a share of the card width (0.55..0.8, seeded). */
fun KkCardStyle.watermarkScale(): Float = 0.55f + 0.25f * kkSeedFraction(seed, 7)

/** Whether the watermark is mirrored left to right (seeded). */
fun KkCardStyle.watermarkMirrored(): Boolean = kkSeedFraction(seed, 8) < 0.5f

/** How far a glyph's strokes reach from its center, as a share of its size (a square glyph turned by 14°). */
private const val GLYPH_REACH = 0.54f

/** The smallest watermark (share of the card width) when the text leaves little room below it. */
private const val WATERMARK_MIN_SCALE = 0.34f

/** How far above the face's bottom (share of its size) a watermark pushed below the text keeps its center. */
private const val WATERMARK_LOWEST = 0.04f

/**
 * The large faint glyph in the hue, cropped at an edge of the face: the seed picks the anchor, the
 * size, the mirror and the angle, so two cards with the same glyph still differ at a glance. Below a
 * measured [textBottom] the glyph keeps its top clear of the text, shrinking when the text leaves
 * little room.
 */
private fun DrawScope.drawWatermark(bounds: Rect, icon: KkIcon, style: KkCardStyle, textBottom: Float) {
    val angle = -14f + style.watermarkBucket() * 4f
    val jx = kkSeedFraction(style.seed, 2) - 0.5f
    val jy = kkSeedFraction(style.seed, 5) - 0.5f
    var size = bounds.width * style.watermarkScale()
    var cx = when (style.watermarkAnchor()) {
        0 -> bounds.left + bounds.width * (0.72f + jx * 0.1f)
        1 -> bounds.right - size * (0.24f + jx * 0.08f)
        else -> bounds.left + size * (0.32f + jx * 0.08f)
    }
    var cy = when (style.watermarkAnchor()) {
        0 -> bounds.bottom - size * (0.3f + jy * 0.12f)
        1 -> bounds.top + bounds.height * (0.82f + jy * 0.06f)
        else -> bounds.bottom - size * (0.28f + jy * 0.1f)
    }
    if (!textBottom.isNaN()) {
        val gap = d(6f)
        // Pushed down, the glyph's center stays just inside the face (at least its top half shows).
        val lowest = bounds.bottom - size * WATERMARK_LOWEST
        if (cy - size * GLYPH_REACH < textBottom + gap) {
            cy = textBottom + gap + size * GLYPH_REACH
            if (cy > lowest) {
                // Little room under the text: a smaller glyph, still cropped at the edge.
                size = max(bounds.width * WATERMARK_MIN_SCALE, (bounds.bottom - textBottom - gap) / (GLYPH_REACH + WATERMARK_LOWEST))
                cy = textBottom + gap + size * GLYPH_REACH
                cx = if (cx > bounds.center.x) bounds.right - size * 0.3f else bounds.left + size * 0.36f
            }
        }
    }
    val center = Offset(cx, cy)
    val mirror = if (style.watermarkMirrored()) -1f else 1f
    rotate(angle, center) {
        scale(mirror, 1f, center) { drawKkIcon(icon, center, size, style.hue, WATERMARK_ALPHA, strokeWidth = GLYPH_STROKE) }
    }
}

/** The watermark's stroke (icon grid units). */
private const val GLYPH_STROKE = 1.6f

private const val ORNAMENT_ALPHA = 0.55f

/** Cards scaled below this (phone deals) take the slimmer ornaments. */
private const val COMPACT_SCALE = 0.8f

/** The gap (dp) an ornament beside the text keeps from the text column. */
private const val TEXT_CLEARANCE_DP = 5f

/** The bumper's longest run: a share of the face height below the band. */
private const val BUMPER_MAX_SHARE = 0.55f

/** The share of the bottom edge the weight bar and the perforation span (centered). */
private const val BOTTOM_SPAN = 0.6f

/**
 * How far (px) ornaments keep inside the face edge on a card of [bounds] in [material]: past the
 * border (and the prismatic rim) and its 1 dp ink seam, then [KK_ORNAMENT_FRAME_CLEARANCE_DP] more
 * (and half a dp for the antialiased fringe), so no ornament traces the frame.
 */
internal fun DrawScope.ornamentInset(bounds: Rect, material: KkCardMaterial): Float =
    frameInnerWidth(material, bounds) + d(1.5f) + d(KK_ORNAMENT_FRAME_CLEARANCE_DP) * cardScale(bounds)

/**
 * The frame ornament of [style] (inside the face clip), in the card's hue (the type channel), clear
 * of the text column and of the rarity frame: an ornament beside the text keeps [TEXT_CLEARANCE_DP]
 * from [textLeft] (the column's left edge, px; NaN when the caller has no text column), and every
 * ornament keeps [ornamentInset] inside the face edge and stops short of the corners.
 */
internal fun DrawScope.drawCardOrnament(bounds: Rect, cut: Float, bandBottom: Float, style: KkCardStyle, textLeft: Float = Float.NaN) {
    val k = cardScale(bounds)
    val hue = style.hue
    val seed = style.seed
    val oa = ORNAMENT_ALPHA
    val edge = ornamentInset(bounds, style.material)
    when (style.ornament) {
        KkCardOrnament.NONE -> Unit
        KkCardOrnament.BUMPER -> {
            // Small (phone) cards take a slimmer bar, without the rail.
            val compact = k < COMPACT_SCALE
            val bar = if (compact) d(3f) else d(6f) * k
            val inset = edge + bar * 0.5f
            val rail = inset + bar * 0.5f + d(4f) * k
            // The bar's (or the rail's) right edge, with the antialiased fringe.
            val reach = if (compact) inset + bar * 0.5f + d(1f) else rail + d(1f) * k
            val y1 = bounds.bottom - edge - d(if (compact) 6f else 10f) * k
            var y0 = max(bandBottom + d(16f) * k, y1 - (bounds.bottom - bandBottom) * BUMPER_MAX_SHARE)
            if (!textLeft.isNaN()) {
                // The slanted edge leans into the text toward the top: the bar starts where it clears the column.
                val room = textLeft - d(TEXT_CLEARANCE_DP) - reach - bounds.left
                val clear = bounds.top + bounds.height * (1f - room / cut)
                y0 = max(y0, clear)
            }
            if (y1 - y0 < d(24f) * k) return
            drawLine(hue, Offset(leftEdge(bounds, cut, y0) + inset, y0), Offset(leftEdge(bounds, cut, y1) + inset, y1), bar, alpha = oa)
            if (!compact) {
                drawLine(hue, Offset(leftEdge(bounds, cut, y0) + rail, y0 + d(6f) * k), Offset(leftEdge(bounds, cut, y1) + rail, y1 - d(6f) * k),
                    d(1f) * k, alpha = 0.35f)
            }
            val first = 0.16f + kkSeedFraction(seed, 20) * 0.08f
            val rivet = if (compact) 0.75f else 1f
            for (index in 0 until 3) {
                val y = kkLerp(y0, y1, first + index * (1f - 2f * first) / 2f)
                val center = Offset(leftEdge(bounds, cut, y) + inset, y)
                drawCircle(Kk.Ink2, d(3.2f) * k * rivet, center)
                drawCircle(hue, d(2.3f) * k * rivet, center, alpha = 0.8f)
                drawCircle(Kk.Ink2, d(0.9f) * k * rivet, center, alpha = 0.8f)
            }
        }
        KkCardOrnament.SLASHES -> {
            val top = bandBottom + d(KK_ORNAMENT_FRAME_CLEARANCE_DP) * k
            for (index in 0 until 3) {
                val length = d(30f - index * 6f) * k
                val x = rightEdge(bounds, cut, top) - edge - d(2f + index * 8.5f) * k
                // Leaning like the slash cut; butt caps give straight ends.
                drawLine(hue, Offset(x, top), Offset(x - length * KkShape.SlashRatio, top + length), d(3.5f) * k,
                    StrokeCap.Butt, alpha = oa + 0.08f * (2 - index))
            }
        }
        KkCardOrnament.WEIGHT -> {
            val bar = d(6f) * k
            // The end blocks reach 0.9 bar widths below the bar's middle.
            val y = bounds.bottom - edge - bar * 0.9f
            val half = bounds.width * BOTTOM_SPAN * 0.5f
            val left = bounds.center.x - half
            val right = bounds.center.x + half
            drawLine(hue, Offset(left, y), Offset(right, y), bar, alpha = oa)
            // Heavier blocks at both ends.
            drawRect(hue, Offset(left, y - bar * 0.9f), Size(d(10f) * k, bar * 1.8f), alpha = oa + 0.1f)
            drawRect(hue, Offset(right - d(10f) * k, y - bar * 0.9f), Size(d(10f) * k, bar * 1.8f), alpha = oa + 0.1f)
            // Link notches cut into the bar.
            val pitch = d(16f) * k
            var x = left + d(18f) * k + kkSeedFraction(seed, 21) * pitch
            while (x < right - d(20f) * k) {
                capsule(Kk.Ink2, x, y, x + d(7f) * k, y, bar * 0.44f, 1f)
                x += pitch
            }
        }
        KkCardOrnament.RACK -> {
            val top = bounds.top + bounds.height * 0.3f
            val pitch = d(17f) * k
            val count = ((bounds.bottom - d(26f) * k - top) / pitch).toInt().coerceIn(0, 12)
            val railY0 = top - d(4f) * k
            val railY1 = top + count * pitch
            val rail = edge + d(0.5f) * k
            drawLine(hue, Offset(rightEdge(bounds, cut, railY0) - rail, railY0), Offset(rightEdge(bounds, cut, railY1) - rail, railY1),
                d(1f) * k, alpha = 0.35f)
            for (index in 0 until count) {
                val y = top + index * pitch
                val round = d(4.5f) * k
                val x = rightEdge(bounds, cut, y) - edge - d(4f) * k - round
                val spent = kkSeedFraction(seed, 30 + index) < 0.3f
                capsule(hue, x + round * 0.5f, y + round * 0.5f, x + round * 0.5f, y + d(12f) * k - round * 0.5f, round,
                    if (spent) 0.22f else oa)
            }
        }
        KkCardOrnament.RETICLE -> {
            val inset = edge + d(1f) * k
            val leg = d(14f) * k
            val w = d(1.3f) * k
            val top = bandBottom + inset
            val bottom = bounds.bottom - inset
            val lt = leftEdge(bounds, cut, top) + inset
            val lb = leftEdge(bounds, cut, bottom) + inset
            val rt = rightEdge(bounds, cut, top) - inset
            val rb = rightEdge(bounds, cut, bottom) - inset
            val a = oa + 0.1f
            drawLine(hue, Offset(lt, top), Offset(lt + leg, top), w, alpha = a)
            drawLine(hue, Offset(lt, top), Offset(lt - leg * cut / bounds.height, top + leg), w, alpha = a)
            drawLine(hue, Offset(rt, top), Offset(rt - leg, top), w, alpha = a)
            drawLine(hue, Offset(rt, top), Offset(rt - leg * cut / bounds.height, top + leg), w, alpha = a)
            drawLine(hue, Offset(lb, bottom), Offset(lb + leg, bottom), w, alpha = a)
            drawLine(hue, Offset(lb, bottom), Offset(lb + leg * cut / bounds.height, bottom - leg), w, alpha = a)
            drawLine(hue, Offset(rb, bottom), Offset(rb - leg, bottom), w, alpha = a)
            drawLine(hue, Offset(rb, bottom), Offset(rb + leg * cut / bounds.height, bottom - leg), w, alpha = a)
            // Range ticks on the side edges.
            val mid = kkLerp(top, bottom, 0.45f + kkSeedFraction(seed, 22) * 0.1f)
            for (index in -1..1) {
                val y = mid + index * d(7f) * k
                val tick = if (index == 0) d(7f) * k else d(4f) * k
                drawLine(hue, Offset(leftEdge(bounds, cut, y) + edge, y), Offset(leftEdge(bounds, cut, y) + edge + tick, y), w, alpha = a)
                drawLine(hue, Offset(rightEdge(bounds, cut, y) - edge, y), Offset(rightEdge(bounds, cut, y) - edge - tick, y), w, alpha = a)
            }
        }
        KkCardOrnament.DOUBLE_RIM -> {
            // A shield's double rim on the two lower corners only: never a second outline round the card.
            val outer = edge + d(2f) * k
            val inner = outer + d(4f + kkSeedFraction(seed, 23) * 2f) * k
            val across = bounds.width * (0.22f + kkSeedFraction(seed, 26) * 0.04f)
            val up = bounds.height * 0.18f
            for (ring in 0..1) {
                val inset = if (ring == 0) outer else inner
                val width = d(if (ring == 0) 1.6f else 1f) * k
                val alpha = if (ring == 0) oa * 0.62f else 0.2f
                // The inner rim is shorter, so the two read as one bevelled shield edge.
                val shrink = if (ring == 0) 0f else inner - outer
                val y0 = bounds.bottom - inset - (up - shrink)
                val y1 = bounds.bottom - inset
                val half = width * 0.5f
                val l1 = leftEdge(bounds, cut, y1) + inset
                val r1 = rightEdge(bounds, cut, y1) - inset
                // Butt ends meeting at the corners without overlapping (the sides reach past the bottom by half a width).
                drawLine(hue, Offset(leftEdge(bounds, cut, y0) + inset, y0), Offset(l1, y1 + half), width, StrokeCap.Butt, alpha = alpha)
                drawLine(hue, Offset(l1 + half, y1), Offset(l1 + across - shrink, y1), width, StrokeCap.Butt, alpha = alpha)
                drawLine(hue, Offset(r1, y1 + half), Offset(rightEdge(bounds, cut, y0) - inset, y0), width, StrokeCap.Butt, alpha = alpha)
                drawLine(hue, Offset(r1 - half, y1), Offset(r1 - across + shrink, y1), width, StrokeCap.Butt, alpha = alpha)
            }
        }
        KkCardOrnament.VENTS -> {
            // At the lower right, or the lower left when the face's lower right carries art.
            val count = 5
            for (index in 0 until count) {
                val y = bounds.bottom - edge - d(5f) * k - index * d(6f) * k
                val width = d(if (index % 2 == 0) 20f else 16f) * k
                val slot = d(3f) * k
                if (style.ornamentLeft) {
                    val left = leftEdge(bounds, cut, y) + edge
                    capsule(hue, left + slot * 0.5f, y, left + width - slot * 0.5f, y, slot, oa - index * 0.04f)
                } else {
                    val right = rightEdge(bounds, cut, y) - edge
                    capsule(hue, right - width + slot * 0.5f, y, right - slot * 0.5f, y, slot, oa - index * 0.04f)
                }
            }
        }
        KkCardOrnament.PERFORATION -> {
            val y = bounds.bottom - edge - d(3f) * k
            val half = bounds.width * BOTTOM_SPAN * 0.5f
            val left = bounds.center.x - half
            val right = bounds.center.x + half
            val pitch = d(9f) * k
            val phase = (kkSeedFraction(seed, 24) * 4f).toInt()
            var x = left
            var index = 0
            while (x <= right && index < 48) {
                if ((index + phase) % 4 == 0) {
                    capsule(hue, x - d(2f) * k, y, x + d(2f) * k, y, d(3f) * k, oa)
                } else {
                    drawCircle(hue, d(1.5f) * k, Offset(x, y), alpha = oa)
                }
                x += pitch
                index++
            }
        }
        KkCardOrnament.TICKET -> {
            // Ticket notches cut into both side edges (a notch is a short local mark, never a run along the frame).
            val y = bounds.top + bounds.height * (0.52f + kkSeedFraction(seed, 25) * 0.14f)
            val radius = d(7f) * k
            for (side in 0..1) {
                val x = if (side == 0) leftEdge(bounds, cut, y) else rightEdge(bounds, cut, y)
                drawCircle(Kk.Ink, radius, Offset(x, y))
                drawCircle(hue, radius, Offset(x, y), alpha = oa, style = kkStroke(d(1.3f) * k))
                val direction = if (side == 0) 1f else -1f
                for (dash in 0 until 3) {
                    val x0 = x + direction * (radius + d(4f + dash * 6f) * k)
                    drawLine(hue, Offset(x0, y), Offset(x0 + direction * d(3f) * k, y), d(1.3f) * k, alpha = 0.4f)
                }
            }
        }
        KkCardOrnament.PATTERN_CORNER -> {
            // The pattern in the lower left corner, fading out from it, inside the frame's clearance.
            val kind = style.pattern ?: KkFillKind.SLASH
            val corner = Offset(leftEdge(bounds, cut, bounds.bottom) + edge, bounds.bottom - edge)
            val patch = KkRects.of(corner.x, corner.y - bounds.height * CORNER_SHARE, corner.x + bounds.width * CORNER_SHARE, corner.y)
            clipPath(insetSlab(bounds, cut, edge)) {
                val canvas = drawContext.canvas
                canvas.saveLayer(patch, KkLayers.paint)
                drawRect(kkFillBrush(kind, hue), patch.topLeft, patch.size, alpha = CORNER_ALPHA)
                kkApplyEllipseMask(patch, corner.x, corner.y, patch.width, patch.height, 0.2f)
                canvas.restore()
            }
        }
    }
}

/** The corner patch: its share of the card's width and height, and the pattern's strength in it. */
private const val CORNER_SHARE = 0.3f
private const val CORNER_ALPHA = 0.4f

/**
 * A capsule from ([x0], [y0]) to ([x1], [y1]), [width] thick: a round-capped line (allocation-free,
 * unlike a rounded rect, whose radii array Skia allocates per draw).
 */
private fun DrawScope.capsule(color: Color, x0: Float, y0: Float, x1: Float, y1: Float, width: Float, alpha: Float) {
    drawLine(color, Offset(x0, y0), Offset(x1, y1), width, StrokeCap.Round, alpha = alpha)
}
