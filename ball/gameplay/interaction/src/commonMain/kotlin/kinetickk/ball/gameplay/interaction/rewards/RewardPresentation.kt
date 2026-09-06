// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.ball.content.api.localizedContent

import androidx.compose.ui.graphics.Color
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.gameplay.interaction.canvas.rarityColor
import kinetickk.ball.gameplay.interaction.canvas.ParticleColors
import kinetickk.ball.gameplay.interaction.canvas.relicAspectColor
import kinetickk.ball.gameplay.interaction.canvas.weaponColor
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.render.TotemAction
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
        item != null -> language.text(GameplayText.Stack, itemStack(item.id) + 1, item.maxStacks)
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
        descriptions = listOfNotNull(
            item?.description ?: weapon?.description ?: relic?.description,
            choice.description,
            relic?.rankEffect,
            incoming?.let {
                language.text(GameplayText.Incoming, it.name.localizedContent(language),
                    it.description.localizedContent(language), it.rankEffect.localizedContent(language))
            },
        ).filter(String::isNotBlank).map { it.localizedContent(language) }.distinct(),
        operation = operation,
        item = item,
        itemStack = item?.let { itemStack(it.id) + 1 },
        weapon = weapon,
        relic = relic,
        incomingRelic = incoming,
        relicRank = rank,
        relicPolicy = policy,
    )
}

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
