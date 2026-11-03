// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world

import kinetickk.foundation.design.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.ProjectileKind
import kinetickk.ball.gameplay.interaction.fx.InteractionFxLimits
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.look.*

internal fun DrawScope.drawProjectiles(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    for (index in engine.projectiles.indices) {
        val projectile = engine.projectiles[index]
        // Drawn between its last two simulated positions, with a one-step streak behind it.
        val x = engine.interpolated(projectile.previousX, projectile.x)
        val y = engine.interpolated(projectile.previousY, projectile.y)
        val center = world(engine, x, y, shakeX, shakeY)
        if (!isOnScreen(center, projectile.radius + 28f)) continue
        val previous = world(engine, x - (projectile.x - projectile.previousX), y - (projectile.y - projectile.previousY), shakeX, shakeY)
        if (projectile.hostile && projectile.kind == ProjectileKind.MINE) {
            drawMine(center, projectile.radius, projectile.life, engine.renderElapsed, roles)
        } else if (projectile.hostile) {
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
            // Apex shots (colour index 15) fly gold; every other shot of the player in its colour.
            val accent = if (projectile.colorIndex == APEX_GOLD_COLOR) roles.gold.base else roles.you
            drawLine(accent.copy(alpha = 0.3f), previous, center, max(1.5f, projectile.radius * 1.2f))
            drawCircle(accent.copy(alpha = 0.18f), projectile.radius + 4f, center)
            drawCircle(if (projectile.colorIndex == APEX_GOLD_COLOR) roles.gold.light else Kk.Bone, max(2f, projectile.radius * 0.8f), center)
        }
    }
}

/** A mine: a threat diamond with a blinking core; once armed a faint ring shows its trigger reach. */
private fun DrawScope.drawMine(center: Offset, radius: Float, life: Float, elapsed: Float, roles: KkRolePalette) {
    val threat = roles.threat
    val armed = life <= MINE_ARMED_LIFE
    rotate(45f, center) {
        drawRect(Kk.Ink1, Offset(center.x - radius, center.y - radius), Size(radius * 2f, radius * 2f))
        drawRect(threat, Offset(center.x - radius, center.y - radius), Size(radius * 2f, radius * 2f), style = kkStroke(2f))
    }
    val blink = if (armed) (if (cos(elapsed * 9f) > 0f) 1f else 0.3f) else 0.25f
    drawCircle(threat.copy(alpha = blink), radius * 0.4f, center)
    if (armed) drawCircle(threat.copy(alpha = 0.22f), MINE_TRIGGER_REACH, center, style = WorldStrokes.dashedHair)
}

internal fun DrawScope.drawPickups(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    for (index in engine.pickups.indices) {
        val pickup = engine.pickups[index]
        val x = engine.interpolated(pickup.previousX, pickup.x)
        val y = engine.interpolated(pickup.previousY, pickup.y)
        val center = world(engine, x, y, shakeX, shakeY)
        if (!isOnScreen(center, 28f)) continue
        val previous = world(engine, x - (pickup.x - pickup.previousX), y - (pickup.y - pickup.previousY), shakeX, shakeY)
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
                drawUnresolvedRelicIcon(center, 11f, engine.renderElapsed)
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
        val alpha = (particle.life / particle.maxLife).coerceIn(0f, 1f)
        val color = fxColor(particle.colorIndex, roles)
        val speed = sqrt(particle.vx * particle.vx + particle.vy * particle.vy)
        if (particle.size >= InteractionFxLimits.SHARD_MIN_SIZE) {
            val heading = if (speed > 0.001f) kotlin.math.atan2(particle.vy, particle.vx) else 0f
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
