// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.presentation.canvas

import kinetickk.core.design.*

import kinetickk.core.content.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import kinetickk.feature.gameplay.domain.model.clamp
import kinetickk.core.content.CoreShape
import kinetickk.feature.gameplay.domain.model.damageNumberTier
import kinetickk.feature.gameplay.domain.model.DamageNumberTier
import kinetickk.core.content.RelicId
import kinetickk.core.content.WeaponId
import kinetickk.feature.gameplay.domain.model.WeaponNodeType
import kinetickk.feature.gameplay.domain.renderModel.GameplayRenderModel
import kinetickk.feature.gameplay.domain.renderModel.VisualFxProjection
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

internal fun DrawScope.drawWorld(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    textMeasurer: TextMeasurer,
) {
    val core = world(engine, engine.coreX, engine.coreY, shakeX, shakeY)
    val pointer = Offset(engine.pointerX + shakeX * 0.18f, engine.pointerY + shakeY * 0.18f)

    drawTotem(engine, shakeX, shakeY, textMeasurer)
    drawShockwaves(engine, visualFx, shakeX, shakeY)
    drawMotionEchoes(engine, visualFx, shakeX, shakeY)
    drawTrail(engine, shakeX, shakeY)
    drawWeaponNodes(engine, shakeX, shakeY)
    drawPickups(engine, shakeX, shakeY)
    drawProjectiles(engine, shakeX, shakeY)
    engine.enemies.forEach { drawEnemy(engine, it, shakeX, shakeY) }
    drawWeapon(engine, core, shakeX, shakeY)
    drawWeaponArcs(engine, visualFx, shakeX, shakeY)
    drawParticles(engine, visualFx, shakeX, shakeY)

    val danger = engine.tetherDistance < 75f
    val tetherColor = when {
        danger -> Red
        engine.polarityStability < 0.22f -> Red
        engine.polarityStability < 0.58f -> Orange
        engine.tetherDistance < 145f -> Orange
        else -> Cyan
    }
    val tetherPulse = (sin(engine.elapsed * 10f) + 1f) * 0.5f
    val strained = engine.polarityStability < 0.58f
    drawLine(tetherColor.copy(alpha = 0.08f + if (danger || strained) tetherPulse * 0.08f else 0f), core, pointer, if (danger || strained) 13f else 9f, StrokeCap.Round)
    drawLine(tetherColor.copy(alpha = if (danger || strained) 0.78f else 0.55f), core, pointer, if (danger || strained) 2f else 1.4f, StrokeCap.Round, dashEffect)
    drawCore(engine, core)
    drawSingularity(pointer, engine.elapsed, danger)

    val settings = engine.settings
    if (settings.damageNumbers) {
        visualFx.damageNumbers.forEach { number ->
            val location = world(engine, number.x, number.y, shakeX, shakeY)
            val tier = damageNumberTier(number.amount, settings.damageNumberTierThreshold, number.critical)
            drawLabel(
                textMeasurer = textMeasurer,
                text = number.formattedAmount(settings.damageNumberFormat),
                x = location.x,
                y = location.y,
                fontSize = 12f * settings.damageNumberSize.scale * DamageNumberTierScales[tier.ordinal],
                color = DamageNumberColors[tier.ordinal],
                centered = true,
                weight = if (number.critical || tier >= DamageNumberTier.POWERFUL) FontWeight.Bold else FontWeight.Normal,
                alpha = clamp(number.life / 0.3f, 0f, 1f),
            )
        }
    }
}

internal fun DrawScope.drawMotionEchoes(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
) {
    visualFx.motionEchoes.forEach { echo ->
        val center = world(engine, echo.x, echo.y, shakeX, shakeY)
        if (!isOnScreen(center, 60f)) return@forEach
        val life = clamp(echo.life / echo.maxLife, 0f, 1f)
        val alpha = life * life * echo.intensity
        val radius = GameplayRenderModel.CORE_RADIUS + (1f - life) * 9f
        drawCircle(Cyan.copy(alpha = alpha * 0.07f), radius + 12f, center)
        drawCircle(White.copy(alpha = alpha * 0.32f), radius, center, style = Stroke(1.2f + echo.intensity))
    }
}

internal fun DrawScope.drawShockwaves(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
) {
    visualFx.shockwaves.forEach { wave ->
        val center = world(engine, wave.x, wave.y, shakeX, shakeY)
        if (!isOnScreen(center, wave.maxRadius)) return@forEach
        val life = clamp(wave.life / wave.maxLife, 0f, 1f)
        val progress = 1f - life
        val eased = 1f - (1f - progress) * (1f - progress)
        val radius = max(2f, wave.maxRadius * eased)
        val color = ParticleColors[wave.colorIndex.coerceIn(ParticleColors.indices)]
        drawCircle(color.copy(alpha = life * 0.08f), radius * 0.72f, center)
        drawCircle(color.copy(alpha = life * 0.68f), radius, center, style = Stroke(1f + life * 2.2f))
    }
}

internal fun DrawScope.drawTrail(engine: GameplayRenderModel, shakeX: Float, shakeY: Float) {
    var previousLocation: Offset? = null
    var previousLife = 0f
    engine.trail.forEach { point ->
        val location = world(engine, point.x, point.y, shakeX, shakeY)
        val life = clamp(1f - point.age / 2.25f, 0f, 1f)
        previousLocation?.let { previous ->
            val segmentLife = min(previousLife, life)
            if (segmentLife > 0f && (isOnScreen(previous, 50f) || isOnScreen(location, 50f))) {
                drawLine(Violet.copy(alpha = segmentLife * 0.1f), previous, location, 17f + segmentLife * 13f, StrokeCap.Round)
                drawLine(Magenta.copy(alpha = segmentLife * 0.28f), previous, location, 7f + segmentLife * 7f, StrokeCap.Round)
                drawLine(Cyan.copy(alpha = segmentLife * 0.42f), previous, location, 1.4f + segmentLife * 2f, StrokeCap.Round)
            }
        }
        previousLocation = location
        previousLife = life
    }
}

internal fun DrawScope.drawScreenFx(engine: GameplayRenderModel, renderTime: Float) {
    if (engine.damageFlash > 0f) {
        drawRect(Red.copy(alpha = engine.damageFlash * 0.08f))
        drawRect(Red.copy(alpha = engine.damageFlash * 0.72f), style = Stroke(5f + engine.damageFlash * 10f))
    }
    if (engine.overheated) {
        val pulse = (sin(renderTime * 11f) + 1f) * 0.5f
        drawRect(Orange.copy(alpha = 0.18f + pulse * 0.16f), style = Stroke(4f + pulse * 4f))
    }
    if (engine.overdriveTime > 0f) {
        val pulse = (sin(renderTime * 7f) + 1f) * 0.5f
        drawRect(Acid.copy(alpha = 0.08f + pulse * 0.05f), style = Stroke(3f))
    }
}

internal fun DrawScope.drawWeapon(engine: GameplayRenderModel, core: Offset, shakeX: Float, shakeY: Float) {
    val motionAngle = if (engine.speed > 1f) atan2(engine.velocityY, engine.velocityX) else 0f
    when (engine.weapon) {
        WeaponId.FLUX_WAKE -> Unit
        WeaponId.MORNINGSTAR -> {
            val ball = world(engine, engine.morningstarX, engine.morningstarY, shakeX, shakeY)
            drawLine(Violet.copy(alpha = 0.24f), core, ball, 7f)
            drawLine(Cyan.copy(alpha = 0.8f), core, ball, 1.5f, pathEffect = dashEffect)
            drawCircle(Magenta.copy(alpha = 0.18f), 34f, ball)
            drawCircle(Violet.copy(alpha = 0.4f), 26f, ball)
            drawPolygon(ball, 20f, 8, engine.morningstarAngle, White, Fill)
            drawPolygon(ball, 25f, 8, engine.morningstarAngle, Magenta, Stroke(2f))
            val agonyRank = engine.relicRank(RelicId.AGONY_SCEPTER)
            if (agonyRank > 0) {
                val agonyBall = world(
                    engine,
                    engine.coreX * 2f - engine.morningstarX,
                    engine.coreY * 2f - engine.morningstarY,
                    shakeX,
                    shakeY,
                )
                val agonyRadius = 17f + agonyRank
                drawLine(Gold.copy(alpha = 0.2f), core, agonyBall, 7f)
                drawLine(Red.copy(alpha = 0.88f), core, agonyBall, 1.5f, pathEffect = dashEffect)
                drawCircle(Red.copy(alpha = 0.13f), agonyRadius + 13f, agonyBall)
                drawCircle(Gold.copy(alpha = 0.3f), agonyRadius + 5f, agonyBall)
                drawPolygon(agonyBall, agonyRadius, 8, -engine.morningstarAngle, White, Fill)
                drawPolygon(agonyBall, agonyRadius + 5f, 8, -engine.morningstarAngle, Gold, Stroke(2f))
            }
        }
        WeaponId.PHASE_LATTICE -> {
            val pulse = (sin(engine.elapsed * 4f) + 1f) * 0.5f
            drawCircle(Violet.copy(alpha = 0.08f), 132f, core)
            drawCircle(Cyan.copy(alpha = 0.42f), 105f + pulse * 18f, core, style = Stroke(1.5f))
            drawCircle(Magenta.copy(alpha = 0.25f), 132f - pulse * 18f, core, style = Stroke(1f, pathEffect = dashEffect))
        }
        WeaponId.NULL_LANCE -> {
            val tip = polar(core, 48f, motionAngle)
            drawLine(Cyan.copy(alpha = 0.22f), core, polar(core, 115f, motionAngle), 7f, StrokeCap.Round)
            drawLine(White, core, tip, 2f, StrokeCap.Round)
            drawPolygon(tip, 7f, 3, motionAngle, Cyan, Fill)
        }
        WeaponId.GRAVITY_MINES -> {
            drawCircle(Violet.copy(alpha = 0.22f), 29f, core, style = Stroke(1.5f, pathEffect = dashEffect))
        }
        WeaponId.ION_SWARM -> engine.weaponOrbitals.forEach { orbital ->
            val point = world(engine, orbital.x, orbital.y, shakeX, shakeY)
            drawCircle(Cyan.copy(alpha = 0.14f), 14f, point)
            drawPolygon(point, 7f, 4, engine.elapsed * 2f + orbital.index, Cyan, Fill)
        }
        WeaponId.RIFT_BLADES -> engine.weaponOrbitals.forEach { orbital ->
            val point = world(engine, orbital.x, orbital.y, shakeX, shakeY)
            drawPolygon(point, 16f, 4, engine.elapsed * 4.2f + orbital.index, Magenta, Fill)
            drawLine(Violet.copy(alpha = 0.25f), core, point, 1f, pathEffect = dashEffect)
        }
        WeaponId.ARC_COIL -> {
            val pulse = (sin(engine.elapsed * 9f) + 1f) * 0.5f
            drawCircle(Violet.copy(alpha = 0.3f), 31f + pulse * 7f, core, style = Stroke(2f))
            drawCircle(Cyan.copy(alpha = 0.45f), 22f - pulse * 4f, core, style = Stroke(1f, pathEffect = dashEffect))
        }
        WeaponId.QUASAR_CANNON -> {
            val rear = polar(core, 19f, motionAngle + PI.toFloat())
            val tip = polar(core, 55f, motionAngle)
            drawLine(Orange.copy(alpha = 0.28f), rear, tip, 14f, StrokeCap.Round)
            drawLine(White, core, tip, 3f, StrokeCap.Round)
            drawCircle(Orange, 7f, tip)
        }
        WeaponId.ENTROPY_FIELD -> {
            val radius = 170f + engine.weaponLevel * 5f
            drawCircle(Red.copy(alpha = 0.045f), radius, core)
            drawCircle(Magenta.copy(alpha = 0.28f), radius, core, style = Stroke(1.2f, pathEffect = dashEffect))
        }
        WeaponId.SINGULARITY_SPEAR -> {
            drawCircle(White.copy(alpha = 0.25f), 36f, core, style = Stroke(2f))
            if (engine.weaponBeamTime > 0f) {
                val start = world(engine, engine.weaponBeamStartX, engine.weaponBeamStartY, shakeX, shakeY)
                val end = world(engine, engine.weaponBeamEndX, engine.weaponBeamEndY, shakeX, shakeY)
                val alpha = clamp(engine.weaponBeamTime / 0.18f, 0f, 1f)
                drawLine(Violet.copy(alpha = alpha * 0.22f), start, end, 19f, StrokeCap.Round)
                drawLine(White.copy(alpha = alpha), start, end, 4f, StrokeCap.Round)
                drawLine(Cyan.copy(alpha = alpha), start, end, 1.3f, StrokeCap.Round)
            }
        }
        WeaponId.PRISM_RELAY -> {
            val pulse = (sin(engine.elapsed * 7f) + 1f) * 0.5f
            drawPolygon(core, 27f + pulse * 4f, 4, engine.elapsed * 0.8f, Color(0xFF73A6FF), Stroke(2f))
            drawPolygon(core, 15f, 4, -engine.elapsed * 1.2f, White, Fill)
        }
    }
}

internal fun DrawScope.drawWeaponNodes(engine: GameplayRenderModel, shakeX: Float, shakeY: Float) {
    engine.weaponNodes.forEach { node ->
        if (node.type == WeaponNodeType.GRAVITY_MINE) {
            val point = world(engine, node.x, node.y, shakeX, shakeY)
            val ratio = clamp(node.life / node.maxLife, 0f, 1f)
            drawCircle(Violet.copy(alpha = 0.08f + (1f - ratio) * 0.16f), node.radius, point)
            drawCircle(Magenta.copy(alpha = 0.65f), node.radius * ratio, point, style = Stroke(1.5f, pathEffect = dashEffect))
            drawPolygon(point, 11f, 6, engine.elapsed * 2.5f, White, Stroke(1.5f))
        }
    }
}

internal fun DrawScope.drawWeaponArcs(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
) {
    visualFx.weaponArcs.forEachIndexed { index, arc ->
        val start = world(engine, arc.fromX, arc.fromY, shakeX, shakeY)
        val end = world(engine, arc.toX, arc.toY, shakeX, shakeY)
        val dx = end.x - start.x
        val dy = end.y - start.y
        val length = max(1f, kotlin.math.sqrt(dx * dx + dy * dy))
        val nx = -dy / length
        val ny = dx / length
        var previous = start
        repeat(6) { segment ->
            val t = (segment + 1f) / 6f
            val jitter = if (segment == 5) 0f else sin(index * 7f + segment * 4.1f + engine.elapsed * 40f) * 8f
            val next = Offset(start.x + dx * t + nx * jitter, start.y + dy * t + ny * jitter)
            drawLine(Cyan.copy(alpha = clamp(arc.life / 0.14f, 0f, 1f)), previous, next, 2f, StrokeCap.Round)
            previous = next
        }
    }
}

internal fun DrawScope.drawCore(engine: GameplayRenderModel, center: Offset) {
    val speedRatio = speedVisualRatio(engine.speed)
    val phase = engine.dashPhaseTime > 0f
    val main = if (phase) White else Cyan
    drawCircle(Cyan.copy(alpha = 0.06f + speedRatio * 0.08f), 50f + speedRatio * 15f, center)
    drawCircle(Violet.copy(alpha = 0.18f), 31f, center)
    if (engine.overdriveTime > 0f) {
        val pulse = (sin(engine.elapsed * 9f) + 1f) * 0.5f
        drawCircle(Acid.copy(alpha = 0.07f + pulse * 0.05f), 58f + pulse * 8f, center)
        drawArc(
            Acid.copy(alpha = 0.72f),
            engine.elapsed * 90f,
            118f,
            false,
            Offset(center.x - 43f, center.y - 43f),
            Size(86f, 86f),
            style = Stroke(2f, cap = StrokeCap.Round),
        )
        drawArc(
            Cyan.copy(alpha = 0.5f),
            -engine.elapsed * 115f + 180f,
            92f,
            false,
            Offset(center.x - 36f, center.y - 36f),
            Size(72f, 72f),
            style = Stroke(1.5f, cap = StrokeCap.Round),
        )
    }
    if (engine.braking) {
        val compression = (sin(engine.elapsed * 15f) + 1f) * 0.5f
        drawCircle(Violet.copy(alpha = 0.12f), 42f - compression * 7f, center)
        drawCircle(Violet.copy(alpha = 0.72f), 47f - compression * 11f, center, style = Stroke(2f, pathEffect = dashEffect))
    }
    if (engine.speed > 25f) {
        val length = 42f + 118f * speedRatio
        val magnitude = max(1f, engine.speed)
        val tail = Offset(center.x - engine.velocityX / magnitude * length, center.y - engine.velocityY / magnitude * length)
        drawLine(Cyan.copy(alpha = 0.24f + if (phase) 0.18f else 0f), tail, center, if (phase) 22f else 13f, StrokeCap.Round)
        drawLine(White.copy(alpha = 0.7f), tail, center, 2f, StrokeCap.Round)
    }
    if (engine.maxShield > 0f && engine.shield > 0f) {
        val shieldRatio = clamp(engine.shield / engine.maxShield, 0f, 1f)
        drawArc(
            color = Violet.copy(alpha = 0.35f + shieldRatio * 0.45f),
            startAngle = -90f,
            sweepAngle = 360f * shieldRatio,
            useCenter = false,
            topLeft = Offset(center.x - 29f, center.y - 29f),
            size = Size(58f, 58f),
            style = Stroke(2.5f, cap = StrokeCap.Round),
        )
    }
    when (engine.coreShape) {
        CoreShape.ORB -> {
            drawCircle(main, GameplayRenderModel.CORE_RADIUS, center)
            drawCircle(SpaceBlack, 7f, center)
            drawCircle(Violet, 4f, center)
        }
        CoreShape.PRISM -> {
            drawPolygon(center, 21f, 4, engine.elapsed * 1.3f + (PI / 4).toFloat(), main, Fill)
            drawPolygon(center, 13f, 4, -engine.elapsed * 1.8f, SpaceBlack, Fill)
            drawCircle(Violet, 4f, center)
        }
        CoreShape.SHARD -> {
            drawPolygon(center, 23f, 3, engine.elapsed * 1.2f - (PI / 2).toFloat(), main, Fill)
            drawPolygon(center, 14f, 3, -engine.elapsed * 1.7f, SpaceBlack, Fill)
            drawCircle(Magenta, 4f, center)
        }
    }
    if (engine.dashPhaseTime > 0f) drawCircle(White.copy(alpha = 0.8f), 28f + engine.dashPhaseTime * 50f, center, style = Stroke(2f))
}
