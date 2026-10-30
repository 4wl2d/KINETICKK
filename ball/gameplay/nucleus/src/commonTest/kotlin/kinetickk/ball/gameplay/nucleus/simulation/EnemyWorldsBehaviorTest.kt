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
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The 0.6.0 kinds: Comet, Pylon, Sentry and Crawler (space and structures), Seraph, Choir, Imp, Hound, Cipher and Mirror
 * (dimensions), the shared flock and the ember queue.
 */
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
            state.spawn(EnemyType.SERAPH, -420f, 300f)
            state.spawn(EnemyType.CHOIR, 0f, 520f)
            state.spawn(EnemyType.IMP, -500f, -200f)
            repeat(3) { state.spawn(EnemyType.HOUND, 200f - it * 30f, 420f) }
            state.spawn(EnemyType.CIPHER, -300f, -380f)
            state.spawn(EnemyType.MIRROR, 600f, 300f)
            state.runEnemiesFor(4f)
            return state.enemies.flatMap { listOf(it.x.toRawBits(), it.y.toRawBits(), it.mode) }
        }
        assertEquals(trace(), trace())
        assertNotEquals(0, trace().size)
    }

    @Test
    fun aSeraphMendsItsTwoNearestAlliesAndShieldsThemAgainstAllButImpacts() {
        val state = quietState()
        val seraph = state.spawn(EnemyType.SERAPH, 450f, 0f).apply { actionTimer = 0f }
        fun hurt(type: EnemyType, x: Float, y: Float, share: Float = 0.5f) = state.spawn(type, x, y).apply { hp = maxHp * share }
        // Nearer than both mended allies, but never mended: another Seraph, a shifting enemy, a mini-boss and a secret boss.
        val kin = hurt(EnemyType.SERAPH, 450f, 30f)
        val shifting = hurt(EnemyType.DRIFTER, 470f, -20f).apply { shiftUntil = Float.MAX_VALUE }
        val prime = hurt(EnemyType.WARDEN_PRIME, 420f, 40f)
        val cartographer = hurt(EnemyType.CARTOGRAPHER, 400f, -40f)
        val near = hurt(EnemyType.DRIFTER, 450f, 60f)
        val next = hurt(EnemyType.SHOOTER, 450f, -120f, share = 0.97f)
        val third = hurt(EnemyType.CHARGER, 450f, 200f)
        val far = hurt(EnemyType.DRIFTER, 450f + SERAPH_LINK_RANGE + 40f, 0f)
        val untouched = listOf(kin, shifting, prime, cartographer, third, far).map { it to it.hp }

        state.updateSeraph(seraph, state.coreX - seraph.x, state.coreY - seraph.y, state.distanceToCore(seraph), FIXED, FIXED)
        assertEquals(near.maxHp * (0.5f + SERAPH_HEAL_SHARE), near.hp, 1e-3f, "it mends 6 % of the ally's integrity")
        assertEquals(next.maxHp, next.hp, "never past full integrity")
        for ((ally, hp) in untouched) {
            assertEquals(hp, ally.hp, "${ally.type} at ${ally.x - seraph.x}, ${ally.y - seraph.y} is not mended")
            assertFalse(state.shielded(ally), "${ally.type} is not shielded")
        }
        for (shielded in listOf(near, next, seraph)) {
            assertEquals(state.elapsed + SERAPH_SHIELD_SECONDS, shielded.shieldUntil, 1e-4f, "${shielded.type} is shielded")
        }
        assertEquals(SERAPH_PULSE_SECONDS, seraph.actionTimer)

        // The shield stops a weapon and is spent by an impact, which lands.
        val mended = near.hp
        assertEquals(0f, state.dealHit(near, 20f, HitSource.WEAPON, canCrit = false).amount)
        assertEquals(mended, near.hp)
        assertTrue(state.shielded(near))
        assertTrue(state.dealHit(near, 20f, HitSource.IMPACT, canCrit = false).amount > 0f)
        assertTrue(near.hp < mended)
        assertFalse(state.shielded(near), "an impact breaks it")

        // A silenced Seraph's pulse passes without a mend.
        val silent = quietState()
        val quiet = silent.spawn(EnemyType.SERAPH, 450f, 0f).apply { actionTimer = 0f; silencedUntil = Float.MAX_VALUE }
        val ally = silent.spawn(EnemyType.DRIFTER, 450f, 40f).apply { hp = maxHp * 0.5f }
        silent.updateSeraph(quiet, silent.coreX - quiet.x, silent.coreY - quiet.y, silent.distanceToCore(quiet), FIXED, FIXED)
        assertEquals(ally.maxHp * 0.5f, ally.hp)
        assertFalse(silent.shielded(quiet) || silent.shielded(ally))
        assertEquals(SERAPH_PULSE_SECONDS, quiet.actionTimer, "its cadence restarts")

        // It weaves in from afar and keeps to mid range.
        val roaming = quietState()
        val weaver = roaming.spawn(EnemyType.SERAPH, 650f, 0f)
        roaming.runEnemiesFor(5f)
        assertTrue(roaming.distanceToCore(weaver) in 340f..560f, "it holds mid range: ${roaming.distanceToCore(weaver)}")
    }

    @Test
    fun aChoirPulseFiresARingOfBoltsAndShovesOnlyACoreInsideItsWave() {
        /** One pulse from a Choir [at] u east of the Core: (bolts fired, the Core's velocity change along and across the shove line). */
        fun pulse(at: Float, dashing: Boolean = false, silenced: Boolean = false): Pair<Int, Pair<Float, Float>> {
            val state = quietState()
            state.setVelocityForTesting(40f, -30f)
            if (dashing) state.dashPhaseTime = 10f
            val choir = state.spawn(EnemyType.CHOIR, at, 0f).apply { actionTimer = 0f }
            state.runEnemiesFor(FIXED)
            assertEquals(EnemyBehavior.TELEGRAPH, choir.mode, "it sings before the pulse")
            assertEquals(CHOIR_TELEGRAPH_SECONDS, state.behaviorRemaining(choir), 0.02f)
            if (silenced) choir.silencedUntil = Float.MAX_VALUE
            val before = state.projectiles.size
            val start = state.elapsed
            state.runUntil { choir.mode == EnemyBehavior.IDLE }
            assertEquals(CHOIR_TELEGRAPH_SECONDS, state.elapsed - start, 0.02f)
            val fired = state.projectiles.size - before
            if (fired > 0) {
                val bolt = state.projectiles.last()
                assertEquals(CHOIR_SHOT_SPEED * state.rebirthProfile.enemySpeedMultiplier, sqrt(bolt.vx * bolt.vx + bolt.vy * bolt.vy), 0.01f)
            }
            // The pulse resolved from where the Choir stood when its last step began.
            val awayX = state.coreX - choir.previousX
            val awayY = state.coreY - choir.previousY
            val away = sqrt(awayX * awayX + awayY * awayY)
            val changeX = state.velocityX - 40f
            val changeY = state.velocityY + 30f
            return fired to ((changeX * awayX + changeY * awayY) / away to (changeY * awayX - changeX * awayY) / away)
        }
        val (closeBolts, shoved) = pulse(200f)
        assertEquals(CHOIR_VOLLEY, closeBolts)
        assertEquals(CHOIR_WAVE_PUSH, shoved.first, 1e-2f, "a Core inside the wave is shoved straight away from the Choir")
        assertEquals(0f, shoved.second, 1e-2f)
        for ((case, outcome) in listOf(
            "outside the wave only the bolts reach out" to pulse(CHOIR_WAVE_RADIUS + 140f),
            "a dash phase slips the shove" to pulse(200f, dashing = true),
            "a silenced pulse does nothing" to pulse(200f, silenced = true),
        )) {
            assertEquals(if (case.startsWith("a silenced")) 0 else CHOIR_VOLLEY, outcome.first, case)
            assertEquals(0f, abs(outcome.second.first) + abs(outcome.second.second), case)
        }
    }

    @Test
    fun anImpPrimesBurstsWithoutAKillAndLeavesThreeEmbersAcrossItsHeading() {
        val state = quietState()
        val imp = state.spawn(EnemyType.IMP, 80f, 0f)
        state.runEnemiesFor(FIXED)
        assertEquals(EnemyBehavior.PRIMED, imp.mode)
        val kills = state.kills
        val blast = state.hostileDamage(imp, IMP_BLAST_DAMAGE)
        val primedAt = state.elapsed
        state.runUntil { imp !in state.enemies }
        assertEquals(IMP_PRIME_SECONDS, state.elapsed - primedAt, 0.02f)
        assertTrue(imp.dead)
        assertEquals(kills, state.kills, "a burst is not a kill")
        assertEquals(state.maxHp - blast, state.hp, 0.05f, "the burst hurts a Core within its reach")

        val embers = state.volatileBursts
        assertEquals(3, embers.size)
        val (centre, left, right) = embers
        assertEquals(imp.x, centre.x, 0.5f)
        assertEquals(imp.y, centre.y, 0.5f)
        for ((ember, delay) in embers.zip(listOf(IMP_EMBER_SECONDS, IMP_LEFT_EMBER_SECONDS, IMP_RIGHT_EMBER_SECONDS))) {
            assertEquals(state.elapsed + delay, ember.detonatesAt, 1e-4f)
        }
        assertTrue(embers.all { it.radius == VOLATILE_BURST_RADIUS && it.armedAt == state.elapsed })
        // The side embers sit on either side of the centre one, across the line to the Core.
        assertEquals(centre.x, (left.x + right.x) * 0.5f, 1e-3f)
        assertEquals(centre.y, (left.y + right.y) * 0.5f, 1e-3f)
        assertEquals(IMP_EMBER_SPREAD * 2f, sqrt((left.x - right.x) * (left.x - right.x) + (left.y - right.y) * (left.y - right.y)), 1e-2f)
        val across = (left.x - right.x) * (state.coreX - centre.x) + (left.y - right.y) * (state.coreY - centre.y)
        assertEquals(0f, across, 1f, "the embers spread across its heading")

        // An encounter's Imp that bursts is no kill of its encounter either.
        val owned = quietState()
        val tag = encounterTag(EncounterOwner.DIMENSION, 3)
        assertTrue(owned.spawnEncounter(EncounterRequest(tag, owned.coreX, owned.coreY, 400f, listOf(EnemyType.IMP),
            positions = floatArrayOf(owned.coreX + 80f, owned.coreY))))
        val ownedImp = owned.enemies.single()
        owned.runUntil { ownedImp !in owned.enemies }
        assertEquals(0, assertNotNull(owned.encounter(tag)).killed)
        assertEquals(3, owned.volatileBursts.size)

        // A killed Imp, whoever owns it, leaves one ember where it fell; other kinds leave none.
        val killing = quietState()
        val victim = killing.spawn(EnemyType.IMP, 400f, 0f, ownerTag = 7L)
        val drifter = killing.spawn(EnemyType.DRIFTER, -400f, 0f)
        killing.dealHit(drifter, 10_000f, HitSource.WEAPON, canCrit = false)
        killing.sweepKills()
        assertTrue(drifter.dead)
        assertTrue(killing.volatileBursts.isEmpty())
        val killedAt = victim.x to victim.y
        killing.dealHit(victim, 10_000f, HitSource.WEAPON, canCrit = false)
        killing.sweepKills()
        assertTrue(victim.dead)
        val ember = killing.volatileBursts.single()
        assertEquals(killedAt, ember.x to ember.y)
        assertEquals(killing.elapsed + IMP_KILL_EMBER_SECONDS, ember.detonatesAt, 1e-4f)

        // A silenced fuse fizzles.
        val silent = quietState()
        val fizzling = silent.spawn(EnemyType.IMP, 80f, 0f)
        silent.runEnemiesFor(FIXED)
        fizzling.silencedUntil = Float.MAX_VALUE
        silent.runEnemiesFor(IMP_PRIME_SECONDS + 0.05f)
        assertTrue(fizzling in silent.enemies)
        assertEquals(EnemyBehavior.IDLE, fizzling.mode)
        assertTrue(silent.volatileBursts.isEmpty())
        assertEquals(silent.maxHp, silent.hp)
    }

    @Test
    fun anEmberBurnsASlowCoreWithoutTheRebirthMutatorAndQueuesOnlyBelowTheSharedCap() {
        for (fast in listOf(false, true)) {
            val state = quietState()
            if (fast) state.setVelocityForTesting(VOLATILE_ESCAPE_SPEED + 20f, 0f)
            state.queueEmber(state.coreX + 30f, state.coreY, 0.2f)
            state.elapsed += 0.1f
            state.updateVolatileRemains()
            assertEquals(1, state.volatileBursts.size, "it waits for its delay")
            assertEquals(state.maxHp, state.hp)
            state.elapsed += 0.11f
            val burn = state.hostileDamage(null, VOLATILE_BURST_DAMAGE)
            state.updateVolatileRemains()
            assertTrue(state.volatileBursts.isEmpty())
            assertEquals(if (fast) state.maxHp else state.maxHp - burn, state.hp, 1e-3f, if (fast) "a fast Core escapes" else "a slow Core burns")
        }

        val capped = quietState()
        repeat(MutableGameState.MAX_VOLATILE_BURSTS - 1) { capped.queueEmber(capped.coreX + 900f, capped.coreY, 5f) }
        assertEquals(MutableGameState.MAX_VOLATILE_BURSTS - 1, capped.volatileBursts.size)
        capped.queueEmber(capped.coreX + 900f, capped.coreY, 5f)
        assertEquals(MutableGameState.MAX_VOLATILE_BURSTS, capped.volatileBursts.size, "the last free slot takes an ember")
        val full = capped.volatileBursts
        capped.queueEmber(capped.coreX + 900f, capped.coreY, 5f)
        assertTrue(full === capped.volatileBursts, "a full list takes none")
    }

    @Test
    fun aHoundWindsUpDashesAtTheCoreAndItsStrikeHurtsAtAnySpeed() {
        val state = quietState()
        val hound = state.spawn(EnemyType.HOUND, 300f, 0f).apply { actionTimer = FIXED * 0.5f }
        state.runEnemiesFor(FIXED)
        assertTrue(hound.actionTimer < 0f && hound.mode == EnemyBehavior.IDLE, "it winds up")
        assertEquals(0f, state.strikeDamage(hound))
        val windupAt = state.elapsed
        state.runUntil { hound.mode == EnemyBehavior.STRIKE }
        assertEquals(HOUND_WINDUP / state.hostileAttackRate(), state.elapsed - windupAt, 0.02f)
        val dash = HOUND_DASH_SPEED * state.rebirthProfile.enemySpeedMultiplier
        assertEquals(dash, sqrt(hound.vx * hound.vx + hound.vy * hound.vy), 0.01f)
        assertEquals(atan2(state.coreY - hound.previousY, state.coreX - hound.previousX), atan2(hound.vy, hound.vx), 1e-4f, "at the Core")
        assertEquals(atan2(hound.vy, hound.vx), hound.facing, 1e-5f, "it faces its dash")
        assertEquals(state.contactDamage(hound), state.strikeDamage(hound))
        assertEquals(HOUND_DASH_CADENCE, hound.actionTimer, FIXED)
        val struckAt = state.elapsed
        state.runUntil { hound.mode == EnemyBehavior.IDLE }
        assertEquals(HOUND_STRIKE_SECONDS, state.elapsed - struckAt, 0.02f)
        assertEquals(0f, state.strikeDamage(hound), "the strike ends with its window")

        // A fast Core meeting a striking Hound is hurt; outside the strike it rams it freely.
        for (striking in listOf(true, false)) {
            val probe = quietState()
            val target = probe.spawn(EnemyType.HOUND, 30f, 0f).apply { hp = 10_000f }
            if (striking) {
                target.mode = EnemyBehavior.STRIKE
                target.modeUntil = probe.elapsed + 0.3f
            }
            probe.previousCoreX = probe.coreX - 10f
            probe.setVelocityForTesting(900f, 0f)
            probe.resolveEnemyCoreCollisions()
            assertEquals(if (striking) probe.maxHp - probe.contactDamage(target) else probe.maxHp, probe.hp, 1e-3f, "striking $striking")
        }

        // A silenced Hound's wind-up passes without a dash.
        val silent = quietState()
        val leashed = silent.spawn(EnemyType.HOUND, 300f, 0f).apply { actionTimer = -HOUND_WINDUP + FIXED * 0.5f; silencedUntil = Float.MAX_VALUE }
        silent.runEnemiesFor(FIXED)
        assertEquals(EnemyBehavior.IDLE, leashed.mode)
        assertEquals(HOUND_DASH_CADENCE, leashed.actionTimer, FIXED)
    }

    @Test
    fun houndsFlockOnlyWithHounds() {
        fun velocityBeside(type: EnemyType?): Pair<Float, Float> {
            val open = quietState()
            val lone = open.spawn(EnemyType.HOUND, 600f, 0f).apply { actionTimer = 2f }
            if (type != null) open.spawn(type, 600f, 20f)
            open.updateHound(lone, open.coreX - lone.x, open.coreY - lone.y, open.distanceToCore(lone), FIXED, FIXED)
            return lone.vx to lone.vy
        }
        val alone = velocityBeside(null)
        assertEquals(alone.first.toRawBits(), velocityBeside(EnemyType.IMP).first.toRawBits())
        assertEquals(alone.second.toRawBits(), velocityBeside(EnemyType.IMP).second.toRawBits())
        assertTrue(velocityBeside(EnemyType.HOUND).second < alone.second, "its pack pushes it apart")
    }

    @Test
    fun aCipherGlitchesOutOfReachAndReappearsAtAHashedTurnAroundTheCore() {
        for (start in listOf(100f, 380f, 800f)) {
            val state = quietState()
            val cipher = state.spawn(EnemyType.CIPHER, start, 0f)
            assertEquals(state.elapsed + 2f, cipher.abilityAt, 1e-4f, "its first glitch waits two seconds")
            cipher.abilityAt = 0f
            state.runEnemiesFor(FIXED)
            assertEquals(EnemyBehavior.PHASED, cipher.mode)
            assertEquals(cipher.modeUntil, cipher.shiftUntil, "out of phase it shifts")
            assertEquals(state.elapsed + CIPHER_PHASE_SECONDS, cipher.modeUntil, 1e-4f)
            assertTrue(state.isPhasedPhantom(cipher), "its profile phases out")
            val fromX = cipher.x - state.coreX
            val fromY = cipher.y - state.coreY
            while (cipher.mode == EnemyBehavior.PHASED) {
                assertEquals(0f, cipher.vx)
                assertEquals(0f, cipher.vy)
                state.runEnemiesFor(FIXED)
            }
            // The spec's hash of the run seed, its id and the run clock picks a 0.9-1.4 rad turn, either way.
            val hash = splitMix64(splitMix64(state.runSeed.toLong() xor 0x0C1F_E55E_ED00_0013L) xor
                (cipher.id.toLong() shl 20) xor (state.elapsed * 120f).toLong())
            val turn = (0.9f + 0.5f * (hash ushr 40).toFloat() / (1 shl 24)) * if (hash and 1L == 1L) 1f else -1f
            val reach = sqrt(fromX * fromX + fromY * fromY).coerceIn(CIPHER_NEAREST, CIPHER_FARTHEST)
            assertEquals(reach, state.distanceToCore(cipher), 0.05f, "from $start it reappears $reach u out")
            var turned = atan2(cipher.y - state.coreY, cipher.x - state.coreX) - atan2(fromY, fromX)
            while (turned > PI) turned -= (2 * PI).toFloat()
            while (turned < -PI) turned += (2 * PI).toFloat()
            assertEquals(turn, turned, 1e-3f, "from $start it turns around the Core by the hashed angle")
            assertEquals(cipher.x, cipher.previousX, "a teleport, not a sweep")
            assertEquals(cipher.y, cipher.previousY)
            assertEquals(EnemyBehavior.TELEGRAPH, cipher.mode)
            assertEquals(CIPHER_AIM_SECONDS, state.behaviorRemaining(cipher), 0.02f)
            assertEquals(atan2(state.coreY - cipher.y, state.coreX - cipher.x), cipher.facing, 1e-4f)
            val before = state.projectiles.size
            state.runUntil { cipher.mode == EnemyBehavior.IDLE }
            assertEquals(CIPHER_VOLLEY, state.projectiles.size - before, "it fires a fan once it has aimed")
            val middle = state.projectiles[before + 1]
            assertEquals(atan2(state.coreY - cipher.y, state.coreX - cipher.x), atan2(middle.vy, middle.vx), 1e-2f)
            assertEquals(state.elapsed + CIPHER_GLITCH_SECONDS / state.hostileAttackRate(), cipher.abilityAt, 1e-3f)
        }
    }

    @Test
    fun aGlitchedCipherIsIntangibleAndReappearsClearOfObstacles() {
        // Out of phase, a slow Core passes through it: no contact either way, and weapons find nothing.
        for (phased in listOf(true, false)) {
            val state = quietState()
            val cipher = state.spawn(EnemyType.CIPHER, 25f, 0f)
            if (phased) {
                cipher.abilityAt = 0f
                state.runEnemiesFor(FIXED)
                assertEquals(EnemyBehavior.PHASED, cipher.mode)
            }
            val integrity = cipher.hp
            state.previousCoreX = state.coreX - 10f
            state.setVelocityForTesting(60f, 0f)
            state.resolveEnemyCoreCollisions()
            if (phased) {
                assertEquals(state.maxHp, state.hp, "a phased Cipher has no contact")
                assertEquals(integrity, cipher.hp)
                assertEquals(0f, state.dealHit(cipher, 30f, HitSource.WEAPON, canCrit = false).amount)
            } else {
                assertTrue(state.hp < state.maxHp, "a solid Cipher hurts a slow Core")
            }
        }

        // An obstacle on its landing point pushes the landing outward along the same bearing.
        fun landing(rock: Boolean): Pair<Float, Float> {
            val state = quietState()
            val cipher = state.spawn(EnemyType.CIPHER, 380f, 0f).apply { abilityAt = 0f }
            if (rock) {
                val clear = landing(rock = false)
                state.placeObstaclesForTesting(testObstacle(state.coreX + clear.first, state.coreY + clear.second, radius = 50f))
            }
            state.runEnemiesFor(FIXED)
            while (cipher.mode == EnemyBehavior.PHASED) state.runEnemiesFor(FIXED)
            assertFalse(rock && state.isSpawnPointBlocked(cipher.x, cipher.y, cipher.radius), "it lands clear of the rock")
            return (cipher.x - state.coreX) to (cipher.y - state.coreY)
        }
        val clear = landing(rock = false)
        val nudged = landing(rock = true)
        val clearReach = sqrt(clear.first * clear.first + clear.second * clear.second)
        val nudgedReach = sqrt(nudged.first * nudged.first + nudged.second * nudged.second)
        assertEquals(atan2(clear.second, clear.first), atan2(nudged.second, nudged.first), 1e-4f)
        val steps = (nudgedReach - clearReach) / 50f
        assertTrue(steps in 0.99f..4.01f && abs(steps - steps.roundToInt()) < 1e-3f, "nudged out in 50 u steps: $steps")
    }

    @Test
    fun cipherGlitchesDrawNothingFromTheSpawnStream() {
        val state = quietState()
        state.placeObstaclesForTesting(testObstacle(state.coreX - 200f, state.coreY + 340f, radius = 80f))
        val ciphers = List(3) { state.spawn(EnemyType.CIPHER, 300f + it * 120f, it * 90f - 90f).apply { abilityAt = 0f } }
        val spawn = state.spawnRandom.snapshot()
        val gameplay = state.gameplayRandom.snapshot()
        val reward = state.rewardRandom.snapshot()
        var glitches = 0
        var seconds = 0f
        val modes = IntArray(ciphers.size) { ciphers[it].mode }
        while (seconds < 9f) {
            state.runEnemiesFor(FIXED)
            seconds += FIXED
            for (index in ciphers.indices) {
                if (modes[index] == EnemyBehavior.PHASED && ciphers[index].mode != EnemyBehavior.PHASED) glitches++
                modes[index] = ciphers[index].mode
            }
        }
        assertTrue(glitches >= 6, "each Cipher glitched at least twice: $glitches")
        assertEquals(spawn, state.spawnRandom.snapshot())
        assertEquals(gameplay, state.gameplayRandom.snapshot())
        assertEquals(reward, state.rewardRandom.snapshot())

        // The same check sees the Phantom's reappearance, which does draw from the spawn stream.
        val phantom = quietState()
        phantom.spawn(EnemyType.PHANTOM, 400f, 0f).apply { abilityAt = 0f }
        val before = phantom.spawnRandom.snapshot()
        phantom.runEnemiesFor(PHANTOM_PHASE_SECONDS + 0.1f)
        assertNotEquals(before, phantom.spawnRandom.snapshot())
    }

    @Test
    fun aMirrorSteersForTheCoresImageThroughTheSingularityAndFiresATwoBoltFan() {
        val state = quietState()
        state.updatePointer(state.screenWidth * 0.5f + 160f, state.screenHeight * 0.5f - 90f)
        val singularityX = state.cameraX + state.pointerX - state.screenWidth * 0.5f
        val singularityY = state.cameraY + state.pointerY - state.screenHeight * 0.5f
        val targetX = 2f * singularityX - state.coreX
        val targetY = 2f * singularityY - state.coreY
        val mirror = state.spawn(EnemyType.MIRROR, -300f, 220f)
        val startX = mirror.x
        val startY = mirror.y
        state.runEnemiesFor(FIXED)
        assertEquals(atan2(targetY - startY, targetX - startX), atan2(mirror.vy, mirror.vx), 1e-4f, "it heads for 2S - core")
        state.runEnemiesFor(0.6f)
        assertTrue(sqrt(mirror.vx * mirror.vx + mirror.vy * mirror.vy) <= MIRROR_MAX_SPEED * state.rebirthProfile.enemySpeedMultiplier + 0.01f)
        state.runEnemiesFor(3.4f)
        val miss = sqrt((mirror.x - targetX) * (mirror.x - targetX) + (mirror.y - targetY) * (mirror.y - targetY))
        assertTrue(miss < 15f, "it waits on the Core's image beyond the cursor: $miss u off")
        val bolts = state.projectiles.size
        assertTrue(bolts in MIRROR_VOLLEY..MIRROR_VOLLEY * 2 && bolts % MIRROR_VOLLEY == 0, "two-bolt fans every three seconds: $bolts")
        val first = state.projectiles[0]
        val second = state.projectiles[1]
        assertEquals(MIRROR_VOLLEY_SPACING, angleBetween(atan2(second.vy, second.vx), atan2(first.vy, first.vx)), 1e-4f)

        // It follows the cursor: a new pointer moves its mirror point.
        state.updatePointer(state.screenWidth * 0.5f - 200f, state.screenHeight * 0.5f + 60f)
        val movedX = 2f * (state.cameraX + state.pointerX - state.screenWidth * 0.5f) - state.coreX
        val movedY = 2f * (state.cameraY + state.pointerY - state.screenHeight * 0.5f) - state.coreY
        state.runEnemiesFor(4f)
        assertTrue(sqrt((mirror.x - movedX) * (mirror.x - movedX) + (mirror.y - movedY) * (mirror.y - movedY)) < 15f)
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
