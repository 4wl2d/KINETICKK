// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation.structures

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.*
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
 * The thresholds are the dungeon design's bands (D6 of the 0.6.0 rebalance; before it the structures design's §11.2
 * bands asked a fresh build to clear most dungeons, met by cutting room enemies to 0.8 of their count, 0.90 of their
 * integrity and 0.72 of their damage). Rooms are now the most dangerous places of a run: room enemies take the open
 * world's numbers, every garrisoned room fights in two waves and every final room in three, the last led by a guardian
 * with escorts, Rebirth tiers grow every ordinary entry with the enemy cap and add champions from tier 2, and every
 * sealed room telegraphs vents or lances (one more per cycle in the final room and per Rebirth tier). So a fresh build
 * clears about half its dungeons and dies inside a fifth to a third of them (the pilot retreats between rooms below 40 %
 * integrity, as a careful player would), a strong build still clears nearly all of them, clearly more often and within
 * 150 s, but takes at least a quarter of its integrity doing it, and in both arms the damage landing per second while
 * shut in a room is at least 1.2 times the plain bot's over the same spans of the same seeds in the open world. Every
 * attempt ends within the pilot's 300 s, and the shipped templates turn up.
 *
 * Calibration on i = 2,301..2,400 with its sub-windows 2,401..2,500 and 2,501..2,600 (the hazard damage 12 and one
 * more hazard per Rebirth tier, the smallest step that kept every window's strong median damage at 0.25 or more while
 * the fresh clear stayed inside its band; 14 with one more per three tiers left the fresh clear 0.38 and 0.39 on the
 * sub-windows): fresh clear 0.49, 0.42 and 0.41 (SE 0.05), died 0.20, 0.23 and 0.27, abandoned 0.31, 0.34 and 0.32;
 * strong clear 0.99, 1.00 and 1.00, median damage 0.296, 0.302 and 0.272 of max integrity (SE 0.02-0.04), median
 * clear 54-57 s; room over open-world damage per second 4.18, 4.44 and 3.52 fresh, 12.5, 9.8 and 6.7 strong. Ram-weapon
 * runs (Flux Wake, half the fresh arm) clear almost none: the bot's ram law meets every wave head on.
 */
class DungeonPilotGateTest {
    /**
     * A fresh profile clears a share of its attempted dungeons inside the design band [0.35, 0.65] and dies inside a
     * share of them inside [0.15, 0.35].
     */
    @Test
    fun aFreshProfileClearsAboutHalfItsDungeonsAndDiesInsideAFifthToAThirdOfThem() {
        val clear = fresh.shareOfAttempts(DungeonOutcome.CLEARED)
        val died = fresh.shareOfAttempts(DungeonOutcome.DIED_INSIDE)
        assertTrue(clear in FRESH_MIN_CLEAR_SHARE..FRESH_MAX_CLEAR_SHARE,
            "fresh clear share ${"%.3f".format(clear)} outside $FRESH_MIN_CLEAR_SHARE..$FRESH_MAX_CLEAR_SHARE")
        assertTrue(died in FRESH_MIN_DIED_SHARE..FRESH_MAX_DIED_SHARE,
            "fresh died-inside share ${"%.3f".format(died)} outside $FRESH_MIN_DIED_SHARE..$FRESH_MAX_DIED_SHARE")
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

    /** A strong build's median attempt lands at least [STRONG_MIN_MEDIAN_DAMAGE_SHARE] of its max integrity on it. */
    @Test
    fun aStrongBuildStillTakesAQuarterOfItsIntegrityInADungeon() {
        val damage = strong.medianAttemptDamageShare()
        assertTrue(damage >= STRONG_MIN_MEDIAN_DAMAGE_SHARE,
            "strong median attempt damage ${"%.3f".format(damage)} of max integrity below $STRONG_MIN_MEDIAN_DAMAGE_SHARE")
    }

    /**
     * In both arms the damage landing per second while shut in a sealed room is at least [MIN_ROOM_WORLD_DAMAGE_RATIO]
     * times what the plain bot takes per second over the same spans of the same seeds in the open world.
     */
    @Test
    fun sealedRoomsHurtMoreThanTheOpenWorldAtTheSameTime() {
        for ((arm, runs) in listOf(Arm.FRESH to fresh, Arm.STRONG to strong)) {
            val ratio = runs.roomDamagePerSecond() / runs.worldDamagePerSecond()
            assertTrue(ratio >= MIN_ROOM_WORLD_DAMAGE_RATIO, "$arm room/world damage per second ${"%.2f".format(ratio)} below $MIN_ROOM_WORLD_DAMAGE_RATIO")
        }
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
        val replays = List(2) { fly(PopulationSeeds.first(), Arm.FRESH, measureWorld = true).line() }
        assertEquals(replays[0], replays[1])
        assertEquals(replays[0], fresh.first().line())
    }

    /**
     * Calibration probe (never the gate population): `KK_DUNGEON_PROBE=first..last` flies the window
     * `i × 7,919 + 13` for both arms (`KK_DUNGEON_ARMS=FRESH,STRONG` picks arms) with the structure knobs of
     * `KK_DUNGEON_KNOBS=garrisonScale=…,garrisonHealthMultiplier=…,garrisonDamageMultiplier=…,wallDamageScale=…,`
     * `waveTriggerShare=…,waveIntervalSeconds=…,waveTelegraphSeconds=…,guardianIntegrityScale=…,championChancePerTier=…,`
     * `hazardDamage=…,hazardPeriod=…,hazardFirstDelay=…,hazardTelegraph=…,hazardVents=…,hazardLances=…,hazardExtraPerTiers=…,hazards=…`
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
            val policy = content.structures
            println("DUNGEON PROBE window $indices knobs=[$knobs] garrisonScale=${policy.garrisonScale} " +
                "health=${policy.garrisonHealthMultiplier} damage=${policy.garrisonDamageMultiplier} " +
                "waveTriggerShare=${policy.waveTriggerShare} waveInterval=${policy.waveIntervalSeconds} " +
                "guardianScale=${policy.guardianIntegrityScale} hazards=${policy.hazards}")
            for (arm in arms) {
                val runs = populationOf(seeds) { seed -> fly(seed, arm, content, measureWorld = true) }
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
        /** Max integrity when the Core entered the attempted structure (NaN without an attempt). */
        val entryMaxHp: Float,
        /** Damage landed (integrity and shield) from entering to the outcome. */
        val attemptDamage: Double,
        /** Seconds the Core spent shut in a sealed room and the damage that landed on it meanwhile. */
        val sealedSeconds: Double,
        val sealedDamage: Double,
        /** The same seed and profile played by the plain bot (no dungeon): damage landed and seconds alive over the sealed spans. */
        val worldDamage: Double,
        val worldSeconds: Double,
    ) {
        /** Damage landed from entering to the outcome as a share of max integrity at entry (NaN without an attempt). */
        val attemptDamageShare: Double get() = attemptDamage / entryMaxHp

        /** True when the attempt cleared a structure that pays a chest while the Core held a Key: the pilot should open it. */
        val chestWaiting: Boolean get() = attempt?.outcome == DungeonOutcome.CLEARED && chestCharges > 0 && keysAtOutcome > 0

        fun line(): String = "seed=$seed ${attempt?.line() ?: "NO_ATTEMPT"} stop=${"%.2f".format(elapsed)} phase=$phase " +
            "hp=${"%.2f".format(hp)} level=$level weapon=$weapon/$weaponLevel reason=$reason " +
            "entryHpShare=${"%.3f".format(entryHpShare)} lastRoom=$lastEngagedRoom roomHp=$roomEntryHp " +
            "wallImpactFrames=$wallImpactFrames wallHpLossUpperBound=${"%.2f".format(wallHpLossUpperBound)} " +
            "nonWallHpLoss=${"%.2f".format(nonWallHpLoss)} chest=$chestCharges keysAtOutcome=$keysAtOutcome chestOpens=$chestOpens " +
            "entryMaxHp=${"%.1f".format(entryMaxHp)} attemptDamageShare=${"%.3f".format(attemptDamageShare)} sealed=${"%.2f".format(sealedSeconds)}s/${"%.2f".format(sealedDamage)} " +
            "world=${"%.2f".format(worldSeconds)}s/${"%.2f".format(worldDamage)}"
    }

    internal companion object {
        /** Runs per arm: the first of [PopulationSeeds] (C42). */
        const val GATE_RUNS = 120

        /** Seconds a run continues after its attempt's outcome. */
        const val TAIL_SECONDS = 5f

        /** One input frame: the give-up is read after the frame that crosses it. */
        const val OUTCOME_FRAME = 0.05f

        /** Simulated seconds of one pilot input frame. */
        const val FRAME_SECONDS = 0.02f

        const val FRESH_MIN_CLEAR_SHARE = 0.35
        const val FRESH_MAX_CLEAR_SHARE = 0.65
        const val FRESH_MIN_DIED_SHARE = 0.15
        const val FRESH_MAX_DIED_SHARE = 0.35
        const val STRONG_MIN_CLEAR_SHARE = 0.85
        const val STRONG_MIN_MEDIAN_DAMAGE_SHARE = 0.25
        const val MIN_ROOM_WORLD_DAMAGE_RATIO = 1.2
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
            populationOf(PopulationSeeds.take(GATE_RUNS)) { seed -> fly(seed, arm, measureWorld = true) }.also { runs ->
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
            measureWorld: Boolean = false,
            observer: (MutableGameState) -> Unit = {},
        ): DungeonRun {
            val state = startGateRun(seed, armProfile(seed, arm, content), content = content)
            val pilot = DungeonPilot(armBot(arm))
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
            var entryMaxHp = Float.NaN
            var sealedSeconds = 0.0
            var sealedDamage = 0.0
            var attemptDamage = 0.0
            val sealedSpans = ArrayList<Float>()
            while (state.elapsed < stopAt && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
                val heldBefore = state.dungeon != null
                val sealedBefore = state.structureEngagementActive()
                val landedBefore = state.landedDamage()
                val damageBefore = state.damageTaken
                val wallReadyBefore = state.obstacleHitReadyAt
                val endedBefore = pilot.attempt != null
                val keysBefore = state.keys
                if (state.phase == GamePhase.CHOICE) pilot.choose(state) else {
                    pilot.control(state)
                    state.update(FRAME_SECONDS)
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
                val sealedAfter = state.structureEngagementActive()
                if (sealedBefore && state.phase != GamePhase.CHOICE) {
                    sealedSeconds += FRAME_SECONDS
                    sealedDamage += state.landedDamage() - landedBefore
                }
                if (heldBefore && !endedBefore) attemptDamage += state.landedDamage() - landedBefore
                if (sealedAfter != sealedBefore) sealedSpans += state.elapsed
                state.dungeon?.let { held ->
                    chestCharges = held.chestCharges
                    if (entryHpShare.isNaN()) {
                        entryHpShare = state.hp / state.maxHp
                        entryMaxHp = state.maxHp
                    }
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
            if (sealedSpans.size % 2 == 1) sealedSpans += state.elapsed
            val world = if (measureWorld) worldDamageOver(seed, arm, content, sealedSpans) else 0.0 to 0.0
            return DungeonRun(
                seed, pilot.attempt, state.phase, state.elapsed, state.hp, state.level, state.weapon, state.weaponLevel, state.message,
                entryHpShare, lastEngagedRoom, roomEntryHp.joinToString(","),
                wallImpactFrames, wallHpLossUpperBound, nonWallHpLoss, chestCharges, keysAtOutcome, chestOpens,
                entryMaxHp, attemptDamage, sealedSeconds, sealedDamage, world.first, world.second,
            )
        }

        /** Damage that landed on the Core so far, on its integrity or its shield. */
        private fun MutableGameState.landedDamage(): Double = damageTaken + damageAbsorbed

        private fun armProfile(seed: Int, arm: Arm, content: GameplayContentSnapshot) = when (arm) {
            // Harness H2: with no world Totem to recalibrate at, the fresh arm keeps both weapon families in its
            // attempts by loading out an orbiting weapon (Arc Coil) or a ram weapon (Flux Wake) by seed parity.
            Arm.FRESH -> gateProfile(GateMatrix.NONE, tier = 0, weapon = if (seed % 2 == 0) WeaponId.ARC_COIL else WeaponId.FLUX_WAKE, content = content)
            Arm.STRONG -> gateProfile(GateMatrix.FULL, tier = 3, shape = CoreShape.PENTAGON, weapon = WeaponId.ARC_COIL, content = content)
        }

        private fun armBot(arm: Arm) = when (arm) {
            Arm.FRESH -> NoPoiBot()
            // Gate B's setup: the bot never binds a Relic in key harmony with its weapon.
            Arm.STRONG -> NoPoiBot(avoidWeaponHarmony = true)
        }

        /**
         * The open world at the same time: [seed] and [arm]'s profile played by the plain bot (which never enters a
         * structure) through the run-time spans [spans] (start, end pairs); returns the integrity it lost inside them
         * and the seconds of them it lived through.
         */
        private fun worldDamageOver(seed: Int, arm: Arm, content: GameplayContentSnapshot, spans: List<Float>): Pair<Double, Double> {
            if (spans.isEmpty()) return 0.0 to 0.0
            val state = startGateRun(seed, armProfile(seed, arm, content), content = content)
            val bot = armBot(arm)
            var damage = 0.0
            var seconds = 0.0
            var span = 0
            while (span < spans.size && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
                if (state.phase == GamePhase.CHOICE) bot.choose(state) else {
                    val inside = span % 2 == 1
                    val damageBefore = state.landedDamage()
                    bot.control(state)
                    state.update(FRAME_SECONDS)
                    if (inside) {
                        seconds += FRAME_SECONDS
                        damage += state.landedDamage() - damageBefore
                    }
                }
                state.takeSoundCues()
                state.takeVisualFxCues()
                while (span < spans.size && state.elapsed >= spans[span]) span++
            }
            return damage to seconds
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
                "medianAttemptDamage" to { sample -> sample.medianAttemptDamageShare() },
                "roomDps" to { sample -> sample.roomDamagePerSecond() },
                "worldDps" to { sample -> sample.worldDamagePerSecond() },
                "roomWorldRatio" to { sample -> sample.roomDamagePerSecond() / sample.worldDamagePerSecond() },
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

        /** Median integrity lost per attempt as a share of max integrity at entry (NaN without an attempt). */
        fun List<DungeonRun>.medianAttemptDamageShare(): Double =
            mapNotNull { run -> run.attempt?.let { run.attemptDamageShare.toFloat() } }.finiteMedian()

        /** Integrity lost per second shut in sealed rooms, pooled over the runs. */
        fun List<DungeonRun>.roomDamagePerSecond(): Double = sumOf { it.sealedDamage } / sumOf { it.sealedSeconds }

        /** Integrity the plain bot lost per second over the same spans of the same seeds, pooled over the runs. */
        fun List<DungeonRun>.worldDamagePerSecond(): Double = sumOf { it.worldDamage } / sumOf { it.worldSeconds }

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
                    "waveTriggerShare" -> structures.copy(waveTriggerShare = value.toFloat())
                    "waveIntervalSeconds" -> structures.copy(waveIntervalSeconds = value.toFloat())
                    "guardianIntegrityScale" -> structures.copy(guardianIntegrityScale = value.toFloat())
                    "championChancePerTier" -> structures.copy(garrisonChampionChancePerTier = value.toFloat())
                    "waveTelegraphSeconds" -> structures.copy(waveTelegraphSeconds = value.toFloat())
                    "hazardDamage" -> structures.copy(hazards = structures.hazards.copy(coreDamage = value.toFloat()))
                    "hazardPeriod" -> structures.copy(hazards = structures.hazards.copy(periodSeconds = value.toFloat()))
                    "hazardFirstDelay" -> structures.copy(hazards = structures.hazards.copy(firstDelaySeconds = value.toFloat()))
                    "hazardVents" -> structures.copy(hazards = structures.hazards.copy(ventsPerCycle = value.toInt()))
                    "hazardLances" -> structures.copy(hazards = structures.hazards.copy(lancesPerCycle = value.toInt()))
                    "hazardExtraPerTiers" -> structures.copy(hazards = structures.hazards.copy(extraPerTiers = value.toInt()))
                    "hazardTelegraph" -> structures.copy(hazards = structures.hazards.copy(telegraphSeconds = value.toFloat()))
                    "hazards" -> structures.copy(hazards = structures.hazards.copy(enabled = value.toBoolean()))
                    else -> error("unknown structure knob $name")
                }
            }
            return canonicalGameplayContent.copy(structures = structures)
        }
    }
}
