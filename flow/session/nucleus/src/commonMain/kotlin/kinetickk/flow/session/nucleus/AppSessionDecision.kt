// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.nucleus

import kinetickk.ball.gameplay.api.GameplayCommand
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileCommand
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.flow.session.api.AppShellProjection
import kinetickk.flow.session.api.SessionRejection
import kinetickk.foundation.collections.ImmutableList

const val MAX_SESSION_OUTPUTS_PER_DECISION: Int = 3

/** Sparse immutable participant observations available to a pure Session decision. */
data class AppSessionContext(
    val runBootstrap: RunBootstrapProjection? = null,
    val preferences: PreferencesProjection? = null,
    val rebirthProgress: RebirthProgressProjection? = null,
    val persistenceStatus: PersistenceStatusProjection? = null,
    val gameplayStatus: GameplayRunStatusProjection? = null,
) {
    companion object {
        val Empty: AppSessionContext = AppSessionContext()
    }
}

sealed interface AppSessionDecision {
    data class Accepted(val frame: AppSessionAcceptedFrame) : AppSessionDecision
    data class Rejected(val reason: SessionRejection) : AppSessionDecision
}

data class AppSessionAcceptedFrame(
    val nextState: AppSessionState,
    val shellProjection: AppShellProjection,
    val outputs: ImmutableList<AppSessionOutput>,
) {
    init {
        require(shellProjection.instanceId == nextState.instanceId) {
            "Session frame projection must retain the next State identity"
        }
        require(shellProjection.revision == nextState.revision) {
            "Session frame projection must retain the next State revision"
        }
        require(outputs.size <= MAX_SESSION_OUTPUTS_PER_DECISION) {
            "Session semantic output bound exceeded: ${outputs.size}"
        }
        outputs.zipWithNext().forEach { (before, after) ->
            require(before.orderRank <= after.orderRank) {
                "Session outputs must retain ensure -> participant -> feedback order"
            }
        }
        require(outputs.count(AppSessionOutput::isParticipantCommand) <= 1) {
            "A Session decision may issue at most one participant command"
        }
        val ensures = outputs.filterIsInstance<AppSessionOutput.EnsureGameplayRun>()
        require(ensures.size <= 1) { "A Session decision may ensure at most one GameplayRun" }
        ensures.singleOrNull()?.let { ensure ->
            val gameplay = outputs.filterIsInstance<AppSessionOutput.SendGameplayCommand>().singleOrNull()
            require(gameplay != null && gameplay.command.ref.targetInstance.runId == ensure.runId) {
                "Ensured GameplayRun must be the target of the same accepted frame"
            }
        }
    }
}

sealed interface AppSessionOutput {
    data class EnsureGameplayRun(val runId: RunId) : AppSessionOutput
    data class SendProfileCommand(val command: ProfileCommand) : AppSessionOutput
    data class SendGameplayCommand(val command: GameplayCommand) : AppSessionOutput
    data class SynchronizeAudioPreferences(val preferences: PlayerPreferences) : AppSessionOutput
    data object PlayMuteFeedback : AppSessionOutput
    data object PlayRebirthAcceptedFeedback : AppSessionOutput
}

private val AppSessionOutput.orderRank: Int
    get() = when (this) {
        is AppSessionOutput.EnsureGameplayRun -> 0
        is AppSessionOutput.SendProfileCommand,
        is AppSessionOutput.SendGameplayCommand,
        -> 1
        is AppSessionOutput.SynchronizeAudioPreferences,
        AppSessionOutput.PlayMuteFeedback,
        AppSessionOutput.PlayRebirthAcceptedFeedback,
        -> 2
    }

private fun AppSessionOutput.isParticipantCommand(): Boolean =
    this is AppSessionOutput.SendProfileCommand || this is AppSessionOutput.SendGameplayCommand
