// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.domain.model

import kinetickk.core.content.*

enum class GamePhase { RUNNING, PAUSED, CHOICE, GAME_OVER, VICTORY }
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
