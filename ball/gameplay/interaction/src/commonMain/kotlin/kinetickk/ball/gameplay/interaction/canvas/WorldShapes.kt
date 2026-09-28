// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.localization.WorldRedesignText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text

/**
 * World-layer caches. Everything drawn per frame reuses these: paths are built once per shape and
 * size and drawn under translate/rotate transforms, strokes and gradient brushes are created once,
 * and changing numbers resolve to cached strings. All of it is draw-thread confined.
 */
internal object WorldPaths {
    private const val CAPACITY = 64
    private val kinds = IntArray(CAPACITY) { -1 }
    private val sizes = FloatArray(CAPACITY)
    private val paths = arrayOfNulls<Path>(CAPACITY)
    private var next = 0

    private const val ARCHITECT_OUTER = 100
    private const val ARCHITECT_INNER = 101
    private const val PLATE = 102

    /** [shape] centered at the origin with the 24-grid half extent (10 units) mapped to [radius]. */
    fun silhouette(shape: EnemySilhouette, radius: Float): Path = get(shape.ordinal, radius)

    /** The Architect's outer triangle (`M50 4 96 84H4Z` on a 100 grid) with half width [radius]. */
    fun architectTriangle(radius: Float): Path = get(ARCHITECT_OUTER, radius)

    /** The Architect's inner bone triangle (`M50 30 74 72H26Z`). */
    fun architectInnerTriangle(radius: Float): Path = get(ARCHITECT_INNER, radius)

    /** A −12° sheared plate [width] wide and 0.7 × [width] tall centered at the origin. */
    fun shearedPlate(width: Float): Path = get(PLATE, width)

    private fun get(kind: Int, size: Float): Path {
        for (index in 0 until CAPACITY) {
            if (kinds[index] == kind && sizes[index] == size) return paths[index]!!
        }
        val path = paths[next]?.also { it.rewind() } ?: Path()
        build(path, kind, size)
        kinds[next] = kind
        sizes[next] = size
        paths[next] = path
        next = (next + 1) % CAPACITY
        return path
    }

    private fun build(path: Path, kind: Int, size: Float) {
        when (kind) {
            ARCHITECT_OUTER -> polygon(path, ArchitectOuter, 50f, size / 46f)
            ARCHITECT_INNER -> polygon(path, ArchitectInner, 50f, size / 46f)
            PLATE -> {
                val halfWidth = size * 0.5f
                val halfHeight = size * 0.35f
                val lean = halfHeight * 0.2126f
                path.moveTo(-halfWidth + lean, -halfHeight)
                path.lineTo(halfWidth + lean, -halfHeight)
                path.lineTo(halfWidth - lean, halfHeight)
                path.lineTo(-halfWidth - lean, halfHeight)
                path.close()
            }
            else -> {
                val shape = EnemySilhouette.entries[kind]
                for (contour in shape.contours) polygon(path, contour, 12f, size / 10f)
            }
        }
    }

    private fun polygon(path: Path, points: FloatArray, center: Float, scale: Float) {
        path.moveTo((points[0] - center) * scale, (points[1] - center) * scale)
        var index = 2
        while (index < points.size) {
            path.lineTo((points[index] - center) * scale, (points[index + 1] - center) * scale)
            index += 2
        }
        path.close()
    }

    private val ArchitectOuter = floatArrayOf(50f, 4f, 96f, 84f, 4f, 84f)
    private val ArchitectInner = floatArrayOf(50f, 30f, 74f, 72f, 26f, 72f)
}

/** Kill shard outline (x, y pairs around the origin): a parallelogram, symmetric about its center. */
internal val ShardOutline = floatArrayOf(-0.5f, -0.21f, 0.3f, -0.21f, 0.5f, 0.21f, -0.3f, 0.21f)

/** Unit shapes for fills that scale freely under a transform (no stroke to distort). */
internal object WorldUnitShapes {
    /**
     * A kill shard: a sheared quad sliver about one unit long with blunt, parallel ends. It is
     * point-symmetric ([ShardOutline]), so no rotation makes it read as an arrowhead.
     */
    val shard: Path by lazy {
        Path().apply {
            moveTo(ShardOutline[0], ShardOutline[1])
            var index = 2
            while (index < ShardOutline.size) {
                lineTo(ShardOutline[index], ShardOutline[index + 1])
                index += 2
            }
            close()
        }
    }

    /** The speed tail (`clip-path: polygon(0 20%, 100% 50%, 0 80%)`) from x = 0 to x = 1. */
    val tail: Path by lazy {
        Path().apply {
            moveTo(0f, -0.3f)
            lineTo(1f, 0f)
            lineTo(0f, 0.3f)
            close()
        }
    }
}

/** Cached strokes, including dashed ones (path effects are native-backed: created on first use). */
internal object WorldStrokes {
    val dashedHair: Stroke by lazy { Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))) }
    val dashedThin: Stroke by lazy { Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f))) }
    val dashedMedium: Stroke by lazy { Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))) }
    /** 6 px progress arcs (the collapsing orbit's ring). */
    val arc6: Stroke by lazy { Stroke(6f, cap = StrokeCap.Butt) }
    val dashEffect: PathEffect by lazy { PathEffect.dashPathEffect(floatArrayOf(8f, 7f)) }
}

/** Horizontal fades from a color to transparent over x = 0..1 (scaled by the caller's transform). */
internal object WorldBrushes {
    private const val CAPACITY = 8
    private val colors = LongArray(CAPACITY)
    private val brushes = arrayOfNulls<Brush>(CAPACITY)
    private var next = 0

    fun fade(color: Color): Brush {
        val key = color.value.toLong()
        for (index in 0 until CAPACITY) {
            if (brushes[index] != null && colors[index] == key) return brushes[index]!!
        }
        val brush = Brush.horizontalGradient(listOf(color, color.copy(alpha = 0f)), startX = 0f, endX = 1f)
        colors[next] = key
        brushes[next] = brush
        next = (next + 1) % CAPACITY
        return brush
    }
}

/** Edge glow gradients (color at the edge, transparent [depth] inside); rebuilt only on resize. */
internal class EdgeGlow(val left: Brush, val right: Brush, val top: Brush, val bottom: Brush)

internal fun WorldBrushes.edgeGlow(color: Color, width: Float, height: Float, depth: Float): EdgeGlow =
    EdgeGlowCache.get(color, width, height, depth)

private object EdgeGlowCache {
    private var key = LongArray(4) { -1L }
    private var cached: EdgeGlow? = null

    fun get(color: Color, width: Float, height: Float, depth: Float): EdgeGlow {
        val current = cached
        if (current != null && key[0] == color.value.toLong() && key[1] == width.toRawBits().toLong() &&
            key[2] == height.toRawBits().toLong() && key[3] == depth.toRawBits().toLong()
        ) return current
        val clear = color.copy(alpha = 0f)
        val glow = EdgeGlow(
            left = Brush.horizontalGradient(listOf(color, clear), startX = 0f, endX = depth),
            right = Brush.horizontalGradient(listOf(clear, color), startX = width - depth, endX = width),
            top = Brush.verticalGradient(listOf(color, clear), startY = 0f, endY = depth),
            bottom = Brush.verticalGradient(listOf(clear, color), startY = height - depth, endY = height),
        )
        key = longArrayOf(color.value.toLong(), width.toRawBits().toLong(), height.toRawBits().toLong(), depth.toRawBits().toLong())
        cached = glow
        return glow
    }
}

/** Strings for per-frame numbers, created once per value and language. */
internal object WorldStrings {
    private val timers = arrayOfNulls<String>(600)
    private val distanceSuffixes = arrayOfNulls<String>(AppLanguage.entries.size)
    private val crit = arrayOfNulls<String>(AppLanguage.entries.size)
    private val secondsSuffixes = arrayOfNulls<String>(AppLanguage.entries.size)
    private val pointNameSources = arrayOfNulls<String>(PointOfInterestKind.entries.size * AppLanguage.entries.size)
    private val pointNames = arrayOfNulls<String>(PointOfInterestKind.entries.size * AppLanguage.entries.size)

    /** Remaining time as `m:ss` (mono readout, no unit). */
    fun timer(seconds: Float): String {
        val whole = kotlin.math.ceil(seconds.coerceAtLeast(0f)).toInt()
        if (whole >= timers.size) return format(whole)
        return timers[whole] ?: format(whole).also { timers[whole] = it }
    }

    /** The constant unit after an edge-marker distance, display-cased once per language. */
    fun distanceSuffix(language: AppLanguage): String =
        distanceSuffixes[language.ordinal] ?: language.text(WorldRedesignText.Distance, "").uppercase()
            .also { distanceSuffixes[language.ordinal] = it }

    fun crit(language: AppLanguage): String =
        crit[language.ordinal] ?: language.text(WorldRedesignText.CriticalHit).also { crit[language.ordinal] = it }

    /** The constant unit after the orbit's seconds in the ring, display-cased once per language. */
    fun secondsSuffix(language: AppLanguage): String =
        secondsSuffixes[language.ordinal] ?: language.text(WorldRedesignText.OrbitSeconds, "").uppercase()
            .also { secondsSuffixes[language.ordinal] = it }

    /** The decimal separator before tenths. */
    fun decimalSeparator(language: AppLanguage): String = if (language == AppLanguage.Russian) "," else "."

    /** A point's content [name] in [language], resolved once per kind, language and name. */
    fun pointName(kind: PointOfInterestKind, name: String, language: AppLanguage): String {
        val slot = kind.ordinal * AppLanguage.entries.size + language.ordinal
        val cached = pointNames[slot]
        if (cached != null && pointNameSources[slot] == name) return cached
        return name.localizedContent(language).also {
            pointNameSources[slot] = name
            pointNames[slot] = it
        }
    }

    private fun format(seconds: Int): String {
        val remainder = seconds % 60
        return "${seconds / 60}:${if (remainder < 10) "0" else ""}$remainder"
    }
}
