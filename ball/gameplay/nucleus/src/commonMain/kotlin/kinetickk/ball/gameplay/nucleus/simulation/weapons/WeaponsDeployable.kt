// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.weapons

import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.build.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*

internal fun MutableGameState.sampleFluxTrail() {
    val runtime = weaponRuntime
    val heading = atan2(velocityY, velocityX)
    val turn = abs(atan2(sin(heading - runtime.previousHeading), cos(heading - runtime.previousHeading)))
    runtime.previousHeading = heading
    if (speed <= 45f) {
        trailLastX = coreX
        trailLastY = coreY
        return
    }
    val dx = coreX - trailLastX
    val dy = coreY - trailLastY
    val distance = length(dx, dy)
    if (distance <= 0f) return
    val agonyRank = agonyPower()
    // Orb signature: a trail laid while turning (a hard turn: 60° per 100 dp or tighter) burns harder and longer.
    val moved = length(coreX - previousCoreX, coreY - previousCoreY)
    val turning = signatureActive() && moved > 0f && turn / moved >= FLUX_TURN_CURVATURE
    val life = (if (hasMasteryForm(2)) FLUX_LONG_LIFE else TRAIL_LIFE_SECONDS) * (if (turning) 1.5f else 1f) + agonyRank * 0.18f
    val scale = if (turning) 1.5f else 1f
    // Samples spread so the bounded trail holds the longest live point's whole life and fade at this speed:
    // at most MAX_TRAIL_POINTS samples in that time, so the oldest burning point is never evicted early.
    var longestLife = life
    for (index in trail.indices) longestLife = max(longestLife, trail[index].life)
    val spacing = max(FLUX_SPACING, speed * (longestLife + TRAIL_FADE_SECONDS) / MutableGameState.MAX_TRAIL_POINTS)
    var sampleDistance = spacing - trailDistanceCarry
    var samples = 0
    while (sampleDistance <= distance && samples < MutableGameState.MAX_TRAIL_SAMPLES_PER_UPDATE) {
        val amount = sampleDistance / distance
        trail += TrailPoint(lerp(trailLastX, coreX, amount), lerp(trailLastY, coreY, amount), life = life, scale = scale)
        sampleDistance += spacing
        samples++
    }
    trailDistanceCarry = (trailDistanceCarry + distance) % spacing
    while (trail.size > MutableGameState.MAX_TRAIL_POINTS) trail.removeAt(0)
    trailLastX = coreX
    trailLastY = coreY
    if (agonyRank > 0) agonyMutationCounts[WeaponId.FLUX_WAKE.ordinal]++
}

internal fun MutableGameState.updateGravityMines(agonyRank: Int) {
    val runtime = weaponRuntime
    // Diamond signature: a parry drops a mine that detonates at once, doubled.
    if (signatureActive() && parriedThisStep && elapsed >= runtime.signatureReadyAt) {
        runtime.signatureReadyAt = elapsed + PARRY_TWIST_GUARD
        val mine = WeaponNode(WeaponNodeType.GRAVITY_MINE, coreX, coreY, 0.001f, 0.001f, gravityMineRadius(agonyRank),
            procKey = beginWeaponActivation(), scale = 2f)
        // It joins the field to detonate this step; a full field (the node bound) sets it off at once instead.
        if (!tryAddWeaponNode(mine)) explodeMine(mine)
    }
    if (weaponClock <= 0f && (braking || speed > 220f)) {
        // The second form keeps more mines in the field: they come sooner.
        weaponClock = cooldown((if (braking) 0.72f else 1.15f) * if (hasMasteryForm(2)) 0.85f else 1f)
        val serial = beginWeaponActivation()
        val mineLife = 0.75f + agonyRank * 0.04f
        if (tryAddWeaponNode(WeaponNode(WeaponNodeType.GRAVITY_MINE, coreX, coreY, mineLife, mineLife, gravityMineRadius(agonyRank),
                procKey = serial))
        ) {
            if (agonyRank > 0) agonyMutationCounts[WeaponId.GRAVITY_MINES.ordinal]++
            emitSound(GameplayAudioCue.WEAPON_LIGHT)
        }
    }
}

private fun MutableGameState.gravityMineRadius(agonyRank: Int): Float =
    GRAVITY_MINE_RADIUS * (if (hasMasteryForm(1)) 1.3f else 1f) + agonyRank * 12f

internal fun MutableGameState.tryAddWeaponNode(node: WeaponNode): Boolean {
    val limit = if (hasMasteryForm(2)) MutableGameState.MAX_WEAPON_NODES else BASE_MINE_LIMIT
    if (weaponNodes.size >= limit) return false
    weaponNodes += node
    return true
}

internal fun MutableGameState.updateWeaponNodes(delta: Float) {
    var index = 0
    while (index < weaponNodes.size) {
        val node = weaponNodes[index]
        node.life -= delta
        // An enemy stepping onto a mine sets it off early.
        if (node.life > 0f && enemyWithin(node.x, node.y, node.radius * MINE_PROXIMITY)) node.life = 0f
        if (node.life <= 0f) {
            weaponNodes.removeAt(index)
            explodeMine(node)
        } else {
            index++
        }
    }
}

private fun MutableGameState.enemyWithin(x: Float, y: Float, radius: Float): Boolean {
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.alive && !isShifting(enemy) && distanceSquared(x, y, enemy.x, enemy.y) <= square(radius + enemy.radius)) return true
    }
    return false
}

internal fun MutableGameState.explodeMine(node: WeaponNode) {
    val power = effectiveWeaponPower()
    for (index in enemies.indices) {
        val enemy = enemies[index]
        val distance = length(enemy.x - node.x, enemy.y - node.y)
        if (distance <= node.radius + enemy.radius) {
            val landed = dealWeaponDamage(enemy, MINE_DAMAGE * node.scale * power * (1f - distance / (node.radius * 1.7f)).coerceAtLeast(0.35f),
                canCrit = true, procKey = node.procKey).amount > 0f
            if (landed) weaponElementTick(enemy, StatusElement.GRAVITIC, MINE_MASS_INTERVAL)
            val pull = (1f - distance / max(1f, node.radius)).coerceAtLeast(0f) * 170f * knockbackTaken(enemy)
            enemy.vx += (node.x - enemy.x) / max(1f, distance) * pull
            enemy.vy += (node.y - enemy.y) / max(1f, distance) * pull
        }
    }
    // Second form: a blast sets off nearby mines.
    if (hasMasteryForm(2)) {
        for (index in weaponNodes.indices) {
            val other = weaponNodes[index]
            if (distanceSquared(other.x, other.y, node.x, node.y) <= square(node.radius * MINE_CHAIN_REACH)) other.life = min(other.life, MINE_CHAIN_FUSE)
        }
    }
    // Third form: the mine collapses into a short well, which grows with weapon power like the blast.
    if (hasMasteryForm(3)) {
        addWeaponEntity(WeaponEntity(WeaponEntityKind.WELL, node.x, node.y, node.radius * MINE_WELL_REACH, MINE_WELL_SECONDS, MINE_WELL_SECONDS,
            damage = MINE_WELL_DPS * power, procKey = node.procKey, phase = 1))
    }
    burst(node.x, node.y, 18, 2)
    screenShake = max(screenShake, 5f)
    emitSound(GameplayAudioCue.IMPACT)
}

internal fun MutableGameState.deploySentry(power: Float, agonyRank: Int) {
    weaponClock = cooldown(SENTRY_INTERVAL)
    onWeaponActivation()
    var x = coreX
    var y = coreY
    // Tesseract signature: sentries take the newest lattice node.
    val nodes = latticeNodeCount
    if (signatureActive() && nodes > 0) {
        x = latticeNodeX(nodes - 1)
        y = latticeNodeY(nodes - 1)
    }
    val limit = SENTRY_LIMIT + (if (hasMasteryForm(1)) 1 else 0) + if (agonyRank > 0) 1 else 0
    val entities = weaponRuntime.entities
    var live = 0
    var oldest: WeaponEntity? = null
    for (index in entities.indices) {
        val entity = entities[index]
        if (entity.kind != WeaponEntityKind.SENTRY || entity.life <= 0f) continue
        live++
        if (oldest == null || entity.life < oldest.life) oldest = entity
    }
    // At the limit the oldest sentry folds (no expiry blast) to make room.
    if (live >= limit) oldest?.let { it.life = 0f; it.phase = SENTRY_FOLDED }
    addWeaponEntity(WeaponEntity(WeaponEntityKind.SENTRY, x, y, SENTRY_RADIUS, SENTRY_LIFE, SENTRY_LIFE,
        damage = SENTRY_DAMAGE * power * (1f + 0.1f * agonyRank), timer = 0f, procKey = WeaponRuntime.sourceKey(SENTRY_SOURCE)))
    if (agonyRank > 0) agonyMutationCounts[WeaponId.SENTRY_GRID.ordinal]++
    shockwave(x, y, 0.25f, 60f, 0)
    emitSound(GameplayAudioCue.WEAPON_LIGHT)
}

internal fun MutableGameState.castLenses(power: Float, agonyRank: Int) {
    weaponClock = cooldown(LENS_COOLDOWN)
    val serial = beginWeaponActivation()
    val angle = atan2(lastAimDirectionY, lastAimDirectionX)
    val count = 1 + (if (hasMasteryForm(2)) 1 else 0) + if (agonyRank > 0) 1 else 0
    for (lens in 0 until count) {
        val lensAngle = angle + (lens - (count - 1) * 0.5f) * LENS_SPREAD
        addWeaponEntity(WeaponEntity(WeaponEntityKind.LENS, coreX, coreY, LENS_RADIUS, LENS_FUSE, LENS_FUSE,
            vx = cos(lensAngle) * LENS_SPEED, vy = sin(lensAngle) * LENS_SPEED,
            damage = power * (1f + 0.1f * agonyRank) * if (lens == 0) 1f else LENS_EXTRA_SHARE,
            angle = lensAngle, procKey = serial))
    }
    if (agonyRank > 0) agonyMutationCounts[WeaponId.EVENT_LENS.ordinal]++
    emitSound(GameplayAudioCue.WEAPON_LIGHT)
}

internal fun MutableGameState.lensWellRadius(): Float = LENS_WELL_RADIUS * if (hasMasteryForm(1)) 1.1f else 1f

internal fun MutableGameState.updateHollowRifts(delta: Float, power: Float, agonyRank: Int, dashed: Boolean) {
    val runtime = weaponRuntime
    val damage = HOLLOW_DAMAGE * power * (1f + 0.1f * agonyRank)
    val reach = HOLLOW_REACH * if (hasMasteryForm(1)) 1.3f else 1f
    val entities = runtime.entities
    // A rift laid by the current Dash stretches with the Core until the Dash ends.
    for (index in entities.indices) {
        val entity = entities[index]
        if (entity.kind == WeaponEntityKind.RIFT && entity.phase == RIFT_GROWING) {
            if (dashPhaseTime > 0f) {
                entity.endX = coreX
                entity.endY = coreY
            } else {
                entity.phase = RIFT_ARMED
            }
        }
    }
    if (dashed) {
        val blink = blinkThisStep
        val startX = if (blink) blinkFromX else coreX
        val startY = if (blink) blinkFromY else coreY
        // Crescent signature: Blink scars become full rifts, at the strength of the Crescent's own scar.
        val scale = if (blink && signatureActive()) HOLLOW_BLINK_SCALE else 1f
        addWeaponEntity(WeaponEntity(WeaponEntityKind.RIFT, startX, startY, reach, HOLLOW_FUSE + HOLLOW_LINGER + DASH_PHASE_SECONDS,
            HOLLOW_FUSE + HOLLOW_LINGER + DASH_PHASE_SECONDS, damage = damage * scale * continuousWeaponRate(), endX = coreX, endY = coreY,
            timer = HOLLOW_FUSE + if (blink) 0f else DASH_PHASE_SECONDS, procKey = beginWeaponActivation(),
            phase = if (blink) RIFT_ARMED else RIFT_GROWING))
        if (agonyRank > 0) agonyMutationCounts[WeaponId.HOLLOW_RIFT.ordinal]++
    }
    runtime.passiveClock -= delta
    if (runtime.passiveClock <= 0f && speed >= HOLLOW_PASSIVE_SPEED) {
        runtime.passiveClock = cooldown(HOLLOW_PASSIVE_INTERVAL)
        val back = 0.15f
        addWeaponEntity(WeaponEntity(WeaponEntityKind.RIFT, coreX - velocityX * back, coreY - velocityY * back, reach,
            HOLLOW_FUSE + HOLLOW_LINGER, HOLLOW_FUSE + HOLLOW_LINGER, damage = damage * HOLLOW_PASSIVE_SHARE, endX = coreX, endY = coreY,
            timer = HOLLOW_FUSE, procKey = beginWeaponActivation(), phase = RIFT_ARMED))
        if (agonyRank > 0) agonyMutationCounts[WeaponId.HOLLOW_RIFT.ordinal]++
    }
}
