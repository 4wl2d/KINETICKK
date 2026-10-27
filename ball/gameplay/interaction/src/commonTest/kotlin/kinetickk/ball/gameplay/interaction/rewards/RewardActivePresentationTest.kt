// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.RuleLineKind
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.content.api.rankLines
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Active item cards and the active slot prompt: an active item card is an item card that names the slot it takes
 * (its key, or its number on touch, or that it replaces one), leads with its active part at the rank it reaches and
 * labels its passive; the prompt's cards carry the held items and the keep card the Matter the nucleus names. Every
 * number and name is read from content, never written here.
 */
class RewardActivePresentationTest {
    private val content = rewardActiveFixtureContent
    private val base = rewardFixtureActiveBase
    private val flare = content.skill(SkillId.SOLAR_FLARE)
    private val meteor = content.skill(SkillId.METEOR)

    @Test
    fun anActiveItemCardIsAnItemCardThatNamesItsSlotAndLeadsWithItsActivePart() {
        val free = rewardActiveChoice(base, slot = 0)
        val card = model(listOf(free)).rewardCardPresentation(free, 0)
        assertEquals(content.item(base), card.item, "an active item card is an item card")
        assertEquals(ItemRarity.RARE.rank, card.rank)
        assertEquals("Rare", card.bandStart)
        assertEquals("Rank 1/${ContentBounds.MAX_SKILL_RANK}", card.bandEnd)
        assertEquals(listOf(StatusElement.THERMAL), card.elements)
        // The slot it takes leads the identity chips: the player's key as a key cap after the label.
        val chip = card.chips.first()
        assertEquals(RewardChipKind.ACTIVE, chip.kind)
        assertEquals("Active", chip.label)
        assertEquals("Z", chip.keyCap)
        assertEquals("Active Z", chip.spoken)
        assertEquals(flare.tags.map { it.displayLabel }, card.chips.filter { it.kind == RewardChipKind.TAG }.map { it.label })

        // The active section: its header, what the effect does, the numbers of the rank it reaches; then the passive.
        val texts = card.rules.map { it.text }
        assertEquals(RuleLineKind.TRIGGER, card.rules.first().kind)
        assertEquals("Active", texts.first())
        assertEquals(flare.summary, texts[1], "the active summary follows its header")
        assertEquals(rankNumbers(flare.id, 1, AppLanguage.English), texts[2])
        assertTrue(texts.none { it == flare.rankThree }, "rank III text waits for rank III")
        val passive = texts.indexOf("Passive")
        assertEquals(RuleLineKind.TRIGGER, card.rules[passive].kind)
        assertTrue(passive in 3 until card.rules.lastIndex, "the passive rule follows its label: $texts")
        assertTrue(card.rules.take(3).all { it.element == StatusElement.THERMAL }, "the active part takes its element")
        assertTrue((texts + card.chips.map { it.spoken }).none { '·' in it }, texts.toString())

        // The third copy reaches rank III: its numbers and what rank III adds.
        val third = rewardActiveChoice(base, slot = 2, stacks = 2)
        val ranked = model(listOf(third), stacks = mapOf(base to 2)).rewardCardPresentation(third, 0)
        assertEquals("Rank 3/3", ranked.bandEnd)
        assertEquals("C", ranked.chips.first().keyCap, "an owned active ranks its own slot")
        assertTrue(ranked.rules.any { it.text == rankNumbers(flare.id, 3, AppLanguage.English) })
        assertTrue(ranked.rules.any { it.text == flare.rankThree })
    }

    @Test
    fun theSlotChipSaysWhenTakingTheItemReplacesASlotAndNamesTheSlotByNumberOnTouch() {
        val replacing = rewardActiveChoice(base + 1, slot = null, replaces = true)
        val full = model(listOf(replacing)).rewardCardPresentation(replacing, 0)
        val chip = full.chips.first()
        assertEquals(RewardChipKind.REPLACES_ACTIVE, chip.kind)
        assertEquals("Replaces a slot", chip.label)
        assertNull(chip.keyCap)
        assertTrue(full.chips.none { it.kind == RewardChipKind.ACTIVE })
        assertEquals("Active", full.rules.first().text, "the active part still leads")

        val free = rewardActiveChoice(base, slot = 1)
        val touched = model(listOf(free)).rewardPresentation(touch = true).cards.single().chips.first()
        assertEquals(RewardChipKind.ACTIVE, touched.kind)
        assertEquals("Slot 2", touched.label)
        assertNull(touched.keyCap, "touch has no key to show")

        val russian = model(listOf(free, replacing), AppLanguage.Russian)
        val keyed = russian.rewardPresentation(AppLanguage.Russian).cards
        assertEquals("Актив", keyed[0].chips.first().label)
        assertEquals("X", keyed[0].chips.first().keyCap)
        assertEquals("Заменит ячейку", keyed[1].chips.first().label)
        assertEquals("Ячейка 2", russian.rewardPresentation(AppLanguage.Russian, touch = true).cards[0].chips.first().label)
        val card = keyed[0]
        assertEquals("Ранг 1/3", card.bandEnd)
        assertEquals("Активный эффект", card.rules.first().text)
        assertEquals(flare.summary.localizedContent(AppLanguage.Russian), card.rules[1].text)
        assertEquals(rankNumbers(flare.id, 1, AppLanguage.Russian), card.rules[2].text)
        assertTrue(card.rules.any { it.text == "Пассивный эффект" })
    }

    @Test
    fun anActiveInTheCharactersLaneCarriesASignatureChipNamingTheCharacter() {
        val lane = CoreShape.entries.first { SkillId.SOLAR_FLARE in content.coreShape(it).activeLane }
        val other = CoreShape.entries.first { SkillId.SOLAR_FLARE !in content.coreShape(it).activeLane }
        val choice = rewardActiveChoice(base, slot = 0)
        for (language in AppLanguage.entries) {
            val chips = model(listOf(choice), language, coreShape = lane).rewardCardPresentation(choice, 0, language).chips
            val signature = chips.single { it.kind == RewardChipKind.SIGNATURE }
            assertEquals(lane, signature.core)
            assertEquals(content.coreShape(lane).displayName.localizedContent(language), signature.label)
            assertEquals(RewardChipKind.ACTIVE, chips.first().kind, "the slot chip stays first")
        }
        assertTrue(model(listOf(choice), coreShape = other).rewardCardPresentation(choice, 0).chips.none { it.kind == RewardChipKind.SIGNATURE })
        val passive = itemChoice(0)
        assertTrue(model(listOf(passive), coreShape = lane).rewardCardPresentation(passive, 0).chips.none {
            it.kind == RewardChipKind.SIGNATURE || it.kind == RewardChipKind.ACTIVE
        }, "a passive item names no slot and no lane")
    }

    @Test
    fun theSlotPromptShowsTheHeldActivesAndTheKeepCardTheMatterTheNucleusNames() {
        val incoming = base + 1
        val replace = ChoiceOption(ChoiceType.ACTIVE_SLOT, flare.name, "", "", itemId = base, skillId = SkillId.SOLAR_FLARE,
            skillRank = 2, activeSlot = 1, incomingItemId = incoming)
        // Not the incoming rarity's salvage: the card shows the nucleus number and never works it out itself.
        val keep = ChoiceOption(ChoiceType.ACTIVE_SLOT, meteor.name, "", "", incomingItemId = incoming, salvageMatter = 7)
        fun prompt(language: AppLanguage, touch: Boolean = false) =
            model(listOf(replace, keep), language, stacks = mapOf(base to 2), type = ChoiceType.ACTIVE_SLOT).rewardPresentation(language, touch)
        val english = prompt(AppLanguage.English)
        assertEquals(RewardLayoutKind.CARDS, english.kind)
        assertEquals("Replace an active", english.heading)
        assertNull(english.level, "the prompt is not a level-up")
        val slot = english.cards[0]
        assertEquals("Replace ${flare.name}", slot.title)
        assertEquals(content.item(base), slot.item, "the held item's card: its material, hue, glyph and rank")
        assertEquals(2, slot.itemStack)
        assertEquals("Rare", slot.bandStart)
        assertEquals("Rank 2/3", slot.bandEnd)
        assertEquals("X", slot.chips.first().keyCap, "the slot it replaces")
        assertEquals("Active", slot.rules.first().text)
        assertEquals(flare.summary, slot.rules[1].text)
        assertEquals(rankNumbers(flare.id, 2, AppLanguage.English), slot.rules[2].text)
        // The rank keeps its number on its line.
        assertEquals("${meteor.name} takes this slot at rank\u00A01", slot.rules.last().text)
        assertEquals(listOf(StatusElement.THERMAL), slot.elements)
        assertFalse(slot.decline)
        assertEquals("Slot 2", prompt(AppLanguage.English, touch = true).cards[0].chips.first().label)
        val kept = english.cards[1]
        assertTrue(kept.decline, "the keep card reads as a decline")
        assertEquals("Keep loadout", kept.title)
        assertEquals(listOf("Every active stays and ${meteor.name} is salvaged for +7 matter"), kept.descriptions)
        assertNull(kept.item)
        assertTrue(kept.chips.isEmpty())

        val russian = prompt(AppLanguage.Russian)
        assertEquals("Замена активного предмета", russian.heading)
        assertEquals("Заменить: " + flare.name.localizedContent(AppLanguage.Russian), russian.cards[0].title)
        assertEquals("Ранг 2/3", russian.cards[0].bandEnd)
        assertEquals("Активный эффект", russian.cards[0].rules.first().text)
        assertTrue(meteor.name.localizedContent(AppLanguage.Russian) in russian.cards[0].rules.last().text)
        assertEquals("Оставить набор", russian.cards[1].title)
        assertEquals(listOf("Все активные эффекты остаются, ${meteor.name.localizedContent(AppLanguage.Russian)} даёт +7 материи"),
            russian.cards[1].descriptions)

        // A keep card without the nucleus number says what happens without one.
        val bare = keep.copy(salvageMatter = null)
        val unnamed = model(listOf(replace, bare), stacks = mapOf(base to 2), type = ChoiceType.ACTIVE_SLOT).rewardCardPresentation(bare, 1)
        assertEquals(listOf("Every active stays and ${meteor.name} is salvaged for matter"), unnamed.descriptions)
    }

    @Test
    fun activeItemsConnectToTheBuildLikeEveryOtherItem() {
        // Owned: Meteor (an active, Thermal) and Capacitor Aegis (Bulwark, Ion).
        val owned = mapOf(base + 1 to 1, 5 to 1)
        val flareCard = rewardActiveChoice(base, slot = 1)
        val connections = model(listOf(flareCard), stacks = owned).rewardCardPresentation(flareCard, 0).connections
        val items = connections.mapNotNull { it.item?.id }
        assertTrue(base + 1 in items, "an active item meets an owned active of its element: $connections")
        assertTrue(5 in items, "an active item meets an owned item of its tag: $connections")
        // A passive card meets an owned active through its element, as it meets any item.
        val choir = itemChoice(1)
        assertTrue(base + 1 in model(listOf(choir), stacks = owned).rewardCardPresentation(choir, 0).connections.mapNotNull { it.item?.id })
        // An unowned active item meets nothing it does not share.
        val catalyze = rewardActiveChoice(base + 2, slot = 1)
        val catalyzeItems = model(listOf(catalyze), stacks = owned).rewardCardPresentation(catalyze, 0).connections.mapNotNull { it.item?.id }
        val shared = content.item(base + 2)!!.tags
        assertEquals(owned.keys.filter { id -> content.item(id)!!.tags.any { it in shared } }.toSet(), catalyzeItems.toSet())
    }

    /**
     * The numbers of [effect] at [rank] as the card shows them: the content's rank lines after the rank line, each held
     * on one line with its label and unit.
     */
    private fun rankNumbers(effect: SkillId, rank: Int, language: AppLanguage): String =
        content.skill(effect).rankLines(rank, language).drop(1).joinToString(", ") { it.replace(' ', '\u00A0') }

    private fun model(
        choices: List<ChoiceOption>,
        language: AppLanguage = AppLanguage.English,
        stacks: Map<Int, Int> = emptyMap(),
        type: ChoiceType = ChoiceType.ITEM,
        coreShape: CoreShape = CoreShape.ORB,
    ) = rewardFixtureModel(content = content, choiceType = type, choices = choices, language = language,
        itemStacks = List(content.items.size) { stacks[it] ?: 0 }, coreShape = coreShape)
}
