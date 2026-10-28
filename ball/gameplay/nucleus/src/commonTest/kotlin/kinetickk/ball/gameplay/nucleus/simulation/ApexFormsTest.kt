// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.model.ApexCell
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime
import kinetickk.ball.gameplay.nucleus.model.ObstacleField
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.Polarity
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.WeaponRuntime
import kinetickk.ball.gameplay.nucleus.model.weaponTaken
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The twelve Apex forms (combat-final §5.3): each amplifies its mechanic on ordinary enemies, keeps the base
 * numbers on boss-class enemies (they take only Apex strikes) and erases bolts only within its budget.
 */
class ApexFormsTest {
    @Test
    fun perihelionChargesOnHalfTheArcCapturesBoltsOnReleaseAndFiresThemBackAfterwards() {
        // Half a radian of turning charges nothing at the base arc and charges Orbit during Perihelion.
        fun charge(apex: Boolean): Float {
            val state = arena(CoreShape.ORB)
            if (apex) state.unlockNow().also { state.castNow() }
            repeat(100) { index ->
                val angle = index * 0.005f
                state.moveAt(cos(angle) * 300f, sin(angle) * 300f)
            }
            return state.characterRuntime.charge
        }
        assertEquals(0f, charge(apex = false))
        assertTrue(charge(apex = true) > 0f, "Perihelion charges on half the arc")

        val state = arena(CoreShape.ORB)
        state.unlockNow()
        state.castNow()
        val struck = state.dummy(x = state.coreX + 40f)
        repeat(30) { state.hostileBolt(state.coreX - 100f + it * 5f, state.coreY + 50f) }
        val far = state.hostileBolt(state.coreX + 400f, state.coreY)
        state.characterRuntime.charge = 1f
        state.onCharacterPrimaryHit(struck)
        assertEquals(24f, state.skillRuntime.apex(ApexCell.HELD), "the bolt budget holds 24")
        assertEquals(6, state.projectiles.count { it.hostile && !it.spent } - 1)
        assertFalse(far.spent)

        // Nothing flies while the activation runs; afterwards one held shot every 0.1 s.
        state.projectiles.clear()
        state.dummy(x = state.coreX - 300f)
        state.stepFor(1f)
        assertEquals(0, state.apexShots().size)
        state.stepFor(state.skillRuntime.apex(ApexCell.ACTIVE_UNTIL) - state.elapsed)
        state.stepFor(0.95f)
        val shots = state.apexShots()
        assertEquals(10, shots.size, "one shot per 0.1 s")
        assertEquals(24f - shots.size, state.skillRuntime.apex(ApexCell.HELD))
        // 45 × ability power when each flew (the power grows a little with run time).
        assertTrue(shots.all { kotlin.math.abs(it.damage / (45f * state.abilityPower()) - 1f) < 0.01f }, "held shot damage")
    }

    @Test
    fun perihelionHoldsItsShotsUntilAShiftingArchitectReturns() {
        val state = arena(CoreShape.ORB)
        state.unlockNow()
        state.castNow()
        state.skillRuntime.setApex(ApexCell.HELD, 5f)
        val boss = state.testArchitect()
        boss.shiftUntil = state.skillRuntime.apex(ApexCell.ACTIVE_UNTIL) + 3f
        state.stepFor(state.skillRuntime.apex(ApexCell.ACTIVE_UNTIL) - state.elapsed + MutableGameState.FIXED_STEP)
        assertEquals(boss.shiftUntil + 0.5f, state.skillRuntime.apex(ApexCell.HELD_RELEASE_AT), 1e-4f)
        state.stepFor(3.4f)
        assertEquals(5f, state.skillRuntime.apex(ApexCell.HELD))
        state.stepFor(0.2f)
        assertTrue(state.apexShots().isNotEmpty(), "the release starts once the shift is half a second over")
    }

    @Test
    fun perihelionKeepsTheBaseOrbitCadenceAgainstABoss() {
        // A steady carve at 300 u/s turning 0.012 rad per step.
        val state = arena(CoreShape.ORB)
        state.unlockNow()
        state.castNow()
        val boss = state.testArchitect()
        var step = 0
        fun carveUntil(limit: Int, done: () -> Boolean): Int {
            var steps = 0
            while (!done() && steps < limit) {
                val angle = step++ * 0.012f
                state.moveAt(cos(angle) * 300f, sin(angle) * 300f)
                steps++
            }
            return steps
        }
        fun hitBoss(): Float {
            boss.x = state.coreX + 100f
            boss.y = state.coreY
            val before = boss.hp
            state.onCharacterPrimaryHit(boss)
            return before - boss.hp
        }

        // Perihelion's half arc charges Orbit first: that release skips the boss and leaves the base charge running.
        carveUntil(1_000) { state.orbitCharged }
        val baseCharge = state.skillRuntime.apex(ApexCell.AUX)
        assertTrue(baseCharge in 0.5f..0.99f, "the base rules have not charged Orbit yet: $baseCharge")
        assertEquals(0f, hitBoss(), "a release on Perihelion's own charge skips the boss")
        assertEquals(baseCharge, state.skillRuntime.apex(ApexCell.AUX), "the skipped boss leaves the base charge")

        // The rest of the base arc charges Orbit again (Perihelion's own charge alone would need a half arc and π more),
        // and that release reaches the boss where a base release would, spending the base charge.
        val steps = carveUntil(1_000) { state.orbitCharged }
        assertTrue(steps * 0.012f <= (1f - baseCharge) * PI.toFloat() + 0.02f, "charged after ${steps * 0.012f} rad")
        assertEquals(1f, state.skillRuntime.apex(ApexCell.AUX))
        assertTrue(hitBoss() > 0f, "the base-cadence release reaches the boss")
        assertEquals(0f, state.skillRuntime.apex(ApexCell.AUX))
    }

    @Test
    fun monolithRestoresFacetsRegrowsThemFastAndRefractsNegatedHits() {
        val state = arena(CoreShape.PRISM)
        state.characterRuntime.facets = 1
        state.unlockNow()
        state.castNow()
        assertEquals(4, state.intactFacets)
        val ordinary = state.dummy(x = state.coreX - 300f)
        val boss = state.testArchitect()
        assertEquals(1.4f, state.characterImpactMultiplier(ordinary))
        assertEquals(FACET_IMPACT_BONUS, state.characterImpactMultiplier(boss), "a boss meets the base bonus")

        // A hit no longer pauses regrowth, and a facet regrows in 2 s instead of 6.
        state.characterRuntime.facets = 3
        state.characterRuntime.facetPauseUntil = state.elapsed + 5f
        state.stepFor(2.05f)
        assertEquals(4, state.intactFacets)

        // A negated hit fires a refraction at the boss while strikes remain.
        assertTrue(state.spendFacet(10f))
        val refraction = state.apexShots().single()
        assertEquals(90f * state.abilityPower(), refraction.damage)
        assertTrue(refraction.vx > 0f, "toward the boss")
        assertEquals(15f, state.skillRuntime.apex(ApexCell.BOLTS_LEFT))
        state.skillRuntime.setApex(ApexCell.STRIKES_LEFT, 0f)
        state.characterRuntime.facets = 4
        assertTrue(state.spendFacet(10f))
        assertTrue(state.apexShots().last().vx < 0f, "toward the ordinary enemy once strikes are spent")
        state.skillRuntime.setApex(ApexCell.BOLTS_LEFT, 0f)
        assertTrue(state.spendFacet(10f))
        assertEquals(2, state.apexShots().size, "no refraction past the budget")
    }

    @Test
    fun vivisectMakesDashesFreeSpeedsPuncturesAndSpreadsMarksButStrikesBossesAtTheBaseInterval() {
        val state = arena(CoreShape.SHARD)
        state.unlockNow()
        state.castNow()
        state.heat = 0f
        state.performDash()
        assertEquals(0f, state.heat, "the first Dash is free")
        state.elapsed += 0.1f
        state.performDash()
        val cost = state.heat
        assertTrue(cost > 0f, "a Dash inside the free gap pays heat")
        state.elapsed += 0.35f
        state.performDash()
        assertEquals(cost, state.heat, "free again 0.35 s later")

        val target = state.dummy(x = state.coreX + 100f, hp = 10_000f)
        val near = state.dummy(x = state.coreX + 300f, hp = 10_000f)
        val far = state.dummy(x = state.coreX + 500f, hp = 10_000f)
        target.characterMarkTime = 3f
        state.characterRuntime.punctureReadyAt = 0f
        state.onCharacterPrimaryHit(target)
        assertEquals(10_000f - PUNCTURE_DAMAGE * state.abilityPower() * target.weaponTaken, target.hp, 1e-2f)
        assertEquals(state.elapsed + 0.12f, state.characterRuntime.punctureReadyAt, 1e-5f)
        assertTrue(near.characterMarkTime > 0f, "the mark spreads to the nearest unmarked enemy within 260")
        assertEquals(0f, far.characterMarkTime)

        val boss = state.testArchitect(x = state.coreX - 200f)
        boss.characterMarkTime = 3f
        state.elapsed += 0.12f
        assertFalse(state.tryPuncture(boss, 1f), "a boss waits the base interval after any puncture")
        state.elapsed += 0.13f
        assertTrue(state.tryPuncture(boss, 1f))
        assertEquals(3_000f - state.architectHitCap(boss), boss.hp, 1e-2f)
        assertEquals(state.apexDefinition.bossStrikes - 1, state.apexStrikesLeft)
        assertEquals(state.elapsed + 0.25f, state.characterRuntime.punctureReadyAt, 1e-5f)
    }

    @Test
    fun coronaLocksTheHaloBurnsHotterErasesBoltsInTheBandAndBurstsOnItsOwnClock() {
        val state = arena(CoreShape.RING)
        val inside = state.dummy(x = state.coreX - 200f, hp = 10_000f)
        val outside = state.dummy(x = state.coreX, y = state.coreY - 400f, hp = 10_000f)
        val boss = state.testArchitect(x = state.coreX + 250f)
        state.unlockNow()
        state.castNow()
        val inBand = state.hostileBolt(state.coreX, state.coreY + 165f)
        val within = state.hostileBolt(state.coreX, state.coreY + 90f)
        state.stepFor(MutableGameState.FIXED_STEP)
        assertEquals(165f, state.characterRuntime.haloRadius)
        val power = state.abilityPower()
        assertEquals(10_000f - 32f * power * inside.weaponTaken, inside.hp, 1e-2f, "the burst at cast")
        assertEquals(10_000f, outside.hp)
        val strikes = state.apexDefinition.bossStrikes
        assertEquals(strikes - 1, state.apexStrikesLeft, "the burst strikes the boss once")
        assertTrue(inBand.spent)
        assertFalse(within.spent)
        assertEquals(31f, state.skillRuntime.apex(ApexCell.BOLTS_LEFT))
        state.stepFor(1.45f)
        assertEquals(strikes - 1, state.apexStrikesLeft, "the next burst waits 1.5 s")
        state.stepFor(0.1f)
        assertEquals(strikes - 2, state.apexStrikesLeft)

        // The band burns ordinary enemies at 36/s; a boss keeps the base 12/s.
        state.skillRuntime.setApex(ApexCell.AUX, Float.MAX_VALUE)
        val banded = state.dummy(x = state.coreX - 165f, hp = 10_000f)
        val bossHp = boss.hp
        boss.x = state.coreX + 165f
        state.moveAt(100f, 0f)
        assertEquals(10_000f - 36f * MutableGameState.FIXED_STEP * power * banded.weaponTaken, banded.hp, 1e-3f)
        assertEquals(bossHp - 12f * MutableGameState.FIXED_STEP * power * boss.weaponTaken, boss.hp, 1e-3f)
    }

    @Test
    fun mirrorStanceParriesItsBudgetOfHitsWhileBrakingAndReturnsThemAtFullEdge() {
        val state = arena(CoreShape.DIAMOND)
        val ordinary = state.dummy(x = state.coreX - 300f)
        val boss = state.testArchitect(x = state.coreX + 2_000f)
        state.unlockNow()
        state.castNow()
        assertFalse(state.tryCharacterParry(state.coreX + 100f, state.coreY, -400f, 0f, projectile = true), "only while braking")
        state.setBrake(true)
        assertTrue(state.tryCharacterParry(state.coreX + 100f, state.coreY, -400f, 0f, projectile = true))
        val returned = state.apexShots().single()
        assertEquals(65f * state.abilityPower() * 1.4f, returned.damage, 1e-3f)
        assertEquals(0f, state.characterRuntime.parryWindow, "no window is needed or spent")
        assertEquals(3f, state.skillRuntime.apex(ApexCell.BOLTS_LEFT))
        repeat(3) { assertTrue(state.tryCharacterParry(state.coreX + 100f, state.coreY, -400f, 0f)) }
        assertFalse(state.tryCharacterParry(state.coreX + 100f, state.coreY, -400f, 0f), "then the normal rules")

        // Edge is held full against ordinary enemies only.
        state.characterRuntime.edge = 1
        assertEquals(1.4f, state.characterDamageMultiplier(ordinary), 1e-5f)
        assertEquals(1.08f, state.characterDamageMultiplier(boss), 1e-5f)
        assertTrue(state.edgeCriticalReady(ordinary))
        assertFalse(state.edgeCriticalReady(boss))
        state.spendEdgeCritical(ordinary)
        assertEquals(1, state.characterRuntime.edge, "the locked Edge is not spent")
    }

    @Test
    fun hypercubeFoldsACageThatErasesCrossingBoltsSlowsTheInsideAndPulses() {
        val state = arena(CoreShape.TESSERACT)
        val inside = state.dummy(x = state.coreX + 50f, hp = 10_000f)
        val onEdge = state.dummy(x = state.coreX + 235f, hp = 10_000f)
        val elite = state.addEnemyForTesting(state.coreX - 60f, state.coreY, hp = 10_000f, type = EnemyType.ELITE)
        val boss = state.testArchitect(x = state.coreX + 220f + 60f)
        state.unlockNow()
        state.castNow()
        val runtime = state.characterRuntime
        assertEquals(4, runtime.latticeCount)
        assertEquals(state.coreX + 220f, runtime.latticeX(1))
        assertEquals(0.5f, state.hypercubeMotionScale(inside))
        assertEquals(1f, state.hypercubeMotionScale(elite), "Elites are not ordinary")
        assertEquals(1f, state.hypercubeMotionScale(onEdge))

        // The step moves them 50 and 20 to the right: one across the right edge (x + 220), one inside.
        val crossing = state.hostileBolt(state.coreX + 200f, state.coreY + 30f).apply { vx = 50f / MutableGameState.FIXED_STEP }
        val held = state.hostileBolt(state.coreX + 100f, state.coreY).apply { vx = 20f / MutableGameState.FIXED_STEP }
        state.stepFor(MutableGameState.FIXED_STEP)
        assertTrue(crossing.spent)
        assertFalse(held.spent)

        // The first pulse comes 0.6 s after the fold.
        state.stepFor(0.6f)
        val pulse = (7f + 3.5f * 4) * state.abilityPower()
        assertEquals(10_000f - pulse * onEdge.weaponTaken, onEdge.hp, 1e-2f)
        assertEquals(10_000f - pulse * 1.5f * inside.weaponTaken, inside.hp, 1e-2f)
        assertEquals(state.apexDefinition.bossStrikes - 1, state.apexStrikesLeft, "a pulse strikes the boss in reach of the Core")

        // The cage holds while it runs; a folded square clears when it ends.
        state.stepFor(state.skillRuntime.apex(ApexCell.ACTIVE_UNTIL) - state.elapsed + MutableGameState.FIXED_STEP)
        assertEquals(0, runtime.latticeCount)
    }

    @Test
    fun hypercubeKeepsAPlayerLatticeAliveAndHandsItBack() {
        val state = arena(CoreShape.TESSERACT)
        val runtime = state.characterRuntime
        listOf(0f to 0f, 300f to 0f, 150f to 260f).forEachIndexed { index, (x, y) ->
            runtime.latticeNodes[index * 2] = state.coreX + x
            runtime.latticeNodes[index * 2 + 1] = state.coreY + y
        }
        runtime.latticeCount = 3
        runtime.latticeTime = 0.5f
        state.unlockNow()
        state.castNow()
        state.stepFor(4f)
        assertEquals(3, runtime.latticeCount, "nodes do not expire")
        state.stepFor(state.skillRuntime.apex(ApexCell.ACTIVE_UNTIL) - state.elapsed + MutableGameState.FIXED_STEP)
        assertEquals(3, runtime.latticeCount)
        assertEquals(7f, runtime.latticeTime, 0.02f)
    }

    @Test
    fun hypernovaBurstsAtOnceComesFasterKeepsItsMassAndOnlyStrikesBossesOffTheBaseGate() {
        val state = arena(CoreShape.STAR)
        val ordinary = state.dummy(x = state.coreX + 200f, hp = 10_000f)
        val boss = state.testArchitect(x = state.coreX - 250f)
        val bolt = state.hostileBolt(state.coreX + 100f, state.coreY + 100f)
        val runtime = state.characterRuntime
        runtime.stellarMass = 5
        runtime.supernovaReadyAt = state.elapsed + 1f
        state.unlockNow()
        state.castNow()
        val power = state.abilityPower()
        assertEquals(10_000f - (20f + 9f * 24f) * power * ordinary.weaponTaken, ordinary.hp, 1e-1f)
        assertEquals(0, runtime.stellarMass)
        assertEquals(state.elapsed + 1f, runtime.supernovaReadyAt, 1e-5f, "novas come every second")
        assertEquals(5, state.apexStrikesLeft, "the base gate was closed: the boss took a strike")
        assertTrue(bolt.spent)

        runtime.stellarMass = 8
        state.onCharacterDamaged()
        assertEquals(8, runtime.stellarMass, "taking damage sheds nothing")
        state.killEnemyForTesting(EnemyType.DRIFTER, x = state.coreX + 900f)
        assertEquals(11, runtime.stellarMass, "a kill gathers 3")
        state.stepFor(state.skillRuntime.apex(ApexCell.ACTIVE_UNTIL) - state.elapsed + MutableGameState.FIXED_STEP)
        assertEquals(12, runtime.stellarMass, "Mass ends at 12")
    }

    @Test
    fun twinEchoAddsASecondGhostHalfASecondBehindThatStrikesAndSwallowsFaster() {
        val state = arena(CoreShape.SPIRAL)
        state.unlockNow()
        state.castNow()
        repeat(70) { state.moveAt(600f, 0f) }
        assertTrue(state.secondGhostActive)
        assertEquals(state.coreX - 60 * 600f * MutableGameState.FIXED_STEP, state.secondGhostX, 0.05f)
        assertFalse(state.ghostActive, "the first ghost still needs a full second of path")

        // Ordinary enemies on the second ghost's path take its strike; a boss there only a strike of the Apex.
        val ahead = state.secondGhostX + 5 * 600f * MutableGameState.FIXED_STEP
        val ordinary = state.dummy(x = ahead, y = state.coreY, hp = 10_000f)
        val boss = state.testArchitect(x = ahead, y = state.coreY + 40f)
        ordinary.previousX = ordinary.x
        boss.previousX = boss.x
        repeat(8) { state.moveAt(600f, 0f) }
        assertTrue(ordinary.hp < 10_000f)
        assertEquals(0.4f, ordinary.characterMarkTime, 0.1f)
        assertEquals(state.apexDefinition.bossStrikes - 1, state.apexStrikesLeft)
        assertEquals(0f, boss.characterMarkTime, "the first ghost's own gate on the boss is untouched")

        // The first ghost swallows a bolt every 0.5 s while the budget lasts (1.5 s at base).
        repeat(60) { state.moveAt(600f, 0f) }
        assertTrue(state.ghostActive)
        val runtime = state.characterRuntime
        runtime.ghostAbsorbReadyAt = 0f
        val bolt = state.hostileBolt(state.ghostX, state.ghostY).apply { previousX = x; previousY = y }
        state.moveAt(600f, 0f)
        assertTrue(bolt.spent)
        assertEquals(state.elapsed + 0.5f, runtime.ghostAbsorbReadyAt, 1e-4f)
    }

    @Test
    fun magnetarFlipsOnItsOwnClockReachesFartherAndFlingsBoltsBack() {
        val state = arena(CoreShape.DIPOLE)
        val ordinary = state.dummy(x = state.coreX + 400f)
        val boss = state.testArchitect(x = state.coreX - 400f)
        state.unlockNow()
        state.castNow()
        val runtime = state.characterRuntime
        assertEquals(Polarity.ATTRACT, runtime.polarity)
        state.stepFor(MutableGameState.FIXED_STEP)
        assertTrue(ordinary.vx < 0f, "Attract reaches 420 on ordinary enemies")
        assertEquals(0f, boss.vx, "a boss meets the base 300")
        state.stepFor(1f)
        assertEquals(Polarity.REPEL, runtime.polarity)
        assertEquals(0f, runtime.polarityFlipReadyAt, "Magnetar's flips leave the turn gate alone")

        val closing = state.hostileBolt(state.coreX + 200f, state.coreY).apply { vx = -300f }
        val leaving = state.hostileBolt(state.coreX, state.coreY + 200f).apply { vy = 300f }
        state.stepFor(MutableGameState.FIXED_STEP)
        assertTrue(closing.spent)
        assertFalse(leaving.spent)
        val flung = state.apexShots().single()
        assertTrue(flung.vx > 0f && kotlin.math.abs(flung.vy) < 1e-3f, "along the reflected path")
        assertEquals(30f * state.abilityPower(), flung.damage)
    }

    @Test
    fun crescendoPutsEveryMoveOnTheBeatAndBurstsOnEachBeat() {
        val state = arena(CoreShape.PENTAGON)
        state.elapsed = 30 * BEAT_PERIOD + 0.35f
        assertFalse(state.isOnBeat())
        val ordinary = state.dummy(x = state.coreX + 100f, hp = 10_000f)
        val boss = state.testArchitect(x = state.coreX - 120f)
        state.unlockNow()
        state.castNow()
        assertTrue(state.onCharacterDashDeparture(), "an off-beat Dash leaves on the beat")
        assertEquals(0, state.characterRuntime.beatStreak, "only a real beat raises the streak")
        assertEquals(1.3f, state.characterImpactMultiplier(ordinary), 1e-5f)
        assertEquals(1f, state.characterImpactMultiplier(boss), "a boss meets the real streak")

        repeat(8) { state.hostileBolt(state.coreX + 20f * it - 70f, state.coreY + 60f) }
        state.elapsed = 31 * BEAT_PERIOD
        state.stepFor(MutableGameState.FIXED_STEP)
        assertEquals(10_000f - 40f * state.abilityPower() * ordinary.weaponTaken, ordinary.hp, 1e-2f)
        val strikes = state.apexDefinition.bossStrikes
        assertEquals(strikes - 1, state.apexStrikesLeft)
        assertEquals(6, state.projectiles.count { it.hostile && it.spent }, "six bolts per beat")
        assertEquals(66f, state.skillRuntime.apex(ApexCell.BOLTS_LEFT))
        state.stepFor(0.5f)
        assertEquals(strikes - 1, state.apexStrikesLeft, "one burst per beat")
    }

    @Test
    fun totalEclipseRechargesBlinksCutsShotsPullsAndHarderScars() {
        val state = arena(CoreShape.CRESCENT)
        val runtime = state.characterRuntime
        state.unlockNow()
        state.castNow()
        runtime.blinkCharges = 0
        runtime.blinkRecharge = 0f
        state.stepFor(0.51f)
        assertEquals(1, runtime.blinkCharges, "a charge in 0.5 s")

        val onPath = state.hostileBolt(state.coreX + 100f, state.coreY + 20f)
        val offPath = state.hostileBolt(state.coreX + 100f, state.coreY + 90f)
        state.aimAt(state.coreX + 600f, state.coreY)
        val startX = state.coreX
        state.crescentBlink(1f, 0f)
        assertTrue(onPath.spent)
        assertFalse(offPath.spent)

        val pulled = state.dummy(x = startX + 120f, y = state.coreY + 70f, hp = 10_000f)
        val cut = state.dummy(x = startX + 60f, y = state.coreY, hp = 10_000f)
        val boss = state.testArchitect(x = startX + 160f, y = state.coreY + 40f)
        val y = pulled.y
        state.stepFor(0.1f)
        assertTrue(pulled.y < y - 20f, "drawn toward the scar")
        state.stepFor(0.35f)
        assertEquals(10_000f - SCAR_DAMAGE * state.abilityPower() * 1.6f * cut.weaponTaken, cut.hp, 1e-2f)
        assertEquals(state.apexDefinition.bossStrikes - 1, state.apexStrikesLeft, "the scar strikes the boss")
    }

    @Test
    fun swarmQueenFillsTheHiveDartsFasterInterceptsBoltsAndLatchesData() {
        val state = arena(CoreShape.HEXAGON)
        state.unlockNow()
        state.castNow()
        val runtime = state.characterRuntime
        assertEquals(4, runtime.droneCount)
        assertTrue((0 until 4).all { runtime.droneValue(it, CharacterRuntime.DRONE_LIFE) == 12f })

        val ordinary = state.dummy(x = state.coreX + 150f, hp = 10_000f)
        val near = state.hostileBolt(state.coreX, state.coreY + 70f)
        val data = Pickup(PickupType.DATA, state.coreX + 500f, state.coreY).also { state.pickups += it }
        val loose = Pickup(PickupType.DATA, state.coreX + 700f, state.coreY).also { state.pickups += it }
        state.stepFor(0.32f)
        val dart = 18f * state.abilityPower() * ordinary.weaponTaken
        assertEquals(10_000f - 4 * dart, ordinary.hp, 1e-1f, "four drones dart at 18 × AP")
        assertTrue(near.spent, "a drone intercepts the bolt")
        assertTrue(data.attracted)
        assertFalse(loose.attracted)
        state.stepFor(0.36f)
        assertEquals(10_000f - 8 * dart, ordinary.hp, 1e-1f, "again 0.35 s later")

        // A boss target keeps the base interval, and the darts at it are strikes.
        state.enemies.remove(ordinary)
        val boss = state.testArchitect(x = state.coreX + 120f)
        state.stepFor(0.36f)
        val struck = state.apexDefinition.bossStrikes - 4
        assertEquals(struck, state.apexStrikesLeft)
        state.stepFor(0.36f)
        assertEquals(struck, state.apexStrikesLeft, "each drone waits a second after a boss dart")
        assertTrue(boss.hp < 3_000f)
    }

    @Test
    fun coronaAndSwarmQueenNeverFallBelowWhatRefitAlreadyGivesTheBaseMechanic() {
        // Refit 10 widens the Ring's halo to 55 + 140 = 195 u: Corona holds that widest ring, not its own 165.
        val ring = arena(CoreShape.RING).apply { characterUpgradeLevel = 10 }
        val widest = CharacterRuntime.HALO_MIN_RADIUS + ring.haloExtraRadius()
        assertEquals(195f, widest)
        ring.unlockNow()
        ring.castNow()
        ring.stepFor(MutableGameState.FIXED_STEP)
        assertEquals(widest, ring.characterRuntime.haloRadius, "Corona holds the Refit-widened ring")
        assertEquals(1f, ring.characterRuntime.charge, "the halo reads full while Corona holds it")

        // Refit 10 lets a drone live 12 + 4 = 16 s: Swarm Queen's refreshed life is never shorter.
        val hive = arena(CoreShape.HEXAGON).apply { characterUpgradeLevel = 10 }
        assertEquals(16f, hive.droneLifeSeconds())
        hive.unlockNow()
        hive.castNow()
        val runtime = hive.characterRuntime
        assertEquals(CharacterRuntime.MAX_DRONES, runtime.droneCount)
        assertTrue((0 until runtime.droneCount).all { runtime.droneValue(it, CharacterRuntime.DRONE_LIFE) == 16f })
    }

    @Test
    fun aProximityChannelReachesAnArchitectOnItsOrbitButNotBeyondTheApexReach() {
        // The Architect orbits the Core at 300-380 u: a beat, a burst, a nova, a pulse, a ghost, a scar or a Vivisect
        // Dash strikes it there, and nothing past the reach (420 u from the event to its edge).
        val orbit = 400f
        val beyond = APEX_BOSS_REACH + 74f + 20f
        fun strikesFrom(shape: CoreShape, bossDistance: Float, event: MutableGameState.() -> Unit): Int {
            val state = arena(shape)
            state.testArchitect(x = state.coreX - bossDistance)
            state.unlockNow()
            state.castNow()
            state.skillRuntime.setApex(ApexCell.STRIKES_LEFT, 12f)
            state.takeVisualFxCues()
            state.event()
            return 12 - state.apexStrikesLeft
        }
        val events = mapOf<CoreShape, MutableGameState.() -> Unit>(
            CoreShape.PENTAGON to { elapsed = (floor(elapsed / BEAT_PERIOD) + 1f) * BEAT_PERIOD; stepFor(MutableGameState.FIXED_STEP) },
            CoreShape.RING to { skillRuntime.setApex(ApexCell.AUX, 0f); stepFor(MutableGameState.FIXED_STEP) },
            CoreShape.TESSERACT to { skillRuntime.setApex(ApexCell.AUX, 0f); stepFor(MutableGameState.FIXED_STEP) },
            CoreShape.STAR to { stepFor(1f + MutableGameState.FIXED_STEP) },
            CoreShape.SHARD to { heat = 0f; performDash() },
        )
        for ((shape, event) in events) {
            assertEquals(1, strikesFrom(shape, orbit, event), "$shape strikes at $orbit u")
            assertEquals(0, strikesFrom(shape, beyond, event), "$shape does not strike at $beyond u")
        }

        // A strike from afar is drawn as the character's Apex link from the event to the boss.
        val state = arena(CoreShape.RING)
        val boss = state.testArchitect(x = state.coreX - orbit)
        state.unlockNow()
        state.castNow()
        state.stepFor(MutableGameState.FIXED_STEP)
        val link = state.takeVisualFxCues().filterIsInstance<VisualFxCue.WeaponArcAdded>().single { it.style == LinkStyleCode.apex(CoreShape.RING) }
        assertEquals(boss.x, link.toX)
        assertEquals(state.coreX, link.fromX)
    }

    @Test
    fun hypernovaNovasComeEverySecondEvenWithoutMassAndEachStrikesTheBossInReach() {
        val state = arena(CoreShape.STAR)
        // Past the cast nova's own radius (160 + 8 × 24 u), within the Apex reach.
        val boss = state.testArchitect(x = state.coreX - 450f)
        state.unlockNow()
        state.castNow()
        val strikes = state.apexDefinition.bossStrikes
        assertEquals(strikes - 1, state.apexStrikesLeft, "the cast's Supernova")
        assertEquals(0, state.characterRuntime.stellarMass)
        state.stepFor(1f + MutableGameState.FIXED_STEP)
        assertEquals(strikes - 2, state.apexStrikesLeft, "a Mass-less nova a second later still strikes")
        assertEquals(3_000f - 2 * state.architectHitCap(boss), boss.hp, 1e-2f)
    }

    @Test
    fun swarmQueenDronesReachForTheArchitectOnItsOrbitWhileStrikesRemain() {
        val state = arena(CoreShape.HEXAGON)
        val boss = state.testArchitect(x = state.coreX + 400f)
        val ordinary = state.dummy(x = state.coreX - 150f, hp = 10_000f)
        state.unlockNow()
        state.castNow()
        state.skillRuntime.setApex(ApexCell.STRIKES_LEFT, 2f)
        state.stepFor(0.32f)
        assertEquals(0, state.apexStrikesLeft, "two drones dart at the boss beyond their own 260 u")
        assertEquals(3_000f - 2 * state.architectHitCap(boss), boss.hp, 1e-2f)
        assertTrue(ordinary.hp < 10_000f, "the other drones keep to the nearest enemy")
    }

    @Test
    fun totalEclipseScarsAndTwinEchoGhostsStrikeTheArchitectInReach() {
        val crescent = arena(CoreShape.CRESCENT)
        crescent.unlockNow()
        crescent.castNow()
        crescent.aimAt(crescent.coreX + 600f, crescent.coreY)
        val startX = crescent.coreX
        crescent.crescentBlink(1f, 0f)
        crescent.testArchitect(x = startX + 100f, y = crescent.coreY + 350f)
        crescent.stepFor(0.45f)
        assertEquals(crescent.apexDefinition.bossStrikes - 1, crescent.apexStrikesLeft, "a scar 350 u away strikes")

        val spiral = arena(CoreShape.SPIRAL)
        spiral.unlockNow()
        spiral.castNow()
        repeat(70) { spiral.moveAt(600f, 0f) }
        spiral.testArchitect(x = spiral.secondGhostX, y = spiral.coreY + 300f)
        repeat(30) { spiral.moveAt(600f, 0f) }
        assertEquals(spiral.apexDefinition.bossStrikes - 1, spiral.apexStrikesLeft, "the second ghost strikes once per 0.4 s")
        repeat(21) { spiral.moveAt(600f, 0f) }
        assertEquals(spiral.apexDefinition.bossStrikes - 2, spiral.apexStrikesLeft)
    }

    @Test
    fun perihelionWaitsForAnArchitectThatStartsShiftingMidRelease() {
        val state = arena(CoreShape.ORB)
        state.unlockNow()
        state.castNow()
        state.skillRuntime.setApex(ApexCell.HELD, 6f)
        val boss = state.testArchitect(x = state.coreX + 300f)
        state.stepFor(state.skillRuntime.apex(ApexCell.ACTIVE_UNTIL) - state.elapsed + 0.15f)
        assertEquals(2, state.apexShots().size, "the release began")
        boss.shiftUntil = state.elapsed + 8f
        state.stepFor(0.5f)
        assertEquals(4f, state.skillRuntime.apex(ApexCell.HELD), "nothing more flies while it shifts")
        assertEquals(boss.shiftUntil + 0.5f, state.skillRuntime.apex(ApexCell.HELD_RELEASE_AT), 1e-4f)
    }

    @Test
    fun apexShotsAtTheArchitectCutThroughTheAddsOnTheirWay() {
        val state = arena(CoreShape.PRISM)
        val add = state.dummy(x = state.coreX + 150f, hp = 10_000f)
        val boss = state.testArchitect(x = state.coreX + 400f)
        state.unlockNow()
        state.castNow()
        state.fireApexShot(state.coreX, state.coreY, boss, 90f)
        val shot = state.apexShots().single()
        assertEquals(APEX_BOSS_SHOT_PIERCE, shot.pierce)
        repeat(60) {
            shot.previousX = shot.x
            shot.previousY = shot.y
            shot.x += shot.vx * MutableGameState.FIXED_STEP
            shot.y += shot.vy * MutableGameState.FIXED_STEP
            state.resolveProjectileHits()
        }
        assertTrue(add.hp < 10_000f, "the add on the way takes the shot's hit")
        assertEquals(3_000f - state.architectHitCap(boss), boss.hp, 1e-2f, "and the shot still strikes the boss")
        state.fireApexShot(state.coreX, state.coreY, add, 90f)
        assertEquals(0, state.apexShots().last().pierce, "a shot at an ordinary enemy stops at it")
    }

    @Test
    fun monolithFacetsAlsoTakeObstacleCollisions() {
        val state = arena(CoreShape.PRISM)
        state.hp = state.maxHp
        state.takeDamage(10f, kind = IncomingDamage.OBSTACLE)
        assertTrue(state.hp < state.maxHp, "at base no facet cancels a collision")
        state.hp = state.maxHp
        state.unlockNow()
        state.castNow()
        val integrity = state.hp
        state.takeDamage(10f, kind = IncomingDamage.OBSTACLE)
        assertEquals(integrity, state.hp, "a Monolith facet took it")
        assertEquals(3, state.intactFacets)
    }

    private fun arena(shape: CoreShape): MutableGameState = buildTestState(shape = shape).apply {
        obstacleField = ObstacleField.EMPTY
        aimAt(coreX + 300f, coreY)
    }

    /** Fixed steps of the Apex and character systems for [seconds], the Core standing still. */
    private fun MutableGameState.stepFor(seconds: Float) {
        var left = seconds
        while (left > 1e-6f) {
            previousCoreX = coreX
            previousCoreY = coreY
            for (shot in projectiles) {
                shot.previousX = shot.x
                shot.previousY = shot.y
                shot.x += shot.vx * MutableGameState.FIXED_STEP
                shot.y += shot.vy * MutableGameState.FIXED_STEP
            }
            elapsed += MutableGameState.FIXED_STEP
            ageCharacterStepFlags()
            updateApex(MutableGameState.FIXED_STEP)
            updateCharacterRuntime(MutableGameState.FIXED_STEP)
            left -= MutableGameState.FIXED_STEP
        }
    }

    /** One fixed step with the Core moving at ([vx], [vy]). */
    private fun MutableGameState.moveAt(vx: Float, vy: Float) {
        velocityX = vx
        velocityY = vy
        smoothedVelocityX = vx
        smoothedVelocityY = vy
        previousCoreX = coreX
        previousCoreY = coreY
        coreX += vx * MutableGameState.FIXED_STEP
        coreY += vy * MutableGameState.FIXED_STEP
        for (enemy in enemies) {
            enemy.previousX = enemy.x
            enemy.previousY = enemy.y
        }
        elapsed += MutableGameState.FIXED_STEP
        ageCharacterStepFlags()
        updateApex(MutableGameState.FIXED_STEP)
        updateCharacterRuntime(MutableGameState.FIXED_STEP)
    }

    private fun MutableGameState.apexShots(): List<Projectile> = projectiles.filter { it.procKey == WeaponRuntime.PROC_APEX }
}
