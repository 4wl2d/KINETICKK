// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GradleOptimizationProfileTest {
    @Test
    fun isolatedProjectsProfileAcceptsOnlyCanonicalBooleans() {
        assertTrue(parseIsolatedProjectsProfile("true"))
        assertFalse(parseIsolatedProjectsProfile("false"))

        listOf("TRUE", "False", "1", "yes", "", " true ").forEach { value ->
            assertFailsWith<GradleException>(value) {
                parseIsolatedProjectsProfile(value)
            }
        }
    }
}
