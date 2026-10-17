// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.PointOfInterestState
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.TrailPoint
import kinetickk.ball.gameplay.nucleus.model.WeaponOrbital
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
import kinetickk.ball.gameplay.nucleus.reducer.GameReducer
import kinetickk.ball.gameplay.nucleus.reducer.GameReductionResult
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Render interpolation, sub-step frames and density units at the Nucleus boundary. */
class FramePacingTest {
    private val reducer = GameReducer()

    @Test
    fun subStepFramePublishesAlphaAndReusesEveryProjectionByIdentity() {
        val committed = runningState().apply {
            pointsOfInterest = listOf(
                PointOfInterestState(
                    offerId = 0,
                    kind = PointOfInterestKind.SEALED_ANOMALY,
                    x = 400f,
                    y = 0f,
                    expiresAt = 30f,
                ),
            )
            trail += TrailPoint(-10f, 0f)
        }
        val committedModel = committed.toRenderModel()

        val candidate = reduceFrame(committed, MutableGameState.FIXED_STEP * 0.5f)
        val model = candidate.toRenderModel(reusableCollections = committedModel, identitySource = committed)

        assertEquals(0, candidate.lastTransitionSteps)
        assertEquals(0.5f, model.interpolationAlpha, 1e-4f)
        assertSame(committedModel.enemies, model.enemies)
        assertSame(committedModel.projectiles, model.projectiles)
        assertSame(committedModel.pickups, model.pickups)
        assertSame(committedModel.trail, model.trail)
        assertSame(committedModel.weaponNodes, model.weaponNodes)
        assertSame(committedModel.weaponOrbitals, model.weaponOrbitals)
        assertSame(committedModel.pointsOfInterest, model.pointsOfInterest)
        assertSame(committedModel.characterAbility, model.characterAbility)
        assertSame(committedModel.runStatistics, model.runStatistics)
        assertSame(committedModel.equippedRelics, model.equippedRelics)
        assertSame(committedModel.choices, model.choices)
        assertSame(committedModel.build, model.build)
    }

    @Test
    fun alphaFollowsTheAccumulatorWhileRunningAndIsOneOtherwise() {
        val state = runningState()
        state.accumulator = MutableGameState.FIXED_STEP * 0.25f
        assertEquals(0.25f, state.toRenderModel().interpolationAlpha, 1e-5f)
        // A deferred backlog beyond one step presents the newest state.
        state.accumulator = MutableGameState.FIXED_STEP * 3f
        assertEquals(1f, state.toRenderModel().interpolationAlpha)
        state.accumulator = MutableGameState.FIXED_STEP * 0.25f
        state.phase = GamePhase.PAUSED
        assertEquals(1f, state.toRenderModel().interpolationAlpha)
        state.phase = GamePhase.CHOICE
        assertEquals(1f, state.toRenderModel().interpolationAlpha)
    }

    @Test
    fun eachStepCapturesPreviousPositionsAtItsStart() {
        val state = runningState().apply {
            equipWeaponForTesting(WeaponId.MORNINGSTAR)
            setVelocityForTesting(900f, 300f)
            cameraX = -40f
            cameraY = 25f
        }
        val coreBefore = state.coreX to state.coreY
        val cameraBefore = state.cameraX to state.cameraY
        val morningstarBefore = state.morningstarX to state.morningstarY

        val stepped = reduceFrame(state, MutableGameState.FIXED_STEP)
        val model = stepped.toRenderModel()

        assertEquals(1, stepped.lastTransitionSteps)
        assertEquals(coreBefore, model.previousCoreX to model.previousCoreY)
        assertEquals(cameraBefore, model.previousCameraX to model.previousCameraY)
        assertEquals(morningstarBefore, model.previousMorningstarX to model.previousMorningstarY)
        assertTrue(model.coreX != model.previousCoreX && model.cameraX != model.previousCameraX)
        assertTrue(model.morningstarX != model.previousMorningstarX)
    }

    @Test
    fun orbitalsInterpolateAlongTheirOrbitButAppearInPlaceWhenFormed() {
        val state = runningState().apply { equipWeaponForTesting(WeaponId.ION_SWARM) }
        val formed = reduceFrame(state, MutableGameState.FIXED_STEP)
        assertTrue(formed.weaponOrbitals.isNotEmpty())
        formed.weaponOrbitals.forEach { orbital ->
            assertEquals(orbital.x to orbital.y, orbital.previousX to orbital.previousY)
        }
        val before = formed.weaponOrbitals.map { it.x to it.y }
        val moved = reduceFrame(formed, MutableGameState.FIXED_STEP)
        val projection = moved.toRenderModel().weaponOrbitals
        assertEquals(before, projection.map { it.previousX to it.previousY })
        assertTrue(projection.any { it.x != it.previousX || it.y != it.previousY })
    }

    @Test
    fun worldRebaseShiftsEveryPreviousPosition() {
        val state = runningState().apply {
            coreX = 260_000f
            coreY = -10f
            previousCoreX = 259_990f
            cameraX = 259_950f
            previousCameraX = 259_940f
            previousCameraY = -12f
            morningstarX = 260_100f
            previousMorningstarX = 260_090f
            pickups += Pickup(PickupType.DATA, 260_050f, 5f).apply { previousX = 260_040f; previousY = 4f }
            projectiles += Projectile(x = 260_020f, y = 0f, vx = 0f, vy = 0f, radius = 2f, life = 1f).apply { previousX = 260_010f }
            weaponOrbitals += WeaponOrbital(index = 0, x = 260_030f, y = 1f, radius = 4f).apply { previousX = 260_025f }
        }

        state.rebaseWorldIfNeeded()

        assertEquals(-10f, state.previousCoreX)
        assertEquals(-60f, state.previousCameraX)
        assertEquals(-2f, state.previousCameraY)
        assertEquals(90f, state.previousMorningstarX)
        assertEquals(40f, state.pickups.single().previousX)
        assertEquals(14f, state.pickups.single().previousY)
        assertEquals(10f, state.projectiles.single().previousX)
        assertEquals(25f, state.weaponOrbitals.single().previousX)
    }

    @Test
    fun viewportDensityIsAcceptedStateAndPublished() {
        val state = runningState()
        val resized = assertIs<GameReductionResult.Accepted>(
            reducer.reduce(EngineState(state), GameplayInteractionPulse.ViewportChanged.fromValidated(640f, 400f, 2f)),
        ).state.model
        val model = resized.toRenderModel()
        assertEquals(640f, model.screenWidth)
        assertEquals(400f, model.screenHeight)
        assertEquals(2f, model.uiScale)
        assertEquals(2f, model.viewportDensity)
        // Below one the HUD keeps its minimum scale, but the world still follows the density.
        val dense = assertIs<GameReductionResult.Accepted>(
            reducer.reduce(EngineState(resized), GameplayInteractionPulse.ViewportChanged.fromValidated(640f, 400f, 0.75f)),
        ).state.model
        assertEquals(1f, dense.uiScale)
        assertEquals(0.75f, dense.toRenderModel().viewportDensity)
    }

    private fun runningState(): MutableGameState = MutableGameState(canonicalGameplayContent, seed = 4_242).apply {
        startRun()
        spawnClock = 1_000f
        enemies.clear()
        addEnemyForTesting(x = 300f, y = 20f).apply { vx = -50f }
        takeSoundCues()
        takeVisualFxCues()
    }

    private fun reduceFrame(state: MutableGameState, seconds: Float): MutableGameState {
        val accepted = assertIs<GameReductionResult.Accepted>(
            reducer.reduce(EngineState(state), GameplayInteractionPulse.FrameElapsed.fromValidated(seconds)),
        )
        return accepted.state.model.apply {
            takeSoundCues()
            takeVisualFxCues()
        }
    }
}
