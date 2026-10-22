// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.gameplay.nucleus.model.CombatFieldKind
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reaction zones sit on the Architect for 30 s without taking its fight away: a Time Bubble, a Maw and
 * an Attractor never stop it firing (volleys ≥ 90 % of the same fight without the zone), and none of them
 * writes its attack timer, shift or stun. A Hush silences it only through Silence's own boss rule.
 */
class ArchitectZoneTest {
    @Test
    fun timeBubbleMawAndAttractorLeaveTheArchitectsVolleysAndTimersAlone() {
        for (reaction in listOf(ReactionId.STASIS, ReactionId.NULL_WELL, ReactionId.MAGNETIZE)) {
            val zoned = fight(reaction, canonicalGameplayContent)
            val plain = fight(reaction, contentWithoutReactionVerbs)
            assertTrue(zoned.zoneSteps > 0, "$reaction kept a zone on the Architect")
            assertTrue(zoned.volleys >= plain.volleys * 0.9f, "$reaction volleys ${zoned.volleys} of ${plain.volleys}")
            assertEquals(plain.actionTimers, zoned.actionTimers, "$reaction never writes the attack timer")
            assertEquals(0f, zoned.latestShift, "$reaction never starts a shift")
            assertEquals(0f, zoned.latestStun, "$reaction never stops the Architect")
        }
    }

    @Test
    fun aHushOnTheArchitectStillLeavesItsAttackWindows() {
        val hushed = fight(ReactionId.SILENCE, canonicalGameplayContent)
        val plain = fight(ReactionId.SILENCE, contentWithoutReactionVerbs)
        assertTrue(hushed.zoneSteps > 0)
        assertTrue(hushed.longestQuietSeconds <= SILENCE_BOSS_SECONDS + 0.02f, "no Silence is extended")
        assertTrue(hushed.quietShare < 0.4f, "the Architect attacks most of the time (${hushed.quietShare})")
        assertEquals(plain.actionTimers, hushed.actionTimers)
        assertEquals(0f, hushed.latestShift)
    }

    private class Fight(
        val volleys: Int,
        val zoneSteps: Int,
        val actionTimers: List<Float>,
        val latestShift: Float,
        val latestStun: Float,
        val quietShare: Float,
        val longestQuietSeconds: Float,
    )

    /** 30 s of the Architect's fight, setting [reaction] off on it every 2.1 s (past its pair cooldown). */
    private fun fight(reaction: ReactionId, content: GameplayContentSnapshot): Fight {
        val state = buildTestState(content = content)
        state.clearObstaclesForTesting()
        state.bossSpawned = true
        state.maxHp = 1_000_000f
        state.hp = state.maxHp
        val boss = state.addEnemyForTesting(380f, 0f, hp = 10_000_000f, radius = 74f, type = EnemyType.ARCHITECT)
        val zone = when (reaction) {
            ReactionId.STASIS -> CombatFieldKind.TIME_BUBBLE
            ReactionId.NULL_WELL -> CombatFieldKind.NULL_WELL
            ReactionId.MAGNETIZE -> CombatFieldKind.ATTRACTOR
            else -> CombatFieldKind.HUSH
        }
        val step = MutableGameState.FIXED_STEP
        val start = state.elapsed
        var nextAt = start
        var volleys = 0
        var zoneSteps = 0
        var latestShift = 0f
        var latestStun = 0f
        var quiet = 0
        var quietRun = 0
        var longestQuiet = 0
        var steps = 0
        val timers = ArrayList<Float>()
        while (state.elapsed < start + 30f) {
            if (state.elapsed >= nextAt) {
                state.setOff(reaction, boss)
                nextAt += 2.1f
            }
            val shots = state.projectiles.size
            state.elapsed += step
            state.updateEnemies(step)
            state.updateStatuses(step)
            state.updateCombatFields(step)
            state.updateCoreReactionStates()
            state.updateBossEncounter()
            if (state.projectiles.size > shots) volleys++
            if (state.fieldRecords.any { it.kind == zone }) zoneSteps++
            latestShift = max(latestShift, boss.shiftUntil)
            latestStun = max(latestStun, boss.disabledUntil)
            timers += boss.actionTimer
            if (state.canAttack(boss)) {
                quietRun = 0
            } else {
                quiet++
                quietRun++
                longestQuiet = max(longestQuiet, quietRun)
            }
            steps++
        }
        return Fight(volleys, zoneSteps, timers, latestShift, latestStun, quiet / steps.toFloat(), longestQuiet * step)
    }
}
