// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.view.Display
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The Android display host: the refresh rate (a display mode at the current resolution; Auto picks
 * the fastest, so a 90, 120 or 144 Hz panel is not left at 60 Hz) and hidden system bars. The
 * system may restore the bars or the mode, so the activity calls [reapply] when it resumes or
 * regains focus. The fixed-step simulation plays the same at any rate. Never writes Profile.
 */
class AndroidDisplayHost(private val activity: Activity) : PlatformDisplayHost {
    private var last: HostDisplayRequest? = null

    /**
     * Whether the system bars were last hidden. The activity then keeps only the cutout (and an
     * open keyboard) clear: hidden bars still report their gesture areas, which would keep the
     * play area as small as with the bars shown.
     */
    var systemBarsHidden: Boolean by mutableStateOf(false)
        private set

    override fun support(): HostDisplaySupport = HostDisplaySupport(
        refreshRatesHz = hostRefreshRateChoices(currentResolutionModes().map { it.refreshRate }),
        immersive = true,
        hardwareKeyboard = activity.resources.configuration.keyboard == Configuration.KEYBOARD_QWERTY,
        touchPrimary = true,
    )

    override fun apply(request: HostDisplayRequest, force: Boolean) {
        val previous = last
        if (!force && request == previous) return
        last = request
        if (force || previous?.refreshRateHz != request.refreshRateHz) applyRefreshRate(request.refreshRateHz)
        if (force || previous?.immersive != request.immersive) applySystemBars(hidden = request.immersive)
    }

    /** Applies the last request again, over a display mode or system bars the system restored. */
    fun reapply() {
        last?.let { apply(it, force = true) }
    }

    private fun display(): Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.display
    } else {
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay
    }

    private fun currentResolutionModes(): List<Display.Mode> {
        val display = display() ?: return emptyList()
        val current = display.mode
        return display.supportedModes.filter {
            it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
        }
    }

    /** A rate this screen does not offer falls back to Auto; Profile keeps the choice. */
    private fun applyRefreshRate(requestedHz: Int) {
        val modes = currentResolutionModes()
        val index = hostRefreshRateModeIndex(modes.map { it.refreshRate }, requestedHz)
        if (index < 0) return
        val window = activity.window
        val attributes = window.attributes
        if (attributes.preferredDisplayModeId == modes[index].modeId) return
        attributes.preferredDisplayModeId = modes[index].modeId
        window.attributes = attributes
    }

    /** Hidden bars come back for a moment on a swipe from the screen edge. */
    @Suppress("DEPRECATION")
    private fun applySystemBars(hidden: Boolean) {
        val window = activity.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController ?: return
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (hidden) controller.hide(WindowInsets.Type.systemBars()) else controller.show(WindowInsets.Type.systemBars())
        } else {
            // Keep the edge-to-edge layout flags; only the hiding flags change.
            val hiding = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            val decor = window.decorView
            decor.systemUiVisibility = if (hidden) decor.systemUiVisibility or hiding else decor.systemUiVisibility and hiding.inv()
        }
        systemBarsHidden = hidden
    }
}
