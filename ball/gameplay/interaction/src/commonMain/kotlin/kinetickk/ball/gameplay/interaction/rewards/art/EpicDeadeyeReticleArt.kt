// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.foundation.design.Kk
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Deadeye Reticle: a long-range scope reticle with range marks. */
internal fun DrawScope.drawDeadeyeReticleArt(frame: RewardArtFrame, @Suppress("UNUSED_PARAMETER") time: Float) {
    // A whole scope low on the right, centred in the room below the text (about 45 % of the card wide
    // when there is room); its range lines run on to the card's edges.
    val g = frame.spot(0.66f, 0.9f, 1.05f)[3]
    val clear = frame.clear
    val top = max(clear.top, frame.textFloor)
    val bottom = frame.bounds.bottom - frame.u(8f)
    val room = (bottom - top).coerceAtLeast(frame.bounds.width * 0.3f)
    val housing = min(frame.bounds.width * 0.45f * 0.5f, room * 0.5f)
    val lens = housing / 1.16f
    val cx = clear.left + clear.width * (if (frame.compact) 0.7f else 0.66f)
    val cy = min(top + room * 0.5f, bottom - housing)
    val cache = frame.cache
    val center = Offset(cx, cy)
    val gap = lens * 0.08f
    val reach = lens * 1.34f
    val posts = cache.path(0) {
        val half = lens * 0.045f
        for (side in 0 until 4) {
            val c = cos(side * HALF_PI)
            val s = sin(side * HALF_PI)
            local(true, cx, cy, c, s, lens * 0.52f, -half)
            local(false, cx, cy, c, s, lens * 1.01f, -half * 1.5f)
            local(false, cx, cy, c, s, lens * 1.01f, half * 1.5f)
            local(false, cx, cy, c, s, lens * 0.52f, half)
            close()
        }
    }
    val hairs = cache.path(1) {
        for (side in 0 until 4) {
            val c = cos(side * HALF_PI)
            val s = sin(side * HALF_PI)
            local(true, cx, cy, c, s, gap, 0f)
            local(false, cx, cy, c, s, lens * 0.52f, 0f)
        }
        // The range lines beyond the housing, out to the card's edges.
        moveTo(frame.bounds.left, cy)
        lineTo(cx - reach, cy)
        moveTo(cx + reach, cy)
        lineTo(frame.bounds.right, cy)
        moveTo(cx, cy + reach)
        lineTo(cx, frame.bounds.bottom)
        moveTo(cx, cy - reach)
        lineTo(cx, cy - reach - lens * 0.9f)
    }
    val marks = cache.path(2) {
        // Mil dots along the hairs, then the holdover ladder under the center.
        for (side in 0 until 4) for (step in 1..3) {
            val c = cos(side * HALF_PI)
            val s = sin(side * HALF_PI)
            val d = gap + (lens * 0.52f - gap) * step / 4f
            val radius = lens * 0.022f
            addOval(Rect(cx + d * c - radius, cy + d * s - radius, cx + d * c + radius, cy + d * s + radius))
        }
        for (step in 0 until 3) {
            val y = cy + lens * (0.16f + step * 0.11f)
            val half = lens * (0.2f - step * 0.05f)
            addRect(Rect(cx - half, y - lens * 0.011f, cx + half, y + lens * 0.011f))
        }
    }
    val ruler = cache.path(3) {
        // Range ticks along the outer lines: long every third.
        val step = lens * 0.22f
        for (tick in 1 until 12) {
            val tall = if (tick % 3 == 0) lens * 0.13f else lens * 0.06f
            val left = cx - reach - tick * step
            if (left > frame.bounds.left) {
                moveTo(left, cy - tall)
                lineTo(left, cy + tall)
            }
            val right = cx + reach + tick * step
            if (right < frame.bounds.right) {
                moveTo(right, cy - tall)
                lineTo(right, cy + tall)
            }
        }
        for (tick in 0 until 36) {
            val angle = tick * 10f * DEG
            val r0 = lens * 1.2f
            val r1 = lens * (if (tick % 3 == 0) 1.3f else 1.25f)
            moveTo(cx + r0 * cos(angle), cy + r0 * sin(angle))
            lineTo(cx + r1 * cos(angle), cy + r1 * sin(angle))
        }
    }
    val glare = cache.path(4) {
        arcTo(Rect(cx - lens * 0.84f, cy - lens * 0.84f, cx + lens * 0.84f, cy + lens * 0.84f), 196f, 58f, true)
    }
    val bone = cache.brush(0) { fadeBrush(frame.hue, cx, cy, lens * 3.4f, 0.4f) }
    val gunmetal = cache.brush(3) { fadeBrush(Kk.Mute2, cx, cy, lens * 3f, 0.5f) }
    val glass = cache.brush(2) {
        Brush.radialGradient(0f to Kk.Bone.copy(alpha = 0.1f), 0.75f to Kk.Bone.copy(alpha = 0.35f), 1f to Kk.Bone.copy(alpha = 0.7f),
            center = Offset(cx - lens * 0.25f, cy - lens * 0.3f), radius = lens * 1.25f)
    }
    drawCircle(glass, lens, center, alpha = RewardArtAlpha.LOW)
    drawCircle(gunmetal, lens * 1.1f, center, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(6f)))
    drawCircle(bone, lens * 1.16f, center, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(1f)))
    drawCircle(bone, lens, center, alpha = RewardArtAlpha.HIGH, style = cache.stroke(frame.u(2.4f)))
    drawPath(ruler, bone, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(1.1f)))
    drawPath(posts, bone, alpha = alphaOf(RewardArtAlpha.HIGH, g))
    drawPath(hairs, bone, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(1.1f)))
    drawPath(marks, bone, alpha = alphaOf(RewardArtAlpha.HIGH, g))
    drawCircle(bone, gap * 0.4f, center, alpha = alphaOf(RewardArtAlpha.HIGH, g))
    drawPath(glare, bone, alpha = alphaOf(RewardArtAlpha.HIGH, g), style = cache.stroke(frame.u(2.6f), StrokeCap.Round))
}
