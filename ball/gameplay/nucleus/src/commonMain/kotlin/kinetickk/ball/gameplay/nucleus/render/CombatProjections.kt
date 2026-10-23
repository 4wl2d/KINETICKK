// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.render

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.SkillId
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOfSize

/** Lingering combat field kinds as renderers see them. Append-only; ordinals follow the run's field kinds. */
enum class CombatFieldVisual {
    FUSION,
    ROT_WELL,
    DARK_STAR,
    ECHO_RING,
    NULL_WELL,
    PLASMA,
    HUSH,
    TIME_BUBBLE,
    ATTRACTOR,
    FROST_LINE,
    STORM_COIL,
    GRAVITY_WELL,
}

/** What created a combat field. */
enum class CombatFieldSource { REACTION, ITEM, SKILL }

/**
 * A lingering field from committed run state. ([x2], [y2]) is a segment's far end (Frost Line), else
 * equal to ([x], [y]). [element] is a status element ordinal and [reaction] a reaction ordinal, −1 when
 * none. Age and remaining life come from [createdAt]/[expiresAt] against the model's elapsed time, so
 * ticks do not rebuild the list. [charge01] fills with the kind's counter; [anchorId] is the enemy an
 * attractor follows (−1 none); [followsCore] fields sit on the Core.
 */
data class CombatFieldProjection(
    val kind: CombatFieldVisual,
    val source: CombatFieldSource,
    val x: Float,
    val y: Float,
    val x2: Float = x,
    val y2: Float = y,
    val radius: Float,
    val element: Int = -1,
    val reaction: Int = -1,
    val createdAt: Float,
    val expiresAt: Float,
    val charge01: Float = 0f,
    val anchorId: Int = -1,
    val followsCore: Boolean = false,
)

/**
 * One skill slot. [skill] is a [SkillId] ordinal or −1 for an empty slot; [rank] 1..3 (0 when empty).
 * [cooldown01] is the remaining share of the last cooldown, [active01] and [pending01] the remaining
 * share of a running effect and of a delayed impact at ([anchorX], [anchorY]). [refunded] is the
 * cooldown seconds refunded this cycle; [refusal] is one of the `REFUSAL_` codes, last set at
 * [refusedAt] (model elapsed seconds, −1 never).
 */
data class SkillSlotProjection(
    val skill: Int = -1,
    val rank: Int = 0,
    val cooldown01: Float = 0f,
    val ready: Boolean = false,
    val charges: Int = 0,
    val maxCharges: Int = 1,
    val active01: Float = 0f,
    val pending01: Float = 0f,
    val anchorX: Float = 0f,
    val anchorY: Float = 0f,
    val refunded: Float = 0f,
    val refusal: Int = REFUSAL_NONE,
    val refusedAt: Float = -1f,
) {
    val isEmpty: Boolean get() = skill < 0

    val skillId: SkillId? get() = SkillId.entries.getOrNull(skill)

    companion object {
        const val REFUSAL_NONE: Int = 0
        const val REFUSAL_COOLING: Int = 1
        const val REFUSAL_NO_TARGET: Int = 2

        val EMPTY: SkillSlotProjection = SkillSlotProjection()
    }
}

/** The Z, X and C slots in order. */
data class SkillBarProjection(
    val slots: ImmutableList<SkillSlotProjection>,
) {
    init {
        require(slots.size == ContentBounds.SKILL_SLOTS) { "A skill bar shows exactly ${ContentBounds.SKILL_SLOTS} slots" }
    }

    fun slot(index: Int): SkillSlotProjection = slots[index]

    companion object {
        /** No skill drafted yet. */
        val EMPTY: SkillBarProjection = SkillBarProjection(
            immutableListOfSize(ContentBounds.SKILL_SLOTS) { SkillSlotProjection.EMPTY },
        )
    }
}

/**
 * The character's Apex: [unlocked] at the level cap, [ready] to cast, the remaining cooldown and active
 * shares, the boss strikes and hostile-bolt erasures left in the running activation, shots [held] for a
 * later release, the cooldown seconds Data has [refunded] this cycle and that refund as a 0..1 charge.
 */
data class ApexProjection(
    val unlocked: Boolean = false,
    val ready: Boolean = false,
    val cooldown01: Float = 0f,
    val active01: Float = 0f,
    val strikesLeft: Int = 0,
    val boltsLeft: Int = 0,
    val held: Int = 0,
    val refunded: Float = 0f,
    val dataCharge01: Float = 0f,
) {
    companion object {
        /** Below the level cap. */
        val LOCKED: ApexProjection = ApexProjection()
    }
}

/**
 * Lingering states on the Core from reactions and skills: remaining shares of Ablaze, Frictionless and
 * Arc Weave, a ready Blast Jump, orbiting guard shards (angles derive from elapsed time), bolts stored by
 * Null Shroud, the Void Step phase, primed and coiled Dashes and one bit per [CombatFieldVisual] ordinal
 * the Core stands in.
 */
data class CoreStateProjection(
    val ablaze01: Float = 0f,
    val frictionless01: Float = 0f,
    val blastJumpReady: Boolean = false,
    val weave01: Float = 0f,
    val guardShards: Int = 0,
    val shroudStored: Int = 0,
    val phased: Boolean = false,
    val primedDash: Boolean = false,
    val coiledDash: Boolean = false,
    val inFieldMask: Int = 0,
) {
    companion object {
        /** No lingering Core state. */
        val NEUTRAL: CoreStateProjection = CoreStateProjection()
    }
}

/** Bits of [EnemyProjection.controlBits]. */
object EnemyControlBits {
    const val STOPPED: Int = 1
    const val SILENCED: Int = 1 shl 1
    const val THROWN: Int = 1 shl 2
    const val BLACKFIRE: Int = 1 shl 3
    const val UNMAKE_PENDING: Int = 1 shl 4
    /** Bits 5..7 hold the mark kind ordinal plus one (0 = no mark). */
    const val MARK_SHIFT: Int = 5
    const val MARK_MASK: Int = 0b111 shl MARK_SHIFT
    const val VOID_ANCHOR: Int = 1 shl 8
    const val ATTRACTOR_ANCHOR: Int = 1 shl 9
    const val APEX_STRIKE_TARGET: Int = 1 shl 10

    /** The mark kind ordinal, or −1 for no mark. */
    fun markKind(bits: Int): Int = ((bits and MARK_MASK) ushr MARK_SHIFT) - 1

    /** The mark, or null for no mark. */
    fun mark(bits: Int): EnemyMarkVisual? = EnemyMarkVisual.entries.getOrNull(markKind(bits))
}

/** An enemy's one mark as renderers see it. Append-only; ordinals follow the run's mark kinds. */
enum class EnemyMarkVisual { SPALL, CRACKED, SPORED, BOOMED, SUPPRESSED }
