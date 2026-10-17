// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.graphics.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KkFormGeometryTest {
    @Test
    fun everyFormFitsAboutItsCircleAndIsCenteredOnTheRequestedPoint() {
        val path = Path()
        for (index in 0 until KkFormGeometry.COUNT) {
            KkFormGeometry.build(path, index, 100f, 200f, 40f)
            val bounds = path.getBounds()
            assertTrue(!path.isEmpty, "form $index is empty")
            assertTrue(bounds.width > 20f && bounds.height > 20f, "form $index is too small: $bounds")
            // Every form stays within 1.05 radii (the diamond's points reach just past the circle).
            assertTrue(bounds.left >= 57.9f && bounds.right <= 142.1f && bounds.top >= 157.9f && bounds.bottom <= 242.1f,
                "form $index leaves its circle: $bounds")
            assertTrue(kotlin.math.abs(bounds.center.x - 100f) < 12f && kotlin.math.abs(bounds.center.y - 200f) < 12f,
                "form $index is off center: $bounds")
        }
    }

    @Test
    fun rebuildingRewindsThePathAndScalesWithTheRadius() {
        val path = Path()
        KkFormGeometry.build(path, KkFormGeometry.HEXAGON, 0f, 0f, 10f)
        val small = path.getBounds()
        KkFormGeometry.build(path, KkFormGeometry.HEXAGON, 0f, 0f, 30f)
        val large = path.getBounds()
        assertEquals(small.width * 3f, large.width, 0.01f)
        KkFormGeometry.build(path, KkFormGeometry.CIRCLE, 500f, 500f, 10f)
        assertEquals(20f, path.getBounds().width, 0.01f)
    }

    @Test
    fun onlyLineFiguresAreStrokedAndTheIndexIsBounded() {
        val stroked = (0 until KkFormGeometry.COUNT).filter(KkFormGeometry::isStroked)
        assertEquals(listOf(KkFormGeometry.RING, KkFormGeometry.TESSERACT, KkFormGeometry.SPIRAL), stroked)
        stroked.forEach { assertTrue(KkFormGeometry.strokeWidth(it, 10f) > 0f) }
        assertEquals(0f, KkFormGeometry.strokeWidth(KkFormGeometry.STAR, 10f))
        assertFailsWith<IllegalArgumentException> { KkFormGeometry.build(Path(), KkFormGeometry.COUNT, 0f, 0f, 1f) }
        assertFailsWith<IllegalArgumentException> { KkFormGeometry.build(Path(), -1, 0f, 0f, 1f) }
    }
}
