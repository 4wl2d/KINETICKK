// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
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
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.PointOfInterestProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.localeList
import kinetickk.foundation.design.measureKkText
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The world's HUD keep-out follows the HUD as drawn: every top block the HUD draws lies inside it at
 * every text size, so edge markers, point labels and the orbit timer never sit on the HUD (above all
 * the elite / Architect block on portrait phones, which has its own row under the chips).
 */
class HudKeepOutTest {
    private val viewports = listOf(1_440f to 810f, 844f to 390f, 390f to 844f)

    @Test
    fun everyTopBlockTheHudDrawsLiesInsideTheWorldKeepOut() {
        val orbit = PointOfInterestProjection(PointOfInterestKind.COLLAPSING_ORBIT, "Collapsing orbit", 0f, 0f, true, 12f, 0, 0.4f,
            immutableListOf(), 0f, 0f)
        for ((dpWidth, dpHeight) in viewports) for (density in listOf(1f, 3f)) for (language in AppLanguage.entries) {
            val width = dpWidth * density
            val height = dpHeight * density
            for (textScale in listOf(1f, 1.25f, 1.75f)) for (boss in listOf(EnemyType.ELITE, EnemyType.ARCHITECT)) for (trial in listOf(false, true)) {
                val model = hudTestModel(dpWidth, dpHeight).with(
                    "screenWidth" to width, "screenHeight" to height, "uiScale" to density,
                    "keys" to 12, "runMatter" to 9_876_543L, "combo" to 128, "comboTime" to 1f, "comboWindow" to 2.8f, "level" to 45,
                    "enemies" to listOf(EnemyProjection(4, boss, 0f, 0f, 0f, 0f, 500f, 1_000f, 60f, 0f, 0f, 0f, 0f, 0f, 0f, false))
                        .toImmutableList(),
                    "pointsOfInterest" to (if (trial) listOf(orbit) else emptyList()).toImmutableList(),
                )
                drawHudAt(width, height, density, language, textScale) { measurer -> drawHud(model, measurer, 1f) }
                val keepOut = WorldHudKeepOut().update(width, height, density, textScale, trial, bossPresent = true)
                val rects = keepOut.rects()
                val where = "$language ${dpWidth}x$dpHeight @$density x$textScale $boss trial=$trial"
                val blocks = listOf(HudBlock.CLOCK, HudBlock.MATTER_CHIP, HudBlock.KEY_CHIP, HudBlock.CHAIN, HudBlock.BOSS, HudBlock.BADGE) +
                    if (trial) listOf(HudBlock.TRIAL_PANEL) else emptyList()
                blocks.forEach { block ->
                    val drawn = assertNotNull(HudLayoutProbe.rect(block), "$where: $block not drawn")
                    assertTrue(covered(drawn, rects), "$where: $block $drawn is outside the keep-out $rects")
                }
            }
        }
    }

    @Test
    fun portraitMarkersLabelsAndTheOrbitTimerStayOffTheBossBlock() {
        for (textScale in listOf(1f, 1.25f, 1.75f)) for (boss in listOf(EnemyType.ELITE, EnemyType.ARCHITECT)) for (trial in listOf(false, true)) {
            for (language in AppLanguage.entries) {
                val model = hudTestModel(390f, 844f).with(
                    "enemies" to listOf(EnemyProjection(4, boss, 0f, 0f, 0f, 0f, 500f, 1_000f, 60f, 0f, 0f, 0f, 0f, 0f, 0f, false))
                        .toImmutableList(),
                )
                var timerHeight = 0f
                var timerBaseline = 0f
                drawHudAt(390f, 844f, 1f, language, textScale) { measurer ->
                    drawHud(model, measurer, 1f)
                    val layout = measureKkText(measurer, "0:15", measurer.typography.condStyle(24f, tabular = true))
                    timerHeight = layout.size.height.toFloat()
                    timerBaseline = layout.firstBaseline
                }
                val bossRect = assertNotNull(HudLayoutProbe.rect(HudBlock.BOSS))
                val where = "$language x$textScale $boss trial=$trial"
                val keepOut = WorldHudKeepOut().update(390f, 844f, 1f, textScale, trial, bossPresent = true)
                // Off-screen targets above the view (up, up-left, up-right) and along the upper side edges.
                val planner = EdgeMarkerPlanner().prepare(keepOut, 390f, 844f, 1f, 64f)
                val center = Offset(195f, 422f)
                for (step in 0..72) {
                    val angle = PI.toFloat() + step * PI.toFloat() / 72f // from straight left, over the top, to straight right
                    val marker = planner.position(center + Offset(cos(angle), sin(angle)) * 3_000f)
                    val half = planner.markerHalfHeight()
                    val box = Rect(planner.markerLeft(marker.x), marker.y - half, planner.markerRight(marker.x), marker.y + half)
                    assertFalse(box.overlaps(bossRect), "$where: edge marker $box on the boss block $bossRect")
                }
                // Point labels the keep-out approves, anywhere across the upper screen.
                var y = 0f
                while (y < 422f) {
                    var x = 0f
                    while (x < 390f) {
                        val label = Rect(x, y, x + 64f, y + 16f)
                        if (!keepOut.intersects(label.left, label.top, label.right, label.bottom)) {
                            assertFalse(label.overlaps(bossRect), "$where: approved label $label on the boss block")
                        }
                        x += 13f
                    }
                    y += 3f
                }
                // The collapsing-orbit timer for every orbit center, as the world places it.
                val half = 28f
                var centerY = -300f
                while (centerY < 1_100f) {
                    val baseline = orbitTimerBaseline(centerY, timerBaseline, 390f, 844f, 1f) { top, base ->
                        !keepOut.intersects(195f - half, top, 195f + half, base + timerHeight - timerBaseline)
                    }
                    if (!baseline.isNaN()) {
                        val box = Rect(195f - half, baseline - timerBaseline, 195f + half, baseline - timerBaseline + timerHeight)
                        assertFalse(box.overlaps(bossRect), "$where: orbit timer $box on the boss block $bossRect")
                    }
                    centerY += 4f
                }
            }
        }
    }

    /** Whether every point of [rect] lies in one of [rects] (sampled on a 1 px grid, edges included). */
    private fun covered(rect: Rect, rects: List<Rect>): Boolean {
        var y = rect.top
        while (true) {
            var x = rect.left
            while (true) {
                if (rects.none { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }) return false
                if (x >= rect.right) break
                x = minOf(rect.right, x + 1f)
            }
            if (y >= rect.bottom) break
            y = minOf(rect.bottom, y + 1f)
        }
        return true
    }

    private fun drawHudAt(
        width: Float,
        height: Float,
        density: Float,
        language: AppLanguage,
        textScale: Float,
        block: androidx.compose.ui.graphics.drawscope.DrawScope.(CanvasTextMeasurer) -> Unit,
    ) {
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(density), LayoutDirection.Ltr), textScale,
            language, HudTestFonts.typography.copy(localeList = language.localeList()))
        // Probe-only: a small bitmap is enough (drawing outside it is clipped).
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(ImageBitmap(64, 64)), Size(width, height)) { block(measurer) }
    }
}
