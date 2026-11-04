// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.RunBoonKind
import kinetickk.ball.content.api.RebirthMutator
import kinetickk.ball.content.api.RuleLineKind
import kinetickk.ball.content.api.SignalOption
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.liabilityText
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.content.api.upsideText
import kinetickk.ball.gameplay.interaction.canvas.look.OverlayRelicLink
import kinetickk.ball.gameplay.interaction.canvas.look.badgeState
import kinetickk.ball.gameplay.interaction.canvas.look.OverlayRelicSlot
import kinetickk.ball.gameplay.interaction.canvas.look.overlayColor
import kinetickk.ball.gameplay.interaction.canvas.look.overlayLevel
import kinetickk.ball.gameplay.interaction.canvas.look.overlayName
import kinetickk.ball.gameplay.interaction.canvas.look.overlayRelicLinks
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.ball.gameplay.interaction.localization.RunVarietyText
import kinetickk.ball.gameplay.nucleus.render.BuildForecast
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.KkApexState
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.label
import kinetickk.ball.gameplay.interaction.canvas.look.*

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
    /** The run's level cap (the badge turns APEX there) and the Apex state it shows. */
    val levelCap: Int = Int.MAX_VALUE,
    val apex: KkApexState = KkApexState.NONE,
    /** Screen position (px) the cards are dealt from: the Core. */
    val dealOrigin: Offset? = null,
    /** Current relic matrix for relic choices. */
    val relicMatrix: RewardRelicMatrix? = null,
    /** Weapon totem copy (title and the mastery rules behind the (!) button). */
    val totem: RewardTotemHeader? = null,
    /** The relic being bound when every slot choice binds the same incoming relic. */
    val relicPanel: RewardRelicPanel? = null,
    /** An omen offer's run conditions (director pattern and Keynote), shown under its heading. */
    val conditions: RewardRunConditions? = null,
) {
    /** An Heirloom offer: gold carries its heading. */
    val heirloom: Boolean get() = cards.any { it.heirloom }
}

/**
 * The reward overlay of the current choice. [touch]: the player's input is touch, so the cards name a Z/X/C slot by its
 * number instead of its key.
 */
internal fun GameplayRenderModel.rewardPresentation(language: AppLanguage = AppLanguage.English, touch: Boolean = false): RewardPresentation {
    val kind = when (choiceType) {
        ChoiceType.TOTEM -> RewardLayoutKind.TOTEM
        ChoiceType.RELIC_BIND -> RewardLayoutKind.RELIC_BIND
        ChoiceType.ITEM, ChoiceType.WEAPON, ChoiceType.RELIC, ChoiceType.ACTIVE_SLOT -> RewardLayoutKind.CARDS
        // Omens, signal prompts and boons lay out as cards.
        ChoiceType.OMEN, ChoiceType.SIGNAL, ChoiceType.BOON -> RewardLayoutKind.CARDS
    }
    val relicChoice = choiceType == ChoiceType.RELIC || choiceType == ChoiceType.RELIC_BIND
    val incoming = choices.firstOrNull { it.relicAction == RelicChoiceAction.REPLACE }?.relicId?.let(content::relic)
    return RewardPresentation(
        heading = if (choices.any { it.heirloom && it.relicId != null }) language.text(OverlayRedesignText.Heirloom)
            else rewardRunVarietyHeading(language) ?: rewardDimensionHeading(language)
                ?: rewardHeading(choiceType, language),
        subtitle = "",
        cards = choices.mapIndexed { index, choice -> rewardCardPresentation(choice, index, language, touch) },
        titleAccent = Kk.Bone,
        rerollAccent = Kk.Bone,
        rerollsRemaining = rerollsRemaining,
        kind = kind,
        level = level.takeIf { choiceType == ChoiceType.ITEM && !directedChoice && choices.none { it.rewardFocus != null } },
        levelCap = maxLevel,
        apex = apex.badgeState(),
        // The Core on screen in device pixels (the simulation viewport is dp).
        dealOrigin = Offset(
            (coreX - cameraX + screenWidth * 0.5f) * viewportDensity,
            (coreY - cameraY + screenHeight * 0.5f) * viewportDensity,
        ),
        relicMatrix = if (relicChoice) rewardRelicMatrix(language) else null,
        totem = if (kind == RewardLayoutKind.TOTEM) RewardTotemHeader(
            title = language.text(GameplayText.WeaponTotem),
            masteryInfo = language.text(OverlayRedesignText.MasteryInfo, content.weaponMasteries
                .filter { it.minimumLevel > 1 }
                .joinToString(", ") { overlayLevel(it.minimumLevel, language) },
                (content.tempo.weaponChangeKeptLevelShare * 100f).roundToInt()),
            changeSpent = if (build.weaponChangesSpent && content.tempo.weaponChangesPerRun > 0) {
                language.text(OverlayRedesignText.WeaponChangeSpent)
            } else null,
        ) else null,
        relicPanel = incoming?.let { relic -> rewardRelicPanel(relic, 1, equippedRelics.map { it.id }, language) },
        conditions = if (choiceType == ChoiceType.OMEN) rewardRunConditions(language) else null,
    )
}

/** The relic panel of [relic] at [rank]: its keys read against the bound [partners]. */
internal fun GameplayRenderModel.rewardRelicPanel(
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
    description = relic.upsideText(language, rank, numberScale),
    liability = relic.liabilityText(language),
    relic = relic,
    values = relic.upsideValueRanges(language, rank, numberScale),
    keys = rewardRelicKeys(relic, partners, language),
    attuned = rewardAttunedChips(relic, language),
    heirloom = heirloom,
    level = rank,
)

/**
 * Where the rank-scaled values of the upside sit in [RelicDefinition.upsideText] at [numberScale]: the localized
 * template's literal parts are matched in order, and what lies between them is a value.
 */
internal fun RelicDefinition.upsideValueRanges(language: AppLanguage, rank: Int, numberScale: Double = 1.0): List<IntRange> {
    val template = upside.localizedContent(language)
    val text = upsideText(language, rank, numberScale)
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

// Android's ICU regex parser requires both literal braces to be escaped.
private val RelicPlaceholder = Regex("\\{[0-9]+\\}")

/**
 * The upside values a rank change raises, at [from] and at [to] ("15 %", "18 %"; several join with
 * a comma), each with the percent mark that follows it in the text. Null when no value changes.
 */
internal fun RelicDefinition.rankValueChange(language: AppLanguage, from: Int, to: Int, numberScale: Double): Pair<String?, String?> {
    fun values(rank: Int): List<String> {
        val text = upsideText(language, rank, numberScale)
        return upsideValueRanges(language, rank, numberScale).map { range ->
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
internal fun GameplayRenderModel.rewardRelicKeys(relic: RelicDefinition, partners: List<RelicId>, language: AppLanguage): List<RewardRelicKey> {
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
internal fun GameplayRenderModel.rewardAttunedChips(relic: RelicDefinition, language: AppLanguage): List<RewardChip> =
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
        sealed = build.meldedSlots,
        claims = build.relicClaims,
        budget = content.relicPolicy.acquisitionsPerRun,
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

/** The Totem board's title, its mastery note and, once the run's weapon change is spent, the note saying so. */
internal data class RewardTotemHeader(val title: String, val masteryInfo: String, val changeSpent: String? = null)

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

/**
 * The equipped relic matrix in slot order ([slots] has one entry per slot; null is free), the last [sealed] free slots
 * sealed by Melds, and the [claims] of the run's Relic [budget].
 */
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
    val sealed: Int = 0,
    val claims: Int = 0,
    val budget: Int = 0,
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
    /** A Meld: [targetSlot] is the slot it seals. */
    val meld: Boolean = false,
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

/**
 * A weapon totem offering row. Ticks fill [fromLevel] owned levels plus the gained ones. A weapon
 * row's [meta] names its family, element and tags; [badge] marks the run character's signature weapon.
 */
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
    val badge: String? = null,
)

/** The outcome tag of a relic offer (Bind, Meld, Salvage, Replace). */
internal data class RewardAction(val label: String, val tone: RewardTone)

/** What a chip on a reward card marks: identity beside the icon, or a build forecast in the footer. */
internal enum class RewardChipKind {
    TAG, ATTUNED, RISK, REBORN, HEIRLOOM, SUPPRESSED, REACTION, TAG_TIER, AFFINITY, CONCORD, DISCORD, HARMONY, DISSONANCE,
    /** A weapon offer whose signature character is the run's ([RewardChip.core] draws its form). */
    SIGNATURE,
    /** A weapon offer's element (no glyph for a Kinetic weapon). */
    ELEMENT,
    /**
     * The Z/X/C slot an active item binds to or ranks (or, on the slot prompt, the slot a card replaces): the
     * player's key for it as a key cap after the label ([RewardChip.keyCap]), or the slot's number in the label on touch.
     */
    ACTIVE,
    /** An unowned active item offered while every slot holds another active: taking it asks which one to replace. */
    REPLACES_ACTIVE,
    /** The run's director pattern on an omen offer: the personality's sigil ([RewardChip.icon]) and name. */
    PATTERN,
    /** The run's Keynote on an omen offer: its tag's mark, the element's mark in its hue, and both names. */
    KEYNOTE,
    /** The Rebirth mutator an omen's liability adds for the run ([RewardChip.mutator] draws its sigil). */
    MUTATOR,
    /** What a signal card takes: a price, a stake or the integrity it costs, by its mark ([RewardChip.icon]) and number. */
    COST,
}

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
    /** The character a SIGNATURE chip names. */
    val core: CoreShape? = null,
    /** The key an ACTIVE chip's slot is bound to, drawn as a key cap after the label (null on touch). */
    val keyCap: String? = null,
    /** The mark of a PATTERN or COST chip. */
    val icon: KkIcon? = null,
    /** The mutator a MUTATOR chip names. */
    val mutator: RebirthMutator? = null,
) {
    /** What the chip reads as: its label, then its key cap. */
    val spoken: String get() = if (keyCap == null) label else "$label $keyCap"
}

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
    /** Identity chips beside the icon: an active item's slot and lane, Risk, Reborn, Heirloom, tags or a relic's attuned tags. */
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
    /** A signal card the run cannot take now (HATCH = locked): it cannot be picked or focused. */
    val locked: Boolean = false,
    /** A Blessing or a Pact card: the kind gives its hue and face ([rewardBoonCardLook]). */
    val boonKind: RunBoonKind? = null,
) {
    /**
     * A relic decline, the active slot prompt's keep card, No omen, a signal's Ignore or the Pact offer's Refuse card:
     * each reads as declining the offer.
     */
    val decline: Boolean get() = choice.relicAction == RelicChoiceAction.DECLINE || choice.keepsActiveLoadout ||
        choice.type == ChoiceType.OMEN && choice.omenId == null || choice.signalOption == SignalOption.IGNORE ||
        choice.type == ChoiceType.BOON && choice.boonId == null
    val reborn: Boolean get() = choice.reborn && item != null
    val heirloom: Boolean get() = choice.heirloom && relic != null

    /**
     * The card hue under the player's color-vision preset: a relic takes its aspect (an element's
     * hue, the preset's volt for Kinetic, gold for Sovereign), a direction its aspect or, without an
     * element, Kinetic, and so does a meld (two forms joined, no element).
     */
    fun accentColor(roles: KkRolePalette): Color {
        relic?.let { return it.aspect.overlayColor(roles) }
        boonKind?.let { kind ->
            if (kind == RunBoonKind.ASPECT) elements.firstOrNull()?.let { return roles.element(it.ordinal) }
            return kind.hue(roles)
        }
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

internal fun rewardCardIsCompact(widthDp: Float, heightDp: Float): Boolean =
    widthDp < 180f || heightDp < 240f

internal fun rewardHeading(
    type: ChoiceType,
    language: AppLanguage = AppLanguage.English,
): String = when (type) {
    ChoiceType.ITEM -> language.text(GameplayText.ChooseArtifact)
    ChoiceType.TOTEM -> language.text(GameplayText.TotemResonance)
    ChoiceType.WEAPON -> language.text(GameplayText.WeaponSynchronization)
    ChoiceType.RELIC -> language.text(GameplayText.RelicIntercept)
    ChoiceType.RELIC_BIND -> language.text(GameplayText.RelicRebind)
    ChoiceType.ACTIVE_SLOT -> language.text(GameplayText.ChooseActiveSlot)
    ChoiceType.OMEN -> language.text(RunVarietyText.ChooseOmen)
    // A signal prompt is headed by its kind (rewardRunVarietyHeading) and a boon offer by its layer (rewardDimensionHeading).
    ChoiceType.SIGNAL, ChoiceType.BOON -> language.text(GameplayText.ChooseArtifact)
}
