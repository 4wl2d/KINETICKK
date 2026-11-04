// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kinetickk.ball.profile.interaction.localization.ProfileScreensRedesignText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.formatCompactNumber
import kinetickk.foundation.common.localization.formatGroupedNumber
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.roundToInt

/**
 * The panel's own focus target. A press on a control that can disable itself (a purchase, the
 * last page step) returns keyboard routing to the panel instead of leaving focus on a node that
 * stops being focusable, so Escape and other shortcuts keep reaching the Session.
 */
internal val LocalProfilePanelFocus = staticCompositionLocalOf<FocusRequester?> { null }

/** Home/back slot of the shared header (the only header target pointer resolvers map). */
internal fun profileHeaderBackRect(frame: ProfileFrame, backWidth: Float): Rect {
    val height = frame.d(if (frame.regular) 38f else 30f)
    val centerY = if (frame.portrait) frame.d(30f) else frame.headerHeight * 0.5f
    return Rect(frame.left, centerY - height * 0.5f, frame.left + backWidth, centerY + height * 0.5f)
}

/** Natural width in px of the header's back button for [label]. */
internal fun profileHeaderBackWidth(measurer: CanvasTextMeasurer, frame: ProfileFrame, label: String): Float =
    profileButtonNaturalWidth(measurer, frame, label, if (frame.regular) KkButtonSize.SM else KkButtonSize.XS)

/**
 * Shared screen scaffold (SPEC 7.12): full-bleed [background], the header (Back ghost button,
 * cond 44 title, count from data, (!) info, matter chip) and the screen [content]. Actions and
 * profile decisions remain with each feature; the panel only lays out and routes Back.
 */
@Composable
internal fun ProfilePanel(
    title: String,
    count: String?,
    info: String?,
    matter: Long,
    scale: Float,
    tag: String,
    onBack: () -> Unit,
    contentKey: Any = Unit,
    background: DrawScope.(ProfileFrame) -> Unit,
    headerExtra: (@Composable RowScope.(ProfileFrame) -> Unit)? = null,
    headerExtraWidth: (ProfileFrame) -> Float = { 0f },
    content: @Composable BoxScope.(ProfileFrame) -> Unit,
) {
    val panelFocus = remember { FocusRequester() }
    LaunchedEffect(contentKey) { panelFocus.requestFocus() }
    val focusManager = LocalFocusManager.current
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .testTag(tag)
            .focusRequester(panelFocus)
            .onKeyEvent { event ->
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
        CompositionLocalProvider(LocalProfilePanelFocus provides panelFocus) {
            Box(Modifier.fillMaxSize().drawBehind { background(frame) })
            content(frame)
            ProfileHeader(frame, title, count, info, matter, scale, tag, onBack, headerExtra, headerExtraWidth(frame))
        }
    }
}

@Composable
private fun ProfileHeader(
    frame: ProfileFrame,
    title: String,
    count: String?,
    info: String?,
    matter: Long,
    scale: Float,
    tag: String,
    onBack: () -> Unit,
    headerExtra: (@Composable RowScope.(ProfileFrame) -> Unit)?,
    headerExtraWidth: Float,
) {
    val language = LocalAppLanguage.current
    val typography = rememberInterfaceTypography()
    val measurer = rememberKkCanvasMeasurer(scale)
    val backLabel = language.text(ProfileScreensRedesignText.Back)
    val backWidth = profileHeaderBackWidth(measurer, frame, backLabel)
    val back = profileHeaderBackRect(frame, backWidth)
    val gap = frame.d(if (frame.regular) 18f else 12f)
    val titleSize = when (frame.mode) {
        ProfileLayoutMode.REGULAR -> 44f
        ProfileLayoutMode.COMPACT_LANDSCAPE -> 30f
        ProfileLayoutMode.COMPACT_PORTRAIT -> 32f
    } * frame.k
    val countSize = (if (frame.regular) 18f else 14f) * frame.k
    val matterLabel = language.text(ProfileScreensRedesignText.Matter)
    val matterText = formatMatter(matter, language)
    val chipScale = frame.k * if (frame.regular) 1f else 0.9f
    val chipWidth = kkChipWidth(measurer, matterText, matterLabel, density = frame.density) * chipScale
    val chipHeight = frame.density * 34f * chipScale
    val px = frame.density
    fun Float.toDp() = (this / px).dp

    ProfileSlabButton(
        label = backLabel, onClick = onBack, frame = frame, tag = "$tag-back",
        variant = KkButtonVariant.GHOST, size = if (frame.regular) KkButtonSize.SM else KkButtonSize.XS,
        textScale = scale,
        modifier = Modifier.offset { IntOffset(back.left.roundToInt(), back.top.roundToInt()) }
            .size(back.width.toDp(), back.height.toDp()),
    )
    val chipCenterY = back.center.y
    Box(
        Modifier.offset { IntOffset((frame.right - chipWidth).roundToInt(), (chipCenterY - chipHeight * 0.5f).roundToInt()) }
            .size(chipWidth.toDp(), chipHeight.toDp())
            .semantics { contentDescription = "$matterText $matterLabel" }
            .drawBehind {
                scale(chipScale, chipScale, Offset.Zero) {
                    drawProfileChip(measurer, matterText, matterLabel)
                }
            },
    )
    val titleRowTop = if (frame.portrait) frame.d(56f) else 0f
    val titleRowLeft = if (frame.portrait) frame.left else back.right + gap
    val titleRowHeight = if (frame.portrait) frame.d(48f) else frame.headerHeight
    val titleRowRight = if (frame.portrait) frame.right else frame.right - chipWidth - gap
    // The title shrinks to fit its row (never truncated): the rule, count, (!) and any extra
    // controls sharing the row keep their natural widths.
    val countWidth = if (count == null) 0f else gap + measureKkText(measurer, count, measurer.typography.wideStyle(countSize, tabular = true,
        lineHeightEm = 1f)).size.width
    val infoWidth = if (info == null) 0f else gap + px * 24f
    val ruleWidth = if (frame.portrait) 0f else px + gap
    // Phones put extra controls on the Back row when they fit there, else in the title row.
    val backRowRoom = frame.right - chipWidth - gap - back.right - gap
    val extraOnBackRow = frame.portrait && headerExtra != null && headerExtraWidth <= backRowRoom
    val extraWidth = if (extraOnBackRow || headerExtra == null) 0f else headerExtraWidth
    val titleRoom = titleRowRight - titleRowLeft - ruleWidth - countWidth - infoWidth - extraWidth - px * 2f
    val titleFactor = fitKkFactor(measurer, title, titleSize, titleRoom, minFactor = 0.4f) {
        measurer.typography.condStyle(it, lineHeightEm = 1f)
    }
    if (extraOnBackRow) {
        // Phones keep the title row for the title when the Back row has room for the extras.
        Row(
            Modifier.offset { IntOffset(back.right.roundToInt(), 0) }
                .size((frame.right - chipWidth - gap - back.right).coerceAtLeast(0f).toDp(), frame.d(56f).toDp()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
        ) { headerExtra(frame) }
    }
    Row(
        Modifier.offset { IntOffset(titleRowLeft.roundToInt(), titleRowTop.roundToInt()) }
            .size((titleRowRight - titleRowLeft).coerceAtLeast(0f).toDp(), titleRowHeight.toDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!frame.portrait) {
            Box(Modifier.width(1.dp).height(frame.d(28f).toDp()).drawBehind { drawRect(Kk.Line) })
            Spacer(Modifier.width(gap.toDp()))
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                title.uppercase(),
                style = typography.condStyle(titleSize * scale * titleFactor, color = Kk.Bone, lineHeightEm = 1f),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                // The width fits the row; the line box may be taller than the phone title row, so
                // the height is not bounded (a bounded one would clip Cyrillic descenders).
                modifier = Modifier.weight(1f, fill = false).wrapContentHeight(unbounded = true),
                onTextLayout = { ProfileTextProbe.record("header.title", null, it, Rect(Offset.Zero, it.size.toSize())) },
            )
            if (count != null) {
                Spacer(Modifier.width(gap.toDp()))
                BasicText(count, style = typography.wideStyle(countSize * scale, tabular = true, color = Kk.Mute, lineHeightEm = 1f), maxLines = 1,
                    onTextLayout = { ProfileTextProbe.record("header.count", null, it, Rect(Offset.Zero, it.size.toSize())) })
            }
            if (info != null) {
                Spacer(Modifier.width(gap.toDp()))
                KkInfoButton(info, Modifier.testTag("$tag-info"), placement = KkTooltipPlacement.BELOW, textScale = scale)
            }
        }
        if (headerExtra != null && !extraOnBackRow) headerExtra(frame)
    }
}

/** Matter with digit grouping (1,284); large balances switch to the compact form. */
internal fun formatMatter(value: Long, language: AppLanguage): String {
    val safe = value.coerceAtLeast(0L)
    return if (safe >= 100_000L) formatCompactNumber(safe, language) else formatGroupedNumber(safe, language)
}

private fun chipValueStyle(measurer: CanvasTextMeasurer) =
    measurer.typography.condStyle(21f, trackingEm = 0.02f, tabular = true, lineHeightEm = 1f)

private fun chipLabelStyle(measurer: CanvasTextMeasurer) = measurer.typography.monoStyle(9f, trackingEm = 0.06f)

/** Width in px (unscaled dp) of [drawProfileChip] for these texts. */
internal fun kkChipWidth(measurer: CanvasTextMeasurer, value: String, label: String, density: Float): Float {
    val valueWidth = measureKkText(measurer, value, chipValueStyle(measurer), uppercase = true).size.width
    val labelWidth = measureKkText(measurer, label, chipLabelStyle(measurer), uppercase = true).size.width
    return density * (10f + 14f + 8f + 8f + 14f) + valueWidth + labelWidth
}

/** Header chip (`.chip`); the gem takes `you`, which the Rebirth theme maps to its accent. */
internal fun DrawScope.drawProfileChip(measurer: CanvasTextMeasurer, value: String, label: String) {
    drawKkChip(measurer, Offset.Zero, value, label)
}

/** The boards' `.bg-grid`: 48 px bone @4.5 % lines. */
internal fun DrawScope.drawProfileGrid(frame: ProfileFrame) {
    drawKkGrid(Rect(0f, 0f, size.width, size.height), Kk.Bone.copy(alpha = 0.045f), spacingDp = 48f * frame.k,
        origin = Offset(frame.originX, 0f))
}

/**
 * The boards' right-hand ink-1 panel skewed −12° with its bottom-left corner at [bottomLeftX]
 * (`transform-origin: 0 100%`) and a 1 px line on its leading edge.
 */
internal fun DrawScope.drawProfileSidePanel(bottomLeftX: Float, color: Color = Kk.Ink1) {
    val lean = size.height * KkShape.ShearRatio
    val path = SidePanelPaths.slab(0, bottomLeftX, 0f, size.width + lean + density * 10f, size.height, lean, KkSlabKind.LEFT)
    drawPath(path, color)
    drawLine(Kk.Line, Offset(bottomLeftX + lean - density, 0f), Offset(bottomLeftX - density, size.height), density)
}

/** Draw-thread memo for the side panel slab (rebuilt only when the window size changes). */
private val SidePanelPaths = KkPathCache(slots = 1)
