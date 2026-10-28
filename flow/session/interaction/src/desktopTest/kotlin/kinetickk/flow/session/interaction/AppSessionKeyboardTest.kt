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
import kinetickk.ball.gameplay.api.RunMode
import kinetickk.ball.gameplay.interaction.GameplayInteractionOutput
import kinetickk.ball.gameplay.interaction.GameplayPresentation
import kinetickk.ball.profile.interaction.workshop.api.WorkshopFeature
import kinetickk.ball.profile.interaction.workshop.api.WorkshopOutput
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTab
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTabRequest
import kinetickk.ball.profile.interaction.workshop.impl.DefaultWorkshopFeature
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.profile.api.KeyBindings
import kinetickk.ball.profile.api.ProfileReadPort
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.input.BindableKey
import kinetickk.foundation.design.toBindableKey
import androidx.compose.foundation.focusable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
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
        val keys = listOf(Key.S, Key.W, Key.L, Key.A, Key.B, Key.C, Key.I, Key.H, Key.M, Key.Enter, Key.Escape)
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
    fun hOverTheHomeBaseOpensTheWorkshopOnCharactersAndWClosesIt() = runComposeUiTest {
        val port = KeyboardSessionPort().apply { overlay = null }
        val workshop = RecordingWorkshop()
        setContent {
            Box(Modifier.size(1000.dp, 700.dp)) { KeyboardShell(port, NoCodex, workshop) }
        }
        onRoot().performKeyInput { pressKey(Key.H) }
        runOnIdle {
            assertEquals(SessionInteractionPulse.OpenOverlay(AppDestination.Workshop), port.pulses.last())
            assertEquals(AppDestination.Workshop, port.overlay)
            assertEquals(WorkshopTab.CHARACTERS, workshop.requests.last()?.tab)
        }
        onNodeWithTag("keyboard-workshop").assertExists()
        // An alias while the Workshop is shown asks again with a new serial: the tab switches in place.
        onRoot().performKeyInput { pressKey(Key.L) }
        runOnIdle {
            val (first, second) = workshop.requests.filterNotNull().distinct().takeLast(2)
            assertEquals(WorkshopTab.MATRIX, second.tab)
            assertTrue(second.serial > first.serial)
            assertEquals(AppDestination.Workshop, port.overlay)
        }
        onRoot().performKeyInput { pressKey(Key.W) }
        runOnIdle {
            assertEquals(SessionInteractionPulse.ShortcutObserved(SessionShortcut.WORKSHOP), port.pulses.last())
            assertEquals(null, port.overlay)
        }
        onNodeWithTag("keyboard-workshop").assertDoesNotExist()
    }

    @Test
    fun hOverARunIsANoOpWhileLAndAOpenTheWorkshopOnTheirTabs() = runComposeUiTest {
        val port = KeyboardSessionPort().apply { overlay = null; base = AppDestination.Gameplay }
        val workshop = RecordingWorkshop()
        val gameplay = KeyboardGameplay()
        setContent {
            Box(Modifier.size(1000.dp, 700.dp)) { KeyboardShell(port, NoCodex, workshop, gameplay) }
        }
        waitForIdle()
        onRoot().performKeyInput { pressKey(Key.H) }
        runOnIdle { assertTrue(port.pulses.isEmpty(), "H over a run: ${port.pulses}") }
        onRoot().performKeyInput { pressKey(Key.A) }
        runOnIdle {
            assertEquals(listOf<SessionInteractionPulse>(SessionInteractionPulse.OpenOverlay(AppDestination.Workshop)), port.pulses)
            assertEquals(WorkshopTab.WEAPONS, workshop.requests.last()?.tab)
        }
    }

    @Test
    fun aRefusedAliasLeavesThePreviousTabRequest() = runComposeUiTest {
        val port = KeyboardSessionPort().apply { overlay = null; refuseWorkshop = true }
        val workshop = RecordingWorkshop()
        setContent {
            Box(Modifier.size(1000.dp, 700.dp)) { KeyboardShell(port, NoCodex, workshop) }
        }
        onRoot().performKeyInput { pressKey(Key.L) }
        runOnIdle { assertEquals(null, port.overlay) }
        // Once Session accepts, W opens the Workshop on the tab shown last, with no stale request.
        port.refuseWorkshop = false
        onRoot().performKeyInput { pressKey(Key.W) }
        runOnIdle {
            assertEquals(AppDestination.Workshop, port.overlay)
            assertEquals(null, workshop.requests.last())
        }
    }

    @Test
    fun eOverHomeAsksSessionToToggleBabyMode() = runComposeUiTest {
        val port = KeyboardSessionPort().apply { overlay = null }
        setContent {
            Box(Modifier.size(1000.dp, 700.dp)) { KeyboardShell(port, NoCodex) }
        }
        waitForIdle()
        // Over the bare Home, E asks Session for the mode other than the one its shell shows.
        onRoot().performKeyInput { pressKey(Key.E) }
        runOnIdle {
            assertEquals(listOf<SessionInteractionPulse>(SessionInteractionPulse.SelectRunModeRequested(RunMode.EASY)), port.pulses)
            assertEquals(RunMode.EASY, port.runMode)
        }
        onRoot().performKeyInput { pressKey(Key.E) }
        runOnIdle {
            assertEquals(SessionInteractionPulse.SelectRunModeRequested(RunMode.STANDARD), port.pulses.last())
            assertEquals(RunMode.STANDARD, port.runMode)
        }
        // Session decides: a refusal (here, with an overlay open) leaves the mode alone.
        onRoot().performKeyInput { pressKey(Key.W) }
        onRoot().performKeyInput { pressKey(Key.E) }
        runOnIdle {
            assertEquals(SessionInteractionPulse.SelectRunModeRequested(RunMode.EASY), port.pulses.last())
            assertEquals(RunMode.STANDARD, port.runMode)
        }
    }

    @Test
    fun eStaysFreeInARun() = runComposeUiTest {
        val port = KeyboardSessionPort().apply { overlay = null; base = AppDestination.Gameplay }
        val gameplay = KeyboardGameplay()
        setContent {
            Box(Modifier.size(1000.dp, 700.dp)) { KeyboardShell(port, NoCodex, gameplay = gameplay) }
        }
        waitForIdle()
        onRoot().performKeyInput { pressKey(Key.E) }
        runOnIdle {
            assertTrue(port.pulses.isEmpty(), "E over a run: ${port.pulses}")
            assertTrue(Key.E in gameplay.seen, "E reaches the run")
            assertEquals(RunMode.STANDARD, port.runMode)
        }
    }

    @Test
    fun boundGameplayKeysReachGameplayFirstOnlyWhileRunning() = runComposeUiTest {
        val port = KeyboardSessionPort().apply { overlay = null; base = AppDestination.Gameplay }
        val gameplay = KeyboardGameplay()
        setContent {
            Box(Modifier.size(1000.dp, 700.dp)) { KeyboardShell(port, NoCodex, gameplay = gameplay) }
        }
        waitForIdle()
        // Running: C is Skill 3 and gameplay consumes it; the Codex does not open.
        onRoot().performKeyInput { pressKey(Key.C) }
        runOnIdle {
            assertEquals(listOf(Key.C), gameplay.consumed)
            assertTrue(port.pulses.isEmpty(), "${port.pulses}")
        }
        // An unbound shell key (B) still goes to Session first; Escape then closes its overlay.
        onRoot().performKeyInput { pressKey(Key.B) }
        runOnIdle {
            assertEquals(SessionInteractionPulse.ShortcutObserved(SessionShortcut.REBIRTH), port.pulses.last())
            assertTrue(Key.B !in gameplay.seen, "B never reached gameplay")
        }
        onRoot().performKeyInput { pressKey(Key.Escape) }
        runOnIdle { assertEquals(null, port.overlay) }
        // Paused: Skill 3 does not apply, the key bubbles back and opens the Codex as before.
        gameplay.running = false
        onRoot().performKeyInput { pressKey(Key.C) }
        runOnIdle {
            assertEquals(listOf(Key.C), gameplay.consumed)
            assertEquals(SessionInteractionPulse.ShortcutObserved(SessionShortcut.CODEX), port.pulses.last())
            assertEquals(AppDestination.Codex, port.overlay)
        }
    }

    @Test
    fun rebindingSToDashShadowsSettingsOnlyWhileRunning() = runComposeUiTest {
        val port = KeyboardSessionPort().apply { overlay = null; base = AppDestination.Gameplay }
        val bindings = KeyBindings.DEFAULT.with(GameAction.DASH, BindableKey.S)
        val gameplay = KeyboardGameplay(bindings)
        setContent {
            Box(Modifier.size(1000.dp, 700.dp)) {
                KeyboardShell(port, NoCodex, gameplay = gameplay, profile = KeyboardPreferencesPort(PlayerPreferences(keyBindings = bindings)))
            }
        }
        waitForIdle()
        onRoot().performKeyInput { pressKey(Key.S) }
        runOnIdle {
            assertEquals(listOf(Key.S), gameplay.consumed)
            assertTrue(port.pulses.isEmpty(), "S dashes while running: ${port.pulses}")
        }
        // Space, no longer bound to Dash, is not one of gameplay's keys now.
        onRoot().performKeyInput { pressKey(Key.Spacebar) }
        runOnIdle { assertTrue(Key.Spacebar !in gameplay.consumed) }
        gameplay.running = false
        onRoot().performKeyInput { pressKey(Key.S) }
        runOnIdle {
            assertEquals(listOf<SessionInteractionPulse>(SessionInteractionPulse.ShortcutObserved(SessionShortcut.SETTINGS)), port.pulses)
            assertEquals(AppDestination.Settings, port.overlay)
        }
    }

    @Test
    fun escapeStillPausesARunningRun() = runComposeUiTest {
        val port = KeyboardSessionPort().apply { overlay = null; base = AppDestination.Gameplay }
        val gameplay = KeyboardGameplay()
        setContent {
            Box(Modifier.size(1000.dp, 700.dp)) { KeyboardShell(port, NoCodex, gameplay = gameplay) }
        }
        waitForIdle()
        onRoot().performKeyInput { pressKey(Key.Escape) }
        runOnIdle {
            // Escape is not bindable: Session sees Back first, refuses it without an overlay, and gameplay pauses.
            assertEquals(listOf<SessionInteractionPulse>(SessionInteractionPulse.ShortcutObserved(SessionShortcut.BACK)), port.pulses)
            assertEquals(listOf(Key.Escape), gameplay.consumed)
        }
    }

    @Test
    fun enterAfterInspectingACharacterSelectsItThroughSessionAndKeepsTheWorkshopOpen() = runComposeUiTest {
        val profile = KeyboardHangarProfilePort()
        val port = KeyboardSessionPort().apply {
            overlay = AppDestination.Workshop
            onSelect = { shape -> profile.accept(ProfilePulse.SelectCoreShape(shape)) }
        }
        setContent {
            Box(Modifier.size(1280.dp, 720.dp)) {
                val workshop = remember {
                    DefaultWorkshopFeature(profile, immutableListOf(), immutableListOf(), immutableListOf(), defaultCoreShapeDefinitions(),
                        KeyboardSilentAudio)
                }
                KeyboardShell(port, NoCodex, workshop)
            }
        }
        waitForIdle()
        // Right after opening, focus rests on the panel: Enter belongs to the Characters tab, not to Session
        // (the selected character is inspected, so nothing is asked and the Workshop stays open).
        onRoot().performKeyInput { pressKey(Key.Enter) }
        runOnIdle {
            assertTrue(port.pulses.isEmpty(), "${port.pulses}")
            assertEquals(AppDestination.Workshop, port.overlay)
        }
        // A press inspects a tile; hovering another one on the way only highlights it, so Enter selects the inspected one.
        onNodeWithTag("profile-hangar-tile-${CoreShape.PRISM}").performMouseInput { click() }
        waitForIdle()
        assertTrue(profile.pulses.isEmpty(), "the first press only inspects")
        onNodeWithTag("profile-hangar-tile-${CoreShape.SPIRAL}").performMouseInput { moveTo(center) }
        waitForIdle()
        onRoot().performKeyInput { pressKey(Key.Enter) }
        runOnIdle {
            assertEquals(SessionInteractionPulse.SelectCoreShapeRequested(CoreShape.PRISM), port.pulses.last())
            assertEquals(listOf<ProfilePulse.Business>(ProfilePulse.SelectCoreShape(CoreShape.PRISM)), profile.pulses)
            assertTrue(SessionInteractionPulse.ShortcutObserved(SessionShortcut.ENTER) !in port.pulses, "Enter stays in the Workshop")
            assertEquals(AppDestination.Workshop, port.overlay)
        }
        onNodeWithTag("profile-hangar-tile-${CoreShape.PRISM}").assertIsSelected()
    }
}

private val NoCodex = object : CodexFeature {
    @Composable override fun Content(runStacks: CodexRunStacks, onOutput: (CodexOutput) -> Unit) = Unit
}

/** A Workshop stand-in that records every tab request the shell passes. */
private class RecordingWorkshop : WorkshopFeature {
    val requests = mutableListOf<WorkshopTabRequest?>()

    @Composable
    override fun Content(
        routeToken: Long,
        tabRequest: WorkshopTabRequest?,
        characterSelectionAvailable: Boolean,
        activeRunWeapon: WeaponId?,
        onOutput: (WorkshopOutput) -> Unit,
    ) {
        SideEffect { requests += tabRequest }
        Box(Modifier.size(48.dp).testTag("keyboard-workshop"))
    }
}

/**
 * A gameplay stand-in with the precedence table's RUNNING rule: while [running] it consumes the keys
 * bound to Dash and the skills (and Escape, its fixed pause key); in every other phase they bubble.
 */
private class KeyboardGameplay(private val bindings: KeyBindings = KeyBindings.DEFAULT) : GameplayPresentation {
    var running = true
    val seen = mutableListOf<Key>()
    val consumed = mutableListOf<Key>()

    override fun activePresentation() = null

    @Composable
    override fun Content(inputEnabled: Boolean, onOutput: (GameplayInteractionOutput) -> Unit) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        Box(
            Modifier.size(64.dp).focusRequester(focus).onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                seen += event.key
                val action = event.key.toBindableKey()?.let(bindings::action)
                val takes = event.key == Key.Escape || (running && action in RunningActions)
                if (takes) consumed += event.key
                takes
            }.focusable(),
        )
    }

    private companion object {
        val RunningActions = setOf(GameAction.DASH, GameAction.SKILL_1, GameAction.SKILL_2, GameAction.SKILL_3, GameAction.APEX)
    }
}

/** Preferences only: the shell reads the key bindings from them. */
private class KeyboardPreferencesPort(private val preferences: PlayerPreferences) : ProfileReadPort {
    override val instanceId = LOCAL_PROFILE_INSTANCE_ID
    private val revision = ProfileRevision(1L)
    override fun query(query: ProfileQuery.GetPreferences): PreferencesProjection = PreferencesProjection(instanceId, revision, preferences)
    override fun query(query: ProfileQuery.GetHangar): HangarProjection = error("unused")
    override fun query(query: ProfileQuery.GetLoadout): LoadoutProjection = error("unused")
    override fun query(query: ProfileQuery.GetMatrixProgress): MatrixProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection = error("unused")
    override fun query(query: ProfileQuery.GetHomeProgress): HomeProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetCollection): CollectionProjection = error("unused")
    override fun query(query: ProfileQuery.GetRebirthProgress): RebirthProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetPersistenceStatus): PersistenceStatusProjection = error("unused")
}

/** The Profile reads and the character selection the Workshop's Characters tab needs, with all twelve characters available. */
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

/**
 * A Session stand-in over the Home or Gameplay [base] with the Nucleus' route rules the shell relies
 * on: shortcuts open, toggle or close overlays, Back without an overlay is refused, and a character
 * selection runs [onSelect] inline as the participant command.
 */
internal class KeyboardSessionPort : AppSessionPort {
    override val instanceId = LOCAL_APP_SESSION_INSTANCE_ID
    val pulses = mutableListOf<SessionInteractionPulse>()
    var overlay: AppDestination? = AppDestination.Codex
    var base: AppDestination = AppDestination.Home
    var refuseWorkshop = false
    var onSelect: (CoreShape) -> Unit = {}
    var runMode = RunMode.STANDARD

    override fun accept(pulse: SessionInteractionPulse): SessionAcceptance {
        pulses += pulse
        val accepted = when (pulse) {
            SessionInteractionPulse.CloseOverlay -> overlay != null && run { overlay = null; true }
            is SessionInteractionPulse.OpenOverlay -> open(pulse.destination)
            is SessionInteractionPulse.ShortcutObserved -> when (pulse.shortcut) {
                // Like the Session Nucleus, an Enter or Back that reaches the Session closes any overlay.
                SessionShortcut.ENTER, SessionShortcut.BACK -> overlay != null && run { overlay = null; true }
                SessionShortcut.CODEX -> toggle(AppDestination.Codex)
                SessionShortcut.WORKSHOP -> toggle(AppDestination.Workshop)
                SessionShortcut.SETTINGS -> open(AppDestination.Settings)
                SessionShortcut.REBIRTH -> open(AppDestination.Rebirth)
                SessionShortcut.MUTE -> true
            }
            is SessionInteractionPulse.SelectCoreShapeRequested -> {
                onSelect(pulse.shape)
                true
            }
            // Like the Session Nucleus, the run mode changes only over the bare Home.
            is SessionInteractionPulse.SelectRunModeRequested ->
                base == AppDestination.Home && overlay == null && run { runMode = pulse.mode; true }
            else -> true
        }
        return if (accepted) {
            SessionAcceptance.Accepted(instanceId, SessionRevision(pulses.size.toLong()))
        } else {
            SessionAcceptance.Rejected(instanceId, SessionRevision(pulses.size.toLong()), SessionRejection.ShortcutUnavailable)
        }
    }

    private fun open(destination: AppDestination): Boolean {
        if (destination == AppDestination.Workshop && refuseWorkshop) return false
        overlay = destination
        return true
    }

    private fun toggle(destination: AppDestination): Boolean = if (overlay == destination) {
        overlay = null
        true
    } else {
        open(destination)
    }

    override fun query(query: AppSessionQuery.GetShell) = AppShellProjection(
        instanceId, SessionRevision.ZERO, SessionRevision.ZERO, base, overlay,
        if (base == AppDestination.Gameplay) kinetickk.ball.gameplay.api.RunId(1L) else null, false, null, SessionLifecycle.READY, false, null,
        runMode = runMode,
    )
}

@Composable
internal fun KeyboardShell(
    port: AppSessionPort,
    codex: CodexFeature,
    workshop: WorkshopFeature = RecordingWorkshop(),
    gameplay: GameplayPresentation = object : GameplayPresentation {
        override fun activePresentation() = null
        @Composable override fun Content(inputEnabled: Boolean, onOutput: (GameplayInteractionOutput) -> Unit) = Unit
    },
    profile: ProfileReadPort? = null,
) {
    AppSessionContent(
        sessionPort = port,
        audioExecutor = remember { SessionAudioExecutor(KeyboardSilentAudio) },
        gameplayPresentation = gameplay,
        homeFeature = object : HomeFeature {
            @Composable override fun Content(inputEnabled: Boolean, runMode: RunMode, onOutput: (HomeOutput) -> Unit) = Unit
        },
        settingsFeature = object : SettingsFeature {
            @Composable override fun Content(routeToken: Long, onOutput: (SettingsOutput) -> Unit) = Unit
        },
        workshopFeature = workshop,
        rebirthFeature = object : RebirthFeature {
            override fun playAcceptedFeedback() = Unit
            @Composable override fun Content(routeToken: Long, eligible: Boolean, confirmationArmed: Boolean, onOutput: (RebirthOutput) -> Unit) = Unit
        },
        codexFeature = codex,
        profileUnavailableFeature = object : ProfileUnavailableFeature {
            @Composable override fun Content() = Unit
        },
        profileReadPort = profile,
    )
}

private object KeyboardSilentAudio : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
