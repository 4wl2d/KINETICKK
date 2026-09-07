// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class NumberLocalizationTest {
    @Test
    fun compactNumbersUseRussianScaleNamesAtEveryMagnitudeBoundary() {
        val cases = listOf(
            -1L to "0",
            0L to "0",
            999L to "999",
            1_000L to "1 тыс.",
            1_600L to "1,6 тыс.",
            999_999L to "999,9 тыс.",
            1_000_000L to "1 млн",
            1_600_000L to "1,6 млн",
            999_999_999L to "999,9 млн",
            1_000_000_000L to "1 млрд",
            1_600_000_000L to "1,6 млрд",
            999_999_999_999L to "999,9 млрд",
            1_000_000_000_000L to "1 трлн",
            1_600_000_000_000L to "1,6 трлн",
            Long.MAX_VALUE to "9223372 трлн",
        )
        cases.forEach { (value, expected) ->
            assertEquals(expected, formatCompact(value, AppLanguage.Russian), value.toString())
        }
    }

    @Test
    fun englishDefaultPreservesCompactSuffixesTruncationAndOverflowSafety() {
        listOf(
            Long.MIN_VALUE to "0",
            999L to "999",
            1_699L to "1.6K",
            1_699_999L to "1.6M",
            1_699_999_999L to "1.6B",
            1_699_999_999_999L to "1.6T",
            Long.MAX_VALUE to "9223372T",
        ).forEach { (value, expected) ->
            assertEquals(expected, formatCompact(value))
            assertEquals(expected, formatCompact(value, AppLanguage.English))
        }
    }

    @Test
    fun decimalAndMultiplierPresentationOnlyChangesTheDecimalSeparator() {
        assertEquals("1.2", formatOneDecimal(1.29f))
        assertEquals("1,2", formatOneDecimal(1.29f, AppLanguage.Russian))
        assertEquals("0,0", formatOneDecimal(0f, AppLanguage.Russian))
        assertEquals("1.35x", formatMultiplier(1.35f))
        assertEquals("1,35x", formatMultiplier(1.35f, AppLanguage.Russian))
        assertEquals("0,75x", formatMultiplier(0.75f, AppLanguage.Russian))
        assertEquals("2x", formatMultiplier(2f, AppLanguage.Russian))
    }
}
