// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.DirectorPersonalityId
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.OmenId
import kinetickk.ball.content.api.RebirthMutator
import kinetickk.ball.content.api.SignalKind
import kinetickk.ball.content.api.SignalOption
import kinetickk.ball.content.api.SignalTuning
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.canvas.overlayGrouped
import kinetickk.ball.gameplay.interaction.canvas.with
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.choiceLayoutGeometry
import kinetickk.ball.gameplay.interaction.localization.RunVarietyText
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RewardPreview
import kinetickk.ball.gameplay.nucleus.render.RewardStatChange
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.LocalAppLanguage
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The run-variety reward overlays: the omen offer (three omens, No omen, the run conditions under its heading) and the
 * event signal prompts (three trades and Ignore), in English and Russian, with the decline and locked looks.
 */
@OptIn(ExperimentalTestApi::class)
class RewardRunVarietyTest {
    @get:Rule
    val compose = createComposeRule()

    private val content = rewardFixtureContent
    private val latin = Regex("[A-Za-z]")

    @Test
    fun anOmenOfferIsHeadedByItsChoiceWithThePatternAndTheKeynoteUnderIt() {
        for (language in AppLanguage.entries) {
            val presentation = omenModel(language).rewardPresentation(language)
            assertEquals(language.text(RunVarietyText.ChooseOmen), presentation.heading, "$language")
            assertEquals(RewardLayoutKind.CARDS, presentation.kind)
            assertNull(presentation.level, "An omen offer is no level-up")
            val conditions = assertNotNull(presentation.conditions, "$language: the omen offer shows the run's conditions")
            assertEquals(listOf(RewardChipKind.PATTERN, RewardChipKind.KEYNOTE), conditions.chips.map { it.kind })
            val (pattern, keynote) = conditions.chips
            assertEquals(content.variety.director.personalities[DirectorPersonalityId.TIDE.ordinal].name.localizedContent(language), pattern.label)
            assertEquals(DirectorPersonalityId.TIDE.sigil(), pattern.icon)
            assertEquals(BuildTag.RAM.displayLabel.localizedContent(language) + " " + StatusElement.THERMAL.displayLabel.localizedContent(language),
                keynote.label)
            assertEquals(BuildTag.RAM, keynote.tag)
            assertEquals(listOf(StatusElement.THERMAL), keynote.elements)
            assertTrue(language.text(RunVarietyText.ConditionPattern) in conditions.description && pattern.label in conditions.description)
            assertTrue(language.text(RunVarietyText.ConditionKeynote) in conditions.description && keynote.label in conditions.description)
            if (language == AppLanguage.Russian) conditions.chips.forEach { assertFalse(latin.containsMatchIn(it.label), it.label) }
        }
        // Each personality has its own sigil.
        assertEquals(DirectorPersonalityId.entries.size, DirectorPersonalityId.entries.map { it.sigil() }.distinct().size)
        // Without a pattern or a Keynote there is nothing to show; other choices never show the conditions.
        assertNull(rewardFixtureModel(choiceType = ChoiceType.OMEN, choices = listOf(noOmen)).rewardPresentation().conditions)
        val item = rewardFixtureModel(choices = listOf(itemChoice(0))).with("directorPersonality" to DirectorPersonalityId.SIEGE)
        assertNull(item.rewardPresentation().conditions)
        // An omen offer is never rerolled: the overlay leaves the Reroll button out.
        assertNull(choiceLayoutGeometry(1440f, 810f, 1f, 4, omenModel(AppLanguage.English).choicesCanReroll).reroll)
    }

    @Test
    fun omenCardsReadTheOmenItsCostAndTheStatsItLeavesInACalmCard() {
        val preview = RewardPreview(immutableListOf(RewardStatChange("Impact power", 1.64f, 2.05f, "×")))
        for (language in AppLanguage.entries) {
            val model = omenModel(language, previews = listOf(preview))
            val cards = model.rewardPresentation(language).cards
            assertEquals(4, cards.size)
            val glass = cards[0]
            val omen = content.variety.omens.definitions[OmenId.GLASS_SUN.ordinal]
            assertEquals(omen.name.localizedContent(language), glass.title)
            assertEquals(language.text(RunVarietyText.OmenBand), glass.bandStart)
            assertEquals(listOf(omen.upside.localizedContent(language)), glass.descriptions)
            assertEquals(listOf(omen.liability.localizedContent(language)), glass.liabilities)
            assertEquals(1, glass.changes.size, "The nucleus preview's stat lines show")
            assertEquals(KkIcon.SYSTEM_OVERDRIVE, glass.icon)
            assertTrue(glass.chips.isEmpty(), "No mutator on Glass Sun")
            // A mutator liability names its mutator on a chip that draws its sigil.
            val dark = cards[1]
            assertEquals(listOf(RewardChipKind.MUTATOR), dark.chips.map { it.kind })
            assertEquals(RebirthMutator.BLACKOUT, dark.chips.single().mutator)
            assertEquals(RebirthMutator.BLACKOUT.displayName.localizedContent(language), dark.chips.single().label)
            assertEquals(listOf(RebirthMutator.BLACKOUT.description.localizedContent(language)), dark.liabilities)
            // The Amplified Keynote takes the run's Keynote tag as its mark.
            assertEquals(KkIcon.TAG_RAM, cards[2].icon)
            cards.take(3).forEach { card ->
                // Calm bone omens: the plain card material, never gold (gold is the Rebirth's), never locked or declined.
                assertEquals(RewardTone.FIXED, card.tone)
                assertEquals(Kk.Bone2, card.accent)
                assertEquals(1, card.rank)
                assertFalse(card.heirloom || card.reborn || card.decline || card.locked)
                if (language == AppLanguage.Russian) (listOf(card.title) + card.descriptions + card.liabilities).forEach {
                    assertFalse(latin.containsMatchIn(it), "Russian omen copy: $it")
                }
            }
            val none = cards[3]
            assertTrue(none.decline, "No omen reads as a decline")
            assertEquals(language.text(RunVarietyText.NoOmen), none.title)
            assertEquals(listOf(language.text(RunVarietyText.NoOmenText)), none.descriptions)
            assertEquals(KkIcon.SYSTEM_CYCLE_CLOCK, none.icon)
            assertEquals(language.text(RunVarietyText.OmenBand), none.bandStart)
            assertTrue(none.liabilities.isEmpty() && none.chips.isEmpty())
        }
        // Every omen has a mark (the Amplified Keynote's needs no Keynote to have one).
        OmenId.entries.forEach { assertNotNull(it.icon(null)) }
    }

    @Test
    fun signalCardsWordTheNucleusTermsUnderTheKindsHeadingInBothLanguages() {
        for (language in AppLanguage.entries) {
            for (kind in SignalKind.entries) {
                val options = SignalOption.entries.filter { it.kind == kind } + SignalOption.IGNORE
                val model = rewardFixtureModel(choiceType = ChoiceType.SIGNAL, choices = options.map { signal(it, kind, cost = 20, payout = 40) },
                    language = language)
                val presentation = model.rewardPresentation(language)
                assertEquals(kind.displayName.localizedContent(language), presentation.heading, "$language $kind")
                assertNull(presentation.conditions)
                presentation.cards.forEachIndexed { index, card ->
                    val option = options[index]
                    // The card title is the content title the build-change notice of a taken card shows.
                    assertEquals(option.title.localizedContent(language), card.title, "$language $option")
                    assertEquals(kind.displayName.localizedContent(language), card.bandStart)
                    assertEquals(option.icon(), card.icon)
                    // The padlock means a locked card (hatch + lock): a card that can be taken never wears it.
                    assertNotEquals(KkIcon.SYSTEM_LOCKED, card.icon, "$option is takeable but shows a padlock")
                    assertTrue(card.descriptions.single().isNotBlank())
                    assertEquals(option == SignalOption.IGNORE, card.decline, "$option decline")
                    if (language == AppLanguage.Russian) (listOf(card.title) + card.descriptions + card.liabilities).forEach {
                        assertFalse(latin.containsMatchIn(it), "Russian signal copy: $it")
                    }
                }
            }
        }
        fun card(option: SignalOption, cost: Int = 0, payout: Int = 0, language: AppLanguage = AppLanguage.English, preview: RewardPreview? = null) =
            rewardFixtureModel(choiceType = ChoiceType.SIGNAL, choices = listOf(signal(option, option.kind ?: SignalKind.MERCHANT, cost, payout)),
                previews = listOfNotNull(preview), language = language).rewardCardPresentation(signal(option, option.kind ?: SignalKind.MERCHANT, cost, payout), 0, language)
        val rare = card(SignalOption.MERCHANT_RARE_OFFER, cost = 60)
        assertEquals(listOf("An item offer of Rare or better"), rare.descriptions)
        assertEquals(listOf("60 matter"), rare.liabilities)
        assertEquals(RewardChip(RewardChipKind.COST, "60", icon = KkIcon.SYSTEM_MATTER), rare.chips.single())
        val rareRu = card(SignalOption.MERCHANT_RARE_OFFER, cost = 1_250, language = AppLanguage.Russian)
        assertEquals(listOf("Предложение предметов: «" + ItemRarity.RARE.displayLabel.localizedContent(AppLanguage.Russian) + "» и выше"),
            rareRu.descriptions)
        assertEquals(listOf(overlayGrouped(1_250, AppLanguage.Russian) + " материи"), rareRu.liabilities, "Large prices group their digits")
        assertTrue(rareRu.liabilities.single().startsWith("1") && "250" in rareRu.liabilities.single())
        assertEquals(listOf("+2 rerolls"), card(SignalOption.MERCHANT_REROLLS, cost = 30, payout = 2).descriptions)
        val blessing = RewardPreview(immutableListOf(RewardStatChange("Impact power", 1f, 1.1f, "×"), RewardStatChange("Weapon power", 1f, 1.1f, "×")))
        val blood = card(SignalOption.SHRINE_BLOOD, cost = 54, preview = blessing)
        assertEquals(listOf("Costs 54 integrity"), blood.liabilities)
        assertEquals(RewardChip(RewardChipKind.COST, "54", icon = KkIcon.SYSTEM_INTEGRITY), blood.chips.single())
        assertEquals(2, blood.changes.size, "A Shrine card shows the stats its blessing leaves")
        val wager = card(SignalOption.GAMBLE_MATTER, cost = 30, payout = 66)
        assertEquals(listOf("45% odds to win 66 matter"), wager.descriptions)
        assertEquals(listOf("Stake: 30 matter"), wager.liabilities)
        assertEquals(listOf("Stake: 1 reroll"), card(SignalOption.GAMBLE_REROLL, cost = 1, payout = 3).liabilities)
        val crate = card(SignalOption.CACHE_CRATE)
        assertEquals(listOf("An item offer of Epic or better"), crate.descriptions)
        assertEquals(listOf(RunVarietyText.CacheCrateCurse.english), crate.liabilities, "A cache's cost is its curse")
        assertTrue(crate.chips.isEmpty())
        assertEquals(listOf(RunVarietyText.OfferLegendary.english), card(SignalOption.CACHE_RELIQUARY).descriptions)
        // At a Rebirth rank with no Legendary item unlocked the reliquary names the Epic offer it deals.
        val epicReliquary = signal(SignalOption.CACHE_RELIQUARY, SignalKind.CURSED_CACHE).copy(signalRarity = ItemRarity.EPIC)
        for (language in AppLanguage.entries) {
            val shown = rewardFixtureModel(choiceType = ChoiceType.SIGNAL, choices = listOf(epicReliquary), language = language)
                .rewardCardPresentation(epicReliquary, 0, language)
            assertEquals(listOf(language.text(RunVarietyText.OfferAtLeast, ItemRarity.EPIC.displayLabel.localizedContent(language))), shown.descriptions)
        }
        val repair = card(SignalOption.DERELICT_REPAIR, payout = 72)
        assertEquals(listOf("Restore 72 integrity"), repair.descriptions)
        assertTrue(repair.liabilities.isEmpty() && repair.chips.isEmpty(), "Salvage costs nothing")
        assertEquals(listOf("+1,240 matter"), card(SignalOption.DERELICT_CARGO, payout = 1_240).descriptions)
        val ignore = card(SignalOption.IGNORE)
        assertEquals(listOf(RunVarietyText.IgnoreText.english), ignore.descriptions)
        assertEquals(Kk.Mute2, ignore.accent)
        // The nucleus lock passes through; the card keeps its copy.
        val locked = rewardFixtureModel(choiceType = ChoiceType.SIGNAL, choices = listOf(signal(SignalOption.MERCHANT_EPIC_OFFER, SignalKind.MERCHANT,
            cost = 150, locked = true))).let { it.rewardCardPresentation(it.choices.single(), 0) }
        assertTrue(locked.locked)
        assertEquals(listOf("150 matter"), locked.liabilities)
    }

    @Test
    fun theCopyStatesTheShippedSignalTuning() {
        // The numbers the copy writes out are the shipped tuning's: a calibration that moves one rewrites its line.
        val tuning = SignalTuning()
        fun percent(share: Float) = (share * 100f).roundToInt().toString() + "%"
        for (copy in listOf(RunVarietyText.ShrineBloodText.english, RunVarietyText.ShrineBloodText.russian)) {
            assertTrue(copy.startsWith("+" + percent(tuning.shrineBloodImpact)) && "+" + percent(tuning.shrineBloodWeapon) in copy.drop(1), copy)
        }
        assertTrue(RunVarietyText.ShrineWardText.english.startsWith("+${tuning.shrineWardShield.roundToInt()} shield"))
        assertTrue("+" + percent(tuning.shrineWardReduction) in RunVarietyText.ShrineWardText.russian)
        assertTrue(RunVarietyText.ShrineWardCost.english.startsWith("−${tuning.shrineWardIntegrity.roundToInt()} "))
        // "Even odds" and "three rerolls".
        assertEquals(0.5f, tuning.gambleRerollChance)
        assertEquals(3, tuning.gambleRerollWin)
        assertEquals(0.5f, tuning.gambleIntegrityChance)
        assertTrue(RunVarietyText.GambleMatterText.english.startsWith(percent(tuning.gambleMatterChance)))
        assertTrue(percent(tuning.gambleMatterChance) in RunVarietyText.GambleMatterText.russian)
        for ((copy, share, seconds) in listOf(
            Triple(RunVarietyText.CacheCrateCurse, "+" + percent(tuning.crateCurse), tuning.crateCurseSeconds),
            Triple(RunVarietyText.CacheReliquaryCurse, "−" + percent(tuning.reliquaryCurse), tuning.reliquaryCurseSeconds),
            Triple(RunVarietyText.CacheIdolCurse, "−" + percent(tuning.idolCurse), tuning.idolCurseSeconds),
        )) {
            assertTrue(share in copy.english && "${seconds.roundToInt()} s" in copy.english, copy.english)
            assertTrue(share in copy.russian && "${seconds.roundToInt()} с" in copy.russian, copy.russian)
        }
    }

    @Test
    fun aLockedCardCannotBeTakenFocusedOrLiftedFirst() {
        val options = listOf(SignalOption.MERCHANT_RARE_OFFER, SignalOption.MERCHANT_EPIC_OFFER, SignalOption.MERCHANT_REROLLS, SignalOption.IGNORE)
        // The run cannot afford either offer: both lock; the bundle and Ignore stay open.
        val model = rewardFixtureModel(choiceType = ChoiceType.SIGNAL, choices = options.mapIndexed { index, option ->
            signal(option, SignalKind.MERCHANT, cost = listOf(600, 1_500, 30, 0)[index], payout = if (index == 2) 2 else 0, locked = index < 2)
        }, width = 1440f, height = 810f)
        val presentation = model.rewardPresentation()
        // The middle card of four is locked: the nearest open card is lifted first, so Take never starts on a locked card.
        assertEquals(2, presentation.defaultSelection())
        // With every card open the middle one still leads.
        assertEquals(1, presentation.copy(cards = presentation.cards.map { it.copy(locked = false) }).defaultSelection())
        val selections = mutableListOf<Int>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(1440.dp, 810.dp)) {
                    RewardContent(model, choiceLayoutGeometry(1440f, 810f, 1f, model.choices.size, model.choicesCanReroll), 0f, true,
                        onSelect = { selections += it }, onReroll = {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        val locked = compose.onNodeWithTag("kinetickk.gameplay.choice.2")
        locked.assertIsNotEnabled()
        locked.performClick()
        // Hovering a locked card lifts nothing: Take still names the open card.
        locked.performMouseInput { moveTo(center) }
        compose.waitForIdle()
        compose.onNodeWithTag("kinetickk.gameplay.take").assertIsEnabled()
        val focus = locked.fetchSemanticsNode().config.getOrNull(SemanticsActions.RequestFocus)
        if (focus != null) {
            locked.performSemanticsAction(SemanticsActions.RequestFocus)
            locked.assertIsNotFocused()
        }
        compose.onNodeWithTag("kinetickk.gameplay.choice.3").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("kinetickk.gameplay.choice.3").assertIsFocused()
        assertTrue(selections.isEmpty(), "A locked card is never taken: $selections")
        compose.onNodeWithTag("kinetickk.gameplay.take").performClick()
        compose.onNodeWithTag("kinetickk.gameplay.choice.3").performClick()
        assertEquals(listOf(2, 2), selections, "Take and a click both take the open bundle")
    }

    @Test
    fun theLockedLookDimsTheCardUnderTheHatch() {
        val choice = signal(SignalOption.MERCHANT_EPIC_OFFER, SignalKind.MERCHANT, cost = 150)
        val open = rewardFixtureModel(choiceType = ChoiceType.SIGNAL, choices = listOf(choice)).rewardCardPresentation(choice, 0)
        /** The card's bright end (its bone text): the luminance 1 % of its pixels reach. */
        fun luminance(card: RewardCardPresentation): Double {
            var bright = 0.0
            runDesktopComposeUiTest(300, 450) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f)) {
                        RewardCard(card, 0, 1f, 0f, true, Modifier.requiredSize(300.dp, 450.dp), onSelect = {})
                    }
                }
                waitForIdle()
                val image = onNodeWithTag("kinetickk.gameplay.choice.1").captureToImage().toPixelMap()
                val values = DoubleArray(image.width * image.height) { index ->
                    val argb = image[index % image.width, index / image.width].toArgb()
                    ((argb shr 16) and 0xFF) * 0.2126 + ((argb shr 8) and 0xFF) * 0.7152 + (argb and 0xFF) * 0.0722
                }
                values.sort()
                bright = values[(values.size * 0.99).toInt()]
            }
            return bright
        }
        val open99 = luminance(open)
        val locked99 = luminance(open.copy(locked = true))
        assertTrue(locked99 < open99 * 0.9, "A locked card's text reads dimmer: $locked99 vs $open99")
        assertTrue(locked99 > open99 * 0.35, "A locked card still shows its face: $locked99 vs $open99")
    }

    @Test
    fun theConditionsSitUnderOrBesideTheHeadingAndClearOfTheCards() {
        var checked = 0
        for ((width, height) in listOf(1440 to 810, 844 to 390, 390 to 844)) for (language in AppLanguage.entries) for (textScale in listOf(1f, 1.75f)) {
            runDesktopComposeUiTest(width, height) {
                val model = omenModel(language, width.toFloat(), height.toFloat(), textScale)
                val layout = choiceLayoutGeometry(width.toFloat(), height.toFloat(), 1f, model.choices.size, model.choicesCanReroll)
                setContent {
                    CompositionLocalProvider(LocalAppLanguage provides language, LocalDensity provides Density(1f)) {
                        Box(Modifier.requiredSize(width.dp, height.dp)) { RewardContent(model, layout, 0f, true, {}, {}) }
                    }
                }
                mainClock.advanceTimeBy(2_000)
                waitForIdle()
                val scene = "omen ${width}x$height $language ${textScale}x"
                val conditions = onNodeWithTag("kinetickk.gameplay.rewards.conditions", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val heading = onNodeWithTag("kinetickk.gameplay.rewards.heading", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue(conditions.width > 0f && conditions.left >= 0f && conditions.right <= width + 0.5f && conditions.top >= 0f, "$scene on screen: $conditions")
                assertTrue(conditions.bottom <= layout.cards.minOf { it.top } + 0.5f, "$scene: the conditions end above the cards: $conditions")
                val overlap = conditions.intersect(heading)
                assertTrue(overlap.width <= 0.5f || overlap.height <= 0.5f, "$scene: the conditions meet the heading: $conditions $heading")
                if (layout.mode == GameplayLayoutMode.COMPACT_LANDSCAPE) assertTrue(conditions.left >= heading.right, "$scene: beside the heading")
                else assertTrue(conditions.top >= heading.bottom - 0.5f, "$scene: under the heading")
                checked++
            }
        }
        assertEquals(12, checked)
    }

    private fun omenChoice(id: OmenId): ChoiceOption {
        val omen = content.variety.omens.definitions[id.ordinal]
        return ChoiceOption(ChoiceType.OMEN, omen.name, omen.upside, "OMEN", omenId = id)
    }

    /** The No omen card as the nucleus deals it. */
    private val noOmen = ChoiceOption(ChoiceType.OMEN, "No omen", "Start the run as it is.", "DECLINE")

    /** An omen offer as the nucleus deals it (Glass Sun, Hungry Dark, Amplified Keynote and No omen) in a Tidewave run keyed to Ram Thermal. */
    private fun omenModel(
        language: AppLanguage,
        width: Float = 1440f,
        height: Float = 810f,
        textScale: Float = 1f,
        previews: List<RewardPreview> = emptyList(),
    ): GameplayRenderModel = rewardFixtureModel(
        choiceType = ChoiceType.OMEN,
        choices = listOf(OmenId.GLASS_SUN, OmenId.HUNGRY_DARK, OmenId.AMPLIFIED_KEYNOTE).map(::omenChoice) + noOmen,
        previews = previews, language = language, width = width, height = height, textScale = textScale,
    ).with("directorPersonality" to DirectorPersonalityId.TIDE, "keynoteTag" to BuildTag.RAM, "keynoteElement" to StatusElement.THERMAL)
}

/** A signal card as the nucleus deals it: the option's content title, its kind uppercase (Ignore: DECLINE) and its terms. */
internal fun signal(option: SignalOption, kind: SignalKind, cost: Int = 0, payout: Int = 0, locked: Boolean = false): ChoiceOption =
    ChoiceOption(ChoiceType.SIGNAL, option.title, "", if (option == SignalOption.IGNORE) "DECLINE" else kind.displayName.uppercase(),
        signalKind = kind, signalOption = option, signalCost = cost, signalPayout = payout, locked = locked)
