// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.weapons

import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.structures.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*

// ---------------------------------------------------------------------------------------------
// Movement and fuses.

/*
 * A disc flies out ([WeaponEntity.timer] counts the distance) and returns to the Core (phase 1, where the timer
 * holds back the next spark while the disc glances along a rock). Asteroids turn a disc back, or end a returning one; the third form
 * ricochets an outgoing disc on and makes a returning one glance along the rock toward the Core, so it comes
 * home around it. Either way the disc keeps moving on the step it meets the rock.
 */
internal fun MutableGameState.advanceDisc(disc: WeaponEntity, delta: Float) {
    disc.life -= delta
    if (disc.phase == 1) {
        val dx = coreX - disc.x
        val dy = coreY - disc.y
        val distance = length(dx, dy)
        if (distance <= GYRE_CATCH_RADIUS) {
            disc.life = 0f
            return
        }
        disc.vx = dx / distance * GYRE_RETURN_SPEED
        disc.vy = dy / distance * GYRE_RETURN_SPEED
    }
    disc.angle += GYRE_SPIN * delta
    if (disc.phase == 1) disc.timer = max(0f, disc.timer - delta)
    val blocker = obstacleOnPath(disc.x, disc.y, disc.x + disc.vx * delta, disc.y + disc.vy * delta, disc.radius)
    val wallIndex = if (blocker == null) {
        wallOnPath(disc.x, disc.y, disc.x + disc.vx * delta, disc.y + disc.vy * delta, disc.radius)
    } else -1
    if (blocker != null || wallIndex >= 0) {
        if (!hasMasteryForm(3)) {
            if (disc.phase == 0) beginDiscReturn(disc) else disc.life = 0f
            return
        }
        if (blocker != null) {
            val nx = disc.x - blocker.x
            val ny = disc.y - blocker.y
            val distance = max(1f, length(nx, ny))
            deflectDisc(disc, nx / distance, ny / distance)
            // The new heading runs along or away from that rock; a second rock on it holds the disc for this step.
            val next = obstacleOnPath(disc.x, disc.y, disc.x + disc.vx * delta, disc.y + disc.vy * delta, disc.radius)
            if (next != null && next !== blocker) return
        } else {
            // A wall deflects like a rock, off the normal from its nearest point.
            val wall = structureField.walls[wallIndex]
            val along = segmentNearest(disc.x, disc.y, wall.ax, wall.ay, wall.bx, wall.by)
            val nx = disc.x - (wall.ax + (wall.bx - wall.ax) * along)
            val ny = disc.y - (wall.ay + (wall.by - wall.ay) * along)
            val distance = max(1f, length(nx, ny))
            deflectDisc(disc, nx / distance, ny / distance)
            val nextX = disc.x + disc.vx * delta
            val nextY = disc.y + disc.vy * delta
            val nextWall = wallOnPath(disc.x, disc.y, nextX, nextY, disc.radius)
            if ((nextWall >= 0 && nextWall != wallIndex) || obstacleOnPath(disc.x, disc.y, nextX, nextY, disc.radius) != null) return
        }
    }
    val stepX = disc.vx * delta
    val stepY = disc.vy * delta
    disc.x += stepX
    disc.y += stepY
    if (disc.phase == 0) {
        disc.timer += length(stepX, stepY)
        if (disc.timer >= disc.extent) beginDiscReturn(disc)
    }
}

private fun beginDiscReturn(disc: WeaponEntity) {
    disc.phase = 1
    disc.timer = 0f
    disc.clearHits()
}

/**
 * The third form at a rock or wall with outward normal ([ux], [uy]): an outgoing disc reflects off it; a returning one
 * glances along it toward the Core.
 */
private fun MutableGameState.deflectDisc(disc: WeaponEntity, ux: Float, uy: Float) {
    val along = disc.vx * ux + disc.vy * uy
    if (along >= 0f) return
    if (disc.phase == 0) {
        disc.vx -= 2f * along * ux
        disc.vy -= 2f * along * uy
        burst(disc.x, disc.y, 4, 0)
        return
    }
    // The heading's part into the rock goes; what is left runs along its surface on the Core's side.
    disc.vx -= along * ux
    disc.vy -= along * uy
    val tangent = length(disc.vx, disc.vy)
    if (tangent < 1f) {
        // Straight behind the rock: either way round is as short.
        disc.vx = -uy * GYRE_RETURN_SPEED
        disc.vy = ux * GYRE_RETURN_SPEED
    } else {
        disc.vx *= GYRE_RETURN_SPEED / tangent
        disc.vy *= GYRE_RETURN_SPEED / tangent
    }
    // One spark as the glance begins, not one per step along the rock.
    if (disc.timer <= 0f) burst(disc.x, disc.y, 4, 0)
    disc.timer = GYRE_GLANCE_SPARK_GAP
}

internal fun MutableGameState.advanceSentry(sentry: WeaponEntity, delta: Float) {
    sentry.life -= delta
    if (sentry.life <= 0f) {
        // Third form: an expiring sentry explodes (a folded one does not).
        if (sentry.phase != SENTRY_FOLDED && hasMasteryForm(3)) {
            for (index in enemies.indices) {
                val enemy = enemies[index]
                if (enemy.alive && distanceSquared(sentry.x, sentry.y, enemy.x, enemy.y) <= square(SENTRY_BLAST_RADIUS + enemy.radius)) {
                    dealWeaponDamage(enemy, sentry.damage * SENTRY_BLAST_DAMAGE / SENTRY_DAMAGE, procKey = WeaponRuntime.PROC_NEVER)
                }
            }
            shockwave(sentry.x, sentry.y, 0.3f, SENTRY_BLAST_RADIUS, 2)
        }
        sentry.phase = SENTRY_FOLDED
        return
    }
    sentry.timer -= delta
    if (sentry.timer > 0f) return
    val target = nearestEnemy(sentry.x, sentry.y, SENTRY_RANGE) ?: return
    sentry.timer = cooldown(SENTRY_FIRE_INTERVAL)
    val angle = atan2(target.y - sentry.y, target.x - sentry.x)
    sentry.angle = angle
    firePlayerProjectile(sentry.x, sentry.y, angle, SENTRY_SHOT_SPEED, sentry.damage, SENTRY_PIERCE + if (hasMasteryForm(2)) 1 else 0, 4f, 1,
        sentry.procKey, life = SENTRY_RANGE / SENTRY_SHOT_SPEED + 0.2f)
}

internal fun MutableGameState.advanceLens(lens: WeaponEntity, delta: Float) {
    lens.life -= delta
    val nextX = lens.x + lens.vx * delta
    val nextY = lens.y + lens.vy * delta
    if (obstacleOnPath(lens.x, lens.y, nextX, nextY, lens.radius) != null || wallOnPath(lens.x, lens.y, nextX, nextY, lens.radius) >= 0) {
        collapseLens(lens)
        return
    }
    lens.x = nextX
    lens.y = nextY
    lens.angle += 4f * delta
    if (lens.life <= 0f) collapseLens(lens)
}

/** A lens collapses into a well where it stands: the collapse hit, then the well's pull and burn. */
internal fun MutableGameState.collapseLens(lens: WeaponEntity) {
    if (lens.phase != 0) return
    lens.phase = 1
    lens.life = 0f
    val radius = lensWellRadius()
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.alive && distanceSquared(lens.x, lens.y, enemy.x, enemy.y) <= square(radius + enemy.radius)) {
            val landed = hitFrom(enemy, lens.x, lens.y) {
                dealWeaponDamage(enemy, LENS_COLLAPSE_DAMAGE * lens.damage, canCrit = true, procKey = lens.procKey)
            }.amount > 0f
            if (landed) weaponElementTick(enemy, StatusElement.GRAVITIC, LENS_MASS_INTERVAL)
        }
    }
    addWeaponEntity(WeaponEntity(WeaponEntityKind.WELL, lens.x, lens.y, radius, LENS_WELL_SECONDS, LENS_WELL_SECONDS,
        damage = LENS_WELL_DPS * lens.damage, extent = if (hasMasteryForm(3)) LENS_IMPLOSION_DAMAGE * lens.damage else 0f,
        procKey = lens.procKey))
    shockwave(lens.x, lens.y, 0.3f, radius, 4)
    emitSound(GameplayAudioCue.IMPACT)
}

internal fun MutableGameState.advanceWell(well: WeaponEntity, delta: Float) {
    well.life -= delta
    well.angle += 2.4f * delta
    if (well.life > 0f || well.extent <= 0f) return
    // Third form: the well implodes; a Dipole's REPEL makes the implosion a throw.
    val repel = signatureActive() && polarity == Polarity.REPEL
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || distanceSquared(well.x, well.y, enemy.x, enemy.y) > square(well.radius + enemy.radius)) continue
        dealWeaponDamage(enemy, well.extent, canCrit = true, procKey = well.procKey)
        if (repel) pushEnemy(enemy, well.x, well.y, LENS_THROW)
    }
    shockwave(well.x, well.y, 0.35f, well.radius, 4)
}

internal fun MutableGameState.advanceRift(rift: WeaponEntity, delta: Float) {
    rift.life -= delta
    if (rift.phase == RIFT_SPENT) return
    rift.timer -= delta
    // Third form: armed rifts draw enemies in before they burst.
    if (rift.phase == RIFT_ARMED && weapon == WeaponId.HOLLOW_RIFT && hasMasteryForm(3)) {
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (!enemy.alive) continue
            val distanceSquared = pointToSegmentDistanceSquared(enemy.x, enemy.y, rift.x, rift.y, rift.endX, rift.endY)
            if (distanceSquared > square(HOLLOW_PULL_RADIUS) || distanceSquared < 1f) continue
            val t = segmentNearest(enemy.x, enemy.y, rift.x, rift.y, rift.endX, rift.endY)
            val dx = rift.x + (rift.endX - rift.x) * t - enemy.x
            val dy = rift.y + (rift.endY - rift.y) * t - enemy.y
            val distance = max(1f, length(dx, dy))
            val pull = HOLLOW_PULL * knockbackTaken(enemy) * delta
            enemy.vx += dx / distance * pull
            enemy.vy += dy / distance * pull
        }
    }
    if (rift.timer > 0f || rift.phase == RIFT_GROWING) return
    val spear = rift.phase == RIFT_SPEAR_SCAR
    val share = if (rift.phase == RIFT_DETONATED) HOLLOW_SECOND_SHARE else 1f
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || !segmentCircleIntersects(rift.x, rift.y, rift.endX, rift.endY, enemy.x, enemy.y, enemy.radius + rift.radius)) continue
        val landed = dealWeaponDamage(enemy, rift.damage * share, canCrit = true, procKey = rift.procKey).amount > 0f
        if (landed) applyWeaponStatus(enemy, StatusElement.VOID, if (spear) 1 else 2)
    }
    addWeaponArc(rift.x, rift.y, rift.endX, rift.endY, 0.3f)
    // Second form: a Hollow Rift bursts twice.
    if (!spear && rift.phase == RIFT_ARMED && weapon == WeaponId.HOLLOW_RIFT && hasMasteryForm(2)) {
        rift.phase = RIFT_DETONATED
        rift.timer = HOLLOW_SECOND_DELAY
        rift.life = max(rift.life, HOLLOW_SECOND_DELAY + 0.2f)
    } else {
        rift.phase = RIFT_SPENT
        rift.life = min(rift.life, 0.25f)
    }
}

internal fun MutableGameState.advanceShard(shard: WeaponEntity, delta: Float) {
    shard.life -= delta
    var target = enemyById(shard.targetId)
    if (target != null && target.alive && isShifting(target)) {
        // A phased target takes nothing: the shard turns to the nearest enemy it can still hit (one it has not
        // struck yet), or flies straight on.
        target = nearestUnstruckEnemy(shard, shard.x, shard.y, AEGIS_RANGE)
        shard.targetId = target?.id ?: -1
    }
    if (target != null && target.alive) steer(shard, target.x, target.y, AEGIS_SPEED, AEGIS_TURN_RATE, delta)
    val nextX = shard.x + shard.vx * delta
    val nextY = shard.y + shard.vy * delta
    if (obstacleOnPath(shard.x, shard.y, nextX, nextY, shard.radius) != null ||
        wallOnPath(shard.x, shard.y, nextX, nextY, shard.radius) >= 0
    ) {
        shard.life = 0f
        burst(shard.x, shard.y, 3, 0)
        return
    }
    shard.x = nextX
    shard.y = nextY
}

internal fun MutableGameState.advanceMissile(missile: WeaponEntity, delta: Float) {
    missile.life -= delta
    var target = enemyById(missile.targetId)?.takeIf { it.alive && !isShifting(it) }
    if (target == null) {
        target = nearestEnemy(missile.x, missile.y, HUNTER_SEEK_RANGE)
        missile.targetId = target?.id ?: -1
    }
    val currentSpeed = length(missile.vx, missile.vy)
    val nextSpeed = min(HUNTER_TOP_SPEED, currentSpeed + HUNTER_ACCELERATION * delta)
    if (target != null) {
        steer(missile, target.x, target.y, nextSpeed, HUNTER_TURN_RATE, delta)
    } else if (currentSpeed > 0f) {
        missile.vx = missile.vx / currentSpeed * nextSpeed
        missile.vy = missile.vy / currentSpeed * nextSpeed
    }
    missile.angle = atan2(missile.vy, missile.vx)
    val nextX = missile.x + missile.vx * delta
    val nextY = missile.y + missile.vy * delta
    if (obstacleOnPath(missile.x, missile.y, nextX, nextY, missile.radius) != null ||
        wallOnPath(missile.x, missile.y, nextX, nextY, missile.radius) >= 0
    ) {
        missileBurst(missile, null)
        missile.life = 0f
        return
    }
    missile.x = nextX
    missile.y = nextY
}

/** Turns an entity's velocity toward (x, y) by at most [turnRate] radians per second, at [speed]. */
private fun steer(entity: WeaponEntity, x: Float, y: Float, speed: Float, turnRate: Float, delta: Float) {
    val desired = atan2(y - entity.y, x - entity.x)
    val current = if (entity.vx == 0f && entity.vy == 0f) desired else atan2(entity.vy, entity.vx)
    val difference = atan2(sin(desired - current), cos(desired - current))
    val heading = current + difference.coerceIn(-turnRate * delta, turnRate * delta)
    entity.vx = cos(heading) * speed
    entity.vy = sin(heading) * speed
}

/**
 * The first shot-stopping obstacle a circle of [radius] runs into from (x0, y0) to (x1, y1) (one it starts
 * overlapping only when it moves inward, see [obstacleStepEntry]); bumpers and kinds with no solid body let it pass.
 */
private fun MutableGameState.obstacleOnPath(x0: Float, y0: Float, x1: Float, y1: Float, radius: Float): Obstacle? {
    if (obstacleField.size == 0) return null
    val reach = length(x1 - x0, y1 - y0) * 0.5f + radius
    val kinds = content.obstacles.kinds
    forEachObstacleNear((x0 + x1) * 0.5f, (y0 + y1) * 0.5f, reach) { _, obstacle ->
        if (obstacle.stopsShotsAt(elapsed, kinds) &&
            obstacleStepEntry(x0, y0, x1, y1, obstacle.x, obstacle.y, obstacle.radius, radius) >= 0f
        ) {
            return obstacle
        }
    }
    return null
}
