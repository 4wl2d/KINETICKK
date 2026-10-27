// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.input

import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RunningTouchTrackerTest {
    @Test
    fun aSteeringThumbKeepsSteeringWhileAnotherTapsDash() {
        val tracker = RunningTouchTracker()
        assertEquals(RunningTouchTracker.DOWN_STEER, tracker.down(1L, null))
        assertEquals(RunningTouchTracker.DOWN_CONTROL, tracker.down(2L, RunningControlTarget.DASH))
        assertTrue(tracker.move(1L))
        assertFalse(tracker.move(2L))
        assertEquals(RunningControlTarget.DASH, tracker.target(2L))
        assertEquals(RunningTouchTracker.UP_NONE, tracker.up(2L))
        assertTrue(tracker.move(1L), "the steering pointer lost the steer when Dash lifted")
    }

    @Test
    fun steeringWithBrakeHeldLetsAThirdPointerTapASkill() {
        val tracker = RunningTouchTracker()
        tracker.down(1L, null)
        tracker.down(2L, RunningControlTarget.BRAKE)
        assertEquals(RunningTouchTracker.DOWN_CONTROL, tracker.down(3L, RunningControlTarget.SKILL_2))
        assertTrue(tracker.holds(RunningControlTarget.BRAKE))
        assertTrue(tracker.move(1L))
        // The skill tap lifts: Brake stays held, steering continues.
        assertEquals(RunningTouchTracker.UP_NONE, tracker.up(3L))
        assertTrue(tracker.holds(RunningControlTarget.BRAKE))
        assertTrue(tracker.move(1L))
    }

    @Test
    fun brakeIsReleasedOnlyByTheLastPointerHoldingIt() {
        val tracker = RunningTouchTracker()
        tracker.down(1L, RunningControlTarget.BRAKE)
        tracker.down(2L, RunningControlTarget.BRAKE)
        tracker.down(3L, null)
        // Lifting the steering pointer or one of two Brake pointers keeps braking.
        assertEquals(RunningTouchTracker.UP_NONE, tracker.up(3L))
        assertEquals(RunningTouchTracker.UP_NONE, tracker.up(1L))
        assertTrue(tracker.holds(RunningControlTarget.BRAKE))
        assertEquals(RunningTouchTracker.UP_BRAKE_RELEASED, tracker.up(2L))
        assertFalse(tracker.holds(RunningControlTarget.BRAKE))
    }

    @Test
    fun liftingTheSteeringPointerPromotesTheOldestIdlePointer() {
        val tracker = RunningTouchTracker()
        tracker.down(10L, null)
        assertEquals(RunningTouchTracker.DOWN_IDLE, tracker.down(11L, null))
        assertEquals(RunningTouchTracker.DOWN_IDLE, tracker.down(12L, null))
        assertFalse(tracker.move(11L))
        val lifted = tracker.up(10L)
        assertTrue(lifted >= RunningTouchTracker.UP_STEER_PROMOTED)
        assertEquals(11L, tracker.idAt(lifted - RunningTouchTracker.UP_STEER_PROMOTED))
        assertTrue(tracker.move(11L))
        assertFalse(tracker.move(12L))
        // Then the next oldest.
        val next = tracker.up(11L)
        assertEquals(12L, tracker.idAt(next - RunningTouchTracker.UP_STEER_PROMOTED))
        assertTrue(tracker.move(12L))
        assertEquals(RunningTouchTracker.UP_NONE, tracker.up(12L))
        assertTrue(tracker.isEmpty)
    }

    @Test
    fun aControlPointerThatSlidesOffItsControlNeverSteers() {
        val tracker = RunningTouchTracker()
        tracker.down(1L, RunningControlTarget.SKILL_1)
        // Its moves anywhere never steer, and its lift promotes no one.
        assertFalse(tracker.move(1L))
        tracker.down(2L, null)
        assertTrue(tracker.move(2L), "a later pointer off the controls steers while the control pointer holds")
        assertEquals(RunningTouchTracker.UP_NONE, tracker.up(1L))
        // A press on a HUD position that is not a control (the trial's info) holds like one.
        assertEquals(RunningTouchTracker.DOWN_CONTROL, tracker.down(3L, null, hudPosition = true))
        assertFalse(tracker.move(3L))
        assertNull(tracker.target(3L))
    }

    @Test
    fun touchTrackerAcceptsFivePointersAndIgnoresTheSixth() {
        val tracker = RunningTouchTracker()
        for (id in 1L..RunningTouchTracker.MAX_POINTERS.toLong()) {
            assertTrue(tracker.down(id, if (id == 1L) null else RunningControlTarget.BRAKE) != RunningTouchTracker.DOWN_FULL, "pointer $id")
        }
        assertEquals(RunningTouchTracker.DOWN_FULL, tracker.down(6L, null))
        assertFalse(tracker.move(6L))
        assertEquals(RunningTouchTracker.UP_NONE, tracker.up(6L))
        // Once one lifts, a new pointer is tracked again.
        tracker.up(5L)
        assertEquals(RunningTouchTracker.DOWN_CONTROL, tracker.down(7L, RunningControlTarget.DASH))
    }

    @Test
    fun leavingRunningReleasesAHeldBrakeOnceAndReturningEngagesItAgainWithEveryRoleKept() {
        val tracker = RunningTouchTracker()
        tracker.down(1L, null)
        tracker.down(2L, RunningControlTarget.BRAKE)
        assertFalse(tracker.running(true), "still RUNNING changed Brake")
        assertTrue(tracker.running(false))
        assertFalse(tracker.running(false), "a second event away released Brake again")
        assertTrue(tracker.move(1L), "the steering thumb lost its role")
        assertTrue(tracker.running(true))
        assertFalse(tracker.running(true))
        assertTrue(tracker.move(1L))
        // Brake lifted while away: already released, and nothing is engaged on return.
        assertTrue(tracker.running(false))
        assertEquals(RunningTouchTracker.UP_NONE, tracker.up(2L))
        assertFalse(tracker.running(true))
        // Without a held Brake the phase changes nothing to dispatch.
        assertFalse(tracker.running(false))
        assertFalse(tracker.running(true))
        assertEquals(RunningTouchTracker.UP_NONE, tracker.up(1L))
        assertTrue(tracker.isEmpty)
    }

    @Test
    fun theSamePointerGoingDownAgainReplacesItsOldRole() {
        val tracker = RunningTouchTracker()
        tracker.down(1L, null)
        assertEquals(RunningTouchTracker.DOWN_CONTROL, tracker.down(1L, RunningControlTarget.DASH))
        assertFalse(tracker.move(1L))
        assertEquals(RunningTouchTracker.DOWN_STEER, tracker.down(2L, null))
    }

    @Test
    fun pressLogFlashesEachControlForNinetyMillisecondsAndKeepsTheApexRefusal() {
        val log = RunningControlPressLog()
        assertEquals(0f, log.pressFlash(RunningControlTarget.SKILL_1, 0f))
        log.press(RunningControlTarget.SKILL_1, 2f)
        assertEquals(1f, log.pressFlash(RunningControlTarget.SKILL_1, 2f))
        assertEquals(1f, log.pressFlash(RunningControlTarget.SKILL_1, 2.08f))
        assertEquals(0f, log.pressFlash(RunningControlTarget.SKILL_1, 2.1f))
        assertEquals(0f, log.pressFlash(RunningControlTarget.SKILL_2, 2f))
        log.refuseApex(3f)
        assertEquals(3f, log.apexRefusedAt)
        assertFalse(log.keyHints)
    }
}
