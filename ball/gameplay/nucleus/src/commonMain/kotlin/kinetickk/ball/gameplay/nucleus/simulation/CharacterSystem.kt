// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_DART
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_LIFE
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_PREVIOUS_X
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_PREVIOUS_Y
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_READY_AT
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_STRIDE
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_TARGET_X
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_TARGET_Y
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_X
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_Y
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_AGE
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_DETONATE_AT
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_STRIDE
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_X0
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_X1
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_Y0
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_Y1
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The twelve character mechanics. Every one reads accepted movement, combat and pickups inside the fixed
 * step; none draws randomness from the gameplay or reward streams (the Hive uses a stateless hash).
 */

// ---------------------------------------------------------------------------------------------
// Step lifecycle.

/** Ages the one-step event flags at the start of each fixed step (see [parriedThisStep], [blinkThisStep]). */
internal fun MutableGameState.ageCharacterStepFlags() {
    val ability = characterRuntime
    if (ability.parryFlagSteps > 0) ability.parryFlagSteps--
    if (ability.blinkFlagSteps > 0) ability.blinkFlagSteps--
}

/** Applies the character's base traits; called by the run reset before the Matrix and items. */
internal fun MutableGameState.applyCharacterTraits() {
    val traits = content.coreShape(coreShape).traits
    mass *= traits.massMultiplier
    maxHp += traits.integrityBonus
    dragCoefficient *= traits.dragMultiplier
    dashHeatCost *= traits.dashHeatMultiplier
    critChance += traits.critChanceBonus
    critMultiplier += traits.critDamageBonus
    pickupRadius += traits.pickupRadiusBonus
    refreshCoreRadius()
}

/** Core radius from the trait multiplier and, for the Star, Stellar Mass. */
internal fun MutableGameState.refreshCoreRadius() {
    val stacks = if (coreShape == CoreShape.STAR) characterRuntime.stellarMass else 0
    coreRadius = MutableGameState.CORE_RADIUS * content.coreShape(coreShape).traits.radiusMultiplier *
        (1f + STAR_RADIUS_PER_STACK * stacks)
}

internal fun MutableGameState.updateCharacterRuntime(delta: Float) {
    val ability = characterRuntime
    val currentSpeed = speed
    val moved = length(coreX - previousCoreX, coreY - previousCoreY)
    val heading = atan2(smoothedVelocityY, smoothedVelocityX)
    val turn = atan2(sin(heading - ability.previousHeading), cos(heading - ability.previousHeading))
    val brakingStarted = braking && !ability.wasBraking
    if (ability.parryWindow.toRawBits() != 0) ability.parryWindow = max(0f, ability.parryWindow - delta)
    if (ability.parryCooldown.toRawBits() != 0) ability.parryCooldown = max(0f, ability.parryCooldown - delta)
    if (ability.latticeTime.toRawBits() != 0) {
        ability.latticeTime = max(0f, ability.latticeTime - delta)
        if (ability.latticeTime <= 0f) ability.latticeCount = 0
    }
    if (ability.haloBurstFlash.toRawBits() != 0) ability.haloBurstFlash = max(0f, ability.haloBurstFlash - delta * FLASH_DECAY)
    if (ability.novaFlash.toRawBits() != 0) ability.novaFlash = max(0f, ability.novaFlash - delta * FLASH_DECAY)
    if (ability.polarityFlash.toRawBits() != 0) ability.polarityFlash = max(0f, ability.polarityFlash - delta * FLASH_DECAY)
    if (ability.edge > 0 && elapsed >= ability.edgeUntil) ability.edge = 0
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.characterMarkTime > 0f) enemy.characterMarkTime = max(0f, enemy.characterMarkTime - delta)
    }
    when (coreShape) {
        CoreShape.ORB -> updateOrbit(ability, turn, moved, currentSpeed)
        CoreShape.PRISM -> updateFacets(ability, delta)
        CoreShape.SHARD -> ability.charge = if (elapsed >= ability.punctureReadyAt) 1f else 0f
        CoreShape.RING -> updateHalo(ability, delta, currentSpeed, moved)
        CoreShape.DIAMOND -> {
            if (brakingStarted && ability.previousSpeed >= PARRY_MIN_SPEED && moved > 0f && ability.parryCooldown <= 0f) {
                ability.parryWindow = PARRY_WINDOW_SECONDS
                ability.parryCooldown = PARRY_COOLDOWN_SECONDS
            }
            ability.charge = if (ability.parryWindow > 0f) 1f else max(0f, 1f - ability.parryCooldown / PARRY_COOLDOWN_SECONDS)
        }
        CoreShape.TESSERACT -> updateLattice(ability)
        CoreShape.STAR -> {
            if (ability.stellarMass >= CharacterRuntime.MAX_STELLAR_MASS) supernova()
            ability.charge = ability.stellarMass / CharacterRuntime.MAX_STELLAR_MASS.toFloat()
        }
        CoreShape.SPIRAL -> updateEcho(ability, delta)
        CoreShape.DIPOLE -> updatePolarity(ability, delta)
        CoreShape.PENTAGON -> {
            if (ability.beatStreak > 0 && elapsed > (ability.streakBeat + 2) * BEAT_PERIOD + BEAT_WINDOW) ability.beatStreak = 0
            ability.charge = ability.beatStreak / CharacterRuntime.MAX_BEAT_STREAK.toFloat()
        }
        CoreShape.CRESCENT -> updateEclipse(ability, delta)
        CoreShape.HEXAGON -> updateHive(ability, delta)
    }
    ability.previousSpeed = currentSpeed
    ability.previousHeading = heading
    ability.wasBraking = braking
}

// ---------------------------------------------------------------------------------------------
// Dash, impact, hit, kill, pickup and turn hooks.

internal fun MutableGameState.beginCharacterDash() {
    val ability = characterRuntime
    ability.dashSequence++
    ability.hasDashOrigin = true
    ability.dashOriginX = coreX
    ability.dashOriginY = coreY
    ability.dashRecorded = false
}

/** A Dash's first contact with [enemy]: counts the Dash hit and marks it for the Shard. */
internal fun MutableGameState.onCharacterDashContact(enemy: Enemy) {
    if (dashPhaseTime <= 0f || enemy.lastCharacterDash == characterRuntime.dashSequence || enemy.dead || enemy.hp <= 0f) return
    enemy.lastCharacterDash = characterRuntime.dashSequence
    pendingDashHits++
    if (coreShape == CoreShape.SHARD) enemy.characterMarkTime = MARK_SECONDS
}

/**
 * An impact or a qualifying weapon hit on [enemy]: releases Orbit, and opens Shard marks unless the
 * hit is a [dash] impact.
 */
internal fun MutableGameState.onCharacterPrimaryHit(enemy: Enemy, dash: Boolean = false) {
    val ability = characterRuntime
    when (coreShape) {
        CoreShape.ORB -> if (ability.charge >= 1f) {
            ability.charge = 0f
            ability.turnArc = 0f
            ability.turnDistance = 0f
            val damage = ORBIT_DAMAGE * abilityPower()
            val dominant = build.dominant
            for (index in enemies.indices) {
                val target = enemies[index]
                if (!target.alive || distanceSquared(coreX, coreY, target.x, target.y) > square(ORBIT_RADIUS + target.radius)) continue
                dealCharacterDamage(target, damage)
            }
            // The struck enemy carries the release's element.
            if (dominant != null && enemy.alive) applyStatus(enemy, dominant, ORBIT_STACKS, 0)
            shockwave(coreX, coreY, 0.35f, ORBIT_RADIUS, 2)
        }
        CoreShape.SHARD -> if (!dash && elapsed >= ability.punctureReadyAt && consumeMark(enemy)) {
            ability.punctureReadyAt = elapsed + PUNCTURE_INTERVAL
            dealCharacterDamage(enemy, PUNCTURE_DAMAGE * abilityPower())
            heat = max(0f, heat - PUNCTURE_HEAT_REFUND)
            shockwave(enemy.x, enemy.y, 0.3f, 65f, 2)
        }
        else -> Unit
    }
}

/**
 * Damage multiplier the character adds to an impact on its way in: all facets intact, Stellar Mass,
 * an on-beat impact and the beat streak.
 */
internal fun MutableGameState.characterImpactMultiplier(): Float = when (coreShape) {
    CoreShape.PRISM -> if (characterRuntime.facets >= CharacterRuntime.MAX_FACETS) FACET_IMPACT_BONUS else 1f
    CoreShape.STAR -> 1f + STAR_IMPACT_PER_STACK * characterRuntime.stellarMass
    CoreShape.PENTAGON -> (if (isOnBeat()) BEAT_IMPACT_MULTIPLIER else 1f) * (1f + STREAK_IMPACT_PER_STACK * characterRuntime.beatStreak)
    else -> 1f
}

/** Damage multiplier on direct hits and ability damage: the Diamond's Edge. */
internal fun MutableGameState.characterDamageMultiplier(): Float =
    if (coreShape == CoreShape.DIAMOND) 1f + EDGE_DAMAGE_PER_STACK * characterRuntime.edge else 1f

/** Mass multiplier from Stellar Mass. */
internal fun MutableGameState.characterMassMultiplier(): Float =
    if (coreShape == CoreShape.STAR) 1f + STAR_MASS_PER_STACK * characterRuntime.stellarMass else 1f

/** Full Edge turns the next impact into a critical hit; the caller spends it with [spendEdgeCritical]. */
internal fun MutableGameState.edgeCriticalReady(): Boolean =
    coreShape == CoreShape.DIAMOND && characterRuntime.edge >= CharacterRuntime.MAX_EDGE

internal fun MutableGameState.spendEdgeCritical() {
    characterRuntime.edge = 0
}

/**
 * After an impact on [enemy] dealt [result] at [impactSpeed] with [multiplier] from the character:
 * heavy impacts feed Facets and Stellar Mass, on-beat impacts pulse, and the multiplier's share counts
 * as ability output.
 */
internal fun MutableGameState.onCharacterImpact(enemy: Enemy, result: DamageResult, impactSpeed: Float, multiplier: Float) {
    val ability = characterRuntime
    val heavy = result.amount >= enemy.maxHp * HEAVY_IMPACT_SHARE && impactSpeed >= HEAVY_IMPACT_SPEED
    when (coreShape) {
        CoreShape.PRISM -> if (heavy) cutFacetTimer()
        CoreShape.STAR -> {
            if (multiplier > 1f) ability.abilityDamage += impactShare(enemy, result, multiplier)
            if (heavy && enemy.alive) gainStellarMass(2)
        }
        CoreShape.DIPOLE -> if (ability.polarity == Polarity.REPEL && enemy.alive) {
            // REPEL throws what the Core strikes away from it at the impact speed.
            val knockback = knockbackTaken(enemy)
            val dx = enemy.x - coreX
            val dy = enemy.y - coreY
            val distance = length(dx, dy)
            if (knockback > 0f && distance >= 1f) {
                val throwSpeed = impactSpeed * REPEL_THROW_SHARE * knockback
                enemy.vx += dx / distance * throwSpeed
                enemy.vy += dy / distance * throwSpeed
                if (!enemy.isBossClass) enemy.characterMarkTime = THROW_SECONDS
            }
        }
        CoreShape.PENTAGON -> {
            if (multiplier > 1f) ability.abilityDamage += impactShare(enemy, result, multiplier)
            if (isOnBeat()) {
                onBeatAction()
                val beat = currentBeatIndex()
                if (ability.pulseBeat != beat) {
                    ability.pulseBeat = beat
                    val burst = BEAT_BURST_DAMAGE * abilityPower()
                    for (index in enemies.indices) {
                        val target = enemies[index]
                        if (target.alive && distanceSquared(coreX, coreY, target.x, target.y) <= square(BEAT_BURST_RADIUS + target.radius)) {
                            dealCharacterDamage(target, burst)
                        }
                    }
                    shockwave(coreX, coreY, 0.25f, BEAT_BURST_RADIUS, 2)
                }
            }
        }
        else -> Unit
    }
}

/** The share of an impact the character's [multiplier] added, up to the integrity the hit removed. */
private fun impactShare(enemy: Enemy, result: DamageResult, multiplier: Float): Double {
    val removed = min(result.amount, result.amount + enemy.hp.coerceAtMost(0f))
    return (removed * (1f - 1f / multiplier)).toDouble().coerceAtLeast(0.0)
}

/** A resolved kill: Elite kills restore a facet, kills gather Stellar Mass. */
internal fun MutableGameState.onCharacterKill(enemy: Enemy, eliteReward: Boolean) {
    val lastSource = enemy.status[StatusCell.LAST_SOURCE].toInt() - 1
    val impactKill = lastSource == HitSource.IMPACT.ordinal || lastSource == HitSource.DASH.ordinal
    val ability = characterRuntime
    when (coreShape) {
        CoreShape.PRISM -> {
            if (eliteReward && ability.facets < CharacterRuntime.MAX_FACETS) ability.facets++
            if (impactKill) cutFacetTimer()
        }
        CoreShape.STAR -> {
            gainStellarMass(if (impactKill) 2 else 1)
            if (impactKill && ability.stellarMass >= SUPERNOVA_EARLY_STACKS) supernova()
        }
        else -> Unit
    }
}

/** Real damage reached the shield or integrity: the Star sheds a quarter of its mass. */
internal fun MutableGameState.onCharacterDamaged() {
    if (coreShape != CoreShape.STAR) return
    val ability = characterRuntime
    if (ability.stellarMass <= 0) return
    ability.stellarMass -= (ability.stellarMass + 3) / 4
    refreshCoreRadius()
}

/** A Data pickup: the Hive may build a drone and refreshes the ones it has. */
internal fun MutableGameState.onCharacterDataPickup() {
    if (coreShape != CoreShape.HEXAGON) return
    val ability = characterRuntime
    for (index in 0 until ability.droneCount) ability.drones[index * DRONE_STRIDE + DRONE_LIFE] = DRONE_LIFE_SECONDS
    if (ability.droneCount >= CharacterRuntime.MAX_DRONES) return
    ability.droneRolls++
    val roll = ((characterHash(pickupsCollected + DRONE_HASH_SALT) ushr 40) and 0xFFFFL).toInt()
    if (roll >= (DRONE_CHANCE * 65_536f).toInt() && ability.droneRolls < DRONE_GUARANTEE) return
    ability.droneRolls = 0
    val base = ability.droneCount * DRONE_STRIDE
    val drones = ability.drones
    drones[base + DRONE_X] = coreX
    drones[base + DRONE_Y] = coreY
    drones[base + DRONE_PREVIOUS_X] = coreX
    drones[base + DRONE_PREVIOUS_Y] = coreY
    drones[base + DRONE_LIFE] = DRONE_LIFE_SECONDS
    drones[base + DRONE_READY_AT] = elapsed + DRONE_FIRST_DART_SECONDS
    drones[base + DRONE_DART] = 0f
    drones[base + DRONE_TARGET_X] = coreX
    drones[base + DRONE_TARGET_Y] = coreY
    ability.droneCount++
}

/** A real TURN edge: the Dipole flips polarity (at most every two seconds). */
internal fun MutableGameState.onCharacterTurn() {
    if (coreShape != CoreShape.DIPOLE) return
    val ability = characterRuntime
    if (elapsed < ability.polarityFlipReadyAt) return
    ability.polarity = if (ability.polarity == Polarity.ATTRACT) Polarity.REPEL else Polarity.ATTRACT
    ability.polarityFlipReadyAt = elapsed + POLARITY_FLIP_INTERVAL
    ability.polarityFlash = 1f
    shockwave(coreX, coreY, 0.3f, if (ability.polarity == Polarity.ATTRACT) ATTRACT_RADIUS else REPEL_RADIUS, 2)
}

/**
 * Prism facets: an incoming hit that facets may cancel spends one and is negated completely. Every hit
 * pauses the regeneration; losing the last facet pushes nearby enemies away. Returns true when negated.
 */
internal fun MutableGameState.spendFacet(amount: Float): Boolean {
    if (coreShape != CoreShape.PRISM) return false
    val ability = characterRuntime
    if (ability.facets <= 0) return false
    ability.facets--
    ability.damagePrevented += amount.toDouble()
    hurtCooldown = 0.14f
    shockwave(coreX, coreY, 0.22f, coreRadius * 3f, 2)
    if (ability.facets == 0) {
        pushEnemies(coreX, coreY, FACET_BREAK_RADIUS, FACET_BREAK_PUSH, inward = false)
        shockwave(coreX, coreY, 0.35f, FACET_BREAK_RADIUS, 2)
    }
    return true
}

/** Any incoming hit pauses facet regeneration. */
internal fun MutableGameState.pauseFacetRegeneration() {
    if (coreShape == CoreShape.PRISM) characterRuntime.facetPauseUntil = elapsed + FACET_PAUSE_SECONDS
}

private fun MutableGameState.cutFacetTimer() {
    val ability = characterRuntime
    if (ability.facets >= CharacterRuntime.MAX_FACETS || elapsed < ability.facetCutReadyAt) return
    ability.facetCutReadyAt = elapsed + FACET_CUT_INTERVAL
    ability.facetTimer += FACET_CUT_SECONDS
    if (ability.facetTimer >= FACET_REGEN_SECONDS) {
        ability.facetTimer -= FACET_REGEN_SECONDS
        ability.facets++
    }
}

/**
 * Diamond parry: an open window negates a hit approaching from ([x], [y]) with velocity ([vx], [vy]),
 * returns a hostile projectile as a shot and grants Edge. [damage] is what the hit would have dealt.
 */
internal fun MutableGameState.tryCharacterParry(
    x: Float, y: Float, vx: Float, vy: Float,
    threatenedCoreX: Float = coreX, threatenedCoreY: Float = coreY,
    damage: Float = 0f,
    projectile: Boolean = false,
): Boolean {
    val ability = characterRuntime
    if (coreShape != CoreShape.DIAMOND || ability.parryWindow <= 0f) return false
    // Collision callers pass the beginning of the swept segment: an incoming projectile
    // may already be beyond the core at the end of the same fixed step.
    if (vx * (threatenedCoreX - x) + vy * (threatenedCoreY - y) <= 0f) return false
    ability.parryWindow = 0f
    ability.parryFlagSteps = 2
    ability.damagePrevented += damage.toDouble()
    ability.edge = min(CharacterRuntime.MAX_EDGE, ability.edge + 1)
    ability.edgeUntil = elapsed + EDGE_SECONDS
    if (projectile) {
        nearestOtherEnemy(coreX, coreY, -1, PARRY_RETURN_RANGE)?.let { enemy ->
            fireRelicProjectile(coreX, coreY, enemy, PARRY_SHOT_DAMAGE * abilityPower() * characterDamageMultiplier())
        }
    }
    shockwave(coreX, coreY, 0.25f, 110f, 2)
    return true
}

/** Dash heat and impulse multipliers of the current Dash; Pentagon rewards an on-beat Dash. */
internal fun MutableGameState.onCharacterDashDeparture(): Boolean {
    if (coreShape != CoreShape.PENTAGON) {
        characterRuntime.dashOnBeat = false
        return false
    }
    val onBeat = isOnBeat()
    characterRuntime.dashOnBeat = onBeat
    if (onBeat) onBeatAction()
    return onBeat
}

/** Crescent Dashes need a Blink charge instead of heat. */
internal fun MutableGameState.characterDashReady(): Boolean? =
    if (coreShape == CoreShape.CRESCENT) characterRuntime.blinkCharges > 0 else null

// ---------------------------------------------------------------------------------------------
// Character runtime API: the read side other systems (signature weapon twists) may consult.

/** Intact Prism facets (0 for other characters). */
internal val MutableGameState.intactFacets: Int
    get() = if (coreShape == CoreShape.PRISM) characterRuntime.facets else 0

/** Current Halo radius (0 for other characters). */
internal val MutableGameState.haloRadius: Float
    get() = if (coreShape == CoreShape.RING) characterRuntime.haloRadius else 0f

/** True while a Shard mark is open on [enemy]. */
internal fun MutableGameState.isMarked(enemy: Enemy): Boolean =
    coreShape == CoreShape.SHARD && enemy.characterMarkTime > 0f

/** Opens (removes) a Shard mark; true when one was open. */
internal fun MutableGameState.consumeMark(enemy: Enemy): Boolean {
    if (!isMarked(enemy)) return false
    enemy.characterMarkTime = 0f
    return true
}

/** True from a parry until the end of the next fixed step, so weapon updates of that step can react. */
internal val MutableGameState.parriedThisStep: Boolean get() = characterRuntime.parryFlagSteps > 0

/** True for the rest of the fixed step in which the Crescent blinked. */
internal val MutableGameState.blinkThisStep: Boolean get() = characterRuntime.blinkFlagSteps > 0
internal val MutableGameState.blinkFromX: Float get() = characterRuntime.blinkFromX
internal val MutableGameState.blinkFromY: Float get() = characterRuntime.blinkFromY
internal val MutableGameState.blinkToX: Float get() = characterRuntime.blinkToX
internal val MutableGameState.blinkToY: Float get() = characterRuntime.blinkToY

/** Live Hive drones and their positions. */
internal val MutableGameState.droneCount: Int get() = if (coreShape == CoreShape.HEXAGON) characterRuntime.droneCount else 0
internal fun MutableGameState.droneX(index: Int): Float = characterRuntime.droneValue(index, DRONE_X)
internal fun MutableGameState.droneY(index: Int): Float = characterRuntime.droneValue(index, DRONE_Y)

/** Star stacks (0 for other characters). */
internal val MutableGameState.stellarMass: Int get() = if (coreShape == CoreShape.STAR) characterRuntime.stellarMass else 0

/** True once the Spiral's echo ghost has a full second of path (and the step before it) to replay. */
internal val MutableGameState.ghostActive: Boolean
    get() = coreShape == CoreShape.SPIRAL && characterRuntime.ghostSamples >= CharacterRuntime.GHOST_SAMPLES
internal val MutableGameState.ghostX: Float get() = characterRuntime.ghostSampleX(CharacterRuntime.GHOST_DELAY_STEPS)
internal val MutableGameState.ghostY: Float get() = characterRuntime.ghostSampleY(CharacterRuntime.GHOST_DELAY_STEPS)

/**
 * The ghost's position one step earlier. A replayed teleport follows the teleport rule: the previous
 * position is the arrival point, so the ghost jumps the gap without travelling (or striking) along it.
 */
internal val MutableGameState.previousGhostX: Float get() = characterRuntime.ghostSampleX(previousGhostStepsAgo())
internal val MutableGameState.previousGhostY: Float get() = characterRuntime.ghostSampleY(previousGhostStepsAgo())

private fun MutableGameState.previousGhostStepsAgo(): Int {
    val delay = CharacterRuntime.GHOST_DELAY_STEPS
    return if (characterRuntime.ghostSampleTeleported(delay)) delay else delay + 1
}

/** Ghost speed over its last fixed step (0 until it is active, and on a replayed teleport). */
internal val MutableGameState.ghostSpeed: Float
    get() = if (!ghostActive) 0f else length(ghostX - previousGhostX, ghostY - previousGhostY) / MutableGameState.FIXED_STEP

/** Live Tesseract lattice nodes. */
internal val MutableGameState.latticeNodeCount: Int get() = if (coreShape == CoreShape.TESSERACT) characterRuntime.latticeCount else 0
internal fun MutableGameState.latticeNodeX(index: Int): Float = characterRuntime.latticeX(index)
internal fun MutableGameState.latticeNodeY(index: Int): Float = characterRuntime.latticeY(index)

/** Dipole polarity. */
internal val MutableGameState.polarity: Polarity get() = characterRuntime.polarity

/** True while a REPEL throw carries [enemy]: it flies on its momentum instead of steering (never a boss). */
internal fun MutableGameState.isThrownByPolarity(enemy: Enemy): Boolean =
    coreShape == CoreShape.DIPOLE && enemy.characterMarkTime > 0f && !enemy.isBossClass

/** True when Orbit is fully charged and the next impact or weapon hit releases it. */
internal val MutableGameState.orbitCharged: Boolean get() = coreShape == CoreShape.ORB && characterRuntime.charge >= 1f

/** Seconds from the current time to the nearest Pentagon beat. */
internal fun MutableGameState.beatOffset(): Float {
    val phase = elapsed % BEAT_PERIOD
    return min(phase, BEAT_PERIOD - phase)
}

internal fun MutableGameState.currentBeatIndex(): Int = (elapsed / BEAT_PERIOD).roundToInt()

/** True within the on-beat window of a Pentagon beat (never for other characters, never before the first beat). */
internal fun MutableGameState.isOnBeat(): Boolean =
    coreShape == CoreShape.PENTAGON && elapsed >= BEAT_PERIOD - BEAT_WINDOW && beatOffset() <= BEAT_WINDOW

/** Weapon activation multiplier from the character: on-beat activations of the Pentagon. `cooldown()` applies it. */
internal fun MutableGameState.characterActivationMultiplier(): Float = if (isOnBeat()) BEAT_ACTIVATION_MULTIPLIER else 1f

// ---------------------------------------------------------------------------------------------
// Mechanics.

private fun MutableGameState.updateOrbit(ability: CharacterRuntime, turn: Float, moved: Float, currentSpeed: Float) {
    if (!braking && currentSpeed >= ORBIT_MIN_SPEED && ability.previousSpeed >= ORBIT_MIN_SPEED && moved > 0f) {
        val sameDirection = ability.turnArc == 0f || turn * ability.turnArc >= 0f
        val arc = if (sameDirection) ability.turnArc + turn else turn
        val distance = if (sameDirection) ability.turnDistance + moved else moved
        val earned = if (abs(arc) >= ORBIT_ARC && distance >= ORBIT_MIN_DISTANCE) abs(turn) / PI.toFloat() else 0f
        ability.turnArc = arc.coerceIn(-TAU, TAU)
        ability.turnDistance = min(2_000f, distance)
        ability.charge = min(1f, ability.charge + earned)
    } else {
        ability.turnArc = 0f
        ability.turnDistance = 0f
    }
}

private fun MutableGameState.updateFacets(ability: CharacterRuntime, delta: Float) {
    if (ability.facets < CharacterRuntime.MAX_FACETS) {
        if (elapsed >= ability.facetPauseUntil) ability.facetTimer += delta
        if (ability.facetTimer >= FACET_REGEN_SECONDS) {
            ability.facetTimer -= FACET_REGEN_SECONDS
            ability.facets++
        }
    }
    if (ability.facets >= CharacterRuntime.MAX_FACETS) ability.facetTimer = 0f
    ability.charge = ability.facets / CharacterRuntime.MAX_FACETS.toFloat()
}

private fun MutableGameState.updateHalo(ability: CharacterRuntime, delta: Float, currentSpeed: Float, moved: Float) {
    val target = CharacterRuntime.HALO_MIN_RADIUS + clamp(currentSpeed / HALO_SPEED_SPAN, 0f, 1f) * HALO_EXTRA_RADIUS
    if (ability.previousSpeed < HALO_BURST_SPEED && currentSpeed >= HALO_BURST_SPEED && elapsed >= ability.haloBurstReadyAt) {
        // The ring snaps out to the burst radius and strikes every enemy it crosses on the way.
        val inner = ability.haloRadius
        val damage = HALO_BURST_DAMAGE * abilityPower()
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (!enemy.alive) continue
            val distance = length(enemy.x - coreX, enemy.y - coreY)
            if (distance + enemy.radius >= inner && distance - enemy.radius <= HALO_BURST_RADIUS) dealCharacterDamage(enemy, damage)
        }
        ability.haloRadius = HALO_BURST_RADIUS
        ability.haloBurstReadyAt = elapsed + HALO_BURST_COOLDOWN
        ability.haloBurstFlash = 1f
        shockwave(coreX, coreY, 0.35f, HALO_BURST_RADIUS, 2)
    } else {
        ability.haloRadius += (target - ability.haloRadius) * min(1f, delta * HALO_EASE)
    }
    val radius = ability.haloRadius
    ability.charge = ((radius - CharacterRuntime.HALO_MIN_RADIUS) / HALO_EXTRA_RADIUS).coerceIn(0f, 1f)
    if (moved <= 0.01f || currentSpeed < HALO_MIN_SPEED) return
    val damage = HALO_BAND_DPS * delta * abilityPower()
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive) continue
        val distance = length(enemy.x - coreX, enemy.y - coreY)
        if (distance >= max(36f, radius - HALO_BAND) && distance <= radius + HALO_BAND + enemy.radius) dealCharacterDamage(enemy, damage)
    }
}

private fun MutableGameState.updateLattice(ability: CharacterRuntime) {
    if (dashPhaseTime > 0f && ability.hasDashOrigin && !ability.dashRecorded &&
        distanceSquared(ability.dashOriginX, ability.dashOriginY, coreX, coreY) >= square(LATTICE_RECORD_DISTANCE)
    ) {
        val originX = ability.dashOriginX
        val originY = ability.dashOriginY
        val count = ability.latticeCount
        ability.dashRecorded = true
        if (count >= 3 && distanceSquared(originX, originY, ability.latticeX(0), ability.latticeY(0)) < square(LATTICE_CLOSE_DISTANCE)) {
            collapseLattice(count)
        } else {
            var separated = true
            for (index in 0 until count) {
                if (distanceSquared(originX, originY, ability.latticeX(index), ability.latticeY(index)) < square(LATTICE_SPACING)) {
                    separated = false
                    break
                }
            }
            if (separated) {
                ability.latticeNodes[count * 2] = originX
                ability.latticeNodes[count * 2 + 1] = originY
                ability.latticeCount = count + 1
                if (ability.latticeCount >= CharacterRuntime.MAX_LATTICE_NODES) collapseLattice(ability.latticeCount)
                else ability.latticeTime = LATTICE_LIFE_SECONDS
            }
        }
    }
    ability.charge = ability.latticeCount / CharacterRuntime.MAX_LATTICE_NODES.toFloat()
}

/** Strikes every enemy touching a lattice edge; enemies inside the closed polygon take half again. */
private fun MutableGameState.collapseLattice(count: Int) {
    val ability = characterRuntime
    ability.latticeCount = 0
    ability.latticeTime = 0f
    if (count < 2) return
    val damage = (LATTICE_BASE_DAMAGE + count * LATTICE_NODE_DAMAGE) * abilityPower()
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive) continue
        var touches = false
        for (index in 0 until count) {
            val next = (index + 1) % count
            if (segmentCircleIntersects(ability.latticeX(index), ability.latticeY(index), ability.latticeX(next), ability.latticeY(next),
                    enemy.x, enemy.y, enemy.radius)
            ) {
                touches = true
                break
            }
        }
        val inside = count >= 3 && insideLattice(ability, count, enemy.x, enemy.y)
        if (touches || inside) dealCharacterDamage(enemy, damage * if (inside) LATTICE_INSIDE_MULTIPLIER else 1f)
    }
    for (index in 0 until count) {
        val next = (index + 1) % count
        addWeaponArc(ability.latticeX(index), ability.latticeY(index), ability.latticeX(next), ability.latticeY(next), 0.35f)
    }
}

/** Even-odd point-in-polygon test over the first [count] lattice nodes. */
private fun insideLattice(ability: CharacterRuntime, count: Int, x: Float, y: Float): Boolean {
    var inside = false
    var previous = count - 1
    for (index in 0 until count) {
        val ax = ability.latticeX(index)
        val ay = ability.latticeY(index)
        val bx = ability.latticeX(previous)
        val by = ability.latticeY(previous)
        if ((ay > y) != (by > y) && x < (bx - ax) * (y - ay) / (by - ay) + ax) inside = !inside
        previous = index
    }
    return inside
}

private fun MutableGameState.gainStellarMass(stacks: Int) {
    val ability = characterRuntime
    val next = min(CharacterRuntime.MAX_STELLAR_MASS, ability.stellarMass + stacks)
    if (next == ability.stellarMass) return
    ability.stellarMass = next
    refreshCoreRadius()
}

/** Consumes all Stellar Mass into a nova whose radius, damage and element stacks grow with it. */
private fun MutableGameState.supernova() {
    val ability = characterRuntime
    val stacks = ability.stellarMass
    if (stacks <= 0 || elapsed < ability.supernovaReadyAt) return
    val radius = SUPERNOVA_RADIUS + SUPERNOVA_RADIUS_PER_STACK * stacks
    val damage = (SUPERNOVA_DAMAGE + SUPERNOVA_DAMAGE_PER_STACK * stacks) * abilityPower()
    val dominant = build.dominant
    val elementStacks = stacks / SUPERNOVA_STACKS_PER_ELEMENT
    ability.stellarMass = 0
    ability.supernovaReadyAt = elapsed + SUPERNOVA_COOLDOWN
    ability.novaFlash = 1f
    refreshCoreRadius()
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || distanceSquared(coreX, coreY, enemy.x, enemy.y) > square(radius + enemy.radius)) continue
        dealCharacterDamage(enemy, damage)
        if (dominant != null && elementStacks > 0 && enemy.alive) applyStatus(enemy, dominant, elementStacks, 0)
    }
    shockwave(coreX, coreY, 0.5f, radius, 3)
    screenShake = max(screenShake, 8f)
    emitSound(GameplayAudioCue.OVERDRIVE)
}

private fun MutableGameState.updateEcho(ability: CharacterRuntime, delta: Float) {
    // A teleport (Blink Drive or any other) sets the previous position to the arrival point on its
    // step: a gap from the last sample that this step did not travel is flagged, never replayed as motion.
    val teleported = ability.ghostSamples > 0 && previousCoreX == coreX && previousCoreY == coreY &&
        (ability.ghostSampleX(0) != coreX || ability.ghostSampleY(0) != coreY)
    ability.recordGhostSample(coreX, coreY, teleported)
    if (ability.ghostSamples < CharacterRuntime.GHOST_SAMPLES) {
        ability.charge = ability.ghostSamples / CharacterRuntime.GHOST_SAMPLES.toFloat()
        return
    }
    val x = ghostX
    val y = ghostY
    val fromX = previousGhostX
    val fromY = previousGhostY
    val speedNow = if (delta > 0f) length(x - fromX, y - fromY) / delta else 0f
    val reach = coreRadius
    if (elapsed >= ability.ghostAbsorbReadyAt) {
        for (index in projectiles.indices) {
            val shot = projectiles[index]
            if (!shot.hostile || shot.spent || shot.kind != kinetickk.ball.gameplay.nucleus.render.ProjectileKind.BOLT) continue
            if (segmentCircleIntersects(shot.previousX - fromX, shot.previousY - fromY, shot.x - x, shot.y - y, 0f, 0f, reach + shot.radius)) {
                // Spent, not removed: the collision pass removes spent shots once no pass walks the list.
                shot.life = 0f
                ability.ghostAbsorbReadyAt = elapsed + GHOST_ABSORB_INTERVAL
                ability.damagePrevented += shot.damage.toDouble()
                burst(x, y, 5, 2)
                break
            }
        }
    }
    ability.charge = if (elapsed >= ability.ghostAbsorbReadyAt) 1f else 0.5f
    if (speedNow < GHOST_MIN_SPEED) return
    val damage = impactPower(speedNow) * GHOST_IMPACT_SHARE
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || enemy.characterMarkTime > 0f || isShifting(enemy)) continue
        if (segmentCircleIntersects(fromX - enemy.previousX, fromY - enemy.previousY, x - enemy.x, y - enemy.y, 0f, 0f,
                reach + enemy.radius)
        ) {
            enemy.characterMarkTime = GHOST_HIT_INTERVAL
            val integrity = enemy.hp
            recordAbilityDamage(integrity, dealProcDamage(enemy, damage))
            directionalBurst(enemy.x, enemy.y, 5, 2, x - fromX, y - fromY)
        }
    }
}

private fun MutableGameState.updatePolarity(ability: CharacterRuntime, delta: Float) {
    val attract = ability.polarity == Polarity.ATTRACT
    val radius = if (attract) ATTRACT_RADIUS else REPEL_RADIUS
    val acceleration = (if (attract) -ATTRACT_ACCELERATION else REPEL_ACCELERATION) * delta
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive) continue
        val dx = enemy.x - coreX
        val dy = enemy.y - coreY
        val distance = length(dx, dy)
        if (distance < 1f || distance > radius + enemy.radius) continue
        val knockback = knockbackTaken(enemy)
        if (knockback <= 0f) continue
        enemy.vx += dx / distance * acceleration * knockback
        enemy.vy += dy / distance * acceleration * knockback
        // ATTRACT only adds its pull to the enemy's own steering. REPEL throws what it pushes: the
        // enemy flies on its momentum (no steering) until THROW_SECONDS after the push carries it out
        // of the field, and what it strikes meanwhile is a slam. Bosses and mini-bosses are never thrown.
        if (!attract && !enemy.isBossClass) enemy.characterMarkTime = THROW_SECONDS
    }
    ability.charge = if (elapsed >= ability.polarityFlipReadyAt) 1f else
        1f - ((ability.polarityFlipReadyAt - elapsed) / POLARITY_FLIP_INTERVAL).coerceIn(0f, 1f)
    var slams = 0
    for (index in enemies.indices) {
        if (slams >= MAX_SLAMS_PER_STEP) break
        val thrown = enemies[index]
        if (!thrown.alive || thrown.characterMarkTime <= 0f) continue
        val thrownSpeed = length(thrown.vx, thrown.vy)
        if (thrownSpeed >= SLAM_MIN_SPEED &&
            obstacleBlocksSegment(thrown.previousX, thrown.previousY, thrown.x, thrown.y, thrown.radius)
        ) {
            thrown.characterMarkTime = 0f
            dipoleSlam(thrown, thrownSpeed)
            slams++
            continue
        }
        for (otherIndex in enemies.indices) {
            if (otherIndex == index) continue
            val other = enemies[otherIndex]
            if (!other.alive) continue
            val dx = other.x - thrown.x
            val dy = other.y - thrown.y
            val distance = length(dx, dy)
            if (distance < 0.5f || distance > thrown.radius + other.radius) continue
            val closing = ((thrown.vx - other.vx) * dx + (thrown.vy - other.vy) * dy) / distance
            if (closing < SLAM_MIN_SPEED) continue
            thrown.characterMarkTime = 0f
            // The pair shares the closing momentum, so one contact slams once. An enemy struck hard
            // enough flies on as a throw of its own and may slam the next one (never a boss).
            val share = closing * 0.5f
            thrown.vx -= dx / distance * share
            thrown.vy -= dy / distance * share
            other.vx += dx / distance * share
            other.vy += dy / distance * share
            if (share >= THROW_MIN_SPEED && !other.isBossClass) other.characterMarkTime = THROW_SECONDS
            dipoleSlam(thrown, closing)
            dipoleSlam(other, closing)
            slams++
            break
        }
    }
}

/** A Dipole slam: the polarity damage, then the shared slam rule (SLAM trigger, obstacle slam damage). */
private fun MutableGameState.dipoleSlam(enemy: Enemy, speed: Float) {
    dealCharacterDamage(enemy, (SLAM_BASE_DAMAGE + SLAM_DAMAGE_PER_SPEED * speed) * abilityPower())
    applySlamDamage(enemy, speed)
    burst(enemy.x, enemy.y, 6, 3)
}

/** Records an on-beat action: the streak rises once per beat. */
private fun MutableGameState.onBeatAction() {
    val ability = characterRuntime
    val beat = currentBeatIndex()
    if (ability.streakBeat == beat) return
    ability.streakBeat = beat
    ability.beatStreak = min(CharacterRuntime.MAX_BEAT_STREAK, ability.beatStreak + 1)
}

private fun MutableGameState.updateEclipse(ability: CharacterRuntime, delta: Float) {
    if (ability.blinkCharges < CharacterRuntime.MAX_BLINK_CHARGES) {
        ability.blinkRecharge += delta
        if (ability.blinkRecharge >= BLINK_RECHARGE_SECONDS) {
            ability.blinkRecharge -= BLINK_RECHARGE_SECONDS
            ability.blinkCharges++
        }
    }
    if (ability.blinkCharges >= CharacterRuntime.MAX_BLINK_CHARGES) ability.blinkRecharge = 0f
    ability.charge = (ability.blinkCharges + ability.blinkRecharge / BLINK_RECHARGE_SECONDS) / CharacterRuntime.MAX_BLINK_CHARGES
    var index = 0
    while (index < ability.scarCount) {
        val age = ability.scarValue(index, SCAR_AGE)
        if (age < 0f) {
            if (elapsed >= ability.scarValue(index, SCAR_DETONATE_AT)) detonateScar(index)
            index++
        } else if (age + delta >= SCAR_FADE_SECONDS) {
            removeScar(index)
        } else {
            ability.scars[index * SCAR_STRIDE + SCAR_AGE] = age + delta
            index++
        }
    }
}

/**
 * Crescent's Dash: a Blink along the aim that stops short of the singularity and of obstacles, keeps
 * most of the speed, costs a charge instead of heat, counts enemies it crosses as Dash hits and leaves
 * a void scar. Blink Drive extends it and adds its strike and pairings.
 */
internal fun MutableGameState.crescentBlink(directionX: Float, directionY: Float) {
    val ability = characterRuntime
    ability.blinkCharges = max(0, ability.blinkCharges - 1)
    dashPhaseTime = DASH_PHASE_SECONDS
    val drive = build.upside(RelicId.BLINK_DRIVE)
    var range = BLINK_RANGE
    var strikeMass = 1f
    if (drive) {
        range *= BLINK_DRIVE_RANGE_MULTIPLIER
        if (build.pairing(RelicPairingId.HEAVY_BLINK)) {
            range *= 0.65f
            strikeMass = 2f
        }
    }
    val targetX = cameraX + pointerX - screenWidth * 0.5f
    val targetY = cameraY + pointerY - screenHeight * 0.5f
    val ahead = (targetX - coreX) * directionX + (targetY - coreY) * directionY
    if (ahead > 0f) range = min(range, max(0f, length(targetX - coreX, targetY - coreY) - BLINK_SINGULARITY_GAP))
    while (range > 0f && obstacleBlocksSegment(coreX, coreY, coreX + directionX * range, coreY + directionY * range, coreRadius)) {
        range = max(0f, range - BLINK_OBSTACLE_STEP)
    }
    val startX = coreX
    val startY = coreY
    val endX = coreX + directionX * range
    val endY = coreY + directionY * range
    val impact = impactPower(max(speed, 600f))
    val strike = if (drive) impact * strikeMass * relicValue(RelicId.BLINK_DRIVE, 0) / 100f else 0f
    val assassin = drive && build.pairing(RelicPairingId.ASSASSINS_LINE)
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (!enemy.alive || isShifting(enemy)) continue
        if (!segmentCircleIntersects(startX, startY, endX, endY, enemy.x, enemy.y, enemy.radius + coreRadius)) continue
        val newContact = enemy.lastCharacterDash != ability.dashSequence
        onCharacterDashContact(enemy)
        if (newContact) onDashHit(enemy, impact)
        if (drive && enemy.alive) {
            if (assassin) enemy.status[StatusCell.MARKED_UNTIL] = elapsed + 1f
            dealHit(enemy, strike, HitSource.DASH, canCrit = true)
            if (assassin) {
                enemy.status[StatusCell.MARKED_UNTIL] = 0f
                if (enemy.alive) enemy.status[StatusCell.BRITTLE] = 1f
            }
        }
    }
    if (drive) addRelicArc(startX, startY, endX, endY)
    coreX = endX
    coreY = endY
    previousCoreX = endX
    previousCoreY = endY
    previousSingularityX = targetX
    previousSingularityY = targetY
    velocityX *= BLINK_SPEED_RETENTION
    velocityY *= BLINK_SPEED_RETENTION
    ability.blinkFlagSteps = 1
    ability.blinkFromX = startX
    ability.blinkFromY = startY
    ability.blinkToX = endX
    ability.blinkToY = endY
    if (ability.scarCount >= CharacterRuntime.MAX_SCARS) {
        // The oldest scar detonates early rather than being dropped.
        if (ability.scarValue(0, SCAR_AGE) < 0f) detonateScar(0)
        removeScar(0)
    }
    val base = ability.scarCount * SCAR_STRIDE
    ability.scars[base + SCAR_X0] = startX
    ability.scars[base + SCAR_Y0] = startY
    ability.scars[base + SCAR_X1] = endX
    ability.scars[base + SCAR_Y1] = endY
    ability.scars[base + SCAR_DETONATE_AT] = elapsed + SCAR_FUSE_SECONDS
    ability.scars[base + SCAR_AGE] = -1f
    ability.scarCount++
    shockwave(endX, endY, 0.25f, 110f, 0)
}

private fun MutableGameState.detonateScar(index: Int) {
    val ability = characterRuntime
    val x0 = ability.scarValue(index, SCAR_X0)
    val y0 = ability.scarValue(index, SCAR_Y0)
    val x1 = ability.scarValue(index, SCAR_X1)
    val y1 = ability.scarValue(index, SCAR_Y1)
    ability.scars[index * SCAR_STRIDE + SCAR_AGE] = 0f
    val damage = SCAR_DAMAGE * abilityPower()
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || !segmentCircleIntersects(x0, y0, x1, y1, enemy.x, enemy.y, enemy.radius + SCAR_REACH)) continue
        dealCharacterDamage(enemy, damage)
        if (enemy.alive) applyStatus(enemy, StatusElement.VOID, 1, 0)
    }
    addWeaponArc(x0, y0, x1, y1, 0.3f)
}

private fun MutableGameState.removeScar(index: Int) {
    val ability = characterRuntime
    val last = ability.scarCount - 1
    for (slot in index until last) {
        for (field in 0 until SCAR_STRIDE) {
            ability.scars[slot * SCAR_STRIDE + field] = ability.scars[(slot + 1) * SCAR_STRIDE + field]
        }
    }
    ability.scarCount = last
}

private fun MutableGameState.updateHive(ability: CharacterRuntime, delta: Float) {
    var index = 0
    while (index < ability.droneCount) {
        val base = index * DRONE_STRIDE
        val life = ability.drones[base + DRONE_LIFE] - delta
        if (life <= 0f) {
            val last = ability.droneCount - 1
            for (field in 0 until DRONE_STRIDE) ability.drones[base + field] = ability.drones[last * DRONE_STRIDE + field]
            ability.droneCount = last
            continue
        }
        ability.drones[base + DRONE_LIFE] = life
        index++
    }
    val count = ability.droneCount
    ability.charge = count / CharacterRuntime.MAX_DRONES.toFloat()
    for (drone in 0 until count) {
        val base = drone * DRONE_STRIDE
        val drones = ability.drones
        drones[base + DRONE_PREVIOUS_X] = drones[base + DRONE_X]
        drones[base + DRONE_PREVIOUS_Y] = drones[base + DRONE_Y]
        val angle = elapsed * DRONE_ORBIT_SPEED + drone * TAU / count
        val orbitX = coreX + cos(angle) * DRONE_ORBIT_RADIUS
        val orbitY = coreY + sin(angle) * DRONE_ORBIT_RADIUS
        var dart = drones[base + DRONE_DART]
        if (dart <= 0f && elapsed >= drones[base + DRONE_READY_AT]) {
            val target = nearestEnemy(orbitX, orbitY, DRONE_RANGE)
            if (target != null) {
                drones[base + DRONE_READY_AT] = elapsed + DRONE_INTERVAL
                drones[base + DRONE_TARGET_X] = target.x
                drones[base + DRONE_TARGET_Y] = target.y
                dart = DRONE_DART_SECONDS
                val integrity = target.hp
                recordAbilityDamage(integrity, dealProcDamage(target, DRONE_DAMAGE * abilityPower()))
                if (!target.alive && elapsed >= ability.droneHealReadyAt) {
                    ability.droneHealReadyAt = elapsed + DRONE_HEAL_INTERVAL
                    heal(DRONE_KILL_HEAL, HealSource.ITEM)
                }
            }
        }
        if (dart > 0f) {
            dart = max(0f, dart - delta)
            val progress = 1f - dart / DRONE_DART_SECONDS
            val reach = 1f - abs(progress * 2f - 1f)
            drones[base + DRONE_X] = lerp(orbitX, drones[base + DRONE_TARGET_X], reach)
            drones[base + DRONE_Y] = lerp(orbitY, drones[base + DRONE_TARGET_Y], reach)
        } else {
            drones[base + DRONE_X] = orbitX
            drones[base + DRONE_Y] = orbitY
        }
        drones[base + DRONE_DART] = dart
    }
}

/** Shifts every stored character position after a world rebase. */
internal fun MutableGameState.rebaseCharacterRuntime(shiftX: Float, shiftY: Float) {
    val ability = characterRuntime
    ability.dashOriginX -= shiftX
    ability.dashOriginY -= shiftY
    ability.blinkFromX -= shiftX
    ability.blinkFromY -= shiftY
    ability.blinkToX -= shiftX
    ability.blinkToY -= shiftY
    for (index in 0 until ability.latticeCount) {
        ability.latticeNodes[index * 2] = ability.latticeNodes[index * 2] - shiftX
        ability.latticeNodes[index * 2 + 1] = ability.latticeNodes[index * 2 + 1] - shiftY
    }
    if (ability.ghostSamples > 0) {
        val path = ability.ghostPath
        for (index in 0 until CharacterRuntime.GHOST_SAMPLES) {
            val base = index * CharacterRuntime.GHOST_STRIDE
            path[base + CharacterRuntime.GHOST_X] = path[base + CharacterRuntime.GHOST_X] - shiftX
            path[base + CharacterRuntime.GHOST_Y] = path[base + CharacterRuntime.GHOST_Y] - shiftY
        }
    }
    val drones = ability.drones
    for (index in 0 until ability.droneCount) {
        val base = index * DRONE_STRIDE
        drones[base + DRONE_X] = drones[base + DRONE_X] - shiftX
        drones[base + DRONE_Y] = drones[base + DRONE_Y] - shiftY
        drones[base + DRONE_PREVIOUS_X] = drones[base + DRONE_PREVIOUS_X] - shiftX
        drones[base + DRONE_PREVIOUS_Y] = drones[base + DRONE_PREVIOUS_Y] - shiftY
        drones[base + DRONE_TARGET_X] = drones[base + DRONE_TARGET_X] - shiftX
        drones[base + DRONE_TARGET_Y] = drones[base + DRONE_TARGET_Y] - shiftY
    }
    for (index in 0 until ability.scarCount) {
        val base = index * SCAR_STRIDE
        ability.scars[base + SCAR_X0] = ability.scars[base + SCAR_X0] - shiftX
        ability.scars[base + SCAR_Y0] = ability.scars[base + SCAR_Y0] - shiftY
        ability.scars[base + SCAR_X1] = ability.scars[base + SCAR_X1] - shiftX
        ability.scars[base + SCAR_Y1] = ability.scars[base + SCAR_Y1] - shiftY
    }
}

/** SplitMix64 finalizer: a stateless hash for the Hive's drone roll, so no gameplay stream is drawn. */
private fun characterHash(value: Long): Long {
    var z = value + -0x61c8864680b583ebL
    z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
    z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
    return z xor (z ushr 31)
}

internal const val DASH_PHASE_SECONDS: Float = 0.24f
private const val FLASH_DECAY: Float = 2.5f

// Ability damage is calibrated by CharacterBalanceTest: averaged over its seeded 90 s fights (packs at
// random bearings, content bolt damage, exact and jittered timing) every offensive character's ability
// output stays within 40 % of their median (Orbit 56, Puncture 180, Halo 12/s and 16, Lattice 7 + 3.5
// per node, slams 120 + 0.25 per u/s from 100 u/s, Pulse burst 40, scars 45, drones 9).

private const val ORBIT_ARC: Float = 0.698f
private const val ORBIT_MIN_DISTANCE: Float = 100f
private const val ORBIT_MIN_SPEED: Float = 200f
internal const val ORBIT_RADIUS: Float = 210f
internal const val ORBIT_DAMAGE: Float = 70f
private const val ORBIT_STACKS: Int = 2

internal const val FACET_REGEN_SECONDS: Float = 6f
internal const val FACET_PAUSE_SECONDS: Float = 2f
private const val FACET_CUT_SECONDS: Float = 1f
private const val FACET_CUT_INTERVAL: Float = 1.2f
internal const val FACET_IMPACT_BONUS: Float = 1.2f
private const val FACET_BREAK_RADIUS: Float = 180f
private const val FACET_BREAK_PUSH: Float = 520f
private const val HEAVY_IMPACT_SHARE: Float = 0.25f
private const val HEAVY_IMPACT_SPEED: Float = 450f

private const val MARK_SECONDS: Float = 3f
internal const val PUNCTURE_DAMAGE: Float = 180f
internal const val PUNCTURE_HEAT_REFUND: Float = 12f
private const val PUNCTURE_INTERVAL: Float = 0.25f

private const val HALO_SPEED_SPAN: Float = 1_200f
internal const val HALO_EXTRA_RADIUS: Float = 110f
private const val HALO_BAND: Float = 18f
internal const val HALO_BAND_DPS: Float = 12f
private const val HALO_MIN_SPEED: Float = 40f
private const val HALO_EASE: Float = 6f
internal const val HALO_BURST_SPEED: Float = 1_200f
internal const val HALO_BURST_RADIUS: Float = 260f
internal const val HALO_BURST_DAMAGE: Float = 16f
private const val HALO_BURST_COOLDOWN: Float = 1.2f

private const val PARRY_MIN_SPEED: Float = 200f
internal const val PARRY_WINDOW_SECONDS: Float = 0.18f
internal const val PARRY_COOLDOWN_SECONDS: Float = 1.2f
private const val PARRY_RETURN_RANGE: Float = 800f
internal const val PARRY_SHOT_DAMAGE: Float = 65f
internal const val EDGE_DAMAGE_PER_STACK: Float = 0.08f
private const val EDGE_SECONDS: Float = 6f

private const val LATTICE_RECORD_DISTANCE: Float = 120f
private const val LATTICE_CLOSE_DISTANCE: Float = 130f
private const val LATTICE_SPACING: Float = 150f
private const val LATTICE_LIFE_SECONDS: Float = 7f
internal const val LATTICE_BASE_DAMAGE: Float = 7f
internal const val LATTICE_NODE_DAMAGE: Float = 3.5f
internal const val LATTICE_INSIDE_MULTIPLIER: Float = 1.5f

private const val STAR_IMPACT_PER_STACK: Float = 0.03f
private const val STAR_RADIUS_PER_STACK: Float = 0.015f
private const val STAR_MASS_PER_STACK: Float = 0.02f
private const val SUPERNOVA_EARLY_STACKS: Int = 12
private const val SUPERNOVA_RADIUS: Float = 160f
private const val SUPERNOVA_RADIUS_PER_STACK: Float = 8f
internal const val SUPERNOVA_DAMAGE: Float = 20f
internal const val SUPERNOVA_DAMAGE_PER_STACK: Float = 9f
private const val SUPERNOVA_STACKS_PER_ELEMENT: Int = 4
private const val SUPERNOVA_COOLDOWN: Float = 2f

private const val GHOST_MIN_SPEED: Float = 300f
internal const val GHOST_IMPACT_SHARE: Float = 0.32f
private const val GHOST_HIT_INTERVAL: Float = 0.4f
private const val GHOST_ABSORB_INTERVAL: Float = 1.5f

private const val POLARITY_FLIP_INTERVAL: Float = 2f
internal const val ATTRACT_RADIUS: Float = 300f
private const val ATTRACT_ACCELERATION: Float = 260f
internal const val REPEL_RADIUS: Float = 220f
private const val REPEL_ACCELERATION: Float = 420f
internal const val THROW_SECONDS: Float = 0.6f
private const val REPEL_THROW_SHARE: Float = 1f
internal const val SLAM_MIN_SPEED: Float = 100f
/** A struck enemy that gains this speed from a slam is thrown on (as fast as a slam needs). */
internal const val THROW_MIN_SPEED: Float = SLAM_MIN_SPEED
internal const val SLAM_BASE_DAMAGE: Float = 120f
internal const val SLAM_DAMAGE_PER_SPEED: Float = 0.25f
private const val MAX_SLAMS_PER_STEP: Int = 8

internal const val BEAT_PERIOD: Float = 0.75f
internal const val BEAT_WINDOW: Float = 0.11f
/** An on-beat Dash costs this share of the usual heat (40 % less) and pushes [BEAT_DASH_IMPULSE] harder. */
internal const val BEAT_DASH_HEAT: Float = 0.6f
internal const val BEAT_DASH_IMPULSE: Float = 1.2f
private const val BEAT_IMPACT_MULTIPLIER: Float = 1.6f
private const val BEAT_BURST_RADIUS: Float = 80f
internal const val BEAT_BURST_DAMAGE: Float = 40f
internal const val BEAT_ACTIVATION_MULTIPLIER: Float = 1.4f
private const val STREAK_IMPACT_PER_STACK: Float = 0.06f

internal const val BLINK_RANGE: Float = 240f
private const val BLINK_SINGULARITY_GAP: Float = 60f
private const val BLINK_OBSTACLE_STEP: Float = 20f
internal const val BLINK_SPEED_RETENTION: Float = 0.8f
internal const val BLINK_RECHARGE_SECONDS: Float = 2.2f
private const val BLINK_DRIVE_RANGE_MULTIPLIER: Float = 1.4f
internal const val SCAR_FUSE_SECONDS: Float = 0.4f
private const val SCAR_FADE_SECONDS: Float = 0.3f
internal const val SCAR_DAMAGE: Float = 45f
private const val SCAR_REACH: Float = 40f

private const val DRONE_CHANCE: Float = 0.25f
private const val DRONE_GUARANTEE: Int = 4
private const val DRONE_HASH_SALT: Long = 0x4E1F_C0DEL
private const val DRONE_LIFE_SECONDS: Float = 12f
private const val DRONE_FIRST_DART_SECONDS: Float = 0.3f
internal const val DRONE_ORBIT_RADIUS: Float = 70f
private const val DRONE_ORBIT_SPEED: Float = 2.4f
private const val DRONE_RANGE: Float = 260f
internal const val DRONE_DAMAGE: Float = 9f
private const val DRONE_INTERVAL: Float = 1f
private const val DRONE_DART_SECONDS: Float = 0.2f
private const val DRONE_KILL_HEAL: Float = 0.5f
private const val DRONE_HEAL_INTERVAL: Float = 1f
