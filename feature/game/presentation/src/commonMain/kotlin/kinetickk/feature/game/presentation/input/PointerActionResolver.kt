// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.presentation.input

import kinetickk.feature.game.domain.model.CoreShape
import kinetickk.feature.game.domain.model.GamePhase
import kinetickk.feature.game.domain.model.MetaUpgradeId
import kinetickk.feature.game.domain.model.SettingsRow
import kinetickk.feature.game.domain.model.settingsRowsPerPage
import kinetickk.feature.game.domain.model.UiScreen
import kinetickk.feature.game.domain.projection.GameProjection
import kinetickk.feature.game.domain.protocol.BrakeSource
import kinetickk.feature.game.domain.protocol.GameIntent
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Maps presentation coordinates to semantic domain actions. */
fun GameProjection.resolvePointerPress(x: Float, y: Float): GameIntent? =
    if (screen == UiScreen.GAME) {
        when (phase) {
            GamePhase.MENU -> resolveMenuPress(x, y)
            GamePhase.GAME_OVER, GamePhase.VICTORY -> resolveEndPress(x, y)
            GamePhase.CHOICE -> resolveChoicePress(x, y)
            GamePhase.PAUSED -> resolvePausePress(x, y)
            GamePhase.RUNNING -> resolveHudPress(x, y)
        }
    } else {
        resolveOverlayPress(x, y)
    }

fun GameProjection.isHudControlPosition(x: Float, y: Float): Boolean =
    screen == UiScreen.GAME && phase == GamePhase.RUNNING &&
        (isDashButton(x, y) || isBrakeButton(x, y))

private fun GameProjection.resolveMenuPress(x: Float, y: Float): GameIntent? {
    val cardY = screenHeight * 0.62f
    if (y in cardY - d(55f)..cardY + d(55f)) {
        val center = screenWidth * 0.5f
        when {
            x in center - d(190f)..center - d(70f) -> return GameIntent.CoreShapeSelected(CoreShape.ORB)
            x in center - d(60f)..center + d(60f) -> return GameIntent.CoreShapeSelected(CoreShape.PRISM)
            x in center + d(70f)..center + d(190f) -> return GameIntent.CoreShapeSelected(CoreShape.SHARD)
        }
    }

    val buttonY = screenHeight * 0.78f
    if (
        x in screenWidth * 0.5f - d(150f)..screenWidth * 0.5f + d(150f) &&
        y in buttonY - d(31f)..buttonY + d(31f)
    ) {
        return GameIntent.RunStartRequested
    }

    val secondaryY = screenHeight * 0.9f
    if (y !in secondaryY - d(20f)..secondaryY + d(20f)) return null
    val spacing = min(d(132f), screenWidth * 0.19f)
    val start = screenWidth * 0.5f - spacing * 2f
    val index = ((x - start) / spacing).roundToInt()
    val itemCenter = start + index * spacing
    if (index !in 0..4 || x !in itemCenter - spacing * 0.44f..itemCenter + spacing * 0.44f) return null
    return GameIntent.ScreenOpenRequested(
        when (index) {
            0 -> UiScreen.LAB
            1 -> UiScreen.ARMORY
            2 -> UiScreen.REBIRTH
            3 -> UiScreen.CODEX
            else -> UiScreen.SETTINGS
        },
    )
}

private fun GameProjection.resolvePausePress(x: Float, y: Float): GameIntent? {
    val center = screenWidth * 0.5f
    if (x !in center - d(150f)..center + d(150f)) return null
    return when {
        y in screenHeight * 0.5f..screenHeight * 0.5f + d(52f) -> GameIntent.PauseToggled
        y in screenHeight * 0.62f..screenHeight * 0.62f + d(52f) ->
            GameIntent.ScreenOpenRequested(UiScreen.SETTINGS)
        y in screenHeight * 0.74f..screenHeight * 0.74f + d(52f) -> GameIntent.ReturnToMenuRequested
        else -> null
    }
}

private fun GameProjection.resolveEndPress(x: Float, y: Float): GameIntent? {
    val centerX = screenWidth * 0.5f
    val buttonY = screenHeight * 0.72f
    if (x in centerX - d(155f)..centerX + d(155f) && y in buttonY - d(38f)..buttonY + d(38f)) {
        return GameIntent.RunStartRequested
    }
    if (
        phase == GamePhase.VICTORY &&
        x in centerX - d(120f)..centerX + d(120f) &&
        y in buttonY + d(50f)..buttonY + d(90f)
    ) {
        return GameIntent.ScreenOpenRequested(UiScreen.REBIRTH)
    }
    return if (y > buttonY + d(if (phase == GamePhase.VICTORY) 100f else 50f)) {
        GameIntent.ReturnToMenuRequested
    } else {
        null
    }
}

private fun GameProjection.resolveChoicePress(x: Float, y: Float): GameIntent? {
    val choiceCount = choices.size.coerceAtLeast(1)
    val gap = d(if (choiceCount >= 4) 10f else 18f)
    val maxCardWidth = d(
        when {
            choiceCount >= 4 -> 190f
            choiceCount == 3 -> 250f
            else -> 300f
        },
    )
    val availableCardWidth = (screenWidth - d(30f) - gap * (choiceCount - 1)) / choiceCount
    val cardWidth = min(maxCardWidth, availableCardWidth).coerceAtLeast(d(92f))
    val total = cardWidth * choiceCount + gap * (choiceCount - 1)
    val startX = (screenWidth - total) * 0.5f
    val top = screenHeight * if (choiceCount >= 4) 0.29f else 0.31f
    val bottomReserve = d(if (choicesCanReroll) 105f else 35f)
    val cardHeight = min(d(270f), screenHeight - bottomReserve - top).coerceAtLeast(d(170f))
    if (y in top..top + cardHeight) {
        repeat(choiceCount) { index ->
            val left = startX + index * (cardWidth + gap)
            if (x in left..left + cardWidth) return GameIntent.ChoiceSelected(index)
        }
    }
    val rerollY = screenHeight - d(72f)
    return if (
        x in screenWidth * 0.5f - d(90f)..screenWidth * 0.5f + d(90f) &&
        y in rerollY - d(22f)..rerollY + d(22f)
    ) {
        GameIntent.ChoicesRerolled
    } else {
        null
    }
}

private fun GameProjection.resolveHudPress(x: Float, y: Float): GameIntent? = when {
    isDashButton(x, y) -> GameIntent.DashRequested
    isBrakeButton(x, y) -> GameIntent.BrakeChanged(BrakeSource.TOUCH_CONTROL, true)
    else -> null
}

private fun GameProjection.resolveOverlayPress(x: Float, y: Float): GameIntent? = when (screen) {
    UiScreen.SETTINGS -> resolveSettingsPress(x, y)
    UiScreen.LAB -> resolveLabPress(x, y)
    UiScreen.ARMORY -> resolveArmoryPress(x, y)
    UiScreen.REBIRTH -> resolveRebirthPress(x, y)
    UiScreen.CODEX -> resolveCodexPress(x, y)
    UiScreen.GAME -> null
}

private fun GameProjection.resolveSettingsPress(x: Float, y: Float): GameIntent? {
    val bounds = overlayBounds(640f, 620f)
    val left = bounds.left
    val right = bounds.right
    val bottom = bounds.bottom
    val startY = bounds.top + d(72f)
    val availableHeight = bottom - d(64f) - startY
    val rowsPerPage = settingsRowsPerPage(availableHeight, uiScale)
    val maxPage = SettingsRow.entries.lastIndex / rowsPerPage
    val currentPage = settingsPage.coerceIn(0, maxPage)
    if (y > bottom - d(55f)) {
        if (x !in left..right) return null
        if (maxPage == 0 || x < left + (right - left) * 0.45f) return closeOverlayIntent()
        return if (x < right - d(85f)) {
            GameIntent.SettingsPageSelected(max(0, currentPage - 1))
        } else {
            GameIntent.SettingsPageSelected(min(maxPage, currentPage + 1))
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
    return GameIntent.SettingAdjusted(SettingsRow.entries[rowIndex], direction)
}

private fun GameProjection.resolveLabPress(x: Float, y: Float): GameIntent? {
    val bounds = overlayBounds()
    if (y > bounds.bottom - d(55f)) return closeOverlayIntent()
    val contentTop = bounds.top + d(88f)
    val contentWidth = bounds.right - bounds.left - d(50f)
    val columnWidth = contentWidth * 0.5f
    val rowHeight = d(105f)
    if (
        x !in bounds.left + d(25f)..bounds.right - d(25f) ||
        y !in contentTop..contentTop + rowHeight * 4f
    ) {
        return null
    }
    val column = if (x < bounds.left + d(25f) + columnWidth) 0 else 1
    val row = floor((y - contentTop) / rowHeight).toInt()
    val upgrade = MetaUpgradeId.entries.getOrNull(row * 2 + column) ?: return null
    return GameIntent.MetaUpgradePurchaseRequested(upgrade)
}

private fun GameProjection.resolveArmoryPress(x: Float, y: Float): GameIntent? {
    val bounds = overlayBounds()
    if (y > bounds.bottom - d(55f)) {
        return when {
            x < bounds.left + (bounds.right - bounds.left) * 0.45f -> closeOverlayIntent()
            x < bounds.right - d(85f) -> GameIntent.ArmoryPageSelected(max(0, armoryPage - 1))
            else -> GameIntent.ArmoryPageSelected(min(maxArmoryPage, armoryPage + 1))
        }
    }
    val cardWidth = min(d(245f), (bounds.right - bounds.left - d(80f)) / 3f)
    val gap = d(16f)
    val startX = (screenWidth - (cardWidth * 3f + gap * 2f)) * 0.5f
    if (y !in bounds.top + d(118f)..bounds.bottom - d(85f)) return null
    repeat(3) { index ->
        val cardLeft = startX + index * (cardWidth + gap)
        if (x in cardLeft..cardLeft + cardWidth) {
            val weapon = armoryPageWeapons.getOrNull(index) ?: return null
            return GameIntent.WeaponPurchaseOrEquipRequested(weapon.id)
        }
    }
    return null
}

private fun GameProjection.resolveRebirthPress(x: Float, y: Float): GameIntent? {
    val bounds = overlayBounds()
    return when {
        x in bounds.left + d(24f)..bounds.right - d(24f) &&
            y in bounds.bottom - d(118f)..bounds.bottom - d(68f) -> GameIntent.RebirthRequested
        x in bounds.left + d(20f)..bounds.right - d(20f) &&
            y in bounds.bottom - d(55f)..bounds.bottom - d(14f) -> closeOverlayIntent()
        else -> null
    }
}

private fun GameProjection.resolveCodexPress(x: Float, y: Float): GameIntent? {
    val bounds = overlayBounds()
    if (y <= bounds.bottom - d(55f)) return null
    return when {
        x < bounds.left + (bounds.right - bounds.left) * 0.45f -> closeOverlayIntent()
        x < bounds.right - d(85f) -> GameIntent.CodexPageSelected(max(0, codexPage - 1))
        else -> GameIntent.CodexPageSelected(min(maxCodexPage, codexPage + 1))
    }
}

private fun GameProjection.isDashButton(x: Float, y: Float): Boolean =
    distanceSquared(x, y, screenWidth - d(82f), screenHeight - d(88f)) < square(d(48f))

private fun GameProjection.isBrakeButton(x: Float, y: Float): Boolean =
    distanceSquared(x, y, screenWidth - d(190f), screenHeight - d(67f)) < square(d(38f))

private fun GameProjection.overlayBounds(maxWidth: Float = 900f, maxHeight: Float = 650f): Bounds {
    val width = min(d(maxWidth), screenWidth - d(30f))
    val height = min(d(maxHeight), screenHeight - d(30f))
    val left = (screenWidth - width) * 0.5f
    val top = (screenHeight - height) * 0.5f
    return Bounds(left, top, left + width, top + height)
}

private fun closeOverlayIntent(): GameIntent = GameIntent.ScreenOpenRequested(UiScreen.GAME)
private fun GameProjection.d(value: Float): Float = value * uiScale
private fun square(value: Float): Float = value * value
private fun distanceSquared(ax: Float, ay: Float, bx: Float, by: Float): Float =
    square(ax - bx) + square(ay - by)

private data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float)
