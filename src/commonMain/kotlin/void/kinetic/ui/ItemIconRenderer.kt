package void.kinetic.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import void.kinetic.model.ItemDefinition
import void.kinetic.model.ItemEffect

private val ItemIconInk = Color(0xFFF4F6FF)
private val ItemIconVoid = Color(0xFF050610)
private const val ITEM_ICON_TAU = 6.2831855f

/**
 * Draws an artifact as two deliberately overlapping effect glyphs.
 *
 * The large rarity-colored glyph is the primary modifier. The smaller white
 * glyph is the secondary modifier, offset just enough for repeated effects to
 * remain legible. Rarity changes the outer frame geometry, while the optional
 * stack value fills the thin progress ring.
 */
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
    drawItemIconPolygon(
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
            center = itemIconPolar(center, radius * 0.84f, angle),
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
        drawCircle(ItemIconVoid.copy(alpha = 0.76f), radius * 0.56f, center)
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

    val primaryCenter = Offset(center.x - radius * 0.08f, center.y - radius * 0.08f)
    drawItemEffectGlyph(
        effect = item.primary.effect,
        center = primaryCenter,
        radius = radius * 0.65f,
        color = accent.copy(alpha = accent.alpha * 0.94f),
    )

    val secondaryCenter = Offset(center.x + radius * 0.18f, center.y + radius * 0.17f)
    drawCircle(ItemIconVoid.copy(alpha = 0.86f), radius * 0.43f, secondaryCenter)
    drawCircle(
        color = accent.copy(alpha = accent.alpha * 0.58f),
        radius = radius * 0.43f,
        center = secondaryCenter,
        style = Stroke((radius * 0.055f).coerceAtLeast(0.6f)),
    )
    drawItemEffectGlyph(
        effect = item.secondary.effect,
        center = secondaryCenter,
        radius = radius * 0.34f,
        color = ItemIconInk,
    )
}

private fun DrawScope.drawItemEffectGlyph(effect: ItemEffect, center: Offset, radius: Float, color: Color) {
    val stroke = (radius * 0.13f).coerceAtLeast(0.65f)
    val thinStroke = (radius * 0.085f).coerceAtLeast(0.55f)

    when (effect) {
        ItemEffect.IMPACT_DAMAGE -> {
            repeat(8) { index ->
                val angle = index * ITEM_ICON_TAU / 8f
                drawLine(
                    color,
                    itemIconPolar(center, radius * 0.56f, angle),
                    itemIconPolar(center, radius * 0.94f, angle),
                    thinStroke,
                    StrokeCap.Round,
                )
            }
            drawItemIconPolygon(center, radius * 0.48f, 4, (PI / 4.0).toFloat(), color, Fill)
        }

        ItemEffect.WEAPON_POWER -> {
            repeat(3) { index ->
                val y = center.y + (index - 1) * radius * 0.42f
                val tip = Offset(center.x + radius * (0.82f - index * 0.1f), y)
                drawLine(color, Offset(center.x - radius * 0.78f, y), tip, thinStroke, StrokeCap.Round)
                drawLine(color, tip, Offset(tip.x - radius * 0.28f, tip.y - radius * 0.22f), thinStroke, StrokeCap.Round)
                drawLine(color, tip, Offset(tip.x - radius * 0.28f, tip.y + radius * 0.22f), thinStroke, StrokeCap.Round)
            }
        }

        ItemEffect.MASS -> {
            drawCircle(color.copy(alpha = color.alpha * 0.18f), radius * 0.84f, center)
            drawCircle(color, radius * 0.72f, center, style = Stroke(stroke))
            drawCircle(color.copy(alpha = color.alpha * 0.84f), radius * 0.42f, center)
            drawCircle(ItemIconVoid.copy(alpha = 0.72f), radius * 0.13f, center)
        }

        ItemEffect.MAGNETISM -> {
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

        ItemEffect.COOLING -> {
            repeat(3) { index ->
                val angle = index * (PI / 3.0).toFloat()
                val start = itemIconPolar(center, radius * 0.82f, angle)
                val end = itemIconPolar(center, radius * 0.82f, angle + PI.toFloat())
                drawLine(color, start, end, thinStroke, StrokeCap.Round)
                repeat(2) { direction ->
                    val branchAngle = angle + if (direction == 0) 0f else PI.toFloat()
                    val branchRoot = itemIconPolar(center, radius * 0.56f, branchAngle)
                    drawLine(color, branchRoot, itemIconPolar(branchRoot, radius * 0.25f, branchAngle + 2.45f), thinStroke, StrokeCap.Round)
                    drawLine(color, branchRoot, itemIconPolar(branchRoot, radius * 0.25f, branchAngle - 2.45f), thinStroke, StrokeCap.Round)
                }
            }
        }

        ItemEffect.MAX_INTEGRITY -> {
            drawItemIconPolygon(center, radius * 0.82f, 6, (PI / 6.0).toFloat(), color, Stroke(stroke))
            drawLine(color, Offset(center.x - radius * 0.4f, center.y), Offset(center.x + radius * 0.4f, center.y), stroke, StrokeCap.Round)
            drawLine(color, Offset(center.x, center.y - radius * 0.4f), Offset(center.x, center.y + radius * 0.4f), stroke, StrokeCap.Round)
        }

        ItemEffect.REGEN -> {
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

        ItemEffect.DASH_POWER -> {
            repeat(2) { index ->
                val shift = (index - 0.5f) * radius * 0.72f
                drawLine(color, Offset(center.x - radius * 0.46f + shift, center.y - radius * 0.68f), Offset(center.x + radius * 0.18f + shift, center.y), stroke, StrokeCap.Round)
                drawLine(color, Offset(center.x + radius * 0.18f + shift, center.y), Offset(center.x - radius * 0.46f + shift, center.y + radius * 0.68f), stroke, StrokeCap.Round)
            }
        }

        ItemEffect.DASH_EFFICIENCY -> {
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
            drawItemIconPolygon(Offset(center.x + radius * 0.72f, center.y - radius * 0.2f), radius * 0.24f, 3, 0.25f, color, Fill)
            drawItemIconPolygon(Offset(center.x - radius * 0.72f, center.y + radius * 0.2f), radius * 0.24f, 3, PI.toFloat() + 0.25f, color, Fill)
        }

        ItemEffect.CRIT_CHANCE -> {
            drawCircle(color, radius * 0.56f, center, style = Stroke(thinStroke))
            drawCircle(color, radius * 0.16f, center)
            drawLine(color, Offset(center.x - radius * 0.95f, center.y), Offset(center.x - radius * 0.36f, center.y), thinStroke, StrokeCap.Round)
            drawLine(color, Offset(center.x + radius * 0.36f, center.y), Offset(center.x + radius * 0.95f, center.y), thinStroke, StrokeCap.Round)
            drawLine(color, Offset(center.x, center.y - radius * 0.95f), Offset(center.x, center.y - radius * 0.36f), thinStroke, StrokeCap.Round)
            drawLine(color, Offset(center.x, center.y + radius * 0.36f), Offset(center.x, center.y + radius * 0.95f), thinStroke, StrokeCap.Round)
        }

        ItemEffect.CRIT_DAMAGE -> {
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

        ItemEffect.PICKUP_RADIUS -> {
            drawCircle(color.copy(alpha = color.alpha * 0.2f), radius * 0.7f, center)
            drawCircle(color, radius * 0.7f, center, style = Stroke(thinStroke))
            repeat(4) { index ->
                val angle = index * ITEM_ICON_TAU / 4f
                val outside = itemIconPolar(center, radius * 0.95f, angle)
                val inside = itemIconPolar(center, radius * 0.48f, angle)
                drawCircle(color, radius * 0.12f, outside)
                drawLine(color, outside, inside, thinStroke, StrokeCap.Round)
            }
        }

        ItemEffect.LUCK -> {
            repeat(4) { index ->
                val leafCenter = itemIconPolar(center, radius * 0.4f, index * ITEM_ICON_TAU / 4f)
                drawCircle(color.copy(alpha = color.alpha * 0.2f), radius * 0.34f, leafCenter)
                drawCircle(color, radius * 0.34f, leafCenter, style = Stroke(thinStroke))
            }
            drawItemIconPolygon(center, radius * 0.22f, 4, (PI / 4.0).toFloat(), color, Fill)
        }

        ItemEffect.DATA_GAIN -> {
            repeat(3) { index ->
                val y = center.y + (index - 1) * radius * 0.48f
                val nodeOnRight = index % 2 == 0
                val nodeX = center.x + if (nodeOnRight) radius * 0.68f else -radius * 0.68f
                drawLine(color, Offset(center.x - radius * 0.76f, y), Offset(center.x + radius * 0.76f, y), thinStroke, StrokeCap.Round)
                drawCircle(ItemIconVoid, radius * 0.16f, Offset(nodeX, y))
                drawCircle(color, radius * 0.14f, Offset(nodeX, y), style = Stroke(thinStroke))
            }
            drawLine(color.copy(alpha = color.alpha * 0.7f), Offset(center.x, center.y - radius * 0.48f), Offset(center.x, center.y + radius * 0.48f), thinStroke)
        }

        ItemEffect.MATTER_GAIN -> {
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

        ItemEffect.ATTACK_SPEED -> {
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

        ItemEffect.SHIELD_CAPACITY -> {
            val shield = itemShieldPath(center, radius * 0.88f)
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

        ItemEffect.DAMAGE_REDUCTION -> {
            repeat(3) { row ->
                val y = center.y + (row - 1) * radius * 0.48f
                val offset = if (row % 2 == 0) 0f else radius * 0.22f
                drawLine(color, Offset(center.x - radius * 0.8f + offset, y), Offset(center.x - radius * 0.05f + offset, y), stroke, StrokeCap.Round)
                drawLine(color, Offset(center.x + radius * 0.05f + offset, y), Offset(center.x + radius * 0.8f, y), stroke, StrokeCap.Round)
            }
        }

        ItemEffect.COMBO_WINDOW -> {
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

        ItemEffect.OVERDRIVE_GAIN -> {
            drawCircle(color.copy(alpha = color.alpha * 0.16f), radius * 0.82f, center)
            drawCircle(color, radius * 0.78f, center, style = Stroke(thinStroke))
            repeat(3) { index ->
                val angle = -PI.toFloat() / 2f + index * ITEM_ICON_TAU / 3f
                val bladeCenter = itemIconPolar(center, radius * 0.39f, angle)
                drawItemIconPolygon(bladeCenter, radius * 0.31f, 3, angle, color, Fill)
                drawLine(color, center, bladeCenter, thinStroke, StrokeCap.Round)
            }
            drawCircle(ItemIconVoid, radius * 0.17f, center)
            drawCircle(color, radius * 0.12f, center)
        }
    }
}

private fun itemShieldPath(center: Offset, radius: Float): Path = Path().apply {
    moveTo(center.x, center.y - radius)
    lineTo(center.x + radius * 0.76f, center.y - radius * 0.58f)
    lineTo(center.x + radius * 0.62f, center.y + radius * 0.34f)
    quadraticTo(center.x + radius * 0.34f, center.y + radius * 0.82f, center.x, center.y + radius)
    quadraticTo(center.x - radius * 0.34f, center.y + radius * 0.82f, center.x - radius * 0.62f, center.y + radius * 0.34f)
    lineTo(center.x - radius * 0.76f, center.y - radius * 0.58f)
    close()
}

private fun DrawScope.drawItemIconPolygon(
    center: Offset,
    radius: Float,
    sides: Int,
    rotation: Float,
    color: Color,
    style: androidx.compose.ui.graphics.drawscope.DrawStyle,
) {
    val path = Path()
    repeat(sides) { index ->
        val angle = rotation + index * ITEM_ICON_TAU / sides
        val point = itemIconPolar(center, radius, angle)
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    drawPath(path, color, style = style)
}

private fun itemIconPolar(center: Offset, radius: Float, angle: Float): Offset =
    Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
