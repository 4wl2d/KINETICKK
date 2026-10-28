// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.impactTaken
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.model.weaponTaken
import kinetickk.ball.gameplay.nucleus.render.EnemyBehavior
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The mini-bosses: Warden Prime, Brood Queen, Throne and Pyre Herald, their adds and their Core-impact rules. */
class MiniBossBehaviorTest {
    @Test
    fun wardenPrimePullsThenPushesTheCoreWithAStrongerFinalWellAndCallsTwoWardens() {
        val state = quietState()
        val prime = state.spawn(EnemyType.WARDEN_PRIME, 300f, 0f)
        state.runFor(FIXED)
        assertFalse(prime.phaseStartPending, "its first phase start ran on its first step")

        // The first phase only pulls: the Core is drawn toward it, harder the closer it is.
        repeat(3) { assertEquals(wellPull(state, prime), wellStep(state, prime), 1e-3f) }
        state.runFor(3f)
        assertEquals(EnemyBehavior.IDLE, prime.mode, "no push in the first phase")
        assertTrue(wellStep(state, prime) > 0f)

        // The second phase starts with its shift; then it pulls for 2.5 s and pushes for 1.5 s in turn.
        state.crossGate(prime)
        assertEquals(1, prime.phase)
        state.runFor(2f)
        assertEquals(EnemyBehavior.IDLE, prime.mode, "the shift pulls")
        val pulling = state.elapsed
        state.runUntil { prime.mode == EnemyBehavior.PRIMED }
        assertEquals(PRIME_PULL_SECONDS, state.elapsed - pulling, 0.03f)
        assertEquals(-wellPull(state, prime), wellStep(state, prime), 1e-3f, "it pushes the Core away")
        val pushing = state.elapsed
        state.runUntil { prime.mode == EnemyBehavior.IDLE }
        assertEquals(PRIME_PUSH_SECONDS, state.elapsed - pushing, 0.03f)

        // The final phase: its well grows by 1.3 and two Wardens at half integrity join it, never more.
        state.crossGate(prime)
        state.runFor(FIXED)
        assertEquals(2, prime.phase)
        val guards = state.enemies.filter { it.type == EnemyType.WARDEN }
        assertEquals(PRIME_GUARDS, guards.size)
        guards.forEach { assertEquals(state.spawnIntegrity(EnemyType.WARDEN) * PRIME_GUARD_SHARE, it.maxHp, 1e-2f) }
        state.runFor(FIXED)
        assertEquals(PRIME_GUARDS, state.enemies.count { it.type == EnemyType.WARDEN }, "its phase start calls them once")
        // Measured without the Wardens' own wells.
        state.enemies.removeAll { it.type == EnemyType.WARDEN }
        prime.mode = EnemyBehavior.IDLE
        prime.modeUntil = state.elapsed + 1f
        assertEquals(wellPull(state, prime) * PRIME_FINAL_WELL_SCALE, wellStep(state, prime), 1e-3f)

        // A dash phase slips through the well.
        state.dashPhaseTime = 1f
        assertEquals(0f, wellStep(state, prime))
    }

    @Test
    fun wardenPrimeFiresTwelveFourteenAndSixteenBoltRingsAndAnAimedFanOnEveryOtherVolleyOfItsSecondPhase() {
        val state = quietState()
        val prime = state.spawn(EnemyType.WARDEN_PRIME, 300f, 0f)
        state.runFor(FIXED)
        state.projectiles.clear()

        prime.actionTimer = FIXED * 0.5f
        state.runFor(FIXED)
        assertEquals(12, state.projectiles.size)
        assertEquals(primeVolleySeconds(0), prime.actionTimer, 1e-4f)

        // The second phase runs a double cycle: a ring of 14 at its half, a ring of 14 with three aimed bolts at its end.
        prime.phase = 1
        state.projectiles.clear()
        prime.actionTimer = primeVolleySeconds(1) + FIXED * 0.5f
        state.runFor(FIXED)
        assertEquals(14, state.projectiles.size, "a ring alone at the half")
        state.projectiles.clear()
        prime.actionTimer = FIXED * 0.5f
        state.runFor(FIXED)
        assertEquals(14 + PRIME_FAN, state.projectiles.size, "a ring and the fan at the end")
        assertEquals(primeVolleySeconds(1) * 2f, prime.actionTimer, 1e-4f)
        val toCore = atan2(state.coreY - prime.previousY, state.coreX - prime.previousX)
        val fan = state.projectiles.filter { abs(angleBetween(atan2(it.vy, it.vx), toCore)) <= PRIME_FAN_SPACING + 1e-3f }
        assertTrue(fan.size >= PRIME_FAN, "the fan aims at the Core")

        prime.phase = 2
        state.projectiles.clear()
        prime.actionTimer = FIXED * 0.5f
        state.runFor(FIXED)
        assertEquals(16, state.projectiles.size)

        // Silenced, the volley's turn passes without a shot.
        prime.silencedUntil = Float.MAX_VALUE
        state.projectiles.clear()
        prime.actionTimer = FIXED * 0.5f
        state.runFor(FIXED)
        assertTrue(state.projectiles.isEmpty())
    }

    @Test
    fun aBroodQueenHatchesOnePlusItsSacsLaunchedSidewaysAndFastImpactsBurstTheSacs() {
        val state = quietState()
        val queen = state.spawn(EnemyType.BROOD_QUEEN, 500f, 0f)
        state.runFor(FIXED)
        assertEquals(QUEEN_SACS, queen.charges, "its first phase start grows three sacs")

        queen.actionTimer = FIXED * 0.5f
        state.runFor(FIXED)
        val brood = state.enemies.filter { it.type == EnemyType.SWARMLING }
        assertEquals(1 + QUEEN_SACS, brood.size)
        assertEquals(queenBroodSeconds(0), queen.actionTimer, 1e-4f)
        for (child in brood) {
            val outX = child.x - queen.x
            val outY = child.y - queen.y
            val speed = sqrt(child.vx * child.vx + child.vy * child.vy)
            assertEquals(QUEEN_BROOD_LAUNCH_SPEED, speed, 1e-2f, "launched at its speed")
            assertEquals(0f, (child.vx * outX + child.vy * outY) / (speed * sqrt(outX * outX + outY * outY)), 1e-2f, "sideways")
        }
        state.enemies.removeAll { it.type == EnemyType.SWARMLING }

        // 599 u/s leaves the sacs; 600 bursts one, then the sacs rest half a second.
        state.ram(queen, QUEEN_SAC_BURST_SPEED - 1f)
        assertEquals(QUEEN_SACS, queen.charges)
        state.ram(queen, QUEEN_SAC_BURST_SPEED)
        assertEquals(QUEEN_SACS - 1, queen.charges)
        state.ram(queen, 1_200f)
        assertEquals(QUEEN_SACS - 1, queen.charges, "the sacs rest after a burst")
        state.elapsed += QUEEN_SAC_COOLDOWN
        state.ram(queen, 1_200f)
        assertEquals(QUEEN_SACS - 2, queen.charges)

        // Fewer sacs, smaller broods.
        queen.actionTimer = FIXED * 0.5f
        state.runFor(FIXED)
        assertEquals(2, state.enemies.count { it.type == EnemyType.SWARMLING })

        // The second phase regrows them; the final phase keeps what is left and no ram bursts an empty queen.
        state.crossGate(queen)
        state.runFor(FIXED)
        assertEquals(QUEEN_SACS, queen.charges)
        queen.charges = 1
        state.elapsed = maxOf(state.elapsed, queen.shiftUntil)
        state.crossGate(queen)
        state.runFor(FIXED)
        assertEquals(2, queen.phase)
        assertEquals(1, queen.charges)
        queen.charges = 0
        state.elapsed += QUEEN_SAC_COOLDOWN
        state.ram(queen, 1_200f)
        assertEquals(0, queen.charges)
    }

    @Test
    fun aBroodQueenKeepsAtMostTenOfHerBroodAliveAndSpiralsFromHerSecondPhase() {
        val state = encounterState()
        val tag = encounterTag(EncounterOwner.ROOM, 2)
        assertTrue(state.spawnEncounter(EncounterRequest(tag, state.coreX + 500f, state.coreY, 900f, listOf(EnemyType.BROOD_QUEEN))))
        val queen = state.enemies.single()
        state.runFor(FIXED)
        val hatched = mutableListOf<Int>()
        repeat(4) {
            val before = state.enemies.count { it.type == EnemyType.SWARMLING }
            queen.actionTimer = FIXED * 0.5f
            state.runFor(FIXED)
            hatched += state.enemies.count { it.type == EnemyType.SWARMLING } - before
        }
        assertEquals(listOf(4, 4, 2, 0), hatched, "broods of four until ten of hers live")
        assertTrue(state.enemies.filter { it.type == EnemyType.SWARMLING }.all { it.ownerTag == tag }, "her brood joins her encounter")
        assertEquals(1 + QUEEN_BROOD_LIVE, assertNotNull(state.encounter(tag)).spawned)

        // From the second phase a spiral volley every two seconds, of 10 bolts in the final phase.
        queen.phase = 1
        state.projectiles.clear()
        queen.actionTimer = 100f
        queen.modeUntil = state.elapsed
        state.runFor(FIXED)
        assertEquals(QUEEN_SPIRAL, state.projectiles.size)
        assertEquals(state.elapsed + QUEEN_SPIRAL_SECONDS / state.hostileAttackRate(), queen.modeUntil, 1e-3f)
        queen.phase = 2
        state.projectiles.clear()
        queen.modeUntil = state.elapsed
        state.runFor(FIXED)
        assertEquals(QUEEN_FINAL_SPIRAL, state.projectiles.size)
    }

    @Test
    fun aThronesArcsGuardTheirDirectionsAndOnlyHitsThroughTheGapsLandInFull() {
        val state = quietState()
        val throne = state.spawn(EnemyType.THRONE, 400f, 0f)
        state.runFor(FIXED)
        assertEquals(THRONE_ARCS, throne.charges, "three arcs from its first phase start")

        throne.facing = 0f
        val degree = (PI / 180.0).toFloat()
        assertTrue(arcGuards(throne, 0f))
        assertTrue(arcGuards(throne, 39.9f * degree))
        assertFalse(arcGuards(throne, 40.1f * degree))
        assertFalse(arcGuards(throne, 60f * degree), "a gap between two arcs")
        assertTrue(arcGuards(throne, 120f * degree))
        assertTrue(arcGuards(throne, -120f * degree))
        throne.charges = 1
        assertFalse(arcGuards(throne, 120f * degree), "a lost arc guards nothing")
        throne.charges = THRONE_ARCS

        // Each step the arcs turn and the guard follows the Core's direction.
        val toCore = atan2(state.coreY - throne.y, state.coreX - throne.x)
        throne.facing = toCore - throneSpin(0) * FIXED
        state.runFor(FIXED)
        assertEquals(toCore, throne.facing, 1e-3f)
        assertTrue(throne.guarded)
        assertEquals(0.1f, throne.impactTaken)
        assertEquals(0.15f, throne.weaponTaken)
        throne.facing = toCore + 60f * degree - throneSpin(0) * FIXED
        state.runFor(FIXED)
        assertFalse(throne.guarded)
        assertEquals(0.9f, throne.impactTaken)
        assertEquals(0.6f, throne.weaponTaken)

        // The same ram lands 0.1 / 0.9 as hard through an arc as through a gap.
        fun ramDamage(guarded: Boolean): Float {
            val arena = quietState().apply { critChance = 0f }
            val target = arena.spawnTouching(EnemyType.THRONE)
            arena.runFor(FIXED)
            target.facing = (if (guarded) PI.toFloat() else PI.toFloat() + 60f * degree) - throneSpin(0) * FIXED
            arena.runFor(FIXED)
            assertEquals(guarded, target.guarded)
            val before = target.hp
            arena.ram(target, 250f)
            return before - target.hp
        }
        val arc = ramDamage(guarded = true)
        val gap = ramDamage(guarded = false)
        assertTrue(arc > 0f)
        assertEquals(0.1f / 0.9f, arc / gap, 1e-3f)
    }

    @Test
    fun aRamIntoAThronesArcIsPunishedWithARingAtMostEveryOneAndAHalfSeconds() {
        val state = quietState()
        val throne = state.spawnTouching(EnemyType.THRONE)
        state.runFor(FIXED)
        throne.facing = PI.toFloat() - throneSpin(0) * FIXED
        state.runFor(FIXED)
        assertTrue(throne.guarded)
        state.projectiles.clear()
        state.ram(throne, 420f)
        val ring = state.projectiles.filter { it.hostile }
        assertEquals(THRONE_PUNISH_RING, ring.size)
        ring.forEach { assertEquals(THRONE_PUNISH_SPEED * state.rebirthProfile.enemySpeedMultiplier, sqrt(it.vx * it.vx + it.vy * it.vy), 0.5f) }
        assertEquals(state.elapsed + THRONE_PUNISH_COOLDOWN, throne.abilityAt)

        state.projectiles.clear()
        state.ram(throne, 420f)
        assertTrue(state.projectiles.none { it.hostile }, "the punish rests")
        state.elapsed += THRONE_PUNISH_COOLDOWN
        throne.guarded = false
        state.ram(throne, 420f)
        assertTrue(state.projectiles.none { it.hostile }, "a ram through a gap is not punished")
        throne.guarded = true
        throne.silencedUntil = Float.MAX_VALUE
        state.ram(throne, 420f)
        assertTrue(state.projectiles.none { it.hostile }, "a silenced Throne punishes nothing")
    }

    @Test
    fun aShotOnAThronesArcIsSpentWithoutAReturnAndAShotThroughAGapPassesOn() {
        val state = quietState()
        val throne = state.spawn(EnemyType.THRONE, 300f, 0f)
        state.runFor(FIXED)
        val maxHp = throne.hp

        fun shoot(): Projectile {
            val shot = Projectile(throne.x - 200f, throne.y, 900f, 0f, 5f, 2f, hostile = false, damage = 40f, pierce = 3)
            shot.x = throne.x - 10f
            shot.previousX = throne.x - 200f
            state.projectiles += shot
            return shot
        }
        // An arc faces the shot (it comes from the Throne's west), while the Core-facing guard says otherwise.
        throne.facing = PI.toFloat()
        throne.guarded = false
        val blocked = shoot()
        state.resolveProjectileHits()
        assertTrue(blocked !in state.projectiles, "the arc stops the shot")
        assertTrue(state.projectiles.none { it.hostile }, "no return shot")
        assertEquals(maxHp - 40f * 0.15f, throne.hp, 1e-2f)
        assertFalse(throne.guarded, "the Core-facing guard holds again after the shot")

        throne.facing = PI.toFloat() + 60f * (PI / 180.0).toFloat()
        throne.guarded = true
        val before = throne.hp
        val through = shoot()
        state.resolveProjectileHits()
        assertEquals(40f * 0.6f, before - throne.hp, 1e-2f)
        assertTrue(through in state.projectiles, "a shot through a gap passes on")
        assertTrue(throne.guarded)

        // The Reflector's alias still answers only for the Reflector.
        assertFalse(reflectorShieldFaces(throne, throne.x - 200f, throne.y))
        throne.facing = PI.toFloat()
        assertTrue(shieldFaces(throne, throne.x - 200f, throne.y))
    }

    @Test
    fun eachGateShattersAThroneArcAndItsArcsTurnFasterPhaseByPhase() {
        val state = quietState()
        val throne = state.spawn(EnemyType.THRONE, 400f, 0f)
        state.runFor(FIXED)
        state.takeVisualFxCues()
        for (phase in 1..2) {
            state.elapsed = maxOf(state.elapsed, throne.shiftUntil)
            state.crossGate(throne)
            state.runFor(FIXED)
            assertEquals(phase, throne.phase)
            assertEquals(THRONE_ARCS - phase, throne.charges)
            val bursts = state.takeVisualFxCues().filterIsInstance<kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue.Burst>()
            assertTrue(bursts.any { it.requestedCount == 16 }, "the lost arc shatters")
            val facing = throne.facing
            state.runFor(FIXED)
            assertEquals(throneSpin(phase) * FIXED, throne.facing - facing, 1e-4f)
        }
        assertEquals(0.8f, throneSpin(1))
        assertEquals(1f, throneSpin(2))
    }

    @Test
    fun aThroneFiresFansThenAddsARingOnEverySecondVolleyAndPassesJudgementInItsFinalPhase() {
        val state = quietState()
        val throne = state.spawn(EnemyType.THRONE, 400f, 0f)
        state.runFor(FIXED)
        state.projectiles.clear()
        throne.actionTimer = FIXED * 0.5f
        state.runFor(FIXED)
        assertEquals(THRONE_FAN, state.projectiles.size)
        assertEquals(THRONE_VOLLEY_SECONDS, throne.actionTimer, 1e-4f)

        throne.phase = 1
        state.projectiles.clear()
        throne.actionTimer = THRONE_VOLLEY_SECONDS + FIXED * 0.5f
        state.runFor(FIXED)
        assertEquals(THRONE_FAN, state.projectiles.size, "a fan alone on the first of two volleys")
        state.projectiles.clear()
        throne.actionTimer = FIXED * 0.5f
        state.runFor(FIXED)
        assertEquals(THRONE_FAN + THRONE_RING, state.projectiles.size, "a fan and the ring on the second")

        // Judgement: the arcs stop for a second while four lanes show, then four trains of six bolts run down them.
        throne.phase = 2
        throne.charges = 1
        state.projectiles.clear()
        throne.actionTimer = FIXED * 0.5f
        state.runFor(FIXED)
        assertEquals(EnemyBehavior.TELEGRAPH, throne.mode)
        assertEquals(THRONE_JUDGEMENT_SECONDS, throne.actionTimer, 1e-4f)
        val lanes = throne.facing
        state.runFor(THRONE_JUDGEMENT_TELEGRAPH_SECONDS - 0.05f)
        assertEquals(lanes, throne.facing, "the arcs stop while it judges")
        assertTrue(state.projectiles.isEmpty())
        state.runUntil { throne.mode == EnemyBehavior.IDLE }
        val bolts = state.projectiles.filter { it.hostile }
        assertEquals(THRONE_LANES * THRONE_TRAIN, bolts.size)
        val speed = THRONE_TRAIN_SPEED * state.rebirthProfile.enemySpeedMultiplier
        for (lane in 0 until THRONE_LANES) {
            val angle = lanes + PI.toFloat() / 4f + lane * PI.toFloat() / 2f
            val train = bolts.filter { angleBetween(atan2(it.vy, it.vx), angle) < 1e-3f }
            assertEquals(THRONE_TRAIN, train.size, "lane $lane")
            train.forEach {
                assertEquals(speed, sqrt(it.vx * it.vx + it.vy * it.vy), 0.5f)
                assertEquals(THRONE_TRAIN_RADIUS, it.radius)
            }
            // The train starts at the rim and runs out, its bolts 34 u apart.
            val reaches = train.map { (it.x - throne.previousX) * cos(angle) + (it.y - throne.previousY) * sin(angle) }.sorted()
            assertTrue(reaches.first() > throne.radius - 10f, "the train starts at the rim: ${reaches.first()}")
            for (index in 1 until reaches.size) assertEquals(THRONE_TRAIN_SPACING, reaches[index] - reaches[index - 1], 0.05f)
        }
    }

    @Test
    fun aPyreHeraldTrailsEmbersShieldsWithImpsAndHurtsAtAnySpeed() {
        val state = quietState()
        val pyre = state.spawn(EnemyType.PYRE_HERALD, 400f, 0f)
        val spawnedAt = state.elapsed
        state.runFor(1.25f)
        val embers = state.volatileBursts
        assertEquals(2, embers.size, "an ember every 0.6 s")
        embers.forEach { assertEquals(PYRE_EMBER_DELAY, it.detonatesAt - it.armedAt, 1e-4f) }
        assertEquals(spawnedAt + PYRE_EMBER_SECONDS, embers[0].armedAt, 0.02f)

        // Its first shield rises six seconds in and calls two Imps; the next comes twelve seconds later.
        assertEquals(spawnedAt + HERALD_FIRST_SHIELD_SECONDS, pyre.abilityAt, 1e-4f)
        pyre.abilityAt = state.elapsed
        state.runFor(FIXED)
        assertTrue(state.shielded(pyre))
        assertEquals(state.elapsed + HERALD_SHIELD_SECONDS, pyre.shieldUntil, 1e-4f)
        assertEquals(state.elapsed + HERALD_SHIELD_INTERVAL_SECONDS, pyre.abilityAt, 1e-4f)
        assertEquals(PYRE_IMPS, state.enemies.count { it.type == EnemyType.IMP })
        assertEquals(0f, state.dealHit(pyre, 500f, HitSource.WEAPON).amount, "its shield stops all but impacts")

        // Its contact hurts a fast Core.
        assertEquals(state.contactDamage(pyre), state.strikeDamage(pyre))
        assertTrue(state.strikeDamage(pyre) > 0f)
        val hp = state.hp
        state.ram(pyre, 900f)
        assertEquals(hp - state.contactDamage(pyre), state.hp, 1e-3f)
        assertFalse(state.shielded(pyre), "the impact broke the shield")

        // Spirals of 6 + 2 per phase bolts that live six seconds.
        state.projectiles.clear()
        pyre.actionTimer = FIXED * 0.5f
        state.runFor(FIXED)
        assertEquals(6, state.projectiles.size)
        state.projectiles.forEach { assertTrue(it.life > PYRE_SHOT_LIFE - 0.1f && it.life <= PYRE_SHOT_LIFE) }

        // The final phase opens with a ring of six embers 160 u out and shields every nine seconds.
        state.volatileBursts = kinetickk.foundation.collections.immutableListOf()
        state.elapsed = maxOf(state.elapsed, pyre.shiftUntil)
        pyre.phase = 1
        state.crossGate(pyre)
        state.runFor(FIXED)
        assertEquals(2, pyre.phase)
        val ring = state.volatileBursts.filter { abs(it.detonatesAt - it.armedAt - PYRE_RING_DELAY) < 1e-4f }
        assertEquals(PYRE_RING_EMBERS, ring.size)
        assertEquals(PYRE_RING_EMBERS, state.volatileBursts.size, "no trail ember while it shifts")
        ring.forEach { assertEquals(PYRE_RING_RADIUS, sqrt((it.x - pyre.x) * (it.x - pyre.x) + (it.y - pyre.y) * (it.y - pyre.y)), 1e-2f) }
        pyre.shieldUntil = 0f
        pyre.abilityAt = state.elapsed
        state.runFor(FIXED)
        assertEquals(state.elapsed + PYRE_FINAL_SHIELD_INTERVAL_SECONDS, pyre.abilityAt, 1e-4f)
    }

    @Test
    fun aPyreHeraldsImpsJoinItsEncounterFourAtMost() {
        val state = encounterState()
        val tag = encounterTag(EncounterOwner.DIMENSION, 3)
        assertTrue(state.spawnEncounter(EncounterRequest(tag, state.coreX + 500f, state.coreY, 900f, listOf(EnemyType.PYRE_HERALD))))
        val pyre = state.enemies.single()
        val called = mutableListOf<Int>()
        repeat(3) {
            val before = state.enemies.count { it.type == EnemyType.IMP }
            pyre.shieldUntil = 0f
            pyre.abilityAt = state.elapsed
            state.runFor(FIXED)
            called += state.enemies.count { it.type == EnemyType.IMP } - before
        }
        assertEquals(listOf(PYRE_IMPS, PYRE_IMPS, 0), called)
        assertTrue(state.enemies.filter { it.type == EnemyType.IMP }.all { it.ownerTag == tag && state.leashExempt(it) })
        assertEquals(1 + PYRE_IMPS_LIVE, assertNotNull(state.encounter(tag)).spawned)
    }

    @Test
    fun aShiftingMiniBossAttacksNothingAndResumesAfterItsShift() {
        for (type in listOf(EnemyType.WARDEN_PRIME, EnemyType.BROOD_QUEEN, EnemyType.THRONE, EnemyType.PYRE_HERALD)) {
            val state = quietState()
            val boss = state.spawn(type, 450f, 0f)
            state.runFor(FIXED)
            if (type == EnemyType.PYRE_HERALD) boss.abilityAt = Float.MAX_VALUE
            state.crossGate(boss)
            val shift = boss.shiftUntil - state.elapsed
            boss.actionTimer = 0f
            boss.modeUntil = state.elapsed
            state.projectiles.clear()
            state.volatileBursts = kinetickk.foundation.collections.immutableListOf()
            val enemies = state.enemies.size
            state.runFor(shift - FIXED)
            assertTrue(state.projectiles.isEmpty(), "$type fires nothing while it shifts")
            assertTrue(state.volatileBursts.isEmpty(), "$type drops no ember while it shifts")
            assertEquals(enemies, state.enemies.size, "$type calls nothing while it shifts")
            // Its next attack comes within one cycle after the shift.
            state.runFor(4.5f)
            assertTrue(state.projectiles.isNotEmpty() || state.enemies.size > enemies, "$type attacks again")
        }
    }

    @Test
    fun anEnragedMiniBossAttacksThirtyPercentFaster() {
        val state = encounterState()
        val tag = encounterTag(EncounterOwner.SITE, 5)
        assertTrue(state.spawnEncounter(EncounterRequest(tag, state.coreX + 500f, state.coreY, 900f, listOf(EnemyType.THRONE))))
        val throne = state.enemies.single()
        state.runFor(FIXED)
        throne.actionTimer = 1f
        state.runFor(FIXED)
        val calm = (1f - throne.actionTimer) / state.hostileAttackRate()
        assertEquals(FIXED, calm, 1e-5f)
        state.elapsed = assertNotNull(state.encounter(tag)).startedAt + 75.5f
        throne.actionTimer = 1f
        state.runFor(FIXED)
        val enraged = (1f - throne.actionTimer) / state.hostileAttackRate()
        assertEquals(1.3f, enraged / calm, 1e-3f)
    }

    @Test
    fun miniBossAddsAndAttacksDrawNothingFromTheRunStreams() {
        val state = quietState()
        val prime = state.spawn(EnemyType.WARDEN_PRIME, 400f, 0f)
        val queen = state.spawn(EnemyType.BROOD_QUEEN, -400f, 0f)
        val pyre = state.spawn(EnemyType.PYRE_HERALD, 0f, 400f)
        val throne = state.spawn(EnemyType.THRONE, 0f, -400f)
        state.runFor(FIXED)
        val spawn = state.spawnRandom.snapshot()
        val gameplay = state.gameplayRandom.snapshot()
        val reward = state.rewardRandom.snapshot()
        prime.phase = 1
        state.crossGate(prime)
        queen.actionTimer = 0f
        pyre.abilityAt = state.elapsed
        throne.phase = 2
        throne.actionTimer = 0f
        state.runFor(FIXED)
        assertTrue(state.enemies.any { it.type == EnemyType.WARDEN })
        assertTrue(state.enemies.any { it.type == EnemyType.SWARMLING })
        assertTrue(state.enemies.any { it.type == EnemyType.IMP })
        state.runFor(2.5f)
        assertTrue(state.projectiles.isNotEmpty())
        assertEquals(spawn, state.spawnRandom.snapshot())
        assertEquals(gameplay, state.gameplayRandom.snapshot())
        assertEquals(reward, state.rewardRandom.snapshot())
    }

    // --- helpers ---

    /** The Core-velocity change one step of [prime]'s well makes (toward it positive), from a resting Core. */
    private fun wellStep(state: MutableGameState, prime: Enemy): Float {
        val toX = (prime.x - state.coreX) / state.distanceToCore(prime)
        val toY = (prime.y - state.coreY) / state.distanceToCore(prime)
        state.setVelocityForTesting(0f, 0f)
        state.runFor(FIXED)
        return state.velocityX * toX + state.velocityY * toY
    }

    /** The first-phase pull of [prime]'s well on a Core where it stands now, for one step. */
    private fun wellPull(state: MutableGameState, prime: Enemy): Float {
        val distance = state.distanceToCore(prime)
        return (PRIME_WELL_BASE_PULL + (1f - distance / PRIME_WELL_RADIUS) * PRIME_WELL_PULL) * FIXED
    }

    /** Brings [boss] to the next gate with a hit, starting its next phase (and its shift). */
    private fun MutableGameState.crossGate(boss: Enemy) {
        val gate = bossGate(boss, assertNotNull(encounterSpec(boss)))
        boss.hp = gate + 5f
        damageEnemy(boss, 20f)
        assertTrue(isShifting(boss), "${boss.type} reached its gate")
    }

    /** A Core touching [enemy] from its west, moving at [speed] straight into it, resolved as one contact. */
    private fun MutableGameState.ram(enemy: Enemy, speed: Float) {
        enemy.contactCooldown = 0f
        enemy.previousX = enemy.x
        enemy.previousY = enemy.y
        val touch = enemy.radius + coreRadius - 4f
        coreX = enemy.x - touch
        coreY = enemy.y
        previousCoreX = coreX - 10f
        previousCoreY = coreY
        setVelocityForTesting(speed, 0f)
        resolveEnemyCoreCollisions()
    }

    /** A quiet arena with the pointer on the Core; the grace keeps the singularity from ending the run. */
    private fun quietState(tier: Int = 0): MutableGameState = buildTestState(rebirth = tier).apply {
        updatePointer(640f, 360f)
        runGrace = Float.MAX_VALUE
        clearObstaclesForTesting()
    }

    /** A quiet started run where encounters may spawn. */
    private fun encounterState(): MutableGameState = quietState().apply { quietArenaForTesting() }

    private fun MutableGameState.spawn(type: EnemyType, x: Float, y: Float): Enemy {
        val enemy = spawnedEnemy(type, coreX + x, coreY + y)
        enemies += enemy
        return enemy
    }

    /** A [type] spawned east of the Core, touching it. */
    private fun MutableGameState.spawnTouching(type: EnemyType): Enemy = spawn(type, type.profile.radius + coreRadius - 4f, 0f)

    private fun MutableGameState.distanceToCore(enemy: Enemy): Float =
        sqrt((enemy.x - coreX) * (enemy.x - coreX) + (enemy.y - coreY) * (enemy.y - coreY))

    /** Steps until [done] holds, at most five seconds. */
    private fun MutableGameState.runUntil(done: () -> Boolean) {
        var seconds = 0f
        while (!done() && seconds < 5f) {
            runFor(FIXED)
            seconds += FIXED
        }
        assertTrue(done(), "reached within five seconds")
    }

    /** Enemy steps with the encounter step after them (phase starts), as the run orders them. */
    private fun MutableGameState.runFor(seconds: Float) {
        var left = seconds
        while (left > 1e-6f) {
            elapsed += FIXED
            updateEnemies(FIXED)
            updateBossEncounter()
            left -= FIXED
        }
    }

    private companion object {
        const val FIXED = MutableGameState.FIXED_STEP
    }
}
