// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.DimensionNotices
import kinetickk.ball.content.api.DimensionObjective
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.RunBoonKind
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.nucleus.model.DimensionRewardGrant
import kinetickk.ball.gameplay.nucleus.model.HaloRing
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
import kinetickk.ball.gameplay.nucleus.reducer.GameReducer
import kinetickk.ball.gameplay.nucleus.reducer.GameReductionResult
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Objectives (the Ascent course, Harvest embers, boss duels) and their completion. */
class DimensionRewardsTest {
    private val step = MutableGameState.FIXED_STEP
    private val tuning = canonicalGameplayContent.dimensions.objectives
    private val policy = canonicalGameplayContent.dimensions

    /** No obstacles and no structures: placements land exactly where their rule puts them. */
    private val openWorld: GameplayContentSnapshot = canonicalGameplayContent.copy(
        obstacles = canonicalGameplayContent.obstacles.copy(meanPerChunk = 0f),
        structures = canonicalGameplayContent.structures.copy(enabled = false),
    )

    /** The open world whose Zenith keeps no ambient rings, so every ring in view is a course ring. */
    private val courseWorld: GameplayContentSnapshot = openWorld.withDimensions {
        copy(definitions = definitions.map { if (it.layer == WorldLayer.ZENITH) it.copy(physics = it.physics.copy(ambientHalos = 0)) else it }.toImmutableList())
    }

    private fun game(content: GameplayContentSnapshot = canonicalGameplayContent, seed: Int = 80_801) =
        MutableGameState(content, seed).apply {
            resize(1_280f, 800f)
            startRun()
            enemies.clear()
            elapsed = 200f
        }

    private fun MutableGameState.streams() =
        listOf(gameplayRandom.snapshot(), rewardRandom.snapshot(), spawnRandom.snapshot(), siteRandom.snapshot())

    private fun MutableGameState.visit() = checkNotNull(dimensionRun.visit)

    private fun MutableGameState.courseRing(index: Int): HaloRing? = visit().halos.firstOrNull { it.courseIndex == index }

    /** The Core sweeps through ([x], [y]) along +x at [speedScale] × the pace, then the dimension steps once. */
    private fun MutableGameState.flyThrough(x: Float, y: Float, speedScale: Float = 1.2f) {
        velocityX = dimensionPace() * speedScale
        velocityY = 0f
        flyThroughForTesting(x, y)
        updateDimension(step)
    }

    private fun MutableGameState.queue(vararg grants: DimensionRewardGrant) {
        dimensionRun = dimensionRun.copy(pendingRewards = grants.toList().toImmutableList())
    }

    private fun MutableGameState.offer(vararg cards: ChoiceOption, layer: WorldLayer = WorldLayer.NADIR) {
        activeChoiceType = cards.first().type
        choices = cards.toList()
        dimensionRewardLayer = layer
        phase = GamePhase.CHOICE
    }

    private fun boonCard(id: Int): ChoiceOption {
        val boon = checkNotNull(canonicalGameplayContent.boon(id))
        return ChoiceOption(ChoiceType.BOON, boon.name, boon.description, boon.kind.name, boonId = id)
    }

    private fun angleBetween(a: Float, b: Float): Float {
        var difference = (a - b) % (2f * PI.toFloat())
        if (difference > PI) difference -= 2f * PI.toFloat()
        if (difference < -PI) difference += 2f * PI.toFloat()
        return abs(difference) * 180f / PI.toFloat()
    }

    // ---- Ascent ----

    @Test
    fun anAscentPlacesItsFirstTwoCourseRingsByItsTurnAndSpacingRules() {
        val state = game(openWorld).apply {
            velocityX = 0f
            velocityY = 0f
            lastAimDirectionX = 1f
            lastAimDirectionY = 0f
        }
        val entryX = state.coreX
        val entryY = state.coreY
        state.enterDimensionForTesting(WorldLayer.ZENITH, DimensionObjective.ASCENT)
        val first = assertNotNull(state.courseRing(0))
        val second = assertNotNull(state.courseRing(1))
        assertNull(state.courseRing(2), "only the next ring and the one after it show")
        assertEquals(2, state.visit().halos.size)
        assertEquals(tuning.ascentFirstDistance, hypot(first.x - entryX, first.y - entryY), 0.05f)
        val firstHeading = atan2(first.y - entryY, first.x - entryX)
        assertTrue(angleBetween(firstHeading, 0f) in tuning.ascentMinimumTurnDegrees - 0.01f..tuning.ascentMaximumTurnDegrees + 0.01f)
        val spacing = hypot(second.x - first.x, second.y - first.y)
        assertTrue(spacing in tuning.ascentMinimumSpacing - 0.05f..tuning.ascentMaximumSpacing + 0.05f, "spacing $spacing")
        val secondHeading = atan2(second.y - first.y, second.x - first.x)
        assertTrue(angleBetween(secondHeading, firstHeading) in tuning.ascentMinimumTurnDegrees - 0.01f..tuning.ascentMaximumTurnDegrees + 0.01f)
        assertEquals(second.x, state.visit().courseX)
        assertEquals(second.y, state.visit().courseY)

        // The course is a stateless hash of the run and the visit: the same seed lays the same rings, another seed others.
        val again = game(openWorld).apply { enterDimensionForTesting(WorldLayer.ZENITH, DimensionObjective.ASCENT) }
        assertEquals(state.visit().halos, again.visit().halos)
        val other = game(openWorld, seed = 80_802).apply { enterDimensionForTesting(WorldLayer.ZENITH, DimensionObjective.ASCENT) }
        assertNotEquals(state.visit().halos, other.visit().halos)
        // Other objectives place no course.
        val judgement = game(openWorld).apply { enterDimensionForTesting(WorldLayer.ZENITH, DimensionObjective.JUDGEMENT) }
        assertTrue(judgement.visit().halos.isEmpty())
    }

    @Test
    fun aCourseRingCountsOnlyInItsTurnAndAtPace() {
        val state = game(courseWorld).apply {
            enterDimensionForTesting(WorldLayer.ZENITH, DimensionObjective.ASCENT)
            hp = maxHp * 0.5f
        }
        val streams = state.streams()
        val first = checkNotNull(state.courseRing(0))
        val second = checkNotNull(state.courseRing(1))

        // Out of turn: ring 1 before ring 0 stays and counts nothing.
        state.flyThrough(second.x, second.y)
        assertEquals(0, state.visit().progress)
        assertNotNull(state.courseRing(1))
        assertEquals(state.maxHp * 0.5f, state.hp)
        // Below the pace the next ring stays too.
        state.flyThrough(first.x, first.y, speedScale = 0.8f)
        assertEquals(0, state.visit().progress)
        assertNotNull(state.courseRing(0))

        // At pace it counts, mends, leaves and brings ring 2 into view.
        state.takeSoundCues()
        state.flyThrough(first.x, first.y)
        assertEquals(1, state.visit().progress)
        assertNull(state.courseRing(0))
        assertNotNull(state.courseRing(1))
        assertNotNull(state.courseRing(2))
        assertEquals(state.maxHp * (0.5f + policy.definition(WorldLayer.ZENITH).physics.haloRepairShare), state.hp, 0.001f)
        assertTrue(GameplayAudioCue.PICKUP in state.takeSoundCues())
        assertEquals(streams, state.streams(), "the course draws nothing from the run streams")
        assertFalse(state.visit().completed)
    }

    @Test
    fun passingTheLastCourseRingAtPaceCompletesTheAscentAndOpensTheExitRift() {
        val state = game(courseWorld).apply { enterDimensionForTesting(WorldLayer.ZENITH, DimensionObjective.ASCENT) }
        for (index in 0 until tuning.ascentRings) {
            assertFalse(state.visit().completed, "ring $index")
            val ring = assertNotNull(state.courseRing(index), "ring $index is in view")
            assertTrue(state.visit().halos.count { it.courseIndex >= 0 } <= 2)
            state.takeSoundCues()
            state.flyThrough(ring.x, ring.y)
        }
        val visit = state.visit()
        assertTrue(visit.completed)
        assertEquals(tuning.ascentRings, visit.progress)
        assertTrue(visit.halos.none { it.courseIndex >= 0 })
        val exit = assertNotNull(visit.exitPortal)
        assertEquals(WorldLayer.BASE, exit.target)
        assertEquals(visit.endsAt, exit.expiresAt)
        // The Core flies along +x: the rift opens that far ahead of it.
        assertEquals(state.coreX + policy.exitPortalDistance, exit.x, 0.01f)
        assertEquals(state.coreY, exit.y, 0.01f)
        assertEquals(DimensionNotices.completed(DimensionObjective.ASCENT), state.message)
        assertTrue(GameplayAudioCue.LEVEL_UP in state.takeSoundCues())

        // Flying through it returns with the full reward.
        state.flyThrough(exit.x, exit.y)
        assertEquals(WorldLayer.BASE, state.worldLayer)
        assertEquals(immutableListOf(DimensionRewardGrant(WorldLayer.ZENITH, completed = true)), state.dimensionRun.pendingRewards)
    }

    // ---- Boss objectives ----

    @Test
    fun aBossObjectiveSpawnsItsBossThroughAnEncounterOnceItsDelayRunsOut() {
        val cases = listOf(
            Triple(WorldLayer.ZENITH, DimensionObjective.JUDGEMENT, tuning.bossDelaySeconds),
            Triple(WorldLayer.NADIR, DimensionObjective.PYRE, tuning.bossDelaySeconds),
            Triple(WorldLayer.ARCHIVE, DimensionObjective.ANTECEDENT, tuning.antecedentDelaySeconds),
        )
        for ((layer, objective, delay) in cases) {
            val state = game(openWorld).apply {
                velocityX = dimensionPace() * 1.2f
                velocityY = 0f
                enterDimensionForTesting(layer, objective)
            }
            val streams = state.streams()
            val type = checkNotNull(objective.boss)
            state.elapsed = 200f + delay - 0.01f
            state.updateDimension(step)
            assertTrue(state.enemies.none { it.type == type }, "$objective: not before its delay")
            state.elapsed = 200f + delay
            state.updateDimension(step)
            val boss = state.enemies.single { it.type == type }
            val visit = state.visit()
            assertEquals(boss.id, visit.bossId, "$objective records its boss")
            assertEquals(dimensionEncounterTag(visit), boss.ownerTag)
            assertNotNull(state.encounter(boss.ownerTag))
            assertEquals(state.coreX + tuning.bossDistance, boss.x, 0.01f, "$objective arrives ahead of the Core")
            assertEquals(state.coreY, boss.y, 0.01f)
            assertEquals(streams, state.streams(), "$objective: the arrival draws nothing from the run streams")
            // Once it arrived it is not spawned again.
            state.updateDimension(step)
            assertEquals(1, state.enemies.count { it.type == type })
        }
    }

    @Test
    fun killingTheObjectiveBossCompletesTheVisitAndLeavingFirstRemovesItWithoutCredit() {
        val state = game(openWorld).apply { enterDimensionForTesting(WorldLayer.ZENITH, DimensionObjective.JUDGEMENT) }
        state.elapsed = 202f
        state.updateDimension(step)
        val boss = state.enemies.single { it.type == EnemyType.THRONE }
        val tag = boss.ownerTag
        state.onEnemyKilled(boss)
        assertTrue(state.visit().completed)
        assertEquals(1, state.visit().progress)
        assertNotNull(state.visit().exitPortal)
        assertEquals(DimensionNotices.completed(DimensionObjective.JUDGEMENT), state.message)
        assertNull(state.encounter(tag), "the duel's encounter ends with its boss")

        // A boss still alive when the visit ends stays behind: no kill, no completion, the smaller reward.
        val fled = game(openWorld).apply { enterDimensionForTesting(WorldLayer.NADIR, DimensionObjective.PYRE) }
        fled.elapsed = 202f
        fled.updateDimension(step)
        assertTrue(fled.enemies.any { it.type == EnemyType.PYRE_HERALD })
        val kills = fled.kills
        fled.skipToVisitEndForTesting()
        fled.updateDimension(step)
        assertEquals(WorldLayer.BASE, fled.worldLayer)
        assertTrue(fled.enemies.none { it.type == EnemyType.PYRE_HERALD })
        assertTrue(fled.encounters.isEmpty())
        assertEquals(kills, fled.kills)
        assertEquals(immutableListOf(DimensionRewardGrant(WorldLayer.NADIR, completed = false)), fled.dimensionRun.pendingRewards)

        // Another enemy's death completes nothing.
        val other = game(openWorld).apply { enterDimensionForTesting(WorldLayer.ZENITH, DimensionObjective.JUDGEMENT) }
        other.killEnemyForTesting(EnemyType.SERAPH)
        assertFalse(other.visit().completed)
    }

    @Test
    fun aFullArenaMakesRoomForTheObjectiveBoss() {
        val state = game(openWorld).apply { enterDimensionForTesting(WorldLayer.ZENITH, DimensionObjective.JUDGEMENT) }
        val cap = state.content.rebirth.maxActiveEnemies
        repeat(cap) { index -> state.addEnemyForTesting(-2_000f - index * 40f, 1_500f) }
        assertEquals(cap, state.enemies.size)
        state.elapsed = 202f
        state.updateDimension(step)
        assertEquals(cap, state.enemies.size, "one ordinary enemy made room")
        assertEquals(1, state.enemies.count { it.type == EnemyType.THRONE })
        assertTrue(state.visit().bossId >= 0)
    }

    // ---- Harvest ----

    @Test
    fun embersDropOnlyFromBurningKillsDuringAnOpenHarvest() {
        val state = game(openWorld).apply { enterDimensionForTesting(WorldLayer.NADIR, DimensionObjective.HARVEST) }
        state.killEnemyForTesting(EnemyType.IMP, x = 300f, y = 40f)
        assertEquals(1, state.visit().embers.size, "a Nadir native burns")
        val ember = state.visit().embers.single()
        assertEquals(300f, ember.x)
        assertEquals(40f, ember.y)
        assertEquals(200f + tuning.emberLifeSeconds, ember.expiresAt)
        state.killEnemyForTesting(EnemyType.DRIFTER, x = 500f, y = 0f)
        assertEquals(1, state.visit().embers.size, "a Drifter without Thermal leaves nothing")
        val thermal = state.addEnemyForTesting(600f, 0f, hp = 1f)
        thermal.status[StatusCell.STACKS + StatusElement.THERMAL.ordinal] = 1f
        state.onEnemyKilled(thermal)
        assertEquals(2, state.visit().embers.size, "an enemy carrying Thermal burns")

        // A Pyre duel leaves no embers, nor does a completed Harvest.
        val pyre = game(openWorld).apply { enterDimensionForTesting(WorldLayer.NADIR, DimensionObjective.PYRE) }
        pyre.killEnemyForTesting(EnemyType.IMP)
        assertTrue(pyre.visit().embers.isEmpty())
        state.completeObjective()
        state.killEnemyForTesting(EnemyType.IMP)
        assertEquals(2, state.visit().embers.size)
    }

    @Test
    fun embersAcceptTwentyFourAndEvictTheOldestOnTheNext() {
        val state = game(openWorld).apply { enterDimensionForTesting(WorldLayer.NADIR, DimensionObjective.HARVEST) }
        repeat(ContentBounds.MAX_EMBERS) { index -> state.dropEmber(1_000f + index * 10f, 900f) }
        val full = state.visit().embers
        assertEquals(ContentBounds.MAX_EMBERS, full.size)
        state.dropEmber(5_000f, 900f)
        val next = state.visit().embers
        assertEquals(ContentBounds.MAX_EMBERS, next.size)
        assertEquals(full.drop(1), next.dropLast(1), "the oldest went out, the rest kept their order")
        assertEquals(5_000f, next.last().x)
        assertEquals(1_000f, full.first().x, "the committed list was not touched")
    }

    @Test
    fun embersAreCollectedBySweptFlightGoOutAtTheirLifeAndTheGoalCompletesTheHarvest() {
        val state = game(openWorld).apply { enterDimensionForTesting(WorldLayer.NADIR, DimensionObjective.HARVEST) }
        state.dropEmber(400f, 0f)
        state.dropEmber(-900f, 700f)
        state.takeSoundCues()
        state.flyThrough(400f, 0f)
        assertEquals(1, state.visit().progress)
        assertEquals(1, state.visit().embers.size)
        assertTrue(GameplayAudioCue.PICKUP in state.takeSoundCues())
        // The other one goes out unclaimed at the end of its life.
        state.elapsed = 200f + tuning.emberLifeSeconds - 0.01f
        state.updateDimension(step)
        assertEquals(1, state.visit().embers.size)
        state.elapsed = 200f + tuning.emberLifeSeconds
        state.updateDimension(step)
        assertTrue(state.visit().embers.isEmpty())
        assertEquals(1, state.visit().progress)

        // The goal's last ember completes the Harvest.
        state.dimensionRun = state.dimensionRun.copy(visit = state.visit().copy(progress = tuning.harvestEmbers - 1))
        state.dropEmber(700f, 0f)
        state.flyThrough(700f, 0f)
        assertTrue(state.visit().completed)
        assertEquals(tuning.harvestEmbers, state.visit().progress)
        assertNotNull(state.visit().exitPortal)
        assertEquals(DimensionNotices.completed(DimensionObjective.HARVEST), state.message)
    }
}
