// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.WindowMode
import kinetickk.ball.profile.api.WindowSizePreset
import kinetickk.ball.profile.interaction.settings.api.SettingsDisplaySupport
import kinetickk.ball.profile.interaction.settings.api.SettingsLiveWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DisplayPreferenceProjectionTest {
    @Test
    fun theDefaultPreferencesRequestTheDefaultHostDisplay() {
        assertEquals(HostDisplayRequest(), PlayerPreferences().toHostDisplayRequest())
    }

    @Test
    fun everyDisplayPreferenceReachesTheHostRequest() {
        val preferences = PlayerPreferences(
            windowMode = WindowMode.BORDERLESS,
            windowSize = WindowSizePreset.W1600X900,
            vsync = false,
            refreshRateHz = 90,
            immersive = true,
            // Not display-host options: frame pacing and scale are applied in the app itself.
            frameRateCap = 60,
            interfaceScalePercent = 115,
        )
        assertEquals(
            HostDisplayRequest(HostWindowMode.BORDERLESS, 1600, 900, vsync = false, refreshRateHz = 90, immersive = true),
            preferences.toHostDisplayRequest(),
        )
        WindowMode.entries.forEach { mode ->
            assertEquals(mode, mode.toHostWindowMode().toWindowMode())
            assertEquals(mode.name, mode.toHostWindowMode().name)
        }
    }

    @Test
    fun windowPresetsAreOfferedOnlyWhereTheyFitTheUsableArea() {
        // A 1440×900 laptop screen without the menu bar and the dock: 1280×800 fits, 1440×900 does not.
        val laptop = HostDisplaySupport(windowModes = true, usableWidthDp = 1440, usableHeightDp = 805)
        assertEquals(
            listOf(WindowSizePreset.W1280X720, WindowSizePreset.W1280X800, WindowSizePreset.W1366X768),
            laptop.toSettingsSupport().windowSizes,
        )
        // Exactly the preset's size fits; one dp less does not.
        assertTrue(WindowSizePreset.W1920X1080 in HostDisplaySupport(true, 1920, 1080).toSettingsSupport().windowSizes)
        assertFalse(WindowSizePreset.W1920X1080 in HostDisplaySupport(true, 1920, 1079).toSettingsSupport().windowSizes)
        assertFalse(WindowSizePreset.W1920X1080 in HostDisplaySupport(true, 1919, 1080).toSettingsSupport().windowSizes)
        assertEquals(WindowSizePreset.entries, HostDisplaySupport(true, 2560, 1440).toSettingsSupport().windowSizes)
        // Without window modes (phones, browsers) no preset is listed whatever the screen.
        assertEquals(emptyList(), HostDisplaySupport(false, 2560, 1440).toSettingsSupport().windowSizes)
    }

    @Test
    fun hostSupportMapsOntoTheSettingsRows() {
        assertEquals(
            SettingsDisplaySupport(
                windowModes = true,
                vsync = true,
                refreshReadout = true,
                monitorRefreshHz = 144,
                keyboard = true,
            ),
            HostDisplaySupport(windowModes = true, vsync = true, refreshReadout = true, monitorRefreshHz = 144).toSettingsSupport(),
        )
        assertEquals(
            SettingsDisplaySupport(refreshRatesHz = listOf(120, 90, 60), immersive = true, keyboard = false, touchPrimary = true),
            HostDisplaySupport(refreshRatesHz = listOf(120, 90, 60), immersive = true, hardwareKeyboard = false, touchPrimary = true)
                .toSettingsSupport(),
        )
        assertEquals(
            SettingsDisplaySupport(browserFullscreen = true),
            HostDisplaySupport(browserFullscreen = true).toSettingsSupport(),
        )
        // The host-less platform keeps the rows every platform offers, the key bindings included.
        assertEquals(SettingsDisplaySupport(), PlatformDisplayHost.None.support().toSettingsSupport())
    }

    @Test
    fun refreshChoicesAreDistinctWholeRatesHighestFirstAndAtMostFour() {
        assertEquals(listOf(120, 90, 60), hostRefreshRateChoices(listOf(60f, 90f, 119.88f, 120f, 59.94f)))
        assertEquals(listOf(165, 144, 120, 90), hostRefreshRateChoices(listOf(60f, 90f, 120f, 144f, 165f)))
        assertEquals(listOf(60), hostRefreshRateChoices(listOf(60f)))
        assertEquals(emptyList(), hostRefreshRateChoices(emptyList()))
    }

    @Test
    fun aRequestedRateSelectsItsFastestModeAndAutoOrAMissingRateSelectsTheFastestMode() {
        val rates = listOf(60f, 119.88f, 90f, 120f)
        assertEquals(3, hostRefreshRateModeIndex(rates, 0))
        assertEquals(3, hostRefreshRateModeIndex(rates, 120))
        assertEquals(2, hostRefreshRateModeIndex(rates, 90))
        assertEquals(0, hostRefreshRateModeIndex(rates, 60))
        // 144 Hz was chosen on another screen: this one falls back to Auto (Profile is not written).
        assertEquals(3, hostRefreshRateModeIndex(rates, 144))
        assertEquals(-1, hostRefreshRateModeIndex(emptyList(), 0))
    }

    @Test
    fun settingsReappliesTheStoredPreferencesWithForceAndReadsTheLiveHostState() {
        val host = RecordingDisplayHost()
        var stored = PlayerPreferences(windowMode = WindowMode.FULLSCREEN)
        val display = PlatformSettingsDisplay(host) { stored }

        display.reapply()
        stored = stored.copy(windowMode = WindowMode.WINDOWED, windowSize = WindowSizePreset.W1366X768)
        display.reapply()
        assertEquals(
            listOf(
                PlayerPreferences(windowMode = WindowMode.FULLSCREEN).toHostDisplayRequest() to true,
                HostDisplayRequest(HostWindowMode.WINDOWED, 1366, 768) to true,
            ),
            host.applied,
        )

        assertEquals(null, display.liveWindow())
        host.live = HostLiveWindow(HostWindowMode.WINDOWED, 1500, 900)
        assertEquals(SettingsLiveWindow(WindowMode.WINDOWED, 1500, 900), display.liveWindow())

        assertFalse(display.browserFullscreen)
        display.setBrowserFullscreen(true)
        assertTrue(display.browserFullscreen)
        assertEquals(listOf(true), host.fullscreenCalls)
    }
}

/** Records what Assembly and Settings ask of a platform host. */
internal class RecordingDisplayHost(
    var supported: HostDisplaySupport = HostDisplaySupport(),
) : PlatformDisplayHost {
    val applied = ArrayList<Pair<HostDisplayRequest, Boolean>>()
    val fullscreenCalls = ArrayList<Boolean>()
    // Snapshot state like a real host's window state, so Settings sees a change at once.
    var live: HostLiveWindow? by mutableStateOf(null)
    private var fullscreen = false

    override fun support(): HostDisplaySupport = supported

    override fun apply(request: HostDisplayRequest, force: Boolean) {
        applied += request to force
    }

    override val liveWindow: HostLiveWindow?
        get() = live

    override val browserFullscreen: Boolean
        get() = fullscreen

    override fun setBrowserFullscreen(on: Boolean) {
        fullscreenCalls += on
        fullscreen = on
    }
}
