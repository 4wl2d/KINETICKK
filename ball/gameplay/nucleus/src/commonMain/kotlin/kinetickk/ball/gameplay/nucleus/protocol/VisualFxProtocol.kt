// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.protocol

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableListAppending
import kinetickk.ball.profile.api.ParticleDensity

object VisualFxCueLimits {
    /** Includes one slot reserved for explicit drop metadata. */
    const val MAX_CUES_PER_PROJECTION = 2_048
}

/**
 * Look of a transient link ([VisualFxCue.WeaponArcAdded.style]): [WEAPON] for weapons and items, then one
 * code per reaction, skill, element Break chain and character Apex.
 */
object LinkStyleCode {
    const val WEAPON: Int = 0
    const val REACTION_BASE: Int = 1
    const val SKILL_BASE: Int = 32
    const val BREAK_BASE: Int = 64
    const val APEX_BASE: Int = 96

    fun reaction(id: ReactionId): Int = REACTION_BASE + id.ordinal
    fun skill(id: SkillId): Int = SKILL_BASE + id.ordinal
    fun elementBreak(element: StatusElement): Int = BREAK_BASE + element.ordinal
    fun apex(shape: CoreShape): Int = APEX_BASE + shape.ordinal
}

/** Bits of [VisualFxCue.ReactionTriggered.flags]. */
object ReactionCueFlags {
    /** The reaction's Core or play-style branch fired. */
    const val VERB: Int = 1
    /** A keystone rewrote the reaction. */
    const val KEYSTONE: Int = 1 shl 1
    /** A replay of an earlier reaction (no stacks consumed). */
    const val ECHO: Int = 1 shl 2
    /** Set off by another reaction (generation 1 or later). */
    const val CASCADE: Int = 1 shl 3
}

/** [VisualFxCue.SkillCast.phase] values and [VisualFxCue.SkillCast.flags] bits. */
object SkillCueCodes {
    const val PHASE_CAST: Int = 0
    /** The delayed landing, rift, release or collapse of an earlier cast. */
    const val PHASE_IMPACT: Int = 1
    /** The Core stood inside the effect. */
    const val FLAG_CORE_INSIDE: Int = 1
}

/**
 * Colour indices of [VisualFxCue.ShockwaveAdded], [VisualFxCue.Burst] and projectile colours past the base
 * looks (0 and 3 you, 1 Bone, 2 Bone2, 4 threat, which stays reserved for damage the player takes): an
 * element-tone ring per status element, the Apex gold, and a jagged quake ring per element.
 */
object FxColorCode {
    const val ELEMENT_TONE_BASE: Int = 8
    const val APEX_GOLD: Int = 15
    const val QUAKE_BASE: Int = 16

    fun elementTone(element: StatusElement): Int = ELEMENT_TONE_BASE + element.ordinal
    fun quake(element: StatusElement): Int = QUAKE_BASE + element.ordinal
}

/**
 * Closed, bounded visual notifications emitted by the authoritative reducer.
 *
 * These values describe presentation consequences only. They never feed back
 * into gameplay reductions and carry no behavior-authoritative random result.
 */
sealed interface VisualFxCue {
    /** A grouped acquisition result, produced only by an accepted build transition. */
    data class BuildChanged(val title: String, val details: ImmutableList<String>) : VisualFxCue

    data object ClearAll : VisualFxCue
    data object ClearWeaponArcs : VisualFxCue

    data class MotionSample(
        val deltaSeconds: Float,
        val previousCoreX: Float,
        val previousCoreY: Float,
        val speed: Float,
        val dashPhaseTime: Float,
    ) : VisualFxCue

    data class EffectsAdvanced(
        val deltaSeconds: Float,
    ) : VisualFxCue

    data class WeaponArcsAdvanced(
        val deltaSeconds: Float,
    ) : VisualFxCue

    data class Burst(
        val x: Float,
        val y: Float,
        val requestedCount: Int,
        val colorIndex: Int,
        val density: ParticleDensity,
    ) : VisualFxCue

    data class DirectionalBurst(
        val x: Float,
        val y: Float,
        val requestedCount: Int,
        val colorIndex: Int,
        val directionX: Float,
        val directionY: Float,
        val density: ParticleDensity,
    ) : VisualFxCue

    data class ShockwaveAdded(
        val x: Float,
        val y: Float,
        val life: Float,
        val maxRadius: Float,
        val colorIndex: Int,
    ) : VisualFxCue

    /** [tone] is 0 for Kinetic damage, otherwise 1 + the status element ordinal. */
    data class DamageNumberAdded(
        val x: Float,
        val y: Float,
        val amount: Long,
        val critical: Boolean,
        val tone: Int = 0,
    ) : VisualFxCue

    /** A status Break (Ignite, Freeze, Overload, Collapse, Rupture) at a world point. */
    data class StatusBroken(
        val x: Float,
        val y: Float,
        val element: Int,
    ) : VisualFxCue

    /**
     * An elemental reaction at a world point; [firstDiscovery] marks the profile's first occurrence (never in a Baby mode
     * run, which records no discovery).
     * [direction] is a heading in radians for directional looks (NaN when unused); [flags] holds
     * [ReactionCueFlags] bits.
     */
    data class ReactionTriggered(
        val x: Float,
        val y: Float,
        val reaction: Int,
        val radius: Float,
        val firstDiscovery: Boolean,
        val direction: Float = Float.NaN,
        val flags: Int = 0,
    ) : VisualFxCue

    /** A transient link; [style] is a [LinkStyleCode]. */
    data class WeaponArcAdded(
        val fromX: Float,
        val fromY: Float,
        val toX: Float,
        val toY: Float,
        val life: Float,
        val style: Int = LinkStyleCode.WEAPON,
    ) : VisualFxCue

    /**
     * A skill cast ([phase] [SkillCueCodes.PHASE_CAST]) or its delayed impact at ([x], [y]) toward
     * ([dirX], [dirY]) with the effect [radius] at [rank] 1..3; [flags] holds [SkillCueCodes] bits.
     */
    data class SkillCast(
        val skill: SkillId,
        val x: Float,
        val y: Float,
        val dirX: Float,
        val dirY: Float,
        val radius: Float,
        val rank: Int,
        val phase: Int = SkillCueCodes.PHASE_CAST,
        val flags: Int = 0,
    ) : VisualFxCue

    /** The character's Apex started at the Core's position. */
    data class ApexActivated(
        val shape: CoreShape,
        val x: Float,
        val y: Float,
    ) : VisualFxCue

    /** Particles, damage text and Break and reaction flashes rebase; shockwaves stay in place. */
    data class WorldRebased(
        val shiftX: Float,
        val shiftY: Float,
    ) : VisualFxCue

    /** Number of drop-eligible visual cues omitted before source acceptance. */
    data class VisualCuesDropped(
        val count: Int,
    ) : VisualFxCue
}

/**
 * Bounded accumulator with a deterministic drop policy for cosmetic deltas.
 *
 * Lifecycle, advancement, clear, and rebase cues are synchronization-critical;
 * they first displace the oldest decorative cue. If a caller bypasses the normal
 * one-reduction drain long enough to fill the batch with lifecycle cues, the
 * oldest visual cue is displaced as a drop-eligible fallback. The final slot is
 * reserved for explicit drop metadata, so gameplay acceptance never depends on
 * an unbounded number of cosmetic events.
 */
internal class BoundedVisualFxCueAccumulator private constructor(
    private var cues: MutableList<VisualFxCue>?,
    private var droppedVisualCueCount: Int,
) {
    constructor() : this(null, 0)

    fun record(cue: VisualFxCue) {
        val retainedCues = cues
        if (retainedCues == null) {
            cues = arrayListOf(cue)
            return
        }
        if (retainedCues.size < MAX_RETAINED_CUES) {
            retainedCues += cue
            return
        }
        if (cue.isSynchronizationCritical()) {
            val replaceIndex = retainedCues.indexOfFirst { retained ->
                !retained.isSynchronizationCritical()
            }.takeIf { it >= 0 } ?: 0
            retainedCues.removeAt(replaceIndex)
            recordDrop()
            retainedCues += cue
        } else {
            recordDrop()
        }
    }

    fun drain(): ImmutableList<VisualFxCue> {
        val retainedCues = cues
        val result = if (droppedVisualCueCount > 0) {
            retainedCues.orEmpty().toImmutableListAppending(
                VisualFxCue.VisualCuesDropped(droppedVisualCueCount),
            )
        } else if (retainedCues == null) {
            immutableListOf()
        } else {
            retainedCues.toImmutableList()
        }
        cues = null
        droppedVisualCueCount = 0
        return result
    }

    fun isEmpty(): Boolean = cues == null && droppedVisualCueCount == 0

    fun copy(): BoundedVisualFxCueAccumulator = BoundedVisualFxCueAccumulator(
        cues = cues?.toMutableList(),
        droppedVisualCueCount = droppedVisualCueCount,
    )

    private fun recordDrop() {
        if (droppedVisualCueCount < Int.MAX_VALUE) {
            droppedVisualCueCount++
        }
    }

    private fun VisualFxCue.isSynchronizationCritical(): Boolean = when (this) {
        VisualFxCue.ClearAll,
        VisualFxCue.ClearWeaponArcs,
        is VisualFxCue.MotionSample,
        is VisualFxCue.EffectsAdvanced,
        is VisualFxCue.WeaponArcsAdvanced,
        is VisualFxCue.WorldRebased,
        is VisualFxCue.VisualCuesDropped,
        -> true
        is VisualFxCue.BuildChanged,
        is VisualFxCue.Burst,
        is VisualFxCue.DirectionalBurst,
        is VisualFxCue.ShockwaveAdded,
        is VisualFxCue.DamageNumberAdded,
        is VisualFxCue.StatusBroken,
        is VisualFxCue.ReactionTriggered,
        is VisualFxCue.WeaponArcAdded,
        is VisualFxCue.SkillCast,
        is VisualFxCue.ApexActivated,
        -> false
    }

    private companion object {
        const val MAX_RETAINED_CUES = VisualFxCueLimits.MAX_CUES_PER_PROJECTION - 1
    }
}
