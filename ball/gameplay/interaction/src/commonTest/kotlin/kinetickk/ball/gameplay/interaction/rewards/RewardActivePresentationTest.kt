// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.RuleLineKind
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.defaultSkillDefinitions
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RewardActivePresentationTest {
    private val skills = defaultSkillDefinitions()
    private val flare = skills[SkillId.SOLAR_FLARE.ordinal]
    private val meteor = skills[SkillId.METEOR.ordinal]
    private val base = rewardFixtureContent.items.size

    /** The fixture catalog plus two active items (Solar Flare, Meteor), each with its one passive rule. */
    private val content = rewardFixtureContent.copy(items = (rewardFixtureContent.items + listOf(
        activeItem(base, SkillId.SOLAR_FLARE, ItemRule.ElementPotency(StatusElement.THERMAL, 0.2f)),
        activeItem(base + 1, SkillId.METEOR, ItemRule.BreakPower(StatusElement.THERMAL, 0.25f)),
    )).toImmutableList())

    @Test
    fun anActiveItemCardIsAnItemCardThatLeadsWithItsActivePartAndRanksInsteadOfStacking() {
        val free = activeCard(base, slot = 0)
        val card = model(listOf(free)).rewardCardPresentation(free, 0)
        assertEquals(content.item(base), card.item, "an active item card is an item card")
        assertEquals(ItemRarity.RARE.rank, card.rank)
        assertEquals("Rare", card.bandStart)
        assertEquals("Rank 1/3", card.bandEnd)
        assertEquals(listOf(StatusElement.THERMAL), card.elements)
        val texts = card.rules.map { it.text }
        assertEquals(RuleLineKind.TRIGGER, card.rules.first().kind)
        assertEquals("Active on Z", texts.first())
        assertEquals(flare.summary, texts[1], "the active summary follows its header")
        assertTrue(texts[2].startsWith("Cooldown 10 s"), texts[2])
        assertTrue(texts.none { it == flare.rankThree }, "rank III text waits for rank III")
        val passive = texts.indexOf("Passive")
        assertEquals(RuleLineKind.TRIGGER, card.rules[passive].kind)
        assertTrue(passive in 3 until card.rules.lastIndex, "the passive rule follows its label: $texts")
        // Phone cards keep the mechanics (rules), so the active summary is never dropped.
        assertTrue(card.rules.any { it.kind != RuleLineKind.COST })
        assertTrue(texts.none { '·' in it }, texts.toString())

        // The third copy reaches rank III and says what rank III adds; an unowned card with every slot full says it
        // will replace a slot.
        val third = activeCard(base, slot = 0)
        val ranked = model(listOf(third), stacks = mapOf(base to 2)).rewardCardPresentation(third, 0)
        assertEquals("Rank 3/3", ranked.bandEnd)
        assertTrue(ranked.rules.any { it.text == flare.rankThree })
        val replacing = activeCard(base + 1, slot = null, replaces = true)
        assertEquals("Active replaces a slot", model(listOf(replacing)).rewardCardPresentation(replacing, 0).rules.first().text)

        val russian = model(listOf(free), AppLanguage.Russian).rewardCardPresentation(free, 0, AppLanguage.Russian)
        assertEquals("Ранг 1/3", russian.bandEnd)
        assertEquals("Активный эффект на Z", russian.rules.first().text)
        assertEquals(flare.summary.localizedContent(AppLanguage.Russian), russian.rules[1].text)
        assertTrue(russian.rules.any { it.text == "Пассивный эффект" })
    }

    @Test
    fun theSlotPromptNamesTheHeldActivesAndTheKeepCardReadsAsADecline() {
        val incoming = base + 1
        val replace = ChoiceOption(ChoiceType.ACTIVE_SLOT, "Solar Flare", "", "", itemId = base, skillId = SkillId.SOLAR_FLARE,
            skillRank = 2, activeSlot = 1, incomingItemId = incoming)
        val keep = ChoiceOption(ChoiceType.ACTIVE_SLOT, "Meteor", "", "", incomingItemId = incoming)
        fun prompt(language: AppLanguage) = model(listOf(replace, keep), language, stacks = mapOf(base to 2), type = ChoiceType.ACTIVE_SLOT)
            .rewardPresentation(language)
        val english = prompt(AppLanguage.English)
        assertEquals(RewardLayoutKind.CARDS, english.kind)
        assertEquals("Choose an active to replace", english.heading)
        val slot = english.cards[0]
        assertEquals("Replace Solar Flare", slot.title)
        assertEquals("Rank 2/3", slot.bandEnd)
        assertEquals("Active on X", slot.rules.first().text)
        assertEquals(flare.summary, slot.rules[1].text)
        assertEquals("Meteor takes this slot at rank 1", slot.rules.last().text)
        assertEquals(listOf(StatusElement.THERMAL), slot.elements)
        assertFalse(slot.decline)
        val kept = english.cards[1]
        assertTrue(kept.decline, "the keep card reads as a decline")
        assertEquals("Keep loadout", kept.title)
        assertEquals(listOf("Every active stays and Meteor is salvaged for matter"), kept.descriptions)
        assertEquals(null, kept.item)
        assertEquals(null, english.level, "the prompt is not a level-up")

        val russian = prompt(AppLanguage.Russian)
        assertEquals("Выберите активный предмет на замену", russian.heading)
        assertEquals("Заменить: " + flare.name.localizedContent(AppLanguage.Russian), russian.cards[0].title)
        assertEquals("Ранг 2/3", russian.cards[0].bandEnd)
        assertEquals("Оставить набор", russian.cards[1].title)
        assertTrue(russian.cards[1].descriptions.single().startsWith("Все активные эффекты остаются"))
        assertTrue(meteor.name.localizedContent(AppLanguage.Russian) in russian.cards[0].rules.last().text)
    }

    private fun activeItem(id: Int, skill: SkillId, passive: ItemRule): ItemDefinition {
        val definition = skills[skill.ordinal]
        return ItemDefinition(id, definition.name, definition.summary, ItemRarity.RARE, definition.tags,
            listOfNotNull(definition.element).toImmutableList(), rules = immutableListOf(passive),
            maxStacks = ContentBounds.MAX_SKILL_RANK, unlockLevel = 7, active = skill)
    }

    private fun activeCard(id: Int, slot: Int?, replaces: Boolean = false): ChoiceOption {
        val item = requireNotNull(content.item(id))
        return ChoiceOption(ChoiceType.ITEM, item.name, item.description, item.rarity.displayLabel.uppercase(), itemId = id,
            skillId = item.active, skillRank = 1, activeSlot = slot, replacesActive = replaces)
    }

    private fun model(
        choices: List<ChoiceOption>,
        language: AppLanguage = AppLanguage.English,
        stacks: Map<Int, Int> = emptyMap(),
        type: ChoiceType = ChoiceType.ITEM,
    ) = rewardFixtureModel(content = content, choiceType = type, choices = choices, language = language,
        itemStacks = List(content.items.size) { stacks[it] ?: 0 })
}
