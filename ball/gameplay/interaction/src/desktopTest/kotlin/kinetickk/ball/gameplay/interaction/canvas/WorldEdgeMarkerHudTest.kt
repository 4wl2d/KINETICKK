// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.layout.forEachRunningControlBounds
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.PointOfInterestProjection
import kinetickk.ball.gameplay.nucleus.render.TotemProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.localeList
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Off-screen markers drawn by the real world renderer next to the real HUD (bundled fonts): every
 * edge marker stays clear of every block the HUD actually draws (its probe rects and the running
 * controls), not only of the keep-out the planner reads, for targets in every direction, at every
 * text size, with and without a boss and a trial.
 */
class WorldEdgeMarkerHudTest {
    @Test
    fun edgeMarkersNeverTouchTheHudAsDrawn() {
        var markers = 0
        for ((dpWidth, dpHeight) in listOf(1_440f to 810f, 844f to 390f, 390f to 844f)) for (density in listOf(1f, 3f)) {
            val width = dpWidth * density
            val height = dpHeight * density
            for (language in AppLanguage.entries) for (textScale in listOf(1f, 1.25f, 1.75f)) {
                val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(density), LayoutDirection.Ltr, cacheSize = 64),
                    textScale, language, HudTestFonts.typography.copy(localeList = language.localeList()))
                for (boss in listOf(null, EnemyType.ELITE, EnemyType.ARCHITECT)) for (trial in listOf(false, true)) {
                    val base = hudTestModel(dpWidth, dpHeight).with(
                        "screenWidth" to width, "screenHeight" to height, "uiScale" to density,
                        "coreX" to 0f, "coreY" to 0f, "cameraX" to 0f, "cameraY" to 0f,
                        "keys" to 12, "runMatter" to 9_876_543L, "combo" to 128, "comboTime" to 1f, "comboWindow" to 2.8f, "level" to 45,
                        "enemies" to (if (boss == null) emptyList() else listOf(EnemyProjection(4, boss, 0f, -9_000f, 0f, 0f, 500f, 1_000f,
                            60f, 0f, 0f, 0f, 0f, 0f, 0f, false))).toImmutableList(),
                    )
                    for (step in 0 until DIRECTIONS) {
                        val angle = step * 2f * PI.toFloat() / DIRECTIONS
                        // The totem in this direction and, while a trial runs, its orbit on the opposite side.
                        val model = base.with(
                            "totem" to TotemProjection(cos(angle) * DISTANCE, sin(angle) * DISTANCE, 1f),
                            "pointsOfInterest" to (if (trial) listOf(orbit(-cos(angle) * DISTANCE, -sin(angle) * DISTANCE)) else emptyList())
                                .toImmutableList(),
                        )
                        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, canvas, Size(width, height)) {
                            drawWorld(model, VisualFxProjection.EMPTY, 0f, 0f, measurer)
                            drawHud(model, measurer, 1f)
                        }
                        val drawn = drawnHud(width, height, density)
                        val where = "$language ${dpWidth}x$dpHeight @$density text=$textScale boss=$boss trial=$trial " +
                            "direction=${step * 360 / DIRECTIONS}"
                        val boxes = WorldDrawProbe.rects(WorldDrawn.EDGE_MARKER)
                        assertTrue(boxes.size == (if (trial) 2 else 1), "$where: one marker per off-screen target ($boxes)")
                        boxes.forEach { box ->
                            markers++
                            assertTrue(box.left >= 0f && box.top >= 0f && box.right <= width && box.bottom <= height, "$where: marker $box on screen")
                            drawn.forEach { (name, block) ->
                                assertTrue(!box.overlaps(block), "$where: marker $box on the HUD's $name $block")
                            }
                        }
                    }
                }
            }
        }
        assertTrue(markers > 10_000, "the sweep draws its markers ($markers)")
    }

    /** What the HUD drew in the last frame: its probe blocks (not the world-anchored polarity label) and the running controls. */
    private fun drawnHud(width: Float, height: Float, density: Float): List<Pair<String, Rect>> = buildList {
        HudBlock.entries.forEach { block ->
            if (block == HudBlock.POLARITY_LABEL) return@forEach
            HudLayoutProbe.rect(block)?.let { add(block.name to it) }
        }
        forEachRunningControlBounds(width, height, density) { target, left, top, right, bottom ->
            add(target.name to Rect(left, top, right, bottom))
        }
    }

    private fun orbit(x: Float, y: Float) = PointOfInterestProjection(PointOfInterestKind.COLLAPSING_ORBIT, "Collapsing orbit", x, y, true,
        12f, 0, 0.4f, immutableListOf(), 0f, 0f)

    // The size passed to draw sets the screen; a 1 px bitmap keeps the sweep fast.
    private val canvas = Canvas(ImageBitmap(1, 1))

    private companion object {
        const val DIRECTIONS = 48
        const val DISTANCE = 3_000f
    }
}
