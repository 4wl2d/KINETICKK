// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.api

import kinetickk.ball.profile.api.DEFAULT_INTERFACE_SCALE_PERCENT
import kinetickk.ball.profile.api.INTERFACE_SCALE_OPTIONS
import kinetickk.ball.profile.api.WindowMode
import kinetickk.ball.profile.api.WindowSizePreset
import kotlin.math.max
import kotlin.math.min

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

/** The smallest window, in dp at the chosen scale, that every screen is laid out for. */
const val INTERFACE_SCALED_SHORT_SIDE_DP: Float = 320f
const val INTERFACE_SCALED_LONG_SIDE_DP: Float = 568f

/**
 * Interface scales offered for a window of [widthDp] × [heightDp] (unscaled dp): a scale is offered
 * when the window at that scale keeps an [INTERFACE_SCALED_SHORT_SIDE_DP] short side and an
 * [INTERFACE_SCALED_LONG_SIDE_DP] long side; scales below 100 % are not offered on touch-primary
 * devices. 100 % is always offered, so the row never empties.
 */
fun offeredInterfaceScales(widthDp: Float, heightDp: Float, touchPrimary: Boolean): List<Int> {
    val short = min(widthDp, heightDp)
    val long = max(widthDp, heightDp)
    return INTERFACE_SCALE_OPTIONS.filter { percent ->
        val scale = percent / 100f
        when {
            percent == DEFAULT_INTERFACE_SCALE_PERCENT -> true
            touchPrimary && percent < DEFAULT_INTERFACE_SCALE_PERCENT -> false
            else -> short / scale >= INTERFACE_SCALED_SHORT_SIDE_DP && long / scale >= INTERFACE_SCALED_LONG_SIDE_DP
        }
    }
}

/**
 * The interface scale shown: the stored percentage clamped to the largest [offered] option at or
 * below it (presentation only, never written back), or the smallest offered one.
 */
fun appliedInterfaceScalePercent(storedPercent: Int, offered: List<Int>): Int =
    offered.lastOrNull { it <= storedPercent } ?: offered.first()
