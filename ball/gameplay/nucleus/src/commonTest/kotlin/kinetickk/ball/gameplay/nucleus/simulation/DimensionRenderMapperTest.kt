// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DimensionObjective
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.nucleus.model.DimensionRewardGrant
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
import kinetickk.ball.gameplay.nucleus.reducer.GameReducer
import kinetickk.ball.gameplay.nucleus.reducer.GameReductionResult
import kinetickk.ball.gameplay.nucleus.render.DimensionProjection
import kinetickk.ball.gameplay.nucleus.render.EmberProjection
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.HaloProjection
import kinetickk.ball.gameplay.nucleus.render.PortalProjection
import kinetickk.ball.gameplay.nucleus.render.TrialClock
import kinetickk.ball.gameplay.nucleus.render.TrialReward
import kinetickk.ball.gameplay.nucleus.render.TrialSource
import kinetickk.ball.gameplay.nucleus.render.TrialValue
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The dimension facts presentation draws: realspace's shared default, rifts and their locked look, and a visit. */
class DimensionRenderMapperTest {
    private val policy = canonicalGameplayContent.dimensions

    private fun game(seed: Int = 80_801) = MutableGameState(canonicalGameplayContent, seed).apply {
        resize(1_280f, 800f)
        startRun()
        enemies.clear()
        elapsed = 200f
    }

    @Test
    fun realspacePublishesTheSharedDefaultUntilARiftNeedsAFact() {
        val game = game()
        assertSame(DimensionProjection.REALSPACE, game.dimensionProjection(null, null))
        repeat(30) { game.simulateStep(MutableGameState.FIXED_STEP) }
        assertSame(DimensionProjection.REALSPACE, game.dimensionProjection(null, null), "a quiet run stays on the shared default")

        // An open window rift needs no fact of its own while the Core could enter it.
        game.openPortalForTesting(WorldLayer.ZENITH)
        assertSame(DimensionProjection.REALSPACE, game.dimensionProjection(null, null))

        // The glitch rift is published with its clock; it leads to the Archive and is no exit.
        val archive = game()
        val (x, y) = archive.openPortalForTesting(WorldLayer.ARCHIVE)
        val portal = checkNotNull(archive.dimensionRun.glitchPortal)
        val projection = archive.dimensionProjection(null, null)
        assertEquals(WorldLayer.BASE, projection.layer)
        assertEquals(
            PortalProjection(portal.id, x, y, WorldLayer.ARCHIVE, locked = false, glitch = true, remaining = portal.expiresAt - archive.elapsed),
            projection.glitchPortal,
        )
        assertNull(projection.exitPortal)
        assertEquals(0, projection.lockedRifts)
    }

    @Test
    fun aRiftTheCoreCannotEnterIsPublishedLockedEvenWhilePaused() {
        val game = game()
        game.openPortalForTesting(WorldLayer.NADIR)
        game.openPortalForTesting(WorldLayer.ARCHIVE)
        assertEquals(0, game.dimensionProjection(null, null).lockedRifts)
        assertFalse(checkNotNull(game.dimensionProjection(null, null).glitchPortal).locked)

        // A living boss-class enemy refuses every entry (entering would delete it and its reward).
        val herald = game.addEnemyForTesting(900f, 0f, type = EnemyType.HERALD)
        val locked = game.dimensionProjection(null, null)
        assertEquals(WorldLayer.NADIR.bit or WorldLayer.ARCHIVE.bit, locked.lockedRifts)
        assertTrue(checkNotNull(locked.glitchPortal).locked)

        // The phase is not an entry rule of the look: pausing keeps both a locked and an open rift as they were.
        game.phase = GamePhase.PAUSED
        assertEquals(locked, game.dimensionProjection(null, null))
        game.enemies.remove(herald)
        assertEquals(0, game.dimensionProjection(null, null).lockedRifts, "an open rift stays open while paused")
        game.phase = GamePhase.RUNNING

        // A trial in progress refuses both rifts too (one engagement at a time).
        val trial = SiteState(id = 1L, kind = SiteKind.COLLAPSING_ORBIT, x = game.coreX - 400f, y = game.coreY, expiresAt = 260f, active = true)
        game.sites = game.sites + trial
        assertEquals(WorldLayer.NADIR.bit or WorldLayer.ARCHIVE.bit, game.dimensionProjection(null, null).lockedRifts)
    }

    @Test
    fun aVisitPublishesItsClockObjectiveCourseAndExitRift() {
        val game = game()
        game.enterDimensionForTesting(WorldLayer.ZENITH, DimensionObjective.ASCENT)
        val visit = checkNotNull(game.dimensionRun.visit)
        game.elapsed += 4f
        game.dimensionLocalClock = 3.25f
        val projection = game.dimensionProjection(null, null)
        assertEquals(WorldLayer.ZENITH, projection.layer)
        assertEquals(DimensionObjective.ASCENT, projection.objective)
        assertEquals(visit.endsAt - game.elapsed, projection.remaining)
        assertEquals(visit.endsAt - visit.enteredAt, projection.duration)
        assertEquals(0, projection.progress)
        assertEquals(visit.goal, projection.goal)
        assertFalse(projection.completed)
        assertEquals(game.dimensionPace(), projection.pace)
        assertEquals(3.25f, projection.localClock)
        assertEquals(0f, projection.scorching, "nothing scorches in the Zenith")
        // The course's first two rings with their places; the next ring is the one whose place is the progress.
        assertEquals(visit.halos.map { HaloProjection(it.id, it.x, it.y, it.courseIndex) }, projection.halos.toList())
        assertEquals(listOf(0, 1), projection.halos.filter { it.courseIndex >= 0 }.map { it.courseIndex })
        assertEquals(visit.halos.single { it.courseIndex == 0 }.id, projection.nextHaloId)
        assertNull(projection.exitPortal)
        assertNull(projection.glitchPortal)

        // Completion opens the exit rift back to realspace and leaves no ring to fly through next.
        game.completeObjectiveForTesting()
        val exit = checkNotNull(checkNotNull(game.dimensionRun.visit).exitPortal)
        val completed = game.dimensionProjection(null, null)
        assertTrue(completed.completed)
        assertEquals(-1, completed.nextHaloId)
        assertEquals(
            PortalProjection(exit.id, exit.x, exit.y, WorldLayer.BASE, locked = false, glitch = false, remaining = exit.expiresAt - game.elapsed),
            completed.exitPortal,
        )

        // Back in realspace the shared default returns.
        game.skipToVisitEndForTesting()
        game.updateDimension(MutableGameState.FIXED_STEP)
        assertSame(DimensionProjection.REALSPACE, game.dimensionProjection(null, null))
    }

    @Test
    fun theNadirHeatWarningRampsOverItsGraceAndHoldsWhileItScorches() {
        val game = game()
        game.enterDimensionForTesting(WorldLayer.NADIR, DimensionObjective.HARVEST)
        val physics = policy.definition(WorldLayer.NADIR).physics
        assertEquals(0f, game.dimensionProjection(null, null).scorching, "no warning at pace")
        game.dimensionSlowSeconds = physics.scorchGraceSeconds * 0.5f
        assertEquals(0.5f, game.dimensionProjection(null, null).scorching, 1e-6f)
        game.dimensionSlowSeconds = physics.scorchGraceSeconds
        assertEquals(1f, game.dimensionProjection(null, null).scorching)
        game.dimensionSlowSeconds = physics.scorchGraceSeconds + 3f
        assertEquals(1f, game.dimensionProjection(null, null).scorching, "full while the scorch ticks")
    }

    @Test
    fun embersCarryTheirRemainingLifeAndReprojectOnlyWhenItChanges() {
        val game = game()
        game.enterDimensionForTesting(WorldLayer.NADIR, DimensionObjective.HARVEST)
        // One ember thrown in from a kill, one placed where it lies.
        game.dropEmber(game.coreX + 400f, game.coreY - 120f, fromX = game.coreX + 250f, fromY = game.coreY - 40f)
        game.dropEmber(game.coreX - 300f, game.coreY + 260f)
        game.elapsed += 0.5f
        val life = policy.objectives.emberLifeSeconds
        val embers = checkNotNull(game.dimensionRun.visit).embers
        val first = game.dimensionProjection(null, null)
        assertEquals(
            embers.map { EmberProjection(it.x, it.y, it.expiresAt - game.elapsed, it.fromX, it.fromY, life - (it.expiresAt - game.elapsed)) },
            first.embers.toList(),
        )
        assertEquals(game.coreX + 250f to game.coreY - 40f, first.embers[0].fromX to first.embers[0].fromY)
        assertEquals(first.embers[1].x to first.embers[1].y, first.embers[1].fromX to first.embers[1].fromY, "a placed ember has no throw")
        assertEquals(0.5f, first.embers[0].age, 1e-5f)

        // The same state keeps the previous projection whole; a later clock reprojects the embers (their life ran on).
        assertSame(first, game.dimensionProjection(first, game))
        game.elapsed += 0.25f
        val later = game.dimensionProjection(first, game)
        assertNotSame(first.embers, later.embers)
        assertEquals(first.embers[0].remaining - 0.25f, later.embers[0].remaining, 1e-5f)
        assertEquals(first.embers[0].age + 0.25f, later.embers[0].age, 1e-5f)
        assertSame(first.halos, later.halos)
    }

    @Test
    fun theSplitToTheNextRingAndABrokenCourseArePublished() {
        val game = game()
        game.enterDimensionForTesting(WorldLayer.ZENITH, DimensionObjective.ASCENT)
        val idle = game.dimensionProjection(null, null)
        assertEquals(0f to 0f, idle.splitRemaining to idle.splitDuration, "no split before the first ring counts")
        assertFalse(idle.failed)
        val visit = checkNotNull(game.dimensionRun.visit)
        game.dimensionRun = game.dimensionRun.copy(visit = visit.copy(splitEndsAt = game.elapsed + 1.5f, splitSeconds = 2.5f))
        val running = game.dimensionProjection(null, null)
        assertEquals(1.5f, running.splitRemaining, 1e-4f)
        assertEquals(2.5f, running.splitDuration)
        assertEquals(visit.halos.single { it.courseIndex == 0 }.id, running.nextHaloId)
        // Run out but not yet stepped: nothing is left of it; the next step breaks the course.
        game.elapsed += 2f
        assertEquals(0f, game.dimensionProjection(null, null).splitRemaining)
        game.updateDimension(MutableGameState.FIXED_STEP)
        val broken = game.dimensionProjection(null, null)
        assertTrue(broken.failed)
        assertFalse(broken.completed)
        assertEquals(-1, broken.nextHaloId)
        assertEquals(0f to 0f, broken.splitRemaining to broken.splitDuration)
        assertTrue(broken.halos.none { it.courseIndex >= 0 }, "the broken course's rings left")
    }

    @Test
    fun aReusedVisitProjectionMatchesAFreshOneAcrossReducedSteps() {
        val frame = GameplayInteractionPulse.FrameElapsed.fromValidated(1f / 60f)
        listOf(WorldLayer.ZENITH to DimensionObjective.ASCENT, WorldLayer.NADIR to DimensionObjective.HARVEST).forEach { (layer, objective) ->
            val initial = game().apply {
                enterDimensionForTesting(layer, objective)
                if (layer == WorldLayer.NADIR) {
                    dropEmber(coreX + 200f, coreY)
                    dropEmber(coreX, coreY + 340f)
                }
            }
            var state = EngineState(initial)
            var projection = initial.toRenderModel()
            val reducer = GameReducer()
            var reusedHalos = 0
            repeat(40) { step ->
                val accepted = assertIs<GameReductionResult.Accepted>(reducer.reduce(state, frame))
                val optimized = accepted.state.model.toRenderModel(reusableCollections = projection, identitySource = state.model)
                val fresh = accepted.state.model.toRenderModel()
                assertEquals(fresh.dimension, optimized.dimension, "$layer step $step")
                assertEquals(layer, optimized.dimension.layer)
                if (optimized.dimension.halos.isNotEmpty() && optimized.dimension.halos === projection.dimension.halos) reusedHalos++
                state = accepted.state
                projection = optimized
            }
            if (layer == WorldLayer.ZENITH) assertTrue(reusedHalos > 0, "unchanged rings keep their projected list")
            assertNotNull(projection.dimension.objective)
        }
    }

    /**
     * A counted objective's panel: its name and instruction, no clock, one cell per ring or ember with the count as its
     * value and the layer's reward; the mapper publishes it (no site or dungeon runs in a visit), a completed objective
     * stays full until the visit ends, and realspace shows none.
     */
    @Test
    fun aCountedObjectivesTrialPanelShowsItsCellsCountAndReward() {
        val cases = listOf(
            Triple(WorldLayer.ZENITH, DimensionObjective.ASCENT, TrialReward.BLESSING),
            Triple(WorldLayer.NADIR, DimensionObjective.HARVEST, TrialReward.PACT),
        )
        for ((layer, objective, reward) in cases) {
            val game = game()
            assertNull(game.dimensionTrialProjection(), "realspace has no dimension panel")
            game.enterDimensionForTesting(layer, objective)
            val goal = checkNotNull(game.dimensionRun.visit).goal
            val trial = checkNotNull(game.dimensionTrialProjection())
            assertEquals(TrialSource.DIMENSION, trial.source)
            assertEquals(layer, trial.dimension)
            assertNull(trial.kind)
            assertEquals(objective.label, trial.title)
            assertEquals(objective.instruction, trial.rules)
            assertEquals(TrialClock.NONE, trial.clock, "$objective: the plate carries the visit timer")
            assertEquals(goal, trial.segments)
            assertEquals(TrialValue.COUNT, trial.value)
            assertEquals(0f, trial.done)
            assertEquals(goal.toFloat(), trial.required)
            assertEquals(0f, trial.progress)
            assertEquals(reward, trial.reward)
            assertEquals(trial, game.toRenderModel().trial, "$objective: the mapper publishes the dimension panel")

            game.dimensionRun = game.dimensionRun.copy(visit = checkNotNull(game.dimensionRun.visit).copy(progress = 3))
            val counted = checkNotNull(game.dimensionTrialProjection())
            assertEquals(3f, counted.done)
            assertEquals(3f / goal, counted.progress, 1e-6f)

            game.completeObjectiveForTesting()
            val completed = checkNotNull(game.dimensionTrialProjection())
            assertEquals(1f, completed.progress, "$objective: complete stays full")
            assertEquals(goal.toFloat(), completed.done)
            game.skipToVisitEndForTesting()
            game.updateDimension(MutableGameState.FIXED_STEP)
            assertNull(game.dimensionTrialProjection(), "$objective: the panel leaves with the visit")
            assertNull(game.toRenderModel().trial)
        }
    }

    /**
     * A boss objective's panel is one cell without a value: empty until the boss arrives, filling with the integrity it
     * has lost, full once its fall completes the objective; the Archive pays its trove.
     */
    @Test
    fun aBossObjectivesTrialPanelFillsWithTheIntegrityItsBossLost() {
        val cases = listOf(
            Triple(WorldLayer.ZENITH, DimensionObjective.JUDGEMENT, TrialReward.BLESSING),
            Triple(WorldLayer.NADIR, DimensionObjective.PYRE, TrialReward.PACT),
            Triple(WorldLayer.ARCHIVE, DimensionObjective.ANTECEDENT, TrialReward.TROVE),
        )
        for ((layer, objective, reward) in cases) {
            val game = game()
            game.enterDimensionForTesting(layer, objective)
            val waiting = checkNotNull(game.dimensionTrialProjection())
            assertEquals(1, waiting.segments)
            assertEquals(TrialValue.NONE, waiting.value)
            assertEquals(0f, waiting.progress, "$objective: nothing before its boss arrives")
            assertEquals(reward, waiting.reward)
            assertEquals(objective.label, waiting.title)

            val boss = game.addEnemyForTesting(game.coreX + 900f, game.coreY, hp = 1_000f, type = checkNotNull(objective.boss))
            boss.hp = boss.maxHp * 0.35f
            game.dimensionRun = game.dimensionRun.copy(visit = checkNotNull(game.dimensionRun.visit).copy(bossId = boss.id))
            assertEquals(0.65f, checkNotNull(game.dimensionTrialProjection()).progress, 1e-5f, "$objective: integrity lost")

            game.completeObjectiveForTesting()
            assertEquals(1f, checkNotNull(game.dimensionTrialProjection()).progress, "$objective: its fall fills the cell")
        }
    }

    /**
     * An open dimension reward publishes its layer (the overlay heads it by the layer's reward), in realspace after the
     * visit; finishing the choice returns the shared default.
     */
    @Test
    fun anOpenDimensionRewardPublishesItsLayerUntilTheChoiceFinishes() {
        listOf(WorldLayer.ZENITH, WorldLayer.NADIR, WorldLayer.ARCHIVE).forEach { layer ->
            // Every Legendary waits for a Rebirth rank: the Archive's trove is met at tier 10.
            val game = if (layer == WorldLayer.ARCHIVE) buildTestState(seed = 80_801, rebirth = 10).apply { elapsed = 200f; level = 60 }
                else game(seed = 80_811)
            game.dimensionRun = game.dimensionRun.copy(pendingRewards = listOf(DimensionRewardGrant(layer, completed = true)).toImmutableList())
            game.openPendingDimensionReward()
            assertEquals(GamePhase.CHOICE, game.phase, "$layer deals a hand")
            val open = game.dimensionProjection(null, null)
            assertEquals(layer, open.rewardLayer)
            assertEquals(WorldLayer.BASE, open.layer)
            assertEquals(open, game.toRenderModel().dimension)
            game.choose(0)
            assertNull(game.dimensionRewardLayer)
            assertSame(DimensionProjection.REALSPACE, game.dimensionProjection(open, null), "$layer: the choice finished")
        }
    }
}
