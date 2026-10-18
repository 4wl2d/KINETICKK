// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.DamageBasis
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.ActiveBuff
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The direct and status damage pipelines and their caps. */
class DamagePipelineTest {
    @Test
    fun itemAmplificationAboveTheSoftCapCountsHalf() {
        val state = buildTestState()
        val target = state.dummy()
        state.buffs = listOf(ActiveBuff(sourceItem = 0, stat = BuffStat.DAMAGE, amountPerStack = 1f, stacks = 1, expiresAt = 10f))
        assertEquals(200f, state.dealHit(target, 100f, HitSource.WEAPON, canCrit = false).amount, 0.01f)
        state.buffs = listOf(ActiveBuff(sourceItem = 0, stat = BuffStat.DAMAGE, amountPerStack = 3.5f, stacks = 1, expiresAt = 10f))
        val cap = state.content.build.policy.amplificationSoftCap
        assertEquals(100f * (1f + cap + (3.5f - cap) * 0.5f), state.dealHit(target, 100f, HitSource.WEAPON, canCrit = false).amount, 0.01f)
        state.elapsed = 10f
        assertEquals(100f, state.dealHit(target, 100f, HitSource.WEAPON, canCrit = false).amount, 0.01f, "Expired buffs stop counting")
    }

    @Test
    fun criticalMultiplierAndVulnerabilityAreCapped() {
        val state = buildTestState()
        state.critMultiplier = 10f
        val marked = state.dummy().apply { status[StatusCell.MARKED_UNTIL] = 5f }
        val crit = state.dealHit(marked, 100f, HitSource.WEAPON)
        assertTrue(crit.critical)
        assertEquals(100f * state.content.build.policy.criticalMultiplierCap, crit.amount, 0.01f)

        val rotting = state.dummy()
        rotting.status[StatusCell.STACKS + StatusElement.ENTROPY.ordinal] = 60f
        rotting.status[StatusCell.EMBRITTLED_UNTIL] = 5f
        assertEquals(state.content.build.policy.vulnerabilityCap, state.vulnerability(rotting, HitSource.WEAPON))
        assertEquals(250f, state.dealHit(rotting, 100f, HitSource.WEAPON, canCrit = false).amount, 0.01f)
    }

    @Test
    fun relicDamageBonusesShareOneSoftCappedSurgeForDirectAndStatusDamage() {
        val glass = buildTestState(RelicId.GLASS_COMET)
        assertEquals(175f, glass.dealHit(glass.dummy(), 100f, HitSource.WEAPON, canCrit = false).amount, 0.01f)
        assertEquals(175f, glass.dealStatusDamage(glass.dummy(), 100f, StatusElement.THERMAL).amount, 0.01f)
        // Proc damage measured against a hit keeps that hit's value.
        assertEquals(100f, glass.dealProcDamage(glass.dummy(), 100f, rescale = false).amount, 0.01f)

        val stacked = buildTestState(RelicId.GLASS_COMET, RelicId.FURNACE_COVENANT).apply {
            repeat(2) { acquireRelic(RelicId.GLASS_COMET) }
            heat = MutableGameState.MAX_HEAT
        }
        val bonus = 1.5f + 0.48f
        assertEquals(1f + 3f * bonus / (3f + bonus), stacked.relicSurge(), 0.0001f)
        assertTrue(stacked.relicSurge() < 4f)
    }

    @Test
    fun liabilitiesMultiplyAfterTheSurgeAndOnlyAffectTheirSources() {
        val state = buildTestState(RelicId.OVERDRIVE_CASCADE, RelicId.EXECUTIONERS_PRISM)
        val target = state.dummy()
        // Outside Overdrive −30 %, every non-critical direct hit −20 %.
        assertEquals(56f, state.dealHit(target, 100f, HitSource.WEAPON).amount, 0.01f)
        assertEquals(70f, state.dealStatusDamage(target, 100f, StatusElement.ION).amount, 0.01f)
        state.overdriveTime = 3f
        assertEquals(80f, state.dealHit(target, 100f, HitSource.WEAPON).amount, 0.01f)
        assertFalse(state.dealStatusDamage(target, 100f, StatusElement.ION).critical, "Status damage never crits")
    }

    @Test
    fun hitBasedProcsIgnoreVulnerabilityWhileOtherBasesTakeIt() {
        val state = buildTestState().apply { own("Calibrated Coil") }
        val rotting = state.dummy()
        rotting.status[StatusCell.STACKS + StatusElement.ENTROPY.ordinal] = 10f
        repeat(6) { state.fireClauses(EffectTrigger.WEAPON_HIT, rotting, hit = 100f, source = HitSource.WEAPON) }
        assertEquals(rotting.maxHp - 60f, rotting.hp, 0.01f)

        val flat = state.dealProcDamage(rotting, 100f)
        assertEquals(100f * (1f + 10 * 0.03f), flat.amount, 0.01f)
    }

    @Test
    fun maxIntegrityBasisShrinksForElitesAndTheArchitect() {
        val state = buildTestState()
        val normal = state.dummy(hp = 1_000f)
        val elite = state.dummy(hp = 1_000f, type = EnemyType.ELITE)
        val architect = state.dummy(hp = 1_000f, type = EnemyType.ARCHITECT)
        assertEquals(1_000f, state.basisAmount(DamageBasis.VICTIM_MAX_HP, 0f, normal))
        assertEquals(150f, state.basisAmount(DamageBasis.VICTIM_MAX_HP, 0f, elite), 0.01f)
        assertEquals(40f, state.basisAmount(DamageBasis.VICTIM_MAX_HP, 0f, architect), 0.01f)
        assertEquals(37f, state.basisAmount(DamageBasis.HIT, 37f, null))
        assertEquals(state.runScale(), state.basisAmount(DamageBasis.FLAT, 0f, null), 0.0001f)
    }

    @Test
    fun chainsNestAtMostThreeLevelsAndEachLevelKeepsItsOwnVisitedTargets() {
        val state = buildTestState()
        val line = List(4) { index -> state.dummy(x = 100f + index * 50f) }
        val cluster = List(3) { index -> state.dummy(x = 10_000f + index * 50f) }
        val outer = mutableListOf<Int>()
        val inner = mutableListOf<Int>()
        var third = 0
        var fourth = 0
        state.chain(0f, 0f, -1, 4, 200f) { link ->
            outer += link.id
            // A nested chain elsewhere must not disturb the outer chain's visited targets.
            state.chain(10_000f, 0f, -1, 2, 200f) { nested ->
                inner += nested.id
                state.chain(nested.x, nested.y, nested.id, 1, 200f) {
                    third++
                    state.chain(0f, 0f, -1, 6, 200f) { fourth++ }
                }
            }
        }
        assertEquals(line.map { it.id }, outer, "Outer links stay distinct and nearest-first")
        assertEquals(List(4) { listOf(cluster[0].id, cluster[1].id) }.flatten(), inner)
        assertEquals(8, third, "The third level still runs")
        assertEquals(0, fourth, "A fourth nested level is dropped")
        assertEquals(0, state.chainDepth)
        assertEquals(MAX_CHAIN_TARGETS * (MAX_CHAIN_NESTING) + MAX_CHAIN_NESTING, MutableGameState.MAX_CHAIN_SCRATCH)
    }

    @Test
    fun damageNumbersCarryTheElementTone() {
        val state = buildTestState()
        state.dealStatusDamage(state.dummy(), 50f, StatusElement.CRYO)
        state.dealHit(state.dummy(), 50f, HitSource.IMPACT, canCrit = false)
        val tones = state.takeVisualFxCues().filterIsInstance<VisualFxCue.DamageNumberAdded>().map { it.tone }
        assertEquals(listOf(StatusElement.CRYO.ordinal + 1, 0), tones)
    }
}
