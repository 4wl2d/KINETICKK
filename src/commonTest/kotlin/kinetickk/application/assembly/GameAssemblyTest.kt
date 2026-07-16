// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.application.assembly

import kinetickk.features.audio.AudioFeatureBall
import kinetickk.features.audio.resources.tone.NumericTonePlayer
import kinetickk.features.game.GameDispatchResult
import kinetickk.features.game.nucleus.CoreShape
import kinetickk.features.game.nucleus.GameSettings
import kinetickk.features.game.nucleus.GamePhase
import kinetickk.features.game.nucleus.projection.GameProjection
import kinetickk.features.game.nucleus.protocol.CommandRequest
import kinetickk.features.game.nucleus.protocol.GameCommand
import kinetickk.features.game.nucleus.protocol.GameIntent
import kinetickk.features.game.nucleus.protocol.GameOutputKind
import kinetickk.features.game.nucleus.protocol.GameRunStartContract
import kinetickk.features.game.nucleus.protocol.OperationId
import kinetickk.features.game.nucleus.protocol.ProfileChange
import kinetickk.features.game.nucleus.protocol.SemanticHandle
import kinetickk.features.profile.nucleus.domain.CoreShape as ProfileCoreShape
import kinetickk.features.profile.nucleus.domain.ItemCatalogFacts
import kinetickk.features.profile.nucleus.domain.RebirthProgression as ProfileRebirthProgression
import kinetickk.features.profile.nucleus.protocol.ProfileApplyRunOutcomeContract
import kinetickk.features.profile.nucleus.protocol.ProfileAdvanceRebirthContract
import kinetickk.features.settings.nucleus.domain.DamageNumberFormat
import kinetickk.features.settings.nucleus.domain.DamageNumberSize
import kinetickk.features.settings.nucleus.domain.ParticleDensity
import kinetickk.features.settings.nucleus.domain.SettingsValues
import kinetickk.flows.persistence.ProgressPersistenceStatus
import kinetickk.flows.persistence.ProgressPersistenceSchema
import kinetickk.flows.persistence.model.PersistedProgress
import kinetickk.flows.persistence.resources.ProgressLoadRejection
import kinetickk.flows.persistence.resources.ProgressLoadResult
import kinetickk.flows.persistence.resources.ProgressPersistResult
import kinetickk.flows.persistence.resources.ProgressResourceFailure
import kinetickk.flows.persistence.resources.ProgressStore
import kinetickk.flows.rebirth.nucleus.protocol.RebirthStatus
import kinetickk.flows.rebirth.nucleus.protocol.RebirthStartContract
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GameAssemblyTest {
    @Test
    fun rebirthStartMappingPreservesTheExactGameSourceTupleScopeAndDepth() {
        val request = CommandRequest(
            semanticHandle = SemanticHandle(
                operationId = OperationId(43uL),
                outputKind = GameOutputKind.BEGIN_REBIRTH,
                localOrdinalOrName = "authority-0",
            ),
            sourceOrdinal = 1u,
            payload = GameCommand.BeginRebirth(expectedLevel = 3),
        )

        val mapped = request.toRebirthStartCommand(
            sourceCommitRevision = 21uL,
            expectedLevel = 3,
        )

        assertEquals(GameProjection.BALL_INSTANCE_ID, mapped.commandSource.sourceBallInstanceId)
        assertEquals(21uL, mapped.commandSource.sourceCommitRevision)
        assertEquals(1u, mapped.commandSource.sourceOrdinal)
        assertEquals(43uL, mapped.commandSource.sourceOperationId)
        assertEquals(RebirthStartContract.SOURCE_OUTPUT_KIND, mapped.commandSource.sourceOutputKind)
        assertEquals("authority-0", mapped.commandSource.sourceLocalOrdinalOrName)
        assertEquals(GameProjection.BALL_INSTANCE_ID, mapped.causalBudgetScope.ownerBallInstanceId)
        assertEquals(43uL, mapped.causalBudgetScope.operationId)
        assertEquals(RebirthStartContract.COMMAND_CAUSAL_DEPTH, mapped.causalDepth)
        assertEquals(3, mapped.expectedLevel)
    }

    @Test
    fun runSettlementMappingPreservesTheExactGameSourceTupleAndDepth() {
        val change = ProfileChange.ApplyRunOutcome(
            matterEarned = 37L,
            clearedRebirthLevel = 2,
        )
        val request = CommandRequest(
            semanticHandle = SemanticHandle(
                operationId = OperationId(41uL),
                outputKind = GameOutputKind.CHANGE_PROFILE,
                localOrdinalOrName = "authority-0",
            ),
            sourceOrdinal = 1u,
            payload = GameCommand.ChangeProfile(change),
        )

        val mapped = request.toProfileRunOutcomeCommand(
            sourceCommitRevision = 19uL,
            change = change,
        )

        assertEquals(GameProjection.BALL_INSTANCE_ID, mapped.commandSource.sourceBallInstanceId)
        assertEquals(19uL, mapped.commandSource.sourceCommitRevision)
        assertEquals(1u, mapped.commandSource.sourceOrdinal)
        assertEquals(41uL, mapped.commandSource.sourceOperationId)
        assertEquals(
            ProfileApplyRunOutcomeContract.SOURCE_OUTPUT_KIND,
            mapped.commandSource.sourceOutputKind,
        )
        assertEquals("authority-0", mapped.commandSource.sourceLocalOrdinalOrName)
        assertEquals(GameProjection.BALL_INSTANCE_ID, mapped.causalBudgetScope.ownerBallInstanceId)
        assertEquals(41uL, mapped.causalBudgetScope.operationId)
        assertEquals(ProfileApplyRunOutcomeContract.COMMAND_CAUSAL_DEPTH, mapped.causalDepth)
        assertEquals(37L, mapped.matterEarned)
        assertEquals(2, mapped.clearedRebirthLevel)
    }

    @Test
    fun persistenceSettingsMappingRoundTripsEveryStableCode() {
        ParticleDensity.entries.forEach { density ->
            DamageNumberSize.entries.forEach { size ->
                DamageNumberFormat.entries.forEach { format ->
                    val settings = SettingsValues(
                        particleDensity = density,
                        damageNumberSize = size,
                        damageNumberFormat = format,
                    )

                    assertEquals(
                        settings,
                        settings.toPersistedSettings().toSettingsValues(),
                    )
                }
            }
        }
    }

    @Test
    fun gameReplicaUsesProfileOwnedCurrentAndNextRunTuning() {
        val assembly = createAssembly(
            progressStore = LoadedProgressStore(
                PersistedProgress(
                    rebirthLevel = 4,
                    highestClearedRebirth = 3,
                ),
            ),
            seed = 4,
        )
        try {
            val projection = assembly.readCurrentGameProjection()
            val expectedCurrent = ProfileRebirthProgression.profile(4)
            val expectedNext = ProfileRebirthProgression.profile(5)

            assertEquals(expectedCurrent.tier, projection.rebirthProfile.tier)
            assertEquals(
                expectedCurrent.enemyHealthMultiplier,
                projection.rebirthProfile.enemyHealthMultiplier,
            )
            assertEquals(
                expectedCurrent.matterGainMultiplier,
                projection.rebirthProfile.matterGainMultiplier,
            )
            assertEquals(expectedNext.tier, projection.nextRebirthProfile.tier)
            assertEquals(
                expectedNext.spawnRateMultiplier,
                projection.nextRebirthProfile.spawnRateMultiplier,
            )
        } finally {
            assembly.close()
        }
    }

    @Test
    fun interactionOwnsAndAppliesTheUnstampedVisualAttachment() {
        val assembly = createAssembly(seed = 2)
        try {
            fun dispatch(intent: GameIntent): GameDispatchResult.Committed {
                val committed = assertIs<GameDispatchResult.Committed>(assembly.dispatch(intent))
                assembly.applyVisualFx(committed.visualFxCues)
                return committed
            }

            dispatch(GameIntent.RunStartRequested)
            dispatch(GameIntent.DashRequested)
            dispatch(GameIntent.FrameElapsed(0.1f))

            val visualFx = assembly.visualFxSnapshot()
            assertTrue(visualFx.particles.isNotEmpty())
            assertTrue(visualFx.shockwaves.isNotEmpty())

            dispatch(GameIntent.RunStartRequested)
            val cleared = assembly.visualFxSnapshot()
            assertTrue(
                listOf(
                    cleared.particles,
                    cleared.motionEchoes,
                    cleared.shockwaves,
                    cleared.damageNumbers,
                    cleared.weaponArcs,
                ).all { it.isEmpty() },
            )
        } finally {
            assembly.close()
        }
    }

    @Test
    fun settingsCommandRoutesRefreshesTheGameReplicaAndPersistsBothAuthorities() {
        val store = RecordingProgressStore(
            loadResult = ProgressLoadResult.Loaded(
                PersistedProgress(matter = 43L, lifetimeMatter = 90L),
            ),
        )
        val assembly = createAssembly(progressStore = store, seed = 6)
        try {
            val result = assertIs<GameDispatchResult.Committed>(
                assembly.dispatch(GameIntent.MuteToggled),
            )

            assertTrue(result.commands.any { it.payload is GameCommand.ChangeSettings })
            val settings = currentSettings(assembly)
            assertFalse(settings.soundEnabled)
            assertFalse(settings.musicEnabled)
            assertEquals(settings.soundEnabled, result.projectionRead.payload.settings.soundEnabled)
            assertEquals(settings.musicEnabled, result.projectionRead.payload.settings.musicEnabled)
            assertTrue(
                result.projectionRead.consistencyStamp.commitRevision > result.sourceCommitRevision,
                "the returned projection must include the post-route dependency observation",
            )

            val persisted = store.persisted.single()
            assertEquals(43L, persisted.matter)
            assertEquals(90L, persisted.lifetimeMatter)
            assertEquals(settings.toPersistedSettings(), persisted.settings)
            assertIs<ProgressPersistenceStatus.Persisted>(
                assembly.progressPersistenceStatus().payload,
            )
        } finally {
            assembly.close()
        }
    }

    @Test
    fun profileCommandRoutesRefreshesTheGameReplicaAndPersistsBothAuthorities() {
        val store = RecordingProgressStore(
            loadResult = ProgressLoadResult.Loaded(
                PersistedProgress(matter = 7L, lifetimeMatter = 100L),
            ),
        )
        val assembly = createAssembly(progressStore = store, seed = 7)
        try {
            val result = assertIs<GameDispatchResult.Committed>(
                assembly.dispatch(GameIntent.CoreShapeSelected(CoreShape.PRISM)),
            )

            val routed = result.commands
                .map { it.payload }
                .filterIsInstance<GameCommand.ChangeProfile>()
                .single()
            assertEquals(ProfileChange.SelectCoreShape(CoreShape.PRISM), routed.change)

            val profile = currentProfile(assembly)
            assertEquals(ProfileCoreShape.PRISM, profile.selectedCoreShape)
            assertEquals(CoreShape.PRISM, result.projectionRead.payload.coreShape)
            assertTrue(
                result.projectionRead.consistencyStamp.commitRevision > result.sourceCommitRevision,
                "the returned projection must include the refreshed Profile replica",
            )

            val persisted = store.persisted.single()
            assertEquals(
                ProgressPersistenceSchema.CORE_SHAPE_PRISM_CODE,
                persisted.coreShapeIndex,
            )
            assertEquals(currentSettings(assembly).toPersistedSettings(), persisted.settings)
            assertIs<ProgressPersistenceStatus.Persisted>(
                assembly.progressPersistenceStatus().payload,
            )
        } finally {
            assembly.close()
        }
    }

    @Test
    fun gameHasNoResourceCapabilityAndAssemblyRoutesItsAudioCommandToTheAudioBall() {
        val progressStore = RecordingProgressStore()
        val tonePlayer = RecordingTonePlayer()
        val assembly = createAssembly(
            progressStore = progressStore,
            tonePlayer = tonePlayer,
            seed = 8,
        )
        try {
            val result = assertIs<GameDispatchResult.Committed>(
                assembly.dispatch(GameIntent.UserGestureObserved),
            )

            assertEquals(listOf(GameCommand.EnsureAudioUnlocked), result.commands.map { it.payload })
            assertEquals(
                result.sourceCommitRevision,
                result.projectionRead.consistencyStamp.commitRevision,
                "an audio-only route must not create a redundant dependency-observation commit",
            )
            assertEquals(1, tonePlayer.unlockCalls)
            assertTrue(progressStore.persisted.isEmpty())
            assertEquals(
                ProgressPersistenceStatus.NeverRequested,
                assembly.progressPersistenceStatus().payload,
            )
        } finally {
            assembly.close()
        }
    }

    @Test
    fun rebirthFlowAdvancesProfileThenStartsGameFromTheRefreshedReplica() {
        val store = RecordingProgressStore(
            loadResult = ProgressLoadResult.Loaded(
                PersistedProgress(
                    rebirthLevel = 0,
                    highestClearedRebirth = 0,
                ),
            ),
        )
        val assembly = createAssembly(progressStore = store, seed = 9)
        try {
            val armed = assertIs<GameDispatchResult.Committed>(
                assembly.dispatch(GameIntent.RebirthRequested),
            )
            assertTrue(armed.commands.none { it.payload is GameCommand.BeginRebirth })

            val started = assertIs<GameDispatchResult.Committed>(
                assembly.dispatch(GameIntent.RebirthRequested),
            )
            assertTrue(started.commands.any { it.payload is GameCommand.BeginRebirth })
            assertEquals(1, currentProfile(assembly).rebirthLevel)
            assertEquals(1, started.projectionRead.payload.rebirthLevel)
            assertEquals(GamePhase.RUNNING, started.projectionRead.payload.phase)
            val completed = assertIs<RebirthStatus.Completed>(assembly.rebirthStatus().payload)
            val beginRebirth = started.commands.single { it.payload is GameCommand.BeginRebirth }
            assertEquals(
                beginRebirth.toRebirthStartCommand(
                    sourceCommitRevision = started.sourceCommitRevision,
                    expectedLevel = 0,
                ),
                completed.startCommand,
            )
            assertEquals(
                completed.startCommand.causalBudgetScope.ownerBallInstanceId,
                completed.profileResult.causalBudgetScope.ownerBallInstanceId,
            )
            assertEquals(
                completed.startCommand.causalBudgetScope.operationId,
                completed.profileResult.causalBudgetScope.operationId,
            )
            assertEquals(
                completed.profileResult.causalBudgetScope.ownerBallInstanceId,
                completed.result.causalBudgetScope.ownerBallInstanceId,
            )
            assertEquals(
                completed.profileResult.causalBudgetScope.operationId,
                completed.result.causalBudgetScope.operationId,
            )
            assertEquals(
                ProfileAdvanceRebirthContract.RESULT_CAUSAL_DEPTH,
                completed.profileResult.causalDepth,
            )
            assertEquals(GameRunStartContract.RESULT_CAUSAL_DEPTH, completed.result.causalDepth)
            assertEquals(
                completed.gameCommandSource.sourceBallInstanceId,
                completed.result.commandSource.sourceBallInstanceId,
            )
            assertEquals(
                started.projectionRead.consistencyStamp.commitRevision,
                completed.result.gameCommitRevision,
            )
            assertEquals(1, store.persisted.single().rebirthLevel)
        } finally {
            assembly.close()
        }
    }

    @Test
    fun bootstrapResourceExceptionBecomesTypedOutcomeUnknown() {
        listOf(ThrowingLoadProgressStore).forEach { store ->
            val assembly = createAssembly(progressStore = store, seed = 1)
            try {
                val status = assertIs<BootstrapProgressStatus.OutcomeUnknown>(
                    assembly.bootstrapProgressStatus,
                )
                assertEquals(ProgressResourceFailure.PROVIDER_READ_FAILED, status.reason)
            } finally {
                assembly.close()
            }
        }
    }

    @Test
    fun bootstrapResourceValueIsNormalizedBeforeRevisionZero() {
        val assembly = createAssembly(
            progressStore = LoadedProgressStore(
                PersistedProgress(
                    matter = -10L,
                    lifetimeMatter = -20L,
                    coreShapeIndex = Int.MAX_VALUE,
                    selectedWeaponIndex = Int.MAX_VALUE,
                    unlockedWeaponIndices = setOf(-1, Int.MAX_VALUE),
                    metaLevels = listOf(Int.MAX_VALUE),
                    discoveredItemIds = setOf(-1, ItemCatalogFacts.ITEM_COUNT),
                    settings = SettingsValues(
                        masterVolume = -5f,
                        simulationSpeed = 99f,
                        textScale = 0.1f,
                        damageNumberTierThreshold = Int.MAX_VALUE,
                    ).toPersistedSettings(),
                    rebirthLevel = Int.MAX_VALUE,
                    highestClearedRebirth = Int.MAX_VALUE,
                ),
            ),
            seed = 3,
        )
        try {
            assertEquals(BootstrapProgressStatus.Loaded, assembly.bootstrapProgressStatus)
            val projection = assembly.readCurrentGameProjection()
            assertEquals(0L, projection.totalMatter)
            assertEquals(0f, projection.settings.masterVolume)
            assertEquals(2f, projection.settings.simulationSpeed)
            assertEquals(1f, projection.settings.textScale)
            assertEquals(0, projection.discoveredItemCount)
        } finally {
            assembly.close()
        }
    }

    @Test
    fun nonFiniteOrOversizedBootstrapResourceValueIsRejectedBeforeRevisionZero() {
        val nonFinite = createAssembly(
            progressStore = LoadedProgressStore(
                PersistedProgress(
                    settings = SettingsValues(simulationSpeed = Float.NaN).toPersistedSettings(),
                ),
            ),
            seed = 4,
        )
        try {
            assertEquals(
                BootstrapProgressStatus.Rejected(
                    ProgressLoadRejection.BOOTSTRAP_NON_FINITE_NUMBER,
                ),
                nonFinite.bootstrapProgressStatus,
            )
            assertEquals(GameSettings(), nonFinite.readCurrentGameProjection().settings)
        } finally {
            nonFinite.close()
        }

        val oversized = createAssembly(
            progressStore = LoadedProgressStore(
                PersistedProgress(
                    discoveredItemIds = (0..ItemCatalogFacts.ITEM_COUNT).toSet(),
                ),
            ),
            seed = 5,
        )
        try {
            assertEquals(
                BootstrapProgressStatus.Rejected(
                    ProgressLoadRejection.BOOTSTRAP_COLLECTION_LIMIT_EXCEEDED,
                ),
                oversized.bootstrapProgressStatus,
            )
        } finally {
            oversized.close()
        }
    }
}

private fun createAssembly(
    progressStore: ProgressStore = NotFoundProgressStore,
    tonePlayer: NumericTonePlayer = RecordingTonePlayer(),
    seed: Int,
): GameAssembly = GameAssembly.create(
    progressStore = progressStore,
    audioBall = AudioFeatureBall(tonePlayer),
    seed = seed,
)

private fun currentSettings(assembly: GameAssembly): SettingsValues =
    assembly.readCurrentSettings()

private fun currentProfile(assembly: GameAssembly) =
    assembly.readCurrentProfile()

private object ThrowingLoadProgressStore : ProgressStore {
    override fun load(): ProgressLoadResult = error("provider unavailable")

    override fun persist(progress: PersistedProgress): ProgressPersistResult =
        ProgressPersistResult.Persisted
}

private object NotFoundProgressStore : ProgressStore {
    override fun load(): ProgressLoadResult = ProgressLoadResult.NotFound

    override fun persist(progress: PersistedProgress): ProgressPersistResult =
        ProgressPersistResult.Persisted
}

private class LoadedProgressStore(
    private val progress: PersistedProgress,
) : ProgressStore {
    override fun load(): ProgressLoadResult = ProgressLoadResult.Loaded(progress)

    override fun persist(progress: PersistedProgress): ProgressPersistResult =
        ProgressPersistResult.Persisted
}

private class RecordingProgressStore(
    private val loadResult: ProgressLoadResult = ProgressLoadResult.NotFound,
) : ProgressStore {
    val persisted = mutableListOf<PersistedProgress>()

    override fun load(): ProgressLoadResult = loadResult

    override fun persist(progress: PersistedProgress): ProgressPersistResult {
        persisted += progress
        return ProgressPersistResult.Persisted
    }
}

private class RecordingTonePlayer : NumericTonePlayer {
    var unlockCalls: Int = 0
        private set

    override fun unlock() {
        unlockCalls++
    }

    override fun play(
        frequency: Float,
        durationSeconds: Float,
        volume: Float,
        wave: Int,
    ) = Unit

    override fun close() = Unit
}
