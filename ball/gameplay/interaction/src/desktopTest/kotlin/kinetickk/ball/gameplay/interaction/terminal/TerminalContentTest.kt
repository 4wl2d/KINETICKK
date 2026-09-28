// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.terminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.localizedContent
import kinetickk.foundation.common.localization.text
import kinetickk.ball.gameplay.interaction.input.GameplayInput
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.KkIcon
import kinetickk.ball.gameplay.interaction.rewards.assertTextFitsWithoutBreakingWords
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kinetickk.ball.gameplay.interaction.rewards.assertNoForbiddenGlyphs
import kinetickk.ball.gameplay.interaction.rewards.OverlayButtonProbe
import kinetickk.ball.gameplay.interaction.rewards.rewardFixtureModel
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TerminalContentTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun desktopReportKeepsActionsVisibleAtMaximumTextSizeWithoutScrolling() {
        compose.setContent {
            // Fit a 1280 dp layout into the offscreen test host without cropping the capture.
            CompositionLocalProvider(LocalDensity provides Density(0.75f), LocalAppLanguage provides AppLanguage.Russian) {
                Box(Modifier.requiredSize(1280.dp, 720.dp).testTag("terminal-capture")) {
                    TerminalContent(report(AppLanguage.Russian), 1.75f, false, 3f, true) {}
                }
            }
        }
        compose.onNodeWithTag("kinetickk.gameplay.restart").assertIsDisplayed()
        compose.onNodeWithTag("kinetickk.gameplay.exit").assertIsDisplayed()
        capture("death-report-desktop-large-actions")
    }

    @Test
    fun deathReportWaitsForCoreRuptureAndRevealsStatisticsFromTopToBottom() {
        val elapsed = mutableStateOf(0f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides AppLanguage.English) {
                Box(Modifier.requiredSize(1000.dp, 720.dp).testTag("terminal-capture")) {
                    TerminalContent(report(), 1.25f, false, elapsed.value, true) {}
                }
            }
        }
        compose.onNodeWithTag("kinetickk.gameplay.results").assertDoesNotExist()
        compose.runOnIdle { elapsed.value = 0.8f }
        compose.onNodeWithTag("kinetickk.gameplay.restart").assertIsNotEnabled()
        compose.onNodeWithTag("kinetickk.gameplay.stat.LevelReached").assertDoesNotExist()
        capture("death-report-entering")
        compose.runOnIdle { elapsed.value = 1.05f }
        compose.onNodeWithTag("kinetickk.gameplay.restart").assertIsEnabled()
        compose.onNodeWithTag("kinetickk.gameplay.stat.EnemiesDestroyed").assertIsDisplayed()
        compose.onNodeWithTag("kinetickk.gameplay.stat.LevelReached").assertDoesNotExist()
        capture("death-report-cascade")
        compose.runOnIdle { elapsed.value = 3f }
        compose.onNodeWithTag("kinetickk.gameplay.stat.LevelReached").performScrollTo().assertIsDisplayed()
        capture("death-report-complete-en")
        assertFalse(terminalActionsReady(0.5f, false))
        assertTrue(terminalActionsReady(1.01f, false))
    }

    @Test
    fun mirroredPanelsKeepNativeActionsAndKeyboardActivationSingle() {
        val onLeft = mutableStateOf(false)
        val actions = mutableListOf<GameplayInput>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides AppLanguage.Russian) {
                Box(Modifier.requiredSize(1000.dp, 720.dp).testTag("terminal-capture")) {
                    TerminalContent(report(AppLanguage.Russian), 1.25f, onLeft.value, 3f, true, actions::add)
                }
            }
        }
        assertPanelOrder(statisticsOnLeft = false)
        capture("death-report-complete-ru")
        compose.onNodeWithTag("kinetickk.gameplay.restart").performMouseInput { click() }
        compose.runOnIdle { assertEquals(listOf<GameplayInput>(GameplayInput.RestartRun), actions) }
        compose.runOnIdle { onLeft.value = true }
        assertPanelOrder(statisticsOnLeft = true)
        compose.onNodeWithTag("kinetickk.gameplay.exit").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("kinetickk.gameplay.exit").performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        compose.runOnIdle { assertEquals(listOf<GameplayInput>(GameplayInput.RestartRun, GameplayInput.ExitToHome), actions) }
        capture("death-report-mirrored-ru")
    }

    @Test
    fun portraitAndShortLandscapeKeepLargeTextAndLongStatisticsScrollableWithoutRestarting() {
        val dimensions = mutableStateOf(390 to 720)
        val actions = mutableListOf<GameplayInput>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides AppLanguage.Russian) {
                Box(Modifier.requiredSize(dimensions.value.first.dp, dimensions.value.second.dp).testTag("terminal-capture")) {
                    TerminalContent(report(AppLanguage.Russian), 1.75f, true, 3f, true, actions::add)
                }
            }
        }
        for (size in listOf(390 to 720, 780 to 360)) {
            compose.runOnIdle { dimensions.value = size }
            val portrait = size.first < size.second
            // Portrait scrolls one column; short landscape pins the actions under the scrolling summary.
            val restart = compose.onNodeWithTag("kinetickk.gameplay.restart")
            if (portrait) restart.performScrollTo()
            restart.assertIsDisplayed()
            capture("death-report-${size.first}x${size.second}-large-actions")
            compose.onNodeWithTag("kinetickk.gameplay.results").performTouchInput { swipeUp() }
            compose.runOnIdle { assertTrue(actions.isEmpty()) }
            compose.onNodeWithTag("kinetickk.gameplay.stat.LevelReached").performScrollTo().assertIsDisplayed()
            capture("death-report-${size.first}x${size.second}-large-stats")
            val exit = compose.onNodeWithTag("kinetickk.gameplay.exit")
            if (portrait) exit.performScrollTo()
            exit.assertIsDisplayed()
            val bounds = exit.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.height >= 48f, "The small Menu action keeps a touch target on phones")
        }
    }

    @Test
    fun phoneLandscapeKeepsBankAndBuildClearOfThePinnedActions() {
        for (language in AppLanguage.entries) for (victory in listOf(true, false)) for (textScale in listOf(1f, 1.25f, 1.75f)) {
            runDesktopComposeUiTest(844, 390) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides language) {
                        Box(Modifier.requiredSize(844.dp, 390.dp)) {
                            TerminalContent(report(language).copy(victory = victory, bank = "1 284", weaponIcon = KkIcon.WEAPONS_FLUX_WAKE,
                                weaponLevel = "8"), textScale, false, 3f, true) {}
                        }
                    }
                }
                val scene = "$language victory $victory ${textScale}x"
                val actions = listOfNotNull("restart", "exit", "rebirth".takeIf { victory }).map { tag ->
                    onNodeWithTag("kinetickk.gameplay.$tag").fetchSemanticsNode().boundsInRoot
                }
                // The summary's own viewport, at rest: Bank and Build must be inside it without scrolling.
                val summary = onNodeWithTag("kinetickk.gameplay.results.summary")
                val viewport = summary.fetchSemanticsNode().boundsInRoot
                assertEquals(0f, summary.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), "$scene at rest")
                listOf("kinetickk.gameplay.results.bank-info", "kinetickk.gameplay.results.build").forEach { tag ->
                    // Unclipped: a row scrolled out of the viewport keeps its real position.
                    val bounds = onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
                        .let { Rect(it.left.value, it.top.value, it.right.value, it.bottom.value) }
                    assertTrue(bounds.height > 0f && bounds.top >= viewport.top - 0.5f && bounds.bottom <= viewport.bottom + 0.5f &&
                        bounds.left >= viewport.left - 0.5f && bounds.right <= viewport.right + 0.5f,
                        "$tag inside the summary viewport without scrolling ($scene): $bounds in $viewport")
                    actions.forEach { action -> assertFalse(bounds.overlaps(action), "$tag clear of the actions ($scene)") }
                }
                assertTextFitsWithoutBreakingWords("report $scene")
            }
        }
    }

    @Test
    fun phoneLandscapeStatisticsBelowTheActionsShowAScrollCue() {
        var scrolling = 0
        for (language in AppLanguage.entries) for (victory in listOf(true, false)) for (textScale in listOf(1.25f, 1.75f)) {
            runDesktopComposeUiTest(844, 390) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides language) {
                        Box(Modifier.requiredSize(844.dp, 390.dp)) {
                            TerminalContent(report(language).copy(victory = victory), textScale, false, 3f, true) {}
                        }
                    }
                }
                val scene = "$language victory $victory ${textScale}x"
                val statistics = onNodeWithTag("kinetickk.gameplay.results.statistics").fetchSemanticsNode()
                val viewport = statistics.boundsInRoot
                val range = statistics.config[SemanticsProperties.VerticalScrollAxisRange]
                val actionsTop = onNodeWithTag("kinetickk.gameplay.restart").fetchSemanticsNode().boundsInRoot.top
                assertTrue(viewport.bottom <= actionsTop, "$scene: the statistics end above the pinned actions")
                val last = onNodeWithTag("kinetickk.gameplay.stat.LevelReached", useUnmergedTree = true).getUnclippedBoundsInRoot()
                if (range.maxValue() > 0f) {
                    scrolling++
                    assertTrue(last.bottom.value > viewport.bottom, "$scene: a statistic sits below the fold")
                    val bar = onNodeWithTag("kinetickk.gameplay.results.statistics.scroll", useUnmergedTree = true).assertIsDisplayed()
                        .fetchSemanticsNode().boundsInRoot
                    val fade = onNodeWithTag("kinetickk.gameplay.results.statistics.fade", useUnmergedTree = true).assertIsDisplayed()
                        .fetchSemanticsNode().boundsInRoot
                    assertTrue(bar.height >= viewport.height * 0.9f && bar.right <= viewport.right + 12f && bar.left >= viewport.left,
                        "$scene: the scroll bar runs along the statistics: $bar in $viewport")
                    assertEquals(viewport.bottom, fade.bottom, 1f, "$scene: the fade sits at the cut edge")
                    assertTrue(fade.height >= 24f, "$scene: the fade is visible")
                } else {
                    assertTrue(last.bottom.value <= viewport.bottom + 0.5f, "$scene: every statistic is above the actions")
                }
            }
        }
        assertTrue(scrolling > 0, "Thirteen statistics outgrow a phone in landscape")
    }

    @Test
    fun levelReachedReadsAsTheSharedLevelFormatAndFitsEveryLayout() {
        for (language in AppLanguage.entries) {
            val model = rewardFixtureModel(phase = GamePhase.GAME_OVER, level = 16, language = language)
            val value = model.terminalPresentation(language).collection.single { it.label == GameplayText.LevelReached }.value
            assertEquals(if (language == AppLanguage.Russian) "Ур. 16" else "Lvl 16", value)
            for ((width, height) in listOf(1440 to 810, 844 to 390, 390 to 844)) runDesktopComposeUiTest(width, height) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides language) {
                        Box(Modifier.requiredSize(width.dp, height.dp)) {
                            TerminalContent(model.terminalPresentation(language), 1.75f, false, 3f, true) {}
                        }
                    }
                }
                onNodeWithText(value, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
                assertTextFitsWithoutBreakingWords("report $language ${width}x$height")
            }
        }
    }

    @Test
    fun actionLabelsShrinkToTheirButtonsInsteadOfBeingCut() {
        val labels = mutableListOf<Pair<TextLayoutResult, Float>>()
        OverlayButtonProbe.records = labels
        try {
            for ((width, height) in listOf(1440 to 810, 844 to 390, 390 to 844)) for (language in AppLanguage.entries)
                for (victory in listOf(true, false)) for (textScale in listOf(1f, 1.25f, 1.75f)) {
                    labels.clear()
                    runDesktopComposeUiTest(width, height) {
                        setContent {
                            CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides language) {
                                Box(Modifier.requiredSize(width.dp, height.dp)) {
                                    TerminalContent(report(language).copy(victory = victory), textScale, false, 3f, true) {}
                                }
                            }
                        }
                        waitForIdle()
                    }
                    val scene = "report ${width}x$height $language victory $victory ${textScale}x"
                    assertTrue(labels.size >= (if (victory) 3 else 2), "$scene draws its actions")
                    labels.forEach { (layout, room) ->
                        val text = layout.layoutInput.text.text
                        // A single cut line reports its clamped width: its overflow flag says it was cut.
                        assertTrue(!layout.isLineEllipsized(0) && !layout.hasVisualOverflow && layout.size.width <= room + 0.5f,
                            "$scene: \"$text\" ${layout.size.width} fits $room")
                    }
                }
        } finally {
            OverlayButtonProbe.records = null
        }
    }

    @Test
    fun titleStampAndInfoButtonsFollowTheOutcomeWithoutHintText() {
        val victory = mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides AppLanguage.English) {
                Box(Modifier.requiredSize(1000.dp, 720.dp).testTag("terminal-capture")) {
                    TerminalContent(report().copy(victory = victory.value, bank = "1,284"), 1f, false, 3f, true) {}
                }
            }
        }
        compose.onNodeWithText("CORE", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("BROKEN", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("CORE FRACTURED", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("kinetickk.gameplay.rebirth").assertDoesNotExist()
        compose.onNodeWithContentDescription(AppLanguage.English.text(GameplayText.DamageAccountingHint)).assertIsDisplayed()
        compose.onNodeWithContentDescription("Matter banks on victory, on death and when you quit a run.").assertIsDisplayed()
        // The damage explanation lives behind the (!) button, never as a visible line.
        compose.onNodeWithText(AppLanguage.English.text(GameplayText.DamageAccountingHint), useUnmergedTree = true).assertDoesNotExist()
        compose.assertNoForbiddenGlyphs()
        capture("report-defeat-board")
        compose.runOnIdle { victory.value = true }
        compose.onNodeWithText("ARCHITECT", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("DISMANTLED", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(AppLanguage.English.text(GameplayText.ArchitectFallen), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("kinetickk.gameplay.rebirth").assertIsDisplayed()
        compose.assertNoForbiddenGlyphs()
        capture("report-victory-board")
    }

    @Test
    fun victoryOffersRebirthAndDisabledHostCannotActivateActions() {
        val enabled = mutableStateOf(false)
        val actions = mutableListOf<GameplayInput>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides AppLanguage.English) {
                Box(Modifier.requiredSize(1000.dp, 720.dp)) {
                    TerminalContent(report().copy(victory = true), 1f, false, 3f, enabled.value, actions::add)
                }
            }
        }
        compose.onNodeWithTag("kinetickk.gameplay.rebirth").assertIsNotEnabled()
        compose.onNodeWithTag("kinetickk.gameplay.rebirth").performMouseInput { click() }
        compose.runOnIdle { assertTrue(actions.isEmpty()); enabled.value = true }
        compose.onNodeWithTag("kinetickk.gameplay.rebirth").performClick()
        compose.runOnIdle { assertEquals(listOf<GameplayInput>(GameplayInput.OpenRebirth), actions) }
    }

    private fun assertPanelOrder(statisticsOnLeft: Boolean) {
        val summary = compose.onNodeWithTag("kinetickk.gameplay.results.summary").fetchSemanticsNode().boundsInRoot
        val stats = compose.onNodeWithTag("kinetickk.gameplay.results.statistics").fetchSemanticsNode().boundsInRoot
        assertEquals(statisticsOnLeft, stats.left < summary.left)
        assertTrue(if (statisticsOnLeft) stats.right < summary.left else summary.right < stats.left)
    }

    private fun capture(name: String) {
        val directory = System.getenv("KINETICKK_RENDER_CAPTURE_DIR") ?: return
        val bitmap = compose.onNodeWithTag("terminal-capture").captureToImage()
        val pixels = bitmap.toPixelMap()
        val image = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) image.setRGB(x, y, pixels[x, y].toArgb())
        File(directory).mkdirs()
        ImageIO.write(image, "png", File(directory, "$name.png"))
    }
}

private fun report(language: AppLanguage = AppLanguage.English) = TerminalPresentation(
    victory = false, reason = "CORE FRACTURED".localizedContent(language), time = "08:42", kills = "486", matter = "1248",
    weapon = "Flux Wake".localizedContent(language),
    combat = listOf(
        TerminalStatistic(GameplayText.EnemiesDestroyed, "486"),
        TerminalStatistic(GameplayText.ElitesDestroyed, "12"),
        TerminalStatistic(GameplayText.DamageDealt, "128450"),
        TerminalStatistic(GameplayText.DamageTaken, "264"),
        TerminalStatistic(GameplayText.DamageAbsorbed, "380"),
        TerminalStatistic(GameplayText.BestCombo, "47"),
    ),
    collection = listOf(
        TerminalStatistic(GameplayText.MatterEarned, "1248"),
        TerminalStatistic(GameplayText.DataCollected, "1820"),
        TerminalStatistic(GameplayText.PickupsCollected, "934"),
        TerminalStatistic(GameplayText.KeysCollected, "12"),
        TerminalStatistic(GameplayText.ArtifactsAcquired, "23"),
        TerminalStatistic(GameplayText.LevelReached, if (language == AppLanguage.Russian) "Ур. 18" else "Lvl 18"),
    ),
)
