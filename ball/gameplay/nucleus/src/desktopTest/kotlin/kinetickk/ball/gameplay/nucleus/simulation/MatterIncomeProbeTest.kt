// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CharacterUnlockRequirement
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.PlayerEconomy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Banked Matter per run at the reference tiers (economy §4.3), measured on the gate harness with upgrades at 0.
 * The assertions lock the Refit and Honing prices to this income; they are a derivation lock, not a balance
 * verdict. If income drifts outside them, the class bases are re-derived in the same change
 * (`Bc = round5(1.2 × M6 / 512)`, `Bw = round5(0.85 × M6 / 512)`); the bounds never widen.
 *
 * The two price locks read their medians over [PopulationSeeds] (lead Addendum D5, 0.5.0 active items): a five-seed
 * median moves with the reward stream, and the active items' offers moved it enough that the gate seeds became a coin
 * flip. Their bounds stay the design's (Addendum D1: the base population meets each with room to spare). The rising
 * medians keep the five gate seeds, which pass with probability 0.99 on the 05885fa9 base and with the active items.
 */
class MatterIncomeProbeTest {
    /**
     * Every rank-0 Refit and Honing base costs at most 3 % of a fresh tier-0 run's median Matter over
     * [PopulationSeeds]. The five gate seeds met it with probability 0.91 on the 05885fa9 base and 0.84 with the
     * shipped active items. The base population's median was 2,499 Matter (bootstrap SE 17, n 300), so the largest
     * base, 55, was 0.022 of it (SE 0.0002); by Addendum D1 the bound is the higher of the design's 0.03 and that plus
     * two standard errors: 0.03.
     */
    @Test fun theFirstRefitAndHoningLevelsCostAtMostThreePercentOfAFreshWin() {
        val fresh = population(Setup.T0_FRESH).median
        val outside = rankZeroBases().filter { (_, base) -> base > 0.03f * fresh }
        assertTrue(outside.isEmpty(), "rank-0 bases above 3 % of the fresh tier-0 population median $fresh: $outside")
    }

    /**
     * The tenth Refit level costs 1.0-1.5 and the tenth Honing level 0.7-1.0 full-Matrix tier-6 runs, by the median
     * Matter over [PopulationSeeds]. The five gate seeds met both with probability 0.95 on the 05885fa9 base and 0.89
     * with the shipped active items. The base population's median was 23,517 Matter (bootstrap SE 183, n 300): Refit 10
     * cost 1.197 of it (SE 0.009) and Honing 10 0.871 (SE 0.007), so by Addendum D1 each band (the wider of the
     * design's and the base ± two standard errors) is the design's.
     */
    @Test fun theTenthRefitAndHoningLevelsTrackAFullMatrixTierSixRun() {
        val tierSix = population(Setup.T6_FULL).median
        val policy = canonicalGameplayContent.upgrades
        val characterClass = canonicalGameplayContent.coreShapes
            .filter { it.unlockRequirement != CharacterUnlockRequirement.REBIRTH_RANK }.map { it.upgradeBaseCost }.distinct()
        val weaponClass = canonicalGameplayContent.weapons.filter { it.minimumRebirth == 0 }.map { it.upgradeBaseCost }.distinct()
        assertEquals(1, characterClass.size, "rank-0 characters share one Refit class base")
        assertEquals(1, weaponClass.size, "rank-0 weapons share one Honing class base")
        val lastCharacterLevel = policy.nextCost(characterClass.single(), policy.maxLevel - 1).toFloat()
        val lastWeaponLevel = policy.nextCost(weaponClass.single(), policy.maxLevel - 1).toFloat()
        println("MATTER_LOCK M6=$tierSix lastRefit=$lastCharacterLevel (${lastCharacterLevel / tierSix}x) " +
            "lastHoning=$lastWeaponLevel (${lastWeaponLevel / tierSix}x)")
        assertTrue(lastCharacterLevel in tierSix * 1f..tierSix * 1.5f,
            "the tenth Refit level ($lastCharacterLevel) must cost 1.0-1.5 full-Matrix tier-6 runs (median $tierSix)")
        assertTrue(lastWeaponLevel in tierSix * 0.7f..tierSix * 1f,
            "the tenth Honing level ($lastWeaponLevel) must cost 0.7-1.0 full-Matrix tier-6 runs (median $tierSix)")
    }

    @Test fun aFullMatrixEarnsMoreMatterAtHigherTiers() {
        val fresh = measured(Setup.T0_FRESH).median
        val tierZero = measured(Setup.T0_FULL).median
        val tierThree = measured(Setup.T3_FULL).median
        val tierSix = measured(Setup.T6_FULL).median
        // Reported only: the strong build at tier 10 has no price bound.
        measured(Setup.T10_STRONG)
        assertTrue(fresh < tierZero && tierZero < tierThree && tierThree < tierSix,
            "Matter per run must rise with the Matrix and the tier: $fresh / $tierZero / $tierThree / $tierSix")
    }

    private enum class Setup(val profile: () -> GameplayProfileSnapshot, val relics: List<RelicId> = emptyList()) {
        T0_FRESH({ gateProfile(GateMatrix.NONE, tier = 0) }),
        T0_FULL({ fullMatrix(0) }),
        T3_FULL({ fullMatrix(3) }),
        T6_FULL({ fullMatrix(6) }),
        /** RebirthBalanceGateTest's strong build: Pentagon, Arc Coil and four Relics bound from the start, full Matrix. */
        T10_STRONG(
            { gateProfile(GateMatrix.FULL, tier = 10, shape = CoreShape.PENTAGON, weapon = WeaponId.ARC_COIL) },
            listOf(RelicId.STORMHEART_CAPACITOR, RelicId.PANDEMIC_CODEX, RelicId.THUNDER_CHOIR, RelicId.CINDER_CROWN),
        ),
    }

    private class Income(val runs: List<Float>) {
        val median: Float get() = runs.gateMedian()
    }

    private companion object {
        private val measurements = HashMap<Setup, Income>()
        private val populations = HashMap<Setup, Income>()

        fun fullMatrix(tier: Int): GameplayProfileSnapshot =
            gateProfile(GateMatrix.FULL, tier, economy = PlayerEconomy(0L, fullMatrixPrice()))

        /** Every Refit and Honing base a rank-0 profile can buy (characters not gated by the Reborn rank). */
        fun rankZeroBases(): List<Pair<String, Int>> =
            canonicalGameplayContent.coreShapes.filter { it.unlockRequirement != CharacterUnlockRequirement.REBIRTH_RANK }
                .map { it.id.name to it.upgradeBaseCost } +
                canonicalGameplayContent.weapons.filter { it.minimumRebirth == 0 }.map { it.id.name to it.upgradeBaseCost }

        /** The setup's banked Matter on each gate seed, measured once per test JVM. */
        fun measured(setup: Setup): Income = measurements.getOrPut(setup) {
            val matter = EconomyGateSeeds.map { seed ->
                val state = playGateRun(seed, setup.profile(), NoPoiBot(), setup.relics)
                val perMinute = if (state.elapsed > 0f) state.runMatter / state.elapsed * 60f else 0f
                println("MATTER ${setup.name} seed=$seed matter=${state.runMatter} seconds=${state.elapsed.toInt()} " +
                    "outcome=${if (state.phase == GamePhase.VICTORY) "WIN" else "LOSS"} perMinute=${perMinute.toInt()} " +
                    "matterGain=${(state.matterGain * 100f).toInt() / 100f}")
                state.runMatter.toFloat()
            }
            val income = Income(matter)
            println("MATTER_MEDIAN ${setup.name} median=${income.median.toLong()} min=${matter.min().toLong()} max=${matter.max().toLong()}")
            income
        }

        /** The setup's banked Matter on each [PopulationSeeds] seed, measured once per test JVM. */
        fun population(setup: Setup): Income = populations.getOrPut(setup) {
            val income = Income(populationOf(PopulationSeeds) { seed ->
                playGateRun(seed, setup.profile(), NoPoiBot(), setup.relics).runMatter.toFloat()
            })
            println("MATTER_POPULATION ${setup.name} median=${income.median.toLong()} runs=${income.runs.size}")
            income
        }
    }
}
