// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.nucleus

import kinetickk.ball.gameplay.api.GameplayRunExited
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.ProfileSettingsChanged
import kinetickk.ball.profile.api.ProfileRebirthTierSelected
import kinetickk.ball.profile.api.ProfileCoreShapeSelected
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.SessionWorkflowFailureCode
import kinetickk.foundation.collections.immutableListOf

internal fun completeCoreShape(state: AppSessionState, result: ProfileCoreShapeSelected): AppSessionDecision {
    val pending = checkNotNull(state.pendingWorkflow as? PendingWorkflow.SelectingCoreShape)
    check(result.shape == pending.shape) { "Profile selected a different core shape" }
    return accepted(state.copy(revision = state.nextRevision(), pendingWorkflow = null, lastFailure = null))
}

internal fun rejectCoreShape(state: AppSessionState): AppSessionDecision {
    check(state.pendingWorkflow is PendingWorkflow.SelectingCoreShape)
    return accepted(state.copy(
        revision = state.nextRevision(),
        pendingWorkflow = null,
        lastFailure = SessionWorkflowFailureCode.PROFILE_COMMAND_REFUSED,
    ))
}

internal fun completeMuteProfileResult(
    state: AppSessionState,
    result: ProfileSettingsChanged,
): AppSessionDecision {
    check(state.pendingWorkflow === PendingWorkflow.TogglingMute) {
        "Profile settings result does not match the current Session workflow"
    }
    val preferences = result.preferences
    val revision = state.nextRevision()
    if (state.activeRunId != null && state.gameplayPhase?.acceptsPreferenceUpdate() == true) {
        return accepted(
            state.copy(
                revision = revision,
                pendingWorkflow = PendingWorkflow.PropagatingMute(
                    preferences,
                    state.activeRunId,
                ),
                lastFailure = null,
            ),
            immutableListOf(
                AppSessionOutput.ApplyPreferences(state.activeRunId, preferences),
                AppSessionOutput.SynchronizeAudioPreferences(preferences),
                AppSessionOutput.PlayMuteFeedback,
            ),
        )
    }
    return accepted(
        state.copy(
            revision = revision,
            pendingWorkflow = null,
            lastFailure = null,
        ),
        immutableListOf(
            AppSessionOutput.SynchronizeAudioPreferences(preferences),
            AppSessionOutput.PlayMuteFeedback,
        ),
    )
}

internal fun completeRebirthProfileResult(
    state: AppSessionState,
    result: ProfileRebirthTierSelected,
    context: AppSessionContext,
): AppSessionDecision {
    val pending = checkNotNull(state.pendingWorkflow as? PendingWorkflow.SelectingRebirthTier)
    check(result.progress.level == pending.tier) { "Profile selected a different Rebirth tier" }
    val ready = checkNotNull(
        checkNotNull(context.runBootstrap).result as? ProfileRunBootstrapResult.Ready,
    ) { "Validated rebirth result requires a ready Profile bootstrap" }
    check(ready.snapshot.rebirthProgress == result.progress) {
        "Profile bootstrap contradicted the accepted rebirth result"
    }
    val reusableRunId = state.reusableCreatedRunId()
    val reservation = if (reusableRunId != null) {
        RunReservation(reusableRunId, state.nextRunId, ensure = false)
    } else {
        checkNotNull(state.reserveRun()) { "RunId capacity was not reserved before rebirth" }
    }
    val revision = state.nextRevision()
    val next = state.copy(
        revision = revision,
        activeRunId = reservation.runId,
        gameplayPhase = GameplayRunPhase.CREATED,
        pendingWorkflow = PendingWorkflow.StartingRebirthRun(
            reservation.runId,
        ),
        rebirthConfirmation = RebirthConfirmation.Disarmed,
        lastFailure = null,
        nextRunId = reservation.nextRunId,
    )
    val send = AppSessionOutput.StartRun(reservation.runId, state.runMode)
    return accepted(
        next,
        if (reservation.ensure) {
            immutableListOf(
                AppSessionOutput.EnsureGameplayRun(reservation.runId),
                send,
                AppSessionOutput.PlayRebirthAcceptedFeedback,
            )
        } else {
            immutableListOf(send, AppSessionOutput.PlayRebirthAcceptedFeedback)
        },
    )
}

internal fun completeStart(state: AppSessionState, pulse: GameplayRunStartedPulse): AppSessionDecision {
    val expectedRun = state.pendingStartRunId()
    check(pulse.result.runId == expectedRun && expectedRun == state.activeRunId)
    return accepted(state.copy(
        revision = state.nextRevision(),
        routeRevision = state.nextRevision(),
        base = AppDestination.Gameplay,
        overlay = null,
        gameplayPhase = GameplayRunPhase.RUNNING,
        pendingWorkflow = null,
        rebirthConfirmation = RebirthConfirmation.Disarmed,
        lastFailure = null,
    ))
}

internal fun completePause(state: AppSessionState, pulse: GameplayOverlayPausedPulse): AppSessionDecision {
    val pending = checkNotNull(state.pendingWorkflow as? PendingWorkflow.PausingForOverlay)
    check(pulse.result.runId == pending.runId && pending.runId == state.activeRunId)
    return accepted(state.copy(
        revision = state.nextRevision(),
        routeRevision = state.nextRevision(),
        overlay = pending.destination,
        gameplayPhase = GameplayRunPhase.PAUSED,
        pendingWorkflow = null,
        rebirthConfirmation = RebirthConfirmation.Disarmed,
        lastFailure = null,
    ))
}

internal fun rejectStart(state: AppSessionState, pulse: GameplayStartRefusedPulse): AppSessionDecision {
    check(pulse.runId == state.pendingStartRunId() && pulse.runId == state.activeRunId)
    return accepted(state.copy(
        revision = state.nextRevision(),
        routeRevision = if (state.base != AppDestination.Home || state.overlay != null) state.nextRevision() else state.routeRevision,
        base = AppDestination.Home,
        overlay = null,
        gameplayPhase = GameplayRunPhase.CREATED,
        pendingWorkflow = null,
        lastFailure = SessionWorkflowFailureCode.GAMEPLAY_COMMAND_REFUSED,
    ))
}

internal fun rejectPause(state: AppSessionState, pulse: GameplayPauseRefusedPulse): AppSessionDecision {
    val pending = checkNotNull(state.pendingWorkflow as? PendingWorkflow.PausingForOverlay)
    check(pulse.runId == pending.runId && pending.runId == state.activeRunId)
    return accepted(state.copy(revision = state.nextRevision(), pendingWorkflow = null,
        lastFailure = SessionWorkflowFailureCode.GAMEPLAY_COMMAND_REFUSED))
}

private fun AppSessionState.pendingStartRunId(): RunId = when (val pending = pendingWorkflow) {
    is PendingWorkflow.StartingRun -> pending.runId
    is PendingWorkflow.StartingRebirthRun -> pending.runId
    else -> error("Gameplay start result does not match the current Session workflow")
}

internal fun completeGameplaySettings(
    state: AppSessionState,
    pulse: GameplaySettingsAppliedPulse,
): AppSessionDecision = when (val pending = state.pendingWorkflow) {
    is PendingWorkflow.ApplyingSettings -> {
        check(pulse.result.runId == pending.runId && pending.runId == state.activeRunId)
        accepted(
            state.copy(
                revision = state.nextRevision(),
                routeRevision = state.nextRevision(),
                overlay = pending.continuation.overlayAfterCompletion,
                pendingWorkflow = null,
                rebirthConfirmation = RebirthConfirmation.Disarmed,
                lastFailure = null,
            ),
            immutableListOf(AppSessionOutput.SynchronizeAudioPreferences(pending.preferences)),
        )
    }
    is PendingWorkflow.PropagatingMute -> {
        check(pulse.result.runId == pending.runId && pending.runId == state.activeRunId)
        accepted(state.copy(revision = state.nextRevision(), pendingWorkflow = null, lastFailure = null))
    }
    else -> error("Gameplay settings result does not match the current Session workflow")
}

internal fun rejectProfileSettings(state: AppSessionState): AppSessionDecision {
    check(state.pendingWorkflow === PendingWorkflow.TogglingMute)
    return accepted(
        state.copy(
            revision = state.nextRevision(),
            pendingWorkflow = null,
            lastFailure = SessionWorkflowFailureCode.PROFILE_COMMAND_REFUSED,
        ),
        immutableListOf(AppSessionOutput.PlayMuteFeedback),
    )
}

internal fun rejectGameplaySettings(
    state: AppSessionState,
    pulse: GameplaySettingsRefusedPulse,
): AppSessionDecision {
    val expectedRun = when (val pending = state.pendingWorkflow) {
        is PendingWorkflow.ApplyingSettings -> pending.runId
        is PendingWorkflow.PropagatingMute -> pending.runId
        else -> error("Gameplay settings refusal does not match the current Session workflow")
    }
    check(pulse.runId == expectedRun && expectedRun == state.activeRunId)
    return accepted(state.copy(
        revision = state.nextRevision(),
        pendingWorkflow = null,
        lastFailure = SessionWorkflowFailureCode.GAMEPLAY_COMMAND_REFUSED,
    ))
}

internal fun completeExitResult(
    state: AppSessionState,
    result: GameplayRunExited,
): AppSessionDecision {
    val pending = checkNotNull(state.pendingWorkflow as? PendingWorkflow.ExitingRun)
    check(result.runId == pending.runId && result.runId == state.activeRunId)
    val revision = state.nextRevision()
    return when (result.progress) {
        kinetickk.ball.gameplay.api.GameplayExitProgressResult.NoProgress,
        kinetickk.ball.gameplay.api.GameplayExitProgressResult.Applied,
        -> accepted(
            state.copy(
                revision = revision,
                base = AppDestination.Home,
                routeRevision = revision,
                overlay = null,
                gameplayPhase = GameplayRunPhase.EXITED,
                pendingWorkflow = null,
                rebirthConfirmation = RebirthConfirmation.Disarmed,
                lastFailure = null,
            ),
        )
        kinetickk.ball.gameplay.api.GameplayExitProgressResult.NotApplied -> accepted(
            state.copy(
                revision = revision,
                gameplayPhase = GameplayRunPhase.EXITED,
                pendingWorkflow = null,
                rebirthConfirmation = RebirthConfirmation.Disarmed,
                lastFailure = SessionWorkflowFailureCode.EXIT_PROGRESS_NOT_APPLIED,
            ),
        )
    }
}

internal fun rejectRebirth(state: AppSessionState): AppSessionDecision {
    check(state.pendingWorkflow is PendingWorkflow.SelectingRebirthTier)
    return accepted(state.copy(
        revision = state.nextRevision(),
        pendingWorkflow = null,
        rebirthConfirmation = RebirthConfirmation.Disarmed,
        lastFailure = SessionWorkflowFailureCode.PROFILE_COMMAND_REFUSED,
    ))
}

internal fun rejectExit(state: AppSessionState, pulse: GameplayExitRefusedPulse): AppSessionDecision {
    val pending = checkNotNull(state.pendingWorkflow as? PendingWorkflow.ExitingRun)
    check(pulse.runId == pending.runId && pulse.runId == state.activeRunId)
    return accepted(state.copy(
        revision = state.nextRevision(),
        pendingWorkflow = null,
        lastFailure = SessionWorkflowFailureCode.GAMEPLAY_COMMAND_REFUSED,
    ))
}
