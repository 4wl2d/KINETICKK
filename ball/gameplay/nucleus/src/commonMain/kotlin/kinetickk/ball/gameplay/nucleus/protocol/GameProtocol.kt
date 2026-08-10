// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.protocol

import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.foundation.collections.ImmutableList

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
