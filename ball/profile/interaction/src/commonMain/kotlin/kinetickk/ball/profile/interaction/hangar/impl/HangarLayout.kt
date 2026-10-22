// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.hangar.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.ProfileLayoutMode
import kinetickk.ball.profile.interaction.profileHeaderBackRect
import kinetickk.ball.profile.interaction.profileLayoutGrow
import kinetickk.ball.profile.interaction.workshop.impl.WorkshopSegment
import kinetickk.ball.profile.interaction.workshop.impl.workshopSegmentHeight
import kinetickk.ball.profile.interaction.workshop.impl.workshopUpgradeBlockHeight
import kotlin.math.max
import kotlin.math.min

/**
 * One geometry for the Hangar: drawing, Compose placement and [resolveHangarPress]. The roster
 * tiles and the Select button are in screen coordinates; the detail rects are in detail content
 * coordinates (add [detailViewport] top-left, subtract the detail scroll).
 */
internal class HangarLayout(
    val frame: ProfileFrame,
    val back: Rect,
    /** The roster: [HANGAR_COLUMNS] × [HANGAR_ROWS] tiles in character order. */
    val roster: Rect,
    val tiles: List<Rect>,
    val detailViewport: Rect,
    val detailContentHeight: Float,
    val preview: Rect,
    val tag: Rect,
    val name: Rect,
    val description: Rect,
    /** Mass, Integrity, Handling and Heat bars. */
    val traits: List<Rect>,
    val difficulty: Rect,
    /** The signature block: its label beside one row per signature weapon (each with its harmony line). */
    val signature: Rect,
    /** The unlock progress (a locked character's Upgrade segment). */
    val unlock: Rect,
    val select: Rect,
    /** One row per signature weapon inside [signature] (detail content coordinates), harmony line included. */
    val signatureRows: List<Rect> = emptyList(),
    /** Height (px) of a signature row's main line (icon, name, state), above its harmony line. */
    val signatureLine: Float = 0f,
    /** The segmented switch under the description (Upgrade, Traits). */
    val segments: Rect = Rect.Zero,
    /** The shown segment: the blocks of the other one are 0 px tall at the segment body's top. */
    val segment: WorkshopSegment = WorkshopSegment.UPGRADE,
    /** The upgrade block's slot (an unlocked character's Upgrade segment). */
    val upgrade: Rect = Rect.Zero,
) {
    val detailScrollMax: Float get() = max(0f, detailContentHeight - detailViewport.height)
}

internal const val HANGAR_COLUMNS = 4
internal const val HANGAR_ROWS = 3

/** Board type sizes per mode (design px before the frame scale and the text-size setting). */
internal class HangarType(
    val tileName: Float,
    val tileStatus: Float,
    val name: Float,
    val body: Float,
    val mono: Float,
    val select: Float,
)

internal fun hangarType(mode: ProfileLayoutMode): HangarType = when (mode) {
    ProfileLayoutMode.REGULAR -> HangarType(17f, 12f, 40f, 17f, 11f, 30f)
    ProfileLayoutMode.COMPACT_LANDSCAPE -> HangarType(12f, 9.5f, 22f, 12.5f, 9.5f, 20f)
    ProfileLayoutMode.COMPACT_PORTRAIT -> HangarType(12f, 9.5f, 24f, 13f, 10f, 20f)
}

/**
 * [textScale] is the board-relative text multiplier ([kinetickk.ball.profile.interaction.profileTextScale]);
 * [descriptionHeight] measures the inspected character's description at a width (three lines without it).
 */
internal fun hangarLayout(
    frame: ProfileFrame,
    textScale: Float,
    backWidth: Float,
    /** Per signature weapon, whether it shows a harmony line under its row. */
    signatureHarmony: List<Boolean> = listOf(false, false),
    /** The detail segment shown under the description; the other takes no room. */
    segment: WorkshopSegment = WorkshopSegment.UPGRADE,
    /** The inspected character is locked: its Upgrade segment shows the unlock progress instead of the upgrade block. */
    locked: Boolean = false,
    descriptionHeight: ((width: Float) -> Float)? = null,
): HangarLayout {
    fun d(value: Float) = frame.d(value)
    val t = textScale.coerceIn(0.75f, 2f)
    val grow = profileLayoutGrow(t, 0.5f)
    val back = profileHeaderBackRect(frame, backWidth)
    val type = hangarType(frame.mode)
    val gap: Float
    val roster: Rect
    val detailLeft: Float
    val detailRight: Float
    val detailTop: Float
    val detailBottom: Float
    when (frame.mode) {
        ProfileLayoutMode.REGULAR -> {
            gap = d(16f)
            roster = Rect(frame.x(56f), d(112f), frame.x(820f), frame.height - d(28f))
            detailLeft = frame.x(872f)
            detailRight = frame.right
            detailTop = d(112f)
            detailBottom = frame.height - d(24f)
        }
        ProfileLayoutMode.COMPACT_LANDSCAPE -> {
            gap = d(8f)
            val detailWidth = (frame.width * 0.42f).coerceIn(d(250f), d(380f))
            detailRight = frame.right
            detailLeft = detailRight - detailWidth
            roster = Rect(frame.left, frame.headerHeight + d(6f), detailLeft - d(20f), frame.height - d(8f))
            detailTop = frame.headerHeight + d(6f)
            detailBottom = frame.height - d(8f)
        }
        ProfileLayoutMode.COMPACT_PORTRAIT -> {
            gap = d(8f)
            val tileWidth = (frame.right - frame.left - gap * (HANGAR_COLUMNS - 1)) / HANGAR_COLUMNS
            val tileHeight = min(tileWidth, d(92f)).coerceAtLeast(frame.density * 56f)
            val top = frame.headerHeight + d(4f)
            roster = Rect(frame.left, top, frame.right, top + tileHeight * HANGAR_ROWS + gap * (HANGAR_ROWS - 1))
            detailLeft = frame.left
            detailRight = frame.right
            detailTop = roster.bottom + d(24f)
            detailBottom = frame.height - d(10f)
        }
    }
    val tileWidth = (roster.width - gap * (HANGAR_COLUMNS - 1)) / HANGAR_COLUMNS
    val tileHeight = (roster.height - gap * (HANGAR_ROWS - 1)) / HANGAR_ROWS
    val tiles = List(HANGAR_COLUMNS * HANGAR_ROWS) { index ->
        val left = roster.left + (index % HANGAR_COLUMNS) * (tileWidth + gap)
        val top = roster.top + (index / HANGAR_COLUMNS) * (tileHeight + gap)
        Rect(left, top, left + tileWidth, top + tileHeight)
    }

    val regular = frame.regular
    val width = max(d(120f), detailRight - detailLeft)
    val selectHeight = d(if (regular) 64f else 48f).coerceAtLeast(frame.density * 48f)
    val select = Rect(detailLeft, detailBottom - selectHeight, detailRight, detailBottom)
    val viewport = Rect(detailLeft, detailTop, detailRight, max(detailTop + d(40f), select.top - d(if (regular) 16f else 8f)))
    val monoHeight = d(type.mono) * 1.4f * t
    val previewHeight = d(when (frame.mode) {
        ProfileLayoutMode.REGULAR -> 132f
        ProfileLayoutMode.COMPACT_LANDSCAPE -> 70f
        ProfileLayoutMode.COMPACT_PORTRAIT -> 80f
    })
    val preview = Rect(0f, 0f, width, previewHeight)
    val tag = Rect(0f, preview.bottom + d(if (regular) 12f else 6f), width, preview.bottom + d(if (regular) 12f else 6f) + monoHeight)
    val nameTop = tag.bottom + d(if (regular) 6f else 2f)
    val name = Rect(0f, nameTop, width, nameTop + d(type.name) * 1.12f * t)
    val descriptionTop = name.bottom + d(if (regular) 10f else 5f)
    val description = Rect(0f, descriptionTop, width,
        descriptionTop + (descriptionHeight?.invoke(width) ?: (d(type.body) * 1.4f * 3f * t)))
    // The segmented switch under the description; only the shown segment's blocks take room below it.
    val segmentsTop = description.bottom + d(if (regular) 16f else 8f)
    val segments = Rect(0f, segmentsTop, width, segmentsTop + workshopSegmentHeight(frame))
    val bodyTop = segments.bottom + d(if (regular) 16f else 8f)
    fun hidden() = Rect(0f, bodyTop, width, bodyTop)
    val showTraits = segment == WorkshopSegment.TRAITS
    val showUpgrade = segment == WorkshopSegment.UPGRADE
    val rowHeight = d(if (regular) 24f else 17f) * grow
    var y = bodyTop
    val traits = List(4) { index ->
        if (showTraits) Rect(0f, y + index * rowHeight, width, y + (index + 1) * rowHeight) else hidden()
    }
    y = if (showTraits) traits.last().bottom + d(if (regular) 10f else 5f) else bodyTop
    val difficulty = if (showTraits) Rect(0f, y, width, y + rowHeight) else hidden()
    y = if (showTraits) difficulty.bottom + d(if (regular) 10f else 5f) else bodyTop
    // Each signature weapon takes a row (icon, name, state), plus its harmony line when a heirloom
    // candidate carries its key.
    val weaponRow = max(rowHeight * 1.3f, frame.density * 28f)
    val signatureTop = y
    val signatureRows = signatureHarmony.ifEmpty { listOf(false) }.map { harmony ->
        if (!showTraits) return@map hidden()
        val row = Rect(0f, y, width, y + weaponRow + if (harmony) monoHeight else 0f)
        y = row.bottom + d(if (regular) 4f else 2f)
        row
    }
    val signature = if (showTraits) Rect(0f, signatureTop, width, signatureRows.last().bottom) else hidden()
    val unlock = if (showUpgrade && locked) Rect(0f, bodyTop, width, bodyTop + monoHeight * 2.2f) else hidden()
    val upgrade = if (showUpgrade && !locked) Rect(0f, bodyTop, width, bodyTop + workshopUpgradeBlockHeight(frame)) else hidden()
    val contentHeight = maxOf(signature.bottom, unlock.bottom, upgrade.bottom) + d(4f)
    return HangarLayout(frame, back, roster, tiles, viewport, contentHeight, preview, tag, name, description, traits, difficulty,
        signature, unlock, select, signatureRows, weaponRow, segments, segment, upgrade)
}

/** Maps a press to the Hangar action it hits: header Back, a roster tile or the Select button. */
internal fun resolveHangarPress(layout: HangarLayout, state: HangarState, x: Float, y: Float): HangarAction? {
    val point = Offset(x, y)
    if (layout.back.contains(point)) return HangarAction.Back
    layout.tiles.forEachIndexed { index, tile ->
        if (tile.contains(point)) return state.model.shapes.getOrNull(index)?.shape?.let(HangarAction::Activate)
    }
    if (layout.select.contains(point)) return HangarAction.SelectInspected
    return null
}
