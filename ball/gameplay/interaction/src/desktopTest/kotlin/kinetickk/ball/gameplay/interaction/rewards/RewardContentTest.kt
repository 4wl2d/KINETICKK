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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.*
import kinetickk.foundation.collections.immutableListOf
import kinetickk.ball.gameplay.interaction.layout.choiceLayoutGeometry
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.render.TotemAction
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
    fun rewardDealSelectionAndLiftFollowThePointerOnTheNextFrame() {
        val selections = mutableListOf<Int>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(1000.dp, 700.dp)) {
                    RewardContent(
                        RewardPresentation("Upgrade", "", conciseCards(), Color.White, Color.Magenta, 2),
                        choiceLayoutGeometry(1000f, 700f, 1f, 3, true), 1000f, 1f, 1f, 0f, true,
                        onSelect = { selections += it }, onReroll = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(RewardEntranceMs.toLong() + 64)
        compose.mainClock.autoAdvance = false
        fun pixels(): IntArray {
            val image = compose.onNodeWithTag("kinetickk.gameplay.rewards").captureToImage()
            val map = image.toPixelMap()
            return IntArray(image.width * image.height) { map[it % image.width, it / image.width].toArgb() }
        }
        for (index in listOf(1, 3, 2, 3, 1)) {
            compose.onNodeWithTag("kinetickk.gameplay.choice.$index").performMouseInput { moveTo(center) }
            compose.mainClock.advanceTimeByFrame()
            val entered = pixels()
            compose.mainClock.advanceTimeBy(260)
            val settled = pixels()
            assertEquals(0, entered.indices.count { entered[it] != settled[it] }, "card $index must finish selecting and lifting on the next frame")
        }
        assertTrue(selections.isEmpty(), "Hover must not accept a reward")
        compose.onNodeWithTag("kinetickk.gameplay.take").performClick()
        assertEquals(listOf(0), selections)
    }

    @Test
    fun cardHoverHighlightIsCompleteOnTheNextFrame() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                RewardCard(card(), 0, 1f, 0f, true, Modifier.requiredSize(240.dp, 360.dp), onSelect = {})
            }
        }
        val node = compose.onNodeWithTag("kinetickk.gameplay.choice.1")
        fun pixels(): IntArray {
            val image = node.captureToImage()
            val map = image.toPixelMap()
            return IntArray(image.width * image.height) { map[it % image.width, it / image.width].toArgb() }
        }
        val idle = pixels()
        compose.mainClock.autoAdvance = false
        node.performMouseInput { moveTo(center) }
        compose.mainClock.advanceTimeByFrame()
        val entered = pixels()
        compose.mainClock.advanceTimeBy(260)
        val settled = pixels()
        assertTrue(idle.indices.any { idle[it] != settled[it] }, "hover must visibly highlight the card")
        assertEquals(0, entered.indices.count { entered[it] != settled[it] }, "the first hover frame must show the complete highlight")
        node.performMouseInput { exit(Offset(-10f, -10f)) }
        compose.mainClock.advanceTimeByFrame()
        val exited = pixels()
        assertEquals(0, idle.indices.count { idle[it] != exited[it] }, "leaving must clear the highlight on the next frame")
    }

    @Test
    fun firstDiscoveryBadgeIsVisibleAndDoesNotCreateASecondSelectionTarget() {
        val fresh = mutableStateOf(true)
        var selections = 0
        compose.setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.Russian, LocalDensity provides Density(1f)) {
                RewardCard(card().copy(isNewDiscovery = fresh.value), 0, 1.25f, 0f, true,
                    Modifier.requiredSize(200.dp, 300.dp), onSelect = { selections++ })
            }
        }
        compose.onNodeWithTag("kinetickk.gameplay.choice.1.new", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onNodeWithTag("kinetickk.gameplay.choice.1").performClick()
        compose.runOnIdle { assertEquals(1, selections); fresh.value = false }
        compose.onNodeWithTag("kinetickk.gameplay.choice.1.new", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun statChangesShowBothValuesWithoutArrowsAndTakeActsOnTheHoveredOrFocusedCard() {
        val selections = mutableListOf<Int>()
        val cards = mutableStateOf(conciseCards())
        compose.setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.Russian, LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(1000.dp, 700.dp)) {
                    RewardContent(
                        RewardPresentation("Улучшение", "", cards.value, Color.White, Color.Magenta, 2),
                        choiceLayoutGeometry(1000f, 700f, 1f, 3, true), 1000f, 1f, 1f, 0f, true,
                        onSelect = { selections += it }, onReroll = { cards.value = listOf(card(), card(), card()) },
                    )
                }
            }
        }
        // Before value muted and after value in the accent: two separate values, never "A → B".
        compose.onNodeWithText("7,2", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("1,5%", useUnmergedTree = true).assertIsDisplayed()
        compose.assertNoForbiddenGlyphs()
        capture("concise-rewards-russian")
        // The middle card is lifted first; its interactions are listed without selecting it.
        compose.onNodeWithTag("kinetickk.gameplay.reward-connections", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Эхо массы", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("kinetickk.gameplay.choice.1").performMouseInput { moveTo(center) }
        compose.onNodeWithText("Эхо массы", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { assertTrue(selections.isEmpty()) }
        compose.onNodeWithTag("kinetickk.gameplay.choice.3").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText("Сбор данных", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { assertTrue(selections.isEmpty()) }
        capture("concise-rewards-focus")
        compose.onNodeWithTag("kinetickk.gameplay.take").performClick()
        compose.runOnIdle { assertEquals(listOf(2), selections) }
        compose.onNodeWithTag("kinetickk.gameplay.reroll").performClick()
        compose.onNodeWithTag("kinetickk.gameplay.reward-connections").assertDoesNotExist()
    }

    @Test
    fun conciseCardsKeepEffectsVisibleAtNormalSizeAndReadableInCompactLayouts() {
        val scenario = mutableStateOf(Scenario(900f, 600f, 3, 1f))
        compose.setContent {
            val value = scenario.value
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.Russian, LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(value.width.dp, value.height.dp)) {
                    RewardContent(
                        RewardPresentation("Улучшение", "", conciseCards(), Color.White, Color.Magenta, 2),
                        choiceLayoutGeometry(value.width, value.height, 1f, 3, true), value.width, 1f, value.scale, 0f, true,
                        onSelect = {}, onReroll = {},
                    )
                }
            }
        }
        compose.onNodeWithText("2,84 с", useUnmergedTree = true).assertIsDisplayed()
        listOf(360f to 720f, 780f to 360f).forEach { (width, height) ->
            listOf(1f, 1.75f).forEach { scale ->
                compose.runOnIdle { scenario.value = Scenario(width, height, 3, scale) }
                // Dealt cards are rotated; scroll the card body by gestures (the test helper
                // performScrollTo assumes an axis-aligned viewport) until the value shows.
                revealInCard("2,84 с", "kinetickk.gameplay.choice.3.text")
                compose.onNodeWithTag("kinetickk.gameplay.take").assertIsDisplayed()
                compose.onNodeWithTag("kinetickk.gameplay.reward-build").assertIsDisplayed()
                capture("concise-rewards-${width.toInt()}x${height.toInt()}-$scale")
            }
        }
    }

    @Test
    fun changingLanguageUpdatesTheFooterWithoutRecreatingTheCards() {
        val language = mutableStateOf(AppLanguage.Russian)
        val selections = mutableListOf<Int>()
        compose.setContent {
            CompositionLocalProvider(LocalAppLanguage provides language.value, LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(1000.dp, 700.dp)) {
                    RewardContent(
                        RewardPresentation("Upgrade", "", listOf(card(), card(), card()), Color.White, Color.Magenta, 2),
                        choiceLayoutGeometry(1000f, 700f, 1f, 3, false), 1000f, 1f, 1f, 0f, true,
                        onSelect = { selections += it }, onReroll = {},
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("Взять").assertIsDisplayed()
        compose.onNodeWithContentDescription("Сборка").assertIsDisplayed()
        compose.runOnIdle { language.value = AppLanguage.English }
        compose.onNodeWithContentDescription("Take").assertIsDisplayed()
        compose.onNodeWithContentDescription("Build").assertIsDisplayed()
        compose.onNodeWithTag("kinetickk.gameplay.reroll").assertDoesNotExist()
        compose.onNodeWithTag("kinetickk.gameplay.choice.1").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(0), selections) }
    }

    @Test
    fun buildOpensTheCodexAndNeverTakesAChoice() {
        var builds = 0
        val selections = mutableListOf<Int>()
        compose.setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.English, LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(1000.dp, 700.dp)) {
                    RewardContent(
                        RewardPresentation("Upgrade", "", listOf(card(), card()), Color.White, Color.Magenta, 1),
                        choiceLayoutGeometry(1000f, 700f, 1f, 2, true), 1000f, 1f, 1f, 0f, true,
                        onSelect = { selections += it }, onReroll = {}, onBuild = { builds++ },
                    )
                }
            }
        }
        compose.onNodeWithTag("kinetickk.gameplay.reward-build").performClick()
        compose.runOnIdle { assertEquals(1, builds); assertTrue(selections.isEmpty()) }
    }

    @Test
    fun clickSelectsOnceAndOnlyVisibleCardHasActivation() {
        var selections = 0
        setEnglishContent { RewardCard(card(), 0, 1f, 0f, true, Modifier.requiredSize(250.dp, 270.dp)) { selections++ } }

        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onNodeWithTag("kinetickk.gameplay.choice.1").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, selections) }
        compose.onNodeWithTag("kinetickk.gameplay.choice.1.text", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun draggingAndWheelScrollFullTextWithoutSelectingAndTheHeaderStaysPinned() {
        var selections = 0
        setEnglishContent { RewardCard(card(), 0, 1.75f, 0f, true, Modifier.requiredSize(178.dp, 220.dp)) { selections++ } }
        val header = compose.onNodeWithTag("kinetickk.gameplay.choice.1.compact", useUnmergedTree = true)
        val headerTop = header.fetchSemanticsNode().boundsInRoot.top
        val text = compose.onNodeWithTag("kinetickk.gameplay.choice.1.text", useUnmergedTree = true)
        text.performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(0, selections) }
        text.performMouseInput { moveTo(center); scroll(100f) }
        compose.runOnIdle { assertEquals(0, selections) }
        compose.onNodeWithText("Description paragraph 11 includes all modifiers and the original catalog text without clipping.",
            useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        assertEquals(headerTop, header.fetchSemanticsNode().boundsInRoot.top)
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
            // 300 × 419 is the board card of a 1280 × 720 window: it keeps the board layout.
            listOf(180f to 240f, 179f to 240f, 180f to 239f, 220f to 256f, 300f to 450f, 300f to 419f).forEach { (width, height) ->
                compose.runOnIdle { dimensions.value = Triple(width, height, scale) }
                // Squat cards (Mobile-LevelUp 220 × 256) use the compact header too.
                val compact = rewardCardUsesCompactLayout(width, height)
                assertEquals(width < 180f || height < 240f || height < width * 1.3f, compact)
                if (width == 300f && height == 419f) assertTrue(!compact, "A short board card keeps the board layout")
                val header = if (compact) "compact" else "expanded"
                compose.onNodeWithTag("kinetickk.gameplay.choice.1.$header", useUnmergedTree = true).assertIsDisplayed()
                val body = compose.onNodeWithTag("kinetickk.gameplay.choice.1.text", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue(body.height > 0f, "Scrollable body remains reachable at $width × $height / $scale")
            }
        }
    }

    @Test
    fun theLayoutModeDecidesThePhoneCardLayoutNotTheCardShape() {
        // A 1280 × 720 board deals 300 × 419 cards (and a 1366 × 768 laptop window about 300 × 390):
        // the board layout, scaled. The four-card landscape phone deal (195 × 254) stays compact.
        assertTrue(!rewardCardUsesCompactLayout(300f, 419f))
        assertTrue(rewardCardUsesCompactLayout(195f, 254f, phoneDeal = true))
        val board = rewardFixtureModel(choices = listOf(itemChoice(0), itemChoice(1), itemChoice(2)), width = 1280f, height = 720f)
        val phone = rewardFixtureModel(choices = List(4) { itemChoice(it) }, width = 844f, height = 390f)
        val shown = mutableStateOf(board)
        compose.setContent {
            val model = shown.value
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.English, LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(model.screenWidth.dp, model.screenHeight.dp)) {
                    RewardContent(model, choiceLayoutGeometry(model.screenWidth, model.screenHeight, 1f, model.choices.size, model.choicesCanReroll),
                        0f, true, {}, {})
                }
            }
        }
        val layout = choiceLayoutGeometry(1280f, 720f, 1f, 3, true)
        assertTrue(layout.cards.all { it.height < it.width * 1.4f }, "The board cards are squat at 720p (${layout.cards.first()})")
        (1..3).forEach { index ->
            compose.onNodeWithTag("kinetickk.gameplay.choice.$index.expanded", useUnmergedTree = true).assertExists()
        }
        compose.runOnIdle { shown.value = phone }
        (1..4).forEach { index -> compose.onNodeWithTag("kinetickk.gameplay.choice.$index.compact", useUnmergedTree = true).assertExists() }
    }

    @Test
    fun aPhoneRiskCardShowsItsCostWhenItsStatsMakeWayForItsRules() {
        // Overcharged Capacitor trades Cooling −25 % for weapon power: the cost is a striped Cost line on every card size.
        val shown = mutableStateOf(AppLanguage.English)
        compose.setContent {
            val language = shown.value
            val card = rewardFixtureModel(choices = listOf(itemChoice(2))).rewardCardPresentation(itemChoice(2), 0, language)
            CompositionLocalProvider(LocalAppLanguage provides language, LocalDensity provides Density(1f)) {
                RewardCard(card, 0, 1f, 0f, true, Modifier.requiredSize(220.dp, 256.dp), phoneDeal = true) {}
            }
        }
        listOf(AppLanguage.English, AppLanguage.Russian).forEach { language ->
            compose.runOnIdle { shown.value = language }
            compose.onNodeWithTag("kinetickk.gameplay.choice.1.compact", useUnmergedTree = true).assertExists()
            val texts = compose.onAllNodes(androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("kinetickk.gameplay.choice.1.text")) and
                androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
                .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
            assertTrue(texts.any { it.contains("25") && it.contains('−') }, "$language: the cost line shows ($texts)")
            assertTrue(texts.any { it.contains("35") }, "$language: the weapon power gain stays ($texts)")
        }
    }

    @Test
    fun aPhoneActiveCardKeepsItsSlotChipItsActivePartAndItsPassive() {
        // Solar Flare in a free slot: the slot chip (with its key), the effect's summary and numbers and the passive
        // all stay on the phone card, in both languages.
        val shown = mutableStateOf(AppLanguage.English)
        val content = rewardActiveFixtureContent
        val choice = rewardActiveChoice(rewardFixtureActiveBase, slot = 0)
        compose.setContent {
            val language = shown.value
            val card = rewardFixtureModel(content = content, choices = listOf(choice), language = language).rewardCardPresentation(choice, 0, language)
            CompositionLocalProvider(LocalAppLanguage provides language, LocalDensity provides Density(1f)) {
                RewardCard(card, 0, 1f, 0f, true, Modifier.requiredSize(220.dp, 256.dp), phoneDeal = true) {}
            }
        }
        val flare = content.skill(SkillId.SOLAR_FLARE)
        listOf(AppLanguage.English, AppLanguage.Russian).forEach { language ->
            compose.runOnIdle { shown.value = language }
            compose.onNodeWithTag("kinetickk.gameplay.choice.1.compact", useUnmergedTree = true).assertExists()
            fun textsUnder(tag: String) = compose.onAllNodes(androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag(tag)) and
                androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
                .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
            val chips = textsUnder("kinetickk.gameplay.choice.1.chips")
            val slotChip = if (language == AppLanguage.English) "Active Z" else "Актив Z"
            assertTrue(slotChip in chips, "$language: the slot chip shows ($chips)")
            val texts = textsUnder("kinetickk.gameplay.choice.1.text").map { it.replace(' ', ' ').replace("⁠", "") }
            assertTrue(texts.any { it == flare.summary.localizedContent(language) }, "$language: the active summary stays ($texts)")
            val numbers = flare.rankLines(1, language).drop(1).joinToString(", ")
            assertTrue(texts.any { it == numbers }, "$language: the rank's numbers stay ($texts)")
            assertTrue(texts.any { it.equals(if (language == AppLanguage.English) "Passive" else "Пассивный эффект", ignoreCase = true) },
                "$language: the passive part stays ($texts)")
        }
    }

    @Test
    fun aPhoneCardsNewStampStaysAboveItsBandAtEveryTextSize() {
        val card = rewardFixtureModel(choices = listOf(itemChoice(1))).rewardCardPresentation(itemChoice(1), 0)
        check(card.isNewDiscovery)
        val scale = mutableStateOf(1f)
        val size = mutableStateOf(220f to 256f)
        compose.setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.Russian, LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(400.dp, 400.dp)) {
                    Box(Modifier.requiredSize(size.value.first.dp, size.value.second.dp).testTag("card")) {
                        RewardCard(card, 0, scale.value, 0f, true, Modifier.requiredSize(size.value.first.dp, size.value.second.dp), phoneDeal = true) {}
                    }
                }
            }
        }
        // Landscape and portrait phone deals at the default and the largest text size.
        listOf(220f to 256f, 178f to 305f).forEach { dimensions ->
            listOf(1f, 1.4f).forEach { textScale ->
                compose.runOnIdle { size.value = dimensions; scale.value = textScale }
                val cardTop = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.top
                val stamp = compose.onNodeWithTag("kinetickk.gameplay.choice.1.new", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                // The band's labels and pips are centered in its 30 dp: the stamp keeps to the frame above them.
                val u = minOf(dimensions.first / 220f, dimensions.second / 256f).coerceIn(0.6f, 1.25f)
                assertTrue(stamp.bottom <= cardTop + (RewardNewStampOverlapDp + 4f) * u,
                    "Stamp $stamp reaches into the band of a ${dimensions.first} × ${dimensions.second} card at $textScale (top $cardTop)")
            }
        }
    }

    @Test
    fun aBoardActiveCardsNewStampLeavesItsRankPipsInSight() {
        val choice = rewardActiveChoice(rewardFixtureActiveBase, slot = 0)
        val scale = mutableStateOf(1f)
        val language = mutableStateOf(AppLanguage.English)
        compose.setContent {
            CompositionLocalProvider(LocalAppLanguage provides language.value, LocalDensity provides Density(1f)) {
                val card = rewardFixtureModel(content = rewardActiveFixtureContent, choices = listOf(choice), language = language.value)
                    .rewardCardPresentation(choice, 0, language.value).copy(isNewDiscovery = true)
                Box(Modifier.requiredSize(400.dp, 500.dp)) {
                    RewardCard(card, 0, scale.value, 0f, true, Modifier.requiredSize(300.dp, 450.dp)) {}
                }
            }
        }
        // English and the wider Russian stamp, at the default and the largest text size.
        for (lang in AppLanguage.entries) for (textScale in listOf(1f, 1.4f)) {
            compose.runOnIdle { language.value = lang; scale.value = textScale }
            val plate = compose.onNodeWithTag("kinetickk.gameplay.choice.1.expanded", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val stamp = compose.onNodeWithTag("kinetickk.gameplay.choice.1.new", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val chips = compose.onNodeWithTag("kinetickk.gameplay.choice.1.chips", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            // The rank pips drawActiveItemMark draws: 0.78 to 0.85 of the plate down, 0.49 of it wide about its centre.
            val side = plate.minDimension
            val pips = Rect(plate.center.x - side * 0.245f, plate.top + side * 0.78f, plate.center.x + side * 0.245f, plate.top + side * 0.85f)
            val where = "$lang at $textScale: the stamp $stamp"
            assertTrue(!stamp.overlaps(pips), "$where covers the rank pips $pips")
            assertTrue(stamp.top >= plate.top - 0.5f && stamp.right <= chips.left, "$where leaves the plate $plate for the band or the chips $chips")
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
    fun twoToFourChoicesAreDealtAtTheSharedRectanglesWithTheirFooterAcrossLayoutsAndScales() {
        val scenario = mutableStateOf(Scenario(900f, 600f, 3, 1f))
        setEnglishContent {
            val value = scenario.value
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(value.width.dp, value.height.dp)) {
                    RewardContent(
                        presentation = RewardPresentation("Upgrade", "", List(value.count) { card() }, Color.White, Color(0xFFA96CFF), 3),
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
        // By default each size and card count is dealt at one text scale, rotating so every size and every count meets all
        // three (the card rectangles do not depend on the scale); KK_FULL_SWEEPS=1 deals every combination.
        val fullSweep = System.getenv("KK_FULL_SWEEPS") == "1"
        listOf(900f to 600f, 1440f to 810f, 360f to 720f, 780f to 360f).forEachIndexed { sizeIndex, (width, height) ->
            listOf(2, 3, 4).forEachIndexed { countIndex, count ->
                listOf(1f, 1.25f, 1.75f).forEachIndexed { scaleIndex, scale ->
                    if (!fullSweep && scaleIndex != (sizeIndex + countIndex) % 3) return@forEachIndexed
                    compose.runOnIdle { scenario.value = Scenario(width, height, count, scale) }
                    // Every card plus Reroll, Take and Build.
                    compose.onAllNodes(hasClickAction()).assertCountEquals(count + 3)
                    val origin = compose.onNodeWithTag("kinetickk.gameplay.rewards").fetchSemanticsNode().boundsInRoot.topLeft
                    val layout = choiceLayoutGeometry(width, height, 1f, count, true)
                    val lifted = (count - 1) / 2
                    layout.cards.forEachIndexed { index, bounds ->
                        // Cards rest rotated (−4° … +3°) around their centers; the lifted card rises 22 dp.
                        val card = compose.onNodeWithTag("kinetickk.gameplay.choice.${index + 1}").fetchSemanticsNode().boundsInRoot
                        assertEquals(bounds.center.x, card.center.x - origin.x, 1.5f)
                        assertEquals(bounds.center.y - if (index == lifted) 22f else 0f, card.center.y - origin.y, 1.5f)
                        assertTrue(card.width >= bounds.width - 1f && card.height >= bounds.height - 1f)
                    }
                    listOf("reroll", "take", "reward-build").forEach { tag ->
                        compose.onNodeWithTag("kinetickk.gameplay.$tag").assertIsDisplayed()
                    }
                    capture("rewards-${width.toInt()}x${height.toInt()}-$count-$scale")
                }
            }
        }
    }

    @Test
    fun relicReplacementArmsNothingUntilASlotIsLiftedAndThenTakesThatSlot() {
        val selections = mutableListOf<Int>()
        val relics = listOf(EquippedRelic(RelicId.PERPETUAL_VECTOR, 2), EquippedRelic(RelicId.GYRE_ENGINE, 1),
            EquippedRelic(RelicId.FURNACE_COVENANT, 1), EquippedRelic(RelicId.AEGIS_INVERSION, 1))
        val model = rewardFixtureModel(
            choiceType = ChoiceType.RELIC_BIND, relics = relics, rerolls = 0,
            choices = relics.mapIndexed { index, equipped ->
                ChoiceOption(ChoiceType.RELIC_BIND, "Replace ${equipped.id}", "Break slot ${index + 1}.", "REPLACE",
                    relicId = RelicId.EVENT_HORIZON, relicAction = RelicChoiceAction.REPLACE, relicSlot = index)
            },
        )
        showModel(model, 1000f, 700f) { selections += it }
        compose.onNodeWithTag("kinetickk.gameplay.take").assertIsNotEnabled()
        compose.onNodeWithTag("kinetickk.gameplay.rewards.incoming").assertIsDisplayed()
        assertRewardPreviewIsImmediate(3)
        compose.onNodeWithTag("kinetickk.gameplay.take").assertIsEnabled()
        compose.runOnIdle { assertTrue(selections.isEmpty()) }
        compose.onNodeWithTag("kinetickk.gameplay.take").performClick()
        compose.runOnIdle { assertEquals(listOf(2), selections) }
        // Clicking a slot still takes it immediately, as the host accepts choices today.
        compose.onNodeWithTag("kinetickk.gameplay.choice.1").performClick()
        compose.runOnIdle { assertEquals(listOf(2, 0), selections) }
        compose.assertNoForbiddenGlyphs()
    }

    @Test
    fun totemOfferingsAreRowsWithMasteryRulesBehindTheInfoButtonAndNoReroll() {
        val selections = mutableListOf<Int>()
        val model = rewardFixtureModel(
            choiceType = ChoiceType.TOTEM, weaponLevel = 5,
            choices = listOf(
                ChoiceOption(ChoiceType.TOTEM, "Amplify Flux Wake", "Advance.", "RESONANT", weaponId = WeaponId.FLUX_WAKE,
                    totemAction = TotemAction.AMPLIFY_CURRENT),
                ChoiceOption(ChoiceType.TOTEM, "Change weapon", "Recalibrate.", "RECALIBRATE", totemAction = TotemAction.CHANGE_WEAPON),
            ),
        )
        showModel(model, 1000f, 700f) { selections += it }
        compose.onNodeWithTag("kinetickk.gameplay.reroll").assertDoesNotExist()
        compose.onNodeWithContentDescription("Weapon level stays for the whole run, even when you change weapon. Mastery milestones: Lvl 3, Lvl 6, Lvl 10.")
            .assertIsDisplayed()
        compose.onNodeWithText("Flux Wake", substring = true, ignoreCase = true, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("kinetickk.gameplay.choice.2").performClick()
        compose.runOnIdle { assertEquals(listOf(1), selections) }
        // Hovering a row lifts it; Take then accepts that row through the same choice path.
        assertRewardPreviewIsImmediate(2)
        assertRewardPreviewIsImmediate(1)
        compose.onNodeWithTag("kinetickk.gameplay.take").performClick()
        compose.runOnIdle { assertEquals(listOf(1, 0), selections) }
        compose.assertNoForbiddenGlyphs()
    }

    @Test
    fun totemAndRelicChoicesKeepPhoneTouchTargetsInsideTheViewport() {
        val scenario = mutableStateOf<GameplayRenderModel?>(null)
        val size = mutableStateOf(390f to 844f)
        compose.setContent {
            val model = scenario.value ?: return@setContent
            val (width, height) = size.value
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.Russian, LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(width.dp, height.dp)) {
                    RewardContent(model, choiceLayoutGeometry(width, height, 1f, model.choices.size, model.choicesCanReroll), 0f, true, {}, {})
                }
            }
        }
        val relics = listOf(EquippedRelic(RelicId.PERPETUAL_VECTOR, 2), EquippedRelic(RelicId.GYRE_ENGINE, 1), EquippedRelic(RelicId.FURNACE_COVENANT, 1))
        val models = listOf(
            rewardFixtureModel(choiceType = ChoiceType.TOTEM, choices = listOf(
                ChoiceOption(ChoiceType.TOTEM, "Amplify Flux Wake", "Advance.", "", weaponId = WeaponId.FLUX_WAKE, totemAction = TotemAction.AMPLIFY_CURRENT),
                ChoiceOption(ChoiceType.WEAPON, "Arc Coil", "Chains.", "CHANGE", weaponId = WeaponId.ARC_COIL),
                ChoiceOption(ChoiceType.WEAPON, "Gravity Mines", "Pulls.", "CHANGE", weaponId = WeaponId.GRAVITY_MINES),
            ), language = AppLanguage.Russian),
            rewardFixtureModel(choiceType = ChoiceType.RELIC_BIND, relics = relics, language = AppLanguage.Russian,
                choices = relics.mapIndexed { index, equipped ->
                    ChoiceOption(ChoiceType.RELIC_BIND, "Meld ${equipped.id}", "Meld.", "", relicId = equipped.id,
                        relicAction = RelicChoiceAction.MELD_TARGET, relicSlot = index)
                }),
        )
        listOf(390f to 844f, 844f to 390f, 1440f to 810f).forEach { dimensions ->
            models.forEach { model ->
                compose.runOnIdle { size.value = dimensions; scenario.value = model }
                val (width, height) = dimensions
                val targets = model.choices.indices.map { "kinetickk.gameplay.choice.${it + 1}" } + "kinetickk.gameplay.take"
                targets.forEach { tag ->
                    val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                    assertTrue(bounds.left >= -1f && bounds.top >= -1f && bounds.right <= width + 1f && bounds.bottom <= height + 1f,
                        "$tag inside ${width}x$height: $bounds")
                    // Phones need 48 dp targets; the desktop board keeps its 38 dp small buttons.
                    val minimum = if (width >= 1000f) 38f else 48f
                    assertTrue(bounds.width >= minimum && bounds.height >= minimum, "$tag touch target at ${width}x$height: $bounds")
                }
            }
        }
    }

    private fun assertRewardPreviewIsImmediate(index: Int) {
        compose.mainClock.advanceTimeBy(RewardEntranceMs.toLong() + 64)
        compose.mainClock.autoAdvance = false
        fun pixels(): IntArray {
            val image = compose.onNodeWithTag("kinetickk.gameplay.rewards").captureToImage()
            val map = image.toPixelMap()
            return IntArray(image.width * image.height) { map[it % image.width, it / image.width].toArgb() }
        }
        compose.onNodeWithTag("kinetickk.gameplay.choice.$index").performMouseInput { moveTo(center) }
        compose.mainClock.advanceTimeByFrame()
        val entered = pixels()
        compose.mainClock.advanceTimeBy(260)
        val settled = pixels()
        assertEquals(0, entered.indices.count { entered[it] != settled[it] }, "choice $index must show its selected state on the next frame")
        compose.mainClock.autoAdvance = true
    }

    private fun revealInCard(text: String, bodyTag: String) {
        val body = compose.onNodeWithTag(bodyTag, useUnmergedTree = true)
        val target = compose.onNodeWithText(text, useUnmergedTree = true)
        repeat(8) {
            val viewport = body.fetchSemanticsNode().boundsInRoot
            val bounds = target.fetchSemanticsNode().boundsInRoot
            if (bounds.top >= viewport.top - 2f && bounds.bottom <= viewport.bottom + 2f) return
            body.performTouchInput { swipeUp() }
        }
        target.assertIsDisplayed()
        val viewport = body.fetchSemanticsNode().boundsInRoot
        val bounds = target.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.bottom <= viewport.bottom + 2f, "$text is reachable by scrolling the card body")
    }

    private fun showModel(model: GameplayRenderModel, width: Float, height: Float, onSelect: (Int) -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.English, LocalDensity provides Density(1f)) {
                Box(Modifier.requiredSize(width.dp, height.dp)) {
                    RewardContent(model, choiceLayoutGeometry(width, height, 1f, model.choices.size, model.choicesCanReroll), 0f, true, onSelect, {})
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

    private fun conciseCards(): List<RewardCardPresentation> {
        val names = listOf("Эгида «Память»", "Демпфер «Гравитация»", "Сборщик «Эхо»")
        val effects = listOf(ItemEffect.SHIELD_CAPACITY to ItemEffect.DATA_GAIN, ItemEffect.DAMAGE_REDUCTION to ItemEffect.MASS, ItemEffect.PICKUP_RADIUS to ItemEffect.COMBO_WINDOW)
        val tags = listOf(BuildTag.BULWARK, BuildTag.ANCHOR, BuildTag.FORTUNE)
        val changes = listOf(
            listOf(RewardStatPresentation("Щит", "0", "7,2", true), RewardStatPresentation("Получение данных", "1×", "1,02×", true)),
            listOf(RewardStatPresentation("Снижение урона", "0", "1,5%", true), RewardStatPresentation("Масса", "1", "1,02", true)),
            listOf(RewardStatPresentation("Радиус сбора", "150", "163,96", true), RewardStatPresentation("Окно комбо", "2,8 с", "2,84 с", true)),
        )
        return names.mapIndexed { index, name ->
            val item = ItemDefinition(80 + index, name, "Catalog flavor must stay outside reward cards", ItemRarity.COMMON,
                immutableListOf(tags[index]),
                stats = immutableListOf(StatDelta(effects[index].first, 1f), StatDelta(effects[index].second, 1f)), maxStacks = 3, unlockLevel = 1)
            RewardCardPresentation(
                ChoiceOption(ChoiceType.ITEM, name, item.description, "ОБЫЧНЫЙ", itemId = item.id),
                Color(0xFF94A0BC), "ОБЫЧНЫЙ", emptyList(), "", item = item, itemStack = 1,
                relicPolicy = RelicPolicy(4, 3), changes = changes[index], bandEnd = "Копии 1/3", connections = when (index) {
                    1 -> listOf(RewardConnection("Ядро", "Усиливает", core = CoreShape.entries.first()), RewardConnection("Эхо массы", "Усиливает",
                        relic = rewardFixtureContent.relic(RelicId.NEUTRON_HEART)))
                    2 -> listOf(RewardConnection("Ядро", "Усиливает", core = CoreShape.entries.first()),
                        RewardConnection("Сбор данных", "Усиливает", item = rewardFixtureContent.item(4)))
                    else -> listOf(RewardConnection("Ядро", "Усиливает", core = CoreShape.entries.first()))
                },
            )
        }
    }

    private fun card() = RewardCardPresentation(
        choice = ChoiceOption(ChoiceType.ITEM, "A very long artifact name with every word preserved", "Full description", "LEGENDARY"),
        accent = Color(0xFFFFD45B),
        tag = "LEGENDARY",
        descriptions = List(12) { "Description paragraph $it includes all modifiers and the original catalog text without clipping." },
        operation = "",
        rank = 5,
    )
}

/** SPEC §2: no separator dots, arrows or section marks in any rendered text. */
internal fun SemanticsNodeInteractionsProvider.assertNoForbiddenGlyphs() {
    val forbidden = charArrayOf('·', '→', '←', '↑', '↓', '↵', '›', '‹', '▶', '◀', '◇', '§')
    onAllNodes(isRoot()).fetchSemanticsNodes().forEach { root ->
        fun visit(node: androidx.compose.ui.semantics.SemanticsNode) {
            val texts = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                listOfNotNull(node.config.getOrNull(SemanticsProperties.StateDescription))
            texts.forEach { text -> assertTrue(text.none { it in forbidden }, "Forbidden glyph in \"$text\"") }
            node.children.forEach(::visit)
        }
        visit(root)
    }
}
