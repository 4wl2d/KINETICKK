// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin

private val LayeredInk = Color(0xFFF4F6FF)
private val LayeredBackground = Color(0xFF050610)

/** Two layered glyphs, a polygonal frame, dot markers and an optional outer arc. */
fun DrawScope.drawLayeredGlyph(
    primaryStyle: CanvasGlyphStyle,
    secondaryStyle: CanvasGlyphStyle,
    center: Offset,
    radius: Float,
    accent: Color,
    frameSides: Int,
    markerCount: Int,
    outerArcDegrees: Float = 0f,
    crossedOut: Boolean = false,
) {
    if (radius <= 0f) return

    val frameStroke = (radius * 0.075f).coerceAtLeast(0.7f)
    drawCircle(accent.copy(alpha = accent.alpha * 0.09f), radius * 1.13f, center)
    drawPolygon(
        center = center,
        radius = radius,
        sides = frameSides,
        rotation = -(PI / 2.0).toFloat(),
        color = accent.copy(alpha = accent.alpha * 0.78f),
        style = Stroke(frameStroke),
    )

    repeat(markerCount) { index ->
        val angle = -(PI / 2.0).toFloat() + (index - (markerCount - 1) * 0.5f) * 0.19f
        drawCircle(
            color = accent,
            radius = (radius * 0.038f).coerceAtLeast(0.55f),
            center = iconPolar(center, radius * 0.84f, angle),
        )
    }

    if (outerArcDegrees > 0f) {
        val ringRadius = radius * 1.075f
        drawArc(
            color = LayeredInk.copy(alpha = 0.62f),
            startAngle = -90f,
            sweepAngle = outerArcDegrees,
            useCenter = false,
            topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
            size = Size(ringRadius * 2f, ringRadius * 2f),
            style = Stroke((radius * 0.055f).coerceAtLeast(0.65f), cap = StrokeCap.Round),
        )
    }

    if (crossedOut) {
        drawCircle(LayeredBackground.copy(alpha = 0.76f), radius * 0.56f, center)
        drawCircle(accent.copy(alpha = 0.72f), radius * 0.52f, center, style = Stroke(frameStroke))
        drawLine(
            color = accent.copy(alpha = 0.72f),
            start = Offset(center.x - radius * 0.25f, center.y - radius * 0.25f),
            end = Offset(center.x + radius * 0.25f, center.y + radius * 0.25f),
            strokeWidth = frameStroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = accent.copy(alpha = 0.72f),
            start = Offset(center.x + radius * 0.25f, center.y - radius * 0.25f),
            end = Offset(center.x - radius * 0.25f, center.y + radius * 0.25f),
            strokeWidth = frameStroke,
            cap = StrokeCap.Round,
        )
        return
    }

    drawCanvasGlyph(
        style = primaryStyle,
        center = Offset(center.x - radius * 0.08f, center.y - radius * 0.08f),
        radius = radius * 0.65f,
        color = accent.copy(alpha = accent.alpha * 0.94f),
    )

    val secondaryCenter = Offset(center.x + radius * 0.18f, center.y + radius * 0.17f)
    drawCircle(LayeredBackground.copy(alpha = 0.86f), radius * 0.43f, secondaryCenter)
    drawCircle(
        color = accent.copy(alpha = accent.alpha * 0.58f),
        radius = radius * 0.43f,
        center = secondaryCenter,
        style = Stroke((radius * 0.055f).coerceAtLeast(0.6f)),
    )
    drawCanvasGlyph(
        style = secondaryStyle,
        center = secondaryCenter,
        radius = radius * 0.34f,
        color = LayeredInk,
    )
}

private fun iconPolar(center: Offset, radius: Float, angle: Float): Offset =
    Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
