// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.lab.impl

import kinetickk.core.content.MetaUpgradeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LabPointerResolverTest {
    @Test
    fun twoColumnGridUsesOriginalCatalogOrdering() {
        assertEquals(
            LabAction.PurchaseRequested(MetaUpgradeId.CORE_INTEGRITY),
            resolveLabPress(1_280f, 720f, 1f, x = 250f, y = 140f),
        )
        assertEquals(
            LabAction.PurchaseRequested(MetaUpgradeId.KINETIC_AMPLIFIER),
            resolveLabPress(1_280f, 720f, 1f, x = 700f, y = 140f),
        )
        assertEquals(
            LabAction.PurchaseRequested(MetaUpgradeId.ARMORY_LICENSE),
            resolveLabPress(1_280f, 720f, 1f, x = 700f, y = 470f),
        )
        assertNull(resolveLabPress(1_280f, 720f, 1f, x = 100f, y = 140f))
    }

    @Test
    fun footerMapsToBackAcrossItsOriginalFullWidthPolicy() {
        assertEquals(
            LabAction.Back,
            resolveLabPress(1_280f, 720f, 1f, x = 50f, y = 690f),
        )
    }
}
