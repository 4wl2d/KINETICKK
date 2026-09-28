// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.gameplay.interaction.layout.choiceLayoutGeometry
import kinetickk.ball.gameplay.nucleus.render.RewardPreview
import kinetickk.ball.gameplay.nucleus.render.RewardStatChange
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.rememberKkCanvasMeasurer
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * Epic and legendary cards keep their rising sparks, halftone and sheen behind the text with
 * soft face plates. Compares each rendered card with its bare face (the same frame and effects,
 * no text) over the 2.8 s spark loop: how much of the face effects the card hides must change
 * gradually everywhere outside the glyphs, so no spark is ever cut into a caret or sliver and the
 * halftone and sheen show no hard edge around the text.
 */
@OptIn(ExperimentalTestApi::class)
class RewardCardPlatesTest {
    @Test
    fun faceEffectsFadeUnderTheTextWithoutAHardEdge() {
        val model = rewardFixtureModel(
            choices = listOf(itemChoice(0), itemChoice(1), itemChoice(2)), discoveredItems = setOf(0, 1, 2),
            previews = listOf(
                RewardPreview(immutableListOf(RewardStatChange("Dash impulse", 873f, 1083f, ""))),
                RewardPreview(immutableListOf(RewardStatChange("Critical chance", 8f, 27f, "%"), RewardStatChange("Critical power", 1.5f, 1.64f, "×"))),
                RewardPreview(immutableListOf(RewardStatChange("Overdrive gain", 1.2f, 1.51f, "×"), RewardStatChange("Combo window", 2.8f, 2.98f, "s"))),
            ),
        )
        var frames = 0
        for ((screenWidth, screenHeight) in listOf(844f to 390f, 390f to 844f)) for (language in AppLanguage.entries) {
            val bounds = choiceLayoutGeometry(screenWidth, screenHeight, 1f, 3, true).cards[1]
            val width = bounds.width.toInt()
            val height = bounds.height.toInt()
            // Legendary (Cataclysm Lens) and epic (Echo Reactor).
            for (index in listOf(1, 2)) {
                val card = model.rewardCardPresentation(model.choices[index], index, language)
                check(card.rank >= 4)
                runDesktopComposeUiTest(width * 2 + 20, height) {
                    val time = mutableFloatStateOf(0f)
                    setContent {
                        CompositionLocalProvider(LocalAppLanguage provides language, LocalDensity provides Density(1f)) {
                            val measurer = rememberKkCanvasMeasurer(1f)
                            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                // Clipped: legendary rays reach far past a card and would paint over its neighbour.
                                Box(Modifier.requiredSize(width.dp, height.dp).clipToBounds().testTag("card")) {
                                    RewardCard(card, 0, 1f, 0f, true, Modifier.requiredSize(width.dp, height.dp), time = time) {}
                                }
                                Canvas(Modifier.requiredSize(width.dp, height.dp).clipToBounds().testTag("face")) {
                                    drawRewardCardFace(measurer, card, card.accent, time.floatValue, 0f, -1f)
                                }
                            }
                        }
                    }
                    waitForIdle()
                    val cardRoot = onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
                    // Everything drawn over the face on purpose: glyphs (the name with its glow), the icon plate.
                    val drawn = onAllNodes(hasAnyAncestor(hasTestTag("card")) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
                        useUnmergedTree = true).fetchSemanticsNodes().map { node ->
                        val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text }
                        // The epic name glow spreads 8 dp around the name.
                        val pad = if (text == card.title.uppercase()) 10f else 2f
                        node.boundsInRoot.translate(-cardRoot.left, -cardRoot.top).inflate(pad)
                    } + onAllNodes(hasTestTag("kinetickk.gameplay.choice.1.compact") or hasTestTag("kinetickk.gameplay.choice.1.expanded"),
                        useUnmergedTree = true).fetchSemanticsNodes().map { node ->
                        node.boundsInRoot.translate(-cardRoot.left, -cardRoot.top).inflate(2f)
                    }
                    val scene = "${card.title} ${screenWidth.toInt()}x${screenHeight.toInt()} $language"
                    var t = 0f
                    while (t <= 2.8f + 0.001f) {
                        runOnIdle { time.floatValue = t }
                        waitForIdle()
                        val rendered = onNodeWithTag("card").captureToImage().toPixelMap()
                        val face = onNodeWithTag("face").captureToImage().toPixelMap()
                        assertSoftCover(rendered, face, drawn, "$scene at ${"%.1f".format(t)} s")
                        frames++
                        t += 0.1f
                    }
                }
            }
        }
        assertTrue(frames >= 4 * 2 * 29, "Swept the whole spark loop ($frames frames)")
    }

    /**
     * Where the bare [face] shows an effect (spark, halftone dot, sheen) that differs from the
     * card face color, the share the card hides it by must not jump between neighbouring pixels.
     */
    private fun assertSoftCover(rendered: PixelMap, face: PixelMap, drawn: List<Rect>, scene: String) {
        val width = minOf(rendered.width, face.width)
        val height = minOf(rendered.height, face.height)
        val plate = Kk.Ink2.toArgb()
        val cut = 22f
        fun coverage(x: Int, y: Int): Float? {
            if (y < 4 || y > height - 5) return null
            // Stay clear of the slanted face outline (the card's own edge) and of intended drawing.
            val left = cut * (1f - y / height.toFloat()) + 4f
            val right = width - cut * (y / height.toFloat()) - 4f
            if (x < left || x > right) return null
            if (drawn.any { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }) return null
            val a = rendered[x, y].toArgb()
            val b = face[x, y].toArgb()
            var best = 0f
            var result = 0f
            for (shift in intArrayOf(16, 8, 0)) {
                val f = (plate shr shift and 0xFF).toFloat()
                val under = (b shr shift and 0xFF).toFloat()
                val shown = (a shr shift and 0xFF).toFloat()
                val contrast = f - under
                if (abs(contrast) > abs(best)) {
                    best = contrast
                    result = (shown - under) / contrast
                }
            }
            return if (abs(best) >= 32f) result.coerceIn(-0.5f, 1.5f) else null
        }
        for (y in 0 until height - 1) for (x in 0 until width - 1) {
            val here = coverage(x, y) ?: continue
            coverage(x + 1, y)?.let { right ->
                assertTrue(abs(here - right) <= 0.3f, "$scene: the face effects are cut at a hard edge at ($x, $y): $here to $right")
            }
            coverage(x, y + 1)?.let { below ->
                assertTrue(abs(here - below) <= 0.3f, "$scene: the face effects are cut at a hard edge at ($x, $y): $here to $below")
            }
        }
    }
}
