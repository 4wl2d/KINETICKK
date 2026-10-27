// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

/**
 * Desktop frame pacing, decided before the first window opens. By default Compose Desktop waits
 * for V-Sync, so the frame rate equals the display refresh rate (already above 120 fps on a
 * 144 or 240 Hz monitor). The uncapped mode turns V-Sync off; the fixed-step simulation and the
 * render interpolation keep the game identical at any frame rate.
 */
object DesktopFramePacing {
    /** `-Dkinetickk.uncapped=true` (the Gradle `run` task forwards `-Pkinetickk.uncapped=true`). */
    const val UNCAPPED_PROPERTY: String = "kinetickk.uncapped"

    /** `KINETICKK_UNCAPPED_FPS=1` in the environment. */
    const val UNCAPPED_ENVIRONMENT: String = "KINETICKK_UNCAPPED_FPS"

    /** Read by Skiko when it creates a window's redrawer. */
    const val SKIKO_VSYNC_PROPERTY: String = "skiko.vsync.enabled"

    fun uncappedRequested(property: String?, environment: String?): Boolean =
        property?.trim().equals("true", ignoreCase = true) ||
            environment?.trim().let { it == "1" || it.equals("true", ignoreCase = true) }

    /**
     * Decides V-Sync before `application {}` and writes it for Skiko when it must be off. Precedence:
     * an explicit `skiko.vsync.enabled` set by the user, then the uncapped property or variable, then
     * the stored [preferenceVsync]. Either launch setting makes the choice [DesktopPacing.overridden],
     * which hides the V-Sync row.
     */
    fun apply(
        property: String? = System.getProperty(UNCAPPED_PROPERTY),
        environment: String? = System.getenv(UNCAPPED_ENVIRONMENT),
        currentVsync: String? = System.getProperty(SKIKO_VSYNC_PROPERTY),
        preferenceVsync: Boolean = true,
        setSystemProperty: (String, String) -> Unit = { key, value -> System.setProperty(key, value) },
    ): DesktopPacing {
        // Skiko reads the property with toBoolean(): anything but "true" turns V-Sync off.
        if (currentVsync != null) return DesktopPacing(vsync = currentVsync.trim().toBoolean(), overridden = true)
        if (uncappedRequested(property, environment)) {
            setSystemProperty(SKIKO_VSYNC_PROPERTY, "false")
            return DesktopPacing(vsync = false, overridden = true)
        }
        if (!preferenceVsync) setSystemProperty(SKIKO_VSYNC_PROPERTY, "false")
        return DesktopPacing(vsync = preferenceVsync, overridden = false)
    }
}

/** V-Sync as decided before the first window; [overridden] when a launch setting decided it. */
data class DesktopPacing(val vsync: Boolean, val overridden: Boolean)
