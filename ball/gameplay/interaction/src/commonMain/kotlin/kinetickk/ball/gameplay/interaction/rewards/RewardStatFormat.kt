// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ModifierUnit
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.StatDelta
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RewardStatChange
import kinetickk.ball.gameplay.nucleus.render.TotemAction
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.label

internal fun RewardStatChange.presentation(language: AppLanguage): RewardStatPresentation {
    val precision = (2..5).firstOrNull {
        rewardNumber(before, unit, language, it) != rewardNumber(after, unit, language, it)
    } ?: 2
    val label = name.rewardStatLabel(language)
    return RewardStatPresentation(
        name = label.name,
        before = rewardNumber(before, unit, language, precision),
        after = rewardNumber(after, unit, language, precision),
        improved = if (lowerIsBetter) after < before else after > before,
        condition = label.condition,
    )
}

/** Hundredths preserve small bonuses such as 0.04s; rounding never truncates them to zero. */
internal fun rewardNumber(value: Float, unit: String, language: AppLanguage, precision: Int = 2): String {
    val factor = (1..precision).fold(1L) { result, _ -> result * 10L }
    val rounded = kotlin.math.round(value * factor).toLong()
    val magnitude = kotlin.math.abs(rounded)
    val decimals = (factor + magnitude % factor).toString().substring(1).trimEnd('0')
    val separator = if (language == AppLanguage.Russian) "," else "."
    val number = (if (rounded < 0) "−" else "") + magnitude / factor + if (decimals.isEmpty()) "" else separator + decimals
    val suffix = if (language == AppLanguage.Russian) when (unit) { "s" -> " с"; "/s" -> "/с"; "u/s" -> " ед/с"; else -> unit } else unit
    return number + suffix
}

/** A passive stat line of an offered item: [scale] × the per-copy amount; negative amounts are costs. */
internal fun StatDelta.presentation(scale: Float, language: AppLanguage): RewardStatPresentation =
    statPresentation(effect, amount * scale, language)

/** A stat line: the stat's name and [value] signed in its unit ("+25", "+20%", "−0.6 s"); negative values are costs. */
internal fun statPresentation(effect: ItemEffect, value: Float, language: AppLanguage): RewardStatPresentation {
    val (shown, unit) = when (effect.unit) {
        ModifierUnit.PERCENT -> value * 100f to "%"
        ModifierUnit.PER_SECOND -> value to "/s"
        ModifierUnit.SECONDS -> value to "s"
        ModifierUnit.FLAT -> value to ""
    }
    val number = rewardNumber(shown, unit, language)
    return RewardStatPresentation(effect.displayLabel.localizedContent(language), "", if (value > 0f) "+$number" else number, value > 0f)
}

/** Tags whose items act through the weapon system rather than the Core alone. */
private val WeaponTags = setOf(BuildTag.ARSENAL, BuildTag.PRECISION)

/** Whether two items meet in the tag graph: a shared archetype tag or a shared element. */
private fun ItemDefinition.sharesTagWith(other: ItemDefinition): Boolean =
    tags.any { it in other.tags } || elements.any { it in other.elements }

/** Whether a relic draws on an item: attuned to one of its tags, or of an element it carries. */
private fun RelicDefinition.drawsOn(item: ItemDefinition): Boolean =
    attunedTags.any { it in item.tags } || aspect.element?.let { it in item.elements } == true

internal fun GameplayRenderModel.rewardConnections(choice: ChoiceOption, language: AppLanguage): List<RewardConnection> = buildList {
    fun label(key: GameplayText) = language.text(key)
    fun addCore() = add(RewardConnection(content.coreShape(coreShape).displayName.localizedContent(language), label(GameplayText.AffectedComponent), core = coreShape))
    fun addWeapon() = add(RewardConnection(currentWeaponDefinition.name.localizedContent(language), label(GameplayText.AffectedComponent), weapon = currentWeaponDefinition))
    val item = choice.itemId?.let(content::item)
    if (item != null) {
        if (item.tags.any { it in WeaponTags }) addWeapon()
        if (item.tags.any { it !in WeaponTags } || item.tags.isEmpty()) addCore()
        content.items.filter { it.id != item.id && itemStack(it.id) > 0 && it.sharesTagWith(item) }.forEach {
            add(RewardConnection(it.name.localizedContent(language), label(GameplayText.SharedTag), item = it))
        }
        equippedRelics.map { content.relic(it.id) }.filter { it.drawsOn(item) }.forEach { relic ->
            add(RewardConnection(relic.name.localizedContent(language), label(GameplayText.AffectedComponent), relic = relic))
        }
    } else if (choice.weaponId != null || choice.totemAction == TotemAction.AMPLIFY_CURRENT) {
        addCore()
        content.items.filter { itemStack(it.id) > 0 && it.tags.any { tag -> tag in WeaponTags } }.forEach {
            add(RewardConnection(it.name.localizedContent(language), label(GameplayText.WeaponInteraction), item = it))
        }
        equippedRelics.map { content.relic(it.id) }.filter { relic -> relic.attunedTags.any { it in WeaponTags } }.forEach { relic ->
            add(RewardConnection(relic.name.localizedContent(language), label(GameplayText.WeaponInteraction), relic = relic))
        }
    } else {
        val id = choice.relicId ?: choice.relicSlot?.let { equippedRelics.getOrNull(it)?.id }
        if (id != null) {
            val incoming = content.relic(id)
            if (incoming.attunedTags.any { it in WeaponTags }) addWeapon() else addCore()
            equippedRelics.filter { owned ->
                owned.id != id && (content.pairing(id, owned.id) != null || incoming.harmonizes(content.relic(owned.id)) ||
                    incoming.dissonates(content.relic(owned.id)))
            }.forEach { owned ->
                val relic = content.relic(owned.id)
                add(RewardConnection(relic.name.localizedContent(language), label(GameplayText.SynergyConnection), relic = relic))
            }
            content.items.filter { itemStack(it.id) > 0 && incoming.drawsOn(it) }.forEach {
                add(RewardConnection(it.name.localizedContent(language), label(GameplayText.AffectedComponent), item = it))
            }
        }
    }
}.distinct()
