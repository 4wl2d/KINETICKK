// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.foundation.collections.immutableListOf
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LeaderLeashTest {
    @Test
    fun anEliteLeftBehindByFarTravelReturnsWithoutBeingKilledOrReplaced() {
        checkFarLeader(EnemyType.ELITE)
    }

    @Test
    fun aHeraldLeftBehindByFarTravelReturnsWithoutBeingKilledOrReplaced() {
        checkFarLeader(EnemyType.HERALD)
    }

    private fun checkFarLeader(type: EnemyType) {
        // The two fuzz witnesses, plus independent runs; none belongs to the balance-gate window.
        for (seed in listOf(27_051_317, 43_459_485, 731_991, 12_345)) {
            for ((width, height) in listOf(1_280f to 720f, 844f to 390f)) {
                val state = arena(seed).apply { resize(width, height) }
                val leader = state.spawn(type, -12_000f).apply { hp = maxHp * 0.35f }
                val id = leader.id
                val hp = leader.hp
                val aspect = leader.aspect
                val affix = leader.affix
                val streams = state.streams()
                val rewards = state.rewards()

                state.updateEnemies(0f)

                assertSame(leader, state.enemies.single(), "$type keeps its identity")
                assertTrue(state.distance(leader) < state.ordinaryLeash(), "$type never returned from far travel")
                assertTrue(
                    abs(leader.x - state.coreX) > width * 0.5f || abs(leader.y - state.coreY) > height * 0.5f,
                    "$type appeared inside the viewport",
                )
                assertEquals(id, leader.id)
                assertEquals(hp, leader.hp)
                assertEquals(aspect, leader.aspect)
                assertEquals(affix, leader.affix)
                assertEquals(leader.x, leader.previousX, "a teleport has no swept path")
                assertEquals(leader.y, leader.previousY)
                assertEquals(0f, leader.vx)
                assertEquals(0f, leader.vy)
                assertEquals(streams, state.streams(), "the new relocation draws from no existing stream")
                assertEquals(rewards, state.rewards(), "returning a live leader grants no reward")
            }
        }
    }

    @Test
    fun leadersAllowAnOrdinaryRetreatButReturnBeyondSixThousandUnits() {
        for (type in listOf(EnemyType.ELITE, EnemyType.HERALD)) {
            val state = arena()
            val leader = state.spawn(type, -6_000f)
            state.updateEnemies(0f)
            assertEquals(-6_000f, leader.x, "$type can be left several screens behind")
            leader.x = -6_001f
            state.updateEnemies(0f)
            assertTrue(state.distance(leader) < state.ordinaryLeash(), "$type did not return past the far-travel leash")
        }
    }

    @Test
    fun aWideViewportKeepsTheLeaderLeashBeyondItsOrdinaryLeash() {
        val state = arena().apply { resize(8_000f, 4_000f) }
        val leader = state.spawn(EnemyType.ELITE, -7_000f)
        state.updateEnemies(0f)
        assertEquals(-7_000f, leader.x)
        leader.x = -state.ordinaryLeash() - 1f
        state.updateEnemies(0f)
        assertTrue(state.distance(leader) < state.ordinaryLeash())
    }

    @Test
    fun avoidingAnOfferedSiteNeverMovesALeaderBackInsideTheViewport() {
        var redirected = 0
        for (type in listOf(EnemyType.ELITE, EnemyType.HERALD)) {
            for (seed in 1..64) {
                val state = arena(seed)
                val leader = state.spawn(type, -12_000f)
                val withoutSite = state.copyForReduction()
                // A site beyond the short viewport edge can redirect a returning leader toward the long edge.
                state.sites = listOf(SiteState(
                    id = 7L,
                    kind = SiteKind.RESONANT_CIRCUIT,
                    x = 0f,
                    y = 500f,
                    expiresAt = 1_000f,
                ))

                state.updateEnemies(0f)
                withoutSite.updateEnemies(0f)

                val direct = withoutSite.enemies.single()
                if (leader.x != direct.x || leader.y != direct.y) redirected++
                assertTrue(
                    abs(leader.x - state.coreX) > state.screenWidth * 0.5f ||
                        abs(leader.y - state.coreY) > state.screenHeight * 0.5f,
                    "$type appeared in view while avoiding the offered site: seed=$seed at (${leader.x}, ${leader.y})",
                )
            }
        }
        assertTrue(redirected > 0, "the offered site must redirect at least one returning leader")
    }

    @Test
    fun placeBoundLeadersAndOtherBossesKeepTheirPlaces() {
        val state = arena()
        val held = listOf(
            state.spawn(EnemyType.ELITE, -12_000f).apply { ownerTag = 17L },
            state.spawn(EnemyType.HERALD, -14_000f).apply { ownerTag = 18L },
            state.spawn(EnemyType.ELITE, -16_000f),
            state.spawn(EnemyType.HERALD, -18_000f),
            state.spawn(EnemyType.ARCHITECT, -20_000f),
            state.spawn(EnemyType.WARDEN_PRIME, -22_000f),
            state.spawn(EnemyType.CARTOGRAPHER, -24_000f),
        )
        state.sites = listOf(SiteState(
            id = 7L,
            kind = SiteKind.RESONANT_CIRCUIT,
            x = -17_000f,
            y = 0f,
            expiresAt = 1_000f,
            active = true,
            foeIds = immutableListOf(held[2].id, held[3].id),
        ))
        val positions = held.map { it.x to it.y }
        held.forEach { assertTrue(state.leashExempt(it), "${it.type} must stay with its place or boss fight") }

        state.updateEnemies(0f)

        assertEquals(positions, held.map { it.x to it.y })
    }

    @Test
    fun leaderRelocationCopiesDeterministicallyAndLeavesOrdinarySpawnDrawsUnchanged() {
        val source = arena()
        val leader = source.spawn(EnemyType.HERALD, -12_000f)
        val ordinary = source.spawn(EnemyType.DRIFTER, 12_000f)
        val first = source.copyForReduction()
        val second = source.copyForReduction()
        val withoutLeader = source.copyForReduction().apply { enemies.removeAt(0) }
        val expectedOrdinaryStream = source.spawnRandom.copy().apply { repeat(4) { nextFloat() } }.snapshot()

        first.updateEnemies(0f)
        second.updateEnemies(0f)
        withoutLeader.updateEnemies(0f)

        assertEquals(first.enemies.map { it.x to it.y }, second.enemies.map { it.x to it.y })
        assertTrue(first.distance(first.enemies.first()) < first.ordinaryLeash(), "the copied leader must return")
        val relocatedOrdinary = first.enemies.single { it.id == ordinary.id }
        assertEquals(withoutLeader.enemies.single().x, relocatedOrdinary.x)
        assertEquals(withoutLeader.enemies.single().y, relocatedOrdinary.y)
        assertEquals(expectedOrdinaryStream, first.spawnRandom.snapshot())
        assertEquals(first.streams(), second.streams())
        assertEquals(first.streams(), withoutLeader.streams())
        assertEquals(-12_000f, leader.x, "reducing a copy must not move the committed leader")
        assertEquals(12_000f, ordinary.x, "reducing a copy must not move the committed ordinary enemy")
    }

    private fun arena(seed: Int = 27_051_317): MutableGameState = buildTestState(seed = seed).apply {
        clearObstaclesForTesting()
        updatePointer(screenWidth, screenHeight * 0.5f)
    }

    private fun MutableGameState.spawn(type: EnemyType, x: Float): Enemy =
        spawnedEnemy(type, x, 0f).also {
            it.actionTimer = Float.MAX_VALUE
            it.abilityAt = Float.MAX_VALUE
            it.phaseStartPending = false
            enemies += it
        }

    private fun MutableGameState.distance(enemy: Enemy): Float = length(enemy.x - coreX, enemy.y - coreY)

    private fun MutableGameState.ordinaryLeash(): Float = max(screenWidth, screenHeight) * 1.15f + 360f

    private fun MutableGameState.streams() = listOf(
        spawnRandom.snapshot(), gameplayRandom.snapshot(), rewardRandom.snapshot(), siteRandom.snapshot(),
    )

    private fun MutableGameState.rewards() = listOf(
        kills.toLong(), eliteKills.toLong(), keys.toLong(), data.toLong(), runMatter, pendingBankedMatter,
        pendingRelicChoices.toLong(), pickups.size.toLong(),
    )
}
