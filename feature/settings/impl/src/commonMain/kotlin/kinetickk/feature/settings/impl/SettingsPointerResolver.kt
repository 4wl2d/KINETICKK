// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.settings.impl

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal fun resolveSettingsPress(
    screenWidth: Float,
    screenHeight: Float,
    density: Float,
    page: Int,
    x: Float,
    y: Float,
): SettingsAction? {
    val scale = density.coerceAtLeast(1f)
    fun d(value: Float): Float = value * scale

    val width = min(d(640f), screenWidth - d(30f))
    val height = min(d(620f), screenHeight - d(30f))
    val left = (screenWidth - width) * 0.5f
    val top = (screenHeight - height) * 0.5f
    val right = left + width
    val bottom = top + height
    val startY = top + d(72f)
    val availableHeight = bottom - d(64f) - startY
    val rowsPerPage = settingsRowsPerPage(availableHeight, scale)
    val maxPage = SettingsRow.entries.lastIndex / rowsPerPage
    val currentPage = page.coerceIn(0, maxPage)
    if (y > bottom - d(55f)) {
        if (x !in left..right) return null
        if (maxPage == 0 || x < left + (right - left) * 0.45f) return SettingsAction.Back
        return if (x < right - d(85f)) {
            SettingsAction.PageSelected(max(0, currentPage - 1))
        } else {
            SettingsAction.PageSelected(min(maxPage, currentPage + 1))
        }
    }

    val pageStart = currentPage * rowsPerPage
    val visibleCount = min(rowsPerPage, SettingsRow.entries.size - pageStart)
    val spacing = min(d(48f), availableHeight / visibleCount)
    if (spacing <= 0f) return null
    val visibleIndex = floor((y - startY) / spacing).toInt()
    val rowIndex = pageStart + visibleIndex
    if (visibleIndex !in 0 until visibleCount || rowIndex !in SettingsRow.entries.indices) return null
    if (x !in right - d(190f)..right - d(20f)) return null
    val rowTop = startY + spacing * visibleIndex
    if (y > rowTop + spacing - d(4f)) return null
    val direction = if (x < right - d(105f)) -1 else 1
    return SettingsAction.Adjust(SettingsRow.entries[rowIndex], direction)
}
