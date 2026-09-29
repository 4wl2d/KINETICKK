// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.graphics.Color
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicId
import kinetickk.foundation.design.CanvasRuneStyle
import kinetickk.foundation.design.Kk
import kotlin.test.Test
import kotlin.test.assertEquals

class RelicIconMappingTest {
    @Test
    fun everyCatalogRelicHasItsOwnSharedGeometry() {
        assertEquals(40, RelicId.entries.size)
        assertEquals(CanvasRuneStyle.entries.toList(), RelicId.entries.map { it.toCanvasRuneStyle() })
    }

    @Test
    fun aspectPaletteUsesTheRedesignAspectTokensInAspectOrder() {
        val expected = mapOf(
            RelicAspect.VECTOR to Color(0xFFD8FF3E),
            RelicAspect.GRAVITIC to Color(0xFF9B7BFF),
            RelicAspect.ION to Color(0xFF45E0FF),
            RelicAspect.RIFT to Color(0xFFFF4FA3),
            RelicAspect.PRISM to Color(0xFFE4F1FF),
            RelicAspect.ENTROPY to Color(0xFFFF6A2B),
            RelicAspect.SOVEREIGN to Color(0xFFFFC93C),
        )
        RelicAspect.entries.forEach { aspect -> assertEquals(expected[aspect], relicAspectColor(aspect)) }
        assertEquals(Kk.Aspects, RelicAspect.entries.map(::relicAspectColor))
    }
}
