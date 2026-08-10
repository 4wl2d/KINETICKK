// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus

import kinetickk.ball.gameplay.api.GameplayCommand
import kinetickk.ball.gameplay.api.GameplayCommandAdmission
import kinetickk.ball.gameplay.api.GameplayCommandResult
import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.gameplay.api.GameplayRenderProjection
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.profile.api.ProfileCommand
import kinetickk.foundation.collections.ImmutableList

const val MAX_GAMEPLAY_OUTPUTS_PER_DECISION: Int = 3

data class GameplayContext(
    val command: GameplayCommand? = null,
    val admission: GameplayCommandAdmission? = null,
) {
    companion object {
        val Local: GameplayContext = GameplayContext()
    }
}

sealed interface GameplayDecision {
    data class Accepted(val frame: GameplayAcceptedFrame) : GameplayDecision
    data class Rejected(val reason: GameplayRejection) : GameplayDecision
}

data class GameplayAcceptedFrame(
    val nextState: GameplayState,
    val renderProjection: GameplayRenderProjection,
    val outputs: ImmutableList<GameplayOutput>,
) {
    init {
        require(renderProjection.instanceId == nextState.instanceId) {
            "Gameplay frame projection must retain the next State identity"
        }
        require(renderProjection.revision == nextState.revision) {
            "Gameplay frame projection must retain the next State revision"
        }
        val renderModel = renderProjection.renderModel
        require((renderModel == null) == (nextState.engine == null)) {
            "Gameplay frame projection and simulation must be present together"
        }
        if (renderModel != null) {
            require(renderModel.content === nextState.content) {
                "Gameplay frame projection must retain the captured content object"
            }
        }
        require(outputs.size <= MAX_GAMEPLAY_OUTPUTS_PER_DECISION) {
            "Gameplay semantic output bound exceeded: ${outputs.size}"
        }
        val completionIndex = outputs.indexOfFirst { it is GameplayOutput.CompleteCommand }
        require(completionIndex < 0 || completionIndex == outputs.lastIndex) {
            "Gameplay command completion must be the final semantic output"
        }
        outputs.zipWithNext().forEach { (before, after) ->
            require(before.orderRank() <= after.orderRank()) {
                "Gameplay semantic outputs must retain FX -> Profile -> Audio -> completion order"
            }
        }
    }
}

sealed interface GameplayOutput {
    data class EmitVisualFx(val cues: ImmutableList<VisualFxCue>) : GameplayOutput
    data class SendProfileCommand(val command: ProfileCommand) : GameplayOutput
    data class AdvanceAudio(
        val realDeltaSeconds: Float,
        val cues: ImmutableList<GameplayAudioCue>,
    ) : GameplayOutput
    data object EnsureAudioUnlocked : GameplayOutput
    data class CompleteCommand(val result: GameplayCommandResult.Accepted) : GameplayOutput
}

private fun GameplayOutput.orderRank(): Int = when (this) {
    is GameplayOutput.EmitVisualFx -> 0
    is GameplayOutput.SendProfileCommand -> 1
    is GameplayOutput.AdvanceAudio,
    GameplayOutput.EnsureAudioUnlocked,
    -> 2
    is GameplayOutput.CompleteCommand -> 3
}
