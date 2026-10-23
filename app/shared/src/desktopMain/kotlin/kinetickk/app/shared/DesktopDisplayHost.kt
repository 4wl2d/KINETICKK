// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.awt.DisplayMode
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import kotlin.math.roundToInt

/**
 * The desktop display host. One [windowState] serves every window it opens, so position and size
 * survive a recreation. A decoration (Borderless) or V-Sync change needs a new window: it bumps
 * [generation], which the application keys its window on. Fullscreen and window sizes change the
 * window in place. Requests apply as diffs against the last one, so a window the player resized or
 * maximized keeps that until a size or mode is chosen again. Leaving fullscreen, the platform puts
 * back the bounds it saved on the way in, so the windowed preset waits until the shown window has
 * left fullscreen and the window calls [settle]. The host never writes Profile.
 */
class DesktopDisplayHost internal constructor(
    private val screens: DesktopScreens,
    private val setSystemProperty: (String, String) -> Unit,
    private val shownFullscreen: (Window?) -> Boolean,
) : PlatformDisplayHost {
    constructor() : this(
        AwtDesktopScreens,
        { key, value -> System.setProperty(key, value) },
        { window -> (window as? ComposeWindow)?.placement == WindowPlacement.Fullscreen },
    )

    val windowState: WindowState = WindowState(size = DpSize(DEFAULT_WIDTH_DP.dp, DEFAULT_HEIGHT_DP.dp))

    /** Changes when the window must be opened again (decoration or V-Sync). */
    var generation: Int by mutableIntStateOf(0)
        private set

    /** Whether the next window opens without a title bar (Borderless). */
    var undecorated: Boolean by mutableStateOf(false)
        private set

    /**
     * Whether the next window can be resized at its edges. A Borderless window covers its screen;
     * resizable and undecorated, Compose would lay an edge resizer over the game's outer 8 dp.
     */
    val resizable: Boolean get() = !undecorated

    private var pacing = DesktopPacing(vsync = true, overridden = false)
    private var vsync = true
    private var last: HostDisplayRequest? = null
    private var window: Window? = null

    /** The windowed preset chosen while the shown window was fullscreen; [settle] places it. */
    private var pendingWindowed: HostDisplayRequest? by mutableStateOf(null)

    /** Whether a windowed preset waits for the shown window to leave fullscreen. */
    val leavingFullscreen: Boolean get() = pendingWindowed != null

    /** Before the first window: places it for the stored [request] under the decided [pacing]. */
    fun start(request: HostDisplayRequest, pacing: DesktopPacing) {
        this.pacing = pacing
        vsync = pacing.vsync
        last = request
        undecorated = request.windowMode == HostWindowMode.BORDERLESS
        place(request)
    }

    /** The shown window: its screen decides the offered sizes, the refresh readout and Borderless bounds. */
    fun attach(window: Window) {
        this.window = window
    }

    fun detach(window: Window) {
        if (this.window === window) this.window = null
    }

    override fun support(): HostDisplaySupport {
        val screen = screens.screen(window)
        return HostDisplaySupport(
            windowModes = true,
            usableWidthDp = screen?.usable?.width ?: 0,
            usableHeightDp = screen?.usable?.height ?: 0,
            vsync = !pacing.overridden,
            refreshReadout = true,
            monitorRefreshHz = screen?.refreshHz ?: 0,
            hardwareKeyboard = true,
            touchPrimary = false,
        )
    }

    override fun apply(request: HostDisplayRequest, force: Boolean) {
        val previous = last
        if (!force && request == previous) return
        last = request
        var reopen = false
        if (!pacing.overridden && request.vsync != vsync) {
            // Skiko reads the property when the next window creates its redrawer.
            vsync = request.vsync
            setSystemProperty(DesktopFramePacing.SKIKO_VSYNC_PROPERTY, request.vsync.toString())
            reopen = true
        }
        val modeChanged = force || previous?.windowMode != request.windowMode
        val sizeChanged = previous == null || previous.windowWidthDp != request.windowWidthDp ||
            previous.windowHeightDp != request.windowHeightDp
        if (modeChanged || sizeChanged && request.windowMode == HostWindowMode.WINDOWED) place(request)
        val nextUndecorated = request.windowMode == HostWindowMode.BORDERLESS
        if (nextUndecorated != undecorated) {
            // Decoration cannot change on a shown frame: the window opens again with the new value.
            undecorated = nextUndecorated
            reopen = true
        }
        if (reopen) generation++
    }

    /**
     * On a frame of the shown window: once it has left fullscreen (the platform has restored its
     * saved bounds by then), the pending windowed preset applies, centred.
     */
    fun settle() {
        val pending = pendingWindowed ?: return
        if (shownFullscreen(window)) return
        pendingWindowed = null
        placeWindowed(pending)
    }

    override val liveWindow: HostLiveWindow?
        get() {
            val size = windowState.size.takeIf { it.isSpecified } ?: return null
            val mode = when {
                windowState.placement == WindowPlacement.Fullscreen -> HostWindowMode.FULLSCREEN
                undecorated -> HostWindowMode.BORDERLESS
                else -> HostWindowMode.WINDOWED
            }
            return HostLiveWindow(mode, size.width.value.roundToInt(), size.height.value.roundToInt())
        }

    private fun place(request: HostDisplayRequest) {
        pendingWindowed = null
        when (request.windowMode) {
            HostWindowMode.WINDOWED -> if (shownFullscreen(window)) {
                // Compose applies size and position before placement, so they would land while the
                // window is still fullscreen and the platform's restore would then replace them.
                windowState.placement = WindowPlacement.Floating
                pendingWindowed = request
            } else {
                placeWindowed(request)
            }
            HostWindowMode.BORDERLESS -> {
                windowState.placement = WindowPlacement.Floating
                screens.screen(window)?.bounds?.let { bounds ->
                    windowState.position = WindowPosition(bounds.x.dp, bounds.y.dp)
                    windowState.size = DpSize(bounds.width.dp, bounds.height.dp)
                }
            }
            // Native fullscreen on macOS, GraphicsDevice fullscreen elsewhere.
            HostWindowMode.FULLSCREEN -> windowState.placement = WindowPlacement.Fullscreen
        }
    }

    private fun placeWindowed(request: HostDisplayRequest) {
        val usable = screens.screen(window)?.usable
        windowState.placement = WindowPlacement.Floating
        windowState.size = DpSize(
            (usable?.width?.let { minOf(it, request.windowWidthDp) } ?: request.windowWidthDp).dp,
            (usable?.height?.let { minOf(it, request.windowHeightDp) } ?: request.windowHeightDp).dp,
        )
        windowState.position = WindowPosition(Alignment.Center)
    }

    private companion object {
        const val DEFAULT_WIDTH_DP = 1280
        const val DEFAULT_HEIGHT_DP = 800
    }
}

/** One screen in AWT user-space units (dp in Compose Desktop): its bounds, the part outside bars and docks, its refresh rate. */
internal data class DesktopScreen(val bounds: Rectangle, val usable: Rectangle, val refreshHz: Int)

/** The screen a window is on, or the default screen before a window exists; null when headless. */
internal fun interface DesktopScreens {
    fun screen(window: Window?): DesktopScreen?
}

internal object AwtDesktopScreens : DesktopScreens {
    override fun screen(window: Window?): DesktopScreen? {
        if (GraphicsEnvironment.isHeadless()) return null
        val configuration = window?.graphicsConfiguration
            ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
        val bounds = configuration.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
        val usable = Rectangle(
            bounds.x + insets.left,
            bounds.y + insets.top,
            bounds.width - insets.left - insets.right,
            bounds.height - insets.top - insets.bottom,
        )
        val refresh = configuration.device.displayMode?.refreshRate ?: DisplayMode.REFRESH_RATE_UNKNOWN
        return DesktopScreen(bounds, usable, refresh.coerceAtLeast(0))
    }
}
