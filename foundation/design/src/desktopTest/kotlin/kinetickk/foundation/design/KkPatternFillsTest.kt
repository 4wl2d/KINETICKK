// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The seven pattern fills that let hues be told apart without color: each covers a readable share
 * of its area, repeats seamlessly, differs from the others and from the hazard hatch, and draws
 * without allocating once its tile exists.
 */
class KkPatternFillsTest {
    private val patterns = listOf(
        KkFillKind.SHIMMER, KkFillKind.LATTICE, KkFillKind.ZIGZAG, KkFillKind.RINGS, KkFillKind.PITTING, KkFillKind.BARS,
        KkFillKind.CROSSES,
    )
    private val area = Rect(0f, 0f, 216f, 144f)

    private fun render(kind: KkFillKind, density: Float = 1f): ImageBitmap =
        kkRender(216, 144, density) { drawKkPattern(area, kind, Color.White) }

    @Test
    fun everyPatternCoversAReadableShareOfItsArea() {
        for (kind in patterns) for (density in listOf(1f, 2f)) {
            val lit = render(kind, density).argb().count { colorDistance(it, Kk.Ink.toArgb()) > 60 }
            val share = lit / (216f * 144f)
            assertTrue(share in 0.05f..0.5f, "$kind at density $density covers ${share * 100} % of its area")
        }
    }

    @Test
    fun patternsRepeatSeamlesslyAcrossTheirTiles() {
        for (kind in patterns) {
            val image = render(kind)
            val (tileWidth, tileHeight) = tileSize(kind)
            for (y in listOf(20, 61, 97)) {
                val row = (0 until 150).map { image.at(it, y) }
                val shifted = (tileWidth until tileWidth + 150).map { image.at(it, y) }
                assertTrue(row == shifted, "$kind repeats every $tileWidth px along row $y")
            }
            for (x in listOf(13, 50, 111)) {
                val column = (0 until 60).map { image.at(x, it) }
                val shifted = (tileHeight until tileHeight + 60).map { image.at(x, it) }
                assertTrue(column == shifted, "$kind repeats every $tileHeight px down column $x")
            }
        }
    }

    @Test
    fun patternsDifferFromEachOtherAndFromTheHatch() {
        val images = (patterns + KkFillKind.HATCH).associateWith { kind -> render(kind).argb().map { colorDistance(it, Kk.Ink.toArgb()) > 60 } }
        val kinds = images.keys.toList()
        for (a in kinds.indices) for (b in a + 1 until kinds.size) {
            val first = images.getValue(kinds[a])
            val second = images.getValue(kinds[b])
            val different = first.indices.count { first[it] != second[it] }
            assertTrue(different > first.size / 10, "${kinds[a]} and ${kinds[b]} differ in only $different pixels")
        }
    }

    @Test
    fun patternsFollowTheShapeTheyFillAndTurnWithIt() {
        val path = Path().apply {
            moveTo(40f, 10f)
            lineTo(130f, 30f)
            lineTo(90f, 120f)
            close()
        }
        for (kind in patterns) {
            val image = kkRender(216, 144) { rotate(17f, Offset(80f, 60f)) { drawKkPattern(path, kind, Color.White, alpha = 0.8f) } }
            val outside = image.regionCountNear(Color.White, 160, 0, 216, 144, tolerance = 120)
            assertTrue(outside == 0, "$kind stays inside its shape ($outside pixels outside)")
            assertTrue(image.regionCountNear(Kk.Ink, 60, 40, 100, 80, tolerance = 30) < 1_600, "$kind fills its shape")
        }
    }

    @Test
    fun drawingWarmPatternsAllocatesNothing() {
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val bitmap = ImageBitmap(216, 144)
        val canvas = Canvas(bitmap)
        val scope = CanvasDrawScope()
        val path = Path().apply {
            moveTo(20f, 10f)
            lineTo(120f, 20f)
            lineTo(70f, 120f)
            close()
        }
        val colors = listOf(Kk.EThermal, Kk.ECryo, Kk.EIon, Kk.EGravitic, Kk.EEntropy, Kk.ESonic, Kk.EVoid)
        fun frame(index: Int) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(216f, 144f)) {
            for (kind in patterns.indices) {
                rotate(index * 0.7f + kind * 30f, Offset(70f, 60f)) {
                    drawKkPattern(path, patterns[kind], colors[kind], alpha = 0.25f + (index % 10) * 0.05f)
                }
            }
        }
        fun empty() = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(216f, 144f)) { drawRect(Kk.Ink) }
        repeat(50) { frame(it); empty() }
        val id = Thread.currentThread().id
        val emptyStart = threads.getThreadAllocatedBytes(id)
        repeat(400) { empty() }
        val emptyBytes = threads.getThreadAllocatedBytes(id) - emptyStart
        val start = threads.getThreadAllocatedBytes(id)
        repeat(400) { frame(it) }
        val perFrame = (threads.getThreadAllocatedBytes(id) - start - emptyBytes) / 400
        assertTrue(perFrame <= 32, "pattern frame allocates $perFrame bytes")
    }

    /** Tile size in px at density 1, as the cache builds it. */
    private fun tileSize(kind: KkFillKind): Pair<Int, Int> = when (kind) {
        KkFillKind.SHIMMER -> 12 to 6
        KkFillKind.LATTICE -> 12 to Math.round(12f * kotlin.math.sqrt(3f))
        KkFillKind.ZIGZAG -> 7 to 9
        else -> Math.round(kind.periodDp).let { it to it }
    }
}
