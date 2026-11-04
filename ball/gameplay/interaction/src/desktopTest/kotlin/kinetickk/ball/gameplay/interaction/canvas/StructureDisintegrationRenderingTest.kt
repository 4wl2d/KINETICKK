// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.gameplay.interaction.*
import kinetickk.ball.gameplay.interaction.canvas.world.*
import kinetickk.ball.gameplay.interaction.canvas.fx.*
import kinetickk.ball.gameplay.interaction.canvas.world.places.*
import kinetickk.ball.gameplay.interaction.fx.InteractionFxLimits
import kinetickk.ball.gameplay.interaction.fx.StructureDisintegrationProjection
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.ObstacleProjection
import kinetickk.ball.gameplay.nucleus.render.PickupProjection
import kinetickk.ball.gameplay.nucleus.render.SiteProjection
import kinetickk.ball.gameplay.nucleus.render.TrailPointProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A spent structure shattering, drawn by the real world renderer from the FX projection: early on its floor and its
 * flashing slabs show, half-way its shards drift (taking rubble draw operations) over a fainter floor, after its life
 * nothing is left; a refused rubble budget skips only the shards; and a warm frame builds no path.
 */
class StructureDisintegrationRenderingTest {
    private val roles = KkRolePalette.Default

    @Test
    fun floorsCracksAndShardsDrawOverTheLifeAndNothingAfterIt() {
        val empty = render(VisualFxProjection.EMPTY)
        val early = render(fx(progress = 0.1f))
        val middle = render(fx(progress = 0.5f))
        val shardOps = FxDrawBudget.used(FxPool.RUBBLE)
        assertTrue(shardOps > 0, "half-way the shards take rubble operations")
        assertTrue(shardOps <= FxPool.RUBBLE.ops)

        // The floor of a room (a Spire's base hall around its centre-line) fades: brighter early than half-way.
        val floorEarly = luminance(early.at(0f, 900f))
        val floorMiddle = luminance(middle.at(0f, 900f))
        assertTrue(floorEarly > luminance(empty.at(0f, 900f)), "the floor shows early")
        assertTrue(floorEarly > floorMiddle, "the floor fades ($floorEarly → $floorMiddle)")
        // The slabs flash early: the Spire's outer west wall line is lit while the rest of the screen stays ink.
        assertTrue(litPixels(early) > litPixels(empty) + 500, "the slabs and floor draw early (${litPixels(early)})")
        assertTrue(litPixels(middle) > litPixels(empty) + 100, "shards and a fading floor draw half-way (${litPixels(middle)})")
        // After its life nothing is drawn.
        assertEquals(litPixels(empty), litPixels(render(fx(progress = 1f))), "a finished disintegration draws nothing")
    }

    @Test
    fun aRefusedRubbleBudgetSkipsOnlyTheShards() {
        val disintegration = fx(progress = 0.12f).disintegrations.single()
        fun draw(starved: Boolean): PixelMap {
            val bitmap = ImageBitmap(WIDTH.toInt(), HEIGHT.toInt())
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(WIDTH, HEIGHT)) {
                drawRect(Kk.Ink)
                FxDrawBudget.reset()
                if (starved) FxDrawBudget.take(FxPool.RUBBLE, FxPool.RUBBLE.ops, signature = false)
                withWorldUnits(scene.worldScale) {
                    drawStructureDisintegrations(scene, VisualFxProjection.EMPTY.copy(disintegrations = immutableListOf(disintegration)), 0f, 0f, roles)
                }
            }
            return bitmap.toPixelMap()
        }
        val full = draw(starved = false)
        val starved = draw(starved = true)
        assertEquals(1, FxDrawBudget.skipped(FxPool.RUBBLE), "the first refused shard stops the shards for the frame")
        assertTrue(litPixels(starved) > 500, "the floor and the flashing slabs still draw (${litPixels(starved)})")
        assertTrue(litPixels(full) > litPixels(starved), "the shards add to the frame")
    }

    @Test
    fun aWarmDisintegrationFrameBuildsNoPath() {
        render(fx(progress = 0.05f))
        render(fx(progress = 0.4f))
        val built = StructurePaths.builds
        render(fx(progress = 0.06f))
        render(fx(progress = 0.7f))
        assertEquals(built, StructurePaths.builds, "floors and slabs come from the cached structure paths")
    }

    // -----------------------------------------------------------------------------------------

    /** A Spire shattering at the view's centre, [progress] of the way through its life. */
    private fun fx(progress: Float): VisualFxProjection {
        val life = InteractionFxLimits.DISINTEGRATION_SECONDS
        return VisualFxProjection.EMPTY.copy(disintegrations = immutableListOf(
            StructureDisintegrationProjection(StructureTemplateId.SPIRE, 0, CX, CY, life * (1f - progress), life, seed = 1_234),
        ))
    }

    /** One run's model, built once: its content holds the templates the structure paths are cached by. */
    private val scene: GameplayRenderModel = hudTestModel(WIDTH, HEIGHT).with(
        "coreX" to CX + 2_000f, "coreY" to CY, "previousCoreX" to CX + 2_000f, "previousCoreY" to CY,
        "cameraX" to CX, "cameraY" to CY + 600f, "previousCameraX" to CX, "previousCameraY" to CY + 600f,
        "velocityX" to 0f, "velocityY" to 0f, "pointerX" to 5f, "pointerY" to 5f, "interpolationAlpha" to 1f,
        "obstacles" to immutableListOf<ObstacleProjection>(), "enemies" to immutableListOf<EnemyProjection>(),
        "pickups" to immutableListOf<PickupProjection>(), "trail" to immutableListOf<TrailPointProjection>(),
        "sites" to immutableListOf<SiteProjection>(), "trial" to null,
        "viewportDensity" to 1f, "uiScale" to 1f, "screenWidth" to WIDTH, "screenHeight" to HEIGHT,
    )

    private fun render(visualFx: VisualFxProjection): PixelMap {
        val bitmap = ImageBitmap(WIDTH.toInt(), HEIGHT.toInt())
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f,
            AppLanguage.English)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(WIDTH, HEIGHT)) {
            drawRect(Kk.Ink)
            drawWorld(scene, visualFx, 0f, 0f, measurer)
        }
        return bitmap.toPixelMap()
    }

    /** The pixel of the Spire's template point ([x], [y]); the view is centred 600 below its centre. */
    private fun PixelMap.at(x: Float, y: Float): Color = this[(WIDTH * 0.5f + x).toInt(), (HEIGHT * 0.5f + y - 600f).toInt()]

    private fun litPixels(pixels: PixelMap): Int {
        var count = 0
        for (y in 0 until pixels.height step 2) for (x in 0 until pixels.width step 2) {
            if (luminance(pixels[x, y]) > luminance(Kk.Ink) + 0.02f) count++
        }
        return count
    }

    private fun luminance(color: Color): Float = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

    private companion object {
        const val WIDTH = 1_200f
        const val HEIGHT = 1_400f
        const val CX = 40_000f
        const val CY = 40_000f
    }
}
