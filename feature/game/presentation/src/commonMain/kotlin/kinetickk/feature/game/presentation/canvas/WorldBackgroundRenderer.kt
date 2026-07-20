// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.presentation.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import kinetickk.feature.game.domain.model.clamp
import kinetickk.feature.game.domain.model.GamePhase
import kinetickk.feature.game.domain.model.ParticleDensity
import kinetickk.feature.game.domain.projection.GameProjection
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

internal fun DrawScope.drawBackdrop(engine: GameProjection, shakeX: Float, shakeY: Float, renderTime: Float) {
    val menuDrift = if (engine.phase == GamePhase.MENU) renderTime * 13f else 0f
    val backdropCameraX = engine.cameraX + menuDrift
    val backdropCameraY = engine.cameraY + menuDrift * 0.38f

    drawGridLayer(backdropCameraX, backdropCameraY, 172f, 0.42f, GridBlue.copy(alpha = 0.26f), 1.35f, shakeX, shakeY)
    drawGridLayer(backdropCameraX, backdropCameraY, 86f, 1f, GridBlue.copy(alpha = 0.18f), 0.8f, shakeX, shakeY)

    val startCellX = floor((engine.cameraX - size.width * 0.5f) / 180f).toInt() - 1
    val endCellX = ceil((engine.cameraX + size.width * 0.5f) / 180f).toInt() + 1
    val startCellY = floor((engine.cameraY - size.height * 0.5f) / 180f).toInt() - 1
    val endCellY = ceil((engine.cameraY + size.height * 0.5f) / 180f).toInt() + 1
    for (cellX in startCellX..endCellX) {
        for (cellY in startCellY..endCellY) {
            val hash = abs(cellX * 7_919 + cellY * 104_729)
            if (hash % 4 == 0) {
                val worldX = cellX * 180f + (hash % 91)
                val worldY = cellY * 180f + ((hash / 97) % 113)
                val point = world(engine, worldX, worldY, shakeX, shakeY)
                val twinkle = 0.25f + (sin(renderTime * (0.8f + hash % 5 * 0.17f) + hash) + 1f) * 0.13f
                drawCircle(if (hash % 3 == 0) Violet else Cyan, 0.9f + hash % 3 * 0.28f, point, alpha = twinkle)
            }
        }
    }
    drawSpeedField(engine, shakeX, shakeY)
    drawRect(Color.Black.copy(alpha = 0.15f), style = Stroke(28f))
    drawRect(Color.Black.copy(alpha = 0.08f), style = Stroke(76f))
}

internal fun DrawScope.drawGridLayer(
    cameraX: Float,
    cameraY: Float,
    spacing: Float,
    parallax: Float,
    color: Color,
    lineWidth: Float,
    shakeX: Float,
    shakeY: Float,
) {
    val offsetX = positiveModulo(-cameraX * parallax + size.width * 0.5f, spacing)
    val offsetY = positiveModulo(-cameraY * parallax + size.height * 0.5f, spacing)
    var x = offsetX - spacing
    while (x < size.width + spacing) {
        drawLine(color, Offset(x + shakeX, 0f), Offset(x + shakeX, size.height), lineWidth)
        x += spacing
    }
    var y = offsetY - spacing
    while (y < size.height + spacing) {
        drawLine(color, Offset(0f, y + shakeY), Offset(size.width, y + shakeY), lineWidth)
        y += spacing
    }
}

internal fun DrawScope.drawSpeedField(engine: GameProjection, shakeX: Float, shakeY: Float) {
    val rawSpeed = engine.speed
    val speedRatio = speedVisualRatio(rawSpeed)
    val dashBoost = clamp(engine.dashPhaseTime / 0.24f, 0f, 1f)
    val intensity = clamp(sqrt(speedRatio) * 0.78f + dashBoost * 0.32f, 0f, 1f)
    if (rawSpeed < 18f && dashBoost <= 0f) return

    val speed = max(1f, rawSpeed)
    val directionX = engine.velocityX / speed
    val directionY = engine.velocityY / speed
    val core = world(engine, engine.coreX, engine.coreY, shakeX, shakeY)
    val count = when (engine.settings.particleDensity) {
        ParticleDensity.LOW -> 12
        ParticleDensity.NORMAL -> 22
        ParticleDensity.HIGH -> 32
    }
    val margin = 150f
    val fieldWidth = size.width + margin * 2f
    val fieldHeight = size.height + margin * 2f
    val clearRadiusSquared = 105f * 105f
    val fullStrengthRadiusSquared = 340f * 340f

    repeat(count) { index ->
        val seedX = ((index * 73 + 19) % 101) / 101f
        val seedY = ((index * 47 + 31) % 97) / 97f
        val depth = ((index * 37 + 11) % 100) / 100f
        val parallax = 0.14f + depth * 0.34f
        val x = positiveModulo(seedX * fieldWidth - engine.cameraX * parallax, fieldWidth) - margin
        val y = positiveModulo(seedY * fieldHeight - engine.cameraY * parallax, fieldHeight) - margin
        val end = Offset(x + shakeX, y + shakeY)
        val distanceX = end.x - core.x
        val distanceY = end.y - core.y
        val distanceSquared = distanceX * distanceX + distanceY * distanceY
        val centerFade = 0.12f + 0.88f * clamp(
            (distanceSquared - clearRadiusSquared) / (fullStrengthRadiusSquared - clearRadiusSquared),
            0f,
            1f,
        )
        val length = 5f + intensity * (28f + depth * 54f)
        val start = Offset(end.x - directionX * length, end.y - directionY * length)
        val variation = 0.82f + ((index * 29) % 19) / 100f
        val alpha = (0.025f + intensity * (0.075f + depth * 0.045f)) * centerFade * variation
        val width = 0.55f + depth * 0.55f + intensity * 0.35f
        val color = if (index % 7 == 0) White else Cyan

        drawLine(Cyan.copy(alpha = alpha * 0.24f), start, end, width * 3f, StrokeCap.Round)
        drawLine(color.copy(alpha = alpha), start, end, width, StrokeCap.Round)
    }
}
