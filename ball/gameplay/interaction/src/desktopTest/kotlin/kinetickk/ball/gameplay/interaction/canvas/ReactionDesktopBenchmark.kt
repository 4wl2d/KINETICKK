// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.ComposeRuntimeFlags
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer as ComposeTextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import java.awt.GraphicsEnvironment
import java.io.File
import kotlin.math.max

/**
 * Opt-in real-window companion to [ReactionHeavyRenderBenchmarkTest]. Uses the same prepared
 * reaction frames, fonts and draw entrypoint at the actual window density. Simulation, FX reduction
 * and projection are excluded. Viewport variants are prepared before the warmup begins.
 * Canvas CPU time records command submission; frame callbacks include window scheduling but are
 * not GPU timings or proof that the compositor presented each frame.
 *
 * KK_REACTION_DESKTOP_BENCH=1 enables this main. WARMUP_MS (default 2000), SAMPLE_MS (6000), LABEL,
 * OUT, and EFFECTS (0 disables FX, default 1) use the same KK_REACTION_DESKTOP_ prefix. Results append
 * as JSON lines. No profile or preferences are loaded, and this process closes only its own window
 * when measurement finishes.
 */
@OptIn(ExperimentalComposeApi::class, ExperimentalComposeUiApi::class)
fun main() {
    check(System.getenv("KK_REACTION_DESKTOP_BENCH") == "1") { "Set KK_REACTION_DESKTOP_BENCH=1 to run" }
    check(!GraphicsEnvironment.isHeadless()) { "This benchmark requires a visible desktop session" }
    val warmupMs = desktopDuration("WARMUP_MS", 2_000, allowZero = true)
    val sampleMs = desktopDuration("SAMPLE_MS", 6_000)
    val label = System.getenv("KK_REACTION_DESKTOP_LABEL") ?: "run"
    val effects = when (System.getenv("KK_REACTION_DESKTOP_EFFECTS") ?: "1") {
        "1" -> true
        "0" -> false
        else -> error("KK_REACTION_DESKTOP_EFFECTS must be 0 or 1")
    }
    val output = File(System.getenv("KK_REACTION_DESKTOP_OUT")
        ?: "build/performance/reaction-optimization/desktop-result.jsonl")
    val frames = reactionFrames()
    val renderer = ReactionWindowRenderer(frames, effects)
    val samples = ReactionWindowSamples(warmupMs, sampleMs)
    var result: String? = null
    // Match the production desktop entrypoint before creating the first Composition.
    ComposeRuntimeFlags.isLinkBufferComposerEnabled = true
    application(exitProcessOnExit = false) {
        Window(
            onCloseRequest = ::exitApplication,
            title = "KINETICKK reaction benchmark — $label",
            state = WindowState(size = DpSize(REACTION_WIDTH.dp, REACTION_HEIGHT.dp)),
            resizable = false,
        ) {
            val frameIndex = remember { mutableIntStateOf(0) }
            Canvas(Modifier.fillMaxSize()) {
                // A display or surface change restarts warmup so a result never mixes viewports.
                if (renderer.prepare(this)) samples.surfaceChanged()
                val index = frameIndex.intValue
                val started = System.nanoTime()
                renderer.draw(this, index)
                samples.draw(started, System.nanoTime(), size, density, fontScale)
            }
            LaunchedEffect(Unit) {
                try {
                    while (true) {
                        val finished = withFrameNanos { clockNanos -> samples.frame(clockNanos, System.nanoTime()) }
                        if (finished) break
                        // Read only by Canvas: each callback invalidates drawing, not the entire UI.
                        frameIndex.intValue = (frameIndex.intValue + 1) % frames.size
                    }
                    result = samples.json(label, window, frames.size, effects)
                } finally {
                    exitApplication()
                }
            }
        }
    }
    val completed = checkNotNull(result) { "Benchmark window closed before measurement completed" }
    output.parentFile?.mkdirs()
    output.appendText(completed + "\n")
    println(completed)
    println("Desktop reaction benchmark appended to ${output.absolutePath}")
}

/** The same world fixture, using the viewport and text-density contract of GameInteraction. */
private class ReactionWindowRenderer(
    private val sourceFrames: List<ReactionRenderFrame>,
    private val effects: Boolean,
) {
    private var size = Size.Zero
    private var density = 0f
    private var fontScale = 0f
    private var frames: List<ReactionRenderFrame> = emptyList()
    private var memory = HudPresentationMemory()
    private lateinit var measurer: CanvasTextMeasurer

    fun prepare(target: DrawScope): Boolean {
        if (size == target.size && density == target.density && fontScale == target.fontScale) return false
        require(target.size.width > 0f && target.size.height > 0f && target.density > 0f)
        size = target.size
        density = target.density
        fontScale = target.fontScale
        val width = max(1f, size.width / density)
        val height = max(1f, size.height / density)
        // Match MutableGameState.resize: viewport/pointer in dp, UI scale at least one.
        // Enemy, projectile and visual-effect coordinates remain the original seeded world values.
        frames = sourceFrames.map { frame ->
            val model = frame.model
            frame.copy(model = model.with(
                "screenWidth" to width,
                "screenHeight" to height,
                "viewportDensity" to density,
                "uiScale" to max(1f, density),
                "pointerX" to (model.pointerX / model.screenWidth * width).coerceIn(0f, width),
                "pointerY" to (model.pointerY / model.screenHeight * height).coerceIn(0f, height),
            ))
        }
        memory = HudPresentationMemory()
        measurer = CanvasTextMeasurer(
            ComposeTextMeasurer(createFontFamilyResolver(), Density(density, fontScale), LayoutDirection.Ltr, cacheSize = 64),
            1.25f, AppLanguage.English, HudTestFonts.typography,
        )
        return true
    }

    fun draw(target: DrawScope, index: Int) {
        val frame = frames[index]
        with(target) {
            drawGameplay(frame.model, if (effects) frame.effects else VisualFxProjection.EMPTY,
                measurer, frame.model.elapsed, pauseLayout = null, hudMemory = memory)
        }
    }
}

private class ReactionWindowSamples(private val warmupMs: Int, private val sampleMs: Int) {
    // Preallocated outside measurement. Exceeding 4,000 callbacks/draws per second fails visibly.
    private val callbackIntervals = LongArray(sampleMs * 4)
    private val clockIntervals = LongArray(sampleMs * 4)
    private val drawTimes = LongArray(sampleMs * 4)
    private var frameCount = 0
    private var drawCount = 0
    private var firstDraw = 0L
    private var previousCallback = 0L
    private var previousClock = 0L
    private var canvasSize = Size.Zero
    private var canvasDensity = 0f
    private var canvasFontScale = 0f
    private var surfaceConfigurations = 0
    private val measurementStart get() = firstDraw + warmupMs * 1_000_000L
    private val measurementEnd get() = measurementStart + sampleMs * 1_000_000L

    @Synchronized
    fun surfaceChanged() {
        frameCount = 0
        drawCount = 0
        firstDraw = 0L
        previousCallback = 0L
        previousClock = 0L
        surfaceConfigurations++
    }

    @Synchronized
    fun frame(clockNanos: Long, callbackNanos: Long): Boolean {
        if (firstDraw == 0L) return false
        if (previousCallback >= measurementStart && callbackNanos < measurementEnd) {
            check(frameCount < callbackIntervals.size) { "Frame sample capacity exceeded" }
            callbackIntervals[frameCount] = callbackNanos - previousCallback
            clockIntervals[frameCount] = clockNanos - previousClock
            frameCount++
        }
        previousCallback = callbackNanos
        previousClock = clockNanos
        return callbackNanos >= measurementEnd
    }

    @Synchronized
    fun draw(started: Long, finished: Long, size: Size, density: Float, fontScale: Float) {
        if (firstDraw == 0L) firstDraw = started
        canvasSize = size
        canvasDensity = density
        canvasFontScale = fontScale
        if (started >= measurementStart && finished < measurementEnd) {
            check(drawCount < drawTimes.size) { "Draw sample capacity exceeded" }
            drawTimes[drawCount++] = finished - started
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Synchronized
    fun json(label: String, window: ComposeWindow, fixtureFrames: Int, effects: Boolean): String {
        check(frameCount > 0 && drawCount > 0) { "No measured frame callbacks or Canvas draws" }
        val transform = window.graphicsConfiguration.defaultTransform
        val backend = window.renderApi.toString()
        return "{" +
            "\"suite\":\"reaction-desktop-window-v2\",\"label\":${desktopJsonString(label)},\"effects\":$effects," +
            "\"warmupMs\":$warmupMs,\"sampleMs\":$sampleMs,\"fixtureFrames\":$fixtureFrames," +
            "\"fixtureWidth\":$REACTION_WIDTH,\"fixtureHeight\":$REACTION_HEIGHT," +
            "\"canvasWidthPx\":${canvasSize.width},\"canvasHeightPx\":${canvasSize.height}," +
            "\"composeDensity\":$canvasDensity,\"fontScale\":$canvasFontScale,\"displayScaleX\":${transform.scaleX},\"displayScaleY\":${transform.scaleY}," +
            "\"viewportWidthDp\":${canvasSize.width / canvasDensity},\"viewportHeightDp\":${canvasSize.height / canvasDensity}," +
            "\"uiScale\":${max(1f, canvasDensity)},\"surfaceConfigurations\":$surfaceConfigurations," +
            "\"backend\":${desktopJsonString(backend)},\"requestedVsync\":${desktopJsonString(System.getProperty("skiko.vsync.enabled") ?: "default")}," +
            "\"reportedRefreshHz\":${window.graphicsConfiguration.device.displayMode.refreshRate}," +
            "\"linkBufferComposer\":true,\"javaVersion\":${desktopJsonString(System.getProperty("java.version"))}," +
            "\"os\":${desktopJsonString(System.getProperty("os.name"))},\"arch\":${desktopJsonString(System.getProperty("os.arch"))}," +
            "\"callbackInterval\":${desktopTimingSummary(callbackIntervals, frameCount)}," +
            "\"frameClockInterval\":${desktopTimingSummary(clockIntervals, frameCount)}," +
            "\"canvasCpu\":${desktopTimingSummary(drawTimes, drawCount)}}"
    }
}

private fun desktopTimingSummary(samples: LongArray, count: Int): String {
    val sorted = samples.copyOf(count).apply { sort() }
    fun percentile(fraction: Double) = sorted[((count - 1) * fraction).toInt()] / 1_000_000.0
    return "{\"count\":$count,\"meanMs\":${sorted.average() / 1_000_000.0}," +
        "\"p50Ms\":${percentile(0.5)},\"p95Ms\":${percentile(0.95)},\"p99Ms\":${percentile(0.99)}," +
        "\"maxMs\":${sorted.last() / 1_000_000.0},\"over20Ms\":${sorted.count { it > 20_000_000L }}," +
        "\"over33_33Ms\":${sorted.count { it > 33_333_333L }},\"over50Ms\":${sorted.count { it > 50_000_000L }}}"
}

private fun desktopDuration(suffix: String, default: Int, allowZero: Boolean = false): Int {
    val name = "KK_REACTION_DESKTOP_$suffix"
    return (System.getenv(name)?.toInt() ?: default).also {
        require(it in (if (allowZero) 0 else 1)..60_000) { "$name must be between ${if (allowZero) 0 else 1} and 60000" }
    }
}

private fun desktopJsonString(value: String): String = buildString {
    append('"')
    for (character in value) when (character) {
        '"' -> append("\\\"")
        '\\' -> append("\\\\")
        else -> if (character < ' ') append("\\u" + character.code.toString(16).padStart(4, '0')) else append(character)
    }
    append('"')
}
