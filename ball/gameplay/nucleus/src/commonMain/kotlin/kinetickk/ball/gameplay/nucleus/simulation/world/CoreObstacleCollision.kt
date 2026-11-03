// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.world

import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.gameplay.nucleus.model.Obstacle
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kotlin.math.max
import kotlin.math.min
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.build.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.structures.*

// ---------------------------------------------------------------------------------------------
// The Core.

/**
 * Runs right after the Core moves: keeps the window current, resolves the swept Core path against
 * obstacles (bounces, damage, ricochets, explosions), then runs the obstacle kinds' pass (proximity, Near Miss).
 */
internal fun MutableGameState.updateObstacles() {
    refreshObstacleWindow()
    val collided = resolveCoreObstacleCollisions()
    if (phase != GamePhase.RUNNING) return
    updateObstacleKinds(collided)
}

private const val MAX_CORE_BOUNCES: Int = 3
private const val CONTACT_SEPARATION: Float = 0.05f

/**
 * Returns true when the Core touched an obstacle this step. At most [MAX_CORE_BOUNCES] contacts resolve per step;
 * the last one ends the step's motion at its rest point.
 */
private fun MutableGameState.resolveCoreObstacleCollisions(): Boolean {
    if (obstacleField.size == 0 && structureField.structures.isEmpty()) return false
    var startX = previousCoreX
    var startY = previousCoreY
    var endX = coreX
    var endY = coreY
    var collided = false
    val delta = MutableGameState.FIXED_STEP
    for (bounce in 0 until MAX_CORE_BOUNCES) {
        var hitIndex = -1
        var hitT = 2f
        val reach = length(endX - startX, endY - startY) * 0.5f + coreRadius
        val segmentStartX = startX
        val segmentStartY = startY
        val segmentEndX = endX
        val segmentEndY = endY
        forEachObstacleNear((startX + endX) * 0.5f, (startY + endY) * 0.5f, reach) { index, obstacle ->
            if (!coreMayHit(obstacle, segmentStartX, segmentStartY)) return@forEachObstacleNear
            val t = sweepEntry(
                segmentStartX, segmentStartY, segmentEndX, segmentEndY,
                obstacle.x, obstacle.y, obstacle.radius + coreRadius,
            )
            if (t >= 0f && t < hitT) {
                hitT = t
                hitIndex = index
            }
        }
        // Structure walls sweep in the same pass; a circle wins a tie.
        var wallIndex = -1
        val wallHit = earliestWallEntry(segmentStartX, segmentStartY, segmentEndX, segmentEndY, coreRadius, hitT)
        if (wallHit >= 0L) {
            wallIndex = (wallHit ushr 32).toInt()
            hitT = Float.fromBits(wallHit.toInt())
            hitIndex = -1
        }
        if (hitIndex < 0 && wallIndex < 0) break
        collided = true
        val contactX = startX + (endX - startX) * hitT
        val contactY = startY + (endY - startY) * hitT
        if (wallIndex >= 0) {
            // The wall response leaves the Core at its rest point off the wall; the sweep goes on from there.
            coreX = contactX
            coreY = contactY
            respondToWall(structureField.walls[wallIndex], contactX, contactY)
            startX = coreX
            startY = coreY
        } else {
            val obstacle = obstacleField.obstacles[hitIndex]
            var normalX = contactX - obstacle.x
            var normalY = contactY - obstacle.y
            val normalLength = length(normalX, normalY)
            if (normalLength < 0.001f) {
                val currentSpeed = speed
                normalX = if (currentSpeed > 0.001f) -velocityX / currentSpeed else 1f
                normalY = if (currentSpeed > 0.001f) -velocityY / currentSpeed else 0f
            } else {
                normalX /= normalLength
                normalY /= normalLength
            }
            val surface = obstacle.radius + coreRadius + CONTACT_SEPARATION
            val restX = obstacle.x + normalX * surface
            val restY = obstacle.y + normalY * surface
            val passedThrough = respondToObstacle(hitIndex, obstacle, normalX, normalY, restX, restY)
            if (passedThrough) {
                // The rock shattered: the Core keeps its line from the contact point.
                startX = contactX
                startY = contactY
            } else {
                startX = restX
                startY = restY
            }
        }
        if (bounce == MAX_CORE_BOUNCES - 1) {
            // The last allowed contact ends the step's motion at its rest point: the remainder is never moved
            // un-swept into whatever lies behind it.
            endX = startX
            endY = startY
        } else {
            val remaining = (1f - hitT) * delta
            endX = startX + velocityX * remaining
            endY = startY + velocityY * remaining
        }
        if (phase != GamePhase.RUNNING) break
    }
    coreX = endX
    coreY = endY
    if (collided) {
        pushCoreOutOfObstacles()
        pushCoreOutOfWalls()
    }
    return collided
}

/**
 * Last guard after the bounces: the Core never rests inside a solid obstacle. It is pushed out of every steady solid
 * obstacle it overlaps (a phase shard that materialised around it lets it drift out instead). Inward speed below the
 * damage-start impact speed is removed silently; the first contact at that speed or faster keeps it and, once the walk
 * is done (the field does not change during it), responds like a swept contact: it bounces, hurts, kills or ricochets.
 */
internal fun MutableGameState.pushCoreOutOfObstacles() {
    var hitIndex = -1
    var hitObstacle: Obstacle? = null
    var hitNormalX = 0f
    var hitNormalY = 0f
    forEachObstacleNear(coreX, coreY, coreRadius) { index, obstacle ->
        if (!obstacle.isSteadySurface()) return@forEachObstacleNear
        val combined = obstacle.radius + coreRadius
        val dx = coreX - obstacle.x
        val dy = coreY - obstacle.y
        val distance = length(dx, dy)
        if (distance < combined) {
            val nx = if (distance > 0.001f) dx / distance else 1f
            val ny = if (distance > 0.001f) dy / distance else 0f
            coreX = obstacle.x + nx * (combined + CONTACT_SEPARATION)
            coreY = obstacle.y + ny * (combined + CONTACT_SEPARATION)
            val inward = -(velocityX * nx + velocityY * ny)
            if (inward > 0f) {
                val sliding = length(velocityX + inward * nx, velocityY + inward * ny)
                if (hitObstacle == null && obstacleImpactSpeed(inward, sliding) >= obstacleDamageStartSpeed()) {
                    hitIndex = index
                    hitObstacle = obstacle
                    hitNormalX = nx
                    hitNormalY = ny
                } else {
                    velocityX += inward * nx
                    velocityY += inward * ny
                }
            }
        }
    }
    val hit = hitObstacle ?: return
    respondToObstacle(hitIndex, hit, hitNormalX, hitNormalY, coreX, coreY)
}

/**
 * Velocity response, damage and triggers of the Core meeting [obstacle] along the outward normal
 * ([nx], [ny]) at ([contactX], [contactY]), dispatched by kind: bumpers throw, an unstable asteroid hit at
 * the damage-start impact speed bursts and a crystal hit that hard shatters, every other solid obstacle is a
 * solid surface (a crystal hit more gently only bounces, it never hurts); a kind with no solid body never
 * responds. Returns true when the obstacle shattered and the Core passes on without bouncing.
 */
private fun MutableGameState.respondToObstacle(
    index: Int,
    obstacle: Obstacle,
    nx: Float,
    ny: Float,
    contactX: Float,
    contactY: Float,
): Boolean {
    val normalSpeed = -(velocityX * nx + velocityY * ny)
    if (normalSpeed <= 0f) return false
    when (obstacle.kind) {
        ObstacleKind.BUMPER -> {
            bounceOffBumper(index, obstacle, nx, ny, normalSpeed, contactX, contactY)
            return false
        }
        ObstacleKind.UNSTABLE, ObstacleKind.CRYSTAL -> {
            val impact = obstacleImpactSpeed(normalSpeed, length(velocityX + normalSpeed * nx, velocityY + normalSpeed * ny))
            if (impact >= obstacleDamageStartSpeed()) {
                if (obstacle.kind == ObstacleKind.UNSTABLE) explodeUnstable(index, obstacle, impact)
                else shatterCrystal(index, obstacle, impact, byCore = true)
                return true
            }
        }
        ObstacleKind.ACCEL_GATE, ObstacleKind.DERELICT_MINE, ObstacleKind.THERMAL_VENT -> return false
        ObstacleKind.ASTEROID, ObstacleKind.GRAVITY_WELL, ObstacleKind.PHASE_SHARD -> Unit
    }
    resolveSolidImpact(obstacle.id, nx, ny, contactX, contactY, obstacle = obstacle.kind)
    return false
}

/**
 * Ricochet payoffs: Ricochet Rig's burst, Gravity Surfer's dash phase and RICOCHET clauses, which carry the [obstacle]
 * kind bounced off (none for a structure wall).
 */
internal fun MutableGameState.onRicochet(x: Float, y: Float, normalSpeed: Float, obstacle: ObstacleKind? = null) {
    val impact = impactPower(normalSpeed)
    matrix.ricochetRig?.let { rig -> if (rig.burstRadius > 0f) areaDamage(x, y, rig.burstRadius, impact) }
    if (matrix.ricochetPhaseSeconds > 0f) dashPhaseTime = max(dashPhaseTime, matrix.ricochetPhaseSeconds)
    fireClauses(EffectTrigger.RICOCHET, null, impact, obstacle = obstacle)
}

/**
 * Elastic throw: the Core leaves at 1.05 × its speed, held to the bumper band and below the lethal
 * collision speed (on a small viewport that speed can lie inside the band); never hurts. Like an asteroid
 * bounce it is a ricochet only at the ricochet speed or faster, at most once per cooldown per bumper.
 */
private fun MutableGameState.bounceOffBumper(
    index: Int,
    obstacle: Obstacle,
    nx: Float,
    ny: Float,
    normalSpeed: Float,
    contactX: Float,
    contactY: Float,
) {
    val tuning = content.obstacles
    val incoming = speed
    velocityX += 2f * normalSpeed * nx
    velocityY += 2f * normalSpeed * ny
    val current = speed
    val directionX = if (current > 0.001f) velocityX / current else nx
    val directionY = if (current > 0.001f) velocityY / current else ny
    val band = max(tuning.bumperMinimumExitSpeed, min(tuning.bumperGain * incoming, tuning.bumperMaximumExitSpeed))
    val exit = min(band, obstacleLethalSpeed() * tuning.bumperLethalShare)
    velocityX = directionX * exit
    velocityY = directionY * exit
    shockwave(obstacle.x, obstacle.y, 0.25f, obstacle.radius * 2.4f, 2)
    emitSound(GameplayAudioCue.IMPACT)
    screenShake = max(screenShake, 4f)
    if (normalSpeed < obstacleRicochetSpeed() || obstacle.ricochetReadyAt > elapsed) return
    obstacleField = obstacleField.replaced(index, obstacle.copy(ricochetReadyAt = elapsed + tuning.bumperRicochetCooldownSeconds))
    pendingRicochets++
    onRicochet(contactX - nx * coreRadius, contactY - ny * coreRadius, normalSpeed, ObstacleKind.BUMPER)
}

/**
 * An unstable asteroid hit at the damage-start impact speed bursts for good: it hurts the Core a little and enemies
 * around it, as hard as the [impact] speed, then fires SHATTER with its kind from its centre.
 */
private fun MutableGameState.explodeUnstable(index: Int, obstacle: Obstacle, impact: Float) {
    val tuning = content.obstacles
    obstacleField = obstacleField.removed(index)
    recordDestroyedObstacle(obstacle.id)
    areaDamage(obstacle.x, obstacle.y, tuning.unstableBlastRadius, impactPower(impact))
    burst(obstacle.x, obstacle.y, 24, 4)
    screenShake = max(screenShake, 12f)
    emitSound(GameplayAudioCue.IMPACT)
    if (distanceSquared(coreX, coreY, obstacle.x, obstacle.y) <= square(tuning.unstableBlastRadius + coreRadius + obstacle.radius)) {
        takeDamage(tuning.unstableBlastDamage * build.obstacleDamageScale, kind = IncomingDamage.OBSTACLE, obstacle = ObstacleKind.UNSTABLE)
    }
    if (phase != GamePhase.RUNNING) return
    fireClauses(EffectTrigger.SHATTER, null, impactPower(impact), obstacle = ObstacleKind.UNSTABLE, originX = obstacle.x, originY = obstacle.y)
}
