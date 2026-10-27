// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.SkillLineKind
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.content.api.rankLines
import kinetickk.ball.content.api.ruleLines
import kinetickk.ball.gameplay.api.GameplayBuildSummaryProjection
import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.RunId
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.flow.session.interaction.localization.CodexText
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Active items in the Codex: an active's sheet shows its active part (every rank's numbers, what rank III adds) from
 * its effect's content and its passive apart, its offers wait for the run level, the Active filter keeps them, and its
 * matrix cell keeps its ACTIVE notch clear of the glyph. Numbers and names are read from content, never pinned.
 */
class CodexActiveItemsTest {
    private val catalog = codexTestCatalog()
    private val solarFlareId = catalog.items.size
    private val frostLineId = catalog.items.size + 1

    /** Built like a shipped active item: name, flavor, tags and element from its effect, one passive rule, three ranks. */
    private fun active(id: Int, effect: SkillId, passive: ItemRule, unlockLevel: Int = 7): ItemDefinition {
        val skill = catalog.skill(effect)
        return ItemDefinition(id, skill.name, skill.summary, ItemRarity.RARE, skill.tags, listOfNotNull(skill.element).toImmutableList(),
            rules = immutableListOf(passive), maxStacks = ContentBounds.MAX_SKILL_RANK, unlockLevel = unlockLevel, active = effect)
    }

    private val items = (catalog.items + listOf(
        active(solarFlareId, SkillId.SOLAR_FLARE, ItemRule.ElementPotency(StatusElement.THERMAL, 0.2f)),
        active(frostLineId, SkillId.FROST_LINE, ItemRule.StatusDuration(StatusElement.CRYO, 1.2f), unlockLevel = 5),
    )).toImmutableList()
    private val withActives = catalog.copy(items = items)

    @Test
    fun anActiveItemsSheetListsEveryRanksNumbersWhatRankThreeAddsAndItsPassiveApart() {
        val solarFlare = items[solarFlareId]
        val skill = withActives.skill(SkillId.SOLAR_FLARE)
        for (language in AppLanguage.entries) for (held in 0..ContentBounds.MAX_SKILL_RANK) {
            val stacks = List(items.size) { if (it == solarFlareId) held else 0 }.toImmutableList()
            val run = CodexRunStacks(stacks, if (held > 0) GameplayBuildSummaryProjection(GameplayInstanceId(RunId(1)), GameplayRevision.ZERO, stacks) else null)
            val model = CodexRenderModel(items.map { it.id }.toImmutableSet(), run, items)
            val entry = codexItemEntry(solarFlare, model, 0L, language, withActives)
            val detail = entry.detail as CodexDetail.Item
            val part = assertNotNull(detail.active, "$language: an active item's sheet has its active part")
            assertEquals(SkillId.SOLAR_FLARE, part.effect)
            assertEquals(held, part.held)
            // Every rank: its label and its cooldown and values, as the effect's content writes them.
            assertEquals(ContentBounds.MAX_SKILL_RANK, part.ranks.size)
            part.ranks.forEachIndexed { index, rank ->
                val lines = skill.rankLines(index + 1, language)
                assertEquals(lines.first(), rank.label)
                assertEquals(lines.drop(1).joinToString(", "), rank.numbers)
                val cooldown = SkillLineKind.COOLDOWN.text(language, "").substringBefore(' ')
                assertTrue(rank.numbers.startsWith(cooldown), "$language rank ${index + 1} leads with its cooldown: ${rank.numbers}")
            }
            assertTrue(part.ranks.map { it.numbers }.distinct().size > 1, "the ranks differ")
            assertEquals(skill.rankThree.localizedContent(language), part.rankThree)
            // The passive part stays the item's rule lines; the sheet reads the effect's summary as its flavor.
            assertEquals(solarFlare.ruleLines(language, codexRuleContext(solarFlare, held, false, withActives)).map { it.text },
                detail.lines.map { it.text })
            assertTrue(detail.lines.isNotEmpty())
            assertTrue(entry.description.startsWith(skill.summary.localizedContent(language)))
            listOf(CodexText.ACTIVE_PART, CodexText.PASSIVE_PART).forEach { assertTrue(language.text(it) in entry.description, "$language $it") }
            assertTrue(part.rankThree in entry.description)
            // Read aloud as an active item.
            assertTrue(language.text(CodexText.ACTIVE_ITEM) in entry.summary, entry.summary)
        }
        // A passive item has no active part and is not read as one.
        val passive = codexItemEntry(items[0], CodexRenderModel(setOf(0).toImmutableSet(), CodexRunStacks(), items), 0L, AppLanguage.English, withActives)
        assertNull((passive.detail as CodexDetail.Item).active)
        assertTrue(AppLanguage.English.text(CodexText.ACTIVE_ITEM) !in passive.summary)
    }

    @Test
    fun anActiveItemIsOfferedFromItsOwnLevelWhateverMatterTheProfileCollected() {
        val frostLine = items[frostLineId]
        val model = CodexRenderModel(items.map { it.id }.toImmutableSet(), CodexRunStacks(), items)
        for (language in AppLanguage.entries) for (lifetimeMatter in listOf(0L, 2_440L, 3_200L)) {
            val fact = codexItemEntry(frostLine, model, lifetimeMatter, language, withActives).facts.single { !it.isStatus }
            assertEquals(language.text(SessionText.OFFERS_FROM_LEVEL), fact.label)
            assertEquals(language.text(SessionText.LEVEL_SHORT, frostLine.unlockLevel), fact.value, "$language $lifetimeMatter")
            assertEquals(language.text(CodexText.ACTIVE_OFFERS_HELP), fact.info)
        }
        // A passive item at the same level opens from the run's start once Matter has unlocked its level.
        val passive = items[3].copy(unlockLevel = frostLine.unlockLevel)
        assertEquals(1, codexOfferedFromLevel(passive, 3_200L))
        assertEquals(frostLine.unlockLevel, codexOfferedFromLevel(frostLine, 3_200L))
    }

    @Test
    fun theActiveFilterKeepsTheDiscoveredActiveItemsOnly() {
        val discovered = (listOf(0, 1, 2, solarFlareId)).toImmutableSet()
        val model = CodexRenderModel(discovered, CodexRunStacks(), items)
        assertEquals(listOf(solarFlareId), codexFilteredItems(model, "", CodexItemFilter.ACTIVE).map { it.id })
        assertEquals(listOf(0, 1, 2, solarFlareId), codexFilteredItems(model, "", CodexItemFilter.ALL).map { it.id })
        val everything = CodexRenderModel(items.map { it.id }.toImmutableSet(), CodexRunStacks(), items)
        assertEquals(listOf(solarFlareId, frostLineId), codexFilteredItems(everything, "", CodexItemFilter.ACTIVE).map { it.id })
        // The Items matrix under the filter shows the found actives only, no hatched gaps.
        val rows = codexItemRows(CodexItemSort.TAG, "", CodexItemFilter.ACTIVE, everything, withActives, codexTestProgress(emptySet()))
        assertEquals(listOf("item/$solarFlareId", "item/$frostLineId").sorted(), rows.entries.map { it.key }.sorted())
    }

    @Test
    fun theActiveNotchStaysInsideTheCellClearOfTheGlyphAndOfTheOtherMarks() {
        val eps = 0.001f
        // The active glyph's square box keeps its corners within the reach every mark avoids.
        assertTrue(CODEX_ACTIVE_GLYPH_BOX * 0.5f * sqrt(2f) <= CODEX_GLYPH_REACH)
        for (dp in listOf(22f, 24f, 30f, 36f, 40f, 56f)) for (density in listOf(1f, 1.5f, 2f)) {
            val cell = dp * density
            val marks = codexItemCellMarks(cell, density)
            val where = "$dp dp at $density"
            val box = marks.activeNotch
            val inset = marks.inset
            assertTrue(box.left >= inset - eps && box.top >= inset - eps && box.right <= cell - inset + eps, "$where: $box leaves the inset")
            // Its triangle's diagonal (top-left to bottom-right) passes the glyph's reach on the glyph's side.
            val center = marks.glyphCenter
            val diagonal = ((center.x - box.left) - (center.y - box.top)) / sqrt(2f)
            assertTrue(diagonal >= marks.glyphRadius * CODEX_GLYPH_REACH - eps, "$where: the ACTIVE notch reaches the glyph")
            // Apart from the other marks: 1 dp above the stack bar, below the NEW notch, left of the element strip.
            assertEquals(marks.bar.top - density, box.bottom, eps)
            assertTrue(box.top > marks.notch.bottom && box.right < marks.strip.left, where)
            assertTrue(box.width >= 5f * density - eps, where)
        }
    }
}
