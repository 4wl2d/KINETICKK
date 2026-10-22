// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.common.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BindableKeyTest {
    @Test
    fun theVocabularyHoldsExactlyTheEightyThreeBindableKeys() {
        assertEquals(83, BindableKey.entries.size)
        assertEquals(26, BindableKey.entries.count { it.name.length == 1 })
        assertEquals(10, BindableKey.entries.count { it.name.startsWith("DIGIT_") })
        assertEquals(10, BindableKey.entries.count { it.name.startsWith("NUMPAD_") })
        assertEquals(12, BindableKey.entries.count { it.name.matches(Regex("F[0-9]+")) })
        listOf("ESCAPE", "ENTER", "TAB", "BACKSPACE", "CAPS_LOCK", "META").forEach { never ->
            assertNull(BindableKey.fromWireId(never), never)
        }
    }

    @Test
    fun wireIdsRoundTripAndUnknownIdsAreRejected() {
        BindableKey.entries.forEach { key -> assertEquals(key, BindableKey.fromWireId(key.wireId)) }
        assertNull(BindableKey.fromWireId("a"))
        assertNull(BindableKey.fromWireId(""))
    }
}
