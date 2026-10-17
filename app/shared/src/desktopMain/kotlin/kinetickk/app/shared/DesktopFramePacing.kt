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
     * Turns V-Sync off when the uncapped mode is requested and returns whether it is. Must run
     * before `application {}`; an explicit `skiko.vsync.enabled` set by the user is respected.
     */
    fun apply(
        property: String? = System.getProperty(UNCAPPED_PROPERTY),
        environment: String? = System.getenv(UNCAPPED_ENVIRONMENT),
        currentVsync: String? = System.getProperty(SKIKO_VSYNC_PROPERTY),
        setSystemProperty: (String, String) -> Unit = { key, value -> System.setProperty(key, value) },
    ): Boolean {
        if (!uncappedRequested(property, environment)) return false
        if (currentVsync == null) setSystemProperty(SKIKO_VSYNC_PROPERTY, "false")
        return currentVsync == null || currentVsync.equals("false", ignoreCase = true)
    }
}
