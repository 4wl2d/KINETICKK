// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import androidx.compose.ui.geometry.Rect
import kotlin.math.floor
import kotlin.math.min

internal data class SettingsGroupTab(val group: SettingsGroup, val bounds: Rect)

/** One geometry for canvas drawing, pointer targets and accessible controls. */
internal data class SettingsLayout(
    val bounds: Rect,
    val tabs: List<SettingsGroupTab>,
    val visibleRows: List<SettingsRow>,
    val startY: Float,
    val spacing: Float,
    val page: Int,
    val maxPage: Int,
)

internal fun SettingsLayout.volumeBounds(density: Float): Rect? {
    val index = visibleRows.indexOf(SettingsRow.MASTER_VOLUME)
    if (index < 0) return null
    val top = startY + spacing * index
    return Rect(bounds.left + 20f * density, top, bounds.right - 20f * density,
        min(top + 80f * density, bounds.bottom - 64f * density))
}

internal fun settingsLayout(
    screenWidth: Float,
    screenHeight: Float,
    density: Float,
    group: SettingsGroup,
    page: Int,
): SettingsLayout {
    fun d(value: Float): Float = value * density
    val width = min(d(640f), screenWidth - d(30f)).coerceAtLeast(0f)
    val height = min(d(468f), screenHeight - d(30f)).coerceAtLeast(0f)
    val left = (screenWidth - width) * 0.5f
    val top = (screenHeight - height) * 0.5f
    val bounds = Rect(left, top, left + width, top + height)
    val tabWidth = ((width - d(40f) - d(6f) * (SettingsGroup.entries.size - 1)) / SettingsGroup.entries.size).coerceAtLeast(0f)
    val tabs = SettingsGroup.entries.mapIndexed { index, tabGroup ->
        val tabLeft = left + d(20f) + (tabWidth + d(6f)) * index
        SettingsGroupTab(tabGroup, Rect(tabLeft, top + d(70f), tabLeft + tabWidth, top + d(106f)))
    }
    val startY = top + d(116f)
    val availableHeight = (bounds.bottom - d(64f) - startY).coerceAtLeast(0f)
    val rowsPerPage = floor(availableHeight / d(32f)).toInt().coerceIn(1, group.rows.size)
    val maxPage = group.rows.lastIndex / rowsPerPage
    val visiblePage = page.coerceIn(0, maxPage)
    val pageStart = visiblePage * rowsPerPage
    val visibleRows = group.rows.subList(pageStart, min(pageStart + rowsPerPage, group.rows.size))
    return SettingsLayout(bounds, tabs, visibleRows, startY, min(d(48f), availableHeight / visibleRows.size), visiblePage, maxPage)
}
