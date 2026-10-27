// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import androidx.compose.ui.graphics.Color
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.ConversionKind
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.EnemyDefinition
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.RelicPairingDefinition
import kinetickk.ball.content.api.RelicPairingKind
import kinetickk.ball.content.api.RuleLine
import kinetickk.ball.content.api.RuleTextContext
import kinetickk.ball.content.api.SkillDefinition
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.ball.content.api.WeaponCadence
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.defaultSkillDefinitions
import kinetickk.ball.content.api.lifetimeMatterOfferLevel
import kinetickk.ball.content.api.liabilityText
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.content.api.rankLines
import kinetickk.ball.content.api.ruleLines
import kinetickk.ball.content.api.upsideText
import kinetickk.ball.gameplay.api.GameplayBuildSummaryProjection
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.rank
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.localization.CodexText
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.roundToInt

internal sealed interface CodexIcon {
    data class Item(val definition: ItemDefinition, val stack: Int, val reborn: Boolean = false, val suppressed: Boolean = false) : CodexIcon
    data class Relic(val definition: RelicDefinition, val rank: Int?, val severed: Boolean = false) : CodexIcon
    data class Weapon(val id: WeaponId) : CodexIcon
    data class Shape(val id: CoreShape) : CodexIcon
    /** A roster enemy; [locked] while the profile's Rebirth rank is below its minimum. */
    data class Enemy(val type: EnemyType, val locked: Boolean) : CodexIcon
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
 * its rank label and its cooldown and values as one comma-joined line, as the reward card writes them), what rank III
 * adds ([rankThree]) and the rank the run holds ([held], 0 when not owned).
 */
internal data class CodexActivePart(val effect: SkillId, val ranks: List<CodexActiveRank>, val rankThree: String, val held: Int)

/** One rank of an active effect: its label ("Rank 2") and its numbers ("Cooldown 12 s, Radius 3"). */
internal data class CodexActiveRank(val label: String, val numbers: String)

/** The active part of [item] (null for a passive item) read from [skills] in [language], with the run's [held] rank. */
internal fun codexActivePart(item: ItemDefinition, skills: List<SkillDefinition>, held: Int, language: AppLanguage): CodexActivePart? {
    val effect = item.active ?: return null
    val skill = skills[effect.ordinal]
    val ranks = (1..ContentBounds.MAX_SKILL_RANK).map { rank ->
        val lines = skill.rankLines(rank, language)
        CodexActiveRank(lines.first(), lines.drop(1).joinToString(", "))
    }
    return CodexActivePart(effect, ranks, skill.rankThree.localizedContent(language), held)
}

/** The shipped effect table, for entries built without a catalog snapshot (the snapshot's own default). */
private val DefaultSkills: List<SkillDefinition> by lazy { defaultSkillDefinitions() }

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
 * Run level from which ordinary offers include [item]: 1 once lifetime Matter has unlocked its level; an active item
 * always waits for the run level itself ([lifetimeMatterOfferLevel]).
 */
internal fun codexOfferedFromLevel(item: ItemDefinition, lifetimeMatter: Long): Int =
    if (!item.isActive && item.unlockLevel <= lifetimeMatterOfferLevel(lifetimeMatter)) 1 else item.unlockLevel

/**
 * The rule-line context of [item] as owned now, as the reward card writes it: the current stacks
 * (one copy when not owned), the Reborn and relic attunement multipliers, and the run's dominant
 * and secondary elements. Run scales the Codex cannot see stay as the basis phrases.
 */
internal fun codexRuleContext(
    item: ItemDefinition,
    stack: Int,
    reborn: Boolean,
    catalog: UiCatalogSnapshot?,
    build: GameplayBuildSummaryProjection? = null,
): RuleTextContext {
    val policy = catalog?.build?.policy
    val copies = stack.coerceIn(1, item.maxStacks.coerceIn(1, ContentBounds.MAX_ITEM_STACKS))
    val attuned = catalog != null && build != null &&
        build.relics.any { bound -> catalog.relic(bound.id).attunedTags.any { it in item.tags } }
    val elements = build?.elements.orEmpty().filter { it.affinity > 0 }
    val dominant = elements.firstOrNull { it.dominant }?.element
    return RuleTextContext(
        stackMagnitude = policy?.stackMagnitude(copies) ?: 1f,
        dominant = dominant,
        secondary = elements.filter { it.element != dominant }.maxByOrNull { it.affinity }?.element,
        stacks = copies,
        bonus = (if (reborn && policy != null) policy.rebornMagnitude else 1f) *
            (if (attuned && policy != null) 1f + policy.attunementBonus else 1f),
    )
}

internal fun codexItemEntry(
    item: ItemDefinition,
    model: CodexRenderModel,
    lifetimeMatter: Long,
    language: AppLanguage = AppLanguage.English,
    catalog: UiCatalogSnapshot? = null,
): CodexEntry {
    if (!model.isDiscovered(item.id)) return unknownEntry("item/${item.id}", language)
    val stack = model.itemStack(item.id)
    val build = model.runStacks.build
    val reborn = build?.rebornItemIds?.contains(item.id) == true
    val suppressed = build?.suppressedItemIds?.contains(item.id) == true
    // Every part waits for a skill or for a weapon of another family (never the relics' doing).
    val dormant = build?.dormantItemIds?.contains(item.id) == true
    // The run's draft deck left it out: no ordinary offer of this run shows it.
    val outOfDeck = build?.outOfDeckItemIds?.contains(item.id) == true
    val lines = item.ruleLines(language, codexRuleContext(item, stack, reborn, catalog, build))
    val facts = buildList {
        // Offers depend on the run: the stack limit, then the game's current eligibility.
        when {
            suppressed -> add(codexStatus(language, language.text(CodexText.SUPPRESSED), language.text(CodexText.SUPPRESSED_EFFECT)))
            dormant -> add(codexStatus(language, language.text(CodexText.DORMANT), language.text(CodexText.DORMANT_HELP)))
            outOfDeck -> add(codexStatus(language, language.text(CodexText.NOT_IN_RUN), language.text(CodexText.NOT_IN_RUN_HELP)))
            stack >= item.maxStacks -> add(codexStatus(language, language.text(SessionText.STACK_LIMIT)))
            build != null && item.id in build.eligibleItemIds -> add(codexStatus(language, language.text(SessionText.NEXT_ACQUISITION)))
            build != null -> add(codexStatus(language, language.text(SessionText.OFFER_LIMIT), language.text(SessionText.OFFER_LIMIT_HELP)))
        }
        add(CodexFact(language.text(SessionText.OFFERS_FROM_LEVEL), language.text(SessionText.LEVEL_SHORT, codexOfferedFromLevel(item, lifetimeMatter)),
            language.text(if (item.isActive) CodexText.ACTIVE_OFFERS_HELP else SessionText.OFFERS_FROM_LEVEL_HELP)))
    }
    val active = codexActivePart(item, catalog?.skills ?: DefaultSkills, stack, language)
    val description = buildString {
        append(item.description.localizedContent(language))
        if (active != null) {
            append("\n\n").append(language.text(CodexText.ACTIVE_PART))
            active.ranks.forEach { append("\n").append(it.label).append(": ").append(it.numbers) }
            append("\n").append(active.rankThree)
            if (lines.isNotEmpty()) append("\n\n").append(language.text(CodexText.PASSIVE_PART))
        }
        if (lines.isNotEmpty()) append(if (active != null) "\n" else "\n\n").append(lines.joinToString("\n") { it.text })
    }
    return CodexEntry(
        "item/${item.id}", item.name.localizedContent(language), description,
        item.rarity.displayLabel.localizedContent(language), "$stack/${item.maxStacks}", facts,
        CodexIcon.Item(item, stack, reborn, suppressed), codexItemColor(item), true, item.rarity.rank, item.id in model.newItemIds,
        detail = CodexDetail.Item(item, lines, stack, reborn, suppressed, active),
        spoken = if (active != null) language.text(CodexText.ACTIVE_ITEM) else null,
    )
}

/** A cell for content behind a Rebirth tier not reached yet: the tier, never the identity. */
internal fun codexLockedEntry(key: String, rebirth: Int, language: AppLanguage): CodexEntry = CodexEntry(
    key, language.text(SessionText.REBIRTH_LEVEL, rebirth), "", CODEX_NONE, CODEX_NONE,
    listOf(codexStatus(language, language.text(SessionText.LOCKED))), CodexIcon.Locked(rebirth), Kk.Gold,
    discovered = false, help = language.text(CodexText.REBIRTH_GATE_HELP),
)

/**
 * A weapon, always named with its typed facts (family, element, Relic key, scaling input, cadence,
 * signature character) and, while not owned, its unlock cost and Rebirth rank; an unowned weapon
 * keeps the locked look. The detail reads its live synergy from the run (its character, bound
 * Relics and tag points, active on the equipped weapon only) or, outside a run, from the Profile
 * (the next run's character and the heirloom candidates).
 */
internal fun codexWeaponEntry(
    weapon: WeaponDefinition,
    model: CodexRenderModel,
    progress: HomeProgressProjection,
    catalog: UiCatalogSnapshot,
    language: AppLanguage = AppLanguage.English,
    allowCurrentRun: Boolean = true,
): CodexEntry {
    val build = model.runStacks.build
    val equipped = build?.weapon == weapon.id
    val unlocked = weapon.id in progress.loadout.unlockedWeapons
    val owned = unlocked || (allowCurrentRun && equipped)
    val rebirthLocked = !unlocked && weapon.minimumRebirth > progress.rebirthProgress.rank
    val signature = catalog.coreShapes.firstOrNull { it.id == weapon.signature }?.displayName?.localizedContent(language)
        ?: weapon.signature.name
    val facts = buildList {
        add(codexStatus(language, language.text(when {
            !unlocked -> SessionText.LOCKED
            weapon.id == progress.loadout.selectedWeapon -> SessionText.START_WEAPON_SELECTED
            else -> SessionText.START_WEAPON_UNLOCKED
        })))
        if (!unlocked) add(CodexFact(language.text(SessionText.UNLOCK_COST), weapon.permanentUnlockCost.toString()))
        if (!unlocked && weapon.minimumRebirth > 0) {
            add(CodexFact(language.text(CodexText.UNLOCKS_AT), language.text(CodexText.REBORN_GATE, weapon.minimumRebirth)))
        }
        if (equipped && build != null) {
            add(CodexFact(language.text(SessionText.WEAPON_LEVEL), language.text(SessionText.LEVEL_SHORT, build.weaponLevel)))
            add(CodexFact(language.text(SessionText.MASTERY), build.mastery.localizedContent(language)))
            // The permanent Honing the run applies to the weapon in hand.
            add(CodexFact(language.text(CodexText.HONING), language.text(SessionText.LEVEL_SHORT, build.weaponUpgradeLevel)))
        }
        add(CodexFact(language.text(CodexText.WEAPON_FAMILY), weapon.family.displayLabel.localizedContent(language)))
        add(CodexFact(language.text(CodexText.WEAPON_ELEMENT), codexWeaponElementLabel(weapon, language)))
        add(CodexFact(language.text(CodexText.WEAPON_KEY), weapon.key.displayLabel.localizedContent(language)))
        add(CodexFact(language.text(CodexText.WEAPON_SCALING), weapon.scaling.displayLabel.localizedContent(language)))
        add(CodexFact(language.text(CodexText.WEAPON_CADENCE), codexCadenceLabel(weapon.cadence, language)))
        add(CodexFact(language.text(CodexText.WEAPON_SIGNATURE), signature))
    }
    // While a run is on, Gameplay applies these bonuses to the equipped weapon alone: its character, bound
    // Relics and tag points light only that weapon, and the heirloom choice is no longer the sheet's to offer.
    // Outside a run: the next run's character and the heirloom candidates.
    val lit = build?.takeIf { equipped }
    val character = if (build == null) progress.loadout.coreShape else lit?.character
    val bound = lit?.relics.orEmpty().mapNotNull { slot -> catalog.relics.firstOrNull { it.id == slot.id } }
        .filter { weapon.key in it.keys }
    val heirlooms = if (build != null) emptyList() else {
        progress.collection.heirloomRelicIds.distinct().mapNotNull { id -> catalog.relics.firstOrNull { it.id == id } }
            .filter { weapon.key in it.keys }
    }
    val tagPointsByTag = lit?.let { summary -> weapon.tags.associateWith { tag -> summary.tags.filter { it.tag == tag }.sumOf { it.points } } }
    val formLevels = catalog.weaponMasteries.map { it.minimumLevel }.filter { it > 1 }.sorted().take(weapon.masteryForms.size)
        .takeIf { it.size == weapon.masteryForms.size } ?: listOf(3, 6, 10).take(weapon.masteryForms.size)
    return CodexEntry("weapon/${weapon.id}", weapon.name.localizedContent(language), weapon.description.localizedContent(language),
        language.text(SessionText.WEAPON), if (equipped && build != null) language.text(SessionText.LEVEL_SHORT, build.weaponLevel) else CODEX_NONE,
        facts, CodexIcon.Weapon(weapon.id), Kk.Bone, owned,
        help = language.text(if (owned) SessionText.WEAPON_HELP else SessionText.WEAPON_LOCKED_HELP), active = equipped,
        detail = CodexDetail.Weapon(weapon, signature, character == weapon.signature, formLevels, bound, heirlooms, tagPointsByTag, rebirthLocked))
}

/** A weapon's element as the Codex names it: its element, or Kinetic. */
internal fun codexWeaponElementLabel(weapon: WeaponDefinition, language: AppLanguage): String =
    (weapon.element?.displayLabel ?: CODEX_KINETIC).localizedContent(language)

private const val CODEX_KINETIC = "Kinetic"

/** Display label of a weapon cadence. */
internal fun codexCadenceLabel(cadence: WeaponCadence, language: AppLanguage): String = language.text(when (cadence) {
    WeaponCadence.CONTINUOUS -> CodexText.CADENCE_CONTINUOUS
    WeaponCadence.CONTACT -> CodexText.CADENCE_CONTACT
    WeaponCadence.DISCRETE -> CodexText.CADENCE_DISCRETE
})

internal fun codexRelicEntry(
    relic: RelicDefinition,
    model: CodexRenderModel,
    catalog: UiCatalogSnapshot,
    language: AppLanguage = AppLanguage.English,
    severed: Boolean = false,
): CodexEntry {
    if (!severed && !model.isRelicDiscovered(relic.id)) return unknownEntry("relic/${relic.id}", language)
    val build = model.runStacks.build
    val rank = if (severed) null else build?.relics?.firstOrNull { it.id == relic.id }?.rank
    val maxRank = catalog.relicPolicy.maxRank
    val ranked = relic.meldable && relic.values.any { it.perRank != 0f }
    val upsides = (1..maxRank).map { relic.upsideText(language, it) }
    val liability = relic.liabilityText(language)
    val facts = buildList {
        add(when {
            severed -> codexStatus(language, language.text(CodexText.SEVERED), language.text(CodexText.SEVERED_HELP))
            rank != null -> codexStatus(language, language.text(SessionText.EQUIPPED))
            else -> codexStatus(language, language.text(SessionText.RELIC_AVAILABLE))
        })
        if (rank != null) add(CodexFact(language.text(SessionText.RANK), "$rank/${if (relic.meldable) maxRank else 1}"))
        if (relic.minimumRebirth > 0) add(CodexFact(language.text(CodexText.UNLOCKS_AT), language.text(SessionText.REBIRTH_LEVEL, relic.minimumRebirth)))
    }
    val bound = build?.relics.orEmpty().filter { it.id != relic.id }.map { catalog.relic(it.id) }
    val opposedBound = relic.keys.filter { key -> bound.any { other -> other.keys.any { RelicKey.opposed(key, it) } } }.toSet()
    val pairings = catalog.build.pairings.filter { it.involves(relic.id) }.mapNotNull { pairing ->
        val partner = if (pairing.first == relic.id) pairing.second else pairing.first
        if (!model.isRelicDiscovered(partner)) return@mapNotNull null
        CodexPairingNote(catalog.relic(partner), pairing.kind.linkKind(),
            pairing.name.localizedContent(language).takeIf { model.isPairingDiscovered(pairing.id) })
    }
    return CodexEntry("relic/${relic.id}", relic.name.localizedContent(language), "${upsides[(rank ?: 1) - 1]}\n\n$liability",
        relic.aspect.displayLabel.localizedContent(language), "${rank ?: 0}/$maxRank", facts,
        CodexIcon.Relic(relic, rank, severed), relicAspectColor(relic.aspect), isNew = relic.id in model.newRelicIds, active = rank != null,
        detail = CodexDetail.Relic(relic, upsides, rank ?: 1, ranked, liability, opposedBound, pairings, severed))
}

internal fun codexShapeEntry(shape: CoreShapeDefinition, model: CodexRenderModel, progress: HomeProgressProjection, language: AppLanguage = AppLanguage.English): CodexEntry {
    val unlocked = shape.id in progress.unlockedCoreShapes
    // The Profile counts unlock progress; a projection without it shows none yet.
    val current = progress.characterProgress.firstOrNull { it.shape == shape.id }?.progress ?: 0L
    val requirement = shape.unlockDescription.localizedContent(language)
    val equipped = shape.id == model.runStacks.build?.character
    val facts = buildList {
        add(codexStatus(language, language.text(when {
            equipped -> SessionText.EQUIPPED
            unlocked -> SessionText.UNLOCKED
            else -> SessionText.LOCKED
        })))
        if (!unlocked) add(CodexFact(language.text(SessionText.PROGRESS), "$current/${shape.unlockTarget}"))
        // The permanent Refit the run applies to the character it plays.
        model.runStacks.build?.takeIf { equipped }?.let { build ->
            add(CodexFact(language.text(CodexText.REFIT), language.text(SessionText.LEVEL_SHORT, build.characterUpgradeLevel)))
        }
        add(CodexFact(language.text(CodexText.MECHANIC), shape.mechanicName.localizedContent(language)))
        add(CodexFact(language.text(CodexText.TRIGGER), shape.triggerKind.displayLabel.localizedContent(language)))
        add(CodexFact(language.text(CodexText.DIFFICULTY), "${shape.difficulty}/3"))
    }
    return CodexEntry("shape/${shape.id}", if (unlocked) shape.displayName.localizedContent(language) else language.text(SessionText.UNKNOWN_CORE),
        if (unlocked) "${shape.mechanicDescription.localizedContent(language)}\n\n$requirement" else requirement,
        language.text(SessionText.CHARACTER), if (equipped) language.text(SessionText.EQUIPPED) else CODEX_NONE,
        facts, CodexIcon.Shape(shape.id), if (unlocked) Kk.Bone else Kk.Mute, unlocked,
        help = if (unlocked) language.text(SessionText.CHARACTER_LAB_HELP) else null, active = equipped)
}

/**
 * A roster enemy, always named: its threat, when it joins the waves ("From 5:30") and the Rebirth rank it
 * needs ("Reborn 3"); a rank below that gives it the locked look.
 */
internal fun codexEnemyEntry(enemy: EnemyDefinition, progress: HomeProgressProjection, language: AppLanguage = AppLanguage.English): CodexEntry {
    val locked = enemy.minimumRebirth > progress.rebirthProgress.rank
    val kind = language.text(when {
        enemy.isBoss -> CodexText.BOSS_KIND
        enemy.isMiniBoss -> CodexText.MINI_BOSS_KIND
        enemy.isElite -> CodexText.ELITE_KIND
        else -> CodexText.ENEMY_KIND
    })
    val threat = "${enemy.threat}/$MAX_CODEX_THREAT"
    val facts = buildList {
        add(codexStatus(language, language.text(if (locked) SessionText.LOCKED else CodexText.IN_ROSTER)))
        add(CodexFact(language.text(CodexText.THREAT), threat))
        add(CodexFact(language.text(CodexText.APPEARS), language.text(CodexText.FROM_TIME, codexClock(enemy.timeGateSeconds))))
        if (enemy.minimumRebirth > 0) {
            add(CodexFact(language.text(CodexText.UNLOCKS_AT), language.text(CodexText.REBORN_GATE, enemy.minimumRebirth)))
        }
    }
    return CodexEntry("enemy/${enemy.type}", enemy.name.localizedContent(language), enemy.description.localizedContent(language),
        kind, threat, facts, CodexIcon.Enemy(enemy.type, locked), if (locked) Kk.Mute else Kk.Bone, discovered = !locked,
        help = language.text(if (locked) CodexText.ENEMY_LOCKED_HELP else CodexText.ENEMY_HELP))
}

private const val MAX_CODEX_THREAT = 5

/** Run clock as m:ss ("5:30"), the way the Codex states when an enemy arrives. */
internal fun codexClock(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/**
 * The Catalog tab's entries of [category]. Items (0) are the matrix of [sort] in reading order, row by
 * row and slot by slot (see [codexItemRows]); the other categories keep catalog order.
 */
internal fun codexCatalogEntries(
    category: Int,
    search: String,
    filter: CodexItemFilter,
    model: CodexRenderModel,
    catalog: UiCatalogSnapshot,
    progress: HomeProgressProjection,
    language: AppLanguage = AppLanguage.English,
    sort: CodexItemSort = CodexItemSort.TAG,
): List<CodexEntry> {
    // Rebirth-gated content (opened by the rank) shows its tier in the open collection, never its identity.
    val rebirth = progress.rebirthProgress.rank
    val showLocks = search.isBlank() && filter == CodexItemFilter.ALL
    return when (category) {
        0 -> codexItemRows(sort, search, filter, model, catalog, progress, language).entries
        // Weapons are always named (an unowned one keeps the locked look), so search reaches every one.
        1 -> catalog.weapons.map { codexWeaponEntry(it, model, progress, catalog, language, allowCurrentRun = false) }
            .filter { search.isBlank() || it.title.contains(search, ignoreCase = true) }
        2 -> catalog.relics.mapNotNull { relic ->
            when {
                model.isRelicDiscovered(relic.id) -> codexRelicEntry(relic, model, catalog, language)
                    .takeIf { it.title.contains(search, ignoreCase = true) }
                showLocks && relic.minimumRebirth > rebirth -> codexLockedEntry("relic/${relic.id}", relic.minimumRebirth, language)
                else -> null
            }
        }
        3 -> catalog.coreShapes.map { codexShapeEntry(it, model, progress, language) }.filter { search.isBlank() || it.discovered && it.title.contains(search, ignoreCase = true) }
        // Enemies are always listed by name; a Rebirth gate only gives them the locked look.
        else -> catalog.enemies.map { codexEnemyEntry(it, progress, language) }.filter { search.isBlank() || it.title.contains(search, ignoreCase = true) }
    }
}

/** Catalog category of the enemy roster (after Items, Weapons, Relics and Forms). */
internal const val CODEX_ENEMIES_CATEGORY = 4

/**
 * The Build tab of a run: character, weapon, every relic slot (empty ones as placeholders, never
 * numbered by position) and severed relics, the items held, then the build's resonance, affinity,
 * relic links and reachable reactions.
 */
internal fun codexBuildEntries(model: CodexRenderModel, catalog: UiCatalogSnapshot, progress: HomeProgressProjection, language: AppLanguage = AppLanguage.English): List<CodexEntry> {
    val build = model.runStacks.build ?: return emptyList()
    return buildList {
        build.character?.let { add(codexShapeEntry(catalog.coreShape(it), model, progress, language)) }
        build.weapon?.let { add(codexWeaponEntry(catalog.weapon(it), model, progress, catalog, language)) }
        repeat(catalog.relicPolicy.maxSlots) { index ->
            val relic = build.relics.getOrNull(index)
            add(if (relic != null) codexRelicEntry(catalog.relic(relic.id), model, catalog, language) else CodexEntry(
                "empty-relic/$index", language.text(SessionText.EMPTY_RELIC_SLOT), "", language.text(SessionText.RELIC_SLOT), CODEX_NONE,
                listOf(codexStatus(language, language.text(SessionText.EMPTY))), CodexIcon.Empty, Kk.Mute, help = language.text(SessionText.EMPTY_RELIC_HELP),
            ))
        }
        build.severedRelicIds.filter { id -> build.relics.none { it.id == id } }.distinct().forEach { id ->
            add(codexRelicEntry(catalog.relic(id), model, catalog, language, severed = true))
        }
        addAll(model.items.filter { model.itemStack(it.id) > 0 }.map { codexItemEntry(it, model, progress.economy.lifetimeMatter, language, catalog) })
        build.tags.filter { it.points > 0 }.sortedWith(compareByDescending<kinetickk.ball.gameplay.api.BuildTagSummary> { it.points }.thenBy { it.tag.ordinal })
            .forEach { add(codexTagEntry(it.tag, catalog, model, language)) }
        build.elements.filter { it.affinity > 0 }.sortedWith(compareByDescending<kinetickk.ball.gameplay.api.BuildElementSummary> { it.affinity }.thenBy { it.element.ordinal })
            .forEach { add(codexAffinityEntry(it.element, catalog, model, language)) }
        build.relicLinks.forEach { link ->
            val first = catalog.relic(link.first)
            val second = catalog.relic(link.second)
            codexPairingEntry(first, second, catalog, model, language)?.let(::add)
        }
        build.reachableReactions.forEach { add(codexReactionEntry(it, catalog, model, language)) }
    }
}

internal fun unknownEntry(key: String, language: AppLanguage): CodexEntry = CodexEntry(
    key, language.text(SessionText.UNKNOWN_DISCOVERY), "",
    CODEX_NONE, CODEX_NONE, listOf(codexStatus(language, language.text(SessionText.UNDISCOVERED))), CodexIcon.Unknown, Kk.Mute,
    discovered = false, help = language.text(SessionText.DISCOVERY_HELP),
)

/** One Break per element (the matrix diagonal) and every reaction, in matrix reading order. */
internal fun codexReactionEntries(model: CodexRenderModel, catalog: UiCatalogSnapshot, language: AppLanguage = AppLanguage.English): List<CodexEntry> =
    CodexMatrix.flatten().map { cell ->
        cell.reaction?.let { codexReactionEntry(it, catalog, model, language) } ?: codexBreakEntry(cell.row, catalog, language)
    }

/** A Break is part of the open vocabulary: always shown with its threshold and both effects. */
internal fun codexBreakEntry(element: StatusElement, catalog: UiCatalogSnapshot, language: AppLanguage = AppLanguage.English): CodexEntry {
    val definition = catalog.build.element(element)
    return CodexEntry(
        codexBreakKey(element), element.breakLabel.localizedContent(language),
        "${definition.statusDescription.localizedContent(language)}\n\n${definition.breakDescription.localizedContent(language)}",
        language.text(CodexText.KIND_BREAK), CODEX_NONE,
        listOf(
            CodexFact(language.text(CodexText.STACKS_TO_BREAK), definition.breakThreshold.toString(), language.text(CodexText.BREAK_RULE)),
            CodexFact(language.text(CodexText.FADES_AFTER), language.text(CodexText.SECONDS, codexNumber(definition.decaySeconds, language).removeSuffix(".0").removeSuffix(",0"))),
        ),
        CodexIcon.Break(element), Kk.element(element.ordinal), detail = CodexDetail.Break(element),
    )
}

/** A reaction keeps its inputs visible; its name, effect and residue appear once triggered. */
internal fun codexReactionEntry(id: ReactionId, catalog: UiCatalogSnapshot, model: CodexRenderModel, language: AppLanguage = AppLanguage.English): CodexEntry {
    val definition = catalog.build.reaction(id)
    val discovered = model.isReactionDiscovered(id)
    val build = model.runStacks.build
    val reachable = build?.reachableReactions?.contains(id) == true
    val facts = buildList {
        add(codexStatus(language, language.text(if (discovered) CodexText.DISCOVERED else SessionText.UNDISCOVERED)))
        if (build != null) add(CodexFact(language.text(CodexText.THIS_RUN), language.text(if (reachable) CodexText.REACHABLE else CodexText.OUT_OF_REACH)))
    }
    val residue = definition.residue.takeIf { discovered }
    val feeds = residue?.let(::codexResidueFeeds).orEmpty().map { next ->
        CodexFeed(next, catalog.build.reaction(next).name.localizedContent(language).takeIf { model.isReactionDiscovered(next) })
    }
    return CodexEntry(
        codexReactionKey(id), if (discovered) definition.name.localizedContent(language) else language.text(CodexText.UNKNOWN_REACTION),
        if (discovered) definition.description.localizedContent(language) else "",
        language.text(CodexText.KIND_REACTION), CODEX_NONE, facts,
        CodexIcon.Reaction(id, discovered, reachable), Kk.element(id.first.ordinal), discovered = discovered,
        help = if (discovered) null else language.text(CodexText.REACTION_HELP), active = reachable,
        detail = CodexDetail.Reaction(id, discovered, residue, feeds),
        spoken = language.text(CodexText.BOTH, id.first.displayLabel.localizedContent(language), id.second.displayLabel.localizedContent(language)),
    )
}

internal fun RelicPairingKind.linkKind(): CodexLinkKind = if (this == RelicPairingKind.CONCORD) CodexLinkKind.CONCORD else CodexLinkKind.DISCORD

/** The link two relics form: a named pairing first, then opposed keys, then a shared key; null when neutral. */
internal fun codexLink(first: RelicDefinition, second: RelicDefinition, catalog: UiCatalogSnapshot): Pair<CodexLinkKind, RelicPairingDefinition?>? {
    if (first.id == second.id) return null
    val named = catalog.build.pairing(first.id, second.id)
    return when {
        named != null -> named.kind.linkKind() to named
        first.dissonates(second) -> CodexLinkKind.DISSONANCE to null
        first.harmonizes(second) -> CodexLinkKind.HARMONY to null
        else -> null
    }
}

internal fun codexPairingKey(first: RelicId, second: RelicId): String =
    if (first.ordinal <= second.ordinal) "pairing/$first/$second" else "pairing/$second/$first"

/**
 * The link of two relics. Harmony and dissonance follow from the keys and always show; a named
 * pairing keeps its name and effect hidden until it is discovered.
 */
internal fun codexPairingEntry(a: RelicDefinition, b: RelicDefinition, catalog: UiCatalogSnapshot, model: CodexRenderModel, language: AppLanguage = AppLanguage.English): CodexEntry? {
    val (first, second) = if (a.id.ordinal <= b.id.ordinal) a to b else b to a
    val (kind, named) = codexLink(first, second, catalog) ?: return null
    val discovered = named == null || model.isPairingDiscovered(named.id)
    val bound = model.runStacks.build?.relics?.let { relics -> relics.any { it.id == first.id } && relics.any { it.id == second.id } } == true
    val policy = catalog.build.policy
    val keys = if (kind.interferes) {
        first.keys.flatMap { mine -> second.keys.filter { RelicKey.opposed(mine, it) }.map { listOf(mine, it) } }
    } else {
        first.keys.filter { it in second.keys }.map { listOf(it) }
    }
    // A generic link is named by the keys behind it: the shared key, or the opposed pair.
    val title = when {
        named != null && discovered -> named.name.localizedContent(language)
        named != null -> language.text(CodexText.UNKNOWN_PAIRING)
        keys.isNotEmpty() -> keys.joinToString(", ") { group -> group.joinToString(" / ") { it.displayLabel.localizedContent(language) } }
        else -> language.text(kind.label)
    }
    val description = when {
        named != null && discovered -> named.description.localizedContent(language)
        named != null -> ""
        kind == CodexLinkKind.HARMONY -> language.text(CodexText.HARMONY_EFFECT, ((policy.harmonyScale - 1f) * 100f).roundToInt())
        else -> language.text(CodexText.DISSONANCE_EFFECT, ((1f - policy.dissonanceScale) * 100f).roundToInt())
    }
    val facts = buildList {
        when {
            bound -> add(codexStatus(language, language.text(CodexText.LINK_ACTIVE)))
            named != null -> add(codexStatus(language, language.text(if (discovered) CodexText.DISCOVERED else SessionText.UNDISCOVERED)))
        }
    }
    return CodexEntry(
        codexPairingKey(first.id, second.id), title, description, language.text(kind.label), CODEX_NONE, facts,
        CodexIcon.Pairing(first, second, kind, discovered, bound), if (kind.interferes) Kk.Hazard else Kk.Bone, discovered = discovered,
        help = if (discovered) null else language.text(CodexText.PAIRING_HELP), active = bound,
        detail = CodexDetail.Pairing(first, second, kind, discovered, keys),
        spoken = language.text(CodexText.BOTH, first.name.localizedContent(language), second.name.localizedContent(language)),
    )
}

internal val CodexLinkKind.label: CodexText
    get() = when (this) {
        CodexLinkKind.CONCORD -> CodexText.CONCORD
        CodexLinkKind.DISCORD -> CodexText.DISCORD
        CodexLinkKind.HARMONY -> CodexText.HARMONY
        CodexLinkKind.DISSONANCE -> CodexText.DISSONANCE
    }

/** Section of a Pairings tab entry; discovered discords and conflicts are the known incompatibilities. */
internal fun CodexEntry.pairingSection(): CodexPairingSection = when (val icon = icon) {
    is CodexIcon.Pairing -> when (icon.kind) {
        CodexLinkKind.CONCORD -> CodexPairingSection.CONCORDS
        CodexLinkKind.HARMONY -> CodexPairingSection.HARMONY
        CodexLinkKind.DISSONANCE -> CodexPairingSection.DISSONANCE
        CodexLinkKind.DISCORD -> if (icon.discovered) CodexPairingSection.INCOMPATIBLE else CodexPairingSection.DISSONANCE
    }
    else -> CodexPairingSection.INCOMPATIBLE
}

/**
 * Item and relic conflicts the game documents, keyed by the item's conversion rule (what makes the
 * two overlap), so they follow the content whatever the item is called; each shows once both sides
 * are discovered.
 */
private class DocumentedConflict(val conversion: ConversionKind, val relic: RelicId, val text: CodexText)

private val DocumentedConflicts = listOf(
    // A crit-chance cap leaves the Prism nothing to convert above it.
    DocumentedConflict(ConversionKind.CRIT_CHANCE_TO_DAMAGE, RelicId.EXECUTIONERS_PRISM, CodexText.CONFLICT_DEADEYE_PRISM),
    // The Aegis already turns all healing into shield, so overheal never happens.
    DocumentedConflict(ConversionKind.OVERHEAL_TO_SHIELD, RelicId.AEGIS_INVERSION, CodexText.CONFLICT_HARDLIGHT_AEGIS),
)

private fun ItemDefinition.converts(kind: ConversionKind): Boolean = rules.any { it is ItemRule.Convert && it.kind == kind }

/**
 * Every relic pair among the discovered relics that interacts, then the known incompatibilities:
 * items this build's relics suppress and the documented item and relic conflicts.
 */
internal fun codexPairingEntries(model: CodexRenderModel, catalog: UiCatalogSnapshot, language: AppLanguage = AppLanguage.English): List<CodexEntry> {
    val relics = catalog.relics.filter { model.isRelicDiscovered(it.id) }
    val pairs = relics.indices.flatMap { i -> (i + 1 until relics.size).mapNotNull { j -> codexPairingEntry(relics[i], relics[j], catalog, model, language) } }
    val build = model.runStacks.build
    val suppressions = build?.suppressedItemIds.orEmpty().distinct().mapNotNull { id -> model.items.firstOrNull { it.id == id } }.map { item ->
        // The kind already says it: no status fact repeating "Suppressed".
        CodexEntry("conflict/item/${item.id}", item.name.localizedContent(language), language.text(CodexText.SUPPRESSED_EFFECT),
            language.text(CodexText.SUPPRESSED), CODEX_NONE, emptyList(),
            CodexIcon.Conflict(item, emptyList()), Kk.Hazard, detail = CodexDetail.Conflict(item, emptyList()))
    }
    val documented = DocumentedConflicts.flatMap { conflict ->
        if (!model.isRelicDiscovered(conflict.relic)) return@flatMap emptyList()
        val relic = catalog.relic(conflict.relic)
        model.items.filter { it.converts(conflict.conversion) && model.isDiscovered(it.id) }.map { item ->
            CodexEntry("conflict/${relic.id}/${item.id}", item.name.localizedContent(language), language.text(conflict.text),
                language.text(CodexText.KIND_CONFLICT), CODEX_NONE, emptyList(), CodexIcon.Conflict(item, listOf(relic)), Kk.Hazard,
                detail = CodexDetail.Conflict(item, listOf(relic)),
                spoken = relic.name.localizedContent(language))
        }
    }
    return (pairs + suppressions + documented).sortedBy { it.pairingSection().ordinal }
}

internal fun codexTagEntry(tag: BuildTag, catalog: UiCatalogSnapshot, model: CodexRenderModel, language: AppLanguage = AppLanguage.English): CodexEntry {
    val resonance = catalog.build.resonance(tag)
    val summary = model.runStacks.build?.tags?.firstOrNull { it.tag == tag }
    val thresholds = catalog.build.policy.resonanceThresholds
    val tiers = resonance.tiers.map { it.localizedContent(language) }
    return CodexEntry(
        "tag/$tag", tag.displayLabel.localizedContent(language),
        "${resonance.description.localizedContent(language)}\n\n${tiers.joinToString("\n")}",
        language.text(CodexText.KIND_ARCHETYPE), summary?.points?.toString() ?: CODEX_NONE,
        listOfNotNull(summary?.let {
            CodexFact(language.text(CodexText.POINTS), it.points.toString(),
                language.text(CodexText.TAGS_RULE, thresholds[0], thresholds[1], thresholds[2]))
        }),
        CodexIcon.Tag(tag, summary?.points, summary?.tier ?: 0), Kk.Bone, active = (summary?.tier ?: 0) > 0,
        detail = CodexDetail.Tag(tag, tiers, thresholds, summary?.points),
    )
}

internal fun codexAffinityEntry(element: StatusElement, catalog: UiCatalogSnapshot, model: CodexRenderModel, language: AppLanguage = AppLanguage.English): CodexEntry {
    val definition = catalog.build.element(element)
    val summary = model.runStacks.build?.elements?.firstOrNull { it.element == element }
    val thresholds = catalog.build.policy.affinityThresholds
    val tiers = catalog.build.affinityTiers.map { it.description.localizedContent(language) }
    val rule = language.text(CodexText.AFFINITY_RULE, thresholds[0], thresholds[1], thresholds[2])
    return CodexEntry(
        "affinity/$element", element.displayLabel.localizedContent(language),
        "${definition.statusDescription.localizedContent(language)}\n\n${tiers.joinToString("\n")}",
        language.text(CodexText.KIND_AFFINITY), summary?.affinity?.toString() ?: CODEX_NONE,
        buildList {
            if (summary?.dominant == true) add(codexStatus(language, language.text(CodexText.DOMINANT)))
            summary?.let { add(CodexFact(language.text(CodexText.KIND_AFFINITY), it.affinity.toString(), rule)) }
        },
        CodexIcon.Affinity(element, summary?.affinity, summary?.tier ?: 0, summary?.dominant == true), Kk.element(element.ordinal),
        help = rule, active = summary?.dominant == true,
        detail = CodexDetail.Affinity(element, tiers, thresholds, summary?.affinity, summary?.dominant == true),
    )
}

internal fun codexKeyEntry(key: RelicKey, catalog: UiCatalogSnapshot, model: CodexRenderModel, language: AppLanguage = AppLanguage.English): CodexEntry {
    val opposed = RelicKey.entries.filter { RelicKey.opposed(key, it) }
    val carriers = catalog.relics.filter { key in it.keys }
    val known = carriers.filter { model.isRelicDiscovered(it.id) }
    return CodexEntry(
        "key/$key", key.displayLabel.localizedContent(language), "",
        language.text(CodexText.KIND_KEY), "${known.size}/${carriers.size}",
        listOfNotNull(opposed.takeIf { it.isNotEmpty() }?.let { keys ->
            CodexFact(language.text(CodexText.OPPOSES), keys.joinToString(", ") { it.displayLabel.localizedContent(language) })
        }),
        CodexIcon.Key(key), Kk.Bone, help = language.text(CodexText.KEYS_RULE),
        detail = CodexDetail.Key(key, opposed, known, carriers.size - known.size),
    )
}

/** Every Tags tab entry: the nine archetypes, one affinity per element, then the relic keys in chip order. */
internal fun codexTagsEntries(model: CodexRenderModel, catalog: UiCatalogSnapshot, language: AppLanguage = AppLanguage.English): List<CodexEntry> =
    BuildTag.entries.map { codexTagEntry(it, catalog, model, language) } +
        StatusElement.entries.map { codexAffinityEntry(it, catalog, model, language) } +
        codexKeyGroups().flatten().map { codexKeyEntry(it, catalog, model, language) }

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
