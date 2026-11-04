// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class KkPacifierSigilTest {
    @Test
    fun thePacifierIsTallAndFitsItsCircle() {
        val path = Path()
        val radius = 10f
        KkPacifierSigil.build(path, 50f, -20f, radius)
        val bounds = path.getBounds()
        assertTrue(!path.isEmpty, "the pacifier is empty")
        assertTrue(path.fillType == PathFillType.EvenOdd, "the ring hole and the vents are cut with even-odd")
        assertTrue(
            bounds.left >= 50f - radius - 0.01f && bounds.right <= 50f + radius + 0.01f &&
                bounds.top >= -20f - radius - 0.01f && bounds.bottom <= -20f + radius + 0.01f,
            "the pacifier leaves its circle: $bounds",
        )
        assertTrue(bounds.height >= 1.8f * radius, "handle ring to teat spans the circle: $bounds")
        assertTrue(bounds.width >= 1.8f * radius, "the guard spans the circle: $bounds")
        // Rebuilding into the same path replaces it rather than adding to it.
        KkPacifierSigil.build(path, 0f, 0f, radius)
        assertTrue(abs(path.getBounds().center.x) < 0.5f, "rebuilt at the new centre: ${path.getBounds()}")
    }

    @Test
    fun cachedPacifiersAreReusedForTheSameRadius() {
        val first = KkPacifierSigilPaths.of(12f)
        assertTrue(first === KkPacifierSigilPaths.of(12f))
        assertTrue(KkPacifierSigilPaths.of(13f) !== first)
        val bounds = KkPacifierSigilPaths.of(12f).getBounds()
        assertTrue(abs(bounds.center.x) < 0.5f && bounds.height >= 1.8f * 12f, "centred on the origin: $bounds")
    }
}
