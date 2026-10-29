// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.fx.WeaponArcProjection
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

class WeaponArcCullingTest {
    @Test
    fun cullingKeepsCrossingLinksAndZigzagStrokeAtEveryViewportEdge() {
        val width = 320
        val height = 180
        val links = listOf(
            arc(-100f, 20f, 420f, 160f), // Both endpoints outside, beam crosses the screen.
            arc(20f, -100f, 300f, 280f),
            arc(-8f, -100f, -8f, 280f), // Jitter and its stroke can enter from each side.
            arc(328f, -100f, 328f, 280f),
            arc(-100f, -8f, 420f, -8f),
            arc(-100f, 188f, 420f, 188f),
            arc(-12f, 10f, -12f, 170f), // Exactly at the conservative margin.
            arc(332f, 10f, 332f, 170f),
            arc(10f, -12f, 310f, -12f),
            arc(10f, 192f, 310f, 192f),
            arc(-50f, 10f, -50f, 170f), // Entire strokes outside the viewport.
            arc(370f, 10f, 370f, 170f),
            arc(10f, -50f, 310f, -50f),
            arc(10f, 230f, 310f, 230f),
            arc(10f, 10f, 300f, 170f).copy(life = 0f),
        )
        val fx = VisualFxProjection.EMPTY.copy(weaponArcs = links.toImmutableList())
        for (scale in listOf(0.5f, 1f, 2f)) for (time in listOf(0f, 1.23f, 7.5f)) for (shake in listOf(0f, 0.37f, -2.5f)) {
            val pixelWidth = (width * scale).toInt()
            val pixelHeight = (height * scale).toInt()
            val model = FxTestScenes.model(width, height, overrides = arrayOf("elapsed" to time, "viewportDensity" to scale))
            val expected = FxTestScenes.render(pixelWidth, pixelHeight) {
                withWorldUnits(scale) {
                    for ((index, arc) in links.withIndex()) {
                        referenceLink(index, world(model, arc.fromX, arc.fromY, shake, -shake),
                            world(model, arc.toX, arc.toY, shake, -shake), (arc.life / 0.14f).coerceIn(0f, 1f), model.renderElapsed)
                    }
                }
            }.toPixelMap()
            val actual = FxTestScenes.render(pixelWidth, pixelHeight) {
                withWorldUnits(scale) { drawWeaponArcs(model, fx, shake, -shake, KkRolePalette.Default) }
            }.toPixelMap()
            var equal = true
            for (y in 0 until pixelHeight) for (x in 0 until pixelWidth) {
                if (expected[x, y] != actual[x, y]) equal = false
            }
            assertTrue(equal, "Culling changed pixels at scale $scale, time $time, shake $shake")
        }
    }

    private fun arc(x0: Float, y0: Float, x1: Float, y1: Float) = WeaponArcProjection(x0, y0, x1, y1, 0.14f)

    /** The original unculled line renderer is the pixel reference. */
    private fun DrawScope.referenceLink(index: Int, start: Offset, end: Offset, alpha: Float, time: Float) {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val length = max(1f, sqrt(dx * dx + dy * dy))
        val nx = -dy / length
        val ny = dx / length
        var previous = start
        for (segment in 0 until 6) {
            val t = (segment + 1f) / 6f
            val jitter = if (segment == 5) 0f else sin(index * 7f + segment * 4.1f + time * 40f) * 8f
            val next = Offset(start.x + dx * t + nx * jitter, start.y + dy * t + ny * jitter)
            drawLine(KkRolePalette.Default.you.copy(alpha = alpha * 0.25f), previous, next, 6f)
            drawLine(Kk.Bone.copy(alpha = alpha), previous, next, 2f)
            previous = next
        }
    }
}
