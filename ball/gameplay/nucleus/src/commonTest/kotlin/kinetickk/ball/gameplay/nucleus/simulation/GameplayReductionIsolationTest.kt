// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RewardFocus
import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.DirectorPersonalityId
import kinetickk.ball.content.api.EliteAffix
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.OmenId
import kinetickk.ball.content.api.RebirthMutator
import kinetickk.ball.content.api.SignalKind
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.WorldEventKind
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.content.api.DimensionObjective
import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.nucleus.model.ActiveBuff
import kinetickk.ball.gameplay.nucleus.model.CombatFieldKind
import kinetickk.ball.gameplay.nucleus.model.FieldFlags
import kinetickk.ball.gameplay.nucleus.model.FieldSource
import kinetickk.ball.gameplay.nucleus.model.EchoHit
import kinetickk.ball.gameplay.nucleus.model.Obstacle
import kinetickk.ball.gameplay.nucleus.model.ObstacleField
import kinetickk.ball.gameplay.nucleus.model.SeveredRelic
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime
import kinetickk.ball.gameplay.nucleus.model.ApexCell
import kinetickk.ball.gameplay.nucleus.model.SkillCell
import kinetickk.ball.gameplay.nucleus.model.SkillLoadout
import kinetickk.ball.gameplay.nucleus.model.SkillRuntime
import kinetickk.ball.gameplay.nucleus.model.Polarity
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.AmbientSiteWindow
import kinetickk.ball.gameplay.nucleus.model.DimensionPortal
import kinetickk.ball.gameplay.nucleus.model.DimensionRewardGrant
import kinetickk.ball.gameplay.nucleus.model.DimensionRunState
import kinetickk.ball.gameplay.nucleus.model.DimensionVisit
import kinetickk.ball.gameplay.nucleus.model.DungeonState
import kinetickk.ball.gameplay.nucleus.model.Ember
import kinetickk.ball.gameplay.nucleus.model.HaloRing
import kinetickk.ball.gameplay.nucleus.model.PlacedStructure
import kinetickk.ball.gameplay.nucleus.model.SignalPrompt
import kinetickk.ball.gameplay.nucleus.model.SitePoint
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.model.StructureField
import kinetickk.ball.gameplay.nucleus.model.StructureSignal
import kinetickk.ball.gameplay.nucleus.model.Wall
import kinetickk.ball.gameplay.nucleus.model.WorldEventPlan
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.Totem
import kinetickk.ball.gameplay.nucleus.model.TrailPoint
import kinetickk.ball.gameplay.nucleus.model.WeaponNode
import kinetickk.ball.gameplay.nucleus.model.WeaponEntity
import kinetickk.ball.gameplay.nucleus.model.WeaponOrbital
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
import kinetickk.ball.gameplay.nucleus.reducer.GameReducer
import kinetickk.ball.gameplay.nucleus.reducer.GameReductionResult
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.render.WeaponEntityKind
import kinetickk.ball.gameplay.nucleus.render.WeaponNodeType
import kinetickk.ball.gameplay.nucleus.render.EventSignalProjection
import kinetickk.ball.gameplay.nucleus.render.EventStrikeProjection
import kinetickk.ball.gameplay.nucleus.render.WallKind
import kinetickk.ball.gameplay.nucleus.render.WorldEventProjection
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.immutableListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameplayReductionIsolationTest {
    @Test
    fun candidateForksPreserveSelectedLoadoutAndPendingChoiceWithoutApplyingBootstrapDefaults() {
        val source = MutableGameState(canonicalGameplayContent, seed = 12_003, initialMatter = 57).apply {
            unlockedWeaponSet += WeaponId.MORNINGSTAR
            unlockedWeaponView = unlockedWeaponSet.toSet()
            startingWeapon = WeaponId.MORNINGSTAR
            coreShape = CoreShape.PRISM
            settings = settings.copy(soundEnabled = false, masterVolume = 0.25f)
            startRun()
            acquireItemForTesting(0)
            acquireRelicForTesting(RelicId.ECHO_CHAMBER)
            openItemChoice()
            pendingBindingRelic = RelicId.ECHO_CHAMBER
            pendingRelicBindAction = RelicChoiceAction.MELD
            pendingClearedRebirthLevel = 3
            totem = Totem(81f, 29f, 0.5f)
            velocityX = -0f
            weaponBeamTime = Float.fromBits(0x7fc00001)
            populateAdditionalRetainedState()
            populateWorldControlState()
        }

        listOf(source.copyForReduction(), source.copyForScalarInputReduction()).forEach { fork ->
            assertEquivalentSimulation(source, fork)
            val projection = fork.toRenderModel()
            assertEquals(WeaponId.MORNINGSTAR, projection.weapon)
            assertEquals(CoreShape.PRISM, projection.coreShape)
            assertEquals(source.settings, projection.settings)
            assertEquals(source.choices, projection.choices)
        }

        // A later cleared value must stay cleared, including fields with no bootstrap value.
        val cleared = source.copyForReduction().apply {
            pendingBindingRelic = null
            pendingRelicBindAction = null
            pendingActiveItemId = null
            pendingActiveReborn = false
            pendingClearedRebirthLevel = null
            recentItem = null
            totem = null
            characterRuntime.hasDashOrigin = false
            pendingArchitectDefeatedWith = null
            directedReward = null
            selectedRewardFocus = null
            dimensionRewardLayer = null
        }
        listOf(cleared.copyForReduction(), cleared.copyForScalarInputReduction()).forEach { fork ->
            assertEquivalentSimulation(cleared, fork)
        }
    }

    @Test
    fun everyScalarCowIntentLeavesTheCompleteCommittedSourceFingerprintUnchanged() {
        val source = MutableGameState(
            content = canonicalGameplayContent,
            seed = 1_111,
            initialMatter = 0,
        ).apply {
            unlockedWeaponSet += WeaponId.MORNINGSTAR
            unlockedWeaponView = unlockedWeaponSet.toSet()
            matrixRanks[0] = 1
            discoveredItemIds += 17
            itemStacks[0] = 2
            rebornItems[0] = 1
            relicRanks[RelicId.ECHO_CHAMBER.ordinal] = 1
            addEnemyForTesting(x = 20f, y = 30f).status[StatusCell.STACKS + StatusElement.ION.ordinal] = 3f
            projectiles += Projectile(
                x = 40f,
                y = 50f,
                vx = 0f,
                vy = 0f,
                radius = 2f,
                life = 1f,
                hostile = false,
            )
            pickups += Pickup(PickupType.DATA, 60f, 70f)
            trail += TrailPoint(80f, 90f)
            weaponNodes += WeaponNode(
                type = WeaponNodeType.GRAVITY_MINE,
                x = 100f,
                y = 110f,
                life = 1f,
                maxLife = 2f,
                radius = 3f,
            )
            weaponOrbitals += WeaponOrbital(index = 0, x = 120f, y = 130f, radius = 4f)
            totem = Totem(140f, 150f, 0.25f)
        }
        val committedFingerprint = source.exactSimulationFingerprint()
        val sourceEngine = EngineState(source)
        val reducer = GameReducer()
        val scalarIntents = listOf(
            GameplayInteractionPulse.ViewportChanged.fromValidated(1_024f, 768f, 2f),
            GameplayInteractionPulse.PointerMoved.fromValidated(901f, 361f),
            GameplayInteractionPulse.BrakeChanged(
                source = BrakeSource.KEYBOARD,
                active = true,
            ),
            GameplayInteractionPulse.DashRequested,
            GameplayInteractionPulse.PauseToggled,
        )

        scalarIntents.forEach { intent ->
            val reduced = assertIs<GameReductionResult.Accepted>(
                reducer.reduce(sourceEngine, intent),
                "scalar intent $intent",
            ).state.model

            assertEquals(
                committedFingerprint,
                source.exactSimulationFingerprint(),
                "scalar intent mutated its committed source: $intent",
            )
            assertFalse(
                reduced.exactSimulationFingerprint() == committedFingerprint,
                "fixture did not exercise a state-changing scalar intent: $intent",
            )
        }
    }

    @Test
    fun fullReductionAfterScalarCowDoesNotMutateSourceOrSiblingBranch() {
        val relicIndex = RelicId.ECHO_CHAMBER.ordinal
        val weaponIndex = WeaponId.FLUX_WAKE.ordinal
        val source = MutableGameState(
            content = canonicalGameplayContent,
            seed = 1_337,
            initialMatter = 0,
        ).apply {
            unlockedWeaponSet += WeaponId.MORNINGSTAR
            unlockedWeaponView = unlockedWeaponSet.toSet()
            matrixRanks[0] = 1
            characterUpgradeLevels[0] = 4
            weaponUpgradeLevels[weaponIndex] = 6
            discoveredItemIds += 17
            itemStacks[0] = 2
            rebornItems[0] = 1
            clauseCounters[0] = 3
            statusAccumulators[0] = 0.25f
            relicRanks[relicIndex] = 1
            relicCooldowns[relicIndex] = 0.75f
            relicCounters[relicIndex] = 2
            clauseReadyAt[0] = 0.4f
            itemStatusReadyAt[0] = 0.6f
            agonyMutationCounts[weaponIndex] = 4

            val enemy = addEnemyForTesting(x = 10f, y = 20f)
            enemy.status[StatusCell.STACKS + StatusElement.CRYO.ordinal] = 5f
            enemy.status[StatusCell.DECAY_AT + StatusElement.CRYO.ordinal] = 0.5f
            enemy.status[StatusCell.CINDER_BANK] = 6f
            echoHits += EchoHit(
                enemyId = enemy.id,
                delay = 1f,
                damage = 7f,
                appliesDominant = false,
            )

            projectiles += Projectile(
                x = 11f,
                y = 21f,
                vx = 0f,
                vy = 0f,
                radius = 2f,
                life = 1f,
                hostile = false,
            ).also { projectile ->
                assertTrue(projectile.tryRecordEnemyHit(enemy.id))
            }
            pickups += Pickup(PickupType.DATA, 12f, 22f)
            trail += TrailPoint(13f, 23f)
            weaponNodes += WeaponNode(
                type = WeaponNodeType.GRAVITY_MINE,
                x = 14f,
                y = 24f,
                life = 1f,
                maxLife = 2f,
                radius = 3f,
            )
            weaponOrbitals += WeaponOrbital(index = 0, x = 15f, y = 25f, radius = 4f)
            weaponRuntime.entities += WeaponEntity(WeaponEntityKind.DISC, x = 17f, y = 27f, radius = 5f, life = 1f, maxLife = 1f)
                .also { it.recordHit(enemy.id) }
            weaponRuntime.tetherRamp = 1.5f
            totem = Totem(16f, 26f, 0.25f)
        }
        val expectedNextRandom = source.gameplayRandom.copy().nextInt()
        val reducer = GameReducer()
        val sourceEngine = EngineState(source)

        val pointerBranch = assertIs<GameReductionResult.Accepted>(
            reducer.reduce(
                sourceEngine,
                GameplayInteractionPulse.PointerMoved.fromValidated(901f, 360f),
            ),
        ).state
        val siblingBranch = assertIs<GameReductionResult.Accepted>(
            reducer.reduce(
                sourceEngine,
                GameplayInteractionPulse.BrakeChanged(
                    source = BrakeSource.KEYBOARD,
                    active = true,
                ),
            ),
        ).state

        // These assertions prove the test actually traverses the optimized scalar-COW path.
        assertSame(source.gameplayRandom, pointerBranch.model.gameplayRandom)
        assertSame(source.matrixRanks, pointerBranch.model.matrixRanks)
        assertSame(source.characterUpgradeLevels, pointerBranch.model.characterUpgradeLevels)
        assertSame(source.weaponUpgradeLevels, pointerBranch.model.weaponUpgradeLevels)
        assertSame(source.enemies, pointerBranch.model.enemies)
        assertSame(source.projectiles, pointerBranch.model.projectiles)
        assertSame(source.enemies, siblingBranch.model.enemies)

        // A reroll outside a choice is a production full reduction that simulates nothing, so it
        // isolates copy ownership alone. (A sub-step frame deliberately shares storage instead.)
        val isolated = assertIs<GameReductionResult.Accepted>(
            reducer.reduce(
                pointerBranch,
                GameplayInteractionPulse.ChoicesRerolled,
            ),
        ).state.model

        assertNotSame(pointerBranch.model.gameplayRandom, isolated.gameplayRandom)
        assertNotSame(pointerBranch.model.matrixRanks, isolated.matrixRanks)
        assertNotSame(pointerBranch.model.characterUpgradeLevels, isolated.characterUpgradeLevels)
        assertNotSame(pointerBranch.model.weaponUpgradeLevels, isolated.weaponUpgradeLevels)
        assertNotSame(pointerBranch.model.enemies, isolated.enemies)
        assertNotSame(pointerBranch.model.projectiles, isolated.projectiles)
        assertNotSame(pointerBranch.model.totem, isolated.totem)
        assertSame(source.weaponRuntime, pointerBranch.model.weaponRuntime)
        assertNotSame(pointerBranch.model.weaponRuntime, isolated.weaponRuntime)

        // Mutate every mutable storage family that the scalar COW path is allowed to share.
        isolated.gameplayRandom.nextInt()
        isolated.unlockedWeaponSet.clear()
        isolated.matrixRanks[0] = 91
        isolated.characterUpgradeLevels[0] = 9
        isolated.weaponUpgradeLevels[weaponIndex] = 10
        isolated.discoveredItemIds.clear()
        isolated.itemStacks[0] = 92
        isolated.rebornItems[0] = 0
        isolated.clauseCounters[0] = 93
        isolated.statusAccumulators[0] = 0.75f
        isolated.relicRanks[relicIndex] = 94
        isolated.relicCooldowns[relicIndex] = 95f
        isolated.relicCounters[relicIndex] = 96
        isolated.clauseReadyAt[0] = 97f
        isolated.itemStatusReadyAt[0] = 97.5f
        isolated.agonyMutationCounts[weaponIndex] = 98
        isolated.echoHits.single().delay = 99f
        isolated.enemies.single().apply {
            x = 100f
            status[StatusCell.STACKS + StatusElement.CRYO.ordinal] = 101f
            status[StatusCell.DECAY_AT + StatusElement.CRYO.ordinal] = 102f
            status[StatusCell.CINDER_BANK] = 103f
        }
        isolated.projectiles.single().apply {
            x = 104f
            assertTrue(tryRecordEnemyHit(10_000))
        }
        isolated.pickups.single().x = 105f
        isolated.trail.single().x = 106f
        isolated.weaponNodes.single().x = 107f
        isolated.weaponOrbitals.single().x = 108f
        isolated.totem!!.x = 109f
        isolated.weaponRuntime.entities.single().apply {
            x = 110f
            recordHit(10_001)
        }
        isolated.weaponRuntime.tetherRamp = 2.5f

        listOf(source, siblingBranch.model).forEach { retained ->
            assertEquals(expectedNextRandom, retained.gameplayRandom.copy().nextInt())
            assertTrue(WeaponId.FLUX_WAKE in retained.unlockedWeaponSet)
            assertTrue(WeaponId.MORNINGSTAR in retained.unlockedWeaponSet)
            assertEquals(1, retained.matrixRanks[0])
            assertEquals(4, retained.characterUpgradeLevels[0])
            assertEquals(6, retained.weaponUpgradeLevels[weaponIndex])
            assertTrue(17 in retained.discoveredItemIds)
            assertEquals(2, retained.itemStacks[0])
            assertEquals(1, retained.rebornItems[0])
            assertEquals(3, retained.clauseCounters[0])
            assertEquals(0.25f, retained.statusAccumulators[0])
            assertEquals(1, retained.relicRanks[relicIndex])
            assertEquals(0.75f, retained.relicCooldowns[relicIndex])
            assertEquals(2, retained.relicCounters[relicIndex])
            assertEquals(0.4f, retained.clauseReadyAt[0])
            assertEquals(0.6f, retained.itemStatusReadyAt[0])
            assertEquals(4, retained.agonyMutationCounts[weaponIndex])
            assertEquals(1f, retained.echoHits.single().delay)

            retained.enemies.single().let { enemy ->
                assertEquals(10f, enemy.x)
                assertEquals(5f, enemy.status[StatusCell.STACKS + StatusElement.CRYO.ordinal])
                assertEquals(0.5f, enemy.status[StatusCell.DECAY_AT + StatusElement.CRYO.ordinal])
                assertEquals(6f, enemy.status[StatusCell.CINDER_BANK])
            }
            retained.projectiles.single().let { projectile ->
                assertEquals(11f, projectile.x)
                assertFalse(projectile.hasRecordedEnemyHit(10_000))
            }
            assertEquals(12f, retained.pickups.single().x)
            assertEquals(13f, retained.trail.single().x)
            assertEquals(14f, retained.weaponNodes.single().x)
            assertEquals(15f, retained.weaponOrbitals.single().x)
            assertEquals(16f, retained.totem!!.x)
            retained.weaponRuntime.entities.single().let { entity ->
                assertEquals(17f, entity.x)
                assertFalse(entity.hasHit(10_001))
            }
            assertEquals(1.5f, retained.weaponRuntime.tetherRamp)
        }
    }

    @Test
    fun scalarReductionDrainsCopiedPendingOutputsWithoutMutatingSourceOrSiblings() {
        val source = MutableGameState(canonicalGameplayContent, seed = 6_101).apply {
            pendingBankedMatter = 7L
            pendingDiscoveredItemIds += 0
            pendingEliteKills = 2
            pendingDashHits = 3
            pendingCompletedOrbits = 4
            pendingArchitectDefeatedWith = CoreShape.PRISM
            emitSound(GameplayAudioCue.UI_CLICK)
            emitVisualFx(VisualFxCue.EffectsAdvanced(0.125f))
            emitVisualFx(VisualFxCue.ClearWeaponArcs)
            emitVisualFx(VisualFxCue.EffectsAdvanced(0.25f))
        }
        val reducer = GameReducer()
        val sourceEngine = EngineState(source)

        val reductions = List(3) {
            assertIs<GameReductionResult.Accepted>(
                reducer.reduce(
                    sourceEngine,
                    GameplayInteractionPulse.PointerMoved.fromValidated(901f, 360f),
                ),
            )
        }

        reductions.forEach { reduction ->
            assertFalse(reduction.state.model.hasPendingReductionOutputs())
            assertSame(source.matrixRanks, reduction.state.model.matrixRanks)
            assertSame(source.enemies, reduction.state.model.enemies)
            assertEquals(
                listOf(
                    VisualFxCue.EffectsAdvanced(0.125f),
                    VisualFxCue.ClearWeaponArcs,
                    VisualFxCue.EffectsAdvanced(0.25f),
                ),
                reduction.outputs.visualFxCuesOrNull?.toList(),
            )
            val progress = assertNotNull(reduction.outputs.progressUpdate)
            assertEquals(2, progress.eliteKills)
            assertEquals(3, progress.dashHits)
            assertEquals(4, progress.completedOrbits)
            assertEquals(CoreShape.PRISM, progress.architectDefeatedWith)
        }
        assertEquals(reductions[0].outputs, reductions[1].outputs)
        assertEquals(reductions[1].outputs, reductions[2].outputs)

        // The committed source remains independently drainable after every sibling reduction.
        assertTrue(source.hasPendingReductionOutputs())
        assertEquals(listOf(GameplayAudioCue.UI_CLICK), source.takeSoundCues())
        assertEquals(
            listOf(
                VisualFxCue.EffectsAdvanced(0.125f),
                VisualFxCue.ClearWeaponArcs,
                VisualFxCue.EffectsAdvanced(0.25f),
            ),
            source.takeVisualFxCues().toList(),
        )
        val progress = source.takeProgressUpdate()
        assertEquals(7L, progress?.bankedMatter)
        assertTrue(0 in requireNotNull(progress).discoveredItemIds)
        assertEquals(2, progress.eliteKills)
        assertEquals(3, progress.dashHits)
        assertEquals(4, progress.completedOrbits)
        assertEquals(CoreShape.PRISM, progress.architectDefeatedWith)
        assertFalse(source.hasPendingReductionOutputs())
    }

    @Test
    fun unchangedPointerDrainsAchievementOnlyProgressWithoutMutatingSource() {
        assertAchievementOnlyProgressIsDrained { source ->
            source.updatePointer(source.pointerX, source.pointerY, source.pointerActive)
            GameplayInteractionPulse.PointerMoved.fromValidated(
                source.pointerX,
                source.pointerY,
                source.pointerActive,
            )
        }
    }

    @Test
    fun pausedFrameDrainsAchievementOnlyProgressWithoutMutatingSource() {
        assertAchievementOnlyProgressIsDrained { source ->
            source.togglePause()
            GameplayInteractionPulse.FrameElapsed.fromValidated(1f / 60f)
        }
    }

    private fun assertAchievementOnlyProgressIsDrained(
        preparePulse: (MutableGameState) -> GameplayInteractionPulse,
    ) {
        val cases: List<Pair<String, MutableGameState.() -> Unit>> = listOf(
            "eliteKills" to { pendingEliteKills = 2 },
            "dashHits" to { pendingDashHits = 3 },
            "completedOrbits" to { pendingCompletedOrbits = 4 },
            "architectDefeatedWith" to { pendingArchitectDefeatedWith = CoreShape.PRISM },
        )
        cases.forEach { (name, retainProgress) ->
            val source = MutableGameState(canonicalGameplayContent, seed = 6_102).apply(retainProgress)
            val pulse = preparePulse(source)
            val retainedFingerprint = source.exactSimulationFingerprint()
            // The expected result is explicit owner data, not a copy/drain of the source.
            val expected = kinetickk.ball.profile.api.GameplayProgressUpdate(
                bankedMatter = 0L,
                discoveredItemIds = emptySet(),
                eliteKills = if (name == "eliteKills") 2 else 0,
                dashHits = if (name == "dashHits") 3 else 0,
                completedOrbits = if (name == "completedOrbits") 4 else 0,
                architectDefeatedWith = CoreShape.PRISM.takeIf { name == "architectDefeatedWith" },
            )

            val reducer = GameReducer()
            val accepted = assertIs<GameReductionResult.Accepted>(
                reducer.reduce(EngineState(source), pulse),
                name,
            )

            assertEquals(retainedFingerprint, source.exactSimulationFingerprint(), name)
            assertEquals(expected, accepted.outputs.progressUpdate, "$name must be delivered")
            assertFalse(accepted.state.model.hasPendingReductionOutputs(), "$name must be drained")
            val repeated = assertIs<GameReductionResult.Accepted>(reducer.reduce(accepted.state, pulse))
            assertNull(repeated.outputs.progressUpdate, "$name must not be delivered twice")
            assertEquals(retainedFingerprint, source.exactSimulationFingerprint(), name)
        }
    }

    @Test
    fun subStepFrameRunsNoStepSharesStableStorageAndLeavesTheCommittedStateUntouched() {
        val source = MutableGameState(canonicalGameplayContent, seed = 7_331).apply {
            startRun()
            spawnClock = 1_000f
            setVelocityForTesting(640f, -120f)
            addEnemyForTesting(x = 320f, y = 40f).apply { vx = -90f; vy = 12f }
            projectiles += Projectile(x = 30f, y = 0f, vx = 500f, vy = 0f, radius = 3f, life = 1f, hostile = false)
            pickups += Pickup(PickupType.DATA, 60f, 10f, vx = 40f)
            weaponOrbitals += WeaponOrbital(index = 0, x = 15f, y = 25f, radius = 4f)
            takeSoundCues()
            takeVisualFxCues()
        }
        val reducer = GameReducer()
        // One full frame first, so the committed source reports executed steps.
        val committed = assertIs<GameReductionResult.Accepted>(
            reducer.reduce(EngineState(source), GameplayInteractionPulse.FrameElapsed.fromValidated(MutableGameState.FIXED_STEP * 1.5f)),
        ).state.model
        committed.takeSoundCues()
        committed.takeVisualFxCues()
        assertTrue(committed.lastTransitionSteps > 0)
        val retained = committed.exactSimulationFingerprint()
        val randomBefore = committed.gameplayRandom.copy().nextInt()

        val subStep = assertIs<GameReductionResult.Accepted>(
            reducer.reduce(EngineState(committed), GameplayInteractionPulse.FrameElapsed.fromValidated(MutableGameState.FIXED_STEP * 0.25f)),
        )
        val candidate = subStep.state.model

        assertEquals(0, candidate.lastTransitionSteps)
        assertEquals(committed.elapsed, candidate.elapsed)
        assertTrue(candidate.accumulator > committed.accumulator && candidate.accumulator < MutableGameState.FIXED_STEP)
        assertNull(subStep.outputs.visualFxCuesOrNull)
        assertNull(subStep.outputs.progressUpdate)
        assertEquals(MutableGameState.FIXED_STEP * 0.25f, subStep.outputs.audioRealDeltaSeconds)
        assertSame(committed.enemies, candidate.enemies)
        assertSame(committed.projectiles, candidate.projectiles)
        assertSame(committed.pickups, candidate.pickups)
        assertSame(committed.weaponOrbitals, candidate.weaponOrbitals)
        assertSame(committed.gameplayRandom, candidate.gameplayRandom)
        assertEquals(retained, committed.exactSimulationFingerprint())
        assertEquals(randomBefore, committed.gameplayRandom.copy().nextInt())

        // The next frame that completes the step runs it on an isolated copy; both retained states stay intact.
        val retainedCandidate = candidate.exactSimulationFingerprint()
        val stepped = assertIs<GameReductionResult.Accepted>(
            reducer.reduce(subStep.state, GameplayInteractionPulse.FrameElapsed.fromValidated(MutableGameState.FIXED_STEP)),
        ).state.model
        assertEquals(1, stepped.lastTransitionSteps)
        assertNotSame(candidate.enemies, stepped.enemies)
        assertEquals(retained, committed.exactSimulationFingerprint())
        assertEquals(retainedCandidate, candidate.exactSimulationFingerprint())
    }

    @Test
    fun achievementCountersWaitForTheBankAndNeverForceAnIdleFrameToPublish() {
        val source = MutableGameState(canonicalGameplayContent, seed = 6_104).apply {
            pendingTotalKills = 5
            pendingRicochets = 6
            pendingDataPickups = 7
        }
        val reducer = GameReducer()
        val pointer = GameplayInteractionPulse.PointerMoved.fromValidated(source.pointerX, source.pointerY, source.pointerActive)
        val idle = assertIs<GameReductionResult.Accepted>(reducer.reduce(EngineState(source), pointer))
        assertNull(idle.outputs.progressUpdate)
        assertSame(source, idle.state.model, "counters alone leave an unchanged input unreduced")

        val banked = source.copyForReduction().apply { bankRunMatter() }
        val update = assertNotNull(banked.takeProgressUpdate())
        assertEquals(5, update.totalKills)
        assertEquals(6, update.ricochets)
        assertEquals(7, update.dataPickups)
        assertFalse(banked.hasPendingReductionOutputs())
        assertEquals(5, source.pendingTotalKills, "the committed source keeps its counters")

        // Any other progress carries the counters along without waiting for the bank.
        val elite = source.copyForReduction().apply { pendingEliteKills = 1 }
        assertEquals(5, assertNotNull(elite.takeProgressUpdate()).totalKills)
    }

    @Test
    fun threeProductionFrameSiblingsDetachOnlyActiveRelicTimersAndRemainDeterministic() {
        val furnaceIndex = RelicId.FURNACE_COVENANT.ordinal
        val codexIndex = RelicId.PANDEMIC_CODEX.ordinal
        val thermal = StatusElement.THERMAL.ordinal
        val source = MutableGameState(canonicalGameplayContent, seed = 7_201).apply {
            startRun()
            enemies.clear()
            spawnClock = 1_000f
            // Admit exactly one fixed step independently of presentation-speed preferences.
            accumulator = MutableGameState.FIXED_STEP
            acquireRelicForTesting(RelicId.FURNACE_COVENANT)
            acquireRelicForTesting(RelicId.PANDEMIC_CODEX)
            relicCooldowns[furnaceIndex] = 0.8f
            relicCounters[codexIndex] = 2
            val enemy = addEnemyForTesting(x = 240f, y = 40f)
            // A due decay is an event: it is the only status write this frame performs.
            enemy.status[StatusCell.STACKS + thermal] = 3f
            enemy.status[StatusCell.DECAY_AT + thermal] = 0f
        }
        // Make the source a committed, drained state just like a production component frame.
        source.takeSoundCues()
        source.takeVisualFxCues()
        val sourceEngine = EngineState(source)
        val reducer = GameReducer()

        val siblings = List(3) {
            assertIs<GameReductionResult.Accepted>(
                reducer.reduce(
                    sourceEngine,
                    GameplayInteractionPulse.FrameElapsed.fromValidated(0f),
                ),
            ).state.model
        }

        siblings.forEach { branch ->
            assertFalse(branch.relicCooldowns.sharesStorageWith(source.relicCooldowns))
            assertFalse(branch.enemies.single().status.sharesStorageWith(source.enemies.single().status))
            assertTrue(branch.relicRanks.sharesStorageWith(source.relicRanks))
            assertTrue(branch.relicCounters.sharesStorageWith(source.relicCounters))
            assertTrue(branch.clauseReadyAt.sharesStorageWith(source.clauseReadyAt))
            assertTrue(branch.itemStatusReadyAt.sharesStorageWith(source.itemStatusReadyAt))
            assertEquals(2f, branch.enemies.single().status[StatusCell.STACKS + thermal])
            assertEquals(0.8f, source.relicCooldowns[furnaceIndex])
            assertEquals(3f, source.enemies.single().status[StatusCell.STACKS + thermal])
            assertEquals(0f, source.enemies.single().status[StatusCell.DECAY_AT + thermal])
        }
        assertEquivalentSimulation(siblings[0], siblings[1])
        assertEquivalentSimulation(siblings[1], siblings[2])

        // A later production reduction of one sibling must not mutate either retained sibling.
        val retainedSecond = siblings[1].exactSimulationFingerprint()
        val retainedThird = siblings[2].exactSimulationFingerprint()
        val advancedFirst = assertIs<GameReductionResult.Accepted>(
            reducer.reduce(
                EngineState(siblings[0]),
                GameplayInteractionPulse.FrameElapsed.fromValidated(MutableGameState.FIXED_STEP * 2f),
            ),
        ).state.model
        assertTrue(advancedFirst.elapsed > siblings[0].elapsed)
        assertEquals(retainedSecond, siblings[1].exactSimulationFingerprint())
        assertEquals(retainedThird, siblings[2].exactSimulationFingerprint())
    }

    @Test
    fun threeProductionCollisionSiblingsDetachEnemyRelicBuffersWithoutTouchingSource() {
        val thermal = StatusElement.THERMAL.ordinal
        val source = MutableGameState(canonicalGameplayContent, seed = 8_301).apply {
            startRun()
            enemies.clear()
            spawnClock = 1_000f
            // Admit exactly one fixed step independently of presentation-speed preferences.
            accumulator = MutableGameState.FIXED_STEP
            // Furnace Covenant makes every hit above 70 heat apply Thermal to its target.
            acquireRelicForTesting(RelicId.FURNACE_COVENANT)
            heat = 75f
            val enemy = addEnemyForTesting(x = 90f, y = 0f)
            trail += TrailPoint(enemy.x, enemy.y)
            takeSoundCues()
            takeVisualFxCues()
        }
        val reducer = GameReducer()
        val sourceEngine = EngineState(source)

        val siblings = List(3) {
            assertIs<GameReductionResult.Accepted>(
                reducer.reduce(
                    sourceEngine,
                    GameplayInteractionPulse.FrameElapsed.fromValidated(0f),
                ),
            ).state.model
        }

        siblings.forEach { branch ->
            val enemy = branch.enemies.single()
            // Flux Wake's own Scorch and Furnace Covenant's Thermal.
            assertEquals(2f, enemy.status[StatusCell.STACKS + thermal])
            assertEquals((HitSource.WEAPON.ordinal + 1).toFloat(), enemy.status[StatusCell.LAST_SOURCE])
            assertTrue(enemy.hp < enemy.maxHp)
            assertFalse(enemy.status.sharesStorageWith(source.enemies.single().status))
            assertFalse(branch.relicCooldowns.sharesStorageWith(source.relicCooldowns))
        }
        source.enemies.single().let { enemy ->
            assertEquals(0f, enemy.status[StatusCell.STACKS + thermal])
            assertEquals(0f, enemy.status[StatusCell.LAST_SOURCE])
            assertEquals(enemy.maxHp, enemy.hp)
        }
        assertEquals(0f, source.relicCooldowns[RelicId.FURNACE_COVENANT.ordinal])
        assertEquivalentSimulation(siblings[0], siblings[1])
        assertEquivalentSimulation(siblings[1], siblings[2])
    }

    @Test
    fun threeProductionItemChoiceSiblingsDetachProgressionStorageIndependently() {
        val source = MutableGameState(canonicalGameplayContent, seed = 9_401).apply {
            openItemChoice()
            takeSoundCues()
            takeVisualFxCues()
        }
        assertEquals(3, source.choices.size)
        val selectedItemIds = source.choices.map { choice -> requireNotNull(choice.itemId) }
        val sourceRandomCursor = source.gameplayRandom.snapshot()
        val sourceRewardCursor = source.rewardRandom.snapshot()
        val reducer = GameReducer()
        val sourceEngine = EngineState(source)

        val siblings = selectedItemIds.indices.map { choiceIndex ->
            assertIs<GameReductionResult.Accepted>(
                reducer.reduce(
                    sourceEngine,
                    GameplayInteractionPulse.ChoiceSelected.fromValidated(choiceIndex),
                ),
            ).state.model
        }

        siblings.forEachIndexed { index, branch ->
            val selectedItemId = selectedItemIds[index]
            assertEquals(1, branch.itemStacks[selectedItemId])
            assertEquals(0, source.itemStacks[selectedItemId])
            assertFalse(branch.itemStacks.sharesStorageWith(source.itemStacks))
            assertFalse(branch.discoveredItemIds.sharesStorageWith(source.discoveredItemIds))
            assertNotSame(source.build, branch.build)
            assertEquals(listOf(selectedItemId), branch.acquisitionOrder)
            assertTrue(source.acquisitionOrder.isEmpty())
            assertEquals(0, source.build.ownedItemCount)
            assertTrue(branch.relicRanks.sharesStorageWith(source.relicRanks))
            assertTrue(branch.clauseReadyAt.sharesStorageWith(source.clauseReadyAt))
        }
        assertEquals(sourceRandomCursor, source.gameplayRandom.snapshot())
        assertEquals(sourceRewardCursor, source.rewardRandom.snapshot())
        assertEquals(selectedItemIds.toSet().size, siblings.size)
        siblings.forEachIndexed { branchIndex, branch ->
            selectedItemIds.forEachIndexed { itemIndex, itemId ->
                assertEquals(if (branchIndex == itemIndex) 1 else 0, branch.itemStacks[itemId])
            }
        }
    }

    @Test
    fun copyOnWriteProductionTraceMatchesIndependentEagerMutableReference() {
        fun newTraceState(): MutableGameState = MutableGameState(
            content = canonicalGameplayContent,
            seed = 10_501,
            initialMatter = 0,
        ).apply {
            startRun()
            acquireRelicForTesting(RelicId.ECHO_CHAMBER)
            acquireRelicForTesting(RelicId.FURNACE_COVENANT)
            acquireRelicForTesting(RelicId.PANDEMIC_CODEX)
            acquireItemForTesting(0)
            acquireItemForTesting(content.items.single { it.name == "Igniter" }.id)
            acquireItemForTesting(content.items.single { it.name == "Rime Rounds" }.id)
            // Ensure the trace covers both entity and per-run relic/progression storage.
            addEnemyForTesting(x = 120f, y = 15f)
            trail += TrailPoint(120f, 15f)
            populateAdditionalRetainedState()
            drainReferenceOutputs()
        }

        var production = EngineState(newTraceState())
        val eagerReference = newTraceState()
        val retained = mutableListOf<Pair<MutableGameState, List<Pair<String, Any?>>>>()
        val reducer = GameReducer()
        repeat(240) { index ->
            val pulse = when {
                index % 61 == 0 -> GameplayInteractionPulse.DashRequested
                index % 53 == 0 -> GameplayInteractionPulse.ApexRequested
                index % 47 == 0 -> GameplayInteractionPulse.BrakeChanged(
                    source = BrakeSource.KEYBOARD,
                    active = (index / 47) % 2 == 0,
                )
                index % 31 == 0 -> GameplayInteractionPulse.PointerMoved.fromValidated(
                    x = 640f + (index % 9) * 11f,
                    y = 360f + (index % 7) * 7f,
                )
                else -> GameplayInteractionPulse.FrameElapsed.fromValidated(MutableGameState.FIXED_STEP)
            }
            val precedingState = production.model
            val precedingFingerprint = precedingState.exactSimulationFingerprint()
            if (index % 30 == 0) retained += precedingState to precedingFingerprint
            production = assertIs<GameReductionResult.Accepted>(
                reducer.reduce(production, pulse),
            ).state
            eagerReference.applyReferencePulse(pulse)
            eagerReference.drainReferenceOutputs()
            // Step scratch is idle between accepted reductions, so no candidate inherits it.
            listOf(production.model, eagerReference).forEach { state ->
                assertNull(state.activeLoadoutMismatch(), "transition $index active loadout")
                assertEquals(0, state.chainDepth, "transition $index chain scratch depth")
                assertEquals(-1, state.castSlot, "transition $index skill cast scratch")
                assertEquals(-1, state.shatteringEnemyId, "transition $index shatter target")
                assertEquals(0, state.clauseDepth, "transition $index clause depth")
            }
            assertEquals(
                precedingFingerprint,
                precedingState.exactSimulationFingerprint(),
                "transition $index mutated the preceding State",
            )
            assertEquivalentSimulation(production.model, eagerReference)
        }
        retained.forEachIndexed { index, (state, fingerprint) ->
            assertEquals(fingerprint, state.exactSimulationFingerprint(), "retained State $index")
        }
        assertEquals(2f, production.model.skillRuntime.apex(ApexCell.CAST_COUNT), "the trace casts the Apex")
    }

    // ---- 0.6.0 DIMENSIONS ----
    @Test
    fun aDimensionVisitIsIsolatedAcrossReductions() {
        val source = MutableGameState(canonicalGameplayContent, seed = 6_061, initialMatter = 0).apply {
            startRun()
            enemies.clear()
            elapsed = 200f
            enterDimensionForTesting(WorldLayer.ZENITH)
            completeObjectiveForTesting()
            // The visit's timer runs out inside the candidate's frame.
            dimensionRun = dimensionRun.copy(visit = checkNotNull(dimensionRun.visit).copy(endsAt = 200f + MutableGameState.FIXED_STEP))
            drainReferenceOutputs()
        }
        val committedFingerprint = source.exactSimulationFingerprint()
        val committedRun = source.dimensionRun
        val engine = EngineState(source)
        val reducer = GameReducer()

        val pointer = assertIs<GameReductionResult.Accepted>(
            reducer.reduce(engine, GameplayInteractionPulse.PointerMoved.fromValidated(700f, 380f)),
        ).state.model
        assertSame(committedRun, pointer.dimensionRun, "a scalar-input reduction shares the dimension value")

        val candidate = assertIs<GameReductionResult.Accepted>(
            reducer.reduce(engine, GameplayInteractionPulse.FrameElapsed.fromValidated(3f * MutableGameState.FIXED_STEP)),
        ).state.model
        assertEquals(WorldLayer.BASE, candidate.worldLayer)
        assertNull(candidate.dimensionRun.visit)
        // The queued Blessing reward opens on the same frame.
        assertTrue(candidate.dimensionRun.pendingRewards.isEmpty())
        assertEquals(kinetickk.ball.gameplay.nucleus.render.GamePhase.CHOICE, candidate.phase)
        assertEquals(WorldLayer.ZENITH, candidate.dimensionRewardLayer)
        assertEquals(committedFingerprint, source.exactSimulationFingerprint(), "the return left the committed visit alone")
        assertSame(committedRun, source.dimensionRun)
        assertEquals(WorldLayer.ZENITH, source.worldLayer)
        assertNull(source.dimensionRewardLayer)
    }

    @Test
    fun aHaloPassedInACandidateLeavesTheCommittedVisitAlone() {
        val source = MutableGameState(canonicalGameplayContent, seed = 6_061, initialMatter = 0).apply {
            startRun()
            enemies.clear()
            elapsed = 200f
            enterDimensionForTesting(WorldLayer.ZENITH, DimensionObjective.JUDGEMENT)
            hp = maxHp * 0.5f
            velocityX = dimensionPace() * 1.5f
            velocityY = 0f
            // The Core starts inside the ring and is fast: the candidate's first step passes it.
            val visit = checkNotNull(dimensionRun.visit)
            dimensionRun = dimensionRun.copy(visit = visit.copy(halos = immutableListOf(HaloRing(7, coreX + 10f, coreY, -1))))
            drainReferenceOutputs()
        }
        val committedFingerprint = source.exactSimulationFingerprint()
        val committedRun = source.dimensionRun
        val committedHp = source.hp

        val candidate = assertIs<GameReductionResult.Accepted>(
            GameReducer().reduce(EngineState(source), GameplayInteractionPulse.FrameElapsed.fromValidated(MutableGameState.FIXED_STEP)),
        ).state.model
        assertTrue(checkNotNull(candidate.dimensionRun.visit).halos.none { it.id == 7 }, "the candidate passed the ring")
        assertTrue(candidate.hp > committedHp, "the ring mended the candidate")

        assertEquals(committedFingerprint, source.exactSimulationFingerprint(), "the pass left the committed visit alone")
        assertSame(committedRun, source.dimensionRun)
        assertEquals(listOf(7), checkNotNull(source.dimensionRun.visit).halos.map { it.id })
        assertEquals(committedHp, source.hp)
    }

    @Test
    fun boonCardsTakenAndPreviewedInAForkNeverChangeTheCommittedOffer() {
        val source = MutableGameState(canonicalGameplayContent, seed = 6_061, initialMatter = 0).apply {
            startRun()
            enemies.clear()
            elapsed = 200f
            dimensionRun = dimensionRun.copy(pendingRewards = immutableListOf(DimensionRewardGrant(WorldLayer.NADIR, completed = true)))
            openNextPendingChoice()
            drainReferenceOutputs()
        }
        assertEquals(kinetickk.ball.gameplay.nucleus.render.ChoiceType.BOON, source.choiceType)
        val retained = source.exactSimulationFingerprint()
        val committedProfile = source.activeRebirthProfile
        val reducer = GameReducer()
        source.choices.indices.forEach { index ->
            val reduced = assertIs<GameReductionResult.Accepted>(
                reducer.reduce(EngineState(source), GameplayInteractionPulse.ChoiceSelected.fromValidated(index)),
            ).state.model
            assertEquals(retained, source.exactSimulationFingerprint(), "card $index changed the committed state")
            assertEquals(listOfNotNull(source.choices[index].boonId), reduced.runBoons.toList())
            assertNull(reduced.dimensionRewardLayer)
            assertEquals(kinetickk.ball.gameplay.nucleus.render.GamePhase.RUNNING, reduced.phase)
        }
        source.rewardPreviews()
        assertEquals(retained, source.exactSimulationFingerprint(), "the previews changed the committed state")
        assertTrue(source.runBoons.isEmpty())
        assertEquals(committedProfile, source.activeRebirthProfile)
        assertEquals(WorldLayer.NADIR, source.dimensionRewardLayer)
    }

    // ---- 0.6.0 VARIETY ----
    @Test
    fun omenAcceptanceAndPreviewsInAForkNeverChangeTheCommittedState() {
        // An offer dealing Fault Line, the omen that rebuilds the obstacle window.
        val source = (1..60).asSequence().map { seed ->
            MutableGameState(canonicalGameplayContent, seed = seed, offerRunOmen = true).apply {
                startRun()
                simulateStep(MutableGameState.FIXED_STEP)
                drainReferenceOutputs()
            }
        }.first { state -> state.choices.any { it.omenId == OmenId.FAULT_LINE } }
        assertEquals(kinetickk.ball.gameplay.nucleus.render.ChoiceType.OMEN, source.choiceType)
        val retained = source.exactSimulationFingerprint()
        val reducer = GameReducer()
        source.choices.indices.forEach { index ->
            val reduced = assertIs<GameReductionResult.Accepted>(
                reducer.reduce(EngineState(source), GameplayInteractionPulse.ChoiceSelected.fromValidated(index)),
            ).state.model
            assertEquals(retained, source.exactSimulationFingerprint(), "card $index changed the committed state")
            assertEquals(source.choices[index].omenId, reduced.runOmen)
            assertEquals(kinetickk.ball.gameplay.nucleus.render.GamePhase.RUNNING, reduced.phase)
        }
        source.rewardPreviews()
        assertEquals(retained, source.exactSimulationFingerprint(), "the previews changed the committed state")
    }

    private fun MutableGameState.populateAdditionalRetainedState() {
        characterRuntime = CharacterRuntime().apply {
            charge = 0.3f
            previousSpeed = 123f
            previousHeading = 0.4f
            turnArc = 0.7f
            turnDistance = 50f
            wasBraking = true
            parryWindow = 0.2f
            parryCooldown = 0.6f
            haloRadius = 58f
            dashSequence = 2
            hasDashOrigin = true
            dashOriginX = 4f
            dashOriginY = -0f
            dashRecorded = true
            punctureReadyAt = 0.9f
            facets = 3
            facetTimer = 1.5f
            edge = 2
            edgeUntil = 4f
            latticeCount = 2
            latticeNodes[0] = -1f
            latticeNodes[1] = 2f
            latticeNodes[2] = 3f
            latticeNodes[3] = -4f
            latticeTime = 0.5f
            stellarMass = 7
            ghostHead = 3
            ghostSamples = 4
            ghostPath[6] = 11f
            polarity = Polarity.REPEL
            beatStreak = 2
            blinkCharges = 1
            blinkRecharge = 0.7f
            scarCount = 1
            scars[0] = 1f
            scars[4] = 2f
            droneCount = 1
            drones[0] = 5f
            drones[4] = 12f
            abilityDamage = 33.0
            damagePrevented = 4.0
        }
        pendingEliteKills = 2
        pendingDashHits = 3
        pendingCompletedOrbits = 4
        pendingArchitectDefeatedWith = CoreShape.PRISM
        pendingTotalKills = 5
        pendingRicochets = 6
        pendingDataPickups = 7
        pendingAcquiredItemIds = listOf(3, 5)
        itemDeck = kinetickk.ball.gameplay.nucleus.model.ItemDeck.dealt(
            content.items.size, IntArray(content.items.size) { it }, intArrayOf(content.items.size, 0, 0, 0, 0), listOf(0, 3, 5),
        )
        achievementFlushDue = true
        sites = listOf(
            SiteState(
                id = 3L,
                kind = SiteKind.SEALED_ANOMALY,
                x = 400f,
                y = 300f,
                expiresAt = 100f,
                remaining = 20f,
                clock = 0.15f,
                step = 1,
                count = 1,
                meter = 1f,
                volleyClock = 1.8f,
                warningRemaining = 0.3f,
                volleyAngle = 0.4f,
                aimX = 12f,
                aimY = -14f,
                foeIds = immutableListOf(11, 13),
                course = immutableListOf(SitePoint(20f, 30f), SitePoint(-5f, 6f)),
                sides = 0b10,
                par = 7.5f,
                axis = 0.6f,
                limit = 9.5f,
                hurtBaseline = 12.5,
                settled = false,
            ),
        )
        nextSiteWindowIndex = 2
        siteRandom.nextInt()
        ambientSites = AmbientSiteWindow(3, -2, immutableListOf(
            SiteState(id = 0x0100_0000_0000_0005L, kind = SiteKind.DRIFT_CACHE, x = 900f, y = -700f,
                expiresAt = Float.POSITIVE_INFINITY, settled = false),
        ))
        secretSigil = SiteState(id = 0x10L + SiteKind.FAR_SIGIL.ordinal, kind = SiteKind.FAR_SIGIL, x = -800f, y = 600f,
            expiresAt = Float.POSITIVE_INFINITY, foeIds = immutableListOf(17))
        openedSiteIds = listOf(5L, 9L)
        runSecretFlags = 0b101
        worldDiscoveryMask = 0b11
        pendingWorldDiscoveryMask = 0b10
        goldenDrifterId = 99
        goldenDrifterUntil = 33f
        spinBuckets = listOf(1.5f, -0.5f)
        spinCurrent = 0.75f
        spinClock = 0.4f
        spinLastAngle = 1.2f
        spinPrimed = true
        runOmen = OmenId.GLASS_SUN
        directorPersonality = DirectorPersonalityId.SIEGE
        featuredAspect = StatusElement.CRYO.ordinal
        surgeIndex = 2
        surgeThemeType = 4
        varietyAnnouncement = 3
        keynoteTag = BuildTag.RAM
        keynoteElement = StatusElement.ION
        worldEventPlans = immutableListOf(WorldEventPlan(WorldEventKind.ION_STORM, 70f, 85f, 115f))
        nextWorldEvent = 1
        worldEvent = WorldEventProjection(
            WorldEventKind.METEOR_SHOWER, 60f, 75f, 105f, windAngle = 0.3f, windStrength = 0.4f,
            sweepStartedAt = 76f, sweepAngle = 0.9f,
            strikes = immutableListOf(EventStrikeProjection(150f, -60f, 90f, 77f, 78f)),
        )
        worldEventPulse = 2
        worldEventNextPulseAt = 79f
        eventSignals = immutableListOf(EventSignalProjection(4, SignalKind.SHRINE, 500f, 450f, 130f))
        nextSignalSlot = 2
        signalsSpawned = 3
        signalsEngaged = 1
        pendingSignalPrompts = immutableListOf(SignalPrompt(SignalKind.GAMBLE, 1))
        secretBossMask = 1L shl 33
        worldKeepOut = WorldKeepOut(WorldLayer.ZENITH, 1_234.5, -987.25, 900f)
        runBoons = immutableListOf(32, 48)
        dimensionRun = DimensionRunState(
            portalOffers = 2, visitedMask = WorldLayer.ZENITH.bit, completedMask = WorldLayer.ZENITH.bit,
            glitchPortal = DimensionPortal(4, 5_000f, -4_000f, WorldLayer.ARCHIVE, 900f), nextPortalId = 5,
        )
        dimensionSlowSeconds = 0.4f
        dimensionScorchClock = 0.2f
        dimensionLocalClock = 3.5f
        obstacleHitSurfaceId = 7L
        coreObstacleGap = 120f
        grazeReadyAt = 0.4f
        slingWellId = 9L
        slingEntryHeading = 0.3f
        structureField = StructureField.of(
            4, -3, WorldLayer.BASE.ordinal, false,
            listOf(PlacedStructure((1L shl 63) or (5L shl 34) or (6L shl 8), StructureTemplateId.VAULT, 1,
                2_000f, 1_500f, 600f, 400f, 0, 1, false)),
            listOf(Wall((1L shl 63) or (5L shl 34) or (6L shl 8), WallKind.DOOR_SEALED, 1_800f, 1_400f, 2_200f, 1_400f, 24f, door = 0)),
            listOf(StructureSignal(77L, StructureTemplateId.SPIRE, 5_000f, 4_000f, 5_100f, 4_200f)),
        )
        dungeon = DungeonState(
            structureId = (1L shl 63) or (5L shl 34) or (6L shl 8), template = StructureTemplateId.VAULT, rotation = 1,
            x = 2_000f, y = 1_500f, clearedRooms = 0b1, engagedRoom = 2, garrisonIds = immutableListOf(21, 22),
            garrisonTotal = 4, engaged = true, enteredAt = 0.5f,
        )
        spentStructureIds = listOf(11L, 12L)
        lastAnnouncedStructureId = 13L
        coreStraightness = 0.25f
        gripStrength = 1.5f
        slingStrength = 0.75f
        pendingDirectedRewards = listOf(DirectedReward.ITEM_AND_REPAIR, DirectedReward.WEAPON)
        directedReward = DirectedReward.RELIC
        selectedRewardFocus = RewardFocus.CONTROL
        buffs = listOf(ActiveBuff(sourceKey = 0, stat = BuffStat.DAMAGE, amountPerStack = 0.2f, stacks = 2, expiresAt = 9f))
        addField(
            CombatFieldKind.ROT_WELL, FieldSource.REACTION, ReactionId.ROT_WELL.ordinal, x = 200f, y = 300f, radius = 70f,
            seconds = 1.5f, damagePerSecond = 4f, element = StatusElement.ENTROPY, stacksPerTick = 1, tickClock = 0.1f,
        )
        addField(
            CombatFieldKind.ECHO_RING, FieldSource.SKILL, 1, x = 210f, y = 290f, radius = 90f, seconds = 4f,
            damagePerSecond = 2f, anchorId = 11, flags = FieldFlags.FOLLOWS_CORE, power = 3f,
        )
        coreFieldMask = 1 shl CombatFieldKind.ROT_WELL.ordinal
        fieldEnterReadyAt = 0.25f
        ablazeUntil = 1.1f
        frictionlessUntil = 1.2f
        blastJumpUntil = 1.3f
        weaveUntil = 1.4f
        weaveDamage = 6f
        weaveRange = 220f
        guardShards = 3
        guardUntil = 1.5f
        guardDamage = 7f
        voidAnchorId = 11
        voidAnchorUntil = 1.6f
        overcurrentReadyAt = 1.7f
        flameRefundReadyAt = 1.8f
        erasureLatchId = 13
        coreImpulseReadyAt = 1.9f
        primedDashUntil = 2.1f
        phaseUntil = 2.2f
        aftershockAt0 = 2.3f
        aftershockX0 = 120f
        aftershockY0 = -40f
        aftershockDamage0 = 9f
        aftershockAt1 = 2.4f
        aftershockX1 = -60f
        aftershockY1 = 80f
        aftershockDamage1 = 7f
        shotErasedDispatches = 2
        markDispatches = 1
        shotsErased = 5L
        dashHeatBank = 4.5f
        // An active item waits for a slot with its Reborn roll.
        pendingActiveItemId = checkNotNull(content.activeItem(SkillId.VOID_STEP)).id
        pendingActiveReborn = true
        skillBufferMask = 0b101
        skillBufferTime = 0.08f
        apexBufferTime = 0.05f
        // Slot Z casts inside the trace (its buffered request is ready); slot C cools and holds a pending landing.
        skillLoadout = SkillLoadout.EMPTY.with(0, SkillId.FROST_SNAP, 2).with(2, SkillId.METEOR, 1)
        // The loadout's ranks are its active items' stacks.
        itemStacks[checkNotNull(content.activeItem(SkillId.FROST_SNAP)).id] = 2
        itemStacks[checkNotNull(content.activeItem(SkillId.METEOR)).id] = 1
        skillRuntime = SkillRuntime().apply {
            this[2, SkillCell.READY_AT] = 3f
            this[2, SkillCell.CAST_AT] = 0.1f
            this[2, SkillCell.LAST_COOLDOWN] = 12f
            this[2, SkillCell.REFUNDED] = 0.25f
            this[2, SkillCell.ANCHOR_X] = 140f
            this[2, SkillCell.ANCHOR_Y] = -20f
            this[2, SkillCell.PENDING_AT] = 0.9f
            this[2, SkillCell.RANKED_AT_LEVEL] = 2f
            this[2, SkillCell.CAST_COUNT] = 1f
            // The Apex is unlocked and ready, so its buffered request casts inside the trace; a refund and held
            // shots are on record.
            setApex(ApexCell.UNLOCKED, 1f)
            setApex(ApexCell.READY_AT, 0f)
            setApex(ApexCell.LAST_CAST, -60f)
            setApex(ApexCell.REFUNDED, 1.5f)
            setApex(ApexCell.HELD, 2f)
            setApex(ApexCell.HELD_RELEASE_AT, 30f)
            setApex(ApexCell.CAST_COUNT, 1f)
            setApex(ApexCell.AUX, 0.5f)
            skillDamage = 12.5
            apexBossDamage = 3.0
        }
        severedRelics = listOf(SeveredRelic(RelicId.GLASS_COMET, until = 40f))
        echoHits += EchoHit(enemyId = 11, delay = 0.3f, damage = 5f, appliesDominant = true)
        clauseReadyAt[0] = 0.7f
        clauseCounters[0] = 2
        statusAccumulators[1] = 0.4f
        itemStatusReadyAt[1] = 0.6f
        reactionTokens = 5.5f
        breakTokens = 7.25f
        discoveredReactionMask = 1 shl ReactionId.FUSION.ordinal
        pendingDiscoveredReactionMask = 1 shl ReactionId.FUSION.ordinal
        discoveredPairingMask = 2
        pendingDiscoveredPairingMask = 2
        heirloomCandidates = listOf(RelicId.GYRE_ENGINE)
        heirloomPending = true
        pendingVictoryRelics = listOf(RelicId.GYRE_ENGINE)
        stormCharge = 0.4f
        stasisMeter = 2.5f
        stasisBank = 3
        overdriveBurnoutUntil = 12f
        glassCharge = 6f
        integrityLiabilityCut = 9f
        critMissStreak = 2
        offerPity = 4
        itemOffersOpened = 3
        secondWindSpent = true
        matrixFreeDashReadyAt = 0.65f
        // Every forked build scalar holds a distinct non-default value, so a fork that drops one copy
        // (resetting it to its initial value) diverges from its source. Several expire inside the trace.
        rebornItems[0] = 1
        heirloomOfferActive = true
        pendingHeirloomResolved = true
        ouroborosSpent = true
        ouroborosLockedUntil = 1.5f
        invulnerableUntil = 0.9f
        glassInvulnerabilityReadyAt = 1.1f
        bulwarkInvulnerabilityReadyAt = 1.3f
        stasisLockedUntil = 0.7f
        sinceTurn = 0.45f
        gyreReadyAt = 0.6f
        gyreEchoAt = 1f
        gyreEchoDamage = 7f
        overdriveExtension = 0.4f
        overdriveKills = 3
        aegisAbsorbed = 2.5f
        aegisNovaReadyAt = 1.2f
        shieldBreakArmed = true
        shieldBreakReadyAt = 0.8f
        overheatArmed = false
        wasBraking = true
        brakeHoldTime = 0.2f
        brakeShedSpeed = 210f
        freeDashPrimed = true
        freeDashReadyAt = 0.5f
        comboKillCounter = 2
        cinderRecoilReadyAt = 0.3f
        dashHitThisDash = true
        arsenalCounter = 1
        necroticClock = 0.25f
        statusCuesThisStep = 2
        smoothedVelocityX = 14f
        smoothedVelocityY = 15f
        turnHeadingEstablished = true
        turnHoldTime = 0.2f
        turnRecoveryCooldown = 0.3f
        turnDirection = -1
        elitesSpawned = 3
        heraldsSpawned = 1
        directorPhase = 2
        directorPhaseUntil = 9.5f
        spawnBudget = 2.25f
        pendingEventKind = 3
        pendingEventSize = 7
        pendingEventType = 9
        recentEventTypes = 0b101L or (1L shl 34)
        previousEventTypes = 0b110L or (1L shl 33)
        ambushReadyAt = 21f
        encircleReadyAt = 41f
        wallReadyAt = 31f
        capOverflowReadyAt = 22f
        ambushSpeedSeconds = 0.75f
        spawnEventCount = 4
        lastSpawnEventKind = 1
        lastSpawnEventPlanned = 5
        lastSpawnEventSpawned = 4
        spawnRandom.nextInt()
        coreRadius = 17.5f
        worldOriginX = 2_400.5
        worldOriginY = -1_200.25
        obstacleField = ObstacleField.of(1, -1, 1, listOf(listOf(
            Obstacle(id = 7L, kind = ObstacleKind.BUMPER, x = 30f, y = -40f, radius = 34f, shapeSeed = 9, ricochetReadyAt = 1.5f,
                fuseAt = 2.5f, repels = true),
        )))
        destroyedObstacleIds = listOf(3L, 4L)
        obstacleHitReadyAt = 0.6f
        nearMissReadyAt = 0.7f
        reviveInvulnerableUntil = 0.8f
        rebirthRank = 7
        volatileBursts = kinetickk.foundation.collections.immutableListOf(
            kinetickk.ball.gameplay.nucleus.render.VolatileBurstProjection(12f, -8f, 70f, 0.2f, 0.55f),
        )
        pickups.firstOrNull()?.attracted = true
        enemies.firstOrNull()?.apply {
            characterMarkTime = 0.75f
            lastCharacterDash = 2
            aspect = StatusElement.ION.ordinal
            aspectImmune = true
            phase = 1
            shiftUntil = 0.4f
            escortsPending = 2
            lastDamagedAt = 0.3f
            disabledUntil = 0.35f
            silencedUntil = 0.45f
            mode = 2
            modeUntil = 0.55f
            facing = 1.25f
            guarded = true
            linkedBy = 7
            linkedUntil = 0.65f
            shieldUntil = 0.7f
            abilityAt = 0.85f
            thrownUntil = 0.9f
            slamReadyAt = 0.95f
            armored = true
            armorCracked = true
            charges = 2
            phaseStartPending = true
            affix = EliteAffix.HASTED
            champion = true
            status[StatusCell.STACKS + StatusElement.THERMAL.ordinal] = 2f
            status[StatusCell.DECAY_AT + StatusElement.THERMAL.ordinal] = 0.5f
            status[StatusCell.FREEZE_IMMUNE_UNTIL] = 0.6f
            status[StatusCell.CRYO_IMMUNE_CARRY] = 0.5f
            status[StatusCell.REACTION_PAIR_READY_AT + ReactionId.FUSION.ordinal] = 0.4f
        }
    }

    /**
     * Retained facts that would steer a production trace (a queued offer opens a choice, a run mutator or another world
     * layer changes generation, a tagged enemy leaves the ordinary cap), so only fork tests populate them.
     */
    private fun MutableGameState.populateWorldControlState() {
        pendingFloorOffers = listOf(ItemRarity.RARE, ItemRarity.EPIC)
        offerRarityFloor = ItemRarity.RARE
        omenPending = true
        runMutatorMask = 1 shl RebirthMutator.PACK_HUNTERS.ordinal
        worldLayer = WorldLayer.NADIR
        dimensionRun = dimensionRun.copy(
            visit = DimensionVisit(
                WorldLayer.NADIR, DimensionObjective.HARVEST, serial = 1, enteredAt = 0.1f, endsAt = 75.1f, progress = 3,
                goal = 12, bossSpawnAt = Float.POSITIVE_INFINITY,
                exitPortal = DimensionPortal(6, 300f, 200f, WorldLayer.BASE, 75.1f),
                halos = immutableListOf(HaloRing(100, 10f, 20f, -1)), haloSerial = 1, courseX = 5f, courseY = 6f,
                courseHeading = 0.7f, embers = immutableListOf(Ember(30f, 40f, 7f)),
            ),
            pendingRewards = immutableListOf(DimensionRewardGrant(WorldLayer.ZENITH, completed = true)),
        )
        dimensionRewardLayer = WorldLayer.ZENITH
        encounters = immutableListOf(EncounterState(
            encounterTag(EncounterOwner.ROOM, 6L), 300f, -200f, 600f, 2_400f, spawned = 3, killed = 1, bossId = 41,
            startedAt = 0.2f,
        ))
        enemies.firstOrNull()?.ownerTag = encounterTag(EncounterOwner.ROOM, 6L)
    }

    private fun MutableGameState.applyReferencePulse(pulse: GameplayInteractionPulse) {
        when (pulse) {
            is GameplayInteractionPulse.FrameElapsed -> update(pulse.realDeltaSeconds)
            is GameplayInteractionPulse.ViewportChanged -> resize(pulse.width, pulse.height, pulse.density)
            is GameplayInteractionPulse.PointerMoved -> updatePointer(pulse.x, pulse.y, pulse.active)
            is GameplayInteractionPulse.BrakeChanged -> when (pulse.source) {
                BrakeSource.KEYBOARD -> setBrake(pulse.active)
                BrakeSource.SECONDARY_POINTER -> setSecondaryBrake(pulse.active)
                BrakeSource.TOUCH_CONTROL -> setTouchBrake(pulse.active)
            }
            GameplayInteractionPulse.DashRequested -> requestDash()
            GameplayInteractionPulse.PauseToggled -> togglePause()
            is GameplayInteractionPulse.ChoiceSelected -> choose(pulse.index)
            GameplayInteractionPulse.ChoicesRerolled -> rerollChoices()
            GameplayInteractionPulse.UserGestureObserved -> Unit
            is GameplayInteractionPulse.SkillRequested -> requestSkill(pulse.slot)
            GameplayInteractionPulse.ApexRequested -> requestApex()
        }
    }

    private fun MutableGameState.drainReferenceOutputs() {
        takeVisualFxCues()
        takeProgressUpdate()
        takeSoundCues()
    }

    private fun MutableGameState.hasPendingReductionOutputs(): Boolean =
        soundCues.isNotEmpty() ||
            !visualFxCues.isEmpty() ||
            pendingBankedMatter != 0L ||
            pendingDiscoveredItemIds.isNotEmpty() ||
            pendingClearedRebirthLevel != null ||
            pendingEliteKills != 0 ||
            pendingDashHits != 0 ||
            pendingCompletedOrbits != 0 ||
            pendingArchitectDefeatedWith != null ||
            achievementFlushDue && (pendingTotalKills != 0 || pendingRicochets != 0 || pendingDataPickups != 0 ||
                pendingAcquiredItemIds.isNotEmpty()) ||
            pendingDiscoveredRelicMask != 0L ||
            pendingDiscoveredReactionMask != 0 ||
            pendingDiscoveredPairingMask != 0 ||
            pendingVictoryRelics != null ||
            pendingHeirloomResolved

    private fun assertEquivalentSimulation(
        actual: MutableGameState,
        expected: MutableGameState,
    ) {
        val expectedFingerprint = expected.exactSimulationFingerprint()
        val actualFingerprint = actual.exactSimulationFingerprint()
        assertEquals(
            expectedFingerprint.map { it.first },
            actualFingerprint.map { it.first },
            "fingerprint field inventory",
        )
        expectedFingerprint.indices.forEach { index ->
            val expectedField = expectedFingerprint[index]
            val actualField = actualFingerprint[index]
            assertEquals(expectedField.second, actualField.second, expectedField.first)
        }
    }

    /**
     * Snapshot of every directly readable simulation fact, independent of production copy helpers.
     * Every Float is reduced to raw IEEE-754 bits so NaN payloads and signed zero stay distinct.
     * The private visual accumulator is checked through explicit ordered drain expectations above;
     * copying it here would make a broken accumulator copy part of the isolation oracle itself.
     */
    private fun MutableGameState.exactSimulationFingerprint(): List<Pair<String, Any?>> =
        ExactFingerprint().apply {
            value("content", content)
            value("random", gameplayRandom.snapshot())
            value("rewardRandom", rewardRandom.snapshot())
            value("spawnRandom", spawnRandom.snapshot())
            value("activeRebirthProfile", activeRebirthProfile)
            value("unlockedWeaponSet", unlockedWeaponSet.toList())
            value("unlockedWeaponView", unlockedWeaponView.toList())
            ints("matrixRanks", matrixRanks)
            value("matrix", matrix)
            ints("characterUpgradeLevels", characterUpgradeLevels)
            ints("weaponUpgradeLevels", weaponUpgradeLevels)
            value("characterUpgradeLevel", characterUpgradeLevel)
            float("characterUpgradePower", characterUpgradePower)
            value("discoveredItemIds", discoveredItemIds.toList())
            value("pendingDiscoveredItemIds", pendingDiscoveredItemIds.toList())
            ints("itemStacks", itemStacks)
            ints("rebornItems", rebornItems)
            value("acquisitionOrder", acquisitionOrder.toList())
            value("pendingAcquiredItemIds", pendingAcquiredItemIds.toList())
            value("deckSeed", deckSeed)
            value("easyMode", easyMode)
            value("offersRunOmen", offersRunOmen)
            value("itemDeck", itemDeck.dealtIds() to itemDeck.reserve())
            value("itemDeck.dealsEverything", itemDeck.dealsEverything)
            value("itemHistory", content.items.indices.map(itemHistory::deckWeight))
            buildFingerprint(this@exactSimulationFingerprint)
            floats("clauseReadyAt", clauseReadyAt)
            ints("clauseCounters", clauseCounters)
            floats("statusAccumulators", statusAccumulators)
            floats("itemStatusReadyAt", itemStatusReadyAt)
            value("buffs.size", buffs.size)
            buffs.forEachIndexed { index, buff ->
                val prefix = "buffs[$index]"
                value("$prefix.sourceKey", buff.sourceKey)
                value("$prefix.stat", buff.stat)
                float("$prefix.amountPerStack", buff.amountPerStack)
                value("$prefix.stacks", buff.stacks)
                float("$prefix.expiresAt", buff.expiresAt)
            }
            value("combatFieldTable.count", combatFieldTable.count)
            value("combatFieldTable.revision", combatFieldTable.revision)
            value("combatFieldTable.nextSequence", combatFieldTable.nextSequence)
            combatFieldTable.records().forEachIndexed { index, field ->
                val prefix = "combatFields[$index]"
                value("$prefix.kind", field.kind)
                value("$prefix.source", field.source)
                value("$prefix.owner", field.owner)
                float("$prefix.x", field.x)
                float("$prefix.y", field.y)
                float("$prefix.x2", field.x2)
                float("$prefix.y2", field.y2)
                float("$prefix.radius", field.radius)
                float("$prefix.damagePerSecond", field.damagePerSecond)
                value("$prefix.element", field.element)
                value("$prefix.stacksPerTick", field.stacksPerTick)
                value("$prefix.generation", field.generation)
                float("$prefix.pull", field.pull)
                float("$prefix.createdAt", field.createdAt)
                float("$prefix.expiresAt", field.expiresAt)
                float("$prefix.remaining", field.remaining)
                float("$prefix.tickClock", field.tickClock)
                float("$prefix.charge", field.charge)
                value("$prefix.anchorId", field.anchorId)
                value("$prefix.flags", field.flags)
                float("$prefix.power", field.power)
                value("$prefix.sequence", field.sequence)
            }
            value("coreFieldMask", coreFieldMask)
            float("fieldEnterReadyAt", fieldEnterReadyAt)
            float("ablazeUntil", ablazeUntil)
            float("frictionlessUntil", frictionlessUntil)
            float("blastJumpUntil", blastJumpUntil)
            float("weaveUntil", weaveUntil)
            float("weaveDamage", weaveDamage)
            float("weaveRange", weaveRange)
            value("guardShards", guardShards)
            float("guardUntil", guardUntil)
            float("guardDamage", guardDamage)
            value("voidAnchorId", voidAnchorId)
            float("voidAnchorUntil", voidAnchorUntil)
            float("overcurrentReadyAt", overcurrentReadyAt)
            float("flameRefundReadyAt", flameRefundReadyAt)
            value("erasureLatchId", erasureLatchId)
            float("coreImpulseReadyAt", coreImpulseReadyAt)
            float("primedDashUntil", primedDashUntil)
            float("phaseUntil", phaseUntil)
            float("aftershockAt0", aftershockAt0)
            float("aftershockX0", aftershockX0)
            float("aftershockY0", aftershockY0)
            float("aftershockDamage0", aftershockDamage0)
            float("aftershockAt1", aftershockAt1)
            float("aftershockX1", aftershockX1)
            float("aftershockY1", aftershockY1)
            float("aftershockDamage1", aftershockDamage1)
            value("shotErasedDispatches", shotErasedDispatches)
            value("markDispatches", markDispatches)
            value("shotsErased", shotsErased)
            float("dashHeatBank", dashHeatBank)
            value("pendingActiveItemId", pendingActiveItemId)
            value("pendingActiveReborn", pendingActiveReborn)
            value("skillLoadout", skillLoadout.toString())
            value("skillRuntime", skillRuntime.toString())
            value("skillBufferMask", skillBufferMask)
            float("skillBufferTime", skillBufferTime)
            float("apexBufferTime", apexBufferTime)
            float("reactionTokens", reactionTokens)
            float("breakTokens", breakTokens)
            value("discoveredRelicMask", discoveredRelicMask)
            value("pendingDiscoveredRelicMask", pendingDiscoveredRelicMask)
            value("discoveredReactionMask", discoveredReactionMask)
            value("pendingDiscoveredReactionMask", pendingDiscoveredReactionMask)
            value("discoveredPairingMask", discoveredPairingMask)
            value("pendingDiscoveredPairingMask", pendingDiscoveredPairingMask)
            value("heirloomCandidates", heirloomCandidates.toList())
            value("heirloomPending", heirloomPending)
            value("heirloomOfferActive", heirloomOfferActive)
            value("pendingHeirloomResolved", pendingHeirloomResolved)
            value("pendingVictoryRelics", pendingVictoryRelics?.toList())
            value("severedRelics.size", severedRelics.size)
            severedRelics.forEachIndexed { index, severed ->
                value("severedRelics[$index].id", severed.id)
                float("severedRelics[$index].until", severed.until)
            }
            value("ouroborosSpent", ouroborosSpent)
            float("ouroborosLockedUntil", ouroborosLockedUntil)
            float("invulnerableUntil", invulnerableUntil)
            float("glassInvulnerabilityReadyAt", glassInvulnerabilityReadyAt)
            float("bulwarkInvulnerabilityReadyAt", bulwarkInvulnerabilityReadyAt)
            float("stormCharge", stormCharge)
            float("stasisMeter", stasisMeter)
            value("stasisBank", stasisBank)
            float("stasisLockedUntil", stasisLockedUntil)
            float("sinceTurn", sinceTurn)
            float("gyreReadyAt", gyreReadyAt)
            float("gyreEchoAt", gyreEchoAt)
            float("gyreEchoDamage", gyreEchoDamage)
            float("overdriveBurnoutUntil", overdriveBurnoutUntil)
            float("overdriveExtension", overdriveExtension)
            value("overdriveKills", overdriveKills)
            float("aegisAbsorbed", aegisAbsorbed)
            float("aegisNovaReadyAt", aegisNovaReadyAt)
            value("shieldBreakArmed", shieldBreakArmed)
            float("shieldBreakReadyAt", shieldBreakReadyAt)
            value("overheatArmed", overheatArmed)
            value("wasBraking", wasBraking)
            float("brakeHoldTime", brakeHoldTime)
            float("brakeShedSpeed", brakeShedSpeed)
            value("critMissStreak", critMissStreak)
            value("freeDashPrimed", freeDashPrimed)
            float("freeDashReadyAt", freeDashReadyAt)
            value("comboKillCounter", comboKillCounter)
            float("glassCharge", glassCharge)
            float("integrityLiabilityCut", integrityLiabilityCut)
            float("cinderRecoilReadyAt", cinderRecoilReadyAt)
            value("offerPity", offerPity)
            value("itemOffersOpened", itemOffersOpened)
            value("secondWindSpent", secondWindSpent)
            float("matrixFreeDashReadyAt", matrixFreeDashReadyAt)
            value("dashHitThisDash", dashHitThisDash)
            value("clauseDepth", clauseDepth)
            value("arsenalCounter", arsenalCounter)
            float("necroticClock", necroticClock)
            value("statusCuesThisStep", statusCuesThisStep)
            value("soundCues", soundCues.toList())
            value("pendingDiscoveredItemIdStorage.present", pendingDiscoveredItemIdStorage != null)
            value("soundCueStorage.present", soundCueStorage != null)
            value("visualFxCueStorage.present", visualFxCueStorage != null)
            value("visualFxCueStorage.empty", visualFxCueStorage?.isEmpty())
            value("hasPendingReductionOutputs", hasPendingReductionOutputs())
            value("pendingBankedMatter", pendingBankedMatter)
            value("pendingClearedRebirthLevel", pendingClearedRebirthLevel)
            value("pendingEliteKills", pendingEliteKills)
            value("pendingDashHits", pendingDashHits)
            value("pendingCompletedOrbits", pendingCompletedOrbits)
            value("pendingArchitectDefeatedWith", pendingArchitectDefeatedWith)
            value("pendingTotalKills", pendingTotalKills)
            value("pendingRicochets", pendingRicochets)
            value("pendingDataPickups", pendingDataPickups)
            value("achievementFlushDue", achievementFlushDue)
            value("characterRuntime", characterRuntime.toString())
            value("weaponRuntime", weaponRuntime.toString())
            site("sites", sites)
            value("nextSiteWindowIndex", nextSiteWindowIndex)
            value("siteRandom", siteRandom.snapshot())
            value("ambientSites.cell", ambientSites.chunkX to ambientSites.chunkY)
            site("ambientSites", ambientSites.sites)
            site("secretSigil", listOfNotNull(secretSigil))
            value("openedSiteIds", openedSiteIds.toList())
            value("runSecretFlags", runSecretFlags)
            value("worldDiscoveryMask", worldDiscoveryMask)
            value("pendingWorldDiscoveryMask", pendingWorldDiscoveryMask)
            value("pendingFloorOffers", pendingFloorOffers.toList())
            value("goldenDrifterId", goldenDrifterId)
            float("goldenDrifterUntil", goldenDrifterUntil)
            floats("spinBuckets", spinBuckets)
            float("spinCurrent", spinCurrent)
            float("spinClock", spinClock)
            float("spinLastAngle", spinLastAngle)
            value("spinPrimed", spinPrimed)
            value("omenPending", omenPending)
            value("runOmen", runOmen)
            value("runMutatorMask", runMutatorMask)
            value("directorPersonality", directorPersonality)
            value("featuredAspect", featuredAspect)
            value("surgeIndex", surgeIndex)
            value("surgeThemeType", surgeThemeType)
            value("varietyAnnouncement", varietyAnnouncement)
            value("keynoteTag", keynoteTag)
            value("keynoteElement", keynoteElement)
            value("worldEventPlans", worldEventPlans.toList())
            value("nextWorldEvent", nextWorldEvent)
            value("worldEvent", worldEvent)
            value("worldEventPulse", worldEventPulse)
            float("worldEventNextPulseAt", worldEventNextPulseAt)
            value("eventSignals", eventSignals.toList())
            value("nextSignalSlot", nextSignalSlot)
            value("signalsSpawned", signalsSpawned)
            value("signalsEngaged", signalsEngaged)
            value("pendingSignalPrompts", pendingSignalPrompts.toList())
            value("offerRarityFloor", offerRarityFloor)
            value("pendingDirectedRewards", pendingDirectedRewards.toList())
            value("directedReward", directedReward)
            value("selectedRewardFocus", selectedRewardFocus)

            value("nextEntityId", nextEntityId)
            float("spawnClock", spawnClock)
            float("nextEliteAt", nextEliteAt)
            value("elitesSpawned", elitesSpawned)
            value("heraldsSpawned", heraldsSpawned)
            value("directorPhase", directorPhase)
            float("directorPhaseUntil", directorPhaseUntil)
            float("spawnBudget", spawnBudget)
            value("pendingEventKind", pendingEventKind)
            value("pendingEventSize", pendingEventSize)
            value("pendingEventType", pendingEventType)
            value("recentEventTypes", recentEventTypes)
            value("previousEventTypes", previousEventTypes)
            value("secretBossMask", secretBossMask)
            value("encounters", encounters.toList())
            float("ambushReadyAt", ambushReadyAt)
            float("encircleReadyAt", encircleReadyAt)
            float("wallReadyAt", wallReadyAt)
            float("capOverflowReadyAt", capOverflowReadyAt)
            float("ambushSpeedSeconds", ambushSpeedSeconds)
            value("spawnEventCount", spawnEventCount)
            value("lastSpawnEventKind", lastSpawnEventKind)
            value("lastSpawnEventPlanned", lastSpawnEventPlanned)
            value("lastSpawnEventSpawned", lastSpawnEventSpawned)
            value("runSeed", runSeed)
            value("worldOriginX", worldOriginX.toRawBits())
            value("worldOriginY", worldOriginY.toRawBits())
            value("obstacleField.center", obstacleField.centerChunkX to obstacleField.centerChunkY)
            value("obstacleField.span", obstacleField.span)
            value("obstacleField.size", obstacleField.size)
            obstacleField.obstacles.forEachIndexed { index, obstacle ->
                val prefix = "obstacles[$index]"
                value("$prefix.id", obstacle.id)
                value("$prefix.kind", obstacle.kind)
                float("$prefix.x", obstacle.x)
                float("$prefix.y", obstacle.y)
                float("$prefix.radius", obstacle.radius)
                value("$prefix.shapeSeed", obstacle.shapeSeed)
                float("$prefix.ricochetReadyAt", obstacle.ricochetReadyAt)
                float("$prefix.fuseAt", obstacle.fuseAt)
                value("$prefix.repels", obstacle.repels)
            }
            for (cell in 0 until obstacleField.span * obstacleField.span) {
                value("obstacleField.cell[$cell]", obstacleField.cellBegin(cell) to obstacleField.cellEnd(cell))
            }
            value("destroyedObstacleIds", destroyedObstacleIds.toList())
            value("worldLayer", worldLayer)
            value("worldKeepOut", worldKeepOut)
            value("runBoons", runBoons.toList())
            value("dimensionRun", dimensionRun)
            float("dimensionSlowSeconds", dimensionSlowSeconds)
            float("dimensionScorchClock", dimensionScorchClock)
            float("dimensionLocalClock", dimensionLocalClock)
            value("dimensionRewardLayer", dimensionRewardLayer)
            float("obstacleHitReadyAt", obstacleHitReadyAt)
            value("obstacleHitSurfaceId", obstacleHitSurfaceId)
            float("nearMissReadyAt", nearMissReadyAt)
            float("coreObstacleGap", coreObstacleGap)
            float("grazeReadyAt", grazeReadyAt)
            value("slingWellId", slingWellId)
            float("slingEntryHeading", slingEntryHeading)
            float("reviveInvulnerableUntil", reviveInvulnerableUntil)
            value("structureField.key", listOf(structureField.centerChunkX, structureField.centerChunkY, structureField.layer))
            value("structureField.bossCollapsed", structureField.bossCollapsed)
            value("structureField.structures", structureField.structures.toList())
            value("structureField.walls", structureField.walls.toList())
            value("structureField.signals", structureField.signals.toList())
            value("dungeon", dungeon)
            value("spentStructureIds", spentStructureIds.toList())
            value("lastAnnouncedStructureId", lastAnnouncedStructureId)
            float("dashBufferTime", dashBufferTime)
            value("bossSpawned", bossSpawned)
            value("keyboardBrakeActive", keyboardBrakeActive)
            value("secondaryBrakeActive", secondaryBrakeActive)
            value("touchBrakeActive", touchBrakeActive)
            float("uiScale", uiScale)
            float("viewportDensity", viewportDensity)
            float("accumulator", accumulator)
            value("lastTransitionSteps", lastTransitionSteps)
            float("previousCoreX", previousCoreX)
            float("previousCoreY", previousCoreY)
            float("previousSingularityX", previousSingularityX)
            float("previousSingularityY", previousSingularityY)
            float("previousCameraX", previousCameraX)
            float("previousCameraY", previousCameraY)
            float("trailLastX", trailLastX)
            float("trailLastY", trailLastY)
            float("trailDistanceCarry", trailDistanceCarry)
            float("weaponClock", weaponClock)
            float("weaponSecondaryClock", weaponSecondaryClock)
            value("pendingLevelChoices", pendingLevelChoices)
            value("pendingRelicChoices", pendingRelicChoices)
            value("pendingBindingRelic", pendingBindingRelic)
            value("pendingRelicBindAction", pendingRelicBindAction)
            ints("relicRanks", relicRanks)
            floats("relicCooldowns", relicCooldowns)
            ints("relicCounters", relicCounters)
            ints("agonyMutationCounts", agonyMutationCounts)
            float("dataFraction", dataFraction)
            float("matterFraction", matterFraction)
            float("shieldRechargeDelay", shieldRechargeDelay)
            float("overheatHoldTime", overheatHoldTime)
            float("saturationHeadingX", saturationHeadingX)
            float("saturationHeadingY", saturationHeadingY)
            float("smoothedVelocityX", smoothedVelocityX)
            float("smoothedVelocityY", smoothedVelocityY)
            value("turnHeadingEstablished", turnHeadingEstablished)
            float("turnHoldTime", turnHoldTime)
            float("turnRecoveryCooldown", turnRecoveryCooldown)
            value("turnDirection", turnDirection)
            float("coreStraightness", coreStraightness)
            float("timeSinceDamage", timeSinceDamage)
            float("hurtCooldown", hurtCooldown)
            float("lastAimDirectionX", lastAimDirectionX)
            float("lastAimDirectionY", lastAimDirectionY)
            value("bankedRunMatter", bankedRunMatter)
            value("activeChoiceType", activeChoiceType)

            value("phase", phase)
            value("settings.soundEnabled", settings.soundEnabled)
            value("settings.musicEnabled", settings.musicEnabled)
            float("settings.masterVolume", settings.masterVolume)
            float("settings.simulationSpeed", settings.simulationSpeed)
            float("settings.textScale", settings.textScale)
            value("settings.screenShake", settings.screenShake)
            value("settings.particleDensity", settings.particleDensity)
            value("settings.damageNumbers", settings.damageNumbers)
            value("settings.damageNumberSize", settings.damageNumberSize)
            value("settings.damageNumberFormat", settings.damageNumberFormat)
            value("settings.damageNumberTierThreshold", settings.damageNumberTierThreshold)
            value("settings.language", settings.language)
            value("settings.runStatisticsOnLeft", settings.runStatisticsOnLeft)
            value("settings.minimapEnabled", settings.minimapEnabled)
            value("rebirthLevel", rebirthLevel)
            value("rebirthRank", rebirthRank)
            value("volatileBursts", volatileBursts.toList())
            float("screenWidth", screenWidth)
            float("screenHeight", screenHeight)
            float("coreX", coreX)
            float("coreY", coreY)
            float("coreRadius", coreRadius)
            float("velocityX", velocityX)
            float("velocityY", velocityY)
            float("cameraX", cameraX)
            float("cameraY", cameraY)
            float("pointerX", pointerX)
            float("pointerY", pointerY)
            value("pointerActive", pointerActive)
            value("braking", braking)
            float("elapsed", elapsed)
            float("heat", heat)
            value("overheated", overheated)
            float("dashPhaseTime", dashPhaseTime)
            float("hp", hp)
            float("maxHp", maxHp)
            float("shield", shield)
            float("maxShield", maxShield)
            value("level", level)
            value("data", data)
            value("nextLevelData", nextLevelData)
            value("keys", keys)
            value("kills", kills)
            value("damageDealt", damageDealt)
            value("damageTaken", damageTaken)
            value("damageAbsorbed", damageAbsorbed)
            value("dataCollected", dataCollected)
            value("pickupsCollected", pickupsCollected)
            value("keysCollected", keysCollected)
            value("eliteKills", eliteKills)
            value("bestCombo", bestCombo)
            value("combo", combo)
            float("comboTime", comboTime)
            value("runMatter", runMatter)
            value("totalMatter", totalMatter)
            value("lifetimeMatter", lifetimeMatter)
            float("lastImpact", lastImpact)
            float("lastImpactTime", lastImpactTime)
            float("damageFlash", damageFlash)
            float("runGrace", runGrace)
            float("screenShake", screenShake)
            value("message", message)
            float("messageTime", messageTime)

            float("mass", mass)
            float("damageMultiplier", damageMultiplier)
            float("weaponPower", weaponPower)
            float("coolingRate", coolingRate)
            float("magnetStrength", magnetStrength)
            float("dashImpulse", dashImpulse)
            float("dashHeatCost", dashHeatCost)
            float("regenPerSecond", regenPerSecond)
            float("critChance", critChance)
            float("critMultiplier", critMultiplier)
            float("pickupRadius", pickupRadius)
            float("luck", luck)
            float("dataGain", dataGain)
            float("matterGain", matterGain)
            float("attackSpeed", attackSpeed)
            float("damageReduction", damageReduction)
            float("comboWindow", comboWindow)
            float("overdriveGain", overdriveGain)
            float("dragCoefficient", dragCoefficient)
            float("gripStrength", gripStrength)
            float("slingStrength", slingStrength)
            float("polarityStability", polarityStability)

            value("weapon", weapon)
            value("startingWeapon", startingWeapon)
            value("weaponLevel", weaponLevel)
            float("overdriveCharge", overdriveCharge)
            float("overdriveTime", overdriveTime)
            value("rerollsRemaining", rerollsRemaining)
            value("acquiredItemCount", acquiredItemCount)
            value("recentItem", recentItem)
            value("equippedRelics", equippedRelics.toList())
            float("morningstarAngle", morningstarAngle)
            float("morningstarX", morningstarX)
            float("morningstarY", morningstarY)
            float("previousMorningstarX", previousMorningstarX)
            float("previousMorningstarY", previousMorningstarY)
            value("interpolationPrimed", interpolationPrimed)
            float("weaponBeamTime", weaponBeamTime)
            float("weaponBeamStartX", weaponBeamStartX)
            float("weaponBeamStartY", weaponBeamStartY)
            float("weaponBeamEndX", weaponBeamEndX)
            float("weaponBeamEndY", weaponBeamEndY)
            value("coreShape", coreShape)
            value("choices", choices.toList())

            value("echoHits.size", echoHits.size)
            echoHits.forEachIndexed { index, hit ->
                value("echoHits[$index].enemyId", hit.enemyId)
                float("echoHits[$index].delay", hit.delay)
                float("echoHits[$index].damage", hit.damage)
                value("echoHits[$index].appliesDominant", hit.appliesDominant)
            }

            value("enemies.size", enemies.size)
            enemies.forEachIndexed { index, enemy ->
                val prefix = "enemies[$index]"
                value("$prefix.id", enemy.id)
                value("$prefix.type", enemy.type)
                float("$prefix.x", enemy.x)
                float("$prefix.y", enemy.y)
                float("$prefix.vx", enemy.vx)
                float("$prefix.vy", enemy.vy)
                float("$prefix.hp", enemy.hp)
                float("$prefix.maxHp", enemy.maxHp)
                float("$prefix.radius", enemy.radius)
                float("$prefix.actionTimer", enemy.actionTimer)
                float("$prefix.flash", enemy.flash)
                float("$prefix.contactCooldown", enemy.contactCooldown)
                float("$prefix.weaponCooldown", enemy.weaponCooldown)
                float("$prefix.previousX", enemy.previousX)
                float("$prefix.previousY", enemy.previousY)
                value("$prefix.dead", enemy.dead)
                value("$prefix.killClausesEligible", enemy.killClausesEligible)
                float("$prefix.hitQualificationCooldown", enemy.hitQualificationCooldown)
                float("$prefix.characterMarkTime", enemy.characterMarkTime)
                value("$prefix.lastCharacterDash", enemy.lastCharacterDash)
                value("$prefix.aspect", enemy.aspect)
                value("$prefix.aspectImmune", enemy.aspectImmune)
                value("$prefix.phase", enemy.phase)
                float("$prefix.shiftUntil", enemy.shiftUntil)
                value("$prefix.escortsPending", enemy.escortsPending)
                float("$prefix.lastDamagedAt", enemy.lastDamagedAt)
                float("$prefix.disabledUntil", enemy.disabledUntil)
                float("$prefix.silencedUntil", enemy.silencedUntil)
                value("$prefix.mode", enemy.mode)
                float("$prefix.modeUntil", enemy.modeUntil)
                float("$prefix.facing", enemy.facing)
                value("$prefix.guarded", enemy.guarded)
                value("$prefix.linkedBy", enemy.linkedBy)
                float("$prefix.linkedUntil", enemy.linkedUntil)
                float("$prefix.shieldUntil", enemy.shieldUntil)
                float("$prefix.abilityAt", enemy.abilityAt)
                float("$prefix.thrownUntil", enemy.thrownUntil)
                float("$prefix.slamReadyAt", enemy.slamReadyAt)
                value("$prefix.armored", enemy.armored)
                value("$prefix.armorCracked", enemy.armorCracked)
                value("$prefix.ownerTag", enemy.ownerTag)
                value("$prefix.charges", enemy.charges)
                value("$prefix.phaseStartPending", enemy.phaseStartPending)
                value("$prefix.affix", enemy.affix)
                value("$prefix.champion", enemy.champion)
                floats("$prefix.status", enemy.status)
            }

            value("projectiles.size", projectiles.size)
            projectiles.forEachIndexed { index, projectile ->
                val prefix = "projectiles[$index]"
                float("$prefix.x", projectile.x)
                float("$prefix.y", projectile.y)
                float("$prefix.vx", projectile.vx)
                float("$prefix.vy", projectile.vy)
                float("$prefix.radius", projectile.radius)
                float("$prefix.life", projectile.life)
                value("$prefix.hostile", projectile.hostile)
                float("$prefix.damage", projectile.damage)
                value("$prefix.pierce", projectile.pierce)
                value("$prefix.colorIndex", projectile.colorIndex)
                value("$prefix.sourceWeapon", projectile.sourceWeapon)
                float("$prefix.previousX", projectile.previousX)
                float("$prefix.previousY", projectile.previousY)
                value("$prefix.statusElement", projectile.statusElement)
                value("$prefix.kind", projectile.kind)
                value("$prefix.procKey", projectile.procKey)
                value("$prefix.hasRecordedEnemyHits", projectile.hasRecordedEnemyHits)
                for (enemyId in 1 until nextEntityId) {
                    value("$prefix.recordedEnemy[$enemyId]", projectile.hasRecordedEnemyHit(enemyId))
                }
            }

            value("pickups.size", pickups.size)
            pickups.forEachIndexed { index, pickup ->
                val prefix = "pickups[$index]"
                value("$prefix.type", pickup.type)
                float("$prefix.x", pickup.x)
                float("$prefix.y", pickup.y)
                float("$prefix.vx", pickup.vx)
                float("$prefix.vy", pickup.vy)
                float("$prefix.life", pickup.life)
                float("$prefix.previousX", pickup.previousX)
                float("$prefix.previousY", pickup.previousY)
                value("$prefix.attracted", pickup.attracted)
            }

            value("trail.size", trail.size)
            trail.forEachIndexed { index, point ->
                float("trail[$index].x", point.x)
                float("trail[$index].y", point.y)
                float("trail[$index].age", point.age)
                float("trail[$index].life", point.life)
                float("trail[$index].scale", point.scale)
            }

            value("weaponNodes.size", weaponNodes.size)
            weaponNodes.forEachIndexed { index, node ->
                val prefix = "weaponNodes[$index]"
                value("$prefix.type", node.type)
                float("$prefix.x", node.x)
                float("$prefix.y", node.y)
                float("$prefix.life", node.life)
                float("$prefix.maxLife", node.maxLife)
                float("$prefix.radius", node.radius)
                value("$prefix.procKey", node.procKey)
                float("$prefix.scale", node.scale)
            }

            value("weaponOrbitals.size", weaponOrbitals.size)
            weaponOrbitals.forEachIndexed { index, orbital ->
                val prefix = "weaponOrbitals[$index]"
                value("$prefix.index", orbital.index)
                float("$prefix.x", orbital.x)
                float("$prefix.y", orbital.y)
                float("$prefix.radius", orbital.radius)
                float("$prefix.previousX", orbital.previousX)
                float("$prefix.previousY", orbital.previousY)
            }

            value("totem.present", totem != null)
            totem?.let { activeTotem ->
                float("totem.x", activeTotem.x)
                float("totem.y", activeTotem.y)
                float("totem.pulse", activeTotem.pulse)
            }
        }.fields

    /**
     * The compiled build is an immutable value derived from items, Reborn flags, character and relics;
     * its complete observable result is part of the State, independent of the instance identity.
     */
    private fun ExactFingerprint.buildFingerprint(state: MutableGameState) {
        val build = state.build
        BuildTag.entries.forEach { value("build.tagPoints[$it]", build.tagPoints(it)) }
        StatusElement.entries.forEach { element ->
            value("build.affinity[$element]", build.affinity(element))
            float("build.potency[$element]", build.potency(element))
            float("build.duration[$element]", build.durationMultiplier(element))
            value("build.thresholdCut[$element]", build.thresholdCut(element))
            float("build.breakPower[$element]", build.breakPower(element))
            value("build.breakSpread[$element]", build.breakSpread(element))
            float("build.breakSpreadRadius[$element]", build.breakSpreadRadius(element))
        }
        value("build.dominant", build.dominant)
        value("build.secondary", build.secondary)
        kinetickk.ball.content.api.EffectTrigger.entries.forEach { trigger ->
            value("build.clauses[$trigger]", build.clauses(trigger).toList())
        }
        state.content.items.indices.forEach { id ->
            float("build.magnitude[$id]", build.magnitude(id))
            float("build.bonus[$id]", build.bonus(id))
        }
        RelicId.entries.forEach { float("build.relicUpside[$it]", build.relicUpside(it)) }
        value("build.upsideMask", build.upsideMask)
        value("build.liabilityMask", build.liabilityMask)
        value("build.pairingMask", build.pairingMask)
        value("build.links", build.links.toList())
        value("build.reachableReactionMask", build.reachableReactionMask)
        value("build.ownedItemCount", build.ownedItemCount)
        float("build.reactionPower", build.reactionPower)
        value("build.reactionResidue", build.reactionResidue)
        float("build.reactionRecoil", build.reactionRecoil)
        float("build.reactionRecoilRadius", build.reactionRecoilRadius)
        value("build.alwaysCrit", build.alwaysCrit.toList())
        value("build.critDamageVersus", build.critDamageVersus.toList())
        value("build.damageVersus", build.damageVersus.toList())
        value("build.whileModifiers", build.whileModifiers.toList())
        float("build.nonCriticalDamage", build.nonCriticalDamage)
        float("build.comboDamagePer", build.comboDamagePer)
        float("build.comboDamageCap", build.comboDamageCap)
        value("build.offerCards", build.offerCards)
        float("build.overhealOverfill", build.overhealOverfill)
        float("build.regenerationToDecay", build.regenerationToDecay)
        float("build.critConversionRate", build.critConversionRate)
        value("build.guaranteedCritAfter", build.guaranteedCritAfter)
        float("build.freeDashHeat", build.freeDashHeat)
        float("build.freeDashCooldown", build.freeDashCooldown)
        float("build.obstacleRestitutionBonus", build.obstacleRestitutionBonus)
        float("build.obstacleDamageScale", build.obstacleDamageScale)
        float("build.obstacleLethalBonus", build.obstacleLethalBonus)
        float("build.boostPowerScale", build.boostPowerScale)
        float("build.wellPullScale", build.wellPullScale)
    }

    private class ExactFingerprint {
        val fields = mutableListOf<Pair<String, Any?>>()

        fun value(name: String, value: Any?) {
            fields += name to value
        }

        fun float(name: String, value: Float) {
            fields += name to value.toRawBits()
        }

        fun ints(name: String, values: Iterable<Int>) {
            var index = 0
            values.forEach { value("$name[$index]", it); index++ }
        }

        fun floats(name: String, values: Iterable<Float>) {
            var index = 0
            values.forEach { float("$name[$index]", it); index++ }
        }

        /** Every field of each site, Floats by their bits. */
        fun site(name: String, sites: List<SiteState>) {
            value("$name.size", sites.size)
            sites.forEachIndexed { index, site ->
                val prefix = "$name[$index]"
                value("$prefix.id", site.id)
                value("$prefix.kind", site.kind)
                float("$prefix.x", site.x)
                float("$prefix.y", site.y)
                float("$prefix.expiresAt", site.expiresAt)
                value("$prefix.active", site.active)
                float("$prefix.remaining", site.remaining)
                float("$prefix.clock", site.clock)
                value("$prefix.step", site.step)
                value("$prefix.count", site.count)
                float("$prefix.meter", site.meter)
                float("$prefix.volleyClock", site.volleyClock)
                float("$prefix.warningRemaining", site.warningRemaining)
                float("$prefix.volleyAngle", site.volleyAngle)
                float("$prefix.aimX", site.aimX)
                float("$prefix.aimY", site.aimY)
                value("$prefix.foeIds", site.foeIds.toList())
                site.course.forEachIndexed { mark, point ->
                    float("$prefix.course[$mark].dx", point.dx)
                    float("$prefix.course[$mark].dy", point.dy)
                }
                value("$prefix.course.size", site.course.size)
                value("$prefix.sides", site.sides)
                float("$prefix.par", site.par)
                float("$prefix.axis", site.axis)
                float("$prefix.limit", site.limit)
                value("$prefix.hurtBaseline", site.hurtBaseline.toRawBits())
                value("$prefix.settled", site.settled)
            }
        }
    }
}
