// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards.art

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.foundation.design.Kk
import kotlin.math.cos
import kotlin.math.sin

// Redline Governor.

private const val GovernorX = 150f
private const val GovernorY = 426f
private const val GovernorStart = 196f
private const val GovernorEnd = 344f
private const val GovernorRed = 312f
private const val GovernorNeedle = 328f
private const val GovernorSteps = 32

/** Ticks of the gauge: minor ([major] false) or major ones outside the redline, or all inside it ([red]). */
private fun Path.governorTicks(major: Boolean, red: Boolean) {
    for (index in 0..GovernorSteps) {
        val angle = GovernorStart + index * (GovernorEnd - GovernorStart) / GovernorSteps
        val isMajor = index % 4 == 0
        val inRed = angle >= GovernorRed - 0.01f
        if (inRed != red || (!red && isMajor != major)) continue
        val inner = if (isMajor) 105f else 113f
        segB(polarX(GovernorX, inner, angle), polarY(GovernorY, inner, angle), polarX(GovernorX, 122f, angle), polarY(GovernorY, 122f, angle))
    }
}

/**
 * Redline Governor: a tachometer arc rising from the bottom of the card, the needle held in the
 * redline zone (a bright band and dense ticks in the card hue, never the threat colour) with a
 * governor stop pin where the redline starts and a sweep trail behind the needle.
 */
internal fun DrawScope.drawRedlineGovernorArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val b = EpicABoard.fit(frame, 430f - 190f * 0.55f)
    val hue = frame.hue
    val cache = frame.cache
    val hair = cache.stroke(b.hair)
    val line = cache.stroke(b.line)
    val tick = cache.stroke(b.l(2.4f))
    val x = GovernorX
    val y = GovernorY
    fadedArt(frame, x, 430f, 210f, 190f, 0.55f) {
        ovalB(Kk.Ink, x, y, 142f, 142f, 0.7f)
        drawPath(cache.path(0) { annulusB(x, y, 30f, 100f, GovernorStart, GovernorNeedle - GovernorStart) }, cache.brush(0) {
            val s0 = GovernorStart / 360f
            val s1 = GovernorNeedle / 360f
            Brush.sweepGradient(
                0f to Clear, s0 to hue.copy(alpha = 0f), (s0 + s1) * 0.5f to hue.copy(alpha = 0.05f), s1 to hue.copy(alpha = RewardArtAlpha.HIGH),
                s1 + 0.002f to hue.copy(alpha = 0f), 1f to Clear, center = b.at(x, y),
            )
        })
        drawPath(cache.path(1) { annulusB(x, y, 102f, 124f, GovernorRed, GovernorEnd - GovernorRed) }, hue, alpha = 0.07f)
        drawPath(cache.path(2) { arcB(x, y, 134f, 134f, GovernorStart - 5f, GovernorEnd - GovernorStart + 10f, true) }, hue, alpha = RewardArtAlpha.HIGH, style = line)
        drawPath(cache.path(3) {
            arcB(x, y, 140f, 140f, GovernorStart - 3f, GovernorEnd - GovernorStart + 6f, true)
            arcB(x, y, 100f, 100f, GovernorStart, GovernorEnd - GovernorStart, true)
        }, hue, alpha = 0.1f, style = hair)
        drawPath(cache.path(4) { governorTicks(major = false, red = false) }, hue, alpha = 0.2f, style = hair)
        drawPath(cache.path(5) { governorTicks(major = true, red = false) }, hue, alpha = RewardArtAlpha.HIGH, style = tick)
        drawPath(cache.path(6) { governorTicks(major = true, red = true) }, hue, alpha = RewardArtAlpha.HIGH, style = tick)
        // The redline band outside the ticks, and the governor's stop pin where it starts.
        drawPath(cache.path(7) { annulusB(x, y, 125f, 131f, GovernorRed, GovernorEnd - GovernorRed) }, hue, alpha = RewardArtAlpha.HIGH)
        lineB(hue, polarX(x, 98f, GovernorRed), polarY(y, 98f, GovernorRed), polarX(x, 137f, GovernorRed), polarY(y, 137f, GovernorRed), b.l(3f), 0.3f)
        drawPath(cache.path(8) {
            for (index in 0..GovernorSteps step 4) {
                val angle = GovernorStart + index * (GovernorEnd - GovernorStart) / GovernorSteps
                ovalB(polarX(x, 94f, angle), polarY(y, 94f, angle), 1.6f, 1.6f)
            }
        }, hue, alpha = 0.16f)
        // The needle: a slim blade with a square tip and a counterweight, then the hub.
        val needle = cache.path(9) {
            val c = cos(GovernorNeedle * Deg)
            val s = sin(GovernorNeedle * Deg)
            moveB(x - 22f * c + 4.6f * s, y - 22f * s - 4.6f * c)
            lineB(x + 116f * c + 1.3f * s, y + 116f * s - 1.3f * c)
            lineB(x + 116f * c - 1.3f * s, y + 116f * s + 1.3f * c)
            lineB(x - 22f * c - 4.6f * s, y - 22f * s + 4.6f * c)
            close()
        }
        drawPath(needle, Kk.Ink, alpha = Knockout)
        drawPath(needle, hue, alpha = 0.3f)
        lineB(hue, x, y, polarX(x, 112f, GovernorNeedle), polarY(y, 112f, GovernorNeedle), b.hair, 0.3f)
        ovalB(Kk.Ink, x, y, 15f, 15f, 0.9f)
        ovalB(hue, x, y, 15f, 15f, 0.22f, line)
        ovalB(hue, x, y, 22f, 22f, 0.08f, hair)
        ovalB(hue, x, y, 5f, 5f, 0.26f)
    }
}
