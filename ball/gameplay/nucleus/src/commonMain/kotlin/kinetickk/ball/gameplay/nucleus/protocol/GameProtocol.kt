// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.protocol

import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

/** Simulation consequences awaiting target-owned correlation and accepted-frame publication. */
internal sealed interface SimulationOutput {
    data class AdvanceAudio(
        val realDeltaSeconds: Float,
        val cues: ImmutableList<GameplayAudioCue>,
    ) : SimulationOutput

    data object EnsureAudioUnlocked : SimulationOutput

    data class PublishProgress(
        val update: GameplayProgressUpdate,
    ) : SimulationOutput

    data class EmitVisualFx(
        val cues: ImmutableList<VisualFxCue>,
    ) : SimulationOutput
}

/**
 * Fixed-cardinality immutable storage for canonical simulation consequences.
 *
 * Production mapping reads the typed slots directly, so a reduction allocates neither a generic
 * list/backing array nor transient [SimulationOutput] wrappers. The read-only list view preserves
 * exhaustive protocol inspection for characterization tests and diagnostics; wrappers are created
 * only when that view is explicitly indexed or iterated.
 */
internal class SimulationOutputs private constructor(
    internal val visualFxCuesOrNull: ImmutableList<VisualFxCue>?,
    internal val progressUpdate: GameplayProgressUpdate?,
    internal val audioRealDeltaSeconds: Float,
    internal val audioCuesOrNull: ImmutableList<GameplayAudioCue>?,
    internal val ensuresAudioUnlocked: Boolean,
) : AbstractList<SimulationOutput>() {
    override val size: Int
        get() =
            (if (visualFxCuesOrNull == null) 0 else 1) +
                (if (progressUpdate == null) 0 else 1) +
                (if (audioCuesOrNull == null && !ensuresAudioUnlocked) 0 else 1)

    override fun get(index: Int): SimulationOutput {
        if (index !in indices) throw IndexOutOfBoundsException("index: $index, size: $size")
        var remainingIndex = index
        visualFxCuesOrNull?.let { cues ->
            if (remainingIndex == 0) return SimulationOutput.EmitVisualFx(cues)
            remainingIndex--
        }
        progressUpdate?.let { update ->
            if (remainingIndex == 0) return SimulationOutput.PublishProgress(update)
            remainingIndex--
        }
        audioCuesOrNull?.let { cues ->
            if (remainingIndex == 0) {
                return SimulationOutput.AdvanceAudio(audioRealDeltaSeconds, cues)
            }
            remainingIndex--
        }
        if (ensuresAudioUnlocked && remainingIndex == 0) {
            return SimulationOutput.EnsureAudioUnlocked
        }
        error("Canonical Simulation output index was not resolved")
    }

    companion object {
        val Empty: SimulationOutputs = SimulationOutputs(
            visualFxCuesOrNull = null,
            progressUpdate = null,
            audioRealDeltaSeconds = 0f,
            audioCuesOrNull = null,
            ensuresAudioUnlocked = false,
        )

        val EnsureAudioUnlocked: SimulationOutputs = SimulationOutputs(
            visualFxCuesOrNull = null,
            progressUpdate = null,
            audioRealDeltaSeconds = 0f,
            audioCuesOrNull = null,
            ensuresAudioUnlocked = true,
        )

        fun create(
            visualFxCues: ImmutableList<VisualFxCue> = immutableListOf(),
            progressUpdate: GameplayProgressUpdate? = null,
            advanceAudio: Boolean = false,
            audioRealDeltaSeconds: Float = 0f,
            audioCues: ImmutableList<GameplayAudioCue> = immutableListOf(),
        ): SimulationOutputs {
            require(advanceAudio || audioCues.isEmpty()) {
                "Simulation audio cues require an AdvanceAudio consequence"
            }
            require(advanceAudio || audioRealDeltaSeconds.toRawBits() == 0f.toRawBits()) {
                "Simulation audio delta requires an AdvanceAudio consequence"
            }
            val retainedVisualFxCues = visualFxCues.takeIf { it.isNotEmpty() }
            val retainedAudioCues = audioCues.takeIf { advanceAudio }
            if (
                retainedVisualFxCues == null &&
                progressUpdate == null &&
                retainedAudioCues == null
            ) {
                return Empty
            }
            return SimulationOutputs(
                visualFxCuesOrNull = retainedVisualFxCues,
                progressUpdate = progressUpdate,
                audioRealDeltaSeconds = if (advanceAudio) audioRealDeltaSeconds else 0f,
                audioCuesOrNull = retainedAudioCues,
                ensuresAudioUnlocked = false,
            )
        }
    }
}
