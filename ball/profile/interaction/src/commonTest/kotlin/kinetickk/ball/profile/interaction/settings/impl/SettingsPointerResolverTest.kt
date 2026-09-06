// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsPointerResolverTest {
    @Test
    fun languageOptionsAreExactlyRussianAndEnglishAndMatchPointerTargets() {
        for ((width, height, density) in listOf(Triple(1280f, 720f, 1f), Triple(390f, 720f, 1f), Triple(1440f, 720f, 2f))) {
            val options = settingsLanguageOptions(width, height, density, page = 0)
            assertEquals(listOf(AppLanguage.Russian, AppLanguage.English), options.map { it.language })
            options.forEach { option ->
                assertEquals(
                    SettingsAction.SelectLanguage(option.language),
                    resolveSettingsPress(width, height, density, 0, option.bounds.center.x, option.bounds.center.y),
                )
            }
            if (height / density <= 360f) {
                assertEquals(emptyList(), settingsLanguageOptions(width, height, density, page = 1))
            } else {
                assertEquals(options, settingsLanguageOptions(width, height, density, page = 1))
            }
        }
    }

    @Test
    fun desktopMasterVolumePlusFollowsTheLanguageRow() {
        assertEquals(
            SettingsAction.Adjust(SettingsRow.MASTER_VOLUME, direction = 1),
            resolveSettingsPress(
                screenWidth = 1_280f,
                screenHeight = 720f,
                density = 1f,
                page = 0,
                x = 910f,
                y = 258f,
            ),
        )
        assertNull(
            resolveSettingsPress(1_280f, 720f, 1f, page = 0, x = 700f, y = 258f),
        )
    }

    @Test
    fun shortViewportPaginatesSixRowsAndKeepsFooterZones() {
        val panelHeight = minOf(620f, 360f - 30f)
        val startY = (360f - panelHeight) * 0.5f + 72f
        val availableHeight = (360f + panelHeight) * 0.5f - 64f - startY
        assertEquals(6, settingsRowsPerPage(availableHeight, density = 1f))

        assertEquals(
            SettingsAction.PageSelected(1),
            resolveSettingsPress(720f, 360f, 1f, page = 0, x = 680f, y = 320f),
        )
        assertEquals(
            SettingsAction.Back,
            resolveSettingsPress(720f, 360f, 1f, page = 1, x = 220f, y = 320f),
        )
    }
}
