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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The fights of the Apex against the Architect (combat-final §5.2, §10 "Apex"), shared by [ApexArchitectFightTest]
 * (the contract), [ApexArchitectCastTest] (the cast rule), [ApexArchitectOffenceTest] (the offence band) and
 * [ApexArchitectPrismDefenceTest] and [ApexArchitectDiamondDefenceTest] (the defensive bands). They are separate
 * classes only so that test forks and CI shards can run them side by side; each computes its own fights, as the
 * methods of the one class did.
 *
 * Fights: per character, tier (0 fresh; 10 with the strong build's Matrix, Arc Coil and Relics) and seed, the
 * no-POI bot plays the run to the Architect's arrival (the Core cannot be hurt on the way; a singularity contact can
 * still end a run, so a case takes the first runs of a fixed seed list that reach the Architect); the run then forks
 * into two arms that play the next 180 s with copies of the same bot, both with the level forced to the cap (so
 * neither keeps drafting): one unlocks the Apex (the bot casts it by its rule), one does not. In the fight the Core's
 * integrity is refilled every frame and the damage it takes (shield and integrity) is counted.
 */
internal object ApexArchitectFightHarness {
    /** What one arm of a fight measured. Times are seconds since the Architect arrived. */
    class Fight(
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

    class ArenaOutcome(val mechanic: Float, val strikes: Float)

    /** One forked pair of the benefit: both arms' time-to-kill and the damage each took inside the Apex arm's windows. */
    class BenefitPair(val plainKill: Float, val apexKill: Float, val apexWindowDamage: Double, val plainWindowDamage: Double)

    /**
     * The seed list (the Rebirth gates' sequence, i · 7,919 + 13 from i = 6): the contract uses each case's first
     * three runs that reach the Architect.
     */
    val SEED_POOL = (6 until 6 + SEED_POOL_SIZE).map { it * 7_919 + 13 }
    const val SEED_POOL_SIZE = 200
    const val FIGHTS_PER_CASE = 3
    /** The offence's runs per character along [PopulationSeeds] (the population the base was measured on). */
    const val BENEFIT_RUNS = 96
    /**
     * The Prism's and the Diamond's runs along [PopulationSeeds] (the population the base was measured on): a
     * defensive character's damage comes in rarer, larger events than a time-to-kill moves.
     */
    const val DEFENSIVE_RUNS = 128
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

    private val benefitCache = ConcurrentHashMap<Pair<CoreShape, Int>, List<BenefitPair>>()

    /**
     * The benefit's pairs of [shape] at [tier], each run forked into [FORKS] pairs: the first [BENEFIT_RUNS] runs
     * of an offensive character, or [DEFENSIVE_RUNS] of a defensive one, along [PopulationSeeds].
     */
    fun benefitPairs(shape: CoreShape, tier: Int): List<BenefitPair> = benefitCache.getOrPut(shape to tier) {
        val runs = if (shape in DEFENSIVE) DEFENSIVE_RUNS else BENEFIT_RUNS
        firstRuns(shape, tier, runs, PopulationSeeds) { _, state, bot ->
            (0 until FORKS).map { fork ->
                val apex = fight(state.forked(fork), bot.copy(), apex = true, windows = emptyList())
                val plain = fight(state.forked(fork), bot.copy(), apex = false, windows = apex.windows)
                BenefitPair(plain.killSeconds, apex.killSeconds, apex.windowDamage, plain.windowDamage)
            }
        }.flatMap { it.second }
    }

    /**
     * The defensive band of [shape] (the Prism or the Diamond): tier 10, where the Architect's fire and the arena put
     * real damage on them (at tier 0 their facets and parries leave almost nothing to defend), the first
     * [DEFENSIVE_RUNS] runs along [PopulationSeeds], each forked into [FORKS] pairs: the damage taken during the Apex
     * arm's windows, summed over the pairs, is [0.55, 0.85] of what the no-Apex arm takes in the same windows. Prints
     * the pairs and returns the breach, or null.
     *
     * Both bands are population rules (0.5.0 active items; policy.md, "Balance gate populations"). Their populations
     * are the ones the base was measured on, 128 runs per character with four forks each: fewer than 300 runs, since
     * these fights already set the suite's runtime. By the population threshold rule (the wider of the design's band
     * and the base ± two standard errors, rounded outward) both bands stay the design's, [0.55, 0.85].
     *
     * In 0.5.0, 128 runs did not settle the defensive bands either: a fresh 128-run draw (four forks each) from the 256
     * calibration-window runs landed in [0.55, 0.85] with probability about 0.82 for the Prism (window ratio 0.814) and
     * 0.84 for the Diamond (0.589); 0.6.0's single Mirror Stance parry moved the Diamond's pooled window reading to
     * 0.701. baseline.md records this as a trade-off for the owner, not a reason to restate again.
     */
    fun defensiveBandBreach(shape: CoreShape): String? {
        val list = benefitPairs(shape, DEFENSIVE_TIER)
        val taken = list.sumOf { it.apexWindowDamage }
        val without = list.sumOf { it.plainWindowDamage }
        println("APEX-DEFENSE $shape tier=$DEFENSIVE_TIER pairs=${list.size} taken=${"%.0f".format(taken)} " +
            "without=${"%.0f".format(without)} ratio=${"%.3f".format(taken / without)}")
        return if (!(without > 0.0) || (taken / without).toFloat() !in 0.55f..0.85f) "$shape took $taken against $without" else null
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
            state.quietArenaForTesting()
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
