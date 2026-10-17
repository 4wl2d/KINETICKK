// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.ConversionKind
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.ModifierUnit
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.RewardFocus
import kinetickk.ball.content.api.RuleLineKind
import kinetickk.ball.content.api.RuleTextContext
import kinetickk.ball.content.api.StatDelta
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.liabilityText
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.content.api.ruleLines
import kinetickk.ball.content.api.upsideText
import kinetickk.ball.gameplay.interaction.canvas.OverlayRelicLink
import kinetickk.ball.gameplay.interaction.canvas.OverlayRelicSlot
import kinetickk.ball.gameplay.interaction.canvas.UNKNOWN_PAIRING_NAME
import kinetickk.ball.gameplay.interaction.canvas.overlayColor
import kinetickk.ball.gameplay.interaction.canvas.overlayDecimal
import kinetickk.ball.gameplay.interaction.canvas.overlayDescription
import kinetickk.ball.gameplay.interaction.canvas.overlayIcon
import kinetickk.ball.gameplay.interaction.canvas.overlayLevel
import kinetickk.ball.gameplay.interaction.canvas.overlayLinkMultiplier
import kinetickk.ball.gameplay.interaction.canvas.overlayName
import kinetickk.ball.gameplay.interaction.canvas.overlayRelicLinks
import kinetickk.ball.gameplay.interaction.canvas.overlayUpsideMultiplier
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.ball.gameplay.nucleus.render.BuildForecast
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.ForecastKind
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.render.RelicLinkKind
import kinetickk.ball.gameplay.nucleus.render.RewardStatChange
import kinetickk.ball.gameplay.nucleus.render.TotemAction
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.pow
import kotlin.math.sqrt

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
) {
    /** An Heirloom offer: gold carries its heading. */
    val heirloom: Boolean get() = cards.any { it.heirloom }
}

internal fun GameplayRenderModel.rewardPresentation(language: AppLanguage = AppLanguage.English): RewardPresentation {
    val kind = when (choiceType) {
        ChoiceType.TOTEM -> RewardLayoutKind.TOTEM
        ChoiceType.RELIC_BIND -> RewardLayoutKind.RELIC_BIND
        ChoiceType.ITEM, ChoiceType.WEAPON, ChoiceType.RELIC -> RewardLayoutKind.CARDS
    }
    val relicChoice = choiceType == ChoiceType.RELIC || choiceType == ChoiceType.RELIC_BIND
    val incoming = choices.firstOrNull { it.relicAction == RelicChoiceAction.REPLACE }?.relicId?.let(content::relic)
    return RewardPresentation(
        heading = if (choices.any { it.heirloom && it.relicId != null }) language.text(OverlayRedesignText.Heirloom)
            else rewardHeading(choiceType, choices.firstOrNull()?.relicAction, language),
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
        relicPanel = incoming?.let { relic -> rewardRelicPanel(relic, 1, equippedRelics.map { it.id }, language) },
    )
}

/** The relic panel of [relic] at [rank]: its keys read against the bound [partners]. */
private fun GameplayRenderModel.rewardRelicPanel(
    relic: RelicDefinition,
    rank: Int,
    partners: List<RelicId>,
    language: AppLanguage,
    heirloom: Boolean = false,
): RewardRelicPanel = RewardRelicPanel(
    aspect = relic.aspect,
    aspectLabel = relic.aspect.displayLabel.localizedContent(language),
    rank = language.text(OverlayRedesignText.Rank, rank),
    isNew = !isRelicDiscovered(relic.id),
    name = relic.name.localizedContent(language),
    description = relic.upsideText(language, rank),
    liability = relic.liabilityText(language),
    relic = relic,
    values = relic.upsideValueRanges(language, rank),
    keys = rewardRelicKeys(relic, partners, language),
    attuned = rewardAttunedChips(relic, language),
    heirloom = heirloom,
    level = rank,
)

/**
 * Where the rank-scaled values of the upside sit in [RelicDefinition.upsideText]: the localized
 * template's literal parts are matched in order, and what lies between them is a value.
 */
internal fun RelicDefinition.upsideValueRanges(language: AppLanguage, rank: Int): List<IntRange> {
    val template = upside.localizedContent(language)
    val text = upsideText(language, rank)
    val parts = template.split(RelicPlaceholder)
    if (parts.size < 2 || !text.startsWith(parts.first())) return emptyList()
    val ranges = ArrayList<IntRange>(parts.size - 1)
    var cursor = parts.first().length
    for (index in 1 until parts.size) {
        val literal = parts[index]
        val end = when {
            literal.isNotEmpty() -> text.indexOf(literal, cursor).takeIf { it >= 0 } ?: return ranges
            index == parts.lastIndex -> text.length
            else -> return ranges
        }
        if (end > cursor) ranges += cursor until end
        cursor = end + literal.length
    }
    return ranges
}

private val RelicPlaceholder = Regex("\\{[0-9]+}")

/**
 * The upside values a rank change raises, at [from] and at [to] ("15 %", "18 %"; several join with
 * a comma), each with the percent mark that follows it in the text. Null when no value changes.
 */
internal fun RelicDefinition.rankValueChange(language: AppLanguage, from: Int, to: Int): Pair<String?, String?> {
    fun values(rank: Int): List<String> {
        val text = upsideText(language, rank)
        return upsideValueRanges(language, rank).map { range ->
            val unit = text.substring(range.last + 1).takeWhile { it == ' ' || it == '\u00A0' || it == '%' }
            text.substring(range) + if ('%' in unit) "\u00A0%" else ""
        }
    }
    val before = values(from)
    val after = values(to)
    val changed = before.indices.filter { it < after.size && before[it] != after[it] }
    if (changed.isEmpty()) return null to null
    return changed.joinToString(", ") { before[it] } to changed.joinToString(", ") { after[it] }
}

/** Keys of [relic] against the bound [partners] (itself excluded): shared ones harmonize, opposed ones interfere. */
private fun GameplayRenderModel.rewardRelicKeys(relic: RelicDefinition, partners: List<RelicId>, language: AppLanguage): List<RewardRelicKey> {
    val others = partners.filter { it != relic.id }.map(content::relic)
    return relic.keys.map { key ->
        val state = when {
            others.any { other -> other.keys.any { RelicKey.opposed(key, it) } } -> RewardKeyState.OPPOSED
            others.any { other -> key in other.keys } -> RewardKeyState.SHARED
            else -> RewardKeyState.NEUTRAL
        }
        RewardRelicKey(key.displayLabel.localizedContent(language), state)
    }
}

/**
 * A relic's attuned tags, marked with the relic's aspect (they are not the card's own tags); a tag
 * the build already carries is lit (its items gain the attunement bonus).
 */
private fun GameplayRenderModel.rewardAttunedChips(relic: RelicDefinition, language: AppLanguage): List<RewardChip> =
    relic.attunedTags.map { tag ->
        RewardChip(RewardChipKind.ATTUNED, tag.displayLabel.localizedContent(language), tag = tag, aspect = relic.aspect,
            active = content.items.any { item -> itemStack(item.id) > 0 && tag in item.tags })
    }

private fun GameplayRenderModel.rewardRelicMatrix(language: AppLanguage): RewardRelicMatrix {
    val maxSlots = content.relicPolicy.maxSlots
    val slots = List(maxSlots) { index ->
        equippedRelics.getOrNull(index)?.let { OverlayRelicSlot(it.id, content.relic(it.id).aspect, it.rank) }
    }
    val links = overlayRelicLinks(slots.map { it?.id })
    return RewardRelicMatrix(
        slots = slots,
        names = slots.map { slot -> slot?.let { content.relic(it.id).name.localizedContent(language) } ?: language.text(OverlayRedesignText.FreeSlot) },
        ranks = slots.map { slot -> slot?.let { language.text(OverlayRedesignText.Rank, it.rank) }.orEmpty() },
        links = links,
        linkNames = links.map { it.overlayName(content, language) },
        equipped = equippedRelics.size,
        maxSlots = maxSlots,
        definitions = slots.map { slot -> slot?.let { content.relic(it.id) } },
        policy = content.relicPolicy,
    )
}

internal data class RewardTotemHeader(val title: String, val masteryInfo: String)

/** Left "incoming" relic panel: rune diamond, tags, name, upside, its mandatory cost, keys and attunement. */
internal data class RewardRelicPanel(
    val aspect: RelicAspect?,
    val aspectLabel: String?,
    val rank: String?,
    val isNew: Boolean,
    val name: String,
    /** The upside at the panel's rank. */
    val description: String,
    /** The liability; it never scales with rank. */
    val liability: String?,
    val relic: RelicDefinition? = null,
    /** Where the rank-scaled values sit in [description] (tinted in the aspect hue). */
    val values: List<IntRange> = emptyList(),
    val keys: List<RewardRelicKey> = emptyList(),
    val attuned: List<RewardChip> = emptyList(),
    val heirloom: Boolean = false,
    /** The rank the panel shows, for the rune's rank pips. */
    val level: Int = 1,
)

/** The equipped relic matrix in slot order ([slots] has one entry per slot; null is free). */
internal data class RewardRelicMatrix(
    val slots: List<OverlayRelicSlot?>,
    val names: List<String>,
    val ranks: List<String>,
    val links: List<OverlayRelicLink>,
    val linkNames: List<String>,
    val equipped: Int,
    val maxSlots: Int,
    /** The bound relic of each slot (null when free), for its rune. */
    val definitions: List<RelicDefinition?> = emptyList(),
    val policy: RelicPolicy = RelicPolicy(maxSlots.coerceAtLeast(1), 1),
)

/** How a relic choice would change the matrix: target slot, relic links formed or broken, rows. */
internal data class RewardRelicPreview(
    val targetSlot: Int?,
    val replace: Boolean,
    val incomingAspect: RelicAspect?,
    val addedLinks: List<OverlayRelicLink>,
    val addedNames: List<String>,
    /** Current links the choice breaks (matched by their two relics). */
    val removedLinks: List<OverlayRelicLink>,
    val rows: List<RewardPreviewRow>,
    val panel: RewardRelicPanel?,
    val kicker: String,
    val primaryLabel: String,
    /** The relic entering [targetSlot] (its rune previews in the slot). */
    val incoming: RelicDefinition? = null,
)

/**
 * A signed preview row (`+` gained, `−` lost, `=` kept). [kicker] names the bound relic the row is
 * about; [before]/[after] show a value now and after the choice (an upside multiplier);
 * [unknown] marks a named pairing the player has not met yet.
 */
internal data class RewardPreviewRow(
    val sign: String,
    val title: String,
    val detail: String?,
    val tone: RewardTone,
    val color: Color = Kk.Bone,
    val kicker: String? = null,
    val before: String? = null,
    val after: String? = null,
    val unknown: Boolean = false,
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

/** What a chip on a reward card marks: identity beside the icon, or a build forecast in the footer. */
internal enum class RewardChipKind { TAG, ATTUNED, RISK, REBORN, HEIRLOOM, SUPPRESSED, REACTION, TAG_TIER, AFFINITY, CONCORD, DISCORD, HARMONY, DISSONANCE }

/**
 * A chip of a reward card. [tag] and [elements] give its glyphs (a reaction carries its two
 * elements); [before]/[after] are the tiers a TAG_TIER forecast crosses (of [pips]) or the tier
 * ring marks of a REBORN chip; [active] marks a tag the build already carries or an affinity tier
 * the card reaches; [discovered] is false for a reaction or pairing the player has not met.
 * [relic] is the bound relic that voids a SUPPRESSED card ([rune] draws its mark); [aspect] is
 * the relic aspect an ATTUNED tag belongs to.
 */
internal data class RewardChip(
    val kind: RewardChipKind,
    val label: String,
    val tag: BuildTag? = null,
    val elements: List<StatusElement> = emptyList(),
    val relic: RelicId? = null,
    val rune: RelicDefinition? = null,
    val aspect: RelicAspect? = null,
    val discovered: Boolean = true,
    val before: Int = 0,
    val after: Int = 0,
    val pips: Int = 0,
    val active: Boolean = false,
)

/** One mechanical line of an item ([RuleLineKind.TRIGGER] headers its effect); [suppressed] lines are void. */
internal data class RewardRuleLine(
    val kind: RuleLineKind,
    val text: String,
    val element: StatusElement? = null,
    val suppressed: Boolean = false,
)

/** How a relic key meets the bound relics: a shared key harmonizes, an opposed one interferes. */
internal enum class RewardKeyState { NEUTRAL, SHARED, OPPOSED }

internal data class RewardRelicKey(val label: String, val state: RewardKeyState)

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
    /** Mandatory costs: a relic's liability, an item's cost rules. */
    val liabilities: List<String> = emptyList(),
    /** Build consequences of taking the card, most important first (from the reward preview). */
    val forecasts: List<BuildForecast> = emptyList(),
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
    /** The card's elements: pips on the band and the icon hue (an item's elements). */
    val elements: List<StatusElement> = emptyList(),
    /** Identity chips beside the icon: Risk, Reborn, Heirloom, tags or a relic's attuned tags. */
    val chips: List<RewardChip> = emptyList(),
    /** Forecast chips in the footer, most important first. */
    val forecast: List<RewardChip> = emptyList(),
    /** An item's mechanics line by line (costs last); [descriptions] and [liabilities] hold their text. */
    val rules: List<RewardRuleLine> = emptyList(),
    /** Where the rank-scaled values of a relic upside (the first description) sit. */
    val values: List<IntRange> = emptyList(),
    val keys: List<RewardRelicKey> = emptyList(),
    /** Rebirth tier shown as ring marks on Reborn and Heirloom cards. */
    val rebirthTier: Int = 0,
) {
    val decline: Boolean get() = choice.relicAction == RelicChoiceAction.DECLINE
    val reborn: Boolean get() = choice.reborn && item != null
    val heirloom: Boolean get() = choice.heirloom && relic != null

    /**
     * The card hue under the player's color-vision preset: a relic takes its aspect (an element's
     * hue, the preset's volt for Kinetic, gold for Sovereign), a direction its aspect or, without an
     * element, Kinetic, and so does a meld (two forms joined, no element).
     */
    fun accentColor(roles: KkRolePalette): Color {
        relic?.let { return it.aspect.overlayColor(roles) }
        choice.rewardFocus?.let { focus -> return focus.relicAspect()?.overlayColor(roles) ?: roles.kinetic }
        if (choice.relicAction == RelicChoiceAction.MELD) return roles.kinetic
        return tone.color(roles, accent)
    }

    /** The icon hue: an item's first element (the preset's volt when it deals Kinetic damage), else the card hue. */
    fun iconColor(roles: KkRolePalette): Color = when {
        item != null -> elements.firstOrNull()?.overlayColor(roles) ?: roles.kinetic
        else -> accentColor(roles)
    }
}

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
    /** A bound relic's liability voids this stat (struck through in the threat role). */
    val suppressed: Boolean = false,
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
    val itemRules = item?.let { rewardItemRules(it, reborn, itemStackAfter, liable, language) }.orEmpty()
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
            weapon != null -> weapon.tags.firstOrNull()?.localizedContent(language) ?: choiceTag
            decline -> language.text(if (choice.heirloom) OverlayRedesignText.Heirloom else OverlayRedesignText.Relics)
            else -> choiceTag
        },
        bandEnd = when {
            item != null -> language.text(OverlayRedesignText.StackCount, itemStackAfter, item.maxStacks)
            relic != null && rank != null -> language.text(OverlayRedesignText.Rank, rank)
            weapon != null -> overlayLevel(weaponLevel, language)
            else -> null
        },
        family = null,
        tags = when {
            weapon != null -> weapon.tags.map { it.localizedContent(language) }.take(2)
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
        elements = item?.elements.orEmpty(),
        chips = when {
            item != null -> buildList {
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
private fun GameplayRenderModel.liableRelics(): Set<RelicId> =
    equippedRelics.mapTo(HashSet()) { it.id }.also { liable -> build.severed.forEach { liable += it.id } }

/**
 * The item's clauses and rules as lines for the next copy: the stack magnitude and bonus it will
 * have and the build's actual values (dominant element, run scale, weapon power, shield). Positive
 * passive stats are the card's stat lines and are left out; negative ones are costs and join the
 * cost lines, so a Risk item always shows what it takes. [liable] holds the relics whose
 * liabilities apply when the nucleus reports the item suppressed; the lines they void are marked.
 */
private fun GameplayRenderModel.rewardItemRules(
    item: ItemDefinition,
    reborn: Boolean,
    stacks: Int,
    liable: Set<RelicId>,
    language: AppLanguage,
): List<RewardRuleLine> {
    val policy = content.build.policy
    val attuned = equippedRelics.any { bound -> content.relic(bound.id).attunedTags.any { it in item.tags } }
    val runScale = content.tempo.runScaleGrowth(elapsed) * content.rebirth.profile(rebirthLevel).playerPowerMultiplier
    val shield = maxShield * sqrt(runScale)
    val context = RuleTextContext(
        stackMagnitude = policy.stackMagnitude(stacks.coerceAtLeast(1)),
        dominant = build.dominant,
        secondary = build.secondary,
        flatScale = runScale,
        weaponUnit = WeaponUnitDamage * abilityPower,
        shieldCapacity = shield.takeIf { it > 0f },
        bonus = (if (reborn) policy.rebornMagnitude else 1f) * (if (attuned) 1f + policy.attunementBonus else 1f),
        stacks = stacks.coerceIn(1, ContentBounds.MAX_ITEM_STACKS),
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

/** The damage of one weapon unit; mirrors the nucleus damage pipeline. */
private const val WeaponUnitDamage = 40f

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
private fun GameplayRenderModel.suppressor(stat: StatDelta, liable: Set<RelicId>): RelicId? = when {
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
private fun ItemEffect.previewStatName(): String? = when (this) {
    ItemEffect.SHIELD_CAPACITY -> "Shield"
    ItemEffect.DAMAGE_REDUCTION -> "Damage reduction"
    ItemEffect.REGEN -> "Regeneration"
    else -> null
}

/** The first bound relic that voids part of [item]. */
private fun GameplayRenderModel.suppressor(item: ItemDefinition, liable: Set<RelicId>): RelicId? =
    item.stats.firstNotNullOfOrNull { suppressor(it, liable) } ?: item.clauses.firstNotNullOfOrNull { it.suppressor(liable) }
        ?: item.rules.firstNotNullOfOrNull { it.suppressor(liable) }

/** Forecast order on a card: suppression first, then reactions, tiers, affinity and relic links. */
private fun ForecastKind.priority(): Int = when (this) {
    ForecastKind.SUPPRESSED -> 0
    ForecastKind.REACTION -> 1
    ForecastKind.TAG_TIER -> 2
    ForecastKind.AFFINITY -> 3
    ForecastKind.CONCORD -> 4
    ForecastKind.DISCORD -> 5
    ForecastKind.DISSONANCE -> 6
    ForecastKind.HARMONY -> 7
}

/** Forecast chips of an item card, most important first; [suppressor] is the relic that voids it. */
private fun GameplayRenderModel.rewardForecastChips(
    forecasts: List<BuildForecast>,
    suppressor: RelicId?,
    language: AppLanguage,
): List<RewardChip> = forecasts.sortedBy { it.kind.priority() }.mapNotNull { rewardForecastChip(it, suppressor, language) }.distinct()

private fun GameplayRenderModel.rewardForecastChip(forecast: BuildForecast, suppressor: RelicId?, language: AppLanguage): RewardChip? {
    val policy = content.build.policy
    return when (forecast.kind) {
        ForecastKind.SUPPRESSED -> RewardChip(RewardChipKind.SUPPRESSED, language.text(OverlayRedesignText.Suppressed), relic = suppressor,
            rune = suppressor?.let(content::relic))
        ForecastKind.REACTION -> forecast.reaction?.let { id ->
            RewardChip(
                RewardChipKind.REACTION,
                if (forecast.discovered) content.build.reaction(id).name.localizedContent(language) else UNKNOWN_PAIRING_NAME,
                elements = listOf(id.first, id.second),
                discovered = forecast.discovered,
            )
        }
        ForecastKind.TAG_TIER -> forecast.tag?.let { tag ->
            RewardChip(RewardChipKind.TAG_TIER, tag.displayLabel.localizedContent(language), tag = tag,
                before = forecast.before, after = forecast.after, pips = policy.resonanceThresholds.size)
        }
        ForecastKind.AFFINITY -> forecast.element?.let { element ->
            val thresholds = policy.affinityThresholds
            val goal = thresholds.firstOrNull { it >= forecast.after } ?: forecast.after
            RewardChip(RewardChipKind.AFFINITY, element.displayLabel.localizedContent(language) + " " + forecast.after + "/" + goal,
                elements = listOf(element), before = forecast.before, after = forecast.after,
                active = policy.tier(forecast.after, thresholds) > policy.tier(forecast.before, thresholds))
        }
        ForecastKind.CONCORD, ForecastKind.DISCORD, ForecastKind.HARMONY, ForecastKind.DISSONANCE ->
            rewardLinkChip(forecast.kind.linkKind(), forecast.pairing, forecast.discovered, 1, language)
    }
}

private fun ForecastKind.linkKind(): RelicLinkKind = when (this) {
    ForecastKind.DISCORD -> RelicLinkKind.DISCORD
    ForecastKind.HARMONY -> RelicLinkKind.HARMONY
    ForecastKind.DISSONANCE -> RelicLinkKind.DISSONANCE
    else -> RelicLinkKind.CONCORD
}

private fun RelicLinkKind.priority(): Int = when (this) {
    RelicLinkKind.CONCORD -> 0
    RelicLinkKind.DISCORD -> 1
    RelicLinkKind.DISSONANCE -> 2
    RelicLinkKind.HARMONY -> 3
}

/**
 * A relic link chip: a named pairing reads "+ Name" or "− Name" ("?" until met); [count] generic
 * links of one kind compound into one multiplier ("Harmony ×1.21").
 */
private fun GameplayRenderModel.rewardLinkChip(
    kind: RelicLinkKind,
    pairing: RelicPairingId?,
    discovered: Boolean,
    count: Int,
    language: AppLanguage,
): RewardChip {
    val policy = content.build.policy
    val name = pairing?.let { if (discovered) content.build.pairing(it).name.localizedContent(language) else UNKNOWN_PAIRING_NAME }
    return when (kind) {
        RelicLinkKind.CONCORD -> RewardChip(RewardChipKind.CONCORD, "+ " + (name ?: UNKNOWN_PAIRING_NAME), discovered = discovered)
        RelicLinkKind.DISCORD -> RewardChip(RewardChipKind.DISCORD, "− " + (name ?: UNKNOWN_PAIRING_NAME), discovered = discovered)
        RelicLinkKind.HARMONY -> RewardChip(RewardChipKind.HARMONY, language.text(OverlayRedesignText.Harmony) + " " +
            overlayLinkMultiplier(policy.harmonyScale.pow(count), language), after = count)
        RelicLinkKind.DISSONANCE -> RewardChip(RewardChipKind.DISSONANCE, language.text(OverlayRedesignText.Dissonance) + " " +
            overlayLinkMultiplier(policy.dissonanceScale.pow(count), language), after = count)
    }
}

/**
 * Forecast chips of a relic card: the links it forms (named pairings first, generic ones
 * compounded), then the affinity and tag tiers it adds. The nucleus forecasts lead; a choice
 * without them reads the links of its candidate matrix.
 */
private fun GameplayRenderModel.rewardRelicForecastChips(
    forecasts: List<BuildForecast>,
    preview: RewardRelicPreview,
    language: AppLanguage,
): List<RewardChip> {
    class Link(val kind: RelicLinkKind, val pairing: RelicPairingId?, val discovered: Boolean)
    val fromForecasts = forecasts.filter { it.kind.priority() >= ForecastKind.CONCORD.priority() }
        .map { Link(it.kind.linkKind(), it.pairing, it.discovered) }
    val links = fromForecasts.ifEmpty { preview.addedLinks.map { Link(it.kind, it.pairing, it.discovered) } }
    val named = links.filter { it.pairing != null }.sortedBy { it.kind.priority() }
        .map { rewardLinkChip(it.kind, it.pairing, it.discovered, 1, language) }
    val generic = links.filter { it.pairing == null }.groupBy { it.kind }.entries.sortedBy { it.key.priority() }
        .map { (kind, group) -> rewardLinkChip(kind, null, true, group.size, language) }
    val rest = forecasts.filter { it.kind.priority() < ForecastKind.CONCORD.priority() }.sortedBy { it.kind.priority() }
        .mapNotNull { rewardForecastChip(it, null, language) }
    return (named + generic + rest).distinct()
}

private fun RewardFocus.relicAspect(): RelicAspect? = RelicAspect.entries.firstOrNull { it.name == name }

/** What picking a focus leads to, by the directed reward's choice type (artifacts, relics, weapons). */
private fun rewardFocusEffect(type: ChoiceType): OverlayRedesignText = when (type) {
    ChoiceType.ITEM -> OverlayRedesignText.FocusArtifacts
    ChoiceType.RELIC, ChoiceType.RELIC_BIND -> OverlayRedesignText.FocusRelics
    ChoiceType.TOTEM, ChoiceType.WEAPON -> OverlayRedesignText.FocusWeapons
}

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
    RelicChoiceAction.DECLINE -> RewardAction(language.text(OverlayRedesignText.Decline), RewardTone.MUTE)
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
        RelicChoiceAction.MELD, RelicChoiceAction.DECLINE -> null to current
    }
    val before = overlayRelicLinks(current)
    val after = overlayRelicLinks(candidate)
    val added = after.filter { link -> before.none(link::joinsSame) }
    val removed = before.filter { link -> after.none(link::joinsSame) }
    val kept = before.filter { link -> removed.none(link::joinsSame) }
    val slotRelic = slot?.let(equippedRelics::getOrNull)
    val relicAction = rewardRelicAction(action, relicId?.let(::relicRank) ?: 0, slotRelic?.rank, equippedRelics.size, policy, language)
    // The relic the preview is about: the one entering the matrix, or the bound one a meld raises.
    val subject = when (action) {
        RelicChoiceAction.MELD_TARGET -> slotRelic?.id
        RelicChoiceAction.ACQUIRE, RelicChoiceAction.REPLACE -> relicId?.takeIf { target != null }
        else -> null
    }
    // The Hollow Crown's weakening is its liability: it lingers through severance, and replacing
    // the Crown severs it. The nucleus reports the current multipliers when it has compiled them.
    val crownSevered = build.severed.any { it.id == RelicId.HOLLOW_CROWN }
    val crownSeveredAfter = crownSevered || (action == RelicChoiceAction.REPLACE && slot?.let(current::getOrNull) == RelicId.HOLLOW_CROWN)
    fun upsideNow(index: Int): Float {
        val id = current.getOrNull(index) ?: return 1f
        return if (build.relicUpsides.isNotEmpty()) build.relicUpside(id) else overlayUpsideMultiplier(current, index, content, crownSevered)
    }
    fun upsideAfter(index: Int): Float = overlayUpsideMultiplier(candidate, index, content, crownSeveredAfter)
    fun name(id: RelicId) = content.relic(id).name.localizedContent(language)
    /** A link row about the partner relic in slot [partner]: its name, the link and its multiplier now and after. */
    fun linkRow(link: OverlayRelicLink, sign: String, tone: RewardTone, partner: Int?): RewardPreviewRow {
        val generic = link.pairing == null
        val partnerId = partner?.let { candidate.getOrNull(it) ?: current.getOrNull(it) }
        return RewardPreviewRow(
            sign = sign,
            title = link.overlayName(content, language),
            detail = link.overlayDescription(content, language),
            tone = tone,
            color = link.overlayColor(),
            kicker = partnerId?.let(::name),
            before = if (generic && partner != null) overlayLinkMultiplier(upsideNow(partner), language) else null,
            after = if (generic && partner != null) overlayLinkMultiplier(upsideAfter(partner), language) else null,
            unknown = !link.discovered,
        )
    }
    fun partnerOf(link: OverlayRelicLink, self: Int?): Int? = link.slots.firstOrNull { it != self } ?: link.slots.firstOrNull()
    val rows = buildList {
        if (action == RelicChoiceAction.REPLACE && slotRelic != null) {
            val lost = content.relic(slotRelic.id)
            add(RewardPreviewRow("−", lost.name.localizedContent(language), lost.upsideText(language, slotRelic.rank), RewardTone.THREAT))
            // Severance: the replaced relic's liability lingers after it leaves.
            add(RewardPreviewRow("=", language.text(OverlayRedesignText.CostStays, overlayDecimal(content.build.policy.severanceSeconds, language)),
                lost.liabilityText(language), RewardTone.THREAT))
        }
        if (action == RelicChoiceAction.MELD_TARGET && slotRelic != null) {
            val target = content.relic(slotRelic.id)
            if (slotRelic.rank < policy.maxRank) {
                // The panel shows the whole upside at the next rank; the row shows only the values that grow.
                val (before, after) = target.rankValueChange(language, slotRelic.rank, slotRelic.rank + 1)
                add(RewardPreviewRow("+", language.text(OverlayRedesignText.Rank, slotRelic.rank + 1),
                    if (after == null) target.upsideText(language, slotRelic.rank + 1) else null, RewardTone.YOU, before = before, after = after))
            } else {
                add(RewardPreviewRow("+", relicAction.label, language.text(OverlayRedesignText.SalvageEffect), RewardTone.MUTE))
            }
        }
        // The subject's own upside multiplier, now and after the choice, when its links change it.
        if (subject != null && target != null) {
            val now = current.indexOf(subject).takeIf { it >= 0 }?.let(::upsideNow) ?: 1f
            val next = upsideAfter(target)
            if (now != next || next != 1f) {
                val (sign, tone) = when {
                    next > now -> "+" to RewardTone.YOU
                    next < now -> "−" to RewardTone.THREAT
                    else -> "=" to RewardTone.FIXED
                }
                add(RewardPreviewRow(sign, language.text(OverlayRedesignText.Upside), null, tone,
                    before = overlayLinkMultiplier(now, language), after = overlayLinkMultiplier(next, language)))
            }
        }
        // Losing interference is a gain; forming it is a loss.
        removed.forEach { link ->
            add(if (link.interferes) linkRow(link, "+", RewardTone.YOU, partnerOf(link, slot)) else linkRow(link, "−", RewardTone.THREAT, partnerOf(link, slot)))
        }
        added.forEach { link ->
            add(if (link.interferes) linkRow(link, "−", RewardTone.THREAT, partnerOf(link, target)) else linkRow(link, "+", RewardTone.FIXED, partnerOf(link, target)))
        }
        // Kept links of the subject (a meld keeps every link of its relic).
        kept.filter { link -> subject == null || subject in link.relics }.forEach { link ->
            add(linkRow(link, "=", RewardTone.FIXED, partnerOf(link, target)).copy(before = null, after = null))
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
        addedNames = added.map { it.overlayName(content, language) },
        removedLinks = removed,
        rows = rows,
        panel = panelRelic?.let { relic ->
            rewardRelicPanel(relic, panelRank ?: 1, candidate.filterNotNull(), language, choice.heirloom)
        },
        kicker = choice.title.localizedContent(language),
        primaryLabel = relicAction.label,
        incoming = when (action) {
            RelicChoiceAction.ACQUIRE, RelicChoiceAction.REPLACE -> relicId?.let(content::relic)
            else -> null
        },
    )
}

/** A totem offering row; [description] is the card's effect line (never the game's step text). */
private fun GameplayRenderModel.rewardTotemRow(
    choice: ChoiceOption,
    weapon: WeaponDefinition?,
    description: String,
    language: AppLanguage,
): RewardTotemRow {
    val mastery = { level: Int -> content.weaponMasteryForLevel(level).displayLabel.localizedContent(language) }
    val focus = choice.rewardFocus
    return when {
        focus != null -> RewardTotemRow(
            icon = focus.overlayIcon(), kind = choice.tag.localizedContent(language), kindTone = RewardTone.MUTE,
            meta = null, name = choice.title.localizedContent(language), description = description,
            level = null, fromLevel = 0, toLevel = 0, mastery = null,
        )
        choice.totemAction == TotemAction.AMPLIFY_CURRENT -> RewardTotemRow(
            icon = this.weapon.overlayIcon(), kind = language.text(OverlayRedesignText.UpgradeKind), kindTone = RewardTone.MUTE,
            meta = language.text(OverlayRedesignText.Equipped), name = currentWeaponDefinition.name.localizedContent(language),
            description = description,
            level = weaponLevel + 1, fromLevel = weaponLevel, toLevel = weaponLevel + 1, mastery = mastery(weaponLevel + 1),
        )
        choice.totemAction == TotemAction.CHANGE_WEAPON -> RewardTotemRow(
            icon = KkIcon.SYSTEM_REROLL, kind = language.text(OverlayRedesignText.ChangeKind), kindTone = RewardTone.YOU,
            meta = null, name = choice.title.localizedContent(language), description = description,
            level = weaponLevel, fromLevel = weaponLevel, toLevel = weaponLevel, mastery = mastery(weaponLevel),
        )
        weapon != null -> RewardTotemRow(
            icon = weapon.id.overlayIcon(), kind = language.text(OverlayRedesignText.NewKind), kindTone = RewardTone.YOU,
            meta = null, name = weapon.name.localizedContent(language), description = description,
            level = weaponLevel, fromLevel = weaponLevel, toLevel = weaponLevel, mastery = mastery(weaponLevel),
        )
        else -> RewardTotemRow(
            icon = KkIcon.SYSTEM_DATA, kind = choice.tag.localizedContent(language), kindTone = RewardTone.MUTE, meta = null,
            name = choice.title.localizedContent(language), description = description,
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

/** A passive stat line of an offered item: [scale] × the per-copy amount; negative amounts are costs. */
private fun StatDelta.presentation(scale: Float, language: AppLanguage): RewardStatPresentation {
    val value = amount * scale
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

private fun GameplayRenderModel.rewardConnections(choice: ChoiceOption, language: AppLanguage): List<RewardConnection> = buildList {
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
