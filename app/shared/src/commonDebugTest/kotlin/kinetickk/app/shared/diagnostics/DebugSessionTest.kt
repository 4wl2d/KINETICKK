// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared.diagnostics

import kinetickk.foundation.diagnostics.CrashDiagnostics
import kinetickk.foundation.diagnostics.DiagnosticPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DebugSessionTest {
    @Test
    fun disabledCollectorIgnoresFramesAndPhases() {
        val session = DebugSession()
        assertFalse(session.capturesPerformance)
        session.frame(10L, 10L)
        session.phase(DiagnosticPhase.Simulation, 100L)
        session.frame(100L, 10L)
        assertEquals(0L, session.snapshot().frames)
        assertFalse(session.hasRecording)
    }

    @Test
    fun frameContainsPhasesBetweenCallbacksAndUsesActualBudget() {
        val session = DebugSession()
        session.panelVisible = true
        session.frame(1_000_000L, 10_000_000L)
        session.phase(DiagnosticPhase.Dispatch, 6_000_000L)
        session.phase(DiagnosticPhase.Simulation, 4_000_000L)
        session.phase(DiagnosticPhase.Projection, 1_000_000L)
        session.phase(DiagnosticPhase.Audio, 500_000L)
        session.phase(DiagnosticPhase.CanvasDraw, 2_000_000L)
        session.workload(7, 12, 3, 8)
        session.frame(21_000_000L, 0L)
        session.frame(41_000_000L, 0L)
        val snapshot = session.snapshot()
        assertEquals(50.0, snapshot.fps)
        assertEquals(20.0, snapshot.p95Millis)
        assertEquals(1, snapshot.budgetedFrames)
        assertEquals(1, snapshot.jankFrames)
        assertEquals(1L, snapshot.missedIntervals)
        assertEquals(3.0, snapshot.dispatchMillis)
        assertEquals(2.0, snapshot.simulationMillis)
        assertEquals(0.5, snapshot.projectionMillis)
        assertEquals(0.25, snapshot.audioMillis)
        assertEquals(1.0, snapshot.drawMillis)
        assertEquals(7, snapshot.enemies)
    }

    @Test
    fun recordingKeepsBoundedChronologicalTailAndDisclosesLoss() {
        var now = 0L
        val session = DebugSession(frameCapacity = 2, eventCapacity = 3, nowNanos = { now++ })
        session.start()
        session.frame(1L, 10L)
        repeat(5) { index ->
            session.event("input", "action-$index")
            session.frame(11L + index * 10L, 10L)
        }
        session.stop()
        val export = session.export()
        assertEquals(5L, session.recordedFrames)
        assertEquals(3L, session.overwrittenFrames)
        assertEquals(4L, session.overwrittenEvents)
        assertTrue(export.contains("KINETICKK_DEBUG_DIAGNOSTICS_V1"))
        assertTrue(export.contains("frames_total=5; frames_retained=2; frames_overwritten=3"))
        assertFalse(export.contains("action-2"))
        assertTrue(export.indexOf("action-3") < export.indexOf("action-4"))
    }

    @Test
    fun stoppedRecordingAndContextsStayFrozenWhileLivePanelContinues() {
        val session = DebugSession()
        var context = "before"
        session.context("run") { context }
        session.start()
        session.frame(1L, 10L)
        session.frame(11L, 10L)
        context = "at stop"
        session.stop()
        val export = session.export()
        context = "after stop"
        session.panelVisible = true
        session.frame(21L, 10L)
        session.frame(31L, 10L)
        session.frame(41L, 10L)
        session.event("input", "not recorded")
        assertEquals(export, session.export())
        assertTrue(export.contains("run=before"))
        assertTrue(export.contains("run=at stop"))
        assertFalse(export.contains("after stop"))
        assertEquals(1L, session.recordedFrames)
        assertTrue(session.snapshot().frames > session.recordedFrames)
    }

    @Test
    fun longStallRemainsInStatisticsAndRawRecording() {
        val session = DebugSession()
        session.start()
        session.frame(1L, 16_666_667L)
        session.frame(3_000_000_001L, 16_666_667L)
        session.stop()
        assertEquals(3_000.0, session.snapshot().maxMillis)
        assertEquals(1, session.snapshot().jankFrames)
        val export = session.export()
        assertTrue(export.contains("frame.long_gap"))
        assertTrue(export.contains("\t3000000000\t16666667\t"))
    }

    @Test
    fun exportRequiresStopAndRestartClearsPreviousRecording() {
        val session = DebugSession()
        session.start()
        session.event("warning", "previous-run")
        assertFailsWith<IllegalStateException> { session.export() }
        session.clear()
        assertTrue(session.recording)
        session.stop()
        session.start()
        session.stop()
        assertFalse(session.export().contains("previous-run"))
    }

    @Test
    fun formatterAndDownstreamFailuresDoNotChangeTheGameOrBreakExport() {
        val sink = object : CrashDiagnostics {
            override fun context(key: String, describe: () -> String) { error("sink unavailable") }
            override fun event(category: String, message: String, highFrequency: Boolean) { error("sink unavailable") }
            override fun performanceRecordingChanged(active: Boolean) { error("sink unavailable") }
        }
        val session = DebugSession(sink)
        session.context("bad") { error("broken formatter") }
        session.start()
        session.event("error", "captured despite failing sink")
        session.stop()
        val export = session.export()
        assertTrue(export.contains("context unavailable"))
        assertTrue(export.contains("captured despite failing sink"))
        assertTrue(export.contains("context_failures=2"))
        assertEquals(export, session.export())
    }

    @Test
    fun unknownFrameBudgetDoesNotInventJankOrMissedVsyncFrames() {
        val session = DebugSession()
        session.panelVisible = true
        session.frame(1L, 0L)
        session.frame(100_000_001L, 0L)
        assertEquals(10.0, session.snapshot().fps)
        assertEquals(0, session.snapshot().budgetedFrames)
        assertEquals(0, session.snapshot().jankFrames)
        assertEquals(0L, session.snapshot().missedIntervals)
    }

    @Test
    fun livePanelCoversOnlyTheLatest600IntervalsWithoutRecording() {
        val session = DebugSession()
        session.panelVisible = true
        assertTrue(session.capturesPerformance)
        session.frame(1L, 0L)
        repeat(700) { session.frame(1L + (it + 1) * 10_000_000L, 0L) }
        assertEquals(100.0, session.snapshot().fps)
        // Overwritten intervals leave the live statistics.
        repeat(600) { session.frame(7_000_000_001L + (it + 1) * 20_000_000L, 0L) }
        assertEquals(50.0, session.snapshot().fps)
        assertFalse(session.hasRecording)
        session.panelVisible = false
        assertFalse(session.capturesPerformance)
    }

    @Test
    fun reopenedPanelDoesNotMixIntervalsFromAnEarlierCapturePeriod() {
        val session = DebugSession()
        session.panelVisible = true
        repeat(5) { session.frame(1L + it * 10_000_000L, 0L) }
        assertEquals(100.0, session.snapshot().fps)
        session.panelVisible = false
        session.panelVisible = true
        repeat(5) { session.frame(1_000_000_001L + it * 20_000_000L, 0L) }
        assertEquals(50.0, session.snapshot().fps)
        assertEquals(4L, session.snapshot().frames)
    }

    @Test
    fun recordingKeepsRuntimeBoundariesAndThrottlesRepeatedSamples() {
        var now = 0L
        val session = DebugSession(nowNanos = { now })
        session.start()
        // A restarted panel loop samples immediately; the boundary sample already covers it.
        session.sampleRuntime()
        now = 1_499_999_999L
        session.sampleRuntime()
        now = 1_500_000_000L
        session.sampleRuntime()
        now = 1_600_000_000L
        session.stop()
        assertTrue(session.export().contains("runtime_samples_total=3;"))
    }

    @Test
    fun downstreamCheckpointSuspensionTracksOnlyRecordingTransitions() {
        val transitions = mutableListOf<Boolean>()
        val sink = object : CrashDiagnostics {
            override fun context(key: String, describe: () -> String) = Unit
            override fun event(category: String, message: String, highFrequency: Boolean) = Unit
            override fun performanceRecordingChanged(active: Boolean) { transitions += active }
        }
        val session = DebugSession(sink)
        session.stop()
        session.panelVisible = true
        assertTrue(transitions.isEmpty())
        session.start()
        session.start()
        session.clear()
        session.panelVisible = false
        assertEquals(listOf(true), transitions)
        session.stop()
        session.stop()
        assertEquals(listOf(true, false), transitions)
        session.start()
        session.stop()
        assertEquals(listOf(true, false, true, false), transitions)
    }
}
