// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.SiteProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A running bounty drawn by the real world renderer: its arena's edge is a threat ring, the exit band past it is faint
 * and grey, and only the bounty's target carries threat brackets.
 */
class BountyRenderingTest {
    private val arena = 900f
    private val exit = arena + hudTestModel(1_440f, 810f).content.sites.bounty.exitMargin

    @Test
    fun theArenaEdgeIsAThreatRingTheExitBandIsFaintAndGreyAndOnlyTheTargetIsBracketed() {
        // The arena's centre sits 700 u left of the Core, so both rings cross the screen; the target waits inside it.
        val target = enemy(7, EnemyType.WARDEN_PRIME, -400f, 100f, 44f)
        val stranger = enemy(8, EnemyType.DRIFTER, 300f, -200f, 17f)
        val bounty = SiteProjection(SiteKind.BOUNTY_ARENA, "Bounty", -700f, 0f, true, 0f, 0, 0.3f, immutableListOf(7), 0f, 0f,
            radius = arena)
        val model = model(bounty).with("enemies" to immutableListOf(target, stranger))
        val with = render(model)
        val without = render(model.with("sites" to immutableListOf<SiteProjection>(), "trial" to null))
        val centerX = 720f - 700f
        val centerY = 405f
        var edge = 0
        var edgeThreat = 0
        var band = 0
        var bracket = 0
        for (y in 0 until 810) for (x in 0 until 1_440) {
            val index = y * 1_440 + x
            // Only a visible change counts (an antialiased fringe one level off the backdrop has no hue to read).
            if (visibleChange(with[index], without[index]) < 4) continue
            val distance = sqrt((x + 0.5f - centerX) * (x + 0.5f - centerX) + (y + 0.5f - centerY) * (y + 0.5f - centerY))
            val red = (with[index] shr 16) and 0xFF
            val green = (with[index] shr 8) and 0xFF
            val blue = with[index] and 0xFF
            when {
                abs(distance - arena) <= 4f -> {
                    edge++
                    // Even its antialiased fringe keeps the threat hue; the dashes' cores reach its full tint.
                    assertTrue(red > green && red > blue, "the arena's edge at ($x, $y) is threat")
                    if (red > green + 60) edgeThreat++
                }
                abs(distance - exit) <= 5f -> {
                    band++
                    assertTrue(abs(red - green) <= 12 && abs(green - blue) <= 12, "the exit band at ($x, $y) is grey")
                }
                else -> {
                    val dx = x + 0.5f - (720f - 400f)
                    val dy = y + 0.5f - (405f + 100f)
                    assertTrue(abs(dx) <= target.radius + 14f && abs(dy) <= target.radius + 14f,
                        "a pixel at ($x, $y) off both rings brackets the target")
                    bracket++
                }
            }
        }
        assertTrue(edge > 300, "the arena's edge is drawn ($edge px)")
        assertTrue(edgeThreat * 4 >= edge, "the dashes are drawn in full threat ($edgeThreat of $edge px)")
        assertTrue(band > 300, "the exit band is drawn ($band px)")
        assertTrue(bracket > 40, "the target is bracketed ($bracket px)")
    }

    /** The largest channel difference between two pixels. */
    private fun visibleChange(a: Int, b: Int): Int {
        var largest = 0
        for (shift in intArrayOf(0, 8, 16)) largest = maxOf(largest, abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)))
        return largest
    }

    private fun enemy(id: Int, type: EnemyType, x: Float, y: Float, radius: Float) =
        EnemyProjection(id, type, x, y, 0f, 0f, 30f, 30f, radius, 0f, 0f, 0f, 0f, x, y, false)

    private fun render(model: GameplayRenderModel): IntArray {
        val bitmap = ImageBitmap(1_440, 810)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(1_440f, 810f)) {
            drawRect(Kk.Ink)
            drawWorld(model, VisualFxProjection.EMPTY, 0f, 0f, measurer)
        }
        val pixels = IntArray(1_440 * 810)
        bitmap.readPixels(pixels, 0, 0, 1_440, 810)
        return pixels
    }

    private val measurer = CanvasTextMeasurer(
        TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64), 1f, AppLanguage.English,
        HudTestFonts.typography,
    )

    private fun model(site: SiteProjection): GameplayRenderModel =
        hudTestModel(1_440f, 810f).with(
            "coreX" to 0f, "coreY" to 0f, "cameraX" to 0f, "cameraY" to 0f, "totem" to null,
            "enemies" to immutableListOf<EnemyProjection>(), "sites" to immutableListOf(site), "trial" to siteTrial(site),
        )
}
