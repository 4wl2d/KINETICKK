// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.RunVarietyNotices
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WorldEventKind
import kinetickk.ball.content.api.WorldEventPolicy
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.BuffSource
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.WorldEventPlan
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.EventStrikeProjection
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.immutableListOf
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.nextDown
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ambient world events (variety.md §6): the stateless plan made at run start, the telegraph banner and the end notice,
 * each kind's effect at its shipped numbers, strikes following a rebase, and the Architect, other world layers and the
 * quiet arena ending or skipping events. The plan and strike bounds are in RunVarietyBoundsTest.
 */
class WorldEventTest {
    private val content = canonicalGameplayContent
    private val policy = content.variety.worldEvents

    /** A started run with no enemies and no obstacles at 0 s; only what a test adds acts. */
    private fun arena(seed: Int = 5_150, content: GameplayContentSnapshot = this.content): MutableGameState =
        buildTestState(seed = seed, content = content).apply { clearObstaclesForTesting() }

    /** Replaces the run's plan by one event of [kind] telegraphed at [telegraphAt] (shipped telegraph and duration). */
    private fun MutableGameState.planOnly(kind: WorldEventKind, telegraphAt: Float = 10f): WorldEventPlan {
        val startsAt = telegraphAt + policy.telegraphSeconds
        val plan = WorldEventPlan(kind, telegraphAt, startsAt, startsAt + policy.durationOf(kind))
        worldEventPlans = immutableListOf(plan)
        nextWorldEvent = 0
        worldEvent = null
        return plan
    }

    /** Runs the world events alone (nothing else moves) in fixed steps until [time]. */
    private fun MutableGameState.runEventsUntil(time: Float, each: () -> Unit = {}) {
        while (elapsed < time) {
            elapsed += MutableGameState.FIXED_STEP
            updateWorldEvents(MutableGameState.FIXED_STEP)
            each()
        }
    }

    /** Sets the clock to [time] and runs one world-event step there. */
    private fun MutableGameState.eventStepAt(time: Float) {
        elapsed = time
        updateWorldEvents(MutableGameState.FIXED_STEP)
    }

    private fun MutableGameState.dataPickups(): Int = pickups.count { it.type == PickupType.DATA }

    /** A fresh Core's integrity after one sourceless hostile blast of [base] damage at [time] seconds. */
    private fun expectedHpAfterBlast(time: Float, base: Float): Float = arena().run {
        elapsed = time
        takeDamage(hostileDamage(null, base))
        hp
    }

    @Test
    fun everyRunPlansTwoOrThreeEventsOneAtATimeInsideTheWindow() {
        val withThree = IntArray(1)
        val kindRuns = IntArray(WorldEventKind.entries.size)
        val distinctPlans = HashSet<List<WorldEventPlan>>()
        for (seed in 1..SEEDS) {
            val plans = planWorldEvents(seed, policy)
            assertTrue(plans.size in policy.minPerRun..policy.maxPerRun, "Seed $seed plans ${plans.size}")
            if (plans.size == 3) withThree[0]++
            assertEquals(plans.size, plans.map { it.kind }.toSet().size, "Seed $seed repeats no kind")
            plans.forEachIndexed { index, plan ->
                assertTrue(plan.telegraphAt >= policy.firstAtSeconds, "Seed $seed telegraphs at ${plan.telegraphAt}")
                assertEquals(policy.telegraphSeconds, plan.startsAt - plan.telegraphAt, TIME_TOLERANCE)
                assertEquals(policy.durationOf(plan.kind), plan.endsAt - plan.startsAt, TIME_TOLERANCE)
                assertTrue(plan.endsAt <= policy.lastEndSeconds - policy.gapSeconds + TIME_TOLERANCE, "Seed $seed ends at ${plan.endsAt}")
                if (index > 0) {
                    val before = plans[index - 1]
                    assertTrue(before.endsAt + policy.gapSeconds <= plan.telegraphAt + TIME_TOLERANCE, "Seed $seed events $index overlap")
                }
                kindRuns[plan.kind.ordinal]++
            }
            assertEquals(plans, planWorldEvents(seed, policy), "Seed $seed plans the same events again")
            distinctPlans += plans
        }
        // Half the runs take the extra event, every kind shows up in about half the runs, and seeds differ.
        assertEquals(policy.extraEventChance, withThree[0] / SEEDS.toFloat(), 0.04f)
        kindRuns.forEachIndexed { kind, runs ->
            assertEquals(0.5f, runs / SEEDS.toFloat(), 0.06f, "${WorldEventKind.entries[kind]} share")
        }
        assertEquals(SEEDS, distinctPlans.size, "Every seed plans its own events")
    }

    @Test
    fun theKindsAreDrawnByWeightWithoutReplacement() {
        // Two zero weights: every three-event run plays exactly the three weighted kinds.
        val three = policy.copy(minPerRun = 3, weights = immutableListOf(0f, 0f, 1f, 1f, 1f))
        for (seed in 1..200) {
            assertEquals(
                setOf(WorldEventKind.GRAVITY_STORM, WorldEventKind.ION_STORM, WorldEventKind.SOLAR_FLARE),
                planWorldEvents(seed, three).map { it.kind }.toSet(),
            )
        }
        // Meteor at weight 4 against four kinds at 1, two events a run: P = 1/2 + 1/2 × 4/7.
        val heavy = policy.copy(maxPerRun = 2, weights = immutableListOf(4f, 1f, 1f, 1f, 1f))
        val meteorRuns = (1..SEEDS).count { seed -> planWorldEvents(seed, heavy).any { it.kind == WorldEventKind.METEOR_SHOWER } }
        assertEquals(0.5f + 0.5f * 4f / 7f, meteorRuns / SEEDS.toFloat(), 0.03f)
    }

    @Test
    fun theRunStartPlansTheEventsFromNoStream() {
        for (seed in 1..6) {
            val on = arena(seed = seed)
            val off = arena(seed = seed, content = content.withoutWorldEvents())
            assertEquals(planWorldEvents(seed, policy), on.worldEventPlans, "Seed $seed")
            assertEquals(0, on.nextWorldEvent)
            assertNull(on.worldEvent)
            assertTrue(off.worldEventPlans.isEmpty(), "Switched off, seed $seed plans nothing")
            assertEquals(off.spawnRandom.snapshot(), on.spawnRandom.snapshot(), "Seed $seed spawn stream")
            assertEquals(off.gameplayRandom.snapshot(), on.gameplayRandom.snapshot(), "Seed $seed gameplay stream")
            assertEquals(off.rewardRandom.snapshot(), on.rewardRandom.snapshot(), "Seed $seed reward stream")
            // A new run in the same state plans again from its own seed and forgets the event in progress.
            on.eventStepAt(on.worldEventPlans.first().telegraphAt)
            assertNotNull(on.worldEvent)
            on.startRun()
            assertEquals(planWorldEvents(seed, policy), on.worldEventPlans)
            assertEquals(0, on.nextWorldEvent)
            assertNull(on.worldEvent)
        }
        assertTrue(arena(content = content.withoutRunVariety()).worldEventPlans.isEmpty())
    }

    @Test
    fun theTelegraphRaisesTheBannerAndTheEndClearsTheSkies() {
        for (kind in WorldEventKind.entries) {
            val state = arena()
            val plan = state.planOnly(kind)
            state.message = "BEFORE"
            state.eventStepAt(plan.telegraphAt.nextDown())
            assertNull(state.worldEvent, "$kind waits for its telegraph")
            assertEquals("BEFORE", state.message)

            state.eventStepAt(plan.telegraphAt)
            val event = assertNotNull(state.worldEvent, "$kind telegraphs")
            assertEquals(kind, event.kind)
            assertEquals(plan.telegraphAt, event.telegraphAt)
            assertEquals(plan.startsAt, event.startsAt)
            assertEquals(plan.endsAt, event.endsAt)
            assertTrue(event.strikes.isEmpty())
            assertEquals(kind.displayName.uppercase(), state.message)
            assertTrue(state.message in RunVarietyNotices, "$kind banner is a localized notice")
            assertEquals(WORLD_EVENT_BANNER_SECONDS, state.messageTime)

            state.eventStepAt(plan.endsAt.nextDown())
            assertNotNull(state.worldEvent, "$kind still runs just before its end")
            state.eventStepAt(plan.endsAt)
            assertNull(state.worldEvent, "$kind ends")
            assertEquals(SKIES_CLEAR, state.message)
            assertTrue(SKIES_CLEAR in RunVarietyNotices)
            assertEquals(SKIES_CLEAR_SECONDS, state.messageTime)
            assertEquals(1, state.nextWorldEvent)
            state.message = "AFTER"
            state.eventStepAt(plan.endsAt + 30f)
            assertNull(state.worldEvent)
            assertEquals("AFTER", state.message)
        }
    }

    @Test
    fun theWholeScheduleRunsEveryPlanOnceInOrder() {
        // Seeds with two and with three events.
        val seeds = (1..40).filter { planWorldEvents(it, policy).size == 2 }.take(1) +
            (1..40).filter { planWorldEvents(it, policy).size == 3 }.take(1)
        assertEquals(2, seeds.size)
        for (seed in seeds) {
            val state = arena(seed = seed)
            val started = mutableListOf<Pair<WorldEventKind, Float>>()
            var ended = 0
            var active: WorldEventKind? = null
            state.runEventsUntil(content.tempo.bossAtSeconds) {
                val kind = state.worldEvent?.kind
                if (kind != null && active == null) started += kind to state.elapsed
                if (kind == null && active != null) ended++
                active = kind
            }
            val plans = state.worldEventPlans
            assertEquals(plans.map { it.kind }, started.map { it.first }, "Seed $seed plays its plan in order")
            started.forEachIndexed { index, (_, at) -> assertEquals(plans[index].telegraphAt, at, MutableGameState.FIXED_STEP * 1.01f) }
            assertEquals(plans.size, ended)
            assertEquals(plans.size, state.nextWorldEvent)
        }
    }

    @Test
    fun aMeteorShowerStrikesAroundTheHeadingAndEveryTelegraphedStrikeLands() {
        val meteor = policy.meteor
        val state = arena()
        val plan = state.planOnly(WorldEventKind.METEOR_SHOWER)
        // Heading +x: the Core drifts right (nothing moves it while only the events run).
        state.setVelocityForTesting(300f, 0f)
        val armedAt = HashSet<Float>()
        var mostLive = 0
        state.runEventsUntil(plan.endsAt + 1f) {
            val strikes = state.worldEvent?.strikes ?: return@runEventsUntil
            mostLive = maxOf(mostLive, strikes.size)
            for (strike in strikes) {
                if (!armedAt.add(strike.armedAt)) continue
                val dx = strike.x - state.coreX
                val dy = strike.y - state.coreY
                val distance = sqrt(dx * dx + dy * dy)
                assertTrue(distance >= meteor.minDistance - 0.01f && distance <= meteor.minDistance + meteor.distanceSpread + 0.01f)
                assertTrue(abs(atan2(dy, dx)) <= meteor.angleSpread / 2f + 1e-4f, "Within the heading's spread")
                assertEquals(meteor.radius, strike.radius)
                assertEquals(meteor.armSeconds, strike.strikesAt - strike.armedAt, TIME_TOLERANCE)
                assertTrue(strike.strikesAt <= plan.endsAt, "A telegraphed strike lands before the end")
            }
        }
        // One strike per interval while it can still land before the end: the pulses at 0, 1.1, ..., 27.5 s of the
        // 30 s shower (the one at 28.6 s would land at or after the end).
        assertEquals(26, armedAt.size)
        // Every strike landed and left its Data at its centre; the Core stood outside every ring.
        assertEquals(armedAt.size, state.dataPickups())
        assertEquals(state.maxHp, state.hp)
        assertEquals(2, mostLive, "At most two strikes are armed at once")
    }

    @Test
    fun aMeteorHurtsTheCoreInsideItsRingUnlessItDashesAndShovesEnemiesOut() {
        val meteor = policy.meteor
        /** Lands one meteor at [x], [y] on [state] during the shower. */
        fun land(state: MutableGameState, x: Float, y: Float) {
            val plan = state.planOnly(WorldEventKind.METEOR_SHOWER)
            state.eventStepAt(plan.startsAt + 5f)
            val event = checkNotNull(state.worldEvent)
            state.worldEvent = event.copy(strikes = immutableListOf(EventStrikeProjection(x, y, meteor.radius, state.elapsed - 1f, state.elapsed)))
            state.worldEventNextPulseAt = Float.MAX_VALUE
            state.updateWorldEvents(MutableGameState.FIXED_STEP)
            assertTrue(checkNotNull(state.worldEvent).strikes.isEmpty(), "A landed strike leaves the list")
        }
        val reach = meteor.radius + arena().coreRadius
        val hit = arena().also { land(it, it.coreX + reach, it.coreY) }
        val fullHp = hit.maxHp
        assertEquals(expectedHpAfterBlast(hit.elapsed, meteor.coreDamage), hit.hp, 1e-3f, "Inside its ring at the edge: the meteor's damage")
        assertTrue(hit.hp < fullHp)
        val missed = arena().also { land(it, it.coreX + reach + 0.01f, it.coreY) }
        assertEquals(fullHp, missed.hp, "Just outside its ring: nothing")
        val dashing = arena().also { it.dashPhaseTime = 1f; land(it, it.coreX, it.coreY) }
        assertEquals(fullHp, dashing.hp, "A Dash phase slips under it")

        val state = arena()
        val x = state.coreX + 400f
        val y = state.coreY
        val inside = state.addEnemyForTesting(x + meteor.radius + 16f, y, radius = 17f).apply { vx = -100f; vy = 30f }
        val outside = state.addEnemyForTesting(x - meteor.radius - 18f, y, radius = 17f)
        val herald = state.addEnemyForTesting(x, y + 20f, hp = 5_000f, radius = 30f, type = EnemyType.HERALD)
        val fragile = state.addEnemyForTesting(x, y - 20f, hp = 1f)
        land(state, x, y)
        val damage = meteor.enemyDamageScale * state.spawnIntegrity(EnemyType.DRIFTER)
        assertEquals(1_000f - damage, inside.hp, 1e-3f, "Damage in Drifter integrities")
        assertEquals(meteor.pushSpeed, inside.vx, 1e-3f, "Pushed straight out at the push speed")
        assertEquals(30f, inside.vy, 1e-3f, "Its sideways motion stays")
        assertEquals(1_000f, outside.hp)
        assertEquals(0f, outside.vx)
        assertTrue(herald.hp < 5_000f, "A boss-class enemy takes the meteor")
        assertEquals(0f, herald.vx, "but is not pushed")
        assertEquals(0f, herald.vy)
        assertTrue(fragile.hp <= 0f)
        assertFalse(fragile.killClausesEligible, "The meteor fires no kill clause")
        assertEquals(1, state.pickups.count { it.type == PickupType.DATA && it.x == x && it.y == y }, "One Data at the centre")
    }

    @Test
    fun aDataStormRainsDataAroundTheHeadingAndEveryKillPaysMore() {
        val storm = policy.dataStorm
        val state = arena()
        val plan = state.planOnly(WorldEventKind.DATA_STORM)
        state.setVelocityForTesting(0f, -300f)
        val heading = atan2(-300f, 0f)
        state.runEventsUntil(plan.endsAt + 1f)
        // One Data every interval for the whole storm: 30 s / 0.6 s.
        assertEquals(50, state.dataPickups())
        for (pickup in state.pickups) {
            val dx = pickup.x - state.coreX
            val dy = pickup.y - state.coreY
            val distance = sqrt(dx * dx + dy * dy)
            assertTrue(distance >= storm.minDistance - 0.01f && distance <= storm.maxDistance + 0.01f, "Distance $distance")
            var off = atan2(dy, dx) - heading
            while (off > TAU / 2f) off -= TAU
            while (off < -TAU / 2f) off += TAU
            assertTrue(abs(off) <= storm.angleSpread / 2f + 1e-4f, "Within the heading's spread")
        }

        /** Data a 1-integrity Drifter's kill leaves at [time] while a data storm is planned at 10 s. */
        fun killData(time: Float, storm: Boolean = true): Int {
            val run = arena()
            if (storm) run.planOnly(WorldEventKind.DATA_STORM) else run.worldEventPlans = immutableListOf()
            run.eventStepAt(time)
            run.worldEventNextPulseAt = Float.MAX_VALUE
            run.pickups.clear()
            val drifter = run.addEnemyForTesting(run.coreX + 300f, run.coreY, hp = 1f)
            run.dealHit(drifter, 10f, HitSource.WEAPON, canCrit = false)
            run.sweepKills()
            if (storm && time >= plan.startsAt && time < plan.endsAt) {
                assertEquals(1, run.pickups.count { it.type == PickupType.DATA && it.x == drifter.x && it.y == drifter.y - 16f })
            }
            return run.dataPickups()
        }
        val plain = killData(20f, storm = false)
        assertEquals(plain + storm.killBonusData, killData(20f), "A kill during the storm")
        assertEquals(plain, killData(plan.startsAt.nextDown()), "Not during its telegraph")
        assertEquals(plain, killData(plan.endsAt), "Not once it is over")
    }

    @Test
    fun aGravityStormBlowsATurningWindThatRampsInAndOut() {
        val gravity = policy.gravity
        val state = arena()
        val plan = state.planOnly(WorldEventKind.GRAVITY_STORM)
        state.eventStepAt(plan.telegraphAt)
        val base = checkNotNull(state.worldEvent).windAngle
        assertTrue(base in 0f..TAU)
        assertEquals(0f, checkNotNull(state.worldEvent).windStrength)

        val step = MutableGameState.FIXED_STEP

        /** The wind's ramp over one step at [time] seconds into the storm, checked on the Core and the enemies. */
        fun rampAt(time: Float): Float {
            val drifter = state.addEnemyForTesting(state.coreX + 300f, state.coreY)
            val herald = state.addEnemyForTesting(state.coreX - 300f, state.coreY, type = EnemyType.HERALD)
            val corpse = state.addEnemyForTesting(state.coreX, state.coreY + 300f).apply { dead = true }
            state.setVelocityForTesting(0f, 0f)
            state.eventStepAt(plan.startsAt + time)
            val angle = base + gravity.turnRate * time
            val ramp = sqrt(state.velocityX * state.velocityX + state.velocityY * state.velocityY) / (gravity.coreAcceleration * step)
            assertEquals(cos(angle) * gravity.coreAcceleration * ramp * step, state.velocityX, 1e-4f, "The Core goes with the wind")
            assertEquals(sin(angle) * gravity.coreAcceleration * ramp * step, state.velocityY, 1e-4f)
            assertEquals(cos(angle) * gravity.enemyAcceleration * ramp * step, drifter.vx, 1e-4f, "and so do enemies, less")
            assertEquals(sin(angle) * gravity.enemyAcceleration * ramp * step, drifter.vy, 1e-4f)
            assertEquals(0f, herald.vx, "A boss-class enemy stands its ground")
            assertEquals(0f, herald.vy)
            assertEquals(0f, corpse.vx)
            state.enemies.clear()
            return ramp
        }
        assertEquals(0.5f, rampAt(gravity.rampSeconds / 2f), 1e-3f, "Half way up the ramp")
        assertEquals(1f, rampAt(gravity.rampSeconds), 1e-3f, "Full strength after the ramp")
        assertEquals(1f, rampAt(12f), 1e-3f)
        // The projection took this step's wind on its sample clock, and never lags more than one sample behind.
        val sampled = checkNotNull(state.worldEvent)
        assertEquals(base + gravity.turnRate * 12f, sampled.windAngle, 1e-3f)
        assertEquals(1f, sampled.windStrength, 1e-3f)
        state.runEventsUntil(plan.startsAt + 14f) {
            val event = checkNotNull(state.worldEvent)
            val exact = base + gravity.turnRate * (state.elapsed - plan.startsAt)
            assertTrue(abs(event.windAngle - exact) <= gravity.turnRate * GRAVITY_WIND_SAMPLE_SECONDS + 1e-3f)
        }
        assertEquals(0.5f, rampAt(gravity.durationSeconds - gravity.rampSeconds / 2f), 1e-3f, "Half way down")
        // At the start of the storm the wind has no strength yet.
        val fresh = arena()
        val freshPlan = fresh.planOnly(WorldEventKind.GRAVITY_STORM)
        fresh.eventStepAt(freshPlan.startsAt)
        assertEquals(0f, fresh.velocityX)
        assertEquals(0f, fresh.velocityY)
    }

    @Test
    fun anIonStormChargesItsZonesAndShocksACoreAtTheirHeart() {
        val ion = policy.ion
        val state = arena()
        val plan = state.planOnly(WorldEventKind.ION_STORM)
        state.eventStepAt(plan.startsAt)
        val armed = checkNotNull(state.worldEvent).strikes.single()
        val distance = sqrt(distanceSquared(armed.x, armed.y, state.coreX, state.coreY))
        assertTrue(distance >= ion.minDistance - 0.01f && distance <= ion.maxDistance + 0.01f, "Distance $distance")
        assertEquals(ion.radius, armed.radius)
        assertEquals(ion.armSeconds, armed.strikesAt - armed.armedAt, TIME_TOLERANCE)

        /** Lands one ion strike at [x], [y] on [run] and returns the visual cues it sent. */
        fun land(run: MutableGameState, x: Float, y: Float): List<VisualFxCue> {
            val runPlan = run.planOnly(WorldEventKind.ION_STORM)
            run.eventStepAt(runPlan.startsAt + 5f)
            run.takeVisualFxCues()
            run.worldEvent = checkNotNull(run.worldEvent).copy(
                strikes = immutableListOf(EventStrikeProjection(x, y, ion.radius, run.elapsed - 1f, run.elapsed)),
            )
            run.worldEventNextPulseAt = Float.MAX_VALUE
            run.updateWorldEvents(MutableGameState.FIXED_STEP)
            return run.takeVisualFxCues()
        }
        val run = arena()
        val x = run.coreX + 300f
        val y = run.coreY
        val inside = run.addEnemyForTesting(x + ion.radius + 10f, y, radius = 17f)
        val outside = run.addEnemyForTesting(x - ion.radius - 18f, y, radius = 17f)
        assertTrue(run.breakThreshold(StatusElement.ION, inside) > ion.ionStacks, "Three stacks stay below the Ion Break")
        val cues = land(run, x, y)
        val damage = ion.enemyDamageScale * run.spawnIntegrity(EnemyType.DRIFTER)
        assertEquals(ion.ionStacks, stacks(inside, StatusElement.ION))
        assertEquals(1_000f - damage, inside.hp, 1e-3f)
        assertEquals(0, stacks(outside, StatusElement.ION))
        assertEquals(1_000f, outside.hp)
        val bolts = cues.filterIsInstance<VisualFxCue.WeaponArcAdded>()
        assertEquals(2, bolts.size, "Two bolts fall on the zone")
        bolts.forEach { assertEquals(x, it.toX); assertEquals(y, it.toY); assertTrue(it.fromY < y) }

        val shocked = arena().also { land(it, it.coreX + ion.coreRadius, it.coreY) }
        val fullHp = shocked.maxHp
        assertEquals(expectedHpAfterBlast(shocked.elapsed, ion.coreDamage), shocked.hp, 1e-3f, "A Core at the heart's edge takes the shock")
        assertTrue(shocked.hp < fullHp)
        val spared = arena().also { land(it, it.coreX + ion.coreRadius + 0.01f, it.coreY) }
        assertEquals(fullHp, spared.hp, "Just beyond it: nothing")
        val dashing = arena().also { it.dashPhaseTime = 1f; land(it, it.coreX, it.coreY) }
        assertEquals(fullHp, dashing.hp, "A Dash phase slips under it")
    }

    @Test
    fun aSolarFlareCoolsTheCoreWarmsItsWeaponsAndSweepsHeat() {
        val solar = policy.solar
        val state = arena()
        val plan = state.planOnly(WorldEventKind.SOLAR_FLARE)
        val near = state.addEnemyForTesting(state.coreX + solar.sweepRadius - 20f, state.coreY)
        val far = state.addEnemyForTesting(state.coreX - solar.sweepRadius - 40f, state.coreY)
        assertTrue(state.breakThreshold(StatusElement.THERMAL, near) > solar.sweepStacks)
        state.eventStepAt(plan.startsAt)
        val source = BuffSource.worldEvent(WorldEventKind.SOLAR_FLARE)
        val cooling = state.buffs.single { it.sourceKey == source && it.stat == BuffStat.COOLING }
        val weapon = state.buffs.single { it.sourceKey == source && it.stat == BuffStat.WEAPON }
        assertEquals(-solar.coolingPenalty, cooling.amountPerStack)
        assertEquals(solar.weaponBonus, weapon.amountPerStack)
        assertEquals(state.elapsed + solar.buffSeconds, cooling.expiresAt)
        assertEquals(1, cooling.stacks)
        assertEquals(solar.sweepStacks, stacks(near, StatusElement.THERMAL), "The first sweep heats what is near")
        assertEquals(0, stacks(far, StatusElement.THERMAL))
        assertEquals(plan.startsAt, checkNotNull(state.worldEvent).sweepStartedAt)

        // The buffs refresh on every pulse and never stack; the next sweep waits for its interval.
        state.runEventsUntil(plan.startsAt + solar.sweepIntervalSeconds - 0.1f)
        val refreshed = state.buffs.single { it.sourceKey == source && it.stat == BuffStat.COOLING }
        assertEquals(1, refreshed.stacks)
        assertTrue(refreshed.expiresAt > state.elapsed)
        assertEquals(plan.startsAt, checkNotNull(state.worldEvent).sweepStartedAt)
        val first = checkNotNull(state.worldEvent).sweepAngle
        val late = state.addEnemyForTesting(state.coreX, state.coreY + 300f)
        // The next sweep heats on its own time, between two buff pulses (7.5 s is not on the 1 s pulse clock).
        state.runEventsUntil(plan.startsAt + solar.sweepIntervalSeconds + MutableGameState.FIXED_STEP)
        assertEquals(plan.startsAt + solar.sweepIntervalSeconds, checkNotNull(state.worldEvent).sweepStartedAt)
        assertTrue(checkNotNull(state.worldEvent).sweepAngle != first, "Each sweep rolls its own angle")
        assertEquals(solar.sweepStacks, stacks(late, StatusElement.THERMAL))
        assertEquals(0, stacks(far, StatusElement.THERMAL))
        // Once it is over, nothing renews the buffs.
        state.runEventsUntil(plan.endsAt + solar.buffSeconds + 0.1f)
        state.expireBuffs()
        assertTrue(state.buffs.none { it.sourceKey == source })
    }

    @Test
    fun strikesFollowAWorldRebase() {
        val state = arena()
        val plan = state.planOnly(WorldEventKind.METEOR_SHOWER)
        state.eventStepAt(plan.startsAt)
        val before = checkNotNull(state.worldEvent).strikes.single()
        val shift = 260_000f
        state.coreX = shift
        state.rebaseWorldIfNeeded()
        val after = checkNotNull(state.worldEvent).strikes.single()
        assertEquals(before.x - shift, after.x)
        assertEquals(before.y, after.y)
        assertEquals(before.strikesAt, after.strikesAt)
    }

    @Test
    fun theArchitectEndsTheEventSilentlyAndNoLaterPlanStarts() {
        val state = arena()
        val first = state.planOnly(WorldEventKind.METEOR_SHOWER)
        val second = WorldEventPlan(WorldEventKind.SOLAR_FLARE, 200f, 203f, 233f)
        state.worldEventPlans = immutableListOf(first, second)
        state.eventStepAt(first.startsAt + 2f)
        assertNotNull(state.worldEvent)
        state.message = "THE ARCHITECT"
        state.bossSpawned = true
        state.eventStepAt(first.startsAt + 3f)
        assertNull(state.worldEvent, "The event ends")
        assertEquals("THE ARCHITECT", state.message, "silently")
        assertEquals(1, state.nextWorldEvent)
        state.eventStepAt(second.telegraphAt + 1f)
        assertNull(state.worldEvent, "No event during the Architect")
        assertEquals(2, state.nextWorldEvent, "The passed plan is consumed")
    }

    @Test
    fun anotherWorldLayerEndsTheEventSilentlyAndSkipsWhatItMissed() {
        val state = arena()
        val first = state.planOnly(WorldEventKind.ION_STORM)
        val second = WorldEventPlan(WorldEventKind.DATA_STORM, 100f, 103f, 133f)
        val third = WorldEventPlan(WorldEventKind.SOLAR_FLARE, 200f, 203f, 233f)
        state.worldEventPlans = immutableListOf(first, second, third)
        state.eventStepAt(first.startsAt + 2f)
        assertNotNull(state.worldEvent)
        state.message = "RIFT"
        state.worldLayer = WorldLayer.ZENITH
        state.eventStepAt(first.startsAt + 3f)
        assertNull(state.worldEvent)
        assertEquals("RIFT", state.message)
        assertEquals(1, state.nextWorldEvent)
        // The second plan's telegraph passes during the visit: it is consumed, not started late on the return.
        state.eventStepAt(second.telegraphAt + 1f)
        state.worldLayer = WorldLayer.BASE
        state.eventStepAt(second.startsAt + 5f)
        assertNull(state.worldEvent)
        assertEquals(2, state.nextWorldEvent)
        state.eventStepAt(third.telegraphAt)
        assertEquals(WorldEventKind.SOLAR_FLARE, state.worldEvent?.kind, "The next plan runs as planned")

        // The layer switch's own hook ends the event in progress silently.
        state.message = "SWITCH"
        state.endWorldEventForLayerChange()
        assertNull(state.worldEvent)
        assertEquals("SWITCH", state.message)
        assertEquals(3, state.nextWorldEvent)
        state.endWorldEventForLayerChange()
        assertEquals(3, state.nextWorldEvent, "Without an event the hook consumes nothing")
    }

    @Test
    fun theQuietArenaClearsTheWorldEvents() {
        val state = arena(seed = 77)
        val plan = state.worldEventPlans.first()
        state.eventStepAt(plan.startsAt + 1f)
        assertNotNull(state.worldEvent)
        state.quietArenaForTesting()
        assertNull(state.worldEvent)
        assertTrue(state.worldEventPlans.isEmpty())
        state.runEventsUntil(content.tempo.bossAtSeconds)
        assertNull(state.worldEvent)
    }

    private companion object {
        const val SEEDS = 2_000
        const val TIME_TOLERANCE = 1e-3f
    }
}
