// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import androidx.compose.ui.graphics.Color
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.RuleLine
import kinetickk.ball.content.api.SkillDefinition
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.content.api.defaultSkillDefinitions
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.content.api.rankLines
import kinetickk.ball.gameplay.interaction.rewards.activeRankNumbers
import kinetickk.ball.profile.api.rank
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkRolePalette

internal sealed interface CodexIcon {
    data class Item(val definition: ItemDefinition, val stack: Int, val reborn: Boolean = false, val suppressed: Boolean = false) : CodexIcon
    data class Relic(val definition: RelicDefinition, val rank: Int?, val severed: Boolean = false) : CodexIcon
    data class Weapon(val id: WeaponId) : CodexIcon
    data class Shape(val id: CoreShape) : CodexIcon
    /**
     * A roster enemy; [locked] while the profile's Rebirth rank is below its minimum; [hidden] for a secret boss not
     * yet discovered (its shape stays secret).
     */
    data class Enemy(val type: EnemyType, val locked: Boolean, val hidden: Boolean = false) : CodexIcon
    /** Content gated behind a Rebirth tier the player has not reached; its identity stays hidden. */
    data class Locked(val rebirth: Int) : CodexIcon
    data class Reaction(val id: ReactionId, val discovered: Boolean, val reachable: Boolean) : CodexIcon
    data class Break(val element: StatusElement) : CodexIcon
    data class Pairing(
        val first: RelicDefinition,
        val second: RelicDefinition,
        val kind: CodexLinkKind,
        val discovered: Boolean,
        val bound: Boolean,
    ) : CodexIcon
    data class Conflict(val item: ItemDefinition?, val relics: List<RelicDefinition>) : CodexIcon
    data class Tag(val tag: BuildTag, val points: Int?, val tier: Int) : CodexIcon
    data class Affinity(val element: StatusElement, val affinity: Int?, val tier: Int, val dominant: Boolean) : CodexIcon
    data class Key(val key: RelicKey) : CodexIcon
    /** A world place or secret: its glyph in Bone once [discovered], muted over a hatch before. */
    data class World(val id: WorldDiscovery, val discovered: Boolean) : CodexIcon
    data object Unknown : CodexIcon
    data object Empty : CodexIcon
}

/** How two relics interact: a named pairing (amplification or interference) or the key rule. */
internal enum class CodexLinkKind {
    CONCORD, DISCORD, HARMONY, DISSONANCE;

    val interferes: Boolean get() = this == DISCORD || this == DISSONANCE
    val named: Boolean get() = this == CONCORD || this == DISCORD
}

/** The Pairings tab sections, in order. */
internal enum class CodexPairingSection { CONCORDS, HARMONY, DISSONANCE, INCOMPATIBLE }

/** Structured detail of an entry beyond its plain paragraphs, drawn by the detail panel. */
internal sealed interface CodexDetail {
    /** An item's sheet: its passive [lines] and, for an active item, its [active] part. */
    data class Item(
        val item: ItemDefinition,
        val lines: List<RuleLine>,
        val stack: Int,
        val reborn: Boolean,
        val suppressed: Boolean,
        val active: CodexActivePart? = null,
    ) : CodexDetail

    /** [upsides] holds the upside at every rank (index rank − 1); [rank] is the one shown first. */
    data class Relic(
        val relic: RelicDefinition,
        val upsides: List<String>,
        val rank: Int,
        val ranked: Boolean,
        val liability: String,
        /** Keys of this relic that a bound relic opposes. */
        val opposedBound: Set<RelicKey>,
        val pairings: List<CodexPairingNote>,
        val severed: Boolean,
    ) : CodexDetail

    /** [feeds] are the reactions the [residue] can trigger next (the chain), each named once discovered. */
    data class Reaction(
        val id: ReactionId,
        val discovered: Boolean,
        val residue: StatusElement?,
        val feeds: List<CodexFeed> = emptyList(),
    ) : CodexDetail
    data class Break(val element: StatusElement) : CodexDetail
    data class Pairing(
        val first: RelicDefinition,
        val second: RelicDefinition,
        val kind: CodexLinkKind,
        val discovered: Boolean,
        /** Shared keys as single groups, opposed keys as pairs. */
        val keys: List<List<RelicKey>>,
    ) : CodexDetail
    data class Conflict(val item: ItemDefinition?, val relics: List<RelicDefinition>) : CodexDetail
    data class Tag(val tag: BuildTag, val tiers: List<String>, val thresholds: List<Int>, val points: Int?) : CodexDetail
    data class Affinity(
        val element: StatusElement,
        val tiers: List<String>,
        val thresholds: List<Int>,
        val affinity: Int?,
        val dominant: Boolean,
    ) : CodexDetail
    data class Key(val key: RelicKey, val opposed: List<RelicKey>, val relics: List<RelicDefinition>, val hidden: Int) : CodexDetail

    /**
     * A weapon's typed sheet: its mastery forms at [formLevels], its [signature] character (active
     * with the run's character on the equipped weapon, or the next run's outside a run) and twist,
     * the Relics that carry its key ([bound] to the equipped weapon this run, else outside a run the
     * [heirlooms] candidates) and the run's points in each of its tags ([tagPointsByTag], equipped only).
     */
    data class Weapon(
        val weapon: WeaponDefinition,
        val signature: String,
        val signatureActive: Boolean,
        val formLevels: List<Int>,
        val bound: List<RelicDefinition>,
        val heirlooms: List<RelicDefinition>,
        val tagPointsByTag: Map<BuildTag, Int>?,
        val rebirthLocked: Boolean,
    ) : CodexDetail {
        /** The run's points across both tags (the resonance input), or null when no run lights them. */
        val tagPoints: Int? get() = tagPointsByTag?.values?.sum()
    }
}

/**
 * The active part of an active item's sheet, from its effect's content: every rank's numbers ([ranks], rank I first:
 * its rank label and its cooldown and values as one comma-joined line, as the reward card writes them, each value held
 * on its label's line), what rank III adds ([rankThree]) and the rank the run holds ([held], 0 when not owned).
 */
internal data class CodexActivePart(val effect: SkillId, val ranks: List<CodexActiveRank>, val rankThree: String, val held: Int)

/** One rank of an active effect: its label ("Rank 2") and its numbers ("Cooldown 12 s, Radius 3"). */
internal data class CodexActiveRank(val label: String, val numbers: String)

/** The active part of [item] (null for a passive item) read from [skills] in [language], with the run's [held] rank. */
internal fun codexActivePart(item: ItemDefinition, skills: List<SkillDefinition>, held: Int, language: AppLanguage): CodexActivePart? {
    val effect = item.active ?: return null
    val skill = skills[effect.ordinal]
    val ranks = (1..ContentBounds.MAX_SKILL_RANK).map { rank ->
        CodexActiveRank(skill.rankLines(rank, language).first(), skill.activeRankNumbers(rank, language))
    }
    return CodexActivePart(effect, ranks, skill.rankThree.localizedContent(language), held)
}

/** The shipped effect table, for entries built without a catalog snapshot (the snapshot's own default). */
internal val DefaultSkills: List<SkillDefinition> by lazy { defaultSkillDefinitions() }

/** A reaction a residue can feed next; [name] is null until that reaction is discovered. */
internal data class CodexFeed(val id: ReactionId, val name: String?)

/** A named pairing of a relic with a discovered partner; [name] is null until the pairing is discovered. */
internal data class CodexPairingNote(val partner: RelicDefinition, val kind: CodexLinkKind, val name: String?)

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
    /** True for the equipped form or a bound link (shown with color, not a glyph). */
    val active: Boolean = false,
    val detail: CodexDetail? = null,
    /** What tells this entry apart when read aloud (two relic names, two input elements); never drawn. */
    val spoken: String? = null,
) {
    /** The facts as "label value" lines (accessibility and search summaries). */
    val availability: String
        get() = facts.joinToString("\n") { "${it.label} ${it.value}" }

    /** Short status value of the entry (its Status fact), if it has one. */
    val status: String?
        get() = facts.firstOrNull { it.isStatus }?.value

    /** Accessible summary: title, kind, what tells it apart, amount and facts as separate phrases, each once. */
    val summary: String
        get() = listOf(title, kind, spoken.orEmpty(), quantity, availability.replace("\n", ", "))
            .filter { it.isNotBlank() && it != CODEX_NONE }
            .distinct()
            .joinToString(", ") + if (!discovered && help != null) ", $help" else ""
}

/** Placeholder for "no value" in entry fields; never rendered as a label. */
internal const val CODEX_NONE = "—"

internal fun codexStatus(language: AppLanguage, value: String, info: String? = null): CodexFact =
    CodexFact(language.text(SessionText.STATUS), value, info, isStatus = true)

/**
 * An item's hue is its first element in the player's color-vision mode; items without one deal
 * Kinetic damage, in the preset's volt.
 */
internal fun codexItemColor(item: ItemDefinition, roles: KkRolePalette = KkRolePalette.Default): Color =
    item.elements.firstOrNull()?.let(roles::of) ?: roles.kinetic

/** The entry's own hue in the player's color-vision mode: elemental entries follow the palette. */
internal fun CodexEntry.tone(roles: KkRolePalette): Color = when (val icon = icon) {
    is CodexIcon.Item -> codexItemColor(icon.definition, roles)
    is CodexIcon.Relic -> relicAspectColor(icon.definition.aspect, roles)
    is CodexIcon.Break -> roles.of(icon.element)
    is CodexIcon.Affinity -> roles.of(icon.element)
    is CodexIcon.Reaction -> roles.of(icon.id.first)
    else -> color
}

/** Redesign weapon icon (`icons.json weapons.*`) for a weapon id. */
internal fun codexWeaponIcon(id: WeaponId): KkIcon = requireNotNull(KkIcon.byKey("weapons." + id.name.lowercase())) {
    "Missing weapon icon for $id"
}

internal fun codexNumber(value: Float, language: AppLanguage = AppLanguage.English): String {
    val number = ((value * 100f).toInt() / 100f).toString()
    return when (language) {
        AppLanguage.English -> number
        AppLanguage.Russian -> number.replace('.', ',')
    }
}
