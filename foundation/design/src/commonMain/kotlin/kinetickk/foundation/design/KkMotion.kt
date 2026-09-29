// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.animation.core.Easing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Size
import kotlin.math.max

/** Linear interpolation. */
fun kkLerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Fraction 0..1 of a repeating loop of [periodSeconds] at [time] seconds, started after [delay]. */
fun kkLoop(time: Float, periodSeconds: Float, delay: Float = 0f): Float =
    if (periodSeconds <= 0f) 0f else positiveModulo((time - delay) / periodSeconds, 1f)

/** Progress 0..1 of a one-shot animation of [durationMs] started [elapsedMs] ago, eased by [easing]. */
fun kkProgress(elapsedMs: Float, durationMs: Float, easing: Easing? = null): Float {
    val t = if (durationMs <= 0f) 1f else (elapsedMs / durationMs).coerceIn(0f, 1f)
    return easing?.transform(t) ?: t
}

/** Triangle pulse 0 to 1 to 0 over [periodSeconds] (eased by [easing] each half). */
fun kkPulse(time: Float, periodSeconds: Float, easing: Easing = KkEase.Snap): Float {
    val t = kkLoop(time, periodSeconds)
    return easing.transform(if (t < 0.5f) t * 2f else (1f - t) * 2f)
}

/**
 * Screen-change shutter (`kk-shutter`, Motion board): three slabs at the −27.5° slash angle cross
 * the screen left to right, each in [SLAB_MS] with [KkEase.Snap], staggered [STAGGER_MS]. The
 * last (ink, volt-edged) slab covers the whole screen between [COVER_START_MS] and
 * [COVER_END_MS]: swap the screen underneath at [SWAP_MS]; entrances start at [TOTAL_MS].
 */
object KkShutter {
    const val SLAB_MS = KkTime.Shutter
    const val STAGGER_MS = 60
    const val TOTAL_MS = SLAB_MS + 2 * STAGGER_MS
    const val COVER_START_MS = 2 * STAGGER_MS + (SLAB_MS * 45) / 100
    const val COVER_END_MS = 2 * STAGGER_MS + (SLAB_MS * 55) / 100
    const val SWAP_MS = (COVER_START_MS + COVER_END_MS) / 2

    /** True while the ink slab fully covers the screen. */
    fun covered(elapsedMs: Float): Boolean = elapsedMs >= COVER_START_MS && elapsedMs <= COVER_END_MS
}

/**
 * Draws the shutter at [elapsedMs] since it started (nothing outside 0..[KkShutter.TOTAL_MS]).
 * Slab colors: `you` (lead), bone, ink with a 2 px `you` edge. Allocation-free.
 */
fun DrawScope.drawKkShutter(elapsedMs: Float, roles: KkRolePalette) {
    if (elapsedMs <= 0f || elapsedMs >= KkShutter.TOTAL_MS) return
    val w = size.width
    val h = size.height
    val lean = h * KkShape.SlashRatio
    // Two narrow leading slabs (you, bone) ahead of an ink slab wide enough to cover the screen.
    for (index in 0..2) {
        val slabWidth = when (index) {
            0 -> w * 0.16f
            1 -> w * 0.2f
            else -> w + lean + d(8f)
        }
        val t = (elapsedMs - index * KkShutter.STAGGER_MS) / KkShutter.SLAB_MS
        if (t <= 0f || t >= 1f) continue
        // kk-shutter: off-screen left, hold (45-55 %), off-screen right; Snap per segment. The
        // leading slabs hold ahead of the ink slab, which covers the screen during its hold.
        val start = -slabWidth - lean
        val hold = when (index) {
            0 -> w * 0.58f
            1 -> w * 0.3f
            else -> (w - slabWidth) * 0.5f
        }
        val end = w + lean
        val x = when {
            t < 0.45f -> kkLerp(start, hold, KkEase.Snap.transform(t / 0.45f))
            t < 0.55f -> hold
            else -> kkLerp(hold, end, KkEase.Snap.transform((t - 0.55f) / 0.45f))
        }
        // skewX(-27.5deg) around the vertical center: the top edge moves right.
        val topLeft = x + lean * 0.5f
        val bottomLeft = x - lean * 0.5f
        val color = when (index) {
            0 -> roles.you
            1 -> Kk.Bone
            else -> Kk.Ink
        }
        // A slab drawn as a sheared rect: no path rebuild while it moves.
        withTransform({ kkShear(h * 0.5f, KkShape.Slash) }) {
            drawRect(color, Offset(x, 0f), Size(slabWidth, h))
        }
        if (index == 2) {
            val edge = d(2f)
            drawLine(roles.you, Offset(topLeft, 0f), Offset(bottomLeft, h), edge)
            drawLine(roles.you, Offset(topLeft + slabWidth, 0f), Offset(bottomLeft + slabWidth, h), edge)
        }
    }
}

/** Slam transform values (`a-slam` / level badge): scale 1.3 to 1, rotation to [rotationDeg]. */
object KkSlam {
    const val DURATION_MS = KkTime.Pull

    /** Scale at linear [progress] 0..1 (Pull easing, overshoot dips below 1). */
    fun scale(progress: Float): Float = kkLerp(1.3f, 1f, KkEase.Pull.transform(progress.coerceIn(0f, 1f)))

    /** Rotation in degrees at [progress], settling at [rotationDeg]. */
    fun rotation(progress: Float, rotationDeg: Float = -3f): Float =
        kkLerp(rotationDeg - 5f, rotationDeg, KkEase.Pull.transform(progress.coerceIn(0f, 1f)))

    /** Opacity at [progress]: fully visible after the first 40 %. */
    fun alpha(progress: Float): Float = (progress / 0.4f).coerceIn(0f, 1f)
}

/**
 * Draws [block] slammed in around [pivot] at linear [progress] (0 = start, 1 = settled). Callers
 * multiply their alpha by [KkSlam.alpha]. Allocation-free (inline).
 */
inline fun DrawScope.withKkSlam(
    progress: Float,
    pivot: Offset,
    rotationDeg: Float = -3f,
    block: DrawScope.() -> Unit,
) {
    val s = KkSlam.scale(progress)
    rotate(KkSlam.rotation(progress, rotationDeg), pivot) {
        scale(s, s, pivot) { block() }
    }
}

/**
 * Card deal (`kk-deal`): cards fly from an origin (e.g. the Core) with rotation, [KkEase.Pull],
 * 550–750 ms, staggered [STAGGER_MS].
 */
object KkDeal {
    const val DURATION_MS = 620
    const val STAGGER_MS = 90

    /** Linear progress 0..1 of card [index] at [elapsedMs] since the deal started. */
    fun progress(elapsedMs: Float, index: Int, durationMs: Float = DURATION_MS.toFloat()): Float =
        ((elapsedMs - index * STAGGER_MS) / durationMs).coerceIn(0f, 1f)

    /** Opacity at [progress] (visible by 65 %). */
    fun alpha(progress: Float): Float = (progress / 0.65f).coerceIn(0f, 1f)
}

/**
 * Draws [block] dealt from [from] (offset of the origin relative to the card's resting
 * position) at linear [progress]: starts at scale 0.6 rotated −38° and lands at [rotationDeg]
 * around [pivot]. Callers multiply alpha by [KkDeal.alpha]. Allocation-free (inline).
 */
inline fun DrawScope.withKkDeal(
    progress: Float,
    from: Offset,
    pivot: Offset,
    rotationDeg: Float = 0f,
    block: DrawScope.() -> Unit,
) {
    val eased = KkEase.Pull.transform(progress.coerceIn(0f, 1f))
    val s = kkLerp(0.6f, 1f, eased)
    translate(from.x * (1f - eased), from.y * (1f - eased)) {
        rotate(kkLerp(-38f, rotationDeg, eased), pivot) {
            scale(s, s, pivot) { block() }
        }
    }
}

/**
 * Ring burst (`kk-ring`): a ring scaling 0.15 to 1.5 of [radius] and fading out, at linear
 * [progress] 0..1 (Out easing). Used for the legendary deal and confirms. Allocation-free.
 */
fun DrawScope.drawKkRingBurst(center: Offset, radius: Float, progress: Float, color: Color, strokeDp: Float = 3f) {
    if (progress <= 0f || progress >= 1f) return
    val eased = KkEase.Out.transform(progress)
    val r = radius * kkLerp(0.15f, 1.5f, eased)
    drawCircle(color.copy(alpha = color.alpha * (1f - eased)), max(r, 0.5f), center, style = kkStroke(d(strokeDp)))
}
