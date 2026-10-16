// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Fires a reaction when [incoming] lands on an enemy that already holds at least two stacks of another
 * element. The older element with the most stacks pairs (ties by element order). Bounded by per-enemy and
 * per-pair cooldowns, the generation cap and the reaction token budget.
 */
internal fun MutableGameState.tryReact(enemy: Enemy, incoming: StatusElement, incomingGeneration: Int) {
    if (!enemy.alive) return
    if (enemy.status[StatusCell.REACTION_READY_AT] > elapsed) return
    var partner: StatusElement? = null
    var partnerStacks = 1
    for (element in StatusElement.entries) {
        if (element == incoming) continue
        val count = stacks(enemy, element)
        if (count > partnerStacks) {
            partner = element
            partnerStacks = count
        }
    }
    val older = partner ?: return
    if (partnerStacks < 2) return
    val reaction = ReactionId.between(incoming, older) ?: return
    val generation = max(incomingGeneration, generation(enemy, older))
    if (generation >= 2) return
    // Each pair keeps its own cooldown, so alternating two reactions cannot bypass it.
    if (enemy.status[StatusCell.REACTION_PAIR_READY_AT + reaction.ordinal] > elapsed) return
    if (!admitReaction(generation)) return

    val a = partnerStacks
    val b = min(3, stacks(enemy, incoming))
    enemy.status[StatusCell.STACKS + older.ordinal] = 0f
    enemy.status[StatusCell.STACKS + incoming.ordinal] = (stacks(enemy, incoming) - b).toFloat()
    enemy.status[StatusCell.REACTION_READY_AT] = elapsed + content.build.policy.reactionCooldownSeconds
    enemy.status[StatusCell.LAST_REACTION] = (reaction.ordinal + 1).toFloat()
    enemy.status[StatusCell.LAST_REACTION_AT] = elapsed
    enemy.status[StatusCell.REACTION_PAIR_READY_AT + reaction.ordinal] =
        elapsed + content.build.policy.reactionPairCooldownSeconds
    resolveReaction(enemy, reaction, a, b, older, incoming, generation)
}

private fun MutableGameState.resolveReaction(
    enemy: Enemy,
    reaction: ReactionId,
    a: Int,
    b: Int,
    older: StatusElement,
    incoming: StatusElement,
    generation: Int,
) {
    val definition = content.build.reaction(reaction)
    val potency = (build.potency(older) + build.potency(incoming)) * 0.5f
    val magnitude = definition.power * (a + b) * potency * build.reactionPower * runScale()
    val residueGeneration = generation + 1
    val x = enemy.x
    val y = enemy.y
    when (reaction) {
        ReactionId.THERMAL_SHOCK -> {
            dealStatusDamage(enemy, magnitude, StatusElement.THERMAL, ignoresAffinity = true)
            enemy.status[StatusCell.BRITTLE] = 1f
        }
        ReactionId.PLASMA_BURST -> {
            for (index in enemies.indices) {
                val target = enemies[index]
                if (target.alive && distanceSquared(x, y, target.x, target.y) <= square(definition.radius + target.radius)) {
                    dealStatusDamage(target, magnitude, StatusElement.THERMAL, ignoresAffinity = true)
                    if (residueGeneration < 2 && target.alive) applyStatus(target, StatusElement.THERMAL, definition.residueStacks, residueGeneration)
                }
            }
        }
        ReactionId.FUSION -> addField(CombatField(
            kind = CombatFieldKind.FUSION, remaining = 1.6f, x = x, y = y, radius = definition.radius,
            damagePerSecond = magnitude, element = StatusElement.THERMAL, stacksPerTick = 1,
            generation = residueGeneration, pull = 220f,
        ))
        ReactionId.WILDFIRE -> {
            dealStatusDamage(enemy, magnitude, StatusElement.THERMAL, ignoresAffinity = true)
            var spread = 0
            val spreadStacks = a / 2 + 1
            for (index in enemies.indices) {
                if (spread >= 4) break
                val target = enemies[index]
                if (target.id == enemy.id || !target.alive) continue
                if (distanceSquared(x, y, target.x, target.y) <= square(definition.radius + target.radius)) {
                    spread++
                    addRelicArc(x, y, target.x, target.y)
                    if (residueGeneration < 2) applyStatus(target, StatusElement.THERMAL, spreadStacks, residueGeneration)
                }
            }
        }
        ReactionId.SUPERCONDUCT -> {
            dealStatusDamage(enemy, magnitude, StatusElement.CRYO, ignoresAffinity = true)
            chain(x, y, enemy.id, 3, definition.radius) { target ->
                dealStatusDamage(target, magnitude, StatusElement.ION, ignoresAffinity = true)
                if (residueGeneration < 2) applyStatus(target, StatusElement.CRYO, definition.residueStacks, residueGeneration)
            }
        }
        ReactionId.GLACIER_CRUSH -> {
            repeat(5) { shard ->
                val angle = shard * TAU / 5f + enemy.id * 0.37f
                val endX = x + cos(angle) * 220f
                val endY = y + sin(angle) * 220f
                addRelicArc(x, y, endX, endY)
                for (index in enemies.indices) {
                    val target = enemies[index]
                    if (target.id == enemy.id || !target.alive) continue
                    if (segmentCircleIntersects(x, y, endX, endY, target.x, target.y, target.radius + 6f)) {
                        dealStatusDamage(target, magnitude, StatusElement.CRYO, ignoresAffinity = true)
                        if (residueGeneration < 2 && target.alive) applyStatus(target, StatusElement.CRYO, definition.residueStacks, residueGeneration)
                    }
                }
            }
            dealStatusDamage(enemy, magnitude, StatusElement.CRYO, ignoresAffinity = true)
        }
        ReactionId.EMBRITTLE -> {
            enemy.status[StatusCell.EMBRITTLED_UNTIL] = elapsed + 4f
        }
        ReactionId.MAGNETIZE -> {
            for (index in enemies.indices) {
                val target = enemies[index]
                if (target.id == enemy.id || !target.alive) continue
                val dx = x - target.x
                val dy = y - target.y
                val distance = max(1f, length(dx, dy))
                if (distance > definition.radius + target.radius) continue
                val knockback = target.knockback
                target.vx += dx / distance * 640f * knockback
                target.vy += dy / distance * 640f * knockback
                dealStatusDamage(target, magnitude, StatusElement.GRAVITIC, ignoresAffinity = true)
                if (residueGeneration < 2 && target.alive) applyStatus(target, StatusElement.GRAVITIC, definition.residueStacks, residueGeneration)
            }
            dealStatusDamage(enemy, magnitude, StatusElement.ION, ignoresAffinity = true)
        }
        ReactionId.FEEDBACK -> {
            if (enemy.status[StatusCell.MAX_HP_PROC_READY_AT] <= elapsed) {
                enemy.status[StatusCell.MAX_HP_PROC_READY_AT] = elapsed + maxHpProcCooldown(enemy)
                dealStatusDamage(enemy, enemy.maxHp * victimFactor(enemy) * definition.power, StatusElement.ION, ignoresAffinity = true)
            }
            val arc = 5f * (a + b) * potency * build.reactionPower * runScale()
            chain(x, y, enemy.id, 2, definition.radius) { target ->
                dealStatusDamage(target, arc, StatusElement.ION, ignoresAffinity = true)
                if (residueGeneration < 2 && target.alive) applyStatus(target, StatusElement.ENTROPY, definition.residueStacks, residueGeneration)
            }
        }
        ReactionId.ROT_WELL -> addField(CombatField(
            kind = CombatFieldKind.ROT_WELL, remaining = 2f, x = x, y = y, radius = definition.radius,
            damagePerSecond = magnitude, element = StatusElement.ENTROPY, stacksPerTick = 1,
            generation = residueGeneration, pull = 180f,
        ))
    }
    if (build.reactionResidue > 0 && enemy.alive && residueGeneration < 2) {
        applyStatus(enemy, incoming, build.reactionResidue, residueGeneration)
    }
    if (build.reactionRecoil > 0f && distanceSquared(x, y, coreX, coreY) <= square(build.reactionRecoilRadius)) {
        takeDamage(build.reactionRecoil * selfDamageScale(), ignoreReduction = false, kind = IncomingDamage.SELF)
    }
    val bit = 1 shl reaction.ordinal
    val first = discoveredReactionMask and bit == 0
    if (first) {
        discoveredReactionMask = discoveredReactionMask or bit
        pendingDiscoveredReactionMask = pendingDiscoveredReactionMask or bit
        emitVisualFx(VisualFxCue.BuildChanged("REACTION DISCOVERED", kinetickk.foundation.collections.immutableListOf("+ " + definition.name)))
    }
    recordStatusCue(VisualFxCue.ReactionTriggered(x, y, reaction.ordinal, max(definition.radius, enemy.radius * 2f), first))
    onReaction(enemy, reaction)
}

internal fun MutableGameState.addField(field: CombatField) {
    if (combatFields.size >= content.build.policy.maximumFields) return
    combatFields = combatFields + field
}

internal fun MutableGameState.updateCombatFields(delta: Float) {
    val current = combatFields
    if (current.isEmpty()) return
    val retained = ArrayList<CombatField>(current.size)
    for (field in current) {
        val remaining = field.remaining - delta
        var clock = field.tickClock + delta
        for (index in enemies.indices) {
            val target = enemies[index]
            val knockback = target.knockback
            if (!target.alive || knockback <= 0f) continue
            val dx = field.x - target.x
            val dy = field.y - target.y
            val distance = length(dx, dy)
            if (distance > field.radius + target.radius || distance < 1f) continue
            target.vx += dx / distance * field.pull * delta * knockback
            target.vy += dy / distance * field.pull * delta * knockback
        }
        while (clock >= CombatField.TICK_SECONDS) {
            clock -= CombatField.TICK_SECONDS
            for (index in enemies.indices) {
                val target = enemies[index]
                if (!target.alive) continue
                if (distanceSquared(field.x, field.y, target.x, target.y) > square(field.radius + target.radius)) continue
                // Reaction fields ignore affinity like every reaction; item wells meet the target's aspect.
                dealStatusDamage(target, field.damagePerSecond * CombatField.TICK_SECONDS, field.element, showNumber = false,
                    ignoresAffinity = field.kind == CombatFieldKind.FUSION || field.kind == CombatFieldKind.ROT_WELL)
                val element = field.element
                if (element != null && field.stacksPerTick > 0 && field.generation < 2 && target.alive) {
                    applyStatus(target, element, field.stacksPerTick, field.generation)
                }
            }
        }
        if (remaining > 0f) retained += field.copy(remaining = remaining, tickClock = clock)
    }
    // addField only appends while fields tick, so everything past the snapshot was spawned this step;
    // the field bound already counted it against the larger list.
    combatFields = if (combatFields.size > current.size) retained + combatFields.subList(current.size, combatFields.size) else retained
}

internal fun MutableGameState.rebaseCombatFields(shiftX: Float, shiftY: Float) {
    if (combatFields.isEmpty()) return
    combatFields = combatFields.map { it.copy(x = it.x - shiftX, y = it.y - shiftY) }
}
