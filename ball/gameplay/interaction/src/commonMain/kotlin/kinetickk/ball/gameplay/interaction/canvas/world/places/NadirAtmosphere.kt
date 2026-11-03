// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world.places

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.positiveModulo
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/*
 * The Nadir is hell: a crimson glow over soot-black ground, fire light from below the screen, glowing magma cracks that breathe, smoke banks
 * drifting over them, bands of heat shimmer rising up the screen, and sparks raining upward through the field (the
 * weather). The cracks belong to the world (stateless cells), the smoke and the shimmer to the air above it.
 */

/** The ground glow, cracks, smoke and shimmer under the Nadir's grid. */
internal fun DrawScope.drawNadirDepths(engine: GameplayRenderModel, theme: DimensionTheme, shakeX: Float, shakeY: Float) {
    val w = size.width
    val h = size.height
    // The pit's crimson glow, fire light rising from below the screen (hell is lit from beneath, heaven from above),
    // black at the edges of sight.
    with(ScreenGlows) {
        drawScreenGlow(ScreenGlow.NADIR_PIT, theme.glow.copy(alpha = 0.8f), w * 0.5f, h * 0.55f, maxOf(w, h) * 0.62f)
        drawScreenGlow(ScreenGlow.NADIR_FIRE, theme.crackGlow.copy(alpha = 0.22f), w * 0.5f, h + w * 0.28f, w * 0.48f, core = 0.15f)
    }
    drawNadirCracks(engine, theme, shakeX, shakeY)
    drawNadirSmoke(engine, theme)
    drawHeatShimmer(theme, engine.renderElapsed)
}

/** Side of a crack cell in world units, the share of cells with a crack and how far a crack reaches from its anchor. */
internal const val CRACK_CELL = 200f
internal const val CRACK_SHARE = 0.36f
internal const val CRACK_REACH = 100f
internal const val CRACK_SALT = 6

/**
 * Magma cracks in a share of the Nadir's cells, one of the cached shapes per cell: a wide glow, the magma body and its
 * bright core, the glow breathing on its own slow phase (once per six seconds or slower); one crack in four sits over a
 * faint pool of light.
 */
private fun DrawScope.drawNadirCracks(engine: GameplayRenderModel, theme: DimensionTheme, shakeX: Float, shakeY: Float) {
    val originX = worldOriginX(engine, shakeX)
    val originY = worldOriginY(engine, shakeY)
    val time = engine.renderElapsed
    val pool = DimensionGlows.of(theme.crackGlow.copy(alpha = 0.26f), 0.1f)
    forEachNadirCrack(-originX, -originY, size.width - originX, size.height - originY) { worldX, worldY, variant, hash ->
        val x = originX + worldX
        val y = originY + worldY
        val crack = NadirPaths.crack(variant)
        val breath = 0.65f + 0.35f * sin(time * TWO_PI / (6f + 3f * cellUnit(hash, CRACK_SALT + 4)) + TWO_PI * cellUnit(hash, CRACK_SALT + 5))
        if (cellUnit(hash, CRACK_SALT + 6) < 0.25f) drawSoftDisc(pool, x, y, 110f * (0.8f + 0.2f * breath), alpha = breath)
        // Each cell turns its shape its own way, so the field never repeats a crack in line.
        translate(x, y) {
            rotate(360f * cellUnit(hash, CRACK_SALT + 7), Offset.Zero) {
                drawPath(crack, theme.crackGlow, alpha = 0.32f * breath, style = AtmosphereStrokes.crackGlow)
                drawPath(crack, theme.crackGlow, alpha = 0.75f + 0.25f * breath, style = AtmosphereStrokes.crackBody)
                drawPath(crack, theme.crack, alpha = 0.7f + 0.3f * breath, style = AtmosphereStrokes.crackCore)
            }
        }
    }
}

/**
 * Every crack whose cell lies within its reach of the world rectangle [left]..[right] × [top]..[bottom], as its world
 * anchor, its shape variant and its cell's hash: the cracks belong to the world, so the same cell always holds the same
 * crack at the same place.
 */
internal inline fun forEachNadirCrack(left: Float, top: Float, right: Float, bottom: Float, action: (x: Float, y: Float, variant: Int, hash: Int) -> Unit) {
    val firstX = floor((left - CRACK_REACH) / CRACK_CELL).toInt()
    val lastX = floor((right + CRACK_REACH) / CRACK_CELL).toInt()
    val firstY = floor((top - CRACK_REACH) / CRACK_CELL).toInt()
    val lastY = floor((bottom + CRACK_REACH) / CRACK_CELL).toInt()
    for (cy in firstY..lastY) for (cx in firstX..lastX) {
        val hash = dimensionCellHash(cx, cy, kinetickk.ball.content.api.WorldLayer.NADIR.ordinal)
        if (cellUnit(hash, CRACK_SALT) >= CRACK_SHARE) continue
        val variant = (cellUnit(hash, CRACK_SALT + 1) * NadirPaths.CRACKS).toInt().coerceAtMost(NadirPaths.CRACKS - 1)
        action(
            (cx + 0.3f + 0.4f * cellUnit(hash, CRACK_SALT + 2)) * CRACK_CELL,
            (cy + 0.3f + 0.4f * cellUnit(hash, CRACK_SALT + 3)) * CRACK_CELL,
            variant,
            hash,
        )
    }
}

/** Smoke banks: how many, how fast they drift and at what share of the world's motion. */
private const val SMOKE_BANKS = 2
private const val SMOKE_WIND = 16f
private const val SMOKE_PARALLAX = 0.8f

/** Smoke banks drifting over the cracks, slightly slower than the world, wrapping around the screen. */
private fun DrawScope.drawNadirSmoke(engine: GameplayRenderModel, theme: DimensionTheme) {
    val brush = DimensionGlows.of(theme.smoke.copy(alpha = 0.62f), 0.35f)
    val spanX = size.width + 800f
    val spanY = size.height + 600f
    for (index in 0 until SMOKE_BANKS) {
        val hash = dimensionCellHash(index, 7, SMOKE_SALT)
        val x = positiveModulo(cellUnit(hash, 0) * spanX - engine.renderCameraX * SMOKE_PARALLAX + engine.renderElapsed * SMOKE_WIND * (0.6f + 0.8f * cellUnit(hash, 1)), spanX) - 400f
        val y = positiveModulo(cellUnit(hash, 2) * spanY - engine.renderCameraY * SMOKE_PARALLAX, spanY) - 300f
        val radius = 170f + 110f * cellUnit(hash, 3)
        drawSoftDisc(brush, x, y, radius * 1.4f, radius)
    }
}

private const val SMOKE_SALT = 47

/** Heat shimmer: bands rising up the screen at this speed, each this tall. */
private const val SHIMMER_BANDS = 4
private const val SHIMMER_RISE = 22f
private const val SHIMMER_HEIGHT = 34f

/** Bands of heat shimmer rising up the screen: soft strips of heat light, each breathing on its own phase. */
private fun DrawScope.drawHeatShimmer(theme: DimensionTheme, time: Float) {
    val band = NadirPaths.shimmerBrush(theme.crackGlow)
    val span = size.height + SHIMMER_HEIGHT * 2f
    for (index in 0 until SHIMMER_BANDS) {
        val y = positiveModulo(span * index / SHIMMER_BANDS - time * SHIMMER_RISE, span) - SHIMMER_HEIGHT
        translate(0f, y) {
            scale(1f, SHIMMER_HEIGHT, Offset.Zero) {
                drawRect(band, size = Size(size.width, 1f), alpha = 0.07f + 0.04f * sin(time * 0.8f + index * 1.9f))
            }
        }
    }
}

/** Ember-rain cells: their side, the share that holds a spark and the spark's rise speed range (world units per second). */
private const val SPARK_CELL = 120f
private const val SPARK_SHARE = 0.6f
private const val SPARK_RISE = 45f
private const val SPARK_RISE_SPREAD = 55f
private const val SPARK_SALT = 49

/**
 * Sparks raining upward through the Nadir (the weather): a cell holds one when its hash falls under the share times
 * [density], rising inside its cell with a flicker sideways, drawn as a short streak with a bright head; a [crowded]
 * frame keeps every other cell. Two draws per spark from the atmosphere's budget.
 */
internal fun DrawScope.drawNadirEmberRain(
    engine: GameplayRenderModel,
    theme: DimensionTheme,
    shakeX: Float,
    shakeY: Float,
    density: Float,
    crowded: Boolean,
) {
    val originX = worldOriginX(engine, shakeX)
    val originY = worldOriginY(engine, shakeY)
    val time = engine.renderElapsed
    val firstX = floor((-originX) / SPARK_CELL).toInt() - 1
    val lastX = floor((size.width - originX) / SPARK_CELL).toInt()
    val firstY = floor((-originY) / SPARK_CELL).toInt() - 1
    val lastY = floor((size.height - originY) / SPARK_CELL).toInt()
    for (cy in firstY..lastY) for (cx in firstX..lastX) {
        if (crowded && (cx + cy) and 1 != 0) continue
        val hash = dimensionCellHash(cx, cy, SPARK_SALT)
        if (cellUnit(hash, 0) >= SPARK_SHARE * density) continue
        val rise = SPARK_RISE + SPARK_RISE_SPREAD * cellUnit(hash, 1)
        val climb = positiveModulo(cellUnit(hash, 2) * SPARK_CELL - rise * time, SPARK_CELL)
        val x = originX + (cx + cellUnit(hash, 3)) * SPARK_CELL + 6f * sin(time * 3f + TWO_PI * cellUnit(hash, 4))
        val y = originY + cy * SPARK_CELL + climb
        if (!inView(x, y, 20f) || !atmosphereAffords(2)) continue
        // A spark fades in at the bottom of its cell and out at the top.
        val life = climb / SPARK_CELL
        val alpha = (life * 4f).coerceAtMost(1f) * (0.45f + 0.5f * cellUnit(hash, 5))
        val streak = 5f + 6f * cellUnit(hash, 6)
        drawLine(theme.crackGlow.copy(alpha = alpha * 0.5f), Offset(x - 2f * sin(time * 3f + cellUnit(hash, 4)), y + streak), Offset(x, y), 2.2f)
        drawCircle(theme.highlight.copy(alpha = alpha), 1.2f + 1.3f * cellUnit(hash, 7), Offset(x, y))
    }
}

private const val TWO_PI = (2.0 * PI).toFloat()

/** Cached shapes of the Nadir: the crack paths (object-local) and the shimmer strip's brush. */
internal object NadirPaths {
    const val CRACKS = 5
    private val cracks = arrayOfNulls<Path>(CRACKS)

    /** Crack [variant]: open polylines turning only at obtuse angles, around the origin. */
    fun crack(variant: Int): Path = cracks[variant] ?: Path().also { path ->
        for (line in NadirCrackLines[variant]) {
            path.moveTo(line[0], line[1])
            for (point in 2 until line.size step 2) path.lineTo(line[point], line[point + 1])
        }
        cracks[variant] = path
    }

    private var shimmerColor = Color.Unspecified
    private var shimmer: Brush? = null

    /** A unit-tall strip of [color] clear at its edges, strongest in its middle; rebuilt only for a new colour. */
    fun shimmerBrush(color: Color): Brush {
        val cached = shimmer
        if (cached != null && color == shimmerColor) return cached
        return Brush.verticalGradient(0f to color.copy(alpha = 0f), 0.5f to color, 1f to color.copy(alpha = 0f), startY = 0f, endY = 1f)
            .also {
                shimmer = it
                shimmerColor = color
            }
    }
}

/** The crack shapes' polylines (x, y pairs; a branch is its own line), each within [CRACK_REACH] of its anchor. */
internal val NadirCrackLines: List<List<FloatArray>> = listOf(
    listOf(floatArrayOf(-90f, -20f, -48f, 4f, -10f, -6f, 28f, 14f, 88f, 26f), floatArrayOf(-10f, -6f, 8f, -40f, 30f, -62f),
        floatArrayOf(28f, 14f, 40f, 46f)),
    listOf(floatArrayOf(-80f, 40f, -40f, 18f, -6f, 24f, 30f, -10f, 76f, -34f), floatArrayOf(-6f, 24f, 12f, 58f), floatArrayOf(30f, -10f, 50f, 20f)),
    listOf(floatArrayOf(-94f, 0f, -52f, -14f, -14f, 6f, 24f, -4f, 58f, 16f, 92f, 8f), floatArrayOf(-52f, -14f, -60f, -50f)),
    listOf(floatArrayOf(-40f, -86f, -22f, -44f, -30f, -6f, -4f, 30f, 6f, 84f), floatArrayOf(-30f, -6f, -70f, 10f), floatArrayOf(-4f, 30f, 36f, 40f, 72f, 70f)),
    listOf(floatArrayOf(-86f, 30f, -44f, 34f, -12f, 8f, 30f, 20f, 64f, -12f, 90f, -22f), floatArrayOf(-12f, 8f, -2f, -34f, 22f, -70f)),
)

/** The Nadir's cached shapes never depend on the world, the viewport or the preferences. */
internal fun prepareNadirResources() {
    repeat(NadirPaths.CRACKS) { NadirPaths.crack(it) }
}
