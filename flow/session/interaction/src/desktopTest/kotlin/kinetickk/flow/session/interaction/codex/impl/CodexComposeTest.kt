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
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.toArgb
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.flow.session.interaction.localization.CodexText
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.KkRolePalette
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
    @Test fun everyCatalogCategoryCanSwitchToTheBuildSystemTabsAndBack() {
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
                    // The Items matrix regroups under every sort before the tabs switch.
                    if (category == 0) for (sort in listOf(1, 2, 0)) {
                        onNodeWithTag("codex-sort-$sort").performClick()
                        drawCurrentGrid()
                        onNodeWithTag("codex-sort-$sort").assertIsSelected()
                        assertTrue(onAllNodes(hasTestTagPrefix("codex-slot-item/")).fetchSemanticsNodes().isNotEmpty(), "sort $sort")
                    }
                    for ((tab, tabPrefix) in listOf(2 to "break", 3 to "pairing", 4 to "tag")) {
                        onNodeWithTag("codex-tab-$tab").performClick()
                        drawCurrentGrid()
                        assertTrue(onAllNodes(hasTestTagPrefix("codex-slot-$tabPrefix/")).fetchSemanticsNodes().isNotEmpty(), "tab $tab")
                    }
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

    @Test fun catalogComposesOnlyVisibleSlotsAndTheLastItemIsReachable() = runComposeUiTest {
        // On a phone the matrix's rows wrap and run below the screen: only the cells in view (and prefetch) compose.
        setContent { TestCodex(390, 720) }
        val composed = onAllNodes(hasTestTagPrefix("codex-slot-item/")).fetchSemanticsNodes().size
        assertTrue(composed in 1..60, "Only viewport and prefetch should compose; got $composed")
        onNodeWithTag("codex-slot-item/79").assertDoesNotExist()
        onNodeWithTag("codex-grid").performScrollToKey("item/79")
        onNodeWithTag("codex-slot-item/79").assertIsDisplayed().performClick()
        onNodeWithTag("codex-sheet").assertExists()
        onNodeWithTag("codex-detail-title").assertTextEquals("Item 79")
    }

    @Test fun sheetEscapeRestoresOriginFocusAndScrollThenClosesCodex() = runComposeUiTest {
        var closes = 0
        setContent { TestCodex(390, 720, onClose = { closes++ }) }
        onNodeWithTag("codex-grid").performScrollToKey("item/79")
        val originalBounds = onNodeWithTag("codex-slot-item/79").fetchSemanticsNode().boundsInRoot
        onNodeWithTag("codex-slot-item/79").performClick()
        onNodeWithTag("codex-sheet").assertExists()
        onNodeWithTag("codex-sheet-close").performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag("codex-sheet").assertDoesNotExist()
        onNodeWithTag("codex-slot-item/79").assertIsFocused()
        assertEquals(originalBounds, onNodeWithTag("codex-slot-item/79").fetchSemanticsNode().boundsInRoot)
        assertEquals(0, closes)
        onNodeWithTag("codex-slot-item/79").performKeyInput { pressKey(Key.Escape) }
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
                CodexContent(catalog, CodexRenderModel((0 until 80).toImmutableSet(), CodexRunStacks(), itemsValue), codexTestProgress(), 1f) { }
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
        onNodeWithTag("codex-grid").performScrollToKey("item/79")
        onNodeWithTag("codex-slot-item/79").performClick()
        onNodeWithTag("codex-search").performClick().performTextInput("ITEM 7")
        onNodeWithTag("codex-detail-title").assertTextEquals("Item 79")
        onNodeWithTag("codex-slot-item/7").assertIsDisplayed()
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
                        CodexContent(catalog, CodexRenderModel((0 until 80).toImmutableSet(), runStacks, catalog.items), codexTestProgress(), 1f) { onOutput(CodexOutput.Back) }
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
                CodexContent(catalog, CodexRenderModel((0 until 80).toSet().toImmutableSet(), CodexRunStacks(), catalog.items, discoveredRelicIds = kinetickk.ball.content.api.RelicId.entries.toImmutableSet()), codexTestProgress(), 1f) { }
            }
        }
        onNodeWithTag("codex-search").performClick().performTextInput("Item")
        onNodeWithTag("codex-filter-0").performClick()
        onNodeWithTag("codex-sort-2").performClick()
        onNodeWithTag("codex-grid").performScrollToKey("item/79")
        onNodeWithTag("codex-slot-item/79").performClick()
        val bounds = onNodeWithTag("codex-slot-item/79").fetchSemanticsNode().boundsInRoot
        restoration.emulateSaveAndRestore()
        onNodeWithTag("codex-tab-1").assertIsSelected()
        onNodeWithTag("codex-search").assertTextEquals("Item")
        onNodeWithTag("codex-filter-0").assertIsSelected()
        onNodeWithTag("codex-sort-2").assertIsSelected()
        onNodeWithTag("codex-items-row-rarity/COMMON").assertExists()
        onNodeWithTag("codex-detail-title").assertTextEquals("Item 79")
        assertEquals(bounds, onNodeWithTag("codex-slot-item/79").fetchSemanticsNode().boundsInRoot)
        onNodeWithTag("codex-category-2").performClick()
        onNodeWithTag("codex-search").performTextClearance()
        onNodeWithTag("codex-grid").performScrollToKey("relic/OUROBOROS_SEAL")
        restoration.emulateSaveAndRestore()
        onNodeWithTag("codex-category-2").assertIsSelected()
        onNodeWithTag("codex-slot-relic/OUROBOROS_SEAL").assertIsDisplayed()
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
                        CodexContent(catalog, CodexRenderModel((0 until 80).toImmutableSet(), CodexRunStacks(), catalog.items), codexTestProgress(), scaleValue) { }
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
                ((0..4).map { "codex-tab-$it" } + (0..3).map { "codex-category-$it" } + listOf("codex-filter-0", "codex-filter-2") +
                    (0..2).map { "codex-sort-$it" })
                    .forEach { tag ->
                        val bounds = onNodeWithTag(tag).getUnclippedBoundsInRoot()
                        assertTrue(bounds.left >= box.left - 0.5.dp && bounds.right <= box.right + 0.5.dp,
                            "$tag leaves the Codex at $where: $bounds in $box")
                        assertTrue(bounds.width > 0.dp, "$tag is hidden at $where")
                    }
                // The sort names its purpose to a screen reader whether or not its visible label fits; up to the default
                // text size it shows the label on every layout, phones included, so its segments never pass for more filters.
                onNodeWithTag("codex-sort").assertContentDescriptionEquals(language.text(CodexText.SORT))
                if (scale <= 1.25f) onNodeWithTag("codex-sort-label", useUnmergedTree = true).assertIsDisplayed()
                // Landscape phones put the sort on the search line: beside the search, never over it.
                val sort = onNodeWithTag("codex-sort").getUnclippedBoundsInRoot()
                val search = onNodeWithTag("codex-search").getUnclippedBoundsInRoot()
                val overlaps = sort.left < search.right - 0.5.dp && search.left < sort.right - 0.5.dp &&
                    sort.top < search.bottom - 0.5.dp && search.top < sort.bottom - 0.5.dp
                assertFalse(overlaps, "the sort $sort overlaps the search $search at $where")
            }
        }
    }

    @Test fun aRowTooWideEvenAtItsSmallestScaleScrollsToEveryTab() = runComposeUiTest {
        // 300 dp at 175 % in Russian: the category row cannot fit at 60 %, so it scrolls instead.
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.Russian) {
                Box(Modifier.requiredSize(300.dp, 700.dp)) {
                    val catalog = remember { codexTestCatalog() }
                    CodexContent(catalog, CodexRenderModel((0 until 80).toImmutableSet(), CodexRunStacks(), catalog.items), codexTestProgress(), 1.75f) { }
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
        val stacks = List(80) { if (it == 0 || it == 1) 1 else 0 }.toImmutableList()
        val build = GameplayBuildSummaryProjection(
            GameplayInstanceId(RunId(1)), GameplayRevision.ZERO, stacks,
            character = CoreShape.ORB, weapon = WeaponId.FLUX_WAKE, weaponLevel = 7,
            relics = immutableListOf(kinetickk.ball.content.api.EquippedRelic(kinetickk.ball.content.api.RelicId.PERPETUAL_VECTOR, 2)),
        )
        // Item 0 was acquired this run and is new: NEW stamp and stack badge share the band above the glyph.
        val model = CodexRenderModel(immutableSetOf(), CodexRunStacks(stacks, build), catalog.items, newItemIds = immutableSetOf(0))
        val keys = listOf("item/0", "item/1", "relic/PERPETUAL_VECTOR", "weapon/FLUX_WAKE")
        for (language in AppLanguage.entries) for ((width, height) in listOf(1440 to 810, 390 to 844, 844 to 390)) for (scale in listOf(1f, 1.25f, 1.75f)) androidx.compose.ui.test.v2.runDesktopComposeUiTest(width, height) {
            setContent {
                CompositionLocalProvider(LocalAppLanguage provides language, androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f, 1f)) {
                    Box(Modifier.requiredSize(width.dp, height.dp)) { CodexContent(catalog, model, codexTestProgress(), scale) { } }
                }
            }
            onNodeWithTag("codex-tab-0").assertIsSelected()
            // The Build tab's cells (the Items matrix has its own marks, see newNotchAndStackBarStayInsideTheMatrixCellClearOfTheGlyph).
            keys.forEach { key ->
                val where = "$key build ${width}x$height ${language.code} @$scale"
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
                    val face = pixels[(cell.left + CODEX_FRAME_PX).toInt(), (cell.bottom - CODEX_FRAME_PX).toInt()]
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
                // Every Build-tab glyph is at least as large as a 64 dp cell's centered glyph.
                assertTrue(glyph.radius >= 64f * 0.36f, "glyph radius ${glyph.radius} at $where")
                assertTrue(badge.bottom <= cell.bottom && badge.right <= cell.right && badge.top >= cell.top, "badge leaves the cell ($where)")
            }
        }
    }

    @Test fun newNotchAndStackBarStayInsideTheMatrixCellClearOfTheGlyph() = androidx.compose.ui.test.v2.runDesktopComposeUiTest(1000, 700) {
        val catalog = codexTestCatalog()
        val stacks = List(80) { if (it == 0) 1 else 0 }.toImmutableList()
        val build = GameplayBuildSummaryProjection(GameplayInstanceId(RunId(1)), GameplayRevision.ZERO, stacks, character = CoreShape.ORB, weapon = WeaponId.FLUX_WAKE)
        // Item 0 is new, in the build and of an element: notch, stack bar and element strip at once.
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.Russian, LocalDensity provides Density(1f, 1f)) {
                Box(Modifier.requiredSize(1000.dp, 700.dp)) {
                    CodexContent(catalog, CodexRenderModel((0 until 80).toImmutableSet(), CodexRunStacks(stacks, build), catalog.items,
                        newItemIds = immutableSetOf(0)), codexTestProgress(), 1.75f) { }
                }
            }
        }
        onNodeWithTag("codex-tab-1").performSemanticsAction(SemanticsActions.OnClick)
        onNodeWithTag("codex-grid").performScrollToKey("item/0")
        // The notch carries no word: the cell's description says the discovery is new, and only for a new item.
        val newWord = AppLanguage.Russian.text(SessionText.NEW_DISCOVERY)
        fun description(key: String) = onNodeWithTag("codex-slot-$key").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertTrue(description("item/0").endsWith(", $newWord"), description("item/0"))
        assertFalse(newWord in description("item/1"), description("item/1"))
        val cell = onNodeWithTag("codex-slot-item/0").fetchSemanticsNode().boundsInRoot
        val notch = onNodeWithTag("codex-new-item/0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val bar = onNodeWithTag("codex-badge-item/0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        listOf("notch" to notch, "bar" to bar).forEach { (name, mark) ->
            assertTrue(mark.width > 0f && mark.left >= cell.left && mark.top >= cell.top && mark.right <= cell.right && mark.bottom <= cell.bottom,
                "the $name $mark stays inside its cell $cell")
        }
        val marks = codexItemCellMarks(cell.width, 1f)
        val center = Offset(cell.left + marks.glyphCenter.x, cell.top + marks.glyphCenter.y)
        val reach = marks.glyphRadius * CODEX_GLYPH_REACH
        // The notch is its box's top-left half: its hypotenuse (x + y = right + top) and the bar stay beyond the glyph's reach.
        assertTrue((center.x + center.y - notch.right - notch.top) / kotlin.math.sqrt(2f) >= reach - 0.5f, "the notch covers the glyph")
        val nearest = Offset(center.x.coerceIn(bar.left, bar.right), center.y.coerceIn(bar.top, bar.bottom))
        assertTrue((nearest - center).getDistance() >= reach - 0.5f, "the bar $bar runs under the glyph (reach $reach at $center)")
        // Drawn pixels: just beyond the hypotenuse and on the line just above the bar, the cell keeps its fill.
        val pixels = onRoot().captureToImage().toPixelMap()
        val face = pixels[(cell.left + marks.inset + 1f).toInt(), (cell.top + cell.height * 0.4f).toInt()]
        val beyondNotch = (1 until notch.width.toInt() - 1).map { step ->
            (notch.right - step + 1.5f).toInt() to (notch.top + step + 1.5f).toInt()
        }
        val aboveBar = (bar.left.toInt() + 1 until bar.right.toInt() - 1).map { it to (bar.top - 1f).toInt() }
        (beyondNotch + aboveBar).forEach { (x, y) ->
            val pixel = pixels[x, y]
            val delta = maxOf(kotlin.math.abs(pixel.red - face.red), kotlin.math.abs(pixel.green - face.green), kotlin.math.abs(pixel.blue - face.blue))
            assertTrue(delta < 0.05f, "ink at $x,$y beside the marks of $cell")
        }
    }

    @Test fun sortRegroupsRowsKeepsThePinAndScrollsToTop() {
        runComposeUiTest {
            setContent { TestCodex(1440, 810) }
            onNodeWithTag("codex-sort-0").assertIsSelected()
            onNodeWithTag("codex-items-row-tag/RAM").assertExists()
            onNodeWithTag("codex-slot-item/5").performClick()
            onNodeWithTag("codex-detail-title").assertTextEquals("Item 5")
            for ((sort, row) in listOf(1 to "element/KINETIC", 2 to "rarity/COMMON", 0 to "tag/RAM")) {
                onNodeWithTag("codex-sort-$sort").performClick().assertIsSelected()
                onNodeWithTag("codex-items-row-$row").assertExists()
                // Every row of the other sorts is gone; the pin is the same item.
                listOf("tag/RAM", "element/KINETIC", "rarity/COMMON").filter { it != row }.forEach { onNodeWithTag("codex-items-row-$it").assertDoesNotExist() }
                onNodeWithTag("codex-slot-item/5").assertIsSelected()
                onNodeWithTag("codex-detail-title").assertTextEquals("Item 5")
            }
        }
        // A phone's matrix scrolls: another sort starts again from the top.
        runComposeUiTest {
            setContent { TestCodex(390, 844) }
            onNodeWithTag("codex-grid").performScrollToKey("item/79")
            fun offset() = onNodeWithTag("codex-grid").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            assertTrue(offset() > 0f)
            onNodeWithTag("codex-sort-1").performClick()
            assertEquals(0f, offset())
            onNodeWithTag("codex-items-row-element/THERMAL").assertIsDisplayed()
        }
    }

    @Test fun previewHighlightsRowColumnAndLabels() = androidx.compose.ui.test.v2.runDesktopComposeUiTest(1440, 810) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) { TestCodex(1440, 810) }
        }
        // Banded: Ram's nine Common cells fill the band, Fortune's eight leave a blank under Ram's last one.
        onNodeWithTag("codex-items-head").assertExists()
        val ram = onNodeWithTag("codex-slot-item/72").fetchSemanticsNode().boundsInRoot
        val fortune = onNodeWithTag("codex-slot-item/71").fetchSemanticsNode().boundsInRoot
        val pitch = ram.width + CODEX_ITEM_GAP_DP
        assertTrue(kotlin.math.abs(fortune.left + pitch - ram.left) < 2f, "Fortune's last cell sits one column left of Ram's: $fortune, $ram")
        val blank = ram.center.x.toInt() to fortune.center.y.toInt()
        val other = (ram.center.x + pitch * 2f).toInt() to fortune.center.y.toInt()
        // Drive's last cell shares the previewed column: a face (beside its glyph) the stripe must still reach.
        val drive = onNodeWithTag("codex-slot-item/73").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(drive.left - ram.left) < 2f, "Drive's last cell sits in Ram's last column: $drive, $ram")
        val face = (drive.left + codexItemCellMarks(drive.width, 1f).inset + 2f).toInt() to (drive.top + drive.height * 0.4f).toInt()
        val label = onNodeWithTag("codex-items-row-tag/RAM").fetchSemanticsNode().boundsInRoot
        val you = KkRolePalette.Default.you
        fun PixelMap.volt(): Int = (label.top.toInt() until label.bottom.toInt()).sumOf { y ->
            (label.left.toInt() until label.right.toInt()).count { x ->
                val pixel = this[x, y]
                maxOf(kotlin.math.abs(pixel.red - you.red), kotlin.math.abs(pixel.green - you.green), kotlin.math.abs(pixel.blue - you.blue)) < 0.1f
            }
        }
        fun PixelMap.at(point: Pair<Int, Int>) = this[point.first, point.second]
        val before = onRoot().captureToImage().toPixelMap()
        onNodeWithTag("codex-slot-item/72").performSemanticsAction(SemanticsActions.RequestFocus)
        onNodeWithTag("codex-detail-title").assertTextEquals("Item 72")
        val after = onRoot().captureToImage().toPixelMap()
        fun delta(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color) =
            maxOf(kotlin.math.abs(a.red - b.red), kotlin.math.abs(a.green - b.green), kotlin.math.abs(a.blue - b.blue))
        // The previewed column runs through the blank of another row; other columns stay as they were.
        assertTrue(delta(before.at(blank), after.at(blank)) > 0.02f, "the blank under the previewed column stays dark")
        assertTrue(delta(before.at(other), after.at(other)) < 0.005f, "another column lights up")
        assertTrue(after.at(blank).green > before.at(blank).green, "the column stripe is `you`")
        // Through the opaque cells too: a faint `you` wash over the face of every other cell in the column.
        assertTrue(delta(before.at(face), after.at(face)) > 0.02f, "the stripe stops at the cells of its column")
        assertTrue(after.at(face).green > before.at(face).green, "the wash over the column's cells is `you`")
        // The previewed row's name turns `you`.
        assertEquals(0, before.volt())
        assertTrue(after.volt() > 0, "the row name stays bone")
    }

    @Test fun arrowKeysWalkTheMatrixAndBringOffscreenCellsIntoView() {
        runComposeUiTest {
            var unconsumedUp = 0
            setContent {
                Box(Modifier.onKeyEvent { if (it.key == Key.DirectionUp && it.type == KeyEventType.KeyDown) unconsumedUp++; false }) { TestCodex(1440, 810) }
            }
            onNodeWithTag("codex-items-head").assertExists()
            fun press(key: Key, expected: String) {
                onRoot().performKeyInput { pressKey(key) }
                onNodeWithTag("codex-slot-$expected").assertIsFocused()
            }
            // From the grid itself the first key enters at the first cell.
            onNodeWithTag("codex-grid").performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
            press(Key.DirectionRight, "item/0")
            // Ram is 0, 9, 18…; Drive 1, 10, 19… one line down; Anchor starts at 2.
            press(Key.DirectionRight, "item/9")
            press(Key.DirectionDown, "item/10")
            press(Key.MoveEnd, "item/73")
            press(Key.PageDown, "item/2")
            press(Key.DirectionLeft, "item/73")
            press(Key.MoveHome, "item/1")
            press(Key.PageUp, "item/0")
            onNodeWithTag("codex-detail-title").assertTextEquals("Item 0")
            // At the top edge Up is left to the rest of the window.
            onRoot().performKeyInput { pressKey(Key.DirectionUp) }
            assertEquals(1, unconsumedUp)
        }
        runComposeUiTest {
            setContent { TestCodex(390, 844) }
            // Packed: no header; Chain's first cell starts below the first screen.
            onNodeWithTag("codex-items-head").assertDoesNotExist()
            onNodeWithTag("codex-slot-item/0").performSemanticsAction(SemanticsActions.RequestFocus)
            repeat(7) { onRoot().performKeyInput { pressKey(Key.PageDown) } }
            onNodeWithTag("codex-slot-item/7").assertIsFocused().assertIsDisplayed()
            onRoot().performKeyInput { pressKey(Key.MoveEnd) }
            onNodeWithTag("codex-slot-item/79").assertIsFocused().assertIsDisplayed()
            onNodeWithTag("codex-sheet").assertDoesNotExist()
            // Up from Drive's first line, with Ram's name scrolled away, lands on Ram's wrapped last line and brings
            // Ram's name back into view beside it.
            onNodeWithTag("codex-grid").performScrollToKey("items/label/tag/DRIVE")
            assertFalse(runCatching { onNodeWithTag("codex-items-row-tag/RAM").assertIsDisplayed() }.isSuccess, "Ram's name is in view")
            onNodeWithTag("codex-slot-item/1").performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
            onRoot().performKeyInput { pressKey(Key.DirectionUp) }
            val focused = onNode(isFocused()).fetchSemanticsNode().config[SemanticsProperties.TestTag]
            assertEquals(0, focused.removePrefix("codex-slot-item/").toInt() % 9, "$focused is not a Ram cell")
            assertNotEquals("codex-slot-item/0", focused, "Up skipped Ram's wrapped line")
            onNode(isFocused()).assertIsDisplayed()
            onNodeWithTag("codex-items-row-tag/RAM").assertIsDisplayed()
        }
    }

    @Test fun switchingTheSortNeverResizesTheCells() {
        // Russian names differ most in length between the sorts (the longest tag, element and rarity names).
        for ((width, height) in listOf(1440 to 810, 1000 to 700)) androidx.compose.ui.test.v2.runDesktopComposeUiTest(width, height) {
            setContent {
                CompositionLocalProvider(LocalAppLanguage provides AppLanguage.Russian, LocalDensity provides Density(1f, 1f)) {
                    Box(Modifier.requiredSize(width.dp, height.dp)) {
                        val catalog = remember { codexTestCatalog() }
                        CodexContent(catalog, CodexRenderModel((0 until 80).toImmutableSet(), CodexRunStacks(), catalog.items), codexTestProgress(), 1.75f) { }
                    }
                }
            }
            fun measure(): Pair<Float, Float> {
                val cell = onNodeWithTag("codex-slot-item/0").fetchSemanticsNode().boundsInRoot.width
                val label = onAllNodes(hasTestTagPrefix("codex-items-row-")).fetchSemanticsNodes().first().boundsInRoot.width
                return cell to label
            }
            val tag = measure()
            for (sort in listOf(1, 2, 0)) {
                onNodeWithTag("codex-sort-$sort").performClick().assertIsSelected()
                onNodeWithTag("codex-grid").performScrollToKey("item/0")
                assertEquals(tag, measure(), "sort $sort at ${width}x$height: cell and label widths")
            }
        }
    }

    @Test fun smallDesktopsKeepTheRarityBandsAndShortGridsPackTheRows() {
        val base = codexTestCatalog()
        val catalog = base.copy(items = codexShippedTagShape(base.items))
        val model = CodexRenderModel((0 until 80).toImmutableSet(), CodexRunStacks(), catalog.items)
        for (language in AppLanguage.entries) {
            // 1280x720: the longest names (Russian ones) shrink a little so the rarity columns stay aligned.
            androidx.compose.ui.test.v2.runDesktopComposeUiTest(1280, 720) {
                setContent {
                    CompositionLocalProvider(LocalAppLanguage provides language, LocalDensity provides Density(1f, 1f)) {
                        Box(Modifier.requiredSize(1280.dp, 720.dp)) { CodexContent(catalog, model, codexTestProgress(), 1.25f) { } }
                    }
                }
                onNodeWithTag("codex-items-head").assertExists()
            }
            // 844x390: a header would take a third of the short grid and scroll away; the rows pack, one line each.
            androidx.compose.ui.test.v2.runDesktopComposeUiTest(844, 390) {
                setContent {
                    CompositionLocalProvider(LocalAppLanguage provides language, LocalDensity provides Density(1f, 1f)) {
                        Box(Modifier.requiredSize(844.dp, 390.dp)) { CodexContent(catalog, model, codexTestProgress(), 1.25f) { } }
                    }
                }
                onNodeWithTag("codex-items-row-tag/RAM").assertIsDisplayed()
                onNodeWithTag("codex-items-head").assertDoesNotExist()
                val ram = onNodeWithTag("codex-items-row-tag/RAM").fetchSemanticsNode().boundsInRoot
                val ramCells = (0 until 7).map { onNodeWithTag("codex-slot-${codexItemRows(CodexItemSort.TAG, "", CodexItemFilter.ALL, model, catalog,
                    codexTestProgress()).rows.first().cells[it].entry.key}").fetchSemanticsNode().boundsInRoot }
                assertTrue(ramCells.all { kotlin.math.abs(it.center.y - ram.center.y) < 1f }, "Ram's seven cells share its line (${language.code})")
            }
        }
    }

    @Test fun shippedTagMatrixIsBandedAndWholeAt1440x810() {
        val base = codexTestCatalog()
        val catalog = base.copy(items = codexShippedTagShape(base.items))
        val model = CodexRenderModel((0 until 80).toImmutableSet(), CodexRunStacks(), catalog.items)
        assertEquals(17, codexItemRefColumns(catalog.items))
        val last = codexItemRows(CodexItemSort.TAG, "", CodexItemFilter.ALL, model, catalog, codexTestProgress()).entries.last().key
        for (language in AppLanguage.entries) for (scale in listOf(1f, 1.25f, 1.75f)) androidx.compose.ui.test.v2.runDesktopComposeUiTest(1440, 810) {
            setContent {
                CompositionLocalProvider(LocalAppLanguage provides language, LocalDensity provides Density(1f, 1f)) {
                    Box(Modifier.requiredSize(1440.dp, 810.dp)) { CodexContent(catalog, model, codexTestProgress(), scale) { } }
                }
            }
            val where = "${language.code} @$scale"
            onNodeWithTag("codex-items-head").assertExists()
            // The whole matrix fits without scrolling: the last cell in reading order is in view.
            val grid = onNodeWithTag("codex-grid").fetchSemanticsNode()
            val range = grid.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
            assertTrue(range == null || range.maxValue() == 0f, "the matrix scrolls ($where)")
            val cell = onNodeWithTag("codex-slot-$last").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(cell.bottom <= grid.boundsInRoot.bottom + 0.5f && cell.right <= grid.boundsInRoot.right + 0.5f, "$last $cell leaves the grid ($where)")
        }
    }

    @Test fun hatchesFillFixedSlotsWithoutIdentity() = runComposeUiTest {
        setContent { TestCodex(1000, 700, discovered = false) }
        // A fresh profile: the notice over the matrix of hatches.
        val notice = onNodeWithTag("codex-empty-EMPTY_INVENTORY").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val hatch = onNodeWithTag("codex-slot-item/0").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(hatch.top >= notice.bottom, "the notice sits above the matrix")
        // The hatch tells its row, never its item.
        val description = onNodeWithTag("codex-slot-item/0").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertFalse("Item 0" in description, description)
        assertTrue("Unknown discovery" in description && "Ram" in description, description)
        onNodeWithTag("codex-items-row-tag/RAM").assertContentDescriptionEquals("Ram, Discovered 0 / 9").assert(isHeading())
        onNodeWithTag("codex-slot-item/0").performClick()
        onNodeWithTag("codex-detail-title").assertTextEquals("Unknown discovery")
    }

    @Test fun pairingRowsOnPhonesNeverSqueezeTextIntoLetterColumns() = runComposeUiTest {
        val catalog = codexTestCatalog()
        setContent {
            Box(Modifier.requiredSize(390.dp, 844.dp)) {
                CompositionLocalProvider(LocalAppLanguage provides AppLanguage.Russian) {
                    CodexContent(catalog, CodexRenderModel((0 until 80).toImmutableSet(), CodexRunStacks(), catalog.items,
                        discoveredRelicIds = kinetickk.ball.content.api.RelicId.entries.toImmutableSet()), codexTestProgress(), 1.25f) { }
                }
            }
        }
        onNodeWithTag("codex-tab-3").performClick()
        val rows = onAllNodes(hasTestTagPrefix("codex-slot-pairing/")).fetchSemanticsNodes()
        assertTrue(rows.isNotEmpty())
        rows.forEach { row ->
            // Title, the two relic names and the kind tag beside the diagram: letter-by-letter
            // wrapping would make these rows several times taller.
            assertTrue(row.boundsInRoot.height <= 150f, "${row.config.getOrElse(SemanticsProperties.TestTag) { "" }} is ${row.boundsInRoot.height} dp tall")
        }
        // Every relic name is shown whole: no line of the names is cut off.
        val names = onAllNodesWithTag("codex-pairing-names", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(names.isNotEmpty())
        names.forEach { node ->
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.single()
            // (Width "overflow" only reports the mono tracking after the last letter; lines are what get cut.)
            assertFalse(layout.didOverflowHeight || layout.multiParagraph.didExceedMaxLines, "cut names: ${layout.layoutInput.text}")
        }
        // Rows of one line share their height, so the grid reads as even plates.
        val heights = rows.groupBy { it.boundsInRoot.top }.values.map { line -> line.map { it.boundsInRoot.height }.distinct() }
        heights.forEach { assertEquals(1, it.size, "rows of one line differ in height: $it") }
    }

    @Test fun aResidueLightsOnlyWhileItsReactionIsInViewAndOpensTheReactionsItFeeds() {
        val catalog = codexTestCatalog()
        val model = CodexRenderModel(immutableSetOf(), CodexRunStacks(), catalog.items,
            discoveredReactionIds = immutableSetOf(kinetickk.ball.content.api.ReactionId.PLASMA_BURST, kinetickk.ball.content.api.ReactionId.SUPERCONDUCT))
        runComposeUiTest {
            setContent {
                Box(Modifier.requiredSize(1440.dp, 810.dp)) {
                    CompositionLocalProvider(LocalAppLanguage provides AppLanguage.English) { CodexContent(catalog, model, codexTestProgress(), 1.25f) { } }
                }
            }
            onNodeWithTag("codex-tab-2").performClick()
            onNodeWithTag("codex-slot-reaction/PLASMA_BURST").performClick()
            onNodeWithTag("codex-residue").performClick().assertIsSelected()
            // Another reaction in view shows its own residue, unlit; coming back finds the old one dropped.
            onNodeWithTag("codex-slot-reaction/SUPERCONDUCT").performClick()
            onNodeWithTag("codex-detail-title").assertTextEquals("Superconduct")
            onNodeWithTag("codex-residue").assertIsNotSelected()
            onNodeWithTag("codex-slot-reaction/PLASMA_BURST").performClick()
            onNodeWithTag("codex-residue").assertIsNotSelected().performClick().assertIsSelected()
            // Switching tabs drops it too.
            onNodeWithTag("codex-tab-3").performClick()
            onNodeWithTag("codex-tab-2").performClick()
            onNodeWithTag("codex-slot-reaction/PLASMA_BURST").performClick()
            onNodeWithTag("codex-residue").assertIsNotSelected()
            // The detail lists the reactions a Thermal residue feeds, and each opens its own entry.
            onNodeWithTag("codex-feed-SUPERCONDUCT").assertDoesNotExist()
            onNodeWithTag("codex-feed-THERMAL_SHOCK").performClick()
            onNodeWithTag("codex-detail-title").assertTextEquals("Unknown reaction")
        }
        runComposeUiTest {
            setContent {
                Box(Modifier.requiredSize(390.dp, 844.dp)) {
                    CompositionLocalProvider(LocalAppLanguage provides AppLanguage.English) { CodexContent(catalog, model, codexTestProgress(), 1.25f) { } }
                }
            }
            onNodeWithTag("codex-tab-2").performClick()
            onNodeWithTag("codex-slot-reaction/PLASMA_BURST").performClick()
            onNodeWithTag("codex-sheet").assertExists()
            // On a phone the sheet steps aside so the chain the residue lights is in view.
            onNodeWithTag("codex-residue").performClick()
            onNodeWithTag("codex-sheet").assertDoesNotExist()
            onNodeWithTag("codex-slot-reaction/PLASMA_BURST").assertIsFocused().performClick()
            onNodeWithTag("codex-residue").assertIsSelected()
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
            CodexContent(catalog, CodexRenderModel(if (discovered) (0 until 80).toImmutableSet() else immutableSetOf(), CodexRunStacks(), catalog.items, discoveredRelicIds = if (discovered) kinetickk.ball.content.api.RelicId.entries.toImmutableSet() else immutableSetOf()), codexTestProgress(), scale, onClose = onClose)
        }
    }
}

private fun hasTestTagPrefix(prefix: String): SemanticsMatcher = SemanticsMatcher("Tag starts with $prefix") {
    it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith(prefix)
}

/** [items] re-cut to the shipped catalog's first-tag × rarity counts (Common to Legendary per row, the untagged row last). */
internal fun codexShippedTagShape(items: List<kinetickk.ball.content.api.ItemDefinition>): kinetickk.foundation.collections.ImmutableList<kinetickk.ball.content.api.ItemDefinition> {
    val counts = listOf(
        listOf(2, 2, 1, 1, 1), listOf(3, 3, 3, 1, 0), listOf(2, 3, 1, 1, 0), listOf(6, 2, 1, 2, 0), listOf(1, 2, 1, 3, 0),
        listOf(3, 2, 1, 3, 0), listOf(2, 2, 1, 1, 0), listOf(1, 2, 2, 2, 1), listOf(1, 2, 1, 2, 0), listOf(0, 3, 4, 1, 1),
    )
    var id = 0
    return buildList {
        counts.forEachIndexed { row, rarities ->
            rarities.forEachIndexed { rarity, count ->
                repeat(count) {
                    add(items[id++].copy(rarity = kinetickk.ball.content.api.ItemRarity.entries[rarity],
                        tags = listOfNotNull(kinetickk.ball.content.api.BuildTag.entries.getOrNull(row)).toImmutableList()))
                }
            }
        }
    }.toImmutableList()
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
