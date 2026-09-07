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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.localizedContent
import kinetickk.foundation.common.localization.text
import kinetickk.ball.gameplay.interaction.input.GameplayInput
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
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
            compose.onNodeWithTag("kinetickk.gameplay.restart").performScrollTo().assertIsDisplayed()
            capture("death-report-${size.first}x${size.second}-large-actions")
            compose.onNodeWithTag("kinetickk.gameplay.results").performTouchInput { swipeUp() }
            compose.runOnIdle { assertTrue(actions.isEmpty()) }
            compose.onNodeWithTag("kinetickk.gameplay.stat.LevelReached").performScrollTo().assertIsDisplayed()
            capture("death-report-${size.first}x${size.second}-large-stats")
            compose.onNodeWithTag("kinetickk.gameplay.exit").performScrollTo().assertIsDisplayed()
        }
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
    weapon = language.text(GameplayText.WeaponLevel, "Flux Wake".localizedContent(language), 7),
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
        TerminalStatistic(GameplayText.LevelReached, "18"),
    ),
)
