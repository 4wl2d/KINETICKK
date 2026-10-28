// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The pacifier of Baby mode (the easy run mode): a handle ring, a wide guard with two vents and a teat, inside the circle
 * of [build]'s radius. Like [KkMutatorSigil] it writes a caller-owned [Path] with lines only; the sub-paths never overlap,
 * so the even-odd rule leaves only the ring's hole and the two vents open.
 */
object KkPacifierSigil {
    /** Rewinds [path] and writes the pacifier centred on ([cx], [cy]) inside the circle of [radius]. */
    fun build(path: Path, cx: Float, cy: Float, radius: Float) {
        path.rewind()
        path.fillType = PathFillType.EvenOdd
        val r = radius
        ellipse(path, cx, cy - 0.58f * r, 0.38f * r, 0.38f * r, 20)
        ellipse(path, cx, cy - 0.58f * r, 0.22f * r, 0.22f * r, 16)
        ellipse(path, cx, cy + 0.20f * r, 0.95f * r, 0.36f * r, 24)
        ellipse(path, cx - 0.52f * r, cy + 0.20f * r, 0.11f * r, 0.11f * r, 12)
        ellipse(path, cx + 0.52f * r, cy + 0.20f * r, 0.11f * r, 0.11f * r, 12)
        ellipse(path, cx, cy + 0.78f * r, 0.20f * r, 0.20f * r, 14)
    }

    /** A closed polygon of [sides] vertices on the ellipse ([rx], [ry]) around ([cx], [cy]). */
    private fun ellipse(path: Path, cx: Float, cy: Float, rx: Float, ry: Float, sides: Int) {
        for (index in 0 until sides) {
            val angle = index * 2f * PI.toFloat() / sides
            val x = cx + cos(angle) * rx
            val y = cy + sin(angle) * ry
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }
}

/**
 * Origin-centred [KkPacifierSigil] paths cached by radius (like [KkMutatorSigilPaths]): per-frame renderers translate a
 * cached pacifier instead of rebuilding it, so a steady frame allocates nothing.
 */
object KkPacifierSigilPaths {
    private const val CAPACITY = 8
    private val keys = IntArray(CAPACITY)
    private val paths = arrayOfNulls<Path>(CAPACITY)
    private var next = 0

    /** The pacifier at [radius], centred on the origin. Valid until [CAPACITY] other radii are requested. */
    fun of(radius: Float): Path {
        val key = radius.toRawBits()
        for (slot in 0 until CAPACITY) {
            val path = paths[slot]
            if (path != null && keys[slot] == key) return path
        }
        val path = paths[next] ?: Path().also { paths[next] = it }
        KkPacifierSigil.build(path, 0f, 0f, radius)
        keys[next] = key
        next = (next + 1) % CAPACITY
        return path
    }
}
