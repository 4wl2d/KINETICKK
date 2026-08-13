// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.lab.impl

import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.profile.api.LabProfileSnapshot
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.interaction.TestMetaUpgrades
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LabPointerResolverTest {
    @Test
    fun twoColumnGridUsesInjectedSnapshotOrdering() {
        val model = LabProfileSnapshot(PlayerEconomy(), LabProgress()).toRenderModel(TestMetaUpgrades)
        assertEquals(
            LabAction.PurchaseRequested(MetaUpgradeId.CORE_INTEGRITY),
            resolveLabPress(model, 1_280f, 720f, 1f, x = 250f, y = 140f),
        )
        assertEquals(
            LabAction.PurchaseRequested(MetaUpgradeId.KINETIC_AMPLIFIER),
            resolveLabPress(model, 1_280f, 720f, 1f, x = 700f, y = 140f),
        )
        assertEquals(
            LabAction.PurchaseRequested(MetaUpgradeId.ARMORY_LICENSE),
            resolveLabPress(model, 1_280f, 720f, 1f, x = 700f, y = 470f),
        )
        assertNull(resolveLabPress(model, 1_280f, 720f, 1f, x = 100f, y = 140f))

        val reversed = TestMetaUpgrades.reversed().toImmutableList()
        val reversedModel = LabProfileSnapshot(PlayerEconomy(), LabProgress()).toRenderModel(reversed)
        assertEquals(
            LabAction.PurchaseRequested(MetaUpgradeId.ARMORY_LICENSE),
            resolveLabPress(reversedModel, 1_280f, 720f, 1f, x = 250f, y = 140f),
        )
    }

    @Test
    fun footerMapsToBackAcrossItsOriginalFullWidthPolicy() {
        val model = LabProfileSnapshot(PlayerEconomy(), LabProgress()).toRenderModel(TestMetaUpgrades)
        assertEquals(
            LabAction.Back,
            resolveLabPress(model, 1_280f, 720f, 1f, x = 50f, y = 690f),
        )
    }
}
