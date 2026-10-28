// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.content.api.WorldDiscoveryKind
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.canvas.SiteGlyph
import kinetickk.ball.gameplay.interaction.canvas.worldDiscoveryGlyph
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.Kk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CodexWorldEntriesTest {
    private val catalog = codexTestCatalog()

    private fun model(found: Set<WorldDiscovery> = emptySet()) =
        CodexRenderModel(kinetickk.foundation.collections.immutableSetOf(), CodexRunStacks(), catalog.items, discoveredWorldEntries = found.toImmutableSet())

    private fun world(found: Set<WorldDiscovery> = emptySet(), search: String = "", language: AppLanguage = AppLanguage.English) =
        codexCatalogEntries(CODEX_WORLD_CATEGORY, search, CodexItemFilter.ALL, model(found), catalog, codexTestProgress(), language)

    private val listed = WorldDiscovery.entries.filter { it.kind != WorldDiscoveryKind.SECRET_BOSS }

    @Test
    fun theWorldListsEveryPlaceAndSecretOnceBySiteDimensionStructureThenSecretAndNoSecretBoss() {
        val entries = world()
        assertEquals(listed.map { "world/$it" }, entries.map { it.key })
        val kinds = listOf(WorldDiscoveryKind.SITE, WorldDiscoveryKind.DIMENSION, WorldDiscoveryKind.STRUCTURE, WorldDiscoveryKind.SECRET)
        assertEquals(kinds.flatMap { kind -> WorldDiscovery.entries.filter { it.kind == kind } }.map { "world/$it" }, entries.map { it.key })
        assertTrue(entries.none { it.key == "world/CARTOGRAPHER" || it.key == "world/ANTECEDENT" }, "secret bosses stay in Enemies")
        assertEquals(listOf("Site", "Site", "Site", "Site", "Dimension", "Dimension", "Dimension"), entries.take(7).map { it.kind })
        assertEquals("Structure", entries.single { it.key == "world/SPIRE" }.kind)
        assertEquals("Secret", entries.single { it.key == "world/QUIET_SIGNAL" }.kind)
    }

    @Test
    fun anUnfoundEntryIsQuestionMarksOverItsHintAndAFoundOneNamesAndDescribesIt() {
        val hidden = world().single { it.key == "world/GATE_RACE" }
        assertEquals("???", hidden.title)
        assertEquals("Some anomalies want speed.", hidden.description)
        assertEquals("Not found", hidden.status)
        assertEquals("Not found", hidden.quantity)
        assertFalse(hidden.discovered)
        assertEquals(CodexIcon.World(WorldDiscovery.GATE_RACE, discovered = false), hidden.icon)
        assertEquals(Kk.Mute, hidden.color)
        assertEquals("Not found yet. The hint points the way.", hidden.help)
        assertFalse("Gate race" in hidden.summary || "loop" in hidden.summary, hidden.summary)

        val found = world(setOf(WorldDiscovery.GATE_RACE)).single { it.key == "world/GATE_RACE" }
        assertEquals("Gate race", found.title)
        assertEquals("Gates laid in a loop, timed against a par.", found.description)
        assertEquals("Found", found.status)
        assertTrue(found.discovered)
        assertEquals(CodexIcon.World(WorldDiscovery.GATE_RACE, discovered = true), found.icon)
        assertEquals(Kk.Bone, found.color)
        assertEquals("Places and secrets found across your runs.", found.help)

        // Every other entry keeps its own state: one discovery reveals nothing else.
        world(setOf(WorldDiscovery.GATE_RACE)).filter { it.key != "world/GATE_RACE" }.forEach { entry ->
            assertEquals("???", entry.title, entry.key)
            assertFalse(entry.discovered, entry.key)
        }
    }

    @Test
    fun everyEntryReadsInRussianWithoutLatinLettersFoundOrNot() {
        val hidden = world(language = AppLanguage.Russian)
        val found = world(WorldDiscovery.entries.toSet(), language = AppLanguage.Russian)
        assertEquals("Гонка по вратам", found.single { it.key == "world/GATE_RACE" }.title)
        assertEquals("Тихий сигнал", found.single { it.key == "world/QUIET_SIGNAL" }.title)
        assertEquals("Некоторым аномалиям нужна скорость.", hidden.single { it.key == "world/GATE_RACE" }.description)
        assertEquals("Не найдено", hidden.first().status)
        assertEquals("Найдено", found.first().status)
        assertEquals(listOf("Локация", "Измерение", "Строение", "Тайна"), found.map { it.kind }.distinct())
        (hidden + found).forEach { entry ->
            val texts = listOfNotNull(entry.description, entry.kind, entry.status, entry.help) +
                // Structure template names are the structures lane's phrase keys and are localized by its own list.
                listOfNotNull(entry.title.takeUnless { entry.discovered && (entry.icon as CodexIcon.World).id.kind == WorldDiscoveryKind.STRUCTURE })
            texts.forEach { text -> assertFalse(text.any { it in 'a'..'z' || it in 'A'..'Z' }, "Latin letters in ${entry.key}: $text") }
        }
        found.forEach { entry ->
            val definition = catalog.worldDiscoveries.single { "world/${it.id}" == entry.key }
            assertEquals(definition.name.localizedContent(AppLanguage.Russian), entry.title)
        }
    }

    @Test
    fun aSearchFindsOnlyFoundEntriesByTheirNames() {
        assertTrue(world(search = "race").isEmpty(), "an unfound name is never searchable")
        assertTrue(world(search = "???").isEmpty(), "nor are the question marks")
        assertTrue(world(search = "speed").isEmpty(), "nor a hint")
        val found = setOf(WorldDiscovery.GATE_RACE, WorldDiscovery.LOST_CURSOR)
        assertEquals(listOf("world/GATE_RACE"), world(found, "rAcE").map { it.key })
        assertEquals(listOf("world/LOST_CURSOR"), world(found, "cursor").map { it.key })
        assertEquals(listOf("world/GATE_RACE"), world(found, "гонка", AppLanguage.Russian).map { it.key })
        assertTrue(world(found, "race", AppLanguage.Russian).isEmpty(), "a search reads the shown language")
        assertEquals(listed.size, world(found, "   ").size, "a blank search lists everything")
    }

    @Test
    fun theCategoryCountsFoundPlacesAndSecretsOutOfEveryListedOne() {
        assertEquals(0 to 18, codexWorldCounts(model(), catalog))
        assertEquals(2 to 18, codexWorldCounts(model(setOf(WorldDiscovery.ZENITH, WorldDiscovery.SPIN_CYCLE)), catalog))
        // A defeated secret boss is counted by the Enemies category, not here.
        assertEquals(1 to 18, codexWorldCounts(model(setOf(WorldDiscovery.VAULT, WorldDiscovery.CARTOGRAPHER)), catalog))
        assertEquals(18 to 18, codexWorldCounts(model(WorldDiscovery.entries.toSet()), catalog))
        assertEquals(listed.size, 18)
    }

    @Test
    fun theProfilesDiscoveriesReachTheRenderModelAndTheCategoriesEndAtTheWorld() {
        val found = setOf(WorldDiscovery.DRIFT_CACHE, WorldDiscovery.ARCHITECTS_DOODLE)
        val rendered = CodexReducer(catalog.items).renderModel(
            CollectionProjection(LOCAL_PROFILE_INSTANCE_ID, ProfileRevision.ZERO, PlayerCollection(emptySet(), discoveredWorldEntries = found)),
            CodexRunStacks(),
        )
        assertEquals(found, rendered.discoveredWorldEntries)
        assertTrue(rendered.isWorldDiscovered(WorldDiscovery.DRIFT_CACHE))
        assertFalse(rendered.isWorldDiscovered(WorldDiscovery.GATE_RACE))
        assertEquals(found.map { "world/$it" }.toSet(), world(found).filter { it.discovered }.map { it.key }.toSet())
        assertEquals(5, CODEX_WORLD_CATEGORY)
        assertFailsWith<IllegalStateException> {
            codexCatalogEntries(CODEX_WORLD_CATEGORY + 1, "", CodexItemFilter.ALL, model(), catalog, codexTestProgress())
        }
    }

    @Test
    fun eachPlaceWearsItsSitesGlyphAndEachSecretItsOwn() {
        assertEquals(SiteGlyph.RACE, worldDiscoveryGlyph(WorldDiscovery.GATE_RACE))
        assertEquals(SiteGlyph.CHALLENGE, worldDiscoveryGlyph(WorldDiscovery.TRIALS))
        assertEquals(SiteGlyph.BOUNTY, worldDiscoveryGlyph(WorldDiscovery.BOUNTY))
        assertEquals(SiteGlyph.CACHE, worldDiscoveryGlyph(WorldDiscovery.DRIFT_CACHE))
        WorldDiscovery.entries.filter { it.kind == WorldDiscoveryKind.DIMENSION }.forEach { assertEquals(SiteGlyph.DIMENSION, worldDiscoveryGlyph(it)) }
        WorldDiscovery.entries.filter { it.kind == WorldDiscoveryKind.STRUCTURE }.forEach { assertEquals(SiteGlyph.STRUCTURE, worldDiscoveryGlyph(it)) }
        val secrets = WorldDiscovery.entries.filter { it.kind == WorldDiscoveryKind.SECRET }.map(::worldDiscoveryGlyph)
        assertEquals(secrets.distinct(), secrets, "every secret has its own mark")
        assertEquals(listOf(SiteGlyph.LOST_CURSOR, SiteGlyph.GOLDEN, SiteGlyph.SPIN, SiteGlyph.DOODLE, SiteGlyph.QUIET), secrets)
    }
}
