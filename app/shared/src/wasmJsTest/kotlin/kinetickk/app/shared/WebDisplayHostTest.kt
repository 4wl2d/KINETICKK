// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebDisplayHostTest {
    private class FakeFullscreen(
        override val enabled: Boolean = true,
        override val touchPrimary: Boolean = false,
    ) : WebFullscreen {
        override var active = false
        val calls = ArrayList<String>()
        private var listener: () -> Unit = {}

        override fun enter() {
            calls += "enter"
        }

        override fun exit() {
            calls += "exit"
        }

        override fun onChange(listener: () -> Unit) {
            this.listener = listener
        }

        /** The browser entered or left fullscreen (the request was granted, or Esc was pressed). */
        fun change(active: Boolean) {
            this.active = active
            listener()
        }
    }

    @Test
    fun theToggleAsksTheBrowserAndTheStateFollowsTheBrowsersChangeEvents() {
        val browser = FakeFullscreen()
        val host = WebDisplayHost(browser)
        assertFalse(host.browserFullscreen)
        host.setBrowserFullscreen(true)
        // Requested, not yet granted: the state stays until the browser reports the change.
        assertEquals(listOf("enter"), browser.calls)
        assertFalse(host.browserFullscreen)
        browser.change(active = true)
        assertTrue(host.browserFullscreen)
        // Already fullscreen: nothing is asked again.
        host.setBrowserFullscreen(true)
        assertEquals(listOf("enter"), browser.calls)
        // Esc in the browser leaves fullscreen without the game asking.
        browser.change(active = false)
        assertFalse(host.browserFullscreen)
        host.setBrowserFullscreen(true)
        browser.change(active = true)
        host.setBrowserFullscreen(false)
        assertEquals(listOf("enter", "enter", "exit"), browser.calls)
    }

    @Test
    fun supportOffersFullscreenWhereTheBrowserAllowsItAndKeysWithoutTouch() {
        assertEquals(
            HostDisplaySupport(browserFullscreen = true, hardwareKeyboard = true, touchPrimary = false),
            WebDisplayHost(FakeFullscreen()).support(),
        )
        assertEquals(
            HostDisplaySupport(browserFullscreen = false, hardwareKeyboard = false, touchPrimary = true),
            WebDisplayHost(FakeFullscreen(enabled = false, touchPrimary = true)).support(),
        )
        // Nothing else is applied by a browser page.
        val browser = FakeFullscreen()
        WebDisplayHost(browser).apply(HostDisplayRequest(HostWindowMode.FULLSCREEN, immersive = true), force = true)
        assertEquals(emptyList(), browser.calls)
    }

    @Test
    fun theBrowserBindingReadsThePageAndARefusedRequestLeavesItAsItIs() {
        val host = WebDisplayHost()
        val support = host.support()
        assertEquals(!support.touchPrimary, support.hardwareKeyboard)
        assertFalse(host.browserFullscreen)
        // Outside a user gesture the browser refuses; the refusal is consumed and nothing changes.
        host.setBrowserFullscreen(true)
        assertFalse(host.browserFullscreen)
        host.setBrowserFullscreen(false)
    }
}
