// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.structures.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The dungeon pilot gate (structures §11.2): structures are opt-in content, so the population gates never enter them
 * and this gate measures them with an engaging pilot. [DungeonPilot] flies one dungeon attempt per run on the first
 * [GATE_RUNS] of [PopulationSeeds] (C42), in two arms: FRESH, gate A's fresh tier-0 profile (the Orb, no Matrix)
 * loaded out by seed parity with an orbiting weapon (Arc Coil) or a ram weapon (Flux Wake), and STRONG, gate B's
 * full-Matrix tier-3 Pentagon with its signature Arc Coil. A run stops [TAIL_SECONDS] after its attempt's outcome
 * (long enough for the pilot to open a waiting Totem chest, which the finished attempt no longer measures), or when
 * no attempt started by 150 s before the Architect.
 *
 * The thresholds are the structures design's bands (§11.2): a fresh build clears most of its dungeons without
 * trivialising them and rarely dies inside, a strong build clears nearly all of them, clearly more often and within
 * 150 s, every attempt ends within the pilot's 300 s, and the shipped templates turn up. Elemental squads keep
 * `garrisonScale` 0.8 and `wallDamageScale` 1.0. The slower flight law is balanced with integrity 0.75 and hostile
 * damage 0.72 for room-owned enemies. Lower integrity alone was not reliably better: on i = 2,301..2,400, 0.75
 * integrity gave 0.309 deaths and 0.65 gave 0.320. A surface-id/cooldown diagnostic ruled walls out (no
 * wall-impact frames in those deaths), so room attack damage changed while attacks and room populations stayed
 * intact. The pilot fights a room's boss (the Brood Queen, the Warden Prime) as the bot fights the Architect and the
 * other pilots fight their bosses; ramming it had made ram-weapon runs die in about half their attempts, almost all
 * of it contact damage. The damage multiplier was then chosen on i = 2,301..2,600 against a stated target: died at
 * most 0.15 (the gate draws more ram-weapon runs, 0.50 of its attempts against 0.40), clear at most 0.82 and every
 * 100-seed sub-window inside the band; 0.70 left clear 0.824 and 0.867 on i = 2,401..2,500, 0.72 is the smallest
 * change that meets it: fresh clear 0.807 (SE 0.023), died 0.146 (SE 0.021), strong clear 1.000, median 23.98 s.
 * The gate population confirms it without changing its design bands: fresh clear 0.670 (SE 0.043), died 0.186
 * (SE 0.036), abandoned 0.144; strong clear 1.000, median 24.88 s.
 *
 * Elite resonance (a weapon level per Elite-class kill) then shortened fresh fights: the gate read fresh clear 0.867
 * (SE 0.031), above the 0.85 ceiling, and nothing abandoned, so the 0.30 death ceiling became a 0.70 clear floor.
 * Orbiting runs clear every attempt; only ram runs die. Room integrity, the counterweight to weapon power, was chosen
 * on i = 2,301..2,400 and, as above, its sub-windows 2,401..2,500 and 2,501..2,600, against a stated target: pooled
 * fresh clear in [0.75, 0.80], the middle of that band, every sub-window inside the band and strong clear at least
 * 0.95. 0.90 is the smallest step that meets it (0.85 left 0.809): fresh clear 0.773 (SE 0.024), died 0.224, median
 * 69.40 s (the pre-resonance gate's 69.30 s), sub-windows 0.810, 0.758 and 0.750; strong clear 0.997, median 25.58 s.
 * The gate confirms it: fresh clear 0.758 (SE 0.039), died 0.242 (SE 0.039), median 73.86 s; strong clear 1.000,
 * median 26.54 s.
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
     * `KK_DUNGEON_KNOBS=garrisonScale=…,garrisonHealthMultiplier=…,garrisonDamageMultiplier=…,wallDamageScale=…`
     * (`;` separates knob sets flown one after another), and prints every run and the arms' summaries with bootstrap
     * standard errors.
     */
    @Test
    fun probeTheCalibrationWindow() {
        val window = System.getenv("KK_DUNGEON_PROBE")?.takeIf { it.isNotBlank() } ?: return
        val indices = probeWindow(window)
        val seeds = windowSeeds(indices)
        val arms = System.getenv("KK_DUNGEON_ARMS")?.takeIf { it.isNotBlank() }?.split(',')?.map { Arm.valueOf(it.trim()) }
            ?: Arm.entries
        for (knobs in System.getenv("KK_DUNGEON_KNOBS").orEmpty().split(';')) {
            val content = probedContent(knobs)
            println("DUNGEON PROBE window $indices garrisonScale=${content.structures.garrisonScale} " +
                "garrisonHealthMultiplier=${content.structures.garrisonHealthMultiplier} " +
                "garrisonDamageMultiplier=${content.structures.garrisonDamageMultiplier} wallDamageScale=${content.structures.wallDamageScale}")
            for (arm in arms) {
                val runs = populationOf(seeds) { seed -> fly(seed, arm, content) }
                runs.forEach { println("DUNGEON PROBE $arm ${it.line()}") }
                println("DUNGEON PROBE SUMMARY $arm ${summary(runs)}")
            }
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
        val entryHpShare: Float,
        val lastEngagedRoom: Int,
        val roomEntryHp: String,
        val wallImpactFrames: Int,
        val wallHpLossUpperBound: Double,
        val nonWallHpLoss: Double,
        /** Totem chest charges the attempted structure pays on its clear (0: none). */
        val chestCharges: Int,
        /** Keys the Core held when the attempt ended (-1 without an outcome). */
        val keysAtOutcome: Int,
        /** Chest charges the pilot opened after the outcome, before the run stopped (Keys spent: only a chest spends one). */
        val chestOpens: Int,
    ) {
        /** True when the attempt cleared a structure that pays a chest while the Core held a Key: the pilot should open it. */
        val chestWaiting: Boolean get() = attempt?.outcome == DungeonOutcome.CLEARED && chestCharges > 0 && keysAtOutcome > 0

        fun line(): String = "seed=$seed ${attempt?.line() ?: "NO_ATTEMPT"} stop=${"%.2f".format(elapsed)} phase=$phase " +
            "hp=${"%.2f".format(hp)} level=$level weapon=$weapon/$weaponLevel reason=$reason " +
            "entryHpShare=${"%.3f".format(entryHpShare)} lastRoom=$lastEngagedRoom roomHp=$roomEntryHp " +
            "wallImpactFrames=$wallImpactFrames wallHpLossUpperBound=${"%.2f".format(wallHpLossUpperBound)} " +
            "nonWallHpLoss=${"%.2f".format(nonWallHpLoss)} chest=$chestCharges keysAtOutcome=$keysAtOutcome chestOpens=$chestOpens"
    }

    internal companion object {
        /** Runs per arm: the first of [PopulationSeeds] (C42). */
        const val GATE_RUNS = 120

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

        /** The six dungeon templates (the Architect's Doodle is a secret, not a dungeon). */
        val DungeonTemplates: List<StructureTemplateId> =
            StructureTemplateId.entries.filter { !canonicalGameplayContent.structures.template(it).secret }

        val fresh: List<DungeonRun> by lazy { gate(Arm.FRESH) }
        val strong: List<DungeonRun> by lazy { gate(Arm.STRONG) }

        private fun gate(arm: Arm): List<DungeonRun> =
            populationOf(PopulationSeeds.take(GATE_RUNS)) { seed -> fly(seed, arm) }.also { runs ->
                println("DUNGEON GATE $arm ${summary(runs)}")
            }

        /**
         * One gate run of [arm] on [seed]: the pilot's attempt from 90 s, then [TAIL_SECONDS] more. Probes pass an
         * [observer] that sees the state after every input frame.
         */
        fun fly(
            seed: Int,
            arm: Arm,
            content: GameplayContentSnapshot = canonicalGameplayContent,
            observer: (MutableGameState) -> Unit = {},
        ): DungeonRun {
            val profile = when (arm) {
                // Harness H2: with no world Totem to recalibrate at, the fresh arm keeps both weapon families in its
                // attempts by loading out an orbiting weapon (Arc Coil) or a ram weapon (Flux Wake) by seed parity.
                Arm.FRESH -> gateProfile(GateMatrix.NONE, tier = 0, weapon = if (seed % 2 == 0) WeaponId.ARC_COIL else WeaponId.FLUX_WAKE, content = content)
                Arm.STRONG -> gateProfile(GateMatrix.FULL, tier = 3, shape = CoreShape.PENTAGON, weapon = WeaponId.ARC_COIL, content = content)
            }
            val bot = when (arm) {
                Arm.FRESH -> NoPoiBot()
                // Gate B's setup: the bot never binds a Relic in key harmony with its weapon.
                Arm.STRONG -> NoPoiBot(avoidWeaponHarmony = true)
            }
            val state = startGateRun(seed, profile, content = content)
            val pilot = DungeonPilot(bot)
            val seekEnd = content.tempo.bossAtSeconds - DungeonPilot.SEEK_LEAD
            var stopAt = Float.POSITIVE_INFINITY
            var entryHpShare = Float.NaN
            var lastEngagedRoom = -1
            val roomEntryHp = ArrayList<String>()
            var wallImpactFrames = 0
            var wallHpLossUpperBound = 0.0
            var nonWallHpLoss = 0.0
            var chestCharges = 0
            var keysAtOutcome = -1
            var chestOpens = 0
            while (state.elapsed < stopAt && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
                val heldBefore = state.dungeon != null
                val damageBefore = state.damageTaken
                val wallReadyBefore = state.obstacleHitReadyAt
                val endedBefore = pilot.attempt != null
                val keysBefore = state.keys
                if (state.phase == GamePhase.CHOICE) pilot.choose(state) else {
                    pilot.control(state)
                    state.update(0.02f)
                }
                if (heldBefore || state.dungeon != null) {
                    val wallImpact = state.obstacleHitReadyAt != wallReadyBefore && state.structureField.structures.any {
                        structureSurfaceId(it.id) == state.obstacleHitSurfaceId
                    }
                    val hpLoss = state.damageTaken - damageBefore
                    if (wallImpact) {
                        wallImpactFrames++
                        // Cooldown + surface id proves the wall event. Other damage may land in the same frame,
                        // so the frame's tracked HP loss is only an upper bound, never claimed as exact wall damage.
                        wallHpLossUpperBound += hpLoss
                    } else nonWallHpLoss += hpLoss
                }
                if (endedBefore && state.keys < keysBefore) chestOpens += keysBefore - state.keys
                state.dungeon?.let { held ->
                    chestCharges = held.chestCharges
                    if (entryHpShare.isNaN()) entryHpShare = state.hp / state.maxHp
                    if (held.engagedRoom >= 0 && held.engagedRoom != lastEngagedRoom) {
                        lastEngagedRoom = held.engagedRoom
                        roomEntryHp += "${held.engagedRoom}:${"%.3f".format(state.hp / state.maxHp)}"
                    }
                }
                pilot.observe(state)
                if (pilot.attempt != null && keysAtOutcome < 0) keysAtOutcome = state.keys
                observer(state)
                state.takeSoundCues()
                state.takeVisualFxCues()
                if (pilot.attempt != null && stopAt == Float.POSITIVE_INFINITY) stopAt = state.elapsed + TAIL_SECONDS
                if (pilot.attempt == null && !pilot.inside && state.elapsed >= seekEnd) break
            }
            pilot.observe(state)
            return DungeonRun(
                seed, pilot.attempt, state.phase, state.elapsed, state.hp, state.level, state.weapon, state.weaponLevel, state.message,
                entryHpShare, lastEngagedRoom, roomEntryHp.joinToString(","),
                wallImpactFrames, wallHpLossUpperBound, nonWallHpLoss, chestCharges, keysAtOutcome, chestOpens,
            )
        }

        /**
         * The arm's shares and median clear with bootstrap standard errors (resamples of runs), its templates, and how
         * many cleared attempts that left a chest while the Core held a Key had it opened before the run stopped.
         */
        fun summary(runs: List<DungeonRun>): String {
            val metrics = listOf<Pair<String, (List<DungeonRun>) -> Double>>(
                "attemptShare" to { sample -> sample.count { it.attempt != null }.toDouble() / sample.size },
                "clearShare" to { sample -> sample.shareOfAttempts(DungeonOutcome.CLEARED) },
                "diedShare" to { sample -> sample.shareOfAttempts(DungeonOutcome.DIED_INSIDE) },
                "abandonedShare" to { sample -> sample.shareOfAttempts(DungeonOutcome.ABANDONED) },
                "collapsedShare" to { sample -> sample.shareOfAttempts(DungeonOutcome.COLLAPSED) },
                "medianClear" to { sample -> sample.medianClearSeconds() },
            )
            val attempts = runs.mapNotNull { it.attempt }
            val templates = attempts.groupingBy { it.template }.eachCount().toSortedMap()
            val waiting = runs.filter { it.chestWaiting }
            return "n=${runs.size} attempts=${attempts.size} " +
                metrics.joinToString(" ") { (name, metric) -> bootstrapLine(name, runs, metric = metric) } + " templates=$templates" +
                " chestsOpened=${waiting.count { it.chestOpens > 0 }}/${waiting.size}"
        }

        /** The share of the runs' attempts that ended in [outcome] (NaN without an attempt). */
        fun List<DungeonRun>.shareOfAttempts(outcome: DungeonOutcome): Double {
            val attempts = mapNotNull { it.attempt }
            return if (attempts.isEmpty()) Double.NaN else attempts.count { it.outcome == outcome }.toDouble() / attempts.size
        }

        /** Median seconds from entering to the clear over the cleared attempts (NaN without one). */
        fun List<DungeonRun>.medianClearSeconds(): Double =
            mapNotNull { run -> run.attempt?.takeIf { it.outcome == DungeonOutcome.CLEARED }?.seconds }.finiteMedian()

        /** The shipped content with the structure knobs of [overrides] (`name=value`, comma-separated). */
        fun probedContent(overrides: String): GameplayContentSnapshot {
            var structures = canonicalGameplayContent.structures
            overrides.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { entry ->
                val (name, value) = entry.split('=').map { it.trim() }
                structures = when (name) {
                    "garrisonScale" -> structures.copy(garrisonScale = value.toFloat())
                    "garrisonHealthMultiplier" -> structures.copy(garrisonHealthMultiplier = value.toFloat())
                    "garrisonDamageMultiplier" -> structures.copy(garrisonDamageMultiplier = value.toFloat())
                    "wallDamageScale" -> structures.copy(wallDamageScale = value.toFloat())
                    else -> error("unknown structure knob $name")
                }
            }
            return canonicalGameplayContent.copy(structures = structures)
        }
    }
}
