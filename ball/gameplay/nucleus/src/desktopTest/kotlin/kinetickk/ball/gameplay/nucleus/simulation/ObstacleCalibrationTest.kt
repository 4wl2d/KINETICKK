// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.ObstacleAspect
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Calibrates the collision bands against real flight (dp units) on a 1280×800 desktop and a 412×915
 * phone: holding the cursor at the dash-safe reach (0.30) of the long axis stays below the damage-start
 * speed, 0.6 lands in the damage band and 0.79 (just inside the edge strain) is lethal for every Matrix
 * and obstacle-tuning state. The bands are fixed speeds from the straight steady model: they do not depend on the heading, so
 * the shorter axis is only ever slower, a turn never moves them, and speed carved in a curve counts like
 * any speed. A Dash from a gentle cruise can hurt but never kills.
 */
class ObstacleCalibrationTest {
    private val step = MutableGameState.FIXED_STEP
    // No generated obstacles or structures, and chunks so large that the Core never changes chunk (which would
    // regenerate the window and drop a placed test asteroid).
    private val emptyWorld = canonicalGameplayContent.copy(
        obstacles = canonicalGameplayContent.obstacles.copy(meanPerChunk = 0f, chunkSize = HUGE_CHUNK),
        structures = canonicalGameplayContent.structures.copy(enabled = false),
    )
    private val viewports = listOf(1_280f to 800f, 412f to 915f)

    /** The cruise below the damage start from which a Dash may hurt but never kills. */
    private val gentleReach = emptyWorld.obstacles.dashSafeReach

    private class Measured(val speed: Float, val start: Float, val lethal: Float)

    /**
     * A quiet run of [shape] owning the Matrix [ranks] and the [items] (appended to the catalog), with the cursor held at
     * [reach] toward +x ([horizontal]) or +y.
     */
    private fun cruise(
        width: Float,
        height: Float,
        reach: Float,
        horizontal: Boolean,
        tier: Int,
        shape: CoreShape = CoreShape.ORB,
        ranks: Map<MatrixNodeId, Int> = emptyMap(),
        items: List<ItemDefinition> = emptyList(),
    ): MutableGameState =
        buildTestState(content = emptyWorld.withProbes(*items.toTypedArray()), rebirth = tier, shape = shape).apply {
            resize(width, height)
            ranks.forEach { (node, rank) -> matrixRanks[content.matrixNodes.indexOfFirst { it.id == node }] = rank }
            startRun()
            items.forEach { own(it.name) }
            enemies.clear()
            spawnClock = Float.MAX_VALUE
            nextEliteAt = Float.MAX_VALUE
            runGrace = 0f
            // Overdrive would add thrust; the bands are measured on plain flight.
            overdriveBurnoutUntil = Float.MAX_VALUE
            // Start in the middle of a chunk: the run origin sits on a chunk corner.
            worldOriginX = HUGE_CHUNK * 0.5
            worldOriginY = HUGE_CHUNK * 0.5
            refreshObstacleWindow()
            aim(reach, horizontal)
        }

    private fun MutableGameState.aim(reach: Float, horizontal: Boolean) {
        if (horizontal) updatePointer(screenWidth * 0.5f * (1f + reach), screenHeight * 0.5f)
        else updatePointer(screenWidth * 0.5f, screenHeight * 0.5f * (1f + reach))
    }

    private fun MutableGameState.fly(seconds: Float) {
        repeat((seconds / step).toInt()) {
            if (phase != GamePhase.RUNNING) return
            // At a gentle reach the Core rides close to the singularity; the grace keeps it measuring.
            runGrace = 1f
            simulateStep(step)
            enemies.clear()
            projectiles.clear()
        }
    }

    /** Terminal speed with the cursor held at [reach] toward +x ([horizontal]) or +y, after 20 s. */
    private fun measure(width: Float, height: Float, reach: Float, horizontal: Boolean, tier: Int): Measured {
        val state = cruise(width, height, reach, horizontal, tier)
        state.fly(20f)
        assertEquals(GamePhase.RUNNING, state.phase)
        return Measured(state.speed, state.obstacleDamageStartSpeed(), state.obstacleLethalSpeed())
    }

    /** Puts a large asteroid straight ahead of the Core's current flight, [gap] past its surface. */
    private fun MutableGameState.placeAsteroidAhead(gap: Float) {
        val currentSpeed = speed
        val radius = 120f
        val distance = coreRadius + radius + gap
        placeObstaclesForTesting(
            testObstacle(coreX + velocityX / currentSpeed * distance, coreY + velocityY / currentSpeed * distance, radius),
        )
    }

    /** Flies on until the Core bounces off the asteroid ahead (or the run ends); returns its speed just before. */
    private fun MutableGameState.flyIntoTheAsteroid(): Float {
        val headingX = velocityX / speed
        val headingY = velocityY / speed
        repeat((1f / step).toInt()) {
            val before = speed
            runGrace = 1f
            simulateStep(step)
            if (phase != GamePhase.RUNNING || velocityX * headingX + velocityY * headingY < 0f) return before
        }
        fail("the Core never reached the asteroid")
    }

    @Test
    fun cursorReachAlongTheLongAxisDecidesTheCollisionBandOnDesktopAndPhone() {
        for ((width, height) in viewports) for (tier in listOf(0, 3)) {
            val longIsHorizontal = width >= height
            val reaches = listOf(gentleReach, 0.6f, 0.79f)
            val along = reaches.map { measure(width, height, it, longIsHorizontal, tier) }
            val across = reaches.map { measure(width, height, it, !longIsHorizontal, tier) }
            val (gentle, brisk, edge) = along
            val label = "${width.toInt()}x${height.toInt()} tier $tier"
            println(
                "OBSTACLE CALIBRATION $label: reach $gentleReach=${gentle.speed} 0.6=${brisk.speed} 0.79=${edge.speed} " +
                    "start=${brisk.start} lethal=${brisk.lethal} short axis=${across.map { it.speed }}",
            )
            assertTrue(gentle.speed < gentle.start, "$label: reach $gentleReach (${gentle.speed}) below ${gentle.start}")
            assertTrue(brisk.speed >= brisk.start && brisk.speed < brisk.lethal,
                "$label: reach 0.6 (${brisk.speed}) inside ${brisk.start}..${brisk.lethal}")
            assertTrue(edge.speed >= edge.lethal, "$label: reach 0.79 (${edge.speed}) at least ${edge.lethal}")
            // Across the short axis the same reach flies slower against the very same bands.
            for (index in reaches.indices) {
                assertEquals(along[index].start, across[index].start, "$label: the damage start ignores the heading")
                assertEquals(along[index].lethal, across[index].lethal, "$label: the lethal speed ignores the heading")
                assertTrue(across[index].speed < along[index].speed, "$label reach ${reaches[index]}: the short axis is slower")
            }
        }
    }

    @Test
    fun theSteadySpeedModelMatchesMeasuredFlight() {
        for ((width, height) in viewports) for (reach in listOf(0.35f, 0.6f, 0.79f)) {
            val viewport = buildTestState(content = emptyWorld).apply { resize(width, height) }
            val model = viewport.steadyCursorSpeed(reach)
            val measured = measure(width, height, reach, horizontal = width >= height, tier = 3)
            val shortModel = viewport.steadyCursorSpeedAtOffset(reach * min(width, height) * 0.5f)
            val shortMeasured = measure(width, height, reach, horizontal = width < height, tier = 3)
            println(
                "STEADY MODEL ${width.toInt()}x${height.toInt()} reach $reach: long model=$model measured=${measured.speed} " +
                    "error=${(measured.speed - model) / model}; short model=$shortModel measured=${shortMeasured.speed} " +
                    "error=${(shortMeasured.speed - shortModel) / shortModel}",
            )
            assertEquals(model, measured.speed, model * 0.03f, "steady model at ${width.toInt()} reach $reach")
            assertEquals(shortModel, shortMeasured.speed, shortModel * 0.03f, "short-axis model at ${width.toInt()} reach $reach")
        }
    }

    /** True when a fixed step that ends at [time] falls inside a Pentagon beat window. */
    private fun onBeatAt(time: Float): Boolean {
        val offset = time % BEAT_PERIOD
        return time >= BEAT_PERIOD - BEAT_WINDOW && min(offset, BEAT_PERIOD - offset) <= BEAT_WINDOW
    }

    /**
     * Dashes from a cruise at the dash-safe reach into an asteroid on each of [sizes], both axes, tiers 0 and 3:
     * the Dash hurts along the long axis but never kills. With [onBeat] the Dash leaves on a Pentagon beat.
     */
    private fun assertAGentleDashNeverKills(sizes: List<Pair<Float, Float>>, shape: CoreShape, onBeat: Boolean) {
        for ((width, height) in sizes) for (horizontal in listOf(true, false)) for (tier in listOf(0, 3)) {
            val label = "$shape ${width.toInt()}x${height.toInt()} ${if (horizontal) "x" else "y"} tier $tier"
            val state = cruise(width, height, gentleReach, horizontal, tier, shape)
            state.fly(20f)
            // The Dash leaves in the next fixed step: wait until that step lands on a beat.
            if (onBeat) while (!onBeatAt(state.elapsed + step)) state.fly(step)
            val cruiseSpeed = state.speed
            state.heat = 0f
            state.placeAsteroidAhead(gap = 60f)
            state.requestDash()
            state.runGrace = 1f
            state.simulateStep(step)
            assertEquals(onBeat, state.characterRuntime.dashOnBeat, "$label: the Dash left on a beat: $onBeat")
            val impact = state.flyIntoTheAsteroid()
            assertTrue(impact > cruiseSpeed + state.effectiveDashImpulse() * 0.5f, "$label: the Dash landed ($cruiseSpeed -> $impact)")
            assertEquals(GamePhase.RUNNING, state.phase, "$label: a Dash from reach $gentleReach at $impact must not kill")
            assertTrue(impact < state.obstacleLethalSpeed(), "$label: $impact below ${state.obstacleLethalSpeed()}")
            if (horizontal == width >= height) assertTrue(state.hp < state.maxHp, "$label: along the long axis it hurts ($impact)")
        }
    }

    @Test
    fun aDashFromAGentleCruiseIntoAnAsteroidNeverKills() {
        assertAGentleDashNeverKills(viewports, CoreShape.ORB, onBeat = false)
    }

    /**
     * An on-beat Pentagon Dash pushes harder, so its lethal floor counts the stronger impulse. The floor sets the
     * lethal speed on the 360×640 phone and the 720×540 desktop minimum window.
     */
    @Test
    fun anOnBeatPentagonDashFromAGentleCruiseIntoAnAsteroidNeverKills() {
        assertAGentleDashNeverKills(listOf(360f to 640f, 720f to 540f) + viewports, CoreShape.PENTAGON, onBeat = true)
    }

    @Test
    fun turningAtReachSixTenthsNeverTurnsABandHitIntoALethalOne() {
        for ((width, height) in viewports) for (tier in listOf(0, 3)) {
            val longIsHorizontal = width >= height
            val label = "${width.toInt()}x${height.toInt()} tier $tier"
            val state = cruise(width, height, 0.6f, longIsHorizontal, tier)
            state.fly(20f)
            val lethalBefore = state.obstacleLethalSpeed()
            // A 90 degree turn: momentum carries the speed while the heading swings toward the short axis.
            state.aim(0.6f, !longIsHorizontal)
            state.fly(0.15f)
            assertEquals(lethalBefore, state.obstacleLethalSpeed(), "$label: the turn leaves the lethal speed alone")
            state.placeAsteroidAhead(gap = 20f)
            val impact = state.flyIntoTheAsteroid()
            assertTrue(state.pendingRicochets > 0, "$label: the band hit landed at $impact")
            assertEquals(GamePhase.RUNNING, state.phase, "$label: a band hit at $impact after a turn must not kill")
            assertTrue(state.hp < state.maxHp, "$label: it still hurts")
        }
    }

    /** A Matrix and item state of the edge-pin case: owned node ranks and items on a Core [shape]. */
    private class MatrixState(
        val label: String,
        val ranks: Map<MatrixNodeId, Int>,
        val shape: CoreShape = CoreShape.ORB,
        val items: List<ItemDefinition> = emptyList(),
    )

    /**
     * [count] copies of a roll-cage item: obstacle tuning with 150 u/s of lethal speed, a 35 % damage cut and 15 % more
     * bounce. Two of them add 300 u/s; four reach the item cap.
     */
    private fun cages(count: Int): List<ItemDefinition> = List(count) { index ->
        obstacleProbe(
            "Test Roll Cage $index",
            ItemRule.ObstacleTuning(ObstacleAspect.LETHAL_SPEED, 150f),
            ItemRule.ObstacleTuning(ObstacleAspect.COLLISION_DAMAGE, 0.35f),
            ItemRule.ObstacleTuning(ObstacleAspect.RESTITUTION, 0.15f),
        )
    }

    /**
     * Holding the cursor at reach 0.79 along the long axis flies at least the lethal collision speed whatever the
     * Matrix owns and whatever obstacle tuning the items add: the lethal-speed bonuses of Impact Dampers, Gravity Surfer
     * and the items stop together at the build's own ceiling, the stronger Dash of Vector Thrusters lifts only the
     * gentle-Dash floor, and a heavier or lighter build moves its ceiling with its own speed.
     */
    @Test
    fun edgePinningStaysLethalForEveryMatrixState() {
        val guard = mapOf(MatrixNodeId.IMPACT_DAMPERS to 2, MatrixNodeId.GRAVITY_SURFER to 1, MatrixNodeId.VECTOR_THRUSTERS to 2)
        val guarded = MatrixState("Impact Dampers 2 + Gravity Surfer + Vector Thrusters 2", guard)
        val caged = MatrixState("two roll cages", emptyMap(), items = cages(2))
        val states = listOf(
            MatrixState("none", emptyMap()),
            guarded,
            MatrixState("Juggernaut", mapOf(MatrixNodeId.JUGGERNAUT to 1)),
            MatrixState("Prism + Juggernaut", mapOf(MatrixNodeId.JUGGERNAUT to 1), CoreShape.PRISM),
            // The slowest build with the full bonus: its ceiling leaves the least room under its own pin speed.
            MatrixState("Prism + Juggernaut + guard", guard + (MatrixNodeId.JUGGERNAUT to 1), CoreShape.PRISM),
            MatrixState("Perpetual Drift", mapOf(MatrixNodeId.PERPETUAL_DRIFT to 1)),
            caged,
            MatrixState("guard + two roll cages", guard, items = cages(2)),
            // Every lethal-speed bonus at once: the Matrix guard and the items at their cap on the slowest build.
            MatrixState("Prism + Juggernaut + guard + four roll cages", guard + (MatrixNodeId.JUGGERNAUT to 1), CoreShape.PRISM,
                cages(4)),
        )
        for ((width, height) in viewports) for (tier in listOf(3, 10)) {
            val lethalByState = states.associateWith { matrixState ->
                val label = "${width.toInt()}x${height.toInt()} tier $tier ${matrixState.label}"
                val state = cruise(width, height, 0.79f, width >= height, tier, matrixState.shape, matrixState.ranks, matrixState.items)
                state.fly(20f)
                assertEquals(GamePhase.RUNNING, state.phase, "$label: the pin flies on an empty field")
                val lethal = state.obstacleLethalSpeed()
                val ceiling = state.buildSteadyCursorSpeed(emptyWorld.obstacles.lethalCeilingReach)
                println(
                    "EDGE PIN $label: reach 0.79=${state.speed} lethal=$lethal " +
                        "bonus=${state.obstacleLethalSpeedBonus()} ceiling=$ceiling",
                )
                assertTrue(state.speed >= lethal, "$label: reach 0.79 (${state.speed}) at least $lethal")
                lethal
            }
            val plain = lethalByState.getValue(states.first())
            assertTrue(
                lethalByState.getValue(guarded) > plain,
                "${width.toInt()}x${height.toInt()} tier $tier: the guard still raises the lethal speed over $plain",
            )
            assertTrue(
                lethalByState.getValue(caged) > plain,
                "${width.toInt()}x${height.toInt()} tier $tier: the cages still raise the lethal speed over $plain",
            )
        }
    }

    @Test
    fun prismFlyingIntoAnAsteroidAtLethalSpeedWithoutSecondWindDies() {
        val state = buildTestState(shape = CoreShape.PRISM)
        state.placeObstaclesForTesting(testObstacle(400f, 0f, radius = 120f))
        state.setVelocityForTesting(state.obstacleLethalSpeed() + 100f, 0f)
        state.updatePointer(state.screenWidth, state.screenHeight * 0.5f)
        repeat(60) { if (state.phase == GamePhase.RUNNING) state.simulateStep(step) }
        assertEquals(GamePhase.GAME_OVER, state.phase)
        assertEquals(OBSTACLE_COLLISION_REASON, state.message)
    }

    private companion object {
        const val HUGE_CHUNK: Float = 10_000_000f
    }
}
