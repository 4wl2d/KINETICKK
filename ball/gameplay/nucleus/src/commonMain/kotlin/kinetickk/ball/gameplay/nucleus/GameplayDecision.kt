// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.gameplay.api.GameplaySettingsApplied
import kinetickk.ball.gameplay.api.GameplayRunStarted
import kinetickk.ball.gameplay.api.GameplayRunExited
import kinetickk.ball.profile.api.ProfileProgressApplied
import kinetickk.ball.profile.api.ProfileRefusal
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.ball.gameplay.api.GameplayOverlayPaused
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.foundation.collections.ImmutableList

const val MAX_GAMEPLAY_OUTPUTS_PER_DECISION: Int = 3

/** Sparse trusted read inputs; command, admission, and causal mechanics stay outside Context. */
data class GameplayContext(
    val start: GameplayStartContext? = null,
) {
    companion object {
        val Empty: GameplayContext = GameplayContext()
    }
}

sealed interface GameplayStartContext {
    data class Ready(val inputs: GameplayStartInputs) : GameplayStartContext
    data object ProfileUnavailable : GameplayStartContext
}

data class GameplayStartInputs(
    val content: GameplayContentSnapshot,
    val profile: GameplayProfileSnapshot,
    val seed: Int,
)

sealed interface GameplayNucleusPulse {
    data class Intent(val intent: GameplayInteractionPulse) : GameplayNucleusPulse
    data class ApplyPreferences(val preferences: PlayerPreferences) : GameplayNucleusPulse
    data object StartRun : GameplayNucleusPulse
    data object PauseForOverlay : GameplayNucleusPulse

    data object ExitRun : GameplayNucleusPulse
    data class ProgressApplied(val result: ProfileProgressApplied) : GameplayNucleusPulse
    data class ProgressRefused(val reason: ProfileRefusal) : GameplayNucleusPulse

}

sealed interface GameplayDecision {
    data class Accepted(val frame: GameplayAcceptedFrame) : GameplayDecision
    data class Rejected(val reason: GameplayRejection) : GameplayDecision
}

public data class GameplayAcceptedFrame(
    val nextState: GameplayState,
    val outputs: ImmutableList<GameplayOutput>,
) {
    init {
        require(outputs.size <= MAX_GAMEPLAY_OUTPUTS_PER_DECISION)
        var completionIndex = -1
        var index = 0
        while (index < outputs.size && completionIndex < 0) {
            if (outputs[index] is GameplayOutput.RunExited ||
                outputs[index] is GameplayOutput.SettingsApplied ||
                outputs[index] is GameplayOutput.RunStarted || outputs[index] is GameplayOutput.OverlayPaused
            ) completionIndex = index
            index++
        }
        require(completionIndex < 0 || completionIndex == outputs.lastIndex)
        index = 1
        while (index < outputs.size) {
            require(outputs[index - 1].orderRank() <= outputs[index].orderRank())
            index++
        }
    }
}

sealed interface GameplayOutput {
    data class EmitVisualFx(val cues: ImmutableList<VisualFxCue>) : GameplayOutput
    data class SendProfileCommand(val update: GameplayProgressUpdate) : GameplayOutput
    data class AdvanceAudio(
        val realDeltaSeconds: Float,
        val cues: ImmutableList<GameplayAudioCue>,
    ) : GameplayOutput
    data object EnsureAudioUnlocked : GameplayOutput
    data class RunExited(val result: GameplayRunExited) : GameplayOutput
    data class SettingsApplied(val result: GameplaySettingsApplied) : GameplayOutput
    data class RunStarted(val result: GameplayRunStarted) : GameplayOutput
    data class OverlayPaused(val result: GameplayOverlayPaused) : GameplayOutput
}

internal fun GameplayOutput.orderRank(): Int = when (this) {
    is GameplayOutput.EmitVisualFx -> 0
    is GameplayOutput.SendProfileCommand -> 1
    is GameplayOutput.AdvanceAudio,
    GameplayOutput.EnsureAudioUnlocked,
    -> 2
    is GameplayOutput.RunExited, is GameplayOutput.SettingsApplied,
    is GameplayOutput.RunStarted, is GameplayOutput.OverlayPaused,
    -> 3
}
