// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.content.api.WorldLayer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The dimension sigils: one filled mark per layer, lines only, holes by the even-odd rule. The Zenith is a ring over a
 * short bar with three blunt rays, the Nadir a hexagon with three vent bars cut out, the Archive two offset square
 * frames, and realspace (the exit rift's centre) a diamond frame. A rift carries its target's sigil; the HUD plate and
 * the Codex draw the same marks.
 */

/** Sides of the polygon that stands for the Zenith's ring. */
private const val RING_SIDES = 24

/**
 * The closed polygons of [layer]'s sigil fitting the circle of [radius] around the origin, each as x, y pairs (y grows
 * downward). Every polygon is convex with no vertex sharper than a right angle, so no part reads as an arrowhead.
 */
internal fun dimensionSigilPolygons(layer: WorldLayer, radius: Float): List<FloatArray> {
    val r = radius
    return when (layer) {
        WorldLayer.ZENITH -> {
            val centerY = r * 0.02f
            buildList {
                add(regularPolygon(0f, centerY, r * 0.5f, RING_SIDES))
                add(regularPolygon(0f, centerY, r * 0.39f, RING_SIDES))
                add(rectangle(-r * 0.6f, r * 0.62f, r * 0.6f, r * 0.75f))
                for (degrees in intArrayOf(-50, 0, 50)) add(ray(0f, centerY, degrees * DEGREES, r * 0.62f, r * 0.9f, r * 0.065f))
            }
        }
        WorldLayer.NADIR -> buildList {
            add(regularPolygon(0f, 0f, r * 0.92f, 6))
            for (row in -1..1) add(rectangle(-r * 0.42f, row * r * 0.34f - r * 0.065f, r * 0.42f, row * r * 0.34f + r * 0.065f))
        }
        WorldLayer.ARCHIVE -> buildList {
            for (side in intArrayOf(-1, 1)) {
                val offset = side * r * 0.15f
                add(rectangle(offset - r * 0.5f, offset - r * 0.5f, offset + r * 0.5f, offset + r * 0.5f))
                add(rectangle(offset - r * 0.36f, offset - r * 0.36f, offset + r * 0.36f, offset + r * 0.36f))
            }
        }
        WorldLayer.BASE -> listOf(diamond(r * 0.9f), diamond(r * 0.56f))
    }
}

/** [layer]'s sigil filled in [color] at [alpha], centred on [center] and fitting the circle of [radius]. Allocation-free once cached. */
internal fun DrawScope.drawDimensionSigil(layer: WorldLayer, center: Offset, radius: Float, color: Color, alpha: Float = 1f) {
    val path = DimensionSigilPaths.get(layer, radius)
    translate(center.x, center.y) { drawPath(path, color, alpha = alpha) }
}

/**
 * A world discovery's mark, [size] px across (the Codex World category): a dimension's own sigil for the three layers,
 * else the discovery's site glyph ([worldDiscoveryGlyph]). Allocation-free once cached.
 */
fun DrawScope.drawWorldDiscoveryMark(id: WorldDiscovery, center: Offset, size: Float, color: Color) {
    val layer = worldDiscoveryLayer(id)
    if (layer != null) drawDimensionSigil(layer, center, size * DiscoverySigilShare[layer.ordinal], color)
    else drawSiteGlyph(worldDiscoveryGlyph(id), center, size, color)
}

/** The dimension a world discovery names, or null for every other discovery. */
fun worldDiscoveryLayer(id: WorldDiscovery): WorldLayer? = when (id) {
    WorldDiscovery.ZENITH -> WorldLayer.ZENITH
    WorldDiscovery.NADIR -> WorldLayer.NADIR
    WorldDiscovery.ARCHIVE -> WorldLayer.ARCHIVE
    else -> null
}

/**
 * Each layer's sigil radius as a share of the mark's size: the sigil's own bounding box spans [DISCOVERY_SIGIL_SPAN] of
 * the mark, as far as the site glyphs' widest marks reach, so a sigil reads as large as the glyphs beside it.
 */
private val DiscoverySigilShare: FloatArray by lazy {
    FloatArray(WorldLayer.entries.size) { ordinal ->
        val polygons = dimensionSigilPolygons(WorldLayer.entries[ordinal], 1f)
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        polygons.forEach { points ->
            for (index in points.indices step 2) {
                minX = minOf(minX, points[index]); maxX = maxOf(maxX, points[index])
                minY = minOf(minY, points[index + 1]); maxY = maxOf(maxY, points[index + 1])
            }
        }
        DISCOVERY_SIGIL_SPAN / maxOf(maxX - minX, maxY - minY)
    }
}

/** A discovery sigil's span in mark sizes: the site glyphs' widest marks (the cache's 20 of 24 units). */
private const val DISCOVERY_SIGIL_SPAN = 0.8f

/** Sigil paths centred on the origin, kept per layer and radius (a rift, the HUD plate, the Codex: a few radii each). */
private object DimensionSigilPaths {
    private const val CAPACITY = 24
    private val layers = IntArray(CAPACITY) { -1 }
    private val radii = FloatArray(CAPACITY)
    private val paths = arrayOfNulls<Path>(CAPACITY)
    private var next = 0

    fun get(layer: WorldLayer, radius: Float): Path {
        for (index in 0 until CAPACITY) {
            if (layers[index] == layer.ordinal && radii[index] == radius) return paths[index]!!
        }
        val slot = next
        next = (next + 1) % CAPACITY
        val path = paths[slot] ?: Path().also { paths[slot] = it }
        path.rewind()
        path.fillType = PathFillType.EvenOdd
        for (polygon in dimensionSigilPolygons(layer, radius)) {
            path.moveTo(polygon[0], polygon[1])
            for (point in 2 until polygon.size step 2) path.lineTo(polygon[point], polygon[point + 1])
            path.close()
        }
        layers[slot] = layer.ordinal
        radii[slot] = radius
        return path
    }
}

private const val DEGREES: Float = (PI / 180.0).toFloat()

private fun regularPolygon(cx: Float, cy: Float, radius: Float, sides: Int): FloatArray = FloatArray(sides * 2).also { points ->
    for (side in 0 until sides) {
        val angle = side * 2f * PI.toFloat() / sides
        points[side * 2] = cx + cos(angle) * radius
        points[side * 2 + 1] = cy + sin(angle) * radius
    }
}

private fun rectangle(left: Float, top: Float, right: Float, bottom: Float): FloatArray =
    floatArrayOf(left, top, right, top, right, bottom, left, bottom)

private fun diamond(half: Float): FloatArray = floatArrayOf(0f, -half, half, 0f, 0f, half, -half, 0f)

/** A blunt ray: a bar from [from] to [to] out of ([cx], [cy]) at [angle] from straight up, [halfWidth] each side. */
private fun ray(cx: Float, cy: Float, angle: Float, from: Float, to: Float, halfWidth: Float): FloatArray {
    val dx = sin(angle)
    val dy = -cos(angle)
    val nx = -dy * halfWidth
    val ny = dx * halfWidth
    return floatArrayOf(
        cx + dx * from + nx, cy + dy * from + ny,
        cx + dx * to + nx, cy + dy * to + ny,
        cx + dx * to - nx, cy + dy * to - ny,
        cx + dx * from - nx, cy + dy * from - ny,
    )
}
