// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.DimensionObjective
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.canvas.hud.*
import kinetickk.ball.gameplay.interaction.canvas.world.*
import kinetickk.ball.gameplay.interaction.canvas.world.places.*
import kinetickk.ball.gameplay.interaction.layout.*
import kinetickk.ball.gameplay.interaction.localization.DimensionHudText
import kinetickk.ball.gameplay.nucleus.render.DimensionProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.TrialReward
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.localeList
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A dimension visit on the HUD: the plate in the Rebirth strip's slot (sigil, timer, remaining meter, objective pips and
 * the layer's gauge), the portal of a layer change, the Nadir's heat edge, and the objective on the trial panel.
 */
class HudDimensionPlateTest {
    private val sizes = listOf(1_440 to 810, 844 to 390, 390 to 844)

    private fun visit(layer: WorldLayer, model: GameplayRenderModel, remaining: Float = 52f, progress: Int = 3, scorching: Float = 0f) =
        dimensionVisit(layer, model.coreX, model.coreY, progress = progress, scorching = scorching).copy(remaining = remaining)

    /**
     * In every layout, language and text size, at tier 10 in a Baby mode run with a boss, a feed message and the
     * objective's panel on screen, the plate takes the whole slot (no tier plate), shows the timer, stays on screen and
     * clear of the clock, the badge, the chips, the chain, the boss block, the trial panel and the feed, and lies in the
     * world's keep-out. A counted objective's pips make the plate wider than a boss objective's; realspace draws none.
     */
    @Test
    fun thePlateTakesTheRebirthSlotClearOfTheHeaderInEveryLayout() {
        for (language in AppLanguage.entries) sizes.forEach { (w, h) ->
            for (textScale in listOf(1f, 1.25f, 1.75f)) {
                val base = hudTestModel(w.toFloat(), h.toFloat()).with(
                    "easyMode" to true, "rebirthLevel" to 10,
                    "keys" to 12, "runMatter" to 9_876_543L, "combo" to 128, "comboTime" to 1f, "comboWindow" to 2.8f, *levelOverrides(41),
                    "message" to "ELITE SIGNAL", "messageTime" to 1.5f,
                    "enemies" to listOf(EnemyProjection(4, EnemyType.ELITE, 0f, 0f, 0f, 0f, 500f, 1_000f, 60f, 0f, 0f, 0f, 0f, 0f, 0f, false,
                        affinityBits = bossAffinity(EnemyType.ELITE))).toImmutableList(),
                )
                val widths = HashMap<WorldLayer, Float>()
                val duels = HashMap<WorldLayer, Float>()
                for (layer in listOf(WorldLayer.ZENITH, WorldLayer.NADIR, WorldLayer.ARCHIVE)) for (duel in listOf(false, true)) {
                    if (duel && layer == WorldLayer.ARCHIVE) continue
                    val counted = visit(layer, base)
                    // The same layer's boss objective: the same gauge, no pips.
                    val shown = if (!duel) counted else counted.copy(objective = DimensionObjective.entries.first { it.layer == layer && it.boss != null }, goal = 1)
                    val model = base.inVisit(shown)
                    HudDrawCache.forgetLayouts()
                    draw(w, h, language, textScale) { measurer -> drawHud(model, measurer, 1f) }
                    val where = "$language $w x $h x$textScale $layer"
                    val plate = assertNotNull(HudLayoutProbe.rect(HudBlock.REBIRTH), "$where: no plate")
                    assertNull(HudDrawCache.peekLayout(HudText.REBIRTH_TIER), "$where: the tier plate yields the slot to the visit")
                    assertEquals("0:52", assertNotNull(HudDrawCache.peekLayout(HudText.DIMENSION_TIMER)).layoutInput.text.text)
                    assertTrue(plate.width > plate.height * 1.5f, "$where: the timer follows the sigil ($plate)")
                    assertTrue(plate.left >= 0f && plate.top >= 0f && plate.right <= w && plate.bottom <= h, "$where: plate $plate off screen")
                    listOf(HudBlock.CLOCK, HudBlock.BADGE, HudBlock.MATTER_CHIP, HudBlock.KEY_CHIP, HudBlock.CHAIN, HudBlock.BOSS,
                        HudBlock.TRIAL_PANEL, HudBlock.FEED).forEach { block ->
                        val other = HudLayoutProbe.rect(block) ?: return@forEach
                        assertFalse(plate.overlaps(other), "$where: the plate $plate meets $block $other")
                    }
                    val keepOut = WorldHudKeepOut().update(w.toFloat(), h.toFloat(), 1f, textScale, trialActive = true, bossPresent = true)
                    assertTrue(inside(plate, keepOut.rects()), "$where: the plate $plate is outside the keep-out")
                    if (duel) duels[layer] = plate.width else widths[layer] = plate.width
                    // A counted objective shows its pips (where the slot has room), a duel never does; the gauge follows them
                    // where it fits, always clear of them.
                    val pips = HudLayoutProbe.rect(HudBlock.DIMENSION_PIPS)
                    if (duel || layer == WorldLayer.ARCHIVE) assertNull(pips, "$where: a duel shows no pips")
                    else if (w != 844 || textScale < 1.75f) assertNotNull(pips, "$where: the counted objective shows its pips")
                    val gauge = HudLayoutProbe.rect(HudBlock.DIMENSION_GAUGE)
                    if (pips != null && gauge != null) assertTrue(gauge.left >= pips.right, "$where: the gauge $gauge follows the pips $pips")
                    if (gauge != null) assertTrue(gauge.right <= plate.right + 0.5f, "$where: the gauge stays in the plate")
                }
                val where = "$language $w x $h x$textScale"
                assertTrue(widths.values.all { it > 0f } && duels.values.all { it > 0f }, "$where: every plate drawn ($widths, $duels)")
            }
        }
        // Realspace: the strip is the tier plate's again.
        val tier = hudTestModel().with("rebirthLevel" to 3)
        HudDrawCache.forgetLayouts()
        draw(1_440, 810, AppLanguage.English, 1f) { measurer -> drawHud(tier, measurer, 1f) }
        assertNotNull(HudDrawCache.peekLayout(HudText.REBIRTH_TIER), "realspace shows the tier plate")
        assertNull(HudDrawCache.peekLayout(HudText.DIMENSION_TIMER))
    }

    /**
     * The plate's sigil ring and meter are the layer's accent under every colour-vision preset (the Zenith's shield, the
     * Nadir's heat, the Archive's void), the timer bone; over the last ten seconds the timer pulses between 0.55 and 1,
     * never turning threat.
     */
    @Test
    fun thePlateWearsTheLayersAccentAndTheTimerPulsesOverItsLastTenSeconds() {
        for (roles in listOf(KkRolePalette.Default, KkRolePalette.Protan, KkRolePalette.Mono)) {
            for (layer in listOf(WorldLayer.ZENITH, WorldLayer.NADIR, WorldLayer.ARCHIVE)) {
                val base = hudTestModel()
                val model = base.inVisit(visit(layer, base))
                val (pixels, plate) = plate(model, roles)
                val accent = riftAccent(layer, roles)
                assertTrue(count(pixels, plate) { near(it, accent, 0.06f) } > 30, "$layer under $roles: the accent shows")
                assertTrue(count(pixels, plate) { near(it, Kk.Bone, 0.06f) } > 10, "$layer under $roles: the bone timer")
            }
        }
        assertEquals(1f, dimensionTimerAlpha(10.5f, 0.5f))
        val beat = (0..100).map { dimensionTimerAlpha(6f, it / 100f) }
        assertEquals(0.55f, beat.min(), 0.01f)
        assertEquals(1f, beat.max(), 0.01f)
        // At the pulse's trough the timer is dimmer than at its peak, still bone, never threat.
        val base = hudTestModel()
        val calm = base.inVisit(visit(WorldLayer.NADIR, base, remaining = 6f)).with("elapsed" to 200f)
        val dim = calm.with("elapsed" to 200.5f)
        val roles = KkRolePalette.Default
        val (bright, area) = plate(calm, roles)
        val (faded, _) = plate(dim, roles)
        val brightBone = count(bright, area) { near(it, Kk.Bone, 0.06f) }
        assertTrue(brightBone > 10 && count(faded, area) { near(it, Kk.Bone, 0.06f) } < brightBone / 2, "the timer fades at the trough")
        assertEquals(0, count(faded, area) { near(it, roles.threat, 0.06f) }, "the countdown never turns threat")
    }

    /**
     * The layer change as a portal, once per layer edge for its 900 ms and never on a first frame: the old layer implodes
     * (ink closing on the Core behind a rim in the old layer's accent: realspace's bone), the iris shuts, then the new
     * layer explodes out of the Core (the iris reopening behind a rim in the Zenith's gold), and nothing is left at the end.
     */
    @Test
    fun thePortalPlaysOncePerLayerEdgeForItsDurationAndNeverOnAFirstFrame() {
        val roles = KkRolePalette.Default
        val realspace = hudTestModel().with("elapsed" to 200f)
        val zenith = realspace.inVisit(visit(WorldLayer.ZENITH, realspace)).with("elapsed" to 200.1f)
        val memory = HudPresentationMemory()
        memory.observe(realspace, 1f, BossTarget().select(realspace))
        assertTrue(memory.dimensionTransition.progress(1f) < 0f, "nothing on a quiet first frame")
        memory.observe(zenith, 2f, BossTarget().select(zenith))
        memory.observe(zenith, 2f, BossTarget().select(zenith))
        assertEquals(WorldLayer.BASE, memory.dimensionTransition.from, "the portal remembers the layer it left")
        assertEquals(0f, memory.dimensionTransition.progress(2f))
        assertEquals(0.5f, memory.dimensionTransition.progress(2.45f), 1e-4f)
        assertTrue(memory.dimensionTransition.progress(2.9f) >= 1f)
        val world = Color(0xFF2050A0)
        sizes.forEach { (w, h) ->
            val core = Offset(w * 0.5f, h * 0.5f)
            val corners = listOf(2 to 2, w - 3 to 2, 2 to h - 3, w - 3 to h - 3)
            // Half way into the implosion the corners are ink while the Core still sees the world.
            val closing = portal(zenith, memory, roles, w, h, 2.2f, world)
            corners.forEach { (x, y) -> assertTrue(near(closing[x, y], Kk.Ink, 0.02f), "$w x $h: ink over ($x, $y) as the old layer implodes") }
            assertTrue(near(closing[core.x.toInt(), core.y.toInt()], world, 0.02f), "$w x $h: the Core still sees through the closing iris")
            // Shut: ink over everything.
            val shut = portal(zenith, memory, roles, w, h, 2f + 0.9f * 0.38f, world)
            listOf(core.x.toInt() to core.y.toInt(), 2 to 2, w - 3 to h - 3).forEach { (x, y) ->
                assertTrue(near(shut[x, y], Kk.Ink, 0.06f) || near(shut[x, y], Kk.Bone, 0.3f), "$w x $h: the iris is shut at ($x, $y)")
            }
            // Half way out of it the new layer shows round the Core inside a gold rim; the corners still wait in ink.
            val opening = portal(zenith, memory, roles, w, h, 2.38f, world)
            corners.forEach { (x, y) -> assertTrue(near(opening[x, y], Kk.Ink, 0.02f), "$w x $h: ink over ($x, $y) before the iris opens") }
            assertTrue(count(opening, Rect(0f, 0f, w.toFloat(), h.toFloat())) { it.red > 0.7f && it.green in 0.45f..0.9f && it.blue < 0.4f } > h,
                "$w x $h: the opening rim burns in the Zenith's gold")
            val end = portal(zenith, memory, roles, w, h, 2.9f, world)
            assertEquals(0, count(end, Rect(0f, 0f, w.toFloat(), h.toFloat())) { !near(it, world, 0.02f) }, "$w x $h: done at 900 ms")
        }
        // A host's first frame inside a dimension, and a new run's first frame after a visit, only record.
        val fresh = HudPresentationMemory()
        fresh.observe(zenith, 5f, BossTarget().select(zenith))
        assertTrue(fresh.dimensionTransition.progress(5f) < 0f, "a host's first frame plays nothing")
        val restarted = realspace.with("elapsed" to 1f)
        fresh.observe(restarted, 6f, BossTarget().select(restarted))
        assertTrue(fresh.dimensionTransition.progress(6f) < 0f, "a new run's first frame plays nothing")
        // A new run drops a portal the last one was playing.
        val playing = HudPresentationMemory()
        playing.observe(realspace, 1f, BossTarget().select(realspace))
        playing.observe(zenith, 2f, BossTarget().select(zenith))
        playing.observe(restarted, 2.1f, BossTarget().select(restarted))
        assertTrue(playing.dimensionTransition.progress(2.1f) < 0f, "the new run stops the portal")
    }

    /**
     * The Nadir's heat edge: nothing at pace, the screen's corners warmed in heat as strongly as the warning (half as much
     * at half the grace), the centre untouched, and a pulse once the scorch ticks.
     */
    @Test
    fun theHeatEdgeFollowsTheWarningAndPulsesWhileTheCoreScorches() {
        val roles = KkRolePalette.Default
        val base = hudTestModel()
        fun corners(scorching: Float, time: Float = 0f): Pair<Float, Float> {
            val model = base.with("dimension" to visit(WorldLayer.NADIR, base, scorching = scorching))
            val pixels = canvas(400, 300) {
                drawRect(Kk.Ink)
                drawScorchEdge(model, roles, time)
            }
            return shift(pixels[1, 1], roles.heat) to shift(pixels[200, 150], roles.heat)
        }
        assertEquals(0f, scorchEdgeAlpha(0f, 0f))
        assertEquals(0.15f, scorchEdgeAlpha(0.5f, 0f), 1e-6f)
        val (none, _) = corners(0f)
        val (half, halfCentre) = corners(0.5f)
        val (full, fullCentre) = corners(0.999f)
        assertEquals(0f, none, 0.004f)
        assertTrue(full > 0.15f, "the corner warms toward heat: $full")
        assertEquals(full * 0.5f, half, 0.03f)
        assertEquals(0f, halfCentre, 0.004f)
        assertEquals(0f, fullCentre, 0.004f)
        // Scorching: 1.2 Hz between 0.55 and 1 of the full edge.
        val pulse = (0..40).map { scorchEdgeAlpha(1f, it / 48f) }
        assertEquals(0.30f, pulse.max(), 0.005f)
        assertEquals(0.30f * 0.55f, pulse.min(), 0.005f)
    }

    /**
     * During a visit the trial panel names the objective under the Objective label with the layer's sigil, counts a
     * course or a harvest, names the layer's reward and keeps the instruction behind the (!) in both languages; it shows
     * no clock (the plate carries the timer).
     */
    @Test
    fun theTrialPanelNamesTheObjectiveWithTheLayersSigilCountAndReward() {
        val base = hudTestModel()
        val rewards = mapOf(WorldLayer.ZENITH to TrialReward.BLESSING, WorldLayer.NADIR to TrialReward.PACT, WorldLayer.ARCHIVE to TrialReward.TROVE)
        val latin = Regex("[A-Za-z]")
        for (language in AppLanguage.entries) {
            val icons = HashMap<WorldLayer, List<Int>>()
            for (layer in listOf(WorldLayer.ZENITH, WorldLayer.NADIR, WorldLayer.ARCHIVE)) {
                val dimension = visit(layer, base)
                val model = base.inVisit(dimension)
                val trial = assertNotNull(model.activeTrial())
                val objective = assertNotNull(dimension.objective)
                assertSame(DimensionHudText.Objective, trialLabel(trial))
                assertEquals(objective.instruction.localizedContent(language), model.trialRules(trial, language))
                assertEquals(rewards[layer], trial.reward)
                val bitmap = ImageBitmap(1_440, 810)
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(1_440f, 810f)) {
                    drawRect(Kk.Ink)
                    HudLayoutProbe.begin()
                    drawTrialPanel(model, measurer(language), 0f, infoOpen = false)
                }
                fun line(slot: HudText) = assertNotNull(HudDrawCache.peekLayout(slot)).layoutInput.text.text.uppercase()
                assertEquals(language.text(DimensionHudText.Objective).uppercase(), line(HudText.TRIAL_LABEL))
                assertEquals(objective.label.localizedContent(language).uppercase(), line(HudText.TRIAL_NAME))
                assertEquals(language.text(dimensionTrialRewardText(trial.reward)).uppercase(), line(HudText.TRIAL_REWARD))
                assertNull(HudLayoutProbe.rect(HudBlock.TRIAL_CLOCK), "$layer: no clock row")
                if (objective == DimensionObjective.ANTECEDENT) {
                    assertNull(HudLayoutProbe.rect(HudBlock.TRIAL_PROGRESS), "a boss objective shows no count")
                } else {
                    assertEquals("3 / 10", trialProgressText(trial, language))
                }
                if (language == AppLanguage.Russian) {
                    listOf(line(HudText.TRIAL_LABEL), line(HudText.TRIAL_NAME), line(HudText.TRIAL_REWARD), model.trialRules(trial, language))
                        .forEach { assertFalse(latin.containsMatchIn(it), "$layer: Latin in Russian: $it") }
                }
                // The first row's mark: the icon square left of the label.
                val panel = assertNotNull(HudLayoutProbe.rect(HudBlock.TRIAL_LABEL))
                val pixels = bitmap.toPixelMap()
                val top = (panel.center.y - 9f).toInt()
                val left = (panel.left - 8f - 16f - 2f).toInt().coerceAtLeast(0)
                icons[layer] = (0 until 20).flatMap { dy -> (0 until 20).map { dx -> if (near(pixels[left + dx, top + dy], Kk.Ink2, 0.08f)) 0 else 1 } }
            }
            // Each layer's own sigil, not one shared glyph.
            assertTrue(icons[WorldLayer.ZENITH] != icons[WorldLayer.NADIR] && icons[WorldLayer.NADIR] != icons[WorldLayer.ARCHIVE],
                "$language: each objective carries its layer's sigil")
        }
        // Every dimension reward and the label read in both languages, distinct and without Latin in Russian.
        listOf(TrialReward.BLESSING, TrialReward.PACT, TrialReward.TROVE).map(::dimensionTrialRewardText).forEach { text ->
            assertTrue(text.english != text.russian && !latin.containsMatchIn(text.russian), "$text")
        }
        DimensionHudText.entries.forEach { text ->
            assertTrue(text.english.isNotBlank() && text.russian.isNotBlank() && text.english != text.russian, "$text")
            assertFalse(latin.containsMatchIn(text.russian), "$text: Latin letters in Russian")
            listOf("·", "→", "←", "›", "‹", "▶", "◀", "§").forEach { banned -> assertFalse(banned in text.english || banned in text.russian, "$text") }
        }
    }

    /** The world keep-out holds the objective's panel and the plate as it holds a site's trial. */
    @Test
    fun theWorldKeepOutHoldsTheObjectivesPanel() {
        sizes.forEach { (w, h) ->
            val base = hudTestModel(w.toFloat(), h.toFloat())
            val model = base.inVisit(visit(WorldLayer.ZENITH, base))
            val measurer = measurer(AppLanguage.Russian)
            var held = emptyList<Rect>()
            var without = emptyList<Rect>()
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(64, 64)), Size(w.toFloat(), h.toFloat())) {
                without = worldHudKeepOut(model.with("trial" to null), measurer).rects()
                held = worldHudKeepOut(model, measurer).rects()
                drawHud(model, measurer, 1f)
            }
            val panel = assertNotNull(HudLayoutProbe.rect(HudBlock.TRIAL_PANEL), "$w x $h: no objective panel")
            assertTrue(inside(panel, held), "$w x $h: the panel $panel is outside $held")
            assertFalse(inside(panel, without), "$w x $h: held without a trial")
        }
    }

    private fun plate(model: GameplayRenderModel, roles: KkRolePalette): Pair<PixelMap, Rect> {
        var area = Rect.Zero
        val pixels = canvas(1_440, 810) {
            drawRect(Kk.Ink)
            val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f,
                AppLanguage.English, HudTestFonts.typography, roles)
            HudLayoutProbe.begin()
            drawHud(model, measurer, 1f)
            area = assertNotNull(HudLayoutProbe.rect(HudBlock.REBIRTH))
        }
        return pixels to area
    }

    private fun portal(model: GameplayRenderModel, memory: HudPresentationMemory, roles: KkRolePalette, w: Int, h: Int, time: Float, world: Color): PixelMap =
        canvas(w, h) {
            drawRect(world)
            drawDimensionTransition(model.with("screenWidth" to w.toFloat(), "screenHeight" to h.toFloat(), "cameraX" to model.coreX,
                "cameraY" to model.coreY, "previousCameraX" to model.coreX, "previousCameraY" to model.coreY), roles, time, memory)
        }

    private fun canvas(w: Int, h: Int, block: DrawScope.() -> Unit): PixelMap {
        val bitmap = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(w.toFloat(), h.toFloat()), block)
        return bitmap.toPixelMap()
    }

    private fun count(pixels: PixelMap, area: Rect, predicate: (Color) -> Boolean): Int {
        var found = 0
        for (y in area.top.toInt().coerceAtLeast(0) until area.bottom.toInt().coerceAtMost(pixels.height)) {
            for (x in area.left.toInt().coerceAtLeast(0) until area.right.toInt().coerceAtMost(pixels.width)) if (predicate(pixels[x, y])) found++
        }
        return found
    }

    private fun near(a: Color, b: Color, tolerance: Float) =
        abs(a.red - b.red) <= tolerance && abs(a.green - b.green) <= tolerance && abs(a.blue - b.blue) <= tolerance

    /** How far [pixel] moved from ink toward [toward] (0 = ink, 1 = the colour itself), on the channel that differs most. */
    private fun shift(pixel: Color, toward: Color): Float {
        val ink = Kk.Ink
        val channels = listOf(
            Triple(ink.red, toward.red, pixel.red), Triple(ink.green, toward.green, pixel.green), Triple(ink.blue, toward.blue, pixel.blue),
        )
        val (from, to, value) = channels.maxBy { abs(it.second - it.first) }
        return (value - from) / (to - from)
    }

    /** Whether [rect] lies inside one of [rects] (edges included). */
    private fun inside(rect: Rect, rects: List<Rect>): Boolean = rects.any {
        rect.left >= it.left && rect.top >= it.top && rect.right <= it.right && rect.bottom <= it.bottom
    }

    private fun draw(width: Int, height: Int, language: AppLanguage, textScale: Float, block: DrawScope.(CanvasTextMeasurer) -> Unit) {
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), textScale, language,
            HudTestFonts.typography.copy(localeList = language.localeList()))
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(64, 64)), Size(width.toFloat(), height.toFloat())) {
            block(measurer)
        }
    }

    private fun measurer(language: AppLanguage) =
        CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f, language,
            HudTestFonts.typography.copy(localeList = language.localeList()))
}
