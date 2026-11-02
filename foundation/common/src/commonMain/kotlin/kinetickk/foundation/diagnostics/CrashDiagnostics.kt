// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.diagnostics

/** Mechanical, best-effort telemetry. Implementations must not throw or change game decisions. */
interface CrashDiagnostics {
    /** Lets callers skip formatting and context captures when diagnostics are disabled. */
    val enabled: Boolean get() = true
    /** Capture immutable values only; never query an owner from this deferred formatter. */
    fun context(key: String, describe: () -> String)
    fun event(category: String, message: String, highFrequency: Boolean = false)
    /** Pause optional background checkpoints while a performance recording is measuring the game. */
    fun performanceRecordingChanged(active: Boolean) = Unit

    data object None : CrashDiagnostics {
        override val enabled: Boolean get() = false
        override fun context(key: String, describe: () -> String) = Unit
        override fun event(category: String, message: String, highFrequency: Boolean) = Unit
    }
}

/** UI-thread performance samples. Durations are CPU work, never GPU/presentation timings. */
interface PerformanceDiagnostics : CrashDiagnostics {
    val capturesPerformance: Boolean get() = true
    val overlayVisible: Boolean get() = false
    val framesPerSecond: Int get() = -1
    var nominalFrameBudgetNanos: Long
        get() = 0L
        set(@Suppress("UNUSED_PARAMETER") value) = Unit
    var showFpsRequested: Boolean
        get() = false
        set(@Suppress("UNUSED_PARAMETER") value) = Unit
    fun toggleOverlay() = Unit
    /** Absolute monotonic frame timestamp; zero budget means uncapped or unknown refresh rate. */
    fun frame(frameTimeNanos: Long, budgetNanos: Long)
    fun phase(phase: DiagnosticPhase, durationNanos: Long)
    fun workload(enemies: Int, projectiles: Int, pickups: Int, effects: Int)
}

enum class DiagnosticPhase { Dispatch, Simulation, Projection, Audio, CanvasDraw }

/** The slower of the selected software limit and the active host refresh constrains presentation. */
fun diagnosticFrameBudgetNanos(capHz: Int, hostBudgetNanos: Long): Long =
    maxOf(if (capHz > 0) 1_000_000_000L / capHz else 0L, hostBudgetNanos.coerceAtLeast(0L))
