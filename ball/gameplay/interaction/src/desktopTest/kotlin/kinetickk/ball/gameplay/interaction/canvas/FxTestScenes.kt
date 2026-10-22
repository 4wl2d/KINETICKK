// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.gameplay.interaction.fx.ApexBurstProjection
import kinetickk.ball.gameplay.interaction.fx.ApexFxSpec
import kinetickk.ball.gameplay.interaction.fx.ElementBurstProjection
import kinetickk.ball.gameplay.interaction.fx.ReactionFxSpecs
import kinetickk.ball.gameplay.interaction.fx.SkillBurstProjection
import kinetickk.ball.gameplay.interaction.fx.SkillFxSpecs
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.interaction.fx.BreakBurstRadii
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.profile.api.ReactionEffectsLevel
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.design.Kk
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Shared fixtures for the world FX tests and contact sheets: a model whose camera puts world (x, y)
 * at screen (x, y), burst factories at a chosen progress, a one-frame renderer and colour maths.
 */
internal object FxTestScenes {
    private val bases = HashMap<Long, GameplayRenderModel>()

    /** A running model of [width] × [height] whose world point (x, y) draws at screen (x, y), the Core at ([coreX], [coreY]). */
    fun model(width: Int, height: Int, coreX: Float = width * 0.5f, coreY: Float = height * 0.5f, vararg overrides: Pair<String, Any?>) =
        bases.getOrPut((width.toLong() shl 32) or height.toLong()) { hudTestModel(width.toFloat(), height.toFloat()) }.with(
            "cameraX" to width * 0.5f, "cameraY" to height * 0.5f, "coreX" to coreX, "coreY" to coreY,
            "previousCoreX" to coreX, "previousCoreY" to coreY, "previousCameraX" to width * 0.5f, "previousCameraY" to height * 0.5f,
            "velocityX" to 0f, "velocityY" to 0f, "elapsed" to 10f, *overrides,
        )

    /** A reaction flash at [progress] of its life with [reach] (the projection's own reach). */
    fun reaction(
        reaction: ReactionId,
        x: Float,
        y: Float,
        reach: Float,
        progress: Float,
        seed: Int = 7,
        direction: Float = 0f,
        flags: Int = 0,
        weight: Int = 1,
    ): ElementBurstProjection {
        val life = ReactionFxSpecs[reaction].life
        return ElementBurstProjection(x, y, reaction.first.ordinal, reaction.second.ordinal, reaction.ordinal, reach, life * (1f - progress), life,
            weight, direction, flags, seed)
    }

    /** A Break flash of [element] at [progress]. */
    fun breakBurst(element: Int, x: Float, y: Float, progress: Float, reach: Float = BreakBurstRadii[element]): ElementBurstProjection =
        ElementBurstProjection(x, y, element, -1, -1, reach, 0.5f * (1f - progress), 0.5f, 1)

    fun skill(skill: SkillId, phase: Int, x: Float, y: Float, reach: Float, progress: Float, rank: Int = 3, flags: Int = 0,
              dirX: Float = 1f, dirY: Float = 0f): SkillBurstProjection {
        val life = SkillFxSpecs.of(skill, phase)!!.life
        return SkillBurstProjection(skill.ordinal, phase, x, y, dirX, dirY, reach, rank, flags, 7, life * (1f - progress), life)
    }

    fun apex(shape: Int, x: Float, y: Float, progress: Float): ApexBurstProjection {
        val life = ApexFxSpec.activation.life
        return ApexBurstProjection(shape, x, y, 7, life * (1f - progress), life)
    }

    fun fx(
        bursts: List<ElementBurstProjection> = emptyList(),
        skills: List<SkillBurstProjection> = emptyList(),
        apex: List<ApexBurstProjection> = emptyList(),
    ) = VisualFxProjection.EMPTY.copy(elementBursts = bursts.toImmutableList(), skillBursts = skills.toImmutableList(),
        apexBursts = apex.toImmutableList())

    /**
     * One frame on an ink ground: [block] draws after the frame's FX detail starts for [crowd]
     * on-screen enemies and the [level] setting.
     */
    fun render(
        width: Int,
        height: Int,
        level: ReactionEffectsLevel = ReactionEffectsLevel.FULL,
        crowd: Int = 0,
        ground: Color = Kk.Ink,
        block: DrawScope.() -> Unit,
    ): ImageBitmap {
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawRect(ground)
            FxFrame.begin(crowd, level)
            block()
        }
        return bitmap
    }

    fun write(bitmap: ImageBitmap, file: File) {
        val map = bitmap.toPixelMap()
        val image = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) image.setRGB(x, y, map[x, y].toArgb())
        file.parentFile.mkdirs()
        ImageIO.write(image, "png", file)
    }

    /** A model with [model]'s settings at another reaction effects [level]. */
    fun GameplayRenderModel.atLevel(level: ReactionEffectsLevel): GameplayRenderModel =
        with("settings" to settings.copy(reactionEffects = level))
}

/** CIELAB of an sRGB colour (D65). */
internal fun labOf(color: Color): FloatArray {
    fun linear(c: Float) = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    val r = linear(color.red)
    val g = linear(color.green)
    val b = linear(color.blue)
    val x = (0.4124f * r + 0.3576f * g + 0.1805f * b) / 0.95047f
    val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
    val z = (0.0193f * r + 0.1192f * g + 0.9505f * b) / 1.08883f
    fun f(t: Float) = if (t > 0.008856f) cbrt(t) else 7.787f * t + 16f / 116f
    return floatArrayOf(116f * f(y) - 16f, 500f * (f(x) - f(y)), 200f * (f(y) - f(z)))
}

/** CIEDE2000 colour difference between two Lab colours. */
internal fun deltaE2000(a: FloatArray, b: FloatArray): Double {
    val l1 = a[0].toDouble(); val a1 = a[1].toDouble(); val b1 = a[2].toDouble()
    val l2 = b[0].toDouble(); val a2 = b[1].toDouble(); val b2 = b[2].toDouble()
    val c1 = sqrt(a1 * a1 + b1 * b1)
    val c2 = sqrt(a2 * a2 + b2 * b2)
    val cBar = (c1 + c2) / 2
    val g = 0.5 * (1 - sqrt(cBar.pow(7) / (cBar.pow(7) + 25.0.pow(7))))
    val a1p = (1 + g) * a1
    val a2p = (1 + g) * a2
    val c1p = sqrt(a1p * a1p + b1 * b1)
    val c2p = sqrt(a2p * a2p + b2 * b2)
    fun hue(bp: Double, ap: Double) = if (bp == 0.0 && ap == 0.0) 0.0 else (atan2(bp, ap) * 180 / PI).let { if (it < 0) it + 360 else it }
    val h1p = hue(b1, a1p)
    val h2p = hue(b2, a2p)
    val dLp = l2 - l1
    val dCp = c2p - c1p
    var dhp = h2p - h1p
    if (c1p * c2p == 0.0) dhp = 0.0 else if (dhp > 180) dhp -= 360 else if (dhp < -180) dhp += 360
    val dHp = 2 * sqrt(c1p * c2p) * sin(dhp * PI / 360)
    val lBarP = (l1 + l2) / 2
    val cBarP = (c1p + c2p) / 2
    var hBarP = h1p + h2p
    if (c1p * c2p != 0.0) hBarP = if (abs(h1p - h2p) <= 180) hBarP / 2 else if (hBarP < 360) (hBarP + 360) / 2 else (hBarP - 360) / 2
    val t = 1 - 0.17 * cos((hBarP - 30) * PI / 180) + 0.24 * cos(2 * hBarP * PI / 180) + 0.32 * cos((3 * hBarP + 6) * PI / 180) -
        0.20 * cos((4 * hBarP - 63) * PI / 180)
    val dTheta = 30 * exp(-((hBarP - 275) / 25).pow(2))
    val rc = 2 * sqrt(cBarP.pow(7) / (cBarP.pow(7) + 25.0.pow(7)))
    val sl = 1 + 0.015 * (lBarP - 50).pow(2) / sqrt(20 + (lBarP - 50).pow(2))
    val sc = 1 + 0.045 * cBarP
    val sh = 1 + 0.015 * cBarP * t
    val rt = -sin(2 * dTheta * PI / 180) * rc
    return sqrt((dLp / sl).pow(2) + (dCp / sc).pow(2) + (dHp / sh).pow(2) + rt * (dCp / sc) * (dHp / sh))
}

/** Pixels of [map] within ΔE2000 [limit] of [color]. */
internal fun PixelMap.countNearColor(color: Color, limit: Double = 6.0): Int {
    val target = labOf(color)
    var count = 0
    for (y in 0 until height) for (x in 0 until width) if (deltaE2000(labOf(this[x, y]), target) <= limit) count++
    return count
}
