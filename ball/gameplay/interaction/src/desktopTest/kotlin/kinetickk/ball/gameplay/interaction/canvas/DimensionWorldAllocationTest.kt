// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer as ComposeTextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.interaction.canvas.fx.*
import kinetickk.ball.gameplay.interaction.canvas.world.*
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.PortalProjection
import kinetickk.ball.gameplay.nucleus.render.RiftLock
import kinetickk.ball.gameplay.nucleus.render.SiteProjection
import kinetickk.ball.gameplay.nucleus.render.VolatileBurstProjection
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.KkRolePalette
import java.lang.management.ManagementFactory
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The dimensions' atmospheres on steady frames: each layer's sky, ground and weather at the highest particle density
 * (and in a crowd, which thins the weather), every visit mark (halo rings and their glow, embers with catch rings mid
 * life, fire traps on the hostile clock), the tears of the exit rift and of a locked glitch rift with its reason, and in
 * realspace an open and a locked window rift naming why; the clock, the camera and the Desync's local clock move every
 * frame (the datamosh steps, the rain flickers, the shafts sway). Once warm, a frame allocates nothing.
 */
class DimensionWorldAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    @Test
    fun everyLayersAtmosphereAndMarksDrawWithoutAllocating() {
        for (layer in listOf(WorldLayer.ZENITH, WorldLayer.NADIR, WorldLayer.ARCHIVE)) {
            for (roles in listOf(KkRolePalette.Default, KkRolePalette.Mono)) for (crowded in listOf(false, true)) {
                // The mono palette flies below the pace (the slow look of the next ring).
                val perFrame = steadyFrameBytes(roles) { frame -> visitFrame(layer, frame, crowded, slow = roles == KkRolePalette.Mono) }
                assertTrue(perFrame < MAX_BYTES, "$layer ${if (crowded) "in a crowd " else ""}allocates $perFrame bytes a frame")
            }
        }
    }

    @Test
    fun realspaceRiftsAndTheirLocksDrawWithoutAllocating() {
        val perFrame = steadyFrameBytes(KkRolePalette.Default) { frame -> realspaceFrame(frame) }
        assertTrue(perFrame < MAX_BYTES, "realspace rifts allocate $perFrame bytes a frame")
    }

    /** A visit of [layer] at [frame]: every mark, the weather at the high density, a moving camera and clocks. */
    private fun visitFrame(layer: WorldLayer, frame: Int, crowded: Boolean, slow: Boolean): GameplayRenderModel {
        val base = hudTestModel(WIDTH.toFloat(), HEIGHT.toFloat())
        val x = base.coreX
        val y = base.coreY
        val embers = List(12) { index -> (160f + index * 14f) * cos(index * 0.52f) to (160f + index * 14f) * sin(index * 0.52f) }
        val visit = dimensionVisit(layer, x, y, course = listOf(260f to -60f, -240f to 200f), ambient = listOf(-380f to -180f, 400f to 230f),
            embers = embers, emberLife = 0.9f - frame * 0.002f, thrownFrom = listOf(-200f to 0f), emberAge = 0.05f + frame * 0.002f,
            exit = 300f to 160f, completed = false, failed = layer == WorldLayer.ZENITH, localClock = 4f + frame * 0.013f,
            pace = if (slow) 10_000f else 1f,
            timeScale = 0.25f + (frame % 20) * 0.05f, splitRemaining = 1f, splitDuration = 2f)
            .copy(hostileLag = 3f, glitchPortal = PortalProjection(2, x - 260f, y + 220f, WorldLayer.ARCHIVE, locked = true, glitch = true,
                remaining = 20f, radius = FIXTURE_CATCH_RADIUS), lockedRifts = WorldLayer.ARCHIVE.bit, riftLock = RiftLock.BOSS)
        val now = base.elapsed + frame * 0.016f
        val traps = immutableListOf(
            VolatileBurstProjection(x + 120f, y - 80f, 46f, now - 3.4f, now - 2.6f),
            VolatileBurstProjection(x - 140f, y + 60f, 46f, now - 3.8f, now - 2.2f),
        )
        val crowd = if (crowded) List(FxFrame.CROWD_SECONDARY_OFF + 4) { index ->
            enemyFixture(index + 10, x - 400f + (index % 10) * 80f, y - 300f + (index / 10) * 90f)
        } else emptyList()
        return base.withDimension(visit).with(
            "elapsed" to now, "cameraX" to x + frame * 0.7f, "cameraY" to y - frame * 0.4f,
            "previousCameraX" to x + frame * 0.7f, "previousCameraY" to y - frame * 0.4f,
            "volatileBursts" to traps, "enemies" to crowd.toImmutableList(),
            "settings" to base.settings.copy(particleDensity = ParticleDensity.HIGH),
        )
    }

    /** Realspace at [frame] with an open Zenith rift and a Nadir rift locked while a boss lives. */
    private fun realspaceFrame(frame: Int): GameplayRenderModel {
        val base = hudTestModel(WIDTH.toFloat(), HEIGHT.toFloat())
        fun rift(kind: SiteKind, dx: Float, dy: Float) =
            SiteProjection(kind, kind.name, base.coreX + dx, base.coreY + dy, false, 30f - frame * 0.01f, 0, 0f, immutableListOf(), 0f, 0f)
        return base.withDimension(kinetickk.ball.gameplay.nucleus.render.DimensionProjection(lockedRifts = WorldLayer.NADIR.bit, riftLock = RiftLock.BOSS))
            .with("sites" to listOf(rift(SiteKind.ZENITH_RIFT, 260f, 110f), rift(SiteKind.NADIR_RIFT, -300f, -60f)).toImmutableList(),
                "elapsed" to base.elapsed + frame * 0.016f)
    }

    private fun enemyFixture(id: Int, x: Float, y: Float) =
        EnemyProjection(id, EnemyType.DRIFTER, x, y, 0f, 0f, 100f, 100f, 14f, 0f, 0f, 0f, 0f, x, y, false)

    /** Bytes per frame above an empty frame, once [FRAMES] frames warmed every cache; the best of a few passes. */
    private fun steadyFrameBytes(roles: KkRolePalette, model: (Int) -> GameplayRenderModel): Long {
        val warm = Array(FRAMES) { model(it) }
        val measured = Array(FRAMES) { model(FRAMES + it) }
        val measurer = CanvasTextMeasurer(ComposeTextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1.25f,
            AppLanguage.English, roles = roles)
        val bitmap = ImageBitmap(WIDTH, HEIGHT)
        val scope = CanvasDrawScope()
        val canvas = Canvas(bitmap)
        val thread = Thread.currentThread().id
        fun frames(models: Array<GameplayRenderModel>, draw: Boolean): Long {
            val before = threads.getThreadAllocatedBytes(thread)
            for (frame in models) {
                scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(WIDTH.toFloat(), HEIGHT.toFloat())) {
                    if (draw) {
                        drawBackdrop(frame, 1f, -1f, 2f, roles)
                        drawWorld(frame, VisualFxProjection.EMPTY, 1f, -1f, measurer)
                    }
                }
            }
            return (threads.getThreadAllocatedBytes(thread) - before) / models.size
        }
        repeat(2) { frames(warm, draw = true) }
        val empty = frames(measured, draw = false)
        var best = Long.MAX_VALUE
        repeat(PASSES) { best = minOf(best, frames(measured, draw = true) - empty) }
        return best
    }

    private companion object {
        const val WIDTH = 960
        const val HEIGHT = 600
        const val FRAMES = 40
        const val PASSES = 3
        /** The world hot path's bound (WorldHotPathAllocationTest). */
        const val MAX_BYTES = 96L
    }
}
