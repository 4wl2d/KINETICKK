// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.ball.content.api.*
import kinetickk.ball.gameplay.api.*
import kinetickk.ball.profile.api.*
import kinetickk.flow.session.interaction.codex.api.*
import kinetickk.flow.session.interaction.testItems
import kinetickk.foundation.collections.*
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.flow.session.interaction.localization.SessionText
import kotlin.test.*

class CodexReducerTest {
    @Test fun collectionDoesNotExposeUnfoundItemsThroughSearchOrDetails() {
        val catalog = codexTestCatalog()
        val empty = CodexRenderModel(immutableSetOf(), CodexRunStacks(), catalog.items)
        assertTrue(codexCatalogEntries(0, "", CodexItemFilter.ALL, empty, catalog, codexTestProgress()).isEmpty())
        assertTrue(codexFilteredItems(empty, catalog.items.first().name, CodexItemFilter.ALL).isEmpty())
        val hidden = codexItemEntry(catalog.items.first(), empty)
        assertIs<CodexIcon.Unknown>(hidden.icon)
        assertNotEquals(catalog.items.first().name, hidden.title)
        assertFalse(hidden.description.contains(catalog.items.first().description))
        val found = empty.copy(discoveredItemIds = immutableSetOf(0), newItemIds = immutableSetOf(0))
        assertEquals(listOf(0), codexFilteredItems(found, "", CodexItemFilter.ALL).map { it.id })
        assertTrue(codexItemEntry(catalog.items.first(), found).isNew)
        assertFalse(codexItemEntry(catalog.items.first(), found.copy(newItemIds = immutableSetOf())).isNew)
    }

    @Test fun lockedCharactersRevealTheirUnlockGoalButNotTheirIdentityOrPower() {
        val catalog = codexTestCatalog()
        val shape = catalog.coreShape(CoreShape.PRISM).copy(mechanicDescription = "Hidden power",
            unlockDescription = "Defeat three elites", unlockTarget = 3, unlockRequirement = CharacterUnlockRequirement.ELITE_KILLS)
        val progress = codexTestProgress().copy(characterAchievements = CharacterAchievementProgress(eliteKills = 2))
        val entry = codexShapeEntry(shape, model(), progress)
        assertEquals("Unknown core", entry.title)
        assertEquals("Defeat three elites", entry.description)
        assertEquals(CodexFact("Progress", "2/3"), entry.facts.single { it.label == "Progress" })
        assertEquals("Locked", entry.status)
        assertFalse(entry.discovered)
        assertTrue(codexCatalogEntries(3, shape.displayName, CodexItemFilter.ALL, model(), catalog, progress).isEmpty())
        val opened = codexShapeEntry(shape, model(), progress.copy(unlockedCoreShapes = immutableSetOf(CoreShape.ORB, CoreShape.PRISM)))
        assertEquals(shape.displayName, opened.title)
        assertTrue(opened.description.contains("Hidden power"))
    }

    @Test fun availabilityIsShownAsLabelledValuesWithLevelsInLvlForm() {
        val catalog = codexTestCatalog()
        val run = model()
        val noRun = run.copy(runStacks = CodexRunStacks())
        val known = run.copy(discoveredRelicIds = RelicId.entries.toImmutableSet())
        for (language in AppLanguage.entries) {
            val level = Regex(if (language == AppLanguage.English) "^Lvl \\d+$" else "^Ур\\. \\d+$")
            val entries = listOf(run, noRun).flatMap { m -> codexCatalogEntries(0, "", CodexItemFilter.ALL, m, catalog, codexTestProgress(), language) } +
                (1..3).flatMap { category -> codexCatalogEntries(category, "", CodexItemFilter.ALL, known, catalog, codexTestProgress(), language) }
            assertTrue(entries.isNotEmpty())
            entries.forEach { entry ->
                assertTrue(entry.facts.isNotEmpty(), "${entry.key} has no facts")
                entry.facts.forEach { fact ->
                    // Short label + short value; any sentence lives behind the fact's (!).
                    assertTrue(fact.label.length <= 16 && fact.value.length <= 16, "${entry.key}: $fact")
                    assertFalse(fact.label.any(Char::isDigit), "${entry.key}: digits belong in the value, $fact")
                    assertFalse('.' in fact.value.removePrefix("Ур."), "${entry.key}: sentence value $fact")
                    fact.info?.let { assertTrue(it.endsWith('.'), "${entry.key}: explanation expected behind (!)") }
                }
            }
            val item = catalog.items[2]
            val offered = codexItemEntry(item, run, language).facts.single { !it.isStatus }
            assertTrue(level.matches(offered.value), offered.value)
            assertEquals(language.text(SessionText.LEVEL_SHORT, item.unlockLevel), offered.value)
            assertTrue(offered.info != null)
        }
    }

    @Test fun relicsAndSynergyRecipesAppearOnlyAfterTheirComponentsAreFound() {
        val catalog = codexTestCatalog()
        val empty = CodexRenderModel(immutableSetOf(), CodexRunStacks(), catalog.items)
        assertTrue(codexCatalogEntries(2, "", CodexItemFilter.ALL, empty, catalog, codexTestProgress()).isEmpty())
        assertFalse(codexSynergyDiscovered(catalog.synergies.first(), empty, catalog))
        val found = empty.copy(discoveredRelicIds = immutableSetOf(RelicId.KINETIC_FLYWHEEL, RelicId.GHOST_VECTOR))
        assertEquals(2, codexCatalogEntries(2, "", CodexItemFilter.ALL, found, catalog, codexTestProgress()).size)
        assertTrue(codexSynergyDiscovered(catalog.synergies.first(), found, catalog))
    }

    @Test fun catalogAcceptsFourHundredAndRejectsFourHundredOne() {
        assertEquals(400, model().items.size)
        assertFailsWith<IllegalArgumentException> { CodexReducer(testItems(401)) }
    }

    @Test fun searchAccepts128AndClamps129() {
        assertEquals("s".repeat(128), codexSearchInput("s".repeat(128)))
        assertEquals("s".repeat(128), codexSearchInput("s".repeat(129)))
        assertEquals("s".repeat(128), codexSearchInput("s".repeat(400)))
    }

    @Test fun modelCombinesProfileDiscoveryWithShellRunStacks() {
        val model = model()
        assertTrue(model.isDiscovered(2))
        assertEquals(4, model.itemStack(2))
        assertEquals(0, model.itemStack(399))
    }

    @Test fun searchAndFiltersPreserveCatalogOrderAndUseProfileDiscovery() {
        val model = model()
        assertEquals(listOf(2), codexFilteredItems(model, "iTeM 2", CodexItemFilter.ALL).map { it.id })
        assertEquals(listOf(2, 399), codexFilteredItems(model, "", CodexItemFilter.DISCOVERED).map { it.id })
        assertEquals(listOf(2), codexFilteredItems(model, "", CodexItemFilter.IN_BUILD).map { it.id })
        assertTrue(codexFilteredItems(model.copy(runStacks = CodexRunStacks()), "", CodexItemFilter.IN_BUILD).isEmpty())
    }

    @Test fun everyCategoryUsesCaseInsensitiveNameSubstringWithoutReordering() {
        val catalog = codexTestCatalog()
        val known = model().copy(discoveredRelicIds = RelicId.entries.toImmutableSet())
        for (category in 0..3) {
            val entries = codexCatalogEntries(category, "", CodexItemFilter.ALL, known, catalog, codexTestProgress())
            val query = entries.last { it.discovered }.title.takeLast(3).lowercase()
            assertEquals(entries.filter { it.discovered && it.title.contains(query, ignoreCase = true) }, codexCatalogEntries(category, query, CodexItemFilter.ALL, known, catalog, codexTestProgress()))
        }
    }

    @Test fun hoverAndNewFocusReturnToPinnedEntryWhenPreviewEnds() {
        val pinned = CodexSelection().activate("item/2")
        assertEquals("item/2", pinned.previewKey)
        val hovered = pinned.copy(hoverKey = "item/3")
        assertEquals("item/3", hovered.previewKey)
        assertEquals("item/2", hovered.copy(hoverKey = null).previewKey)
        val focused = pinned.copy(focusKey = "item/4")
        assertEquals("item/4", focused.previewKey)
        assertEquals("item/2", focused.copy(focusKey = null).previewKey)
    }

    @Test fun changingResultsOnlyClearsSelectionWhenEntryDisappears() {
        val selection = CodexSelection().activate("item/2")
        assertEquals(selection, selection.retain(setOf("item/2", "item/3")))
        assertEquals(CodexSelection(), selection.retain(setOf("item/3")))
        assertEquals("item/2", selection.closeSheet().pinnedKey)
        assertFalse(selection.closeSheet().sheetOpen)
    }

    @Test fun sidePanelRequiresBothDimensionsAndIncludesExactBoundary() {
        assertTrue(codexUsesSidePanel(900f, 480f))
        assertFalse(codexUsesSidePanel(899f, 480f))
        assertFalse(codexUsesSidePanel(900f, 479f))
    }

    @Test fun noRunEmptyInventoryAndEmptySearchAreDistinct() {
        assertEquals(CodexEmptyState.NO_RUN, codexEmptyState(0, false, "", 0))
        assertEquals(CodexEmptyState.EMPTY_INVENTORY, codexEmptyState(0, true, "", 0))
        assertEquals(CodexEmptyState.EMPTY_SEARCH, codexEmptyState(1, false, "unknown", 0))
        assertEquals(CodexEmptyState.NONE, codexEmptyState(1, false, "", 400))
    }

    @Test fun aspectSynergiesShowTwoDistinctComponentsAndMissingPlaces() {
        val catalog = codexTestCatalog()
        val aspect = catalog.synergies.first()
        assertEquals(listOf(null, null), codexSynergyComponents(aspect, catalog, emptySet()))
        assertEquals(listOf(RelicId.KINETIC_FLYWHEEL, null), codexSynergyComponents(aspect, catalog, setOf(RelicId.KINETIC_FLYWHEEL)))
        assertEquals(listOf(RelicId.KINETIC_FLYWHEEL, RelicId.GHOST_VECTOR), codexSynergyComponents(aspect, catalog, setOf(RelicId.GHOST_VECTOR, RelicId.KINETIC_FLYWHEEL)))
        val specific = catalog.synergies.first { it.requiredAspect == null }
        assertEquals(specific.requiredRelics, codexSynergyComponents(specific, catalog, emptySet()))
    }

    private fun model(): CodexRenderModel {
        val stacks = List(400) { if (it == 2) 4 else 0 }.toImmutableList()
        return CodexReducer(testItems()).renderModel(
            CollectionProjection(LOCAL_PROFILE_INSTANCE_ID, ProfileRevision.ZERO, PlayerCollection(setOf(2, 399))),
            CodexRunStacks(stacks, GameplayBuildSummaryProjection(GameplayInstanceId(RunId(1)), GameplayRevision.ZERO, stacks)),
        )
    }
}
