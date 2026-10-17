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
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.TotemProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Continuous world animations run on the interpolated time, not on the 120 Hz step time: a frame
 * between two steps turns every rotating part by the elapsed part of the step, so animations stay
 * as smooth as the interpolated positions above 120 frames per second.
 */
class InterpolatedAnimationTimeTest {
    @Test
    fun aFrameBetweenStepsDrawsTheWorldAnimationsAtItsOwnTime() {
        val model = scene()
        val step = GameplayRenderModel.FIXED_STEP
        val halfway = rotations(model.with("interpolationAlpha" to 0.5f))
        // Exactly what a simulated frame at that time draws, for every rotating world part.
        assertEquals(rotations(model.with("interpolationAlpha" to 1f, "elapsed" to ELAPSED - 0.5f * step)), halfway)
        assertEquals(rotations(model.with("interpolationAlpha" to 1f, "elapsed" to ELAPSED - step)),
            rotations(model.with("interpolationAlpha" to 0f)))
    }

    @Test
    fun theArchitectFramesAndTheTotemRingTurnByHalfAStepAtAlphaOneHalf() {
        val model = scene()
        val atStep = rotations(model.with("interpolationAlpha" to 0f))
        val halfway = rotations(model.with("interpolationAlpha" to 0.5f))
        assertEquals(atStep.size, halfway.size, "the same parts are drawn")
        val turns = halfway.zip(atStep) { later, earlier -> later - earlier }
        val half = GameplayRenderModel.FIXED_STEP * 0.5f
        // The Architect's dashed ring, outer and inner frames (40 s, 18 s and 12 s per turn).
        val architect = listOf(-360f / 40f * half, 360f / 18f * half, -360f / 12f * half)
        assertTrue(turns.windowed(3).any { window -> window.indices.all { abs(window[it] - architect[it]) < TOLERANCE } },
            "the Architect frames turn by $architect between the steps: $turns")
        // The totem's dashed ring (24 s per turn).
        val totem = 360f / 24f * half
        assertTrue(turns.any { abs(it - totem) < TOLERANCE }, "the totem ring turns by $totem between the steps: $turns")
    }

    /** A running frame with an Architect and the weapon totem on screen, nothing moving between steps. */
    private fun scene(): GameplayRenderModel {
        val base = hudTestModel(WIDTH.toFloat(), HEIGHT.toFloat())
        val architectX = base.cameraX + 280f
        val architect = EnemyProjection(ARCHITECT_ID, EnemyType.ARCHITECT, architectX, base.cameraY, 0f, 0f, 900f, 1_000f, 74f,
            0f, 0f, 0f, 0f, architectX, base.cameraY, false)
        return base.with(
            "enemies" to immutableListOf(architect),
            "totem" to TotemProjection(base.cameraX - 280f, base.cameraY, 0f),
            "elapsed" to ELAPSED,
            "previousCoreX" to base.coreX, "previousCoreY" to base.coreY,
            "previousCameraX" to base.cameraX, "previousCameraY" to base.cameraY,
            "previousMorningstarX" to base.morningstarX, "previousMorningstarY" to base.morningstarY,
        )
    }

    /** Every rotation the world draw applies, in draw order. */
    private fun rotations(model: GameplayRenderModel): List<Float> {
        val measurer = CanvasTextMeasurer(
            ComposeTextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr),
            1.25f, AppLanguage.English, roles = KkRolePalette.Default,
        )
        val canvas = RotationRecorder(Canvas(ImageBitmap(WIDTH, HEIGHT)))
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(WIDTH.toFloat(), HEIGHT.toFloat())) {
            drawRect(Kk.Ink)
            drawWorld(model, VisualFxProjection.EMPTY, 0f, 0f, measurer)
        }
        check(canvas.degrees.isNotEmpty()) { "the world draw applied no rotation" }
        return canvas.degrees
    }

    private class RotationRecorder(private val delegate: Canvas) : Canvas by delegate {
        val degrees = ArrayList<Float>()

        override fun rotate(degrees: Float) {
            this.degrees += degrees
            delegate.rotate(degrees)
        }
    }

    private companion object {
        const val WIDTH = 1_440
        const val HEIGHT = 810
        const val ELAPSED = 12f
        const val ARCHITECT_ID = 4_101
        const val TOLERANCE = 1e-3f
    }
}
