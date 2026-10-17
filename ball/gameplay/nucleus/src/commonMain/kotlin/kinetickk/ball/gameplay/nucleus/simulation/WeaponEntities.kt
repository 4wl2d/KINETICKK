// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Weapon entities: [advanceWeaponEntities] moves them and runs their fuses in the weapon step;
 * [resolveWeaponEntityHits] resolves their contacts and fields in the collision pass, after enemies
 * moved. Finished entities (life spent) leave the list only after each pass, never inside a hit.
 */

internal fun MutableGameState.advanceWeaponEntities(delta: Float) {
    val entities = weaponRuntime.entities
    if (entities.isEmpty()) return
    val count = entities.size
    for (index in 0 until count) {
        val entity = entities[index]
        if (entity.life <= 0f) continue
        when (entity.kind) {
            WeaponEntityKind.DISC -> advanceDisc(entity, delta)
            WeaponEntityKind.SENTRY -> advanceSentry(entity, delta)
            WeaponEntityKind.LENS -> advanceLens(entity, delta)
            WeaponEntityKind.WELL -> advanceWell(entity, delta)
            WeaponEntityKind.RIFT -> advanceRift(entity, delta)
            WeaponEntityKind.SHARD -> advanceShard(entity, delta)
            WeaponEntityKind.MISSILE -> advanceMissile(entity, delta)
            WeaponEntityKind.RING -> entity.life -= delta
            WeaponEntityKind.TETHER, WeaponEntityKind.BEAM -> Unit
        }
    }
    entities.removeMatchingStable { it.life <= 0f }
}

internal fun MutableGameState.resolveWeaponEntityHits(delta: Float) {
    val entities = weaponRuntime.entities
    if (entities.isEmpty()) return
    val count = entities.size
    for (index in 0 until count) {
        if (phase != GamePhase.RUNNING) break
        val entity = entities[index]
        if (entity.life <= 0f) continue
        when (entity.kind) {
            WeaponEntityKind.DISC -> discHits(entity)
            WeaponEntityKind.LENS -> lensContact(entity)
            WeaponEntityKind.WELL -> wellTick(entity, delta)
            WeaponEntityKind.SHARD -> shardHits(entity)
            WeaponEntityKind.MISSILE -> missileHits(entity)
            WeaponEntityKind.TETHER -> tetherTick(entity, delta)
            WeaponEntityKind.BEAM -> beamTick(entity, delta)
            WeaponEntityKind.SENTRY, WeaponEntityKind.RIFT, WeaponEntityKind.RING -> Unit
        }
    }
    entities.removeMatchingStable { it.life <= 0f }
}

// ---------------------------------------------------------------------------------------------
// Movement and fuses.

private fun MutableGameState.advanceDisc(disc: WeaponEntity, delta: Float) {
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
    val stepX = disc.vx * delta
    val stepY = disc.vy * delta
    val nextX = disc.x + stepX
    val nextY = disc.y + stepY
    disc.angle += GYRE_SPIN * delta
    // Asteroids turn a disc back; the third form ricochets it on.
    val blocker = obstacleOnPath(disc.x, disc.y, nextX, nextY, disc.radius)
    if (blocker != null) {
        if (hasMasteryForm(3)) {
            val nx = disc.x - blocker.x
            val ny = disc.y - blocker.y
            val distance = max(1f, length(nx, ny))
            val ux = nx / distance
            val uy = ny / distance
            val along = disc.vx * ux + disc.vy * uy
            if (along < 0f) {
                disc.vx -= 2f * along * ux
                disc.vy -= 2f * along * uy
            }
            burst(disc.x, disc.y, 4, 0)
        } else if (disc.phase == 0) {
            disc.phase = 1
            disc.clearHits()
        } else {
            disc.life = 0f
        }
        return
    }
    disc.x = nextX
    disc.y = nextY
    if (disc.phase == 0) {
        disc.timer += length(stepX, stepY)
        if (disc.timer >= disc.extent) {
            disc.phase = 1
            disc.clearHits()
        }
    }
}

private fun MutableGameState.advanceSentry(sentry: WeaponEntity, delta: Float) {
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

private fun MutableGameState.advanceLens(lens: WeaponEntity, delta: Float) {
    lens.life -= delta
    val nextX = lens.x + lens.vx * delta
    val nextY = lens.y + lens.vy * delta
    if (obstacleOnPath(lens.x, lens.y, nextX, nextY, lens.radius) != null) {
        collapseLens(lens)
        return
    }
    lens.x = nextX
    lens.y = nextY
    lens.angle += 4f * delta
    if (lens.life <= 0f) collapseLens(lens)
}

/** A lens collapses into a well where it stands: the collapse hit, then the well's pull and burn. */
private fun MutableGameState.collapseLens(lens: WeaponEntity) {
    if (lens.phase != 0) return
    lens.phase = 1
    lens.life = 0f
    val radius = lensWellRadius()
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.alive && distanceSquared(lens.x, lens.y, enemy.x, enemy.y) <= square(radius + enemy.radius)) {
            val landed = dealWeaponDamage(enemy, LENS_COLLAPSE_DAMAGE * lens.damage, canCrit = true, procKey = lens.procKey).amount > 0f
            if (landed) weaponElementTick(enemy, StatusElement.GRAVITIC, LENS_MASS_INTERVAL)
        }
    }
    addWeaponEntity(WeaponEntity(WeaponEntityKind.WELL, lens.x, lens.y, radius, LENS_WELL_SECONDS, LENS_WELL_SECONDS,
        damage = LENS_WELL_DPS * lens.damage, extent = if (hasMasteryForm(3)) LENS_IMPLOSION_DAMAGE * lens.damage else 0f,
        procKey = lens.procKey))
    shockwave(lens.x, lens.y, 0.3f, radius, 4)
    emitSound(GameplayAudioCue.IMPACT)
}

private fun MutableGameState.advanceWell(well: WeaponEntity, delta: Float) {
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

private fun MutableGameState.advanceRift(rift: WeaponEntity, delta: Float) {
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
            val (closestX, closestY) = closestOnSegment(enemy.x, enemy.y, rift.x, rift.y, rift.endX, rift.endY)
            val dx = closestX - enemy.x
            val dy = closestY - enemy.y
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

private fun MutableGameState.advanceShard(shard: WeaponEntity, delta: Float) {
    shard.life -= delta
    val target = enemyById(shard.targetId)
    if (target != null && target.alive) steer(shard, target.x, target.y, AEGIS_SPEED, AEGIS_TURN_RATE, delta)
    val nextX = shard.x + shard.vx * delta
    val nextY = shard.y + shard.vy * delta
    if (obstacleOnPath(shard.x, shard.y, nextX, nextY, shard.radius) != null) {
        shard.life = 0f
        burst(shard.x, shard.y, 3, 0)
        return
    }
    shard.x = nextX
    shard.y = nextY
}

private fun MutableGameState.advanceMissile(missile: WeaponEntity, delta: Float) {
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
    if (obstacleOnPath(missile.x, missile.y, nextX, nextY, missile.radius) != null) {
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

/** The first asteroid or unstable rock a circle of [radius] crosses from (x0, y0) to (x1, y1); bumpers let it pass. */
private fun MutableGameState.obstacleOnPath(x0: Float, y0: Float, x1: Float, y1: Float, radius: Float): Obstacle? {
    if (obstacleField.size == 0) return null
    val reach = length(x1 - x0, y1 - y0) * 0.5f + radius
    forEachObstacleNear((x0 + x1) * 0.5f, (y0 + y1) * 0.5f, reach) { _, obstacle ->
        if (obstacle.kind != ObstacleKind.BUMPER && sweepEntry(x0, y0, x1, y1, obstacle.x, obstacle.y, obstacle.radius + radius) >= 0f) {
            return obstacle
        }
    }
    return null
}

private fun closestOnSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Pair<Float, Float> {
    val sx = bx - ax
    val sy = by - ay
    val lengthSquared = sx * sx + sy * sy
    if (lengthSquared <= 0f) return ax to ay
    val t = (((px - ax) * sx + (py - ay) * sy) / lengthSquared).coerceIn(0f, 1f)
    return (ax + sx * t) to (ay + sy * t)
}

// ---------------------------------------------------------------------------------------------
// Contacts and fields.

private fun MutableGameState.sweptContact(entity: WeaponEntity, enemy: Enemy): Boolean =
    segmentCircleIntersects(
        entity.previousX - enemy.previousX,
        entity.previousY - enemy.previousY,
        entity.x - enemy.x,
        entity.y - enemy.y,
        0f,
        0f,
        entity.radius + enemy.radius,
    )

private fun MutableGameState.discHits(disc: WeaponEntity) {
    // Orb signature: a disc thrown or flying while Orbit is charged hits harder; the third form's return pass too.
    val scale = (if (signatureActive() && orbitCharged) GYRE_ORBIT_BONUS else 1f) *
        (if (disc.phase == 1 && hasMasteryForm(3)) GYRE_RETURN_BONUS else 1f)
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || isShifting(enemy) || disc.hasHit(enemy.id) || !sweptContact(disc, enemy)) continue
        disc.recordHit(enemy.id)
        dealWeaponDamage(enemy, disc.damage * scale, canCrit = true, procKey = disc.procKey)
        burst(enemy.x, enemy.y, 4, 0)
    }
}

private fun MutableGameState.lensContact(lens: WeaponEntity) {
    if (lens.phase != 0) return
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || isShifting(enemy) || !sweptContact(lens, enemy)) continue
        collapseLens(lens)
        return
    }
}

private fun MutableGameState.wellTick(well: WeaponEntity, delta: Float) {
    // A Dipole's ATTRACT doubles the pull.
    val pull = LENS_PULL * (if (signatureActive() && polarity == Polarity.ATTRACT && weapon == WeaponId.EVENT_LENS) 2f else 1f) * delta
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive) continue
        val dx = well.x - enemy.x
        val dy = well.y - enemy.y
        val distanceSquared = dx * dx + dy * dy
        if (distanceSquared > square(well.radius + enemy.radius)) continue
        val distance = max(1f, sqrt(distanceSquared))
        val knockback = knockbackTaken(enemy)
        enemy.vx += dx / distance * pull * knockback
        enemy.vy += dy / distance * pull * knockback
        val landed = dealWeaponDamage(enemy, well.damage * delta, procKey = well.procKey).amount > 0f
    }
}

private fun MutableGameState.shardHits(shard: WeaponEntity) {
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || isShifting(enemy) || shard.hasHit(enemy.id) || !sweptContact(shard, enemy)) continue
        shard.recordHit(enemy.id)
        dealWeaponDamage(enemy, shard.damage, canCrit = true, procKey = shard.procKey)
        burst(enemy.x, enemy.y, 4, 0)
        if (shard.phase > 0) {
            // Second form: the shard pierces on to the next nearest enemy, at half strength.
            shard.phase--
            shard.damage *= 0.5f
            val next = nearestOtherEnemy(enemy.x, enemy.y, enemy.id, AEGIS_RANGE)
            shard.targetId = next?.id ?: -1
            shard.life = max(shard.life, AEGIS_SHARD_LIFE * 0.6f)
        } else {
            shard.life = 0f
            return
        }
    }
}

private fun MutableGameState.missileHits(missile: WeaponEntity) {
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || isShifting(enemy) || !sweptContact(missile, enemy)) continue
        val landed = dealWeaponDamage(enemy, missile.damage, canCrit = true, procKey = missile.procKey).amount > 0f
        if (landed) {
            val runtime = weaponRuntime
            runtime.elementHits = (runtime.elementHits + 1) % HUNTER_DECAY_EVERY
            if (runtime.elementHits == 0) applyWeaponStatus(enemy, StatusElement.ENTROPY, 1)
        }
        missileBurst(missile, enemy)
        // Third form: a missile that kills splits into two smaller ones.
        if (!enemy.alive && missile.phase == 0 && hasMasteryForm(3)) {
            val heading = atan2(missile.vy, missile.vx)
            launchMissile(enemy.x, enemy.y, heading - 0.7f, missile.damage * 0.5f, WeaponRuntime.PROC_NEVER, split = false)
            launchMissile(enemy.x, enemy.y, heading + 0.7f, missile.damage * 0.5f, WeaponRuntime.PROC_NEVER, split = false)
        }
        missile.life = 0f
        return
    }
}

private fun MutableGameState.missileBurst(missile: WeaponEntity, direct: Enemy?) {
    val radius = HUNTER_BURST_RADIUS
    val share = HUNTER_BURST_SHARE * if (hasMasteryForm(2)) HUNTER_BURST_FORM else 1f
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy === direct || !enemy.alive || distanceSquared(missile.x, missile.y, enemy.x, enemy.y) > square(radius + enemy.radius)) continue
        dealWeaponDamage(enemy, missile.damage * share, procKey = WeaponRuntime.PROC_NEVER)
    }
    burst(missile.x, missile.y, 6, 4)
}

private fun MutableGameState.tetherTick(tether: WeaponEntity, delta: Float) {
    val target = enemyById(tether.targetId)
    if (target == null || !target.alive) {
        tether.life = 0f
        return
    }
    val runtime = weaponRuntime
    val agonyRank = agonyPower()
    val primary = tether.phase == 0
    // The second tether rides the primary's ramp.
    val ramp = runtime.tetherRamp
    val share = if (primary) 1f else TETHER_SECOND_SHARE
    val damage = TETHER_DPS * delta * effectiveWeaponPower() * ramp * share * (1f + 0.1f * agonyRank)
    val landed = dealWeaponDamage(target, damage, cadence = WeaponHitCadence.CONTINUOUS, procKey = tether.procKey).amount > 0f
    if (landed) weaponElementTick(target, StatusElement.ION, TETHER_CHARGE_INTERVAL)
    if (!target.alive) {
        // Third form: a tethered kill overloads into the nearest enemies.
        if (hasMasteryForm(3)) {
            val power = content.build.element(StatusElement.ION).perStack * build.potency(StatusElement.ION) * runScale() * TETHER_OVERLOAD_STACKS
            chain(target.x, target.y, target.id, TETHER_OVERLOAD_LINKS, TETHER_RANGE) { link ->
                dealStatusDamage(link, power, StatusElement.ION)
                if (link.alive) applyStatus(link, StatusElement.ION, 1, 1)
            }
        }
        return
    }
    // Dipole signature: ATTRACT draws the tethered enemy in, REPEL throws it out into slams.
    if (signatureActive()) {
        val knockback = knockbackTaken(target)
        val dx = coreX - target.x
        val dy = coreY - target.y
        val distance = max(1f, length(dx, dy))
        val accel = TETHER_PULL * knockback * delta
        if (polarity == Polarity.ATTRACT) {
            target.vx += dx / distance * accel
            target.vy += dy / distance * accel
        } else {
            target.vx -= dx / distance * accel * 1.6f
            target.vy -= dy / distance * accel * 1.6f
            markThrown(target)
        }
    }
}

private fun MutableGameState.beamTick(beam: WeaponEntity, delta: Float) {
    val power = effectiveWeaponPower()
    val agonyRank = agonyPower()
    val dps = SOLAR_DPS * power * (1f + 0.1f * agonyRank)
    val cosine = cos(beam.angle)
    val sine = sin(beam.angle)
    val innerX = coreX + cosine * beam.radius
    val innerY = coreY + sine * beam.radius
    val outerX = coreX + cosine * (beam.radius + beam.extent)
    val outerY = coreY + sine * (beam.radius + beam.extent)
    val arcs = hasMasteryForm(3)
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive) continue
        if (segmentCircleIntersects(innerX, innerY, outerX, outerY, enemy.x, enemy.y, enemy.radius + SOLAR_REACH)) {
            val landed = dealWeaponDamage(enemy, dps * delta, cadence = WeaponHitCadence.CONTINUOUS, procKey = beam.procKey).amount > 0f
            if (landed) weaponElementTick(enemy, StatusElement.THERMAL, SOLAR_SCORCH_INTERVAL)
            continue
        }
        if (!arcs) continue
        // Third form: the arc each beam just swept keeps burning.
        val dx = enemy.x - coreX
        val dy = enemy.y - coreY
        val distance = length(dx, dy)
        if (distance < beam.radius || distance > beam.radius + beam.extent + enemy.radius) continue
        val behind = atan2(sin(beam.angle - atan2(dy, dx)), cos(beam.angle - atan2(dy, dx)))
        if (behind in 0f..SOLAR_ARC_SECTOR) {
            dealWeaponDamage(enemy, dps * SOLAR_ARC_SHARE * delta, cadence = WeaponHitCadence.CONTINUOUS, procKey = beam.procKey)
        }
    }
}

internal const val GYRE_CATCH_RADIUS: Float = 30f
internal const val GYRE_SPIN: Float = 18f
internal const val SENTRY_SHOT_SPEED: Float = 900f
internal const val SENTRY_PIERCE: Int = 1
internal const val LENS_THROW: Float = 420f
internal const val LENS_MASS_INTERVAL: Float = 2.5f
internal const val HOLLOW_PULL: Float = 320f
internal const val AEGIS_TURN_RATE: Float = 9f
internal const val HUNTER_SEEK_RANGE: Float = 900f
internal const val HUNTER_ACCELERATION: Float = 900f
internal const val TETHER_CHARGE_INTERVAL: Float = 0.8f
internal const val TETHER_OVERLOAD_LINKS: Int = 3
internal const val TETHER_OVERLOAD_STACKS: Float = 3f
internal const val SOLAR_REACH: Float = 10f
internal const val SOLAR_SCORCH_INTERVAL: Float = 1.5f
internal const val RIFT_SPENT: Int = 4

// ---------------------------------------------------------------------------------------------
// Weapon shots (player projectiles of the equipped weapon).

/**
 * A weapon shot meets [enemy]: its forms and signature twists, the hit itself (proc key from the shot)
 * and the weapon's own element. Runs inside the projectile pass; the pass removes spent shots.
 */
internal fun MutableGameState.weaponShotHit(projectile: Projectile, sourceWeapon: WeaponId, enemy: Enemy) {
    val equipped = sourceWeapon == weapon
    var damage = projectile.damage
    var forceCrit = false
    when (sourceWeapon) {
        WeaponId.NULL_LANCE -> if (equipped) {
            // Third form: statused or marked enemies take a crit.
            if (hasMasteryForm(3) && (totalStacks(enemy) > 0 || isMarked(enemy) || enemy.status[StatusCell.MARKED_UNTIL] > elapsed)) {
                forceCrit = true
            }
            // Shard signature: a lance on a mark strikes a double Puncture and keeps its pierce.
            if (signatureActive() && tryPuncture(enemy, 2f)) projectile.pierce++
        }
        // Third form: a point-blank pellet tears through without spending its pierce.
        WeaponId.SCATTER_BLOOM -> if (equipped && hasMasteryForm(3) &&
            distanceSquared(coreX, coreY, enemy.x, enemy.y) <= square(SCATTER_POINT_BLANK + enemy.radius)
        ) projectile.pierce++
        else -> Unit
    }
    if (!enemy.alive) return
    val result = dealWeaponDamage(enemy, damage, canCrit = true, sourceWeapon = sourceWeapon, procKey = projectile.procKey, forceCrit = forceCrit)
    if (!equipped || result.amount <= 0f) return
    when (sourceWeapon) {
        WeaponId.ION_SWARM -> {
            val runtime = weaponRuntime
            runtime.elementHits = (runtime.elementHits + 1) % ION_SWARM_CHARGE_EVERY
            if (runtime.elementHits == 0) applyWeaponStatus(enemy, StatusElement.ION, 1)
        }
        WeaponId.QUASAR_CANNON -> {
            applyWeaponStatus(enemy, StatusElement.SONIC, 2)
            val speed = max(1f, length(projectile.vx, projectile.vy))
            val knockback = QUASAR_KNOCKBACK * knockbackTaken(enemy)
            if (enemy.alive && knockback > 0f) {
                enemy.vx += projectile.vx / speed * knockback
                enemy.vy += projectile.vy / speed * knockback
                markThrown(enemy)
            }
        }
        WeaponId.SCATTER_BLOOM -> if (signatureActive()) markForPuncture(enemy)
        else -> Unit
    }
}

/** Quasar Cannon's third form: a shot that ends (spent or out of life) bursts into a sonic wave. */
internal fun MutableGameState.quasarShotEnded(projectile: Projectile) {
    if (weapon != WeaponId.QUASAR_CANNON || !hasMasteryForm(3) || projectile.hostile) return
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || distanceSquared(projectile.x, projectile.y, enemy.x, enemy.y) > square(QUASAR_BURST_RADIUS + enemy.radius)) continue
        val landed = dealWeaponDamage(enemy, projectile.damage * QUASAR_BURST_SHARE, procKey = WeaponRuntime.PROC_NEVER).amount > 0f
        if (landed) applyWeaponStatus(enemy, StatusElement.SONIC, 1)
    }
    shockwave(projectile.x, projectile.y, 0.3f, QUASAR_BURST_RADIUS, 0)
}

/**
 * A Prism Relay shard bounced off an enemy: the third form bursts there, and the Tesseract signature
 * runs the bounce through the lattice (every enemy on a lattice edge is cut).
 */
internal fun MutableGameState.prismRelayBounced(projectile: Projectile) {
    if (weapon != WeaponId.PRISM_RELAY) return
    if (hasMasteryForm(3)) {
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (!enemy.alive || projectile.hasRecordedEnemyHit(enemy.id)) continue
            if (distanceSquared(projectile.x, projectile.y, enemy.x, enemy.y) <= square(PRISM_BURST_RADIUS + enemy.radius)) {
                dealWeaponDamage(enemy, projectile.damage * PRISM_BURST_SHARE, procKey = WeaponRuntime.PROC_NEVER)
            }
        }
        shockwave(projectile.x, projectile.y, 0.2f, PRISM_BURST_RADIUS, 0)
    }
    val nodes = latticeNodeCount
    if (signatureActive() && nodes >= 2) {
        for (node in 0 until nodes) {
            val next = (node + 1) % nodes
            if (nodes == 2 && node == 1) break
            val x0 = latticeNodeX(node)
            val y0 = latticeNodeY(node)
            val x1 = latticeNodeX(next)
            val y1 = latticeNodeY(next)
            for (index in enemies.indices) {
                val enemy = enemies[index]
                if (enemy.alive && segmentCircleIntersects(x0, y0, x1, y1, enemy.x, enemy.y, enemy.radius + 8f)) {
                    dealWeaponDamage(enemy, projectile.damage * PRISM_LATTICE_SHARE, procKey = WeaponRuntime.PROC_NEVER)
                }
            }
            addWeaponArc(x0, y0, x1, y1, 0.12f)
        }
    }
}

internal const val ION_SWARM_CHARGE_EVERY: Int = 2
internal const val QUASAR_KNOCKBACK: Float = 220f
internal const val PRISM_BURST_RADIUS: Float = 70f
internal const val PRISM_BURST_SHARE: Float = 0.25f
internal const val PRISM_LATTICE_SHARE: Float = 0.4f
