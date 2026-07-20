// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.settings.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import kinetickk.core.design.Cyan
import kinetickk.core.design.DamageNumberColors
import kinetickk.core.design.DarkLine
import kinetickk.core.design.Muted
import kinetickk.core.design.Orange
import kinetickk.core.design.Red
import kinetickk.core.design.TextMeasurer
import kinetickk.core.design.Violet
import kinetickk.core.design.White
import kinetickk.core.design.d
import kinetickk.core.design.drawFooterBack
import kinetickk.core.design.drawLabel
import kinetickk.core.design.drawOverlayFrame
import kinetickk.core.design.drawPagedFooter
import kinetickk.core.design.formatCompact
import kinetickk.core.design.formatMultiplier
import kinetickk.core.design.overlayBounds
import kinetickk.core.profile.api.DamageNumberFormat
import kinetickk.core.profile.api.DamageNumberSize
import kinetickk.core.profile.api.ParticleDensity
import kinetickk.core.profile.api.PlayerPreferences
import kinetickk.feature.settings.api.SettingsRenderModel
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal fun DrawScope.drawSettings(
    model: SettingsRenderModel,
    page: Int,
    textMeasurer: TextMeasurer,
) {
    drawRect(Color(0xD9050610))
    val bounds = overlayBounds(640f, 620f)
    drawOverlayFrame(bounds, Violet)
    drawLabel(textMeasurer, "SYSTEM SETTINGS", bounds.left + d(24f), bounds.top + d(24f), 19f, White, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "− / + ADJUST // DAMAGE HEAT: YELLOW > RED", bounds.right - d(24f), bounds.top + d(29f), 7f, Muted, alignRight = true)

    val startY = bounds.top + d(72f)
    val settingsBottom = bounds.bottom - d(64f)
    val availableHeight = settingsBottom - startY
    val rowsPerPage = settingsRowsPerPage(availableHeight, density)
    val maxPage = SettingsRow.entries.lastIndex / rowsPerPage
    val visiblePage = page.coerceIn(0, maxPage)
    val pageStart = visiblePage * rowsPerPage
    val visibleRows = SettingsRow.entries.subList(
        pageStart,
        min(pageStart + rowsPerPage, SettingsRow.entries.size),
    )
    val spacing = min(d(48f), availableHeight / visibleRows.size)
    visibleRows.forEachIndexed { index, row ->
        val top = startY + spacing * index
        val rowHeight = spacing - d(4f)
        val controlLeft = bounds.right - d(190f)
        val controlRight = bounds.right - d(20f)
        val controlTop = top + d(4f)
        val controlHeight = rowHeight - d(8f)
        val labelY = top + max(0f, (rowHeight - d(9f * textMeasurer.scale)) * 0.5f)
        val valueY = top + max(0f, (rowHeight - d(8f * textMeasurer.scale)) * 0.5f)
        val buttonY = top + max(0f, (rowHeight - d(14f * textMeasurer.scale)) * 0.5f)
        val value = settingValue(model.preferences, row)
        drawRect(Color(0x66101225), Offset(bounds.left + d(20f), top), Size(bounds.width - d(40f), rowHeight))
        drawRect(DarkLine, Offset(bounds.left + d(20f), top), Size(bounds.width - d(40f), rowHeight), style = Stroke(d(1f)))
        drawLabel(textMeasurer, SETTINGS_LABELS[row.ordinal], bounds.left + d(35f), labelY, 9f, White, weight = FontWeight.Bold)
        if (row == SettingsRow.DAMAGE_COLOR_THRESHOLDS) {
            DamageNumberColors.forEachIndexed { colorIndex, color ->
                drawCircle(
                    color = color,
                    radius = d(3.5f),
                    center = Offset(controlLeft - d(65f) + d(15f) * colorIndex, top + rowHeight * 0.5f),
                )
            }
        }
        drawRect(Violet.copy(alpha = 0.08f), Offset(controlLeft, controlTop), Size(controlRight - controlLeft, controlHeight))
        drawLine(DarkLine, Offset(controlLeft + d(42f), controlTop), Offset(controlLeft + d(42f), controlTop + controlHeight), d(1f))
        drawLine(DarkLine, Offset(controlRight - d(42f), controlTop), Offset(controlRight - d(42f), controlTop + controlHeight), d(1f))
        drawLabel(textMeasurer, "−", controlLeft + d(21f), buttonY, 14f, Violet, centered = true, weight = FontWeight.Bold)
        drawLabel(textMeasurer, "+", controlRight - d(21f), buttonY, 14f, Violet, centered = true, weight = FontWeight.Bold)
        val valueColor = when {
            value == "OFF" -> Red
            row == SettingsRow.DAMAGE_COLOR_THRESHOLDS -> Orange
            else -> Cyan
        }
        drawLabel(textMeasurer, value, (controlLeft + controlRight) * 0.5f, valueY, 8f, valueColor, centered = true, weight = FontWeight.Bold)
    }
    if (maxPage > 0) {
        drawPagedFooter(textMeasurer, bounds, visiblePage, maxPage, Violet)
    } else {
        drawFooterBack(textMeasurer, bounds, Violet)
    }
}

internal fun settingsRowsPerPage(availableHeight: Float, density: Float): Int {
    val logicalHeight = availableHeight.coerceAtLeast(0f) / density.coerceAtLeast(1f)
    return floor(logicalHeight / SETTINGS_MIN_ROW_SPACING_DP)
        .toInt()
        .coerceIn(1, SettingsRow.entries.size)
}

internal fun settingValue(preferences: PlayerPreferences, row: SettingsRow): String = when (row) {
    SettingsRow.SFX -> if (preferences.soundEnabled) "ON" else "OFF"
    SettingsRow.MUSIC -> if (preferences.musicEnabled) "ON" else "OFF"
    SettingsRow.MASTER_VOLUME -> "${(preferences.masterVolume * 100f).roundToInt()}%"
    SettingsRow.SIMULATION_SPEED -> formatMultiplier(preferences.simulationSpeed)
    SettingsRow.TEXT_SIZE -> "${(preferences.textScale * 100f).roundToInt()}%"
    SettingsRow.SCREEN_SHAKE -> if (preferences.screenShake) "ON" else "OFF"
    SettingsRow.PARTICLES -> preferences.particleDensity.name
    SettingsRow.DAMAGE_NUMBERS -> if (preferences.damageNumbers) "ON" else "OFF"
    SettingsRow.DAMAGE_NUMBER_SIZE -> preferences.damageNumberSize.name
    SettingsRow.DAMAGE_NUMBER_FORMAT -> preferences.damageNumberFormat.name
    SettingsRow.DAMAGE_COLOR_THRESHOLDS -> {
        val first = preferences.damageNumberTierThreshold.toLong()
        val second = first * DAMAGE_NUMBER_POWERFUL_MULTIPLIER
        val third = first * DAMAGE_NUMBER_DEVASTATING_MULTIPLIER
        "${formatCompact(first)}/${formatCompact(second)}/${formatCompact(third)}"
    }
}

private const val SETTINGS_MIN_ROW_SPACING_DP = 32f
private const val DAMAGE_NUMBER_POWERFUL_MULTIPLIER = 4L
private const val DAMAGE_NUMBER_DEVASTATING_MULTIPLIER = 20L

private val SETTINGS_LABELS = listOf(
    "SFX",
    "MUSIC",
    "MASTER VOLUME",
    "SIMULATION SPEED",
    "TEXT SIZE",
    "SCREEN SHAKE",
    "PARTICLES",
    "DAMAGE NUMBERS",
    "DAMAGE NUMBER SIZE",
    "DAMAGE NUMBER FORMAT",
    "DAMAGE COLOR TIERS",
)
