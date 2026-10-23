// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.input

import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.interaction.layout.RunningControlMask
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.interaction.layout.runningControlBounds
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameplayInputMappingTest {
    @Test
    fun runningHudMapsOnlyToLiveRunActions() {
        val model = hitTestState(GamePhase.RUNNING)
        val controls = runningControlBounds(model.screenWidth, model.screenHeight, model.uiScale).associate { it.target to it.bounds }
        val dashCenter = controls.getValue(RunningControlTarget.DASH).center
        val brakeCenter = controls.getValue(RunningControlTarget.BRAKE).center

        val dash = assertIs<GameplayInput.Action>(model.resolveGameplayPress(dashCenter.x, dashCenter.y))
        val brake = assertIs<GameplayInput.Action>(model.resolveGameplayPress(brakeCenter.x, brakeCenter.y))

        assertSame(GameplayInteractionPulse.DashRequested, dash.action)
        assertIs<GameplayInteractionPulse.BrakeChanged>(brake.action)
    }

    @Test
    fun desktopPauseButtonTogglesPauseLikeTheKeyboardAndIsNotSteering() {
        val model = hitTestState(GamePhase.RUNNING)
        val pause = runningControlBounds(model.screenWidth, model.screenHeight, model.uiScale)
            .single { it.target == RunningControlTarget.PAUSE }.bounds

        val input = assertIs<GameplayInput.Action>(model.resolveGameplayPress(pause.center.x, pause.center.y))
        assertSame(GameplayInteractionPulse.PauseToggled, input.action)
        assertTrue(model.isHudControlPosition(pause.left, pause.top))
        assertFalse(model.isHudControlPosition(pause.center.x, pause.bottom + 1f))
    }

    @Test
    fun rectangularBrakeTargetIncludesItsCornersAndDoesNotStealTheArenaAbove() {
        val running = hitTestState(GamePhase.RUNNING)
        val brakeBounds = runningControlBounds(
            running.screenWidth,
            running.screenHeight,
            running.uiScale,
        ).single { it.target == RunningControlTarget.BRAKE }.bounds
        val cornerX = brakeBounds.left + 1f
        val cornerY = brakeBounds.top + 1f

        assertIs<GameplayInput.Action>(running.resolveGameplayPress(cornerX, cornerY))
        assertTrue(running.isHudControlPosition(cornerX, cornerY))
        assertNull(running.resolveGameplayPress(brakeBounds.center.x, brakeBounds.top - 1f))
        assertFalse(running.isHudControlPosition(brakeBounds.center.x, brakeBounds.top - 1f))
    }

    @Test
    fun runningHudClassificationUsesTheSameCanonicalTargetsAsActionResolution() {
        val running = hitTestState(GamePhase.RUNNING)

        runningControlBounds(running.screenWidth, running.screenHeight, running.uiScale, running.controlMask)
            .forEach { control ->
                val center = control.bounds.center
                assertTrue(running.isHudControlPosition(center.x, center.y))
                assertIs<GameplayInput>(running.resolveGameplayPress(center.x, center.y))
            }

        assertFalse(running.isHudControlPosition(640f, 360f))
        assertNull(running.resolveGameplayPress(640f, 360f))
    }

    @Test
    fun compactRunningControlsMapDashBrakePauseAndPerformanceFromSharedGeometry() {
        val running = hitTestState(
            phase = GamePhase.RUNNING,
            screenWidth = 2_400f,
            screenHeight = 1_080f,
            uiScale = 3f,
        )

        runningControlBounds(running.screenWidth, running.screenHeight, running.uiScale, running.controlMask).forEach { control ->
            val input = running.resolveGameplayPress(control.bounds.center.x, control.bounds.center.y)
            when (control.target) {
                RunningControlTarget.DASH -> assertSame(
                    GameplayInteractionPulse.DashRequested,
                    assertIs<GameplayInput.Action>(input).action,
                )
                RunningControlTarget.BRAKE -> assertIs<GameplayInteractionPulse.BrakeChanged>(
                    assertIs<GameplayInput.Action>(input).action,
                )
                RunningControlTarget.PERFORMANCE -> assertSame(GameplayInput.TogglePerformance, input)
                RunningControlTarget.PAUSE -> assertSame(
                    GameplayInteractionPulse.PauseToggled,
                    assertIs<GameplayInput.Action>(input).action,
                )
                RunningControlTarget.SKILL_1, RunningControlTarget.SKILL_2, RunningControlTarget.SKILL_3,
                RunningControlTarget.APEX,
                -> assertNull(input, "${control.target} is not placed without its mask")
            }
        }
    }

    @Test
    fun skillSquaresRequestTheirSlotAndTheBadgeRequestsTheApexInEveryMode() {
        for ((width, height, scale) in listOf(Triple(1_280f, 720f, 1f), Triple(2_532f, 1_170f, 3f), Triple(1_170f, 2_532f, 3f))) {
            val running = hitTestState(GamePhase.RUNNING, screenWidth = width, screenHeight = height, uiScale = scale,
                controlMask = RunningControlMask.ALL, skillSlots = 0b111)
            val controls = runningControlBounds(width, height, scale).associate { it.target to it.bounds }
            listOf(RunningControlTarget.SKILL_1, RunningControlTarget.SKILL_2, RunningControlTarget.SKILL_3).forEachIndexed { slot, target ->
                val center = controls.getValue(target).center
                val input = assertIs<GameplayInput.Action>(running.resolveGameplayPress(center.x, center.y), "$width x $height $target")
                assertSame(GameplayInteractionPulse.SkillRequested.fromValidated(slot), input.action)
                assertTrue(running.isHudControlPosition(center.x, center.y))
            }
            val apex = controls.getValue(RunningControlTarget.APEX).center
            assertSame(GameplayInteractionPulse.ApexRequested, assertIs<GameplayInput.Action>(running.resolveGameplayPress(apex.x, apex.y)).action)
            // Without the mask the same points steer: nothing is placed there.
            val bare = running.copy(controlMask = RunningControlMask.NONE)
            assertNull(bare.resolveGameplayPress(apex.x, apex.y))
            assertFalse(bare.isHudControlPosition(apex.x, apex.y))
        }
    }

    @Test
    fun anEmptySkillSlotIsAHudPositionThatDispatchesNothing() {
        // Only Z holds a skill: X and C show locked; pressing them neither casts nor steers.
        val running = hitTestState(GamePhase.RUNNING, controlMask = RunningControlMask.SKILLS, skillSlots = 0b001)
        val controls = runningControlBounds(running.screenWidth, running.screenHeight, running.uiScale).associate { it.target to it.bounds }
        listOf(RunningControlTarget.SKILL_2, RunningControlTarget.SKILL_3).forEach { target ->
            val center = controls.getValue(target).center
            assertNull(running.resolveGameplayPress(center.x, center.y), "$target dispatched")
            assertTrue(running.isHudControlPosition(center.x, center.y), "$target steers")
        }
        val z = controls.getValue(RunningControlTarget.SKILL_1).center
        assertSame(GameplayInteractionPulse.SkillRequested.fromValidated(0),
            assertIs<GameplayInput.Action>(running.resolveGameplayPress(z.x, z.y)).action)
        // A paused run maps nothing on the HUD row.
        assertNull(running.copy(phase = GamePhase.PAUSED).resolveGameplayPress(z.x, z.y))
    }

    private fun hitTestState(
        phase: GamePhase,
        choiceCount: Int = 0,
        choicesCanReroll: Boolean = false,
        screenWidth: Float = 1_280f,
        screenHeight: Float = 720f,
        uiScale: Float = 1f,
        controlMask: Int = RunningControlMask.NONE,
        skillSlots: Int = 0,
    ): GameplayHitTestState = GameplayHitTestState(
        phase = phase,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        uiScale = uiScale,
        choiceCount = choiceCount,
        choicesCanReroll = choicesCanReroll,
        controlMask = controlMask,
        skillSlots = skillSlots,
    )
}
