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
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.interaction.canvas.world.*
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test

/**
 * The dimensions' draw cost, a benchmark and not a test: it returns at once unless `KK_DIMENSION_BENCH=1`, then draws
 * the backdrop and the world of a visit in each layer (two course rings, two ambient rings, twelve embers, the exit
 * rift, the clocks moving) for 600 warm and 2,000 timed frames at 1440 × 810 and prints the mean, median and 95th
 * percentile per frame (`KK_DIMENSION_BENCH_OUT` appends them to a file). It uses only fixtures both sides of a change
 * share, so the same file times a base tree and the change (run Release against Release, A-B-B-A).
 */
class DimensionRenderBenchmarkTest {
    @Test
    fun measureDimensionDraw() {
        if (System.getenv("KK_DIMENSION_BENCH") != "1") return
        val label = System.getenv("KK_DIMENSION_BENCH_LABEL") ?: "run"
        // KK_DIMENSION_BENCH_LAYERS (e.g. "ZENITH,REALSPACE") limits the layers timed.
        val wanted = System.getenv("KK_DIMENSION_BENCH_LAYERS")?.split(',')?.map { it.trim() }?.toSet()
        val lines = listOf(null, WorldLayer.ZENITH, WorldLayer.NADIR, WorldLayer.ARCHIVE)
            .filter { wanted == null || (it?.name ?: "REALSPACE") in wanted }
            .map { layer -> "label=$label ${measure(layer)}" }
        lines.forEach(::println)
        System.getenv("KK_DIMENSION_BENCH_OUT")?.let { path -> File(path).appendText(lines.joinToString("\n", postfix = "\n")) }
    }

    private fun measure(layer: WorldLayer?): String {
        val base = hudTestModel(WIDTH.toFloat(), HEIGHT.toFloat())
        val frames = Array(FRAME_MODELS) { frame -> model(base, layer, frame) }
        val measurer = CanvasTextMeasurer(ComposeTextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1.25f,
            AppLanguage.English)
        val bitmap = ImageBitmap(WIDTH, HEIGHT)
        val canvas = Canvas(bitmap)
        val scope = CanvasDrawScope()
        fun draw(index: Int) {
            val model = frames[index % FRAME_MODELS]
            scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(WIDTH.toFloat(), HEIGHT.toFloat())) {
                drawBackdrop(model, 0f, 0f, index / 60f, measurer.roles)
                drawWorld(model, VisualFxProjection.EMPTY, 0f, 0f, measurer)
            }
        }
        repeat(WARM_FRAMES) { draw(it) }
        val samples = LongArray(TIMED_FRAMES)
        for (index in samples.indices) {
            val start = System.nanoTime()
            draw(index)
            samples[index] = System.nanoTime() - start
        }
        samples.sort()
        return "layer=${layer ?: "REALSPACE"} meanMs=${"%.3f".format(samples.average() / 1e6)} " +
            "p50Ms=${"%.3f".format(samples[samples.size / 2] / 1e6)} p95Ms=${"%.3f".format(samples[(samples.size * 0.95).toInt()] / 1e6)}"
    }

    /** [base] at [frame] inside [layer]'s visit (realspace when null), the camera and the clocks moving. */
    private fun model(base: GameplayRenderModel, layer: WorldLayer?, frame: Int): GameplayRenderModel {
        val moved = base.with("elapsed" to base.elapsed + frame / 60f, "cameraX" to base.coreX + frame * 0.6f,
            "previousCameraX" to base.coreX + frame * 0.6f)
        if (layer == null) return moved
        val embers = List(12) { index -> (150f + index * 15f) * cos(index * 0.5f) to (150f + index * 15f) * sin(index * 0.5f) }
        return moved.withDimension(dimensionVisit(layer, base.coreX, base.coreY, course = listOf(260f to -60f, -240f to 200f),
            ambient = listOf(-380f to -180f, 400f to 230f), embers = embers, exit = 300f to 160f, completed = false,
            localClock = 4f + frame / 60f))
    }

    private companion object {
        const val WIDTH = 1_440
        const val HEIGHT = 810
        const val FRAME_MODELS = 120
        const val WARM_FRAMES = 600
        const val TIMED_FRAMES = 2_000
    }
}
