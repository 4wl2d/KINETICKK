// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.ball.profile.api.*
import kinetickk.ball.gameplay.api.*
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.WeaponId
import kinetickk.foundation.collections.immutableListOf
import kinetickk.flow.session.interaction.KeyboardSessionPort
import kinetickk.flow.session.interaction.KeyboardShell
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.interaction.codex.api.*
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class CodexComposeTest {
    @OptIn(ExperimentalComposeApi::class)
    @Test fun everyCatalogCategoryCanSwitchToSynergiesAndBack() {
        val previousLinkBufferFlag = ComposeRuntimeFlags.isLinkBufferComposerEnabled
        ComposeRuntimeFlags.isLinkBufferComposerEnabled = true
        try {
            runComposeUiTest {
                mainClock.autoAdvance = false
                setContent { TestCodex(1000, 700) }
                fun drawCurrentGrid() {
                    repeat(3) { mainClock.advanceTimeByFrame() }
                    waitForIdle()
                    onRoot().captureToImage()
                }
                drawCurrentGrid()
                for ((category, prefix) in listOf("item", "weapon", "relic", "shape").withIndex()) {
                    onNodeWithTag("codex-category-$category").performClick()
                    drawCurrentGrid()
                    assertTrue(onAllNodes(hasTestTagPrefix("codex-slot-$prefix/")).fetchSemanticsNodes().isNotEmpty())
                    onNodeWithTag("codex-tab-2").performClick()
                    drawCurrentGrid()
                    assertTrue(onAllNodes(hasTestTagPrefix("codex-slot-synergy/")).fetchSemanticsNodes().isNotEmpty())
                    onNodeWithTag("codex-tab-1").performClick()
                    drawCurrentGrid()
                    onNodeWithTag("codex-category-$category").assertIsSelected()
                    assertTrue(onAllNodes(hasTestTagPrefix("codex-slot-$prefix/")).fetchSemanticsNodes().isNotEmpty())
                }
            }
        } finally {
            ComposeRuntimeFlags.isLinkBufferComposerEnabled = previousLinkBufferFlag
        }
    }

    @Test fun catalogComposesOnlyVisibleSlotsAndLastOf400IsReachable() = runComposeUiTest {
        setContent { TestCodex(1000, 700) }
        val composed = onAllNodes(hasTestTagPrefix("codex-slot-item/")).fetchSemanticsNodes().size
        assertTrue(composed in 1..100, "Only viewport and prefetch should compose; got $composed")
        onNodeWithTag("codex-grid").performScrollToKey("item/399")
        onNodeWithTag("codex-slot-item/399").assertIsDisplayed().performClick()
        onNodeWithTag("codex-detail-title").assertTextEquals("Item 399")
        onNodeWithTag("codex-slot-item/399").assertIsDisplayed()
        onNodeWithTag("codex-sheet").assertDoesNotExist()
    }

    @Test fun sheetEscapeRestoresOriginFocusAndScrollThenClosesCodex() = runComposeUiTest {
        var closes = 0
        setContent { TestCodex(390, 720, onClose = { closes++ }) }
        onNodeWithTag("codex-grid").performScrollToKey("item/399")
        val originalBounds = onNodeWithTag("codex-slot-item/399").fetchSemanticsNode().boundsInRoot
        onNodeWithTag("codex-slot-item/399").performClick()
        onNodeWithTag("codex-sheet").assertExists()
        onNodeWithTag("codex-sheet-close").performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag("codex-sheet").assertDoesNotExist()
        onNodeWithTag("codex-slot-item/399").assertIsFocused()
        assertEquals(originalBounds, onNodeWithTag("codex-slot-item/399").fetchSemanticsNode().boundsInRoot)
        assertEquals(0, closes)
        onNodeWithTag("codex-slot-item/399").performKeyInput { pressKey(Key.Escape) }
        assertEquals(1, closes)
    }

    @Test fun closeButtonAndScrimDismissWithoutSelectingAnotherSlot() = runComposeUiTest {
        setContent { TestCodex(390, 720) }
        onNodeWithTag("codex-slot-item/0").performClick()
        onNodeWithTag("codex-sheet-close").performClick()
        onNodeWithTag("codex-slot-item/0").assertIsFocused()
        onNodeWithTag("codex-slot-item/0").performKeyInput { pressKey(Key.Enter) }
        onNodeWithTag("codex-sheet").assertExists()
        onNodeWithTag("codex-scrim").performTouchInput { click(topCenter) }
        onNodeWithTag("codex-sheet").assertDoesNotExist()
        onNodeWithTag("codex-slot-item/0").assertIsFocused()
    }

    @Test fun modalTrapsForwardAndReverseTabUntilClose() = runComposeUiTest {
        setContent { TestCodex(390, 720) }
        onNodeWithTag("codex-slot-item/0").performClick()
        repeat(8) {
            onNodeWithTag("codex-sheet-close").performKeyInput { pressKey(Key.Tab) }
            onAllNodes(isFocused() and (hasTestTag("codex-sheet-modal") or hasAnyAncestor(hasTestTag("codex-sheet-modal"))), useUnmergedTree = true).assertCountEquals(1)
        }
        repeat(8) {
            onNodeWithTag("codex-sheet-close").performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
            onAllNodes(isFocused() and (hasTestTag("codex-sheet-modal") or hasAnyAncestor(hasTestTag("codex-sheet-modal"))), useUnmergedTree = true).assertCountEquals(1)
        }
        onNodeWithTag("codex-sheet-close").performClick()
        onNodeWithTag("codex-slot-item/0").assertIsFocused()
    }

    @Test fun disappearingSheetOriginClosesSheetAndRestoresSearchFocus() = runComposeUiTest {
        val catalog = codexTestCatalog()
        var itemsValue by mutableStateOf(catalog.items)
        setContent {
            Box(Modifier.requiredSize(390.dp, 720.dp)) {
                CodexContent(catalog, CodexRenderModel((0 until 400).toImmutableSet(), CodexRunStacks(), itemsValue), codexTestProgress(), 1f) { }
            }
        }
        onNodeWithTag("codex-slot-item/0").performClick()
        onNodeWithTag("codex-sheet").assertExists()
        runOnIdle { itemsValue = catalog.items.drop(1).toImmutableList() }
        onNodeWithTag("codex-sheet").assertDoesNotExist()
        onNodeWithTag("codex-search").assertIsFocused()
        onNodeWithTag("codex-slot-item/0").assertDoesNotExist()
    }

    @Test fun sidePanelHoverAndKeyboardFocusTemporarilyPreviewPinnedEntry() = runComposeUiTest {
        setContent { TestCodex(1000, 700) }
        onNodeWithTag("codex-slot-item/0").performClick()
        onNodeWithTag("codex-slot-item/1").performMouseInput { enter(center); moveTo(center) }
        onNodeWithTag("codex-detail-title").assertTextEquals("Item 1")
        // A new keyboard focus takes over even while the pointer remains in the old slot.
        onNodeWithTag("codex-slot-item/2").performSemanticsAction(SemanticsActions.RequestFocus)
        onNodeWithTag("codex-detail-title").assertTextEquals("Item 2")
        onNodeWithTag("codex-search").performClick()
        onNodeWithTag("codex-detail-title").assertTextEquals("Item 0")
    }

    @Test fun changingResultsResetsGridAndClearsOnlyDisappearedSelection() = runComposeUiTest {
        setContent { TestCodex(1000, 700) }
        onNodeWithTag("codex-grid").performScrollToKey("item/399")
        onNodeWithTag("codex-slot-item/399").performClick()
        onNodeWithTag("codex-search").performClick().performTextInput("ITEM 39")
        onNodeWithTag("codex-detail-title").assertTextEquals("Item 399")
        onNodeWithTag("codex-slot-item/39").assertIsDisplayed()
        onNodeWithTag("codex-search").performTextReplacement("Item 2")
        onNodeWithTag("codex-detail-title").assertDoesNotExist()
        onNodeWithTag("codex-slot-item/2").assertIsDisplayed()
        onNodeWithTag("codex-search").performTextReplacement("zzzzzzz")
        onNodeWithTag("codex-empty-EMPTY_SEARCH").assertIsDisplayed()
    }

    @Test fun searchInputAcceptsHotLettersUnderRealShellAndTwoEscAreOrdered() = runComposeUiTest {
        val port = KeyboardSessionPort()
        setContent {
            Box(Modifier.requiredSize(390.dp, 720.dp)) {
                KeyboardShell(port, object : CodexFeature {
                    @Composable override fun Content(runStacks: CodexRunStacks, onOutput: (CodexOutput) -> Unit) {
                        val catalog = remember { codexTestCatalog() }
                        CodexContent(catalog, CodexRenderModel((0 until 400).toImmutableSet(), runStacks, catalog.items), codexTestProgress(), 1f) { onOutput(CodexOutput.Back) }
                    }
                })
            }
        }
        onNodeWithTag("codex-search").performClick()
        listOf(Key.S, Key.L, Key.A, Key.B, Key.C, Key.I, Key.M).forEach { key ->
            onNodeWithTag("codex-search").performKeyInput { pressKey(key) }
        }
        // Desktop key presses and typed text are separate platform events.
        onNodeWithTag("codex-search").performTextInput("slabcim")
        onNodeWithTag("codex-search").assertTextEquals("slabcim")
        assertTrue(port.pulses.isEmpty())
        onNodeWithTag("codex-search").performTextReplacement("")
        onNodeWithTag("codex-slot-item/0").performClick()
        onNodeWithTag("codex-sheet-close").performKeyInput { pressKey(Key.Escape) }
        assertTrue(port.pulses.isEmpty())
        onNodeWithTag("codex-slot-item/0").performKeyInput { pressKey(Key.Escape) }
        assertEquals(listOf<SessionInteractionPulse>(SessionInteractionPulse.CloseOverlay), port.pulses)
        onRoot().performKeyInput { pressKey(Key.C) }
        assertEquals(
            SessionInteractionPulse.ShortcutObserved(kinetickk.flow.session.api.SessionShortcut.CODEX),
            port.pulses.last(),
        )
    }

    @Test fun saveAndRestoreRetainsSearchFilterCategoryPinAndGridScroll() = runComposeUiTest {
        val restoration = CodexRestorationHarness(this)
        val catalog = codexTestCatalog()
        restoration.setContent {
            Box(Modifier.requiredSize(1000.dp, 700.dp)) {
                CodexContent(catalog, CodexRenderModel((0 until 400).toSet().toImmutableSet(), CodexRunStacks(), catalog.items, discoveredRelicIds = kinetickk.ball.content.api.RelicId.entries.toImmutableSet()), codexTestProgress(), 1f) { }
            }
        }
        onNodeWithTag("codex-search").performClick().performTextInput("Item")
        onNodeWithTag("codex-filter-0").performClick()
        onNodeWithTag("codex-grid").performScrollToKey("item/399")
        onNodeWithTag("codex-slot-item/399").performClick()
        val bounds = onNodeWithTag("codex-slot-item/399").fetchSemanticsNode().boundsInRoot
        restoration.emulateSaveAndRestore()
        onNodeWithTag("codex-tab-1").assertIsSelected()
        onNodeWithTag("codex-search").assertTextEquals("Item")
        onNodeWithTag("codex-filter-0").assertIsSelected()
        onNodeWithTag("codex-detail-title").assertTextEquals("Item 399")
        assertEquals(bounds, onNodeWithTag("codex-slot-item/399").fetchSemanticsNode().boundsInRoot)
        onNodeWithTag("codex-category-2").performClick()
        onNodeWithTag("codex-search").performTextClearance()
        onNodeWithTag("codex-grid").performScrollToKey("relic/ENGINE_OF_PARADOX")
        restoration.emulateSaveAndRestore()
        onNodeWithTag("codex-category-2").assertIsSelected()
        onNodeWithTag("codex-slot-relic/ENGINE_OF_PARADOX").assertIsDisplayed()
    }

    @Test fun buildExpansionAndSelectedStatSourcesRestoreWithoutExpandingEveryStat() = runComposeUiTest {
        val restoration = CodexRestorationHarness(this)
        val catalog = codexTestCatalog()
        val build = GameplayBuildSummaryProjection(
            GameplayInstanceId(RunId(1)), GameplayRevision.ZERO, immutableListOf(),
            character = CoreShape.ORB, weapon = WeaponId.FLUX_WAKE, weaponLevel = 3,
            stats = listOf("Impact", "Cooling").map { name -> BuildStatSummary(name, 5f, "%",
                immutableListOf(BuildStatContribution(BuildStatSource.LAB, 2f))) }.toImmutableList(),
        )
        restoration.setContent {
            Box(Modifier.requiredSize(1000.dp, 700.dp)) {
                CodexContent(catalog, CodexRenderModel(immutableSetOf(), CodexRunStacks(build = build), catalog.items), codexTestProgress(), 1f) { }
            }
        }
        onNodeWithTag("codex-grid").performScrollToKey("stats-heading")
        onNodeWithTag("codex-stats").performClick()
        onNodeWithTag("codex-grid").performScrollToKey("stat/Impact")
        onNodeWithTag("codex-stat-Impact").performClick()
        onAllNodesWithText("Lab / Rebirth  2.0%").assertCountEquals(1)
        restoration.emulateSaveAndRestore()
        onNodeWithTag("codex-stats").assertIsSelected()
        onNodeWithTag("codex-stat-Impact").assertIsSelected()
        onNodeWithTag("codex-stat-Cooling").assertIsNotSelected()
        onAllNodesWithText("Lab / Rebirth  2.0%").assertCountEquals(1)
    }

    @Test fun phonePortraitLandscapeAndDesktopKeepGridAndSheetUsableAtEveryTextScale() = runComposeUiTest {
        var widthValue by mutableIntStateOf(390)
        var heightValue by mutableIntStateOf(720)
        var scaleValue by mutableFloatStateOf(1f)
        setContent { TestCodex(widthValue, heightValue, scaleValue) }
        for ((width, height) in listOf(390 to 720, 780 to 320, 1000 to 700)) {
            for (scale in listOf(1f, 1.25f, 1.75f)) {
                runOnIdle { widthValue = width; heightValue = height; scaleValue = scale }
                onNodeWithTag("codex-slot-item/0").assertIsDisplayed()
                assertTrue(onNodeWithTag("codex-grid").fetchSemanticsNode().boundsInRoot.height >= 80f)
                saveCapture("catalog-$width-$height-$scale")
                onNodeWithTag("codex-slot-item/0").performClick()
                onNodeWithTag("codex-detail-title").assertTextEquals("Item 0")
                saveCapture("details-$width-$height-$scale")
                if (width < 900 || height < 480) {
                    onNodeWithTag("codex-sheet-close").assertIsDisplayed().performClick()
                    onNodeWithTag("codex-slot-item/0").assertIsFocused()
                }
            }
        }
    }

    @Test fun responsiveBoundaryRetainsPinnedRecordAcrossOrientationAndTextScales() = runComposeUiTest {
        var widthValue by mutableIntStateOf(900)
        var heightValue by mutableIntStateOf(480)
        var scaleValue by mutableFloatStateOf(1f)
        setContent { TestCodex(widthValue, heightValue, scaleValue) }
        onNodeWithTag("codex-side-panel").assertExists()
        onNodeWithTag("codex-slot-item/0").performClick()
        for (scale in listOf(1f, 1.25f, 1.75f)) {
            runOnIdle { widthValue = 899; heightValue = 480; scaleValue = scale }
            onNodeWithTag("codex-sheet").assertExists()
            onNodeWithTag("codex-sheet-close").assertIsDisplayed()
            onNodeWithTag("codex-detail-title").assertTextEquals("Item 0")
            runOnIdle { widthValue = 900; heightValue = 479 }
            onNodeWithTag("codex-sheet").assertExists()
            runOnIdle { widthValue = 900; heightValue = 480 }
            onNodeWithTag("codex-side-panel").assertExists()
            onNodeWithTag("codex-sheet").assertDoesNotExist()
            onNodeWithTag("codex-detail-title").assertTextEquals("Item 0")
        }
    }

    @Test fun tabRowsFitThePhoneWidthInBothLanguagesAtEveryTextScale() {
        for (language in AppLanguage.entries) runComposeUiTest {
            var widthValue by mutableIntStateOf(390)
            var heightValue by mutableIntStateOf(844)
            var scaleValue by mutableFloatStateOf(1f)
            setContent {
                CompositionLocalProvider(LocalAppLanguage provides language) {
                    Box(Modifier.requiredSize(widthValue.dp, heightValue.dp)) {
                        val catalog = remember { codexTestCatalog() }
                        CodexContent(catalog, CodexRenderModel((0 until 400).toImmutableSet(), CodexRunStacks(), catalog.items), codexTestProgress(), scaleValue) { }
                    }
                }
            }
            for ((width, height) in listOf(390 to 844, 360 to 800, 844 to 390, 1440 to 810)) for (scale in listOf(1f, 1.25f, 1.75f)) {
                runOnIdle { widthValue = width; heightValue = height; scaleValue = scale }
                val where = "${width}x$height ${language.code} @$scale"
                // Compare with the Codex box itself (not the larger test window), using unclipped bounds:
                // a scrolling row clips its tabs, so clipped bounds could never leave it.
                val box = onNodeWithTag("codex").getUnclippedBoundsInRoot()
                // Wide screens list the categories as nav rows; every tab row present must fit unscrolled.
                val rows = onAllNodes(hasTestTag("codex-tab-row") or hasTestTag("codex-category-row")).fetchSemanticsNodes()
                assertTrue(rows.isNotEmpty(), where)
                rows.forEach { row ->
                    val range = row.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)
                    assertTrue(range == null || range.maxValue() == 0f, "${row.config[SemanticsProperties.TestTag]} scrolls at rest at $where")
                }
                ((0..2).map { "codex-tab-$it" } + (0..3).map { "codex-category-$it" } + listOf("codex-filter-0", "codex-filter-2"))
                    .forEach { tag ->
                        val bounds = onNodeWithTag(tag).getUnclippedBoundsInRoot()
                        assertTrue(bounds.left >= box.left - 0.5.dp && bounds.right <= box.right + 0.5.dp,
                            "$tag leaves the Codex at $where: $bounds in $box")
                        assertTrue(bounds.width > 0.dp, "$tag is hidden at $where")
                    }
            }
        }
    }

    @Test fun aRowTooWideEvenAtItsSmallestScaleScrollsToEveryTab() = runComposeUiTest {
        // 300 dp at 175 % in Russian: the category row cannot fit at 60 %, so it scrolls instead.
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.Russian) {
                Box(Modifier.requiredSize(300.dp, 700.dp)) {
                    val catalog = remember { codexTestCatalog() }
                    CodexContent(catalog, CodexRenderModel((0 until 400).toImmutableSet(), CodexRunStacks(), catalog.items), codexTestProgress(), 1.75f) { }
                }
            }
        }
        val box = onNodeWithTag("codex").getUnclippedBoundsInRoot()
        val row = "codex-category-row"
        val range = requireNotNull(onNodeWithTag(row).fetchSemanticsNode().config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)) {
            "the overflowing row must scroll"
        }
        assertTrue(range.maxValue() > 0f && range.value() == 0f)
        assertTrue(onNodeWithTag("codex-category-3").getUnclippedBoundsInRoot().right > box.right, "the last tab starts beyond the edge")
        onNodeWithTag("codex-category-3").performScrollTo().performClick()
        onNodeWithTag("codex-category-3").assertIsSelected()
        val shown = onNodeWithTag("codex-category-3").getUnclippedBoundsInRoot()
        assertTrue(shown.left >= box.left - 0.5.dp && shown.right <= box.right + 0.5.dp, "scrolling brings the last tab in: $shown in $box")
    }

    @Test fun buildTabBadgesAndNewStampsLeaveTheCellGlyphClear() {
        val catalog = codexTestCatalog()
        val stacks = List(400) { if (it == 0 || it == 1) 1 else 0 }.toImmutableList()
        val build = GameplayBuildSummaryProjection(
            GameplayInstanceId(RunId(1)), GameplayRevision.ZERO, stacks,
            character = CoreShape.ORB, weapon = WeaponId.FLUX_WAKE, weaponLevel = 7,
            relics = immutableListOf(kinetickk.ball.content.api.EquippedRelic(kinetickk.ball.content.api.RelicId.KINETIC_FLYWHEEL, 2)),
        )
        // Item 0 was acquired this run and is new: NEW stamp and stack badge share the band above the glyph.
        val model = CodexRenderModel(immutableSetOf(), CodexRunStacks(stacks, build), catalog.items, newItemIds = immutableSetOf(0))
        val keys = listOf("item/0", "item/1", "relic/KINETIC_FLYWHEEL", "weapon/FLUX_WAKE")
        for (language in AppLanguage.entries) for ((width, height) in listOf(1440 to 810, 390 to 844, 844 to 390)) for (scale in listOf(1f, 1.25f, 1.75f)) androidx.compose.ui.test.v2.runDesktopComposeUiTest(width, height) {
            setContent {
                CompositionLocalProvider(LocalAppLanguage provides language, androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f, 1f)) {
                    Box(Modifier.requiredSize(width.dp, height.dp)) { CodexContent(catalog, model, codexTestProgress(), scale) { } }
                }
            }
            onNodeWithTag("codex-tab-0").assertIsSelected()
            // The Build tab, then the collection grid with the same in-run items.
            (keys.map { it to true } + listOf("item/0", "item/1").map { it to false }).forEach { (key, buildTab) ->
                if (!buildTab) onNodeWithTag("codex-tab-1").performSemanticsAction(SemanticsActions.OnClick)
                val where = "$key ${if (buildTab) "build" else "collection"} ${width}x$height ${language.code} @$scale"
                onNodeWithTag("codex-grid").performScrollToKey(key)
                val cell = onNodeWithTag("codex-slot-$key").fetchSemanticsNode().boundsInRoot
                val badge = onNodeWithTag("codex-badge-$key", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val stamp = onAllNodesWithTag("codex-new-$key", useUnmergedTree = true).fetchSemanticsNodes().singleOrNull()?.boundsInRoot
                assertEquals(key == "item/0", stamp != null, where)
                // One glyph layout for the whole grid, from the grid's stamp size (the new item's stamp).
                onNodeWithTag("codex-grid").performScrollToKey("item/0")
                val gridStamp = onNodeWithTag("codex-new-item/0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                onNodeWithTag("codex-grid").performScrollToKey(key)
                val ui = codexUiScale(scale)
                val band = codexMarkBand(androidx.compose.ui.geometry.Size(gridStamp.width, gridStamp.height), 10f / ui, codexBadgeFont(ui), 1f)
                val glyph = codexCellGlyph(cell.width, band, 1f)
                val center = androidx.compose.ui.geometry.Offset(cell.left + glyph.centerX, cell.top + glyph.centerY)
                val reach = glyph.radius * CODEX_GLYPH_REACH
                if (key.startsWith("item/")) {
                    // Drawn pixels: the 1 px strips just left of and just below the badge plate keep the
                    // cell's plain face, so no glyph ink runs under the badge.
                    val pixels = onRoot().captureToImage().toPixelMap()
                    val face = pixels[(cell.left + 2f).toInt(), (cell.bottom - 2f).toInt()]
                    val strips = (badge.top.toInt() + 1 until badge.bottom.toInt() - 1).map { (badge.left - 1f).toInt() to it } +
                        (badge.left.toInt() + 1 until badge.right.toInt() - 1).map { it to (badge.bottom + 1f).toInt() }
                    strips.forEach { (x, y) ->
                        val pixel = pixels[x, y]
                        val delta = maxOf(kotlin.math.abs(pixel.red - face.red), kotlin.math.abs(pixel.green - face.green), kotlin.math.abs(pixel.blue - face.blue))
                        assertTrue(delta < 0.05f, "glyph or stamp ink at $x,$y next to the badge ($where)")
                    }
                }
                // The glyph with its stack ring stays clear of the badge plate...
                val nearest = androidx.compose.ui.geometry.Offset(center.x.coerceIn(badge.left, badge.right), center.y.coerceIn(badge.top, badge.bottom))
                assertTrue((nearest - center).getDistance() >= reach - 0.5f, "glyph (reach $reach at $center) runs under the badge $badge ($where)")
                // ...and below the rotated NEW stamp with its shadow; the stamp ends before the badge.
                stamp?.let {
                    assertTrue(it.bottom + it.width * 0.5f * 0.1045f <= center.y - reach + 0.5f, "stamp covers the glyph ($where)")
                    assertTrue(it.right + it.height * 0.1045f + 3f * 10f / ui / 17f <= badge.left, "stamp $it runs into the badge $badge ($where)")
                }
                assertTrue(center.x - reach >= cell.left - 0.5f && center.x + reach <= cell.right + 0.5f && center.y + reach <= cell.bottom + 0.5f,
                    "glyph leaves the cell ($where)")
                // Every glyph, Build tab or collection, is at least as large as a 64 dp cell's centered glyph.
                assertTrue(glyph.radius >= 64f * 0.36f, "glyph radius ${glyph.radius} at $where")
                assertTrue(badge.bottom <= cell.bottom && badge.right <= cell.right && badge.top >= cell.top, "badge leaves the cell ($where)")
            }
        }
    }

    @Test fun newStampSitsInItsOwnBandAboveTheCellGlyph() = runComposeUiTest {
        val catalog = codexTestCatalog()
        setContent {
            Box(Modifier.requiredSize(1000.dp, 700.dp)) {
                CompositionLocalProvider(LocalAppLanguage provides AppLanguage.Russian) {
                    CodexContent(catalog, CodexRenderModel((0 until 400).toImmutableSet(), CodexRunStacks(), catalog.items,
                        newItemIds = immutableSetOf(0)), codexTestProgress(), 1.75f) { }
                }
            }
        }
        val cell = onNodeWithTag("codex-slot-item/0").fetchSemanticsNode().boundsInRoot
        val stamp = onNodeWithTag("codex-new-item/0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(stamp.left >= cell.left && stamp.right <= cell.right, "The stamp stays inside its cell: $stamp in $cell")
        val ui = codexUiScale(1.75f)
        val band = codexMarkBand(androidx.compose.ui.geometry.Size(stamp.width, stamp.height), 10f / ui, codexBadgeFont(ui), 1f)
        val (centerY, radius) = codexCellGlyph(cell.width, band, 1f)
        // Rotated −6°, the stamp's lowest corner sits sin(6°) × width / 2 below its box.
        val stampBottom = stamp.bottom + stamp.width * 0.5f * 0.1045f
        assertTrue(stampBottom <= cell.top + centerY - radius, "stamp bottom $stampBottom covers the glyph from ${cell.top + centerY - radius}")
        assertTrue(radius >= cell.width * 0.25f, "The glyph stays readable")
    }

    @Test fun synergyRowsOnPhonesNeverSqueezeTagsIntoLetterColumns() = runComposeUiTest {
        val catalog = codexTestCatalog()
        setContent {
            Box(Modifier.requiredSize(390.dp, 844.dp)) {
                CompositionLocalProvider(LocalAppLanguage provides AppLanguage.Russian) {
                    CodexContent(catalog, CodexRenderModel((0 until 400).toImmutableSet(), CodexRunStacks(), catalog.items,
                        discoveredRelicIds = kinetickk.ball.content.api.RelicId.entries.toImmutableSet()), codexTestProgress(), 1.25f) { }
                }
            }
        }
        onNodeWithTag("codex-tab-2").performClick()
        val rows = onAllNodes(hasTestTagPrefix("codex-slot-synergy/")).fetchSemanticsNodes()
        assertTrue(rows.isNotEmpty())
        rows.forEach { row ->
            // Title (up to two lines at a fitted size), a tag line and the diagram: letter-by-letter
            // wrapping made these rows several times taller.
            assertTrue(row.boundsInRoot.height <= 150f, "${row.config.getOrElse(SemanticsProperties.TestTag) { "" }} is ${row.boundsInRoot.height} dp tall")
        }
    }

    @Test fun searchPasteIsBoundedAndNoRunInventoryAndFilterStatesAreDistinct() = runComposeUiTest {
        setContent { TestCodex(1000, 700, discovered = false) }
        onNodeWithTag("codex-filter-2").assertIsNotEnabled()
        onNodeWithTag("codex-tab-0").performClick()
        onNodeWithTag("codex-empty-NO_RUN").assertIsDisplayed()
        onNodeWithTag("codex-tab-1").performClick()
        onNodeWithTag("codex-filter-0").performClick()
        onNodeWithTag("codex-empty-EMPTY_INVENTORY").assertIsDisplayed()
        onNodeWithTag("codex-search").performTextInput("a".repeat(129))
        onNodeWithTag("codex-search").assertTextEquals("a".repeat(128))
        onNodeWithTag("codex-empty-EMPTY_SEARCH").assertIsDisplayed()
    }
}

@Composable
private fun TestCodex(width: Int, height: Int, scale: Float = 1f, discovered: Boolean = true, onClose: () -> Unit = {}) {
    val catalog = remember { codexTestCatalog() }
    Box(Modifier.requiredSize(width.dp, height.dp)) {
        CompositionLocalProvider(LocalAppLanguage provides AppLanguage.English) {
            CodexContent(catalog, CodexRenderModel(if (discovered) (0 until 400).toImmutableSet() else immutableSetOf(), CodexRunStacks(), catalog.items, discoveredRelicIds = if (discovered) kinetickk.ball.content.api.RelicId.entries.toImmutableSet() else immutableSetOf()), codexTestProgress(), scale, onClose = onClose)
        }
    }
}

private fun hasTestTagPrefix(prefix: String): SemanticsMatcher = SemanticsMatcher("Tag starts with $prefix") {
    it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith(prefix)
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.saveCapture(name: String) {
    val bitmap = onRoot().captureToImage()
    val pixels = bitmap.toPixelMap()
    val image = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) image.setRGB(x, y, pixels[x, y].toArgb())
    val folder = File("/tmp/kinetickk-codex-captures").apply { mkdirs() }
    ImageIO.write(image, "png", File(folder, "$name.png"))
}

/** The upstream Desktop StateRestorationTester currently has a TODO in platformEncodeDecode.
 * Exercise the same Compose registry save/dispose/recreate boundary without claiming Android Bundle coverage. */
@OptIn(ExperimentalTestApi::class)
private class CodexRestorationHarness(private val test: ComposeUiTest) {
    private var emitsValue by mutableStateOf(true)
    private var registryValue by mutableStateOf(SaveableStateRegistry(null) { true })
    fun setContent(content: @Composable () -> Unit) {
        test.setContent {
            if (emitsValue) CompositionLocalProvider(LocalSaveableStateRegistry provides registryValue, LocalAppLanguage provides AppLanguage.English) { content() }
        }
    }
    fun emulateSaveAndRestore() {
        val saved = test.runOnIdle { registryValue.performSave().also { emitsValue = false } }
        test.waitForIdle()
        test.runOnIdle {
            registryValue = SaveableStateRegistry(saved) { true }
            emitsValue = true
        }
        test.waitForIdle()
    }
}
