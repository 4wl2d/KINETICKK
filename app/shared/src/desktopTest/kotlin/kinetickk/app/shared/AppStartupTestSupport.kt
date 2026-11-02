// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import kotlin.test.assertTrue

/** Waits for real resource IO while also advancing the frame barriers used by startup. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.awaitAppStartup() {
    // Commit the initial/recreated composition before checking absence: an as-yet-empty semantics
    // tree must never count as a completed startup. The root lookup also verifies an attached host.
    mainClock.advanceTimeByFrame()
    waitForIdle()
    onRoot().fetchSemanticsNode()
    waitUntil(timeoutMillis = 30_000) {
        mainClock.advanceTimeByFrame()
        assertTrue(onAllNodesWithTag("startup-retry").fetchSemanticsNodes().isEmpty(),
            "App resource preparation failed; the game must not be exercised behind its retry screen")
        onAllNodesWithTag("startup-loading").fetchSemanticsNodes().isEmpty()
    }
    waitForIdle()
}
