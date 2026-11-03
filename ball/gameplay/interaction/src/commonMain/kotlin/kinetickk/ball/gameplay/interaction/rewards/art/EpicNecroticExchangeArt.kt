// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards.art

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.foundation.design.Kk
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Necrotic Exchange: rot tendrils weaving through repair hex cells. */
internal fun DrawScope.drawNecroticExchangeArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    // A long text covers most of this card: the honeycomb grows from the lower right corner and fades
    // out well below the name and chips; the rot creeps along the foot and up the right margin.
    val g = frame.spot(0.5f, 0.5f, 1f)[3]
    val cache = frame.cache
    val w = frame.bounds.width
    val h = frame.bounds.height
    val cx = frame.bounds.left + w * 0.8f
    val cy = frame.bounds.top + h * 0.84f
    val r = w * 0.36f
    val a = w * 0.088f
    val outline = cache.path(0) { hexCells(frame, cx, cy, a, HexPart.OUTLINE) }
    val rot = cache.path(1) { hexCells(frame, cx, cy, a, HexPart.ROT) }
    val mend = cache.path(2) { hexCells(frame, cx, cy, a, HexPart.MEND) }
    val samples = cache.floats(0, TendrilCount * 2 * TendrilSamples * 2) {
        tendrilSamples(it, frame.bounds.left, frame.bounds.top, w, h)
    }
    val tendrils = cache.path(3) { tendrils(samples, r) }
    val spores = cache.path(4) {
        for (index in 0 until TendrilCount) {
            for (step in 1 until 4) {
                val sample = index * TendrilSamples * 2 + step * (TendrilSamples * 2 / 4)
                val side = if ((index + step) % 2 == 0) 1f else -1f
                val x = samples[sample * 2] + side * r * 0.07f
                val y = samples[sample * 2 + 1] - r * 0.05f
                val radius = r * (0.016f + 0.01f * step)
                addOval(Rect(x - radius, y - radius, x + radius, y + radius))
            }
        }
    }
    // Faded radially from the lower right: nearly gone above 45 % of the face height.
    val reach = h * 0.5f
    val hue = cache.brush(0) { fadeBrush(frame.hue, cx, cy + h * 0.04f, reach, 0.42f) }
    val bone = cache.brush(1) { fadeBrush(Kk.Bone, cx, cy + h * 0.04f, reach * 0.85f, 0.42f) }
    val creep = cache.brush(2) { fadeBrush(frame.hue, frame.bounds.left + w * 0.62f, frame.bounds.top + h * 0.86f, w * 0.75f, 0.6f) }
    drawPath(rot, hue, alpha = min(alphaOf(RewardArtAlpha.LOW, g), NecroticCellAlpha))
    drawPath(outline, hue, alpha = min(alphaOf(RewardArtAlpha.HIGH, g), NecroticCellAlpha), style = cache.stroke(frame.u(1.3f)))
    drawPath(mend, bone, alpha = NecroticCellAlpha, style = cache.stroke(frame.u(1.6f)))
    // A dark casing cuts the cell lines where a tendril crosses, so the rot reads in front of them.
    drawPath(tendrils, Kk.Ink, alpha = 0.7f, style = cache.stroke(frame.u(3.5f), StrokeCap.Round, StrokeJoin.Round))
    drawPath(tendrils, creep, alpha = alphaOf(RewardArtAlpha.HIGH * 1.15f, g))
    drawPath(spores, creep, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(1.1f)))
}

// region Necrotic Exchange

private enum class HexPart { OUTLINE, ROT, MEND }

private const val HexCols = 9
private const val HexRows = 11

/** What fills a cell at ([i], [j]): 0 a whole repair cell with its mend mark, 1 an eaten cell, 2 an empty one. */
private fun hexKind(frame: RewardArtFrame, i: Int, j: Int): Int {
    val roll = frame.random(200 + j * HexCols + i)
    return when {
        roll < 0.3f -> 1
        roll < 0.52f -> 0
        else -> 2
    }
}

private fun Path.hexCells(frame: RewardArtFrame, cx: Float, cy: Float, a: Float, part: HexPart) {
    val w = a * 1.7320508f
    for (j in 0 until HexRows) for (i in 0 until HexCols) {
        val row = j - HexRows / 2
        val x = cx + (i - HexCols / 2 + 0.5f * (row and 1)) * w
        val y = cy + row * a * 1.5f
        val kind = hexKind(frame, i, j)
        val radius = a * 0.86f
        when (part) {
            HexPart.OUTLINE -> {
                // Eaten cells lose two of their walls.
                for (edge in 0 until 6) {
                    if (kind == 1 && (edge == (i + j) % 6 || edge == (i + j + 2) % 6)) continue
                    val a0 = (-90f + edge * 60f) * DEG
                    val a1 = (-30f + edge * 60f) * DEG
                    moveTo(x + radius * cos(a0), y + radius * sin(a0))
                    lineTo(x + radius * cos(a1), y + radius * sin(a1))
                }
            }
            HexPart.ROT -> if (kind == 1) {
                for (corner in 0 until 6) {
                    val angle = (-90f + corner * 60f) * DEG
                    val px = x + radius * 0.9f * cos(angle)
                    val py = y + radius * 0.9f * sin(angle)
                    if (corner == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
            HexPart.MEND -> if (kind == 0) {
                val arm = a * 0.2f
                moveTo(x - arm, y)
                lineTo(x + arm, y)
                moveTo(x, y - arm)
                lineTo(x, y + arm)
            }
        }
    }
}

private const val TendrilSamples = 14

/** The honeycomb's strongest cell lines and fills: faint enough that the cells never fight the text. */
private const val NecroticCellAlpha = 0.12f

/** Tendrils in card fractions, two cubic segments each: start, two controls, joint, two controls, end (14 floats). */
private val NecroticTendrils = floatArrayOf(
    // From the lower left corner along the foot of the card, then up the right margin.
    -0.02f, 1.0f, 0.3f, 0.92f, 0.5f, 1.03f, 0.74f, 0.9f,
    0.98f, 0.76f, 0.88f, 0.64f, 0.95f, 0.54f,
    // In from the right edge, curling down into the cells of the lower right.
    1.03f, 0.62f, 0.9f, 0.64f, 0.95f, 0.74f, 0.82f, 0.8f,
    0.7f, 0.86f, 0.72f, 0.95f, 0.84f, 0.95f,
    // Up from the foot, winding into the honeycomb.
    0.3f, 1.03f, 0.36f, 0.94f, 0.48f, 0.99f, 0.56f, 0.91f,
    0.64f, 0.84f, 0.6f, 0.78f, 0.7f, 0.75f,
)

private val TendrilCount = NecroticTendrils.size / 14

/** Samples (x, y) along every tendril across the card: [TendrilSamples] per segment, both segments in a row. */
private fun tendrilSamples(out: FloatArray, left: Float, top: Float, width: Float, height: Float) {
    for (t in 0 until TendrilCount) {
        val p = t * 14
        for (segment in 0 until 2) {
            val o = p + segment * 6
            val x0 = NecroticTendrils[o]
            val y0 = NecroticTendrils[o + 1]
            val x1 = NecroticTendrils[o + 2]
            val y1 = NecroticTendrils[o + 3]
            val x2 = NecroticTendrils[o + 4]
            val y2 = NecroticTendrils[o + 5]
            val x3 = NecroticTendrils[o + 6]
            val y3 = NecroticTendrils[o + 7]
            for (step in 0 until TendrilSamples) {
                val u = if (segment == 0) step / TendrilSamples.toFloat() else step / (TendrilSamples - 1f)
                val v = 1f - u
                val bx = v * v * v * x0 + 3f * v * v * u * x1 + 3f * v * u * u * x2 + u * u * u * x3
                val by = v * v * v * y0 + 3f * v * v * u * y1 + 3f * v * u * u * y2 + u * u * u * y3
                val index = ((t * 2 + segment) * TendrilSamples + step) * 2
                out[index] = left + bx * width
                out[index + 1] = top + by * height
            }
        }
    }
}

/** Each tendril as a filled sliver tapering from its root to a fine tip. */
private fun Path.tendrils(samples: FloatArray, r: Float) {
    val count = TendrilSamples * 2
    val tendrils = samples.size / (count * 2)
    for (t in 0 until tendrils) {
        val start = t * count
        for (pass in 0 until 2) {
            for (n in 0 until count) {
                val step = if (pass == 0) n else count - 1 - n
                val index = (start + step) * 2
                val prev = (start + (step - 1).coerceAtLeast(0)) * 2
                val next = (start + (step + 1).coerceAtMost(count - 1)) * 2
                val dx = samples[next] - samples[prev]
                val dy = samples[next + 1] - samples[prev + 1]
                val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
                val progress = step / (count - 1f)
                val half = r * (0.045f * (1f - progress) + 0.004f)
                val side = if (pass == 0) 1f else -1f
                val x = samples[index] - dy / length * half * side
                val y = samples[index + 1] + dx / length * half * side
                if (pass == 0 && n == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        close()
    }
}

// endregion
