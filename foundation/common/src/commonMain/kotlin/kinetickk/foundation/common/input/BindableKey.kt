// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.common.input

/**
 * Physical keys a player may bind to an action, independent of any UI toolkit. The wire id is the
 * name; entries are append-only. Left and right modifiers share one key. Escape, Enter, Tab,
 * Backspace, Caps Lock, Meta and unknown keys are deliberately absent: they are never bindable.
 */
enum class BindableKey {
    A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z,
    DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4, DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9,
    F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,
    SPACE, SHIFT, CTRL, ALT,
    ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT,
    MINUS, EQUALS, BRACKET_LEFT, BRACKET_RIGHT, SEMICOLON, APOSTROPHE, COMMA, PERIOD, SLASH, BACKSLASH, GRAVE,
    INSERT, DELETE, HOME, END, PAGE_UP, PAGE_DOWN,
    NUMPAD_0, NUMPAD_1, NUMPAD_2, NUMPAD_3, NUMPAD_4, NUMPAD_5, NUMPAD_6, NUMPAD_7, NUMPAD_8, NUMPAD_9,
    ;

    /** Stable persisted identity. */
    val wireId: String get() = name

    companion object {
        /** The key with [wireId], or null for an unknown id. */
        fun fromWireId(wireId: String): BindableKey? = entries.firstOrNull { it.name == wireId }
    }
}
