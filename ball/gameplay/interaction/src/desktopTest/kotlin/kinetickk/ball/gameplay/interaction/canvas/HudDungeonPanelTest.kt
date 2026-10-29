// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.StructureTemplateId
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.DUNGEON_ASPECT_SALVAGE_MATTER
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.localization.StructureHudText
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.localeList
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The dungeon on the HUD: the trial panel names a held structure with its rooms, rewards and rules, its sealed room's
 * cell pulses, the vignette closes in as deep as the world's scrim, and the world keeps clear of the panel.
 */
class HudDungeonPanelTest {
    private val base = hudTestModel()
    private val templates = base.content.structures

    private fun station(cleared: Int = 0b0011, engaged: Int = 2, dx: Float = 0f, dy: Float = 0f) =
        StructureFixture(templates.template(StructureTemplateId.DERELICT_STATION), base.coreX + dx, base.coreY + dy,
            clearedRooms = cleared, engagedRoom = engaged)

    @Test
    fun aDungeonPanelNamesItselfAStructureWithItsRoomsRewardsAndRules() {
        val fixture = station()
        val model = base.withDungeon(fixture)
        val trial = assertNotNull(model.activeTrial(), "a held dungeon shows its panel")
        assertEquals(SiteGlyph.STRUCTURE, trialGlyph(trial))
        assertSame(StructureHudText.Structure, trialLabel(trial))
        assertEquals("Structure", AppLanguage.English.text(trialLabel(trial)))
        assertEquals("Сооружение", AppLanguage.Russian.text(trialLabel(trial)))
        assertEquals("Derelict Station", trial.title)
        assertEquals("Заброшенная станция", trial.title.localizedContent(AppLanguage.Russian))
        assertEquals("2 / 4", trialProgressText(trial, AppLanguage.Russian))
        // The (!) explains the dungeon in the player's language: UI text, not a content instruction.
        assertTrue(model.trialRules(trial, AppLanguage.English).contains(StructureHudText.Rules.english))
        assertTrue(model.trialRules(trial, AppLanguage.Russian).contains(StructureHudText.Rules.russian))

        // The reward row joins the template's reward labels, once per template and language.
        val vault = StructureFixture(templates.template(StructureTemplateId.VAULT), base.coreX, base.coreY)
        val vaultTrial = assertNotNull(structureTrial(vault))
        assertTrue(structureTrialRewards(model, vaultTrial, AppLanguage.English).contains("Rime Heart"))
        val russian = structureTrialRewards(model, vaultTrial, AppLanguage.Russian)
        assertFalse(Regex("[A-Za-z]").containsMatchIn(russian), russian)
        assertSame(russian, structureTrialRewards(model, vaultTrial, AppLanguage.Russian), "a steady panel builds no string")
        templates.templates.filter { !it.secret }.forEach { template ->
            val held = assertNotNull(structureTrial(StructureFixture(template, base.coreX, base.coreY)))
            AppLanguage.entries.forEach { language ->
                assertEquals(template.rewards.joinToString(" + ") { it.label.localizedContent(language) },
                    structureTrialRewards(model, held, language), "${template.name} in $language")
            }
        }
        // The Doodle is found, not fought: no panel.
        val doodle = StructureFixture(templates.template(StructureTemplateId.ARCHITECTS_DOODLE), base.coreX, base.coreY)
        assertEquals(null, base.withDungeon(doodle).activeTrial())

        // Copy rules: no numeral in the label, Cyrillic Russian, no banned glyph, a label without closing punctuation.
        val banned = listOf("·", "→", "←", "↑", "↓", "›", "‹", "▶", "◀", "◇", "§")
        val latin = Regex("[A-Za-z]")
        StructureHudText.entries.forEach { text ->
            assertTrue(text.english.isNotBlank() && text.russian.isNotBlank() && text.english != text.russian, "$text")
            assertFalse(latin.containsMatchIn(text.russian), "$text: Latin letters in Russian")
            listOf(text.english, text.russian).forEach { value -> assertTrue(banned.none { it in value }, "$text: $value") }
        }
        listOf(StructureHudText.Structure.english, StructureHudText.Structure.russian).forEach { label ->
            assertTrue(label.none { it.isDigit() } && !label.endsWith("."), label)
        }
    }

    /**
     * The one panel shows a running site's trial, then the dungeon, then the site again in the same language: each
     * time its label, name, reward row and open rules are that engagement's own, never the previous one's cached text.
     */
    @Test
    fun thePanelSwitchesBetweenASiteAndADungeonWithoutKeepingTheOthersText() {
        val race = trialSites(base.coreX, base.coreY).first { it.kind == kinetickk.ball.content.api.SiteKind.GATE_RACE }
        val site = base.withSites(race)
        val dungeon = base.withDungeon(station())
        val raceRules = base.content.sites.definition(race.kind).instruction
        AppLanguage.entries.forEach { language ->
            fun shows(model: GameplayRenderModel, label: String, name: String, reward: String, rules: String) {
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(1_440, 810)), Size(1_440f, 810f)) {
                    drawTrialPanel(model, measurer(language), 0f, infoOpen = true)
                    drawTrialTooltip(model, measurer(language), open = true)
                }
                fun line(slot: HudText) = assertNotNull(HudDrawCache.peekLayout(slot)).layoutInput.text.text
                assertEquals(label.uppercase(), line(HudText.TRIAL_LABEL).uppercase(), "$language label")
                assertEquals(name.uppercase(), line(HudText.TRIAL_NAME).uppercase(), "$language name")
                assertEquals(reward.uppercase(), line(HudText.TRIAL_REWARD).uppercase(), "$language reward")
                assertEquals(rules, TrialRulesPlacement.body.layoutInput.text.text, "$language rules")
            }
            val raceLabel = language.text(kinetickk.ball.gameplay.interaction.localization.HudRedesignText.RaceTrial)
            val raceName = race.name.localizedContent(language)
            val raceReward = language.text(trialRewardText(siteTrial(race, base.content.sites)))
            val stationRewards = templates.template(StructureTemplateId.DERELICT_STATION).rewards
                .joinToString(" + ") { it.label.localizedContent(language) }
            repeat(2) {
                shows(site, raceLabel, raceName, raceReward, raceRules.localizedContent(language))
                shows(dungeon, language.text(StructureHudText.Structure), "Derelict Station".localizedContent(language), stationRewards,
                    dungeon.trialRules(assertNotNull(dungeon.activeTrial()), language))
            }
        }
    }

    @Test
    fun switchingDungeonsRefreshesElementalRulesAndExplainsTheAspectInBothLanguages() {
        val templates = base.content.structures.templates.filter { !it.secret }
        for (language in AppLanguage.entries) {
            val renderedRules = mutableSetOf<String>()
            // Repeat the sequence to exercise the shared tooltip cache after every template and language change.
            repeat(2) {
                for (template in templates) {
                    val model = base.withDungeon(StructureFixture(template, base.coreX, base.coreY))
                    val trial = assertNotNull(model.activeTrial())
                    val rules = model.trialRules(trial, language)
                    assertTrue(rules.contains(template.trialDescription.localizedContent(language)), template.name)
                    val aspect = template.rewards.firstNotNullOf { it.boonId?.let(base.content::boon) }
                    assertTrue(rules.contains(aspect.description.localizedContent(language)), "${template.name}: reward effect")
                    if (language == AppLanguage.Russian) assertFalse(Regex("[A-Za-z]").containsMatchIn(rules), rules)
                    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(1_440, 810)), Size(1_440f, 810f)) {
                        drawTrialPanel(model, measurer(language), 0f, infoOpen = true)
                        drawTrialTooltip(model, measurer(language), open = true)
                    }
                    assertEquals(rules, TrialRulesPlacement.body.layoutInput.text.text, "${template.name}: no stale rules")
                    renderedRules += rules
                }
            }
            assertEquals(templates.size, renderedRules.size, "each dungeon explains a different challenge and aspect")
        }
    }

    @Test
    fun elementalRulesAndRewardsFitPortraitAndLandscapePhoneTooltips() {
        for ((width, height) in listOf(390f to 844f, 844f to 390f)) {
            for (language in AppLanguage.entries) for (template in templates.templates.filter { !it.secret }) {
                val model = hudTestModel(width, height).withDungeon(StructureFixture(template, base.coreX, base.coreY))
                val measurer = measurer(language)
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(64, 64)), Size(width, height)) {
                    drawTrialPanel(model, measurer, 0f, infoOpen = true)
                    drawTrialTooltip(model, measurer, open = true)
                }
                val placement = TrialRulesPlacement
                val label = "${template.name} $language $width x $height"
                assertTrue(placement.bottom <= height && placement.right <= width && placement.left >= 0f, label)
                assertFalse(placement.body.hasVisualOverflow, "$label: all rules and reward effects remain readable")
            }
        }
    }

    @Test
    fun ownedFullAndMissingAspectsAdvertiseMatterAndInvalidateTheRewardAndRulesCaches() {
        val available = base.withDungeon(station())
        val trial = assertNotNull(available.activeTrial())
        val aspect = templates.template(StructureTemplateId.DERELICT_STATION).rewards.firstNotNullOf { it.boonId }
        val boon = assertNotNull(base.content.boon(aspect))
        val owned = available.with("runBoons" to immutableListOf(aspect))
        val full = available.with("runBoons" to base.content.boons.filter { it.id != aspect }
            .take(ContentBounds.MAX_RUN_BOONS).map { it.id }.toImmutableList())
        val missing = available.with("content" to base.content.copy(boons = base.content.boons.filter { it.id != aspect }.toImmutableList()))
        for (language in AppLanguage.entries) {
            // Switch both ways: availability can change without changing the dungeon or the language.
            for (fallback in listOf(owned, full, missing)) {
                assertTrue(structureTrialRewards(available, trial, language).contains(boon.name.localizedContent(language)))
                assertTrue(available.trialRules(trial, language).contains(boon.description.localizedContent(language)))
                val rewards = structureTrialRewards(fallback, trial, language)
                assertFalse(rewards.contains(boon.name.localizedContent(language)), rewards)
                assertTrue(rewards.contains("Matter".localizedContent(language)), rewards)
                val rules = fallback.trialRules(trial, language)
                assertFalse(rules.contains(boon.description.localizedContent(language)), rules)
                assertTrue(rules.contains(language.text(StructureHudText.AspectFallback, DUNGEON_ASPECT_SALVAGE_MATTER)), rules)
            }
        }
    }

    /**
     * Two of the station's four rooms are clear and a third is sealed: the first empty cell (the sealed room's) pulses in
     * volt between its dim and bright beats, the cleared cells stay solid and the last cell stays empty; with no room
     * sealed nothing pulses.
     */
    @Test
    fun theSealedRoomsCellPulsesInVoltOnlyWhileARoomIsSealed() {
        val peak = (PI / 12.0).toFloat()
        val trough = (PI / 4.0).toFloat()
        val bright = cells(base.withDungeon(station()).with("elapsed" to peak))
        val dim = cells(base.withDungeon(station()).with("elapsed" to trough))
        val idle = cells(base.withDungeon(station(engaged = -1)).with("elapsed" to peak))
        val volt = KkRolePalette.Default.you
        // Cleared cells: solid volt in every frame.
        listOf(bright, dim, idle).forEach { frame -> (0..1).forEach { assertNear(volt, frame[it], 0.03f, "cleared cell $it") } }
        // The sealed room's cell: brighter toward volt at the beat's peak than at its trough, both above an idle cell.
        assertTrue(distance(bright[2], volt) < distance(dim[2], volt) - 0.05f, "the cell pulses: ${bright[2]} vs ${dim[2]}")
        assertTrue(distance(dim[2], volt) < distance(idle[2], volt) - 0.02f, "even its trough reads: ${dim[2]} vs ${idle[2]}")
        // The last room and an idle panel's cells stay the empty track.
        listOf(bright, dim, idle).forEach { frame -> assertNear(idle[3], frame[3], 0.01f, "the last cell") }
        assertNear(idle[3], idle[2], 0.01f, "nothing pulses with no room sealed")
    }

    /**
     * The vignette is as deep as the scrim: ink at the screen's corners at full depth, half as much at half the fade
     * depth, none outside the footprint or for a secret template, and never on the screen's centre.
     */
    @Test
    fun theVignetteClosesInFromTheCornersAsDeepAsTheScrimAndNeverOnTheCentre() {
        val template = templates.template(StructureTemplateId.DERELICT_STATION)
        val full = base.withDungeon(station())
        // The Core 120 u inside the footprint's east edge: half of the 240 u fade.
        val half = base.withDungeon(station(dx = 120f - template.halfWidth))
        val outside = base.withDungeon(station(dx = -template.halfWidth - 200f))
        val doodle = base.withDungeon(StructureFixture(templates.template(StructureTemplateId.ARCHITECTS_DOODLE), base.coreX, base.coreY))
        assertEquals(1f, structureInteriorAmount(full))
        assertEquals(0.5f, structureInteriorAmount(half), 0.001f)
        assertEquals(0f, structureInteriorAmount(outside))
        assertEquals(0f, structureInteriorAmount(doodle))

        fun corners(model: GameplayRenderModel): Pair<Float, Float> {
            val bitmap = ImageBitmap(400, 300)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(400f, 300f)) {
                drawRect(Kk.Bone)
                drawDungeonVignette(model)
            }
            val pixels = bitmap.toPixelMap()
            return pixels[1, 1].red to pixels[200, 150].red
        }
        val bone = Kk.Bone.red
        val (fullCorner, fullCentre) = corners(full)
        val (halfCorner, halfCentre) = corners(half)
        assertEquals(bone, fullCentre, 0.004f)
        assertEquals(bone, halfCentre, 0.004f)
        assertTrue(fullCorner < bone - 0.3f, "the corner darkens at full depth: $fullCorner")
        // Ink at alpha 0.5 × amount over bone: halving the depth halves the darkening.
        assertEquals((bone - fullCorner) * 0.5f, bone - halfCorner, 0.03f)
        listOf(outside, doodle).forEach { model -> assertEquals(bone, corners(model).first, 0.004f) }
    }

    /** The world keep-out holds the dungeon panel as it holds a site's trial panel, at every reference size. */
    @Test
    fun theWorldKeepOutHoldsTheDungeonPanel() {
        listOf(1_440f to 810f, 844f to 390f, 390f to 844f).forEach { (width, height) ->
            val model = hudTestModel(width, height).withDungeon(station())
            val idle = model.with("trial" to null)
            var held = emptyList<Rect>()
            var without = emptyList<Rect>()
            hud(width, height) { measurer ->
                without = worldHudKeepOut(idle, measurer).rects()
                held = worldHudKeepOut(model, measurer).rects()
                drawHud(model, measurer, 1f)
            }
            val panel = assertNotNull(HudLayoutProbe.rect(HudBlock.TRIAL_PANEL), "$width x $height: no dungeon panel")
            assertTrue(covered(panel, held), "$width x $height: the panel $panel is outside the keep-out $held")
            assertFalse(covered(panel, without), "$width x $height: the keep-out held the panel without a trial")
        }
    }

    /** The colours at the centres of the meter's four cells of a drawn dungeon panel. */
    private fun cells(model: GameplayRenderModel): List<Color> {
        val bitmap = ImageBitmap(1_440, 810)
        var meter = Rect.Zero
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(1_440f, 810f)) {
            drawRect(Kk.Ink)
            drawTrialPanel(model, measurer(AppLanguage.English), 0f, infoOpen = false)
            meter = assertNotNull(HudDrawCache.peekRect(HudRect.TRIAL_METER))
        }
        val pixels = bitmap.toPixelMap()
        val pitch = meter.width / 4f
        // The cells are sheared about the meter's centre line, so their centres sit on it unshifted (3 px gaps).
        return List(4) { index -> pixels[(meter.left + index * pitch + (pitch - 3f) * 0.5f).toInt(), meter.center.y.toInt()] }
    }

    private fun hud(width: Float, height: Float, block: DrawScope.(CanvasTextMeasurer) -> Unit) {
        val measurer = measurer(AppLanguage.Russian)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(64, 64)), Size(width, height)) { block(measurer) }
    }

    private fun measurer(language: AppLanguage) =
        CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f, language,
            HudTestFonts.typography.copy(localeList = language.localeList()))

    /** Whether every point of [rect] lies in one of [rects] (sampled on a 1 px grid, edges included). */
    private fun covered(rect: Rect, rects: List<Rect>): Boolean {
        var y = rect.top
        while (true) {
            var x = rect.left
            while (true) {
                if (rects.none { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }) return false
                if (x >= rect.right) break
                x = minOf(rect.right, x + 1f)
            }
            if (y >= rect.bottom) break
            y = minOf(rect.bottom, y + 1f)
        }
        return true
    }

    private fun distance(a: Color, b: Color): Float = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)

    private fun assertNear(expected: Color, actual: Color, tolerance: Float, what: String) =
        assertTrue(distance(expected, actual) <= tolerance * 3f, "$what: $actual, expected near $expected")
}
