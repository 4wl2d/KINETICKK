// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.matrix.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kinetickk.ball.content.api.MatrixBranch
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.ProfileLayoutMode
import kinetickk.ball.profile.interaction.matrix.api.MatrixRenderModel
import kinetickk.ball.profile.interaction.profileHeaderBackRect
import kinetickk.ball.profile.interaction.profileLayoutGrow
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * One geometry for the Matrix: drawing, Compose placement and [resolveMatrixPress]. Branch headers
 * and tiles are in grid content coordinates (add [gridViewport] top-left, subtract the grid scroll);
 * detail rects are in screen coordinates for a detail scroll of 0.
 */
internal class MatrixLayout(
    val frame: ProfileFrame,
    val back: Rect,
    val gridViewport: Rect,
    val gridContentHeight: Float,
    /** Branch column headers by [MatrixBranch.ordinal]. */
    val branchHeaders: List<Rect>,
    /** Node tiles in model order. */
    val tiles: List<Rect>,
    /** Vertical gap between two tile rows (px); prerequisite lines turn in its middle. */
    val rowGap: Float,
    val detailViewport: Rect,
    val detailContentHeight: Float,
    val tag: Rect,
    val name: Rect,
    val description: Rect,
    val rank: Rect,
    val status: Rect,
    val buy: Rect,
    /** True when the details overflow and [buy] is pinned under them. */
    val buyPinned: Boolean,
) {
    val gridScrollMax: Float get() = max(0f, gridContentHeight - gridViewport.height)
    val detailScrollMax: Float get() = max(0f, detailContentHeight - detailViewport.height)
}

/** Board type sizes per mode (design px before the frame scale and the text-size setting). */
internal class MatrixType(
    val branch: Float,
    val tileName: Float,
    val tileStatus: Float,
    val name: Float,
    val body: Float,
    val mono: Float,
    val buy: Float,
)

internal fun matrixType(mode: ProfileLayoutMode): MatrixType = when (mode) {
    ProfileLayoutMode.REGULAR -> MatrixType(20f, 17f, 13f, 30f, 18f, 11f, 34f)
    ProfileLayoutMode.COMPACT_LANDSCAPE -> MatrixType(14f, 13f, 10f, 18f, 13f, 9.5f, 20f)
    ProfileLayoutMode.COMPACT_PORTRAIT -> MatrixType(15f, 14f, 10.5f, 18f, 14f, 10f, 20f)
}

/** Grid spacing per mode, in board px: branch columns per block row, gaps and tile height. */
private class MatrixGridSpec(
    val branchesPerRow: Int,
    val columnGap: Float,
    val laneGap: Float,
    val headerHeight: Float,
    val tileHeight: Float,
    val rowGap: Float,
    val blockGap: Float,
)

private fun matrixGridSpec(mode: ProfileLayoutMode): MatrixGridSpec = when (mode) {
    ProfileLayoutMode.REGULAR -> MatrixGridSpec(4, 24f, 12f, 34f, 124f, 30f, 24f)
    ProfileLayoutMode.COMPACT_LANDSCAPE -> MatrixGridSpec(2, 16f, 8f, 26f, 72f, 18f, 18f)
    ProfileLayoutMode.COMPACT_PORTRAIT -> MatrixGridSpec(1, 0f, 10f, 28f, 78f, 18f, 20f)
}

/**
 * [textScale] is the board-relative text multiplier ([kinetickk.ball.profile.interaction.profileTextScale]).
 * [descriptionHeight] measures the selected node's description at a width; without it the slot
 * keeps three lines.
 */
internal fun matrixLayout(
    frame: ProfileFrame,
    model: MatrixRenderModel,
    textScale: Float,
    backWidth: Float,
    descriptionHeight: ((width: Float) -> Float)? = null,
): MatrixLayout {
    fun d(value: Float) = frame.d(value)
    val t = textScale.coerceIn(0.75f, 2f)
    val grow = profileLayoutGrow(t, 0.55f)
    val back = profileHeaderBackRect(frame, backWidth)
    val type = matrixType(frame.mode)
    val spec = matrixGridSpec(frame.mode)
    val pad = d(MATRIX_GRID_PAD)
    val gridLeft: Float
    val gridRight: Float
    val gridTop: Float
    val gridBottom: Float
    val detailLeft: Float
    val detailRight: Float
    val detailTop: Float
    val detailBottom: Float
    when (frame.mode) {
        ProfileLayoutMode.REGULAR -> {
            gridLeft = frame.x(44f)
            gridRight = frame.x(994f)
            gridTop = d(106f)
            gridBottom = frame.height - d(12f)
            detailLeft = frame.x(1060f)
            detailRight = frame.right
            detailTop = d(112f)
            detailBottom = frame.height - d(16f)
        }
        ProfileLayoutMode.COMPACT_LANDSCAPE -> {
            val detailWidth = (frame.width * 0.34f).coerceIn(d(230f), d(300f))
            detailRight = frame.right
            detailLeft = detailRight - detailWidth
            gridLeft = frame.left - d(6f)
            gridRight = detailLeft - d(24f) - pad
            gridTop = frame.headerHeight + d(4f)
            gridBottom = frame.height - d(4f)
            detailTop = frame.headerHeight + d(8f)
            detailBottom = frame.height - d(8f)
        }
        ProfileLayoutMode.COMPACT_PORTRAIT -> {
            val dock = d(250f) * (1f + (grow - 1f) * 0.6f)
            gridLeft = frame.left - d(4f)
            gridRight = min(frame.right + d(4f), frame.width - pad - d(4f))
            gridTop = frame.headerHeight
            gridBottom = frame.height - dock
            detailLeft = frame.left
            detailRight = frame.right
            detailTop = gridBottom + d(14f)
            detailBottom = frame.height - d(10f)
        }
    }

    val contentWidth = max(d(120f), gridRight - gridLeft)
    val perRow = spec.branchesPerRow
    val columnWidth = (contentWidth - (perRow - 1) * d(spec.columnGap)) / perRow
    val headerHeight = d(spec.headerHeight) * (1f + (grow - 1f) * 0.6f)
    val tileHeight = d(spec.tileHeight) * grow
    val rowGap = d(spec.rowGap)
    val blockGap = d(spec.blockGap)
    val rows = (model.nodes.maxOfOrNull { it.row } ?: 0) + 1
    val sectionHeight = headerHeight + d(8f) + rows * tileHeight + (rows - 1) * rowGap
    val branchHeaders = MatrixBranch.entries.map { branch ->
        val column = branch.ordinal % perRow
        val block = branch.ordinal / perRow
        val left = pad + column * (columnWidth + d(spec.columnGap))
        val top = pad + block * (sectionHeight + blockGap)
        Rect(left, top, left + columnWidth, top + headerHeight)
    }
    val tiles = model.nodes.map { node ->
        val header = branchHeaders[node.branch.ordinal]
        val lanes = (model.nodes.filter { it.branch == node.branch }.maxOfOrNull { it.lane } ?: 0) + 1
        val tileWidth = (columnWidth - (lanes - 1) * d(spec.laneGap)) / lanes
        val left = header.left + node.lane * (tileWidth + d(spec.laneGap))
        val top = header.bottom + d(8f) + node.row * (tileHeight + rowGap)
        Rect(left, top, left + tileWidth, top + tileHeight)
    }
    val blocks = ceil(MatrixBranch.entries.size / perRow.toFloat()).toInt()
    val gridContentHeight = pad * 2f + blocks * sectionHeight + (blocks - 1) * blockGap
    val gridViewport = Rect(gridLeft - pad, gridTop - pad, gridLeft + contentWidth + pad + matrixScrollBarRoom(frame),
        max(gridTop, gridBottom))

    val regular = frame.regular
    val monoHeight = d(type.mono) * 1.35f * t
    val tag = Rect(detailLeft, detailTop, detailRight, detailTop + monoHeight)
    val nameTop = tag.bottom + d(if (regular) 10f else 4f)
    val name = Rect(detailLeft, nameTop, detailRight, nameTop + d(type.name) * 1.1f * t)
    val descriptionTop = name.bottom + d(if (regular) 12f else 6f)
    val descriptionBottom = descriptionTop + (descriptionHeight?.invoke(detailRight - detailLeft)
        ?: (d(type.body) * 1.4f * 3f * t))
    val description = Rect(detailLeft, descriptionTop, detailRight, descriptionBottom)
    val rankTop = description.bottom + d(if (regular) 20f else 8f)
    val rank = Rect(detailLeft, rankTop, detailRight, rankTop + max(d(if (regular) 22f else 14f), monoHeight))
    val statusTop = rank.bottom + d(if (regular) 12f else 6f)
    val status = Rect(detailLeft, statusTop, detailRight, statusTop + d(type.body) * 1.3f * t * 2f)
    val buyHeight = d(if (regular) 72f else 52f)
    val limit = max(detailTop + buyHeight, detailBottom)
    val naturalTop = status.bottom + d(if (regular) 24f else 10f)
    val buyPinned = naturalTop + buyHeight > limit
    val buyTop = if (buyPinned) limit - buyHeight else naturalTop
    val buy = Rect(detailLeft, buyTop, detailRight, buyTop + buyHeight)
    val detailViewport = Rect(detailLeft - pad, detailTop - pad, detailRight + pad, if (buyPinned) buy.top - d(8f) else limit)
    val detailContentHeight = status.bottom + pad - detailViewport.top
    return MatrixLayout(frame, back, gridViewport, gridContentHeight, branchHeaders, tiles, rowGap, detailViewport, detailContentHeight,
        tag, name, description, rank, status, buy, buyPinned)
}

/** The grid viewport's padding around branch columns (dp at the board scale): room for the lift echo and focus ring. */
internal const val MATRIX_GRID_PAD = 14f

/** Width (px) of the grid's scroll bar. */
internal fun matrixScrollBarWidth(frame: ProfileFrame): Float = frame.d(3f)

/** Room (px) right of the grid content for the scroll bar and a 2 dp gap. */
internal fun matrixScrollBarRoom(frame: ProfileFrame): Float = matrixScrollBarWidth(frame) + frame.density * 2f

/** Height of the grid's scroll cue fades. */
internal fun matrixGridFade(frame: ProfileFrame): Float = frame.d(20f)

/**
 * A prerequisite line from the bottom centre of a prerequisite tile to the top centre of the node's
 * tile, in grid content coordinates: down to [bendY] (the gap just above the node's row, so a line
 * that skips a row runs behind that row's tiles), across, then down. [owned] is true when the
 * prerequisite has a rank.
 */
internal class MatrixLink(val from: Offset, val to: Offset, val bendY: Float, val owned: Boolean)

internal fun matrixLinks(layout: MatrixLayout, model: MatrixRenderModel): List<MatrixLink> = buildList {
    model.nodes.forEachIndexed { index, node ->
        val tile = layout.tiles[index]
        node.prerequisites.forEach { id ->
            val sourceIndex = model.nodes.indexOfFirst { it.id == id }
            if (sourceIndex < 0) return@forEach
            val source = layout.tiles[sourceIndex]
            add(MatrixLink(Offset(source.center.x, source.bottom), Offset(tile.center.x, tile.top), tile.top - layout.rowGap * 0.5f,
                model.nodes[sourceIndex].owned))
        }
    }
}

/**
 * The grid scroll that brings [tile] (content px) fully into view, or null when it is visible: a
 * keyboard or restored selection never sits under the fold.
 */
internal fun matrixRevealScroll(value: Float, tile: Rect, viewportHeight: Float, maxValue: Float, fade: Float): Float? {
    val above = tile.top < value + (if (value > 0f) fade else 0f)
    val below = tile.bottom > value + viewportHeight - (if (value < maxValue) fade else 0f)
    val target = when {
        above -> tile.top - fade
        below -> tile.bottom - viewportHeight + fade
        else -> return null
    }
    return target.coerceIn(0f, max(0f, maxValue))
}

/**
 * Maps a press to the Matrix action it hits: header Back, a tile (activation: selects, or buys the
 * selected node's next rank) or the detail panel's Buy rank button for the selected node.
 */
internal fun resolveMatrixPress(
    layout: MatrixLayout,
    state: MatrixState,
    gridScroll: Float,
    x: Float,
    y: Float,
): MatrixAction? {
    if (layout.back.contains(Offset(x, y))) return MatrixAction.Back
    val grid = layout.gridViewport
    if (x in grid.left..grid.right && y in grid.top..grid.bottom) {
        val contentX = x - grid.left
        val contentY = y - grid.top + gridScroll.coerceIn(0f, layout.gridScrollMax)
        layout.tiles.forEachIndexed { index, tile ->
            if (contentX in tile.left..tile.right && contentY in tile.top..tile.bottom) {
                return state.model.nodes.getOrNull(index)?.id?.let(MatrixAction::Activate)
            }
        }
        return null
    }
    val node = state.selectedNode ?: return null
    if (x in layout.buy.left..layout.buy.right && y in layout.buy.top..layout.buy.bottom) {
        return MatrixAction.PurchaseRequested(node.id)
    }
    return null
}
