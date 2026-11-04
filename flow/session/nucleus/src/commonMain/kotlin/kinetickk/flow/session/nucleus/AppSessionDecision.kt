// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.nucleus

import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.api.RunMode
import kinetickk.ball.gameplay.api.GameplayRefusal
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.ball.profile.api.ProfileRefusal
import kinetickk.ball.content.api.CoreShape
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.api.SessionRejection
import kinetickk.foundation.collections.ImmutableList

const val MAX_SESSION_OUTPUTS_PER_DECISION: Int = 3

/** Sparse exact reads. Impl validates identity/run before constructing a trusted Nucleus input. */
data class AppSessionContext(
    val runBootstrap: RunBootstrapProjection? = null,
    val preferences: PreferencesProjection? = null,
    val rebirthProgress: RebirthProgressProjection? = null,
    val gameplayStatus: GameplayRunStatusProjection? = null,
) {
    companion object {
        val Empty: AppSessionContext = AppSessionContext()
    }
}

sealed interface AppSessionNucleusPulse {
    data class Intent(
        val intent: SessionInteractionPulse,
    ) : AppSessionNucleusPulse

    /** A participant accepted the command Session issued. */
    sealed interface Result : AppSessionNucleusPulse {
        data class ProfileRebirthTierSelected(val result: kinetickk.ball.profile.api.ProfileRebirthTierSelected) : Result
        data class ProfileSettingsChanged(val result: kinetickk.ball.profile.api.ProfileSettingsChanged) : Result
        data class ProfileCoreShapeSelected(val result: kinetickk.ball.profile.api.ProfileCoreShapeSelected) : Result
        data class GameplaySettingsApplied(val result: kinetickk.ball.gameplay.api.GameplaySettingsApplied) : Result
        data class GameplayRunExited(val result: kinetickk.ball.gameplay.api.GameplayRunExited) : Result
        data class GameplayRunStarted(val result: kinetickk.ball.gameplay.api.GameplayRunStarted) : Result
        data class GameplayOverlayPaused(val result: kinetickk.ball.gameplay.api.GameplayOverlayPaused) : Result
    }

    /** A participant refused the command Session issued. */
    sealed interface Refusal : AppSessionNucleusPulse {
        data class ProfileRebirthRefused(val reason: ProfileRefusal) : Refusal
        data class ProfileSettingsRefused(val reason: ProfileRefusal) : Refusal
        data class ProfileCoreShapeRefused(val reason: ProfileRefusal) : Refusal
        data class GameplaySettingsRefused(val runId: RunId, val reason: GameplayRefusal) : Refusal
        data class GameplayExitRefused(val runId: RunId, val reason: GameplayRefusal) : Refusal
        data class GameplayStartRefused(val runId: RunId, val reason: GameplayRefusal) : Refusal
        data class GameplayPauseRefused(val runId: RunId, val reason: GameplayRefusal) : Refusal
    }
}

sealed interface AppSessionDecision {
    data class Accepted(val frame: AppSessionAcceptedFrame) : AppSessionDecision
    data class Rejected(val reason: SessionRejection) : AppSessionDecision
}

/** Canonical accepted frame; shell UI is derived separately through the Session Query surface. */
public data class AppSessionAcceptedFrame(
    val nextState: AppSessionState,
    val outputs: ImmutableList<AppSessionOutput>,
) {
    init {
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
            val gameplay = outputs.filterIsInstance<AppSessionOutput.StartRun>().singleOrNull()
            require(gameplay != null && gameplay.runId == ensure.runId) {
                "Ensured GameplayRun must be the target of the same accepted frame"
            }
        }
    }
}

sealed interface AppSessionOutput {
    data class EnsureGameplayRun(val runId: RunId) : AppSessionOutput
    data class StartRun(val runId: RunId, val mode: RunMode) : AppSessionOutput
    data class PauseForOverlay(val runId: RunId) : AppSessionOutput
    data class ExitRun(val runId: RunId) : AppSessionOutput
    data class SelectRebirthTier(val tier: Int) : AppSessionOutput
    data object ToggleMute : AppSessionOutput
    data class SelectCoreShape(val shape: CoreShape) : AppSessionOutput
    data class ApplyPreferences(val runId: RunId, val preferences: PlayerPreferences) : AppSessionOutput
    data class SynchronizeAudioPreferences(val preferences: PlayerPreferences) : AppSessionOutput
    data object PlayMuteFeedback : AppSessionOutput
    data object PlayRebirthAcceptedFeedback : AppSessionOutput
}

private val AppSessionOutput.orderRank: Int
    get() = when (this) {
        is AppSessionOutput.EnsureGameplayRun -> 0
        is AppSessionOutput.ExitRun,
        is AppSessionOutput.SelectRebirthTier,
        AppSessionOutput.ToggleMute,
        is AppSessionOutput.SelectCoreShape,
        is AppSessionOutput.StartRun, is AppSessionOutput.PauseForOverlay,
        is AppSessionOutput.ApplyPreferences,
        -> 1
        is AppSessionOutput.SynchronizeAudioPreferences,
        AppSessionOutput.PlayMuteFeedback,
        AppSessionOutput.PlayRebirthAcceptedFeedback,
        -> 2
    }

private fun AppSessionOutput.isParticipantCommand(): Boolean =
    this is AppSessionOutput.ExitRun ||
        this is AppSessionOutput.SelectRebirthTier || this === AppSessionOutput.ToggleMute || this is AppSessionOutput.ApplyPreferences ||
        this is AppSessionOutput.SelectCoreShape || this is AppSessionOutput.StartRun ||
        this is AppSessionOutput.PauseForOverlay
