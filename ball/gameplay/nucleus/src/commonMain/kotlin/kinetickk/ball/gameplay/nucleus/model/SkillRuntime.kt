// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.SkillId

/**
 * Cells of one skill slot in [SkillRuntime]. Times are run seconds; ids are stored as exact floats. What a
 * slot holds (skill and rank) lives in the immutable [SkillLoadout]; the runtime keeps only timing and the
 * per-cast state a slot's effect needs.
 */
internal object SkillCell {
    /** When the slot may cast again. */
    const val READY_AT = 0
    /** When the slot last cast. */
    const val CAST_AT = 1
    /** The cooldown the last cast set, after every scale and the recast floor. */
    const val LAST_COOLDOWN = 2
    /** Cooldown seconds refunded since the last cast (bounded by the refund bank). */
    const val REFUNDED = 3
    /** Charges held (a second charge comes with the SkillCharges rule). */
    const val CHARGES = 4
    /** End of a running effect (Mass Driver, Null Shroud, Slingshot, Dual Infusion). */
    const val ACTIVE_UNTIL = 5
    /** Where a delayed impact lands (Meteor) or detonates (Void Step's rift). */
    const val ANCHOR_X = 6
    const val ANCHOR_Y = 7
    /** When the delayed impact resolves; negative when none is pending. */
    const val PENDING_AT = 8
    /** A per-cast store: bolts swallowed (Null Shroud), weapon hits counted (Dual Infusion), Data dropped (Spores). */
    const val STORED = 9
    /** The enemy a cast is tied to (Arc Hook's target), or -1. */
    const val TARGET_ID = 10
    /** End of the target window (Arc Hook's Dash-hit window). */
    const val TARGET_UNTIL = 11
    /** End of a secondary window (Frost Snap's coiled Dash, Arc Hook's kill window). */
    const val AUX_UNTIL = 12
    /** The run level at which the slot was filled or last ranked up. */
    const val RANKED_AT_LEVEL = 13
    /** Successful casts. */
    const val CAST_COUNT = 14
    /** When the Spiral's ghost repeats the last cast; negative when none is pending. */
    const val GHOST_AT = 15
    /** A per-cast counter: stacks applied in the current second (Dual Infusion), seconds refunded (Spores). */
    const val AUX_COUNT = 16
    /** Start of the counted second (Dual Infusion). */
    const val AUX_AT = 17
    /** The last refusal shown ([kinetickk.ball.gameplay.nucleus.render.SkillSlotProjection] codes) and when. */
    const val REFUSAL = 18
    const val REFUSED_AT = 19
    /** Damage and radius the Spiral's ghost repeats. */
    const val GHOST_DAMAGE = 20
    const val GHOST_RADIUS = 21
    /**
     * Skill damage the slot dealt since it was last filled (each instance up to the integrity it removed); a replaced
     * active item's damage stays in [SkillRuntime.effectDamage].
     */
    const val DAMAGE = 22
    /** With Skill Charges, when the second spent charge finishes recharging (each charge has its own clock); -1 = none. */
    const val CHARGE_READY_AT = 23
    const val STRIDE = 24
}

/** Cells of the character's Apex in [SkillRuntime] (the Apex system fills them). */
internal object ApexCell {
    const val UNLOCKED = 0
    const val READY_AT = 1
    const val ACTIVE_UNTIL = 2
    const val LAST_CAST = 3
    const val REFUNDED = 4
    const val STRIKES_LEFT = 5
    const val BOLTS_LEFT = 6
    const val HELD = 7
    const val HELD_RELEASE_AT = 8
    const val BOSS_BASE_READY_AT = 9
    const val CAST_COUNT = 10
    const val AUX = 11
    const val STRIDE = 12
}

/**
 * Timing and per-cast state of the three skill slots and the Apex, then the run's skill damage per active effect: one
 * fixed copy-on-write float block ([ContentBounds.SKILL_SLOTS] × [SkillCell.STRIDE] + [ApexCell.STRIDE] +
 * [ContentBounds.MAX_SKILLS]). A full reduction forks it (the block is shared until the candidate first writes it), a
 * scalar-input reduction shares it, a new run replaces it. A step allocates nothing.
 */
internal class SkillRuntime private constructor(
    private val cells: CopyOnWriteFloatArray,
) {
    constructor() : this(CopyOnWriteFloatArray(initialCells()))

    /** Skill damage dealt this run (each instance up to the integrity it removed). */
    var skillDamage: Double = 0.0
    /** Damage the Apex dealt to boss-class enemies this run. */
    var apexBossDamage: Double = 0.0
    /**
     * Damage the active effects caused this run (each instance up to the integrity it removed): their own hits,
     * whatever a cast resolves (forced reactions, a cast's shockwave) and the Core's impacts while a skill moves it
     * (Slingshot's swing, Mass Driver's charge, Arc Hook's flight and Dash window). It contains [skillDamage].
     */
    var skillCausedDamage: Double = 0.0

    operator fun get(slot: Int, cell: Int): Float = cells[slot * SkillCell.STRIDE + cell]

    operator fun set(slot: Int, cell: Int, value: Float) {
        cells[slot * SkillCell.STRIDE + cell] = value
    }

    fun apex(cell: Int): Float = cells[APEX_OFFSET + cell]

    fun setApex(cell: Int, value: Float) {
        cells[APEX_OFFSET + cell] = value
    }

    /**
     * Skill damage [id] dealt this run from whichever slot held it (each instance up to the integrity it removed). Unlike
     * a slot's [SkillCell.DAMAGE] it outlives a replacement, so a replaced active item keeps the damage it caused.
     */
    fun effectDamage(id: SkillId): Float = cells[EFFECT_DAMAGE_OFFSET + id.ordinal]

    fun addEffectDamage(id: SkillId, dealt: Float) {
        val index = EFFECT_DAMAGE_OFFSET + id.ordinal
        cells[index] = cells[index] + dealt
    }

    /** Shifts every stored slot position after a world rebase. */
    fun rebase(shiftX: Float, shiftY: Float) {
        for (slot in 0 until SLOTS) {
            set(slot, SkillCell.ANCHOR_X, get(slot, SkillCell.ANCHOR_X) - shiftX)
            set(slot, SkillCell.ANCHOR_Y, get(slot, SkillCell.ANCHOR_Y) - shiftY)
        }
    }

    /** An isolated candidate: metrics copied, cells shared until the first changed write. */
    fun fork(): SkillRuntime = SkillRuntime(cells.fork()).also { copy ->
        copy.skillDamage = skillDamage
        copy.apexBossDamage = apexBossDamage
        copy.skillCausedDamage = skillCausedDamage
    }

    fun sharesStorageWith(other: SkillRuntime): Boolean = cells.sharesStorageWith(other.cells)

    /** Every retained fact in a stable order (state witnesses and diagnostics). */
    override fun toString(): String =
        "SkillRuntime(skillDamage=$skillDamage, skillCausedDamage=$skillCausedDamage, apexBossDamage=$apexBossDamage, " +
            "cells=${cells.toList()})"

    companion object {
        const val SLOTS: Int = ContentBounds.SKILL_SLOTS
        const val CELL_COUNT: Int = SLOTS * SkillCell.STRIDE + ApexCell.STRIDE + ContentBounds.MAX_SKILLS
        private const val APEX_OFFSET: Int = SLOTS * SkillCell.STRIDE
        private const val EFFECT_DAMAGE_OFFSET: Int = APEX_OFFSET + ApexCell.STRIDE

        private fun initialCells(): FloatArray = FloatArray(CELL_COUNT).also { values ->
            for (slot in 0 until SLOTS) {
                val base = slot * SkillCell.STRIDE
                values[base + SkillCell.TARGET_ID] = -1f
                values[base + SkillCell.PENDING_AT] = -1f
                values[base + SkillCell.GHOST_AT] = -1f
                values[base + SkillCell.REFUSED_AT] = -1f
            }
        }
    }
}

/**
 * What the three slots hold: the active effect ([SkillId]) of an owned active item and its rank
 * (1..[ContentBounds.MAX_SKILL_RANK], the item's stacks) per slot, or an empty slot. Immutable: acquiring, stacking or
 * replacing an active item replaces it, and nothing else writes it.
 */
internal class SkillLoadout private constructor(
    private val skills: IntArray,
    private val ranks: IntArray,
) {
    fun skill(slot: Int): SkillId? = SkillId.entries.getOrNull(skills[slot])

    /** The slot's rank, 0 when the slot is empty. */
    fun rank(slot: Int): Int = ranks[slot]

    fun isEmpty(slot: Int): Boolean = skills[slot] < 0

    fun owns(id: SkillId): Boolean = slotOf(id) >= 0

    /** The slot holding [id], or -1. */
    fun slotOf(id: SkillId): Int {
        for (slot in skills.indices) if (skills[slot] == id.ordinal) return slot
        return -1
    }

    val ownedCount: Int
        get() = skills.count { it >= 0 }

    /** The lowest empty slot, or -1 when all are filled. */
    fun lowestEmptySlot(): Int {
        for (slot in skills.indices) if (skills[slot] < 0) return slot
        return -1
    }

    /** The loadout with [slot] holding [id] at [rank]. */
    fun with(slot: Int, id: SkillId, rank: Int): SkillLoadout {
        require(slot in skills.indices) { "skill slot $slot is outside 0..${skills.lastIndex}" }
        require(rank in 1..ContentBounds.MAX_SKILL_RANK) { "skill rank $rank is outside 1..${ContentBounds.MAX_SKILL_RANK}" }
        val other = slotOf(id)
        require(other < 0 || other == slot) { "$id already fills slot $other" }
        return SkillLoadout(
            skills.copyOf().also { it[slot] = id.ordinal },
            ranks.copyOf().also { it[slot] = rank },
        )
    }

    /** The loadout with [slot] empty. */
    fun without(slot: Int): SkillLoadout {
        require(slot in skills.indices) { "skill slot $slot is outside 0..${skills.lastIndex}" }
        return SkillLoadout(skills.copyOf().also { it[slot] = -1 }, ranks.copyOf().also { it[slot] = 0 })
    }

    override fun equals(other: Any?): Boolean =
        other is SkillLoadout && skills.contentEquals(other.skills) && ranks.contentEquals(other.ranks)

    override fun hashCode(): Int = skills.contentHashCode() * 31 + ranks.contentHashCode()

    override fun toString(): String = "SkillLoadout(" +
        skills.indices.joinToString { slot -> skill(slot)?.let { "$it ${ranks[slot]}" } ?: "-" } + ")"

    companion object {
        val EMPTY: SkillLoadout = SkillLoadout(IntArray(ContentBounds.SKILL_SLOTS) { -1 }, IntArray(ContentBounds.SKILL_SLOTS))
    }
}
