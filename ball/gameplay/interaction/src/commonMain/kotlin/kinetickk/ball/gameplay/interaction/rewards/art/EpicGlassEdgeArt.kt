// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards.art

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.foundation.design.Kk

// Glass Edge.

/** The blade's spine and edge as quadratic curves (board x, y of the start at the guard, control and end). */
private val GlassSpine = floatArrayOf(80f, 424f, 190f, 384f, 334f, 346f)
private val GlassEdge = floatArrayOf(52f, 392f, 150f, 330f, 334f, 300f)
private const val GlassFlat = 0.08f
private const val GlassRidge = 0.58f
private const val GlassBevel = 0.9f
private const val GlassFacets = 8
private const val GlassBreak = 0.62f

/** The guard at the blade's root: its centre and the blade's direction there (unit). */
private const val GlassGuardX = 66f
private const val GlassGuardY = 408f
private const val GlassDirX = 0.898f
private const val GlassDirY = -0.441f

private fun quadAt(curve: FloatArray, axis: Int, t: Float): Float {
    val m = 1f - t
    return m * m * curve[axis] + 2f * m * t * curve[2 + axis] + t * t * curve[4 + axis]
}

/** Board x and y of the blade point at [t] along it (0 at the guard, 1 past the card) and [s] across it (0 spine, 1 edge). */
private fun glassX(t: Float, s: Float): Float = quadAt(GlassSpine, 0, t) + s * (quadAt(GlassEdge, 0, t) - quadAt(GlassSpine, 0, t))
private fun glassY(t: Float, s: Float): Float = quadAt(GlassSpine, 1, t) + s * (quadAt(GlassEdge, 1, t) - quadAt(GlassSpine, 1, t))

private fun Path.glassMove(t: Float, s: Float) = moveB(glassX(t, s), glassY(t, s))
private fun Path.glassLine(t: Float, s: Float) = lineB(glassX(t, s), glassY(t, s))

/** A run along the blade at [s] from [t0] to [t1], continuing the current contour unless [move]. */
private fun Path.glassRun(s: Float, t0: Float, t1: Float, move: Boolean = true, steps: Int = 24) {
    for (step in 0..steps) {
        val t = t0 + (t1 - t0) * step / steps
        if (step == 0 && move) glassMove(t, s) else glassLine(t, s)
    }
}

/** The band of the blade between [s0] and [s1], closed. */
private fun Path.glassBand(s0: Float, s1: Float) {
    glassRun(s0, 0f, 1.04f)
    glassRun(s1, 1.04f, 0f, move = false)
    close()
}

/** Where facet boundary [index] of the lower ([upper] false) or upper row meets its lower and upper side. */
private fun glassFacetT(frame: RewardArtFrame, index: Int, upper: Boolean, top: Boolean): Float {
    val base = index * 1.06f / GlassFacets + (frame.random(index * 2 + if (upper) 1 else 0) - 0.5f) * 0.35f / GlassFacets
    val shift = if (upper) 0.5f / GlassFacets + 0.045f else 0f
    return (base + shift + if (top) (if (upper) 0.025f else 0.045f) else 0f).coerceIn(0f, 1.04f)
}

/** The long facets of one row ([upper]) whose seeded shade class is [shade] (0..2). */
private fun Path.glassFacets(frame: RewardArtFrame, upper: Boolean, shade: Int) {
    val s0 = if (upper) GlassRidge else GlassFlat
    val s1 = if (upper) GlassBevel else GlassRidge
    for (index in -1 until GlassFacets) {
        val first = index.coerceAtLeast(0)
        if ((index + 1 + (if (upper) 1 else 0) + (frame.random(150 + index + if (upper) 20 else 0) * 2.2f).toInt()) % 3 != shade) continue
        val a0 = if (index < 0) 0f else glassFacetT(frame, first, upper, false)
        val a1 = if (index < 0) 0f else glassFacetT(frame, first, upper, true)
        val b0 = glassFacetT(frame, index + 1, upper, false)
        val b1 = glassFacetT(frame, index + 1, upper, true)
        glassMove(a0, s0)
        glassLine(b0, s0)
        glassLine(b1, s1)
        glassLine(a1, s1)
        close()
    }
}

/** The fracture across the blade at [GlassBreak], from the edge to the spine, with its branches. */
private fun Path.glassFracture() {
    val f = GlassBreak
    glassMove(f, 1f)
    glassLine(f - 0.014f, 0.8f)
    glassLine(f + 0.008f, 0.56f)
    glassLine(f - 0.01f, 0.3f)
    glassLine(f + 0.006f, 0f)
    glassMove(f - 0.014f, 0.8f)
    glassLine(f - 0.05f, 0.62f)
    glassLine(f - 0.085f, 0.46f)
    glassMove(f + 0.008f, 0.56f)
    glassLine(f + 0.045f, 0.42f)
    glassLine(f + 0.07f, 0.24f)
    glassMove(f - 0.01f, 0.3f)
    glassLine(f - 0.04f, 0.14f)
}

/** A point of the guard and grip frame: [along] the blade from the guard's centre, [across] it. */
private fun glassHiltX(along: Float, across: Float): Float = GlassGuardX + along * GlassDirX - across * GlassDirY
private fun glassHiltY(along: Float, across: Float): Float = GlassGuardY + along * GlassDirY + across * GlassDirX

private fun Path.glassHilt(along: Float, across: Float, first: Boolean) =
    if (first) moveB(glassHiltX(along, across), glassHiltY(along, across)) else lineB(glassHiltX(along, across), glassHiltY(along, across))

/** The guard: an angular bar across the blade's root, its ends cut. */
private fun Path.glassGuard() {
    glassHilt(-5f, -27f, true)
    glassHilt(-1f, -33f, false)
    glassHilt(5f, -31f, false)
    glassHilt(5f, 31f, false)
    glassHilt(-1f, 33f, false)
    glassHilt(-5f, 27f, false)
    close()
}

/**
 * Glass Edge: a glass sword rising from its guard at the lower left, the curved blade sweeping
 * across the face past the card's edge: two rows of long crystal facets, a keen bevelled edge with
 * a glint, and one fracture running clean through it, its broken faces catching the light.
 */
internal fun DrawScope.drawGlassEdgeArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val b = EpicABoard.fit(frame, 392f - 150f * 0.58f)
    val hue = frame.hue
    val cache = frame.cache
    val hair = cache.stroke(b.hair)
    val line = cache.stroke(b.line, join = StrokeJoin.Round)
    val keen = cache.stroke(b.l(1.7f), StrokeCap.Round)
    val crack = cache.stroke(b.hair, StrokeCap.Round, StrokeJoin.Round)
    val gap = cache.stroke(b.l(3f), StrokeCap.Round, StrokeJoin.Round)
    fadedArt(frame, 180f, 392f, 210f, 150f, 0.58f) {
        // The grip below the guard, wrapped.
        val grip = cache.path(13) {
            glassHilt(-4f, -6f, true)
            glassHilt(-40f, -6f, false)
            glassHilt(-40f, 6f, false)
            glassHilt(-4f, 6f, false)
            close()
        }
        drawPath(grip, Kk.Ink, alpha = 0.92f)
        drawPath(grip, hue, alpha = 0.05f)
        drawPath(grip, hue, alpha = 0.16f, style = hair)
        drawPath(cache.path(14) {
            for (wrap in 0 until 5) {
                val along = -10f - wrap * 6.5f
                glassHilt(along + 2f, -6f, true)
                glassHilt(along - 2f, 6f, false)
            }
        }, hue, alpha = 0.14f, style = hair)
        // The blade.
        val blade = cache.path(0) { glassBand(0f, 1f) }
        drawPath(blade, Kk.Ink, alpha = 0.9f)
        drawPath(blade, cache.brush(0) {
            Brush.linearGradient(0f to hue.copy(alpha = 0.03f), 0.6f to hue.copy(alpha = 0.07f), 1f to hue.copy(alpha = 0.03f),
                start = b.at(glassX(0.5f, 0f), glassY(0.5f, 0f)), end = b.at(glassX(0.5f, 1f), glassY(0.5f, 1f)))
        })
        for (row in 0 until 2) {
            val upper = row == 1
            drawPath(cache.path(1 + row * 3) { glassFacets(frame, upper, 0) }, hue, alpha = 0.03f)
            drawPath(cache.path(2 + row * 3) { glassFacets(frame, upper, 1) }, hue, alpha = 0.07f)
            drawPath(cache.path(3 + row * 3) { glassFacets(frame, upper, 2) }, hue, alpha = 0.125f)
        }
        drawPath(cache.path(7) {
            for (row in 0 until 2) {
                val upper = row == 1
                val s0 = if (upper) GlassRidge else GlassFlat
                val s1 = if (upper) GlassBevel else GlassRidge
                for (index in 0..GlassFacets) {
                    glassMove(glassFacetT(frame, index, upper, false), s0)
                    glassLine(glassFacetT(frame, index, upper, true), s1)
                }
            }
            glassRun(GlassFlat, 0f, 1.04f)
            glassRun(GlassRidge, 0f, 1.04f)
        }, hue, alpha = 0.08f, style = hair)
        // The edge bevel, bright toward the edge, and the spine.
        drawPath(cache.path(8) { glassBand(GlassBevel, 1f) }, cache.brush(1) {
            Brush.linearGradient(0f to hue.copy(alpha = 0.06f), 1f to hue.copy(alpha = 0.22f),
                start = b.at(glassX(0.55f, GlassBevel), glassY(0.55f, GlassBevel)), end = b.at(glassX(0.55f, 1f), glassY(0.55f, 1f)))
        })
        drawPath(cache.path(9) { glassRun(0f, 0f, 1.04f) }, hue, alpha = 0.18f, style = hair)
        drawPath(cache.path(10) { glassRun(GlassBevel, 0f, 1.04f) }, hue, alpha = 0.14f, style = hair)
        // The keen edge with its glint.
        drawPath(cache.path(11) { glassRun(1f, 0f, 1.04f) }, cache.brush(2) {
            Brush.linearGradient(
                0f to hue.copy(alpha = 0.1f), 0.3f to hue.copy(alpha = 0.2f), 0.46f to hue.copy(alpha = RewardArtAlpha.HIGH), 0.62f to hue.copy(alpha = 0.18f),
                1f to hue.copy(alpha = 0.08f),
                start = b.at(glassX(0f, 1f), glassY(0f, 1f)), end = b.at(glassX(1f, 1f), glassY(1f, 1f)),
            )
        }, style = keen)
        // The guard over the blade's root.
        val guard = cache.path(12) { glassGuard() }
        drawPath(guard, Kk.Ink, alpha = 0.94f)
        drawPath(guard, cache.brush(3) { lightBrush(hue, glassHiltX(0f, -33f), glassHiltY(0f, -33f), glassHiltX(0f, 33f), glassHiltY(0f, 33f), 0.18f, 0.05f) })
        drawPath(guard, hue, alpha = RewardArtAlpha.HIGH, style = line)
        // The fracture: a dark gap with its broken faces lit, and two flakes off the edge.
        val fracture = cache.path(15) { glassFracture() }
        drawPath(fracture, Kk.Ink, alpha = 0.85f, style = gap)
        drawPath(fracture, hue, alpha = RewardArtAlpha.HIGH, style = crack)
        drawPath(cache.path(16) {
            val f = GlassBreak
            glassMove(f + 0.02f, 1.18f)
            glassLine(f + 0.034f, 1.16f)
            glassLine(f + 0.036f, 1.27f)
            glassLine(f + 0.022f, 1.29f)
            close()
            glassMove(f + 0.055f, 1.4f)
            glassLine(f + 0.064f, 1.39f)
            glassLine(f + 0.066f, 1.47f)
            glassLine(f + 0.056f, 1.48f)
            close()
        }, hue, alpha = 0.22f)
    }
}
