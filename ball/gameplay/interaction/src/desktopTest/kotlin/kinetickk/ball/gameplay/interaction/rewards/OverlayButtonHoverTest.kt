// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkButtonVariant
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class OverlayButtonHoverTest {
    @Test
    fun ghostButtonHighlightFollowsThePointerOnTheNextFrame() = runSkikoComposeUiTest(Size(360f, 220f), Density(1f)) {
        val clicks = mutableListOf<String>()
        setContent {
            Column(Modifier.padding(20.dp)) {
                OverlayButton("First", { clicks += "first" }, Modifier.testTag("first").size(240.dp, 60.dp), variant = KkButtonVariant.GHOST)
                OverlayButton("Second", { clicks += "second" }, Modifier.testTag("second").size(240.dp, 60.dp), variant = KkButtonVariant.GHOST)
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        val first = onNodeWithTag("first")
        val second = onNodeWithTag("second")
        first.performMouseInput { moveTo(center) }
        mainClock.advanceTimeByFrame()
        assertEquals(Kk.Bone.toArgb(), first.captureToImage().toPixelMap()[30, 10].toArgb())
        mainClock.advanceTimeBy(240)
        second.performMouseInput { moveTo(center) }
        mainClock.advanceTimeByFrame()
        assertEquals(Kk.Ink3.toArgb(), first.captureToImage().toPixelMap()[30, 10].toArgb(), "the previous button must stop looking active")
        assertEquals(Kk.Bone.toArgb(), second.captureToImage().toPixelMap()[30, 10].toArgb(), "the current button must look active")
        second.performMouseInput { click() }
        mainClock.advanceTimeBy(240)
        assertEquals(listOf("second"), clicks)
        first.performMouseInput { moveTo(center) }
        mainClock.advanceTimeByFrame()
        assertEquals(Kk.Ink3.toArgb(), second.captureToImage().toPixelMap()[30, 10].toArgb(), "clicking must not pin the old hover highlight")
        assertEquals(Kk.Bone.toArgb(), first.captureToImage().toPixelMap()[30, 10].toArgb(), "hover follows the pointer after a click")
        second.assertIsFocused()
        second.performKeyInput { pressKey(Key.Enter) }
        mainClock.advanceTimeBy(240)
        assertEquals(listOf("second", "second"), clicks, "mouse-origin focus still supports keyboard activation")
        assertEquals(Kk.Bone.toArgb(), second.captureToImage().toPixelMap()[30, 10].toArgb(), "keyboard input restores the focused highlight")
    }
}
