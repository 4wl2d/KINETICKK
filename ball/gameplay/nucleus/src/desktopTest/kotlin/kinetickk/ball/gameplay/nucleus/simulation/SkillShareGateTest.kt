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
 * skills deal a meaningful but not dominant share of its damage. Seeds are the no-POI probe sequence
 * (`i * 7_919 + 13`, i = 1..31) for the share, and the first three of it per character for the spread, chosen by
 * definition, never by outcome.
 *
 * The share counts the damage the skills cause ([kinetickk.ball.gameplay.nucleus.model.SkillRuntime.skillCausedDamage]):
 * their own hits, what a cast resolves (Catalyze's forced reactions) and the Core's impacts while a skill moves it
 * (Slingshot, Mass Driver, Arc Hook). The NoPoi bot is the Orb, whose drafted lane (Slingshot, Arc Hook, Catalyze)
 * delivers its damage through the Core and reactions: counted by skill hits alone it read 0.3–2.6 % (q10–q90 of 200
 * runs), so that metric could not see the Orb's skills at all.
 *
 * The band is judged on the median run. A run's share depends on which lane skills its three drafts offered: over
 * 300 probe runs the 10th–90th percentile spans 0.056–0.17, wider than the band itself, and no skill number narrows
 * it (Catalyze reaction scale ×1.5, two more targets or a second off its cooldown, Arc Hook links ×1.5–×3 move the
 * 10th percentile by at most 0.004), so "every run in the band" measured the draft, not the skills. The share is read
 * at 720 s as specified: a run that ends earlier is reported, not judged (gate A owns survival), and at least half of
 * the runs must be judged.
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
        val judged = runs.filter { it.judged }.map { it.share }.sorted()
        assertTrue(judged.size * 2 >= runs.size, "the share is read at 720 s: only ${judged.size} of ${runs.size} runs got there")
        val median = judged[judged.size / 2]
        println("SKILL-SHARE median=${"%.3f".format(median)} judged=${judged.size} q10=${"%.3f".format(judged[judged.size / 10])} " +
            "q90=${"%.3f".format(judged[judged.size * 9 / 10])}")
        assertTrue(median in MIN_SHARE..MAX_SHARE, "median skill-caused damage share ${"%.3f".format(median)} outside [$MIN_SHARE, $MAX_SHARE]")
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
        val ShareSeeds: List<Int> = (1..31).map { it * 7_919 + 13 }
        val SpreadSeeds: List<Int> = (1..3).map { it * 7_919 + 13 }
        const val SHARE_SECONDS = 720f
        const val MIN_SHARE = 0.08
        const val MAX_SHARE = 0.20
        const val MAX_SINGLE_SKILL_SHARE = 0.30
    }
}
