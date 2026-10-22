// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import androidx.compose.ui.geometry.Rect
import kinetickk.ball.profile.api.WindowSizePreset
import kinetickk.ball.profile.interaction.settings.api.SettingsDisplaySupport
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
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
        for (platform in testPlatforms) for (group in listOf(SettingsGroup.GAME, SettingsGroup.SOUND, SettingsGroup.GRAPHICS, SettingsGroup.INTERFACE)) {
            assertEquals(group.rows, platform.rows(group), "$group rows are offered on every platform")
        }
        for ((width, height, density) in viewports) for (group in SettingsGroup.entries) for (language in AppLanguage.entries) {
            val first = layout(width, height, density, group, 0, language)
            val reached = (0..first.maxPage).flatMap { page -> layout(width, height, density, group, page, language).visibleRows }
            val offered = SettingsPlatform.DEFAULT.rows(group)
            if (twoColumns(first)) {
                // Two columns page by row pairs: each row once, each column in order on every page.
                assertEquals(offered.toSet(), reached.toSet(), "$width x $height @$density $group $language")
                assertEquals(offered.size, reached.size)
                for (page in 0..first.maxPage) {
                    val rows = layout(width, height, density, group, page, language).rows
                    for (column in rows.groupBy { it.bounds.left }.values) {
                        assertEquals(column.map { it.row }, column.map { it.row }.sortedBy { group.rows.indexOf(it) })
                    }
                }
            } else {
                assertEquals(offered, reached, "$width x $height @$density $group $language")
            }
            assertEquals(first.maxPage, layout(width, height, density, group, 99, language).page, "out-of-range pages clamp")
        }
        for ((width, height, density) in viewports) for (platform in testPlatforms) for (group in platform.groups) {
            val first = layout(width, height, density, group, platform = platform)
            val reached = (0..first.maxPage).flatMap { page -> layout(width, height, density, group, page, platform = platform).visibleRows }
            assertEquals(platform.rows(group).toSet(), reached.toSet(), "$width x $height @$density $group ${platform.support}")
            assertEquals(platform.rows(group).size, reached.size)
        }
    }

    @Test
    fun everyDrawnControlResolvesToItsOwnActionInAllModes() {
        for ((width, height, density) in viewports) for (platform in testPlatforms) for (group in platform.groups) {
            val maxPage = layout(width, height, density, group, platform = platform).maxPage
            for (page in 0..maxPage) {
                val layout = layout(width, height, density, group, page, platform = platform)
                val where = "$width x $height @$density $group p$page ${platform.support}"
                assertEquals(SettingsAction.Back, press(layout, layout.back), where)
                layout.tabs.forEach { assertEquals(SettingsAction.SelectGroup(it.group), press(layout, it.bounds), where) }
                layout.pages.forEachIndexed { index, pip -> assertEquals(SettingsAction.PageSelected(index), press(layout, pip), where) }
                layout.rows.forEach { row ->
                    if (row.hasInfo) {
                        assertEquals(SettingsAction.ToggleInfo(row.row), press(layout, row.info), "$where ${row.row} info")
                    } else {
                        // Key rows have no (!) of their own (Reset keys explains the group).
                        assertEquals(SettingsControl.KEYBIND, row.control, "$where ${row.row}")
                        assertEquals(0f, row.info.width)
                    }
                    when (row.control) {
                        SettingsControl.SEGMENTED -> {
                            assertEquals(row.row.optionCount(platform), row.options.size, "$where ${row.row}")
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
                        SettingsControl.KEYBIND -> assertEquals(
                            SettingsAction.BeginCapture(assertNotNull(row.row.boundAction)), press(layout, assertNotNull(row.key)), "$where ${row.row}",
                        )
                        SettingsControl.READOUT -> assertNull(press(layout, assertNotNull(row.readout)), "$where a readout has no control")
                        SettingsControl.ACTION -> {
                            val button = assertNotNull(row.action)
                            assertEquals(SettingsAction.ArmReset, press(layout, button), where)
                            assertEquals(SettingsAction.ConfirmReset, resolveSettingsPress(layout, button.center.x, button.center.y, resetArmed = true), where)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun geometryStaysInsideTheViewportWithoutOverlapsAndMeetsTouchMinimums() {
        for ((width, height, density) in viewports) for (platform in testPlatforms) for (group in platform.groups) for (language in AppLanguage.entries) {
            val maxPage = layout(width, height, density, group, 0, language, platform = platform).maxPage
            for (page in 0..maxPage) {
                val layout = layout(width, height, density, group, page, language, platform = platform)
                val where = "$width x $height @$density $group p$page $language ${platform.support}"
                val screen = layout.bounds
                (listOfNotNull(layout.back, layout.title) + layout.tabs.map { it.bounds } + layout.pages).forEach {
                    assertTrue(it.inside(screen), "$where header/tab/pager $it")
                }
                layout.tabs.forEachIndexed { index, a ->
                    layout.tabs.drop(index + 1).forEach { b -> assertTrue(!a.bounds.overlaps(b.bounds), "$where tabs overlap") }
                    layout.rows.forEach { row -> assertTrue(!a.bounds.overlaps(row.bounds), "$where tab ${a.group} covers ${row.row}") }
                }
                // Rows follow each other down their column; columns never overlap.
                layout.rows.groupBy { it.bounds.left }.values.forEach { column ->
                    column.zipWithNext().forEach { (a, b) -> assertTrue(a.bounds.bottom <= b.bounds.top, "$where rows overlap") }
                }
                layout.rows.forEachIndexed { index, a ->
                    layout.rows.drop(index + 1).forEach { b -> assertTrue(!a.bounds.overlaps(b.bounds), "$where ${a.row} overlaps ${b.row}") }
                }
                layout.rows.forEach { row ->
                    val parts = listOfNotNull(
                        row.info, row.toggle, row.decrease, row.track, row.increase, row.output, row.key, row.readout, row.action,
                    ) + row.options
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
        // With Reaction effects (0.5.0) Graphics has 8 rows: 8 × 44 + 7 × 4 = 380 dp exceed the 366 dp of
        // the reference phone, so it pages there too; the desktop still shows it on one page.
        assertEquals(1, layout(844f, 390f, 1f, SettingsGroup.GRAPHICS).maxPage)
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
                for (row in layout.rows.filter { it.hasInfo }.map { it.row }) for (bodyDp in listOf(40f, 90f, 150f)) {
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

    @Test
    fun sixTabsFitTheirRowColumnOrGridAtEverySize() {
        for ((width, height) in listOf(720f to 360f, 640f to 320f, 390f to 844f, 1000f to 700f, 1440f to 810f, 900f to 560f)) {
            for (language in AppLanguage.entries) for (scale in listOf(1f, 1.4f)) {
                val layout = layout(width, height, 1f, SettingsGroup.CONTROLS, 0, language, scale)
                val where = "$width x $height $language x$scale"
                assertEquals(SettingsGroup.entries, layout.tabs.map { it.group }, where)
                assertTrue(layout.tabs.all { it.bounds.inside(layout.bounds) }, where)
                layout.tabs.forEachIndexed { index, a ->
                    layout.tabs.drop(index + 1).forEach { b -> assertTrue(!a.bounds.overlaps(b.bounds), "$where tabs overlap") }
                }
                // Each label fits its tab at the layout's label factor (with the tab's own padding).
                val padding = if (layout.mode == SettingsLayoutMode.COMPACT_PORTRAIT) 20f else 40f
                layout.tabs.forEach { tab ->
                    val text = stubSettingsMetrics(1f, scale).width(language.text(tab.group.label), SettingsTextKind.TAB)
                    if (layout.tabScale > 0.7f + 0.001f || layout.mode == SettingsLayoutMode.REGULAR) {
                        assertTrue(text * layout.tabScale + padding <= tab.bounds.width + 0.5f, "$where ${tab.group} label")
                    }
                }
                if (layout.mode != SettingsLayoutMode.COMPACT_LANDSCAPE) {
                    val rowsTop = layout.rows.minOf { it.bounds.top }
                    assertTrue(layout.tabs.all { it.bounds.bottom <= rowsTop }, "$where tabs end above the rows")
                } else {
                    assertTrue(layout.tabs.all { it.bounds.right <= layout.rowsLeft }, "$where tabs sit beside the rows")
                }
            }
        }
        // Landscape drops the title: the side column starts 10 dp under Back, tabs are 32..42 dp with 4 dp gaps.
        val landscape = layout(720f, 360f, 1f, SettingsGroup.GAME)
        assertNull(landscape.title)
        assertEquals(60f, landscape.tabs.first().bounds.top)
        assertEquals(42f, landscape.tabs.first().bounds.height)
        // 60 + 6 · 42 + 5 · 4 = 332 ≤ 348 (the side column's end above the 12 dp margin).
        assertEquals(332f, landscape.tabs.last().bounds.bottom, 0.01f)
        assertEquals(38f, layout(640f, 320f, 1f).tabs.first().bounds.height, 0.01f)
        // Portrait: a 3 × 2 grid at 70 and 116 dp, rows from 176 dp.
        val portrait = layout(390f, 844f, 1f)
        assertEquals(listOf(70f, 70f, 70f, 116f, 116f, 116f), portrait.tabs.map { it.bounds.top })
        assertEquals((390f - 32f - 12f) / 3f, portrait.tabs.first().bounds.width, 0.01f)
        assertEquals(176f, portrait.rows.first().bounds.top)
        // Desktop: one row from x 50 at 104 dp, rows from 172 dp.
        val desktop = layout(1440f, 810f, 1f)
        assertTrue(desktop.tabs.all { it.bounds.top == 104f && it.bounds.height == 40f })
        assertEquals(50f, desktop.tabs.first().bounds.left)
        assertTrue(desktop.tabs.last().bounds.right <= desktop.rowsRight)
        assertEquals(172f, desktop.rows.first().bounds.top)
    }

    @Test
    fun desktopTabsShrinkTo80PercentAndThenWrapToASecondRow() {
        // Shrinks in 5 % steps: English at the largest text needs 95 % at 1000 dp.
        val shrunk = layout(1000f, 700f, 1f, scale = 1.4f)
        assertEquals(0.95f, shrunk.tabScale, 0.001f)
        assertTrue(shrunk.tabs.all { it.bounds.top == 104f })
        assertTrue(shrunk.tabs.last().bounds.right <= shrunk.rowsRight)
        assertEquals(172f, shrunk.rows.first().bounds.top)
        // Still too wide at 80 %: the tabs wrap to a second row at 150 dp and the rows start at 218 dp.
        val wrapped = layout(900f, 600f, 1f, scale = 1.6f)
        assertEquals(0.8f, wrapped.tabScale, 0.001f)
        val tops = wrapped.tabs.map { it.bounds.top }.distinct()
        assertEquals(listOf(104f, 150f), tops)
        assertEquals(50f, wrapped.tabs.first { it.bounds.top == 150f }.bounds.left)
        assertTrue(wrapped.tabs.all { it.bounds.right <= wrapped.rowsRight })
        assertEquals(218f, wrapped.rows.first().bounds.top)
    }

    @Test
    fun controlsSplitIntoTwoColumnsFrom600DpOfRowWidth() {
        // Landscape at 806 dp leaves 600.05 dp for the rows (a 143.95 dp side column); 0.1 dp less keeps one column.
        val two = layout(806f, 390f, 1f, SettingsGroup.CONTROLS)
        assertEquals(600f, two.rowsRight - two.rowsLeft, 0.1f)
        assertTrue(twoColumns(two))
        val one = layout(805.9f, 390f, 1f, SettingsGroup.CONTROLS)
        assertTrue(one.rowsRight - one.rowsLeft < 600f)
        assertTrue(!twoColumns(one))
        // Desktop: Dash..Pause on the left, Choice 1..Performance on the right, Reset keys across both, one page.
        val desktop = layout(1440f, 810f, 1f, SettingsGroup.CONTROLS)
        assertEquals(0, desktop.maxPage)
        val left = desktop.rows.filter { it.bounds.left == desktop.rowsLeft && it.bounds.right < desktop.rowsRight }.map { it.row }
        val right = desktop.rows.filter { it.bounds.right == desktop.rowsRight && it.bounds.left > desktop.rowsLeft }.map { it.row }
        assertEquals(SettingsGroup.CONTROLS.rows.subList(0, 7), left)
        assertEquals(SettingsGroup.CONTROLS.rows.subList(7, 14), right)
        val reset = assertNotNull(desktop.row(SettingsRow.RESET_BINDINGS))
        assertEquals(Rect(desktop.rowsLeft, 172f + 7 * 56f, desktop.rowsRight, 172f + 7 * 56f + 62f), reset.bounds)
        assertEquals(52f, desktop.row(SettingsRow.BIND_DASH)!!.bounds.height)
        assertEquals(24f, desktop.row(SettingsRow.BIND_CHOICE_1)!!.bounds.left - desktop.row(SettingsRow.BIND_DASH)!!.bounds.right, 0.01f)
        assertEquals(0, layout(1000f, 700f, 1f, SettingsGroup.CONTROLS).maxPage)
        // Phones: two pages in landscape (two columns) and in portrait (one column).
        val landscape = layout(844f, 390f, 1f, SettingsGroup.CONTROLS)
        assertTrue(twoColumns(landscape))
        assertEquals(1, landscape.maxPage)
        val portrait = layout(390f, 844f, 1f, SettingsGroup.CONTROLS)
        assertTrue(!twoColumns(portrait))
        assertEquals(1, portrait.maxPage)
        assertEquals(52f, portrait.row(SettingsRow.BIND_DASH)!!.bounds.height)
        assertEquals(44f, layout(720f, 360f, 1f, SettingsGroup.CONTROLS).row(SettingsRow.BIND_DASH)!!.bounds.height, 12.01f)
    }

    @Test
    fun keyCellsAreSizedPerModeAndKeyRowsHaveNoInfoOfTheirOwn() {
        val desktop = assertNotNull(layout(1440f, 810f, 1f, SettingsGroup.CONTROLS).row(SettingsRow.BIND_SKILL_1))
        assertEquals(132f, assertNotNull(desktop.key).width)
        assertEquals(34f, desktop.key!!.height)
        assertTrue(!desktop.hasInfo)
        val phone = assertNotNull(layout(390f, 844f, 1f, SettingsGroup.CONTROLS).row(SettingsRow.BIND_SKILL_1))
        assertEquals(112f, assertNotNull(phone.key).width)
        assertEquals(32f, phone.key!!.height)
        val reset = assertNotNull(layout(1440f, 810f, 1f, SettingsGroup.CONTROLS).row(SettingsRow.RESET_BINDINGS))
        assertTrue(reset.hasInfo)
        assertNotNull(reset.action)
    }

    @Test
    fun platformsShowOnlyTheirDisplayRowsAndHideAnEmptyControlsTab() {
        assertEquals(
            listOf(SettingsRow.FRAME_RATE_CAP, SettingsRow.INTERFACE_SCALE, SettingsRow.SHOW_FPS),
            SettingsPlatform.DEFAULT.rows(SettingsGroup.DISPLAY),
        )
        assertEquals(
            listOf(SettingsRow.WINDOW_MODE, SettingsRow.WINDOW_SIZE, SettingsRow.VSYNC, SettingsRow.FRAME_RATE_CAP,
                SettingsRow.REFRESH_RATE, SettingsRow.INTERFACE_SCALE, SettingsRow.SHOW_FPS),
            SettingsPlatform(DESKTOP_SUPPORT).rows(SettingsGroup.DISPLAY),
        )
        val android = SettingsPlatform(ANDROID_SUPPORT)
        assertEquals(
            listOf(SettingsRow.FRAME_RATE_CAP, SettingsRow.REFRESH_RATE, SettingsRow.IMMERSIVE, SettingsRow.INTERFACE_SCALE, SettingsRow.SHOW_FPS),
            android.rows(SettingsGroup.DISPLAY),
        )
        assertTrue(SettingsGroup.CONTROLS !in android.groups, "no hardware keyboard: no Controls tab")
        assertEquals(5, layout(390f, 844f, 1f, SettingsGroup.CONTROLS, platform = android).tabs.size)
        assertEquals(SettingsGroup.GAME, layout(390f, 844f, 1f, SettingsGroup.CONTROLS, platform = android).group, "a hidden tab falls back")
        assertEquals(
            listOf(SettingsRow.FULLSCREEN, SettingsRow.FRAME_RATE_CAP, SettingsRow.INTERFACE_SCALE, SettingsRow.SHOW_FPS),
            SettingsPlatform(WEB_SUPPORT).rows(SettingsGroup.DISPLAY),
        )
        // The refresh rate is chosen on Android and read out on desktop.
        assertEquals(SettingsControl.SEGMENTED, android.control(SettingsRow.REFRESH_RATE))
        assertEquals(SettingsControl.READOUT, SettingsPlatform(DESKTOP_SUPPORT).control(SettingsRow.REFRESH_RATE))
        val readout = assertNotNull(layout(1440f, 810f, 1f, SettingsGroup.DISPLAY, platform = SettingsPlatform(DESKTOP_SUPPORT)).row(SettingsRow.REFRESH_RATE))
        assertNotNull(readout.readout)
        assertTrue(readout.options.isEmpty())
    }

    private fun twoColumns(layout: SettingsLayout): Boolean = layout.rows.map { it.bounds.left }.distinct().size > 1

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
            SettingsTextKind.READOUT -> 16f
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
    platform: SettingsPlatform = SettingsPlatform.DEFAULT,
): SettingsLayout = settingsLayout(width, height, density, group, page, language, stubSettingsMetrics(density, scale), platform)

/** Platform descriptors as the three hosts report them. */
internal val DESKTOP_SUPPORT = SettingsDisplaySupport(
    windowModes = true, windowSizes = WindowSizePreset.entries.take(6), vsync = true, refreshReadout = true, monitorRefreshHz = 144,
)
internal val ANDROID_SUPPORT = SettingsDisplaySupport(
    refreshRatesHz = listOf(120, 90, 60), immersive = true, keyboard = false, touchPrimary = true,
)
internal val WEB_SUPPORT = SettingsDisplaySupport(browserFullscreen = true)

/** Every row kind on some platform: no display support, desktop, Android (two scales offered) and web. */
internal val testPlatforms: List<SettingsPlatform> = listOf(
    SettingsPlatform.DEFAULT,
    SettingsPlatform(DESKTOP_SUPPORT),
    SettingsPlatform(ANDROID_SUPPORT, interfaceScales = listOf(100, 115)),
    SettingsPlatform(WEB_SUPPORT),
)
