// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.Projectile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SweptProjectileCollisionTest {
    @Test
    fun movingTargetCrossingAProjectileIsHitOnceWhileDistantHistoryIsRetained() {
        val state = buildTestState()
        val distant = List(24) { index -> state.dummy(x = 2_000f + index * 40f, y = 2_000f) }
        val crossing = state.dummy(x = 100f, y = 100f).apply { previousY = -100f }
        val shot = Projectile(
            x = 200f, y = 0f, vx = 0f, vy = 0f, radius = 2f, life = 2f,
            hostile = false, damage = 20f, pierce = 3, previousX = 0f, previousY = 0f,
        )
        distant.forEach { assertTrue(shot.tryRecordEnemyHit(it.id)) }
        state.projectiles += shot
        val before = crossing.hp

        state.resolveProjectileHits()

        assertTrue(crossing.hp < before, "the relative swept paths cross although both endpoints miss")
        assertTrue(shot.hasRecordedEnemyHit(crossing.id))
        assertEquals(2, shot.pierce)
        distant.forEach { assertTrue(shot.hasRecordedEnemyHit(it.id)) }
        val after = crossing.hp
        state.resolveProjectileHits()
        assertEquals(after, crossing.hp, "the same crossing must not spend another hit or pierce")
        assertEquals(2, shot.pierce)

        val miss = state.dummy(x = 100f, y = 200f)
        state.resolveProjectileHits()
        assertEquals(miss.maxHp, miss.hp)
        assertFalse(shot.hasRecordedEnemyHit(miss.id), "a broad-phase miss cannot enter hit history")
    }
}
