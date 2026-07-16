// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.game.resources.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopAudioExecutionPolicyTest {
    @Test
    fun workerQueueAndBestEffortLossPolicyAreFiniteAndExplicit() {
        assertEquals(1, DesktopAudioExecutionPolicy.WORKER_COUNT)
        assertEquals(24, DesktopAudioExecutionPolicy.QUEUE_CAPACITY)
        assertEquals("discard-oldest", DesktopAudioExecutionPolicy.BACKPRESSURE_POLICY)
    }
}
