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
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.defaultCoreShapeDefinitions
import kinetickk.ball.gameplay.interaction.GameplayInteractionOutput
import kinetickk.ball.gameplay.interaction.GameplayPresentation
import kinetickk.ball.profile.interaction.armory.api.ArmoryFeature
import kinetickk.ball.profile.interaction.armory.api.ArmoryOutput
import kinetickk.ball.profile.interaction.hangar.api.HangarFeature
import kinetickk.ball.profile.interaction.hangar.api.HangarOutput
import kinetickk.ball.profile.interaction.hangar.impl.DefaultHangarFeature
import kinetickk.ball.profile.interaction.matrix.api.MatrixFeature
import kinetickk.ball.profile.interaction.matrix.api.MatrixOutput
import kinetickk.ball.profile.interaction.rebirth.api.RebirthFeature
import kinetickk.ball.profile.interaction.rebirth.api.RebirthOutput
import kinetickk.ball.profile.interaction.settings.api.SettingsFeature
import kinetickk.ball.profile.interaction.settings.api.SettingsOutput
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.HangarProjection
import kinetickk.ball.profile.api.HangarShapeProgress
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.LoadoutProfileSnapshot
import kinetickk.ball.profile.api.LoadoutProjection
import kinetickk.ball.profile.api.MatrixProgressProjection
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.flow.session.api.*
import kinetickk.flow.session.interaction.audio.SessionAudioExecutor
import kinetickk.flow.session.interaction.codex.api.CodexFeature
import kinetickk.flow.session.interaction.codex.api.CodexOutput
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.flow.session.interaction.home.api.HomeFeature
import kinetickk.flow.session.interaction.home.api.HomeOutput
import kinetickk.flow.session.interaction.profile.api.ProfileUnavailableFeature
import kinetickk.foundation.collections.toImmutableList
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
        val keys = listOf(Key.S, Key.L, Key.A, Key.B, Key.C, Key.I, Key.H, Key.M, Key.Enter, Key.Escape)
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

    @Test
    fun hOverTheHomeBaseOpensTheHangarFeatureAndHAgainClosesIt() = runComposeUiTest {
        val port = KeyboardSessionPort().apply { overlay = null }
        val noCodex = object : CodexFeature {
            @Composable override fun Content(runStacks: CodexRunStacks, onOutput: (CodexOutput) -> Unit) = Unit
        }
        val hangar = object : HangarFeature {
            @Composable override fun Content(routeToken: Long, onOutput: (HangarOutput) -> Unit) {
                Box(Modifier.size(48.dp).testTag("keyboard-hangar"))
            }
        }
        setContent {
            Box(Modifier.size(1000.dp, 700.dp)) { KeyboardShell(port, noCodex, hangar) }
        }
        onRoot().performKeyInput { pressKey(Key.H) }
        runOnIdle {
            assertEquals(SessionInteractionPulse.ShortcutObserved(SessionShortcut.HANGAR), port.pulses.last())
            assertEquals(AppDestination.Hangar, port.overlay)
        }
        onNodeWithTag("keyboard-hangar").assertExists()
        onRoot().performKeyInput { pressKey(Key.H) }
        runOnIdle { assertEquals(null, port.overlay) }
        onNodeWithTag("keyboard-hangar").assertDoesNotExist()
    }

    @Test
    fun enterAfterInspectingAHangarTileSelectsItAndKeepsTheHangarOpen() = runComposeUiTest {
        val port = KeyboardSessionPort().apply { overlay = AppDestination.Hangar }
        val profile = KeyboardHangarProfilePort()
        val noCodex = object : CodexFeature {
            @Composable override fun Content(runStacks: CodexRunStacks, onOutput: (CodexOutput) -> Unit) = Unit
        }
        setContent {
            Box(Modifier.size(1280.dp, 720.dp)) {
                KeyboardShell(port, noCodex, DefaultHangarFeature(profile, defaultCoreShapeDefinitions(), KeyboardSilentAudio))
            }
        }
        // A press inspects a tile; hovering another one on the way only highlights it, so Enter selects the inspected one.
        onNodeWithTag("profile-hangar-tile-${CoreShape.PRISM}").performMouseInput { click() }
        waitForIdle()
        assertTrue(profile.pulses.isEmpty(), "the first press only inspects")
        onNodeWithTag("profile-hangar-tile-${CoreShape.SPIRAL}").performMouseInput { moveTo(center) }
        waitForIdle()
        onRoot().performKeyInput { pressKey(Key.Enter) }
        runOnIdle {
            assertEquals(listOf<ProfilePulse.Business>(ProfilePulse.SelectCoreShape(CoreShape.PRISM)), profile.pulses)
            assertTrue(SessionInteractionPulse.ShortcutObserved(SessionShortcut.ENTER) !in port.pulses, "Enter stays in the Hangar")
            assertEquals(AppDestination.Hangar, port.overlay)
        }
        onNodeWithTag("profile-hangar-tile-${CoreShape.PRISM}").assertIsSelected()
    }
}

/** The Profile reads and the character selection the Hangar needs, with all twelve characters available. */
private class KeyboardHangarProfilePort : ProfilePort {
    private var selected = CoreShape.ORB
    private var revision = ProfileRevision(1L)
    val pulses = mutableListOf<ProfilePulse.Business>()

    override val instanceId = LOCAL_PROFILE_INSTANCE_ID

    override fun accept(pulse: ProfilePulse.Business): ProfileAcceptance {
        pulses += pulse
        selected = (pulse as ProfilePulse.SelectCoreShape).shape
        revision = ProfileRevision(revision.value + 1)
        return ProfileAcceptance.Accepted(instanceId, revision)
    }

    override fun query(query: ProfileQuery.GetHangar): HangarProjection = HangarProjection(
        instanceId, revision,
        defaultCoreShapeDefinitions().map { definition ->
            val target = definition.unlockTarget.toLong()
            HangarShapeProgress(definition.id, true, target, target)
        }.toImmutableList(),
        selected,
    )

    override fun query(query: ProfileQuery.GetLoadout): LoadoutProjection =
        LoadoutProjection(instanceId, revision, LoadoutProfileSnapshot(PlayerEconomy(0L), PlayerLoadout(coreShape = selected)))

    override fun query(query: ProfileQuery.GetPreferences): PreferencesProjection =
        PreferencesProjection(instanceId, revision, PlayerPreferences())

    override fun query(query: ProfileQuery.GetMatrixProgress): MatrixProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection = error("unused")
    override fun query(query: ProfileQuery.GetHomeProgress): HomeProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetCollection): CollectionProjection = error("unused")
    override fun query(query: ProfileQuery.GetRebirthProgress): RebirthProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetPersistenceStatus): PersistenceStatusProjection = error("unused")
}

internal class KeyboardSessionPort : AppSessionPort {
    override val instanceId = LOCAL_APP_SESSION_INSTANCE_ID
    val pulses = mutableListOf<SessionInteractionPulse>()
    var overlay: AppDestination? = AppDestination.Codex
    override fun accept(pulse: SessionInteractionPulse): SessionAcceptance {
        pulses += pulse
        if (pulse == SessionInteractionPulse.CloseOverlay) overlay = null
        // Like the Session Nucleus, an Enter that reaches the Session closes any overlay.
        if (pulse == SessionInteractionPulse.ShortcutObserved(SessionShortcut.ENTER)) overlay = null
        // The Hangar shortcut toggles its overlay over the Home base.
        if (pulse == SessionInteractionPulse.ShortcutObserved(SessionShortcut.HANGAR)) {
            overlay = if (overlay == AppDestination.Hangar) null else AppDestination.Hangar
        }
        return SessionAcceptance.Accepted(instanceId, SessionRevision(pulses.size.toLong()))
    }
    override fun query(query: AppSessionQuery.GetShell) = AppShellProjection(
        instanceId, SessionRevision.ZERO, SessionRevision.ZERO, AppDestination.Home, overlay,
        null, false, null, SessionLifecycle.READY, false, null,
    )
}

@Composable
internal fun KeyboardShell(port: AppSessionPort, codex: CodexFeature, hangar: HangarFeature? = null) {
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
        matrixFeature = object : MatrixFeature {
            @Composable override fun Content(routeToken: Long, onOutput: (MatrixOutput) -> Unit) = Unit
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
        hangarFeature = hangar,
    )
}

private object KeyboardSilentAudio : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
