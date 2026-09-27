// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.ball.content.api.localizedContent

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ModifierUnit
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.RewardFocus
import kinetickk.ball.content.api.SynergyId
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.interaction.canvas.OverlayRelicSlot
import kinetickk.ball.gameplay.interaction.canvas.OverlaySynergyLink
import kinetickk.ball.gameplay.interaction.canvas.overlayIcon
import kinetickk.ball.gameplay.interaction.canvas.overlayLevel
import kinetickk.ball.gameplay.interaction.canvas.overlaySynergyLinks
import kinetickk.ball.gameplay.interaction.canvas.overlayColor
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.render.TotemAction
import kinetickk.ball.gameplay.nucleus.render.RewardStatChange
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkIcon

/** Which of the three reward layouts presents the choice (cards, totem rows, relic matrix). */
internal enum class RewardLayoutKind { CARDS, TOTEM, RELIC_BIND }

/** Where an accent comes from: a fixed token color, or a palette role resolved at draw time. */
internal enum class RewardTone { FIXED, YOU, THREAT, MUTE }

internal data class RewardPresentation(
    val heading: String,
    val subtitle: String,
    val cards: List<RewardCardPresentation>,
    val titleAccent: Color,
    val rerollAccent: Color,
    val rerollsRemaining: Int,
    val kind: RewardLayoutKind = RewardLayoutKind.CARDS,
    /** Player level shown by the slamming badge when a level-up opened the choice. */
    val level: Int? = null,
    /** Screen position (px) the cards are dealt from: the Core. */
    val dealOrigin: Offset? = null,
    /** Current relic matrix for relic choices. */
    val relicMatrix: RewardRelicMatrix? = null,
    /** Weapon totem copy (title and the mastery rules behind the (!) button). */
    val totem: RewardTotemHeader? = null,
    /** The relic being bound when every slot choice binds the same incoming relic. */
    val relicPanel: RewardRelicPanel? = null,
)

internal fun GameplayRenderModel.rewardPresentation(language: AppLanguage = AppLanguage.English): RewardPresentation {
    val kind = when (choiceType) {
        ChoiceType.TOTEM -> RewardLayoutKind.TOTEM
        ChoiceType.RELIC_BIND -> RewardLayoutKind.RELIC_BIND
        ChoiceType.ITEM, ChoiceType.WEAPON, ChoiceType.RELIC -> RewardLayoutKind.CARDS
    }
    val relicChoice = choiceType == ChoiceType.RELIC || choiceType == ChoiceType.RELIC_BIND
    val incoming = choices.firstOrNull { it.relicAction == RelicChoiceAction.REPLACE }?.relicId?.let(content::relic)
    return RewardPresentation(
        heading = rewardHeading(choiceType, choices.firstOrNull()?.relicAction, language),
        subtitle = "",
        cards = choices.mapIndexed { index, choice -> rewardCardPresentation(choice, index, language) },
        titleAccent = Kk.Bone,
        rerollAccent = Kk.Bone,
        rerollsRemaining = rerollsRemaining,
        kind = kind,
        level = level.takeIf { choiceType == ChoiceType.ITEM && !directedChoice && choices.none { it.rewardFocus != null } },
        dealOrigin = Offset(coreX - cameraX + screenWidth * 0.5f, coreY - cameraY + screenHeight * 0.5f),
        relicMatrix = if (relicChoice) rewardRelicMatrix(language) else null,
        totem = if (kind == RewardLayoutKind.TOTEM) RewardTotemHeader(
            title = language.text(GameplayText.WeaponTotem),
            masteryInfo = language.text(OverlayRedesignText.MasteryInfo, content.weaponMasteries
                .filter { it.minimumLevel > 1 }
                .joinToString(", ") { overlayLevel(it.minimumLevel, language) }),
        ) else null,
        relicPanel = incoming?.let { relic ->
            RewardRelicPanel(
                aspect = relic.aspect,
                aspectLabel = relic.aspect.displayLabel.localizedContent(language),
                rank = language.text(OverlayRedesignText.Rank, 1),
                isNew = !isRelicDiscovered(relic.id),
                name = relic.name.localizedContent(language),
                description = relic.description.localizedContent(language),
                effect = relic.rankEffect.localizedContent(language),
            )
        },
    )
}

internal data class RewardTotemHeader(val title: String, val masteryInfo: String)

/** Left "incoming" relic panel: big aspect diamond, tags, name, description and effect line. */
internal data class RewardRelicPanel(
    val aspect: RelicAspect?,
    val aspectLabel: String?,
    val rank: String?,
    val isNew: Boolean,
    val name: String,
    val description: String,
    val effect: String?,
)

/** The equipped relic matrix in slot order ([slots] has one entry per slot; null is free). */
internal data class RewardRelicMatrix(
    val slots: List<OverlayRelicSlot?>,
    val names: List<String>,
    val ranks: List<String>,
    val links: List<OverlaySynergyLink>,
    val linkNames: List<String>,
    val equipped: Int,
    val maxSlots: Int,
)

private fun GameplayRenderModel.rewardRelicMatrix(language: AppLanguage): RewardRelicMatrix {
    val maxSlots = content.relicPolicy.maxSlots
    val slots = List(maxSlots) { index ->
        equippedRelics.getOrNull(index)?.let { OverlayRelicSlot(it.id, content.relic(it.id).aspect, it.rank) }
    }
    val links = overlaySynergyLinks(slots.map { it?.id }, content)
    return RewardRelicMatrix(
        slots = slots,
        names = slots.map { slot -> slot?.let { content.relic(it.id).name.localizedContent(language) } ?: language.text(OverlayRedesignText.FreeSlot) },
        ranks = slots.map { slot -> slot?.let { language.text(OverlayRedesignText.Rank, it.rank) }.orEmpty() },
        links = links,
        linkNames = links.map { it.definition.name.localizedContent(language) },
        equipped = equippedRelics.size,
        maxSlots = maxSlots,
    )
}

/** How a relic choice would change the matrix: target slot, synergies gained or broken, rows. */
internal data class RewardRelicPreview(
    val targetSlot: Int?,
    val replace: Boolean,
    val incomingAspect: RelicAspect?,
    val addedLinks: List<OverlaySynergyLink>,
    val addedNames: List<String>,
    val removedSynergies: Set<SynergyId>,
    val rows: List<RewardPreviewRow>,
    val panel: RewardRelicPanel?,
    val kicker: String,
    val primaryLabel: String,
)

/** A signed preview row (`+` gained, `−` lost, `=` kept). */
internal data class RewardPreviewRow(
    val sign: String,
    val title: String,
    val detail: String?,
    val tone: RewardTone,
    val color: Color = Kk.Bone,
)

/** A weapon totem offering row. Ticks fill [fromLevel] owned levels plus the gained ones. */
internal data class RewardTotemRow(
    val icon: KkIcon,
    val kind: String,
    val kindTone: RewardTone,
    val meta: String?,
    val name: String,
    val description: String,
    val level: Int?,
    val fromLevel: Int,
    val toLevel: Int,
    val mastery: String?,
)

/** The outcome tag of a relic offer (Bind, Meld, Salvage, Replace). */
internal data class RewardAction(val label: String, val tone: RewardTone)

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
    val isNewDiscovery: Boolean = false,
    /** Card effect rank 1 (flat) .. 5 (legendary); rarity for items. */
    val rank: Int = 1,
    /** [accent] is used as is for [RewardTone.FIXED]; roles resolve the others. */
    val tone: RewardTone = RewardTone.FIXED,
    val icon: KkIcon? = null,
    val bandStart: String = tag,
    val bandEnd: String? = null,
    val family: String? = null,
    val tags: List<String> = emptyList(),
    val action: RewardAction? = null,
    val relicPreview: RewardRelicPreview? = null,
    val totemRow: RewardTotemRow? = null,
)

/**
 * A stat line. [before] empty = a plain added value; otherwise the before value is shown muted
 * next to the [after] value in the accent (never an arrow).
 */
internal data class RewardStatPresentation(
    val name: String,
    val before: String,
    val after: String,
    val improved: Boolean,
    val source: String? = null,
    val condition: String? = null,
)

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
    val choiceTag = choice.tag.localizedContent(language)
    val focus = choice.rewardFocus
    val action = choice.relicAction?.let { relicAction ->
        rewardRelicAction(relicAction, ownedRank, slotRelic?.rank, equippedRelics.size, policy, language)
    }
    val previewChanges = preview?.changes?.map { change ->
        change.presentation(language).copy(source = change.sourceRelic?.takeIf {
            it != displayedRelicId || choice.relicAction == RelicChoiceAction.REPLACE
        }?.let { content.relic(it).name.localizedContent(language) })
    }.orEmpty()
    val itemEffects = if (item != null && preview == null) {
        listOf(item.primary, item.secondary).map { modifier ->
            val (amount, unit) = when (modifier.effect.unit) {
                ModifierUnit.PERCENT -> modifier.amount * 100f to "%"
                ModifierUnit.PER_SECOND -> modifier.amount to "/s"
                ModifierUnit.SECONDS -> modifier.amount to "s"
                ModifierUnit.FLAT -> modifier.amount to ""
            }
            RewardStatPresentation(modifier.effect.displayLabel.localizedContent(language), "", "+" + rewardNumber(amount, unit, language), true)
        }
    } else emptyList()
    val rankLine = if (choice.relicAction == RelicChoiceAction.ACQUIRE && ownedRank < policy.maxRank) {
        listOf(RewardStatPresentation(
            language.text(OverlayRedesignText.RankLabel),
            if (ownedRank > 0) ownedRank.toString() else "",
            (ownedRank + 1).toString(),
            true,
        ))
    } else emptyList()
    val descriptions = buildList {
        // Flavor and generated catalog paragraphs belong in the Codex. Offers show effects only.
        when {
            focus != null -> add(choice.description.localizedContent(language))
            item != null -> if (preview != null && preview.changes.isEmpty()) add(language.text(GameplayText.NoStatChange))
            relic != null && choice.relicAction == RelicChoiceAction.ACQUIRE && ownedRank == 0 -> add(relic.description.localizedContent(language))
            relic != null -> add(relic.rankEffect.localizedContent(language))
            choice.totemAction == TotemAction.AMPLIFY_CURRENT -> add(currentWeaponDefinition.description.localizedContent(language))
            weapon != null -> add(weapon.description.localizedContent(language))
            else -> add(choice.description.localizedContent(language))
        }
    }.filter(String::isNotBlank).distinct()
    val relicAspect = relic?.aspect
    val (accent, tone) = when {
        relicAspect != null -> relicAspect.overlayColor() to RewardTone.FIXED
        item != null -> Kk.rarity(item.rarity.rank) to RewardTone.FIXED
        focus != null -> (focus.relicAspect()?.overlayColor() ?: Kk.Bone) to RewardTone.FIXED
        // Weapons are the player's own system: the palette's `you` color, resolved when drawn.
        weapon != null || choice.type == ChoiceType.TOTEM -> Kk.Bone to RewardTone.YOU
        else -> Kk.Bone to RewardTone.FIXED
    }
    return RewardCardPresentation(
        choice = choice,
        title = choice.title.localizedContent(language),
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
        changes = rankLine + itemEffects + previewChanges,
        connections = rewardConnections(choice, language),
        isNewDiscovery = when {
            item != null -> !isItemDiscovered(item.id)
            else -> choice.relicId?.let { !isRelicDiscovered(it) } ?: false
        },
        rank = when {
            item != null -> item.rarity.rank
            relicAspect == RelicAspect.SOVEREIGN -> 5
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
            else -> KkIcon.SYSTEM_DATA
        },
        bandStart = when {
            item != null -> item.rarity.displayLabel.localizedContent(language)
            relicAspect != null -> relicAspect.displayLabel.localizedContent(language)
            weapon != null -> weapon.tags.firstOrNull()?.localizedContent(language) ?: choiceTag
            else -> choiceTag
        },
        bandEnd = when {
            item != null -> language.text(OverlayRedesignText.StackCount,
                (itemStack(item.id) + 1).coerceAtMost(item.maxStacks), item.maxStacks)
            relic != null && rank != null -> language.text(OverlayRedesignText.Rank, rank)
            weapon != null -> overlayLevel(weaponLevel, language)
            else -> null
        },
        family = item?.let { language.text(OverlayRedesignText.FamilyLabel, it.family.localizedContent(language)) },
        tags = when {
            weapon != null -> weapon.tags.map { it.localizedContent(language) }.take(2)
            else -> emptyList()
        },
        action = action,
        relicPreview = if (choice.relicAction != null) rewardRelicPreview(choice, preview, language) else null,
        totemRow = if (choiceType == ChoiceType.TOTEM) rewardTotemRow(choice, weapon, language) else null,
    )
}

private fun RewardFocus.relicAspect(): RelicAspect? = RelicAspect.entries.firstOrNull { it.name == name }

private fun RewardFocus.overlayIcon(): KkIcon = relicAspect()?.overlayIcon() ?: when (this) {
    RewardFocus.MOTION -> KkIcon.SYSTEM_DASH
    RewardFocus.CONTROL -> KkIcon.SYSTEM_POLARITY
    RewardFocus.STRIKE -> KkIcon.SYSTEM_ELITE
    RewardFocus.OFFENSE -> KkIcon.SYSTEM_OVERDRIVE
    RewardFocus.DEFENSE -> KkIcon.SYSTEM_SHIELD
    RewardFocus.ECONOMY -> KkIcon.SYSTEM_MATTER
    else -> KkIcon.SYSTEM_DATA
}

/** The game's outcome of a relic action, named for its button or tag. */
internal fun rewardRelicAction(
    action: RelicChoiceAction,
    ownedRank: Int,
    slotRank: Int?,
    equipped: Int,
    policy: RelicPolicy,
    language: AppLanguage,
): RewardAction = when (action) {
    RelicChoiceAction.ACQUIRE -> when {
        ownedRank >= policy.maxRank -> RewardAction(language.text(OverlayRedesignText.Salvage), RewardTone.MUTE)
        ownedRank > 0 -> RewardAction(language.text(OverlayRedesignText.Meld), RewardTone.YOU)
        equipped < policy.maxSlots -> RewardAction(language.text(OverlayRedesignText.Bind), RewardTone.YOU)
        else -> RewardAction(language.text(OverlayRedesignText.Replace), RewardTone.THREAT)
    }
    RelicChoiceAction.MELD -> RewardAction(language.text(OverlayRedesignText.Meld), RewardTone.YOU)
    RelicChoiceAction.REPLACE -> RewardAction(language.text(OverlayRedesignText.Replace), RewardTone.THREAT)
    RelicChoiceAction.MELD_TARGET -> if ((slotRank ?: 1) >= policy.maxRank) {
        RewardAction(language.text(OverlayRedesignText.Salvage), RewardTone.MUTE)
    } else RewardAction(language.text(OverlayRedesignText.Meld), RewardTone.YOU)
}

private fun GameplayRenderModel.rewardRelicPreview(
    choice: ChoiceOption,
    preview: kinetickk.ball.gameplay.nucleus.render.RewardPreview?,
    language: AppLanguage,
): RewardRelicPreview {
    val policy = content.relicPolicy
    val current: List<RelicId?> = List(policy.maxSlots) { equippedRelics.getOrNull(it)?.id }
    val action = requireNotNull(choice.relicAction)
    val relicId = choice.relicId
    val slot = choice.relicSlot
    val ownedSlot = relicId?.let { id -> equippedRelics.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
    val (target, candidate) = when (action) {
        RelicChoiceAction.ACQUIRE -> when {
            relicId == null -> null to current
            ownedSlot != null -> ownedSlot to current
            equippedRelics.size < policy.maxSlots -> equippedRelics.size to current.toMutableList().also { it[equippedRelics.size] = relicId }
            else -> null to current
        }
        RelicChoiceAction.REPLACE -> if (slot != null && relicId != null && slot in current.indices) {
            slot to current.toMutableList().also { it[slot] = relicId }
        } else slot to current
        RelicChoiceAction.MELD_TARGET -> slot to current
        RelicChoiceAction.MELD -> null to current
    }
    val before = overlaySynergyLinks(current, content)
    val after = overlaySynergyLinks(candidate, content)
    val addedNames = preview?.addedSynergies?.toSet()
    val removedNames = preview?.removedSynergies?.toSet()
    val added = after.filter { link ->
        addedNames?.contains(link.definition.name) ?: before.none { it.definition.id == link.definition.id }
    }
    val removed = before.filter { link ->
        removedNames?.contains(link.definition.name) ?: after.none { it.definition.id == link.definition.id }
    }
    val kept = before.filter { link -> removed.none { it.definition.id == link.definition.id } }
    val slotRelic = slot?.let(equippedRelics::getOrNull)
    val relicAction = rewardRelicAction(action, relicId?.let(::relicRank) ?: 0, slotRelic?.rank, equippedRelics.size, policy, language)
    val rows = buildList {
        if (action == RelicChoiceAction.REPLACE && slotRelic != null) {
            val lost = content.relic(slotRelic.id)
            add(RewardPreviewRow("−", lost.name.localizedContent(language), lost.rankEffect.localizedContent(language), RewardTone.THREAT))
        }
        if (action == RelicChoiceAction.MELD_TARGET && slotRelic != null) {
            val target = content.relic(slotRelic.id)
            if (slotRelic.rank < policy.maxRank) {
                add(RewardPreviewRow("+", language.text(OverlayRedesignText.Rank, slotRelic.rank + 1),
                    target.rankEffect.localizedContent(language), RewardTone.YOU))
            } else {
                add(RewardPreviewRow("+", relicAction.label, choice.description.localizedContent(language), RewardTone.MUTE))
            }
        }
        removed.forEach { link ->
            add(RewardPreviewRow("−", link.definition.name.localizedContent(language),
                link.definition.description.localizedContent(language), RewardTone.THREAT))
        }
        added.forEach { link ->
            add(RewardPreviewRow("+", link.definition.name.localizedContent(language),
                link.definition.description.localizedContent(language), RewardTone.FIXED, link.definition.overlayColor()))
        }
        kept.forEach { link ->
            add(RewardPreviewRow("=", link.definition.name.localizedContent(language),
                link.definition.description.localizedContent(language), RewardTone.FIXED, link.definition.overlayColor()))
        }
    }
    val panelRelic = when (action) {
        RelicChoiceAction.MELD_TARGET -> slotRelic?.let { content.relic(it.id) }
        else -> relicId?.let(content::relic)
    }
    val panelRank = when (action) {
        RelicChoiceAction.MELD_TARGET -> slotRelic?.rank?.let { (it + 1).coerceAtMost(policy.maxRank) }
        RelicChoiceAction.REPLACE -> 1
        else -> relicId?.let { (relicRank(it) + 1).coerceAtMost(policy.maxRank) }
    }
    return RewardRelicPreview(
        targetSlot = target,
        replace = action == RelicChoiceAction.REPLACE,
        incomingAspect = when (action) {
            RelicChoiceAction.ACQUIRE, RelicChoiceAction.REPLACE -> relicId?.let { content.relic(it).aspect }
            else -> null
        },
        addedLinks = added,
        addedNames = added.map { it.definition.name.localizedContent(language) },
        removedSynergies = removed.mapTo(mutableSetOf()) { it.definition.id },
        rows = rows,
        panel = panelRelic?.let { relic ->
            RewardRelicPanel(
                aspect = relic.aspect,
                aspectLabel = relic.aspect.displayLabel.localizedContent(language),
                rank = panelRank?.let { language.text(OverlayRedesignText.Rank, it) },
                isNew = !isRelicDiscovered(relic.id),
                name = relic.name.localizedContent(language),
                description = relic.description.localizedContent(language),
                effect = relic.rankEffect.localizedContent(language),
            )
        },
        kicker = choice.title.localizedContent(language),
        primaryLabel = relicAction.label,
    )
}

private fun GameplayRenderModel.rewardTotemRow(
    choice: ChoiceOption,
    weapon: WeaponDefinition?,
    language: AppLanguage,
): RewardTotemRow {
    val mastery = { level: Int -> content.weaponMasteryForLevel(level).displayLabel.localizedContent(language) }
    val focus = choice.rewardFocus
    return when {
        focus != null -> RewardTotemRow(
            icon = focus.overlayIcon(), kind = choice.tag.localizedContent(language), kindTone = RewardTone.MUTE,
            meta = null, name = choice.title.localizedContent(language), description = choice.description.localizedContent(language),
            level = null, fromLevel = 0, toLevel = 0, mastery = null,
        )
        choice.totemAction == TotemAction.AMPLIFY_CURRENT -> RewardTotemRow(
            icon = this.weapon.overlayIcon(), kind = language.text(OverlayRedesignText.UpgradeKind), kindTone = RewardTone.MUTE,
            meta = language.text(OverlayRedesignText.Equipped), name = currentWeaponDefinition.name.localizedContent(language),
            description = currentWeaponDefinition.description.localizedContent(language),
            level = weaponLevel + 1, fromLevel = weaponLevel, toLevel = weaponLevel + 1, mastery = mastery(weaponLevel + 1),
        )
        choice.totemAction == TotemAction.CHANGE_WEAPON -> RewardTotemRow(
            icon = KkIcon.SYSTEM_REROLL, kind = language.text(OverlayRedesignText.ChangeKind), kindTone = RewardTone.YOU,
            meta = null, name = choice.title.localizedContent(language), description = choice.description.localizedContent(language),
            level = weaponLevel, fromLevel = weaponLevel, toLevel = weaponLevel, mastery = mastery(weaponLevel),
        )
        weapon != null -> RewardTotemRow(
            icon = weapon.id.overlayIcon(), kind = language.text(OverlayRedesignText.NewKind), kindTone = RewardTone.YOU,
            meta = null, name = weapon.name.localizedContent(language), description = weapon.description.localizedContent(language),
            level = weaponLevel, fromLevel = weaponLevel, toLevel = weaponLevel, mastery = mastery(weaponLevel),
        )
        else -> RewardTotemRow(
            icon = KkIcon.SYSTEM_DATA, kind = choice.tag.localizedContent(language), kindTone = RewardTone.MUTE, meta = null,
            name = choice.title.localizedContent(language), description = choice.description.localizedContent(language),
            level = null, fromLevel = 0, toLevel = 0, mastery = null,
        )
    }
}

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
