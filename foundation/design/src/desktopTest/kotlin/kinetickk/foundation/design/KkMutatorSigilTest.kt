// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KkMutatorSigilTest {
    @Test
    fun everySigilIsNonEmptyAndFitsItsCircle() {
        val path = Path()
        for (index in 0..KkMutatorSigil.TIER) {
            KkMutatorSigil.build(path, index, 50f, -20f, 10f)
            val bounds = path.getBounds()
            assertTrue(!path.isEmpty, "sigil $index is empty")
            assertTrue(bounds.width > 8f && bounds.height > 6f, "sigil $index is too small: $bounds")
            assertTrue(bounds.left >= 39.9f && bounds.right <= 60.1f && bounds.top >= -30.1f && bounds.bottom <= -9.9f,
                "sigil $index leaves its circle: $bounds")
            assertTrue(path.fillType == PathFillType.EvenOdd, "sigil $index cuts its holes with even-odd")
        }
        assertFailsWith<IllegalArgumentException> { KkMutatorSigil.build(path, -1, 0f, 0f, 1f) }
        assertFailsWith<IllegalArgumentException> { KkMutatorSigil.build(path, KkMutatorSigil.TIER + 1, 0f, 0f, 1f) }
    }

    @Test
    fun cachedSigilsAreReusedForTheSameIndexAndRadius() {
        val first = KkMutatorSigilPaths.of(3, 8f)
        assertTrue(first === KkMutatorSigilPaths.of(3, 8f))
        assertTrue(KkMutatorSigilPaths.of(4, 8f) !== KkMutatorSigilPaths.of(3, 9f))
        val bounds = KkMutatorSigilPaths.of(KkMutatorSigil.ASCENDANT, 12f).getBounds()
        assertTrue(kotlin.math.abs(bounds.center.x) < 1f, "centred on the origin: $bounds")
    }
}
