// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction

import kinetickk.flow.session.api.AppDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionTransitionTest {
    @Test
    fun destinationChangesUseTheShutterOnlyWhenThePreviousFrameIsKept() {
        assertEquals(SessionTransitionKind.NONE, sessionTransitionKind(null, null, AppDestination.Home, null, snapshotAvailable = true))
        assertEquals(SessionTransitionKind.NONE, sessionTransitionKind(AppDestination.Home, null, AppDestination.Home, null, snapshotAvailable = true))
        assertEquals(SessionTransitionKind.SHUTTER,
            sessionTransitionKind(AppDestination.Home, null, AppDestination.Home, AppDestination.Codex, snapshotAvailable = true))
        assertEquals(SessionTransitionKind.CROSSFADE,
            sessionTransitionKind(AppDestination.Gameplay, null, AppDestination.Home, null, snapshotAvailable = false))
    }

    @Test
    fun theInRunScreenIsNeverRecordedButItsOverlaysAre() {
        assertFalse(sessionRecordsSnapshot(AppDestination.Gameplay, null))
        assertTrue(sessionRecordsSnapshot(AppDestination.Gameplay, AppDestination.Codex))
        assertTrue(sessionRecordsSnapshot(AppDestination.Home, null))
        assertTrue(sessionRecordsSnapshot(AppDestination.Home, AppDestination.Settings))
    }

    @Test
    fun stateFreezesTheSnapshotDuringTheShutterAndFadesOverlappingChanges() {
        val state = SessionTransitionState()
        state.show(AppDestination.Home, null)
        assertEquals(SessionTransitionKind.NONE, state.kind)
        assertTrue(state.recording)
        // Without a recorded frame there is nothing to cover: fade.
        state.show(AppDestination.Home, AppDestination.Lab)
        assertEquals(SessionTransitionKind.CROSSFADE, state.kind)
        state.finish()
        state.recorded()
        state.show(AppDestination.Home, AppDestination.Codex)
        assertEquals(SessionTransitionKind.SHUTTER, state.kind)
        assertFalse(state.recording, "The previous frame stays frozen until the shutter ends")
        // A second change before the shutter ends has no fresh frame of the shown screen.
        state.show(AppDestination.Home, null)
        assertEquals(SessionTransitionKind.CROSSFADE, state.kind)
        assertTrue(state.recording)
        state.finish()
        state.recorded() // the next drawn frame of Home is kept
        state.show(AppDestination.Gameplay, null)
        assertEquals(SessionTransitionKind.SHUTTER, state.kind)
        state.finish()
        assertFalse(state.recording, "The in-run screen is the hot path and is not recorded")
        val runId = state.id
        state.show(AppDestination.Home, null)
        assertEquals(SessionTransitionKind.CROSSFADE, state.kind)
        assertEquals(runId + 1, state.id)
    }
}
