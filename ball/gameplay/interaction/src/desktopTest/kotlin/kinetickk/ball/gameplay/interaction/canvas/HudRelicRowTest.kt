// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What the HUD's relic row draws for synergies: link bars between neighbours, stacked brackets otherwise. */
class HudRelicRowTest {
    private val sizes = listOf(1_440 to 810, 844 to 390, 390 to 844)

    @Test
    fun interleavedSynergiesDrawSeparateBracketsThatNeverMeet() {
        sizes.forEach { (w, h) ->
            // Vector (0, 2) and Gravitic (1, 3) interleave; the named Brake compression pair spans (0, 3) over both.
            listOf(
                listOf(RelicId.KINETIC_FLYWHEEL, RelicId.ORBITAL_NAIL, RelicId.GHOST_VECTOR, RelicId.MASS_ECHO) to 2,
                listOf(RelicId.BRAKEPOINT_MEMORY, RelicId.ORBITAL_NAIL, RelicId.KINETIC_FLYWHEEL, RelicId.MASS_ECHO) to 3,
            ).forEach { (relics, expected) ->
                val marks = drawRelics(w, h, relics)
                val brackets = marks.filter { it.bracket }
                val where = "$w x $h $relics"
                assertEquals(expected, brackets.size, "$where: $brackets")
                brackets.forEachIndexed { index, a ->
                    brackets.drop(index + 1).forEach { b ->
                        if (a.from <= b.to && b.from <= a.to) {
                            assertFalse(a.rect.overlaps(b.rect), "$where: brackets ${a.from}-${a.to} ${a.rect} and ${b.from}-${b.to} ${b.rect} meet")
                        }
                    }
                }
                // Brackets stay above the diamonds' row and on screen.
                brackets.forEach { assertTrue(it.rect.top >= 0f && it.rect.right <= w, "$where: ${it.rect}") }
            }
        }
    }

    @Test
    fun linkedRelicsTwoSlotsApartGetABracketAndNoLinkBar() {
        sizes.forEach { (w, h) ->
            // Two Vector relics in slots 0 and 2 with a Gravitic relic between them.
            val marks = drawRelics(w, h, listOf(RelicId.KINETIC_FLYWHEEL, RelicId.ORBITAL_NAIL, RelicId.GHOST_VECTOR))
            assertEquals(listOf(Triple(true, 0, 2)), marks.map { Triple(it.bracket, it.from, it.to) }, "$w x $h")
            // Neighbours of one aspect are joined by a link bar instead.
            val neighbours = drawRelics(w, h, listOf(RelicId.KINETIC_FLYWHEEL, RelicId.GHOST_VECTOR))
            assertEquals(listOf(Triple(false, 0, 1)), neighbours.map { Triple(it.bracket, it.from, it.to) }, "$w x $h")
            // The bracket spans the two diamonds' centers, above them.
            val bracket = marks.single().rect
            val bar = neighbours.single().rect
            assertTrue(bracket.bottom <= bar.top + 1f, "$w x $h: bracket $bracket is not above the row $bar")
        }
    }

    private fun drawRelics(width: Int, height: Int, relics: List<RelicId>): List<HudLinkMark> {
        val base = hudTestModel(width.toFloat(), height.toFloat())
        // The game's aspects: six relics per aspect in RelicId order.
        val content = base.content.copy(relics = RelicId.entries.map { id ->
            RelicDefinition(id, id.name, RelicAspect.entries[(id.ordinal / 6).coerceAtMost(RelicAspect.entries.size - 1)], "Fixture", "Fixture")
        }.toImmutableList())
        val model: GameplayRenderModel = base.with(
            "content" to content,
            "equippedRelics" to relics.map { EquippedRelic(it, 1) }.toImmutableList(),
        )
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1.25f,
            AppLanguage.English, HudTestFonts.typography)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(width, height)), Size(width.toFloat(), height.toFloat())) {
            drawHud(model, measurer, 1f)
        }
        return HudLayoutProbe.links()
    }
}
