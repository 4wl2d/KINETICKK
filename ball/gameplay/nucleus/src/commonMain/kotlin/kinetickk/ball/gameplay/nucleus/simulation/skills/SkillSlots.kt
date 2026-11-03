// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.skills

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------------------------------------
// Refund bank.

/**
 * Takes up to [seconds] off [slot]'s cooldown. The bank bounds it: an empty or ready slot takes nothing, a cast
 * takes back at most half its cooldown in total, and no refund brings the slot below [MIN_SKILL_RECAST_SECONDS]
 * after its cast. Returns the seconds refunded.
 */
internal fun MutableGameState.refundSkill(slot: Int, seconds: Float): Float {
    if (!(seconds > 0f) || skillLoadout.isEmpty(slot)) return 0f
    val runtime = skillRuntime
    val readyAt = runtime[slot, SkillCell.READY_AT]
    if (readyAt <= elapsed) return 0f
    val refunded = runtime[slot, SkillCell.REFUNDED]
    val room = min(
        MAX_REFUND_SHARE * runtime[slot, SkillCell.LAST_COOLDOWN] - refunded,
        readyAt - max(elapsed, runtime[slot, SkillCell.CAST_AT] + MIN_SKILL_RECAST_SECONDS),
    )
    val refund = min(seconds, room)
    if (!(refund > 0f)) return 0f
    runtime[slot, SkillCell.READY_AT] = readyAt - refund
    runtime[slot, SkillCell.REFUNDED] = refunded + refund
    return refund
}

/** Refunds [seconds] to every owned skill through the bank; returns the seconds refunded in total. */
internal fun MutableGameState.refundAllSkills(seconds: Float): Float {
    if (skillLoadout.ownedCount == 0) return 0f
    var total = 0f
    for (slot in 0 until SkillRuntime.SLOTS) total += refundSkill(slot, seconds)
    return total
}

/**
 * Catalysis: a reaction between [older] and [incoming] recharges each owned skill of either element and, less,
 * each element-less skill. Echoes never call it.
 */
internal fun MutableGameState.catalyze(older: StatusElement, incoming: StatusElement) {
    val loadout = skillLoadout
    if (loadout.ownedCount == 0) return
    for (slot in 0 until SkillRuntime.SLOTS) {
        val id = loadout.skill(slot) ?: continue
        val element = skillElement(id)
        val seconds = when {
            element == null -> CATALYSIS_ELEMENTLESS_SECONDS
            element == older || element == incoming -> CATALYSIS_SECONDS
            else -> continue
        }
        refundSkill(slot, seconds * build.catalysisRate)
    }
}

// ---------------------------------------------------------------------------------------------
// Slots. Active items are the only writers: acquiring, stacking or replacing one.

/**
 * The active effect of [item] after it gained a stack ([stacks] now): the first stack binds the effect into the lowest
 * empty slot at rank I, ready at once; a later stack raises the rank to [stacks], ready at once. Stacks and rank never
 * diverge. The caller recompiles the build.
 */
internal fun MutableGameState.stackActiveEffect(item: ItemDefinition, stacks: Int) {
    val id = checkNotNull(item.active) { "${item.name} carries no active effect" }
    if (stacks <= 1) acquireSkill(id) else rankUpSkill(id, stacks)
}

/** Fills the lowest empty slot with [id] at rank I, ready at once. */
private fun MutableGameState.acquireSkill(id: SkillId) {
    val slot = skillLoadout.lowestEmptySlot()
    check(slot >= 0 && !skillLoadout.owns(id)) { "$id needs an empty slot and must not be bound yet" }
    skillLoadout = skillLoadout.with(slot, id, 1)
    resetSkillSlot(slot)
}

/** Raises [id] to [rank] (up to III); the skill is ready at once. */
private fun MutableGameState.rankUpSkill(id: SkillId, rank: Int) {
    val slot = skillLoadout.slotOf(id)
    check(slot >= 0) { "$id is not bound" }
    skillLoadout = skillLoadout.with(slot, id, rank.coerceAtMost(ContentBounds.MAX_SKILL_RANK))
    val runtime = skillRuntime
    runtime[slot, SkillCell.RANKED_AT_LEVEL] = level.toFloat()
    runtime[slot, SkillCell.READY_AT] = elapsed
    runtime[slot, SkillCell.REFUNDED] = 0f
}

/**
 * Empties [slot] for a replacement and ends everything the active effect it held still has running: the slot's
 * timing and per-cast state (pending impacts, running effects, windows, ghost repeats), its skill field (without the
 * field's closing effect), its own buffs, Null Shroud's returning bolts, the marks it seeded that would still pay out
 * (Decay Spores' seeds, Sonic Boom's ringing) and the slot's buffered request. The effect's run damage stays counted.
 */
internal fun MutableGameState.releaseSkillSlot(slot: Int) {
    val id = skillLoadout.skill(slot) ?: return
    resetSkillSlot(slot)
    combatFieldTable.removeSkillField(slot)
    val buffSource = BuffSource.skill(id)
    if (buffs.any { it.sourceKey == buffSource }) buffs = buffs.filter { it.sourceKey != buffSource }
    when (id) {
        SkillId.NULL_SHROUD -> projectiles.removeMatchingStable { !it.hostile && it.procKey == WeaponRuntime.PROC_SKILL }
        SkillId.DECAY_SPORES -> for (index in enemies.indices) {
            val enemy = enemies[index]
            if (enemy.status[StatusCell.MARK_CAST] > 0f) consumeMark(enemy, MarkKind.SPORED)
        }
        SkillId.SONIC_BOOM -> for (index in enemies.indices) consumeMark(enemies[index], MarkKind.BOOMED)
        else -> Unit
    }
    skillLoadout = skillLoadout.without(slot)
}

private fun MutableGameState.resetSkillSlot(slot: Int) {
    val runtime = skillRuntime
    for (cell in 0 until SkillCell.STRIDE) runtime[slot, cell] = 0f
    runtime[slot, SkillCell.TARGET_ID] = -1f
    runtime[slot, SkillCell.PENDING_AT] = -1f
    runtime[slot, SkillCell.GHOST_AT] = -1f
    runtime[slot, SkillCell.REFUSED_AT] = -1f
    runtime[slot, SkillCell.READY_AT] = elapsed
    runtime[slot, SkillCell.RANKED_AT_LEVEL] = level.toFloat()
    // A request buffered for what the slot held before never casts what it holds now.
    skillBufferMask = skillBufferMask and (1 shl slot).inv()
    if (skillBufferMask == 0) skillBufferTime = 0f
}

/** True when an unowned Catalyze may be offered: at least [CATALYZE_MIN_ELEMENTS] elements are in play. */
internal fun MutableGameState.catalyzeOfferable(): Boolean =
    StatusElement.entries.count { build.affinity(it) >= 1 } >= CATALYZE_MIN_ELEMENTS
