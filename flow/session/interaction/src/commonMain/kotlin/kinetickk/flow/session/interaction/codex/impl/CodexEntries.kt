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
import kinetickk.foundation.design.*

internal sealed interface CodexIcon {
    data class Item(val definition: ItemDefinition, val stack: Int) : CodexIcon
    data class Relic(val definition: RelicDefinition, val rank: Int?) : CodexIcon
    data class Weapon(val id: WeaponId) : CodexIcon
    data class Shape(val id: CoreShape) : CodexIcon
    data class Synergy(val components: List<RelicId?>) : CodexIcon
    data object Unknown : CodexIcon
    data object Empty : CodexIcon
}

internal data class CodexEntry(
    val key: String,
    val title: String,
    val description: String,
    val kind: String,
    val quantity: String,
    val availability: String,
    val icon: CodexIcon,
    val color: Color,
    val discovered: Boolean = true,
    val rarity: Int = 0,
    val isNew: Boolean = false,
)

internal fun codexItemEntry(item: ItemDefinition, model: CodexRenderModel, language: AppLanguage = AppLanguage.English): CodexEntry {
    if (!model.isDiscovered(item.id)) return unknownEntry("item/${item.id}", language)
    val stack = model.itemStack(item.id)
    val build = model.runStacks.build
    val availability = when {
        stack >= item.maxStacks -> language.text(SessionText.STACK_LIMIT)
        build != null && item.id in build.eligibleItemIds -> language.text(SessionText.NEXT_ACQUISITION)
        build != null -> language.text(SessionText.OFFER_LIMIT)
        else -> language.text(SessionText.OFFERS_FROM_LEVEL, item.unlockLevel)
    }
    return CodexEntry(
        "item/${item.id}", item.name.localizedContent(language),
        "${item.description.localizedContent(language)}\n\n${item.primary.effect.displayLabel.localizedContent(language)}: ${modifier(item.primary, language)}\n${item.secondary.effect.displayLabel.localizedContent(language)}: ${modifier(item.secondary, language)}",
        item.rarity.displayLabel.localizedContent(language), "$stack/${item.maxStacks}",
        "$availability\n${if (model.isDiscovered(item.id)) language.text(SessionText.DISCOVERED) else language.text(SessionText.UNDISCOVERED)}",
        CodexIcon.Item(item, stack), codexRarityColor(item.rarity), model.isDiscovered(item.id), item.rarity.rank, item.id in model.newItemIds,
    )
}

internal fun codexWeaponEntry(weapon: WeaponDefinition, model: CodexRenderModel, progress: HomeProgressProjection, language: AppLanguage = AppLanguage.English, allowCurrentRun: Boolean = true): CodexEntry {
    val build = model.runStacks.build
    val equipped = build?.weapon == weapon.id
    if (weapon.id !in progress.loadout.unlockedWeapons && !(allowCurrentRun && equipped)) return unknownEntry("weapon/${weapon.id}", language).copy(
        description = language.text(SessionText.START_WEAPON_LOCKED, weapon.permanentUnlockCost), availability = language.text(SessionText.LOCKED))
    val availability = if (weapon.id in progress.loadout.unlockedWeapons) {
        if (weapon.id == progress.loadout.selectedWeapon) language.text(SessionText.START_WEAPON_SELECTED) else language.text(SessionText.START_WEAPON_UNLOCKED)
    } else language.text(SessionText.START_WEAPON_LOCKED, weapon.permanentUnlockCost)
    return CodexEntry("weapon/${weapon.id}", weapon.name.localizedContent(language), "${weapon.description.localizedContent(language)}\n\n${language.text(SessionText.WEAPON_HELP)}",
        language.text(SessionText.WEAPON), if (equipped) language.text(SessionText.LEVEL_SHORT, build.weaponLevel) else "—", "$availability${if (equipped) "\n" + language.text(SessionText.EQUIPPED_MASTERY, build.mastery.localizedContent(language)) else ""}",
        CodexIcon.Weapon(weapon.id), codexWeaponColor(weapon.id), weapon.id in progress.loadout.unlockedWeapons || equipped)
}

internal fun codexRelicEntry(relic: RelicDefinition, model: CodexRenderModel, catalog: UiCatalogSnapshot, language: AppLanguage = AppLanguage.English): CodexEntry {
    if (!model.isRelicDiscovered(relic.id)) return unknownEntry("relic/${relic.id}", language)
    val rank = model.runStacks.build?.relics?.firstOrNull { it.id == relic.id }?.rank
    return CodexEntry("relic/${relic.id}", relic.name.localizedContent(language), "${relic.description.localizedContent(language)}\n\n${relic.rankEffect.localizedContent(language)}", relic.aspect.displayLabel.localizedContent(language),
        "${rank ?: 0}/${catalog.relicPolicy.maxRank}", if (rank != null) language.text(SessionText.EQUIPPED) else language.text(SessionText.RELIC_AVAILABLE),
        CodexIcon.Relic(relic, rank), relicAspectColor(relic.aspect), isNew = relic.id in model.newRelicIds)
}

internal fun codexShapeEntry(shape: CoreShapeDefinition, model: CodexRenderModel, progress: HomeProgressProjection, language: AppLanguage = AppLanguage.English): CodexEntry {
    val unlocked = shape.id in progress.unlockedCoreShapes
    val achievements = progress.characterAchievements
    val current = when (shape.unlockRequirement) {
        kinetickk.ball.content.api.CharacterUnlockRequirement.AVAILABLE -> 1L
        kinetickk.ball.content.api.CharacterUnlockRequirement.ELITE_KILLS -> achievements.eliteKills
        kinetickk.ball.content.api.CharacterUnlockRequirement.DASH_HITS -> achievements.dashHits
        kinetickk.ball.content.api.CharacterUnlockRequirement.COMPLETED_ORBITS -> achievements.completedOrbits
        kinetickk.ball.content.api.CharacterUnlockRequirement.ARCHITECT_VICTORIES -> achievements.architectVictories
        kinetickk.ball.content.api.CharacterUnlockRequirement.DISTINCT_CHARACTER_VICTORIES -> achievements.victoriousCharacters.size.toLong()
    }
    val requirement = shape.unlockDescription.localizedContent(language)
    val unlockProgress = if (unlocked) language.text(SessionText.UNLOCKED) else
        language.text(SessionText.UNLOCK_PROGRESS, current.coerceAtMost(shape.unlockTarget.toLong()), shape.unlockTarget)
    return CodexEntry("shape/${shape.id}", if (unlocked) shape.displayName.localizedContent(language) else language.text(SessionText.UNKNOWN_CORE),
        if (unlocked) "${shape.mechanicDescription.localizedContent(language)}\n\n$requirement\n\n${language.text(SessionText.CHARACTER_LAB_HELP)}" else requirement,
        language.text(SessionText.CHARACTER), if (shape.id == model.runStacks.build?.character) "✓" else "—",
        unlockProgress, CodexIcon.Shape(shape.id), if (unlocked) Cyan else Muted, unlocked)
}

internal fun codexCatalogEntries(category: Int, search: String, filter: CodexItemFilter, model: CodexRenderModel, catalog: UiCatalogSnapshot, progress: HomeProgressProjection, language: AppLanguage = AppLanguage.English): List<CodexEntry> = when (category) {
    0 -> codexFilteredItems(model, search, filter, language).map { codexItemEntry(it, model, language) }
    1 -> catalog.weapons.map { codexWeaponEntry(it, model, progress, language, allowCurrentRun = false) }.filter { search.isBlank() || it.discovered && it.title.contains(search, ignoreCase = true) }
    2 -> catalog.relics.filter { model.isRelicDiscovered(it.id) && it.name.localizedContent(language).contains(search, ignoreCase = true) }.map { codexRelicEntry(it, model, catalog, language) }
    else -> catalog.coreShapes.map { codexShapeEntry(it, model, progress, language) }.filter { search.isBlank() || it.discovered && it.title.contains(search, ignoreCase = true) }
}

internal fun unknownEntry(key: String, language: AppLanguage): CodexEntry = CodexEntry(
    key, language.text(SessionText.UNKNOWN_DISCOVERY), language.text(SessionText.DISCOVERY_HELP),
    "—", "—", language.text(SessionText.UNDISCOVERED), CodexIcon.Unknown, Muted, discovered = false,
)

internal fun codexSynergyDiscovered(definition: kinetickk.ball.content.api.SynergyDefinition,
    model: CodexRenderModel, catalog: UiCatalogSnapshot): Boolean =
    if (definition.requiredAspect != null) catalog.relics.count { it.aspect == definition.requiredAspect && model.isRelicDiscovered(it.id) } >= 2
    else definition.requiredRelics.all(model::isRelicDiscovered)

internal fun codexRarityColor(rarity: ItemRarity): Color = when (rarity) {
    ItemRarity.COMMON -> Muted
    ItemRarity.UNCOMMON -> Cyan
    ItemRarity.RARE -> Violet
    ItemRarity.EPIC -> Magenta
    ItemRarity.LEGENDARY -> Acid
}

internal fun codexWeaponColor(id: WeaponId): Color = when (id) {
    WeaponId.FLUX_WAKE, WeaponId.ION_SWARM -> Cyan
    WeaponId.MORNINGSTAR, WeaponId.ARC_COIL -> Violet
    WeaponId.PHASE_LATTICE, WeaponId.RIFT_BLADES -> Magenta
    WeaponId.NULL_LANCE -> Acid
    WeaponId.GRAVITY_MINES, WeaponId.QUASAR_CANNON -> Orange
    WeaponId.ENTROPY_FIELD -> Red
    WeaponId.SINGULARITY_SPEAR -> White
    WeaponId.PRISM_RELAY -> Blue
}

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
