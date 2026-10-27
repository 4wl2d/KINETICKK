// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

class CoreSteeringTest {
    @Test
    fun theAimAsksTheLawForTheRequestedAccelerationAtFullAuthority() {
        val state = buildTestState().apply {
            polarityStability = 1f
            overdriveTime = 0f
            braking = false
        }
        val velocities = listOf(0f to 0f, 640f to -210f, -1_200f to 900f)
        val requests = listOf(900f to 0f, -400f to 1_300f, 150f to -2_000f, -1_700f to -1_700f)
        for ((velocityX, velocityY) in velocities) for ((requestX, requestY) in requests) {
            val aim = state.steeringAimFor(requestX, requestY)
            val tetherX = cos(aim.heading) * aim.distance
            val tetherY = sin(aim.heading) * aim.distance
            val law = state.steeringAcceleration(tetherX, tetherY, tetherX, tetherY, velocityX, velocityY)
            val case = "request=($requestX, $requestY) velocity=($velocityX, $velocityY)"
            assertEquals(requestX, law.x, abs(requestX) * 1e-4f + 0.05f, case)
            assertEquals(requestY, law.y, abs(requestY) * 1e-4f + 0.05f, case)
        }
    }
}
