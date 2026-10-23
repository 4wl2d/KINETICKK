// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.input.key.Key
import kinetickk.foundation.common.input.BindableKey
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KkKeysTest {
    private val mappedKeys = listOf(
        Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M,
        Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z,
        Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine,
        Key.F1, Key.F2, Key.F3, Key.F4, Key.F5, Key.F6, Key.F7, Key.F8, Key.F9, Key.F10, Key.F11, Key.F12,
        Key.Spacebar, Key.ShiftLeft, Key.CtrlLeft, Key.AltLeft,
        Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
        Key.Minus, Key.Equals, Key.LeftBracket, Key.RightBracket, Key.Semicolon, Key.Apostrophe, Key.Comma,
        Key.Period, Key.Slash, Key.Backslash, Key.Grave,
        Key.Insert, Key.Delete, Key.MoveHome, Key.MoveEnd, Key.PageUp, Key.PageDown,
        Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4, Key.NumPad5, Key.NumPad6, Key.NumPad7,
        Key.NumPad8, Key.NumPad9,
    )

    @Test
    fun everyBindableKeyIsReachedFromExactlyOneKeyInEnumOrder() {
        assertEquals(BindableKey.entries.toList(), mappedKeys.map { it.toBindableKey() })
        assertEquals(BindableKey.SHIFT, Key.ShiftRight.toBindableKey())
        assertEquals(BindableKey.CTRL, Key.CtrlRight.toBindableKey())
        assertEquals(BindableKey.ALT, Key.AltRight.toBindableKey())
    }

    @Test
    fun reservedAndUnknownKeysAreNeverBindable() {
        listOf(
            Key.Escape, Key.Enter, Key.NumPadEnter, Key.Tab, Key.Backspace, Key.CapsLock,
            Key.MetaLeft, Key.MetaRight, Key.Unknown,
        ).forEach { key -> assertNull(key.toBindableKey(), "$key") }
    }

    @Test
    fun everyKeyHasAStableCapLabelInBothLanguages() {
        BindableKey.entries.forEach { key ->
            AppLanguage.entries.forEach { language ->
                val label = key.label(language)
                assertTrue(label.isNotBlank() && label.length <= 6, "$key $language: '$label'")
                assertSame(label, key.label(language))
            }
        }
        assertEquals("Space", BindableKey.SPACE.label(AppLanguage.English))
        assertEquals("Пробел", BindableKey.SPACE.label(AppLanguage.Russian))
        assertEquals("Вверх", BindableKey.ARROW_UP.label(AppLanguage.Russian))
        assertEquals("Z", BindableKey.Z.label(AppLanguage.Russian))
        assertEquals("4", BindableKey.DIGIT_4.label(AppLanguage.English))
        assertEquals("Num 7", BindableKey.NUMPAD_7.label(AppLanguage.English))
        assertEquals("F3", BindableKey.F3.label(AppLanguage.English))
        assertEquals("PgDn", BindableKey.PAGE_DOWN.label(AppLanguage.Russian))
        assertEquals(
            BindableKey.entries.size,
            BindableKey.entries.map { it.label(AppLanguage.English) }.toSet().size,
        )
    }
}
