// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.ball.profile.interaction.localization.ProfileText
import kinetickk.ball.content.api.localizedContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import kinetickk.foundation.design.Cyan
import kinetickk.foundation.design.DarkLine
import kinetickk.foundation.design.Gold
import kinetickk.foundation.design.Muted
import kinetickk.foundation.design.Orange
import kinetickk.foundation.design.Red
import kinetickk.foundation.design.TextMeasurer
import kinetickk.foundation.design.Violet
import kinetickk.foundation.design.White
import kinetickk.foundation.design.d
import kinetickk.foundation.design.drawFooterBack
import kinetickk.foundation.design.drawLabel
import kinetickk.foundation.design.drawOverlayFrame
import kinetickk.foundation.design.drawPagedFooter
import kinetickk.foundation.design.formatCompact
import kinetickk.foundation.design.formatMultiplier
import kinetickk.foundation.design.overlayBounds
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.DamageNumberSize
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.interaction.settings.api.SettingsRenderModel
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
    drawLabel(textMeasurer, textMeasurer.language.text(ProfileText.SettingsTitle), bounds.left + d(24f), bounds.top + d(12f), 19f, White, weight = FontWeight.Bold)
    drawLabel(textMeasurer, textMeasurer.language.text(ProfileText.SettingsHint), bounds.right - d(24f), bounds.top + d(43f), 7f, Muted, alignRight = true)

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
        val labelY = top + d(3f)
        val valueY = top + max(0f, (rowHeight - d(8f * textMeasurer.scale)) * 0.5f)
        val buttonY = top + max(0f, (rowHeight - d(14f * textMeasurer.scale)) * 0.5f)
        val value = settingValue(model.preferences, row, textMeasurer.language)
        drawRect(Color(0x66101225), Offset(bounds.left + d(20f), top), Size(bounds.width - d(40f), rowHeight))
        drawRect(DarkLine, Offset(bounds.left + d(20f), top), Size(bounds.width - d(40f), rowHeight), style = Stroke(d(1f)))
        drawLabel(textMeasurer, textMeasurer.language.text(SETTINGS_LABELS[row.ordinal]), bounds.left + d(30f), labelY, 9f, White, weight = FontWeight.Bold, maxWidth = controlLeft - bounds.left - d(40f), maxLines = 2)
        if (row == SettingsRow.LANGUAGE) {
            AppLanguage.entries.forEachIndexed { languageIndex, language ->
                val optionWidth = (controlRight - controlLeft) * 0.5f
                val optionLeft = controlLeft + optionWidth * languageIndex
                val selected = model.preferences.language == language
                drawRect(Violet.copy(alpha = if (selected) 0.24f else 0.05f), Offset(optionLeft, controlTop), Size(optionWidth, controlHeight))
                drawRect(if (selected) Violet else DarkLine, Offset(optionLeft, controlTop), Size(optionWidth, controlHeight), style = Stroke(d(if (selected) 2f else 1f)))
                drawLabel(textMeasurer, language.nativeName, optionLeft + optionWidth * 0.5f, valueY, 8f, if (selected) White else Muted, centered = true, maxWidth = optionWidth - d(6f))
            }
            return@forEachIndexed
        }
        if (row == SettingsRow.DAMAGE_COLOR_THRESHOLDS) {
            SettingsDamageNumberColors.forEachIndexed { colorIndex, color ->
                drawCircle(
                    color = color,
                    radius = d(3.5f),
                    center = Offset(bounds.left + d(35f) + d(15f) * colorIndex, top + rowHeight - d(5f)),
                )
            }
        }
        drawRect(Violet.copy(alpha = 0.08f), Offset(controlLeft, controlTop), Size(controlRight - controlLeft, controlHeight))
        drawLine(DarkLine, Offset(controlLeft + d(42f), controlTop), Offset(controlLeft + d(42f), controlTop + controlHeight), d(1f))
        drawLine(DarkLine, Offset(controlRight - d(42f), controlTop), Offset(controlRight - d(42f), controlTop + controlHeight), d(1f))
        drawLabel(textMeasurer, "−", controlLeft + d(21f), buttonY, 14f, Violet, centered = true, weight = FontWeight.Bold)
        drawLabel(textMeasurer, "+", controlRight - d(21f), buttonY, 14f, Violet, centered = true, weight = FontWeight.Bold)
        val valueColor = when {
            value == textMeasurer.language.text(ProfileText.Off) -> Red
            row == SettingsRow.DAMAGE_COLOR_THRESHOLDS -> Orange
            else -> Cyan
        }
        val displayValue = if (row == SettingsRow.DAMAGE_COLOR_THRESHOLDS && value.length > 12) {
            value.replace('/', '\n')
        } else value
        val stackedValue = '\n' in displayValue
        drawLabel(
            textMeasurer, displayValue, (controlLeft + controlRight) * 0.5f,
            if (stackedValue) top + d(1f) else valueY,
            if (stackedValue) 5.5f else 8f, valueColor, centered = true, weight = FontWeight.Bold,
            maxWidth = controlRight - controlLeft - d(86f), maxLines = if (stackedValue) 3 else 1,
        )
    }
    if (maxPage > 0) {
        drawPagedFooter(textMeasurer, bounds, visiblePage, maxPage, Violet)
    } else {
        drawFooterBack(textMeasurer, bounds, Violet)
    }
}

private val SettingsDamageNumberColors = listOf(Color(0xFFFFF2C2), Gold, Orange, Red)

internal fun settingsRowsPerPage(availableHeight: Float, density: Float): Int {
    val logicalHeight = availableHeight.coerceAtLeast(0f) / density.coerceAtLeast(1f)
    return floor(logicalHeight / SETTINGS_MIN_ROW_SPACING_DP)
        .toInt()
        .coerceIn(1, SettingsRow.entries.size)
}

internal fun settingValue(
    preferences: PlayerPreferences,
    row: SettingsRow,
    language: AppLanguage = preferences.language,
): String = when (row) {
    SettingsRow.LANGUAGE -> preferences.language.nativeName
    SettingsRow.SFX -> if (preferences.soundEnabled) language.text(ProfileText.On) else language.text(ProfileText.Off)
    SettingsRow.MUSIC -> if (preferences.musicEnabled) language.text(ProfileText.On) else language.text(ProfileText.Off)
    SettingsRow.MASTER_VOLUME -> "${(preferences.masterVolume * 100f).roundToInt()}%"
    SettingsRow.SIMULATION_SPEED -> formatMultiplier(preferences.simulationSpeed, language)
    SettingsRow.TEXT_SIZE -> "${(preferences.textScale * 100f).roundToInt()}%"
    SettingsRow.SCREEN_SHAKE -> if (preferences.screenShake) language.text(ProfileText.On) else language.text(ProfileText.Off)
    SettingsRow.PARTICLES -> language.text(when (preferences.particleDensity) {
        ParticleDensity.LOW -> ProfileText.Low
        ParticleDensity.NORMAL -> ProfileText.Normal
        ParticleDensity.HIGH -> ProfileText.High
    })
    SettingsRow.DAMAGE_NUMBERS -> if (preferences.damageNumbers) language.text(ProfileText.On) else language.text(ProfileText.Off)
    SettingsRow.DAMAGE_NUMBER_SIZE -> language.text(when (preferences.damageNumberSize) {
        DamageNumberSize.SMALL -> ProfileText.Small
        DamageNumberSize.NORMAL -> ProfileText.Normal
        DamageNumberSize.LARGE -> ProfileText.Large
        DamageNumberSize.HUGE -> ProfileText.Huge
    })
    SettingsRow.DAMAGE_NUMBER_FORMAT -> language.text(when (preferences.damageNumberFormat) {
        DamageNumberFormat.COMPACT -> ProfileText.Compact
        DamageNumberFormat.FULL -> ProfileText.Full
    })
    SettingsRow.DAMAGE_COLOR_THRESHOLDS -> {
        val first = preferences.damageNumberTierThreshold.toLong()
        val second = first * DAMAGE_NUMBER_POWERFUL_MULTIPLIER
        val third = first * DAMAGE_NUMBER_DEVASTATING_MULTIPLIER
        "${formatCompact(first, language)}/${formatCompact(second, language)}/${formatCompact(third, language)}"
    }
}

private const val SETTINGS_MIN_ROW_SPACING_DP = 32f
private const val DAMAGE_NUMBER_POWERFUL_MULTIPLIER = 4L
private const val DAMAGE_NUMBER_DEVASTATING_MULTIPLIER = 20L

private val SETTINGS_LABELS = listOf(
    ProfileText.Language,
    ProfileText.Sfx,
    ProfileText.Music,
    ProfileText.MasterVolume,
    ProfileText.SimulationSpeed,
    ProfileText.TextSize,
    ProfileText.ScreenShake,
    ProfileText.Particles,
    ProfileText.DamageNumbers,
    ProfileText.DamageNumberSize,
    ProfileText.DamageNumberFormat,
    ProfileText.DamageColorTiers,
)
