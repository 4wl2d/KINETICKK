// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world

import kinetickk.foundation.design.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.world.places.*

/**
 * A Break's own figure in its element at [progress] of its life, at [strength] of its brightness
 * (the reaction effects setting's peak alpha). Merged Breaks ([weight]) burn brighter.
 */
internal fun DrawScope.drawBreakBurst(
    element: Int,
    center: Offset,
    reach: Float,
    progress: Float,
    color: androidx.compose.ui.graphics.Color,
    weight: Int,
    strength: Float = 1f,
) {
    val out = KkEase.Out.transform(progress)
    val fade = (1f - progress) * strength
    val bright = (0.7f + 0.1f * weight).coerceAtMost(1f) * strength
    if (progress < 0.22f) drawCircle(color, reach * (0.28f + out * 0.3f), center, alpha = (1f - progress / 0.22f) * 0.4f * strength)
    when (element) {
        // Ignite: the stacks explode outward; flame ticks ride the ring.
        0 -> {
            val radius = reach * out
            drawCircle(color, radius, center, alpha = fade * bright, style = kkStroke(3f))
            for (tick in 0 until 10) {
                val angle = tick * TAU / 10f + progress * 0.6f
                drawLine(color.copy(alpha = fade * bright), polar(center, radius * 0.72f, angle), polar(center, radius * 1.08f, angle), 2f)
            }
        }
        // Freeze: a hexagon locks around the target and six shards fly off its corners.
        1 -> {
            val radius = reach * (0.55f + 0.45f * out)
            for (side in 0 until 6) {
                val a = side * TAU / 6f + 0.52f
                drawLine(color.copy(alpha = fade * bright), polar(center, radius, a), polar(center, radius, a + TAU / 6f), 2.5f)
                drawLine(Kk.Bone.copy(alpha = fade * 0.8f), polar(center, radius * 1.05f, a), polar(center, radius * (1.15f + out * 0.4f), a), 1.5f)
            }
        }
        // Overload: lightning forks out from the target.
        2 -> {
            drawCircle(color, reach * out * 0.6f, center, alpha = fade * 0.6f, style = WorldStrokes.dashedThin)
            for (bolt in 0 until 6) {
                val a = bolt * TAU / 6f + 0.3f
                val length = reach * (0.35f + 0.65f * out)
                var previous = polar(center, length * 0.2f, a)
                for (segment in 1..3) {
                    val t = segment / 3f
                    val wobble = if (segment == 3) 0f else (if (segment % 2 == 0) 0.22f else -0.22f)
                    val next = polar(center, length * (0.2f + 0.8f * t), a + wobble)
                    drawLine(color.copy(alpha = fade * bright), previous, next, 2f)
                    previous = next
                }
            }
        }
        // Collapse: everything is pulled in; two rings close on the target to a third of the reach,
        // streaks spiral in behind them, and a dense core swells where they meet.
        3 -> {
            val closing = reach * (1f - 0.68f * out)
            drawCircle(color, closing, center, alpha = fade * bright, style = kkStroke(3.5f))
            drawCircle(color, closing * 0.62f, center, alpha = fade * 0.8f, style = kkStroke(2f))
            for (streak in 0 until 8) {
                val a = streak * TAU / 8f + 0.2f - progress * 0.9f
                drawLine(color.copy(alpha = fade * bright), polar(center, closing * 1.06f, a), polar(center, closing * (1.34f - 0.14f * out), a + 0.14f),
                    2f)
            }
            drawCircle(color, reach * (0.12f + 0.12f * out), center, alpha = fade * 0.22f)
            drawCircle(color, reach * (0.04f + 0.07f * out), center, alpha = fade * 0.95f)
        }
        // Shatter: three sound rings race outward one after another, split by the gaps of a
        // shockwave, and short throw strokes fly off the outermost.
        5 -> {
            for (ring in 0 until 3) {
                val lag = ring * 0.16f
                val t = ((progress - lag) / (1f - lag)).coerceIn(0f, 1f)
                if (t <= 0f) continue
                val radius = reach * KkEase.Out.transform(t)
                val alpha = (1f - t) * bright
                for (arc in 0 until 4) {
                    drawArc(color, arc * 90f + 12f + ring * 30f, 66f, false, Offset(center.x - radius, center.y - radius),
                        Size(radius * 2f, radius * 2f), alpha = alpha, style = kkStroke(3f - ring * 0.6f))
                }
            }
            val outer = reach * out
            for (stroke in 0 until 8) {
                val a = stroke * TAU / 8f + 0.2f
                drawLine(color.copy(alpha = fade * bright), polar(center, outer * 0.86f, a), polar(center, outer * 1.12f, a), 2f)
            }
        }
        // Unmake: a dashed hollow ring closes on the target while its core goes dark, then the
        // remembered damage punches back out as a thin bright ring.
        6 -> {
            val closing = reach * (1f - 0.7f * out)
            drawCircle(color, closing, center, alpha = fade * bright, style = WorldStrokes.dashedThin)
            drawCircle(Kk.Ink, reach * 0.22f * out, center, alpha = fade * 0.7f)
            drawCircle(color, reach * 0.22f * out, center, alpha = fade * bright, style = kkStroke(2f))
            if (progress > 0.45f) {
                val t = (progress - 0.45f) / 0.55f
                drawCircle(color, reach * (0.3f + 0.7f * KkEase.Out.transform(t)), center, alpha = (1f - t) * bright, style = kkStroke(1.5f))
            }
        }
        // Rupture: a broken ring tears open and pits scatter.
        else -> {
            val radius = reach * out
            for (arc in 0 until 5) {
                val start = arc * 72f + progress * 50f
                drawArc(color, start, 44f, false, Offset(center.x - radius, center.y - radius), Size(radius * 2f, radius * 2f),
                    alpha = fade * bright, style = kkStroke(3f))
            }
            for (pit in 0 until 7) {
                val a = pit * TAU / 7f + 0.4f
                drawCircle(color, 2f, polar(center, radius * (0.4f + 0.11f * pit), a), alpha = fade)
            }
        }
    }
}

private const val TAU = CanvasMath.TAU

/** Reaction name stamps: cond 30 at the world label scale, big enough to read mid-fight. */
private val REACTION_STAMP_SP = worldLabelSp(30f)

/**
 * The first time a profile meets a reaction, its name stamps at the enemy: condensed, tilted −6°,
 * slammed in and fading out over 900 ms, printed in the first element's hue over an offset in the
 * second's and an ink shadow. At most ten times in a profile's life.
 */
internal fun DrawScope.drawReactionStamps(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    textMeasurer: TextMeasurer,
) {
    val stamps = visualFx.reactionStamps
    if (stamps.isEmpty()) return
    val roles = textMeasurer.roles
    val reactions = engine.content.build.reactions
    for (index in stamps.indices) {
        val stamp = stamps[index]
        val definition = reactions.getOrNull(stamp.reaction) ?: continue
        val center = worldOnScreen(engine, stamp.x, stamp.y, shakeX, shakeY)
        if (!isOnScreen(center, 160f)) continue
        val elapsed = stamp.maxLife - stamp.life
        val slam = (elapsed * 1000f / KkSlam.DURATION_MS).coerceIn(0f, 1f)
        val fade = if (stamp.life < 0.25f) (stamp.life / 0.25f).coerceIn(0f, 1f) else 1f
        val alpha = KkSlam.alpha(slam) * fade
        if (alpha <= 0f) continue
        val first = roles.element(definition.id.first.ordinal)
        val second = roles.element(definition.id.second.ordinal)
        val name = WorldStrings.reactionName(definition.name, stamp.reaction, textMeasurer.language)
        val style = textMeasurer.typography.condStyle(REACTION_STAMP_SP, lineHeightEm = 1f, color = first)
        val face = measureKkText(textMeasurer, name, style, uppercase = true)
        val echo = measureKkText(textMeasurer, name, textMeasurer.typography.condStyle(REACTION_STAMP_SP, lineHeightEm = 1.001f, color = second),
            uppercase = true)
        val shadow = measureKkText(textMeasurer, name, textMeasurer.typography.condStyle(REACTION_STAMP_SP, lineHeightEm = 1.002f, color = Kk.Ink),
            uppercase = true)
        val half = face.size.width * 0.5f + 8f
        val x = keepInside(center.x, half, half, size.width, d(8f))
        val y = keepInside(center.y - 46f, face.kkBoxHeight, face.kkBoxHeight, size.height, d(8f))
        withTransform({
            translate(x, y)
            rotate(KkSlam.rotation(slam, -6f), Offset.Zero)
            val s = KkSlam.scale(slam)
            scale(s, s, Offset.Zero)
        }) {
            drawKkText(shadow, 4f, 4f, Kk.Ink, KkAlign.CENTER, KkVAlign.CENTER, alpha)
            drawKkText(echo, 3f, 2.5f, second, KkAlign.CENTER, KkVAlign.CENTER, alpha)
            drawKkText(face, 0f, 0f, first, KkAlign.CENTER, KkVAlign.CENTER, alpha)
        }
    }
}
