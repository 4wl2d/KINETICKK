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

private val GlyphCutout = Color(0xFF050610)
private const val GLYPH_TAU = 6.2831855f

/** A mechanical visual style. Semantic identifiers are mapped by each Interaction owner. */
enum class CanvasGlyphStyle {
    SUNBURST,
    TRIPLE_ARROW,
    CONCENTRIC_ORB,
    HORSESHOE,
    SNOWFLAKE,
    HEX_CROSS,
    LEAF,
    DOUBLE_CHEVRON,
    ORBIT_ARROWS,
    RETICLE,
    BOLT,
    RADIAL_NODES,
    FOUR_LEAF,
    CIRCUIT_LINES,
    CRYSTAL,
    SLASH_BARS,
    SHIELD,
    BRICK_LINES,
    INTERLOCKING_RINGS,
    THREE_BLADE,
}

/** Draws one geometry-only glyph selected by an Interaction-supplied style. */
fun DrawScope.drawCanvasGlyph(
    style: CanvasGlyphStyle,
    center: Offset,
    radius: Float,
    color: Color,
) {
    if (radius <= 0f) return
    val stroke = (radius * 0.13f).coerceAtLeast(0.65f)
    val thinStroke = (radius * 0.085f).coerceAtLeast(0.55f)

    when (style) {
        CanvasGlyphStyle.SUNBURST -> {
            repeat(8) { index ->
                val angle = index * GLYPH_TAU / 8f
                drawLine(
                    color,
                    glyphPolar(center, radius * 0.56f, angle),
                    glyphPolar(center, radius * 0.94f, angle),
                    thinStroke,
                    StrokeCap.Round,
                )
            }
            drawGlyphPolygon(center, radius * 0.48f, 4, (PI / 4.0).toFloat(), color, Fill)
        }

        CanvasGlyphStyle.TRIPLE_ARROW -> {
            repeat(3) { index ->
                val y = center.y + (index - 1) * radius * 0.42f
                val tip = Offset(center.x + radius * (0.82f - index * 0.1f), y)
                drawLine(color, Offset(center.x - radius * 0.78f, y), tip, thinStroke, StrokeCap.Round)
                drawLine(color, tip, Offset(tip.x - radius * 0.28f, tip.y - radius * 0.22f), thinStroke, StrokeCap.Round)
                drawLine(color, tip, Offset(tip.x - radius * 0.28f, tip.y + radius * 0.22f), thinStroke, StrokeCap.Round)
            }
        }

        CanvasGlyphStyle.CONCENTRIC_ORB -> {
            drawCircle(color.copy(alpha = color.alpha * 0.18f), radius * 0.84f, center)
            drawCircle(color, radius * 0.72f, center, style = Stroke(stroke))
            drawCircle(color.copy(alpha = color.alpha * 0.84f), radius * 0.42f, center)
            drawCircle(GlyphCutout.copy(alpha = 0.72f), radius * 0.13f, center)
        }

        CanvasGlyphStyle.HORSESHOE -> {
            val arcRadius = radius * 0.66f
            val arcTop = center.y - radius * 0.13f
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(center.x - arcRadius, arcTop),
                size = Size(arcRadius * 2f, arcRadius * 1.45f),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            val poleY = arcTop + arcRadius * 0.72f
            drawLine(color, Offset(center.x - arcRadius, poleY), Offset(center.x - arcRadius, center.y - radius * 0.68f), stroke, StrokeCap.Round)
            drawLine(color, Offset(center.x + arcRadius, poleY), Offset(center.x + arcRadius, center.y - radius * 0.68f), stroke, StrokeCap.Round)
            drawLine(color, Offset(center.x - radius * 0.82f, center.y - radius * 0.68f), Offset(center.x - radius * 0.49f, center.y - radius * 0.68f), stroke)
            drawLine(color, Offset(center.x + radius * 0.49f, center.y - radius * 0.68f), Offset(center.x + radius * 0.82f, center.y - radius * 0.68f), stroke)
        }

        CanvasGlyphStyle.SNOWFLAKE -> {
            repeat(3) { index ->
                val angle = index * (PI / 3.0).toFloat()
                val start = glyphPolar(center, radius * 0.82f, angle)
                val end = glyphPolar(center, radius * 0.82f, angle + PI.toFloat())
                drawLine(color, start, end, thinStroke, StrokeCap.Round)
                repeat(2) { direction ->
                    val branchAngle = angle + if (direction == 0) 0f else PI.toFloat()
                    val branchRoot = glyphPolar(center, radius * 0.56f, branchAngle)
                    drawLine(color, branchRoot, glyphPolar(branchRoot, radius * 0.25f, branchAngle + 2.45f), thinStroke, StrokeCap.Round)
                    drawLine(color, branchRoot, glyphPolar(branchRoot, radius * 0.25f, branchAngle - 2.45f), thinStroke, StrokeCap.Round)
                }
            }
        }

        CanvasGlyphStyle.HEX_CROSS -> {
            drawGlyphPolygon(center, radius * 0.82f, 6, (PI / 6.0).toFloat(), color, Stroke(stroke))
            drawLine(color, Offset(center.x - radius * 0.4f, center.y), Offset(center.x + radius * 0.4f, center.y), stroke, StrokeCap.Round)
            drawLine(color, Offset(center.x, center.y - radius * 0.4f), Offset(center.x, center.y + radius * 0.4f), stroke, StrokeCap.Round)
        }

        CanvasGlyphStyle.LEAF -> {
            val leaf = Path().apply {
                moveTo(center.x, center.y + radius * 0.78f)
                cubicTo(
                    center.x - radius * 0.78f,
                    center.y + radius * 0.32f,
                    center.x - radius * 0.62f,
                    center.y - radius * 0.64f,
                    center.x,
                    center.y - radius * 0.7f,
                )
                cubicTo(
                    center.x + radius * 0.7f,
                    center.y - radius * 0.28f,
                    center.x + radius * 0.54f,
                    center.y + radius * 0.54f,
                    center.x,
                    center.y + radius * 0.78f,
                )
                close()
            }
            drawPath(leaf, color, style = Stroke(thinStroke))
            drawLine(color, Offset(center.x, center.y + radius * 0.64f), Offset(center.x, center.y - radius * 0.42f), thinStroke, StrokeCap.Round)
            drawLine(color, Offset(center.x, center.y + radius * 0.08f), Offset(center.x + radius * 0.38f, center.y - radius * 0.18f), thinStroke, StrokeCap.Round)
        }

        CanvasGlyphStyle.DOUBLE_CHEVRON -> {
            repeat(2) { index ->
                val shift = (index - 0.5f) * radius * 0.72f
                drawLine(color, Offset(center.x - radius * 0.46f + shift, center.y - radius * 0.68f), Offset(center.x + radius * 0.18f + shift, center.y), stroke, StrokeCap.Round)
                drawLine(color, Offset(center.x + radius * 0.18f + shift, center.y), Offset(center.x - radius * 0.46f + shift, center.y + radius * 0.68f), stroke, StrokeCap.Round)
            }
        }

        CanvasGlyphStyle.ORBIT_ARROWS -> {
            drawArc(
                color,
                -70f,
                142f,
                false,
                Offset(center.x - radius * 0.73f, center.y - radius * 0.73f),
                Size(radius * 1.46f, radius * 1.46f),
                style = Stroke(thinStroke, cap = StrokeCap.Round),
            )
            drawArc(
                color,
                110f,
                142f,
                false,
                Offset(center.x - radius * 0.73f, center.y - radius * 0.73f),
                Size(radius * 1.46f, radius * 1.46f),
                style = Stroke(thinStroke, cap = StrokeCap.Round),
            )
            drawGlyphPolygon(Offset(center.x + radius * 0.72f, center.y - radius * 0.2f), radius * 0.24f, 3, 0.25f, color, Fill)
            drawGlyphPolygon(Offset(center.x - radius * 0.72f, center.y + radius * 0.2f), radius * 0.24f, 3, PI.toFloat() + 0.25f, color, Fill)
        }

        CanvasGlyphStyle.RETICLE -> {
            drawCircle(color, radius * 0.56f, center, style = Stroke(thinStroke))
            drawCircle(color, radius * 0.16f, center)
            drawLine(color, Offset(center.x - radius * 0.95f, center.y), Offset(center.x - radius * 0.36f, center.y), thinStroke, StrokeCap.Round)
            drawLine(color, Offset(center.x + radius * 0.36f, center.y), Offset(center.x + radius * 0.95f, center.y), thinStroke, StrokeCap.Round)
            drawLine(color, Offset(center.x, center.y - radius * 0.95f), Offset(center.x, center.y - radius * 0.36f), thinStroke, StrokeCap.Round)
            drawLine(color, Offset(center.x, center.y + radius * 0.36f), Offset(center.x, center.y + radius * 0.95f), thinStroke, StrokeCap.Round)
        }

        CanvasGlyphStyle.BOLT -> {
            drawCircle(color.copy(alpha = color.alpha * 0.22f), radius * 0.72f, center)
            drawCircle(color, radius * 0.68f, center, style = Stroke(thinStroke))
            val bolt = Path().apply {
                moveTo(center.x + radius * 0.15f, center.y - radius * 0.92f)
                lineTo(center.x - radius * 0.48f, center.y + radius * 0.05f)
                lineTo(center.x - radius * 0.06f, center.y + radius * 0.01f)
                lineTo(center.x - radius * 0.28f, center.y + radius * 0.92f)
                lineTo(center.x + radius * 0.52f, center.y - radius * 0.18f)
                lineTo(center.x + radius * 0.08f, center.y - radius * 0.08f)
                close()
            }
            drawPath(bolt, color, style = Fill)
        }

        CanvasGlyphStyle.RADIAL_NODES -> {
            drawCircle(color.copy(alpha = color.alpha * 0.2f), radius * 0.7f, center)
            drawCircle(color, radius * 0.7f, center, style = Stroke(thinStroke))
            repeat(4) { index ->
                val angle = index * GLYPH_TAU / 4f
                val outside = glyphPolar(center, radius * 0.95f, angle)
                val inside = glyphPolar(center, radius * 0.48f, angle)
                drawCircle(color, radius * 0.12f, outside)
                drawLine(color, outside, inside, thinStroke, StrokeCap.Round)
            }
        }

        CanvasGlyphStyle.FOUR_LEAF -> {
            repeat(4) { index ->
                val leafCenter = glyphPolar(center, radius * 0.4f, index * GLYPH_TAU / 4f)
                drawCircle(color.copy(alpha = color.alpha * 0.2f), radius * 0.34f, leafCenter)
                drawCircle(color, radius * 0.34f, leafCenter, style = Stroke(thinStroke))
            }
            drawGlyphPolygon(center, radius * 0.22f, 4, (PI / 4.0).toFloat(), color, Fill)
        }

        CanvasGlyphStyle.CIRCUIT_LINES -> {
            repeat(3) { index ->
                val y = center.y + (index - 1) * radius * 0.48f
                val nodeOnRight = index % 2 == 0
                val nodeX = center.x + if (nodeOnRight) radius * 0.68f else -radius * 0.68f
                drawLine(color, Offset(center.x - radius * 0.76f, y), Offset(center.x + radius * 0.76f, y), thinStroke, StrokeCap.Round)
                drawCircle(GlyphCutout, radius * 0.16f, Offset(nodeX, y))
                drawCircle(color, radius * 0.14f, Offset(nodeX, y), style = Stroke(thinStroke))
            }
            drawLine(color.copy(alpha = color.alpha * 0.7f), Offset(center.x, center.y - radius * 0.48f), Offset(center.x, center.y + radius * 0.48f), thinStroke)
        }

        CanvasGlyphStyle.CRYSTAL -> {
            val crystal = Path().apply {
                moveTo(center.x, center.y - radius * 0.9f)
                lineTo(center.x + radius * 0.64f, center.y - radius * 0.2f)
                lineTo(center.x + radius * 0.42f, center.y + radius * 0.75f)
                lineTo(center.x - radius * 0.42f, center.y + radius * 0.75f)
                lineTo(center.x - radius * 0.64f, center.y - radius * 0.2f)
                close()
            }
            drawPath(crystal, color.copy(alpha = color.alpha * 0.18f), style = Fill)
            drawPath(crystal, color, style = Stroke(thinStroke))
            drawLine(color, Offset(center.x, center.y - radius * 0.82f), Offset(center.x, center.y + radius * 0.66f), thinStroke)
            drawLine(color, Offset(center.x - radius * 0.58f, center.y - radius * 0.15f), Offset(center.x, center.y + radius * 0.03f), thinStroke)
            drawLine(color, Offset(center.x + radius * 0.58f, center.y - radius * 0.15f), Offset(center.x, center.y + radius * 0.03f), thinStroke)
        }

        CanvasGlyphStyle.SLASH_BARS -> {
            repeat(3) { index ->
                val x = center.x + (index - 1) * radius * 0.46f
                drawLine(
                    color.copy(alpha = color.alpha * (0.62f + index * 0.18f)),
                    Offset(x - radius * 0.26f, center.y + radius * 0.72f),
                    Offset(x + radius * 0.26f, center.y - radius * 0.72f),
                    stroke,
                    StrokeCap.Round,
                )
            }
        }

        CanvasGlyphStyle.SHIELD -> {
            val shield = glyphShieldPath(center, radius * 0.88f)
            drawPath(shield, color.copy(alpha = color.alpha * 0.2f), style = Fill)
            drawPath(shield, color, style = Stroke(stroke))
            drawArc(
                color,
                205f,
                130f,
                false,
                Offset(center.x - radius * 0.45f, center.y - radius * 0.38f),
                Size(radius * 0.9f, radius * 0.9f),
                style = Stroke(thinStroke, cap = StrokeCap.Round),
            )
        }

        CanvasGlyphStyle.BRICK_LINES -> {
            repeat(3) { row ->
                val y = center.y + (row - 1) * radius * 0.48f
                val offset = if (row % 2 == 0) 0f else radius * 0.22f
                drawLine(color, Offset(center.x - radius * 0.8f + offset, y), Offset(center.x - radius * 0.05f + offset, y), stroke, StrokeCap.Round)
                drawLine(color, Offset(center.x + radius * 0.05f + offset, y), Offset(center.x + radius * 0.8f, y), stroke, StrokeCap.Round)
            }
        }

        CanvasGlyphStyle.INTERLOCKING_RINGS -> {
            drawOval(
                color,
                topLeft = Offset(center.x - radius * 0.86f, center.y - radius * 0.43f),
                size = Size(radius * 1.02f, radius * 0.86f),
                style = Stroke(stroke),
            )
            drawOval(
                color,
                topLeft = Offset(center.x - radius * 0.16f, center.y - radius * 0.43f),
                size = Size(radius * 1.02f, radius * 0.86f),
                style = Stroke(stroke),
            )
            drawCircle(color, radius * 0.12f, center)
        }

        CanvasGlyphStyle.THREE_BLADE -> {
            drawCircle(color.copy(alpha = color.alpha * 0.16f), radius * 0.82f, center)
            drawCircle(color, radius * 0.78f, center, style = Stroke(thinStroke))
            repeat(3) { index ->
                val angle = -PI.toFloat() / 2f + index * GLYPH_TAU / 3f
                val bladeCenter = glyphPolar(center, radius * 0.39f, angle)
                drawGlyphPolygon(bladeCenter, radius * 0.31f, 3, angle, color, Fill)
                drawLine(color, center, bladeCenter, thinStroke, StrokeCap.Round)
            }
            drawCircle(GlyphCutout, radius * 0.17f, center)
            drawCircle(color, radius * 0.12f, center)
        }
    }
}

private fun glyphShieldPath(center: Offset, radius: Float): Path = Path().apply {
    moveTo(center.x, center.y - radius)
    lineTo(center.x + radius * 0.76f, center.y - radius * 0.58f)
    lineTo(center.x + radius * 0.62f, center.y + radius * 0.34f)
    quadraticTo(center.x + radius * 0.34f, center.y + radius * 0.82f, center.x, center.y + radius)
    quadraticTo(center.x - radius * 0.34f, center.y + radius * 0.82f, center.x - radius * 0.62f, center.y + radius * 0.34f)
    lineTo(center.x - radius * 0.76f, center.y - radius * 0.58f)
    close()
}

private fun DrawScope.drawGlyphPolygon(
    center: Offset,
    radius: Float,
    sides: Int,
    rotation: Float,
    color: Color,
    style: androidx.compose.ui.graphics.drawscope.DrawStyle,
) {
    val path = Path()
    repeat(sides) { index ->
        val angle = rotation + index * GLYPH_TAU / sides
        val point = glyphPolar(center, radius, angle)
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    drawPath(path, color, style = style)
}

private fun glyphPolar(center: Offset, radius: Float, angle: Float): Offset =
    Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
