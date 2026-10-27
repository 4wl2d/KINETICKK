// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.profile.interaction.localization.SettingsRedesignText
import kinetickk.foundation.common.input.BindableKey
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
        // Platform-dependent explanations (the refresh rate is a choice on Android, a readout on desktop).
        for (platform in testPlatforms) for (row in SettingsRow.entries) for (language in AppLanguage.entries) {
            val about = row.about(language, platform)
            assertTrue(about.length > row.label(language).length, "$row $language ${platform.support}")
            banned.forEach { glyph -> assertTrue(glyph !in about, "$row $language $glyph") }
        }
        assertNotEquals(
            SettingsRow.REFRESH_RATE.about(AppLanguage.Russian, SettingsPlatform(ANDROID_SUPPORT)),
            SettingsRow.REFRESH_RATE.about(AppLanguage.Russian, SettingsPlatform(DESKTOP_SUPPORT)),
        )
    }

    @Test
    fun displayAndControlsCopyIsBilingualDistinctAndNamesEveryAction() {
        for (language in AppLanguage.entries) {
            val names = GameAction.entries.map { settingsActionName(it, language) }
            assertEquals(names.size, names.toSet().size, "$language action names are distinct")
            names.forEach { name -> assertTrue(name.isNotBlank() && banned.none { it in name }, "$language $name") }
            val groups = SettingsGroup.entries.map { language.text(it.label) }
            assertEquals(6, groups.toSet().size, "$language tab labels")
            for (announcement in listOf(
                SettingsAnnouncement.KeyBound(GameAction.APEX, BindableKey.F),
                SettingsAnnouncement.KeyBound(GameAction.DASH, BindableKey.Z, GameAction.SKILL_1, BindableKey.SPACE),
                SettingsAnnouncement.KeyUnavailable,
                SettingsAnnouncement.KeysReset,
            )) {
                val text = settingsAnnouncementText(announcement, language)
                assertTrue(text.isNotBlank() && "{" !in text && banned.none { it in text }, "$language $announcement: $text")
            }
            // Every segmented choice of the new rows has a label on every platform that offers it.
            for (platform in testPlatforms) for (row in listOf(SettingsRow.WINDOW_MODE, SettingsRow.REFRESH_RATE, SettingsRow.INTERFACE_SCALE, SettingsRow.REACTION_EFFECTS)) {
                val labels = (0 until row.optionCount(platform)).map { row.optionLabel(it, language, platform) }
                assertEquals(labels.size, labels.toSet().size, "$language $row")
                labels.forEach { assertTrue(it.isNotBlank()) }
            }
        }
        assertEquals("Экран", AppLanguage.Russian.text(SettingsGroup.DISPLAY.label))
        assertEquals("Управление", AppLanguage.Russian.text(SettingsGroup.CONTROLS.label))
        assertEquals("Controls", AppLanguage.English.text(SettingsGroup.CONTROLS.label))
        assertEquals("Без лимита", settingsFrameRateText(0, AppLanguage.Russian))
        assertEquals("1600×900", settingsWindowSizeText(1600, 900, AppLanguage.English))
    }

    @Test
    fun theTextSizePreviewShowsGameWordsAndNumbersNotAnExplanation() {
        for (language in AppLanguage.entries) {
            val sample = settingsTextSizeSample(language)
            assertTrue(sample.isNotEmpty())
            val explanations = SettingsRow.entries.map { it.about(language) }
            sample.forEach { (label, value) ->
                assertTrue(label.isNotBlank() && value.all(Char::isDigit), "$language $label $value")
                explanations.forEach { about -> assertTrue(label !in about && about !in label, "$language $label") }
                banned.forEach { glyph -> assertTrue(glyph !in label, "$language $label $glyph") }
            }
        }
    }
}
