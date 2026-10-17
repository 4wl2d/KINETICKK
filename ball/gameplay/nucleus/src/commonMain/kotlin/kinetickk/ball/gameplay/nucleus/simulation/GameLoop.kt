// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId

import kinetickk.ball.gameplay.api.*
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min


internal fun MutableGameState.update(rawDelta: Float) {
    lastTransitionSteps = 0
    if (phase != GamePhase.RUNNING) {
        accumulator = 0f
        return
    }
    if (!rawDelta.isFinite()) {
        accumulator = 0f
        return
    }
    runAdmittedFrame(
        nextSimulationAccumulator(
            currentAccumulator = accumulator,
            rawDeltaSeconds = rawDelta,
            simulationSpeed = settings.simulationSpeed,
        ),
    )
}

/** Runs a running frame whose real time [nextAccumulator] was already admitted ([nextSimulationAccumulator]). */
internal fun MutableGameState.runAdmittedFrame(nextAccumulator: Float) {
    check(phase == GamePhase.RUNNING) { "Only a running simulation admits frame time" }
    lastTransitionSteps = 0
    accumulator = nextAccumulator
    consumeFixedStepBudget()
}

internal fun nextSimulationAccumulator(
    currentAccumulator: Float,
    rawDeltaSeconds: Float,
    simulationSpeed: Float,
): Float = min(
    MAX_SIMULATION_ACCUMULATOR_SECONDS,
    currentAccumulator +
        rawDeltaSeconds.coerceIn(0f, MAX_SIMULATION_RAW_DELTA_SECONDS) * simulationSpeed,
)

/** Executes the already-admitted accumulator while retaining the first step beyond the frame cap. */
internal fun MutableGameState.consumeFixedStepBudget() {
    var steps = 0
    while (
        fixedStepIsDue(accumulator) &&
        phase == GamePhase.RUNNING &&
        steps < MAX_FIXED_STEPS_PER_FRAME
    ) {
        simulateStep(MutableGameState.FIXED_STEP)
        accumulator -= MutableGameState.FIXED_STEP
        steps++
    }
    lastTransitionSteps = steps
}

/**
 * Whether [accumulator] holds a whole fixed step. Adding frame deltas such as 1/144 s and
 * subtracting steps rounds in Float, so a sum that is nominally on a step boundary can sit an ulp
 * below it; within [FIXED_STEP_ROUNDING_TOLERANCE] the step is due. That keeps the step at which an
 * input lands independent of the frame rate, while a delta that is genuinely shorter (0.02f is
 * below 20 ms) still waits for the next frame.
 */
internal fun fixedStepIsDue(accumulator: Float): Boolean =
    accumulator >= MutableGameState.FIXED_STEP - FIXED_STEP_ROUNDING_TOLERANCE

/**
 * Two ulps of the fixed step: Float rounding of the accumulator, never real time. `Float.ulp` is
 * not common Kotlin (no Wasm), so the ulp is the exact gap to the next representable Float.
 */
internal val FIXED_STEP_ROUNDING_TOLERANCE: Float =
    (Float.fromBits(MutableGameState.FIXED_STEP.toRawBits() + 1) - MutableGameState.FIXED_STEP) * 2f

internal const val MAX_FIXED_STEPS_PER_FRAME: Int = 48
internal const val MAX_SIMULATION_RAW_DELTA_SECONDS: Float = 0.1f
internal const val MAX_SIMULATION_ACCUMULATOR_SECONDS: Float = 0.3f

internal fun MutableGameState.simulateStep(delta: Float) {
    interpolationPrimed = true
    previousCameraX = cameraX
    previousCameraY = cameraY
    elapsed += delta
    // One cue budget covers every Break and reaction source in the step.
    statusCuesThisStep = 0
    if (runGrace.toRawBits() != 0) runGrace = max(0f, runGrace - delta)
    if (hurtCooldown.toRawBits() != 0) hurtCooldown = max(0f, hurtCooldown - delta)
    if (dashPhaseTime.toRawBits() != 0) dashPhaseTime = max(0f, dashPhaseTime - delta)
    if (screenShake.toRawBits() != 0) screenShake = max(0f, screenShake - delta * 18f)
    if (lastImpactTime.toRawBits() != 0) lastImpactTime = max(0f, lastImpactTime - delta)
    if (damageFlash.toRawBits() != 0) damageFlash = max(0f, damageFlash - delta * 3.4f)
    if (messageTime.toRawBits() != 0) messageTime = max(0f, messageTime - delta)
    if (comboTime.toRawBits() != 0) comboTime = max(0f, comboTime - delta)
    if (weaponBeamTime.toRawBits() != 0) weaponBeamTime = max(0f, weaponBeamTime - delta)
    if (comboTime <= 0f && combo > 0) {
        // CHAIN III: a lapsing combo halves instead of resetting.
        combo = if (tagTier(BuildTag.CHAIN) >= 3 && combo >= 2) {
            comboTime = effectiveComboWindow() * 0.5f
            combo / 2
        } else 0
    }

    updateSurvival(delta)
    updateOverdrive(delta)
    updateHeat(delta)
    updateCore(delta)
    if (phase != GamePhase.RUNNING) return
    // Obstacles bend the Core's path before anything meets it at its new position.
    updateObstacles()
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
    updateBuildEdges(delta)
    if (phase != GamePhase.RUNNING) return
    updateCharacterRuntime(delta)
    updateCamera(delta)
    updatePointsOfInterest(delta)
    updateWeapons(delta)
    updateEnemies(delta)
    updateBossEncounter()
    updateStatuses(delta)
    updateCombatFields(delta)
    updateRelicRuntime(delta)
    if (phase != GamePhase.RUNNING) return
    updateProjectiles(delta)
    updatePickups(delta)
    updateTotem(delta)
    emitVisualFx(VisualFxCue.EffectsAdvanced(delta))
    spawnWave(delta)
    resolveCollisions(delta)
    if (phase != GamePhase.RUNNING) return
    rebaseWorldIfNeeded()

    openNextPendingChoice()

    if (!bossSpawned && phase == GamePhase.RUNNING && elapsed >= content.tempo.bossAtSeconds) {
        bossSpawned = true
        message = "THE ARCHITECT"
        messageTime = 3f
        projectiles.removeMatchingStable { projectile -> projectile.hostile }
        // The terminal boss has priority over the oldest ordinary enemy at the hard cap.
        if (enemies.size >= content.rebirth.maxActiveEnemies) enemies.removeAt(0)
        spawnEnemy(EnemyType.ARCHITECT)
    }
}

internal fun MutableGameState.updateSurvival(delta: Float) {
    timeSinceDamage += delta
    if (shieldRechargeDelay.toRawBits() != 0) {
        shieldRechargeDelay = max(0f, shieldRechargeDelay - delta)
    }
    if (regenPerSecond > 0f && timeSinceDamage >= 3f && build.regenerationToDecay <= 0f) {
        heal(regenPerSecond * delta, HealSource.REGENERATION)
    }
    val capacity = shieldCapacity()
    if (shield > capacity) {
        // Overfilled shield (Hardlight Weave) decays back toward capacity.
        shield = max(capacity, shield - 3f * delta)
    } else if (capacity > 0f && shieldRechargeDelay <= 0f && !build.liability(RelicId.AEGIS_INVERSION)) {
        shield = min(capacity, shield + max(2f, capacity * 0.09f) * delta)
        if (shield >= capacity * 0.5f) shieldBreakArmed = true
    }
    if (shield > capacity * (if (build.overhealOverfill > 0f) build.overhealOverfill else 1f)) {
        shield = capacity * if (build.overhealOverfill > 0f) build.overhealOverfill else 1f
    }
}

internal fun MutableGameState.updateOverdrive(delta: Float) {
    if (overdriveTime > 0f) {
        overdriveTime = max(0f, overdriveTime - delta)
        if (overdriveTime <= 0f) {
            overdriveBurnoutUntil = elapsed + OVERDRIVE_BURNOUT_SECONDS
            overdriveExtension = 0f
            overdriveKills = 0
        }
    } else if (overdriveCharge >= 100f) {
        overdriveCharge -= 100f
        overdriveTime = 7f
        overdriveExtension = 0f
        overdriveKills = 0
        if (tagTier(BuildTag.OVERCLOCK) >= 3) {
            heat = 0f
            overdriveTime += 2f
        }
        if (build.pairing(RelicPairingId.MELTDOWN)) {
            val before = heat
            heat = MutableGameState.MAX_HEAT
            applyOverheatEdge(before)
        }
        message = "KINETIC OVERDRIVE"
        messageTime = 2f
        emitSound(GameplayAudioCue.OVERDRIVE)
        fireClauses(EffectTrigger.OVERDRIVE)
    }
    if (speed > 450f) gainOverdrive(min(5f, (speed - 450f) / 900f) * delta * 8f)
}

internal fun MutableGameState.updateHeat(delta: Float) {
    val brakeCooling = if (tagTier(BuildTag.ANCHOR) >= 1) 2.4f else 1.6f
    val cooling = effectiveCooling() * if (braking) brakeCooling else 1f
    if (overheatHoldTime.toRawBits() != 0) overheatHoldTime = max(0f, overheatHoldTime - delta)
    if (overheatHoldTime <= 0f) heat = max(0f, heat - cooling * delta)
    if (!overheatArmed && heat <= 70f) overheatArmed = true
    if (build.freeDashHeat > 0f && heat < build.freeDashHeat && freeDashReadyAt <= elapsed) freeDashPrimed = true
    if (overheated && heat <= 28f) {
        overheated = false
        message = "DASH ONLINE"
        messageTime = 1f
        emitSound(GameplayAudioCue.RECOVERED)
    }
    if (dashBufferTime.toRawBits() != 0) dashBufferTime = max(0f, dashBufferTime - delta)
    if (dashBufferTime > 0f && buildDashReady()) {
        dashBufferTime = 0f
        performDash()
    }
}

/** Furnace Covenant keeps Dash available through overheat; otherwise the usual heat rule applies. */
internal fun MutableGameState.buildDashReady(): Boolean =
    if (build.upside(RelicId.FURNACE_COVENANT)) true
    else !overheated && (heat <= MutableGameState.MAX_HEAT - effectiveDashHeatCost() * 0.5f || matrixFreeDashReady())

internal fun MutableGameState.performDash() {
    beginCharacterDash()
    dashHitThisDash = false
    val targetX = cameraX + pointerX - screenWidth * 0.5f
    val targetY = cameraY + pointerY - screenHeight * 0.5f
    val dx = targetX - coreX
    val dy = targetY - coreY
    val distance = length(dx, dy)
    val directionX = if (distance > 24f) dx / distance else lastAimDirectionX
    val directionY = if (distance > 24f) dy / distance else lastAimDirectionY
    var cost = effectiveDashHeatCost()
    if (freeDashPrimed) {
        freeDashPrimed = false
        freeDashReadyAt = elapsed + build.freeDashCooldown
        cost = 0f
    }
    if (cost > 0f && spendMatrixFreeDash()) cost = 0f
    if (!relicOnDash(directionX, directionY)) {
        val forwardVelocity = velocityX * directionX + velocityY * directionY
        if (forwardVelocity < 0f) {
            val reverseAssist = forwardVelocity * 0.72f
            velocityX -= directionX * reverseAssist
            velocityY -= directionY * reverseAssist
        }
        velocityX += directionX * effectiveDashImpulse()
        velocityY += directionY * effectiveDashImpulse()
    }
    val before = heat
    heat += cost
    if (heat > MutableGameState.MAX_HEAT && build.upside(RelicId.FURNACE_COVENANT)) {
        // Heat past full burns integrity instead of locking the Dash.
        loseIntegrity((heat - MutableGameState.MAX_HEAT) * 0.5f)
        heat = MutableGameState.MAX_HEAT
    }
    dashPhaseTime = 0.24f
    screenShake = max(screenShake, 5f)
    gainOverdrive(5f)
    shockwave(coreX, coreY, 0.3f, 112f, 0)
    directionalBurst(coreX, coreY, 18, 0, -directionX, -directionY)
    emitSound(GameplayAudioCue.DASH)
    fireClauses(EffectTrigger.DASH)
    applyOverheatEdge(before)
}

/**
 * Heat reaching full from any source: OVERHEAT clauses fire once per climb (re-armed at 70), then the Dash
 * locks unless Furnace Covenant holds. Call after every heat increase with the heat it started from.
 */
internal fun MutableGameState.applyOverheatEdge(before: Float) {
    if (before >= MutableGameState.MAX_HEAT || heat < MutableGameState.MAX_HEAT) return
    if (overheatArmed) {
        overheatArmed = false
        fireClauses(EffectTrigger.OVERHEAT)
    }
    if (!build.upside(RelicId.FURNACE_COVENANT) && !overheated) {
        heat = MutableGameState.MAX_HEAT
        overheated = true
        overheatHoldTime = 0.08f
        message = "OVERHEAT"
        messageTime = 1.4f
        emitSound(GameplayAudioCue.OVERHEAT)
    }
}

/** Brake release and other movement edges, derived inside the fixed step (inputs only set flags). */
internal fun MutableGameState.updateBuildEdges(delta: Float) {
    if (braking) {
        brakeHoldTime += delta
    } else if (wasBraking) {
        if (brakeHoldTime >= 0.35f) onBrakeRelease()
        brakeHoldTime = 0f
        brakeShedSpeed = 0f
    }
    wasBraking = braking
}

private fun MutableGameState.onBrakeRelease() {
    fireClauses(EffectTrigger.BRAKE_RELEASE)
    if (tagTier(BuildTag.ANCHOR) >= 2) {
        pushEnemies(coreX, coreY, 200f, 320f, inward = false)
        areaDamage(coreX, coreY, 200f, impactPower(max(brakeShedSpeed, 300f)) * 0.4f, rescale = false)
    }
    if (anchorBreaksOnRelease) {
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (enemy.alive && distanceSquared(enemy.x, enemy.y, coreX, coreY) <= square(220f + enemy.radius)) forceBreaks(enemy, 0.6f)
        }
    }
    relicOnBrakeRelease()
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
    val overdriveMultiplier = if (overdriveTime > 0f) 1.3f else 1f
    val pull = (CORE_BASE_PULL + distance * effectiveMagnetism()) * overdriveMultiplier
    val brakeFactor = if (braking) 1.12f else 1f
    val currentSpeed = speed
    val forwardX = if (currentSpeed > 1f) velocityX / currentSpeed else directionX
    val forwardY = if (currentSpeed > 1f) velocityY / currentSpeed else directionY
    val forwardPull = max(0f, (directionX * forwardX + directionY * forwardY) * pull)
    // Exhaustion only weakens acceleration along existing momentum. Lateral steering,
    // counter-thrust and the Brake damping remain available even at zero stability.
    val suppressedPull = if (currentSpeed > 1f) forwardPull * (1f - tetherAuthority) else 0f
    velocityX += (directionX * pull - forwardX * suppressedPull) * brakeFactor * delta
    velocityY += (directionY * pull - forwardY * suppressedPull) * brakeFactor * delta
    val perpetual = build.upside(RelicId.PERPETUAL_VECTOR)
    val brakeDamping = if (build.liability(RelicId.PERPETUAL_VECTOR)) -2.6f else -5.2f
    val drag = if (perpetual) dragCoefficient * 0.15f else dragCoefficient
    val damping = exp((if (braking) brakeDamping else -drag) * delta)
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
    updatePolarityStability(delta)
    if (braking && speed < currentSpeed) brakeShedSpeed = min(3_000f, brakeShedSpeed + (currentSpeed - speed))

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

internal fun MutableGameState.updatePolarityStability(delta: Float) {
    val tuning = content.tempo.fatigue
    val stabilityBefore = polarityStability
    val smoothing = 1f - exp(-delta / tuning.velocitySmoothingSeconds)
    smoothedVelocityX = lerp(smoothedVelocityX, velocityX, smoothing)
    smoothedVelocityY = lerp(smoothedVelocityY, velocityY, smoothing)
    turnRecoveryCooldown = max(0f, turnRecoveryCooldown - delta)
    val smoothedSpeed = length(smoothedVelocityX, smoothedVelocityY)
    // Use screen-relative reach so camera lag, momentum and magnet upgrades cannot
    // exhaust ordinary steering. Every side and corner shares the same edge band.
    val cursorReach = max(abs(pointerX / screenWidth * 2f - 1f), abs(pointerY / screenHeight * 2f - 1f))
    val edgeStrain = ((cursorReach - tuning.edgeStrainStart) / (1f - tuning.edgeStrainStart)).coerceIn(0f, 1f)
    if (edgeStrain > 0f) {
        polarityStability = max(0f, polarityStability - tuning.edgeDrainPerSecond * edgeStrain * delta)
    } else {
        val recovery = if (braking && speed <= tuning.maximumBrakeRecoverySpeed) {
            max(tuning.normalRecoveryPerSecond, tuning.brakeRecoveryPerSecond)
        } else tuning.normalRecoveryPerSecond
        if (!build.liability(RelicId.GYRE_ENGINE)) polarityStability = min(1f, polarityStability + recovery * delta)
    }
    if (speed < tuning.minimumTurnSpeed || smoothedSpeed < tuning.minimumTurnSpeed) {
        turnHeadingEstablished = false
        turnHoldTime = 0f
        turnDirection = 0
    } else {
        val headingX = smoothedVelocityX / smoothedSpeed
        val headingY = smoothedVelocityY / smoothedSpeed
        if (!turnHeadingEstablished || turnRecoveryCooldown > 0f) {
            saturationHeadingX = headingX
            saturationHeadingY = headingY
            turnHeadingEstablished = true
            turnHoldTime = 0f
            turnDirection = 0
        } else {
            val headingDot = (saturationHeadingX * headingX + saturationHeadingY * headingY).coerceIn(-1f, 1f)
            val headingCross = saturationHeadingX * headingY - saturationHeadingY * headingX
            val angle = atan2(headingCross, headingDot)
            val direction = if (angle >= 0f) 1 else -1
            if (kotlin.math.abs(angle) >= tuning.turnDegrees * TAU / 360f) {
                if (turnDirection != direction) turnHoldTime = 0f
                turnDirection = direction
                turnHoldTime += delta
                if (turnHoldTime >= tuning.turnHoldSeconds) {
                    if (edgeStrain <= 0f) {
                        val restore = if (build.liability(RelicId.GYRE_ENGINE)) 0.3f else tuning.turnRecovery
                        polarityStability = min(1f, polarityStability + restore)
                    }
                    fireClauses(EffectTrigger.TURN)
                    relicOnTurn()
                    turnRecoveryCooldown = tuning.turnCooldownSeconds
                    saturationHeadingX = headingX
                    saturationHeadingY = headingY
                    turnHoldTime = 0f
                }
            } else {
                turnHoldTime = 0f
                turnDirection = 0
            }
        }
    }
    if (stabilityBefore >= 0.35f && polarityStability < 0.35f) {
        message = "POLARITY FIELD STRAIN"
        messageTime = 1.4f
        emitSound(GameplayAudioCue.OVERHEAT)
    }
}

/** Thrust toward the singularity that does not depend on its distance. */
internal const val CORE_BASE_PULL: Float = 92f

/** The camera closes this share of its gap to the Core per second (exponentially). */
internal const val CAMERA_FOLLOW_RATE: Float = 7.5f

internal fun MutableGameState.updateCamera(delta: Float) {
    val factor = 1f - exp(-CAMERA_FOLLOW_RATE * delta)
    cameraX = lerp(cameraX, coreX, factor)
    cameraY = lerp(cameraY, coreY, factor)
}
