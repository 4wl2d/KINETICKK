// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Filled sigils of the ten Rebirth mutators, keyed by activation index 0..9, plus the Ascendant
 * crown ([ASCENDANT]) and the tier plate ([TIER]). Every builder rewinds and writes a caller-owned
 * [Path] with lines only (holes use the even-odd rule of their own sub-paths), so the HUD, the Rebirth
 * ladder, Home and the Hangar share one geometry without allocating per frame. Each sigil fits the
 * circle of [radius] around ([cx], [cy]).
 *
 * Index order: pack hunters, hardened elites, hunter protocol, volatile remains, fractured space,
 * overclocked swarm, entropy tide, armored vanguard, blackout, singularity.
 */
object KkMutatorSigil {
    const val COUNT: Int = 10

    /** The Ascendant crown: three spikes over a band. */
    const val ASCENDANT: Int = 10

    /** The tier plate: a wide diamond that carries the tier numeral. */
    const val TIER: Int = 11

    /** Writes sigil [index] (0 until [COUNT], [ASCENDANT] or [TIER]) into [path]. */
    fun build(path: Path, index: Int, cx: Float, cy: Float, radius: Float) {
        require(index in 0..TIER) { "sigil index $index is outside 0..$TIER" }
        path.rewind()
        path.fillType = PathFillType.EvenOdd
        val r = radius
        when (index) {
            // Pack Hunters: three chevrons closing in.
            0 -> {
                chevron(path, cx - r * 0.5f, cy + r * 0.3f, r * 0.42f)
                chevron(path, cx + r * 0.5f, cy + r * 0.3f, r * 0.42f)
                chevron(path, cx, cy - r * 0.35f, r * 0.42f)
            }
            // Hardened Elites: a diamond with a diamond cut out of it.
            1 -> {
                diamond(path, cx, cy, r, r)
                diamond(path, cx, cy, r * 0.45f, r * 0.45f)
            }
            // Hunter Protocol: a reticle (square frame with a cross cut).
            2 -> {
                rect(path, cx - r * 0.9f, cy - r * 0.9f, cx + r * 0.9f, cy + r * 0.9f)
                rect(path, cx - r * 0.62f, cy - r * 0.62f, cx + r * 0.62f, cy + r * 0.62f)
                rect(path, cx - r * 0.12f, cy - r * 0.9f, cx + r * 0.12f, cy + r * 0.9f)
                rect(path, cx - r * 0.9f, cy - r * 0.12f, cx + r * 0.9f, cy + r * 0.12f)
            }
            // Volatile Remains: an eight-point burst.
            3 -> star(path, cx, cy, r, r * 0.42f, 8, -PI.toFloat() / 2f)
            // Fractured Space: a hexagon split by a zigzag gap.
            4 -> {
                path.moveTo(cx - r * 0.1f, cy - r)
                path.lineTo(cx - r * 0.87f, cy - r * 0.5f)
                path.lineTo(cx - r * 0.87f, cy + r * 0.5f)
                path.lineTo(cx - r * 0.2f, cy + r * 0.9f)
                path.lineTo(cx + r * 0.05f, cy + r * 0.2f)
                path.lineTo(cx - r * 0.25f, cy - r * 0.1f)
                path.close()
                path.moveTo(cx + r * 0.1f, cy - r * 0.95f)
                path.lineTo(cx + r * 0.87f, cy - r * 0.5f)
                path.lineTo(cx + r * 0.87f, cy + r * 0.5f)
                path.lineTo(cx + r * 0.05f, cy + r)
                path.lineTo(cx + r * 0.25f, cy + r * 0.3f)
                path.lineTo(cx - r * 0.05f, cy)
                path.close()
            }
            // Overclocked Swarm: a double fast-forward chevron.
            5 -> {
                arrow(path, cx - r * 0.95f, cy, r * 0.95f)
                arrow(path, cx, cy, r * 0.95f)
            }
            // Entropy Tide: two wave bands.
            6 -> {
                wave(path, cx, cy - r * 0.42f, r, r * 0.26f)
                wave(path, cx, cy + r * 0.42f, r, r * 0.26f)
            }
            // Armored Vanguard: a shield with a plate seam.
            7 -> {
                path.moveTo(cx - r * 0.85f, cy - r * 0.8f)
                path.lineTo(cx + r * 0.85f, cy - r * 0.8f)
                path.lineTo(cx + r * 0.75f, cy + r * 0.2f)
                path.lineTo(cx, cy + r)
                path.lineTo(cx - r * 0.75f, cy + r * 0.2f)
                path.close()
                rect(path, cx - r * 0.1f, cy - r * 0.62f, cx + r * 0.1f, cy + r * 0.62f)
            }
            // Blackout: a disc eclipsed by a bar.
            8 -> {
                polygonPath(path, cx, cy, r, 16, 0f)
                rect(path, cx - r, cy - r * 0.16f, cx + r, cy + r * 0.16f)
            }
            // Singularity: a ring around a core.
            9 -> {
                polygonPath(path, cx, cy, r, 20, 0f)
                polygonPath(path, cx, cy, r * 0.68f, 20, 0f)
                polygonPath(path, cx, cy, r * 0.32f, 12, 0f)
            }
            ASCENDANT -> {
                path.moveTo(cx - r, cy + r * 0.7f)
                path.lineTo(cx - r, cy - r * 0.55f)
                path.lineTo(cx - r * 0.5f, cy - r * 0.05f)
                path.lineTo(cx, cy - r)
                path.lineTo(cx + r * 0.5f, cy - r * 0.05f)
                path.lineTo(cx + r, cy - r * 0.55f)
                path.lineTo(cx + r, cy + r * 0.7f)
                path.close()
                rect(path, cx - r * 0.7f, cy + r * 0.32f, cx + r * 0.7f, cy + r * 0.48f)
            }
            else -> diamond(path, cx, cy, r, r * 0.78f)
        }
    }

    private fun diamond(path: Path, cx: Float, cy: Float, rx: Float, ry: Float) {
        path.moveTo(cx, cy - ry)
        path.lineTo(cx + rx, cy)
        path.lineTo(cx, cy + ry)
        path.lineTo(cx - rx, cy)
        path.close()
    }

    private fun rect(path: Path, left: Float, top: Float, right: Float, bottom: Float) {
        path.moveTo(left, top)
        path.lineTo(right, top)
        path.lineTo(right, bottom)
        path.lineTo(left, bottom)
        path.close()
    }

    private fun chevron(path: Path, cx: Float, cy: Float, size: Float) {
        path.moveTo(cx - size, cy + size * 0.6f)
        path.lineTo(cx, cy - size * 0.6f)
        path.lineTo(cx + size, cy + size * 0.6f)
        path.lineTo(cx + size * 0.55f, cy + size * 0.6f)
        path.lineTo(cx, cy - size * 0.05f)
        path.lineTo(cx - size * 0.55f, cy + size * 0.6f)
        path.close()
    }

    private fun arrow(path: Path, left: Float, cy: Float, size: Float) {
        path.moveTo(left, cy - size * 0.8f)
        path.lineTo(left + size, cy)
        path.lineTo(left, cy + size * 0.8f)
        path.lineTo(left + size * 0.3f, cy)
        path.close()
    }

    private fun wave(path: Path, cx: Float, cy: Float, halfWidth: Float, thickness: Float) {
        val steps = 6
        val amplitude = thickness * 0.9f
        for (step in 0..steps) {
            val x = cx - halfWidth + halfWidth * 2f * step / steps
            val y = cy - thickness * 0.5f + if (step % 2 == 0) -amplitude else amplitude
            if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        for (step in steps downTo 0) {
            val x = cx - halfWidth + halfWidth * 2f * step / steps
            val y = cy + thickness * 0.5f + if (step % 2 == 0) -amplitude else amplitude
            path.lineTo(x, y)
        }
        path.close()
    }

    private fun star(path: Path, cx: Float, cy: Float, outer: Float, inner: Float, points: Int, start: Float) {
        for (index in 0 until points * 2) {
            val radius = if (index % 2 == 0) outer else inner
            val angle = start + index * PI.toFloat() / points
            val x = cx + cos(angle) * radius
            val y = cy + sin(angle) * radius
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    private fun polygonPath(path: Path, cx: Float, cy: Float, radius: Float, sides: Int, start: Float) {
        for (index in 0 until sides) {
            val angle = start + index * 2f * PI.toFloat() / sides
            val x = cx + cos(angle) * radius
            val y = cy + sin(angle) * radius
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }
}

/**
 * Origin-centred [KkMutatorSigil] paths cached by index and radius (like [KkFormPaths]): per-frame
 * renderers translate a cached sigil instead of rebuilding it, so a steady frame allocates nothing.
 */
object KkMutatorSigilPaths {
    private const val CAPACITY = 32
    private val keys = LongArray(CAPACITY) { -1L }
    private val paths = arrayOfNulls<Path>(CAPACITY)
    private var next = 0

    /** Sigil [index] at [radius], centred on the origin. Valid until [CAPACITY] other sigils are requested. */
    fun of(index: Int, radius: Float): Path {
        val key = (index.toLong() shl 32) or (radius.toRawBits().toLong() and 0xFFFF_FFFFL)
        for (slot in 0 until CAPACITY) {
            if (keys[slot] == key) return paths[slot]!!
        }
        val path = paths[next] ?: Path().also { paths[next] = it }
        KkMutatorSigil.build(path, index, 0f, 0f, radius)
        keys[next] = key
        next = (next + 1) % CAPACITY
        return path
    }
}
