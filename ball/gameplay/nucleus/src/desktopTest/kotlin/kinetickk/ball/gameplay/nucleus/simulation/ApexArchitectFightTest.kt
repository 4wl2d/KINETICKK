// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.ApexCell
import kinetickk.ball.gameplay.nucleus.model.WeaponRuntime
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.RebirthProgress
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Apex against the Architect (combat-final §5.2, §10 "Apex"; lead addendum B1).
 *
 * Fights: per character, tier (0 fresh; 10 with the strong build's Matrix, Arc Coil and Relics) and seed, the
 * no-POI bot plays the run to the Architect's arrival (the Core cannot be hurt on the way; a singularity contact can
 * still end a run, so a case takes the first runs of a fixed seed list that reach the Architect); the run then forks
 * into two arms that play the next 180 s with copies of the same bot, both with the level forced to the cap (so
 * neither keeps drafting): one unlocks the Apex (the bot casts it by its rule), one does not. In the fight the Core's
 * integrity is refilled every frame and the damage it takes (shield and integrity) is counted.
 *
 * The bot casts its Apex in nearly every fight: a population rule over [PopulationSeeds] (see
 * [theBotCastsItsApexInNearlyEveryArchitectFight]). The rest of the contract holds in every Apex arm of the first three
 * runs per case: each strike stays within the boss's hit cap, an activation spends at most its strikes and its bolt
 * budget, the boss's integrity never moves while it shifts, every shift starts at a phase gate and lasts exactly 8 s,
 * its escorts arrive, and the Apex costs the Architect at most 10 % of the volleys its own timer schedules: a volley
 * counts against it when the boss could attack and the shared projectile pool was at the volley budget only because of
 * live Apex shots (a Silence and a pool full of other shots hold volleys back with or without an Apex). In a scripted
 * arena where the Architect is the only enemy, the character's own damage to it during an activation (strikes included)
 * is never below the same script's without the Apex.
 *
 * The benefit is judged on many paired fights, because one fight's time-to-kill moves by ±15 % when the Core starts
 * 0.01 u elsewhere: each run is forked into [FORKS] pairs whose two arms both start with the Core moved by k/100 u
 * (k = 0 is the unmoved fight). Offensive characters (tier 0, the first [BENEFIT_RUNS] runs each along
 * [PopulationSeeds]): per character the median over its pairs of the with/without time-to-kill ratio; the median of the
 * ten lies in [0.75, 0.95] and each character's benefit within ±40 % of that median benefit. The Prism and the Diamond
 * (tier 10, where the Architect's fire and the arena put real damage on them; at tier 0 their facets and parries leave
 * almost nothing to defend; the Prism's first [DEFENSIVE_RUNS] runs along the fixed seed list, the Diamond's first
 * [DIAMOND_RUNS] along [PopulationSeeds]): the damage taken during the Apex arm's windows, summed over the pairs, is
 * [0.55, 0.85] of what the no-Apex arm takes in the same windows.
 *
 * The offence band and the Diamond's band are population rules (lead Addendum D5, 0.5.0 active items). On the fixed
 * seed list (48 offensive runs per character, 64 defensive) the offence passed with probability 0.97 on the 05885fa9
 * base but 0.87 with the shipped active items, through its ±40 % clause (the median band passed every time, and the
 * clause is measured against it), and the Diamond passed with probability 0.99 and 0.72. Their populations are the
 * ones the base was measured on, 96 offensive and 128 Diamond runs with four forks each: fewer than 300 runs, since
 * these fights already set the suite's runtime. On the base the offence median was 0.847 (bootstrap SE 0.005), each
 * character's benefit 0.80-1.17 of the median benefit (SE 0.06-0.08), and the Diamond's ratio 0.694 (SE 0.041); by
 * Addendum D1 (the wider of the design's band and the base ± two standard errors) every band stays the design's. The
 * Prism's band passed with probability 0.99 and 0.95 and keeps the fixed seed list.
 */
class ApexArchitectFightTest {
    @Test
    fun everyApexKeepsTheArchitectContractAtTierZeroAndTen() {
        val broken = mutableListOf<String>()
        for (shape in CoreShape.entries) for (tier in listOf(0, 10)) {
            if (fightsOf(shape, tier).size < FIGHTS_PER_CASE) broken += "$shape tier $tier reached too few Architects"
        }
        for ((key, pair) in fights) {
            val (shape, tier, seed) = key
            val label = "$shape tier $tier seed $seed"
            val (apex, plain) = pair
            println("APEX-FIGHT $shape tier=$tier seed=$seed ${apex.line()} | without ${plain.line()}")
            if (apex.strikeExcess > 1e-3f) broken += "$label strike over the cap by ${apex.strikeExcess}"
            if (apex.maxStrikes > apex.strikeBudget) broken += "$label spent ${apex.maxStrikes} strikes"
            if (apex.maxErasures > apex.boltBudget || apex.minBoltsLeft < 0f) broken += "$label erased ${apex.maxErasures}"
            if (apex.shiftingLoss > 0f) broken += "$label boss lost ${apex.shiftingLoss} while shifting"
            // A shift starts inside a frame (up to three fixed steps) at a phase gate and ends 8 s later.
            val earliest = ARCHITECT_SHIFT_SECONDS - FRAME - MutableGameState.FIXED_STEP
            if (apex.shiftLengths.any { it !in earliest..ARCHITECT_SHIFT_SECONDS + 1e-3f }) {
                broken += "$label shifts end ${apex.shiftLengths} s after their frame"
            }
            if (apex.shiftEscorts.any { it < 2 }) broken += "$label escorts ${apex.shiftEscorts}"
            if (apex.apexVolleyShare < 0.9f) broken += "$label fired ${apex.apexVolleyShare} of the volleys its Apex could cost " +
                "(${apex.apexMissedVolleys} held back by Apex shots, ${apex.volleyShare} of all scheduled)"
        }
        assertTrue(broken.isEmpty(), "Apex contract: $broken")
    }

    /**
     * The no-POI bot casts its Apex in at least [MIN_CAST_SHARE] of the fights of [CAST_FIGHTS_PER_CASE] runs per
     * character and tier along [PopulationSeeds] (312 fights). A population rule (lead Addendum C5, 0.5.0 active
     * items): the contract once required a cast in every Apex arm of its 72 fights, and the 05885fa9 base left 3 of 1,816
     * fights without a cast (the first 50 to 128 runs per character and tier along [PopulationSeeds]; Orb tier 10 once,
     * Dipole tier 10 twice), so the 72 fights passed with probability 0.83. The bound is the base share 0.9983 minus two
     * standard errors at 312 fights (0.0023), rounded down; the base's own first 13 runs per case all cast.
     */
    @Test
    fun theBotCastsItsApexInNearlyEveryArchitectFight() {
        // The cases look for their runs at once, so the fight pool never waits on one case's slowest batch.
        val cases = CoreShape.entries.flatMap { shape -> listOf(0, 10).map { tier -> shape to tier } }
        val fights = populationOf(cases) { (shape, tier) ->
            firstRuns(shape, tier, CAST_FIGHTS_PER_CASE, PopulationSeeds) { _, state, bot ->
                fight(state.copyForReduction(), bot.copy(), apex = true, windows = emptyList()).casts
            }.map { (seed, casts) -> Triple("$shape tier $tier", seed, casts) }
        }.flatten()
        val silent = fights.filter { it.third == 0 }
        val share = fights.shareOf { it.third > 0 }
        println("APEX-CASTS fights=${fights.size} cast=${"%.4f".format(share)} without a cast: " +
            silent.joinToString { (case, seed) -> "$case seed $seed" })
        assertTrue(share >= MIN_CAST_SHARE, "the bot cast its Apex in only ${"%.4f".format(share)} of ${fights.size} fights: " +
            silent.joinToString { (case, seed) -> "$case seed $seed" })
    }

    @Test
    fun anActivationNeverLowersTheCharactersDamageToTheArchitect() {
        val lowered = mutableListOf<String>()
        for (tier in listOf(0, 10)) for (shape in CoreShape.entries) {
            val apex = arena(shape, tier, apex = true)
            val plain = arena(shape, tier, apex = false)
            println("APEX-ARENA $shape tier=$tier mechanic=${"%.1f".format(apex.mechanic)} (strikes ${"%.1f".format(apex.strikes)}, " +
                "base ${"%.1f".format(apex.mechanic - apex.strikes)}) without=${"%.1f".format(plain.mechanic)}")
            if (apex.mechanic < plain.mechanic) lowered += "$shape tier $tier ${apex.mechanic} below ${plain.mechanic}"
        }
        assertTrue(lowered.isEmpty(), "an Apex lowered the damage its character deals the Architect: $lowered")
    }

    @Test
    fun theApexShortensOffensiveFightsAndShieldsTheDefensiveCharactersWithinItsBand() {
        val outside = mutableListOf<String>()
        val pairs = OFFENSIVE.associateWith { shape -> benefitPairs(shape, 0) }
        val ratios = pairs.mapValues { (_, list) -> median(list.map { it.apexKill / it.plainKill }) }
        val median = median(ratios.values.toList())
        val benefit = 1f - median
        println("APEX-BENEFIT tier=0 pairs=${pairs.values.sumOf { it.size }} median=${"%.3f".format(median)} " +
            ratios.entries.joinToString { (shape, ratio) ->
                val list = pairs.getValue(shape)
                "$shape=${"%.3f".format(ratio)} (medians ${"%.3f".format(median(list.map { it.apexKill }) / median(list.map { it.plainKill }))})"
            })
        if (median !in 0.75f..0.95f) outside += "median time-to-kill ratio $median"
        ratios.forEach { (shape, ratio) ->
            val own = 1f - ratio
            if (own !in min(benefit * 0.6f, benefit * 1.4f)..max(benefit * 0.6f, benefit * 1.4f)) {
                outside += "$shape benefit $own (median $benefit)"
            }
        }
        for (shape in DEFENSIVE) {
            val list = benefitPairs(shape, DEFENSIVE_TIER)
            val taken = list.sumOf { it.apexWindowDamage }
            val without = list.sumOf { it.plainWindowDamage }
            println("APEX-DEFENSE $shape tier=$DEFENSIVE_TIER pairs=${list.size} taken=${"%.0f".format(taken)} " +
                "without=${"%.0f".format(without)} ratio=${"%.3f".format(taken / without)}")
            if (!(without > 0.0) || (taken / without).toFloat() !in 0.55f..0.85f) outside += "$shape took $taken against $without"
        }
        assertTrue(outside.isEmpty(), "Apex benefit: $outside")
    }

    /** What one arm of a fight measured. Times are seconds since the Architect arrived. */
    private class Fight(
        val killSeconds: Float,
        val casts: Int,
        val strikeBudget: Int,
        val boltBudget: Int,
        val maxStrikes: Int,
        val maxErasures: Int,
        val minBoltsLeft: Float,
        val strikeExcess: Float,
        val shiftingLoss: Float,
        val shiftLengths: List<Float>,
        val shiftEscorts: List<Int>,
        /** Volleys fired as a share of the volleys the Architect's attack timer scheduled while it was open. */
        val volleyShare: Float,
        /** Scheduled volleys held back only by live Apex shots filling the projectile pool. */
        val apexMissedVolleys: Int,
        /** Volleys fired as a share of those fired and those the Apex held back. */
        val apexVolleyShare: Float,
        /** Apex windows (start, end) of this arm. */
        val windows: List<Pair<Float, Float>>,
        /** Damage the Core took (shield and integrity) inside the windows this arm was measured over. */
        val windowDamage: Double,
        val apexBossDamage: Double,
    ) {
        fun line(): String = "kill=${"%.1f".format(killSeconds)}s casts=$casts strikes≤$maxStrikes/$strikeBudget " +
            "erased≤$maxErasures/$boltBudget volleys=${"%.3f".format(volleyShare)} apexHeldBack=$apexMissedVolleys shifts=${shiftLengths.size} " +
            "escorts=$shiftEscorts apexBoss=${"%.0f".format(apexBossDamage)}"
    }

    private class ArenaOutcome(val mechanic: Float, val strikes: Float)

    /** One forked pair of the benefit: both arms' time-to-kill and the damage each took inside the Apex arm's windows. */
    private class BenefitPair(val plainKill: Float, val apexKill: Float, val apexWindowDamage: Double, val plainWindowDamage: Double)

    private companion object {
        /**
         * The seed list (the Rebirth gates' sequence, i · 7,919 + 13 from i = 6): the contract uses each case's first
         * three runs that reach the Architect, the Prism's benefit its first [DEFENSIVE_RUNS].
         */
        val SEED_POOL = (6 until 6 + SEED_POOL_SIZE).map { it * 7_919 + 13 }
        const val SEED_POOL_SIZE = 200
        const val FIGHTS_PER_CASE = 3
        /** The cast rule's runs per character and tier along [PopulationSeeds]. */
        const val CAST_FIGHTS_PER_CASE = 13
        /** The least share of those fights in which the bot casts its Apex (base 0.9983, SE 0.0023 at 312 fights). */
        const val MIN_CAST_SHARE = 0.99
        /** The offence's runs per character along [PopulationSeeds] (Addendum D5: the base's population). */
        const val BENEFIT_RUNS = 96
        /**
         * The Prism's runs along [SEED_POOL]: a defensive character's damage comes in rarer, larger events than a
         * time-to-kill moves.
         */
        const val DEFENSIVE_RUNS = 64
        /** The Diamond's runs along [PopulationSeeds] (Addendum D5: the base's population). */
        const val DIAMOND_RUNS = 128
        /** Pairs per benefit run; the k-th pair's arms both start with the Core moved by k × [FORK_OFFSET]. */
        const val FORKS = 4
        const val FORK_OFFSET = 0.01f
        const val DEFENSIVE_TIER = 10
        /** Runs approached at once while looking for a case's next runs that reach the Architect. */
        const val SEED_BATCH = 8
        const val FIGHT_SECONDS = 180f
        const val FRAME = 0.02f
        const val ARENA_ARCHITECT_INTEGRITY = 13_500f
        /** Frames after a shift's start in which its escorts are counted (they arrive in the step that resolves it). */
        const val ESCORT_FRAMES = 5
        val DEFENSIVE = listOf(CoreShape.PRISM, CoreShape.DIAMOND)
        val OFFENSIVE = CoreShape.entries - DEFENSIVE.toSet()
        val BotWeapons: List<WeaponId> = listOf(WeaponId.ARC_COIL, WeaponId.ION_SWARM, WeaponId.ENTROPY_FIELD, WeaponId.PRISM_RELAY)
        val StrongRelics = listOf(RelicId.STORMHEART_CAPACITOR, RelicId.PANDEMIC_CODEX, RelicId.THUNDER_CHOIR, RelicId.CINDER_CROWN)

        /**
         * The fights run on a small pool: each run and its arms are independent and deterministic, and every case
         * keeps its runs in seed order, so the outcome does not depend on scheduling.
         */
        val pool: ExecutorService by lazy {
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors().coerceIn(1, 8)) { task ->
                Thread(task, "apex-fight").apply { isDaemon = true }
            }
        }

        /**
         * [work] on the first [count] runs of [shape] at [tier] along [seeds] that reach the Architect, in seed order
         * (runs are approached [SEED_BATCH] at a time); fails when the seed list runs out first.
         */
        fun <T> firstRuns(
            shape: CoreShape, tier: Int, count: Int, seeds: List<Int> = SEED_POOL, work: (Int, MutableGameState, NoPoiBot) -> T,
        ): List<Pair<Int, T>> {
            val found = mutableListOf<Pair<Int, T>>()
            var next = 0
            while (found.size < count) {
                check(next < seeds.size) { "$shape tier $tier: only ${found.size} of $count runs reached the Architect" }
                val batch = seeds.subList(next, min(next + SEED_BATCH, seeds.size))
                next += batch.size
                val results = batch.map { seed ->
                    pool.submit(Callable { approach(shape, tier, seed)?.let { (state, bot) -> seed to work(seed, state, bot) } })
                }.map { it.get() }
                results.filterNotNull().take(count - found.size).forEach { found += it }
            }
            return found
        }

        /** Both arms of every contract fight (character, tier, seed), played once per test JVM. */
        val fights: Map<Triple<CoreShape, Int, Int>, Pair<Fight, Fight>> by lazy {
            val played = linkedMapOf<Triple<CoreShape, Int, Int>, Pair<Fight, Fight>>()
            for (shape in CoreShape.entries) for (tier in listOf(0, 10)) {
                firstRuns(shape, tier, FIGHTS_PER_CASE) { _, state, bot ->
                    val apex = fight(state.copyForReduction(), bot.copy(), apex = true, windows = emptyList())
                    apex to fight(state.copyForReduction(), bot.copy(), apex = false, windows = apex.windows)
                }.forEach { (seed, pair) -> played[Triple(shape, tier, seed)] = pair }
            }
            played
        }

        fun fightsOf(shape: CoreShape, tier: Int): List<Pair<Fight, Fight>> =
            fights.filterKeys { it.first == shape && it.second == tier }.values.toList()

        private val benefitCache = HashMap<Pair<CoreShape, Int>, List<BenefitPair>>()

        /**
         * The benefit's pairs of [shape] at [tier], each run forked into [FORKS] pairs: an offensive character's first
         * [BENEFIT_RUNS] runs and the Diamond's first [DIAMOND_RUNS] along [PopulationSeeds], the Prism's first
         * [DEFENSIVE_RUNS] along [SEED_POOL].
         */
        fun benefitPairs(shape: CoreShape, tier: Int): List<BenefitPair> = benefitCache.getOrPut(shape to tier) {
            val (runs, seeds) = when (shape) {
                CoreShape.PRISM -> DEFENSIVE_RUNS to SEED_POOL
                CoreShape.DIAMOND -> DIAMOND_RUNS to PopulationSeeds
                else -> BENEFIT_RUNS to PopulationSeeds
            }
            firstRuns(shape, tier, runs, seeds) { _, state, bot ->
                (0 until FORKS).map { fork ->
                    val apex = fight(state.forked(fork), bot.copy(), apex = true, windows = emptyList())
                    val plain = fight(state.forked(fork), bot.copy(), apex = false, windows = apex.windows)
                    BenefitPair(plain.killSeconds, apex.killSeconds, apex.windowDamage, plain.windowDamage)
                }
            }.flatMap { it.second }
        }

        /** An independent copy of the run at the Architect's arrival, its Core moved by [fork] × [FORK_OFFSET]. */
        fun MutableGameState.forked(fork: Int): MutableGameState = copyForReduction().also { it.coreX += fork * FORK_OFFSET }

        fun median(values: List<Float>): Float {
            val sorted = values.sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2f else sorted[middle]
        }

        /** The run up to the Architect's arrival, its integrity refilled; null when it never arrives. */
        fun approach(shape: CoreShape, tier: Int, seed: Int): Pair<MutableGameState, NoPoiBot>? {
            val content = canonicalGameplayContent
            val strong = tier > 0
            val state = MutableGameState(
                content,
                seed = seed,
                bootstrapProgress = GameplayProfileSnapshot(
                    preferences = PlayerPreferences(simulationSpeed = 1f),
                    economy = PlayerEconomy(),
                    loadout = PlayerLoadout(shape, if (strong) WeaponId.ARC_COIL else WeaponId.FLUX_WAKE, ReferenceArmory.toSet()),
                    matrix = MatrixProgress(content.matrixNodes.map { if (strong) it.maxRanks else 0 }),
                    collection = PlayerCollection(),
                    rebirthProgress = RebirthProgress(level = tier, highestCleared = tier - 1),
                ),
            )
            state.unlockWeaponsForTesting(ReferenceArmory)
            state.startRun()
            if (strong) StrongRelics.forEach(state::acquireRelicForTesting)
            val bot = NoPoiBot(archetypeTag = BuildTag.RAM, weaponOrder = BotWeapons)
            while (state.elapsed < 900f && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
                state.invulnerableUntil = Float.MAX_VALUE
                if (state.phase == GamePhase.CHOICE) bot.choose(state) else {
                    bot.control(state)
                    state.update(FRAME)
                }
                state.hp = state.maxHp
                drain(state)
                if (state.liveArchitect() != null) {
                    state.invulnerableUntil = 0f
                    return state to bot
                }
            }
            println("APEX-FIGHT $shape tier=$tier seed=$seed reached no Architect: ${state.phase} ${state.message} at ${state.elapsed}")
            return null
        }

        /**
         * 180 s of the fight from the fork, the level forced to the cap. With [apex] the Apex unlocks ready and the bot
         * casts it by its rule. [windows] are the activations of the Apex arm (kept for the other arm).
         */
        fun fight(state: MutableGameState, bot: NoPoiBot, apex: Boolean, windows: List<Pair<Float, Float>>): Fight {
            val start = state.elapsed
            state.level = state.content.tempo.maximumLevel
            if (apex) state.unlockApex()
            val definition = state.apexDefinition
            val ownWindows = mutableListOf<Pair<Float, Float>>()
            val measured = if (apex) ownWindows else windows
            var windowDamage = 0.0
            var killSeconds = FIGHT_SECONDS
            var casts = 0
            var maxStrikes = 0
            var maxErasures = 0
            var minBolts = Float.MAX_VALUE
            var strikeExcess = 0f
            var shiftingLoss = 0f
            val shiftLengths = mutableListOf<Float>()
            val shiftEscorts = mutableListOf<Int>()
            var volleys = 0
            var scheduled = 0
            var apexMissed = 0
            // Escorts of the latest shift: enemies newer than the shift's frame, seen within the next few frames.
            var escortMark = Int.MAX_VALUE
            var escortFrames = 0
            val escortIds = HashSet<Int>()
            while (state.elapsed < start + FIGHT_SECONDS && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
                if (state.phase == GamePhase.CHOICE) {
                    bot.choose(state)
                    continue
                }
                val boss = state.liveArchitect() ?: break
                val runtime = state.skillRuntime
                val castsBefore = runtime.apex(ApexCell.CAST_COUNT).toInt()
                val strikesBefore = runtime.apex(ApexCell.STRIKES_LEFT)
                val bossDamageBefore = runtime.apexBossDamage
                val hpBefore = boss.hp
                val takenBefore = state.damageTaken + state.damageAbsorbed
                val shiftingBefore = state.isShifting(boss)
                val phaseBefore = boss.phase
                val shiftBefore = boss.shiftUntil
                val timerBefore = boss.actionTimer
                val newestBefore = state.enemies.maxOfOrNull { it.id } ?: -1
                // Every strike is held to its target's hit cap; a Herald's never exceeds the Architect's share.
                val cap = max(state.architectHitCap(boss), heraldCap(state))
                bot.control(state)
                state.update(FRAME)
                val now = state.elapsed - start
                val castsNow = runtime.apex(ApexCell.CAST_COUNT).toInt()
                if (castsNow > castsBefore) {
                    casts += castsNow - castsBefore
                    ownWindows += now to now + definition.durationSeconds
                }
                val strikesLeft = runtime.apex(ApexCell.STRIKES_LEFT)
                val strikesSpent = if (castsNow > castsBefore) definition.bossStrikes - strikesLeft else strikesBefore - strikesLeft
                maxStrikes = max(maxStrikes, (definition.bossStrikes - strikesLeft).toInt())
                maxErasures = max(maxErasures, (definition.boltBudget - runtime.apex(ApexCell.BOLTS_LEFT)).toInt())
                if (casts > 0) minBolts = min(minBolts, runtime.apex(ApexCell.BOLTS_LEFT))
                val struck = (runtime.apexBossDamage - bossDamageBefore).toFloat()
                if (strikesSpent > 0f) strikeExcess = max(strikeExcess, struck / strikesSpent - cap)
                val removed = if (boss.alive) hpBefore - boss.hp else hpBefore
                if (shiftingBefore && state.isShifting(boss)) shiftingLoss = max(shiftingLoss, removed)
                if (boss.shiftUntil != shiftBefore) {
                    // Only a phase gate starts a shift; it ends 8 s after the moment inside this frame it started.
                    shiftLengths += if (boss.phase > phaseBefore) boss.shiftUntil - state.elapsed else -1f
                    if (escortFrames > 0) shiftEscorts += escortIds.size
                    escortMark = newestBefore
                    escortFrames = ESCORT_FRAMES
                    escortIds.clear()
                }
                if (escortFrames > 0) {
                    state.enemies.forEach { if (it.id > escortMark) escortIds += it.id }
                    if (--escortFrames == 0) shiftEscorts += escortIds.size
                }
                // A volley is due when the attack timer resets (the Apex never writes it); it fires unless the boss
                // cannot attack or the projectile pool is at the volley budget. The Apex held it back only when its
                // own shots filled the pool.
                if (!shiftingBefore && !state.isShifting(boss) && boss.actionTimer > timerBefore) {
                    scheduled++
                    val live = state.projectiles.size
                    if (state.canAttack(boss) && live < ARCHITECT_VOLLEY_PROJECTILE_BUDGET) volleys++
                    if (state.canAttack(boss) && live >= ARCHITECT_VOLLEY_PROJECTILE_BUDGET &&
                        live - state.projectiles.count { !it.hostile && it.procKey == WeaponRuntime.PROC_APEX } < ARCHITECT_VOLLEY_PROJECTILE_BUDGET
                    ) apexMissed++
                }
                // Damage taken, not the drop in integrity and shield: refilling integrity every frame turns each heal
                // into overheal, and an overfilled shield decays back without anything hitting the Core.
                if (measured.any { (from, to) -> now > from && now <= to }) {
                    windowDamage += state.damageTaken + state.damageAbsorbed - takenBefore
                }
                state.hp = state.maxHp
                drain(state)
                if (!boss.alive || state.phase == GamePhase.VICTORY) {
                    killSeconds = now
                    break
                }
            }
            if (escortFrames > 0) shiftEscorts += escortIds.size
            return Fight(
                killSeconds = killSeconds,
                casts = casts,
                strikeBudget = definition.bossStrikes,
                boltBudget = definition.boltBudget,
                maxStrikes = maxStrikes,
                maxErasures = maxErasures,
                minBoltsLeft = if (minBolts == Float.MAX_VALUE) 0f else minBolts,
                strikeExcess = strikeExcess,
                shiftingLoss = shiftingLoss,
                shiftLengths = shiftLengths,
                shiftEscorts = shiftEscorts,
                volleyShare = if (scheduled > 0) volleys / scheduled.toFloat() else 1f,
                apexMissedVolleys = apexMissed,
                apexVolleyShare = if (volleys + apexMissed > 0) volleys / (volleys + apexMissed).toFloat() else 1f,
                windows = measured,
                windowDamage = windowDamage,
                apexBossDamage = state.skillRuntime.apexBossDamage,
            )
        }

        /** The largest hit cap of a live Herald (0 for none). */
        fun heraldCap(state: MutableGameState): Float =
            state.enemies.filter { it.type == EnemyType.HERALD && it.alive }.maxOfOrNull { state.architectHitCap(it) } ?: 0f

        /**
         * One activation's length of a scripted arena at the fight's moment (720 s): the Architect alone, at its last
         * phase so no gate or shift interrupts it, its integrity refilled; the pointer traces a figure eight, a Dash
         * fires every 1.5 s and a short brake follows each. With [apex] the Apex casts on the first frame. Returns
         * the character's damage to the Architect over the activation (strikes included) and the strikes' share.
         */
        fun arena(shape: CoreShape, tier: Int, apex: Boolean): ArenaOutcome {
            val content = canonicalGameplayContent.copy(tempo = canonicalGameplayContent.tempo.copy(bossAtSeconds = 1_000_000f))
            val state = MutableGameState(content, seed = 4_242, initialRebirthLevel = tier)
            state.applyPreferences(state.settings.copy(simulationSpeed = 1f))
            state.resize(1_280f, 720f)
            state.coreShape = shape
            state.startRun()
            state.enemies.clear()
            state.nextEliteAt = Float.MAX_VALUE
            state.nextLevelData = Int.MAX_VALUE
            state.elapsed = 720f
            state.bossSpawned = true
            // The integrity the Architect arrives with at 720 s of a run (its tier-0 hit cap is then ≈ 203).
            val boss = state.addEnemyForTesting(state.coreX + 340f, state.coreY,
                hp = ARENA_ARCHITECT_INTEGRITY * state.rebirthProfile.enemyHealthMultiplier, radius = 74f, type = EnemyType.ARCHITECT)
            boss.phase = state.architectFinalPhase()
            if (apex) {
                state.level = content.tempo.maximumLevel
                state.unlockApex()
                state.requestApex()
            }
            val duration = state.apexDefinition.durationSeconds
            val startAbility = state.characterRuntime.abilityDamage
            var frame = 0
            val begin = state.elapsed
            while (state.elapsed < begin + duration && state.phase == GamePhase.RUNNING) {
                state.spawnBudget = 0f
                state.runGrace = 1f
                state.pointsOfInterest = emptyList()
                state.enemies.removeAll { !it.isBoss }
                val angle = frame / 60f * 1.4f
                state.updatePointer(state.screenWidth * 0.5f + cos(angle) * 300f, state.screenHeight * 0.5f + sin(angle * 2f) * 220f)
                if (frame % 90 == 45) state.requestDash()
                state.setBrake(frame % 90 in 60..70)
                state.update(1f / 60f)
                boss.hp = boss.maxHp
                state.hp = state.maxHp
                drain(state)
                frame++
            }
            val mechanic = (state.characterRuntime.abilityDamage - startAbility).toFloat()
            return ArenaOutcome(mechanic, state.skillRuntime.apexBossDamage.toFloat())
        }

        fun drain(state: MutableGameState) {
            state.takeSoundCues()
            state.takeVisualFxCues()
            state.takeProgressUpdate()
        }
    }
}
