// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.rank
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CodexEnemyEntriesTest {
    private val catalog = codexTestCatalog()
    private val model = CodexRenderModel(kinetickk.foundation.collections.immutableSetOf(), CodexRunStacks(), catalog.items)

    @Test
    fun theEnemiesCategoryListsTheWholeRosterByNameWithThreatAndGates() {
        val entries = codexCatalogEntries(CODEX_ENEMIES_CATEGORY, "", CodexItemFilter.ALL, model, catalog, codexTestProgress())
        assertEquals(EnemyType.entries.map { "enemy/$it" }, entries.map { it.key })
        val sniper = entries.single { it.key == "enemy/SNIPER" }
        assertEquals("Sniper", sniper.title)
        assertEquals("3/5", sniper.quantity)
        assertTrue(sniper.facts.any { it.label == "Appears" && it.value == "From 5:30" }, "${sniper.facts}")
        assertTrue(sniper.discovered)
        val architect = entries.single { it.key == "enemy/ARCHITECT" }
        assertEquals("Boss", architect.kind)
        assertTrue(architect.facts.any { it.value == "From 12:00" })
        assertEquals("Mini-boss", entries.single { it.key == "enemy/HERALD" }.kind)
    }

    @Test
    fun rebirthGatedEnemiesKeepTheirNameAndTakeTheLockedLookBelowTheirRank() {
        val fresh = codexCatalogEntries(CODEX_ENEMIES_CATEGORY, "", CodexItemFilter.ALL, model, catalog, codexTestProgress())
        val broodmother = fresh.single { it.key == "enemy/BROODMOTHER" }
        assertEquals("Broodmother", broodmother.title, "Always named")
        assertFalse(broodmother.discovered)
        assertTrue((broodmother.icon as CodexIcon.Enemy).locked)
        assertTrue(broodmother.facts.any { it.value == "Reborn 3" }, "${broodmother.facts}")

        val reborn = codexTestProgress().copy(rebirthProgress = RebirthProgress(level = 3, highestCleared = 2))
        val ranked = codexCatalogEntries(CODEX_ENEMIES_CATEGORY, "", CodexItemFilter.ALL, model, catalog, reborn)
        assertFalse((ranked.single { it.key == "enemy/BROODMOTHER" }.icon as CodexIcon.Enemy).locked)
        assertTrue((ranked.single { it.key == "enemy/REFLECTOR" }.icon as CodexIcon.Enemy).locked, "Reborn 4 still locked at rank 3")
    }

    @Test
    fun enemyEntriesReadInRussianAndFilterBySearch() {
        val russian = codexCatalogEntries(CODEX_ENEMIES_CATEGORY, "", CodexItemFilter.ALL, model, catalog, codexTestProgress(), AppLanguage.Russian)
        val lancer = russian.single { it.key == "enemy/LANCER" }
        assertEquals("Копейщик", lancer.title)
        assertTrue(lancer.facts.any { it.value == "С 4:00" }, "${lancer.facts}")
        russian.forEach { entry ->
            listOf(entry.title, entry.description, entry.kind).forEach { text ->
                assertFalse(text.any { it in 'a'..'z' || it in 'A'..'Z' }, "Latin letters in ${entry.key}: $text")
            }
        }
        val found = codexCatalogEntries(CODEX_ENEMIES_CATEGORY, "swarm", CodexItemFilter.ALL, model, catalog, codexTestProgress())
        assertEquals(listOf("enemy/SWARMLING"), found.map { it.key })
        assertEquals("0:38", codexClock(38f))
    }

    @Test
    fun secretBossesStayUnknownWithoutNameDescriptionOrShapeUntilTheirDiscoveryIsRecorded() {
        listOf(AppLanguage.English, AppLanguage.Russian).forEach { language ->
            val entries = codexCatalogEntries(CODEX_ENEMIES_CATEGORY, "", CodexItemFilter.ALL, model, catalog, codexTestProgress(), language)
            listOf(EnemyType.CARTOGRAPHER, EnemyType.ANTECEDENT).forEach { type ->
                val hidden = entries.single { it.key == "enemy/$type" }
                assertEquals("???", hidden.title, "$type in $language")
                assertEquals("", hidden.description)
                assertEquals(if (language == AppLanguage.English) "Secret boss" else "Тайный босс", hidden.kind)
                assertEquals("?/5", hidden.quantity)
                assertEquals(listOf(if (language == AppLanguage.English) "Undiscovered" else "Не обнаружен"), hidden.facts.map { it.value },
                    "only the status: no threat, habitat or arrival time")
                assertEquals(CodexIcon.Enemy(type, locked = true, hidden = true), hidden.icon, "the icon keeps its shape secret")
                assertFalse(hidden.discovered)
                assertEquals(if (language == AppLanguage.English) "Defeat it to reveal its record." else "Победите его, чтобы открыть запись.",
                    hidden.help)
            }
            // No other enemy is hidden, and every ordinary, Elite, mini-boss and the Architect keeps its name.
            assertEquals(listOf("enemy/CARTOGRAPHER", "enemy/ANTECEDENT"), entries.filter { (it.icon as CodexIcon.Enemy).hidden }.map { it.key })
        }
    }

    @Test
    fun aRecordedDiscoveryNamesItsSecretBossAsASecretBossWhereItIsFound() {
        val found = CodexRenderModel(kinetickk.foundation.collections.immutableSetOf(), CodexRunStacks(), catalog.items,
            discoveredWorldEntries = kinetickk.foundation.collections.immutableSetOf(WorldDiscovery.CARTOGRAPHER))
        val entries = codexCatalogEntries(CODEX_ENEMIES_CATEGORY, "", CodexItemFilter.ALL, found, catalog, codexTestProgress())
        val cartographer = entries.single { it.key == "enemy/CARTOGRAPHER" }
        assertEquals("Cartographer", cartographer.title)
        assertTrue(cartographer.description.startsWith("A secret boss"), cartographer.description)
        assertEquals("Secret boss", cartographer.kind)
        assertEquals("5/5", cartographer.quantity)
        assertEquals(CodexIcon.Enemy(EnemyType.CARTOGRAPHER, locked = false), cartographer.icon)
        assertTrue(cartographer.discovered)
        assertTrue(cartographer.facts.any { it.label == "Found in" && it.value == "Open space" }, "${cartographer.facts}")
        // The Antecedent's discovery is its own: it stays hidden.
        assertTrue((entries.single { it.key == "enemy/ANTECEDENT" }.icon as CodexIcon.Enemy).hidden)
        val antecedent = codexCatalogEntries(CODEX_ENEMIES_CATEGORY, "", CodexItemFilter.ALL,
            found.copy(discoveredWorldEntries = kinetickk.foundation.collections.immutableSetOf(WorldDiscovery.ANTECEDENT)), catalog, codexTestProgress(),
            AppLanguage.Russian).single { it.key == "enemy/ANTECEDENT" }
        assertEquals("Предтеча", antecedent.title)
        assertEquals("Тайный босс", antecedent.kind)
        assertTrue(antecedent.facts.any { it.label == "Где встречается" && it.value == "Архив" }, "${antecedent.facts}")
        assertFalse(antecedent.facts.any { it.label == "Появляется" }, "a kind outside open space states no arrival time")
    }

    @Test
    fun theHabitatFactNamesWhereEachKindIsFoundAndOnlyOpenSpaceKindsStateTheirArrival() {
        val english = codexCatalogEntries(CODEX_ENEMIES_CATEGORY, "", CodexItemFilter.ALL, model, catalog, codexTestProgress())
        fun facts(type: EnemyType, entries: List<CodexEntry> = english) = entries.single { it.key == "enemy/$type" }.facts.associate { it.label to it.value }
        assertEquals("Everywhere", facts(EnemyType.ELITE)["Found in"])
        assertEquals("Open space, Structures, Archive", facts(EnemyType.DRIFTER)["Found in"])
        assertEquals("Open space", facts(EnemyType.ARCHITECT)["Found in"])
        assertEquals("Structures", facts(EnemyType.SENTRY)["Found in"])
        assertEquals("Zenith", facts(EnemyType.SERAPH)["Found in"])
        assertEquals("Nadir", facts(EnemyType.PYRE_HERALD)["Found in"])
        assertEquals("Open space, Structures", facts(EnemyType.WARDEN_PRIME)["Found in"])
        assertEquals("From 0:00", facts(EnemyType.DRIFTER)["Appears"])
        assertEquals("From 2:00", facts(EnemyType.COMET)["Appears"])
        listOf(EnemyType.SENTRY, EnemyType.CRAWLER, EnemyType.SERAPH, EnemyType.CHOIR, EnemyType.IMP, EnemyType.HOUND, EnemyType.CIPHER,
            EnemyType.MIRROR, EnemyType.THRONE, EnemyType.PYRE_HERALD).forEach { type ->
            assertFalse("Appears" in facts(type), "$type lives outside open space")
        }
        // The facts read in order: status, threat, where it is found, then when it arrives and its Rebirth gate.
        assertEquals(listOf("Status", "Threat", "Found in", "Appears", "Unlocks at"),
            english.single { it.key == "enemy/BROODMOTHER" }.facts.map { it.label })
        val russian = codexCatalogEntries(CODEX_ENEMIES_CATEGORY, "", CodexItemFilter.ALL, model, catalog, codexTestProgress(), AppLanguage.Russian)
        assertEquals("Повсюду", facts(EnemyType.ELITE, russian)["Где встречается"])
        assertEquals("Открытый космос, Сооружения", facts(EnemyType.WARDEN_PRIME, russian)["Где встречается"])
        assertEquals("Надир", facts(EnemyType.IMP, russian)["Где встречается"])
        russian.flatMap { it.facts }.forEach { fact ->
            listOf(fact.label, fact.value).forEach { text -> assertFalse(text.any { it in 'a'..'z' || it in 'A'..'Z' }, "Latin letters in $text") }
        }
    }

    @Test
    fun hiddenSecretBossesNeverMatchASearchAndLeaveTheCount() {
        val progress = codexTestProgress()
        assertTrue(codexCatalogEntries(CODEX_ENEMIES_CATEGORY, "?", CodexItemFilter.ALL, model, catalog, progress).isEmpty(), "\"???\" is not a name")
        assertTrue(codexCatalogEntries(CODEX_ENEMIES_CATEGORY, "cartographer", CodexItemFilter.ALL, model, catalog, progress).isEmpty())
        assertTrue(codexCatalogEntries(CODEX_ENEMIES_CATEGORY, "secret", CodexItemFilter.ALL, model, catalog, progress).isEmpty())
        val found = model.copy(discoveredWorldEntries = kinetickk.foundation.collections.immutableSetOf(WorldDiscovery.CARTOGRAPHER))
        assertEquals(listOf("enemy/CARTOGRAPHER"),
            codexCatalogEntries(CODEX_ENEMIES_CATEGORY, "cartog", CodexItemFilter.ALL, found, catalog, progress).map { it.key })
        // Counted: kinds the rank lets into runs that the Codex may name, out of the whole roster.
        val open = catalog.enemies.count { it.minimumRebirth <= progress.rebirthProgress.rank }
        assertEquals((open - 2) to catalog.enemies.size, codexEnemyCounts(model, catalog, progress))
        assertEquals((open - 1) to catalog.enemies.size, codexEnemyCounts(found, catalog, progress))
        val both = model.copy(discoveredWorldEntries = kinetickk.foundation.collections.immutableSetOf(WorldDiscovery.CARTOGRAPHER, WorldDiscovery.ANTECEDENT))
        assertEquals(open to catalog.enemies.size, codexEnemyCounts(both, catalog, progress))
    }
}
