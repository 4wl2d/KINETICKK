// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Silhouettes of the twelve character forms, keyed by form index 0..11: circle, square, triangle,
 * ring, diamond, tesseract, star, spiral, dipole, pentagon, crescent, hexagon (the icon order of
 * [KkIcon.Forms]). Every builder rewinds and writes a caller-owned [Path] with lines and cubic arcs
 * only, so world, Home, Hangar, Codex and HUD renderers share one geometry without allocating per frame.
 *
 * [radius] is the nominal size: the circle's radius; every other form fits the same circle.
 */
object KkFormGeometry {
    const val COUNT: Int = 12

    const val CIRCLE: Int = 0
    const val SQUARE: Int = 1
    const val TRIANGLE: Int = 2
    const val RING: Int = 3
    const val DIAMOND: Int = 4
    const val TESSERACT: Int = 5
    const val STAR: Int = 6
    const val SPIRAL: Int = 7
    const val DIPOLE: Int = 8
    const val PENTAGON: Int = 9
    const val CRESCENT: Int = 10
    const val HEXAGON: Int = 11

    /** True when the form is a line figure (ring, tesseract, spiral) that is stroked, not filled. */
    fun isStroked(index: Int): Boolean = index == RING || index == TESSERACT || index == SPIRAL

    /** Stroke width a stroked form uses at [radius] (0 for filled forms). */
    fun strokeWidth(index: Int, radius: Float): Float = when (index) {
        RING -> radius * 0.33f
        TESSERACT -> radius * 0.12f
        SPIRAL -> radius * 0.16f
        else -> 0f
    }

    /** Writes form [index] centred at ([cx], [cy]) into [path]. */
    fun build(path: Path, index: Int, cx: Float, cy: Float, radius: Float) {
        require(index in 0 until COUNT) { "form index $index is outside 0..${COUNT - 1}" }
        path.rewind()
        val u = radius / 42f
        when (index) {
            CIRCLE -> circle(path, cx, cy, radius)
            SQUARE -> polygon(path, cx, cy, 28f * u * SQRT2, 4, PI.toFloat() / 4f)
            TRIANGLE -> {
                path.moveTo(cx, cy - 40f * u)
                path.lineTo(cx + 42f * u, cy + 34f * u)
                path.lineTo(cx - 42f * u, cy + 34f * u)
                path.close()
            }
            RING -> circle(path, cx, cy, 36f * u)
            DIAMOND -> polygon(path, cx, cy, 44f * u, 4, -PI.toFloat() / 2f)
            TESSERACT -> {
                square(path, cx - 11f * u, cy - 11f * u, 25f * u)
                square(path, cx + 11f * u, cy + 11f * u, 25f * u)
                for (corner in 0 until 4) {
                    val x = if (corner and 1 == 0) -36f else 14f
                    val y = if (corner and 2 == 0) -36f else 14f
                    path.moveTo(cx + x * u, cy + y * u)
                    path.lineTo(cx + (x + 22f) * u, cy + (y + 22f) * u)
                }
            }
            STAR -> star(path, cx, cy, 44f * u, 19f * u, 5)
            SPIRAL -> spiral(path, cx, cy, 4f * u, 38f * u, SPIRAL_TURNS)
            DIPOLE -> {
                circle(path, cx - 20f * u, cy, 18f * u)
                circle(path, cx + 20f * u, cy, 18f * u)
                path.moveTo(cx - 20f * u, cy - 7f * u)
                path.lineTo(cx + 20f * u, cy - 7f * u)
                path.lineTo(cx + 20f * u, cy + 7f * u)
                path.lineTo(cx - 20f * u, cy + 7f * u)
                path.close()
            }
            PENTAGON -> polygon(path, cx, cy + 3f * u, 42f * u, 5, -PI.toFloat() / 2f)
            CRESCENT -> crescent(path, cx, cy, 42f * u)
            HEXAGON -> polygon(path, cx, cy, 42f * u, 6, -PI.toFloat() / 2f)
        }
    }

    /** A circle of four cubic arcs, clockwise from the top. */
    fun circle(path: Path, cx: Float, cy: Float, radius: Float) {
        val k = radius * KAPPA
        path.moveTo(cx, cy - radius)
        path.cubicTo(cx + k, cy - radius, cx + radius, cy - k, cx + radius, cy)
        path.cubicTo(cx + radius, cy + k, cx + k, cy + radius, cx, cy + radius)
        path.cubicTo(cx - k, cy + radius, cx - radius, cy + k, cx - radius, cy)
        path.cubicTo(cx - radius, cy - k, cx - k, cy - radius, cx, cy - radius)
        path.close()
    }

    /** A regular polygon of [sides] with circumradius [radius]; the first vertex sits at [start] radians. */
    fun polygon(path: Path, cx: Float, cy: Float, radius: Float, sides: Int, start: Float) {
        for (vertex in 0 until sides) {
            val angle = start + vertex * TAU / sides
            val x = cx + cos(angle) * radius
            val y = cy + sin(angle) * radius
            if (vertex == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    private fun square(path: Path, cx: Float, cy: Float, half: Float) {
        path.moveTo(cx - half, cy - half)
        path.lineTo(cx + half, cy - half)
        path.lineTo(cx + half, cy + half)
        path.lineTo(cx - half, cy + half)
        path.close()
    }

    private fun star(path: Path, cx: Float, cy: Float, outer: Float, inner: Float, points: Int) {
        val count = points * 2
        for (vertex in 0 until count) {
            val angle = -PI.toFloat() / 2f + vertex * TAU / count
            val reach = if (vertex % 2 == 0) outer else inner
            val x = cx + cos(angle) * reach
            val y = cy + sin(angle) * reach
            if (vertex == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    private fun spiral(path: Path, cx: Float, cy: Float, inner: Float, outer: Float, turns: Float) {
        val steps = SPIRAL_STEPS
        for (step in 0..steps) {
            val t = step / steps.toFloat()
            val angle = -PI.toFloat() / 2f + t * turns * TAU
            val reach = inner + (outer - inner) * t
            val x = cx + cos(angle) * reach
            val y = cy + sin(angle) * reach
            if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
    }

    /** The outer circle's left arc closed by the left arc of a smaller circle shifted right: horns point right. */
    private fun crescent(path: Path, cx: Float, cy: Float, radius: Float) {
        val shift = radius * 0.5f
        val innerRadius = radius * 0.86f
        // The two circles meet where |p - c| = radius and |p - (c + shift)| = innerRadius.
        val meetX = (shift * shift + radius * radius - innerRadius * innerRadius) / (2f * shift)
        val meetY = sqrt((radius * radius - meetX * meetX).coerceAtLeast(0f))
        val outerStart = atan2(-meetY, meetX)
        val outerSweep = TAU - 2f * atan2(meetY, meetX)
        val steps = CRESCENT_STEPS
        for (step in 0..steps) {
            // From the upper horn counter-clockwise around the left of the outer circle to the lower horn.
            val angle = outerStart - outerSweep * step / steps
            val x = cx + cos(angle) * radius
            val y = cy + sin(angle) * radius
            if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val innerStart = atan2(meetY, meetX - shift)
        var innerSweep = atan2(-meetY, meetX - shift) - innerStart
        if (innerSweep < 0f) innerSweep += TAU
        for (step in 1..steps) {
            // Back to the upper horn around the left of the inner circle.
            val angle = innerStart + innerSweep * step / steps
            path.lineTo(cx + shift + cos(angle) * innerRadius, cy + sin(angle) * innerRadius)
        }
        path.close()
    }

    private const val KAPPA: Float = 0.5522848f
    private const val SQRT2: Float = 1.4142135f
    private const val TAU: Float = (PI * 2.0).toFloat()
    private const val SPIRAL_TURNS: Float = 2.25f
    private const val SPIRAL_STEPS: Int = 54
    private const val CRESCENT_STEPS: Int = 20
}
