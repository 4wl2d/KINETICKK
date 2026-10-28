// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.StructureField
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Mini-boss and secret-boss duels (ENEMIES spec §14.4): each boss's own encounter, measured in a quiet arena.
 *
 * A run is the no-POI bot's own run to [ARRIVAL_SECONDS] (the Core cannot be hurt on the way, but a singularity
 * contact or a lethal rock can still end it; such a run joins no duel and is reported). The run then forks once per
 * boss of its arm: the arena falls quiet (no sites, signals, world events, ambient spawns, Elites or Heralds, no rocks,
 * structures or pickups left from the run, only the encounter's own enemies), the Core's integrity is refilled once,
 * and `spawnEncounter` places the boss alone [SPAWN_DISTANCE] u ahead of the Core with its content integrity. The duel
 * pilot engages it through the production steering inverse: it orbits [ORBIT_RADIUS] u around the boss and makes a
 * ram pass every [RAM_PERIOD] s, waiting for a gap while a guarded boss (the Throne, the Antecedent's arcs) faces it,
 * and it sidesteps hostile bolts as the no-POI bot does; the bot's own skill casts, threat Dash and choices play on.
 * A duel ends when the boss dies (its kill time), the Core dies, or after [DUEL_SECONDS].
 *
 * Arms: the fresh tier-0 profile (Gate A's Orb with the starter weapon) and the strong build at tier 10 (Gate D's
 * Pentagon with Arc Coil, Stormheart Capacitor and three proven Relics, every Matrix rank) meet the four mini-bosses;
 * the strong build at tier 3 meets the two secret bosses. A duel's time is its kill time, or infinite when the boss
 * survives it, so a median counts every duel.
 *
 * Design targets: fresh tier 0, each mini-boss's median kill time in [25 s, 90 s] with at most 0.30 of its duels
 * ending in the Core's death, and the four medians within ±40 % of their mean; strong tier 10, each mini-boss's median
 * in [20 s, 120 s]; strong tier 3, each secret boss's median in [60 s, 180 s] with at most 0.50 deaths. The knobs are
 * the bosses' integrity and hit-cap share (plan §8.3), calibrated on the ENEMIES window i = 1,301..1,600 (seed
 * `i * 7_919 + 13`); the gate plays the first [DUEL_RUNS] of [PopulationSeeds], and at least [MIN_ARENA_SHARE] of
 * an arm's runs must reach the arena. Each test states its base on those runs and on the window; every threshold is
 * the design's, since each base keeps two standard errors inside it (the population threshold rule).
 *
 * `KK_DUEL_WINDOW=first..last` plays that calibration window instead (never the gate population) and allows the probe
 * overrides `KK_DUEL_INTEGRITY=TYPE=share,...` (each boss's spawn integrity share) and `KK_DUEL_HITCAP=TYPE=share,...`
 * (its encounter hit-cap share). Every arm prints one line per duel and a summary with bootstrap standard errors
 * (20,000 resamples of the runs, RNG seed 20261027).
 */
class EncounterDuelGateTest {
    /**
     * Base (149 of 150 runs; one ended on a rock at 233 s): median kill times Warden Prime 30.68 s (SE 0.71), Brood
     * Queen 31.85 (1.30), Throne 43.89 (1.26), Pyre Herald 32.89 (1.21); deaths 0.020 (0.011), 0, 0, 0.154 (0.030); the
     * widest median 0.260 (0.021) from the four's mean. Window (300 runs): 29.06 (0.73), 33.59 (0.70), 42.42 (0.84),
     * 31.64 (0.69); deaths 0.017, 0, 0, 0.140 (0.020); spread 0.241 (0.014).
     */
    @Test
    fun aFreshTierZeroBuildDuelsEachMiniBossWithinTheDesignBand() {
        val summary = summaryOf(Arm.FRESH_TIER_0)
        val outside = summary.thinRuns()
        for (boss in Arm.FRESH_TIER_0.bosses) {
            val median = summary.median(boss)
            if (median !in FRESH_MIN_MEDIAN..FRESH_MAX_MEDIAN) outside += "$boss median ${median.format()} s"
            val deaths = summary.deathShare(boss)
            if (deaths > FRESH_MAX_DEATH_SHARE) outside += "$boss death share ${deaths.format()}"
        }
        val medians = Arm.FRESH_TIER_0.bosses.map(summary::median)
        val mean = medians.average()
        Arm.FRESH_TIER_0.bosses.zip(medians).forEach { (boss, median) ->
            if (median !in mean * (1.0 - FRESH_SPREAD)..mean * (1.0 + FRESH_SPREAD)) {
                outside += "$boss median ${median.format()} s against the four's mean ${mean.format()} s"
            }
        }
        assertTrue(outside.isEmpty(), "fresh tier-0 mini-boss duels: $outside")
    }

    /**
     * Base (147 of 150 runs; three singularity contacts on the way): Warden Prime 80.26 s (SE 2.15), Brood Queen 75.07
     * (1.16), Throne 100.63 (2.32), Pyre Herald 100.36 (5.62). The Pyre Herald kills the Core in 0.435 (0.041) of its
     * duels, so 0.055 of the resampled medians are infinite. Window (282 runs): 79.15 (1.64), 75.86 (0.96), 101.06
     * (1.96), 95.03 (3.95), the Pyre Herald's deaths 0.401 (0.029) with no infinite resample.
     */
    @Test
    fun aStrongTierTenBuildDuelsEachMiniBossWithinTheDesignBand() {
        val summary = summaryOf(Arm.STRONG_TIER_10)
        val outside = summary.thinRuns()
        for (boss in Arm.STRONG_TIER_10.bosses) {
            val median = summary.median(boss)
            if (median !in STRONG_MIN_MEDIAN..STRONG_MAX_MEDIAN) outside += "$boss median ${median.format()} s"
        }
        assertTrue(outside.isEmpty(), "strong tier-10 mini-boss duels: $outside")
    }

    /**
     * Base (150 runs): Cartographer 71.03 s (SE 0.87), Antecedent 87.32 (1.43); deaths 0 and 0.013 (0.009). Window
     * (300 runs): 72.16 (0.84), 87.80 (0.86); deaths 0 and 0.020 (0.008).
     */
    @Test
    fun aStrongTierThreeBuildDuelsEachSecretBossWithinTheDesignBand() {
        val summary = summaryOf(Arm.STRONG_TIER_3)
        val outside = summary.thinRuns()
        for (boss in Arm.STRONG_TIER_3.bosses) {
            val median = summary.median(boss)
            if (median !in SECRET_MIN_MEDIAN..SECRET_MAX_MEDIAN) outside += "$boss median ${median.format()} s"
            val deaths = summary.deathShare(boss)
            if (deaths > SECRET_MAX_DEATH_SHARE) outside += "$boss death share ${deaths.format()}"
        }
        assertTrue(outside.isEmpty(), "strong tier-3 secret-boss duels: $outside")
    }

    /** A profile played to the arena and the bosses it meets there. */
    private enum class Arm(val label: String, val tier: Int, val strong: Boolean, val bosses: List<EnemyType>) {
        FRESH_TIER_0("fresh-t0", 0, strong = false, MiniBosses),
        STRONG_TIER_10("strong-t10", 10, strong = true, MiniBosses),
        STRONG_TIER_3("strong-t3", 3, strong = true, SecretBosses),
    }

    private enum class Outcome { KILLED, DIED, TIMED_OUT, LOST }

    /** One duel: how it ended, after how many seconds, and what the Core and the pilot did in it. */
    private class Duel(
        val seed: Int,
        val boss: EnemyType,
        val outcome: Outcome,
        val seconds: Float,
        val integrityLeft: Float,
        val rams: Int,
        val contacts: Int,
        val level: Int,
        val weapon: WeaponId,
    ) {
        /** The kill time, or infinite when the boss survived the duel. */
        val killTime: Double get() = if (outcome == Outcome.KILLED) seconds.toDouble() else Double.POSITIVE_INFINITY

        override fun toString(): String =
            "seed=$seed boss=$boss outcome=$outcome seconds=${"%.2f".format(seconds)} " +
                "integrityLeft=${"%.1f".format(integrityLeft)} rams=$rams contacts=$contacts level=$level weapon=$weapon"
    }

    /** Every duel of an arm, grouped by the run it forked from (runs that never reached the arena are left out). */
    private class ArmSummary(val arm: Arm, val runs: List<List<Duel>>) {
        fun duels(boss: EnemyType): List<Duel> = runs.mapNotNull { run -> run.firstOrNull { it.boss == boss } }
        fun median(boss: EnemyType): Double = medianKillTime(duels(boss))
        fun deathShare(boss: EnemyType): Double = deathShare(duels(boss))

        /** The finding when too few of the runs reached the arena to judge the arm by (none otherwise). */
        fun thinRuns(): MutableList<String> = if (runs.size >= seeds.size * MIN_ARENA_SHARE) {
            mutableListOf()
        } else {
            mutableListOf("only ${runs.size} of ${seeds.size} runs reached the arena")
        }
    }

    /**
     * The duel pilot: the bot's skill casts, threat Dash and Brake play on (it decides first and its pointer is then
     * replaced), and the pointer steers the Core through the production steering inverse around and through the boss.
     */
    private class DuelPilot(private val bot: NoPoiBot, start: Float) {
        private var nextRamAt = start + RAM_PERIOD
        private var ramStartedAt = Float.NEGATIVE_INFINITY
        private var ramming = false
        var rams = 0
            private set
        var contacts = 0
            private set

        fun control(s: MutableGameState, boss: Enemy) {
            bot.control(s)
            val dx = s.coreX - boss.x
            val dy = s.coreY - boss.y
            val distance = max(1f, sqrt(dx * dx + dy * dy))
            val nx = dx / distance
            val ny = dy / distance
            val contact = distance <= boss.radius + s.coreRadius + RAM_CONTACT_SLACK
            if (ramming && (contact || s.elapsed >= ramStartedAt + RAM_MAX_SECONDS)) {
                if (contact) contacts++
                ramming = false
                nextRamAt = ramStartedAt + RAM_PERIOD
            }
            // A guarded boss is rammed only through a gap: the pass waits while its guard faces the Core.
            if (!ramming && s.elapsed >= nextRamAt && !(boss.type.profile.directionalGuard && boss.guarded)) {
                ramming = true
                ramStartedAt = s.elapsed
                rams++
            }
            var desiredX: Float
            var desiredY: Float
            if (ramming) {
                val tx = boss.x + boss.vx * RAM_LEAD_SECONDS - s.coreX
                val ty = boss.y + boss.vy * RAM_LEAD_SECONDS - s.coreY
                val reach = max(1f, sqrt(tx * tx + ty * ty))
                desiredX = tx / reach * RAM_SPEED
                desiredY = ty / reach * RAM_SPEED
            } else {
                val radial = ((ORBIT_RADIUS - distance) * ORBIT_RADIAL_GAIN).coerceIn(-ORBIT_RADIAL_LIMIT, ORBIT_RADIAL_LIMIT)
                desiredX = -ny * ORBIT_SPEED + nx * radial
                desiredY = nx * ORBIT_SPEED + ny * radial
                // Bolts about to pass through the Core are sidestepped, as the no-POI bot does.
                for (shot in s.projectiles) if (shot.hostile) {
                    val px = shot.x - s.coreX
                    val py = shot.y - s.coreY
                    val vx = shot.vx - s.velocityX
                    val vy = shot.vy - s.velocityY
                    val time = -(px * vx + py * vy) / max(1f, vx * vx + vy * vy)
                    val missX = px + vx * time
                    val missY = py + vy * time
                    if (time in 0f..DODGE_LOOKAHEAD && missX * missX + missY * missY < DODGE_REACH * DODGE_REACH) {
                        val speed = max(1f, sqrt(vx * vx + vy * vy))
                        val side = if (px * -vy + py * vx > 0f) -1f else 1f
                        desiredX += -vy / speed * side * DODGE_SPEED
                        desiredY += vx / speed * side * DODGE_SPEED
                    }
                }
            }
            steer(s, desiredX, desiredY)
            if (ramming) s.setBrake(false)
        }

        /** Points the cursor where the production inverse asks for the acceleration that reaches [desiredX], [desiredY]. */
        private fun steer(s: MutableGameState, desiredX: Float, desiredY: Float) {
            val ax = (desiredX - s.velocityX) * STEER_GAIN + s.velocityX * s.dragCoefficient
            val ay = (desiredY - s.velocityY) * STEER_GAIN + s.velocityY * s.dragCoefficient
            val aim = s.steeringAimFor(ax, ay)
            val ux = cos(aim.heading)
            val uy = sin(aim.heading)
            val offsetX = s.coreX - s.cameraX
            val offsetY = s.coreY - s.cameraY
            // The pointer stays inside the strain-free area, as the no-POI bot keeps it.
            val halfWidth = s.screenWidth * 0.5f * s.content.tempo.fatigue.edgeStrainStart - 10f
            val halfHeight = s.screenHeight * 0.5f * s.content.tempo.fatigue.edgeStrainStart - 10f
            val availableX = if (ux > 0f) (halfWidth - offsetX) / ux else if (ux < 0f) (-halfWidth - offsetX) / ux else 10_000f
            val availableY = if (uy > 0f) (halfHeight - offsetY) / uy else if (uy < 0f) (-halfHeight - offsetY) / uy else 10_000f
            val tether = min(aim.distance.coerceIn(MIN_TETHER, MAX_TETHER), min(availableX, availableY)).coerceAtLeast(MIN_TETHER)
            s.updatePointer(s.screenWidth * 0.5f + offsetX + ux * tether, s.screenHeight * 0.5f + offsetY + uy * tether)
        }
    }

    private companion object {
        val MiniBosses = listOf(EnemyType.WARDEN_PRIME, EnemyType.BROOD_QUEEN, EnemyType.THRONE, EnemyType.PYRE_HERALD)
        val SecretBosses = listOf(EnemyType.CARTOGRAPHER, EnemyType.ANTECEDENT)
        val BotWeapons = listOf(WeaponId.ARC_COIL, WeaponId.ION_SWARM, WeaponId.ENTROPY_FIELD, WeaponId.PRISM_RELAY)
        /** Gate D's strong build: Stormheart Capacitor in key harmony with Arc Coil and three proven Relics. */
        val StrongRelics =
            listOf(RelicId.STORMHEART_CAPACITOR, RelicId.PANDEMIC_CODEX, RelicId.THUNDER_CHOIR, RelicId.CINDER_CROWN)

        /** The gate's runs: the first of [PopulationSeeds]. */
        const val DUEL_RUNS = 150
        /** The least share of an arm's runs that must reach the arena (singularity contacts and rocks end the rest). */
        const val MIN_ARENA_SHARE = 0.9
        const val GATE_FIRST = 1_001
        const val GATE_LAST = 1_300

        const val FRESH_MIN_MEDIAN = 25.0
        const val FRESH_MAX_MEDIAN = 90.0
        const val FRESH_MAX_DEATH_SHARE = 0.30
        const val FRESH_SPREAD = 0.40
        const val STRONG_MIN_MEDIAN = 20.0
        const val STRONG_MAX_MEDIAN = 120.0
        const val SECRET_MIN_MEDIAN = 60.0
        const val SECRET_MAX_MEDIAN = 180.0
        const val SECRET_MAX_DEATH_SHARE = 0.50

        const val ARRIVAL_SECONDS = 300f
        const val DUEL_SECONDS = 300f
        const val FRAME = 0.02f
        const val SPAWN_DISTANCE = 600f
        /** The arena's clamp and leash: the dimension objective's, so the boss moves as it does in a duel of its own. */
        const val ARENA_RADIUS = 2_400f
        const val ARENA_ABANDON_DISTANCE = 6_000f
        const val DUEL_ENCOUNTER_ID = 0x0E8L

        const val ORBIT_RADIUS = 260f
        const val ORBIT_SPEED = 460f
        const val ORBIT_RADIAL_GAIN = 2.4f
        const val ORBIT_RADIAL_LIMIT = 480f
        const val RAM_PERIOD = 3f
        const val RAM_SPEED = 1_100f
        const val RAM_LEAD_SECONDS = 0.2f
        const val RAM_MAX_SECONDS = 1.2f
        const val RAM_CONTACT_SLACK = 6f
        const val DODGE_LOOKAHEAD = 0.45f
        const val DODGE_REACH = 80f
        const val DODGE_SPEED = 380f
        const val STEER_GAIN = 5f
        const val MIN_TETHER = 65f
        const val MAX_TETHER = 400f

        const val BOOTSTRAP_RESAMPLES = 20_000
        const val BOOTSTRAP_SEED = 20_261_027

        /** `KK_DUEL_WINDOW` as indices, or null for the gate population. */
        val window: IntRange? by lazy {
            val text = System.getenv("KK_DUEL_WINDOW")?.takeIf { it.isNotBlank() } ?: return@lazy null
            val (first, last) = text.split("..").map { it.trim().toInt() }
            require(first in 1..last) { "KK_DUEL_WINDOW must be first..last, got $text" }
            require(last < GATE_FIRST || first > GATE_LAST) { "the gate population ($GATE_FIRST..$GATE_LAST) is never probed" }
            first..last
        }

        val seeds: List<Int> by lazy { window?.map { it * 7_919 + 13 } ?: PopulationSeeds.take(DUEL_RUNS) }

        /** Probe overrides: only on a calibration window. */
        fun overrides(name: String): Map<EnemyType, Float> {
            val text = System.getenv(name)?.takeIf { it.isNotBlank() } ?: return emptyMap()
            check(window != null) { "$name probes only a calibration window (KK_DUEL_WINDOW)" }
            return text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.associate { entry ->
                val (type, value) = entry.split('=').map { it.trim() }
                EnemyType.valueOf(type) to value.toFloat()
            }
        }

        val integrityShares: Map<EnemyType, Float> by lazy { overrides("KK_DUEL_INTEGRITY") }

        /** The shipped content, with the probe's hit-cap shares on a calibration window. */
        val content: GameplayContentSnapshot by lazy {
            val shares = overrides("KK_DUEL_HITCAP")
            if (shares.isEmpty()) canonicalGameplayContent else canonicalGameplayContent.copy(
                enemies = canonicalGameplayContent.enemies.map { definition ->
                    val share = shares[definition.type] ?: return@map definition
                    definition.copy(encounter = checkNotNull(definition.encounter).copy(hitCapShare = share))
                }.toImmutableList(),
            )
        }

        private val summaries = HashMap<Arm, ArmSummary>()

        /** Every duel of [arm], played once per test JVM, with its lines and summary printed. */
        @Synchronized
        fun summaryOf(arm: Arm): ArmSummary = summaries.getOrPut(arm) {
            val label = window?.let { "window ${it.first}..${it.last}" } ?: "gate ${seeds.size} runs"
            println("ENCOUNTER-DUEL ${arm.label} $label integrity=$integrityShares hitCap=${content.enemies
                .filter { it.type in arm.bosses }.associate { it.type to checkNotNull(it.encounter).hitCapShare }}")
            val runs = populationOf(seeds) { seed -> duelsOf(arm, seed) }
            runs.forEach { run -> run?.forEach { println("ENCOUNTER-DUEL ${arm.label} $it") } }
            val summary = ArmSummary(arm, runs.filterNotNull())
            for (boss in arm.bosses) println("ENCOUNTER-DUEL SUMMARY ${arm.label} $boss ${describe(summary, boss)}")
            println("ENCOUNTER-DUEL SUMMARY ${arm.label} ${describeSpread(summary)}")
            summary
        }

        /** The run of [seed] to the arena, forked into one duel per boss of [arm]; null when it never got there. */
        fun duelsOf(arm: Arm, seed: Int): List<Duel>? {
            val (state, bot) = approach(arm, seed) ?: return null
            return arm.bosses.map { boss -> duel(state.copyForReduction(), bot.copy(), boss, seed) }
        }

        /**
         * The no-POI bot's run of [arm] to [ARRIVAL_SECONDS], the Core unhurt on the way; null when it ended first or
         * stands in another world layer then (the bot does not avoid rifts yet), with the reason printed.
         */
        fun approach(arm: Arm, seed: Int): Pair<MutableGameState, NoPoiBot>? {
            val profile = if (arm.strong) {
                gateProfile(GateMatrix.FULL, arm.tier, CoreShape.PENTAGON, WeaponId.ARC_COIL, content = content)
            } else {
                gateProfile(GateMatrix.NONE, arm.tier, content = content)
            }
            val state = MutableGameState(content, seed = seed, bootstrapProgress = profile)
            state.unlockWeaponsForTesting(ReferenceArmory)
            state.startRun()
            if (arm.strong) StrongRelics.forEach(state::acquireRelicForTesting)
            val bot = NoPoiBot(archetypeTag = BuildTag.RAM, weaponOrder = BotWeapons)
            while (state.elapsed < ARRIVAL_SECONDS && state.isLive()) {
                state.invulnerableUntil = Float.MAX_VALUE
                if (state.phase == GamePhase.CHOICE) bot.choose(state) else {
                    bot.control(state)
                    state.update(FRAME)
                }
                state.hp = state.maxHp
                drain(state)
            }
            state.invulnerableUntil = 0f
            if (state.phase == GamePhase.RUNNING && state.worldLayer == WorldLayer.BASE) return state to bot
            println("ENCOUNTER-DUEL ${arm.label} seed=$seed reached no arena: ${state.phase} ${state.message} " +
                "at ${state.elapsed} in ${state.worldLayer}")
            return null
        }

        /** The quiet arena's duel with [boss] from the fork [state]. */
        fun duel(state: MutableGameState, bot: NoPoiBot, boss: EnemyType, seed: Int): Duel {
            state.quietArenaForTesting()
            state.spawnClock = Float.MAX_VALUE
            state.nextEliteAt = Float.MAX_VALUE
            state.heraldsSpawned = Int.MAX_VALUE / 2
            state.enemies.clear()
            state.projectiles.clear()
            state.pickups.clear()
            state.keepArenaQuiet(0L)
            state.hp = state.maxHp
            val speed = sqrt(state.velocityX * state.velocityX + state.velocityY * state.velocityY)
            val heading = if (speed > 1f) atan2(state.velocityY, state.velocityX) else 0f
            val x = state.coreX + cos(heading) * SPAWN_DISTANCE
            val y = state.coreY + sin(heading) * SPAWN_DISTANCE
            val tag = encounterTag(EncounterOwner.SITE, DUEL_ENCOUNTER_ID)
            val spawned = state.spawnEncounter(
                EncounterRequest(
                    tag = tag,
                    centerX = x,
                    centerY = y,
                    areaRadius = ARENA_RADIUS,
                    members = listOf(boss),
                    integrityShare = integrityShares[boss] ?: 1f,
                    abandonDistance = ARENA_ABANDON_DISTANCE,
                    positions = floatArrayOf(x, y),
                ),
            )
            check(spawned) { "seed $seed: the arena refused $boss" }
            val target = state.enemies.single { it.id == checkNotNull(state.encounter(tag)).bossId }
            val start = state.elapsed
            val pilot = DuelPilot(bot, start)
            var outcome = Outcome.TIMED_OUT
            while (state.elapsed < start + DUEL_SECONDS) {
                if (!state.isLive()) {
                    outcome = Outcome.DIED
                    break
                }
                if (state.phase == GamePhase.CHOICE) {
                    bot.choose(state)
                    continue
                }
                state.keepArenaQuiet(tag)
                pilot.control(state, target)
                state.update(FRAME)
                drain(state)
                if (target.dead || target.hp <= 0f) {
                    outcome = if (target.hp <= 0f) Outcome.KILLED else Outcome.LOST
                    break
                }
            }
            if (outcome == Outcome.TIMED_OUT && !state.isLive()) outcome = Outcome.DIED
            return Duel(
                seed, boss, outcome, state.elapsed - start, state.hp, pilot.rams, pilot.contacts, state.level, state.weapon,
            )
        }

        /**
         * Keeps the arena quiet each frame: no rocks (the empty window follows the Core, so none stream in), no
         * structures, no enemy outside the encounter [tag], and the singularity's grace, since the pilot's pointer may
         * sweep across the Core.
         */
        fun MutableGameState.keepArenaQuiet(tag: Long) {
            clearObstaclesForTesting()
            structureField = StructureField.of(
                obstacleField.centerChunkX, obstacleField.centerChunkY, worldLayer.ordinal, bossSpawned,
                emptyList(), emptyList(), emptyList(),
            )
            if (tag != 0L) enemies.removeAll { it.ownerTag != tag }
            runGrace = max(runGrace, 1f)
        }

        fun MutableGameState.isLive(): Boolean = phase == GamePhase.RUNNING || phase == GamePhase.CHOICE

        fun drain(state: MutableGameState) {
            state.takeSoundCues()
            state.takeVisualFxCues()
            state.takeProgressUpdate()
        }

        fun medianKillTime(duels: List<Duel>): Double {
            val sorted = duels.map { it.killTime }.sorted()
            if (sorted.isEmpty()) return Double.POSITIVE_INFINITY
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
        }

        fun deathShare(duels: List<Duel>): Double = duels.shareOf { it.outcome == Outcome.DIED }

        /**
         * The boss's metrics over the arm's runs, with bootstrap standard errors (the runs resampled). A resampled median
         * is infinite when at least half its duels leave the boss alive: the standard errors read the finite resamples
         * and `medianInfinite` gives the share of the others.
         */
        fun describe(summary: ArmSummary, boss: EnemyType): String {
            val duels = summary.duels(boss)
            val metrics = listOf<Pair<String, (List<Duel>) -> Double>>(
                "median" to ::medianKillTime,
                "deathShare" to ::deathShare,
                "killShare" to { sample -> sample.shareOf { it.outcome == Outcome.KILLED } },
                "timeoutShare" to { sample -> sample.shareOf { it.outcome == Outcome.TIMED_OUT } },
                "lostShare" to { sample -> sample.shareOf { it.outcome == Outcome.LOST } },
                "killedMean" to { sample ->
                    sample.filter { it.outcome == Outcome.KILLED }.map { it.seconds.toDouble() }.average()
                },
            )
            return "n=${duels.size} " + bootstrap(duels, metrics) +
                " medianInfinite=${resampledShare(duels) { !medianKillTime(it).isFinite() }.format()}"
        }

        /**
         * The widest distance of a boss's median from the mean of the arm's medians, as a share of that mean, with its
         * bootstrap standard error (the runs resampled, each with all its duels).
         */
        fun describeSpread(summary: ArmSummary): String {
            fun spread(runs: List<List<Duel>>): Double {
                val medians = summary.arm.bosses.map { boss ->
                    medianKillTime(runs.mapNotNull { run -> run.firstOrNull { it.boss == boss } })
                }
                val mean = medians.average()
                return medians.maxOf { kotlin.math.abs(it / mean - 1.0) }
            }
            return "runs=${summary.runs.size} " + bootstrap(summary.runs, listOf("spread" to ::spread))
        }

        /** Each metric's value over [items] and its bootstrap standard error over the finite resamples. */
        fun <T> bootstrap(items: List<T>, metrics: List<Pair<String, (List<T>) -> Double>>): String {
            val random = Random(BOOTSTRAP_SEED)
            val resampled = List(metrics.size) { DoubleArray(BOOTSTRAP_RESAMPLES) }
            repeat(BOOTSTRAP_RESAMPLES) { resample ->
                val sample = List(items.size) { items[random.nextInt(items.size)] }
                metrics.forEachIndexed { index, (_, metric) -> resampled[index][resample] = metric(sample) }
            }
            return metrics.mapIndexed { index, (name, metric) ->
                val values = resampled[index].filter { it.isFinite() }
                val mean = values.average()
                val se = if (values.size < 2) Double.NaN else sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
                "$name=${metric(items).format()}(SE ${se.format()})"
            }.joinToString(" ")
        }

        /** The share of bootstrap resamples of [items] for which [predicate] holds. */
        fun <T> resampledShare(items: List<T>, predicate: (List<T>) -> Boolean): Double {
            val random = Random(BOOTSTRAP_SEED)
            var count = 0
            repeat(BOOTSTRAP_RESAMPLES) { if (predicate(List(items.size) { items[random.nextInt(items.size)] })) count++ }
            return count.toDouble() / BOOTSTRAP_RESAMPLES
        }

        fun Double.format(): String = "%.3f".format(this)
    }
}
