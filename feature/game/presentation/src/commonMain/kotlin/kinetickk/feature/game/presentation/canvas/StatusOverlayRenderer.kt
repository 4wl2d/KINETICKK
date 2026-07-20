// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.presentation.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import kinetickk.feature.game.domain.model.abbreviateNumber
import kinetickk.feature.game.domain.model.DAMAGE_NUMBER_DEVASTATING_MULTIPLIER
import kinetickk.feature.game.domain.model.DAMAGE_NUMBER_POWERFUL_MULTIPLIER
import kinetickk.feature.game.domain.model.formatRunTime
import kinetickk.feature.game.domain.model.SettingsRow
import kinetickk.feature.game.domain.model.settingsRowsPerPage
import kinetickk.feature.game.domain.projection.GameProjection
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal fun DrawScope.drawPause(textMeasurer: TextMeasurer) {
    drawRect(Color(0xC9050610))
    drawLabel(textMeasurer, "SYSTEM PAUSED", size.width * 0.5f, size.height * 0.30f, 28f, White, centered = true, weight = FontWeight.Bold)
    drawPauseButton(textMeasurer, "RESUME [P / ESC]", size.height * 0.5f, Cyan)
    drawPauseButton(textMeasurer, "SETTINGS [S]", size.height * 0.62f, Violet)
    drawPauseButton(textMeasurer, "RETURN TO MENU", size.height * 0.74f, Red)
}

internal fun DrawScope.drawPauseButton(textMeasurer: TextMeasurer, label: String, top: Float, accent: Color) {
    val left = size.width * 0.5f - d(150f)
    drawRect(accent.copy(alpha = 0.1f), Offset(left, top), Size(d(300f), d(52f)))
    drawRect(accent, Offset(left, top), Size(d(300f), d(52f)), style = Stroke(d(1.5f)))
    drawLabel(textMeasurer, label, size.width * 0.5f, top + d(17f), 11f, accent, centered = true, weight = FontWeight.Bold)
}

internal fun DrawScope.drawEnd(engine: GameProjection, textMeasurer: TextMeasurer, victory: Boolean) {
    drawRect(Color(0xDE050610))
    val color = if (victory) Acid else Red
    drawLabel(textMeasurer, if (victory) "RUN CONQUERED" else engine.message, size.width * 0.5f, size.height * 0.25f, if (size.width / density < 700f) 28f else 42f, color, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, if (victory) "THE ARCHITECT HAS FALLEN" else "THE SINGULARITY REMEMBERS", size.width * 0.5f, size.height * 0.36f, 10f, Muted, centered = true)
    val statY = size.height * 0.47f
    drawLabel(textMeasurer, "TIME ${formatRunTime(engine.elapsed)}", size.width * 0.5f - d(165f), statY, 13f, White, centered = true)
    drawLabel(textMeasurer, "KILLS ${engine.kills}", size.width * 0.5f, statY, 13f, White, centered = true)
    drawLabel(textMeasurer, "MATTER ${formatCompact(engine.runMatter)}", size.width * 0.5f + d(165f), statY, 13f, Acid, centered = true)
    drawLabel(textMeasurer, "WEAPON ${engine.currentWeaponDefinition.name.uppercase()} // LV ${engine.weaponLevel}", size.width * 0.5f, statY + d(38f), 10f, weaponColor(engine.weapon), centered = true)
    drawLabel(textMeasurer, "ITEMS ${engine.acquiredItemCount}   DISCOVERIES ${engine.discoveredItemCount}/400   PEAK ${VelocityNames[engine.velocityTier.coerceIn(VelocityNames.indices)]}", size.width * 0.5f, statY + d(64f), 9f, Muted, centered = true)
    val buttonY = size.height * 0.72f
    drawRect(color.copy(alpha = 0.1f), Offset(size.width * 0.5f - d(155f), buttonY - d(38f)), Size(d(310f), d(76f)))
    drawRect(color, Offset(size.width * 0.5f - d(155f), buttonY - d(38f)), Size(d(310f), d(76f)), style = Stroke(d(2f)))
    drawLabel(textMeasurer, "RE-ENTER [R]", size.width * 0.5f, buttonY - d(10f), 15f, White, centered = true, weight = FontWeight.Bold)
    if (victory) {
        val rebirthTop = buttonY + d(50f)
        val rebirthAccent = if (engine.canRebirth) Acid else Muted
        val rebirthLabel = if (engine.nextRebirthProfile.tier > engine.rebirthLevel) {
            "REBIRTH [B] // TIER ${engine.nextRebirthProfile.tier}"
        } else {
            "MAXIMUM REBIRTH TIER"
        }
        drawRect(rebirthAccent.copy(alpha = 0.1f), Offset(size.width * 0.5f - d(120f), rebirthTop), Size(d(240f), d(40f)))
        drawRect(rebirthAccent, Offset(size.width * 0.5f - d(120f), rebirthTop), Size(d(240f), d(40f)), style = Stroke(d(1.4f)))
        drawLabel(textMeasurer, rebirthLabel, size.width * 0.5f, rebirthTop + d(12f), 9f, rebirthAccent, centered = true, weight = FontWeight.Bold)
    }
    val menuHintY = buttonY + d(if (victory) 104f else 65f)
    drawLabel(textMeasurer, "TAP BELOW FOR CORE SELECT // BANK ${formatCompact(engine.totalMatter)}", size.width * 0.5f, menuHintY, 8f, Muted, centered = true)
}

internal fun DrawScope.drawSettings(engine: GameProjection, textMeasurer: TextMeasurer) {
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
    val page = engine.settingsPage.coerceIn(0, maxPage)
    val pageStart = page * rowsPerPage
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
        val value = settingValue(engine, row)
        drawRect(Color(0x66101225), Offset(bounds.left + d(20f), top), Size(bounds.width - d(40f), rowHeight))
        drawRect(DarkLine, Offset(bounds.left + d(20f), top), Size(bounds.width - d(40f), rowHeight), style = Stroke(d(1f)))
        drawLabel(textMeasurer, SettingsLabels[row.ordinal], bounds.left + d(35f), labelY, 9f, White, weight = FontWeight.Bold)
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
        drawPagedFooter(textMeasurer, bounds, page, maxPage, Violet)
    } else {
        drawFooterBack(textMeasurer, bounds, Violet)
    }
}

internal fun DrawScope.settingValue(engine: GameProjection, row: SettingsRow): String = when (row) {
    SettingsRow.SFX -> if (engine.settings.soundEnabled) "ON" else "OFF"
    SettingsRow.MUSIC -> if (engine.settings.musicEnabled) "ON" else "OFF"
    SettingsRow.MASTER_VOLUME -> "${(engine.settings.masterVolume * 100f).roundToInt()}%"
    SettingsRow.SIMULATION_SPEED -> formatMultiplier(engine.settings.simulationSpeed)
    SettingsRow.TEXT_SIZE -> "${(engine.settings.textScale * 100f).roundToInt()}%"
    SettingsRow.SCREEN_SHAKE -> if (engine.settings.screenShake) "ON" else "OFF"
    SettingsRow.PARTICLES -> engine.settings.particleDensity.name
    SettingsRow.DAMAGE_NUMBERS -> if (engine.settings.damageNumbers) "ON" else "OFF"
    SettingsRow.DAMAGE_NUMBER_SIZE -> engine.settings.damageNumberSize.name
    SettingsRow.DAMAGE_NUMBER_FORMAT -> engine.settings.damageNumberFormat.name
    SettingsRow.DAMAGE_COLOR_THRESHOLDS -> {
        val first = engine.settings.damageNumberTierThreshold.toLong()
        val second = first * DAMAGE_NUMBER_POWERFUL_MULTIPLIER
        val third = first * DAMAGE_NUMBER_DEVASTATING_MULTIPLIER
        "${abbreviateNumber(first)}/${abbreviateNumber(second)}/${abbreviateNumber(third)}"
    }
}
