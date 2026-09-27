// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import kinetickk.ball.profile.interaction.localization.SettingsRedesignText
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsRedesignTextTest {
    private val banned = listOf("·", "→", "←", "↑", "↓", "↵", "›", "‹", "▶", "◀", "◇", "§")

    @Test
    fun everyResourceHasBothTranslationsWithMatchingArgumentsAndNoBannedGlyphs() {
        val parameter = Regex("\\{[0-9]+\\}")
        SettingsRedesignText.entries.forEach { key ->
            for (text in listOf(key.english, key.russian)) {
                assertTrue(text.isNotBlank(), "$key")
                banned.forEach { glyph -> assertTrue(glyph !in text, "$key contains $glyph") }
            }
            assertEquals(parameter.findAll(key.english).map { it.value }.toSet(),
                parameter.findAll(key.russian).map { it.value }.toSet(), "$key arguments")
        }
    }

    @Test
    fun everyRowHasALabelAndAnExplanationInBothLanguages() {
        for (row in SettingsRow.entries) for (language in AppLanguage.entries) {
            val label = row.label(language)
            val about = row.about(language)
            assertTrue(label.isNotBlank() && about.isNotBlank(), "$row $language")
            assertTrue(about.length > label.length, "$row $language explanation")
            banned.forEach { glyph -> assertTrue(glyph !in label && glyph !in about, "$row $language $glyph") }
        }
        assertEquals(
            "Remaps you, threat, heat, shield and polarity to pairs you can tell apart. Mono adds hatching to every threat.",
            SettingsRow.COLOR_VISION.about(AppLanguage.English),
        )
    }
}
