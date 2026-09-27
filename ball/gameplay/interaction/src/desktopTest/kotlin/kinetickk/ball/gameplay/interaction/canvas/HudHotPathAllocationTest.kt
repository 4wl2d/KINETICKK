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
            val bitmap = ImageBitmap(width, height)
            val canvas = Canvas(bitmap)
            val scope = CanvasDrawScope()
            val size = Size(width.toFloat(), height.toFloat())
            fun empty() = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) { drawRect(Kk.Ink) }
            // Time stays inside one animation cycle so the frames only move shaders, pulses and alphas.
            fun hud(frame: Int) = scope.draw(Density(1f), LayoutDirection.Ltr, canvas, size) {
                drawHud(model, measurer, 10f + frame * 0.0001f, 0f, 0f, memory, trialInfoFocused = false)
                drawHudFeed(model, fx, measurer, 10f + frame * 0.0001f, memory)
            }
            repeat(200) { empty(); hud(it) }
            val emptyBytes = allocated { repeat(FRAMES) { empty() } }
            val hudBytes = allocated { repeat(FRAMES) { hud(it) } }
            val perFrame = (hudBytes - emptyBytes) / FRAMES
            assertTrue(perFrame <= 64, "HUD frame at $width x $height allocates $perFrame bytes above an empty frame")
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
