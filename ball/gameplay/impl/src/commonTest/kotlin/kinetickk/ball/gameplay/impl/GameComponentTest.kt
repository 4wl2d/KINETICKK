// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl

import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayCommandAdmissionFailureReason
import kinetickk.ball.gameplay.api.GameplayCommandBoundaryResponse
import kinetickk.ball.gameplay.api.GameplayCommandIngressResult
import kinetickk.ball.gameplay.api.GameplayCommandSource
import kinetickk.ball.gameplay.api.GameplayEffectiveProtocolIdentity
import kinetickk.ball.gameplay.api.GameplayExitProgressResult
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayModuleCommand
import kinetickk.ball.gameplay.api.GameplayModuleCommandRequest
import kinetickk.ball.gameplay.api.GameplayModuleResult
import kinetickk.ball.gameplay.api.GameplayModuleResultDelivery
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplaySemanticHandle
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.GameplayProfileRoute
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.LabProgressProjection
import kinetickk.ball.profile.api.LoadoutProjection
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfileBootstrapBlockReason
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileCommandBoundaryResponse
import kinetickk.ball.profile.api.ProfileCommandIngressResult
import kinetickk.ball.profile.api.ProfileCommandRefusalEvidence
import kinetickk.ball.profile.api.ProfileCommandSourceToken
import kinetickk.ball.profile.api.ProfileEffectiveProtocolIdentity
import kinetickk.ball.profile.api.ProfileModuleCommandRequest
import kinetickk.ball.profile.api.ProfileModuleResult
import kinetickk.ball.profile.api.ProfileModuleResultDelivery
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileResultIssuerProvenance
import kinetickk.ball.profile.api.ProfileResultSourceToken
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.ProfileTargetBoundaryProvenance
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
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

class GameComponentTest {
    @Test
    fun createdComponentCapturesContentWithoutPublishingSimulation() {
        val component = component()

        val status = component.query(GameplayQuery.GetRunStatus)
        val render = component.renderSnapshot()
        assertEquals(GameplayRevision.ZERO, status.revision)
        assertEquals(GameplayRunPhase.CREATED, status.phase)
        assertFalse(status.profileCommandPending)
        assertNull(render.renderModel)
        assertSame(SyntheticGameplayContent, component.stateSnapshot().content)
        assertNull(component.query(GameplayQuery.GetActiveWeapon).weapon)
        assertTrue(component.query(GameplayQuery.GetCodexStacks).itemStacks.isEmpty())
        assertTrue(component.visualFxSnapshot().particles.isEmpty())
    }

    @Test
    fun startReadsProfileAtBoundaryThenDeliversCanonicalResultAfterPublication() {
        val profile = TestProfilePort()
        val results = mutableListOf<GameplayModuleResultDelivery>()
        lateinit var component: GameComponent
        component = component(profile) { delivery ->
            results += delivery
            assertEquals(GameplayRunPhase.RUNNING, component.query(GameplayQuery.GetRunStatus).phase)
            assertSame(SyntheticGameplayContent, component.renderSnapshot().renderModel!!.content)
        }
        val request = component.request(GameplayModuleCommand.StartRun, sourceRevision = 7, ordinal = 2)

        val ingress = assertIs<GameplayCommandIngressResult.Accepted>(
            component.acceptFromSession(request, causalScope = 41, causalDepth = 2),
        )

        assertEquals(GameplayRevision(1), ingress.targetRevision)
        assertEquals(1, profile.bootstrapReadCount)
        val delivery = results.single()
        assertEquals(request.semanticHandle, delivery.commandSource.semanticHandle)
        assertEquals(41, delivery.commandSource.causalScope)
        assertEquals(2, delivery.commandSource.causalDepth)
        assertEquals(GameplayEffectiveProtocolIdentity.SESSION_START, delivery.effectiveProtocolIdentity)
        assertEquals(GameplayModuleResult.RunStarted, delivery.result)
        assertEquals(41, delivery.resultSource.causalScope)
        assertEquals(3, delivery.resultSource.causalDepth)
        assertEquals(GameplayRevision(1), delivery.resultSource.targetRevision)
    }

    @Test
    fun wrongTargetAndUnavailableBootstrapRefuseBeforePublication() {
        val profile = TestProfilePort()
        val results = mutableListOf<GameplayModuleResultDelivery>()
        val component = component(profile, commandResultSink = results::add)
        val exact = component.request(GameplayModuleCommand.StartRun)
        val wrongTarget = exact.copy(
            targetInstance = kinetickk.ball.gameplay.api.GameplayInstanceId(RunId(99)),
        )

        val targetRefusal = assertIs<GameplayCommandIngressResult.RejectedBeforeAcceptance>(
            component.acceptFromSession(wrongTarget, 4, 0),
        )
        assertIs<GameplayCommandBoundaryResponse.ValidationFailure>(
            targetRefusal.refusal.boundaryResponse,
        )
        assertEquals(GameplayRevision.ZERO, component.stateSnapshot().revision)
        assertEquals(0, profile.bootstrapReadCount)

        profile.bootstrapResult = ProfileRunBootstrapResult.Unavailable(
            ProfileBootstrapStatus.Blocked(ProfileBootstrapBlockReason.ResetInProgress),
        )
        val bootstrapRefusal = assertIs<GameplayCommandIngressResult.RejectedBeforeAcceptance>(
            component.acceptFromSession(exact, 5, 0),
        )
        val decision = assertIs<GameplayCommandBoundaryResponse.DecisionRejected>(
            bootstrapRefusal.refusal.boundaryResponse,
        )
        assertEquals(
            kinetickk.ball.gameplay.api.GameplayRejection.ProfileBootstrapUnavailable,
            decision.reason,
        )
        assertEquals(GameplayRevision.ZERO, component.stateSnapshot().revision)
        assertTrue(results.isEmpty())
    }

    @Test
    fun invalidTrustedProfileContextIsAStateDecisionRejection() {
        val profile = TestProfilePort().apply {
            snapshot = snapshot.copy(
                preferences = PlayerPreferences(masterVolume = Float.NaN),
            )
        }
        val component = component(profile)

        val refusal = assertIs<GameplayCommandIngressResult.RejectedBeforeAcceptance>(
            component.acceptFromSession(component.request(GameplayModuleCommand.StartRun), 9, 0),
        )

        val decision = assertIs<GameplayCommandBoundaryResponse.DecisionRejected>(
            refusal.refusal.boundaryResponse,
        )
        assertIs<kinetickk.ball.gameplay.api.GameplayRejection.InvalidStartConfiguration>(
            decision.reason,
        )
        assertEquals(GameplayRevision.ZERO, component.stateSnapshot().revision)
        assertNull(component.stateSnapshot().engine)
    }

    @Test
    fun invalidPreferencesContextIsAStateDecisionRejection() {
        val profile = TestProfilePort()
        val component = component(profile)
        component.start()
        val before = component.stateSnapshot()
        profile.snapshot = profile.snapshot.copy(
            preferences = PlayerPreferences(masterVolume = Float.NaN),
        )

        val refusal = assertIs<GameplayCommandIngressResult.RejectedBeforeAcceptance>(
            component.acceptFromSession(
                component.request(GameplayModuleCommand.ApplyPreferences),
                causalScope = 11,
                causalDepth = 0,
            ),
        )

        assertEquals(
            GameplayCommandBoundaryResponse.DecisionRejected(
                kinetickk.ball.gameplay.api.GameplayRejection.InvalidPreferencesProjection,
            ),
            refusal.refusal.boundaryResponse,
        )
        assertEquals(before, component.stateSnapshot())
    }

    @Test
    fun applyPreferencesReadsCurrentProfileProjectionAtTheTargetBoundary() {
        val profile = TestProfilePort()
        val results = mutableListOf<GameplayModuleResultDelivery>()
        val component = component(profile, commandResultSink = results::add)
        component.start()
        results.clear()
        profile.snapshot = profile.snapshot.copy(
            preferences = PlayerPreferences(masterVolume = 0.4f),
        )

        assertIs<GameplayCommandIngressResult.Accepted>(
            component.acceptFromSession(
                component.request(GameplayModuleCommand.ApplyPreferences, 8, 1),
                causalScope = 19,
                causalDepth = 1,
            ),
        )

        assertEquals(1, profile.preferencesReadCount)
        assertEquals(0.4f, component.renderSnapshot().renderModel!!.settings.masterVolume)
        assertEquals(GameplayModuleResult.PreferencesApplied, results.single().result)
    }

    @Test
    fun acceptedProfileProgressPreservesScopeAndCompletesExitNonReentrantly() {
        val profile = TestProfilePort()
        val results = mutableListOf<GameplayModuleResultDelivery>()
        val component = component(profile, commandResultSink = results::add)
        component.start()
        results.clear()
        component.advanceUntilMatter()
        profile.calls.clear()

        val ingress = assertIs<GameplayCommandIngressResult.Accepted>(
            component.acceptFromSession(
                component.request(GameplayModuleCommand.ExitRun, sourceRevision = 22, ordinal = 5),
                causalScope = 77,
                causalDepth = 1,
            ),
        )

        assertEquals(GameplayRunPhase.EXITED, component.query(GameplayQuery.GetRunStatus).phase)
        assertFalse(component.query(GameplayQuery.GetRunStatus).profileCommandPending)
        assertEquals(77, profile.calls.single().causalScope)
        assertEquals(2, profile.calls.single().causalDepth)
        val delivery = results.single()
        assertEquals(
            GameplayModuleResult.RunExited(GameplayExitProgressResult.Applied),
            delivery.result,
        )
        assertEquals(77, delivery.resultSource.causalScope)
        assertEquals(4, delivery.resultSource.causalDepth)
        assertTrue(delivery.resultSource.targetRevision.value > ingress.targetRevision.value)
    }

    @Test
    fun verifiedProfilePreacceptRefusalUsesCallerOwnedControlCarrier() {
        val profile = TestProfilePort().apply { mode = ProfileMode.Refuse }
        val results = mutableListOf<GameplayModuleResultDelivery>()
        val component = component(profile, commandResultSink = results::add)
        component.start()
        results.clear()
        component.advanceUntilMatter()

        assertIs<GameplayCommandIngressResult.Accepted>(
            component.acceptFromSession(
                component.request(GameplayModuleCommand.ExitRun),
                causalScope = 31,
                causalDepth = 0,
            ),
        )

        assertEquals(
            GameplayModuleResult.RunExited(GameplayExitProgressResult.NotApplied),
            results.single().result,
        )
        assertFalse(component.query(GameplayQuery.GetRunStatus).profileCommandPending)
    }

    @Test
    fun forgedProfileDeliveryIsRejectedBeforeNucleusCarrierConstruction() {
        val profile = TestProfilePort().apply { mode = ProfileMode.ForgeIdentity }
        val component = component(profile)
        component.start()
        component.advanceUntilMatter()

        assertFailsWith<IllegalStateException> {
            component.acceptFromSession(
                component.request(GameplayModuleCommand.ExitRun),
                causalScope = 35,
                causalDepth = 0,
            )
        }
        assertTrue(component.query(GameplayQuery.GetRunStatus).profileCommandPending)
    }

    @Test
    fun profileResultMustNameTheAcceptedTargetFrameBeforeCarrierConstruction() {
        val profile = TestProfilePort().apply { mode = ProfileMode.ForgeRevision }
        val component = component(profile)
        component.start()
        component.advanceUntilMatter()

        assertFailsWith<IllegalStateException> {
            component.acceptFromSession(
                component.request(GameplayModuleCommand.ExitRun),
                causalScope = 36,
                causalDepth = 0,
            )
        }
        assertTrue(component.query(GameplayQuery.GetRunStatus).profileCommandPending)
    }

    @Test
    fun validProfileResultIsDrainedBeforePostDeliveryInvocationFaultIsRethrown() {
        val profile = TestProfilePort().apply { mode = ProfileMode.DeliverThenThrow }
        val results = mutableListOf<GameplayModuleResultDelivery>()
        val component = component(profile, commandResultSink = results::add)
        component.start()
        results.clear()
        component.advanceUntilMatter()

        assertFailsWith<ProfileInvocationFault> {
            component.acceptFromSession(
                component.request(GameplayModuleCommand.ExitRun),
                causalScope = 37,
                causalDepth = 0,
            )
        }

        assertFalse(component.query(GameplayQuery.GetRunStatus).profileCommandPending)
        assertEquals(
            GameplayModuleResult.RunExited(GameplayExitProgressResult.Applied),
            results.single().result,
        )
    }

    @Test
    fun profileInvocationFaultBeforeResultPreservesPendingRouteWithoutFakeCarrier() {
        val profile = TestProfilePort().apply { mode = ProfileMode.ThrowBeforeResult }
        val results = mutableListOf<GameplayModuleResultDelivery>()
        val component = component(profile, commandResultSink = results::add)
        component.start()
        results.clear()
        component.advanceUntilMatter()

        assertFailsWith<ProfileInvocationFault> {
            component.acceptFromSession(
                component.request(GameplayModuleCommand.ExitRun),
                causalScope = 38,
                causalDepth = 0,
            )
        }

        assertTrue(component.query(GameplayQuery.GetRunStatus).profileCommandPending)
        assertTrue(results.isEmpty())
    }

    @Test
    fun featureAcceptsOneActiveRunAndRefusesEveryFirstNPlusOneReplacement() {
        val profile = TestProfilePort()
        val feature = gameplayFeature(profile)
        val run0 = assertIs<GameComponent>(feature.createRun(RunId(0), commandResultSink = {}))
        profile.resultSink = feature::receiveProfileModuleResult

        assertSame(run0, feature.activeRun())
        assertFailsWith<IllegalStateException> {
            feature.createRun(RunId(1), commandResultSink = {})
        }
        assertSame(run0, feature.activeRun())

        run0.start()
        assertFailsWith<IllegalStateException> {
            feature.createRun(RunId(1), commandResultSink = {})
        }
        assertIs<GameplayCommandIngressResult.Accepted>(
            run0.acceptFromSession(
                run0.request(GameplayModuleCommand.ExitRun),
                causalScope = 2,
                causalDepth = 0,
            ),
        )
        assertEquals(GameplayRunPhase.EXITED, run0.query(GameplayQuery.GetRunStatus).phase)
        assertFailsWith<IllegalArgumentException> {
            feature.createRun(RunId(0), commandResultSink = {})
        }
        val run1 = feature.createRun(RunId(1), commandResultSink = {})
        assertSame(run1, feature.activeRun())

        val pendingProfile = TestProfilePort().apply { mode = ProfileMode.ThrowBeforeResult }
        val pendingFeature = gameplayFeature(pendingProfile)
        val pendingRun = assertIs<GameComponent>(
            pendingFeature.createRun(RunId(0), commandResultSink = {}),
        )
        pendingProfile.resultSink = pendingFeature::receiveProfileModuleResult
        pendingRun.start()
        pendingRun.advanceUntilMatter()
        assertFailsWith<ProfileInvocationFault> {
            pendingRun.acceptFromSession(
                pendingRun.request(GameplayModuleCommand.ExitRun),
                causalScope = 3,
                causalDepth = 0,
            )
        }
        assertTrue(pendingRun.query(GameplayQuery.GetRunStatus).profileCommandPending)
        assertFailsWith<IllegalStateException> {
            pendingFeature.createRun(RunId(1), commandResultSink = {})
        }
        assertSame(pendingRun, pendingFeature.activeRun())
    }

    @Test
    fun featureReusesTheExactCapturedContentAndFrozenSeedAcrossTerminalReplacement() {
        assertEquals(731_991, DEFAULT_GAMEPLAY_SEED)
        val profile = TestProfilePort()
        val feature = gameplayFeature(profile)
        val run0 = assertIs<GameComponent>(feature.createRun(RunId(0), commandResultSink = {}))
        profile.resultSink = feature::receiveProfileModuleResult
        assertSame(SyntheticGameplayContent, run0.stateSnapshot().content)
        run0.start()
        val run0Render = run0.renderSnapshot().renderModel!!
        assertSame(SyntheticGameplayContent, run0Render.content)
        val run0StartFacts = listOf(
            run0Render.coreX,
            run0Render.coreY,
            run0Render.velocityX,
            run0Render.velocityY,
            run0Render.weapon,
            run0Render.morningstarAngle,
            run0Render.enemies,
            run0Render.choices,
        )
        assertIs<GameplayCommandIngressResult.Accepted>(
            run0.acceptFromSession(
                run0.request(GameplayModuleCommand.ExitRun),
                causalScope = 2,
                causalDepth = 0,
            ),
        )

        val run1 = assertIs<GameComponent>(feature.createRun(RunId(1), commandResultSink = {}))
        assertSame(SyntheticGameplayContent, run1.stateSnapshot().content)
        run1.start()
        val run1Render = run1.renderSnapshot().renderModel!!
        assertSame(SyntheticGameplayContent, run1Render.content)
        assertEquals(
            run0StartFacts,
            listOf(
                run1Render.coreX,
                run1Render.coreY,
                run1Render.velocityX,
                run1Render.velocityY,
                run1Render.weapon,
                run1Render.morningstarAngle,
                run1Render.enemies,
                run1Render.choices,
            ),
        )
    }

    @Test
    fun causalBudgetRefusalIsTypedAndPublishesNothing() {
        val component = component()

        val refusal = assertIs<GameplayCommandIngressResult.RejectedBeforeAcceptance>(
            component.acceptFromSession(
                component.request(GameplayModuleCommand.StartRun),
                causalScope = 88,
                causalDepth = 6,
            ),
        )
        val admission = assertIs<GameplayCommandBoundaryResponse.AdmissionFailure>(
            refusal.refusal.boundaryResponse,
        )
        val budget = assertIs<GameplayCommandAdmissionFailureReason.CausalBudgetExceeded>(
            admission.reason,
        )
        assertEquals(88, budget.causalScope)
        assertEquals(GameplayRevision.ZERO, component.stateSnapshot().revision)
    }

    @Test
    fun audioFailureDoesNotRollBackAnAcceptedInteractionFrame() {
        val audio = RecordingGameplayAudioExecutor(throwOnEveryCall = true)
        val component = component(audio = audio)
        component.start()

        val frame = assertIs<GameplayAcceptance.Accepted>(
            component.accept(GameplayInteractionPulse.FrameElapsed.fromValidated(0.1f)),
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
    fun completionDequeAndStaticBoundsRefuseNPlusOneWithoutTruncation() {
        val completions = gameplayCompletionDeque<Int>()
        repeat(8) { value -> assertTrue(completions.tryAddLast(value)) }
        assertFalse(completions.tryAddLast(8))
        assertEquals((0 until 8).toList(), List(8) { completions.removeFirstOrNull() })

        repeat(8, ::requireGameplayCausalDepth)
        assertFailsWith<IllegalStateException> { requireGameplayCausalDepth(8) }
        requireGameplayProfileOutputFanoutBound(1)
        assertFailsWith<IllegalStateException> { requireGameplayProfileOutputFanoutBound(2) }
        requireGameplayCompletionCapacity(1, 1)
        assertFailsWith<IllegalStateException> { requireGameplayCompletionCapacity(0, 1) }

        assertTrue(
            hasGameplayCommandRevisionCapacity(
                GameplayRevision(Long.MAX_VALUE - 1),
                GameplayModuleCommand.StartRun,
            ),
        )
        assertFalse(
            hasGameplayCommandRevisionCapacity(
                GameplayRevision(Long.MAX_VALUE),
                GameplayModuleCommand.StartRun,
            ),
        )
        assertTrue(
            hasGameplayCommandRevisionCapacity(
                GameplayRevision(Long.MAX_VALUE - 2),
                GameplayModuleCommand.ExitRun,
            ),
        )
        assertFalse(
            hasGameplayCommandRevisionCapacity(
                GameplayRevision(Long.MAX_VALUE - 1),
                GameplayModuleCommand.ExitRun,
            ),
        )
    }
}

private fun component(
    profile: TestProfilePort = TestProfilePort(),
    audio: GameplayAudioExecutor = RecordingGameplayAudioExecutor(),
    commandResultSink: (GameplayModuleResultDelivery) -> Unit = {},
): GameComponent {
    val component = GameComponent.create(
        runId = RunId(31),
        content = SyntheticGameplayContent,
        profilePort = profile,
        audioExecutor = audio,
        commandResultSink = commandResultSink,
        seed = DEFAULT_GAMEPLAY_SEED,
    )
    profile.resultSink = component::receiveProfileModuleResult
    return component
}

private fun GameComponent.request(
    command: GameplayModuleCommand,
    sourceRevision: Long = stateSnapshot().revision.value,
    ordinal: Int = 0,
): GameplayModuleCommandRequest {
    val handle = GameplaySemanticHandle(GameplayCommandSource.LocalSession, sourceRevision, ordinal)
    return GameplayModuleCommandRequest(handle, ordinal, instanceId, command)
}

private fun GameComponent.start() {
    assertIs<GameplayCommandIngressResult.Accepted>(
        acceptFromSession(request(GameplayModuleCommand.StartRun), causalScope = 1, causalDepth = 0),
    )
}

private fun GameComponent.advanceUntilMatter() {
    repeat(1_200) { frameIndex ->
        val render = checkNotNull(renderSnapshot().renderModel)
        if (render.runMatter > 0L) return
        check(query(GameplayQuery.GetRunStatus).phase == GameplayRunPhase.RUNNING) {
            "Run stopped before earning progress at frame $frameIndex"
        }
        assertIs<GameplayAcceptance.Accepted>(
            accept(GameplayInteractionPulse.FrameElapsed.fromValidated(0.1f)),
        )
    }
    error("Run earned no progress within the deterministic frame budget")
}

private enum class ProfileMode {
    Accept,
    Refuse,
    ForgeIdentity,
    ForgeRevision,
    DeliverThenThrow,
    ThrowBeforeResult,
}

private data class ProfileCall(
    val request: ProfileModuleCommandRequest,
    val causalScope: Long,
    val causalDepth: Int,
)

private class TestProfilePort : GameplayProfileRoute {
    override val instanceId = LOCAL_PROFILE_INSTANCE_ID
    var snapshot: GameplayProfileSnapshot = PlayerProfile().toGameplaySnapshot()
    var bootstrapResult: ProfileRunBootstrapResult = ProfileRunBootstrapResult.Ready(snapshot)
    var mode: ProfileMode = ProfileMode.Accept
    var resultSink: (ProfileModuleResultDelivery) -> Unit = {}
    val calls = mutableListOf<ProfileCall>()
    var bootstrapReadCount: Int = 0
    var preferencesReadCount: Int = 0
    private var revision = ProfileRevision(10)

    override fun acceptFromGameplay(
        request: ProfileModuleCommandRequest,
        causalScope: Long,
        causalDepth: Int,
    ): ProfileCommandIngressResult {
        calls += ProfileCall(request, causalScope, causalDepth)
        val commandSource = ProfileCommandSourceToken(
            request.semanticHandle,
            request.targetInstance,
            causalScope,
            causalDepth,
        )
        if (mode == ProfileMode.ThrowBeforeResult) throw ProfileInvocationFault()
        return when (mode) {
            ProfileMode.Refuse -> ProfileCommandIngressResult.RejectedBeforeAcceptance(
                ProfileCommandRefusalEvidence(
                    commandSource = commandSource,
                    effectiveProtocolIdentity = ProfileEffectiveProtocolIdentity.GAMEPLAY_PROGRESS,
                    boundaryResponse = ProfileCommandBoundaryResponse.DecisionRejected(
                        ProfileRejection.NoChange,
                    ),
                    targetBoundaryProvenance = ProfileTargetBoundaryProvenance(
                        instanceId,
                        ProfileEffectiveProtocolIdentity.GAMEPLAY_PROGRESS,
                    ),
                ),
            )
            ProfileMode.Accept,
            ProfileMode.ForgeIdentity,
            ProfileMode.ForgeRevision,
            ProfileMode.DeliverThenThrow,
            -> {
                revision = ProfileRevision(revision.value + 1)
                resultSink(
                    ProfileModuleResultDelivery(
                        commandSource = commandSource,
                        resultSource = ProfileResultSourceToken(
                            semanticHandle = request.semanticHandle,
                            targetInstance = instanceId,
                            targetRevision = if (mode == ProfileMode.ForgeRevision) {
                                ProfileRevision(revision.value + 1)
                            } else {
                                revision
                            },
                            sourceOrdinal = 1,
                            causalScope = causalScope,
                            causalDepth = causalDepth + 1,
                        ),
                        effectiveProtocolIdentity = if (mode == ProfileMode.ForgeIdentity) {
                            ProfileEffectiveProtocolIdentity.SESSION_MUTE
                        } else {
                            ProfileEffectiveProtocolIdentity.GAMEPLAY_PROGRESS
                        },
                        result = ProfileModuleResult.GameplayProgressApplied,
                        issuerProvenance = ProfileResultIssuerProvenance.LOCAL_PROFILE_STATIC_BINDING,
                    ),
                )
                if (mode == ProfileMode.DeliverThenThrow) throw ProfileInvocationFault()
                ProfileCommandIngressResult.Accepted(instanceId, revision)
            }
            ProfileMode.ThrowBeforeResult -> error("handled before result construction")
        }
    }

    override fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection {
        bootstrapReadCount++
        val result = if (bootstrapResult is ProfileRunBootstrapResult.Ready) {
            ProfileRunBootstrapResult.Ready(snapshot)
        } else {
            bootstrapResult
        }
        return RunBootstrapProjection(instanceId, revision, result)
    }

    override fun query(query: ProfileQuery.GetPreferences): PreferencesProjection {
        preferencesReadCount++
        return PreferencesProjection(instanceId, revision, snapshot.preferences)
    }

}

private fun PlayerProfile.toGameplaySnapshot(): GameplayProfileSnapshot = GameplayProfileSnapshot(
    preferences,
    economy,
    loadout,
    labProgress,
    collection,
    rebirthProgress,
)

private fun gameplayFeature(profile: TestProfilePort): DefaultGameplayFeature =
    DefaultGameplayFeature(
        gameplayContent = SyntheticGameplayContent,
        profilePort = profile,
        audioService = NoOpAudioService,
    )

private class RecordingGameplayAudioExecutor(
    private val throwOnEveryCall: Boolean = false,
) : GameplayAudioExecutor {
    val frames = mutableListOf<Pair<Float, List<GameplayAudioCue>>>()
    var unlockCount = 0

    override fun advance(realDeltaSeconds: Float, cues: ImmutableList<GameplayAudioCue>) {
        frames += realDeltaSeconds to cues.toList()
        if (throwOnEveryCall) throw AudioResourceFault()
    }

    override fun ensureUnlocked() {
        unlockCount++
        if (throwOnEveryCall) throw AudioResourceFault()
    }
}

private class AudioResourceFault : RuntimeException()
private class ProfileInvocationFault : RuntimeException()

private object NoOpAudioService : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
