// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatDelta
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.abs
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The 0.4.0 expansion's new behavior: ARSENAL III, reaction clauses naming both elements, and the item
 * clauses on ricochets and slams, dispatched directly (the collisions that fire them belong to the
 * obstacle field).
 */
class ExpansionItemsTest {
    /** A run at ARSENAL III whose Arsenal items only carry their tag, so nothing else touches the targets. */
    /** ARSENAL III with element-free Arsenal items; the kinetic Morningstar by default. */
    private fun arsenalThree(weapon: WeaponId = WeaponId.MORNINGSTAR): MutableGameState {
        val content = canonicalGameplayContent.copy(items = canonicalGameplayContent.items.map { item ->
            if (BuildTag.ARSENAL !in item.tags) item
            else item.copy(elements = immutableListOf(), stats = immutableListOf(StatDelta(ItemEffect.PICKUP_RADIUS, 1f)),
                clauses = immutableListOf(), rules = immutableListOf(), risk = false)
        }.toImmutableList())
        val state = buildTestState(content = content, weapon = weapon)
        for (item in content.items.filter { BuildTag.ARSENAL in it.tags }) {
            if (state.tagTier(BuildTag.ARSENAL) >= 3) break
            state.acquireItem(item.id)
        }
        assertEquals(3, state.tagTier(BuildTag.ARSENAL))
        return state
    }

    @Test
    fun arsenalThreeAddsTheDominantElementOnEveryFourthWeaponHit() {
        val state = arsenalThree().apply { own("Kindling") }
        assertEquals(StatusElement.THERMAL, state.build.dominant)
        val target = state.dummy()
        val stacksAfterEachHit = (1..8).map {
            state.dealHit(target, 10f, HitSource.WEAPON, canCrit = false)
            target.stacksOf(StatusElement.THERMAL)
        }
        assertEquals(listOf(0, 0, 0, 1, 1, 1, 1, 2), stacksAfterEachHit)

        // Impacts never advance the weapon counter.
        val impacts = arsenalThree().apply { own("Kindling") }
        val rammed = impacts.dummy()
        repeat(8) { impacts.dealHit(rammed, 10f, HitSource.IMPACT, canCrit = false) }
        assertEquals(0, rammed.stacksOf(StatusElement.THERMAL))
        assertEquals(0, impacts.arsenalCounter)
    }

    @Test
    fun arsenalThreeWithoutAnElementAddsNothingToTheFourthHit() {
        val state = arsenalThree()
        assertNull(state.build.dominant)
        val target = state.dummy()
        repeat(8) { shot ->
            val before = target.hp
            val result = state.dealHit(target, 10f, HitSource.WEAPON, canCrit = false)
            assertEquals(result.amount, before - target.hp, 0.001f, "Weapon hit ${shot + 1} deals only itself")
        }
        assertTrue(StatusElement.entries.all { target.stacksOf(it) == 0 })
    }

    @Test
    fun arsenalThreeAddsAnElementalWeaponsOwnElementWithoutADominantOne() {
        val state = arsenalThree(WeaponId.SINGULARITY_SPEAR)
        assertNull(state.build.dominant)
        val target = state.dummy()
        val stacksAfterEachHit = (1..8).map {
            state.dealHit(target, 10f, HitSource.WEAPON, canCrit = false)
            target.stacksOf(StatusElement.VOID)
        }
        assertEquals(listOf(0, 0, 0, 1, 1, 1, 1, 2), stacksAfterEachHit)
    }

    @Test
    fun arsenalThreeBreaksDealAQuarterMoreDamage() {
        fun breakDamage(state: MutableGameState): Float {
            val target = state.dummy()
            val beside = state.dummy(x = 430f)
            val threshold = state.breakThreshold(StatusElement.THERMAL, target)
            state.applyStatus(target, StatusElement.THERMAL, threshold, 0)
            assertEquals(0, target.stacksOf(StatusElement.THERMAL), "The stacks broke")
            return beside.maxHp - beside.hp
        }
        val plain = breakDamage(buildTestState())
        val arsenal = breakDamage(arsenalThree())
        assertTrue(plain > 0f)
        assertEquals(plain * (1f + ARSENAL_BREAK_BONUS), arsenal, plain * 0.001f)
    }

    @Test
    fun aReactionClauseNamingTwoElementsFiresOnlyForTheReactionBetweenThem() {
        val state = buildTestState().apply { own("Stormcaller Plate") }
        val target = state.dummy()
        val beside = state.dummy(x = 450f)
        fun fires(reaction: ReactionId): Boolean {
            val before = beside.hp
            state.onReaction(target, reaction)
            return beside.hp < before
        }
        assertTrue(fires(ReactionId.THUNDERCLAP))
        // Each other reaction of Thermal or of Sonic involves only one of the two named elements.
        ReactionId.entries.filter { it != ReactionId.THUNDERCLAP }.forEach { reaction ->
            assertTrue(!fires(reaction), "$reaction is not the Thermal and Sonic reaction")
        }

        // The real reaction fires it too: Sonic landing on two Thermal stacks.
        fun thunderclapSplash(plate: Boolean): Float {
            val live = buildTestState().apply { if (plate) own("Stormcaller Plate") }
            val reacting = live.dummy()
            val bystander = live.dummy(x = 450f)
            live.applyStatus(reacting, StatusElement.THERMAL, 2, 0)
            live.applyStatus(reacting, StatusElement.SONIC, 2, 0)
            return bystander.maxHp - bystander.hp
        }
        assertTrue(thunderclapSplash(plate = true) > thunderclapSplash(plate = false), "Thunderclap set off the plate")
    }

    @Test
    fun aReactionClauseNamingOneElementFiresForEveryReactionInvolvingIt() {
        val content = contentWithClauses(
            "Stormcaller Plate",
            canonicalGameplayContent.items.single { it.name == "Stormcaller Plate" }.clauses.single().let { clause ->
                clause.copy(conditions = immutableListOf(clause.conditions.last()))
            },
        )
        val state = buildTestState(content = content).apply { own("Stormcaller Plate") }
        val target = state.dummy()
        val beside = state.dummy(x = 450f)
        val firing = ReactionId.entries.filter { reaction ->
            val before = beside.hp
            state.onReaction(target, reaction)
            beside.hp < before
        }
        assertEquals(ReactionId.entries.filter { it.involves(StatusElement.SONIC) }, firing)
    }

    @Test
    fun ricochetItemClausesFireOnARicochetWithinTheirCooldowns() {
        val plating = buildTestState().apply { own("Rebound Plating") }
        plating.fireClauses(EffectTrigger.RICOCHET)
        assertEquals(12f, plating.shield, 0.001f)

        val shunt = buildTestState().apply { own("Heat Shunt"); heat = 70f }
        shunt.fireClauses(EffectTrigger.RICOCHET)
        assertEquals(50f, shunt.heat, 0.001f)
        shunt.fireClauses(EffectTrigger.RICOCHET)
        assertEquals(50f, shunt.heat, 0.001f, "Heat Shunt rests for 0.5 s")
        shunt.elapsed += 0.5f
        shunt.fireClauses(EffectTrigger.RICOCHET)
        assertEquals(30f, shunt.heat, 0.001f)

        val bank = buildTestState().apply { own("Bank Shot") }
        bank.fireClauses(EffectTrigger.RICOCHET)
        assertEquals(0.4f, bank.buffs.single { it.stat == BuffStat.IMPACT }.amountPerStack, 0.001f)

        val recapture = buildTestState().apply { own("Kinetic Recapture") }
        val charge = recapture.overdriveCharge
        recapture.fireClauses(EffectTrigger.RICOCHET)
        assertEquals(charge + 8f * recapture.overdriveGainFactor(), recapture.overdriveCharge, 0.001f)

        val carom = buildTestState().apply { own("Perfect Carom"); setVelocityForTesting(900f, 0f) }
        val near = carom.dummy(x = carom.coreX + 200f, y = carom.coreY)
        val far = carom.dummy(x = carom.coreX + 400f, y = carom.coreY)
        carom.fireClauses(EffectTrigger.RICOCHET)
        assertTrue(near.hp < near.maxHp, "The blast reaches 240")
        assertEquals(far.maxHp, far.hp, "and no farther")
    }

    @Test
    fun slamItemClausesFireOnTheSlammedEnemy() {
        val rivets = buildTestState().apply { own("Concussive Rivets") }
        val slammed = rivets.dummy()
        rivets.fireClauses(EffectTrigger.SLAM, slammed, hit = 20f)
        assertEquals(2, slammed.stacksOf(StatusElement.SONIC))

        val splinters = buildTestState().apply { own("Splinter Field") }
        val thrown = splinters.dummy(x = 400f, y = 0f)
        val inLine = splinters.dummy(x = 520f, y = 0f)
        splinters.fireClauses(EffectTrigger.SLAM, thrown, hit = 20f)
        assertTrue(inLine.hp < inLine.maxHp, "A shard leaves the slammed enemy along its line")
        assertEquals(thrown.maxHp, thrown.hp, "Shards spare the enemy they leave")

        val crumple = buildTestState().apply { own("Crumple Zone") }
        val crushed = crumple.dummy(hp = 1_000f)
        crumple.fireClauses(EffectTrigger.SLAM, crushed, hit = 20f)
        assertEquals(1_000f * 0.06f * victimFactor(crushed), crushed.maxHp - crushed.hp, 0.01f)

        val salvage = buildTestState().apply { own("Salvage Collisions") }
        val wreck = salvage.dummy()
        val collected = (1..6).map {
            salvage.fireClauses(EffectTrigger.SLAM, wreck, hit = 20f)
            salvage.dataCollected
        }
        assertEquals(listOf(0L, 0L, 2L, 2L, 2L, 4L), collected, "Every third slam pays 2 Data")

        val pileUp = buildTestState().apply { own("Pile-Up Protocol") }
        val burning = pileUp.dummy()
        // Placed directly: landing one on the other would react before the slam.
        burning.status[StatusCell.STACKS + StatusElement.THERMAL.ordinal] = 3f
        burning.status[StatusCell.STACKS + StatusElement.ENTROPY.ordinal] = 3f
        val before = burning.hp
        pileUp.fireClauses(EffectTrigger.SLAM, burning, hit = 20f)
        assertTrue(burning.hp < before, "The slam detonates the fire and the rot")
        assertEquals(0, burning.stacksOf(StatusElement.THERMAL))
        assertEquals(0, burning.stacksOf(StatusElement.ENTROPY))
    }

    @Test
    fun hyperfeedRailsVentsFromFullHeatAndShortensTheOverheatLockout() {
        fun overheatingDash(rails: Boolean): MutableGameState = buildTestState().apply {
            if (rails) own("Hyperfeed Rails")
            heat = 80f
            requestDash()
            updateHeat(0f)
        }
        fun secondsLocked(state: MutableGameState): Float {
            var seconds = 0f
            while (state.overheated) {
                state.updateHeat(1f / 60f)
                seconds += 1f / 60f
                assertTrue(seconds < 60f, "The lockout ends")
            }
            return seconds
        }
        val plain = overheatingDash(rails = false)
        assertTrue(plain.overheated)
        assertEquals(MutableGameState.MAX_HEAT, plain.heat, 0.001f)

        val rails = overheatingDash(rails = true)
        val magnitude = rails.build.magnitude(rails.itemNamed("Hyperfeed Rails").id)
        assertTrue(rails.overheated, "The Dash still overheats")
        assertEquals(MutableGameState.MAX_HEAT - 40f * magnitude, rails.heat, 0.001f, "The vent counts down from full heat")
        assertTrue(secondsLocked(rails) < secondsLocked(plain), "Venting shortens the lockout")
    }

    @Test
    fun onlyOneCardAddingItemIsOfferedPerRun() {
        val pair = listOf("Gambler's Ledger", "Cornucopia Core")
        for (held in pair) {
            val other = pair.single { it != held }
            val fresh = buildTestState(rebirth = 10).apply { lifetimeMatter = 4_000L }
            assertTrue(fresh.itemNamed(other) in fresh.offerableItems(), "$other is offered to a build without $held")

            val state = buildTestState(rebirth = 10).apply {
                lifetimeMatter = 4_000L
                own(held)
            }
            assertFalse(state.itemNamed(other) in state.offerableItems(), "$other would add no card beside $held")
            assertFalse(state.hasUsefulItemEffect(state.itemNamed(other)))
            state.openItemChoice()
            assertEquals(4, state.choices.size, "$held adds its card")
        }
    }

    @Test
    fun dataFromALevelUpClauseLevelsAgainOnlyAgainstTheSettledRequirement() {
        // Chunks that end one level short of a stale-requirement cascade, and that cross several levels.
        for (chunk in listOf(25f, 140f)) {
            val state = buildTestState(rebirth = 7).apply { own("Compound Interest", stacks = 2) }
            val tempo = state.content.tempo
            val item = state.itemNamed("Compound Interest")
            val perLevel = 8f * state.build.magnitude(item.id) * state.build.bonus(item.id)
            state.level = 2
            state.nextLevelData = tempo.dataRequiredForLevel(2)
            state.data = state.nextLevelData - 1
            state.dataFraction = 0f

            // The same grants applied one after another, each level fully settled before its dividend lands.
            var level = state.level
            var data = state.data
            var fraction = 0f
            var weapon = state.weaponLevel
            val weaponCap = tempo.maximumAutomaticWeaponLevel + state.matrix.startingWeaponLevels
            fun gain(amount: Float) {
                fraction += amount
                val whole = floor(fraction).toInt()
                fraction -= whole
                data += whole
            }
            gain(chunk)
            while (data >= tempo.dataRequiredForLevel(level)) {
                data -= tempo.dataRequiredForLevel(level)
                level++
                if (tempo.grantsWeaponAmplification(level) && weapon < weaponCap) weapon++
                gain(perLevel)
            }

            state.gainData(chunk)
            assertEquals(level, state.level, "Level after $chunk Data")
            assertEquals(data, state.data, "Data after $chunk Data")
            assertEquals(tempo.dataRequiredForLevel(level), state.nextLevelData)
            assertEquals(weapon, state.weaponLevel, "Weapon level after $chunk Data")
            assertTrue(abs(fraction - state.dataFraction) < 0.001f)
        }
    }
}
