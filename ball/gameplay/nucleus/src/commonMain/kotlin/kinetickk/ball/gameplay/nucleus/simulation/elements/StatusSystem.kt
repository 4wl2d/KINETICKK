// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.elements

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.builds.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.dimensions.*
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------------------------------------
// Reads.

internal fun stacks(enemy: Enemy, element: StatusElement): Int = enemy.status[StatusCell.STACKS + element.ordinal].toInt()

internal fun totalStacks(enemy: Enemy): Int {
    var total = 0
    for (index in 0 until StatusCell.ELEMENTS) total += enemy.status[StatusCell.STACKS + index].toInt()
    return total
}

/**
 * The Architect, the mini-bosses and the secret bosses: immune to executes, stops and stuns, only briefly silenced,
 * and Unmake meets their weighted integrity. Every element rule and [canAct] ask this one question.
 */
internal val Enemy.isBossClass: Boolean get() = type.profile.bossClass

/** Elites and the bosses; Null Covenant keeps Hollow on them. */
internal val Enemy.isEliteClass: Boolean get() = type.profile.eliteClass

/**
 * Displacement [enemy] takes from anything the player causes: its frame's knockback, raised by each
 * Resonance stack and by Thunder Choir. Frames that never move (knockback 0) stay immovable.
 */
internal fun MutableGameState.knockbackTaken(enemy: Enemy): Float {
    val frame = enemy.knockback
    if (frame <= 0f) return 0f
    var scale = 1f
    val resonance = stacks(enemy, StatusElement.SONIC)
    if (resonance > 0) scale += resonance * content.build.element(StatusElement.SONIC).perStack * build.potency(StatusElement.SONIC)
    if (build.upside(RelicId.THUNDER_CHOIR)) scale *= 1f + THUNDER_CHOIR_KNOCKBACK * build.relicUpside(RelicId.THUNDER_CHOIR)
    return frame * scale
}

internal fun MutableGameState.isFrozen(enemy: Enemy): Boolean = enemy.status[StatusCell.FROZEN_UNTIL] > elapsed

internal fun generation(enemy: Enemy, element: StatusElement): Int = enemy.status[StatusCell.GENERATION + element.ordinal].toInt()

/** Effective Break threshold for [element] on [enemy], respecting the content floor. */
internal fun MutableGameState.breakThreshold(element: StatusElement, enemy: Enemy? = null): Int {
    val definition = content.build.element(element)
    var threshold = definition.breakThreshold - build.thresholdCut(element)
    if (element == StatusElement.THERMAL && build.upside(RelicId.CINDER_CROWN)) threshold = min(threshold, 5)
    if (element == StatusElement.CRYO && enemy != null && enemy.status[StatusCell.EMBRITTLED_UNTIL] > elapsed) threshold -= 2
    return max(definition.breakFloor, threshold)
}

internal fun MutableGameState.statusNearBreak(enemy: Enemy, fraction: Float): Boolean {
    for (element in StatusElement.entries) {
        val count = stacks(enemy, element)
        if (count > 0 && count >= breakThreshold(element, enemy) * fraction) return true
    }
    return false
}

/**
 * Scale of [enemy]'s integration and attack cadence: zero while Frozen, slowed by Chill and stasis. A
 * stunned or stopped enemy neither steers nor counts down an attack, but still drifts at this scale while
 * its momentum bleeds off.
 */
internal fun MutableGameState.enemyMotionScale(enemy: Enemy): Float {
    if (isFrozen(enemy)) return 0f
    var scale = 1f
    val chill = stacks(enemy, StatusElement.CRYO)
    if (chill > 0) {
        val slow = min(0.63f, chill * content.build.element(StatusElement.CRYO).perStack * build.potency(StatusElement.CRYO))
        scale *= 1f - if (enemy.isBossClass) slow * 0.5f else slow
    }
    if (stasisActive() && distanceSquared(enemy.x, enemy.y, coreX, coreY) <= square(stasisRadius())) {
        scale *= 1f - stasisSlow()
    }
    if (build.liability(RelicId.PANDEMIC_CODEX) && totalStacks(enemy) > 0) scale *= 1.2f
    // Hypercube's cage slows the ordinary enemies inside.
    scale *= hypercubeMotionScale(enemy)
    scale *= layerTimeScale(enemy)
    return scale
}

internal val MutableGameState.anchorBreaksOnRelease: Boolean get() = tagTier(BuildTag.ANCHOR) >= 3
