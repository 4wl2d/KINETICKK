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
    fun everyShippedTierKeepsAPositiveNonDecreasingTableWithItsScaledTotal() {
        val table = RunTempoProfile().levelRequirements
        val totals = (ContentBounds.MIN_REBIRTH_LEVEL..ContentBounds.MAX_REBIRTH_LEVEL).map { tier ->
            val profile = defaultRebirthProfile(tier)
            val scaled = table.map(profile::dataRequirement)
            assertTrue(scaled.all { it > 0 }, "tier $tier positive")
            assertTrue(scaled.zipWithNext().all { (first, second) -> first <= second }, "tier $tier non-decreasing")
            assertTrue(scaled.zip(table).all { (tiered, base) -> tiered >= base }, "tier $tier never cheaper")
            scaled.sum()
        }
        assertEquals(14_428, totals[0])
        assertEquals(20_490, totals[3])
        assertEquals(26_547, totals[6])
        assertEquals(34_625, totals[10])
        assertTrue(totals.zipWithNext().all { (lower, higher) -> lower < higher }, "each tier needs more Data to 42")
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
