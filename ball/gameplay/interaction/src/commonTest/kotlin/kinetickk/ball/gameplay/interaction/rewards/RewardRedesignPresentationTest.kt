// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RuleLineKind
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.upsideText
import kinetickk.ball.gameplay.interaction.canvas.overlayUpsideMultiplier
import kinetickk.ball.gameplay.nucleus.render.BuildForecast
import kinetickk.ball.gameplay.nucleus.render.BuildProjection
import kinetickk.ball.gameplay.nucleus.render.ForecastKind
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkVisionMode
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.liabilityText
import kinetickk.ball.gameplay.interaction.canvas.UNKNOWN_PAIRING_NAME
import kinetickk.ball.gameplay.interaction.canvas.overlayName
import kinetickk.ball.gameplay.interaction.canvas.overlayRelicLinks
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.ball.gameplay.nucleus.render.RelicLinkKind
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.render.RewardPreview
import kinetickk.ball.gameplay.nucleus.render.RewardStatChange
import kinetickk.ball.gameplay.nucleus.render.TotemAction
import kinetickk.ball.content.api.ConversionKind
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.DamageBasis
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.StatDelta
import kinetickk.ball.gameplay.nucleus.render.SeveredRelicProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RewardRedesignPresentationTest {
    private val vector = EquippedRelic(RelicId.PERPETUAL_VECTOR, 2)
    private val echo = EquippedRelic(RelicId.ECHO_CHAMBER, 1)
    private val gyre = EquippedRelic(RelicId.GYRE_ENGINE, 1)

    @Test
    fun theLevelBadgeOnlyAnnouncesALevelUpAndLayoutsFollowTheChoiceType() {
        val levelUp = rewardFixtureModel(choices = listOf(itemChoice(0), itemChoice(1), itemChoice(2))).rewardPresentation()
        assertEquals(15, levelUp.level)
        assertEquals(RewardLayoutKind.CARDS, levelUp.kind)
        assertEquals(1, levelUp.defaultSelection(), "The middle card is lifted first, as dealt on the board")
        val directed = rewardFixtureModel(choices = listOf(itemChoice(0)), directedChoice = true).rewardPresentation()
        assertNull(directed.level, "A point-of-interest reward is not a level-up")
        val weapon = rewardFixtureModel(choiceType = ChoiceType.WEAPON, choices = listOf(
            ChoiceOption(ChoiceType.WEAPON, "Arc Coil", "", "CHAIN", weaponId = WeaponId.ARC_COIL))).rewardPresentation()
        assertNull(weapon.level)
        val totem = rewardFixtureModel(choiceType = ChoiceType.TOTEM, choices = listOf(
            ChoiceOption(ChoiceType.TOTEM, "Amplify Flux Wake", "", "", weaponId = WeaponId.FLUX_WAKE, totemAction = TotemAction.AMPLIFY_CURRENT),
        )).rewardPresentation()
        assertEquals(RewardLayoutKind.TOTEM, totem.kind)
        assertEquals(0, totem.defaultSelection())
    }

    @Test
    fun itemCardsReadRarityStackTagsAndPlainOrComparedValuesWithoutArrows() {
        // Calibrated Coil: +8 % weapon power per copy and a weapon-hit proc; the second copy adds its
        // diminishing share (1 to 1.7 stack magnitude).
        val plain = rewardFixtureModel(choices = listOf(itemChoice(3)), itemStacks = listOf(0, 0, 0, 1, 0)).rewardCardPresentation(itemChoice(3), 0)
        assertEquals(1, plain.rank)
        assertEquals("Common", plain.bandStart)
        assertEquals("Stack 2/3", plain.bandEnd)
        assertNull(plain.family)
        assertEquals(listOf("Arsenal"), plain.tags)
        assertEquals(listOf("+5.6%"), plain.changes.map { it.after })
        assertTrue(plain.changes.all { it.before.isEmpty() && it.improved })
        assertTrue(plain.descriptions.isNotEmpty(), "The trigger and effect of the clause are on the card")
        assertTrue(plain.liabilities.isEmpty())
        assertTrue(plain.isNewDiscovery)

        // A Reborn copy is half again as strong and says so.
        val reborn = rewardFixtureModel(choices = listOf(itemChoice(3, reborn = true)), itemStacks = listOf(0, 0, 0, 1, 0))
            .rewardCardPresentation(itemChoice(3, reborn = true), 0)
        assertEquals(listOf("+8.4%"), reborn.changes.map { it.after })
        assertEquals(listOf("Arsenal", "Reborn"), reborn.tags)

        // A high-risk stat trade reads its gain as a stat and its cost as a cost line, and carries the Risk tag.
        val risk = rewardFixtureModel(choices = listOf(itemChoice(2))).rewardCardPresentation(itemChoice(2), 0)
        assertEquals(listOf("+35%"), risk.changes.map { it.after })
        assertTrue(risk.changes.all { it.improved })
        assertEquals(listOf(RuleLineKind.COST), risk.rules.map { it.kind }, "The negative stat is the card's cost")
        assertEquals(listOf("−25\u00A0% cooling"), risk.liabilities)
        assertEquals(listOf("Arsenal", "Overclock", "Risk"), risk.tags)
        assertEquals(4, risk.rank)

        // A rule-only legendary has no stat lines; its rule is the card's text.
        val legendary = rewardFixtureModel(choices = listOf(itemChoice(1))).rewardCardPresentation(itemChoice(1), 0)
        assertEquals(5, legendary.rank)
        assertEquals("Legendary", legendary.bandStart)
        assertEquals("Stack 1/1", legendary.bandEnd)
        assertTrue(legendary.changes.isEmpty())
        assertTrue(legendary.descriptions.isNotEmpty())

        val compared = rewardFixtureModel(
            choices = listOf(itemChoice(0)), discoveredItems = setOf(0),
            previews = listOf(RewardPreview(immutableListOf(RewardStatChange("Dash power", 873f, 1083f, "")))),
        ).rewardCardPresentation(itemChoice(0), 0)
        assertEquals("873", compared.changes.single().before)
        assertEquals("1083", compared.changes.single().after)
        assertEquals(false, compared.isNewDiscovery)
        val text = listOf(plain, reborn, risk, legendary, compared).flatMap { card ->
            card.changes.flatMap { listOf(it.name, it.before, it.after) } + card.descriptions + card.liabilities +
                listOfNotNull(card.bandStart, card.bandEnd, card.family) + card.operation + card.tags
        }
        assertTrue(text.none { it.contains('→') || it.contains('·') }, text.toString())
    }

    @Test
    fun itemCardsCarryChipsRuleLinesAndElementsForTheirFaceAndIcon() {
        val coil = rewardFixtureModel(choices = listOf(itemChoice(3)), build = BuildProjection(tagPoints = immutableListOf(0, 0, 0, 2)))
            .rewardCardPresentation(itemChoice(3), 0)
        // A clause reads as a trigger heading its effect; a tag the build already carries is lit.
        assertEquals(listOf(RuleLineKind.TRIGGER, RuleLineKind.EFFECT), coil.rules.map { it.kind })
        assertEquals(listOf(RewardChip(RewardChipKind.TAG, "Arsenal", tag = BuildTag.ARSENAL, active = true)), coil.chips)
        assertTrue(coil.elements.isEmpty(), "A Kinetic item shows no element pips")

        // Risk and Reborn lead the chips; Reborn carries the Rebirth tier as ring marks.
        val risk = rewardFixtureModel(choices = listOf(itemChoice(2, reborn = true)), rebirthLevel = 3).rewardCardPresentation(itemChoice(2, reborn = true), 0)
        assertEquals(listOf(RewardChipKind.RISK, RewardChipKind.REBORN, RewardChipKind.TAG, RewardChipKind.TAG), risk.chips.map { it.kind })
        assertEquals(3, risk.chips[1].after)
        assertTrue(risk.reborn && risk.rebirthTier == 3)

        // An elemental item: its element drives the band pips and the icon hue; costs come last.
        val stillpoint = rewardFixtureModel(choices = listOf(itemChoice(0))).rewardCardPresentation(itemChoice(0), 0)
        assertEquals(listOf(StatusElement.CRYO), stillpoint.elements)
        assertEquals(StatusElement.CRYO, stillpoint.rules.first { it.kind == RuleLineKind.EFFECT }.element)
        assertEquals(Kk.ECryo, stillpoint.iconColor(KkRolePalette.Default))
        // A Kinetic item's icon takes the preset's volt.
        KkVisionMode.entries.forEach { mode -> assertEquals(mode.palette.you, coil.iconColor(mode.palette), "$mode") }
        val rules = stillpoint.rules.map { it.kind }
        assertEquals(rules.filter { it != RuleLineKind.COST } + rules.filter { it == RuleLineKind.COST }, rules)
        assertTrue(stillpoint.rules.none { it.kind == RuleLineKind.PASSIVE }, "Passive stats are the card's stat lines")
    }

    @Test
    fun forecastChipsKeepTheBuildPriorityAndHideWhatThePlayerHasNotMet() {
        val choice = itemChoice(0)
        fun card(discovered: Set<ReactionId>) = rewardFixtureModel(
            choices = listOf(choice), discoveredReactions = discovered,
            previews = listOf(RewardPreview(forecasts = immutableListOf(
                BuildForecast(ForecastKind.AFFINITY, element = StatusElement.CRYO, before = 1, after = 2),
                BuildForecast(ForecastKind.TAG_TIER, tag = BuildTag.ANCHOR, before = 0, after = 1),
                BuildForecast(ForecastKind.REACTION, reaction = ReactionId.THERMAL_SHOCK, discovered = ReactionId.THERMAL_SHOCK in discovered),
            ))),
        ).rewardCardPresentation(choice, 0)
        val unmet = card(emptySet()).forecast
        assertEquals(listOf(RewardChipKind.REACTION, RewardChipKind.TAG_TIER, RewardChipKind.AFFINITY), unmet.map { it.kind })
        assertEquals(UNKNOWN_PAIRING_NAME, unmet[0].label, "An unmet reaction shows only its two elements")
        assertEquals(listOf(StatusElement.THERMAL, StatusElement.CRYO), unmet[0].elements)
        assertEquals(listOf(0, 1, 3), unmet[1].let { listOf(it.before, it.after, it.pips) }, "Tier pips, never a numeral")
        assertEquals("Cryo 2/2", unmet[2].label)
        assertTrue(unmet[2].active, "Reaching the affinity threshold lights the chip")
        assertEquals("Thermal Shock", card(setOf(ReactionId.THERMAL_SHOCK)).forecast[0].label)
        assertEquals("Термо", rewardFixtureModel(choices = listOf(choice), previews = listOf(RewardPreview(forecasts = immutableListOf(
            BuildForecast(ForecastKind.AFFINITY, element = StatusElement.THERMAL, before = 2, after = 3)))))
            .rewardCardPresentation(choice, 0, AppLanguage.Russian).forecast.single().label.substringBefore(' '))
    }

    @Test
    fun suppressionStrikesTheLinesABoundRelicVoidsOnlyWhenTheNucleusReportsIt() {
        val aegis = itemChoice(5)
        val glass = listOf(EquippedRelic(RelicId.GLASS_COMET, 1))
        val reported = rewardFixtureModel(choices = listOf(aegis), relics = glass,
            previews = listOf(RewardPreview(forecasts = immutableListOf(BuildForecast(ForecastKind.SUPPRESSED)))))
            .rewardCardPresentation(aegis, 0)
        assertTrue(reported.rules.isNotEmpty() && reported.rules.all { it.suppressed }, "Glass Comet voids the shield-break clause")
        assertEquals(RewardChip(RewardChipKind.SUPPRESSED, "Suppressed", relic = RelicId.GLASS_COMET,
            rune = rewardFixtureContent.relic(RelicId.GLASS_COMET)), reported.forecast.first(), "The chip carries the voiding relic's rune")
        val unreported = rewardFixtureModel(choices = listOf(aegis), relics = glass).rewardCardPresentation(aegis, 0)
        assertTrue(unreported.rules.none { it.suppressed })
        val free = rewardFixtureModel(choices = listOf(aegis),
            previews = listOf(RewardPreview(forecasts = immutableListOf(BuildForecast(ForecastKind.SUPPRESSED))))).rewardCardPresentation(aegis, 0)
        assertTrue(free.rules.none { it.suppressed }, "Without the voiding relic no line is struck")
    }

    @Test
    fun suppressionStrikesThePassiveStatsABoundRelicVoids() {
        // Hardlight-like shield weave and Nanite-like mesh: what Glass Comet and Verdant Rot void are passive stats.
        val weave = ItemDefinition(6, "Hardlight Weave", "Light woven tight enough to hold.", ItemRarity.RARE, immutableListOf(BuildTag.BULWARK),
            stats = immutableListOf(StatDelta(ItemEffect.SHIELD_CAPACITY, 10f), StatDelta(ItemEffect.MASS, 0.1f)),
            rules = immutableListOf(ItemRule.Convert(ConversionKind.OVERHEAL_TO_SHIELD, 1f)), maxStacks = 2, unlockLevel = 1)
        val mesh = ItemDefinition(7, "Nanite Mesh", "Tiny menders in the hull.", ItemRarity.UNCOMMON, immutableListOf(BuildTag.BULWARK),
            stats = immutableListOf(StatDelta(ItemEffect.REGEN, 1.2f)), maxStacks = 3, unlockLevel = 1)
        val decay = ItemDefinition(8, "Rot Converter", "Mending turned to rot.", ItemRarity.RARE, immutableListOf(BuildTag.BULWARK),
            rules = immutableListOf(ItemRule.Convert(ConversionKind.REGENERATION_TO_DECAY, 0.5f)), maxStacks = 1, unlockLevel = 1)
        val content = rewardFixtureContent.copy(items = (rewardFixtureContent.items + weave + mesh + decay).toImmutableList())
        val suppressed = RewardPreview(forecasts = immutableListOf(BuildForecast(ForecastKind.SUPPRESSED)))
        fun card(item: ItemDefinition, relic: RelicId, preview: RewardPreview?, stacks: List<Int> = List(content.items.size) { 0 }): RewardCardPresentation {
            val choice = ChoiceOption(ChoiceType.ITEM, item.name, "", "RARE", itemId = item.id)
            return rewardFixtureModel(content = content, choices = listOf(choice), relics = listOf(EquippedRelic(relic, 1)),
                itemStacks = stacks, previews = listOfNotNull(preview)).rewardCardPresentation(choice, 0)
        }
        // Without a nucleus preview the card's own stat lines are struck once the nucleus reports the suppression.
        assertTrue(card(weave, RelicId.GLASS_COMET, null).changes.none { it.suppressed }, "Nothing is struck until the nucleus reports it")
        val weaveChoice = ChoiceOption(ChoiceType.ITEM, weave.name, "", "RARE", itemId = weave.id)
        val reported = rewardFixtureModel(content = content, choices = listOf(weaveChoice), relics = listOf(EquippedRelic(RelicId.GLASS_COMET, 1)),
            build = BuildProjection(suppressedItemIds = immutableListOf(weave.id))).rewardCardPresentation(weaveChoice, 0)
        assertEquals(listOf(true, false), reported.changes.map { it.suppressed }, "Shield capacity is void under Glass Comet, mass is not")
        // With a preview, the nucleus stat rows of the voided stats are struck and the chip names the relic.
        val previewed = card(weave, RelicId.GLASS_COMET, RewardPreview(immutableListOf(RewardStatChange("Shield", 0f, 10f, ""),
            RewardStatChange("Mass", 1.18f, 1.28f, "")), suppressed.forecasts))
        assertEquals(listOf(true, false), previewed.changes.map { it.suppressed })
        assertTrue(previewed.rules.all { it.suppressed }, "The overheal conversion is void too")
        assertEquals(RelicId.GLASS_COMET, previewed.forecast.first().relic)
        val rotted = card(mesh, RelicId.VERDANT_ROT, RewardPreview(immutableListOf(RewardStatChange("Regeneration", 0f, 1.2f, "/s")), suppressed.forecasts))
        assertEquals(listOf(true), rotted.changes.map { it.suppressed }, "Verdant Rot voids regeneration")
        assertEquals(RelicId.VERDANT_ROT, rotted.forecast.first().relic)
        val converted = card(mesh, RelicId.VERDANT_ROT, RewardPreview(immutableListOf(RewardStatChange("Regeneration", 0f, 1.2f, "/s")), suppressed.forecasts),
            stacks = List(content.items.size) { if (it == decay.id) 1 else 0 })
        assertTrue(converted.changes.none { it.suppressed }, "Regeneration turned into decay is not voided")
    }

    @Test
    fun relicCardsReadKeysAttunementLinksAndTheirRankValues() {
        val stasis = ChoiceOption(ChoiceType.RELIC, "Stasis Anchor", "", "CRYO", relicId = RelicId.STASIS_ANCHOR, relicAction = RelicChoiceAction.ACQUIRE)
        val model = rewardFixtureModel(choiceType = ChoiceType.RELIC, relics = listOf(vector, gyre), choices = listOf(stasis),
            itemStacks = listOf(1, 0, 0, 0, 0, 0))
        val card = model.rewardCardPresentation(stasis, 0)
        // Stillness opposes the bound Motion keys; Cold meets no bound key.
        assertEquals(listOf(RewardRelicKey("Stillness", RewardKeyState.OPPOSED), RewardRelicKey("Cold", RewardKeyState.NEUTRAL)), card.keys)
        // Anchor is lit: the build owns Stillpoint, an Anchor item. Attuned tags carry the relic's aspect.
        assertEquals(listOf(true, false), card.chips.map { it.active })
        assertTrue(card.chips.all { it.kind == RewardChipKind.ATTUNED && it.aspect == RelicAspect.CRYO })
        // The named discord with Perpetual Vector stays unnamed until met; the plain dissonance with Gyre Engine shows its multiplier.
        assertEquals(listOf("− ?", "Dissonance ×0.85"), card.forecast.map { it.label })
        assertEquals(listOf(false, true), card.forecast.map { it.discovered })
        val twice = rewardFixtureModel(choiceType = ChoiceType.RELIC, relics = listOf(gyre, EquippedRelic(RelicId.BLINK_DRIVE, 1)), choices = listOf(stasis))
            .rewardCardPresentation(stasis, 0)
        assertEquals(listOf("Dissonance ×0.72"), twice.forecast.map { it.label }, "Generic links of one kind compound into one chip")
        // The rank value of the upside is located in its text.
        val upside = card.descriptions.single()
        assertEquals(listOf("45"), card.values.map { upside.substring(it) })
        val vectorCard = ChoiceOption(ChoiceType.RELIC, "Perpetual Vector", "", "KINETIC", relicId = RelicId.PERPETUAL_VECTOR,
            relicAction = RelicChoiceAction.ACQUIRE)
        val ranked = rewardFixtureModel(choiceType = ChoiceType.RELIC, relics = listOf(vector), choices = listOf(vectorCard))
            .rewardCardPresentation(vectorCard, 0, AppLanguage.Russian)
        assertEquals(listOf("18"), ranked.values.map { ranked.descriptions.single().substring(it) }, "Rank 3 value in the Russian text")
        val shared = rewardFixtureContent.relic(RelicId.STORMHEART_CAPACITOR).upsideValueRanges(AppLanguage.English, 2)
        assertEquals(listOf("240"), shared.map { rewardFixtureContent.relic(RelicId.STORMHEART_CAPACITOR).upsideText(AppLanguage.English, 2).substring(it) })
    }

    @Test
    fun upsideMultipliersFollowHarmonyDissonanceNamedPairingsAndTheHollowCrown() {
        val content = rewardFixtureContent
        val matrix = listOf(RelicId.STORMHEART_CAPACITOR, RelicId.GYRE_ENGINE, RelicId.STASIS_ANCHOR, RelicId.AEGIS_INVERSION)
        // Stormheart: harmony with Gyre (Motion), dissonance with Stasis, the named Faraday Cage with Aegis.
        assertEquals(1.1f * 0.85f, overlayUpsideMultiplier(matrix, 0, content), 0.0001f)
        assertEquals(1.1f * 0.85f, overlayUpsideMultiplier(matrix, 1, content), 0.0001f)
        assertEquals(1f, overlayUpsideMultiplier(matrix, 3, content))
        val crowned = listOf(RelicId.HOLLOW_CROWN, RelicId.ECHO_CHAMBER, null, null)
        assertEquals(0.75f, overlayUpsideMultiplier(crowned, 1, content))
        assertEquals(1f, overlayUpsideMultiplier(crowned, 0, content), "The Crown never weakens itself")
        assertEquals(1f, overlayUpsideMultiplier(crowned, 2, content), "An empty slot has no upside")
        val severed = listOf(RelicId.ECHO_CHAMBER, null, null, null)
        assertEquals(0.75f, overlayUpsideMultiplier(severed, 0, content, crownSevered = true), "A severed Crown's weakening lingers")
    }

    @Test
    fun replacingTheHollowCrownKeepsItsWeakeningThroughSeverance() {
        val crown = EquippedRelic(RelicId.HOLLOW_CROWN, 1)
        val bound = listOf(crown, echo, gyre, EquippedRelic(RelicId.STASIS_ANCHOR, 1))
        val replaceCrown = ChoiceOption(ChoiceType.RELIC_BIND, "Replace Hollow Crown", "", "", relicId = RelicId.BLINK_DRIVE,
            relicAction = RelicChoiceAction.REPLACE, relicSlot = 0)
        val preview = requireNotNull(rewardFixtureModel(choiceType = ChoiceType.RELIC_BIND, relics = bound, choices = listOf(replaceCrown))
            .rewardCardPresentation(replaceCrown, 0).relicPreview)
        // Blink Drive shares Motion with Gyre Engine: a harmony.
        val harmony = preview.rows.single { it.title == OverlayRedesignText.Harmony.english }
        assertEquals("Gyre Engine", harmony.kicker)
        // Gyre Engine after: harmony with Blink, dissonance with Stasis, and the lingering Crown (1.10 × 0.85 × 0.75).
        assertEquals("×0.70", harmony.after, "The severed Crown still weakens the partner")
        assertEquals("×0.64", harmony.before, "Gyre Engine now: dissonance with Stasis Anchor under the bound Crown")
        // The nucleus multipliers lead the current value once it has compiled them.
        val compiled = BuildProjection(relicUpsides = List(RelicId.entries.size) { if (it == RelicId.GYRE_ENGINE.ordinal) 0.5f else 1f }.toImmutableList(),
            severed = immutableListOf(SeveredRelicProjection(RelicId.HOLLOW_CROWN, 30f)))
        val nucleus = requireNotNull(rewardFixtureModel(choiceType = ChoiceType.RELIC_BIND, relics = bound, choices = listOf(replaceCrown), build = compiled)
            .rewardCardPresentation(replaceCrown, 0).relicPreview)
        assertEquals("×0.50", nucleus.rows.single { it.title == OverlayRedesignText.Harmony.english }.before)
    }

    @Test
    fun declineIsACalmCardAndHeirloomsAreGold() {
        val hollow = ChoiceOption(ChoiceType.RELIC, "Leave hollow", "", "DECLINE", relicAction = RelicChoiceAction.DECLINE)
        val heirloom = ChoiceOption(ChoiceType.RELIC, "Event Horizon", "", "GRAVITIC", relicId = RelicId.EVENT_HORIZON,
            relicAction = RelicChoiceAction.ACQUIRE, heirloom = true)
        val model = rewardFixtureModel(choiceType = ChoiceType.RELIC, choices = listOf(heirloom, hollow), rebirthLevel = 4)
        val decline = model.rewardCardPresentation(hollow, 1)
        assertTrue(decline.decline)
        assertEquals(1, decline.rank, "No rarity effects on the calm card")
        assertTrue(decline.forecast.isEmpty() && decline.chips.isEmpty())
        val gold = model.rewardCardPresentation(heirloom, 0)
        assertTrue(gold.heirloom)
        assertEquals(OverlayRedesignText.Heirloom.english, model.rewardPresentation().heading, "Gold carries the Heirloom offer's heading")
        assertTrue(model.rewardPresentation().heirloom)
        assertEquals("Gravitic", gold.bandStart, "The band keeps the aspect; the gold rim and chip mark the heirloom")
        assertEquals(RewardChipKind.HEIRLOOM, gold.chips.first().kind)
        assertEquals(4, gold.chips.first().after)
    }

    @Test
    fun nucleusConditionLabelsBecomeANameAndAConditionInBothLanguages() {
        val english = RewardStatChange("Damage: speed ≥500", 5f, 10f, "%").presentation(AppLanguage.English)
        assertEquals("Damage", english.name)
        assertEquals("speed ≥500", english.condition)
        val russian = RewardStatChange("Damage: speed ≥500", 5f, 10f, "%").presentation(AppLanguage.Russian)
        assertEquals("Урон", russian.name)
        assertEquals("скорость ≥500", russian.condition)
        val single = RewardStatChange("Pull: hit", 0f, 24f, "u/s").presentation(AppLanguage.Russian)
        assertEquals("Притяжение при попадании", single.name)
        assertNull(single.condition)
        assertNull(RewardStatChange("Dash heat", 36f, 33f, "").presentation(AppLanguage.English).condition)
    }

    @Test
    fun relicActionsNameTheGameOutcomeForEveryRelicChoiceAction() {
        val policy = RelicPolicy(4, 5)
        fun label(action: RelicChoiceAction, owned: Int = 0, slotRank: Int? = null, equipped: Int = 2) =
            rewardRelicAction(action, owned, slotRank, equipped, policy, AppLanguage.English)
        assertEquals(RewardAction("Bind", RewardTone.YOU), label(RelicChoiceAction.ACQUIRE))
        assertEquals(RewardAction("Meld", RewardTone.YOU), label(RelicChoiceAction.ACQUIRE, owned = 2))
        assertEquals(RewardAction("Salvage", RewardTone.MUTE), label(RelicChoiceAction.ACQUIRE, owned = 5))
        assertEquals(RewardAction("Replace", RewardTone.THREAT), label(RelicChoiceAction.ACQUIRE, equipped = 4))
        assertEquals(RewardAction("Meld", RewardTone.YOU), label(RelicChoiceAction.MELD))
        assertEquals(RewardAction("Replace", RewardTone.THREAT), label(RelicChoiceAction.REPLACE, slotRank = 3))
        assertEquals(RewardAction("Meld", RewardTone.YOU), label(RelicChoiceAction.MELD_TARGET, slotRank = 4))
        assertEquals(RewardAction("Salvage", RewardTone.MUTE), label(RelicChoiceAction.MELD_TARGET, slotRank = 5))
        assertEquals(RewardAction("Decline", RewardTone.MUTE), label(RelicChoiceAction.DECLINE))
    }

    @Test
    fun decliningAnOfferNamesWhatTheEmptySlotOrTheHeirloomGivesBackInBothLanguages() {
        val hollow = ChoiceOption(ChoiceType.RELIC, "Leave hollow", "", "DECLINE", relicAction = RelicChoiceAction.DECLINE)
        val heirloom = hollow.copy(title = "Leave the heirloom", heirloom = true)
        val model = rewardFixtureModel(choiceType = ChoiceType.RELIC, relics = listOf(vector), choices = listOf(hollow))
        val card = model.rewardCardPresentation(hollow, 0)
        assertEquals("Leave hollow", card.title)
        assertEquals(RewardAction("Decline", RewardTone.MUTE), card.action)
        assertEquals(listOf(AppLanguage.English.text(OverlayRedesignText.LeaveHollowEffect, model.content.relicPolicy.salvageMatter)), card.descriptions)
        assertTrue(card.descriptions.single().contains("+8"), "The salvage names its matter")
        assertNull(card.relic)
        val preview = requireNotNull(card.relicPreview)
        assertNull(preview.targetSlot, "Declining binds nothing")
        assertTrue(preview.addedLinks.isEmpty() && preview.removedLinks.isEmpty())
        val russian = model.rewardCardPresentation(heirloom, 0, AppLanguage.Russian)
        assertEquals(OverlayRedesignText.LeaveHeirloom.russian, russian.title)
        assertEquals(listOf(AppLanguage.Russian.text(OverlayRedesignText.LeaveHeirloomEffect, model.content.relicPolicy.heirloomDeclineRerolls)), russian.descriptions)
        assertTrue(russian.descriptions.single().contains("+2"))
    }

    @Test
    fun matrixLinksFollowNamedPairingsThenOpposedKeysThenSharedKeys() {
        val content = rewardFixtureContent
        // Perpetual Vector and Gyre Engine share Motion and are the named concord Endless Gyre.
        val named = overlayRelicLinks(listOf(RelicId.PERPETUAL_VECTOR, RelicId.ECHO_CHAMBER, RelicId.GYRE_ENGINE, null), content) { false }
        assertEquals(listOf(RelicLinkKind.CONCORD), named.map { it.kind })
        assertEquals(RelicPairingId.ENDLESS_GYRE, named.single().pairing)
        assertEquals(listOf(0, 2), named.single().slots)
        assertEquals(UNKNOWN_PAIRING_NAME, named.single().overlayName(content, AppLanguage.English), "Unmet pairings stay unnamed")
        val met = overlayRelicLinks(listOf(RelicId.PERPETUAL_VECTOR, RelicId.GYRE_ENGINE), content) { it == RelicPairingId.ENDLESS_GYRE }
        assertEquals("Endless Gyre", met.single().overlayName(content, AppLanguage.English))
        // A shared key without a named pairing harmonizes; opposed keys interfere.
        val harmony = overlayRelicLinks(listOf(RelicId.GYRE_ENGINE, RelicId.STORMHEART_CAPACITOR, null, null), content) { false }
        assertEquals(listOf(RelicLinkKind.HARMONY), harmony.map { it.kind })
        assertEquals(OverlayRedesignText.Harmony.russian, harmony.single().overlayName(content, AppLanguage.Russian))
        val dissonance = overlayRelicLinks(listOf(RelicId.GYRE_ENGINE, RelicId.NEUTRON_HEART, null, null), content) { false }
        assertEquals(listOf(RelicLinkKind.DISSONANCE), dissonance.map { it.kind })
        assertTrue(dissonance.single().interferes)
        // Two copies of one relic do not link, and unrelated keys form nothing.
        assertTrue(overlayRelicLinks(listOf(RelicId.GYRE_ENGINE, RelicId.GYRE_ENGINE, null, null), content) { true }.isEmpty())
        assertTrue(overlayRelicLinks(listOf(RelicId.ECHO_CHAMBER, RelicId.HOLLOW_CROWN, null, null), content) { true }.isEmpty())
    }

    @Test
    fun relicPreviewsPlaceTheIncomingRelicAndSignWhatTheChoiceGainsKeepsAndBreaks() {
        val acquire = ChoiceOption(ChoiceType.RELIC, "Stormheart Capacitor", "", "ION",
            relicId = RelicId.STORMHEART_CAPACITOR, relicAction = RelicChoiceAction.ACQUIRE)
        val model = rewardFixtureModel(choiceType = ChoiceType.RELIC, relics = listOf(vector, echo, gyre), choices = listOf(acquire))
        val card = model.rewardCardPresentation(acquire, 0)
        val preview = requireNotNull(card.relicPreview)
        assertEquals(3, preview.targetSlot, "A new relic binds to the free slot")
        assertEquals(false, preview.replace)
        // Lightning Rod with Perpetual Vector, plain harmony with Gyre Engine (both share Motion).
        assertEquals(listOf(RelicLinkKind.CONCORD, RelicLinkKind.HARMONY), preview.addedLinks.map { it.kind })
        assertEquals(listOf(listOf(0, 3), listOf(2, 3)), preview.addedLinks.map { it.slots })
        assertEquals(RewardAction("Bind", RewardTone.YOU), card.action)
        // The band names the resulting rank; the liability always travels with the relic.
        assertEquals("Rank 1", card.bandEnd)
        assertTrue(card.changes.none { it.name == "Rank" })
        assertEquals(listOf(rewardFixtureContent.relic(RelicId.STORMHEART_CAPACITOR).liabilityText(AppLanguage.English)), card.liabilities)
        val matrix = requireNotNull(model.rewardPresentation().relicMatrix)
        assertEquals(3, matrix.equipped)
        assertEquals(listOf("Perpetual Vector", "Echo Chamber", "Gyre Engine", "Free slot"), matrix.names)

        val replaceVector = ChoiceOption(ChoiceType.RELIC_BIND, "Replace Perpetual Vector", "", "", relicId = RelicId.STORMHEART_CAPACITOR,
            relicAction = RelicChoiceAction.REPLACE, relicSlot = 0)
        val full = rewardFixtureModel(choiceType = ChoiceType.RELIC_BIND, relics = listOf(vector, echo, gyre, EquippedRelic(RelicId.AEGIS_INVERSION, 1)),
            choices = listOf(replaceVector))
        val replace = requireNotNull(full.rewardCardPresentation(replaceVector, 0).relicPreview)
        assertEquals(0, replace.targetSlot)
        assertTrue(replace.replace)
        assertEquals(listOf(RelicPairingId.ENDLESS_GYRE), replace.removedLinks.map { it.pairing })
        // Lost relic, its lingering cost, the incoming upside (one harmony with Gyre Engine), the broken
        // concord, then the formed harmony and concord, each about the bound relic it touches.
        assertEquals(listOf("−", "=", "+", "−", "+", "+"), replace.rows.map { it.sign })
        assertEquals("Perpetual Vector", replace.rows[0].title)
        assertEquals("Cost stays 45 s", replace.rows[1].title)
        assertEquals(rewardFixtureContent.relic(RelicId.PERPETUAL_VECTOR).liabilityText(AppLanguage.English), replace.rows[1].detail)
        assertEquals(listOf(OverlayRedesignText.Upside.english, "×1.00", "×1.10"), replace.rows[2].let { listOf(it.title, it.before, it.after) })
        assertEquals("Gyre Engine", replace.rows[3].kicker, "A broken link names the bound relic left behind")
        val harmony = replace.rows.single { it.title == OverlayRedesignText.Harmony.english }
        assertEquals(listOf("Gyre Engine", "×1.00", "×1.10"), listOf(harmony.kicker, harmony.before, harmony.after), "Gyre Engine gains a harmony")
        val faraday = replace.rows.last()
        assertEquals(listOf("Aegis Inversion", UNKNOWN_PAIRING_NAME), listOf(faraday.kicker, faraday.title))
        assertTrue(faraday.unknown && faraday.after == null, "An unmet pairing keeps its name and effect hidden")
        assertEquals("Replace", replace.primaryLabel)
        assertEquals(rewardFixtureContent.relic(RelicId.STORMHEART_CAPACITOR).liabilityText(AppLanguage.English), replace.panel?.liability)
        val presentation = full.rewardPresentation()
        assertEquals(RewardLayoutKind.RELIC_BIND, presentation.kind)
        assertNull(presentation.defaultSelection(), "Nothing is armed before the player lifts a slot")
        assertEquals("Stormheart Capacitor", presentation.relicPanel?.name)

        // Binding interference is a loss even though it forms a link.
        val stasis = ChoiceOption(ChoiceType.RELIC, "Stasis Anchor", "", "CRYO", relicId = RelicId.STASIS_ANCHOR, relicAction = RelicChoiceAction.ACQUIRE)
        val discord = requireNotNull(rewardFixtureModel(choiceType = ChoiceType.RELIC, relics = listOf(echo, gyre), choices = listOf(stasis))
            .rewardCardPresentation(stasis, 0).relicPreview)
        assertEquals(listOf(RelicLinkKind.DISSONANCE), discord.addedLinks.map { it.kind })
        assertEquals(listOf("−", "−"), discord.rows.map { it.sign }, "The weakened upside, then the dissonance with Gyre Engine")
        assertEquals("×0.85", discord.rows.first().after)
        assertEquals(listOf("×1.00", "×0.85"), discord.rows.last().let { listOf(it.before, it.after) })

        val meld = ChoiceOption(ChoiceType.RELIC_BIND, "Meld Perpetual Vector", "", "", relicId = RelicId.PERPETUAL_VECTOR,
            relicAction = RelicChoiceAction.MELD_TARGET, relicSlot = 0)
        val melding = rewardFixtureModel(choiceType = ChoiceType.RELIC_BIND, relics = listOf(vector, gyre), choices = listOf(meld))
        val meldPreview = requireNotNull(melding.rewardCardPresentation(meld, 0).relicPreview)
        assertEquals(listOf("+", "="), meldPreview.rows.map { it.sign }, "Next rank, kept concord")
        assertEquals("Rank 3", meldPreview.rows.first().title)
        // The row shows the values the meld raises, not the upside the panel already shows.
        assertEquals(listOf("15\u00A0%", "18\u00A0%", null), meldPreview.rows.first().let { listOf(it.before, it.after, it.detail) })
        assertEquals("Rank 3", meldPreview.panel?.rank)
        assertEquals(0, melding.rewardPresentation().defaultSelection())
    }

    @Test
    fun bracketsStackOverlappingLinksAndMarkTheOnesAChoiceBreaks() {
        val model = rewardFixtureModel(choiceType = ChoiceType.RELIC_BIND,
            relics = listOf(vector, echo, gyre, EquippedRelic(RelicId.STORMHEART_CAPACITOR, 1)))
        val matrix = requireNotNull(model.rewardPresentation().relicMatrix)
        assertEquals(setOf(RelicPairingId.ENDLESS_GYRE, RelicPairingId.LIGHTNING_ROD, null), matrix.links.map { it.pairing }.toSet())
        val brackets = relicBrackets(matrix, null)
        val endless = brackets.single { it.link.pairing == RelicPairingId.ENDLESS_GYRE }
        val rod = brackets.single { it.link.pairing == RelicPairingId.LIGHTNING_ROD }
        assertNotEquals(endless.level, rod.level, "Overlapping spans take separate levels")
        val breaking = RewardRelicPreview(0, true, null, emptyList(), emptyList(), listOf(endless.link), emptyList(), null, "", "")
        assertTrue(relicBrackets(matrix, breaking).single { it.link.pairing == RelicPairingId.ENDLESS_GYRE }.faded)
        assertTrue(relicBrackets(matrix, breaking).none { it.link.pairing == RelicPairingId.LIGHTNING_ROD && it.faded })
    }

    @Test
    fun cardsAreDealtWithTheBoardRotationsAndStagger() {
        assertEquals(listOf(-4f, 0f, 3f), (0..2).map { rewardCardRestRotation(it, 3) })
        assertEquals(0f, rewardCardRestRotation(0, 1))
        assertTrue((0..3).map { rewardCardRestRotation(it, 4) }.zipWithNext().all { (a, b) -> a < b })
        assertEquals(0f, rewardDealProgress(120f, 0, 1))
        assertEquals(0f, rewardDealProgress(210f, 1, 1), "90 ms stagger")
        assertEquals(1f, rewardDealProgress(720f, 0, 3), "Common to rare land in 600 ms")
        assertTrue(rewardDealProgress(720f, 0, 5) < 1f, "Epic and legendary take 750 ms")
    }

    @Test
    fun overlayCopyIsTranslatedKeepsArgumentsAndFollowsTheHardRules() {
        val arguments = Regex("\\{\\d+}")
        val forbidden = charArrayOf('·', '→', '←', '↑', '↓', '↵', '›', '‹', '▶', '◀', '◇', '§')
        OverlayRedesignText.entries.forEach { resource ->
            assertTrue(resource.english.isNotBlank() && resource.russian.isNotBlank(), "$resource")
            assertNotEquals(resource.english, resource.russian, "Untranslated $resource")
            assertEquals(arguments.findAll(resource.english).map { it.value }.toList().sorted(),
                arguments.findAll(resource.russian).map { it.value }.toList().sorted(), "$resource arguments")
            listOf(resource.english, resource.russian).forEach { text ->
                assertTrue(text.none { it in forbidden }, "$resource: $text")
                assertTrue(!Regex("\\bL\\d+\\b|LV |Lv\\.").containsMatchIn(text), "$resource level label: $text")
            }
        }
        assertEquals("Lvl", OverlayRedesignText.LevelLabel.english)
        assertEquals("Ур.", OverlayRedesignText.LevelLabel.russian)
    }

    @Test
    fun meldingUsesOneTermForTheHeadingAndItsAction() {
        val english = rewardHeading(ChoiceType.RELIC_BIND, RelicChoiceAction.MELD_TARGET, AppLanguage.English)
        val action = rewardRelicAction(RelicChoiceAction.MELD_TARGET, 0, 1, 3, RelicPolicy(4, 5), AppLanguage.English)
        assertEquals(OverlayRedesignText.Meld.english, english)
        assertEquals(english, action.label)
        // Russian pairs the noun and the verb of the same word (Слияние / Слить).
        val russian = rewardHeading(ChoiceType.RELIC_BIND, RelicChoiceAction.MELD_TARGET, AppLanguage.Russian)
        assertEquals(russian.take(3), OverlayRedesignText.Meld.russian.take(3))
    }

    @Test
    fun relicPreviewPlansShrinkFromTheFullPreviewToTheTersest() {
        val plans = relicPreviewPlans(rowCount = 3, statCount = 2)
        assertEquals(RelicPreviewPlan(3, 3, 2), plans.first(), "Everything first")
        assertEquals(RelicPreviewPlan(1, 0, 0), plans.last(), "At least one titled row always shows")
        // Stat lines go before any detail line, and details before whole rows.
        val firstWithoutStats = plans.indexOfFirst { it.stats == 0 }
        val firstWithoutDetail = plans.indexOfFirst { it.detailRows < it.rows }
        val firstWithoutRow = plans.indexOfFirst { it.rows < 3 }
        assertTrue(firstWithoutStats < firstWithoutDetail && firstWithoutDetail < firstWithoutRow)
        plans.zipWithNext().forEach { (fuller, terser) ->
            assertTrue(terser.rows <= fuller.rows && terser.detailRows <= fuller.detailRows && terser.stats <= fuller.stats)
            assertTrue(terser != fuller)
        }
        assertEquals(listOf(RelicPreviewPlan(0, 0, 0)), relicPreviewPlans(0, 0))
    }

    @Test
    fun flatCardDamageGrowsWithTheSameRunScaleAsGameplay() {
        // Shock Plate with a flat burst instead of its impact burst.
        val content = rewardFixtureContent.copy(items = rewardFixtureContent.items.map { item ->
            if (item.id != 4) item else item.copy(clauses = immutableListOf(EffectClause(EffectTrigger.IMPACT,
                actions = immutableListOf(EffectAction.Burst(140f, DamageBasis.FLAT, 100f)))))
        }.toImmutableList())
        assertEquals(1f, content.rebirth.profile(0).playerPowerMultiplier)
        val boss = content.tempo.bossAtSeconds
        // The nucleus run scale is 1 + 1.6p: 1.8 halfway to the boss and 2.6 at it.
        for ((elapsed, damage) in listOf(boss * 0.5f to "180", boss to "260")) {
            assertEquals(damage.toFloat() / 100f, content.tempo.runScaleGrowth(elapsed), 0.0001f)
            val card = rewardFixtureModel(content = content, choices = listOf(itemChoice(4)), elapsed = elapsed, rebirthLevel = 0)
                .rewardCardPresentation(itemChoice(4), 0)
            val effect = card.rules.single { it.kind == RuleLineKind.EFFECT }.text
            assertTrue(damage in effect, "At $elapsed s the card shows $damage: $effect")
        }
    }
}
