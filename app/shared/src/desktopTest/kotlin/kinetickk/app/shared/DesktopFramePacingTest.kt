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
            assertTrue(DesktopFramePacing.apply(property, environment, currentVsync = null) { key, value -> written[key] = value })
            assertEquals(mapOf(DesktopFramePacing.SKIKO_VSYNC_PROPERTY to "false"), written, "$property / $environment")
        }
    }

    @Test
    fun theDefaultKeepsVsyncAndAnExplicitSkikoChoiceWins() {
        listOf(null to null, "false" to null, "yes" to "0", null to "").forEach { (property, environment) ->
            assertFalse(DesktopFramePacing.apply(property, environment, currentVsync = null) { _, _ -> error("no write expected") })
        }
        assertFalse(DesktopFramePacing.apply("true", null, currentVsync = "true") { _, _ -> error("explicit V-Sync is kept") })
        assertTrue(DesktopFramePacing.apply("true", null, currentVsync = "false") { _, _ -> error("already uncapped") })
    }
}
