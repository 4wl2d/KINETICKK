// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.foundation.design.CanvasGlyphStyle
import kinetickk.foundation.design.drawCanvasGlyph
import kinetickk.foundation.design.drawPolygon
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin

private val ItemIconInk = Color(0xFFF4F6FF)
private val ItemIconBackground = Color(0xFF050610)

/** Gameplay-owned composition of Content item semantics from mechanical canvas glyphs. */
internal fun DrawScope.drawItemIcon(
    item: ItemDefinition,
    center: Offset,
    radius: Float,
    accent: Color,
    stack: Int? = null,
    obscured: Boolean = false,
) {
    if (radius <= 0f) return

    val frameStroke = (radius * 0.075f).coerceAtLeast(0.7f)
    val rank = item.rarity.rank.coerceIn(1, 5)
    drawCircle(accent.copy(alpha = accent.alpha * 0.09f), radius * 1.13f, center)
    drawPolygon(
        center = center,
        radius = radius,
        sides = if (obscured) 4 else rank + 3,
        rotation = -(PI / 2.0).toFloat(),
        color = accent.copy(alpha = accent.alpha * 0.78f),
        style = Stroke(frameStroke),
    )

    val visibleRank = if (obscured) 1 else rank
    repeat(visibleRank) { index ->
        val angle = -(PI / 2.0).toFloat() + (index - (visibleRank - 1) * 0.5f) * 0.19f
        drawCircle(
            color = accent,
            radius = (radius * 0.038f).coerceAtLeast(0.55f),
            center = iconPolar(center, radius * 0.84f, angle),
        )
    }

    val stackValue = stack?.coerceIn(0, item.maxStacks)
    if (stackValue != null && stackValue > 0) {
        val ringRadius = radius * 1.075f
        drawArc(
            color = ItemIconInk.copy(alpha = 0.62f),
            startAngle = -90f,
            sweepAngle = 360f * stackValue / item.maxStacks,
            useCenter = false,
            topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
            size = Size(ringRadius * 2f, ringRadius * 2f),
            style = Stroke((radius * 0.055f).coerceAtLeast(0.65f), cap = StrokeCap.Round),
        )
    }

    if (obscured) {
        drawCircle(ItemIconBackground.copy(alpha = 0.76f), radius * 0.56f, center)
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
        style = item.primary.effect.toCanvasGlyphStyle(),
        center = Offset(center.x - radius * 0.08f, center.y - radius * 0.08f),
        radius = radius * 0.65f,
        color = accent.copy(alpha = accent.alpha * 0.94f),
    )

    val secondaryCenter = Offset(center.x + radius * 0.18f, center.y + radius * 0.17f)
    drawCircle(ItemIconBackground.copy(alpha = 0.86f), radius * 0.43f, secondaryCenter)
    drawCircle(
        color = accent.copy(alpha = accent.alpha * 0.58f),
        radius = radius * 0.43f,
        center = secondaryCenter,
        style = Stroke((radius * 0.055f).coerceAtLeast(0.6f)),
    )
    drawCanvasGlyph(
        style = item.secondary.effect.toCanvasGlyphStyle(),
        center = secondaryCenter,
        radius = radius * 0.34f,
        color = ItemIconInk,
    )
}

private fun ItemEffect.toCanvasGlyphStyle(): CanvasGlyphStyle = when (this) {
    ItemEffect.IMPACT_DAMAGE -> CanvasGlyphStyle.SUNBURST
    ItemEffect.WEAPON_POWER -> CanvasGlyphStyle.TRIPLE_ARROW
    ItemEffect.MASS -> CanvasGlyphStyle.CONCENTRIC_ORB
    ItemEffect.MAGNETISM -> CanvasGlyphStyle.HORSESHOE
    ItemEffect.COOLING -> CanvasGlyphStyle.SNOWFLAKE
    ItemEffect.MAX_INTEGRITY -> CanvasGlyphStyle.HEX_CROSS
    ItemEffect.REGEN -> CanvasGlyphStyle.LEAF
    ItemEffect.DASH_POWER -> CanvasGlyphStyle.DOUBLE_CHEVRON
    ItemEffect.DASH_EFFICIENCY -> CanvasGlyphStyle.ORBIT_ARROWS
    ItemEffect.CRIT_CHANCE -> CanvasGlyphStyle.RETICLE
    ItemEffect.CRIT_DAMAGE -> CanvasGlyphStyle.BOLT
    ItemEffect.PICKUP_RADIUS -> CanvasGlyphStyle.RADIAL_NODES
    ItemEffect.LUCK -> CanvasGlyphStyle.FOUR_LEAF
    ItemEffect.DATA_GAIN -> CanvasGlyphStyle.CIRCUIT_LINES
    ItemEffect.MATTER_GAIN -> CanvasGlyphStyle.CRYSTAL
    ItemEffect.ATTACK_SPEED -> CanvasGlyphStyle.SLASH_BARS
    ItemEffect.SHIELD_CAPACITY -> CanvasGlyphStyle.SHIELD
    ItemEffect.DAMAGE_REDUCTION -> CanvasGlyphStyle.BRICK_LINES
    ItemEffect.COMBO_WINDOW -> CanvasGlyphStyle.INTERLOCKING_RINGS
    ItemEffect.OVERDRIVE_GAIN -> CanvasGlyphStyle.THREE_BLADE
}

private fun iconPolar(center: Offset, radius: Float, angle: Float): Offset =
    Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
