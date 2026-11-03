// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import kinetickk.foundation.design.Kk
import kotlin.math.cos
import kotlin.math.sin

/** Dark Star: an accretion disk around a black core. */
internal fun DrawScope.drawDarkStarArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    val spot = frame.spot(0.58f, 0.6f, 1.0f)
    val cx = spot[0]
    val cy = spot[1]
    val r = spot[2]
    val g = spot[3]
    val cache = frame.cache
    val center = Offset(cx, cy)
    val disk = r * 1.25f
    val hole = r * 0.22f
    val inner = r * 0.32f
    val back = cache.path(0) { annulusHalf(cx, cy, inner, disk, upper = true) }
    val front = cache.path(1) { annulusHalf(cx, cy, inner, disk, upper = false) }
    val stars = cache.path(2) {
        for (index in 0 until 7) {
            val angle = (index * 51f + frame.random(70 + index) * 30f) * DEG
            val reach = r * (0.55f + 0.7f * frame.random(80 + index))
            diamond(cx + reach * cos(angle), cy + reach * sin(angle) * 0.85f, r * (0.012f + 0.014f * frame.random(90 + index)))
        }
    }
    val hueColor = frame.hue
    val glow = cache.brush(0) {
        Brush.radialGradient(
            0f to hueColor.copy(alpha = 0f), (inner / disk) * 0.96f to hueColor.copy(alpha = 0f), (inner / disk) to hueColor,
            0.5f to hueColor.copy(alpha = 0.55f), 1f to hueColor.copy(alpha = 0f), center = center, radius = disk,
        )
    }
    val ring = cache.brush(1) { fadeBrush(hueColor, cx, cy, disk, 0.35f) }
    val halo = cache.brush(2) {
        Brush.verticalGradient(0f to hueColor, 0.55f to hueColor.copy(alpha = 0.35f), 1f to hueColor.copy(alpha = 0.7f),
            startY = cy - hole * 1.5f, endY = cy + hole * 1.5f)
    }
    val bone = cache.brush(3) { fadeBrush(Kk.Bone, cx, cy, r * 1.3f, 0.3f) }
    val thin = cache.stroke(frame.u(1f))
    // The far half of the disk, behind the core: the plane tilts up to the right, seen nearly edge on.
    rotate(DarkStarTilt, center) {
        scale(1f, DarkStarSquash, center) {
            drawPath(back, glow, alpha = alphaOf(RewardArtAlpha.HIGH, g))
            for (index in DarkStarRings.indices) {
                val radius = disk * DarkStarRings[index]
                drawArc(ring, 180f, 180f, false, Offset(cx - radius, cy - radius), Size(radius * 2f, radius * 2f),
                    alpha = alphaOf(RewardArtAlpha.HIGH, g), style = thin)
            }
        }
    }
    drawPath(stars, bone, alpha = RewardArtAlpha.MID)
    // The lensed far side of the disk wraps over the core; then the core itself, opaque.
    drawCircle(halo, hole * 1.42f, center, alpha = alphaOf(RewardArtAlpha.LOW, g), style = cache.stroke(frame.u(6f)))
    drawCircle(halo, hole * 1.22f, center, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(2f)))
    drawCircle(Kk.Ink, hole, center)
    drawCircle(Kk.Bone, hole * 1.04f, center, alpha = RewardArtAlpha.MID, style = thin)
    // The near half crosses in front of the core.
    rotate(DarkStarTilt, center) {
        scale(1f, DarkStarSquash, center) {
            drawPath(front, glow, alpha = alphaOf(RewardArtAlpha.HIGH * 1.25f, g))
            drawArc(bone, 0f, 180f, false, Offset(cx - inner, cy - inner), Size(inner * 2f, inner * 2f),
                alpha = RewardArtAlpha.HIGH, style = cache.stroke(frame.u(2f)))
            for (index in DarkStarRings.indices) {
                val radius = disk * DarkStarRings[index]
                drawArc(ring, 0f, 180f, false, Offset(cx - radius, cy - radius), Size(radius * 2f, radius * 2f),
                    alpha = alphaOf(RewardArtAlpha.HIGH, g), style = thin)
            }
            for (index in DarkStarStreaks.indices step 3) {
                val radius = disk * DarkStarStreaks[index]
                drawArc(bone, DarkStarStreaks[index + 1], DarkStarStreaks[index + 2], false, Offset(cx - radius, cy - radius),
                    Size(radius * 2f, radius * 2f), alpha = RewardArtAlpha.HIGH, style = cache.stroke(frame.u(1.6f), StrokeCap.Round))
            }
        }
    }
}

// region Dark Star

private const val DarkStarTilt = -14f
private const val DarkStarSquash = 0.3f
private val DarkStarRings = floatArrayOf(0.31f, 0.36f, 0.42f, 0.5f, 0.6f, 0.72f, 0.86f, 1f)

/** Hot clumps of matter on the disk: radius share, start angle and sweep (near half). */
private val DarkStarStreaks = floatArrayOf(0.4f, 140f, 30f, 0.58f, 24f, 26f, 0.74f, 104f, 38f, 0.5f, 62f, 18f)

/** The upper (far) or lower (near) half of the disk ring between [inner] and [outer]. */
private fun Path.annulusHalf(cx: Float, cy: Float, inner: Float, outer: Float, upper: Boolean) {
    val start = if (upper) 180f else 0f
    arcTo(Rect(cx - outer, cy - outer, cx + outer, cy + outer), start, 180f, true)
    arcTo(Rect(cx - inner, cy - inner, cx + inner, cy + inner), start + 180f, -180f, false)
    close()
}

// endregion
