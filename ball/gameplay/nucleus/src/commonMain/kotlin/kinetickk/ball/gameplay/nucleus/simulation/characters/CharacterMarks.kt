// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.characters

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_X
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_Y
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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

/** Opens a Shard mark on [enemy] (a Scatter Bloom pellet's Puncture mark); nothing for other characters. */
internal fun MutableGameState.markForPuncture(enemy: Enemy) {
    if (coreShape == CoreShape.SHARD && enemy.alive) enemy.characterMarkTime = MARK_SECONDS
}

/**
 * Spends an open Shard mark on [enemy] for a Puncture of [multiplier] times its damage when Puncture is
 * ready (the Null Lance signature's double Puncture); true when it struck.
 */
internal fun MutableGameState.tryPuncture(enemy: Enemy, multiplier: Float): Boolean {
    if (!punctureReady(enemy) || !consumeMark(enemy)) return false
    puncture(enemy, multiplier)
    return true
}

/**
 * Puncture is ready for [enemy]. While Vivisect runs a boss-class enemy still waits the base interval after the
 * last puncture of any enemy, so the faster cadence never reaches it.
 */
internal fun MutableGameState.punctureReady(enemy: Enemy): Boolean {
    if (elapsed < characterRuntime.punctureReadyAt) return false
    return !enemy.isBossClass || !apexActive() || elapsed >= skillRuntime.apex(ApexCell.BOSS_BASE_READY_AT)
}

/**
 * A Puncture of [multiplier] times its damage on [enemy] (its mark already spent). While Vivisect runs, punctures
 * come at the Apex interval on ordinary enemies and spread the mark to the next one; on a boss-class enemy the
 * first punctures of the activation are Apex strikes (full hit cap), at the base interval.
 */
internal fun MutableGameState.puncture(enemy: Enemy, multiplier: Float) {
    val ability = characterRuntime
    val vivisect = apexActive()
    val damage = PUNCTURE_DAMAGE * multiplier * abilityPower()
    ability.punctureReadyAt = elapsed + if (vivisect && !enemy.isBossClass) apexValue(VIVISECT_INTERVAL) else PUNCTURE_INTERVAL
    if (vivisect) skillRuntime.setApex(ApexCell.BOSS_BASE_READY_AT, elapsed + PUNCTURE_INTERVAL)
    if (!(vivisect && enemy.isBossClass && apexStrike(enemy) > 0f)) {
        dealCharacterDamage(enemy, damage)
    }
    heat = max(0f, heat - punctureHeatRefund())
    shockwave(enemy.x, enemy.y, 0.3f, 65f, 2)
    if (vivisect) spreadPunctureMark(enemy)
}

/** Vivisect: a puncture opens a mark on the nearest unmarked ordinary enemy within reach of [from]. */
private fun MutableGameState.spreadPunctureMark(from: Enemy) {
    val reach = apexValue(VIVISECT_SPREAD_RADIUS)
    var nearest: Enemy? = null
    var nearestDistance = reach * reach
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy === from || !enemy.alive || enemy.isBossClass || enemy.characterMarkTime > 0f || isShifting(enemy)) continue
        val distance = distanceSquared(from.x, from.y, enemy.x, enemy.y)
        if (distance <= nearestDistance) {
            nearest = enemy
            nearestDistance = distance
        }
    }
    val target = nearest ?: return
    target.characterMarkTime = MARK_SECONDS
    addRelicArc(from.x, from.y, target.x, target.y, LinkStyleCode.apex(CoreShape.SHARD))
}

/** True from a parry until the end of the next fixed step, so weapon updates of that step can react. */
internal val MutableGameState.parriedThisStep: Boolean get() = characterRuntime.parryFlagSteps > 0

/** True for the rest of the fixed step in which the Core blinked (the Crescent's Blink or Blink Drive's). */
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
