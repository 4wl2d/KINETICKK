// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RebirthNumberScaleTest {
    @Test
    fun everyTierPinsItsTwoSignificantDigitDisplayScale() {
        val expected = listOf(1.0, 1.1, 1.5, 2.3, 4.2, 8.7, 21.0, 61.0, 200.0, 790.0, 3_600.0)
        assertEquals(ContentBounds.MAX_REBIRTH_LEVEL + 1, expected.size)
        expected.forEachIndexed { tier, scale ->
            assertEquals(scale, rebirthNumberScale(tier), "tier $tier")
            assertEquals(scale, defaultRebirthProfile(tier).numberScale, "tier $tier profile")
        }
    }

    @Test
    fun theScaleGrowsWithEveryTierAndNeverShrinksANumber() {
        val scales = (0..ContentBounds.MAX_REBIRTH_LEVEL + 5).map(::rebirthNumberScale)
        assertTrue(scales.zipWithNext().all { (lower, higher) -> higher > lower }, scales.toString())
        assertEquals(1.0, scales.first())
        assertFailsWith<IllegalArgumentException> { rebirthNumberScale(-1) }
        assertFailsWith<IllegalArgumentException> { defaultRebirthProfile(0).copy(numberScale = 0.5) }
    }
}
