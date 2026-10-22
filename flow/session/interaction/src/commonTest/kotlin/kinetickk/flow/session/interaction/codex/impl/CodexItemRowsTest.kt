// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import androidx.compose.ui.geometry.Rect
import kinetickk.ball.content.api.*
import kinetickk.ball.gameplay.api.GameplayBuildSummaryProjection
import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.flow.session.interaction.localization.SessionText
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.test.*

/** The Items matrix: fixed slots in labelled rows, its grid geometry, keyboard moves and cell marks. */
/**
 * The shipped catalog's first-tag × rarity counts (Common to Legendary), the untagged row last: 221 items.
 * A desktop test checks it against the real catalog.
 */
internal val ShippedTagRarityCounts: List<List<Int>> = listOf(
    listOf(6, 5, 4, 6, 1), listOf(8, 8, 5, 5, 1), listOf(5, 5, 6, 3, 1), listOf(11, 6, 4, 4, 1), listOf(3, 5, 4, 7, 1),
    listOf(3, 4, 4, 5, 1), listOf(5, 4, 6, 4, 0), listOf(4, 7, 4, 11, 3), listOf(5, 5, 6, 3, 1), listOf(1, 7, 7, 4, 2),
)

class CodexItemRowsTest {
    private val catalog = codexTestCatalog()

    /** Two tags and two elements (0), untagged (2), untagged and elementless (3), a Rebirth-gated Legendary (5). */
    private val mixed: ImmutableList<ItemDefinition> = listOf(
        item(0, ItemRarity.COMMON, listOf(BuildTag.RAM, BuildTag.CHAIN), listOf(StatusElement.THERMAL, StatusElement.ENTROPY), unlock = 2),
        item(1, ItemRarity.UNCOMMON, listOf(BuildTag.CHAIN), unlock = 3),
        item(2, ItemRarity.RARE, elements = listOf(StatusElement.CRYO), unlock = 8),
        item(3, ItemRarity.EPIC, unlock = 12),
        item(4, ItemRarity.COMMON, listOf(BuildTag.FORTUNE), listOf(StatusElement.ION)),
        item(5, ItemRarity.LEGENDARY, listOf(BuildTag.RAM), unlock = 16, rebirth = 2),
        item(6, ItemRarity.COMMON, listOf(BuildTag.RAM), unlock = 3),
        item(7, ItemRarity.UNCOMMON, listOf(BuildTag.DRIVE, BuildTag.RAM), listOf(StatusElement.GRAVITIC), unlock = 4),
        item(8, ItemRarity.RARE, listOf(BuildTag.RAM), unlock = 9),
        item(9, ItemRarity.COMMON, listOf(BuildTag.RAM)),
        item(10, ItemRarity.COMMON, listOf(BuildTag.RAM)),
    ).toImmutableList()

    /** The shipped catalog's shape ([ShippedTagRarityCounts]) with stand-in items. */
    private val shipped: ImmutableList<ItemDefinition> = shape(ShippedTagRarityCounts)

    /** A board-sized shape (the 0.3 catalog's 80 items): its seventeen rarity columns band beside the labels. */
    private val boardShape: ImmutableList<ItemDefinition> = shape(
        listOf(
            listOf(2, 2, 1, 1, 1), listOf(3, 3, 3, 1, 0), listOf(2, 3, 1, 1, 0), listOf(6, 2, 1, 2, 0), listOf(1, 2, 1, 3, 0),
            listOf(3, 2, 1, 3, 0), listOf(2, 2, 1, 1, 0), listOf(1, 2, 2, 2, 1), listOf(1, 2, 1, 2, 0), listOf(0, 3, 4, 1, 1),
        ),
    )

    private fun shape(counts: List<List<Int>>): ImmutableList<ItemDefinition> = buildList {
        counts.forEachIndexed { row, rowCounts ->
            rowCounts.forEachIndexed { rarity, count ->
                val tags = listOfNotNull(BuildTag.entries.getOrNull(row))
                repeat(count) { add(item(size, ItemRarity.entries[rarity], tags, unlock = 1 + rarity * 4)) }
            }
        }
    }.toImmutableList()

    /** Ram: Common 0, 1 and Rare 2; Drive: Common 3 and Uncommon 4; Chain: Rare 5 and 6. */
    private val arrows: ImmutableList<ItemDefinition> = listOf(
        item(0, ItemRarity.COMMON, listOf(BuildTag.RAM)), item(1, ItemRarity.COMMON, listOf(BuildTag.RAM)),
        item(2, ItemRarity.RARE, listOf(BuildTag.RAM)), item(3, ItemRarity.COMMON, listOf(BuildTag.DRIVE)),
        item(4, ItemRarity.UNCOMMON, listOf(BuildTag.DRIVE)), item(5, ItemRarity.RARE, listOf(BuildTag.CHAIN)),
        item(6, ItemRarity.RARE, listOf(BuildTag.CHAIN)),
    ).toImmutableList()

    @Test
    fun everyItemSitsInExactlyOneRowPerSortWithUniqueKeys() {
        val keys = mixed.map { "item/${it.id}" }.toSet()
        for (sort in CodexItemSort.entries) for (discovered in listOf(emptySet(), mixed.ids)) {
            val matrix = rows(sort, discovered)
            val listed = matrix.entries.map { it.key }
            assertEquals(listed.size, listed.toSet().size, "$sort lists an item twice")
            assertEquals(keys, listed.toSet(), "$sort misses an item")
            assertEquals(mixed.size, matrix.rows.sumOf { it.total })
            // Rows keep the sort's display order.
            val groups = matrix.rows.map { it.group }
            assertEquals(codexItemGroups(sort).filter { it in groups }, groups)
        }
        fun rowOf(sort: CodexItemSort, id: Int): CodexItemGroup = rows(sort).rows.single { row -> row.cells.any { it.entry.key == "item/$id" } }.group
        // An item with two tags or two elements sits under its first one only.
        assertEquals(CodexItemGroup.Tag(BuildTag.RAM), rowOf(CodexItemSort.TAG, 0))
        assertEquals(CodexItemGroup.Tag(BuildTag.DRIVE), rowOf(CodexItemSort.TAG, 7))
        assertEquals(CodexItemGroup.Element(StatusElement.THERMAL), rowOf(CodexItemSort.ELEMENT, 0))
        // No tag and no element (Kinetic) form their own rows.
        assertEquals(CodexItemGroup.Tag(null), rowOf(CodexItemSort.TAG, 2))
        assertEquals(CodexItemGroup.Tag(null), rowOf(CodexItemSort.TAG, 3))
        assertEquals(CodexItemGroup.Element(null), rowOf(CodexItemSort.ELEMENT, 3))
        assertEquals(CodexItemGroup.Element(null), rowOf(CodexItemSort.ELEMENT, 1))
        assertEquals(CodexItemGroup.Rarity(ItemRarity.LEGENDARY), rowOf(CodexItemSort.RARITY, 5))
        // Enum order with No tag and Kinetic last; a row without members (Entropy, a second element only) drops.
        assertEquals(listOf("tag/RAM", "tag/DRIVE", "tag/CHAIN", "tag/FORTUNE", "tag/NONE"), rows(CodexItemSort.TAG).rows.map { it.group.key })
        assertEquals(listOf("element/THERMAL", "element/CRYO", "element/ION", "element/GRAVITIC", "element/KINETIC"),
            rows(CodexItemSort.ELEMENT).rows.map { it.group.key })
        assertEquals(ItemRarity.entries.map { "rarity/$it" }, rows(CodexItemSort.RARITY).rows.map { it.group.key })
        assertEquals("tag/NONE", codexItemGroups(CodexItemSort.TAG).last().key)
        assertEquals("element/KINETIC", codexItemGroups(CodexItemSort.ELEMENT).last().key)
        val groupKeys = CodexItemSort.entries.flatMap(::codexItemGroups).map { it.key }
        assertEquals(groupKeys.size, groupKeys.toSet().size)
    }

    @Test
    fun slotsAreFixedSoDiscoveringAnItemFillsItsOwnSlot() {
        for (sort in CodexItemSort.entries) {
            val open = rows(sort, mixed.ids).entries.map { it.key }
            for (discovered in listOf(emptySet(), setOf(5, 9), setOf(0, 2, 3, 7))) for (language in AppLanguage.entries) {
                val matrix = rows(sort, discovered, language = language)
                assertEquals(open, matrix.entries.map { it.key }, "$sort with $discovered in $language")
                // The Catalog tab's Items entries are this matrix in reading order.
                assertEquals(matrix.entries, codexCatalogEntries(0, "", CodexItemFilter.ALL, model(discovered), catalog.copy(items = mixed),
                    progress(), language, sort))
            }
        }
        fun slots(sort: CodexItemSort, key: String) =
            rows(sort).rows.single { it.group.key == key }.cells.map { it.entry.key.removePrefix("item/").toInt() }
        // Tag and element rows: rarity, then unlock level, then id.
        assertEquals(listOf(9, 10, 0, 6, 8, 5), slots(CodexItemSort.TAG, "tag/RAM"))
        assertEquals(listOf(9, 10, 6, 1, 8, 3, 5), slots(CodexItemSort.ELEMENT, "element/KINETIC"))
        // Rarity rows: first tag (untagged last), first element (Kinetic last), unlock level, then id.
        assertEquals(listOf(0, 9, 10, 6, 4), slots(CodexItemSort.RARITY, "rarity/COMMON"))
        assertEquals(listOf(7, 1), slots(CodexItemSort.RARITY, "rarity/UNCOMMON"))
        assertEquals(listOf(8, 2), slots(CodexItemSort.RARITY, "rarity/RARE"))
        // Discovering an item turns its own hatched cell into the item, in place.
        val hatched = rows(CodexItemSort.TAG, emptySet()).entries
        val filled = rows(CodexItemSort.TAG, setOf(9)).entries
        assertEquals(hatched.indexOfFirst { it.key == "item/9" }, filled.indexOfFirst { it.key == "item/9" })
        assertEquals(CodexIcon.Unknown, hatched.single { it.key == "item/9" }.icon)
        assertIs<CodexIcon.Item>(filled.single { it.key == "item/9" }.icon)
    }

    @Test
    fun undiscoveredAndRebirthLockedCellsNeverCarryIdentity() {
        for (language in AppLanguage.entries) for (sort in CodexItemSort.entries) {
            rows(sort, emptySet(), language = language).rows.forEach { row ->
                val name = row.group.name(language)
                row.cells.forEach { cell ->
                    val entry = cell.entry
                    val item = mixed[entry.key.removePrefix("item/").toInt()]
                    val where = "${entry.key} in $sort, $language"
                    assertFalse(entry.discovered, where)
                    assertEquals(name, entry.spoken, where)
                    assertEquals("", entry.description, where)
                    assertNull(entry.detail, where)
                    assertEquals(0, entry.rarity, where)
                    assertEquals(CODEX_NONE, entry.kind, where)
                    listOf(item.name, item.name.localizedContent(language), item.description, "Item").forEach { secret ->
                        assertFalse(secret in entry.summary, "$where announces $secret: ${entry.summary}")
                    }
                    if (item.id == 5) {
                        assertEquals(CodexIcon.Locked(2), entry.icon, where)
                        assertEquals(language.text(SessionText.REBIRTH_LEVEL, 2), entry.title, where)
                    } else {
                        assertEquals(CodexIcon.Unknown, entry.icon, where)
                        assertEquals(language.text(SessionText.UNKNOWN_DISCOVERY), entry.title, where)
                    }
                }
            }
        }
        // Row names in the visible language, the untagged and the Kinetic rows included.
        assertEquals("No tag", CodexItemGroup.Tag(null).name(AppLanguage.English))
        assertEquals("Без тега", CodexItemGroup.Tag(null).name(AppLanguage.Russian))
        assertEquals("Kinetic", CodexItemGroup.Element(null).name(AppLanguage.English))
        assertEquals("Кинетика", CodexItemGroup.Element(null).name(AppLanguage.Russian))
        assertEquals("Таран", CodexItemGroup.Tag(BuildTag.RAM).name(AppLanguage.Russian))
        assertEquals("Легендарный", CodexItemGroup.Rarity(ItemRarity.LEGENDARY).name(AppLanguage.Russian))
        // Once its tier is reached the gated item is an anonymous cell like any other.
        assertEquals(CodexIcon.Unknown, rows(CodexItemSort.TAG, emptySet(), rebirth = 2).entries.single { it.key == "item/5" }.icon)
    }

    @Test
    fun searchAndInBuildShowOnlyMatchingDiscoveredItemsDropEmptyRowsAndKeepCounts() {
        val discovered = setOf(0, 2, 6, 10)
        val open = rows(CodexItemSort.TAG, discovered)
        val counts = open.rows.associate { it.group.key to (it.found to it.total) }
        assertEquals(3 to 6, counts["tag/RAM"])
        assertEquals(1 to 2, counts["tag/NONE"])
        assertEquals(0 to 1, counts["tag/DRIVE"])
        // A search shows only matching discovered items in their slots, drops empty rows and keeps the counts.
        val searched = rows(CodexItemSort.TAG, discovered, search = "iTeM")
        assertEquals(listOf("tag/RAM", "tag/NONE"), searched.rows.map { it.group.key })
        assertEquals(listOf("item/10", "item/0", "item/6", "item/2"), searched.entries.map { it.key })
        assertTrue(searched.entries.all { it.discovered && it.icon is CodexIcon.Item })
        searched.rows.forEach { assertEquals(counts[it.group.key], it.found to it.total) }
        // The result is the open matrix filtered by title, never reordered.
        for (query in listOf("item", "1", "ITEM 6", "zzz")) {
            assertEquals(open.entries.filter { it.discovered && it.title.contains(query, ignoreCase = true) },
                rows(CodexItemSort.TAG, discovered, search = query).entries, query)
        }
        assertTrue(rows(CodexItemSort.TAG, discovered, search = "zzz").rows.isEmpty())
        // Spaces alone are no search: the open matrix, every found item and every hatch in its slot.
        for (blank in listOf(" ", "  ", "\t")) {
            assertEquals(open, rows(CodexItemSort.TAG, discovered, search = blank), "search '$blank'")
            assertEquals("", codexItemQuery(blank))
        }
        assertEquals(" ra", codexItemQuery(" ra"))
        // In build: only the items held this run, with the same counts.
        val stacks = List(mixed.size) { if (it == 6) 1 else 0 }.toImmutableList()
        val run = CodexRunStacks(stacks, GameplayBuildSummaryProjection(GameplayInstanceId(RunId(1)), GameplayRevision.ZERO, stacks))
        val held = rows(CodexItemSort.TAG, discovered, filter = CodexItemFilter.IN_BUILD, runStacks = run)
        assertEquals(listOf("item/6"), held.entries.map { it.key })
        assertEquals(3 to 6, held.rows.single().let { it.found to it.total })
    }

    @Test
    fun geometryClampsCellsAndKeepsOneSizeForEverySort() {
        // 1440x810 at 125 %: the board, three label columns beside the 17 reference columns.
        val board = codexItemGeometry(710f, 100f, 17, wide = true, ui = 1f)
        assertEquals(20 to 3, board.columns to board.labelSpan)
        assertEquals(31.7f, board.cellDp, 0.001f)
        // The board pass holds cells at 22 dp on a wide layout; one dp less packs the rows instead.
        assertEquals(CodexItemGeometry(18, 1, 22f), codexItemGeometry(464f, 20f, 17, wide = true, ui = 1f))
        val packed = codexItemGeometry(463f, 20f, 17, wide = true, ui = 1f)
        assertEquals(17 to 1, packed.columns to packed.labelSpan)
        assertTrue(packed.cellDp >= CODEX_ITEM_MIN_CELL_WIDE_DP)
        // 1000x700: the packed pass gives the label the columns it needs beside 24 dp cells.
        assertEquals(CodexItemGeometry(12, 4, 24f), codexItemGeometry(332f, 100f, 17, wide = true, ui = 1f))
        // A phone keeps touch-sized cells (36 dp at least).
        assertEquals(CodexItemGeometry(8, 2, 40.75f), codexItemGeometry(354f, 70f, 17, wide = false, ui = 1f))
        // Cells above 40 dp × the text factor gain columns instead.
        assertEquals(CodexItemGeometry(16, 2, 40.625f), codexItemGeometry(710f, 60f, 9, wide = true, ui = 1f))
        val large = codexItemGeometry(710f, 60f, 9, wide = true, ui = 1.75f)
        assertEquals(10 to 1, large.columns to large.labelSpan)
        assertEquals(67.4f, large.cellDp, 0.001f)
        // Side labels need five cell columns beside them, not four...
        assertEquals(CodexItemGeometry(7, 2, 36f), codexItemGeometry(276f, 60f, 17, wide = false, ui = 1f))
        assertEquals(CodexItemGeometry(6, 0, 36f), codexItemGeometry(236f, 60f, 17, wide = false, ui = 1f))
        // ...and at most 0.4 of the columns; otherwise each row stacks its heading above its cells.
        assertEquals(CodexItemGeometry(10, 4, 36f), codexItemGeometry(396f, 150f, 17, wide = false, ui = 1f))
        assertEquals(CodexItemGeometry(9, 0, 36f), codexItemGeometry(356f, 150f, 17, wide = false, ui = 1f))
        // Any width: the columns fill it, cells keep the minimum and a side label fits its columns.
        for (inner in 180..1400 step 7) for (need in listOf(40f, 90f, 160f)) for (wide in listOf(true, false)) for (ui in listOf(1f, 1.4f, 1.75f)) {
            val geometry = codexItemGeometry(inner.toFloat(), need, 17, wide, ui)
            val where = "$inner dp, label $need, wide $wide, text $ui: $geometry"
            val minimum = if (wide) CODEX_ITEM_MIN_CELL_WIDE_DP else CODEX_ITEM_MIN_CELL_TOUCH_DP
            assertEquals(inner.toFloat(), geometry.columns * geometry.cellDp + (geometry.columns - 1) * CODEX_ITEM_GAP_DP, 0.01f, where)
            assertTrue(geometry.cellDp >= minimum - 0.001f, where)
            assertTrue(geometry.labelSpan in 0 until geometry.columns, where)
            val label = geometry.labelSpan * geometry.cellDp + (geometry.labelSpan - 1) * CODEX_ITEM_GAP_DP
            if (geometry.labelSpan > 0) assertTrue(label >= need - 0.001f, where)
        }
        // 1280x720 in Russian: no label column fits the 108 dp label at full size, so the rows would pack beside
        // a five-column label...
        val unfloored = codexItemGeometry(550f, 108f, 17, wide = true, ui = 1f)
        assertEquals(21 to 5, unfloored.columns to unfloored.labelSpan)
        // ...but its names shrunk to their floor (96 dp) fit four columns of 22.4 dp: the board keeps its bands.
        val floored = codexItemGeometry(550f, 108f, 17, wide = true, ui = 1f, labelFloorDp = 96f)
        assertEquals(21 to 4, floored.columns to floored.labelSpan)
        assertEquals(22.381f, floored.cellDp, 0.001f)
        assertEquals(CodexItemLayout.BANDED, codexItemGrid(rows(CodexItemSort.TAG, emptySet(), items = boardShape).rows, floored, CodexItemSort.TAG).layout)
        // The floor is a fallback only: a label that fits at full size keeps its columns, and a floor no smaller changes nothing.
        assertEquals(board, codexItemGeometry(710f, 100f, 17, wide = true, ui = 1f, labelFloorDp = 50f))
        assertEquals(unfloored, codexItemGeometry(550f, 108f, 17, wide = true, ui = 1f, labelFloorDp = 120f))
        // Where even the floor does not fit, the rows pack beside the full label.
        assertEquals(unfloored, codexItemGeometry(550f, 108f, 17, wide = true, ui = 1f, labelFloorDp = 102f))
        // A width with no room left still yields one column.
        assertEquals(1, codexItemGeometry(0f, 100f, 17, wide = true, ui = 1f).columns)
        // One geometry for every sort: it reads the whole catalog, never the rows.
        val geometry = codexItemGeometry(400f, 80f, codexItemRefColumns(mixed), wide = true, ui = 1f)
        val grids = CodexItemSort.entries.map { sort -> codexItemGrid(rows(sort).rows, geometry, sort) }
        assertEquals(listOf(geometry), grids.map { it.geometry }.distinct())
    }

    @Test
    fun bandsAlignWhenTheyFitAndRowsWrapOtherwise() {
        val matrix = rows(CodexItemSort.TAG, emptySet(), items = boardShape)
        // The widest rows need 17 band columns: they fit beside a 3-column label in 20 columns...
        val banded = codexItemGrid(matrix.rows, CodexItemGeometry(20, 3, 30f), CodexItemSort.TAG)
        assertEquals(CodexItemLayout.BANDED, banded.layout)
        assertEquals("items/head", banded.items.first().key)
        assertEquals(20, banded.items.first().span)
        banded.items.filterIsInstance<CodexGridItem.Cell>().forEach { item ->
            val band = banded.bands.single { it.rarity == item.cell.rarity }
            assertTrue(item.column in band.column until band.column + band.width, "${item.key} outside its rarity's columns")
        }
        assertGridShape(banded, matrix)
        // Every banded line holds its row's label beside its cells.
        assertEquals(banded.items.filterIsInstance<CodexGridItem.Label>().map { it.line }.toSet(), banded.labelled)
        banded.rows.indices.forEach { assertTrue(banded.items[banded.rowStart(it)] is CodexGridItem.Label, "row $it starts with its label") }
        // ...but not in 19, so the rows pack, each on one line: nothing to set apart.
        val packed = codexItemGrid(matrix.rows, CodexItemGeometry(19, 3, 30f), CodexItemSort.TAG)
        assertEquals(CodexItemLayout.PACKED, packed.layout)
        assertTrue(packed.bands.isEmpty())
        assertTrue(packed.items.none { it is CodexGridItem.Rule })
        assertGridShape(packed, matrix)
        // A grid too short for the header packs the rows even where the bands would fit.
        val short = codexItemGrid(matrix.rows, CodexItemGeometry(20, 3, 30f), CodexItemSort.TAG, banded = false)
        assertEquals(CodexItemLayout.PACKED, short.layout)
        assertTrue(short.items.none { it is CodexGridItem.Head })
        assertGridShape(short, matrix)
        // Packed rows wrap behind a gutter: five cells per line beside a 3-column label.
        val wrapped = codexItemGrid(matrix.rows, CodexItemGeometry(8, 3, 30f), CodexItemSort.TAG)
        assertGridShape(wrapped, matrix)
        wrapped.rows.forEachIndexed { index, row ->
            val lines = wrapped.items.filter { it.rowIndex == index }.map { it.line }.distinct()
            assertEquals((row.cells.size + 4) / 5, lines.size, row.group.key)
            assertEquals(lines.size - 1, wrapped.items.count { it.rowIndex == index && it.key.startsWith("items/gutter/") })
            // Only a row's first line holds its label; its continuation lines are a cell tall.
            assertTrue(lines.first() in wrapped.labelled, row.group.key)
            lines.drop(1).forEach { assertFalse(it in wrapped.labelled, "${row.group.key} line $it") }
            // A rule line sets every row but the first apart from the one above.
            val start = wrapped.rowStart(index)
            assertEquals(index, wrapped.items[start].rowIndex)
            if (index > 0) assertEquals("items/rule/${row.group.key}", wrapped.items[start - 1].key)
        }
        assertEquals(wrapped.rows.size - 1, wrapped.items.count { it is CodexGridItem.Rule })
        assertEquals(-1, wrapped.rowStart(wrapped.rows.size))
        // No label columns: a full-width heading above each row's cells.
        val stacked = codexItemGrid(matrix.rows, CodexItemGeometry(8, 0, 37f), CodexItemSort.TAG)
        assertEquals(CodexItemLayout.STACKED, stacked.layout)
        assertTrue(stacked.items.filterIsInstance<CodexGridItem.Label>().all { it.stacked && it.span == 8 })
        assertGridShape(stacked, matrix)
        // The Rarity sort never bands, however wide the grid.
        val rarity = rows(CodexItemSort.RARITY, emptySet(), items = boardShape)
        val rarityGrid = codexItemGrid(rarity.rows, CodexItemGeometry(200, 3, 30f), CodexItemSort.RARITY)
        assertEquals(CodexItemLayout.PACKED, rarityGrid.layout)
        assertGridShape(rarityGrid, rarity)
        // The Element sort bands like the Tag sort when it fits.
        val elements = rows(CodexItemSort.ELEMENT, mixed.ids)
        val elementGrid = codexItemGrid(elements.rows, CodexItemGeometry(12, 2, 30f), CodexItemSort.ELEMENT)
        assertEquals(CodexItemLayout.BANDED, elementGrid.layout)
        assertGridShape(elementGrid, elements)
        // A search keeps every line whole.
        val searched = rows(CodexItemSort.TAG, setOf(0, 6, 9), search = "item")
        assertGridShape(codexItemGrid(searched.rows, CodexItemGeometry(12, 2, 30f), CodexItemSort.TAG), searched)
    }

    @Test
    fun theShippedTagShapePacksOnTheBoardAndBandsOnAWideWindow() {
        assertEquals(221, shipped.size)
        assertEquals(40, codexItemRefColumns(shipped))
        assertEquals(9, codexItemRefColumns(codexTestCatalog().items))
        val matrix = rows(CodexItemSort.TAG, emptySet(), items = shipped)
        assertEquals(listOf(22, 27, 20, 26, 20, 17, 19, 29, 20, 21), matrix.rows.map { it.cells.size })
        // On the 710 dp board the 40 band columns no longer fit beside the labels: every row wraps, packed.
        val board = codexItemGrid(matrix.rows, codexItemGeometry(710f, 100f, codexItemRefColumns(shipped), wide = true, ui = 1f), CodexItemSort.TAG)
        assertEquals(CodexItemLayout.PACKED, board.layout)
        assertGridShape(board, matrix)
        assertEquals(22, board.items.maxOf { it.line } + 1)
        // A 1,280 dp window bands them: Common to Legendary beside a 4-column label, the header and one line per row.
        val wide = codexItemGrid(matrix.rows, codexItemGeometry(1_280f, 100f, codexItemRefColumns(shipped), wide = true, ui = 1f), CodexItemSort.TAG)
        assertEquals(CodexItemLayout.BANDED, wide.layout)
        assertEquals(ItemRarity.entries, wide.bands.map { it.rarity })
        assertEquals(listOf(11, 8, 7, 11, 3), wide.bands.map { it.width })
        assertEquals(listOf(4, 15, 23, 30, 41), wide.bands.map { it.column })
        assertEquals(11, wide.items.maxOf { it.line } + 1)
        assertGridShape(wide, matrix)
    }

    @Test
    fun arrowKeysMoveByReadingOrderAndNearestColumnAndStopAtEdges() {
        val matrix = rows(CodexItemSort.TAG, items = arrows)
        // Bands: Common 2, Uncommon 1, Rare 2 beside a one-column label.
        val banded = codexItemGrid(matrix.rows, CodexItemGeometry(6, 1, 30f), CodexItemSort.TAG)
        assertEquals(CodexItemLayout.BANDED, banded.layout)
        fun CodexItemGrid.move(from: Int, move: CodexItemMove): Int? = codexItemNeighbour(this, "item/$from", move)?.removePrefix("item/")?.toInt()
        // Left and Right follow the reading order across lines and rows, and stop at the ends.
        assertEquals(1, banded.move(0, CodexItemMove.RIGHT))
        assertEquals(3, banded.move(2, CodexItemMove.RIGHT))
        assertEquals(2, banded.move(3, CodexItemMove.LEFT))
        assertNull(banded.move(0, CodexItemMove.LEFT))
        assertNull(banded.move(6, CodexItemMove.RIGHT))
        // Up and Down keep the column, skip blanks and take the nearest cell, the left one on a tie.
        assertEquals(3, banded.move(0, CodexItemMove.DOWN))
        assertEquals(3, banded.move(1, CodexItemMove.DOWN))
        assertEquals(4, banded.move(2, CodexItemMove.DOWN))
        assertEquals(5, banded.move(4, CodexItemMove.DOWN))
        assertEquals(1, banded.move(4, CodexItemMove.UP))
        assertEquals(4, banded.move(6, CodexItemMove.UP))
        assertNull(banded.move(0, CodexItemMove.UP))
        assertNull(banded.move(5, CodexItemMove.DOWN))
        // Home and End reach the ends of the row; Page Up and Page Down the first cell of the rows around it.
        assertEquals(0, banded.move(2, CodexItemMove.HOME))
        assertEquals(2, banded.move(0, CodexItemMove.END))
        assertNull(banded.move(0, CodexItemMove.HOME))
        assertNull(banded.move(6, CodexItemMove.END))
        assertEquals(3, banded.move(1, CodexItemMove.PAGE_DOWN))
        assertEquals(5, banded.move(4, CodexItemMove.PAGE_DOWN))
        assertNull(banded.move(6, CodexItemMove.PAGE_DOWN))
        assertEquals(3, banded.move(6, CodexItemMove.PAGE_UP))
        assertNull(banded.move(2, CodexItemMove.PAGE_UP))
        assertNull(codexItemNeighbour(banded, "item/99", CodexItemMove.RIGHT))
        // Packed rows wrap: Down from a line reaches the row's continuation line.
        val packed = codexItemGrid(matrix.rows, CodexItemGeometry(3, 1, 30f), CodexItemSort.TAG)
        assertEquals(CodexItemLayout.PACKED, packed.layout)
        assertEquals(2, packed.move(1, CodexItemMove.DOWN))
        assertEquals(2, packed.move(3, CodexItemMove.UP))
        assertEquals(3, packed.move(2, CodexItemMove.DOWN))
        assertEquals(2, packed.move(0, CodexItemMove.END))
        // Stacked headings hold no cell: Down passes over them.
        val stacked = codexItemGrid(matrix.rows, CodexItemGeometry(2, 0, 30f), CodexItemSort.TAG)
        assertEquals(3, stacked.move(2, CodexItemMove.DOWN))
        assertEquals(2, stacked.move(3, CodexItemMove.UP))
    }

    @Test
    fun cellMarksStayInsideTheCellClearOfTheGlyph() {
        val eps = 0.001f
        for (dp in listOf(22f, 24f, 30f, 36f, 40f, 56f)) for (density in listOf(1f, 1.5f, 2f)) {
            val cell = dp * density
            val marks = codexItemCellMarks(cell, density)
            val where = "$dp dp at $density"
            val reach = marks.glyphRadius * CODEX_GLYPH_REACH
            val inset = 2f * density
            assertEquals(inset, marks.inset, eps)
            // Inside the cell's inset.
            listOf(marks.notch, marks.strip, marks.bar).forEach { mark ->
                val far = cell - inset + eps
                val inside = mark.left >= inset - eps && mark.top >= inset - eps && mark.right <= far && mark.bottom <= far
                assertTrue(inside, "$where: $mark leaves the inset")
            }
            // Clear of the glyph's reach: the notch's triangle by its diagonal, the strip and the bar as rectangles.
            val center = marks.glyphCenter
            val diagonal = (center.x + center.y - (marks.notch.left + marks.notch.bottom)) / sqrt(2f)
            assertTrue(diagonal >= reach - eps, "$where: the NEW notch reaches the glyph ($diagonal < $reach)")
            assertTrue(distance(marks.strip, center.x, center.y) >= reach - eps, "$where: the element strip reaches the glyph")
            assertTrue(distance(marks.bar, center.x, center.y) >= reach - eps, "$where: the stack bar reaches the glyph")
            // Apart from each other: the strip ends 1 dp above the bar.
            assertTrue(marks.notch.right < marks.strip.left && marks.notch.bottom < marks.bar.top, where)
            assertEquals(marks.bar.top - density, marks.strip.bottom, eps)
            // Never thinner than their minimums.
            assertTrue(marks.notch.width >= 6f * density - eps, where)
            assertTrue(marks.strip.width >= 2f * density - eps && marks.bar.height >= 2f * density - eps, where)
            // The Reborn rim's outer edge sits 1 dp inside the inset.
            assertEquals(inset + density, marks.rim.left - marks.rimWidth / 2f, eps)
            assertEquals(cell - inset - density, marks.rim.bottom + marks.rimWidth / 2f, eps)
            // Segments tile their mark, a hairline apart.
            for (count in 1..3) {
                val bars = (0 until count).map { marks.barSegment(it, count) }
                assertEquals(marks.bar.left, bars.first().left, eps)
                assertEquals(marks.bar.right, bars.last().right, eps)
                bars.zipWithNext().forEach { (a, b) -> assertEquals(marks.hairline, b.left - a.right, eps) }
                assertTrue(bars.all { it.top > marks.bar.top && it.bottom <= marks.bar.bottom + eps && it.width > 0f }, where)
                val strips = (0 until count).map { marks.stripSegment(it, count) }
                assertEquals(marks.strip.top, strips.first().top, eps)
                assertEquals(marks.strip.bottom, strips.last().bottom, eps)
                assertTrue(strips.all { it.left > marks.strip.left && it.right <= marks.strip.right + eps && it.height > 0f }, where)
            }
        }
    }

    /** Shape every layout keeps: whole lines, unique keys and the rows' reading order. */
    private fun assertGridShape(grid: CodexItemGrid, matrix: CodexItemRows) {
        val columns = grid.geometry.columns
        grid.items.groupBy { it.line }.forEach { (line, items) ->
            assertEquals(columns, items.sumOf { it.span }, "line $line of ${grid.layout}")
            var column = 0
            items.forEach { item ->
                assertEquals(column, item.column, "${item.key} on line $line")
                column += item.span
            }
            assertEquals(1, items.map { it.rowIndex }.distinct().size, "line $line mixes rows")
        }
        assertEquals(grid.items.map { it.line }.sorted(), grid.items.map { it.line })
        val keys = grid.items.map { it.key }
        assertEquals(keys.size, keys.toSet().size, "duplicate lazy keys in ${grid.layout}")
        assertEquals(matrix.entries.map { it.key }, grid.items.filterIsInstance<CodexGridItem.Cell>().map { it.key })
        assertEquals(matrix.entries.map { it.key }, grid.cells.map { it.key })
        grid.cells.forEachIndexed { order, position ->
            val item = grid.items[position.gridIndex]
            assertEquals(position.key, item.key)
            assertEquals(position.line to position.column, item.line to item.column)
            assertEquals(order, position.order)
            assertEquals(position, grid.positions.getValue(position.key))
        }
    }

    private fun distance(rect: Rect, x: Float, y: Float): Float {
        val dx = max(max(rect.left - x, 0f), x - rect.right)
        val dy = max(max(rect.top - y, 0f), y - rect.bottom)
        return sqrt(dx * dx + dy * dy)
    }

    private val List<ItemDefinition>.ids: Set<Int> get() = mapTo(HashSet()) { it.id }

    private fun model(discovered: Set<Int>, items: ImmutableList<ItemDefinition> = mixed, runStacks: CodexRunStacks = CodexRunStacks()) =
        CodexRenderModel(discovered.toImmutableSet(), runStacks, items)

    private fun progress(rebirth: Int = 0) = codexTestProgress(emptySet()).copy(rebirthProgress = RebirthProgress(level = rebirth, highestCleared = rebirth - 1))

    private fun rows(
        sort: CodexItemSort,
        discovered: Set<Int> = mixed.ids,
        search: String = "",
        filter: CodexItemFilter = CodexItemFilter.ALL,
        language: AppLanguage = AppLanguage.English,
        runStacks: CodexRunStacks = CodexRunStacks(),
        items: ImmutableList<ItemDefinition> = mixed,
        rebirth: Int = 0,
    ): CodexItemRows =
        codexItemRows(sort, search, filter, model(discovered, items, runStacks), catalog.copy(items = items), progress(rebirth), language)

    private fun item(
        id: Int,
        rarity: ItemRarity,
        tags: List<BuildTag> = emptyList(),
        elements: List<StatusElement> = emptyList(),
        unlock: Int = 1,
        rebirth: Int = 0,
    ) = ItemDefinition(id, "Item $id", "Test item $id", rarity, tags.toImmutableList(), elements.toImmutableList(),
        stats = immutableListOf(StatDelta(ItemEffect.IMPACT_DAMAGE, 0.1f)), maxStacks = 1, unlockLevel = unlock, minimumRebirth = rebirth)
}
