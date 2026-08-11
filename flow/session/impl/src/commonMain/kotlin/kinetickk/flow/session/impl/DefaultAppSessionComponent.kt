// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.impl

import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayCommand
import kinetickk.ball.gameplay.api.GameplayCommandAdmission
import kinetickk.ball.gameplay.api.GameplayCommandResult
import kinetickk.ball.gameplay.api.GameplayCommandSource
import kinetickk.ball.gameplay.api.GameplayPort
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.interaction.GameplayFeature
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileCommand
import kinetickk.ball.profile.api.ProfileCommandAdmission
import kinetickk.ball.profile.api.ProfileCommandResult
import kinetickk.ball.profile.api.ProfileCommandSource
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.AppSessionInstanceId
import kinetickk.flow.session.api.AppSessionQuery
import kinetickk.flow.session.api.AppShellProjection
import kinetickk.flow.session.api.SessionAcceptance
import kinetickk.flow.session.api.SessionConfiguration
import kinetickk.flow.session.api.SessionControlPulse
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.api.SessionPulse
import kinetickk.flow.session.api.SessionResetLifecycle
import kinetickk.flow.session.api.SessionShortcut
import kinetickk.flow.session.api.isOverlayDestination
import kinetickk.flow.session.nucleus.AppSessionAcceptedFrame
import kinetickk.flow.session.nucleus.AppSessionContext
import kinetickk.flow.session.nucleus.AppSessionDecision
import kinetickk.flow.session.nucleus.AppSessionNucleus
import kinetickk.flow.session.nucleus.AppSessionOutput
import kinetickk.flow.session.nucleus.AppSessionState
import kinetickk.flow.session.nucleus.MAX_SESSION_OUTPUTS_PER_DECISION
import kinetickk.flow.session.nucleus.PendingParticipantCommand
import kinetickk.flow.session.nucleus.PendingWorkflow
import kinetickk.foundation.dispatch.BoundedCompletionDeque
import kinetickk.foundation.dispatch.InlineDispatchGuard

/** Sole owner, acceptor, publisher, and ordered-output dispatcher for AppSession. */
internal class DefaultAppSessionComponent private constructor(
    initialState: AppSessionState,
    private val profilePort: ProfilePort,
    private val gameplayFeature: GameplayFeature,
    private val updateAudioPreferences: (PlayerPreferences) -> Unit,
    private val playMuteFeedback: () -> Unit,
    private val playRebirthAcceptedFeedback: () -> Unit,
) : AppSessionComponent {
    private val dispatchGuard = InlineDispatchGuard()
    private val completions =
        sessionCompletionDeque<SessionWorkItem>()
    private var committedState: AppSessionState = initialState
    private var profileDispatchSourceDepth: Int? = null
    private var gameplayDispatchSourceDepth: Int? = null
    private var profileResultObservedDuringDispatch: Boolean = false
    private var gameplayResultObservedDuringDispatch: Boolean = false

    override val instanceId: AppSessionInstanceId
        get() = committedState.instanceId

    override fun accept(pulse: SessionInteractionPulse): SessionAcceptance =
        requireNotNull(dispatchRoot(pulse, reportAcceptance = true))

    override fun query(query: AppSessionQuery.GetShell): AppShellProjection =
        AppSessionNucleus.query(committedState, query)

    override fun receiveProfileCommandResult(result: ProfileCommandResult.Accepted) {
        check(dispatchGuard.isDispatching) {
            "Inline Profile completion arrived outside its Session causal scope"
        }
        val pending = committedState.pendingWorkflow?.participant as? PendingParticipantCommand.Profile
        checkNotNull(pending) { "Profile result arrived without a pending Session command" }
        check(result.commandRef == pending.command.ref) {
            "Profile result command correlation mismatch"
        }
        val sourceDepth = checkNotNull(profileDispatchSourceDepth) {
            "Profile result arrived outside Profile output dispatch"
        }
        check(!profileResultObservedDuringDispatch) {
            "Profile emitted more than one result for one Session command"
        }
        profileResultObservedDuringDispatch = true
        enqueueCompletion(
            SessionControlPulse.ProfileCommandCompleted(result),
            sourceDepth + PARTICIPANT_ACCEPTED_COMPLETION_DEPTH,
        )
    }

    override fun receiveGameplayCommandResult(result: GameplayCommandResult.Accepted) {
        check(dispatchGuard.isDispatching) {
            "Inline Gameplay completion arrived outside its Session causal scope"
        }
        val pending = committedState.pendingWorkflow?.participant as? PendingParticipantCommand.Gameplay
        checkNotNull(pending) { "Gameplay result arrived without a pending Session command" }
        check(result.commandRef == pending.command.ref) {
            "Gameplay result command correlation mismatch"
        }
        val sourceDepth = checkNotNull(gameplayDispatchSourceDepth) {
            "Gameplay result arrived outside Gameplay output dispatch"
        }
        check(!gameplayResultObservedDuringDispatch) {
            "Gameplay emitted more than one result for one Session command"
        }
        gameplayResultObservedDuringDispatch = true
        enqueueCompletion(
            SessionControlPulse.GameplayCommandCompleted(result),
            sourceDepth + PARTICIPANT_ACCEPTED_COMPLETION_DEPTH,
        )
    }

    internal fun stateSnapshot(): AppSessionState = committedState

    private fun dispatchRoot(
        pulse: SessionPulse,
        reportAcceptance: Boolean,
    ): SessionAcceptance? = dispatchGuard.dispatch {
        check(completions.isEmpty) { "Session completion deque leaked across dispatches" }
        check(completions.tryAddLast(SessionWorkItem(pulse, causalDepth = 0)))

        var rootAcceptance: SessionAcceptance? = null
        var root = true
        var deferredFault: Throwable? = null
        while (!completions.isEmpty) {
            val item = checkNotNull(completions.removeFirstOrNull())
            val before = committedState
            val context = readContext(before, item.pulse)
            when (val decision = AppSessionNucleus.decide(before, item.pulse, context)) {
                is AppSessionDecision.Rejected -> {
                    check(root && reportAcceptance) {
                        "A trusted Session completion was rejected: ${decision.reason}"
                    }
                    rootAcceptance = SessionAcceptance.Rejected(
                        instanceId = before.instanceId,
                        observedRevision = before.revision,
                        reason = decision.reason,
                    )
                }
                is AppSessionDecision.Accepted -> {
                    preflight(before, item, decision.frame)
                    committedState = decision.frame.nextState
                    if (root && reportAcceptance) {
                        rootAcceptance = SessionAcceptance.Accepted(
                            instanceId = committedState.instanceId,
                            revision = committedState.revision,
                        )
                    }
                    decision.frame.outputs.forEach { output ->
                        try {
                            execute(output, item.causalDepth)
                        } catch (failure: Throwable) {
                            if (deferredFault == null) deferredFault = failure
                        }
                    }
                }
            }
            root = false
        }

        deferredFault?.let { throw it }
        if (reportAcceptance) checkNotNull(rootAcceptance) else null
    }

    private fun preflight(
        before: AppSessionState,
        item: SessionWorkItem,
        frame: AppSessionAcceptedFrame,
    ) {
        val next = frame.nextState
        check(next.instanceId == before.instanceId) { "Session instance identity changed" }
        check(before.revision.value < Long.MAX_VALUE)
        check(next.revision.value == before.revision.value + 1L) {
            "Session revision must advance exactly once"
        }
        check(next.content === before.content) { "Captured Session content identity changed" }
        check(frame.shellProjection == AppSessionNucleus.query(next, AppSessionQuery.GetShell)) {
            "Session frame shell projection does not match the next State"
        }
        check(frame.outputs.size <= MAX_SESSION_OUTPUTS_PER_DECISION) {
            "Session output limit exceeded"
        }
        check(frame.outputs.zipWithNext().all { (left, right) ->
            left.dispatchOrder <= right.dispatchOrder
        }) { "Session outputs are not in ensure -> participant -> feedback order" }

        val participantOutputs = frame.outputs.filter { output -> output.isParticipantCommand }
        val ensureOutputs = frame.outputs.filterIsInstance<AppSessionOutput.EnsureGameplayRun>()
        requireSessionOutputFanoutBounds(participantOutputs.size, ensureOutputs.size)

        participantOutputs.singleOrNull()?.let { output ->
            requireSessionCausalDepth(item.causalDepth + PARTICIPANT_ACCEPTED_COMPLETION_DEPTH)
            requireSessionCompletionCapacity(completions.remainingCapacity, requiredCompletions = 1)
            when (output) {
                is AppSessionOutput.SendProfileCommand -> preflightProfileCommand(next, output.command)
                is AppSessionOutput.SendGameplayCommand -> preflightGameplayCommand(
                    next,
                    output.command,
                    ensureOutputs.singleOrNull(),
                )
                else -> error("Filtered Session participant output changed kind")
            }
        } ?: check(next.pendingWorkflow == null) {
            "Session retained a participant command without emitting it"
        }

        ensureOutputs.singleOrNull()?.let { ensure ->
            val gameplay = participantOutputs.singleOrNull() as? AppSessionOutput.SendGameplayCommand
            check(gameplay?.command?.ref?.targetInstance?.runId == ensure.runId) {
                "Ensured GameplayRun does not match the emitted command target"
            }
        }
    }

    private fun preflightProfileCommand(
        next: AppSessionState,
        command: ProfileCommand,
    ) {
        val pending = next.pendingWorkflow?.participant as? PendingParticipantCommand.Profile
        checkNotNull(pending) { "Session emitted Profile command without retaining it" }
        check(pending.command == command) { "Session retained a different Profile command" }
        check(command.ref.sourceInstance == ProfileCommandSource.LocalSession)
        check(command.ref.sourceRevision == next.revision.value)
        check(command.ref.targetInstance == profilePort.instanceId)
    }

    private fun preflightGameplayCommand(
        next: AppSessionState,
        command: GameplayCommand,
        ensure: AppSessionOutput.EnsureGameplayRun?,
    ) {
        val pending = next.pendingWorkflow?.participant as? PendingParticipantCommand.Gameplay
        checkNotNull(pending) { "Session emitted Gameplay command without retaining it" }
        check(pending.command == command) { "Session retained a different Gameplay command" }
        check(command.ref.sourceInstance == GameplayCommandSource.LocalSession)
        check(command.ref.sourceRevision == next.revision.value)
        check(command.ref.targetInstance.runId == next.activeRunId)
        if (ensure == null) {
            val active = checkNotNull(gameplayFeature.activeRun()) {
                "Session emitted a Gameplay command without a bound active run"
            }
            check(command.ref.targetInstance == active.instanceId) {
                "Session Gameplay command does not target the bound active run"
            }
        }
    }

    private fun execute(output: AppSessionOutput, sourceDepth: Int) {
        when (output) {
            is AppSessionOutput.EnsureGameplayRun -> ensureGameplayRun(output)
            is AppSessionOutput.SendProfileCommand -> executeProfileCommand(output, sourceDepth)
            is AppSessionOutput.SendGameplayCommand -> executeGameplayCommand(output, sourceDepth)
            is AppSessionOutput.SynchronizeAudioPreferences ->
                updateAudioPreferences(output.preferences)
            AppSessionOutput.PlayMuteFeedback -> playMuteFeedback()
            AppSessionOutput.PlayRebirthAcceptedFeedback -> playRebirthAcceptedFeedback()
        }
    }

    private fun ensureGameplayRun(output: AppSessionOutput.EnsureGameplayRun) {
        val active = gameplayFeature.activeRun()
        val run = if (active?.instanceId?.runId == output.runId) {
            active
        } else {
            gameplayFeature.createRun(output.runId, ::receiveGameplayCommandResult)
        }
        check(run.instanceId.runId == output.runId) {
            "GameplayFeature created a different RunId than Session reserved"
        }
        check(gameplayFeature.activeRun() === run) {
            "GameplayFeature did not retain the ensured GameplayRun"
        }
    }

    private fun executeProfileCommand(
        output: AppSessionOutput.SendProfileCommand,
        sourceDepth: Int,
    ) {
        check(profileDispatchSourceDepth == null)
        profileDispatchSourceDepth = sourceDepth
        profileResultObservedDuringDispatch = false
        val acceptance = try {
            profilePort.accept(
                output.command,
                ProfileCommandAdmission(output.command.ref),
            )
        } finally {
            profileDispatchSourceDepth = null
        }
        check(acceptance.instanceId == output.command.ref.targetInstance) {
            "Profile acceptance marker target identity mismatch"
        }
        when (acceptance) {
            is ProfileAcceptance.Accepted -> check(profileResultObservedDuringDispatch) {
                "Accepted inline Profile command returned without its reserved result"
            }
            is ProfileAcceptance.Rejected -> {
                check(!profileResultObservedDuringDispatch) {
                    "Profile command both completed and rejected before acceptance"
                }
                enqueueCompletion(
                    SessionControlPulse.ProfileCommandRejectedBeforeAcceptance(
                        commandRef = output.command.ref,
                        rejection = acceptance,
                    ),
                    sourceDepth + PARTICIPANT_REJECTED_COMPLETION_DEPTH,
                )
            }
        }
    }

    private fun executeGameplayCommand(
        output: AppSessionOutput.SendGameplayCommand,
        sourceDepth: Int,
    ) {
        val target = checkNotNull(gameplayFeature.activeRun()) {
            "Session cannot command Gameplay before ensuring a run"
        }
        check(target.instanceId == output.command.ref.targetInstance) {
            "Session Gameplay command target is not the bound active run"
        }
        check(gameplayDispatchSourceDepth == null)
        gameplayDispatchSourceDepth = sourceDepth
        gameplayResultObservedDuringDispatch = false
        val acceptance = try {
            target.accept(
                output.command,
                GameplayCommandAdmission(output.command.ref),
            )
        } finally {
            gameplayDispatchSourceDepth = null
        }
        check(acceptance.instanceId == output.command.ref.targetInstance) {
            "Gameplay acceptance marker target identity mismatch"
        }
        when (acceptance) {
            is GameplayAcceptance.Accepted -> check(gameplayResultObservedDuringDispatch) {
                "Accepted inline Gameplay command returned without its reserved result"
            }
            is GameplayAcceptance.Rejected -> {
                check(!gameplayResultObservedDuringDispatch) {
                    "Gameplay command both completed and rejected before acceptance"
                }
                enqueueCompletion(
                    SessionControlPulse.GameplayCommandRejectedBeforeAcceptance(
                        commandRef = output.command.ref,
                        rejection = acceptance,
                    ),
                    sourceDepth + PARTICIPANT_REJECTED_COMPLETION_DEPTH,
                )
            }
        }
    }

    private fun readContext(
        state: AppSessionState,
        pulse: SessionPulse,
    ): AppSessionContext {
        if (state.revision.value == Long.MAX_VALUE) return AppSessionContext.Empty
        if (pulse is SessionInteractionPulse) {
            if (state.pendingWorkflow != null) return AppSessionContext.Empty
            val resetAction = pulse == SessionInteractionPulse.ResetCancelled ||
                pulse == SessionInteractionPulse.ResetConfirmed ||
                pulse == SessionInteractionPulse.ResetRetryRequested
            if (state.resetLifecycle != SessionResetLifecycle.READY && !resetAction) {
                return AppSessionContext.Empty
            }
        }
        var runBootstrap = false
        var preferences = false
        var rebirthProgress = false
        var persistenceStatus = false
        var gameplayStatus = false

        fun requestOpenOverlayContext(destination: AppDestination) {
            if (!destination.isOverlayDestination()) return
            gameplayStatus = state.activeRunId != null
            preferences = state.overlay == AppDestination.Settings &&
                destination != AppDestination.Settings
        }

        fun requestCloseOverlayContext() {
            if (state.overlay == null) return
            gameplayStatus = state.activeRunId != null
            preferences = state.overlay == AppDestination.Settings
        }

        when (pulse) {
            SessionInteractionPulse.StartRunRequested -> {
                runBootstrap = true
                gameplayStatus = state.overlay == null && state.activeRunId != null
            }
            SessionInteractionPulse.RestartRunRequested -> {
                runBootstrap = true
                gameplayStatus = state.overlay == null && state.activeRunId != null
            }
            SessionInteractionPulse.ExitRunRequested ->
                gameplayStatus = state.base == AppDestination.Gameplay &&
                    state.overlay == null &&
                    state.activeRunId != null
            is SessionInteractionPulse.OpenOverlay ->
                requestOpenOverlayContext(pulse.destination)
            SessionInteractionPulse.CloseOverlay -> requestCloseOverlayContext()
            is SessionInteractionPulse.ShortcutObserved -> when (pulse.shortcut) {
                SessionShortcut.SETTINGS -> requestOpenOverlayContext(AppDestination.Settings)
                SessionShortcut.LAB -> requestOpenOverlayContext(AppDestination.Lab)
                SessionShortcut.ARMORY -> requestOpenOverlayContext(AppDestination.Armory)
                SessionShortcut.REBIRTH -> requestOpenOverlayContext(AppDestination.Rebirth)
                SessionShortcut.CODEX -> requestOpenOverlayContext(AppDestination.Codex)
                SessionShortcut.MUTE -> Unit
                SessionShortcut.BACK -> if (state.overlay != null) {
                    requestCloseOverlayContext()
                }
                SessionShortcut.ENTER -> when {
                    state.overlay != null -> requestCloseOverlayContext()
                    state.base == AppDestination.Home -> {
                        runBootstrap = true
                        gameplayStatus = state.activeRunId != null
                    }
                    else -> Unit
                }
            }
            SessionInteractionPulse.ToggleMuteRequested,
            is SessionInteractionPulse.SelectCoreShapeRequested,
            SessionInteractionPulse.ResetCancelled,
            -> Unit
            SessionInteractionPulse.RebirthRequested -> {
                rebirthProgress = state.overlay == AppDestination.Rebirth &&
                    (state.base == AppDestination.Home ||
                        state.gameplayPhase == kinetickk.ball.gameplay.api.GameplayRunPhase.VICTORY)
            }
            SessionInteractionPulse.ResetConfirmed -> {
                persistenceStatus =
                    state.resetLifecycle == SessionResetLifecycle.CONFIRMATION_REQUIRED
            }
            SessionInteractionPulse.ResetRetryRequested -> {
                persistenceStatus =
                    state.resetLifecycle == SessionResetLifecycle.PURGE_NEEDS_ATTENTION
            }
            is SessionControlPulse.ProfileCommandCompleted -> when (state.pendingWorkflow) {
                is PendingWorkflow.AdvancingRebirth -> runBootstrap = true
                is PendingWorkflow.ConfirmingReset,
                is PendingWorkflow.RetryingPurge,
                -> {
                    persistenceStatus = true
                    preferences = true
                }
                is PendingWorkflow.SelectingCoreShape,
                is PendingWorkflow.TogglingMute,
                is PendingWorkflow.StartingRun,
                is PendingWorkflow.PausingForOverlay,
                is PendingWorkflow.ApplyingSettings,
                is PendingWorkflow.PropagatingMute,
                is PendingWorkflow.StartingRebirthRun,
                is PendingWorkflow.ExitingRun,
                null,
                -> Unit
            }
            is SessionControlPulse.ProfileCommandRejectedBeforeAcceptance,
            is SessionControlPulse.GameplayCommandCompleted,
            is SessionControlPulse.GameplayCommandRejectedBeforeAcceptance,
            -> Unit
        }

        return AppSessionContext(
            runBootstrap = if (runBootstrap) profilePort.query(ProfileQuery.GetRunBootstrap) else null,
            preferences = if (preferences) profilePort.query(ProfileQuery.GetPreferences) else null,
            rebirthProgress = if (rebirthProgress) {
                profilePort.query(ProfileQuery.GetRebirthProgress)
            } else {
                null
            },
            persistenceStatus = if (persistenceStatus) {
                profilePort.query(ProfileQuery.GetPersistenceStatus)
            } else {
                null
            },
            gameplayStatus = if (gameplayStatus) {
                gameplayFeature.activeRun()?.query(GameplayQuery.GetRunStatus)
            } else {
                null
            },
        )
    }

    private fun enqueueCompletion(pulse: SessionControlPulse, causalDepth: Int) {
        requireSessionCausalDepth(causalDepth)
        check(
            completions.tryAddLast(SessionWorkItem(pulse, causalDepth)),
        ) { "Pre-reserved Session completion could not be retained" }
    }

    companion object {
        fun create(
            configuration: SessionConfiguration,
            profilePort: ProfilePort,
            gameplayFeature: GameplayFeature,
            updateAudioPreferences: (PlayerPreferences) -> Unit,
            playMuteFeedback: () -> Unit,
            playRebirthAcceptedFeedback: () -> Unit,
        ): DefaultAppSessionComponent {
            val persistence = profilePort.query(ProfileQuery.GetPersistenceStatus)
            val preferences = profilePort.query(ProfileQuery.GetPreferences)
            check(persistence.instanceId == profilePort.instanceId) {
                "Session initial persistence projection has the wrong Profile identity"
            }
            check(preferences.instanceId == profilePort.instanceId) {
                "Session initial preferences projection has the wrong Profile identity"
            }
            check(preferences.revision == persistence.revision) {
                "Session initial Profile projections do not share one revision"
            }
            return DefaultAppSessionComponent(
                initialState = AppSessionState.initial(configuration, persistence),
                profilePort = profilePort,
                gameplayFeature = gameplayFeature,
                updateAudioPreferences = updateAudioPreferences,
                playMuteFeedback = playMuteFeedback,
                playRebirthAcceptedFeedback = playRebirthAcceptedFeedback,
            ).also {
                updateAudioPreferences(preferences.preferences)
            }
        }
    }
}

internal fun <T> sessionCompletionDeque(): BoundedCompletionDeque<T> =
    BoundedCompletionDeque(SESSION_COMPLETION_CAPACITY)

internal fun requireSessionCausalDepth(causalDepth: Int) {
    check(causalDepth >= 0 && causalDepth < MAX_SESSION_CAUSAL_DEPTH) {
        "Session causal depth exhausted before participant dispatch"
    }
}

internal fun requireSessionOutputFanoutBounds(participantCount: Int, ensureCount: Int) {
    check(participantCount in 0..1) {
        "A Session decision may issue at most one participant command"
    }
    check(ensureCount in 0..1) {
        "A Session decision may ensure at most one GameplayRun"
    }
}

internal fun requireSessionCompletionCapacity(remainingCapacity: Int, requiredCompletions: Int) {
    check(requiredCompletions >= 0 && remainingCapacity >= requiredCompletions) {
        "Session completion capacity exhausted before acceptance"
    }
}

private data class SessionWorkItem(
    val pulse: SessionPulse,
    val causalDepth: Int,
)

private val AppSessionOutput.dispatchOrder: Int
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

private val AppSessionOutput.isParticipantCommand: Boolean
    get() = this is AppSessionOutput.SendProfileCommand ||
        this is AppSessionOutput.SendGameplayCommand

private const val SESSION_COMPLETION_CAPACITY: Int = 8
private const val MAX_SESSION_CAUSAL_DEPTH: Int = 8
private const val PARTICIPANT_REJECTED_COMPLETION_DEPTH: Int = 1
private const val PARTICIPANT_ACCEPTED_COMPLETION_DEPTH: Int = 2
