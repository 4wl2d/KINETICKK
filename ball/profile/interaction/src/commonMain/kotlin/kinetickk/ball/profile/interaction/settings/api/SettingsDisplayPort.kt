// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.api

import kinetickk.ball.profile.api.WindowMode
import kinetickk.ball.profile.api.WindowSizePreset

/**
 * What the platform can list and apply for the DISPLAY and CONTROLS rows. Profile owns the chosen
 * values; the platform only says which of them exist here. Every flag false hides its row.
 */
data class SettingsDisplaySupport(
    /** Desktop window modes (Windowed, Borderless, Fullscreen). */
    val windowModes: Boolean = false,
    /** Window size presets that fit the current screen's usable area, smallest first. */
    val windowSizes: List<WindowSizePreset> = emptyList(),
    /** V-Sync is chosen by the player; false also when a launch override decides it. */
    val vsync: Boolean = false,
    /** The current monitor's refresh rate is shown as a readout (desktop). */
    val refreshReadout: Boolean = false,
    /** The current monitor's refresh rate in Hz; 0 when unknown. */
    val monitorRefreshHz: Int = 0,
    /** Selectable refresh rates in Hz, highest first, at most [MAX_REFRESH_CHOICES] (Android). */
    val refreshRatesHz: List<Int> = emptyList(),
    /** The system bars can be hidden (Android). */
    val immersive: Boolean = false,
    /** The browser offers its fullscreen mode (web). */
    val browserFullscreen: Boolean = false,
    /** A keyboard is available, so the key binding rows are shown. */
    val keyboard: Boolean = true,
    /** Touch is the primary input: the smallest interface scale is not offered. */
    val touchPrimary: Boolean = false,
) {
    init {
        require(refreshRatesHz.size <= MAX_REFRESH_CHOICES) { "At most $MAX_REFRESH_CHOICES refresh rates are offered" }
    }

    companion object {
        const val MAX_REFRESH_CHOICES: Int = 4
    }
}

/** The window as the system shows it now, which may differ from the stored preferences (OS-driven changes). */
data class SettingsLiveWindow(val mode: WindowMode, val widthDp: Int, val heightDp: Int)

/**
 * The platform display host as Settings sees it. Assembly adapts the platform host to it; the host
 * never writes Profile.
 */
interface SettingsDisplayPort {
    fun support(): SettingsDisplaySupport

    /** The live window, or null when the platform has no window of its own. */
    fun liveWindow(): SettingsLiveWindow? = null

    /** Live browser fullscreen state (never persisted). */
    val browserFullscreen: Boolean get() = false

    /** Enters or leaves browser fullscreen; called only inside an input handler. */
    fun setBrowserFullscreen(on: Boolean) = Unit

    /** Applies the stored display preferences again, over OS-driven changes. */
    fun reapply() = Unit

    /** No platform display options: only the rows every platform offers are shown. */
    object None : SettingsDisplayPort {
        override fun support(): SettingsDisplaySupport = SettingsDisplaySupport()
    }
}
