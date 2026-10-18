// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.WeaponFamily
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Weapon balance in two fixed arenas flown by the same pilot (SPEC 10.3): straight passes through the
 * targets and laps around them ([Shuttle]) against one Elite (single-target damage) and against a crowd of
 * eight (crowd damage), with a Dash every 2 s and a short Brake every 3 s. Targets chase the Core like
 * Drifters (without contact damage), rejoin when they stray and refill below a quarter of their integrity,
 * so no kill ends a measurement. Checkpoint (a) is a fresh level-1 weapon; checkpoint (b) is an Ascended
 * level-10 weapon with the reference stat block (shield 60, combo 30, mass 1.6, luck 0.6, attack speed
 * 1.25, heat 50, cruise 1 100 u/s, Weapon Calibration 3) and neither signature nor harmony. Overdrive is
 * held off. No weapon dominates: each weapon's primary arena sits within 0.65..1.35 of that arena's median
 * and the other within 0.5..1.35 of its median; in each arena no weapon of the other families out-damages
 * the best weapon of that arena's own families (a crowd weapon never out-duels every single-target weapon,
 * and the reverse); the geometric mean of a weapon's two shares stays at most 1.3 (strong in one arena and
 * near the top of the other is a dominant pick); and Arc Coil, the weapon that dominated before the rework,
 * leads neither arena.
 */
class WeaponBalanceTest {
    @Test
    fun everyWeaponSitsWithinItsFamilyBandAtBothCheckpoints() {
        val outside = mutableListOf<String>()
        for (checkpoint in Checkpoint.entries) {
            val results = WeaponId.entries.associateWith { weapon ->
                Arena.entries.associateWith { arena -> weaponDps(weapon, arena, checkpoint) }
            }
            val singleMedian = median(results.values.map { it.getValue(Arena.SINGLE) })
            val crowdMedian = median(results.values.map { it.getValue(Arena.CROWD) })
            println("WEAPON-BALANCE $checkpoint singleMedian=${"%.1f".format(singleMedian)} crowdMedian=${"%.1f".format(crowdMedian)}")
            for ((weapon, dps) in results) {
                val single = dps.getValue(Arena.SINGLE) / singleMedian
                val crowd = dps.getValue(Arena.CROWD) / crowdMedian
                val primary = primaryArena(weapon)
                println("WEAPON-BALANCE $checkpoint $weapon primary=$primary single=${"%.2f".format(single)} crowd=${"%.2f".format(crowd)}")
                val (main, other) = if (primary == Arena.SINGLE) single to crowd else crowd to single
                if (main !in PRIMARY_BAND || other !in OTHER_BAND) outside += "$checkpoint $weapon single=$single crowd=$crowd"
                if (sqrt(single * crowd) > OVERALL_CEILING) outside += "$checkpoint $weapon overall=${sqrt(single * crowd)} (single=$single crowd=$crowd)"
            }
            for (arena in Arena.entries) {
                val (family, others) = results.entries.partition { (weapon, _) -> primaryArena(weapon) == arena }
                val best = family.maxBy { (_, dps) -> dps.getValue(arena) }
                // The arena's own families lead it: no weapon of the other families out-damages their best there.
                for ((weapon, dps) in others) {
                    if (dps.getValue(arena) >= best.value.getValue(arena)) outside += "$checkpoint $weapon out-damages ${best.key} in the $arena arena"
                }
                // Arc Coil, the weapon that once dominated, leads neither arena.
                if (results.maxBy { (_, dps) -> dps.getValue(arena) }.key == WeaponId.ARC_COIL) outside += "$checkpoint ARC_COIL leads the $arena arena"
            }
        }
        assertTrue(outside.isEmpty(), "weapons outside their band: $outside")
    }

    @Test
    fun aQuarterMoreAttackSpeedRaisesEveryWeaponsDamageByAtLeastTwelvePercent() {
        val weak = mutableListOf<String>()
        for (weapon in WeaponId.entries) {
            val arena = primaryArena(weapon)
            // Anchored targets: both flights take the same path, so only the weapon's rate differs.
            val base = weaponDps(weapon, arena, Checkpoint.FRESH, seconds = ATTACK_SPEED_SECONDS, anchored = true, variants = 8)
            val faster = weaponDps(weapon, arena, Checkpoint.FRESH, attackSpeedBonus = 0.25f, seconds = ATTACK_SPEED_SECONDS, anchored = true,
                variants = 8)
            val gain = faster / base - 1.0
            println("ATTACK-SPEED $weapon $arena gain=${"%.1f".format(gain * 100)} %")
            if (gain < 0.12) weak += "$weapon +${"%.1f".format(gain * 100)} %"
        }
        assertTrue(weak.isEmpty(), "attack speed +25 % raised too little: $weak")
    }

    @Test
    fun oneWeaponHitItemProcsAtMostOneAndAHalfTimesPerBaseCooldownAmongFortyEnemies() {
        val rime = canonicalGameplayContent.items.single { it.name == "Rime Rounds" }
        val clause = rime.clauses.single { it.trigger == EffectTrigger.WEAPON_HIT }
        val global = canonicalGameplayContent.clauseIndex(rime.id, 0)
        val fast = mutableListOf<String>()
        for (weapon in WeaponId.entries) {
            val game = arenaState(weapon, Checkpoint.FRESH, shape = nonSignature(weapon))
            game.acquireItem(rime.id)
            // The orbit crosses the crowd: a deep integrity pool keeps forty contacts in one step from ending the run.
            game.maxHp = 100_000f
            val targets = cluster(game, 40, ring = 70f, integrity = 900f)
            val seconds = 24f
            var frame = 0
            while (game.elapsed < seconds) {
                assertTrue(frame < seconds * FPS * 2f, "$weapon flight stalled in ${game.phase} (${game.message}, hp ${game.hp}/${game.maxHp}, t ${game.elapsed})")
                hold(game, targets, Checkpoint.FRESH)
                orbit(game, frame / FPS, 150f, 760f)
                game.update(1f / FPS)
                drain(game)
                frame++
            }
            val procs = game.weaponRuntime.qualifiedHits
            // Every qualifying weapon hit dispatched the item's WEAPON_HIT clause once: its every-Nth counter agrees.
            assertEquals(procs % clause.everyNth, game.clauseCounters[global], "$weapon clause dispatches")
            val rate = procs / seconds
            val bound = 1.5f / BaseCooldowns.getValue(weapon)
            println("PROC-RATE $weapon procs=$procs rate=${"%.2f".format(rate)}/s bound=${"%.2f".format(bound)}/s")
            assertTrue(procs > 0, "$weapon never fired its WEAPON_HIT item")
            if (rate > bound) fast += "$weapon ${"%.2f".format(rate)}/s > ${"%.2f".format(bound)}/s"
        }
        assertTrue(fast.isEmpty(), "proc rate above 1.5 per base cooldown: $fast")
    }

    @Test
    fun aSpiralsGhostEmitsShockwavesAtMostAtTheCoresCadenceAmongFortyEnemies() {
        val rime = canonicalGameplayContent.items.single { it.name == "Rime Rounds" }
        for (level in listOf(1, 10)) {
            val game = arenaState(WeaponId.CONCUSSION_ENGINE, Checkpoint.FRESH, shape = CoreShape.SPIRAL)
            game.acquireItem(rime.id)
            game.maxHp = 100_000f
            game.weaponLevel = level
            // A wide shield and a drained one each frame: every point a wave grants is counted, none hidden by the cap.
            game.maxShield = 10_000f
            val targets = cluster(game, 40, ring = 70f, integrity = 900f)
            val anchors = targets.map { it.x to it.y }
            val seconds = 24f
            val firstSerial = game.weaponRuntime.activationSerial
            var shieldGained = 0.0
            var frame = 0
            while (game.elapsed < seconds) {
                assertTrue(frame < seconds * FPS * 2f, "flight stalled in ${game.phase} (${game.message}, t ${game.elapsed})")
                hold(game, targets, Checkpoint.FRESH)
                // Anchored targets (the shockwaves cannot clear the ghost's path), and the Core never strikes,
                // so every shockwave (and every activation) is the ghost's.
                for ((index, target) in targets.withIndex()) {
                    target.x = anchors[index].first
                    target.y = anchors[index].second
                    target.vx = 0f
                    target.vy = 0f
                    target.disabledUntil = Float.MAX_VALUE
                    target.contactCooldown = 1f
                }
                orbit(game, frame / FPS, 150f, 760f)
                game.shield = 0f
                // No recharge: only the shockwaves fill the shield.
                game.shieldRechargeDelay = 1f
                game.update(1f / FPS)
                shieldGained += game.shield
                drain(game)
                frame++
            }
            val waves = game.weaponRuntime.activationSerial - firstSerial
            val procs = game.weaponRuntime.qualifiedHits
            println("GHOST-WAVES L$level waves=$waves procs=$procs shield=${"%.0f".format(shieldGained)}")
            assertTrue(waves > 0, "L$level: the ghost never emitted a shockwave")
            val waveBound = seconds / CONCUSSION_COOLDOWN + 1f
            assertTrue(waves <= waveBound, "L$level: $waves ghost shockwaves in $seconds s (at most $waveBound)")
            assertTrue(procs <= 1.5f * waveBound, "L$level: $procs qualifying hits from $waves shockwaves")
            if (level == 10) {
                // The third form grants 4 shield per enemy a wave strikes: bounded by the cadence, never per step.
                val shieldBound = waveBound * CONCUSSION_SHIELD_PER_HIT * targets.size
                assertTrue(shieldGained > 0.0, "the third form granted no shield")
                assertTrue(shieldGained <= shieldBound, "the ghost granted $shieldGained shield in $seconds s (at most $shieldBound)")
            }
        }
    }

    internal enum class Arena { SINGLE, CROWD }

    internal enum class Checkpoint { FRESH, ASCENDED }

    internal companion object {
        const val FPS = 60f
        const val SECONDS = 30f
        const val ATTACK_SPEED_SECONDS = 60f

        /** A weapon's family arena, as a share of that arena's median. */
        val PRIMARY_BAND = 0.65..1.35

        /** The other arena, as a share of its median: a floor, and a ceiling no higher than the primary one. */
        val OTHER_BAND = 0.5..1.35

        /** The highest geometric mean of a weapon's two shares. */
        const val OVERALL_CEILING = 1.3

        /** Seconds between a weapon's qualifying activations by design (0.25 s for persistent sources). */
        val BaseCooldowns: Map<WeaponId, Float> = mapOf(
            WeaponId.FLUX_WAKE to 0.25f, WeaponId.MORNINGSTAR to 0.25f, WeaponId.PHASE_LATTICE to 0.25f,
            WeaponId.NULL_LANCE to 1.15f, WeaponId.GRAVITY_MINES to 0.72f, WeaponId.ION_SWARM to 0.72f,
            WeaponId.RIFT_BLADES to 0.25f, WeaponId.ARC_COIL to 1.15f, WeaponId.QUASAR_CANNON to 1.65f,
            WeaponId.ENTROPY_FIELD to 0.25f, WeaponId.SINGULARITY_SPEAR to 3.2f, WeaponId.PRISM_RELAY to 0.92f,
            WeaponId.GYRE_DISC to 1.3f, WeaponId.SCATTER_BLOOM to 2.4f, WeaponId.TETHER_ARC to 0.25f,
            WeaponId.SENTRY_GRID to 0.25f, WeaponId.HUNTER_SALVO to 2.2f, WeaponId.CONCUSSION_ENGINE to 0.35f,
            WeaponId.GLACIER_BELL to 3.5f, WeaponId.EVENT_LENS to 2.8f, WeaponId.SOLAR_SWEEP to 0.25f,
            WeaponId.AEGIS_SHARDS to 1.4f, WeaponId.HOLLOW_RIFT to 2f, WeaponId.RESONANCE_DRUM to 1.6f,
        )

        fun primaryArena(weapon: WeaponId): Arena = when (canonicalGameplayContent.weapon(weapon).family) {
            WeaponFamily.AURA, WeaponFamily.PULSE, WeaponFamily.TRAP, WeaponFamily.ORBITAL -> Arena.CROWD
            WeaponFamily.PROJECTILE, WeaponFamily.BEAM, WeaponFamily.CHAIN, WeaponFamily.MOMENTUM -> Arena.SINGLE
        }

        /** A character that is not [weapon]'s signature (and deals no ability damage of its own). */
        fun nonSignature(weapon: WeaponId): CoreShape =
            if (canonicalGameplayContent.weapon(weapon).signature == CoreShape.DIAMOND) CoreShape.PRISM else CoreShape.DIAMOND

        fun median(values: List<Double>): Double {
            val sorted = values.sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
        }

        /**
         * The weapon's damage per second in [arena]. The Core passes through the targets without impacts, except for
         * the Concussion Engine, whose shockwaves impacts fire: its damage is the flight minus the same flight silenced.
         */
        fun weaponDps(
            weapon: WeaponId,
            arena: Arena,
            checkpoint: Checkpoint,
            attackSpeedBonus: Float = 0f,
            seconds: Float = SECONDS,
            anchored: Boolean = false,
            variants: Int = VARIANTS,
        ): Double {
            val impacts = weapon == WeaponId.CONCUSSION_ENGINE
            var total = 0.0
            // Each variant starts the pilot on another axis with another seed, so firing times meet the passes differently.
            for (variant in 0 until variants) {
                val dealt = flight(weapon, arena, checkpoint, attackSpeedBonus, silenced = false, impacts = impacts, variant = variant, seconds = seconds, anchored = anchored)
                total += dealt
                if (impacts) total -= flight(weapon, arena, checkpoint, attackSpeedBonus, silenced = true, impacts = true, variant = variant, seconds = seconds, anchored = anchored)
            }
            return total / variants / seconds
        }

        const val VARIANTS = 4

        fun arenaState(weapon: WeaponId, checkpoint: Checkpoint, shape: CoreShape, seed: Int = 6_061): MutableGameState =
            MutableGameState(canonicalGameplayContent, seed = seed).apply {
                coreShape = shape
                unlockWeaponsForTesting(listOf(weapon))
                startingWeapon = weapon
                if (checkpoint == Checkpoint.ASCENDED) {
                    val node = content.matrixNodes.indexOfFirst { it.id == MatrixNodeId.WEAPON_CALIBRATION }
                    matrixRanks[node] = 3
                }
                resize(1_920f, 1_080f)
                startRun()
                applyPreferences(settings.copy(simulationSpeed = 1f))
                enemies.clear()
                projectiles.clear()
                pickups.clear()
                spawnClock = Float.MAX_VALUE
                nextEliteAt = Float.MAX_VALUE
                runGrace = 0f
                pointsOfInterest = emptyList()
                nextPointOfferIndex = Int.MAX_VALUE / 2
                nextLevelData = Int.MAX_VALUE / 2
                clearObstaclesForTesting()
                if (checkpoint == Checkpoint.ASCENDED) {
                    weaponLevel = 10
                    maxShield = 60f
                    mass = 1.6f
                    luck = 0.6f
                }
            }

        /** Total damage the arena's targets took over [SECONDS]. */
        fun flight(
            weapon: WeaponId,
            arena: Arena,
            checkpoint: Checkpoint,
            attackSpeedBonus: Float,
            silenced: Boolean,
            impacts: Boolean,
            variant: Int,
            seconds: Float,
            anchored: Boolean,
        ): Double {
            val game = arenaState(weapon, checkpoint, nonSignature(weapon), seed = 6_061 + variant)
            if (checkpoint == Checkpoint.ASCENDED) game.attackSpeed = 1.25f
            game.attackSpeed += attackSpeedBonus
            val targets = when (arena) {
                // The single target is an Elite (its victim factor tempers %-integrity effects), without an aspect.
                Arena.SINGLE -> listOf(game.addEnemyForTesting(0f, 0f, hp = SINGLE_INTEGRITY, radius = 22f, type = EnemyType.ELITE)
                    .also { assignAspect(it, -1, immune = false) })
                Arena.CROWD -> cluster(game, CROWD_SIZE, ring = CROWD_RING, integrity = CROWD_INTEGRITY)
            }
            val anchors = targets.map { it.x to it.y }
            val cruise = if (checkpoint == Checkpoint.ASCENDED) 1_100f else 650f
            val reach = if (checkpoint == Checkpoint.ASCENDED) 420f else 280f
            var dealt = 0.0
            var frame = 0
            // Variants also change the legs' length and pace, so no firing cadence keeps meeting the same leg phase.
            val pilot = Shuttle(reach * (0.85f + 0.12f * variant), cruise * (0.92f + 0.06f * variant), startAngle = variant * 1.3f)
            while (game.elapsed < seconds && game.phase == GamePhase.RUNNING) {
                if (silenced) game.weaponPower = 0f
                hold(game, targets, checkpoint)
                if (anchored) {
                    for ((index, target) in targets.withIndex()) {
                        target.x = anchors[index].first
                        target.y = anchors[index].second
                        target.vx = 0f
                        target.vy = 0f
                        target.disabledUntil = Float.MAX_VALUE
                    }
                }
                if (!impacts) for (target in targets) target.contactCooldown = 1f
                val t = frame / FPS
                pilot.fly(game, targets.sumOf { it.x.toDouble() }.toFloat() / targets.size, targets.sumOf { it.y.toDouble() }.toFloat() / targets.size)
                game.setBrake(t % 3f in 2.6f..3f)
                if (frame > 0 && frame % (2 * FPS).toInt() == 0) game.requestDash()
                val before = targets.sumOf { it.hp.toDouble() }
                game.update(1f / FPS)
                dealt += before - targets.sumOf { it.hp.toDouble() }
                drain(game)
                frame++
            }
            return dealt
        }

        const val SINGLE_INTEGRITY = 6_000f
        const val CROWD_INTEGRITY = 900f
        const val CROWD_SIZE = 8
        const val STRAY_DISTANCE = 650f
        const val CROWD_RING = 150f

        /** [count] anchored targets: a quarter on an inner ring of radius [ring], the rest on one twice as wide. */
        fun cluster(game: MutableGameState, count: Int, ring: Float, integrity: Float): List<Enemy> {
            val inner = count / 4
            return List(count) { index ->
                val (radius, slot, slots) = if (index < inner) Triple(ring, index, inner) else Triple(ring * 2.1f, index - inner, count - inner)
                val angle = slot * 6.2831855f / slots + if (index < inner) 0f else 0.3f
                game.addEnemyForTesting(cos(angle) * radius, sin(angle) * radius, hp = integrity)
            }
        }

        /** Keeps the targets in place, idle and topped up, and holds the checkpoint's stat block. */
        fun hold(game: MutableGameState, targets: List<Enemy>, checkpoint: Checkpoint) {
            for (target in targets) {
                if (target !in game.enemies) {
                    target.dead = false
                    game.enemies += target
                }
                if (target.hp < target.maxHp * 0.25f) target.hp = target.maxHp
                // Targets chase the Core like Drifters; a straggler rejoins the fight 280 away.
                if (length(target.x - game.coreX, target.y - game.coreY) > STRAY_DISTANCE) {
                    val angle = target.id * 2.3999632f + game.elapsed
                    target.x = game.coreX + cos(angle) * 280f
                    target.y = game.coreY + sin(angle) * 280f
                    target.previousX = target.x
                    target.previousY = target.y
                    target.vx = 0f
                    target.vy = 0f
                }
            }
            game.hp = game.maxHp
            // The arena stays open: the empty window follows the Core, so no generated obstacles stream in.
            game.clearObstaclesForTesting()
            // The pilot's cursor may sweep across the Core: the singularity grace keeps the flight going.
            game.runGrace = 1f
            game.overdriveCharge = 0f
            game.overdriveTime = 0f
            if (checkpoint == Checkpoint.ASCENDED) {
                game.combo = 30
                game.comboTime = 10f
                game.heat = 50f
                game.shield = game.shieldCapacity()
            }
        }

        /**
         * The arena pilot: two straight passes through the targets' center at about [cruise] u/s (each leg heads in
         * to the center, then out to a waypoint [reach] away whose axis turns 37° per leg), then one lap around the
         * center at [ORBIT_RADIUS] and [ORBIT_SPEED]. Passes favor aimed weapons, laps favor auras and orbitals;
         * every weapon meets both.
         */
        class Shuttle(private val reach: Float, private val cruise: Float, startAngle: Float = 0f) {
            private var angle = startAngle
            private var waypointX = cos(startAngle) * reach
            private var waypointY = sin(startAngle) * reach
            /** Heading in to the targets' center (then out to the waypoint). */
            private var inbound = false
            private var legs = 0
            /** Lap angle travelled, or a negative value while passing. */
            private var lap = -1f
            private var lapAngle = 0f

            /** Flies one frame around the targets' center ([centerX], [centerY]). */
            fun fly(game: MutableGameState, centerX: Float, centerY: Float) {
                if (lap >= 0f) {
                    val omega = ORBIT_SPEED / ORBIT_RADIUS
                    lap += omega / FPS
                    lapAngle += omega / FPS
                    if (lap >= 6.2831855f) {
                        lap = -1f
                    } else {
                        steer(game, centerX + cos(lapAngle) * ORBIT_RADIUS, centerY + sin(lapAngle) * ORBIT_RADIUS,
                            -sin(lapAngle) * ORBIT_SPEED, cos(lapAngle) * ORBIT_SPEED)
                        return
                    }
                }
                if (inbound && length(game.coreX - centerX, game.coreY - centerY) < 40f) inbound = false
                if (!inbound && length(centerX + waypointX - game.coreX, centerY + waypointY - game.coreY) < 90f) {
                    legs++
                    if (legs % 2 == 0) {
                        // A lap from where the Core stands, swinging the same way it was heading.
                        lap = 0f
                        lapAngle = atan2(game.coreY - centerY, game.coreX - centerX)
                    }
                    angle += 3.7867f
                    waypointX = cos(angle) * reach
                    waypointY = sin(angle) * reach
                    inbound = true
                }
                val dx = centerX + (if (inbound) 0f else waypointX) - game.coreX
                val dy = centerY + (if (inbound) 0f else waypointY) - game.coreY
                val distance = length(dx, dy).coerceAtLeast(1f)
                steer(game, game.coreX + dx / distance * 40f, game.coreY + dy / distance * 40f, dx / distance * cruise, dy / distance * cruise)
            }
        }

        const val ORBIT_RADIUS = 125f
        const val ORBIT_SPEED = 650f

        /** Steers around the origin at radius [radius] and [cruise] u/s (the proc arena). */
        fun orbit(game: MutableGameState, t: Float, radius: Float, cruise: Float) {
            val omega = cruise / radius
            steer(game, cos(omega * t) * radius, sin(omega * t) * radius, -sin(omega * t) * radius * omega, cos(omega * t) * radius * omega)
        }

        fun steer(game: MutableGameState, x: Float, y: Float, vx: Float, vy: Float) {
            val ax = (x - game.coreX) * 6f + (vx - game.velocityX) * 4f
            val ay = (y - game.coreY) * 6f + (vy - game.velocityY) * 4f
            val heading = atan2(ay, ax)
            val distance = ((length(ax, ay) - 92f) / game.magnetStrength).coerceIn(65f, 380f)
            val edge = (1f - game.content.tempo.fatigue.edgeStrainStart) * 0.5f
            game.updatePointer(
                (game.coreX + cos(heading) * distance - game.cameraX + game.screenWidth / 2f)
                    .coerceIn(game.screenWidth * edge, game.screenWidth * (1f - edge)),
                (game.coreY + sin(heading) * distance - game.cameraY + game.screenHeight / 2f)
                    .coerceIn(game.screenHeight * edge, game.screenHeight * (1f - edge)),
            )
        }

        fun drain(game: MutableGameState) {
            if (game.phase == GamePhase.CHOICE) game.choose(0)
            game.takeVisualFxCues()
            game.takeSoundCues()
            game.takeProgressUpdate()
        }
    }
}
