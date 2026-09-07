// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.nucleus

import kinetickk.ball.gameplay.api.GameplayRunExited

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.api.GameplayExitProgressResult
import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.api.GameplaySettingsApplied
import kinetickk.ball.gameplay.api.GameplayRefusal
import kinetickk.ball.gameplay.api.GameplayRunStarted
import kinetickk.ball.gameplay.api.GameplayOverlayPaused
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileBootstrapBlockReason
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfileRebirthAdvanced
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.RebirthProfileSnapshot
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.ball.profile.api.ProfileSettingsChanged
import kinetickk.ball.profile.api.ProfileRefusal
import kinetickk.ball.profile.api.ProfileCoreShapeSelected
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.AppSessionQuery
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.api.SessionLifecycle
import kinetickk.flow.session.api.SessionRejection
import kinetickk.flow.session.api.SessionRevision
import kinetickk.flow.session.api.SessionShortcut
import kinetickk.flow.session.api.SessionWorkflowFailureCode
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppSessionNucleusTest {
    @Test
    fun nucleusPulseInventoryIsExactlyIntentModuleResultOrControlPulse() {
        val start = startFrame()
        val startCommand = assertIs<AppSessionOutput.StartRun>(start.outputs.last())

        val intents: List<AppSessionNucleusPulse> = listOf(
            AppSessionNucleusPulse.Intent(SessionInteractionPulse.StartRunRequested),
        )
        val results: List<AppSessionNucleusPulse.Result> = listOf(
            gameplayRunStarted(GameplayRunStarted(startCommand.runId, GameplayRevision(5))),
            profileSettingsChanged(ProfileSettingsChanged(ProfileRevision(5), PlayerPreferences())),
        )
        val controls: List<AppSessionNucleusPulse.Refusal> = listOf(
            gameplayStartRefused(startCommand.runId, GameplayRefusal.Busy),
            profileSettingsRefused(ProfileRefusal.Busy),
        )

        assertEquals(1, intents.size)
        assertEquals(2, results.size)
        assertEquals(2, controls.size)
    }

    @Test
    fun acceptedFrameOutputBoundAcceptsThreeAndRejectsFour() {
        val next = initialState().copy(revision = SessionRevision(1L))
        val three = immutableListOf<AppSessionOutput>(
            AppSessionOutput.SynchronizeAudioPreferences(PlayerPreferences()),
            AppSessionOutput.PlayMuteFeedback,
            AppSessionOutput.PlayRebirthAcceptedFeedback,
        )

        AppSessionAcceptedFrame(next, three)
        assertFailsWith<IllegalArgumentException> {
            AppSessionAcceptedFrame(
                next,
                (three.asIterable() + AppSessionOutput.PlayMuteFeedback).toImmutableList(),
            )
        }
    }

    @Test
    fun initialStateOwnsOnlySessionWorkflowAndPublishesNarrowHomeShell() {
        val state = initialState()

        assertEquals(SessionRevision.ZERO, state.revision)
        assertEquals(RunId(0L), state.nextRunId)
        assertNull(state.pendingWorkflow)
        val shell = AppSessionNucleus.query(state, AppSessionQuery.GetShell)
        assertEquals(AppDestination.Home, shell.base)
        assertEquals(immutableListOf(AppDestination.Home), shell.entries)
        assertTrue(shell.rebirthEligible)
        assertNull(shell.activeRunId)
        assertNull(shell.workflowFailure)
    }

    @Test
    fun routeTokenChangesOnlyWhenBaseOrOverlayChanges() {
        val initial = initialState()
        val mute = decide(initial, SessionInteractionPulse.ToggleMuteRequested).accepted()
        assertEquals(SessionRevision(1L), mute.nextState.revision)
        assertEquals(SessionRevision.ZERO, mute.nextState.routeRevision)
        assertEquals(initial.toShell().routeToken, mute.nextState.toShell().routeToken)

        val armed = decide(
            initial.copy(overlay = AppDestination.Rebirth),
            SessionInteractionPulse.RebirthRequested,
            AppSessionContext(rebirthProgress = rebirthProjection(RebirthProgress())),
        ).accepted()
        assertEquals(SessionRevision.ZERO, armed.nextState.routeRevision)

        val opened = decide(
            initial,
            SessionInteractionPulse.OpenOverlay(AppDestination.Settings),
        ).accepted()
        assertEquals(opened.nextState.revision, opened.nextState.routeRevision)
        assertEquals(opened.nextState.revision.value, opened.nextState.toShell().routeToken.value)
    }

    @Test
    fun startRequiresReadyProfileAndCompletesNavigationOnlyAfterExactGameplayResult() {
        val state = initialState()
        assertEquals(
            SessionRejection.StartUnavailable,
            decide(
                state,
                SessionInteractionPulse.StartRunRequested,
                AppSessionContext(runBootstrap = unavailableRunBootstrap()),
            ).rejection(),
        )

        val frame = decide(
            state,
            SessionInteractionPulse.StartRunRequested,
            AppSessionContext(runBootstrap = runBootstrap()),
        ).accepted()

        assertEquals(SessionRevision(1L), frame.nextState.revision)
        assertEquals(GameplayRunPhase.CREATED, frame.nextState.gameplayPhase)
        assertEquals(AppSessionOutput.EnsureGameplayRun(RunId(0L)), frame.outputs[0])
        val send = assertIs<AppSessionOutput.StartRun>(frame.outputs[1])
        assertEquals(RunId(0L), send.runId)

        val completed = AppSessionNucleus.decide(
            frame.nextState,
            gameplayRunStarted(GameplayRunStarted(send.runId, GameplayRevision(5))),
        ).accepted()
        assertEquals(AppDestination.Gameplay, completed.nextState.base)
        assertEquals(GameplayRunPhase.RUNNING, completed.nextState.gameplayPhase)
        assertNull(completed.nextState.pendingWorkflow)
        assertEquals(SessionRevision(2L), completed.nextState.revision)
    }

    @Test
    fun rejectedStartRetainsCreatedRunForExactReuseWithoutSecondEnsure() {
        val first = startFrame()
        val send = assertIs<AppSessionOutput.StartRun>(first.outputs.last())
        val rejected = AppSessionNucleus.decide(
            first.nextState,
            gameplayStartRefused(send.runId, GameplayRefusal.Busy),
        ).accepted()

        assertEquals(AppDestination.Home, rejected.nextState.base)
        assertEquals(RunId(0L), rejected.nextState.activeRunId)
        assertEquals(GameplayRunPhase.CREATED, rejected.nextState.gameplayPhase)
        assertEquals(SessionWorkflowFailureCode.GAMEPLAY_COMMAND_REFUSED, rejected.nextState.lastFailure)

        val retry = decide(
            rejected.nextState,
            SessionInteractionPulse.StartRunRequested,
            AppSessionContext(
                runBootstrap = runBootstrap(),
                gameplayStatus = gameplayStatus(rejected.nextState, GameplayRunPhase.CREATED),
            ),
        ).accepted()
        val retrySend = assertIs<AppSessionOutput.StartRun>(retry.outputs.single())
        assertEquals(RunId(0L), retrySend.runId)
        assertEquals(RunId(1L), retry.nextState.nextRunId)
    }

    @Test
    fun restartRequiresTerminalRunAndAllocatesMonotonicRunId() {
        val running = gameplayState(GameplayRunPhase.RUNNING)
        assertEquals(
            SessionRejection.RestartUnavailable,
            decide(
                running,
                SessionInteractionPulse.RestartRunRequested,
                AppSessionContext(
                    runBootstrap = runBootstrap(),
                    gameplayStatus = gameplayStatus(running, GameplayRunPhase.RUNNING),
                ),
            ).rejection(),
        )

        val gameOver = gameplayState(GameplayRunPhase.GAME_OVER)
        val restarted = decide(
            gameOver,
            SessionInteractionPulse.RestartRunRequested,
            AppSessionContext(
                runBootstrap = runBootstrap(),
                gameplayStatus = gameplayStatus(gameOver, GameplayRunPhase.GAME_OVER),
            ),
        ).accepted()
        assertEquals(RunId(1L), restarted.nextState.activeRunId)
        assertEquals(RunId(2L), restarted.nextState.nextRunId)
        assertEquals(AppSessionOutput.EnsureGameplayRun(RunId(1L)), restarted.outputs.first())
        assertEquals(
            RunStartReason.RESTART,
            assertIs<PendingWorkflow.StartingRun>(restarted.nextState.pendingWorkflow).reason,
        )
    }

    @Test
    fun runningGameplayPausesBeforeOpeningEveryOverlayRoute() {
        listOf(
            AppDestination.Settings,
            AppDestination.Lab,
            AppDestination.Armory,
            AppDestination.Rebirth,
            AppDestination.Codex,
        ).forEach { destination ->
            val running = gameplayState(GameplayRunPhase.RUNNING)
            val pause = decide(
                running,
                SessionInteractionPulse.OpenOverlay(destination),
                AppSessionContext(gameplayStatus = gameplayStatus(running, GameplayRunPhase.RUNNING)),
            ).accepted()
            assertNull(pause.nextState.overlay)
            val send = assertIs<AppSessionOutput.PauseForOverlay>(pause.outputs.single())
            assertEquals(running.activeRunId, send.runId)

            val opened = AppSessionNucleus.decide(
                pause.nextState,
                gameplayOverlayPaused(GameplayOverlayPaused(send.runId, GameplayRevision(5))),
            ).accepted()
            assertEquals(destination, opened.nextState.overlay)
            assertEquals(GameplayRunPhase.PAUSED, opened.nextState.gameplayPhase)
        }
    }

    @Test
    fun codexPreservesPausedOrPendingRewardPhaseWhenClosed() {
        listOf(GameplayRunPhase.PAUSED, GameplayRunPhase.CHOICE).forEach { phase ->
            val state = gameplayState(phase)
            val opened = decide(state, SessionInteractionPulse.OpenOverlay(AppDestination.Codex),
                AppSessionContext(gameplayStatus = gameplayStatus(state, phase))).accepted()
            assertEquals(AppDestination.Codex, opened.nextState.overlay)
            assertEquals(phase, opened.nextState.gameplayPhase)
            assertTrue(opened.outputs.isEmpty())
            val closed = decide(opened.nextState, SessionInteractionPulse.CloseOverlay,
                AppSessionContext(gameplayStatus = gameplayStatus(state, phase))).accepted()
            assertNull(closed.nextState.overlay)
            assertEquals(phase, closed.nextState.gameplayPhase)
            assertTrue(closed.outputs.isEmpty())
        }
    }

    @Test
    fun settingsCloseRetainsReadPreferencesInTypedGameplayCommand() {
        val preferences = PlayerPreferences(masterVolume = 0.31f)
        val state = gameplayState(GameplayRunPhase.PAUSED).copy(overlay = AppDestination.Settings)
        val close = decide(
            state,
            SessionInteractionPulse.CloseOverlay,
            AppSessionContext(
                preferences = preferencesProjection(preferences),
                gameplayStatus = gameplayStatus(state, GameplayRunPhase.PAUSED),
            ),
        ).accepted()

        val send = assertIs<AppSessionOutput.ApplyPreferences>(close.outputs.single())
        assertEquals(preferences, send.preferences)
        assertEquals(state.activeRunId, send.runId)
        assertIs<PendingWorkflow.ApplyingSettings>(close.nextState.pendingWorkflow)

        val completed = AppSessionNucleus.decide(
            close.nextState,
            gameplaySettingsApplied(GameplaySettingsApplied(send.runId, GameplayRevision(5))),
        ).accepted()
        assertNull(completed.nextState.overlay)
        assertEquals(
            AppSessionOutput.SynchronizeAudioPreferences(preferences),
            completed.outputs.single(),
        )
    }

    @Test
    fun muteResultSynchronizesAudioAndPropagatesAcceptedPreferences() {
        val preferences = PlayerPreferences(soundEnabled = false, musicEnabled = false)
        val running = gameplayState(GameplayRunPhase.RUNNING)
        val requested = decide(running, SessionInteractionPulse.ToggleMuteRequested).accepted()
        assertEquals(AppSessionOutput.ToggleMute, requested.outputs.single())
        assertEquals(PendingWorkflow.TogglingMute, requested.nextState.pendingWorkflow)

        val propagated = AppSessionNucleus.decide(
            requested.nextState,
            profileSettingsChanged(ProfileSettingsChanged(ProfileRevision(5), preferences)),
        ).accepted()
        assertEquals(3, propagated.outputs.size)
        val gameplaySend = assertIs<AppSessionOutput.ApplyPreferences>(propagated.outputs[0])
        assertEquals(preferences, gameplaySend.preferences)
        assertEquals(running.activeRunId, gameplaySend.runId)
        assertEquals(AppSessionOutput.SynchronizeAudioPreferences(preferences), propagated.outputs[1])
        assertEquals(AppSessionOutput.PlayMuteFeedback, propagated.outputs[2])

        val completed = AppSessionNucleus.decide(
            propagated.nextState,
            gameplaySettingsApplied(GameplaySettingsApplied(gameplaySend.runId, GameplayRevision(5))),
        ).accepted()
        assertNull(completed.nextState.pendingWorkflow)

        val homeRequested = decide(initialState(), SessionInteractionPulse.ToggleMuteRequested).accepted()
        val homeCompleted = AppSessionNucleus.decide(
            homeRequested.nextState,
            profileSettingsChanged(ProfileSettingsChanged(ProfileRevision(5), preferences)),
        ).accepted()
        assertEquals(2, homeCompleted.outputs.size)
        assertTrue(homeCompleted.outputs.none { it is AppSessionOutput.ApplyPreferences })
    }

    @Test
    fun coreShapeMappingRetainsExactRequestedAndAcceptedShape() {
        val requested = decide(
            initialState(),
            SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM),
        ).accepted()
        val send = assertIs<AppSessionOutput.SelectCoreShape>(requested.outputs.single())
        assertEquals(CoreShape.PRISM, send.shape)

        val completed = AppSessionNucleus.decide(
            requested.nextState,
            profileCoreShapeSelected(ProfileCoreShapeSelected(ProfileRevision(5), CoreShape.PRISM)),
        ).accepted()
        assertNull(completed.nextState.pendingWorkflow)
        assertNull(completed.nextState.lastFailure)
    }

    @Test
    fun rebirthArmsThenUsesProfileResultToEnsureAndStartOneRun() {
        val progress = RebirthProgress(level = 0, highestCleared = 0)
        val advanced = RebirthProgress(level = 1, highestCleared = 0)
        val state = initialState().copy(overlay = AppDestination.Rebirth)
        val context = AppSessionContext(rebirthProgress = rebirthProjection(progress))

        val armed = decide(state, SessionInteractionPulse.RebirthRequested, context).accepted()
        assertTrue(armed.nextState.toShell().rebirthConfirmationArmed)
        assertTrue(armed.outputs.isEmpty())

        val requested = decide(
            armed.nextState,
            SessionInteractionPulse.RebirthRequested,
            context,
        ).accepted()
        assertEquals(AppSessionOutput.AdvanceRebirth, requested.outputs.single())

        val starting = AppSessionNucleus.decide(
            requested.nextState,
            profileRebirthAdvanced(ProfileRebirthAdvanced(ProfileRevision(1), advanced)),
            AppSessionContext(runBootstrap = runBootstrap(rebirthProgress = advanced)),
        ).accepted()
        assertEquals(3, starting.outputs.size)
        assertEquals(AppSessionOutput.EnsureGameplayRun(RunId(0L)), starting.outputs[0])
        val gameplaySend = assertIs<AppSessionOutput.StartRun>(starting.outputs[1])
        assertEquals(RunId(0L), gameplaySend.runId)
        assertEquals(AppSessionOutput.PlayRebirthAcceptedFeedback, starting.outputs[2])

        val completed = AppSessionNucleus.decide(
            starting.nextState,
            gameplayRunStarted(GameplayRunStarted(gameplaySend.runId, GameplayRevision(5))),
        ).accepted()
        assertEquals(AppDestination.Gameplay, completed.nextState.base)
    }

    @Test
    fun exitNavigatesHomeOnlyForNoProgressOrApplied() {
        listOf(
            GameplayExitProgressResult.NoProgress,
            GameplayExitProgressResult.Applied,
        ).forEach { progress ->
            val exit = exitFrame()
            val send = assertIs<AppSessionOutput.ExitRun>(exit.outputs.single())
            val completed = AppSessionNucleus.decide(
                exit.nextState,
                gameplayRunExited(GameplayRunExited(send.runId, GameplayRevision(5), progress)),
            ).accepted()
            assertEquals(AppDestination.Home, completed.nextState.base)
            assertNull(completed.nextState.lastFailure)
        }

        val exit = exitFrame()
        val send = assertIs<AppSessionOutput.ExitRun>(exit.outputs.single())
        val notApplied = AppSessionNucleus.decide(
            exit.nextState,
            gameplayRunExited(GameplayRunExited(send.runId, GameplayRevision(5), GameplayExitProgressResult.NotApplied)),
        ).accepted()
        assertEquals(AppDestination.Gameplay, notApplied.nextState.base)
        assertEquals(GameplayRunPhase.EXITED, notApplied.nextState.gameplayPhase)
        assertEquals(
            SessionWorkflowFailureCode.EXIT_PROGRESS_NOT_APPLIED,
            notApplied.nextState.lastFailure,
        )
    }

    @Test
    fun unavailableBootstrapBlocksEveryInteractionWithoutChangingSessionState() {
        val state = initialState(unavailablePersistence())
        val interactions = listOf(
            SessionInteractionPulse.StartRunRequested,
            SessionInteractionPulse.RestartRunRequested,
            SessionInteractionPulse.ExitRunRequested,
            SessionInteractionPulse.OpenOverlay(AppDestination.Settings),
            SessionInteractionPulse.CloseOverlay,
            SessionInteractionPulse.ShortcutObserved(SessionShortcut.MUTE),
            SessionInteractionPulse.ToggleMuteRequested,
            SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM),
            SessionInteractionPulse.RebirthRequested,
        )

        interactions.forEach { interaction ->
            assertEquals(SessionRejection.BootstrapUnavailable, decide(state, interaction).rejection())
        }
        assertEquals(SessionLifecycle.BOOTSTRAP_UNAVAILABLE, state.lifecycle)
        assertFalse(state.toShell().normalInputEnabled)
    }

    @Test
    fun participantRefusalCarriersRecoverWithoutExposingTargetPayloads() {
        val profileRequested = decide(
            initialState(),
            SessionInteractionPulse.ToggleMuteRequested,
        ).accepted()
        assertEquals(AppSessionOutput.ToggleMute, profileRequested.outputs.single())
        val profileRecovered = AppSessionNucleus.decide(
            profileRequested.nextState,
            profileSettingsRefused(ProfileRefusal.Busy),
        ).accepted()
        assertEquals(SessionWorkflowFailureCode.PROFILE_COMMAND_REFUSED, profileRecovered.nextState.lastFailure)
        assertEquals(AppSessionOutput.PlayMuteFeedback, profileRecovered.outputs.single())

        val gameplayRequested = startFrame()
        val gameplaySend = assertIs<AppSessionOutput.StartRun>(gameplayRequested.outputs.last())
        val gameplayRecovered = AppSessionNucleus.decide(
            gameplayRequested.nextState,
            gameplayStartRefused(gameplaySend.runId, GameplayRefusal.Busy),
        ).accepted()
        assertEquals(SessionWorkflowFailureCode.GAMEPLAY_COMMAND_REFUSED, gameplayRecovered.nextState.lastFailure)
        assertEquals(GameplayRunPhase.CREATED, gameplayRecovered.nextState.gameplayPhase)
    }

    @Test
    fun preacceptRecoveryMatrixCoversEveryPendingWorkflowVariant() {
        val running = gameplayState(GameplayRunPhase.RUNNING)
        val pausing = decide(
            running,
            SessionInteractionPulse.OpenOverlay(AppDestination.Lab),
            AppSessionContext(gameplayStatus = gameplayStatus(running, GameplayRunPhase.RUNNING)),
        ).accepted()
        val settings = gameplayState(GameplayRunPhase.PAUSED).copy(
            overlay = AppDestination.Settings,
        )
        val applying = decide(
            settings,
            SessionInteractionPulse.CloseOverlay,
            AppSessionContext(
                gameplayStatus = gameplayStatus(settings, GameplayRunPhase.PAUSED),
                preferences = preferencesProjection(PlayerPreferences()),
            ),
        ).accepted()
        val selecting = decide(
            initialState(),
            SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM),
        ).accepted()
        val toggling = decide(
            gameplayState(GameplayRunPhase.RUNNING),
            SessionInteractionPulse.ToggleMuteRequested,
        ).accepted()
        val propagating = AppSessionNucleus.decide(
            toggling.nextState,
            profileSettingsChanged(ProfileSettingsChanged(ProfileRevision(5), PlayerPreferences())),
        ).accepted()
        val rebirthState = initialState().copy(overlay = AppDestination.Rebirth)
        val rebirthContext = AppSessionContext(
            rebirthProgress = rebirthProjection(RebirthProgress(level = 0, highestCleared = 0)),
        )
        val armed = decide(
            rebirthState,
            SessionInteractionPulse.RebirthRequested,
            rebirthContext,
        ).accepted()
        val advancing = decide(
            armed.nextState,
            SessionInteractionPulse.RebirthRequested,
            rebirthContext,
        ).accepted()
        assertEquals(AppSessionOutput.AdvanceRebirth, advancing.outputs.single())
        val advanced = RebirthProgress(level = 1, highestCleared = 0)
        val startingRebirth = AppSessionNucleus.decide(
            advancing.nextState,
            profileRebirthAdvanced(ProfileRebirthAdvanced(ProfileRevision(1), advanced)),
            AppSessionContext(runBootstrap = runBootstrap(rebirthProgress = advanced)),
        ).accepted()
        val frames = listOf(
            startFrame(),
            pausing,
            applying,
            selecting,
            toggling,
            propagating,
            advancing,
            startingRebirth,
            exitFrame(),
        )
        assertEquals(9, frames.size)
        assertEquals(9, frames.map { it.nextState.pendingWorkflow!!::class }.toSet().size)

        frames.forEach { frame ->
            val pending = checkNotNull(frame.nextState.pendingWorkflow)
            val refusal = when (pending) {
                is PendingWorkflow.ExitingRun -> gameplayExitRefused(pending.runId, GameplayRefusal.Busy)
                PendingWorkflow.AdvancingRebirth -> profileRebirthRefused(ProfileRefusal.Busy)
                PendingWorkflow.TogglingMute -> profileSettingsRefused(ProfileRefusal.Busy)
                is PendingWorkflow.SelectingCoreShape -> profileCoreShapeRefused(ProfileRefusal.Busy)
                is PendingWorkflow.StartingRun -> gameplayStartRefused(pending.runId, GameplayRefusal.Busy)
                is PendingWorkflow.StartingRebirthRun -> gameplayStartRefused(pending.runId, GameplayRefusal.Busy)
                is PendingWorkflow.PausingForOverlay -> gameplayPauseRefused(pending.runId, GameplayRefusal.Busy)
                is PendingWorkflow.ApplyingSettings -> gameplaySettingsRefused(pending.runId, GameplayRefusal.Busy)
                is PendingWorkflow.PropagatingMute -> gameplaySettingsRefused(pending.runId, GameplayRefusal.Busy)
            }
            val recovered = AppSessionNucleus.decide(frame.nextState, refusal).accepted()
            assertNull(recovered.nextState.pendingWorkflow)
            assertEquals(
                if (pending === PendingWorkflow.TogglingMute || pending is PendingWorkflow.SelectingCoreShape ||
                    pending === PendingWorkflow.AdvancingRebirth
                ) {
                    SessionWorkflowFailureCode.PROFILE_COMMAND_REFUSED
                } else {
                    SessionWorkflowFailureCode.GAMEPLAY_COMMAND_REFUSED
                },
                recovered.nextState.lastFailure,
            )
        }
    }

    @Test
    fun forgedCorrelationProvenanceOrOutcomeFaultsInsteadOfBecomingBusinessRejection() {
        val requested = decide(
            initialState(),
            SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM),
        ).accepted()
        assertIs<AppSessionOutput.SelectCoreShape>(requested.outputs.single())

        assertFailsWith<IllegalStateException> {
            AppSessionNucleus.decide(
                requested.nextState,
                profileCoreShapeSelected(ProfileCoreShapeSelected(ProfileRevision(5), CoreShape.SHARD)),
            )
        }
        assertFailsWith<IllegalStateException> {
            AppSessionNucleus.decide(
                requested.nextState,
                profileSettingsChanged(ProfileSettingsChanged(ProfileRevision(5), PlayerPreferences())),
            )
        }
        assertFailsWith<IllegalStateException> {
            AppSessionNucleus.decide(
                requested.nextState,
                profileSettingsRefused(ProfileRefusal.Busy),
            )
        }
        assertFailsWith<IllegalStateException> {
            AppSessionNucleus.decide(
                initialState(),
                profileCoreShapeSelected(ProfileCoreShapeSelected(ProfileRevision(5), CoreShape.PRISM)),
            )
        }
    }

    @Test
    fun missingSparseReadIsAnImplContractFaultNotSessionBusinessRejection() {
        assertFailsWith<IllegalStateException> {
            decide(initialState(), SessionInteractionPulse.StartRunRequested)
        }
        val gameplay = gameplayState(GameplayRunPhase.RUNNING)
        assertFailsWith<IllegalStateException> {
            decide(
                gameplay,
                SessionInteractionPulse.OpenOverlay(AppDestination.Settings),
            )
        }
    }

    @Test
    fun pendingAndRunNamespaceGatesRejectWithoutOutputs() {
        assertEquals(
            SessionRejection.RunIdExhausted,
            decide(
                initialState().copy(nextRunId = null),
                SessionInteractionPulse.StartRunRequested,
                AppSessionContext(runBootstrap = runBootstrap()),
            ).rejection(),
        )
        val pending = startFrame().nextState
        assertEquals(
            SessionRejection.ParticipantCommandPending,
            decide(
                pending,
                SessionInteractionPulse.OpenOverlay(AppDestination.Lab),
            ).rejection(),
        )
    }

    @Test
    fun acceptedFrameEnforcesThreeOutputsAndRejectsFirstFourth() {
        val progress = RebirthProgress(level = 0, highestCleared = 0)
        val advanced = RebirthProgress(level = 1, highestCleared = 0)
        val armed = decide(
            initialState().copy(overlay = AppDestination.Rebirth),
            SessionInteractionPulse.RebirthRequested,
            AppSessionContext(rebirthProgress = rebirthProjection(progress)),
        ).accepted()
        val requested = decide(
            armed.nextState,
            SessionInteractionPulse.RebirthRequested,
            AppSessionContext(rebirthProgress = rebirthProjection(progress)),
        ).accepted()
        assertEquals(AppSessionOutput.AdvanceRebirth, requested.outputs.single())
        val exact = AppSessionNucleus.decide(
            requested.nextState,
            profileRebirthAdvanced(ProfileRebirthAdvanced(ProfileRevision(1), advanced)),
            AppSessionContext(runBootstrap = runBootstrap(rebirthProgress = advanced)),
        ).accepted()
        assertEquals(3, exact.outputs.size)

        assertFailsWith<IllegalArgumentException> {
            AppSessionAcceptedFrame(
                exact.nextState,
                (exact.outputs + AppSessionOutput.PlayMuteFeedback).toImmutableList(),
            )
        }
        val start = startFrame()
        val ensure = start.outputs[0]
        val send = start.outputs[1]
        assertFailsWith<IllegalArgumentException> {
            AppSessionAcceptedFrame(
                start.nextState,
                immutableListOf(send, ensure),
            )
        }
    }
}

private fun decide(
    state: AppSessionState,
    intent: SessionInteractionPulse,
    context: AppSessionContext = AppSessionContext.Empty,
): AppSessionDecision = AppSessionNucleus.decide(
    state,
    AppSessionNucleusPulse.Intent(intent),
    context,
)

private fun AppSessionDecision.accepted(): AppSessionAcceptedFrame =
    assertIs<AppSessionDecision.Accepted>(this).frame.also { frame ->
        assertTrue(frame.outputs.size <= MAX_SESSION_OUTPUTS_PER_DECISION)
        val shell = frame.nextState.toShell()
        assertEquals(frame.nextState.instanceId, shell.instanceId)
        assertEquals(frame.nextState.revision, shell.revision)
    }

private fun AppSessionDecision.rejection(): SessionRejection =
    assertIs<AppSessionDecision.Rejected>(this).reason

private fun AppSessionState.toShell() = AppSessionNucleus.query(this, AppSessionQuery.GetShell)

private fun startFrame(): AppSessionAcceptedFrame = decide(
    initialState(),
    SessionInteractionPulse.StartRunRequested,
    AppSessionContext(runBootstrap = runBootstrap()),
).accepted()

private fun exitFrame(): AppSessionAcceptedFrame {
    val state = gameplayState(GameplayRunPhase.RUNNING)
    return decide(
        state,
        SessionInteractionPulse.ExitRunRequested,
        AppSessionContext(gameplayStatus = gameplayStatus(state, GameplayRunPhase.RUNNING)),
    ).accepted()
}

private fun initialState(
    persistence: PersistenceStatusProjection = readyPersistence(),
): AppSessionState = AppSessionState.initial(persistence)

private fun gameplayState(phase: GameplayRunPhase): AppSessionState = initialState().copy(
    base = AppDestination.Gameplay,
    activeRunId = RunId(0L),
    gameplayPhase = phase,
    nextRunId = RunId(1L),
)

private fun gameplayStatus(
    state: AppSessionState,
    phase: GameplayRunPhase,
): GameplayRunStatusProjection = GameplayRunStatusProjection(
    instanceId = GameplayInstanceId(requireNotNull(state.activeRunId)),
    revision = GameplayRevision(4L),
    phase = phase,
    progressPending = false,
)

private fun runBootstrap(
    rebirthProgress: RebirthProgress = RebirthProgress(),
): RunBootstrapProjection = RunBootstrapProjection(
    instanceId = LOCAL_PROFILE_INSTANCE_ID,
    revision = ProfileRevision(3L),
    result = ProfileRunBootstrapResult.Ready(profileSnapshot(rebirthProgress = rebirthProgress)),
)

private fun unavailableRunBootstrap(): RunBootstrapProjection = RunBootstrapProjection(
    instanceId = LOCAL_PROFILE_INSTANCE_ID,
    revision = ProfileRevision(3L),
    result = ProfileRunBootstrapResult.Unavailable(
        ProfileBootstrapStatus.Blocked(
            ProfileBootstrapBlockReason.ResourceFailure(
                kinetickk.ball.profile.api.ProfileReadFailure.PROVIDER_READ_FAILED,
            ),
        ),
    ),
)

private fun profileSnapshot(
    preferences: PlayerPreferences = PlayerPreferences(),
    rebirthProgress: RebirthProgress = RebirthProgress(),
): GameplayProfileSnapshot = GameplayProfileSnapshot(
    preferences = preferences,
    economy = PlayerEconomy(),
    loadout = PlayerLoadout(),
    labProgress = LabProgress(),
    collection = PlayerCollection(),
    rebirthProgress = rebirthProgress,
)

private fun preferencesProjection(preferences: PlayerPreferences): PreferencesProjection =
    PreferencesProjection(
        instanceId = LOCAL_PROFILE_INSTANCE_ID,
        revision = ProfileRevision(4L),
        preferences = preferences,
    )

private fun rebirthProjection(
    progress: RebirthProgress,
    revision: Long = 2L,
): RebirthProgressProjection = RebirthProgressProjection(
    instanceId = LOCAL_PROFILE_INSTANCE_ID,
    revision = ProfileRevision(revision),
    snapshot = RebirthProfileSnapshot(progress),
    canAdvance = true,
)

private fun readyPersistence(): PersistenceStatusProjection = PersistenceStatusProjection(
    instanceId = LOCAL_PROFILE_INSTANCE_ID,
    revision = ProfileRevision(1L),
    bootstrap = ProfileBootstrapStatus.Ready,
    persistence = ProfilePersistenceStatus.Persisted(ProfileRevision(1L)),
)

private fun unavailablePersistence(): PersistenceStatusProjection = PersistenceStatusProjection(
    instanceId = LOCAL_PROFILE_INSTANCE_ID,
    revision = ProfileRevision(1L),
    bootstrap = ProfileBootstrapStatus.Blocked(
        ProfileBootstrapBlockReason.ResourceFailure(
            kinetickk.ball.profile.api.ProfileReadFailure.PROVIDER_READ_FAILED,
        ),
    ),
    persistence = ProfilePersistenceStatus.NotAttempted,
)
