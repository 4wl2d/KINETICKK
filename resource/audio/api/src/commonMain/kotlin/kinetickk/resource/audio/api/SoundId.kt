// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.resource.audio.api

/** Every shipped effect sound. The audio resource owns what each one sounds like and how it ranks. */
enum class SoundId {
    UI_CLICK,
    UI_CONFIRM,
    UI_REFUSED,
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
    GAME_OVER,
    VICTORY,
    SKILL,
    APEX,
    /** A spent structure shatters (a finish, a fought abandon or the Architect's collapse). */
    STRUCTURE_COLLAPSE,
}
