// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.localization

import kinetickk.ball.content.api.localizedContent
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RunVarietyTextTest {
    @Test
    fun everyRunVarietyTextIsTranslatedKeepsItsArgumentsAndFollowsTheCopyRules() {
        val arguments = Regex("\\{\\d+}")
        val latin = Regex("[A-Za-z]")
        // Overlay copy has no separators or arrows; actives are never skills, in either language.
        val forbidden = charArrayOf('·', '→', '←', '↑', '↓', '›', '‹', '/', '|')
        val skill = Regex("skill|навык", RegexOption.IGNORE_CASE)
        RunVarietyText.entries.forEach { resource ->
            assertTrue(resource.english.isNotBlank() && resource.russian.isNotBlank(), "$resource is blank")
            assertNotEquals(resource.english, resource.russian, "$resource is untranslated")
            assertEquals(arguments.findAll(resource.english).map { it.value }.sorted().toList(),
                arguments.findAll(resource.russian).map { it.value }.sorted().toList(), "$resource arguments")
            assertFalse(latin.containsMatchIn(resource.russian.replace(arguments, "")), "$resource Russian has Latin: ${resource.russian}")
            listOf(resource.english, resource.russian).forEach { text ->
                assertTrue(text.none { it in forbidden }, "$resource: $text")
                assertFalse(skill.containsMatchIn(text), "$resource: $text")
                // A percent stays attached to its number and a Russian second is «с».
                assertFalse(Regex("\\d %").containsMatchIn(text), "$resource detaches a percent: $text")
            }
            assertFalse(Regex("\\d+ s\\b").containsMatchIn(resource.russian), "$resource Russian seconds: ${resource.russian}")
        }
    }

    @Test
    fun theDeclineCopyNamesWhatTheNucleusDealsInBothLanguages() {
        // The No omen card and the Ignore card read as the content keys the nucleus deals (its build-change notice).
        for (language in AppLanguage.entries) {
            val text = { resource: RunVarietyText -> if (language == AppLanguage.English) resource.english else resource.russian }
            assertEquals("No omen".localizedContent(language), text(RunVarietyText.NoOmen), "$language")
            assertEquals("Start the run as it is.".localizedContent(language).trimEnd('.'), text(RunVarietyText.NoOmenText), "$language")
            assertEquals("Ignore".localizedContent(language), text(RunVarietyText.Ignore), "$language")
            assertEquals("Omen".localizedContent(language), text(RunVarietyText.OmenBand), "$language")
            assertEquals("Keynote".localizedContent(language), text(RunVarietyText.ConditionKeynote), "$language")
        }
    }

    @Test
    fun conditionCaptionsAreDistinctInBothLanguages() {
        val captions = listOf(RunVarietyText.ConditionOmen, RunVarietyText.ConditionPattern, RunVarietyText.ConditionKeynote, RunVarietyText.ConditionEvent)
        assertEquals(captions.size, captions.map { it.english }.distinct().size)
        assertEquals(captions.size, captions.map { it.russian }.distinct().size)
    }
}
