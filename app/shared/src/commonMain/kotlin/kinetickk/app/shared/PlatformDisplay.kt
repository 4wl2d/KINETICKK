// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.runtime.staticCompositionLocalOf
import kinetickk.ball.profile.api.REFRESH_RATE_RANGE
import kotlin.math.roundToInt

/** How a desktop window is shown. Mechanical: no Profile type reaches a platform host. */
enum class HostWindowMode { WINDOWED, BORDERLESS, FULLSCREEN }

/**
 * The display options a platform host applies, translated by Assembly from the accepted
 * preferences. A host applies only what it supports and ignores the rest.
 */
data class HostDisplayRequest(
    val windowMode: HostWindowMode = HostWindowMode.WINDOWED,
    val windowWidthDp: Int = 1280,
    val windowHeightDp: Int = 800,
    val vsync: Boolean = true,
    /** 0 selects the highest rate the screen offers. */
    val refreshRateHz: Int = 0,
    val immersive: Boolean = false,
)

/** What a platform host can list and apply here. */
data class HostDisplaySupport(
    /** Windowed, borderless and fullscreen windows (desktop). */
    val windowModes: Boolean = false,
    /** The current screen's area outside the system's bars and docks, in dp. */
    val usableWidthDp: Int = 0,
    val usableHeightDp: Int = 0,
    /** False also when a launch override decides V-Sync. */
    val vsync: Boolean = false,
    /** The current monitor's refresh rate is shown as a readout (desktop). */
    val refreshReadout: Boolean = false,
    /** The current monitor's refresh rate in Hz; 0 when unknown. */
    val monitorRefreshHz: Int = 0,
    /** Selectable refresh rates in Hz, from [hostRefreshRateChoices] (Android). */
    val refreshRatesHz: List<Int> = emptyList(),
    /** The system bars can be hidden (Android). */
    val immersive: Boolean = false,
    /** The browser offers its fullscreen mode (web). */
    val browserFullscreen: Boolean = false,
    /** A physical keyboard is available, so keys can be bound. */
    val hardwareKeyboard: Boolean = true,
    /** Touch is the primary input. */
    val touchPrimary: Boolean = false,
)

/** The window as the system shows it now; OS-driven changes may make it differ from the last request. */
data class HostLiveWindow(val mode: HostWindowMode, val widthDp: Int, val heightDp: Int)

/**
 * A platform's display host: lists what can be applied and applies it mechanically. It never
 * writes Profile; Assembly feeds it the accepted preferences.
 */
interface PlatformDisplayHost {
    fun support(): HostDisplaySupport

    /** Applies the changes against the last applied request, or everything again when [force]. */
    fun apply(request: HostDisplayRequest, force: Boolean = false)

    /** The live window, or null when the platform has no window of its own. Snapshot-state backed. */
    val liveWindow: HostLiveWindow? get() = null

    /** Live browser fullscreen state, never stored. Snapshot-state backed. */
    val browserFullscreen: Boolean get() = false

    /** Enters or leaves browser fullscreen; call only inside an input handler (user activation). */
    fun setBrowserFullscreen(on: Boolean) = Unit

    /** No display options: nothing is applied and only the rows every platform offers are shown. */
    object None : PlatformDisplayHost {
        override fun support(): HostDisplaySupport = HostDisplaySupport()

        override fun apply(request: HostDisplayRequest, force: Boolean) = Unit
    }
}

/** The host a platform entry hands to [KinetickkApp] when it cannot pass it as an argument. */
val LocalPlatformDisplayHost = staticCompositionLocalOf<PlatformDisplayHost> { PlatformDisplayHost.None }

/** The most refresh rates a host offers (Auto plus these fill one segmented row). */
const val MAX_HOST_REFRESH_CHOICES: Int = 4

/**
 * The refresh rates to offer from the [rates] of the display modes at the current resolution:
 * distinct whole Hz within [REFRESH_RATE_RANGE] (a rate outside it cannot be requested), highest
 * first, at most [MAX_HOST_REFRESH_CHOICES].
 */
internal fun hostRefreshRateChoices(rates: List<Float>): List<Int> = rates.map(Float::roundToInt)
    .filter { it in REFRESH_RATE_RANGE }
    .distinct()
    .sortedDescending()
    .take(MAX_HOST_REFRESH_CHOICES)

/**
 * The index in [rates] of the display mode a [requestedHz] selects: the fastest mode that rounds to
 * it, or the fastest mode overall for 0 (Auto) and for a rate no mode offers. -1 when [rates] is empty.
 */
internal fun hostRefreshRateModeIndex(rates: List<Float>, requestedHz: Int): Int {
    var fastest = -1
    var matching = -1
    rates.forEachIndexed { index, rate ->
        if (fastest < 0 || rate > rates[fastest]) fastest = index
        if (requestedHz > 0 && rate.roundToInt() == requestedHz && (matching < 0 || rate > rates[matching])) matching = index
    }
    return if (matching >= 0) matching else fastest
}
