// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.content.api.WeaponId
import kotlin.test.Test
import kotlin.test.assertEquals

class WeaponIconMappingTest {
    @Test fun everyWeaponRetainsItsDistinctNamedGlyph() {
        // Distinct weapon names give distinct icons.
        for (weapon in WeaponId.entries) assertEquals("WEAPONS_${weapon.name}", weapon.overlayIcon().name)
    }
}
