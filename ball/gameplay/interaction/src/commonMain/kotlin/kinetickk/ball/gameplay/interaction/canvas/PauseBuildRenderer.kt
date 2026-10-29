// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.content.api.liabilityText
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.PauseLayoutGeometry
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.localization.HudRedesignText
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.ball.gameplay.interaction.localization.RunVarietyText
import kinetickk.ball.gameplay.interaction.rewards.activeSlotKey
import kinetickk.ball.gameplay.interaction.rewards.overlayUiTextScale
import kinetickk.ball.gameplay.nucleus.model.formatRunTime
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RelicLinkKind
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
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
) {
    /** The names the brackets carry, in bracket order. */
    val synergyNames: List<String> = brackets.map { it.name }

    /** Bracket stacks in use. */
    val bracketLevels: Int = (brackets.maxOfOrNull { it.level } ?: -1) + 1
}

internal fun GameplayRenderModel.pauseBuildOverview(language: AppLanguage, touch: Boolean = false): PauseBuildOverview {
    val policy = content.relicPolicy
    val slots = List(policy.maxSlots) { index -> equippedRelics.getOrNull(index) }
    val relics = slots.map { equipped ->
        equipped?.let {
            val upside = build.relicUpside(it.id)
            PauseRelicEntry(
                definition = content.relic(it.id),
                rank = it.rank,
                maxRank = policy.maxRank,
                multiplier = overlayMultiplier(upside, language),
                tone = when {
                    upside > 1.0005f -> PauseValueTone.YOU
                    upside < 0.9995f -> PauseValueTone.THREAT
                    else -> PauseValueTone.BONE
                },
                spent = it.id == RelicId.OUROBOROS_SEAL && build.ouroborosSpent,
            )
        }
    }
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
        relicsLabel = language.text(OverlayRedesignText.Relics) + " " + equippedRelics.size + "/" + policy.maxSlots,
        relics = relics,
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
            OverlayStat(language.text(HudRedesignText.ShieldCapacity), overlayGrouped(maxShield.roundToLong(), language), false),
        ),
        runLabel = language.text(GameplayText.DetailedStatistics),
        run = listOf(
            PauseRunStat(language.text(OverlayRedesignText.RunTimeShort), formatRunTime(elapsed), PauseValueTone.BONE),
            PauseRunStat(language.text(OverlayRedesignText.DestroyedShort), overlayGrouped(kills.toLong(), language), PauseValueTone.BONE),
            PauseRunStat(language.text(OverlayRedesignText.ElitesShort), overlayGrouped(stats.eliteKills.toLong(), language), PauseValueTone.THREAT),
            PauseRunStat(language.text(OverlayRedesignText.MatterShort), "+" + overlayGrouped(runMatter, language), PauseValueTone.YOU),
            PauseRunStat(language.text(OverlayRedesignText.ChainShort), "×" + stats.bestCombo, PauseValueTone.YOU),
            PauseRunStat(language.text(OverlayRedesignText.DamageShort), overlayCompact(stats.damageDealt.roundToLong(), language), PauseValueTone.BONE),
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
private const val MAX_COST_LINES = 6

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
private object PauseBuildMemo {
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

internal fun DrawScope.drawPauseBuild(
    engine: GameplayRenderModel,
    measurer: CanvasTextMeasurer,
    layout: PauseLayoutGeometry,
    time: Float,
    touch: Boolean = false,
) {
    val overview = PauseBuildMemo.of(engine, measurer.language, touch)
    val ui = PauseMeasurers.ui(measurer, engine.settings.textScale)
    val display = PauseMeasurers.display(measurer, engine.settings.textScale)
    when (layout.mode) {
        GameplayLayoutMode.REGULAR -> drawRegularBuild(overview, ui, display, layout.build, layout.unit, time)
        GameplayLayoutMode.COMPACT_LANDSCAPE -> drawCompactBuild(overview, ui, display, layout.build, layout.unit, time, portrait = false)
        GameplayLayoutMode.COMPACT_PORTRAIT -> drawCompactBuild(overview, ui, display, layout.build, layout.unit, time, portrait = true)
    }
}

/**
 * Pause measurers derived from the host's: UI text follows the text-size setting relative to the
 * default (board size at 125 %); display type (title, frozen clock, menu items, level numerals)
 * ignores it. Rebuilt only when the host measurer or the setting changes.
 */
internal object PauseMeasurers {
    private var base: CanvasTextMeasurer? = null
    private var setting = Float.NaN
    private var ui: CanvasTextMeasurer? = null
    private var display: CanvasTextMeasurer? = null

    fun ui(base: CanvasTextMeasurer, setting: Float): CanvasTextMeasurer = update(base, setting).let { requireNotNull(ui) }

    fun display(base: CanvasTextMeasurer, setting: Float): CanvasTextMeasurer = update(base, setting).let { requireNotNull(display) }

    private fun update(base: CanvasTextMeasurer, setting: Float) {
        if (this.base === base && this.setting == setting) return
        this.base = base
        this.setting = setting
        ui = CanvasTextMeasurer(base.delegate, overlayUiTextScale(setting), base.language, base.typography, base.roles)
        display = CanvasTextMeasurer(base.delegate, 1f, base.language, base.typography, base.roles)
    }
}

/** What a pause text is, for the layout probe. */
internal enum class PauseTextKind {
    TITLE, TIMER, HEADING, TAG, LABEL, SYNERGY_NAME, MULTIPLIER, COST, TAG_NAME, ELEMENT_NAME, REACTION, EMPTY,
    STAT_LABEL, STAT_VALUE, RUN_LABEL, RUN_VALUE, MORE, SYNERGY, KEY,
    /** The run Conditions: an entry's caption, its name, what it does or costs. */
    CONDITION_LABEL, CONDITION, CONDITION_TEXT,
}

/** One laid-out pause text: where it was drawn ([rect], px) and the width it had to fit ([box]). */
internal class PauseTextRecord(val kind: PauseTextKind, val layout: TextLayoutResult, val rect: Rect, val box: Float)

/** Test probe: while [records] is set, every pause text is recorded as it is drawn. */
internal object PauseTextProbe {
    var records: MutableList<PauseTextRecord>? = null
}

/** Draws a laid-out pause text and reports it to the probe with the width it had to fit. */
internal fun DrawScope.drawPauseText(
    kind: PauseTextKind,
    layout: TextLayoutResult,
    x: Float,
    y: Float,
    color: Color,
    box: Float,
    align: KkAlign = KkAlign.START,
    valign: KkVAlign = KkVAlign.TOP,
) {
    drawKkText(layout, x, y, color, align, valign)
    val records = PauseTextProbe.records ?: return
    val left = when (align) {
        KkAlign.START -> x
        KkAlign.CENTER -> x - layout.size.width * 0.5f
        KkAlign.END -> x - layout.size.width
    }
    val top = when (valign) {
        KkVAlign.TOP -> y - layout.kkBoxTop
        KkVAlign.CENTER -> y - (layout.kkBoxTop + layout.kkBoxBottom) * 0.5f
        KkVAlign.BASELINE -> y - layout.firstBaseline
    }
    records += PauseTextRecord(kind, layout, Rect(left, top, left + layout.size.width, top + layout.size.height), box)
}

private fun runColor(tone: PauseValueTone, roles: KkRolePalette): Color = when (tone) {
    PauseValueTone.BONE -> Kk.Bone
    PauseValueTone.YOU -> roles.you
    PauseValueTone.THREAT -> roles.threat
}

/**
 * Pause board, two columns under the header: relics (brackets, runes, multipliers), their cost
 * list, the reachable reactions and four derived stats on the left; resonance ladders, element
 * meters and the owned items on the right; the six run panels across the bottom. Sections flow
 * down their column at the measured height of their text, so larger text sizes push, never
 * overlap; the cost list and the reactions share what is left. [ui] measures text that follows the
 * text-size setting, [display] the level badge.
 */
private fun DrawScope.drawRegularBuild(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    display: CanvasTextMeasurer,
    area: Rect,
    unit: Float,
    time: Float,
) {
    val k = unit / density
    fun x(v: Float) = area.left + v * unit
    fun y(v: Float) = area.top + v * unit
    val width = area.width / unit
    drawBuildHeader(overview, ui, display, x(0f), x(width), y(0f), y(4f), 40f * k, 16f * unit, k, 12f, 20f, time, weaponName = true)
    val column = 352f
    val right = 376f
    val rightWidth = width - right
    val gap = 12f * unit
    val runTop = y(640f)
    val columnBottom = runTop - gap
    // Left column: the relic matrix, its costs (never hidden), then the reactions fill the rest.
    var top = drawPauseLabel(overview.relicsLabel, ui, 15f * k, x(0f), y(58f), column * unit) + 4f * unit
    top = drawPauseRelics(overview, ui, x(0f), top + 4f * unit, column * unit, 56f * unit, 88f * unit, 22f * unit, k, names = true, time = time) + gap
    top = drawPauseLabel(overview.costLabel, ui, 15f * k, x(0f), top, column * unit) + 6f * unit
    // The costs take what they need of the column, keeping two rows of reaction chips under them and, in a run with
    // conditions, a row of their chips (after their label) at the column's foot.
    val reactionsHeight = pauseLabelHeight(overview.reactionsLabel, ui, 15f * k, column * unit) + 6f * unit + 26f * unit * 2f + 6f * unit
    val conditionChip = 24f * unit * maxOf(1f, ui.scale * 0.85f)
    val conditionsRow = pauseConditionsRow(overview, conditionChip)
    val leftBottom = columnBottom - if (conditionsRow > 0f) gap + conditionsRow else 0f
    val costBottom = drawPauseCosts(overview, ui, x(0f), top, column * unit, leftBottom - gap - reactionsHeight, 12f * k, k)
    top = drawPauseLabel(overview.reactionsLabel, ui, 15f * k, x(0f), costBottom + gap, column * unit) + 6f * unit
    val reactionsTop = top
    // Right column: resonance and element meters, the four derived stats at the foot, the items between.
    top = drawPauseLabel(overview.resonanceLabel, ui, 15f * k, x(right), y(58f), rightWidth * unit) + 6f * unit
    top = drawPauseTags(overview, ui, x(right), top, rightWidth * unit, 24f * unit * maxOf(1f, ui.scale * 0.85f), 1, 15f * k, k) + gap
    top = drawPauseLabel(overview.elementsLabel, ui, 15f * k, x(right), top, rightWidth * unit) + 6f * unit
    top = drawPauseElements(overview, ui, x(right), top, rightWidth * unit, 58f * unit * maxOf(1f, ui.scale * 0.85f), k, names = true) + gap
    val statRow = 27f * unit * maxOf(1f, ui.scale * 0.9f)
    val statsTop = columnBottom - pauseLabelHeight(overview.statsLabel, ui, 15f * k, rightWidth * unit) - overview.stats.size * statRow
    // The weapon's synergies: up to two rows of chips in the right column where they leave the items a label
    // and a row of icons, else one row under the reactions where those leave room (neither ever gives way).
    val synergyChip = 24f * unit * maxOf(1f, ui.scale * 0.85f)
    val synergyLabel = pauseLabelHeight(overview.synergyLabel, ui, 15f * k, rightWidth * unit) + 6f * unit
    val itemsNeed = pauseLabelHeight(overview.itemsLabel, ui, 15f * k, rightWidth * unit) + 6f * unit + 44f * unit * 0.6f
    val synergyRoom = statsTop - gap - itemsNeed - gap - top - synergyLabel
    val synergyRows = floor((synergyRoom + 6f * unit) / (synergyChip + 6f * unit) + 0.001f).toInt().coerceIn(0, 2)
    var reactionsBottom = leftBottom
    // Where the left column's last section ends: the Conditions take the room under it.
    var leftEnd = -1f
    if (synergyRows > 0) {
        top = drawPauseLabel(overview.synergyLabel, ui, 15f * k, x(right), top, rightWidth * unit) + 6f * unit
        val bottom = top + synergyRows * synergyChip + (synergyRows - 1) * 6f * unit
        top = drawPauseSynergies(overview, ui, x(right), top, rightWidth * unit, bottom, synergyChip, 13f * k, k) + gap
    } else {
        val needed = pauseReactionsBottom(overview, ui, x(0f), reactionsTop, column * unit, leftBottom, 26f * unit, 13f * k, k)
        val leftChip = 24f * unit
        val leftLabel = pauseLabelHeight(overview.synergyLabel, ui, 15f * k, column * unit) + 6f * unit
        // The weapon's synergies come before the conditions: they may take the conditions' row (which then shows where
        // room is left, if any).
        if (needed + gap + leftLabel + leftChip <= columnBottom + 0.5f) {
            reactionsBottom = needed
            val chipsTop = drawPauseLabel(overview.synergyLabel, ui, 15f * k, x(0f), needed + gap, column * unit) + 6f * unit
            leftEnd = drawPauseSynergies(overview, ui, x(0f), chipsTop, column * unit, chipsTop + leftChip, leftChip, 13f * k, k)
        }
    }
    if (leftEnd < 0f) leftEnd = pauseReactionsBottom(overview, ui, x(0f), reactionsTop, column * unit, reactionsBottom, 26f * unit, 13f * k, k)
    drawPauseReactions(overview, ui, x(0f), reactionsTop, column * unit, reactionsBottom, 26f * unit, 13f * k, k)
    drawPauseConditions(overview, ui, x(0f), leftEnd + gap, column * unit, columnBottom, 15f * k, conditionChip, 13f * k, k, detailed = true)
    top = drawPauseLabel(overview.itemsLabel, ui, 15f * k, x(right), top, rightWidth * unit) + 6f * unit
    drawPauseItems(overview, ui, x(right), top, rightWidth * unit, statsTop - gap - top, 44f * unit, k)
    val rowsTop = drawPauseLabel(overview.statsLabel, ui, 15f * k, x(right), statsTop, rightWidth * unit)
    drawStatRows(overview.stats, ui, display, area, unit, right, (rowsTop - area.top) / unit, statRow / unit, 1, rightWidth, 10f, 20f * k, 15f * k)
    // Run statistics.
    val panelsTop = drawPauseLabel(overview.runLabel, ui, 15f * k, x(0f), runTop, width * unit) + 6f * unit
    drawRunPanels(overview.run, ui, x(0f), panelsTop, width * unit, 8f * unit, minOf(58f * unit * maxOf(1f, ui.scale * 0.8f), area.bottom - panelsTop),
        6, k, 9f, 20f)
}

/** A section label (label role, uppercase) fitted to [box]; returns its bottom. */
private fun DrawScope.drawPauseLabel(text: String, measurer: CanvasTextMeasurer, size: Float, x: Float, y: Float, box: Float): Float {
    val layout = fitOverlayText(measurer, text, KkTextRole.LABEL, size, box, uppercase = true)
    drawPauseText(PauseTextKind.LABEL, layout, x, y, Kk.Mute, box)
    return y + layout.kkBoxHeight
}

/** Height of a section label as [drawPauseLabel] draws it. */
private fun pauseLabelHeight(text: String, measurer: CanvasTextMeasurer, size: Float, box: Float): Float =
    fitOverlayText(measurer, text, KkTextRole.LABEL, size, box, uppercase = true).kkBoxHeight

/**
 * Mobile-Pause boards, one flow under the header: the relic matrix (with the reactions beside it
 * on landscape), the cost list, resonance in two columns with the element meters, then (portrait)
 * the reactions and (landscape) the four derived stats in two columns. A section that no longer
 * fits the area at a large text size is left out rather than cut.
 */
private fun DrawScope.drawCompactBuild(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    display: CanvasTextMeasurer,
    area: Rect,
    unit: Float,
    time: Float,
    portrait: Boolean,
) {
    val k = unit / density
    fun x(v: Float) = area.left + v * unit
    fun y(v: Float) = area.top + v * unit
    val width = area.width / unit
    val gap = 8f * unit
    val grow = maxOf(1f, ui.scale * 0.85f)
    drawBuildHeader(overview, ui, display, x(0f), x(width), y(0f), y(2f), 28f * k, 10f * unit, k, 9f, 16f, time, weaponName = false)
    var top = y(34f)
    // Bound relics' costs only: a replaced relic's lingering cost shows in the regular view.
    val bound = overview.relics.count { it != null }
    val tagRow = 15f * unit * grow
    if (!portrait) {
        val relicsBottom = drawPauseRelics(overview, ui, x(0f), top, 196f * unit, 30f * unit, 46f * unit, 9f * unit, k, names = false, time = time)
        val chipsTop = drawPauseLabel(overview.reactionsLabel, ui, 11f * k, x(206f), top, (width - 206f) * unit) + 4f * unit
        val besideBottom = maxOf(relicsBottom, chipsTop + 50f * unit)
        // One row of synergy chips under the reactions, beside the relics, where the reactions leave room (they never give way).
        val reactionsBottom = pauseReactionsBottom(overview, ui, x(206f), chipsTop, (width - 206f) * unit, besideBottom, 22f * unit, 11f * k, k)
        val synergyChip = 18f * unit * grow
        val synergyTop = reactionsBottom + gap
        val synergyFits = synergyTop + pauseLabelHeight(overview.synergyLabel, ui, 11f * k, (width - 206f) * unit) + 4f * unit + synergyChip <=
            besideBottom + 0.5f
        drawPauseReactions(overview, ui, x(206f), chipsTop, (width - 206f) * unit, if (synergyFits) reactionsBottom else besideBottom, 22f * unit,
            11f * k, k)
        if (synergyFits) {
            val synergyChipsTop = drawPauseLabel(overview.synergyLabel, ui, 11f * k, x(206f), synergyTop, (width - 206f) * unit) + 4f * unit
            drawPauseSynergies(overview, ui, x(206f), synergyChipsTop, (width - 206f) * unit, synergyChipsTop + synergyChip, synergyChip, 10.5f * k, k)
        }
        top = besideBottom + gap
        val statRow = 22f * unit * grow
        val elementRow = 12f * unit * grow
        // The seven element meters sit in two columns, so they stand no taller than the three tag rows.
        val elementRows = (STATUS_ELEMENT_COUNT + COMPACT_ELEMENT_COLUMNS - 1) / COMPACT_ELEMENT_COLUMNS
        val resonanceHeight = pauseLabelHeight(overview.resonanceLabel, ui, 11f * k, 236f * unit) + 4f * unit +
            maxOf(3f * tagRow, elementRows * elementRow)
        // Costs come before the derived stats: at a large text size the stats give way to keep two
        // readable lines per cost.
        val costNeed = bound * (2f * 9.5f * k * ui.scale * density * COST_LINE_HEIGHT + 4f * unit)
        val showStats = top + costNeed + gap + resonanceHeight + gap + 2f * statRow <= area.bottom
        val statsTop = if (showStats) area.bottom - 2f * statRow else area.bottom + gap
        // Without room beside the relics, the synergy chips keep one row under the resonance up to the default text
        // size: the costs shrink to leave it (the stats keep their place).
        val synergyRow = if (!synergyFits && ui.scale <= 1.001f) synergyChip + gap else 0f
        top = drawPauseCosts(overview, ui, x(0f), top, width * unit, statsTop - gap - resonanceHeight - gap - synergyRow, 9.5f * k, k,
            maxEntries = bound) + gap
        val rowsTop = drawPauseLabel(overview.resonanceLabel, ui, 11f * k, x(0f), top, 236f * unit) + 4f * unit
        val tagsBottom = drawPauseTags(overview, ui, x(0f), rowsTop, 236f * unit, tagRow, 2, 10.5f * k, k, maxRows = 3)
        val meterTop = drawPauseLabel(overview.elementsLabel, ui, 11f * k, x(248f), top, (width - 248f) * unit) + 4f * unit
        drawPauseElementRows(overview, ui.roles, x(248f), meterTop, (width - 248f) * unit, elementRow, k, columns = COMPACT_ELEMENT_COLUMNS)
        // Without room beside the relics, the synergy chips take one row of the space the costs leave above the
        // stats, if any: the section label, then the chips that fit after it.
        val resonanceBottom = maxOf(tagsBottom, meterTop + elementRows * elementRow)
        val synergyLimit = if (showStats) statsTop - gap else area.bottom
        val lowerTop = resonanceBottom + gap
        var conditionsTop = lowerTop
        if (!synergyFits && lowerTop + synergyChip <= synergyLimit + 0.5f) {
            drawPauseSynergyRow(overview, ui, x(0f), lowerTop, width * unit, synergyChip, unit, k)
            conditionsTop = lowerTop + synergyChip + gap
        }
        // The run's Conditions as one row of chips where the rest leaves it (they never push a section out).
        drawPauseConditions(overview, ui, x(0f), conditionsTop, width * unit, synergyLimit, 11f * k, synergyChip, 10.5f * k, k, detailed = false,
            inlineLabel = true)
        if (showStats) {
            drawStatRows(overview.stats, ui, display, area, unit, 0f, (statsTop - area.top) / unit, statRow / unit, 2, (width - 20f) / 2f, 8f,
                15f * k, 12f * k)
        }
        return
    }
    top = drawPauseRelics(overview, ui, x(0f), top, width * unit, 36f * unit, 56f * unit, 9f * unit, k, names = false, time = time) + gap
    val synergyChip = 18f * unit * grow
    // Up to the default text size the synergy section keeps one row under the reactions (its label beside the chips
    // when it cannot stand above them): the costs and the tag rows give way to it. At a larger size it shows only
    // where the rest leaves room.
    val reserveSynergy = ui.scale <= 1.001f
    val synergyRow = if (reserveSynergy) gap + synergyChip else 0f
    // What follows the costs, without the resonance rows: the costs and up to three rows of tags share the rest.
    val rest = gap + pauseLabelHeight(overview.resonanceLabel, ui, 11f * k, width * unit) + 4f * unit + gap +
        pauseLabelHeight(overview.elementsLabel, ui, 11f * k, width * unit) + 4f * unit + 20f * unit * grow + gap +
        pauseLabelHeight(overview.reactionsLabel, ui, 11f * k, width * unit) + 4f * unit + 20f * unit + synergyRow
    // A larger text size never leaves the costs smaller than the default does: below that, the tags give up rows.
    // Up to the default size the costs keep their full size while the tags give up rows, then shrink rather than
    // take the synergy row (they never count an entry away).
    val costBottom = drawPauseCosts(overview, ui, x(0f), top, width * unit,
        area.bottom - rest - (if (reserveSynergy) 1f else 3f) * tagRow, 9.5f * k, k, maxEntries = bound,
        minScale = if (reserveSynergy) MIN_COST_SCALE else (1f / ui.scale).coerceIn(MIN_COST_SCALE, 1f), limit = area.bottom - rest - tagRow)
    val tagRows = floor((area.bottom - rest - costBottom) / tagRow + 0.001f).toInt().coerceIn(1, 3)
    top = drawPauseLabel(overview.resonanceLabel, ui, 11f * k, x(0f), costBottom + gap, width * unit) + 4f * unit
    top = drawPauseTags(overview, ui, x(0f), top, width * unit, tagRow, 2, 10.5f * k, k, maxRows = tagRows) + gap
    top = drawPauseLabel(overview.elementsLabel, ui, 11f * k, x(0f), top, width * unit) + 4f * unit
    top = drawPauseElements(overview, ui, x(0f), top, width * unit, 20f * unit * grow, k, names = false) + gap
    top = drawPauseLabel(overview.reactionsLabel, ui, 11f * k, x(0f), top, width * unit) + 4f * unit
    // One row of synergy chips under the reactions where the reactions leave room for it (they never give way).
    val reactionsBottom = pauseReactionsBottom(overview, ui, x(0f), top, width * unit, area.bottom, 20f * unit, 10.5f * k, k)
    val synergyTop = reactionsBottom + gap
    val synergyFits = synergyTop + pauseLabelHeight(overview.synergyLabel, ui, 11f * k, width * unit) + 4f * unit + synergyChip <= area.bottom + 0.5f
    val synergyBeside = !synergyFits && synergyTop + synergyChip <= area.bottom + 0.5f
    drawPauseReactions(overview, ui, x(0f), top, width * unit, if (synergyFits || synergyBeside) reactionsBottom else area.bottom, 20f * unit,
        10.5f * k, k)
    val synergyBottom = if (synergyFits) {
        val chipsTop = drawPauseLabel(overview.synergyLabel, ui, 11f * k, x(0f), synergyTop, width * unit) + 4f * unit
        drawPauseSynergies(overview, ui, x(0f), chipsTop, width * unit, chipsTop + synergyChip, synergyChip, 10.5f * k, k)
    } else if (synergyBeside) {
        drawPauseSynergyRow(overview, ui, x(0f), synergyTop, width * unit, synergyChip, unit, k)
        synergyTop + synergyChip
    } else area.bottom
    // The run's Conditions as one row of chips where the rest leaves it (they never push a section out).
    drawPauseConditions(overview, ui, x(0f), synergyBottom + gap, width * unit, area.bottom, 11f * k, synergyChip, 10.5f * k, k, detailed = false,
        inlineLabel = true)
}

/** The synergy section in one row of [chip] height from ([left], [top]): its label in the first 40 % of [width], then the chips that fit. */
private fun DrawScope.drawPauseSynergyRow(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    left: Float,
    top: Float,
    width: Float,
    chip: Float,
    unit: Float,
    k: Float,
) {
    val labelBox = width * 0.4f
    val label = fitOverlayText(ui, overview.synergyLabel, KkTextRole.LABEL, 11f * k, labelBox, uppercase = true)
    drawPauseText(PauseTextKind.LABEL, label, left, top + chip * 0.5f, Kk.Mute, labelBox, valign = KkVAlign.CENTER)
    val chipsLeft = left + label.size.width + 10f * unit
    drawPauseSynergies(overview, ui, chipsLeft, top, left + width - chipsLeft, top + chip, chip, 10.5f * k, k)
}

/**
 * The relic matrix from [top]: bracket stacks ([PauseBracket], named in the regular layout), then
 * the diamonds with each relic's own rune in its element's hue, its rank as pips and the net
 * multiplier of its upside under it. Empty slots show the hatched slot.
 */
private fun DrawScope.drawPauseRelics(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    left: Float,
    top: Float,
    width: Float,
    slot: Float,
    pitch: Float,
    levelStep: Float,
    k: Float,
    names: Boolean,
    time: Float,
): Float {
    val roles = ui.roles
    val half = slot * 0.5f
    val stacks = overview.bracketLevels.coerceIn(0, MAX_BRACKET_STACKS)
    val slotTop = top + stacks * levelStep + if (stacks > 0) levelStep * 0.5f else 0f
    val centerY = slotTop + half
    val firstCenter = left + half + 6f * k * density
    val stroke = 2f * k * density
    for (index in overview.brackets.indices) {
        val bracket = overview.brackets[index]
        val color = if (bracket.interferes) roles.threat else Kk.Bone
        val ax = firstCenter + bracket.from * pitch
        val bx = firstCenter + bracket.to * pitch
        val lineY = slotTop - levelStep * 0.35f - bracket.level * levelStep
        val legBottom = if (bracket.level == 0) slotTop - 2f * k * density else lineY + levelStep - 3f * k * density
        val effect = if (bracket.discovered) null else WorldStrokes.dashEffect
        drawLine(color, Offset(ax - stroke * 0.5f, lineY), Offset(bx + stroke * 0.5f, lineY), stroke, pathEffect = effect)
        drawLine(color, Offset(ax, lineY), Offset(ax, legBottom), stroke)
        drawLine(color, Offset(bx, lineY), Offset(bx, legBottom), stroke)
        if (names) {
            val box = (bx - ax) + pitch * 0.8f
            val name = fitOverlayText(ui, bracket.name, KkTextRole.COND, 13f * k, box, uppercase = true)
            drawPauseText(PauseTextKind.SYNERGY_NAME, name, (ax + bx) * 0.5f, lineY - 3f * k * density, color, box,
                KkAlign.CENTER, KkVAlign.BASELINE)
        }
    }
    val pipHeight = (if (names) 3f else 2.5f) * k
    val pipsTop = slotTop + slot + 4f * k * density
    val multiplierTop = pipsTop + pipHeight * density + 3f * k * density
    var bottom = pipsTop + pipHeight * density
    for (index in overview.relics.indices) {
        val center = Offset(firstCenter + index * pitch, centerY)
        if (center.x + half > left + width + 1f) break
        val relic = overview.relics[index]
        if (relic == null) {
            drawKkRelicSlot(center, Color.Unspecified, sizeDp = slot / density)
            continue
        }
        val hue = relicHue(relic.definition, roles)
        val alpha = if (relic.spent) 0.45f else 1f
        drawKkRelicSlot(center, hue, sizeDp = slot / density, alpha = alpha)
        drawRelicRune(relic.definition, center, half * 0.62f, hue, alpha)
        if (relic.definition.isSovereign) {
            // Sovereign relics carry the gold of Rebirth on their rim.
            drawPath(PauseShapes.diamond(index, center.x, center.y, half + 3f * k * density), Kk.Gold, alpha = 0.7f * alpha,
                style = kkStroke(1.5f * k * density))
        }
        val pipWidth = (if (names) 7f else 5f) * k
        val pipsWidth = relic.maxRank * pipWidth * density + (relic.maxRank - 1) * 2f * k * density
        drawKkPips(Offset(center.x - pipsWidth * 0.5f, pipsTop), relic.maxRank, relic.rank, hue, sizeDp = pipHeight, widthDp = pipWidth,
            gapDp = 2f * k, emptyColor = Kk.Ink4)
        val box = pitch - 4f * k * density
        val multiplier = fitOverlayText(ui, relic.multiplier, KkTextRole.COND, (if (names) 15f else 11f) * k, box, tabular = true)
        drawPauseText(PauseTextKind.MULTIPLIER, multiplier, center.x, multiplierTop, runColor(relic.tone, roles), box, KkAlign.CENTER)
        bottom = maxOf(bottom, multiplierTop + multiplier.kkBoxHeight)
    }
    return bottom
}

/** Bracket stacks reserved over the pause matrix. */
private const val MAX_BRACKET_STACKS = 2

/**
 * The cost list from [top], fitted to end by [bottom]: one entry per liability (bound relics first,
 * then the lingering cost of a replaced one, faded), each a threat stripe, the relic's rune and the
 * cost text on up to three lines. Every entry shares one text size ([PauseCostFit]), never below
 * [minScale] of [size], and is as tall as its own text; at that floor the list may run on to
 * [limit]. Costs never hide behind a toggle; should even that leave entries out, a "+N" mark
 * counts them. Returns the list's bottom.
 */
private fun DrawScope.drawPauseCosts(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    left: Float,
    top: Float,
    width: Float,
    bottom: Float,
    size: Float,
    k: Float,
    maxEntries: Int = Int.MAX_VALUE,
    minScale: Float = MIN_COST_SCALE,
    limit: Float = bottom,
): Float {
    val roles = ui.roles
    val rune = 7f * k * density
    val textLeft = left + rune * 2f + 8f * k * density
    val box = left + width - textLeft
    val count = minOf(overview.costs.size, maxEntries)
    if (count == 0) return top
    val gap = 6f * k * density
    val runeHeight = rune * 2f + 2f * k * density
    val fit = PauseCostFit.of(overview.costs, count, ui, size, box, bottom - top, runeHeight, gap, minScale)
    var y = top
    var shown = 0
    for (index in 0 until count) {
        val height = fit.height(index)
        // An entry that does not fit leaves room for the count of those after it.
        val reserve = if (index < count - 1) fit.moreHeight else 0f
        if (y + height + reserve > maxOf(bottom, limit) + 1f) break
        val cost = overview.costs[index]
        val alpha = if (cost.lingering) 0.55f else 1f
        val text = fit.layout(index)
        drawRect(roles.threat, Offset(left, y + 1f * k * density), Size(2.5f * k * density, height - 2f * k * density), alpha = alpha)
        drawRelicRune(cost.definition, Offset(left + 4f * k * density + rune, y + rune + 1f * k * density), rune, relicHue(cost.definition, roles),
            alpha)
        drawPauseText(PauseTextKind.COST, text, textLeft, y, if (cost.lingering) roles.threat.copy(alpha = 0.7f) else Kk.Bone2, box)
        y += height + gap
        shown++
    }
    if (shown < count) {
        val more = fitOverlayText(ui, moreLabel(count - shown), KkTextRole.MONO, size, box)
        drawPauseText(PauseTextKind.MORE, more, textLeft, y, roles.threat, box)
        y += more.kkBoxHeight + gap
    }
    return y - gap
}

/**
 * The fitted cost list of the last drawn frame (a paused frame redraws the same list): one text
 * size for every entry, the largest from the requested size down to its floor (a share of it) at
 * which all of them, each on up to [COST_LINES] lines without breaking inside a word, fit the
 * room; at the floor the list is as tall as its text. Fitted once per list, measurer, box and room.
 */
private object PauseCostFit {
    private var costs: List<PauseCostLine>? = null
    private var measurer: CanvasTextMeasurer? = null
    private val key = FloatArray(7) { Float.NaN }
    private val layouts = arrayOfNulls<TextLayoutResult>(MAX_COST_LINES)
    private val heights = FloatArray(MAX_COST_LINES)

    /** Height of the "+N" mark of entries left out. */
    var moreHeight = 0f
        private set

    fun layout(index: Int): TextLayoutResult = requireNotNull(layouts[index])

    fun height(index: Int): Float = heights[index]

    fun of(
        costs: List<PauseCostLine>,
        count: Int,
        measurer: CanvasTextMeasurer,
        size: Float,
        box: Float,
        room: Float,
        minHeight: Float,
        gap: Float,
        minScale: Float,
    ): PauseCostFit {
        if (this.costs === costs && this.measurer === measurer && key[0] == count.toFloat() && key[1] == size && key[2] == box &&
            key[3] == room && key[4] == minHeight && key[5] == gap && key[6] == minScale
        ) return this
        this.costs = costs
        this.measurer = measurer
        key[0] = count.toFloat()
        key[1] = size
        key[2] = box
        key[3] = room
        key[4] = minHeight
        key[5] = gap
        key[6] = minScale
        var scale = 1f
        while (true) {
            val last = scale <= minScale + 0.001f
            var total = -gap
            var whole = true
            for (index in 0 until count) {
                val layout = measureKkText(measurer, costs[index].text, measurer.typography.kkStyle(KkTextRole.BODY, size * scale),
                    maxWidth = box, maxLines = COST_LINES)
                whole = whole && layout.fitsWithoutCuts()
                layouts[index] = layout
                heights[index] = maxOf(layout.kkBoxHeight, minHeight)
                total += heights[index] + gap
            }
            if ((whole && total <= room) || last) break
            scale = maxOf(minScale, scale - 0.05f)
        }
        moreHeight = measureKkText(measurer, "+9", measurer.typography.kkStyle(KkTextRole.MONO, size)).kkBoxHeight + gap
        return this
    }
}

/** "+N", the count of entries a full list leaves out (cached: the paused frame redraws it). */
private fun moreLabel(count: Int): String = MoreLabels[count.coerceIn(0, MoreLabels.lastIndex)]

private val MoreLabels = Array(64) { "+$it" }

/** The cost list's smallest text, as a share of its size (see [drawPauseCosts]), and the lines an entry may take. */
private const val MIN_COST_SCALE = 0.6f
private const val COST_LINES = 3

/** A number keeps its unit on its line ("30 %" and "600 ед/с" never break before the unit). */
private fun String.keepUnitsWithNumbers(): String = UnitAfterNumber.replace(this) { it.groupValues[1] + "\u00A0" + it.groupValues[2] }

/**
 * A space between a number and its unit. Units end where no letter follows (an explicit class:
 * `\b` sees only ASCII letters on some platforms, so it never ends a Cyrillic unit).
 */
private val UnitAfterNumber = Regex("([0-9]) (%|ед/с(?![А-Яа-яЁё])|ед(?![А-Яа-яЁё])|u/s(?![A-Za-z])|u(?![A-Za-z])|s(?![A-Za-z])|с(?![А-Яа-яЁё]))")

/** Line height of the body role the costs use, with a little room for glyph overhang. */
private const val COST_LINE_HEIGHT = 1.46f

/**
 * Resonance from [top]: one row per tag with points, in [columns] columns (row-major): the tag's
 * glyph and name, then a ladder of ticks for its points with a notch at each tier threshold, lit
 * in the you color once reached. [maxRows] caps the rows per column; tags past the cap leave a
 * "+N" mark in the last cell.
 */
private fun DrawScope.drawPauseTags(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    left: Float,
    top: Float,
    width: Float,
    row: Float,
    columns: Int,
    size: Float,
    k: Float,
    maxRows: Int = Int.MAX_VALUE,
): Float {
    val roles = ui.roles
    val tags = overview.tags
    if (tags.isEmpty()) return top
    val gap = 12f * k * density
    val cell = (width - gap * (columns - 1)) / columns
    val thresholds = overview.tagThresholds
    val ticks = (thresholds.lastOrNull() ?: 7).coerceIn(3, 9)
    val glyph = row * 0.72f
    // The name keeps about two fifths of the cell; the ladder takes the rest, its ticks up to 16 dp wide.
    val ladderRoom = (cell - glyph - 6f * k * density - 8f * k * density) * 0.58f
    val tick = (ladderRoom / (ticks + (ticks - 1) * 0.45f)).coerceIn(3f, 16f * k * density)
    val tickGap = tick * 0.45f
    val ladder = ticks * tick + (ticks - 1) * tickGap
    val pipHeightPx = (row * 0.34f).coerceAtMost(tick * 1.2f).coerceAtLeast(3f)
    val nameBox = (cell - glyph - 6f * k * density - ladder - 8f * k * density).coerceAtLeast(10f)
    val capacity = if (maxRows == Int.MAX_VALUE) Int.MAX_VALUE else maxRows * columns
    val shown = if (tags.size > capacity) capacity - 1 else tags.size
    if (shown < tags.size) {
        val x = left + (shown % columns) * (cell + gap)
        val more = fitOverlayText(ui, moreLabel(tags.size - shown), KkTextRole.COND, size, cell)
        drawPauseText(PauseTextKind.MORE, more, x, top + (shown / columns) * row + row * 0.5f, Kk.Bone2, cell, valign = KkVAlign.CENTER)
    }
    for (index in 0 until shown) {
        val rowIndex = index / columns
        val tag = tags[index]
        val x = left + (index % columns) * (cell + gap)
        val y = top + rowIndex * row
        val middle = y + row * 0.5f
        drawKkIcon(KkIcon.Tags[tag.tag.ordinal], Offset(x + glyph * 0.5f, middle), glyph, if (tag.tier > 0) Kk.Bone else Kk.Mute)
        val name = fitOverlayText(ui, tag.label, KkTextRole.COND, size, nameBox, uppercase = true)
        // The Keynote tag is listed without points as a muted ghost; it is bracketed at any count.
        drawPauseText(PauseTextKind.TAG_NAME, name, x + glyph + 6f * k * density, middle, when {
            tag.tier > 0 -> Kk.Bone
            tag.points > 0 -> Kk.Bone2
            else -> Kk.Mute
        }, nameBox, valign = KkVAlign.CENTER)
        if (tag.keynote) drawPauseKeynoteBrackets(Rect(x - 3f * k * density, y + 1f * k * density, x + cell + 3f * k * density, y + row - 1f * k * density),
            5f * k * density, 1.5f * k * density)
        val ladderLeft = x + cell - ladder
        val pipTop = middle - pipHeightPx * 0.5f
        drawKkPips(Offset(ladderLeft, pipTop), ticks, tag.points.coerceAtMost(ticks), Kk.Bone, sizeDp = pipHeightPx / density,
            widthDp = tick / density, gapDp = tickGap / density, emptyColor = Kk.Ink4)
        for (threshold in thresholds) {
            if (threshold > ticks) continue
            val notchX = ladderLeft + threshold * (tick + tickGap) - tickGap * 0.5f
            val reached = tag.points >= threshold
            drawRect(if (reached) roles.you else Kk.Mute, Offset(notchX - 0.75f * k * density, pipTop - pipHeightPx * 0.45f),
                Size(1.5f * k * density, pipHeightPx * 1.9f))
        }
    }
    val rows = minOf(maxRows, (tags.size + columns - 1) / columns)
    return top + rows * row
}

/**
 * One cell per element across [width] from [top]: the glyph (ringed on the dominant element), then
 * (with [names]) the element's name under it across the cell, and a meter of one pip per affinity
 * point notched at the affinity tiers. The names share one size, the size the longest of them
 * needs, and sit clear of the ring.
 */
private fun DrawScope.drawPauseElements(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    k: Float,
    names: Boolean,
): Float {
    val roles = ui.roles
    val cells = overview.elements.size.coerceAtLeast(1)
    val cell = width / cells
    val glyph = (height * if (names) 0.36f else 0.62f).coerceAtMost(18f * k * density)
    val ringStroke = 1.5f * k * density
    val ring = glyph * DOMINANT_RING
    val box = cell - 6f * k * density
    val nameSize = if (names) sharedElementNameSize(overview, ui, 12f * k, box) else 0f
    for (index in overview.elements.indices) {
        val element = overview.elements[index]
        val color = roles.element(element.element)
        val cellLeft = left + index * cell
        // The ring stays inside the cell.
        val glyphCenter = if (names) Offset(cellLeft + ring + ringStroke, top + ring + ringStroke) else
            Offset(cellLeft + glyph * 0.6f, top + height * 0.5f)
        val shown = element.affinity > 0
        drawKkIcon(elementIcon(element.element), glyphCenter, glyph, if (shown) color else Kk.Ink4)
        if (element.dominant) drawCircle(color, ring, glyphCenter, style = kkStroke(ringStroke))
        if (element.keynote) drawPauseKeynoteBrackets(Rect(cellLeft, top, cellLeft + box, top + height), 5f * k * density, 1.5f * k * density)
        if (names) {
            val nameTop = glyphCenter.y + ring + ringStroke + 3f * k * density
            val name = fitOverlayText(ui, element.label, KkTextRole.COND, nameSize, box, uppercase = true)
            drawPauseText(PauseTextKind.ELEMENT_NAME, name, cellLeft, nameTop, if (shown) Kk.Bone else Kk.Mute, box)
            drawElementMeter(overview, element, color, cellLeft, nameTop + name.kkBoxHeight + 4f * k * density, box, k)
        } else {
            val meterLeft = cellLeft + glyph * 1.4f
            drawElementMeter(overview, element, color, meterLeft, top + height * 0.5f - 2.5f * k * density, cell - (meterLeft - cellLeft) - 6f * k * density, k)
        }
    }
    return top + height
}

/** The dominant element's ring, as a share of its glyph's size. */
private const val DOMINANT_RING = 0.78f

/** The size (before the text scale) at which every element name fits [box]: the one the longest needs. */
private fun sharedElementNameSize(overview: PauseBuildOverview, ui: CanvasTextMeasurer, size: Float, box: Float): Float {
    var shared = 1f
    for (index in overview.elements.indices) {
        val fitted = fitOverlayText(ui, overview.elements[index].label, KkTextRole.COND, size, box, uppercase = true)
        shared = minOf(shared, fitted.layoutInput.style.fontSize.value / (size * ui.scale))
    }
    return size * shared
}

/** Element meters as rows (compact landscape): glyph then the meter, one element per row. */
private fun DrawScope.drawPauseElementRows(
    overview: PauseBuildOverview,
    roles: KkRolePalette,
    left: Float,
    top: Float,
    width: Float,
    row: Float,
    k: Float,
    columns: Int = 1,
) {
    val glyph = row * 0.72f
    val rows = (overview.elements.size + columns - 1) / columns
    val columnGap = if (columns > 1) 8f * k * density else 0f
    val columnWidth = (width - columnGap * (columns - 1)) / columns
    for (index in overview.elements.indices) {
        val element = overview.elements[index]
        val color = roles.element(element.element)
        // Column-major, so element order reads down the first column, then the next.
        val columnLeft = left + (index / rows) * (columnWidth + columnGap)
        val middle = top + (index % rows) * row + row * 0.5f
        val center = Offset(columnLeft + glyph * 0.6f, middle)
        drawKkIcon(elementIcon(element.element), center, glyph, if (element.affinity > 0) color else Kk.Ink4)
        if (element.dominant) drawCircle(color, glyph * DOMINANT_RING, center, style = kkStroke(1.2f * k * density))
        if (element.keynote) drawPauseKeynoteBrackets(Rect(columnLeft, middle - row * 0.5f, columnLeft + columnWidth, middle + row * 0.5f),
            4f * k * density, 1.2f * k * density)
        drawElementMeter(overview, element, color, columnLeft + glyph * 1.6f, middle - 2.5f * k * density, columnWidth - glyph * 1.6f, k)
    }
}

/** Columns of the landscape compact pause's element meters. */
private const val COMPACT_ELEMENT_COLUMNS = 2

/** One pip per affinity point (eight shown), notched at each affinity tier; reached notches in the element's hue. */
private fun DrawScope.drawElementMeter(overview: PauseBuildOverview, element: PauseElementEntry, color: Color, left: Float, top: Float, width: Float, k: Float) {
    val count = AFFINITY_PIPS
    val gap = 2f * k * density
    val pip = ((width - gap * (count - 1)) / count).coerceIn(2f, 8f * k * density)
    val height = 5f * k * density
    drawKkPips(Offset(left, top), count, element.affinity.coerceAtMost(count), color, sizeDp = height / density, widthDp = pip / density,
        gapDp = gap / density, emptyColor = Kk.Ink4)
    for (threshold in overview.affinityThresholds) {
        if (threshold > count) continue
        val x = left + threshold * (pip + gap) - gap * 0.5f
        drawRect(if (element.affinity >= threshold) color else Kk.Mute, Offset(x - 0.6f * k * density, top - 2f * k * density),
            Size(1.2f * k * density, height + 4f * k * density))
    }
}

private const val AFFINITY_PIPS = 8

/**
 * Reaction chips flowing from [top] to [bottom]: each a plate with both elements' glyphs and the
 * reaction's name, or "?" over a hatch while it is still unknown. Chips that find no room leave a
 * "+N" mark in their place. Without a reachable pair, a muted line says so.
 */
private fun DrawScope.drawPauseReactions(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    left: Float,
    top: Float,
    width: Float,
    bottom: Float,
    chip: Float,
    size: Float,
    k: Float,
) {
    val roles = ui.roles
    val reactions = overview.reactions
    if (reactions.isEmpty()) {
        val text = fitOverlayText(ui, overview.noReactions, KkTextRole.MONO, size * 0.8f, width, uppercase = true)
        drawPauseText(PauseTextKind.EMPTY, text, left, top + chip * 0.5f, Kk.Mute, width, valign = KkVAlign.CENTER)
        return
    }
    val gap = 6f * k * density
    val glyph = chip * 0.5f
    val lead = glyph * 2f + 10f * k * density
    val maxText = width - lead - 10f * k * density
    val count = minOf(reactions.size, ReactionChips.CAPACITY)
    // Place every chip first; when some find no room, the last shown ones give way to the "+N" mark.
    var shown = ReactionChips.place(reactions, count, ui, left, top, width, bottom, chip, gap, lead, maxText, size, k, density)
    var more: TextLayoutResult? = null
    var moreX = 0f
    var moreY = 0f
    while (shown < count) {
        val mark = fitOverlayText(ui, moreLabel(count - shown), KkTextRole.COND, size, maxText)
        val markWidth = mark.size.width + 12f * k * density
        val afterX = if (shown == 0) left else ReactionChips.x(shown - 1) + ReactionChips.width(shown - 1) + gap
        val afterY = if (shown == 0) top else ReactionChips.y(shown - 1)
        if (afterX + markWidth <= left + width + 0.5f && afterY + chip <= bottom + 0.5f) {
            more = mark
            moreX = afterX
            moreY = afterY
            break
        }
        if (afterY + chip + gap + chip <= bottom + 0.5f) {
            more = mark
            moreX = left
            moreY = afterY + chip + gap
            break
        }
        if (shown == 0) break
        shown--
    }
    for (index in 0 until shown) {
        val reaction = reactions[index]
        val x = ReactionChips.x(index)
        val y = ReactionChips.y(index)
        val chipWidth = ReactionChips.width(index)
        val first = roles.element(reaction.id.first.ordinal)
        val second = roles.element(reaction.id.second.ordinal)
        val plate = PauseShapes.slab(index, x, y, x + chipWidth, y + chip, 6f * k * density)
        drawPath(plate, Kk.Ink2)
        if (!reaction.discovered) drawKkHatch(plate)
        // Both elements' glyphs, and a two-tone edge along the plate's foot.
        drawKkIcon(elementIcon(reaction.id.first.ordinal), Offset(x + 6f * k * density + glyph * 0.5f, y + chip * 0.5f), glyph, first)
        drawKkIcon(elementIcon(reaction.id.second.ordinal), Offset(x + 8f * k * density + glyph * 1.5f, y + chip * 0.5f), glyph, second)
        drawRect(first, Offset(x + 6f * k * density, y + chip - 2f * k * density), Size(chipWidth * 0.5f - 6f * k * density, 2f * k * density))
        drawRect(second, Offset(x + chipWidth * 0.5f, y + chip - 2f * k * density), Size(chipWidth * 0.5f - 10f * k * density, 2f * k * density))
        drawPauseText(PauseTextKind.REACTION, ReactionChips.name(index), x + lead, y + chip * 0.5f,
            if (reaction.discovered) Kk.Bone else Kk.Mute, maxText, valign = KkVAlign.CENTER)
    }
    if (more != null) drawPauseText(PauseTextKind.MORE, more, moreX + 6f * k * density, moreY + chip * 0.5f, Kk.Bone2, maxText, valign = KkVAlign.CENTER)
}

/**
 * The weapon's synergy chips flowing from [top] while they fit above [bottom]: a plate with the chip's text
 * and an accent edge (the player's color, the infused element's hue, the harmonizing Relic's aspect).
 * Without any, a muted line says so. Returns the bottom of what it drew.
 */
private fun DrawScope.drawPauseSynergies(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    left: Float,
    top: Float,
    width: Float,
    bottom: Float,
    chip: Float,
    size: Float,
    k: Float,
): Float {
    val roles = ui.roles
    val chips = overview.synergies
    if (chips.isEmpty()) {
        val text = fitOverlayText(ui, overview.noSynergy, KkTextRole.MONO, size * 0.8f, width, uppercase = true)
        drawPauseText(PauseTextKind.EMPTY, text, left, top + chip * 0.5f, Kk.Mute, width, valign = KkVAlign.CENTER)
        return top + chip
    }
    val gap = 6f * k * density
    val pad = 10f * k * density
    val maxText = width - pad * 2f
    var x = left
    var y = top
    var drawnBottom = top
    for (index in chips.indices) {
        val entry = chips[index]
        val text = fitOverlayText(ui, entry.text, KkTextRole.COND, size, maxText, uppercase = true)
        val chipWidth = text.size.width + pad * 2f
        if (x > left && x + chipWidth > left + width + 0.5f) {
            x = left
            y += chip + gap
        }
        if (y + chip > bottom + 0.5f) break
        val accent = when (entry.accent) {
            PauseSynergyAccent.YOU -> roles.you
            PauseSynergyAccent.ELEMENT -> overview.synergyElement?.let { roles.element(it.ordinal) } ?: roles.you
            PauseSynergyAccent.RELIC -> overview.synergyRelic?.let { relicHue(it, roles) } ?: roles.you
        }
        val plate = PauseShapes.synergySlab(index, x, y, x + chipWidth, y + chip, 6f * k * density)
        drawPath(plate, Kk.Ink2)
        drawRect(accent, Offset(x + 6f * k * density, y + chip - 2f * k * density), Size(chipWidth - 12f * k * density, 2f * k * density))
        drawPauseText(PauseTextKind.SYNERGY, text, x + pad, y + chip * 0.5f, Kk.Bone, maxText, valign = KkVAlign.CENTER)
        drawnBottom = y + chip
        x += chipWidth + gap
    }
    return drawnBottom
}

/** The bottom of the reaction chips [drawPauseReactions] would draw from [top]: all of [bottom] when some find no room. */
private fun DrawScope.pauseReactionsBottom(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    left: Float,
    top: Float,
    width: Float,
    bottom: Float,
    chip: Float,
    size: Float,
    k: Float,
): Float {
    val reactions = overview.reactions
    if (reactions.isEmpty()) return top + chip
    val gap = 6f * k * density
    val lead = chip * 0.5f * 2f + 10f * k * density
    val maxText = width - lead - 10f * k * density
    val count = minOf(reactions.size, ReactionChips.CAPACITY)
    val shown = ReactionChips.place(reactions, count, ui, left, top, width, bottom, chip, gap, lead, maxText, size, k, density)
    return if (shown < count || shown == 0) bottom else ReactionChips.y(shown - 1) + chip
}

/** Where the reaction chips of the last drawn list sit (draw-thread scratch, no allocation). */
private object ReactionChips {
    val CAPACITY = ReactionId.entries.size
    private val xs = FloatArray(CAPACITY)
    private val ys = FloatArray(CAPACITY)
    private val widths = FloatArray(CAPACITY)
    private val names = arrayOfNulls<TextLayoutResult>(CAPACITY)

    fun x(index: Int) = xs[index]
    fun y(index: Int) = ys[index]
    fun width(index: Int) = widths[index]
    fun name(index: Int): TextLayoutResult = requireNotNull(names[index])

    /** Flows [count] chips in rows from [left], [top]; returns how many fit above [bottom]. */
    fun place(
        reactions: List<PauseReactionEntry>,
        count: Int,
        ui: CanvasTextMeasurer,
        left: Float,
        top: Float,
        width: Float,
        bottom: Float,
        chip: Float,
        gap: Float,
        lead: Float,
        maxText: Float,
        size: Float,
        k: Float,
        density: Float,
    ): Int {
        var x = left
        var y = top
        for (index in 0 until count) {
            val name = fitOverlayText(ui, reactions[index].name, KkTextRole.COND, size, maxText, uppercase = true)
            val chipWidth = lead + name.size.width + 10f * k * density
            if (x > left && x + chipWidth > left + width + 0.5f) {
                x = left
                y += chip + gap
            }
            if (y + chip > bottom + 0.5f) return index
            xs[index] = x
            ys[index] = y
            widths[index] = chipWidth
            names[index] = name
            x += chipWidth + gap
        }
        return count
    }
}

/**
 * The owned items as an icon grid from [top] within [height]: the largest pitch up to [pitch]
 * that fits them all (never below 60 % of it), stacks as each icon's arc, a threat hatch over items
 * the bound relics disable and a gold rim on Reborn copies. An active item draws as its Z/X/C slot in
 * small (a sheared plate with its effect's mark and rank pips, as its card's plate shows them) with
 * its slot's key on a bone key cap at the cell's top-right corner (its card's Active chip). Items
 * past the grid leave a "+N" mark.
 */
private fun DrawScope.drawPauseItems(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    pitch: Float,
    k: Float,
) {
    val roles = ui.roles
    val items = overview.items
    if (items.isEmpty()) {
        val text = fitOverlayText(ui, overview.noItems, KkTextRole.MONO, 11f * k, width, uppercase = true)
        drawPauseText(PauseTextKind.EMPTY, text, left, top + pitch * 0.4f, Kk.Mute, width, valign = KkVAlign.CENTER)
        return
    }
    var cell = pitch
    while (cell > pitch * 0.6f) {
        val perRow = floor(width / cell).toInt().coerceAtLeast(1)
        if (ceil(items.size / perRow.toFloat()) * cell <= height) break
        cell -= 2f * k * density
    }
    val perRow = floor(width / cell).toInt().coerceAtLeast(1)
    val rows = floor(height / cell).toInt().coerceAtLeast(1)
    // Items past the grid leave a "+N" mark in its last cell.
    val shown = if (items.size > perRow * rows) perRow * rows - 1 else items.size
    if (shown < items.size) {
        val center = Offset(left + (shown % perRow) * cell + cell * 0.5f, top + (shown / perRow) * cell + cell * 0.5f)
        val more = fitOverlayText(ui, moreLabel(items.size - shown), KkTextRole.COND, 15f * k, cell)
        drawPauseText(PauseTextKind.MORE, more, center.x, center.y, Kk.Bone2, cell, KkAlign.CENTER, KkVAlign.CENTER)
    }
    for (index in 0 until shown) {
        val item = items[index]
        val center = Offset(left + (index % perRow) * cell + cell * 0.5f, top + (index / perRow) * cell + cell * 0.5f)
        val radius = cell * 0.36f
        val hue = item.item.elements.firstOrNull()?.let { roles.element(it.ordinal) } ?: roles.kinetic
        val effect = item.active
        if (effect != null) {
            // Its Z/X/C slot in small: a sheared Ink2 plate edged in its hue, the effect's mark and rank pips on it.
            val half = cell * ACTIVE_PLATE_SHARE * 0.5f
            val plate = PauseShapes.activePlate(index, center.x - half, center.y - half, center.x + half, center.y + half, half * 0.34f)
            drawPath(plate, Kk.Ink2)
            drawPath(plate, hue, alpha = 0.6f, style = kkStroke(1.2f * k * density))
            drawActiveItemMark(effect, center, half * 2f, hue, item.stacks, item.item.maxStacks, plate = Kk.Ink2)
        } else {
            drawItemImage(item.item, center, radius, hue, item.stacks)
        }
        if (item.reborn) drawCircle(Kk.Gold, radius * 1.18f, center, style = kkStroke(1.5f * k * density))
        if (item.suppressed) {
            drawCircle(kkFillBrush(KkFillKind.HATCH, roles.threat), radius * 1.12f, center)
            drawCircle(roles.threat, radius * 1.12f, center, alpha = 0.8f, style = kkStroke(1.2f * k * density))
        }
        item.slotKey?.let { key -> drawPauseKeyCap(index, key, ui, center.x + cell * 0.5f, center.y - cell * 0.5f, cell, k) }
    }
}

/**
 * The key of an active item's slot on a bone key cap (ink mono, at least square) in the top-right corner of its
 * [cell] whose top-right point is ([right], [top]). The cap keeps to a third of the cell like the grid's icons (the
 * label follows the text size up to the cap's height); a long key name shrinks to the cell's width.
 */
private fun DrawScope.drawPauseKeyCap(index: Int, key: String, ui: CanvasTextMeasurer, right: Float, top: Float, cell: Float, k: Float) {
    val inset = cell * 0.04f
    val pad = cell * 0.06f
    val box = cell - inset * 2f - pad * 2f
    val height = cell * KEY_CAP_SHARE
    val size = min(KEY_CAP_SP * k, height / density * 0.62f)
    val label = fitOverlayText(ui, key, KkTextRole.MONO, size, box, uppercase = true, minScale = 0.3f)
    val width = max(label.size.width + pad * 2f, height)
    val left = right - inset - width
    val capTop = top + inset
    drawPath(PauseShapes.keyCap(index, left, capTop, left + width, capTop + height, height * 0.22f), Kk.Bone)
    drawPauseText(PauseTextKind.KEY, label, left + width * 0.5f, capTop + height * 0.5f, Kk.Ink, box, KkAlign.CENTER, KkVAlign.CENTER)
}

/** An active's key cap label in the pause grid (board sp, before the text factor). */
private const val KEY_CAP_SP = 10f

/** An active's key cap is this share of its grid cell tall. */
private const val KEY_CAP_SHARE = 0.32f

/** An active's slot plate spans this share of its grid cell. */
private const val ACTIVE_PLATE_SHARE = 0.8f

/** The element's hue of a relic (the preset's volt for Kinetic, gold for Sovereign) in this color vision palette. */
internal fun relicHue(definition: RelicDefinition, roles: KkRolePalette): Color = definition.aspect.overlayColor(roles)

/** Shapes the paused frame redraws in place: one path per slot, rebuilt only when its geometry moves. */
private object PauseShapes {
    private val diamonds = KkPathCache(8)
    private val slabs = KkPathCache(16)
    private val synergySlabs = KkPathCache(6)
    private val keyCaps = KkPathCache(ContentBounds.SKILL_SLOTS)
    private val activePlates = KkPathCache(ContentBounds.SKILL_SLOTS)

    fun diamond(slot: Int, cx: Float, cy: Float, r: Float) = diamonds.diamond(slot.coerceIn(0, 7), cx, cy, r)

    fun slab(slot: Int, left: Float, top: Float, right: Float, bottom: Float, cut: Float) =
        slabs.slab(slot.coerceIn(0, 15), left, top, right, bottom, cut)

    fun synergySlab(slot: Int, left: Float, top: Float, right: Float, bottom: Float, cut: Float) =
        synergySlabs.slab(slot.coerceIn(0, 5), left, top, right, bottom, cut)

    /** The slot plate of the active in grid cell [cell] (the actives lead the grid, so one per Z/X/C slot). */
    fun activePlate(cell: Int, left: Float, top: Float, right: Float, bottom: Float, cut: Float) =
        activePlates.slab(cell.coerceIn(0, ContentBounds.SKILL_SLOTS - 1), left, top, right, bottom, cut)

    /** The key cap of the active in grid cell [cell] (the actives lead the grid, so one per Z/X/C slot). */
    fun keyCap(cell: Int, left: Float, top: Float, right: Float, bottom: Float, cut: Float) =
        keyCaps.slab(cell.coerceIn(0, ContentBounds.SKILL_SLOTS - 1), left, top, right, bottom, cut)
}

/**
 * "Build" heading at [left] and the tags right-aligned at [right] on one row: when both do not
 * fit, the tags shrink first (to 60 %), then the heading. [weaponName] names the weapon on its chip
 * (the regular board); phones show its icon and level.
 */
private fun DrawScope.drawBuildHeader(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    display: CanvasTextMeasurer,
    left: Float,
    right: Float,
    headingTop: Float,
    tagsTop: Float,
    headingSize: Float,
    gap: Float,
    k: Float,
    badgeLabel: Float,
    badgeNumber: Float,
    time: Float,
    weaponName: Boolean,
) {
    val heading = measureKkText(ui, overview.heading, ui.typography.condStyle(headingSize), uppercase = true).size.width
    val natural = buildTagsWidth(overview, ui, display, k, 1f, badgeLabel, badgeNumber, weaponName)
    val tagScale = ((right - left - heading - gap) / natural).coerceIn(0.6f, 1f)
    val tagsLeft = drawBuildTags(overview, ui, display, right, tagsTop, k, tagScale, badgeLabel, badgeNumber, time, weaponName)
    val box = tagsLeft - left - gap
    drawPauseText(PauseTextKind.HEADING, fitOverlayText(ui, overview.heading, KkTextRole.COND, headingSize, box, uppercase = true),
        left, headingTop, Kk.Bone, box)
}

private fun DrawScope.buildTagsWidth(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    display: CanvasTextMeasurer,
    k: Float,
    tagScale: Float,
    badgeLabel: Float,
    badgeNumber: Float,
    weaponName: Boolean,
): Float {
    val badge = kkLevelBadgeSize(display, overview.level, density, overview.levelLabel, badgeLabel, badgeNumber)
    val tagHeight = 22f * k * maxOf(1f, ui.scale) * tagScale
    val fontSize = 13f * k * tagScale
    return badge.width + 8f * density + 30f * density + kkTagSize(ui, overview.rebirth, density, tagHeight, fontSize).width +
        kkTagSize(ui, overview.form, density, tagHeight, fontSize).width + tagHeight * density * 0.8f +
        weaponChipWidth(overview, ui, tagHeight, fontSize, weaponName)
}

/** Width of the weapon chip: its icon, the name when shown, and its level. */
private fun DrawScope.weaponChipWidth(overview: PauseBuildOverview, ui: CanvasTextMeasurer, tagHeight: Float, fontSize: Float, weaponName: Boolean): Float =
    tagHeight * density * 0.9f + kkTagSize(ui, overview.weaponLevelText, density, tagHeight, fontSize).width +
        if (weaponName) kkTagSize(ui, overview.weaponName, density, tagHeight, fontSize).width - 10f * density else 0f

/**
 * One list of build stats: labels share one fitted size; values right-aligned in their column
 * ([PauseStatFit]). [base] measures at the default text size.
 */
private fun DrawScope.drawStatRows(
    stats: List<OverlayStat>,
    ui: CanvasTextMeasurer,
    base: CanvasTextMeasurer,
    area: Rect,
    unit: Float,
    firstLeft: Float,
    top: Float,
    rowHeight: Float,
    columns: Int,
    column: Float,
    valueGap: Float,
    valueSize: Float,
    labelSize: Float,
) {
    fun x(v: Float) = area.left + v * unit
    fun y(v: Float) = area.top + v * unit
    val roles = ui.roles
    val fit = PauseStatFit.of(stats, ui, base, column * unit, valueGap * unit, valueSize, labelSize)
    for (index in stats.indices) {
        val stat = stats[index]
        val left = firstLeft + if (index % columns == 0) 0f else column + 20f
        val bottom = y(top + (index / columns + 1) * rowHeight)
        val value = fit.value(index)
        drawPauseText(PauseTextKind.STAT_VALUE, value, x(left + column), bottom - 6f * unit, if (stat.highlight) roles.you else Kk.Bone,
            fit.valueBox(index), KkAlign.END, KkVAlign.BASELINE)
        drawPauseText(PauseTextKind.STAT_LABEL, fit.label(index), x(left), bottom - 6f * unit, Kk.Bone,
            column * unit - value.size.width - valueGap * unit, valign = KkVAlign.BASELINE)
        drawRect(Kk.Line, Offset(x(left), bottom - unit), Size(column * unit, unit))
    }
}

/**
 * The fitted rows of the last drawn stat list (a paused frame redraws the same list every frame,
 * so they are fitted once). At the default text size and below, values fit 45 % of the column
 * and the labels shrink together to the rest. Above the default, labels and values grow together
 * from the default fit only as far as every row allows, so a larger setting never leaves a label
 * smaller than the default did (long Russian labels in a phone's two columns).
 */
private object PauseStatFit {
    private var stats: List<OverlayStat>? = null
    private var ui: CanvasTextMeasurer? = null
    private var base: CanvasTextMeasurer? = null
    private val key = FloatArray(4) { Float.NaN }
    private var labels = arrayOfNulls<TextLayoutResult>(0)
    private var values = arrayOfNulls<TextLayoutResult>(0)
    private var valueBoxes = FloatArray(0)

    fun label(index: Int): TextLayoutResult = requireNotNull(labels[index])
    fun value(index: Int): TextLayoutResult = requireNotNull(values[index])
    fun valueBox(index: Int): Float = valueBoxes[index]

    fun of(
        stats: List<OverlayStat>,
        ui: CanvasTextMeasurer,
        base: CanvasTextMeasurer,
        column: Float,
        gap: Float,
        valueSize: Float,
        labelSize: Float,
    ): PauseStatFit {
        if (this.stats === stats && this.ui === ui && this.base === base && key[0] == column && key[1] == gap &&
            key[2] == valueSize && key[3] == labelSize
        ) return this
        this.stats = stats
        this.ui = ui
        this.base = base
        key[0] = column
        key[1] = gap
        key[2] = valueSize
        key[3] = labelSize
        fit(stats, if (ui.scale <= 1f) ui else base, column, gap, valueSize, labelSize)
        if (ui.scale > 1f) grow(stats, base, ui.scale, column, gap)
        return this
    }

    private fun fit(stats: List<OverlayStat>, measurer: CanvasTextMeasurer, column: Float, gap: Float, valueSize: Float, labelSize: Float) {
        labels = arrayOfNulls(stats.size)
        values = arrayOfNulls(stats.size)
        valueBoxes = FloatArray(stats.size) { column * 0.45f }
        var shared = 1f
        for (index in stats.indices) {
            val value = fitOverlayText(measurer, stats[index].value, KkTextRole.COND, valueSize, column * 0.45f, tabular = true)
            values[index] = value
            val fitted = fitOverlayText(measurer, stats[index].label, KkTextRole.BODY, labelSize, column - value.size.width - gap)
            shared = minOf(shared, fitted.layoutInput.style.fontSize.value / (labelSize * measurer.scale))
        }
        for (index in stats.indices) {
            labels[index] = fitOverlayText(measurer, stats[index].label, KkTextRole.BODY, labelSize * shared,
                column - value(index).size.width - gap)
        }
    }

    /** Grows the default fit (measured by [base], scale 1) by up to [limit], in 5 % steps. */
    private fun grow(stats: List<OverlayStat>, base: CanvasTextMeasurer, limit: Float, column: Float, gap: Float) {
        val labelFont = label(0).layoutInput.style.fontSize.value
        val valueFonts = FloatArray(stats.size) { value(it).layoutInput.style.fontSize.value }
        val grownLabels = arrayOfNulls<TextLayoutResult>(stats.size)
        val grownValues = arrayOfNulls<TextLayoutResult>(stats.size)
        var growth = limit
        while (growth > 1.001f) {
            var fits = true
            for (index in stats.indices) {
                val label = measureKkText(base, stats[index].label, base.typography.kkStyle(KkTextRole.BODY, labelFont * growth))
                val value = measureKkText(base, stats[index].value, base.typography.kkStyle(KkTextRole.COND, valueFonts[index] * growth, tabular = true))
                if (label.size.width + gap + value.size.width > column) {
                    fits = false
                    break
                }
                grownLabels[index] = label
                grownValues[index] = value
            }
            if (fits) {
                labels = grownLabels
                values = grownValues
                for (index in stats.indices) valueBoxes[index] = column - gap - label(index).size.width
                return
            }
            growth = maxOf(1f, growth - 0.05f)
        }
    }
}

/** Level badge (display type), rebirth tag and form tag, right-aligned at [right]; returns their left edge. */
private fun DrawScope.drawBuildTags(
    overview: PauseBuildOverview,
    ui: CanvasTextMeasurer,
    display: CanvasTextMeasurer,
    right: Float,
    top: Float,
    k: Float,
    tagScale: Float,
    badgeLabel: Float,
    badgeNumber: Float,
    time: Float,
    weaponName: Boolean,
): Float {
    val badge = kkLevelBadgeSize(display, overview.level, density, overview.levelLabel, badgeLabel, badgeNumber)
    var x = right - badge.width - 8f * density
    drawKkLevelBadge(display, Offset(x, top), overview.level, overview.levelLabel, time, 1f, badgeLabel, badgeNumber,
        maxLevel = overview.levelCap, apex = overview.apex, apexActive = overview.apexActive)
    val tagHeight = 22f * k * maxOf(1f, ui.scale) * tagScale
    val tagTop = top + (badge.height - tagHeight * density) * 0.5f
    val fontSize = 13f * k * tagScale
    val rebirth = kkTagSize(ui, overview.rebirth, density, tagHeight, fontSize)
    x -= 10f * density + rebirth.width
    // Gold means Rebirth: the tier chip turns gold from the first Rebirth on.
    if (overview.rebirthLevel > 0) {
        drawKkTag(ui, overview.rebirth, Offset(x, tagTop), KkTagVariant.DEFAULT, tagHeight, fontSize, background = Kk.Gold, foreground = Kk.Ink)
    } else {
        drawKkTag(ui, overview.rebirth, Offset(x, tagTop), KkTagVariant.LINE, tagHeight, fontSize)
    }
    recordPauseTag(ui, overview.rebirth, x, tagTop, rebirth)
    val form = kkTagSize(ui, overview.form, density, tagHeight, fontSize)
    val iconSpace = tagHeight * density * 0.8f
    x -= 10f * density + form.width + iconSpace
    val formRect = PauseRects.of(2, x, tagTop, x + form.width + iconSpace, tagTop + tagHeight * density)
    drawKkSlab(formRect, Kk.Bone, 6f * density)
    drawKkIcon(overview.formIcon, Offset(formRect.left + 6f * density + iconSpace * 0.45f, formRect.center.y), iconSpace * 0.62f, Kk.Ink)
    drawKkTag(ui, overview.form, Offset(x + iconSpace, tagTop), KkTagVariant.BONE, tagHeight, fontSize, background = Color.Transparent)
    recordPauseTag(ui, overview.form, x, tagTop, Size(form.width + iconSpace, form.height))
    // The run's weapon: its icon in the you color, its name (regular board) and its level, gold-lit
    // at the last mastery.
    val chip = weaponChipWidth(overview, ui, tagHeight, fontSize, weaponName)
    x -= 10f * density + chip
    val chipRect = PauseRects.of(1, x, tagTop, x + chip, tagTop + tagHeight * density)
    drawKkSlab(chipRect, Kk.Ink3, 6f * density)
    val weaponIcon = tagHeight * density * 0.9f
    drawKkIcon(overview.weaponIcon, Offset(chipRect.left + weaponIcon * 0.6f, chipRect.center.y), weaponIcon * 0.62f, ui.roles.you)
    var textX = x + weaponIcon - 4f * density
    if (weaponName) {
        val name = drawKkTag(ui, overview.weaponName, Offset(textX, tagTop), KkTagVariant.DEFAULT, tagHeight, fontSize,
            background = Color.Transparent, foreground = Kk.Bone)
        textX = name.right - 10f * density
    }
    val levelColor = if (overview.masteryTier == WeaponMastery.entries.last()) Kk.RLegend else Kk.Bone2
    drawKkTag(ui, overview.weaponLevelText, Offset(textX, tagTop), KkTagVariant.DEFAULT, tagHeight, fontSize, background = Color.Transparent,
        foreground = levelColor)
    recordPauseTag(ui, if (weaponName) overview.weaponName else overview.weaponLevelText, x, tagTop, Size(chip, tagHeight * density))
    return x
}

/** Tags are drawn (and sized to their label) by the foundation; the probe records their plates. */
private fun recordPauseTag(measurer: CanvasTextMeasurer, text: String, x: Float, y: Float, size: Size) {
    val records = PauseTextProbe.records ?: return
    records += PauseTextRecord(PauseTextKind.TAG, measureKkText(measurer, text, measurer.typography.labelStyle(13f)), Rect(Offset(x, y), size),
        Float.POSITIVE_INFINITY)
}

/**
 * Run statistic panels in one row ([PauseRunFit]): labels share one fitted size, as do values;
 * panels are equal unless a value or label needs more room.
 */
private fun DrawScope.drawRunPanels(
    run: List<PauseRunStat>,
    measurer: CanvasTextMeasurer,
    left: Float,
    top: Float,
    width: Float,
    gap: Float,
    height: Float,
    columns: Int,
    k: Float,
    labelSize: Float,
    valueSize: Float,
) {
    val roles = measurer.roles
    val pad = 10f * k * density
    val fit = PauseRunFit.of(run, measurer, width, gap, pad, minOf(columns, run.size), labelSize * k, valueSize * k)
    var x = left
    for (index in 0 until fit.count) {
        val stat = run[index]
        val cell = fit.width(index)
        drawRect(Kk.Ink2, Offset(x, top), Size(cell, height))
        val room = cell - pad * 2f
        drawPauseText(PauseTextKind.RUN_LABEL, fit.label(index), x + pad, top + pad * 0.9f, Kk.Mute, room)
        drawPauseText(PauseTextKind.RUN_VALUE, fit.value(index), x + pad, top + height - pad * 0.9f, runColor(stat.tone, roles), room,
            valign = KkVAlign.BASELINE)
        x += cell + gap
    }
}

/**
 * The fitted run panels of the last drawn row. Panels share the row equally while every label and
 * value fits its panel; a panel whose text needs more (a long Russian total such as "184,3 тыс.",
 * "Лучшая серия" at a large text size) widens and the others give way evenly
 * ([overlayRowWidths]). Labels then share one fitted size and values another, so no panel's text
 * is smaller than its neighbours'.
 */
private object PauseRunFit {
    private var run: List<PauseRunStat>? = null
    private var measurer: CanvasTextMeasurer? = null
    private val key = FloatArray(5) { Float.NaN }
    var count = 0
        private set
    private var widths = FloatArray(0)
    private var labels = arrayOfNulls<TextLayoutResult>(0)
    private var values = arrayOfNulls<TextLayoutResult>(0)

    fun width(index: Int): Float = widths[index]
    fun label(index: Int): TextLayoutResult = requireNotNull(labels[index])
    fun value(index: Int): TextLayoutResult = requireNotNull(values[index])

    fun of(run: List<PauseRunStat>, measurer: CanvasTextMeasurer, width: Float, gap: Float, pad: Float, count: Int, labelSize: Float, valueSize: Float): PauseRunFit {
        if (this.run === run && this.measurer === measurer && this.count == count && key[0] == width && key[1] == gap && key[2] == pad &&
            key[3] == labelSize && key[4] == valueSize
        ) return this
        this.run = run
        this.measurer = measurer
        this.count = count
        key[0] = width
        key[1] = gap
        key[2] = pad
        key[3] = labelSize
        key[4] = valueSize
        val typography = measurer.typography
        val content = FloatArray(count) { index ->
            maxOf(
                measureKkText(measurer, run[index].label, typography.kkStyle(KkTextRole.MONO, labelSize), uppercase = true).size.width,
                measureKkText(measurer, run[index].value, typography.kkStyle(KkTextRole.WIDE, valueSize, tabular = true)).size.width,
            ).toFloat()
        }
        widths = overlayRowWidths(content, FloatArray(count) { pad * 2f }, FloatArray(count) { 1f }, width - gap * (count - 1)).first
        var labelScale = 1f
        var valueScale = 1f
        for (index in 0 until count) {
            val room = widths[index] - pad * 2f
            val label = fitOverlayText(measurer, run[index].label, KkTextRole.MONO, labelSize, room, uppercase = true, minScale = 0.65f)
            labelScale = minOf(labelScale, label.layoutInput.style.fontSize.value / (labelSize * measurer.scale))
            val value = fitOverlayText(measurer, run[index].value, KkTextRole.WIDE, valueSize, room, tabular = true, minScale = 0.3f)
            valueScale = minOf(valueScale, value.layoutInput.style.fontSize.value / (valueSize * measurer.scale))
        }
        labels = arrayOfNulls(count)
        values = arrayOfNulls(count)
        for (index in 0 until count) {
            val room = widths[index] - pad * 2f
            labels[index] = fitOverlayText(measurer, run[index].label, KkTextRole.MONO, labelSize * labelScale, room, uppercase = true, minScale = 0.65f)
            values[index] = fitOverlayText(measurer, run[index].value, KkTextRole.WIDE, valueSize * valueScale, room, tabular = true, minScale = 0.3f)
        }
        return this
    }
}

/**
 * Widths (px) of a row of boxes sharing [available] px, and one factor for all their text. A box
 * needs its content ([contents], px at full size) plus [pads]. With room to spare, the boxes with
 * a weight share the rest by [weights] and never get less than they need (weight 0 keeps the
 * need). Without room, every content shrinks by the same factor.
 */
internal fun overlayRowWidths(contents: FloatArray, pads: FloatArray, weights: FloatArray, available: Float): Pair<FloatArray, Float> {
    val count = contents.size
    val widths = FloatArray(count)
    val need = FloatArray(count) { contents[it] + pads[it] }
    if (need.sum() > available) {
        val factor = ((available - pads.sum()) / contents.sum()).coerceIn(0f, 1f)
        for (index in 0 until count) widths[index] = contents[index] * factor + pads[index]
        return widths to factor
    }
    val fixed = BooleanArray(count) { weights[it] <= 0f }
    while (true) {
        var rest = available
        var weight = 0f
        for (index in 0 until count) if (fixed[index]) rest -= need[index] else weight += weights[index]
        if (weight <= 0f) {
            for (index in 0 until count) widths[index] = need[index]
            return widths to 1f
        }
        val share = rest / weight
        var changed = false
        for (index in 0 until count) if (!fixed[index] && weights[index] * share < need[index]) {
            fixed[index] = true
            changed = true
        }
        if (!changed) {
            for (index in 0 until count) widths[index] = if (fixed[index]) need[index] else weights[index] * share
            return widths to 1f
        }
    }
}

/** A few reusable rects for the paused frame (redrawn every frame with the same geometry). */
private object PauseRects {
    private val rects = arrayOfNulls<Rect>(3)

    fun of(slot: Int, left: Float, top: Float, right: Float, bottom: Float): Rect {
        val cached = rects[slot]
        if (cached != null && cached.left == left && cached.top == top && cached.right == right && cached.bottom == bottom) return cached
        return Rect(left, top, right, bottom).also { rects[slot] = it }
    }
}

/**
 * A pause text layout that fits [maxWidth] in [maxLines] without breaking inside a word: the
 * [role] font shrinks (to [minScale] of [size]) and, only if it still does not fit, further until
 * it does. Nothing is ever cut with an ellipsis. Memoized per text and box, so the paused frame
 * redraws without measuring.
 */
internal fun fitOverlayText(
    measurer: CanvasTextMeasurer,
    text: String,
    role: KkTextRole,
    size: Float,
    maxWidth: Float,
    maxLines: Int = 1,
    uppercase: Boolean = false,
    minScale: Float = 0.7f,
    tabular: Boolean = false,
): TextLayoutResult = PauseFitMemo.find(measurer, text, role, size, maxWidth, maxLines, uppercase, tabular)
    ?: measureFitted(measurer, text, role, size, maxWidth, maxLines, uppercase, minScale, tabular)
        .also { PauseFitMemo.put(measurer, text, role, size, maxWidth, maxLines, uppercase, tabular, it) }

private fun measureFitted(
    measurer: CanvasTextMeasurer,
    text: String,
    role: KkTextRole,
    size: Float,
    maxWidth: Float,
    maxLines: Int,
    uppercase: Boolean,
    minScale: Float,
    tabular: Boolean,
): TextLayoutResult {
    val typography = measurer.typography
    val words = (if (uppercase) text.uppercase() else text).split(' ').filter(String::isNotEmpty)
    fun fitted(scale: Float): TextLayoutResult? {
        val style = typography.kkStyle(role, size * scale, tabular)
        if (maxLines == 1) {
            val layout = measureKkText(measurer, text, style, uppercase)
            return layout.takeIf { it.size.width <= maxWidth }
        }
        val widest = words.maxOfOrNull { measureKkText(measurer, it, style).size.width } ?: 0
        if (widest > maxWidth) return null
        return measureKkText(measurer, text, style, uppercase, maxWidth, maxLines).takeIf { !it.hasVisualOverflow }
    }
    var scale = 1f
    while (scale >= minScale - 0.001f) {
        fitted(scale)?.let { return it }
        scale -= 0.05f
    }
    // Below the preferred floor the text keeps shrinking rather than being cut.
    scale = minScale - 0.05f
    while (scale > 0.2f) {
        fitted(scale)?.let { return it }
        scale -= 0.05f
    }
    return measureKkText(measurer, text, typography.kkStyle(role, size * 0.2f, tabular), uppercase, maxWidth, maxLines)
}

/** Recently fitted pause texts (draw-thread confined, round robin). */
private object PauseFitMemo {
    private const val CAPACITY = 128
    private val texts = arrayOfNulls<String>(CAPACITY)
    private val roles = arrayOfNulls<KkTextRole>(CAPACITY)
    private val delegates = arrayOfNulls<Any>(CAPACITY)
    private val floats = FloatArray(CAPACITY * 3)
    private val ints = IntArray(CAPACITY * 3)
    private val results = arrayOfNulls<TextLayoutResult>(CAPACITY)
    private var next = 0

    fun find(
        measurer: CanvasTextMeasurer,
        text: String,
        role: KkTextRole,
        size: Float,
        maxWidth: Float,
        maxLines: Int,
        uppercase: Boolean,
        tabular: Boolean,
    ): TextLayoutResult? {
        for (index in 0 until CAPACITY) {
            if (roles[index] !== role || delegates[index] !== measurer.delegate) continue
            val f = index * 3
            val i = index * 3
            if (floats[f] == size && floats[f + 1] == maxWidth && floats[f + 2] == measurer.scale &&
                ints[i] == maxLines && ints[i + 1] == (if (uppercase) 1 else 0) && ints[i + 2] == (if (tabular) 1 else 0) &&
                texts[index] == text
            ) return results[index]
        }
        return null
    }

    fun put(
        measurer: CanvasTextMeasurer,
        text: String,
        role: KkTextRole,
        size: Float,
        maxWidth: Float,
        maxLines: Int,
        uppercase: Boolean,
        tabular: Boolean,
        result: TextLayoutResult,
    ) {
        val index = next
        next = (next + 1) % CAPACITY
        texts[index] = text
        roles[index] = role
        delegates[index] = measurer.delegate
        floats[index * 3] = size
        floats[index * 3 + 1] = maxWidth
        floats[index * 3 + 2] = measurer.scale
        ints[index * 3] = maxLines
        ints[index * 3 + 1] = if (uppercase) 1 else 0
        ints[index * 3 + 2] = if (tabular) 1 else 0
        results[index] = result
    }
}
