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
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.render.ApexProjection
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
                    "enemies" to listOf(EnemyProjection(4, boss, 0f, 0f, 0f, 0f, 600f, 1_000f, 60f, 0f, 0f, 0f, 0f, 0f, 0f, false,
                        affinityBits = bossAffinity(boss)))
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

    /**
     * The Architect's bar shows its affinity without new cells: the active third's track takes the
     * aspect's hue and stripes while it shifts, and its weakness glyph fills the name row's icon slot.
     */
    @Test
    fun theArchitectsActiveThirdCarriesItsAspectAndShiftAndItsNameRowTheWeakness() {
        val (w, h) = 1_440 to 810
        val roles = KkRolePalette.Default
        fun draw(affinity: Int): PixelMap = render(hudTestModel(w.toFloat(), h.toFloat()).with(
            "enemies" to listOf(EnemyProjection(4, EnemyType.ARCHITECT, 0f, 0f, 0f, 0f, 550f, 1_000f, 74f, 0f, 0f, 0f, 0f, 0f, 0f, false,
                affinityBits = affinity)).toImmutableList(),
        ), w, h, roles)
        val neutral = draw(runAffinity(EnemyType.ARCHITECT, architectPhase = 1))
        val aspected = draw(runAffinity(EnemyType.ARCHITECT, architectAspect = StatusElement.THERMAL, architectPhase = 1))
        val shifting = draw(runAffinity(EnemyType.ARCHITECT, architectAspect = StatusElement.THERMAL, architectPhase = 1, shifting = true))
        val active = assertNotNull(HudDrawCache.peekRect(HudRect.BOSS_1))
        val drained = assertNotNull(HudDrawCache.peekRect(HudRect.BOSS_0))
        // At 55 % the middle third is 65 % full: its empty end shows the track.
        val inset = active.height * 0.3f
        val track = Rect(active.right - active.width * 0.25f, active.top + inset, active.right - active.height, active.bottom - inset)
        // Faint tracks (threat at 18 %, Thermal at 22 %) differ by less than a hatch mark.
        assertTrue(differing(neutral, aspected, track, threshold = 0.1f) > 20, "the aspect tints the active track")
        assertEquals(0, differing(neutral, aspected, filledPart(drained), threshold = 0.1f), "a drained third keeps the threat track")
        assertTrue(differing(aspected, shifting, filledPart(active)) >= 6, "the shift stripes the active third")
        // The weakness (Cryo, against Thermal) in the name row above the bar; none without an aspect.
        val row = Rect(active.left - active.width, active.top - active.height * 6f, active.right + active.width, active.top)
        val cryo = roles.element(StatusElement.CRYO.ordinal)
        assertEquals(0, near(neutral, row, cryo))
        assertTrue(near(aspected, row, cryo) > 10, "the weakness glyph in the name row")
    }

    /**
     * The level cluster at the cap (every Apex state) and at 41 and 34: no pixel of the preset's threat
     * in the badge, its echoes, rays and glow, the Data bar or the Data line, in every preset with a
     * threat hue of its own; and under Mono the APEX still looks unlike the T4 badge it shares a dark
     * face with.
     */
    @Test
    fun theLevelClusterNeverShowsThreatAndTheApexStaysDistinctInMono() {
        val cooling = ApexProjection(unlocked = true, cooldown01 = 0.4f)
        val ready = ApexProjection(unlocked = true, ready = true)
        val active = ApexProjection(unlocked = true, active01 = 0.6f)
        listOf(1_440 to 810, 844 to 390, 390 to 844).forEach { (w, h) ->
            fun model(level: Int, apex: ApexProjection = ApexProjection.LOCKED) =
                hudTestModel(w.toFloat(), h.toFloat()).with(*levelOverrides(level, apex), "data" to 30, "nextLevelData" to 100)
            val models = listOf(model(HUD_TEST_LEVEL_CAP, cooling), model(HUD_TEST_LEVEL_CAP, ready), model(HUD_TEST_LEVEL_CAP, active),
                model(41), model(34))
            listOf(KkRolePalette.Default, KkRolePalette.Protan, KkRolePalette.Deutan, KkRolePalette.Tritan).forEach { roles ->
                models.forEachIndexed { index, model ->
                    val map = render(model, w, h, roles)
                    val cluster = clusterArea(w, h)
                    assertEquals(0, near(map, cluster, roles.threat), "$w x $h model $index: threat pixels in the level cluster")
                }
            }
            val t4 = render(model(34), w, h, KkRolePalette.Mono)
            val apex = render(model(HUD_TEST_LEVEL_CAP, ready), w, h, KkRolePalette.Mono)
            val distinct = differing(t4, apex, clusterArea(w, h))
            assertTrue(distinct >= 300, "$w x $h: the Mono APEX differs from T4 in only $distinct px")
        }
    }

    /** The badge (as last drawn) grown by its echoes, glow and inner rays, and the Data line row. */
    private fun clusterArea(w: Int, h: Int): Rect {
        val badge = assertNotNull(HudLayoutProbe.rect(HudBlock.BADGE))
        return Rect(0f, 0f, kotlin.math.min(w.toFloat(), badge.right + 40f), kotlin.math.min(h.toFloat(), badge.bottom + 30f))
    }

    private fun near(map: PixelMap, rect: Rect, color: androidx.compose.ui.graphics.Color): Int {
        var count = 0
        for (y in rect.top.toInt().coerceAtLeast(0) until rect.bottom.toInt()) for (x in rect.left.toInt().coerceAtLeast(0) until rect.right.toInt()) {
            val p = map[x, y]
            if (abs(p.red - color.red) + abs(p.green - color.green) + abs(p.blue - color.blue) < 0.12f) count++
        }
        return count
    }

    /** The left, filled part of a meter (the boss at 60 %, integrity at 10 %), inside its lean. */
    private fun filledPart(rect: Rect): Rect {
        val inset = rect.height * 0.3f
        return Rect(rect.left + rect.height, rect.top + inset, rect.left + rect.width * 0.08f + rect.height, rect.bottom - inset)
    }

    private fun differing(a: PixelMap, b: PixelMap, rect: Rect, threshold: Float = 0.3f): Int {
        var count = 0
        for (y in rect.top.toInt() until rect.bottom.toInt()) for (x in rect.left.toInt() until rect.right.toInt()) {
            val p = a[x, y]
            val q = b[x, y]
            if (abs(p.red - q.red) + abs(p.green - q.green) + abs(p.blue - q.blue) > threshold) count++
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
