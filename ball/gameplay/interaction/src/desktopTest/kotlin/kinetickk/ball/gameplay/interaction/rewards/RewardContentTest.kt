// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.gameplay.interaction.layout.choiceLayoutGeometry
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RewardContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun changingLanguageUpdatesVisibleRewardActionWithoutRecreatingTheCard() {
        val language = mutableStateOf(AppLanguage.Russian)
        var selections = 0
        compose.setContent {
            CompositionLocalProvider(LocalAppLanguage provides language.value) {
                RewardCard(card(), 0, 1f, 0f, true, Modifier.requiredSize(250.dp, 270.dp)) { selections++ }
            }
        }
        compose.onNodeWithText("ВЫБРАТЬ [1]", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { language.value = AppLanguage.English }
        compose.onNodeWithText("SELECT [1]", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { language.value = AppLanguage.Russian }
        compose.onNodeWithText("ВЫБРАТЬ [1]", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("kinetickk.gameplay.choice.1").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, selections) }
    }

    @Test
    fun clickSelectsOnceAndOnlyVisibleCardHasActivation() {
        var selections = 0
        setEnglishContent { RewardCard(card(), 0, 1f, 0f, true, Modifier.requiredSize(250.dp, 270.dp)) { selections++ } }

        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onNodeWithTag("kinetickk.gameplay.choice.1").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, selections) }
        compose.onNodeWithTag("kinetickk.gameplay.choice.1.action", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun draggingAndWheelScrollFullTextWithoutSelectingAndActionStaysPinned() {
        var selections = 0
        setEnglishContent { RewardCard(card(), 0, 1.75f, 0f, true, Modifier.requiredSize(178.dp, 220.dp)) { selections++ } }
        val action = compose.onNodeWithTag("kinetickk.gameplay.choice.1.action", useUnmergedTree = true)
        val actionTop = action.fetchSemanticsNode().boundsInRoot.top
        val text = compose.onNodeWithTag("kinetickk.gameplay.choice.1.text", useUnmergedTree = true)
        text.performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(0, selections) }
        text.performMouseInput { moveTo(center); scroll(100f) }
        compose.runOnIdle { assertEquals(0, selections) }
        compose.onNodeWithText("FINAL OPERATION · complete text", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        action.assertIsDisplayed()
        assertEquals(actionTop, action.fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithTag("kinetickk.gameplay.choice.1.scroll", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun actualCardBoundsChooseCompactHeaderAtEveryTextScale() {
        val dimensions = mutableStateOf(Triple(180f, 240f, 1f))
        setEnglishContent {
            val (width, height, scale) = dimensions.value
            RewardCard(card(), 0, scale, 0f, true, Modifier.requiredSize(width.dp, height.dp)) {}
        }
        listOf(1f, 1.25f, 1.75f).forEach { scale ->
            listOf(180f to 240f, 179f to 240f, 180f to 239f).forEach { (width, height) ->
                compose.runOnIdle { dimensions.value = Triple(width, height, scale) }
                val header = if (width < 180f || height < 240f) "compact" else "expanded"
                compose.onNodeWithTag("kinetickk.gameplay.choice.1.$header", useUnmergedTree = true).assertIsDisplayed()
                compose.onNodeWithTag("kinetickk.gameplay.choice.1.action", useUnmergedTree = true).assertIsDisplayed()
                val body = compose.onNodeWithTag("kinetickk.gameplay.choice.1.text", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue(body.height > 0f, "Scrollable body remains reachable at $width × $height / $scale")
            }
        }
    }

    @Test
    fun draggingVisualHeaderDoesNotActivateReward() {
        var selections = 0
        setEnglishContent { RewardCard(card(), 0, 1f, 0f, true, Modifier.requiredSize(250.dp, 270.dp)) { selections++ } }
        compose.onNodeWithTag("kinetickk.gameplay.choice.1").performTouchInput {
            swipe(Offset(30f, 30f), Offset(200f, 30f), durationMillis = 300)
        }
        compose.runOnIdle { assertEquals(0, selections) }
    }

    @Test
    fun headerVerticalDragAndWheelScrollTheTextWithNoSelection() {
        var selections = 0
        setEnglishContent { RewardCard(card(), 0, 1f, 0f, true, Modifier.requiredSize(250.dp, 270.dp)) { selections++ } }
        val text = compose.onNodeWithTag("kinetickk.gameplay.choice.1.text", useUnmergedTree = true)
        fun position() = text.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(0f, position())
        compose.onNodeWithTag("kinetickk.gameplay.choice.1").performTouchInput {
            swipe(Offset(30f, 70f), Offset(30f, 10f), durationMillis = 300)
        }
        val afterDrag = position()
        assertTrue(afterDrag > 0f)
        compose.onNodeWithTag("kinetickk.gameplay.choice.1").performMouseInput {
            moveTo(Offset(40f, 30f))
            scroll(100f)
        }
        assertTrue(position() > afterDrag)
        compose.runOnIdle { assertEquals(0, selections) }
    }

    @Test
    fun mouseDragsScrollCardTextWithoutActivatingIt() {
        var selections = 0
        setEnglishContent { RewardCard(card(), 0, 1f, 0f, true, Modifier.requiredSize(250.dp, 270.dp)) { selections++ } }
        val card = compose.onNodeWithTag("kinetickk.gameplay.choice.1")
        val text = compose.onNodeWithTag("kinetickk.gameplay.choice.1.text", useUnmergedTree = true)
        fun position() = text.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        card.performMouseInput {
            moveTo(Offset(30f, 30f))
            press()
            moveTo(Offset(200f, 30f))
            release()
        }
        compose.runOnIdle { assertEquals(0, selections) }
        assertEquals(0f, position())
        card.performMouseInput {
            moveTo(Offset(30f, 70f))
            press()
            moveTo(Offset(30f, 10f))
            release()
        }
        val afterHeader = position()
        assertTrue(afterHeader > 0f)
        text.performMouseInput {
            moveTo(Offset(30f, 100f))
            press()
            moveTo(Offset(30f, 30f))
            release()
        }
        assertTrue(position() > afterHeader)
        compose.runOnIdle { assertEquals(0, selections) }
    }

    @Test
    fun threeAndFourChoicesRetainSharedRectanglesAndPinnedActionsAcrossLayoutsAndScales() {
        val scenario = mutableStateOf(Scenario(900f, 600f, 3, 1f))
        setEnglishContent {
            val value = scenario.value
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(value.width.dp, value.height.dp)) {
                    RewardContent(
                        presentation = RewardPresentation(
                            "CHOOSE AN ARTIFACT", "TIME IS SUSPENDED", List(value.count) { card() },
                            Color.White, Color(0xFFA96CFF), 3,
                        ),
                        layout = choiceLayoutGeometry(value.width, value.height, 1f, value.count, true),
                        screenWidth = value.width,
                        uiScale = 1f,
                        textScale = value.scale,
                        renderTime = 0f,
                        enabled = true,
                        onSelect = {},
                        onReroll = {},
                    )
                }
            }
        }
        listOf(900f to 600f, 360f to 720f, 780f to 360f).forEach { (width, height) ->
            listOf(3, 4).forEach { count ->
                listOf(1f, 1.25f, 1.75f).forEach { scale ->
                    compose.runOnIdle { scenario.value = Scenario(width, height, count, scale) }
                    compose.onAllNodes(hasClickAction()).assertCountEquals(count + 1)
                    val origin = compose.onNodeWithTag("kinetickk.gameplay.rewards").fetchSemanticsNode().boundsInRoot.topLeft
                    val layout = choiceLayoutGeometry(width, height, 1f, count, true)
                    layout.cards.forEachIndexed { index, bounds ->
                        val card = compose.onNodeWithTag("kinetickk.gameplay.choice.${index + 1}").fetchSemanticsNode().boundsInRoot
                        assertEquals(bounds.left, card.left - origin.x, 1f)
                        assertEquals(bounds.top, card.top - origin.y, 1f)
                        assertEquals(bounds.width, card.width, 1f)
                        assertEquals(bounds.height, card.height, 1f)
                        compose.onNodeWithTag("kinetickk.gameplay.choice.${index + 1}.action", useUnmergedTree = true).assertIsDisplayed()
                    }
                    compose.onNodeWithTag("kinetickk.gameplay.reroll").assertIsDisplayed()
                    capture("rewards-${width.toInt()}x${height.toInt()}-$count-$scale")
                }
            }
        }
    }

    private fun setEnglishContent(content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.English, content = content)
        }
    }

    private fun capture(name: String) {
        val directory = System.getenv("KINETICKK_RENDER_CAPTURE_DIR") ?: return
        val bitmap = compose.onNodeWithTag("kinetickk.gameplay.rewards").captureToImage()
        val pixels = bitmap.toPixelMap()
        val image = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) image.setRGB(x, y, pixels[x, y].toArgb())
        File(directory).mkdirs()
        ImageIO.write(image, "png", File(directory, "$name.png"))
    }

    private data class Scenario(val width: Float, val height: Float, val count: Int, val scale: Float)

    private fun card() = RewardCardPresentation(
        choice = ChoiceOption(ChoiceType.ITEM, "A very long artifact name with every word preserved", "Full description", "LEGENDARY"),
        accent = Color(0xFFFFD45B),
        tag = "LEGENDARY // COMPLETE ARTIFACT INFORMATION",
        descriptions = List(12) { "Description paragraph $it includes all modifiers and the original catalog text without clipping." },
        operation = "FINAL OPERATION · complete text",
    )
}
