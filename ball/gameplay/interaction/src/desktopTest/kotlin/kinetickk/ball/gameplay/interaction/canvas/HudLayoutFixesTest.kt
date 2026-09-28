// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.gameplay.interaction.fx.BuildNotificationProjection
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_BOSS_ROW_DP
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_BOSS_ROW_HEIGHT_DP
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_HUD_TOP_DP
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.interaction.layout.runningBuildButtonBounds
import kinetickk.ball.gameplay.interaction.layout.runningControlBounds
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.PointOfInterestProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.InterfaceTypography
import kinetickk.foundation.design.labelStyle
import kinetickk.foundation.design.localeList
import kinetickk.foundation.design.measureKkText
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Layout findings of the review round, drawn with the real fonts in both languages. */
class HudLayoutFixesTest {
    private val sizes = listOf(1_440 to 810, 844 to 390, 390 to 844)

    /** The text-size setting's minimum, the game's default and the maximum. */
    private val textScales = listOf(1f, 1.25f, 1.75f)

    @Test
    fun portraitBossRowNeverMeetsTheChipsClockOrChain() {
        for (language in AppLanguage.entries) for (boss in listOf(EnemyType.ELITE, EnemyType.ARCHITECT)) for (textScale in textScales) {
            val model = hudTestModel(390f, 844f).with(
                "keys" to 2, "runMatter" to 9_876_543L, "combo" to 128, "comboTime" to 1f, "comboWindow" to 2.8f,
                "enemies" to listOf(EnemyProjection(4, boss, 0f, 0f, 0f, 0f, 500f, 1_000f, 60f, 0f, 0f, 0f, 0f, 0f, 0f, false))
                    .toImmutableList(),
            )
            draw(390, 844, language, textScale) { measurer -> drawHud(model, measurer, 1f) }
            val bossRect = assertNotNull(HudLayoutProbe.rect(HudBlock.BOSS))
            listOf(HudBlock.MATTER_CHIP, HudBlock.KEY_CHIP, HudBlock.CLOCK, HudBlock.CHAIN, HudBlock.BADGE).forEach { block ->
                val other = assertNotNull(HudLayoutProbe.rect(block), "$block not drawn")
                assertFalse(bossRect.overlaps(other), "$language $boss x$textScale: boss $bossRect meets $block $other")
            }
            assertTrue(bossRect.left >= 0f && bossRect.right <= 390f)
            // The boss block stays inside its reserved row, and the trial panel starts below it.
            assertTrue(bossRect.bottom <= (PORTRAIT_HUD_TOP_DP + PORTRAIT_BOSS_ROW_DP + PORTRAIT_BOSS_ROW_HEIGHT_DP) + 0.5f,
                "$language $boss x$textScale: boss $bossRect leaves its row")
            assertTrue(HudTrialPanelLayout().update(390f, 844f, 1f, textScale).top >= bossRect.bottom)
        }
    }

    /**
     * Every HUD block at every text size, in both languages and the three layouts: blocks that share
     * a region never meet (clock, badge, chips, chain, boss, trial panel, feed, controls; the bottom
     * clusters), and no HUD line is cut with an ellipsis.
     */
    @Test
    fun hudBlocksStayApartAndUncutAtEveryTextSize() {
        val orbit = PointOfInterestProjection(PointOfInterestKind.COLLAPSING_ORBIT, "Collapsing orbit", 0f, 0f, true, 12f, 0, 0.4f,
            immutableListOf(), 0f, 0f)
        val fx = VisualFxProjection.EMPTY.copy(buildNotifications = immutableListOf(
            BuildNotificationProjection("Neon Ram", immutableListOf("Impact damage +0.05", "Weapon power +0.04"), 5f),
        ))
        val hudSlots = HudText.entries.filter { !it.name.startsWith("NOTICE_") && !it.name.startsWith("MESSAGE_") }
        for (language in AppLanguage.entries) sizes.forEach { (w, h) ->
            val width = w.toFloat()
            val height = h.toFloat()
            val controls = runningControlBounds(width, height, 1f).map { it.target.name to it.bounds } +
                ("BUILD" to runningBuildButtonBounds(width, height, 1f))
            for (textScale in textScales) for (boss in listOf(EnemyType.ELITE, EnemyType.ARCHITECT)) for (trial in listOf(false, true)) {
                val model = hudTestModel(width, height).with(
                    "keys" to 12, "runMatter" to 9_876_543L, "combo" to 128, "comboTime" to 1f, "comboWindow" to 2.8f,
                    "level" to 45, "weaponLevel" to 12, "hp" to 1_234f, "maxHp" to 1_500f, "velocityX" to 2_600f,
                    "message" to "ELITE SIGNAL", "messageTime" to 1.5f,
                    "equippedRelics" to listOf(EquippedRelic(RelicId.KINETIC_FLYWHEEL, 1), EquippedRelic(RelicId.GHOST_VECTOR, 1))
                        .toImmutableList(),
                    "enemies" to listOf(EnemyProjection(4, boss, 0f, 0f, 0f, 0f, 500f, 1_000f, 60f, 0f, 0f, 0f, 0f, 0f, 0f, false))
                        .toImmutableList(),
                    "pointsOfInterest" to (if (trial) listOf(orbit) else emptyList()).toImmutableList(),
                )
                HudDrawCache.forgetLayouts()
                draw(w, h, language, textScale) { measurer ->
                    drawHud(model, measurer, 1f)
                    drawHudFeed(model, fx, measurer, 1f, null)
                }
                val where = "$language $w x $h x$textScale $boss trial=$trial"
                fun rect(block: HudBlock) = HudLayoutProbe.rect(block)
                fun apart(a: HudBlock, b: HudBlock) {
                    val first = rect(a) ?: return
                    val second = rect(b) ?: return
                    assertFalse(first.overlaps(second), "$where: $a $first meets $b $second")
                }
                val top = listOf(HudBlock.CLOCK, HudBlock.BADGE, HudBlock.MATTER_CHIP, HudBlock.KEY_CHIP, HudBlock.CHAIN, HudBlock.BOSS,
                    HudBlock.TRIAL_PANEL, HudBlock.FEED)
                // The feed's own placement (feed area) is checked against the chain, clock, badge, chips and
                // trial panel here; where it docks relative to the boss block is the feed's layout.
                top.forEachIndexed { index, a -> top.drop(index + 1).forEach { b -> if (setOf(a, b) != setOf(HudBlock.BOSS, HudBlock.FEED)) apart(a, b) } }
                val bottom = listOf(HudBlock.INTEGRITY, HudBlock.SPEED, HudBlock.LOADOUT)
                bottom.forEachIndexed { index, a -> bottom.drop(index + 1).forEach { b -> apart(a, b) } }
                listOf(HudBlock.CLOCK, HudBlock.BOSS, HudBlock.TRIAL_PANEL, HudBlock.INTEGRITY, HudBlock.SPEED, HudBlock.LOADOUT, HudBlock.FEED)
                    .forEach { block ->
                        val drawn = rect(block) ?: return@forEach
                        controls.forEach { (name, bounds) -> assertFalse(drawn.overlaps(bounds), "$where: $block $drawn meets $name $bounds") }
                    }
                listOf(HudBlock.CLOCK, HudBlock.BADGE, HudBlock.CHAIN, HudBlock.BOSS, HudBlock.INTEGRITY, HudBlock.SPEED).forEach { block ->
                    val drawn = assertNotNull(rect(block), "$where: $block not drawn")
                    assertTrue(drawn.left >= 0f && drawn.top >= 0f && drawn.right <= width && drawn.bottom <= height, "$where: $block $drawn off screen")
                }
                hudSlots.forEach { slot ->
                    val layout = HudDrawCache.peekLayout(slot) ?: return@forEach
                    assertFalse(layout.isCut(), "$where: $slot is cut")
                }
            }
        }
    }

    /** Dash and Brake labels are never cut and stay inside their slabs (with padding) at any text size. */
    @Test
    fun touchButtonLabelsFitInsideTheirSlabsAtEveryTextSize() {
        for (language in AppLanguage.entries) sizes.forEach { (w, h) ->
            for (textScale in textScales) {
                val model = hudTestModel(w.toFloat(), h.toFloat())
                val controls = runningControlBounds(w.toFloat(), h.toFloat(), 1f).associate { it.target to it.bounds }
                HudDrawCache.forgetLayouts()
                draw(w, h, language, textScale) { measurer ->
                    HudLayoutProbe.begin()
                    drawControls(model, measurer)
                }
                listOf(
                    Triple(HudText.DASH_LABEL, HudBlock.DASH_LABEL, RunningControlTarget.DASH),
                    Triple(HudText.BRAKE_LABEL, HudBlock.BRAKE_LABEL, RunningControlTarget.BRAKE),
                ).forEach { (slot, block, target) ->
                    val where = "$language $w x $h x$textScale $target"
                    val layout = assertNotNull(HudDrawCache.peekLayout(slot), where)
                    assertFalse(layout.isCut(), "$where: label cut")
                    val label = assertNotNull(HudLayoutProbe.rect(block), where)
                    val slab = controls.getValue(target)
                    // The slab is a parallelogram: its left edge runs from (left + cut, top) to (left, bottom).
                    val cut = if (w == 1_440) 12f else 14f
                    val padding = TOUCH_LABEL_PADDING_DP - 0.01f
                    listOf(label.top, label.bottom).forEach { y ->
                        val t = ((y - slab.top) / slab.height).coerceIn(0f, 1f)
                        assertTrue(label.left >= slab.left + cut * (1f - t) + padding, "$where: label $label past the slab's left edge")
                        assertTrue(label.right <= slab.right - cut * t - padding, "$where: label $label past the slab's right edge")
                    }
                    assertTrue(label.top >= slab.top && label.bottom <= slab.bottom, "$where: label $label outside $slab")
                    // At the default text size the label renders at its board size when it fits.
                    if (textScale == 1.25f && language == AppLanguage.English) {
                        val board = if (w == 1_440) 22f else if (target == RunningControlTarget.DASH) 20f else 17f
                        assertEquals(board, layout.layoutInput.style.fontSize.value, 0.01f, where)
                    }
                }
            }
        }
    }

    /** The trial panel's label, name and reward are never cut, for every trial, language and text size. */
    @Test
    fun trialPanelLinesAreNeverCutForAnyTrial() {
        for (language in AppLanguage.entries) sizes.forEach { (w, h) ->
            for (textScale in textScales) for (kind in PointOfInterestKind.entries) {
                val point = PointOfInterestProjection(kind, kind.name, 0f, 0f, true, 12f, 1, 0.4f, immutableListOf(1, 2, 3), 0f, 0f)
                val model = hudTestModel(w.toFloat(), h.toFloat()).with("pointsOfInterest" to listOf(point).toImmutableList())
                HudDrawCache.forgetLayouts()
                draw(w, h, language, textScale) { measurer -> drawHud(model, measurer, 1f) }
                val where = "$language $w x $h x$textScale $kind"
                val layout = HudTrialPanelLayout().update(w.toFloat(), h.toFloat(), 1f, textScale)
                listOf(HudText.TRIAL_LABEL, HudText.TRIAL_NAME, HudText.TRIAL_REWARD, HudText.TRIAL_CLOCK).forEach { slot ->
                    val line = assertNotNull(HudDrawCache.peekLayout(slot), "$where: $slot")
                    assertFalse(line.isCut(), "$where: $slot is cut")
                }
                val innerLeft = layout.left + layout.padding - 0.5f
                val innerRight = layout.right - layout.padding + 0.5f
                listOf(HudBlock.TRIAL_LABEL, HudBlock.TRIAL_CLOCK, HudBlock.TRIAL_NAME, HudBlock.TRIAL_PROGRESS, HudBlock.TRIAL_REWARD).forEach { block ->
                    val line = assertNotNull(HudLayoutProbe.rect(block), "$where: $block")
                    assertTrue(line.left >= innerLeft && line.right <= innerRight && line.top >= layout.top && line.bottom <= layout.bottom,
                        "$where: $block $line outside the panel")
                }
                fun rect(block: HudBlock) = assertNotNull(HudLayoutProbe.rect(block))
                assertFalse(rect(HudBlock.TRIAL_LABEL).overlaps(rect(HudBlock.TRIAL_CLOCK)), "$where: label meets the clock")
                assertFalse(rect(HudBlock.TRIAL_PROGRESS).overlaps(rect(HudBlock.TRIAL_REWARD)), "$where: progress meets the reward")
                assertTrue(rect(HudBlock.TRIAL_NAME).right <= layout.infoLeft, "$where: name runs under the (!)")
            }
        }
    }

    @Test
    fun polarityValueStaysOnScreenAndClearOfTheControlsWhereverTheCursorStrains() {
        val box = FloatArray(4)
        sizes.forEach { (w, h) ->
            val width = w.toFloat()
            val height = h.toFloat()
            val controls = runningControlBounds(width, height, 1f).map { it.bounds }
            var y = 4f
            while (y < height) {
                var x = 4f
                while (x < width) {
                    placePolarityLabel(x, y, 46f, 56f, 26f, width, height, 1f, box)
                    val label = Rect(box[0], box[1], box[2], box[3])
                    assertTrue(label.left >= 8f && label.top >= 8f && label.right <= width - 8f && label.bottom <= height - 8f,
                        "label off screen at $w x $h cursor ($x, $y): $label")
                    controls.forEach { control ->
                        assertFalse(label.overlaps(control), "label on a control at $w x $h cursor ($x, $y): $label vs $control")
                    }
                    x += 12f
                }
                y += 12f
            }
        }
        // The reviewed case: the cursor strains at the right edge beside Dash on a landscape phone.
        placePolarityLabel(823f, 226f, 46f, 56f, 26f, 844f, 390f, 1f, box)
        val dash = runningControlBounds(844f, 390f, 1f).first { it.target.name == "DASH" }.bounds
        assertFalse(Rect(box[0], box[1], box[2], box[3]).overlaps(dash))
    }

    @Test
    fun feedPlatesHoldEveryLineWithoutCuttingAndStayAboveTheControls() {
        val fx = VisualFxProjection.EMPTY.copy(buildNotifications = immutableListOf(
            BuildNotificationProjection("Neon Ram", immutableListOf("Impact damage +0.05", "Weapon power +0.04", "+ Vector maneuver"), 5f),
            BuildNotificationProjection("Ghost Vector", immutableListOf("Dash power +12"), 3f),
        ))
        for (language in AppLanguage.entries) sizes.forEach { (w, h) ->
            for (textScale in listOf(1f, 1.25f, 1.75f)) {
                val model = hudTestModel(w.toFloat(), h.toFloat()).with("message" to "ELITE SIGNAL", "messageTime" to 1.5f)
                draw(w, h, language, textScale) { measurer ->
                    HudLayoutProbe.begin()
                    drawHudFeed(model, fx, measurer, 1f, null)
                }
                val context = "$language $w x $h x$textScale"
                // Only what this frame drew: the banner, then one toast per notice the layout holds
                // (newest first) with its title and details; phones keep two details per toast, the
                // synergy line among them.
                val notices = if (w == 844) 1 else 2
                assertEquals(1 + notices, FeedProbe.plateCount, "$context: plates")
                assertTrue(FeedProbe.isBanner(0), context)
                val lines = (0 until FeedProbe.lineCount).groupBy { FeedProbe.linePlate(it) }
                assertEquals(1, lines[0]?.size, "$context: banner lines")
                assertEquals(2, lines[1]?.size, "$context: Ghost Vector lines")
                if (notices == 2) assertEquals(if (w == 1_440) 4 else 3, lines[2]?.size, "$context: Neon Ram lines")
                assertFeedLinesFit(context, w, h)
                val feed = assertNotNull(HudLayoutProbe.rect(HudBlock.FEED))
                runningControlBounds(w.toFloat(), h.toFloat(), 1f).forEach { control ->
                    assertFalse(feed.overlaps(control.bounds), "$context: feed $feed covers ${control.target}")
                }
            }
        }
    }

    @Test
    fun instructionTailsStayOutOfTheFeedWhileEventTailsRemain() {
        assertEquals("TWO ANOMALIES DETECTED" to null, feedMessageParts("TWO ANOMALIES DETECTED // CHOOSE A COURSE", AppLanguage.English))
        assertEquals("ОБНАРУЖЕНЫ ДВЕ АНОМАЛИИ" to null, feedMessageParts("TWO ANOMALIES DETECTED // CHOOSE A COURSE", AppLanguage.Russian))
        assertEquals("CATALOG COMPLETE" to "MATTER SALVAGED", feedMessageParts("CATALOG COMPLETE // MATTER SALVAGED", AppLanguage.English))
        assertEquals("ELITE SIGNAL" to null, feedMessageParts("ELITE SIGNAL", AppLanguage.English))
    }

    @Test
    fun trialRulesOpenBelowThePanelWithoutCoveringIt() {
        val orbit = PointOfInterestProjection(PointOfInterestKind.SEALED_ANOMALY, "Sealed anomaly", 0f, 0f, true, 12f, 0, 1f / 3f,
            immutableListOf(1, 2, 3), 0f, 0f)
        for (language in AppLanguage.entries) sizes.forEach { (w, h) ->
            for (textScale in textScales) for (kind in PointOfInterestKind.entries) {
                val trial = orbit.copy(kind = kind)
                val model = hudTestModel(w.toFloat(), h.toFloat()).with("pointsOfInterest" to listOf(trial).toImmutableList())
                draw(w, h, language, textScale) { measurer ->
                    drawHud(model, measurer, 1f, trialInfoOpen = true)
                    drawTrialTooltip(model, measurer, open = true)
                }
                val panel = assertNotNull(HudLayoutProbe.rect(HudBlock.TRIAL_PANEL))
                val tooltip = assertNotNull(HudLayoutProbe.rect(HudBlock.TRIAL_TOOLTIP))
                assertFalse(panel.overlaps(tooltip), "$language $w x $h x$textScale $kind: rules cover the panel")
                assertTrue(tooltip.left >= 0f && tooltip.right <= w && tooltip.top >= 0f && tooltip.bottom <= h)
                // Large text never pushes the rules over the bottom clusters or the controls.
                listOf(HudBlock.INTEGRITY, HudBlock.SPEED, HudBlock.LOADOUT, HudBlock.BADGE).forEach { block ->
                    val other = assertNotNull(HudLayoutProbe.rect(block))
                    assertFalse(tooltip.overlaps(other), "$language $w x $h x$textScale $kind: rules $tooltip cover $block $other")
                }
                runningControlBounds(w.toFloat(), h.toFloat(), 1f).forEach { control ->
                    assertFalse(tooltip.overlaps(control.bounds), "$language $w x $h x$textScale: rules cover ${control.target}")
                }
            }
        }
        // Closed: nothing drawn.
        val model = hudTestModel().with("pointsOfInterest" to listOf(orbit).toImmutableList())
        draw(1_440, 810, AppLanguage.English) { measurer -> drawHud(model, measurer, 1f); drawTrialTooltip(model, measurer, open = false) }
        assertNull(HudLayoutProbe.rect(HudBlock.TRIAL_TOOLTIP))
    }

    /** The phone-landscape matter value is display-cased like the chips of the other layouts ("+1,2 МЛН"). */
    @Test
    fun matterValueIsDisplayCasedInEveryLayout() {
        for (language in AppLanguage.entries) sizes.forEach { (w, h) ->
            val model = hudTestModel(w.toFloat(), h.toFloat()).with("runMatter" to 1_234_567L, "keys" to 2)
            HudDrawCache.forgetLayouts()
            draw(w, h, language) { measurer -> drawHud(model, measurer, 1f) }
            val shown = assertNotNull(HudDrawCache.peekLayout(HudText.MATTER), "$language $w x $h").layoutInput.text.text
            assertEquals(shown.uppercase(), shown, "$language $w x $h")
            assertTrue(shown.any { it.isLetter() }, "$language $w x $h: $shown has no unit")
        }
    }

    @Test
    fun theCutCheckSeesAnEllipsisThatSkiaDoesNotReport() {
        draw(200, 100, AppLanguage.Russian) { measurer ->
            val style = measurer.typography.labelStyle(15f)
            val whole = measureKkText(measurer, "Испытание аномалии", style, uppercase = true)
            val cut = measureKkText(measurer, "Испытание аномалии", style, uppercase = true, maxWidth = whole.size.width * 0.6f)
            assertFalse(whole.isCut())
            assertTrue(cut.isCut(), "an ellipsized line is not seen as cut")
            assertFalse(cut.isLineEllipsized(0), "Skia started reporting ellipsis: the plain check would do")
        }
    }

    @Test
    fun orbitSecondsTemplatesLeadWithTheValue() {
        // The orbit progress is drawn as digits + the template's tail, so the value must come first.
        listOf(kinetickk.ball.gameplay.interaction.localization.HudRedesignText.TrialSeconds).forEach {
            assertTrue(it.english.startsWith("{0}") && it.russian.startsWith("{0}"))
        }
    }

    private fun draw(width: Int, height: Int, language: AppLanguage, textScale: Float = 1f, block: androidx.compose.ui.graphics.drawscope.DrawScope.(CanvasTextMeasurer) -> Unit) {
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), textScale, language,
            HudTestFonts.typography.copy(localeList = language.localeList()))
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(width, height)), Size(width.toFloat(), height.toFloat())) {
            block(measurer)
        }
    }
}

/**
 * Whether a layout lost text to its width or line limit. Skia's `isLineEllipsized` is always false
 * on desktop, so a one-line layout counts as cut when its text needs more width than it was laid
 * out in, and a wrapped one when it ran past its line limit.
 */
internal fun androidx.compose.ui.text.TextLayoutResult.isCut(): Boolean =
    multiParagraph.didExceedMaxLines || (lineCount == 1 && multiParagraph.intrinsics.maxIntrinsicWidth > size.width + 0.5f)

/** The bundled redesign fonts, loaded from the design module's resources for layout tests. */
internal object HudTestFonts {
    val typography: InterfaceTypography by lazy {
        val directory = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "foundation/design/src/commonMain/composeResources/font") }
            .first { File(it, "kk_wide_black.ttf").isFile }
        fun font(name: String, weight: FontWeight, style: FontStyle = FontStyle.Normal) = Font(File(directory, "$name.ttf"), weight, style)
        val cond = FontFamily(
            font("kk_cond_black_italic", FontWeight.Black, FontStyle.Italic),
            font("kk_cond_black", FontWeight.Black),
            font("kk_cond_extrabold", FontWeight.ExtraBold),
        )
        InterfaceTypography(
            body = FontFamily(font("kk_body_regular", FontWeight.Normal), font("kk_body_medium", FontWeight.Medium), font("kk_body_bold", FontWeight.Bold)),
            display = FontFamily(font("kk_cond_black_italic", FontWeight.Bold), font("kk_cond_extrabold", FontWeight.Normal)),
            wide = FontFamily(font("kk_wide_black", FontWeight.Black), font("kk_wide_bold", FontWeight.Bold)),
            cond = cond,
            label = cond,
            mono = FontFamily(font("kk_mono_medium", FontWeight.Medium), font("kk_mono_bold", FontWeight.Bold)),
        )
    }
}
