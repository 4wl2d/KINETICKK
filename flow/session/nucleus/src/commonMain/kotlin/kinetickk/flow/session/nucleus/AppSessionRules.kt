// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.nucleus

import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.RunId
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.AppShellProjection
import kinetickk.flow.session.api.SessionRejection
import kinetickk.flow.session.api.SessionRevision
import kinetickk.flow.session.api.SessionWorkflowPhase
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

internal data class RunReservation(
    val runId: RunId,
    val nextRunId: RunId?,
    val ensure: Boolean = true,
)

internal fun AppSessionState.nextRevision(): SessionRevision {
    check(revision.value < Long.MAX_VALUE) { "Session revision exhausted before acceptance" }
    return SessionRevision(revision.value + 1L)
}

internal fun AppSessionState.reserveRun(): RunReservation? {
    val reserved = nextRunId ?: return null
    val next = if (reserved.value == Long.MAX_VALUE) null else RunId(reserved.value + 1L)
    return RunReservation(reserved, next)
}

internal fun AppSessionState.reusableCreatedRunId(): RunId? =
    activeRunId?.takeIf { gameplayPhase == GameplayRunPhase.CREATED }

internal fun AppSessionState.gameplayStatusIfActive(
    context: AppSessionContext,
): GameplayRunStatusProjection? = if (activeRunId == null) {
    null
} else {
    checkNotNull(context.gameplayStatus) {
        "Impl must supply the validated active Gameplay status read"
    }
}

internal fun GameplayRunPhase.canBeReplaced(): Boolean = when (this) {
    GameplayRunPhase.GAME_OVER,
    GameplayRunPhase.VICTORY,
    GameplayRunPhase.EXITED,
    -> true
    GameplayRunPhase.CREATED,
    GameplayRunPhase.RUNNING,
    GameplayRunPhase.PAUSED,
    GameplayRunPhase.CHOICE,
    -> false
}

internal fun GameplayRunPhase.acceptsPreferenceUpdate(): Boolean = when (this) {
    GameplayRunPhase.RUNNING,
    GameplayRunPhase.PAUSED,
    GameplayRunPhase.CHOICE,
    GameplayRunPhase.GAME_OVER,
    GameplayRunPhase.VICTORY,
    -> true
    GameplayRunPhase.CREATED,
    GameplayRunPhase.EXITED,
    -> false
}

internal fun GameplayRunPhase.canExit(): Boolean = acceptsPreferenceUpdate()

internal val RunStartReason.startUnavailableReason: SessionRejection
    get() = if (this == RunStartReason.RESTART) {
        SessionRejection.RestartUnavailable
    } else {
        SessionRejection.StartUnavailable
    }

internal val SettingsContinuation.overlayAfterCompletion: AppDestination?
    get() = when (this) {
        SettingsContinuation.Close -> null
        is SettingsContinuation.Open -> destination
    }

internal fun AppSessionState.toShellProjection(): AppShellProjection = AppShellProjection(
    instanceId = instanceId,
    revision = revision,
    routeRevision = routeRevision,
    base = base,
    overlay = overlay,
    activeRunId = activeRunId,
    rebirthEligible = base == AppDestination.Home || gameplayPhase == GameplayRunPhase.VICTORY,
    pendingWorkflow = pendingWorkflow?.toProjection(),
    lifecycle = lifecycle,
    rebirthConfirmationArmed = rebirthConfirmation is RebirthConfirmation.Armed,
    workflowFailure = lastFailure,
    runMode = runMode,
)

private fun PendingWorkflow.toProjection(): SessionWorkflowPhase = when (this) {
    is PendingWorkflow.StartingRun -> when (reason) {
        RunStartReason.START -> SessionWorkflowPhase.STARTING_RUN
        RunStartReason.RESTART -> SessionWorkflowPhase.RESTARTING_RUN
        RunStartReason.REBIRTH -> SessionWorkflowPhase.STARTING_REBIRTH_RUN
    }
    is PendingWorkflow.PausingForOverlay -> SessionWorkflowPhase.PAUSING_FOR_OVERLAY
    is PendingWorkflow.ApplyingSettings -> SessionWorkflowPhase.APPLYING_SETTINGS
    is PendingWorkflow.SelectingCoreShape -> SessionWorkflowPhase.SELECTING_CORE_SHAPE
    is PendingWorkflow.TogglingMute -> SessionWorkflowPhase.TOGGLING_MUTE
    is PendingWorkflow.PropagatingMute -> SessionWorkflowPhase.PROPAGATING_MUTE
    is PendingWorkflow.SelectingRebirthTier -> SessionWorkflowPhase.SELECTING_REBIRTH_TIER
    is PendingWorkflow.StartingRebirthRun -> SessionWorkflowPhase.STARTING_REBIRTH_RUN
    is PendingWorkflow.ExitingRun -> SessionWorkflowPhase.EXITING_RUN
}

internal fun accepted(
    nextState: AppSessionState,
    outputs: ImmutableList<AppSessionOutput> = immutableListOf(),
): AppSessionDecision = AppSessionDecision.Accepted(
    AppSessionAcceptedFrame(
        nextState = nextState,
        outputs = outputs,
    ),
)

internal fun rejected(reason: SessionRejection): AppSessionDecision =
    AppSessionDecision.Rejected(reason)
