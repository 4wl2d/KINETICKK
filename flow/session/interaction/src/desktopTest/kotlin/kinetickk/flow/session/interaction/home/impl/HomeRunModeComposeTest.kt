// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kinetickk.ball.gameplay.api.RunMode
import kinetickk.flow.session.interaction.ShotCatalog
import kinetickk.flow.session.interaction.ShotProfile
import kinetickk.flow.session.interaction.home.api.HomeOutput
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Baby mode toggle on the real Home: an announced switch that asks Session for the other mode and shows the mode. */
@OptIn(ExperimentalTestApi::class)
class HomeRunModeComposeTest {
    @Test
    fun theToggleIsAnAnnouncedSwitchThatAsksForTheOtherModeAndShowsTheMode() = runComposeUiTest {
        mainClock.autoAdvance = false
        var runMode by mutableStateOf(RunMode.STANDARD)
        val outputs = mutableListOf<HomeOutput>()
        val feature = DefaultHomeFeature(ShotProfile(), ShotCatalog, RunModeSilentAudio)
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.English) {
                Box(Modifier.requiredSize(1000.dp, 700.dp)) {
                    feature.Content(inputEnabled = true, runMode = runMode) { output ->
                        outputs += output
                        // A Session stand-in over the bare Home: it accepts the selection.
                        if (output is HomeOutput.SelectRunMode) runMode = output.mode
                    }
                }
            }
        }
        mainClock.advanceTimeBy(300)
        val layout = homeLayoutGeometry(1000f, 700f, density.density)
        val plate = layout.runModeToggle.bounds
        // Inside the plate, clear of its cut corners and of the pacifier.
        val fillProbe = Offset(plate.right - 4f * density.density, plate.top + 3f * density.density)
        // The tooltip slip opens below the toggle, kept 8 dp inside the right edge.
        val tipProbe = Offset(plate.right - 135f * density.density, plate.bottom + 17f * density.density)
        val hero = layout.scene.heroCenter
        val toggle = onNodeWithTag(HOME_RUN_MODE_TAG)
        val home = onNodeWithTag(HOME_TAG)

        fun frame(): PixelMap {
            mainClock.advanceTimeByFrame()
            return home.captureToImage().toPixelMap()
        }
        fun state(expected: ToggleableState, description: String) {
            mainClock.advanceTimeByFrame()
            toggle.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, expected))
            toggle.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, description))
        }

        toggle.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        toggle.assertContentDescriptionEquals("Baby mode")
        state(ToggleableState.Off, "Off")
        frame().let { off ->
            assertTrue(off.near(fillProbe, Kk.Ink2), "the standard toggle is an ink plate: ${off[fillProbe]}")
            assertTrue(!off.near(tipProbe, Kk.Bone), "no tooltip before the toggle is touched")
            assertTrue(off.count(hero, layout.scene.heroRadius, KkRolePalette.Default.soft) < 20, "the standard hero is not soft")
        }

        // A tap goes through Home's own hit test: Baby mode is asked for, and the tooltip stays open after the tap.
        toggle.performTouchInput { click(center) }
        mainClock.advanceTimeByFrame()
        assertEquals(listOf<HomeOutput>(HomeOutput.SelectRunMode(RunMode.EASY)), outputs)
        state(ToggleableState.On, "On")
        frame().let { on ->
            assertTrue(on.near(fillProbe, KkRolePalette.Default.soft), "the Baby mode toggle is a soft plate: ${on[fillProbe]}")
            assertTrue(on.near(tipProbe, Kk.Bone), "the tapped toggle shows its tooltip: ${on[tipProbe]}")
            // The soft singularity and the stamp under it.
            assertTrue(on.count(hero, layout.scene.heroRadius, KkRolePalette.Default.soft) > 500, "the Baby mode hero is soft")
        }

        // A tap anywhere else closes the tooltip and asks for nothing.
        home.performTouchInput { click(hero) }
        mainClock.advanceTimeByFrame()
        assertEquals(1, outputs.size)
        assertTrue(!frame().near(tipProbe, Kk.Bone), "a tap elsewhere closes the tooltip")

        // Focused (Tab or a screen reader), it shows the tooltip; Enter and the semantic click ask for the other mode.
        toggle.performSemanticsAction(SemanticsActions.RequestFocus)
        mainClock.advanceTimeByFrame()
        toggle.assertIsFocused()
        assertTrue(frame().near(tipProbe, Kk.Bone), "the focused toggle shows its tooltip")
        onRoot().performKeyInput { pressKey(Key.Enter) }
        mainClock.advanceTimeByFrame()
        assertEquals(HomeOutput.SelectRunMode(RunMode.STANDARD), outputs.last())
        state(ToggleableState.Off, "Off")
        toggle.performSemanticsAction(SemanticsActions.OnClick)
        mainClock.advanceTimeByFrame()
        assertEquals(HomeOutput.SelectRunMode(RunMode.EASY), outputs.last())
        assertEquals(3, outputs.size)
        // The arrow keys keep the five menu rows: Down from the switch selects the row after the active one.
        onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        mainClock.advanceTimeByFrame()
        onNodeWithTag("kinetickk.home.workshop").assertIsFocused()
    }

    @Test
    fun aHomeWithoutInputOffersNoToggle() = runComposeUiTest {
        mainClock.autoAdvance = false
        val outputs = mutableListOf<HomeOutput>()
        val feature = DefaultHomeFeature(ShotProfile(), ShotCatalog, RunModeSilentAudio)
        setContent {
            Box(Modifier.requiredSize(1000.dp, 700.dp)) {
                feature.Content(inputEnabled = false, runMode = RunMode.STANDARD, onOutput = outputs::add)
            }
        }
        mainClock.advanceTimeBy(300)
        onNodeWithTag(HOME_RUN_MODE_TAG).assertDoesNotExist()
        val plate = homeLayoutGeometry(1000f, 700f, density.density).runModeToggle.bounds
        onNodeWithTag(HOME_TAG).performTouchInput { click(plate.center) }
        mainClock.advanceTimeByFrame()
        assertTrue(outputs.isEmpty(), "an overlay over Home blocks the toggle: $outputs")
    }
}

private const val HOME_TAG = "kinetickk.home"

private fun PixelMap.near(at: Offset, color: Color, tolerance: Float = 3f / 255f): Boolean {
    val pixel = this[at.x.toInt(), at.y.toInt()]
    return abs(pixel.red - color.red) <= tolerance && abs(pixel.green - color.green) <= tolerance &&
        abs(pixel.blue - color.blue) <= tolerance && pixel.alpha > 0.99f
}

private operator fun PixelMap.get(at: Offset): Color = this[at.x.toInt(), at.y.toInt()]

/** Pixels of [color] inside the square of [radius] around [center]. */
private fun PixelMap.count(center: Offset, radius: Float, color: Color): Int {
    var count = 0
    for (y in (center.y - radius).toInt().coerceAtLeast(0) until (center.y + radius).toInt().coerceAtMost(height)) {
        for (x in (center.x - radius).toInt().coerceAtLeast(0) until (center.x + radius).toInt().coerceAtMost(width)) {
            if (near(Offset(x.toFloat(), y.toFloat()), color)) count++
        }
    }
    return count
}

private object RunModeSilentAudio : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
