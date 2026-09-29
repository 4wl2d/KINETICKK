// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KkListRowFitTest {
    /** Bounding box (left, top, right, bottom) of the pixels near [color] inside [area]. */
    private fun ImageBitmap.inkBox(color: Color, area: Rect, tolerance: Int = 48): Rect? {
        val map = toPixelMap()
        val target = color.toArgb()
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE
        for (y in area.top.toInt() until area.bottom.toInt()) for (x in area.left.toInt() until area.right.toInt()) {
            if (colorDistance(map[x, y].toArgb(), target) <= tolerance) {
                left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y)
            }
        }
        return if (left > right) null else Rect(left.toFloat(), top.toFloat(), right + 1f, bottom + 1f)
    }

    @Test
    fun aTitleTooWideForItsRowShrinksBesideTheTrailingValueInsteadOfBeingCut() {
        // A 250 px section row at the largest text size (the Codex nav at 175 %).
        val measurer = kkTestMeasurer(scale = 1.4f)
        val row = Rect(20f, 10f, 270f, 74f)
        fun render(title: String, trailing: String) = kkRender(300, 84) { drawKkListRow(measurer, row, title, trailing, titleSize = 24f) }
        val short = render("Relics", "23/40")
        val long = render("Inventory sections", "216/400")
        val shortTitle = assertNotNull(short.inkBox(Kk.Bone, row))
        val longTitle = assertNotNull(long.inkBox(Kk.Bone, row))
        // An ellipsized title keeps its full cap height; a fitted one is set smaller, whole.
        assertTrue(longTitle.height < shortTitle.height - 2f, "the long title is set smaller: $longTitle vs $shortTitle")
        // It stays in its area (18 px padding, 14 px before the count) and still fills it: the
        // fitted size is the largest that fits.
        val countWidth = measureKkText(measurer, "216/400", measurer.typography.monoStyle(), uppercase = true).size.width
        val available = row.width - 36f - countWidth - 14f
        assertTrue(longTitle.right <= row.left + 18f + available + 1f, "the title $longTitle runs into the count")
        assertTrue(longTitle.width >= available * 0.85f, "title ${longTitle.width} of $available")
        // A title that fits keeps the requested size.
        val fitting = render("Relics", "216/400").inkBox(Kk.Bone, row)
        assertTrue(fitting != null && kotlin.math.abs(fitting.height - shortTitle.height) < 0.5f)
    }
}
