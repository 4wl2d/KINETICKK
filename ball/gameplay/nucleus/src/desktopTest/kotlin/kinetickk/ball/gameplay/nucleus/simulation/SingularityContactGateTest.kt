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
 * them, and touches obstacles at most 15 % more often (a contact is the frame the Core first touches a rock or
 * bumper, sampled every 0.02 s input frame). Each arm is the no-POI bot on ten fixed seeds (the
 * no-POI probe `i * 7_919 + 13`, i = 1..10) for 300 s, with no skill drafts: a skill arm owns one skill from the
 * start and casts it by the bot's policy, a reaction arm sets the reaction off on the nearest enemy close to the
 * Core every three seconds, and an item arm owns one item that launches the Core (items.md §6.2) from the start.
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
        arms.forEach(::report)
        val worse = arms.filter { it.contacts > baseline.contacts || it.bouncesPerSecond > baseline.bouncesPerSecond * MAX_BOUNCE_RATIO }
        assertTrue(worse.isEmpty(), "against the baseline (contacts ${baseline.contacts}, obstacle-contacts/s " +
            "${"%.4f".format(baseline.bouncesPerSecond)}): " +
            worse.joinToString { "${it.label} contacts=${it.contacts} obstacle-contacts/s=${"%.4f".format(it.bouncesPerSecond)}" })
    }

    private fun report(arm: Arm) {
        println("SINGULARITY-CONTACT ${arm.label} contacts=${arm.contacts} obstacle-contacts/s=${"%.4f".format(arm.bouncesPerSecond)} ${arm.outcomes}")
    }

    private fun arm(label: String, skill: SkillId?, reaction: ReactionId?, item: String? = null): Arm {
        var contacts = 0
        var bounces = 0L
        var seconds = 0.0
        val outcomes = StringBuilder()
        for (seed in Seeds) {
            val state = MutableGameState(NoDrafts, seed = seed)
            state.unlockWeaponsForTesting(ReferenceArmory)
            state.applyPreferences(state.settings.copy(simulationSpeed = 1f))
            state.startRun()
            skill?.let(state::acquireSkill)
            item?.let { name -> state.acquireItem(state.content.items.single { it.name == name }.id) }
            val bot = NoPoiBot()
            var nextReactionAt = REACTION_EVERY
            var touching = false
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
            if (state.phase == GamePhase.GAME_OVER && state.message == SINGULARITY_CONTACT) contacts++
            seconds += state.elapsed
            outcomes.append(seed).append(':').append(state.phase).append('@').append(state.elapsed.toInt()).append(' ')
        }
        return Arm(label, contacts, bounces / seconds, outcomes.toString().trim())
    }

    private companion object {
        val Seeds: List<Int> = (1..10).map { it * 7_919 + 13 }
        val NoDrafts = withoutReachableSkillSlots()
        /** Skills that move the Core: the MOTION family, Meteor's ride and Sonic Boom's recoil. */
        val MovingSkills = listOf(SkillId.METEOR, SkillId.ARC_HOOK, SkillId.MASS_DRIVER, SkillId.VOID_STEP,
            SkillId.SLINGSHOT, SkillId.SONIC_BOOM)
        /** Items whose clauses launch the Core (CoreImpulse). */
        val LaunchingItems = listOf("Recoil Spring", "Kickback Plate", "Spin Turbine", "Event Saddle", "Gravity Assist", "Crest Rider")
        /** Reaction verbs that move the Core: Frictionless, Blast Jump and Void Anchor. */
        val MovingReactions = listOf(ReactionId.SUPERCONDUCT, ReactionId.THUNDERCLAP, ReactionId.ANNIHILATE)
        const val ARM_SECONDS = 300f
        const val REACTION_EVERY = 3f
        const val MAX_BOUNCE_RATIO = 1.15
        const val SINGULARITY_CONTACT = "SINGULARITY CONTACT"
        const val CONTACT_SLACK = 2f
    }
}
