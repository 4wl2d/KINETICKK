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
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.gameplay.interaction.*
import kinetickk.ball.gameplay.interaction.canvas.hud.*
import kinetickk.ball.gameplay.interaction.canvas.world.*
import kinetickk.ball.gameplay.interaction.canvas.world.places.*
import kinetickk.ball.gameplay.interaction.fx.InteractionFxLimits
import kinetickk.ball.gameplay.interaction.fx.StructureDisintegrationProjection
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.TotemChestProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Totem chests and a shattering structure on the hot path: four chests (openable, re-arming, materialising and one off
 * screen, then all locked) and a disintegration in the middle of its shards, drawn with the HUD over frames whose time
 * and shard progress move, allocate at most the world budget above an empty frame and build no structure path.
 */
class WorldChestHotPathAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    @Test
    fun chestsAndAShatteringStructureDrawWithoutAllocating() {
        for (keys in listOf(2, 0)) for (roles in listOf(KkRolePalette.Default, KkRolePalette.Mono)) {
            val base = hudTestModel(WIDTH.toFloat(), HEIGHT.toFloat())
            val chests = immutableListOf(
                TotemChestProjection(1L shl 8, base.coreX + 220f, base.coreY - 120f, 3, 0f, 0f),
                TotemChestProjection(2L shl 8, base.coreX - 260f, base.coreY + 90f, 2, 0f, Float.MAX_VALUE),
                TotemChestProjection(3L shl 8, base.coreX - 60f, base.coreY - 260f, 1, Float.MAX_VALUE, Float.MAX_VALUE),
                TotemChestProjection(4L shl 8, base.coreX + 2_600f, base.coreY + 700f, 2, 0f, 0f),
            )
            val life = InteractionFxLimits.DISINTEGRATION_SECONDS
            val frames = List(FRAMES) { frame ->
                base.with("keys" to keys, "totemChests" to chests, "elapsed" to 20f + frame * GameplayRenderModel.FIXED_STEP)
            }
            val fx = List(FRAMES) { frame ->
                VisualFxProjection.EMPTY.copy(disintegrations = immutableListOf(StructureDisintegrationProjection(
                    StructureTemplateId.FOUNDRY, 1, base.coreX + 500f, base.coreY, life * (0.6f - frame * 0.001f), life, seed = 77,
                )))
            }
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 64),
                1.25f, AppLanguage.English, HudTestFonts.typography, roles)
            val canvas = Canvas(ImageBitmap(WIDTH, HEIGHT))
            val scope = CanvasDrawScope()
            val size = Size(WIDTH.toFloat(), HEIGHT.toFloat())
            val memory = HudPresentationMemory()
            fun empty() = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) { drawRect(Kk.Ink) }
            fun frame(index: Int) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) {
                drawGameplay(frames[index], fx[index], measurer, 10f + index * 0.0001f, pauseLayout = null, hudMemory = memory)
            }
            val thread = Thread.currentThread().id
            repeat(FRAMES) { empty(); frame(it) }
            val built = StructurePaths.builds
            var before = threads.getThreadAllocatedBytes(thread)
            repeat(FRAMES) { empty() }
            val emptyBytes = threads.getThreadAllocatedBytes(thread) - before
            before = threads.getThreadAllocatedBytes(thread)
            repeat(FRAMES) { frame(it) }
            val frameBytes = threads.getThreadAllocatedBytes(thread) - before
            val perFrame = (frameBytes - emptyBytes) / FRAMES
            assertTrue(perFrame < 96, "chests ($keys Keys) and a shattering Foundry allocate $perFrame bytes above an empty frame")
            assertEquals(built, StructurePaths.builds, "a warm frame builds no structure path")
        }
    }

    private companion object {
        const val WIDTH = 1_440
        const val HEIGHT = 810
        const val FRAMES = 240
    }
}
