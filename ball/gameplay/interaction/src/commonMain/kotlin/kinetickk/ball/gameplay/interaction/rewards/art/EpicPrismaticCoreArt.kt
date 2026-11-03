// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.gameplay.interaction.canvas.look.elementFill
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.kkFillBrush
import kinetickk.foundation.design.kkMix
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Prismatic Core: a prism splitting one beam into the element hues. */
internal fun DrawScope.drawPrismaticCoreArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val spot = frame.spot(0.4f, 0.58f, 1.0f)
    val cx = spot[0]
    val cy = spot[1]
    val r = spot[2]
    val g = spot[3]
    val cache = frame.cache
    val side = r * 1.08f
    val geometry = cache.floats(0, PrismFloats) { prismGeometry(it, frame, cx, cy, side, r) }
    val prism = cache.path(0) { triangle(geometry, 0) }
    val inset = cache.path(1) { triangle(geometry, 6) }
    val wedge = cache.path(2) {
        moveTo(geometry[PrismEntry], geometry[PrismEntry + 1])
        lineTo(geometry[PrismExits], geometry[PrismExits + 1])
        lineTo(geometry[PrismExits + 10], geometry[PrismExits + 11])
        close()
    }
    val beamIn = cache.path(3) {
        moveTo(geometry[PrismSource], geometry[PrismSource + 1])
        lineTo(geometry[PrismEntry], geometry[PrismEntry + 1])
    }
    val bone = cache.brush(0) { fadeBrush(Kk.Bone, cx, cy, r * 1.6f, 0.45f) }
    val source = cache.brush(1) {
        Brush.linearGradient(0f to Kk.Bone.copy(alpha = 0.15f), 1f to Kk.Bone,
            start = Offset(geometry[PrismSource], geometry[PrismSource + 1]), end = Offset(geometry[PrismEntry], geometry[PrismEntry + 1]))
    }
    drawPath(beamIn, source, alpha = RewardArtAlpha.LOW, style = cache.stroke(frame.u(10f), StrokeCap.Butt))
    drawPath(beamIn, source, alpha = RewardArtAlpha.HIGH, style = cache.stroke(frame.u(3f), StrokeCap.Butt))
    // Without hue (Mono) the beams step down in lightness instead, so the split still reads.
    val lowChroma = prismLowChroma(frame)
    for (band in 0 until PrismBands) {
        val path = cache.path(4 + band) { prismBand(geometry, band, 0f, 1f) }
        val element = PrismOrder[band]
        val color = if (lowChroma) kkMix(Kk.Bone, Kk.Ink, 0.1f + 0.13f * band) else frame.roles.element(element)
        val brush = cache.brush(2 + band) {
            Brush.linearGradient(0f to color, 0.55f to color.copy(alpha = 0.6f), 1f to color.copy(alpha = 0f),
                start = Offset(geometry[PrismExits + band * 2], geometry[PrismExits + band * 2 + 1]),
                end = Offset(geometry[PrismFar + band * 2], geometry[PrismFar + band * 2 + 1]))
        }
        drawPath(path, brush, alpha = RewardArtAlpha.HIGH)
        // Each beam carries its element's pattern near the prism, fading out in two steps.
        val pattern = kkFillBrush(elementFill(element), color)
        drawPath(cache.path(9 + band * 2) { prismBand(geometry, band, 0f, 0.22f) }, pattern, alpha = RewardArtAlpha.HIGH)
        drawPath(cache.path(10 + band * 2) { prismBand(geometry, band, 0.22f, 0.42f) }, pattern, alpha = RewardArtAlpha.LOW)
    }
    val entry = Offset(geometry[PrismEntry], geometry[PrismEntry + 1])
    val flare = cache.brush(7) { glowBrush(Kk.Bone, entry.x, entry.y, side * 0.32f) }
    drawPath(wedge, bone, alpha = RewardArtAlpha.LOW)
    drawPath(prism, bone, alpha = RewardArtAlpha.LOW * 0.5f)
    drawCircle(flare, side * 0.32f, entry, alpha = RewardArtAlpha.LOW)
    drawPath(inset, bone, alpha = alphaOf(RewardArtAlpha.MID, g), style = cache.stroke(frame.u(0.9f)))
    drawPath(prism, bone, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(2f), join = StrokeJoin.Miter))
}

// region Prismatic Core

private const val PrismBands = 5

/** Element order of the fanned beams, least bent first: thermal, entropy, cryo, ion, gravitic (a spectrum). */
private val PrismOrder = intArrayOf(0, 4, 1, 2, 3)

private const val PrismEntry = 12
private const val PrismSource = 14
private const val PrismExits = 16
private const val PrismFar = 28
private const val PrismFloats = 40

/**
 * The prism (apex up): outline 0..5, inset 6..11, the beam entry 12, its source at the left edge 14,
 * the six exit points along the right face 16..27 and the six far ends of the fan 28..39.
 */
private fun prismGeometry(out: FloatArray, frame: RewardArtFrame, cx: Float, cy: Float, side: Float, r: Float) {
    val height = side * SQRT3_HALF
    val topX = cx
    val topY = cy - height * 0.62f
    val leftX = cx - side * 0.5f
    val rightX = cx + side * 0.5f
    val baseY = cy + height * 0.38f
    out[0] = topX; out[1] = topY
    out[2] = rightX; out[3] = baseY
    out[4] = leftX; out[5] = baseY
    val inset = side * 0.075f
    out[6] = topX; out[7] = topY + inset * 2f
    out[8] = rightX - inset * 1.73f; out[9] = baseY - inset
    out[10] = leftX + inset * 1.73f; out[11] = baseY - inset
    // The white beam meets the left face 45 % up, climbing gently from the card's left edge.
    val ex = leftX + (topX - leftX) * 0.45f
    val ey = baseY + (topY - baseY) * 0.45f
    out[PrismEntry] = ex; out[PrismEntry + 1] = ey
    val sourceX = frame.face.left - side * 0.1f
    out[PrismSource] = sourceX
    out[PrismSource + 1] = ey + (ex - sourceX) * 0.2f
    // Exits from 34 % to 58 % up the right face; the fan spreads from 4° to 30° below the horizon.
    for (k in 0..PrismBands) {
        val f = 0.58f - 0.24f * k / PrismBands
        val x = rightX + (topX - rightX) * f
        val y = baseY + (topY - baseY) * f
        out[PrismExits + k * 2] = x
        out[PrismExits + k * 2 + 1] = y
        val angle = (4f + 26f * k / PrismBands) * DEG
        val reach = r * 2.2f
        out[PrismFar + k * 2] = x + reach * cos(angle)
        out[PrismFar + k * 2 + 1] = y + reach * sin(angle)
    }
}

private fun Path.triangle(f: FloatArray, at: Int) {
    moveTo(f[at], f[at + 1])
    lineTo(f[at + 2], f[at + 3])
    lineTo(f[at + 4], f[at + 5])
    close()
}

/** The stretch of beam [band] from [from] to [to] of its length (0 at the prism face, 1 at its far end). */
private fun Path.prismBand(f: FloatArray, band: Int, from: Float, to: Float) {
    fun x(edge: Int, t: Float): Float = f[PrismExits + edge * 2] + (f[PrismFar + edge * 2] - f[PrismExits + edge * 2]) * t
    fun y(edge: Int, t: Float): Float = f[PrismExits + edge * 2 + 1] + (f[PrismFar + edge * 2 + 1] - f[PrismExits + edge * 2 + 1]) * t
    moveTo(x(band, from), y(band, from))
    lineTo(x(band + 1, from), y(band + 1, from))
    lineTo(x(band + 1, to), y(band + 1, to))
    lineTo(x(band, to), y(band, to))
    close()
}

/** Whether the palette's element ramp has (almost) no hue: Mono, where the beams must split by lightness. */
private fun prismLowChroma(frame: RewardArtFrame): Boolean {
    for (element in 0 until PrismBands) {
        val c = frame.roles.element(element)
        if (max(c.red, max(c.green, c.blue)) - min(c.red, min(c.green, c.blue)) > 0.12f) return false
    }
    return true
}

// endregion
