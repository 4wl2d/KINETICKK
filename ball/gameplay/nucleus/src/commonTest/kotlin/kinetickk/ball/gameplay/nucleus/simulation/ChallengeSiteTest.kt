// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.SiteFamily
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.SiteNotice
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.model.windowSiteId
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The five challenges: each counts down its definition's duration, keeps the Core within 2,400 u of its centre (a
 * slalom: of its next pole) and grants its directed reward only when its rule is met in time.
 */
class ChallengeSiteTest {
    private val policy = canonicalGameplayContent.sites.challenges

    /**
     * The Ram rite's rules on a rite that needs six Dash kills of its eight foes (the shipped rite needs [policy]'s
     * `ramRequired`), so a count can climb past two and wasted kills have room before the rite fails.
     */
    private val sixOfEightRite = canonicalGameplayContent.copy(
        sites = canonicalGameplayContent.sites.copy(challenges = policy.copy(ramRequired = 6)),
    )

    private fun game(seed: Int = 40_417, content: GameplayContentSnapshot = canonicalGameplayContent) = MutableGameState(content, seed).apply {
        startRun()
        enemies.clear()
        projectiles.clear()
        clearObstaclesForTesting()
    }

    /** Starts the [kind] of window [offerId]'s [side] at ([x], [y]) and returns the running site. */
    private fun MutableGameState.startChallenge(kind: SiteKind, offerId: Int = 0, side: Int = 0, x: Float = 0f, y: Float = 0f): SiteState {
        activateWindowSite(SiteState(windowSiteId(offerId, side), kind, x, y, 85f))
        return sites.single()
    }

    /** One step with the Core held at ([x], [y]) at velocity ([vx], [vy]). */
    private fun MutableGameState.hold(x: Float, y: Float, vx: Float = 0f, vy: Float = 0f, delta: Float = 0.1f) {
        previousCoreX = x
        previousCoreY = y
        coreX = x
        coreY = y
        velocityX = vx
        velocityY = vy
        updateSites(delta)
    }

    /** One step that sweeps the Core from ([fromX], [fromY]) to ([toX], [toY]). */
    private fun MutableGameState.flyStep(fromX: Float, fromY: Float, toX: Float, toY: Float, delta: Float = 0.01f) {
        previousCoreX = fromX
        previousCoreY = fromY
        coreX = toX
        coreY = toY
        updateSites(delta)
    }

    private fun MutableGameState.hasDiscoveredTrials(): Boolean =
        worldDiscoveryMask and WorldDiscovery.TRIALS.bit != 0 && pendingWorldDiscoveryMask and WorldDiscovery.TRIALS.bit != 0

    private fun MutableGameState.assertGrantedNothing() {
        assertTrue(sites.isEmpty())
        assertEquals(SiteNotice.CHALLENGE_FAILED.english, message)
        assertTrue(pendingDirectedRewards.isEmpty())
        assertFalse(worldDiscoveryMask and WorldDiscovery.TRIALS.bit != 0)
    }

    private fun MutableGameState.assertCompleted(reward: DirectedReward) {
        assertTrue(sites.isEmpty())
        assertEquals(SiteNotice.CHALLENGE_COMPLETE.english, message)
        assertEquals(listOf(reward), pendingDirectedRewards)
        assertTrue(hasDiscoveredTrials())
    }

    @Test
    fun everyChallengeCountsDownItsOwnDurationAndShowsItsRule() {
        for (kind in SiteKind.entries.filter { it.family == SiteFamily.CHALLENGE }) {
            val game = game()
            val definition = game.content.sites.definition(kind)
            val trial = game.startChallenge(kind)
            assertTrue(trial.active)
            assertEquals(definition.duration, trial.remaining, "$kind")
            assertEquals(0f, trial.clock)
            assertEquals(definition.instruction, game.message)
            assertTrue(game.engagementActive())

            // Running out of time grants nothing; a hundredth before the end it still runs (held still, outside every
            // ring, the Core earns nothing meanwhile).
            game.hold(1_000f, 0f, delta = definition.duration - 0.01f)
            assertEquals(1, game.sites.size, "$kind runs until its time is up")
            game.hold(1_000f, 0f, delta = 0.02f)
            game.assertGrantedNothing()
        }
    }

    @Test
    fun strayingPastTheLeashFromTheCentreLosesARingChallenge() {
        for (kind in listOf(SiteKind.BRAKELESS_TRIAL, SiteKind.UNTOUCHED_TRIAL, SiteKind.VELOCITY_TRIAL, SiteKind.RAM_TRIAL)) {
            val game = game()
            game.startChallenge(kind, x = 100f, y = -50f)
            game.hold(100f, -50f + CHALLENGE_LEASH)
            assertEquals(1, game.sites.size, "$kind: exactly the leash keeps the trial")
            game.hold(100f, -50f + CHALLENGE_LEASH + 1f)
            game.assertGrantedNothing()
        }
    }

    @Test
    fun brakingFailsTheBrakelessRunOnlyAfterItsGrace() {
        val game = game()
        game.startChallenge(SiteKind.BRAKELESS_TRIAL)
        game.braking = true
        game.hold(300f, 0f, vx = 600f, delta = policy.brakelessGrace)
        assertEquals(1, game.sites.size, "braking within the grace is allowed")
        game.braking = false
        game.hold(300f, 0f, vx = 600f, delta = 0.01f)
        game.braking = true
        game.hold(300f, 0f, vx = 600f, delta = 0.01f)
        game.assertGrantedNothing()
    }

    @Test
    fun theBrakelessRunEarnsOnlyInsideItsShrinkingRingAtSpeed() {
        val game = game()
        game.startChallenge(SiteKind.BRAKELESS_TRIAL)
        game.hold(800f, 0f, vx = policy.brakelessMinSpeed, delta = 0.1f)
        assertEquals(0.1f, game.sites.single().meter, 1e-6f)
        game.hold(800f, 0f, vx = policy.brakelessMinSpeed - 1f, delta = 0.1f)
        assertEquals(0.1f, game.sites.single().meter, 1e-6f, "too slow earns nothing")

        // Ten seconds in, the ring has shrunk from 900 to 672.75 u: just outside earns nothing, just inside earns.
        game.sites = listOf(game.sites.single().copy(clock = 10f))
        val ring = policy.brakelessRing(10.1f)
        assertEquals(672.75f, ring, 1e-3f)
        game.hold(0f, ring + 2f, vy = 900f, delta = 0.1f)
        assertEquals(0.1f, game.sites.single().meter, 1e-6f)
        game.hold(0f, policy.brakelessRing(10.2f) - 2f, vy = 900f, delta = 0.1f)
        assertEquals(0.2f, game.sites.single().meter, 1e-6f)

        // The twelfth second completes it.
        game.sites = listOf(game.sites.single().copy(meter = policy.brakelessRequired - 0.05f))
        game.hold(0f, 400f, vx = 900f, delta = 0.1f)
        game.assertCompleted(DirectedReward.ITEM_AND_REPAIR)
    }

    @Test
    fun theRamRiteSpawnsItsMarkedDriftersAsAnEncounterFromNoStream() {
        val game = game()
        val reward = game.rewardRandom.snapshot()
        val spawn = game.spawnRandom.snapshot()
        val gameplay = game.gameplayRandom.snapshot()
        val siteStream = game.siteRandom.snapshot()
        val rite = game.startChallenge(SiteKind.RAM_TRIAL, offerId = 3, side = 1, x = 200f, y = 100f)
        assertEquals(reward, game.rewardRandom.snapshot())
        assertEquals(spawn, game.spawnRandom.snapshot())
        assertEquals(gameplay, game.gameplayRandom.snapshot())
        assertEquals(siteStream, game.siteRandom.snapshot())

        val tag = siteEncounterTag(rite)
        assertEquals(encounterTag(EncounterOwner.SITE, windowSiteId(3, 1)), tag)
        assertNotNull(game.encounter(tag))
        assertEquals(policy.ramFoes, rite.foeIds.size)
        assertEquals(game.enemies.map { it.id }, rite.foeIds)
        val integrity = game.spawnIntegrity(EnemyType.DRIFTER) * policy.ramFoeHealthShare
        game.enemies.forEachIndexed { index, foe ->
            assertEquals(EnemyType.DRIFTER, foe.type)
            assertEquals(tag, foe.ownerTag)
            assertEquals(integrity, foe.hp, 1e-4f)
            assertEquals(integrity, foe.maxHp, 1e-4f)
            val angle = TAU * index / policy.ramFoes
            assertEquals(200f + cos(angle) * policy.ramRingRadius, foe.x, 1e-3f)
            assertEquals(100f + sin(angle) * policy.ramRingRadius, foe.y, 1e-3f)
            assertTrue(game.isPlaceBound(foe))
        }
    }

    @Test
    fun ramTrialReservesEightSlotsAndRefusesTheNinth() {
        val cap = canonicalGameplayContent.rebirth.maxActiveEnemies
        val offer = SiteState(windowSiteId(0, 0), SiteKind.RAM_TRIAL, 0f, 0f, 85f)
        val other = SiteState(windowSiteId(0, 1), SiteKind.RESONANT_CIRCUIT, 400f, 0f, 85f)

        val room = game()
        repeat(cap - policy.ramFoes) { room.addEnemyForTesting(-900f, 40f * it) }
        room.sites = listOf(offer, other)
        room.flyStep(-50f, 0f, 0f, 0f)
        assertTrue(room.sites.single().active, "exactly eight free slots start the rite")
        assertEquals(cap, room.enemies.size)

        val full = game()
        repeat(cap - policy.ramFoes + 1) { full.addEnemyForTesting(-900f, 40f * it) }
        full.sites = listOf(offer, other)
        val before = full.enemies.map { it.id }
        full.flyStep(-50f, 0f, 0f, 0f)
        assertEquals(listOf(offer, other), full.sites, "a refused rite stays offered")
        assertEquals(before, full.enemies.map { it.id }, "nothing spawned and nothing evicted")
        assertNull(full.encounter(siteEncounterTag(offer)))
        assertFalse(full.engagementActive())
    }

    /** Kills [foe] with a blow of [source] and resolves the kill as the collision step does. */
    private fun MutableGameState.kill(foe: Enemy, source: HitSource) {
        dealHit(foe, foe.hp + 1_000f, source, canCrit = false)
        sweepKills()
    }

    @Test
    fun onlyDashKillsCountTowardTheRamRite() {
        val game = game(content = sixOfEightRite)
        val rite = game.startChallenge(SiteKind.RAM_TRIAL)
        val foes = game.enemies.toList()
        game.kill(foes[0], HitSource.DASH)
        assertEquals(1, game.sites.single().count, "a Dash strike counts")
        game.dashPhaseTime = 0.2f
        game.kill(foes[1], HitSource.IMPACT)
        assertEquals(2, game.sites.single().count, "an impact during the Dash counts")
        game.dashPhaseTime = 0f
        game.kill(foes[2], HitSource.IMPACT)
        assertEquals(2, game.sites.single().count, "an impact outside the Dash does not")
        game.kill(foes[3], HitSource.WEAPON)
        assertEquals(2, game.sites.single().count, "a weapon kill does not")
        // A kill outside the rite's foes never counts.
        val stranger = game.addEnemyForTesting(300f, 0f, hp = 5f)
        game.kill(stranger, HitSource.DASH)
        assertEquals(2, game.sites.single().count)

        // Two wasted kills still leave six possible: the rite runs on.
        game.hold(0f, 0f)
        assertEquals(rite.id, game.sites.single().id)
    }

    @Test
    fun wastedKillsFailTheRamRiteOnceTooFewFoesRemainAndTheSurvivorsStayAsOrdinaryEnemies() {
        val game = game(content = sixOfEightRite)
        val rite = game.startChallenge(SiteKind.RAM_TRIAL)
        val foes = game.enemies.toList()
        game.kill(foes[0], HitSource.WEAPON)
        game.kill(foes[1], HitSource.WEAPON)
        game.hold(0f, 0f)
        assertEquals(1, game.sites.size, "six foes left can still make six Dash kills")
        game.kill(foes[2], HitSource.WEAPON)
        game.hold(0f, 0f)
        game.assertGrantedNothing()
        assertNull(game.encounter(siteEncounterTag(rite)))
        assertEquals(foes.drop(3).map { it.id }, game.enemies.map { it.id }, "the survivors stay")
        assertTrue(game.enemies.all { it.ownerTag == 0L && !game.isPlaceBound(it) }, "as ordinary enemies")
    }

    @Test
    fun sixDashKillsCompleteTheRamRiteWithItsWeaponDirection() {
        val game = game()
        val rite = game.startChallenge(SiteKind.RAM_TRIAL)
        val foes = game.enemies.toList()
        foes.take(policy.ramRequired - 1).forEach { game.kill(it, HitSource.DASH) }
        game.hold(0f, 0f)
        assertEquals(policy.ramRequired - 1, game.sites.single().count)
        game.kill(foes[policy.ramRequired - 1], HitSource.DASH)
        game.hold(0f, 0f)
        game.assertCompleted(DirectedReward.WEAPON)
        assertNull(game.encounter(siteEncounterTag(rite)))
        assertEquals(policy.ramFoes - policy.ramRequired, game.enemies.size)
        assertTrue(game.enemies.all { it.ownerTag == 0L })
    }

    @Test
    fun leavingTheRamRiteTurnsItsFoesIntoOrdinaryEnemies() {
        val game = game()
        val rite = game.startChallenge(SiteKind.RAM_TRIAL)
        game.hold(0f, CHALLENGE_LEASH + 1f)
        game.assertGrantedNothing()
        // The encounter would have despawned them at its own abandon distance; the site released them first.
        game.updateEnemies(0.01f)
        assertEquals(rite.foeIds, game.enemies.map { it.id })
        assertTrue(game.enemies.all { it.ownerTag == 0L })
    }

    @Test
    fun theUntouchedTrialEarnsInsideItsRingAndFailsOnAShieldAbsorbedHit() {
        val game = game()
        game.damageTaken = 7.0
        val trial = game.startChallenge(SiteKind.UNTOUCHED_TRIAL)
        assertEquals(7.0, trial.hurtBaseline)
        assertEquals(policy.untouchedVolleyInterval, trial.volleyClock)
        game.hold(policy.untouchedRadius, 0f, delta = 0.1f)
        assertEquals(0.1f, game.sites.single().meter, 1e-6f)
        game.hold(policy.untouchedRadius + 1f, 0f, delta = 0.1f)
        assertEquals(0.1f, game.sites.single().meter, 1e-6f, "outside the ring earns nothing")

        game.shield = 40f
        game.maxShield = 40f
        val hp = game.hp
        game.takeDamage(10f)
        assertEquals(hp, game.hp, "the shield took the whole hit")
        assertTrue(game.damageAbsorbed > 0.0)
        game.hold(100f, 0f, delta = 0.01f)
        game.assertGrantedNothing()
    }

    @Test
    fun theUntouchedVolleyWarnsAtTheCoreThenFiresThreeLanesFromTheCentre() {
        val game = game()
        game.startChallenge(SiteKind.UNTOUCHED_TRIAL, x = 50f, y = 20f)
        game.hold(350f, 20f, delta = policy.untouchedVolleyInterval - 0.1f)
        assertEquals(0f, game.sites.single().warningRemaining, "no warning before the interval")
        game.hold(350f, 20f, delta = 0.2f)
        val warned = game.sites.single()
        assertEquals(policy.untouchedWarning, warned.warningRemaining)
        assertEquals(0f, warned.volleyAngle, 1e-6f)
        assertEquals(350f, warned.aimX)
        assertEquals(20f, warned.aimY)
        assertTrue(game.projectiles.none { it.hostile })

        // The Core moves on; the shots still fly along the warned aim once the warning runs out.
        game.hold(50f, 300f, delta = policy.untouchedWarning)
        val shots = game.projectiles.filter { it.hostile }
        assertEquals(3, shots.size)
        assertEquals(listOf(-60f, 0f, 60f).map { 20f + it }, shots.map { it.y }.sorted())
        val life = (300f + 400f) / policy.untouchedShotSpeed
        for (shot in shots) {
            assertEquals(50f, shot.x, 1e-4f)
            assertEquals(policy.untouchedShotSpeed, shot.vx, 1e-3f)
            assertEquals(0f, shot.vy, 1e-3f)
            assertEquals(7f, shot.radius)
            assertEquals(life, shot.life, 1e-5f)
            assertEquals(game.hostileDamage(null, policy.untouchedShotDamage), shot.damage)
        }
        assertEquals(0f, game.sites.single().warningRemaining)
    }

    @Test
    fun theUntouchedTrialCompletesAfterTwelveCleanSeconds() {
        val game = game()
        game.startChallenge(SiteKind.UNTOUCHED_TRIAL)
        game.sites = listOf(game.sites.single().copy(meter = policy.untouchedRequired - 0.05f))
        game.hold(200f, 0f, delta = 0.1f)
        game.assertCompleted(DirectedReward.RELIC)
    }

    @Test
    fun theVelocityHoldEarnsOnlyAtOverdriveSpeedInsideItsZone() {
        val game = game()
        game.startChallenge(SiteKind.VELOCITY_TRIAL)
        game.hold(policy.velocityZoneRadius, 0f, vy = policy.velocityMinSpeed, delta = 0.1f)
        assertEquals(0.1f, game.sites.single().meter, 1e-6f)
        game.hold(1_000f, 0f, vy = policy.velocityMinSpeed - 1f, delta = 0.1f)
        assertEquals(0.1f, game.sites.single().meter, 1e-6f, "below the speed earns nothing")
        game.hold(policy.velocityZoneRadius + 1f, 0f, vy = 2_000f, delta = 0.1f)
        assertEquals(0.1f, game.sites.single().meter, 1e-6f, "outside the zone earns nothing")
        // Neither slowing down nor leaving the zone fails it: only time does.
        assertEquals(1, game.sites.size)
        game.sites = listOf(game.sites.single().copy(meter = policy.velocityRequired - 0.05f))
        game.hold(-600f, 800f, vx = 1_500f, delta = 0.1f)
        game.assertCompleted(DirectedReward.ITEM_AND_REPAIR)
    }

    @Test
    fun theSlalomIsAHashOfTheRunSeedAndTheWindowSideWithAlternatingFlaggedPoles() {
        val game = game()
        game.velocityX = 600f
        val streams = listOf(game.rewardRandom.snapshot(), game.spawnRandom.snapshot(), game.gameplayRandom.snapshot(),
            game.siteRandom.snapshot())
        val slalom = game.startChallenge(SiteKind.SLALOM_TRIAL, offerId = 2, side = 1, x = 40f, y = -30f)
        assertEquals(streams, listOf(game.rewardRandom.snapshot(), game.spawnRandom.snapshot(),
            game.gameplayRandom.snapshot(), game.siteRandom.snapshot()))
        assertEquals(0f, slalom.axis, "an open field keeps the flight's heading")
        assertEquals(policy.slalomPoles, slalom.course.size)
        val first = if (slalom.sides and 1 != 0) 1f else -1f
        for ((index, pole) in slalom.course.withIndex()) {
            val side = if (index % 2 == 0) first else -first
            assertEquals(policy.slalomFirstAlong + policy.slalomSpacing * index, pole.dx, 1e-3f)
            assertEquals(side * policy.slalomPoleOffset, pole.dy, 1e-3f)
            assertEquals(side > 0f, slalom.sides and (1 shl index) != 0)
        }
        assertTrue(slalom.sides == 0b010101 || slalom.sides == 0b101010)

        // The same seed and side lay the same slalom; the first pole side follows the hash across sides and seeds.
        val again = game().apply { velocityX = 600f }.startChallenge(SiteKind.SLALOM_TRIAL, offerId = 2, side = 1, x = 40f, y = -30f)
        assertEquals(slalom.course, again.course)
        assertEquals(slalom.sides, again.sides)
        val firstSides = (0 until 40).map { seed ->
            game(seed).apply { velocityX = 600f }.startChallenge(SiteKind.SLALOM_TRIAL).sides and 1
        }.toSet()
        assertEquals(setOf(0, 1), firstSides)
    }

    @Test
    fun aBlockedSlalomTurnsItsAxisUntilEveryPoleIsClear() {
        val open = game().apply { velocityX = 600f }.startChallenge(SiteKind.SLALOM_TRIAL)
        val blocked = game()
        blocked.velocityX = 600f
        val pole = open.course[3]
        blocked.placeObstaclesForTesting(testObstacle(pole.dx, pole.dy, radius = 40f))
        val turned = blocked.startChallenge(SiteKind.SLALOM_TRIAL)
        assertEquals(policy.slalomAxisTurn, turned.axis, 1e-6f)
        for (mark in turned.course) assertFalse(blocked.isSpawnPointBlocked(mark.dx, mark.dy, policy.slalomPoleClearance))
        assertEquals(open.sides, turned.sides, "the hashed sides do not depend on the axis")

        // With every candidate axis blocked the slalom keeps the heading.
        val walled = game()
        walled.velocityX = 600f
        walled.placeObstaclesForTesting(testObstacle(policy.slalomFirstAlong, 0f, radius = 260f))
        assertEquals(0f, walled.startChallenge(SiteKind.SLALOM_TRIAL).axis)
    }

    /** One step crossing [slalom]'s pole [index] line at [lateral] (axis 0: along x, lateral along y). */
    private fun MutableGameState.cross(slalom: SiteState, index: Int, lateral: Float) {
        val along = policy.slalomFirstAlong + policy.slalomSpacing * index
        flyStep(slalom.x + along - 20f, slalom.y + lateral, slalom.x + along + 20f, slalom.y + lateral)
    }

    private fun SiteState.flag(index: Int): Float = if (sides and (1 shl index) != 0) 1f else -1f

    @Test
    fun aSlalomPoleCountsOnlyOnItsFlaggedSideClearOfThePole() {
        val game = game()
        game.velocityX = 600f
        val slalom = game.startChallenge(SiteKind.SLALOM_TRIAL)
        val clear = policy.slalomPoleOffset + game.coreRadius
        game.takeSoundCues()
        game.cross(slalom, 0, slalom.flag(0) * clear)
        assertEquals(1, game.sites.single().step)
        assertTrue(GameplayAudioCue.PICKUP in game.takeSoundCues())
        // Beyond the course's lateral reach a crossing is no attempt.
        game.cross(slalom, 1, slalom.flag(1) * (policy.slalomMaxLateral + 1f))
        game.cross(slalom, 1, -slalom.flag(1) * (policy.slalomMaxLateral + 1f))
        assertEquals(1, game.sites.single().step)
        // Crossing backward does nothing either.
        val along = policy.slalomFirstAlong + policy.slalomSpacing
        game.flyStep(along + 20f, 0f, along - 20f, 0f)
        assertEquals(1, game.sites.single().step)
        // Brushing the pole is the wrong side.
        game.cross(slalom, 1, slalom.flag(1) * (clear - 1f))
        game.assertGrantedNothing()
    }

    @Test
    fun aSlalomCrossedOnTheWrongSideFails() {
        val game = game()
        game.velocityX = 600f
        val slalom = game.startChallenge(SiteKind.SLALOM_TRIAL)
        game.cross(slalom, 0, -slalom.flag(0) * 400f)
        game.assertGrantedNothing()
    }

    @Test
    fun alternatingSidesCompleteTheSlalomWithItsWeaponDirection() {
        val game = game()
        game.velocityX = 600f
        val slalom = game.startChallenge(SiteKind.SLALOM_TRIAL)
        for (index in 0 until policy.slalomPoles - 1) {
            game.cross(slalom, index, slalom.flag(index) * 300f)
            assertEquals(index + 1, game.sites.single().step)
        }
        game.cross(slalom, policy.slalomPoles - 1, slalom.flag(policy.slalomPoles - 1) * 300f)
        game.assertCompleted(DirectedReward.WEAPON)
    }

    @Test
    fun theSlalomLeashFollowsItsNextPole() {
        val game = game()
        game.velocityX = 600f
        val slalom = game.startChallenge(SiteKind.SLALOM_TRIAL)
        game.sites = listOf(slalom.copy(step = 4))
        val pole = slalom.course[4]
        game.hold(pole.dx, pole.dy + CHALLENGE_LEASH)
        assertEquals(1, game.sites.size, "exactly the leash keeps the slalom")
        assertTrue(length(pole.dx, pole.dy + CHALLENGE_LEASH) > CHALLENGE_LEASH, "farther than the leash from the centre")
        game.hold(pole.dx, pole.dy + CHALLENGE_LEASH + 1f)
        game.assertGrantedNothing()
    }

    @Test
    fun aRebaseShiftsAChallengeAndItsAimAndKeepsItsCourse() {
        val game = game()
        game.velocityX = 600f
        val slalom = game.startChallenge(SiteKind.SLALOM_TRIAL, x = 300_000f, y = 20f)
        game.coreX = 300_000f
        game.coreY = 0f
        game.rebaseWorldIfNeeded()
        val shifted = game.sites.single()
        assertEquals(0f, shifted.x)
        assertEquals(20f, shifted.y)
        assertEquals(slalom.course, shifted.course)
        game.cross(shifted, 0, shifted.flag(0) * 300f)
        assertEquals(1, game.sites.single().step)

        val untouched = game()
        untouched.startChallenge(SiteKind.UNTOUCHED_TRIAL, x = 300_000f)
        untouched.sites = listOf(untouched.sites.single().copy(aimX = 300_200f, aimY = 50f, warningRemaining = 0.4f))
        untouched.coreX = 300_000f
        untouched.coreY = 0f
        untouched.rebaseWorldIfNeeded()
        assertEquals(200f, untouched.sites.single().aimX)
        assertEquals(50f, untouched.sites.single().aimY)
    }

    @Test
    fun aWindowCanOfferEveryChallengeAndEnteringOneStartsTheOnlyEngagement() {
        val challenges = SiteKind.entries.filter { it.family == SiteFamily.CHALLENGE }
        for (kind in challenges) assertEquals(0.36f, canonicalGameplayContent.sites.definition(kind).windowWeight)
        val found = mutableMapOf<SiteKind, Pair<MutableGameState, SiteState>>()
        for (seed in 1..400) {
            if (found.size == challenges.size) break
            val game = game(seed)
            val offer = game.offerWindowForTesting(0).firstOrNull { it.kind.family == SiteFamily.CHALLENGE } ?: continue
            if (offer.kind !in found) found[offer.kind] = game to offer
        }
        assertEquals(challenges.toSet(), found.keys, "the shipped weights offer every challenge within 400 seeds")
        for ((kind, entry) in found) {
            val (game, offer) = entry
            game.velocityX = 500f
            game.flyStep(offer.x - 50f, offer.y, offer.x, offer.y)
            val running = game.sites.single()
            assertTrue(running.active, "$kind")
            assertEquals(offer.id, running.id)
            assertTrue(game.engagementActive())
            assertEquals(game.content.sites.definition(kind).instruction, game.message)
        }
    }

    @Test
    fun theArchitectsArrivalClearsTheChallengeAndKeepsTheRamFoes() {
        val game = game()
        val rite = game.startChallenge(SiteKind.RAM_TRIAL)
        game.elapsed = game.content.tempo.bossAtSeconds
        game.hold(0f, 0f, delta = 0.01f)
        assertTrue(game.sites.isEmpty())
        assertNull(game.encounter(siteEncounterTag(rite)))
        assertEquals(rite.foeIds, game.enemies.map { it.id })
        assertTrue(game.enemies.all { it.ownerTag == 0L })
        assertNotEquals(SiteNotice.CHALLENGE_COMPLETE.english, game.message)
        assertTrue(game.pendingDirectedRewards.isEmpty())
    }

    @Test
    fun theFirstPoleSideIsTheOnlyHashedValue() {
        // The axis and every pole follow from the heading and the first side: flipping the heading flips the course.
        val east = game().apply { velocityX = 600f }.startChallenge(SiteKind.SLALOM_TRIAL)
        val west = game().apply { velocityX = -600f }.startChallenge(SiteKind.SLALOM_TRIAL)
        assertEquals(east.sides, west.sides)
        assertEquals(abs(atan2(0f, -600f)), abs(west.axis), 1e-6f)
        for (index in east.course.indices) {
            assertEquals(-east.course[index].dx, west.course[index].dx, 1e-2f)
            assertEquals(-east.course[index].dy, west.course[index].dy, 1e-2f)
        }
    }
}
