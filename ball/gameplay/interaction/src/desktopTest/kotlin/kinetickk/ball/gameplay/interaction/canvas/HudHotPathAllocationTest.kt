// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.interaction.fx.BuildNotificationProjection
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.PointOfInterestProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/** The running HUD draws every frame: once its caches are warm it must not allocate. */
class HudHotPathAllocationTest {
    @Test
    fun steadyHudFramesAllocateNothingBeyondTheEmptyFrame() {
        listOf(1_440 to 810, 844 to 390, 390 to 844).forEach { (width, height) ->
            val model = hudTestModel(width.toFloat(), height.toFloat()).with(
                "hp" to 30f, "maxHp" to 180f, "shield" to 20f, "maxShield" to 60f, "heat" to 100f, "overheated" to true,
                "combo" to 14, "comboTime" to 1.4f, "comboWindow" to 2.8f, "keys" to 2, "runMatter" to 12_345L,
                "overdriveTime" to 3f, "overdriveCharge" to 40f, "dashPhaseTime" to 0.1f, "velocityX" to 2_600f,
                "level" to 45, "polarityStability" to 0.2f, "message" to "ELITE SIGNAL", "messageTime" to 1f,
                "equippedRelics" to listOf(EquippedRelic(RelicId.entries[0], 1), EquippedRelic(RelicId.entries[1], 2)).toImmutableList(),
                "enemies" to listOf(EnemyProjection(3, EnemyType.ARCHITECT, 200f, 0f, 0f, 0f, 400f, 1000f, 74f, 0f, 0f, 0f, 0f, 200f, 0f, false))
                    .toImmutableList(),
                "pointsOfInterest" to listOf(PointOfInterestProjection(PointOfInterestKind.COLLAPSING_ORBIT, "Collapsing orbit", 0f, 0f,
                    true, 12f, 0, 0.4f, immutableListOf(), 0f, 0f)).toImmutableList(),
            )
            val fx = VisualFxProjection.EMPTY.copy(buildNotifications = immutableListOf(
                BuildNotificationProjection("Neon Ram", immutableListOf("Impact damage +0.05", "Weapon power +0.04"), 4f),
            ))
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f,
                AppLanguage.Russian)
            val memory = HudPresentationMemory()
            // Values that change every frame: speed, draining polarity (its % label shows), falling
            // integrity (hit ghost + split channels), chain and orbit progress. Models are built up
            // front so the measured frames only draw.
            val frames = List(FRAMES) { frame ->
                model.with(
                    "velocityX" to 300f + frame * 7.3f,
                    "polarityStability" to 0.24f - frame * 0.0005f,
                    "hp" to 36f - frame * 0.05f,
                    "combo" to 14 + frame,
                    "pointsOfInterest" to listOf(PointOfInterestProjection(PointOfInterestKind.COLLAPSING_ORBIT, "Collapsing orbit", 0f, 0f,
                        true, 12f, 0, frame / FRAMES.toFloat(), immutableListOf(), 0f, 0f)).toImmutableList(),
                )
            }
            val bitmap = ImageBitmap(width, height)
            val canvas = Canvas(bitmap)
            val scope = CanvasDrawScope()
            val size = Size(width.toFloat(), height.toFloat())
            fun empty() = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) { drawRect(Kk.Ink) }
            // Time stays inside one animation cycle so the frames only move shaders, pulses and alphas.
            fun hud(frame: Int) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) {
                val current = frames[frame % FRAMES]
                drawHud(current, measurer, 10f + frame * 0.0001f, 0f, 0f, memory, trialInfoOpen = true)
                drawHudFeed(current, fx, measurer, 10f + frame * 0.0001f, memory)
                drawTrialTooltip(current, measurer, open = true)
            }
            // Warm every digit, label and path once, then measure frames whose values all differ.
            repeat(FRAMES) { empty(); hud(it) }
            val emptyBytes = allocated { repeat(FRAMES) { empty() } }
            val hudBytes = allocated { repeat(FRAMES) { hud(it) } }
            val perFrame = (hudBytes - emptyBytes) / FRAMES
            assertTrue(perFrame <= 64, "HUD frame at $width x $height allocates $perFrame bytes above an empty frame")
        }
    }

    /**
     * On a landscape phone the chain (bone) and the draining polarity % (threat) are both drawn digit
     * by digit at the same size; with shared digits on screen (×12 while polarity drains from 21 %
     * to 10 %) neither may reshape the other's paragraphs, at the default text size and at 100 %.
     */
    @Test
    fun chainAndDrainingPolarityOnAPhoneShareNoDigitLayouts() {
        listOf(1.25f, 1f).forEach { textScale ->
            val model = hudTestModel(844f, 390f).with(
                "combo" to 12, "comboTime" to 1.4f, "comboWindow" to 2.8f, "pointerX" to 300f, "pointerY" to 190f,
                "polarityStability" to 0.21f,
            )
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), textScale,
                AppLanguage.English)
            val memory = HudPresentationMemory()
            // 21 % down to 10 % every hundred frames (each step a visible drain).
            val frames = List(FRAMES) { frame -> model.with("polarityStability" to 0.215f - (frame % 100) * 0.0011f) }
            val bitmap = ImageBitmap(844, 390)
            val canvas = Canvas(bitmap)
            val scope = CanvasDrawScope()
            val size = Size(844f, 390f)
            fun empty() = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) { drawRect(Kk.Ink) }
            fun hud(frame: Int) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) {
                drawHud(frames[frame % FRAMES], measurer, 10f + frame * 0.0001f, 0f, 0f, memory)
            }
            repeat(FRAMES) { empty(); hud(it) }
            // Both numbers are on screen together.
            kotlin.test.assertNotNull(HudLayoutProbe.rect(HudBlock.POLARITY_LABEL), "the polarity % is not drawn")
            kotlin.test.assertNotNull(HudLayoutProbe.rect(HudBlock.CHAIN), "the chain is not drawn")
            val emptyBytes = allocated { repeat(FRAMES) { empty() } }
            val hudBytes = allocated { repeat(FRAMES) { hud(it) } }
            val perFrame = (hudBytes - emptyBytes) / FRAMES
            assertTrue(perFrame <= 64, "chain + polarity frame at x$textScale allocates $perFrame bytes above an empty frame")
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
        const val FRAMES = 400
    }
}
