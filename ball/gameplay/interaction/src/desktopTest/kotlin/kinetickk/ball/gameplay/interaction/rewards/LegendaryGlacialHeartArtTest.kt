// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** The Glacial Heart scene loops without a jump: the last frame of a loop leads straight into the first. */
class LegendaryGlacialHeartArtTest {
    private val item = shippedRewardContent.items.single { it.name == "Glacial Heart" }

    @Test
    fun theLoopSeamIsNoBiggerThanAnOrdinaryFrameStep() {
        for (palette in listOf(KkRolePalette.Default, KkRolePalette.Mono)) for (compact in listOf(false, true)) {
            val step = 1f / 60f
            val loop = GlacialHeartLoopSeconds
            val seam = difference(art(loop - step, palette, compact), art(0f, palette, compact))
            val ordinary = difference(art(1f, palette, compact), art(1f + step, palette, compact))
            assertTrue(ordinary > 0.0, "the scene stands still at t = 1 s (compact $compact)")
            assertTrue(seam <= ordinary * 1.5, "the loop seam steps $seam, an ordinary frame $ordinary (compact $compact)")
            assertTrue(difference(art(loop, palette, compact), art(0f, palette, compact)) == 0.0, "t = loop and t = 0 differ")
        }
    }

    /**
     * The scene alone (no card loops) at [time] on a board or phone sized card, as the mean channel
     * level of each [BLOCK] px block: a coarse image, so a flake or crack tip that jumps several pixels
     * changes it far more than one that moves a pixel.
     */
    private fun art(time: Float, palette: KkRolePalette, compact: Boolean): DoubleArray {
        val width = if (compact) 195 else 300
        val height = if (compact) 254 else 450
        val unit = if (compact) 0.886f else 1f
        val choice = ChoiceOption(ChoiceType.ITEM, item.name, item.description, item.rarity.displayLabel, itemId = item.id)
        val card = rewardFixtureModel(content = shippedRewardContent, choices = listOf(choice)).rewardCardPresentation(choice, 0)
        val look = rewardCardLook(card, palette)
        val frame = RewardArtFrame(
            bounds = Rect(0f, 0f, width.toFloat(), height.toFloat()),
            face = Rect(0f, 42f * unit, width.toFloat(), height.toFloat()),
            clear = Rect(26f * unit, height * (if (compact) 0.62f else 0.56f), width - 26f * unit, height - 30f * unit),
            icon = Rect(26f * unit, 60f * unit, 90f * unit, 124f * unit),
            hue = look.hue,
            secondHue = look.secondHue,
            roles = palette,
            density = 1f,
            seed = look.style.seed,
            compact = compact,
            unit = unit,
            cache = RewardArtCache(),
            look = look,
        )
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawRect(Kk.Ink2)
            drawRewardCardArt(RewardCardArt.GLACIAL_HEART, frame, time)
        }
        val pixels = bitmap.toPixelMap()
        val columns = width / BLOCK
        val rows = height / BLOCK
        val blocks = DoubleArray(columns * rows)
        for (y in 0 until rows * BLOCK) for (x in 0 until columns * BLOCK) {
            val argb = pixels[x, y].toArgb()
            blocks[(y / BLOCK) * columns + x / BLOCK] += ((argb shr 16 and 0xFF) + (argb shr 8 and 0xFF) + (argb and 0xFF)) / 3.0
        }
        for (index in blocks.indices) blocks[index] /= (BLOCK * BLOCK).toDouble()
        return blocks
    }

    /** Mean absolute difference of two coarse frames, 0..255. */
    private fun difference(a: DoubleArray, b: DoubleArray): Double = a.indices.sumOf { abs(a[it] - b[it]) } / a.size

    private companion object {
        const val BLOCK = 12
    }
}
