// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.nucleus

import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.RunMode
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.rank
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.api.SessionLifecycle
import kinetickk.flow.session.api.SessionRejection
import kinetickk.flow.session.api.SessionShortcut
import kinetickk.flow.session.api.isOverlayDestination
import kinetickk.foundation.collections.immutableListOf

internal fun decideInteraction(
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
        is SessionInteractionPulse.RebirthRequested -> rebirth(state, intent.tier, context)
        is SessionInteractionPulse.SelectRunModeRequested -> selectRunMode(state, intent.mode)
    }
}

/** Baby mode is chosen between runs, on the bare Home only; the mode then rides every start until changed. */
private fun selectRunMode(state: AppSessionState, mode: RunMode): AppSessionDecision {
    if (state.base != AppDestination.Home || state.overlay != null) {
        return rejected(SessionRejection.RunModeSelectionUnavailable)
    }
    return accepted(state.copy(revision = state.nextRevision(), runMode = mode, lastFailure = null))
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
    val send = AppSessionOutput.StartRun(reservation.runId, state.runMode)
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
    SessionShortcut.WORKSHOP -> if (state.overlay == AppDestination.Workshop) closeOverlay(state, context)
        else openOverlay(state, AppDestination.Workshop, context)
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
    // Characters change between runs: only over the Home base, bare or under the Workshop.
    if (state.base != AppDestination.Home || (state.overlay != null && state.overlay != AppDestination.Workshop)) {
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

/**
 * Selects [tier] and starts a run at it once Profile accepts. Only a tier above the highest clear
 * (the newly earned one) needs the two-press confirmation; a cleared tier is sent at once. Profile
 * stays the authority for the tier range; this only refuses tiers the read shows are not earned.
 */
private fun rebirth(
    state: AppSessionState,
    tier: Int,
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
    val progress = projection.snapshot.progress
    if (tier > progress.rank) return rejected(SessionRejection.RebirthUnavailable)
    val confirmation = state.rebirthConfirmation
    if (tier > progress.highestCleared) {
        val confirmed = confirmation is RebirthConfirmation.Armed && confirmation.tier == tier
        if (!confirmed) {
            return accepted(
                state.copy(
                    revision = state.nextRevision(),
                    rebirthConfirmation = RebirthConfirmation.Armed(projection.revision, progress, tier),
                    lastFailure = null,
                ),
            )
        }
        if (
            projection.revision.value < confirmation.profileRevision.value ||
            progress != confirmation.progress
        ) {
            return rejected(SessionRejection.RebirthUnavailable)
        }
    }
    if (state.reusableCreatedRunId() == null && state.nextRunId == null) {
        return rejected(SessionRejection.RunIdExhausted)
    }
    return accepted(
        state.copy(
            revision = state.nextRevision(),
            pendingWorkflow = PendingWorkflow.SelectingRebirthTier(tier),
            rebirthConfirmation = RebirthConfirmation.Disarmed,
            lastFailure = null,
        ),
        immutableListOf(AppSessionOutput.SelectRebirthTier(tier)),
    )
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
