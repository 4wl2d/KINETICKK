// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.impl

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayCommandOutcome
import kinetickk.ball.gameplay.api.GameplayCommandRef
import kinetickk.ball.gameplay.api.GameplayCommandResult
import kinetickk.ball.gameplay.api.GameplayCommandSource
import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplaySessionPulse
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileBootstrapBlockReason
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileCommandOutcome
import kinetickk.ball.profile.api.ProfileCommandSource
import kinetickk.ball.profile.api.ProfileLegacyKeys
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileResetReason
import kinetickk.ball.profile.api.ProfileResetStatus
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.AppSessionQuery
import kinetickk.flow.session.api.SessionAcceptance
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.api.SessionRevision
import kinetickk.flow.session.api.SessionWorkflowFailure
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
    fun startPublishesBeforeEnsureAndCommandThenDrainsTheExactResult() {
        val content = sessionGameplayContentFixture()
        val rig = AppSessionTestRig(gameplayContent = content)
        val events = mutableListOf<String>()
        rig.profile.queries.clear()
        rig.gameplay.onCreateRun = { runId ->
            events += "ensure"
            assertEquals(RunId(0L), runId)
            val shell = rig.component.query(AppSessionQuery.GetShell)
            assertEquals(SessionRevision(1L), shell.revision)
            assertEquals(SessionWorkflowPhase.STARTING_RUN, shell.pendingWorkflow)
            assertEquals(runId, shell.activeRunId)
            assertEquals(GameplayRunPhase.CREATED, shell.gameplayPhase)
            assertEquals(AppDestination.Home, shell.base)
        }
        rig.gameplay.configureRun = { run ->
            run.onCommandObserved = { command ->
                events += "command"
                val shell = rig.component.query(AppSessionQuery.GetShell)
                assertEquals(SessionWorkflowPhase.STARTING_RUN, shell.pendingWorkflow)
                assertEquals(SessionRevision(1L), shell.revision)
                assertEquals(command.ref.targetInstance, run.instanceId)
                assertEquals(1L, command.ref.sourceRevision)
                assertEquals(0, command.ref.ordinal)
                val start = assertIs<GameplaySessionPulse.StartRun>(command.pulse)
                assertSame(content, start.configuration.content)
            }
        }

        val acceptance = assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.StartRunRequested),
        )

        assertEquals(SessionRevision(1L), acceptance.revision)
        assertEquals(listOf("ensure", "command"), events)
        assertEquals(listOf("runBootstrap"), rig.profile.queries)
        val shell = rig.component.query(AppSessionQuery.GetShell)
        assertEquals(SessionRevision(2L), shell.revision)
        assertEquals(AppDestination.Gameplay, shell.base)
        assertEquals(RunId(0L), shell.activeRunId)
        assertEquals(GameplayRunPhase.RUNNING, shell.gameplayPhase)
        assertNull(shell.pendingWorkflow)
        assertNull(shell.workflowFailure)
    }

    @Test
    fun gameplayPreacceptRejectionRetainsCreatedRunForExactStartRetry() {
        val rig = AppSessionTestRig()
        var attempts = 0
        rig.profile.queries.clear()
        rig.gameplay.configureRun = { run ->
            run.commandHandler = { command ->
                attempts += 1
                if (attempts == 1) {
                    GameplayAcceptance.Rejected(
                        run.instanceId,
                        run.revision,
                        GameplayRejection.AlreadyStarted,
                    )
                } else {
                    run.phase = GameplayRunPhase.RUNNING
                    run.complete(command, GameplayCommandOutcome.RunStarted)
                }
            }
        }

        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.StartRunRequested),
        )

        val failed = rig.component.query(AppSessionQuery.GetShell)
        assertEquals(AppDestination.Home, failed.base)
        assertEquals(RunId(0L), failed.activeRunId)
        assertEquals(GameplayRunPhase.CREATED, failed.gameplayPhase)
        assertNull(failed.pendingWorkflow)
        assertIs<SessionWorkflowFailure.GameplayCommandRejected>(failed.workflowFailure)
        assertEquals(1, rig.gameplay.createdRunIds.size)

        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.StartRunRequested),
        )

        val retried = rig.component.query(AppSessionQuery.GetShell)
        assertEquals(AppDestination.Gameplay, retried.base)
        assertEquals(GameplayRunPhase.RUNNING, retried.gameplayPhase)
        assertNull(retried.pendingWorkflow)
        assertNull(retried.workflowFailure)
        assertEquals(1, rig.gameplay.createdRunIds.size)
        val commands = rig.gameplay.activeFakeRun()!!.commands
        assertEquals(2, commands.size)
        assertEquals(0, commands[0].ref.ordinal)
        assertEquals(1, commands[1].ref.ordinal)
        assertEquals(listOf("runBootstrap", "runBootstrap"), rig.profile.queries)
    }

    @Test
    fun rejectedRestartReturnsHomeAndReusesItsCreatedRunOnStart() {
        val rig = AppSessionTestRig()
        var restartAttempts = 0
        rig.gameplay.configureRun = { run ->
            if (run.instanceId.runId == RunId(1L)) {
                run.commandHandler = { command ->
                    restartAttempts += 1
                    if (restartAttempts == 1) {
                        GameplayAcceptance.Rejected(
                            run.instanceId,
                            run.revision,
                            GameplayRejection.AlreadyStarted,
                        )
                    } else {
                        run.phase = GameplayRunPhase.RUNNING
                        run.complete(command, GameplayCommandOutcome.RunStarted)
                    }
                }
            }
        }
        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.StartRunRequested),
        )
        rig.gameplay.activeFakeRun()!!.phase = GameplayRunPhase.GAME_OVER

        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.RestartRunRequested),
        )

        val rejected = shell(rig)
        assertEquals(AppDestination.Home, rejected.base)
        assertNull(rejected.overlay)
        assertEquals(RunId(1L), rejected.activeRunId)
        assertEquals(GameplayRunPhase.CREATED, rejected.gameplayPhase)
        assertNull(rejected.pendingWorkflow)
        assertIs<SessionWorkflowFailure.GameplayCommandRejected>(rejected.workflowFailure)
        assertEquals(listOf(RunId(0L), RunId(1L)), rig.gameplay.createdRunIds)

        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.StartRunRequested),
        )

        assertEquals(listOf(RunId(0L), RunId(1L)), rig.gameplay.createdRunIds)
        assertEquals(2, restartAttempts)
        assertEquals(AppDestination.Gameplay, shell(rig).base)
        assertEquals(GameplayRunPhase.RUNNING, shell(rig).gameplayPhase)
        assertNull(shell(rig).workflowFailure)
    }

    @Test
    fun rejectedRebirthStartReturnsHomeAndReusesItsCreatedRunOnStart() {
        val rig = AppSessionTestRig()
        var startAttempts = 0
        rig.gameplay.configureRun = { run ->
            run.commandHandler = { command ->
                startAttempts += 1
                if (startAttempts == 1) {
                    GameplayAcceptance.Rejected(
                        run.instanceId,
                        run.revision,
                        GameplayRejection.AlreadyStarted,
                    )
                } else {
                    run.phase = GameplayRunPhase.RUNNING
                    run.complete(command, GameplayCommandOutcome.RunStarted)
                }
            }
        }
        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(
                SessionInteractionPulse.OpenOverlay(AppDestination.Rebirth),
            ),
        )
        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.RebirthRequested),
        )

        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.RebirthRequested),
        )

        val rejected = shell(rig)
        assertEquals(AppDestination.Home, rejected.base)
        assertNull(rejected.overlay)
        assertEquals(RunId(0L), rejected.activeRunId)
        assertEquals(GameplayRunPhase.CREATED, rejected.gameplayPhase)
        assertNull(rejected.pendingWorkflow)
        assertIs<SessionWorkflowFailure.GameplayCommandRejected>(rejected.workflowFailure)
        assertEquals(listOf(RunId(0L)), rig.gameplay.createdRunIds)
        assertEquals(1, rig.rebirthAcceptedFeedbackCount)

        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.StartRunRequested),
        )

        assertEquals(listOf(RunId(0L)), rig.gameplay.createdRunIds)
        assertEquals(2, startAttempts)
        assertEquals(AppDestination.Gameplay, shell(rig).base)
        assertEquals(GameplayRunPhase.RUNNING, shell(rig).gameplayPhase)
        assertNull(shell(rig).workflowFailure)
    }

    @Test
    fun participantDispatchCannotRecursivelyEnterSession() {
        val rig = AppSessionTestRig()
        var recursiveFailure: Throwable? = null
        rig.gameplay.configureRun = { run ->
            run.commandHandler = { command ->
                recursiveFailure = runCatching {
                    rig.component.accept(SessionInteractionPulse.ToggleMuteRequested)
                }.exceptionOrNull()
                run.phase = GameplayRunPhase.RUNNING
                run.complete(command, GameplayCommandOutcome.RunStarted)
            }
        }

        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.StartRunRequested),
        )

        assertIs<IllegalStateException>(recursiveFailure)
        assertEquals(AppDestination.Gameplay, rig.component.query(AppSessionQuery.GetShell).base)
    }

    @Test
    fun mismatchedGameplayResultIsRejectedBeforeItCanEnterTheCompletionDeque() {
        val rig = AppSessionTestRig()
        rig.gameplay.configureRun = { run ->
            run.commandHandler = { command ->
                rig.component.receiveGameplayCommandResult(
                    GameplayCommandResult.Accepted(
                        command.ref.copy(ordinal = command.ref.ordinal + 1),
                        GameplayRevision(1L),
                        GameplayCommandOutcome.RunStarted,
                    ),
                )
                error("unreachable")
            }
        }

        assertFailsWith<IllegalStateException> {
            rig.component.accept(SessionInteractionPulse.StartRunRequested)
        }

        val shell = rig.component.query(AppSessionQuery.GetShell)
        assertEquals(SessionWorkflowPhase.STARTING_RUN, shell.pendingWorkflow)
        assertEquals(AppDestination.Home, shell.base)
        assertFailsWith<IllegalStateException> {
            rig.component.receiveGameplayCommandResult(
                GameplayCommandResult.Accepted(
                    GameplayCommandRef(
                        GameplayCommandSource.LocalSession,
                        rig.gameplay.activeFakeRun()!!.instanceId,
                        sourceRevision = shell.revision.value,
                        ordinal = 0,
                    ),
                    GameplayRevision(1L),
                    GameplayCommandOutcome.RunStarted,
                ),
            )
        }
    }

    @Test
    fun throwBeforeResultLeavesPendingWhileThrowAfterResultDrainsBeforeRethrow() {
        val beforeRig = AppSessionTestRig()
        beforeRig.gameplay.configureRun = { run ->
            run.commandHandler = { error("before-result") }
        }

        val beforeFailure = assertFailsWith<IllegalStateException> {
            beforeRig.component.accept(SessionInteractionPulse.StartRunRequested)
        }
        assertEquals("before-result", beforeFailure.message)
        val pending = beforeRig.component.query(AppSessionQuery.GetShell)
        assertEquals(SessionWorkflowPhase.STARTING_RUN, pending.pendingWorkflow)
        assertEquals(AppDestination.Home, pending.base)

        val afterRig = AppSessionTestRig()
        afterRig.gameplay.configureRun = { run ->
            run.commandHandler = { command ->
                run.phase = GameplayRunPhase.RUNNING
                run.complete(command, GameplayCommandOutcome.RunStarted)
                error("after-result")
            }
        }

        val afterFailure = assertFailsWith<IllegalStateException> {
            afterRig.component.accept(SessionInteractionPulse.StartRunRequested)
        }
        assertEquals("after-result", afterFailure.message)
        val drained = afterRig.component.query(AppSessionQuery.GetShell)
        assertEquals(AppDestination.Gameplay, drained.base)
        assertEquals(GameplayRunPhase.RUNNING, drained.gameplayPhase)
        assertNull(drained.pendingWorkflow)
    }

    @Test
    fun acceptedWithoutResultAndResultPlusRejectedAreProgrammingFaults() {
        val missingRig = AppSessionTestRig()
        missingRig.gameplay.configureRun = { run ->
            run.commandHandler = {
                GameplayAcceptance.Accepted(run.instanceId, run.revision)
            }
        }

        assertFailsWith<IllegalStateException> {
            missingRig.component.accept(SessionInteractionPulse.StartRunRequested)
        }
        assertEquals(
            SessionWorkflowPhase.STARTING_RUN,
            missingRig.component.query(AppSessionQuery.GetShell).pendingWorkflow,
        )

        val contradictoryRig = AppSessionTestRig()
        contradictoryRig.gameplay.configureRun = { run ->
            run.commandHandler = { command ->
                run.phase = GameplayRunPhase.RUNNING
                run.complete(command, GameplayCommandOutcome.RunStarted)
                GameplayAcceptance.Rejected(
                    run.instanceId,
                    run.revision,
                    GameplayRejection.AlreadyStarted,
                )
            }
        }

        assertFailsWith<IllegalStateException> {
            contradictoryRig.component.accept(SessionInteractionPulse.StartRunRequested)
        }
        val drained = contradictoryRig.component.query(AppSessionQuery.GetShell)
        assertEquals(AppDestination.Gameplay, drained.base)
        assertNull(drained.pendingWorkflow)
    }

    @Test
    fun secondRebirthRequestDrainsProfileThenGameplayInOrderedNestedChain() {
        val rig = AppSessionTestRig()
        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(
                SessionInteractionPulse.OpenOverlay(AppDestination.Rebirth),
            ),
        )
        rig.effectEvents.clear()
        rig.profile.onCommandObserved = { command ->
            rig.effectEvents += "profile"
            assertEquals(ProfileCommandSource.LocalSession, command.ref.sourceInstance)
            assertEquals(SessionWorkflowPhase.ADVANCING_REBIRTH, shell(rig).pendingWorkflow)
        }
        rig.gameplay.onCreateRun = { runId ->
            rig.effectEvents += "ensure"
            assertEquals(RunId(0L), runId)
            assertEquals(SessionWorkflowPhase.STARTING_REBIRTH_RUN, shell(rig).pendingWorkflow)
        }
        rig.gameplay.configureRun = { run ->
            run.onCommandObserved = { command ->
                rig.effectEvents += "gameplay"
                assertEquals(SessionWorkflowPhase.STARTING_REBIRTH_RUN, shell(rig).pendingWorkflow)
                assertIs<GameplaySessionPulse.StartRun>(command.pulse)
            }
        }

        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.RebirthRequested),
        )
        val armed = shell(rig)
        assertTrue(armed.rebirthConfirmationArmed)
        assertTrue(rig.profile.commands.isEmpty())
        assertTrue(rig.effectEvents.isEmpty())

        val acceptance = assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.RebirthRequested),
        )

        assertEquals(SessionRevision(3L), acceptance.revision)
        assertEquals(listOf("profile", "ensure", "gameplay", "rebirth"), rig.effectEvents)
        assertEquals(1, rig.profile.commands.size)
        assertEquals(ProfilePulse.AdvanceRebirth, rig.profile.commands.single().pulse)
        assertEquals(1, rig.gameplay.createdRunIds.size)
        assertEquals(1, rig.rebirthAcceptedFeedbackCount)
        val completed = shell(rig)
        assertEquals(SessionRevision(5L), completed.revision)
        assertEquals(AppDestination.Gameplay, completed.base)
        assertEquals(GameplayRunPhase.RUNNING, completed.gameplayPhase)
        assertFalse(completed.rebirthConfirmationArmed)
        assertNull(completed.pendingWorkflow)
    }

    @Test
    fun muteCompletionSynchronizesPreferencesBeforeItsSingleFeedback() {
        val rig = AppSessionTestRig()
        rig.effectEvents.clear()
        rig.profile.onCommandObserved = {
            rig.effectEvents += "profile"
            assertEquals(SessionWorkflowPhase.TOGGLING_MUTE, shell(rig).pendingWorkflow)
        }

        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.ToggleMuteRequested),
        )

        assertEquals(listOf("profile", "audio", "mute"), rig.effectEvents)
        assertEquals(1, rig.muteFeedbackCount)
        val synchronized = rig.audioPreferences.last()
        assertFalse(synchronized.soundEnabled)
        assertFalse(synchronized.musicEnabled)
        assertNull(shell(rig).pendingWorkflow)
    }

    @Test
    fun profilePreacceptRejectionUsesItsExactCarrierWithoutAResultCallback() {
        val rig = AppSessionTestRig()
        rig.profile.commandHandler = { command ->
            assertEquals(ProfilePulse.SelectCoreShape(CoreShape.PRISM), command.pulse)
            ProfileAcceptance.Rejected(
                rig.profile.instanceId,
                rig.profile.revision,
                ProfileRejection.CoreShapeLocked,
            )
        }

        val acceptance = assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(
                SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM),
            ),
        )

        assertEquals(SessionRevision(1L), acceptance.revision)
        val shell = shell(rig)
        assertEquals(SessionRevision(2L), shell.revision)
        assertNull(shell.pendingWorkflow)
        val failure = assertIs<SessionWorkflowFailure.ProfileCommandRejected>(
            shell.workflowFailure,
        )
        assertEquals(rig.profile.commands.single().ref, failure.commandRef)
        assertEquals(ProfileRejection.CoreShapeLocked, failure.reason)
        assertEquals(CoreShape.ORB, rig.profile.profile.loadout.coreShape)
    }

    @Test
    fun profileAcceptedWithoutResultFaultsWhileThrowAfterResultStillDrains() {
        val missingRig = AppSessionTestRig()
        missingRig.profile.commandHandler = {
            ProfileAcceptance.Accepted(
                missingRig.profile.instanceId,
                missingRig.profile.revision,
            )
        }

        assertFailsWith<IllegalStateException> {
            missingRig.component.accept(
                SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM),
            )
        }
        assertEquals(
            SessionWorkflowPhase.SELECTING_CORE_SHAPE,
            shell(missingRig).pendingWorkflow,
        )

        val afterRig = AppSessionTestRig()
        afterRig.profile.commandHandler = { command ->
            afterRig.profile.complete(
                command,
                ProfileCommandOutcome.CoreShapeSelected(CoreShape.PRISM),
            )
            error("profile-after-result")
        }

        val failure = assertFailsWith<IllegalStateException> {
            afterRig.component.accept(
                SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM),
            )
        }
        assertEquals("profile-after-result", failure.message)
        assertNull(shell(afterRig).pendingWorkflow)
        assertEquals(CoreShape.PRISM, afterRig.profile.profile.loadout.coreShape)
    }

    @Test
    fun resetCompletionRereadsPersistenceAndFreshPreferencesBeforeAudioSync() {
        val profile = FakeSessionProfilePort(
            profile = kinetickk.ball.profile.api.PlayerProfile(
                preferences = PlayerPreferences(
                    soundEnabled = false,
                    musicEnabled = false,
                    masterVolume = 0.2f,
                ),
            ),
        ).apply {
            val reason = ProfileResetReason.LegacyDataDetected
            bootstrap = ProfileBootstrapStatus.Blocked(
                ProfileBootstrapBlockReason.ResetRequired(reason),
            )
            reset = ProfileResetStatus.ConfirmationRequired(reason, ProfileLegacyKeys.ALL)
        }
        val rig = AppSessionTestRig(profile = profile)
        rig.profile.queries.clear()
        rig.effectEvents.clear()

        assertIs<SessionAcceptance.Accepted>(
            rig.component.accept(SessionInteractionPulse.ResetConfirmed),
        )

        assertEquals(
            listOf("persistenceStatus", "preferences", "persistenceStatus"),
            rig.profile.queries,
        )
        assertEquals(listOf("audio"), rig.effectEvents)
        assertEquals(PlayerPreferences(), rig.audioPreferences.last())
        assertNull(shell(rig).pendingWorkflow)
        assertEquals(kinetickk.flow.session.api.SessionResetLifecycle.READY, shell(rig).resetLifecycle)
    }

    @Test
    fun deployedCompletionQueueAcceptsEightAndRefusesNinthWithoutTruncation() {
        val completions = sessionCompletionDeque<Int>()

        repeat(8) { value -> assertTrue(completions.tryAddLast(value)) }

        assertEquals(8, completions.size)
        assertFalse(completions.tryAddLast(8))
        assertEquals((0 until 8).toList(), List(8) { completions.removeFirstOrNull() })
    }

    @Test
    fun acceptorCausalDepthAndOutputFanoutAcceptNAndRefuseNPlusOne() {
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
