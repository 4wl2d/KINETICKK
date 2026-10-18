// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.lang.management.ManagementFactory
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** A rarity material away from a card (a tile, a legend swatch, a tag band) keeps the card's ladder and draws without allocating. */
class KkRarityTileTest {
    private val ladder = KkCardMaterial.entries.filter { it != KkCardMaterial.CALM }

    @Test
    fun theTileLadderRisesFromIronToGoldAsEachPresetIsSeen() {
        // Each step dresses the tile more, as each preset is seen (lightness alone in Mono), on a grid
        // tile and on a legend swatch: its pixels stand a tenth or more further from the ink they lie on
        // (the tile's face, the screen round it), or it changes a tenth or more of the tile more (the
        // legendary rays spread over the face where the epic rim is brighter). The pixel count holds with
        // [ROUNDING] to spare: the higher step counts only pixels that clear the bar by that much, the lower
        // one every pixel within that much of it, so a faint fringe sitting on the bar (which one rasterizer
        // draws a level brighter than another) cannot decide a step.
        val failures = ArrayList<String>()
        for (mode in KkVisionMode.entries) for (side in listOf(84, 26)) {
            val palette = mode.palette
            val looks = ladder.map { material ->
                // The tile's ink face with room round it for its halo.
                val tile = kkRender(side + 8, side + 8) {
                    val bounds = Rect(4f, 4f, side + 4f, side + 4f)
                    drawRect(Kk.Ink2, bounds.topLeft, bounds.size)
                    drawKkRarityTile(material.frame(palette.gold, palette.spectrum), bounds)
                }
                look(tile.argb(), mode.vision(), side)
            }
            for (index in 1 until looks.size) {
                val (salience, coverage) = looks[index]
                val (lower, _, lowerCoverage) = looks[index - 1]
                if (salience <= lower * 1.1 && coverage <= lowerCoverage * 1.1) {
                    failures += "$mode ${side}dp: ${ladder[index]} ($salience, $coverage px) under ${ladder[index - 1]} ($lower, $lowerCoverage px) + 10 %"
                }
            }
        }
        assertTrue(failures.isEmpty(), "the tile ladder does not rise:\n" + failures.joinToString("\n"))
    }

    @Test
    fun aWallOfTilesAndBandsAllocatesNothingPerFrame() {
        // Every material in one preset's gold and spectrum (a screen shows one preset): grid tiles
        // (moving), legend swatches (still) and tag bands (moving) in one draw scope, and swatches
        // filling a draw scope of their own (the default bounds a Codex cell draws with). Each scope
        // keeps one size, as a Compose node's does: a brush's shader is sized by its draw scope.
        for (mode in KkVisionMode.entries) {
            val palette = mode.palette
            val frames = KkCardMaterial.entries.map { it.frame(palette.gold, palette.spectrum) }
            val cell = Size(120f, 140f)
            val tile = Rect(4f, 4f, 88f, 88f)
            val swatch = Rect(94f, 4f, 120f, 30f)
            val band = Rect(0f, 112f, 110f, 138f)
            val wall = bytesPerFrame(Size(cell.width * frames.size, cell.height)) { index ->
                val time = index / 60f
                for (i in frames.indices) {
                    translate(i * cell.width, 0f) {
                        drawKkRarityTile(frames[i], tile, time, seed = i)
                        drawKkRarityTile(frames[i], swatch)
                        drawKkRarityBand(frames[i], band, time = time)
                    }
                }
            }
            assertTrue(wall <= 64, "$mode: every material as tiles and bands allocates $wall bytes per frame")
            val cells = bytesPerFrame(Size(26f, 26f)) {
                for (frame in frames) {
                    drawKkRarityTile(frame)
                    kkRarityTileInset(frame.material)
                }
            }
            assertTrue(cells <= 64, "$mode: every material filling its draw scope allocates $cells bytes per frame")
        }
    }

    /** Heap bytes one frame of [draw] (given the frame index) allocates on a canvas of [size] once warm, above an empty frame's. */
    private fun bytesPerFrame(size: Size, draw: DrawScope.(Int) -> Unit): Long {
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val canvas = Canvas(ImageBitmap(size.width.toInt(), size.height.toInt()))
        val scope = CanvasDrawScope()
        fun frame(index: Int) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) { draw(index) }
        fun empty() = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) { drawRect(Kk.Ink) }
        repeat(400) { frame(it); empty() }
        val id = Thread.currentThread().id
        val emptyStart = threads.getThreadAllocatedBytes(id)
        repeat(400) { empty() }
        val emptyBytes = threads.getThreadAllocatedBytes(id) - emptyStart
        val start = threads.getThreadAllocatedBytes(id)
        repeat(400) { frame(it) }
        return (threads.getThreadAllocatedBytes(id) - start - emptyBytes) / 400
    }

    /** A tile's [salience] and how many pixels step past the bar, by at least [ROUNDING] ([covered]) or within it ([reached]). */
    private data class Look(val salience: Int, val covered: Int, val reached: Int)

    /**
     * How the tile in [argb] stands from the ink each pixel lies on (the tile's face inside, the
     * screen's ink round it) as [vision] sees it: Σ of the lightness step plus the chroma step (lightness
     * alone when only lightness is seen), and how many pixels step more than [COVERED] from it, give or
     * take [ROUNDING].
     */
    private fun look(argb: IntArray, vision: Vision, side: Int): Look {
        val face = lab(Kk.Ink2, vision)
        val ink = lab(Kk.Ink, vision)
        var total = 0.0
        var covered = 0
        var reached = 0
        val width = side + 8
        for (index in argb.indices) {
            val x = index % width
            val y = index / width
            val under = if (x in 4 until side + 4 && y in 4 until side + 4) face else ink
            val seen = lab(Color(argb[index]), vision)
            val step = abs(seen.l - under.l) + if (vision == Vision.LUMINANCE) 0.0 else abs(seen.chroma - under.chroma)
            total += step
            if (step > COVERED + ROUNDING) covered++
            if (step > COVERED - ROUNDING) reached++
        }
        return Look(total.toInt(), covered, reached)
    }

    private companion object {
        /** A pixel this many CIELAB units from the ink it lies on is dressed by the frame. */
        const val COVERED = 3.0

        /** How far (CIELAB units) one rasterizer's faint strokes may land from another's: about two 8-bit levels on the ink. */
        const val ROUNDING = 1.0
    }
}
