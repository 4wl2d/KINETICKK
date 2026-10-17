// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

import kinetickk.ball.gameplay.nucleus.render.ObstacleKind

/**
 * One static world obstacle in run-local coordinates. [id] packs its absolute chunk and its slot in the
 * chunk, so it survives rebases and regenerating the window. Immutable; replaced on change.
 */
internal data class Obstacle(
    val id: Long,
    val kind: ObstacleKind,
    val x: Float,
    val y: Float,
    val radius: Float,
    val shapeSeed: Int,
    /** Bumpers only: run time before which a bounce fires no further ricochet. */
    val ricochetReadyAt: Float = 0f,
)

/**
 * The generated obstacle window: [span] × [span] chunks centred on the absolute chunk
 * ([centerChunkX], [centerChunkY]). [obstacles] are grouped by window cell (row-major from the
 * lowest chunk), and [cellStart] gives each cell's first index, so a lookup walks only the cells a
 * query touches. Immutable value: changes build a new field, so reduction siblings may share it.
 */
internal class ObstacleField private constructor(
    val centerChunkX: Int,
    val centerChunkY: Int,
    val span: Int,
    val obstacles: List<Obstacle>,
    private val cellStart: IntArray,
) {
    val size: Int get() = obstacles.size
    val minimumChunkX: Int get() = centerChunkX - span / 2
    val minimumChunkY: Int get() = centerChunkY - span / 2

    /** First obstacle index of window cell [cell]; [cellEnd] is exclusive. */
    fun cellBegin(cell: Int): Int = cellStart[cell]
    fun cellEnd(cell: Int): Int = cellStart[cell + 1]

    /** Obstacles of the absolute chunk, or null when it is outside the window. */
    fun chunkObstacles(chunkX: Int, chunkY: Int): List<Obstacle>? {
        val cell = cellOf(chunkX, chunkY)
        if (cell < 0) return null
        return obstacles.subList(cellStart[cell], cellStart[cell + 1])
    }

    /** Window cell of an absolute chunk, or -1 outside the window. */
    fun cellOf(chunkX: Int, chunkY: Int): Int {
        val column = chunkX - minimumChunkX
        val row = chunkY - minimumChunkY
        if (column !in 0 until span || row !in 0 until span) return -1
        return row * span + column
    }

    /** The same window with obstacle [index] replaced (positions and cells are unchanged). */
    fun replaced(index: Int, obstacle: Obstacle): ObstacleField {
        val next = ArrayList(obstacles)
        next[index] = obstacle
        return ObstacleField(centerChunkX, centerChunkY, span, next, cellStart)
    }

    /** The same window without obstacle [index]. */
    fun removed(index: Int): ObstacleField {
        val next = ArrayList(obstacles)
        next.removeAt(index)
        val starts = cellStart.copyOf()
        for (cell in starts.indices) if (starts[cell] > index) starts[cell]--
        return ObstacleField(centerChunkX, centerChunkY, span, next, starts)
    }

    /** Every obstacle moved by (−[shiftX], −[shiftY]); the absolute window is unchanged. */
    fun shifted(shiftX: Float, shiftY: Float): ObstacleField {
        if (obstacles.isEmpty()) return this
        val next = ArrayList<Obstacle>(obstacles.size)
        for (index in obstacles.indices) {
            val obstacle = obstacles[index]
            next += obstacle.copy(x = obstacle.x - shiftX, y = obstacle.y - shiftY)
        }
        return ObstacleField(centerChunkX, centerChunkY, span, next, cellStart)
    }

    companion object {
        /** No window yet: the first query after a run starts generates one. */
        val EMPTY: ObstacleField = ObstacleField(Int.MIN_VALUE, Int.MIN_VALUE, 0, emptyList(), IntArray(1))

        /** Builds a window from per-cell obstacle lists in row-major cell order. */
        fun of(centerChunkX: Int, centerChunkY: Int, span: Int, cells: List<List<Obstacle>>): ObstacleField {
            require(cells.size == span * span) { "An obstacle window needs one list per cell" }
            val starts = IntArray(cells.size + 1)
            val all = ArrayList<Obstacle>(cells.sumOf { it.size })
            for (cell in cells.indices) {
                starts[cell] = all.size
                all.addAll(cells[cell])
            }
            starts[cells.size] = all.size
            return ObstacleField(centerChunkX, centerChunkY, span, all, starts)
        }
    }
}
