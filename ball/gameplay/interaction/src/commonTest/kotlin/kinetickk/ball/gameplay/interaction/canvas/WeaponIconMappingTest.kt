// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.content.api.WeaponId
import kotlin.test.Test
import kotlin.test.assertEquals

class WeaponIconMappingTest {
    @Test fun everyWeaponRetainsItsDistinctNamedGlyph() {
        val icons = WeaponId.entries.map { weapon ->
            weapon.overlayIcon().also { assertEquals("WEAPONS_${weapon.name}", it.name) }
        }
        assertEquals(WeaponId.entries.size, icons.toSet().size)
    }
}
