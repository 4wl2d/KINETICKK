// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.max
import kotlin.math.min

// region drawing (called by drawKkCard)

/** The resting echo of [material] (dp), and its echo when the card is selected. */
internal fun KkCardMaterial.restingEchoDp(): Float = when (this) {
    KkCardMaterial.CHROME -> 4f
    KkCardMaterial.PRISMATIC -> 8f
    KkCardMaterial.GOLD -> 8f
    else -> 0f
}

internal fun KkCardMaterial.selectedEchoDp(): Float = when (this) {
    KkCardMaterial.CALM -> 0f
    KkCardMaterial.PRISMATIC, KkCardMaterial.GOLD -> 12f
    else -> 10f
}

/** The scale of a card's border and rim: its [cardScale], never under 80 % (a phone card). */
internal fun DrawScope.frameScale(bounds: Rect): Float = frameScaleOf(bounds.width / density)

private fun frameScaleOf(widthDp: Float): Float = max(cardScaleOf(widthDp), 0.8f)

/** The border width of [material] in px at [scale] (a card's [frameScale] by default). */
internal fun DrawScope.borderWidth(material: KkCardMaterial, bounds: Rect, scale: Float = frameScale(bounds)): Float =
    d(material.frame.borderDp) * scale

/** The width of the prismatic inner rim in px at [scale] (scaled like the border). */
private fun DrawScope.prismRimWidth(scale: Float): Float = d(PRISM_RIM_DP) * scale

/**
 * How far in px the frame reaches into the face of [bounds] with its border at [scale]: the border,
 * and the prismatic inner rim inside it at [rimScale].
 */
internal fun DrawScope.frameInnerWidth(material: KkCardMaterial, bounds: Rect, scale: Float = frameScale(bounds), rimScale: Float = scale): Float =
    borderWidth(material, bounds, scale) + if (material == KkCardMaterial.PRISMATIC) prismRimWidth(rimScale) else 0f

/**
 * How far in dp the frame of [material] reaches into the face of a card [widthDp] wide ([frameInnerWidth] at the card's
 * own scale): what text beside the face's edge keeps clear of.
 */
fun kkCardFrameReachDp(material: KkCardMaterial, widthDp: Float): Float =
    (material.frame.borderDp + if (material == KkCardMaterial.PRISMATIC) PRISM_RIM_DP else 0f) * frameScaleOf(widthDp)

/**
 * The face slab of [bounds] inset by [inset] px on every side, its slanted edges parallel to the
 * face's (so a stroke along it reads as an even border).
 */
internal fun insetSlab(bounds: Rect, cut: Float, inset: Float): Path {
    val h = bounds.height
    return KkPathMemo.slab(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset,
        (cut * (h - 2f * inset) / h).coerceAtLeast(0f))
}

/**
 * The soft halo outside the frame (stacked strokes of the face outline, drawn before the face so it
 * covers their inner half): white round chrome, the spectrum round a prismatic card, gold round gold.
 * [scale] shrinks its reach (12 dp at 1; a phone card's is 0.75).
 */
internal fun DrawScope.drawCardHalo(
    face: Path,
    bounds: Rect,
    frame: KkCardFrame,
    scale: Float = if (frameScale(bounds) < 0.9f) 0.75f else 1f,
    gain: Float = 1f,
) {
    if (frame.haloStrength <= 0f) return
    val prism = frame.prism
    val brush = if (prism != null) spectrumSweep(bounds, prism) else null
    for (index in 0 until HaloSteps) {
        val alpha = (HaloAlphas[index] * frame.haloStrength * gain).coerceAtMost(1f)
        val width = d(GlowWidths[index]) * scale
        if (brush != null) drawPath(face, brush, alpha = alpha, style = kkStroke(width))
        else drawPath(face, frame.halo[0], alpha = alpha, style = kkStroke(width))
    }
}

/** The halo uses the first glow widths (4 to 24 dp: it reaches [KK_CARD_HALO_DP] past the frame). */
private const val HaloSteps = 6

/** How far (dp) a board card's halo reaches past its frame. */
internal const val KK_CARD_HALO_DP = 12f
private val HaloAlphas = floatArrayOf(0.2f, 0.13f, 0.085f, 0.055f, 0.032f, 0.016f)

/**
 * The inner glow (stacked strokes of the face outline, the face clip keeping the inner half): cool
 * white on steel, white on chrome, the spectrum on prismatic, gold on gold; none on iron. [scale]
 * shrinks its reach (1 on a card: about 30 dp).
 */
internal fun DrawScope.drawCardGlow(face: Path, bounds: Rect, frame: KkCardFrame, scale: Float = 1f) {
    if (frame.glowStrength <= 0f) return
    val prism = frame.prism
    val brush = if (prism != null) spectrumSweep(bounds, prism) else null
    for (index in GlowWidths.indices) {
        val alpha = (GlowAlphas[index] * frame.glowStrength).coerceAtMost(1f)
        val width = d(GlowWidths[index]) * scale
        if (brush != null) drawPath(face, brush, alpha = alpha, style = kkStroke(width))
        else drawPath(face, frame.glow[0], alpha = alpha, style = kkStroke(width))
    }
}

// Twelve nested strokes approximate `inset 0 0 46px -14px` (strongest at the rim, gone by ~30 dp).
private val GlowWidths = floatArrayOf(4f, 8f, 12f, 16f, 20f, 24f, 28f, 32f, 38f, 44f, 52f, 60f)
private val GlowAlphas = floatArrayOf(0.05f, 0.045f, 0.04f, 0.035f, 0.03f, 0.026f, 0.022f, 0.018f, 0.014f, 0.011f, 0.008f, 0.006f)

/**
 * The echo slab(s) behind the face, [echo] px down and right: iron and steel slabs when selected,
 * one chrome slab, a spectrum slab with a bone rim behind it (prismatic), striped gold. Every slab
 * is the face's own cached outline moved by [echo] (and its brushes keyed by the card alone), so a
 * selection tween that changes [echo] every frame builds no path or brush.
 */
internal fun DrawScope.drawCardEcho(bounds: Rect, cut: Float, echo: Float, frame: KkCardFrame, time: Float) {
    if (echo <= 0.25f) return
    val face = KkPathMemo.slab(bounds, cut)
    when (frame.material) {
        KkCardMaterial.CALM -> Unit
        KkCardMaterial.IRON, KkCardMaterial.STEEL -> translate(echo, echo) {
            drawPath(face, frame.echo[0])
            drawPath(face, frame.echo[1], alpha = 0.8f, style = kkStroke(d(1f)))
        }
        KkCardMaterial.CHROME -> translate(echo, echo) {
            drawPath(face, chromeEchoBrush(bounds))
            drawPath(face, FrameWhite, alpha = 0.75f, style = kkStroke(d(1f)))
        }
        KkCardMaterial.PRISMATIC -> {
            // The bone offset behind: a bright rim over a faint glass fill.
            val back = echo + d(4f)
            translate(back, back) {
                drawPath(face, Kk.Bone, alpha = 0.12f)
                drawPath(face, Kk.Bone, alpha = 0.85f, style = kkStroke(d(2f)))
            }
            // The spectrum slab, its colours sliding along it (anchored to the card, not the slab).
            val tile = prismTile(bounds)
            val shift = -kkLoop(time, 6f) * tile
            val brush = spectrumLinear(tile, frame.prism ?: KkSpectrum.Full)
            translate(echo, echo) {
                clipPath(face) {
                    translate(shift - echo, -echo) {
                        drawRect(brush, Offset(bounds.left - shift + echo, bounds.top + echo), bounds.size)
                    }
                }
                drawPath(face, FrameWhite, alpha = 0.18f * kkPulse(time, 1.6f, KkEase.Out))
            }
        }
        KkCardMaterial.GOLD -> {
            val gold = frame.gold ?: KkGold.Bright
            translate(echo, echo) {
                clipPath(face) {
                    // The stripes stay anchored to the card, so they never slide while the echo grows.
                    translate(-echo, -echo) {
                        kkDrawStripes(bounds.left + echo, bounds.top + echo, bounds.right + echo, bounds.bottom + echo,
                            gold.base, gold.stripe, time, KkFillKind.CARD_STRIPES)
                    }
                }
            }
        }
    }
}

/** The band fill of [material] inside [path] (bounds [band]); labels are drawn by the caller. */
internal fun DrawScope.drawCardBandMaterial(path: Path, band: Rect, frame: KkCardFrame, time: Float) {
    val k = (band.height / density / 42f).coerceIn(0.6f, 1.25f)
    val material = frame.material
    when (material) {
        KkCardMaterial.CALM -> {
            drawPath(path, Kk.Ink3)
            drawRect(Kk.Line2, Offset(band.left, band.bottom - d(1f)), Size(band.width - d(9f), d(1f)))
        }
        KkCardMaterial.IRON -> clipPath(path) {
            // Matte iron: a dark vertical ramp, faint hairlines, a lit lip and a mid-grey edge into the face.
            drawRect(bandBrush(material, band), band.topLeft, band.size)
            drawKkPattern(band, KkFillKind.HAIRLINE, Kk.Bone, IRON_HAIRLINE)
            drawRect(IronLip, band.topLeft, Size(band.width, d(1f)), alpha = 0.7f)
            val edge = max(d(1.5f), d(2f) * k)
            drawRect(IronEdge, Offset(band.left, band.bottom - edge), Size(band.width, edge))
        }
        KkCardMaterial.STEEL -> clipPath(path) {
            // Brushed steel: a lit bevel above the label zone, the mid-grey zone, bright brushing, a dark edge into the face.
            drawRect(bandBrush(material, band), band.topLeft, band.size)
            drawKkPattern(band, KkFillKind.HAIRLINE, Kk.Bone, STEEL_HAIRLINE)
            drawRect(FrameWhite, band.topLeft, Size(band.width, d(1f)), alpha = 0.7f)
            val edge = max(d(2f), d(2.5f) * k)
            drawRect(SteelEdge, Offset(band.left, band.bottom - edge), Size(band.width, edge))
        }
        KkCardMaterial.CHROME -> {
            clipPath(path) {
                // Polished chrome: a white rim, the bright middle a label reads on, a dark horizon, reflected light below.
                drawRect(bandBrush(material, band), band.topLeft, band.size)
                val zone = labelZoneTop(band)
                drawRect(FrameWhite, Offset(band.left, band.top + band.height * zone * 0.42f), Size(band.width, d(1f)), alpha = 0.95f)
                drawRect(Kk.Ink, Offset(band.left, band.bottom - d(1f)), Size(band.width, d(1f)), alpha = 0.55f)
            }
            drawKkSheen(path, band, time, 3.4f, FrameWhite.copy(alpha = CHROME_SHEEN), bandFraction = 0.3f, startFraction = -0.4f)
        }
        KkCardMaterial.PRISMATIC -> {
            drawPath(path, Kk.Ink)
            clipPath(path) {
                // A faint glass reflection; a refraction streak crossing the glass every 5 s; the spectrum
                // top edge (3 dp on the board, 2 dp on phones) with its soft glow, sliding over 6 s.
                drawRect(Kk.Bone, Offset(band.left, band.top), Size(band.width, band.height * 0.45f), alpha = PRISM_GLASS_ALPHA)
                val tile = prismTile(band)
                val shift = -kkLoop(time, 6f) * tile
                val brush = spectrumLinear(tile, frame.prism ?: KkSpectrum.Full)
                val streak = kkLoop(time, 5f)
                val streakWidth = d(9f) * k
                val sx = band.left - band.height + (band.width + band.height * 2f) * streak
                translate(shift, 0f) {
                    // The streak leans like the card's slash cut; the brush stays anchored, so its colours slide.
                    rotate(PRISM_STREAK_DEGREES, Offset(sx - shift, band.center.y)) {
                        drawRect(brush, Offset(sx - shift - streakWidth * 0.5f, band.top - band.height), Size(streakWidth, band.height * 3f),
                            alpha = PRISM_STREAK_ALPHA)
                    }
                    val edge = max(d(2f), d(3f) * k)
                    drawRect(brush, Offset(band.left - shift, band.top), Size(band.width, edge + d(6f) * k), alpha = 0.11f)
                    drawRect(brush, Offset(band.left - shift, band.top), Size(band.width, edge + d(3f) * k), alpha = 0.2f)
                    drawRect(brush, Offset(band.left - shift, band.top), Size(band.width, edge))
                    // A spectrum lip along the bottom edge as well.
                    drawRect(brush, Offset(band.left - shift, band.bottom - d(1.5f)), Size(band.width, d(1.5f)), alpha = 0.85f)
                }
            }
        }
        KkCardMaterial.GOLD -> drawKkFoil(path, band, time, 2.2f, gold = frame.gold ?: KkGold.Bright)
    }
}

/**
 * The frame border round [bounds] (the face slab with its [cut]), drawn over the band so it frames
 * the whole card: iron, a steel bevel, chrome with a glint running round it, the turning spectrum
 * (one turn per [KkTime.AmbientMin]) with a bright bone rim inside it, and moving gold foil, each
 * with an ink seam inside it.
 */
internal fun DrawScope.drawCardBorder(bounds: Rect, cut: Float, frame: KkCardFrame, time: Float, scale: Float = frameScale(bounds), rimScale: Float = scale) {
    val material = frame.material
    if (frame.borderDp <= 0f) return
    val width = borderWidth(material, bounds, scale)
    when (material) {
        KkCardMaterial.PRISMATIC -> {
            val ring = KkCardRings.ring(bounds, cut, width)
            val turn = time * 360f / (KkTime.AmbientMin / 1000f)
            val reach = bounds.width + bounds.height
            val sweep = spectrumSweep(bounds, frame.prism ?: KkSpectrum.Full)
            clipPath(ring) {
                rotate(turn, bounds.center) {
                    drawRect(sweep, Offset(bounds.center.x - reach, bounds.center.y - reach), Size(reach * 2f, reach * 2f))
                }
            }
            // The bright rim just inside the spectrum: the epic frame outshines chrome in lightness, not only in colour.
            val rim = prismRimWidth(rimScale)
            drawPath(insetSlab(bounds, cut, width + rim * 0.5f), PrismRim, style = kkStroke(rim))
        }
        KkCardMaterial.GOLD -> drawKkFoil(KkCardRings.ring(bounds, cut, width), bounds, time, 2.4f, gold = frame.gold ?: KkGold.Bright)
        else -> drawPath(insetSlab(bounds, cut, width * 0.5f), borderBrush(material, bounds), style = kkStroke(width))
    }
    // The ink seam inside the border (and the prismatic rim), so it reads as a raised edge over the face.
    drawPath(insetSlab(bounds, cut, frameInnerWidth(material, bounds, scale, rimScale) + d(0.5f)), frame.gold?.seam ?: Kk.Ink,
        alpha = if (material == KkCardMaterial.GOLD) 0.8f else 0.6f, style = kkStroke(d(1f)))
    if (material == KkCardMaterial.CHROME || material == KkCardMaterial.GOLD) {
        // A glint running round the border every 3.4 s.
        drawKkSheen(KkCardRings.ring(bounds, cut, width), bounds, time, 3.4f, FrameWhite.copy(alpha = 0.95f), bandFraction = 0.2f,
            startFraction = -0.3f)
    }
}

/** A gradient border brush over [bounds] (iron top to bottom, steel and chrome along the diagonal). */
private fun borderBrush(material: KkCardMaterial, bounds: Rect): Brush =
    KkFrameBrushes.get(material.ordinal * PARTS + PART_BORDER, bounds.left, bounds.top, bounds.right, bounds.bottom) {
        when (material) {
            KkCardMaterial.IRON -> Brush.verticalGradient(listOf(IronBorderTop, IronBorderBottom), startY = bounds.top, endY = bounds.bottom)
            else -> Brush.linearGradient(material.frame.border, start = bounds.topLeft, end = bounds.bottomRight)
        }
    }

/** The vertical band ramp of iron, steel and chrome over [band], keeping only dark (or only bright) tones in the label zone. */
private fun DrawScope.bandBrush(material: KkCardMaterial, band: Rect): Brush {
    val zone = labelZoneTop(band)
    return KkFrameBrushes.get(material.ordinal * PARTS + PART_BAND, band.top, band.bottom, zone, 0f) {
        when (material) {
            KkCardMaterial.CHROME -> Brush.verticalGradient(
                0f to ChromeBand[0],
                zone * 0.6f to ChromeBand[1],
                zone to ChromeBand[2],
                0.5f to ChromeBand[3],
                1f - zone to ChromeBand[4],
                1f - zone * 0.4f to ChromeBand[5],
                1f to ChromeBand[6],
                startY = band.top, endY = band.bottom,
            )
            else -> {
                val tones = if (material == KkCardMaterial.STEEL) SteelBand else IronBand
                Brush.verticalGradient(
                    0f to tones[0],
                    zone * 0.6f to tones[1],
                    zone to tones[2],
                    0.5f to tones[3],
                    1f - zone to tones[4],
                    1f to tones[5],
                    startY = band.top, endY = band.bottom,
                )
            }
        }
    }
}

/** The chrome echo slab's polish along the card's diagonal (the slab is the face moved by its echo). */
private fun chromeEchoBrush(bounds: Rect): Brush =
    KkFrameBrushes.get(KkCardMaterial.CHROME.ordinal * PARTS + PART_ECHO, bounds.left, bounds.top, bounds.right, bounds.bottom) {
        Brush.linearGradient(ChromeEcho, start = bounds.topLeft, end = bounds.bottomRight)
    }

/** [spectrum] swept round the center of [bounds] (prismatic border, halo and glow). */
private fun spectrumSweep(bounds: Rect, spectrum: KkSpectrum): Brush =
    KkFrameBrushes.get(KkCardMaterial.PRISMATIC.ordinal * PARTS + PART_SWEEP, bounds.center.x, bounds.center.y, spectrum.id.toFloat(), 0f) {
        Brush.sweepGradient(spectrum.loop, center = bounds.center)
    }

/** [spectrum] as a gradient repeating every [tile] px from x = 0 (prismatic band edge, echo and halftone). */
internal fun spectrumLinear(tile: Float, spectrum: KkSpectrum): Brush =
    KkFrameBrushes.get(KkCardMaterial.PRISMATIC.ordinal * PARTS + PART_LINEAR, tile, spectrum.id.toFloat(), 0f, 0f) {
        Brush.linearGradient(spectrum.loop, start = Offset(0f, 0f), end = Offset(tile, tile * 0.08f), tileMode = TileMode.Repeated)
    }

/** [spectrum]'s label gradient repeating every [tile] px from x = 0 (its stops lifted to read on the glass). */
internal fun prismLabelBrush(tile: Float, spectrum: KkSpectrum): Brush =
    KkFrameBrushes.get(KkCardMaterial.PRISMATIC.ordinal * PARTS + PART_LABEL, tile, spectrum.id.toFloat(), 0f, 0f) {
        Brush.linearGradient(spectrum.label, start = Offset(0f, 0f), end = Offset(tile, 0f), tileMode = TileMode.Repeated)
    }

/** Width of the repeating spectrum over a band or card. */
internal fun prismTile(band: Rect): Float = max(band.width * 1.4f, 1f)

/** Width of the repeating prism gradient over a band label [labelWidth] wide: two of them, so several stops cross the word. */
internal fun prismLabelTile(labelWidth: Float): Float = max(labelWidth * 2f, 1f)

/**
 * Half the height (dp) a band label's glyphs cover round the band's middle, with a margin: metal
 * bands keep only their label-zone tones inside it ([labelZoneTop]).
 */
private const val LABEL_HALF_DP = 7f

/** The top of [band]'s label zone as a share of its height (the zone is symmetric round the middle). */
private fun DrawScope.labelZoneTop(band: Rect): Float = (0.5f - d(LABEL_HALF_DP) / band.height).coerceIn(0.04f, 0.5f)

/**
 * The rising halftone of the face: the spectrum's dots on a prismatic card (masked from below,
 * drifting up 36 dp every 6 s), gold dots on a gold one.
 */
internal fun DrawScope.drawCardHalftone(bounds: Rect, frame: KkCardFrame, time: Float) {
    val shift = -kkLoop(time, 6f) * d(36f)
    val canvas = drawContext.canvas
    canvas.saveLayer(bounds, KkLayers.paint)
    val prismatic = frame.material == KkCardMaterial.PRISMATIC
    translate(0f, shift) {
        val dots = if (prismatic) FrameWhite.copy(alpha = 0.26f) else (frame.gold ?: KkGold.Bright).base.copy(alpha = 0.3f)
        drawRect(kkFillBrush(KkFillKind.HALFTONE, dots), Offset(bounds.left, bounds.top - shift), bounds.size)
    }
    if (prismatic) {
        // The dots take the spectrum, sliding across the card with the band edge.
        val tile = prismTile(bounds)
        val slide = -kkLoop(time, 6f) * tile
        val brush = spectrumLinear(tile, frame.prism ?: KkSpectrum.Full)
        translate(slide, 0f) {
            drawRect(brush, Offset(bounds.left - slide, bounds.top), bounds.size, blendMode = BlendMode.SrcIn)
        }
    }
    kkApplyEllipseMask(bounds, bounds.center.x, bounds.top + bounds.height * 1.08f,
        bounds.width * 1.2f * 0.7f, bounds.height * 0.7f * 0.7f, 0.2f)
    canvas.restore()
}

private val SparkLeft = floatArrayOf(0.18f, 0.42f, 0.66f, 0.84f, 0.30f)
private val SparkDelay = floatArrayOf(0.2f, 1.1f, 0.6f, 1.7f, 2.2f)
private val SparkSize = floatArrayOf(7f, 5f, 7f, 5f, 7f)

/** Spectrum stop of each spark on a prismatic card (spread round the loop; wraps on a shorter spectrum). */
private val SparkSpectrum = intArrayOf(0, 3, 6, 1, 4)

/**
 * Five rising diamonds (whole squares turned 45°, never a cut spark): spectrum colours on a
 * prismatic card, gold on gold; the card's [seed] moves them sideways. [scale] shrinks their size
 * and rise (1 on a card).
 */
internal fun DrawScope.drawCardSparks(bounds: Rect, frame: KkCardFrame, time: Float, seed: Int, scale: Float = 1f) {
    for (index in 0 until 5) {
        val p = kkLoop(time, 2.8f, SparkDelay[index])
        val opacity = if (p < 0.2f) p / 0.2f else 1f - (p - 0.2f) / 0.8f
        val size = d(SparkSize[index]) * scale
        val jitter = if (seed == 0) 0f else (kkSeedFraction(seed, 10 + index) - 0.5f) * 0.12f
        val cx = bounds.left + bounds.width * (SparkLeft[index] + jitter) + size * 0.5f
        val cy = bounds.bottom + (d(12f) - d(120f) * p) * scale - size * 0.5f
        val half = size * 0.5f * 0.7071f
        val prism = frame.prism
        val color = if (prism != null) prism.stop(SparkSpectrum[index]) else (frame.gold ?: KkGold.Bright).base
        rotate(45f, Offset(cx, cy)) {
            drawRect(color, Offset(cx - half, cy - half), Size(half * 2f, half * 2f), alpha = opacity.coerceIn(0f, 1f))
        }
    }
}

/**
 * Prismatic: two thin bone facet lines in the face's margins, clear of the text column, and a
 * short, faint glint running along them every 3.2 s (inside the face clip). The [seed] sets where
 * the facets run, so the glint crosses each epic in its own place; facet B keeps below a measured
 * [textBottom].
 */
internal fun DrawScope.drawPrismFacets(bounds: Rect, bandBottom: Float, seed: Int, time: Float, textBottom: Float = Float.NaN) {
    val w = bounds.width
    val h = bounds.height
    val cut = d(CARD_FACET_CUT_DP)
    // The slanted face leaves its widest margins at the upper right and the lower left. Facet A runs
    // from under the band down the right margin, outside the text column; facet B crosses the lower
    // body below the text, from the left edge down to the bottom edge.
    val ay0 = bandBottom
    val ay1 = bounds.top + h * (0.48f + kkSeedFraction(seed, 41) * 0.22f)
    val margin = d(FACET_MARGIN_DP) * cardScale(bounds)
    val ax0 = rightEdge(bounds, cut, ay0) - margin * (0.55f + 0.35f * kkSeedFraction(seed, 40))
    val ax1 = rightEdge(bounds, cut, ay1) - margin * 0.15f
    val lowest = bounds.top + h * 0.9f
    val floor = if (textBottom.isNaN()) bounds.top + h * 0.72f else max(bounds.top + h * 0.62f, textBottom + d(10f))
    val by0 = min(floor + h * kkSeedFraction(seed, 42) * 0.06f, lowest)
    val bx0 = bounds.left
    val bx1 = bounds.left + w * (0.5f + kkSeedFraction(seed, 44) * 0.3f)
    val by1 = bounds.bottom
    val line = d(1f)
    drawLine(Kk.Bone, Offset(ax0, ay0), Offset(ax1, ay1), line, alpha = 0.1f)
    drawLine(Kk.Bone, Offset(bx0, by0), Offset(bx1, by1), line, alpha = 0.1f)
    drawLine(Kk.Bone, Offset(ax0 + d(2f), ay0), Offset(ax1 + d(2f), ay1), line, alpha = 0.06f)
    val p = kkLoop(time, 3.2f)
    // The glint crosses facet A in the first 40 % of the loop and facet B in the next 40 %.
    if (p < 0.8f) {
        val onA = p < 0.4f
        val t = if (onA) p / 0.4f else (p - 0.4f) / 0.4f
        val x0 = if (onA) ax0 else bx0
        val y0 = if (onA) ay0 else by0
        val x1 = if (onA) ax1 else bx1
        val y1 = if (onA) ay1 else by1
        val dx = x1 - x0
        val dy = y1 - y0
        val length = kotlin.math.sqrt(dx * dx + dy * dy)
        val glint = length * GLINT_LENGTH
        val angle = kotlin.math.atan2(dy, dx) * 180f / PI_F
        val travel = -glint + (length + glint) * t
        translate(x0, y0) {
            rotate(angle, Offset.Zero) {
                // The cached unit sheen gradient stretched over the glint: no brush per length.
                translate(travel, 0f) {
                    scale(glint, 1f, Offset.Zero) {
                        drawRect(KkGradients.sheen(Kk.Bone), Offset(0f, -d(1.5f)), Size(1f, d(3f)), alpha = GLINT_ALPHA)
                    }
                }
            }
        }
    }
}

/** The card's face cut (dp), as [drawKkCard] draws it. */
private const val CARD_FACET_CUT_DP = 22f

/** How far (board dp) facet A keeps inside the side edge at its top: the margin outside the text column. */
private const val FACET_MARGIN_DP = 22f

/** The glint's share of its facet's length and its peak opacity: a passing gleam, never a scratch across the art. */
private const val GLINT_LENGTH = 0.22f
private const val GLINT_ALPHA = 0.3f

/** The scale of a card's frame and ornaments: 1 on a 300 dp board card. */
internal fun DrawScope.cardScale(bounds: Rect): Float = cardScaleOf(bounds.width / density)

private fun cardScaleOf(widthDp: Float): Float = (widthDp / 300f).coerceIn(0.6f, 1.25f)

/** x of the face's slanted left edge at [y]. */
internal fun leftEdge(bounds: Rect, cut: Float, y: Float): Float = bounds.left + cut * (1f - (y - bounds.top) / bounds.height)

/** x of the face's slanted right edge at [y]. */
internal fun rightEdge(bounds: Rect, cut: Float, y: Float): Float = bounds.right - cut * ((y - bounds.top) / bounds.height)

// endregion

// Brush cache parts (each material has PARTS slots of kinds).
private const val PARTS = 16
private const val PART_BORDER = 0
private const val PART_BAND = 1
private const val PART_ECHO = 2
private const val PART_SWEEP = 3
private const val PART_LINEAR = 4
private const val PART_LABEL = 5

/**
 * Frame brushes by kind (material and part) and geometry: every card of a material at one size
 * shares them, so a dealt hand draws without allocating. Draw thread only.
 */
internal object KkFrameBrushes {
    @PublishedApi internal const val CAPACITY = 64
    @PublishedApi internal val kinds = IntArray(CAPACITY) { -1 }
    @PublishedApi internal val keys = FloatArray(CAPACITY * 4)
    @PublishedApi internal val brushes = arrayOfNulls<Brush>(CAPACITY)
    @PublishedApi internal var next = 0

    inline fun get(kind: Int, a: Float, b: Float, c: Float, d: Float, create: () -> Brush): Brush {
        for (index in 0 until CAPACITY) {
            if (kinds[index] != kind) continue
            val base = index * 4
            if (keys[base] == a && keys[base + 1] == b && keys[base + 2] == c && keys[base + 3] == d) return brushes[index]!!
        }
        val created = create()
        val slot = next
        kinds[slot] = kind
        val base = slot * 4
        keys[base] = a
        keys[base + 1] = b
        keys[base + 2] = c
        keys[base + 3] = d
        brushes[slot] = created
        next = (slot + 1) % CAPACITY
        return created
    }
}

/**
 * The border rings (the face outline less the face inset by the border width, even-odd) by
 * geometry, reused while it holds. Draw thread only.
 */
private object KkCardRings {
    private const val CAPACITY = 8
    private val keys = FloatArray(CAPACITY * 6) { Float.NaN }
    private val paths = arrayOfNulls<Path>(CAPACITY)
    private var next = 0

    fun ring(bounds: Rect, cut: Float, width: Float): Path {
        for (index in 0 until CAPACITY) {
            val base = index * 6
            if (keys[base] == bounds.left && keys[base + 1] == bounds.top && keys[base + 2] == bounds.right &&
                keys[base + 3] == bounds.bottom && keys[base + 4] == cut && keys[base + 5] == width
            ) return paths[index]!!
        }
        val slot = next
        val path = paths[slot] ?: Path().also { paths[slot] = it }
        path.rewind()
        path.fillType = PathFillType.EvenOdd
        quad(path, bounds.left, bounds.top, bounds.right, bounds.bottom, cut)
        val h = bounds.height
        quad(path, bounds.left + width, bounds.top + width, bounds.right - width, bounds.bottom - width, cut * (h - 2f * width) / h)
        val base = slot * 6
        keys[base] = bounds.left
        keys[base + 1] = bounds.top
        keys[base + 2] = bounds.right
        keys[base + 3] = bounds.bottom
        keys[base + 4] = cut
        keys[base + 5] = width
        paths[slot] = path
        next = (slot + 1) % CAPACITY
        return path
    }

    /** The face slab quad (top-left and bottom-right corners cut by [cut]) as one closed contour. */
    private fun quad(path: Path, left: Float, top: Float, right: Float, bottom: Float, cut: Float) {
        path.moveTo(left + cut, top)
        path.lineTo(right, top)
        path.lineTo(right - cut, bottom)
        path.lineTo(left, bottom)
        path.close()
    }
}
