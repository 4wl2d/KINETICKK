// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EasyModeTuningTest {
    @Test
    fun easyModeTuningAcceptsItsBoundsAndRejectsTheNextValue() {
        val shipped = EasyModeTuning()
        assertEquals(0.5f, shipped.incomingDamageMultiplier)
        assertEquals(44f, shipped.boopClearance)
        assertEquals(0.6f, shipped.boopRestitution)
        assertEquals(280f, shipped.boopMinimumOutwardSpeed)
        assertEquals(0.5f, shipped.lethalObstacleDamageShare)
        assertEquals(1f, shipped.obstacleIntegrityFloor)

        // field to (accepted bounds, rejected next values)
        val cases: List<Triple<String, List<Float>, (Float) -> EasyModeTuning>> = listOf(
            Triple("incomingDamageMultiplier", listOf(0.1f, 1f, 0.0999f, 1.0001f)) { EasyModeTuning(incomingDamageMultiplier = it) },
            Triple("boopClearance", listOf(23f, 120f, 22.99f, 120.01f)) { EasyModeTuning(boopClearance = it) },
            Triple("boopRestitution", listOf(0f, 1f, -0.01f, 1.01f)) { EasyModeTuning(boopRestitution = it) },
            Triple("boopMinimumOutwardSpeed", listOf(0f, 900f, -0.01f, 900.1f)) { EasyModeTuning(boopMinimumOutwardSpeed = it) },
            Triple("lethalObstacleDamageShare", listOf(0f, 1f, -0.01f, 1.01f)) { EasyModeTuning(lethalObstacleDamageShare = it) },
            Triple("obstacleIntegrityFloor", listOf(1f, 10f, 0.99f, 10.01f)) { EasyModeTuning(obstacleIntegrityFloor = it) },
        )
        cases.forEach { (name, values, build) ->
            build(values[0])
            build(values[1])
            assertFailsWith<IllegalArgumentException>("$name ${values[2]}") { build(values[2]) }
            assertFailsWith<IllegalArgumentException>("$name ${values[3]}") { build(values[3]) }
            assertFailsWith<IllegalArgumentException>("$name NaN") { build(Float.NaN) }
        }
    }
}
