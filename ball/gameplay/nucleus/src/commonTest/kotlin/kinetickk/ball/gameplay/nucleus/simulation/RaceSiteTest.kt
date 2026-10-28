// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.RacePolicy
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.SiteNotice
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.gameplay.nucleus.model.SitePoint
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.model.windowSiteId
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.immutableListOf
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.nextUp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The gate race: a hashed course, a par from the steady model, gates in order, medals by the clock. */
class RaceSiteTest {
    private fun game(content: GameplayContentSnapshot = canonicalGameplayContent, seed: Int = 40_417) =
        MutableGameState(content, seed).apply {
            startRun()
            enemies.clear()
            clearObstaclesForTesting()
        }

    /** Starts the race of window [offerId]'s [side] at ([x], [y]) and returns the running site. */
    private fun MutableGameState.startRace(offerId: Int = 0, side: Int = 0, x: Float = 0f, y: Float = 0f): SiteState {
        activateWindowSite(SiteState(windowSiteId(offerId, side), SiteKind.GATE_RACE, x, y, 85f))
        return sites.single()
    }

    /** One step that sweeps the Core from ([fromX], [fromY]) to ([toX], [toY]). */
    private fun MutableGameState.flyStep(fromX: Float, fromY: Float, toX: Float, toY: Float, delta: Float = 0.01f) {
        previousCoreX = fromX
        previousCoreY = fromY
        coreX = toX
        coreY = toY
        updateSites(delta)
    }

    /** One step straight through gate [index] of [race] (from 40 u before it to 40 u past it, along x). */
    private fun MutableGameState.passGate(race: SiteState, index: Int, delta: Float = 0.01f) {
        val gateX = race.x + race.course[index].dx
        val gateY = race.y + race.course[index].dy
        flyStep(gateX - 40f, gateY, gateX + 40f, gateY, delta)
    }

    private fun MutableGameState.matter(): Double = runMatter + matterFraction.toDouble()

    private fun MutableGameState.hasDiscovered(id: WorldDiscovery): Boolean =
        worldDiscoveryMask and id.bit != 0 && pendingWorldDiscoveryMask and id.bit != 0

    @Test
    fun theCourseIsAHashOfTheRunSeedAndTheWindowSideAndDrawsFromNoStream() {
        val first = game()
        val reward = first.rewardRandom.snapshot()
        val spawn = first.spawnRandom.snapshot()
        val gameplay = first.gameplayRandom.snapshot()
        val siteStream = first.siteRandom.snapshot()
        val race = first.startRace(offerId = 2, side = 1)
        assertEquals(reward, first.rewardRandom.snapshot())
        assertEquals(spawn, first.spawnRandom.snapshot())
        assertEquals(gameplay, first.gameplayRandom.snapshot())
        assertEquals(siteStream, first.siteRandom.snapshot())

        // The same seed, window and side lay the same course whenever the race starts.
        val again = game()
        again.elapsed = 300f
        again.nextSiteWindowIndex = 4
        val same = again.startRace(offerId = 2, side = 1)
        assertEquals(race.course, same.course)
        assertEquals(race.par, same.par)
        assertEquals(race.limit, same.limit)

        // Another side, window or run seed lays another course.
        assertNotEquals(race.course, game().startRace(offerId = 2, side = 0).course)
        assertNotEquals(race.course, game().startRace(offerId = 3, side = 1).course)
        assertNotEquals(race.course, game(seed = 40_418).startRace(offerId = 2, side = 1).course)
    }

    @Test
    fun everyCourseHasFiveToSevenGatesSweptAroundTheStartWithinItsBound() {
        val policy = canonicalGameplayContent.sites.race
        val bound = policy.maxRadius + policy.nudgeStep * policy.nudgeAttempts
        assertEquals(1_950f, bound)
        val sizes = mutableSetOf<Int>()
        for (seed in 0 until 40) for (offerId in 0 until 6) for (side in 0..1) {
            val game = game(seed = seed)
            game.velocityX = 600f
            val race = game.startRace(offerId, side, x = 120f, y = -80f)
            sizes += race.course.size
            assertTrue(race.course.size in policy.minGates..policy.maxGates, "seed $seed window $offerId side $side")
            for (gate in race.course) {
                val distance = length(gate.dx, gate.dy)
                assertTrue(distance >= policy.minRadius - 0.5f && distance <= bound + 0.5f, "gate at $distance u")
            }
            // The first gate lies ahead of the flight; each next gate turns the same way by about a sweep.
            val sweep = 2f * kotlin.math.PI.toFloat() * policy.loopShare / race.course.size
            assertTrue(kotlin.math.abs(angleOf(race.course[0])) <= policy.angleJitter + 1e-4f)
            val turn = kotlin.math.sign(angleBetween(race.course[0], race.course[1]))
            for (index in 1 until race.course.size) {
                val step = angleBetween(race.course[index - 1], race.course[index])
                assertEquals(turn, kotlin.math.sign(step))
                assertEquals(sweep, kotlin.math.abs(step), 2f * policy.angleJitter + 1e-4f)
            }
        }
        assertEquals(setOf(5, 6, 7), sizes)
    }

    private fun angleOf(point: SitePoint): Float = atan2(point.dy, point.dx)

    private fun angleBetween(from: SitePoint, to: SitePoint): Float {
        var turn = angleOf(to) - angleOf(from)
        while (turn > kotlin.math.PI) turn -= 2f * kotlin.math.PI.toFloat()
        while (turn < -kotlin.math.PI) turn += 2f * kotlin.math.PI.toFloat()
        return turn
    }

    @Test
    fun aBlockedGateMovesOutwardFromTheStartUntilClearAndTheOthersStay() {
        val policy = canonicalGameplayContent.sites.race
        val open = game().startRace(offerId = 1)
        val blocked = game()
        val target = open.course[2]
        blocked.placeObstaclesForTesting(testObstacle(target.dx, target.dy, radius = 120f))
        val nudged = blocked.startRace(offerId = 1)

        assertEquals(open.course.size, nudged.course.size)
        for (index in open.course.indices) if (index != 2) assertEquals(open.course[index], nudged.course[index])
        val moved = nudged.course[2]
        assertFalse(blocked.isSpawnPointBlocked(moved.dx, moved.dy, policy.gateClearance))
        assertTrue(length(moved.dx, moved.dy) > length(target.dx, target.dy) + policy.nudgeStep - 1f, "moved outward")
        assertEquals(angleOf(target), angleOf(moved), 1e-3f) // along its ray from the start
        assertNotEquals(open.par, nudged.par)
    }

    @Test
    fun theParIsTheCourseAtTheSteadySpeedPlusATurnAllowanceAndTheLimitFollowsIt() {
        for (screen in listOf(1_280f to 800f, 412f to 915f)) {
            val game = game()
            game.screenWidth = screen.first
            game.screenHeight = screen.second
            val race = game.startRace(offerId = 4, side = 1)
            val policy = game.content.sites.race
            var path = 0f
            var fromX = 0f
            var fromY = 0f
            for (gate in race.course) {
                path += length(gate.dx - fromX, gate.dy - fromY)
                fromX = gate.dx
                fromY = gate.dy
            }
            val par = path / game.steadyCursorSpeed(policy.parReach) + policy.turnAllowanceSeconds * (race.course.size - 1)
            assertEquals(par, race.par, "par on ${screen.first}x${screen.second}")
            val limit = min(max(par * policy.limitFactor, policy.minimumLimit), game.content.sites.windows.trialDuration)
            assertEquals(limit, race.limit)
            assertEquals(limit, race.remaining)
            assertEquals(0, race.step)
            assertEquals(0f, race.clock)
            assertTrue(race.active)
            assertEquals(game.content.sites.definition(SiteKind.GATE_RACE).instruction, game.message)
        }

        // A short course is held to the minimum limit; a generous factor stops at the window's trial.
        val short = game(canonicalGameplayContent.withRace(RacePolicy(minRadius = 600f, maxRadius = 600f))).startRace()
        assertTrue(short.par * 2.4f < 12f)
        assertEquals(12f, short.limit)
        val generous = game(
            canonicalGameplayContent.withRace(RacePolicy(limitFactor = 20f)),
        ).startRace()
        assertEquals(canonicalGameplayContent.sites.windows.trialDuration, generous.limit)
    }

    @Test
    fun onlyTheNextGateCountsAndEachPassChimes() {
        val game = game()
        val race = game.startRace()
        game.takeSoundCues()
        game.passGate(race, 1)
        assertEquals(0, game.sites.single().step, "gate 2 before gate 1 does nothing")
        assertTrue(GameplayAudioCue.PICKUP !in game.takeSoundCues())
        game.passGate(race, 0)
        assertEquals(1, game.sites.single().step)
        assertTrue(GameplayAudioCue.PICKUP in game.takeSoundCues())
        game.passGate(race, 0)
        assertEquals(1, game.sites.single().step, "a passed gate never counts again")
        game.passGate(race, 1)
        assertEquals(2, game.sites.single().step)
        assertEquals(0.04f, game.sites.single().clock, 1e-6f)
        assertEquals(race.limit - 0.04f, game.sites.single().remaining, 1e-5f)
        // A swept pass grazing the gate's radius counts; one just outside it does not.
        val radius = game.content.sites.race.gateRadius
        val gateX = race.course[2].dx
        val gateY = race.course[2].dy
        game.flyStep(gateX - 40f, gateY + radius + 1f, gateX + 40f, gateY + radius + 1f)
        assertEquals(2, game.sites.single().step)
        game.flyStep(gateX - 40f, gateY + radius - 1f, gateX + 40f, gateY + radius - 1f)
        assertEquals(3, game.sites.single().step)
    }

    /** The running race of [game] with its last gate next and [clock] seconds flown. */
    private fun lastGateAt(game: MutableGameState, clock: Float): SiteState {
        val race = game.startRace()
        val running = race.copy(step = race.course.size - 1, clock = clock, remaining = 5f)
        game.sites = listOf(running)
        return running
    }

    @Test
    fun aGoldFinishGrantsTheGoldRewardAtTheLastGate() {
        val game = game()
        val race = lastGateAt(game, clock = game.startRace().par - 0.02f)
        val matter = game.matter()
        game.passGate(race, race.course.lastIndex)

        assertTrue(game.sites.isEmpty())
        assertEquals(SiteNotice.RACE_GOLD.english, game.message)
        assertEquals(listOf(DirectedReward.ITEM_AND_REPAIR), game.pendingDirectedRewards)
        val last = race.course.last()
        val key = game.pickups.single { it.type == PickupType.KEY }
        assertEquals(race.x + last.dx, key.x)
        assertEquals(race.y + last.dy, key.y)
        assertEquals(matter + 15.0 * game.matterGain, game.matter(), 1e-3)
        assertEquals(0, game.pendingLevelChoices)
        assertTrue(game.hasDiscovered(WorldDiscovery.GATE_RACE))
    }

    @Test
    fun silverBronzeAndThePlainFinishFollowTheClock() {
        fun finish(clockShare: Float, extra: Float): MutableGameState {
            val game = game()
            val par = game.startRace().par
            val race = lastGateAt(game, clock = par * clockShare + extra)
            game.passGate(race, race.course.lastIndex)
            assertTrue(game.sites.isEmpty())
            assertTrue(game.hasDiscovered(WorldDiscovery.GATE_RACE), "any finish discovers the race")
            return game
        }
        val silver = finish(1.3f, -0.02f)
        assertEquals(SiteNotice.RACE_SILVER.english, silver.message)
        assertEquals(listOf(DirectedReward.ITEM_AND_REPAIR), silver.pendingDirectedRewards)
        assertTrue(silver.pickups.none { it.type == PickupType.KEY })

        val bronze = finish(1.3f, 0.01f)
        assertEquals(SiteNotice.RACE_BRONZE.english, bronze.message)
        assertTrue(bronze.pendingDirectedRewards.isEmpty())
        assertEquals(1, bronze.pendingLevelChoices)

        val plain = game()
        val matter = plain.matter()
        val par = plain.startRace().par
        val race = lastGateAt(plain, clock = par * 1.75f + 0.01f)
        plain.passGate(race, race.course.lastIndex)
        assertEquals(SiteNotice.RACE_FINISHED.english, plain.message)
        assertTrue(plain.pendingDirectedRewards.isEmpty())
        assertEquals(0, plain.pendingLevelChoices)
        assertEquals(matter + 10.0 * plain.matterGain, plain.matter(), 1e-3)
        assertTrue(plain.hasDiscovered(WorldDiscovery.GATE_RACE))
    }

    @Test
    fun theMedalThresholdsIncludeTheirOwnTime() {
        val game = game()
        val race = game.startRace()
        val policy = game.content.sites.race
        fun medal(clock: Float) = game.raceMedal(race.copy(clock = clock))
        assertEquals(3, medal(race.par * policy.goldFactor))
        assertEquals(2, medal((race.par * policy.goldFactor).nextUp()))
        assertEquals(2, medal(race.par * policy.silverFactor))
        assertEquals(1, medal((race.par * policy.silverFactor).nextUp()))
        assertEquals(1, medal(race.par * policy.bronzeFactor))
        assertEquals(0, medal((race.par * policy.bronzeFactor).nextUp()))
        assertEquals(0, game.raceMedal(race.copy(par = 0f)), "no par, no medal")
    }

    @Test
    fun aFullDirectedQueueSalvagesTheGoldDirectionAndKeepsTheRest() {
        val game = game()
        val bound = game.content.sites.maxPendingDirectedRewards
        game.pendingDirectedRewards = List(bound) { DirectedReward.WEAPON }
        val race = lastGateAt(game, clock = 0f)
        val matter = game.matter()
        game.passGate(race, race.course.lastIndex)
        assertEquals(bound, game.pendingDirectedRewards.size)
        assertEquals(SiteNotice.REWARD_SALVAGED.english, game.message)
        assertEquals(matter + (8.0 + 15.0) * game.matterGain, game.matter(), 1e-3)
        assertEquals(1, game.pickups.count { it.type == PickupType.KEY })
    }

    @Test
    fun aTimeoutLosesTheRaceAndGrantsNothing() {
        val game = game()
        val race = game.startRace()
        val matter = game.matter()
        game.flyStep(0f, 0f, 0f, 0f, delta = race.limit - 0.01f)
        assertEquals(1, game.sites.size, "still running just before the limit")
        game.flyStep(0f, 0f, 0f, 0f, delta = 0.02f)
        assertTrue(game.sites.isEmpty())
        assertEquals(SiteNotice.RACE_LOST.english, game.message)
        assertTrue(game.pendingDirectedRewards.isEmpty())
        assertEquals(0, game.pendingLevelChoices)
        assertTrue(game.pickups.isEmpty())
        assertEquals(matter, game.matter())
        assertFalse(game.worldDiscoveryMask and WorldDiscovery.GATE_RACE.bit != 0)
    }

    @Test
    fun strayingPastTheLeashFromTheNextGateLosesTheRace() {
        val game = game()
        val course = immutableListOf(SitePoint(1_000f, 0f), SitePoint(1_500f, 900f), SitePoint(500f, 1_500f),
            SitePoint(-500f, 1_400f), SitePoint(-1_100f, 600f))
        val race = SiteState(windowSiteId(0, 0), SiteKind.GATE_RACE, 0f, 0f, 85f, active = true, remaining = 20f,
            course = course, par = 6f, limit = 20f)
        game.sites = listOf(race)
        game.flyStep(1_000f, RACE_LEASH, 1_000f, RACE_LEASH)
        assertEquals(1, game.sites.size, "exactly the leash keeps the race")
        game.flyStep(1_000f, RACE_LEASH + 1f, 1_000f, RACE_LEASH + 1f)
        assertTrue(game.sites.isEmpty())
        assertEquals(SiteNotice.RACE_LOST.english, game.message)

        // The leash follows the next gate, not the start.
        game.sites = listOf(race.copy(step = 3))
        game.flyStep(-500f, 1_400f + RACE_LEASH - 1f, -500f, 1_400f + RACE_LEASH - 1f)
        assertEquals(1, game.sites.size)
        assertTrue(length(-500f, 1_400f + RACE_LEASH - 1f) > RACE_LEASH)
    }

    @Test
    fun aRebaseShiftsTheStartAndKeepsTheCourse() {
        val game = game()
        val race = game.startRace(x = 300_000f, y = 20f)
        game.coreX = 300_000f
        game.coreY = 0f
        game.rebaseWorldIfNeeded()
        val shifted = game.sites.single()
        assertEquals(0f, shifted.x)
        assertEquals(20f, shifted.y)
        assertEquals(race.course, shifted.course)
        game.passGate(shifted, 0)
        assertEquals(1, game.sites.single().step)
    }

    @Test
    fun aWindowCanOfferTheRaceAndEnteringItStartsTheOnlyEngagement() {
        assertEquals(1.4f, canonicalGameplayContent.sites.definition(SiteKind.GATE_RACE).windowWeight)
        var offered: Pair<MutableGameState, SiteState>? = null
        for (seed in 1..200) {
            val game = game(seed = seed)
            val race = game.offerWindowForTesting(0).firstOrNull { it.kind == SiteKind.GATE_RACE } ?: continue
            offered = game to race
            break
        }
        val (game, race) = assertNotNull(offered, "the shipped weights offer a race within 200 seeds")
        game.velocityX = 500f
        game.flyStep(race.x - 50f, race.y, race.x, race.y)
        val running = game.sites.single()
        assertTrue(running.active)
        assertEquals(race.id, running.id)
        assertTrue(running.course.size >= 5)
        assertTrue(game.engagementActive())
        assertEquals(game.content.sites.definition(SiteKind.GATE_RACE).instruction, game.message)
    }
}

private fun GameplayContentSnapshot.withRace(race: RacePolicy): GameplayContentSnapshot = copy(sites = sites.copy(race = race))
