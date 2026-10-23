// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopDisplayHostTest {
    // A 1920×1080 screen at (1920, 0) whose bars leave 1920×1030 usable.
    private val screen = DesktopScreen(Rectangle(1920, 0, 1920, 1080), Rectangle(1920, 25, 1920, 1030), refreshHz = 144)
    private val written = ArrayList<Pair<String, String>>()

    private fun host(
        request: HostDisplayRequest = HostDisplayRequest(),
        pacing: DesktopPacing = DesktopPacing(vsync = true, overridden = false),
        screen: DesktopScreen? = this.screen,
    ) = DesktopDisplayHost({ screen }, { key, value -> written += key to value }, { false }).apply { start(request, pacing) }

    @Test
    fun theStoredRequestPlacesTheFirstWindowWithoutReopeningIt() {
        val windowed = host(HostDisplayRequest(windowWidthDp = 1600, windowHeightDp = 900))
        assertEquals(0, windowed.generation)
        assertFalse(windowed.undecorated)
        assertEquals(WindowPlacement.Floating, windowed.windowState.placement)
        assertEquals(DpSize(1600.dp, 900.dp), windowed.windowState.size)
        assertEquals(WindowPosition(Alignment.Center), windowed.windowState.position)

        val borderless = host(HostDisplayRequest(HostWindowMode.BORDERLESS))
        assertEquals(0, borderless.generation)
        assertTrue(borderless.undecorated)
        assertEquals(WindowPosition(1920.dp, 0.dp), borderless.windowState.position)
        assertEquals(DpSize(1920.dp, 1080.dp), borderless.windowState.size)

        assertEquals(WindowPlacement.Fullscreen, host(HostDisplayRequest(HostWindowMode.FULLSCREEN)).windowState.placement)
        assertEquals(emptyList(), written)
    }

    @Test
    fun onlyDecorationAndVsyncChangesOpenTheWindowAgain() {
        val host = host()
        val windowed = HostDisplayRequest()
        host.apply(windowed.copy(windowMode = HostWindowMode.FULLSCREEN))
        assertEquals(0, host.generation)
        assertEquals(WindowPlacement.Fullscreen, host.windowState.placement)

        host.apply(windowed.copy(windowMode = HostWindowMode.BORDERLESS))
        assertEquals(1, host.generation)
        assertTrue(host.undecorated)
        assertEquals(WindowPlacement.Floating, host.windowState.placement)
        assertEquals(WindowPosition(1920.dp, 0.dp), host.windowState.position)
        assertEquals(DpSize(1920.dp, 1080.dp), host.windowState.size)

        // Borderless → Fullscreen needs the title bar back: a new window.
        host.apply(windowed.copy(windowMode = HostWindowMode.FULLSCREEN))
        assertEquals(2, host.generation)
        assertFalse(host.undecorated)

        host.apply(windowed.copy(windowMode = HostWindowMode.FULLSCREEN, windowWidthDp = 1366, windowHeightDp = 768))
        host.apply(windowed.copy(windowMode = HostWindowMode.FULLSCREEN, windowWidthDp = 1366, windowHeightDp = 768, refreshRateHz = 90))
        host.apply(windowed.copy(windowWidthDp = 1366, windowHeightDp = 768))
        assertEquals(2, host.generation)
        assertEquals(WindowPlacement.Floating, host.windowState.placement)
        // The size chosen while fullscreen applies on the way back to a window.
        assertEquals(DpSize(1366.dp, 768.dp), host.windowState.size)
        assertEquals(WindowPosition(Alignment.Center), host.windowState.position)

        host.apply(windowed.copy(windowWidthDp = 1366, windowHeightDp = 768, vsync = false))
        assertEquals(3, host.generation)
        assertEquals(listOf(DesktopFramePacing.SKIKO_VSYNC_PROPERTY to "false"), written)
        host.apply(windowed.copy(windowWidthDp = 1366, windowHeightDp = 768, vsync = true))
        assertEquals(4, host.generation)
        assertEquals(DesktopFramePacing.SKIKO_VSYNC_PROPERTY to "true", written.last())
    }

    @Test
    fun leavingFullscreenPlacesTheStoredPresetOnlyOnceTheWindowHasLeftIt() {
        var fullscreen = false
        val host = DesktopDisplayHost({ screen }, { _, _ -> }, { fullscreen })
        host.start(HostDisplayRequest(HostWindowMode.FULLSCREEN, windowWidthDp = 1600, windowHeightDp = 900), DesktopPacing(vsync = true, overridden = false))
        // The window opened at the default size and went fullscreen; Compose copied the screen bounds.
        fullscreen = true
        host.windowState.size = DpSize(1920.dp, 1080.dp)
        host.windowState.position = WindowPosition(1920.dp, 0.dp)

        host.apply(HostDisplayRequest(windowWidthDp = 1600, windowHeightDp = 900))
        assertEquals(WindowPlacement.Floating, host.windowState.placement)
        host.settle()
        // Leaving fullscreen, the JDK puts back the bounds it saved on the way in (here the default
        // size at the platform's spot), after anything written while the window was still fullscreen.
        fullscreen = false
        host.windowState.size = DpSize(1280.dp, 800.dp)
        host.windowState.position = WindowPosition(1960.dp, 40.dp)
        host.settle()
        assertEquals(DpSize(1600.dp, 900.dp), host.windowState.size)
        assertEquals(WindowPosition(Alignment.Center), host.windowState.position)
        assertEquals(HostLiveWindow(HostWindowMode.WINDOWED, 1600, 900), host.liveWindow)

        // Placed once: a size the player drags to afterwards stays.
        host.windowState.size = DpSize(1500.dp, 950.dp)
        host.settle()
        assertEquals(DpSize(1500.dp, 950.dp), host.windowState.size)

        // Fullscreen chosen again before the window left it: no preset waits any more.
        host.apply(HostDisplayRequest(HostWindowMode.FULLSCREEN, windowWidthDp = 1600, windowHeightDp = 900))
        fullscreen = true
        host.apply(HostDisplayRequest(windowWidthDp = 1366, windowHeightDp = 768))
        assertTrue(host.leavingFullscreen)
        host.apply(HostDisplayRequest(HostWindowMode.FULLSCREEN, windowWidthDp = 1366, windowHeightDp = 768))
        assertFalse(host.leavingFullscreen)
        assertEquals(WindowPlacement.Fullscreen, host.windowState.placement)
    }

    @Test
    fun aBorderlessWindowHasNoResizableEdges() {
        // Undecorated and resizable, Compose lays an edge resizer over the outer 8 dp of the content.
        assertFalse(host(HostDisplayRequest(HostWindowMode.BORDERLESS)).resizable)
        val host = host()
        assertTrue(host.resizable)
        host.apply(HostDisplayRequest(HostWindowMode.BORDERLESS))
        assertFalse(host.resizable)
        host.apply(HostDisplayRequest(HostWindowMode.FULLSCREEN))
        assertTrue(host.resizable)
        host.apply(HostDisplayRequest(HostWindowMode.BORDERLESS))
        host.apply(HostDisplayRequest())
        assertTrue(host.resizable)
    }

    @Test
    fun aLaunchOverrideKeepsVsyncAndHidesItsRow() {
        val host = host(pacing = DesktopPacing(vsync = false, overridden = true))
        assertFalse(host.support().vsync)
        host.apply(HostDisplayRequest(vsync = false))
        host.apply(HostDisplayRequest(vsync = true))
        assertEquals(0, host.generation)
        assertEquals(emptyList(), written)
        assertTrue(host(pacing = DesktopPacing(vsync = false, overridden = false)).support().vsync)
    }

    @Test
    fun anUnrelatedChangeKeepsAWindowThePlayerResizedAndForceRestoresTheStoredOne() {
        val host = host()
        // The player drags the window larger and maximizes it (OS-driven, never stored).
        host.windowState.size = DpSize(1500.dp, 950.dp)
        host.windowState.placement = WindowPlacement.Maximized
        host.apply(HostDisplayRequest())
        host.apply(HostDisplayRequest(refreshRateHz = 120, immersive = true))
        assertEquals(DpSize(1500.dp, 950.dp), host.windowState.size)
        assertEquals(WindowPlacement.Maximized, host.windowState.placement)
        assertEquals(HostLiveWindow(HostWindowMode.WINDOWED, 1500, 950), host.liveWindow)

        host.apply(HostDisplayRequest(refreshRateHz = 120, immersive = true), force = true)
        assertEquals(DpSize(1280.dp, 800.dp), host.windowState.size)
        assertEquals(WindowPlacement.Floating, host.windowState.placement)
        assertEquals(0, host.generation)

        // macOS fullscreen left from the green button: the live mode differs until forced again.
        host.apply(HostDisplayRequest(HostWindowMode.FULLSCREEN))
        host.windowState.placement = WindowPlacement.Floating
        assertEquals(HostWindowMode.WINDOWED, host.liveWindow?.mode)
        host.apply(HostDisplayRequest(HostWindowMode.FULLSCREEN), force = true)
        assertEquals(HostWindowMode.FULLSCREEN, host.liveWindow?.mode)
    }

    @Test
    fun aPresetLargerThanTheUsableAreaIsClampedToIt() {
        val host = host(screen = DesktopScreen(Rectangle(0, 0, 1440, 900), Rectangle(0, 25, 1440, 805), refreshHz = 60))
        host.apply(HostDisplayRequest(windowWidthDp = 1920, windowHeightDp = 1080))
        assertEquals(DpSize(1440.dp, 805.dp), host.windowState.size)
        host.apply(HostDisplayRequest(windowWidthDp = 1280, windowHeightDp = 800))
        assertEquals(DpSize(1280.dp, 800.dp), host.windowState.size)
    }

    @Test
    fun supportListsTheUsableAreaAndTheMonitorRefreshRate() {
        assertEquals(
            HostDisplaySupport(
                windowModes = true, usableWidthDp = 1920, usableHeightDp = 1030, vsync = true,
                refreshReadout = true, monitorRefreshHz = 144, hardwareKeyboard = true,
            ),
            host().support(),
        )
        // Headless or unknown: no sizes are offered and the readout shows "unknown".
        assertEquals(
            HostDisplaySupport(windowModes = true, vsync = true, refreshReadout = true),
            host(screen = null).support(),
        )
        assertEquals(
            listOf(WindowSizeOffer(1280, 720), WindowSizeOffer(1280, 800), WindowSizeOffer(1366, 768), WindowSizeOffer(1440, 900),
                WindowSizeOffer(1600, 900)),
            host().support().toSettingsSupport().windowSizes.map { WindowSizeOffer(it.widthDp, it.heightDp) },
        )
    }

    private data class WindowSizeOffer(val width: Int, val height: Int)
}
