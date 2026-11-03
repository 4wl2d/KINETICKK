// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.skills

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.protocol.SkillCueCodes
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.progression.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kotlin.math.exp
import kotlin.math.max

// ---------------------------------------------------------------------------------------------
// Skill fields (kind branches the field pass calls).

/** Seconds between two ticks of a field of [kind]: Storm Coil zaps every half second, a Resonator on the beat. */
internal fun tickPeriod(kind: CombatFieldKind): Float = when (kind) {
    CombatFieldKind.STORM_COIL -> STORM_COIL_ZAP_SECONDS
    CombatFieldKind.ECHO_RING -> BEAT_PERIOD
    else -> CombatField.TICK_SECONDS
}

/** True when ([x], [y]) is within [extra] of the field at [index] (a Frost Line measures to its segment). */
internal fun MutableGameState.fieldContains(index: Int, x: Float, y: Float, extra: Float): Boolean {
    val table = combatFieldTable
    val reach = table[index, FieldCell.RADIUS] + extra
    return if (table.kind(index) == CombatFieldKind.FROST_LINE) {
        pointToSegmentDistanceSquared(x, y, table[index, FieldCell.X], table[index, FieldCell.Y],
            table[index, FieldCell.X2], table[index, FieldCell.Y2]) <= reach * reach
    } else {
        distanceSquared(table[index, FieldCell.X], table[index, FieldCell.Y], x, y) <= reach * reach
    }
}

/**
 * Every step a Frost Line slows the ordinary enemies on it, and a thrown enemy that crosses it slams into it
 * and takes Chill.
 */
internal fun MutableGameState.frostLineStep(index: Int, delta: Float) {
    val table = combatFieldTable
    val x0 = table[index, FieldCell.X]
    val y0 = table[index, FieldCell.Y]
    val x1 = table[index, FieldCell.X2]
    val y1 = table[index, FieldCell.Y2]
    val damping = exp(-FROST_LINE_DAMPING * delta)
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || isShifting(enemy)) continue
        val thrown = enemy.thrownUntil > elapsed || isThrownByPolarity(enemy)
        if (thrown && segmentsCross(enemy.previousX, enemy.previousY, enemy.x, enemy.y, x0, y0, x1, y1)) {
            if (applySlamDamage(enemy, length(enemy.vx, enemy.vy)) && enemy.alive) {
                applyStatus(enemy, StatusElement.CRYO, FROST_LINE_SLAM_CHILL, 0)
            }
        }
        if (!enemy.alive || enemy.isEliteClass || !fieldContains(index, enemy.x, enemy.y, enemy.radius)) continue
        enemy.vx *= damping
        enemy.vy *= damping
    }
}

/** A Frost Line tick: Chill on the line (rank III: Embrittled while it stays). */
internal fun MutableGameState.frostLineTick(index: Int) {
    val table = combatFieldTable
    val chill = table[index, FieldCell.STACKS].toInt()
    val rankThree = table.hasFlag(index, FieldFlags.RANK_III)
    val element = table.element(index) ?: StatusElement.CRYO
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || !fieldContains(index, enemy.x, enemy.y, enemy.radius)) continue
        applyStatus(enemy, element, chill, 0)
        if (rankThree && enemy.alive) {
            enemy.status[StatusCell.EMBRITTLED_UNTIL] = max(enemy.status[StatusCell.EMBRITTLED_UNTIL], elapsed + FROST_LINE_EMBRITTLE_SECONDS)
        }
    }
}

/**
 * A Storm Coil zap: the nearest enemies inside (at most its target count, three by construction) take Ion
 * damage and one Charge; rank III forces Overload on an enemy close to it.
 */
internal fun MutableGameState.stormCoilZap(index: Int) {
    val table = combatFieldTable
    val x = table[index, FieldCell.X]
    val y = table[index, FieldCell.Y]
    val radius = table[index, FieldCell.RADIUS]
    val count = table[index, FieldCell.STACKS].toInt().coerceIn(0, STORM_COIL_MAX_TARGETS)
    val damage = table[index, FieldCell.POWER]
    val rankThree = table.hasFlag(index, FieldFlags.RANK_III)
    var firstId = -1
    var secondId = -1
    var thirdId = -1
    var firstDistance = Float.POSITIVE_INFINITY
    var secondDistance = Float.POSITIVE_INFINITY
    var thirdDistance = Float.POSITIVE_INFINITY
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || isShifting(enemy)) continue
        val distance = distanceSquared(x, y, enemy.x, enemy.y)
        if (distance > square(radius + enemy.radius)) continue
        when {
            distance < firstDistance -> {
                thirdId = secondId; thirdDistance = secondDistance
                secondId = firstId; secondDistance = firstDistance
                firstId = enemy.id; firstDistance = distance
            }
            distance < secondDistance -> {
                thirdId = secondId; thirdDistance = secondDistance
                secondId = enemy.id; secondDistance = distance
            }
            distance < thirdDistance -> {
                thirdId = enemy.id; thirdDistance = distance
            }
        }
    }
    val link = LinkStyleCode.skill(SkillId.STORM_COIL)
    val element = table.element(index) ?: StatusElement.ION
    for (pick in 0 until count) {
        val id = when (pick) { 0 -> firstId; 1 -> secondId; else -> thirdId }
        val enemy = enemyById(id) ?: break
        if (!enemy.alive) continue
        addRelicArc(x, y, enemy.x, enemy.y, link)
        skillFieldHit(table.owner(index), enemy, damage, element)
        if (enemy.alive) applyStatus(enemy, element, 1, 0)
        if (rankThree && enemy.alive &&
            stacks(enemy, element) >= breakThreshold(element, enemy) - SKILL_BREAK_MARGIN
        ) forceElementBreak(enemy, element)
    }
}

/**
 * A Resonator pulse on the beat: one Resonance to every enemy inside, then the latest reaction inside (within
 * the last beat) echoes once on its enemy, while the resonator has echoes left.
 */
internal fun MutableGameState.resonatorPulse(index: Int) {
    val table = combatFieldTable
    var latest: Enemy? = null
    var latestAt = elapsed - BEAT_PERIOD
    val element = table.element(index) ?: StatusElement.SONIC
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || !fieldContains(index, enemy.x, enemy.y, enemy.radius)) continue
        applyStatus(enemy, element, 1, 0)
    }
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || !fieldContains(index, enemy.x, enemy.y, enemy.radius)) continue
        val at = enemy.status[StatusCell.LAST_REACTION_AT]
        if (enemy.status[StatusCell.LAST_REACTION] > 0f && at >= latestAt && (latest == null || at > latestAt)) {
            latest = enemy
            latestAt = at
        }
    }
    val echoTarget = latest ?: return
    if (table[index, FieldCell.CHARGE] >= table[index, FieldCell.STACKS]) return
    val reaction = kinetickk.ball.content.api.ReactionId.entries.getOrNull(echoTarget.status[StatusCell.LAST_REACTION].toInt() - 1)
        ?: return
    if (echoReaction(echoTarget, reaction, table[index, FieldCell.POWER])) {
        table[index, FieldCell.CHARGE] = table[index, FieldCell.CHARGE] + 1f
        table.touch()
        addRelicArc(table[index, FieldCell.X], table[index, FieldCell.Y], echoTarget.x, echoTarget.y,
            LinkStyleCode.skill(SkillId.RESONATOR))
    }
}

/** A tick of the skill field at [index]'s own damage (a Gravity Well's): skill damage, never a skill hit. */
internal fun MutableGameState.skillFieldTickDamage(index: Int, target: Enemy, amount: Float, element: StatusElement?) {
    skillFieldHit(combatFieldTable.owner(index), target, amount, element)
}

/** A Gravity Well closes: loose Data and repairs inside latch to the Core; rank III collapses the heavy enemies. */
internal fun MutableGameState.gravityWellClosed(index: Int) {
    val table = combatFieldTable
    val x = table[index, FieldCell.X]
    val y = table[index, FieldCell.Y]
    val radius = table[index, FieldCell.RADIUS]
    val latch = latchedVacuumActive()
    for (pickupIndex in pickups.indices) {
        val pickup = pickups[pickupIndex]
        if (!pickup.expires || pickup.attracted) continue
        if (distanceSquared(x, y, pickup.x, pickup.y) <= radius * radius) pickup.attracted = latch
    }
    val rank = skillLoadout.rank(table.owner(index)).coerceIn(1, ContentBounds.MAX_SKILL_RANK)
    emitVisualFx(VisualFxCue.SkillCast(SkillId.GRAVITY_WELL, x, y, 0f, 0f, radius, rank, SkillCueCodes.PHASE_IMPACT))
    if (!table.hasFlag(index, FieldFlags.RANK_III)) return
    val element = table.element(index) ?: StatusElement.GRAVITIC
    for (enemyIndex in enemies.indices) {
        val enemy = enemies[enemyIndex]
        if (!enemy.alive || distanceSquared(x, y, enemy.x, enemy.y) > square(radius + enemy.radius)) continue
        if (stacks(enemy, element) >= GRAVITY_WELL_COLLAPSE_MASS) forceElementBreak(enemy, element)
    }
}
