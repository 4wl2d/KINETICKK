// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** SPEC §9: under Mono every hazard meter of the HUD carries the threat hatch. */
class HudColorVisionTest {
    @Test
    fun monoHatchesTheBossBarsAndTheCriticalIntegrityMeter() {
        listOf(1_440 to 810, 844 to 390, 390 to 844).forEach { (w, h) ->
            listOf(EnemyType.ELITE, EnemyType.ARCHITECT).forEach { boss ->
                // Critical integrity (18 of 180) and a boss at 60 %.
                val model = hudTestModel(w.toFloat(), h.toFloat()).with(
                    "hp" to 18f, "maxHp" to 180f,
                    "enemies" to listOf(EnemyProjection(4, boss, 0f, 0f, 0f, 0f, 600f, 1_000f, 60f, 0f, 0f, 0f, 0f, 0f, 0f, false))
                        .toImmutableList(),
                )
                val mono = render(model, w, h, KkRolePalette.Mono)
                val meters = listOf(
                    "boss" to assertNotNull(HudDrawCache.peekRect(if (boss == EnemyType.ARCHITECT) HudRect.BOSS_2 else HudRect.BOSS_0)),
                    "integrity" to assertNotNull(HudDrawCache.peekRect(HudRect.INTEGRITY)),
                )
                val plain = render(model, w, h, KkRolePalette.Mono.copy(hatchThreats = false))
                val default = render(model, w, h, KkRolePalette.Default)
                val defaultPlain = render(model, w, h, KkRolePalette.Default.copy(hatchThreats = false))
                meters.forEach { (name, rect) ->
                    val where = "$w x $h $boss $name $rect"
                    // Hatch marks: filled pixels that differ from the same meter drawn without the hatch.
                    val hatched = differing(mono, plain, filledPart(rect))
                    assertTrue(hatched >= 6, "$where: Mono draws no hatch in the meter ($hatched px)")
                    // The other palettes draw no hatch.
                    assertEquals(0, differing(default, defaultPlain, filledPart(rect)), "$where: hatch outside Mono")
                }
            }
        }
    }

    /** The left, filled part of a meter (the boss at 60 %, integrity at 10 %), inside its lean. */
    private fun filledPart(rect: Rect): Rect {
        val inset = rect.height * 0.3f
        return Rect(rect.left + rect.height, rect.top + inset, rect.left + rect.width * 0.08f + rect.height, rect.bottom - inset)
    }

    private fun differing(a: PixelMap, b: PixelMap, rect: Rect): Int {
        var count = 0
        for (y in rect.top.toInt() until rect.bottom.toInt()) for (x in rect.left.toInt() until rect.right.toInt()) {
            val p = a[x, y]
            val q = b[x, y]
            if (abs(p.red - q.red) + abs(p.green - q.green) + abs(p.blue - q.blue) > 0.3f) count++
        }
        return count
    }

    private fun render(model: kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel, width: Int, height: Int, roles: KkRolePalette): PixelMap {
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1.25f,
            AppLanguage.English, HudTestFonts.typography, roles)
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawRect(Kk.Ink)
            // Render time 0.12 s: the critical heartbeat is near its peak, so the integrity fill is strong.
            drawHud(model, measurer, 0.12f)
        }
        return bitmap.toPixelMap()
    }
}
