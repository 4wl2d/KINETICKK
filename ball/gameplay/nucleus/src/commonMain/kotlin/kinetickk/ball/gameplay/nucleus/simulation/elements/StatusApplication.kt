// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.elements

import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------------------------------------
// Application.

/**
 * Applies [count] stacks of [element] at [generation]. Conversions, caps, reactions and Breaks resolve
 * here. Outputs at generation two or above are dropped by the caller's rules, never here.
 */
internal fun MutableGameState.applyStatus(enemy: Enemy, element: StatusElement, count: Int, generation: Int) {
    if (count <= 0 || !enemy.alive) return
    if (generation >= 2) return
    if (isShifting(enemy)) return
    var landing = element
    var amount = count
    val converted = element == StatusElement.THERMAL && build.liability(RelicId.ABSOLUTE_ZERO)
    if (converted) {
        amount /= 2
        landing = StatusElement.CRYO
        if (amount <= 0) return
    }
    // A Suppressed enemy has no aspect: no immunity, resistance or weakness.
    val suppressed = markIs(enemy, MarkKind.SUPPRESSED)
    // An immune aspect refuses the landing element before any side effect.
    if (!suppressed && enemy.aspectIsImmune && enemy.aspectElement == landing.ordinal) {
        signalImmunity(enemy)
        return
    }
    if (converted && build.pairing(RelicPairingId.THERMAL_PARADOX)) heat = max(0f, heat - 3f * amount)
    val affinityCarry = when {
        suppressed -> -1
        landing.ordinal == enemy.aspectElement -> StatusCell.RESIST_CARRY
        landing.ordinal == enemy.weaknessElement -> StatusCell.WEAK_CARRY
        else -> -1
    }
    if (affinityCarry >= 0) {
        // Resisted stacks land at half strength, the weakness at one and a half; fractions carry.
        val scaled = enemy.status[affinityCarry] + amount * if (affinityCarry == StatusCell.RESIST_CARRY) 0.5f else 1.5f
        amount = scaled.toInt()
        enemy.status[affinityCarry] = scaled - amount
        if (amount <= 0) return
    }
    if (landing == StatusElement.CRYO && enemy.status[StatusCell.FREEZE_IMMUNE_UNTIL] > elapsed && !isFrozen(enemy)) {
        // Half strength while immune; an odd half stack carries to the next Cryo landing.
        val half = enemy.status[StatusCell.CRYO_IMMUNE_CARRY] + amount * 0.5f
        amount = half.toInt()
        enemy.status[StatusCell.CRYO_IMMUNE_CARRY] = half - amount
        if (amount <= 0) return
    }
    val definition = content.build.element(landing)
    val index = landing.ordinal
    val before = enemy.status[StatusCell.STACKS + index].toInt()
    val after = min(definition.stackCap, before + amount)
    enemy.status[StatusCell.STACKS + index] = after.toFloat()
    val generationCell = StatusCell.GENERATION + index
    enemy.status[generationCell] = if (before == 0) generation.toFloat() else max(enemy.status[generationCell], generation.toFloat())
    enemy.status[StatusCell.DECAY_AT + index] = elapsed + decaySeconds(landing)
    if (landing == StatusElement.CRYO && build.pairing(RelicPairingId.EXTINGUISHED)) {
        enemy.status[StatusCell.CINDER_BANK] += amount * 3f * runScale()
    }
    tryReact(enemy, landing, generation)
    if (enemy.alive) tryBreak(enemy, landing)
}

/**
 * Sets an Elite's or the Architect's aspect. An immune element loses its stacks, so nothing stale keeps
 * reacting or adding vulnerability; both affinity carries restart.
 */
internal fun assignAspect(enemy: Enemy, element: Int, immune: Boolean) {
    enemy.aspect = element
    enemy.aspectImmune = immune && element >= 0
    if (enemy.aspectImmune) {
        enemy.status[StatusCell.STACKS + element] = 0f
        enemy.status[StatusCell.GENERATION + element] = 0f
        enemy.status[StatusCell.DECAY_AT + element] = 0f
        forgetHollowWhenEmpty(enemy)
    }
    enemy.status[StatusCell.RESIST_CARRY] = 0f
    enemy.status[StatusCell.WEAK_CARRY] = 0f
}

/** Feedback when an immune aspect refuses stacks or damage, at most once per [IMMUNE_CUE_SECONDS] per enemy. */
internal fun MutableGameState.signalImmunity(enemy: Enemy) {
    if (enemy.status[StatusCell.IMMUNE_FX_READY_AT] > elapsed) return
    enemy.status[StatusCell.IMMUNE_FX_READY_AT] = elapsed + IMMUNE_CUE_SECONDS
    recordStatusCue(VisualFxCue.Burst(enemy.x, enemy.y, 3, IMMUNE_CUE_COLOR, settings.particleDensity))
}

internal const val IMMUNE_CUE_SECONDS: Float = 0.25f
private const val IMMUNE_CUE_COLOR: Int = 2

/** Applies stacks around a point, generation +1 rules handled by the caller passing [generation]. */
internal fun MutableGameState.applyStatusAround(
    x: Float,
    y: Float,
    radius: Float,
    element: StatusElement,
    count: Float,
    generation: Int,
    excludedId: Int = -1,
) {
    val whole = count.toInt().coerceAtLeast(1)
    if (generation >= 2) return
    for (index in enemies.indices) {
        val target = enemies[index]
        if (target.id == excludedId || !target.alive) continue
        if (distanceSquared(x, y, target.x, target.y) <= square(radius + target.radius)) applyStatus(target, element, whole, generation)
    }
}

internal fun MutableGameState.decaySeconds(element: StatusElement): Float {
    var seconds = content.build.element(element).decaySeconds * build.durationMultiplier(element)
    if (element == StatusElement.THERMAL && build.liability(RelicId.CINDER_CROWN)) seconds *= 0.5f
    return seconds
}
