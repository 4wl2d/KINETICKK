// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ConversionKind
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.RelicPairingDefinition
import kinetickk.ball.content.api.RelicPairingKind
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.localization.CodexText
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.Kk
import kotlin.math.roundToInt

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
