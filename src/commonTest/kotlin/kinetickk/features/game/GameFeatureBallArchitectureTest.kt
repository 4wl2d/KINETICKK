// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.game

import kinetickk.application.runtime.Accepted
import kinetickk.application.runtime.AdmissionFailure
import kinetickk.application.runtime.MandatoryDecisionLimit
import kinetickk.features.game.nucleus.CoreShape
import kinetickk.features.game.nucleus.GamePhase
import kinetickk.features.game.nucleus.GameSettings
import kinetickk.features.game.nucleus.MetaUpgradeCatalog
import kinetickk.features.game.nucleus.MetaUpgradeId
import kinetickk.features.game.nucleus.RebirthProfile
import kinetickk.features.game.nucleus.RebirthProgression
import kinetickk.features.game.nucleus.UiScreen
import kinetickk.features.game.nucleus.WeaponId
import kinetickk.features.game.nucleus.projection.GameProjection
import kinetickk.features.game.nucleus.protocol.BrakeSource
import kinetickk.features.game.nucleus.protocol.CommandRequest
import kinetickk.features.game.nucleus.protocol.GameCommand
import kinetickk.features.game.nucleus.protocol.GameDecisionContext
import kinetickk.features.game.nucleus.protocol.GameDependencyContract
import kinetickk.features.game.nucleus.protocol.GameDependencySource
import kinetickk.features.game.nucleus.protocol.GameFact
import kinetickk.features.game.nucleus.protocol.GameIntent
import kinetickk.features.game.nucleus.protocol.GameOutputKind
import kinetickk.features.game.nucleus.protocol.GameProfileReplica
import kinetickk.features.game.nucleus.protocol.GameProjectionPayload
import kinetickk.features.game.nucleus.protocol.GameQuery
import kinetickk.features.game.nucleus.protocol.GameQueryResult
import kinetickk.features.game.nucleus.protocol.GameRejection
import kinetickk.features.game.nucleus.protocol.GameRunConfigurationReference
import kinetickk.features.game.nucleus.protocol.GameRunStartCausalScope
import kinetickk.features.game.nucleus.protocol.GameRunStartCommandSource
import kinetickk.features.game.nucleus.protocol.GameRunStartContract
import kinetickk.features.game.nucleus.protocol.GameRunStartModuleCommand
import kinetickk.features.game.nucleus.protocol.GameRunStartModuleResult
import kinetickk.features.game.nucleus.protocol.GameRunStartRejectionReason
import kinetickk.features.game.nucleus.protocol.OperationId
import kinetickk.features.game.nucleus.protocol.ProfileChange
import kinetickk.features.game.nucleus.protocol.ProjectionOutput
import kinetickk.features.game.nucleus.protocol.SemanticOutput
import kinetickk.features.game.nucleus.protocol.SettingsChange
import kinetickk.features.game.nucleus.protocol.SoundCue
import kinetickk.features.game.nucleus.read.ConsistencyStamp
import kinetickk.features.game.nucleus.read.ReadContext
import kinetickk.features.game.nucleus.read.ReadResult
import kinetickk.features.game.nucleus.transition.GameBallState
import kinetickk.features.game.nucleus.transition.GameNucleus
import kinetickk.features.game.nucleus.transition.initialGameBallState
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameFeatureBallArchitectureTest {
    @Test
    fun factoryCreatesTheResourceFreeGameBoundaryWithCommandOnlyLimits() {
        val ball = GameFeatureBall.create(bootstrapProgress = null)

        assertEquals(0uL, ball.projectionRead().consistencyStamp.commitRevision)
        assertEquals(65_536L, GameFeatureBall.LIMITS.maxInputBytes)
        assertEquals(16_777_216L, GameFeatureBall.LIMITS.maxStateBytes)
        assertEquals(2_048, GameFeatureBall.LIMITS.maxCollectionItems)
        assertEquals(8, GameFeatureBall.LIMITS.maxOutputsPerDecision)
        assertEquals(0, GameFeatureBall.LIMITS.maxEffectsPerDecision)
        assertEquals(7, GameFeatureBall.LIMITS.maxCommandsPerDecision)
        assertEquals(GameRunStartContract.COMMAND_CAUSAL_DEPTH, GameFeatureBall.LIMITS.maxCausalDepth)
        assertEquals(0, GameFeatureBall.LIMITS.maxRetriesPerOperation)
        assertEquals(48, GameFeatureBall.LIMITS.maxTransitionSteps)

        val gesture = assertIs<GameDispatchResult.Committed>(
            ball.dispatch(GameIntent.UserGestureObserved),
        )
        assertEquals(listOf(GameCommand.EnsureAudioUnlocked), gesture.commands.map { it.payload })
    }

    @Test
    fun nucleusOutputsArePureClosedEnvelopesWithCanonicalKindsAndOrdinals() {
        val nucleus = GameNucleus()
        val richState = observedState(
            nucleus = nucleus,
            profile = profile(
                matter = 1_000,
                lifetimeMatter = 1_000,
                highestClearedRebirth = 0,
            ),
        )
        val firstRebirth = acceptedDecision(
            nucleus = nucleus,
            state = richState,
            intent = GameIntent.RebirthRequested,
            operationId = OperationId(90uL),
        )
        val cases = listOf(
            OutputCase(
                state = richState,
                intent = GameIntent.UserGestureObserved,
                commandKinds = listOf(GameOutputKind.ENSURE_AUDIO_UNLOCKED),
            ),
            OutputCase(
                state = richState,
                intent = GameIntent.FrameElapsed(0.1f),
                commandKinds = listOf(GameOutputKind.ADVANCE_AUDIO),
            ),
            OutputCase(
                state = richState,
                intent = GameIntent.MuteToggled,
                commandKinds = listOf(
                    GameOutputKind.CHANGE_SETTINGS,
                    GameOutputKind.ADVANCE_AUDIO,
                ),
            ),
            OutputCase(
                state = richState,
                intent = GameIntent.CoreShapeSelected(CoreShape.SHARD),
                commandKinds = listOf(
                    GameOutputKind.CHANGE_PROFILE,
                    GameOutputKind.ADVANCE_AUDIO,
                ),
            ),
            OutputCase(
                state = richState,
                intent = GameIntent.MetaUpgradePurchaseRequested(MetaUpgradeId.CORE_INTEGRITY),
                commandKinds = listOf(
                    GameOutputKind.CHANGE_PROFILE,
                    GameOutputKind.ADVANCE_AUDIO,
                ),
            ),
            OutputCase(
                state = richState,
                intent = GameIntent.WeaponPurchaseOrEquipRequested(WeaponId.MORNINGSTAR),
                commandKinds = listOf(
                    GameOutputKind.CHANGE_PROFILE,
                    GameOutputKind.ADVANCE_AUDIO,
                ),
            ),
            OutputCase(
                state = firstRebirth.nextState,
                intent = GameIntent.RebirthRequested,
                commandKinds = listOf(
                    GameOutputKind.BEGIN_REBIRTH,
                    GameOutputKind.ADVANCE_AUDIO,
                ),
            ),
        )

        cases.forEachIndexed { index, case ->
            val operationId = OperationId((index + 1).toULong())
            val decision = acceptedDecision(nucleus, case.state, case.intent, operationId)
            val expectedKinds = listOf(GameOutputKind.GAME_PROJECTION_CHANGED) + case.commandKinds

            assertEquals(expectedKinds, decision.outputs.map { it.semanticHandle.outputKind })
            assertTrue(decision.outputs.size <= GameFeatureBall.LIMITS.maxOutputsPerDecision)
            assertTrue(
                decision.outputs.count { it is CommandRequest } <=
                    GameFeatureBall.LIMITS.maxCommandsPerDecision,
            )
            assertIs<ProjectionOutput>(decision.outputs.first())
            decision.outputs.forEachIndexed { outputIndex, output ->
                assertEquals(outputIndex.toUInt(), output.sourceOrdinal)
                assertEquals(operationId, output.semanticHandle.operationId)
                when (output) {
                    is ProjectionOutput -> {
                        assertEquals(
                            GameOutputKind.GAME_PROJECTION_CHANGED,
                            output.semanticHandle.outputKind,
                        )
                        assertIs<GameProjectionPayload.GameProjectionChanged>(output.payload)
                    }
                    is CommandRequest -> assertEquals(
                        expectedOutputKind(output.payload),
                        output.semanticHandle.outputKind,
                    )
                }
            }
        }
    }

    @Test
    fun dependencyFactUpdatesCapturedReplicasWithoutCommands() {
        val ball = GameFeatureBall.create(bootstrapProgress = null, seed = 44)
        val observedSettings = GameSettings(
            masterVolume = 4f,
            simulationSpeed = 0.2f,
            textScale = 3f,
        )
        val observedProfile = profile(
            matter = 500,
            lifetimeMatter = 200,
            coreShape = CoreShape.SHARD,
            selectedWeapon = WeaponId.MORNINGSTAR,
            unlockedWeapons = setOf(WeaponId.MORNINGSTAR),
            metaRanks = listOf(99, -4, 2, 0, 0, 0, 0, 0),
            discoveredItemIds = setOf(-1, 0, 100_000),
            rebirthLevel = 2,
            highestClearedRebirth = 99,
        )

        val committed = assertIs<GameDispatchResult.Committed>(
            ball.observeDependencies(observedSettings, observedProfile),
        )
        val projection = committed.projectionRead.payload

        assertEquals(1uL, committed.sourceCommitRevision)
        assertTrue(committed.commands.isEmpty())
        assertTrue(committed.visualFxCues.isEmpty())
        assertEquals(observedSettings.normalized(), projection.settings)
        assertEquals(500L, projection.totalMatter)
        assertEquals(500L, projection.lifetimeMatter)
        assertEquals(CoreShape.SHARD, projection.coreShape)
        assertEquals(WeaponId.MORNINGSTAR, projection.startingWeapon)
        assertEquals(WeaponId.MORNINGSTAR, projection.weapon)
        assertTrue(projection.isWeaponUnlocked(WeaponId.FLUX_WAKE))
        assertTrue(projection.isWeaponUnlocked(WeaponId.MORNINGSTAR))
        assertEquals(
            MetaUpgradeCatalog.byId(MetaUpgradeId.CORE_INTEGRITY).maxRanks,
            projection.metaLevel(MetaUpgradeId.CORE_INTEGRITY),
        )
        assertEquals(0, projection.metaLevel(MetaUpgradeId.KINETIC_AMPLIFIER))
        assertEquals(2, projection.metaLevel(MetaUpgradeId.MAGNETIC_RESONANCE))
        assertEquals(1, projection.discoveredItemCount)
        assertTrue(projection.isItemDiscovered(0))
        assertFalse(projection.isItemDiscovered(-1))
        assertEquals(2, projection.rebirthLevel)
        assertEquals(2, projection.highestClearedRebirth)
    }

    @Test
    fun targetOwnedRunStartExecutesAgainstTheExactCurrentProfileReference() {
        val ball = GameFeatureBall.create(bootstrapProgress = null, seed = 46)
        assertIs<GameDispatchResult.Committed>(
            ball.observeDependencies(
                settings = GameSettings(),
                profile = profile(rebirthLevel = 2),
                settingsSource = settingsDependencyStamp(3uL),
                profileSource = profileDependencyStamp(9uL),
            ),
        )
        val command = gameRunStartCommand(profileRevision = 9uL, rebirthLevel = 2)

        val execution = ball.execute(command)

        val started = assertIs<GameRunStartModuleResult.Started>(execution.moduleResult)
        assertEquals(command.commandSource, started.commandSource)
        assertEquals(command.causalBudgetScope, started.causalBudgetScope)
        assertEquals(GameRunStartContract.RESULT_CAUSAL_DEPTH, started.causalDepth)
        assertEquals(GameRunStartContract.RESULT_PROVENANCE, started.provenance)
        val committed = assertIs<GameDispatchResult.Committed>(execution.committed)
        assertEquals(committed.sourceCommitRevision, started.gameCommitRevision)
        assertEquals(GamePhase.RUNNING, committed.projectionRead.payload.phase)
        assertEquals(2, committed.projectionRead.payload.rebirthLevel)
    }

    @Test
    fun exactRunStartRetryReturnsTheOriginalResultWithoutResettingTheRun() {
        val ball = GameFeatureBall.create(bootstrapProgress = null, seed = 49)
        assertIs<GameDispatchResult.Committed>(
            ball.observeDependencies(
                settings = GameSettings(),
                profile = profile(rebirthLevel = 2),
                settingsSource = settingsDependencyStamp(3uL),
                profileSource = profileDependencyStamp(9uL),
            ),
        )
        val command = gameRunStartCommand(profileRevision = 9uL, rebirthLevel = 2)
        val first = ball.execute(command)
        assertIs<GameDispatchResult.Committed>(first.committed)
        assertIs<GameRunStartModuleResult.Started>(first.moduleResult)
        assertIs<GameDispatchResult.Committed>(
            ball.dispatch(GameIntent.FrameElapsed(realDeltaSeconds = 0.25f)),
        )
        val beforeRetry = ball.projectionRead()
        assertTrue(beforeRetry.payload.elapsed > 0f)

        val retry = ball.execute(command)

        assertSame(first.moduleResult, retry.moduleResult)
        assertEquals(null, retry.committed)
        val afterRetry = ball.projectionRead()
        assertEquals(beforeRetry.consistencyStamp, afterRetry.consistencyStamp)
        assertSame(beforeRetry.payload, afterRetry.payload)
        assertEquals(beforeRetry.payload.elapsed, afterRetry.payload.elapsed)
        assertEquals(GamePhase.RUNNING, afterRetry.payload.phase)
    }

    @Test
    fun reusedRunStartSourceRejectsAConflictingCommandWithoutTouchingGame() {
        val ball = GameFeatureBall.create(bootstrapProgress = null, seed = 50)
        assertIs<GameDispatchResult.Committed>(
            ball.observeDependencies(
                settings = GameSettings(),
                profile = profile(rebirthLevel = 2),
                settingsSource = settingsDependencyStamp(3uL),
                profileSource = profileDependencyStamp(9uL),
            ),
        )
        val command = gameRunStartCommand(profileRevision = 9uL, rebirthLevel = 2)
        val first = ball.execute(command)
        assertIs<GameDispatchResult.Committed>(first.committed)
        val beforeConflict = ball.projectionRead()
        val conflicting = command.copy(
            runConfigurationReference = command.runConfigurationReference.copy(
                profileCommitRevision = 8uL,
            ),
        )

        val conflict = ball.execute(conflicting)

        val rejected = assertIs<GameRunStartModuleResult.Rejected>(conflict.moduleResult)
        assertEquals(GameRunStartRejectionReason.COMMAND_SOURCE_CONFLICT, rejected.reason)
        assertEquals(null, conflict.committed)
        val afterConflict = ball.projectionRead()
        assertEquals(beforeConflict.consistencyStamp, afterConflict.consistencyStamp)
        assertSame(beforeConflict.payload, afterConflict.payload)
        val exactRetry = ball.execute(command)
        assertSame(first.moduleResult, exactRetry.moduleResult)
        assertEquals(null, exactRetry.committed)
    }

    @Test
    fun laterRunStartReplacesTheReceiptWhileStaleRedeliveryCannotResetIt() {
        val ball = GameFeatureBall.create(bootstrapProgress = null, seed = 52)
        assertIs<GameDispatchResult.Committed>(
            ball.observeDependencies(
                settings = GameSettings(),
                profile = profile(rebirthLevel = 2),
                settingsSource = settingsDependencyStamp(3uL),
                profileSource = profileDependencyStamp(9uL),
            ),
        )
        val firstCommand = gameRunStartCommand(
            profileRevision = 9uL,
            rebirthLevel = 2,
            sourceCommitRevision = 2uL,
            sourceOperationId = 17uL,
        )
        val laterCommand = gameRunStartCommand(
            profileRevision = 9uL,
            rebirthLevel = 2,
            sourceCommitRevision = 3uL,
            sourceOperationId = 18uL,
        )

        val first = ball.execute(firstCommand)
        assertIs<GameRunStartModuleResult.Started>(first.moduleResult)
        assertIs<GameDispatchResult.Committed>(first.committed)
        val later = ball.execute(laterCommand)
        assertIs<GameRunStartModuleResult.Started>(later.moduleResult)
        assertIs<GameDispatchResult.Committed>(later.committed)
        assertIs<GameDispatchResult.Committed>(
            ball.dispatch(GameIntent.FrameElapsed(realDeltaSeconds = 0.25f)),
        )
        val beforeStale = ball.projectionRead()
        assertTrue(beforeStale.payload.elapsed > 0f)

        val stale = ball.execute(firstCommand)

        val staleResult = assertIs<GameRunStartModuleResult.Rejected>(stale.moduleResult)
        assertEquals(GameRunStartRejectionReason.STALE_COMMAND_SOURCE, staleResult.reason)
        assertEquals(null, stale.committed)
        val afterStale = ball.projectionRead()
        assertEquals(beforeStale.consistencyStamp, afterStale.consistencyStamp)
        assertSame(beforeStale.payload, afterStale.payload)

        val malformedOld = firstCommand.copy(
            commandSource = firstCommand.commandSource.copy(sourceCommitRevision = 0uL),
        )
        val malformed = ball.execute(malformedOld)
        assertEquals(
            GameRunStartRejectionReason.INVALID_COMMAND_SOURCE,
            assertIs<GameRunStartModuleResult.Rejected>(malformed.moduleResult).reason,
        )
        assertEquals(null, malformed.committed)

        val laterReplay = ball.execute(laterCommand)

        assertSame(later.moduleResult, laterReplay.moduleResult)
        assertEquals(null, laterReplay.committed)
        val afterReplay = ball.projectionRead()
        assertEquals(beforeStale.consistencyStamp, afterReplay.consistencyStamp)
        assertSame(beforeStale.payload, afterReplay.payload)
    }

    @Test
    fun sameDeliverySlotWithAlteredOperationIsAConflictAndDoesNotReplaceReceipt() {
        val ball = GameFeatureBall.create(bootstrapProgress = null, seed = 53)
        assertIs<GameDispatchResult.Committed>(
            ball.observeDependencies(
                settings = GameSettings(),
                profile = profile(rebirthLevel = 2),
                settingsSource = settingsDependencyStamp(3uL),
                profileSource = profileDependencyStamp(9uL),
            ),
        )
        val command = gameRunStartCommand(profileRevision = 9uL, rebirthLevel = 2)
        val first = ball.execute(command)
        assertIs<GameRunStartModuleResult.Started>(first.moduleResult)
        assertIs<GameDispatchResult.Committed>(first.committed)
        val beforeConflict = ball.projectionRead()
        val conflicting = command.copy(
            commandSource = command.commandSource.copy(sourceOperationId = 18uL),
            causalBudgetScope = command.causalBudgetScope.copy(operationId = 18uL),
        )

        val conflict = ball.execute(conflicting)

        val rejected = assertIs<GameRunStartModuleResult.Rejected>(conflict.moduleResult)
        assertEquals(GameRunStartRejectionReason.COMMAND_SOURCE_CONFLICT, rejected.reason)
        assertEquals(null, conflict.committed)
        val afterConflict = ball.projectionRead()
        assertEquals(beforeConflict.consistencyStamp, afterConflict.consistencyStamp)
        assertSame(beforeConflict.payload, afterConflict.payload)

        val exactReplay = ball.execute(command)
        assertSame(first.moduleResult, exactReplay.moduleResult)
        assertEquals(null, exactReplay.committed)
    }

    @Test
    fun oversizedRunStartIsNotRetainedByTheDedupBoundary() {
        val ball = GameFeatureBall.create(bootstrapProgress = null, seed = 51)
        assertIs<GameDispatchResult.Committed>(
            ball.observeDependencies(
                settings = GameSettings(),
                profile = profile(rebirthLevel = 2),
                settingsSource = settingsDependencyStamp(3uL),
                profileSource = profileDependencyStamp(9uL),
            ),
        )
        val command = gameRunStartCommand(profileRevision = 9uL, rebirthLevel = 2)
        val oversized = command.copy(
            runConfigurationReference = command.runConfigurationReference.copy(
                profileBallInstanceId = "x".repeat(
                    (GameFeatureBall.LIMITS.maxInputBytes / 4L).toInt() + 1,
                ),
            ),
        )
        val before = ball.projectionRead()

        val rejectedExecution = ball.execute(oversized)

        val rejected = assertIs<GameRunStartModuleResult.Rejected>(
            rejectedExecution.moduleResult,
        )
        assertEquals(GameRunStartRejectionReason.TARGET_ADMISSION_REJECTED, rejected.reason)
        assertEquals(null, rejectedExecution.committed)
        assertEquals(before.consistencyStamp, ball.projectionRead().consistencyStamp)
        assertSame(before.payload, ball.projectionRead().payload)

        val correctedExecution = ball.execute(command)

        assertIs<GameRunStartModuleResult.Started>(correctedExecution.moduleResult)
        assertIs<GameDispatchResult.Committed>(correctedExecution.committed)
        assertEquals(GamePhase.RUNNING, ball.projectionRead().payload.phase)
    }

    @Test
    fun runStartRejectsAStaleProfileReferenceWithoutCommittingGameState() {
        val ball = GameFeatureBall.create(bootstrapProgress = null, seed = 47)
        assertIs<GameDispatchResult.Committed>(
            ball.observeDependencies(
                settings = GameSettings(),
                profile = profile(rebirthLevel = 2),
                settingsSource = settingsDependencyStamp(3uL),
                profileSource = profileDependencyStamp(9uL),
            ),
        )
        val before = ball.projectionRead()

        val execution = ball.execute(
            gameRunStartCommand(profileRevision = 8uL, rebirthLevel = 2),
        )

        val rejected = assertIs<GameRunStartModuleResult.Rejected>(execution.moduleResult)
        assertEquals(GameRunStartRejectionReason.PROFILE_REFERENCE_NOT_CURRENT, rejected.reason)
        assertEquals(null, execution.committed)
        val after = ball.projectionRead()
        assertEquals(before.consistencyStamp, after.consistencyStamp)
        assertSame(before.payload, after.payload)
        assertEquals(GamePhase.MENU, after.payload.phase)
    }

    @Test
    fun runStartRejectsMalformedSourceCorrelationWithoutCommittingGameState() {
        val ball = GameFeatureBall.create(bootstrapProgress = null, seed = 48)
        assertIs<GameDispatchResult.Committed>(
            ball.observeDependencies(
                settings = GameSettings(),
                profile = profile(rebirthLevel = 1),
                settingsSource = settingsDependencyStamp(2uL),
                profileSource = profileDependencyStamp(5uL),
            ),
        )
        val before = ball.projectionRead()
        val valid = gameRunStartCommand(profileRevision = 5uL, rebirthLevel = 1)
        val malformed = valid.let { command ->
            command.copy(
                commandSource = command.commandSource.copy(
                    sourceLocalOrdinalOrName = "different-command",
                ),
            )
        }

        val execution = ball.execute(malformed)

        val rejected = assertIs<GameRunStartModuleResult.Rejected>(execution.moduleResult)
        assertEquals(GameRunStartRejectionReason.INVALID_COMMAND_SOURCE, rejected.reason)
        assertEquals(null, execution.committed)
        assertEquals(before.consistencyStamp, ball.projectionRead().consistencyStamp)
        assertSame(before.payload, ball.projectionRead().payload)

        val corrected = ball.execute(valid)

        assertIs<GameRunStartModuleResult.Started>(corrected.moduleResult)
        assertIs<GameDispatchResult.Committed>(corrected.committed)
    }

    @Test
    fun dependencyFactRejectsWrongAuthorityOrSchemaWithoutMutation() {
        val validSettingsSource = settingsDependencyStamp(1uL)
        val validProfileSource = profileDependencyStamp(1uL)
        val cases = listOf(
            Triple(
                GameDependencySource.SETTINGS,
                validSettingsSource.copy(ballInstanceId = "unexpected/Settings"),
                validProfileSource,
            ),
            Triple(
                GameDependencySource.SETTINGS,
                validSettingsSource.copy(stateSchemaVersion = 2),
                validProfileSource,
            ),
            Triple(
                GameDependencySource.PROFILE,
                validSettingsSource,
                validProfileSource.copy(ballInstanceId = "unexpected/Profile"),
            ),
            Triple(
                GameDependencySource.PROFILE,
                validSettingsSource,
                validProfileSource.copy(stateSchemaVersion = 2),
            ),
        )

        cases.forEachIndexed { index, (expectedDependency, settingsSource, profileSource) ->
            val ball = GameFeatureBall.create(bootstrapProgress = null, seed = 45 + index)
            val before = ball.projectionRead()

            val result = assertIs<GameDispatchResult.DecisionRejected>(
                ball.observeDependencies(
                    settings = GameSettings(masterVolume = 0.25f),
                    profile = profile(matter = 50L),
                    settingsSource = settingsSource,
                    profileSource = profileSource,
                ),
            )

            val rejection = assertIs<GameRejection.InvalidDependencySource>(result.reason)
            assertEquals(expectedDependency, rejection.dependency)
            val after = ball.projectionRead()
            assertEquals(before.consistencyStamp, after.consistencyStamp)
            assertSame(before.payload, after.payload)
        }
    }

    @Test
    fun dependencySourceRevisionsAdvanceIndependentlyAndRejectRegressionAtomically() {
        val ball = GameFeatureBall.create(bootstrapProgress = null, seed = 49)
        val settingsAtRevision2 = GameSettings(masterVolume = 0.4f)
        val profileAtRevision3 = profile(matter = 100L, lifetimeMatter = 100L)

        val first = assertIs<GameDispatchResult.Committed>(
            ball.observeDependencies(
                settings = settingsAtRevision2,
                profile = profileAtRevision3,
                settingsSource = settingsDependencyStamp(2uL),
                profileSource = profileDependencyStamp(3uL),
            ),
        )
        assertEquals(1uL, first.sourceCommitRevision)

        val profileAtRevision4 = profile(matter = 200L, lifetimeMatter = 200L)
        val profileOnlyAdvance = assertIs<GameDispatchResult.Committed>(
            ball.observeDependencies(
                settings = settingsAtRevision2,
                profile = profileAtRevision4,
                settingsSource = settingsDependencyStamp(2uL),
                profileSource = profileDependencyStamp(4uL),
            ),
        )
        assertEquals(2uL, profileOnlyAdvance.sourceCommitRevision)
        assertEquals(200L, profileOnlyAdvance.projectionRead.payload.totalMatter)

        val settingsAtRevision5 = GameSettings(masterVolume = 0.25f)
        val settingsOnlyAdvance = assertIs<GameDispatchResult.Committed>(
            ball.observeDependencies(
                settings = settingsAtRevision5,
                profile = profileAtRevision4,
                settingsSource = settingsDependencyStamp(5uL),
                profileSource = profileDependencyStamp(4uL),
            ),
        )
        assertEquals(3uL, settingsOnlyAdvance.sourceCommitRevision)
        assertEquals(0.25f, settingsOnlyAdvance.projectionRead.payload.settings.masterVolume)
        val beforeStale = ball.projectionRead()

        val staleSettings = assertIs<GameDispatchResult.DecisionRejected>(
            ball.observeDependencies(
                settings = GameSettings(masterVolume = 0.9f),
                profile = profile(matter = 300L, lifetimeMatter = 300L),
                settingsSource = settingsDependencyStamp(4uL),
                profileSource = profileDependencyStamp(5uL),
            ),
        )
        assertEquals(
            GameRejection.StaleDependencySource(
                dependency = GameDependencySource.SETTINGS,
                receivedCommitRevision = 4uL,
                lastAcceptedCommitRevision = 5uL,
            ),
            staleSettings.reason,
        )
        assertEquals(beforeStale.consistencyStamp, ball.projectionRead().consistencyStamp)
        assertSame(beforeStale.payload, ball.projectionRead().payload)

        val staleProfile = assertIs<GameDispatchResult.DecisionRejected>(
            ball.observeDependencies(
                settings = GameSettings(masterVolume = 0.8f),
                profile = profile(matter = 400L, lifetimeMatter = 400L),
                settingsSource = settingsDependencyStamp(6uL),
                profileSource = profileDependencyStamp(3uL),
            ),
        )
        assertEquals(
            GameRejection.StaleDependencySource(
                dependency = GameDependencySource.PROFILE,
                receivedCommitRevision = 3uL,
                lastAcceptedCommitRevision = 4uL,
            ),
            staleProfile.reason,
        )
        val afterStale = ball.projectionRead()
        assertEquals(beforeStale.consistencyStamp, afterStale.consistencyStamp)
        assertSame(beforeStale.payload, afterStale.payload)
    }

    @Test
    fun normalIntentPreservesCapturedDependencyProvenance() {
        val nucleus = GameNucleus()
        val settingsSource = ConsistencyStamp(
            ballInstanceId = "kinetickk.local/Settings/local-player",
            commitRevision = 11uL,
            stateSchemaVersion = 1,
        )
        val profileSource = ConsistencyStamp(
            ballInstanceId = "kinetickk.local/Profile/local-player",
            commitRevision = 17uL,
            stateSchemaVersion = 1,
        )
        val observed = assertIs<Accepted<GameBallState, SemanticOutput>>(
            nucleus.decide(
                state = initialGameBallState(seed = 45, bootstrapProgress = null),
                pulse = GameFact.DependenciesObserved(
                    settings = GameSettings(),
                    profile = profile(),
                    settingsSource = settingsSource,
                    profileSource = profileSource,
                ),
                context = context(OperationId(1uL)),
            ),
        ).decision.nextState

        val next = acceptedDecision(
            nucleus = nucleus,
            state = observed,
            intent = GameIntent.PointerMoved(x = 120f, y = 180f),
            operationId = OperationId(2uL),
        ).nextState

        assertEquals(settingsSource, next.settingsSource)
        assertEquals(profileSource, next.profileSource)
    }

    @Test
    fun explicitSettingsAndProfileIntentsRequestOwnerChangesWithoutMutatingReplicas() {
        val ball = GameFeatureBall.create(bootstrapProgress = null, seed = 73)
        val settings = GameSettings(soundEnabled = true, musicEnabled = true)
        assertIs<GameDispatchResult.Committed>(
            ball.observeDependencies(
                settings = settings,
                profile = profile(matter = 1_000, lifetimeMatter = 1_000),
            ),
        )

        val mute = assertIs<GameDispatchResult.Committed>(ball.dispatch(GameIntent.MuteToggled))
        assertEquals(
            GameCommand.ChangeSettings(SettingsChange.ToggleMute),
            mute.commands.first().payload,
        )
        assertEquals(settings, mute.projectionRead.payload.settings)

        val shape = assertIs<GameDispatchResult.Committed>(
            ball.dispatch(GameIntent.CoreShapeSelected(CoreShape.SHARD)),
        )
        assertEquals(
            GameCommand.ChangeProfile(ProfileChange.SelectCoreShape(CoreShape.SHARD)),
            shape.commands.first().payload,
        )
        assertEquals(CoreShape.ORB, shape.projectionRead.payload.coreShape)

        val upgrade = assertIs<GameDispatchResult.Committed>(
            ball.dispatch(
                GameIntent.MetaUpgradePurchaseRequested(MetaUpgradeId.CORE_INTEGRITY),
            ),
        )
        assertEquals(
            GameCommand.ChangeProfile(
                ProfileChange.PurchaseMetaUpgrade(MetaUpgradeId.CORE_INTEGRITY),
            ),
            upgrade.commands.first().payload,
        )
        assertEquals(0, upgrade.projectionRead.payload.metaLevel(MetaUpgradeId.CORE_INTEGRITY))

        val weapon = assertIs<GameDispatchResult.Committed>(
            ball.dispatch(GameIntent.WeaponPurchaseOrEquipRequested(WeaponId.MORNINGSTAR)),
        )
        assertEquals(
            GameCommand.ChangeProfile(
                ProfileChange.PurchaseOrSelectWeapon(WeaponId.MORNINGSTAR),
            ),
            weapon.commands.first().payload,
        )
        assertEquals(WeaponId.FLUX_WAKE, weapon.projectionRead.payload.startingWeapon)
        assertFalse(weapon.projectionRead.payload.isWeaponUnlocked(WeaponId.MORNINGSTAR))
        assertEquals(1_000L, weapon.projectionRead.payload.totalMatter)
    }

    @Test
    fun rebirthRequestWaitsForProfileReplicaFeedback() {
        val ball = GameFeatureBall.create(bootstrapProgress = null, seed = 82)
        assertIs<GameDispatchResult.Committed>(
            ball.observeDependencies(
                settings = GameSettings(),
                profile = profile(
                    matter = 400,
                    lifetimeMatter = 400,
                    rebirthLevel = 2,
                    highestClearedRebirth = 2,
                ),
            ),
        )

        val armed = assertIs<GameDispatchResult.Committed>(
            ball.dispatch(GameIntent.RebirthRequested),
        )
        assertTrue(armed.projectionRead.payload.rebirthConfirmationArmed)
        assertEquals(
            listOf(GameOutputKind.ADVANCE_AUDIO),
            armed.commands.map { it.semanticHandle.outputKind },
        )

        val requested = assertIs<GameDispatchResult.Committed>(
            ball.dispatch(GameIntent.RebirthRequested),
        )
        assertEquals(GameCommand.BeginRebirth(expectedLevel = 2), requested.commands.first().payload)
        assertEquals(2, requested.projectionRead.payload.rebirthLevel)
        assertEquals(GamePhase.MENU, requested.projectionRead.payload.phase)
        assertFalse(requested.projectionRead.payload.rebirthConfirmationArmed)

        val feedback = assertIs<GameDispatchResult.Committed>(
            ball.observeDependencies(
                settings = GameSettings(),
                profile = profile(
                    matter = 400,
                    lifetimeMatter = 400,
                    rebirthLevel = 3,
                    highestClearedRebirth = 2,
                ),
            ),
        )
        assertTrue(feedback.commands.isEmpty())
        assertEquals(3, feedback.projectionRead.payload.rebirthLevel)
        assertEquals(2, feedback.projectionRead.payload.highestClearedRebirth)
    }

    @Test
    fun zeroMatterVictoryStillRequestsProfileClearanceSettlement() {
        val nucleus = GameNucleus()
        val state = initialGameBallState(seed = 83, bootstrapProgress = null)
        state.model.markCurrentRebirthClearedForTesting()

        val decision = acceptedDecision(
            nucleus = nucleus,
            state = state,
            intent = GameIntent.ReturnToMenuRequested,
            operationId = OperationId(83uL),
        )
        val outcome = decision.outputs
            .filterIsInstance<CommandRequest>()
            .map { it.payload }
            .filterIsInstance<GameCommand.ChangeProfile>()
            .single()

        assertEquals(
            ProfileChange.ApplyRunOutcome(
                matterEarned = 0L,
                clearedRebirthLevel = 0,
            ),
            outcome.change,
        )
    }

    @Test
    fun audioWorkLeavesGameAsTypedCommands() {
        val ball = GameFeatureBall.create(bootstrapProgress = null, seed = 91)

        val gesture = assertIs<GameDispatchResult.Committed>(
            ball.dispatch(GameIntent.UserGestureObserved),
        )
        val unlock = gesture.commands.single()
        assertEquals(GameCommand.EnsureAudioUnlocked, unlock.payload)
        assertEquals(GameOutputKind.ENSURE_AUDIO_UNLOCKED, unlock.semanticHandle.outputKind)
        assertEquals(1u, unlock.sourceOrdinal)

        val frame = assertIs<GameDispatchResult.Committed>(
            ball.dispatch(GameIntent.FrameElapsed(0.25f)),
        )
        val advance = assertIs<GameCommand.AdvanceAudio>(frame.commands.single().payload)
        assertEquals(0.25f, advance.realDeltaSeconds)
        assertTrue(advance.cues.isEmpty())
        assertEquals(GameOutputKind.ADVANCE_AUDIO, frame.commands.single().semanticHandle.outputKind)
        assertEquals(1u, frame.commands.single().sourceOrdinal)

        val mute = assertIs<GameDispatchResult.Committed>(ball.dispatch(GameIntent.MuteToggled))
        val muteAdvance = assertIs<GameCommand.AdvanceAudio>(mute.commands.last().payload)
        assertEquals(0f, muteAdvance.realDeltaSeconds)
        assertEquals(listOf(SoundCue.UI_CLICK), muteAdvance.cues.toList())
        assertEquals(2u, mute.commands.last().sourceOrdinal)
    }

    @Test
    fun canonicalReadsAreStampedAndDoNotAdvanceRevision() {
        val ball = GameFeatureBall.create(bootstrapProgress = null, seed = 121)
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
        assertSame(initial.payload, repeated.payload)
        assertEquals(initial.consistencyStamp, repeated.consistencyStamp)

        assertFailsWith<IllegalArgumentException> {
            ball.read(GameQuery.GetGameProjection, ReadContext(protocolVersion = "unsupported"))
        }
        assertEquals(0uL, ball.projectionRead().consistencyStamp.commitRevision)

        val committed = assertIs<GameDispatchResult.Committed>(
            ball.dispatch(GameIntent.PointerMoved(120f, 180f)),
        )
        assertEquals(1uL, committed.sourceCommitRevision)
        assertEquals(
            ConsistencyStamp(
                ballInstanceId = GameProjection.BALL_INSTANCE_ID,
                commitRevision = 1uL,
                stateSchemaVersion = GameProjection.STATE_SCHEMA_VERSION,
            ),
            committed.projectionRead.consistencyStamp,
        )
        assertNotSame(initial.payload, committed.projectionRead.payload)
        assertSame(committed.projectionRead.payload, ball.projectionRead().payload)
    }

    @Test
    fun sameSeedDependenciesAndIntentTraceAreDeterministic() {
        val first = GameFeatureBall.create(bootstrapProgress = null, seed = 91_337)
        val second = GameFeatureBall.create(bootstrapProgress = null, seed = 91_337)
        val settings = GameSettings(simulationSpeed = 1.5f)
        val profile = profile(
            matter = 250,
            lifetimeMatter = 500,
            unlockedWeapons = setOf(WeaponId.FLUX_WAKE, WeaponId.MORNINGSTAR),
        )
        assertEquals(
            assertIs<GameDispatchResult.Committed>(
                first.observeDependencies(settings, profile),
            ).projectionRead.snapshot(),
            assertIs<GameDispatchResult.Committed>(
                second.observeDependencies(settings, profile),
            ).projectionRead.snapshot(),
        )

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

        trace.forEach { intent ->
            val firstResult = assertIs<GameDispatchResult.Committed>(first.dispatch(intent))
            val secondResult = assertIs<GameDispatchResult.Committed>(second.dispatch(intent))
            assertEquals(firstResult.sourceCommitRevision, secondResult.sourceCommitRevision)
            assertEquals(firstResult.commands, secondResult.commands)
            assertEquals(firstResult.visualFxCues, secondResult.visualFxCues)
            assertEquals(firstResult.projectionRead.snapshot(), secondResult.projectionRead.snapshot())
        }
    }

    @Test
    fun decisionAndAdmissionRejectionsAreAtomicAtTheCollectionBound() {
        val atLimit = GameFeatureBall.create(bootstrapProgress = null, seed = 501)
        val maximum = GameFeatureBall.LIMITS.maxCollectionItems
        val accepted = assertIs<GameDispatchResult.Committed>(
            atLimit.observeDependencies(
                settings = GameSettings(),
                profile = profile(
                    discoveredItemIds = (0 until maximum).toSet(),
                ),
            ),
        )
        assertEquals(1uL, accepted.sourceCommitRevision)
        assertTrue(accepted.commands.isEmpty())

        val overLimit = GameFeatureBall.create(bootstrapProgress = null, seed = 502)
        val before = overLimit.projectionRead()

        val invalid = assertIs<GameDispatchResult.DecisionRejected>(
            overLimit.dispatch(GameIntent.FrameElapsed(Float.NaN)),
        )
        assertIs<GameRejection.InvalidInput>(invalid.reason)
        val afterDecisionRejection = overLimit.projectionRead()
        assertEquals(before.consistencyStamp, afterDecisionRejection.consistencyStamp)
        assertSame(before.payload, afterDecisionRejection.payload)

        val rejected = assertIs<GameDispatchResult.AdmissionRejected>(
            overLimit.observeDependencies(
                settings = GameSettings(),
                profile = profile(
                    discoveredItemIds = (0..maximum).toSet(),
                ),
            ),
        )
        val failure = assertIs<AdmissionFailure.LimitExceeded>(rejected.reason)
        assertEquals(MandatoryDecisionLimit.COLLECTION_ITEMS, failure.limit)
        assertEquals((maximum + 1).toLong(), failure.actual)
        assertEquals(maximum.toLong(), failure.maximum)
        val afterAdmissionRejection = overLimit.projectionRead()
        assertEquals(before.consistencyStamp, afterAdmissionRejection.consistencyStamp)
        assertSame(before.payload, afterAdmissionRejection.payload)
    }

    @Test
    fun nucleusDecisionCopiesTheSourceModelBeforeMutation() {
        val nucleus = GameNucleus()
        val source = initialGameBallState(seed = 700, bootstrapProgress = null)
        val sourceBefore = source.model.toProjection().stateSnapshot()

        val decision = acceptedDecision(
            nucleus = nucleus,
            state = source,
            intent = GameIntent.RunStartRequested,
            operationId = OperationId(1uL),
        )

        assertNotSame(source.model, decision.nextState.model)
        assertEquals(sourceBefore, source.model.toProjection().stateSnapshot())
        assertEquals(GamePhase.MENU, source.model.toProjection().phase)
        assertEquals(GamePhase.RUNNING, decision.nextState.model.toProjection().phase)

        decision.nextState.model.updatePointer(120f, 160f)
        assertEquals(sourceBefore, source.model.toProjection().stateSnapshot())
        assertEquals(120f, decision.nextState.model.toProjection().pointerX)
        assertNotEquals(
            source.model.toProjection().stateSnapshot(),
            decision.nextState.model.toProjection().stateSnapshot(),
        )
    }

    private data class OutputCase(
        val state: GameBallState,
        val intent: GameIntent,
        val commandKinds: List<GameOutputKind>,
    )

    private fun acceptedDecision(
        nucleus: GameNucleus,
        state: GameBallState,
        intent: GameIntent,
        operationId: OperationId,
    ) = assertIs<Accepted<GameBallState, SemanticOutput>>(
        nucleus.decide(state, intent, context(operationId)),
    ).decision

    private fun observedState(
        nucleus: GameNucleus,
        profile: GameProfileReplica,
        settings: GameSettings = GameSettings(),
    ): GameBallState = assertIs<Accepted<GameBallState, SemanticOutput>>(
        nucleus.decide(
            state = initialGameBallState(seed = 300, bootstrapProgress = null),
            pulse = GameFact.DependenciesObserved(settings, profile),
            context = context(OperationId(300uL)),
        ),
    ).decision.nextState

    private fun context(operationId: OperationId): GameDecisionContext = GameDecisionContext(
        operationId = operationId,
        causalBudgetScope = operationId,
    )
}

private fun profile(
    matter: Long = 0,
    lifetimeMatter: Long = matter,
    coreShape: CoreShape = CoreShape.ORB,
    selectedWeapon: WeaponId = WeaponId.FLUX_WAKE,
    unlockedWeapons: Set<WeaponId> = setOf(WeaponId.FLUX_WAKE),
    metaRanks: List<Int> = List(MetaUpgradeId.entries.size) { 0 },
    discoveredItemIds: Set<Int> = emptySet(),
    rebirthLevel: Int = 0,
    highestClearedRebirth: Int = -1,
    activeRebirthProfile: RebirthProfile = RebirthProgression.profile(rebirthLevel),
    nextRebirthProfile: RebirthProfile = RebirthProgression.profile(rebirthLevel + 1),
): GameProfileReplica = GameProfileReplica(
    matter = matter,
    lifetimeMatter = lifetimeMatter,
    coreShape = coreShape,
    selectedWeapon = selectedWeapon,
    unlockedWeapons = unlockedWeapons.toImmutableSet(),
    metaRanks = metaRanks.toImmutableList(),
    discoveredItemIds = discoveredItemIds.toImmutableSet(),
    rebirthLevel = rebirthLevel,
    highestClearedRebirth = highestClearedRebirth,
    activeRebirthProfile = activeRebirthProfile,
    nextRebirthProfile = nextRebirthProfile,
)

private fun settingsDependencyStamp(commitRevision: ULong): ConsistencyStamp = ConsistencyStamp(
    ballInstanceId = GameDependencyContract.SETTINGS_BALL_INSTANCE_ID,
    commitRevision = commitRevision,
    stateSchemaVersion = GameDependencyContract.SETTINGS_STATE_SCHEMA_VERSION,
)

private fun profileDependencyStamp(commitRevision: ULong): ConsistencyStamp = ConsistencyStamp(
    ballInstanceId = GameDependencyContract.PROFILE_BALL_INSTANCE_ID,
    commitRevision = commitRevision,
    stateSchemaVersion = GameDependencyContract.PROFILE_STATE_SCHEMA_VERSION,
)

private fun gameRunStartCommand(
    profileRevision: ULong,
    rebirthLevel: Int,
    sourceCommitRevision: ULong = 2uL,
    sourceOrdinal: UInt = 0u,
    sourceOperationId: ULong = 17uL,
): GameRunStartModuleCommand {
    val source = GameRunStartCommandSource(
        sourceBallInstanceId = GameRunStartContract.SOURCE_BALL_INSTANCE_ID,
        sourceCommitRevision = sourceCommitRevision,
        sourceOrdinal = sourceOrdinal,
        sourceOperationId = sourceOperationId,
        sourceOutputKind = GameRunStartContract.SOURCE_OUTPUT_KIND,
        sourceLocalOrdinalOrName = GameRunStartContract.SOURCE_LOCAL_ORDINAL_OR_NAME,
    )
    return GameRunStartModuleCommand(
        commandSource = source,
        causalBudgetScope = GameRunStartCausalScope(
            ownerBallInstanceId = GameRunStartContract.CAUSAL_SCOPE_OWNER_BALL_INSTANCE_ID,
            operationId = source.sourceOperationId,
        ),
        causalDepth = GameRunStartContract.COMMAND_CAUSAL_DEPTH,
        runConfigurationReference = GameRunConfigurationReference(
            profileBallInstanceId = GameDependencyContract.PROFILE_BALL_INSTANCE_ID,
            profileCommitRevision = profileRevision,
            profileStateSchemaVersion = GameDependencyContract.PROFILE_STATE_SCHEMA_VERSION,
            rebirthLevel = rebirthLevel,
        ),
    )
}

private fun expectedOutputKind(command: GameCommand): GameOutputKind = when (command) {
    is GameCommand.AdvanceAudio -> GameOutputKind.ADVANCE_AUDIO
    GameCommand.EnsureAudioUnlocked -> GameOutputKind.ENSURE_AUDIO_UNLOCKED
    is GameCommand.ChangeSettings -> GameOutputKind.CHANGE_SETTINGS
    is GameCommand.ChangeProfile -> GameOutputKind.CHANGE_PROFILE
    is GameCommand.BeginRebirth -> GameOutputKind.BEGIN_REBIRTH
}

private fun GameFeatureBall.projectionRead(): ReadResult<GameProjection> =
    assertIs<GameQueryResult.Projection>(query(GameQuery.GetGameProjection)).value

private data class ProjectionSnapshot(
    val stamp: ConsistencyStamp,
    val state: GameStateSnapshot,
)

private data class GameStateSnapshot(
    val phase: GamePhase,
    val screen: UiScreen,
    val settings: GameSettings,
    val rebirthLevel: Int,
    val highestClearedRebirth: Int,
    val rebirthConfirmationArmed: Boolean,
    val coreX: Float,
    val coreY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val cameraX: Float,
    val cameraY: Float,
    val pointerX: Float,
    val pointerY: Float,
    val elapsed: Float,
    val heat: Float,
    val hp: Float,
    val shield: Float,
    val level: Int,
    val data: Int,
    val keys: Int,
    val kills: Int,
    val combo: Int,
    val runMatter: Long,
    val totalMatter: Long,
    val lifetimeMatter: Long,
    val message: String,
    val weapon: WeaponId,
    val startingWeapon: WeaponId,
    val weaponLevel: Int,
    val coreShape: CoreShape,
    val acquiredItemCount: Int,
    val discoveredItemCount: Int,
    val unlockedWeapons: Set<WeaponId>,
    val metaRanks: List<Int>,
    val enemies: List<Any>,
    val projectiles: List<Any>,
    val pickups: List<Any>,
    val trail: List<Any>,
    val weaponNodes: List<Any>,
    val weaponOrbitals: List<Any>,
    val choices: List<Any>,
)

private fun ReadResult<GameProjection>.snapshot(): ProjectionSnapshot = ProjectionSnapshot(
    stamp = consistencyStamp,
    state = payload.stateSnapshot(),
)

private fun GameProjection.stateSnapshot(): GameStateSnapshot = GameStateSnapshot(
    phase = phase,
    screen = screen,
    settings = settings,
    rebirthLevel = rebirthLevel,
    highestClearedRebirth = highestClearedRebirth,
    rebirthConfirmationArmed = rebirthConfirmationArmed,
    coreX = coreX,
    coreY = coreY,
    velocityX = velocityX,
    velocityY = velocityY,
    cameraX = cameraX,
    cameraY = cameraY,
    pointerX = pointerX,
    pointerY = pointerY,
    elapsed = elapsed,
    heat = heat,
    hp = hp,
    shield = shield,
    level = level,
    data = data,
    keys = keys,
    kills = kills,
    combo = combo,
    runMatter = runMatter,
    totalMatter = totalMatter,
    lifetimeMatter = lifetimeMatter,
    message = message,
    weapon = weapon,
    startingWeapon = startingWeapon,
    weaponLevel = weaponLevel,
    coreShape = coreShape,
    acquiredItemCount = acquiredItemCount,
    discoveredItemCount = discoveredItemCount,
    unlockedWeapons = unlockedWeapons.toSet(),
    metaRanks = MetaUpgradeId.entries.map(::metaLevel),
    enemies = enemies.toList(),
    projectiles = projectiles.toList(),
    pickups = pickups.toList(),
    trail = trail.toList(),
    weaponNodes = weaponNodes.toList(),
    weaponOrbitals = weaponOrbitals.toList(),
    choices = choices.toList(),
)
