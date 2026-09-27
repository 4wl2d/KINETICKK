// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kinetickk.foundation.common.localization.AppLanguage

/** Pointer targets of the Settings canvas; the accessible controls reuse the same rects. */
internal sealed interface SettingsTarget {
    data object Back : SettingsTarget
    data class Tab(val group: SettingsGroup) : SettingsTarget
    data class Page(val page: Int) : SettingsTarget
    data class Info(val row: SettingsRow) : SettingsTarget
    data class Option(val row: SettingsRow, val option: Int) : SettingsTarget
    data class Toggle(val row: SettingsRow) : SettingsTarget
    data class Step(val row: SettingsRow, val direction: Int) : SettingsTarget
}

internal val SettingsTarget.row: SettingsRow?
    get() = when (this) {
        is SettingsTarget.Info -> row
        is SettingsTarget.Option -> row
        is SettingsTarget.Toggle -> row
        is SettingsTarget.Step -> row
        SettingsTarget.Back, is SettingsTarget.Tab, is SettingsTarget.Page -> null
    }

internal fun SettingsTarget.toAction(): SettingsAction = when (this) {
    SettingsTarget.Back -> SettingsAction.Back
    is SettingsTarget.Tab -> SettingsAction.SelectGroup(group)
    is SettingsTarget.Page -> SettingsAction.PageSelected(page)
    is SettingsTarget.Info -> SettingsAction.ToggleInfo(row)
    is SettingsTarget.Option -> if (row == SettingsRow.LANGUAGE) {
        SettingsAction.SelectLanguage(AppLanguage.entries[option])
    } else {
        SettingsAction.Select(row, option)
    }
    is SettingsTarget.Toggle -> SettingsAction.Adjust(row, 1)
    is SettingsTarget.Step -> SettingsAction.Adjust(row, direction)
}

/**
 * The target under ([x], [y]). Segmented cells split the gap between neighbours and extend to
 * the row's height; small controls extend to the 44 dp touch minimum. The master volume strip
 * belongs to the Compose slider and numeric editor, so it resolves to nothing here.
 */
internal fun SettingsLayout.targetAt(x: Float, y: Float): SettingsTarget? {
    val position = Offset(x, y)
    if (!bounds.contains(position)) return null
    if (back.settingsTouch(density).contains(position)) return SettingsTarget.Back
    tabs.firstOrNull { it.bounds.settingsTouch(density).contains(position) }?.let { return SettingsTarget.Tab(it.group) }
    pages.forEachIndexed { index, pip -> if (pip.settingsTouch(density).contains(position)) return SettingsTarget.Page(index) }
    val row = rows.firstOrNull { it.bounds.top <= y && y < it.bounds.bottom } ?: return null
    if (row.info.settingsTouch(density).contains(position)) return SettingsTarget.Info(row.row)
    val band = controlBand(row)
    if (y < band.first || y > band.second) return null
    when (row.row.control) {
        SettingsControl.SEGMENTED -> {
            val half = 1.5f * density
            row.options.forEachIndexed { option, cell ->
                if (x >= cell.left - half && x <= cell.right + half) return SettingsTarget.Option(row.row, option)
            }
        }
        SettingsControl.TOGGLE -> {
            val toggle = row.toggle ?: return null
            if (Rect(toggle.left - 8f * density, band.first, toggle.right + 8f * density, band.second).contains(position)) {
                return SettingsTarget.Toggle(row.row)
            }
        }
        SettingsControl.SLIDER -> {
            if (row.row == SettingsRow.MASTER_VOLUME) return null
            val decrease = row.decrease ?: return null
            val increase = row.increase ?: return null
            if (x in decrease.settingsTouch(density).left..decrease.right + 4f * density) return SettingsTarget.Step(row.row, -1)
            if (x in increase.left - 4f * density..increase.settingsTouch(density).right) return SettingsTarget.Step(row.row, 1)
        }
    }
    return null
}

/** Vertical band of a row's control: the whole row, or the control line of a stacked row. */
private fun SettingsLayout.controlBand(row: SettingsRowLayout): Pair<Float, Float> {
    val control = row.options.firstOrNull() ?: row.toggle ?: row.decrease ?: return row.bounds.top to row.bounds.bottom
    // The role swatches under Color vision are a readout; presses on them choose nothing.
    val bottom = row.swatches.firstOrNull()?.top ?: row.bounds.bottom
    return if (control.top >= row.info.bottom) {
        // Stacked portrait row: the control line owns the row below the label line.
        row.label.bottom to bottom
    } else {
        row.bounds.top to bottom
    }
}

internal fun resolveSettingsPress(layout: SettingsLayout, x: Float, y: Float): SettingsAction? =
    layout.targetAt(x, y)?.toAction()
