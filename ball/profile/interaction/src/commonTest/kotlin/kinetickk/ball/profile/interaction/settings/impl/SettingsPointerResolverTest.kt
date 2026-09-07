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
            assertEquals(options, settingsLanguageOptions(width, height, density, page = 1))
            for (group in listOf(SettingsGroup.SOUND, SettingsGroup.GRAPHICS, SettingsGroup.INTERFACE)) {
                assertEquals(emptyList(), settingsLanguageOptions(width, height, density, page = 0, group = group))
            }
        }
    }

    @Test
    fun interfaceGroupExposesTheStatisticsSideInWideAndNarrowViewports() {
        for ((width, height) in listOf(1000f to 720f, 390f to 720f, 780f to 360f)) {
            val layout = settingsLayout(width, height, 1f, SettingsGroup.INTERFACE, 0)
            assertEquals(listOf(SettingsRow.TEXT_SIZE, SettingsRow.RUN_STATISTICS_SIDE), layout.visibleRows)
            val row = layout.visibleRows.indexOf(SettingsRow.RUN_STATISTICS_SIDE)
            assertEquals(SettingsAction.Adjust(SettingsRow.RUN_STATISTICS_SIDE, 1), resolveSettingsPress(
                width, height, 1f, 0, layout.bounds.right - 30f,
                layout.startY + layout.spacing * (row + 0.5f), SettingsGroup.INTERFACE,
            ))
        }
    }

    @Test
    fun canvasDoesNotDispatchOldVolumeButtonsBehindComposeControls() {
        assertNull(
            resolveSettingsPress(
                screenWidth = 1_280f,
                screenHeight = 720f,
                density = 1f,
                page = 0,
                x = 910f,
                y = 362f,
                group = SettingsGroup.SOUND,
            ),
        )
        assertNull(
            resolveSettingsPress(1_280f, 720f, 1f, page = 0, x = 700f, y = 362f, group = SettingsGroup.SOUND),
        )
    }

    @Test
    fun volumeControlFitsAboveFooterAcrossViewportsAndDensities() {
        for ((width, height, density) in listOf(Triple(1280f, 720f, 1f), Triple(390f, 720f, 1f), Triple(1440f, 720f, 2f))) {
            val layout = settingsLayout(width, height, density, SettingsGroup.SOUND, 0)
            val bounds = kotlin.test.assertNotNull(layout.volumeBounds(density))
            kotlin.test.assertTrue(bounds.height >= 54f * density)
            kotlin.test.assertTrue(bounds.bottom <= layout.bounds.bottom - 64f * density)
            for (x in listOf(bounds.left + 1f, bounds.center.x, bounds.right - 1f)) {
                assertNull(resolveSettingsPress(width, height, density, 0, x, bounds.center.y, SettingsGroup.SOUND))
            }
            assertNull(settingsLayout(width, height, density, SettingsGroup.GAME, 0).volumeBounds(density))
        }
    }

    @Test
    fun shortViewportPaginatesWithinGraphicsAndKeepsFooterZones() {
        val layout = settingsLayout(720f, 360f, 1f, SettingsGroup.GRAPHICS, page = 0)
        assertEquals(4, layout.visibleRows.size)
        assertEquals(1, layout.maxPage)

        assertEquals(
            SettingsAction.PageSelected(1),
            resolveSettingsPress(720f, 360f, 1f, page = 0, x = 660f, y = 320f, group = SettingsGroup.GRAPHICS),
        )
        assertEquals(
            SettingsAction.Back,
            resolveSettingsPress(720f, 360f, 1f, page = 1, x = 220f, y = 320f, group = SettingsGroup.GRAPHICS),
        )
        assertEquals(
            SettingsAction.Adjust(SettingsRow.DAMAGE_COLOR_THRESHOLDS, 1),
            resolveSettingsPress(720f, 360f, 1f, page = 1, x = 639f, y = 203f, group = SettingsGroup.GRAPHICS),
        )
        assertNull(resolveSettingsPress(720f, 360f, 1f, page = 0, x = 220f, y = 350f))
    }

    @Test
    fun everyGroupTabMatchesItsPointerTargetAtDifferentDensities() {
        for ((width, height, density) in listOf(Triple(1280f, 720f, 1f), Triple(390f, 720f, 1f), Triple(1440f, 720f, 2f))) {
            val layout = settingsLayout(width, height, density, SettingsGroup.GRAPHICS, page = 1)
            layout.tabs.forEach { tab ->
                assertEquals(
                    SettingsAction.SelectGroup(tab.group),
                    resolveSettingsPress(width, height, density, 1, tab.bounds.center.x, tab.bounds.center.y, SettingsGroup.GRAPHICS),
                )
            }
        }
    }
}
