// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.nucleus

import kinetickk.ball.gameplay.api.GameplayCommandIssuerProvenance
import kinetickk.ball.gameplay.api.GameplayCommandSource
import kinetickk.ball.gameplay.api.GameplayEffectiveProtocolIdentity
import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.gameplay.api.GameplayModuleCommand
import kinetickk.ball.gameplay.api.GameplayModuleCommandRequest
import kinetickk.ball.gameplay.api.GameplayModuleResult
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.GameplaySemanticHandle
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfileCommandIssuerProvenance
import kinetickk.ball.profile.api.ProfileCommandSource
import kinetickk.ball.profile.api.ProfileEffectiveProtocolIdentity
import kinetickk.ball.profile.api.ProfileModuleCommand
import kinetickk.ball.profile.api.ProfileModuleCommandRequest
import kinetickk.ball.profile.api.ProfileModuleResult
import kinetickk.ball.profile.api.ProfileResetStatus
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.ProfileSemanticHandle
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.AppSessionQuery
import kinetickk.flow.session.api.AppShellProjection
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.api.SessionRejection
import kinetickk.flow.session.api.SessionResetLifecycle
import kinetickk.flow.session.api.SessionRevision
import kinetickk.flow.session.api.SessionShortcut
import kinetickk.flow.session.api.SessionWorkflowFailureCode
import kinetickk.flow.session.api.SessionWorkflowPhase
import kinetickk.flow.session.api.isOverlayDestination
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

/** Pure coordinator for the singleton AppSession Flow. */
object AppSessionNucleus {
    fun decide(
        state: AppSessionState,
        pulse: AppSessionNucleusPulse,
        context: AppSessionContext = AppSessionContext.Empty,
    ): AppSessionDecision = when (pulse) {
        is AppSessionNucleusPulse.Intent -> decideInteraction(state, pulse.intent, context)
        is ProfileModuleResultPulse -> completeProfileCommand(state, pulse, context)
        is GameplayModuleResultPulse -> completeGameplayCommand(state, pulse)
        is ProfileCommandRejectedBeforeAcceptance -> rejectProfileCommandBeforeAcceptance(state, pulse)
        is GameplayCommandRejectedBeforeAcceptance -> rejectGameplayCommandBeforeAcceptance(state, pulse)
    }

    fun query(
        state: AppSessionState,
        query: AppSessionQuery.GetShell,
    ): AppShellProjection = when (query) {
        AppSessionQuery.GetShell -> state.toShellProjection()
    }

    private fun decideInteraction(
        state: AppSessionState,
        intent: SessionInteractionPulse,
        context: AppSessionContext,
    ): AppSessionDecision {
        if (state.pendingWorkflow != null) {
            return rejected(SessionRejection.ParticipantCommandPending)
        }
        if (
            state.resetLifecycle != SessionResetLifecycle.READY &&
            intent != SessionInteractionPulse.ResetCancelled &&
            intent != SessionInteractionPulse.ResetConfirmed &&
            intent != SessionInteractionPulse.ResetRetryRequested
        ) {
            return rejected(SessionRejection.ResetBlocksInput)
        }
        return when (intent) {
            SessionInteractionPulse.StartRunRequested -> startRun(state, context, RunStartReason.START)
            SessionInteractionPulse.RestartRunRequested -> startRun(state, context, RunStartReason.RESTART)
            SessionInteractionPulse.ExitRunRequested -> exitRun(state, context)
            is SessionInteractionPulse.OpenOverlay -> openOverlay(state, intent.destination, context)
            SessionInteractionPulse.CloseOverlay -> closeOverlay(state, context)
            is SessionInteractionPulse.ShortcutObserved -> shortcut(state, intent.shortcut, context)
            SessionInteractionPulse.ToggleMuteRequested -> toggleMute(state)
            is SessionInteractionPulse.SelectCoreShapeRequested -> selectCoreShape(state, intent.shape)
            SessionInteractionPulse.RebirthRequested -> rebirth(state, context)
            SessionInteractionPulse.ResetCancelled -> cancelReset(state)
            SessionInteractionPulse.ResetConfirmed -> confirmReset(state, context)
            SessionInteractionPulse.ResetRetryRequested -> retryReset(state, context)
        }
    }

    private fun startRun(
        state: AppSessionState,
        context: AppSessionContext,
        reason: RunStartReason,
    ): AppSessionDecision {
        val bootstrap = checkNotNull(context.runBootstrap) {
            "Impl must supply the validated Profile run bootstrap read"
        }
        if (bootstrap.result !is ProfileRunBootstrapResult.Ready || state.overlay != null) {
            return rejected(reason.startUnavailableReason)
        }
        val status = state.gameplayStatusIfActive(context)
        val reservation = when (reason) {
            RunStartReason.START -> {
                if (state.base != AppDestination.Home) {
                    return rejected(SessionRejection.StartUnavailable)
                }
                if (status?.phase == GameplayRunPhase.CREATED && !status.profileCommandPending) {
                    RunReservation(status.instanceId.runId, state.nextRunId, ensure = false)
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
        val request = gameplayRequest(
            revision = revision,
            sourceOrdinal = if (reservation.ensure) 1 else 0,
            runId = reservation.runId,
            command = GameplayModuleCommand.StartRun,
        )
        val next = state.copy(
            revision = revision,
            activeRunId = reservation.runId,
            gameplayPhase = GameplayRunPhase.CREATED,
            pendingWorkflow = PendingWorkflow.StartingRun(
                reason = reason,
                runId = reservation.runId,
                participant = PendingParticipantCommand.Gameplay(request),
            ),
            rebirthConfirmation = RebirthConfirmation.Disarmed,
            lastFailure = null,
            nextRunId = reservation.nextRunId,
        )
        val send = AppSessionOutput.SendGameplayCommand(request)
        return accepted(
            next,
            if (reservation.ensure) {
                immutableListOf(AppSessionOutput.EnsureGameplayRun(reservation.runId), send)
            } else {
                immutableListOf(send)
            },
        )
    }

    private fun openOverlay(
        state: AppSessionState,
        destination: AppDestination,
        context: AppSessionContext,
    ): AppSessionDecision {
        if (!destination.isOverlayDestination()) {
            return rejected(SessionRejection.OverlayUnavailable(destination))
        }
        val status = state.gameplayStatusIfActive(context)
        if (state.overlay == AppDestination.Settings && destination != AppDestination.Settings) {
            return finishSettings(
                state,
                context,
                status,
                SettingsContinuation.Open(destination),
            )
        }
        if (state.base == AppDestination.Gameplay) {
            val phase = checkNotNull(status).phase
            when (phase) {
                GameplayRunPhase.CHOICE ->
                    return rejected(SessionRejection.OverlayUnavailable(destination))
                GameplayRunPhase.GAME_OVER,
                GameplayRunPhase.VICTORY,
                -> if (destination != AppDestination.Rebirth) {
                    return rejected(SessionRejection.OverlayUnavailable(destination))
                }
                GameplayRunPhase.RUNNING -> {
                    val revision = state.nextRevision()
                    val request = gameplayRequest(
                        revision,
                        sourceOrdinal = 0,
                        runId = status.instanceId.runId,
                        command = GameplayModuleCommand.PauseForOverlay,
                    )
                    return accepted(
                        state.copy(
                            revision = revision,
                            gameplayPhase = phase,
                            pendingWorkflow = PendingWorkflow.PausingForOverlay(
                                destination,
                                PendingParticipantCommand.Gameplay(request),
                            ),
                            lastFailure = null,
                        ),
                        immutableListOf(AppSessionOutput.SendGameplayCommand(request)),
                    )
                }
                GameplayRunPhase.CREATED,
                GameplayRunPhase.PAUSED,
                GameplayRunPhase.EXITED,
                -> Unit
            }
        }

        return accepted(
            state.copy(
                revision = state.nextRevision(),
                routeRevision = if (state.overlay != destination) {
                    state.nextRevision()
                } else {
                    state.routeRevision
                },
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
        val status = state.gameplayStatusIfActive(context)
        if (overlay == AppDestination.Settings) {
            return finishSettings(state, context, status, SettingsContinuation.Close)
        }
        return accepted(
            state.copy(
                revision = state.nextRevision(),
                routeRevision = state.nextRevision(),
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
        status: GameplayRunStatusProjection?,
        continuation: SettingsContinuation,
    ): AppSessionDecision {
        val preferences = checkNotNull(context.preferences) {
            "Impl must supply the validated Profile preferences read"
        }.preferences
        val revision = state.nextRevision()
        if (status != null && status.phase.acceptsPreferenceUpdate()) {
            val request = gameplayRequest(
                revision,
                sourceOrdinal = 0,
                runId = status.instanceId.runId,
                command = GameplayModuleCommand.ApplyPreferences,
            )
            return accepted(
                state.copy(
                    revision = revision,
                    gameplayPhase = status.phase,
                    pendingWorkflow = PendingWorkflow.ApplyingSettings(
                        preferences,
                        continuation,
                        PendingParticipantCommand.Gameplay(request),
                    ),
                    lastFailure = null,
                ),
                immutableListOf(AppSessionOutput.SendGameplayCommand(request)),
            )
        }
        return accepted(
            state.copy(
                revision = revision,
                routeRevision = if (state.overlay != continuation.overlayAfterCompletion) {
                    revision
                } else {
                    state.routeRevision
                },
                overlay = continuation.overlayAfterCompletion,
                gameplayPhase = status?.phase ?: state.gameplayPhase,
                rebirthConfirmation = RebirthConfirmation.Disarmed,
                lastFailure = null,
            ),
            immutableListOf(AppSessionOutput.SynchronizeAudioPreferences(preferences)),
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
        val revision = state.nextRevision()
        val request = profileRequest(revision, 0, ProfileModuleCommand.ToggleMute)
        return accepted(
            state.copy(
                revision = revision,
                pendingWorkflow = PendingWorkflow.TogglingMute(
                    PendingParticipantCommand.Profile(request),
                ),
                lastFailure = null,
            ),
            immutableListOf(AppSessionOutput.SendProfileCommand(request)),
        )
    }

    private fun selectCoreShape(
        state: AppSessionState,
        shape: kinetickk.ball.content.api.CoreShape,
    ): AppSessionDecision {
        if (state.base != AppDestination.Home || state.overlay != null) {
            return rejected(SessionRejection.CoreShapeSelectionUnavailable)
        }
        val revision = state.nextRevision()
        val request = profileRequest(
            revision,
            0,
            ProfileModuleCommand.SelectCoreShape(shape),
        )
        return accepted(
            state.copy(
                revision = revision,
                pendingWorkflow = PendingWorkflow.SelectingCoreShape(
                    shape,
                    PendingParticipantCommand.Profile(request),
                ),
                lastFailure = null,
            ),
            immutableListOf(AppSessionOutput.SendProfileCommand(request)),
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
        val projection = checkNotNull(context.rebirthProgress) {
            "Impl must supply the validated Profile rebirth read"
        }
        if (!projection.canAdvance) return rejected(SessionRejection.RebirthUnavailable)
        return when (val confirmation = state.rebirthConfirmation) {
            RebirthConfirmation.Disarmed -> accepted(
                state.copy(
                    revision = state.nextRevision(),
                    rebirthConfirmation = RebirthConfirmation.Armed(
                        projection.revision,
                        projection.snapshot.progress,
                    ),
                    lastFailure = null,
                ),
            )
            is RebirthConfirmation.Armed -> {
                if (
                    projection.revision.value < confirmation.profileRevision.value ||
                    projection.snapshot.progress != confirmation.progress
                ) {
                    return rejected(SessionRejection.RebirthUnavailable)
                }
                if (state.reusableCreatedRunId() == null && state.nextRunId == null) {
                    return rejected(SessionRejection.RunIdExhausted)
                }
                val revision = state.nextRevision()
                val request = profileRequest(revision, 0, ProfileModuleCommand.AdvanceRebirth)
                accepted(
                    state.copy(
                        revision = revision,
                        pendingWorkflow = PendingWorkflow.AdvancingRebirth(
                            PendingParticipantCommand.Profile(request),
                        ),
                        rebirthConfirmation = RebirthConfirmation.Disarmed,
                        lastFailure = null,
                    ),
                    immutableListOf(AppSessionOutput.SendProfileCommand(request)),
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
        val status = checkNotNull(state.gameplayStatusIfActive(context))
        if (!status.phase.canExit()) return rejected(SessionRejection.ExitUnavailable)
        val revision = state.nextRevision()
        val request = gameplayRequest(
            revision,
            sourceOrdinal = 0,
            runId = status.instanceId.runId,
            command = GameplayModuleCommand.ExitRun,
        )
        return accepted(
            state.copy(
                revision = revision,
                gameplayPhase = status.phase,
                pendingWorkflow = PendingWorkflow.ExitingRun(
                    PendingParticipantCommand.Gameplay(request),
                ),
                lastFailure = null,
            ),
            immutableListOf(AppSessionOutput.SendGameplayCommand(request)),
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
        val persistence = checkNotNull(context.persistenceStatus) {
            "Impl must supply the validated Profile persistence read"
        }
        if (persistence.toSessionResetLifecycle() != SessionResetLifecycle.CONFIRMATION_REQUIRED) {
            return rejected(SessionRejection.ResetActionUnavailable)
        }
        val revision = state.nextRevision()
        val request = profileRequest(revision, 0, ProfileModuleCommand.ConfirmLegacyReset)
        return accepted(
            state.copy(
                revision = revision,
                pendingWorkflow = PendingWorkflow.ConfirmingReset(
                    PendingParticipantCommand.Profile(request),
                ),
                resetLifecycle = SessionResetLifecycle.RESET_IN_PROGRESS,
                lastFailure = null,
            ),
            immutableListOf(AppSessionOutput.SendProfileCommand(request)),
        )
    }

    private fun retryReset(
        state: AppSessionState,
        context: AppSessionContext,
    ): AppSessionDecision {
        if (state.resetLifecycle != SessionResetLifecycle.PURGE_NEEDS_ATTENTION) {
            return rejected(SessionRejection.ResetActionUnavailable)
        }
        val persistence = checkNotNull(context.persistenceStatus) {
            "Impl must supply the validated Profile persistence read"
        }
        if (persistence.toSessionResetLifecycle() != SessionResetLifecycle.PURGE_NEEDS_ATTENTION) {
            return rejected(SessionRejection.ResetActionUnavailable)
        }
        val revision = state.nextRevision()
        val request = profileRequest(revision, 0, ProfileModuleCommand.RetryLegacyPurge)
        return accepted(
            state.copy(
                revision = revision,
                pendingWorkflow = PendingWorkflow.RetryingPurge(
                    PendingParticipantCommand.Profile(request),
                ),
                resetLifecycle = SessionResetLifecycle.RESET_IN_PROGRESS,
                lastFailure = null,
            ),
            immutableListOf(AppSessionOutput.SendProfileCommand(request)),
        )
    }

    private fun completeProfileCommand(
        state: AppSessionState,
        pulse: ProfileModuleResultPulse,
        context: AppSessionContext,
    ): AppSessionDecision {
        val pending = checkNotNull(state.pendingWorkflow) {
            "Trusted Profile result arrived without a pending Session command"
        }
        val participant = checkNotNull(pending.participant as? PendingParticipantCommand.Profile) {
            "Trusted Profile result contradicted the pending participant"
        }
        requireProfileCorrelation(participant.request, pulse)
        return when (pending) {
            is PendingWorkflow.SelectingCoreShape -> {
                val result = checkNotNull(pulse.result as? ProfileModuleResult.CoreShapeSelected) {
                    "Validated Profile result contradicted core-shape mapping"
                }
                check(result.shape == pending.shape) {
                    "Validated Profile core-shape result changed the requested shape"
                }
                accepted(
                    state.copy(
                        revision = state.nextRevision(),
                        pendingWorkflow = null,
                        lastFailure = null,
                    ),
                )
            }
            is PendingWorkflow.TogglingMute -> completeMuteProfileResult(state, pulse.result)
            is PendingWorkflow.AdvancingRebirth -> completeRebirthProfileResult(state, pulse.result, context)
            is PendingWorkflow.ConfirmingReset,
            is PendingWorkflow.RetryingPurge,
            -> completeResetProfileResult(state, pulse.result, context)
            else -> error("Trusted Profile result contradicted the pending workflow")
        }
    }

    private fun completeMuteProfileResult(
        state: AppSessionState,
        result: ProfileModuleResult,
    ): AppSessionDecision {
        val preferences = checkNotNull(
            (result as? ProfileModuleResult.PreferencesChanged)?.preferences,
        ) { "Validated Profile result contradicted mute mapping" }
        val revision = state.nextRevision()
        if (state.activeRunId != null && state.gameplayPhase?.acceptsPreferenceUpdate() == true) {
            val request = gameplayRequest(
                revision,
                sourceOrdinal = 0,
                runId = state.activeRunId,
                command = GameplayModuleCommand.ApplyPreferences,
            )
            return accepted(
                state.copy(
                    revision = revision,
                    pendingWorkflow = PendingWorkflow.PropagatingMute(
                        preferences,
                        PendingParticipantCommand.Gameplay(request),
                    ),
                    lastFailure = null,
                ),
                immutableListOf(
                    AppSessionOutput.SendGameplayCommand(request),
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
        result: ProfileModuleResult,
        context: AppSessionContext,
    ): AppSessionDecision {
        val advanced = checkNotNull(result as? ProfileModuleResult.RebirthAdvanced) {
            "Validated Profile result contradicted rebirth mapping"
        }
        val ready = checkNotNull(
            checkNotNull(context.runBootstrap).result as? ProfileRunBootstrapResult.Ready,
        ) { "Validated rebirth result requires a ready Profile bootstrap" }
        check(ready.snapshot.rebirthProgress == advanced.progress) {
            "Profile bootstrap contradicted the accepted rebirth result"
        }
        val reusableRunId = state.reusableCreatedRunId()
        val reservation = if (reusableRunId != null) {
            RunReservation(reusableRunId, state.nextRunId, ensure = false)
        } else {
            checkNotNull(state.reserveRun()) { "RunId capacity was not reserved before rebirth" }
        }
        val revision = state.nextRevision()
        val request = gameplayRequest(
            revision,
            sourceOrdinal = if (reservation.ensure) 1 else 0,
            runId = reservation.runId,
            command = GameplayModuleCommand.StartRun,
        )
        val next = state.copy(
            revision = revision,
            activeRunId = reservation.runId,
            gameplayPhase = GameplayRunPhase.CREATED,
            pendingWorkflow = PendingWorkflow.StartingRebirthRun(
                reservation.runId,
                PendingParticipantCommand.Gameplay(request),
            ),
            rebirthConfirmation = RebirthConfirmation.Disarmed,
            lastFailure = null,
            nextRunId = reservation.nextRunId,
        )
        val send = AppSessionOutput.SendGameplayCommand(request)
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

    private fun completeResetProfileResult(
        state: AppSessionState,
        result: ProfileModuleResult,
        context: AppSessionContext,
    ): AppSessionDecision {
        val persistence = checkNotNull(context.persistenceStatus) {
            "Impl must supply the validated Profile persistence read"
        }
        val preferences = checkNotNull(context.preferences) {
            "Impl must supply the validated Profile preferences read"
        }.preferences
        val lifecycle = persistence.toSessionResetLifecycle()
        val failure = when (result) {
            ProfileModuleResult.ResetCompleted -> {
                check(lifecycle == SessionResetLifecycle.READY)
                null
            }
            is ProfileModuleResult.ResetWriteRejected -> {
                check(lifecycle == SessionResetLifecycle.CONFIRMATION_REQUIRED)
                SessionWorkflowFailureCode.RESET_WRITE_REJECTED
            }
            is ProfileModuleResult.ResetWriteResourceFailure -> {
                check(lifecycle == SessionResetLifecycle.CONFIRMATION_REQUIRED)
                SessionWorkflowFailureCode.RESET_WRITE_RESOURCE_FAILURE
            }
            is ProfileModuleResult.ResetWriteOutcomeUnknown -> {
                check(lifecycle == SessionResetLifecycle.CONFIRMATION_REQUIRED)
                SessionWorkflowFailureCode.RESET_WRITE_OUTCOME_UNKNOWN
            }
            is ProfileModuleResult.ResetNeedsAttention -> {
                check(
                    lifecycle == SessionResetLifecycle.PURGE_NEEDS_ATTENTION &&
                        persistence.reset == result.status,
                )
                SessionWorkflowFailureCode.RESET_NEEDS_ATTENTION
            }
            else -> error("Validated Profile result contradicted reset mapping")
        }
        return accepted(
            state.copy(
                revision = state.nextRevision(),
                pendingWorkflow = null,
                resetLifecycle = lifecycle,
                lastFailure = failure,
            ),
            immutableListOf(AppSessionOutput.SynchronizeAudioPreferences(preferences)),
        )
    }

    private fun completeGameplayCommand(
        state: AppSessionState,
        pulse: GameplayModuleResultPulse,
    ): AppSessionDecision {
        val pending = checkNotNull(state.pendingWorkflow) {
            "Trusted Gameplay result arrived without a pending Session command"
        }
        val participant = checkNotNull(pending.participant as? PendingParticipantCommand.Gameplay) {
            "Trusted Gameplay result contradicted the pending participant"
        }
        requireGameplayCorrelation(participant.request, pulse)
        return when (pending) {
            is PendingWorkflow.StartingRun,
            is PendingWorkflow.StartingRebirthRun,
            -> {
                check(pulse.result == GameplayModuleResult.RunStarted) {
                    "Validated Gameplay result contradicted start mapping"
                }
                accepted(
                    state.copy(
                        revision = state.nextRevision(),
                        routeRevision = state.nextRevision(),
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
                check(pulse.result == GameplayModuleResult.OverlayPaused) {
                    "Validated Gameplay result contradicted pause mapping"
                }
                accepted(
                    state.copy(
                        revision = state.nextRevision(),
                        routeRevision = state.nextRevision(),
                        overlay = pending.destination,
                        gameplayPhase = GameplayRunPhase.PAUSED,
                        pendingWorkflow = null,
                        rebirthConfirmation = RebirthConfirmation.Disarmed,
                        lastFailure = null,
                    ),
                )
            }
            is PendingWorkflow.ApplyingSettings -> {
                check(pulse.result == GameplayModuleResult.PreferencesApplied) {
                    "Validated Gameplay result contradicted preferences mapping"
                }
                accepted(
                    state.copy(
                        revision = state.nextRevision(),
                        routeRevision = state.nextRevision(),
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
                check(pulse.result == GameplayModuleResult.PreferencesApplied) {
                    "Validated Gameplay result contradicted mute propagation mapping"
                }
                accepted(
                    state.copy(
                        revision = state.nextRevision(),
                        pendingWorkflow = null,
                        lastFailure = null,
                    ),
                )
            }
            is PendingWorkflow.ExitingRun -> completeExitResult(state, pulse.result)
            else -> error("Trusted Gameplay result contradicted the pending workflow")
        }
    }

    private fun completeExitResult(
        state: AppSessionState,
        result: GameplayModuleResult,
    ): AppSessionDecision {
        val exited = checkNotNull(result as? GameplayModuleResult.RunExited) {
            "Validated Gameplay result contradicted exit mapping"
        }
        val revision = state.nextRevision()
        return when (exited.progress) {
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

    private fun rejectProfileCommandBeforeAcceptance(
        state: AppSessionState,
        pulse: ProfileCommandRejectedBeforeAcceptance,
    ): AppSessionDecision {
        val pending = checkNotNull(state.pendingWorkflow)
        val participant = checkNotNull(pending.participant as? PendingParticipantCommand.Profile)
        requireProfileRefusalCorrelation(participant.request, pulse)
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
                lastFailure = SessionWorkflowFailureCode.PROFILE_COMMAND_REFUSED,
            ),
            outputs,
        )
    }

    private fun rejectGameplayCommandBeforeAcceptance(
        state: AppSessionState,
        pulse: GameplayCommandRejectedBeforeAcceptance,
    ): AppSessionDecision {
        val pending = checkNotNull(state.pendingWorkflow)
        val participant = checkNotNull(pending.participant as? PendingParticipantCommand.Gameplay)
        requireGameplayRefusalCorrelation(participant.request, pulse)
        val failedStart = pending is PendingWorkflow.StartingRun ||
            pending is PendingWorkflow.StartingRebirthRun
        return accepted(
            state.copy(
                revision = state.nextRevision(),
                routeRevision = if (
                    failedStart &&
                    (state.base != AppDestination.Home || state.overlay != null)
                ) {
                    state.nextRevision()
                } else {
                    state.routeRevision
                },
                base = if (failedStart) AppDestination.Home else state.base,
                overlay = if (failedStart) null else state.overlay,
                gameplayPhase = if (failedStart) GameplayRunPhase.CREATED else state.gameplayPhase,
                pendingWorkflow = null,
                lastFailure = SessionWorkflowFailureCode.GAMEPLAY_COMMAND_REFUSED,
            ),
        )
    }
}

private data class RunReservation(
    val runId: RunId,
    val nextRunId: RunId?,
    val ensure: Boolean = true,
)

private fun profileRequest(
    revision: SessionRevision,
    sourceOrdinal: Int,
    command: ProfileModuleCommand,
): ProfileModuleCommandRequest {
    val handle = ProfileSemanticHandle(
        sourceInstance = ProfileCommandSource.LocalSession,
        sourceRevision = revision.value,
        sourceOrdinal = sourceOrdinal,
    )
    return ProfileModuleCommandRequest(
        semanticHandle = handle,
        sourceOrdinal = sourceOrdinal,
        targetInstance = LOCAL_PROFILE_INSTANCE_ID,
        command = command,
    )
}

private fun gameplayRequest(
    revision: SessionRevision,
    sourceOrdinal: Int,
    runId: RunId,
    command: GameplayModuleCommand,
): GameplayModuleCommandRequest {
    val handle = GameplaySemanticHandle(
        sourceInstance = GameplayCommandSource.LocalSession,
        sourceRevision = revision.value,
        sourceOrdinal = sourceOrdinal,
    )
    return GameplayModuleCommandRequest(
        semanticHandle = handle,
        sourceOrdinal = sourceOrdinal,
        targetInstance = GameplayInstanceId(runId),
        command = command,
    )
}

private fun requireProfileCorrelation(
    request: ProfileModuleCommandRequest,
    pulse: ProfileModuleResultPulse,
) {
    // Impl has already verified the full target evidence. Nucleus retains only the semantic
    // correlation and closed mapping needed to interpret the accepted workflow result.
    check(pulse.commandSource.semanticHandle == request.semanticHandle)
    check(pulse.effectiveProtocolIdentity == request.command.effectiveIdentity)
}

private fun requireGameplayCorrelation(
    request: GameplayModuleCommandRequest,
    pulse: GameplayModuleResultPulse,
) {
    // Raw source/target/revision/ordinal/provenance/causal evidence is an Impl concern.
    check(pulse.commandSource.semanticHandle == request.semanticHandle)
    check(pulse.effectiveProtocolIdentity == request.command.effectiveIdentity)
}

private fun requireProfileRefusalCorrelation(
    request: ProfileModuleCommandRequest,
    pulse: ProfileCommandRejectedBeforeAcceptance,
) {
    check(pulse.commandSource.semanticHandle == request.semanticHandle)
    check(pulse.effectiveProtocolIdentity == request.command.effectiveIdentity)
}

private fun requireGameplayRefusalCorrelation(
    request: GameplayModuleCommandRequest,
    pulse: GameplayCommandRejectedBeforeAcceptance,
) {
    check(pulse.commandSource.semanticHandle == request.semanticHandle)
    check(pulse.effectiveProtocolIdentity == request.command.effectiveIdentity)
}

private val ProfileModuleCommand.effectiveIdentity: ProfileEffectiveProtocolIdentity
    get() = when (this) {
        is ProfileModuleCommand.SelectCoreShape -> ProfileEffectiveProtocolIdentity.SESSION_CORE_SHAPE
        ProfileModuleCommand.ToggleMute -> ProfileEffectiveProtocolIdentity.SESSION_MUTE
        ProfileModuleCommand.AdvanceRebirth -> ProfileEffectiveProtocolIdentity.SESSION_REBIRTH
        ProfileModuleCommand.ConfirmLegacyReset -> ProfileEffectiveProtocolIdentity.SESSION_RESET_CONFIRM
        ProfileModuleCommand.RetryLegacyPurge -> ProfileEffectiveProtocolIdentity.SESSION_RESET_RETRY
        is ProfileModuleCommand.ApplyGameplayProgress -> error("Gameplay progress is not a Session mapping")
    }

private val GameplayModuleCommand.effectiveIdentity: GameplayEffectiveProtocolIdentity
    get() = when (this) {
        GameplayModuleCommand.StartRun -> GameplayEffectiveProtocolIdentity.SESSION_START
        GameplayModuleCommand.PauseForOverlay -> GameplayEffectiveProtocolIdentity.SESSION_PAUSE
        GameplayModuleCommand.ApplyPreferences -> GameplayEffectiveProtocolIdentity.SESSION_PREFERENCES
        GameplayModuleCommand.ExitRun -> GameplayEffectiveProtocolIdentity.SESSION_EXIT
    }

private fun AppSessionState.nextRevision(): SessionRevision {
    check(revision.value < Long.MAX_VALUE) { "Session revision exhausted before acceptance" }
    return SessionRevision(revision.value + 1L)
}

private fun AppSessionState.reserveRun(): RunReservation? {
    val reserved = nextRunId ?: return null
    val next = if (reserved.value == Long.MAX_VALUE) null else RunId(reserved.value + 1L)
    return RunReservation(reserved, next)
}

private fun AppSessionState.reusableCreatedRunId(): RunId? =
    activeRunId?.takeIf { gameplayPhase == GameplayRunPhase.CREATED }

private fun AppSessionState.gameplayStatusIfActive(
    context: AppSessionContext,
): GameplayRunStatusProjection? = if (activeRunId == null) {
    null
} else {
    checkNotNull(context.gameplayStatus) {
        "Impl must supply the validated active Gameplay status read"
    }
}

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

private val RunStartReason.startUnavailableReason: SessionRejection
    get() = if (this == RunStartReason.RESTART) {
        SessionRejection.RestartUnavailable
    } else {
        SessionRejection.StartUnavailable
    }

private val SettingsContinuation.overlayAfterCompletion: AppDestination?
    get() = when (this) {
        SettingsContinuation.Close -> null
        is SettingsContinuation.Open -> destination
    }

private fun AppSessionState.toShellProjection(): AppShellProjection = AppShellProjection(
    instanceId = instanceId,
    revision = revision,
    routeRevision = routeRevision,
    base = base,
    overlay = overlay,
    activeRunId = activeRunId,
    rebirthEligible = base == AppDestination.Home || gameplayPhase == GameplayRunPhase.VICTORY,
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
        outputs = outputs,
    ),
)

private fun rejected(reason: SessionRejection): AppSessionDecision =
    AppSessionDecision.Rejected(reason)
