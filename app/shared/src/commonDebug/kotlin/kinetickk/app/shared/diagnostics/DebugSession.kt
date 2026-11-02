// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared.diagnostics

import androidx.compose.runtime.mutableStateOf
import kinetickk.foundation.diagnostics.CrashDiagnostics
import kinetickk.foundation.diagnostics.DiagnosticPhase
import kinetickk.foundation.diagnostics.PerformanceDiagnostics
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.time.TimeSource

private const val EXPORT_MARKER = "KINETICKK_DEBUG_DIAGNOSTICS_V1"
private const val MAX_CONTEXTS = 48
private const val MAX_RUNTIME_SAMPLES = 512
private const val FRAME_DISCONTINUITY_NANOS = 2_000_000_000L

data class DebugSnapshot(
    val frames: Long,
    val retainedFrames: Int,
    val fps: Double,
    val meanMillis: Double,
    val p95Millis: Double,
    val p99Millis: Double,
    val maxMillis: Double,
    val jankFrames: Int,
    val budgetedFrames: Int,
    val missedIntervals: Long,
    val dispatchMillis: Double,
    val simulationMillis: Double,
    val drawMillis: Double,
    val projectionMillis: Double,
    val audioMillis: Double,
    val enemies: Int,
    val projectiles: Int,
    val pickups: Int,
    val effects: Int,
)

/**
 * Game/UI-thread recorder. No sorting, strings, collection growth or Compose state writes in frame
 * callbacks. Recording is a bounded tail; counters disclose everything overwritten by the rings.
 * UI snapshots and runtime sampling are explicitly invoked at a much lower frequency.
 */
class DebugSession(
    private val downstream: CrashDiagnostics = CrashDiagnostics.None,
    frameCapacity: Int = 12_000,
    eventCapacity: Int = 2_048,
    private val nowNanos: () -> Long = monotonicClock(),
) : PerformanceDiagnostics {
    private val live = FrameRing(600)
    private val recorded = FrameRing(frameCapacity)
    private val events = arrayOfNulls<DebugEvent>(eventCapacity.also { require(it > 0) })
    private val contexts = linkedMapOf<String, () -> String>()
    private val runtimeSamples = arrayOfNulls<TimedRuntimeSample>(MAX_RUNTIME_SAMPLES)
    private var eventWrites = 0L
    private var runtimeWrites = 0L
    private var startNanos = 0L
    private var stopNanos = 0L
    private var lastFrameNanos = 0L
    private var dispatchNanos = 0L
    private var simulationNanos = 0L
    private var drawNanos = 0L
    private var projectionNanos = 0L
    private var audioNanos = 0L
    private var enemies = 0
    private var projectiles = 0
    private var pickups = 0
    private var effects = 0
    private var budgetNanos = 0L
    private var discontinuities = 0L
    private var contextFailures = 0L
    private var runtimeDescription = "not sampled"
    private var initialContexts: Map<String, String> = emptyMap()
    private var finalContexts: Map<String, String> = emptyMap()

    private val recordingState = mutableStateOf(false)
    private val panelVisibleState = mutableStateOf(false)
    private val showFpsRequestedState = mutableStateOf(false)
    private var recordingFlag = false
    private var panelVisibleFlag = false
    private var showFpsFlag = false
    private var captureEnabled = false
    var recording: Boolean
        get() = recordingState.value
        private set(value) {
            recordingFlag = value
            recordingState.value = value
            updateCaptureEnabled()
        }
    var panelVisible: Boolean
        get() = panelVisibleState.value
        set(value) {
            if (panelVisibleFlag == value) return
            panelVisibleFlag = value
            panelVisibleState.value = value
            updateCaptureEnabled()
        }

    override var showFpsRequested: Boolean
        get() = showFpsRequestedState.value
        set(value) {
            if (showFpsFlag == value) return
            showFpsFlag = value
            showFpsRequestedState.value = value
            updateCaptureEnabled()
        }
    override val capturesPerformance: Boolean get() = captureEnabled
    override val overlayVisible: Boolean get() = panelVisibleFlag
    override var nominalFrameBudgetNanos: Long = 0L
    override val framesPerSecond: Int get() = live.framesPerSecond.roundToLong().toInt()
    override fun toggleOverlay() { panelVisible = !panelVisible }
    val hasRecording: Boolean get() = eventWrites > 0 || recorded.total > 0
    val recordedFrames: Long get() = recorded.total
    val overwrittenFrames: Long get() = (recorded.total - recorded.capacity).coerceAtLeast(0L)
    val overwrittenEvents: Long get() = (eventWrites - events.size).coerceAtLeast(0L)

    override fun context(key: String, describe: () -> String) {
        val boundedKey = key.take(80)
        if (boundedKey in contexts || contexts.size < MAX_CONTEXTS) contexts[boundedKey] = describe
        runCatching { downstream.context(key, describe) }
    }

    override fun event(category: String, message: String, highFrequency: Boolean) {
        runCatching { downstream.event(category, message, highFrequency) }
        if (!recordingFlag) return
        appendEvent(category, message)
    }

    fun start() {
        if (recordingFlag) return
        clear()
        startNanos = nowNanos()
        recording = true
        runCatching { downstream.performanceRecordingChanged(true) }
        resetFrameBoundary()
        appendEvent("session", "recording started; frame/phase callbacks are game-thread measurements")
        initialContexts = captureContexts()
        runtimeDescription = runCatching { debugRuntimeDescription().take(1_024) }.getOrDefault("unavailable")
        sampleRuntime()
    }

    fun stop() {
        if (!recordingFlag) return
        try {
            appendEvent("session", "recording stopped; pending incomplete frame excluded")
            sampleRuntime()
            stopNanos = nowNanos()
        } finally {
            recording = false
            runCatching { downstream.performanceRecordingChanged(false) }
            finalContexts = captureContexts()
        }
    }

    fun clear() {
        if (recordingFlag) return
        recorded.clear()
        events.fill(null)
        runtimeSamples.fill(null)
        eventWrites = 0L
        runtimeWrites = 0L
        startNanos = 0L
        stopNanos = 0L
        discontinuities = 0L
        contextFailures = 0L
        initialContexts = emptyMap()
        finalContexts = emptyMap()
    }

    override fun frame(frameTimeNanos: Long, budgetNanos: Long) {
        if (!capturesPerformance) return
        val interval = if (lastFrameNanos == 0L) 0L else frameTimeNanos - lastFrameNanos
        if (interval > 0L) {
            val timestamp = nowNanos() - startNanos
            live.add(timestamp, interval, this.budgetNanos, dispatchNanos, simulationNanos, drawNanos, projectionNanos, audioNanos,
                enemies, projectiles, pickups, effects)
            if (recordingFlag) recorded.add(timestamp, interval, this.budgetNanos, dispatchNanos,
                simulationNanos, drawNanos, projectionNanos, audioNanos, enemies, projectiles, pickups, effects)
            if (interval > FRAME_DISCONTINUITY_NANOS && recordingFlag) {
                discontinuities++
                appendEvent("frame.long_gap", "interval_ns=$interval; retained; stall or suspension, cause unknown")
            }
        } else if (lastFrameNanos != 0L && recordingFlag) {
            discontinuities++
            appendEvent("frame.discontinuity", "interval_ns=$interval; excluded (non-positive callback timestamp delta)")
        }
        lastFrameNanos = frameTimeNanos
        this.budgetNanos = budgetNanos.coerceAtLeast(0L)
        dispatchNanos = 0L
        simulationNanos = 0L
        drawNanos = 0L
        projectionNanos = 0L
        audioNanos = 0L
    }

    override fun phase(phase: DiagnosticPhase, durationNanos: Long) {
        if (!capturesPerformance || durationNanos < 0L) return
        when (phase) {
            DiagnosticPhase.Dispatch -> dispatchNanos += durationNanos
            DiagnosticPhase.Simulation -> simulationNanos += durationNanos
            DiagnosticPhase.CanvasDraw -> drawNanos += durationNanos
            DiagnosticPhase.Projection -> projectionNanos += durationNanos
            DiagnosticPhase.Audio -> audioNanos += durationNanos
        }
    }

    override fun workload(enemies: Int, projectiles: Int, pickups: Int, effects: Int) {
        if (!capturesPerformance) return
        this.enemies = enemies
        this.projectiles = projectiles
        this.pickups = pickups
        this.effects = effects
    }

    /** Call at most once every two seconds, plus recording boundaries. */
    fun sampleRuntime(): DebugRuntimeSample {
        val value = runCatching { collectDebugRuntimeSample() }.getOrDefault(DebugRuntimeSample())
        if (recordingFlag) {
            runtimeSamples[(runtimeWrites % runtimeSamples.size).toInt()] =
                TimedRuntimeSample(nowNanos() - startNanos, value)
            runtimeWrites++
        }
        return value
    }

    /** Quantiles cover the latest 600 callback intervals, refreshed only when the panel is visible. */
    fun snapshot(): DebugSnapshot = live.snapshot()

    /** Export only a stopped, frozen recording: formatting/copying cannot contaminate captured data. */
    fun export(): String {
        check(!recordingFlag) { "Stop recording before exporting diagnostics" }
        val summary = recorded.snapshot()
        return buildString {
            appendLine(EXPORT_MARKER)
            appendLine("variant=Debug; runtime=$runtimeDescription")
            appendLine("duration_ms=${decimal((stopNanos - startNanos).coerceAtLeast(0L) / 1e6)}")
            appendLine("frames_total=${recorded.total}; frames_retained=${recorded.size}; frames_overwritten=$overwrittenFrames")
            appendLine("events_total=$eventWrites; events_overwritten=$overwrittenEvents; discontinuities=$discontinuities")
            appendLine("runtime_samples_total=$runtimeWrites; runtime_samples_overwritten=${(runtimeWrites - runtimeSamples.size).coerceAtLeast(0L)}")
            appendLine("retained_frame_fps=${decimal(summary.fps)}; mean_ms=${decimal(summary.meanMillis)}; p95_ms=${decimal(summary.p95Millis)}; p99_ms=${decimal(summary.p99Millis)}; max_ms=${decimal(summary.maxMillis)}")
            appendLine("budgeted_frames=${summary.budgetedFrames}; jank_frames=${summary.jankFrames}; estimated_missed_intervals=${summary.missedIntervals}")
            appendLine("MEASUREMENT NOTES")
            appendLine("Frame interval = consecutive game frame callbacks, not GPU presentation. 0 budget = unknown/uncapped; no FPS-target jank claim is made for those rows. Jank = interval > 1.5 x supplied frame budget. Missed intervals are estimates, not compositor statistics.")
            appendLine("Each frame row contains CPU phases accumulated BETWEEN its previous callback and this callback; workload is the last observation in that interval. Simulation, projection and audio are INCLUDED in dispatch; do not add them together. Canvas draw is CPU command submission, not GPU execution. There are no GPU timings. Gaps > 2s remain in the raw frames and percentiles and are also logged; they may be stalls or suspension, and their cause is unknown.")
            appendLine("Percentiles describe the retained tail, not the entire overwritten session. Runtime counters are process-wide cumulative samples every ~2s; GC/CPU changes correlate with a window and do not prove a specific frame cause. Unavailable fields are NA. Panel refreshes at 2Hz; close it while recording to reduce UI overhead. Export occurs after recording stops.")
            appendLine("LIKELY CONTRIBUTORS (heuristics, not a diagnosis)")
            appendContributors(recorded)
            appendLine("CONTEXT_AT_START")
            for ((key, value) in initialContexts) appendLine("${oneLine(key)}=${oneLine(value)}")
            appendLine("CONTEXT_AT_STOP (frozen)")
            for ((key, value) in finalContexts) appendLine("${oneLine(key)}=${oneLine(value)}")
            appendLine("context_failures=$contextFailures")
            appendLine("FRAMES_TSV")
            appendLine("elapsed_ns\tinterval_ns\tbudget_ns\tdispatch_ns\tsimulation_ns\tcanvas_cpu_ns\tprojection_ns\taudio_ns\tenemies\tprojectiles\tpickups\teffects")
            recorded.forEachIndex { i ->
                append(recorded.timestamp[i]).append('\t').append(recorded.interval[i]).append('\t')
                    .append(recorded.budget[i]).append('\t').append(recorded.dispatch[i]).append('\t')
                    .append(recorded.simulation[i]).append('\t').append(recorded.draw[i]).append('\t')
                    .append(recorded.projection[i]).append('\t').append(recorded.audio[i]).append('\t')
                    .append(recorded.enemies[i]).append('\t').append(recorded.projectiles[i]).append('\t')
                    .append(recorded.pickups[i]).append('\t').append(recorded.effects[i]).appendLine()
            }
            appendLine("EVENTS_TSV")
            appendLine("elapsed_ns\tcategory\tmessage")
            for (n in max(0L, eventWrites - events.size) until eventWrites) {
                events[(n % events.size).toInt()]?.let {
                    append(it.elapsedNanos).append('\t').append(oneLine(it.category)).append('\t')
                        .appendLine(oneLine(it.message))
                }
            }
            appendLine("RUNTIME_TSV")
            appendLine("elapsed_ns\theap_used_bytes\theap_committed_bytes\tgc_count\tgc_time_ms\tprocess_cpu_ns")
            for (n in max(0L, runtimeWrites - runtimeSamples.size) until runtimeWrites) {
                runtimeSamples[(n % runtimeSamples.size).toInt()]?.let {
                    append(it.elapsedNanos).append('\t').append(it.sample.heapUsedBytes ?: "NA").append('\t')
                        .append(it.sample.heapCommittedBytes ?: "NA").append('\t')
                        .append(it.sample.gcCount ?: "NA").append('\t')
                        .append(it.sample.gcTimeMillis ?: "NA").append('\t')
                        .appendLine(it.sample.processCpuNanos ?: "NA")
                }
            }
        }
    }

    private fun captureContexts(): Map<String, String> = contexts.mapValues { (_, formatter) ->
        runCatching { formatter().take(4_096) }.getOrElse {
            contextFailures++
            "<context unavailable: ${it::class.simpleName}>"
        }
    }

    private fun appendEvent(category: String, message: String) {
        events[(eventWrites % events.size).toInt()] =
            DebugEvent(nowNanos() - startNanos, category.take(80), message.take(1_024))
        eventWrites++
    }

    private fun resetFrameBoundary() {
        lastFrameNanos = 0L
        dispatchNanos = 0L
        simulationNanos = 0L
        drawNanos = 0L
        projectionNanos = 0L
        audioNanos = 0L
    }

    private fun updateCaptureEnabled() {
        val enabled = recordingFlag || panelVisibleFlag || showFpsFlag
        if (enabled != captureEnabled) resetFrameBoundary()
        captureEnabled = enabled
    }
}

private fun monotonicClock(): () -> Long {
    val origin = TimeSource.Monotonic.markNow()
    return { origin.elapsedNow().inWholeNanoseconds }
}

private data class DebugEvent(val elapsedNanos: Long, val category: String, val message: String)
private data class TimedRuntimeSample(val elapsedNanos: Long, val sample: DebugRuntimeSample)

private class FrameRing(val capacity: Int) {
    init { require(capacity > 0) }
    val timestamp = LongArray(capacity)
    val interval = LongArray(capacity)
    val budget = LongArray(capacity)
    val dispatch = LongArray(capacity)
    val simulation = LongArray(capacity)
    val draw = LongArray(capacity)
    val projection = LongArray(capacity)
    val audio = LongArray(capacity)
    val enemies = IntArray(capacity)
    val projectiles = IntArray(capacity)
    val pickups = IntArray(capacity)
    val effects = IntArray(capacity)
    var total = 0L
        private set
    private var rollingIntervalTotal = 0L
    val size: Int get() = minOf(total, capacity.toLong()).toInt()
    val framesPerSecond: Double get() = if (rollingIntervalTotal == 0L) 0.0 else size * 1e9 / rollingIntervalTotal

    fun add(time: Long, frame: Long, target: Long, dispatchTime: Long, simulationTime: Long,
            drawTime: Long, projectionTime: Long, audioTime: Long, enemyCount: Int, projectileCount: Int, pickupCount: Int, effectCount: Int) {
        val i = (total % capacity).toInt()
        if (total >= capacity) rollingIntervalTotal -= interval[i]
        rollingIntervalTotal += frame
        timestamp[i] = time
        interval[i] = frame
        budget[i] = target
        dispatch[i] = dispatchTime
        simulation[i] = simulationTime
        draw[i] = drawTime
        projection[i] = projectionTime
        audio[i] = audioTime
        enemies[i] = enemyCount
        projectiles[i] = projectileCount
        pickups[i] = pickupCount
        effects[i] = effectCount
        total++
    }

    fun clear() {
        total = 0L
        rollingIntervalTotal = 0L
    }

    inline fun forEachIndex(block: (Int) -> Unit) {
        for (n in max(0L, total - capacity) until total) block((n % capacity).toInt())
    }

    fun snapshot(): DebugSnapshot {
        val count = size
        val sorted = LongArray(count)
        var at = 0
        var sum = 0L
        var dispatchSum = 0L
        var simulationSum = 0L
        var drawSum = 0L
        var projectionSum = 0L
        var audioSum = 0L
        var jank = 0
        var budgeted = 0
        var missed = 0L
        forEachIndex { i ->
            sorted[at++] = interval[i]
            sum += interval[i]
            dispatchSum += dispatch[i]
            simulationSum += simulation[i]
            drawSum += draw[i]
            projectionSum += projection[i]
            audioSum += audio[i]
            if (budget[i] > 0L) {
                budgeted++
                if (interval[i] > budget[i] * 1.5) jank++
                missed += ((interval[i].toDouble() / budget[i]).roundToLong() - 1).coerceAtLeast(0L)
            }
        }
        sorted.sort()
        fun percentile(fraction: Double): Double = if (count == 0) 0.0
            else sorted[(ceil(count * fraction).toInt() - 1).coerceIn(0, count - 1)] / 1e6
        val last = if (total == 0L) 0 else ((total - 1) % capacity).toInt()
        val divisor = max(1, count) * 1e6
        return DebugSnapshot(total, count, if (sum == 0L) 0.0 else count * 1e9 / sum,
            sum / divisor, percentile(0.95), percentile(0.99), percentile(1.0), jank, budgeted,
            missed, dispatchSum / divisor, simulationSum / divisor, drawSum / divisor, projectionSum / divisor, audioSum / divisor,
            enemies[last], projectiles[last], pickups[last], effects[last])
    }
}

private fun StringBuilder.appendContributors(frames: FrameRing) {
    var slowSimulation = 0
    var slowProjection = 0
    var slowAudio = 0
    var slowDispatch = 0
    var slowDraw = 0
    var unexplained = 0
    frames.forEachIndex { i ->
        val budget = frames.budget[i]
        if (budget > 0L && frames.interval[i] > budget * 1.5) {
            val simulationPressure = frames.simulation[i] >= budget * 0.6
            val projectionPressure = frames.projection[i] >= budget * 0.6
            val audioPressure = frames.audio[i] >= budget * 0.6
            val dispatchPressure = frames.dispatch[i] >= budget * 0.6
            val drawPressure = frames.draw[i] >= budget * 0.6
            if (simulationPressure) slowSimulation++
            if (projectionPressure) slowProjection++
            if (audioPressure) slowAudio++
            if (dispatchPressure && !simulationPressure && !projectionPressure && !audioPressure) slowDispatch++
            if (drawPressure) slowDraw++
            if (!dispatchPressure && !drawPressure && !simulationPressure && !projectionPressure && !audioPressure) unexplained++
        }
    }
    appendLine("jank_with_simulation_cpu_pressure=$slowSimulation; projection_or_invariants=$slowProjection; audio=$slowAudio; remaining_dispatch_or_reducers=$slowDispatch; canvas_cpu_pressure=$slowDraw; not_explained_by_instrumented_cpu=$unexplained")
    appendLine("A pressure count means that phase used >=60% of the frame budget during a slow interval. Counts can overlap and are not an additive breakdown.")
    appendLine("Unexplained intervals can include OS scheduling, GC, compositor/V-Sync, GPU work, or phases outside these hooks. Inspect nearby events, workload counts and runtime windows before changing code.")
}

internal fun decimal(value: Double): String = if (value.isFinite()) ((value * 100).roundToLong() / 100.0).toString() else "NA"
private fun oneLine(value: String): String = value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ')
