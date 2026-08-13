// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.impl

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.api.GameplayCommandBoundaryResponse
import kinetickk.ball.gameplay.api.GameplayCommandIngressResult
import kinetickk.ball.gameplay.api.GameplayCommandAdmissionFailureReason
import kinetickk.ball.gameplay.api.GameplayCommandValidationFailureReason
import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.gameplay.api.GameplayModuleCommand
import kinetickk.ball.gameplay.api.GameplayModuleResult
import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfileBootstrapBlockReason
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileCommandBoundaryResponse
import kinetickk.ball.profile.api.ProfileCommandIngressResult
import kinetickk.ball.profile.api.ProfileCommandAdmissionFailureReason
import kinetickk.ball.profile.api.ProfileCommandValidationFailureReason
import kinetickk.ball.profile.api.ProfileModuleCommand
import kinetickk.ball.profile.api.ProfileModuleResult
import kinetickk.ball.profile.api.ProfileReadFailure
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileResultSourceToken
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
import kotlin.test.assertTrue

class DefaultAppSessionComponentTest {
    @Test
    fun constructionSynchronizesBootstrappedPreferencesBeforeAnyPulse() {
        val expected = PlayerPreferences(
            soundEnabled = false,
            musicEnabled = false,
            masterVolume = 0.4f,
        )
        val profile = FakeSessionProfileRoute().also { route ->
            route.profile = route.profile.copy(preferences = expected)
        }
        val observed = mutableListOf<PlayerPreferences>()

        createAppSessionComponent(
            profileRoute = profile,
            gameplaySessionHost = FakeSessionGameplayHost(),
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
            run.onCommandObserved = { call ->
                events += "gameplay"
                assertEquals(SessionWorkflowPhase.STARTING_RUN, shell(rig).pendingWorkflow)
                assertEquals(GameplayModuleCommand.StartRun, call.request.command)
                assertEquals(1, call.request.sourceOrdinal)
                assertEquals(1L, call.causalScope)
                assertEquals(0, call.causalDepth)
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
        val delivery = rig.gameplay.activeFakeRun()!!.deliveries.single()
        assertEquals(1, delivery.resultSource.causalDepth)
        assertEquals(0, delivery.resultSource.sourceOrdinal)
        assertEquals(delivery.commandSource.causalScope, delivery.resultSource.causalScope)
    }

    @Test
    fun preacceptCarrierRetainsCreatedRunAndNextStartReusesItAtOrdinalZero() {
        val rig = AppSessionTestRig()
        var rejectFirst = true
        rig.gameplay.configureRun = { run ->
            run.commandHandler = { call ->
                if (rejectFirst) {
                    rejectFirst = false
                    run.refuse(
                        call,
                        GameplayCommandBoundaryResponse.DecisionRejected(
                            GameplayRejection.AlreadyStarted,
                        ),
                    )
                } else {
                    run.complete(call, GameplayModuleResult.RunStarted)
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
        val calls = rig.gameplay.activeFakeRun()!!.commands
        assertEquals(listOf(1, 0), calls.map { it.request.sourceOrdinal })
        assertEquals(listOf(1L, 2L), calls.map { it.causalScope })
        assertEquals(AppDestination.Gameplay, shell(rig).base)
        assertNull(shell(rig).workflowFailure)
    }

    @Test
    fun mutePreservesOneScopeAcrossProfileResultAndNestedGameplayCommand() {
        val rig = AppSessionTestRig()
        rig.component.accept(SessionInteractionPulse.StartRunRequested)
        rig.profile.commands.clear()
        rig.profile.deliveries.clear()
        rig.gameplay.activeFakeRun()!!.commands.clear()
        rig.gameplay.activeFakeRun()!!.deliveries.clear()

        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.ToggleMuteRequested),
        )

        val profileCall = rig.profile.commands.single()
        val profileDelivery = rig.profile.deliveries.single()
        val gameplayCall = rig.gameplay.activeFakeRun()!!.commands.single()
        val gameplayDelivery = rig.gameplay.activeFakeRun()!!.deliveries.single()
        assertEquals(0, profileCall.causalDepth)
        assertEquals(1, profileDelivery.resultSource.causalDepth)
        assertEquals(2, gameplayCall.causalDepth)
        assertEquals(3, gameplayDelivery.resultSource.causalDepth)
        assertEquals(
            listOf(
                profileCall.causalScope,
                profileDelivery.resultSource.causalScope,
                gameplayCall.causalScope,
                gameplayDelivery.resultSource.causalScope,
            ).distinct(),
            listOf(profileCall.causalScope),
        )
        assertEquals(listOf("audio", "mute"), rig.effectEvents.takeLast(2))
        assertNull(shell(rig).pendingWorkflow)
    }

    @Test
    fun nestedExitDeliveryKeepsRootScopeAndExactThreeLevelTargetDepth() {
        val rig = AppSessionTestRig()
        rig.component.accept(SessionInteractionPulse.StartRunRequested)
        val run = rig.gameplay.activeFakeRun()!!
        run.commands.clear()
        run.deliveries.clear()
        run.commandHandler = { call ->
            run.complete(
                call,
                GameplayModuleResult.RunExited(
                    kinetickk.ball.gameplay.api.GameplayExitProgressResult.Applied,
                ),
                nestedExit = true,
            )
        }

        rig.component.accept(SessionInteractionPulse.ExitRunRequested)

        val call = run.commands.single()
        val delivery = run.deliveries.single()
        assertEquals(0, call.causalDepth)
        assertEquals(3, delivery.resultSource.causalDepth)
        assertEquals(call.causalScope, delivery.resultSource.causalScope)
        assertEquals(AppDestination.Home, shell(rig).base)
        assertNull(shell(rig).pendingWorkflow)
    }

    @Test
    fun providerReadFailureKeepsSessionUnavailableWithoutIssuingParticipantCommands() {
        val profile = FakeSessionProfileRoute().apply {
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
        assertTrue(profile.commands.isEmpty())
    }

    @Test
    fun forgedProfileEvidenceConstructsNoTrustedResultPulse() {
        val rig = AppSessionTestRig()
        rig.profile.commandHandler = { call ->
            rig.profile.complete(
                call,
                ProfileModuleResult.CoreShapeSelected(CoreShape.PRISM),
                deliveryTransform = { delivery ->
                    delivery.copy(
                        resultSource = ProfileResultSourceToken(
                            semanticHandle = delivery.resultSource.semanticHandle,
                            targetInstance = delivery.resultSource.targetInstance,
                            targetRevision = delivery.resultSource.targetRevision,
                            sourceOrdinal = delivery.resultSource.sourceOrdinal + 1,
                            causalScope = delivery.resultSource.causalScope,
                            causalDepth = delivery.resultSource.causalDepth,
                        ),
                    )
                },
            )
        }

        assertFailsWith<IllegalStateException> {
            rig.component.accept(SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM))
        }

        assertEquals(SessionWorkflowPhase.SELECTING_CORE_SHAPE, shell(rig).pendingWorkflow)
        assertEquals(SessionRevision(1L), shell(rig).revision)

        val identityRig = AppSessionTestRig()
        identityRig.profile.commandHandler = { call ->
            identityRig.profile.complete(
                call,
                ProfileModuleResult.CoreShapeSelected(CoreShape.PRISM),
                deliveryTransform = { delivery ->
                    delivery.copy(
                        effectiveProtocolIdentity =
                            kinetickk.ball.profile.api.ProfileEffectiveProtocolIdentity.SESSION_MUTE,
                    )
                },
            )
        }
        assertFailsWith<IllegalStateException> {
            identityRig.component.accept(
                SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM),
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
            run.commandHandler = { call ->
                run.complete(
                    call,
                    GameplayModuleResult.RunStarted,
                    deliveryTransform = { delivery ->
                        delivery.copy(result = GameplayModuleResult.OverlayPaused)
                    },
                )
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
        missing.profile.commandHandler = {
            ProfileCommandIngressResult.Accepted(missing.profile.instanceId, missing.profile.revision)
        }
        assertFailsWith<IllegalStateException> {
            missing.component.accept(
                SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM),
            )
        }
        assertEquals(SessionWorkflowPhase.SELECTING_CORE_SHAPE, shell(missing).pendingWorkflow)

        val contradiction = AppSessionTestRig()
        contradiction.profile.commandHandler = { call ->
            contradiction.profile.complete(
                call,
                ProfileModuleResult.CoreShapeSelected(CoreShape.PRISM),
            )
            contradiction.profile.refuse(
                call,
                ProfileCommandBoundaryResponse.DecisionRejected(ProfileRejection.CoreShapeLocked),
            )
        }
        assertFailsWith<IllegalStateException> {
            contradiction.component.accept(
                SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM),
            )
        }
        assertEquals(SessionWorkflowPhase.SELECTING_CORE_SHAPE, shell(contradiction).pendingWorkflow)
    }

    @Test
    fun profileThrowAfterValidatedResultStillDrainsThenRethrows() {
        val rig = AppSessionTestRig()
        rig.profile.commandHandler = { call ->
            rig.profile.complete(
                call,
                ProfileModuleResult.CoreShapeSelected(CoreShape.PRISM),
            )
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
            run.commandHandler = { call ->
                run.complete(call, GameplayModuleResult.RunStarted)
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
        profileRig.profile.commandHandler = { call ->
            profileRig.profile.refuse(
                call,
                ProfileCommandBoundaryResponse.DecisionRejected(ProfileRejection.CoreShapeLocked),
            )
        }
        profileRig.component.accept(
            SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM),
        )
        assertNull(shell(profileRig).pendingWorkflow)
        assertEquals(SessionWorkflowFailureCode.PROFILE_COMMAND_REFUSED, shell(profileRig).workflowFailure)

        val gameplayRig = AppSessionTestRig()
        gameplayRig.gameplay.configureRun = { run ->
            run.commandHandler = { call ->
                run.refuse(
                    call,
                    GameplayCommandBoundaryResponse.DecisionRejected(GameplayRejection.AlreadyStarted),
                )
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
            ProfileCommandBoundaryResponse.ValidationFailure(
                ProfileCommandValidationFailureReason.WRONG_TARGET,
            ),
            ProfileCommandBoundaryResponse.AdmissionFailure(
                ProfileCommandAdmissionFailureReason.CompletionCapacityExhausted,
            ),
            ProfileCommandBoundaryResponse.DecisionRejected(ProfileRejection.CoreShapeLocked),
        )
        profileResponses.forEach { response ->
            val rig = AppSessionTestRig()
            rig.profile.commandHandler = { call -> rig.profile.refuse(call, response) }
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
            GameplayCommandBoundaryResponse.ValidationFailure(
                GameplayCommandValidationFailureReason.WrongTarget,
            ),
            GameplayCommandBoundaryResponse.AdmissionFailure(
                GameplayCommandAdmissionFailureReason.CompletionCapacityExhausted,
            ),
            GameplayCommandBoundaryResponse.DecisionRejected(GameplayRejection.AlreadyStarted),
        )
        gameplayResponses.forEach { response ->
            val rig = AppSessionTestRig()
            rig.gameplay.configureRun = { run ->
                run.commandHandler = { call -> run.refuse(call, response) }
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
        val profile = FakeSessionProfileRoute()
        val gameplay = FakeSessionGameplayHost()
        var muteFeedback = 0
        var failAudioUpdate = false
        val component = createAppSessionComponent(
            profileRoute = profile,
            gameplaySessionHost = gameplay,
            updateAudioPreferences = {
                if (failAudioUpdate) error("audio-fault")
            },
            playMuteFeedback = { muteFeedback += 1 },
            playRebirthAcceptedFeedback = {},
        ) as DefaultAppSessionComponent
        profile.resultSink = component::receiveProfileModuleResult
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
    fun deployedQueueDepthFanoutAndCapacityAcceptNRejectNPlusOne() {
        val completions = sessionCompletionDeque<Int>()
        repeat(8) { value -> assertTrue(completions.tryAddLast(value)) }
        assertFalse(completions.tryAddLast(8))
        assertEquals((0 until 8).toList(), List(8) { completions.removeFirstOrNull() })

        repeat(8, ::requireSessionCausalDepth)
        assertFailsWith<IllegalStateException> { requireSessionCausalDepth(8) }
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
