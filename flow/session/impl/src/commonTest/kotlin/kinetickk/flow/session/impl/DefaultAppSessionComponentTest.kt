// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.impl

import kinetickk.ball.gameplay.api.GameplayExitProgressResult

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.api.GameplayRefusal
import kinetickk.ball.gameplay.api.GameplayRunStarted
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfileBootstrapBlockReason
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileReadFailure
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileSettingsChanged
import kinetickk.ball.profile.api.ProfileRefusal
import kinetickk.ball.profile.api.ProfileCoreShapeSelected
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.AppSessionQuery
import kinetickk.flow.session.api.SessionAcceptance
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.api.SessionLifecycle
import kinetickk.flow.session.api.SessionRejection
import kinetickk.flow.session.api.SessionRevision
import kinetickk.flow.session.api.SessionWorkflowFailureCode
import kinetickk.flow.session.api.SessionWorkflowPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DefaultAppSessionComponentTest {
    @Test
    fun constructionSynchronizesBootstrappedPreferencesBeforeAnyPulse() {
        val expected = PlayerPreferences(
            soundEnabled = false,
            musicEnabled = false,
            masterVolume = 0.4f,
        )
        val profile = FakeProfileCapabilities().also { route ->
            route.profile = route.profile.copy(preferences = expected)
        }
        val observed = mutableListOf<PlayerPreferences>()

        createAppSessionComponent(
            profilePort = profile,
            profileSettings = profile,
            profileLoadout = profile,
            profileRebirth = profile,
            gameplayRunHost = FakeSessionGameplayHost(),
            updateAudioPreferences = observed::add,
            playMuteFeedback = {},
            playRebirthAcceptedFeedback = {},
        )

        assertEquals(listOf("persistenceStatus", "preferences"), profile.queries)
        assertEquals(listOf(expected), observed)
    }

    @Test
    fun startPublishesBeforeEnsureAndExactTargetCommandThenDrainsResult() {
        val rig = AppSessionTestRig()
        val events = mutableListOf<String>()
        val initialRouteToken = shell(rig).routeToken
        rig.gameplay.onCreateRun = { runId ->
            events += "ensure"
            assertEquals(RunId(0L), runId)
            val published = shell(rig)
            assertEquals(SessionRevision(1L), published.revision)
            assertEquals(initialRouteToken, published.routeToken)
            assertEquals(SessionWorkflowPhase.STARTING_RUN, published.pendingWorkflow)
            assertEquals(AppDestination.Home, published.base)
        }
        rig.gameplay.configureRun = { run ->
            run.onStartObserved = {
                events += "gameplay"
                assertEquals(SessionWorkflowPhase.STARTING_RUN, shell(rig).pendingWorkflow)
                assertEquals(RunId(0), run.instanceId.runId)
            }
        }

        val acceptance = assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.StartRunRequested),
        )

        assertEquals(SessionRevision(1L), acceptance.revision)
        assertEquals(listOf("ensure", "gameplay"), events)
        val completed = shell(rig)
        assertEquals(SessionRevision(2L), completed.revision)
        assertEquals(completed.revision, completed.routeRevision)
        assertEquals(AppDestination.Gameplay, completed.base)
        assertNull(completed.pendingWorkflow)
        val reply = rig.gameplay.activeFakeRun()!!.startReplies.single()
        assertFailsWith<IllegalStateException> { reply.checkAvailable() }
    }

    @Test
    fun preacceptCarrierRetainsCreatedRunAndNextStartReusesItAtOrdinalZero() {
        val rig = AppSessionTestRig()
        var rejectFirst = true
        rig.gameplay.configureRun = { run ->
            run.startHandler = { reply ->
                if (rejectFirst) {
                    rejectFirst = false
                    reply.refused(GameplayRefusal.DecisionRejected(GameplayRejection.AlreadyStarted))
                } else {
                    run.completeStart(reply)
                }
            }
        }

        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.StartRunRequested),
        )
        assertEquals(SessionWorkflowFailureCode.GAMEPLAY_COMMAND_REFUSED, shell(rig).workflowFailure)
        assertEquals(GameplayRunPhase.CREATED, rig.component.stateSnapshot().gameplayPhase)
        assertEquals(listOf(RunId(0L)), rig.gameplay.createdRunIds)

        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.StartRunRequested),
        )

        assertEquals(listOf(RunId(0L)), rig.gameplay.createdRunIds)
        val calls = rig.gameplay.activeFakeRun()!!.startReplies
        assertEquals(2, calls.size)
        calls.forEach { assertFailsWith<IllegalStateException> { it.checkAvailable() } }
        assertEquals(AppDestination.Gameplay, shell(rig).base)
        assertNull(shell(rig).workflowFailure)
    }

    @Test
    fun mutePreservesOneScopeAcrossProfileResultAndNestedGameplayCommand() {
        val rig = AppSessionTestRig()
        rig.component.accept(SessionInteractionPulse.StartRunRequested)
        val run = rig.gameplay.activeFakeRun()!!
        val events = mutableListOf<String>()
        rig.profile.muteHandler = { reply ->
            events += "profile"
            assertEquals(SessionWorkflowPhase.TOGGLING_MUTE, shell(rig).pendingWorkflow)
            rig.profile.completeMute(reply)
            assertEquals(SessionWorkflowPhase.TOGGLING_MUTE, shell(rig).pendingWorkflow)
            events += "profile-return"
        }
        run.settingsHandler = { preferences, reply ->
            events += "gameplay"
            assertEquals(rig.profile.profile.preferences, preferences)
            assertEquals(SessionWorkflowPhase.PROPAGATING_MUTE, shell(rig).pendingWorkflow)
            run.completeSettings(reply)
            assertEquals(SessionWorkflowPhase.PROPAGATING_MUTE, shell(rig).pendingWorkflow)
            events += "gameplay-return"
        }

        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.ToggleMuteRequested),
        )

        assertEquals(listOf("profile", "profile-return", "gameplay", "gameplay-return"), events)
        assertEquals(1, rig.profile.muteCalls.size)
        assertEquals(listOf(rig.profile.profile.preferences), run.settingsCalls)
        assertFailsWith<IllegalStateException> { rig.profile.muteCalls.single().checkAvailable() }
        assertFailsWith<IllegalStateException> { run.settingsReplies.single().checkAvailable() }
        assertEquals(listOf("audio", "mute"), rig.effectEvents.takeLast(2))
        assertNull(shell(rig).pendingWorkflow)
    }

    @Test
    fun nestedExitDeliveryCompletesAfterTheTypedTargetReturns() {
        val rig = AppSessionTestRig()
        rig.component.accept(SessionInteractionPulse.StartRunRequested)
        val run = rig.gameplay.activeFakeRun()!!
        run.exitHandler = { reply ->
            run.completeExit(reply, GameplayExitProgressResult.Applied)
            assertEquals(SessionWorkflowPhase.EXITING_RUN, shell(rig).pendingWorkflow)
            assertEquals(AppDestination.Gameplay, shell(rig).base)
        }
        rig.component.accept(SessionInteractionPulse.ExitRunRequested)
        assertEquals(1, run.exitReplies.size)
        assertFailsWith<IllegalStateException> { run.exitReplies.single().checkAvailable() }
        assertEquals(AppDestination.Home, shell(rig).base)
        assertNull(shell(rig).pendingWorkflow)
    }

    @Test
    fun providerReadFailureKeepsSessionUnavailableWithoutIssuingParticipantCommands() {
        val profile = FakeProfileCapabilities().apply {
            bootstrap = ProfileBootstrapStatus.Blocked(
                ProfileBootstrapBlockReason.ResourceFailure(
                    ProfileReadFailure.PROVIDER_READ_FAILED,
                ),
            )
        }
        val rig = AppSessionTestRig(profile = profile)

        val acceptance = assertIs<SessionAcceptance.Rejected>(
            rig.component.accept(SessionInteractionPulse.StartRunRequested),
        )

        assertEquals(SessionRejection.BootstrapUnavailable, acceptance.reason)
        assertEquals(SessionLifecycle.BOOTSTRAP_UNAVAILABLE, shell(rig).lifecycle)
        assertFalse(shell(rig).normalInputEnabled)
    }

    @Test
    fun forgedProfileEvidenceConstructsNoTrustedResultPulse() {
        val rig = AppSessionTestRig()
        rig.profile.shapeHandler = { shape, reply ->
            rig.profile.completeShape(shape, reply) { it.copy(shape = CoreShape.SHARD) }
        }

        assertFailsWith<IllegalStateException> {
            rig.component.accept(SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM))
        }

        assertEquals(SessionWorkflowPhase.SELECTING_CORE_SHAPE, shell(rig).pendingWorkflow)
        assertEquals(SessionRevision(1L), shell(rig).revision)

        val identityRig = AppSessionTestRig()
        identityRig.component.accept(SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM))
        val previousCall = identityRig.profile.shapeReplies.single()
        identityRig.profile.shapeHandler = { shape, _ ->
            previousCall.accepted(ProfileCoreShapeSelected(identityRig.profile.revision, shape))
        }
        assertFailsWith<IllegalStateException> {
            identityRig.component.accept(
                SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.SHARD),
            )
        }
        assertEquals(
            SessionWorkflowPhase.SELECTING_CORE_SHAPE,
            shell(identityRig).pendingWorkflow,
        )
    }

    @Test
    fun forgedGameplayOutcomeConstructsNoTrustedResultPulse() {
        val rig = AppSessionTestRig()
        rig.gameplay.configureRun = { run ->
            run.startHandler = { reply ->
                reply.accepted(GameplayRunStarted(RunId(99), run.revision))
            }
        }

        assertFailsWith<IllegalStateException> {
            rig.component.accept(SessionInteractionPulse.StartRunRequested)
        }

        assertEquals(SessionWorkflowPhase.STARTING_RUN, shell(rig).pendingWorkflow)
        assertEquals(AppDestination.Home, shell(rig).base)
    }

    @Test
    fun acceptedWithoutResultAndResultPlusRejectionAreFaults() {
        val missing = AppSessionTestRig()
        missing.profile.shapeHandler = { _, _ -> }
        assertFailsWith<IllegalStateException> {
            missing.component.accept(
                SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM),
            )
        }
        assertEquals(SessionWorkflowPhase.SELECTING_CORE_SHAPE, shell(missing).pendingWorkflow)

        val contradiction = AppSessionTestRig()
        contradiction.profile.shapeHandler = { shape, reply ->
            contradiction.profile.completeShape(shape, reply)
            reply.refused(ProfileRefusal.DecisionRejected(ProfileRejection.CoreShapeLocked))
        }
        assertFailsWith<IllegalStateException> {
            contradiction.component.accept(
                SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM),
            )
        }
        assertNull(shell(contradiction).pendingWorkflow)
        assertNull(shell(contradiction).workflowFailure)
        assertEquals(SessionRevision(2), shell(contradiction).revision)
    }

    @Test
    fun profileThrowAfterValidatedResultStillDrainsThenRethrows() {
        val rig = AppSessionTestRig()
        rig.profile.shapeHandler = { shape, reply ->
            rig.profile.completeShape(shape, reply)
            error("profile-after-result")
        }

        val failure = assertFailsWith<IllegalStateException> {
            rig.component.accept(SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM))
        }

        assertEquals("profile-after-result", failure.message)
        assertNull(shell(rig).pendingWorkflow)
        assertNull(shell(rig).workflowFailure)
        assertEquals(SessionRevision(2L), shell(rig).revision)
    }

    @Test
    fun gameplayThrowAfterValidatedResultStillDrainsThenRethrows() {
        val rig = AppSessionTestRig()
        rig.gameplay.configureRun = { run ->
            run.startHandler = { reply ->
                run.completeStart(reply)
                error("gameplay-after-result")
            }
        }

        val failure = assertFailsWith<IllegalStateException> {
            rig.component.accept(SessionInteractionPulse.StartRunRequested)
        }

        assertEquals("gameplay-after-result", failure.message)
        assertEquals(AppDestination.Gameplay, shell(rig).base)
        assertNull(shell(rig).pendingWorkflow)
        assertEquals(SessionRevision(2L), shell(rig).revision)
    }

    @Test
    fun exactProfileAndGameplayPreacceptCarriersRecoverPendingWorkflow() {
        val profileRig = AppSessionTestRig()
        profileRig.profile.shapeHandler = { _, reply ->
            reply.refused(ProfileRefusal.DecisionRejected(ProfileRejection.CoreShapeLocked))
        }
        profileRig.component.accept(
            SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM),
        )
        assertNull(shell(profileRig).pendingWorkflow)
        assertEquals(SessionWorkflowFailureCode.PROFILE_COMMAND_REFUSED, shell(profileRig).workflowFailure)

        val gameplayRig = AppSessionTestRig()
        gameplayRig.gameplay.configureRun = { run ->
            run.startHandler = { reply ->
                reply.refused(GameplayRefusal.DecisionRejected(GameplayRejection.AlreadyStarted))
            }
        }
        gameplayRig.component.accept(SessionInteractionPulse.StartRunRequested)
        assertNull(shell(gameplayRig).pendingWorkflow)
        assertEquals(
            SessionWorkflowFailureCode.GAMEPLAY_COMMAND_REFUSED,
            shell(gameplayRig).workflowFailure,
        )
    }

    @Test
    fun validationAdmissionAndDecisionRefusalsAllUseTheOneCarrierBranch() {
        val profileResponses = listOf(
            ProfileRefusal.Busy,
            ProfileRefusal.RevisionCapacityExhausted,
            ProfileRefusal.DecisionRejected(ProfileRejection.CoreShapeLocked),
        )
        profileResponses.forEach { response ->
            val rig = AppSessionTestRig()
            rig.profile.shapeHandler = { _, reply -> reply.refused(response) }
            rig.component.accept(
                SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM),
            )
            assertNull(shell(rig).pendingWorkflow)
            assertEquals(
                SessionWorkflowFailureCode.PROFILE_COMMAND_REFUSED,
                shell(rig).workflowFailure,
            )
        }

        val gameplayResponses = listOf(
            GameplayRefusal.Busy,
            GameplayRefusal.RevisionCapacityExhausted,
            GameplayRefusal.DecisionRejected(GameplayRejection.AlreadyStarted),
        )
        gameplayResponses.forEach { response ->
            val rig = AppSessionTestRig()
            rig.gameplay.configureRun = { run ->
                run.startHandler = { reply -> reply.refused(response) }
            }
            rig.component.accept(SessionInteractionPulse.StartRunRequested)
            assertNull(shell(rig).pendingWorkflow)
            assertEquals(
                SessionWorkflowFailureCode.GAMEPLAY_COMMAND_REFUSED,
                shell(rig).workflowFailure,
            )
        }
    }

    @Test
    fun wrongGameplayReadIdentityFaultsBeforeDecision() {
        val rig = AppSessionTestRig()
        rig.component.accept(SessionInteractionPulse.StartRunRequested)
        val before = shell(rig)
        val run = rig.gameplay.activeFakeRun()!!
        run.statusInstanceId = GameplayInstanceId(RunId(99L))

        assertFailsWith<IllegalStateException> {
            rig.component.accept(
                SessionInteractionPulse.OpenOverlay(AppDestination.Settings),
            )
        }
        assertEquals(before, shell(rig))
    }

    @Test
    fun outputFaultDoesNotRollbackPublishedFrameAndLaterOutputStillRuns() {
        val profile = FakeProfileCapabilities()
        val gameplay = FakeSessionGameplayHost()
        var muteFeedback = 0
        var failAudioUpdate = false
        val component = createAppSessionComponent(
            profilePort = profile,
            profileSettings = profile,
            profileLoadout = profile,
            profileRebirth = profile,
            gameplayRunHost = gameplay,
            updateAudioPreferences = {
                if (failAudioUpdate) error("audio-fault")
            },
            playMuteFeedback = { muteFeedback += 1 },
            playRebirthAcceptedFeedback = {},
        ) as DefaultAppSessionComponent
        failAudioUpdate = true

        val failure = assertFailsWith<IllegalStateException> {
            component.accept(SessionInteractionPulse.ToggleMuteRequested)
        }

        assertEquals("audio-fault", failure.message)
        assertEquals(1, muteFeedback)
        assertNull(component.query(AppSessionQuery.GetShell).pendingWorkflow)
        assertEquals(SessionRevision(2L), component.query(AppSessionQuery.GetShell).revision)
    }

    @Test
    fun muteAcceptedProfileAndGameplayResultsFinishBeforeTheFirstTargetFaultEscapes() {
        val rig = AppSessionTestRig()
        rig.component.accept(SessionInteractionPulse.StartRunRequested)
        val run = rig.gameplay.activeFakeRun()!!
        val first = IllegalStateException("profile after accepted settings")
        val later = IllegalStateException("gameplay after accepted settings")
        rig.profile.muteHandler = { reply ->
            rig.profile.completeMute(reply)
            throw first
        }
        run.settingsHandler = { _, reply ->
            run.completeSettings(reply)
            throw later
        }

        val thrown = assertFailsWith<IllegalStateException> {
            rig.component.accept(SessionInteractionPulse.ToggleMuteRequested)
        }

        assertSame(first, thrown)
        assertEquals(SessionRevision(5), shell(rig).revision)
        assertNull(shell(rig).pendingWorkflow)
        assertNull(shell(rig).workflowFailure)
        assertFalse(run.settingsCalls.single().soundEnabled)
        assertEquals(run.settingsCalls.single(), rig.audioPreferences.last())
        assertEquals(1, rig.muteFeedbackCount)
    }

    @Test
    fun muteTypedParticipantRefusalsFinishWithoutRewritingAcceptedProfileSettings() {
        val profileRig = AppSessionTestRig()
        profileRig.profile.muteHandler = { it.refused(ProfileRefusal.Busy) }
        profileRig.component.accept(SessionInteractionPulse.ToggleMuteRequested)
        assertNull(shell(profileRig).pendingWorkflow)
        assertEquals(SessionWorkflowFailureCode.PROFILE_COMMAND_REFUSED, shell(profileRig).workflowFailure)
        assertTrue(profileRig.profile.profile.preferences.soundEnabled)
        assertEquals(1, profileRig.muteFeedbackCount)

        val gameplayRig = AppSessionTestRig()
        gameplayRig.component.accept(SessionInteractionPulse.StartRunRequested)
        val run = gameplayRig.gameplay.activeFakeRun()!!
        val beforeRevision = run.revision
        run.settingsHandler = { _, reply -> reply.refused(GameplayRefusal.Busy) }
        gameplayRig.component.accept(SessionInteractionPulse.ToggleMuteRequested)
        assertNull(shell(gameplayRig).pendingWorkflow)
        assertEquals(SessionWorkflowFailureCode.GAMEPLAY_COMMAND_REFUSED, shell(gameplayRig).workflowFailure)
        assertEquals(beforeRevision, run.revision)
        assertFalse(gameplayRig.profile.profile.preferences.soundEnabled)
        assertEquals(gameplayRig.profile.profile.preferences, gameplayRig.audioPreferences.last())
    }

    @Test
    fun duplicateAndLateMuteRepliesCannotReplaceTheAcceptedResultOrFinishAnotherCall() {
        val rig = AppSessionTestRig()
        rig.component.accept(SessionInteractionPulse.StartRunRequested)
        val run = rig.gameplay.activeFakeRun()!!
        rig.profile.muteHandler = { reply ->
            rig.profile.completeMute(reply)
            reply.accepted(ProfileSettingsChanged(rig.profile.revision, PlayerPreferences()))
        }
        assertFailsWith<IllegalStateException> {
            rig.component.accept(SessionInteractionPulse.ToggleMuteRequested)
        }
        assertFalse(run.settingsCalls.single().soundEnabled)
        assertNull(shell(rig).pendingWorkflow)
        assertEquals(SessionRevision(5), shell(rig).revision)
        val oldReply = rig.profile.muteCalls.single()
        val beforeLate = shell(rig)
        assertFailsWith<IllegalStateException> {
            oldReply.accepted(ProfileSettingsChanged(rig.profile.revision, PlayerPreferences()))
        }
        assertEquals(beforeLate, shell(rig))

        rig.profile.muteHandler = { current ->
            assertFailsWith<IllegalStateException> { oldReply.refused(ProfileRefusal.Busy) }
            rig.profile.completeMute(current)
        }
        rig.component.accept(SessionInteractionPulse.ToggleMuteRequested)
        assertTrue(run.settingsCalls.last().soundEnabled)
        assertEquals(2, run.settingsCalls.size)
        assertNull(shell(rig).pendingWorkflow)
    }

    @Test
    fun settingsResultForAnotherRunIsRetainedAndCannotCompleteTheCurrentWorkflow() {
        val rig = AppSessionTestRig()
        rig.component.accept(SessionInteractionPulse.StartRunRequested)
        val run = rig.gameplay.activeFakeRun()!!
        run.settingsHandler = { _, reply ->
            reply.accepted(kinetickk.ball.gameplay.api.GameplaySettingsApplied(RunId(99), run.revision))
        }

        assertFailsWith<IllegalStateException> {
            rig.component.accept(SessionInteractionPulse.ToggleMuteRequested)
        }

        assertEquals(SessionWorkflowPhase.PROPAGATING_MUTE, shell(rig).pendingWorkflow)
        assertNull(shell(rig).workflowFailure)
        val retained = rig.component.stateSnapshot()
        assertFailsWith<IllegalStateException> {
            rig.component.accept(SessionInteractionPulse.ToggleMuteRequested)
        }
        assertEquals(retained, rig.component.stateSnapshot())
        assertEquals(1, rig.profile.muteCalls.size)
    }

    @Test
    fun muteWithoutAReplyFaultsWithoutSynthesizingParticipantRefusal() {
        val rig = AppSessionTestRig()
        rig.profile.muteHandler = {}

        assertFailsWith<IllegalStateException> {
            rig.component.accept(SessionInteractionPulse.ToggleMuteRequested)
        }

        assertEquals(SessionWorkflowPhase.TOGGLING_MUTE, shell(rig).pendingWorkflow)
        assertNull(shell(rig).workflowFailure)
        assertTrue(rig.profile.profile.preferences.soundEnabled)
        assertEquals(0, rig.muteFeedbackCount)
        assertEquals(
            SessionRejection.ParticipantCommandPending,
            assertIs<SessionAcceptance.Rejected>(
                rig.component.accept(SessionInteractionPulse.ToggleMuteRequested),
            ).reason,
        )
    }

    @Test
    fun pauseCompletesItsOverlayBeforeAValidatedTargetFaultEscapes() {
        val rig = AppSessionTestRig()
        rig.component.accept(SessionInteractionPulse.StartRunRequested)
        val run = rig.gameplay.activeFakeRun()!!
        val fault = IllegalStateException("pause-after-result")
        run.pauseHandler = { reply ->
            assertEquals(SessionWorkflowPhase.PAUSING_FOR_OVERLAY, shell(rig).pendingWorkflow)
            run.completePause(reply)
            assertNull(shell(rig).overlay)
            throw fault
        }

        val thrown = assertFailsWith<IllegalStateException> {
            rig.component.accept(SessionInteractionPulse.OpenOverlay(AppDestination.Settings))
        }

        assertSame(fault, thrown)
        assertEquals(AppDestination.Settings, shell(rig).overlay)
        assertEquals(GameplayRunPhase.PAUSED, rig.component.stateSnapshot().gameplayPhase)
        assertNull(shell(rig).pendingWorkflow)
        val completed = shell(rig)
        assertFailsWith<IllegalStateException> {
            run.pauseReplies.single().refused(GameplayRefusal.Busy)
        }
        assertEquals(completed, shell(rig))
    }

    @Test
    fun pauseRefusalKeepsTheRunningDestinationAndClearsItsWorkflow() {
        val rig = AppSessionTestRig()
        rig.component.accept(SessionInteractionPulse.StartRunRequested)
        val run = rig.gameplay.activeFakeRun()!!
        val revision = run.revision
        run.pauseHandler = { reply -> reply.refused(GameplayRefusal.Busy) }

        rig.component.accept(SessionInteractionPulse.OpenOverlay(AppDestination.Settings))

        assertEquals(revision, run.revision)
        assertEquals(AppDestination.Gameplay, shell(rig).base)
        assertNull(shell(rig).overlay)
        assertNull(shell(rig).pendingWorkflow)
        assertEquals(SessionWorkflowFailureCode.GAMEPLAY_COMMAND_REFUSED, shell(rig).workflowFailure)
    }

    @Test
    fun rebirthFinishesItsNewRunBeforeAProfileFaultEscapes() {
        val rig = AppSessionTestRig()
        rig.component.accept(SessionInteractionPulse.OpenOverlay(AppDestination.Rebirth))
        rig.component.accept(SessionInteractionPulse.RebirthRequested)
        val fault = IllegalStateException("rebirth-after-result")
        rig.profile.rebirthHandler = { reply ->
            assertEquals(SessionWorkflowPhase.ADVANCING_REBIRTH, shell(rig).pendingWorkflow)
            rig.profile.completeRebirth(reply)
            assertEquals(SessionWorkflowPhase.ADVANCING_REBIRTH, shell(rig).pendingWorkflow)
            throw fault
        }

        val thrown = assertFailsWith<IllegalStateException> {
            rig.component.accept(SessionInteractionPulse.RebirthRequested)
        }

        assertSame(fault, thrown)
        assertEquals(AppDestination.Gameplay, shell(rig).base)
        assertNull(shell(rig).overlay)
        assertNull(shell(rig).pendingWorkflow)
        assertEquals(listOf(RunId(0)), rig.gameplay.createdRunIds)
        assertEquals(1, rig.profile.profile.rebirthProgress.level)
        assertEquals(1, rig.rebirthAcceptedFeedbackCount)
    }

    @Test
    fun rebirthRefusalLeavesTheConfirmationDisarmedWithoutCreatingARun() {
        val rig = AppSessionTestRig()
        rig.component.accept(SessionInteractionPulse.OpenOverlay(AppDestination.Rebirth))
        rig.component.accept(SessionInteractionPulse.RebirthRequested)
        rig.profile.rebirthHandler = { reply -> reply.refused(ProfileRefusal.Busy) }

        rig.component.accept(SessionInteractionPulse.RebirthRequested)

        assertEquals(AppDestination.Home, shell(rig).base)
        assertEquals(AppDestination.Rebirth, shell(rig).overlay)
        assertFalse(shell(rig).rebirthConfirmationArmed)
        assertNull(shell(rig).pendingWorkflow)
        assertEquals(SessionWorkflowFailureCode.PROFILE_COMMAND_REFUSED, shell(rig).workflowFailure)
        assertTrue(rig.gameplay.createdRunIds.isEmpty())
        assertEquals(0, rig.rebirthAcceptedFeedbackCount)
    }

    @Test
    fun deployedQueueDepthFanoutAndCapacityAcceptNRejectNPlusOne() {
        val completions = sessionCompletionDeque<Int>()
        repeat(8) { value -> assertTrue(completions.tryAddLast(value)) }
        assertFalse(completions.tryAddLast(8))
        assertEquals((0 until 8).toList(), List(8) { completions.removeFirstOrNull() })

        requireSessionOutputFanoutBounds(participantCount = 1, ensureCount = 1)
        assertFailsWith<IllegalStateException> {
            requireSessionOutputFanoutBounds(participantCount = 2, ensureCount = 1)
        }
        assertFailsWith<IllegalStateException> {
            requireSessionOutputFanoutBounds(participantCount = 1, ensureCount = 2)
        }
        requireSessionCompletionCapacity(remainingCapacity = 1, requiredCompletions = 1)
        assertFailsWith<IllegalStateException> {
            requireSessionCompletionCapacity(remainingCapacity = 0, requiredCompletions = 1)
        }
    }
}

private fun shell(rig: AppSessionTestRig) = rig.component.query(AppSessionQuery.GetShell)
