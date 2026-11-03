// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.world.places

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/*
 * The Zenith is heaven: a dusk-gold ground lit from above, god rays fanning down from a sun beyond the top of the
 * screen, banks of cloud drifting far below the field (they move at half the world's speed, so they read as depth) and
 * feathers falling through it. Every shape is a cached unit path or a unit glow scaled into place.
 */

/** The light, the clouds and the shafts under the Zenith's grid. */
internal fun DrawScope.drawZenithSky(engine: GameplayRenderModel, theme: DimensionTheme, shakeX: Float, shakeY: Float) {
    val w = size.width
    val h = size.height
    // Heaven's light from above the screen: a wide gold dawn, a white sun beyond the top edge and a warm floor glow
    // where the Core flies; the clouds below catch it and the shafts fall through it.
    drawSoftDisc(DimensionGlows.of(theme.sky.copy(alpha = 0.26f), 0.05f), w * 0.5f, -h * 0.05f, w * 0.95f, h * 0.85f)
    drawSoftDisc(DimensionGlows.of(theme.glow.copy(alpha = 0.62f), 0.12f), w * 0.5f, -h * 0.14f, w * 0.45f, h * 0.5f)
    val core = world(engine, engine.renderCoreX, engine.renderCoreY, shakeX, shakeY)
    drawSoftDisc(DimensionGlows.of(theme.highlight.copy(alpha = 0.12f), 0.3f), core.x, core.y, 560f)
    drawZenithClouds(engine, theme, shakeX, shakeY)
    drawZenithShafts(theme, engine.renderElapsed)
}

/** Cloud banks parallax at this share of the world's motion and drift with the wind. */
private const val CLOUD_PARALLAX = 0.5f
private const val CLOUD_CELL = 560f
private const val CLOUD_SHARE = 0.45f
private const val CLOUD_WIND = 7f
private const val CLOUD_BLOBS = 5
/** A bank reaches this far from its centre. */
private const val CLOUD_REACH = 420f

/**
 * Cloud banks in the far layer: each cell of the parallax plane holds one when its hash says so, five soft blobs of
 * haze around a hashed centre, the plane sliding with the wind so the banks drift past.
 */
private fun DrawScope.drawZenithClouds(engine: GameplayRenderModel, theme: DimensionTheme, shakeX: Float, shakeY: Float) {
    val drift = engine.renderElapsed * CLOUD_WIND
    val centerX = engine.renderCameraX * CLOUD_PARALLAX - drift
    val centerY = engine.renderCameraY * CLOUD_PARALLAX
    val left = centerX - size.width * 0.5f - CLOUD_REACH
    val top = centerY - size.height * 0.5f - CLOUD_REACH
    val firstX = floor(left / CLOUD_CELL).toInt()
    val lastX = floor((left + size.width + 2f * CLOUD_REACH) / CLOUD_CELL).toInt()
    val firstY = floor(top / CLOUD_CELL).toInt()
    val lastY = floor((top + size.height + 2f * CLOUD_REACH) / CLOUD_CELL).toInt()
    val body = DimensionGlows.of(theme.sky.copy(alpha = 0.06f), 0.5f)
    val lit = DimensionGlows.of(theme.glowAlt.copy(alpha = 0.13f), 0.35f)
    for (cy in firstY..lastY) for (cx in firstX..lastX) {
        val hash = dimensionCellHash(cx, cy, CLOUD_LAYER_SALT)
        if (cellUnit(hash, 0) >= CLOUD_SHARE) continue
        val bankX = (cx + 0.2f + 0.6f * cellUnit(hash, 1)) * CLOUD_CELL - centerX + size.width * 0.5f + shakeX
        val bankY = (cy + 0.2f + 0.6f * cellUnit(hash, 2)) * CLOUD_CELL - centerY + size.height * 0.5f + shakeY
        if (!inView(bankX, bankY, CLOUD_REACH)) continue
        // Each blob's body, then the light on its upper side.
        for (blob in 0 until CLOUD_BLOBS) {
            val spread = (blob - (CLOUD_BLOBS - 1) * 0.5f) / CLOUD_BLOBS
            val radius = 120f + 130f * cellUnit(hash, 3 + blob)
            val x = bankX + spread * 520f
            val y = bankY + 60f * (cellUnit(hash, 8 + blob) - 0.5f)
            drawSoftDisc(body, x, y, radius * 1.35f, radius * 0.7f)
            drawSoftDisc(lit, x - radius * 0.15f, y - radius * 0.28f, radius * 0.8f, radius * 0.38f)
        }
    }
}

private const val CLOUD_LAYER_SALT = 41

/** The shafts fan out from one sun beyond the top of the screen: their angles off the vertical, half openings and alphas. */
private val ShaftAngles = floatArrayOf(-30f, -15f, 0f, 14f, 29f)
private val ShaftHalfAngles = floatArrayOf(3f, 4.5f, 3.5f, 5f, 3f)
private val ShaftAlphas = floatArrayOf(0.12f, 0.15f, 0.18f, 0.14f, 0.11f)

/**
 * God rays: five shafts of light fanning down from a sun above the top of the screen, each a soft wedge (a wide faint
 * one round a narrow bright one) fading toward its foot, swaying a few degrees once per 40 s or slower (an ambient loop
 * never turns faster than once per 24 s).
 */
private fun DrawScope.drawZenithShafts(theme: DimensionTheme, time: Float) {
    val length = max(size.width, size.height) * 1.9f
    val brush = ZenithPaths.shaftBrush(theme.ray)
    translate(size.width * 0.5f, -size.height * 0.55f) {
        for (index in ShaftAngles.indices) {
            val sway = 3f * sin(time * TWO_PI / (40f + 6f * index) + index * 1.7f)
            val wedge = ZenithPaths.shaft(index)
            rotate(ShaftAngles[index] + sway, Offset.Zero) {
                scale(length * 2.2f, length, Offset.Zero) { drawPath(wedge, brush, alpha = ShaftAlphas[index] * 0.4f) }
                scale(length, length, Offset.Zero) { drawPath(wedge, brush, alpha = ShaftAlphas[index]) }
            }
        }
    }
}

/** Feather cells: their side in world units, the share that holds one, and how fast a feather falls. */
private const val FEATHER_CELL = 240f
private const val FEATHER_SHARE = 0.42f
private const val FEATHER_FALL = 26f
private const val FEATHER_SALT = 43

/**
 * Feathers falling through the Zenith (the weather): each cell holds one when its hash falls under the share times
 * [density], falling inside its cell and swaying as it goes; a [crowded] frame keeps every other cell. Two draws per
 * feather from the atmosphere's budget.
 */
internal fun DrawScope.drawZenithFeathers(
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
    val firstX = floor((-originX - FEATHER_CELL) / FEATHER_CELL).toInt()
    val lastX = floor((size.width - originX) / FEATHER_CELL).toInt()
    val firstY = floor((-originY - FEATHER_CELL) / FEATHER_CELL).toInt()
    val lastY = floor((size.height - originY) / FEATHER_CELL).toInt()
    val feather = ZenithPaths.feather
    for (cy in firstY..lastY) for (cx in firstX..lastX) {
        if (crowded && (cx + cy) and 1 != 0) continue
        val hash = dimensionCellHash(cx, cy, FEATHER_SALT)
        if (cellUnit(hash, 0) >= FEATHER_SHARE * density) continue
        val phase = TWO_PI * cellUnit(hash, 1)
        val fall = wrapped(cellUnit(hash, 2) * FEATHER_CELL + FEATHER_FALL * (0.7f + 0.6f * cellUnit(hash, 3)) * time, FEATHER_CELL)
        val x = originX + (cx + cellUnit(hash, 4)) * FEATHER_CELL + 22f * sin(time * 0.7f + phase)
        val y = originY + cy * FEATHER_CELL + fall
        if (!inView(x, y, 30f) || !atmosphereAffords(2)) continue
        val side = 22f + 16f * cellUnit(hash, 5)
        val tilt = 40f * cellUnit(hash, 6) - 20f + 28f * sin(time * 0.9f + phase)
        val color = if (cellUnit(hash, 7) < 0.4f) theme.highlight else theme.glow
        translate(x, y) {
            rotate(tilt, Offset.Zero) {
                scale(side, side, Offset.Zero) {
                    drawPath(feather, color, alpha = 0.7f)
                    drawPath(ZenithPaths.featherShaft, theme.glow, alpha = 0.9f, style = ZenithPaths.shaftStroke)
                }
            }
        }
    }
}

private const val TWO_PI = (2.0 * PI).toFloat()

/** Cached unit paths of the Zenith: the shafts' wedges (one per opening), a feather's vane and shaft, and the shafts' fade. */
private object ZenithPaths {
    private val shafts = arrayOfNulls<Path>(ShaftHalfAngles.size)
    private var shaftColor = Color.Unspecified
    private var shaftFade: Brush? = null

    /** A unit wedge opening downward from the origin to y = 1 with half opening [ShaftHalfAngles] of [index]. */
    fun shaft(index: Int): Path = shafts[index] ?: Path().also { path ->
        val spread = tan(ShaftHalfAngles[index] * PI.toFloat() / 180f)
        path.moveTo(0f, 0f)
        path.lineTo(spread, 1f)
        path.lineTo(-spread, 1f)
        path.close()
        shafts[index] = path
    }

    /** The shafts' light: [color] at the top fading out before the foot (unit length), rebuilt only for a new colour. */
    fun shaftBrush(color: Color): Brush {
        val cached = shaftFade
        if (cached != null && color == shaftColor) return cached
        return Brush.verticalGradient(0f to color, 0.25f to color, 0.55f to color.copy(alpha = 0.5f), 1f to color.copy(alpha = 0f),
            startY = 0f, endY = 1f)
            .also {
                shaftFade = it
                shaftColor = color
            }
    }

    /** A unit feather: the vane, pointed at the top (y = -0.5) and tapering into the quill at y = 0.5. */
    val feather: Path by lazy {
        Path().apply {
            moveTo(0f, -0.5f)
            cubicTo(0.17f, -0.32f, 0.21f, 0.1f, 0.05f, 0.4f)
            lineTo(0f, 0.46f)
            lineTo(-0.05f, 0.4f)
            cubicTo(-0.19f, 0.08f, -0.15f, -0.3f, 0f, -0.5f)
            close()
        }
    }

    val featherShaft: Path by lazy {
        Path().apply {
            moveTo(0f, -0.42f)
            lineTo(0f, 0.62f)
        }
    }

    /** The shaft's line in unit space: about 1.2 px on a feather 18 u long. */
    val shaftStroke: Stroke by lazy { Stroke(0.065f) }
}

/** The Zenith's cached shapes never depend on the world, the viewport or the preferences. */
internal fun prepareZenithResources() {
    ZenithPaths.feather
    ZenithPaths.featherShaft
    ZenithPaths.shaftStroke
    repeat(ShaftHalfAngles.size) { ZenithPaths.shaft(it) }
}
