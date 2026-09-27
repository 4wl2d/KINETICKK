// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kinetickk.ball.profile.interaction.localization.ProfileScreensRedesignText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Layout modes shared by the Armory, Lab and Rebirth screens (same thresholds as Home). */
internal enum class ProfileLayoutMode {
    REGULAR,
    COMPACT_LANDSCAPE,
    COMPACT_PORTRAIT,
}

/**
 * Screen frame in px. Board numbers are design px at the mode's reference frame (1440×810,
 * 844×390 or 390×844) and map to px through [d]; [x] maps a board x of the regular 1440 board
 * (the regular composition is centered when the window is wider than the scaled board).
 */
internal class ProfileFrame(
    val width: Float,
    val height: Float,
    val density: Float,
    val mode: ProfileLayoutMode,
    val k: Float,
    val originX: Float,
    val left: Float,
    val right: Float,
    val headerHeight: Float,
) {
    val unit: Float get() = density * k

    fun d(value: Float): Float = value * density * k

    fun x(value: Float): Float = originX + d(value)

    val regular: Boolean get() = mode == ProfileLayoutMode.REGULAR
    val portrait: Boolean get() = mode == ProfileLayoutMode.COMPACT_PORTRAIT
}

internal fun profileLayoutMode(width: Float, height: Float, density: Float): ProfileLayoutMode {
    val scale = density.coerceAtLeast(0.5f)
    val logicalWidth = width / scale
    val logicalHeight = height / scale
    return when {
        logicalWidth >= 900f && logicalHeight >= 560f -> ProfileLayoutMode.REGULAR
        logicalWidth <= logicalHeight -> ProfileLayoutMode.COMPACT_PORTRAIT
        else -> ProfileLayoutMode.COMPACT_LANDSCAPE
    }
}

internal fun profileFrame(width: Float, height: Float, density: Float): ProfileFrame {
    val scale = density.coerceAtLeast(0.5f)
    val logicalWidth = width / scale
    val logicalHeight = height / scale
    return when (profileLayoutMode(width, height, density)) {
        ProfileLayoutMode.REGULAR -> {
            val k = min(logicalWidth / 1440f, logicalHeight / 810f).coerceIn(0.6f, 1.5f)
            val originX = max(0f, (width - 1440f * k * scale) * 0.5f)
            ProfileFrame(width, height, scale, ProfileLayoutMode.REGULAR, k, originX,
                left = originX + 56f * k * scale, right = min(width - 24f * k * scale, originX + 1392f * k * scale),
                headerHeight = 84f * k * scale)
        }
        ProfileLayoutMode.COMPACT_LANDSCAPE -> {
            val k = min(logicalWidth / 844f, logicalHeight / 390f).coerceIn(0.75f, 1.3f)
            ProfileFrame(width, height, scale, ProfileLayoutMode.COMPACT_LANDSCAPE, k, 0f,
                left = 20f * k * scale, right = width - 20f * k * scale, headerHeight = 56f * k * scale)
        }
        ProfileLayoutMode.COMPACT_PORTRAIT -> {
            val k = min(logicalWidth / 390f, logicalHeight / 844f).coerceIn(0.75f, 1.3f)
            ProfileFrame(width, height, scale, ProfileLayoutMode.COMPACT_PORTRAIT, k, 0f,
                left = 16f * k * scale, right = width - 16f * k * scale, headerHeight = 108f * k * scale)
        }
    }
}

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
            ProfileHeader(frame, title, count, info, matter, scale, tag, onBack, headerExtra)
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
                style = typography.condStyle(titleSize * scale, color = Kk.Bone, lineHeightEm = 1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (count != null) {
                Spacer(Modifier.width(gap.toDp()))
                BasicText(count, style = typography.wideStyle(countSize * scale, tabular = true, color = Kk.Mute, lineHeightEm = 1f), maxLines = 1)
            }
            if (info != null) {
                Spacer(Modifier.width(gap.toDp()))
                KkInfoButton(info, Modifier.testTag("$tag-info"), placement = KkTooltipPlacement.BELOW, textScale = scale)
            }
        }
        if (headerExtra != null) headerExtra(frame)
    }
}

/** Matter with digit grouping (1,284); large balances switch to the compact form. */
internal fun formatMatter(value: Long, language: AppLanguage): String {
    val safe = value.coerceAtLeast(0L)
    if (safe >= 100_000L) return formatCompact(safe, language)
    val separator = if (language == AppLanguage.Russian) "\u202F" else ","
    return safe.toString().reversed().chunked(3).joinToString(separator).reversed()
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

/** Natural width in px of a [ProfileSlabButton] with [label] and an optional gem [cost]. */
internal fun profileButtonNaturalWidth(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    label: String,
    size: KkButtonSize,
    fontSize: Float = size.fontSp,
    cost: String? = null,
    icon: Boolean = false,
): Float {
    val density = frame.density
    val labelWidth = measureKkText(measurer, label, profileButtonStyle(measurer, fontSize), uppercase = true).size.width
    val costWidth = if (cost == null) 0f else {
        density * 12f + density * fontSize * 0.36f + density * 6f +
            measureKkText(measurer, cost, profileButtonStyle(measurer, fontSize * 0.76f, tabular = true), uppercase = true).size.width
    }
    val iconWidth = if (icon) density * (size.iconDp + 12f) else 0f
    return (size.paddingDp * density * 2f + labelWidth + costWidth + iconWidth) * frame.k
}

internal fun profileButtonStyle(measurer: CanvasTextMeasurer, fontSize: Float, tabular: Boolean = false): TextStyle =
    measurer.typography.condStyle(fontSize, trackingEm = 0.02f, tabular = tabular, lineHeightEm = 1f)

/**
 * Slab button (`.btn`) drawn at the frame scale, with an optional gem + [cost] after the label
 * ([spread] pushes the cost to the right edge, `justify-content: space-between`). Semantics match
 * [KkButton]: role Button, focusable, Enter/Space and pointer activation; [locked] shows the
 * hatched lock face and, like a disabled button, does not act.
 */
@Composable
internal fun ProfileSlabButton(
    label: String,
    onClick: () -> Unit,
    frame: ProfileFrame,
    tag: String,
    modifier: Modifier = Modifier,
    variant: KkButtonVariant = KkButtonVariant.PRIMARY,
    size: KkButtonSize = KkButtonSize.MD,
    fontSize: Float = size.fontSp,
    enabled: Boolean = true,
    locked: Boolean = false,
    lockIcon: Boolean = locked,
    armed: Boolean = false,
    cost: String? = null,
    costScale: Float = 0.76f,
    spread: Boolean = false,
    textScale: Float = 1f,
    contentDescription: String = if (cost == null) label else "$label $cost",
    stateDescription: String? = null,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val pressed by source.collectIsPressedAsState()
    val interactive = enabled && !locked
    val emphasis by animateFloatAsState(
        if ((hovered || focused) && interactive) 1f else 0f,
        tween(200, easing = KkEase.Pull),
        label = "profileButtonEmphasis",
    )
    val time = if (armed && interactive) profileLoopTime(0.9f) else 0f
    val measurer = rememberKkCanvasMeasurer(textScale)
    val panelFocus = LocalProfilePanelFocus.current
    val k = frame.k
    Box(
        modifier
            .testTag(tag)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                if (stateDescription != null) this.stateDescription = stateDescription
            }
            .clickable(interactionSource = source, indication = null, enabled = interactive, role = Role.Button) {
                panelFocus?.requestFocus()
                onClick()
            }
            .then(if (locked && enabled) Modifier.focusable(interactionSource = source) else Modifier)
            .drawBehind {
                scale(k, k, Offset.Zero) {
                    val bounds = Rect(0f, 0f, this.size.width / k, this.size.height / k)
                    drawKkButton(measurer, bounds, "", variant, size, emphasis, focused && enabled, pressed && interactive,
                        enabled, locked, armed, icon = null, time = time)
                    val shiftX = d(5f) * (if (interactive && !pressed) emphasis.coerceIn(0f, 1.2f) else 0f) +
                        if (pressed && interactive) d(3f) else 0f
                    val shiftY = if (pressed && interactive) d(3f) else 0f
                    val fg = profileButtonForeground(variant, emphasis, enabled, locked, armed, pressed && interactive)
                    translate(shiftX, shiftY) {
                        drawProfileButtonContent(measurer, bounds, label, size, fontSize, cost, costScale, spread, locked && lockIcon, fg)
                    }
                }
            },
    )
}

private fun profileButtonForeground(
    variant: KkButtonVariant,
    emphasis: Float,
    enabled: Boolean,
    locked: Boolean,
    armed: Boolean,
    pressed: Boolean,
): Color = when {
    !enabled && !locked -> Kk.Mute2
    locked -> Kk.Mute
    armed || pressed -> Kk.Ink
    variant == KkButtonVariant.GHOST -> if (emphasis > 0.5f) Kk.Ink else Kk.Bone
    else -> Kk.Ink
}

private fun DrawScope.drawProfileButtonContent(
    measurer: CanvasTextMeasurer,
    bounds: Rect,
    label: String,
    size: KkButtonSize,
    fontSize: Float,
    cost: String?,
    costScale: Float,
    spread: Boolean,
    locked: Boolean,
    color: Color,
) {
    val costLayout = cost?.let { measureKkText(measurer, it, profileButtonStyle(measurer, fontSize * costScale, tabular = true), uppercase = true) }
    val costRoom = if (costLayout == null) 0f else d(12f) + d(fontSize * 0.36f) + d(6f) + costLayout.size.width
    val labelRoom = (bounds.width - d(size.paddingDp) * 2f - costRoom - if (locked) d(size.iconDp + 12f) else 0f).coerceAtLeast(d(10f))
    val labelLayout = fitKkText(measurer, label, fontSize, labelRoom, minFactor = 0.6f) { profileButtonStyle(measurer, it) }
    val gemSize = fontSize * 0.36f * (costScale / 0.76f).coerceAtMost(1f)
    val costWidth = if (costLayout == null) 0f else d(gemSize) + d(6f) + costLayout.size.width
    val iconSize = if (locked) d(size.iconDp) else 0f
    val iconGap = if (locked) d(12f) else 0f
    val cy = bounds.center.y
    if (spread && costLayout != null) {
        var x = bounds.left + d(size.paddingDp)
        drawKkText(labelLayout, x, cy, color, valign = KkVAlign.CENTER)
        x = bounds.right - d(size.paddingDp) - costWidth
        drawKkGem(Offset(x + d(gemSize) * 0.5f, cy), color, gemSize)
        drawKkText(costLayout, x + d(gemSize) + d(6f), cy, color, valign = KkVAlign.CENTER)
        return
    }
    val total = iconSize + iconGap + labelLayout.size.width + (if (costLayout != null) d(12f) + costWidth else 0f)
    var x = bounds.center.x - total * 0.5f
    if (locked) {
        drawKkIcon(KkIcon.SYSTEM_LOCKED, Offset(x + iconSize * 0.5f, cy), iconSize, color)
        x += iconSize + iconGap
    }
    drawKkText(labelLayout, x, cy, color, valign = KkVAlign.CENTER)
    x += labelLayout.size.width
    if (costLayout != null) {
        x += d(12f)
        drawKkGem(Offset(x + d(gemSize) * 0.5f, cy), color, gemSize)
        drawKkText(costLayout, x + d(gemSize) + d(6f), cy, color, valign = KkVAlign.CENTER)
    }
}

/** Seconds of a repeating loop (armed pulses, spinning rings). */
@Composable
internal fun profileLoopTime(periodSeconds: Float): Float {
    val transition = rememberInfiniteTransition(label = "profileLoop")
    val value by transition.animateFloat(
        0f, periodSeconds,
        infiniteRepeatable(tween((periodSeconds * 1000f).roundToInt(), easing = LinearEasing), RepeatMode.Restart),
        label = "profileLoopValue",
    )
    return value
}

/**
 * Measures [text] at [size] (sp, before the text-size setting) and shrinks it (down to
 * [minFactor]) until it fits [maxWidth] in [maxLines]; the last resort ellipsizes.
 */
internal fun fitKkText(
    measurer: CanvasTextMeasurer,
    text: String,
    size: Float,
    maxWidth: Float,
    maxLines: Int = 1,
    uppercase: Boolean = true,
    minFactor: Float = 0.62f,
    style: (Float) -> TextStyle,
): TextLayoutResult {
    var factor = 1f
    while (true) {
        val layout = measureKkText(measurer, text, style(size * factor), uppercase, maxWidth.coerceAtLeast(1f), maxLines)
        if ((!layout.hasVisualOverflow && !layout.breaksWord()) || factor <= minFactor) return layout
        factor = max(minFactor, factor - 0.08f)
    }
}

/** True when a soft line break falls inside a word (the word is wider than the line). */
private fun TextLayoutResult.breaksWord(): Boolean {
    if (lineCount < 2) return false
    val text = layoutInput.text
    for (line in 0 until lineCount - 1) {
        val end = getLineEnd(line)
        if (end <= 0 || end >= text.length) continue
        if (!text[end - 1].isWhitespace() && !text[end].isWhitespace() && text[end - 1] != '-') return true
    }
    return false
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

/** Converts px to dp for Compose sizes. */
internal fun ProfileFrame.dp(px: Float) = (px / density).dp
