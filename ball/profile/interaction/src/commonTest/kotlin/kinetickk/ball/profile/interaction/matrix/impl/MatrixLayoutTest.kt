// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.matrix.impl

import androidx.compose.ui.geometry.Rect
import kinetickk.ball.content.api.MatrixBranch
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.profile.api.MatrixProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.interaction.ProfileLayoutMode
import kinetickk.ball.profile.interaction.TestMatrixNodes
import kinetickk.ball.profile.interaction.profileFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatrixLayoutTest {
    private val model = MatrixProfileSnapshot(PlayerEconomy(1_000L), MatrixProgress(), 0).toRenderModel(TestMatrixNodes)
    private val frames = listOf(1440f to 810f, 1280f to 720f, 844f to 390f, 390f to 844f, 360f to 640f)

    private fun layout(width: Float, height: Float, textScale: Float = 1f): MatrixLayout =
        matrixLayout(profileFrame(width, height, 1f), model, textScale, backWidth = 80f)

    @Test
    fun everyModeKeepsTilesApartInsideTheirBranchColumnAndRowsInPrerequisiteOrder() {
        for ((width, height) in frames) for (scale in listOf(0.8f, 1f, 1.4f)) {
            val layout = layout(width, height, scale)
            val context = "${width}x$height @$scale"
            assertEquals(model.nodes.size, layout.tiles.size, context)
            layout.tiles.forEachIndexed { index, tile ->
                assertTrue(tile.width > 0f && tile.height > 0f, context)
                assertTrue(tile.left >= 0f && tile.right <= layout.gridViewport.width + 0.5f, "$context ${model.nodes[index].id} $tile")
                assertTrue(tile.bottom <= layout.gridContentHeight + 0.5f, "$context ${model.nodes[index].id} below the content")
                val header = layout.branchHeaders[model.nodes[index].branch.ordinal]
                assertTrue(tile.left >= header.left - 0.5f && tile.right <= header.right + 0.5f && tile.top >= header.bottom,
                    "$context ${model.nodes[index].id} outside its branch column")
                for (other in index + 1 until layout.tiles.size) {
                    assertTrue(!tile.overlapsBy(layout.tiles[other]), "$context ${model.nodes[index].id} overlaps ${model.nodes[other].id}")
                }
            }
            model.nodes.forEachIndexed { index, node ->
                node.prerequisites.forEach { id ->
                    val parent = layout.tiles[model.nodes.indexOfFirst { it.id == id }]
                    assertTrue(parent.bottom < layout.tiles[index].top, "$context $id above ${node.id}")
                }
            }
            assertTrue(layout.buy.bottom <= height + 0.5f, "$context buy on screen")
            assertTrue(layout.detailViewport.bottom <= layout.buy.top + 0.5f || !layout.buyPinned, context)
        }
    }

    @Test
    fun theRegularBoardShowsAllFourBranchesSideBySideWithoutScrolling() {
        val layout = layout(1440f, 810f)
        assertEquals(ProfileLayoutMode.REGULAR, layout.frame.mode)
        assertEquals(0f, layout.gridScrollMax)
        val tops = layout.branchHeaders.map { it.top }.distinct()
        assertEquals(1, tops.size, "one block row")
        assertTrue(layout.branchHeaders.zipWithNext().all { (left, right) -> left.right < right.left })
        // The grid ends left of the detail panel.
        assertTrue(layout.gridViewport.right <= layout.detailViewport.left)
    }

    @Test
    fun phonesStackTheBranchesAndScroll() {
        val landscape = layout(844f, 390f)
        assertEquals(2, landscape.branchHeaders.map { it.top }.distinct().size)
        assertTrue(landscape.gridScrollMax > 0f)
        val portrait = layout(390f, 844f)
        assertEquals(MatrixBranch.entries.size, portrait.branchHeaders.map { it.top }.distinct().size)
        assertTrue(portrait.gridScrollMax > 0f)
        assertTrue(portrait.detailViewport.top >= portrait.gridViewport.bottom)
    }

    @Test
    fun tilesMeetTheTouchTargetOnPhones() {
        for ((width, height) in listOf(844f to 390f, 390f to 844f, 360f to 640f)) {
            val layout = layout(width, height)
            layout.tiles.forEach { assertTrue(it.width >= 48f && it.height >= 48f, "${width}x$height $it") }
        }
    }

    @Test
    fun aPressMapsToTheTileUnderItAtTheCurrentScrollAndToBuyAndBack() {
        val layout = layout(390f, 844f)
        val state = MatrixState(model, selected = MatrixNodeId.CRYO_LOOP)
        val index = model.nodes.indexOfFirst { it.id == MatrixNodeId.PROSPECTOR }
        val tile = layout.tiles[index]
        val scroll = (tile.top - 20f).coerceIn(0f, layout.gridScrollMax)
        val x = layout.gridViewport.left + tile.center.x
        val y = layout.gridViewport.top + tile.center.y - scroll
        assertEquals(MatrixAction.Activate(MatrixNodeId.PROSPECTOR), resolveMatrixPress(layout, state, scroll, x, y))
        assertEquals(MatrixAction.PurchaseRequested(MatrixNodeId.CRYO_LOOP),
            resolveMatrixPress(layout, state, scroll, layout.buy.center.x, layout.buy.center.y))
        assertEquals(MatrixAction.Back, resolveMatrixPress(layout, state, scroll, layout.back.center.x, layout.back.center.y))
        // Between two tiles is no action.
        val gapY = layout.gridViewport.top + layout.tiles[0].bottom + 2f
        assertNull(resolveMatrixPress(layout, state, 0f, layout.gridViewport.left + layout.tiles[0].center.x, gapY))
    }

    @Test
    fun linksJoinEachPrerequisiteToItsNodeAndRevealScrollBringsTilesIntoView() {
        val layout = layout(844f, 390f)
        val links = matrixLinks(layout, model)
        assertEquals(model.nodes.sumOf { it.prerequisites.size }, links.size)
        links.forEach {
            assertTrue(it.from.y < it.to.y)
            assertTrue(it.bendY > it.from.y && it.bendY < it.to.y, "the line turns between the rows")
        }

        val last = layout.tiles.last()
        val target = matrixRevealScroll(0f, last, layout.gridViewport.height, layout.gridScrollMax, 20f)
        val scroll = assertNotNull(target)
        assertTrue(last.top >= scroll && last.bottom <= scroll + layout.gridViewport.height + 0.5f)
        assertNull(matrixRevealScroll(0f, layout.tiles.first(), layout.gridViewport.height, layout.gridScrollMax, 20f))
    }

    private fun Rect.overlapsBy(other: Rect): Boolean =
        left < other.right - 0.5f && other.left < right - 0.5f && top < other.bottom - 0.5f && other.top < bottom - 0.5f
}
