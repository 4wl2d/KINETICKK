// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.gameplay.nucleus.model.SkillRuntime
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Skill share gate (combat-final §10 Skills): the no-POI bot takes, ranks and casts active items as a player would, and
 * their effects deal a meaningful but not dominant share of its damage.
 *
 * Population (restated for active items): an active item reaches a run only through ordinary item offers, so the
 * gate measures runs that own one. The share plays the Orb on every one of the 300 [PopulationSeeds] and judges the runs
 * that have bound an active item by [OWNED_BY_SECONDS]; the spread walks [PopulationSeeds] for every character until
 * [SPREAD_RUNS] runs of each have. Seeds are chosen by that definition, never by outcome; a run that binds no active item
 * by then is left out. The spread's walk is bounded: a population that cannot fill within [MAX_PROBES_PER_RUN] probes
 * per run fails as such.
 *
 * The share counts the damage the active effects cause ([kinetickk.ball.gameplay.nucleus.model.SkillRuntime.skillCausedDamage]):
 * their own hits, what a cast resolves (Catalyze's forced reactions) and the Core's impacts while an effect moves it
 * (Slingshot, Mass Driver, Arc Hook). The NoPoi bot is the Orb, whose lane (Slingshot, Arc Hook, Catalyze) delivers its
 * damage through the Core and reactions: counted by skill hits alone it read 0.3–2.6 % (q10–q90 of 200 runs, measured
 * on the 0.5.0 base before active items, when skills came as drafts), so that metric could not see the Orb's skills.
 *
 * The band is judged on the median run. A run's share depends on which effects it got: on the 0.5.0 base's drafts, over
 * 300 probe runs the 10th–90th percentile spanned 0.056–0.17, wider than the band itself, and no skill number narrowed
 * it (Catalyze's reaction scale ×1.5 or two more targets, which a cast caps at the 1.3 and the 10 enemies that rank III
 * already had; a second off Catalyze's cooldown; Arc Hook links ×1.5–×3: none moved the 10th percentile by more than
 * 0.004), so "every run in the band" measured the offers, not the effects. The share is read at 720 s as specified: a
 * run that ends earlier is reported, not judged (gate A owns survival), and at least half of the runs must be judged.
 * The spread pools each effect's damage over the whole run
 * ([kinetickk.ball.gameplay.nucleus.model.SkillRuntime.effectDamage], kept when its item is replaced) across every
 * character's runs, over all active damage.
 *
 * The share is a population gate (policy.md, "Balance gate populations"): judged on 31 owning runs walked from i = 1,
 * it passed with probability 0.99 on the 05885fa9 base but 0.70 with the shipped active items (population median
 * 0.089), so it re-rolled with the reward stream. On the base (its lane skills free from level 2) all 300
 * [PopulationSeeds] runs owned one, 214 were judged and their median share was 0.113 (bootstrap SE 0.0038). By the
 * population threshold rule the band is min(0.08, base − 2 SE) to max(0.20, base + 2 SE), that is [MIN_SHARE] to
 * [MAX_SHARE], the design band. The walk also failed when fewer than 31 of its 124 probes owned an active item; that
 * stays a population statement next to the band: at least [MIN_OWNED_SHARE] of the runs bind one by [OWNED_BY_SECONDS],
 * a design bound that all 300 base runs met.
 *
 * The spread is a population gate: with three runs per character the 05885fa9 base kept its top
 * effect within 0.30 with probability 0.76 (Sonic Boom 0.253 over 100 runs per character, 300 for the Orb), so it
 * re-rolled with the reward stream. Each character now walks [PopulationSeeds] until [SPREAD_RUNS] runs bind an active
 * item by [OWNED_BY_SECONDS]: 300 runs, on which the base's top effect read 0.280 (Sonic Boom, bootstrap SE 0.014, with
 * the old per-slot damage). The base plus two standard errors is 0.31; the bound stays the design's 0.30.
 */
class SkillShareGateTest {
    private class Run(
        val seed: Int,
        val shape: CoreShape,
        val share: Double,
        val bySkill: Map<SkillId, Double>,
        val seconds: Float,
        /** When the run first held an active item, or null when it held none by [OWNED_BY_SECONDS]. */
        val firstActiveAt: Float?,
    ) {
        val owned: Boolean get() = firstActiveAt != null
        val judged: Boolean get() = seconds >= SHARE_SECONDS
    }

    @Test
    fun skillsDealBetweenEightAndTwentyPercentOfAFreshRunsDamage() {
        val probes = populationOf(PopulationSeeds) { seed -> play(seed, CoreShape.ORB) }
        val runs = probes.filter { it.owned }
        println("SKILL-SHARE ${runs.size} of ${probes.size} runs bound an active item by $OWNED_BY_SECONDS s; left out: " +
            probes.filterNot { it.owned }.joinToString { "${it.seed}(sec=${it.seconds.toInt()})" })
        assertTrue(runs.size >= probes.size * MIN_OWNED_SHARE,
            "only ${runs.size} of ${probes.size} runs bound an active item by $OWNED_BY_SECONDS s, below $MIN_OWNED_SHARE of them")
        runs.forEach { run ->
            println("SKILL-SHARE seed=${run.seed} share=${"%.3f".format(run.share)} sec=${run.seconds.toInt()} " +
                "firstActive=${run.firstActiveAt?.toInt()}" + (if (run.judged) " " else " (ended early, not judged) ") +
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
        val runs = populationOf(CoreShape.entries) { shape -> population(shape, SPREAD_RUNS, PopulationSeeds) }.flatten()
        val totals = SkillId.entries.associateWith { skill -> runs.sumOf { it.bySkill[skill] ?: 0.0 } }
        val all = totals.values.sum()
        println("SKILL-SPREAD runs=${runs.size} total=${all.toInt()} " + totals.entries.sortedByDescending { it.value }
            .joinToString { (skill, damage) -> "$skill=${"%.3f".format(if (all > 0.0) damage / all else 0.0)}" })
        assertTrue(all > 0.0, "the bots dealt skill damage")
        val top = totals.maxBy { it.value }
        assertTrue(top.value / all <= MAX_SINGLE_SKILL_SHARE,
            "${top.key} dealt ${"%.3f".format(top.value / all)} of all skill damage")
    }

    /** The first [size] runs of [shape] along [probes] that bind an active item by [OWNED_BY_SECONDS]. */
    private fun population(shape: CoreShape, size: Int, probes: List<Int>): List<Run> {
        val runs = ArrayList<Run>(size)
        var probe = 0
        while (runs.size < size) {
            probe++
            assertTrue(probe <= size * MAX_PROBES_PER_RUN && probe <= probes.size,
                "only ${runs.size} of ${probe - 1} $shape probes bound an active item by $OWNED_BY_SECONDS s; $size needed")
            val run = play(probes[probe - 1], shape)
            if (run.owned) runs += run
            else println("SKILL-SHARE $shape seed=${run.seed} bound no active item by $OWNED_BY_SECONDS s (sec=${run.seconds.toInt()}), left out")
        }
        return runs
    }

    private fun play(seed: Int, shape: CoreShape): Run {
        val state = MutableGameState(canonicalGameplayContent, seed = seed)
        state.unlockWeaponsForTesting(ReferenceArmory)
        state.applyPreferences(state.settings.copy(simulationSpeed = 1f))
        state.coreShape = shape
        state.startRun()
        val bot = NoPoiBot()
        var firstActiveAt: Float? = null
        while (state.elapsed < SHARE_SECONDS && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
            if (state.phase == GamePhase.CHOICE) bot.choose(state) else {
                bot.control(state)
                state.update(0.02f)
            }
            state.takeSoundCues()
            state.takeVisualFxCues()
            if (firstActiveAt == null && (0 until SkillRuntime.SLOTS).any { !state.skillLoadout.isEmpty(it) }) {
                firstActiveAt = state.elapsed
            }
            // A run without an active item by then is not in the population: nothing later is read from it.
            if (firstActiveAt == null && state.elapsed >= OWNED_BY_SECONDS) break
        }
        // Per active effect over the whole run, so a replaced active keeps the damage it dealt.
        val bySkill = SkillId.entries.associateWith { state.skillRuntime.effectDamage(it).toDouble() }.filterValues { it > 0.0 }
        val share = if (state.damageDealt > 0.0) state.skillRuntime.skillCausedDamage / state.damageDealt else 0.0
        return Run(seed, shape, share, bySkill, state.elapsed, firstActiveAt)
    }

    private companion object {
        const val SPREAD_RUNS = 25
        /** A population run has bound an active item by this run time. */
        const val OWNED_BY_SECONDS = 360f
        /** The spread walk's bound: at most this many probes per run before the population counts as unfillable. */
        const val MAX_PROBES_PER_RUN = 4
        const val SHARE_SECONDS = 720f
        const val MIN_SHARE = 0.08
        /** The least share of the population that binds an active item by [OWNED_BY_SECONDS] (the walk's 31 of 124). */
        const val MIN_OWNED_SHARE = 0.25
        const val MAX_SHARE = 0.20
        const val MAX_SINGLE_SKILL_SHARE = 0.30
    }
}
