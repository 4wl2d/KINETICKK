// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.characters

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
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
    // Hypercube holds the lattice as its cage: its nodes do not expire while it runs.
    if (ability.latticeTime.toRawBits() != 0 && !hypercubeRunning()) {
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
                ability.parryWindow = parryWindowSeconds()
                ability.parryCooldown = PARRY_COOLDOWN_SECONDS
            }
            ability.charge = if (ability.parryWindow > 0f) 1f else max(0f, 1f - ability.parryCooldown / PARRY_COOLDOWN_SECONDS)
        }
        CoreShape.TESSERACT -> updateLattice(ability)
        CoreShape.STAR -> {
            // Hypernova: a nova comes on every Apex cooldown, with whatever Mass the Star holds.
            if (ability.stellarMass >= CharacterRuntime.MAX_STELLAR_MASS || apexActive()) supernova()
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
    if (apexUnlocked) updateApexForm(delta)
    ability.previousSpeed = currentSpeed
    ability.previousHeading = heading
    ability.wasBraking = braking
}
