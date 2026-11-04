// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.common.localization

import kotlin.test.Test
import kotlin.test.assertEquals

class NumberFormattingTest {
    @Test
    fun compactNumbersKeepThreeSignificantDigitsAcrossEverySuffixBoundary() {
        listOf(
            0L to "0",
            5L to "5",
            999L to "999",
            1_000L to "1K",
            1_050L to "1.05K",
            1_234L to "1.23K",
            1_235L to "1.24K",
            1_500L to "1.5K",
            12_345L to "12.3K",
            123_456L to "123K",
            999_499L to "999K",
            999_500L to "1M",
            1_000_000L to "1M",
            1_234_567L to "1.23M",
            999_999_999L to "1B",
            1_000_000_000L to "1B",
            45_600_000_000L to "45.6B",
            1_000_000_000_000L to "1T",
            1_000_000_000_000_000L to "1Qa",
            1_000_000_000_000_000_000L to "1Qi",
            Long.MAX_VALUE to "9.22Qi",
        ).forEach { (value, expected) ->
            assertEquals(expected, formatCompactNumber(value, AppLanguage.English), value.toString())
        }
    }

    @Test
    fun russianCompactNumbersKeepLatinSuffixesWithADecimalComma() {
        listOf(
            999L to "999",
            1_000L to "1K",
            1_234L to "1,23K",
            12_345L to "12,3K",
            123_456L to "123K",
            999_500L to "1M",
            1_600_000L to "1,6M",
            1_000_000_000L to "1B",
            2_050_000_000_000_000L to "2,05Qa",
        ).forEach { (value, expected) ->
            assertEquals(expected, formatCompactNumber(value, AppLanguage.Russian), value.toString())
        }
    }

    @Test
    fun negativeNumbersMirrorTheirMagnitudeWithAMinusSign() {
        assertEquals("−5", formatCompactNumber(-5L, AppLanguage.English))
        assertEquals("−1.24K", formatCompactNumber(-1_240L, AppLanguage.English))
        assertEquals("−9.22Qi", formatCompactNumber(Long.MIN_VALUE, AppLanguage.English))
        assertEquals("−1,234", formatGroupedNumber(-1_234L, AppLanguage.English))
        assertEquals("−9,223,372,036,854,775,808", formatGroupedNumber(Long.MIN_VALUE, AppLanguage.English))
    }

    @Test
    fun roundedKeysChangeOnlyWhenTheShownTextChanges() {
        assertEquals(999L, compactNumberRounded(999L))
        assertEquals(1_230L, compactNumberRounded(1_234L))
        assertEquals(1_000_000L, compactNumberRounded(999_500L))
        assertEquals(999_000L, compactNumberRounded(999_499L))
        assertEquals(-1_240L, compactNumberRounded(-1_235L))
        // Every value in one shown bucket shares the key, and formatting the key writes the same text.
        (1_075_000L..1_084_999L step 997L).forEach { value ->
            assertEquals(1_080_000L, compactNumberRounded(value), value.toString())
            assertEquals(formatCompactNumber(value, AppLanguage.English), formatCompactNumber(compactNumberRounded(value), AppLanguage.English))
        }
    }

    @Test
    fun groupedNumbersWriteEveryDigitWithTheLanguagesThousandsSeparator() {
        assertEquals("999", formatGroupedNumber(999L, AppLanguage.English))
        assertEquals("1,000", formatGroupedNumber(1_000L, AppLanguage.English))
        assertEquals("1,234,567", formatGroupedNumber(1_234_567L, AppLanguage.English))
        assertEquals("1 234 567", formatGroupedNumber(1_234_567L, AppLanguage.Russian))
        assertEquals("9,223,372,036,854,775,807", formatGroupedNumber(Long.MAX_VALUE, AppLanguage.English))
        // Below the threshold both forms are the same text.
        assertEquals(formatGroupedNumber(COMPACT_NUMBER_THRESHOLD - 1, AppLanguage.Russian),
            formatCompactNumber(COMPACT_NUMBER_THRESHOLD - 1, AppLanguage.Russian))
    }
}
