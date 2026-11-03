// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world.places

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.positiveModulo
import kotlin.math.floor

/*
 * The Archive is a corrupted signal: a blue-black ground lit cyan from one corner and magenta from the other, columns
 * of data falling through the world, scanlines over everything, datamosh blocks tearing the picture and glyph shards
 * adrift (the weather). Its motion runs on the visit's local clock, so the rain, the blocks and the shards freeze when
 * the Core stops, as hostile time does.
 */

/** The corner glows and the data rain under the Archive's grid. */
internal fun DrawScope.drawArchiveSignal(engine: GameplayRenderModel, theme: DimensionTheme, shakeX: Float, shakeY: Float) {
    val w = size.width
    val h = size.height
    with(ScreenGlows) {
        drawScreenGlow(ScreenGlow.ARCHIVE_SIGNAL, theme.glow.copy(alpha = 0.18f), 0f, 0f, h * 0.75f)
        drawScreenGlow(ScreenGlow.ARCHIVE_GLITCH, theme.glowAlt.copy(alpha = 0.15f), w, h, h * 0.75f)
    }
    drawDataRain(engine, theme, shakeX, shakeY)
}

/** Data rain: column width, the share of columns that carry data, the cell height a drop falls through and its speed. */
private const val RAIN_COLUMN = 26f
private const val RAIN_SHARE = 0.42f
private const val RAIN_CELL = 560f
private const val RAIN_FALL = 110f
private const val RAIN_FALL_SPREAD = 120f
private const val RAIN_SALT = 53
/** Glyphs lit behind each head, this far apart. */
private const val RAIN_GLYPHS = 3
private const val RAIN_GLYPH_PITCH = 11f

/**
 * Columns of data falling through the world on the local clock: a share of the world's columns carry one drop per cell
 * of height, a fading trail with a bright head; about one column in five runs magenta.
 */
private fun DrawScope.drawDataRain(engine: GameplayRenderModel, theme: DimensionTheme, shakeX: Float, shakeY: Float) {
    val originX = worldOriginX(engine, shakeX)
    val originY = worldOriginY(engine, shakeY)
    val time = engine.dimension.localClock
    val firstX = floor(-originX / RAIN_COLUMN).toInt()
    val lastX = floor((size.width - originX) / RAIN_COLUMN).toInt()
    val firstY = floor(-originY / RAIN_CELL).toInt() - 1
    val lastY = floor((size.height - originY) / RAIN_CELL).toInt()
    for (column in firstX..lastX) {
        val columnHash = dimensionCellHash(column, 0, RAIN_SALT)
        if (cellUnit(columnHash, 0) >= RAIN_SHARE) continue
        val color = if (cellUnit(columnHash, 1) < 0.2f) theme.glowAlt else theme.rain
        val trail = ArchiveResources.rainTrail(color)
        val x = originX + column * RAIN_COLUMN + RAIN_COLUMN * 0.5f
        val fall = RAIN_FALL + RAIN_FALL_SPREAD * cellUnit(columnHash, 2)
        val length = 90f + 140f * cellUnit(columnHash, 3)
        for (row in firstY..lastY) {
            val hash = dimensionCellHash(column, row, RAIN_SALT + 1)
            val head = originY + row * RAIN_CELL + positiveModulo(cellUnit(hash, 0) * RAIN_CELL + fall * time, RAIN_CELL)
            if (head < -4f || head - length > size.height) continue
            translate(x, head - length) {
                scale(1.6f, length, Offset.Zero) { drawRect(trail, Offset(-0.5f, 0f), Size(1f, 1f)) }
            }
            // The glyphs nearest the head, flickering on their own steps of the local clock, then the bright head.
            val flicker = floor(time * 6f).toInt()
            for (glyph in 1..RAIN_GLYPHS) {
                val cell = dimensionCellHash(column, row * 16 + glyph + flicker, RAIN_SALT + 2)
                if (cellUnit(cell, 0) < 0.35f) continue
                val glyphWidth = 3f + 4f * cellUnit(cell, 1)
                drawRect(color.copy(alpha = 0.75f - 0.18f * glyph), Offset(x - glyphWidth * 0.5f, head - glyph * RAIN_GLYPH_PITCH - 4f),
                    Size(glyphWidth, 5f))
            }
            drawRect(theme.highlight.copy(alpha = 0.9f), Offset(x - 2.5f, head - 5f), Size(5f, 6f))
        }
    }
}

/** The Archive's scanlines over the whole screen (one batched draw of thin lines) and its brighter band on the local clock. */
internal fun DrawScope.drawArchiveScanlines(engine: GameplayRenderModel, theme: DimensionTheme) {
    val paint = ArchiveResources.scanlinePaint
    paint.color = theme.scanline.copy(alpha = 0.07f)
    drawContext.canvas.drawRawPoints(PointMode.Lines, ArchiveResources.scanlines(size.width, size.height), paint)
    val top = positiveModulo(engine.dimension.localClock * SCANLINE_SPEED, size.height + SCANLINE_HEIGHT) - SCANLINE_HEIGHT
    drawRect(theme.scanline.copy(alpha = 0.05f), Offset(0f, top), Size(size.width, SCANLINE_HEIGHT))
}

/** The Archive's scanline band: its height and how fast the local clock moves it down the screen. */
private const val SCANLINE_HEIGHT = 24f
private const val SCANLINE_SPEED = 36f

/** Datamosh: blocks per step, the local seconds a step holds and the share of steps that tear. */
private const val MOSH_BLOCKS = 7
private const val MOSH_STEP_SECONDS = 0.18f
private const val MOSH_SHARE = 0.7f
private const val MOSH_SALT = 59

/** Glyph shard cells: their side, the share that holds a shard and its spin on the local clock (degrees per second). */
private const val SHARD_CELL = 230f
private const val SHARD_SHARE = 0.32f
private const val SHARD_SPIN = 9f
private const val SHARD_SALT = 61

/**
 * The Archive's weather: glyph shards adrift in the world (a shard in a share of the cells times [density], split into
 * its magenta, cyan and bone copies; a [crowded] frame keeps every other cell) and the datamosh tearing the picture for
 * a step of the local clock at a time. Three draws per shard and per block from the atmosphere's budget.
 */
internal fun DrawScope.drawArchiveShards(
    engine: GameplayRenderModel,
    theme: DimensionTheme,
    shakeX: Float,
    shakeY: Float,
    density: Float,
    crowded: Boolean,
) {
    val originX = worldOriginX(engine, shakeX)
    val originY = worldOriginY(engine, shakeY)
    val time = engine.dimension.localClock
    val firstX = floor((-originX) / SHARD_CELL).toInt() - 1
    val lastX = floor((size.width - originX) / SHARD_CELL).toInt()
    val firstY = floor((-originY) / SHARD_CELL).toInt() - 1
    val lastY = floor((size.height - originY) / SHARD_CELL).toInt()
    val split = ArchiveResources.SHARD_SPLIT
    for (cy in firstY..lastY) for (cx in firstX..lastX) {
        if (crowded && (cx + cy) and 1 != 0) continue
        val hash = dimensionCellHash(cx, cy, SHARD_SALT)
        if (cellUnit(hash, 0) >= SHARD_SHARE * density) continue
        val x = originX + (cx + 0.15f + 0.7f * cellUnit(hash, 1)) * SHARD_CELL
        val y = originY + (cy + 0.15f + 0.7f * cellUnit(hash, 2)) * SHARD_CELL
        if (!inView(x, y, 24f) || !atmosphereAffords(3)) continue
        val shard = ArchiveResources.shard((cellUnit(hash, 3) * ArchiveResources.SHARDS).toInt().coerceAtMost(ArchiveResources.SHARDS - 1))
        val side = 10f + 10f * cellUnit(hash, 4)
        val turn = 360f * cellUnit(hash, 5) + time * SHARD_SPIN * (if (hash and 1 == 0) 1f else -1f)
        translate(x, y) {
            rotate(turn, Offset.Zero) {
                scale(side, side, Offset.Zero) {
                    translate(-split, 0f) { drawPath(shard, theme.glowAlt, alpha = 0.5f, style = ArchiveResources.shardStroke) }
                    translate(split, 0f) { drawPath(shard, theme.glow, alpha = 0.5f, style = ArchiveResources.shardStroke) }
                    drawPath(shard, theme.highlight, alpha = 0.7f, style = ArchiveResources.shardStroke)
                }
            }
        }
    }
    drawDatamosh(theme, time)
}

/** Blocks of the picture torn sideways for one step of the local clock: a cyan and a magenta copy and a bone tear line. */
private fun DrawScope.drawDatamosh(theme: DimensionTheme, time: Float) {
    val step = floor(time / MOSH_STEP_SECONDS).toInt()
    if (cellUnit(dimensionCellHash(step, 0, MOSH_SALT), 0) >= MOSH_SHARE) return
    for (index in 0 until MOSH_BLOCKS) {
        if (!atmosphereAffords(3)) return
        val hash = dimensionCellHash(step, index + 1, MOSH_SALT)
        val width = 50f + 170f * cellUnit(hash, 0)
        val height = 4f + 22f * cellUnit(hash, 1)
        val x = cellUnit(hash, 2) * (size.width - width)
        val y = cellUnit(hash, 3) * (size.height - height)
        val shift = 5f + 14f * cellUnit(hash, 4)
        drawRect(theme.glow.copy(alpha = 0.09f), Offset(x + shift, y), Size(width, height))
        drawRect(theme.glowAlt.copy(alpha = 0.09f), Offset(x - shift, y + height * 0.3f), Size(width, height))
        drawLine(theme.highlight.copy(alpha = 0.16f), Offset(x, y), Offset(x + width, y), 1f)
    }
}

/** Cached resources of the Archive: the rain's fading trail per colour, the scanline brush and the shards' shapes. */
internal object ArchiveResources {
    const val SHARDS = 3
    /** How far a shard's magenta and cyan copies stand off, in its unit space. */
    const val SHARD_SPLIT = 0.09f
    private val shards = arrayOfNulls<Path>(SHARDS)
    // Keys as raw colour values: an array of Color would box each colour it compares.
    private val trailKeys = LongArray(2)
    private val trails = arrayOfNulls<Brush>(2)
    private var nextTrail = 0
    private var scanlineWidth = Float.NaN
    private var scanlineHeight = Float.NaN
    private var scanlineEnds = FloatArray(0)

    /** The scanlines' one-pixel stroke (its colour set per frame from the theme). */
    val scanlinePaint = Paint().apply {
        style = PaintingStyle.Stroke
        strokeWidth = 1f
    }

    /** A shard's line in its unit space: about 1.3 px on a 14 u shard. */
    val shardStroke: Stroke by lazy { Stroke(0.09f) }

    /** A unit trail fading in from its top to [color] at its foot (two colours kept: the rain's cyan and magenta). */
    fun rainTrail(color: Color): Brush {
        val key = color.value.toLong()
        for (index in trails.indices) if (trails[index] != null && trailKeys[index] == key) return trails[index]!!
        val brush = Brush.verticalGradient(0f to color.copy(alpha = 0f), 0.7f to color.copy(alpha = 0.35f), 1f to color.copy(alpha = 0.8f),
            startY = 0f, endY = 1f)
        trailKeys[nextTrail] = key
        trails[nextTrail] = brush
        nextTrail = (nextTrail + 1) % trails.size
        return brush
    }

    /** The ends of one line every [SCANLINE_PERIOD] rows across a [width] × [height] screen; rebuilt only on a resize. */
    fun scanlines(width: Float, height: Float): FloatArray {
        if (width == scanlineWidth && height == scanlineHeight) return scanlineEnds
        scanlineWidth = width
        scanlineHeight = height
        val lines = (height / SCANLINE_PERIOD).toInt() + 1
        scanlineEnds = FloatArray(lines * 4) { index ->
            val y = (index / 4) * SCANLINE_PERIOD + 0.5f
            when (index % 4) {
                0 -> 0f
                2 -> width
                else -> y
            }
        }
        return scanlineEnds
    }

    /** Shard [variant]: an angular glyph fragment (a broken bracket, a split chevron, a cut frame) in unit space. */
    fun shard(variant: Int): Path = shards[variant] ?: Path().also { path ->
        val points = ShardLines[variant]
        path.moveTo(points[0], points[1])
        for (point in 2 until points.size step 2) path.lineTo(points[point], points[point + 1])
        shards[variant] = path
    }

    private const val SCANLINE_PERIOD = 4f
}

/** The shards' polylines (x, y pairs in unit space). */
private val ShardLines: List<FloatArray> = listOf(
    floatArrayOf(-0.3f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.3f, 0.5f),
    floatArrayOf(-0.45f, -0.35f, 0f, 0.1f, 0.45f, -0.35f, 0.2f, 0.45f),
    floatArrayOf(-0.5f, -0.2f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.1f, 0.1f, 0.5f),
)

/** The Archive's cached shapes never depend on the world, the viewport or the preferences. */
internal fun prepareArchiveResources() {
    ArchiveResources.shardStroke
    repeat(ArchiveResources.SHARDS) { ArchiveResources.shard(it) }
}
