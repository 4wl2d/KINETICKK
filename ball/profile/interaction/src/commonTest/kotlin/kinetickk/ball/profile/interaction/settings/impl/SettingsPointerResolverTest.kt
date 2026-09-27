// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import androidx.compose.ui.geometry.Rect
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsPointerResolverTest {
    /** Viewports of the three layout modes plus a high-density desktop and a short landscape phone. */
    private val viewports = listOf(
        Triple(1440f, 810f, 1f), Triple(1000f, 700f, 1f), Triple(1280f, 720f, 1f), Triple(2880f, 1620f, 2f),
        Triple(844f, 390f, 1f), Triple(720f, 360f, 1f), Triple(1440f, 720f, 2f), Triple(640f, 300f, 1f),
        Triple(390f, 844f, 1f), Triple(390f, 720f, 1f), Triple(780f, 1440f, 2f),
    )

    @Test
    fun layoutModesFollowTheViewport() {
        assertEquals(SettingsLayoutMode.REGULAR, layout(1440f, 810f).mode)
        assertEquals(SettingsLayoutMode.REGULAR, layout(1000f, 700f).mode)
        assertEquals(SettingsLayoutMode.COMPACT_LANDSCAPE, layout(844f, 390f).mode)
        assertEquals(SettingsLayoutMode.COMPACT_LANDSCAPE, layout(1440f, 720f, 2f).mode)
        assertEquals(SettingsLayoutMode.COMPACT_PORTRAIT, layout(390f, 844f).mode)
        // Wide desktops keep the preview in its own column beside the rows, on every tab.
        for (group in SettingsGroup.entries) {
            val wide = layout(1440f, 810f, 1f, group)
            val preview = assertNotNull(wide.preview, "$group")
            assertTrue(preview.left >= wide.rowsRight, "$group side column")
            assertNotNull(wide.panelEdgeX)
        }
        // Elsewhere it stacks under a short tab and gives way to rows on a full one.
        for ((width, height) in listOf(1000f to 700f, 844f to 390f, 390f to 844f)) {
            val short = layout(width, height, 1f, SettingsGroup.GAME)
            val stacked = assertNotNull(short.preview, "$width x $height")
            assertTrue(stacked.top >= short.rows.last().bounds.bottom, "$width x $height stacked")
            assertNull(short.panelEdgeX)
            assertNull(layout(width, height, 1f, SettingsGroup.GRAPHICS).preview, "$width x $height graphics")
        }
    }

    @Test
    fun graphicsLeadsWithColorVisionAndEveryRowIsReachableOnSomePage() {
        assertEquals(SettingsRow.COLOR_VISION, SettingsGroup.GRAPHICS.rows.first())
        for ((width, height, density) in viewports) for (group in SettingsGroup.entries) for (language in AppLanguage.entries) {
            val first = layout(width, height, density, group, 0, language)
            val reached = (0..first.maxPage).flatMap { page -> layout(width, height, density, group, page, language).visibleRows }
            assertEquals(group.rows, reached, "$width x $height @$density $group $language")
            assertEquals(first.maxPage, layout(width, height, density, group, 99, language).page, "out-of-range pages clamp")
        }
    }

    @Test
    fun everyDrawnControlResolvesToItsOwnActionInAllModes() {
        for ((width, height, density) in viewports) for (group in SettingsGroup.entries) {
            val maxPage = layout(width, height, density, group).maxPage
            for (page in 0..maxPage) {
                val layout = layout(width, height, density, group, page)
                val where = "$width x $height @$density $group p$page"
                assertEquals(SettingsAction.Back, press(layout, layout.back), where)
                layout.tabs.forEach { assertEquals(SettingsAction.SelectGroup(it.group), press(layout, it.bounds), where) }
                layout.pages.forEachIndexed { index, pip -> assertEquals(SettingsAction.PageSelected(index), press(layout, pip), where) }
                layout.rows.forEach { row ->
                    assertEquals(SettingsAction.ToggleInfo(row.row), press(layout, row.info), "$where ${row.row} info")
                    when (row.row.control) {
                        SettingsControl.SEGMENTED -> {
                            assertEquals(row.row.optionCount(), row.options.size, "$where ${row.row}")
                            row.options.forEachIndexed { option, cell ->
                                val expected = if (row.row == SettingsRow.LANGUAGE) {
                                    SettingsAction.SelectLanguage(AppLanguage.entries[option])
                                } else {
                                    SettingsAction.Select(row.row, option)
                                }
                                assertEquals(expected, press(layout, cell), "$where ${row.row} option $option")
                            }
                        }
                        SettingsControl.TOGGLE -> assertEquals(SettingsAction.Adjust(row.row, 1), press(layout, assertNotNull(row.toggle)), where)
                        SettingsControl.SLIDER -> if (row.row == SettingsRow.MASTER_VOLUME) {
                            // The Compose slider, steppers and numeric editor own the whole strip.
                            val strip = assertNotNull(layout.volumeBounds())
                            for (x in listOf(strip.left + 1f, strip.center.x, strip.right - 1f)) {
                                assertNull(resolveSettingsPress(layout, x, strip.center.y), where)
                            }
                        } else {
                            assertEquals(SettingsAction.Adjust(row.row, -1), press(layout, assertNotNull(row.decrease)), where)
                            assertEquals(SettingsAction.Adjust(row.row, 1), press(layout, assertNotNull(row.increase)), where)
                            assertNull(press(layout, assertNotNull(row.track)), "$where the track is a readout")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun geometryStaysInsideTheViewportWithoutOverlapsAndMeetsTouchMinimums() {
        for ((width, height, density) in viewports) for (group in SettingsGroup.entries) for (language in AppLanguage.entries) {
            val maxPage = layout(width, height, density, group, 0, language).maxPage
            for (page in 0..maxPage) {
                val layout = layout(width, height, density, group, page, language)
                val where = "$width x $height @$density $group p$page $language"
                val screen = layout.bounds
                (listOf(layout.back, layout.title) + layout.tabs.map { it.bounds } + layout.pages).forEach {
                    assertTrue(it.inside(screen), "$where header/tab/pager $it")
                }
                layout.tabs.zipWithNext().forEach { (a, b) -> assertTrue(!a.bounds.overlaps(b.bounds), "$where tabs overlap") }
                layout.rows.zipWithNext().forEach { (a, b) -> assertTrue(a.bounds.bottom <= b.bounds.top, "$where rows overlap") }
                layout.rows.forEach { row ->
                    val parts = listOfNotNull(row.info, row.toggle, row.decrease, row.track, row.increase, row.output) + row.options
                    assertTrue(row.bounds.inside(screen), "$where ${row.row} row")
                    parts.forEach { assertTrue(it.inside(row.bounds), "$where ${row.row} part $it in ${row.bounds}") }
                    row.options.zipWithNext().forEach { (a, b) -> assertTrue(a.right <= b.left, "$where ${row.row} cells overlap") }
                    assertTrue(row.label.width >= 40f * density, "$where ${row.row} label room ${row.label.width}")
                    // Every row offers at least a 44 dp tall hit band for its control.
                    assertTrue(row.bounds.height >= SETTINGS_TOUCH_DP * density - 0.01f, "$where ${row.row} height")
                }
                layout.preview?.let { preview ->
                    assertTrue(preview.inside(screen), "$where preview")
                    layout.rows.forEach { assertTrue(!it.bounds.overlaps(preview), "$where preview covers ${it.row}") }
                }
            }
        }
    }

    @Test
    fun aShortLandscapeViewportPagesGraphicsAndKeepsEveryPageReachable() {
        val first = layout(640f, 300f, 1f, SettingsGroup.GRAPHICS, 0)
        assertEquals(SettingsLayoutMode.COMPACT_LANDSCAPE, first.mode)
        assertTrue(first.maxPage >= 1)
        assertEquals(first.maxPage + 1, first.pages.size)
        assertEquals(SettingsAction.PageSelected(1), press(first, first.pages[1]))
        val last = layout(640f, 300f, 1f, SettingsGroup.GRAPHICS, first.maxPage)
        val thresholds = assertNotNull(last.row(SettingsRow.DAMAGE_COLOR_THRESHOLDS))
        assertEquals(SettingsAction.Adjust(SettingsRow.DAMAGE_COLOR_THRESHOLDS, 1), press(last, assertNotNull(thresholds.increase)))
        // The two-page Graphics tab fits on one page at the reference phone size.
        assertEquals(0, layout(844f, 390f, 1f, SettingsGroup.GRAPHICS).maxPage)
        assertEquals(0, layout(1440f, 810f, 1f, SettingsGroup.GRAPHICS).maxPage)
    }

    @Test
    fun regularLayoutMatchesTheBoardGeometry() {
        val layout = layout(1440f, 810f, 1f, SettingsGroup.GRAPHICS)
        val colorVision = assertNotNull(layout.row(SettingsRow.COLOR_VISION))
        assertEquals(Rect(44f, 172f, 964f, 234f), colorVision.bounds)
        assertEquals(Rect(916f, 191f, 940f, 215f), colorVision.info)
        assertEquals(896f, colorVision.options.last().right, 0.01f)
        assertEquals(34f, colorVision.options.first().height)
        assertEquals(238f, layout.rows[1].bounds.top, "rows are 62 px with a 4 px gap")
        val preview = assertNotNull(layout.preview)
        assertEquals(1056f, preview.left)
        assertEquals(108f, preview.top)
        assertEquals(1000f, layout.panelEdgeX)
        assertEquals(104f, layout.tabs.first().bounds.top)
        val sound = assertNotNull(layout(1440f, 810f, 1f, SettingsGroup.SOUND).row(SettingsRow.MASTER_VOLUME))
        assertEquals(380f, assertNotNull(sound.sliderBounds).width, "slider strip is 380 px wide")
    }

    @Test
    fun portraitStacksChoicesUnderTheirLabelAndKeepsTogglesOnOneLine() {
        val layout = layout(390f, 844f, 1f, SettingsGroup.GRAPHICS)
        val colorVision = assertNotNull(layout.row(SettingsRow.COLOR_VISION))
        assertTrue(colorVision.options.first().top >= colorVision.info.bottom, "cells sit below the label line")
        assertTrue(colorVision.options.first().left <= colorVision.label.left + 0.5f, "cells span the whole row")
        val shake = assertNotNull(layout.row(SettingsRow.SCREEN_SHAKE))
        val toggle = assertNotNull(shake.toggle)
        assertEquals(toggle.center.y, shake.info.center.y, 0.5f)
    }

    @Test
    fun colorVisionShowsRoleSwatchesWheneverNoSidePreviewShowsThePalette() {
        for ((width, height, density) in viewports) for (language in AppLanguage.entries) for (scale in listOf(1f, 1.4f)) {
            val layout = layout(width, height, density, SettingsGroup.GRAPHICS, 0, language, scale)
            val where = "$width x $height @$density $language x$scale"
            val row = assertNotNull(layout.row(SettingsRow.COLOR_VISION), where)
            if (layout.panelEdgeX != null) {
                assertTrue(row.swatches.isEmpty(), "$where: the side preview already shows the palette")
                continue
            }
            assertEquals(5, row.swatches.size, where)
            val cells = row.options
            row.swatches.forEach { swatch ->
                assertTrue(swatch.top >= cells.maxOf { it.bottom }, "$where swatch under the cells")
                assertTrue(swatch.bottom <= row.bounds.bottom + 0.01f && swatch.left >= cells.first().left - 0.01f &&
                    swatch.right <= cells.last().right + 0.01f, "$where swatch inside the cell span")
                assertTrue(swatch.width >= 24f * density, "$where swatch width ${swatch.width}")
                // The strip is a readout: pressing it chooses nothing.
                assertNull(resolveSettingsPress(layout, swatch.center.x, swatch.center.y), where)
            }
            row.swatches.zipWithNext().forEach { (a, b) -> assertTrue(a.right <= b.left, "$where swatches overlap") }
            // Every cell keeps a 44 dp tall hit band above the strip.
            cells.forEachIndexed { option, cell ->
                val band = (0..200).map { cell.top - 30f * density + it * density * 0.5f }
                    .filter { resolveSettingsPress(layout, cell.center.x, it) == SettingsAction.Select(SettingsRow.COLOR_VISION, option) }
                assertTrue(band.max() - band.min() >= SETTINGS_TOUCH_DP * density - density, "$where cell $option band")
            }
        }
    }

    @Test
    fun explanationsNeverCoverNavigationAndStayOnScreen() {
        for ((width, height, density) in viewports) for (group in SettingsGroup.entries) {
            val maxPage = layout(width, height, density, group).maxPage
            for (page in 0..maxPage) {
                val layout = layout(width, height, density, group, page)
                for (row in layout.visibleRows) for (bodyDp in listOf(40f, 90f, 150f)) {
                    val rect = assertNotNull(layout.tooltipRect(row, bodyDp * density))
                    val where = "$width x $height @$density $group p$page $row $bodyDp"
                    assertTrue(rect.inside(layout.bounds), "$where on screen $rect")
                    layout.navigation.forEach { assertTrue(!it.overlaps(rect), "$where covers $it") }
                }
            }
        }
    }

    @Test
    fun compactLandscapeRowsMakeRoomForTwoLineLabelsAtLargeText() {
        for ((width, height) in listOf(720f to 360f, 844f to 390f, 640f to 300f)) for (language in AppLanguage.entries) {
            for (group in SettingsGroup.entries) {
                val first = layout(width, height, 1f, group, 0, language, scale = 1.4f)
                for (page in 0..first.maxPage) {
                    val layout = layout(width, height, 1f, group, page, language, scale = 1.4f)
                    val line = stubSettingsMetrics(1f, 1.4f).rowLabelLineHeight()
                    layout.rows.forEach { row ->
                        val natural = stubSettingsMetrics(1f, 1.4f).width(row.row.label(language), SettingsTextKind.ROW_LABEL)
                        val lines = if (natural <= row.label.width) 1 else 2
                        assertTrue(row.label.height >= line * lines + 12f,
                            "$width x $height $language ${row.row}: $lines lines need ${line * lines} in ${row.label.height}")
                    }
                }
            }
        }
    }

    private fun press(layout: SettingsLayout, rect: Rect): SettingsAction? =
        resolveSettingsPress(layout, rect.center.x, rect.center.y)

    private fun Rect.inside(outer: Rect): Boolean =
        left >= outer.left - 0.01f && top >= outer.top - 0.01f && right <= outer.right + 0.01f && bottom <= outer.bottom + 0.01f
}

/** Deterministic text sizes: 0.55 em per character at the kind's reference size, times [scale]. */
internal fun stubSettingsMetrics(density: Float, scale: Float = 1f): SettingsTextMetrics = object : SettingsTextMetrics {
    override fun width(text: String, kind: SettingsTextKind): Float {
        val size = when (kind) {
            SettingsTextKind.TITLE -> 44f
            SettingsTextKind.BUTTON -> 19f
            SettingsTextKind.TAB -> 21f
            SettingsTextKind.SEGMENT -> 16f
            SettingsTextKind.OUTPUT -> 22f
            SettingsTextKind.ROW_LABEL -> 21f
        }
        return text.length * size * 0.55f * density * scale
    }

    override fun rowLabelLineHeight(): Float = 21f * 0.86f * density * scale
}

internal fun layout(
    width: Float,
    height: Float,
    density: Float = 1f,
    group: SettingsGroup = SettingsGroup.GAME,
    page: Int = 0,
    language: AppLanguage = AppLanguage.English,
    scale: Float = 1f,
): SettingsLayout = settingsLayout(width, height, density, group, page, language, stubSettingsMetrics(density, scale))
