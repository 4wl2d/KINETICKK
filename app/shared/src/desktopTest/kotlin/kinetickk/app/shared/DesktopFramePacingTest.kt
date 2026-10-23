// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopFramePacingTest {
    @Test
    fun thePropertyOrTheEnvironmentTurnsVsyncOff() {
        listOf("true" to null, "TRUE" to null, null to "1", null to "true", "false" to "1").forEach { (property, environment) ->
            val written = mutableMapOf<String, String>()
            val pacing = DesktopFramePacing.apply(property, environment, currentVsync = null) { key, value -> written[key] = value }
            assertFalse(pacing.vsync)
            assertTrue(pacing.overridden)
            assertEquals(mapOf(DesktopFramePacing.SKIKO_VSYNC_PROPERTY to "false"), written, "$property / $environment")
        }
    }

    @Test
    fun theDefaultKeepsVsyncAndAnExplicitSkikoChoiceWins() {
        listOf(null to null, "false" to null, "yes" to "0", null to "").forEach { (property, environment) ->
            assertTrue(DesktopFramePacing.apply(property, environment, currentVsync = null) { _, _ -> error("no write expected") }.vsync)
        }
        assertTrue(DesktopFramePacing.apply("true", null, currentVsync = "true") { _, _ -> error("explicit V-Sync is kept") }.vsync)
        assertFalse(DesktopFramePacing.apply("true", null, currentVsync = "false") { _, _ -> error("already uncapped") }.vsync)
    }

    @Test
    fun theStoredPreferenceDecidesOnlyWithoutALaunchOverride() {
        val written = mutableMapOf<String, String>()
        assertEquals(
            DesktopPacing(vsync = false, overridden = false),
            DesktopFramePacing.apply(null, null, currentVsync = null, preferenceVsync = false) { key, value -> written[key] = value },
        )
        assertEquals(mapOf(DesktopFramePacing.SKIKO_VSYNC_PROPERTY to "false"), written)
        assertEquals(
            DesktopPacing(vsync = true, overridden = false),
            DesktopFramePacing.apply(null, null, currentVsync = null, preferenceVsync = true) { _, _ -> error("V-Sync is Skiko's default") },
        )
        // The uncapped flag beats a stored "on"; an explicit Skiko property beats both and a stored "off".
        assertEquals(
            DesktopPacing(vsync = false, overridden = true),
            DesktopFramePacing.apply("true", null, currentVsync = null, preferenceVsync = true) { _, _ -> },
        )
        assertEquals(
            DesktopPacing(vsync = true, overridden = true),
            DesktopFramePacing.apply(null, null, currentVsync = "true", preferenceVsync = false) { _, _ -> error("explicit V-Sync is kept") },
        )
        assertEquals(
            DesktopPacing(vsync = false, overridden = true),
            DesktopFramePacing.apply(null, "1", currentVsync = "no", preferenceVsync = true) { _, _ -> error("explicit V-Sync is kept") },
        )
    }
}
