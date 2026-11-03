// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.flight

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kinetickk.ball.gameplay.nucleus.simulation.build.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.skills.*
import kinetickk.ball.gameplay.nucleus.simulation.variety.*
import kinetickk.ball.gameplay.nucleus.simulation.*

/** Furnace Covenant keeps Dash available through overheat; otherwise the usual heat rule applies. */
internal fun MutableGameState.buildDashReady(): Boolean =
    characterDashReady() ?: if (build.upside(RelicId.FURNACE_COVENANT)) true
    else !overheated && (heat <= MutableGameState.MAX_HEAT - effectiveDashHeatCost() * 0.5f || matrixFreeDashReady() ||
        apexFreeDashReady())

internal fun MutableGameState.performDash() {
    beginCharacterDash()
    dashHitThisDash = false
    val targetX = cameraX + pointerX - screenWidth * 0.5f
    val targetY = cameraY + pointerY - screenHeight * 0.5f
    val dx = targetX - coreX
    val dy = targetY - coreY
    val distance = length(dx, dy)
    var directionX = if (distance > 24f) dx / distance else lastAimDirectionX
    var directionY = if (distance > 24f) dy / distance else lastAimDirectionY
    val onBeat = onCharacterDashDeparture()
    var cost = effectiveDashHeatCost() * if (onBeat) BEAT_DASH_HEAT else 1f
    val crescent = coreShape == kinetickk.ball.content.api.CoreShape.CRESCENT
    // The Crescent's Blink spends a charge instead of heat.
    if (crescent) cost = 0f
    // Blast Jump: a Dash on Thunderclap's beat rides the blast for free.
    val blastJump = blastJumpUntil > elapsed && hasVerb(ReactionId.THUNDERCLAP)
    if (blastJump) {
        blastJumpUntil = 0f
        cost = 0f
    }
    // Frost Snap: a coiled Dash costs no heat and pushes harder (the Crescent's Blink reaches farther).
    val coiled = consumeCoiledDash()
    if (coiled > 1f) cost = 0f
    // Void Anchor: a Dash aimed at the anchor first blinks the Core to it and then drives into it.
    val anchor = voidAnchorAimedAt(directionX, directionY)
    if (anchor != null) {
        voidAnchorId = -1
        voidAnchorUntil = 0f
        val anchorDistance = max(1f, length(anchor.x - coreX, anchor.y - coreY))
        directionX = (anchor.x - coreX) / anchorDistance
        directionY = (anchor.y - coreY) / anchorDistance
        if (keystoneOn(ReactionId.ANNIHILATE)) {
            // Event Strike: the strike crits and costs no heat.
            anchor.status[StatusCell.MARKED_UNTIL] = max(anchor.status[StatusCell.MARKED_UNTIL], elapsed + EVENT_STRIKE_CRIT_SECONDS)
            cost = 0f
        }
        if (!crescent) voidBlink(anchor, directionX, directionY, anchorDistance, targetX, targetY)
        onMarkCashed(anchor, ReactionId.ANNIHILATE, null)
    }
    // Prime Dash: an item made this Dash free.
    if (cost > 0f && primedDashUntil > elapsed) {
        primedDashUntil = 0f
        cost = 0f
    }
    if (cost > 0f && freeDashPrimed) {
        freeDashPrimed = false
        freeDashReadyAt = elapsed + build.freeDashCooldown
        cost = 0f
    }
    if (cost > 0f && spendMatrixFreeDash()) cost = 0f
    // Vivisect: a Dash far enough after the last free one costs no heat.
    if (cost > 0f && spendApexFreeDash()) cost = 0f
    vivisectDash()
    if (crescent) {
        relicOnDashDeparture()
        when {
            anchor != null -> crescentBlink(directionX, directionY,
                rangeOverride = reactionValue(ReactionId.ANNIHILATE, VOID_ANCHOR_REACH_VALUE))
            blastJump -> crescentBlink(directionX, directionY, rangeScale = BLAST_JUMP_BLINK_RANGE * coiled)
            else -> crescentBlink(directionX, directionY, rangeScale = coiled)
        }
    } else if (!relicOnDash(directionX, directionY)) {
        val forwardVelocity = velocityX * directionX + velocityY * directionY
        if (forwardVelocity < 0f) {
            val reverseAssist = forwardVelocity * 0.72f
            velocityX -= directionX * reverseAssist
            velocityY -= directionY * reverseAssist
        }
        var impulse = effectiveDashImpulse() * characterDashImpulseMultiplier(onBeat)
        if (blastJump) impulse *= reactionValue(ReactionId.THUNDERCLAP, BLAST_JUMP_IMPULSE_VALUE)
        impulse *= coiled
        velocityX += directionX * impulse
        velocityY += directionY * impulse
    }
    val before = heat
    heat += cost
    bankDashHeat(cost)
    if (heat > MutableGameState.MAX_HEAT && build.upside(RelicId.FURNACE_COVENANT)) {
        // Heat past full burns integrity instead of locking the Dash.
        loseIntegrity((heat - MutableGameState.MAX_HEAT) * 0.5f)
        heat = MutableGameState.MAX_HEAT
    }
    dashPhaseTime = DASH_PHASE_SECONDS
    screenShake = max(screenShake, 5f)
    gainOverdrive(5f)
    shockwave(coreX, coreY, 0.3f, 112f, 0)
    directionalBurst(coreX, coreY, 18, 0, -directionX, -directionY)
    emitSound(GameplayAudioCue.DASH)
    fireClauses(EffectTrigger.DASH)
    applyOverheatEdge(before)
}

/**
 * Pushes the Core along ([dirX], [dirY]) (a unit vector) at [speed], at most [MAX_CORE_IMPULSE_SPEED]. A
 * [gated] push (a reaction's or an item's) waits for [CORE_IMPULSE_GATE_SECONDS] after the previous gated
 * one; player-timed pushes are never gated. True when the push applied.
 */
internal fun MutableGameState.applyCoreImpulse(dirX: Float, dirY: Float, speed: Float, gated: Boolean): Boolean {
    if (!(speed > 0f)) return false
    if (gated) {
        if (coreImpulseReadyAt > elapsed) return false
        coreImpulseReadyAt = elapsed + CORE_IMPULSE_GATE_SECONDS
    }
    val applied = min(speed, MAX_CORE_IMPULSE_SPEED)
    velocityX += dirX * applied
    velocityY += dirY * applied
    return true
}

/**
 * The live Void Anchor when the Dash along ([directionX], [directionY]) aims within the anchor's cone and it is
 * within reach; otherwise null.
 */
private fun MutableGameState.voidAnchorAimedAt(directionX: Float, directionY: Float): Enemy? {
    if (!hasVerb(ReactionId.ANNIHILATE)) return null
    val anchor = voidAnchor() ?: return null
    val dx = anchor.x - coreX
    val dy = anchor.y - coreY
    val distance = length(dx, dy)
    if (distance < 1f || distance > reactionValue(ReactionId.ANNIHILATE, VOID_ANCHOR_REACH_VALUE)) return null
    val cone = cos(reactionValue(ReactionId.ANNIHILATE, VOID_ANCHOR_CONE_VALUE) * RADIANS_PER_DEGREE)
    return if ((directionX * dx + directionY * dy) / distance >= cone) anchor else null
}

/**
 * Blinks the Core along ([directionX], [directionY]) to just short of [anchor] at [distance], never closer than
 * the singularity gap to the pointer at ([targetX], [targetY]) and never into or past an obstacle.
 */
private fun MutableGameState.voidBlink(anchor: Enemy, directionX: Float, directionY: Float, distance: Float, targetX: Float, targetY: Float) {
    var range = max(0f, distance - anchor.radius - coreRadius - VOID_BLINK_CLEARANCE)
    val ahead = (targetX - coreX) * directionX + (targetY - coreY) * directionY
    if (ahead > 0f) range = min(range, max(0f, length(targetX - coreX, targetY - coreY) - VOID_BLINK_SINGULARITY_GAP))
    range = blinkRangeClearOfObstacles(directionX, directionY, range)
    if (range <= 0f) return
    val startX = coreX
    val startY = coreY
    coreX += directionX * range
    coreY += directionY * range
    previousCoreX = coreX
    previousCoreY = coreY
    previousSingularityX = targetX
    previousSingularityY = targetY
    markBlink(startX, startY, coreX, coreY)
}

internal const val MAX_CORE_IMPULSE_SPEED: Float = 700f
internal const val CORE_IMPULSE_GATE_SECONDS: Float = 0.5f

internal fun MutableGameState.onBrakeRelease() {
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
    // Breakpoint: the release detonates the Cracked enemies close by.
    detonateCracked()
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
    val currentSpeed = speed
    val acceleration = steeringAcceleration(dx, dy, pointerX - screenWidth * 0.5f, pointerY - screenHeight * 0.5f, velocityX, velocityY)
    velocityX += acceleration.x * delta
    velocityY += acceleration.y * delta
    val brakeDamping = if (build.liability(RelicId.PERPETUAL_VECTOR)) -2.6f else -5.2f
    val damping = exp((if (braking) brakeDamping else -steeringDrag()) * delta)
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
    val smoothedBeforeX = smoothedVelocityX
    val smoothedBeforeY = smoothedVelocityY
    updatePolarityStability(delta)
    updateStraightness(delta, smoothedBeforeX, smoothedBeforeY)
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
        if (easyMode) boopOffSingularity(targetX, targetY) else endRun("SINGULARITY CONTACT")
    }
    previousSingularityX = targetX
    previousSingularityY = targetY
}

/**
 * Forgets how the Core has moved at a run start: the saturation heading, the smoothed velocity, the turn hold, the
 * straightness and Core Stasis (its anchor on the Core, which the start has already placed).
 */
internal fun MutableGameState.resetCoreMotionMemory() {
    saturationHeadingX = 1f
    saturationHeadingY = 0f
    smoothedVelocityX = 0f
    smoothedVelocityY = 0f
    turnHeadingEstablished = false
    turnHoldTime = 0f
    turnRecoveryCooldown = 0f
    turnDirection = 0
    coreStraightness = 0f
    coreStasis.reset(coreX, coreY)
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
                    fireTurnEdge()
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

/** A sharp turn's build edge: TURN clauses, Relic and character turn rules and the reactions' turn verbs. */
internal fun MutableGameState.fireTurnEdge() {
    fireClauses(EffectTrigger.TURN)
    relicOnTurn()
    onCharacterTurn()
    onTurnForReactions()
}

/** The camera closes this share of its gap to the Core per second (exponentially). */
internal const val CAMERA_FOLLOW_RATE: Float = 7.5f

internal fun MutableGameState.updateCamera(delta: Float) {
    val factor = 1f - exp(-CAMERA_FOLLOW_RATE * delta)
    cameraX = lerp(cameraX, coreX, factor)
    cameraY = lerp(cameraY, coreY, factor)
}

internal fun MutableGameState.movementAngle(): Float {
    if (speed > 20f) return atan2(velocityY, velocityX)
    val targetX = cameraX + pointerX - screenWidth * 0.5f
    val targetY = cameraY + pointerY - screenHeight * 0.5f
    return atan2(targetY - coreY, targetX - coreX)
}
