// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Singularity contact gate (combat-final §9 and §10 Skills): skills and reaction verbs that move the Core are all
 * player-timed or bounded, so a bot using them touches the singularity no more often than the same bot without
 * them, and touches obstacles no more often than the 0.6.0 base measured beyond noise (a contact is the frame the
 * Core first touches a solid obstacle, sampled every 0.02 s input frame). Each arm is the no-POI bot for 300 s, and no
 * active item reaches it through its offers: a skill arm owns one active item from the start and casts its effect
 * by the bot's policy, a reaction arm sets the reaction off on the nearest enemy close to the Core every three seconds,
 * and an item arm owns one item that launches the Core (items.md §6.2) from the start. The baseline, reaction and item
 * arms play the catalog without its active items, a run as before they existed; a skill arm keeps them in the catalog
 * (it acquires the active item it measures) and is never offered another.
 *
 * A population gate (0.5.0 active items; policy.md, "Balance gate populations"): on the thirty fixed seeds
 * (`i * 7_919 + 13`, i = 1..30) the 05885fa9 tree met "at most 15 % more obstacle contacts per second and no more
 * singularity contacts" in every arm with probability 0.205 (one arm alone with 0.44 for Slingshot to 0.94 for Arc
 * Hook). Every arm plays the 300 [PopulationSeeds] runs. An arm's obstacle-contact rate is at most [MaxBounceRatio] ×
 * the baseline's (by the population threshold rule the higher of the design's 1.15 and its 0.6.0 base ratio plus two
 * bootstrap standard errors, rounded up; the 0.6.0 baseline reads 0.00849 per second, 740 contacts in 87,120 s), and the
 * share of its runs ending in a singularity contact exceeds the baseline's by at most [MaxExtraContactShare].
 */
class SingularityContactGateTest {
    private class Arm(val label: String, val contacts: Int, val bouncesPerSecond: Double, val outcomes: String)


    @Test
    fun coreMovingSkillsAndReactionsAddNoSingularityContacts() {
        val baseline = arm("baseline", skill = null, reaction = null)
        report(baseline)
        val arms = MovingSkills.map { skill -> arm(skill.name, skill = skill, reaction = null) } +
            MovingReactions.map { reaction -> arm(reaction.name, skill = null, reaction = reaction) } +
            LaunchingItems.map { item -> arm(item, skill = null, reaction = null, item = item) }
        arms.forEach { arm -> report(arm, baseline) }
        val worse = arms.filter { arm ->
            (arm.contacts - baseline.contacts).toDouble() / PopulationSeeds.size > MaxExtraContactShare.getOrDefault(arm.label, 0.0) ||
                arm.bouncesPerSecond > baseline.bouncesPerSecond * MaxBounceRatio.getValue(arm.label)
        }
        assertTrue(worse.isEmpty(), "against the baseline (contacts ${baseline.contacts}, obstacle-contacts/s " +
            "${"%.4f".format(baseline.bouncesPerSecond)}): " + worse.joinToString {
                "${it.label} contacts=${it.contacts} obstacle-contacts/s=${"%.4f".format(it.bouncesPerSecond)} " +
                    "(ratio ${"%.3f".format(it.bouncesPerSecond / baseline.bouncesPerSecond)}, at most ${MaxBounceRatio.getValue(it.label)})"
            })
    }

    private fun report(arm: Arm, baseline: Arm? = null) {
        println("SINGULARITY-CONTACT ${arm.label} contacts=${arm.contacts} obstacle-contacts/s=${"%.4f".format(arm.bouncesPerSecond)}" +
            baseline?.let { " ratio=${"%.3f".format(arm.bouncesPerSecond / it.bouncesPerSecond)}" }.orEmpty() + " ${arm.outcomes}")
    }

    /** One run of an arm: its obstacle contacts, length, and whether it ended in a singularity contact. */
    private class Run(val bounces: Long, val seconds: Float, val contact: Boolean, val outcome: String)

    private fun arm(label: String, skill: SkillId?, reaction: ReactionId?, item: String? = null): Arm {
        val runs = populationOf(PopulationSeeds) { seed -> run(seed, skill, reaction, item) }
        return Arm(label, runs.count { it.contact }, runs.sumOf { it.bounces } / runs.sumOf { it.seconds.toDouble() },
            runs.joinToString(" ") { it.outcome })
    }

    private fun run(seed: Int, skill: SkillId?, reaction: ReactionId?, item: String?): Run {
        val state = MutableGameState(if (skill != null) NoOtherActives else NoActives, seed = seed)
        state.unlockWeaponsForTesting(ReferenceArmory)
        state.applyPreferences(state.settings.copy(simulationSpeed = 1f))
        state.startRun()
        skill?.let { state.acquireActive(it) }
        item?.let { name -> state.acquireItem(state.content.items.single { it.name == name }.id) }
        val bot = NoPoiBot()
        var nextReactionAt = REACTION_EVERY
        var touching = false
        var bounces = 0L
        while (state.elapsed < ARM_SECONDS && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
            if (state.phase == GamePhase.CHOICE) bot.choose(state) else {
                bot.control(state)
                if (reaction != null && state.elapsed >= nextReactionAt) {
                    nextReactionAt += REACTION_EVERY
                    state.enemies.filter { it.alive && !it.isBossClass && state.nearCore(it.x, it.y) }
                        .minByOrNull { (it.x - state.coreX) * (it.x - state.coreX) + (it.y - state.coreY) * (it.y - state.coreY) }
                        ?.let { state.setOff(reaction, it) }
                }
                state.update(0.02f)
            }
            state.takeSoundCues()
            state.takeVisualFxCues()
            val touches = state.obstacleField.obstacles.any { obstacle ->
                if (!obstacle.solidAt(state.elapsed, state.content.obstacles.kinds)) return@any false
                val reach = obstacle.radius + state.coreRadius + CONTACT_SLACK
                (obstacle.x - state.coreX) * (obstacle.x - state.coreX) + (obstacle.y - state.coreY) * (obstacle.y - state.coreY) <= reach * reach
            }
            if (touches && !touching) bounces++
            touching = touches
        }
        return Run(bounces, state.elapsed, state.phase == GamePhase.GAME_OVER && state.message == SINGULARITY_CONTACT,
            "$seed:${state.phase}@${state.elapsed.toInt()}")
    }

    private companion object {
        /** The catalog without its active items: the arms that measure no active. */
        val NoActives = withoutActiveItemCatalog()
        /** Offers that never show an active item: a skill arm owns exactly the active it measures. */
        val NoOtherActives = withoutActiveItems()
        /** Skills that move the Core: the MOTION family, Meteor's ride and Sonic Boom's recoil. */
        val MovingSkills = listOf(SkillId.METEOR, SkillId.ARC_HOOK, SkillId.MASS_DRIVER, SkillId.VOID_STEP,
            SkillId.SLINGSHOT, SkillId.SONIC_BOOM)
        /** Items whose clauses launch the Core (CoreImpulse). */
        val LaunchingItems = listOf("Recoil Spring", "Kickback Plate", "Spin Turbine", "Event Saddle", "Gravity Assist", "Crest Rider")
        /** Reaction verbs that move the Core: Frictionless, Blast Jump and Void Anchor. */
        val MovingReactions = listOf(ReactionId.SUPERCONDUCT, ReactionId.THUNDERCLAP, ReactionId.ANNIHILATE)
        const val ARM_SECONDS = 300f
        const val REACTION_EVERY = 3f
        /**
         * Each arm's largest obstacle-contact rate over the baseline's on [PopulationSeeds]: the design's
         * [DESIGN_BOUNCE_RATIO], or its 0.6.0 base ratio plus two bootstrap standard errors (20,000 resamples of the
         * seeds, paired with the baseline, rounded up) where that is higher. Base ratio (SE, base + 2 SE) over 300 runs:
         * Meteor 0.946 (0.064, 1.08), Arc Hook 0.857 (0.056, 0.98), Mass Driver 0.948 (0.071, 1.09), Void Step 0.987
         * (0.066, 1.12), Slingshot 1.328 (0.089, 1.51), Sonic Boom 0.938 (0.067, 1.08), Superconduct 0.856 (0.059, 0.98),
         * Thunderclap 0.921 (0.064, 1.05), Annihilate 0.836 (0.060, 0.96), Recoil Spring 1.010 (0.066, 1.15), Kickback
         * Plate 0.962 (0.071, 1.11), Spin Turbine 1.189 (0.081, 1.36), Event Saddle 1.001 (0.066, 1.14), Gravity Assist
         * 1.029 (0.071, 1.18), Crest Rider 1.026 (0.068, 1.17). Slingshot read 1.129 on the 05885fa9 tree and 1.27 to
         * 1.37 under the 0.6.0 flight law as the reference bot changed; 1.328 once the bot held the swings that would
         * throw the Core into a skipped optional site.
         */
        val MaxBounceRatio: Map<String, Double> =
            (MovingSkills.map { it.name } + MovingReactions.map { it.name } + LaunchingItems).associateWith { DESIGN_BOUNCE_RATIO } +
                mapOf(SkillId.SLINGSHOT.name to 1.51, "Spin Turbine" to 1.36, "Gravity Assist" to 1.18, "Crest Rider" to 1.17)
        /** The design bound: at most 15 % more obstacle contacts per second than the baseline. */
        const val DESIGN_BOUNCE_RATIO = 1.15
        /**
         * The largest share of an arm's runs ending in a singularity contact beyond the baseline's: the higher of the
         * design's 0 and the base difference plus two standard errors, rounded up. On the 0.6.0 base no arm and no
         * baseline run touches the singularity (0 of 300 each, binomial SE 0), so every arm keeps "no more than the
         * baseline"; 0.5.0's Slingshot allowance of 0.02 (two 05885fa9 contacts in 300 runs) is gone.
         */
        val MaxExtraContactShare: Map<String, Double> = emptyMap()
        const val SINGULARITY_CONTACT = "SINGULARITY CONTACT"
        const val CONTACT_SLACK = 2f
    }
}
