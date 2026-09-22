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
    val width = min(d(900f), screenWidth - d(30f)).coerceAtLeast(0f)
    val height = min(d(620f), screenHeight - d(30f)).coerceAtLeast(0f)
    val left = (screenWidth - width) * 0.5f
    val top = (screenHeight - height) * 0.5f
    val bounds = Rect(left, top, left + width, top + height)
    val compact = screenHeight / density < 480f
    val columns = if (!compact && screenWidth / density < 520f) 2 else SettingsGroup.entries.size
    val header = if (compact) 70f else 94f
    val rowStart = if (compact) 116f else if (columns == 2) 196f else 146f
    val tabWidth = ((width - d(40f) - d(6f) * (columns - 1)) / columns).coerceAtLeast(0f)
    val tabs = SettingsGroup.entries.mapIndexed { index, tabGroup ->
        val tabLeft = left + d(20f) + (tabWidth + d(6f)) * (index % columns)
        val tabTop = top + d(header + 46f * (index / columns))
        SettingsGroupTab(tabGroup, Rect(tabLeft, tabTop, tabLeft + tabWidth, tabTop + d(40f)))
    }
    val startY = top + d(rowStart)
    val availableHeight = (bounds.bottom - d(64f) - startY).coerceAtLeast(0f)
    val rowsPerPage = floor(availableHeight / d(44f)).toInt().coerceIn(1, group.rows.size)
    val maxPage = group.rows.lastIndex / rowsPerPage
    val visiblePage = page.coerceIn(0, maxPage)
    val pageStart = visiblePage * rowsPerPage
    val visibleRows = group.rows.subList(pageStart, min(pageStart + rowsPerPage, group.rows.size))
    // Sound reserves at least 54 dp for the slider/editor even on a 360 dp landscape viewport.
    val rowLimit = if (group == SettingsGroup.SOUND) 48f else 64f
    return SettingsLayout(bounds, tabs, visibleRows, startY, min(d(rowLimit), availableHeight / visibleRows.size), visiblePage, maxPage)
}
