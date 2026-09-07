// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.ball.content.api.localizedContent

import androidx.compose.ui.graphics.Color
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ModifierUnit
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.interaction.canvas.rarityColor
import kinetickk.ball.gameplay.interaction.canvas.ParticleColors
import kinetickk.ball.gameplay.interaction.canvas.relicAspectColor
import kinetickk.ball.gameplay.interaction.canvas.weaponColor
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.render.TotemAction
import kinetickk.ball.gameplay.nucleus.render.RewardStatChange
import kinetickk.foundation.design.Gold
import kinetickk.foundation.design.White
import kinetickk.foundation.design.Violet

internal data class RewardPresentation(
    val heading: String,
    val subtitle: String,
    val cards: List<RewardCardPresentation>,
    val titleAccent: Color,
    val rerollAccent: Color,
    val rerollsRemaining: Int,
)

internal fun GameplayRenderModel.rewardPresentation(language: AppLanguage = AppLanguage.English): RewardPresentation = RewardPresentation(
    heading = rewardHeading(choiceType, choices.firstOrNull()?.relicAction, language),
    subtitle = rewardSubtitle(language),
    cards = choices.mapIndexed { index, choice -> rewardCardPresentation(choice, index, language) },
    titleAccent = if (choiceType == ChoiceType.RELIC || choiceType == ChoiceType.RELIC_BIND) Gold else White,
    rerollAccent = if (choiceType == ChoiceType.RELIC) Gold else Violet,
    rerollsRemaining = rerollsRemaining,
)

/** Only presentation data; the existing validator and Gameplay decision still select rewards. */
internal data class RewardCardPresentation(
    val choice: ChoiceOption,
    val accent: Color,
    val tag: String,
    val descriptions: List<String>,
    val operation: String,
    val item: ItemDefinition? = null,
    val itemStack: Int? = null,
    val weapon: WeaponDefinition? = null,
    val relic: RelicDefinition? = null,
    val incomingRelic: RelicDefinition? = null,
    val relicRank: Int? = null,
    val relicPolicy: RelicPolicy? = null,
    val title: String = choice.title,
    val changes: List<RewardStatPresentation> = emptyList(),
    val connections: List<RewardConnection> = emptyList(),
)

internal data class RewardStatPresentation(val name: String, val before: String, val after: String, val improved: Boolean, val source: String? = null)

internal data class RewardConnection(
    val name: String,
    val reason: String,
    val item: ItemDefinition? = null,
    val weapon: WeaponDefinition? = null,
    val relic: RelicDefinition? = null,
    val core: CoreShape? = null,
)

internal fun GameplayRenderModel.rewardCardPresentation(
    choice: ChoiceOption,
    index: Int,
    language: AppLanguage = AppLanguage.English,
): RewardCardPresentation {
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
        RelicChoiceAction.MELD, null -> null
    }
    val slotLabel = choice.relicSlot?.let { language.text(GameplayText.Slot, it + 1) }
    val choiceTag = choice.tag.localizedContent(language)
    val weaponTags = weapon?.tags?.joinToString(" / ") { it.localizedContent(language) }
    val tag = listOfNotNull(
        slotLabel?.takeUnless { choiceTag.contains(it) },
        choiceTag.takeIf(String::isNotBlank),
        weaponTags?.takeUnless { it == choiceTag },
    ).joinToString(" // ")
    val relicAction = choice.relicAction
    val operation = when {
        relicAction != null -> relicRewardOperation(
            relicAction, ownedRank, slotRelic?.rank, choice.relicSlot ?: index, policy.maxRank, language,
        )
        item != null -> language.text(GameplayText.StackChange, itemStack(item.id), (itemStack(item.id) + 1).coerceAtMost(item.maxStacks), item.maxStacks)
        choice.totemAction == TotemAction.AMPLIFY_CURRENT -> language.text(GameplayText.CurrentWeaponLevel, weaponLevel, weaponLevel + 1)
        choice.type == ChoiceType.TOTEM -> language.text(GameplayText.WeaponPicker)
        weapon != null -> language.text(GameplayText.RunWeapon, requireNotNull(weaponTags))
        else -> choiceTag
    }
    return RewardCardPresentation(
        choice = choice,
        title = choice.title.localizedContent(language),
        accent = relic?.let { relicAspectColor(it.aspect) }
            ?: item?.let { rarityColor(it.rarity) }
            ?: weapon?.let { weaponColor(it.id) }
            ?: if (choice.type == ChoiceType.RELIC) Gold else ParticleColors[index % ParticleColors.size],
        tag = tag,
        descriptions = buildList {
            // Flavor and generated catalog paragraphs belong in the Codex. Offers show effects only.
            when {
                choice.rewardFocus != null -> add(choice.description.localizedContent(language))
                preview?.requiresSlot == true -> {
                    add(language.text(GameplayText.PreviewAfterSlot))
                    relic?.let { add(it.rankEffect.localizedContent(language)) }
                }
                item != null -> if (preview == null) {
                    listOf(item.primary, item.secondary).forEach { modifier ->
                        val (amount, unit) = when (modifier.effect.unit) {
                            ModifierUnit.PERCENT -> modifier.amount * 100f to "%"
                            ModifierUnit.PER_SECOND -> modifier.amount to "/s"
                            ModifierUnit.SECONDS -> modifier.amount to "s"
                            ModifierUnit.FLAT -> modifier.amount to ""
                        }
                        add(modifier.effect.displayLabel.localizedContent(language) + ": +" + rewardNumber(amount, unit, language))
                    }
                } else if (preview.changes.isEmpty()) add(language.text(GameplayText.NoStatChange))
                relic != null -> if (preview?.changes.isNullOrEmpty()) add(relic.rankEffect.localizedContent(language))
                choice.totemAction == TotemAction.AMPLIFY_CURRENT -> Unit
                weapon != null -> add(weapon.description.localizedContent(language))
                else -> add(choice.description.localizedContent(language))
            }
            preview?.addedSynergies?.forEach { add("+ " + it.localizedContent(language)) }
            preview?.removedSynergies?.forEach { add("− " + it.localizedContent(language)) }
            if (preview?.changes?.any { it.sourceRelic == optionRelicId } == true) {
                when (optionRelicId) {
                    RelicId.FRACTURE_GATE -> add(language.text(GameplayText.RewardTranspose))
                    RelicId.ENGINE_OF_PARADOX -> add(language.text(GameplayText.RewardRewind))
                    RelicId.AGONY_SCEPTER -> add(content.relic(optionRelicId).rankEffect.localizedContent(language))
                    else -> Unit
                }
            }
        }.filter(String::isNotBlank).distinct(),
        operation = operation,
        item = item,
        itemStack = item?.let { itemStack(it.id) + 1 },
        weapon = weapon,
        relic = relic,
        incomingRelic = incoming,
        relicRank = rank,
        relicPolicy = policy,
        changes = preview?.changes?.map { change ->
            change.presentation(language).copy(source = change.sourceRelic?.takeIf {
                it != displayedRelicId || relicAction == RelicChoiceAction.REPLACE
            }?.let { content.relic(it).name.localizedContent(language) })
        }.orEmpty(),
        connections = rewardConnections(choice, language),
    )
}

internal fun RewardStatChange.presentation(language: AppLanguage): RewardStatPresentation {
    val precision = (2..5).firstOrNull {
        rewardNumber(before, unit, language, it) != rewardNumber(after, unit, language, it)
    } ?: 2
    return RewardStatPresentation(
        name = name.rewardLabel(language),
        before = rewardNumber(before, unit, language, precision),
        after = rewardNumber(after, unit, language, precision),
        improved = if (lowerIsBetter) after < before else after > before,
    )
}

/** Hundredths preserve small bonuses such as 0.04s; rounding never truncates them to zero. */
internal fun rewardNumber(value: Float, unit: String, language: AppLanguage, precision: Int = 2): String {
    val factor = (1..precision).fold(1L) { result, _ -> result * 10L }
    val rounded = kotlin.math.round(value * factor).toLong()
    val magnitude = kotlin.math.abs(rounded)
    val decimals = (magnitude % factor).toString().padStart(precision, '0').trimEnd('0')
    val separator = if (language == AppLanguage.Russian) "," else "."
    val number = (if (rounded < 0) "−" else "") + magnitude / factor + if (decimals.isEmpty()) "" else separator + decimals
    val suffix = if (language == AppLanguage.Russian) when (unit) { "s" -> " с"; "/s" -> "/с"; "u/s" -> " ед/с"; else -> unit } else unit
    return number + suffix
}

private fun GameplayRenderModel.rewardConnections(choice: ChoiceOption, language: AppLanguage): List<RewardConnection> = buildList {
    fun label(key: GameplayText) = language.text(key)
    fun addCore() = add(RewardConnection(content.coreShape(coreShape).displayName.localizedContent(language), label(GameplayText.AffectedComponent), core = coreShape))
    fun addWeapon() = add(RewardConnection(currentWeaponDefinition.name.localizedContent(language), label(GameplayText.AffectedComponent), weapon = currentWeaponDefinition))
    val item = choice.itemId?.let(content::item)
    if (item != null) {
        val effects = setOf(item.primary.effect, item.secondary.effect)
        val weaponEffects = setOf(ItemEffect.WEAPON_POWER, ItemEffect.ATTACK_SPEED, ItemEffect.CRIT_CHANCE, ItemEffect.CRIT_DAMAGE)
        if (effects.any { it in weaponEffects } || ItemEffect.MASS in effects && weapon == WeaponId.MORNINGSTAR) addWeapon()
        if (effects.any { it != ItemEffect.WEAPON_POWER && it != ItemEffect.ATTACK_SPEED }) addCore()
        content.items.filter { it.id / 20 == item.id / 20 && itemStack(it.id) > 0 }.forEach {
            add(RewardConnection(it.name.localizedContent(language), label(GameplayText.FamilyResonance), item = it))
        }
        equippedRelics.filter { equipped ->
            when (equipped.id) {
                RelicId.MASS_ECHO -> ItemEffect.MASS in effects
                RelicId.CHROMA_FEEDBACK, RelicId.DEVOURERS_TOLL -> ItemEffect.SHIELD_CAPACITY in effects
                RelicId.FRACTURE_LENS, RelicId.HARDLIGHT_EDGE, RelicId.MIRROR_CUT -> effects.any { it == ItemEffect.CRIT_CHANCE || it == ItemEffect.CRIT_DAMAGE }
                else -> false
            }
        }.forEach { equipped ->
            val relic = content.relic(equipped.id)
            add(RewardConnection(relic.name.localizedContent(language), label(GameplayText.AffectedComponent), relic = relic))
        }
    } else if (choice.weaponId != null || choice.totemAction == TotemAction.AMPLIFY_CURRENT) {
        addCore()
        val relatedEffects = setOf(ItemEffect.WEAPON_POWER, ItemEffect.ATTACK_SPEED, ItemEffect.CRIT_CHANCE, ItemEffect.CRIT_DAMAGE) +
            if ((choice.weaponId ?: weapon) == WeaponId.MORNINGSTAR) setOf(ItemEffect.MASS) else emptySet()
        content.items.filter { itemStack(it.id) > 0 && (it.primary.effect in relatedEffects || it.secondary.effect in relatedEffects) }.forEach {
            add(RewardConnection(it.name.localizedContent(language), label(GameplayText.WeaponInteraction), item = it))
        }
        equippedRelics.filter { it.id != RelicId.GHOST_VECTOR }.forEach {
            val relic = content.relic(it.id)
            add(RewardConnection(relic.name.localizedContent(language), label(GameplayText.WeaponInteraction), relic = relic))
        }
    } else {
        val id = choice.relicId ?: choice.relicSlot?.let { equippedRelics.getOrNull(it)?.id }
        if (id != null) {
            if (id == RelicId.GHOST_VECTOR) addCore() else addWeapon()
            val relevantSynergies = content.synergies.filter { it.requiredAspect == content.relic(id).aspect || id in it.requiredRelics }
            equippedRelics.filter { owned -> owned.id != id && (
                id == RelicId.CROWN_OF_FOUR_WINDS || owned.id == RelicId.CROWN_OF_FOUR_WINDS ||
                    relevantSynergies.any { synergy -> owned.id in synergy.requiredRelics || content.relic(owned.id).aspect == synergy.requiredAspect }
                ) }.forEach { owned ->
                val relic = content.relic(owned.id)
                add(RewardConnection(relic.name.localizedContent(language), label(GameplayText.SynergyConnection), relic = relic))
            }
            if (id == RelicId.MASS_ECHO) content.items.filter {
                itemStack(it.id) > 0 && (it.primary.effect == ItemEffect.MASS || it.secondary.effect == ItemEffect.MASS)
            }.forEach { add(RewardConnection(it.name.localizedContent(language), label(GameplayText.AffectedComponent), item = it)) }
        }
    }
}.distinct()

internal fun relicRewardOperation(
    action: RelicChoiceAction,
    ownedRank: Int,
    slotRank: Int?,
    slotIndex: Int,
    maxRank: Int,
    language: AppLanguage = AppLanguage.English,
): String = when (action) {
    RelicChoiceAction.ACQUIRE -> when {
        ownedRank >= maxRank -> language.text(GameplayText.SalvageResonance)
        ownedRank > 0 -> language.text(GameplayText.MeldRank, ownedRank, (ownedRank + 1).coerceAtMost(maxRank))
        else -> language.text(GameplayText.BindMatrix)
    }
    RelicChoiceAction.MELD -> language.text(GameplayText.MeldSlot)
    RelicChoiceAction.REPLACE -> language.text(GameplayText.ReplaceSlot, slotIndex + 1)
    RelicChoiceAction.MELD_TARGET -> {
        val rank = slotRank ?: 1
        if (rank >= maxRank) language.text(GameplayText.SalvageExcess) else language.text(GameplayText.MeldRank, rank, rank + 1)
    }
}

internal fun rewardCardIsCompact(widthDp: Float, heightDp: Float): Boolean =
    widthDp < 180f || heightDp < 240f

internal fun rewardHeading(
    type: ChoiceType,
    action: RelicChoiceAction?,
    language: AppLanguage = AppLanguage.English,
): String = when (type) {
    ChoiceType.ITEM -> language.text(GameplayText.ChooseArtifact)
    ChoiceType.TOTEM -> language.text(GameplayText.TotemResonance)
    ChoiceType.WEAPON -> language.text(GameplayText.WeaponSynchronization)
    ChoiceType.RELIC -> language.text(GameplayText.RelicIntercept)
    ChoiceType.RELIC_BIND -> if (action == RelicChoiceAction.MELD_TARGET) language.text(GameplayText.RelicMeld) else language.text(GameplayText.RelicRebind)
}

internal fun GameplayRenderModel.rewardSubtitle(language: AppLanguage = AppLanguage.English): String = when (choiceType) {
    ChoiceType.ITEM -> language.text(GameplayText.TimeSuspended)
    ChoiceType.TOTEM -> language.text(GameplayText.AmplifyOrRecalibrate)
    ChoiceType.WEAPON -> language.text(GameplayText.NextRunWeapon)
    ChoiceType.RELIC -> if (equippedRelics.size >= content.relicPolicy.maxSlots) {
        language.text(GameplayText.MatrixFull)
    } else language.text(GameplayText.EliteSignalCaptured)
    ChoiceType.RELIC_BIND -> if (choices.firstOrNull()?.relicAction == RelicChoiceAction.MELD_TARGET) {
        language.text(GameplayText.SelectMeldTarget)
    } else language.text(GameplayText.SelectReplaceSlot)
}
