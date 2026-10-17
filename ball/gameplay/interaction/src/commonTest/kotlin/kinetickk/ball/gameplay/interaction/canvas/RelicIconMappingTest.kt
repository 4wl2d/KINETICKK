// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.graphics.Color
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicId
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkVisionMode
import kotlin.test.Test
import kotlin.test.assertEquals

class RelicIconMappingTest {
    @Test
    fun everyCatalogRelicHasItsOwnSharedGeometry() {
        assertEquals(22, RelicId.entries.size)
        val styles = RelicId.entries.map { it.toCanvasRuneStyle() }
        assertEquals(RelicId.entries.size, styles.toSet().size, "No two relics share a rune: $styles")
    }

    @Test
    fun aspectPaletteUsesTheElementTokensInAspectOrder() {
        val expected = mapOf(
            RelicAspect.KINETIC to Color(0xFFD8FF3E),
            RelicAspect.THERMAL to Color(0xFFFF8A1F),
            RelicAspect.CRYO to Color(0xFFBDEBFF),
            RelicAspect.ION to Color(0xFF4F7DFF),
            RelicAspect.GRAVITIC to Color(0xFFD24CFF),
            RelicAspect.ENTROPY to Color(0xFF46D96A),
            RelicAspect.SONIC to Color(0xFF009C84),
            RelicAspect.VOID to Color(0xFFCCC0FC),
            RelicAspect.SOVEREIGN to Color(0xFFFFC93C),
        )
        RelicAspect.entries.forEach { aspect -> assertEquals(expected[aspect], relicAspectColor(aspect)) }
        assertEquals(Kk.Aspects, RelicAspect.entries.map(::relicAspectColor))
        // An elemental aspect wears its element's hue everywhere.
        RelicAspect.entries.forEach { aspect ->
            aspect.element?.let { element -> assertEquals(Kk.element(element.ordinal), relicAspectColor(aspect), "$aspect") }
        }
        // In the player's color-vision palette Kinetic is the preset's volt, an element its ramp, Sovereign the gold.
        for (mode in KkVisionMode.entries) {
            val roles = mode.palette
            RelicAspect.entries.forEach { aspect ->
                val expected = aspect.element?.let { roles.element(it.ordinal) } ?: if (aspect == RelicAspect.KINETIC) roles.you else Kk.Gold
                assertEquals(expected, aspect.overlayColor(roles), "$mode $aspect")
            }
        }
    }
}
