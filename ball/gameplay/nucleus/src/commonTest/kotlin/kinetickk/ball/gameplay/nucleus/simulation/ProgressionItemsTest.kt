// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.gameplay.nucleus.model.SkillCell
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Selected 0.5.0 items as shipped (items.md §6.2): their rules land as the catalog states them. */
class ProgressionItemsTest {
    @Test
    fun summitCrownClearsTwelveBoltsAndShieldsOnEachApexAndChargesItOnLevelUps() {
        val state = buildTestState(rebirth = 10)
        state.own("Shield Cell", stacks = 3)
        state.own("Summit Crown")
        state.shield = 0f
        val bolts = (1..20).map { state.hostileBolt(30f + it * 15f, 0f) }
        state.fireClauses(EffectTrigger.APEX)
        assertEquals(12, bolts.count { it.spent }, "at most twelve bolts per activation")
        assertTrue(bolts.filter { it.spent }.all { bolt -> bolts.filter { !it.spent }.all { it.x > bolt.x } }, "the nearest first")
        assertEquals(min(60f, state.shieldCapacity()), state.shield, 1e-3f)

        // Before level 42 a level-up's 5 s Apex charge refunds a quarter of it to the skills.
        state.acquireSkill(SkillId.SOLAR_FLARE)
        state.elapsed = 20f
        state.skillRuntime[0, SkillCell.CAST_AT] = 15f
        state.skillRuntime[0, SkillCell.LAST_COOLDOWN] = 10f
        state.skillRuntime[0, SkillCell.READY_AT] = 25f
        state.fireClauses(EffectTrigger.LEVEL_UP)
        assertEquals(25f - 5f * CHARGE_APEX_SKILL_SHARE, state.skillRuntime[0, SkillCell.READY_AT], 1e-4f)
    }

    @Test
    fun staticUmbrellaErasesAtMostSixBoltsEveryFourSeconds() {
        val state = buildTestState()
        state.own("Static Umbrella")
        val bolts = (1..10).map { state.hostileBolt(20f * it, 0f) }
        state.fireClauses(EffectTrigger.HURT)
        assertEquals(6, bolts.count { it.spent })
        state.elapsed += 1f
        state.fireClauses(EffectTrigger.HURT)
        assertEquals(6, bolts.count { it.spent }, "the 4 s cooldown holds")
        state.elapsed += 3.1f
        state.fireClauses(EffectTrigger.HURT)
        assertEquals(10, bolts.count { it.spent })
    }

    @Test
    fun anvilDoctrineTurnsThrowsOnTheArchitectIntoDamageWithinItsHitCap() {
        val state = buildTestState()
        state.own("Anvil Doctrine")
        state.bossSpawned = true
        val architect = state.addEnemyForTesting(300f, 0f, hp = 1_000f, radius = 74f, type = EnemyType.ARCHITECT)
        val cap = state.architectHitCap(architect)
        // A 600 u/s throw converts to 90 × run scale, far above 1.5 % of this small Architect's integrity.
        assertTrue(600f * KNOCKBACK_DAMAGE_SCALE * state.runScale() > cap)
        val before = architect.hp
        state.hurl(state.coreX, state.coreY, architect, 600f)
        assertTrue(before - architect.hp > 0f, "the immovable Architect takes the throw as damage")
        assertTrue(before - architect.hp <= cap + 1e-3f, "${before - architect.hp} within the hit cap $cap")
        assertEquals(0f, architect.vx, "nothing moves it")
    }
}
