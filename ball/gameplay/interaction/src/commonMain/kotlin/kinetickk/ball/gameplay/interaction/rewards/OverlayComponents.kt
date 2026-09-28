// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkButtonSize
import kinetickk.foundation.design.KkButtonVariant
import kinetickk.foundation.design.KkEase
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.KkStampVariant
import kinetickk.foundation.design.KkTagVariant
import kinetickk.foundation.design.KkVAlign
import kinetickk.foundation.design.drawKkButton
import kinetickk.foundation.design.drawKkIcon
import kinetickk.foundation.design.drawKkStamp
import kinetickk.foundation.design.drawKkTag
import kinetickk.foundation.design.drawKkText
import kinetickk.foundation.design.kkButtonWidth
import kinetickk.foundation.design.kkStampSize
import kinetickk.foundation.design.kkTagSize
import kinetickk.foundation.design.measureKkText
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.rememberKkCanvasMeasurer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Compose wrappers around the foundation DrawScope components for the reward and report
// overlays: each keeps its text in semantics so the overlays stay accessible and testable.

/** Places a composable at a px [bounds] rectangle inside a full-size parent. */
@Composable
internal fun Modifier.overlayBounds(bounds: Rect): Modifier {
    val density = LocalDensity.current
    return offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }.requiredSize(
        with(density) { bounds.width.coerceAtLeast(1f).toDp() },
        with(density) { bounds.height.coerceAtLeast(1f).toDp() },
    )
}

/**
 * A board frame scaled into the available space: board coordinates (px on the 1440 x 810,
 * 844 x 390 or 390 x 844 reference) become dp through [scale], centered in the viewport.
 */
internal class OverlayFrame(
    val scale: Float,
    private val left: Float,
    private val top: Float,
    val textScale: Float,
) {
    fun dp(value: Float): Dp = (value * scale).dp
    fun x(value: Float): Dp = (left + value * scale).dp
    fun y(value: Float): Dp = (top + value * scale).dp
    /** Font size for a board size, never below [min] before the text-size setting. */
    fun sp(value: Float, min: Float = 11f): Float = max(value * scale, min) * textScale
    /** Font size of large display type (level numerals, hero figures): the text-size setting does not apply. */
    fun display(value: Float, min: Float = 11f): Float = max(value * scale, min)
}

/**
 * The text-size setting at which overlay text renders at the boards' reference size: the game's
 * default (125 %). UI text follows the setting relative to it; large display type ignores it.
 */
internal const val OverlayReferenceTextScale = 1.25f

/** Factor for UI text at the text-size [setting]: 0.8 at 100 %, 1 at the default, 1.4 at 175 %. */
internal fun overlayUiTextScale(setting: Float): Float = setting / OverlayReferenceTextScale

internal fun overlayFrame(
    widthDp: Float,
    heightDp: Float,
    boardWidth: Float,
    boardHeight: Float,
    textScale: Float,
    minScale: Float = 0.5f,
    maxScale: Float = 1.3f,
): OverlayFrame {
    val scale = min(widthDp / boardWidth, heightDp / boardHeight).coerceIn(minScale, maxScale)
    return OverlayFrame(scale, (widthDp - boardWidth * scale) * 0.5f, (heightDp - boardHeight * scale) * 0.5f, textScale)
}

/** Offsets a composable to px ([x], [y]) inside a full-size parent without constraining its size. */
internal fun Modifier.overlayOffset(x: Float, y: Float): Modifier =
    offset { IntOffset(x.roundToInt(), y.roundToInt()) }

/**
 * Set while a layout only measures its content (a size probe): overlay texts then take the size
 * this measurer gives them, drawing nothing and adding no semantics.
 */
internal val LocalOverlayTextProbe = staticCompositionLocalOf<TextMeasurer?> { null }

/** A text's measured size only (see [LocalOverlayTextProbe]); lays out exactly like [BasicText]. */
@Composable
internal fun OverlayProbeText(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    modifier: Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Layout(modifier) { _, constraints ->
        val result = measurer.measure(text, style, overflow, softWrap = true, maxLines = maxLines, constraints = constraints)
        layout(result.size.width, result.size.height) {}
    }
}

/** Plain text in a Kk role style; [uppercase] applies display casing for the current locale. */
@Composable
internal fun OverlayText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    uppercase: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) {
    val shown = if (uppercase) text.uppercase() else text
    val aligned = if (align != null) style.merge(TextStyle(textAlign = align)) else style
    val overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis
    LocalOverlayTextProbe.current?.let { probe ->
        OverlayProbeText(probe, shown, aligned, modifier, maxLines, overflow)
        return
    }
    BasicText(shown, modifier, style = aligned, maxLines = maxLines, overflow = overflow)
}

/**
 * [style] shrunk so that the longest word of [text] (display-cased when [uppercase]) fits
 * [maxWidthPx]: long single words (Russian compounds) shrink instead of breaking mid-word. With
 * [wholeLine] the whole text must fit one line.
 */
@Composable
internal fun rememberWordFitStyle(
    text: String,
    style: TextStyle,
    maxWidthPx: Float,
    uppercase: Boolean = true,
    wholeLine: Boolean = false,
): TextStyle {
    val measurer = rememberTextMeasurer(cacheSize = 16)
    return remember(text, style, maxWidthPx, uppercase, wholeLine) {
        fitTextStyle(measurer, if (uppercase) text.uppercase() else text, style, maxWidthPx, if (wholeLine) 1 else Int.MAX_VALUE, 0f)
    }
}

/**
 * The largest [style] (down to [minScale] of its size) at which [shown] fits [maxWidthPx] in at
 * most [maxLines] lines without breaking inside a word. Below [minScale] the text keeps the
 * minimum size and may take more lines, but a single word always shrinks to fit the width.
 */
internal fun fitTextStyle(
    measurer: TextMeasurer,
    shown: String,
    style: TextStyle,
    maxWidthPx: Float,
    maxLines: Int,
    minScale: Float,
): TextStyle {
    if (maxWidthPx <= 0f || maxWidthPx.isInfinite() || shown.isEmpty()) return style
    val words = shown.split(' ', '\n').filter(String::isNotEmpty)
    fun styleAt(scale: Float) = if (scale == 1f) style else style.copy(fontSize = style.fontSize * scale)
    fun widest(candidate: TextStyle) = words.maxOfOrNull { word ->
        measurer.measure(word, candidate, softWrap = false, maxLines = 1).size.width.toFloat()
    } ?: 0f
    fun fits(candidate: TextStyle): Boolean {
        if (widest(candidate) > maxWidthPx) return false
        if (maxLines == Int.MAX_VALUE) return true
        val layout = measurer.measure(shown, candidate, softWrap = maxLines > 1, maxLines = maxLines + 1,
            constraints = Constraints(maxWidth = maxWidthPx.toInt().coerceAtLeast(1)))
        // A single unwrapped line reports the constrained width; its overflow flag says whether it fits.
        return layout.lineCount <= maxLines && !layout.didOverflowWidth && layout.size.width <= maxWidthPx
    }
    var scale = 1f
    while (scale >= minScale - 0.001f) {
        val candidate = styleAt(scale)
        if (fits(candidate)) return candidate
        scale -= 0.05f
    }
    val minimum = styleAt(max(minScale, 0.05f))
    val widest = widest(minimum)
    return if (widest <= maxWidthPx) minimum else minimum.copy(fontSize = minimum.fontSize * (maxWidthPx / widest) * 0.97f)
}

/** A text with the width and height (px) its place gives it. */
internal data class OverlayTextBox(val text: String, val width: Float, val height: Float = Float.POSITIVE_INFINITY)

/**
 * One size for texts of one kind (a list's descriptions, a row's names): the largest [base] size
 * (to [minScale] of it) at which every text fits its box in [maxLines] lines without breaking
 * inside a word. Below [minScale] every word still stays whole on its line (a text may take more
 * lines).
 */
internal fun sharedFitStyle(measurer: TextMeasurer, texts: List<OverlayTextBox>, base: TextStyle, maxLines: Int, minScale: Float): TextStyle {
    val shown = texts.filter { it.text.isNotBlank() }
    var scale = 1f
    while (scale >= minScale - 0.001f) {
        val candidate = if (scale == 1f) base else base.copy(fontSize = base.fontSize * scale)
        if (shown.all { box -> fitsWhole(measurer, box.text, candidate, box.width, maxLines, box.height) }) return candidate
        scale -= 0.05f
    }
    return shown.fold(base.copy(fontSize = base.fontSize * minScale)) { style, box ->
        val fitted = fitTextStyle(measurer, box.text, style, box.width, Int.MAX_VALUE, 1f)
        if (fitted.fontSize.value < style.fontSize.value) fitted else style
    }
}

/** [text] fits [width] and [height] in [maxLines] lines and no single word is wider than a line. */
private fun fitsWhole(measurer: TextMeasurer, text: String, style: TextStyle, width: Float, maxLines: Int, height: Float): Boolean {
    val widest = text.split(' ', '\n').filter(String::isNotEmpty).maxOfOrNull { word ->
        measurer.measure(word, style, softWrap = false, maxLines = 1).size.width
    } ?: 0
    if (widest > width) return false
    val layout = measurer.measure(text, style, softWrap = true, maxLines = maxLines + 1,
        constraints = Constraints(maxWidth = width.toInt().coerceAtLeast(1)))
    return layout.lineCount <= maxLines && layout.size.height <= height + 0.5f
}

/**
 * Text that never breaks inside a word and never cuts off: the font shrinks (to [minScale] of
 * its size) until the text fits the width in [maxLines]; below that it keeps the minimum size
 * and wraps on word boundaries.
 */
@Composable
internal fun OverlayFitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    uppercase: Boolean = false,
    maxLines: Int = 2,
    minScale: Float = 0.7f,
    align: TextAlign? = null,
) {
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val shown = if (uppercase) text.uppercase() else text
    val probe = LocalOverlayTextProbe.current
    BoxWithConstraints(modifier) {
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else Float.POSITIVE_INFINITY
        val fitted = remember(shown, style, width, maxLines, minScale) { fitTextStyle(measurer, shown, style, width, maxLines, minScale) }
        val aligned = if (align != null) fitted.merge(TextStyle(textAlign = align)) else fitted
        if (probe != null) OverlayProbeText(probe, shown, aligned, Modifier) else BasicText(shown, style = aligned)
    }
}

/** Resolves a reward tone to its color; [fixed] is used for [RewardTone.FIXED]. */
internal fun RewardTone.color(roles: KkRolePalette, fixed: Color): Color = when (this) {
    RewardTone.FIXED -> fixed
    RewardTone.YOU -> roles.you
    RewardTone.THREAT -> roles.threat
    RewardTone.MUTE -> Kk.Mute
}

/** Tag plate (`.tag`) sized to its label. [leading] draws a small colored mark before the text. */
@Composable
internal fun OverlayTag(
    text: String,
    modifier: Modifier = Modifier,
    variant: KkTagVariant = KkTagVariant.DEFAULT,
    background: Color = Color.Unspecified,
    foreground: Color = Color.Unspecified,
    heightDp: Float = 22f,
    fontSize: Float = 13f,
    textScale: Float = 1f,
) {
    val measurer = rememberKkCanvasMeasurer(textScale)
    val density = LocalDensity.current.density
    val size = remember(measurer, text, density, heightDp, fontSize) { kkTagSize(measurer, text, density, heightDp, fontSize) }
    Box(
        modifier
            .size((size.width / density).dp, (size.height / density).dp)
            .semantics { this.text = AnnotatedString(text) }
            .drawBehind {
                drawKkTag(measurer, text, Offset.Zero, variant, heightDp, fontSize, background, foreground)
            },
    )
}

/** Stamp (`.stamp`) sized to its label, rotated −6°, slammed in by [slam] (1 = settled). */
@Composable
internal fun OverlayStamp(
    text: String,
    modifier: Modifier = Modifier,
    variant: KkStampVariant = KkStampVariant.YOU,
    color: Color = Color.Unspecified,
    fontSize: Float = 17f,
    textScale: Float = 1f,
    slam: () -> Float = { 1f },
) {
    val measurer = rememberKkCanvasMeasurer(textScale)
    val density = LocalDensity.current.density
    val size = remember(measurer, text, density, fontSize) { kkStampSize(measurer, text, density, fontSize) }
    Box(
        modifier
            .size((size.width / density).dp + 4.dp, (size.height / density).dp + 4.dp)
            .semantics { this.text = AnnotatedString(text) }
            .drawBehind { drawKkStamp(measurer, text, Offset.Zero, variant, fontSize, slam = slam(), color = color) },
    )
}

/**
 * Slab button (`.btn`) filling its modifier bounds, with an optional leading [icon] and a
 * trailing bone count tag ([count], e.g. rerolls left). Hover/focus slide the echo (Pull),
 * pressing flashes bone; role Button, focusable, Enter/Space activate.
 */
@Composable
internal fun OverlayButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: KkButtonVariant = KkButtonVariant.PRIMARY,
    size: KkButtonSize = KkButtonSize.SM,
    enabled: Boolean = true,
    icon: KkIcon? = null,
    count: String? = null,
    textScale: Float = 1f,
    contentDescription: String? = null,
    stateDescription: String? = null,
    fontSize: Float = size.fontSp,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val pressed by source.collectIsPressedAsState()
    val emphasis by animateFloatAsState(if ((hovered || focused) && enabled) 1f else 0f, tween(200, easing = KkEase.Pull), label = "overlayButton")
    val measurer = rememberKkCanvasMeasurer(textScale)
    Box(
        modifier
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription ?: label
                if (stateDescription != null) this.stateDescription = stateDescription
            }
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .drawBehind {
                val bounds = Rect(Offset.Zero, this.size)
                drawKkButton(measurer, bounds, "", variant, size, emphasis, focused && enabled, pressed && enabled, enabled, icon = null)
                drawOverlayButtonContent(measurer, bounds, label, variant, size, fontSize, emphasis, pressed && enabled, enabled, icon, count)
            },
    )
}

private fun DrawScope.drawOverlayButtonContent(
    measurer: CanvasTextMeasurer,
    bounds: Rect,
    label: String,
    variant: KkButtonVariant,
    size: KkButtonSize,
    fontSize: Float,
    emphasis: Float,
    pressed: Boolean,
    enabled: Boolean,
    icon: KkIcon?,
    count: String?,
) {
    val fg = when {
        !enabled -> Kk.Mute2
        pressed -> Kk.Ink
        variant == KkButtonVariant.GHOST && emphasis > 0.5f -> Kk.Ink
        variant == KkButtonVariant.GHOST -> Kk.Bone
        else -> Kk.Ink
    }
    val layout = overlayButtonLabel(measurer, bounds.width, label, size, fontSize, icon, count, density)
    OverlayButtonProbe.records?.add(layout to overlayButtonLabelRoom(measurer, bounds.width, size, icon, count, density))
    val iconSize = size.iconDp * density
    val gap = 10f * density
    val countSize = count?.let { kkTagSize(measurer, it, density, 20f, 12f) }
    val countWidth = countSize?.width ?: 0f
    val contentWidth = (if (icon != null) iconSize + gap else 0f) + layout.size.width + (if (countSize != null) gap + countWidth else 0f)
    val shiftX = 5f * density * (if (enabled && !pressed) emphasis else 0f) + if (pressed) 3f * density else 0f
    val shiftY = if (pressed) 3f * density else 0f
    var x = bounds.center.x - contentWidth * 0.5f + shiftX
    val cy = bounds.center.y + shiftY
    if (icon != null) {
        drawKkIcon(icon, Offset(x + iconSize * 0.5f, cy), iconSize, fg)
        x += iconSize + gap
    }
    drawKkText(layout, x, cy, fg, valign = KkVAlign.CENTER)
    x += layout.size.width
    if (count != null && countSize != null) {
        x += gap
        drawKkTag(measurer, count, Offset(x, cy - countSize.height * 0.5f), KkTagVariant.BONE, heightDp = 20f, fontSize = 12f,
            background = if (enabled) Kk.Bone else Kk.Mute2)
    }
}

/**
 * The laid-out label of an [OverlayButton] [width] px wide: long localized labels shrink (in 5 %
 * steps to 70 %, then exactly to the room) and are never cut.
 */
internal fun overlayButtonLabel(
    measurer: CanvasTextMeasurer,
    width: Float,
    label: String,
    size: KkButtonSize,
    fontSize: Float,
    icon: KkIcon?,
    count: String?,
    density: Float,
): TextLayoutResult {
    val room = overlayButtonLabelRoom(measurer, width, size, icon, count, density)
    var fittedSize = fontSize
    var layout = measureKkText(measurer, label, measurer.typography.condStyle(fittedSize, trackingEm = 0.02f, lineHeightEm = 1f), uppercase = true)
    while (layout.size.width > room && fittedSize > fontSize * 0.7f) {
        fittedSize = (fittedSize - fontSize * 0.05f)
        layout = measureKkText(measurer, label, measurer.typography.condStyle(fittedSize, trackingEm = 0.02f, lineHeightEm = 1f), uppercase = true)
    }
    if (layout.size.width > room) {
        fittedSize = (fittedSize * room / layout.size.width * 0.98f * 10f).toInt() / 10f
        layout = measureKkText(measurer, label, measurer.typography.condStyle(fittedSize, trackingEm = 0.02f, lineHeightEm = 1f), uppercase = true)
    }
    return layout
}

/** Test probe: while [records] is set, every drawn button label is recorded with its room (px). */
internal object OverlayButtonProbe {
    var records: MutableList<Pair<TextLayoutResult, Float>>? = null
}

/** Width in px an [OverlayButton] label may take beside its padding, icon and count tag. */
internal fun overlayButtonLabelRoom(measurer: CanvasTextMeasurer, width: Float, size: KkButtonSize, icon: KkIcon?, count: String?, density: Float): Float =
    max(1f, width - size.paddingDp * density - (if (icon != null) size.iconDp * density + 10f * density else 0f) -
        (count?.let { kkTagSize(measurer, it, density, 20f, 12f).width + 10f * density } ?: 0f))

/** Natural width in px of an [OverlayButton] with [label] (for layouts that size to content). */
internal fun overlayButtonWidth(measurer: CanvasTextMeasurer, label: String, size: KkButtonSize, density: Float, icon: KkIcon?): Float =
    kkButtonWidth(measurer, label, size, density, icon)
