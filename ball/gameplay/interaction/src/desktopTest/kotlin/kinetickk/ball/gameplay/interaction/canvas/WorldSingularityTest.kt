// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The singularity (cursor) follows `kk.css .sing`: its ring is a border with no background, so an
 * enemy, glyph or effect under the cursor stays visible inside the ring around the bone dot. Baby
 * mode's cursor keeps that see-through interior with its pacifier wings, in the soft role.
 */
class WorldSingularityTest {
    @Test
    fun singularityCenterIsSeeThrough() {
        listOf(KkRolePalette.Default, KkRolePalette.Mono).forEach { roles ->
            listOf(false, true).forEach { danger -> listOf(false, true).forEach { easy ->
                val bitmap = ImageBitmap(120, 120)
                val center = Offset(60f, 60f)
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(120f, 120f)) {
                    drawRect(Kk.AGravitic) // whatever lies under the cursor
                    drawSingularity(center, 1.3f, danger, roles, easy)
                }
                val pixels = bitmap.toPixelMap()
                // Between the bone dot (radius 4) and the ring's inner edge (13.5 - 1.5), in every direction.
                for (step in 0 until 16) {
                    val angle = step * Math.PI / 8
                    for (radius in listOf(6.5, 8.5, 10.5)) {
                        val x = (center.x + radius * kotlin.math.cos(angle)).toInt()
                        val y = (center.y + radius * kotlin.math.sin(angle)).toInt()
                        assertEquals(Kk.AGravitic, pixels[x, y], "$roles danger=$danger easy=$easy: pixel ($x, $y) inside the ring shows what is under it")
                    }
                }
                assertEquals(Kk.Bone, pixels[60, 60], "the bone dot stays")
                // The ring is threat, or the soft role of Baby mode's harmless cursor.
                assertEquals(if (easy) roles.soft else roles.threat, pixels[60 + 13, 60], "$roles danger=$danger easy=$easy: the ring's tone")
                // The pacifier wings (radius 19, 4 px) sit left and right of the ring, never above or below it.
                assertEquals(easy, pixels[78, 60] != Kk.AGravitic && pixels[41, 60] != Kk.AGravitic,
                    "$roles danger=$danger easy=$easy: the wings at the sides")
                assertEquals(Kk.AGravitic, pixels[60, 41], "$roles danger=$danger easy=$easy: nothing above the ring at the wings' radius")
                assertEquals(Kk.AGravitic, pixels[60, 78], "$roles danger=$danger easy=$easy: nothing below the ring at the wings' radius")
            } }
        }
    }
}
