// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.ball.gameplay.impl.DefaultGameplayFeature
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.WindowMode
import kinetickk.ball.profile.impl.ProfilePersistenceCapability
import kinetickk.ball.profile.impl.ProfilePersistenceMutationResult
import kinetickk.ball.profile.impl.ProfilePersistenceReadResult
import kinetickk.ball.profile.impl.createProfileComponent
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.AppSessionQuery
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRunPhase
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import kinetickk.ball.gameplay.impl.GameplayCompositionComponent
import kinetickk.ball.gameplay.api.RunMode
import kinetickk.ball.gameplay.interaction.GameplayInteractionOutput
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.interaction.home.api.HomeFeature
import kinetickk.flow.session.interaction.home.api.HomeOutput
import kinetickk.foundation.design.LocalKkFrameRateCap
import kinetickk.foundation.design.LocalKkInterfaceScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** Assembly hands every accepted display preference to the platform host, and nothing else writes it. */
@OptIn(ExperimentalTestApi::class)
class DisplayApplicationComposeTest {
    @Test
    fun acceptedDisplayPreferencesReachTheHostAndAStoredModePressReappliesIt() = runComposeUiTest {
        mainClock.autoAdvance = false
        val catalog = createContentCatalog()
        val profile = createProfileComponent(DisplayTestPersistence(), catalog.profilePolicy())
        val audio = DisplayTestAudio()
        val host = RecordingDisplayHost(
            HostDisplaySupport(
                windowModes = true, usableWidthDp = 1920, usableHeightDp = 1050, vsync = true,
                refreshReadout = true, monitorRefreshHz = 60,
            ),
        )
        val owner = AppCompositionOwner(
            contentCatalog = catalog,
            profileComponent = profile,
            audioService = audio,
            gameplayComponent = DefaultGameplayFeature(catalog.gameplayContent(), profile, profile, audio),
            displayHost = host,
        )
        try {
            setContent {
                Box(Modifier.requiredSize(1000.dp, 700.dp).testTag("app")) { owner.Content() }
            }
            fun settle() {
                mainClock.advanceTimeBy(260)
                waitForIdle()
            }
            fun stored() = profile.query(ProfileQuery.GetPreferences).preferences
            settle()
            // The stored preferences are applied once at start; the host diffs later requests.
            assertEquals(listOf(HostDisplayRequest() to false), host.applied)

            onRoot().performKeyInput { pressKey(Key.S) }
            settle()
            assertEquals(AppDestination.Settings, owner.sessionPort.query(AppSessionQuery.GetShell).active)
            onNodeWithTag("kinetickk.settings.group.display").performClick()
            settle()

            onNodeWithTag("kinetickk.settings.windowmode.fullscreen").performClick()
            settle()
            assertEquals(WindowMode.FULLSCREEN, stored().windowMode)
            assertEquals(stored().toHostDisplayRequest() to false, host.applied.last())
            assertEquals(HostWindowMode.FULLSCREEN, host.applied.last().first.windowMode)

            onNodeWithTag("kinetickk.settings.vsync.toggle").performClick()
            settle()
            assertEquals(HostDisplayRequest(HostWindowMode.FULLSCREEN, vsync = false) to false, host.applied.last())

            // The system left fullscreen (OS-driven): pressing the stored mode re-applies it with force,
            // and Profile is not written.
            val before = stored()
            val requests = host.applied.size
            host.live = HostLiveWindow(HostWindowMode.WINDOWED, 1280, 800)
            settle()
            onNodeWithTag("kinetickk.settings.windowmode.fullscreen").assertIsSelected().performClick()
            settle()
            assertEquals(before, stored())
            assertEquals(listOf(before.toHostDisplayRequest() to true), host.applied.drop(requests))
        } finally {
            owner.close()
        }
    }

    @Test
    fun aWindowOpenedAgainKeepsTheSessionTheRunAndTheOpenSettingsTab() = runComposeUiTest {
        mainClock.autoAdvance = false
        val catalog = createContentCatalog()
        val profile = createProfileComponent(DisplayTestPersistence(), catalog.profilePolicy())
        val audio = DisplayTestAudio()
        val gameplay = DefaultGameplayFeature(catalog.gameplayContent(), profile, profile, audio)
        val owner = AppCompositionOwner(
            contentCatalog = catalog,
            profileComponent = profile,
            audioService = audio,
            gameplayComponent = gameplay,
            displayHost = RecordingDisplayHost(HostDisplaySupport(windowModes = true, usableWidthDp = 1920, usableHeightDp = 1050, vsync = true)),
        )
        // The desktop application keys its window on the host's generation; the owner lives above it.
        var generation by mutableIntStateOf(0)
        try {
            setContent {
                key(generation) {
                    Box(Modifier.requiredSize(1000.dp, 700.dp)) { owner.Content() }
                }
            }
            fun settle() {
                mainClock.advanceTimeBy(260)
                waitForIdle()
            }
            settle()
            owner.sessionPort.accept(SessionInteractionPulse.StartRunRequested)
            onRoot().performKeyInput { pressKey(Key.S) }
            settle()
            val shell = owner.sessionPort.query(AppSessionQuery.GetShell)
            assertEquals(AppDestination.Gameplay, shell.base)
            assertEquals(AppDestination.Settings, shell.overlay)
            val run = gameplay.activeRun()
            assertEquals(GameplayRunPhase.PAUSED, run?.query(GameplayQuery.GetRunStatus)?.phase)
            onNodeWithTag("kinetickk.settings.group.display").performClick()
            settle()

            generation++
            settle()
            assertEquals(shell, owner.sessionPort.query(AppSessionQuery.GetShell))
            assertSame(run, gameplay.activeRun())
            assertEquals(GameplayRunPhase.PAUSED, run?.query(GameplayQuery.GetRunStatus)?.phase)
            onNodeWithTag("kinetickk.settings.group.display").assertIsSelected()
        } finally {
            owner.close()
        }
    }

    @Test
    fun aWorkshopAliasAfterTheWindowOpensAgainShowsItsTab() = runComposeUiTest {
        mainClock.autoAdvance = false
        val catalog = createContentCatalog()
        val profile = createProfileComponent(DisplayTestPersistence(), catalog.profilePolicy())
        val audio = DisplayTestAudio()
        val owner = AppCompositionOwner(
            contentCatalog = catalog,
            profileComponent = profile,
            audioService = audio,
            gameplayComponent = DefaultGameplayFeature(catalog.gameplayContent(), profile, profile, audio),
            displayHost = RecordingDisplayHost(HostDisplaySupport(windowModes = true, usableWidthDp = 1920, usableHeightDp = 1050, vsync = true)),
        )
        var generation by mutableIntStateOf(0)
        try {
            setContent {
                key(generation) {
                    Box(Modifier.requiredSize(1000.dp, 700.dp)) { owner.Content() }
                }
            }
            fun settle() {
                mainClock.advanceTimeBy(260)
                waitForIdle()
            }
            fun workshop() = owner.sessionPort.query(AppSessionQuery.GetShell).overlay
            settle()
            onRoot().performKeyInput { pressKey(Key.L) }
            settle()
            assertEquals(AppDestination.Workshop, workshop())
            onNodeWithTag("kinetickk.workshop.tab.matrix").assertIsSelected()
            onNodeWithTag("kinetickk.workshop.tab.weapons").performClick()
            settle()
            onNodeWithTag("kinetickk.workshop.tab.weapons").assertIsSelected()
            onRoot().performKeyInput { pressKey(Key.W) }
            settle()
            assertEquals(null, workshop())

            // A V-Sync or decoration change opens the window again; the owner and its features stay.
            generation++
            settle()
            onRoot().performKeyInput { pressKey(Key.L) }
            settle()
            assertEquals(AppDestination.Workshop, workshop())
            onNodeWithTag("kinetickk.workshop.tab.matrix").assertIsSelected()
            // Without an alias the Workshop still opens on the tab shown last.
            onNodeWithTag("kinetickk.workshop.tab.characters").performClick()
            settle()
            onRoot().performKeyInput { pressKey(Key.W) }
            settle()
            onRoot().performKeyInput { pressKey(Key.W) }
            settle()
            onNodeWithTag("kinetickk.workshop.tab.characters").assertIsSelected()
        } finally {
            owner.close()
        }
    }

    @Test
    fun menusLayOutAtTheAppliedInterfaceScaleWhileTheRunKeepsTheRawDensity() {
        // Landscape window: 130 % fits 1280×800 (984×615 dp scaled); 720×540 at 130 % would be 554 dp
        // long, so 115 % is applied there. Profile keeps 130 % in both.
        assertEquals(DisplayProbe(1.3f, 1.3f, 60, gameplayDensity = 1f), probeAt(1280, 800))
        assertEquals(DisplayProbe(1.15f, 1.15f, 60, gameplayDensity = 1f), probeAt(720, 540))
    }

    private data class DisplayProbe(val menuDensity: Float, val scale: Float, val frameRateCap: Int, val gameplayDensity: Float)

    private fun probeAt(width: Int, height: Int): DisplayProbe {
        var probe = DisplayProbe(0f, 0f, -1, 0f)
        runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat()), density = Density(1f)) {
            mainClock.autoAdvance = false
            val catalog = createContentCatalog()
            val profile = createProfileComponent(DisplayTestPersistence(), catalog.profilePolicy())
            profile.accept(ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.SetInterfaceScale(130)))
            profile.accept(ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.SetFrameRateCap(60)))
            val audio = DisplayTestAudio()
            val gameplay = DefaultGameplayFeature(catalog.gameplayContent(), profile, profile, audio)
            val owner = AppCompositionOwner(
                contentCatalog = catalog,
                profileComponent = profile,
                audioService = audio,
                gameplayComponent = object : GameplayCompositionComponent by gameplay {
                    @Composable
                    override fun Content(inputEnabled: Boolean, onOutput: (GameplayInteractionOutput) -> Unit) {
                        probe = probe.copy(gameplayDensity = LocalDensity.current.density)
                    }
                },
                homeFeature = object : HomeFeature {
                    @Composable
                    override fun Content(inputEnabled: Boolean, runMode: RunMode, onOutput: (HomeOutput) -> Unit) {
                        probe = probe.copy(
                            menuDensity = LocalDensity.current.density,
                            scale = LocalKkInterfaceScale.current,
                            frameRateCap = LocalKkFrameRateCap.current,
                        )
                    }
                },
            )
            try {
                setContent {
                    Box(Modifier.requiredSize(width.dp, height.dp)) { owner.Content() }
                }
                mainClock.advanceTimeBy(100)
                waitForIdle()
                owner.sessionPort.accept(SessionInteractionPulse.StartRunRequested)
                // The shell reads the session again on its next input; any key does.
                onRoot().performKeyInput { pressKey(Key.Enter) }
                mainClock.advanceTimeBy(400)
                waitForIdle()
            } finally {
                owner.close()
            }
        }
        return probe
    }
}

private class DisplayTestPersistence : ProfilePersistenceCapability {
    private var payload: String? = null
    override fun readSnapshot() = ProfilePersistenceReadResult.Observed(payload)
    override fun writeSnapshot(payload: String): ProfilePersistenceMutationResult {
        this.payload = payload
        return ProfilePersistenceMutationResult.COMPLETED
    }
}

private class DisplayTestAudio : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
