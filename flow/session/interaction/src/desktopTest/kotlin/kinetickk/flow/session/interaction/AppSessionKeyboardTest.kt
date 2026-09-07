// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
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
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises the real shell's capture and bubble handlers, including focused text controls. */
@OptIn(ExperimentalTestApi::class)
class AppSessionKeyboardTest {
    @Test
    fun codexFocusedControlsReceiveEveryHotLetterAndEscapeWithoutSessionShortcuts() = runComposeUiTest {
        val port = KeyboardSessionPort()
        val received = mutableListOf<Key>()
        val keys = listOf(Key.S, Key.L, Key.A, Key.B, Key.C, Key.I, Key.M, Key.Enter, Key.Escape)
        setContent {
            Box(Modifier.size(1000.dp, 700.dp)) {
                KeyboardShell(port, object : CodexFeature {
                    @Composable
                    override fun Content(runStacks: CodexRunStacks, onOutput: (CodexOutput) -> Unit) {
                        var textValue by remember { mutableStateOf("") }
                        BasicTextField(textValue, { textValue = it }, Modifier.testTag("keyboard-search")
                            .onPreviewKeyEvent {
                                if (it.type == KeyEventType.KeyDown) received += it.key
                                false
                            })
                    }
                })
            }
        }
        onNodeWithTag("keyboard-search").performClick()
        keys.forEach { key -> onNodeWithTag("keyboard-search").performKeyInput { pressKey(key) } }
        runOnIdle {
            assertEquals(keys, received)
            assertTrue(port.pulses.isEmpty(), "Codex keys must not dispatch global Session shortcuts")
        }
    }
}

internal class KeyboardSessionPort : AppSessionPort {
    override val instanceId = LOCAL_APP_SESSION_INSTANCE_ID
    val pulses = mutableListOf<SessionInteractionPulse>()
    var overlay: AppDestination? = AppDestination.Codex
    override fun accept(pulse: SessionInteractionPulse): SessionAcceptance {
        pulses += pulse
        if (pulse == SessionInteractionPulse.CloseOverlay) overlay = null
        return SessionAcceptance.Accepted(instanceId, SessionRevision(pulses.size.toLong()))
    }
    override fun query(query: AppSessionQuery.GetShell) = AppShellProjection(
        instanceId, SessionRevision.ZERO, SessionRevision.ZERO, AppDestination.Home, overlay,
        null, false, null, SessionLifecycle.READY, false, null,
    )
}

@Composable
internal fun KeyboardShell(port: AppSessionPort, codex: CodexFeature) {
    AppSessionContent(
        sessionPort = port,
        audioExecutor = remember { SessionAudioExecutor(KeyboardSilentAudio) },
        gameplayPresentation = object : GameplayPresentation {
            override fun activePresentation() = null
            @Composable override fun Content(inputEnabled: Boolean, onOutput: (GameplayInteractionOutput) -> Unit) = Unit
        },
        homeFeature = object : HomeFeature {
            @Composable override fun Content(inputEnabled: Boolean, onOutput: (HomeOutput) -> Unit) = Unit
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
        codexFeature = codex,
        profileUnavailableFeature = object : ProfileUnavailableFeature {
            @Composable override fun Content() = Unit
        },
    )
}

private object KeyboardSilentAudio : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
