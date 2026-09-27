// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.foundation.design.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.model.clamp
import kinetickk.ball.gameplay.interaction.fx.InteractionFxLimits
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val RAD_TO_DEG = 57.29578f

/** Degrees per second for a full turn every [seconds]. */
private fun spin(seconds: Float): Float = 360f / seconds

/**
 * The singularity (cursor): a threat ring with a soft halo, a slowly turning dashed outer ring, a
 * bone dot and four crosshair ticks (`kk.css .sing`). [danger] (contact is close) brightens the halo.
 */
internal fun DrawScope.drawSingularity(center: Offset, time: Float, danger: Boolean, roles: KkRolePalette) {
    val threat = roles.threat
    val pulse = if (danger) (sin(time * 10f) + 1f) * 0.5f else 0f
    drawCircle(threat.copy(alpha = 0.10f + pulse * 0.10f), 24f, center, style = kkStroke(8f))
    drawCircle(threat.copy(alpha = 0.05f + pulse * 0.06f), 28f, center, style = kkStroke(10f))
    rotate(time * spin(3.2f), center) {
        drawCircle(threat.copy(alpha = 0.7f), 27.25f, center, style = WorldStrokes.dashedThin)
    }
    drawCircle(Kk.Ink, 13.5f, center)
    drawCircle(threat, 13.5f, center, style = kkStroke(3f))
    drawCircle(Kk.Bone, 4f, center)
    drawRect(threat, Offset(center.x - 1f, center.y - 40f), Size(2f, 8f))
    drawRect(threat, Offset(center.x - 1f, center.y + 32f), Size(2f, 8f))
    drawRect(threat, Offset(center.x - 40f, center.y - 1f), Size(8f, 2f))
    drawRect(threat, Offset(center.x + 32f, center.y - 1f), Size(8f, 2f))
}

internal fun DrawScope.drawEnemy(
    engine: GameplayRenderModel,
    enemy: EnemyProjection,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    val center = world(engine, enemy.x, enemy.y, shakeX, shakeY)
    val renderMargin = if (enemy.type == EnemyType.WARDEN) 460f else if (enemy.type == EnemyType.ARCHITECT) 220f else 120f
    if (center.x < -renderMargin || center.y < -renderMargin || center.x > size.width + renderMargin || center.y > size.height + renderMargin) return
    val threat = roles.threat
    drawEnemyTelegraph(engine, enemy, center, shakeX, shakeY, threat)
    // Hit flash: the fill turns bone for the impact frame, then a short bone afterglow.
    val fill = when {
        enemy.flash > 0.9f -> Kk.Bone
        enemy.flash > 0f -> kkMix(Kk.Ink1, Kk.Bone, enemy.flash * 0.35f)
        else -> Kk.Ink1
    }
    if (enemy.type == EnemyType.ARCHITECT) {
        drawArchitect(engine, enemy, center, fill, roles)
        return
    }
    val silhouette = enemySilhouette(enemy.type)
    val path = WorldPaths.silhouette(silhouette, enemy.radius)
    val degrees = enemyRotation(enemy.type, engine.elapsed, enemy.id)
    if (enemy.type == EnemyType.ELITE) {
        val pulse = (sin(engine.elapsed * 3.2f) + 1f) * 0.5f
        drawCircle(threat.copy(alpha = 0.10f + pulse * 0.06f), enemy.radius * 1.45f, center)
        drawCircle(threat.copy(alpha = 0.05f + pulse * 0.04f), enemy.radius * 1.9f, center)
    }
    withTransform({
        translate(center.x, center.y)
        rotate(degrees, Offset.Zero)
    }) {
        drawPath(path, fill)
        drawKkThreatHatch(path, roles)
        drawPath(path, threat, style = kkStroke(if (enemy.type == EnemyType.ELITE) 3f else 2f))
        if (enemy.type == EnemyType.ELITE) {
            drawPath(WorldPaths.silhouette(silhouette, enemy.radius * 0.52f), threat.copy(alpha = 0.6f), style = kkStroke(1.5f))
        }
    }
    when (enemy.type) {
        EnemyType.SHOOTER -> drawCircle(threat, max(2.5f, enemy.radius * 0.2f), center)
        EnemyType.WARDEN -> drawCircle(threat, enemy.radius * 0.24f, center, style = kkStroke(2f))
        EnemyType.ELITE -> drawKkIcon(KkIcon.SYSTEM_ELITE, center, enemy.radius * 1.05f, threat)
        else -> Unit
    }
}

/** Slow turns (the elite spins once per 20 s, as on the boards); ids offset the phase. */
private fun enemyRotation(type: EnemyType, elapsed: Float, id: Int): Float {
    val degreesPerSecond = when (type) {
        EnemyType.DRIFTER -> 11f
        EnemyType.SHOOTER -> -8f
        EnemyType.CHARGER -> 6f
        EnemyType.INTERCEPTOR -> -10f
        EnemyType.WEAVER -> 12f
        EnemyType.WARDEN -> -5f
        EnemyType.SPLITTER -> 7f
        EnemyType.ELITE -> spin(20f)
        EnemyType.ARCHITECT -> 0f
    }
    return elapsed * degreesPerSecond + (id * 37 % 360)
}

/** Gameplay telegraphs keep their timing; only their look changed (threat lines and rings). */
private fun DrawScope.drawEnemyTelegraph(
    engine: GameplayRenderModel,
    enemy: EnemyProjection,
    center: Offset,
    shakeX: Float,
    shakeY: Float,
    threat: androidx.compose.ui.graphics.Color,
) {
    when (enemy.type) {
        EnemyType.SHOOTER -> if (enemy.actionTimer in 0f..0.42f) {
            val core = world(engine, engine.coreX, engine.coreY, shakeX, shakeY)
            val charge = 1f - enemy.actionTimer / 0.42f
            drawLine(threat.copy(alpha = 0.12f + charge * 0.42f), center, core, 1f + charge * 1.2f, pathEffect = WorldStrokes.dashEffect)
            drawCircle(threat.copy(alpha = 0.5f), enemy.radius + 15f * (1f - charge), center, style = kkStroke(1.5f))
        }
        EnemyType.CHARGER -> if (enemy.actionTimer < 0f) {
            val core = world(engine, engine.coreX, engine.coreY, shakeX, shakeY)
            val charge = clamp(-enemy.actionTimer / 0.45f, 0f, 1f)
            drawLine(threat.copy(alpha = 0.2f + charge * 0.62f), center, core, 2f + charge * 2f)
            drawCircle(threat.copy(alpha = 0.72f), enemy.radius + 28f * (1f - charge), center, style = WorldStrokes.dashedMedium)
        }
        EnemyType.INTERCEPTOR -> {
            val predicted = world(
                engine,
                engine.coreX + engine.velocityX * 0.45f,
                engine.coreY + engine.velocityY * 0.45f,
                shakeX,
                shakeY,
            )
            drawLine(threat.copy(alpha = 0.3f), center, predicted, 1.4f, pathEffect = WorldStrokes.dashEffect)
            drawCircle(threat.copy(alpha = 0.55f), 9f, predicted, style = kkStroke(1.5f))
            drawCircle(threat.copy(alpha = 0.55f), 2f, predicted)
        }
        EnemyType.WEAVER -> {
            repeat(2) { index ->
                val orbitAngle = engine.elapsed * 2.7f + enemy.id + index * PI.toFloat()
                drawCircle(threat.copy(alpha = 0.8f), 2.5f, polar(center, enemy.radius + 10f, orbitAngle))
            }
            if (enemy.actionTimer in 0f..0.34f) {
                val angle = engine.elapsed * 0.2f + enemy.id
                drawLine(threat.copy(alpha = 0.48f), polar(center, enemy.radius + 19f, angle),
                    polar(center, enemy.radius + 19f, angle + PI.toFloat()), 2f)
            }
        }
        EnemyType.WARDEN -> {
            // The gravity well's reach, kept to a hairline so several wardens never tint the arena.
            val gravityPulse = (sin(engine.elapsed * 2.2f + enemy.id) + 1f) * 0.5f
            drawCircle(threat.copy(alpha = 0.12f + gravityPulse * 0.08f), 440f, center, style = WorldStrokes.dashedHair)
            if (enemy.actionTimer in 0f..0.38f) {
                drawCircle(threat.copy(alpha = 0.52f), enemy.radius + 18f + enemy.actionTimer * 50f, center, style = kkStroke(2f))
            }
        }
        EnemyType.ELITE -> if (enemy.actionTimer in 0f..0.28f) {
            drawCircle(threat.copy(alpha = 0.48f), enemy.radius + 20f + enemy.actionTimer * 38f, center, style = kkStroke(1.5f))
        }
        EnemyType.DRIFTER, EnemyType.SPLITTER, EnemyType.ARCHITECT -> Unit
    }
}

/** The Architect: a triangle with a bone inner triangle and threat eye inside two counter-rotating frames. */
private fun DrawScope.drawArchitect(
    engine: GameplayRenderModel,
    enemy: EnemyProjection,
    center: Offset,
    fill: androidx.compose.ui.graphics.Color,
    roles: KkRolePalette,
) {
    val threat = roles.threat
    val radius = enemy.radius
    val outer = radius * 1.62f
    val inner = radius * 1.22f
    rotate(-engine.elapsed * spin(40f), center) {
        drawCircle(threat.copy(alpha = 0.4f), radius * 2.57f, center, style = WorldStrokes.dashedHair)
    }
    rotate(engine.elapsed * spin(18f) + 12f, center) {
        drawRect(threat, Offset(center.x - outer, center.y - outer), Size(outer * 2f, outer * 2f), style = kkStroke(2f))
    }
    rotate(-engine.elapsed * spin(12f) - 6f, center) {
        drawRect(threat.copy(alpha = 0.6f), Offset(center.x - inner, center.y - inner), Size(inner * 2f, inner * 2f), style = kkStroke(2f))
    }
    val triangle = WorldPaths.architectTriangle(radius)
    // The triangle's visual center sits below its box center (`M50 4 96 84H4Z`), as on the board.
    translate(center.x, center.y) {
        drawPath(triangle, fill)
        drawKkThreatHatch(triangle, roles)
        drawPath(triangle, threat, style = kkStroke(2.5f))
        drawPath(WorldPaths.architectInnerTriangle(radius), Kk.Bone, style = kkStroke(1.5f))
        drawCircle(threat, radius * 0.174f, Offset(0f, radius * 0.174f))
    }
}

internal fun DrawScope.drawProjectiles(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    for (index in engine.projectiles.indices) {
        val projectile = engine.projectiles[index]
        val center = world(engine, projectile.x, projectile.y, shakeX, shakeY)
        if (!isOnScreen(center, projectile.radius + 28f)) continue
        val previous = world(engine, projectile.previousX, projectile.previousY, shakeX, shakeY)
        if (projectile.hostile) {
            val threat = roles.threat
            drawLine(threat.copy(alpha = 0.22f), previous, center, projectile.radius * 1.1f)
            drawCircle(threat.copy(alpha = 0.1f), projectile.radius + 6f, center)
            drawCircle(threat.copy(alpha = 0.22f), projectile.radius + 2.5f, center)
            drawCircle(threat, projectile.radius, center)
            if (roles.hatchThreats) {
                translate(center.x, center.y) {
                    drawKkThreatHatch(WorldPaths.silhouette(EnemySilhouette.OCTAGON, projectile.radius), roles, Kk.Ink)
                }
            }
        } else {
            drawLine(roles.you.copy(alpha = 0.3f), previous, center, max(1.5f, projectile.radius * 1.2f))
            drawCircle(roles.you.copy(alpha = 0.18f), projectile.radius + 4f, center)
            drawCircle(Kk.Bone, max(2f, projectile.radius * 0.8f), center)
        }
    }
}

internal fun DrawScope.drawPickups(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    for (index in engine.pickups.indices) {
        val pickup = engine.pickups[index]
        val center = world(engine, pickup.x, pickup.y, shakeX, shakeY)
        if (!isOnScreen(center, 28f)) continue
        val previous = world(engine, pickup.previousX, pickup.previousY, shakeX, shakeY)
        when (pickup.type) {
            PickupType.DATA -> {
                drawLine(Kk.Bone.copy(alpha = 0.22f), previous, center, 2f)
                // Bone diamond; sizes vary a little per pickup like the boards.
                val half = 3.2f + (index % 3) * 0.6f
                rotate(45f, center) { drawRect(Kk.Bone, Offset(center.x - half, center.y - half), Size(half * 2f, half * 2f)) }
            }
            PickupType.KEY -> {
                drawLine(roles.you.copy(alpha = 0.25f), previous, center, 2f)
                drawPickupPlate(center, roles.you)
                drawKkIcon(KkIcon.SYSTEM_KEY, center, 15f, roles.you)
            }
            PickupType.REPAIR -> {
                drawLine(Kk.Bone.copy(alpha = 0.22f), previous, center, 2f)
                drawPickupPlate(center, Kk.Bone)
                drawKkIcon(KkIcon.SYSTEM_INTEGRITY, center, 14f, Kk.Bone)
                drawRect(Kk.Bone, Offset(center.x - 3f, center.y - 0.75f), Size(6f, 1.5f))
                drawRect(Kk.Bone, Offset(center.x - 0.75f, center.y - 3f), Size(1.5f, 6f))
            }
            PickupType.RELIC -> {
                drawLine(Kk.RLegend.copy(alpha = 0.3f), previous, center, 2.5f)
                drawUnresolvedRelicIcon(center, 11f, engine.elapsed)
            }
        }
    }
}

/** A small ink-2 sheared plate with an outline, the chip look for world pickups. */
private fun DrawScope.drawPickupPlate(center: Offset, outline: androidx.compose.ui.graphics.Color) {
    val plate = WorldPaths.shearedPlate(28f)
    translate(center.x, center.y) {
        drawPath(plate, Kk.Ink2)
        drawPath(plate, outline.copy(alpha = 0.7f), style = kkStroke(1f))
    }
}

/**
 * Particles: the first shards of a burst fly as tumbling sheared slivers (never pointed); the rest
 * are short sparks along their velocity.
 */
internal fun DrawScope.drawParticles(
    engine: GameplayRenderModel,
    visualFx: VisualFxProjection,
    shakeX: Float,
    shakeY: Float,
    roles: KkRolePalette,
) {
    for (index in visualFx.particles.indices) {
        val particle = visualFx.particles[index]
        val center = world(engine, particle.x, particle.y, shakeX, shakeY)
        if (!isOnScreen(center, 24f)) continue
        val alpha = clamp(particle.life / particle.maxLife, 0f, 1f)
        val color = fxColor(particle.colorIndex, roles)
        val speed = sqrt(particle.vx * particle.vx + particle.vy * particle.vy)
        val heading = if (speed > 0.001f) kotlin.math.atan2(particle.vy, particle.vx) else 0f
        if (particle.size >= InteractionFxLimits.SHARD_MIN_SIZE) {
            val length = particle.size * 2.6f
            withTransform({
                translate(center.x, center.y)
                rotate(shardTumble(heading, index, alpha), Offset.Zero)
                scale(length, length, Offset.Zero)
            }) {
                drawPath(WorldUnitShapes.shard, color, alpha = min(1f, alpha * 1.4f))
            }
        } else if (speed > 1f) {
            val stretch = min(0.05f, 14f / speed)
            val tailX = center.x - particle.vx * stretch
            val tailY = center.y - particle.vy * stretch
            drawLine(color.copy(alpha = alpha * 0.8f), Offset(tailX, tailY), center, max(1f, particle.size * 0.7f))
        } else {
            drawCircle(color.copy(alpha = alpha), max(0.8f, particle.size * 0.6f), center)
        }
    }
}

/** Shards tumble: a per-shard phase plus a turn over their life, never aligned to their travel. */
internal fun shardTumble(heading: Float, index: Int, life: Float): Float =
    heading * RAD_TO_DEG + 55f + (index % 4) * 17f + (1f - life) * 220f

/**
 * The weapon totem: stacked sheared ink plates with you-color edge lines around a you-color key
 * block (`Totem.dc.html`) inside a slowly turning dashed ring. Off-screen, [drawTotemEdgeMarker]
 * marks it at the screen edge with the distance.
 */
internal fun DrawScope.drawTotem(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, textMeasurer: TextMeasurer) {
    val totem = engine.totem ?: return
    val roles = textMeasurer.roles
    val location = world(engine, totem.x, totem.y, shakeX, shakeY)
    if (!isOnScreen(location, 60f)) return
    val pulse = (sin(totem.pulse) + 1f) * 0.5f
    rotate(engine.elapsed * spin(24f), location) {
        drawCircle(roles.you.copy(alpha = 0.45f), 45f, location, style = WorldStrokes.dashedThin)
    }
    drawCircle(roles.you.copy(alpha = 0.05f + pulse * 0.04f), 45f, location)
    drawTotemPlates(location, 1f, roles.you, keyBlock = true, keyIcon = true)
}

internal fun DrawScope.drawTotemEdgeMarker(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, textMeasurer: TextMeasurer) {
    val totem = engine.totem ?: return
    val location = world(engine, totem.x, totem.y, shakeX, shakeY)
    if (isOnScreen(location, 60f)) return
    val dx = totem.x - engine.coreX
    val dy = totem.y - engine.coreY
    drawEdgeMarker(location, sqrt(dx * dx + dy * dy), EdgeMarkerIcon.TOTEM, textMeasurer)
}

/**
 * Five stacked plates (60/80/110/80/60 on the board) around a key block, scaled to the world:
 * [scale] 1 is 30 units wide. [keyBlock] fills the middle plate in [accent] ([keyIcon] adds the
 * key); otherwise the middle plate is outlined.
 */
internal fun DrawScope.drawTotemPlates(
    center: Offset,
    scale: Float,
    accent: androidx.compose.ui.graphics.Color,
    keyBlock: Boolean,
    keyIcon: Boolean,
) {
    val width = 30f * scale
    val gap = 2.2f * scale
    val heights = TotemPlateHeights
    var total = 0f
    for (height in heights) total += height * scale
    total += gap * (heights.size - 1)
    var top = center.y - total * 0.5f
    for (index in heights.indices) {
        val height = heights[index] * scale
        val middle = index == heights.size / 2
        val plateTop = top
        withKkShear(plateTop + height * 0.5f) {
            val face = if (middle && keyBlock) accent else Kk.Ink3
            drawRect(face, Offset(center.x - width * 0.5f, plateTop), Size(width, height))
            if (!middle) {
                val edgeY = if (index < heights.size / 2) plateTop + height - 1f * scale else plateTop
                drawRect(accent, Offset(center.x - width * 0.5f, edgeY), Size(width, max(1f, scale)))
            } else if (!keyBlock) {
                drawRect(accent, Offset(center.x - width * 0.5f, plateTop), Size(width, height), style = kkStroke(1.5f))
            }
        }
        if (middle && keyBlock && keyIcon) drawKkIcon(KkIcon.SYSTEM_KEY, Offset(center.x, plateTop + height * 0.5f), 16f * scale, Kk.Ink)
        top += height + gap
    }
}

private val TotemPlateHeights = floatArrayOf(10f, 13f, 18f, 13f, 10f)

/** The stacked-plates mark used by edge markers (the HUD-Elite totem marker). */
internal fun DrawScope.drawTotemIcon(center: Offset, size: Float, color: androidx.compose.ui.graphics.Color) {
    val unit = size / 24f
    drawRect(color, Offset(center.x - 3f * unit, center.y - 9f * unit), Size(6f * unit, 4f * unit), style = kkStroke(max(1f, 1.6f * unit)))
    drawRect(color, Offset(center.x - 4f * unit, center.y - 3f * unit), Size(8f * unit, 5f * unit), style = kkStroke(max(1f, 1.6f * unit)))
    drawRect(color, Offset(center.x - 3f * unit, center.y + 4f * unit), Size(6f * unit, 5f * unit), style = kkStroke(max(1f, 1.6f * unit)))
}
