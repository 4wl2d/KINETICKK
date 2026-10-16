// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import java.lang.management.ManagementFactory
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Solar Choir scene loops without a seam (a frame one whole loop later is the same frame) and,
 * once warm, draws without allocating, on the board and on a phone, in a hue palette and in Mono.
 */
class LegendarySolarChoirArtTest {
    private val card: RewardCardPresentation by lazy {
        val item = shippedRewardContent.items.single { it.name == RewardCardArt.SOLAR_CHOIR.itemName }
        val choice = ChoiceOption(ChoiceType.ITEM, item.name, item.description, item.rarity.displayLabel.uppercase(), itemId = item.id)
        rewardFixtureModel(content = shippedRewardContent, choices = listOf(choice)).rewardCardPresentation(choice, 0)
    }

    @Test
    fun theSceneMeetsItselfOneLoopLater() {
        for (palette in Palettes) for (compact in listOf(false, true)) {
            // Includes the instants an ember ignites (whole seconds) and the one just before the seam.
            for (time in listOf(0f, 0.95f, 1f, 2.5f, 5.98f)) {
                val now = artPixels(palette, compact, time)
                val later = artPixels(palette, compact, time + SolarChoirLoopSeconds)
                val worst = now.indices.maxOf { channelDelta(now[it], later[it]) }
                assertTrue(worst <= 3, "Solar Choir at $time s and one loop later differ by $worst (compact $compact)")
            }
        }
    }

    @Test
    fun theSceneAllocatesNothingPerFrame() {
        for (palette in Palettes) for (compact in listOf(false, true)) {
            val size = cardSize(compact)
            val frame = artFrame(palette, compact, size)
            val canvas = Canvas(ImageBitmap(size.width.toInt(), size.height.toInt()))
            val scope = CanvasDrawScope()
            fun empty() = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) { drawRect(Kk.Ink) }
            fun scene(index: Int) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) {
                drawRect(Kk.Ink)
                drawRewardCardArt(RewardCardArt.SOLAR_CHOIR, frame, index / 60f)
            }
            // One whole loop warms every cache; the same frames are then measured.
            repeat(Frames) { empty(); scene(it) }
            val emptyBytes = allocated { repeat(Frames) { empty() } }
            val sceneBytes = allocated { repeat(Frames) { scene(it) } }
            val perFrame = (sceneBytes - emptyBytes) / Frames
            assertTrue(perFrame <= 8, "Solar Choir allocates $perFrame bytes per frame (compact $compact)")
        }
    }

    /** The art alone, on ink, at [time]. */
    private fun artPixels(palette: KkRolePalette, compact: Boolean, time: Float): IntArray {
        val size = cardSize(compact)
        val bitmap = ImageBitmap(size.width.toInt(), size.height.toInt())
        val frame = artFrame(palette, compact, size)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size) {
            drawRect(Kk.Ink)
            drawRewardCardArt(RewardCardArt.SOLAR_CHOIR, frame, time)
        }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.readPixels(pixels)
        return pixels
    }

    /** The art frame of a board (300 × 450) or phone (195 × 254) card, laid out like the reward card's. */
    private fun artFrame(palette: KkRolePalette, compact: Boolean, size: Size): RewardArtFrame {
        val look = rewardCardLook(card, palette)
        val width = size.width
        val height = size.height
        val unit = if (compact) minOf(width / 220f, height / 256f) else 1f
        val band = if (compact) 30f else 42f
        val plateX = if (compact) 22f else 34f
        val plateY = if (compact) 38f else 56f
        val plate = if (compact) 46f else 88f
        val start = if (compact) 22f else 34f
        val end = if (compact) 24f else 34f
        val clearTop = height * (if (compact) 0.62f else 0.56f)
        val bottom = height - (if (compact) 34f else 52f) * unit
        return RewardArtFrame(
            bounds = Rect(0f, 0f, width, height),
            face = Rect(0f, band * unit, width, height),
            clear = Rect(start * unit, clearTop, width - end * unit, maxOf(bottom, clearTop + 24f * unit)),
            icon = Rect(plateX * unit, plateY * unit, (plateX + plate) * unit, (plateY + plate) * unit),
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
    }

    private fun cardSize(compact: Boolean): Size = if (compact) Size(195f, 254f) else Size(300f, 450f)

    private fun channelDelta(a: Int, b: Int): Int {
        var worst = 0
        for (shift in intArrayOf(0, 8, 16, 24)) worst = maxOf(worst, abs(((a ushr shift) and 0xFF) - ((b ushr shift) and 0xFF)))
        return worst
    }

    private inline fun allocated(block: () -> Unit): Long {
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val id = Thread.currentThread().id
        val before = threads.getThreadAllocatedBytes(id)
        block()
        return threads.getThreadAllocatedBytes(id) - before
    }

    private companion object {
        val Palettes = listOf(KkRolePalette.Default, KkRolePalette.Mono)

        /** One whole loop at 60 fps. */
        const val Frames = 360
    }
}
