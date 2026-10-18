// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RussianContentSourcesTest {
    @Test
    fun noEnglishKeyMapsToTwoDifferentRussianValuesAcrossTheMergedSources() {
        // The merged map also adds every key in uppercase; a collision there overwrites silently too.
        val entries = RussianContentSources.flatten().flatMap { (english, russian) ->
            listOf(english to russian, english.uppercase() to russian.uppercase())
        }
        val conflicts = entries.groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.toSet() }
            .filterValues { it.size > 1 }

        assertTrue(conflicts.isEmpty(), "English keys with several Russian values: $conflicts")
    }

    @Test
    fun everySourceEntryIsNonBlankAndRussianCarriesNoLatinLetters() {
        RussianContentSources.flatten().forEach { (english, russian) ->
            assertTrue(english.isNotBlank() && russian.isNotBlank(), "Blank entry: '$english' -> '$russian'")
            assertFalse(russian.any { it in 'A'..'Z' || it in 'a'..'z' }, "Latin letters in Russian: $russian")
            assertFalse("·" in russian || "→" in russian || "←" in russian, "Forbidden mark in: $russian")
            assertEquals(english.trim(), english, "Padded key: '$english'")
        }
    }

    @Test
    fun theCompactModifierFormIsTotalOverEveryItemEffect() {
        ItemEffect.entries.forEach { effect ->
            listOf("+4.5% ", "−20% ", "+12 ", "+0.2/s ", "+0.8s ").forEach { value ->
                val russian = assertNotNull((value + effect.displayLabel).itemModifierInRussian(), "$value${effect.displayLabel}")
                assertFalse(russian.any { it in 'A'..'Z' || it in 'a'..'z' }, russian)
            }
        }
        assertEquals("Восстановление прочности: +0,2/с", "+0.2/s Integrity regeneration".localizedContent(AppLanguage.Russian))
        assertEquals("Окно комбо: +0,8 с", "+0.8s Combo window".localizedContent(AppLanguage.Russian))
        assertEquals(null, "+4.5% Unknown effect".itemModifierInRussian())
        assertEquals(null, "4.5% Impact damage".itemModifierInRussian())
    }

    @Test
    fun displayNumbersRoundHalfUpGroupThousandsAndUseTheLanguageSeparator() {
        assertEquals("1,200", AppLanguage.English.number(1_200.0, 0))
        assertEquals("1\u00A0200", AppLanguage.Russian.number(1_200.0, 0))
        assertEquals("12,345,678", AppLanguage.English.number(12_345_678.0, 0))
        assertEquals("999", AppLanguage.English.number(999.0, 0))
        assertEquals("0.25", AppLanguage.English.number(0.25, 2))
        assertEquals("0,25", AppLanguage.Russian.number(0.25, 2))
        assertEquals("35", AppLanguage.English.number(0.35f * 100.0, 1))
        assertEquals("59.5", AppLanguage.English.number(0.35f * 1.7f * 100.0, 1))
        assertEquals("2.3", AppLanguage.English.number(2.25, 1))
        assertEquals("3", AppLanguage.English.number(2.96, 1))
        assertEquals("0", AppLanguage.English.number(0.0, 2))
        assertEquals("−10", AppLanguage.English.number(-10.0, 0))
        assertEquals("−1\u00A0200,5", AppLanguage.Russian.number(-1_200.5, 1))
        assertEquals("0", AppLanguage.English.number(-0.004, 2), "A value that rounds to zero carries no sign")
    }
}
