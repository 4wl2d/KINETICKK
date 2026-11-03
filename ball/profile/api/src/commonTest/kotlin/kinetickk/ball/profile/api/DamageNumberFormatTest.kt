// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.api

import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class DamageNumberFormatTest {
    @Test
    fun compactNumbersAbbreviateAndFullNumbersGroupEveryDigit() {
        assertEquals("999", formatDamageNumber(999L, DamageNumberFormat.COMPACT, AppLanguage.English))
        assertEquals("999", formatDamageNumber(999L, DamageNumberFormat.FULL, AppLanguage.Russian))
        assertEquals("14K", formatDamageNumber(14_000L, DamageNumberFormat.COMPACT, AppLanguage.English))
        assertEquals("14,000", formatDamageNumber(14_000L, DamageNumberFormat.FULL, AppLanguage.English))
        assertEquals("2,47M", formatDamageNumber(2_468_000L, DamageNumberFormat.COMPACT, AppLanguage.Russian))
        assertEquals("2 468 000", formatDamageNumber(2_468_000L, DamageNumberFormat.FULL, AppLanguage.Russian))
        assertEquals("9,223,372,036,854,775,807", formatDamageNumber(Long.MAX_VALUE, DamageNumberFormat.FULL, AppLanguage.English))
    }
}
