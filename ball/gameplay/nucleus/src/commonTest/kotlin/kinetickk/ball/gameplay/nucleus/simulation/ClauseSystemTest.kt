// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DamageBasis
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.foundation.collections.immutableListOf
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Trigger–Condition–Effect dispatch: gates, cooldowns, conditions, proc depth and the kill sweep. */
class ClauseSystemTest {
    @Test
    fun everyNthGateFiresOnTheNthEligibleEventAndRestarts() {
        val state = buildTestState().apply { own("Calibrated Coil") }
        val target = state.dummy()
        val procs = (1..12).filter { _ ->
            val before = target.hp
            state.fireClauses(EffectTrigger.WEAPON_HIT, target, hit = 100f, source = HitSource.WEAPON)
            target.hp < before
        }
        assertEquals(2, procs.size)
        // A HIT-based proc takes the triggering value as is: 60 % of 100.
        assertEquals(target.maxHp - 120f, target.hp, 0.01f)
    }

    @Test
    fun cooldownsGateClausesInRunTime() {
        val state = buildTestState().apply { own("Shock Plate") }
        val target = state.dummy()
        val bystander = state.dummy(x = 450f)
        fun fires(): Boolean {
            val before = bystander.hp
            state.fireClauses(EffectTrigger.IMPACT, target, hit = 50f, source = HitSource.IMPACT)
            return bystander.hp < before
        }
        assertTrue(fires())
        state.elapsed = 0.5f
        assertFalse(fires(), "Shock Plate rests for 0.8 s")
        state.elapsed = 0.8f
        assertTrue(fires())
    }

    @Test
    fun luckShortensLuckGatedClausesButNeverBelowThree() {
        fun procIndices(luck: Float): List<Int> {
            val state = buildTestState().apply {
                own("Jackpot Relay")
                this.luck = luck
            }
            return (1..12).filter {
                val before = state.data
                state.fireClauses(EffectTrigger.KILL, state.dummy(x = 2_000f), hit = 0f)
                state.data > before
            }
        }
        assertEquals(listOf(8), procIndices(luck = 0f))
        assertEquals(listOf(6, 12), procIndices(luck = 0.5f), "One step shorter per 0.25 luck")
        assertEquals(listOf(4, 8, 12), procIndices(luck = 1f))
        assertEquals(listOf(3, 6, 9, 12), procIndices(luck = 4f), "Never shorter than every third")
    }

    @Test
    fun everyNthCountersCountOnlyEventsThatPassConditionsAndCooldowns() {
        val conditioned = buildTestState(content = contentWithClauses("Calibrated Coil",
            EffectClause(EffectTrigger.WEAPON_HIT, immutableListOf(EffectCondition.TargetFrozen),
                immutableListOf(EffectAction.Strike(DamageBasis.HIT, 1f)), everyNth = 3)))
        conditioned.own("Calibrated Coil")
        val frozen = conditioned.dummy().apply { status[StatusCell.FROZEN_UNTIL] = 1_000f }
        val free = conditioned.dummy(x = -400f)
        val order = listOf(frozen, free, free, frozen, free, frozen, free, free, frozen, frozen, frozen)
        val procs = order.indices.filter { index ->
            val target = order[index]
            val before = target.hp
            conditioned.fireClauses(EffectTrigger.WEAPON_HIT, target, hit = 100f, source = HitSource.WEAPON)
            target.hp < before
        }
        // Only the Frozen hits count: the third, sixth Frozen hit, never an ignored one.
        assertEquals(listOf(5, 10), procs)
        assertEquals(free.maxHp, free.hp)

        val cooled = buildTestState(content = contentWithClauses("Calibrated Coil",
            EffectClause(EffectTrigger.WEAPON_HIT, actions = immutableListOf(EffectAction.Strike(DamageBasis.HIT, 1f)),
                cooldownSeconds = 1f, everyNth = 2)))
        cooled.own("Calibrated Coil")
        val target = cooled.dummy()
        val times = listOf(0f, 0.1f, 0.2f, 0.3f, 0.4f, 1.2f, 1.3f)
        val fired = times.filter { time ->
            cooled.elapsed = time
            val before = target.hp
            cooled.fireClauses(EffectTrigger.WEAPON_HIT, target, hit = 100f, source = HitSource.WEAPON)
            target.hp < before
        }
        assertEquals(listOf(0.1f, 1.3f), fired, "Hits during the cooldown never advance the counter")
    }

    @Test
    fun itemStatusApplicationsAreRateBoundAndCarryFractionalStacks() {
        val state = buildTestState().apply { own("Igniter", stacks = 2) }
        val magnitude = state.build.magnitude(state.itemNamed("Igniter").id)
        val perApplication = 2f * magnitude
        assertTrue(perApplication % 1f != 0f, "Two Igniters apply a fractional stack count")
        var landed = 0
        var stacks = 0
        // One impact every fixed step for two seconds, each on a fresh target.
        repeat(240) { index ->
            state.elapsed = index * MutableGameState.FIXED_STEP
            val target = state.dummy(x = 400f + index * 1_000f)
            state.fireClauses(EffectTrigger.IMPACT, target, hit = 10f, source = HitSource.IMPACT)
            val count = target.stacksOf(StatusElement.THERMAL)
            if (count > 0) {
                landed++
                stacks += count
            }
        }
        // At most six applications per second per item, scaled by its stack magnitude.
        val bound = 2f * ITEM_STATUS_RATE * magnitude
        assertTrue(landed <= ceil(bound).toInt() && landed >= floor(bound).toInt() - 1, "landed=$landed bound=$bound")
        // Fractions accumulate across applications instead of rounding down each time.
        val exact = floor(landed * perApplication).toInt()
        assertTrue(stacks in exact - 1..exact, "stacks=$stacks exact=$exact")
        assertTrue(stacks > landed * floor(perApplication).toInt())
    }

    @Test
    fun brakeReleaseFiresOnlyAfterTheBrakeWasHeldLongEnough() {
        val state = buildTestState().apply { own("Stillpoint") }
        val near = state.dummy(x = 100f)
        fun holdThenRelease(seconds: Float): Int {
            near.status[StatusCell.STACKS + StatusElement.CRYO.ordinal] = 0f
            state.braking = true
            var held = 0f
            while (held < seconds - 0.0001f) {
                state.elapsed += MutableGameState.FIXED_STEP
                state.updateBuildEdges(MutableGameState.FIXED_STEP)
                held += MutableGameState.FIXED_STEP
            }
            state.braking = false
            state.elapsed += MutableGameState.FIXED_STEP
            state.updateBuildEdges(MutableGameState.FIXED_STEP)
            return near.stacksOf(StatusElement.CRYO)
        }
        assertEquals(0, holdThenRelease(0.3f), "A tap is not a brake release")
        assertEquals(3, holdThenRelease(0.4f))
        // The hold restarts with every press.
        assertEquals(0, holdThenRelease(0.2f))
    }

    @Test
    fun shieldBreakFiresOnlyForAShieldThatReachedHalfAndThenRestsThreeSeconds() {
        val state = buildTestState().apply { own("Capacitor Aegis"); own("Shield Cell") }
        val capacity = state.shieldCapacity()
        assertTrue(capacity > 0f)
        val near = state.dummy(x = 100f)
        fun breakShieldAt(time: Float, fill: Float): Boolean {
            state.elapsed = time
            state.hurtCooldown = 0f
            state.hp = state.maxHp
            near.status[StatusCell.STACKS + StatusElement.ION.ordinal] = 0f
            state.addShield(capacity * fill)
            state.takeDamage(capacity * 2f, ignoreReduction = true, kind = IncomingDamage.PROJECTILE)
            assertEquals(0f, state.shield)
            return near.stacksOf(StatusElement.ION) > 0
        }
        assertTrue(breakShieldAt(0f, fill = 1f))
        assertFalse(breakShieldAt(4f, fill = 0.4f), "A shield that never reached half does not count")
        assertTrue(breakShieldAt(8f, fill = 0.6f))
        assertFalse(breakShieldAt(10f, fill = 1f), "Shield breaks rest for three seconds")
        assertTrue(breakShieldAt(11.1f, fill = 1f))
    }

    @Test
    fun conditionsMustAllHoldAtTheMomentOfUse() {
        val state = buildTestState().apply { own("Ballast Core") }
        val target = state.dummy()
        val bystander = state.dummy(x = 450f)
        state.fireClauses(EffectTrigger.IMPACT, target, hit = 100f, source = HitSource.IMPACT)
        assertEquals(bystander.maxHp, bystander.hp, "Below 800 u/s Ballast Core stays quiet")
        state.setVelocityForTesting(900f, 0f)
        state.fireClauses(EffectTrigger.IMPACT, target, hit = 100f, source = HitSource.IMPACT)
        assertEquals(bystander.maxHp - 25f, bystander.hp, 0.01f)

        val elemental = buildTestState().apply { own("Rot Bloom") }
        val clean = elemental.dummy()
        val rotting = elemental.dummy(x = -400f).apply { status[StatusCell.STACKS + StatusElement.ENTROPY.ordinal] = 1f }
        val nearClean = elemental.dummy(x = 450f)
        val nearRotting = elemental.dummy(x = -450f)
        elemental.fireClauses(EffectTrigger.KILL, clean)
        elemental.fireClauses(EffectTrigger.KILL, rotting)
        assertEquals(0, nearClean.stacksOf(StatusElement.ENTROPY))
        assertEquals(4, nearRotting.stacksOf(StatusElement.ENTROPY))
    }

    @Test
    fun procKillsNeverFireKillClausesButDirectKillsDo() {
        val state = buildTestState().apply { own("Chain Detonation") }
        val bystander = state.dummy(x = 450f)
        val procVictim = state.dummy(x = 400f, hp = 1_000f).apply { hp = 1f }
        state.dealProcDamage(procVictim, 10f)
        state.sweepKills()
        assertEquals(1, state.kills)
        assertEquals(bystander.maxHp, bystander.hp, "An item proc kill must not chain into kill clauses")

        val directVictim = state.dummy(x = 400f, hp = 1_000f).apply { hp = 1f }
        state.dealHit(directVictim, 10f, HitSource.IMPACT, canCrit = false)
        state.sweepKills()
        assertEquals(2, state.kills)
        assertEquals(bystander.maxHp - 300f, bystander.hp, 0.01f)

        val statusVictim = state.dummy(x = 400f, hp = 1_000f).apply { hp = 1f }
        // Rupture deals a share of maximum integrity to its own target only.
        state.applyStatus(statusVictim, StatusElement.ENTROPY, 10, generation = 0)
        state.sweepKills()
        assertEquals(3, state.kills)
        assertEquals(bystander.maxHp - 600f, bystander.hp, 0.01f, "Break kills fire kill clauses")
    }

    @Test
    fun aDeathCausedDuringTheKillSweepResolvesOnTheNextSweepAndStillYieldsVictory() {
        val state = buildTestState().apply { own("Chain Detonation") }
        val architect = state.finalPhaseArchitectForTesting(x = 450f, y = 0f)
        val victim = state.dummy(x = 400f, hp = 1_000f).apply { hp = 1f }
        state.dealHit(victim, 10f, HitSource.WEAPON, canCrit = false)

        state.sweepKills()
        assertEquals(1, state.kills)
        assertTrue(architect.hp <= 0f)
        assertFalse(architect.dead, "The sweep only removes the deaths it started with")
        assertTrue(architect in state.enemies)
        assertEquals(GamePhase.RUNNING, state.phase)

        state.sweepKills()
        assertEquals(2, state.kills)
        assertTrue(architect !in state.enemies)
        assertEquals(GamePhase.VICTORY, state.phase)
    }

    @Test
    fun detonateTurnsRemainingDamageOverTimeIntoOneBurst() {
        val state = buildTestState().apply { own("Detonator Spur") }
        val target = state.dummy()
        target.status[StatusCell.STACKS + StatusElement.THERMAL.ordinal] = 4f
        target.status[StatusCell.STACKS + StatusElement.ENTROPY.ordinal] = 4f
        val thermal = state.content.build.element(StatusElement.THERMAL)
        val entropy = state.content.build.element(StatusElement.ENTROPY)
        // n stacks that decay one at a time deal perStack × n(n+1)/2 × decay seconds.
        val remaining = thermal.perStack * 10f * thermal.decaySeconds + ENTROPY_DAMAGE_PER_STACK * 10f * entropy.decaySeconds

        state.fireClauses(EffectTrigger.DASH_HIT, target, hit = 10f, source = HitSource.DASH)
        assertEquals(0, target.stacksOf(StatusElement.THERMAL))
        assertEquals(0, target.stacksOf(StatusElement.ENTROPY))
        assertEquals(target.maxHp - remaining * 2f * state.runScale(), target.hp, 0.05f)

        target.status[StatusCell.STACKS + StatusElement.THERMAL.ordinal] = 4f
        state.fireClauses(EffectTrigger.DASH_HIT, target, hit = 10f, source = HitSource.DASH)
        assertEquals(4, target.stacksOf(StatusElement.THERMAL), "A target detonates at most every two seconds")
    }

    @Test
    fun conversionsRewriteHealingRegenerationAndCriticalChance() {
        val hardlight = buildTestState().apply { own("Hardlight Weave") }
        hardlight.heal(20f, HealSource.ITEM)
        assertEquals(hardlight.maxHp, hardlight.hp)
        assertEquals(hardlight.shieldCapacity() * 1.5f, hardlight.shield, 0.001f, "Overheal overfills the shield")

        val necrotic = buildTestState().apply { own("Necrotic Exchange") }
        necrotic.hp = 50f
        necrotic.timeSinceDamage = 10f
        val near = necrotic.dummy(x = 100f)
        repeat(132) { necrotic.updateSurvival(MutableGameState.FIXED_STEP); necrotic.updateRelicRuntime(MutableGameState.FIXED_STEP) }
        assertEquals(50f, necrotic.hp, "Regeneration no longer heals")
        assertTrue(near.stacksOf(StatusElement.ENTROPY) >= 1, "It rots the enemies around you instead")

        val deadeye = buildTestState().apply { own("Deadeye Reticle") }
        deadeye.critChance = 0.5f
        val marked = deadeye.dummy().apply { status[StatusCell.MARKED_UNTIL] = 10f }
        val hit = deadeye.dealHit(marked, 100f, HitSource.WEAPON)
        assertTrue(hit.critical)
        assertEquals(100f * (1.5f + 0.2f * 2.5f), hit.amount, 0.01f)
    }

    @Test
    fun guaranteedCritArrivesAfterTheConfiguredMisses() {
        val state = buildTestState().apply { own("Focus Lens") }
        state.critChance = 0f
        val target = state.dummy()
        val crits = List(5) { state.dealHit(target, 10f, HitSource.WEAPON).critical }
        assertEquals(listOf(false, false, false, false, true), crits)
    }
}
