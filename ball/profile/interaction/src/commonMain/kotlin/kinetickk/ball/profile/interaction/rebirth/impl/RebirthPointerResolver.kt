// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.ProfileLayoutMode
import kinetickk.ball.profile.interaction.profileHeaderBackRect
import kinetickk.ball.profile.interaction.profileLayoutGrow
import kotlin.math.max

/** Board type sizes per mode (design px before the frame scale and the text-size setting). */
internal class RebirthType(
    val fromNumeral: Float,
    val fromName: Float,
    val numeral: Float,
    val numeralMax: Float,
    val name: Float,
    val tag: Float,
    val ladderNumber: Float,
    val tableHead: Float,
    val tableLabel: Float,
    val body: Float,
    val mono: Float,
    val action: Float,
)

internal fun rebirthType(mode: ProfileLayoutMode): RebirthType = when (mode) {
    ProfileLayoutMode.REGULAR -> RebirthType(58f, 30f, 300f, 230f, 64f, 13f, 20f, 20f, 15f, 15f, 11f, 34f)
    ProfileLayoutMode.COMPACT_LANDSCAPE -> RebirthType(30f, 16f, 130f, 100f, 30f, 10f, 12f, 13f, 11f, 12f, 9.5f, 22f)
    ProfileLayoutMode.COMPACT_PORTRAIT -> RebirthType(32f, 17f, 170f, 130f, 34f, 10f, 12f, 14f, 12f, 14f, 10f, 22f)
}

/**
 * One geometry for the Rebirth screen: drawing, Compose placement and [resolveRebirthPress].
 * Everything below the header scrolls in [viewport]; rects are in screen coordinates for a
 * scroll of 0. [hostileRows] and [compensationRows] are the comparison rows.
 */
internal class RebirthLayout(
    val frame: ProfileFrame,
    val back: Rect,
    val viewport: Rect,
    val contentHeight: Float,
    val from: Rect,
    val numeral: Rect,
    val name: Rect,
    val ladder: Rect,
    val cells: List<Rect>,
    val tableHeader: Rect,
    val hostileRows: List<Rect>,
    val compensationLabel: Rect,
    val compensationRows: List<Rect>,
    val action: Rect,
    val info: Rect,
    /** True when the content overflows and [action] is pinned to the bottom of the screen. */
    val actionPinned: Boolean = false,
) {
    val scrollMax: Float get() = max(0f, contentHeight - viewport.height)
}

internal const val REBIRTH_HOSTILE_ROWS = 7
internal const val REBIRTH_COMPENSATION_ROWS = 4

internal fun rebirthLayout(
    frame: ProfileFrame,
    minimumTier: Int,
    maximumTier: Int,
    targetTier: Int,
    textScale: Float,
    backWidth: Float,
): RebirthLayout {
    fun d(value: Float) = frame.d(value)
    val t = textScale.coerceIn(0.75f, 2f)
    val grow = profileLayoutGrow(t, 0.6f)
    val back = profileHeaderBackRect(frame, backWidth)
    val twoDigits = targetTier >= 10
    val sectionLeft: Float
    val sectionRight: Float
    val asideLeft: Float
    val asideRight: Float
    val fromTop: Float
    val fromHeight: Float
    val numeralTop: Float
    val numeralHeight: Float
    val nameOffset: Float
    val nameTop: Float
    val ladderGap: Float
    val ladderHeight: Float
    val tableTop: Float
    val headHeight: Float
    val rowHeight: Float
    val labelGap: Float
    val labelHeight: Float
    val actionGap: Float
    val actionHeight: Float
    when (frame.mode) {
        ProfileLayoutMode.REGULAR -> {
            sectionLeft = frame.x(56f); sectionRight = frame.x(876f)
            asideLeft = frame.x(930f); asideRight = frame.right
            fromTop = d(108f); fromHeight = d(52f)
            numeralTop = d(164f); numeralHeight = d(270f)
            nameOffset = d(if (twoDigits) 400f else 250f); nameTop = d(236f)
            ladderGap = d(30f); ladderHeight = d(70f)
            tableTop = d(112f); headHeight = d(26f); rowHeight = d(31f) * grow
            labelGap = d(18f); labelHeight = d(23f); actionGap = d(26f); actionHeight = d(72f)
        }
        ProfileLayoutMode.COMPACT_LANDSCAPE -> {
            sectionLeft = frame.left; sectionRight = frame.width * 0.47f
            asideLeft = frame.width * 0.52f; asideRight = frame.right
            fromTop = frame.headerHeight + d(8f); fromHeight = d(28f)
            numeralTop = fromTop + fromHeight + d(4f); numeralHeight = d(120f)
            nameOffset = d(if (twoDigits) 190f else 115f); nameTop = numeralTop + d(30f)
            ladderGap = d(14f); ladderHeight = d(36f)
            tableTop = frame.headerHeight + d(8f); headHeight = d(20f); rowHeight = d(22f) * grow
            labelGap = d(10f); labelHeight = d(18f); actionGap = d(12f); actionHeight = d(52f)
        }
        ProfileLayoutMode.COMPACT_PORTRAIT -> {
            sectionLeft = frame.left; sectionRight = frame.right
            asideLeft = frame.left; asideRight = frame.right
            fromTop = frame.headerHeight + d(10f); fromHeight = d(30f)
            numeralTop = fromTop + fromHeight + d(6f); numeralHeight = d(150f)
            nameOffset = d(if (twoDigits) 200f else 130f); nameTop = numeralTop + d(38f)
            ladderGap = d(16f); ladderHeight = d(36f)
            tableTop = 0f; headHeight = d(22f); rowHeight = d(26f) * grow
            labelGap = d(10f); labelHeight = d(20f); actionGap = d(16f); actionHeight = d(52f)
        }
    }
    val from = Rect(sectionLeft, fromTop, sectionRight, fromTop + fromHeight)
    val numeral = Rect(sectionLeft, numeralTop, sectionRight, numeralTop + numeralHeight)
    val name = Rect(sectionLeft + nameOffset, nameTop, sectionRight, nameTop + numeralHeight * 0.5f)
    val ladderTop = numeral.bottom + ladderGap
    val ladder = Rect(sectionLeft, ladderTop, sectionRight, ladderTop + ladderHeight)
    val tiers = (maximumTier - minimumTier + 1).coerceAtLeast(1)
    val cellGap = d(6f)
    val cellWidth = (ladder.width - cellGap * (tiers - 1)) / tiers
    val cells = List(tiers) { index ->
        val left = ladder.left + index * (cellWidth + cellGap)
        Rect(left, ladder.top, left + cellWidth, ladder.bottom)
    }
    val asideTop = if (frame.portrait) ladder.bottom + d(24f) else tableTop
    val tableHeader = Rect(asideLeft, asideTop, asideRight, asideTop + headHeight * grow)
    val hostileRows = List(REBIRTH_HOSTILE_ROWS) { index ->
        val top = tableHeader.bottom + index * rowHeight
        Rect(asideLeft, top, asideRight, top + rowHeight)
    }
    val labelTop = hostileRows.last().bottom + labelGap
    val compensationLabel = Rect(asideLeft, labelTop, asideRight, labelTop + labelHeight * grow)
    val compensationRows = List(REBIRTH_COMPENSATION_ROWS) { index ->
        val top = compensationLabel.bottom + index * rowHeight
        Rect(asideLeft, top, asideRight, top + rowHeight)
    }
    // Landscape phones keep Advance on screen: it sits under the ladder instead of the table.
    val landscape = frame.mode == ProfileLayoutMode.COMPACT_LANDSCAPE
    val naturalTop = if (landscape) ladder.bottom + d(16f) else compensationRows.last().bottom + actionGap
    // Advance never sits below the fold: when the content overflows (phones, large text) it is
    // pinned to the bottom of the screen and the content scrolls above it.
    val pinnedTop = frame.height - d(12f) - actionHeight
    val actionPinned = naturalTop > pinnedTop
    val actionTop = if (actionPinned) pinnedTop else naturalTop
    val actionLeft = if (landscape) sectionLeft else asideLeft
    val actionRight = if (landscape) sectionRight else asideRight
    val infoSize = frame.density * 24f
    val infoGap = d(14f)
    val info = Rect(actionRight - infoSize, actionTop + (actionHeight - infoSize) * 0.5f, actionRight,
        actionTop + (actionHeight + infoSize) * 0.5f)
    val action = Rect(actionLeft, actionTop, info.left - infoGap, actionTop + actionHeight)
    val viewport = Rect(0f, frame.headerHeight, frame.width, if (actionPinned) action.top - d(12f) else frame.height)
    val bottom = maxOf(if (actionPinned) 0f else action.bottom, ladder.bottom, compensationRows.last().bottom) + d(16f)
    return RebirthLayout(frame, back, viewport, bottom - viewport.top, from, numeral, name, ladder, cells, tableHeader,
        hostileRows, compensationLabel, compensationRows, action, info, actionPinned)
}

/** Maps a press to the Rebirth action it hits: header Back or the Advance button. */
internal fun resolveRebirthPress(
    layout: RebirthLayout,
    scroll: Float,
    x: Float,
    y: Float,
): RebirthAction? {
    if (layout.back.contains(Offset(x, y))) return RebirthAction.Back
    if (y < layout.viewport.top) return null
    // A pinned Advance does not scroll; otherwise it moves with the content.
    val contentY = if (layout.actionPinned) y else y + scroll.coerceIn(0f, layout.scrollMax)
    return if (layout.action.contains(Offset(x, contentY))) RebirthAction.AdvanceRequested else null
}
