// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.graphics.Path

/**
 * Origin-centred [KkFormGeometry] silhouettes cached by form index and radius. Drawing a path that has
 * not changed allocates nothing, while drawing a freshly rebuilt one does, so per-frame renderers
 * translate (and rotate) a cached silhouette instead of rebuilding it at the draw position.
 */
object KkFormPaths {
    private const val CAPACITY = 48
    private val keys = LongArray(CAPACITY) { -1L }
    private val paths = arrayOfNulls<Path>(CAPACITY)
    private var next = 0

    /** Form [index] at [radius], centred on the origin. Valid until [CAPACITY] other forms are requested. */
    fun of(index: Int, radius: Float): Path {
        val key = (index.toLong() shl 32) or (radius.toRawBits().toLong() and 0xFFFF_FFFFL)
        for (slot in 0 until CAPACITY) {
            if (keys[slot] == key) return paths[slot]!!
        }
        val path = paths[next] ?: Path().also { paths[next] = it }
        KkFormGeometry.build(path, index, 0f, 0f, radius)
        keys[next] = key
        next = (next + 1) % CAPACITY
        return path
    }
}
