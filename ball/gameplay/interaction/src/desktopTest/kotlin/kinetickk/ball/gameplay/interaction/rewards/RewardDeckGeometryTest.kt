// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Where reward cards sit: a decline card's plate and text start clear of its slanted dashed outline at every card size.
 */
@OptIn(ExperimentalTestApi::class)
class RewardDeckGeometryTest {
    @Test
    fun aDeclineCardsPlateAndTextStartClearOfItsOutline() {
        // The portrait 2 x 2 deck's card, the phone gallery's and landscape deal's cards, and the board card.
        val sizes = listOf(Triple(178, 305, false), Triple(195, 254, true), Triple(220, 256, true), Triple(300, 450, false))
        for (language in AppLanguage.entries) for ((width, height, phoneDeal) in sizes) {
            val decline = rewardFixtureModel(choiceType = ChoiceType.OMEN, choices = listOf(noOmen), language = language)
                .rewardPresentation(language).cards.single()
            assertTrue(decline.decline)
            runDesktopComposeUiTest(width + 40, height + 40) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides language) {
                        Box(Modifier.requiredSize((width + 40).dp, (height + 40).dp)) {
                            RewardCard(decline, 0, overlayUiTextScale(1.25f), 0f, true,
                                Modifier.requiredSize(width.dp, height.dp), phoneDeal = phoneDeal) {}
                        }
                    }
                }
                waitForIdle()
                val card = onNodeWithTag("kinetickk.gameplay.choice.1").fetchSemanticsNode().boundsInRoot
                val where = "${width}x$height ${language.code}"
                val band = if (rewardCardUsesCompactLayout(width.toFloat(), height.toFloat(), phoneDeal)) 30f else 42f
                val u = rewardCardUnit(width.toFloat(), height.toFloat(), phoneDeal)
                fun clear(box: Rect, what: String) {
                    val outline = rewardDeclineOutlineLeftDp(box.top - card.top, height.toFloat(), band * u)
                    assertTrue(box.left - card.left >= outline + 3f,
                        "$what starts ${box.left - card.left} dp in, on the outline at $outline dp ($where)")
                }
                val plateTag = "kinetickk.gameplay.choice.1.${if (rewardCardUsesCompactLayout(width.toFloat(), height.toFloat(), phoneDeal)) "compact" else "expanded"}"
                clear(onNodeWithTag(plateTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot, "the plate")
                val texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
                    .filter { node -> node.boundsInRoot.top > card.top + band * u }
                assertTrue(texts.size >= 2, "the decline's title and line are shown ($where)")
                texts.forEach { node -> clear(node.boundsInRoot, "'${node.config[SemanticsProperties.Text].joinToString()}'") }
            }
        }
    }

    private val noOmen = ChoiceOption(ChoiceType.OMEN, "No omen", "Start the run as it is.", "DECLINE")
}
