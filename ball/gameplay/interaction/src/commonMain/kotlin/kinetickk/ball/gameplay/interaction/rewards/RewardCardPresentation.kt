// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RuleLineKind
import kinetickk.ball.content.api.liabilityText
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.content.api.upsideText
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
    val previewChanges = preview?.changes?.map { change ->
        change.presentation(language).copy(
            source = change.sourceRelic?.takeIf {
                it != displayedRelicId || choice.relicAction == RelicChoiceAction.REPLACE
            }?.let { content.relic(it).name.localizedContent(language) },
            suppressed = voidedStats.any { it.effect.previewStatName() == change.name },
        )
    }.orEmpty()
    val itemStackAfter = item?.let { (itemStack(it.id) + 1).coerceAtMost(it.maxStacks) } ?: 0
    val stackPolicy = content.build.policy
    val reborn = item != null && (choice.reborn || item.id in build.rebornItemIds)
    val itemEffects = if (item != null && preview == null) {
        // The next copy adds its marginal share of the diminishing stack magnitude. Negative
        // amounts are the item's costs: they read as cost lines with the rules, never as stats.
        val marginal = stackPolicy.stackMagnitude(itemStackAfter) - stackPolicy.stackMagnitude(itemStackAfter - 1)
        val scale = marginal * if (reborn) stackPolicy.rebornMagnitude else 1f
        item.stats.filter { it.amount > 0f }.map { stat -> stat.presentation(scale, language).copy(suppressed = stat in voidedStats) }
    } else emptyList()
    val passiveRules = item?.let { rewardItemRules(it, reborn, itemStackAfter, liable, language) }.orEmpty()
    // An active item leads with its active part (the effect at the rank it reaches), then labels its passive.
    val itemRules = if (item?.active != null) rewardActiveRules(item, itemStackAfter, language) + passiveRules else passiveRules
    val decline = choice.relicAction == RelicChoiceAction.DECLINE
    // The band already names the resulting rank; cards keep their lines for the relic's effects.
    val descriptions = buildList {
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
    val relicAspect = relic?.aspect
    val (accent, tone) = when {
        relicAspect != null -> relicAspect.overlayColor() to RewardTone.FIXED
        item != null -> Kk.rarity(item.rarity.rank) to RewardTone.FIXED
        focus != null -> (focus.relicAspect()?.overlayColor() ?: Kk.EKinetic) to RewardTone.FIXED
        // Weapons are the player's own system: the palette's `you` color, resolved when drawn.
        weapon != null || choice.type == ChoiceType.TOTEM -> Kk.Bone to RewardTone.YOU
        decline -> Kk.Mute2 to RewardTone.FIXED
        choice.relicAction == RelicChoiceAction.MELD -> Kk.EKinetic to RewardTone.FIXED
        else -> Kk.Bone to RewardTone.FIXED
    }
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
        liabilities = when {
            relic != null -> listOf(relic.liabilityText(language))
            item != null -> itemRules.filter { it.kind == RuleLineKind.COST }.map { it.text }
            else -> emptyList()
        },
        forecasts = preview?.forecasts.orEmpty(),
        connections = rewardConnections(choice, language),
        isNewDiscovery = when {
            item != null -> !isItemDiscovered(item.id)
            else -> choice.relicId?.let { !isRelicDiscovered(it) } ?: false
        },
        rank = when {
            item != null -> item.rarity.rank
            // Every relic takes the one relic rank (its chrome material), whatever its aspect: a
            // Sovereign's gold is its type and stays on its face, never the legendary frame or name. An
            // heirloom keeps it too: gold (rim, name, chip) carries the Rebirth, never the epic glow.
            relic != null -> 3
            else -> 1
        },
        icon = when {
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
        },
        bandStart = when {
            item != null -> item.rarity.displayLabel.localizedContent(language)
            relicAspect != null -> relicAspect.displayLabel.localizedContent(language)
            weapon != null -> weapon.tags.first().displayLabel.localizedContent(language)
            decline -> language.text(if (choice.heirloom) OverlayRedesignText.Heirloom else OverlayRedesignText.Relics)
            else -> choiceTag
        },
        bandEnd = when {
            item?.active != null -> language.text(OverlayRedesignText.ActiveRank, itemStackAfter, item.maxStacks)
            item != null -> language.text(OverlayRedesignText.StackCount, itemStackAfter, item.maxStacks)
            relic != null && rank != null -> language.text(OverlayRedesignText.Rank, rank)
            weapon != null -> overlayLevel(weaponLevel, language)
            else -> null
        },
        family = weapon?.let { rewardWeaponFamily(it, language) },
        tags = when {
            weapon != null -> weapon.tags.map { it.displayLabel.localizedContent(language) }
            item != null -> buildList {
                item.tags.forEach { add(it.displayLabel.localizedContent(language)) }
                if (item.risk) add(language.text(OverlayRedesignText.Risk))
                if (choice.reborn) add(language.text(OverlayRedesignText.Reborn))
            }
            else -> emptyList()
        },
        action = action,
        relicPreview = relicPreview,
        totemRow = if (choiceType == ChoiceType.TOTEM) rewardTotemRow(choice, weapon, descriptions.firstOrNull().orEmpty(), language) else null,
        elements = item?.elements ?: weapon?.element?.let(::listOf).orEmpty(),
        chips = when {
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
        },
        forecast = when {
            item != null -> rewardForecastChips(preview?.forecasts.orEmpty(), suppressor(item, liableRelics()), language)
            relic != null && relicPreview != null -> rewardRelicForecastChips(preview?.forecasts.orEmpty(), relicPreview, language)
            else -> emptyList()
        },
        rules = itemRules,
        values = if (relic != null) relic.upsideValueRanges(language, rank ?: ownedRank.coerceAtLeast(1)) else emptyList(),
        keys = if (relic != null) rewardRelicKeys(relic, equippedRelics.map { it.id }.filter {
            choice.relicAction != RelicChoiceAction.REPLACE || it != slotRelic?.id
        }, language) else emptyList(),
        rebirthTier = rebirthLevel,
    )
}

/** Relics whose liabilities apply now: bound ones and those severed in the last seconds. */
internal fun GameplayRenderModel.liableRelics(): Set<RelicId> =
    equippedRelics.mapTo(HashSet()) { it.id }.also { liable -> build.severed.forEach { liable += it.id } }
