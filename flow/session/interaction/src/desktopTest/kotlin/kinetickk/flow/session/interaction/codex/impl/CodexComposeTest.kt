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
                CodexContent(catalog, CodexRenderModel(immutableSetOf(), CodexRunStacks(), itemsValue), codexTestProgress(), 1f) { }
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
                        CodexContent(catalog, CodexRenderModel(immutableSetOf(), runStacks, catalog.items), codexTestProgress(), 1f) { onOutput(CodexOutput.Back) }
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
                CodexContent(catalog, CodexRenderModel((0 until 400).toSet().toImmutableSet(), CodexRunStacks(), catalog.items), codexTestProgress(), 1f) { }
            }
        }
        onNodeWithTag("codex-search").performClick().performTextInput("Item")
        onNodeWithTag("codex-filter-1").performClick()
        onNodeWithTag("codex-grid").performScrollToKey("item/399")
        onNodeWithTag("codex-slot-item/399").performClick()
        val bounds = onNodeWithTag("codex-slot-item/399").fetchSemanticsNode().boundsInRoot
        restoration.emulateSaveAndRestore()
        onNodeWithTag("codex-tab-1").assertIsSelected()
        onNodeWithTag("codex-search").assertTextEquals("Item")
        onNodeWithTag("codex-filter-1").assertIsSelected()
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

    @Test fun searchPasteIsBoundedAndNoRunInventoryAndFilterStatesAreDistinct() = runComposeUiTest {
        setContent { TestCodex(1000, 700) }
        onNodeWithTag("codex-filter-2").assertIsNotEnabled()
        onNodeWithTag("codex-tab-0").performClick()
        onNodeWithTag("codex-empty-NO_RUN").assertIsDisplayed()
        onNodeWithTag("codex-tab-1").performClick()
        onNodeWithTag("codex-filter-1").performClick()
        onNodeWithTag("codex-empty-EMPTY_INVENTORY").assertIsDisplayed()
        onNodeWithTag("codex-search").performTextInput("a".repeat(129))
        onNodeWithTag("codex-search").assertTextEquals("a".repeat(128))
        onNodeWithTag("codex-empty-EMPTY_SEARCH").assertIsDisplayed()
    }
}

@Composable
private fun TestCodex(width: Int, height: Int, scale: Float = 1f, onClose: () -> Unit = {}) {
    val catalog = remember { codexTestCatalog() }
    Box(Modifier.requiredSize(width.dp, height.dp)) {
        CompositionLocalProvider(LocalAppLanguage provides AppLanguage.English) {
            CodexContent(catalog, CodexRenderModel(immutableSetOf(), CodexRunStacks(), catalog.items), codexTestProgress(), scale, onClose)
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
