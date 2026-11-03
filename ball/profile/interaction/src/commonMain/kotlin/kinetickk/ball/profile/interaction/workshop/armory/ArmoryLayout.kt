// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.workshop.armory

import androidx.compose.ui.geometry.Rect
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.ProfileLayoutMode
import kinetickk.ball.profile.interaction.profileHeaderBackRect
import kinetickk.ball.profile.interaction.profileLayoutGrow
import kinetickk.ball.profile.interaction.workshop.impl.WorkshopSegment
import kinetickk.ball.profile.interaction.workshop.impl.workshopSegmentHeight
import kinetickk.ball.profile.interaction.workshop.impl.workshopUpgradeBlockHeight
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * One geometry for drawing, Compose placement and pointer mapping. Tiles are in grid content
 * coordinates (add [gridViewport] top-left, subtract the grid scroll); every detail rect is in
 * screen coordinates for a detail scroll of 0.
 */
internal class ArmoryLayout(
    val frame: ProfileFrame,
    val back: Rect,
    val gridViewport: Rect,
    val tiles: List<Rect>,
    val gridContentHeight: Float,
    val rowPitch: Float,
    val columns: Int,
    val detailViewport: Rect,
    val detailContentHeight: Float,
    val plate: Rect,
    val name: Rect,
    val description: Rect,
    val tags: Rect,
    val mastery: Rect,
    val ladder: Rect,
    val action: Rect,
    val need: Rect,
    /** True when the details overflow and [action] is pinned under the scrolling details. */
    val actionPinned: Boolean = false,
    /** Size (px) of the weapon icon every tile draws, and its centre (tile y): one size for the whole grid. */
    val tileIcon: Float = 0f,
    val tileIconY: Float = 0f,
    /** True when the mastery milestones stack one per line under the level cells. */
    val ladderStacked: Boolean = false,
    /** The three mastery forms by level, under the ladder. */
    val forms: Rect = Rect.Zero,
    /** The live synergy: signature character and twist, heirloom harmony, tag resonance. */
    val synergy: Rect = Rect.Zero,
    /** The weapon's typed facts: family, element, Relic key, scaling input and cadence. */
    val facts: Rect = Rect.Zero,
    /** The segmented switch under the tags (Upgrade, Mastery, Details); its cells start at the left. */
    val segments: Rect = Rect.Zero,
    /** The shown segment: the blocks of the other segments are 0 px tall at the segment body's top. */
    val segment: WorkshopSegment = WorkshopSegment.UPGRADE,
    /** The upgrade block's slot (the Upgrade segment). */
    val upgrade: Rect = Rect.Zero,
) {
    val gridScrollMax: Float get() = max(0f, gridContentHeight - gridViewport.height)
    val detailScrollMax: Float get() = max(0f, detailContentHeight - detailViewport.height)
}

/** Board type sizes per layout mode (design px before the frame scale and the text-size setting). */
internal class ArmoryType(
    val status: Float,
    val tileIcon: Float,
    val tileName: Float,
    val tileTags: Float,
    val name: Float,
    val body: Float,
    val tag: Float,
    val label: Float,
    val ladderMono: Float,
    val ladderName: Float,
    val action: Float,
)

internal fun armoryType(mode: ProfileLayoutMode): ArmoryType = when (mode) {
    ProfileLayoutMode.REGULAR -> ArmoryType(11f, 48f, 24f, 8.5f, 34f, 18f, 13f, 15f, 11f, 13f, 34f)
    else -> ArmoryType(9.5f, 30f, 17f, 7.5f, 21f, 14f, 11f, 12f, 9.5f, 11f, 22f)
}

/**
 * Measured heights (px) of the inspected weapon's name block, description and tag rows, and
 * whether the mastery milestones fit side by side with their bonus groups clearly apart.
 */
internal class ArmoryDetailMetrics(
    val nameHeight: Float,
    val descriptionHeight: Float,
    val tagsHeight: Float,
    val masterySideBySide: Boolean = true,
    /** Heights (px) of the mastery forms, the synergy block and the facts as drawn; null keeps an estimate. */
    val formsHeight: Float? = null,
    val synergyHeight: Float? = null,
    val factsHeight: Float? = null,
)

/** A weapon tile's content padding in px (the board's `padding: 14px 22px 14px 24px`; tighter on phones). */
internal class ArmoryTileInsets(val left: Float, val top: Float, val right: Float, val bottom: Float)

internal fun armoryTileInsets(frame: ProfileFrame): ArmoryTileInsets = if (frame.regular) {
    ArmoryTileInsets(frame.d(24f), frame.d(14f), frame.d(22f), frame.d(14f))
} else {
    ArmoryTileInsets(frame.d(14f), frame.d(10f), frame.d(12f), frame.d(10f))
}

/**
 * The tallest status line and the tallest name block (the name, plus the tag line under it on
 * the regular tile) of all tiles in the grid, in px.
 */
internal class ArmoryTileMetrics(val statusHeight: Float, val nameBlockHeight: Float)

/** Gap (dp at the board scale) between a tile's icon and the status line above / the name below. */
internal const val ARMORY_TILE_ICON_GAP = 4f

/** The smallest tile icon, as a share of the board's icon: tiles grow in height rather than draw less. */
internal const val ARMORY_TILE_ICON_MIN = 0.6f

/**
 * [textScale] is the board-relative text multiplier ([kinetickk.ball.profile.interaction.profileTextScale]).
 * [detail] measures the inspected weapon's texts for a name width and the panel width (the
 * renderer's layouts), so the panel stacks what is actually drawn; without it the slots keep
 * the board's line counts. [tileText] measures the grid's tile texts for the tiles' inner width,
 * so every tile draws its icon at one size between them; without it the icon keeps the board's size.
 */
internal fun armoryLayout(
    frame: ProfileFrame,
    weaponCount: Int,
    textScale: Float,
    backWidth: Float,
    actionWidth: Float,
    needRoom: Boolean = false,
    masteryCount: Int = 4,
    /** The detail segment shown under the tags; the others take no room. */
    segment: WorkshopSegment = WorkshopSegment.UPGRADE,
    tileText: ((innerWidth: Float) -> ArmoryTileMetrics)? = null,
    /** Measures the upgrade block at the panel width; without it the slot keeps the board's height. */
    upgradeHeight: ((width: Float) -> Float)? = null,
    detail: ((nameWidth: Float, width: Float) -> ArmoryDetailMetrics)? = null,
): ArmoryLayout {
    fun d(value: Float) = frame.d(value)
    val grow = profileLayoutGrow(textScale, 0.55f)
    val back = profileHeaderBackRect(frame, backWidth)
    // Room for the selected lift (−8) and echo (+7) inside the scroll viewport.
    val pad = d(12f)
    val regions = armoryRegions(frame, grow)
    val detailLeft = regions.detailLeft
    val detailRight = regions.detailRight
    val detailTop = regions.detailTop
    val detailBottom = regions.detailBottom
    val grid = armoryTileGrid(frame, regions, weaponCount, pad, tileText)
    val type = armoryType(frame.mode)
    val t = textScale.coerceIn(0.75f, 2f)
    val regular = frame.regular
    val plateSize = d(if (regular) 92f else 44f)
    val plate = Rect(detailLeft, detailTop, detailLeft + plateSize, detailTop + plateSize)
    val nameLeft = plate.right + d(if (regular) 18f else 12f)
    val metrics = detail?.invoke(detailRight - nameLeft, detailRight - detailLeft)
    // A name that fits the plate is centred on it; a taller one (two lines of large text) starts
    // at the plate's top and pushes the description down, so it never rises out of the panel.
    val nameHeight = metrics?.nameHeight ?: plateSize
    val name = if (nameHeight <= plateSize) {
        Rect(nameLeft, plate.center.y - nameHeight * 0.5f, detailRight, plate.center.y + nameHeight * 0.5f)
    } else {
        Rect(nameLeft, plate.top, detailRight, plate.top + nameHeight)
    }
    val descriptionTop = max(plate.bottom, name.bottom) + d(if (regular) 18f else 10f)
    // The description slot is as tall as the wrapped description (the details scroll), and at
    // least the board's slot (3 lines, 2 on phones, at the board size) so short texts keep the
    // board's spacing.
    val boardDescription = d(type.body) * 1.4f * (if (regular) 3f else 2f)
    val description = Rect(detailLeft, descriptionTop, detailRight,
        descriptionTop + max(boardDescription, metrics?.descriptionHeight ?: (boardDescription * ceil((if (regular) 3f else 2f) * max(1f, t)) *
            t / (if (regular) 3f else 2f))))
    val tagsTop = description.bottom + d(if (regular) 12f else 8f)
    val tags = Rect(detailLeft, tagsTop, detailRight, tagsTop + (metrics?.tagsHeight ?: armoryTagHeight(frame, t)))
    // The segmented switch under the tags; only the shown segment's blocks take room below it.
    val segmentsTop = tags.bottom + d(if (regular) 20f else 10f)
    val segments = Rect(detailLeft, segmentsTop, detailRight, segmentsTop + workshopSegmentHeight(frame))
    val bodyTop = segments.bottom + d(if (regular) 16f else 8f)
    fun hidden() = Rect(detailLeft, bodyTop, detailRight, bodyTop)
    val upgrade = if (segment == WorkshopSegment.UPGRADE) {
        Rect(detailLeft, bodyTop, detailRight, bodyTop + (upgradeHeight?.invoke(detailRight - detailLeft) ?: workshopUpgradeBlockHeight(frame)))
    } else hidden()
    val showMastery = segment == WorkshopSegment.MASTERY
    val mastery = if (showMastery) Rect(detailLeft, bodyTop, detailRight, bodyTop + max(frame.density * 24f, d(type.label) * t)) else hidden()
    val ladderTop = mastery.bottom + if (showMastery) d(if (regular) 16f else 6f) else 0f
    // Large text, or a panel too narrow to keep the milestones' bonus groups clearly apart,
    // stacks the milestones (one line each) instead of squeezing them side by side.
    val ladderStacked = armoryMasteryStacked(t) || metrics?.masterySideBySide == false
    val ladderHeight = if (ladderStacked) {
        armoryLadderCellsTop(frame, stacked = true, textScale = t) + d(if (regular) 12f else 10f) + d(10f) +
            masteryCount * d(type.ladderName) * ARMORY_STACKED_LINE * t
    } else {
        d(if (regular) 94f else 60f) * t
    }
    val ladder = Rect(detailLeft, ladderTop, detailRight, ladderTop + if (showMastery) ladderHeight else 0f)
    // The weapon sheet: the mastery forms under the ladder; the live synergy and the typed facts on Details.
    val sectionGap = d(if (regular) 24f else 14f)
    val bodyLine = d(type.body) * 1.4f * t
    val labelLine = d(type.label) * 1.5f * t
    val forms = if (showMastery) {
        val formsTop = ladder.bottom + sectionGap
        Rect(detailLeft, formsTop, detailRight, formsTop + (metrics?.formsHeight ?: (labelLine + bodyLine * 3f + d(18f))))
    } else hidden()
    val showDetails = segment == WorkshopSegment.DETAILS
    val synergy = if (showDetails) {
        Rect(detailLeft, bodyTop, detailRight, bodyTop + (metrics?.synergyHeight ?: (labelLine * 4f + bodyLine * 5f)))
    } else hidden()
    val facts = if (showDetails) {
        val factsTop = synergy.bottom + sectionGap
        Rect(detailLeft, factsTop, detailRight, factsTop + (metrics?.factsHeight ?: (labelLine * 5f + d(16f))))
    } else hidden()
    val visibleBottom = maxOf(upgrade.bottom, forms.bottom, facts.bottom)
    val placement = armoryActionPlacement(frame, detailLeft, detailRight, detailTop, detailBottom, pad, visibleBottom, actionWidth, needRoom)
    val action = placement.action
    return ArmoryLayout(frame, back, grid.gridViewport, grid.tiles, grid.gridContentHeight, grid.tileH + regions.gap, regions.columns,
        placement.detailViewport, placement.detailContentHeight, plate, name, description, tags, mastery, ladder, action, placement.need,
        placement.actionPinned, grid.tileIcon, grid.tileIconY, ladderStacked, forms, synergy, facts, segments, segment, upgrade)
}


/** The primary action under the visible details (pinned to the panel's bottom when they run past it), and the detail viewport. */
private class ArmoryActionPlacement(
    val action: Rect,
    val need: Rect,
    val actionPinned: Boolean,
    val detailViewport: Rect,
    val detailContentHeight: Float,
)

private fun armoryActionPlacement(
    frame: ProfileFrame,
    detailLeft: Float,
    detailRight: Float,
    detailTop: Float,
    detailBottom: Float,
    pad: Float,
    visibleBottom: Float,
    actionWidth: Float,
    needRoom: Boolean,
): ArmoryActionPlacement {
    fun d(value: Float) = frame.d(value)
    val regular = frame.regular
    val actionHeight = d(if (regular) 72f else 52f)
    // A locked weapon keeps room beside the button for "Need N more matter".
    val actionRoom = (detailRight - detailLeft) * if (needRoom) 0.64f else 1f
    val clampedAction = min(actionWidth, actionRoom)
    val limit = max(detailTop + actionHeight, detailBottom)
    val naturalTop = visibleBottom + d(if (regular) 26f else 10f)
    // When the details run past the fold (large text, phones) the primary action is pinned to
    // the bottom of the panel and the details above it scroll.
    val actionPinned = naturalTop + actionHeight > limit
    val actionTop = if (actionPinned) limit - actionHeight else naturalTop
    val action = Rect(detailLeft, actionTop, detailLeft + clampedAction, actionTop + actionHeight)
    val need = Rect(action.right + d(16f), action.top, detailRight, action.bottom)
    val detailViewport = Rect(detailLeft - pad, detailTop - pad, detailRight + pad,
        if (actionPinned) action.top - d(8f) else limit)
    val detailContentHeight = visibleBottom + pad - detailViewport.top
    return ArmoryActionPlacement(action, need, actionPinned, detailViewport, detailContentHeight)
}
/** Where [armoryLayout] puts the weapon grid and the detail panel in each layout mode, and how tall a tile row may grow. */
private class ArmoryRegions(
    val gridLeft: Float,
    val gridRight: Float,
    val gridTop: Float,
    val gridBottom: Float,
    val detailLeft: Float,
    val detailRight: Float,
    val detailTop: Float,
    val detailBottom: Float,
    val columns: Int,
    val gap: Float,
    val tileHeight: (width: Float, available: Float) -> Float,
)

private fun armoryRegions(frame: ProfileFrame, grow: Float): ArmoryRegions {
    fun d(value: Float) = frame.d(value)
    val gridLeft: Float
    val gridRight: Float
    val gridTop: Float
    val gridBottom: Float
    val detailLeft: Float
    val detailRight: Float
    val detailTop: Float
    val detailBottom: Float
    val columns: Int
    val gap: Float
    val tileHeight: (width: Float, available: Float) -> Float
    when (frame.mode) {
        ProfileLayoutMode.REGULAR -> {
            gridLeft = frame.x(56f)
            gridRight = frame.x(886f)
            gridTop = frame.headerHeight + d(24f)
            gridBottom = frame.height - d(16f)
            detailLeft = frame.x(950f)
            detailRight = frame.right
            detailTop = d(108f)
            detailBottom = frame.height - d(16f)
            columns = 4
            gap = d(12f)
            tileHeight = { _, _ -> d(172f) * grow }
        }
        ProfileLayoutMode.COMPACT_LANDSCAPE -> {
            val detailWidth = (frame.width * 0.38f).coerceIn(d(250f), d(340f))
            detailRight = frame.right
            detailLeft = detailRight - detailWidth
            gridLeft = frame.left
            gridRight = detailLeft - d(28f)
            gridTop = frame.headerHeight + d(4f)
            gridBottom = frame.height - d(4f)
            detailTop = frame.headerHeight + d(8f)
            detailBottom = frame.height - d(8f)
            columns = if (gridRight - gridLeft >= d(430f)) 4 else 3
            gap = d(8f)
            // Rows stay at least 84 px tall (the press target), even when tiles are narrower than that.
            tileHeight = { width, available -> available.coerceIn(d(84f) * grow, max(d(84f) * grow, width * 0.95f * grow)) }
        }
        ProfileLayoutMode.COMPACT_PORTRAIT -> {
            // The detail dock grows with the text so the ladder starts above the pinned action.
            val dock = d(300f) * grow
            gridLeft = frame.left
            gridRight = frame.right
            gridTop = frame.headerHeight
            gridBottom = frame.height - dock
            detailLeft = frame.left
            detailRight = frame.right
            detailTop = gridBottom + d(12f)
            detailBottom = frame.height - d(10f)
            columns = 3
            gap = d(8f)
            // Rows stay at least 84 px tall (the press target), even when tiles are narrower than that.
            tileHeight = { width, available -> available.coerceIn(d(84f) * grow, max(d(84f) * grow, width * 0.95f * grow)) }
        }
    }
    return ArmoryRegions(gridLeft, gridRight, gridTop, gridBottom, detailLeft, detailRight, detailTop, detailBottom, columns, gap, tileHeight)
}

/** The weapon tiles of [armoryLayout]: their boxes, row height, icon size and line, and the grid viewport. */
private class ArmoryTileGrid(
    val tiles: List<Rect>,
    val tileH: Float,
    val tileIcon: Float,
    val tileIconY: Float,
    val gridViewport: Rect,
    val gridContentHeight: Float,
)

private fun armoryTileGrid(
    frame: ProfileFrame,
    regions: ArmoryRegions,
    weaponCount: Int,
    pad: Float,
    tileText: ((innerWidth: Float) -> ArmoryTileMetrics)?,
): ArmoryTileGrid {
    fun d(value: Float) = frame.d(value)
    val gridLeft = regions.gridLeft
    val gridRight = regions.gridRight
    val gridTop = regions.gridTop
    val gridBottom = regions.gridBottom
    val columns = regions.columns
    val gap = regions.gap
    val tileHeight = regions.tileHeight
    val tileWidth = ((gridRight - gridLeft) - gap * (columns - 1)) / columns
    val rows = if (weaponCount <= 0) 0 else (weaponCount + columns - 1) / columns
    // Compact grids size their rows to fit the viewport when a row stays at least 84 px tall.
    val fitHeight = if (rows == 0) 0f else (gridBottom - gridTop - pad - gap * (rows - 1)) / rows
    val type = armoryType(frame.mode)
    // Every tile draws its icon at one size, between the tallest status line and the tallest
    // name block of the grid, so a name that wraps never shrinks its own icon; when large text
    // leaves less than the smallest icon, the tiles grow (the grid scrolls) instead.
    val insets = armoryTileInsets(frame)
    val tileMetrics = tileText?.invoke(tileWidth - insets.left - insets.right)
    val iconMax = d(type.tileIcon)
    val iconMin = iconMax * ARMORY_TILE_ICON_MIN
    val iconGap = d(ARMORY_TILE_ICON_GAP)
    val iconBandTop = if (tileMetrics == null) 0f else insets.top + tileMetrics.statusHeight + iconGap
    val iconBandBelow = if (tileMetrics == null) 0f else insets.bottom + tileMetrics.nameBlockHeight + iconGap
    val tileH = max(tileHeight(tileWidth, fitHeight), if (tileMetrics == null) 0f else iconBandTop + iconMin + iconBandBelow)
    val tileIcon = if (tileMetrics == null) iconMax else (tileH - iconBandTop - iconBandBelow).coerceIn(iconMin, iconMax)
    val tileIconY = if (tileMetrics == null) tileH * 0.5f else (iconBandTop + tileH - iconBandBelow) * 0.5f
    val tiles = List(weaponCount) { index ->
        val column = index % columns
        val row = index / columns
        val left = pad + column * (tileWidth + gap)
        val top = pad + row * (tileH + gap)
        Rect(left, top, left + tileWidth, top + tileH)
    }
    val gridViewport = Rect(gridLeft - pad, gridTop - pad, gridRight + pad, max(gridTop, gridBottom))
    val gridContentHeight = if (rows == 0) 0f else pad * 2f + rows * tileH + (rows - 1) * gap
    return ArmoryTileGrid(tiles, tileH, tileIcon, tileIconY, gridViewport, gridContentHeight)
}

/** Height in px of one detail tag chip at the board-relative text multiplier [textScale]. */
internal fun armoryTagHeight(frame: ProfileFrame, textScale: Float): Float =
    frame.d(if (frame.regular) 22f else 20f) * max(1f, textScale.coerceIn(0.75f, 2f) * 0.9f)

/**
 * Offset of the mastery level cells below the ladder's top: room for the "Lvl N" labels above
 * them, or a small gap when the milestones are [stacked] under the cells (their levels are in the list).
 */
internal fun armoryLadderCellsTop(frame: ProfileFrame, stacked: Boolean, textScale: Float): Float = when {
    stacked -> frame.d(4f)
    else -> frame.d(if (frame.regular) 30f else 22f) * textScale.coerceIn(0.75f, 2f)
}
