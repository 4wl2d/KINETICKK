// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.content.api.rotateQuarterX
import kinetickk.ball.content.api.rotateQuarterY
import kinetickk.ball.gameplay.nucleus.model.DungeonState
import kinetickk.ball.gameplay.nucleus.model.ObstacleField
import kinetickk.ball.gameplay.nucleus.model.PlacedStructure
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.StructureField
import kinetickk.ball.gameplay.nucleus.model.WeaponEntity
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.render.EnemyBehavior
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kinetickk.ball.gameplay.nucleus.render.ProjectileKind
import kinetickk.ball.gameplay.nucleus.render.WallKind
import kinetickk.ball.gameplay.nucleus.render.WeaponEntityKind
import kinetickk.foundation.collections.immutableListOf
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StructureCollisionTest {
    // A wall across the Core's path: centre line x = 300, half thickness 24; the Core (radius 16) touches it at x = 260.
    private val wallX = 300f
    private val contactX = wallX - 24f - MutableGameState.CORE_RADIUS

    private fun quiet(content: GameplayContentSnapshot = structuresDisabledContent, shape: CoreShape = CoreShape.ORB, weapon: WeaponId = WeaponId.FLUX_WAKE) =
        buildTestState(content = content, shape = shape, weapon = weapon).apply { clearObstaclesForTesting() }

    private fun MutableGameState.bandSpeed(fraction: Float): Float {
        val start = obstacleDamageStartSpeed()
        return start + (obstacleLethalSpeed() - start) * fraction
    }

    /** Places the wall at x = 300 and flies the Core into it at [speed], half a step before the contact. */
    private fun MutableGameState.ramWall(speed: Float, kind: WallKind = WallKind.SOLID) {
        placeWallsForTesting(listOf(testWall(wallX, -200f, wallX, 200f, kind = kind)))
        flyIntoObstaclesForTesting(contactX + speed / 240f, 0f, speed, 0f)
    }

    private fun withWallDamageScale(scale: Float): GameplayContentSnapshot =
        structuresDisabledContent.copy(structures = structuresDisabledContent.structures.copy(wallDamageScale = scale))

    // -----------------------------------------------------------------------------------------
    // The Core.

    @Test
    fun theCoreBouncesOffWallsAndSealedDoorsButPassesOpenDoors() {
        for (kind in listOf(WallKind.SOLID, WallKind.DOOR_SEALED)) {
            val state = quiet()
            val speed = state.obstacleDamageStartSpeed() - 10f
            state.ramWall(speed, kind)
            assertEquals(state.maxHp, state.hp, "$kind: below the damage speed")
            assertEquals(0, state.pendingRicochets)
            assertEquals(-speed * 0.6f, state.velocityX, 0.5f, "$kind: it bounces back")
            assertEquals(0f, state.velocityY, 0.001f)
            assertTrue(state.coreX < contactX, "$kind: it never ends inside the wall: ${state.coreX}")
        }

        val open = quiet()
        val speed = open.obstacleDamageStartSpeed() - 10f
        open.ramWall(speed, WallKind.DOOR_OPEN)
        assertEquals(speed, open.velocityX, "an open door lets the Core through")
        assertEquals(contactX + speed / 240f, open.coreX)

        // Grazing the wall's end above its centre line, the round cap throws the Core off along its radial normal.
        val cap = quiet()
        cap.placeWallsForTesting(listOf(testWall(wallX, -200f, wallX, 200f)))
        cap.sweepCoreForTesting(240f, 220f, 290f, 220f, speed, 0f)
        assertTrue(cap.velocityY > 0f, "turned off the cap: ${cap.velocityY}")
        assertTrue(cap.velocityX < speed)
        assertTrue(wallGapForTesting(cap.coreX, cap.coreY, cap.structureField.walls.single()) >= cap.coreRadius)
    }

    @Test
    fun aWallHitHurtsExactlyLikeAnAsteroidHitScaledByWallDamage() {
        val asteroid = quiet()
        val speed = asteroid.bandSpeed(0.5f)
        asteroid.placeObstaclesForTesting(testObstacle(300f, 0f))
        asteroid.flyIntoObstaclesForTesting(300f - 80f - asteroid.coreRadius + speed / 240f, 0f, speed, 0f)
        val asteroidDamage = asteroid.maxHp - asteroid.hp
        assertTrue(asteroidDamage > 0f)

        val wall = quiet()
        wall.ramWall(speed)
        assertEquals(asteroidDamage, wall.maxHp - wall.hp, 0.001f, "the same band damage at the same normal speed")
        assertEquals(asteroid.velocityX, wall.velocityX, 0.01f, "the same bounce")
        assertEquals(asteroid.pendingRicochets, wall.pendingRicochets)
        assertEquals(1, wall.pendingRicochets)

        val halved = quiet(withWallDamageScale(0.5f))
        halved.ramWall(speed)
        assertEquals(asteroidDamage * 0.5f, halved.maxHp - halved.hp, 0.01f, "the policy scales a wall's band damage")
        val doubled = quiet(withWallDamageScale(2f))
        doubled.ramWall(speed)
        assertEquals(asteroidDamage * 2f, doubled.maxHp - doubled.hp, 0.01f)

        // The scale never touches a lethal hit.
        val lethal = quiet(withWallDamageScale(0.5f))
        lethal.ramWall(lethal.obstacleLethalSpeed() + 50f)
        assertEquals(GamePhase.GAME_OVER, lethal.phase)
        assertEquals(OBSTACLE_COLLISION_REASON, lethal.message)
    }

    @Test
    fun theEarliestContactWinsAcrossCirclesAndCapsules() {
        // One long step from x = 150 to 260 at a crawl: at the first contact the normal motion simply stops.
        fun sweep(wallLine: Float, obstacleRadius: Float, kind: ObstacleKind = ObstacleKind.ASTEROID) = quiet().apply {
            placeObstaclesForTesting(testObstacle(300f, 0f, radius = obstacleRadius, kind = kind))
            placeWallsForTesting(listOf(testWall(wallLine, -200f, wallLine, 200f)))
            sweepCoreForTesting(150f, 0f, 260f, 0f, 60f, 0f)
        }
        val wallFirst = sweep(230f, 80f)
        assertEquals(230f - 40f - 0.05f, wallFirst.coreX, 0.01f, "the wall at 190 comes before the rock at 204")
        assertEquals(0f, wallFirst.velocityX, 0.001f)

        val rockFirst = sweep(290f, 80f)
        assertEquals(300f - 96f - 0.05f, rockFirst.coreX, 0.01f, "the rock at 204 comes before the wall at 250")

        // The same fraction (both at x = 250): the circle wins. A bumper throws the Core back; the wall would stop it.
        val tie = sweep(290f, 34f, ObstacleKind.BUMPER)
        assertTrue(tie.velocityX < -60f, "the bumper answered the tie: ${tie.velocityX}")
        val wallAlone = quiet().apply {
            placeWallsForTesting(listOf(testWall(290f, -200f, 290f, 200f)))
            sweepCoreForTesting(150f, 0f, 260f, 0f, 60f, 0f)
        }
        assertEquals(0f, wallAlone.velocityX, 0.001f)
        assertEquals(250f - 0.05f, wallAlone.coreX, 0.01f)
    }

    @Test
    fun oneStructureSharesOneHitCooldown() {
        val state = quiet()
        val speed = state.bandSpeed(0.4f)
        val near = testWall(300f, -200f, 300f, 200f)
        val far = testWall(700f, -200f, 700f, 200f)
        val (structure) = state.placeWallsForTesting(listOf(near, far))
        state.flyIntoObstaclesForTesting(contactX + speed / 240f, 0f, speed, 0f)
        val afterFirst = state.hp
        assertTrue(afterFirst < state.maxHp)
        assertEquals(1, state.pendingRicochets)
        assertEquals(structureSurfaceId(structure.id), state.obstacleHitSurfaceId, "the hit is keyed by the structure, not the wall")

        // Its other wall, inside the cooldown, bounces the Core without hurting it.
        state.flyIntoObstaclesForTesting(700f - 40f + speed / 240f, 0f, speed, 0f)
        assertEquals(afterFirst, state.hp, "one structure, one cooldown")
        assertEquals(1, state.pendingRicochets)
        assertTrue(state.velocityX < 0f)

        state.elapsed += state.content.obstacles.hitCooldownSeconds + 0.01f
        state.flyIntoObstaclesForTesting(700f - 40f + speed / 240f, 0f, speed, 0f)
        assertTrue(state.hp < afterFirst, "after the cooldown the structure hurts again")
        assertEquals(2, state.pendingRicochets)

        // Every wall of a structure maps to one surface, never to a wall id or to no surface at all.
        val walls = state.structureField.walls
        walls.forEach { assertEquals(structureSurfaceId(structure.id), structureSurfaceId(it.id)) }
        walls.forEach { assertNotEquals(it.id, structureSurfaceId(it.id)) }
        val origin = structureId(WorldLayer.BASE, 0, 0)
        assertEquals(NO_SURFACE_ID, origin, "the realspace cell (0, 0) has the bare id")
        assertNotEquals(NO_SURFACE_ID, structureSurfaceId(origin))
        assertNotEquals(structureSurfaceId(origin), structureSurfaceId(structureId(WorldLayer.BASE, 0, 1)))
        for (index in 0 until 48) assertNotEquals(structureSurfaceId(origin), origin or index.toLong())
    }

    @Test
    fun aSecondStructureInsideTheCooldownHurts() {
        val state = quiet()
        val speed = state.bandSpeed(0.4f)
        val (first, second) = state.placeWallsForTesting(
            listOf(testWall(300f, -200f, 300f, 200f)),
            listOf(testWall(700f, -200f, 700f, 200f)),
        )
        state.flyIntoObstaclesForTesting(contactX + speed / 240f, 0f, speed, 0f)
        val afterFirst = state.hp
        assertTrue(afterFirst < state.maxHp)
        assertEquals(structureSurfaceId(first.id), state.obstacleHitSurfaceId)

        // Another structure is another surface: inside the first one's cooldown it still hurts and takes the cooldown.
        state.flyIntoObstaclesForTesting(700f - 40f + speed / 240f, 0f, speed, 0f)
        assertTrue(state.hp < afterFirst, "a second structure is a second surface")
        assertEquals(structureSurfaceId(second.id), state.obstacleHitSurfaceId)
        assertEquals(2, state.pendingRicochets)
        val afterSecond = state.hp

        // An asteroid is a surface of its own as well.
        state.structureField = StructureField.EMPTY
        state.placeObstaclesForTesting(testObstacle(300f, 0f))
        state.flyIntoObstaclesForTesting(300f - 80f - MutableGameState.CORE_RADIUS + speed / 240f, 0f, speed, 0f)
        assertTrue(state.hp < afterSecond, "a rock inside a structure's cooldown hurts")
        assertNotEquals(structureSurfaceId(second.id), state.obstacleHitSurfaceId)
    }

    @Test
    fun theCoreNeverRestsInsideAWall() {
        val run = buildTestState(content = forcedStructureContent(StructureTemplateId.DERELICT_STATION), seed = 44)
        val placement = run.structureAtForTesting(12, -9)
        val template = run.content.structures.template(StructureTemplateId.DERELICT_STATION)
        val lethal = run.obstacleLethalSpeed()

        fun structure(): PlacedStructure = run.structureField.structures[run.structureField.indexOf(placement.id)]
        fun roomCentre(room: Int): Pair<Float, Float> {
            val s = structure()
            val spec = template.rooms[room]
            val cx = (spec.minX + spec.maxX) / 2f
            val cy = (spec.minY + spec.maxY) / 2f
            return s.x + rotateQuarterX(cx, cy, s.rotation) to s.y + rotateQuarterY(cx, cy, s.rotation)
        }
        fun assertOutsideEveryWall(label: String) {
            run.structureField.walls.forEach { wall ->
                if (wall.kind == WallKind.DOOR_OPEN) return@forEach
                val gap = wallGapForTesting(run.coreX, run.coreY, wall)
                assertTrue(gap >= run.coreRadius - 0.01f, "$label: the Core rests $gap from wall ${wall.id and 0xFFL}")
            }
        }
        /** Flies from [start] on 16 headings at three speeds; [check] runs after every step. */
        fun flights(start: Pair<Float, Float>, label: String, check: () -> Unit) {
            for (heading in 0 until 16) for (share in listOf(0.3f, 0.7f, 0.95f)) {
                run.teleportCoreForTesting(placement.absoluteX, placement.absoluteY)
                run.coreX = start.first
                run.coreY = start.second
                val angle = heading * (PI.toFloat() / 8f) + 0.13f
                run.setVelocityForTesting(cos(angle) * lethal * share, sin(angle) * lethal * share)
                repeat(90) { step ->
                    run.hp = run.maxHp
                    run.previousCoreX = run.coreX
                    run.previousCoreY = run.coreY
                    run.coreX += run.velocityX * MutableGameState.FIXED_STEP
                    run.coreY += run.velocityY * MutableGameState.FIXED_STEP
                    run.updateObstacles()
                    assertEquals(GamePhase.RUNNING, run.phase)
                    assertOutsideEveryWall("$label heading $heading at $share, step $step")
                    check()
                }
            }
        }

        run.teleportCoreForTesting(placement.absoluteX, placement.absoluteY)
        for (room in template.rooms.indices) flights(roomCentre(room), "room $room") {}

        // The engaged hall seals both its doors: nothing gets out of it.
        val hall = 1
        run.dungeon = DungeonState(placement.id, placement.template, placement.rotation, structure().x, structure().y, engagedRoom = hall, enteredAt = 0f)
        run.applyStructureDoors(placement.id)
        // A door's capsule overlaps its jamb's cap, leaving a shallow notch where the Core rests 38.2 u off the line.
        val inset = 24f + run.coreRadius - 3f
        flights(roomCentre(hall), "sealed hall") {
            val s = structure()
            assertEquals(hall, roomAt(template, s.rotation, s.x, s.y, run.coreX, run.coreY, inset), "the Core left the sealed hall")
        }

        // A door sealing over the Core's centre line pushes it out of the door at once, back the way it came.
        val door = run.structureField.walls.first { it.door >= 0 && it.kind == WallKind.DOOR_SEALED }
        val midX = (door.ax + door.bx) / 2f
        val midY = (door.ay + door.by) / 2f
        val doorLength = length(door.bx - door.ax, door.by - door.ay)
        val acrossX = -(door.by - door.ay) / doorLength
        val acrossY = (door.bx - door.ax) / doorLength
        for (side in listOf(1f, -1f)) {
            run.sweepCoreForTesting(midX, midY, midX, midY, acrossX * side * 30f, acrossY * side * 30f)
            assertOutsideEveryWall("a sealed door over the Core")
            assertTrue((run.coreX - midX) * acrossX * side + (run.coreY - midY) * acrossY * side < 0f, "it rests on the side it came from")
        }

        // A slot 2 u wider than the Core: a fast crossing spends every bounce of the step, and the last guard still
        // leaves the Core between the walls.
        val slot = quiet()
        slot.placeWallsForTesting(listOf(testWall(0f, -300f, 0f, 300f), testWall(82f, -300f, 82f, 300f)))
        val fast = slot.obstacleLethalSpeed() * 0.9f
        slot.sweepCoreForTesting(41f, 0f, 41f + fast * MutableGameState.FIXED_STEP, 0f, fast, 0f)
        assertEquals(GamePhase.RUNNING, slot.phase)
        slot.structureField.walls.forEach { wall ->
            assertTrue(wallGapForTesting(slot.coreX, slot.coreY, wall) >= slot.coreRadius - 0.01f, "the Core ended at ${slot.coreX} in the slot")
        }
    }

    // -----------------------------------------------------------------------------------------
    // Blinks, steps, swings and hooks.

    private fun arena(shape: CoreShape = CoreShape.ORB): MutableGameState = buildTestState(shape = shape, content = structuresDisabledContent).apply {
        obstacleField = ObstacleField.EMPTY
        aimAt(coreX + 300f, coreY)
    }

    private fun MutableGameState.learn(id: SkillId): Int {
        acquireActive(id, 1)
        takeProgressUpdate()
        return skillLoadout.slotOf(id)
    }

    @Test
    fun blinksVoidStepsSlingshotsAndHooksStopShortOfWalls() {
        // Crescent Blink: the wall's surface sits 150 ahead of the Core's leading edge, inside the 240 Blink range.
        val blink = buildTestState(shape = CoreShape.CRESCENT, content = structuresDisabledContent)
        blink.clearObstaclesForTesting()
        blink.heat = 0f
        val surface = blink.coreX + blink.coreRadius + 150f
        blink.placeWallsForTesting(listOf(testWall(surface + 24f, -300f, surface + 24f, 300f)))
        blink.setVelocityForTesting(500f, 0f)
        blink.pointerX = blink.screenWidth
        blink.pointerY = blink.screenHeight * 0.5f
        val startX = blink.coreX
        blink.performDash()
        assertTrue(blink.coreX > startX + 100f, "it still blinks most of the way: ${blink.coreX - startX}")
        assertTrue(blink.coreX + blink.coreRadius < surface, "it stops before the wall")
        assertFalse(blink.obstacleBlocksSegment(startX, blink.coreY, blink.coreX, blink.coreY, blink.coreRadius))
        blink.updateObstacles()
        assertEquals(blink.maxHp, blink.hp, "the next step meets no wall")

        // Arc Hook: a target behind a wall is out of reach, the one in the clear is hooked.
        val hook = arena()
        val hookSlot = hook.learn(SkillId.ARC_HOOK)
        hook.aimAt(hook.coreX + 420f, hook.coreY)
        val behind = hook.dummy(x = hook.coreX + 300f, y = hook.coreY)
        val open = hook.dummy(x = hook.coreX + 150f, y = hook.coreY + 250f)
        hook.placeWallsForTesting(listOf(testWall(hook.coreX + 150f, hook.coreY - 60f, hook.coreX + 150f, hook.coreY + 60f)))
        assertEquals(open.id, hook.nearestSkillTarget(hook.coreX + 420f, hook.coreY, 460f)?.id, "a target behind a wall is out of reach")
        hook.castSkill(hookSlot)
        assertEquals(open.id.toFloat(), hook.skillRuntime[hookSlot, kinetickk.ball.gameplay.nucleus.model.SkillCell.TARGET_ID])
        assertTrue(behind.alive)

        // Slingshot: a wall across the aim slows the swing to what keeps its next moments clear.
        val sling = arena()
        val slingSlot = sling.learn(SkillId.SLINGSHOT)
        val values = sling.content.skill(SkillId.SLINGSHOT).rank(sling.skillLoadout.rank(slingSlot)).values
        sling.aimAt(sling.coreX, sling.coreY - 400f)
        sling.placeWallsForTesting(listOf(testWall(sling.coreX - 300f, sling.coreY - 260f, sling.coreX + 300f, sling.coreY - 260f)))
        sling.setVelocityForTesting(800f, 0f)
        sling.castSkill(slingSlot)
        assertTrue(sling.speed < (800f * values[0]).coerceIn(values[1], values[2]), "the swing slowed")
        assertTrue(sling.speed > 0f && sling.velocityY < 0f, "the swing still turns onto the aim")
        assertFalse(
            sling.wallBlocksSegment(sling.coreX, sling.coreY, sling.coreX + sling.velocityX * SLING_CLEAR_SECONDS,
                sling.coreY + sling.velocityY * SLING_CLEAR_SECONDS, sling.coreRadius),
            "its next moments are clear",
        )

        // Void Step: the step lands short of the wall with room to spare.
        val step = arena()
        val stepSlot = step.learn(SkillId.VOID_STEP)
        step.aimAt(step.coreX + 600f, step.coreY)
        val wallLine = step.coreX + 260f
        step.placeWallsForTesting(listOf(testWall(wallLine, step.coreY - 300f, wallLine, step.coreY + 300f)))
        val stepStart = step.coreX
        step.castSkill(stepSlot)
        assertTrue(step.coreX > stepStart, "the step still moves the Core")
        assertTrue(wallLine - 24f - step.coreX >= step.coreRadius + VOID_STEP_OBSTACLE_GAP, "it lands clear of the wall: ${step.coreX}")
        assertTrue(step.wallsClearOf(step.coreX, step.coreY, step.coreRadius + VOID_STEP_OBSTACLE_GAP))
        assertFalse(step.wallsClearOf(wallLine - 24f - 10f, step.coreY, 11f))
    }

    // -----------------------------------------------------------------------------------------
    // Enemies.

    @Test
    fun enemiesArePushedOutSlideAlongAndSlamIntoWalls() {
        val state = quiet()
        state.placeWallsForTesting(listOf(
            testWall(500f, -300f, 500f, 300f),
            testWall(500f, 400f, 500f, 600f, kind = WallKind.DOOR_OPEN),
        ))
        val surface = 500f - 24f - 17f
        val thrownIn = state.dummy(x = surface + 4f, y = 0f, hp = 10_000f)
        thrownIn.vx = 700f
        state.resolveEnemyObstacles()
        assertEquals(surface, thrownIn.x, 0.01f, "pushed back to the surface")
        assertEquals(0f, thrownIn.vx, 0.01f, "inward velocity removed")
        assertTrue(thrownIn.hp < thrownIn.maxHp, "a hard contact slams")

        val gentle = state.dummy(x = surface + 4f, y = 100f, hp = 10_000f)
        gentle.vx = 200f
        state.resolveEnemyObstacles()
        assertEquals(gentle.maxHp, gentle.hp, "below the slam speed there is no slam")
        assertEquals(surface, gentle.x, 0.01f)

        // Self-propelled enemies bend along the wall before reaching it; thrown ones fly straight on.
        val walker = state.dummy(x = surface - 20f, y = -100f)
        walker.vx = 100f
        state.resolveEnemyObstacles()
        assertTrue(walker.vx < 100f, "the approach bends")
        assertNotEquals(0f, walker.vy, "along the wall")
        val thrown = state.dummy(x = surface - 20f, y = -150f)
        thrown.vx = 100f
        state.markThrown(thrown)
        state.resolveEnemyObstacles()
        assertEquals(100f, thrown.vx, "thrown enemies fly straight into it")

        // Open doorways let enemies through; a phasing Phantom passes walls.
        val doorway = state.dummy(x = 500f, y = 500f)
        doorway.vx = 300f
        state.resolveEnemyObstacles()
        assertEquals(500f, doorway.x)
        assertEquals(300f, doorway.vx)
        val phantom = state.dummy(x = 500f, y = -200f, type = EnemyType.PHANTOM)
        phantom.shiftUntil = state.elapsed + 1f
        state.resolveEnemyObstacles()
        assertEquals(500f, phantom.x, "a shifting Phantom is not pushed")
        assertTrue(state.enemies.filter { it !== doorway && it !== phantom }.all { abs(it.x - 500f) >= 24f + it.radius - 0.01f })
    }

    @Test
    fun aCrawlerHugsAWallLikeARock() {
        val state = buildTestState(content = structuresDisabledContent).apply {
            updatePointer(640f, 360f)
            runGrace = Float.MAX_VALUE
            clearObstaclesForTesting()
        }
        // A long wall 600 u east of the Core; the Crawler stands on its Core side, 80 u off it and 500 u north.
        val wallX = state.coreX + 600f
        state.placeWallsForTesting(listOf(testWall(wallX, state.coreY - 1_500f, wallX, state.coreY + 1_500f)))
        val crawler = state.spawnedEnemy(EnemyType.CRAWLER, wallX - 24f - 80f, state.coreY - 500f)
        state.enemies += crawler
        fun gap() = wallX - 24f - crawler.x
        repeat(240) { state.stepEnemies() }
        assertTrue(gap() in crawler.radius - 0.5f..CRAWLER_HUG_GAP + 30f, "it closes on the wall: gap ${gap()}")
        val northOfCore = state.coreY - crawler.y
        repeat(60) {
            state.stepEnemies()
            assertTrue(gap() in crawler.radius - 0.5f..CRAWLER_HUG_GAP + 30f, "it hugs the wall: gap ${gap()}")
        }
        assertTrue(state.coreY - crawler.y < northOfCore - 40f, "it creeps along the wall toward the Core's side")
    }

    private fun MutableGameState.stepEnemies() {
        elapsed += MutableGameState.FIXED_STEP
        updateEnemies(MutableGameState.FIXED_STEP)
    }

    @Test
    fun aGarrisonStaysInsideItsEngagedRoom() {
        val run = buildTestState(content = forcedStructureContent(StructureTemplateId.DERELICT_STATION), seed = 44)
        val placement = run.structureAtForTesting(12, -9)
        run.teleportCoreForTesting(placement.absoluteX, placement.absoluteY)
        run.enemies.clear()
        val template = run.content.structures.template(StructureTemplateId.DERELICT_STATION)
        val structure = run.structureField.structures[run.structureField.indexOf(placement.id)]
        fun world(localX: Float, localY: Float) =
            structure.x + rotateQuarterX(localX, localY, structure.rotation) to structure.y + rotateQuarterY(localX, localY, structure.rotation)
        val hall = template.rooms[1]
        // A member pushed past the hall's east wall into the bay, and one outside its room; a stranger in the bay.
        val (memberX, memberY) = world(hall.maxX + 200f, 300f)
        val member = run.dummy(x = memberX, y = memberY)
        val (insideX, insideY) = world((hall.minX + hall.maxX) / 2f, 0f)
        val settled = run.dummy(x = insideX, y = insideY)
        val stranger = run.dummy(x = memberX, y = memberY)
        run.dungeon = DungeonState(
            placement.id, placement.template, placement.rotation, structure.x, structure.y, engagedRoom = 1,
            garrisonIds = immutableListOf(member.id, settled.id), garrisonTotal = 2, engaged = true, enteredAt = 0f,
        )
        run.resolveEnemyObstacles()
        val inset = member.radius + template.wallHalfThickness + 4f
        assertEquals(1, roomAt(template, structure.rotation, structure.x, structure.y, member.x, member.y, inset - 0.01f), "clamped into the hall")
        assertEquals(member.x, member.previousX, "the clamp is a teleport")
        assertEquals(member.y, member.previousY)
        assertEquals(insideX, settled.x, "a member inside its room stays put")
        assertEquals(insideY, settled.y)
        assertEquals(memberX, stranger.x, "only the garrison is held")

        // Without an engaged room nothing is held.
        val (awayX, awayY) = world(hall.maxX + 200f, 300f)
        member.x = awayX
        member.y = awayY
        run.dungeon = run.dungeon!!.copy(engagedRoom = -1)
        run.resolveEnemyObstacles()
        assertEquals(awayX, member.x)
    }

    // -----------------------------------------------------------------------------------------
    // Shots and weapon entities.

    @Test
    fun shotsAndWeaponEntitiesStopAtWallsButPassOpenDoors() {
        val state = quiet()
        state.placeWallsForTesting(listOf(
            testWall(600f, -100f, 600f, 100f),
            testWall(600f, 300f, 600f, 500f, kind = WallKind.DOOR_OPEN),
            testWall(600f, 700f, 600f, 900f, kind = WallKind.DOOR_SEALED),
        ))
        val hostile = Projectile(x = 585f, y = 0f, vx = 500f, vy = 0f, radius = 5f, life = 2f, previousX = 560f, previousY = 0f)
        val friendly = Projectile(x = 615f, y = 800f, vx = -500f, vy = 0f, radius = 5f, life = 2f, hostile = false,
            previousX = 640f, previousY = 800f)
        val throughDoor = Projectile(x = 640f, y = 400f, vx = 500f, vy = 0f, radius = 5f, life = 2f, previousX = 560f, previousY = 400f)
        val mine = Projectile(x = 600f, y = 50f, vx = 0f, vy = 0f, radius = 9f, life = 2f, kind = ProjectileKind.MINE,
            previousX = 600f, previousY = 50f)
        val clear = Projectile(x = 300f, y = -200f, vx = 500f, vy = 0f, radius = 5f, life = 2f, previousX = 290f, previousY = -200f)
        state.projectiles += listOf(hostile, friendly, throughDoor, mine, clear)
        state.resolveCollisions(MutableGameState.FIXED_STEP)
        assertFalse(hostile in state.projectiles, "a solid wall stops a hostile shot")
        assertFalse(friendly in state.projectiles, "a sealed door stops a player shot")
        assertTrue(throughDoor in state.projectiles, "an open door lets it through")
        assertTrue(mine in state.projectiles, "a mine never moves")
        assertTrue(clear in state.projectiles)
        assertEquals(state.maxHp, state.hp)

        // A shot wider than the Core leaves the wall the Core rests on, but not into it.
        val resting = quiet(shape = CoreShape.SHARD)
        val restLine = resting.coreX - 24f - resting.coreRadius - 0.05f
        resting.placeWallsForTesting(listOf(testWall(restLine, -200f, restLine, 200f)))
        fun shot(vx: Float) = Projectile(x = resting.coreX + vx * MutableGameState.FIXED_STEP, y = resting.coreY, vx = vx, vy = 0f,
            radius = resting.coreRadius + 1f, life = 2f, hostile = false, previousX = resting.coreX, previousY = resting.coreY)
        val away = shot(690f)
        val into = shot(-690f)
        resting.projectiles += listOf(away, into)
        resting.blockProjectilesAtWalls()
        assertFalse(away.spent, "a shot fired away from the wall flies on")
        assertTrue(into.spent, "a shot fired into the wall stops")

        // A lens collapses at a wall and flies on through an open door.
        val lensState = quiet()
        lensState.placeWallsForTesting(listOf(
            testWall(100f, -100f, 100f, 100f),
            testWall(100f, 300f, 100f, 500f, kind = WallKind.DOOR_OPEN),
        ))
        val blocked = WeaponEntity(WeaponEntityKind.LENS, 40f, 0f, 12f, 2f, 2f, vx = 1_200f)
        val passing = WeaponEntity(WeaponEntityKind.LENS, 40f, 400f, 12f, 2f, 2f, vx = 1_200f)
        lensState.weaponRuntime.entities.clear()
        lensState.weaponRuntime.entities += listOf(blocked, passing)
        repeat(12) { lensState.advanceWeaponEntities(MutableGameState.FIXED_STEP) }
        assertEquals(1, blocked.phase, "the lens collapsed at the wall")
        assertTrue(blocked.x + blocked.radius <= 100f - 24f, "it collapsed in front of the wall: ${blocked.x}")
        assertEquals(0, passing.phase)
        assertTrue(passing.x > 100f + 24f, "it flew through the doorway: ${passing.x}")

        // An unmastered disc turns back at a wall.
        val discState = quiet(weapon = WeaponId.GYRE_DISC)
        discState.weaponLevel = 1
        discState.placeWallsForTesting(listOf(testWall(100f, -100f, 100f, 100f)))
        val disc = WeaponEntity(WeaponEntityKind.DISC, 40f, 0f, GYRE_RADIUS, GYRE_MAX_LIFE, GYRE_MAX_LIFE, vx = GYRE_SPEED, extent = GYRE_REACH)
        discState.weaponRuntime.entities.clear()
        discState.weaponRuntime.entities += disc
        repeat(10) { if (disc.phase == 0) discState.advanceWeaponEntities(MutableGameState.FIXED_STEP) }
        assertEquals(1, disc.phase, "the disc turned back at the wall")
        assertTrue(disc.x + disc.radius <= 100f - 24f, "in front of the wall: ${disc.x}")
    }

    @Test
    fun aMasteredDiscDeflectsOffAWallNormal() {
        val state = quiet(weapon = WeaponId.GYRE_DISC)
        state.weaponLevel = 10
        assertTrue(state.hasMasteryForm(3))
        // An outgoing disc meets a horizontal wall at a slant: it reflects off the wall's normal.
        state.placeWallsForTesting(listOf(testWall(-300f, -100f, 300f, -100f)))
        val disc = WeaponEntity(WeaponEntityKind.DISC, 0f, -100f + 24f + GYRE_RADIUS + 3f, GYRE_RADIUS, GYRE_MAX_LIFE, GYRE_MAX_LIFE,
            vx = 300f, vy = -600f, extent = 10_000f)
        state.weaponRuntime.entities.clear()
        state.weaponRuntime.entities += disc
        state.advanceWeaponEntities(MutableGameState.FIXED_STEP)
        assertEquals(0, disc.phase, "still outgoing")
        assertEquals(300f, disc.vx, 0.001f, "the part along the wall stays")
        assertEquals(600f, disc.vy, 0.001f, "the part into the wall reverses")
        assertTrue(wallGapForTesting(disc.x, disc.y, state.structureField.walls.single()) >= disc.radius)

        // A returning disc glances along a wall between it and the Core: its part into the wall goes, the rest runs
        // along it at the return speed, and it never enters the wall.
        val behind = quiet(weapon = WeaponId.GYRE_DISC)
        behind.weaponLevel = 10
        behind.placeWallsForTesting(listOf(testWall(behind.coreX - 300f, behind.coreY + 200f, behind.coreX + 300f, behind.coreY + 200f)))
        val glancing = WeaponEntity(WeaponEntityKind.DISC, behind.coreX + 60f, behind.coreY + 200f + 24f + GYRE_RADIUS + 2f, GYRE_RADIUS,
            GYRE_MAX_LIFE, GYRE_MAX_LIFE, phase = 1)
        behind.weaponRuntime.entities.clear()
        behind.weaponRuntime.entities += glancing
        behind.advanceWeaponEntities(MutableGameState.FIXED_STEP)
        assertEquals(0f, glancing.vy, 0.001f, "nothing of its heading runs into the wall")
        assertEquals(-GYRE_RETURN_SPEED, glancing.vx, 0.01f, "it runs along the wall on the Core's side")
        assertTrue(wallGapForTesting(glancing.x, glancing.y, behind.structureField.walls.single()) >= glancing.radius)

        // Met short of its end, it glances along the wall, comes round the end and home.
        val home = quiet(weapon = WeaponId.GYRE_DISC)
        home.weaponLevel = 10
        home.placeWallsForTesting(listOf(testWall(home.coreX + 40f, home.coreY + 200f, home.coreX + 400f, home.coreY + 200f)))
        val returning = WeaponEntity(WeaponEntityKind.DISC, home.coreX + 250f, home.coreY + 400f, GYRE_RADIUS, GYRE_MAX_LIFE, GYRE_MAX_LIFE, phase = 1)
        home.weaponRuntime.entities.clear()
        home.weaponRuntime.entities += returning
        val wall = home.structureField.walls.single()
        var glanced = false
        while (returning in home.weaponRuntime.entities) {
            val x = returning.x
            val y = returning.y
            returning.capturePrevious()
            home.advanceWeaponEntities(MutableGameState.FIXED_STEP)
            if (returning !in home.weaponRuntime.entities) break
            assertTrue(returning.x != x || returning.y != y, "the disc stopped at ($x, $y)")
            assertTrue(wallGapForTesting(returning.x, returning.y, wall) >= returning.radius - 0.01f, "the disc entered the wall at (${returning.x}, ${returning.y})")
            if (abs(returning.vy) < 1f) glanced = true
        }
        assertTrue(glanced, "it ran along the wall")
        val distance = length(returning.x - home.coreX, returning.y - home.coreY)
        assertTrue(distance <= GYRE_CATCH_RADIUS, "the disc ended $distance from the Core instead of returning")
    }

    // -----------------------------------------------------------------------------------------
    // Spawns, placements and sight lines.

    @Test
    fun spawnsTotemsAndAnomaliesLandOutsideFootprints() {
        val run = buildTestState(content = forcedStructureContent(StructureTemplateId.DERELICT_STATION), seed = 44)
        val placement = run.structureAtForTesting(12, -9)
        run.teleportCoreForTesting(placement.absoluteX, placement.absoluteY)
        run.enemies.clear()
        val structure = run.structureField.structures[run.structureField.indexOf(placement.id)]
        fun outside(x: Float, y: Float, inflate: Float): Boolean = run.structureField.structures.all { s ->
            abs(x - s.x) >= s.halfWidth + inflate || abs(y - s.y) >= s.halfHeight + inflate
        }

        // Ejection moves a point to the nearest side of the footprint inflated by its radius and a margin.
        val ejected = run.structureEjected(structure.x + structure.halfWidth - 30f, structure.y + 10f, 20f)
        assertEquals(structure.y + 10f, ejected.y, "only across the nearest side")
        assertTrue(ejected.x >= structure.x + structure.halfWidth + 20f + 64f)
        assertTrue(outside(ejected.x, ejected.y, 20f + 64f))
        val far = run.structureEjected(structure.x + 5_000f, structure.y, 20f)
        assertEquals(structure.x + 5_000f, far.x, "a point away from every footprint stays")

        val (totemX, totemY) = run.nudgedClearOfObstacles(structure.x, structure.y, 70f)
        assertTrue(outside(totemX, totemY, 70f), "the Totem left the footprint")
        assertFalse(run.isSpawnPointBlocked(totemX, totemY, 70f))
        val (pointX, pointY) = run.clearPointOfInterestSite(structure.x, structure.y)
        assertTrue(outside(pointX, pointY, 320f), "the anomaly, its ring and its beacons left the footprint")

        // The director's spawns and leashed relocations around a Core standing in the structure.
        repeat(40) { run.spawnScheduled(EnemyType.DRIFTER) }
        assertTrue(run.enemies.isNotEmpty())
        run.enemies.forEach { enemy -> assertTrue(outside(enemy.x, enemy.y, enemy.radius), "spawned inside a footprint at ${enemy.x}, ${enemy.y}") }
        val leashed = run.enemies.first()
        repeat(20) {
            run.relocateLeashedEnemy(leashed)
            assertTrue(outside(leashed.x, leashed.y, leashed.radius), "relocated inside a footprint")
        }

        // A point inside the engaged room stays (escorts spawn in their room); another room's point is ejected.
        val template = run.content.structures.template(StructureTemplateId.DERELICT_STATION)
        fun roomCentre(room: Int): Pair<Float, Float> {
            val spec = template.rooms[room]
            val cx = (spec.minX + spec.maxX) / 2f
            val cy = (spec.minY + spec.maxY) / 2f
            return structure.x + rotateQuarterX(cx, cy, structure.rotation) to structure.y + rotateQuarterY(cx, cy, structure.rotation)
        }
        run.dungeon = DungeonState(placement.id, placement.template, placement.rotation, structure.x, structure.y, engagedRoom = 1, enteredAt = 0f)
        val (hallX, hallY) = roomCentre(1)
        val kept = run.structureEjected(hallX, hallY, 17f)
        assertEquals(hallX, kept.x)
        assertEquals(hallY, kept.y)
        val (bayX, bayY) = roomCentre(3)
        val moved = run.structureEjected(bayX, bayY, 17f)
        assertTrue(outside(moved.x, moved.y, 17f + 64f), "another room's point leaves the footprint")
    }

    @Test
    fun sightLinesAreBlockedByWalls() {
        val state = quiet()
        state.placeWallsForTesting(listOf(
            testWall(350f, -100f, 350f, 100f),
            testWall(350f, 300f, 350f, 500f, kind = WallKind.DOOR_OPEN),
            testWall(350f, 700f, 350f, 900f, kind = WallKind.DOOR_SEALED),
        ))
        assertTrue(state.obstacleBlocksSegment(0f, 0f, 700f, 0f, 7f), "through a wall")
        assertTrue(state.obstacleBlocksSegment(0f, 120f, 700f, 120f, 7f), "the wall's slab and the swept radius count")
        assertFalse(state.obstacleBlocksSegment(0f, 140f, 700f, 140f, 7f))
        assertFalse(state.obstacleBlocksSegment(0f, 400f, 700f, 400f, 7f), "through an open door")
        assertTrue(state.obstacleBlocksSegment(0f, 800f, 700f, 800f, 7f), "through a sealed door")
        assertFalse(state.obstacleBlocksSegment(0f, 0f, 300f, 0f, 7f), "short of the wall")
        assertTrue(state.isSpawnPointBlocked(350f + 24f + 20f, 0f, 17f), "a spawn keeps its clearance from a wall")
        assertFalse(state.isSpawnPointBlocked(350f + 24f + 17f + 25f, 0f, 17f))
        assertFalse(state.isSpawnPointBlocked(350f, 400f, 17f), "an open doorway is clear")

        // A Sniper behind a wall never takes aim; in the clear it does.
        fun sniperAims(y: Float): Boolean {
            val sniper = state.dummy(x = 700f, y = y, type = EnemyType.SNIPER)
            val dx = state.coreX - sniper.x
            val dy = state.coreY - sniper.y
            state.updateSniper(sniper, dx, dy, length(dx, dy), MutableGameState.FIXED_STEP)
            return sniper.mode == EnemyBehavior.TELEGRAPH
        }
        assertFalse(sniperAims(0f), "the wall hides the Core")
        assertTrue(sniperAims(-400f), "a clear line")
    }
}
