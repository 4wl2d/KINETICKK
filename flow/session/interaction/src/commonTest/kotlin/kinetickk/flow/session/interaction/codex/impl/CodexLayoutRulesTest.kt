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
    fun newBandMovesTheGlyphBelowTheStamp() {
        val cell = 64f
        val (plainCenter, plainRadius) = codexCellGlyph(cell, 0f, 1f)
        assertEquals(32f, plainCenter)
        val band = codexNewBand(Size(40f, 16f), 1f)
        val (center, radius) = codexCellGlyph(cell, band, 1f)
        assertTrue(center - radius >= band, "the glyph starts below the band")
        assertTrue(center + radius <= cell, "the glyph stays inside the cell")
        assertTrue(radius <= plainRadius)
    }
}
