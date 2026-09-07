// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.impl

import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRunPort
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.interaction.GameplayRunHost
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.ball.profile.api.ProfileReadPort
import kinetickk.flow.session.api.AppSessionPort
import kinetickk.flow.session.nucleus.gameplayRunExited
import kinetickk.flow.session.nucleus.gameplayExitRefused
import kinetickk.ball.profile.api.ProfileSettings
import kinetickk.ball.profile.api.ProfileRebirth
import kinetickk.ball.profile.api.ProfileLoadout
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.AppSessionInstanceId
import kinetickk.flow.session.api.AppSessionQuery
import kinetickk.flow.session.api.AppShellProjection
import kinetickk.flow.session.api.SessionAcceptance
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.api.SessionLifecycle
import kinetickk.flow.session.api.SessionShortcut
import kinetickk.flow.session.api.isOverlayDestination
import kinetickk.flow.session.nucleus.AppSessionAcceptedFrame
import kinetickk.flow.session.nucleus.AppSessionContext
import kinetickk.flow.session.nucleus.AppSessionDecision
import kinetickk.flow.session.nucleus.AppSessionNucleus
import kinetickk.flow.session.nucleus.AppSessionNucleusPulse
import kinetickk.flow.session.nucleus.AppSessionOutput
import kinetickk.flow.session.nucleus.AppSessionState
import kinetickk.flow.session.nucleus.MAX_SESSION_OUTPUTS_PER_DECISION
import kinetickk.flow.session.nucleus.PendingWorkflow
import kinetickk.flow.session.nucleus.profileSettingsChanged
import kinetickk.flow.session.nucleus.profileSettingsRefused
import kinetickk.flow.session.nucleus.gameplaySettingsApplied
import kinetickk.flow.session.nucleus.gameplaySettingsRefused
import kinetickk.flow.session.nucleus.profileCoreShapeSelected
import kinetickk.flow.session.nucleus.profileCoreShapeRefused
import kinetickk.flow.session.nucleus.gameplayRunStarted
import kinetickk.flow.session.nucleus.gameplayOverlayPaused
import kinetickk.flow.session.nucleus.gameplayStartRefused
import kinetickk.flow.session.nucleus.gameplayPauseRefused
import kinetickk.foundation.dispatch.BoundedCompletionDeque
import kinetickk.foundation.dispatch.InlineAcceptance
import kinetickk.foundation.dispatch.call

/** Sole owner, acceptor, publisher, and ordered-output dispatcher for AppSession. */
internal class DefaultAppSessionComponent private constructor(
    initialState: AppSessionState,
    private val profilePort: ProfileReadPort,
    private val profileSettings: ProfileSettings,
    private val profileLoadout: ProfileLoadout,
    private val profileRebirth: ProfileRebirth,
    private val gameplayRunHost: GameplayRunHost,
    private val updateAudioPreferences: (PlayerPreferences) -> Unit,
    private val playMuteFeedback: () -> Unit,
    private val playRebirthAcceptedFeedback: () -> Unit,
) : AppSessionPort {
    private val acceptance = InlineAcceptance(sessionCompletionDeque<AppSessionNucleusPulse>())
    private var committedState: AppSessionState = initialState

    override val instanceId: AppSessionInstanceId
        get() = committedState.instanceId

    override fun accept(pulse: SessionInteractionPulse): SessionAcceptance =
        dispatchLocal(pulse)

    override fun query(query: AppSessionQuery.GetShell): AppShellProjection =
        AppSessionNucleus.query(committedState, query)

    internal fun stateSnapshot(): AppSessionState = committedState

    private fun dispatchLocal(intent: SessionInteractionPulse): SessionAcceptance =
        acceptance.dispatch {
            val pulse = AppSessionNucleusPulse.Intent(intent)
            val before = committedState
            val context = readContext(before, pulse)
            when (val decision = AppSessionNucleus.decide(before, pulse, context)) {
                is AppSessionDecision.Rejected -> SessionAcceptance.Rejected(
                    instanceId = before.instanceId,
                    observedRevision = before.revision,
                    reason = decision.reason,
                )
                is AppSessionDecision.Accepted -> {
                    acceptance.acceptAndDrain(
                        rootItem = pulse,
                        rootFrame = decision.frame,
                        outputs = { it.outputs },
                        acceptFrame = { _, frame ->
                            preflight(committedState, frame)
                            committedState = frame.nextState
                        },
                        decideCompletion = { completion ->
                            val current = committedState
                            val currentContext = readContext(current, completion)
                            when (val next = AppSessionNucleus.decide(
                                current,
                                completion,
                                currentContext,
                            )) {
                                is AppSessionDecision.Accepted -> next.frame
                                is AppSessionDecision.Rejected -> error(
                                    "A trusted Session completion was rejected: ${next.reason}",
                                )
                            }
                        },
                        execute = { output, _ -> execute(output) },
                    )
                    SessionAcceptance.Accepted(
                        instanceId = decision.frame.nextState.instanceId,
                        revision = decision.frame.nextState.revision,
                    )
                }
            }
        }

    private fun preflight(
        before: AppSessionState,
        frame: AppSessionAcceptedFrame,
    ) {
        val next = frame.nextState
        check(next.instanceId == before.instanceId) { "Session instance identity changed" }
        check(before.revision.value < Long.MAX_VALUE)
        check(next.revision.value == before.revision.value + 1L) {
            "Session revision must advance exactly once"
        }
        val shellProjection = AppSessionNucleus.query(next, AppSessionQuery.GetShell)
        check(
            shellProjection.instanceId == next.instanceId &&
                shellProjection.revision == next.revision &&
                shellProjection.routeRevision == next.routeRevision,
        ) {
            "Session shell projection must derive from the accepted next State"
        }
        check(frame.outputs.size <= MAX_SESSION_OUTPUTS_PER_DECISION) {
            "Session output limit exceeded"
        }
        check(frame.outputs.zipWithNext().all { (left, right) ->
            left.dispatchOrder <= right.dispatchOrder
        }) { "Session outputs are not in ensure -> participant -> feedback order" }

        val participantOutputs = frame.outputs.filter { it.isParticipantCommand }
        val ensureOutputs = frame.outputs.filterIsInstance<AppSessionOutput.EnsureGameplayRun>()
        requireSessionOutputFanoutBounds(participantOutputs.size, ensureOutputs.size)
        participantOutputs.singleOrNull()?.let { output ->
            requireSessionCompletionCapacity(acceptance.remainingCapacity, 1)
            when (output) {
                is AppSessionOutput.ExitRun -> {
                    val pending = checkNotNull(next.pendingWorkflow as? PendingWorkflow.ExitingRun)
                    check(pending.runId == output.runId && pending.runId == next.activeRunId)
                    boundRun(output.runId)
                }
                AppSessionOutput.AdvanceRebirth -> check(next.pendingWorkflow === PendingWorkflow.AdvancingRebirth)
                AppSessionOutput.ToggleMute -> check(next.pendingWorkflow === PendingWorkflow.TogglingMute)
                is AppSessionOutput.SelectCoreShape -> {
                    val pending = checkNotNull(next.pendingWorkflow as? PendingWorkflow.SelectingCoreShape)
                    check(pending.shape == output.shape)
                }
                is AppSessionOutput.StartRun -> {
                    val runId = when (val pending = next.pendingWorkflow) {
                        is PendingWorkflow.StartingRun -> pending.runId
                        is PendingWorkflow.StartingRebirthRun -> pending.runId
                        else -> error("Session start command has no matching workflow")
                    }
                    check(runId == output.runId && runId == next.activeRunId)
                    if (ensureOutputs.isEmpty()) boundRun(runId)
                }
                is AppSessionOutput.PauseForOverlay -> {
                    val pending = checkNotNull(next.pendingWorkflow as? PendingWorkflow.PausingForOverlay)
                    check(pending.runId == output.runId && pending.runId == next.activeRunId)
                    boundRun(output.runId)
                }
                is AppSessionOutput.ApplyPreferences -> preflightGameplaySettings(next, output)
                else -> error("Filtered Session participant output changed kind")
            }
        } ?: check(next.pendingWorkflow == null) {
            "Session retained a participant command without emitting it"
        }

        ensureOutputs.singleOrNull()?.let { ensure ->
            val gameplay = participantOutputs.singleOrNull() as? AppSessionOutput.StartRun
            check(gameplay?.runId == ensure.runId) {
                "Ensured GameplayRun does not match the emitted command target"
            }
        }
    }

    private fun execute(output: AppSessionOutput) {
        when (output) {
            is AppSessionOutput.EnsureGameplayRun -> ensureGameplayRun(output)
            is AppSessionOutput.ExitRun -> executeExit(output.runId)
            AppSessionOutput.AdvanceRebirth -> acceptance.call(
                invoke = profileRebirth::advanceRebirth,
                acceptedInput = { kinetickk.flow.session.nucleus.profileRebirthAdvanced(it) },
                refusedInput = { kinetickk.flow.session.nucleus.profileRebirthRefused(it) },
            )
            AppSessionOutput.ToggleMute -> acceptance.call(
                invoke = profileSettings::toggleMute,
                acceptedInput = { profileSettingsChanged(it) },
                refusedInput = { profileSettingsRefused(it) },
            )
            is AppSessionOutput.SelectCoreShape -> acceptance.call(
                invoke = { reply -> profileLoadout.selectCoreShape(output.shape, reply) },
                acceptedInput = { result ->
                    check(result.shape == output.shape) { "Profile selected a different core shape" }
                    profileCoreShapeSelected(result)
                },
                refusedInput = { profileCoreShapeRefused(it) },
            )
            is AppSessionOutput.ApplyPreferences -> executeGameplaySettings(output)
            is AppSessionOutput.StartRun -> executeStart(output.runId)
            is AppSessionOutput.PauseForOverlay -> executePause(output.runId)
            is AppSessionOutput.SynchronizeAudioPreferences ->
                updateAudioPreferences(output.preferences)
            AppSessionOutput.PlayMuteFeedback -> playMuteFeedback()
            AppSessionOutput.PlayRebirthAcceptedFeedback -> playRebirthAcceptedFeedback()
        }
    }

    private fun preflightGameplaySettings(
        next: AppSessionState,
        output: AppSessionOutput.ApplyPreferences,
    ) {
        val pending = next.pendingWorkflow
        when (pending) {
            is PendingWorkflow.ApplyingSettings ->
                check(pending.runId == output.runId && pending.preferences == output.preferences)
            is PendingWorkflow.PropagatingMute ->
                check(pending.runId == output.runId && pending.preferences == output.preferences)
            else -> error("Session settings command has no matching workflow")
        }
        check(output.runId == next.activeRunId)
        check(gameplayRunHost.activeRun()?.instanceId?.runId == output.runId)
    }

    private fun executeGameplaySettings(output: AppSessionOutput.ApplyPreferences) {
        val target = boundRun(output.runId)
        acceptance.call(
            invoke = { reply -> target.applyPreferences(output.preferences, reply) },
            acceptedInput = { result ->
                check(result.runId == output.runId) { "Gameplay settings result came from another run" }
                gameplaySettingsApplied(result)
            },
            refusedInput = { gameplaySettingsRefused(output.runId, it) },
        )
    }

    private fun executeStart(runId: RunId) {
        val target = boundRun(runId)
        acceptance.call(
            invoke = target::startRun,
            acceptedInput = { result ->
                check(result.runId == runId) { "Gameplay start result came from another run" }
                gameplayRunStarted(result)
            },
            refusedInput = { gameplayStartRefused(runId, it) },
        )
    }

    private fun executePause(runId: RunId) {
        val target = boundRun(runId)
        acceptance.call(
            invoke = target::pauseForOverlay,
            acceptedInput = { result ->
                check(result.runId == runId) { "Gameplay pause result came from another run" }
                gameplayOverlayPaused(result)
            },
            refusedInput = { gameplayPauseRefused(runId, it) },
        )
    }

    private fun boundRun(runId: RunId): GameplayRunPort =
        checkNotNull(gameplayRunHost.activeRun()) { "Session has no bound GameplayRun" }.also {
            check(it.instanceId.runId == runId) { "Session command targets another GameplayRun" }
        }

    private fun ensureGameplayRun(output: AppSessionOutput.EnsureGameplayRun) {
        val active = gameplayRunHost.activeRun()
        val run = if (active?.instanceId?.runId == output.runId) {
            active
        } else {
            gameplayRunHost.createRun(output.runId)
        }
        check(run.instanceId.runId == output.runId) {
            "GameplayRunHost created a different RunId than Session reserved"
        }
        check(gameplayRunHost.activeRun() === run) {
            "GameplayRunHost did not retain the ensured GameplayRun"
        }
    }

    private fun executeExit(runId: RunId) {
        val run = boundRun(runId)
        acceptance.call(
            invoke = run::exitRun,
            acceptedInput = { result ->
                check(result.runId == runId) { "Gameplay exit result came from another run" }
                gameplayRunExited(result)
            },
            refusedInput = { gameplayExitRefused(runId, it) },
        )
    }

    private fun readContext(
        state: AppSessionState,
        pulse: AppSessionNucleusPulse,
    ): AppSessionContext {
        if (pulse is AppSessionNucleusPulse.Intent) {
            if (state.pendingWorkflow != null) return AppSessionContext.Empty
            if (state.lifecycle != SessionLifecycle.READY) {
                return AppSessionContext.Empty
            }
        }

        var runBootstrap = false
        var preferences = false
        var rebirthProgress = false
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
            is AppSessionNucleusPulse.Intent -> when (val intent = pulse.intent) {
                SessionInteractionPulse.StartRunRequested,
                SessionInteractionPulse.RestartRunRequested,
                -> {
                    runBootstrap = true
                    gameplayStatus = state.overlay == null && state.activeRunId != null
                }
                SessionInteractionPulse.ExitRunRequested ->
                    gameplayStatus = state.base == AppDestination.Gameplay &&
                        state.overlay == null &&
                        state.activeRunId != null
                is SessionInteractionPulse.OpenOverlay ->
                    requestOpenOverlayContext(intent.destination)
                SessionInteractionPulse.CloseOverlay -> requestCloseOverlayContext()
                is SessionInteractionPulse.ShortcutObserved -> when (intent.shortcut) {
                    SessionShortcut.SETTINGS -> requestOpenOverlayContext(AppDestination.Settings)
                    SessionShortcut.LAB -> requestOpenOverlayContext(AppDestination.Lab)
                    SessionShortcut.ARMORY -> requestOpenOverlayContext(AppDestination.Armory)
                    SessionShortcut.REBIRTH -> requestOpenOverlayContext(AppDestination.Rebirth)
                    SessionShortcut.CODEX -> requestOpenOverlayContext(AppDestination.Codex)
                    SessionShortcut.BACK -> requestCloseOverlayContext()
                    SessionShortcut.ENTER -> if (state.overlay != null) {
                        requestCloseOverlayContext()
                    } else if (state.base == AppDestination.Home) {
                        runBootstrap = true
                        gameplayStatus = state.activeRunId != null
                    }
                    SessionShortcut.MUTE -> Unit
                }
                SessionInteractionPulse.RebirthRequested -> {
                    rebirthProgress = state.overlay == AppDestination.Rebirth &&
                        (state.base == AppDestination.Home ||
                            state.gameplayPhase == kinetickk.ball.gameplay.api.GameplayRunPhase.VICTORY)
                }
                SessionInteractionPulse.ToggleMuteRequested,
                is SessionInteractionPulse.SelectCoreShapeRequested,
                -> Unit
            }
            is AppSessionNucleusPulse.Result -> when (state.pendingWorkflow) {
                is PendingWorkflow.AdvancingRebirth -> runBootstrap = true
                else -> Unit
            }
            is AppSessionNucleusPulse.Refusal -> Unit
        }

        return AppSessionContext(
            runBootstrap = if (runBootstrap) readRunBootstrap() else null,
            preferences = if (preferences) readPreferences() else null,
            rebirthProgress = if (rebirthProgress) readRebirthProgress() else null,
            gameplayStatus = if (gameplayStatus) readGameplayStatus(state) else null,
        )
    }

    private fun readRunBootstrap(): RunBootstrapProjection =
        profilePort.query(ProfileQuery.GetRunBootstrap).also(::validateProfileProjection)

    private fun readPreferences(): PreferencesProjection =
        profilePort.query(ProfileQuery.GetPreferences).also(::validateProfileProjection)

    private fun readRebirthProgress(): RebirthProgressProjection =
        profilePort.query(ProfileQuery.GetRebirthProgress).also(::validateProfileProjection)

    private fun validateProfileProjection(projection: kinetickk.ball.profile.api.ProfileProjection) {
        check(projection.instanceId == profilePort.instanceId) {
            "Profile projection came from the wrong instance"
        }
    }

    private fun readGameplayStatus(state: AppSessionState) =
        checkNotNull(gameplayRunHost.activeRun()) {
            "Session retained an active RunId without a bound GameplayRun"
        }.let { run ->
            check(run.instanceId.runId == state.activeRunId) {
                "Session active GameplayRun identity mismatch"
            }
            run.query(GameplayQuery.GetRunStatus).also { projection ->
                check(projection.instanceId == run.instanceId) {
                    "Gameplay status projection came from the wrong run"
                }
            }
        }

    companion object {
        fun create(
            profilePort: ProfileReadPort,
            profileSettings: ProfileSettings,
            profileLoadout: ProfileLoadout,
            profileRebirth: ProfileRebirth,
            gameplayRunHost: GameplayRunHost,
            updateAudioPreferences: (PlayerPreferences) -> Unit,
            playMuteFeedback: () -> Unit,
            playRebirthAcceptedFeedback: () -> Unit,
        ): DefaultAppSessionComponent {
            check(profilePort.instanceId == LOCAL_PROFILE_INSTANCE_ID) {
                "AppSession must bind the application-lifetime local Profile"
            }
            val persistence = profilePort.query(ProfileQuery.GetPersistenceStatus)
            val preferences = profilePort.query(ProfileQuery.GetPreferences)
            check(persistence.instanceId == profilePort.instanceId) {
                "Session construction bootstrap came from the wrong Profile"
            }
            check(preferences.instanceId == profilePort.instanceId) {
                "Session construction preferences came from the wrong Profile"
            }
            check(preferences.revision == persistence.revision) {
                "Session construction Profile projections do not share one revision"
            }
            return DefaultAppSessionComponent(
                initialState = AppSessionState.initial(persistence),
                profilePort = profilePort,
                profileSettings = profileSettings,
                profileLoadout = profileLoadout,
                profileRebirth = profileRebirth,
                gameplayRunHost = gameplayRunHost,
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

private val AppSessionOutput.dispatchOrder: Int
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

private val AppSessionOutput.isParticipantCommand: Boolean
    get() = this is AppSessionOutput.ExitRun ||
        this === AppSessionOutput.AdvanceRebirth || this === AppSessionOutput.ToggleMute || this is AppSessionOutput.ApplyPreferences ||
        this is AppSessionOutput.SelectCoreShape || this is AppSessionOutput.StartRun ||
        this is AppSessionOutput.PauseForOverlay

private const val SESSION_COMPLETION_CAPACITY: Int = 8
