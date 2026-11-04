// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation.dimensions

import kinetickk.ball.content.api.DimensionObjective
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.sites.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.abs
import kotlin.math.acos
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The dimensions' natural path, a probe and not a gate: it returns at once unless `KK_DIM_NATURAL_PROBE=first..last`
 * names a calibration window (policy.md: dimensions use i = 2,401..2,500). Each seed `i * 7_919 + 13` plays a fresh
 * tier-0 run to its end with the reference bot; the pilot flies through every rift the run offers on its own (window
 * rifts and the glitch rift, no test rift), plays each visit's rolled objective and leaves through the exit rift.
 * `KK_DIM_NATURAL_STYLES` lists pilots by their reaction time (EXPERT = 0 ms, LAG160, LAG200, LAG240; default
 * EXPERT,LAG200). Every input frame checks the visit's invariants: the layer matches the visit, no visit outlives its
 * end or meets the Architect, a completed or broken objective always has its exit rift, a pause or an open choice
 * inside a visit moves no clock, the return pays the reward the visit earned (the right hand of cards, the Archive's
 * Matter once), and the glitch rift opens beside the Core's path. It prints one line per visit and a summary per style
 * and fails on any invariant break.
 */
class DimensionNaturalPathProbeTest {
    @Test fun probeTheNaturalPath() {
        val window = System.getenv("KK_DIM_NATURAL_PROBE")?.takeIf { it.isNotBlank() } ?: return
        val seeds = windowSeeds(probeWindow(window))
        val styles = (System.getenv("KK_DIM_NATURAL_STYLES")?.takeIf { it.isNotBlank() } ?: "EXPERT,LAG200")
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val breaks = mutableListOf<String>()
        for (style in styles) {
            val frames = reactionFrames(style)
            val runs = populationOf(seeds) { seed -> flyNatural(seed, frames) }
            runs.flatMap { it.visits }.forEach { println("NATURAL $style $it") }
            runs.filter { it.breaks.isNotEmpty() }.forEach { println("NATURAL $style BREAK seed=${it.seed} ${it.breaks}") }
            println("NATURAL SUMMARY $style ${summary(runs)}")
            runs.forEach { run -> run.breaks.forEach { breaks += "$style seed=${run.seed}: $it" } }
        }
        assertTrue(breaks.isEmpty(), "natural-path invariant breaks:\n${breaks.joinToString("\n")}")
    }

    private fun reactionFrames(style: String): Int = when (style) {
        "EXPERT" -> 0
        else -> style.removePrefix("LAG").toInt() / INPUT_MILLIS
    }

    /** One visit of a natural run: where and when it went, what it played and how it ended and paid. */
    private data class NaturalVisit(
        val seed: Int,
        val layer: WorldLayer,
        val objective: DimensionObjective,
        val enteredAt: Float,
        var completed: Boolean = false,
        var failed: Boolean = false,
        var returnedAt: Float = Float.NaN,
        var diedInside: Boolean = false,
        var hand: String = "none",
        var paused: Boolean = false,
    ) {
        override fun toString(): String =
            "seed=$seed layer=$layer objective=$objective enteredAt=${"%.1f".format(enteredAt)} completed=$completed " +
                "failed=$failed returnedAt=${"%.1f".format(returnedAt)} diedInside=$diedInside hand=$hand paused=$paused"
    }

    private class NaturalRun(val seed: Int) {
        val visits = mutableListOf<NaturalVisit>()
        val breaks = mutableListOf<String>()
        var windowRifts = 0
        var glitchRifts = 0
        var phase = GamePhase.RUNNING
        var seconds = 0f
    }

    /** A fresh tier-0 run on [seed] to its end, the pilot taking every rift the run offers with [frames] of reaction lag. */
    private fun flyNatural(seed: Int, frames: Int): NaturalRun {
        val content = canonicalGameplayContent
        val profile = DimensionProfile.FRESH_TIER_0
        val state = startGateRun(seed, profile.snapshot(content), profile.relics, content)
        val pilot = DimensionPilot(WorldLayer.ZENITH, profile.bot(), reactionFrames = frames)
        val run = NaturalRun(seed)
        val offered = mutableSetOf<Long>()
        var glitchId = -1
        var visit: NaturalVisit? = null
        var awaiting: NaturalVisit? = null
        var awaitingSince = 0f
        while (state.elapsed < 900f && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
            for (site in state.sites) {
                val target = site.kind.riftTarget() ?: continue
                if (offered.add(site.id)) run.windowRifts++
                if (state.dimensionRun.visit == null && state.dimensionRun.visitedMask and target.bit == 0) {
                    pilot.target = target
                    pilot.riftDue = true
                }
            }
            val glitch = state.dimensionRun.glitchPortal
            if (glitch != null) {
                if (glitch.id != glitchId) {
                    glitchId = glitch.id
                    run.glitchRifts++
                    checkGlitchBeside(state, glitch.x, glitch.y, run)
                }
                pilot.target = WorldLayer.ARCHIVE
                pilot.riftDue = true
            }
            val current = visit
            if (state.phase == GamePhase.CHOICE) {
                checkFrozen(state, run, "an open choice")
                val pending = awaiting
                if (pending != null && state.dimensionRewardLayer == pending.layer) {
                    pending.hand = "${state.choiceType}x${state.choices.size}"
                    checkHand(pending, state.choiceType, state.choices.size, run)
                    awaiting = null
                }
                pilot.choose(state)
            } else {
                if (current != null && !current.paused && state.elapsed >= current.enteredAt + PAUSE_AFTER_ENTRY) {
                    state.togglePause()
                    repeat(PAUSE_FRAMES) { checkFrozen(state, run, "a pause") }
                    state.togglePause()
                    current.paused = true
                }
                val layerBefore = state.worldLayer
                val matterBefore = state.runMatter + state.matterFraction
                pilot.control(state)
                state.update(INPUT_SECONDS)
                val entered = state.dimensionRun.visit
                if (current == null && entered != null) {
                    visit = NaturalVisit(seed, entered.layer, entered.objective, entered.enteredAt).also { run.visits += it }
                    pilot.riftDue = false
                }
                if (current != null && layerBefore != WorldLayer.BASE && state.worldLayer == WorldLayer.BASE) {
                    checkReturn(state, current, matterBefore, run)
                    awaiting = if (cardsExpected(current) > 0) current else null
                    awaitingSince = state.elapsed
                    visit = null
                }
            }
            visit?.let { checkVisit(state, it, run) }
            val pending = awaiting
            if (pending != null && state.phase == GamePhase.RUNNING && state.elapsed > awaitingSince + REWARD_WAIT) {
                run.breaks += "the ${pending.layer} reward never opened after the return at ${pending.returnedAt}"
                awaiting = null
            }
            state.takeSoundCues()
            state.takeVisualFxCues()
        }
        if (state.phase == GamePhase.GAME_OVER) visit?.diedInside = true
        run.phase = state.phase
        run.seconds = state.elapsed
        return run
    }

    /** Inside a visit: the layer is the visit's, its end and the Architect are never passed, a done objective has its way back. */
    private fun checkVisit(state: MutableGameState, record: NaturalVisit, run: NaturalRun) {
        val visit = state.dimensionRun.visit ?: return
        if (state.worldLayer != visit.layer) run.breaks += "inside ${visit.layer} but the layer is ${state.worldLayer} at ${state.elapsed}"
        // The timer exits on the step it is due, or one step later for a zero-integrity objective boss's kill sweep.
        if (state.elapsed > visit.endsAt + 3f * MutableGameState.FIXED_STEP) run.breaks += "${visit.layer} outlived its end ${visit.endsAt} at ${state.elapsed}"
        if (state.bossSpawned) run.breaks += "${visit.layer} still open with the Architect at ${state.elapsed}"
        if ((visit.completed || visit.failed) && visit.exitPortal == null) run.breaks += "${visit.objective} done without an exit rift at ${state.elapsed}"
        record.completed = visit.completed
        record.failed = visit.failed
    }

    /** No frame passes while the run is paused or a choice is open: the run and visit clocks stand. */
    private fun checkFrozen(state: MutableGameState, run: NaturalRun, what: String) {
        val elapsed = state.elapsed
        val local = state.dimensionLocalClock
        val visit = state.dimensionRun.visit
        state.update(INPUT_SECONDS)
        if (state.elapsed != elapsed || state.dimensionLocalClock != local || state.dimensionRun.visit !== visit) {
            run.breaks += "$what moved a clock at $elapsed"
        }
    }

    /** The return: realspace, no visit, the reward queued as earned and, from the Archive, its Matter paid once. */
    private fun checkReturn(state: MutableGameState, record: NaturalVisit, matterBefore: Float, run: NaturalRun) {
        record.returnedAt = state.elapsed
        if (state.dimensionRun.visit != null) run.breaks += "back in realspace with a visit at ${state.elapsed}"
        if (state.dimensionRun.completedMask and record.layer.bit != 0 != record.completed) {
            run.breaks += "${record.layer} completion ${record.completed} not recorded at the return"
        }
        if (record.layer == WorldLayer.ARCHIVE) {
            val reward = state.content.dimensions.definition(WorldLayer.ARCHIVE).reward
            val paid = state.runMatter + state.matterFraction - matterBefore
            val owed = if (record.completed) reward.matterOnSuccess else reward.matterOnFailure
            if (paid + MATTER_SLACK < owed) run.breaks += "the Archive paid $paid Matter of $owed"
            if (!record.completed) record.hand = "matter+${"%.0f".format(paid)}"
        }
    }

    private fun cardsExpected(visit: NaturalVisit): Int {
        val reward = canonicalGameplayContent.dimensions.definition(visit.layer).reward
        return if (visit.completed) reward.cardsOnSuccess else reward.cardsOnFailure
    }

    /** The hand a return opens: its layer's boons (and the Pact's Refuse card) or the Archive's items, as many as earned. */
    private fun checkHand(visit: NaturalVisit, type: ChoiceType?, cards: Int, run: NaturalRun) {
        val expectedType = if (visit.layer == WorldLayer.ARCHIVE) ChoiceType.ITEM else ChoiceType.BOON
        val expected = cardsExpected(visit) + if (visit.layer == WorldLayer.NADIR) 1 else 0
        if (type != expectedType || cards != expected) run.breaks += "${visit.layer} completed=${visit.completed} dealt ${type}x$cards, not ${expectedType}x$expected"
    }

    /** The glitch rift opens beside the line the Core flies on: at least 60 degrees off its heading. */
    private fun checkGlitchBeside(state: MutableGameState, x: Float, y: Float, run: NaturalRun) {
        if (state.speed < 80f) return
        val dx = x - state.coreX
        val dy = y - state.coreY
        val cosine = (dx * state.velocityX + dy * state.velocityY) / (length(dx, dy) * state.speed)
        val degrees = Math.toDegrees(acos(cosine.coerceIn(-1f, 1f)).toDouble())
        if (abs(degrees) < GLITCH_MIN_OFF_HEADING) run.breaks += "the glitch rift opened ${"%.0f".format(degrees)} deg off the heading"
    }

    private fun summary(runs: List<NaturalRun>): String {
        val visits = runs.flatMap { it.visits }
        fun share(objective: DimensionObjective): String {
            val played = visits.filter { it.objective == objective }
            return "$objective=${played.count { it.completed }}/${played.size}"
        }
        return "runs=${runs.size} windowRifts=${runs.sumOf { it.windowRifts }} runsWithRift=${runs.count { it.windowRifts > 0 }} " +
            "glitchRifts=${runs.sumOf { it.glitchRifts }} visits=${visits.size} " +
            WorldLayer.entries.filter { it != WorldLayer.BASE }.joinToString(" ") { layer -> "$layer=${visits.count { it.layer == layer }}" } + " " +
            DimensionObjective.entries.joinToString(" ") { share(it) } +
            " failedAscents=${visits.count { it.failed }} diedInside=${visits.count { it.diedInside }} " +
            "wins=${runs.count { it.phase == GamePhase.VICTORY }} breaks=${runs.sumOf { it.breaks.size }}"
    }

    private companion object {
        const val INPUT_SECONDS = 0.02f
        const val INPUT_MILLIS = 20
        /** Each visit is paused once, this long after its entry, for [PAUSE_FRAMES] input frames. */
        const val PAUSE_AFTER_ENTRY = 5f
        const val PAUSE_FRAMES = 50
        /** A return's reward opens within this long (after the level, directed, floor and Relic choices before it). */
        const val REWARD_WAIT = 5f
        /** Matter rounding a reward's payout may lose. */
        const val MATTER_SLACK = 0.01f
        const val GLITCH_MIN_OFF_HEADING = 60.0
    }
}
