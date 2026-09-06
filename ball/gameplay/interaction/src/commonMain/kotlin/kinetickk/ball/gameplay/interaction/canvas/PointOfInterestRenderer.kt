// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.content.api.localizedContent

import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.foundation.common.localization.text

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.*
import kotlin.math.*

internal fun DrawScope.drawPointsOfInterest(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, textMeasurer: TextMeasurer) {
    for (point in engine.pointsOfInterest) {
        val center = world(engine, point.x, point.y, shakeX, shakeY)
        val tint = when (point.kind) {
            PointOfInterestKind.RESONANT_CIRCUIT -> Cyan
            PointOfInterestKind.SEALED_ANOMALY -> Violet
            PointOfInterestKind.COLLAPSING_ORBIT -> Gold
        }
        if (!point.active) {
            drawCircle(tint.copy(alpha = 0.09f), 65f, center)
            drawCircle(tint.copy(alpha = 0.7f), 65f, center, style = Stroke(2f))
            val labelX = center.x.coerceIn(120f, max(120f, size.width - 120f))
            val labelY = center.y.coerceIn(120f, max(120f, size.height - 160f))
            drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.PointTimer, point.name.localizedContent(textMeasurer.language), point.remaining.toInt()), labelX, labelY - 78f,
                fontSize = 12f, color = tint, centered = true, maxWidth = size.width - 24f, maxLines = 2)
            if (!isOnScreen(center, 30f)) {
                val arrow = Offset(labelX, labelY)
                drawLine(tint, arrow, arrow + (center - arrow) / max(1f, (center - arrow).getDistance()) * 26f, 2f)
            }
            continue
        }
        when (point.kind) {
            PointOfInterestKind.RESONANT_CIRCUIT -> {
                val beacons = listOf(center, center + Offset(230f, 200f), center + Offset(-230f, 200f))
                repeat(3) { index ->
                    drawLine(tint.copy(alpha = 0.2f), beacons[index], beacons[(index + 1) % 3], 2f)
                    val target = index == point.nextBeacon % 3
                    drawCircle(tint.copy(alpha = if (target) 0.9f else 0.25f), 55f, beacons[index], style = Stroke(if (target) 3f else 1f))
                    drawLabel(textMeasurer, "${index + 1}", beacons[index].x, beacons[index].y, fontSize = 17f, color = tint, centered = true)
                }
            }
            PointOfInterestKind.SEALED_ANOMALY -> {
                drawCircle(tint.copy(alpha = 0.08f), 290f, center)
                drawCircle(tint.copy(alpha = 0.4f), 45f, center, style = Stroke(3f))
                for (enemy in engine.enemies) if (enemy.id in point.defenderIds && !enemy.dead) {
                    val location = world(engine, enemy.x, enemy.y, shakeX, shakeY)
                    drawCircle(Gold, enemy.radius + 9f, location, style = Stroke(2f))
                    drawLine(tint.copy(alpha = 0.18f), center, location, 1f)
                }
            }
            PointOfInterestKind.COLLAPSING_ORBIT -> {
                drawCircle(tint.copy(alpha = 0.10f), 155f, center, style = Stroke(70f))
                drawCircle(tint.copy(alpha = 0.6f), 120f, center, style = Stroke(1.5f))
                drawCircle(tint.copy(alpha = 0.6f), 190f, center, style = Stroke(1.5f))
                if (point.warningRemaining > 0f) {
                    val direction = Offset(cos(point.volleyAngle), sin(point.volleyAngle))
                    val lateral = Offset(-direction.y, direction.x)
                    repeat(3) { lane ->
                        val offset = lateral * ((lane - 1) * 60f)
                        drawLine(Red.copy(alpha = 0.75f), center + offset - direction * 320f,
                            center + offset + direction * 320f, 3f)
                    }
                }
            }
        }
        drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.PointProgress, point.name.localizedContent(textMeasurer.language), (point.progress * 100).toInt(), ceil(point.remaining).toInt()),
            center.x, center.y - 215f, fontSize = 13f, color = tint, centered = true, maxWidth = size.width - 24f, maxLines = 2)
    }
}
