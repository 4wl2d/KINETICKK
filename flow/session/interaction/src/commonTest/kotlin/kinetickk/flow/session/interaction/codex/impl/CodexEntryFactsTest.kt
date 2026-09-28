// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.ball.content.api.*
import kinetickk.ball.gameplay.api.*
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kotlin.test.*

/** Codex facts show the game's data: real offer gates, no slot positions, rules only behind (!). */
class CodexEntryFactsTest {
    private val catalog = codexTestCatalog()

    @Test
    fun matterUnlockedLevelsFollowTheGameRuleUpToLevelEighty() {
        // Gameplay: max(runLevel, min(80, 1 + lifetimeMatter / 40)).
        assertEquals(1, codexMatterOfferLevel(0L))
        assertEquals(1, codexMatterOfferLevel(39L))
        assertEquals(2, codexMatterOfferLevel(40L))
        assertEquals(62, codexMatterOfferLevel(2_440L))
        assertEquals(80, codexMatterOfferLevel(3_160L))
        assertEquals(80, codexMatterOfferLevel(Long.MAX_VALUE))
        assertEquals(1, codexMatterOfferLevel(-5L))
    }

    @Test
    fun anItemUnlockedByLifetimeMatterShowsNoRunLevelGate() {
        val item = catalog.items[7].copy(unlockLevel = 62)
        val model = CodexRenderModel(immutableSetOf(item.id), CodexRunStacks(), immutableListOf(item))
        for (language in AppLanguage.entries) {
            fun offeredFrom(lifetimeMatter: Long) = codexItemEntry(item, model, lifetimeMatter, language)
                .facts.single { it.label == language.text(SessionText.OFFERS_FROM_LEVEL) }
            // 3,200 lifetime Matter unlocks catalog level 80: the item is offered from the run's start.
            assertEquals(language.text(SessionText.LEVEL_SHORT, 1), offeredFrom(3_200L).value)
            assertEquals(language.text(SessionText.LEVEL_SHORT, 1), offeredFrom(2_440L).value)
            // One Matter short of level 62, only the run level opens it.
            assertEquals(language.text(SessionText.LEVEL_SHORT, 62), offeredFrom(2_439L).value)
            assertEquals(language.text(SessionText.LEVEL_SHORT, 62), offeredFrom(0L).value)
            // The Codex path reads the profile's lifetime Matter.
            val progress = codexTestProgress(setOf(item.id)).copy(economy = PlayerEconomy(matter = 10L, lifetimeMatter = 3_200L))
            val entry = codexCatalogEntries(0, "", CodexItemFilter.ALL, model, catalog, progress, language).single()
            assertEquals(language.text(SessionText.LEVEL_SHORT, 1), entry.facts.single { !it.isStatus }.value)
            // The (!) explains the Matter rule the game applies: 40 Matter per level, up to level 80.
            val info = requireNotNull(entry.facts.single { !it.isStatus }.info)
            assertTrue("40" in info && language.text(SessionText.LEVEL_SHORT, 80) in info, info)
        }
    }

    @Test
    fun buildTabEmptyRelicSlotsCarryNoPositionNumber() {
        val build = GameplayBuildSummaryProjection(
            GameplayInstanceId(RunId(1)), GameplayRevision.ZERO, immutableListOf(),
            character = CoreShape.ORB, weapon = WeaponId.FLUX_WAKE, weaponLevel = 3,
            relics = immutableListOf(EquippedRelic(RelicId.KINETIC_FLYWHEEL, 1)),
        )
        val model = CodexRenderModel(immutableSetOf(), CodexRunStacks(build = build), catalog.items)
        for (language in AppLanguage.entries) {
            val empty = codexBuildEntries(model, catalog, codexTestProgress(), language).filter { it.icon == CodexIcon.Empty }
            assertEquals(catalog.relicPolicy.maxSlots - 1, empty.size)
            empty.forEach { entry ->
                assertEquals(language.text(SessionText.EMPTY_RELIC_SLOT), entry.title)
                assertFalse(entry.title.any(Char::isDigit), "${entry.key} title ${entry.title}")
                assertFalse(entry.summary.any(Char::isDigit), "${entry.key} announces ${entry.summary}")
            }
        }
    }

    @Test
    fun aspectSynergiesShowTheirRequirementAsAValueWithTheRuleBehindInfo() {
        val model = CodexRenderModel(immutableSetOf(), CodexRunStacks(), catalog.items, discoveredRelicIds = RelicId.entries.toImmutableSet())
        val clause = mapOf(AppLanguage.English to "Extra ranks", AppLanguage.Russian to "Дополнительные ранги")
        for (language in AppLanguage.entries) {
            val aspects = catalog.synergies.filter { it.requiredAspect != null }
            assertTrue(aspects.isNotEmpty())
            aspects.forEach { synergy ->
                val entry = codexSynergyEntry(synergy, model, catalog, language)
                val name = requireNotNull(synergy.requiredAspect).displayLabel.localizedContent(language)
                // Body text is the synergy's own description, never the rule.
                assertEquals(synergy.description.localizedContent(language), entry.description)
                entry.description.split("\n\n").forEach { paragraph ->
                    assertFalse(clause.getValue(language) in paragraph, "${entry.key} body: $paragraph")
                }
                val requires = entry.facts.single { it.label == language.text(SessionText.REQUIRES) }
                assertEquals(language.text(SessionText.SYNERGY_ASPECT_PAIR, name), requires.value)
                assertEquals(language.text(SessionText.SYNERGY_REQUIREMENT, name), requires.info)
                assertTrue(clause.getValue(language) in requires.info.orEmpty())
            }
        }
    }

    @Test
    fun relicPairSynergiesListTheirRelicsAndMissingPartsAsValues() {
        val synergy = catalog.synergies.first { it.requiredAspect == null }
        val (first, second) = synergy.requiredRelics
        val build = GameplayBuildSummaryProjection(
            GameplayInstanceId(RunId(1)), GameplayRevision.ZERO, immutableListOf(),
            relics = immutableListOf(EquippedRelic(first, 1)),
            synergies = immutableListOf(BuildSynergySummary(synergy.id.name, synergy.name, synergy.description, false,
                immutableListOf(catalog.relic(second).name))),
        )
        val model = CodexRenderModel(immutableSetOf(), CodexRunStacks(build = build), catalog.items,
            discoveredRelicIds = synergy.requiredRelics.toImmutableSet())
        val entry = codexSynergyEntry(synergy, model, catalog)
        assertEquals(synergy.description, entry.description)
        assertEquals("${catalog.relic(first).name} + ${catalog.relic(second).name}", entry.facts.single { it.label == "Requires" }.value)
        assertEquals(catalog.relic(second).name, entry.facts.single { it.label == "Missing" }.value)
        assertEquals("Inactive", entry.status)
        assertFalse(entry.facts.any { it.label.any(Char::isDigit) || ':' in it.label })
    }
}
