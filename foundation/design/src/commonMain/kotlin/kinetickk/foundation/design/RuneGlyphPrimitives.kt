// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin

private val RuneInk = Color(0xFFF4F6FF)
private val RuneBackground = Color(0xFF050610)
private const val RUNE_TAU = 6.2831855f

/** Geometric rune styles; Content identifiers are mapped only inside Interaction. */
enum class CanvasRuneStyle {
    RADIAL_WHEEL,
    OFFSET_CHEVRONS,
    PARALLEL_ARROW,
    THREE_SPEED_LINES,
    BARS_AND_FAN,
    OPEN_ELLIPSE_DOT,
    ELLIPSE_AND_NAIL,
    RINGED_ANCHOR,
    HOOK_AND_DOT,
    INWARD_CHEVRONS,
    OFFSET_RINGS,
    ELLIPSE_LINK,
    BOLT_AND_DOT,
    THREE_SLASHES,
    FIVE_DOT_RECTANGLE,
    BROKEN_SWITCH,
    RETURN_ARROW,
    FOUR_SWIRL_ARMS,
    NESTED_DIAMONDS,
    OFFSET_DIAGONALS,
    TWO_HAND_DIAL,
    BOLT_BETWEEN_BARS,
    SPLIT_ARCS,
    CROSSED_DIAMOND,
    DIAMOND_EYE,
    CRACKED_RING,
    FIVE_RAY_FAN,
    SLASH_BLADE,
    THREE_NODE_FAN,
    SLASHED_DIAMONDS,
    OPEN_DIAL,
    STITCHED_DIAGONAL,
    SIX_PETAL_ROSETTE,
    THREE_TOOTH_ARC,
    FOUR_TICK_DIAL,
    DIAMOND_FLAME,
    RADIATING_STAFF,
    FOUR_DOT_CROWN,
    SLASHED_EYE_DIAMOND,
    COUNTER_ROTATING_ARCS,
}

/** Draws a rune and polygonal frame. Animation time is supplied by the caller. */
fun DrawScope.drawRuneMedallion(
    style: CanvasRuneStyle,
    center: Offset,
    radius: Float,
    accent: Color,
    frameSides: Int,
    frameRotation: Float = -PI.toFloat() * 0.5f,
    markerCount: Int = 0,
    filledMarkerCount: Int = 0,
    time: Float = 0f,
) {
    if (radius <= 0f || accent.alpha <= 0f) return
    val stroke = (radius * 0.085f).coerceAtLeast(0.65f)
    drawCircle(accent.copy(alpha = accent.alpha * 0.09f), radius * 1.22f, center)
    drawPolygon(center, radius, frameSides, frameRotation, RuneBackground.copy(alpha = 0.9f), Fill)
    drawPolygon(center, radius, frameSides, frameRotation, accent.copy(alpha = accent.alpha * 0.82f), Stroke(stroke))
    drawCircle(accent.copy(alpha = accent.alpha * 0.18f), radius * 0.78f, center, style = Stroke(stroke * 0.72f))
    drawRune(style, center, radius * 0.62f, accent, time)
    repeat(markerCount) { index ->
        val angle = PI.toFloat() * 0.72f + index * PI.toFloat() * 0.14f
        drawCircle(
            color = if (index < filledMarkerCount) RuneInk.copy(alpha = accent.alpha)
                else accent.copy(alpha = accent.alpha * 0.22f),
            radius = (radius * 0.052f).coerceAtLeast(0.7f),
            center = runePolar(center, radius * 1.08f, angle),
        )
    }
}

private fun DrawScope.drawRune(style: CanvasRuneStyle, center: Offset, radius: Float, color: Color, time: Float) {
    val stroke = (radius * 0.14f).coerceAtLeast(0.7f)
    val thin = (radius * 0.085f).coerceAtLeast(0.55f)
    when (style) {
        CanvasRuneStyle.RADIAL_WHEEL -> {
            drawCircle(color, radius * 0.72f, center, style = Stroke(stroke))
            repeat(6) { index -> drawLine(color, center, runePolar(center, radius * 0.72f, index * RUNE_TAU / 6f), thin) }
            drawCircle(RuneInk, radius * 0.16f, center)
        }
        CanvasRuneStyle.OFFSET_CHEVRONS -> {
            drawRuneChevron(Offset(center.x - radius * 0.26f, center.y), radius * 0.72f, color.copy(alpha = color.alpha * 0.48f), stroke)
            drawRuneChevron(Offset(center.x + radius * 0.24f, center.y), radius * 0.72f, RuneInk, stroke)
        }
        CanvasRuneStyle.PARALLEL_ARROW -> {
            drawLine(color, Offset(center.x - radius, center.y + radius * 0.33f), Offset(center.x + radius * 0.55f, center.y + radius * 0.33f), stroke, StrokeCap.Round)
            drawLine(RuneInk, Offset(center.x - radius * 0.62f, center.y - radius * 0.38f), Offset(center.x + radius * 0.88f, center.y - radius * 0.38f), stroke, StrokeCap.Round)
            drawPolygon(Offset(center.x + radius * 0.72f, center.y - radius * 0.38f), radius * 0.25f, 3, 0f, RuneInk, Fill)
        }
        CanvasRuneStyle.THREE_SPEED_LINES -> {
            repeat(3) { index ->
                val y = center.y + (index - 1) * radius * 0.42f
                drawLine(if (index == 1) RuneInk else color, Offset(center.x - radius * (0.94f - index * 0.12f), y), Offset(center.x + radius * (0.72f - index * 0.08f), y), thin, StrokeCap.Round)
            }
            drawCircle(color, radius * 0.18f, Offset(center.x + radius * 0.68f, center.y))
        }
        CanvasRuneStyle.BARS_AND_FAN -> {
            drawLine(color, Offset(center.x - radius * 0.62f, center.y - radius * 0.68f), Offset(center.x - radius * 0.62f, center.y + radius * 0.68f), stroke, StrokeCap.Round)
            drawLine(color, Offset(center.x - radius * 0.18f, center.y - radius * 0.68f), Offset(center.x - radius * 0.18f, center.y + radius * 0.68f), stroke, StrokeCap.Round)
            repeat(5) { index -> drawLine(RuneInk, Offset(center.x + radius * 0.05f, center.y), runePolar(Offset(center.x + radius * 0.05f, center.y), radius * 0.78f, -1.0f + index * 0.5f), thin, StrokeCap.Round) }
        }
        CanvasRuneStyle.OPEN_ELLIPSE_DOT -> {
            drawArc(color, 205f, 310f, false, Offset(center.x - radius * 0.82f, center.y - radius * 0.68f), Size(radius * 1.64f, radius * 1.36f), style = Stroke(stroke, cap = StrokeCap.Round))
            drawCircle(RuneInk, radius * 0.18f, Offset(center.x + radius * 0.72f, center.y - radius * 0.1f))
        }

        CanvasRuneStyle.ELLIPSE_AND_NAIL -> {
            drawOval(color, Offset(center.x - radius, center.y - radius * 0.45f), Size(radius * 2f, radius * 0.9f), style = Stroke(thin))
            drawLine(RuneInk, Offset(center.x, center.y - radius * 0.82f), Offset(center.x, center.y + radius * 0.72f), stroke, StrokeCap.Round)
            drawPolygon(Offset(center.x, center.y + radius * 0.72f), radius * 0.24f, 3, PI.toFloat() / 2f, RuneInk, Fill)
        }
        CanvasRuneStyle.RINGED_ANCHOR -> {
            drawLine(color, Offset(center.x, center.y - radius * 0.88f), Offset(center.x, center.y + radius * 0.56f), stroke, StrokeCap.Round)
            drawCircle(RuneInk, radius * 0.2f, Offset(center.x, center.y - radius * 0.66f), style = Stroke(thin))
            drawArc(color, 10f, 160f, false, Offset(center.x - radius * 0.78f, center.y - radius * 0.26f), Size(radius * 1.56f, radius * 1.18f), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        CanvasRuneStyle.HOOK_AND_DOT -> {
            drawArc(color, -80f, 275f, false, Offset(center.x - radius * 0.72f, center.y - radius * 0.72f), Size(radius * 1.44f, radius * 1.44f), style = Stroke(stroke, cap = StrokeCap.Round))
            drawPolygon(Offset(center.x - radius * 0.47f, center.y + radius * 0.55f), radius * 0.24f, 3, 2.5f, RuneInk, Fill)
            drawCircle(RuneInk, radius * 0.14f, center)
        }
        CanvasRuneStyle.INWARD_CHEVRONS -> {
            repeat(2) { direction ->
                val sign = if (direction == 0) -1f else 1f
                drawLine(color, Offset(center.x + sign * radius * 0.92f, center.y - radius * 0.62f), Offset(center.x + sign * radius * 0.22f, center.y), stroke, StrokeCap.Round)
                drawLine(color, Offset(center.x + sign * radius * 0.92f, center.y + radius * 0.62f), Offset(center.x + sign * radius * 0.22f, center.y), stroke, StrokeCap.Round)
            }
            drawCircle(RuneInk, radius * 0.18f, center)
        }
        CanvasRuneStyle.OFFSET_RINGS -> {
            drawCircle(color.copy(alpha = color.alpha * 0.45f), radius * 0.7f, Offset(center.x - radius * 0.22f, center.y), style = Stroke(stroke))
            drawCircle(RuneInk, radius * 0.55f, Offset(center.x + radius * 0.24f, center.y), style = Stroke(thin))
            drawCircle(color, radius * 0.16f, center)
        }
        CanvasRuneStyle.ELLIPSE_LINK -> {
            drawOval(color, Offset(center.x - radius, center.y - radius * 0.42f), Size(radius * 2f, radius * 0.84f), style = Stroke(thin))
            drawCircle(RuneInk, radius * 0.2f, Offset(center.x - radius * 0.72f, center.y))
            drawCircle(RuneInk, radius * 0.2f, Offset(center.x + radius * 0.72f, center.y))
            drawLine(color, Offset(center.x, center.y - radius * 0.55f), Offset(center.x, center.y + radius * 0.55f), stroke)
        }

        CanvasRuneStyle.BOLT_AND_DOT -> {
            drawBolt(center, radius, color)
            drawCircle(RuneInk, radius * 0.16f, Offset(center.x + radius * 0.72f, center.y - radius * 0.72f))
        }
        CanvasRuneStyle.THREE_SLASHES -> {
            repeat(3) { index ->
                val shift = (index - 1) * radius * 0.45f
                drawLine(if (index == 1) RuneInk else color, Offset(center.x - radius * 0.72f + shift, center.y + radius * 0.7f), Offset(center.x + shift, center.y - radius * 0.7f), stroke, StrokeCap.Round)
            }
        }
        CanvasRuneStyle.FIVE_DOT_RECTANGLE -> {
            drawRect(color.copy(alpha = color.alpha * 0.22f), Offset(center.x - radius * 0.72f, center.y - radius * 0.5f), Size(radius * 1.44f, radius))
            drawRect(color, Offset(center.x - radius * 0.72f, center.y - radius * 0.5f), Size(radius * 1.44f, radius), style = Stroke(thin))
            repeat(5) { index -> drawCircle(if (index < 4) RuneInk else color, radius * 0.1f, Offset(center.x - radius * 0.48f + index * radius * 0.24f, center.y)) }
        }
        CanvasRuneStyle.BROKEN_SWITCH -> {
            drawLine(color, Offset(center.x - radius * 0.9f, center.y), Offset(center.x - radius * 0.2f, center.y), stroke, StrokeCap.Round)
            drawLine(color, Offset(center.x + radius * 0.2f, center.y), Offset(center.x + radius * 0.9f, center.y), stroke, StrokeCap.Round)
            drawLine(RuneInk, Offset(center.x - radius * 0.2f, center.y), Offset(center.x + radius * 0.42f, center.y - radius * 0.6f), stroke, StrokeCap.Round)
            drawCircle(RuneInk, radius * 0.12f, Offset(center.x - radius * 0.2f, center.y))
            drawCircle(RuneInk, radius * 0.12f, Offset(center.x + radius * 0.2f, center.y))
        }
        CanvasRuneStyle.RETURN_ARROW -> {
            drawArc(color, -40f, 285f, false, Offset(center.x - radius * 0.75f, center.y - radius * 0.75f), Size(radius * 1.5f, radius * 1.5f), style = Stroke(stroke, cap = StrokeCap.Round))
            drawPolygon(Offset(center.x + radius * 0.58f, center.y - radius * 0.45f), radius * 0.23f, 3, -0.5f, RuneInk, Fill)
            drawCircle(RuneInk, radius * 0.18f, center)
        }
        CanvasRuneStyle.FOUR_SWIRL_ARMS -> {
            repeat(4) { index ->
                val angle = index * RUNE_TAU / 4f
                drawLine(color, runePolar(center, radius * 0.38f, angle), runePolar(center, radius * 0.9f, angle + 0.24f), stroke, StrokeCap.Round)
            }
            drawPolygon(center, radius * 0.25f, 4, PI.toFloat() / 4f, RuneInk, Fill)
        }

        CanvasRuneStyle.NESTED_DIAMONDS -> {
            repeat(3) { index -> drawPolygon(center, radius * (0.92f - index * 0.25f), 4, PI.toFloat() / 4f, if (index == 2) RuneInk else color.copy(alpha = color.alpha * (1f - index * 0.2f)), Stroke(if (index == 2) stroke else thin)) }
        }
        CanvasRuneStyle.OFFSET_DIAGONALS -> {
            repeat(3) { index ->
                val shift = (index - 1) * radius * 0.22f
                drawLine(if (index == 2) RuneInk else color.copy(alpha = color.alpha * (0.45f + index * 0.22f)), Offset(center.x - radius * 0.7f + shift, center.y - radius * 0.72f), Offset(center.x + radius * 0.52f + shift, center.y + radius * 0.72f), stroke, StrokeCap.Round)
            }
        }
        CanvasRuneStyle.TWO_HAND_DIAL -> {
            drawCircle(color, radius * 0.78f, center, style = Stroke(stroke))
            drawLine(RuneInk, center, Offset(center.x, center.y - radius * 0.62f), stroke, StrokeCap.Round)
            drawLine(color, center, Offset(center.x + radius * 0.56f, center.y + radius * 0.26f), stroke, StrokeCap.Round)
            drawCircle(RuneInk, radius * 0.13f, center)
        }
        CanvasRuneStyle.BOLT_BETWEEN_BARS -> {
            drawLine(color, Offset(center.x - radius * 0.52f, center.y - radius * 0.82f), Offset(center.x - radius * 0.52f, center.y + radius * 0.82f), stroke)
            drawLine(color, Offset(center.x + radius * 0.52f, center.y - radius * 0.82f), Offset(center.x + radius * 0.52f, center.y + radius * 0.82f), stroke)
            drawBolt(center, radius * 0.72f, RuneInk)
        }
        CanvasRuneStyle.SPLIT_ARCS -> {
            drawLine(color, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), thin)
            drawArc(RuneInk, 190f, 160f, false, Offset(center.x - radius * 0.65f, center.y - radius * 0.38f), Size(radius * 1.3f, radius * 1.3f), style = Stroke(stroke))
            drawArc(color, 10f, 160f, false, Offset(center.x - radius * 0.65f, center.y - radius * 0.92f), Size(radius * 1.3f, radius * 1.3f), style = Stroke(stroke))
        }
        CanvasRuneStyle.CROSSED_DIAMOND -> {
            drawPolygon(center, radius * 0.88f, 4, PI.toFloat() / 4f, color, Stroke(stroke))
            drawLine(RuneInk, Offset(center.x - radius * 0.52f, center.y - radius * 0.52f), Offset(center.x + radius * 0.52f, center.y + radius * 0.52f), thin)
            drawLine(RuneInk, Offset(center.x + radius * 0.52f, center.y - radius * 0.52f), Offset(center.x - radius * 0.52f, center.y + radius * 0.52f), thin)
            drawCircle(color, radius * 0.13f, center)
        }

        CanvasRuneStyle.DIAMOND_EYE -> {
            val eye = Path().apply {
                moveTo(center.x - radius, center.y)
                quadraticTo(center.x, center.y - radius * 0.82f, center.x + radius, center.y)
                quadraticTo(center.x, center.y + radius * 0.82f, center.x - radius, center.y)
                close()
            }
            drawPath(eye, color, style = Stroke(stroke))
            drawPolygon(center, radius * 0.34f, 4, PI.toFloat() / 4f, RuneInk, Fill)
        }
        CanvasRuneStyle.CRACKED_RING -> {
            drawCircle(color, radius * 0.76f, center, style = Stroke(stroke))
            drawLine(RuneInk, Offset(center.x - radius * 0.15f, center.y - radius * 0.74f), Offset(center.x + radius * 0.12f, center.y - radius * 0.1f), thin)
            drawLine(RuneInk, Offset(center.x + radius * 0.12f, center.y - radius * 0.1f), Offset(center.x - radius * 0.38f, center.y + radius * 0.68f), thin)
            drawLine(RuneInk, Offset(center.x + radius * 0.12f, center.y - radius * 0.1f), Offset(center.x + radius * 0.68f, center.y + radius * 0.42f), thin)
        }
        CanvasRuneStyle.FIVE_RAY_FAN -> {
            repeat(5) { index ->
                val angle = -0.95f + index * 0.48f
                drawLine(if (index == 2) RuneInk else color, Offset(center.x - radius * 0.62f, center.y + radius * 0.62f), runePolar(center, radius * 0.95f, angle), thin, StrokeCap.Round)
            }
            drawCircle(color, radius * 0.18f, Offset(center.x - radius * 0.62f, center.y + radius * 0.62f))
        }
        CanvasRuneStyle.SLASH_BLADE -> {
            val blade = Path().apply {
                moveTo(center.x + radius * 0.78f, center.y - radius * 0.88f)
                lineTo(center.x + radius * 0.26f, center.y + radius * 0.58f)
                lineTo(center.x - radius * 0.78f, center.y + radius * 0.88f)
                lineTo(center.x - radius * 0.18f, center.y + radius * 0.08f)
                close()
            }
            drawPath(blade, color, style = Fill)
            drawLine(RuneInk, Offset(center.x - radius * 0.65f, center.y + radius * 0.72f), Offset(center.x + radius * 0.67f, center.y - radius * 0.72f), thin)
        }
        CanvasRuneStyle.THREE_NODE_FAN -> {
            repeat(3) { index ->
                val angle = -PI.toFloat() / 2f + index * RUNE_TAU / 3f
                val point = runePolar(center, radius * 0.7f, angle)
                drawLine(color.copy(alpha = color.alpha * (0.55f + index * 0.2f)), center, point, stroke, StrokeCap.Round)
                drawCircle(if (index == 1) RuneInk else color, radius * 0.19f, point)
            }
            drawCircle(RuneBackground, radius * 0.2f, center)
        }
        CanvasRuneStyle.SLASHED_DIAMONDS -> {
            drawPolygon(Offset(center.x - radius * 0.35f, center.y), radius * 0.55f, 4, PI.toFloat() / 4f, color, Stroke(thin))
            drawPolygon(Offset(center.x + radius * 0.35f, center.y), radius * 0.55f, 4, PI.toFloat() / 4f, color, Stroke(thin))
            drawLine(RuneInk, Offset(center.x - radius * 0.82f, center.y + radius * 0.82f), Offset(center.x + radius * 0.82f, center.y - radius * 0.82f), stroke, StrokeCap.Round)
        }

        CanvasRuneStyle.OPEN_DIAL -> {
            drawArc(color, 150f, 240f, false, Offset(center.x - radius * 0.8f, center.y - radius * 0.8f), Size(radius * 1.6f, radius * 1.6f), style = Stroke(stroke, cap = StrokeCap.Round))
            drawLine(RuneInk, center, runePolar(center, radius * 0.68f, -0.72f), stroke, StrokeCap.Round)
            drawCircle(RuneInk, radius * 0.13f, center)
        }
        CanvasRuneStyle.STITCHED_DIAGONAL -> {
            drawLine(color, Offset(center.x - radius * 0.72f, center.y - radius * 0.82f), Offset(center.x + radius * 0.72f, center.y + radius * 0.82f), stroke, StrokeCap.Round)
            repeat(4) { index ->
                val t = (index + 1f) / 5f
                val p = Offset(center.x - radius * 0.72f + radius * 1.44f * t, center.y - radius * 0.82f + radius * 1.64f * t)
                drawLine(RuneInk, Offset(p.x - radius * 0.28f, p.y + radius * 0.22f), Offset(p.x + radius * 0.28f, p.y - radius * 0.22f), thin, StrokeCap.Round)
            }
        }
        CanvasRuneStyle.SIX_PETAL_ROSETTE -> {
            repeat(6) { index ->
                val angle = index * RUNE_TAU / 6f
                val petal = runePolar(center, radius * 0.5f, angle)
                drawCircle(color.copy(alpha = color.alpha * 0.28f), radius * 0.38f, petal)
                drawCircle(color, radius * 0.38f, petal, style = Stroke(thin))
            }
            drawCircle(RuneInk, radius * 0.2f, center)
        }
        CanvasRuneStyle.THREE_TOOTH_ARC -> {
            drawArc(color, 190f, 160f, false, Offset(center.x - radius * 0.72f, center.y - radius * 0.66f), Size(radius * 1.44f, radius * 1.55f), style = Stroke(stroke, cap = StrokeCap.Round))
            repeat(3) { index ->
                val x = center.x + (index - 1) * radius * 0.38f
                drawPolygon(Offset(x, center.y + radius * 0.57f), radius * 0.18f, 3, PI.toFloat() / 2f, if (index == 1) RuneInk else color, Fill)
            }
            drawCircle(RuneInk, radius * 0.14f, Offset(center.x, center.y - radius * 0.62f))
        }
        CanvasRuneStyle.FOUR_TICK_DIAL -> {
            drawCircle(color, radius * 0.78f, center, style = Stroke(stroke))
            repeat(4) { index -> drawLine(color, runePolar(center, radius * 0.62f, index * RUNE_TAU / 4f), runePolar(center, radius * 0.8f, index * RUNE_TAU / 4f), thin) }
            drawLine(RuneInk, center, Offset(center.x + radius * 0.46f, center.y - radius * 0.48f), stroke, StrokeCap.Round)
            drawCircle(RuneInk, radius * 0.13f, center)
        }
        CanvasRuneStyle.DIAMOND_FLAME -> {
            val flame = Path().apply {
                moveTo(center.x, center.y - radius)
                cubicTo(center.x + radius * 0.75f, center.y - radius * 0.2f, center.x + radius * 0.65f, center.y + radius * 0.72f, center.x, center.y + radius * 0.9f)
                cubicTo(center.x - radius * 0.72f, center.y + radius * 0.42f, center.x - radius * 0.35f, center.y - radius * 0.18f, center.x, center.y - radius)
                close()
            }
            drawPath(flame, color, style = Fill)
            drawPolygon(Offset(center.x, center.y + radius * 0.22f), radius * 0.28f, 4, PI.toFloat() / 4f, RuneInk, Fill)
        }

        CanvasRuneStyle.RADIATING_STAFF -> {
            drawLine(color, Offset(center.x - radius * 0.48f, center.y + radius * 0.82f), Offset(center.x + radius * 0.28f, center.y - radius * 0.58f), stroke * 1.25f, StrokeCap.Round)
            drawCircle(RuneInk, radius * 0.3f, Offset(center.x + radius * 0.42f, center.y - radius * 0.68f))
            repeat(3) { index ->
                val angle = -2.65f + index * 0.62f
                drawLine(color, Offset(center.x + radius * 0.42f, center.y - radius * 0.68f), runePolar(Offset(center.x + radius * 0.42f, center.y - radius * 0.68f), radius * 0.58f, angle), thin, StrokeCap.Round)
            }
        }
        CanvasRuneStyle.FOUR_DOT_CROWN -> {
            val crown = Path().apply {
                moveTo(center.x - radius * 0.9f, center.y + radius * 0.55f)
                lineTo(center.x - radius * 0.72f, center.y - radius * 0.62f)
                lineTo(center.x - radius * 0.25f, center.y - radius * 0.08f)
                lineTo(center.x, center.y - radius * 0.9f)
                lineTo(center.x + radius * 0.25f, center.y - radius * 0.08f)
                lineTo(center.x + radius * 0.72f, center.y - radius * 0.62f)
                lineTo(center.x + radius * 0.9f, center.y + radius * 0.55f)
                close()
            }
            drawPath(crown, color.copy(alpha = color.alpha * 0.28f), style = Fill)
            drawPath(crown, color, style = Stroke(stroke))
            repeat(4) { index -> drawCircle(if (index == 1) RuneInk else color, radius * 0.11f, Offset(center.x - radius * 0.48f + index * radius * 0.32f, center.y + radius * 0.35f)) }
        }
        CanvasRuneStyle.SLASHED_EYE_DIAMOND -> {
            drawPolygon(center, radius * 0.88f, 4, PI.toFloat() / 4f, color, Stroke(stroke))
            drawCircle(color.copy(alpha = color.alpha * 0.22f), radius * 0.5f, center)
            drawCircle(RuneInk, radius * 0.2f, center)
            drawLine(RuneInk, Offset(center.x - radius * 0.85f, center.y + radius * 0.7f), Offset(center.x + radius * 0.85f, center.y - radius * 0.7f), thin)
        }
        CanvasRuneStyle.COUNTER_ROTATING_ARCS -> {
            drawArc(color, -65f + time * 8f, 235f, false, Offset(center.x - radius * 0.72f, center.y - radius * 0.72f), Size(radius * 1.44f, radius * 1.44f), style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(RuneInk, 115f - time * 8f, 235f, false, Offset(center.x - radius * 0.48f, center.y - radius * 0.48f), Size(radius * 0.96f, radius * 0.96f), style = Stroke(thin, cap = StrokeCap.Round))
            drawPolygon(center, radius * 0.2f, 6, time, color, Fill)
        }
    }
}

private fun DrawScope.drawRuneChevron(center: Offset, radius: Float, color: Color, stroke: Float) {
    drawLine(color, Offset(center.x - radius * 0.48f, center.y - radius * 0.7f), Offset(center.x + radius * 0.28f, center.y), stroke, StrokeCap.Round)
    drawLine(color, Offset(center.x + radius * 0.28f, center.y), Offset(center.x - radius * 0.48f, center.y + radius * 0.7f), stroke, StrokeCap.Round)
}

private fun DrawScope.drawBolt(center: Offset, radius: Float, color: Color) {
    val bolt = Path().apply {
        moveTo(center.x + radius * 0.18f, center.y - radius)
        lineTo(center.x - radius * 0.55f, center.y + radius * 0.05f)
        lineTo(center.x - radius * 0.08f, center.y)
        lineTo(center.x - radius * 0.35f, center.y + radius)
        lineTo(center.x + radius * 0.62f, center.y - radius * 0.18f)
        lineTo(center.x + radius * 0.1f, center.y - radius * 0.08f)
        close()
    }
    drawPath(bolt, color, style = Fill)
}

private fun runePolar(center: Offset, radius: Float, angle: Float): Offset =
    Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
