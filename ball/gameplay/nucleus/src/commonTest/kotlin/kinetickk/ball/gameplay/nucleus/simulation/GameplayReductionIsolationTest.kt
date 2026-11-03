// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.OmenId
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.content.api.DimensionObjective
import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.DimensionRewardGrant
import kinetickk.ball.gameplay.nucleus.model.DimensionVisit
import kinetickk.ball.gameplay.nucleus.model.Ember
import kinetickk.ball.gameplay.nucleus.model.HaloRing
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.Totem
import kinetickk.ball.gameplay.nucleus.model.TrailPoint
import kinetickk.ball.gameplay.nucleus.model.WeaponNode
import kinetickk.ball.gameplay.nucleus.model.WeaponOrbital
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kinetickk.ball.gameplay.nucleus.projection.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.dimensions.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.enemies.*
import kinetickk.ball.gameplay.nucleus.simulation.progression.*
import kinetickk.ball.gameplay.nucleus.simulation.structures.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*

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

        listOf(source.deepCopy()).forEach { fork ->
            assertEquivalentSimulation(source, fork)
            val projection = fork.toRenderModel()
            assertEquals(WeaponId.MORNINGSTAR, projection.weapon)
            assertEquals(CoreShape.PRISM, projection.coreShape)
            assertEquals(source.settings, projection.settings)
            assertEquals(source.choices, projection.choices)
        }

        // A later cleared value must stay cleared, including fields with no bootstrap value.
        val cleared = source.deepCopy().apply {
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
        listOf(cleared.deepCopy()).forEach { fork ->
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
    fun subStepFrameRunsNoStepAndLeavesTheCommittedStateUntouched() {
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

        val banked = source.deepCopy().apply { bankRunMatter() }
        val update = assertNotNull(banked.takeProgressUpdate())
        assertEquals(5, update.totalKills)
        assertEquals(6, update.ricochets)
        assertEquals(7, update.dataPickups)
        assertFalse(banked.hasPendingReductionOutputs())
        assertEquals(5, source.pendingTotalKills, "the committed source keeps its counters")

        // Any other progress carries the counters along without waiting for the bank.
        val elite = source.deepCopy().apply { pendingEliteKills = 1 }
        assertEquals(5, assertNotNull(elite.takeProgressUpdate()).totalKills)
    }

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
    fun aCourseBrokenInACandidateLeavesTheCommittedVisitAloneAndEveryNewVisitFactReachesTheFingerprint() {
        val source = MutableGameState(canonicalGameplayContent, seed = 6_061, initialMatter = 0).apply {
            startRun()
            enemies.clear()
            elapsed = 200f
            enterDimensionForTesting(WorldLayer.ZENITH, DimensionObjective.ASCENT)
            // The split to the next ring already ran out: the candidate's first step breaks the course.
            dimensionRun = dimensionRun.copy(visit = checkNotNull(dimensionRun.visit).copy(splitEndsAt = 199f, splitSeconds = 2f))
            drainReferenceOutputs()
        }
        val committedFingerprint = source.exactSimulationFingerprint()
        val committedRun = source.dimensionRun
        val candidate = assertIs<GameReductionResult.Accepted>(
            GameReducer().reduce(EngineState(source), GameplayInteractionPulse.FrameElapsed.fromValidated(MutableGameState.FIXED_STEP)),
        ).state.model
        assertTrue(checkNotNull(candidate.dimensionRun.visit).failed, "the candidate broke the course")
        assertEquals(committedFingerprint, source.exactSimulationFingerprint(), "the break left the committed visit alone")
        assertSame(committedRun, source.dimensionRun)
        assertFalse(checkNotNull(source.dimensionRun.visit).failed)

        // The split clock, the failed objective and an ember's throw origin are retained facts of the exact fingerprint.
        val withEmber = source.deepCopy().apply {
            dimensionRun = dimensionRun.copy(visit = checkNotNull(dimensionRun.visit).copy(embers = immutableListOf(Ember(1f, 2f, 230f))))
        }
        val base = withEmber.exactSimulationFingerprint()
        listOf<(DimensionVisit) -> DimensionVisit>(
            { it.copy(splitEndsAt = 230f) }, { it.copy(splitSeconds = 3f) }, { it.copy(failed = true) },
            { it.copy(embers = immutableListOf(Ember(1f, 2f, 230f, fromX = 4f, fromY = 2f))) },
            { it.copy(embers = immutableListOf(Ember(1f, 2f, 230f, fromX = 1f, fromY = 5f))) },
        ).forEachIndexed { index, change ->
            val changed = withEmber.deepCopy().apply { dimensionRun = dimensionRun.copy(visit = change(checkNotNull(dimensionRun.visit))) }
            assertNotEquals(base, changed.exactSimulationFingerprint(), "change $index")
            assertEquals(changed.exactSimulationFingerprint(), changed.deepCopy().exactSimulationFingerprint())
        }
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

    private fun MutableGameState.drainReferenceOutputs() {
        takeVisualFxCues()
        takeProgressUpdate()
        takeSoundCues()
    }
}
