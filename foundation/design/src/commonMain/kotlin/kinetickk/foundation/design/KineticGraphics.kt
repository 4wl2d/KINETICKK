// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

/** Shared visual geometry. Time and cursor are presentation inputs, never gameplay state. */
fun DrawScope.drawKineticRibbon(bounds: Rect, color: Color, cut: Float = d(14f)) {
    val notch = cut.coerceAtMost(bounds.height * 0.25f)
    val path = Path().apply {
        moveTo(bounds.left + notch, bounds.top)
        lineTo(bounds.right - notch * 1.8f, bounds.top)
        lineTo(bounds.right, bounds.bottom)
        lineTo(bounds.left + notch * 0.3f, bounds.bottom)
        lineTo(bounds.left, bounds.top + bounds.height * 0.35f)
        close()
    }
    drawPath(path, color)
}

fun DrawScope.drawKineticArrow(center: Offset, length: Float, color: Color) {
    val half = length * 0.5f
    drawLine(color, center - Offset(half, 0f), center + Offset(half, 0f), d(2f))
    drawLine(color, center + Offset(half * 0.25f, -half * 0.65f), center + Offset(half, 0f), d(2f))
    drawLine(color, center + Offset(half * 0.25f, half * 0.65f), center + Offset(half, 0f), d(2f))
}

fun DrawScope.drawKineticOrbits(center: Offset, radius: Float, time: Float, accent: Color, tilt: Float = -28f) {
    rotate(tilt, center) {
        repeat(5) { index ->
            val rx = radius * (1.15f + index * 0.24f)
            val ry = radius * (0.38f + index * 0.17f)
            val rect = Rect(center - Offset(rx, ry), Size(rx * 2f, ry * 2f))
            drawOval(if (index % 2 == 0) accent.copy(alpha = 0.25f) else White.copy(alpha = 0.18f),
                rect.topLeft, rect.size, style = Stroke(d(if (index == 2) 1.5f else 0.65f)))
            val angle = time * (0.10f + index * 0.035f) + index * 1.62f
            drawCircle(if (index % 2 == 0) accent else White, d(if (index == 2) 5f else 2.5f),
                center + Offset(cos(angle) * rx, sin(angle) * ry))
        }
        val sweep = Rect(center - Offset(radius * 2.2f, radius * 0.63f), Size(radius * 4.4f, radius * 1.26f))
        drawArc(accent, 6f, 150f, false, sweep.topLeft, sweep.size, style = Stroke(d(3f)))
    }
}

fun DrawScope.drawSectionAtmosphere(accent: Color = KineticAccent) {
    drawRect(SpaceBlack)
    drawKineticOrbits(Offset(size.width * 0.94f, size.height * 0.12f), size.minDimension * 0.27f,
        0f, accent.copy(alpha = 0.18f))
    drawLine(accent.copy(alpha = 0.25f), Offset(0f, size.height * 0.93f),
        Offset(size.width, size.height * 0.78f), d(0.8f))
}
