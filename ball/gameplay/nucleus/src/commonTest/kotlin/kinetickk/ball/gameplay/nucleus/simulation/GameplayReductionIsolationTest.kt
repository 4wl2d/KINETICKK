// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RewardFocus
import kinetickk.ball.content.api.SynergyId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.nucleus.model.DelayedRelicHit
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.PointOfInterestState
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.SynergyEffect
import kinetickk.ball.gameplay.nucleus.model.SynergyEffectKind
import kinetickk.ball.gameplay.nucleus.model.Totem
import kinetickk.ball.gameplay.nucleus.model.TrailPoint
import kinetickk.ball.gameplay.nucleus.model.WeaponNode
import kinetickk.ball.gameplay.nucleus.model.WeaponOrbital
import kinetickk.ball.gameplay.nucleus.model.WorldPoint
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
import kinetickk.ball.gameplay.nucleus.reducer.GameReducer
import kinetickk.ball.gameplay.nucleus.reducer.GameReductionResult
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.render.WeaponNodeType
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
            pendingClearedRebirthLevel = null
            recentItem = null
            totem = null
            characterRuntime = characterRuntime.copy(dashOrigin = null)
            pendingArchitectDefeatedWith = null
            directedReward = null
            selectedRewardFocus = null
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
            metaRanks[0] = 1
            discoveredItemIds += 17
            itemStacks[0] = 2
            familyStacks[0] = 3
            relicRanks[RelicId.ECHO_CHAMBER.ordinal] = 1
            addEnemyForTesting(x = 20f, y = 30f)
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
            metaRanks[0] = 1
            discoveredItemIds += 17
            itemStacks[0] = 2
            familyStacks[0] = 3
            relicRanks[relicIndex] = 1
            relicCooldowns[relicIndex] = 0.75f
            relicCounters[relicIndex] = 2
            relicProcCounts[relicIndex] = 3
            agonyMutationCounts[weaponIndex] = 4

            val enemy = addEnemyForTesting(x = 10f, y = 20f)
            enemy.relicCounters[relicIndex] = 5
            enemy.relicTimers[relicIndex] = 0.5f
            enemy.relicValues[relicIndex] = 6f
            delayedRelicHits += DelayedRelicHit(
                relicId = RelicId.ECHO_CHAMBER,
                enemyId = enemy.id,
                delay = 1f,
                damage = 7f,
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
        assertSame(source.metaRanks, pointerBranch.model.metaRanks)
        assertSame(source.enemies, pointerBranch.model.enemies)
        assertSame(source.projectiles, pointerBranch.model.projectiles)
        assertSame(source.enemies, siblingBranch.model.enemies)

        // FrameElapsed is a production full-reduction path. A zero delta isolates copy ownership.
        val isolated = assertIs<GameReductionResult.Accepted>(
            reducer.reduce(
                pointerBranch,
                GameplayInteractionPulse.FrameElapsed.fromValidated(0f),
            ),
        ).state.model

        assertNotSame(pointerBranch.model.gameplayRandom, isolated.gameplayRandom)
        assertNotSame(pointerBranch.model.metaRanks, isolated.metaRanks)
        assertNotSame(pointerBranch.model.enemies, isolated.enemies)
        assertNotSame(pointerBranch.model.projectiles, isolated.projectiles)
        assertNotSame(pointerBranch.model.totem, isolated.totem)

        // Mutate every mutable storage family that the scalar COW path is allowed to share.
        isolated.gameplayRandom.nextInt()
        isolated.unlockedWeaponSet.clear()
        isolated.metaRanks[0] = 91
        isolated.discoveredItemIds.clear()
        isolated.itemStacks[0] = 92
        isolated.familyStacks[0] = 93
        isolated.relicRanks[relicIndex] = 94
        isolated.relicCooldowns[relicIndex] = 95f
        isolated.relicCounters[relicIndex] = 96
        isolated.relicProcCounts[relicIndex] = 97
        isolated.agonyMutationCounts[weaponIndex] = 98
        isolated.delayedRelicHits.single().delay = 99f
        isolated.enemies.single().apply {
            x = 100f
            relicCounters[relicIndex] = 101
            relicTimers[relicIndex] = 102f
            relicValues[relicIndex] = 103f
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

        listOf(source, siblingBranch.model).forEach { retained ->
            assertEquals(expectedNextRandom, retained.gameplayRandom.copy().nextInt())
            assertTrue(WeaponId.FLUX_WAKE in retained.unlockedWeaponSet)
            assertTrue(WeaponId.MORNINGSTAR in retained.unlockedWeaponSet)
            assertEquals(1, retained.metaRanks[0])
            assertTrue(17 in retained.discoveredItemIds)
            assertEquals(2, retained.itemStacks[0])
            assertEquals(3, retained.familyStacks[0])
            assertEquals(1, retained.relicRanks[relicIndex])
            assertEquals(0.75f, retained.relicCooldowns[relicIndex])
            assertEquals(2, retained.relicCounters[relicIndex])
            assertEquals(3, retained.relicProcCounts[relicIndex])
            assertEquals(4, retained.agonyMutationCounts[weaponIndex])
            assertEquals(1f, retained.delayedRelicHits.single().delay)

            retained.enemies.single().let { enemy ->
                assertEquals(10f, enemy.x)
                assertEquals(5, enemy.relicCounters[relicIndex])
                assertEquals(0.5f, enemy.relicTimers[relicIndex])
                assertEquals(6f, enemy.relicValues[relicIndex])
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
            assertSame(source.metaRanks, reduction.state.model.metaRanks)
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
    fun threeProductionFrameSiblingsDetachOnlyActiveRelicTimersAndRemainDeterministic() {
        val glassIndex = RelicId.GLASS_WITNESS.ordinal
        val scarIndex = RelicId.SCAR_TISSUE.ordinal
        val source = MutableGameState(canonicalGameplayContent, seed = 7_201).apply {
            startRun()
            enemies.clear()
            spawnClock = 1_000f
            // Admit exactly one fixed step independently of presentation-speed preferences.
            accumulator = MutableGameState.FIXED_STEP
            acquireRelicForTesting(RelicId.GLASS_WITNESS)
            acquireRelicForTesting(RelicId.SCAR_TISSUE)
            relicCooldowns[glassIndex] = 0.8f
            val enemy = addEnemyForTesting(x = 240f, y = 40f)
            enemy.relicCounters[scarIndex] = 2
            enemy.relicTimers[glassIndex] = 0.6f
            enemy.relicTimers[scarIndex] = 0.7f
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
            assertFalse(branch.enemies.single().relicTimers.sharesStorageWith(source.enemies.single().relicTimers))
            assertTrue(branch.relicRanks.sharesStorageWith(source.relicRanks))
            assertTrue(branch.relicCounters.sharesStorageWith(source.relicCounters))
            assertEquals(0.8f, source.relicCooldowns[glassIndex])
            assertEquals(0.6f, source.enemies.single().relicTimers[glassIndex])
            assertEquals(0.7f, source.enemies.single().relicTimers[scarIndex])
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
        val tidalIndex = RelicId.TIDAL_LOCK.ordinal
        val glassIndex = RelicId.GLASS_WITNESS.ordinal
        val source = MutableGameState(canonicalGameplayContent, seed = 8_301).apply {
            startRun()
            enemies.clear()
            spawnClock = 1_000f
            // Admit exactly one fixed step independently of presentation-speed preferences.
            accumulator = MutableGameState.FIXED_STEP
            acquireRelicForTesting(RelicId.TIDAL_LOCK)
            acquireRelicForTesting(RelicId.GLASS_WITNESS)
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
            assertEquals(1, enemy.relicCounters[tidalIndex])
            assertEquals(1, enemy.relicCounters[glassIndex])
            assertEquals(3f, enemy.relicTimers[glassIndex])
            assertFalse(enemy.relicCounters.sharesStorageWith(source.enemies.single().relicCounters))
            assertFalse(enemy.relicTimers.sharesStorageWith(source.enemies.single().relicTimers))
        }
        assertEquals(0, source.enemies.single().relicCounters[tidalIndex])
        assertEquals(0, source.enemies.single().relicCounters[glassIndex])
        assertEquals(0f, source.enemies.single().relicTimers[glassIndex])
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
            assertFalse(branch.familyStacks.sharesStorageWith(source.familyStacks))
            assertFalse(branch.discoveredItemIds.sharesStorageWith(source.discoveredItemIds))
            assertTrue(branch.relicRanks.sharesStorageWith(source.relicRanks))
        }
        assertEquals(sourceRandomCursor, source.gameplayRandom.snapshot())
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
            acquireRelicForTesting(RelicId.TIDAL_LOCK)
            acquireRelicForTesting(RelicId.GLASS_WITNESS)
            acquireRelicForTesting(RelicId.STATIC_CHORUS)
            acquireItemForTesting(0)
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
    }

    private fun MutableGameState.populateAdditionalRetainedState() {
        characterRuntime = CharacterRuntime(
            charge = 0.3f,
            barrier = 4f,
            barrierTime = 0.8f,
            previousSpeed = 123f,
            previousHeading = 0.4f,
            turnArc = 0.7f,
            turnDistance = 50f,
            wasBraking = true,
            parryWindow = 0.2f,
            parryCooldown = 0.6f,
            ringRadius = 58f,
            dashSequence = 2,
            dashOrigin = WorldPoint(4f, -0f),
            dashRecorded = true,
            ramPower = 0.9f,
            lattice = listOf(WorldPoint(-1f, 2f), WorldPoint(3f, -4f)),
            latticeTime = 0.5f,
        )
        pendingEliteKills = 2
        pendingDashHits = 3
        pendingCompletedOrbits = 4
        pendingArchitectDefeatedWith = CoreShape.PRISM
        pointsOfInterest = listOf(
            PointOfInterestState(
                offerId = 1,
                kind = PointOfInterestKind.SEALED_ANOMALY,
                x = 400f,
                y = 300f,
                expiresAt = 100f,
                remaining = 20f,
                nextBeacon = 2,
                orbitSeconds = 1f,
                defenderIds = immutableListOf(11, 13),
                defeatedDefenders = 1,
                volleyClock = 1.8f,
                warningRemaining = 0.3f,
                volleyAngle = 0.4f,
            ),
        )
        nextPointOfferIndex = 2
        pendingDirectedRewards = listOf(DirectedReward.ITEM_AND_REPAIR, DirectedReward.WEAPON)
        directedReward = DirectedReward.RELIC
        selectedRewardFocus = RewardFocus.CONTROL
        synergyEffects = listOf(
            SynergyEffect(
                synergy = SynergyId.FRACTURE_DECAY,
                kind = SynergyEffectKind.TRAIL,
                remaining = 1f,
                damage = 2f,
                enemyId = 11,
                x = 200f,
                y = 300f,
                endX = 400f,
                endY = 500f,
                radius = 70f,
            ),
        )
        synergyCooldowns[SynergyId.FRACTURE_DECAY.ordinal] = 0.7f
        synergyManeuverCharge = 0.4f
        ghostDashPending = true
        ghostDashStartX = 12f
        ghostDashStartY = 13f
        smoothedVelocityX = 14f
        smoothedVelocityY = 15f
        turnHeadingEstablished = true
        turnHoldTime = 0.2f
        turnRecoveryCooldown = 0.3f
        turnDirection = -1
        enemies.firstOrNull()?.apply {
            characterMarkTime = 0.75f
            lastCharacterDash = 2
        }
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
            pendingArchitectDefeatedWith != null

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
            value("activeRebirthProfile", activeRebirthProfile)
            value("unlockedWeaponSet", unlockedWeaponSet.toList())
            value("unlockedWeaponView", unlockedWeaponView.toList())
            ints("metaRanks", metaRanks)
            value("discoveredItemIds", discoveredItemIds.toList())
            value("pendingDiscoveredItemIds", pendingDiscoveredItemIds.toList())
            ints("itemStacks", itemStacks)
            ints("familyStacks", familyStacks)
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
            float("characterRuntime.charge", characterRuntime.charge)
            float("characterRuntime.barrier", characterRuntime.barrier)
            float("characterRuntime.barrierTime", characterRuntime.barrierTime)
            float("characterRuntime.previousSpeed", characterRuntime.previousSpeed)
            float("characterRuntime.previousHeading", characterRuntime.previousHeading)
            float("characterRuntime.turnArc", characterRuntime.turnArc)
            float("characterRuntime.turnDistance", characterRuntime.turnDistance)
            value("characterRuntime.wasBraking", characterRuntime.wasBraking)
            float("characterRuntime.parryWindow", characterRuntime.parryWindow)
            float("characterRuntime.parryCooldown", characterRuntime.parryCooldown)
            float("characterRuntime.ringRadius", characterRuntime.ringRadius)
            value("characterRuntime.dashSequence", characterRuntime.dashSequence)
            value("characterRuntime.dashOrigin.present", characterRuntime.dashOrigin != null)
            characterRuntime.dashOrigin?.let { origin ->
                float("characterRuntime.dashOrigin.x", origin.x)
                float("characterRuntime.dashOrigin.y", origin.y)
            }
            value("characterRuntime.dashRecorded", characterRuntime.dashRecorded)
            float("characterRuntime.ramPower", characterRuntime.ramPower)
            value("characterRuntime.lattice.size", characterRuntime.lattice.size)
            characterRuntime.lattice.forEachIndexed { index, point ->
                float("characterRuntime.lattice[$index].x", point.x)
                float("characterRuntime.lattice[$index].y", point.y)
            }
            float("characterRuntime.latticeTime", characterRuntime.latticeTime)
            value("pointsOfInterest.size", pointsOfInterest.size)
            pointsOfInterest.forEachIndexed { index, point ->
                val prefix = "pointsOfInterest[$index]"
                value("$prefix.offerId", point.offerId)
                value("$prefix.kind", point.kind)
                float("$prefix.x", point.x)
                float("$prefix.y", point.y)
                float("$prefix.expiresAt", point.expiresAt)
                value("$prefix.active", point.active)
                float("$prefix.remaining", point.remaining)
                value("$prefix.nextBeacon", point.nextBeacon)
                float("$prefix.orbitSeconds", point.orbitSeconds)
                value("$prefix.defenderIds", point.defenderIds.toList())
                value("$prefix.defeatedDefenders", point.defeatedDefenders)
                float("$prefix.volleyClock", point.volleyClock)
                float("$prefix.warningRemaining", point.warningRemaining)
                float("$prefix.volleyAngle", point.volleyAngle)
            }
            value("nextPointOfferIndex", nextPointOfferIndex)
            value("pendingDirectedRewards", pendingDirectedRewards.toList())
            value("directedReward", directedReward)
            value("selectedRewardFocus", selectedRewardFocus)

            value("nextEntityId", nextEntityId)
            float("spawnClock", spawnClock)
            float("nextEliteAt", nextEliteAt)
            float("dashBufferTime", dashBufferTime)
            value("bossSpawned", bossSpawned)
            value("keyboardBrakeActive", keyboardBrakeActive)
            value("secondaryBrakeActive", secondaryBrakeActive)
            value("touchBrakeActive", touchBrakeActive)
            float("uiScale", uiScale)
            float("accumulator", accumulator)
            value("lastTransitionSteps", lastTransitionSteps)
            float("previousCoreX", previousCoreX)
            float("previousCoreY", previousCoreY)
            float("previousSingularityX", previousSingularityX)
            float("previousSingularityY", previousSingularityY)
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
            ints("relicProcCounts", relicProcCounts)
            ints("agonyMutationCounts", agonyMutationCounts)
            float("slipstreamRelayTime", slipstreamRelayTime)
            float("borrowedMomentTime", borrowedMomentTime)
            float("brakepointCharge", brakepointCharge)
            value("synergyEffects.size", synergyEffects.size)
            synergyEffects.forEachIndexed { index, effect ->
                val prefix = "synergyEffects[$index]"
                value("$prefix.synergy", effect.synergy)
                value("$prefix.kind", effect.kind)
                float("$prefix.remaining", effect.remaining)
                float("$prefix.damage", effect.damage)
                value("$prefix.enemyId", effect.enemyId)
                float("$prefix.x", effect.x)
                float("$prefix.y", effect.y)
                float("$prefix.endX", effect.endX)
                float("$prefix.endY", effect.endY)
                float("$prefix.radius", effect.radius)
            }
            floats("synergyCooldowns", synergyCooldowns)
            float("synergyManeuverCharge", synergyManeuverCharge)
            value("ghostDashPending", ghostDashPending)
            float("ghostDashStartX", ghostDashStartX)
            float("ghostDashStartY", ghostDashStartY)
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
            float("timeSinceDamage", timeSinceDamage)
            float("hurtCooldown", hurtCooldown)
            float("lastAimDirectionX", lastAimDirectionX)
            float("lastAimDirectionY", lastAimDirectionY)
            value("bankedThisRun", bankedThisRun)
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
            value("rebirthLevel", rebirthLevel)
            float("screenWidth", screenWidth)
            float("screenHeight", screenHeight)
            float("coreX", coreX)
            float("coreY", coreY)
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
            float("weaponBeamTime", weaponBeamTime)
            float("weaponBeamStartX", weaponBeamStartX)
            float("weaponBeamStartY", weaponBeamStartY)
            float("weaponBeamEndX", weaponBeamEndX)
            float("weaponBeamEndY", weaponBeamEndY)
            value("coreShape", coreShape)
            value("choices", choices.toList())

            value("delayedRelicHits.size", delayedRelicHits.size)
            delayedRelicHits.forEachIndexed { index, hit ->
                value("delayedRelicHits[$index].relicId", hit.relicId)
                value("delayedRelicHits[$index].enemyId", hit.enemyId)
                float("delayedRelicHits[$index].delay", hit.delay)
                float("delayedRelicHits[$index].damage", hit.damage)
                value("delayedRelicHits[$index].linkedEnemyId", hit.linkedEnemyId)
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
                value("$prefix.relicKillProcsEligible", enemy.relicKillProcsEligible)
                float("$prefix.relicQualificationCooldown", enemy.relicQualificationCooldown)
                float("$prefix.characterMarkTime", enemy.characterMarkTime)
                value("$prefix.lastCharacterDash", enemy.lastCharacterDash)
                ints("$prefix.relicCounters", enemy.relicCounters)
                floats("$prefix.relicTimers", enemy.relicTimers)
                floats("$prefix.relicValues", enemy.relicValues)
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
            }

            value("trail.size", trail.size)
            trail.forEachIndexed { index, point ->
                float("trail[$index].x", point.x)
                float("trail[$index].y", point.y)
                float("trail[$index].age", point.age)
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
            }

            value("weaponOrbitals.size", weaponOrbitals.size)
            weaponOrbitals.forEachIndexed { index, orbital ->
                val prefix = "weaponOrbitals[$index]"
                value("$prefix.index", orbital.index)
                float("$prefix.x", orbital.x)
                float("$prefix.y", orbital.y)
                float("$prefix.radius", orbital.radius)
            }

            value("totem.present", totem != null)
            totem?.let { activeTotem ->
                float("totem.x", activeTotem.x)
                float("totem.y", activeTotem.y)
                float("totem.pulse", activeTotem.pulse)
            }
        }.fields

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
    }
}
