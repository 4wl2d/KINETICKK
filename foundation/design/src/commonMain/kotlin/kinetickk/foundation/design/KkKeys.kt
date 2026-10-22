// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.input.key.Key
import kinetickk.foundation.common.input.BindableKey
import kinetickk.foundation.common.localization.AppLanguage

/** The bindable key a Compose key event reports, or null for keys that are never bindable. Allocation-free. */
fun Key.toBindableKey(): BindableKey? = when (this) {
    Key.A -> BindableKey.A
    Key.B -> BindableKey.B
    Key.C -> BindableKey.C
    Key.D -> BindableKey.D
    Key.E -> BindableKey.E
    Key.F -> BindableKey.F
    Key.G -> BindableKey.G
    Key.H -> BindableKey.H
    Key.I -> BindableKey.I
    Key.J -> BindableKey.J
    Key.K -> BindableKey.K
    Key.L -> BindableKey.L
    Key.M -> BindableKey.M
    Key.N -> BindableKey.N
    Key.O -> BindableKey.O
    Key.P -> BindableKey.P
    Key.Q -> BindableKey.Q
    Key.R -> BindableKey.R
    Key.S -> BindableKey.S
    Key.T -> BindableKey.T
    Key.U -> BindableKey.U
    Key.V -> BindableKey.V
    Key.W -> BindableKey.W
    Key.X -> BindableKey.X
    Key.Y -> BindableKey.Y
    Key.Z -> BindableKey.Z
    Key.Zero -> BindableKey.DIGIT_0
    Key.One -> BindableKey.DIGIT_1
    Key.Two -> BindableKey.DIGIT_2
    Key.Three -> BindableKey.DIGIT_3
    Key.Four -> BindableKey.DIGIT_4
    Key.Five -> BindableKey.DIGIT_5
    Key.Six -> BindableKey.DIGIT_6
    Key.Seven -> BindableKey.DIGIT_7
    Key.Eight -> BindableKey.DIGIT_8
    Key.Nine -> BindableKey.DIGIT_9
    Key.F1 -> BindableKey.F1
    Key.F2 -> BindableKey.F2
    Key.F3 -> BindableKey.F3
    Key.F4 -> BindableKey.F4
    Key.F5 -> BindableKey.F5
    Key.F6 -> BindableKey.F6
    Key.F7 -> BindableKey.F7
    Key.F8 -> BindableKey.F8
    Key.F9 -> BindableKey.F9
    Key.F10 -> BindableKey.F10
    Key.F11 -> BindableKey.F11
    Key.F12 -> BindableKey.F12
    Key.Spacebar -> BindableKey.SPACE
    Key.ShiftLeft, Key.ShiftRight -> BindableKey.SHIFT
    Key.CtrlLeft, Key.CtrlRight -> BindableKey.CTRL
    Key.AltLeft, Key.AltRight -> BindableKey.ALT
    Key.DirectionUp -> BindableKey.ARROW_UP
    Key.DirectionDown -> BindableKey.ARROW_DOWN
    Key.DirectionLeft -> BindableKey.ARROW_LEFT
    Key.DirectionRight -> BindableKey.ARROW_RIGHT
    Key.Minus -> BindableKey.MINUS
    Key.Equals -> BindableKey.EQUALS
    Key.LeftBracket -> BindableKey.BRACKET_LEFT
    Key.RightBracket -> BindableKey.BRACKET_RIGHT
    Key.Semicolon -> BindableKey.SEMICOLON
    Key.Apostrophe -> BindableKey.APOSTROPHE
    Key.Comma -> BindableKey.COMMA
    Key.Period -> BindableKey.PERIOD
    Key.Slash -> BindableKey.SLASH
    Key.Backslash -> BindableKey.BACKSLASH
    Key.Grave -> BindableKey.GRAVE
    Key.Insert -> BindableKey.INSERT
    Key.Delete -> BindableKey.DELETE
    Key.MoveHome -> BindableKey.HOME
    Key.MoveEnd -> BindableKey.END
    Key.PageUp -> BindableKey.PAGE_UP
    Key.PageDown -> BindableKey.PAGE_DOWN
    Key.NumPad0 -> BindableKey.NUMPAD_0
    Key.NumPad1 -> BindableKey.NUMPAD_1
    Key.NumPad2 -> BindableKey.NUMPAD_2
    Key.NumPad3 -> BindableKey.NUMPAD_3
    Key.NumPad4 -> BindableKey.NUMPAD_4
    Key.NumPad5 -> BindableKey.NUMPAD_5
    Key.NumPad6 -> BindableKey.NUMPAD_6
    Key.NumPad7 -> BindableKey.NUMPAD_7
    Key.NumPad8 -> BindableKey.NUMPAD_8
    Key.NumPad9 -> BindableKey.NUMPAD_9
    else -> null
}

/** The key-cap text of [this] in [language]; one shared string per key and language. */
fun BindableKey.label(language: AppLanguage): String = when (language) {
    AppLanguage.English -> EnglishKeyLabels[ordinal]
    AppLanguage.Russian -> RussianKeyLabels[ordinal]
}

private val EnglishKeyLabels: Array<String> = Array(BindableKey.entries.size) { index ->
    keyCapLabel(BindableKey.entries[index], AppLanguage.English)
}

private val RussianKeyLabels: Array<String> = Array(BindableKey.entries.size) { index ->
    keyCapLabel(BindableKey.entries[index], AppLanguage.Russian)
}

private fun keyCapLabel(key: BindableKey, language: AppLanguage): String {
    val russian = language == AppLanguage.Russian
    return when (key) {
        BindableKey.SPACE -> if (russian) "Пробел" else "Space"
        BindableKey.SHIFT -> "Shift"
        BindableKey.CTRL -> "Ctrl"
        BindableKey.ALT -> "Alt"
        BindableKey.ARROW_UP -> if (russian) "Вверх" else "Up"
        BindableKey.ARROW_DOWN -> if (russian) "Вниз" else "Down"
        BindableKey.ARROW_LEFT -> if (russian) "Влево" else "Left"
        BindableKey.ARROW_RIGHT -> if (russian) "Вправо" else "Right"
        BindableKey.MINUS -> "-"
        BindableKey.EQUALS -> "="
        BindableKey.BRACKET_LEFT -> "["
        BindableKey.BRACKET_RIGHT -> "]"
        BindableKey.SEMICOLON -> ";"
        BindableKey.APOSTROPHE -> "'"
        BindableKey.COMMA -> ","
        BindableKey.PERIOD -> "."
        BindableKey.SLASH -> "/"
        BindableKey.BACKSLASH -> "\\"
        BindableKey.GRAVE -> "`"
        BindableKey.INSERT -> "Ins"
        BindableKey.DELETE -> "Del"
        BindableKey.HOME -> "Home"
        BindableKey.END -> "End"
        BindableKey.PAGE_UP -> "PgUp"
        BindableKey.PAGE_DOWN -> "PgDn"
        else -> when {
            key.name.startsWith(DIGIT_PREFIX) -> key.name.removePrefix(DIGIT_PREFIX)
            key.name.startsWith(NUMPAD_PREFIX) -> "Num ${key.name.removePrefix(NUMPAD_PREFIX)}"
            else -> key.name
        }
    }
}

private const val DIGIT_PREFIX = "DIGIT_"
private const val NUMPAD_PREFIX = "NUMPAD_"
