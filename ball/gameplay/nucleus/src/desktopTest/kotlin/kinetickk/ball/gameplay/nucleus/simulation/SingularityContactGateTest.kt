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
 * them, and touches obstacles no more often than the 05885fa9 base measured beyond noise (a contact is the frame the
 * Core first touches a rock or bumper, sampled every 0.02 s input frame). Each arm is the no-POI bot for 300 s, and no
 * active item reaches it through its offers: a skill arm owns one active item from the start and casts its effect
 * by the bot's policy, a reaction arm sets the reaction off on the nearest enemy close to the Core every three seconds,
 * and an item arm owns one item that launches the Core (items.md §6.2) from the start. The baseline, reaction and item
 * arms play the catalog without its active items, a run as before they existed; a skill arm keeps them in the catalog
 * (it acquires the active item it measures) and is never offered another.
 *
 * A population gate (lead Addendum C5, 0.5.0 active items): on the thirty fixed seeds (`i * 7_919 + 13`, i = 1..30) the
 * base met "at most 15 % more obstacle contacts per second and no more singularity contacts" in every arm with
 * probability 0.20 (one arm alone with 0.44 for Slingshot to 0.94 for Arc Hook). Every arm now plays the 300
 * [PopulationSeeds] runs. An arm's obstacle-contact rate is at most [MaxBounceRatio] × the
 * baseline's (by Addendum D1 the higher of the design's 1.15 and its base ratio plus two bootstrap standard errors,
 * rounded up; base baseline 0.01061 per second, 899 contacts in 84,733 s), and the share of its runs ending in a
 * singularity contact exceeds the baseline's by at most [MaxExtraContactShare] (0 for every arm without a base contact;
 * Slingshot's two base contacts in 300 runs give 0.02).
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
         * Each arm's largest obstacle-contact rate over the baseline's on [PopulationSeeds] (Addendum D1): the design's
         * [DESIGN_BOUNCE_RATIO], or its 05885fa9 base ratio plus two bootstrap standard errors (20,000 resamples of the
         * seeds, rounded up) where that is higher. Base ratio (SE, base + 2 SE) over 300 runs: Meteor 0.965 (0.068, 1.11),
         * Arc Hook 0.836 (0.056, 0.95), Mass Driver 0.981 (0.074, 1.13), Void Step 0.897 (0.058, 1.02), Slingshot 1.129
         * (0.079, 1.29), Sonic Boom 0.867 (0.056, 0.98), Superconduct 0.881 (0.062, 1.01), Thunderclap 0.995 (0.070,
         * 1.14), Annihilate 0.882 (0.062, 1.01), Recoil Spring 1.040 (0.069, 1.18), Kickback Plate 0.938 (0.070, 1.08),
         * Spin Turbine 1.083 (0.076, 1.24), Event Saddle 0.934 (0.067, 1.07), Gravity Assist 0.960 (0.069, 1.10), Crest
         * Rider 1.041 (0.068, 1.18).
         */
        val MaxBounceRatio: Map<String, Double> =
            (MovingSkills.map { it.name } + MovingReactions.map { it.name } + LaunchingItems).associateWith { DESIGN_BOUNCE_RATIO } +
                mapOf(SkillId.SLINGSHOT.name to 1.29, "Spin Turbine" to 1.24, "Recoil Spring" to 1.18, "Crest Rider" to 1.18)
        /** The design bound: at most 15 % more obstacle contacts per second than the baseline. */
        const val DESIGN_BOUNCE_RATIO = 1.15
        /**
         * The largest share of an arm's runs ending in a singularity contact beyond the baseline's: the base difference
         * plus two standard errors, rounded up. Only Slingshot touched it at the base (2 of 300, SE 0.005); every other
         * arm and the baseline never did, so they keep "no more than the baseline".
         */
        val MaxExtraContactShare: Map<String, Double> = mapOf(SkillId.SLINGSHOT.name to 0.02)
        const val SINGULARITY_CONTACT = "SINGULARITY CONTACT"
        const val CONTACT_SLACK = 2f
    }
}
