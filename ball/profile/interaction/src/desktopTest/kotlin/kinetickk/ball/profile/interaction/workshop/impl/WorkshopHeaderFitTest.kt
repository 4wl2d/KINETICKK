// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.workshop.impl

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import kinetickk.ball.profile.interaction.ProfileLayoutMode
import kinetickk.ball.profile.interaction.localization.ProfileScreensRedesignText
import kinetickk.ball.profile.interaction.matrix.impl.matrixResetWidth
import kinetickk.ball.profile.interaction.profileFrame
import kinetickk.ball.profile.interaction.profileTextScale
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTab
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.measureKkText
import kinetickk.foundation.design.rememberKkCanvasMeasurer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Workshop header with the bundled fonts, in English and Russian, at text 100 %, 125 % and
 * 175 %, on the six reference sizes, for each tab with its widest count and extra control: the
 * fit rule keeps every tab label whole inside its tab, the strip clear of Back, the right
 * cluster and the chip, and the underline inside the header.
 */
@OptIn(ExperimentalTestApi::class)
class WorkshopHeaderFitTest {
    private val sizes = listOf(1440 to 810, 900 to 560, 844 to 390, 640 to 360, 390 to 844, 360 to 740)
    private val settings = listOf(1f, 1.25f, 1.75f)

    private class Case(
        val width: Int,
        val height: Int,
        val language: AppLanguage,
        val setting: Float,
        val tab: WorkshopTab,
        val layout: WorkshopHeaderLayout,
        val labelWidths: List<Float>,
    ) {
        override fun toString() = "${width}x$height $language @$setting $tab"
    }

    @Test
    fun everyTabLabelStaysWholeInsideItsTabAndClearOfTheRestOfTheRow() {
        val cases = ArrayList<Case>()
        runSkikoComposeUiTest(Size(200f, 200f), Density(1f)) {
            setContent {
                for (language in AppLanguage.entries) CompositionLocalProvider(LocalAppLanguage provides language) {
                    for (setting in settings) {
                        val scale = profileTextScale(setting)
                        val measurer = rememberKkCanvasMeasurer(scale)
                        for ((width, height) in sizes) {
                            val frame = profileFrame(width.toFloat(), height.toFloat(), 1f)
                            for (tab in WorkshopTab.entries) {
                                // Each tab's widest count and its extra control at its widest (the scrolling pager, Reset/Confirm).
                                val count = when (tab) {
                                    WorkshopTab.CHARACTERS -> language.text(ProfileScreensRedesignText.CountOf, 12, 12) + ", " +
                                        language.text(ProfileScreensRedesignText.Ascendant)
                                    WorkshopTab.WEAPONS -> language.text(ProfileScreensRedesignText.CountOf, 24, 24)
                                    WorkshopTab.MATRIX -> language.text(ProfileScreensRedesignText.CountOf, 32, 32)
                                }
                                val extra = when (tab) {
                                    WorkshopTab.CHARACTERS -> 0f
                                    WorkshopTab.WEAPONS -> frame.d(30f) * 2f + frame.d(8f) * 2f + frame.d(16f) + frame.d(34f) * scale
                                    WorkshopTab.MATRIX -> matrixResetWidth(measurer, frame, language)
                                }
                                val layout = workshopHeader(measurer, frame, language, 9_999_999L, count, infoShown = true, extraWidth = extra)
                                val labels = WorkshopTab.entries.map { entry ->
                                    measureKkText(measurer, entry.label(language, layout.short && entry == WorkshopTab.MATRIX),
                                        measurer.typography.condStyle(layout.labelSize, trackingEm = 0.03f), uppercase = true).size.width.toFloat()
                                }
                                cases += Case(width, height, language, setting, tab, layout, labels)
                            }
                        }
                    }
                }
            }
            waitForIdle()
        }
        assertEquals(AppLanguage.entries.size * settings.size * sizes.size * WorkshopTab.entries.size, cases.size)
        var shrunk = 0
        var moved = 0
        for (case in cases) {
            val layout = case.layout
            val frame = layout.frame
            val metrics = workshopHeaderMetrics(frame.mode)
            val padding = frame.d(metrics.padding)
            assertTrue(layout.fits, "$case does not fit: tabs ${layout.tabs} cluster ${layout.count} ${layout.info} ${layout.extra}")
            assertTrue(layout.labelFactor >= (if (frame.portrait) WORKSHOP_PORTRAIT_LABEL_FLOOR else WORKSHOP_LABEL_FLOOR) - 0.001f, "$case")
            if (layout.labelFactor < 1f) shrunk++
            if (layout.extraInBody) moved++
            layout.tabs.forEachIndexed { index, tab ->
                assertTrue(case.labelWidths[index] <= tab.width - padding * 2f + 0.5f,
                    "$case label ${case.labelWidths[index]} does not fit tab ${tab.width} (padding $padding)")
                // The sheared underline (5..8 below the tab) stays inside the header.
                assertTrue(tab.bottom + frame.d(8f) <= frame.headerHeight + 0.5f, "$case underline below the header")
                assertTrue(tab.left >= frame.left - 0.5f && tab.right <= frame.right + 0.5f, "$case tab $tab off the frame")
            }
            layout.tabs.zipWithNext().forEach { (a, b) -> assertTrue(a.right <= b.left + 0.5f, "$case tabs overlap") }
            val cluster = listOfNotNull(layout.count, layout.info, layout.extra)
            cluster.forEach { assertTrue(it.right <= layout.chip.left + 0.5f && it.left >= layout.back.right - 0.5f, "$case cluster $it") }
            assertTrue(layout.chip.right <= frame.right + 0.5f && layout.chip.left >= layout.back.right, "$case chip ${layout.chip}")
            if (frame.portrait) {
                assertTrue(layout.tabs.all { it.top >= layout.back.bottom }, "$case portrait tabs under the Back row")
                assertTrue(layout.short, "$case phones use the short Matrix label")
            } else {
                assertTrue(layout.tabs.first().left >= layout.back.right, "$case tabs after Back")
            }
            // Only the regular frame shows the count; phones keep the short Matrix label.
            if (frame.mode != ProfileLayoutMode.REGULAR) {
                assertNull(layout.count, "$case")
                assertTrue(layout.short, "$case")
            }
            // Only landscape moves the extra into the body (and only a tab that has one).
            if (layout.extraInBody) {
                assertTrue(frame.mode != ProfileLayoutMode.REGULAR && case.tab != WorkshopTab.CHARACTERS, "$case")
                assertNull(layout.extra, "$case")
            }
        }
        // The default text on the board frame keeps the full labels and the count of every tab.
        cases.filter { it.width == 1440 && it.setting == 1.25f }.forEach { case ->
            assertEquals(1f, case.layout.labelFactor, "$case")
            assertTrue(case.layout.count != null && !case.layout.short, "$case")
        }
        println("Workshop header fit: ${cases.size} cases, $shrunk with shrunk labels, $moved with the extra in the body: " +
            cases.filter { it.layout.extraInBody }.joinToString { "$it" } + "; smallest label factor " +
            cases.minOf { it.layout.labelFactor })
    }
}
