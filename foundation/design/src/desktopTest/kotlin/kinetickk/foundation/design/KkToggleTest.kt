// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertTrue

/** Toggle state words stay whole and clear of the thumb in every language and text size. */
class KkToggleTest {
    @Test
    fun stateWordsKeepTwoDpClearOfTheThumb() {
        // Settings draws the words with a measurer at most at the design size (0.8 at 100 % text).
        val words = listOf("On" to "Off", "Вкл" to "Выкл")
        for (density in listOf(1f, 2f, 3f)) for (scale in listOf(0.8f, 1f, 1.4f)) for ((onWord, offWord) in words) {
            val measurer = kkTestMeasurer(density = density, scale = scale)
            val left = 10f * density
            val top = 10f * density
            val bounds = Rect(left, top, left + 62f * density, top + 28f * density)
            val width = (bounds.right + 10f * density).toInt()
            val height = (bounds.bottom + 10f * density).toInt()
            fun ink(on: Float, label: String?): IntArray = kkRender(width, height, density) {
                drawKkToggle(measurer, bounds, on, onLabel = label, offLabel = label)
            }.argb()
            fun labelColumns(on: Float, label: String): List<Int> {
                val plain = ink(on, null)
                val labelled = ink(on, label)
                return labelled.indices.filter { labelled[it] != plain[it] }.map { it % width }
            }
            val case = "'$offWord'/'$onWord' at density $density, text $scale"
            // Off: the thumb's sheared slab reaches left + 30 dp at its top edge.
            val off = labelColumns(0f, offWord)
            assertTrue(off.size > 10 * density, "$case: the off word is drawn")
            val offThumbRight = left + 30f * density
            assertTrue(off.min() >= offThumbRight + 2f * density - 1f, "$case: off word starts at ${off.min()}, thumb ends at $offThumbRight")
            assertTrue(off.max() < bounds.right - 4f * density, "$case: off word stays on the track (${off.max()})")
            // On: the thumb's slab starts at left + 32 dp at its bottom edge.
            val on = labelColumns(1f, onWord)
            assertTrue(on.size > 10 * density, "$case: the on word is drawn")
            val onThumbLeft = left + 32f * density
            assertTrue(on.max() + 1f <= onThumbLeft - 2f * density + 1f, "$case: on word ends at ${on.max()}, thumb starts at $onThumbLeft")
            assertTrue(on.min() > left + 4f * density, "$case: on word stays on the track (${on.min()})")
        }
    }
}
