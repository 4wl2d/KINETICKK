// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.localization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileLocalizationTest {
    @Test fun everyResourceHasBothTranslationsWithMatchingArguments() {
        val parameter = Regex("\\{[0-9]+\\}")
        ProfileText.entries.forEach { key ->
            assertTrue(key.english.isNotBlank(), "$key English")
            assertTrue(key.russian.isNotBlank(), "$key Russian")
            assertEquals(parameter.findAll(key.english).map { it.value }.toSet(),
                parameter.findAll(key.russian).map { it.value }.toSet(), "$key arguments")
        }
    }
}
