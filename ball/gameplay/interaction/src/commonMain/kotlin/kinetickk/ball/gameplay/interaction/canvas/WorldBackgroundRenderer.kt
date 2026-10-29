// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.foundation.design.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel

/** World grid pitch in world units (`bg-grid`; the world spec asks for 40 px). */
internal const val WORLD_GRID_SPACING = 40f

/** Diameter of the halftone disc that sits under the Core (`HUD.dc.html`: 700 px). */
private const val CORE_HALFTONE_DIAMETER = 700f

private val CoreHalftoneRect = Rect(
    -CORE_HALFTONE_DIAMETER * 0.5f,
    -CORE_HALFTONE_DIAMETER * 0.5f,
    CORE_HALFTONE_DIAMETER * 0.5f,
    CORE_HALFTONE_DIAMETER * 0.5f,
)

/**
 * Ink ground with a world-anchored 40-unit grid and a faint halftone disc around the Core, drawn
 * in world units ([withWorldUnits]); [shakeX]/[shakeY] are device pixels. The run's rebirth tier
 * tints the grid and halftone only; a Baby mode run's halftone is pastel mint. [roles] is the Color
 * vision palette.
 */
internal fun DrawScope.drawBackdrop(
    engine: GameplayRenderModel,
    shakeX: Float,
    shakeY: Float,
    @Suppress("UNUSED_PARAMETER") renderTime: Float,
    roles: KkRolePalette,
) {
    val scale = engine.worldScale
    withWorldUnits(scale) { drawBackdropLayer(engine, shakeX / scale, shakeY / scale, roles) }
}

private fun DrawScope.drawBackdropLayer(engine: GameplayRenderModel, shakeX: Float, shakeY: Float, roles: KkRolePalette) {
    // A dimension draws its own ground instead of the realspace grid.
    if (engine.dimension.layer != WorldLayer.BASE) {
        drawDimensionBackdrop(engine, shakeX, shakeY, roles)
        return
    }
    val theme = arenaTheme(engine.rebirthLevel)
    val origin = Offset(
        worldGridOffset(engine.renderCameraX, size.width, shakeX),
        worldGridOffset(engine.renderCameraY, size.height, shakeY),
    )
    drawWorldGrid(origin, theme.grid)
    val core = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
    val halftone = if (engine.easyMode) easyPastelMint(roles).copy(alpha = 0.08f) else theme.halftone
    // Dots are anchored to the Core, like the board's halftone element, and fade out radially.
    translate(core.x, core.y) {
        drawKkRadialHalftone(CoreHalftoneRect, halftone)
    }
}

/** Screen offset (0 until the spacing) of the first grid line for a camera coordinate. */
internal fun worldGridOffset(camera: Float, viewport: Float, shake: Float): Float =
    positiveModulo(viewport * 0.5f - camera + shake, WORLD_GRID_SPACING)

/** Batches the same line primitive while preserving order, subpixel coordinates and edge coverage. */
internal fun DrawScope.drawWorldGrid(origin: Offset, color: Color) {
    WorldGridLines.resize(size.width, size.height)
    val paint = WorldGridLines.paint
    paint.color = color
    drawContext.canvas.drawRawPoints(PointMode.Lines, WorldGridLines.vertical(origin.x), paint)
    drawContext.canvas.drawRawPoints(PointMode.Lines, WorldGridLines.horizontal(origin.y), paint)
}

/** Two possible line counts per axis; buffers are replaced only on viewport resize. Draw-thread confined. */
private object WorldGridLines {
    val paint = Paint().apply {
        style = PaintingStyle.Stroke
        strokeWidth = 1f
    }
    private var width = Float.NaN
    private var height = Float.NaN
    private var verticalShort = FloatArray(0)
    private var verticalLong = FloatArray(0)
    private var horizontalShort = FloatArray(0)
    private var horizontalLong = FloatArray(0)

    fun resize(width: Float, height: Float) {
        if (this.width == width && this.height == height) return
        this.width = width
        this.height = height
        val columns = (width / WORLD_GRID_SPACING).toInt()
        val rows = (height / WORLD_GRID_SPACING).toInt()
        verticalShort = FloatArray(columns * 4)
        verticalLong = FloatArray((columns + 1) * 4)
        horizontalShort = FloatArray(rows * 4)
        horizontalLong = FloatArray((rows + 1) * 4)
    }

    fun vertical(origin: Float): FloatArray {
        var x = origin - WORLD_GRID_SPACING
        var index = 0
        while (x <= width) {
            if (x >= 0f) {
                verticalLong[index++] = x
                verticalLong[index++] = 0f
                verticalLong[index++] = x
                verticalLong[index++] = height
            }
            x += WORLD_GRID_SPACING
        }
        if (index == verticalLong.size) return verticalLong
        verticalLong.copyInto(verticalShort, endIndex = index)
        return verticalShort
    }

    fun horizontal(origin: Float): FloatArray {
        var y = origin - WORLD_GRID_SPACING
        var index = 0
        while (y <= height) {
            if (y >= 0f) {
                horizontalLong[index++] = 0f
                horizontalLong[index++] = y
                horizontalLong[index++] = width
                horizontalLong[index++] = y
            }
            y += WORLD_GRID_SPACING
        }
        if (index == horizontalLong.size) return horizontalLong
        horizontalLong.copyInto(horizontalShort, endIndex = index)
        return horizontalShort
    }
}
