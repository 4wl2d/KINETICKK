// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.projection

import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillId
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableListOfSize
import kinetickk.foundation.collections.ImmutableList
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.skills.*

/**
 * The Apex from its runtime cells: the shared [ApexProjection.LOCKED] below the level cap; an unchanged projection
 * keeps the previous instance. Strikes and bolts are those of the running activation (0 outside one).
 */
internal fun MutableGameState.apexProjection(previous: ApexProjection?): ApexProjection {
    if (!apexUnlocked) return ApexProjection.LOCKED
    val runtime = skillRuntime
    val definition = apexDefinition
    val readyAt = runtime.apex(ApexCell.READY_AT)
    val refunded = runtime.apex(ApexCell.REFUNDED)
    val ready = elapsed >= readyAt
    // The cast's cooldown: what it set, before refunds took seconds off.
    val cooldown = readyAt + refunded - runtime.apex(ApexCell.LAST_CAST)
    val active = apexActive()
    val refundable = definition.cooldownSeconds - definition.minimumCooldownSeconds
    val projection = ApexProjection(
        unlocked = true,
        ready = ready,
        cooldown01 = if (ready || cooldown <= 0f) 0f else ((readyAt - elapsed) / cooldown).coerceIn(0f, 1f),
        active01 = if (active) remainingShare(runtime.apex(ApexCell.ACTIVE_UNTIL), definition.durationSeconds) else 0f,
        strikesLeft = if (active) apexStrikesLeft else 0,
        boltsLeft = if (active) runtime.apex(ApexCell.BOLTS_LEFT).toInt() else 0,
        held = runtime.apex(ApexCell.HELD).toInt(),
        refunded = refunded,
        dataCharge01 = if (refundable > 0f) (refunded / refundable).coerceIn(0f, 1f) else 0f,
    )
    return if (projection == previous) checkNotNull(previous) else projection
}

/** The same table or equal records keep the previous projection. */
internal fun MutableGameState.combatFieldProjection(
    reusable: ImmutableList<CombatFieldProjection>?,
    identityTable: CombatFieldTable?,
): ImmutableList<CombatFieldProjection> {
    val table = combatFieldTable
    if (reusable != null) {
        if (table === identityTable) return reusable
        if (matches(reusable, table)) return reusable
    }
    if (table.count == 0) return immutableListOf()
    return immutableListOfSize(table.count) { index ->
        CombatFieldProjection(
            kind = CombatFieldVisual.entries[table.kind(index).ordinal],
            source = CombatFieldSource.entries[table.source(index).ordinal],
            x = table[index, FieldCell.X],
            y = table[index, FieldCell.Y],
            x2 = table[index, FieldCell.X2],
            y2 = table[index, FieldCell.Y2],
            radius = table[index, FieldCell.RADIUS],
            element = table[index, FieldCell.ELEMENT].toInt(),
            reaction = fieldReaction(table, index),
            createdAt = table[index, FieldCell.CREATED_AT],
            expiresAt = table[index, FieldCell.EXPIRES_AT],
            charge01 = fieldCharge01(table, index),
            anchorId = table[index, FieldCell.ANCHOR_ID].toInt(),
            followsCore = table.hasFlag(index, FieldFlags.FOLLOWS_CORE),
        )
    }
}

private fun fieldReaction(table: CombatFieldTable, index: Int): Int =
    if (table.source(index) == FieldSource.REACTION) table.owner(index) else -1

/**
 * The kind counter as a 0..1 fill: shots a Maw, a Time Bubble, Dead Air or a Frost Line ate of its budget, a Rot
 * Well's growths, a Resonator's echoes; 0 for the other kinds.
 */
private fun MutableGameState.fieldCharge01(table: CombatFieldTable, index: Int): Float {
    if (table.source(index) == FieldSource.SKILL) {
        val budget = when (table.kind(index)) {
            CombatFieldKind.FROST_LINE -> table[index, FieldCell.POWER]
            CombatFieldKind.ECHO_RING -> table[index, FieldCell.STACKS]
            else -> 0f
        }
        return if (budget <= 0f) 0f else (table[index, FieldCell.CHARGE] / budget).coerceIn(0f, 1f)
    }
    // An item zone fills like the reaction field it borrows (a maw's or a bubble's shot budget).
    val item = table.source(index) == FieldSource.ITEM
    val keystone = table.hasFlag(index, FieldFlags.KEYSTONE)
    val budget = when (table.kind(index)) {
        CombatFieldKind.NULL_WELL -> verbValueOrZero(ReactionId.NULL_WELL, MAW_SHOTS_VALUE)
        CombatFieldKind.TIME_BUBBLE -> verbValueOrZero(ReactionId.STASIS, BUBBLE_SHOTS_VALUE)
        CombatFieldKind.HUSH -> if (keystone) DEAD_AIR_SHOTS.toFloat() else 0f
        CombatFieldKind.ROT_WELL -> if (keystone || item) 0f else verbValueOrZero(ReactionId.ROT_WELL, COMPOST_GROWTHS_VALUE)
        else -> 0f
    }
    return if (budget <= 0f) 0f else (table[index, FieldCell.CHARGE] / budget).coerceIn(0f, 1f)
}

private fun MutableGameState.verbValueOrZero(reaction: ReactionId, index: Int): Float =
    content.build.reaction(reaction).values.getOrNull(index) ?: 0f

private fun MutableGameState.matches(projection: ImmutableList<CombatFieldProjection>, table: CombatFieldTable): Boolean {
    if (projection.size != table.count) return false
    for (index in 0 until projection.size) if (!projection[index].matches(this, table, index)) return false
    return true
}

private fun CombatFieldProjection.matches(state: MutableGameState, table: CombatFieldTable, index: Int): Boolean =
    kind.ordinal == table.kind(index).ordinal &&
        source.ordinal == table.source(index).ordinal &&
        x.sameBitsAs(table[index, FieldCell.X]) &&
        y.sameBitsAs(table[index, FieldCell.Y]) &&
        x2.sameBitsAs(table[index, FieldCell.X2]) &&
        y2.sameBitsAs(table[index, FieldCell.Y2]) &&
        radius.sameBitsAs(table[index, FieldCell.RADIUS]) &&
        element == table[index, FieldCell.ELEMENT].toInt() &&
        reaction == fieldReaction(table, index) &&
        createdAt.sameBitsAs(table[index, FieldCell.CREATED_AT]) &&
        expiresAt.sameBitsAs(table[index, FieldCell.EXPIRES_AT]) &&
        charge01.sameBitsAs(state.fieldCharge01(table, index)) &&
        anchorId == table[index, FieldCell.ANCHOR_ID].toInt() &&
        followsCore == table.hasFlag(index, FieldFlags.FOLLOWS_CORE)

/**
 * Lingering Core state from committed scalars. The remaining shares of Ablaze, Frictionless and Arc Weave
 * are measured against the duration their reaction grants (its keystone form included); with no such value
 * a live state shows full. The shared [CoreStateProjection.NEUTRAL] stands for no state at all.
 */
internal fun MutableGameState.coreStateProjection(previous: CoreStateProjection?): CoreStateProjection {
    val ablaze = remainingShare(ablazeUntil, if (hasVerb(ReactionId.WILDFIRE)) ablazeSeconds() else 0f)
    val frictionless = remainingShare(frictionlessUntil, verbValueOrZero(ReactionId.SUPERCONDUCT, FRICTIONLESS_SECONDS_VALUE))
    val weave = remainingShare(weaveUntil, if (hasVerb(ReactionId.RESONANT_ARC)) weaveSeconds() else 0f)
    val blastJumpReady = blastJumpUntil > elapsed
    val guard = if (guardUntil > elapsed) guardShards else 0
    val phased = phaseUntil > elapsed
    val primed = primedDashUntil > elapsed
    val stored = shroudStored()
    val coiled = dashCoiled()
    if (ablaze == 0f && frictionless == 0f && weave == 0f && !blastJumpReady && guard == 0 && !phased && !primed &&
        coreFieldMask == 0 && stored == 0 && !coiled
    ) return CoreStateProjection.NEUTRAL
    val projection = CoreStateProjection(
        ablaze01 = ablaze,
        frictionless01 = frictionless,
        blastJumpReady = blastJumpReady,
        weave01 = weave,
        guardShards = guard,
        shroudStored = stored,
        phased = phased,
        primedDash = primed,
        coiledDash = coiled,
        inFieldMask = coreFieldMask,
    )
    return if (projection == previous) previous else projection
}

/**
 * The three skill slots from the loadout and the skill runtime. The shared [SkillBarProjection.EMPTY] stands for
 * no active item owned; an unchanged bar keeps the previous instance.
 */
internal fun MutableGameState.skillBarProjection(previous: SkillBarProjection?): SkillBarProjection {
    val loadout = skillLoadout
    if (loadout.ownedCount == 0) return SkillBarProjection.EMPTY
    val slots = previous?.slots
    val first = skillSlotProjection(0, slots?.get(0))
    val second = skillSlotProjection(1, slots?.get(1))
    val third = skillSlotProjection(2, slots?.get(2))
    if (slots != null && first === slots[0] && second === slots[1] && third === slots[2]) return checkNotNull(previous)
    return SkillBarProjection(kinetickk.foundation.collections.immutableListOf(first, second, third))
}

private fun MutableGameState.skillSlotProjection(slot: Int, previous: SkillSlotProjection?): SkillSlotProjection {
    val id = skillLoadout.skill(slot) ?: return SkillSlotProjection.EMPTY
    val runtime = skillRuntime
    val rank = skillLoadout.rank(slot)
    val values = content.skill(id).rank(rank).values
    val readyAt = runtime[slot, SkillCell.READY_AT]
    val cooldown = runtime[slot, SkillCell.LAST_COOLDOWN]
    // With Skill Charges the slot shows its held charges; the cooldown then fills the next one.
    val charged = build.skillCharges
    val held = if (charged) runtime[slot, SkillCell.CHARGES].toInt().coerceIn(0, MAX_SKILL_CHARGES.toInt()) else 0
    val recharging = if (charged) held < MAX_SKILL_CHARGES.toInt() && elapsed < readyAt else elapsed < readyAt
    val ready = if (charged) held >= 1 else elapsed >= readyAt
    val activeSeconds = when (id) {
        SkillId.MASS_DRIVER, SkillId.NULL_SHROUD, SkillId.DUAL_INFUSION -> values[0]
        SkillId.SLINGSHOT -> values[SLINGSHOT_WINDOW_VALUE]
        else -> 0f
    }
    val pendingAt = runtime[slot, SkillCell.PENDING_AT]
    val pendingSeconds = when (id) {
        SkillId.METEOR -> values[METEOR_DELAY_VALUE]
        SkillId.VOID_STEP -> values[VOID_STEP_RIFT_DELAY_VALUE]
        else -> 0f
    }
    val pending = id != SkillId.NULL_SHROUD && pendingAt >= 0f && pendingAt > elapsed
    val cooldown01 = if (!recharging || cooldown <= 0f) 0f else ((readyAt - elapsed) / cooldown).coerceIn(0f, 1f)
    val charges = if (charged) held else if (ready) 1 else 0
    val maxCharges = if (charged) MAX_SKILL_CHARGES.toInt() else 1
    val active01 = if (activeSeconds > 0f) remainingShare(runtime[slot, SkillCell.ACTIVE_UNTIL], activeSeconds) else 0f
    val pending01 = if (pending && pendingSeconds > 0f) ((pendingAt - elapsed) / pendingSeconds).coerceIn(0f, 1f) else 0f
    val anchorX = if (pending) runtime[slot, SkillCell.ANCHOR_X] else 0f
    val anchorY = if (pending) runtime[slot, SkillCell.ANCHOR_Y] else 0f
    val refunded = runtime[slot, SkillCell.REFUNDED]
    val refusal = runtime[slot, SkillCell.REFUSAL].toInt()
    val refusedAt = runtime[slot, SkillCell.REFUSED_AT]
    // Match data-class Float equality, including signed zero and canonical NaN, before allocating a new slot.
    if (previous != null && previous.skill == id.ordinal && previous.rank == rank &&
        previous.ready == ready && previous.charges == charges && previous.maxCharges == maxCharges &&
        previous.cooldown01.toBits() == cooldown01.toBits() && previous.active01.toBits() == active01.toBits() &&
        previous.pending01.toBits() == pending01.toBits() && previous.anchorX.toBits() == anchorX.toBits() &&
        previous.anchorY.toBits() == anchorY.toBits() && previous.refunded.toBits() == refunded.toBits() &&
        previous.refusal == refusal && previous.refusedAt.toBits() == refusedAt.toBits()
    ) return previous
    return SkillSlotProjection(
        skill = id.ordinal,
        rank = rank,
        cooldown01 = cooldown01,
        ready = ready,
        charges = charges,
        maxCharges = maxCharges,
        active01 = active01,
        pending01 = pending01,
        anchorX = anchorX,
        anchorY = anchorY,
        refunded = refunded,
        refusal = refusal,
        refusedAt = refusedAt,
    )
}

/** Indices of the skill values the skill bar reads (the content order of `defaultSkillDefinitions`). */
private const val SLINGSHOT_WINDOW_VALUE: Int = 3
private const val METEOR_DELAY_VALUE: Int = 4
private const val VOID_STEP_RIFT_DELAY_VALUE: Int = 5

private fun MutableGameState.remainingShare(until: Float, seconds: Float): Float = when {
    until <= elapsed -> 0f
    seconds <= 0f -> 1f
    else -> ((until - elapsed) / seconds).coerceIn(0f, 1f)
}
