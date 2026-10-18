// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.profile.api.RebirthProgress
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
}
