// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.input.BindableKey

/** Desktop window presentation. Persisted by stable wire id (the name); append-only. */
enum class WindowMode { WINDOWED, BORDERLESS, FULLSCREEN }

/** Windowed-mode sizes in dp. Persisted by stable wire id (the name); append-only. */
enum class WindowSizePreset(val widthDp: Int, val heightDp: Int) {
    W1280X720(1280, 720),
    W1280X800(1280, 800),
    W1366X768(1366, 768),
    W1440X900(1440, 900),
    W1600X900(1600, 900),
    W1920X1080(1920, 1080),
    W2560X1440(2560, 1440),
}

/** How much of each elemental reaction is animated. Persisted by stable wire id (the name); append-only. */
enum class ReactionEffectsLevel { FULL, REDUCED, MINIMAL }

/** Frame-rate limits a player may choose; 0 means unlimited. */
val FRAME_RATE_CAP_OPTIONS: ImmutableList<Int> = immutableListOf(0, 30, 60, 90, 120, 144, 165, 240)

/** Interface scale percentages a player may choose. */
val INTERFACE_SCALE_OPTIONS: ImmutableList<Int> = immutableListOf(85, 100, 115, 130)

const val DEFAULT_INTERFACE_SCALE_PERCENT: Int = 100

/** A refresh rate of 0 lets the platform choose. */
const val REFRESH_RATE_AUTO: Int = 0

/** Explicit refresh rates a player may request, in Hz. */
val REFRESH_RATE_RANGE: IntRange = 24..360

/**
 * Gameplay actions a player can rebind. Persisted by stable wire id (the name); append-only, and
 * [KeyBindings] indexes keys by ordinal. Escape (pause) and Enter (confirm) stay fixed aliases.
 */
enum class GameAction {
    DASH,
    BRAKE,
    SKILL_1,
    SKILL_2,
    SKILL_3,
    APEX,
    PAUSE,
    REROLL,
    RESTART,
    CHOICE_1,
    CHOICE_2,
    CHOICE_3,
    CHOICE_4,
    PERFORMANCE_HUD,
}

/**
 * One distinct [BindableKey] per [GameAction], immutable. [with] rebinds an action and swaps the key
 * with its previous holder, so the map always stays complete and conflict-free.
 */
class KeyBindings private constructor(
    /** Keys in [GameAction] order. */
    val keys: ImmutableList<BindableKey>,
) {
    private val actionOrdinalByKey: IntArray = IntArray(BindableKey.entries.size) { NO_ACTION }.also { table ->
        keys.forEachIndexed { action, key -> table[key.ordinal] = action }
    }

    fun key(action: GameAction): BindableKey = keys[action.ordinal]

    /** The action [key] triggers, or null when the key is unbound. Allocation-free. */
    fun action(key: BindableKey): GameAction? =
        actionOrdinalByKey[key.ordinal].let { ordinal -> if (ordinal == NO_ACTION) null else GameAction.entries[ordinal] }

    /** Binds [key] to [action]; the action that held [key] takes [action]'s previous key. */
    fun with(action: GameAction, key: BindableKey): KeyBindings {
        val previous = key(action)
        if (previous == key) return this
        val holder = action(key)
        return KeyBindings(
            keys.mapIndexed { index, current ->
                when (index) {
                    action.ordinal -> key
                    holder?.ordinal -> previous
                    else -> current
                }
            }.toImmutableList(),
        )
    }

    val isDefault: Boolean get() = this == DEFAULT

    override fun equals(other: Any?): Boolean = other is KeyBindings && keys == other.keys

    override fun hashCode(): Int = keys.hashCode()

    override fun toString(): String =
        GameAction.entries.joinToString(prefix = "KeyBindings(", postfix = ")") { "${it.name}=${key(it).name}" }

    companion object {
        private const val NO_ACTION: Int = -1

        /** True when [keys] lists exactly one key per [GameAction] and no key twice. */
        fun isValid(keys: List<BindableKey>): Boolean =
            keys.size == GameAction.entries.size && keys.toSet().size == keys.size

        /** Bindings from keys in [GameAction] order; rejects an incomplete or conflicting map. */
        fun of(keys: List<BindableKey>): KeyBindings {
            require(isValid(keys)) { "Key bindings need one distinct key per gameplay action" }
            return KeyBindings(keys.toImmutableList())
        }

        val DEFAULT: KeyBindings = of(
            GameAction.entries.map { action ->
                when (action) {
                    GameAction.DASH -> BindableKey.SPACE
                    GameAction.BRAKE -> BindableKey.SHIFT
                    GameAction.SKILL_1 -> BindableKey.Z
                    GameAction.SKILL_2 -> BindableKey.X
                    GameAction.SKILL_3 -> BindableKey.C
                    GameAction.APEX -> BindableKey.V
                    GameAction.PAUSE -> BindableKey.P
                    GameAction.REROLL -> BindableKey.Q
                    GameAction.RESTART -> BindableKey.R
                    GameAction.CHOICE_1 -> BindableKey.DIGIT_1
                    GameAction.CHOICE_2 -> BindableKey.DIGIT_2
                    GameAction.CHOICE_3 -> BindableKey.DIGIT_3
                    GameAction.CHOICE_4 -> BindableKey.DIGIT_4
                    GameAction.PERFORMANCE_HUD -> BindableKey.F3
                }
            },
        )
    }
}
