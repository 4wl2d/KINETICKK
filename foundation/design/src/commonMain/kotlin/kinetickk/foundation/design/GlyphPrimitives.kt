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

private val GlyphCutout = Kk.Ink
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
    /** A dot between two pairs of facing arcs (sound spreading both ways). */
    SOUND_WAVES,
    /** A heavy ring around an offset hollow (an eclipse with nothing inside). */
    HOLLOW_RING,
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
        CanvasGlyphStyle.SUNBURST -> drawSunburstGlyph(center, radius, color, thinStroke)
        CanvasGlyphStyle.TRIPLE_ARROW -> drawTripleArrowGlyph(center, radius, color, stroke, thinStroke)
        CanvasGlyphStyle.CONCENTRIC_ORB -> drawConcentricOrbGlyph(center, radius, color, stroke)
        CanvasGlyphStyle.HORSESHOE -> drawHorseshoeGlyph(center, radius, color, stroke)
        CanvasGlyphStyle.SNOWFLAKE -> drawSnowflakeGlyph(center, radius, color, thinStroke)
        CanvasGlyphStyle.HEX_CROSS -> drawHexCrossGlyph(center, radius, color, stroke)
        CanvasGlyphStyle.LEAF -> drawLeafGlyph(center, radius, color, thinStroke)
        CanvasGlyphStyle.DOUBLE_CHEVRON -> drawDoubleChevronGlyph(center, radius, color)
        CanvasGlyphStyle.ORBIT_ARROWS -> drawOrbitArrowsGlyph(center, radius, color, thinStroke)
        CanvasGlyphStyle.RETICLE -> drawReticleGlyph(center, radius, color, thinStroke)
        CanvasGlyphStyle.BOLT -> drawBoltGlyph(center, radius, color, thinStroke)
        CanvasGlyphStyle.RADIAL_NODES -> drawRadialNodesGlyph(center, radius, color, thinStroke)
        CanvasGlyphStyle.FOUR_LEAF -> drawFourLeafGlyph(center, radius, color, thinStroke)
        CanvasGlyphStyle.CIRCUIT_LINES -> drawCircuitLinesGlyph(center, radius, color, thinStroke)
        CanvasGlyphStyle.CRYSTAL -> drawCrystalGlyph(center, radius, color, thinStroke)
        CanvasGlyphStyle.SLASH_BARS -> drawSlashBarsGlyph(center, radius, color, stroke)
        CanvasGlyphStyle.SHIELD -> drawShieldGlyph(center, radius, color, stroke, thinStroke)
        CanvasGlyphStyle.BRICK_LINES -> drawBrickLinesGlyph(center, radius, color, stroke)
        CanvasGlyphStyle.INTERLOCKING_RINGS -> drawInterlockingRingsGlyph(center, radius, color, stroke)
        CanvasGlyphStyle.THREE_BLADE -> drawThreeBladeGlyph(center, radius, color, thinStroke)
        CanvasGlyphStyle.SOUND_WAVES -> drawSoundWavesGlyph(center, radius, color, thinStroke)
        CanvasGlyphStyle.HOLLOW_RING -> drawHollowRingGlyph(center, radius, color, stroke, thinStroke)
    }
}

private fun DrawScope.drawSunburstGlyph(center: Offset, radius: Float, color: Color, thinStroke: Float) {
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

private fun DrawScope.drawTripleArrowGlyph(center: Offset, radius: Float, color: Color, stroke: Float, thinStroke: Float) {
    // Legacy name: three speed lines stopped by impact bars (no arrowheads).
    repeat(3) { index ->
        val y = center.y + (index - 1) * radius * 0.42f
        val end = center.x + radius * (0.82f - index * 0.1f)
        drawLine(color, Offset(center.x - radius * 0.78f, y), Offset(end - radius * 0.2f, y), thinStroke, StrokeCap.Round)
        drawLine(color, Offset(end, y - radius * 0.17f), Offset(end, y + radius * 0.17f), stroke, StrokeCap.Round)
    }
}

private fun DrawScope.drawConcentricOrbGlyph(center: Offset, radius: Float, color: Color, stroke: Float) {
    drawCircle(color.copy(alpha = color.alpha * 0.18f), radius * 0.84f, center)
    drawCircle(color, radius * 0.72f, center, style = Stroke(stroke))
    drawCircle(color.copy(alpha = color.alpha * 0.84f), radius * 0.42f, center)
    drawCircle(GlyphCutout.copy(alpha = 0.72f), radius * 0.13f, center)
}

private fun DrawScope.drawHorseshoeGlyph(center: Offset, radius: Float, color: Color, stroke: Float) {
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

private fun DrawScope.drawSnowflakeGlyph(center: Offset, radius: Float, color: Color, thinStroke: Float) {
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

private fun DrawScope.drawHexCrossGlyph(center: Offset, radius: Float, color: Color, stroke: Float) {
    drawGlyphPolygon(center, radius * 0.82f, 6, (PI / 6.0).toFloat(), color, Stroke(stroke))
    drawLine(color, Offset(center.x - radius * 0.4f, center.y), Offset(center.x + radius * 0.4f, center.y), stroke, StrokeCap.Round)
    drawLine(color, Offset(center.x, center.y - radius * 0.4f), Offset(center.x, center.y + radius * 0.4f), stroke, StrokeCap.Round)
}

private fun DrawScope.drawLeafGlyph(center: Offset, radius: Float, color: Color, thinStroke: Float) {
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

private fun DrawScope.drawDoubleChevronGlyph(center: Offset, radius: Float, color: Color) {
    // Legacy name: two sheared slabs leaning into travel (no chevrons).
    repeat(2) { index ->
        val x = center.x + (index - 0.5f) * radius * 0.72f
        val slab = Path().apply {
            moveTo(x - radius * 0.02f, center.y - radius * 0.68f)
            lineTo(x + radius * 0.3f, center.y - radius * 0.68f)
            lineTo(x + radius * 0.02f, center.y + radius * 0.68f)
            lineTo(x - radius * 0.3f, center.y + radius * 0.68f)
            close()
        }
        drawPath(slab, color.copy(alpha = color.alpha * (0.55f + index * 0.45f)), style = Fill)
    }
}

private fun DrawScope.drawOrbitArrowsGlyph(center: Offset, radius: Float, color: Color, thinStroke: Float) {
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
    // Legacy name: orbit arcs ending in nodes (no arrowheads).
    drawCircle(color, radius * 0.15f, glyphPolar(center, radius * 0.73f, (72f * PI / 180f).toFloat()))
    drawCircle(color, radius * 0.15f, glyphPolar(center, radius * 0.73f, (252f * PI / 180f).toFloat()))
}

private fun DrawScope.drawReticleGlyph(center: Offset, radius: Float, color: Color, thinStroke: Float) {
    drawCircle(color, radius * 0.56f, center, style = Stroke(thinStroke))
    drawCircle(color, radius * 0.16f, center)
    drawLine(color, Offset(center.x - radius * 0.95f, center.y), Offset(center.x - radius * 0.36f, center.y), thinStroke, StrokeCap.Round)
    drawLine(color, Offset(center.x + radius * 0.36f, center.y), Offset(center.x + radius * 0.95f, center.y), thinStroke, StrokeCap.Round)
    drawLine(color, Offset(center.x, center.y - radius * 0.95f), Offset(center.x, center.y - radius * 0.36f), thinStroke, StrokeCap.Round)
    drawLine(color, Offset(center.x, center.y + radius * 0.36f), Offset(center.x, center.y + radius * 0.95f), thinStroke, StrokeCap.Round)
}

private fun DrawScope.drawBoltGlyph(center: Offset, radius: Float, color: Color, thinStroke: Float) {
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

private fun DrawScope.drawRadialNodesGlyph(center: Offset, radius: Float, color: Color, thinStroke: Float) {
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

private fun DrawScope.drawFourLeafGlyph(center: Offset, radius: Float, color: Color, thinStroke: Float) {
    repeat(4) { index ->
        val leafCenter = glyphPolar(center, radius * 0.4f, index * GLYPH_TAU / 4f)
        drawCircle(color.copy(alpha = color.alpha * 0.2f), radius * 0.34f, leafCenter)
        drawCircle(color, radius * 0.34f, leafCenter, style = Stroke(thinStroke))
    }
    drawGlyphPolygon(center, radius * 0.22f, 4, (PI / 4.0).toFloat(), color, Fill)
}

private fun DrawScope.drawCircuitLinesGlyph(center: Offset, radius: Float, color: Color, thinStroke: Float) {
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

private fun DrawScope.drawCrystalGlyph(center: Offset, radius: Float, color: Color, thinStroke: Float) {
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

private fun DrawScope.drawSlashBarsGlyph(center: Offset, radius: Float, color: Color, stroke: Float) {
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

private fun DrawScope.drawShieldGlyph(center: Offset, radius: Float, color: Color, stroke: Float, thinStroke: Float) {
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

private fun DrawScope.drawBrickLinesGlyph(center: Offset, radius: Float, color: Color, stroke: Float) {
    repeat(3) { row ->
        val y = center.y + (row - 1) * radius * 0.48f
        val offset = if (row % 2 == 0) 0f else radius * 0.22f
        drawLine(color, Offset(center.x - radius * 0.8f + offset, y), Offset(center.x - radius * 0.05f + offset, y), stroke, StrokeCap.Round)
        drawLine(color, Offset(center.x + radius * 0.05f + offset, y), Offset(center.x + radius * 0.8f, y), stroke, StrokeCap.Round)
    }
}

private fun DrawScope.drawInterlockingRingsGlyph(center: Offset, radius: Float, color: Color, stroke: Float) {
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

private fun DrawScope.drawThreeBladeGlyph(center: Offset, radius: Float, color: Color, thinStroke: Float) {
    drawCircle(color.copy(alpha = color.alpha * 0.16f), radius * 0.82f, center)
    drawCircle(color, radius * 0.78f, center, style = Stroke(thinStroke))
    repeat(3) { index ->
        val angle = -PI.toFloat() / 2f + index * GLYPH_TAU / 3f
        val bladeCenter = glyphPolar(center, radius * 0.39f, angle)
        drawGlyphPolygon(bladeCenter, radius * 0.27f, 4, angle, color, Fill)
        drawLine(color, center, bladeCenter, thinStroke, StrokeCap.Round)
    }
    drawCircle(GlyphCutout, radius * 0.17f, center)
    drawCircle(color, radius * 0.12f, center)
}

private fun DrawScope.drawSoundWavesGlyph(center: Offset, radius: Float, color: Color, thinStroke: Float) {
    drawCircle(color, radius * 0.17f, center)
    for (ring in 1..2) {
        val arc = radius * (0.2f + 0.34f * ring)
        val topLeft = Offset(center.x - arc, center.y - arc)
        val size = Size(arc * 2f, arc * 2f)
        drawArc(color, -48f, 96f, false, topLeft, size, style = Stroke(thinStroke, cap = StrokeCap.Round))
        drawArc(color, 132f, 96f, false, topLeft, size, style = Stroke(thinStroke, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawHollowRingGlyph(center: Offset, radius: Float, color: Color, stroke: Float, thinStroke: Float) {
    drawCircle(color.copy(alpha = color.alpha * 0.2f), radius * 0.86f, center)
    drawCircle(color, radius * 0.8f, center, style = Stroke(stroke))
    val hollow = Offset(center.x + radius * 0.16f, center.y - radius * 0.12f)
    drawCircle(GlyphCutout, radius * 0.46f, hollow)
    drawCircle(color, radius * 0.46f, hollow, style = Stroke(thinStroke))
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
