// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.runtime.ComposeRuntimeFlags
import androidx.compose.runtime.ExperimentalComposeApi
import kotlin.test.Test
import kotlin.test.assertTrue

class ComposeRuntimeOptimizationTest {
    @OptIn(ExperimentalComposeApi::class)
    @Test
    fun applicationBootstrapEnablesTheLinkBufferComposerIdempotently() {
        enableKinetickkComposeRuntimeOptimizations()
        enableKinetickkComposeRuntimeOptimizations()

        assertTrue(ComposeRuntimeFlags.isLinkBufferComposerEnabled)
    }
}
