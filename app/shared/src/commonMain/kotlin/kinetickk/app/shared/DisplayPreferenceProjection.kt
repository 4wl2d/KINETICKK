// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.WindowMode
import kinetickk.ball.profile.api.WindowSizePreset
import kinetickk.ball.profile.interaction.settings.api.SettingsDisplayPort
import kinetickk.ball.profile.interaction.settings.api.SettingsDisplaySupport
import kinetickk.ball.profile.interaction.settings.api.SettingsLiveWindow

/**
 * Translation of the accepted display preferences into a mechanical host request, the same step
 * the audio executor takes for sound: Profile semantics never cross into a platform host.
 */
internal fun PlayerPreferences.toHostDisplayRequest(): HostDisplayRequest = HostDisplayRequest(
    windowMode = windowMode.toHostWindowMode(),
    windowWidthDp = windowSize.widthDp,
    windowHeightDp = windowSize.heightDp,
    vsync = vsync,
    refreshRateHz = refreshRateHz,
    immersive = immersive,
)

/** What Settings may offer, from what the host lists: presets only where they fit the usable area. */
internal fun HostDisplaySupport.toSettingsSupport(): SettingsDisplaySupport = SettingsDisplaySupport(
    windowModes = windowModes,
    windowSizes = if (windowModes) {
        WindowSizePreset.entries.filter { it.widthDp <= usableWidthDp && it.heightDp <= usableHeightDp }
    } else {
        emptyList()
    },
    vsync = vsync,
    refreshReadout = refreshReadout,
    monitorRefreshHz = monitorRefreshHz,
    refreshRatesHz = refreshRatesHz.take(SettingsDisplaySupport.MAX_REFRESH_CHOICES),
    immersive = immersive,
    browserFullscreen = browserFullscreen,
    keyboard = hardwareKeyboard,
    touchPrimary = touchPrimary,
)

internal fun HostLiveWindow.toSettingsLiveWindow(): SettingsLiveWindow =
    SettingsLiveWindow(mode.toWindowMode(), widthDp, heightDp)

internal fun WindowMode.toHostWindowMode(): HostWindowMode = when (this) {
    WindowMode.WINDOWED -> HostWindowMode.WINDOWED
    WindowMode.BORDERLESS -> HostWindowMode.BORDERLESS
    WindowMode.FULLSCREEN -> HostWindowMode.FULLSCREEN
}

internal fun HostWindowMode.toWindowMode(): WindowMode = when (this) {
    HostWindowMode.WINDOWED -> WindowMode.WINDOWED
    HostWindowMode.BORDERLESS -> WindowMode.BORDERLESS
    HostWindowMode.FULLSCREEN -> WindowMode.FULLSCREEN
}

/**
 * The platform display host as Settings sees it. Re-applying reads the stored preferences through
 * [storedPreferences] (Assembly's Profile read), so the host itself never reads or writes Profile.
 */
internal class PlatformSettingsDisplay(
    private val host: PlatformDisplayHost,
    private val storedPreferences: () -> PlayerPreferences,
) : SettingsDisplayPort {
    override fun support(): SettingsDisplaySupport = host.support().toSettingsSupport()

    override fun liveWindow(): SettingsLiveWindow? = host.liveWindow?.toSettingsLiveWindow()

    override val browserFullscreen: Boolean
        get() = host.browserFullscreen

    override fun setBrowserFullscreen(on: Boolean) = host.setBrowserFullscreen(on)

    override fun reapply() = host.apply(storedPreferences().toHostDisplayRequest(), force = true)
}
