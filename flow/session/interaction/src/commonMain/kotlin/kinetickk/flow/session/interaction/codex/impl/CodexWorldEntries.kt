// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.content.api.WorldDiscoveryDefinition
import kinetickk.ball.content.api.WorldDiscoveryKind
import kinetickk.ball.content.api.localizedContent
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.localization.CodexText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.Kk

/** The kinds the World category lists, in display order; secret bosses stay in the Enemies category. */
private val CodexWorldKinds = listOf(WorldDiscoveryKind.SITE, WorldDiscoveryKind.DIMENSION, WorldDiscoveryKind.STRUCTURE, WorldDiscoveryKind.SECRET)

internal fun codexWorldKey(id: WorldDiscovery): String = "world/$id"

/** The kind label of a world entry. */
internal val WorldDiscoveryKind.codexLabel: CodexText
    get() = when (this) {
        WorldDiscoveryKind.SITE -> CodexText.SITE_KIND
        WorldDiscoveryKind.DIMENSION -> CodexText.DIMENSION_KIND
        WorldDiscoveryKind.STRUCTURE -> CodexText.STRUCTURE_KIND
        WorldDiscoveryKind.SECRET -> CodexText.SECRET_KIND
        WorldDiscoveryKind.SECRET_BOSS -> CodexText.BOSS_KIND
    }

/**
 * One world place or secret. Once found it shows its name and description; before that it is "???" over its hint,
 * so the entry points the way without naming what waits there.
 */
internal fun codexWorldEntry(definition: WorldDiscoveryDefinition, discovered: Boolean, language: AppLanguage = AppLanguage.English): CodexEntry {
    val status = language.text(if (discovered) CodexText.FOUND else CodexText.NOT_FOUND)
    return CodexEntry(
        codexWorldKey(definition.id),
        if (discovered) definition.name.localizedContent(language) else language.text(CodexText.UNKNOWN_TITLE),
        (if (discovered) definition.description else definition.hint).localizedContent(language),
        language.text(definition.id.kind.codexLabel), status, listOf(codexStatus(language, status)),
        CodexIcon.World(definition.id, discovered), if (discovered) Kk.Bone else Kk.Mute, discovered = discovered,
        help = language.text(if (discovered) CodexText.WORLD_HELP else CodexText.WORLD_HIDDEN_HELP),
    )
}

/** The World category: sites, dimensions, structures, then secrets, each in catalog order; a search finds only found ones. */
internal fun codexWorldEntries(
    search: String,
    model: CodexRenderModel,
    catalog: UiCatalogSnapshot,
    language: AppLanguage = AppLanguage.English,
): List<CodexEntry> = CodexWorldKinds
    .flatMap { kind -> catalog.worldDiscoveries.filter { it.id.kind == kind } }
    .map { codexWorldEntry(it, model.isWorldDiscovered(it.id), language) }
    .filter { search.isBlank() || it.discovered && it.title.contains(search, ignoreCase = true) }

/** Found and listed World entries (the category's count); secret bosses are counted by the Enemies category. */
internal fun codexWorldCounts(model: CodexRenderModel, catalog: UiCatalogSnapshot): Pair<Int, Int> {
    val listed = catalog.worldDiscoveries.filter { it.id.kind in CodexWorldKinds }
    return listed.count { model.isWorldDiscovered(it.id) } to listed.size
}
