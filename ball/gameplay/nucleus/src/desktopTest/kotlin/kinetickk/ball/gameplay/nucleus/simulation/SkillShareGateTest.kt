// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.gameplay.nucleus.model.SkillCell
import kinetickk.ball.gameplay.nucleus.model.SkillRuntime
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Skill share gate (combat-final §10 Skills): the no-POI bot drafts and casts skills as a player would, and its
 * skills deal a meaningful but not dominant share of its damage. Seeds are the second half of the no-POI probe
 * (`i * 7_919 + 13`, i = 6..10) for the share, and the first three of it per character for the spread, chosen by
 * definition, never by outcome.
 *
 * The share counts the damage the skills cause ([kinetickk.ball.gameplay.nucleus.model.SkillRuntime.skillCausedDamage]):
 * their own hits, what a cast resolves (Catalyze's forced reactions) and the Core's impacts while a skill moves it
 * (Slingshot, Mass Driver, Arc Hook). The NoPoi bot is the Orb, whose drafted lane (Slingshot, Arc Hook, Catalyze)
 * delivers its damage through the Core and reactions: counted by skill hits alone the lane read 0.3–2.6 % (q10–q90,
 * 200 runs) while the eleven other characters' damage lanes read 3–16 %, so the old metric could not see the
 * Orb's skills at all. The share is read at 720 s as specified; a run that ends earlier is reported, not judged
 * (gate A owns survival), and at least three of the five must be judged.
 */
class SkillShareGateTest {
    private class Run(val seed: Int, val shape: CoreShape, val share: Double, val bySkill: Map<SkillId, Double>, val seconds: Float) {
        val judged: Boolean get() = seconds >= SHARE_SECONDS
    }

    @Test
    fun skillsDealBetweenEightAndTwentyPercentOfAFreshRunsDamage() {
        val runs = ShareSeeds.map { seed -> play(seed, CoreShape.ORB) }
        runs.forEach { run ->
            println("SKILL-SHARE seed=${run.seed} share=${"%.3f".format(run.share)} sec=${run.seconds.toInt()}" +
                (if (run.judged) " " else " (ended early, not judged) ") +
                run.bySkill.entries.joinToString { (skill, damage) -> "$skill=${damage.toInt()}" })
        }
        val judged = runs.filter { it.judged }
        assertTrue(judged.size >= MIN_JUDGED, "the share is read at 720 s: only ${judged.size} of 5 runs got there")
        val outside = judged.filter { it.share !in MIN_SHARE..MAX_SHARE }
        assertTrue(outside.isEmpty(), "skill-caused damage share outside [$MIN_SHARE, $MAX_SHARE]: " +
            outside.joinToString { "${it.seed}=${"%.3f".format(it.share)}" })
    }

    @Test
    fun noSingleSkillDealsMoreThanThirtyPercentOfAllSkillDamageAcrossTheCharacters() {
        val runs = CoreShape.entries.flatMap { shape -> SpreadSeeds.map { seed -> play(seed, shape) } }
        val totals = SkillId.entries.associateWith { skill -> runs.sumOf { it.bySkill[skill] ?: 0.0 } }
        val all = totals.values.sum()
        println("SKILL-SPREAD total=${all.toInt()} " + totals.entries.sortedByDescending { it.value }
            .joinToString { (skill, damage) -> "$skill=${"%.3f".format(if (all > 0.0) damage / all else 0.0)}" })
        assertTrue(all > 0.0, "the bots dealt skill damage")
        val top = totals.maxBy { it.value }
        assertTrue(top.value / all <= MAX_SINGLE_SKILL_SHARE,
            "${top.key} dealt ${"%.3f".format(top.value / all)} of all skill damage")
    }

    private fun play(seed: Int, shape: CoreShape): Run {
        val state = MutableGameState(canonicalGameplayContent, seed = seed)
        state.unlockWeaponsForTesting(ReferenceArmory)
        state.applyPreferences(state.settings.copy(simulationSpeed = 1f))
        state.coreShape = shape
        state.startRun()
        val bot = NoPoiBot()
        while (state.elapsed < SHARE_SECONDS && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
            if (state.phase == GamePhase.CHOICE) bot.choose(state) else {
                bot.control(state)
                state.update(0.02f)
            }
            state.takeSoundCues()
            state.takeVisualFxCues()
        }
        val bySkill = (0 until SkillRuntime.SLOTS).mapNotNull { slot ->
            state.skillLoadout.skill(slot)?.let { it to state.skillRuntime[slot, SkillCell.DAMAGE].toDouble() }
        }.toMap()
        val share = if (state.damageDealt > 0.0) state.skillRuntime.skillCausedDamage / state.damageDealt else 0.0
        return Run(seed, shape, share, bySkill, state.elapsed)
    }

    private companion object {
        val ShareSeeds: List<Int> = (6..10).map { it * 7_919 + 13 }
        val SpreadSeeds: List<Int> = (1..3).map { it * 7_919 + 13 }
        const val SHARE_SECONDS = 720f
        const val MIN_JUDGED = 3
        const val MIN_SHARE = 0.08
        const val MAX_SHARE = 0.20
        const val MAX_SINGLE_SKILL_SHARE = 0.30
    }
}
