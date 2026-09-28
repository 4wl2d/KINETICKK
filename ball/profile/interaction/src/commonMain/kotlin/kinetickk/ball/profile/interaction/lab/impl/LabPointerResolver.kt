// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.lab.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.ProfileLayoutMode
import kinetickk.ball.profile.interaction.lab.api.LabRenderModel
import kinetickk.ball.profile.interaction.profileHeaderBackRect
import kinetickk.ball.profile.interaction.profileLayoutGrow
import kotlin.math.max
import kotlin.math.min

/**
 * Column slots inside one Lab row, in px relative to the row's left/top. Portrait rows use two
 * lines: name and cost on top, rank pips and the current value underneath.
 */
internal class LabRowColumns(
    val iconCenterX: Float,
    val iconSize: Float,
    val nameLeft: Float,
    val nameWidth: Float,
    val pipsLeft: Float,
    val pipsWidth: Float,
    val pipWidth: Float,
    val pipHeight: Float,
    val pipGap: Float,
    val valueLeft: Float,
    val valueWidth: Float,
    val costLeft: Float,
    val costRight: Float,
    val firstLineY: Float,
    val secondLineY: Float,
    val twoLines: Boolean,
)

/**
 * One geometry for the Lab: drawing, Compose placement and [resolveLabPress]. Rows are in list
 * content coordinates (add [listViewport] top-left, subtract the list scroll); detail rects are in
 * screen coordinates for a detail scroll of 0.
 */
internal class LabLayout(
    val frame: ProfileFrame,
    val back: Rect,
    val listViewport: Rect,
    val rows: List<Rect>,
    val listContentHeight: Float,
    val columns: LabRowColumns,
    val detailViewport: Rect,
    val detailContentHeight: Float,
    val icon: Rect,
    val name: Rect,
    val description: Rect,
    val now: Rect,
    val next: Rect,
    val rank: Rect,
    val buy: Rect,
    /** True when the details overflow and [buy] is pinned under them. */
    val buyPinned: Boolean = false,
) {
    val listScrollMax: Float get() = max(0f, listContentHeight - listViewport.height)
    val detailScrollMax: Float get() = max(0f, detailContentHeight - detailViewport.height)
}

/** Board type sizes per mode (design px before the frame scale and the text-size setting). */
internal class LabType(
    val rowName: Float,
    val rowValue: Float,
    val stamp: Float,
    val name: Float,
    val body: Float,
    val mono: Float,
    val panelValue: Float,
    val buy: Float,
)

internal fun labType(mode: ProfileLayoutMode): LabType = when (mode) {
    ProfileLayoutMode.REGULAR -> LabType(29f, 24f, 15f, 30f, 18f, 11f, 26f, 40f)
    ProfileLayoutMode.COMPACT_LANDSCAPE -> LabType(18f, 16f, 11f, 18f, 13f, 9.5f, 17f, 22f)
    ProfileLayoutMode.COMPACT_PORTRAIT -> LabType(20f, 17f, 11f, 18f, 14f, 10f, 17f, 22f)
}

/**
 * [textScale] is the board-relative text multiplier ([kinetickk.ball.profile.interaction.profileTextScale]).
 * [descriptionHeight] measures the selected upgrade's description at a width (the renderer's
 * layout); without it the slot keeps the board's line count.
 */
internal fun labLayout(
    frame: ProfileFrame,
    rowCount: Int,
    maxRanks: Int,
    textScale: Float,
    backWidth: Float,
    descriptionHeight: ((width: Float) -> Float)? = null,
): LabLayout {
    fun d(value: Float) = frame.d(value)
    val t = textScale.coerceIn(0.75f, 2f)
    val grow = profileLayoutGrow(t, 0.55f)
    val back = profileHeaderBackRect(frame, backWidth)
    val pad = d(12f)
    val type = labType(frame.mode)
    val listLeft: Float
    val listRight: Float
    val listTop: Float
    val listBottom: Float
    val rowHeight: Float
    val rowGap: Float
    val detailLeft: Float
    val detailRight: Float
    val detailTop: Float
    val detailBottom: Float
    when (frame.mode) {
        ProfileLayoutMode.REGULAR -> {
            listLeft = frame.x(44f)
            listRight = frame.x(994f)
            listTop = d(106f)
            listBottom = frame.height - d(12f)
            rowHeight = d(72f) * grow
            rowGap = d(8f)
            detailLeft = frame.x(1060f)
            detailRight = frame.right
            detailTop = d(112f)
            detailBottom = frame.height - d(16f)
        }
        ProfileLayoutMode.COMPACT_LANDSCAPE -> {
            val detailWidth = (frame.width * 0.36f).coerceIn(d(240f), d(320f))
            detailRight = frame.right
            detailLeft = detailRight - detailWidth
            listLeft = frame.left - d(6f)
            listRight = detailLeft - d(24f)
            listTop = frame.headerHeight + d(4f)
            listBottom = frame.height - d(4f)
            rowHeight = d(40f) * grow
            rowGap = d(4f)
            detailTop = frame.headerHeight + d(8f)
            detailBottom = frame.height - d(8f)
        }
        ProfileLayoutMode.COMPACT_PORTRAIT -> {
            val dock = d(262f) * (1f + (grow - 1f) * 0.6f)
            listLeft = frame.left - d(4f)
            listRight = frame.right + d(4f)
            listTop = frame.headerHeight
            listBottom = frame.height - dock
            rowHeight = d(64f) * grow
            rowGap = d(6f)
            detailLeft = frame.left
            detailRight = frame.right
            detailTop = listBottom + d(14f)
            detailBottom = frame.height - d(10f)
        }
    }
    val rowWidth = listRight - listLeft
    val rows = List(rowCount) { index ->
        val top = pad + index * (rowHeight + rowGap)
        Rect(pad, top, pad + rowWidth, top + rowHeight)
    }
    val listViewport = Rect(listLeft - pad, listTop - pad, listRight + pad, max(listTop, listBottom))
    val listContentHeight = if (rowCount == 0) 0f else pad * 2f + rowCount * rowHeight + (rowCount - 1) * rowGap
    val columns = labRowColumns(frame, rowWidth, rowHeight, max(1, maxRanks), type, t)

    val regular = frame.regular
    val iconSize = d(when (frame.mode) {
        ProfileLayoutMode.REGULAR -> 64f
        ProfileLayoutMode.COMPACT_LANDSCAPE -> 32f
        ProfileLayoutMode.COMPACT_PORTRAIT -> 28f
    })
    val icon = Rect(detailLeft, detailTop, detailLeft + iconSize, detailTop + iconSize)
    val nameHeight = d(type.name) * 1.1f * t
    val name = if (frame.portrait) {
        Rect(icon.right + d(12f), icon.center.y - nameHeight * 0.5f, detailRight, icon.center.y + nameHeight * 0.5f)
    } else {
        val top = icon.bottom + d(if (regular) 16f else 8f)
        Rect(detailLeft, top, detailRight, top + nameHeight)
    }
    val descriptionTop = (if (frame.portrait) icon.bottom else name.bottom) + d(if (regular) 12f else 6f)
    // The description slot is as tall as the wrapped description, so the panels follow it.
    val descriptionBottom = descriptionTop + (descriptionHeight?.invoke(detailRight - detailLeft)
        ?: (d(type.body) * 1.4f * kotlin.math.ceil((if (regular) 3f else 2f) * max(1f, t)) * t))
    val description = Rect(detailLeft, descriptionTop, detailRight, descriptionBottom)
    val panelsTop = description.bottom + d(if (regular) 22f else 10f)
    val panelHeight = (d(if (regular) 12f else 8f) * 2f + d(type.mono) * 1.35f * t + d(if (regular) 8f else 5f) +
        d(type.panelValue) * 0.9f * t)
    val panelGap = d(10f)
    val panelWidth = (detailRight - detailLeft - panelGap) / 2f
    val now = Rect(detailLeft, panelsTop, detailLeft + panelWidth, panelsTop + panelHeight)
    val next = Rect(now.right + panelGap, panelsTop, detailRight, panelsTop + panelHeight)
    val rankTop = now.bottom + d(if (regular) 12f else 6f)
    val rank = Rect(detailLeft, rankTop, detailRight, rankTop + d(type.mono) * 1.35f * t)
    val buyHeight = d(if (regular) 72f else 52f)
    val limit = max(detailTop + buyHeight, detailBottom)
    val naturalTop = rank.bottom + d(if (regular) 30f else 12f)
    // Details that run past the fold scroll above a pinned Buy rank button.
    val buyPinned = naturalTop + buyHeight > limit
    val buyTop = if (buyPinned) limit - buyHeight else naturalTop
    val buy = Rect(detailLeft, buyTop, detailRight, buyTop + buyHeight)
    val detailViewport = Rect(detailLeft - pad, detailTop - pad, detailRight + pad, if (buyPinned) buy.top - d(8f) else limit)
    val detailContentHeight = rank.bottom + pad - detailViewport.top
    return LabLayout(frame, back, listViewport, rows, listContentHeight, columns, detailViewport, detailContentHeight,
        icon, name, description, now, next, rank, buy, buyPinned)
}

/** Height of the list's scroll cue fades (the list draws them over rows at a scrolled edge). */
internal fun labListFade(frame: ProfileFrame): Float = frame.d(24f)

/**
 * The list scroll that brings the row [rowTop]..[rowBottom] (content px) fully into view, clear
 * of the [fade] bands the scroll cue draws at an edge with more content, or null when no scroll
 * is needed. A row near an end scrolls the list all the way (no fade there). With [whenHidden]
 * only a row that is entirely off screen moves the list, so hover selection (which needs a
 * visible row) never scrolls under the pointer; otherwise (the screen opening on a selection) a
 * partly clipped row is revealed too.
 */
internal fun labRevealScroll(
    value: Float,
    rowTop: Float,
    rowBottom: Float,
    viewportHeight: Float,
    maxValue: Float,
    whenHidden: Boolean = true,
    fade: Float = 0f,
): Float? {
    // A partly clipped row counts the fade over it as clipped (no fade at an end of the list).
    val topFade = if (value > 0f) fade else 0f
    val bottomFade = if (value < maxValue) fade else 0f
    val above = if (whenHidden) rowBottom <= value else rowTop < value + topFade
    val below = if (whenHidden) rowTop >= value + viewportHeight else rowBottom > value + viewportHeight - bottomFade
    // Within a fade of an end the list goes to that end, where the cue draws no fade.
    val target = when {
        above -> (rowTop - fade).let { if (it <= fade) 0f else it }
        below -> (rowBottom - viewportHeight + fade).let { if (it >= maxValue - fade) maxValue else it }
        else -> return null
    }
    return target.coerceIn(0f, max(0f, maxValue))
}

private fun labRowColumns(
    frame: ProfileFrame,
    rowWidth: Float,
    rowHeight: Float,
    maxRanks: Int,
    type: LabType,
    textScale: Float,
): LabRowColumns {
    fun d(value: Float) = frame.d(value)
    return when (frame.mode) {
        ProfileLayoutMode.REGULAR -> {
            // Board slots: padding 24, icon 32, gap 16, name 236, pips 330, value 128, cost 70.
            val pipsLeft = d(24f + 32f + 16f + 236f + 16f)
            val pipsWidth = d(330f)
            val pipGap = d(4f)
            val pipWidth = min(d(22f), (pipsWidth - pipGap * (maxRanks - 1)) / maxRanks)
            LabRowColumns(
                iconCenterX = d(40f), iconSize = d(32f), nameLeft = d(72f), nameWidth = d(236f),
                pipsLeft = pipsLeft, pipsWidth = pipsWidth, pipWidth = pipWidth, pipHeight = d(24f), pipGap = pipGap,
                valueLeft = pipsLeft + pipsWidth + d(16f), valueWidth = d(128f),
                costLeft = pipsLeft + pipsWidth + d(16f) + d(128f) + d(12f), costRight = rowWidth - d(26f) - d(40f),
                firstLineY = rowHeight * 0.5f, secondLineY = rowHeight * 0.5f, twoLines = false,
            )
        }
        ProfileLayoutMode.COMPACT_LANDSCAPE -> {
            val nameLeft = d(38f)
            val nameWidth = rowWidth * 0.3f
            val costWidth = d(58f) * textScale.coerceAtLeast(1f)
            val valueWidth = d(54f) * textScale.coerceAtLeast(1f)
            val pipsLeft = nameLeft + nameWidth + d(8f)
            val costRight = rowWidth - d(12f)
            val pipsWidth = (costRight - costWidth - valueWidth - d(16f) - pipsLeft).coerceAtLeast(d(40f))
            val pipGap = d(3f)
            val pipWidth = min(d(14f), (pipsWidth - pipGap * (maxRanks - 1)) / maxRanks)
            LabRowColumns(
                iconCenterX = d(20f), iconSize = d(20f), nameLeft = nameLeft, nameWidth = nameWidth,
                pipsLeft = pipsLeft, pipsWidth = pipsWidth, pipWidth = pipWidth, pipHeight = d(15f), pipGap = pipGap,
                valueLeft = pipsLeft + pipsWidth + d(8f), valueWidth = valueWidth, costLeft = costRight - costWidth, costRight = costRight,
                firstLineY = rowHeight * 0.5f, secondLineY = rowHeight * 0.5f, twoLines = false,
            )
        }
        ProfileLayoutMode.COMPACT_PORTRAIT -> {
            val nameLeft = d(44f)
            val costRight = rowWidth - d(14f)
            val valueWidth = d(64f) * textScale.coerceAtLeast(1f)
            val pipsWidth = (costRight - valueWidth - d(10f) - nameLeft).coerceAtLeast(d(60f))
            val pipGap = d(3f)
            val pipWidth = min(d(18f), (pipsWidth - pipGap * (maxRanks - 1)) / maxRanks)
            LabRowColumns(
                iconCenterX = d(24f), iconSize = d(22f), nameLeft = nameLeft, nameWidth = costRight - nameLeft - d(70f),
                pipsLeft = nameLeft, pipsWidth = pipsWidth, pipWidth = pipWidth, pipHeight = d(14f), pipGap = pipGap,
                valueLeft = costRight - valueWidth, valueWidth = valueWidth, costLeft = costRight - d(62f), costRight = costRight,
                firstLineY = rowHeight * 0.34f, secondLineY = rowHeight * 0.72f, twoLines = true,
            )
        }
    }
}

/**
 * Maps a press to the Lab action it hits: header Back, a row (activation: selects, or buys the
 * selected row's next rank) or the detail panel's Buy rank button for the selected upgrade.
 */
internal fun resolveLabPress(
    layout: LabLayout,
    model: LabRenderModel,
    selected: LabState,
    listScroll: Float,
    x: Float,
    y: Float,
): LabAction? {
    if (layout.back.contains(Offset(x, y))) return LabAction.Back
    val list = layout.listViewport
    if (x in list.left..list.right && y in list.top..list.bottom) {
        val contentX = x - list.left
        val contentY = y - list.top + listScroll.coerceIn(0f, layout.listScrollMax)
        layout.rows.forEachIndexed { index, row ->
            if (contentX in row.left..row.right && contentY in row.top..row.bottom) {
                return model.upgrades.getOrNull(index)?.id?.let(LabAction::Activate)
            }
        }
        return null
    }
    // Buy rank never scrolls: it follows the details or is pinned under them.
    val upgrade = selected.selectedUpgrade ?: return null
    if (x in layout.buy.left..layout.buy.right && y in layout.buy.top..layout.buy.bottom) {
        return LabAction.PurchaseRequested(upgrade.id)
    }
    return null
}
