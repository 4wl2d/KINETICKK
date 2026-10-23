// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.browser.window
import kotlin.js.ExperimentalWasmJsInterop

/**
 * The web display host: the browser's fullscreen mode, which is live browser state and never
 * stored (entering it needs a user gesture, so it cannot be restored at load). The browser owns
 * every other display option. Never writes Profile.
 */
class WebDisplayHost internal constructor(private val browser: WebFullscreen) : PlatformDisplayHost {
    constructor() : this(BrowserFullscreen)

    // Esc and the browser's own controls leave fullscreen too: the change event keeps this current.
    private var fullscreen by mutableStateOf(browser.active)

    init {
        browser.onChange { fullscreen = browser.active }
    }

    override fun support(): HostDisplaySupport {
        val touch = browser.touchPrimary
        return HostDisplaySupport(browserFullscreen = browser.enabled, hardwareKeyboard = !touch, touchPrimary = touch)
    }

    override fun apply(request: HostDisplayRequest, force: Boolean) = Unit

    override val browserFullscreen: Boolean
        get() = fullscreen

    /** Must run inside the press or key handler that asked for it (transient user activation). */
    override fun setBrowserFullscreen(on: Boolean) {
        if (on == browser.active) return
        if (on) browser.enter() else browser.exit()
    }
}

/** The browser's fullscreen API as the host uses it. */
internal interface WebFullscreen {
    val enabled: Boolean
    val active: Boolean
    val touchPrimary: Boolean
    fun enter()
    fun exit()
    fun onChange(listener: () -> Unit)
}

@OptIn(ExperimentalWasmJsInterop::class)
private object BrowserFullscreen : WebFullscreen {
    override val enabled: Boolean
        get() = document.fullscreenEnabled

    override val active: Boolean
        get() = document.fullscreenElement != null

    /** No fine pointer (mouse or trackpad) at all: a phone or tablet. */
    override val touchPrimary: Boolean
        get() = !window.matchMedia("(any-pointer: fine)").matches

    // A refused request (no user gesture, a policy) leaves the page as it is.
    override fun enter() {
        document.documentElement?.requestFullscreen()?.catch { null }
    }

    override fun exit() {
        document.exitFullscreen().catch { null }
    }

    override fun onChange(listener: () -> Unit) {
        document.addEventListener("fullscreenchange", { listener() })
    }
}
