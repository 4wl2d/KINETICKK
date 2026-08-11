// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.nucleus

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.api.GameplayCommandBoundaryResponse
import kinetickk.ball.gameplay.api.GameplayCommandSourceToken
import kinetickk.ball.gameplay.api.GameplayEffectiveProtocolIdentity
import kinetickk.ball.gameplay.api.GameplayExitProgressResult
import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.gameplay.api.GameplayModuleCommand
import kinetickk.ball.gameplay.api.GameplayModuleCommandRequest
import kinetickk.ball.gameplay.api.GameplayModuleResult
import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.gameplay.api.GameplayResultIssuerProvenance
import kinetickk.ball.gameplay.api.GameplayResultSourceToken
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplayRunStatusProjection
import kinetickk.ball.gameplay.api.GameplayTargetBoundaryProvenance
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
import kinetickk.ball.profile.api.ProfileBootstrapBlockReason
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileCommandBoundaryResponse
import kinetickk.ball.profile.api.ProfileCommandSourceToken
import kinetickk.ball.profile.api.ProfileEffectiveProtocolIdentity
import kinetickk.ball.profile.api.ProfileLegacyKeys
import kinetickk.ball.profile.api.ProfileLegacyPurgeResult
import kinetickk.ball.profile.api.ProfileModuleCommand
import kinetickk.ball.profile.api.ProfileModuleCommandRequest
import kinetickk.ball.profile.api.ProfileModuleResult
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileResetReason
import kinetickk.ball.profile.api.ProfileResetStatus
import kinetickk.ball.profile.api.ProfileResultIssuerProvenance
import kinetickk.ball.profile.api.ProfileResultSourceToken
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.ProfileTargetBoundaryProvenance
import kinetickk.ball.profile.api.ProfileV4Rejection
import kinetickk.ball.profile.api.RebirthProfileSnapshot
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.AppSessionQuery
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.api.SessionRejection
import kinetickk.flow.session.api.SessionResetLifecycle
import kinetickk.flow.session.api.SessionRevision
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
        val gameplayRequest = assertIs<AppSessionOutput.SendGameplayCommand>(start.outputs.last()).request
        val mute = decide(initialState(), SessionInteractionPulse.ToggleMuteRequested).accepted()
        val profileRequest = assertIs<AppSessionOutput.SendProfileCommand>(mute.outputs.single()).request

        val intents: List<AppSessionNucleusPulse> = listOf(
            AppSessionNucleusPulse.Intent(SessionInteractionPulse.StartRunRequested),
        )
        val results: List<AppSessionNucleusPulse.ModuleResultPulse> = listOf(
            gameplayResult(gameplayRequest, GameplayModuleResult.RunStarted),
            profileResult(
                profileRequest,
                ProfileModuleResult.PreferencesChanged(PlayerPreferences()),
            ),
        )
        val controls: List<AppSessionNucleusPulse.ControlPulse> = listOf(
            gameplayRefusal(gameplayRequest),
            profileRefusal(profileRequest),
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
        val send = assertIs<AppSessionOutput.SendGameplayCommand>(frame.outputs[1])
        assertEquals(GameplayModuleCommand.StartRun, send.request.command)
        assertEquals(SessionRevision(1L).value, send.request.semanticHandle.sourceRevision)
        assertEquals(1, send.request.sourceOrdinal)
        assertEquals(RunId(0L), send.request.targetInstance.runId)

        val completed = AppSessionNucleus.decide(
            frame.nextState,
            gameplayResult(send.request, GameplayModuleResult.RunStarted),
        ).accepted()
        assertEquals(AppDestination.Gameplay, completed.nextState.base)
        assertEquals(GameplayRunPhase.RUNNING, completed.nextState.gameplayPhase)
        assertNull(completed.nextState.pendingWorkflow)
        assertEquals(SessionRevision(2L), completed.nextState.revision)
    }

    @Test
    fun rejectedStartRetainsCreatedRunForExactReuseWithoutSecondEnsure() {
        val first = startFrame()
        val send = assertIs<AppSessionOutput.SendGameplayCommand>(first.outputs.last())
        val rejected = AppSessionNucleus.decide(
            first.nextState,
            gameplayRefusal(send.request),
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
        val retrySend = assertIs<AppSessionOutput.SendGameplayCommand>(retry.outputs.single())
        assertEquals(RunId(0L), retrySend.request.targetInstance.runId)
        assertEquals(0, retrySend.request.sourceOrdinal)
        assertEquals(retry.nextState.revision.value, retrySend.request.semanticHandle.sourceRevision)
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
            val send = assertIs<AppSessionOutput.SendGameplayCommand>(pause.outputs.single())
            assertEquals(GameplayModuleCommand.PauseForOverlay, send.request.command)

            val opened = AppSessionNucleus.decide(
                pause.nextState,
                gameplayResult(send.request, GameplayModuleResult.OverlayPaused),
            ).accepted()
            assertEquals(destination, opened.nextState.overlay)
            assertEquals(GameplayRunPhase.PAUSED, opened.nextState.gameplayPhase)
        }
    }

    @Test
    fun settingsCloseReadsPreferencesThenSendsDataFreeGameplayCommand() {
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

        val send = assertIs<AppSessionOutput.SendGameplayCommand>(close.outputs.single())
        assertEquals(GameplayModuleCommand.ApplyPreferences, send.request.command)
        assertIs<PendingWorkflow.ApplyingSettings>(close.nextState.pendingWorkflow)

        val completed = AppSessionNucleus.decide(
            close.nextState,
            gameplayResult(send.request, GameplayModuleResult.PreferencesApplied),
        ).accepted()
        assertNull(completed.nextState.overlay)
        assertEquals(
            AppSessionOutput.SynchronizeAudioPreferences(preferences),
            completed.outputs.single(),
        )
    }

    @Test
    fun muteResultSynchronizesAudioAndOptionallyPropagatesDataFreePreferences() {
        val preferences = PlayerPreferences(soundEnabled = false, musicEnabled = false)
        val running = gameplayState(GameplayRunPhase.RUNNING)
        val requested = decide(running, SessionInteractionPulse.ToggleMuteRequested).accepted()
        val profileSend = assertIs<AppSessionOutput.SendProfileCommand>(requested.outputs.single())
        assertEquals(ProfileModuleCommand.ToggleMute, profileSend.request.command)

        val propagated = AppSessionNucleus.decide(
            requested.nextState,
            profileResult(
                profileSend.request,
                ProfileModuleResult.PreferencesChanged(preferences),
            ),
        ).accepted()
        assertEquals(3, propagated.outputs.size)
        val gameplaySend = assertIs<AppSessionOutput.SendGameplayCommand>(propagated.outputs[0])
        assertEquals(GameplayModuleCommand.ApplyPreferences, gameplaySend.request.command)
        assertEquals(AppSessionOutput.SynchronizeAudioPreferences(preferences), propagated.outputs[1])
        assertEquals(AppSessionOutput.PlayMuteFeedback, propagated.outputs[2])

        val completed = AppSessionNucleus.decide(
            propagated.nextState,
            gameplayResult(gameplaySend.request, GameplayModuleResult.PreferencesApplied),
        ).accepted()
        assertNull(completed.nextState.pendingWorkflow)

        val homeRequested = decide(initialState(), SessionInteractionPulse.ToggleMuteRequested).accepted()
        val homeSend = assertIs<AppSessionOutput.SendProfileCommand>(homeRequested.outputs.single())
        val homeCompleted = AppSessionNucleus.decide(
            homeRequested.nextState,
            profileResult(homeSend.request, ProfileModuleResult.PreferencesChanged(preferences)),
        ).accepted()
        assertEquals(2, homeCompleted.outputs.size)
        assertTrue(homeCompleted.outputs.none { it is AppSessionOutput.SendGameplayCommand })
    }

    @Test
    fun coreShapeMappingRetainsExactRequestedAndAcceptedShape() {
        val requested = decide(
            initialState(),
            SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM),
        ).accepted()
        val send = assertIs<AppSessionOutput.SendProfileCommand>(requested.outputs.single())
        assertEquals(ProfileModuleCommand.SelectCoreShape(CoreShape.PRISM), send.request.command)

        val completed = AppSessionNucleus.decide(
            requested.nextState,
            profileResult(
                send.request,
                ProfileModuleResult.CoreShapeSelected(CoreShape.PRISM),
            ),
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
        val profileSend = assertIs<AppSessionOutput.SendProfileCommand>(requested.outputs.single())
        assertEquals(ProfileModuleCommand.AdvanceRebirth, profileSend.request.command)

        val starting = AppSessionNucleus.decide(
            requested.nextState,
            profileResult(profileSend.request, ProfileModuleResult.RebirthAdvanced(advanced)),
            AppSessionContext(runBootstrap = runBootstrap(rebirthProgress = advanced)),
        ).accepted()
        assertEquals(3, starting.outputs.size)
        assertEquals(AppSessionOutput.EnsureGameplayRun(RunId(0L)), starting.outputs[0])
        val gameplaySend = assertIs<AppSessionOutput.SendGameplayCommand>(starting.outputs[1])
        assertEquals(1, gameplaySend.request.sourceOrdinal)
        assertEquals(GameplayModuleCommand.StartRun, gameplaySend.request.command)
        assertEquals(AppSessionOutput.PlayRebirthAcceptedFeedback, starting.outputs[2])

        val completed = AppSessionNucleus.decide(
            starting.nextState,
            gameplayResult(gameplaySend.request, GameplayModuleResult.RunStarted),
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
            val send = assertIs<AppSessionOutput.SendGameplayCommand>(exit.outputs.single())
            val completed = AppSessionNucleus.decide(
                exit.nextState,
                gameplayResult(send.request, GameplayModuleResult.RunExited(progress)),
            ).accepted()
            assertEquals(AppDestination.Home, completed.nextState.base)
            assertNull(completed.nextState.lastFailure)
        }

        val exit = exitFrame()
        val send = assertIs<AppSessionOutput.SendGameplayCommand>(exit.outputs.single())
        val notApplied = AppSessionNucleus.decide(
            exit.nextState,
            gameplayResult(
                send.request,
                GameplayModuleResult.RunExited(GameplayExitProgressResult.NotApplied),
            ),
        ).accepted()
        assertEquals(AppDestination.Gameplay, notApplied.nextState.base)
        assertEquals(GameplayRunPhase.EXITED, notApplied.nextState.gameplayPhase)
        assertEquals(
            SessionWorkflowFailureCode.EXIT_PROGRESS_NOT_APPLIED,
            notApplied.nextState.lastFailure,
        )
    }

    @Test
    fun resetCancelAdvancesOnlySessionRevisionAndKeepsEveryResetModalBlocking() {
        val blockingLifecycles = SessionResetLifecycle.entries.filterNot {
            it == SessionResetLifecycle.READY
        }

        blockingLifecycles.forEach { lifecycle ->
            val state = initialState().copy(
                revision = SessionRevision(12L),
                routeRevision = SessionRevision(7L),
                overlay = AppDestination.Settings,
                resetLifecycle = lifecycle,
                lastFailure = SessionWorkflowFailureCode.RESET_NEEDS_ATTENTION,
            )

            val cancelled = decide(state, SessionInteractionPulse.ResetCancelled).accepted()

            assertEquals(
                state.copy(revision = SessionRevision(13L)),
                cancelled.nextState,
                "Cancel must retain the complete blocking Session context for $lifecycle",
            )
            assertTrue(
                cancelled.outputs.isEmpty(),
                "Cancel must not invoke Profile, Gameplay, storage, or another participant for $lifecycle",
            )
            assertEquals(lifecycle, cancelled.nextState.toShell().resetLifecycle)
            assertFalse(cancelled.nextState.toShell().normalInputEnabled)
        }
    }

    @Test
    fun resetConfirmationMapsExactResultAndSynchronizesFreshPreferences() {
        val state = initialState(confirmationPersistence())
        val requested = decide(
            state,
            SessionInteractionPulse.ResetConfirmed,
            AppSessionContext(persistenceStatus = confirmationPersistence()),
        ).accepted()
        val send = assertIs<AppSessionOutput.SendProfileCommand>(requested.outputs.single())
        assertEquals(ProfileModuleCommand.ConfirmLegacyReset, send.request.command)
        assertEquals(SessionResetLifecycle.RESET_IN_PROGRESS, requested.nextState.resetLifecycle)

        val preferences = PlayerPreferences(textScale = 1.5f)
        val completed = AppSessionNucleus.decide(
            requested.nextState,
            profileResult(send.request, ProfileModuleResult.ResetCompleted),
            AppSessionContext(
                persistenceStatus = readyPersistence(),
                preferences = preferencesProjection(preferences),
            ),
        ).accepted()
        assertEquals(SessionResetLifecycle.READY, completed.nextState.resetLifecycle)
        assertNull(completed.nextState.lastFailure)
        assertEquals(
            AppSessionOutput.SynchronizeAudioPreferences(preferences),
            completed.outputs.single(),
        )
    }

    @Test
    fun resetFailureMatrixUsesOnlyNarrowSessionFailureCodes() {
        val cases = listOf(
            Triple(
                ProfileModuleResult.ResetWriteRejected(ProfileV4Rejection.INCONSISTENT_PROFILE),
                confirmationPersistence(),
                SessionWorkflowFailureCode.RESET_WRITE_REJECTED,
            ),
            Triple(
                ProfileModuleResult.ResetWriteResourceFailure(
                    kinetickk.ball.profile.api.ProfileWriteFailure
                        .PROVIDER_WRITE_FAILED_BEFORE_EXECUTION,
                ),
                confirmationPersistence(),
                SessionWorkflowFailureCode.RESET_WRITE_RESOURCE_FAILURE,
            ),
            Triple(
                ProfileModuleResult.ResetWriteOutcomeUnknown(
                    kinetickk.ball.profile.api.ProfileWriteOutcomeUnknownReason
                        .PROVIDER_WRITE_MAY_HAVE_EXECUTED,
                ),
                confirmationPersistence(),
                SessionWorkflowFailureCode.RESET_WRITE_OUTCOME_UNKNOWN,
            ),
            Triple(
                ProfileModuleResult.ResetNeedsAttention(
                    assertIs<ProfileResetStatus.NeedsAttention>(needsAttentionPersistence().reset),
                ),
                needsAttentionPersistence(),
                SessionWorkflowFailureCode.RESET_NEEDS_ATTENTION,
            ),
        )

        cases.forEach { (result, persistence, expected) ->
            val retry = result is ProfileModuleResult.ResetNeedsAttention
            val initialPersistence = if (retry) needsAttentionPersistence() else confirmationPersistence()
            val state = initialState(initialPersistence)
            val intent = if (retry) {
                SessionInteractionPulse.ResetRetryRequested
            } else {
                SessionInteractionPulse.ResetConfirmed
            }
            val requested = decide(
                state,
                intent,
                AppSessionContext(persistenceStatus = initialPersistence),
            ).accepted()
            val send = assertIs<AppSessionOutput.SendProfileCommand>(requested.outputs.single())
            val completed = AppSessionNucleus.decide(
                requested.nextState,
                profileResult(send.request, result),
                AppSessionContext(
                    persistenceStatus = persistence,
                    preferences = preferencesProjection(PlayerPreferences()),
                ),
            ).accepted()
            assertEquals(expected, completed.nextState.lastFailure)
        }
    }

    @Test
    fun resetWriteFailureRetriesRequireOneExplicitPulseAndCreateOneNewSemanticCommand() {
        val failures = listOf<ProfileModuleResult>(
            ProfileModuleResult.ResetWriteRejected(ProfileV4Rejection.INCONSISTENT_PROFILE),
            ProfileModuleResult.ResetWriteResourceFailure(
                kinetickk.ball.profile.api.ProfileWriteFailure
                    .PROVIDER_WRITE_FAILED_BEFORE_EXECUTION,
            ),
            ProfileModuleResult.ResetWriteOutcomeUnknown(
                kinetickk.ball.profile.api.ProfileWriteOutcomeUnknownReason
                    .PROVIDER_WRITE_MAY_HAVE_EXECUTED,
            ),
        )

        failures.forEach { failure ->
            val persistence = confirmationPersistence()
            val first = decide(
                initialState(persistence),
                SessionInteractionPulse.ResetConfirmed,
                AppSessionContext(persistenceStatus = persistence),
            ).accepted()
            val firstSend = assertIs<AppSessionOutput.SendProfileCommand>(first.outputs.single())
            assertEquals(ProfileModuleCommand.ConfirmLegacyReset, firstSend.request.command)

            val completed = AppSessionNucleus.decide(
                first.nextState,
                profileResult(firstSend.request, failure),
                AppSessionContext(
                    persistenceStatus = persistence,
                    preferences = preferencesProjection(PlayerPreferences()),
                ),
            ).accepted()
            assertEquals(SessionResetLifecycle.CONFIRMATION_REQUIRED, completed.nextState.resetLifecycle)
            assertNull(completed.nextState.pendingWorkflow)
            assertIs<AppSessionOutput.SynchronizeAudioPreferences>(completed.outputs.single())

            val retried = decide(
                completed.nextState,
                SessionInteractionPulse.ResetConfirmed,
                AppSessionContext(persistenceStatus = persistence),
            ).accepted()
            val retrySend = assertIs<AppSessionOutput.SendProfileCommand>(retried.outputs.single())
            assertEquals(ProfileModuleCommand.ConfirmLegacyReset, retrySend.request.command)
            assertEquals(0, retrySend.request.sourceOrdinal)
            assertFalse(
                firstSend.request.semanticHandle == retrySend.request.semanticHandle,
                "A manual semantic retry must not resend the prior accepted command identity",
            )
            assertEquals(
                retried.nextState.revision.value,
                retrySend.request.semanticHandle.sourceRevision,
            )
            assertEquals(SessionResetLifecycle.RESET_IN_PROGRESS, retried.nextState.resetLifecycle)
            assertNull(retried.nextState.lastFailure)
        }
    }

    @Test
    fun oneExplicitResetRetryPulseIssuesExactlyOnePurgeCommand() {
        val state = initialState(needsAttentionPersistence())
        assertEquals(0L, state.revision.value)
        assertNull(state.pendingWorkflow, "local partial result must not auto-retry")

        val retry = decide(
            state,
            SessionInteractionPulse.ResetRetryRequested,
            AppSessionContext(persistenceStatus = needsAttentionPersistence()),
        ).accepted()

        val send = assertIs<AppSessionOutput.SendProfileCommand>(retry.outputs.single())
        assertEquals(ProfileModuleCommand.RetryLegacyPurge, send.request.command)
        assertEquals(0, send.request.sourceOrdinal)
        assertEquals(retry.nextState.revision.value, send.request.semanticHandle.sourceRevision)
    }

    @Test
    fun participantRefusalCarriersRecoverWithoutExposingTargetPayloads() {
        val profileRequested = decide(
            initialState(),
            SessionInteractionPulse.ToggleMuteRequested,
        ).accepted()
        val profileSend = assertIs<AppSessionOutput.SendProfileCommand>(profileRequested.outputs.single())
        val profileRecovered = AppSessionNucleus.decide(
            profileRequested.nextState,
            profileRefusal(profileSend.request),
        ).accepted()
        assertEquals(SessionWorkflowFailureCode.PROFILE_COMMAND_REFUSED, profileRecovered.nextState.lastFailure)
        assertEquals(AppSessionOutput.PlayMuteFeedback, profileRecovered.outputs.single())

        val gameplayRequested = startFrame()
        val gameplaySend = assertIs<AppSessionOutput.SendGameplayCommand>(gameplayRequested.outputs.last())
        val gameplayRecovered = AppSessionNucleus.decide(
            gameplayRequested.nextState,
            gameplayRefusal(gameplaySend.request),
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
        val toggleRequest = assertIs<AppSessionOutput.SendProfileCommand>(
            toggling.outputs.single(),
        ).request
        val propagating = AppSessionNucleus.decide(
            toggling.nextState,
            profileResult(
                toggleRequest,
                ProfileModuleResult.PreferencesChanged(PlayerPreferences()),
            ),
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
        val advanceRequest = assertIs<AppSessionOutput.SendProfileCommand>(
            advancing.outputs.single(),
        ).request
        val advanced = RebirthProgress(level = 1, highestCleared = 0)
        val startingRebirth = AppSessionNucleus.decide(
            advancing.nextState,
            profileResult(advanceRequest, ProfileModuleResult.RebirthAdvanced(advanced)),
            AppSessionContext(runBootstrap = runBootstrap(rebirthProgress = advanced)),
        ).accepted()
        val confirming = decide(
            initialState(confirmationPersistence()),
            SessionInteractionPulse.ResetConfirmed,
            AppSessionContext(persistenceStatus = confirmationPersistence()),
        ).accepted()
        val retrying = decide(
            initialState(needsAttentionPersistence()),
            SessionInteractionPulse.ResetRetryRequested,
            AppSessionContext(persistenceStatus = needsAttentionPersistence()),
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
            confirming,
            retrying,
        )
        assertEquals(11, frames.size)
        assertEquals(11, frames.map { it.nextState.pendingWorkflow!!::class }.toSet().size)

        frames.forEach { frame ->
            val participant = checkNotNull(frame.nextState.pendingWorkflow).participant
            val recovered = when (participant) {
                is PendingParticipantCommand.Profile -> AppSessionNucleus.decide(
                    frame.nextState,
                    profileRefusal(participant.request),
                )
                is PendingParticipantCommand.Gameplay -> AppSessionNucleus.decide(
                    frame.nextState,
                    gameplayRefusal(participant.request),
                )
            }.accepted()
            assertNull(recovered.nextState.pendingWorkflow)
            assertEquals(
                if (participant is PendingParticipantCommand.Profile) {
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
        val send = assertIs<AppSessionOutput.SendProfileCommand>(requested.outputs.single())

        assertFailsWith<IllegalStateException> {
            AppSessionNucleus.decide(
                requested.nextState,
                profileResult(
                    send.request.copy(
                        semanticHandle = send.request.semanticHandle.copy(sourceRevision = 999L),
                    ),
                    ProfileModuleResult.CoreShapeSelected(CoreShape.PRISM),
                ),
            )
        }
        assertFailsWith<IllegalStateException> {
            AppSessionNucleus.decide(
                requested.nextState,
                profileResult(
                    send.request,
                    ProfileModuleResult.PreferencesChanged(PlayerPreferences()),
                    issuer = ProfileResultIssuerProvenance.LOCAL_PROFILE_STATIC_BINDING,
                ).copy(
                    effectiveProtocolIdentity = ProfileEffectiveProtocolIdentity.SESSION_MUTE,
                ),
            )
        }
        assertFailsWith<IllegalStateException> {
            AppSessionNucleus.decide(
                requested.nextState,
                profileResult(
                    send.request,
                    ProfileModuleResult.PreferencesChanged(PlayerPreferences()),
                ),
            )
        }
        assertFailsWith<IllegalStateException> {
            AppSessionNucleus.decide(
                initialState(),
                profileResult(send.request, ProfileModuleResult.CoreShapeSelected(CoreShape.PRISM)),
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
        val profileSend = assertIs<AppSessionOutput.SendProfileCommand>(requested.outputs.single())
        val exact = AppSessionNucleus.decide(
            requested.nextState,
            profileResult(profileSend.request, ProfileModuleResult.RebirthAdvanced(advanced)),
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
    profileCommandPending = false,
)

private fun gameplayResult(
    request: GameplayModuleCommandRequest,
    result: GameplayModuleResult,
    causalScope: Long = 71L,
    commandDepth: Int = 2,
    resultDepth: Int = 3,
): GameplayModuleResultPulse {
    val commandSource = GameplayCommandSourceToken(
        semanticHandle = request.semanticHandle,
        targetInstance = request.targetInstance,
        causalScope = causalScope,
        causalDepth = commandDepth,
    )
    return GameplayModuleResultPulse(
        commandSource = commandSource,
        resultSource = GameplayResultSourceToken(
            semanticHandle = request.semanticHandle,
            targetInstance = request.targetInstance,
            targetRevision = GameplayRevision(5L),
            sourceOrdinal = 0,
            causalScope = causalScope,
            causalDepth = resultDepth,
        ),
        effectiveProtocolIdentity = request.command.effectiveIdentity,
        result = result,
        issuerProvenance = GameplayResultIssuerProvenance.GAMEPLAY_RUN_STATIC_BINDING,
    )
}

private fun profileResult(
    request: ProfileModuleCommandRequest,
    result: ProfileModuleResult,
    causalScope: Long = 81L,
    commandDepth: Int = 2,
    resultDepth: Int = 3,
    issuer: ProfileResultIssuerProvenance = ProfileResultIssuerProvenance.LOCAL_PROFILE_STATIC_BINDING,
): ProfileModuleResultPulse {
    val commandSource = ProfileCommandSourceToken(
        semanticHandle = request.semanticHandle,
        targetInstance = request.targetInstance,
        causalScope = causalScope,
        causalDepth = commandDepth,
    )
    return ProfileModuleResultPulse(
        commandSource = commandSource,
        resultSource = ProfileResultSourceToken(
            semanticHandle = request.semanticHandle,
            targetInstance = request.targetInstance,
            targetRevision = ProfileRevision(5L),
            sourceOrdinal = 0,
            causalScope = causalScope,
            causalDepth = resultDepth,
        ),
        effectiveProtocolIdentity = request.command.effectiveIdentity,
        result = result,
        issuerProvenance = issuer,
    )
}

private fun gameplayRefusal(
    request: GameplayModuleCommandRequest,
): GameplayCommandRejectedBeforeAcceptance {
    val identity = request.command.effectiveIdentity
    return GameplayCommandRejectedBeforeAcceptance(
        commandSource = GameplayCommandSourceToken(
            request.semanticHandle,
            request.targetInstance,
            causalScope = 91L,
            causalDepth = 1,
        ),
        effectiveProtocolIdentity = identity,
        boundaryResponse = GameplayCommandBoundaryResponse.DecisionRejected(
            GameplayRejection.AlreadyStarted,
        ),
        targetBoundaryProvenance = GameplayTargetBoundaryProvenance(
            request.targetInstance,
            identity,
        ),
    )
}

private fun profileRefusal(
    request: ProfileModuleCommandRequest,
): ProfileCommandRejectedBeforeAcceptance {
    val identity = request.command.effectiveIdentity
    return ProfileCommandRejectedBeforeAcceptance(
        commandSource = ProfileCommandSourceToken(
            request.semanticHandle,
            request.targetInstance,
            causalScope = 92L,
            causalDepth = 1,
        ),
        effectiveProtocolIdentity = identity,
        boundaryResponse = ProfileCommandBoundaryResponse.DecisionRejected(ProfileRejection.NoChange),
        targetBoundaryProvenance = ProfileTargetBoundaryProvenance(
            request.targetInstance,
            identity,
        ),
    )
}

private val GameplayModuleCommand.effectiveIdentity: GameplayEffectiveProtocolIdentity
    get() = when (this) {
        GameplayModuleCommand.StartRun -> GameplayEffectiveProtocolIdentity.SESSION_START
        GameplayModuleCommand.PauseForOverlay -> GameplayEffectiveProtocolIdentity.SESSION_PAUSE
        GameplayModuleCommand.ApplyPreferences -> GameplayEffectiveProtocolIdentity.SESSION_PREFERENCES
        GameplayModuleCommand.ExitRun -> GameplayEffectiveProtocolIdentity.SESSION_EXIT
    }

private val ProfileModuleCommand.effectiveIdentity: ProfileEffectiveProtocolIdentity
    get() = when (this) {
        is ProfileModuleCommand.SelectCoreShape -> ProfileEffectiveProtocolIdentity.SESSION_CORE_SHAPE
        ProfileModuleCommand.ToggleMute -> ProfileEffectiveProtocolIdentity.SESSION_MUTE
        ProfileModuleCommand.AdvanceRebirth -> ProfileEffectiveProtocolIdentity.SESSION_REBIRTH
        ProfileModuleCommand.ConfirmLegacyReset -> ProfileEffectiveProtocolIdentity.SESSION_RESET_CONFIRM
        ProfileModuleCommand.RetryLegacyPurge -> ProfileEffectiveProtocolIdentity.SESSION_RESET_RETRY
        is ProfileModuleCommand.ApplyGameplayProgress -> error("Not a Session mapping")
    }

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
    reset = ProfileResetStatus.NotRequired(legacyResetConfirmed = false),
    persistence = ProfilePersistenceStatus.Persisted(ProfileRevision(1L)),
)

private fun confirmationPersistence(): PersistenceStatusProjection = PersistenceStatusProjection(
    instanceId = LOCAL_PROFILE_INSTANCE_ID,
    revision = ProfileRevision(1L),
    bootstrap = ProfileBootstrapStatus.Blocked(
        ProfileBootstrapBlockReason.ResetRequired(ProfileResetReason.LegacyDataDetected),
    ),
    reset = ProfileResetStatus.ConfirmationRequired(
        ProfileResetReason.LegacyDataDetected,
        ProfileLegacyKeys.ALL,
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
