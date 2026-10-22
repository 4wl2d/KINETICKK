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
import kotlin.test.Test
import kotlin.test.assertEquals

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
