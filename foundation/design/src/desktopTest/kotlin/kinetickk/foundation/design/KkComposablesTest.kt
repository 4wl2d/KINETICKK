// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.focusable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KkComposablesTest {
    @Test
    fun buttonIsAnAccessibleKeyboardActivatableButton() = runSkikoComposeUiTest(Size(480f, 240f), Density(1f)) {
        var clicks = 0
        var lockedClicks = 0
        setContent {
            Column(Modifier.padding(20.dp)) {
                KkButton("Deploy", onClick = { clicks++ }, modifier = Modifier.testTag("deploy"))
                KkButton("Resume", onClick = {}, enabled = false, modifier = Modifier.testTag("disabled"))
                KkButton("430", onClick = { lockedClicks++ }, locked = true, contentDescription = "Unlock for 430",
                    modifier = Modifier.testTag("locked"))
            }
        }
        onNodeWithContentDescription("Deploy")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        waitForIdle()
        assertEquals(1, clicks)
        onNodeWithTag("deploy").performSemanticsAction(SemanticsActions.RequestFocus)
        onNodeWithTag("deploy").assertIsFocused()
        onNodeWithTag("deploy").performKeyInput { pressKey(Key.Enter) }
        onNodeWithTag("deploy").performKeyInput { pressKey(Key.Spacebar) }
        waitForIdle()
        assertEquals(3, clicks, "Enter and Space activate")
        onNodeWithTag("disabled").assertIsNotEnabled()
        onNodeWithTag("locked").performClick()
        waitForIdle()
        assertEquals(0, lockedClicks, "locked buttons are inert")
        onNodeWithContentDescription("Unlock for 430").assertExists()
        onNodeWithTag("locked").performSemanticsAction(SemanticsActions.RequestFocus)
        onNodeWithTag("locked").assertIsFocused()
        // The idle primary face is the `you` role color at its natural size.
        val image = onNodeWithTag("deploy").captureToImage()
        assertEquals(52, image.height)
        assertEquals(Kk.Volt.toArgb(), image.at(20, 10))
    }

    @Test
    fun infoButtonAnnouncesItsTextAndTogglesTheTooltip() = runSkikoComposeUiTest(Size(480f, 320f), Density(1f)) {
        val text = "Mastery climbs during the run and resets with the run build."
        setContent {
            Column {
                Box(Modifier.size(20.dp).focusable().testTag("other"))
                Box(Modifier.padding(start = 200.dp, top = 180.dp)) {
                    KkInfoButton(text, Modifier.testTag("info"))
                }
            }
        }
        onNodeWithContentDescription(text)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        onAllNodes(isPopup()).assertCountEquals(0)
        // Hover shows, leaving hides.
        onNodeWithTag("info").performMouseInput { enter(center) }
        waitForIdle()
        onAllNodes(isPopup()).assertCountEquals(1)
        onNodeWithTag("info").performMouseInput { exit(Offset(-10f, -10f)) }
        waitForIdle()
        onAllNodes(isPopup()).assertCountEquals(0)
        // Tap toggles on touch.
        onNodeWithTag("info").performTouchInput { click() }
        waitForIdle()
        onAllNodes(isPopup()).assertCountEquals(1)
        onNodeWithTag("info").performTouchInput { click() }
        waitForIdle()
        onAllNodes(isPopup()).assertCountEquals(0)
        // Keyboard focus shows it too (focus from a tap does not).
        onNodeWithTag("other").performSemanticsAction(SemanticsActions.RequestFocus)
        waitForIdle()
        onAllNodes(isPopup()).assertCountEquals(0)
        onNodeWithTag("info").performSemanticsAction(SemanticsActions.RequestFocus)
        waitForIdle()
        onAllNodes(isPopup()).assertCountEquals(1)
    }

    @Test
    fun bundledFontsResolveThroughComposeResources() = runSkikoComposeUiTest(Size(200f, 100f), Density(1f)) {
        var typography: InterfaceTypography? = null
        var measurer: CanvasTextMeasurer? = null
        setContent {
            typography = rememberInterfaceTypography()
            measurer = rememberKkCanvasMeasurer()
        }
        waitForIdle()
        val t = requireNotNull(typography)
        val m = requireNotNull(measurer)
        assertNotEquals(FontFamily.SansSerif, t.wide)
        val wide = measureKkText(m, "MOMENTUM", t.wideStyle(30f)).size.width
        val cond = measureKkText(m, "MOMENTUM", t.condStyle(30f)).size.width
        val fallback = m.delegate.measure("MOMENTUM", TextStyle(fontFamily = FontFamily.SansSerif, fontSize = t.wideStyle(30f).fontSize)).size.width
        assertTrue(wide > cond * 2, "Unbounded is far wider than Sofia Sans Extra Condensed ($wide vs $cond)")
        assertTrue(wide > fallback, "Unbounded replaces the system fallback ($wide vs $fallback)")
    }

    @Test
    fun discoveryBadgeFollowsTheRolePalette() = runSkikoComposeUiTest(Size(200f, 100f), Density(1f)) {
        setContent {
            CompositionLocalProvider(LocalKkRolePalette provides KkRolePalette.Tritan) {
                Box(Modifier.padding(30.dp)) { DiscoveryBadge("New!", 1f, Modifier.testTag("badge")) }
            }
        }
        val image = onNodeWithTag("badge").captureToImage()
        assertTrue(image.countNear(KkRolePalette.Tritan.you) > 200)
    }

    @Test
    fun typographyCarriesTheAppLanguageLocale() = runSkikoComposeUiTest(Size(200f, 100f), Density(1f)) {
        val measurers = mutableMapOf<kinetickk.foundation.common.localization.AppLanguage, CanvasTextMeasurer>()
        setContent {
            for (language in kinetickk.foundation.common.localization.AppLanguage.entries) {
                CompositionLocalProvider(LocalAppLanguage provides language) {
                    measurers[language] = rememberKkCanvasMeasurer()
                }
            }
        }
        waitForIdle()
        for ((language, measurer) in measurers) {
            val typography = measurer.typography
            assertEquals(language.localeList(), typography.localeList)
            listOf(typography.wideStyle(), typography.condStyle(), typography.labelStyle(), typography.bodyStyle(), typography.monoStyle())
                .forEach { style -> assertEquals(language.localeList(), style.localeList) }
        }
    }
}
