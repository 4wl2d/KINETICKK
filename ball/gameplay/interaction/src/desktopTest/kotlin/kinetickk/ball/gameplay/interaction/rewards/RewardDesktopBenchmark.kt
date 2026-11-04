// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.ComposeRuntimeFlags
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.interaction.GameplayContent
import kinetickk.ball.gameplay.interaction.GameplayInteractionPort
import kinetickk.ball.gameplay.interaction.canvas.hudTestSnapshot
import kinetickk.ball.gameplay.interaction.canvas.with
import kinetickk.ball.gameplay.interaction.canvas.withModel
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import kotlinx.coroutines.CompletableDeferred
import java.awt.GraphicsEnvironment
import java.io.File
import java.lang.management.ManagementFactory
import jdk.jfr.Event
import jdk.jfr.Label
import jdk.jfr.Name

/**
 * Opt-in real-window benchmark of the production gameplay host with a frozen four-item CHOICE.
 * KK_REWARD_DESKTOP_BENCH=1 enables it; WARMUP_MS (2400), SAMPLE_MS (6000), LABEL, OUT,
 * WIDTH (1728) and HEIGHT (1084) use the same prefix. The first four shipped epic items animate
 * at 125% text scale. No player files, simulation or persistence are used. Callback intervals
 * include native scheduling, not GPU presentation; allocation totals cover threads alive at
 * both measurement boundaries. Raw samples are written beside the append-only JSONL output.
 * SCENARIO=transitions starts RUNNING, opens CHOICE at 1 s, closes it at 4 s and repeats every
 * 4 s. Recording is already active before each opening, including its first composition/layout.
 * PHASE_METRICS=1 adds diagnostic root measure/draw wrappers; off by default for matched timing runs.
 */
@OptIn(ExperimentalComposeApi::class, ExperimentalComposeUiApi::class)
fun main() {
    check(System.getenv("KK_REWARD_DESKTOP_BENCH") == "1") { "Set KK_REWARD_DESKTOP_BENCH=1 to run" }
    check(!GraphicsEnvironment.isHeadless()) { "A visible desktop session is required" }
    val warmupMs = rewardSetting("WARMUP_MS", 2400, 0..60_000)
    val sampleMs = rewardSetting("SAMPLE_MS", 6000, 1..60_000)
    val width = rewardSetting("WIDTH", 1728, 640..3840)
    val height = rewardSetting("HEIGHT", 1084, 360..2160)
    val label = System.getenv("KK_REWARD_DESKTOP_LABEL") ?: "run"
    val scenario = System.getenv("KK_REWARD_DESKTOP_SCENARIO") ?: "frozen"
    require(scenario == "frozen" || scenario == "transitions") { "SCENARIO must be frozen or transitions" }
    val phaseMetrics = when (System.getenv("KK_REWARD_DESKTOP_PHASE_METRICS") ?: "0") {
        "0" -> false
        "1" -> true
        else -> error("PHASE_METRICS must be 0 or 1")
    }
    val output = File(System.getenv("KK_REWARD_DESKTOP_OUT") ?: "build/performance/item-stutter-20261030/desktop.jsonl")
    val content = shippedRewardContent
    val items = content.items.filter { it.rarity == ItemRarity.EPIC }.take(4)
    check(items.size == 4)
    val choices = items.map { ChoiceOption(ChoiceType.ITEM, it.name, it.description, it.rarity.displayLabel, itemId = it.id) }
    val samples = RewardDesktopSamples(warmupMs, sampleMs, scenario, phaseMetrics)
    val run = FrozenDesktopChoice(rewardFixtureModel(content = content, choices = choices,
        width = width.toFloat(), height = height.toFloat(), textScale = 1.25f,
        phase = if (scenario == "transitions") GamePhase.RUNNING else GamePhase.CHOICE), samples,
        transitions = scenario == "transitions")
    // JFR registers event metadata on first use; pay that probe-only cost before any frame is timed.
    if (scenario == "transitions") RewardTransitionEvent().apply { phase = "BENCHMARK_INIT" }.commit()
    var result: String? = null
    ComposeRuntimeFlags.isLinkBufferComposerEnabled = true
    application(exitProcessOnExit = false) {
        Window(onCloseRequest = ::exitApplication, title = "KINETICKK reward benchmark — $label",
            state = WindowState(size = DpSize(width.dp, height.dp)), resizable = false) {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.English) {
                if (phaseMetrics) {
                    // Diagnostic command-recording/measure CPU only; native Metal replay happens later.
                    // Independent descendant remeasure can bypass this parent, so zero is not proof of no layout work.
                    Box(Modifier.fillMaxSize().layout { measurable, constraints ->
                        val started = System.nanoTime()
                        val placeable = measurable.measure(constraints)
                        samples.measure(System.nanoTime() - started)
                        layout(placeable.width, placeable.height) {
                            val placementStarted = System.nanoTime()
                            placeable.place(0, 0)
                            samples.place(System.nanoTime() - placementStarted)
                        }
                    }.drawWithContent {
                        val started = System.nanoTime()
                        drawContent()
                        samples.draw(System.nanoTime() - started)
                    }) { GameplayContent(run, inputEnabled = true, onOutput = {}) }
                } else {
                    GameplayContent(run, inputEnabled = true, onOutput = {})
                }
            }
            LaunchedEffect(Unit) {
                try {
                    samples.finished.await()
                    result = samples.json(label, window, run.model, items.map { it.id })
                } finally {
                    exitApplication()
                }
            }
        }
    }
    val completed = checkNotNull(result) { "Benchmark window closed before measurement completed" }
    output.parentFile?.mkdirs()
    val raw = File(output.parentFile, output.nameWithoutExtension + "-" + label.replace(Regex("[^a-zA-Z0-9_.-]"), "_") + "-" + System.currentTimeMillis() + ".tsv")
    samples.writeRaw(raw)
    val record = completed.dropLast(1) + ",\"rawSamples\":${rewardJson(raw.absolutePath)}}"
    output.appendText(record + "\n")
    println(record)
}

/** The production frame callback publishes phase changes after recording the interval just ended. */
private class FrozenDesktopChoice(
    initialModel: GameplayRenderModel,
    private val samples: RewardDesktopSamples,
    private val transitions: Boolean,
) : GameplayInteractionPort {
    private val base = hudTestSnapshot(initialModel.screenWidth, initialModel.screenHeight)
    var model = initialModel
        private set
    private var snapshot = snapshot()
    private var firstCallback = 0L
    private var phaseStarted = 0L
    private var transitionCount = 0
    private val accepted = GameplayAcceptance.Accepted(base.instanceId, base.revision)
    private fun snapshot() = base.withModel(model)
    override val instanceId get() = base.instanceId
    override fun renderSnapshot() = snapshot
    override fun visualFxSnapshot() = VisualFxProjection.EMPTY
    override fun accept(pulse: GameplayInteractionPulse): GameplayAcceptance {
        when (pulse) {
            is GameplayInteractionPulse.FrameElapsed -> {
                val now = System.nanoTime()
                if (firstCallback == 0L) {
                    firstCallback = now
                    phaseStarted = now
                }
                // The next callback sees the new phase, so its interval includes opening work.
                samples.frame(pulse.realDeltaSeconds, model.phase, phaseStarted, transitionCount)
                if (transitions && !samples.finished.isCompleted) {
                    val elapsed = now - firstCallback
                    val phase = if (elapsed % 4_000_000_000L >= 1_000_000_000L) GamePhase.CHOICE else GamePhase.RUNNING
                    if (phase != model.phase) {
                        phaseStarted = System.nanoTime()
                        model = model.with("phase" to phase)
                        snapshot = snapshot()
                        transitionCount++
                        RewardTransitionEvent().apply {
                            this.phase = phase.name
                            this.transition = transitionCount
                            elapsedNanos = phaseStarted - firstCallback
                        }.commit()
                    }
                }
            }
            is GameplayInteractionPulse.ViewportChanged -> {
                check(!samples.measuring) { "Viewport changed during measurement; rerun on one display" }
                model = model.with("screenWidth" to pulse.width, "screenHeight" to pulse.height,
                    "viewportDensity" to pulse.density, "uiScale" to maxOf(1f, pulse.density))
                snapshot = snapshot()
            }
            else -> Unit
        }
        return accepted
    }
}

@OptIn(ExperimentalComposeApi::class)
private class RewardDesktopSamples(
    private val warmupMs: Int,
    private val sampleMs: Int,
    private val scenario: String,
    private val phaseMetrics: Boolean,
) {
    val finished = CompletableDeferred<Unit>()
    var measuring = false
        private set
    private val elapsed = LongArray(sampleMs * 4)
    private val intervals = LongArray(sampleMs * 4)
    private val gameIntervals = LongArray(sampleMs * 4)
    private val phases = IntArray(sampleMs * 4)
    private val phaseAges = LongArray(sampleMs * 4)
    private val transitionCounts = IntArray(sampleMs * 4)
    private val transitionElapsed = LongArray(sampleMs * 4)
    private val firstAfterTransition = BooleanArray(sampleMs * 4)
    private val measureTimes = LongArray(if (phaseMetrics) sampleMs * 4 else 0)
    private val placementTimes = LongArray(if (phaseMetrics) sampleMs * 4 else 0)
    private val drawTimes = LongArray(if (phaseMetrics) sampleMs * 4 else 0)
    private val measureCounts = IntArray(if (phaseMetrics) sampleMs * 4 else 0)
    private val drawCounts = IntArray(if (phaseMetrics) sampleMs * 4 else 0)
    private var pendingMeasure = 0L
    private var pendingPlacement = 0L
    private var pendingDraw = 0L
    private var pendingMeasureCount = 0
    private var pendingDrawCount = 0
    private var previousTransition = 0
    private var count = 0
    private var first = 0L
    private var start = 0L
    private var previous = 0L
    private var end = 0L
    private val threads = (ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean)?.takeIf {
        it.isThreadAllocatedMemorySupported
    }?.also { if (!it.isThreadAllocatedMemoryEnabled) it.isThreadAllocatedMemoryEnabled = true }
    private val operatingSystem = ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean
    private var ids = longArrayOf()
    private var allocatedBefore: LongArray? = null
    private var allocatedBytes = -1L
    private var callbackThreadBytesBefore = -1L
    private var callbackThreadBytes = -1L
    private var callbackThreadId = -1L
    private var cpuBefore = -1L
    private var cpuNanos = -1L
    private var changesBefore = 0L
    private var changes = 0L
    private var changesNow: () -> Long = { 0L }
    private var gcBefore = 0L
    private var gcMillis = 0L

    fun measure(nanos: Long) { pendingMeasure += nanos; pendingMeasureCount++ }
    fun place(nanos: Long) { pendingPlacement += nanos }
    fun draw(nanos: Long) { pendingDraw += nanos; pendingDrawCount++ }

    private fun clearPhases() {
        pendingMeasure = 0L
        pendingPlacement = 0L
        pendingDraw = 0L
        pendingMeasureCount = 0
        pendingDrawCount = 0
    }

    fun frame(deltaSeconds: Float, phase: GamePhase, phaseStarted: Long, transition: Int) {
        if (finished.isCompleted) return
        val now = System.nanoTime()
        if (first == 0L) first = now
        if (now - first < warmupMs * 1_000_000L) return
        if (!measuring) {
            val recomposers = Recomposer.runningRecomposers.value.toList()
            check(recomposers.isNotEmpty()) { "No running composition" }
            changesNow = { recomposers.sumOf { it.changeCount } }
            changesBefore = changesNow()
            ids = threads?.allThreadIds ?: longArrayOf()
            allocatedBefore = threads?.getThreadAllocatedBytes(ids)
            callbackThreadId = Thread.currentThread().id
            callbackThreadBytesBefore = threads?.getThreadAllocatedBytes(callbackThreadId) ?: -1L
            cpuBefore = operatingSystem?.processCpuTime ?: -1L
            gcBefore = ManagementFactory.getGarbageCollectorMXBeans().sumOf { it.collectionTime.coerceAtLeast(0L) }
            start = System.nanoTime()
            previous = start
            previousTransition = transition
            measuring = true
            if (phaseMetrics) clearPhases()
            return
        }
        check(count < intervals.size) { "Frame sample capacity exceeded" }
        check(Thread.currentThread().id == callbackThreadId) { "Gameplay callback thread changed during measurement" }
        elapsed[count] = now - start
        intervals[count] = now - previous
        gameIntervals[count] = (deltaSeconds * 1_000_000_000.0).toLong()
        phases[count] = phase.ordinal
        phaseAges[count] = now - phaseStarted
        transitionCounts[count] = transition
        transitionElapsed[count] = phaseStarted - start
        firstAfterTransition[count] = transition != previousTransition
        if (phaseMetrics) {
            measureTimes[count] = pendingMeasure
            placementTimes[count] = pendingPlacement
            drawTimes[count] = pendingDraw
            measureCounts[count] = pendingMeasureCount
            drawCounts[count] = pendingDrawCount
            clearPhases()
        }
        previousTransition = transition
        count++
        previous = now
        if (now - start < sampleMs * 1_000_000L) return
        end = now
        callbackThreadBytes = threads?.getThreadAllocatedBytes(callbackThreadId)?.let { it - callbackThreadBytesBefore } ?: -1L
        val after = threads?.getThreadAllocatedBytes(ids)
        val before = allocatedBefore
        if (before != null && after != null) allocatedBytes = ids.indices.sumOf {
            if (before[it] >= 0 && after[it] >= 0) after[it] - before[it] else 0L
        }
        cpuNanos = operatingSystem?.processCpuTime?.let { it - cpuBefore } ?: -1L
        gcMillis = ManagementFactory.getGarbageCollectorMXBeans().sumOf { it.collectionTime.coerceAtLeast(0L) } - gcBefore
        changes = changesNow() - changesBefore
        finished.complete(Unit)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    fun json(label: String, window: ComposeWindow, model: GameplayRenderModel, itemIds: List<Int>): String {
        check(count > 0)
        val sorted = intervals.copyOf(count).apply { sort() }
        fun percentile(p: Double) = sorted[((count - 1) * p).toInt()] / 1_000_000.0
        return "{\"suite\":\"reward-desktop-window-v2\",\"label\":${rewardJson(label)},\"scenario\":${rewardJson(scenario)},\"phaseMetrics\":$phaseMetrics," +
            "\"warmupMs\":$warmupMs,\"sampleMs\":$sampleMs,\"actualSampleMs\":${(end - start) / 1_000_000.0}," +
            "\"viewportWidthDp\":${model.screenWidth},\"viewportHeightDp\":${model.screenHeight},\"density\":${model.viewportDensity}," +
            "\"textScale\":1.25,\"itemIds\":${itemIds.joinToString(prefix = "[", postfix = "]")}," +
            "\"backend\":${rewardJson(window.renderApi.toString())},\"requestedVsync\":${rewardJson(System.getProperty("skiko.vsync.enabled") ?: "default")}," +
            "\"reportedRefreshHz\":${window.graphicsConfiguration.device.displayMode.refreshRate},\"linkBufferComposer\":true," +
            "\"javaVersion\":${rewardJson(System.getProperty("java.version"))},\"os\":${rewardJson(System.getProperty("os.name"))}," +
            "\"maxHeapBytes\":${Runtime.getRuntime().maxMemory()},\"transitions\":${transitionCounts[count - 1]}," +
            "\"callbackCount\":$count,\"meanMs\":${sorted.average() / 1_000_000.0},\"p95Ms\":${percentile(.95)},\"p99Ms\":${percentile(.99)},\"maxMs\":${sorted.last() / 1_000_000.0}," +
            "\"over16_67Ms\":${sorted.count { it > 16_666_667L }},\"over33_33Ms\":${sorted.count { it > 33_333_333L }}," +
            "\"compositionChanges\":$changes,\"existingThreadsAllocatedBytes\":$allocatedBytes,\"callbackThreadAllocatedBytes\":$callbackThreadBytes," +
            "\"processCpuNanos\":$cpuNanos,\"gcMillis\":$gcMillis," +
            "\"rootMeasureCpuMs\":${measureTimes.take(count).sum() / 1_000_000.0}," +
            "\"rootPlacementCpuMs\":${placementTimes.take(count).sum() / 1_000_000.0}," +
            "\"rootDrawRecordingCpuMs\":${drawTimes.take(count).sum() / 1_000_000.0}," +
            "\"choiceOpening\":${group { phases[it] == GamePhase.CHOICE.ordinal && (phaseAges[it] < 300_000_000L || firstAfterTransition[it]) }}," +
            "\"firstCallbackAfterOpening\":${group { phases[it] == GamePhase.CHOICE.ordinal && firstAfterTransition[it] }}," +
            "\"choiceSettled\":${group { phases[it] == GamePhase.CHOICE.ordinal && phaseAges[it] >= 300_000_000L && !firstAfterTransition[it] }}," +
            "\"running\":${group { phases[it] == GamePhase.RUNNING.ordinal }}}"
    }

    private fun group(includes: (Int) -> Boolean): String {
        val selected = (0 until count).filter(includes).map { intervals[it] }.sorted()
        if (selected.isEmpty()) return "{\"count\":0}"
        fun percentile(p: Double) = selected[((selected.size - 1) * p).toInt()] / 1_000_000.0
        return "{\"count\":${selected.size},\"meanMs\":${selected.average() / 1_000_000.0}," +
            "\"p95Ms\":${percentile(.95)},\"p99Ms\":${percentile(.99)},\"maxMs\":${selected.last() / 1_000_000.0}," +
            "\"over16_67Ms\":${selected.count { it > 16_666_667L }},\"over33_33Ms\":${selected.count { it > 33_333_333L }}}"
    }

    fun writeRaw(file: File) = file.bufferedWriter().use { out ->
        out.appendLine("elapsed_ns\tcallback_interval_ns\tgame_frame_delta_ns\tphase\tphase_age_ns\ttransition_count\ttransition_elapsed_ns\tfirst_callback_after_transition\troot_measure_ns\troot_placement_ns\troot_draw_recording_ns\troot_measure_calls\troot_draw_calls")
        repeat(count) { out.appendLine("${elapsed[it]}\t${intervals[it]}\t${gameIntervals[it]}\t${GamePhase.entries[phases[it]].name}\t${phaseAges[it]}\t${transitionCounts[it]}\t${transitionElapsed[it]}\t${firstAfterTransition[it]}\t${measureTimes.getOrElse(it) { 0 }}\t${placementTimes.getOrElse(it) { 0 }}\t${drawTimes.getOrElse(it) { 0 }}\t${measureCounts.getOrElse(it) { 0 }}\t${drawCounts.getOrElse(it) { 0 }}") }
    }
}

@Name("kinetickk.RewardTransition")
@Label("Reward benchmark phase transition")
private class RewardTransitionEvent : Event() {
    @JvmField var phase = ""
    @JvmField var transition = 0
    @JvmField var elapsedNanos = 0L
}

private fun rewardSetting(name: String, fallback: Int, range: IntRange): Int =
    (System.getenv("KK_REWARD_DESKTOP_$name")?.toInt() ?: fallback).also { require(it in range) { "$name must be in $range" } }

private fun rewardJson(value: String): String = buildString {
    append('"')
    value.forEach { when (it) {
        '"' -> append("\\\"")
        '\\' -> append("\\\\")
        else -> if (it < ' ') append("\\u" + it.code.toString(16).padStart(4, '0')) else append(it)
    } }
    append('"')
}
