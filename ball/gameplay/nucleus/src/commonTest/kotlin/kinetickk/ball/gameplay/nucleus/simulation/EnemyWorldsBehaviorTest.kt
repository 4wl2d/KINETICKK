// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.impactTaken
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.model.weaponTaken
import kinetickk.ball.gameplay.nucleus.render.EnemyBehavior
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The space and structure kinds of 0.6.0: Comet, Pylon, Sentry and Crawler, and the shared flock. */
class EnemyWorldsBehaviorTest {
    @Test
    fun aCometMarksALanePastTheCoresLeadPointStreaksAlongItRecoversAndCoolsDown() {
        val state = quietState().apply { setVelocityForTesting(300f, -120f) }
        val comet = state.spawn(EnemyType.COMET, 500f, 0f)
        // Its first lane waits a second after it arrives; meanwhile it holds its band and circles.
        state.runEnemiesFor(0.9f)
        assertEquals(EnemyBehavior.IDLE, comet.mode)
        assertTrue(state.distanceToCore(comet) in 420f..620f, "it keeps 420-620 u out: ${state.distanceToCore(comet)}")

        comet.abilityAt = 0f
        val startX = comet.x
        val startY = comet.y
        state.runEnemiesFor(FIXED)
        assertEquals(EnemyBehavior.TELEGRAPH, comet.mode)
        assertEquals(COMET_TELEGRAPH_SECONDS, state.behaviorRemaining(comet), 0.02f)
        // The lane runs through a point beside the Core's lead point, the lane offset across the Core's bearing on
        // the side the Comet circles, so it misses a Core that holds its course.
        val leadX = state.coreX + state.velocityX * COMET_LEAD_SECONDS
        val leadY = state.coreY + state.velocityY * COMET_LEAD_SECONDS
        val toCoreX = (state.coreX - startX) / state.distanceToCore(startX, startY)
        val toCoreY = (state.coreY - startY) / state.distanceToCore(startX, startY)
        val side = if (comet.id % 2 == 0) 1f else -1f
        val aimX = leadX - toCoreY * COMET_LANE_OFFSET * side
        val aimY = leadY + toCoreX * COMET_LANE_OFFSET * side
        assertEquals(atan2(aimY - startY, aimX - startX), comet.facing, 1e-5f)
        val miss = abs((leadX - startX) * sin(comet.facing) - (leadY - startY) * cos(comet.facing))
        assertTrue(miss in COMET_LANE_OFFSET * 0.5f..COMET_LANE_OFFSET, "the lane misses the lead point: $miss")

        val lane = comet.facing
        state.runEnemiesFor(COMET_TELEGRAPH_SECONDS - 0.05f)
        assertEquals(EnemyBehavior.TELEGRAPH, comet.mode)
        assertEquals(lane, comet.facing, "the lane is locked while it shows")
        assertTrue(state.distanceToCore(comet) > 400f && abs(comet.x - startX) < 25f && abs(comet.y - startY) < 25f, "it settles while marking")

        state.runUntil { comet.mode == EnemyBehavior.STRIKE }
        assertEquals(COMET_SPEED * state.rebirthProfile.enemySpeedMultiplier, sqrt(comet.vx * comet.vx + comet.vy * comet.vy), 1f)
        assertEquals(lane, atan2(comet.vy, comet.vx), 1e-4f)
        // Its contact stays ordinary: a fast Core rams it freely.
        assertEquals(0f, state.strikeDamage(comet))
        val strikeX = comet.previousX
        val strikeY = comet.previousY
        val struckAt = state.elapsed
        state.runUntil { comet.mode != EnemyBehavior.STRIKE }
        assertEquals(EnemyBehavior.RECOVERY, comet.mode)
        assertEquals(COMET_LANE_LENGTH / COMET_SPEED, state.elapsed - struckAt, 0.02f, "the streak lasts its lane")
        val run = sqrt((comet.previousX - strikeX) * (comet.previousX - strikeX) + (comet.previousY - strikeY) * (comet.previousY - strikeY))
        assertEquals(COMET_LANE_LENGTH, run, 15f, "it streaks the lane's length along it")
        assertEquals(lane, atan2(comet.previousY - strikeY, comet.previousX - strikeX), 1e-3f)

        val recoveredAt = state.elapsed
        state.runUntil { comet.mode == EnemyBehavior.IDLE }
        assertEquals(COMET_RECOVERY_SECONDS, state.elapsed - recoveredAt, 0.02f)
        assertEquals(state.elapsed + COMET_COOLDOWN_SECONDS / state.hostileAttackRate(), comet.abilityAt, 1e-3f)
        assertTrue(comet.abilityAt > state.elapsed + 1f, "a cooldown follows the recovery")
    }

    @Test
    fun aSilencedCometRecoversInsteadOfStreaking() {
        val state = quietState()
        val comet = state.spawn(EnemyType.COMET, 500f, 0f).apply { abilityAt = 0f }
        state.runEnemiesFor(FIXED)
        assertEquals(EnemyBehavior.TELEGRAPH, comet.mode)
        comet.silencedUntil = Float.MAX_VALUE
        state.runEnemiesFor(COMET_TELEGRAPH_SECONDS + 0.05f)
        assertEquals(EnemyBehavior.RECOVERY, comet.mode)
        assertTrue(sqrt(comet.vx * comet.vx + comet.vy * comet.vy) < 50f)
    }

    @Test
    fun aCometStreakingIntoAnObstacleSlamsItselfAndIsNeverStunned() {
        val state = quietState()
        val rock = testObstacle(state.coreX + 600f, state.coreY + 300f, radius = 70f)
        state.placeObstaclesForTesting(rock)
        val comet = state.spawn(EnemyType.COMET, 300f, 300f).apply {
            hp = 1_000f
            mode = EnemyBehavior.STRIKE
            modeUntil = state.elapsed + 2f
            facing = 0f
        }
        var steps = 0
        while (comet.hp >= 1_000f && steps < 120) {
            state.runEnemiesFor(FIXED)
            steps++
        }
        val runScale = state.runScale()
        val expected = (COMET_SPEED - state.content.obstacles.slamDamageOffset) * state.content.obstacles.slamDamagePerSpeed *
            runScale * comet.weaponTaken
        assertEquals(expected, 1_000f - comet.hp, expected * 1e-3f, "a head-on contact is an obstacle slam at its streak speed")
        assertTrue(comet.slamReadyAt > state.elapsed, "the slam landed and fired its trigger")
        assertEquals(0f, comet.disabledUntil, "no Lancer stun")
        assertEquals(EnemyBehavior.STRIKE, comet.mode)
        assertTrue(sqrt((comet.x - rock.x) * (comet.x - rock.x) + (comet.y - rock.y) * (comet.y - rock.y)) >= rock.radius + comet.radius - 0.01f)

        // At its own integrity the slam shatters it.
        val fresh = quietState()
        fresh.placeObstaclesForTesting(testObstacle(fresh.coreX + 600f, fresh.coreY + 300f, radius = 70f))
        val fragile = fresh.spawn(EnemyType.COMET, 300f, 300f).apply {
            mode = EnemyBehavior.STRIKE
            modeUntil = fresh.elapsed + 2f
            facing = 0f
        }
        fresh.runEnemiesFor(0.5f)
        assertFalse(fragile.alive, "an obstacle in its path shatters it")
    }

    @Test
    fun aPylonDocksOnTheCoreSideOfTheNearestRockAndFiresAnAimedVolley() {
        val state = quietState()
        val rock = testObstacle(state.coreX, state.coreY + 400f, radius = 80f)
        // A farther rock it must not prefer.
        state.placeObstaclesForTesting(rock, testObstacle(state.coreX + 500f, state.coreY + 900f, radius = 80f))
        val pylon = state.spawn(EnemyType.PYLON, 260f, 330f)
        assertEquals(EnemyBehavior.IDLE, pylon.mode)
        var seconds = 0f
        while (pylon.mode == EnemyBehavior.IDLE && seconds < PYLON_SEEK_SECONDS) {
            state.runEnemiesFor(FIXED)
            seconds += FIXED
        }
        assertEquals(EnemyBehavior.ANCHORED, pylon.mode)
        assertTrue(seconds < PYLON_SEEK_SECONDS - 0.5f, "it reached its dock before the seek ran out ($seconds s)")
        val dockX = rock.x
        val dockY = rock.y - (rock.radius + pylon.radius + PYLON_DOCK_GAP)
        assertTrue(abs(pylon.x - dockX) < PYLON_DOCK_TOLERANCE + 3f && abs(pylon.y - dockY) < PYLON_DOCK_TOLERANCE + 3f,
            "docked on the rock's Core side at (${pylon.x - state.coreX}, ${pylon.y - state.coreY})")
        val mountX = pylon.x
        val mountY = pylon.y

        // Mounted it never moves; after a second it shows its sight line and fires.
        state.runEnemiesFor(PYLON_MOUNT_SECONDS - 0.1f)
        assertEquals(EnemyBehavior.ANCHORED, pylon.mode)
        assertEquals(atan2(state.coreY - pylon.y, state.coreX - pylon.x), pylon.facing, 1e-5f, "mounted, it faces the Core")
        state.runUntil { pylon.mode == EnemyBehavior.TELEGRAPH }
        state.runEnemiesFor(PYLON_TELEGRAPH_SECONDS - PYLON_LOCK_SECONDS - 0.05f)
        assertTrue(state.projectiles.isEmpty(), "no shot during the aim")
        // The sight line follows the Core until the last fifth of a second, then it is locked.
        state.coreX += 60f
        state.runEnemiesFor(FIXED)
        assertEquals(atan2(state.coreY - pylon.y, state.coreX - pylon.x), pylon.facing, 1e-5f, "it still tracks")
        state.runEnemiesFor(0.1f)
        val locked = pylon.facing
        state.coreX += 120f
        state.runEnemiesFor(FIXED)
        assertEquals(locked, pylon.facing, "locked")
        assertFalse(state.content.tempo.denseAttacks(state.threatElapsed))
        state.runUntil { state.projectiles.isNotEmpty() }
        assertEquals(EnemyBehavior.ANCHORED, pylon.mode)
        assertEquals(PYLON_VOLLEY, state.projectiles.size)
        val angles = state.projectiles.map { atan2(it.vy, it.vx) }
        state.projectiles.forEach { shot ->
            assertEquals(PYLON_SHOT_SPEED * state.rebirthProfile.enemySpeedMultiplier, sqrt(shot.vx * shot.vx + shot.vy * shot.vy), 0.5f)
            assertEquals(state.shotDamage(pylon), shot.damage)
        }
        assertEquals(locked, angles[1], 1e-4f, "the volley is centred on the locked sight line")
        assertEquals(PYLON_VOLLEY_SPACING, angles[1] - angles[0], 1e-4f)
        assertEquals(PYLON_VOLLEY_SPACING, angles[2] - angles[1], 1e-4f)
        assertEquals(state.elapsed + PYLON_COOLDOWN_SECONDS / state.hostileAttackRate(), pylon.abilityAt, 1e-3f)
        assertEquals(mountX, pylon.x)
        assertEquals(mountY, pylon.y)
    }

    @Test
    fun aPylonWithoutARockNearMountsWhereItStandsAndARockHidesTheCoreFromIt() {
        val state = quietState()
        state.clearObstaclesForTesting()
        val pylon = state.spawn(EnemyType.PYLON, 500f, 0f)
        state.runEnemiesFor(FIXED)
        assertEquals(EnemyBehavior.ANCHORED, pylon.mode)
        assertEquals(state.coreX + 500f, pylon.x, 0.01f)
        assertEquals(0f, pylon.vx)
        assertEquals(state.elapsed + PYLON_MOUNT_SECONDS, pylon.abilityAt, 0.02f)

        // The seek also ends when its time runs out, wherever it is.
        val late = quietState()
        late.placeObstaclesForTesting(testObstacle(late.coreX + 1_000f, late.coreY, radius = 80f))
        val seeker = late.spawn(EnemyType.PYLON, 500f, 0f).apply { modeUntil = late.elapsed + 0.3f }
        late.runEnemiesFor(0.2f)
        assertEquals(EnemyBehavior.IDLE, seeker.mode)
        late.runEnemiesFor(0.15f)
        assertEquals(EnemyBehavior.ANCHORED, seeker.mode)

        // A rock between the Pylon and the Core blocks its sight line.
        val blocked = quietState()
        blocked.placeObstaclesForTesting(testObstacle(blocked.coreX + 250f, blocked.coreY, radius = 60f))
        val hidden = blocked.spawn(EnemyType.PYLON, 500f, 0f).apply {
            mode = EnemyBehavior.ANCHORED
            abilityAt = 0f
        }
        blocked.runEnemiesFor(2f)
        assertEquals(EnemyBehavior.ANCHORED, hidden.mode)
        assertTrue(blocked.projectiles.isEmpty())
    }

    @Test
    fun slamsMeetADockedPylonsImpactFrameAndAThrownEnemySlamsBoth() {
        val state = quietState()
        state.clearObstaclesForTesting()
        val pylon = state.spawn(EnemyType.PYLON, 500f, 0f).apply {
            hp = 2_000f
        }
        state.runEnemiesFor(FIXED)
        assertEquals(EnemyBehavior.ANCHORED, pylon.mode)
        val obstacles = state.content.obstacles
        val raw = (600f - obstacles.slamDamageOffset) * obstacles.slamDamagePerSpeed * state.runScale()
        assertTrue(state.applySlamDamage(pylon, 600f, polarity = false))
        // ×3.2 on its 0.5 weapon frame: the slam meets its 1.6 impact frame.
        assertEquals(raw * pylon.impactTaken, 2_000f - pylon.hp, raw * 1e-3f)
        assertEquals(EnemyType.PYLON.profile.slamTaken * pylon.weaponTaken, pylon.impactTaken, 1e-4f)

        // A thrown Drifter hitting the docked Pylon slams both; the Pylon stays on its mount.
        state.elapsed += 1f
        pylon.hp = 2_000f
        val mountX = pylon.x
        val drifter = state.spawn(EnemyType.DRIFTER, 500f - pylon.radius - EnemyType.DRIFTER.profile.radius - 2f, 0f).apply {
            hp = 2_000f
            vx = 700f
            vy = 0f
        }
        state.markThrown(drifter)
        state.runEnemiesFor(FIXED)
        val pylonLoss = 2_000f - pylon.hp
        val drifterLoss = 2_000f - drifter.hp
        assertTrue(pylonLoss > 0f && drifterLoss > 0f, "both took the slam ($pylonLoss, $drifterLoss)")
        assertEquals(pylon.impactTaken / drifter.weaponTaken, pylonLoss / drifterLoss, 1e-3f)
        state.runEnemiesFor(0.5f)
        assertEquals(mountX, pylon.x, 0.5f)
        assertTrue(pylon.mode == EnemyBehavior.ANCHORED || pylon.mode == EnemyBehavior.TELEGRAPH, "still on its mount")
    }

    @Test
    fun aLeashedPylonOrSentryLeavesItsMountAndSeeksAgain() {
        val state = quietState()
        state.clearObstaclesForTesting()
        val pylon = state.spawn(EnemyType.PYLON, 300f, 0f)
        val sentry = state.spawn(EnemyType.SENTRY, -300f, 0f, ownerTag = 0L)
        val drifter = state.spawn(EnemyType.DRIFTER, 0f, 300f).apply { mode = EnemyBehavior.STRIKE }
        state.runEnemiesFor(FIXED)
        sentry.mode = EnemyBehavior.ANCHORED
        assertEquals(EnemyBehavior.ANCHORED, pylon.mode)
        listOf(pylon, sentry, drifter).forEach(state::relocateLeashedEnemy)
        assertEquals(EnemyBehavior.IDLE, pylon.mode)
        assertEquals(state.elapsed + PYLON_SEEK_SECONDS, pylon.modeUntil)
        assertEquals(EnemyBehavior.IDLE, sentry.mode)
        assertEquals(EnemyBehavior.STRIKE, drifter.mode, "other kinds keep their mode")

        // Through the step: a mounted Pylon left beyond the leash is moved ahead and seeks a new rock.
        val left = quietState()
        left.clearObstaclesForTesting()
        val far = left.spawn(EnemyType.PYLON, 300f, 0f)
        left.runEnemiesFor(FIXED)
        assertEquals(EnemyBehavior.ANCHORED, far.mode)
        far.x = left.coreX + 4_000f
        left.runEnemiesFor(FIXED)
        assertEquals(EnemyBehavior.IDLE, far.mode)
        assertTrue(left.distanceToCore(far) < 2_000f)
    }

    @Test
    fun aSentryTurnsOntoTheCoreAndFiresABoltTrainAlongAClearSightLine() {
        val state = quietState()
        state.clearObstaclesForTesting()
        // Its encounter placed it: it starts mounted, facing away from the Core.
        val sentry = state.spawn(EnemyType.SENTRY, 500f, 0f, ownerTag = 9L).apply { facing = 0f }
        assertEquals(EnemyBehavior.ANCHORED, sentry.mode)
        val turn = (PI.toFloat() - SENTRY_AIM_TOLERANCE) / SENTRY_TURN_RATE
        val mountedAt = state.elapsed
        state.runEnemiesFor(turn - 0.1f)
        assertEquals(EnemyBehavior.ANCHORED, sentry.mode, "it turns at its rate before it can aim")
        assertTrue(abs(sentry.facing) in 0.2f..PI.toFloat() - 0.2f, "still turning: ${sentry.facing}")
        state.runUntil { sentry.mode == EnemyBehavior.TELEGRAPH }
        assertTrue(state.elapsed - mountedAt < turn + 0.1f, "aimed within its tolerance after the turn (${state.elapsed - mountedAt} s)")
        val aim = sentry.facing
        val aimedAt = state.elapsed
        state.runEnemiesFor(SENTRY_TELEGRAPH_SECONDS - 0.05f)
        assertEquals(aim, sentry.facing, "the aim is locked while it shows")
        assertTrue(state.projectiles.isEmpty())
        state.runUntil { state.projectiles.isNotEmpty() }
        assertEquals(SENTRY_TELEGRAPH_SECONDS, state.elapsed - aimedAt, 0.02f)
        assertEquals(EnemyBehavior.ANCHORED, sentry.mode)
        assertEquals(SENTRY_TRAIN, state.projectiles.size)
        state.projectiles.forEachIndexed { index, bolt ->
            val offset = sentry.radius + 4f - SENTRY_BOLT_SPACING * index
            assertEquals(sentry.x + cos(aim) * offset, bolt.x, 0.01f)
            assertEquals(sentry.y + sin(aim) * offset, bolt.y, 0.01f)
            assertEquals(cos(aim) * SENTRY_BOLT_SPEED * state.rebirthProfile.enemySpeedMultiplier, bolt.vx, 0.01f)
            assertEquals(sin(aim) * SENTRY_BOLT_SPEED * state.rebirthProfile.enemySpeedMultiplier, bolt.vy, 0.01f)
            assertEquals(SENTRY_BOLT_RADIUS, bolt.radius)
            assertEquals(SENTRY_BOLT_LIFE, bolt.life)
            assertEquals(state.shotDamage(sentry), bolt.damage)
        }
        assertEquals(state.elapsed + SENTRY_COOLDOWN_SECONDS / state.hostileAttackRate(), sentry.abilityAt, 1e-3f)
        assertEquals(state.coreX + 500f, sentry.x, "a mounted Sentry never moves")

        // A rock across its sight line keeps it silent however well it aims.
        val blocked = quietState()
        blocked.placeObstaclesForTesting(testObstacle(blocked.coreX + 250f, blocked.coreY, radius = 60f))
        val hidden = blocked.spawn(EnemyType.SENTRY, 500f, 0f, ownerTag = 9L)
        blocked.runEnemiesFor(3f)
        assertEquals(EnemyBehavior.ANCHORED, hidden.mode)
        assertTrue(blocked.projectiles.isEmpty())
        // And out of range it waits.
        val far = quietState()
        far.clearObstaclesForTesting()
        val distant = far.spawn(EnemyType.SENTRY, SENTRY_SIGHT_RANGE + 20f, 0f, ownerTag = 9L)
        far.runEnemiesFor(2f)
        assertEquals(EnemyBehavior.ANCHORED, distant.mode)
    }

    @Test
    fun anAmbientSentryWalksInAndMountsInsideItsDistance() {
        val state = quietState()
        state.clearObstaclesForTesting()
        val sentry = state.spawn(EnemyType.SENTRY, 800f, 0f, ownerTag = 0L)
        assertEquals(EnemyBehavior.IDLE, sentry.mode)
        var seconds = 0f
        while (sentry.mode == EnemyBehavior.IDLE && seconds < 10f) {
            state.runEnemiesFor(FIXED)
            seconds += FIXED
        }
        assertEquals(EnemyBehavior.ANCHORED, sentry.mode)
        val mounted = state.distanceToCore(sentry)
        assertTrue(mounted in SENTRY_MOUNT_DISTANCE - 5f..SENTRY_MOUNT_DISTANCE, "mounted $mounted u out")
        assertTrue(seconds in 2f..6f, "walked in at about 90 u/s ($seconds s)")
        val x = sentry.x
        state.runEnemiesFor(0.5f)
        assertEquals(x, sentry.x)
    }

    @Test
    fun aCrawlerCreepsAlongTheNearestSurfaceTowardTheCoreSideAndWaitsThere() {
        val state = quietState()
        val rockX = state.coreX + 600f
        val rockY = state.coreY
        state.placeObstaclesForTesting(testObstacle(rockX, rockY, radius = 100f))
        // On top of the rock, a quarter turn from the side that faces the Core.
        val crawler = state.spawn(EnemyType.CRAWLER, 600f, 120f)
        fun angle() = atan2(crawler.y - rockY, crawler.x - rockX)
        fun gap() = sqrt((crawler.x - rockX) * (crawler.x - rockX) + (crawler.y - rockY) * (crawler.y - rockY)) - 100f
        val start = angle()
        assertEquals(PI.toFloat() / 2f, start, 1e-3f)
        repeat(120) {
            state.runEnemiesFor(FIXED)
            assertTrue(gap() in crawler.radius - 0.5f..CRAWLER_HUG_GAP + 30f, "it hugs the surface: gap ${gap()}")
        }
        assertTrue(angle() > start + 0.35f, "it creeps around toward the Core's side: ${angle()}")
        state.runEnemiesFor(3f)
        assertTrue(abs(angle()) > PI.toFloat() - 0.3f, "it waits on the side facing the Core: ${angle()}")
        assertTrue(gap() < CRAWLER_HUG_GAP + 30f)
    }

    @Test
    fun aCrawlerLungesInsideItsDistanceAndFlocksOnlyWithCrawlersInTheOpen() {
        val state = quietState()
        state.clearObstaclesForTesting()
        val crawler = state.spawn(EnemyType.CRAWLER, 150f, 0f)
        state.runEnemiesFor(0.5f)
        assertTrue(crawler.vx < -CRAWLER_LUNGE_SPEED * 0.75f, "it lunges at the Core: ${crawler.vx}")
        assertEquals(0f, crawler.vy, 1f)

        // In the open it flocks: a Crawler pushes its kin apart, a Drifter beside it does not.
        fun velocityBeside(type: EnemyType): Pair<Float, Float> {
            val open = quietState()
            open.clearObstaclesForTesting()
            val lone = open.spawn(EnemyType.CRAWLER, 600f, 0f)
            open.spawn(type, 600f, 12f)
            open.updateCrawler(lone, open.coreX - lone.x, open.coreY - lone.y, open.distanceToCore(lone), FIXED)
            return lone.vx to lone.vy
        }
        val alone = run {
            val open = quietState()
            open.clearObstaclesForTesting()
            val lone = open.spawn(EnemyType.CRAWLER, 600f, 0f)
            open.updateCrawler(lone, open.coreX - lone.x, open.coreY - lone.y, open.distanceToCore(lone), FIXED)
            lone.vx to lone.vy
        }
        assertEquals(alone.first.toRawBits(), velocityBeside(EnemyType.DRIFTER).first.toRawBits())
        assertEquals(alone.second.toRawBits(), velocityBeside(EnemyType.DRIFTER).second.toRawBits())
        assertTrue(velocityBeside(EnemyType.CRAWLER).second < alone.second, "its kin pushes it away")
    }

    @Test
    fun theSwarmlingFlocksBitForBitAsReleased() {
        for (tier in listOf(0, 5)) {
            val flocked = crowd(tier)
            val released = crowd(tier)
            for (index in flocked.enemies.indices) {
                val enemy = flocked.enemies[index]
                if (enemy.type != EnemyType.SWARMLING) continue
                val dx = flocked.coreX - enemy.x
                val dy = flocked.coreY - enemy.y
                val distance = kotlin.math.max(1f, sqrt(dx * dx + dy * dy))
                flocked.updateSwarmling(enemy, dx, dy, distance, FIXED)
                released.releasedSwarmling(released.enemies[index], dx, dy, distance, FIXED)
                assertEquals(released.enemies[index].vx.toRawBits(), enemy.vx.toRawBits(), "tier $tier swarmling $index vx")
                assertEquals(released.enemies[index].vy.toRawBits(), enemy.vy.toRawBits(), "tier $tier swarmling $index vy")
            }
        }
    }

    @Test
    fun theNewKindsMoveTheSameWayEveryRunOfASeed() {
        fun trace(): List<Int> {
            val state = quietState()
            state.placeObstaclesForTesting(testObstacle(state.coreX + 400f, state.coreY - 200f, radius = 90f))
            state.spawn(EnemyType.COMET, 520f, 80f)
            state.spawn(EnemyType.PYLON, 300f, -380f)
            state.spawn(EnemyType.SENTRY, -700f, 0f, ownerTag = 0L)
            repeat(4) { state.spawn(EnemyType.CRAWLER, 380f + it * 20f, -60f) }
            state.runEnemiesFor(4f)
            return state.enemies.flatMap { listOf(it.x.toRawBits(), it.y.toRawBits(), it.mode) }
        }
        assertEquals(trace(), trace())
        assertNotEquals(0, trace().size)
    }

    /** Swarmlings in a tight knot (some coincident) among other kinds, for the released-formula comparison. */
    private fun crowd(tier: Int): MutableGameState = quietState(tier).apply {
        clearObstaclesForTesting()
        val types = listOf(EnemyType.SWARMLING, EnemyType.SWARMLING, EnemyType.DRIFTER, EnemyType.SWARMLING, EnemyType.CRAWLER)
        for (index in 0 until 40) {
            val x = 400f + (index % 7) * 13.7f + if (index % 11 == 0) 0f else (index * 7 % 5) * 3.1f
            val y = (index / 7) * 17.3f - 40f
            spawn(types[index % types.size], x, y).apply {
                vx = (index % 5) * 23f - 40f
                vy = (index % 3) * 31f - 30f
            }
        }
        // Two exactly coincident Swarmlings and one exactly at the spacing.
        spawn(EnemyType.SWARMLING, 400f, -40f)
        spawn(EnemyType.SWARMLING, 434f, -40f)
    }

    /** The 0.5.0 Swarmling step, kept here verbatim as the reference for the shared flock. */
    private fun MutableGameState.releasedSwarmling(enemy: Enemy, dx: Float, dy: Float, distance: Float, delta: Float) {
        val swirl = if (enemy.id % 2 == 0) 1f else -1f
        var separationX = 0f
        var separationY = 0f
        for (index in enemies.indices) {
            val other = enemies[index]
            if (other === enemy || other.type != EnemyType.SWARMLING || other.dead) continue
            val ox = enemy.x - other.x
            val oy = enemy.y - other.y
            val squared = ox * ox + oy * oy
            if (squared >= 34f * 34f || squared < 1e-4f) continue
            val apart = kotlin.math.sqrt(squared)
            val push = (34f - apart) / 34f
            separationX += ox / apart * push
            separationY += oy / apart * push
        }
        steerEnemy(
            enemy,
            dx / distance * 150f - dy / distance * 42f * swirl + separationX * 110f,
            dy / distance * 150f + dx / distance * 42f * swirl + separationY * 110f,
            3.2f,
            delta,
        )
    }

    /** A quiet arena with the pointer on the Core; the grace keeps the singularity from ending the run. */
    private fun quietState(tier: Int = 0): MutableGameState = buildTestState(rebirth = tier).apply {
        updatePointer(640f, 360f)
        runGrace = Float.MAX_VALUE
        clearObstaclesForTesting()
    }

    private fun MutableGameState.spawn(type: EnemyType, x: Float, y: Float, ownerTag: Long = 0L): Enemy {
        val enemy = spawnedEnemy(type, coreX + x, coreY + y, ownerTag = ownerTag)
        enemies += enemy
        return enemy
    }

    private fun MutableGameState.distanceToCore(enemy: Enemy): Float = distanceToCore(enemy.x, enemy.y)

    private fun MutableGameState.distanceToCore(x: Float, y: Float): Float = sqrt((x - coreX) * (x - coreX) + (y - coreY) * (y - coreY))

    /** Steps until [done] holds, at most five seconds. */
    private fun MutableGameState.runUntil(done: () -> Boolean) {
        var seconds = 0f
        while (!done() && seconds < 5f) {
            runEnemiesFor(FIXED)
            seconds += FIXED
        }
        assertTrue(done(), "reached within five seconds")
    }

    private fun MutableGameState.runEnemiesFor(seconds: Float) {
        var left = seconds
        while (left > 0f) {
            elapsed += FIXED
            updateEnemies(FIXED)
            left -= FIXED
        }
    }

    private companion object {
        const val FIXED = MutableGameState.FIXED_STEP
    }
}
