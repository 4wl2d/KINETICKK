// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** Mechanical interface shapes. Feature-specific meaning stays in Interaction. */
enum class InterfaceGlyph {
    PLUS, SHIELD, BOLT, GAUGE, DIAMOND, LAYERS, PAUSE, PLAY, CHECK, LOCK,
    FLASK, TARGET, CYCLE, BOOK, SLIDERS, RING, CHART,
}

fun DrawScope.drawInterfaceGlyph(glyph: InterfaceGlyph, center: Offset, radius: Float, color: Color) {
    val stroke = Stroke(radius * 0.15f, cap = StrokeCap.Round)
    fun point(x: Float, y: Float) = center + Offset(x * radius, y * radius)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, point(x1, y1), point(x2, y2), stroke.width, StrokeCap.Round)
    fun path(vararg coordinates: Float, close: Boolean = false) {
        val path = Path()
        coordinates.asList().chunked(2).forEachIndexed { index, xy ->
            val p = point(xy[0], xy[1])
            if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        if (close) path.close()
        drawPath(path, color, style = stroke)
    }
    when (glyph) {
        InterfaceGlyph.PLUS -> { line(-0.65f, 0f, 0.65f, 0f); line(0f, -0.65f, 0f, 0.65f) }
        InterfaceGlyph.SHIELD -> path(-0.7f, -0.6f, 0f, -0.9f, 0.7f, -0.6f, 0.55f, 0.4f, 0f, 0.9f, -0.55f, 0.4f, close = true)
        InterfaceGlyph.BOLT -> path(0.2f, -0.9f, -0.65f, 0.1f, -0.05f, 0.1f, -0.2f, 0.9f, 0.65f, -0.1f, 0.05f, -0.1f, close = true)
        InterfaceGlyph.GAUGE -> {
            drawArc(color, -210f, 240f, false, point(-0.8f, -0.8f), Size(radius * 1.6f, radius * 1.6f), style = stroke)
            line(0f, 0f, 0.5f, -0.5f)
        }
        InterfaceGlyph.DIAMOND -> path(0f, -0.85f, 0.65f, 0f, 0f, 0.85f, -0.65f, 0f, close = true)
        InterfaceGlyph.LAYERS -> {
            path(0f, -0.8f, 0.85f, -0.3f, 0f, 0.2f, -0.85f, -0.3f, close = true)
            path(-0.85f, 0.15f, 0f, 0.65f, 0.85f, 0.15f)
        }
        InterfaceGlyph.PAUSE -> { line(-0.3f, -0.6f, -0.3f, 0.6f); line(0.3f, -0.6f, 0.3f, 0.6f) }
        InterfaceGlyph.PLAY -> path(-0.4f, -0.7f, 0.7f, 0f, -0.4f, 0.7f, close = true)
        InterfaceGlyph.CHECK -> path(-0.65f, 0f, -0.15f, 0.5f, 0.7f, -0.5f)
        InterfaceGlyph.LOCK -> {
            drawRect(color, point(-0.6f, -0.05f), Size(radius * 1.2f, radius * 0.9f), style = stroke)
            drawArc(color, 180f, 180f, false, point(-0.4f, -0.8f), Size(radius * 0.8f, radius * 1.2f), style = stroke)
        }
        InterfaceGlyph.FLASK -> {
            path(-0.3f, -0.85f, -0.3f, -0.2f, -0.75f, 0.7f, 0.75f, 0.7f, 0.3f, -0.2f, 0.3f, -0.85f)
            line(-0.45f, -0.85f, 0.45f, -0.85f); line(-0.4f, 0.25f, 0.4f, 0.25f)
        }
        InterfaceGlyph.TARGET -> {
            drawCircle(color, radius * 0.6f, center, style = stroke)
            line(-0.9f, 0f, -0.4f, 0f); line(0.4f, 0f, 0.9f, 0f)
            line(0f, -0.9f, 0f, -0.4f); line(0f, 0.4f, 0f, 0.9f)
        }
        InterfaceGlyph.CYCLE -> {
            drawArc(color, -60f, 300f, false, point(-0.7f, -0.7f), Size(radius * 1.4f, radius * 1.4f), style = stroke)
            path(0.15f, -0.9f, 0.65f, -0.65f, 0.55f, -0.15f)
        }
        InterfaceGlyph.BOOK -> {
            path(0f, -0.5f, -0.75f, -0.75f, -0.75f, 0.55f, 0f, 0.8f, 0.75f, 0.55f, 0.75f, -0.75f, close = true)
            line(0f, -0.5f, 0f, 0.8f)
        }
        InterfaceGlyph.SLIDERS -> {
            line(-0.8f, -0.45f, 0.8f, -0.45f); line(-0.8f, 0.45f, 0.8f, 0.45f)
            line(-0.3f, -0.8f, -0.3f, -0.1f); line(0.3f, 0.1f, 0.3f, 0.8f)
        }
        InterfaceGlyph.RING -> {
            drawCircle(color, radius * 0.75f, center, style = stroke)
            drawCircle(color, radius * 0.2f, center)
        }
        InterfaceGlyph.CHART -> { line(-0.6f, 0.7f, -0.6f, 0f); line(0f, 0.7f, 0f, -0.7f); line(0.6f, 0.7f, 0.6f, -0.3f) }
    }
}
