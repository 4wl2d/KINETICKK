// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.domain.reducer

import kinetickk.core.collections.ImmutableList
import kinetickk.core.collections.immutableListOf
import kinetickk.core.collections.toImmutableList
import kinetickk.feature.game.domain.model.SettingsRow
import kinetickk.feature.game.domain.model.StoredProgress
import kinetickk.feature.game.domain.model.UiScreen
import kinetickk.feature.game.domain.protocol.BrakeSource
import kinetickk.feature.game.domain.protocol.GameEffect
import kinetickk.feature.game.domain.protocol.GameIntent
import kinetickk.feature.game.domain.protocol.GameRejection
import kinetickk.feature.game.domain.simulation.*

internal data class EngineState(
    val model: MutableGameState,
)

internal fun initialEngineState(
    seed: Int,
    bootstrapProgress: StoredProgress?,
    initialMatter: Int? = null,
    initialRebirthLevel: Int = 0,
): EngineState = EngineState(
    model = MutableGameState(
        seed = seed,
        initialMatter = initialMatter,
        initialRebirthLevel = initialRebirthLevel,
        bootstrapProgress = bootstrapProgress,
    ),
)

internal data class GameReduction(
    val state: EngineState,
    val effects: ImmutableList<GameEffect>,
)

internal sealed interface GameReductionResult {
    data class Accepted(val value: GameReduction) : GameReductionResult
    data class Rejected(val reason: GameRejection) : GameReductionResult
}

/** Purely coordinates a synchronous state transition; effect execution belongs to feature-impl. */
internal class GameReducer {
    fun reduce(state: EngineState, intent: GameIntent): GameReductionResult {
        validate(state, intent)?.let { return GameReductionResult.Rejected(it) }

        if (intent == GameIntent.UserGestureObserved) {
            return GameReductionResult.Accepted(
                GameReduction(
                    state = state,
                    effects = immutableListOf(GameEffect.EnsureAudioUnlocked),
                ),
            )
        }

        val candidate = state.model.copyForReduction()
        applyIntent(candidate, intent)
        val effects = buildList<GameEffect> {
            candidate.takeVisualFxCues()
                .takeIf { it.isNotEmpty() }
                ?.let { add(GameEffect.EmitVisualFx(it.toImmutableList())) }
            candidate.takePersistenceRequest()?.let { snapshot ->
                add(GameEffect.PersistProgress(snapshot))
            }
            val soundCues = candidate.takeSoundCues()
            if (intent is GameIntent.FrameElapsed || soundCues.isNotEmpty()) {
                add(
                    GameEffect.AdvanceAudio(
                        settings = candidate.settings,
                        realDeltaSeconds = (intent as? GameIntent.FrameElapsed)?.realDeltaSeconds ?: 0f,
                        cues = soundCues.toImmutableList(),
                    ),
                )
            }
        }.toImmutableList()

        return GameReductionResult.Accepted(
            GameReduction(
                state = EngineState(candidate),
                effects = effects,
            ),
        )
    }

    private fun applyIntent(state: MutableGameState, intent: GameIntent) {
        when (intent) {
            is GameIntent.FrameElapsed -> state.update(intent.realDeltaSeconds)
            is GameIntent.ViewportChanged -> state.resize(intent.width, intent.height, intent.density)
            is GameIntent.PointerMoved -> state.updatePointer(intent.x, intent.y, intent.active)
            is GameIntent.BrakeChanged -> when (intent.source) {
                BrakeSource.KEYBOARD -> state.setBrake(intent.active)
                BrakeSource.SECONDARY_POINTER -> state.setSecondaryBrake(intent.active)
                BrakeSource.TOUCH_CONTROL -> state.setTouchBrake(intent.active)
            }
            GameIntent.DashRequested -> state.requestDash()
            GameIntent.PauseToggled -> state.togglePause()
            GameIntent.EscapeRequested -> state.handleEscape()
            is GameIntent.ScreenOpenRequested -> when (intent.screen) {
                UiScreen.SETTINGS -> state.openSettings()
                UiScreen.LAB -> state.openLab()
                UiScreen.ARMORY -> state.openArmory()
                UiScreen.REBIRTH -> state.openRebirth()
                UiScreen.CODEX -> state.openCodex()
                UiScreen.GAME -> state.closeOverlay()
            }
            GameIntent.MuteToggled -> state.toggleMute()
            is GameIntent.ChoiceSelected -> state.choose(intent.index)
            GameIntent.ChoicesRerolled -> state.rerollChoices()
            GameIntent.EnterPressed -> state.handleEnter()
            GameIntent.RunStartRequested -> state.startRun()
            GameIntent.ReturnToMenuRequested -> state.returnToMenu()
            GameIntent.RebirthRequested -> state.requestRebirth()
            is GameIntent.CoreShapeSelected -> state.setCoreShape(intent.shape)
            is GameIntent.MetaUpgradePurchaseRequested -> state.buyMetaUpgrade(intent.upgrade)
            is GameIntent.WeaponPurchaseOrEquipRequested -> state.buyOrEquipWeapon(intent.weapon)
            is GameIntent.SettingAdjusted -> state.adjustSetting(intent.setting, intent.direction)
            is GameIntent.SettingsPageSelected -> state.selectSettingsPage(intent.page)
            is GameIntent.ArmoryPageSelected -> state.selectArmoryPage(intent.page)
            is GameIntent.CodexPageSelected -> state.selectCodexPage(intent.page)
            GameIntent.UserGestureObserved -> error("handled before state cloning")
        }
    }

    private fun validate(state: EngineState, intent: GameIntent): GameRejection.InvalidInput? =
        when (intent) {
            is GameIntent.FrameElapsed -> bounded(
                field = "realDeltaSeconds",
                value = intent.realDeltaSeconds,
                minimum = MIN_FRAME_DELTA_SECONDS,
                maximum = MAX_FRAME_DELTA_SECONDS,
            )
            is GameIntent.ViewportChanged ->
                bounded("width", intent.width, MIN_VIEWPORT_DIMENSION, MAX_VIEWPORT_DIMENSION)
                    ?: bounded("height", intent.height, MIN_VIEWPORT_DIMENSION, MAX_VIEWPORT_DIMENSION)
                    ?: bounded("density", intent.density, MIN_DENSITY, MAX_DENSITY)
            is GameIntent.PointerMoved ->
                bounded("x", intent.x, 0f, state.model.screenWidth)
                    ?: bounded("y", intent.y, 0f, state.model.screenHeight)
            is GameIntent.ChoiceSelected -> if (intent.index in 0..3) {
                null
            } else {
                GameRejection.InvalidInput("index", "must be in 0..3")
            }
            is GameIntent.SettingAdjusted -> if (intent.direction == -1 || intent.direction == 1) {
                null
            } else {
                GameRejection.InvalidInput("direction", "must be -1 or 1")
            }
            is GameIntent.SettingsPageSelected -> validPage(intent.page, SettingsRow.entries.lastIndex)
            is GameIntent.ArmoryPageSelected -> validPage(intent.page, state.model.maxArmoryPage)
            is GameIntent.CodexPageSelected -> validPage(intent.page, state.model.maxCodexPage)
            else -> null
        }

    private fun validPage(page: Int, maximum: Int): GameRejection.InvalidInput? =
        if (page in 0..maximum) null else GameRejection.InvalidInput("page", "must be in 0..$maximum")

    private fun bounded(
        field: String,
        value: Float,
        minimum: Float,
        maximum: Float,
    ): GameRejection.InvalidInput? = when {
        !value.isFinite() -> GameRejection.InvalidInput(field, "must be finite")
        value < minimum || value > maximum ->
            GameRejection.InvalidInput(field, "must be in [$minimum, $maximum]")
        else -> null
    }

    private companion object {
        const val MIN_FRAME_DELTA_SECONDS = 0f
        const val MAX_FRAME_DELTA_SECONDS = 1f
        const val MIN_VIEWPORT_DIMENSION = 1f
        const val MAX_VIEWPORT_DIMENSION = 32_768f
        const val MIN_DENSITY = 0.5f
        const val MAX_DENSITY = 8f
    }
}
