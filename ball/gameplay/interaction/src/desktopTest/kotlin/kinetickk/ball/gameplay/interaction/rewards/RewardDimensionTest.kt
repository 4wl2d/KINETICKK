// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kinetickk.ball.content.api.RunBoonKind
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.canvas.with
import kinetickk.ball.gameplay.interaction.localization.DimensionHudText
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.DimensionProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RewardPreview
import kinetickk.ball.gameplay.nucleus.render.RewardStatChange
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.KkCardMaterial
import kinetickk.foundation.design.KkCardOrnament
import kinetickk.foundation.design.KkFillKind
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkRolePalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The dimension reward overlay: each reward headed by its layer's reward, Blessing and Pact cards wording the boon's
 * gains and a Pact's price in their kind's hue, the Refuse card as a decline naming its Matter, the trove's item cards.
 */
class RewardDimensionTest {
    private val content = rewardFixtureContent
    private val latin = Regex("[A-Za-z]")
    private val palettes = listOf(KkRolePalette.Default, KkRolePalette.Protan, KkRolePalette.Mono)

    /** A boon card as the nucleus deals it: the boon's name and flavor, its kind uppercase, its id. */
    private fun boonChoice(id: Int): ChoiceOption {
        val boon = checkNotNull(content.boon(id))
        return ChoiceOption(ChoiceType.BOON, boon.name, boon.description, boon.kind.name, boonId = id)
    }

    /** The Pact offer's Refuse card as the nucleus deals it. */
    private val refuse = ChoiceOption(ChoiceType.BOON, "Refuse the pact", "Keep the run as it is and salvage 10 Kinetic Matter.", "DECLINE",
        salvageMatter = 10)

    private fun offer(layer: WorldLayer?, type: ChoiceType, choices: List<ChoiceOption>, language: AppLanguage = AppLanguage.English,
        previews: List<RewardPreview> = emptyList()): GameplayRenderModel =
        rewardFixtureModel(choiceType = type, choices = choices, previews = previews, language = language, directedChoice = layer != null)
            .with("dimension" to DimensionProjection(rewardLayer = layer))

    @Test
    fun aDimensionRewardIsHeadedByItsLayersReward() {
        val blessings = content.boons.filter { it.kind == RunBoonKind.BLESSING }.take(3).map { boonChoice(it.id) }
        val pacts = content.boons.filter { it.kind == RunBoonKind.PACT }.take(3).map { boonChoice(it.id) } + refuse
        val trove = listOf(itemChoice(0), itemChoice(1))
        for (language in AppLanguage.entries) {
            assertEquals(language.text(DimensionHudText.BlessingHeading), offer(WorldLayer.ZENITH, ChoiceType.BOON, blessings, language).rewardPresentation(language).heading)
            assertEquals(language.text(DimensionHudText.PactHeading), offer(WorldLayer.NADIR, ChoiceType.BOON, pacts, language).rewardPresentation(language).heading)
            val archive = offer(WorldLayer.ARCHIVE, ChoiceType.ITEM, trove, language).rewardPresentation(language)
            assertEquals(language.text(DimensionHudText.ArchiveHeading), archive.heading)
            // The trove keeps the ordinary item cards (their rarity's frame) and no level badge: it is no level-up.
            assertTrue(archive.cards.all { it.item != null && it.rank == it.item.rarity.rank && it.boonKind == null })
            assertNull(archive.level)
            // The same item offer outside a dimension reward keeps the item heading.
            assertEquals(language.text(GameplayText.ChooseArtifact), offer(null, ChoiceType.ITEM, trove, language).rewardPresentation(language).heading)
            if (language == AppLanguage.Russian) listOf(DimensionHudText.BlessingHeading, DimensionHudText.PactHeading, DimensionHudText.ArchiveHeading)
                .forEach { assertFalse(latin.containsMatchIn(language.text(it)), "$it") }
        }
    }

    @Test
    fun blessingCardsWordTheirGainsInTheShieldHue() {
        val blessings = content.boons.filter { it.kind == RunBoonKind.BLESSING }
        assertEquals(10, blessings.size)
        for (language in AppLanguage.entries) {
            val choices = blessings.map { boonChoice(it.id) }
            val model = offer(WorldLayer.ZENITH, ChoiceType.BOON, choices, language)
            val cards = model.rewardPresentation(language).cards
            cards.zip(blessings).forEach { (card, boon) ->
                assertEquals(boon.name.localizedContent(language), card.title)
                assertEquals(language.text(DimensionHudText.BoonBlessing), card.bandStart)
                assertEquals(boon.gains.size, card.changes.size, "${boon.name}: one line per gain")
                assertTrue(card.changes.all { it.improved && it.after.startsWith("+") }, "${boon.name}: ${card.changes}")
                assertTrue(card.liabilities.isEmpty(), "${boon.name}: a Blessing costs nothing")
                assertEquals(KkIcon.FORMS_RING, card.icon)
                assertEquals(RunBoonKind.BLESSING, card.boonKind)
                assertFalse(card.decline || card.locked || card.heirloom || card.reborn)
                palettes.forEach { roles ->
                    assertEquals(roles.shield, card.accentColor(roles), "${boon.name} under $roles")
                    assertNotEquals(roles.gold.base, card.accentColor(roles), "never gold")
                    val look = rewardCardLook(card, roles)
                    assertEquals(KkCardMaterial.CHROME, look.style.material)
                    assertEquals(roles.shield, look.style.hue)
                    assertEquals(KkFillKind.RINGS, look.style.pattern, "concentric halo rings")
                    assertEquals(KkCardOrnament.DOUBLE_RIM, look.style.ornament)
                }
                if (language == AppLanguage.Russian) (listOf(card.title, card.bandStart) + card.changes.map { it.name })
                    .forEach { assertFalse(latin.containsMatchIn(it), "Russian Blessing copy: $it") }
            }
            val mending = cards.first { it.choice.boonId == 32 }
            val separator = if (language == AppLanguage.Russian) "," else "."
            val perSecond = if (language == AppLanguage.Russian) "/с" else "/s"
            assertEquals(listOf("Maximum integrity".localizedContent(language) to "+25", "Integrity regeneration".localizedContent(language) to
                "+0${separator}8$perSecond"), mending.changes.map { it.name to it.after })
        }
    }

    @Test
    fun pactCardsNameTheirPriceInTheLiabilityBlockInTheHeatHue() {
        val pacts = content.boons.filter { it.kind == RunBoonKind.PACT }
        assertEquals(10, pacts.size)
        for (language in AppLanguage.entries) {
            val cards = offer(WorldLayer.NADIR, ChoiceType.BOON, pacts.map { boonChoice(it.id) }, language).rewardPresentation(language).cards
            cards.zip(pacts).forEach { (card, boon) ->
                assertEquals(language.text(DimensionHudText.BoonPact), card.bandStart)
                assertTrue(card.liabilities.isNotEmpty(), "${boon.name}: every Pact names its price")
                assertEquals(KkIcon.SYSTEM_HEAT, card.icon)
                palettes.forEach { roles ->
                    assertEquals(roles.heat, card.accentColor(roles), "${boon.name} under $roles")
                    val look = rewardCardLook(card, roles)
                    assertEquals(KkFillKind.BARS, look.style.pattern, "vent bars")
                    assertEquals(KkCardOrnament.VENTS, look.style.ornament)
                }
                if (language == AppLanguage.Russian) (listOf(card.title) + card.liabilities).forEach {
                    assertFalse(latin.containsMatchIn(it), "Russian Pact copy: $it")
                }
            }
            fun price(name: String) = cards.first { it.choice.title == name }.liabilities
            val integrity = "Maximum integrity".localizedContent(language)
            assertEquals(listOf("$integrity −20"), price("Cinder Pact"))
            assertEquals(listOf(language.text(DimensionHudText.EnemyIntegrityUp, 12)), price("Hunger Pact"))
            assertEquals(listOf(language.text(DimensionHudText.DamageTakenUp, 15)), price("Wrath Pact"))
            assertEquals(listOf(language.text(DimensionHudText.EnemySpeedUp, 8)), price("Ember Pact"))
            assertEquals(listOf("$integrity −15", language.text(DimensionHudText.DamageTakenUp, 8)), price("Greed Pact"))
        }
        // With the nucleus preview, the gains read before and after and a stat the Pact lowers reads only as its price.
        val cinder = boonChoice(48)
        val preview = RewardPreview(immutableListOf(
            RewardStatChange("Impact power", 1.64f, 2.38f, "×"),
            RewardStatChange("Integrity", 180f, 160f, ""),
        ))
        val card = offer(WorldLayer.NADIR, ChoiceType.BOON, listOf(cinder), previews = listOf(preview)).rewardPresentation().cards.single()
        assertEquals(listOf(preview.changes[0].presentation(AppLanguage.English)), card.changes)
        assertEquals(listOf("Maximum integrity −20"), card.liabilities)
    }

    @Test
    fun theRefuseCardReadsAsADeclineNamingTheMatterItSalvages() {
        for (language in AppLanguage.entries) {
            val cards = offer(WorldLayer.NADIR, ChoiceType.BOON, listOf(boonChoice(48), refuse), language).rewardPresentation(language).cards
            val decline = cards.last()
            assertTrue(decline.decline, "the Refuse card declines the offer")
            assertEquals("Refuse the pact".localizedContent(language), decline.title)
            assertEquals(listOf(language.text(DimensionHudText.RefuseEffect, 10)), decline.descriptions)
            assertEquals(KkIcon.SYSTEM_MATTER, decline.icon)
            assertNull(decline.boonKind)
            assertEquals(KkCardMaterial.CALM, rewardCardLook(decline, KkRolePalette.Default).style.material)
            assertFalse(cards.first().decline)
            if (language == AppLanguage.Russian) (listOf(decline.title) + decline.descriptions).forEach {
                assertFalse(latin.containsMatchIn(it), "Russian Refuse copy: $it")
            }
        }
        // The Matter is the nucleus field's, not a number in the copy.
        val richer = refuse.copy(salvageMatter = 35)
        val card = offer(WorldLayer.NADIR, ChoiceType.BOON, listOf(richer)).rewardPresentation().cards.single()
        assertEquals(listOf(AppLanguage.English.text(DimensionHudText.RefuseEffect, 35)), card.descriptions)
    }
}
