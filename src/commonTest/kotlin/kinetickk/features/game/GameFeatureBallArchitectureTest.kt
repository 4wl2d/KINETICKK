// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.game

import kinetickk.application.runtime.Accepted
import kinetickk.application.runtime.AdmissionFailure
import kinetickk.application.runtime.Rejected
import kinetickk.features.game.nucleus.CoreShape
import kinetickk.features.game.nucleus.GamePhase
import kinetickk.features.game.nucleus.GameSettings
import kinetickk.features.game.nucleus.MetaUpgradeId
import kinetickk.features.game.nucleus.MutableGameState
import kinetickk.features.game.nucleus.StoredProgress
import kinetickk.features.game.nucleus.UiScreen
import kinetickk.features.game.nucleus.WeaponId
import kinetickk.features.game.nucleus.projection.EnemyProjection
import kinetickk.features.game.nucleus.projection.GameProjection
import kinetickk.features.game.nucleus.protocol.BrakeSource
import kinetickk.features.game.nucleus.protocol.EffectRequest
import kinetickk.features.game.nucleus.protocol.GameDecisionContext
import kinetickk.features.game.nucleus.protocol.GameEffect
import kinetickk.features.game.nucleus.protocol.GameFact
import kinetickk.features.game.nucleus.protocol.GameIntent
import kinetickk.features.game.nucleus.protocol.GameOutputKind
import kinetickk.features.game.nucleus.protocol.GameProjectionPayload
import kinetickk.features.game.nucleus.protocol.GameQuery
import kinetickk.features.game.nucleus.protocol.GameQueryResult
import kinetickk.features.game.nucleus.protocol.GameRejection
import kinetickk.features.game.nucleus.protocol.OperationId
import kinetickk.features.game.nucleus.protocol.PersistenceStatus
import kinetickk.features.game.nucleus.protocol.ProgressPersistenceUnknownReason
import kinetickk.features.game.nucleus.protocol.ProgressProvider
import kinetickk.features.game.nucleus.protocol.ProjectionOutput
import kinetickk.features.game.nucleus.protocol.SemanticOutput
import kinetickk.features.game.nucleus.protocol.SoundCue
import kinetickk.features.game.nucleus.protocol.VisualFxCue
import kinetickk.features.game.nucleus.protocol.VisualFxCueLimits
import kinetickk.features.game.nucleus.read.ConsistencyStamp
import kinetickk.features.game.nucleus.read.ReadContext
import kinetickk.features.game.nucleus.read.ReadResult
import kinetickk.features.game.nucleus.transition.GameBallState
import kinetickk.features.game.nucleus.transition.GameNucleus
import kinetickk.features.game.nucleus.transition.initialGameBallState
import kinetickk.features.game.resources.audio.AudioResource
import kinetickk.features.game.resources.progress.ProgressLoadResult
import kinetickk.features.game.resources.progress.ProgressPersistResult
import kinetickk.features.game.resources.progress.ProgressProviderId
import kinetickk.features.game.resources.progress.ProgressResourceFailure
import kinetickk.features.game.resources.progress.ProgressStore
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.ImmutableSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameFeatureBallArchitectureTest {
    @Test
    fun sameSeedAndTypedIntentTraceProduceTheSameAcceptedProjection() {
        val trace = listOf(
            GameIntent.ViewportChanged(width = 1_280f, height = 720f, density = 1.5f),
            GameIntent.RunStartRequested,
            GameIntent.PointerMoved(x = 1_100f, y = 240f),
            GameIntent.FrameElapsed(0.1f),
            GameIntent.BrakeChanged(BrakeSource.KEYBOARD, active = true),
            GameIntent.DashRequested,
            GameIntent.FrameElapsed(0.1f),
            GameIntent.BrakeChanged(BrakeSource.KEYBOARD, active = false),
        )
        val first = ball(seed = 91_337)
        val second = ball(seed = 91_337)

        trace.forEach { intent ->
            assertIs<GameDispatchResult.Committed>(first.dispatch(intent))
            assertIs<GameDispatchResult.Committed>(second.dispatch(intent))
        }

        assertEquals(first.projectionSnapshot(), second.projectionSnapshot())
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun acceptedProjectionUsesActuallyImmutableCollectionsAndDoesNotChangeLater() {
        val ball = ball(seed = 17)
        assertIs<GameDispatchResult.Committed>(ball.dispatch(GameIntent.RunStartRequested))
        val retainedRead = ball.projectionRead()
        val retainedProjection = retainedRead.payload
        val retainedSnapshot = retainedRead.testSnapshot()
        val retainedEnemyList = retainedProjection.enemies

        assertIs<ImmutableList<EnemyProjection>>(retainedProjection.enemies)
        assertIs<ImmutableSet<WeaponId>>(retainedProjection.unlockedWeapons)
        assertFalse((retainedProjection.enemies as Any) is MutableList<*>)
        assertFalse((retainedProjection.unlockedWeapons as Any) is MutableSet<*>)
        assertFailsWith<ClassCastException> {
            (retainedProjection.enemies as Any) as MutableList<EnemyProjection>
        }
        assertFailsWith<ClassCastException> {
            (retainedProjection.unlockedWeapons as Any) as MutableSet<WeaponId>
        }

        repeat(8) {
            assertIs<GameDispatchResult.Committed>(ball.dispatch(GameIntent.FrameElapsed(0.1f)))
        }

        assertEquals(retainedSnapshot, retainedRead.testSnapshot())
        assertSame(retainedEnemyList, retainedProjection.enemies)
        assertNotEquals(
            retainedRead.consistencyStamp.commitRevision,
            ball.projectionRead().consistencyStamp.commitRevision,
        )
    }

    @Test
    fun forgedNumericIntentsFailTheSecondQuarantineWithoutMutation() {
        val audio = RecordingAudioResource()
        val progress = RecordingProgressStore()
        val ball = ball(audio = audio, progress = progress)
        val before = ball.projectionSnapshot()

        listOf(
            GameIntent.FrameElapsed(Float.NaN),
            GameIntent.FrameElapsed(-0.001f),
            GameIntent.FrameElapsed(1.001f),
            GameIntent.ViewportChanged(Float.POSITIVE_INFINITY, 720f, 1f),
            GameIntent.ViewportChanged(32_769f, 720f, 1f),
            GameIntent.ViewportChanged(1_280f, 720f, 0.49f),
            GameIntent.ViewportChanged(1_280f, 720f, 8.01f),
            GameIntent.PointerMoved(-1f, 20f),
            GameIntent.PointerMoved(1_281f, 20f),
            GameIntent.PointerPressed(20f, 721f),
        ).forEach { forged ->
            val rejection = assertIs<GameDispatchResult.DecisionRejected>(ball.dispatch(forged))
            assertIs<GameRejection.InvalidInput>(rejection.reason)
            assertEquals(before, ball.projectionSnapshot())
        }

        assertEquals(0, audio.advanceCalls)
        assertEquals(0, progress.persisted.size)
    }

    @Test
    fun completeFrameIsVisibleBeforeResourcesAndCompletionUsesTheNextCommit() {
        val audioObservations = mutableListOf<ProjectionSnapshot>()
        val progressObservations = mutableListOf<Pair<ProjectionSnapshot, PersistenceStatus>>()
        val audio = RecordingAudioResource()
        val progress = RecordingProgressStore()
        lateinit var ball: GameFeatureBall
        audio.onAdvance = { audioObservations += ball.projectionSnapshot() }
        progress.onPersist = {
            progressObservations += ball.projectionSnapshot() to ball.persistenceRead().payload
        }
        ball = ball(audio = audio, progress = progress)

        val start = assertIs<GameDispatchResult.Committed>(ball.dispatch(GameIntent.RunStartRequested))
        assertEquals(1uL, start.sourceCommitRevision)
        assertEquals(1uL, start.projectionRead.consistencyStamp.commitRevision)
        assertEquals(1uL, audioObservations.single().stamp.commitRevision)
        assertEquals(GamePhase.RUNNING, audioObservations.single().phase)

        val mute = assertIs<GameDispatchResult.Committed>(ball.dispatch(GameIntent.MuteToggled))
        val (projectionDuringPersist, statusDuringPersist) = progressObservations.single()
        val pending = assertIs<PersistenceStatus.Pending>(statusDuringPersist)

        assertEquals(2uL, mute.sourceCommitRevision)
        assertEquals(2uL, projectionDuringPersist.stamp.commitRevision)
        assertEquals(GameOutputKind.PERSIST_PROGRESS, pending.handle.outputKind)
        assertEquals("generation-1", pending.handle.localOrdinalOrName)
        assertEquals(3uL, mute.projectionRead.consistencyStamp.commitRevision)
        assertEquals(PersistenceStatus.Persisted(pending.handle), ball.persistenceRead().payload)
        assertEquals(GameContinuationStatus.Idle, mute.continuationStatus)
    }

    @Test
    fun persistenceResourceResultsKeepTheExactFullHandleAndTypedOutcome() {
        val persistedBall = ball(progress = RecordingProgressStore(ProgressPersistResult.Persisted))
        assertIs<GameDispatchResult.Committed>(persistedBall.dispatch(GameIntent.MuteToggled))
        val persisted = assertIs<PersistenceStatus.Persisted>(persistedBall.persistenceRead().payload)
        assertEquals(GameOutputKind.PERSIST_PROGRESS, persisted.handle.outputKind)
        assertEquals("generation-1", persisted.handle.localOrdinalOrName)

        val unknownBall = ball(
            progress = RecordingProgressStore(
                ProgressPersistResult.OutcomeUnknown(
                    ProgressResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
                ),
            ),
        )
        assertIs<GameDispatchResult.Committed>(unknownBall.dispatch(GameIntent.MuteToggled))
        val unknown = assertIs<PersistenceStatus.OutcomeUnknown>(unknownBall.persistenceRead().payload)
        assertEquals(GameOutputKind.PERSIST_PROGRESS, unknown.handle.outputKind)
        assertEquals(
            ProgressPersistenceUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
            unknown.reason,
        )
    }

    @Test
    fun throwingResourcesAreContainedAfterTheAcceptedFrame() {
        val audioBall = ball(audio = RecordingAudioResource(throwOnAdvance = true))
        val audioResult = assertIs<GameDispatchResult.Committed>(
            audioBall.dispatch(GameIntent.RunStartRequested),
        )
        assertEquals(GamePhase.RUNNING, audioResult.projectionRead.payload.phase)
        assertEquals(1uL, audioResult.projectionRead.consistencyStamp.commitRevision)

        val progressBall = ball(progress = RecordingProgressStore(throwOnPersist = true))
        val progressResult = assertIs<GameDispatchResult.Committed>(
            progressBall.dispatch(GameIntent.MuteToggled),
        )
        val unknown = assertIs<PersistenceStatus.OutcomeUnknown>(progressBall.persistenceRead().payload)
        assertEquals(
            ProgressPersistenceUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
            unknown.reason,
        )
        assertEquals(2uL, progressResult.projectionRead.consistencyStamp.commitRevision)
        assertEquals(GameContinuationStatus.Idle, progressResult.continuationStatus)

        val providerBall = ball(
            progress = RecordingProgressStore(throwOnProviderIdentity = true),
        )
        assertIs<GameDispatchResult.Committed>(providerBall.dispatch(GameIntent.MuteToggled))
        val providerUnknown = assertIs<PersistenceStatus.OutcomeUnknown>(
            providerBall.persistenceRead().payload,
        )
        assertEquals(
            ProgressPersistenceUnknownReason.PROVIDER_READ_FAILED,
            providerUnknown.reason,
        )
    }

    @Test
    fun successfulCanonicalReadHasTheExactStampAndDoesNotAdvanceRevision() {
        val ball = ball()
        val initial = ball.projectionRead()
        assertEquals(
            ConsistencyStamp(
                ballInstanceId = GameProjection.BALL_INSTANCE_ID,
                commitRevision = 0uL,
                stateSchemaVersion = GameProjection.STATE_SCHEMA_VERSION,
            ),
            initial.consistencyStamp,
        )

        val repeated = ball.projectionRead()
        val persistence = ball.persistenceRead()
        assertSame(initial.payload, repeated.payload)
        assertEquals(initial.consistencyStamp, repeated.consistencyStamp)
        assertEquals(initial.consistencyStamp, persistence.consistencyStamp)
        assertEquals(PersistenceStatus.NeverRequested, persistence.payload)

        val selectedContext = ReadContext(GameProjection.PROTOCOL_VERSION, actorContext = null)
        assertNull(selectedContext.actorContext)
        assertFailsWith<IllegalArgumentException> {
            ball.read(GameQuery.GetGameProjection, ReadContext("unsupported"))
        }
        assertEquals(0uL, ball.projectionRead().consistencyStamp.commitRevision)

        val accepted = assertIs<GameDispatchResult.Committed>(
            ball.dispatch(GameIntent.PointerMoved(640f, 360f)),
        )
        assertEquals(1uL, accepted.projectionRead.consistencyStamp.commitRevision)
        assertEquals(1uL, ball.persistenceRead().consistencyStamp.commitRevision)
    }

    @Test
    fun visualFxOutputIsAnUnstampedInteractionAttachmentNotPartOfTheGameRead() {
        val ball = ball()

        val committed = assertIs<GameDispatchResult.Committed>(
            ball.dispatch(GameIntent.RunStartRequested),
        )

        assertIs<ImmutableList<VisualFxCue>>(committed.visualFxCues)
        assertTrue(committed.visualFxCues.any { it == VisualFxCue.ClearAll })
        assertEquals(1uL, committed.projectionRead.consistencyStamp.commitRevision)
    }

    @Test
    fun everyNucleusOutputUsesCanonicalContiguousEnvelopesAndDeclaredLimits() {
        val limits = GameFeatureBall.LIMITS
        assertEquals(4_096L, limits.maxInputBytes)
        assertEquals(16_777_216L, limits.maxStateBytes)
        assertEquals(2_048, limits.maxCollectionItems)
        assertEquals(VisualFxCueLimits.MAX_CUES_PER_PROJECTION, limits.maxCollectionItems)
        assertEquals(3, limits.maxOutputsPerDecision)
        assertEquals(2, limits.maxEffectsPerDecision)
        assertEquals(0, limits.maxCommandsPerDecision)
        assertEquals(2, limits.maxCausalDepth)
        assertEquals(0, limits.maxRetriesPerOperation)
        assertEquals(48, limits.maxTransitionSteps)

        val nucleus = GameNucleus()
        val initial = initialGameBallState(seed = 4, bootstrapProgress = null)
        representativeIntents().forEachIndexed { index, intent ->
            val operationId = OperationId((index + 1).toULong())
            val accepted = assertIs<Accepted<GameBallState, SemanticOutput>>(
                nucleus.decide(initial, intent, context(operationId)),
                "Expected a valid representative $intent decision",
            )
            assertTrue(accepted.decision.outputs.size <= limits.maxOutputsPerDecision)
            assertTrue(
                accepted.decision.outputs.count { it is EffectRequest } <=
                    limits.maxEffectsPerDecision,
            )
            assertTrue(accepted.decision.nextState.transitionSteps <= limits.maxTransitionSteps)
            accepted.decision.outputs.forEachIndexed { outputIndex, output ->
                assertEquals(outputIndex.toUInt(), output.sourceOrdinal)
                assertEquals(operationId, output.semanticHandle.operationId)
                when (output) {
                    is ProjectionOutput -> {
                        assertEquals(GameOutputKind.GAME_PROJECTION_CHANGED, output.semanticHandle.outputKind)
                        assertIs<GameProjectionPayload.GameProjectionChanged>(output.payload)
                    }
                    is EffectRequest -> assertEquals(
                        output.semanticHandle.outputKind,
                        when (output.payload) {
                            is GameEffect.AdvanceAudio -> GameOutputKind.ADVANCE_AUDIO
                            GameEffect.EnsureAudioUnlocked -> GameOutputKind.ENSURE_AUDIO_UNLOCKED
                            is GameEffect.PersistProgress -> GameOutputKind.PERSIST_PROGRESS
                        },
                    )
                }
            }
        }
    }

    @Test
    fun everyDeclaredSovereignCollectionCapAcceptsNAndRejectsNPlusOne() {
        val cases = listOf(
            DomainLimitCase("enemies", MutableGameState.MAX_ENEMIES) { state ->
                state.addEnemyForTesting(0f, 0f)
            },
            DomainLimitCase("projectiles", MutableGameState.MAX_PROJECTILES) { state ->
                state.addProjectileForTesting()
            },
            DomainLimitCase("pickups", MutableGameState.MAX_PICKUPS) { state ->
                state.dropRelicForTesting()
            },
            DomainLimitCase("trail", MutableGameState.MAX_TRAIL_POINTS) { state ->
                state.addTrailPointForTesting()
            },
            DomainLimitCase("delayedRelicHits", MutableGameState.MAX_DELAYED_RELIC_HITS) { state ->
                state.addDelayedRelicHitForTesting()
            },
        )

        cases.forEach { case ->
            val atLimitState = initialGameBallState(seed = 51, bootstrapProgress = null)
            repeat(case.maximum) { case.add(atLimitState.model) }
            val atLimitBall = GameFeatureBall(
                initialState = atLimitState,
                progressStore = RecordingProgressStore(),
                audioResource = RecordingAudioResource(),
            )
            assertIs<GameDispatchResult.Committed>(
                atLimitBall.dispatch(GameIntent.PointerMoved(640f, 360f)),
                "${case.name} must accept N",
            )

            val overLimitState = initialGameBallState(seed = 52, bootstrapProgress = null)
            repeat(case.maximum + 1) { case.add(overLimitState.model) }
            val overLimitBall = GameFeatureBall(
                initialState = overLimitState,
                progressStore = RecordingProgressStore(),
                audioResource = RecordingAudioResource(),
            )
            val rejection = assertIs<GameDispatchResult.AdmissionRejected>(
                overLimitBall.dispatch(GameIntent.PointerMoved(640f, 360f)),
                "${case.name} must reject N+1",
            )
            assertEquals(
                AdmissionFailure.AdditionalLimitExceeded(
                    name = case.name,
                    actual = (case.maximum + 1).toLong(),
                    maximum = case.maximum.toLong(),
                ),
                rejection.reason,
            )
            assertEquals(0uL, overLimitBall.projectionRead().consistencyStamp.commitRevision)

            atLimitBall.close()
            overLimitBall.close()
        }
    }

    @Test
    fun fixedStepTransitionWorkRemainsWithinTheFortyEightStepCap() {
        val nucleus = GameNucleus()
        val running = assertIs<Accepted<GameBallState, SemanticOutput>>(
            nucleus.decide(
                initialGameBallState(
                    seed = 8,
                    bootstrapProgress = StoredProgress(
                        settings = GameSettings(simulationSpeed = 2f),
                    ),
                ),
                GameIntent.RunStartRequested,
                context(OperationId(1uL)),
            ),
        ).decision.nextState

        val largeFrame = assertIs<Accepted<GameBallState, SemanticOutput>>(
            nucleus.decide(
                running,
                GameIntent.FrameElapsed(1f),
                context(OperationId(2uL)),
            ),
        ).decision

        assertTrue(largeFrame.nextState.transitionSteps > 0)
        assertTrue(largeFrame.nextState.transitionSteps <= GameFeatureBall.LIMITS.maxTransitionSteps)
        assertTrue(
            largeFrame.nextState.model.elapsed <=
                GameFeatureBall.LIMITS.maxTransitionSteps * GameProjection.FIXED_STEP,
        )
    }

    @Test
    fun nucleusAcceptsExactPersistenceFactsAndRejectsStaleHandlesWithoutMutation() {
        val nucleus = GameNucleus()
        val initial = initialGameBallState(seed = 33, bootstrapProgress = null)
        val operationId = OperationId(41uL)
        val pendingDecision = assertIs<Accepted<GameBallState, SemanticOutput>>(
            nucleus.decide(initial, GameIntent.MuteToggled, context(operationId)),
        ).decision
        val pending = assertIs<PersistenceStatus.Pending>(pendingDecision.nextState.persistenceStatus)
        val output = pendingDecision.outputs.filterIsInstance<EffectRequest>()
            .single { it.payload is GameEffect.PersistProgress }
        assertEquals(output.semanticHandle, pending.handle)

        val beforeStale = pendingDecision.nextState.model.toProjection().coreX
        val wrongHandle = pending.handle.copy(localOrdinalOrName = "wrong-generation")
        val stale = assertIs<Rejected>(
            nucleus.decide(
                pendingDecision.nextState,
                GameFact.ProgressPersisted(wrongHandle, ProgressProvider.PLATFORM_LOCAL),
                context(operationId, causalDepth = 2),
            ),
        )
        val rejection = assertIs<GameRejection.StaleFact>(stale.rejection)
        assertEquals(wrongHandle, rejection.received)
        assertEquals(pending.handle, rejection.expected)
        assertEquals(
            beforeStale,
            pendingDecision.nextState.model.toProjection().coreX,
        )

        val persistedDecision = assertIs<Accepted<GameBallState, SemanticOutput>>(
            nucleus.decide(
                pendingDecision.nextState,
                GameFact.ProgressPersisted(pending.handle, ProgressProvider.PLATFORM_LOCAL),
                context(operationId, causalDepth = 2),
            ),
        ).decision
        assertEquals(PersistenceStatus.Persisted(pending.handle), persistedDecision.nextState.persistenceStatus)
        assertNull(persistedDecision.nextState.outstandingPersistence)
        assertEquals(1, persistedDecision.outputs.size)
        assertIs<ProjectionOutput>(persistedDecision.outputs.single())

        val unknownDecision = assertIs<Accepted<GameBallState, SemanticOutput>>(
            nucleus.decide(
                pendingDecision.nextState,
                GameFact.ProgressPersistenceOutcomeUnknown(
                    handle = pending.handle,
                    provider = ProgressProvider.PLATFORM_LOCAL,
                    reason = ProgressPersistenceUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
                ),
                context(operationId, causalDepth = 2),
            ),
        ).decision
        assertEquals(
            PersistenceStatus.OutcomeUnknown(
                pending.handle,
                ProgressPersistenceUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
            ),
            unknownDecision.nextState.persistenceStatus,
        )
        assertNull(unknownDecision.nextState.outstandingPersistence)
    }

    @Test
    fun nucleusDecisionRequiresOnlyExplicitStatePulseContextAndReservedIdentity() {
        val state = initialGameBallState(seed = 7, bootstrapProgress = null)
        val operationId = OperationId(77uL)
        val result = GameNucleus().decide(
            state = state,
            pulse = GameIntent.PointerMoved(320f, 180f),
            context = context(operationId),
        )

        val accepted = assertIs<Accepted<GameBallState, SemanticOutput>>(result)
        val projection = assertIs<ProjectionOutput>(accepted.decision.outputs.single())
        assertEquals(operationId, projection.semanticHandle.operationId)
        assertEquals(320f, accepted.decision.nextState.model.pointerX)
        assertEquals(180f, accepted.decision.nextState.model.pointerY)

        val unreserved = GameNucleus().decide(
            state,
            GameIntent.PointerReleased,
            context(OperationId(0uL)),
        )
        assertIs<Rejected>(unreserved)
    }

    private fun representativeIntents(): List<GameIntent> = listOf(
        GameIntent.FrameElapsed(0.1f),
        GameIntent.ViewportChanged(1_280f, 720f, 1f),
        GameIntent.PointerMoved(640f, 360f),
        GameIntent.PointerPressed(640f, 360f),
        GameIntent.PointerReleased,
        GameIntent.BrakeChanged(BrakeSource.KEYBOARD, true),
        GameIntent.DashRequested,
        GameIntent.PauseToggled,
        GameIntent.EscapeRequested,
        GameIntent.ScreenOpenRequested(UiScreen.SETTINGS),
        GameIntent.MuteToggled,
        GameIntent.ChoiceSelected(0),
        GameIntent.ChoicesRerolled,
        GameIntent.EnterPressed,
        GameIntent.RunStartRequested,
        GameIntent.ReturnToMenuRequested,
        GameIntent.RebirthRequested,
        GameIntent.CoreShapeSelected(CoreShape.ORB),
        GameIntent.MetaUpgradePurchaseRequested(MetaUpgradeId.entries.first()),
        GameIntent.WeaponPurchaseOrEquipRequested(WeaponId.entries.first()),
        GameIntent.UserGestureObserved,
    )

    private data class DomainLimitCase(
        val name: String,
        val maximum: Int,
        val add: (MutableGameState) -> Unit,
    )

    private fun context(
        operationId: OperationId,
        causalDepth: Int = 1,
    ): GameDecisionContext = GameDecisionContext(
        operationId = operationId,
        causalBudgetScope = operationId,
        causalDepth = causalDepth,
    )

    private fun ball(
        seed: Int = 731_991,
        progress: RecordingProgressStore = RecordingProgressStore(),
        audio: RecordingAudioResource = RecordingAudioResource(),
    ): GameFeatureBall = GameFeatureBall.create(
        progressStore = progress,
        audioResource = audio,
        bootstrapProgress = null,
        seed = seed,
        initialMatter = 0,
    )
}

private fun GameFeatureBall.projectionRead(): ReadResult<GameProjection> =
    assertIs<GameQueryResult.Projection>(query(GameQuery.GetGameProjection)).value

private fun GameFeatureBall.persistenceRead(): ReadResult<PersistenceStatus> =
    assertIs<GameQueryResult.Persistence>(query(GameQuery.GetPersistenceStatus)).value

private fun GameFeatureBall.projectionSnapshot(): ProjectionSnapshot = projectionRead().testSnapshot()

private class RecordingProgressStore(
    private val result: ProgressPersistResult = ProgressPersistResult.Persisted,
    private val throwOnPersist: Boolean = false,
    private val throwOnProviderIdentity: Boolean = false,
) : ProgressStore {
    override val providerId: ProgressProviderId
        get() {
            if (throwOnProviderIdentity) error("private provider identity detail")
            return ProgressProviderId.PLATFORM_LOCAL
        }
    val persisted = mutableListOf<StoredProgress>()
    var onPersist: (StoredProgress) -> Unit = {}

    override fun load(): ProgressLoadResult = ProgressLoadResult.NotFound

    override fun persist(progress: StoredProgress): ProgressPersistResult {
        persisted += progress
        onPersist(progress)
        if (throwOnPersist) error("private progress provider detail")
        return result
    }
}

private class RecordingAudioResource(
    private val throwOnAdvance: Boolean = false,
) : AudioResource {
    var advanceCalls: Int = 0
    var unlockCalls: Int = 0
    var closeCalls: Int = 0
    var onAdvance: (List<SoundCue>) -> Unit = {}

    override fun advance(settings: GameSettings, realDelta: Float, cues: List<SoundCue>) {
        advanceCalls++
        onAdvance(cues)
        if (throwOnAdvance) error("private audio provider detail")
    }

    override fun ensureUnlocked() {
        unlockCalls++
    }

    override fun close() {
        closeCalls++
    }
}

private data class ProjectionSnapshot(
    val stamp: ConsistencyStamp,
    val phase: GamePhase,
    val screen: UiScreen,
    val settings: GameSettings,
    val coreX: Float,
    val coreY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val pointerX: Float,
    val pointerY: Float,
    val elapsed: Float,
    val hp: Float,
    val shield: Float,
    val level: Int,
    val data: Int,
    val keys: Int,
    val kills: Int,
    val combo: Int,
    val runMatter: Long,
    val totalMatter: Long,
    val message: String,
    val enemies: List<Any>,
    val projectiles: List<Any>,
    val pickups: List<Any>,
    val trail: List<Any>,
    val weaponNodes: List<Any>,
    val weaponOrbitals: List<Any>,
)

private fun ReadResult<GameProjection>.testSnapshot(): ProjectionSnapshot = payload.let { projection ->
    ProjectionSnapshot(
        stamp = consistencyStamp,
        phase = projection.phase,
        screen = projection.screen,
        settings = projection.settings,
        coreX = projection.coreX,
        coreY = projection.coreY,
        velocityX = projection.velocityX,
        velocityY = projection.velocityY,
        pointerX = projection.pointerX,
        pointerY = projection.pointerY,
        elapsed = projection.elapsed,
        hp = projection.hp,
        shield = projection.shield,
        level = projection.level,
        data = projection.data,
        keys = projection.keys,
        kills = projection.kills,
        combo = projection.combo,
        runMatter = projection.runMatter,
        totalMatter = projection.totalMatter,
        message = projection.message,
        enemies = projection.enemies.toList(),
        projectiles = projection.projectiles.toList(),
        pickups = projection.pickups.toList(),
        trail = projection.trail.toList(),
        weaponNodes = projection.weaponNodes.toList(),
        weaponOrbitals = projection.weaponOrbitals.toList(),
    )
}
