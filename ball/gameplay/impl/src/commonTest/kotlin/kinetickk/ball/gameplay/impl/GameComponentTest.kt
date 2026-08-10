// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl

import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayCommand
import kinetickk.ball.gameplay.api.GameplayCommandAdmission
import kinetickk.ball.gameplay.api.GameplayCommandOutcome
import kinetickk.ball.gameplay.api.GameplayCommandRef
import kinetickk.ball.gameplay.api.GameplayCommandResult
import kinetickk.ball.gameplay.api.GameplayCommandSource
import kinetickk.ball.gameplay.api.GameplayExitProfileOutcome
import kinetickk.ball.gameplay.api.GameplayInputField
import kinetickk.ball.gameplay.api.GameplayInputReason
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplaySessionPulse
import kinetickk.ball.gameplay.api.RunConfiguration
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileCommand
import kinetickk.ball.profile.api.ProfileCommandAdmission
import kinetickk.ball.profile.api.ProfileCommandOutcome
import kinetickk.ball.profile.api.ProfileCommandResult
import kinetickk.ball.profile.api.ProfileCommandSource as TargetProfileCommandSource
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.foundation.collections.ImmutableList
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val testContent = SyntheticGameplayContent

class GameComponentTest {
    @Test
    fun createdComponentHasNoSimulationBeforeAcceptedStart() {
        val component = component()

        val status = component.query(GameplayQuery.GetRunStatus)
        val render = component.query(GameplayQuery.GetRender)

        assertEquals(GameplayRevision.ZERO, status.revision)
        assertEquals(GameplayRunPhase.CREATED, status.phase)
        assertFalse(status.profileCommandPending)
        assertEquals(GameplayRevision.ZERO, render.revision)
        assertNull(render.renderModel)
        assertNull(component.query(GameplayQuery.GetActiveWeapon).weapon)
        assertTrue(component.query(GameplayQuery.GetCodexStacks).itemStacks.isEmpty())
        assertEquals(GameplayRevision.ZERO, component.stateSnapshot().revision)
        assertTrue(component.visualFxSnapshot().particles.isEmpty())
    }

    @Test
    fun startResultObservesPublishedRevisionAndRender() {
        val results = mutableListOf<GameplayCommandResult.Accepted>()
        var observedPhase: GameplayRunPhase? = null
        var observedRevision: GameplayRevision? = null
        var observedRenderRevision: GameplayRevision? = null
        var observedCapturedContent = false
        lateinit var component: GameComponent
        component = component { result ->
            results += result
            val status = component.query(GameplayQuery.GetRunStatus)
            val render = component.query(GameplayQuery.GetRender)
            observedPhase = status.phase
            observedRevision = status.revision
            observedRenderRevision = render.revision
            observedCapturedContent = render.renderModel?.content === testContent
        }
        val call = component.sessionCall(
            GameplaySessionPulse.StartRun(configuration(seed = 19)),
            sourceRevision = 7,
            ordinal = 2,
        )

        val acceptance = assertIs<GameplayAcceptance.Accepted>(
            component.accept(call.command, call.admission),
        )

        assertEquals(GameplayRevision(1), acceptance.revision)
        assertEquals(GameplayRunPhase.RUNNING, observedPhase)
        assertEquals(acceptance.revision, observedRevision)
        assertEquals(acceptance.revision, observedRenderRevision)
        assertTrue(observedCapturedContent)
        val result = results.single()
        assertEquals(call.command.ref, result.commandRef)
        assertEquals(acceptance.revision, result.targetRevision)
        assertEquals(GameplayCommandOutcome.RunStarted, result.outcome)
        assertSame(testContent, component.query(GameplayQuery.GetRender).renderModel!!.content)
    }

    @Test
    fun interactionFxAndAudioObserveTheCommittedFrameInDispatchOrder() {
        val observedRevisions = mutableListOf<GameplayRevision>()
        val observedFxCounts = mutableListOf<Int>()
        val audio = RecordingGameplayAudioExecutor()
        lateinit var component: GameComponent
        audio.onAdvance = {
            observedRevisions += component.query(GameplayQuery.GetRunStatus).revision
            observedFxCounts += component.visualFxSnapshot().let { projection ->
                projection.particles.size +
                    projection.motionEchoes.size +
                    projection.shockwaves.size +
                    projection.damageNumbers.size +
                    projection.weaponArcs.size
            }
        }
        component = component(audio = audio)
        component.start()
        assertIs<GameplayAcceptance.Accepted>(
            component.accept(GameplayInteractionPulse.DashRequested),
        )
        audio.clear()

        val frame = assertIs<GameplayAcceptance.Accepted>(
            component.accept(GameplayInteractionPulse.FrameElapsed(0.1f)),
        )

        assertEquals(listOf(frame.revision), observedRevisions)
        assertTrue(observedFxCounts.single() > 0)
        assertEquals(frame.revision, component.query(GameplayQuery.GetRunStatus).revision)
        assertTrue(component.visualFxSnapshot().shockwaves.isNotEmpty())
    }

    @Test
    fun audioFailureDoesNotRollBackAcceptedInteractionFrames() {
        val audio = RecordingGameplayAudioExecutor(throwOnEveryCall = true)
        val component = component(audio = audio)
        component.start()

        val frame = assertIs<GameplayAcceptance.Accepted>(
            component.accept(GameplayInteractionPulse.FrameElapsed(0.1f)),
        )
        assertEquals(frame.revision, component.query(GameplayQuery.GetRunStatus).revision)

        val gesture = assertIs<GameplayAcceptance.Accepted>(
            component.accept(GameplayInteractionPulse.UserGestureObserved),
        )
        assertEquals(gesture.revision, component.query(GameplayQuery.GetRunStatus).revision)
        assertEquals(1, audio.frames.size)
        assertEquals(1, audio.unlockCount)
    }

    @Test
    fun profileProgressUsesExactAdmissionAndAcceptedResultIsQueuedNonReentrantly() {
        val profile = ScriptedProfilePort()
        val sessionResults = mutableListOf<GameplayCommandResult.Accepted>()
        lateinit var component: GameComponent
        component = component(profile = profile) { sessionResults += it }
        component.start()
        sessionResults.clear()
        component.advanceUntilMatter()
        var statusAfterEnqueue: kinetickk.ball.gameplay.api.GameplayRunStatusProjection? = null
        profile.onCommand = { command, admission ->
            assertEquals(command.ref, admission.commandRef)
            assertEquals(
                TargetProfileCommandSource.GameplayRun(component.instanceId.runId.value),
                command.ref.sourceInstance,
            )
            assertEquals(LOCAL_PROFILE_INSTANCE_ID, command.ref.targetInstance)
            assertTrue(
                assertIs<ProfilePulse.ApplyGameplayProgress>(command.pulse)
                    .update.bankedMatter > 0,
            )
            val wrongRef = command.ref.copy(ordinal = command.ref.ordinal + 1)
            assertFailsWith<IllegalStateException> {
                component.receiveProfileCommandResult(
                    acceptedProfileResult(wrongRef, ProfileRevision(51)),
                )
            }
            component.receiveProfileCommandResult(
                acceptedProfileResult(command.ref, ProfileRevision(51)),
            )
            statusAfterEnqueue = component.query(GameplayQuery.GetRunStatus)
            ProfileAcceptance.Accepted(LOCAL_PROFILE_INSTANCE_ID, ProfileRevision(51))
        }
        val exit = component.sessionCall(
            GameplaySessionPulse.ExitRun,
            sourceRevision = 8,
            ordinal = 3,
        )

        val rootAcceptance = assertIs<GameplayAcceptance.Accepted>(
            component.accept(exit.command, exit.admission),
        )

        val call = profile.calls.single()
        assertEquals(call.command.ref, call.admission.commandRef)
        assertEquals(rootAcceptance.revision.value, call.command.ref.sourceRevision)
        val queued = requireNotNull(statusAfterEnqueue)
        assertTrue(queued.profileCommandPending)
        assertEquals(rootAcceptance.revision, queued.revision)
        val completed = component.query(GameplayQuery.GetRunStatus)
        assertFalse(completed.profileCommandPending)
        assertEquals(GameplayRevision(rootAcceptance.revision.value + 1), completed.revision)
        val result = sessionResults.single()
        assertEquals(exit.command.ref, result.commandRef)
        assertEquals(completed.revision, result.targetRevision)
        assertEquals(
            GameplayExitProfileOutcome.ProgressApplied,
            assertIs<GameplayCommandOutcome.RunExited>(result.outcome).profile,
        )
    }

    @Test
    fun immediateProfileRejectionCompletesDeferredExitWithTypedOutcome() {
        val profile = ScriptedProfilePort()
        val sessionResults = mutableListOf<GameplayCommandResult.Accepted>()
        val component = component(profile = profile) { sessionResults += it }
        component.start()
        sessionResults.clear()
        component.advanceUntilMatter()
        val rejection = ProfileAcceptance.Rejected(
            instanceId = LOCAL_PROFILE_INSTANCE_ID,
            observedRevision = ProfileRevision(77),
            reason = ProfileRejection.ResetRequired,
        )
        profile.onCommand = { _, _ -> rejection }
        val exit = component.sessionCall(
            GameplaySessionPulse.ExitRun,
            sourceRevision = 12,
            ordinal = 4,
        )

        val rootAcceptance = assertIs<GameplayAcceptance.Accepted>(
            component.accept(exit.command, exit.admission),
        )

        val status = component.query(GameplayQuery.GetRunStatus)
        assertEquals(GameplayRunPhase.EXITED, status.phase)
        assertFalse(status.profileCommandPending)
        assertEquals(GameplayRevision(rootAcceptance.revision.value + 1), status.revision)
        val result = sessionResults.single()
        assertEquals(exit.command.ref, result.commandRef)
        val outcome = assertIs<GameplayExitProfileOutcome.ProgressRejected>(
            assertIs<GameplayCommandOutcome.RunExited>(result.outcome).profile,
        )
        assertEquals(rejection.observedRevision, outcome.observedRevision)
        assertEquals(rejection.reason, outcome.reason)
    }

    @Test
    fun profileThrowBeforeResultPreservesPublishedPendingExitAndRethrows() {
        val profile = ScriptedProfilePort()
        val sessionResults = mutableListOf<GameplayCommandResult.Accepted>()
        val component = component(profile = profile) { sessionResults += it }
        component.start()
        sessionResults.clear()
        component.advanceUntilMatter()
        val before = component.query(GameplayQuery.GetRunStatus).revision
        profile.onCommand = { _, _ -> throw ProfileBeforeResultFault() }
        val exit = component.sessionCall(GameplaySessionPulse.ExitRun, 15, 5)

        assertFailsWith<ProfileBeforeResultFault> {
            component.accept(exit.command, exit.admission)
        }

        val status = component.query(GameplayQuery.GetRunStatus)
        assertEquals(GameplayRunPhase.EXITED, status.phase)
        assertTrue(status.profileCommandPending)
        assertEquals(GameplayRevision(before.value + 1), status.revision)
        assertEquals(
            status.revision.value,
            component.stateSnapshot().pendingProfileCommand!!.command.ref.sourceRevision,
        )
        assertTrue(sessionResults.isEmpty())
    }

    @Test
    fun profileThrowAfterResultAppliesQueuedCompletionBeforeRethrow() {
        val profile = ScriptedProfilePort()
        val sessionResults = mutableListOf<GameplayCommandResult.Accepted>()
        lateinit var component: GameComponent
        component = component(profile = profile) { sessionResults += it }
        component.start()
        sessionResults.clear()
        component.advanceUntilMatter()
        val before = component.query(GameplayQuery.GetRunStatus).revision
        var queuedRevision: GameplayRevision? = null
        profile.onCommand = { command, _ ->
            component.receiveProfileCommandResult(
                acceptedProfileResult(command.ref, ProfileRevision(91)),
            )
            queuedRevision = component.query(GameplayQuery.GetRunStatus).revision
            throw ProfileAfterResultFault()
        }
        val exit = component.sessionCall(GameplaySessionPulse.ExitRun, 16, 6)

        assertFailsWith<ProfileAfterResultFault> {
            component.accept(exit.command, exit.admission)
        }

        val status = component.query(GameplayQuery.GetRunStatus)
        assertEquals(GameplayRevision(before.value + 1), queuedRevision)
        assertEquals(GameplayRevision(before.value + 2), status.revision)
        assertEquals(GameplayRunPhase.EXITED, status.phase)
        assertFalse(status.profileCommandPending)
        val result = sessionResults.single()
        assertEquals(exit.command.ref, result.commandRef)
        assertEquals(status.revision, result.targetRevision)
        assertEquals(
            GameplayExitProfileOutcome.ProgressApplied,
            assertIs<GameplayCommandOutcome.RunExited>(result.outcome).profile,
        )
    }

    @Test
    fun sessionResultSinkFaultPreservesCommittedStartAndReleasesDispatchGuard() {
        var observedRevision: GameplayRevision? = null
        lateinit var component: GameComponent
        component = component {
            observedRevision = component.query(GameplayQuery.GetRunStatus).revision
            throw SessionResultSinkFault()
        }
        val start = component.sessionCall(
            GameplaySessionPulse.StartRun(configuration(seed = 23)),
            sourceRevision = 17,
            ordinal = 7,
        )

        assertFailsWith<SessionResultSinkFault> {
            component.accept(start.command, start.admission)
        }

        val committed = component.query(GameplayQuery.GetRunStatus)
        assertEquals(GameplayRevision(1), observedRevision)
        assertEquals(GameplayRevision(1), committed.revision)
        assertEquals(GameplayRunPhase.RUNNING, committed.phase)
        assertTrue(component.query(GameplayQuery.GetRender).renderModel != null)
        val subsequent = assertIs<GameplayAcceptance.Accepted>(
            component.accept(GameplayInteractionPulse.FrameElapsed(0f)),
        )
        assertEquals(GameplayRevision(2), subsequent.revision)
    }

    @Test
    fun recursiveLocalAcceptIsRejectedWhileThePublishedStartFrameRemains() {
        val results = mutableListOf<GameplayCommandResult.Accepted>()
        var recursiveFailure: Throwable? = null
        lateinit var component: GameComponent
        component = component { result ->
            results += result
            recursiveFailure = runCatching {
                component.accept(GameplayInteractionPulse.DashRequested)
            }.exceptionOrNull()
        }
        val start = component.sessionCall(
            GameplaySessionPulse.StartRun(configuration(seed = 29)),
            sourceRevision = 18,
            ordinal = 8,
        )

        val acceptance = assertIs<GameplayAcceptance.Accepted>(
            component.accept(start.command, start.admission),
        )

        assertIs<IllegalStateException>(recursiveFailure)
        assertEquals("Recursive inline dispatch is forbidden", recursiveFailure!!.message)
        assertEquals(acceptance.revision, component.query(GameplayQuery.GetRunStatus).revision)
        assertEquals(GameplayRunPhase.RUNNING, component.query(GameplayQuery.GetRunStatus).phase)
        assertEquals(start.command.ref, results.single().commandRef)
        val subsequent = assertIs<GameplayAcceptance.Accepted>(
            component.accept(GameplayInteractionPulse.FrameElapsed(0f)),
        )
        assertEquals(GameplayRevision(acceptance.revision.value + 1), subsequent.revision)
    }

    @Test
    fun acceptedProfileCommandWithoutReservedResultLeavesPendingFramePublished() {
        val profile = ScriptedProfilePort()
        val sessionResults = mutableListOf<GameplayCommandResult.Accepted>()
        val component = component(profile = profile) { sessionResults += it }
        component.start()
        sessionResults.clear()
        component.advanceUntilMatter()
        val before = component.query(GameplayQuery.GetRunStatus).revision
        profile.onCommand = { _, _ ->
            ProfileAcceptance.Accepted(LOCAL_PROFILE_INSTANCE_ID, ProfileRevision(101))
        }
        val exit = component.sessionCall(GameplaySessionPulse.ExitRun, 19, 9)

        val failure = assertFailsWith<IllegalStateException> {
            component.accept(exit.command, exit.admission)
        }

        assertTrue(failure.message.orEmpty().contains("without its reserved result"))
        val status = component.query(GameplayQuery.GetRunStatus)
        assertEquals(GameplayRevision(before.value + 1), status.revision)
        assertEquals(GameplayRunPhase.EXITED, status.phase)
        assertTrue(status.profileCommandPending)
        assertTrue(sessionResults.isEmpty())
    }

    @Test
    fun rejectedRootPulseChangesNoStateOrDispatchedOutputs() {
        val profile = ScriptedProfilePort()
        val audio = RecordingGameplayAudioExecutor()
        val sessionResults = mutableListOf<GameplayCommandResult.Accepted>()
        val component = component(profile = profile, audio = audio) { sessionResults += it }
        component.start()
        sessionResults.clear()
        audio.clear()
        val beforeState = component.stateSnapshot()
        val beforeFx = component.visualFxSnapshot()
        val beforeUnlocks = audio.unlockCount

        val rejection = assertIs<GameplayAcceptance.Rejected>(
            component.accept(GameplayInteractionPulse.FrameElapsed(Float.NaN)),
        )

        assertEquals(
            GameplayRejection.InvalidInput(
                GameplayInputField.FRAME_DELTA_SECONDS,
                GameplayInputReason.NON_FINITE,
            ),
            rejection.reason,
        )
        assertSame(beforeState, component.stateSnapshot())
        assertEquals(beforeState.revision, rejection.observedRevision)
        assertEquals(beforeFx, component.visualFxSnapshot())
        assertTrue(audio.frames.isEmpty())
        assertEquals(beforeUnlocks, audio.unlockCount)
        assertTrue(profile.calls.isEmpty())
        assertTrue(sessionResults.isEmpty())
    }

    @Test
    fun defaultFeatureEnforcesActiveRunTerminalPendingAndMonotonicReplacementRules() {
        val profile = ScriptedProfilePort()
        val feature = DefaultGameplayFeature(profile, NoOpAudioService)
        val first = assertIs<GameComponent>(feature.createRun(RunId(1)) {})

        assertFailsWith<IllegalStateException> {
            feature.createRun(RunId(2)) {}
        }
        first.start()
        assertFailsWith<IllegalStateException> {
            feature.createRun(RunId(2)) {}
        }

        val firstExit = first.sessionCall(GameplaySessionPulse.ExitRun, 20, 10)
        assertIs<GameplayAcceptance.Accepted>(
            first.accept(firstExit.command, firstExit.admission),
        )
        assertEquals(GameplayRunPhase.EXITED, first.query(GameplayQuery.GetRunStatus).phase)
        assertFalse(first.query(GameplayQuery.GetRunStatus).profileCommandPending)
        assertFailsWith<IllegalArgumentException> {
            feature.createRun(RunId(1)) {}
        }

        val second = assertIs<GameComponent>(feature.createRun(RunId(2)) {})
        assertSame(second, feature.activeRun())
        second.start()
        second.advanceUntilMatter()
        profile.onCommand = { _, _ ->
            ProfileAcceptance.Accepted(LOCAL_PROFILE_INSTANCE_ID, ProfileRevision(102))
        }
        val secondExit = second.sessionCall(GameplaySessionPulse.ExitRun, 21, 11)
        assertFailsWith<IllegalStateException> {
            second.accept(secondExit.command, secondExit.admission)
        }
        val pendingTerminal = second.query(GameplayQuery.GetRunStatus)
        assertEquals(GameplayRunPhase.EXITED, pendingTerminal.phase)
        assertTrue(pendingTerminal.profileCommandPending)

        val replacementFailure = assertFailsWith<IllegalStateException> {
            feature.createRun(RunId(3)) {}
        }
        assertTrue(replacementFailure.message.orEmpty().contains("pending Profile command"))
        assertSame(second, feature.activeRun())
    }
}

private fun component(
    profile: ProfilePort = ScriptedProfilePort(),
    audio: GameplayAudioExecutor = RecordingGameplayAudioExecutor(),
    commandResultSink: (GameplayCommandResult.Accepted) -> Unit = {},
): GameComponent = GameComponent.create(
    runId = RunId(31),
    profilePort = profile,
    audioExecutor = audio,
    commandResultSink = commandResultSink,
)

private fun GameComponent.start() {
    val call = sessionCall(GameplaySessionPulse.StartRun(configuration()), 1, 0)
    assertIs<GameplayAcceptance.Accepted>(accept(call.command, call.admission))
}

private data class SessionCall(
    val command: GameplayCommand,
    val admission: GameplayCommandAdmission,
)

private fun GameComponent.sessionCall(
    pulse: GameplaySessionPulse,
    sourceRevision: Long,
    ordinal: Int,
): SessionCall {
    val ref = GameplayCommandRef(
        sourceInstance = GameplayCommandSource.LocalSession,
        targetInstance = instanceId,
        sourceRevision = sourceRevision,
        ordinal = ordinal,
    )
    return SessionCall(
        command = GameplayCommand(ref, pulse),
        admission = GameplayCommandAdmission(ref),
    )
}

private fun GameComponent.advanceUntilMatter() {
    repeat(1_200) { frameIndex ->
        val render = checkNotNull(query(GameplayQuery.GetRender).renderModel)
        if (render.runMatter > 0L) return
        check(query(GameplayQuery.GetRunStatus).phase == GameplayRunPhase.RUNNING) {
            "Run stopped before earning progress at frame $frameIndex"
        }
        assertIs<GameplayAcceptance.Accepted>(
            accept(GameplayInteractionPulse.FrameElapsed(0.1f)),
        )
    }
    error("Run earned no progress within the deterministic frame budget")
}

private fun configuration(seed: Int = 73): RunConfiguration = RunConfiguration(
    content = testContent,
    profile = GameplayProfileSnapshot(
        preferences = PlayerPreferences(),
        economy = PlayerEconomy(),
        loadout = PlayerLoadout(),
        labProgress = LabProgress(),
        collection = PlayerCollection(),
        rebirthProgress = RebirthProgress(),
    ),
    seed = seed,
)

private fun acceptedProfileResult(
    commandRef: kinetickk.ball.profile.api.ProfileCommandRef,
    revision: ProfileRevision,
): ProfileCommandResult.Accepted = ProfileCommandResult.Accepted(
    commandRef = commandRef,
    targetRevision = revision,
    outcome = ProfileCommandOutcome.GameplayProgressApplied,
)

private class RecordingGameplayAudioExecutor(
    private val throwOnEveryCall: Boolean = false,
) : GameplayAudioExecutor {
    val frames = mutableListOf<Pair<Float, List<GameplayAudioCue>>>()
    var unlockCount = 0
    var onAdvance: () -> Unit = {}

    override fun advance(realDeltaSeconds: Float, cues: ImmutableList<GameplayAudioCue>) {
        frames += realDeltaSeconds to cues.toList()
        onAdvance()
        if (throwOnEveryCall) throw AudioResourceFault()
    }

    override fun ensureUnlocked() {
        unlockCount++
        if (throwOnEveryCall) throw AudioResourceFault()
    }

    fun clear() {
        frames.clear()
    }
}

private data class ProfileCall(
    val command: ProfileCommand,
    val admission: ProfileCommandAdmission,
)

private class ScriptedProfilePort : ProfilePort {
    override val instanceId = LOCAL_PROFILE_INSTANCE_ID
    val calls = mutableListOf<ProfileCall>()
    var onCommand: (ProfileCommand, ProfileCommandAdmission) -> ProfileAcceptance = { _, _ ->
        error("Unexpected Profile command")
    }

    override fun accept(pulse: ProfilePulse.Business): ProfileAcceptance = unexpected()

    override fun accept(
        command: ProfileCommand,
        admission: ProfileCommandAdmission,
    ): ProfileAcceptance {
        calls += ProfileCall(command, admission)
        return onCommand(command, admission)
    }

    override fun query(query: ProfileQuery.GetRunBootstrap) = unexpected()
    override fun query(query: ProfileQuery.GetPreferences) = unexpected()
    override fun query(query: ProfileQuery.GetHomeProgress) = unexpected()
    override fun query(query: ProfileQuery.GetLabProgress) = unexpected()
    override fun query(query: ProfileQuery.GetLoadout) = unexpected()
    override fun query(query: ProfileQuery.GetCollection) = unexpected()
    override fun query(query: ProfileQuery.GetRebirthProgress) = unexpected()
    override fun query(query: ProfileQuery.GetPersistenceStatus) = unexpected()

    private fun unexpected(): Nothing = error("Unexpected Profile operation")
}

private class AudioResourceFault : RuntimeException()
private class ProfileBeforeResultFault : RuntimeException()
private class ProfileAfterResultFault : RuntimeException()
private class SessionResultSinkFault : RuntimeException()

private object NoOpAudioService : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
