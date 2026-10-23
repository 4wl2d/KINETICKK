// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.workshop.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.toSize
import kinetickk.ball.profile.interaction.LocalProfilePanelFocus
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.ProfileSlabButton
import kinetickk.ball.profile.interaction.ProfileTextProbe
import kinetickk.ball.profile.interaction.dp
import kinetickk.ball.profile.interaction.drawProfileChip
import kinetickk.ball.profile.interaction.drawProfileText
import kinetickk.ball.profile.interaction.formatMatter
import kinetickk.ball.profile.interaction.kkChipWidth
import kinetickk.ball.profile.interaction.localization.ProfileScreensRedesignText
import kinetickk.ball.profile.interaction.profileFrame
import kinetickk.ball.profile.interaction.profileHeaderBackWidth
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTab
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** A tab's label: its long form and the short one the Matrix tab switches to when the strip is tight. */
internal fun WorkshopTab.label(language: AppLanguage, short: Boolean = false): String = language.text(when (this) {
    WorkshopTab.CHARACTERS -> ProfileScreensRedesignText.TabCharacters
    WorkshopTab.WEAPONS -> ProfileScreensRedesignText.TabWeapons
    WorkshopTab.MATRIX -> if (short) ProfileScreensRedesignText.TabMatrixShort else ProfileScreensRedesignText.TabMatrix
})

internal fun WorkshopSegment.label(language: AppLanguage): String = language.text(when (this) {
    WorkshopSegment.UPGRADE -> ProfileScreensRedesignText.SegmentUpgrade
    WorkshopSegment.TRAITS -> ProfileScreensRedesignText.SegmentTraits
    WorkshopSegment.MASTERY -> ProfileScreensRedesignText.Mastery
    WorkshopSegment.DETAILS -> ProfileScreensRedesignText.SegmentDetails
})

/** Test tag of a tab node (`kinetickk.workshop.tab.characters` …). */
internal fun WorkshopTab.tag(): String = "kinetickk.workshop.tab." + name.lowercase()

/** Test tag of a segment node (`kinetickk.workshop.segment.upgrade` …). */
internal fun WorkshopSegment.tag(): String = "kinetickk.workshop.segment.$key"

internal const val WORKSHOP_TAG = "profile-workshop"
internal const val WORKSHOP_ANNOUNCE_TAG = "kinetickk.workshop.announce"
internal const val WORKSHOP_ALERT_TAG = "kinetickk.workshop.announce.alert"

/** The body fades in from ink over this long after a tab change (Motion: no Session transition). */
internal const val WORKSHOP_BODY_FADE_MS = 150

private fun tabLabelStyle(measurer: CanvasTextMeasurer, size: Float) = measurer.typography.condStyle(size, trackingEm = 0.03f)

/** Natural widths (px) of the three tab labels at [size] (sp), the Matrix label short or long. */
internal fun workshopTabLabelWidths(measurer: CanvasTextMeasurer, language: AppLanguage, size: Float, short: Boolean): List<Float> =
    WorkshopTab.entries.map { tab ->
        measureKkText(measurer, tab.label(language, short), tabLabelStyle(measurer, size), uppercase = true).size.width.toFloat()
    }

/** The header layout the panel uses for these inputs (shared with the fit test). */
internal fun workshopHeader(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    language: AppLanguage,
    matter: Long,
    count: String?,
    infoShown: Boolean,
    extraWidth: Float,
): WorkshopHeaderLayout {
    val backWidth = profileHeaderBackWidth(measurer, frame, language.text(ProfileScreensRedesignText.Back))
    val chipScale = frame.k * if (frame.regular) 1f else 0.9f
    val chipWidth = kkChipWidth(measurer, formatMatter(matter, language), language.text(ProfileScreensRedesignText.Matter),
        density = frame.density) * chipScale
    val chipHeight = frame.density * 34f * chipScale
    val countWidth = count?.let {
        measureKkText(measurer, it, measurer.typography.wideStyle(18f * frame.k, tabular = true, lineHeightEm = 1f)).size.width.toFloat()
    }
    return workshopHeaderLayout(frame, backWidth, chipWidth, chipHeight, countWidth, infoShown, extraWidth) { size, short ->
        workshopTabLabelWidths(measurer, language, size, short)
    }
}

/**
 * The Workshop scaffold: full-bleed [background], the header (Back, the tab strip, the tab's count,
 * (!) and extra control, the Matter chip), the shown tab's [content] and the two live regions. Tab
 * and segment choices go to [onAction]; Back goes to [onBack]. Digits 1, 2 and 3 pick a tab from
 * anywhere inside; arrows move focus like every profile panel.
 */
@Composable
internal fun WorkshopPanel(
    tab: WorkshopTab,
    count: String?,
    info: String,
    matter: Long,
    scale: Float,
    onBack: () -> Unit,
    onAction: (WorkshopAction) -> Unit,
    background: DrawScope.(ProfileFrame) -> Unit,
    contentKey: Any = Unit,
    headerExtra: (@Composable RowScope.(ProfileFrame) -> Unit)? = null,
    headerExtraWidth: (ProfileFrame) -> Float = { 0f },
    announcement: String? = null,
    alert: String? = null,
    /** A key that reaches the panel itself (focus on no control): the tab may own it, as Characters owns Enter. */
    onPanelKey: (KeyEvent) -> Boolean = { false },
    content: @Composable BoxScope.(frame: ProfileFrame, header: WorkshopHeaderLayout) -> Unit,
) {
    val language = LocalAppLanguage.current
    val panelFocus = remember { FocusRequester() }
    LaunchedEffect(contentKey) { panelFocus.requestFocus() }
    val focusManager = LocalFocusManager.current
    val title = language.text(ProfileScreensRedesignText.WorkshopTitle)
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .testTag(WORKSHOP_TAG)
            .semantics { paneTitle = title }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val picked = when (event.key) {
                    Key.One, Key.NumPad1 -> WorkshopTab.CHARACTERS
                    Key.Two, Key.NumPad2 -> WorkshopTab.WEAPONS
                    Key.Three, Key.NumPad3 -> WorkshopTab.MATRIX
                    else -> return@onPreviewKeyEvent false
                }
                onAction(WorkshopAction.SelectTab(picked, focusBody = true))
                true
            }
            .focusRequester(panelFocus)
            .onKeyEvent { event ->
                if (onPanelKey(event)) return@onKeyEvent true
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val direction = when (event.key) {
                    Key.DirectionUp -> FocusDirection.Up
                    Key.DirectionDown -> FocusDirection.Down
                    Key.DirectionLeft -> FocusDirection.Left
                    Key.DirectionRight -> FocusDirection.Right
                    else -> return@onKeyEvent false
                }
                focusManager.moveFocus(direction)
            }
            .focusable(),
    ) {
        val density = LocalDensity.current.density
        val frame = remember(constraints.maxWidth, constraints.maxHeight, density) {
            profileFrame(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), density)
        }
        val measurer = rememberKkCanvasMeasurer(scale)
        val extraWidth = if (headerExtra == null) 0f else headerExtraWidth(frame)
        val header = workshopHeader(measurer, frame, language, matter, count, infoShown = true, extraWidth = extraWidth)
        CompositionLocalProvider(LocalProfilePanelFocus provides panelFocus) {
            Box(Modifier.fillMaxSize().drawBehind { background(frame) })
            WorkshopHeader(frame, header, tab, count, info, matter, scale, measurer, onBack, onAction,
                headerExtra.takeIf { !header.extraInBody })
            content(frame, header)
            // A tab change fades the new body in from ink; the header stays put.
            val fade = remember { Animatable(0f) }
            val shown = remember { ShownTab(tab) }
            LaunchedEffect(tab) {
                if (shown.tab == tab) return@LaunchedEffect
                shown.tab = tab
                fade.snapTo(1f)
                fade.animateTo(0f, tween(WORKSHOP_BODY_FADE_MS, easing = KkEase.Out))
            }
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val alpha = fade.value
                    if (alpha > 0f) drawRect(Kk.Ink, Offset(0f, frame.headerHeight), Size(size.width, size.height - frame.headerHeight), alpha)
                },
            )
        }
        // One polite region for confirmations and one assertive region for refusals.
        Box(Modifier.size(frame.dp(1f)).testTag(WORKSHOP_ANNOUNCE_TAG).semantics {
            liveRegion = LiveRegionMode.Polite
            if (announcement != null) contentDescription = announcement
        })
        Box(Modifier.size(frame.dp(1f)).testTag(WORKSHOP_ALERT_TAG).semantics {
            liveRegion = LiveRegionMode.Assertive
            if (alert != null) contentDescription = alert
        })
    }
}

/** The tab the body fade last started for (the first composition shows its tab without a fade). */
private class ShownTab(var tab: WorkshopTab)

@Composable
private fun WorkshopHeader(
    frame: ProfileFrame,
    layout: WorkshopHeaderLayout,
    tab: WorkshopTab,
    count: String?,
    info: String,
    matter: Long,
    scale: Float,
    measurer: CanvasTextMeasurer,
    onBack: () -> Unit,
    onAction: (WorkshopAction) -> Unit,
    headerExtra: (@Composable RowScope.(ProfileFrame) -> Unit)?,
) {
    val language = LocalAppLanguage.current
    val typography = rememberInterfaceTypography()
    val back = layout.back
    ProfileSlabButton(
        label = language.text(ProfileScreensRedesignText.Back), onClick = onBack, frame = frame, tag = "$WORKSHOP_TAG-back",
        variant = KkButtonVariant.GHOST, size = if (frame.regular) KkButtonSize.SM else KkButtonSize.XS, textScale = scale,
        modifier = Modifier.offset { IntOffset(back.left.roundToInt(), back.top.roundToInt()) }
            .size(frame.dp(back.width), frame.dp(back.height)),
    )
    WorkshopTabStrip(frame, layout, tab, measurer, language, onAction)
    val countRect = layout.count
    if (count != null && countRect != null) {
        // The count is centred on the Back row; its box spans the row so large text keeps its whole line.
        val rowHeight = layout.back.center.y * 2f
        Box(
            Modifier.offset { IntOffset(countRect.left.roundToInt(), 0) }
                .size(frame.dp(countRect.width + frame.density * 2f), frame.dp(rowHeight)),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(count, style = typography.wideStyle(18f * frame.k * scale, tabular = true, color = Kk.Mute, lineHeightEm = 1f), maxLines = 1,
                onTextLayout = { ProfileTextProbe.record("header.count", null, it, Rect(Offset.Zero, it.size.toSize())) })
        }
    }
    layout.info?.let { rect ->
        Box(Modifier.offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }) {
            KkInfoButton(info, Modifier.testTag("$WORKSHOP_TAG-info"), placement = KkTooltipPlacement.BELOW, textScale = scale)
        }
    }
    val extra = layout.extra
    if (headerExtra != null && extra != null) {
        Row(
            Modifier.offset { IntOffset(extra.left.roundToInt(), extra.top.roundToInt()) }
                .size(frame.dp(extra.width), frame.dp(extra.height)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) { headerExtra(frame) }
    }
    val chip = layout.chip
    val matterText = formatMatter(matter, language)
    val matterLabel = language.text(ProfileScreensRedesignText.Matter)
    val chipScale = frame.k * if (frame.regular) 1f else 0.9f
    // A purchase lets the drawn balance drift to the new one; the node states the balance at once.
    val drift = remember { MatterDrift(matter) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(matter) {
        if (drift.to == matter) return@LaunchedEffect
        drift.from = drift.at(progress.value)
        drift.to = matter
        progress.snapTo(0f)
        progress.animateTo(1f, tween(KkTime.Drift, easing = KkEase.Out))
    }
    Box(
        Modifier.offset { IntOffset(chip.left.roundToInt(), chip.top.roundToInt()) }
            .size(frame.dp(chip.width), frame.dp(chip.height))
            .testTag("$WORKSHOP_TAG-matter")
            .semantics { contentDescription = "$matterText $matterLabel" }
            .drawBehind {
                val shown = drift.at(progress.value)
                val text = if (shown == matter) matterText else formatMatter(shown, language)
                scale(chipScale, chipScale, Offset.Zero) { drawProfileChip(measurer, text, matterLabel) }
            },
    )
}

/** The Matter chip's drift from the balance before a purchase ([from]) to the new one ([to]). */
private class MatterDrift(var from: Long) {
    var to: Long = from

    /** The balance drawn at [progress] 0..1 of the drift (the Out curve may overshoot). */
    fun at(progress: Float): Long = from + ((to - from) * progress.coerceIn(0f, 1f).toDouble()).roundToLong()
}

/**
 * The tab strip: one bone slab under the shown tab (it moves to a new tab with Snap over 90 ms),
 * a sheared `you` underline under it, and a selectable node per tab. Left/Right on a focused tab
 * picks its neighbour (wrapping) and keeps focus on the strip.
 */
@Composable
private fun WorkshopTabStrip(
    frame: ProfileFrame,
    layout: WorkshopHeaderLayout,
    tab: WorkshopTab,
    measurer: CanvasTextMeasurer,
    language: AppLanguage,
    onAction: (WorkshopAction) -> Unit,
) {
    val tabs = WorkshopTab.entries
    val position = remember { Animatable(tab.ordinal.toFloat()) }
    LaunchedEffect(tab) { position.animateTo(tab.ordinal.toFloat(), tween(KkTime.Snap, easing = KkEase.Snap)) }
    val sources = remember { List(tabs.size) { MutableInteractionSource() } }
    val requesters = remember { List(tabs.size) { FocusRequester() } }
    val hovered = sources.map { it.collectIsHoveredAsState().value }
    val focused = sources.map { it.collectIsFocusedAsState().value }
    val labels = tabs.map { it.label(language, short = layout.short && it == WorkshopTab.MATRIX) }
    val paths = remember { KkPathCache(slots = 2 + tabs.size) }
    Box(
        Modifier.size(frame.dp(frame.width), frame.dp(frame.headerHeight)).drawBehind {
            drawWorkshopStrip(measurer, frame, layout, labels, position.value, hovered, focused, paths)
        },
    )
    Box(Modifier.size(frame.dp(frame.width), frame.dp(frame.headerHeight)).selectableGroup()) {
        tabs.forEachIndexed { index, entry ->
            val rect = layout.tabs[index]
            Box(
                Modifier.offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                    .size(frame.dp(rect.width), frame.dp(rect.height))
                    .testTag(entry.tag())
                    .focusRequester(requesters[index])
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val forward = when (event.key) {
                            Key.DirectionRight -> true
                            Key.DirectionLeft -> false
                            else -> return@onKeyEvent false
                        }
                        onAction(WorkshopAction.StepTab(forward))
                        requesters[(index + if (forward) 1 else tabs.size - 1) % tabs.size].requestFocus()
                        true
                    }
                    .semantics { contentDescription = labels[index] }
                    .selectable(selected = entry == tab, interactionSource = sources[index], indication = null, role = Role.Tab) {
                        onAction(WorkshopAction.SelectTab(entry))
                    },
            )
        }
    }
}

/** Draws the rule, the moving slab and underline, hover faces and the labels at [position] (a tab index, animated). */
private fun DrawScope.drawWorkshopStrip(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    layout: WorkshopHeaderLayout,
    labels: List<String>,
    position: Float,
    hovered: List<Boolean>,
    focused: List<Boolean>,
    paths: KkPathCache,
) {
    layout.rule?.let { drawRect(Kk.Line, it.topLeft, it.size) }
    val tabs = layout.tabs
    val cut = frame.d(10f)
    tabs.forEachIndexed { index, rect ->
        if (hovered[index] && abs(position - index) > 0.5f) drawPath(paths.slab(2 + index, rect, cut), Kk.Ink3)
    }
    val low = floor(position).toInt().coerceIn(0, tabs.lastIndex)
    val high = ceil(position).toInt().coerceIn(0, tabs.lastIndex)
    val t = position - low
    val from = tabs[low]
    val to = tabs[high]
    val slab = Rect(lerpF(from.left, to.left, t), from.top, lerpF(from.right, to.right, t), from.bottom)
    drawPath(paths.slab(0, slab, cut), Kk.Bone)
    drawPath(paths.sheared(1, slab.left + frame.d(12f), slab.bottom + frame.d(5f), slab.right - frame.d(12f), slab.bottom + frame.d(8f)),
        measurer.roles.you)
    val style = tabLabelStyle(measurer, layout.labelSize)
    tabs.forEachIndexed { index, rect ->
        // The label inks over as the slab arrives under it.
        val cover = (1f - abs(position - index)).coerceIn(0f, 1f)
        val base = if (hovered[index]) Kk.Bone else Kk.Mute
        val text: TextLayoutResult = measureKkText(measurer, labels[index], style, uppercase = true)
        drawProfileText(text, rect.center.x, rect.center.y, lerp(base, Kk.Ink, cover), "workshop.tab", WorkshopTab.entries[index],
            align = KkAlign.CENTER, valign = KkVAlign.CENTER)
        if (focused[index]) drawWorkshopFocus(frame, rect)
    }
}

private fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun DrawScope.drawWorkshopFocus(frame: ProfileFrame, bounds: Rect) {
    val inset = frame.density * 5f
    drawRect(Kk.Bone, Offset(bounds.left - inset, bounds.top - inset), Size(bounds.width + inset * 2f, bounds.height + inset * 2f),
        style = kkStroke(frame.density * 2f))
}

/**
 * A detail panel's segmented switch in [strip] (the parent's coordinates): Ink 3 cells with the
 * `you` face under the shown [selected] segment (moving with Snap over 90 ms), one radio node per
 * segment. Left/Right inside the group move the selection; Enter and Space activate a cell.
 */
@Composable
internal fun WorkshopSegmentStrip(
    frame: ProfileFrame,
    strip: Rect,
    segments: List<WorkshopSegment>,
    selected: WorkshopSegment,
    textScale: Float,
    onAction: (WorkshopAction) -> Unit,
) {
    if (segments.isEmpty() || strip.height <= 0f) return
    val language = LocalAppLanguage.current
    val measurer = rememberKkCanvasMeasurer(textScale)
    val labels = segments.map { it.label(language) }
    val cells = workshopSegmentCells(frame, strip, labels.map { kkSegmentWidth(measurer, it, frame.density) })
        .map { it.translate(-strip.left, -strip.top) }
    val selectedIndex = segments.indexOf(selected).coerceAtLeast(0)
    val position = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex) { position.animateTo(selectedIndex.toFloat(), tween(KkTime.Snap, easing = KkEase.Snap)) }
    val sources = remember(segments) { List(segments.size) { MutableInteractionSource() } }
    val requesters = remember(segments) { List(segments.size) { FocusRequester() } }
    val hovered = sources.map { it.collectIsHoveredAsState().value }
    val focused = sources.map { it.collectIsFocusedAsState().value }
    val paths = remember { KkPathCache(slots = 1 + segments.size) }
    val labelSize = 16f * strip.height / (frame.density * 34f)
    Box(
        Modifier.offset { IntOffset(strip.left.roundToInt(), strip.top.roundToInt()) }
            .size(frame.dp(strip.width), frame.dp(strip.height))
            .drawBehind {
                val cut = frame.d(7f)
                cells.forEachIndexed { index, cell -> drawPath(paths.slab(1 + index, cell, cut), Kk.Ink3) }
                val p = position.value
                val low = floor(p).toInt().coerceIn(0, cells.lastIndex)
                val high = ceil(p).toInt().coerceIn(0, cells.lastIndex)
                val t = p - low
                val face = Rect(lerpF(cells[low].left, cells[high].left, t), 0f, lerpF(cells[low].right, cells[high].right, t), size.height)
                drawPath(paths.slab(0, face, cut), measurer.roles.you)
                val style = measurer.typography.labelStyle(labelSize, trackingEm = 0.07f)
                cells.forEachIndexed { index, cell ->
                    val cover = (1f - abs(p - index)).coerceIn(0f, 1f)
                    val base = if (hovered[index]) Kk.Bone else Kk.Mute
                    val text = measureKkText(measurer, labels[index], style, uppercase = true)
                    drawProfileText(text, cell.center.x, cell.center.y, lerp(base, Kk.Ink, cover), "workshop.segment", segments[index],
                        align = KkAlign.CENTER, valign = KkVAlign.CENTER)
                    if (focused[index]) drawWorkshopFocus(frame, cell)
                }
            }
            .selectableGroup(),
    ) {
        segments.forEachIndexed { index, segment ->
            val cell = cells[index]
            Box(
                Modifier.offset { IntOffset(cell.left.roundToInt(), cell.top.roundToInt()) }
                    .size(frame.dp(cell.width), frame.dp(cell.height))
                    .testTag(segment.tag())
                    .focusRequester(requesters[index])
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (event.key) {
                            Key.DirectionRight, Key.DirectionLeft -> {
                                val forward = event.key == Key.DirectionRight
                                onAction(WorkshopAction.StepSegment(forward))
                                requesters[(index + if (forward) 1 else segments.size - 1) % segments.size].requestFocus()
                                true
                            }
                            Key.Spacebar -> {
                                onAction(WorkshopAction.SelectSegment(segment))
                                true
                            }
                            else -> false
                        }
                    }
                    .semantics { contentDescription = labels[index] }
                    .selectable(selected = segment == selected, interactionSource = sources[index], indication = null,
                        role = Role.RadioButton) { onAction(WorkshopAction.SelectSegment(segment)) },
            )
        }
    }
}
