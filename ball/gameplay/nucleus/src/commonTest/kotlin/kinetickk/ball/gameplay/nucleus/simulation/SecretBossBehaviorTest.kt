// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.impactTaken
import kinetickk.ball.gameplay.nucleus.model.weaponTaken
import kinetickk.ball.gameplay.nucleus.render.EnemyAffinity
import kinetickk.ball.gameplay.nucleus.render.EnemyBehavior
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.ProjectileKind
import kinetickk.foundation.collections.immutableListOf
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The secret bosses: the Cartographer's lanes, mine lines and rock jumps, and the Antecedent's alternating phases. */
class SecretBossBehaviorTest {
    @Test
    fun theCartographerSurveysThreeParallelLanesAlongTheAimItLockedWhenItMarkedThem() {
        val state = quietState()
        val carto = state.spawn(EnemyType.CARTOGRAPHER, CARTO_ORBIT_RADIUS, 0f)
        state.runFor(FIXED)
        state.projectiles.clear()

        carto.actionTimer = FIXED * 0.5f
        state.runFor(FIXED)
        assertEquals(EnemyBehavior.TELEGRAPH, carto.mode, "the lanes are marked first")
        val marked = state.elapsed
        val aim = atan2(state.coreY - carto.previousY, state.coreX - carto.previousX)
        assertEquals(aim, carto.facing, 1e-4f)
        assertEquals(CARTO_SURVEY_SECONDS, carto.actionTimer, 1e-4f)

        // The aim is locked: a Core sliding aside moves no lane, and nothing fires while the lanes show.
        state.coreY += 160f
        state.runFor(0.5f)
        assertEquals(aim, carto.facing, 1e-6f)
        assertTrue(state.projectiles.isEmpty())
        state.runUntil { carto.mode == EnemyBehavior.IDLE }
        assertEquals(CARTO_LANE_TELEGRAPH_SECONDS, state.elapsed - marked, 2 * FIXED)

        // Three parallel lanes 60 u apart, each a train of three bolts starting at its rim, all along the aim.
        val shots = state.projectiles.toList()
        assertEquals(3 * CARTO_LANE_TRAIN, shots.size)
        val speed = CARTO_LANE_SPEED * state.rebirthProfile.enemySpeedMultiplier
        val lanes = mutableMapOf<Int, MutableList<Int>>()
        for (shot in shots) {
            assertEquals(cos(aim) * speed, shot.vx, 1e-2f)
            assertEquals(sin(aim) * speed, shot.vy, 1e-2f)
            assertEquals(CARTO_LANE_RADIUS, shot.radius)
            assertEquals(CARTO_LANE_LIFE, shot.life)
            assertEquals(state.shotDamage(carto), shot.damage)
            val rx = shot.x - carto.previousX
            val ry = shot.y - carto.previousY
            val across = (-sin(aim) * rx + cos(aim) * ry).roundToInt()
            lanes.getOrPut(across) { mutableListOf() } += (cos(aim) * rx + sin(aim) * ry).roundToInt()
        }
        assertEquals(setOf(-60, 0, 60), lanes.keys)
        val rim = (carto.radius + 4f).roundToInt()
        lanes.values.forEach { assertEquals(listOf(rim, rim + 40, rim + 80), it.sorted()) }

        // The next Survey is marked a cycle after the first.
        state.runUntil { carto.mode == EnemyBehavior.TELEGRAPH }
        assertEquals(CARTO_SURVEY_SECONDS / state.hostileAttackRate(), state.elapsed - marked, 2 * FIXED)
    }

    @Test
    fun theCartographersSecondPhaseAlternatesItsLanesWithALineOfMinesAheadOfTheCoreWithinTheLiveMineBound() {
        val state = quietState()
        val carto = state.spawn(EnemyType.CARTOGRAPHER, 0f, -CARTO_ORBIT_RADIUS)
        state.runFor(FIXED)
        state.crossGate(carto)
        state.runFor(FIXED)
        assertEquals(1, carto.phase)
        state.elapsed = carto.shiftUntil
        state.projectiles.clear()
        state.setVelocityForTesting(300f, 0f)

        carto.actionTimer = FIXED * 0.5f
        state.runFor(FIXED)
        assertEquals(EnemyBehavior.TELEGRAPH, carto.mode, "Cartography opens with its lanes")
        state.runUntil { carto.mode == EnemyBehavior.IDLE }
        assertEquals(3 * CARTO_LANE_TRAIN, state.projectiles.size)

        // Then a line of six Minelayer mines 380 u ahead of the Core across its heading, 70 u apart, unmarked.
        state.projectiles.clear()
        carto.actionTimer = FIXED * 0.5f
        state.runFor(FIXED)
        assertEquals(EnemyBehavior.IDLE, carto.mode)
        val mines = state.projectiles.filter { it.kind == ProjectileKind.MINE }
        assertEquals(CARTO_MINE_LINE, mines.size)
        for (mine in mines) {
            assertEquals(MINE_RADIUS, mine.radius)
            assertEquals(MINE_LIFE_SECONDS, mine.life)
            assertEquals(state.shotDamage(carto), mine.damage)
            assertEquals(0f, mine.vx)
            assertEquals(0f, mine.vy)
            assertEquals(state.coreX + CARTO_MINE_AHEAD, mine.x, 1e-2f)
        }
        assertEquals(listOf(-175, -105, -35, 35, 105, 175), mines.map { (it.y - state.coreY).roundToInt() }.sorted())

        // And the lanes again.
        carto.actionTimer = FIXED * 0.5f
        state.runFor(FIXED)
        assertEquals(EnemyBehavior.TELEGRAPH, carto.mode)

        // The line never takes the live mines past ten: 4 live leave room for 6, 5 for 5, 9 for 1, 10 for none.
        fun lineWith(live: Int): Int {
            state.projectiles.clear()
            repeat(live) { index ->
                state.projectiles += Projectile(state.coreX - 600f + index * 30f, state.coreY + 500f, 0f, 0f, MINE_RADIUS,
                    MINE_LIFE_SECONDS, damage = 1f, kind = ProjectileKind.MINE)
            }
            carto.mode = EnemyBehavior.IDLE
            carto.charges = 1
            carto.actionTimer = FIXED * 0.5f
            state.runFor(FIXED)
            return state.projectiles.count { it.kind == ProjectileKind.MINE } - live
        }
        assertEquals(6, lineWith(4))
        assertEquals(5, lineWith(5))
        assertEquals(1, lineWith(9))
        assertEquals(0, lineWith(MAX_LIVE_MINES))

        // A Core at rest: the line lies beyond it, seen from the Cartographer.
        state.setVelocityForTesting(0f, 0f)
        assertEquals(CARTO_MINE_LINE, lineWith(0))
        val toCoreX = state.coreX - carto.previousX
        val toCoreY = state.coreY - carto.previousY
        val apart = sqrt(toCoreX * toCoreX + toCoreY * toCoreY)
        val line = state.projectiles.filter { it.kind == ProjectileKind.MINE }
        assertEquals(state.coreX + toCoreX / apart * CARTO_MINE_AHEAD, line.map { it.x }.average().toFloat(), 0.05f)
        assertEquals(state.coreY + toCoreY / apart * CARTO_MINE_AHEAD, line.map { it.y }.average().toFloat(), 0.05f)

        // A silenced turn lays nothing and keeps its turn.
        state.projectiles.clear()
        carto.charges = 1
        carto.silencedUntil = Float.MAX_VALUE
        carto.actionTimer = FIXED * 0.5f
        state.runFor(FIXED)
        assertTrue(state.projectiles.isEmpty())
        assertEquals(1, carto.charges)
    }

    @Test
    fun theCartographerTriangulatesToTheFarRimOfTheRockNearestTheCoreThenFiresARingAndAnAimedFan() {
        val state = quietState()
        val rock = testObstacle(state.coreX - 500f, state.coreY, radius = 80f)
        state.placeObstaclesForTesting(
            // Exactly 220 u from the Core: too near to jump to.
            testObstacle(state.coreX + CARTO_JUMP_NEAREST, state.coreY, radius = 40f),
            rock,
            testObstacle(state.coreX, state.coreY + 700f, radius = 90f),
        )
        val carto = state.spawn(EnemyType.CARTOGRAPHER, 0f, -CARTO_ORBIT_RADIUS)
        state.runFor(FIXED)
        carto.phase = 2
        carto.actionTimer = FIXED * 0.5f
        state.runFor(FIXED)
        assertEquals(EnemyBehavior.PHASED, carto.mode)
        val phasedAt = state.elapsed
        assertTrue(state.isPhasedPhantom(carto), "out of phase it is intangible")
        val integrity = carto.hp
        state.damageEnemy(carto, 100f)
        assertEquals(integrity, carto.hp, "and takes nothing")
        assertEquals(CARTO_JUMP_SECONDS, carto.actionTimer, 1e-4f)

        state.runUntil { carto.mode == EnemyBehavior.TELEGRAPH }
        assertEquals(CARTO_PHASE_SECONDS, state.elapsed - phasedAt, 2 * FIXED)
        val rim = rock.radius + carto.radius + CARTO_RIM_GAP
        assertEquals(rock.x - rim, carto.x, 1e-2f)
        assertEquals(rock.y, carto.y, 1e-2f)
        assertEquals(carto.x, carto.previousX, "no smear from where it was")
        assertEquals(carto.y, carto.previousY)
        assertFalse(state.isShifting(carto))
        assertEquals(0f, carto.facing, 1e-3f, "it aims at the Core")

        state.projectiles.clear()
        val reappeared = state.elapsed
        state.runUntil { carto.mode == EnemyBehavior.IDLE }
        assertEquals(CARTO_AIM_SECONDS, state.elapsed - reappeared, 2 * FIXED)
        assertEquals(CARTO_RING + CARTO_FAN, state.projectiles.size)
        val scale = state.rebirthProfile.enemySpeedMultiplier
        val fan = state.projectiles.filter { abs(sqrt(it.vx * it.vx + it.vy * it.vy) - CARTO_FAN_SPEED * scale) < 0.5f }
        assertEquals(CARTO_FAN, fan.size)
        fan.forEach { assertTrue(abs(angleBetween(atan2(it.vy, it.vx), 0f)) <= 2 * CARTO_FAN_SPACING + 1e-3f) }
        val ring = state.projectiles - fan.toSet()
        ring.forEach { assertEquals(CARTO_RING_SPEED * scale, sqrt(it.vx * it.vx + it.vy * it.vy), 0.5f) }

        // One unit farther out the near rock is the nearest one; past 900 u no rock counts.
        fun jumpAmong(vararg rocks: Float): Pair<Float, Float> {
            val arena = quietState()
            arena.placeObstaclesForTesting(*rocks.map { testObstacle(arena.coreX + it, arena.coreY, radius = 40f) }.toTypedArray())
            val boss = arena.spawn(EnemyType.CARTOGRAPHER, 0f, -CARTO_ORBIT_RADIUS)
            arena.runFor(FIXED)
            boss.phase = 2
            boss.actionTimer = FIXED * 0.5f
            arena.runFor(FIXED)
            arena.runUntil { boss.mode == EnemyBehavior.TELEGRAPH }
            return boss.x - arena.coreX to boss.y - arena.coreY
        }
        val rimGap = 40f + carto.radius + CARTO_RIM_GAP
        assertEquals(CARTO_JUMP_NEAREST + 1f + rimGap, jumpAmong(CARTO_JUMP_NEAREST + 1f, 500f).first, 1e-2f)
        assertEquals(CARTO_JUMP_REACH + rimGap, jumpAmong(CARTO_JUMP_REACH).first, 1e-2f)
        val (fartherX, fartherY) = jumpAmong(CARTO_JUMP_REACH + 1f)
        assertEquals(CARTO_FALLBACK_REACH, sqrt(fartherX * fartherX + fartherY * fartherY), 1e-2f, "no rock within reach")
    }

    @Test
    fun withNoRockInReachTheCartographerJumpsFourHundredTwentyFromTheCoreAtAHashedTurnAndDrawsNothing() {
        fun jump(seed: Int, at: Float, check: Boolean = false): Pair<Float, Float> {
            val state = quietState(seed = seed)
            val carto = state.spawn(EnemyType.CARTOGRAPHER, 0f, -CARTO_ORBIT_RADIUS)
            state.runFor(FIXED)
            carto.phase = 2
            state.elapsed = at
            val spawn = state.spawnRandom.snapshot()
            val gameplay = state.gameplayRandom.snapshot()
            val reward = state.rewardRandom.snapshot()
            carto.actionTimer = FIXED * 0.5f
            state.runFor(FIXED)
            state.runUntil { carto.mode == EnemyBehavior.TELEGRAPH }
            val x = carto.x - state.coreX
            val y = carto.y - state.coreY
            assertEquals(CARTO_FALLBACK_REACH, sqrt(x * x + y * y), 1e-2f)
            if (check) {
                assertEquals(spawn, state.spawnRandom.snapshot())
                assertEquals(gameplay, state.gameplayRandom.snapshot())
                assertEquals(reward, state.rewardRandom.snapshot())
            }
            return x to y
        }
        assertEquals(jump(5_150, 40f, check = true), jump(5_150, 40f), "the same run at the same time jumps the same way")
        val angles = (0 until 8).map { index -> jump(5_150, 40f + index * 7.3f).let { (x, y) -> (atan2(y, x) * 10f).roundToInt() } }
        assertTrue(angles.toSet().size >= 6, "jumps spread around the Core: $angles")
        assertTrue(jump(77, 40f) != jump(5_150, 40f), "another run turns another way")
    }

    @Test
    fun aCartographerGateRingsDropsThreeRepairsAndHoldsItsAttacksThroughItsFourSecondShift() {
        val state = quietState()
        val carto = state.spawn(EnemyType.CARTOGRAPHER, CARTO_ORBIT_RADIUS, 0f)
        state.runFor(FIXED)
        assertEquals(0, state.pickups.count { it.type == PickupType.REPAIR }, "it arrives without repairs")

        // A Survey marked when the gate is reached is called off.
        carto.mode = EnemyBehavior.TELEGRAPH
        carto.modeUntil = state.elapsed + 0.5f
        carto.actionTimer = 0.5f
        state.crossGate(carto)
        assertEquals(state.elapsed + 4f, carto.shiftUntil, 1e-4f)
        state.runFor(FIXED)
        assertEquals(CARTO_SHIFT_REPAIRS, state.pickups.count { it.type == PickupType.REPAIR })
        assertTrue(state.isPhasedPhantom(carto), "its shift is intangible too")
        state.projectiles.clear()
        state.runFor(3.9f)
        assertTrue(state.projectiles.isEmpty(), "no attack while it shifts")
        assertEquals(EnemyBehavior.IDLE, carto.mode)
        assertEquals(CARTO_CARTOGRAPHY_SECONDS, carto.actionTimer, 1e-4f)
        assertEquals(0, carto.charges, "Cartography starts with its lanes")
        state.runFor(0.2f)
        assertFalse(state.isShifting(carto))
        assertTrue(carto.actionTimer < CARTO_CARTOGRAPHY_SECONDS, "its cycle runs again")
    }

    @Test
    fun theAntecedentAlternatesZenithAndNadirPhasesEachCallingItsOwnAddsIntoItsEncounter() {
        val state = encounterState()
        val tag = encounterTag(EncounterOwner.DIMENSION, 0x0402)
        assertTrue(state.spawnEncounter(EncounterRequest(tag, state.coreX + 500f, state.coreY, 900f, listOf(EnemyType.ANTECEDENT),
            abandonDistance = 6_000f)))
        val boss = state.enemies.single { it.type == EnemyType.ANTECEDENT }
        state.runFor(FIXED)
        assertEquals(mapOf(EnemyType.SERAPH to 1, EnemyType.CHOIR to 1), state.adds(boss))
        assertTrue(state.enemies.all { it.ownerTag == tag }, "its adds join its encounter")
        state.enemies.filter { it !== boss }.forEach { assertEquals(state.spawnIntegrity(it.type), it.maxHp, 1e-2f) }
        state.startSecretBossPhase(boss)
        assertEquals(mapOf(EnemyType.SERAPH to 1, EnemyType.CHOIR to 1), state.adds(boss), "one of each alive at most")

        // Nadir: two Imps, four alive at most.
        state.nextGate(boss)
        assertEquals(1, boss.phase)
        assertEquals(2, state.adds(boss)[EnemyType.IMP])
        state.startSecretBossPhase(boss)
        assertEquals(4, state.adds(boss)[EnemyType.IMP])
        state.startSecretBossPhase(boss)
        assertEquals(4, state.adds(boss)[EnemyType.IMP])

        // Zenith again: no adds, two shield arcs.
        val before = state.adds(boss)
        state.nextGate(boss)
        assertEquals(2, boss.phase)
        assertEquals(before, state.adds(boss))
        assertEquals(ANTECEDENT_ARCS, boss.charges)

        // Nadir, final: three Hounds, three alive at most; the arcs fall.
        state.nextGate(boss)
        assertEquals(3, boss.phase)
        assertEquals(0, boss.charges)
        assertEquals(ANTECEDENT_HOUNDS, state.adds(boss)[EnemyType.HOUND])
        state.startSecretBossPhase(boss)
        assertEquals(ANTECEDENT_HOUNDS, state.adds(boss)[EnemyType.HOUND])
        assertEquals(state.enemies.size, assertNotNull(state.encounter(tag)).spawned)
    }

    @Test
    fun theAntecedentHardensAgainstTheBuildAtEachGateAndIsNeverImmune() {
        fun gates(vararg items: String): List<Int> {
            val state = quietState().apply { items.forEach { own(it) } }
            val boss = state.spawn(EnemyType.ANTECEDENT, 400f, 0f)
            state.runFor(FIXED)
            val aspects = mutableListOf(EnemyAffinity.aspect(state.affinityBits(boss)))
            val frames = mutableListOf(boss.impactTaken)
            repeat(3) {
                state.nextGate(boss)
                assertFalse(EnemyAffinity.immune(state.affinityBits(boss)), "never immune")
                aspects += EnemyAffinity.aspect(state.affinityBits(boss))
                boss.guarded = false
                frames += boss.impactTaken
            }
            assertEquals(listOf(0.8f, 0.76f, 0.72f, 0.68f), frames.map { (it * 100f).roundToInt() / 100f }, "the kinetic frame hardens")
            return aspects
        }
        val none = EnemyAffinity.NONE
        val thermal = StatusElement.THERMAL.ordinal
        val cryo = StatusElement.CRYO.ordinal
        assertEquals(listOf(none, none, none, none), gates("Igniter"), "one source is below the first tier")
        assertEquals(listOf(none, thermal, thermal, thermal), gates("Igniter", "Incendiary Rounds", "Frost Plating"))
        // Both at their first tier: the dominant (Cryo), the secondary (Thermal), then the dominant again.
        assertEquals(listOf(none, cryo, thermal, cryo), gates("Igniter", "Incendiary Rounds", "Frost Plating", "Rime Rounds"))
    }

    @Test
    fun theAntecedentRaisesTwoTurningArcsOnlyInItsThirdPhaseAndAShotOnAnArcIsSpent() {
        val state = quietState()
        val boss = state.spawn(EnemyType.ANTECEDENT, 400f, 0f)
        state.runFor(FIXED)
        for (phase in 0..3) {
            if (phase > 0) state.nextGate(boss)
            state.enemies.removeAll { it !== boss }
            assertEquals(if (phase == 2) ANTECEDENT_ARCS else 0, boss.charges, "phase $phase")
            boss.facing = atan2(state.coreY - boss.y, state.coreX - boss.x)
            state.runFor(FIXED)
            assertEquals(phase == 2, boss.guarded, "only its arcs guard it (phase $phase)")
        }

        // The third phase: two arcs turning 0.9 rad/s; through an arc its guarded frame, through a gap its own.
        val arena = quietState()
        val target = arena.spawn(EnemyType.ANTECEDENT, 300f, 0f)
        arena.runFor(FIXED)
        repeat(2) { arena.nextGate(target) }
        arena.enemies.removeAll { it !== target }
        assertEquals(2, target.phase)
        target.facing = 0.5f
        arena.runFor(1f)
        assertEquals(0.5f + ANTECEDENT_ARC_SPIN, target.facing, 2 * FIXED * ANTECEDENT_ARC_SPIN)
        val aim = atan2(arena.coreY - target.y, arena.coreX - target.x)
        target.facing = aim - ANTECEDENT_ARC_SPIN * FIXED
        arena.runFor(FIXED)
        assertTrue(target.guarded)
        assertEquals(0.15f, target.impactTaken)
        assertEquals(0.2f, target.weaponTaken)
        target.facing = aim + 60f * DEGREE - ANTECEDENT_ARC_SPIN * FIXED
        arena.runFor(FIXED)
        assertFalse(target.guarded, "a gap between its two arcs")
        assertEquals(0.72f, target.impactTaken, 1e-5f)
        arena.projectiles.clear()
        val integrity = target.hp
        // An arc faces a shot from its west while the Core-facing guard says otherwise: spent, no return shot.
        target.facing = PI.toFloat()
        target.guarded = false
        val blocked = Projectile(target.x - 200f, target.y, 900f, 0f, 5f, 2f, hostile = false, damage = 40f, pierce = 3).apply {
            x = target.x - 10f
            previousX = target.x - 200f
        }
        arena.projectiles += blocked
        arena.resolveProjectileHits()
        assertTrue(blocked !in arena.projectiles, "the arc stops the shot")
        assertTrue(arena.projectiles.none { it.hostile }, "no return shot")
        assertEquals(integrity - 40f * 0.2f, target.hp, 1e-2f)
        assertFalse(target.guarded)
    }

    @Test
    fun theAntecedentsZenithPhasesShieldAndSingTheChoirsPulseAndItsNadirPhasesHurtAtAnySpeed() {
        val state = quietState()
        val boss = state.spawn(EnemyType.ANTECEDENT, 250f, 0f)
        state.runFor(FIXED)
        state.enemies.removeAll { it !== boss }
        val start = state.elapsed
        assertEquals(0f, state.strikeDamage(boss), "Zenith contact is ordinary")

        // An impact-only shield 8 s into the phase for 2.5 s, then every 8 s.
        state.runUntil(10f) { state.shielded(boss) }
        assertEquals(ANTECEDENT_SHIELD_INTERVAL_SECONDS, state.elapsed - start, 2 * FIXED)
        val first = state.elapsed
        assertEquals(state.elapsed + ANTECEDENT_SHIELD_SECONDS, boss.shieldUntil, 1e-4f)
        state.runUntil(10f) { !state.shielded(boss) }
        state.runUntil(10f) { state.shielded(boss) }
        assertEquals(ANTECEDENT_SHIELD_INTERVAL_SECONDS, state.elapsed - first, 2 * FIXED)

        // The Choir's pulse: sung 0.6 s, then twelve bolts and a shove on a Core inside its wave.
        boss.actionTimer = FIXED * 0.5f
        state.runFor(FIXED)
        assertEquals(EnemyBehavior.TELEGRAPH, boss.mode)
        assertEquals(ANTECEDENT_PULSE_SECONDS, boss.actionTimer, 1e-4f)
        boss.x = state.coreX + 240f
        boss.y = state.coreY
        state.projectiles.clear()
        state.setVelocityForTesting(0f, 0f)
        state.runUntil { boss.mode == EnemyBehavior.IDLE }
        assertEquals(ANTECEDENT_PULSE, state.projectiles.size)
        state.projectiles.forEach {
            assertEquals(CHOIR_SHOT_SPEED * state.rebirthProfile.enemySpeedMultiplier, sqrt(it.vx * it.vx + it.vy * it.vy), 0.5f)
        }
        val awayX = state.coreX - boss.previousX
        val awayY = state.coreY - boss.previousY
        val apart = sqrt(awayX * awayX + awayY * awayY)
        assertTrue(apart <= CHOIR_WAVE_RADIUS + state.coreRadius)
        assertEquals(awayX / apart * CHOIR_WAVE_PUSH, state.velocityX, 1e-2f)
        assertEquals(awayY / apart * CHOIR_WAVE_PUSH, state.velocityY, 1e-2f)
        // Outside its wave the bolts fly and nothing shoves.
        boss.actionTimer = FIXED * 0.5f
        state.runFor(FIXED)
        boss.x = state.coreX + 600f
        boss.y = state.coreY
        state.projectiles.clear()
        state.setVelocityForTesting(0f, 0f)
        state.runUntil { boss.mode == EnemyBehavior.IDLE }
        assertEquals(ANTECEDENT_PULSE, state.projectiles.size)
        assertEquals(0f, state.velocityX)
        assertEquals(0f, state.velocityY)

        // Nadir phases hurt at any Core speed; the third (Zenith) does not.
        boss.shieldUntil = state.elapsed
        state.nextGate(boss)
        assertTrue(state.strikeDamage(boss) > 0f)
        assertEquals(state.contactDamage(boss), state.strikeDamage(boss))
        state.nextGate(boss)
        assertEquals(0f, state.strikeDamage(boss))
        state.nextGate(boss)
        assertEquals(state.contactDamage(boss), state.strikeDamage(boss))
        boss.silencedUntil = Float.MAX_VALUE
        assertEquals(0f, state.strikeDamage(boss), "a silenced Antecedent's contact is ordinary again")
    }

    @Test
    fun theAntecedentsLaterPhasesBurnFanAndRingAndItsShiftAttacksNothing() {
        val state = quietState()
        val boss = state.spawn(EnemyType.ANTECEDENT, 400f, 0f)
        state.runFor(FIXED)
        state.nextGate(boss, skipShift = false)
        state.enemies.removeAll { it !== boss }

        // Through its five-second shift it attacks nothing.
        assertEquals(state.elapsed + 5f - FIXED, boss.shiftUntil, 1e-3f)
        state.projectiles.clear()
        state.volatileBursts = immutableListOf()
        state.runFor(4.9f)
        assertTrue(state.projectiles.isEmpty())
        assertTrue(state.volatileBursts.isEmpty())

        // Nadir: an ember on its trail every 0.5 s and a spiral of ten every second.
        state.runUntil { !state.isShifting(boss) }
        state.runUntil { state.volatileBursts.isNotEmpty() }
        val firstEmber = state.elapsed
        state.runUntil { state.volatileBursts.size == 2 }
        assertEquals(ANTECEDENT_EMBER_SECONDS / state.hostileAttackRate(), state.elapsed - firstEmber, 2 * FIXED)
        assertEquals(PYRE_EMBER_DELAY, state.volatileBursts.last().detonatesAt - state.volatileBursts.last().armedAt, 1e-4f)
        state.projectiles.clear()
        boss.actionTimer = FIXED * 0.5f
        state.runFor(FIXED)
        assertEquals(ANTECEDENT_SPIRAL, state.projectiles.size)
        assertEquals(PYRE_SHOT_LIFE, state.projectiles.first().life)

        // Zenith again: an aimed fan of five every 1.3 s and the pulse every 4 s; no embers.
        state.nextGate(boss)
        state.volatileBursts = immutableListOf()
        state.projectiles.clear()
        boss.actionTimer = FIXED * 0.5f
        state.runFor(FIXED)
        assertEquals(ANTECEDENT_FAN, state.projectiles.size)
        val aim = atan2(state.coreY - boss.previousY, state.coreX - boss.previousX)
        state.projectiles.forEach { assertTrue(abs(angleBetween(atan2(it.vy, it.vx), aim)) <= 2 * ANTECEDENT_FAN_SPACING + 1e-3f) }
        assertEquals(ANTECEDENT_FAN_SECONDS, boss.actionTimer, 1e-4f)
        boss.abilityAt = state.elapsed
        state.runFor(FIXED)
        assertEquals(EnemyBehavior.TELEGRAPH, boss.mode)
        assertEquals(state.elapsed + ANTECEDENT_PULSE_SECONDS / state.hostileAttackRate(), boss.abilityAt, 1e-3f)
        assertTrue(state.volatileBursts.isEmpty())

        // Nadir, final: a ring of eight embers 170 u out every 6 s and a spiral of fourteen every second.
        state.nextGate(boss)
        state.volatileBursts = immutableListOf()
        boss.abilityAt = state.elapsed
        state.runFor(FIXED)
        val ring = state.volatileBursts
        assertEquals(ANTECEDENT_RING_EMBERS, ring.size)
        ring.forEach {
            val dx = it.x - boss.previousX
            val dy = it.y - boss.previousY
            assertEquals(ANTECEDENT_EMBER_RING_RADIUS, sqrt(dx * dx + dy * dy), 1e-2f)
            assertEquals(PYRE_RING_DELAY, it.detonatesAt - it.armedAt, 1e-4f)
        }
        assertEquals(state.elapsed + ANTECEDENT_EMBER_RING_SECONDS / state.hostileAttackRate(), boss.abilityAt, 1e-3f)
        state.projectiles.clear()
        boss.actionTimer = FIXED * 0.5f
        state.runFor(FIXED)
        assertEquals(ANTECEDENT_FINAL_SPIRAL, state.projectiles.size)
    }

    @Test
    fun anEnragedSecretBossAttacksThirtyFivePercentFaster() {
        val state = encounterState()
        val tag = encounterTag(EncounterOwner.SITE, 0x00F0_0000_0000_0010L)
        assertTrue(state.spawnEncounter(EncounterRequest(tag, state.coreX + 420f, state.coreY, 900f, listOf(EnemyType.CARTOGRAPHER))))
        val carto = state.enemies.single { it.type == EnemyType.CARTOGRAPHER }
        val start = assertNotNull(state.encounter(tag)).startedAt
        state.runFor(FIXED)
        // One step's countdown of its cycle at run time [time], per unit of the run's hostile attack rate.
        fun countdownAt(time: Float): Float {
            state.elapsed = time - FIXED
            carto.mode = EnemyBehavior.IDLE
            carto.actionTimer = 2f
            state.runFor(FIXED)
            return (2f - carto.actionTimer) / state.hostileAttackRate()
        }
        val calm = countdownAt(start + 119f)
        assertEquals(FIXED, calm, 1e-6f)
        assertEquals(calm * 1.35f, countdownAt(start + 121f), 1e-6f, "1.35 times faster after 120 s of its encounter")
    }

    @Test
    fun secretBossesDrawNothingFromTheRunStreams() {
        val state = quietState()
        state.placeObstaclesForTesting(testObstacle(state.coreX - 500f, state.coreY, radius = 80f))
        val carto = state.spawn(EnemyType.CARTOGRAPHER, CARTO_ORBIT_RADIUS, 0f)
        val antecedent = state.spawn(EnemyType.ANTECEDENT, -400f, 300f)
        val spawn = state.spawnRandom.snapshot()
        val gameplay = state.gameplayRandom.snapshot()
        val reward = state.rewardRandom.snapshot()
        // Their adds are cleared every step, so only the two bosses act (an add's own attack is not theirs).
        fun runAlone(seconds: Float) {
            var left = seconds
            while (left > 1e-6f) {
                state.runFor(FIXED)
                state.enemies.removeAll { it !== carto && it !== antecedent }
                left -= FIXED
            }
        }
        runAlone(FIXED)
        for (gate in 0 until 3) {
            runAlone(9f)
            if (gate < 2) state.nextGate(carto)
            state.nextGate(antecedent)
        }
        runAlone(8f)
        assertEquals(2, carto.phase)
        assertEquals(3, antecedent.phase)
        assertTrue(state.projectiles.any { it.kind == ProjectileKind.MINE }, "a mine line was laid")
        assertTrue(state.volatileBursts.isNotEmpty(), "embers were queued")
        assertEquals(spawn, state.spawnRandom.snapshot())
        assertEquals(gameplay, state.gameplayRandom.snapshot())
        assertEquals(reward, state.rewardRandom.snapshot())
    }

    // --- helpers ---

    /** Live adds of each kind in [boss]'s company (every enemy but the boss). */
    private fun MutableGameState.adds(boss: Enemy): Map<EnemyType, Int> =
        enemies.filter { it !== boss && it.alive }.groupingBy { it.type }.eachCount()

    /** Brings [boss] to its next gate with a hit and runs the phase start; [skipShift] then lets the shift end. */
    private fun MutableGameState.nextGate(boss: Enemy, skipShift: Boolean = true) {
        elapsed = maxOf(elapsed, boss.shiftUntil)
        crossGate(boss)
        runFor(FIXED)
        if (skipShift) elapsed = boss.shiftUntil
    }

    /** Brings [boss] to the next gate with a hit, starting its next phase (and its shift). */
    private fun MutableGameState.crossGate(boss: Enemy) {
        val gate = bossGate(boss, assertNotNull(encounterSpec(boss)))
        boss.hp = gate + 5f
        damageEnemy(boss, 20f)
        assertTrue(isShifting(boss), "${boss.type} reached its gate")
    }

    /** A quiet arena with the pointer on the Core; the grace keeps the singularity from ending the run. */
    private fun quietState(seed: Int = 5_150): MutableGameState = buildTestState(seed = seed).apply {
        updatePointer(640f, 360f)
        runGrace = Float.MAX_VALUE
        clearObstaclesForTesting()
    }

    /** A quiet started run where encounters may spawn. */
    private fun encounterState(): MutableGameState = quietState().apply { quietArenaForTesting() }

    private fun MutableGameState.spawn(type: EnemyType, x: Float, y: Float): Enemy {
        val enemy = spawnedEnemy(type, coreX + x, coreY + y)
        enemies += enemy
        return enemy
    }

    /** Steps until [done] holds, at most [limit] seconds. */
    private fun MutableGameState.runUntil(limit: Float = 5f, done: () -> Boolean) {
        var seconds = 0f
        while (!done() && seconds < limit) {
            runFor(FIXED)
            seconds += FIXED
        }
        assertTrue(done(), "reached within $limit seconds")
    }

    /** Enemy steps with the encounter step after them (phase starts, enrage notices), as the run orders them. */
    private fun MutableGameState.runFor(seconds: Float) {
        var left = seconds
        while (left > 1e-6f) {
            elapsed += FIXED
            updateEnemies(FIXED)
            updateBossEncounter()
            left -= FIXED
        }
    }

    private companion object {
        const val FIXED = MutableGameState.FIXED_STEP
        val DEGREE = (PI / 180.0).toFloat()
    }
}
