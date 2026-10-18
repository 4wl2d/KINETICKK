// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.rebirth.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.PROFILE_WRAPPED_LINE_HEIGHT
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
 * Everything below the header scrolls in [viewport] (only the aside column when [heroFixed]);
 * rects are in screen coordinates for a scroll of 0. [hostileRows] and [compensationRows] are the
 * comparison rows. [buildHeader] and [buildRows] (Reborn cards, then one featured row per unlock
 * of the target tier) and [heirloomLabel] and [heirloomRows] are the build sections; they are
 * absent (null, empty) when the screen has no build content.
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
    val buildHeader: Rect? = null,
    val buildRows: List<Rect> = emptyList(),
    val heirloomLabel: Rect? = null,
    val heirloomRows: List<Rect> = emptyList(),
    /**
     * True on landscape phones: the hero column ([from], [numeral], [name], [ladder] and [action])
     * stays in place and only the aside column (comparison tables, build sections) scrolls in
     * [viewport], so Advance never leaves the screen however long the tables grow.
     */
    val heroFixed: Boolean = false,
) {
    val scrollMax: Float get() = max(0f, contentHeight - viewport.height)

    /** True when [action] moves with the scroll (neither pinned nor in the fixed hero column). */
    val actionScrolls: Boolean get() = !actionPinned && !heroFixed

    /** Where [action] is on screen at [scroll]. */
    fun actionAt(scroll: Float): Rect =
        if (actionScrolls) action.translate(0f, -scroll.coerceIn(0f, scrollMax)) else action
}

internal const val REBIRTH_HOSTILE_ROWS = 8
internal const val REBIRTH_COMPENSATION_ROWS = 3

/** Description lines a featured unlock row holds at most (longer text shrinks to fit them). */
internal const val REBIRTH_UNLOCK_MAX_LINES = 3

/** Featured unlock rows: name and description sizes as shares of the mode's body size, description line height. */
internal const val REBIRTH_UNLOCK_NAME_SIZE = 1.1f
internal const val REBIRTH_UNLOCK_BLURB_SIZE = 0.76f
internal const val REBIRTH_UNLOCK_BLURB_LINE = 1.25f

/** Rune or item mark of a featured unlock row (board px): larger than the list rows' 26. */
internal fun rebirthUnlockIconSize(frame: ProfileFrame): Float = frame.d(if (frame.regular) 40f else 34f)

/** Vertical padding of a featured unlock row above its name and below its description. */
internal fun rebirthUnlockPad(frame: ProfileFrame): Float = frame.d(6f)

/** Gap between a featured row's name and its description. */
internal fun rebirthUnlockGap(frame: ProfileFrame): Float = frame.d(2f)

/**
 * Height of a featured unlock row holding [lines] of description under its name at the
 * text-size multiplier [textScale]; never shorter than its mark. The text grows fully with the
 * setting (unlike the table rows), so the row does too, but never below the board size.
 */
internal fun rebirthUnlockRowHeight(frame: ProfileFrame, textScale: Float, lines: Int): Float {
    val type = rebirthType(frame.mode)
    val t = textScale.coerceIn(1f, 2f)
    val name = frame.d(type.body * REBIRTH_UNLOCK_NAME_SIZE) * t * PROFILE_WRAPPED_LINE_HEIGHT
    val blurb = frame.d(type.body * REBIRTH_UNLOCK_BLURB_SIZE) * t * REBIRTH_UNLOCK_BLURB_LINE
    val text = rebirthUnlockPad(frame) * 2f + name + rebirthUnlockGap(frame) + blurb * lines.coerceIn(1, REBIRTH_UNLOCK_MAX_LINES)
    return maxOf(text, rebirthUnlockIconSize(frame) + frame.d(12f))
}

internal fun rebirthLayout(
    frame: ProfileFrame,
    minimumTier: Int,
    maximumTier: Int,
    targetTier: Int,
    textScale: Float,
    backWidth: Float,
    buildRows: Int = 0,
    heirloomRows: Int = 0,
    unlockLines: List<Int> = emptyList(),
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
    // Build sections. The regular board sets them in two columns under the ladder (the left column
    // is free there); phones stack them under the compensation rows.
    val build = buildRows > 0
    val heirlooms = build && heirloomRows > 0
    val buildTop: Float
    val buildLeft: Float
    val buildRight: Float
    val heirloomLeft: Float
    val heirloomRight: Float
    if (frame.regular) {
        // The build table takes the larger share: its labels sit beside two value columns.
        val columnGap = d(36f)
        val share = (sectionRight - sectionLeft - columnGap) * 0.56f
        buildTop = ladder.bottom + d(28f)
        buildLeft = sectionLeft; buildRight = sectionLeft + share
        heirloomLeft = buildRight + columnGap; heirloomRight = sectionRight
    } else {
        buildTop = compensationRows.last().bottom + labelGap
        buildLeft = asideLeft; buildRight = asideRight
        heirloomLeft = asideLeft; heirloomRight = asideRight
    }
    val buildHeader = if (build) Rect(buildLeft, buildTop, buildRight, buildTop + headHeight * grow) else null
    // The Reborn row keeps the table's height; each unlock below it is a featured row sized for
    // its name and description lines (one line when [unlockLines] has no entry for it).
    val buildRowRects = if (buildHeader == null) emptyList() else {
        var top = buildHeader.bottom
        List(buildRows) { index ->
            val height = if (index == 0) rowHeight else rebirthUnlockRowHeight(frame, t, unlockLines.getOrElse(index - 1) { 1 })
            Rect(buildLeft, top, buildRight, top + height).also { top += height }
        }
    }
    val heirloomLabel = when {
        !heirlooms -> null
        // Beside the build table: the label matches its header so both titles share a baseline.
        frame.regular -> Rect(heirloomLeft, buildTop, heirloomRight, buildTop + headHeight * grow)
        else -> {
            val top = buildRowRects.last().bottom + labelGap
            Rect(heirloomLeft, top, heirloomRight, top + labelHeight * grow)
        }
    }
    val heirloomRowRects = if (heirloomLabel == null) emptyList() else List(heirloomRows) { index ->
        val top = heirloomLabel.bottom + index * rowHeight
        Rect(heirloomLeft, top, heirloomRight, top + rowHeight)
    }
    val tablesBottom = maxOf(compensationRows.last().bottom, buildRowRects.lastOrNull()?.bottom ?: 0f,
        heirloomRowRects.lastOrNull()?.bottom ?: 0f)
    // Landscape phones keep Advance on screen: it sits under the ladder instead of the table.
    val landscape = frame.mode == ProfileLayoutMode.COMPACT_LANDSCAPE
    val naturalTop = when {
        landscape -> ladder.bottom + d(16f)
        frame.portrait -> tablesBottom + actionGap
        else -> compensationRows.last().bottom + actionGap
    }
    // Advance never sits below the fold: when the content overflows (phones, large text) it is
    // pinned to the bottom of the screen and the content scrolls above it. Landscape phones keep
    // the whole hero column (numerals, ladder, Advance) in place and scroll only the tables beside
    // it, when that column fits the screen.
    val pinnedTop = frame.height - d(12f) - actionHeight
    val heroFixed = landscape && naturalTop <= pinnedTop
    val actionPinned = !heroFixed && naturalTop > pinnedTop
    val actionTop = if (actionPinned) pinnedTop else naturalTop
    val actionLeft = if (landscape) sectionLeft else asideLeft
    val actionRight = if (landscape) sectionRight else asideRight
    val infoSize = frame.density * 24f
    val infoGap = d(14f)
    val info = Rect(actionRight - infoSize, actionTop + (actionHeight - infoSize) * 0.5f, actionRight,
        actionTop + (actionHeight + infoSize) * 0.5f)
    val action = Rect(actionLeft, actionTop, info.left - infoGap, actionTop + actionHeight)
    val viewport = when {
        // The aside column's scroll area starts midway in the gutter between the two columns.
        heroFixed -> Rect((sectionRight + asideLeft) * 0.5f, frame.headerHeight, frame.width, frame.height)
        actionPinned -> Rect(0f, frame.headerHeight, frame.width, action.top - d(12f))
        else -> Rect(0f, frame.headerHeight, frame.width, frame.height)
    }
    val bottom = when {
        heroFixed -> tablesBottom
        else -> maxOf(if (actionPinned) 0f else action.bottom, ladder.bottom, tablesBottom)
    } + d(16f)
    return RebirthLayout(frame, back, viewport, bottom - viewport.top, from, numeral, name, ladder, cells, tableHeader,
        hostileRows, compensationLabel, compensationRows, action, info, actionPinned, buildHeader, buildRowRects,
        heirloomLabel, heirloomRowRects, heroFixed)
}

/**
 * Maps a press to the Rebirth action it hits: header Back, the select button or a ladder cell (which
 * focuses its tier; [minimumTier] is the first cell's tier).
 */
internal fun resolveRebirthPress(
    layout: RebirthLayout,
    scroll: Float,
    x: Float,
    y: Float,
    minimumTier: Int = 0,
): RebirthAction? {
    if (layout.back.contains(Offset(x, y))) return RebirthAction.Back
    if (y < layout.viewport.top) return null
    // A pinned or hero-column select button does not scroll; otherwise it moves with the content.
    if (layout.actionAt(scroll).contains(Offset(x, y))) return RebirthAction.SelectRequested
    // The ladder scrolls with the content unless the hero column is fixed.
    val shift = if (layout.heroFixed) 0f else scroll.coerceIn(0f, layout.scrollMax)
    layout.cells.forEachIndexed { index, cell ->
        if (cell.translate(0f, -shift).contains(Offset(x, y))) return RebirthAction.FocusTier(minimumTier + index)
    }
    return null
}
