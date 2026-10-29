// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The dungeon pilot gate (structures §11.2): structures are opt-in content, so the population gates never enter them
 * and this gate measures them with an engaging pilot. [DungeonPilot] flies one dungeon attempt per run on the first
 * [GATE_RUNS] of [PopulationSeeds] (C42), in two arms: FRESH, gate A's fresh tier-0 profile (the Orb and the Flux
 * Wake, no Matrix), and STRONG, gate B's full-Matrix tier-3 Pentagon with its signature Arc Coil. A run stops
 * [TAIL_SECONDS] after its attempt's outcome, or when no attempt started by 150 s before the Architect.
 *
 * The thresholds are the structures design's bands (§11.2): a fresh build clears most of its dungeons without
 * trivialising them and rarely dies inside, a strong build clears nearly all of them, clearly more often and within
 * 150 s, every attempt ends within the pilot's 300 s, and the shipped templates turn up. The shipped knobs
 * (`garrisonScale` 0.8, `wallDamageScale` 1.0) meet every band on the structures calibration window i = 2,301..2,400
 * after excluding director spawns from sealed rooms (`KK_DUNGEON_PROBE`; FRESH clear 0.720 SE 0.045, died 0.250
 * SE 0.043; STRONG clear 1.000, median clear 26.12 s). Scale 1.0 and 0.9 both gave a 0.310 fresh death share on
 * that window. The gate population only confirms the chosen content; its design bands stay unchanged.
 */
class DungeonPilotGateTest {
    /**
     * A fresh profile clears a share of its attempted dungeons inside the design band [0.35, 0.85] and dies inside at
     * most [FRESH_MAX_DIED_SHARE] of them.
     */
    @Test
    fun aFreshProfileClearsMostDungeonsItAttemptsAndRarelyDiesInside() {
        val clear = fresh.shareOfAttempts(DungeonOutcome.CLEARED)
        val died = fresh.shareOfAttempts(DungeonOutcome.DIED_INSIDE)
        assertTrue(clear in FRESH_MIN_CLEAR_SHARE..FRESH_MAX_CLEAR_SHARE,
            "fresh clear share ${"%.3f".format(clear)} outside $FRESH_MIN_CLEAR_SHARE..$FRESH_MAX_CLEAR_SHARE")
        assertTrue(died <= FRESH_MAX_DIED_SHARE, "fresh died-inside share ${"%.3f".format(died)} above $FRESH_MAX_DIED_SHARE")
    }

    /**
     * A strong build clears at least [STRONG_MIN_CLEAR_SHARE] of its attempts, at least [MIN_CLEAR_SHARE_GAP] more than
     * a fresh profile, with a median clear of at most [STRONG_MAX_MEDIAN_CLEAR] s from entering the footprint.
     */
    @Test
    fun aStrongBuildClearsNearlyEveryDungeonClearlyMoreOftenAndWithinTwoAndAHalfMinutes() {
        val strongClear = strong.shareOfAttempts(DungeonOutcome.CLEARED)
        val freshClear = fresh.shareOfAttempts(DungeonOutcome.CLEARED)
        val median = strong.medianClearSeconds()
        assertTrue(strongClear >= STRONG_MIN_CLEAR_SHARE, "strong clear share ${"%.3f".format(strongClear)} below $STRONG_MIN_CLEAR_SHARE")
        assertTrue(strongClear - freshClear >= MIN_CLEAR_SHARE_GAP,
            "strong − fresh clear share ${"%.3f".format(strongClear - freshClear)} below $MIN_CLEAR_SHARE_GAP")
        assertTrue(median <= STRONG_MAX_MEDIAN_CLEAR, "strong median clear $median s above $STRONG_MAX_MEDIAN_CLEAR s")
    }

    /**
     * Every arm attempts a dungeon in at least [MIN_ATTEMPT_SHARE] of its runs, and every attempt ends in one of the
     * four outcomes within the pilot's [DungeonPilot.GIVE_UP_SECONDS] (no hang).
     */
    @Test
    fun everyArmAttemptsItsDungeonsAndEveryAttemptEndsWithinFiveMinutes() {
        for ((arm, runs) in listOf(Arm.FRESH to fresh, Arm.STRONG to strong)) {
            val share = runs.count { it.attempt != null }.toDouble() / runs.size
            assertTrue(share >= MIN_ATTEMPT_SHARE, "$arm attempted in ${"%.3f".format(share)} of its runs, below $MIN_ATTEMPT_SHARE")
            runs.forEach { run ->
                val attempt = run.attempt ?: return@forEach
                assertTrue(attempt.seconds <= DungeonPilot.GIVE_UP_SECONDS + OUTCOME_FRAME, "$arm ${run.line()}")
            }
        }
    }

    /** At least [MIN_TEMPLATES] of the six dungeon templates appear among each arm's attempts. */
    @Test
    fun eachArmMeetsAtLeastFiveOfTheSixDungeonTemplates() {
        for ((arm, runs) in listOf(Arm.FRESH to fresh, Arm.STRONG to strong)) {
            val met = runs.mapNotNull { it.attempt?.template }.toSet()
            assertTrue(met.count { it in DungeonTemplates } >= MIN_TEMPLATES, "$arm met only $met")
        }
    }

    /** The reference seed flies the same attempt twice on this thread and once more in the gate population. */
    @Test
    fun theReferenceSeedFliesTheSameAttemptEveryTime() {
        val replays = List(2) { fly(PopulationSeeds.first(), Arm.FRESH).line() }
        assertEquals(replays[0], replays[1])
        assertEquals(replays[0], fresh.first().line())
    }

    /**
     * Calibration probe (never the gate population): `KK_DUNGEON_PROBE=first..last` flies the window
     * `i × 7,919 + 13` for both arms (`KK_DUNGEON_ARMS=FRESH,STRONG` picks arms) with the structure knobs of
     * `KK_DUNGEON_KNOBS=garrisonScale=…,wallDamageScale=…`, and prints every run and the arms' summaries with bootstrap
     * standard errors.
     */
    @Test
    fun probeTheCalibrationWindow() {
        val window = System.getenv("KK_DUNGEON_PROBE")?.takeIf { it.isNotBlank() } ?: return
        val (first, last) = window.split("..").map { it.trim().toInt() }
        require(first in 1..last) { "KK_DUNGEON_PROBE must be first..last, got $window" }
        require(last < GATE_FIRST || first > GATE_LAST) { "the gate population ($GATE_FIRST..$GATE_LAST) is never probed" }
        val seeds = (first..last).map { it * 7_919 + 13 }
        val content = probedContent(System.getenv("KK_DUNGEON_KNOBS").orEmpty())
        val arms = System.getenv("KK_DUNGEON_ARMS")?.takeIf { it.isNotBlank() }?.split(',')?.map { Arm.valueOf(it.trim()) }
            ?: Arm.entries
        println("DUNGEON PROBE window $first..$last garrisonScale=${content.structures.garrisonScale} " +
            "wallDamageScale=${content.structures.wallDamageScale}")
        for (arm in arms) {
            val runs = populationOf(seeds) { seed -> fly(seed, arm, content) }
            runs.forEach { println("DUNGEON PROBE $arm ${it.line()}") }
            println("DUNGEON PROBE SUMMARY $arm ${summary(runs)}")
        }
    }

    internal enum class Arm { FRESH, STRONG }

    /** One gate run: its seed, the attempt (null when none started) and how the run stood when it stopped. */
    internal class DungeonRun(
        val seed: Int,
        val attempt: DungeonAttempt?,
        val phase: GamePhase,
        val elapsed: Float,
        val hp: Float,
        val level: Int,
        val weapon: WeaponId,
        val weaponLevel: Int,
        val reason: String,
    ) {
        fun line(): String = "seed=$seed ${attempt?.line() ?: "NO_ATTEMPT"} stop=${"%.2f".format(elapsed)} phase=$phase " +
            "hp=${"%.2f".format(hp)} level=$level weapon=$weapon/$weaponLevel reason=$reason"
    }

    internal companion object {
        /** Runs per arm: the first of [PopulationSeeds] (C42). */
        const val GATE_RUNS = 120

        const val GATE_FIRST = 1_001
        const val GATE_LAST = 1_300

        /** Seconds a run continues after its attempt's outcome. */
        const val TAIL_SECONDS = 5f

        /** One input frame: the give-up is read after the frame that crosses it. */
        const val OUTCOME_FRAME = 0.05f

        const val FRESH_MIN_CLEAR_SHARE = 0.35
        const val FRESH_MAX_CLEAR_SHARE = 0.85
        const val FRESH_MAX_DIED_SHARE = 0.30
        const val STRONG_MIN_CLEAR_SHARE = 0.85
        const val MIN_CLEAR_SHARE_GAP = 0.15
        const val STRONG_MAX_MEDIAN_CLEAR = 150f
        const val MIN_ATTEMPT_SHARE = 0.9
        const val MIN_TEMPLATES = 5

        const val BOOTSTRAP_RESAMPLES = 20_000
        const val BOOTSTRAP_SEED = 20261027

        /** The six dungeon templates (the Architect's Doodle is a secret, not a dungeon). */
        val DungeonTemplates: List<StructureTemplateId> =
            StructureTemplateId.entries.filter { !canonicalGameplayContent.structures.template(it).secret }

        val fresh: List<DungeonRun> by lazy { gate(Arm.FRESH) }
        val strong: List<DungeonRun> by lazy { gate(Arm.STRONG) }

        private fun gate(arm: Arm): List<DungeonRun> =
            populationOf(PopulationSeeds.take(GATE_RUNS)) { seed -> fly(seed, arm) }.also { runs ->
                println("DUNGEON GATE $arm ${summary(runs)}")
            }

        /** One gate run of [arm] on [seed]: the pilot's attempt from 90 s, then [TAIL_SECONDS] more. */
        fun fly(seed: Int, arm: Arm, content: GameplayContentSnapshot = canonicalGameplayContent): DungeonRun {
            val profile = when (arm) {
                Arm.FRESH -> gateProfile(GateMatrix.NONE, tier = 0, content = content)
                Arm.STRONG -> gateProfile(GateMatrix.FULL, tier = 3, shape = CoreShape.PENTAGON, weapon = WeaponId.ARC_COIL, content = content)
            }
            val bot = when (arm) {
                Arm.FRESH -> NoPoiBot()
                // Gate B's setup: the bot never binds a Relic in key harmony with its weapon.
                Arm.STRONG -> NoPoiBot(avoidWeaponHarmony = true)
            }
            val state = MutableGameState(content, seed = seed, bootstrapProgress = profile)
            state.unlockWeaponsForTesting(ReferenceArmory)
            state.startRun()
            val pilot = DungeonPilot(bot)
            val seekEnd = content.tempo.bossAtSeconds - 150f
            var stopAt = Float.POSITIVE_INFINITY
            while (state.elapsed < stopAt && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
                if (state.phase == GamePhase.CHOICE) pilot.choose(state) else {
                    pilot.control(state)
                    state.update(0.02f)
                }
                pilot.observe(state)
                state.takeSoundCues()
                state.takeVisualFxCues()
                if (pilot.attempt != null && stopAt == Float.POSITIVE_INFINITY) stopAt = state.elapsed + TAIL_SECONDS
                if (pilot.attempt == null && !pilot.inside && state.elapsed >= seekEnd) break
            }
            pilot.observe(state)
            return DungeonRun(
                seed, pilot.attempt, state.phase, state.elapsed, state.hp, state.level, state.weapon, state.weaponLevel, state.message,
            )
        }

        /** The arm's shares and median clear with bootstrap standard errors (resamples of runs) and its templates. */
        fun summary(runs: List<DungeonRun>): String {
            val metrics = listOf<Pair<String, (List<DungeonRun>) -> Double>>(
                "attemptShare" to { sample -> sample.count { it.attempt != null }.toDouble() / sample.size },
                "clearShare" to { sample -> sample.shareOfAttempts(DungeonOutcome.CLEARED) },
                "diedShare" to { sample -> sample.shareOfAttempts(DungeonOutcome.DIED_INSIDE) },
                "abandonedShare" to { sample -> sample.shareOfAttempts(DungeonOutcome.ABANDONED) },
                "collapsedShare" to { sample -> sample.shareOfAttempts(DungeonOutcome.COLLAPSED) },
                "medianClear" to { sample -> sample.medianClearSeconds().toDouble() },
            )
            val random = Random(BOOTSTRAP_SEED)
            val resampled = List(metrics.size) { DoubleArray(BOOTSTRAP_RESAMPLES) }
            repeat(BOOTSTRAP_RESAMPLES) { resample ->
                val sample = List(runs.size) { runs[random.nextInt(runs.size)] }
                metrics.forEachIndexed { index, (_, metric) -> resampled[index][resample] = metric(sample) }
            }
            val attempts = runs.mapNotNull { it.attempt }
            val templates = attempts.groupingBy { it.template }.eachCount().toSortedMap()
            return "n=${runs.size} attempts=${attempts.size} " + metrics.mapIndexed { index, (name, metric) ->
                val values = resampled[index].filter { !it.isNaN() }
                val mean = values.average()
                val se = sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
                "$name=${"%.4f".format(metric(runs))}(SE ${"%.4f".format(se)})"
            }.joinToString(" ") + " templates=$templates"
        }

        /** The share of the runs' attempts that ended in [outcome] (NaN without an attempt). */
        fun List<DungeonRun>.shareOfAttempts(outcome: DungeonOutcome): Double {
            val attempts = mapNotNull { it.attempt }
            return if (attempts.isEmpty()) Double.NaN else attempts.count { it.outcome == outcome }.toDouble() / attempts.size
        }

        /** Median seconds from entering to the clear over the cleared attempts (NaN without one). */
        fun List<DungeonRun>.medianClearSeconds(): Float {
            val clears = mapNotNull { run -> run.attempt?.takeIf { it.outcome == DungeonOutcome.CLEARED }?.seconds }
            return if (clears.isEmpty()) Float.NaN else clears.gateMedian()
        }

        /** The shipped content with the structure knobs of [overrides] (`name=value`, comma-separated). */
        fun probedContent(overrides: String): GameplayContentSnapshot {
            var structures = canonicalGameplayContent.structures
            overrides.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { entry ->
                val (name, value) = entry.split('=').map { it.trim() }
                structures = when (name) {
                    "garrisonScale" -> structures.copy(garrisonScale = value.toFloat())
                    "wallDamageScale" -> structures.copy(wallDamageScale = value.toFloat())
                    else -> error("unknown structure knob $name")
                }
            }
            return canonicalGameplayContent.copy(structures = structures)
        }
    }
}
