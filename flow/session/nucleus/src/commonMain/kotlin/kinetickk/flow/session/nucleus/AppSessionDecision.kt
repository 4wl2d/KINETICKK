// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.nucleus

import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.api.GameplaySettingsApplied
import kinetickk.ball.gameplay.api.GameplayRefusal
import kinetickk.ball.gameplay.api.GameplayRunStarted
import kinetickk.ball.gameplay.api.GameplayRunExited
import kinetickk.ball.gameplay.api.GameplayOverlayPaused
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.ball.profile.api.ProfileSettingsChanged
import kinetickk.ball.profile.api.ProfileRefusal
import kinetickk.ball.profile.api.ProfileRebirthAdvanced
import kinetickk.ball.profile.api.ProfileCoreShapeSelected
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

    sealed interface Result : AppSessionNucleusPulse

    sealed interface Refusal : AppSessionNucleusPulse
}

internal data class ProfileRebirthAdvancedPulse(val result: ProfileRebirthAdvanced) : AppSessionNucleusPulse.Result
internal data class ProfileRebirthRefusedPulse(val reason: ProfileRefusal) : AppSessionNucleusPulse.Refusal

fun profileRebirthAdvanced(result: ProfileRebirthAdvanced): AppSessionNucleusPulse.Result = ProfileRebirthAdvancedPulse(result)
fun profileRebirthRefused(reason: ProfileRefusal): AppSessionNucleusPulse.Refusal = ProfileRebirthRefusedPulse(reason)

internal data class ProfileSettingsChangedPulse(
    val result: ProfileSettingsChanged,
) : AppSessionNucleusPulse.Result

internal data class ProfileCoreShapeSelectedPulse(
    val result: ProfileCoreShapeSelected,
) : AppSessionNucleusPulse.Result

internal data class ProfileCoreShapeRefusedPulse(val reason: ProfileRefusal) : AppSessionNucleusPulse.Refusal

fun profileCoreShapeSelected(result: ProfileCoreShapeSelected): AppSessionNucleusPulse.Result =
    ProfileCoreShapeSelectedPulse(result)

fun profileCoreShapeRefused(reason: ProfileRefusal): AppSessionNucleusPulse.Refusal =
    ProfileCoreShapeRefusedPulse(reason)

internal data class GameplaySettingsAppliedPulse(
    val result: GameplaySettingsApplied,
) : AppSessionNucleusPulse.Result

internal data class GameplayRunExitedPulse(val result: GameplayRunExited) : AppSessionNucleusPulse.Result
internal data class GameplayExitRefusedPulse(val runId: RunId, val reason: GameplayRefusal) : AppSessionNucleusPulse.Refusal
fun gameplayRunExited(result: GameplayRunExited): AppSessionNucleusPulse.Result = GameplayRunExitedPulse(result)
fun gameplayExitRefused(runId: RunId, reason: GameplayRefusal): AppSessionNucleusPulse.Refusal = GameplayExitRefusedPulse(runId, reason)

internal data class GameplayRunStartedPulse(val result: GameplayRunStarted) : AppSessionNucleusPulse.Result
internal data class GameplayOverlayPausedPulse(val result: GameplayOverlayPaused) : AppSessionNucleusPulse.Result
internal data class GameplayStartRefusedPulse(val runId: RunId, val reason: GameplayRefusal) : AppSessionNucleusPulse.Refusal
internal data class GameplayPauseRefusedPulse(val runId: RunId, val reason: GameplayRefusal) : AppSessionNucleusPulse.Refusal

fun gameplayRunStarted(result: GameplayRunStarted): AppSessionNucleusPulse.Result = GameplayRunStartedPulse(result)
fun gameplayOverlayPaused(result: GameplayOverlayPaused): AppSessionNucleusPulse.Result = GameplayOverlayPausedPulse(result)
fun gameplayStartRefused(runId: RunId, reason: GameplayRefusal): AppSessionNucleusPulse.Refusal = GameplayStartRefusedPulse(runId, reason)
fun gameplayPauseRefused(runId: RunId, reason: GameplayRefusal): AppSessionNucleusPulse.Refusal = GameplayPauseRefusedPulse(runId, reason)

internal data class ProfileSettingsRefusedPulse(
    val reason: ProfileRefusal,
) : AppSessionNucleusPulse.Refusal

internal data class GameplaySettingsRefusedPulse(
    val runId: RunId,
    val reason: GameplayRefusal,
) : AppSessionNucleusPulse.Refusal

fun profileSettingsChanged(result: ProfileSettingsChanged): AppSessionNucleusPulse.Result =
    ProfileSettingsChangedPulse(result)

fun gameplaySettingsApplied(result: GameplaySettingsApplied): AppSessionNucleusPulse.Result =
    GameplaySettingsAppliedPulse(result)

fun profileSettingsRefused(reason: ProfileRefusal): AppSessionNucleusPulse.Refusal =
    ProfileSettingsRefusedPulse(reason)

fun gameplaySettingsRefused(runId: RunId, reason: GameplayRefusal): AppSessionNucleusPulse.Refusal =
    GameplaySettingsRefusedPulse(runId, reason)

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
    data class StartRun(val runId: RunId) : AppSessionOutput
    data class PauseForOverlay(val runId: RunId) : AppSessionOutput
    data class ExitRun(val runId: RunId) : AppSessionOutput
    data object AdvanceRebirth : AppSessionOutput
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
        AppSessionOutput.AdvanceRebirth,
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
        this === AppSessionOutput.AdvanceRebirth || this === AppSessionOutput.ToggleMute || this is AppSessionOutput.ApplyPreferences ||
        this is AppSessionOutput.SelectCoreShape || this is AppSessionOutput.StartRun ||
        this is AppSessionOutput.PauseForOverlay
