// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.localization

import kinetickk.foundation.common.localization.TextResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CodexTextTest {
    private val placeholder = Regex("\\{[0-9]+\\}")

    @Test
    fun everyCodexPhraseHasRussianWithoutLatinLettersAndTheSameArguments() {
        CodexText.entries.forEach { text ->
            assertTrue(text.english.isNotBlank() && text.russian.isNotBlank(), "$text")
            assertFalse(text.russian.any { it in 'a'..'z' || it in 'A'..'Z' }, "Latin letters in Russian $text: ${text.russian}")
            assertEquals(placeholder.findAll(text.english).map { it.value }.toSet(), placeholder.findAll(text.russian).map { it.value }.toSet(), "$text")
            listOf("·", "→", "←", "§").forEach { banned -> assertFalse(banned in text.english || banned in text.russian, "$text uses $banned") }
        }
    }

    @Test
    fun noSessionTextCallsAnActiveItemASkill() {
        // The glossary: actives are "active" / "активный" everywhere a player reads them; code keeps the word skill.
        val skill = Regex("skill|навык", RegexOption.IGNORE_CASE)
        val resources: List<TextResource> = CodexText.entries + SessionText.entries + SessionRedesignText.entries
        resources.forEach { resource ->
            listOf(resource.english, resource.russian).forEach { text -> assertFalse(skill.containsMatchIn(text), "$resource: $text") }
        }
    }
}
