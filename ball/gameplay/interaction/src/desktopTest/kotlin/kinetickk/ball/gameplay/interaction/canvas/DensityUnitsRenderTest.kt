// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer as ComposeTextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.input.GameInteractionValidator
import kinetickk.ball.gameplay.interaction.input.InteractionValidationResult
import kinetickk.ball.gameplay.interaction.input.hudHeight
import kinetickk.ball.gameplay.interaction.input.hudWidth
import kinetickk.ball.gameplay.nucleus.GameplayDecision
import kinetickk.ball.gameplay.nucleus.GameplayNucleus
import kinetickk.ball.gameplay.nucleus.GameplayNucleusPulse
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import java.lang.management.ManagementFactory
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The simulation runs in dp: the interaction boundary divides the viewport and the pointer by the
 * density, and the world layer is drawn magnified by it. HUD-anchored world points use the same
 * mapping. Frames between fixed steps draw interpolated positions.
 */
class DensityUnitsRenderTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    @Test
    fun atDensityTwoTheNucleusSimulatesHalfThePixelViewport() {
        val dense = modelAt(DP_WIDTH * 2f, DP_HEIGHT * 2f, density = 2f)
        val plain = modelAt(DP_WIDTH, DP_HEIGHT, density = 1f)

        assertEquals(DP_WIDTH, dense.screenWidth)
        assertEquals(DP_HEIGHT, dense.screenHeight)
        assertEquals(2f, dense.viewportDensity)
        assertEquals(2f, dense.uiScale)
        assertEquals(DP_WIDTH * 2f, dense.hudWidth)
        assertEquals(DP_HEIGHT * 2f, dense.hudHeight)
        assertEquals(plain.coreX, dense.coreX)
        assertEquals(plain.cameraX, dense.cameraX)
        assertEquals(plain.pointerX, dense.pointerX)
    }

    @Test
    fun aWorldPointMapsToTheSameScreenPositionScaledByTheDensity() {
        val dense = modelAt(DP_WIDTH * 2f, DP_HEIGHT * 2f, density = 2f)
        val plain = modelAt(DP_WIDTH, DP_HEIGHT, density = 1f)
        val points = listOf(Offset(dense.coreX, dense.coreY), Offset(dense.coreX + 230f, dense.coreY - 145f), Offset(-600f, 410f))

        val plainPoints = drawIn(DP_WIDTH, DP_HEIGHT, 1f) { points.map { world(plain, it.x, it.y, 3f, -2f) } }
        drawIn(DP_WIDTH * 2f, DP_HEIGHT * 2f, 2f) {
            points.forEachIndexed { index, point ->
                val screen = worldOnScreen(dense, point.x, point.y, 6f, -4f)
                assertClose(plainPoints[index] * 2f, screen, "HUD mapping of point $index")
            }
            withWorldUnits(dense.worldScale) {
                assertEquals(Size(DP_WIDTH, DP_HEIGHT), size, "the world layer sees the dp viewport")
                assertEquals(1f, density, "world lengths are dp inside the world layer")
                points.forEachIndexed { index, point ->
                    assertClose(plainPoints[index], world(dense, point.x, point.y, 3f, -2f), "world layer point $index")
                }
                inScreenUnits(dense.worldScale) {
                    assertEquals(Size(DP_WIDTH * 2f, DP_HEIGHT * 2f), size)
                    assertEquals(2f, density)
                }
                assertEquals(1f, density)
            }
            assertEquals(Size(DP_WIDTH * 2f, DP_HEIGHT * 2f), size, "the screen layer is restored")
            assertEquals(2f, density)
        }
    }

    @Test
    fun theWorldLayerAtDensityTwoIsTheDensityOneWorldMagnified() {
        val dense = modelAt(DP_WIDTH * 2f, DP_HEIGHT * 2f, density = 2f)
        val plain = modelAt(DP_WIDTH, DP_HEIGHT, density = 1f)
        val plainBone = boneBounds(renderWorld(plain, DP_WIDTH.toInt(), DP_HEIGHT.toInt(), 1f), DP_WIDTH.toInt())
        val denseBone = boneBounds(renderWorld(dense, DP_WIDTH.toInt() * 2, DP_HEIGHT.toInt() * 2, 2f), DP_WIDTH.toInt() * 2)
        // The Core body (bone) around the screen center: twice as large, at twice the position.
        assertTrue(abs(denseBone.width - plainBone.width * 2f) <= 3f, "Core width $plainBone -> $denseBone")
        assertTrue(abs(denseBone.center.x - plainBone.center.x * 2f) <= 2f, "Core center $plainBone -> $denseBone")
        assertTrue(abs(denseBone.center.y - plainBone.center.y * 2f) <= 2f, "Core center $plainBone -> $denseBone")
    }

    @Test
    fun thePolarityEdgeGlowsOnTheSideTheDpPointerStrainsTowardAtDensityTwo() {
        // Pointer near the right edge of the dp viewport, vertically centered.
        val base = modelAt(DP_WIDTH * 2f, DP_HEIGHT * 2f, density = 2f)
            .with("pointerX" to DP_WIDTH * 0.95f, "pointerY" to DP_HEIGHT * 0.5f)
        val memory = HudPresentationMemory()
        val boss = BossTarget()
        val steady = base.with("polarityStability" to 0.5f)
        val drained = base.with("polarityStability" to 0.4f, "elapsed" to base.elapsed + 0.05f)
        memory.observe(steady, 0f, boss.select(steady))
        memory.observe(drained, 0.05f, boss.select(drained))
        val width = (DP_WIDTH * 2f).toInt()
        val height = (DP_HEIGHT * 2f).toInt()
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawRect(Kk.Ink)
            drawHudScreenStates(drained, KkRolePalette.Default, renderTime = 0.05f, memory = memory, critical = false)
        }
        val pixels = bitmap.toPixelMap()
        val ink = Kk.Ink.toArgb()
        assertTrue(pixels[width - 2, height / 2].toArgb() != ink, "the right edge glows")
        assertEquals(ink, pixels[1, height / 2].toArgb(), "the left edge stays dark")
        assertEquals(ink, pixels[width / 2, 1].toArgb(), "the top edge stays dark")
    }

    @Test
    fun framesBetweenStepsDrawTheInterpolatedCameraAndCore() {
        val base = modelAt(DP_WIDTH, DP_HEIGHT, density = 1f)
        val between = base.with(
            "interpolationAlpha" to 0.25f,
            "previousCoreX" to base.coreX - 40f,
            "previousCameraX" to base.cameraX - 20f,
        )
        assertEquals(base.coreX - 30f, between.renderCoreX)
        assertEquals(base.cameraX - 15f, between.renderCameraX)
        assertEquals(base.elapsed - 0.75f * GameplayRenderModel.FIXED_STEP, between.renderElapsed)
        drawIn(DP_WIDTH, DP_HEIGHT, 1f) {
            assertEquals(DP_WIDTH * 0.5f + 15f, world(between, base.cameraX, 0f, 0f, 0f).x)
        }
        // Alpha 1 (any non-running frame) is exactly the simulated state.
        val exact = between.with("interpolationAlpha" to 1f)
        assertEquals(exact.coreX, exact.renderCoreX)
        assertEquals(exact.cameraX, exact.renderCameraX)
        assertEquals(exact.elapsed, exact.renderElapsed)
    }

    @Test
    fun aDensityTwoWorldAndHudFrameStaysAllocationFree() {
        val base = modelAt(DP_WIDTH * 2f, DP_HEIGHT * 2f, density = 2f)
        val frames = List(FRAMES) { frame ->
            base.with("interpolationAlpha" to (frame % 8) / 8f, "previousCoreX" to base.coreX - 6f, "previousCameraX" to base.cameraX - 4f)
        }
        val measurer = CanvasTextMeasurer(
            ComposeTextMeasurer(createFontFamilyResolver(), Density(2f), LayoutDirection.Ltr, cacheSize = 64),
            1.25f, AppLanguage.English, HudTestFonts.typography,
        )
        val memory = HudPresentationMemory()
        val width = (DP_WIDTH * 2f).toInt()
        val height = (DP_HEIGHT * 2f).toInt()
        val canvas = Canvas(ImageBitmap(width, height))
        val scope = CanvasDrawScope()
        val size = Size(width.toFloat(), height.toFloat())
        fun empty() = scope.draw(Density(2f), LayoutDirection.Ltr, canvas, size) { drawRect(Kk.Ink) }
        fun frame(index: Int) = scope.draw(Density(2f), LayoutDirection.Ltr, canvas, size) {
            drawGameplay(frames[index], VisualFxProjection.EMPTY, measurer, 10f + index * 0.0001f, pauseLayout = null, hudMemory = memory)
        }
        val thread = Thread.currentThread().id
        repeat(4) { repeat(FRAMES) { empty(); frame(it) } }
        var before = threads.getThreadAllocatedBytes(thread)
        repeat(FRAMES) { empty() }
        val emptyBytes = threads.getThreadAllocatedBytes(thread) - before
        before = threads.getThreadAllocatedBytes(thread)
        repeat(FRAMES) { frame(it) }
        val perFrame = (threads.getThreadAllocatedBytes(thread) - before - emptyBytes) / FRAMES
        assertTrue(perFrame <= 96, "A density-2 world + HUD frame allocates $perFrame bytes above an empty frame")
    }

    private fun renderWorld(model: GameplayRenderModel, width: Int, height: Int, density: Float): IntArray {
        val bitmap = ImageBitmap(width, height)
        val measurer = CanvasTextMeasurer(
            ComposeTextMeasurer(createFontFamilyResolver(), Density(density), LayoutDirection.Ltr),
            1.25f, AppLanguage.English, roles = KkRolePalette.Default,
        )
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawRect(Kk.Ink)
            drawBackdrop(model, 0f, 0f, 2f, KkRolePalette.Default)
            drawWorld(model, VisualFxProjection.EMPTY, 0f, 0f, measurer)
        }
        val map = bitmap.toPixelMap()
        return IntArray(width * height) { map[it % width, it / width].toArgb() }
    }

    /** Bounds of exact bone pixels within a window around the screen center (the Core body). */
    private fun boneBounds(pixels: IntArray, width: Int): androidx.compose.ui.geometry.Rect {
        val height = pixels.size / width
        val bone = Kk.Bone.toArgb()
        val window = width / 12
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE
        for (y in height / 2 - window..height / 2 + window) for (x in width / 2 - window..width / 2 + window) {
            if (pixels[y * width + x] != bone) continue
            left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x); bottom = maxOf(bottom, y)
        }
        check(left <= right) { "no Core pixels near the center" }
        return androidx.compose.ui.geometry.Rect(left.toFloat(), top.toFloat(), right + 1f, bottom + 1f)
    }

    private fun <T> drawIn(width: Float, height: Float, density: Float, block: DrawScope.() -> T): T {
        var result: T? = null
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(ImageBitmap(1, 1)), Size(width, height)) {
            result = block()
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun assertClose(expected: Offset, actual: Offset, message: String) {
        assertTrue(abs(expected.x - actual.x) < 0.01f && abs(expected.y - actual.y) < 0.01f, "$message: expected $expected, was $actual")
    }

    /** A started run whose viewport of [widthPx] × [heightPx] at [density] went through the interaction boundary. */
    private fun modelAt(widthPx: Float, heightPx: Float, density: Float): GameplayRenderModel {
        // Resize the started run through the validator, like the host's onSizeChanged does.
        val pulse = assertIs<InteractionValidationResult.Valid<GameplayInteractionPulse.ViewportChanged>>(
            GameInteractionValidator().viewportChanged(widthPx, heightPx, density),
        ).intent
        val state = hudTestState(DP_WIDTH, DP_HEIGHT)
        val resized = assertIs<GameplayDecision.Accepted>(GameplayNucleus.decide(state, GameplayNucleusPulse.Intent(pulse))).frame.nextState
        return requireNotNull(GameplayNucleus.renderSnapshot(resized).renderModel)
    }

    private companion object {
        const val DP_WIDTH = 1_280f
        const val DP_HEIGHT = 800f
        const val FRAMES = 48
    }
}
