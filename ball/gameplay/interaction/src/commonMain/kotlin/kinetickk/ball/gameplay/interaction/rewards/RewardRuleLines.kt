// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.ConversionKind
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RuleLineKind
import kinetickk.ball.content.api.RuleTextContext
import kinetickk.ball.content.api.SkillDefinition
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatDelta
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WEAPON_BASIS_UNIT_DAMAGE
import kinetickk.ball.content.api.flatDamageScale
import kinetickk.ball.content.api.rankLines
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.content.api.ruleLines
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkIcon
import kotlin.math.sqrt
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.ball.gameplay.interaction.overlay.*

/**
 * The item's clauses and rules as lines for the next copy: the stack magnitude and bonus it will
 * have and the build's actual values (dominant element, run scale, weapon power, shield). Positive
 * passive stats are the card's stat lines and are left out; negative ones are costs and join the
 * cost lines, so a Risk item always shows what it takes. [liable] holds the relics whose
 * liabilities apply when the nucleus reports the item suppressed; the lines they void are marked.
 */
internal fun GameplayRenderModel.rewardItemRules(
    item: ItemDefinition,
    reborn: Boolean,
    stacks: Int,
    liable: Set<RelicId>,
    language: AppLanguage,
): List<RewardRuleLine> {
    val policy = content.build.policy
    val attuned = equippedRelics.any { bound -> content.relic(bound.id).attunedTags.any { it in item.tags } }
    val runScale = content.tempo.flatDamageScale(elapsed, content.rebirth.profile(rebirthLevel))
    val shield = maxShield * sqrt(runScale)
    val context = RuleTextContext(
        stackMagnitude = policy.stackMagnitude(stacks.coerceAtLeast(1)),
        dominant = build.dominant,
        secondary = build.secondary,
        flatScale = runScale,
        weaponUnit = WEAPON_BASIS_UNIT_DAMAGE * abilityPower,
        shieldCapacity = shield.takeIf { it > 0f },
        bonus = (if (reborn) policy.rebornMagnitude else 1f) * (if (attuned) 1f + policy.attunementBonus else 1f),
        stacks = stacks.coerceIn(1, ContentBounds.MAX_ITEM_STACKS),
        numberScale = numberScale,
    )
    val empty = immutableListOf<StatDelta>()
    val lines = ArrayList<RewardRuleLine>()
    // Each stat cost, clause and rule is written on its own so every line knows the part it came from.
    item.stats.filter { it.amount < 0f }.forEach { stat ->
        val void = suppressor(stat, liable) != null
        item.copy(stats = immutableListOf(stat), clauses = immutableListOf(), rules = immutableListOf(), risk = false)
            .ruleLines(language, context).forEach { lines += RewardRuleLine(it.kind, it.text, it.element, void) }
    }
    item.clauses.forEach { clause ->
        val void = clause.suppressor(liable) != null
        item.copy(stats = empty, clauses = immutableListOf(clause), rules = immutableListOf(), risk = false)
            .ruleLines(language, context).forEach { lines += RewardRuleLine(it.kind, it.text, it.element, void) }
    }
    item.rules.forEach { rule ->
        val void = rule.suppressor(liable) != null
        item.copy(stats = empty, clauses = immutableListOf(), rules = immutableListOf(rule), risk = false)
            .ruleLines(language, context).forEach { lines += RewardRuleLine(it.kind, it.text, it.element, void) }
    }
    return lines.filter { it.kind != RuleLineKind.COST } + lines.filter { it.kind == RuleLineKind.COST }
}

/**
 * The active part of an active item card at the [rank] its effect reaches: the Active header, what the effect does,
 * the rank's numbers and, at rank III, what rank III adds; then the Passive header of the passive lines that follow.
 * Read from content (the slot it takes is the card's ACTIVE chip, [rewardActiveChip]).
 */
internal fun GameplayRenderModel.rewardActiveRules(item: ItemDefinition, rank: Int, language: AppLanguage): List<RewardRuleLine> {
    val skill = content.skill(item.active ?: return emptyList())
    val element = skill.element
    return buildList {
        add(RewardRuleLine(RuleLineKind.TRIGGER, language.text(OverlayRedesignText.ActivePart), element))
        add(RewardRuleLine(RuleLineKind.EFFECT, skill.summary.localizedContent(language), element))
        add(RewardRuleLine(RuleLineKind.RULE, skill.activeRankNumbers(rank, language, numberScale), element))
        if (rank >= ContentBounds.MAX_SKILL_RANK) {
            // Its numbers keep the word before them, as the numbers line does, so a wrap never leaves "3 s." alone.
            add(RewardRuleLine(RuleLineKind.RULE, skill.rankThree.localizedContent(language).replace(SpaceBeforeNumber, "\u00A0"), element))
        }
        add(RewardRuleLine(RuleLineKind.TRIGGER, language.text(OverlayRedesignText.PassivePart)))
    }
}

/**
 * The ACTIVE chip of a card about Z/X/C [slot] (0 Z, 1 X, 2 C): the player's key for it as a key cap after the label,
 * or the slot's number when the input is [touch]; [replaces] (an unowned active while every slot holds another) says
 * taking it replaces a slot instead. A card that names no slot reads Active alone.
 */
internal fun GameplayRenderModel.rewardActiveChip(slot: Int?, replaces: Boolean, touch: Boolean, language: AppLanguage): RewardChip = when {
    replaces -> RewardChip(RewardChipKind.REPLACES_ACTIVE, language.text(OverlayRedesignText.ActiveReplacesChip))
    slot == null -> RewardChip(RewardChipKind.ACTIVE, language.text(OverlayRedesignText.ActiveChip))
    touch -> RewardChip(RewardChipKind.ACTIVE, language.text(OverlayRedesignText.ActiveSlotChip, slot + 1))
    else -> RewardChip(RewardChipKind.ACTIVE, language.text(OverlayRedesignText.ActiveChip), keyCap = activeSlotKey(slot, language))
}

/** The run character's signature chip, naming the character, when [effect] is in its active lane; else null. */
internal fun GameplayRenderModel.rewardLaneChip(effect: SkillId, language: AppLanguage): RewardChip? {
    val character = content.coreShape(coreShape)
    if (effect !in character.activeLane) return null
    return RewardChip(RewardChipKind.SIGNATURE, character.displayName.localizedContent(language), core = coreShape)
}

/**
 * A card of the active slot prompt ([ChoiceType.ACTIVE_SLOT]), built from its structured fields: a replace card is the
 * held active item's card (its material, element hue, glyph and rank, the slot's ACTIVE chip and its lane chip, what
 * its effect does at that rank), then the incoming item taking the slot at rank I and the replacement's preview; the
 * keep card reads as a decline that salvages the incoming item for the Matter the nucleus names.
 */
internal fun GameplayRenderModel.rewardActiveSlotCard(
    choice: ChoiceOption,
    index: Int,
    language: AppLanguage,
    touch: Boolean,
): RewardCardPresentation {
    val incoming = choice.incomingItemId?.let(content::item)
    val incomingName = incoming?.name?.localizedContent(language).orEmpty()
    val slot = choice.activeSlot
    val held = choice.itemId?.let(content::item)
    if (slot == null || held == null) {
        val salvage = choice.salvageMatter
        return RewardCardPresentation(
            choice = choice,
            accent = Kk.Mute2,
            tag = "",
            descriptions = listOf(
                if (salvage != null) language.text(OverlayRedesignText.KeepLoadoutMatter, incomingName, salvage)
                else language.text(OverlayRedesignText.KeepLoadoutEffect, incomingName),
            ),
            operation = "",
            title = language.text(OverlayRedesignText.KeepLoadout),
            icon = KkIcon.SYSTEM_MATTER,
            bandStart = language.text(OverlayRedesignText.Actives),
        )
    }
    val preview = rewardPreviews.getOrNull(index)?.takeIf { choices.getOrNull(index) == choice }
    val skill = held.active?.let(content::skill)
    val rank = itemStack(held.id)
    val element = skill?.element
    val rules = buildList {
        add(RewardRuleLine(RuleLineKind.TRIGGER, language.text(OverlayRedesignText.ActivePart), element))
        if (skill != null) {
            add(RewardRuleLine(RuleLineKind.EFFECT, skill.summary.localizedContent(language), element))
            add(RewardRuleLine(RuleLineKind.RULE, skill.activeRankNumbers(rank, language, numberScale), element))
        }
        add(RewardRuleLine(RuleLineKind.RULE, language.text(OverlayRedesignText.ActiveIncoming, incomingName), incoming?.elements?.firstOrNull()))
    }
    return RewardCardPresentation(
        choice = choice,
        accent = Kk.rarity(held.rarity.rank),
        tag = "",
        descriptions = rules.map { it.text },
        operation = "",
        item = held,
        itemStack = rank,
        title = language.text(OverlayRedesignText.ReplaceActive, held.name.localizedContent(language)),
        changes = preview?.changes?.map { it.presentation(language) }.orEmpty(),
        forecasts = preview?.forecasts.orEmpty(),
        rank = held.rarity.rank,
        bandStart = held.rarity.displayLabel.localizedContent(language),
        bandEnd = language.text(OverlayRedesignText.ActiveRank, rank, held.maxStacks),
        elements = held.elements,
        // The forecasts are the incoming item's, so a suppressed chip names the relic voiding part of it.
        forecast = rewardForecastChips(preview?.forecasts.orEmpty(), incoming?.let { suppressor(it, liableRelics()) }, language),
        chips = listOfNotNull(rewardActiveChip(slot, false, touch, language), held.active?.let { rewardLaneChip(it, language) }),
        rules = rules,
    )
}

/**
 * The numbers of an active effect at [rank] as one line, as the reward cards and the Codex write them: its cooldown and
 * values ([SkillDefinition.rankLines] after the rank line, which a card's band already shows), joined by commas since
 * overlay copy uses no separator marks. Each value keeps its label and unit on its line (no-break spaces), so a wrap
 * never leaves a number alone. Damage reads at [numberScale]: the run's on a card, 1 (the tier-0 base) in the Codex.
 */
fun SkillDefinition.activeRankNumbers(rank: Int, language: AppLanguage, numberScale: Double = 1.0): String =
    rankLines(rank, language, numberScale).drop(1).joinToString(", ") { it.replace(' ', '\u00A0') }

/** A space before a number, which a no-break space replaces to keep the number with the word before it. */
private val SpaceBeforeNumber = Regex(" (?=[−+]?\\d)")

/** The active slot prompt's keep card: every held active stays and the incoming active item is salvaged. */
internal val ChoiceOption.keepsActiveLoadout: Boolean get() = type == ChoiceType.ACTIVE_SLOT && activeSlot == null

/**
 * The bound relic whose liability voids this clause, mirroring the nucleus suppression read:
 * Glass Comet voids shields, Verdant Rot voids healing, Absolute Zero voids Thermal conditions.
 */
private fun EffectClause.suppressor(liable: Set<RelicId>): RelicId? = when {
    liable.isEmpty() -> null
    RelicId.GLASS_COMET in liable && trigger == EffectTrigger.SHIELD_BREAK -> RelicId.GLASS_COMET
    RelicId.ABSOLUTE_ZERO in liable && conditions.any { it is EffectCondition.ElementIs && it.element == StatusElement.THERMAL } ->
        RelicId.ABSOLUTE_ZERO
    actions.isNotEmpty() && actions.all { action ->
        (RelicId.GLASS_COMET in liable && action is EffectAction.Shield) || (RelicId.VERDANT_ROT in liable && action is EffectAction.Heal)
    } -> if (actions.any { it is EffectAction.Shield }) RelicId.GLASS_COMET else RelicId.VERDANT_ROT
    else -> null
}

private fun ItemRule.suppressor(liable: Set<RelicId>): RelicId? {
    if (liable.isEmpty()) return null
    val thermal = when (this) {
        is ItemRule.ElementPotency -> element
        is ItemRule.StatusDuration -> element
        is ItemRule.BreakThreshold -> element
        is ItemRule.BreakPower -> element
        is ItemRule.BreakSpread -> element
        else -> null
    } == StatusElement.THERMAL
    return when {
        RelicId.ABSOLUTE_ZERO in liable && thermal -> RelicId.ABSOLUTE_ZERO
        RelicId.GLASS_COMET in liable && this is ItemRule.Convert && kind == ConversionKind.OVERHEAL_TO_SHIELD -> RelicId.GLASS_COMET
        else -> null
    }
}

/**
 * The bound relic whose liability voids this passive stat, mirroring the nucleus: Glass Comet
 * voids shield capacity and damage reduction, Verdant Rot voids regeneration unless an owned item
 * turns regeneration into decay.
 */
internal fun GameplayRenderModel.suppressor(stat: StatDelta, liable: Set<RelicId>): RelicId? = when {
    liable.isEmpty() -> null
    RelicId.GLASS_COMET in liable && (stat.effect == ItemEffect.SHIELD_CAPACITY || stat.effect == ItemEffect.DAMAGE_REDUCTION) ->
        RelicId.GLASS_COMET
    RelicId.VERDANT_ROT in liable && stat.effect == ItemEffect.REGEN && !regenerationDecays() -> RelicId.VERDANT_ROT
    else -> null
}

/** Whether an owned item turns regeneration into decay (Verdant Rot then leaves regeneration alone). */
private fun GameplayRenderModel.regenerationDecays(): Boolean = content.items.any { item ->
    itemStack(item.id) > 0 && item.rules.any { it is ItemRule.Convert && it.kind == ConversionKind.REGENERATION_TO_DECAY }
}

/** The nucleus preview row of a passive stat the suppression mirror can void. */
internal fun ItemEffect.previewStatName(): String? = when (this) {
    ItemEffect.SHIELD_CAPACITY -> "Shield"
    ItemEffect.DAMAGE_REDUCTION -> "Damage reduction"
    ItemEffect.REGEN -> "Regeneration"
    else -> null
}

/** The first bound relic that voids part of [item]. */
internal fun GameplayRenderModel.suppressor(item: ItemDefinition, liable: Set<RelicId>): RelicId? =
    item.stats.firstNotNullOfOrNull { suppressor(it, liable) } ?: item.clauses.firstNotNullOfOrNull { it.suppressor(liable) }
        ?: item.rules.firstNotNullOfOrNull { it.suppressor(liable) }
