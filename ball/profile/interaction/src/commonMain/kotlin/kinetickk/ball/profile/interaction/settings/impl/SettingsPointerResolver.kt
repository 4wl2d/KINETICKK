// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import kinetickk.foundation.common.localization.AppLanguage
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
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
    group: SettingsGroup = SettingsGroup.GAME,
): SettingsAction? {
    fun d(value: Float): Float = value * density

    val layout = settingsLayout(screenWidth, screenHeight, density, group, page)
    val position = Offset(x, y)
    if (!layout.bounds.contains(position)) return null
    layout.tabs.firstOrNull { it.bounds.contains(position) }?.let { return SettingsAction.SelectGroup(it.group) }
    val left = layout.bounds.left
    val right = layout.bounds.right
    val bottom = layout.bounds.bottom
    if (y > bottom - d(55f)) {
        if (layout.maxPage == 0 || x < left + (right - left) * 0.45f) return SettingsAction.Back
        return if (x < right - d(85f)) {
            SettingsAction.PageSelected(max(0, layout.page - 1))
        } else {
            SettingsAction.PageSelected(min(layout.maxPage, layout.page + 1))
        }
    }

    val spacing = layout.spacing
    if (spacing <= 0f) return null
    val visibleIndex = floor((y - layout.startY) / spacing).toInt()
    val row = layout.visibleRows.getOrNull(visibleIndex) ?: return null
    // The Compose slider and text field own all volume input.
    if (row == SettingsRow.MASTER_VOLUME) return null
    if (x !in right - d(190f)..right - d(20f)) return null
    val rowTop = layout.startY + spacing * visibleIndex
    if (y > rowTop + spacing - d(4f)) return null
    if (row == SettingsRow.LANGUAGE) {
        return SettingsAction.SelectLanguage(
            if (x < right - d(105f)) AppLanguage.Russian else AppLanguage.English,
        )
    }
    val direction = if (x < right - d(105f)) -1 else 1
    return SettingsAction.Adjust(row, direction)
}

internal data class SettingsLanguageOption(val language: AppLanguage, val bounds: Rect)

/** The two explicit choices share the canvas row's geometry and do not depend on translated text. */
internal fun settingsLanguageOptions(
    screenWidth: Float,
    screenHeight: Float,
    density: Float,
    page: Int,
    group: SettingsGroup = SettingsGroup.GAME,
): List<SettingsLanguageOption> {
    if (screenWidth <= 0f || screenHeight <= 0f) return emptyList()
    fun d(value: Float): Float = value * density
    val layout = settingsLayout(screenWidth, screenHeight, density, group, page)
    val rowIndex = layout.visibleRows.indexOf(SettingsRow.LANGUAGE)
    if (rowIndex < 0) return emptyList()
    val right = layout.bounds.right
    val startY = layout.startY + layout.spacing * rowIndex
    val spacing = layout.spacing
    if (spacing <= d(12f)) return emptyList()
    return AppLanguage.entries.mapIndexed { index, language ->
        val left = right - d(190f) + d(85f) * index
        SettingsLanguageOption(language, Rect(left, startY + d(4f), left + d(85f), startY + spacing - d(8f)))
    }
}
