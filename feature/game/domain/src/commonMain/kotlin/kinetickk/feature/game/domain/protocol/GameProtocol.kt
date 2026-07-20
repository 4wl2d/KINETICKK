// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.domain.protocol

import kinetickk.core.collections.ImmutableList
import kinetickk.feature.game.domain.model.CoreShape
import kinetickk.feature.game.domain.model.GameSettings
import kinetickk.feature.game.domain.model.MetaUpgradeId
import kinetickk.feature.game.domain.model.SettingsRow
import kinetickk.feature.game.domain.model.StoredProgress
import kinetickk.feature.game.domain.model.UiScreen
import kinetickk.feature.game.domain.model.WeaponId

/** User and lifecycle events accepted by the synchronous game reducer. */
sealed interface GameIntent {
    data class FrameElapsed(val realDeltaSeconds: Float) : GameIntent
    data class ViewportChanged(val width: Float, val height: Float, val density: Float) : GameIntent
    data class PointerMoved(val x: Float, val y: Float, val active: Boolean = true) : GameIntent
    data class BrakeChanged(val source: BrakeSource, val active: Boolean) : GameIntent
    data object DashRequested : GameIntent
    data object PauseToggled : GameIntent
    data object EscapeRequested : GameIntent
    data class ScreenOpenRequested(val screen: UiScreen) : GameIntent
    data object MuteToggled : GameIntent
    data class ChoiceSelected(val index: Int) : GameIntent
    data object ChoicesRerolled : GameIntent
    data object EnterPressed : GameIntent
    data object RunStartRequested : GameIntent
    data object ReturnToMenuRequested : GameIntent
    data object RebirthRequested : GameIntent
    data class CoreShapeSelected(val shape: CoreShape) : GameIntent
    data class MetaUpgradePurchaseRequested(val upgrade: MetaUpgradeId) : GameIntent
    data class WeaponPurchaseOrEquipRequested(val weapon: WeaponId) : GameIntent
    data class SettingAdjusted(val setting: SettingsRow, val direction: Int) : GameIntent
    data class SettingsPageSelected(val page: Int) : GameIntent
    data class ArmoryPageSelected(val page: Int) : GameIntent
    data class CodexPageSelected(val page: Int) : GameIntent
    data object UserGestureObserved : GameIntent
}

enum class BrakeSource { KEYBOARD, SECONDARY_POINTER, TOUCH_CONTROL }

enum class SoundCue {
    UI_CLICK,
    DASH,
    WEAPON_LIGHT,
    WEAPON_HEAVY,
    IMPACT,
    ENEMY_DESTROYED,
    PICKUP,
    LEVEL_UP,
    OVERHEAT,
    RECOVERED,
    HURT,
    OVERDRIVE,
    WEAPON_ACQUIRED,
    PURCHASE,
    GAME_OVER,
    VICTORY,
}

/** Work requested by the domain and executed by the feature composition root after commit. */
sealed interface GameEffect {
    data class AdvanceAudio(
        val settings: GameSettings,
        val realDeltaSeconds: Float,
        val cues: ImmutableList<SoundCue>,
    ) : GameEffect

    data object EnsureAudioUnlocked : GameEffect

    data class PersistProgress(
        val snapshot: StoredProgress,
    ) : GameEffect

    data class EmitVisualFx(
        val cues: ImmutableList<VisualFxCue>,
    ) : GameEffect
}

sealed interface GameRejection {
    data class InvalidInput(val field: String, val reason: String) : GameRejection
    data object RevisionExhausted : GameRejection
}
