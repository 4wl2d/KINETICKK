// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer as ComposeTextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.gameplay.interaction.fx.BuildNotificationProjection
import kinetickk.ball.gameplay.interaction.fx.DamageNumberProjection
import kinetickk.ball.gameplay.interaction.fx.InteractionFxLimits
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkTextStats
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Dense combat: the most live damage numbers the reducer allows, each read on every frame of its
 * life (a face and a shadow layout), drawn in the real gameplay frame together with the HUD. The
 * frame's text must stay resident in the shared text caches: a frame that adds no new number
 * measures no text and re-reads no line metrics, so the HUD digits keep their layouts.
 */
class WorldHudTextLoadTest {
    @Test
    fun denseCombatFramesKeepHudAndDamageNumberTextResident() {
        listOf(844 to 390, 1_440 to 810, 390 to 844).forEach { (width, height) -> assertDenseCombat(width, height) }
    }

    private fun assertDenseCombat(width: Int, height: Int) {
        val start = hudTestModel(width.toFloat(), height.toFloat())
        // Full amounts keep every number's text unique, so the measure counts below are exact.
        val base = start.with(
            "settings" to start.settings.copy(damageNumberFormat = DamageNumberFormat.FULL),
            "hp" to 30f, "maxHp" to 180f, "shield" to 20f, "maxShield" to 60f, "combo" to 14, "comboTime" to 1.4f,
            "comboWindow" to 2.8f, "keys" to 2, "runMatter" to 12_345L, "level" to 45, "polarityStability" to 0.2f,
        )
        // HUD values change every frame (speed, draining polarity and integrity, a growing chain).
        val models = List(CYCLE) { frame ->
            base.with(
                "velocityX" to 300f + frame * 37.3f,
                "polarityStability" to 0.24f - frame * 0.004f,
                "hp" to 36f - frame * 0.7f,
                "combo" to 12 + frame,
            )
        }
        val amounts = LongArray(MAX_NUMBERS) { index -> 17L + index * 7_919L }
        val born = IntArray(MAX_NUMBERS) { index -> -(index % CYCLE) }
        val critical = BooleanArray(MAX_NUMBERS) { index -> index % 9 == 0 }
        fun fx(frame: Int, model: GameplayRenderModel): VisualFxProjection {
            val numbers = List(MAX_NUMBERS) { index ->
                // Every number lives one cycle (0.6 s at 60 fps); an expired number respawns with
                // the same amount, so the steady frames add no new text.
                val age = Math.floorMod(frame - born[index], CYCLE)
                DamageNumberProjection(
                    model.cameraX + ((index % 14) - 6.5f) * width / 15f,
                    model.cameraY + ((index / 14) - 4.5f) * height / 11f,
                    amounts[index], critical[index],
                    life = InteractionFxLimits.DAMAGE_NUMBER_LIFE_SECONDS - age / 60f,
                    driftX = 0.6f, driftY = -0.8f,
                )
            }.toImmutableList()
            return VisualFxProjection.EMPTY.copy(
                damageNumbers = numbers,
                buildNotifications = immutableListOf(
                    BuildNotificationProjection("Neon Ram", immutableListOf("Impact damage +0.05", "Weapon power +0.04"), 4f),
                ),
            )
        }
        val steady = List(CYCLE) { frame -> fx(frame, models[frame]) }
        val measurer = CanvasTextMeasurer(ComposeTextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1.25f,
            AppLanguage.English)
        val memory = HudPresentationMemory()
        val bitmap = ImageBitmap(width, height)
        val canvas = Canvas(bitmap)
        val scope = CanvasDrawScope()
        val size = Size(width.toFloat(), height.toFloat())
        fun empty() = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) { drawRect(Kk.Ink) }
        fun frame(model: GameplayRenderModel, effects: VisualFxProjection, index: Int) =
            scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) {
                drawGameplay(model, effects, measurer, 10f + index * 0.0001f, pauseLayout = null, hudMemory = memory)
            }

        repeat(3 * CYCLE) { empty(); frame(models[it % CYCLE], steady[it % CYCLE], it) }
        val measured = KkTextStats.layoutsMeasured
        val reads = KkTextStats.lineMetricReads
        val emptyBytes = allocated { repeat(4 * CYCLE) { empty() } }
        val frameBytes = allocated { repeat(4 * CYCLE) { frame(models[it % CYCLE], steady[it % CYCLE], it) } }
        assertEquals(measured, KkTextStats.layoutsMeasured, "$width x $height: steady dense-combat frames measure no text")
        assertEquals(reads, KkTextStats.lineMetricReads, "$width x $height: steady frames re-read no line metrics")
        val perFrame = (frameBytes - emptyBytes) / (4 * CYCLE)
        assertTrue(perFrame <= 96, "$width x $height: a dense-combat frame allocates $perFrame bytes above an empty frame")

        // Heavy churn: four new numbers every frame for well over a full pass through the caches.
        // Only the new numbers are measured (a face and a shadow each); the HUD, the feed and the
        // numbers still alive stay resident.
        var next = 0
        var serial = 0L
        repeat(CHURN_FRAMES) { churn ->
            val frameIndex = 3 * CYCLE + churn
            repeat(NEW_PER_FRAME) {
                val index = next
                next = (next + 1) % MAX_NUMBERS
                amounts[index] = 1_000_003L + serial++ * 104_729L
                born[index] = frameIndex
            }
            val model = models[frameIndex % CYCLE]
            val effects = fx(frameIndex, model)
            val before = KkTextStats.layoutsMeasured
            val readsBefore = KkTextStats.lineMetricReads
            frame(model, effects, frameIndex)
            assertEquals(2L * NEW_PER_FRAME, KkTextStats.layoutsMeasured - before,
                "$width x $height churn frame $churn: only the new numbers' layouts are measured")
            assertEquals(2L * NEW_PER_FRAME, KkTextStats.lineMetricReads - readsBefore,
                "$width x $height churn frame $churn: only the new layouts read line metrics")
        }
    }

    private inline fun allocated(block: () -> Unit): Long {
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val id = Thread.currentThread().id
        val before = threads.getThreadAllocatedBytes(id)
        block()
        return threads.getThreadAllocatedBytes(id) - before
    }

    private companion object {
        /** Frames in a damage number's life at 60 fps. */
        const val CYCLE = 36
        const val MAX_NUMBERS = InteractionFxLimits.MAX_DAMAGE_NUMBERS
        const val NEW_PER_FRAME = 4
        const val CHURN_FRAMES = 400
    }
}
