// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.localization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileScreensRedesignTextTest {
    @Test
    fun everyStringHasBothLanguagesWithMatchingArgumentsAndNoForbiddenMarks() {
        val parameter = Regex("\\{[0-9]+\\}")
        val forbidden = listOf("·", "→", "←", "↑", "↓", "↵", "›", "‹", "▶", "◀", "◇", "§", "◆")
        val levelLabels = Regex("\\bL\\d+\\b|LV |Lv\\.")
        ProfileScreensRedesignText.entries.forEach { key ->
            assertTrue(key.english.isNotBlank(), "$key English")
            assertTrue(key.russian.isNotBlank(), "$key Russian")
            assertEquals(parameter.findAll(key.english).map { it.value }.toSet(),
                parameter.findAll(key.russian).map { it.value }.toSet(), "$key arguments")
            for (text in listOf(key.english, key.russian)) {
                forbidden.forEach { mark -> assertFalse(mark in text, "$key contains $mark") }
                assertFalse(levelLabels.containsMatchIn(text), "$key level label")
                assertFalse(text.trimEnd().endsWith(":"), "$key trailing colon")
            }
        }
        assertEquals("Lvl {0}", ProfileScreensRedesignText.MasteryLevel.english)
        assertEquals("Ур. {0}", ProfileScreensRedesignText.MasteryLevel.russian)
    }
}
