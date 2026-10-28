// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.DirectorPersonalityId
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.OmenId
import kinetickk.ball.content.api.RebirthMutator
import kinetickk.ball.content.api.RunModifierEffect
import kinetickk.ball.content.api.SignalOption
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.canvas.overlayGrouped
import kinetickk.ball.gameplay.interaction.canvas.overlayIcon
import kinetickk.ball.gameplay.interaction.localization.RunVarietyText
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RewardPreview
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkFillKind
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkMutatorSigilPaths
import kinetickk.foundation.design.KkPathCache
import kinetickk.foundation.design.kkFillBrush

// Run-variety reward cards: the omen offer (three omens and No omen, the run conditions under its heading) and the
// event signal prompts (three trades and Ignore). Cards carry facts only: the nucleus names the omen, the option and
// its terms (cost, payout, lock); this file words them and picks their marks.

/**
 * The run conditions an omen offer shows under its heading: the director pattern (its sigil and name) and the Keynote
 * (its tag's mark, the element's mark in its hue, and both names). [description] reads the two with their captions.
 */
internal data class RewardRunConditions(val chips: List<RewardChip>, val description: String)

/** The run's conditions as chips, or null when the run has neither a director pattern nor a Keynote yet. */
internal fun GameplayRenderModel.rewardRunConditions(language: AppLanguage): RewardRunConditions? {
    val pattern = directorPersonality?.let { id ->
        RewardChip(RewardChipKind.PATTERN, content.variety.director.personalities[id.ordinal].name.localizedContent(language), icon = id.sigil())
    }
    val keynote = rewardKeynoteLabel(language)?.let { label ->
        RewardChip(RewardChipKind.KEYNOTE, label, tag = keynoteTag, elements = listOfNotNull(keynoteElement))
    }
    val captions = listOfNotNull(
        pattern?.let { language.text(RunVarietyText.ConditionPattern) + " " + it.label },
        keynote?.let { language.text(RunVarietyText.ConditionKeynote) + " " + it.label },
    )
    if (captions.isEmpty()) return null
    return RewardRunConditions(listOfNotNull(pattern, keynote), captions.joinToString(", "))
}

/** The Keynote's tag and element names ("Ram Thermal"), or what the run has of them; null without a Keynote. */
internal fun GameplayRenderModel.rewardKeynoteLabel(language: AppLanguage): String? {
    val parts = listOfNotNull(keynoteTag?.displayLabel?.localizedContent(language), keynoteElement?.displayLabel?.localizedContent(language))
    return if (parts.isEmpty()) null else parts.joinToString(" ")
}

/**
 * The director pattern's sigil: a system mark per personality, never a form (the characters'), a tag (the Keynote's)
 * or an element (the hues' channel).
 */
internal fun DirectorPersonalityId.sigil(): KkIcon = when (this) {
    DirectorPersonalityId.TIDE -> KkIcon.SYSTEM_CYCLE_CLOCK
    DirectorPersonalityId.HUNTER_CELL -> KkIcon.SYSTEM_POLARITY
    DirectorPersonalityId.SIEGE -> KkIcon.SYSTEM_DISMANTLED
    DirectorPersonalityId.AMBUSHERS -> KkIcon.SYSTEM_ANOMALY
    DirectorPersonalityId.ELEMENTAL_STORM -> KkIcon.SYSTEM_HEAT
    DirectorPersonalityId.LONG_CALM -> KkIcon.SYSTEM_BRAKE
}

/** An omen's card mark; the Amplified Keynote takes the run's Keynote tag. */
internal fun OmenId.icon(keynote: BuildTag?): KkIcon = when (this) {
    OmenId.GLASS_SUN -> KkIcon.SYSTEM_OVERDRIVE
    OmenId.IRON_TIDE -> KkIcon.SYSTEM_MATTER
    OmenId.HUNGRY_DARK -> KkIcon.SYSTEM_DATA
    OmenId.CROWDED_SKY -> KkIcon.SYSTEM_REROLL
    OmenId.QUICKENED_HOST -> KkIcon.SYSTEM_DASH
    OmenId.RICH_VEINS -> KkIcon.SYSTEM_MATTER
    OmenId.AMPLIFIED_KEYNOTE -> keynote?.overlayIcon() ?: KkIcon.SYSTEM_DATA
    OmenId.VOLATILE_PACT -> KkIcon.SYSTEM_HEAT
    OmenId.FAULT_LINE -> KkIcon.TAG_RAM
    OmenId.ASCETIC_PATH -> KkIcon.SYSTEM_REROLL
    OmenId.CROWNED_QUARRY -> KkIcon.SYSTEM_ELITE
    OmenId.STILL_WATER -> KkIcon.SYSTEM_SHIELD
}

/** The nucleus preview of card [index], when it belongs to [choice]. */
private fun GameplayRenderModel.rewardPreviewOf(choice: ChoiceOption, index: Int): RewardPreview? =
    rewardPreviews.getOrNull(index)?.takeIf { choices.getOrNull(index) == choice }

/**
 * An omen offer card: the omen's name, its upside as the effect and its liability as the cost (a mutator liability adds
 * the mutator's chip with its sigil), the stats it leaves from the nucleus preview, in a calm bone card (never gold:
 * gold is the Rebirth's). The No omen card reads as a decline.
 */
internal fun GameplayRenderModel.rewardOmenCard(choice: ChoiceOption, index: Int, language: AppLanguage): RewardCardPresentation {
    val band = language.text(RunVarietyText.OmenBand)
    val omen = choice.omenId?.let { content.variety.omens.definitions[it.ordinal] }
        ?: return RewardCardPresentation(
            choice = choice,
            accent = Kk.Mute2,
            tag = band,
            descriptions = listOf(language.text(RunVarietyText.NoOmenText)),
            operation = "",
            title = language.text(RunVarietyText.NoOmen),
            icon = KkIcon.SYSTEM_CYCLE_CLOCK,
            bandStart = band,
        )
    val preview = rewardPreviewOf(choice, index)
    val mutator = omen.costs.firstNotNullOfOrNull { (it as? RunModifierEffect.Mutator)?.mutator }
    return RewardCardPresentation(
        choice = choice,
        accent = Kk.Bone2,
        tag = band,
        descriptions = listOf(omen.upside.localizedContent(language)),
        operation = "",
        title = omen.name.localizedContent(language),
        changes = preview?.changes?.map { it.presentation(language) }.orEmpty(),
        liabilities = listOf(omen.liability.localizedContent(language)),
        forecasts = preview?.forecasts.orEmpty(),
        icon = omen.id.icon(keynoteTag),
        bandStart = band,
        chips = listOfNotNull(mutator?.let { RewardChip(RewardChipKind.MUTATOR, it.displayName.localizedContent(language), mutator = it) }),
    )
}

/** How a signal card reads: its title, what it gives, what it costs (a price, a stake, a curse) and its price chip. */
private class SignalCardCopy(val title: RunVarietyText, val effect: String, val cost: String?, val chip: RewardChip?)

/**
 * An event signal card: the option's title and what it gives, its cost or curse as the cost line, a chip with the price,
 * the stake or the integrity it takes (the nucleus numbers), the kind on the band and the option's mark. A Shrine card
 * shows the stats its blessing leaves (the nucleus previews the deterministic part only). A card the run cannot take
 * now is [RewardCardPresentation.locked]; Ignore reads as a decline.
 */
internal fun GameplayRenderModel.rewardSignalCard(choice: ChoiceOption, index: Int, language: AppLanguage): RewardCardPresentation {
    val option = choice.signalOption ?: SignalOption.IGNORE
    val kind = (choice.signalKind ?: option.kind)?.displayName?.localizedContent(language).orEmpty()
    val copy = signalCardCopy(option, choice.signalCost, choice.signalPayout, language)
    if (option == SignalOption.IGNORE) {
        return RewardCardPresentation(
            choice = choice,
            accent = Kk.Mute2,
            tag = kind,
            descriptions = listOf(copy.effect),
            operation = "",
            title = language.text(copy.title),
            icon = KkIcon.SYSTEM_DASH,
            bandStart = kind,
        )
    }
    val preview = rewardPreviewOf(choice, index)
    return RewardCardPresentation(
        choice = choice,
        accent = Kk.Bone2,
        tag = kind,
        descriptions = listOf(copy.effect),
        operation = "",
        title = language.text(copy.title),
        changes = preview?.changes?.map { it.presentation(language) }.orEmpty(),
        liabilities = listOfNotNull(copy.cost),
        forecasts = preview?.forecasts.orEmpty(),
        icon = option.icon(),
        bandStart = kind,
        chips = listOfNotNull(copy.chip),
        locked = choice.locked,
    )
}

/** The copy of [option] with the nucleus [cost] and [payout] (their meaning is the option's, see the signal system). */
private fun signalCardCopy(option: SignalOption, cost: Int, payout: Int, language: AppLanguage): SignalCardCopy {
    fun text(resource: RunVarietyText, vararg arguments: Any) = language.text(resource, *arguments)
    fun offer(rarity: ItemRarity) = text(RunVarietyText.OfferAtLeast, rarity.displayLabel.localizedContent(language))
    val matter = overlayGrouped(cost.toLong(), language)
    val matterChip = RewardChip(RewardChipKind.COST, matter, icon = KkIcon.SYSTEM_MATTER)
    val integrityChip = RewardChip(RewardChipKind.COST, cost.toString(), icon = KkIcon.SYSTEM_INTEGRITY)
    return when (option) {
        SignalOption.MERCHANT_RARE_OFFER ->
            SignalCardCopy(RunVarietyText.MerchantRareTitle, offer(ItemRarity.RARE), text(RunVarietyText.PriceMatter, matter), matterChip)
        SignalOption.MERCHANT_EPIC_OFFER ->
            SignalCardCopy(RunVarietyText.MerchantEpicTitle, offer(ItemRarity.EPIC), text(RunVarietyText.PriceMatter, matter), matterChip)
        SignalOption.MERCHANT_REROLLS -> SignalCardCopy(RunVarietyText.MerchantRerollsTitle, text(RunVarietyText.MerchantRerollsText, payout),
            text(RunVarietyText.PriceMatter, matter), matterChip)
        SignalOption.SHRINE_BLOOD -> SignalCardCopy(RunVarietyText.ShrineBloodTitle, text(RunVarietyText.ShrineBloodText),
            text(RunVarietyText.CostIntegrity, cost), integrityChip)
        SignalOption.SHRINE_WARD -> SignalCardCopy(RunVarietyText.ShrineWardTitle, text(RunVarietyText.ShrineWardText),
            text(RunVarietyText.ShrineWardCost), integrityChip)
        SignalOption.SHRINE_RELIC -> SignalCardCopy(RunVarietyText.ShrineRelicTitle, text(RunVarietyText.RelicOffer),
            text(RunVarietyText.CostIntegrity, cost), integrityChip)
        SignalOption.GAMBLE_REROLL -> SignalCardCopy(RunVarietyText.GambleRerollTitle, text(RunVarietyText.GambleRerollText),
            text(RunVarietyText.GambleRerollStake), RewardChip(RewardChipKind.COST, cost.toString(), icon = KkIcon.SYSTEM_REROLL))
        SignalOption.GAMBLE_MATTER -> SignalCardCopy(RunVarietyText.GambleMatterTitle,
            text(RunVarietyText.GambleMatterText, overlayGrouped(payout.toLong(), language)), text(RunVarietyText.GambleMatterStake, matter), matterChip)
        SignalOption.GAMBLE_INTEGRITY -> SignalCardCopy(RunVarietyText.GambleIntegrityTitle, text(RunVarietyText.GambleIntegrityText),
            text(RunVarietyText.GambleIntegrityStake, cost), integrityChip)
        SignalOption.CACHE_CRATE -> SignalCardCopy(RunVarietyText.CacheCrateTitle, offer(ItemRarity.EPIC), text(RunVarietyText.CacheCrateCurse), null)
        SignalOption.CACHE_RELIQUARY ->
            SignalCardCopy(RunVarietyText.CacheReliquaryTitle, text(RunVarietyText.OfferLegendary), text(RunVarietyText.CacheReliquaryCurse), null)
        SignalOption.CACHE_IDOL -> SignalCardCopy(RunVarietyText.CacheIdolTitle, text(RunVarietyText.RelicOffer), text(RunVarietyText.CacheIdolCurse), null)
        SignalOption.DERELICT_REPAIR -> SignalCardCopy(RunVarietyText.DerelictRepairTitle, text(RunVarietyText.DerelictRepairText, payout), null, null)
        SignalOption.DERELICT_CARGO ->
            SignalCardCopy(RunVarietyText.DerelictCargoTitle, text(RunVarietyText.DerelictCargoText, overlayGrouped(payout.toLong(), language)), null, null)
        SignalOption.DERELICT_VAULT -> SignalCardCopy(RunVarietyText.DerelictVaultTitle, text(RunVarietyText.DerelictVaultText), null, null)
        SignalOption.IGNORE -> SignalCardCopy(RunVarietyText.Ignore, text(RunVarietyText.IgnoreText), null, null)
    }
}

/** A signal option's card mark. */
internal fun SignalOption.icon(): KkIcon = when (this) {
    SignalOption.MERCHANT_RARE_OFFER, SignalOption.MERCHANT_EPIC_OFFER -> KkIcon.SYSTEM_MATTER
    SignalOption.MERCHANT_REROLLS -> KkIcon.SYSTEM_REROLL
    SignalOption.SHRINE_BLOOD -> KkIcon.SYSTEM_INTEGRITY
    SignalOption.SHRINE_WARD -> KkIcon.SYSTEM_SHIELD
    SignalOption.SHRINE_RELIC -> KkIcon.ASPECT_SOVEREIGN
    SignalOption.GAMBLE_REROLL -> KkIcon.SYSTEM_REROLL
    SignalOption.GAMBLE_MATTER -> KkIcon.SYSTEM_MATTER
    SignalOption.GAMBLE_INTEGRITY -> KkIcon.SYSTEM_INTEGRITY
    SignalOption.CACHE_CRATE, SignalOption.CACHE_RELIQUARY, SignalOption.CACHE_IDOL -> KkIcon.SYSTEM_LOCKED
    SignalOption.DERELICT_REPAIR -> KkIcon.SYSTEM_INTEGRITY
    SignalOption.DERELICT_CARGO -> KkIcon.SYSTEM_MATTER
    SignalOption.DERELICT_VAULT -> KkIcon.SYSTEM_KEY
    SignalOption.IGNORE -> KkIcon.SYSTEM_DASH
}

/** The heading of an omen offer or a signal prompt (the signal's kind); null for every other choice. */
internal fun GameplayRenderModel.rewardRunVarietyHeading(language: AppLanguage): String? = when (choiceType) {
    ChoiceType.OMEN -> language.text(RunVarietyText.ChooseOmen)
    ChoiceType.SIGNAL -> choices.firstNotNullOfOrNull { it.signalKind }?.displayName?.localizedContent(language)
    else -> null
}

/** The card a deal lifts first: the middle one, or the nearest card the run can take when the middle one is locked. */
internal fun rewardDefaultCard(cards: List<RewardCardPresentation>): Int? {
    if (cards.isEmpty()) return null
    val middle = (cards.size - 1) / 2
    return cards.indices.filter { !cards[it].locked }.minByOrNull { kotlin.math.abs(it - middle) * 2 + if (it < middle) 1 else 0 } ?: middle
}

/**
 * The locked look of a card the run cannot take now (HATCH = locked): its face dimmed under a bone hatch at 0.35, inside
 * the card's cut outline, and it never takes keyboard focus. No text says why (the cost line already names the price).
 */
internal fun Modifier.rewardLockedLook(locked: Boolean): Modifier {
    if (!locked) return this
    return focusProperties { canFocus = false }.drawWithCache {
        val paths = KkPathCache(1)
        val outline = paths.slab(0, Rect(0f, 0f, size.width, size.height), RewardCardCutDp.dp.toPx())
        onDrawWithContent {
            drawContent()
            drawPath(outline, LockedDimColor)
            // The shared tile cache: allocation-free once the bone hatch exists at this density.
            drawPath(outline, kkFillBrush(KkFillKind.HATCH, Kk.Bone), alpha = RewardLockedHatch)
        }
    }
}

/** How far a locked card's face is dimmed, and the hatch over it. */
internal const val RewardLockedDim = 0.45f
internal const val RewardLockedHatch = 0.35f
private val LockedDimColor = Kk.Ink.copy(alpha = RewardLockedDim)

/**
 * The reward heading with an omen offer's run conditions: under the heading on the board and on a portrait phone,
 * beside it on a landscape phone (whose cards start right under the heading). Without conditions it is the heading alone.
 */
@Composable
internal fun RewardHeadingFrame(
    conditions: RewardRunConditions?,
    textScale: Float,
    beside: Boolean,
    modifier: Modifier,
    heading: @Composable BoxScope.() -> Unit,
) {
    if (conditions == null) {
        Box(modifier, content = heading)
        return
    }
    val chips: @Composable () -> Unit = {
        RewardChipFlow(
            conditions.chips, if (beside) RewardChipCompact else RewardChipRegular, textScale,
            Modifier.widthIn(max = RewardConditionsMaxWidthDp.dp)
                .semantics { contentDescription = conditions.description }
                .testTag("kinetickk.gameplay.rewards.conditions"),
            gapDp = 8f,
        )
    }
    if (beside) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(content = heading)
            chips()
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(content = heading)
            chips()
        }
    }
}

/** The widest the conditions row grows (two chips at the largest text size). */
private const val RewardConditionsMaxWidthDp = 520f

/** A mutator's sigil (the HUD's Rebirth strip geometry) centred on [center] within [radius], in [color]. Allocation-free. */
internal fun DrawScope.drawRewardMutatorSigil(mutator: RebirthMutator, center: Offset, radius: Float, color: Color) {
    val path = KkMutatorSigilPaths.of(mutator.ordinal, radius)
    translate(center.x, center.y) { drawPath(path, color) }
}
