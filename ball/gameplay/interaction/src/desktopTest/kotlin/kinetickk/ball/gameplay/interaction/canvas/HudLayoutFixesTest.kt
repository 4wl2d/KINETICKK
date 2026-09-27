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
import kinetickk.ball.gameplay.interaction.layout.runningControlBounds
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.PointOfInterestProjection
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.InterfaceTypography
import kinetickk.foundation.design.localeList
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

    @Test
    fun portraitBossRowNeverMeetsTheChipsClockOrChain() {
        for (language in AppLanguage.entries) for (boss in listOf(EnemyType.ELITE, EnemyType.ARCHITECT)) {
            val model = hudTestModel(390f, 844f).with(
                "keys" to 2, "runMatter" to 9_876_543L, "combo" to 128, "comboTime" to 1f, "comboWindow" to 2.8f,
                "enemies" to listOf(EnemyProjection(4, boss, 0f, 0f, 0f, 0f, 500f, 1_000f, 60f, 0f, 0f, 0f, 0f, 0f, 0f, false))
                    .toImmutableList(),
            )
            draw(390, 844, language) { measurer -> drawHud(model, measurer, 1f) }
            val bossRect = assertNotNull(HudLayoutProbe.rect(HudBlock.BOSS))
            listOf(HudBlock.MATTER_CHIP, HudBlock.KEY_CHIP, HudBlock.CLOCK, HudBlock.CHAIN).forEach { block ->
                val other = assertNotNull(HudLayoutProbe.rect(block), "$block not drawn")
                assertFalse(bossRect.overlaps(other), "$language $boss: boss $bossRect meets $block $other")
            }
            assertTrue(bossRect.left >= 0f && bossRect.right <= 390f)
            // The trial panel starts below the reserved boss row.
            assertTrue(HudTrialPanelLayout().update(390f, 844f, 1f, 1f).top >= bossRect.bottom)
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
            val model = hudTestModel(w.toFloat(), h.toFloat()).with("message" to "ELITE SIGNAL", "messageTime" to 1.5f)
            draw(w, h, language) { measurer -> drawHudFeed(model, fx, measurer, 1f, null) }
            listOf(HudText.NOTICE_TITLE_0, HudText.NOTICE_DETAIL_0, HudText.NOTICE_TITLE_1, HudText.NOTICE_DETAIL_4, HudText.MESSAGE_TITLE)
                .forEach { slot ->
                    val layout = HudDrawCache.peekLayout(slot) ?: return@forEach
                    assertFalse(layout.isLineEllipsized(layout.lineCount - 1), "$language $w x $h: $slot is cut")
                }
            val feed = assertNotNull(HudLayoutProbe.rect(HudBlock.FEED))
            runningControlBounds(w.toFloat(), h.toFloat(), 1f).forEach { control ->
                assertFalse(feed.overlaps(control.bounds), "$language $w x $h: feed $feed covers ${control.target}")
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
            listOf(1f, 1.75f).forEach { textScale ->
                val model = hudTestModel(w.toFloat(), h.toFloat()).with("pointsOfInterest" to listOf(orbit).toImmutableList())
                draw(w, h, language, textScale) { measurer ->
                    drawHud(model, measurer, 1f, trialInfoOpen = true)
                    drawTrialTooltip(model, measurer, open = true)
                }
                val panel = assertNotNull(HudLayoutProbe.rect(HudBlock.TRIAL_PANEL))
                val tooltip = assertNotNull(HudLayoutProbe.rect(HudBlock.TRIAL_TOOLTIP))
                assertFalse(panel.overlaps(tooltip), "$language $w x $h x$textScale: rules cover the panel")
                assertTrue(tooltip.left >= 0f && tooltip.right <= w && tooltip.top >= 0f && tooltip.bottom <= h)
            }
        }
        // Closed: nothing drawn.
        val model = hudTestModel().with("pointsOfInterest" to listOf(orbit).toImmutableList())
        draw(1_440, 810, AppLanguage.English) { measurer -> drawHud(model, measurer, 1f); drawTrialTooltip(model, measurer, open = false) }
        assertNull(HudLayoutProbe.rect(HudBlock.TRIAL_TOOLTIP))
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
