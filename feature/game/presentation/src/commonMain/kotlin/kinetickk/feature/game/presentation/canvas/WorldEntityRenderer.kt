// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.presentation.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import kinetickk.feature.game.domain.model.clamp
import kinetickk.feature.game.domain.model.EnemyType
import kinetickk.feature.game.domain.model.PickupType
import kinetickk.feature.game.domain.model.TAU
import kinetickk.feature.game.domain.projection.EnemyProjection
import kinetickk.feature.game.domain.projection.GameProjection
import kinetickk.feature.game.domain.projection.VisualFxProjection
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

internal fun DrawScope.drawSingularity(center: Offset, time: Float, danger: Boolean) {
    val color = if (danger) Red else Magenta
    val pulse = (sin(time * 7f) + 1f) * 0.5f
    drawCircle(color.copy(alpha = 0.08f), 34f + pulse * 8f, center)
    drawCircle(color.copy(alpha = 0.24f), 22f + pulse * 3f, center)
    drawCircle(SpaceBlack, 12f, center)
    drawCircle(color, 15f, center, style = Stroke(2f))
    repeat(4) { index ->
        val angle = time * 1.9f + index * TAU / 4f
        val start = Offset(center.x + cos(angle) * 20f, center.y + sin(angle) * 20f)
        val end = Offset(center.x + cos(angle + 0.55f) * 28f, center.y + sin(angle + 0.55f) * 28f)
        drawLine(color.copy(alpha = 0.68f), start, end, 1.5f)
    }
}

internal fun DrawScope.drawEnemy(engine: GameProjection, enemy: EnemyProjection, shakeX: Float, shakeY: Float) {
    val center = world(engine, enemy.x, enemy.y, shakeX, shakeY)
    val renderMargin = if (enemy.type == EnemyType.WARDEN) 460f else 120f
    if (center.x < -renderMargin || center.y < -renderMargin || center.x > size.width + renderMargin || center.y > size.height + renderMargin) return
    val health = clamp(enemy.hp / enemy.maxHp, 0f, 1f)
    val baseColor = when (enemy.type) {
        EnemyType.DRIFTER -> Violet
        EnemyType.SHOOTER -> Magenta
        EnemyType.CHARGER -> Orange
        EnemyType.INTERCEPTOR -> Cyan
        EnemyType.WEAVER -> Blue
        EnemyType.WARDEN -> Violet
        EnemyType.SPLITTER -> Gold
        EnemyType.ELITE -> Acid
        EnemyType.ARCHITECT -> Red
    }
    val flashColor = if (enemy.flash > 0.5f) White else baseColor
    val rotation = engine.elapsed * when (enemy.type) {
        EnemyType.DRIFTER -> 0.8f
        EnemyType.SHOOTER -> -1.1f
        EnemyType.CHARGER -> 1.6f
        EnemyType.INTERCEPTOR -> -2.4f
        EnemyType.WEAVER -> 1.95f
        EnemyType.WARDEN -> -0.32f
        EnemyType.SPLITTER -> 0.62f
        EnemyType.ELITE -> -0.55f
        EnemyType.ARCHITECT -> 0.22f
    } + enemy.id
    val sides = when (enemy.type) {
        EnemyType.DRIFTER -> 6
        EnemyType.SHOOTER -> 3
        EnemyType.CHARGER -> 4
        EnemyType.INTERCEPTOR -> 5
        EnemyType.WEAVER -> 7
        EnemyType.WARDEN -> 8
        EnemyType.SPLITTER -> 10
        EnemyType.ELITE -> 6
        EnemyType.ARCHITECT -> 8
    }
    val core = world(engine, engine.coreX, engine.coreY, shakeX, shakeY)
    when (enemy.type) {
        EnemyType.SHOOTER -> if (enemy.actionTimer in 0f..0.42f) {
            val charge = 1f - enemy.actionTimer / 0.42f
            drawLine(Red.copy(alpha = 0.12f + charge * 0.42f), center, core, 1f + charge * 1.2f, pathEffect = dashEffect)
            drawCircle(Magenta.copy(alpha = 0.5f), enemy.radius + 15f * (1f - charge), center, style = Stroke(1.5f))
        }
        EnemyType.CHARGER -> if (enemy.actionTimer < 0f) {
            val charge = clamp(-enemy.actionTimer / 0.45f, 0f, 1f)
            drawLine(Orange.copy(alpha = 0.2f + charge * 0.62f), center, core, 2f + charge * 2f, StrokeCap.Round)
            drawCircle(Orange.copy(alpha = 0.72f), enemy.radius + 28f * (1f - charge), center, style = Stroke(2f, pathEffect = dashEffect))
        }
        EnemyType.INTERCEPTOR -> {
            val predictedCore = world(
                engine,
                engine.coreX + engine.velocityX * 0.45f,
                engine.coreY + engine.velocityY * 0.45f,
                shakeX,
                shakeY,
            )
            drawLine(Cyan.copy(alpha = 0.32f), center, predictedCore, 1.4f, pathEffect = dashEffect)
            drawCircle(Cyan.copy(alpha = 0.12f), 13f, predictedCore)
            drawPolygon(predictedCore, 5f, 3, atan2(predictedCore.y - center.y, predictedCore.x - center.x), Cyan, Fill)
        }
        EnemyType.WEAVER -> {
            repeat(2) { index ->
                val orbitAngle = engine.elapsed * 2.7f + enemy.id + index * PI.toFloat()
                val mote = polar(center, enemy.radius + 10f, orbitAngle)
                drawLine(Blue.copy(alpha = 0.42f), center, mote, 1.2f)
                drawCircle(Blue, 3f, mote)
            }
            if (enemy.actionTimer in 0f..0.34f) {
                drawLine(Blue.copy(alpha = 0.48f), polar(center, enemy.radius + 19f, rotation), polar(center, enemy.radius + 19f, rotation + PI.toFloat()), 2f)
            }
        }
        EnemyType.WARDEN -> {
            val gravityPulse = (sin(engine.elapsed * 2.2f + enemy.id) + 1f) * 0.5f
            drawCircle(Violet.copy(alpha = 0.035f + gravityPulse * 0.025f), 440f, center)
            drawCircle(Violet.copy(alpha = 0.24f), 440f, center, style = Stroke(1.2f, pathEffect = dashEffect))
            repeat(6) { index ->
                val angle = index * TAU / 6f - engine.elapsed * 0.22f
                val outer = polar(center, enemy.radius + 24f, angle)
                val inner = polar(center, enemy.radius + 12f, angle)
                drawLine(Violet.copy(alpha = 0.68f), outer, inner, 2f, StrokeCap.Round)
            }
            if (enemy.actionTimer in 0f..0.38f) {
                drawCircle(Red.copy(alpha = 0.52f), enemy.radius + 18f + enemy.actionTimer * 50f, center, style = Stroke(2f))
            }
        }
        EnemyType.SPLITTER -> {
            repeat(2) { index ->
                val fragmentAngle = rotation * 1.7f + index * PI.toFloat()
                val fragment = polar(center, enemy.radius * 0.38f, fragmentAngle)
                drawPolygon(fragment, enemy.radius * 0.3f, 6, -rotation, Gold.copy(alpha = 0.72f), Stroke(1.4f))
            }
            drawLine(
                Gold.copy(alpha = 0.7f),
                Offset(center.x - enemy.radius * 0.7f, center.y + enemy.radius * 0.55f),
                Offset(center.x + enemy.radius * 0.62f, center.y - enemy.radius * 0.58f),
                1.5f,
                pathEffect = dashEffect,
            )
        }
        EnemyType.ELITE -> {
            repeat(6) { index ->
                val angle = -engine.elapsed * 0.8f + index * TAU / 6f
                val inner = polar(center, enemy.radius + 9f, angle)
                val outer = polar(center, enemy.radius + 16f, angle)
                drawLine(Acid.copy(alpha = 0.65f), inner, outer, 2f, StrokeCap.Round)
            }
            if (enemy.actionTimer in 0f..0.28f) {
                drawCircle(Acid.copy(alpha = 0.48f), enemy.radius + 20f + enemy.actionTimer * 38f, center, style = Stroke(1.5f))
            }
        }
        EnemyType.ARCHITECT -> {
            val pulse = (sin(engine.elapsed * 3.4f) + 1f) * 0.5f
            drawCircle(Red.copy(alpha = 0.15f), enemy.radius + 30f + pulse * 12f, center, style = Stroke(2f, pathEffect = dashEffect))
            drawCircle(Violet.copy(alpha = 0.11f), enemy.radius + 58f - pulse * 10f, center, style = Stroke(1.3f))
        }
        EnemyType.DRIFTER -> Unit
    }
    drawCircle(flashColor.copy(alpha = 0.07f), enemy.radius * 2f, center)
    drawPolygon(center, enemy.radius, sides, rotation, flashColor.copy(alpha = 0.26f), Fill)
    drawPolygon(center, enemy.radius, sides, rotation, flashColor, Stroke(if (enemy.type == EnemyType.ARCHITECT) 3f else 1.8f))
    drawPolygon(center, enemy.radius * 0.52f, sides, -rotation * 1.4f, flashColor.copy(alpha = 0.7f), Stroke(1f))
    if (
        enemy.type == EnemyType.WARDEN ||
        enemy.type == EnemyType.SPLITTER ||
        enemy.type == EnemyType.ELITE ||
        enemy.type == EnemyType.ARCHITECT
    ) {
        val barWidth = when (enemy.type) {
            EnemyType.ARCHITECT -> 160f
            EnemyType.WARDEN -> 84f
            else -> 72f
        }
        drawBar(center.x - barWidth * 0.5f, center.y - enemy.radius - 18f, barWidth, 5f, health, flashColor, DarkLine)
    }
}

internal fun DrawScope.drawProjectiles(engine: GameProjection, shakeX: Float, shakeY: Float) {
    engine.projectiles.forEach { projectile ->
        val center = world(engine, projectile.x, projectile.y, shakeX, shakeY)
        if (!isOnScreen(center, projectile.radius + 28f)) return@forEach
        val previous = world(engine, projectile.previousX, projectile.previousY, shakeX, shakeY)
        if (projectile.hostile) {
            drawLine(Red.copy(alpha = 0.24f), previous, center, projectile.radius * 1.25f, StrokeCap.Round)
            drawCircle(Red.copy(alpha = 0.13f), projectile.radius + 8f, center)
            drawCircle(Magenta, projectile.radius, center)
            drawCircle(White, projectile.radius * 0.36f, center)
        } else {
            val color = projectile.sourceWeapon?.let(::weaponColor) ?: ParticleColors[projectile.colorIndex.coerceIn(ParticleColors.indices)]
            drawLine(color.copy(alpha = 0.25f), previous, center, projectile.radius * 1.8f, StrokeCap.Round)
            drawCircle(color.copy(alpha = 0.16f), projectile.radius + 9f, center)
            drawCircle(color, projectile.radius, center)
            drawCircle(White, max(1.5f, projectile.radius * 0.28f), center)
        }
    }
}

internal fun DrawScope.drawPickups(engine: GameProjection, shakeX: Float, shakeY: Float) {
    engine.pickups.forEach { pickup ->
        val center = world(engine, pickup.x, pickup.y, shakeX, shakeY)
        if (!isOnScreen(center, 28f)) return@forEach
        val previous = world(engine, pickup.previousX, pickup.previousY, shakeX, shakeY)
        when (pickup.type) {
            PickupType.DATA -> {
                drawLine(Cyan.copy(alpha = 0.28f), previous, center, 2f, StrokeCap.Round)
                drawCircle(Cyan.copy(alpha = 0.14f), 11f, center)
                drawPolygon(center, 6f, 4, engine.elapsed * 1.7f, Cyan, Fill)
            }
            PickupType.KEY -> {
                drawLine(Acid.copy(alpha = 0.25f), previous, center, 2.5f, StrokeCap.Round)
                drawCircle(Acid.copy(alpha = 0.14f), 18f, center)
                drawPolygon(center, 11f, 6, engine.elapsed, Acid, Stroke(2f))
                drawCircle(Acid, 3f, center)
            }
            PickupType.REPAIR -> {
                drawLine(Orange.copy(alpha = 0.25f), previous, center, 2f, StrokeCap.Round)
                drawCircle(Orange.copy(alpha = 0.16f), 15f, center)
                drawLine(Orange, Offset(center.x - 6f, center.y), Offset(center.x + 6f, center.y), 3f)
                drawLine(Orange, Offset(center.x, center.y - 6f), Offset(center.x, center.y + 6f), 3f)
            }
            PickupType.RELIC -> {
                drawLine(Gold.copy(alpha = 0.34f), previous, center, 2.8f, StrokeCap.Round)
                drawCircle(Gold.copy(alpha = 0.09f), 24f, center)
                drawUnresolvedRelicIcon(center, 13f, engine.elapsed)
            }
        }
    }
}

internal fun DrawScope.drawParticles(
    engine: GameProjection,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
) {
    visualFx.particles.forEach { particle ->
        val center = world(engine, particle.x, particle.y, shakeX, shakeY)
        if (!isOnScreen(center, 24f)) return@forEach
        val alpha = clamp(particle.life / particle.maxLife, 0f, 1f)
        val color = ParticleColors[particle.colorIndex.coerceIn(ParticleColors.indices)]
        val particleSpeed = kotlin.math.sqrt(particle.vx * particle.vx + particle.vy * particle.vy)
        if (particleSpeed > 70f) {
            val stretch = min(0.045f, 12f / particleSpeed)
            val tail = world(engine, particle.x - particle.vx * stretch, particle.y - particle.vy * stretch, shakeX, shakeY)
            drawLine(color.copy(alpha = alpha * 0.42f), tail, center, max(1f, particle.size * alpha * 0.65f), StrokeCap.Round)
        }
        drawCircle(color.copy(alpha = alpha), max(0.8f, particle.size * alpha), center)
    }
}

internal fun DrawScope.drawTotem(engine: GameProjection, shakeX: Float, shakeY: Float, textMeasurer: TextMeasurer) {
    val totem = engine.totem ?: return
    val location = world(engine, totem.x, totem.y, shakeX, shakeY)
    val onScreen = location.x in 45f..size.width - 45f && location.y in 45f..size.height - 45f
    if (onScreen) {
        val pulse = (sin(totem.pulse) + 1f) * 0.5f
        drawCircle(Acid.copy(alpha = 0.08f), 47f + pulse * 8f, location)
        drawPolygon(location, 30f, 6, totem.pulse * 0.25f, Acid.copy(alpha = 0.18f), Fill)
        drawPolygon(location, 30f, 6, totem.pulse * 0.25f, Acid, Stroke(2f))
        drawPolygon(location, 15f, 3, -totem.pulse, White, Stroke(1.5f))
        drawLabel(textMeasurer, "WEAPON TOTEM", location.x, location.y + 43f, 10f, Acid, centered = true)
    } else {
        val margin = 34f
        val edge = Offset(clamp(location.x, margin, size.width - margin), clamp(location.y, margin, size.height - margin))
        drawCircle(Acid.copy(alpha = 0.16f), 18f, edge)
        drawPolygon(edge, 10f, 3, kotlin.math.atan2(location.y - center.y, location.x - center.x), Acid, Fill)
    }
}
