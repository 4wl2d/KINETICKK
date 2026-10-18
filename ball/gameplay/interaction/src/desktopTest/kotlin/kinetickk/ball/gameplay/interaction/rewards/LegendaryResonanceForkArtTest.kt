// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import java.lang.management.ManagementFactory
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Resonance Fork scene loops without a seam (a frame one loop later is the same frame, and no
 * two frames 1/60 s apart jump, the seam and the strike included) and, once warm, draws without
 * allocating, on the board and on a phone, in a hue palette and in Mono.
 */
class LegendaryResonanceForkArtTest {
    private val item: ItemDefinition = shippedRewardContent.items.single { it.name == RewardCardArt.RESONANCE_FORK.itemName }

    @Test
    fun theSceneLoopsWithoutASeamOrAPop() {
        val card = card()
        val frame = frame(card, KkRolePalette.Default, compact = false)
        val loop = ResonanceForkLoopSeconds
        fun shot(time: Float): PixelMap {
            val bitmap = ImageBitmap(300, 450)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), frame.bounds.size) {
                drawRect(Kk.Ink2)
                drawRewardCardArt(RewardCardArt.RESONANCE_FORK, frame, time)
            }
            return bitmap.toPixelMap()
        }
        for (time in listOf(0f, 0.08f, 2.3f, 5.9f)) {
            val delta = meanDelta(shot(time), shot(time + loop))
            assertTrue(delta < 0.1f, "t = $time and one loop later differ by $delta")
        }
        // Every frame step of the loop, across the seam: the largest step stays small and the seam is no larger than the rest.
        val step = 1f / 60f
        var previous = shot(loop - step)
        val steps = FloatArray((loop / step).toInt() + 1)
        for (index in steps.indices) {
            val next = shot(index * step)
            steps[index] = meanDelta(previous, next)
            previous = next
        }
        val seam = steps[0]
        val sorted = steps.sorted()
        val median = sorted[sorted.size / 2]
        val largest = sorted.last()
        assertTrue(largest < 3f, "the scene jumps by $largest (mean channel delta) between two frames 1/60 s apart")
        assertTrue(seam <= median * 2f, "the loop seam jumps by $seam (a frame step is $median)")
    }

    @Test
    fun theSceneAllocatesNothingPerFrame() {
        val card = card()
        for (palette in listOf(KkRolePalette.Default, KkRolePalette.Mono)) for (compact in listOf(false, true)) {
            val frame = frame(card, palette, compact)
            val bitmap = ImageBitmap(frame.bounds.width.toInt(), frame.bounds.height.toInt())
            val canvas = Canvas(bitmap)
            val scope = CanvasDrawScope()
            fun empty() = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, frame.bounds.size) { drawRect(Kk.Ink2) }
            fun scene(index: Int) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, frame.bounds.size) {
                drawRect(Kk.Ink2)
                drawRewardCardArt(RewardCardArt.RESONANCE_FORK, frame, index / 60f)
            }
            repeat(Frames) { empty(); scene(it) }
            val emptyBytes = allocated { repeat(Frames) { empty() } }
            val sceneBytes = allocated { repeat(Frames) { scene(it) } }
            val perFrame = (sceneBytes - emptyBytes) / Frames
            assertTrue(perFrame <= 8, "the scene allocates $perFrame bytes per frame (compact $compact)")
        }
    }

    private fun card(): RewardCardPresentation {
        val choice = ChoiceOption(ChoiceType.ITEM, item.name, item.description, item.rarity.displayLabel.uppercase(), itemId = item.id)
        val model = rewardFixtureModel(content = shippedRewardContent, choices = listOf(choice),
            discoveredItems = shippedRewardContent.items.map { it.id }.toSet())
        return model.rewardCardPresentation(choice, 0, AppLanguage.English)
    }

    private fun meanDelta(a: PixelMap, b: PixelMap): Float {
        var sum = 0.0
        for (y in 0 until a.height) for (x in 0 until a.width) {
            val p = a[x, y]
            val q = b[x, y]
            sum += abs(p.red - q.red) + abs(p.green - q.green) + abs(p.blue - q.blue)
        }
        return (sum * 255.0 / (a.width * a.height * 3.0)).toFloat()
    }

    /** A board (300 x 450) or phone (195 x 254) art frame of [card], as the card face lays it out. */
    private fun frame(card: RewardCardPresentation, palette: KkRolePalette, compact: Boolean): RewardArtFrame {
        val look = rewardCardLook(card, palette)
        return if (compact) {
            val u = 195f / 220f
            RewardArtFrame(
                bounds = Rect(0f, 0f, 195f, 254f), face = Rect(0f, 30f * u, 195f, 254f),
                clear = Rect(22f * u, 254f * 0.62f, 195f - 24f * u, 254f - 34f * u),
                icon = Rect(22f * u, 38f * u, 68f * u, 84f * u), hue = look.hue, secondHue = look.secondHue, roles = palette,
                density = 1f, seed = look.style.seed, compact = true, unit = u, cache = RewardArtCache(), look = look,
            )
        } else {
            RewardArtFrame(
                bounds = Rect(0f, 0f, 300f, 450f), face = Rect(0f, 42f, 300f, 450f), clear = Rect(34f, 252f, 266f, 398f),
                icon = Rect(34f, 56f, 122f, 144f), hue = look.hue, secondHue = look.secondHue, roles = palette,
                density = 1f, seed = look.style.seed, compact = false, unit = 1f, cache = RewardArtCache(), look = look,
            )
        }
    }

    private inline fun allocated(block: () -> Unit): Long {
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val id = Thread.currentThread().id
        val before = threads.getThreadAllocatedBytes(id)
        block()
        return threads.getThreadAllocatedBytes(id) - before
    }

    private companion object {
        /** Eight seconds at 60 fps: more than one loop. */
        const val Frames = 480
    }
}
