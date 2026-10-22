// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer as ComposeTextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.interaction.fx.InteractionFxLimits
import kinetickk.ball.gameplay.interaction.fx.InteractionFxReducer
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.PickupProjection
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.ProjectileProjection
import kinetickk.ball.gameplay.nucleus.render.WeaponEntityKind
import kinetickk.ball.gameplay.nucleus.render.WeaponEntityProjection
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.ReactionEffectsLevel
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.KkTextStats
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Opt-in Canvas CPU benchmark for reaction feedback, including newly arriving damage text.
 * Setup, simulation, FX reduction and projection are excluded; the draw includes the complete HUD
 * and world on a raster Canvas. It does not measure Compose scheduling, GPU presentation or devices.
 *
 * Run with KK_REACTION_RENDER_BENCH=1; KK_REACTION_RENDER_OUT selects the JSON-lines output file and
 * KK_REACTION_RENDER_EFFECTS the reaction effects setting (FULL, REDUCED or MINIMAL; FULL by default).
 * The existing redesign benchmark remains an independent workload with no visual FX.
 */
class ReactionHeavyRenderBenchmarkTest {
    @Test
    fun measureReactionHeavyGameplayDraw() {
        if (System.getenv("KK_REACTION_RENDER_BENCH") != "1") return
        val effectsLevel = ReactionEffectsLevel.valueOf(System.getenv("KK_REACTION_RENDER_EFFECTS") ?: ReactionEffectsLevel.FULL.name)
        val frames = reactionFrames().map { it.copy(model = it.model.with("settings" to it.model.settings.copy(reactionEffects = effectsLevel))) }
        val warmups = positiveEnvironmentInt("KK_REACTION_RENDER_WARMUPS", 240)
        val samples = positiveEnvironmentInt("KK_REACTION_RENDER_SAMPLES", 480)
        val canvas = ReactionBenchmarkCanvas()
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        check(threads.isThreadAllocatedMemorySupported)
        if (!threads.isThreadAllocatedMemoryEnabled) threads.isThreadAllocatedMemoryEnabled = true
        val thread = Thread.currentThread().id
        val scenarios = listOf("busy_world_without_fx", "busy_world_reaction_churn") +
            if (System.getenv("KK_REACTION_RENDER_ABLATIONS") == "1") listOf(
                "reaction_churn_without_damage_numbers", "reaction_churn_without_particles",
                "reaction_churn_without_element_bursts", "reaction_churn_without_arcs",
            ) else emptyList()
        for (scenario in scenarios) {
            val withEffects = scenario != "busy_world_without_fx"
            val selectedFrames = frames.map { frame ->
                val effects = when (scenario) {
                    "reaction_churn_without_damage_numbers" -> frame.effects.copy(damageNumbers = immutableListOf())
                    "reaction_churn_without_particles" -> frame.effects.copy(particles = immutableListOf())
                    "reaction_churn_without_element_bursts" -> frame.effects.copy(elementBursts = immutableListOf())
                    "reaction_churn_without_arcs" -> frame.effects.copy(weaponArcs = immutableListOf())
                    else -> frame.effects
                }
                frame.copy(effects = effects)
            }
            repeat(warmups) { frame -> canvas.draw(selectedFrames[frame % frames.size], withEffects) }
            val elapsed = LongArray(samples)
            val allocatedBefore = threads.getThreadAllocatedBytes(thread)
            val measuredBefore = KkTextStats.layoutsMeasured
            for (sample in elapsed.indices) {
                val started = System.nanoTime()
                canvas.draw(selectedFrames[sample % frames.size], withEffects)
                elapsed[sample] = System.nanoTime() - started
            }
            val allocated = threads.getThreadAllocatedBytes(thread) - allocatedBefore
            val layoutsMeasured = KkTextStats.layoutsMeasured - measuredBefore
            elapsed.sort()
            fun percentile(fraction: Double): Double = elapsed[((samples - 1) * fraction).toInt()] / 1_000_000.0
            val result = "{" +
                "\"suite\":\"reaction-canvas-v1\",\"scenario\":\"$scenario\"," +
                "\"label\":\"${jsonString(System.getenv("KK_REACTION_RENDER_LABEL") ?: "run")}\",\"effects\":\"$effectsLevel\"," +
                "\"width\":$REACTION_WIDTH,\"height\":$REACTION_HEIGHT,\"frames\":${frames.size}," +
                "\"warmups\":$warmups,\"samples\":$samples," +
                "\"enemies\":$REACTION_ENEMIES,\"projectiles\":120,\"fields\":6," +
                "\"maxParticles\":${frames.maxOf { it.effects.particles.size }}," +
                "\"maxDamageNumbers\":${frames.maxOf { it.effects.damageNumbers.size }}," +
                "\"maxArcs\":${frames.maxOf { it.effects.weaponArcs.size }}," +
                "\"maxElementBursts\":${frames.maxOf { it.effects.elementBursts.size }}," +
                "\"meanMs\":${elapsed.average() / 1_000_000.0},\"p50Ms\":${percentile(0.5)}," +
                "\"p95Ms\":${percentile(0.95)},\"p99Ms\":${percentile(0.99)}," +
                "\"allocatedBytesPerFrame\":${allocated.toDouble() / samples}," +
                "\"textLayoutsPerFrame\":${layoutsMeasured.toDouble() / samples}}"
            println(result)
            File(System.getenv("KK_REACTION_RENDER_OUT") ?: "build/reaction-render-benchmark.jsonl").apply {
                parentFile?.mkdirs()
                appendText(result + "\n")
            }
        }
    }

    @Test
    fun reactionWorkloadRetainsPublishedFxAndStaysWithinPresentationBounds() {
        val frames = reactionFrames()
        assertEquals(REACTION_CYCLE, frames.size)
        for (frame in frames) {
            assertEquals(REACTION_ENEMIES, frame.model.enemies.size)
            assertTrue(frame.model.enemies.all { it.statusBits != 0 })
            assertEquals(InteractionFxLimits.MAX_DAMAGE_NUMBERS, frame.effects.damageNumbers.size)
            assertTrue(frame.effects.particles.size in 500..InteractionFxLimits.MAX_PARTICLES)
            assertTrue(frame.effects.weaponArcs.size in 96..InteractionFxLimits.MAX_WEAPON_ARCS)
            assertTrue(frame.effects.elementBursts.size in 12..InteractionFxLimits.MAX_ELEMENT_BURSTS)
            assertTrue(frame.effects.shockwaves.size <= InteractionFxLimits.MAX_SHOCKWAVES)
            assertTrue(frame.effects.motionEchoes.size <= InteractionFxLimits.MAX_MOTION_ECHOES)
        }
        // The fixture uses the real reducer; future cue delivery must not mutate a captured frame.
        val reducer = InteractionFxReducer(REACTION_SEED)
        repeat(REACTION_PREWARM) { reducer.apply(reactionCues(it)) }
        val first = reducer.snapshot()
        val frozen = first.toString()
        repeat(REACTION_CYCLE) { reducer.apply(reactionCues(REACTION_PREWARM + it)); reducer.snapshot() }
        assertEquals(frozen, first.toString())
        assertNotEquals(frames.first().effects.damageNumbers.last().amount, frames.last().effects.damageNumbers.last().amount)
    }

    @Test
    fun denseReactionFeedbackIsActuallyDrawn() {
        val frame = reactionFrames().first()
        val canvas = ReactionBenchmarkCanvas()
        canvas.draw(frame, withEffects = false)
        val without = canvas.pixels()
        canvas.draw(frame, withEffects = true)
        val with = canvas.pixels()
        assertTrue(with.indices.count { with[it] != without[it] } > 10_000,
            "The dense scene must exercise visible reaction feedback, not only off-screen effects")
        canvas.draw(frame, withEffects = true)
        assertTrue(with.contentEquals(canvas.pixels()), "Drawing the same immutable frame twice preserves its pixels")
    }
}

internal const val REACTION_WIDTH = 1_440
internal const val REACTION_HEIGHT = 810
internal const val REACTION_ENEMIES = 120
internal const val REACTION_CYCLE = 120
internal const val REACTION_PREWARM = 120
internal const val REACTION_SEED = 731_991
internal const val REACTION_STEP = 1f / 60f

internal data class ReactionRenderFrame(val model: GameplayRenderModel, val effects: VisualFxProjection)

/** The same bounded seeded stream is prepared before both benchmark runs. */
internal fun reactionFrames(): List<ReactionRenderFrame> {
    val source = hudTestModel(REACTION_WIDTH.toFloat(), REACTION_HEIGHT.toFloat())
    val base = source.with(
        "settings" to source.settings.copy(damageNumberFormat = DamageNumberFormat.FULL),
        "coreX" to 0f, "coreY" to 0f, "cameraX" to 0f, "cameraY" to 0f,
        "weapon" to WeaponId.EVENT_LENS, "weaponLevel" to 10, "attackSpeed" to 8f,
        "velocityX" to 520f, "velocityY" to 160f, "combo" to 64, "comboTime" to 2f,
        "projectiles" to List(120) { index ->
            val x = gridX(index, 15, 74f)
            val y = gridY(index, 15, 8, 65f)
            ProjectileProjection(x, y, 200f, -120f, 5f, 1f, index % 3 == 0, 25f, 0, index % 5, null, x - 3f, y + 2f)
        }.toImmutableList(),
        "pickups" to List(60) { index ->
            val x = gridX(index, 12, 80f)
            val y = gridY(index, 12, 5, 76f)
            PickupProjection(PickupType.DATA, x, y, 0f, 0f, 5f, x, y)
        }.toImmutableList(),
    )
    val types = listOf(EnemyType.DRIFTER, EnemyType.SHOOTER, EnemyType.CHARGER, EnemyType.WEAVER,
        EnemyType.WARDEN, EnemyType.SPLITTER, EnemyType.ELITE)
    val reducer = InteractionFxReducer(REACTION_SEED)
    repeat(REACTION_PREWARM) { reducer.apply(reactionCues(it)) }
    return List(REACTION_CYCLE) { frame ->
        reducer.apply(reactionCues(REACTION_PREWARM + frame))
        val enemies = List(REACTION_ENEMIES) { index ->
            val x = gridX(index, 15, 78f) + sin(frame * 0.05f + index) * 5f
            val y = gridY(index, 15, 8, 65f) + cos(frame * 0.04f + index) * 5f
            val type = types[index % types.size]
            val element = (index + frame / 12) % StatusElement.entries.size
            val status = ((3 + index % 3) shl (element * 4)) or (2 shl (((element + 1) % 7) * 4))
            EnemyProjection(index + 1, type, x, y, 30f, -20f, 80f, 120f, 16f, 0.5f,
                if ((index + frame) % 11 == 0) 0.5f else 0f, 0f, 0f, x - 0.5f, y + 0.3f, false,
                statusBits = status, affinityBits = runAffinity(type))
        }.toImmutableList()
        val fields = List(6) { index ->
            val x = gridX(index, 3, 240f)
            val y = gridY(index, 3, 2, 240f)
            WeaponEntityProjection(WeaponEntityKind.WELL, x, y, x, y, x, y, x, y,
                0f, 0f, 105f, 0f, 0.2f + ((frame + index * 17) % 100) / 100f, 1.2f, 0)
        }.toImmutableList()
        ReactionRenderFrame(base.with("elapsed" to 120f + frame * REACTION_STEP,
            "enemies" to enemies, "weaponEntities" to fields), reducer.snapshot())
    }
}

/** Roughly 480 hits/s, 180 burst cues/s and 720 arcs/s; all visual lists obey the reducer's real caps. */
internal fun reactionCues(frame: Int): List<VisualFxCue> = buildList {
    add(VisualFxCue.EffectsAdvanced(REACTION_STEP))
    add(VisualFxCue.WeaponArcsAdvanced(REACTION_STEP))
    add(VisualFxCue.MotionSample(REACTION_STEP, 0f, 0f, 900f, 0f))
    repeat(8) { hit ->
        val index = (frame * 8 + hit) % 140
        val x = gridX(index, 14, 78f)
        val y = gridY(index, 14, 10, 48f)
        add(VisualFxCue.DamageNumberAdded(x, y, 100_003L + (frame * 8L + hit) * 997L, hit % 4 == 0, index % 8))
    }
    repeat(3) { burst ->
        val index = (frame * 3 + burst) % 42
        val x = gridX(index, 7, 145f)
        val y = gridY(index, 7, 6, 85f)
        add(VisualFxCue.Burst(x, y, 12, index % 5, ParticleDensity.NORMAL))
        if (burst == 0) {
            add(VisualFxCue.StatusBroken(x, y, frame % StatusElement.entries.size))
            add(VisualFxCue.ShockwaveAdded(x, y, 0.4f, 70f, 1))
        } else {
            add(VisualFxCue.ReactionTriggered(x, y, (frame + burst) % ReactionId.entries.size, 130f, false))
        }
    }
    repeat(12) { arc ->
        val from = (frame * 12 + arc) % 120
        val to = (from + 16) % 120
        add(VisualFxCue.WeaponArcAdded(gridX(from, 15, 78f), gridY(from, 15, 8, 65f),
            gridX(to, 15, 78f), gridY(to, 15, 8, 65f), 0.2f))
    }
}

private class ReactionBenchmarkCanvas {
    private val bitmap = ImageBitmap(REACTION_WIDTH, REACTION_HEIGHT)
    private val canvas = Canvas(bitmap)
    private val scope = CanvasDrawScope()
    private val density = Density(1f)
    private val size = Size(REACTION_WIDTH.toFloat(), REACTION_HEIGHT.toFloat())
    private val memory = HudPresentationMemory()
    private val measurer = CanvasTextMeasurer(
        ComposeTextMeasurer(createFontFamilyResolver(), density, LayoutDirection.Ltr, cacheSize = 64),
        1.25f, AppLanguage.English, HudTestFonts.typography,
    )

    fun draw(frame: ReactionRenderFrame, withEffects: Boolean) {
        scope.draw(density, LayoutDirection.Ltr, canvas, size) {
            drawGameplay(frame.model, if (withEffects) frame.effects else VisualFxProjection.EMPTY,
                measurer, frame.model.elapsed, pauseLayout = null, hudMemory = memory)
        }
    }

    fun pixels(): IntArray {
        val pixels = bitmap.toPixelMap()
        return IntArray(REACTION_WIDTH * REACTION_HEIGHT) { pixels[it % REACTION_WIDTH, it / REACTION_WIDTH].toArgb() }
    }
}

private fun gridX(index: Int, columns: Int, spacing: Float): Float = ((index % columns) - (columns - 1) / 2f) * spacing
private fun gridY(index: Int, columns: Int, rows: Int, spacing: Float): Float = ((index / columns) - (rows - 1) / 2f) * spacing

private fun positiveEnvironmentInt(name: String, default: Int): Int =
    System.getenv(name)?.toInt()?.also { require(it > 0) { "$name must be positive" } } ?: default

private fun jsonString(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
