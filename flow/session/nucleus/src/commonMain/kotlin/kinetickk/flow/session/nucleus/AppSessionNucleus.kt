// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.nucleus

import kinetickk.ball.gameplay.api.GameplayRunExited
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.ProfileSettingsChanged
import kinetickk.ball.profile.api.ProfileRebirthAdvanced
import kinetickk.ball.profile.api.ProfileCoreShapeSelected
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.AppSessionQuery
import kinetickk.flow.session.api.AppShellProjection
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.api.SessionLifecycle
import kinetickk.flow.session.api.SessionRejection
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
        is ProfileRebirthAdvancedPulse -> completeRebirthProfileResult(state, pulse.result, context)
        is ProfileRebirthRefusedPulse -> rejectRebirth(state)
        is GameplayRunExitedPulse -> completeExitResult(state, pulse.result)
        is GameplayExitRefusedPulse -> rejectExit(state, pulse)
        is ProfileSettingsChangedPulse -> completeMuteProfileResult(state, pulse.result)
        is GameplaySettingsAppliedPulse -> completeGameplaySettings(state, pulse)
        is ProfileSettingsRefusedPulse -> rejectProfileSettings(state)
        is GameplaySettingsRefusedPulse -> rejectGameplaySettings(state, pulse)
        is ProfileCoreShapeSelectedPulse -> completeCoreShape(state, pulse.result)
        is ProfileCoreShapeRefusedPulse -> rejectCoreShape(state)
        is GameplayRunStartedPulse -> completeStart(state, pulse)
        is GameplayOverlayPausedPulse -> completePause(state, pulse)
        is GameplayStartRefusedPulse -> rejectStart(state, pulse)
        is GameplayPauseRefusedPulse -> rejectPause(state, pulse)
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
        if (state.lifecycle != SessionLifecycle.READY) {
            return rejected(SessionRejection.BootstrapUnavailable)
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
                if (status?.phase == GameplayRunPhase.CREATED && !status.progressPending) {
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
        val next = state.copy(
            revision = revision,
            activeRunId = reservation.runId,
            gameplayPhase = GameplayRunPhase.CREATED,
            pendingWorkflow = PendingWorkflow.StartingRun(
                reason = reason,
                runId = reservation.runId,
            ),
            rebirthConfirmation = RebirthConfirmation.Disarmed,
            lastFailure = null,
            nextRunId = reservation.nextRunId,
        )
        val send = AppSessionOutput.StartRun(reservation.runId)
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
                GameplayRunPhase.CHOICE -> if (destination != AppDestination.Codex) {
                    return rejected(SessionRejection.OverlayUnavailable(destination))
                }
                GameplayRunPhase.GAME_OVER,
                GameplayRunPhase.VICTORY,
                -> if (destination != AppDestination.Rebirth) {
                    return rejected(SessionRejection.OverlayUnavailable(destination))
                }
                GameplayRunPhase.RUNNING -> {
                    val revision = state.nextRevision()
                    return accepted(
                        state.copy(
                            revision = revision,
                            gameplayPhase = phase,
                            pendingWorkflow = PendingWorkflow.PausingForOverlay(
                                destination,
                                status.instanceId.runId,
                            ),
                            lastFailure = null,
                        ),
                        immutableListOf(AppSessionOutput.PauseForOverlay(status.instanceId.runId)),
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
            return accepted(
                state.copy(
                    revision = revision,
                    gameplayPhase = status.phase,
                    pendingWorkflow = PendingWorkflow.ApplyingSettings(
                        preferences,
                        continuation,
                        status.instanceId.runId,
                    ),
                    lastFailure = null,
                ),
                immutableListOf(AppSessionOutput.ApplyPreferences(status.instanceId.runId, preferences)),
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
        SessionShortcut.CODEX -> if (state.overlay == AppDestination.Codex) closeOverlay(state, context)
            else openOverlay(state, AppDestination.Codex, context)
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
        return accepted(
            state.copy(
                revision = revision,
                pendingWorkflow = PendingWorkflow.TogglingMute,
                lastFailure = null,
            ),
            immutableListOf(AppSessionOutput.ToggleMute),
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
        return accepted(
            state.copy(
                revision = revision,
                pendingWorkflow = PendingWorkflow.SelectingCoreShape(shape),
                lastFailure = null,
            ),
            immutableListOf(AppSessionOutput.SelectCoreShape(shape)),
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
                accepted(
                    state.copy(
                        revision = revision,
                        pendingWorkflow = PendingWorkflow.AdvancingRebirth,
                        rebirthConfirmation = RebirthConfirmation.Disarmed,
                        lastFailure = null,
                    ),
                    immutableListOf(AppSessionOutput.AdvanceRebirth),
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
        return accepted(
            state.copy(
                revision = revision,
                gameplayPhase = status.phase,
                pendingWorkflow = PendingWorkflow.ExitingRun(status.instanceId.runId),
                lastFailure = null,
            ),
            immutableListOf(AppSessionOutput.ExitRun(status.instanceId.runId)),
        )
    }

    private fun completeCoreShape(state: AppSessionState, result: ProfileCoreShapeSelected): AppSessionDecision {
        val pending = checkNotNull(state.pendingWorkflow as? PendingWorkflow.SelectingCoreShape)
        check(result.shape == pending.shape) { "Profile selected a different core shape" }
        return accepted(state.copy(revision = state.nextRevision(), pendingWorkflow = null, lastFailure = null))
    }

    private fun rejectCoreShape(state: AppSessionState): AppSessionDecision {
        check(state.pendingWorkflow is PendingWorkflow.SelectingCoreShape)
        return accepted(state.copy(
            revision = state.nextRevision(),
            pendingWorkflow = null,
            lastFailure = SessionWorkflowFailureCode.PROFILE_COMMAND_REFUSED,
        ))
    }

    private fun completeMuteProfileResult(
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

    private fun completeRebirthProfileResult(
        state: AppSessionState,
        result: ProfileRebirthAdvanced,
        context: AppSessionContext,
    ): AppSessionDecision {
        check(state.pendingWorkflow === PendingWorkflow.AdvancingRebirth)
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
        val send = AppSessionOutput.StartRun(reservation.runId)
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

    private fun completeStart(state: AppSessionState, pulse: GameplayRunStartedPulse): AppSessionDecision {
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

    private fun completePause(state: AppSessionState, pulse: GameplayOverlayPausedPulse): AppSessionDecision {
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

    private fun rejectStart(state: AppSessionState, pulse: GameplayStartRefusedPulse): AppSessionDecision {
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

    private fun rejectPause(state: AppSessionState, pulse: GameplayPauseRefusedPulse): AppSessionDecision {
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

    private fun completeGameplaySettings(
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

    private fun rejectProfileSettings(state: AppSessionState): AppSessionDecision {
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

    private fun rejectGameplaySettings(
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

    private fun completeExitResult(
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

    private fun rejectRebirth(state: AppSessionState): AppSessionDecision {
        check(state.pendingWorkflow === PendingWorkflow.AdvancingRebirth)
        return accepted(state.copy(
            revision = state.nextRevision(),
            pendingWorkflow = null,
            rebirthConfirmation = RebirthConfirmation.Disarmed,
            lastFailure = SessionWorkflowFailureCode.PROFILE_COMMAND_REFUSED,
        ))
    }

    private fun rejectExit(state: AppSessionState, pulse: GameplayExitRefusedPulse): AppSessionDecision {
        val pending = checkNotNull(state.pendingWorkflow as? PendingWorkflow.ExitingRun)
        check(pulse.runId == pending.runId && pulse.runId == state.activeRunId)
        return accepted(state.copy(
            revision = state.nextRevision(),
            pendingWorkflow = null,
            lastFailure = SessionWorkflowFailureCode.GAMEPLAY_COMMAND_REFUSED,
        ))
    }

}

private data class RunReservation(
    val runId: RunId,
    val nextRunId: RunId?,
    val ensure: Boolean = true,
)

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
    lifecycle = lifecycle,
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
