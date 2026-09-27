// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.interaction.GameplayInteractionOutput
import kinetickk.ball.gameplay.interaction.GameplayPresentation
import kinetickk.ball.profile.interaction.armory.api.ArmoryFeature
import kinetickk.ball.profile.interaction.armory.api.ArmoryOutput
import kinetickk.ball.profile.interaction.lab.api.LabFeature
import kinetickk.ball.profile.interaction.lab.api.LabOutput
import kinetickk.ball.profile.interaction.rebirth.api.RebirthFeature
import kinetickk.ball.profile.interaction.rebirth.api.RebirthOutput
import kinetickk.ball.profile.interaction.settings.api.SettingsFeature
import kinetickk.ball.profile.interaction.settings.api.SettingsOutput
import kinetickk.flow.session.api.*
import kinetickk.flow.session.interaction.audio.SessionAudioExecutor
import kinetickk.flow.session.interaction.codex.api.CodexFeature
import kinetickk.flow.session.interaction.codex.api.CodexOutput
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.flow.session.interaction.home.api.HomeFeature
import kinetickk.flow.session.interaction.home.api.HomeOutput
import kinetickk.flow.session.interaction.profile.api.ProfileUnavailableFeature
import kinetickk.foundation.design.KkShutter
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The shutter is drawn over the new screen; it never delays its composition or its input. */
@OptIn(ExperimentalTestApi::class)
class AppSessionTransitionComposeTest {
    @Test
    fun screensSwapAtOnceAndTakeInputWhileTheShutterStillRuns() = runComposeUiTest {
        mainClock.autoAdvance = false
        val port = OverlaySessionPort()
        setContent { Box(Modifier.size(800.dp, 500.dp)) { TransitionShell(port) } }
        mainClock.advanceTimeBy(500)
        onNodeWithTag("transition-open").performClick()
        // One frame into the shutter (far before its swap point) the overlay already exists and
        // accepts input: the shutter only draws.
        mainClock.advanceTimeByFrame()
        assertTrue(mainClock.currentTime < 500 + KkShutter.SWAP_MS)
        onNodeWithTag("transition-close").performClick()
        mainClock.advanceTimeByFrame()
        assertEquals(
            listOf(SessionInteractionPulse.OpenOverlay(AppDestination.Codex), SessionInteractionPulse.CloseOverlay),
            port.pulses,
        )
        onNodeWithTag("transition-open").performClick()
        mainClock.advanceTimeBy(KkShutter.TOTAL_MS.toLong() + 100)
        onNodeWithTag("transition-close").performClick()
        assertEquals(4, port.pulses.size)
    }
}

private class OverlaySessionPort : AppSessionPort {
    override val instanceId = LOCAL_APP_SESSION_INSTANCE_ID
    val pulses = mutableListOf<SessionInteractionPulse>()
    private var overlay: AppDestination? = null
    override fun accept(pulse: SessionInteractionPulse): SessionAcceptance {
        pulses += pulse
        when (pulse) {
            is SessionInteractionPulse.OpenOverlay -> overlay = pulse.destination
            SessionInteractionPulse.CloseOverlay -> overlay = null
            else -> Unit
        }
        return SessionAcceptance.Accepted(instanceId, SessionRevision(pulses.size.toLong()))
    }
    override fun query(query: AppSessionQuery.GetShell) = AppShellProjection(
        instanceId, SessionRevision(pulses.size.toLong()), SessionRevision.ZERO, AppDestination.Home, overlay,
        null, false, null, SessionLifecycle.READY, false, null,
    )
}

@Composable
private fun TransitionShell(port: AppSessionPort) {
    AppSessionContent(
        sessionPort = port,
        audioExecutor = remember { SessionAudioExecutor(TransitionSilentAudio) },
        gameplayPresentation = object : GameplayPresentation {
            override fun activePresentation() = null
            @Composable override fun Content(inputEnabled: Boolean, onOutput: (GameplayInteractionOutput) -> Unit) = Unit
        },
        homeFeature = object : HomeFeature {
            @Composable override fun Content(inputEnabled: Boolean, onOutput: (HomeOutput) -> Unit) {
                BasicText("open", Modifier.testTag("transition-open").clickable(enabled = inputEnabled) { onOutput(HomeOutput.OpenCodex) })
            }
        },
        settingsFeature = object : SettingsFeature {
            @Composable override fun Content(routeToken: Long, onOutput: (SettingsOutput) -> Unit) = Unit
        },
        labFeature = object : LabFeature {
            @Composable override fun Content(routeToken: Long, onOutput: (LabOutput) -> Unit) = Unit
        },
        armoryFeature = object : ArmoryFeature {
            @Composable override fun Content(activeRunWeapon: WeaponId?, onOutput: (ArmoryOutput) -> Unit) = Unit
        },
        rebirthFeature = object : RebirthFeature {
            override fun playAcceptedFeedback() = Unit
            @Composable override fun Content(routeToken: Long, eligible: Boolean, confirmationArmed: Boolean, onOutput: (RebirthOutput) -> Unit) = Unit
        },
        codexFeature = object : CodexFeature {
            @Composable override fun Content(runStacks: CodexRunStacks, onOutput: (CodexOutput) -> Unit) {
                BasicText("close", Modifier.testTag("transition-close").clickable { onOutput(CodexOutput.Back) })
            }
        },
        profileUnavailableFeature = object : ProfileUnavailableFeature {
            @Composable override fun Content() = Unit
        },
    )
}

private object TransitionSilentAudio : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
