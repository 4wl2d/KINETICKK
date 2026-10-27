// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.gameplay.interaction.fx.FxReach
import kinetickk.ball.gameplay.interaction.fx.ReactionFxSpecs
import kinetickk.ball.profile.api.ReactionEffectsLevel
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Each reaction's look as drawn (hud-fx §7.3): both of its elements' hues show early in the look,
 * nothing paints the threat colour, and nothing reaches past its reach. Rendered at reach 100 (the
 * fixed-reach shard looks at their own 220/240), heading 0, seed 7; colours at full brightness (weight 3,
 * since a single flash draws at 87 %), reach for a single flash.
 */
class ReactionFxRenderingTest {
    private val roles = KkRolePalette.Default

    @Test
    fun everyReactionShowsBothElementsAndNeverTheThreatColour() {
        for (reaction in ReactionId.entries) {
            val first = roles.element(reaction.first.ordinal)
            val second = roles.element(reaction.second.ordinal)
            var bestFirst = 0
            var bestSecond = 0
            for (progress in PROGRESS) {
                val pixels = render(reaction, progress)
                if (progress < 0.6f) {
                    bestFirst = maxOf(bestFirst, pixels.countNearColor(first))
                    bestSecond = maxOf(bestSecond, pixels.countNearColor(second))
                }
                assertEquals(0, pixels.countNearColor(roles.threat), "$reaction at $progress paints the threat colour")
            }
            assertTrue(bestFirst >= 12, "$reaction shows ${reaction.first} in $bestFirst px")
            assertTrue(bestSecond >= 12, "$reaction shows ${reaction.second} in $bestSecond px")
        }
    }

    @Test
    fun everyReactionStaysWithinItsReach() {
        for (reaction in ReactionId.entries) {
            val reach = reachOf(reaction)
            // The fixed shard looks run along their heading to 1.1 × their reach; the rest stay within 1.25 × reach.
            val limit = if (long(reaction)) 1.1f * reach + 24f else 1.25f * reach + 24f
            for (progress in PROGRESS) {
                // A single (unmerged) flash: merged ones reach further by design.
                val pixels = render(reaction, progress, weight = 1)
                val center = pixels.width * 0.5f
                var farthest = 0f
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                    if (!painted(pixels, x, y)) continue
                    farthest = maxOf(farthest, hypot(x + 0.5f - center, y + 0.5f - center))
                }
                assertTrue(farthest <= limit, "$reaction at $progress paints ${farthest.toInt()} px out (limit ${limit.toInt()})")
            }
        }
    }

    @Test
    fun theReducedAndMinimalSettingsDrawLessOfEveryReaction() {
        for (reaction in ReactionId.entries) {
            val full = paintedCount(render(reaction, 0.2f, ReactionEffectsLevel.FULL))
            val reduced = paintedCount(render(reaction, 0.2f, ReactionEffectsLevel.REDUCED))
            val minimal = paintedCount(render(reaction, 0.2f, ReactionEffectsLevel.MINIMAL))
            assertTrue(minimal in 1..reduced && reduced <= full, "$reaction draws $full / $reduced / $minimal px")
        }
    }

    private fun long(reaction: ReactionId) = ReactionFxSpecs[reaction].reach == FxReach.FIXED && ReactionFxSpecs[reaction].fixedReach >= 200f

    private fun reachOf(reaction: ReactionId): Float = if (long(reaction)) ReactionFxSpecs[reaction].fixedReach else REACH

    private fun render(reaction: ReactionId, progress: Float, level: ReactionEffectsLevel = ReactionEffectsLevel.FULL, weight: Int = 3): PixelMap {
        val reach = reachOf(reaction)
        val size = maxOf(SIZE, 2 * ceil(1.25f * reach + 40f).toInt())
        val center = size * 0.5f
        // The Core sits far off the canvas: only the flash itself draws.
        val model = FxTestScenes.model(size, size, -5_000f, -5_000f)
        return FxTestScenes.render(size, size, level) {
            drawElementBursts(model, FxTestScenes.fx(listOf(FxTestScenes.reaction(reaction, center, center, reach, progress, seed = 7,
                direction = 0f, weight = weight))), 0f, 0f, roles)
        }.toPixelMap()
    }

    private fun painted(pixels: PixelMap, x: Int, y: Int): Boolean {
        val pixel = pixels[x, y]
        val ink = Kk.Ink
        return abs(pixel.red - ink.red) + abs(pixel.green - ink.green) + abs(pixel.blue - ink.blue) > 0.03f
    }

    private fun paintedCount(pixels: PixelMap): Int {
        var count = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) if (painted(pixels, x, y)) count++
        return count
    }

    private companion object {
        const val SIZE = 256
        const val REACH = 100f
        val PROGRESS = floatArrayOf(0.15f, 0.5f, 0.85f)
    }
}
