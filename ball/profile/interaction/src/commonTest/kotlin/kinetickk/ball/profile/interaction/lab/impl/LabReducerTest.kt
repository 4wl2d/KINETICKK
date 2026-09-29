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
    fun activationSelectsFirstAndBuysTheSelectedRow() {
        val model = LabProfileSnapshot(PlayerEconomy(matter = 1_000L), LabProgress()).toRenderModel(TestMetaUpgrades)
        val start = LabState(model)
        assertEquals(MetaUpgradeId.CORE_INTEGRITY, start.selectedUpgrade?.id)

        val select = LabReducer.reduce(start, LabAction.Activate(MetaUpgradeId.CRYO_VENTS))
        assertEquals(MetaUpgradeId.CRYO_VENTS, select.state.selectedUpgrade?.id)
        assertTrue(select.effects.isEmpty())
        val buy = LabReducer.reduce(select.state, LabAction.Activate(MetaUpgradeId.CRYO_VENTS))
        assertEquals(MetaUpgradeId.CRYO_VENTS, assertIs<LabEffect.Purchase>(buy.effects.single()).id)
        // Hover/focus selection never purchases.
        assertTrue(LabReducer.reduce(start, LabAction.Select(MetaUpgradeId.CORE_INTEGRITY)).effects.isEmpty())
    }

    @Test
    fun purchaseFeedbackRestartsOnlyForAcceptedPurchases() {
        val model = LabProfileSnapshot(PlayerEconomy(matter = 1_000L), LabProgress()).toRenderModel(TestMetaUpgrades)
        val bought = LabProfileSnapshot(PlayerEconomy(matter = 900L), LabProgress(listOf(1, 0, 0, 0, 0, 0, 0, 0)))
            .toRenderModel(TestMetaUpgrades)
        val first = LabReducer.purchased(LabState(model), MetaUpgradeId.CORE_INTEGRITY, bought, accepted = true)
        assertEquals(LabPurchaseFlash(MetaUpgradeId.CORE_INTEGRITY, 1), first.flash)
        assertEquals(bought, first.model)
        val again = LabReducer.purchased(first, MetaUpgradeId.CORE_INTEGRITY, bought, accepted = true)
        assertEquals(2, again.flash?.sequence)
        val refused = LabReducer.purchased(again, MetaUpgradeId.DATA_ARCHIVE, bought, accepted = false)
        assertEquals(again.flash, refused.flash)
    }

    @Test
    fun rankValuesTotalThePerRankModifierInTheLanguageFormat() {
        val percent = kinetickk.ball.content.api.ItemModifier(kinetickk.ball.content.api.ItemEffect.IMPACT_DAMAGE, 0.05f)
        val flat = kinetickk.ball.content.api.ItemModifier(kinetickk.ball.content.api.ItemEffect.MAX_INTEGRITY, 10f)
        val fraction = kinetickk.ball.content.api.ItemModifier(kinetickk.ball.content.api.ItemEffect.MAX_INTEGRITY, 0.25f)
        val english = kinetickk.foundation.common.localization.AppLanguage.English
        val russian = kinetickk.foundation.common.localization.AppLanguage.Russian
        assertEquals("+25%", labRankValue(percent, 5, english))
        assertEquals("+40", labRankValue(flat, 4, english))
        assertEquals("+0.75", labRankValue(fraction, 3, english))
        assertEquals("+0,75", labRankValue(fraction, 3, russian))
        assertEquals("+0.25", labRankValue(fraction, 1, english))
        assertEquals(null, labRankValue(flat, 0, english))
        assertEquals(null, labRankValue(null, 3, english))
        val model = LabProfileSnapshot(PlayerEconomy(), LabProgress(listOf(1, 2, 0, 0, 0, 0, 0, 3))).toRenderModel(TestMetaUpgrades)
        assertEquals(6 to TestMetaUpgrades.sumOf { it.maxRanks }, model.rankTotals())
    }

    @Test
    fun purchaseFlashNudgesOutAndBackWithOneInvertedFrame() {
        assertEquals(0f, labFlashNudge(0f, 8f))
        assertEquals(8f, labFlashNudge(0.3f, 8f), 0.01f)
        assertEquals(0f, labFlashNudge(1f, 8f))
        assertTrue(labFlashInverted(0f) && labFlashInverted(0.07f))
        assertTrue(!labFlashInverted(0.08f) && !labFlashInverted(1f))
    }

    @Test
    fun backEmitsClickThenNavigationOutput() {
        val model = LabProfileSnapshot(PlayerEconomy(), LabProgress()).toRenderModel(TestMetaUpgrades)
        val effects = LabReducer.reduce(LabState(model), LabAction.Back).effects
        assertEquals(ProfileAudioCue.UI_CLICK, assertIs<LabEffect.PlayAudio>(effects[0]).cue)
        assertEquals(LabOutput.Back, assertIs<LabEffect.Emit>(effects[1]).output)
    }
}
