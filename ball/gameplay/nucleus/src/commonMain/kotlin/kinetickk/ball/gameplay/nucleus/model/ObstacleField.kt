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
    /** Bumpers: next ricochet time. Accelerator gates: next boost time. Thermal vents: end of the boosting eruption. */
    val ricochetReadyAt: Float = 0f,
    /** Derelict mines: run time the armed cluster detonates; 0 while dormant. */
    val fuseAt: Float = 0f,
    /** Gravity knots: true pushes (Repulsor), false pulls (Mass Knot). */
    val repels: Boolean = false,
)

/**
 * The generated obstacle window: [span] × [span] chunks centred on the absolute chunk
 * ([centerChunkX], [centerChunkY]). [obstacles] are grouped by window cell (row-major from the
 * lowest chunk), and [cellStart] gives each cell's first index, so a lookup walks only the cells a
 * query touches. [fieldCount] and [fieldIndex] list the obstacles whose kind has an area effect
 * ([ObstacleKind.field]) in window order, so the per-step field passes skip the plain bodies.
 * Immutable value: changes build a new field, so reduction siblings may share it.
 */
internal class ObstacleField private constructor(
    val centerChunkX: Int,
    val centerChunkY: Int,
    val span: Int,
    val obstacles: List<Obstacle>,
    private val cellStart: IntArray,
    private val fieldIndices: IntArray,
) {
    val size: Int get() = obstacles.size
    val minimumChunkX: Int get() = centerChunkX - span / 2
    val minimumChunkY: Int get() = centerChunkY - span / 2
    val fieldCount: Int get() = fieldIndices.size

    /** Window index of the [n]th field obstacle; ascending in [n]. */
    fun fieldIndex(n: Int): Int = fieldIndices[n]

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
        val fields = if (obstacles[index].kind.field == obstacle.kind.field) fieldIndices else fieldIndicesOf(next)
        return ObstacleField(centerChunkX, centerChunkY, span, next, cellStart, fields)
    }

    /** The same window without obstacle [index]. */
    fun removed(index: Int): ObstacleField {
        val next = ArrayList(obstacles)
        next.removeAt(index)
        val starts = cellStart.copyOf()
        for (cell in starts.indices) if (starts[cell] > index) starts[cell]--
        return ObstacleField(centerChunkX, centerChunkY, span, next, starts, fieldIndicesOf(next))
    }

    /** Every obstacle moved by (−[shiftX], −[shiftY]); the absolute window is unchanged. */
    fun shifted(shiftX: Float, shiftY: Float): ObstacleField {
        if (obstacles.isEmpty()) return this
        val next = ArrayList<Obstacle>(obstacles.size)
        for (index in obstacles.indices) {
            val obstacle = obstacles[index]
            next += obstacle.copy(x = obstacle.x - shiftX, y = obstacle.y - shiftY)
        }
        return ObstacleField(centerChunkX, centerChunkY, span, next, cellStart, fieldIndices)
    }

    companion object {
        /** No window yet: the first query after a run starts generates one. */
        val EMPTY: ObstacleField = ObstacleField(Int.MIN_VALUE, Int.MIN_VALUE, 0, emptyList(), IntArray(1), IntArray(0))

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
            return ObstacleField(centerChunkX, centerChunkY, span, all, starts, fieldIndicesOf(all))
        }

        private fun fieldIndicesOf(obstacles: List<Obstacle>): IntArray {
            var count = 0
            for (index in obstacles.indices) if (obstacles[index].kind.field) count++
            val indices = IntArray(count)
            var next = 0
            for (index in obstacles.indices) if (obstacles[index].kind.field) indices[next++] = index
            return indices
        }
    }
}
