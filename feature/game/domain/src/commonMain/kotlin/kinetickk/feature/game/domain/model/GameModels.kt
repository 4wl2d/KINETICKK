// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.domain.model

import kotlin.math.floor


enum class GamePhase { MENU, RUNNING, PAUSED, CHOICE, GAME_OVER, VICTORY }
enum class UiScreen { GAME, SETTINGS, LAB, ARMORY, REBIRTH, CODEX }
enum class SettingsRow {
    SFX,
    MUSIC,
    MASTER_VOLUME,
    SIMULATION_SPEED,
    TEXT_SIZE,
    SCREEN_SHAKE,
    PARTICLES,
    DAMAGE_NUMBERS,
    DAMAGE_NUMBER_SIZE,
    DAMAGE_NUMBER_FORMAT,
    DAMAGE_COLOR_THRESHOLDS,
}

internal const val SETTINGS_MIN_ROW_SPACING_DP = 32f

fun settingsRowsPerPage(availableHeight: Float, density: Float): Int {
    val logicalHeight = availableHeight.coerceAtLeast(0f) / density.coerceAtLeast(1f)
    return floor(logicalHeight / SETTINGS_MIN_ROW_SPACING_DP)
        .toInt()
        .coerceIn(1, SettingsRow.entries.size)
}
enum class EnemyType {
    DRIFTER,
    SHOOTER,
    CHARGER,
    INTERCEPTOR,
    WEAVER,
    WARDEN,
    SPLITTER,
    ELITE,
    ARCHITECT,
}
enum class PickupType { DATA, KEY, REPAIR, RELIC }
enum class CoreShape { ORB, PRISM, SHARD }
enum class ChoiceType { ITEM, TOTEM, WEAPON, RELIC, RELIC_BIND }
enum class TotemAction { AMPLIFY_CURRENT, CHANGE_WEAPON }
enum class RelicChoiceAction { ACQUIRE, MELD, REPLACE, MELD_TARGET }
enum class WeaponNodeType { GRAVITY_MINE }

data class ChoiceOption(
    val type: ChoiceType,
    val title: String,
    val description: String,
    val tag: String,
    val itemId: Int? = null,
    val weaponId: WeaponId? = null,
    val totemAction: TotemAction? = null,
    val relicId: RelicId? = null,
    val relicAction: RelicChoiceAction? = null,
    val relicSlot: Int? = null,
)
