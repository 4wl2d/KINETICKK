// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.MatrixEffect
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatDelta
import kinetickk.ball.content.api.SteeringTuning
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CoreSteeringTest {
    private val piloting = SteeringTuning()

    @Test
    fun theAimAsksTheLawForTheRequestedAccelerationAtFullAuthority() {
        // The aim inverts the piloting law itself: gate, grip, sling and launch assist are all part of it.
        val state = steeringState()
        val velocities = listOf(0f to 0f, 640f to -210f, -1_200f to 900f)
        val requests = listOf(900f to 0f, -400f to 1_300f, 150f to -2_000f, -1_700f to -1_700f)
        for ((velocityX, velocityY) in velocities) for ((requestX, requestY) in requests) {
            state.velocityX = velocityX
            state.velocityY = velocityY
            val aim = state.steeringAimFor(requestX, requestY)
            val law = state.lawForAim(aim)
            val case = "request=($requestX, $requestY) velocity=($velocityX, $velocityY)"
            assertEquals(requestX, law.x, abs(requestX) * 1e-4f + 0.05f, case)
            assertEquals(requestY, law.y, abs(requestY) * 1e-4f + 0.05f, case)
        }
    }

    @Test
    fun theInverseReproducesTheRequestedAcceleration() {
        // Every request is what the law gives for some tether, so each one is reachable.
        val state = steeringState()
        var braked = 0
        var rested = 0
        repeat(200) { index ->
            state.braking = unit(index, 1) < 0.15f
            state.polarityStability = if (state.braking) 1f else 0.3f + 0.7f * unit(index, 2)
            state.overdriveTime = if (unit(index, 3) < 0.3f) 2f else 0f
            state.magnetStrength = 3f + 4f * unit(index, 4)
            state.gripStrength = 3f * unit(index, 5)
            state.slingStrength = 3f * unit(index, 6)
            val speed = if (index % 10 == 0) 0f else 50f + 2_550f * unit(index, 7)
            val heading = TWO_PI * unit(index, 8)
            state.velocityX = speed * cos(heading)
            state.velocityY = speed * sin(heading)
            // The camera trails the Core by about its steady lag.
            val lag = CAMERA_LAG_SECONDS * (0.5f + unit(index, 9))
            state.cameraX = state.coreX - state.velocityX * lag
            state.cameraY = state.coreY - state.velocityY * lag
            val distance = 65f + 535f * unit(index, 10)
            val direction = TWO_PI * unit(index, 11)
            val request = state.lawForAim(steeringAim(direction, distance))
            val aim = state.steeringAimFor(request.x, request.y)
            val law = state.lawForAim(aim)
            val error = hypot(law.x - request.x, law.y - request.y)
            val size = hypot(request.x, request.y)
            assertTrue(
                error <= 0.01f * size,
                "state $index (speed $speed, braking ${state.braking}): request (${request.x}, ${request.y}), " +
                    "aim gives (${law.x}, ${law.y})",
            )
            if (state.braking) braked++
            if (speed == 0f) rested++
        }
        assertTrue(braked > 0 && rested > 0, "the states include braking ($braked) and resting ($rested) Cores")
    }

    @Test
    fun theInverseSearchesTethersUpToFourLongHalfAxes() {
        val state = steeringState().apply {
            velocityX = 900f
            cameraX = coreX - CAMERA_LAG_SECONDS * 900f
        }
        val bound = 4f * 640f
        fun requestFor(distance: Float): SteeringVector = state.lawForAim(steeringAim(0f, distance))
        val inside = requestFor(bound - 10f)
        assertEquals(bound - 10f, state.steeringAimFor(inside.x, inside.y).distance, 1f, "a tether inside the bound is found")
        val beyond = requestFor(bound + 200f)
        assertEquals(bound, state.steeringAimFor(beyond.x, beyond.y).distance, 1f, "a request past the bound saturates at it")
    }

    @Test
    fun lateralPointingDoesNotBrakeTheCore() {
        val speed = 1_500f
        // Steady flight: the camera trails the Core by λ·v, so the tether is the pointer offset minus that lag.
        val tetherX = -CAMERA_LAG_SECONDS * speed
        val now = steeringState().steeringAcceleration(tetherX, 300f, 0f, 300f, speed, 0f)
        val released = steeringState(SteeringTuning.LEGACY).steeringAcceleration(tetherX, 300f, 0f, 300f, speed, 0f)
        assertTrue(now.x >= 0f, "a sideways pointer must not brake the Core (${now.x})")
        assertTrue(released.x < 0f, "the released law braked it through camera lag (${released.x})")
    }

    @Test
    fun gradualThrustKeepsFullTurningAndCounterSteeringAuthority() {
        val gentle = steeringState(piloting.copy(spoolGain = 0f, slingShare = 0f))
        val full = steeringState(piloting.copy(forwardThrustScale = 1f, spoolGain = 0f, slingShare = 0f))
        val forward = gentle.steeringAcceleration(300f, 0f, 300f, 0f, 700f, 0f)
        val unrestricted = full.steeringAcceleration(300f, 0f, 300f, 0f, 700f, 0f)
        assertTrue(forward.x < unrestricted.x * 0.5f, "ordinary launch thrust is substantially gentler")
        val lateral = gentle.steeringAcceleration(0f, 300f, 0f, 300f, 5_000f, 0f)
        val unrestrictedLateral = full.steeringAcceleration(0f, 300f, 0f, 300f, 5_000f, 0f)
        assertEquals(unrestrictedLateral.y, lateral.y, "full-grip turning authority is preserved")
        val backwards = gentle.steeringAcceleration(-300f, 0f, -300f, 0f, 700f, 0f)
        val unrestrictedBackwards = full.steeringAcceleration(-300f, 0f, -300f, 0f, 700f, 0f)
        assertEquals(unrestrictedBackwards.x, backwards.x, "counter-steering still slows the Core without Brake")
    }

    @Test
    fun ordinaryStraightTravelHasTheSameDragAsTurning() {
        val state = steeringState()
        state.coreStraightness = 0f
        val turning = state.steeringDrag()
        state.coreStraightness = 1f
        assertEquals(turning, state.steeringDrag(), "straight travel must not force slaloming to avoid extra drag")
    }

    @Test
    fun pointingBehindTheScreenCentreKeepsFullCounterThrust() {
        val state = steeringState()
        val legacy = steeringState(SteeringTuning.LEGACY)
        val magnetism = state.effectiveMagnetism()
        for ((velocityX, velocityY) in listOf(1_500f to 0f, 1_200f to -900f)) {
            val speed = hypot(velocityX, velocityY)
            val headingX = velocityX / speed
            val headingY = velocityY / speed
            // The pointer sits 400 dp straight behind the screen centre along the heading.
            val offsetX = -400f * headingX
            val offsetY = -400f * headingY
            val tetherX = offsetX - CAMERA_LAG_SECONDS * velocityX
            val tetherY = offsetY - CAMERA_LAG_SECONDS * velocityY
            val distance = hypot(tetherX, tetherY)
            val now = state.steeringAcceleration(tetherX, tetherY, offsetX, offsetY, velocityX, velocityY)
            val released = legacy.steeringAcceleration(tetherX, tetherY, offsetX, offsetY, velocityX, velocityY)
            val expected = released.along(velocityX, velocityY) *
                (piloting.basePull + magnetism * distance) / (SteeringTuning.LEGACY.basePull + magnetism * distance)
            val along = now.along(velocityX, velocityY)
            assertTrue(along < 0f, "pointing behind decelerates ($along)")
            assertEquals(expected, along, abs(expected) * 1e-3f, "velocity=($velocityX, $velocityY)")
        }
    }

    @Test
    fun theBrakeFliesThePlainLaw() {
        val state = steeringState().apply { braking = true }
        for (stability in listOf(1f, 0.5f)) for ((velocityX, velocityY) in VELOCITIES) for ((tetherX, tetherY) in TETHERS) {
            state.polarityStability = stability
            val reference = state.releasedLaw(piloting.basePull, tetherX, tetherY, velocityX, velocityY)
            for ((offsetX, offsetY) in OFFSETS) {
                val law = state.steeringAcceleration(tetherX, tetherY, offsetX, offsetY, velocityX, velocityY)
                val tolerance = 1e-4f * max(hypot(reference.first, reference.second), piloting.basePull)
                val case = "stability=$stability v=($velocityX, $velocityY) tether=($tetherX, $tetherY) offset=($offsetX, $offsetY)"
                assertEquals(reference.first, law.x, tolerance, case)
                assertEquals(reference.second, law.y, tolerance, case)
            }
        }
    }

    @Test
    fun gripGrowsWithSpeedToItsFullReach() {
        val state = steeringState()
        val legacy = steeringState(SteeringTuning.LEGACY)
        val magnetism = state.effectiveMagnetism()
        val full = state.steadyCursorSpeed(piloting.gripFullReach)
        fun ratio(speed: Float): Float {
            val tetherX = -CAMERA_LAG_SECONDS * speed
            val now = state.steeringAcceleration(tetherX, 300f, 0f, 300f, speed, 0f)
            val released = legacy.steeringAcceleration(tetherX, 300f, 0f, 300f, speed, 0f)
            val distance = hypot(tetherX, 300f)
            val pullRatio =
                (piloting.basePull + magnetism * distance) / (SteeringTuning.LEGACY.basePull + magnetism * distance)
            return now.lateral(speed, 0f) / (released.lateral(speed, 0f) * pullRatio)
        }
        assertEquals(1f, ratio(full * 0.001f), 1e-3f, "slow handling keeps the released turning pull")
        assertEquals(1f + piloting.gripGain * 0.5f, ratio(full * 0.5f), 1e-3f, "grip grows linearly with speed")
        assertEquals(1f + piloting.gripGain, ratio(full), 1e-3f, "full grip at the full-grip reach's steady speed")
        assertEquals(1f + piloting.gripGain, ratio(full * 1.6f), 1e-3f, "grip stops growing above it")
    }

    @Test
    fun slingStopsAboveTheSoftSpeedOfThePointerOffset() {
        val state = steeringState()
        val withoutSling = steeringState(piloting.copy(slingShare = 0f))
        val offset = 300f
        val soft = state.steadyCursorSpeedAtOffset(offset) * (1f + piloting.slingHeadroom)
        fun slingAndLateral(speed: Float): Pair<Float, Float> {
            val tetherX = -CAMERA_LAG_SECONDS * speed
            val now = state.steeringAcceleration(tetherX, offset, 0f, offset, speed, 0f)
            val plain = withoutSling.steeringAcceleration(tetherX, offset, 0f, offset, speed, 0f)
            return now.along(speed, 0f) - plain.along(speed, 0f) to now.lateral(speed, 0f)
        }
        for (speed in listOf(soft, soft * 1.2f, soft * 2f)) {
            assertEquals(0f, slingAndLateral(speed).first, 1e-3f, "no sling at $speed (soft speed $soft)")
        }
        for (speed in listOf(200f, soft * 0.4f, soft * 0.69f)) {
            val (sling, lateral) = slingAndLateral(speed)
            assertEquals(piloting.slingShare * piloting.forwardThrustScale * lateral, sling, lateral * 1e-4f, "full sling at $speed")
        }
        val (halfway, lateral) = slingAndLateral(soft * 0.85f)
        assertEquals(piloting.slingShare * piloting.forwardThrustScale * lateral * 0.5f, halfway, lateral * 1e-3f, "the sling fades linearly")
    }

    @Test
    fun launchAssistVanishesAtTheSteadySpeedOfTheForwardOffset() {
        val state = steeringState()
        val withoutAssist = steeringState(piloting.copy(spoolGain = 0f))
        val magnetism = state.effectiveMagnetism()
        val throttle = 400f
        val steady = state.steadyCursorSpeedAtOffset(throttle)
        fun assist(offsetX: Float, offsetY: Float, speed: Float): Float {
            val tetherX = offsetX - CAMERA_LAG_SECONDS * speed
            val now = state.steeringAcceleration(tetherX, offsetY, offsetX, offsetY, speed, 0f)
            val plain = withoutAssist.steeringAcceleration(tetherX, offsetY, offsetX, offsetY, speed, 0f)
            return now.x - plain.x
        }
        val fullAssist = piloting.spoolGain * piloting.forwardThrustScale * magnetism * throttle
        assertEquals(fullAssist, assist(throttle, 0f, 0f), fullAssist * 1e-4f, "from rest the assist is whole")
        assertEquals(fullAssist * 0.5f, assist(throttle, 0f, steady * 0.5f), fullAssist * 1e-3f, "half the deficit, half the assist")
        assertEquals(0f, assist(throttle, 0f, steady), fullAssist * 1e-4f, "nothing at the forward offset's steady speed")
        assertEquals(0f, assist(throttle, 0f, steady * 1.3f), "nothing above it")
        assertEquals(0f, assist(0f, throttle, steady * 0.5f), "nothing for a sideways pointer")
        assertEquals(0f, assist(-throttle, 0f, steady * 0.5f), "nothing for a pointer behind the centre")
    }

    @Test
    fun exhaustionSuppressesPullSlingAndAssistAlike() {
        val exhausted = steeringState().apply { polarityStability = 0f }
        val rested = steeringState()
        var restedGains = 0
        for (speed in listOf(300f, 900f, 1_600f)) for (heading in 0 until 6) for (direction in 0 until 8) for (reach in listOf(100f, 350f)) {
            val velocityX = speed * cos(heading * 1.1f)
            val velocityY = speed * sin(heading * 1.1f)
            val offsetX = reach * cos(direction * PI_OVER_FOUR)
            val offsetY = reach * sin(direction * PI_OVER_FOUR)
            val tetherX = offsetX - CAMERA_LAG_SECONDS * velocityX
            val tetherY = offsetY - CAMERA_LAG_SECONDS * velocityY
            val spent = exhausted.steeringAcceleration(tetherX, tetherY, offsetX, offsetY, velocityX, velocityY)
            val along = spent.along(velocityX, velocityY)
            assertTrue(along <= 1e-3f * hypot(spent.x, spent.y), "speed $speed heading $heading offset ($offsetX, $offsetY): $along")
            val fresh = rested.steeringAcceleration(tetherX, tetherY, offsetX, offsetY, velocityX, velocityY)
            if (fresh.along(velocityX, velocityY) > 1f) restedGains++
        }
        assertTrue(restedGains > 0, "the same offsets push a rested Core forward")
    }

    @Test
    fun straightnessBuildsOnStraightFlightAndDrainsInCurves() {
        val state = steeringState().apply {
            velocityX = 800f
            smoothedVelocityX = 800f
        }
        repeat(144) { state.straightnessStep() }
        assertTrue(state.coreStraightness >= 0.6f, "1.2 s of straight flight at 800 u/s: ${state.coreStraightness}")
        assertEquals(
            state.dragCoefficient * (1f + piloting.straightDrag * state.coreStraightness),
            state.steeringDrag(),
            1e-6f,
            "straightness raises the drag",
        )
        var angle = 0f
        repeat(60) {
            angle += STEP
            state.velocityX = 800f * cos(angle)
            state.velocityY = 800f * sin(angle)
            state.straightnessStep()
        }
        assertTrue(state.coreStraightness <= 0.1f, "half a second of a 1 rad/s turn drains it: ${state.coreStraightness}")

        val slow = steeringState().apply {
            velocityX = 150f
            smoothedVelocityX = 150f
        }
        repeat(360) { slow.straightnessStep() }
        assertEquals(0f, slow.coreStraightness, "below the minimum turn speed nothing builds")
    }

    @Test
    fun steeringStatsClampAtTheContentBound() {
        val bound = piloting.maximumStatMultiplier
        val speed = 900f
        val tetherX = -CAMERA_LAG_SECONDS * speed
        fun law(grip: Float, sling: Float): Pair<Float, Float> = steeringState().apply {
            gripStrength = grip
            slingStrength = sling
        }.steeringAcceleration(tetherX, 350f, 0f, 350f, speed, 0f).let { it.x to it.y }
        assertEquals(law(bound, 1f), law(bound + 0.5f, 1f), "grip above the bound clamps to it")
        assertNotEquals(law(bound, 1f), law(bound - 0.1f, 1f), "grip just below the bound still counts")
        assertEquals(law(0f, 1f), law(-1f, 1f), "negative grip clamps to none")
        assertNotEquals(law(0f, 1f), law(0.1f, 1f))
        assertEquals(law(1f, bound), law(1f, bound + 0.5f), "sling above the bound clamps to it")
        assertNotEquals(law(1f, bound), law(1f, bound - 0.1f), "sling just below the bound still counts")
        assertEquals(law(1f, 0f), law(1f, -1f), "negative sling clamps to none")
        assertNotEquals(law(1f, 0f), law(1f, 0.1f))
    }

    @Test
    fun steeringStateIsIsolatedInReductionCopiesAndResetForTheNextRun() {
        val source = steeringState().apply { coreStraightness = 0.4f }
        val fork = source.copyForReduction()
        assertEquals(0.4f, fork.coreStraightness)
        assertEquals(1f, fork.gripStrength)
        assertEquals(1f, fork.slingStrength)
        fork.coreStraightness = 0.8f
        fork.gripStrength = 2.5f
        fork.slingStrength = 0.5f
        assertEquals(0.4f, source.coreStraightness)
        assertEquals(1f, source.gripStrength)
        assertEquals(1f, source.slingStrength)
        fork.startRun()
        assertEquals(0f, fork.coreStraightness)
        assertEquals(1f, fork.gripStrength)
        assertEquals(1f, fork.slingStrength)

        val matrixContent = canonicalGameplayContent.withMatrixEffects(
            MatrixNodeId.IMPACT_DRIVER,
            MatrixEffect.Stat(StatDelta(ItemEffect.GRIP, 0.5f)),
            MatrixEffect.Stat(StatDelta(ItemEffect.SLING, 0.25f)),
        )
        val withMatrix = MutableGameState(matrixContent, seed = 41).apply {
            matrixRanks[matrixContent.matrixNodes.indexOfFirst { it.id == MatrixNodeId.IMPACT_DRIVER }] = 2
            startRun()
        }
        assertEquals(2f, withMatrix.gripStrength, 1e-6f, "the Matrix GRIP total lands on the run's start")
        assertEquals(1.5f, withMatrix.slingStrength, 1e-6f, "the Matrix SLING total lands on the run's start")
        withMatrix.coreStraightness = 1f
        withMatrix.gripStrength = 3f
        withMatrix.slingStrength = 3f
        withMatrix.startRun()
        assertEquals(0f, withMatrix.coreStraightness)
        assertEquals(2f, withMatrix.gripStrength, 1e-6f)
        assertEquals(1.5f, withMatrix.slingStrength, 1e-6f)
    }

    @Test
    fun theSteadyModelIsHeadingIndependentAndUsesBaseStats() {
        assertEquals(0.12921f, CAMERA_LAG_SECONDS, 1e-5f, "the step-exact camera lag")
        val landscape = steeringState()
        val portrait = steeringState().apply { resize(800f, 1_280f) }
        val tuned = steeringState().apply {
            magnetStrength = 6f
            dragCoefficient = 0.2f
            coreStraightness = 0.3f
            overdriveTime = 5f
        }
        val perpetual = buildTestState(RelicId.PERPETUAL_VECTOR).apply { resize(1_280f, 800f) }
        assertTrue(perpetual.build.upside(RelicId.PERPETUAL_VECTOR), "precondition: the Perpetual Vector upside is on")
        for (reach in listOf(0.3f, 0.5f, 0.79f)) {
            val offset = reach * 640f
            val model = landscape.steadyCursorSpeed(reach)
            val closedForm = piloting.forwardThrustScale * (piloting.basePull + BASE_MAGNET_STRENGTH * offset) /
                (BASE_DRAG_COEFFICIENT * (1f + piloting.straightDrag) +
                    piloting.forwardThrustScale * BASE_MAGNET_STRENGTH * CAMERA_LAG_SECONDS)
            assertEquals(closedForm, model, model * 1e-5f, "reach $reach")
            assertEquals(model, portrait.steadyCursorSpeed(reach), "a turned viewport keeps the long axis")
            assertEquals(model, tuned.steadyCursorSpeed(reach), "the model uses the base stats")
            assertEquals(model, landscape.buildSteadyCursorSpeed(reach), model * 1e-5f, "a base build flies the model")
            val tunedBuild = piloting.forwardThrustScale * (piloting.basePull + 6f * offset) /
                (0.2f * (1f + piloting.straightDrag) + piloting.forwardThrustScale * 6f * CAMERA_LAG_SECONDS)
            assertEquals(tunedBuild, tuned.buildSteadyCursorSpeed(reach), tunedBuild * 1e-5f, "the build model follows the stats")
            val perpetualBuild = piloting.forwardThrustScale * (piloting.basePull + perpetual.effectiveMagnetism() * offset) /
                (perpetual.dragCoefficient * 0.15f * (1f + piloting.straightDrag) +
                    piloting.forwardThrustScale * perpetual.effectiveMagnetism() * CAMERA_LAG_SECONDS)
            assertEquals(perpetualBuild, perpetual.buildSteadyCursorSpeed(reach), perpetualBuild * 1e-5f)
        }
    }

    @Test
    fun legacyTuningReproducesTheReleasedLaw() {
        val legacy = steeringState(SteeringTuning.LEGACY)
        repeat(1_000) { index ->
            legacy.polarityStability = unit(index, 1)
            legacy.overdriveTime = if (unit(index, 2) < 0.3f) 2f else 0f
            legacy.braking = unit(index, 3) < 0.25f
            legacy.magnetStrength = 3f + 4f * unit(index, 4)
            val tetherX = signed(index, 5) * 900f
            val tetherY = signed(index, 6) * 900f
            val offsetX = signed(index, 7) * 640f
            val offsetY = signed(index, 8) * 400f
            val slow = index % 10 == 0
            val velocityX = signed(index, 9) * if (slow) 0.6f else 2_600f
            val velocityY = signed(index, 10) * if (slow) 0.6f else 2_600f
            val law = legacy.steeringAcceleration(tetherX, tetherY, offsetX, offsetY, velocityX, velocityY)
            val reference = legacy.releasedLaw(SteeringTuning.LEGACY.basePull, tetherX, tetherY, velocityX, velocityY)
            val tolerance = 1e-4f * max(hypot(reference.first, reference.second), SteeringTuning.LEGACY.basePull)
            assertEquals(reference.first, law.x, tolerance, "evaluation $index")
            assertEquals(reference.second, law.y, tolerance, "evaluation $index")
        }
        legacy.braking = false
        legacy.coreStraightness = 1f
        assertEquals(legacy.dragCoefficient, legacy.steeringDrag(), "no straight-line fatigue in the released law")
    }

    private fun steeringState(tuning: SteeringTuning = piloting): MutableGameState =
        buildTestState(content = canonicalGameplayContent.withSteering(tuning)).apply {
            resize(1_280f, 800f)
            runGrace = 1_000f
            polarityStability = 1f
            overdriveTime = 0f
            braking = false
        }

    private fun GameplayContentSnapshot.withSteering(tuning: SteeringTuning): GameplayContentSnapshot =
        copy(tempo = tempo.copy(steering = tuning))

    private fun GameplayContentSnapshot.withMatrixEffects(node: MatrixNodeId, vararg effects: MatrixEffect): GameplayContentSnapshot =
        copy(matrixNodes = matrixNodes.map { definition ->
            if (definition.id == node) definition.copy(effects = effects.toList().toImmutableList()) else definition
        }.toImmutableList())

    /** The law for the singularity held at [aim] from the Core, the pointer at the camera's current lag plus that tether. */
    private fun MutableGameState.lawForAim(aim: SteeringAim): SteeringVector {
        val tetherX = cos(aim.heading) * aim.distance
        val tetherY = sin(aim.heading) * aim.distance
        return steeringAcceleration(
            tetherX,
            tetherY,
            coreX - cameraX + tetherX,
            coreY - cameraY + tetherY,
            velocityX,
            velocityY,
        )
    }

    /** One fixed step of the heading bookkeeping as updateCore runs it. */
    private fun MutableGameState.straightnessStep() {
        val beforeX = smoothedVelocityX
        val beforeY = smoothedVelocityY
        updatePolarityStability(STEP)
        updateStraightness(STEP, beforeX, beforeY)
    }

    /** The 0.5.0 law, inline: pull toward the singularity; exhaustion weakens only the part along momentum. */
    private fun MutableGameState.releasedLaw(
        basePull: Float,
        tetherX: Float,
        tetherY: Float,
        velocityX: Float,
        velocityY: Float,
    ): Pair<Float, Float> {
        val distance = max(0.001f, hypot(tetherX, tetherY))
        val directionX = tetherX / distance
        val directionY = tetherY / distance
        val pull = (basePull + distance * effectiveMagnetism()) * if (overdriveTime > 0f) 1.3f else 1f
        val speed = hypot(velocityX, velocityY)
        val forwardX = if (speed > 1f) velocityX / speed else directionX
        val forwardY = if (speed > 1f) velocityY / speed else directionY
        val forwardPull = max(0f, (directionX * forwardX + directionY * forwardY) * pull)
        val suppressed = if (speed > 1f) forwardPull * (1f - polarityStability * polarityStability) else 0f
        val brake = if (braking) 1.12f else 1f
        return (directionX * pull - forwardX * suppressed) * brake to (directionY * pull - forwardY * suppressed) * brake
    }

    private fun SteeringVector.along(velocityX: Float, velocityY: Float): Float =
        (x * velocityX + y * velocityY) / hypot(velocityX, velocityY)

    private fun SteeringVector.lateral(velocityX: Float, velocityY: Float): Float =
        abs(y * velocityX - x * velocityY) / hypot(velocityX, velocityY)

    /** Stateless SplitMix64 over the evaluation index and a field salt: no game random stream is touched. */
    private fun unit(index: Int, salt: Int): Float {
        var z = index.toLong() * 0x9E37_79B9_7F4A_7C15uL.toLong() + salt * 0x632B_E59B_D9B4_E019L
        z = (z xor (z ushr 30)) * -0x40A7_B892_E31B_1A47L
        z = (z xor (z ushr 27)) * -0x6B2F_B644_ECCE_EE15L
        z = z xor (z ushr 31)
        return (z ushr 40).toFloat() / (1L shl 24).toFloat()
    }

    private fun signed(index: Int, salt: Int): Float = unit(index, salt) * 2f - 1f

    private companion object {
        const val STEP = MutableGameState.FIXED_STEP
        const val PI_OVER_FOUR = 0.785_398_16f
        const val TWO_PI = 6.283_185_3f
        val VELOCITIES = listOf(0f to 0f, 1_400f to 0f, -600f to 900f, 2_100f to -1_300f)
        val TETHERS = listOf(300f to 0f, -250f to 120f, 40f to -380f)
        val OFFSETS = listOf(0f to 0f, 500f to 0f, 0f to 300f, -400f to -200f)
    }
}
