// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.nucleus

import kinetickk.ball.content.api.ContentVersion
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.RebirthDirective
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayCommandOutcome
import kinetickk.ball.gameplay.api.GameplayCommandRef
import kinetickk.ball.gameplay.api.GameplayCommandResult
import kinetickk.ball.gameplay.api.GameplayCommandSource
import kinetickk.ball.gameplay.api.GameplayExitProfileOutcome
import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.GameplaySessionPulse
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileBootstrapBlockReason
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileCommandOutcome
import kinetickk.ball.profile.api.ProfileCommandRef
import kinetickk.ball.profile.api.ProfileCommandResult
import kinetickk.ball.profile.api.ProfileCommandSource
import kinetickk.ball.profile.api.ProfileLegacyKeys
import kinetickk.ball.profile.api.ProfileLegacyPurgeResult
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileResourceFailure
import kinetickk.ball.profile.api.ProfileResetStatus
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.ProfileV4Rejection
import kinetickk.ball.profile.api.RebirthProfileSnapshot
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.AppSessionQuery
import kinetickk.flow.session.api.SessionConfiguration
import kinetickk.flow.session.api.SessionContextField
import kinetickk.flow.session.api.SessionContextReason
import kinetickk.flow.session.api.SessionControlPulse
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.api.SessionParticipantResultRejection
import kinetickk.flow.session.api.SessionRejection
import kinetickk.flow.session.api.SessionResetLifecycle
import kinetickk.flow.session.api.SessionRevision
import kinetickk.flow.session.api.SessionShortcut
import kinetickk.flow.session.api.SessionWorkflowFailure
import kinetickk.foundation.collections.immutableListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppSessionNucleusTest {
    @Test
    fun initialStateCapturesContentIdentityAndPublishesImmutableHomeShell() {
        val content = minimalGameplayContent()
        val state = initialState(content = content)

        assertSame(content, state.content)
        assertEquals(SessionRevision.ZERO, state.revision)
        assertEquals(RunId(0L), state.nextRunId)
        assertEquals(0, state.nextProfileCommandOrdinal)
        assertEquals(0, state.nextGameplayCommandOrdinal)

        val shell = AppSessionNucleus.query(state, AppSessionQuery.GetShell)
        assertEquals(AppDestination.Home, shell.base)
        assertEquals(immutableListOf(AppDestination.Home), shell.entries)
        assertTrue(shell.rebirthEligible)
        assertNull(shell.gameplayPhase)
    }

    @Test
    fun startCapturesBootstrapAndCompletesNavigationOnlyAfterExactGameplayResult() {
        val state = initialState()
        val frame = AppSessionNucleus.decide(
            state,
            SessionInteractionPulse.StartRunRequested,
            AppSessionContext(runBootstrap = runBootstrap()),
        ).accepted()

        assertEquals(SessionRevision(1L), frame.nextState.revision)
        assertEquals(RunId(0L), frame.nextState.activeRunId)
        assertEquals(GameplayRunPhase.CREATED, frame.nextState.gameplayPhase)
        assertIs<PendingWorkflow.StartingRun>(frame.nextState.pendingWorkflow)
        assertEquals(2, frame.outputs.size)
        assertEquals(AppSessionOutput.EnsureGameplayRun(RunId(0L)), frame.outputs[0])
        val send = assertIs<AppSessionOutput.SendGameplayCommand>(frame.outputs[1])
        assertEquals(GameplayCommandSource.LocalSession, send.command.ref.sourceInstance)
        assertEquals(SessionRevision(1L).value, send.command.ref.sourceRevision)
        assertEquals(0, send.command.ref.ordinal)
        val start = assertIs<GameplaySessionPulse.StartRun>(send.command.pulse)
        assertSame(state.content, start.configuration.content)
        assertEquals(profileSnapshot(), start.configuration.profile)

        val completed = AppSessionNucleus.decide(
            frame.nextState,
            SessionControlPulse.GameplayCommandCompleted(
                gameplayResult(send.command.ref, GameplayCommandOutcome.RunStarted),
            ),
        ).accepted()
        assertEquals(AppDestination.Gameplay, completed.nextState.base)
        assertEquals(GameplayRunPhase.RUNNING, completed.nextState.gameplayPhase)
        assertNull(completed.nextState.pendingWorkflow)
        assertEquals(SessionRevision(2L), completed.nextState.revision)
    }

    @Test
    fun rejectedStartRetainsCreatedRunForExactRetryWithoutEnsuringAgain() {
        val first = startFrame()
        val send = assertIs<AppSessionOutput.SendGameplayCommand>(first.outputs.last())
        val rejected = AppSessionNucleus.decide(
            first.nextState,
            SessionControlPulse.GameplayCommandRejectedBeforeAcceptance(
                send.command.ref,
                GameplayAcceptance.Rejected(
                    send.command.ref.targetInstance,
                    GameplayRevision.ZERO,
                    GameplayRejection.AlreadyStarted,
                ),
            ),
        ).accepted()

        assertNull(rejected.nextState.pendingWorkflow)
        assertEquals(AppDestination.Home, rejected.nextState.base)
        assertNull(rejected.nextState.overlay)
        assertEquals(RunId(0L), rejected.nextState.activeRunId)
        assertEquals(GameplayRunPhase.CREATED, rejected.nextState.gameplayPhase)
        assertIs<SessionWorkflowFailure.GameplayCommandRejected>(rejected.nextState.lastFailure)

        val retry = AppSessionNucleus.decide(
            rejected.nextState,
            SessionInteractionPulse.StartRunRequested,
            AppSessionContext(
                runBootstrap = runBootstrap(),
                gameplayStatus = gameplayStatus(rejected.nextState, GameplayRunPhase.CREATED),
            ),
        ).accepted()
        assertEquals(1, retry.outputs.size)
        val retrySend = assertIs<AppSessionOutput.SendGameplayCommand>(retry.outputs.single())
        assertEquals(RunId(0L), retrySend.command.ref.targetInstance.runId)
        assertEquals(1, retrySend.command.ref.ordinal)
        assertEquals(RunId(1L), retry.nextState.nextRunId)

        val completed = AppSessionNucleus.decide(
            retry.nextState,
            SessionControlPulse.GameplayCommandCompleted(
                gameplayResult(retrySend.command.ref, GameplayCommandOutcome.RunStarted),
            ),
        ).accepted()
        assertEquals(AppDestination.Gameplay, completed.nextState.base)
        assertEquals(GameplayRunPhase.RUNNING, completed.nextState.gameplayPhase)
    }

    @Test
    fun restartRequiresTerminalRunAndAllocatesMonotonicRunId() {
        val running = gameplayState(GameplayRunPhase.RUNNING)
        assertEquals(
            SessionRejection.RestartUnavailable,
            AppSessionNucleus.decide(
                running,
                SessionInteractionPulse.RestartRunRequested,
                AppSessionContext(
                    runBootstrap = runBootstrap(),
                    gameplayStatus = gameplayStatus(running, GameplayRunPhase.RUNNING),
                ),
            ).rejection(),
        )

        val gameOver = gameplayState(GameplayRunPhase.GAME_OVER)
        val restarted = AppSessionNucleus.decide(
            gameOver,
            SessionInteractionPulse.RestartRunRequested,
            AppSessionContext(
                runBootstrap = runBootstrap(),
                gameplayStatus = gameplayStatus(gameOver, GameplayRunPhase.GAME_OVER),
            ),
        ).accepted()
        assertEquals(RunId(1L), restarted.nextState.activeRunId)
        assertEquals(RunId(2L), restarted.nextState.nextRunId)
        assertEquals(
            AppSessionOutput.EnsureGameplayRun(RunId(1L)),
            restarted.outputs.first(),
        )
        assertEquals(RunStartReason.RESTART, assertIs<PendingWorkflow.StartingRun>(restarted.nextState.pendingWorkflow).reason)
    }

    @Test
    fun rejectedRestartReturnsHomeAndHomeStartRetriesTheSameCreatedRun() {
        val gameOver = gameplayState(GameplayRunPhase.GAME_OVER)
        val restarted = AppSessionNucleus.decide(
            gameOver,
            SessionInteractionPulse.RestartRunRequested,
            AppSessionContext(
                runBootstrap = runBootstrap(),
                gameplayStatus = gameplayStatus(gameOver, GameplayRunPhase.GAME_OVER),
            ),
        ).accepted()
        val firstSend = assertIs<AppSessionOutput.SendGameplayCommand>(restarted.outputs.last())

        val rejected = AppSessionNucleus.decide(
            restarted.nextState,
            SessionControlPulse.GameplayCommandRejectedBeforeAcceptance(
                firstSend.command.ref,
                GameplayAcceptance.Rejected(
                    firstSend.command.ref.targetInstance,
                    GameplayRevision.ZERO,
                    GameplayRejection.AlreadyStarted,
                ),
            ),
        ).accepted()
        assertEquals(AppDestination.Home, rejected.nextState.base)
        assertNull(rejected.nextState.overlay)
        assertEquals(RunId(1L), rejected.nextState.activeRunId)
        assertEquals(GameplayRunPhase.CREATED, rejected.nextState.gameplayPhase)

        val retry = AppSessionNucleus.decide(
            rejected.nextState,
            SessionInteractionPulse.StartRunRequested,
            AppSessionContext(
                runBootstrap = runBootstrap(),
                gameplayStatus = gameplayStatus(rejected.nextState, GameplayRunPhase.CREATED),
            ),
        ).accepted()
        val retrySend = assertIs<AppSessionOutput.SendGameplayCommand>(retry.outputs.single())
        assertEquals(firstSend.command.ref.targetInstance, retrySend.command.ref.targetInstance)
        assertEquals(RunId(2L), retry.nextState.nextRunId)

        val completed = AppSessionNucleus.decide(
            retry.nextState,
            SessionControlPulse.GameplayCommandCompleted(
                gameplayResult(retrySend.command.ref, GameplayCommandOutcome.RunStarted),
            ),
        ).accepted()
        assertEquals(AppDestination.Gameplay, completed.nextState.base)
        assertEquals(GameplayRunPhase.RUNNING, completed.nextState.gameplayPhase)
    }

    @Test
    fun overlayMatrixPreservesEveryGameplayPhaseAndWaitsForPauseAcceptance() {
        val destinations = listOf(
            AppDestination.Settings,
            AppDestination.Lab,
            AppDestination.Armory,
            AppDestination.Rebirth,
            AppDestination.Codex,
        )
        GameplayRunPhase.entries.forEach { phase ->
            destinations.forEach { destination ->
                val state = gameplayState(phase)
                val decision = AppSessionNucleus.decide(
                    state,
                    SessionInteractionPulse.OpenOverlay(destination),
                    AppSessionContext(gameplayStatus = gameplayStatus(state, phase)),
                )
                val expectedOpen = when (phase) {
                    GameplayRunPhase.CHOICE -> false
                    GameplayRunPhase.GAME_OVER,
                    GameplayRunPhase.VICTORY,
                    -> destination == AppDestination.Rebirth
                    GameplayRunPhase.CREATED,
                    GameplayRunPhase.RUNNING,
                    GameplayRunPhase.PAUSED,
                    GameplayRunPhase.EXITED,
                    -> true
                }
                if (!expectedOpen) {
                    assertEquals(
                        SessionRejection.OverlayUnavailable(destination),
                        decision.rejection(),
                    )
                } else if (phase == GameplayRunPhase.RUNNING) {
                    val frame = decision.accepted()
                    assertNull(frame.nextState.overlay)
                    val pending = assertIs<PendingWorkflow.PausingForOverlay>(frame.nextState.pendingWorkflow)
                    assertEquals(destination, pending.destination)
                    val result = AppSessionNucleus.decide(
                        frame.nextState,
                        SessionControlPulse.GameplayCommandCompleted(
                            gameplayResult(
                                pending.participant.command.ref,
                                GameplayCommandOutcome.OverlayPaused,
                            ),
                        ),
                    ).accepted()
                    assertEquals(destination, result.nextState.overlay)
                    assertEquals(GameplayRunPhase.PAUSED, result.nextState.gameplayPhase)
                } else {
                    assertEquals(destination, decision.accepted().nextState.overlay)
                }
            }
        }
    }

    @Test
    fun settingsCloseQueriesPreferencesThenWaitsForExactGameplayPropagation() {
        val preferences = PlayerPreferences(textScale = 1.5f, simulationSpeed = 1.75f)
        val state = gameplayState(GameplayRunPhase.PAUSED).copy(overlay = AppDestination.Settings)
        val applying = AppSessionNucleus.decide(
            state,
            SessionInteractionPulse.CloseOverlay,
            AppSessionContext(
                preferences = preferencesProjection(preferences),
                gameplayStatus = gameplayStatus(state, GameplayRunPhase.PAUSED),
            ),
        ).accepted()
        assertEquals(AppDestination.Settings, applying.nextState.overlay)
        val pending = assertIs<PendingWorkflow.ApplyingSettings>(applying.nextState.pendingWorkflow)
        assertEquals(preferences, pending.preferences)
        assertEquals(
            GameplaySessionPulse.ApplyPreferences(preferences),
            pending.participant.command.pulse,
        )

        val wrong = AppSessionNucleus.decide(
            applying.nextState,
            SessionControlPulse.GameplayCommandCompleted(
                gameplayResult(
                    pending.participant.command.ref,
                    GameplayCommandOutcome.PreferencesApplied(PlayerPreferences()),
                ),
            ),
        )
        assertEquals(
            SessionRejection.UnexpectedParticipantResult(
                SessionParticipantResultRejection.OUTCOME_MISMATCH,
            ),
            wrong.rejection(),
        )

        val completed = AppSessionNucleus.decide(
            applying.nextState,
            SessionControlPulse.GameplayCommandCompleted(
                gameplayResult(
                    pending.participant.command.ref,
                    GameplayCommandOutcome.PreferencesApplied(preferences),
                ),
            ),
        ).accepted()
        assertNull(completed.nextState.overlay)
        assertEquals(
            immutableListOf<AppSessionOutput>(
                AppSessionOutput.SynchronizeAudioPreferences(preferences),
            ),
            completed.outputs,
        )
    }

    @Test
    fun muteChainsProfileToGameplayAndKeepsArmedRebirthConfirmation() {
        val progress = RebirthProgress(level = 0, highestCleared = 0)
        val rebirthRoute = AppSessionNucleus.decide(
            initialState(),
            SessionInteractionPulse.OpenOverlay(AppDestination.Rebirth),
        ).accepted().nextState
        val armed = AppSessionNucleus.decide(
            rebirthRoute,
            SessionInteractionPulse.RebirthRequested,
            AppSessionContext(rebirthProgress = rebirthProjection(progress, revision = 2L)),
        ).accepted().nextState
        assertIs<RebirthConfirmation.Armed>(armed.rebirthConfirmation)

        val mute = AppSessionNucleus.decide(
            armed,
            SessionInteractionPulse.ShortcutObserved(SessionShortcut.MUTE),
        ).accepted()
        val mutePending = assertIs<PendingWorkflow.TogglingMute>(mute.nextState.pendingWorkflow)
        val mutedPreferences = PlayerPreferences(soundEnabled = false, musicEnabled = false)
        val muted = AppSessionNucleus.decide(
            mute.nextState,
            SessionControlPulse.ProfileCommandCompleted(
                profileResult(
                    mutePending.participant.command.ref,
                    ProfileCommandOutcome.PreferencesChanged(mutedPreferences),
                ),
            ),
        ).accepted()
        assertIs<RebirthConfirmation.Armed>(muted.nextState.rebirthConfirmation)
        assertEquals(
            listOf(
                AppSessionOutput.SynchronizeAudioPreferences(mutedPreferences),
                AppSessionOutput.PlayMuteFeedback,
            ),
            muted.outputs,
        )

        val confirm = AppSessionNucleus.decide(
            muted.nextState,
            SessionInteractionPulse.RebirthRequested,
            AppSessionContext(rebirthProgress = rebirthProjection(progress, revision = 3L)),
        ).accepted()
        assertIs<PendingWorkflow.AdvancingRebirth>(confirm.nextState.pendingWorkflow)
    }

    @Test
    fun rebirthConfirmationRejectsStaleProjectionButAcceptsNewerSameProgress() {
        val progress = RebirthProgress(level = 0, highestCleared = 0)
        val route = AppSessionNucleus.decide(
            initialState(),
            SessionInteractionPulse.OpenOverlay(AppDestination.Rebirth),
        ).accepted().nextState
        val armed = AppSessionNucleus.decide(
            route,
            SessionInteractionPulse.RebirthRequested,
            AppSessionContext(rebirthProgress = rebirthProjection(progress, revision = 5L)),
        ).accepted().nextState

        assertEquals(
            SessionRejection.RebirthUnavailable,
            AppSessionNucleus.decide(
                armed,
                SessionInteractionPulse.RebirthRequested,
                AppSessionContext(rebirthProgress = rebirthProjection(progress, revision = 4L)),
            ).rejection(),
        )

        val accepted = AppSessionNucleus.decide(
            armed,
            SessionInteractionPulse.RebirthRequested,
            AppSessionContext(rebirthProgress = rebirthProjection(progress, revision = 6L)),
        ).accepted()
        assertIs<PendingWorkflow.AdvancingRebirth>(accepted.nextState.pendingWorkflow)
    }

    @Test
    fun activeMuteUsesOneParticipantAtATimeAndExactlyThreeOrderedOutputs() {
        val state = gameplayState(GameplayRunPhase.PAUSED)
        val mute = AppSessionNucleus.decide(
            state,
            SessionInteractionPulse.ToggleMuteRequested,
        ).accepted()
        assertEquals(1, mute.outputs.size)
        val profilePending = assertIs<PendingWorkflow.TogglingMute>(mute.nextState.pendingWorkflow)
        val preferences = PlayerPreferences(soundEnabled = false, musicEnabled = false)

        val profileCompleted = AppSessionNucleus.decide(
            mute.nextState,
            SessionControlPulse.ProfileCommandCompleted(
                profileResult(
                    profilePending.participant.command.ref,
                    ProfileCommandOutcome.PreferencesChanged(preferences),
                ),
            ),
        ).accepted()
        assertEquals(MAX_SESSION_OUTPUTS_PER_DECISION, profileCompleted.outputs.size)
        assertIs<AppSessionOutput.SendGameplayCommand>(profileCompleted.outputs[0])
        assertEquals(
            AppSessionOutput.SynchronizeAudioPreferences(preferences),
            profileCompleted.outputs[1],
        )
        assertEquals(AppSessionOutput.PlayMuteFeedback, profileCompleted.outputs[2])
        assertIs<PendingWorkflow.PropagatingMute>(profileCompleted.nextState.pendingWorkflow)
    }

    @Test
    fun coreShapeSelectionRequiresExactAcceptedOutcomeAndPreacceptRejectionIsFailure() {
        val issued = AppSessionNucleus.decide(
            initialState(),
            SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM),
        ).accepted()
        val pending = assertIs<PendingWorkflow.SelectingCoreShape>(issued.nextState.pendingWorkflow)

        assertEquals(
            SessionRejection.UnexpectedParticipantResult(
                SessionParticipantResultRejection.OUTCOME_MISMATCH,
            ),
            AppSessionNucleus.decide(
                issued.nextState,
                SessionControlPulse.ProfileCommandCompleted(
                    profileResult(
                        pending.participant.command.ref,
                        ProfileCommandOutcome.CoreShapeSelected(CoreShape.SHARD),
                    ),
                ),
            ).rejection(),
        )

        val rejected = AppSessionNucleus.decide(
            issued.nextState,
            SessionControlPulse.ProfileCommandRejectedBeforeAcceptance(
                pending.participant.command.ref,
                ProfileAcceptance.Rejected(
                    LOCAL_PROFILE_INSTANCE_ID,
                    ProfileRevision(4L),
                    ProfileRejection.CoreShapeLocked,
                ),
            ),
        ).accepted()
        assertNull(rejected.nextState.pendingWorkflow)
        assertIs<SessionWorkflowFailure.ProfileCommandRejected>(rejected.nextState.lastFailure)
    }

    @Test
    fun rebirthRunsProfileThenEmitsExactThreeOutputGameplayContinuation() {
        val progress = RebirthProgress(level = 0, highestCleared = 0)
        val advanced = RebirthProgress(level = 1, highestCleared = 0)
        val route = AppSessionNucleus.decide(
            initialState(),
            SessionInteractionPulse.OpenOverlay(AppDestination.Rebirth),
        ).accepted().nextState
        val armed = AppSessionNucleus.decide(
            route,
            SessionInteractionPulse.RebirthRequested,
            AppSessionContext(rebirthProgress = rebirthProjection(progress)),
        ).accepted().nextState
        val requested = AppSessionNucleus.decide(
            armed,
            SessionInteractionPulse.RebirthRequested,
            AppSessionContext(rebirthProgress = rebirthProjection(progress)),
        ).accepted()
        val pending = assertIs<PendingWorkflow.AdvancingRebirth>(requested.nextState.pendingWorkflow)

        val continued = AppSessionNucleus.decide(
            requested.nextState,
            SessionControlPulse.ProfileCommandCompleted(
                profileResult(
                    pending.participant.command.ref,
                    ProfileCommandOutcome.RebirthAdvanced(advanced),
                ),
            ),
            AppSessionContext(
                runBootstrap = runBootstrap(profileSnapshot(rebirthProgress = advanced)),
            ),
        ).accepted()
        val runId = continued.nextState.activeRunId!!
        assertEquals(
            listOf(
                AppSessionOutput.EnsureGameplayRun(runId),
                assertIs<AppSessionOutput.SendGameplayCommand>(continued.outputs[1]),
                AppSessionOutput.PlayRebirthAcceptedFeedback,
            ),
            continued.outputs,
        )
        assertEquals(MAX_SESSION_OUTPUTS_PER_DECISION, continued.outputs.size)
        assertIs<PendingWorkflow.StartingRebirthRun>(continued.nextState.pendingWorkflow)
    }

    @Test
    fun rejectedRebirthStartReturnsHomeAndHomeStartRetriesTheSameCreatedRun() {
        val progress = RebirthProgress(level = 0, highestCleared = 0)
        val advanced = RebirthProgress(level = 1, highestCleared = 0)
        val route = AppSessionNucleus.decide(
            initialState(),
            SessionInteractionPulse.OpenOverlay(AppDestination.Rebirth),
        ).accepted().nextState
        val armed = AppSessionNucleus.decide(
            route,
            SessionInteractionPulse.RebirthRequested,
            AppSessionContext(rebirthProgress = rebirthProjection(progress)),
        ).accepted().nextState
        val requested = AppSessionNucleus.decide(
            armed,
            SessionInteractionPulse.RebirthRequested,
            AppSessionContext(rebirthProgress = rebirthProjection(progress)),
        ).accepted()
        val profilePending = assertIs<PendingWorkflow.AdvancingRebirth>(requested.nextState.pendingWorkflow)
        val starting = AppSessionNucleus.decide(
            requested.nextState,
            SessionControlPulse.ProfileCommandCompleted(
                profileResult(
                    profilePending.participant.command.ref,
                    ProfileCommandOutcome.RebirthAdvanced(advanced),
                ),
            ),
            AppSessionContext(
                runBootstrap = runBootstrap(profileSnapshot(rebirthProgress = advanced)),
            ),
        ).accepted()
        val firstSend = assertIs<AppSessionOutput.SendGameplayCommand>(starting.outputs[1])

        val rejected = AppSessionNucleus.decide(
            starting.nextState,
            SessionControlPulse.GameplayCommandRejectedBeforeAcceptance(
                firstSend.command.ref,
                GameplayAcceptance.Rejected(
                    firstSend.command.ref.targetInstance,
                    GameplayRevision.ZERO,
                    GameplayRejection.AlreadyStarted,
                ),
            ),
        ).accepted()
        assertEquals(AppDestination.Home, rejected.nextState.base)
        assertNull(rejected.nextState.overlay)
        assertEquals(firstSend.command.ref.targetInstance.runId, rejected.nextState.activeRunId)
        assertEquals(GameplayRunPhase.CREATED, rejected.nextState.gameplayPhase)

        val retry = AppSessionNucleus.decide(
            rejected.nextState,
            SessionInteractionPulse.StartRunRequested,
            AppSessionContext(
                runBootstrap = runBootstrap(profileSnapshot(rebirthProgress = advanced)),
                gameplayStatus = gameplayStatus(rejected.nextState, GameplayRunPhase.CREATED),
            ),
        ).accepted()
        val retrySend = assertIs<AppSessionOutput.SendGameplayCommand>(retry.outputs.single())
        assertEquals(firstSend.command.ref.targetInstance, retrySend.command.ref.targetInstance)

        val completed = AppSessionNucleus.decide(
            retry.nextState,
            SessionControlPulse.GameplayCommandCompleted(
                gameplayResult(retrySend.command.ref, GameplayCommandOutcome.RunStarted),
            ),
        ).accepted()
        assertEquals(AppDestination.Gameplay, completed.nextState.base)
        assertEquals(GameplayRunPhase.RUNNING, completed.nextState.gameplayPhase)
    }

    @Test
    fun rebirthContinuationReusesRetainedCreatedRunWithoutEnsureOrAnotherRunId() {
        val progress = RebirthProgress(level = 0, highestCleared = 0)
        val advanced = RebirthProgress(level = 1, highestCleared = 0)
        val retained = initialState().copy(
            activeRunId = RunId(Long.MAX_VALUE),
            gameplayPhase = GameplayRunPhase.CREATED,
            nextRunId = null,
        )
        val route = AppSessionNucleus.decide(
            retained,
            SessionInteractionPulse.OpenOverlay(AppDestination.Rebirth),
            AppSessionContext(
                gameplayStatus = gameplayStatus(retained, GameplayRunPhase.CREATED),
            ),
        ).accepted().nextState
        val armed = AppSessionNucleus.decide(
            route,
            SessionInteractionPulse.RebirthRequested,
            AppSessionContext(rebirthProgress = rebirthProjection(progress)),
        ).accepted().nextState
        val requested = AppSessionNucleus.decide(
            armed,
            SessionInteractionPulse.RebirthRequested,
            AppSessionContext(rebirthProgress = rebirthProjection(progress)),
        ).accepted()
        val profilePending = assertIs<PendingWorkflow.AdvancingRebirth>(requested.nextState.pendingWorkflow)

        val continued = AppSessionNucleus.decide(
            requested.nextState,
            SessionControlPulse.ProfileCommandCompleted(
                profileResult(
                    profilePending.participant.command.ref,
                    ProfileCommandOutcome.RebirthAdvanced(advanced),
                ),
            ),
            AppSessionContext(
                runBootstrap = runBootstrap(profileSnapshot(rebirthProgress = advanced)),
            ),
        ).accepted()
        val send = assertIs<AppSessionOutput.SendGameplayCommand>(continued.outputs[0])
        assertEquals(GameplayInstanceId(RunId(Long.MAX_VALUE)), send.command.ref.targetInstance)
        assertEquals(
            immutableListOf<AppSessionOutput>(
                send,
                AppSessionOutput.PlayRebirthAcceptedFeedback,
            ),
            continued.outputs,
        )
        assertNull(continued.nextState.nextRunId)
    }

    @Test
    fun exitNavigatesHomeOnlyForNoProgressOrAppliedProgress() {
        listOf(
            GameplayExitProfileOutcome.NoProgress,
            GameplayExitProfileOutcome.ProgressApplied,
        ).forEach { profileOutcome ->
            val exiting = exitFrame()
            val pending = assertIs<PendingWorkflow.ExitingRun>(exiting.nextState.pendingWorkflow)
            val completed = AppSessionNucleus.decide(
                exiting.nextState,
                SessionControlPulse.GameplayCommandCompleted(
                    gameplayResult(
                        pending.participant.command.ref,
                        GameplayCommandOutcome.RunExited(profileOutcome),
                    ),
                ),
            ).accepted()
            assertEquals(AppDestination.Home, completed.nextState.base)
            assertNull(completed.nextState.pendingWorkflow)
        }

        val rejectedExit = exitFrame()
        val pending = assertIs<PendingWorkflow.ExitingRun>(rejectedExit.nextState.pendingWorkflow)
        val completed = AppSessionNucleus.decide(
            rejectedExit.nextState,
            SessionControlPulse.GameplayCommandCompleted(
                gameplayResult(
                    pending.participant.command.ref,
                    GameplayCommandOutcome.RunExited(
                        GameplayExitProfileOutcome.ProgressRejected(
                            ProfileRevision(8L),
                            ProfileRejection.NoChange,
                        ),
                    ),
                ),
            ),
        ).accepted()
        assertEquals(AppDestination.Gameplay, completed.nextState.base)
        assertIs<SessionWorkflowFailure.ExitProgressRejected>(completed.nextState.lastFailure)
    }

    @Test
    fun remainingPreacceptRejectionRecoveryMatrixClearsPendingAndPreservesRetryableRoutes() {
        val running = gameplayState(GameplayRunPhase.RUNNING)
        val pausing = AppSessionNucleus.decide(
            running,
            SessionInteractionPulse.OpenOverlay(AppDestination.Lab),
            AppSessionContext(
                gameplayStatus = gameplayStatus(running, GameplayRunPhase.RUNNING),
            ),
        ).accepted()

        val preferences = PlayerPreferences(textScale = 1.25f)
        val settingsState = gameplayState(GameplayRunPhase.PAUSED).copy(
            overlay = AppDestination.Settings,
        )
        val applyingSettings = AppSessionNucleus.decide(
            settingsState,
            SessionInteractionPulse.CloseOverlay,
            AppSessionContext(
                preferences = preferencesProjection(preferences),
                gameplayStatus = gameplayStatus(settingsState, GameplayRunPhase.PAUSED),
            ),
        ).accepted()

        val progress = RebirthProgress(level = 0, highestCleared = 0)
        val victory = gameplayState(GameplayRunPhase.VICTORY)
        val rebirthOverlay = AppSessionNucleus.decide(
            victory,
            SessionInteractionPulse.OpenOverlay(AppDestination.Rebirth),
            AppSessionContext(
                gameplayStatus = gameplayStatus(victory, GameplayRunPhase.VICTORY),
            ),
        ).accepted().nextState
        val armed = AppSessionNucleus.decide(
            rebirthOverlay,
            SessionInteractionPulse.RebirthRequested,
            AppSessionContext(rebirthProgress = rebirthProjection(progress)),
        ).accepted().nextState
        val toggling = AppSessionNucleus.decide(
            armed,
            SessionInteractionPulse.ToggleMuteRequested,
        ).accepted()
        val mutePending = assertIs<PendingWorkflow.TogglingMute>(
            toggling.nextState.pendingWorkflow,
        )
        val mutedPreferences = PlayerPreferences(soundEnabled = false, musicEnabled = false)
        val propagatingMute = AppSessionNucleus.decide(
            toggling.nextState,
            SessionControlPulse.ProfileCommandCompleted(
                profileResult(
                    mutePending.participant.command.ref,
                    ProfileCommandOutcome.PreferencesChanged(mutedPreferences),
                ),
            ),
        ).accepted()

        val exiting = exitFrame()
        val cases = listOf(
            GameplayPreacceptRecoveryCase(
                label = "pause-for-overlay",
                issued = pausing,
                reason = GameplayRejection.PauseUnavailable,
                retry = { recovered ->
                    AppSessionNucleus.decide(
                        recovered,
                        SessionInteractionPulse.OpenOverlay(AppDestination.Lab),
                        AppSessionContext(
                            gameplayStatus = gameplayStatus(
                                recovered,
                                GameplayRunPhase.RUNNING,
                            ),
                        ),
                    )
                },
            ),
            GameplayPreacceptRecoveryCase(
                label = "settings-propagation",
                issued = applyingSettings,
                reason = GameplayRejection.NotStarted,
                retry = { recovered ->
                    AppSessionNucleus.decide(
                        recovered,
                        SessionInteractionPulse.CloseOverlay,
                        AppSessionContext(
                            preferences = preferencesProjection(preferences),
                            gameplayStatus = gameplayStatus(
                                recovered,
                                GameplayRunPhase.PAUSED,
                            ),
                        ),
                    )
                },
            ),
            GameplayPreacceptRecoveryCase(
                label = "mute-propagation",
                issued = propagatingMute,
                reason = GameplayRejection.NotStarted,
                retry = { recovered ->
                    AppSessionNucleus.decide(
                        recovered,
                        SessionInteractionPulse.ToggleMuteRequested,
                    )
                },
            ),
            GameplayPreacceptRecoveryCase(
                label = "exit-run",
                issued = exiting,
                reason = GameplayRejection.NotStarted,
                retry = { recovered ->
                    AppSessionNucleus.decide(
                        recovered,
                        SessionInteractionPulse.ExitRunRequested,
                        AppSessionContext(
                            gameplayStatus = gameplayStatus(
                                recovered,
                                GameplayRunPhase.RUNNING,
                            ),
                        ),
                    )
                },
            ),
        )

        cases.forEach { case ->
            val before = case.issued.nextState
            val pending = requireNotNull(before.pendingWorkflow)
            val participant = assertIs<PendingParticipantCommand.Gameplay>(pending.participant)
            val rejection = GameplayAcceptance.Rejected(
                instanceId = participant.command.ref.targetInstance,
                observedRevision = GameplayRevision(17L),
                reason = case.reason,
            )
            val recovered = AppSessionNucleus.decide(
                before,
                SessionControlPulse.GameplayCommandRejectedBeforeAcceptance(
                    participant.command.ref,
                    rejection,
                ),
            ).accepted()

            assertNull(recovered.nextState.pendingWorkflow, case.label)
            assertEquals(before.base, recovered.nextState.base, case.label)
            assertEquals(before.overlay, recovered.nextState.overlay, case.label)
            assertEquals(before.activeRunId, recovered.nextState.activeRunId, case.label)
            assertEquals(before.gameplayPhase, recovered.nextState.gameplayPhase, case.label)
            assertEquals(
                before.rebirthConfirmation,
                recovered.nextState.rebirthConfirmation,
                case.label,
            )
            assertEquals(
                SessionWorkflowFailure.GameplayCommandRejected(
                    participant.command.ref,
                    rejection,
                ),
                recovered.nextState.lastFailure,
                case.label,
            )
            assertTrue(recovered.outputs.isEmpty(), case.label)
            case.retry(recovered.nextState).accepted()
        }

        val rebirthRoute = AppSessionNucleus.decide(
            initialState(),
            SessionInteractionPulse.OpenOverlay(AppDestination.Rebirth),
        ).accepted().nextState
        val rebirthArmed = AppSessionNucleus.decide(
            rebirthRoute,
            SessionInteractionPulse.RebirthRequested,
            AppSessionContext(rebirthProgress = rebirthProjection(progress)),
        ).accepted().nextState
        val advancing = AppSessionNucleus.decide(
            rebirthArmed,
            SessionInteractionPulse.RebirthRequested,
            AppSessionContext(rebirthProgress = rebirthProjection(progress)),
        ).accepted()
        val profilePending = assertIs<PendingWorkflow.AdvancingRebirth>(
            advancing.nextState.pendingWorkflow,
        )
        val profileRejection = ProfileAcceptance.Rejected(
            instanceId = LOCAL_PROFILE_INSTANCE_ID,
            observedRevision = ProfileRevision(19L),
            reason = ProfileRejection.RebirthLevelNotCleared,
        )
        val rebirthRecovered = AppSessionNucleus.decide(
            advancing.nextState,
            SessionControlPulse.ProfileCommandRejectedBeforeAcceptance(
                profilePending.participant.command.ref,
                profileRejection,
            ),
        ).accepted()
        assertNull(rebirthRecovered.nextState.pendingWorkflow)
        assertEquals(AppDestination.Home, rebirthRecovered.nextState.base)
        assertEquals(AppDestination.Rebirth, rebirthRecovered.nextState.overlay)
        assertEquals(RebirthConfirmation.Disarmed, rebirthRecovered.nextState.rebirthConfirmation)
        assertEquals(
            SessionWorkflowFailure.ProfileCommandRejected(
                profilePending.participant.command.ref,
                profileRejection.observedRevision,
                profileRejection.reason,
            ),
            rebirthRecovered.nextState.lastFailure,
        )
        assertTrue(rebirthRecovered.outputs.isEmpty())

        val rearmed = AppSessionNucleus.decide(
            rebirthRecovered.nextState,
            SessionInteractionPulse.RebirthRequested,
            AppSessionContext(
                rebirthProgress = rebirthProjection(progress, revision = 20L),
            ),
        ).accepted()
        assertIs<RebirthConfirmation.Armed>(rearmed.nextState.rebirthConfirmation)
    }

    @Test
    fun resetIsBlockingCancelIsSameSemanticStateAndCompletionRefreshesAudio() {
        val persistence = confirmationPersistence()
        val initial = initialState(persistence = persistence)
        assertEquals(SessionResetLifecycle.CONFIRMATION_REQUIRED, initial.resetLifecycle)
        assertEquals(
            SessionRejection.ResetBlocksInput,
            AppSessionNucleus.decide(
                initial,
                SessionInteractionPulse.StartRunRequested,
                AppSessionContext(runBootstrap = runBootstrap()),
            ).rejection(),
        )

        val cancelled = AppSessionNucleus.decide(
            initial,
            SessionInteractionPulse.ResetCancelled,
        ).accepted()
        assertEquals(SessionResetLifecycle.CONFIRMATION_REQUIRED, cancelled.nextState.resetLifecycle)
        assertTrue(cancelled.outputs.isEmpty())

        val confirming = AppSessionNucleus.decide(
            cancelled.nextState,
            SessionInteractionPulse.ResetConfirmed,
            AppSessionContext(persistenceStatus = persistence),
        ).accepted()
        val pending = assertIs<PendingWorkflow.ConfirmingReset>(confirming.nextState.pendingWorkflow)
        assertEquals(SessionResetLifecycle.RESET_IN_PROGRESS, confirming.nextState.resetLifecycle)

        val defaults = PlayerPreferences()
        val completed = AppSessionNucleus.decide(
            confirming.nextState,
            SessionControlPulse.ProfileCommandCompleted(
                profileResult(
                    pending.participant.command.ref,
                    ProfileCommandOutcome.ResetCompleted,
                ),
            ),
            AppSessionContext(
                persistenceStatus = readyPersistence(),
                preferences = preferencesProjection(defaults),
            ),
        ).accepted()
        assertEquals(SessionResetLifecycle.READY, completed.nextState.resetLifecycle)
        assertEquals(
            immutableListOf<AppSessionOutput>(
                AppSessionOutput.SynchronizeAudioPreferences(defaults),
            ),
            completed.outputs,
        )
    }

    @Test
    fun resetFailuresStayBlockedRequireManualRetryAndRestoreModeAfterPreacceptRejection() {
        val confirmation = confirmationPersistence()
        val preferences = PlayerPreferences(masterVolume = 0.6f)

        fun completeConfirmation(
            outcome: ProfileCommandOutcome,
            resultPersistence: PersistenceStatusProjection = confirmation,
        ): AppSessionAcceptedFrame {
            val confirming = AppSessionNucleus.decide(
                initialState(persistence = confirmation),
                SessionInteractionPulse.ResetConfirmed,
                AppSessionContext(persistenceStatus = confirmation),
            ).accepted()
            val pending = assertIs<PendingWorkflow.ConfirmingReset>(
                confirming.nextState.pendingWorkflow,
            )
            return AppSessionNucleus.decide(
                confirming.nextState,
                SessionControlPulse.ProfileCommandCompleted(
                    profileResult(pending.participant.command.ref, outcome),
                ),
                AppSessionContext(
                    persistenceStatus = resultPersistence,
                    preferences = preferencesProjection(preferences),
                ),
            ).accepted()
        }

        val writeRejected = completeConfirmation(
            ProfileCommandOutcome.ResetWriteRejected(ProfileV4Rejection.VALUE_OUT_OF_RANGE),
        )
        assertEquals(
            SessionResetLifecycle.CONFIRMATION_REQUIRED,
            writeRejected.nextState.resetLifecycle,
        )
        assertIs<SessionWorkflowFailure.ResetWriteRejected>(writeRejected.nextState.lastFailure)
        assertEquals(
            immutableListOf<AppSessionOutput>(
                AppSessionOutput.SynchronizeAudioPreferences(preferences),
            ),
            writeRejected.outputs,
        )

        val writeUnknown = completeConfirmation(
            ProfileCommandOutcome.ResetWriteOutcomeUnknown(
                ProfileResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
            ),
        )
        assertEquals(
            SessionResetLifecycle.CONFIRMATION_REQUIRED,
            writeUnknown.nextState.resetLifecycle,
        )
        assertIs<SessionWorkflowFailure.ResetWriteOutcomeUnknown>(
            writeUnknown.nextState.lastFailure,
        )

        val attentionPersistence = needsAttentionPersistence()
        val attentionStatus = assertIs<ProfileResetStatus.NeedsAttention>(
            attentionPersistence.reset,
        )
        val attention = completeConfirmation(
            ProfileCommandOutcome.ResetNeedsAttention(attentionStatus),
            resultPersistence = attentionPersistence,
        )
        assertEquals(
            SessionResetLifecycle.PURGE_NEEDS_ATTENTION,
            attention.nextState.resetLifecycle,
        )
        assertIs<SessionWorkflowFailure.ResetNeedsAttention>(attention.nextState.lastFailure)
        assertTrue(attention.outputs.none { it is AppSessionOutput.SendProfileCommand })

        val retry = AppSessionNucleus.decide(
            attention.nextState,
            SessionInteractionPulse.ResetRetryRequested,
            AppSessionContext(persistenceStatus = attentionPersistence),
        ).accepted()
        val retryPending = assertIs<PendingWorkflow.RetryingPurge>(retry.nextState.pendingWorkflow)
        assertEquals(SessionResetLifecycle.RESET_IN_PROGRESS, retry.nextState.resetLifecycle)
        assertIs<AppSessionOutput.SendProfileCommand>(retry.outputs.single())

        val rejectedBeforeAcceptance = AppSessionNucleus.decide(
            retry.nextState,
            SessionControlPulse.ProfileCommandRejectedBeforeAcceptance(
                retryPending.participant.command.ref,
                ProfileAcceptance.Rejected(
                    LOCAL_PROFILE_INSTANCE_ID,
                    ProfileRevision(17L),
                    ProfileRejection.ResetRequired,
                ),
            ),
        ).accepted()
        assertEquals(
            SessionResetLifecycle.PURGE_NEEDS_ATTENTION,
            rejectedBeforeAcceptance.nextState.resetLifecycle,
        )
        assertNull(rejectedBeforeAcceptance.nextState.pendingWorkflow)

        val retryAgain = AppSessionNucleus.decide(
            rejectedBeforeAcceptance.nextState,
            SessionInteractionPulse.ResetRetryRequested,
            AppSessionContext(persistenceStatus = attentionPersistence),
        ).accepted()
        val retryAgainPending = assertIs<PendingWorkflow.RetryingPurge>(
            retryAgain.nextState.pendingWorkflow,
        )
        val completed = AppSessionNucleus.decide(
            retryAgain.nextState,
            SessionControlPulse.ProfileCommandCompleted(
                profileResult(
                    retryAgainPending.participant.command.ref,
                    ProfileCommandOutcome.ResetCompleted,
                ),
            ),
            AppSessionContext(
                persistenceStatus = readyPersistence(),
                preferences = preferencesProjection(preferences),
            ),
        ).accepted()
        assertEquals(SessionResetLifecycle.READY, completed.nextState.resetLifecycle)
        assertNull(completed.nextState.pendingWorkflow)
    }

    @Test
    fun participantCorrelationRejectsEveryUnverifiedCarrierWithoutConsumingPending() {
        val start = startFrame()
        val pending = assertIs<PendingWorkflow.StartingRun>(start.nextState.pendingWorkflow)
        val ref = pending.participant.command.ref

        assertUnexpected(
            SessionParticipantResultRejection.PARTICIPANT_MISMATCH,
            AppSessionNucleus.decide(
                start.nextState,
                SessionControlPulse.ProfileCommandCompleted(
                    profileResult(
                        ProfileCommandRef(
                            ProfileCommandSource.LocalSession,
                            LOCAL_PROFILE_INSTANCE_ID,
                            ref.sourceRevision,
                            ref.ordinal,
                        ),
                        ProfileCommandOutcome.PreferencesChanged(PlayerPreferences()),
                    ),
                ),
            ),
        )

        listOf(
            ref.copy(
                targetInstance = GameplayInstanceId(RunId(ref.targetInstance.runId.value + 1L)),
            ),
            ref.copy(sourceRevision = ref.sourceRevision + 1L),
            ref.copy(ordinal = ref.ordinal + 1),
        ).forEach { wrongRef ->
            assertUnexpected(
                SessionParticipantResultRejection.COMMAND_REF_MISMATCH,
                AppSessionNucleus.decide(
                    start.nextState,
                    SessionControlPulse.GameplayCommandCompleted(
                        gameplayResult(wrongRef, GameplayCommandOutcome.RunStarted),
                    ),
                ),
            )
        }
        assertUnexpected(
            SessionParticipantResultRejection.OUTCOME_MISMATCH,
            AppSessionNucleus.decide(
                start.nextState,
                SessionControlPulse.GameplayCommandCompleted(
                    gameplayResult(ref, GameplayCommandOutcome.OverlayPaused),
                ),
            ),
        )
        assertUnexpected(
            SessionParticipantResultRejection.TARGET_INSTANCE_MISMATCH,
            AppSessionNucleus.decide(
                start.nextState,
                SessionControlPulse.GameplayCommandRejectedBeforeAcceptance(
                    ref,
                    GameplayAcceptance.Rejected(
                        GameplayInstanceId(RunId(999L)),
                        GameplayRevision.ZERO,
                        GameplayRejection.AlreadyStarted,
                    ),
                ),
            ),
        )
        assertUnexpected(
            SessionParticipantResultRejection.NO_COMMAND_PENDING,
            AppSessionNucleus.decide(
                initialState(),
                SessionControlPulse.GameplayCommandCompleted(
                    gameplayResult(ref, GameplayCommandOutcome.RunStarted),
                ),
            ),
        )

        val selecting = AppSessionNucleus.decide(
            initialState(),
            SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM),
        ).accepted()
        val profilePending = assertIs<PendingWorkflow.SelectingCoreShape>(
            selecting.nextState.pendingWorkflow,
        )
        val profileRef = profilePending.participant.command.ref
        listOf(
            profileRef.copy(sourceInstance = ProfileCommandSource.GameplayRun(71L)),
            profileRef.copy(sourceRevision = profileRef.sourceRevision + 1L),
            profileRef.copy(ordinal = profileRef.ordinal + 1),
        ).forEach { wrongRef ->
            assertUnexpected(
                SessionParticipantResultRejection.COMMAND_REF_MISMATCH,
                AppSessionNucleus.decide(
                    selecting.nextState,
                    SessionControlPulse.ProfileCommandCompleted(
                        profileResult(
                            wrongRef,
                            ProfileCommandOutcome.CoreShapeSelected(CoreShape.PRISM),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun exhaustionAndPendingGatesRejectBeforeIssuingParticipantCommands() {
        val bootstrapContext = AppSessionContext(runBootstrap = runBootstrap())
        assertEquals(
            SessionRejection.RunIdExhausted,
            AppSessionNucleus.decide(
                initialState().copy(nextRunId = null),
                SessionInteractionPulse.StartRunRequested,
                bootstrapContext,
            ).rejection(),
        )
        assertEquals(
            SessionRejection.GameplayCommandOrdinalExhausted,
            AppSessionNucleus.decide(
                initialState().copy(nextGameplayCommandOrdinal = null),
                SessionInteractionPulse.StartRunRequested,
                bootstrapContext,
            ).rejection(),
        )
        assertEquals(
            SessionRejection.ProfileCommandOrdinalExhausted,
            AppSessionNucleus.decide(
                initialState().copy(nextProfileCommandOrdinal = null),
                SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM),
            ).rejection(),
        )
        assertEquals(
            SessionRejection.RevisionExhausted,
            AppSessionNucleus.decide(
                initialState().copy(revision = SessionRevision(Long.MAX_VALUE - 1L)),
                SessionInteractionPulse.StartRunRequested,
                bootstrapContext,
            ).rejection(),
        )
        val pending = startFrame().nextState
        assertEquals(
            SessionRejection.ParticipantCommandPending,
            AppSessionNucleus.decide(
                pending,
                SessionInteractionPulse.OpenOverlay(AppDestination.Lab),
            ).rejection(),
        )
    }

    @Test
    fun acceptedFrameEnforcesOutputBoundAndEnsureParticipantFeedbackOrder() {
        val start = startFrame()
        val ensure = assertIs<AppSessionOutput.EnsureGameplayRun>(start.outputs[0])
        val send = assertIs<AppSessionOutput.SendGameplayCommand>(start.outputs[1])
        val outputs = immutableListOf<AppSessionOutput>(
            ensure,
            send,
            AppSessionOutput.PlayRebirthAcceptedFeedback,
        )

        AppSessionAcceptedFrame(start.nextState, start.shellProjection, outputs)

        assertFailsWith<IllegalArgumentException> {
            AppSessionAcceptedFrame(
                start.nextState,
                start.shellProjection,
                immutableListOf(
                    ensure,
                    send,
                    AppSessionOutput.PlayRebirthAcceptedFeedback,
                    AppSessionOutput.PlayMuteFeedback,
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AppSessionAcceptedFrame(
                start.nextState,
                start.shellProjection,
                immutableListOf(send, ensure),
            )
        }
    }
}

private data class GameplayPreacceptRecoveryCase(
    val label: String,
    val issued: AppSessionAcceptedFrame,
    val reason: GameplayRejection,
    val retry: (AppSessionState) -> AppSessionDecision,
)

private fun AppSessionDecision.accepted(): AppSessionAcceptedFrame =
    assertIs<AppSessionDecision.Accepted>(this).frame.also { frame ->
        assertTrue(frame.outputs.size <= MAX_SESSION_OUTPUTS_PER_DECISION)
        assertEquals(frame.nextState.instanceId, frame.shellProjection.instanceId)
        assertEquals(frame.nextState.revision, frame.shellProjection.revision)
    }

private fun AppSessionDecision.rejection(): SessionRejection =
    assertIs<AppSessionDecision.Rejected>(this).reason

private fun assertUnexpected(
    expected: SessionParticipantResultRejection,
    decision: AppSessionDecision,
) {
    assertEquals(
        SessionRejection.UnexpectedParticipantResult(expected),
        decision.rejection(),
    )
}

private fun startFrame(): AppSessionAcceptedFrame = AppSessionNucleus.decide(
    initialState(),
    SessionInteractionPulse.StartRunRequested,
    AppSessionContext(runBootstrap = runBootstrap()),
).accepted()

private fun exitFrame(): AppSessionAcceptedFrame {
    val state = gameplayState(GameplayRunPhase.RUNNING)
    return AppSessionNucleus.decide(
        state,
        SessionInteractionPulse.ExitRunRequested,
        AppSessionContext(gameplayStatus = gameplayStatus(state, GameplayRunPhase.RUNNING)),
    ).accepted()
}

private fun initialState(
    content: GameplayContentSnapshot = minimalGameplayContent(),
    persistence: PersistenceStatusProjection = readyPersistence(),
): AppSessionState = AppSessionState.initial(SessionConfiguration(content), persistence)

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
    profileCommandPending = false,
)

private fun gameplayResult(
    ref: GameplayCommandRef,
    outcome: GameplayCommandOutcome,
): GameplayCommandResult.Accepted = GameplayCommandResult.Accepted(
    commandRef = ref,
    targetRevision = GameplayRevision(5L),
    outcome = outcome,
)

private fun profileResult(
    ref: ProfileCommandRef,
    outcome: ProfileCommandOutcome,
): ProfileCommandResult.Accepted = ProfileCommandResult.Accepted(
    commandRef = ref,
    targetRevision = ProfileRevision(5L),
    outcome = outcome,
)

private fun runBootstrap(
    snapshot: GameplayProfileSnapshot = profileSnapshot(),
): RunBootstrapProjection = RunBootstrapProjection(
    instanceId = LOCAL_PROFILE_INSTANCE_ID,
    revision = ProfileRevision(3L),
    result = ProfileRunBootstrapResult.Ready(snapshot),
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
    reset = ProfileResetStatus.NotRequired(legacyResetConfirmed = false),
    persistence = ProfilePersistenceStatus.Persisted(ProfileRevision(1L)),
)

private fun confirmationPersistence(): PersistenceStatusProjection = PersistenceStatusProjection(
    instanceId = LOCAL_PROFILE_INSTANCE_ID,
    revision = ProfileRevision(1L),
    bootstrap = ProfileBootstrapStatus.Blocked(
        kinetickk.ball.profile.api.ProfileBootstrapBlockReason.ResetRequired(
            kinetickk.ball.profile.api.ProfileResetReason.LegacyDataDetected,
        ),
    ),
    reset = ProfileResetStatus.ConfirmationRequired(
        kinetickk.ball.profile.api.ProfileResetReason.LegacyDataDetected,
        kinetickk.ball.profile.api.ProfileLegacyKeys.ALL,
    ),
    persistence = ProfilePersistenceStatus.NotAttempted,
)

private fun needsAttentionPersistence(): PersistenceStatusProjection {
    val purgeResult = ProfileLegacyPurgeResult.Partial(ProfileLegacyKeys.ALL)
    return PersistenceStatusProjection(
        instanceId = LOCAL_PROFILE_INSTANCE_ID,
        revision = ProfileRevision(6L),
        bootstrap = ProfileBootstrapStatus.Blocked(
            ProfileBootstrapBlockReason.ResetNeedsAttention(purgeResult),
        ),
        reset = ProfileResetStatus.NeedsAttention(
            legacyKeys = ProfileLegacyKeys.ALL,
            result = purgeResult,
        ),
        persistence = ProfilePersistenceStatus.NotAttempted,
    )
}

private fun minimalGameplayContent(): GameplayContentSnapshot {
    val profile = RebirthProfile(
        tier = 0,
        directive = RebirthDirective.BASELINE,
        openingEnemyCount = 1,
        enemyCapMultiplier = 1f,
        spawnRateMultiplier = 1f,
        enemyHealthMultiplier = 1f,
        enemySpeedMultiplier = 1f,
        incomingDamageMultiplier = 1f,
        eliteRateMultiplier = 1f,
        threatTimeOffsetSeconds = 0f,
        playerPowerMultiplier = 1f,
        playerIntegrityBonus = 0f,
        matterGainMultiplier = 1f,
        bonusRerolls = 0,
        maximumActiveEnemies = 1,
        minimumSpawnIntervalSeconds = 1f,
        minimumEliteIntervalSeconds = 1f,
    )
    return GameplayContentSnapshot(
        version = ContentVersion("session-nucleus-test"),
        items = immutableListOf(),
        weapons = immutableListOf(),
        weaponMasteries = immutableListOf(),
        metaUpgrades = immutableListOf(),
        relics = immutableListOf(),
        rebirth = RebirthPolicySnapshot(
            minimumLevel = 0,
            maximumLevel = 0,
            profiles = immutableListOf(profile),
            maxActiveEnemies = 1,
            minSpawnIntervalSeconds = 1f,
            minEliteIntervalSeconds = 1f,
        ),
        relicPolicy = RelicPolicy(maxSlots = 1, maxRank = 1),
    )
}
