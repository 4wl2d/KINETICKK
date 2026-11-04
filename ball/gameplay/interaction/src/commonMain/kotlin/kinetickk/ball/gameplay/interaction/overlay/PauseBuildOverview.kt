// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.overlay

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.content.api.liabilityText
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.localization.HudRedesignText
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.ball.gameplay.interaction.localization.RunVarietyText
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RelicLinkKind
import kinetickk.ball.gameplay.nucleus.render.formatRunTime
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.formatGroupedNumber
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.roundToLong

/** Tone of a run statistic value in the build overview. */
internal enum class PauseValueTone { BONE, YOU, THREAT }

/** One run statistic panel ("Elites 2"). */
internal class PauseRunStat(val label: String, val value: String, val tone: PauseValueTone)

/**
 * One bound relic of the pause matrix: its rune, rank and the net multiplier its links (harmony,
 * dissonance, Hollow Crown) give its upside, read from the build. [spent] marks a used Ouroboros seal.
 */
internal class PauseRelicEntry(
    val definition: RelicDefinition,
    val rank: Int,
    val maxRank: Int,
    val multiplier: String,
    val tone: PauseValueTone,
    val spent: Boolean,
)

/**
 * A bracket over the relic matrix from slot [from] to [to] on stack [level]: only named pairings
 * and dissonance get one (harmony shows as the multipliers alone). [name] is the pairing's name,
 * "?" until met, or the generic rule's word.
 */
internal class PauseBracket(
    val from: Int,
    val to: Int,
    val level: Int,
    val name: String,
    val interferes: Boolean,
    val discovered: Boolean,
)

/** One line of the always-visible cost list; [lingering] is a replaced relic's cost that still applies. */
internal class PauseCostLine(val definition: RelicDefinition, val text: String, val lingering: Boolean)

/**
 * A tag with at least one point, or the run's Keynote tag ([keynote], bone corner brackets; listed even at 0 points, a
 * muted ghost): its ladder notched at the resonance thresholds.
 */
internal class PauseTagEntry(val tag: BuildTag, val label: String, val points: Int, val tier: Int, val keynote: Boolean = false)

/** An element meter notched at the affinity thresholds; [dominant] gets the ring, the run's Keynote element [keynote] brackets. */
internal class PauseElementEntry(
    val element: Int,
    val label: String,
    val affinity: Int,
    val tier: Int,
    val dominant: Boolean,
    val keynote: Boolean = false,
)

/** A reaction both of whose elements the build sources: its name once met, "?" before. */
internal class PauseReactionEntry(val id: ReactionId, val name: String, val discovered: Boolean)

/** One active weapon synergy (SPEC 10.2) as a chip: its text and the hue of its accent edge. */
internal class PauseSynergyChip(val text: String, val accent: PauseSynergyAccent)

/** What a synergy chip's edge shows: the player's color, an element's hue or a Relic's aspect. */
internal enum class PauseSynergyAccent { YOU, ELEMENT, RELIC }

/**
 * An owned item: stacks as the icon's arc, hatched when bound relics disable it, a gold rim when Reborn. An active item
 * ([active]) shows its effect's mark with its rank pips and [slotKey], the player's key for its Z/X/C slot (the slot's
 * number on touch), on a key cap as its card's Active chip does.
 */
internal class PauseItemEntry(
    val item: ItemDefinition,
    val stacks: Int,
    val suppressed: Boolean,
    val reborn: Boolean,
    val slotKey: String? = null,
) {
    val active: SkillId? get() = item.active
}

/**
 * The pause build view of one committed frame (SPEC build view): the run's weapon and its level,
 * form, level and Rebirth tier; the relic matrix with its brackets, multipliers and cost list;
 * resonance tags and element meters; the reactions the build can reach; the owned items; four
 * derived stats and the run.
 */
internal class PauseBuildOverview(
    val heading: String,
    val weaponIcon: KkIcon,
    val weaponName: String,
    val weaponLevelText: String,
    val masteryTier: WeaponMastery,
    val form: String,
    val formIcon: KkIcon,
    val rebirth: String,
    val rebirthLevel: Int,
    val levelLabel: String,
    val level: Int,
    val relicsLabel: String,
    val relics: List<PauseRelicEntry?>,
    val brackets: List<PauseBracket>,
    val costLabel: String,
    val costs: List<PauseCostLine>,
    val resonanceLabel: String,
    val tags: List<PauseTagEntry>,
    val tagThresholds: List<Int>,
    val elementsLabel: String,
    val elements: List<PauseElementEntry>,
    val affinityThresholds: List<Int>,
    val reactionsLabel: String,
    val reactions: List<PauseReactionEntry>,
    val noReactions: String,
    val itemsLabel: String,
    val items: List<PauseItemEntry>,
    val noItems: String,
    val statsLabel: String,
    val stats: List<OverlayStat>,
    val runLabel: String,
    val run: List<PauseRunStat>,
    val synergyLabel: String = "",
    val synergies: List<PauseSynergyChip> = emptyList(),
    val noSynergy: String = "",
    /** The element of an [PauseSynergyAccent.ELEMENT] chip and the Relic of a [PauseSynergyAccent.RELIC] one. */
    val synergyElement: StatusElement? = null,
    val synergyRelic: RelicDefinition? = null,
    /** The run's level cap (the badge turns APEX there) and the Apex the badge shows, as of the pause. */
    val levelCap: Int = Int.MAX_VALUE,
    val apex: KkApexState = KkApexState.NONE,
    val apexActive: Float = 0f,
    /** The run's Conditions: the omen taken, the director pattern, the Keynote, the world event in progress. */
    val conditionsLabel: String = "",
    val conditions: List<PauseConditionEntry> = emptyList(),
    /** The last free slots of [relics] a Meld sealed (drawn sealed; [relicsLabel] names the Melds' bonus). */
    val sealedSlots: Int = 0,
) {
    /** The names the brackets carry, in bracket order. */
    val synergyNames: List<String> = brackets.map { it.name }

    /** Bracket stacks in use. */
    val bracketLevels: Int = (brackets.maxOfOrNull { it.level } ?: -1) + 1
}

internal fun GameplayRenderModel.pauseBuildOverview(language: AppLanguage, touch: Boolean = false): PauseBuildOverview {
    val policy = content.relicPolicy
    val slots = List(policy.maxSlots) { index -> equippedRelics.getOrNull(index) }
    val relics = slots.map { equipped -> equipped?.let { pauseRelicEntry(it, language) } }
    val links = overlayRelicLinks(slots.map { it?.id })
    val brackets = pauseBrackets(links, language)
    val costs = relics.filterNotNull().map {
        PauseCostLine(it.definition, it.definition.liabilityText(language).keepUnitsWithNumbers(), lingering = false)
    } + build.severed.filter { severed -> slots.none { it?.id == severed.id } }.map {
        val definition = content.relic(it.id)
        PauseCostLine(definition, definition.liabilityText(language).keepUnitsWithNumbers(), lingering = true)
    }
    val tags = BuildTag.entries.filter { build.tagPoints(it) > 0 || it == keynoteTag }
        .sortedWith(compareByDescending<BuildTag> { build.tagPoints(it) }.thenBy { it.ordinal })
        .map { PauseTagEntry(it, it.displayLabel.localizedContent(language), build.tagPoints(it), build.tagTier(it), it == keynoteTag) }
    val elements = StatusElement.entries.map {
        PauseElementEntry(it.ordinal, it.displayLabel.localizedContent(language), build.affinity(it), build.affinityTier(it), build.dominant == it,
            it == keynoteElement)
    }
    val reactions = build.reachableReactions.map { id ->
        val discovered = isReactionDiscovered(id)
        PauseReactionEntry(id, if (discovered) content.build.reaction(id).name.localizedContent(language) else
            language.text(HudRedesignText.UnknownReaction), discovered)
    }
    val items = pauseItems(language, touch)
    val stats = runStatistics
    val buildPolicy = content.build.policy
    val synergy = weaponSynergy
    val synergies = pauseSynergyChips(language)
    return PauseBuildOverview(
        heading = language.text(OverlayRedesignText.Build),
        weaponIcon = weapon.overlayIcon(),
        weaponName = currentWeaponDefinition.name.localizedContent(language),
        weaponLevelText = language.text(HudRedesignText.WeaponLevel, weaponLevel),
        masteryTier = currentWeaponMastery,
        form = content.coreShape(coreShape).displayName.localizedContent(language),
        formIcon = coreShape.overlayIcon(),
        rebirth = language.text(OverlayRedesignText.RebirthTag, rebirthLevel),
        rebirthLevel = rebirthLevel,
        levelLabel = language.text(OverlayRedesignText.LevelLabel),
        level = level,
        levelCap = maxLevel,
        apex = apex.badgeState(),
        apexActive = apex.active01,
        relicsLabel = pauseRelicsLabel(language),
        relics = relics,
        sealedSlots = build.meldedSlots,
        brackets = brackets,
        costLabel = language.text(OverlayRedesignText.Cost),
        costs = costs.take(MAX_COST_LINES),
        resonanceLabel = language.text(HudRedesignText.Resonance),
        tags = tags,
        tagThresholds = buildPolicy.resonanceThresholds,
        elementsLabel = language.text(HudRedesignText.Elements),
        elements = elements,
        affinityThresholds = buildPolicy.affinityThresholds,
        reactionsLabel = language.text(HudRedesignText.Reactions),
        reactions = reactions,
        noReactions = language.text(HudRedesignText.NoReactions),
        itemsLabel = language.text(HudRedesignText.Items),
        items = items,
        noItems = language.text(HudRedesignText.NoItems),
        statsLabel = language.text(OverlayRedesignText.Stats),
        stats = listOf(
            OverlayStat(language.text(HudRedesignText.ImpactPower), overlayMultiplier(damageMultiplier, language), damageMultiplier > 1.001f),
            OverlayStat(language.text(HudRedesignText.WeaponPower), overlayMultiplier(effectiveWeaponPower, language), effectiveWeaponPower > 1.001f),
            OverlayStat(language.text(HudRedesignText.CriticalChance), overlayPercent(critChance, language), false),
            OverlayStat(language.text(HudRedesignText.ShieldCapacity), overlayAmount(maxShield.toDouble(), language), false),
        ),
        runLabel = language.text(GameplayText.DetailedStatistics),
        run = listOf(
            PauseRunStat(language.text(OverlayRedesignText.RunTimeShort), formatRunTime(elapsed), PauseValueTone.BONE),
            PauseRunStat(language.text(OverlayRedesignText.DestroyedShort), formatGroupedNumber(kills.toLong(), language), PauseValueTone.BONE),
            PauseRunStat(language.text(OverlayRedesignText.ElitesShort), formatGroupedNumber(stats.eliteKills.toLong(), language), PauseValueTone.THREAT),
            PauseRunStat(language.text(OverlayRedesignText.MatterShort), "+" + formatGroupedNumber(runMatter, language), PauseValueTone.YOU),
            PauseRunStat(language.text(OverlayRedesignText.ChainShort), "×" + stats.bestCombo, PauseValueTone.YOU),
            PauseRunStat(language.text(OverlayRedesignText.DamageShort), overlayAmount(stats.damageDealt, language), PauseValueTone.BONE),
        ),
        synergyLabel = language.text(HudRedesignText.WeaponSynergy),
        synergies = synergies,
        noSynergy = language.text(HudRedesignText.NoSynergy),
        synergyElement = synergy.infusionElement,
        synergyRelic = synergy.harmonizingRelic?.let { content.relic(it) },
        conditionsLabel = language.text(RunVarietyText.Conditions),
        conditions = pauseConditions(language),
    )
}

/** A bound relic of the pause matrix: its rank and the net multiplier its links make of its upside. */
private fun GameplayRenderModel.pauseRelicEntry(equipped: EquippedRelic, language: AppLanguage): PauseRelicEntry {
    val upside = build.relicUpside(equipped.id)
    return PauseRelicEntry(
        definition = content.relic(equipped.id),
        rank = equipped.rank,
        maxRank = content.relicPolicy.maxRank,
        multiplier = overlayMultiplier(upside, language),
        tone = when {
            upside > 1.0005f -> PauseValueTone.YOU
            upside < 0.9995f -> PauseValueTone.THREAT
            else -> PauseValueTone.BONE
        },
        spent = equipped.id == RelicId.OUROBOROS_SEAL && build.ouroborosSpent,
    )
}

/** The Melds' lasting bonus after [melds] Melds: "Meld ×2: damage +25%, integrity +21%". */
internal fun GameplayRenderModel.meldTotal(melds: Int, language: AppLanguage): String {
    val policy = content.relicPolicy
    fun percent(multiplier: Float) = overlayDecimal(((multiplier - 1f) * 100f).roundToLong().toFloat(), language)
    return language.text(OverlayRedesignText.MeldTotal, melds, percent(policy.meldDamageMultiplier(melds)),
        percent(policy.meldIntegrityMultiplier(melds)))
}

/** "Relics 2/4", then the Melds' lasting bonus once a slot is sealed ("Relics 2/4 · Meld ×1: damage +4%, integrity +3%"). */
private fun GameplayRenderModel.pauseRelicsLabel(language: AppLanguage): String {
    val bound = language.text(OverlayRedesignText.Relics) + " " + equippedRelics.size + "/" + content.relicPolicy.maxSlots
    return if (build.meldedSlots > 0) bound + " · " + meldTotal(build.meldedSlots, language) else bound
}

/**
 * The owned items in the grid's order: the active items first in Z/X/C order (so a long build never hides them under
 * "+N"), each with its slot's key (its number on [touch]), then the passive items in catalog order.
 */
private fun GameplayRenderModel.pauseItems(language: AppLanguage, touch: Boolean): List<PauseItemEntry> {
    fun entry(item: ItemDefinition, slotKey: String? = null) =
        PauseItemEntry(item, itemStack(item.id), item.id in build.suppressedItemIds, item.id in build.rebornItemIds, slotKey)
    val owned = content.items.filter { itemStack(it.id) > 0 }
    val slots = skillBar.slots
    val actives = (0 until slots.size).mapNotNull { slot ->
        val effect = slots[slot].skillId ?: return@mapNotNull null
        val item = owned.firstOrNull { it.active == effect } ?: return@mapNotNull null
        entry(item, if (touch) (slot + 1).toString() else activeSlotKey(slot, language))
    }
    return actives + owned.filter { item -> actives.none { it.item.id == item.id } }.map { entry(it) }
}

/**
 * The weapon's active synergies: tag resonance, the signature character, the harmonizing Relic, the element
 * its hits carry, its scaling stat when it adds power, and its open mastery forms.
 */
private fun GameplayRenderModel.pauseSynergyChips(language: AppLanguage): List<PauseSynergyChip> {
    val synergy = weaponSynergy
    val chips = ArrayList<PauseSynergyChip>(6)
    if (synergy.tagBonus > 0.0005f) {
        chips += PauseSynergyChip(language.text(HudRedesignText.SynergyTags, (synergy.tagBonus * 100f).roundToLong()), PauseSynergyAccent.YOU)
    }
    if (synergy.signatureActive) {
        chips += PauseSynergyChip(language.text(HudRedesignText.SynergySignature, overlayMultiplier(SIGNATURE_POWER, language)), PauseSynergyAccent.YOU)
    }
    synergy.harmonizingRelic?.let { relic ->
        chips += PauseSynergyChip(language.text(HudRedesignText.SynergyHarmony, content.relic(relic).name.localizedContent(language)),
            PauseSynergyAccent.RELIC)
    }
    synergy.infusionElement?.let { element ->
        chips += PauseSynergyChip(language.text(HudRedesignText.SynergyElement, element.displayLabel.localizedContent(language)),
            PauseSynergyAccent.ELEMENT)
    }
    if (synergy.scalingMultiplier > 1.005f) {
        chips += PauseSynergyChip(language.text(HudRedesignText.SynergyScaling,
            currentWeaponDefinition.scaling.displayLabel.localizedContent(language), overlayMultiplier(synergy.scalingMultiplier, language)),
            PauseSynergyAccent.YOU)
    }
    if (synergy.masteryForms > 0) {
        chips += PauseSynergyChip(language.text(HudRedesignText.SynergyForms, synergy.masteryForms), PauseSynergyAccent.YOU)
    }
    return chips
}

/** The signature character's weapon power, as the synergy chip shows it (the nucleus holds the rule). */
private const val SIGNATURE_POWER = 1.2f

/** Cost lines the view keeps: four bound relics and up to two lingering ones. */
internal const val MAX_COST_LINES = 6

/** Bracket stacks drawn over the matrix; a link that finds no free stack shows only in its multipliers. */
private const val MAX_BRACKET_LEVELS = 2

/**
 * Brackets for named pairings and dissonance, each on the lowest stack where no bracket over an
 * overlapping or touching slot span sits; narrower spans claim the lower stacks first.
 */
private fun GameplayRenderModel.pauseBrackets(links: List<OverlayRelicLink>, language: AppLanguage): List<PauseBracket> {
    val placed = ArrayList<PauseBracket>()
    links.filter { it.kind != RelicLinkKind.HARMONY }.sortedBy { it.last - it.first }.forEach { link ->
        var level = 0
        while (placed.any { it.level == level && it.from <= link.last && link.first <= it.to }) level++
        if (level >= MAX_BRACKET_LEVELS) return@forEach
        placed += PauseBracket(link.first, link.last, level, link.overlayName(content, language), link.interferes, link.discovered)
    }
    return placed
}

/**
 * Built once per paused frame and language: pause redraws every frame, and a paused run keeps
 * its clock, build and totals, so an equal frame reuses the overview without allocating.
 */
internal object PauseBuildMemo {
    private var engine: GameplayRenderModel? = null
    private var language: AppLanguage? = null
    private var touch = false
    private var overview: PauseBuildOverview? = null

    fun of(engine: GameplayRenderModel, language: AppLanguage, touch: Boolean): PauseBuildOverview {
        val cached = overview
        val previous = this.engine
        if (cached != null && previous != null && this.language == language && this.touch == touch &&
            (previous === engine || previous.sameBuild(engine))) {
            this.engine = engine
            return cached
        }
        return engine.pauseBuildOverview(language, touch).also {
            this.engine = engine
            this.language = language
            this.touch = touch
            overview = it
        }
    }

    /** Every value the overview reads (the build projection compares whole, by value). */
    private fun GameplayRenderModel.sameBuild(other: GameplayRenderModel): Boolean =
        content === other.content && elapsed == other.elapsed && level == other.level && kills == other.kills &&
            weapon == other.weapon && weaponLevel == other.weaponLevel &&
            runMatter == other.runMatter && rebirthLevel == other.rebirthLevel && coreShape == other.coreShape &&
            equippedRelics == other.equippedRelics && build == other.build && runStatistics == other.runStatistics &&
            damageMultiplier == other.damageMultiplier && effectiveWeaponPower == other.effectiveWeaponPower &&
            critChance == other.critChance && maxShield == other.maxShield && itemStacksSnapshot == other.itemStacksSnapshot &&
            weaponSynergy == other.weaponSynergy && sameSlots(other) && settings.keyBindings == other.settings.keyBindings &&
            reactionDiscoveries() == other.reactionDiscoveries() && pairingDiscoveries() == other.pairingDiscoveries() &&
            runBoons == other.runBoons && runOmen == other.runOmen && directorPersonality == other.directorPersonality && keynoteTag == other.keynoteTag &&
            keynoteElement == other.keynoteElement && worldEvent?.kind == other.worldEvent?.kind

    /** The same effect in every Z/X/C slot (the active items' keys). */
    private fun GameplayRenderModel.sameSlots(other: GameplayRenderModel): Boolean {
        val slots = skillBar.slots
        val others = other.skillBar.slots
        if (slots.size != others.size) return false
        for (index in 0 until slots.size) if (slots[index].skill != others[index].skill) return false
        return true
    }

    private fun GameplayRenderModel.reactionDiscoveries(): Int {
        var mask = 0
        val ids = ReactionId.entries
        for (index in ids.indices) if (isReactionDiscovered(ids[index])) mask = mask or (1 shl index)
        return mask
    }

    private fun GameplayRenderModel.pairingDiscoveries(): Int {
        var mask = 0
        val ids = RelicPairingId.entries
        for (index in ids.indices) if (isPairingDiscovered(ids[index])) mask = mask or (1 shl index)
        return mask
    }
}
