// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toPixelMap
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.interaction.fx.ElementBurstProjection
import kinetickk.ball.gameplay.interaction.fx.ReactionFxSpecs
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Recognizability without hue (hud-fx §7.3): the 21 reactions and the 7 Breaks drawn in the Mono
 * preset at 0.3 and 0.6 of their lives, each described by where it paints (4 rings × 12 sectors,
 * made rotation-free by the 12-sector DFT magnitudes per ring). Every pair of looks stays at least
 * 0.18 apart, and a second render at another seed (another rotation) is recognized as its own look.
 */
class ReactionFxSignatureTest {
    private val roles = KkRolePalette.Mono

    @Test
    fun everyReactionAndBreakIsRecognizableInMono() {
        val classes = ReactionId.entries.map { "reaction ${it.name}" } + StatusElement.entries.map { "break ${it.breakLabel}" }
        val trained = classes.indices.map { descriptor(it, seed = 7) }
        val close = mutableListOf<String>()
        var closest = Double.MAX_VALUE
        for (a in classes.indices) for (b in a + 1 until classes.size) {
            val distance = l1(trained[a], trained[b])
            closest = minOf(closest, distance)
            if (distance < MIN_DISTANCE) close += "${classes[a]} / ${classes[b]} ${"%.3f".format(distance)}"
        }
        println("ReactionFxSignatureTest: closest pair ${"%.3f".format(closest)}")
        assertEquals(emptyList(), close, "every pair of looks stays $MIN_DISTANCE apart")
        val misread = classes.indices.mapNotNull { index ->
            val probe = descriptor(index, seed = 1_234)
            val guess = trained.indices.minBy { l1(probe, trained[it]) }
            if (guess == index) null else "${classes[index]} read as ${classes[guess]}"
        }
        assertEquals(emptyList(), misread, "a second seed is recognized for every look")
    }

    /** The L1-normalised descriptor of look [index] (reactions, then Breaks) at [seed]. */
    private fun descriptor(index: Int, seed: Int): DoubleArray {
        val values = DoubleArray(FRAMES.size * RINGS.size * HARMONICS)
        var at = 0
        for (progress in FRAMES) {
            val pixels = render(index, progress, seed)
            val painted = Array(RINGS.size) { DoubleArray(SECTORS) }
            val total = Array(RINGS.size) { DoubleArray(SECTORS) }
            val center = SIZE * 0.5f
            for (y in 0 until SIZE) for (x in 0 until SIZE) {
                val dx = x + 0.5f - center
                val dy = y + 0.5f - center
                val r = hypot(dx, dy) / REACH
                val ring = RINGS.indexOfFirst { r < it }
                if (ring < 0) continue
                val angle = (atan2(dy, dx) + 2 * PI) % (2 * PI)
                val sector = ((angle / (2 * PI)) * SECTORS).toInt().coerceIn(0, SECTORS - 1)
                total[ring][sector] += 1.0
                if (painted(pixels, x, y)) painted[ring][sector] += 1.0
            }
            for (ring in RINGS.indices) {
                val fractions = DoubleArray(SECTORS) { painted[ring][it] / maxOf(1.0, total[ring][it]) }
                for (k in 0 until HARMONICS) {
                    var re = 0.0
                    var im = 0.0
                    for (sector in 0 until SECTORS) {
                        val phase = 2 * PI * k * sector / SECTORS
                        re += fractions[sector] * cos(phase)
                        im -= fractions[sector] * sin(phase)
                    }
                    values[at++] = sqrt(re * re + im * im)
                }
            }
        }
        val sum = values.sum()
        if (sum > 0) for (i in values.indices) values[i] /= sum
        return values
    }

    private fun render(index: Int, progress: Float, seed: Int): PixelMap {
        val center = SIZE * 0.5f
        val burst = if (index < ReactionId.entries.size) {
            val reaction = ReactionId.entries[index]
            FxTestScenes.reaction(reaction, center, center, REACH, progress, seed = seed, direction = Float.NaN)
        } else {
            // A Break's figure turns with nothing but its own time; the second seed turns the canvas instead.
            val element = index - ReactionId.entries.size
            ElementBurstProjection(center, center, element, -1, -1, REACH, 0.5f * (1f - progress), 0.5f, 1)
        }
        val model = FxTestScenes.model(SIZE, SIZE, -5_000f, -5_000f)
        val turn = if (index >= ReactionId.entries.size && seed != 7) 37f else 0f
        return FxTestScenes.render(SIZE, SIZE) {
            rotate(turn, Offset(center, center)) {
                drawElementBursts(model, FxTestScenes.fx(listOf(burst)), 0f, 0f, roles)
            }
        }.toPixelMap()
    }

    private fun painted(pixels: PixelMap, x: Int, y: Int): Boolean = abs(luminance(pixels[x, y]) - INK_LUMINANCE) > 0.1f

    private fun luminance(color: androidx.compose.ui.graphics.Color) = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

    private fun l1(a: DoubleArray, b: DoubleArray): Double {
        var sum = 0.0
        for (i in a.indices) sum += abs(a[i] - b[i])
        return sum
    }

    private companion object {
        const val SIZE = 300
        const val REACH = 100f
        const val SECTORS = 12
        const val HARMONICS = 7
        const val MIN_DISTANCE = 0.18
        val FRAMES = floatArrayOf(0.3f, 0.6f)

        /** Ring outer edges as fractions of the reach. */
        val RINGS = floatArrayOf(0.25f, 0.5f, 0.75f, 1.25f)
        val INK_LUMINANCE = 0.2126f * Kk.Ink.red + 0.7152f * Kk.Ink.green + 0.0722f * Kk.Ink.blue

        init {
            check(ReactionFxSpecs.maxLife > 0f)
        }
    }
}
