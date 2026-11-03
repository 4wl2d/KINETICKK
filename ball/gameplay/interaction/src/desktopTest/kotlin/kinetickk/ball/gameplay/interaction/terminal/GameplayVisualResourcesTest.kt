// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.terminal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.gameplay.interaction.prepareGameplayVisualResources
import kinetickk.foundation.design.Kk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertSame
import kinetickk.ball.gameplay.interaction.canvas.fx.*

class GameplayVisualResourcesTest {
    @Test
    fun preparedReportGeometryIsRetainedAndRendersLikeFreshGeometry() = runBlocking {
        prepareGameplayVisualResources()
        val defeat = reportShatterArt(false)
        val victory = reportShatterArt(true)
        val defeatPaths = defeat.paths
        val victoryPaths = victory.paths
        for (outcome in listOf(false, true)) {
            val prepared = reportShatterArt(outcome)
            val fresh = ReportShatterArt(outcome)
            for (time in listOf(0.1f, 0.5f, 1.2f, 3f)) {
                for (scale in listOf(0.26f, 0.8f)) {
                    assertContentEquals(render(fresh, time, scale), render(prepared, time, scale),
                        "victory=$outcome time=$time scale=$scale")
                }
            }
        }
        // Drawing the same cached paths with different transforms must not mutate their geometry.
        prepareGameplayVisualResources()
        assertSame(defeat, reportShatterArt(false))
        assertSame(victory, reportShatterArt(true))
        assertSame(defeatPaths, reportShatterArt(false).paths)
        assertSame(victoryPaths, reportShatterArt(true).paths)
        assertContentEquals(render(ReportShatterArt(false), 1.2f, 0.8f), render(defeat, 1.2f, 0.8f))
    }

    private fun render(art: ReportShatterArt, time: Float, scale: Float): IntArray {
        val bitmap = ImageBitmap(384, 384)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(384f, 384f)) {
            drawRect(Kk.Ink)
            drawShatterShards(art.shape, art.paths, Offset(192f, 192f), scale, time, Kk.Bone)
        }
        val pixels = bitmap.toPixelMap()
        return IntArray(384 * 384) { pixels[it % 384, it / 384].toArgb() }
    }
}
