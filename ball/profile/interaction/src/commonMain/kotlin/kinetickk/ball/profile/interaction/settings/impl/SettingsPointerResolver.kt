// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import kinetickk.foundation.common.localization.AppLanguage
import androidx.compose.ui.geometry.Rect
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
    if (SettingsRow.entries[rowIndex] == SettingsRow.LANGUAGE) {
        return SettingsAction.SelectLanguage(
            if (x < right - d(105f)) AppLanguage.Russian else AppLanguage.English,
        )
    }
    val direction = if (x < right - d(105f)) -1 else 1
    return SettingsAction.Adjust(SettingsRow.entries[rowIndex], direction)
}

internal data class SettingsLanguageOption(val language: AppLanguage, val bounds: Rect)

/** The two explicit choices share the canvas row's geometry and do not depend on translated text. */
internal fun settingsLanguageOptions(
    screenWidth: Float,
    screenHeight: Float,
    density: Float,
    page: Int,
): List<SettingsLanguageOption> {
    if (screenWidth <= 0f || screenHeight <= 0f) return emptyList()
    val scale = density.coerceAtLeast(1f)
    fun d(value: Float): Float = value * scale
    val width = min(d(640f), screenWidth - d(30f))
    val height = min(d(620f), screenHeight - d(30f))
    val right = (screenWidth + width) * 0.5f
    val startY = (screenHeight - height) * 0.5f + d(72f)
    val availableHeight = height - d(136f)
    val rowsPerPage = settingsRowsPerPage(availableHeight, scale)
    val maxPage = SettingsRow.entries.lastIndex / rowsPerPage
    if (page.coerceIn(0, maxPage) != 0) return emptyList()
    val visibleCount = min(rowsPerPage, SettingsRow.entries.size)
    val spacing = min(d(48f), availableHeight / visibleCount)
    if (spacing <= d(12f)) return emptyList()
    return AppLanguage.entries.mapIndexed { index, language ->
        val left = right - d(190f) + d(85f) * index
        SettingsLanguageOption(language, Rect(left, startY + d(4f), left + d(85f), startY + spacing - d(8f)))
    }
}
