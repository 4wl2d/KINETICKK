// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.graphics.Color
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicId
import kinetickk.foundation.design.CanvasRuneStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class RelicIconMappingTest {
    @Test
    fun everyCatalogRelicHasItsOwnSharedGeometry() {
        assertEquals(40, RelicId.entries.size)
        assertEquals(CanvasRuneStyle.entries.toList(), RelicId.entries.map { it.toCanvasRuneStyle() })
    }

    @Test
    fun aspectPaletteMatchesTheExistingInventoryPalette() {
        val expected = mapOf(
            RelicAspect.VECTOR to Color(0xFF42F5E9),
            RelicAspect.GRAVITIC to Color(0xFFA96CFF),
            RelicAspect.ION to Color(0xFF73A6FF),
            RelicAspect.RIFT to Color(0xFFFF4DC4),
            RelicAspect.PRISM to Color(0xFFB6FF5B),
            RelicAspect.ENTROPY to Color(0xFFFF714B),
            RelicAspect.SOVEREIGN to Color(0xFFFFD45B),
        )
        RelicAspect.entries.forEach { aspect -> assertEquals(expected[aspect], relicAspectColor(aspect)) }
    }
}
