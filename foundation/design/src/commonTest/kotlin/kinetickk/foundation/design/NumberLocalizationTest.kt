// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class NumberLocalizationTest {
    @Test
    fun decimalAndMultiplierPresentationOnlyChangesTheDecimalSeparator() {
        assertEquals("1.2", formatOneDecimal(1.29f))
        assertEquals("1,2", formatOneDecimal(1.29f, AppLanguage.Russian))
        assertEquals("0,0", formatOneDecimal(0f, AppLanguage.Russian))
        assertEquals("1.35x", formatMultiplier(1.35f))
        assertEquals("1,35x", formatMultiplier(1.35f, AppLanguage.Russian))
        assertEquals("0,75x", formatMultiplier(0.75f, AppLanguage.Russian))
        assertEquals("2x", formatMultiplier(2f, AppLanguage.Russian))
        assertEquals("1.05x", formatMultiplier(1.05f))
        assertEquals("1.5x", formatMultiplier(1.5f))
    }
}
