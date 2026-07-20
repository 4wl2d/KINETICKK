// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.lab.impl

import kinetickk.core.audio.api.AudioCue
import kinetickk.core.content.MetaUpgradeCatalog
import kinetickk.core.content.MetaUpgradeId
import kinetickk.core.profile.api.LabProfileSnapshot
import kinetickk.core.profile.api.LabProgress
import kinetickk.core.profile.api.PlayerEconomy
import kinetickk.feature.lab.api.LabOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LabReducerTest {
    @Test
    fun snapshotMapsEightOrderedUpgradesAndExactNextCost() {
        val ranks = List(MetaUpgradeId.entries.size) { index -> if (index == 0) 1 else 0 }
        val model = LabProfileSnapshot(
            economy = PlayerEconomy(matter = 100L),
            progress = LabProgress(ranks),
        ).toRenderModel()

        assertEquals(MetaUpgradeId.entries.size, model.upgrades.size)
        val integrity = model.upgrades.first()
        assertEquals(MetaUpgradeId.CORE_INTEGRITY, integrity.id)
        assertEquals(1, integrity.rank)
        assertEquals(MetaUpgradeCatalog.byId(integrity.id).cost(1).toLong(), integrity.nextCost)
        assertTrue(integrity.isAffordable)
    }

    @Test
    fun affordableCardProducesOnlyTheNarrowPurchaseCommand() {
        val model = LabProfileSnapshot(
            economy = PlayerEconomy(matter = 1_000L),
            progress = LabProgress(),
        ).toRenderModel()
        val reduction = LabReducer.reduce(
            LabState(model),
            LabAction.PurchaseRequested(MetaUpgradeId.CORE_INTEGRITY),
        )

        assertEquals(
            MetaUpgradeId.CORE_INTEGRITY,
            assertIs<LabEffect.Purchase>(reduction.effects.single()).id,
        )
    }

    @Test
    fun unaffordableOrMaxedCardsDoNotRequestMutation() {
        val poor = LabProfileSnapshot(
            economy = PlayerEconomy(matter = 0L),
            progress = LabProgress(),
        ).toRenderModel()
        assertTrue(
            LabReducer.reduce(
                LabState(poor),
                LabAction.PurchaseRequested(MetaUpgradeId.CORE_INTEGRITY),
            ).effects.isEmpty(),
        )

        val maxRanks = MetaUpgradeId.entries.map { MetaUpgradeCatalog.byId(it).maxRanks }
        val maxed = LabProfileSnapshot(
            economy = PlayerEconomy(matter = Long.MAX_VALUE),
            progress = LabProgress(maxRanks),
        ).toRenderModel()
        assertTrue(
            LabReducer.reduce(
                LabState(maxed),
                LabAction.PurchaseRequested(MetaUpgradeId.CORE_INTEGRITY),
            ).effects.isEmpty(),
        )
    }

    @Test
    fun backEmitsClickThenNavigationOutput() {
        val model = LabProfileSnapshot(PlayerEconomy(), LabProgress()).toRenderModel()
        val effects = LabReducer.reduce(LabState(model), LabAction.Back).effects
        assertEquals(LabOutput.Cue(AudioCue.UI_CLICK), assertIs<LabEffect.Emit>(effects[0]).output)
        assertEquals(LabOutput.Back, assertIs<LabEffect.Emit>(effects[1]).output)
    }
}
