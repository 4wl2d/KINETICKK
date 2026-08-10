// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.nucleus

import kinetickk.ball.gameplay.api.GameplayCommand
import kinetickk.ball.gameplay.api.GameplayCommandOutcome
import kinetickk.ball.gameplay.api.GameplayCommandRef
import kinetickk.ball.gameplay.api.GameplayCommandSource
import kinetickk.ball.gameplay.api.GameplayExitProfileOutcome
import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplaySessionPulse
import kinetickk.ball.gameplay.api.RunConfiguration
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileCommand
import kinetickk.ball.profile.api.ProfileCommandOutcome
import kinetickk.ball.profile.api.ProfileCommandRef
import kinetickk.ball.profile.api.ProfileCommandSource
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.AppSessionQuery
import kinetickk.flow.session.api.AppShellProjection
import kinetickk.flow.session.api.SessionContextField
import kinetickk.flow.session.api.SessionContextReason
import kinetickk.flow.session.api.SessionControlPulse
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.api.SessionParticipantResultRejection
import kinetickk.flow.session.api.SessionPulse
import kinetickk.flow.session.api.SessionRejection
import kinetickk.flow.session.api.SessionResetLifecycle
import kinetickk.flow.session.api.SessionRevision
import kinetickk.flow.session.api.SessionShortcut
import kinetickk.flow.session.api.SessionWorkflowFailure
import kinetickk.flow.session.api.SessionWorkflowPhase
import kinetickk.flow.session.api.isOverlayDestination
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

/** Pure coordinator for the singleton AppSession Flow. */
object AppSessionNucleus {
    fun decide(
        state: AppSessionState,
        pulse: SessionPulse,
        context: AppSessionContext = AppSessionContext.Empty,
    ): AppSessionDecision {
        if (state.revision.value == Long.MAX_VALUE) {
            return rejected(SessionRejection.RevisionExhausted)
        }
        if (pulse is SessionInteractionPulse) {
            if (state.pendingWorkflow != null) {
                return rejected(SessionRejection.ParticipantCommandPending)
            }
            if (
                state.resetLifecycle != SessionResetLifecycle.READY &&
                pulse != SessionInteractionPulse.ResetCancelled &&
                pulse != SessionInteractionPulse.ResetConfirmed &&
                pulse != SessionInteractionPulse.ResetRetryRequested
            ) {
                return rejected(SessionRejection.ResetBlocksInput)
            }
        }

        return when (pulse) {
            SessionInteractionPulse.StartRunRequested -> startRun(
                state,
                context,
                RunStartReason.START,
            )
            SessionInteractionPulse.RestartRunRequested -> startRun(
                state,
                context,
                RunStartReason.RESTART,
            )
            SessionInteractionPulse.ExitRunRequested -> exitRun(state, context)
            is SessionInteractionPulse.OpenOverlay -> openOverlay(
                state,
                pulse.destination,
                context,
            )
            SessionInteractionPulse.CloseOverlay -> closeOverlay(state, context)
            is SessionInteractionPulse.ShortcutObserved -> shortcut(
                state,
                pulse.shortcut,
                context,
            )
            SessionInteractionPulse.ToggleMuteRequested -> toggleMute(state)
            is SessionInteractionPulse.SelectCoreShapeRequested -> selectCoreShape(
                state,
                pulse,
            )
            SessionInteractionPulse.RebirthRequested -> rebirth(state, context)
            SessionInteractionPulse.ResetCancelled -> cancelReset(state)
            SessionInteractionPulse.ResetConfirmed -> confirmReset(state, context)
            SessionInteractionPulse.ResetRetryRequested -> retryReset(state, context)
            is SessionControlPulse.ProfileCommandCompleted -> completeProfileCommand(
                state,
                pulse,
                context,
            )
            is SessionControlPulse.ProfileCommandRejectedBeforeAcceptance ->
                rejectProfileCommandBeforeAcceptance(state, pulse)
            is SessionControlPulse.GameplayCommandCompleted -> completeGameplayCommand(
                state,
                pulse,
            )
            is SessionControlPulse.GameplayCommandRejectedBeforeAcceptance ->
                rejectGameplayCommandBeforeAcceptance(state, pulse)
        }
    }

    fun query(
        state: AppSessionState,
        query: AppSessionQuery.GetShell,
    ): AppShellProjection = when (query) {
        AppSessionQuery.GetShell -> state.toShellProjection()
    }

    private fun startRun(
        state: AppSessionState,
        context: AppSessionContext,
        reason: RunStartReason,
    ): AppSessionDecision {
        val bootstrap = context.requireRunBootstrap() ?: return context.invalidRunBootstrap()
        val ready = bootstrap.result as? ProfileRunBootstrapResult.Ready
            ?: return rejected(
                if (reason == RunStartReason.RESTART) {
                    SessionRejection.RestartUnavailable
                } else {
                    SessionRejection.StartUnavailable
                },
            )
        if (state.overlay != null) {
            return rejected(
                if (reason == RunStartReason.RESTART) {
                    SessionRejection.RestartUnavailable
                } else {
                    SessionRejection.StartUnavailable
                },
            )
        }

        val status = state.requireGameplayStatusIfActive(context)
            ?: if (state.activeRunId != null) return context.invalidGameplayStatus(state) else null
        val reservation = when (reason) {
            RunStartReason.START -> {
                if (state.base != AppDestination.Home) {
                    return rejected(SessionRejection.StartUnavailable)
                }
                if (
                    status?.phase == GameplayRunPhase.CREATED &&
                    !status.profileCommandPending
                ) {
                    RunReservation(
                        runId = status.instanceId.runId,
                        nextRunId = state.nextRunId,
                        ensure = false,
                    )
                } else {
                    if (status != null && !status.phase.canBeReplaced()) {
                        return rejected(SessionRejection.StartUnavailable)
                    }
                    state.reserveRun() ?: return rejected(SessionRejection.RunIdExhausted)
                }
            }
            RunStartReason.RESTART -> {
                if (
                    state.base != AppDestination.Gameplay ||
                    status?.phase != GameplayRunPhase.GAME_OVER &&
                    status?.phase != GameplayRunPhase.VICTORY
                ) {
                    return rejected(SessionRejection.RestartUnavailable)
                }
                state.reserveRun() ?: return rejected(SessionRejection.RunIdExhausted)
            }
            RunStartReason.REBIRTH -> error("Rebirth starts only after its Profile result")
        }

        val revision = state.nextRevision()
        if (!state.hasRevisionBudget(2)) {
            return rejected(SessionRejection.RevisionExhausted)
        }
        val issued = state.issueGameplayCommand(
            revision,
            reservation.runId,
            GameplaySessionPulse.StartRun(
                RunConfiguration(
                    content = state.content,
                    profile = ready.snapshot,
                ),
            ),
        ) ?: return rejected(SessionRejection.GameplayCommandOrdinalExhausted)
        val next = state.copy(
            revision = revision,
            activeRunId = reservation.runId,
            gameplayPhase = GameplayRunPhase.CREATED,
            pendingWorkflow = PendingWorkflow.StartingRun(
                reason = reason,
                runId = reservation.runId,
                participant = PendingParticipantCommand.Gameplay(issued.command),
            ),
            rebirthConfirmation = RebirthConfirmation.Disarmed,
            lastFailure = null,
            nextRunId = reservation.nextRunId,
            nextGameplayCommandOrdinal = issued.nextOrdinal,
        )
        val send = AppSessionOutput.SendGameplayCommand(issued.command)
        val outputs = if (reservation.ensure) {
            immutableListOf(
                AppSessionOutput.EnsureGameplayRun(reservation.runId),
                send,
            )
        } else {
            immutableListOf(send)
        }
        return accepted(next, outputs)
    }

    private fun openOverlay(
        state: AppSessionState,
        destination: AppDestination,
        context: AppSessionContext,
    ): AppSessionDecision {
        if (!destination.isOverlayDestination()) {
            return rejected(SessionRejection.OverlayUnavailable(destination))
        }
        val status = state.requireGameplayStatusIfActive(context)
            ?: if (state.activeRunId != null) return context.invalidGameplayStatus(state) else null

        if (state.overlay == AppDestination.Settings && destination != AppDestination.Settings) {
            return finishSettings(
                state = state,
                context = context,
                status = status,
                continuation = SettingsContinuation.Open(destination),
            )
        }
        if (state.base == AppDestination.Gameplay) {
            val phase = status?.phase
                ?: return context.invalidGameplayStatus(state)
            when (phase) {
                GameplayRunPhase.CHOICE ->
                    return rejected(SessionRejection.OverlayUnavailable(destination))
                GameplayRunPhase.GAME_OVER,
                GameplayRunPhase.VICTORY,
                -> if (destination != AppDestination.Rebirth) {
                    return rejected(SessionRejection.OverlayUnavailable(destination))
                }
                GameplayRunPhase.RUNNING -> {
                    if (!state.hasRevisionBudget(2)) {
                        return rejected(SessionRejection.RevisionExhausted)
                    }
                    val revision = state.nextRevision()
                    val issued = state.issueGameplayCommand(
                        revision,
                        status.instanceId.runId,
                        GameplaySessionPulse.PauseForOverlay,
                    ) ?: return rejected(SessionRejection.GameplayCommandOrdinalExhausted)
                    return accepted(
                        state.copy(
                            revision = revision,
                            gameplayPhase = status.phase,
                            pendingWorkflow = PendingWorkflow.PausingForOverlay(
                                destination,
                                PendingParticipantCommand.Gameplay(issued.command),
                            ),
                            lastFailure = null,
                            nextGameplayCommandOrdinal = issued.nextOrdinal,
                        ),
                        immutableListOf(AppSessionOutput.SendGameplayCommand(issued.command)),
                    )
                }
                GameplayRunPhase.CREATED,
                GameplayRunPhase.PAUSED,
                GameplayRunPhase.EXITED,
                -> Unit
            }
        }

        val revision = state.nextRevision()
        return accepted(
            state.copy(
                revision = revision,
                overlay = destination,
                gameplayPhase = status?.phase ?: state.gameplayPhase,
                rebirthConfirmation = RebirthConfirmation.Disarmed,
                lastFailure = null,
            ),
        )
    }

    private fun closeOverlay(
        state: AppSessionState,
        context: AppSessionContext,
    ): AppSessionDecision {
        val overlay = state.overlay ?: return rejected(SessionRejection.CloseUnavailable)
        val status = state.requireGameplayStatusIfActive(context)
            ?: if (state.activeRunId != null) return context.invalidGameplayStatus(state) else null
        if (overlay == AppDestination.Settings) {
            return finishSettings(
                state = state,
                context = context,
                status = status,
                continuation = SettingsContinuation.Close,
            )
        }
        val revision = state.nextRevision()
        return accepted(
            state.copy(
                revision = revision,
                overlay = null,
                gameplayPhase = status?.phase ?: state.gameplayPhase,
                rebirthConfirmation = RebirthConfirmation.Disarmed,
                lastFailure = null,
            ),
        )
    }

    private fun finishSettings(
        state: AppSessionState,
        context: AppSessionContext,
        status: kinetickk.ball.gameplay.api.GameplayRunStatusProjection?,
        continuation: SettingsContinuation,
    ): AppSessionDecision {
        val preferences = context.requirePreferences()
            ?: return context.invalidPreferences()
        if (status != null && status.phase.acceptsPreferenceUpdate() && !state.hasRevisionBudget(2)) {
            return rejected(SessionRejection.RevisionExhausted)
        }
        val revision = state.nextRevision()
        if (status != null && status.phase.acceptsPreferenceUpdate()) {
            val issued = state.issueGameplayCommand(
                revision,
                status.instanceId.runId,
                GameplaySessionPulse.ApplyPreferences(preferences.preferences),
            ) ?: return rejected(SessionRejection.GameplayCommandOrdinalExhausted)
            return accepted(
                state.copy(
                    revision = revision,
                    gameplayPhase = status.phase,
                    pendingWorkflow = PendingWorkflow.ApplyingSettings(
                        preferences = preferences.preferences,
                        continuation = continuation,
                        participant = PendingParticipantCommand.Gameplay(issued.command),
                    ),
                    lastFailure = null,
                    nextGameplayCommandOrdinal = issued.nextOrdinal,
                ),
                immutableListOf(AppSessionOutput.SendGameplayCommand(issued.command)),
            )
        }
        return accepted(
            state.copy(
                revision = revision,
                overlay = continuation.overlayAfterCompletion,
                gameplayPhase = status?.phase ?: state.gameplayPhase,
                rebirthConfirmation = RebirthConfirmation.Disarmed,
                lastFailure = null,
            ),
            immutableListOf(
                AppSessionOutput.SynchronizeAudioPreferences(preferences.preferences),
            ),
        )
    }

    private fun shortcut(
        state: AppSessionState,
        shortcut: SessionShortcut,
        context: AppSessionContext,
    ): AppSessionDecision = when (shortcut) {
        SessionShortcut.SETTINGS -> openOverlay(state, AppDestination.Settings, context)
        SessionShortcut.LAB -> openOverlay(state, AppDestination.Lab, context)
        SessionShortcut.ARMORY -> openOverlay(state, AppDestination.Armory, context)
        SessionShortcut.REBIRTH -> openOverlay(state, AppDestination.Rebirth, context)
        SessionShortcut.CODEX -> openOverlay(state, AppDestination.Codex, context)
        SessionShortcut.MUTE -> toggleMute(state)
        SessionShortcut.BACK -> if (state.overlay != null) {
            closeOverlay(state, context)
        } else {
            rejected(SessionRejection.ShortcutUnavailable)
        }
        SessionShortcut.ENTER -> when {
            state.overlay != null -> closeOverlay(state, context)
            state.base == AppDestination.Home -> startRun(state, context, RunStartReason.START)
            else -> rejected(SessionRejection.ShortcutUnavailable)
        }
    }

    private fun toggleMute(state: AppSessionState): AppSessionDecision {
        val propagatesToGameplay =
            state.activeRunId != null && state.gameplayPhase?.acceptsPreferenceUpdate() == true
        if (!state.hasRevisionBudget(if (propagatesToGameplay) 3 else 2)) {
            return rejected(SessionRejection.RevisionExhausted)
        }
        if (propagatesToGameplay && state.nextGameplayCommandOrdinal == null) {
            return rejected(SessionRejection.GameplayCommandOrdinalExhausted)
        }
        val revision = state.nextRevision()
        val issued = state.issueProfileCommand(revision, ProfilePulse.ToggleMute)
            ?: return rejected(SessionRejection.ProfileCommandOrdinalExhausted)
        return accepted(
            state.copy(
                revision = revision,
                pendingWorkflow = PendingWorkflow.TogglingMute(
                    PendingParticipantCommand.Profile(issued.command),
                ),
                lastFailure = null,
                nextProfileCommandOrdinal = issued.nextOrdinal,
            ),
            immutableListOf(AppSessionOutput.SendProfileCommand(issued.command)),
        )
    }

    private fun selectCoreShape(
        state: AppSessionState,
        pulse: SessionInteractionPulse.SelectCoreShapeRequested,
    ): AppSessionDecision {
        if (state.base != AppDestination.Home || state.overlay != null) {
            return rejected(SessionRejection.CoreShapeSelectionUnavailable)
        }
        if (!state.hasRevisionBudget(2)) {
            return rejected(SessionRejection.RevisionExhausted)
        }
        val revision = state.nextRevision()
        val issued = state.issueProfileCommand(
            revision,
            ProfilePulse.SelectCoreShape(pulse.shape),
        ) ?: return rejected(SessionRejection.ProfileCommandOrdinalExhausted)
        return accepted(
            state.copy(
                revision = revision,
                pendingWorkflow = PendingWorkflow.SelectingCoreShape(
                    pulse.shape,
                    PendingParticipantCommand.Profile(issued.command),
                ),
                lastFailure = null,
                nextProfileCommandOrdinal = issued.nextOrdinal,
            ),
            immutableListOf(AppSessionOutput.SendProfileCommand(issued.command)),
        )
    }

    private fun rebirth(
        state: AppSessionState,
        context: AppSessionContext,
    ): AppSessionDecision {
        if (
            state.overlay != AppDestination.Rebirth ||
            state.base != AppDestination.Home && state.gameplayPhase != GameplayRunPhase.VICTORY
        ) {
            return rejected(SessionRejection.RebirthUnavailable)
        }
        val projection = context.requireRebirthProgress()
            ?: return context.invalidRebirthProgress()
        if (!projection.canAdvance) {
            return rejected(SessionRejection.RebirthUnavailable)
        }
        return when (val confirmation = state.rebirthConfirmation) {
            RebirthConfirmation.Disarmed -> {
                val revision = state.nextRevision()
                accepted(
                    state.copy(
                        revision = revision,
                        rebirthConfirmation = RebirthConfirmation.Armed(
                            profileRevision = projection.revision,
                            progress = projection.snapshot.progress,
                        ),
                        lastFailure = null,
                    ),
                )
            }
            is RebirthConfirmation.Armed -> {
                // Mute may advance Profile revision while the same Rebirth progress stays armed.
                if (
                    projection.revision.value < confirmation.profileRevision.value ||
                    projection.snapshot.progress != confirmation.progress
                ) {
                    return rejected(SessionRejection.RebirthUnavailable)
                }
                if (state.reusableCreatedRunId() == null && state.nextRunId == null) {
                    return rejected(SessionRejection.RunIdExhausted)
                }
                if (state.nextGameplayCommandOrdinal == null) {
                    return rejected(SessionRejection.GameplayCommandOrdinalExhausted)
                }
                if (!state.hasRevisionBudget(3)) {
                    return rejected(SessionRejection.RevisionExhausted)
                }
                val revision = state.nextRevision()
                val issued = state.issueProfileCommand(revision, ProfilePulse.AdvanceRebirth)
                    ?: return rejected(SessionRejection.ProfileCommandOrdinalExhausted)
                accepted(
                    state.copy(
                        revision = revision,
                        pendingWorkflow = PendingWorkflow.AdvancingRebirth(
                            PendingParticipantCommand.Profile(issued.command),
                        ),
                        rebirthConfirmation = RebirthConfirmation.Disarmed,
                        lastFailure = null,
                        nextProfileCommandOrdinal = issued.nextOrdinal,
                    ),
                    immutableListOf(AppSessionOutput.SendProfileCommand(issued.command)),
                )
            }
        }
    }

    private fun exitRun(
        state: AppSessionState,
        context: AppSessionContext,
    ): AppSessionDecision {
        if (state.base != AppDestination.Gameplay || state.overlay != null) {
            return rejected(SessionRejection.ExitUnavailable)
        }
        val status = state.requireGameplayStatusIfActive(context)
            ?: return context.invalidGameplayStatus(state)
        if (!status.phase.canExit()) {
            return rejected(SessionRejection.ExitUnavailable)
        }
        if (!state.hasRevisionBudget(2)) {
            return rejected(SessionRejection.RevisionExhausted)
        }
        val revision = state.nextRevision()
        val issued = state.issueGameplayCommand(
            revision,
            status.instanceId.runId,
            GameplaySessionPulse.ExitRun,
        ) ?: return rejected(SessionRejection.GameplayCommandOrdinalExhausted)
        return accepted(
            state.copy(
                revision = revision,
                gameplayPhase = status.phase,
                pendingWorkflow = PendingWorkflow.ExitingRun(
                    PendingParticipantCommand.Gameplay(issued.command),
                ),
                lastFailure = null,
                nextGameplayCommandOrdinal = issued.nextOrdinal,
            ),
            immutableListOf(AppSessionOutput.SendGameplayCommand(issued.command)),
        )
    }

    private fun cancelReset(state: AppSessionState): AppSessionDecision {
        if (state.resetLifecycle == SessionResetLifecycle.READY) {
            return rejected(SessionRejection.ResetActionUnavailable)
        }
        return accepted(state.copy(revision = state.nextRevision()))
    }

    private fun confirmReset(
        state: AppSessionState,
        context: AppSessionContext,
    ): AppSessionDecision {
        if (state.resetLifecycle != SessionResetLifecycle.CONFIRMATION_REQUIRED) {
            return rejected(SessionRejection.ResetActionUnavailable)
        }
        val persistence = context.requirePersistenceStatus()
            ?: return context.invalidPersistenceStatus()
        if (persistence.toSessionResetLifecycle() != SessionResetLifecycle.CONFIRMATION_REQUIRED) {
            return rejected(SessionRejection.ResetActionUnavailable)
        }
        if (!state.hasRevisionBudget(2)) {
            return rejected(SessionRejection.RevisionExhausted)
        }
        val revision = state.nextRevision()
        val issued = state.issueProfileCommand(revision, ProfilePulse.ConfirmLegacyReset)
            ?: return rejected(SessionRejection.ProfileCommandOrdinalExhausted)
        return accepted(
            state.copy(
                revision = revision,
                pendingWorkflow = PendingWorkflow.ConfirmingReset(
                    PendingParticipantCommand.Profile(issued.command),
                ),
                resetLifecycle = SessionResetLifecycle.RESET_IN_PROGRESS,
                lastFailure = null,
                nextProfileCommandOrdinal = issued.nextOrdinal,
            ),
            immutableListOf(AppSessionOutput.SendProfileCommand(issued.command)),
        )
    }

    private fun retryReset(
        state: AppSessionState,
        context: AppSessionContext,
    ): AppSessionDecision {
        if (state.resetLifecycle != SessionResetLifecycle.PURGE_NEEDS_ATTENTION) {
            return rejected(SessionRejection.ResetActionUnavailable)
        }
        val persistence = context.requirePersistenceStatus()
            ?: return context.invalidPersistenceStatus()
        if (persistence.toSessionResetLifecycle() != SessionResetLifecycle.PURGE_NEEDS_ATTENTION) {
            return rejected(SessionRejection.ResetActionUnavailable)
        }
        if (!state.hasRevisionBudget(2)) {
            return rejected(SessionRejection.RevisionExhausted)
        }
        val revision = state.nextRevision()
        val issued = state.issueProfileCommand(revision, ProfilePulse.RetryLegacyPurge)
            ?: return rejected(SessionRejection.ProfileCommandOrdinalExhausted)
        return accepted(
            state.copy(
                revision = revision,
                pendingWorkflow = PendingWorkflow.RetryingPurge(
                    PendingParticipantCommand.Profile(issued.command),
                ),
                resetLifecycle = SessionResetLifecycle.RESET_IN_PROGRESS,
                lastFailure = null,
                nextProfileCommandOrdinal = issued.nextOrdinal,
            ),
            immutableListOf(AppSessionOutput.SendProfileCommand(issued.command)),
        )
    }

    private fun completeProfileCommand(
        state: AppSessionState,
        pulse: SessionControlPulse.ProfileCommandCompleted,
        context: AppSessionContext,
    ): AppSessionDecision {
        val pending = state.pendingWorkflow
            ?: return unexpected(SessionParticipantResultRejection.NO_COMMAND_PENDING)
        val participant = pending.participant as? PendingParticipantCommand.Profile
            ?: return unexpected(SessionParticipantResultRejection.PARTICIPANT_MISMATCH)
        if (pulse.result.commandRef != participant.command.ref) {
            return unexpected(SessionParticipantResultRejection.COMMAND_REF_MISMATCH)
        }
        return when (pending) {
            is PendingWorkflow.SelectingCoreShape -> {
                val outcome = pulse.result.outcome as? ProfileCommandOutcome.CoreShapeSelected
                    ?: return unexpected(SessionParticipantResultRejection.OUTCOME_MISMATCH)
                if (outcome.shape != pending.shape) {
                    return unexpected(SessionParticipantResultRejection.OUTCOME_MISMATCH)
                }
                accepted(
                    state.copy(
                        revision = state.nextRevision(),
                        pendingWorkflow = null,
                        lastFailure = null,
                    ),
                )
            }
            is PendingWorkflow.TogglingMute -> completeMuteProfileResult(
                state,
                pulse.result.outcome,
            )
            is PendingWorkflow.AdvancingRebirth -> completeRebirthProfileResult(
                state,
                pulse.result.outcome,
                context,
            )
            is PendingWorkflow.ConfirmingReset,
            is PendingWorkflow.RetryingPurge,
            -> completeResetProfileResult(state, pulse.result.outcome, context)
            is PendingWorkflow.StartingRun,
            is PendingWorkflow.PausingForOverlay,
            is PendingWorkflow.ApplyingSettings,
            is PendingWorkflow.PropagatingMute,
            is PendingWorkflow.StartingRebirthRun,
            is PendingWorkflow.ExitingRun,
            -> unexpected(SessionParticipantResultRejection.PARTICIPANT_MISMATCH)
        }
    }

    private fun completeMuteProfileResult(
        state: AppSessionState,
        outcome: ProfileCommandOutcome,
    ): AppSessionDecision {
        val preferences = (outcome as? ProfileCommandOutcome.PreferencesChanged)?.preferences
            ?: return unexpected(SessionParticipantResultRejection.OUTCOME_MISMATCH)
        val revision = state.nextRevision()
        if (state.activeRunId != null && state.gameplayPhase?.acceptsPreferenceUpdate() == true) {
            val issued = state.issueGameplayCommand(
                revision,
                state.activeRunId,
                GameplaySessionPulse.ApplyPreferences(preferences),
            ) ?: return rejected(SessionRejection.GameplayCommandOrdinalExhausted)
            return accepted(
                state.copy(
                    revision = revision,
                    pendingWorkflow = PendingWorkflow.PropagatingMute(
                        preferences,
                        PendingParticipantCommand.Gameplay(issued.command),
                    ),
                    lastFailure = null,
                    nextGameplayCommandOrdinal = issued.nextOrdinal,
                ),
                immutableListOf(
                    AppSessionOutput.SendGameplayCommand(issued.command),
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

    private fun completeRebirthProfileResult(
        state: AppSessionState,
        outcome: ProfileCommandOutcome,
        context: AppSessionContext,
    ): AppSessionDecision {
        val advanced = outcome as? ProfileCommandOutcome.RebirthAdvanced
            ?: return unexpected(SessionParticipantResultRejection.OUTCOME_MISMATCH)
        val bootstrap = context.requireRunBootstrap()
            ?: return context.invalidRunBootstrap()
        val ready = bootstrap.result as? ProfileRunBootstrapResult.Ready
            ?: return unexpected(SessionParticipantResultRejection.OUTCOME_MISMATCH)
        if (ready.snapshot.rebirthProgress != advanced.progress) {
            return unexpected(SessionParticipantResultRejection.OUTCOME_MISMATCH)
        }
        val reusableRunId = state.reusableCreatedRunId()
        val reservation = if (reusableRunId != null) {
            RunReservation(
                runId = reusableRunId,
                nextRunId = state.nextRunId,
                ensure = false,
            )
        } else {
            state.reserveRun() ?: return rejected(SessionRejection.RunIdExhausted)
        }
        val revision = state.nextRevision()
        val issued = state.issueGameplayCommand(
            revision,
            reservation.runId,
            GameplaySessionPulse.StartRun(
                RunConfiguration(state.content, ready.snapshot),
            ),
        ) ?: return rejected(SessionRejection.GameplayCommandOrdinalExhausted)
        val next = state.copy(
            revision = revision,
            activeRunId = reservation.runId,
            gameplayPhase = GameplayRunPhase.CREATED,
            pendingWorkflow = PendingWorkflow.StartingRebirthRun(
                reservation.runId,
                PendingParticipantCommand.Gameplay(issued.command),
            ),
            rebirthConfirmation = RebirthConfirmation.Disarmed,
            lastFailure = null,
            nextRunId = reservation.nextRunId,
            nextGameplayCommandOrdinal = issued.nextOrdinal,
        )
        val send = AppSessionOutput.SendGameplayCommand(issued.command)
        val outputs = if (reservation.ensure) {
            immutableListOf(
                AppSessionOutput.EnsureGameplayRun(reservation.runId),
                send,
                AppSessionOutput.PlayRebirthAcceptedFeedback,
            )
        } else {
            immutableListOf(
                send,
                AppSessionOutput.PlayRebirthAcceptedFeedback,
            )
        }
        return accepted(next, outputs)
    }

    private fun completeResetProfileResult(
        state: AppSessionState,
        outcome: ProfileCommandOutcome,
        context: AppSessionContext,
    ): AppSessionDecision {
        val persistence = context.requirePersistenceStatus()
            ?: return context.invalidPersistenceStatus()
        val preferences = context.requirePreferences()
            ?: return context.invalidPreferences()
        val lifecycle = persistence.toSessionResetLifecycle()
        val failure = when (outcome) {
            ProfileCommandOutcome.ResetCompleted -> {
                if (lifecycle != SessionResetLifecycle.READY) {
                    return unexpected(SessionParticipantResultRejection.OUTCOME_MISMATCH)
                }
                null
            }
            is ProfileCommandOutcome.ResetWriteRejected -> {
                if (lifecycle != SessionResetLifecycle.CONFIRMATION_REQUIRED) {
                    return unexpected(SessionParticipantResultRejection.OUTCOME_MISMATCH)
                }
                SessionWorkflowFailure.ResetWriteRejected(outcome.reason)
            }
            is ProfileCommandOutcome.ResetWriteOutcomeUnknown -> {
                if (lifecycle != SessionResetLifecycle.CONFIRMATION_REQUIRED) {
                    return unexpected(SessionParticipantResultRejection.OUTCOME_MISMATCH)
                }
                SessionWorkflowFailure.ResetWriteOutcomeUnknown(outcome.reason)
            }
            is ProfileCommandOutcome.ResetNeedsAttention -> {
                if (
                    lifecycle != SessionResetLifecycle.PURGE_NEEDS_ATTENTION ||
                    persistence.reset != outcome.status
                ) {
                    return unexpected(SessionParticipantResultRejection.OUTCOME_MISMATCH)
                }
                SessionWorkflowFailure.ResetNeedsAttention(outcome.status)
            }
            is ProfileCommandOutcome.CoreShapeSelected,
            is ProfileCommandOutcome.PreferencesChanged,
            is ProfileCommandOutcome.RebirthAdvanced,
            ProfileCommandOutcome.GameplayProgressApplied,
            -> return unexpected(SessionParticipantResultRejection.OUTCOME_MISMATCH)
        }
        return accepted(
            state.copy(
                revision = state.nextRevision(),
                pendingWorkflow = null,
                resetLifecycle = lifecycle,
                lastFailure = failure,
            ),
            immutableListOf(
                AppSessionOutput.SynchronizeAudioPreferences(preferences.preferences),
            ),
        )
    }

    private fun rejectProfileCommandBeforeAcceptance(
        state: AppSessionState,
        pulse: SessionControlPulse.ProfileCommandRejectedBeforeAcceptance,
    ): AppSessionDecision {
        val pending = state.pendingWorkflow
            ?: return unexpected(SessionParticipantResultRejection.NO_COMMAND_PENDING)
        val participant = pending.participant as? PendingParticipantCommand.Profile
            ?: return unexpected(SessionParticipantResultRejection.PARTICIPANT_MISMATCH)
        if (pulse.commandRef != participant.command.ref) {
            return unexpected(SessionParticipantResultRejection.COMMAND_REF_MISMATCH)
        }
        if (pulse.rejection.instanceId != pulse.commandRef.targetInstance) {
            return unexpected(SessionParticipantResultRejection.TARGET_INSTANCE_MISMATCH)
        }
        val resetLifecycle = when (pending) {
            is PendingWorkflow.ConfirmingReset -> SessionResetLifecycle.CONFIRMATION_REQUIRED
            is PendingWorkflow.RetryingPurge -> SessionResetLifecycle.PURGE_NEEDS_ATTENTION
            else -> state.resetLifecycle
        }
        val outputs: ImmutableList<AppSessionOutput> = if (pending is PendingWorkflow.TogglingMute) {
            immutableListOf(AppSessionOutput.PlayMuteFeedback)
        } else {
            immutableListOf()
        }
        return accepted(
            state.copy(
                revision = state.nextRevision(),
                pendingWorkflow = null,
                resetLifecycle = resetLifecycle,
                rebirthConfirmation = if (pending is PendingWorkflow.AdvancingRebirth) {
                    RebirthConfirmation.Disarmed
                } else {
                    state.rebirthConfirmation
                },
                lastFailure = SessionWorkflowFailure.ProfileCommandRejected(
                    commandRef = pulse.commandRef,
                    observedRevision = pulse.rejection.observedRevision,
                    reason = pulse.rejection.reason,
                ),
            ),
            outputs,
        )
    }

    private fun completeGameplayCommand(
        state: AppSessionState,
        pulse: SessionControlPulse.GameplayCommandCompleted,
    ): AppSessionDecision {
        val pending = state.pendingWorkflow
            ?: return unexpected(SessionParticipantResultRejection.NO_COMMAND_PENDING)
        val participant = pending.participant as? PendingParticipantCommand.Gameplay
            ?: return unexpected(SessionParticipantResultRejection.PARTICIPANT_MISMATCH)
        if (pulse.result.commandRef != participant.command.ref) {
            return unexpected(SessionParticipantResultRejection.COMMAND_REF_MISMATCH)
        }
        return when (pending) {
            is PendingWorkflow.StartingRun,
            is PendingWorkflow.StartingRebirthRun,
            -> {
                if (pulse.result.outcome != GameplayCommandOutcome.RunStarted) {
                    return unexpected(SessionParticipantResultRejection.OUTCOME_MISMATCH)
                }
                accepted(
                    state.copy(
                        revision = state.nextRevision(),
                        base = AppDestination.Gameplay,
                        overlay = null,
                        gameplayPhase = GameplayRunPhase.RUNNING,
                        pendingWorkflow = null,
                        rebirthConfirmation = RebirthConfirmation.Disarmed,
                        lastFailure = null,
                    ),
                )
            }
            is PendingWorkflow.PausingForOverlay -> {
                if (pulse.result.outcome != GameplayCommandOutcome.OverlayPaused) {
                    return unexpected(SessionParticipantResultRejection.OUTCOME_MISMATCH)
                }
                accepted(
                    state.copy(
                        revision = state.nextRevision(),
                        overlay = pending.destination,
                        gameplayPhase = GameplayRunPhase.PAUSED,
                        pendingWorkflow = null,
                        rebirthConfirmation = RebirthConfirmation.Disarmed,
                        lastFailure = null,
                    ),
                )
            }
            is PendingWorkflow.ApplyingSettings -> {
                val outcome = pulse.result.outcome as? GameplayCommandOutcome.PreferencesApplied
                    ?: return unexpected(SessionParticipantResultRejection.OUTCOME_MISMATCH)
                if (outcome.preferences != pending.preferences) {
                    return unexpected(SessionParticipantResultRejection.OUTCOME_MISMATCH)
                }
                accepted(
                    state.copy(
                        revision = state.nextRevision(),
                        overlay = pending.continuation.overlayAfterCompletion,
                        pendingWorkflow = null,
                        rebirthConfirmation = RebirthConfirmation.Disarmed,
                        lastFailure = null,
                    ),
                    immutableListOf(
                        AppSessionOutput.SynchronizeAudioPreferences(pending.preferences),
                    ),
                )
            }
            is PendingWorkflow.PropagatingMute -> {
                val outcome = pulse.result.outcome as? GameplayCommandOutcome.PreferencesApplied
                    ?: return unexpected(SessionParticipantResultRejection.OUTCOME_MISMATCH)
                if (outcome.preferences != pending.preferences) {
                    return unexpected(SessionParticipantResultRejection.OUTCOME_MISMATCH)
                }
                accepted(
                    state.copy(
                        revision = state.nextRevision(),
                        pendingWorkflow = null,
                        lastFailure = null,
                    ),
                )
            }
            is PendingWorkflow.ExitingRun -> completeExitResult(state, pulse.result.outcome)
            is PendingWorkflow.SelectingCoreShape,
            is PendingWorkflow.TogglingMute,
            is PendingWorkflow.AdvancingRebirth,
            is PendingWorkflow.ConfirmingReset,
            is PendingWorkflow.RetryingPurge,
            -> unexpected(SessionParticipantResultRejection.PARTICIPANT_MISMATCH)
        }
    }

    private fun completeExitResult(
        state: AppSessionState,
        outcome: GameplayCommandOutcome,
    ): AppSessionDecision {
        val exited = outcome as? GameplayCommandOutcome.RunExited
            ?: return unexpected(SessionParticipantResultRejection.OUTCOME_MISMATCH)
        val revision = state.nextRevision()
        return when (val profile = exited.profile) {
            GameplayExitProfileOutcome.NoProgress,
            GameplayExitProfileOutcome.ProgressApplied,
            -> accepted(
                state.copy(
                    revision = revision,
                    base = AppDestination.Home,
                    overlay = null,
                    gameplayPhase = GameplayRunPhase.EXITED,
                    pendingWorkflow = null,
                    rebirthConfirmation = RebirthConfirmation.Disarmed,
                    lastFailure = null,
                ),
            )
            is GameplayExitProfileOutcome.ProgressRejected -> accepted(
                state.copy(
                    revision = revision,
                    gameplayPhase = GameplayRunPhase.EXITED,
                    pendingWorkflow = null,
                    rebirthConfirmation = RebirthConfirmation.Disarmed,
                    lastFailure = SessionWorkflowFailure.ExitProgressRejected(profile),
                ),
            )
        }
    }

    private fun rejectGameplayCommandBeforeAcceptance(
        state: AppSessionState,
        pulse: SessionControlPulse.GameplayCommandRejectedBeforeAcceptance,
    ): AppSessionDecision {
        val pending = state.pendingWorkflow
            ?: return unexpected(SessionParticipantResultRejection.NO_COMMAND_PENDING)
        val participant = pending.participant as? PendingParticipantCommand.Gameplay
            ?: return unexpected(SessionParticipantResultRejection.PARTICIPANT_MISMATCH)
        if (pulse.commandRef != participant.command.ref) {
            return unexpected(SessionParticipantResultRejection.COMMAND_REF_MISMATCH)
        }
        if (pulse.rejection.instanceId != pulse.commandRef.targetInstance) {
            return unexpected(SessionParticipantResultRejection.TARGET_INSTANCE_MISMATCH)
        }
        val failedStart = pending is PendingWorkflow.StartingRun ||
            pending is PendingWorkflow.StartingRebirthRun
        return accepted(
            state.copy(
                revision = state.nextRevision(),
                base = if (failedStart) AppDestination.Home else state.base,
                overlay = if (failedStart) null else state.overlay,
                gameplayPhase = if (failedStart) GameplayRunPhase.CREATED else state.gameplayPhase,
                pendingWorkflow = null,
                lastFailure = SessionWorkflowFailure.GameplayCommandRejected(
                    pulse.commandRef,
                    pulse.rejection,
                ),
            ),
        )
    }
}

private data class IssuedProfileCommand(
    val command: ProfileCommand,
    val nextOrdinal: Int?,
)

private data class IssuedGameplayCommand(
    val command: GameplayCommand,
    val nextOrdinal: Int?,
)

private data class RunReservation(
    val runId: RunId,
    val nextRunId: RunId?,
    val ensure: Boolean = true,
)

private fun AppSessionState.nextRevision(): SessionRevision =
    SessionRevision(revision.value + 1L)

private fun AppSessionState.hasRevisionBudget(acceptedFrames: Int): Boolean =
    revision.value <= Long.MAX_VALUE - acceptedFrames.toLong()

private fun AppSessionState.issueProfileCommand(
    revision: SessionRevision,
    pulse: ProfilePulse.Business,
): IssuedProfileCommand? {
    val ordinal = nextProfileCommandOrdinal ?: return null
    val ref = ProfileCommandRef(
        sourceInstance = ProfileCommandSource.LocalSession,
        targetInstance = LOCAL_PROFILE_INSTANCE_ID,
        sourceRevision = revision.value,
        ordinal = ordinal,
    )
    return IssuedProfileCommand(
        command = ProfileCommand(ref, pulse),
        nextOrdinal = ordinal.nextOrdinal(),
    )
}

private fun AppSessionState.issueGameplayCommand(
    revision: SessionRevision,
    runId: RunId,
    pulse: GameplaySessionPulse,
): IssuedGameplayCommand? {
    val ordinal = nextGameplayCommandOrdinal ?: return null
    val ref = GameplayCommandRef(
        sourceInstance = GameplayCommandSource.LocalSession,
        targetInstance = GameplayInstanceId(runId),
        sourceRevision = revision.value,
        ordinal = ordinal,
    )
    return IssuedGameplayCommand(
        command = GameplayCommand(ref, pulse),
        nextOrdinal = ordinal.nextOrdinal(),
    )
}

private fun AppSessionState.reserveRun(): RunReservation? {
    val reserved = nextRunId ?: return null
    val next = if (reserved.value == Long.MAX_VALUE) null else RunId(reserved.value + 1L)
    return RunReservation(reserved, next)
}

private fun AppSessionState.reusableCreatedRunId(): RunId? =
    activeRunId?.takeIf { gameplayPhase == GameplayRunPhase.CREATED }

private fun Int.nextOrdinal(): Int? = if (this == Int.MAX_VALUE) null else this + 1

private fun AppSessionState.requireGameplayStatusIfActive(
    context: AppSessionContext,
): kinetickk.ball.gameplay.api.GameplayRunStatusProjection? {
    val runId = activeRunId ?: return null
    val status = context.gameplayStatus ?: return null
    return status.takeIf { it.instanceId.runId == runId }
}

private fun AppSessionContext.requireRunBootstrap(): RunBootstrapProjection? =
    runBootstrap?.takeIf { it.instanceId == LOCAL_PROFILE_INSTANCE_ID }

private fun AppSessionContext.requirePreferences(): PreferencesProjection? =
    preferences?.takeIf { it.instanceId == LOCAL_PROFILE_INSTANCE_ID }

private fun AppSessionContext.requireRebirthProgress(): RebirthProgressProjection? =
    rebirthProgress?.takeIf { it.instanceId == LOCAL_PROFILE_INSTANCE_ID }

private fun AppSessionContext.requirePersistenceStatus(): PersistenceStatusProjection? =
    persistenceStatus?.takeIf { it.instanceId == LOCAL_PROFILE_INSTANCE_ID }

private fun AppSessionContext.invalidRunBootstrap(): AppSessionDecision = rejected(
    SessionRejection.InvalidContext(
        SessionContextField.RUN_BOOTSTRAP,
        if (runBootstrap == null) SessionContextReason.MISSING else SessionContextReason.WRONG_INSTANCE,
    ),
)

private fun AppSessionContext.invalidPreferences(): AppSessionDecision = rejected(
    SessionRejection.InvalidContext(
        SessionContextField.PREFERENCES,
        if (preferences == null) SessionContextReason.MISSING else SessionContextReason.WRONG_INSTANCE,
    ),
)

private fun AppSessionContext.invalidRebirthProgress(): AppSessionDecision = rejected(
    SessionRejection.InvalidContext(
        SessionContextField.REBIRTH_PROGRESS,
        if (rebirthProgress == null) SessionContextReason.MISSING else SessionContextReason.WRONG_INSTANCE,
    ),
)

private fun AppSessionContext.invalidPersistenceStatus(): AppSessionDecision = rejected(
    SessionRejection.InvalidContext(
        SessionContextField.PERSISTENCE_STATUS,
        if (persistenceStatus == null) SessionContextReason.MISSING else SessionContextReason.WRONG_INSTANCE,
    ),
)

private fun AppSessionContext.invalidGameplayStatus(
    state: AppSessionState,
): AppSessionDecision = rejected(
    SessionRejection.InvalidContext(
        SessionContextField.GAMEPLAY_STATUS,
        when {
            gameplayStatus == null -> SessionContextReason.MISSING
            gameplayStatus.instanceId.runId != state.activeRunId -> SessionContextReason.WRONG_RUN
            else -> SessionContextReason.WRONG_INSTANCE
        },
    ),
)

private fun GameplayRunPhase.canBeReplaced(): Boolean = when (this) {
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

private fun GameplayRunPhase.acceptsPreferenceUpdate(): Boolean = when (this) {
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

private fun GameplayRunPhase.canExit(): Boolean = acceptsPreferenceUpdate()

private val SettingsContinuation.overlayAfterCompletion: AppDestination?
    get() = when (this) {
        SettingsContinuation.Close -> null
        is SettingsContinuation.Open -> destination
    }

private fun AppSessionState.toShellProjection(): AppShellProjection = AppShellProjection(
    instanceId = instanceId,
    revision = revision,
    base = base,
    overlay = overlay,
    activeRunId = activeRunId,
    gameplayPhase = gameplayPhase,
    pendingWorkflow = pendingWorkflow?.toProjection(),
    resetLifecycle = resetLifecycle,
    rebirthConfirmationArmed = rebirthConfirmation is RebirthConfirmation.Armed,
    workflowFailure = lastFailure,
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
    is PendingWorkflow.AdvancingRebirth -> SessionWorkflowPhase.ADVANCING_REBIRTH
    is PendingWorkflow.StartingRebirthRun -> SessionWorkflowPhase.STARTING_REBIRTH_RUN
    is PendingWorkflow.ExitingRun -> SessionWorkflowPhase.EXITING_RUN
    is PendingWorkflow.ConfirmingReset -> SessionWorkflowPhase.CONFIRMING_RESET
    is PendingWorkflow.RetryingPurge -> SessionWorkflowPhase.RETRYING_PURGE
}

private fun accepted(
    nextState: AppSessionState,
    outputs: ImmutableList<AppSessionOutput> = immutableListOf(),
): AppSessionDecision = AppSessionDecision.Accepted(
    AppSessionAcceptedFrame(
        nextState = nextState,
        shellProjection = nextState.toShellProjection(),
        outputs = outputs,
    ),
)

private fun rejected(reason: SessionRejection): AppSessionDecision =
    AppSessionDecision.Rejected(reason)

private fun unexpected(reason: SessionParticipantResultRejection): AppSessionDecision =
    rejected(SessionRejection.UnexpectedParticipantResult(reason))
