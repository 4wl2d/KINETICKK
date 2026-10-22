// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RebirthDataRequirementScaleTest {
    @Test
    fun everyTierScalesTheTableByFourteenHundredthsMore() {
        assertEquals(0.14f, REBIRTH_DATA_REQUIREMENT_SCALE_PER_TIER)
        assertEquals(1f, defaultRebirthProfile(0).dataRequirementScale, "tier 0 keeps the base curve exactly")
        (ContentBounds.MIN_REBIRTH_LEVEL..ContentBounds.MAX_REBIRTH_LEVEL).forEach { tier ->
            assertEquals(1f + tier * 0.14f, defaultRebirthProfile(tier).dataRequirementScale, "tier $tier")
        }
        assertEquals(1.42f, defaultRebirthProfile(3).dataRequirementScale, 1e-6f)
        assertEquals(2.4f, defaultRebirthProfile(10).dataRequirementScale, 1e-6f)
    }

    @Test
    fun scaledRequirementsRoundHalfUpAndNeverFallBelowTheBase() {
        val tierZero = defaultRebirthProfile(0)
        assertEquals(716, tierZero.dataRequirement(716))
        assertEquals(Int.MAX_VALUE, tierZero.dataRequirement(Int.MAX_VALUE))

        val tierThree = defaultRebirthProfile(3)
        // 18 × 1.42 = 25.56 and 716 × 1.42 = 1,016.72.
        assertEquals(26, tierThree.dataRequirement(18))
        assertEquals(1_017, tierThree.dataRequirement(716))

        val half = tierZero.copy(dataRequirementScale = 1.5f)
        assertEquals(2, half.dataRequirement(1), "1.5 rounds up")
        assertEquals(5, half.dataRequirement(3), "4.5 rounds up")
        assertEquals(6, half.dataRequirement(4))
        assertEquals(1, tierZero.copy(dataRequirementScale = 1.0000001f).dataRequirement(1), "never below the base")
        assertEquals(Int.MAX_VALUE, half.dataRequirement(Int.MAX_VALUE), "saturates instead of overflowing")
        assertEquals(Int.MAX_VALUE, half.dataRequirement(Int.MAX_VALUE / 3 * 2 + 1))
    }

    @Test
    fun everyTierKeepsTheOpeningLevelsAndScalesTheRestIntoAPositiveNonDecreasingTable() {
        val tempo = RunTempoProfile()
        assertEquals(16, tempo.rebirthScaleFromLevel)
        val totals = (ContentBounds.MIN_REBIRTH_LEVEL..ContentBounds.MAX_REBIRTH_LEVEL).map { tier ->
            val profile = defaultRebirthProfile(tier)
            val scaled = (1 until tempo.maximumLevel).map { level -> tempo.dataRequiredForLevel(level, profile) }
            assertEquals(tempo.levelRequirements.take(15), scaled.take(15), "tier $tier keeps levels 1-15")
            assertEquals(tempo.levelRequirements.drop(15).map(profile::dataRequirement), scaled.drop(15), "tier $tier")
            assertTrue(scaled.all { it > 0 }, "tier $tier positive")
            assertTrue(scaled.zipWithNext().all { (first, second) -> first <= second }, "tier $tier non-decreasing")
            assertEquals(scaled.last(), tempo.dataRequiredForLevel(tempo.maximumLevel, profile), "tier $tier at the cap")
            scaled.sum()
        }
        assertEquals(14_428, totals[0])
        assertEquals(19_912, totals[3])
        assertEquals(25_393, totals[6])
        assertEquals(32_702, totals[10])
        assertTrue(totals.zipWithNext().all { (lower, higher) -> lower < higher }, "each tier needs more Data to 42")
        assertEquals(298, tempo.dataRequiredForLevel(16, defaultRebirthProfile(3)), "210 × 1.42 = 298.2")
        assertEquals(166, tempo.dataRequiredForLevel(15, defaultRebirthProfile(10)))
    }

    @Test
    fun theFirstScaledLevelIsAContentKnobFromTheWholeTableToNone() {
        val whole = RunTempoProfile(rebirthScaleFromLevel = 1)
        val none = RunTempoProfile(rebirthScaleFromLevel = 42)
        fun RunTempoProfile.total(tier: Int): Int =
            (1 until maximumLevel).sumOf { level -> dataRequiredForLevel(level, defaultRebirthProfile(tier)) }

        assertEquals(listOf(14_428, 20_490, 26_547, 34_625), listOf(0, 3, 6, 10).map(whole::total))
        assertEquals(listOf(14_428, 14_428), listOf(0, 10).map(none::total))
        assertEquals(43, whole.dataRequiredForLevel(1, defaultRebirthProfile(10)), "18 × 2.4 = 43.2")
        assertEquals(43, whole.dataRequiredForLevel(Int.MIN_VALUE, defaultRebirthProfile(10)), "clamped like the table")

        RunTempoProfile(rebirthScaleFromLevel = ContentBounds.MAX_RUN_LEVEL)
        assertFailsWith<IllegalArgumentException> { RunTempoProfile(rebirthScaleFromLevel = 0) }
        assertFailsWith<IllegalArgumentException> { RunTempoProfile(rebirthScaleFromLevel = ContentBounds.MAX_RUN_LEVEL + 1) }
    }

    @Test
    fun theScaleMustBeFiniteAndAtLeastOne() {
        val base = defaultRebirthProfile(3)
        assertEquals(2.4f, base.copy(dataRequirementScale = 2.4f).dataRequirementScale)
        base.copy(dataRequirementScale = 1f)
        assertFailsWith<IllegalArgumentException> { base.copy(dataRequirementScale = 0.999f) }
        assertFailsWith<IllegalArgumentException> { base.copy(dataRequirementScale = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { base.copy(dataRequirementScale = Float.POSITIVE_INFINITY) }
    }
}
