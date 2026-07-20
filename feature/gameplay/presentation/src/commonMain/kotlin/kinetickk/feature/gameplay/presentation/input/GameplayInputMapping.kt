// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.presentation.input

import kinetickk.feature.gameplay.domain.model.GamePhase
import kinetickk.feature.gameplay.domain.protocol.BrakeSource
import kinetickk.feature.gameplay.domain.protocol.GameplayAction
import kinetickk.feature.gameplay.domain.renderModel.GameplayRenderModel
import kotlin.math.min

/** A pointer result is either a live-run action or an output owned by the app shell. */
sealed interface GameplayInput {
    data class Action(val action: GameplayAction) : GameplayInput
    data object OpenSettings : GameplayInput
    data object OpenRebirth : GameplayInput
    data object ExitToHome : GameplayInput
    data object RestartRun : GameplayInput
}

/** Maps gameplay canvas coordinates without encoding any app destination in domain state. */
fun GameplayRenderModel.resolveGameplayPress(x: Float, y: Float): GameplayInput? = when (phase) {
    GamePhase.RUNNING -> resolveHudPress(x, y)
    GamePhase.PAUSED -> resolvePausePress(x, y)
    GamePhase.CHOICE -> resolveChoicePress(x, y)
    GamePhase.GAME_OVER, GamePhase.VICTORY -> resolveEndPress(x, y)
}

fun GameplayRenderModel.isHudControlPosition(x: Float, y: Float): Boolean =
    phase == GamePhase.RUNNING && (isDashButton(x, y) || isBrakeButton(x, y))

private fun GameplayRenderModel.resolvePausePress(x: Float, y: Float): GameplayInput? {
    val center = screenWidth * 0.5f
    if (x !in center - d(150f)..center + d(150f)) return null
    return when {
        y in screenHeight * 0.5f..screenHeight * 0.5f + d(52f) ->
            GameplayInput.Action(GameplayAction.PauseToggled)
        y in screenHeight * 0.62f..screenHeight * 0.62f + d(52f) -> GameplayInput.OpenSettings
        y in screenHeight * 0.74f..screenHeight * 0.74f + d(52f) -> GameplayInput.ExitToHome
        else -> null
    }
}

private fun GameplayRenderModel.resolveEndPress(x: Float, y: Float): GameplayInput? {
    val centerX = screenWidth * 0.5f
    val buttonY = screenHeight * 0.72f
    if (x in centerX - d(155f)..centerX + d(155f) && y in buttonY - d(38f)..buttonY + d(38f)) {
        return GameplayInput.RestartRun
    }
    if (
        phase == GamePhase.VICTORY &&
        x in centerX - d(120f)..centerX + d(120f) &&
        y in buttonY + d(50f)..buttonY + d(90f)
    ) {
        return GameplayInput.OpenRebirth
    }
    return if (y > buttonY + d(if (phase == GamePhase.VICTORY) 100f else 50f)) {
        GameplayInput.ExitToHome
    } else {
        null
    }
}

private fun GameplayRenderModel.resolveChoicePress(x: Float, y: Float): GameplayInput? {
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
            if (x in left..left + cardWidth) {
                return GameplayInput.Action(GameplayAction.ChoiceSelected(index))
            }
        }
    }
    val rerollY = screenHeight - d(72f)
    return if (
        x in screenWidth * 0.5f - d(90f)..screenWidth * 0.5f + d(90f) &&
        y in rerollY - d(22f)..rerollY + d(22f)
    ) {
        GameplayInput.Action(GameplayAction.ChoicesRerolled)
    } else {
        null
    }
}

private fun GameplayRenderModel.resolveHudPress(x: Float, y: Float): GameplayInput? = when {
    isDashButton(x, y) -> GameplayInput.Action(GameplayAction.DashRequested)
    isBrakeButton(x, y) -> GameplayInput.Action(
        GameplayAction.BrakeChanged(BrakeSource.TOUCH_CONTROL, active = true),
    )
    else -> null
}

private fun GameplayRenderModel.isDashButton(x: Float, y: Float): Boolean =
    distanceSquared(x, y, screenWidth - d(82f), screenHeight - d(88f)) < square(d(48f))

private fun GameplayRenderModel.isBrakeButton(x: Float, y: Float): Boolean =
    distanceSquared(x, y, screenWidth - d(190f), screenHeight - d(67f)) < square(d(38f))

private fun GameplayRenderModel.d(value: Float): Float = value * uiScale
private fun square(value: Float): Float = value * value
private fun distanceSquared(ax: Float, ay: Float, bx: Float, by: Float): Float =
    square(ax - bx) + square(ay - by)
