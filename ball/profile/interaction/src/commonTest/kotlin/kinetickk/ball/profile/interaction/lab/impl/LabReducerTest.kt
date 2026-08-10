// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.lab.impl

import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.profile.api.LabProfileSnapshot
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.interaction.TestMetaUpgrades
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.lab.api.LabOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LabReducerTest {
    @Test
    fun snapshotMapsEightOrderedUpgradesAndExactNextCost() {
        val ranks = List(MetaUpgradeId.entries.size) { index -> if (index == 0) 1 else 0 }
        val model = LabProfileSnapshot(
            economy = PlayerEconomy(matter = 1_000L),
            progress = LabProgress(ranks),
        ).toRenderModel(TestMetaUpgrades)

        assertEquals(MetaUpgradeId.entries.size, model.upgrades.size)
        val integrity = model.upgrades.first()
        assertEquals(MetaUpgradeId.CORE_INTEGRITY, integrity.id)
        assertEquals(1, integrity.rank)
        assertEquals(TestMetaUpgrades.first().cost(1).toLong(), integrity.nextCost)
        assertTrue(integrity.isAffordable)
    }

    @Test
    fun affordableCardProducesOnlyTheNarrowPurchaseCommand() {
        val model = LabProfileSnapshot(
            economy = PlayerEconomy(matter = 1_000L),
            progress = LabProgress(),
        ).toRenderModel(TestMetaUpgrades)
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
    fun purchaseIntentAlwaysReachesTheCanonicalProfileAuthority() {
        val poor = LabProfileSnapshot(
            economy = PlayerEconomy(matter = 0L),
            progress = LabProgress(),
        ).toRenderModel(TestMetaUpgrades)
        assertEquals(
            MetaUpgradeId.CORE_INTEGRITY,
            assertIs<LabEffect.Purchase>(
                LabReducer.reduce(
                    LabState(poor),
                    LabAction.PurchaseRequested(MetaUpgradeId.CORE_INTEGRITY),
                ).effects.single(),
            ).id,
        )

        val maxRanks = TestMetaUpgrades.map { definition -> definition.maxRanks }
        val maxed = LabProfileSnapshot(
            economy = PlayerEconomy(matter = Long.MAX_VALUE),
            progress = LabProgress(maxRanks),
        ).toRenderModel(TestMetaUpgrades)
        assertEquals(
            MetaUpgradeId.CORE_INTEGRITY,
            assertIs<LabEffect.Purchase>(
                LabReducer.reduce(
                    LabState(maxed),
                    LabAction.PurchaseRequested(MetaUpgradeId.CORE_INTEGRITY),
                ).effects.single(),
            ).id,
        )
    }

    @Test
    fun backEmitsClickThenNavigationOutput() {
        val model = LabProfileSnapshot(PlayerEconomy(), LabProgress()).toRenderModel(TestMetaUpgrades)
        val effects = LabReducer.reduce(LabState(model), LabAction.Back).effects
        assertEquals(ProfileAudioCue.UI_CLICK, assertIs<LabEffect.PlayAudio>(effects[0]).cue)
        assertEquals(LabOutput.Back, assertIs<LabEffect.Emit>(effects[1]).output)
    }
}
