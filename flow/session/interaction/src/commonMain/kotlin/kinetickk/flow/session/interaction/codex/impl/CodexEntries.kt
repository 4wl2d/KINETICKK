// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.ball.content.api.localizedContent
import androidx.compose.ui.graphics.Color
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemModifier
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.ModifierUnit
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.home.impl.coreShapeUnlockProgress
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkIcon

internal sealed interface CodexIcon {
    data class Item(val definition: ItemDefinition, val stack: Int) : CodexIcon
    data class Relic(val definition: RelicDefinition, val rank: Int?) : CodexIcon
    data class Weapon(val id: WeaponId) : CodexIcon
    data class Shape(val id: CoreShape) : CodexIcon
    data class Synergy(val components: List<RelicId?>) : CodexIcon
    data object Unknown : CodexIcon
    data object Empty : CodexIcon
}

/**
 * One labelled value of an entry (label and value are separate texts, never a sentence caption).
 * [info] is an explanation shown only behind the value's (!) button.
 */
internal data class CodexFact(val label: String, val value: String, val info: String? = null, val isStatus: Boolean = false)

internal data class CodexEntry(
    val key: String,
    val title: String,
    val description: String,
    val kind: String,
    val quantity: String,
    val facts: List<CodexFact>,
    val icon: CodexIcon,
    val color: Color,
    val discovered: Boolean = true,
    val rarity: Int = 0,
    val isNew: Boolean = false,
    /** Explanation shown behind the (!) button, never as a caption. */
    val help: String? = null,
    /** True for the equipped form or an active synergy (shown with color, not a glyph). */
    val active: Boolean = false,
) {
    /** The facts as "label value" lines (accessibility and search summaries). */
    val availability: String
        get() = facts.joinToString("\n") { "${it.label} ${it.value}" }

    /** Short status value of the entry (its Status fact), if it has one. */
    val status: String?
        get() = facts.firstOrNull { it.isStatus }?.value

    /** Accessible summary: title, kind, amount and facts as separate phrases. */
    val summary: String
        get() = listOf(title, kind, quantity, availability.replace("\n", ", "))
            .filter { it.isNotBlank() && it != CODEX_NONE }
            .joinToString(", ") + if (!discovered && help != null) ", $help" else ""
}

/** Placeholder for "no value" in entry fields; never rendered as a label. */
internal const val CODEX_NONE = "—"

internal fun codexStatus(language: AppLanguage, value: String, info: String? = null): CodexFact =
    CodexFact(language.text(SessionText.STATUS), value, info, isStatus = true)

internal fun codexItemEntry(item: ItemDefinition, model: CodexRenderModel, language: AppLanguage = AppLanguage.English): CodexEntry {
    if (!model.isDiscovered(item.id)) return unknownEntry("item/${item.id}", language)
    val stack = model.itemStack(item.id)
    val build = model.runStacks.build
    val facts = buildList {
        // Offers depend on the run: the stack limit, then the game's current eligibility.
        when {
            stack >= item.maxStacks -> add(codexStatus(language, language.text(SessionText.STACK_LIMIT)))
            build != null && item.id in build.eligibleItemIds -> add(codexStatus(language, language.text(SessionText.NEXT_ACQUISITION)))
            build != null -> add(codexStatus(language, language.text(SessionText.OFFER_LIMIT), language.text(SessionText.OFFER_LIMIT_HELP)))
        }
        add(CodexFact(language.text(SessionText.OFFERS_FROM_LEVEL), language.text(SessionText.LEVEL_SHORT, item.unlockLevel),
            language.text(SessionText.OFFERS_FROM_LEVEL_HELP)))
    }
    return CodexEntry(
        "item/${item.id}", item.name.localizedContent(language),
        "${item.description.localizedContent(language)}\n\n${item.primary.effect.displayLabel.localizedContent(language)}: ${modifier(item.primary, language)}\n${item.secondary.effect.displayLabel.localizedContent(language)}: ${modifier(item.secondary, language)}",
        item.rarity.displayLabel.localizedContent(language), "$stack/${item.maxStacks}", facts,
        CodexIcon.Item(item, stack), codexRarityColor(item.rarity), true, item.rarity.rank, item.id in model.newItemIds,
    )
}

internal fun codexWeaponEntry(weapon: WeaponDefinition, model: CodexRenderModel, progress: HomeProgressProjection, language: AppLanguage = AppLanguage.English, allowCurrentRun: Boolean = true): CodexEntry {
    val build = model.runStacks.build
    val equipped = build?.weapon == weapon.id
    val unlocked = weapon.id in progress.loadout.unlockedWeapons
    val costFact = CodexFact(language.text(SessionText.UNLOCK_COST), weapon.permanentUnlockCost.toString())
    if (!unlocked && !(allowCurrentRun && equipped)) return unknownEntry("weapon/${weapon.id}", language).copy(
        facts = listOf(codexStatus(language, language.text(SessionText.LOCKED)), costFact),
        help = language.text(SessionText.WEAPON_LOCKED_HELP),
    )
    val facts = buildList {
        add(codexStatus(language, language.text(when {
            !unlocked -> SessionText.LOCKED
            weapon.id == progress.loadout.selectedWeapon -> SessionText.START_WEAPON_SELECTED
            else -> SessionText.START_WEAPON_UNLOCKED
        })))
        if (!unlocked) add(costFact)
        if (equipped) {
            add(CodexFact(language.text(SessionText.WEAPON_LEVEL), language.text(SessionText.LEVEL_SHORT, build.weaponLevel)))
            add(CodexFact(language.text(SessionText.MASTERY), build.mastery.localizedContent(language)))
        }
    }
    return CodexEntry("weapon/${weapon.id}", weapon.name.localizedContent(language), weapon.description.localizedContent(language),
        language.text(SessionText.WEAPON), if (equipped) language.text(SessionText.LEVEL_SHORT, build.weaponLevel) else CODEX_NONE, facts,
        CodexIcon.Weapon(weapon.id), Kk.Bone, unlocked || equipped,
        help = language.text(SessionText.WEAPON_HELP), active = equipped)
}

internal fun codexRelicEntry(relic: RelicDefinition, model: CodexRenderModel, catalog: UiCatalogSnapshot, language: AppLanguage = AppLanguage.English): CodexEntry {
    if (!model.isRelicDiscovered(relic.id)) return unknownEntry("relic/${relic.id}", language)
    val rank = model.runStacks.build?.relics?.firstOrNull { it.id == relic.id }?.rank
    val facts = buildList {
        add(codexStatus(language, language.text(if (rank != null) SessionText.EQUIPPED else SessionText.RELIC_AVAILABLE)))
        if (rank != null) add(CodexFact(language.text(SessionText.RANK), "$rank/${catalog.relicPolicy.maxRank}"))
    }
    return CodexEntry("relic/${relic.id}", relic.name.localizedContent(language), "${relic.description.localizedContent(language)}\n\n${relic.rankEffect.localizedContent(language)}", relic.aspect.displayLabel.localizedContent(language),
        "${rank ?: 0}/${catalog.relicPolicy.maxRank}", facts,
        CodexIcon.Relic(relic, rank), relicAspectColor(relic.aspect), isNew = relic.id in model.newRelicIds, active = rank != null)
}

internal fun codexShapeEntry(shape: CoreShapeDefinition, model: CodexRenderModel, progress: HomeProgressProjection, language: AppLanguage = AppLanguage.English): CodexEntry {
    val unlocked = shape.id in progress.unlockedCoreShapes
    val current = coreShapeUnlockProgress(shape, progress.characterAchievements)
    val requirement = shape.unlockDescription.localizedContent(language)
    val equipped = shape.id == model.runStacks.build?.character
    val facts = buildList {
        add(codexStatus(language, language.text(when {
            equipped -> SessionText.EQUIPPED
            unlocked -> SessionText.UNLOCKED
            else -> SessionText.LOCKED
        })))
        if (!unlocked) add(CodexFact(language.text(SessionText.PROGRESS), "$current/${shape.unlockTarget}"))
    }
    return CodexEntry("shape/${shape.id}", if (unlocked) shape.displayName.localizedContent(language) else language.text(SessionText.UNKNOWN_CORE),
        if (unlocked) "${shape.mechanicDescription.localizedContent(language)}\n\n$requirement" else requirement,
        language.text(SessionText.CHARACTER), if (equipped) language.text(SessionText.EQUIPPED) else CODEX_NONE,
        facts, CodexIcon.Shape(shape.id), if (unlocked) Kk.Bone else Kk.Mute, unlocked,
        help = if (unlocked) language.text(SessionText.CHARACTER_LAB_HELP) else null, active = equipped)
}

internal fun codexCatalogEntries(category: Int, search: String, filter: CodexItemFilter, model: CodexRenderModel, catalog: UiCatalogSnapshot, progress: HomeProgressProjection, language: AppLanguage = AppLanguage.English): List<CodexEntry> = when (category) {
    0 -> codexFilteredItems(model, search, filter, language).map { codexItemEntry(it, model, language) }
    1 -> catalog.weapons.map { codexWeaponEntry(it, model, progress, language, allowCurrentRun = false) }.filter { search.isBlank() || it.discovered && it.title.contains(search, ignoreCase = true) }
    2 -> catalog.relics.filter { model.isRelicDiscovered(it.id) && it.name.localizedContent(language).contains(search, ignoreCase = true) }.map { codexRelicEntry(it, model, catalog, language) }
    else -> catalog.coreShapes.map { codexShapeEntry(it, model, progress, language) }.filter { search.isBlank() || it.discovered && it.title.contains(search, ignoreCase = true) }
}

internal fun unknownEntry(key: String, language: AppLanguage): CodexEntry = CodexEntry(
    key, language.text(SessionText.UNKNOWN_DISCOVERY), "",
    CODEX_NONE, CODEX_NONE, listOf(codexStatus(language, language.text(SessionText.UNDISCOVERED))), CodexIcon.Unknown, Kk.Mute,
    discovered = false, help = language.text(SessionText.DISCOVERY_HELP),
)

internal fun codexSynergyDiscovered(definition: kinetickk.ball.content.api.SynergyDefinition,
    model: CodexRenderModel, catalog: UiCatalogSnapshot): Boolean =
    if (definition.requiredAspect != null) catalog.relics.count { it.aspect == definition.requiredAspect && model.isRelicDiscovered(it.id) } >= 2
    else definition.requiredRelics.all(model::isRelicDiscovered)

/** Rarity color (foundation rarity tokens, rank 1 common .. 5 legendary). */
internal fun codexRarityColor(rarity: ItemRarity): Color = Kk.rarity(rarity.rank)

/** Redesign weapon icon (`icons.json weapons.*`) for a weapon id. */
internal fun codexWeaponIcon(id: WeaponId): KkIcon = requireNotNull(KkIcon.byKey("weapons." + id.name.lowercase())) {
    "Missing weapon icon for $id"
}

internal fun codexModifierValue(value: ItemModifier, language: AppLanguage): String = modifier(value, language)

private fun modifier(value: ItemModifier, language: AppLanguage): String = when (value.effect.unit) {
    ModifierUnit.PERCENT -> "+${codexNumber(value.amount * 100f, language)}%"
    ModifierUnit.FLAT -> "+${codexNumber(value.amount, language)}"
    ModifierUnit.PER_SECOND -> "+${codexNumber(value.amount, language)}${"/s".localizedContent(language)}"
    ModifierUnit.SECONDS -> "+${codexNumber(value.amount, language)}${"s".localizedContent(language)}"
}

internal fun codexNumber(value: Float, language: AppLanguage = AppLanguage.English): String {
    val number = ((value * 100f).toInt() / 100f).toString()
    return when (language) {
        AppLanguage.English -> number
        AppLanguage.Russian -> number.replace('.', ',')
    }
}
