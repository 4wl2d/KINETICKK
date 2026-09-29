// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import androidx.compose.ui.geometry.Size
import kinetickk.foundation.design.KK_LIST_ROW_SELECTED_SHIFT_DP
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodexLayoutRulesTest {
    @Test
    fun navFocusRingSurroundsTheShiftedSlabEvenly() {
        val size = Size(250f, 46f)
        for (selection in listOf(0f, 0.5f, 1f)) {
            val ring = codexNavFocusRing(size, selection, 2f)
            val slabLeft = KK_LIST_ROW_SELECTED_SHIFT_DP * 2f * selection
            assertEquals(6f, slabLeft - ring.left, 0.001f)
            assertEquals(6f, ring.right - (size.width + slabLeft), 0.001f)
            assertEquals(-6f, ring.top, 0.001f)
            assertEquals(size.height + 6f, ring.bottom, 0.001f)
        }
    }

    @Test
    fun tabsShrinkTogetherToFitAndStopAtTheirMinimum() {
        assertEquals(1f, codexTabScale(listOf(100f, 120f), 400f))
        assertEquals(0.8f, codexTabScale(listOf(100f, 150f), 200f), 0.0001f)
        assertEquals(CODEX_TAB_MIN_SCALE, codexTabScale(listOf(300f, 300f), 100f))
    }

    @Test
    fun fittingSizeShrinksUntilTheLongestWordFitsWithoutSplittingIt() {
        // A fake font: every letter is half the size wide.
        val width = { word: String, size: Float -> (word.length * size * 0.5f).toInt() }
        val size = codexFittingSize("НЕАКТИВНО СИНЕРГИЯ", 90, 24f, 12f, width)
        assertTrue("НЕАКТИВНО".length * size * 0.5f <= 90f)
        assertTrue("НЕАКТИВНО".length * (size + 1f) * 0.5f > 90f, "the largest fitting size is used")
        assertEquals(24f, codexFittingSize("ИОН", 90, 24f, 12f, width))
        assertEquals(12f, codexFittingSize("ОЧЕНЬДЛИННОЕСЛОВОБЕЗПРОБЕЛОВ", 50, 24f, 12f, width), "never below the minimum")
    }

    @Test
    fun markBandHoldsTheRotatedStampWithItsShadowOrTheBadge() {
        val stamp = Size(44f, 15f)
        val band = codexMarkBand(stamp, 10f, 9f, 1f)
        // Inset, box, the lower end of the −6° stamp and its hard shadow (3 px at 17 px), plus a gap.
        assertTrue(band >= 3f + stamp.height + stamp.width * 0.5f * 0.1045f + 3f * 10f / 17f + 2f - 0.001f)
        // A badge taller than the stamp deepens the band.
        assertTrue(codexMarkBand(stamp, 10f, 20f, 1f) >= 3f + 20f * 1.35f + 2f - 0.001f)
    }

    @Test
    fun everyCellOfAGridSharesOneGlyphBelowTheMarkBandAsLargeAsACenteredCatalogGlyph() {
        val band = codexMarkBand(Size(44f, 15f), 10f, 9f, 1f)
        for (cell in listOf(CodexCell.value, 90f, 115f)) {
            val glyph = codexCellGlyph(cell, band, 1f)
            val reach = glyph.radius * CODEX_GLYPH_REACH
            assertTrue(glyph.centerY - reach >= band - 0.001f, "the glyph with its halo starts below the band in $cell")
            assertTrue(glyph.centerY + reach <= cell - 3f + 0.001f, "the glyph stays inside the cell $cell")
            assertEquals(cell * 0.5f, glyph.centerX, "the glyph is centered across $cell")
            // Never smaller than the centered glyph of a 64 dp cell (0.36 of it), never larger than 0.36 of its own cell.
            assertTrue(glyph.radius >= 64f * 0.36f && glyph.radius <= cell * 0.36f + 0.001f, "radius ${glyph.radius} in $cell")
        }
        // The layout depends on the cell and band only: a stamp or badge on one cell cannot move its glyph.
        assertEquals(codexCellGlyph(CodexCell.value, band, 1f), codexCellGlyph(CodexCell.value, band, 1f))
    }

    @Test
    fun labelValueRowsStackTogetherWhenAWordWouldNotFitBesideItsValue() {
        // A fake font: every letter is half the size wide; values are 10 px per character.
        val word = { text: String, size: Float -> text.length * size * 0.5f }
        val value = { text: String -> text.length * 10f }
        val rows = listOf("Impact damage" to "+5%", "Critical damage" to "+3%")
        assertEquals(CodexPairFit(false, 16f), codexPairFit(rows, 200f, 12f, 16f, 10f, word, value))
        // "Критический" (11 letters, 88 px at 16) no longer fits beside "+3,91%" (60 px) in 150 px:
        // both rows stack at the same label size.
        val russian = listOf("Урон от столкновений" to "+5,56%", "Критический урон" to "+3,91%")
        assertEquals(CodexPairFit(true, 16f), codexPairFit(russian, 150f, 12f, 16f, 10f, word, value))
        // Only a word wider than the whole row shrinks the labels, together and never below the minimum.
        val narrow = codexPairFit(russian, 90f, 12f, 16f, 10f, word, value)
        assertTrue(narrow.stacked && "столкновений".length * narrow.labelSize * 0.5f <= 90f && narrow.labelSize >= 10f)
    }
}
