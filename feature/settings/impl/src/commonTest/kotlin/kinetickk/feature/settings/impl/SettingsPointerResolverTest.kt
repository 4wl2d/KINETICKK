// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.settings.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsPointerResolverTest {
    @Test
    fun desktopMasterVolumePlusUsesTheOriginalHitbox() {
        assertEquals(
            SettingsAction.Adjust(SettingsRow.MASTER_VOLUME, direction = 1),
            resolveSettingsPress(
                screenWidth = 1_280f,
                screenHeight = 720f,
                density = 1f,
                page = 0,
                x = 910f,
                y = 230f,
            ),
        )
        assertNull(
            resolveSettingsPress(1_280f, 720f, 1f, page = 0, x = 700f, y = 230f),
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
