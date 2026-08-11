// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.nucleus

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.api.GameplayModuleCommandRequest
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileModuleCommandRequest
import kinetickk.ball.profile.api.ProfileResetStatus
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.AppSessionInstanceId
import kinetickk.flow.session.api.LOCAL_APP_SESSION_INSTANCE_ID
import kinetickk.flow.session.api.SessionResetLifecycle
import kinetickk.flow.session.api.SessionRevision
import kinetickk.flow.session.api.SessionWorkflowFailureCode
import kinetickk.flow.session.api.isBaseDestination
import kinetickk.flow.session.api.isOverlayDestination

sealed interface PendingParticipantCommand {
    data class Profile(
        val request: ProfileModuleCommandRequest,
    ) : PendingParticipantCommand

    data class Gameplay(
        val request: GameplayModuleCommandRequest,
    ) : PendingParticipantCommand
}

enum class RunStartReason {
    START,
    RESTART,
    REBIRTH,
}

sealed interface SettingsContinuation {
    data object Close : SettingsContinuation
    data class Open(val destination: AppDestination) : SettingsContinuation
}

sealed interface PendingWorkflow {
    val participant: PendingParticipantCommand

    data class StartingRun(
        val reason: RunStartReason,
        val runId: RunId,
        override val participant: PendingParticipantCommand.Gameplay,
    ) : PendingWorkflow

    data class PausingForOverlay(
        val destination: AppDestination,
        override val participant: PendingParticipantCommand.Gameplay,
    ) : PendingWorkflow

    data class ApplyingSettings(
        val preferences: PlayerPreferences,
        val continuation: SettingsContinuation,
        override val participant: PendingParticipantCommand.Gameplay,
    ) : PendingWorkflow

    data class SelectingCoreShape(
        val shape: CoreShape,
        override val participant: PendingParticipantCommand.Profile,
    ) : PendingWorkflow

    data class TogglingMute(
        override val participant: PendingParticipantCommand.Profile,
    ) : PendingWorkflow

    data class PropagatingMute(
        val preferences: PlayerPreferences,
        override val participant: PendingParticipantCommand.Gameplay,
    ) : PendingWorkflow

    data class AdvancingRebirth(
        override val participant: PendingParticipantCommand.Profile,
    ) : PendingWorkflow

    data class StartingRebirthRun(
        val runId: RunId,
        override val participant: PendingParticipantCommand.Gameplay,
    ) : PendingWorkflow

    data class ExitingRun(
        override val participant: PendingParticipantCommand.Gameplay,
    ) : PendingWorkflow

    data class ConfirmingReset(
        override val participant: PendingParticipantCommand.Profile,
    ) : PendingWorkflow

    data class RetryingPurge(
        override val participant: PendingParticipantCommand.Profile,
    ) : PendingWorkflow
}

sealed interface RebirthConfirmation {
    data object Disarmed : RebirthConfirmation

    data class Armed(
        val profileRevision: ProfileRevision,
        val progress: RebirthProgress,
    ) : RebirthConfirmation
}

/** Complete immutable snapshot owned and published by the singleton AppSession acceptor. */
data class AppSessionState(
    val instanceId: AppSessionInstanceId,
    val revision: SessionRevision,
    val routeRevision: SessionRevision,
    val base: AppDestination,
    val overlay: AppDestination?,
    val activeRunId: RunId?,
    val gameplayPhase: GameplayRunPhase?,
    val pendingWorkflow: PendingWorkflow?,
    val rebirthConfirmation: RebirthConfirmation,
    val resetLifecycle: SessionResetLifecycle,
    val lastFailure: SessionWorkflowFailureCode?,
    val nextRunId: RunId?,
) {
    init {
        require(instanceId == LOCAL_APP_SESSION_INSTANCE_ID) {
            "AppSession State must retain the singleton identity"
        }
        require(base.isBaseDestination()) { "Only Home and Gameplay may be base destinations" }
        require(overlay == null || overlay.isOverlayDestination()) {
            "Only feature routes may be overlays"
        }
        require((activeRunId == null) == (gameplayPhase == null)) {
            "The retained Gameplay phase must belong to the active RunId"
        }
        require(base != AppDestination.Gameplay || activeRunId != null) {
            "Gameplay base requires an active GameplayRun"
        }
    }

    companion object {
        fun initial(persistenceStatus: PersistenceStatusProjection): AppSessionState {
            require(persistenceStatus.instanceId == LOCAL_PROFILE_INSTANCE_ID) {
                "Session bootstrap must observe the local Profile"
            }
            return AppSessionState(
                instanceId = LOCAL_APP_SESSION_INSTANCE_ID,
                revision = SessionRevision.ZERO,
                routeRevision = SessionRevision.ZERO,
                base = AppDestination.Home,
                overlay = null,
                activeRunId = null,
                gameplayPhase = null,
                pendingWorkflow = null,
                rebirthConfirmation = RebirthConfirmation.Disarmed,
                resetLifecycle = persistenceStatus.toSessionResetLifecycle(),
                lastFailure = null,
                nextRunId = RunId(0L),
            )
        }
    }
}

internal fun PersistenceStatusProjection.toSessionResetLifecycle(): SessionResetLifecycle =
    when (reset) {
        is ProfileResetStatus.ConfirmationRequired -> SessionResetLifecycle.CONFIRMATION_REQUIRED
        is ProfileResetStatus.WritingFreshV4,
        is ProfileResetStatus.PurgingLegacy,
        -> SessionResetLifecycle.RESET_IN_PROGRESS
        is ProfileResetStatus.NeedsAttention -> SessionResetLifecycle.PURGE_NEEDS_ATTENTION
        is ProfileResetStatus.NotRequired -> if (bootstrap == ProfileBootstrapStatus.Ready) {
            SessionResetLifecycle.READY
        } else {
            SessionResetLifecycle.BOOTSTRAP_UNAVAILABLE
        }
    }
