// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.domain.simulation

import kinetickk.feature.game.domain.model.*
import kinetickk.feature.game.domain.protocol.SoundCue
import kinetickk.feature.game.domain.protocol.VisualFxCue
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min


internal fun MutableGameState.update(rawDelta: Float) {
    lastTransitionSteps = 0
    if (phase != GamePhase.RUNNING || screen != UiScreen.GAME) {
        accumulator = 0f
        return
    }
    if (!rawDelta.isFinite()) {
        accumulator = 0f
        return
    }
    val scaledDelta = rawDelta.coerceIn(0f, 0.1f) * settings.simulationSpeed
    accumulator = min(0.3f, accumulator + scaledDelta)
    var steps = 0
    while (accumulator >= MutableGameState.FIXED_STEP && phase == GamePhase.RUNNING && steps < 48) {
        simulateStep(MutableGameState.FIXED_STEP)
        accumulator -= MutableGameState.FIXED_STEP
        steps++
    }
    lastTransitionSteps = steps
}

internal fun MutableGameState.simulateStep(delta: Float) {
    elapsed += delta
    runGrace = max(0f, runGrace - delta)
    hurtCooldown = max(0f, hurtCooldown - delta)
    dashPhaseTime = max(0f, dashPhaseTime - delta)
    screenShake = max(0f, screenShake - delta * 18f)
    lastImpactTime = max(0f, lastImpactTime - delta)
    damageFlash = max(0f, damageFlash - delta * 3.4f)
    messageTime = max(0f, messageTime - delta)
    comboTime = max(0f, comboTime - delta)
    weaponBeamTime = max(0f, weaponBeamTime - delta)
    if (comboTime <= 0f) combo = 0

    updateSurvival(delta)
    updateOverdrive(delta)
    updateHeat(delta)
    updateCore(delta)
    if (phase != GamePhase.RUNNING) return
    emitVisualFx(
        VisualFxCue.MotionSample(
            deltaSeconds = delta,
            previousCoreX = previousCoreX,
            previousCoreY = previousCoreY,
            speed = speed,
            dashPhaseTime = dashPhaseTime,
        ),
    )
    updateCamera(delta)
    updateWeapons(delta)
    updateEnemies(delta)
    updateRelicRuntime(delta)
    updateProjectiles(delta)
    updatePickups(delta)
    updateTotem(delta)
    emitVisualFx(VisualFxCue.EffectsAdvanced(delta))
    spawnWave(delta)
    resolveCollisions(delta)
    if (phase != GamePhase.RUNNING) return
    rebaseWorldIfNeeded()

    openNextPendingChoice()

    if (!bossSpawned && phase == GamePhase.RUNNING && elapsed >= MutableGameState.RUN_DURATION_SECONDS) {
        bossSpawned = true
        message = "THE ARCHITECT"
        messageTime = 3f
        projectiles.removeAll { it.hostile }
        // The terminal boss has priority over the oldest ordinary enemy at the hard cap.
        if (enemies.size >= MutableGameState.MAX_ENEMIES) enemies.removeAt(0)
        spawnEnemy(EnemyType.ARCHITECT)
    }
}

internal fun MutableGameState.updateSurvival(delta: Float) {
    timeSinceDamage += delta
    shieldRechargeDelay = max(0f, shieldRechargeDelay - delta)
    if (regenPerSecond > 0f && timeSinceDamage >= 3f) hp = min(maxHp, hp + regenPerSecond * delta)
    if (maxShield > 0f && shieldRechargeDelay <= 0f) {
        shield = min(maxShield, shield + max(2f, maxShield * 0.09f) * delta)
    }
}

internal fun MutableGameState.updateRelicRuntime(delta: Float) {
    relicCooldowns.indices.forEach { index ->
        relicCooldowns[index] = max(0f, relicCooldowns[index] - delta)
    }
    slipstreamRelayTime = max(0f, slipstreamRelayTime - delta)
    borrowedMomentTime = max(0f, borrowedMomentTime - delta)
    val brakeRank = relicRank(RelicId.BRAKEPOINT_MEMORY)
    if (brakeRank > 0 && braking) {
        val cap = 0.18f * brakeRank
        brakepointCharge = min(cap, brakepointCharge + cap * delta)
    }

    val glassIndex = RelicId.GLASS_WITNESS.ordinal
    val scarIndex = RelicId.SCAR_TISSUE.ordinal
    val scarRank = relicRank(RelicId.SCAR_TISSUE)
    enemies.forEach { enemy ->
        enemy.relicQualificationCooldown = max(0f, enemy.relicQualificationCooldown - delta)
        enemy.relicTimers[glassIndex] = max(0f, enemy.relicTimers[glassIndex] - delta)
        if (enemy.relicTimers[scarIndex] > 0f) {
            enemy.relicTimers[scarIndex] = max(0f, enemy.relicTimers[scarIndex] - delta)
            if (scarRank > 0 && enemy.hp > 0f) {
                val intensity = max(1, enemy.relicCounters[scarIndex])
                damageEnemy(enemy, 3f * scarRank * intensity * delta)
            }
        }
    }

    val iterator = delayedRelicHits.iterator()
    while (iterator.hasNext()) {
        val delayed = iterator.next()
        delayed.delay -= delta
        if (delayed.delay > 0f) continue
        val target = enemies.firstOrNull { it.id == delayed.enemyId && !it.dead && it.hp > 0f }
        if (target != null && relicRank(delayed.relicId) > 0) {
            damageEnemy(target, delayed.damage)
            relicProcCounts[delayed.relicId.ordinal]++
            burst(target.x, target.y, 4, 2)
        }
        iterator.remove()
    }
}

internal fun MutableGameState.updateOverdrive(delta: Float) {
    if (overdriveTime > 0f) {
        overdriveTime = max(0f, overdriveTime - delta)
    } else if (overdriveCharge >= 100f) {
        overdriveCharge -= 100f
        overdriveTime = 7f
        val paradoxRank = relicRank(RelicId.ENGINE_OF_PARADOX)
        if (paradoxRank > 0) {
            weaponClock = 0f
            weaponSecondaryClock = 0f
            heat = max(0f, heat - 10f)
            relicProcCounts[RelicId.ENGINE_OF_PARADOX.ordinal]++
        }
        message = "KINETIC OVERDRIVE"
        messageTime = 2f
        emitSound(SoundCue.OVERDRIVE)
    }
    if (speed > 450f) {
        val gain = min(5f, (speed - 450f) / 900f) * delta * 8f * overdriveGain
        overdriveCharge = min(125f, overdriveCharge + gain)
    }
}

internal fun MutableGameState.updateHeat(delta: Float) {
    val cooling = coolingRate * if (braking) 1.6f else 1f
    overheatHoldTime = max(0f, overheatHoldTime - delta)
    if (overheatHoldTime <= 0f) heat = max(0f, heat - cooling * delta)
    if (overheated && heat <= 28f) {
        overheated = false
        message = "DASH ONLINE"
        messageTime = 1f
        emitSound(SoundCue.RECOVERED)
    }
    dashBufferTime = max(0f, dashBufferTime - delta)
    if (dashBufferTime > 0f && dashReady) {
        dashBufferTime = 0f
        performDash()
    }
}

internal fun MutableGameState.performDash() {
    val departureX = coreX
    val departureY = coreY
    val targetX = cameraX + pointerX - screenWidth * 0.5f
    val targetY = cameraY + pointerY - screenHeight * 0.5f
    val dx = targetX - coreX
    val dy = targetY - coreY
    val distance = length(dx, dy)
    val directionX = if (distance > 24f) dx / distance else lastAimDirectionX
    val directionY = if (distance > 24f) dy / distance else lastAimDirectionY
    val forwardVelocity = velocityX * directionX + velocityY * directionY
    if (forwardVelocity < 0f) {
        val reverseAssist = forwardVelocity * 0.72f
        velocityX -= directionX * reverseAssist
        velocityY -= directionY * reverseAssist
    }
    velocityX += directionX * dashImpulse
    velocityY += directionY * dashImpulse
    heat += dashHeatCost
    dashPhaseTime = 0.24f
    screenShake = max(screenShake, 5f)
    overdriveCharge = min(125f, overdriveCharge + 5f * overdriveGain)
    shockwave(coreX, coreY, 0.3f, 112f, 0)
    directionalBurst(coreX, coreY, 18, 0, -directionX, -directionY)
    val ghostRank = relicRank(RelicId.GHOST_VECTOR)
    if (ghostRank > 0) {
        val radius = 72f + 14f * ghostRank
        enemies.forEach { enemy ->
            if (!enemy.dead && enemy.hp > 0f && distanceSquared(departureX, departureY, enemy.x, enemy.y) <= square(radius + enemy.radius)) {
                damageEnemy(enemy, 24f * ghostRank)
            }
        }
        shockwave(departureX, departureY, 0.22f, radius, 2)
        relicProcCounts[RelicId.GHOST_VECTOR.ordinal]++
    }
    emitSound(SoundCue.DASH)
    if (heat >= MutableGameState.MAX_HEAT) {
        heat = MutableGameState.MAX_HEAT
        overheated = true
        overheatHoldTime = 0.08f
        message = "OVERHEAT"
        messageTime = 1.4f
        emitSound(SoundCue.OVERHEAT)
    }
}

internal fun MutableGameState.updateCore(delta: Float) {
    previousCoreX = coreX
    previousCoreY = coreY
    val targetX = cameraX + pointerX - screenWidth * 0.5f
    val targetY = cameraY + pointerY - screenHeight * 0.5f
    val dx = targetX - coreX
    val dy = targetY - coreY
    val distance = max(0.001f, length(dx, dy))
    if (distance > 24f) {
        lastAimDirectionX = dx / distance
        lastAimDirectionY = dy / distance
    }
    val directionX = dx / distance
    val directionY = dy / distance
    updatePolarityStability(directionX, directionY, delta)
    val overdriveMultiplier = if (overdriveTime > 0f) 1.3f else 1f
    val pull = (92f + distance * magnetStrength) * overdriveMultiplier * tetherAuthority
    val brakeFactor = if (braking) 1.12f else 1f
    velocityX += directionX * pull * brakeFactor * delta
    velocityY += directionY * pull * brakeFactor * delta
    val damping = exp((if (braking) -5.2f else -dragCoefficient) * delta)
    velocityX *= damping
    velocityY *= damping

    if (!velocityX.isFinite() || !velocityY.isFinite()) {
        velocityX = 0f
        velocityY = 0f
    }
    coreX += velocityX * delta
    coreY += velocityY * delta

    if (!coreX.isFinite() || !coreY.isFinite()) {
        coreX = previousCoreX
        coreY = previousCoreY
        velocityX = 0f
        velocityY = 0f
    }

    if (runGrace <= 0f && segmentCircleIntersects(
            previousCoreX - previousSingularityX,
            previousCoreY - previousSingularityY,
            coreX - targetX,
            coreY - targetY,
            0f,
            0f,
            MutableGameState.CURSOR_KILL_RADIUS,
        )
    ) {
        endRun("SINGULARITY CONTACT")
    }
    previousSingularityX = targetX
    previousSingularityY = targetY
}

internal fun MutableGameState.updatePolarityStability(
    directionX: Float,
    directionY: Float,
    delta: Float,
) {
    val halfWidth = max(1f, screenWidth * 0.5f)
    val halfHeight = max(1f, screenHeight * 0.5f)
    val normalizedX = (pointerX - halfWidth) / halfWidth
    val normalizedY = (pointerY - halfHeight) / halfHeight
    val reach = clamp(length(normalizedX, normalizedY), 0f, 1f)
    val load = reach * reach
    val headingDot = clamp(
        saturationHeadingX * directionX + saturationHeadingY * directionY,
        -1f,
        1f,
    )
    val headingCross = saturationHeadingX * directionY - saturationHeadingY * directionX
    val turnAngle = kotlin.math.abs(atan2(headingCross, headingDot))
    val meaningfulTurn = max(0f, turnAngle - 0.15f)
    val nearRecovery = clamp((0.35f - reach) / (0.35f - 0.18f), 0f, 1f)
    val stabilityBefore = polarityStability
    val saturation = clamp(
        (1f - polarityStability) +
            0.40f * load * delta -
            0.85f * nearRecovery * delta -
            0.58f * meaningfulTurn,
        0f,
        1f,
    )
    polarityStability = 1f - saturation
    if (turnAngle > 0.15f) {
        saturationHeadingX = directionX
        saturationHeadingY = directionY
    }
    if (stabilityBefore >= 0.35f && polarityStability < 0.35f) {
        message = "POLARITY FIELD STRAIN"
        messageTime = 1.4f
        emitSound(SoundCue.OVERHEAT)
    }
}

internal fun MutableGameState.updateCamera(delta: Float) {
    val factor = 1f - exp(-7.5f * delta)
    cameraX = lerp(cameraX, coreX, factor)
    cameraY = lerp(cameraY, coreY, factor)
}
