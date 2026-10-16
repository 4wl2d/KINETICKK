// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.StatDelta
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.api.*
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.measureKkText
import kinetickk.foundation.design.monoStyle
import kinetickk.foundation.design.rememberKkCanvasMeasurer
import kotlin.math.abs
import kotlin.test.*

/** Text fit and cell geometry of the Codex, measured with the bundled fonts at 100–175 % text size. */
@OptIn(ExperimentalTestApi::class)
class CodexFitComposeTest {
    /** Items as the game has them: stacking, with the collision / critical damage pair of the finding. */
    private val catalog = codexTestCatalog().let { base ->
        base.copy(items = base.items.map {
            it.copy(rarity = ItemRarity.COMMON, tags = immutableListOf(BuildTag.RAM), elements = immutableListOf(),
                stats = immutableListOf(StatDelta(ItemEffect.IMPACT_DAMAGE, 0.0556f), StatDelta(ItemEffect.CRIT_DAMAGE, 0.0391f)), maxStacks = 3)
        }.toImmutableList())
    }
    private val stacks = List(80) { if (it == 0 || it == 1) 3 else 0 }.toImmutableList()
    private val build = GameplayBuildSummaryProjection(
        GameplayInstanceId(RunId(1)), GameplayRevision.ZERO, stacks,
        character = CoreShape.ORB, weapon = WeaponId.FLUX_WAKE, weaponLevel = 7,
        relics = immutableListOf(kinetickk.ball.content.api.EquippedRelic(kinetickk.ball.content.api.RelicId.PERPETUAL_VECTOR, 2)),
        stats = immutableListOf(
            BuildStatSummary("Impact damage", 38f, "%", immutableListOf(BuildStatContribution(BuildStatSource.ITEMS, 30f), BuildStatContribution(BuildStatSource.LAB, 8f))),
            BuildStatSummary("Critical damage", 12.5f, "%", immutableListOf(BuildStatContribution(BuildStatSource.ITEMS, 12.5f))),
        ),
    )
    /** Item 0: NEW with a stack badge; item 1: badge only; item 2: NEW only; item 3: neither. */
    private val model = CodexRenderModel((0 until 80).toImmutableSet(), CodexRunStacks(stacks, build), catalog.items, newItemIds = immutableSetOf(0, 2))
    private val sizes = listOf(1440 to 810, 844 to 390, 390 to 844)
    private val scales = listOf(1f, 1.25f, 1.75f)

    private fun codex(width: Int, height: Int, language: AppLanguage, scale: Float, model: CodexRenderModel = this.model, test: ComposeUiTest.() -> Unit) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                CompositionLocalProvider(LocalAppLanguage provides language, LocalDensity provides Density(1f, 1f)) {
                    Box(Modifier.requiredSize(width.dp, height.dp)) { CodexContent(catalog, model, codexTestProgress(), scale) { } }
                }
            }
            test()
        }

    /** Every line of every node showing [text] ends between words, and none is cut or ellipsized. */
    private fun ComposeUiTest.assertWrapsOnlyBetweenWords(text: String, where: String) {
        val nodes = onAllNodes(hasText(text)).fetchSemanticsNodes()
        assertTrue(nodes.isNotEmpty(), "'$text' is shown ($where)")
        nodes.forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.single()
            val shown = layout.layoutInput.text.text
            for (line in 0 until layout.lineCount - 1) {
                val end = layout.getLineEnd(line)
                assertFalse(end in 1 until shown.length && shown[end - 1].isLetterOrDigit() && shown[end].isLetterOrDigit(),
                    "'$shown' breaks inside a word after '${shown.substring(0, end)}' ($where)")
            }
            assertFalse(layout.multiParagraph.didExceedMaxLines, "'$shown' is cut ($where)")
            assertFalse(layout.lineCount == 1 && layout.multiParagraph.intrinsics.maxIntrinsicWidth > layout.size.width + 0.5f, "'$shown' is cut ($where)")
        }
    }

    @Test fun statLabelsWrapOnlyBetweenWordsInThePanelsAndTheBuildStats() {
        for (language in AppLanguage.entries) for ((width, height) in sizes) for (scale in scales) codex(width, height, language, scale) {
            val where = "${width}x$height ${language.code} @$scale"
            // The Build tab's effective stats.
            onNodeWithTag("codex-tab-0").assertIsSelected()
            onNodeWithTag("codex-grid").performScrollToKey("stats-heading")
            onNodeWithTag("codex-stats").performSemanticsAction(SemanticsActions.OnClick)
            build.stats.forEach { stat ->
                onNodeWithTag("codex-grid").performScrollToKey("stat/${stat.name}")
                assertWrapsOnlyBetweenWords(stat.name.localizedContent(language), "build stats $where")
            }
            // The item's rule lines (side panel on wide screens, sheet on phones).
            onNodeWithTag("codex-tab-1").performSemanticsAction(SemanticsActions.OnClick)
            onNodeWithTag("codex-grid").performScrollToKey("item/0")
            onNodeWithTag("codex-slot-item/0").performSemanticsAction(SemanticsActions.OnClick)
            val lines = (codexItemEntry(catalog.items[0], model, 0L, language, catalog).detail as CodexDetail.Item).lines
            assertEquals(2, lines.size)
            lines.forEach { line ->
                onNodeWithTag("codex-details-scroll").performScrollToNode(hasText(line.text))
                assertWrapsOnlyBetweenWords(line.text, "rule line $where")
            }
        }
    }

    @Test fun sectionRowTitlesShrinkTogetherToFitBesideTheirCounts() = runDesktopComposeUiTest(200, 200) {
        val measurers = mutableMapOf<Pair<AppLanguage, Float>, CanvasTextMeasurer>()
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                for (language in AppLanguage.entries) CompositionLocalProvider(LocalAppLanguage provides language) {
                    for (scale in listOf(1f, 1.25f, 1.5f, 1.75f)) measurers[language to scale] = rememberKkCanvasMeasurer(codexUiScale(scale))
                }
            }
        }
        waitForIdle()
        // The widest counts the collection can show.
        val counts = listOf("400/400", "12/12", "40/40", "6/6")
        measurers.forEach { (key, measurer) ->
            val (language, scale) = key
            val titles = listOf(SessionText.ITEMS_TITLE, SessionText.WEAPONS_TITLE, SessionText.RELICS_TITLE, SessionText.FORMS_TITLE).map { language.text(it) }
            // The nav is min(250 dp, 22 % of the width) on wide screens (900 dp and up).
            for (row in listOf(250f, 198f)) {
                val size = codexNavTitleSize(measurer, titles, counts, row, 1f)
                assertTrue(size <= 24f, "never above the design size")
                titles.forEachIndexed { index, title ->
                    val count = measureKkText(measurer, counts[index], measurer.typography.monoStyle(), uppercase = true).size.width
                    val width = measureKkText(measurer, title, measurer.typography.condStyle(size), uppercase = true).size.width
                    assertTrue(width <= row - 36f - count - 14f, "$title ($width px at $size) is cut beside ${counts[index]} in $row px, ${language.code} @$scale")
                }
            }
        }
        // The case of the finding: the Russian Items title did not fit a 250 dp row at 175 %.
        val russian = measurers.getValue(AppLanguage.Russian to 1.75f)
        val titles = listOf(SessionText.ITEMS_TITLE, SessionText.WEAPONS_TITLE, SessionText.RELICS_TITLE, SessionText.FORMS_TITLE).map { AppLanguage.Russian.text(it) }
        assertTrue(codexNavTitleSize(russian, titles, listOf("216/400", "7/12", "23/40", "5/6"), 250f, 1f) < 24f)
    }

    @Test fun collectionNavFitsA1440x810WindowAtEveryTextSizeAndScrollsWithACueWhenShorter() {
        for (language in AppLanguage.entries) for (scale in listOf(1f, 1.25f, 1.5f, 1.75f)) codex(1440, 810, language, scale) {
            val where = "${language.code} @$scale"
            onNodeWithTag("codex-tab-1").performSemanticsAction(SemanticsActions.OnClick)
            waitForIdle()
            val nav = onNodeWithTag("codex-nav").fetchSemanticsNode()
            val range = nav.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
            assertTrue(range == null || range.maxValue() == 0f, "the nav scrolls at rest ($where): ${range?.maxValue()}")
            onNodeWithTag("codex-nav-scroll-cue").assertDoesNotExist()
            // The last legend row sits whole inside the nav.
            val last = onNodeWithText(language.text(SessionText.UNDISCOVERED)).fetchSemanticsNode().boundsInRoot
            assertTrue(last.bottom <= nav.boundsInRoot.bottom + 0.5f, "legend row $last below the nav ${nav.boundsInRoot} ($where)")
        }
        // A shorter window: the nav scrolls, and its cut edge carries the fade and scroll bar.
        for (language in AppLanguage.entries) codex(1200, 600, language, 1.75f) {
            onNodeWithTag("codex-tab-1").performSemanticsAction(SemanticsActions.OnClick)
            waitForIdle()
            val range = requireNotNull(onNodeWithTag("codex-nav").fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange))
            assertTrue(range.maxValue() > 0f && range.value() == 0f)
            onNodeWithTag("codex-nav-scroll-cue").assertExists()
            onNodeWithTag("codex-nav").performScrollToNode(hasText(language.text(SessionText.UNDISCOVERED)))
            onNodeWithText(language.text(SessionText.UNDISCOVERED)).assertIsDisplayed()
        }
    }

    @Test fun phoneSheetFadesItsCutEdgeBesideAScrollBar() {
        for (language in AppLanguage.entries) for (scale in listOf(1.25f, 1.75f)) {
            codex(844, 390, language, scale, model.copy(runStacks = CodexRunStacks())) {
                val where = "844x390 ${language.code} @$scale"
                onNodeWithTag("codex-grid").performScrollToKey("item/0")
                onNodeWithTag("codex-slot-item/0").performSemanticsAction(SemanticsActions.OnClick)
                waitForIdle()
                val range = requireNotNull(onNodeWithTag("codex-details-scroll").fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange))
                assertTrue(range.maxValue() > 0f, "the facts run below the sheet ($where)")
                onNodeWithTag("codex-details-scroll-cue").assertExists()
                // At rest the sheet's bottom edge shows only its face (and the bar): no row is cut bare.
                val sheet = onNodeWithTag("codex-sheet").fetchSemanticsNode().boundsInRoot
                val pixels = onRoot().captureToImage().toPixelMap()
                val face = Kk.Ink2
                for (x in (sheet.left + 1f).toInt() until (sheet.right - 16f).toInt()) {
                    val pixel = pixels[x, (sheet.bottom - 1f).toInt()]
                    assertTrue(maxOf(abs(pixel.red - face.red), abs(pixel.green - face.green), abs(pixel.blue - face.blue)) < 0.04f,
                        "content cut bare at $x on the sheet's bottom edge ($where)")
                }
                // Scrolled to the end, every fact is reachable and the cue stays as a bar.
                onNodeWithTag("codex-details-scroll").performScrollToNode(hasTestTag("codex-facts"))
                onNodeWithTag("codex-facts").assertIsDisplayed()
            }
        }
        // A detail that fits its panel has no cue.
        codex(1440, 810, AppLanguage.English, 1.25f, model.copy(runStacks = CodexRunStacks())) {
            onNodeWithTag("codex-grid").performScrollToKey("item/0")
            onNodeWithTag("codex-slot-item/0").performSemanticsAction(SemanticsActions.OnClick)
            waitForIdle()
            onNodeWithTag("codex-details-scroll-cue").assertDoesNotExist()
        }
    }

    /**
     * Box of the glyph's ink (its halo included) in [cell], ignoring what [skip] marks (local px) and the
     * cell's rarity frame (within [CODEX_FRAME_PX] of its edge), relative to the cell's top-left. The
     * cell's plain face is read at [face] (local px).
     */
    private fun PixelMap.glyphBox(cell: Rect, face: androidx.compose.ui.geometry.Offset, skip: (Float, Float) -> Boolean): Rect {
        val plain = this[(cell.left + face.x).toInt(), (cell.top + face.y).toInt()]
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (y in (cell.top + CODEX_FRAME_PX).toInt() until (cell.bottom - CODEX_FRAME_PX).toInt()) {
            for (x in (cell.left + CODEX_FRAME_PX).toInt() until (cell.right - CODEX_FRAME_PX).toInt()) {
                if (skip(x - cell.left, y - cell.top)) continue
                val pixel: Color = this[x, y]
                if (maxOf(abs(pixel.red - plain.red), abs(pixel.green - plain.green), abs(pixel.blue - plain.blue)) > 0.03f) {
                    left = minOf(left, x.toFloat()); right = maxOf(right, x + 1f); top = minOf(top, y.toFloat()); bottom = maxOf(bottom, y + 1f)
                }
            }
        }
        return Rect(left - cell.left, top - cell.top, right - cell.left, bottom - cell.top)
    }

    @Test fun everyDiscoveredCellOfAGridDrawsItsGlyphAtOneSizeAndCentre() {
        for (language in AppLanguage.entries) for ((width, height) in sizes) for (scale in scales) codex(width, height, language, scale) {
            val where = "${width}x$height ${language.code} @$scale"
            // The Build tab (items 0 and 1), then the Items matrix (items 0 to 3 and the unmarked twins of 0 to 2, see below).
            listOf(0 to listOf(0, 1), 1 to listOf(0, 1, 2, 3, 20, 10, 11)).forEach { (tab, items) ->
                val matrix = tab == 1
                onNodeWithTag("codex-tab-$tab").performSemanticsAction(SemanticsActions.OnClick)
                val boxes = items.map { id ->
                    val key = "item/$id"
                    onNodeWithTag("codex-grid").performScrollToKey(key)
                    waitForIdle()
                    val cell = onNodeWithTag("codex-slot-$key").fetchSemanticsNode().boundsInRoot
                    val marks = (onAllNodesWithTag("codex-new-$key", useUnmergedTree = true).fetchSemanticsNodes() +
                        onAllNodesWithTag("codex-badge-$key", useUnmergedTree = true).fetchSemanticsNodes()).map { it.boundsInRoot }
                    assertEquals(id == 0 || id == 2, onAllNodesWithTag("codex-new-$key", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), where)
                    assertEquals(id == 0 || id == 1, onAllNodesWithTag("codex-badge-$key", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), where)
                    val pixels = onRoot().captureToImage().toPixelMap()
                    key to if (matrix) {
                        // A matrix cell's solid fill, read beside the glyph; its notch is the top-left half of its box, its
                        // element strip the right edge and its bar the bottom line.
                        val cellMarks = codexItemCellMarks(cell.width, 1f)
                        pixels.glyphBox(cell, androidx.compose.ui.geometry.Offset(cellMarks.inset + 1f, cell.height * 0.4f)) { x, y ->
                            x + y <= cellMarks.notch.right + cellMarks.notch.top + 2f || y >= cellMarks.bar.top - 2f || x >= cellMarks.strip.left - 2f
                        }
                    } else {
                        pixels.glyphBox(cell, androidx.compose.ui.geometry.Offset(CODEX_FRAME_PX, cell.height - CODEX_FRAME_PX)) { x, y ->
                            marks.any { x + cell.left >= it.left - 2f && x + cell.left <= it.right + 4f && y + cell.top >= it.top - 2f && y + cell.top <= it.bottom + 4f }
                        }
                    }
                }
                fun assertSameBox(key: String, box: Rect, plainKey: String, plain: Rect) {
                    listOf(box.left - plain.left, box.top - plain.top, box.right - plain.right, box.bottom - plain.bottom).forEach { delta ->
                        assertTrue(abs(delta) <= 1.5f, "$key glyph $box differs from $plainKey $plain (tab $tab, $where)")
                    }
                }
                if (matrix) {
                    // A matrix cell draws its item's primary mark alone, so each marked cell is measured against an unmarked
                    // cell of the same mark: item 0 (NEW and held, Thermal) against 20, 1 (held, Drive) against 10 and 2
                    // (NEW, Anchor) against 11. Every mark keeps within the glyph's reach round the one centre.
                    val byKey = boxes.toMap()
                    listOf(0 to 20, 1 to 10, 2 to 11).forEach { (marked, twin) ->
                        assertSameBox("item/$marked", byKey.getValue("item/$marked"), "item/$twin", byKey.getValue("item/$twin"))
                    }
                    onNodeWithTag("codex-grid").performScrollToKey("item/3")
                    val cellWidth = onNodeWithTag("codex-slot-item/3").fetchSemanticsNode().boundsInRoot.width
                    val glyph = codexItemCellMarks(cellWidth, 1f)
                    val reach = glyph.glyphRadius * CODEX_GLYPH_REACH + 1f
                    boxes.forEach { (key, box) ->
                        assertTrue(box.left >= glyph.glyphCenter.x - reach && box.right <= glyph.glyphCenter.x + reach &&
                            box.top >= glyph.glyphCenter.y - reach && box.bottom <= glyph.glyphCenter.y + reach,
                            "$key glyph $box leaves its reach $reach round ${glyph.glyphCenter} ($where)")
                    }
                    val plain = byKey.getValue("item/3")
                    assertTrue(plain.width >= cellWidth * 0.4f, "item/3 glyph ${plain.width} px in a $cellWidth px cell ($where)")
                } else {
                    val (plainKey, plain) = boxes.last()
                    assertTrue(plain.width > 40f, "$plainKey glyph ${plain.width} px ($where)")
                    boxes.forEach { (key, box) -> assertSameBox(key, box, plainKey, plain) }
                }
            }
        }
    }
}
