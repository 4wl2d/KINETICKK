// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.gameplay.interaction.layout.choiceLayoutGeometry
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RewardFitStateTest {
    @Test
    fun sharedFitSurvivesCardTranslationAndResetsForNewTextAndTextScale() = runDesktopComposeUiTest(1640, 900) {
        val width = mutableStateOf(1280f)
        val textScale = mutableStateOf(1.25f)
        val longText = "This paragraph describes a useful effect that applies during movement."
        val cards = mutableStateOf(listOf(card("ALPHA", List(12) { longText }), card("BETA"), card("GAMMA")))
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides AppLanguage.English) {
                Box(Modifier.requiredSize(1640.dp, 900.dp)) {
                    Box(Modifier.align(Alignment.TopStart).requiredSize(width.value.dp, 900.dp)) {
                        RewardContent(
                            RewardPresentation("Upgrade", "", cards.value, Color.White, Color.Cyan, 1),
                            choiceLayoutGeometry(width.value, 900f, 1f, cards.value.size, true),
                            width.value, 1f, textScale.value, 0f, true, {}, {},
                        )
                    }
                }
            }
        }
        fun settle() { mainClock.advanceTimeBy(2400); waitForIdle() }
        fun titleSize(): Float {
            val sizes = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                .fetchSemanticsNodes().mapNotNull { node ->
                    val layouts = mutableListOf<TextLayoutResult>()
                    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                    layouts.firstOrNull()?.takeIf { it.layoutInput.text.text in setOf("ALPHA", "BETA", "GAMMA") }
                        ?.layoutInput?.style?.fontSize?.value
                }
            assertEquals(3, sizes.size, "All three real card titles must participate")
            assertEquals(1, sizes.distinct().size, "One demanding card sets the whole deal's font cap: $sizes")
            return sizes.first()
        }
        settle()
        val initial = titleSize()
        assertTrue(initial < 38f, "The asymmetric long card must actually shrink the shared body size: $initial")
        val before = onNodeWithTag("kinetickk.gameplay.choice.1").fetchSemanticsNode().boundsInRoot
        runOnIdle { width.value = 1640f }
        settle()
        val after = onNodeWithTag("kinetickk.gameplay.choice.1").fetchSemanticsNode().boundsInRoot
        assertEquals(before.width, after.width, 0.01f, "The card width stays capped")
        assertEquals(before.height, after.height, 0.01f, "The card height stays capped")
        assertTrue(after.left > before.left + 100f, "Only the deal's position changes")
        assertEquals(initial, titleSize(), 0.01f, "Translated cards must republish their shared fit into the new layout")

        // Keep the same choice identity and titles; only its body copy changes.
        runOnIdle { cards.value = cards.value.map { it.copy(descriptions = listOf("Small bonus")) } }
        settle()
        assertEquals(38f, titleSize(), 0.01f, "Shortened content must recover the natural font size")

        runOnIdle {
            cards.value = cards.value.mapIndexed { index, card -> if (index == 0) card.copy(descriptions = List(4) { longText }) else card }
            textScale.value = 1.75f
        }
        settle()
        val large = titleSize()
        assertTrue(large < 38f * 1.4f - 0.01f, "The larger text setting must need a fit step: $large")
        runOnIdle { textScale.value = 1.25f }
        settle()
        assertEquals(38f, titleSize(), 0.01f, "A smaller text setting must reconsider the old fit instead of retaining its cap")
    }

    private fun card(title: String, descriptions: List<String> = listOf("Small bonus")) = RewardCardPresentation(
        choice = ChoiceOption(ChoiceType.ITEM, title, "", "COMMON"),
        accent = Color.Cyan,
        tag = "COMMON",
        descriptions = descriptions,
        operation = "",
    )
}
