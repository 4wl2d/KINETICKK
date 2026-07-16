// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.game.nucleus.protocol

import kinetickk.features.game.nucleus.ParticleDensity
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

object VisualFxCueLimits {
    /** Includes one slot reserved for explicit drop metadata. */
    const val MAX_CUES_PER_PROJECTION = 2_048
}

/**
 * Closed, bounded visual notifications emitted by the authoritative Nucleus.
 *
 * These values describe presentation consequences only. They never feed back
 * into gameplay decisions and carry no behavior-authoritative random result.
 */
sealed interface VisualFxCue {
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

    data class DamageNumberAdded(
        val x: Float,
        val y: Float,
        val amount: Long,
        val critical: Boolean,
    ) : VisualFxCue

    data class WeaponArcAdded(
        val fromX: Float,
        val fromY: Float,
        val toX: Float,
        val toY: Float,
        val life: Float,
    ) : VisualFxCue

    /** Preserves the baseline policy: only particles and damage text rebase. */
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
 * one-Decision drain long enough to fill the batch with lifecycle cues, the
 * oldest visual cue is displaced as a drop-eligible fallback. The final slot is
 * reserved for explicit drop metadata, so gameplay acceptance never depends on
 * an unbounded number of cosmetic events.
 */
internal class BoundedVisualFxCueAccumulator private constructor(
    private val cues: MutableList<VisualFxCue>,
    private var droppedVisualCueCount: Int,
) {
    constructor() : this(mutableListOf(), 0)

    fun record(cue: VisualFxCue) {
        if (cues.size < MAX_RETAINED_CUES) {
            cues += cue
            return
        }
        if (cue.isSynchronizationCritical()) {
            val replaceIndex = cues.indexOfFirst { retained ->
                !retained.isSynchronizationCritical()
            }.takeIf { it >= 0 } ?: 0
            cues.removeAt(replaceIndex)
            recordDrop()
            cues += cue
        } else {
            recordDrop()
        }
    }

    fun drain(): ImmutableList<VisualFxCue> {
        val result = buildList {
            addAll(cues)
            if (droppedVisualCueCount > 0) {
                add(VisualFxCue.VisualCuesDropped(droppedVisualCueCount))
            }
        }.toImmutableList()
        cues.clear()
        droppedVisualCueCount = 0
        return result
    }

    fun copy(): BoundedVisualFxCueAccumulator = BoundedVisualFxCueAccumulator(
        cues = cues.toMutableList(),
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
        is VisualFxCue.Burst,
        is VisualFxCue.DirectionalBurst,
        is VisualFxCue.ShockwaveAdded,
        is VisualFxCue.DamageNumberAdded,
        is VisualFxCue.WeaponArcAdded,
        -> false
    }

    private companion object {
        const val MAX_RETAINED_CUES = VisualFxCueLimits.MAX_CUES_PER_PROJECTION - 1
    }
}
