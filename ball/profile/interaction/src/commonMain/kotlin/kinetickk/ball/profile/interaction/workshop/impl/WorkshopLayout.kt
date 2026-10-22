// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.workshop.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.ProfileLayoutMode
import kinetickk.ball.profile.interaction.profileHeaderBackRect
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTab
import kotlin.math.max

/**
 * The Workshop header: Back, the rule (regular only), one tab per [WorkshopTab], the right cluster
 * (count on the regular frame, (!), the tab's extra control) and the Matter chip. The strip takes
 * the old title's place, so [ProfileFrame.headerHeight] and every body's top stay where they were.
 */
internal class WorkshopHeaderLayout(
    val frame: ProfileFrame,
    val back: Rect,
    val rule: Rect?,
    /** One rect per tab, in [WorkshopTab] order. */
    val tabs: List<Rect>,
    /** The owned count (regular only, and only while it fits). */
    val count: Rect?,
    val info: Rect?,
    /** The tab's extra control (Matrix Reset, Armory pager); null when there is none or it moved into the body. */
    val extra: Rect?,
    val chip: Rect,
    /** Tab label size (sp, before the text-size setting): [WorkshopHeaderMetrics.labelSize] × the frame scale × [labelFactor]. */
    val labelSize: Float,
    /** The fit factor applied to the labels (1 at rest, down to [WORKSHOP_LABEL_FLOOR] or the portrait floor). */
    val labelFactor: Float,
    /** The Matrix tab shows its short label. */
    val short: Boolean,
    /** Landscape had no room left: the tab extra sits in the body, pinned to the grid's top-right corner. */
    val extraInBody: Boolean,
) {
    /** True when the tabs end before the right cluster (or the chip) and nothing in the row overlaps. */
    val fits: Boolean
        get() {
            if (frame.portrait) {
                // The cluster shares the Back row; the tabs fill their own row.
                return (info ?: extra)?.let { first -> first.left >= back.right - 0.5f } ?: true
            }
            val right = (listOfNotNull(count, info, extra).minOfOrNull { it.left } ?: chip.left) -
                frame.d(workshopHeaderMetrics(frame.mode).clusterGap)
            return tabs.last().right <= right + 0.5f && tabs.zipWithNext().all { (a, b) -> a.right <= b.left + 0.5f }
        }
}

/** The smallest label fit factor (0.70 in portrait, where the tabs share the full width). */
internal const val WORKSHOP_LABEL_FLOOR = 0.75f
internal const val WORKSHOP_PORTRAIT_LABEL_FLOOR = 0.70f

/** Board sizes of the tab strip per mode (design px before the frame scale). */
internal class WorkshopHeaderMetrics(
    val labelSize: Float,
    val padding: Float,
    val top: Float,
    val height: Float,
    val gap: Float,
    val clusterGap: Float,
)

internal fun workshopHeaderMetrics(mode: ProfileLayoutMode): WorkshopHeaderMetrics = when (mode) {
    ProfileLayoutMode.REGULAR -> WorkshopHeaderMetrics(21f, 20f, 22f, 40f, 6f, 18f)
    ProfileLayoutMode.COMPACT_LANDSCAPE -> WorkshopHeaderMetrics(18f, 14f, 10f, 36f, 4f, 12f)
    ProfileLayoutMode.COMPACT_PORTRAIT -> WorkshopHeaderMetrics(19f, 8f, 60f, 40f, 6f, 12f)
}

/**
 * Lays the header out and applies the fit rule in order until the row fits: (1) drop the count,
 * (2) shrink the labels in 0.05 steps down to the floor, (3) on the regular frame switch the Matrix
 * tab to its short label, (4) in landscape move the tab extra into the body. Labels never truncate.
 *
 * [labelWidths] measures the three labels (px, text-size setting applied) at a label size (sp) and
 * with the short Matrix label or the long one; [countWidth] is null when the tab shows no count;
 * [extraWidth] is 0 for a tab without an extra control.
 */
internal fun workshopHeaderLayout(
    frame: ProfileFrame,
    backWidth: Float,
    chipWidth: Float,
    chipHeight: Float,
    countWidth: Float?,
    infoShown: Boolean,
    extraWidth: Float,
    labelWidths: (size: Float, short: Boolean) -> List<Float>,
): WorkshopHeaderLayout {
    fun d(value: Float) = frame.d(value)
    val metrics = workshopHeaderMetrics(frame.mode)
    val back = profileHeaderBackRect(frame, backWidth)
    val chip = Rect(frame.right - chipWidth, back.center.y - chipHeight * 0.5f, frame.right, back.center.y + chipHeight * 0.5f)
    val gap = d(metrics.clusterGap)
    val infoSize = frame.density * 24f
    // Label sizes are text units (sp before the text-size setting), like every profile header text.
    val baseSize = metrics.labelSize * frame.k

    // The right cluster, right-aligned before the chip and centred on the Back row: count, (!), extra.
    fun cluster(withCount: Boolean, withExtra: Boolean): Triple<Rect?, Rect?, Rect?> {
        var right = chip.left - gap
        val centerY = back.center.y
        val extra = if (withExtra && extraWidth > 0f) {
            Rect(right - extraWidth, centerY - back.height * 0.5f, right, centerY + back.height * 0.5f).also { right = it.left - gap }
        } else null
        val info = if (infoShown) {
            Rect(right - infoSize, centerY - infoSize * 0.5f, right, centerY + infoSize * 0.5f).also { right = it.left - gap }
        } else null
        val count = if (withCount && countWidth != null) {
            Rect(right - countWidth, centerY - infoSize * 0.5f, right, centerY + infoSize * 0.5f)
        } else null
        return Triple(count, info, extra)
    }

    if (frame.portrait) {
        // Three equal cells across the second header row; the cluster stays on the Back row, and an
        // extra that does not fit there moves into the body like the landscape one.
        val top = d(metrics.top)
        val cell = (frame.right - frame.left - d(metrics.gap) * 2f) / 3f
        val tabs = List(3) { index ->
            val left = frame.left + index * (cell + d(metrics.gap))
            Rect(left, top, left + cell, top + d(metrics.height))
        }
        val room = cell - d(metrics.padding) * 2f
        var factor = 1f
        while (factor > WORKSHOP_PORTRAIT_LABEL_FLOOR + 0.001f && labelWidths(baseSize * factor, true).any { it > room }) {
            factor = max(WORKSHOP_PORTRAIT_LABEL_FLOOR, factor - 0.05f)
        }
        var (_, info, extra) = cluster(withCount = false, withExtra = true)
        val extraInBody = extra != null && (info ?: extra).left < back.right + gap
        if (extraInBody) {
            val moved = cluster(withCount = false, withExtra = false)
            info = moved.second
            extra = null
        }
        return WorkshopHeaderLayout(frame, back, null, tabs, null, info, extra, chip, baseSize * factor, factor, true, extraInBody)
    }

    val regular = frame.regular
    val rule = if (regular) {
        val x = back.right + d(18f)
        Rect(x, back.center.y - d(14f), x + frame.density, back.center.y + d(14f))
    } else null
    val tabsLeft = rule?.let { it.right + d(18f) } ?: (back.right + d(12f))
    val top = d(metrics.top)
    val height = d(metrics.height)
    fun tabs(size: Float, short: Boolean): List<Rect> {
        var x = tabsLeft
        return labelWidths(size, short).map { width ->
            Rect(x, top, x + width + d(metrics.padding) * 2f, top + height).also { x = it.right + d(metrics.gap) }
        }
    }
    fun layout(withCount: Boolean, withExtra: Boolean, short: Boolean, factor: Float): WorkshopHeaderLayout {
        val (count, info, extra) = cluster(withCount = withCount, withExtra = withExtra)
        return WorkshopHeaderLayout(frame, back, rule, tabs(baseSize * factor, short), count, info, extra, chip,
            baseSize * factor, factor, short, extraInBody = extraWidth > 0f && !withExtra)
    }
    val factors = generateSequence(1f) { factor -> (factor - 0.05f).takeIf { it >= WORKSHOP_LABEL_FLOOR - 0.001f } }
        .map { max(WORKSHOP_LABEL_FLOOR, it) }
    // Phones always show the short Matrix label; the regular frame switches to it only as step (3).
    val steps = if (regular) {
        sequenceOf(layout(withCount = true, withExtra = true, short = false, factor = 1f)) +
            factors.map { layout(withCount = false, withExtra = true, short = false, factor = it) } +
            factors.map { layout(withCount = false, withExtra = true, short = true, factor = it) }
    } else {
        factors.map { layout(withCount = false, withExtra = true, short = true, factor = it) } +
            factors.map { layout(withCount = false, withExtra = false, short = true, factor = it) }
    }
    // Nothing fits: keep the last step (labels never truncate; the fit test guards the supported frames).
    var last: WorkshopHeaderLayout? = null
    for (step in steps) {
        if (step.fits) return step
        last = step
    }
    return checkNotNull(last)
}

/** What a press on the header hits: Back, a tab, the (!) button or the extra; null passes it to the body. */
internal sealed interface WorkshopPress {
    data object Back : WorkshopPress
    data class Tab(val tab: WorkshopTab) : WorkshopPress
    data object Info : WorkshopPress
    data object Extra : WorkshopPress
}

internal fun resolveWorkshopPress(layout: WorkshopHeaderLayout, x: Float, y: Float): WorkshopPress? {
    val point = Offset(x, y)
    if (layout.back.contains(point)) return WorkshopPress.Back
    layout.tabs.forEachIndexed { index, tab -> if (tab.contains(point)) return WorkshopPress.Tab(WorkshopTab.entries[index]) }
    if (layout.info?.contains(point) == true) return WorkshopPress.Info
    if (layout.extra?.contains(point) == true) return WorkshopPress.Extra
    return null
}

/** Height of a detail's segmented switch: d(34) on the regular frame, d(32) on phones. */
internal fun workshopSegmentHeight(frame: ProfileFrame): Float = frame.d(if (frame.regular) 34f else 32f)

/** The upgrade block's slot under the Upgrade segment: d(168) on the regular frame, d(130) on phones. */
internal fun workshopUpgradeBlockHeight(frame: ProfileFrame): Float = frame.d(if (frame.regular) 168f else 130f)

/**
 * The segment cells inside [strip], from its left edge: each [naturalWidths] entry (px of a 34 dp
 * `kkSegmentWidth` cell) scaled to the strip's height, d(3) apart.
 */
internal fun workshopSegmentCells(frame: ProfileFrame, strip: Rect, naturalWidths: List<Float>): List<Rect> {
    val scale = strip.height / (frame.density * 34f)
    var x = strip.left
    return naturalWidths.map { width ->
        Rect(x, strip.top, x + width * scale, strip.bottom).also { x = it.right + frame.d(3f) }
    }
}
