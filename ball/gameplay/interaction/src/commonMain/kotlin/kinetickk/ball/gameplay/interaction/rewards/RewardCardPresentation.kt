// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RewardFocus
import kinetickk.ball.content.api.StatDelta
import kinetickk.ball.content.api.RuleLineKind
import kinetickk.ball.content.api.liabilityText
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.content.api.upsideText
import kinetickk.ball.content.api.WeaponDefinition
import androidx.compose.ui.graphics.Color
import kinetickk.ball.gameplay.interaction.canvas.look.overlayColor
import kinetickk.ball.gameplay.interaction.canvas.look.overlayIcon
import kinetickk.ball.gameplay.interaction.canvas.look.overlayLevel
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.ForecastKind
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.render.RewardPreview
import kinetickk.ball.gameplay.nucleus.render.TotemAction
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.label
import kinetickk.ball.gameplay.interaction.canvas.look.*

internal fun GameplayRenderModel.rewardCardPresentation(
    choice: ChoiceOption,
    index: Int,
    language: AppLanguage = AppLanguage.English,
    touch: Boolean = false,
): RewardCardPresentation {
    if (choice.type == ChoiceType.ACTIVE_SLOT) return rewardActiveSlotCard(choice, index, language, touch)
    if (choice.type == ChoiceType.OMEN) return rewardOmenCard(choice, index, language)
    if (choice.type == ChoiceType.SIGNAL) return rewardSignalCard(choice, index, language)
    if (choice.type == ChoiceType.BOON) return rewardBoonCard(choice, index, language)
    val item = choice.itemId?.let(content::item)
    val weapon = choice.weaponId?.let(content::weapon)
    val slotRelic = choice.relicSlot?.let(equippedRelics::getOrNull)
    val optionRelicId = choice.relicId ?: slotRelic?.id
    val displayedRelicId = if (choice.relicAction == RelicChoiceAction.REPLACE) {
        slotRelic?.id ?: optionRelicId
    } else optionRelicId
    val relic = displayedRelicId?.let(content::relic)
    val incoming = choice.relicId?.takeIf { choice.relicAction == RelicChoiceAction.REPLACE }?.let(content::relic)
    val ownedRank = optionRelicId?.let(::relicRank) ?: 0
    val policy = content.relicPolicy
    val preview = rewardPreviews.getOrNull(index)?.takeIf { choices.getOrNull(index) == choice }
    val rank = when (choice.relicAction) {
        RelicChoiceAction.ACQUIRE -> (ownedRank + 1).coerceIn(1, policy.maxRank)
        RelicChoiceAction.REPLACE -> slotRelic?.rank
        RelicChoiceAction.MELD_TARGET -> slotRelic?.rank?.plus(1)?.coerceAtMost(policy.maxRank)
        RelicChoiceAction.MELD, RelicChoiceAction.DECLINE, null -> null
    }
    val choiceTag = choice.tag.localizedContent(language)
    val focus = choice.rewardFocus
    val action = choice.relicAction?.let { relicAction ->
        rewardRelicAction(relicAction, ownedRank, slotRelic?.rank, equippedRelics.size, policy, language)
    }
    val suppressedNow = item != null && (item.id in build.suppressedItemIds ||
        preview?.forecasts?.any { it.kind == ForecastKind.SUPPRESSED } == true)
    // The liabilities voiding parts of a suppressed item (only read when the nucleus reports it).
    val liable = if (suppressedNow) liableRelics() else emptySet()
    val voidedStats = if (item != null && liable.isNotEmpty()) item.stats.filter { suppressor(it, liable) != null } else emptyList()
    val previewChanges = rewardPreviewChanges(choice, preview, displayedRelicId, voidedStats, language)
    val itemStackAfter = item?.let { (itemStack(it.id) + 1).coerceAtMost(it.maxStacks) } ?: 0
    val reborn = item != null && (choice.reborn || item.id in build.rebornItemIds)
    val itemEffects = rewardItemEffects(item, preview, itemStackAfter, reborn, voidedStats, language)
    val passiveRules = item?.let { rewardItemRules(it, reborn, itemStackAfter, liable, language) }.orEmpty()
    // An active item leads with its active part (the effect at the rank it reaches), then labels its passive.
    val itemRules = if (item?.active != null) rewardActiveRules(item, itemStackAfter, language) + passiveRules else passiveRules
    val decline = choice.relicAction == RelicChoiceAction.DECLINE
    // The band already names the resulting rank; cards keep their lines for the relic's effects.
    val descriptions = rewardDescriptions(choice, focus, item, itemRules, preview, relic, rank, ownedRank, decline, weapon, language)
    val relicAspect = relic?.aspect
    val (accent, tone) = rewardCardAccent(choice, item, relicAspect, focus, weapon, decline)
    val relicPreview = if (choice.relicAction != null) rewardRelicPreview(choice, preview, language) else null
    val heirloom = choice.heirloom && relic != null
    return RewardCardPresentation(
        choice = choice,
        title = when {
            decline -> language.text(if (choice.heirloom) OverlayRedesignText.LeaveHeirloom else OverlayRedesignText.LeaveHollow)
            else -> choice.title.localizedContent(language)
        },
        accent = accent,
        tone = tone,
        tag = choiceTag,
        descriptions = descriptions,
        operation = action?.label.orEmpty(),
        item = item,
        itemStack = item?.let { itemStack(it.id) + 1 },
        weapon = weapon,
        relic = relic,
        incomingRelic = incoming,
        relicRank = rank,
        relicPolicy = policy,
        changes = itemEffects + previewChanges,
        liabilities = rewardCardLiabilities(relic, item, itemRules, language),
        forecasts = preview?.forecasts.orEmpty(),
        connections = rewardConnections(choice, language),
        isNewDiscovery = when {
            item != null -> !isItemDiscovered(item.id)
            else -> choice.relicId?.let { !isRelicDiscovered(it) } ?: false
        },
        rank = rewardCardRank(item, relic),
        icon = rewardCardIcon(choice, item, relicAspect, focus, weapon, decline),
        bandStart = rewardBandStart(choice, item, relicAspect, weapon, decline, choiceTag, language),
        bandEnd = rewardBandEnd(item, itemStackAfter, relic, rank, weapon, language),
        family = weapon?.let { rewardWeaponFamily(it, language) },
        tags = rewardCardTags(choice, item, weapon, language),
        action = action,
        relicPreview = relicPreview,
        totemRow = if (choiceType == ChoiceType.TOTEM) rewardTotemRow(choice, weapon, descriptions.firstOrNull().orEmpty(), language) else null,
        elements = item?.elements ?: weapon?.element?.let(::listOf).orEmpty(),
        chips = rewardCardChips(choice, item, relic, heirloom, weapon, touch, language),
        forecast = rewardCardForecast(item, preview, relic, relicPreview, language),
        rules = itemRules,
        values = if (relic != null) relic.upsideValueRanges(language, rank ?: ownedRank.coerceAtLeast(1)) else emptyList(),
        keys = if (relic != null) rewardRelicKeys(relic, equippedRelics.map { it.id }.filter {
            choice.relicAction != RelicChoiceAction.REPLACE || it != slotRelic?.id
        }, language) else emptyList(),
        rebirthTier = rebirthLevel,
    )
}

/** An item offer's own stat lines (without a preview): the next copy's marginal share of the stack magnitude. */
private fun GameplayRenderModel.rewardItemEffects(
    item: ItemDefinition?,
    preview: RewardPreview?,
    itemStackAfter: Int,
    reborn: Boolean,
    voidedStats: List<StatDelta>,
    language: AppLanguage,
): List<RewardStatPresentation> {
    val stackPolicy = content.build.policy
    return if (item != null && preview == null) {
        // The next copy adds its marginal share of the diminishing stack magnitude. Negative
        // amounts are the item's costs: they read as cost lines with the rules, never as stats.
        val marginal = stackPolicy.stackMagnitude(itemStackAfter) - stackPolicy.stackMagnitude(itemStackAfter - 1)
        val scale = marginal * if (reborn) stackPolicy.rebornMagnitude else 1f
        item.stats.filter { it.amount > 0f }.map { stat -> stat.presentation(scale, numberScale, language).copy(suppressed = stat in voidedStats) }
    } else emptyList()
}

/** A preview's stat changes, each naming the relic it comes from (unless it is the offered one) and whether it is voided. */
private fun GameplayRenderModel.rewardPreviewChanges(
    choice: ChoiceOption,
    preview: RewardPreview?,
    displayedRelicId: RelicId?,
    voidedStats: List<StatDelta>,
    language: AppLanguage,
): List<RewardStatPresentation> = preview?.changes?.map { change ->
    change.presentation(language).copy(
        source = change.sourceRelic?.takeIf {
            it != displayedRelicId || choice.relicAction == RelicChoiceAction.REPLACE
        }?.let { content.relic(it).name.localizedContent(language) },
        suppressed = voidedStats.any { it.effect.previewStatName() == change.name },
    )
}.orEmpty()

/** The card's accent and whether it is fixed or the palette's `you` color. */
private fun rewardCardAccent(
    choice: ChoiceOption,
    item: ItemDefinition?,
    relicAspect: RelicAspect?,
    focus: RewardFocus?,
    weapon: WeaponDefinition?,
    decline: Boolean,
): Pair<Color, RewardTone> = when {
    relicAspect != null -> relicAspect.overlayColor() to RewardTone.FIXED
    item != null -> Kk.rarity(item.rarity.rank) to RewardTone.FIXED
    focus != null -> (focus.relicAspect()?.overlayColor() ?: Kk.EKinetic) to RewardTone.FIXED
    // Weapons are the player's own system: the palette's `you` color, resolved when drawn.
    weapon != null || choice.type == ChoiceType.TOTEM -> Kk.Bone to RewardTone.YOU
    decline -> Kk.Mute2 to RewardTone.FIXED
    choice.relicAction == RelicChoiceAction.MELD -> Kk.EKinetic to RewardTone.FIXED
    else -> Kk.Bone to RewardTone.FIXED
}

/** The card's lines: the focus effect, the item's rules, the relic's upside, or the leave, weapon or meld effect. */
private fun GameplayRenderModel.rewardDescriptions(
    choice: ChoiceOption,
    focus: RewardFocus?,
    item: ItemDefinition?,
    itemRules: List<RewardRuleLine>,
    preview: RewardPreview?,
    relic: RelicDefinition?,
    rank: Int?,
    ownedRank: Int,
    decline: Boolean,
    weapon: WeaponDefinition?,
    language: AppLanguage,
): List<String> = buildList {
    // Flavor and generated catalog paragraphs belong in the Codex. Offers show effects only;
    // the game's step-by-step choice descriptions ("Choose…, then…") are never shown.
    when {
        focus != null -> add(language.text(rewardFocusEffect(choice.type)))
        item != null -> {
            addAll(itemRules.filter { it.kind != RuleLineKind.COST }.map { it.text })
            if (preview != null && preview.changes.isEmpty() && itemRules.isEmpty()) add(language.text(GameplayText.NoStatChange))
        }
        relic != null -> add(relic.upsideText(language, rank ?: ownedRank.coerceAtLeast(1)))
        decline -> add(if (choice.heirloom) language.text(OverlayRedesignText.LeaveHeirloomEffect, content.relicPolicy.heirloomDeclineRerolls)
            else language.text(OverlayRedesignText.LeaveHollowEffect, content.relicPolicy.salvageMatter))
        choice.totemAction == TotemAction.AMPLIFY_CURRENT -> add(currentWeaponDefinition.description.localizedContent(language))
        weapon != null -> add(weapon.description.localizedContent(language))
        choice.totemAction == TotemAction.CHANGE_WEAPON -> add(language.text(OverlayRedesignText.ChangeWeaponEffect))
        choice.relicAction == RelicChoiceAction.MELD -> add(language.text(OverlayRedesignText.MeldEffect))
    }
}.filter(String::isNotBlank).distinct()

/** The card's head icon (none for an item, whose art is the icon). */
private fun GameplayRenderModel.rewardCardIcon(
    choice: ChoiceOption,
    item: ItemDefinition?,
    relicAspect: RelicAspect?,
    focus: RewardFocus?,
    weapon: WeaponDefinition?,
    decline: Boolean,
): KkIcon? = when {
    item != null -> null
    relicAspect != null -> relicAspect.overlayIcon()
    focus != null -> focus.overlayIcon()
    weapon != null -> weapon.id.overlayIcon()
    choice.totemAction == TotemAction.AMPLIFY_CURRENT -> this.weapon.overlayIcon()
    choice.totemAction == TotemAction.CHANGE_WEAPON -> KkIcon.SYSTEM_REROLL
    choice.relicAction == RelicChoiceAction.MELD -> KkIcon.UI_PLUS
    decline && choice.heirloom -> KkIcon.SYSTEM_REROLL
    decline -> KkIcon.SYSTEM_MATTER
    else -> KkIcon.SYSTEM_DATA
}

/** The card's chips: an item's slot, lane, risk, Reborn and tags; a relic's heirloom and attunements; a weapon's. */
private fun GameplayRenderModel.rewardCardChips(
    choice: ChoiceOption,
    item: ItemDefinition?,
    relic: RelicDefinition?,
    heirloom: Boolean,
    weapon: WeaponDefinition?,
    touch: Boolean,
    language: AppLanguage,
): List<RewardChip> = when {
    item != null -> buildList {
        // An active item first names the slot it takes (or that it replaces one) and the character whose lane it is in.
        item.active?.let { effect ->
            add(rewardActiveChip(choice.activeSlot, choice.replacesActive, touch, language))
            rewardLaneChip(effect, language)?.let(::add)
        }
        if (item.risk) add(RewardChip(RewardChipKind.RISK, language.text(OverlayRedesignText.Risk)))
        if (choice.reborn) add(RewardChip(RewardChipKind.REBORN, language.text(OverlayRedesignText.Reborn), after = rebirthLevel))
        item.tags.forEach { tag ->
            add(RewardChip(RewardChipKind.TAG, tag.displayLabel.localizedContent(language), tag = tag, active = build.tagPoints(tag) > 0))
        }
    }
    relic != null -> buildList {
        if (heirloom) add(RewardChip(RewardChipKind.HEIRLOOM, language.text(OverlayRedesignText.Heirloom), after = rebirthLevel))
        addAll(rewardAttunedChips(relic, language))
    }
    weapon != null -> rewardWeaponChips(weapon, language)
    else -> emptyList()
}


/** The card's costs: a relic's liability, or an item's cost lines. */
private fun GameplayRenderModel.rewardCardLiabilities(relic: RelicDefinition?, item: ItemDefinition?, itemRules: List<RewardRuleLine>, language: AppLanguage): List<String> = when {
    relic != null -> listOf(relic.liabilityText(language))
    item != null -> itemRules.filter { it.kind == RuleLineKind.COST }.map { it.text }
    else -> emptyList()
}

/** The card's frame rank: an item's rarity, the one relic rank, or the plain rank. */
private fun GameplayRenderModel.rewardCardRank(item: ItemDefinition?, relic: RelicDefinition?): Int = when {
    item != null -> item.rarity.rank
    // Every relic takes the one relic rank (its chrome material), whatever its aspect: a
    // Sovereign's gold is its type and stays on its face, never the legendary frame or name. An
    // heirloom keeps it too: gold (rim, name, chip) carries the Rebirth, never the epic glow.
    relic != null -> 3
    else -> 1
}

/** The band's start: rarity, aspect, the weapon's first tag, the relic shelf or the choice's tag. */
private fun GameplayRenderModel.rewardBandStart(choice: ChoiceOption, item: ItemDefinition?, relicAspect: RelicAspect?, weapon: WeaponDefinition?, decline: Boolean, choiceTag: String, language: AppLanguage): String = when {
    item != null -> item.rarity.displayLabel.localizedContent(language)
    relicAspect != null -> relicAspect.displayLabel.localizedContent(language)
    weapon != null -> weapon.tags.first().displayLabel.localizedContent(language)
    decline -> language.text(if (choice.heirloom) OverlayRedesignText.Heirloom else OverlayRedesignText.Relics)
    else -> choiceTag
}

/** The band's end: the stack or active rank reached, the relic rank or the weapon level. */
private fun GameplayRenderModel.rewardBandEnd(item: ItemDefinition?, itemStackAfter: Int, relic: RelicDefinition?, rank: Int?, weapon: WeaponDefinition?, language: AppLanguage): String? = when {
    item?.active != null -> language.text(OverlayRedesignText.ActiveRank, itemStackAfter, item.maxStacks)
    item != null -> language.text(OverlayRedesignText.StackCount, itemStackAfter, item.maxStacks)
    relic != null && rank != null -> language.text(OverlayRedesignText.Rank, rank)
    weapon != null -> overlayLevel(weaponLevel, language)
    else -> null
}

/** The card's tag words: a weapon's tags, or an item's tags with its risk and Reborn marks. */
private fun GameplayRenderModel.rewardCardTags(choice: ChoiceOption, item: ItemDefinition?, weapon: WeaponDefinition?, language: AppLanguage): List<String> = when {
    weapon != null -> weapon.tags.map { it.displayLabel.localizedContent(language) }
    item != null -> buildList {
        item.tags.forEach { add(it.displayLabel.localizedContent(language)) }
        if (item.risk) add(language.text(OverlayRedesignText.Risk))
        if (choice.reborn) add(language.text(OverlayRedesignText.Reborn))
    }
    else -> emptyList()
}

/** The forecast chips of an item or a relic offer. */
private fun GameplayRenderModel.rewardCardForecast(item: ItemDefinition?, preview: RewardPreview?, relic: RelicDefinition?, relicPreview: RewardRelicPreview?, language: AppLanguage): List<RewardChip> = when {
    item != null -> rewardForecastChips(preview?.forecasts.orEmpty(), suppressor(item, liableRelics()), language)
    relic != null && relicPreview != null -> rewardRelicForecastChips(preview?.forecasts.orEmpty(), relicPreview, language)
    else -> emptyList()
}


/** Relics whose liabilities apply now: bound ones and those severed in the last seconds. */
internal fun GameplayRenderModel.liableRelics(): Set<RelicId> =
    equippedRelics.mapTo(HashSet()) { it.id }.also { liable -> build.severed.forEach { liable += it.id } }
