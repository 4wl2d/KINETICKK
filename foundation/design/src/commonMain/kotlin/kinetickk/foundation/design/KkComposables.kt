// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * A [CanvasTextMeasurer] for composables that draw with the Kk DrawScope helpers: remembered text
 * measurer, bundled typography, [LocalAppLanguage] and [LocalKkRolePalette].
 */
@Composable
fun rememberKkCanvasMeasurer(textScale: Float = 1f): CanvasTextMeasurer {
    val delegate = rememberTextMeasurer(cacheSize = 16)
    val typography = rememberInterfaceTypography()
    val language = LocalAppLanguage.current
    val roles = LocalKkRolePalette.current
    return remember(delegate, typography, language, roles, textScale) {
        CanvasTextMeasurer(delegate, textScale, language, typography, roles)
    }
}

/**
 * Slab button (`.btn`) with full semantics: role Button, [contentDescription] (defaults to
 * [label]), focusable, hover, pointer click and Enter/Space activation. Hover/focus slide the
 * echo out ([KkEase.Pull]); pressing flashes the bone face. [locked] keeps the button focusable
 * but inert (hatch + lock icon; put the reason behind a [KkInfoButton]); [enabled] false removes
 * it from interaction. [armed] is presentation only: the caller owns the two-press logic.
 * Sizes to its label unless [modifier] sets a size; the touch target is expanded by Compose to
 * the platform minimum.
 */
@Composable
fun KkButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: KkButtonVariant = KkButtonVariant.PRIMARY,
    size: KkButtonSize = KkButtonSize.MD,
    enabled: Boolean = true,
    locked: Boolean = false,
    armed: Boolean = false,
    icon: KkIcon? = if (locked) KkIcon.SYSTEM_LOCKED else null,
    contentDescription: String? = null,
    stateDescription: String? = null,
    holdProgress: Float = 0f,
    textScale: Float = 1f,
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val pressed by source.collectIsPressedAsState()
    val interactive = enabled && !locked
    val emphasis by animateFloatAsState(
        if ((hovered || focused) && interactive) 1f else 0f,
        tween(200, easing = KkEase.Pull),
        label = "kkButtonEmphasis",
    )
    val time = if (armed) kkAnimatedTime(0.9f) else 0f
    val measurer = rememberKkCanvasMeasurer(textScale)
    val density = LocalDensity.current.density
    val width = remember(measurer, label, size, icon, density) { kkButtonWidth(measurer, label, size, density, icon) / density }
    Box(
        modifier
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription ?: label
                if (stateDescription != null) this.stateDescription = stateDescription
            }
            .clickable(interactionSource = source, indication = null, enabled = interactive, role = Role.Button, onClick = onClick)
            .then(if (locked && enabled) Modifier.focusable(interactionSource = source) else Modifier)
            .defaultMinSize(minWidth = width.dp, minHeight = size.heightDp.dp)
            .drawBehind {
                drawKkButton(
                    measurer, Rect(Offset.Zero, this.size), label, variant, size, emphasis, focused && enabled,
                    pressed && interactive, enabled, locked, armed, icon, holdProgress, time,
                )
            },
    )
}

/** Seconds of a repeating loop of [periodSeconds] (for armed pulses and similar loops). */
@Composable
private fun kkAnimatedTime(periodSeconds: Float): Float {
    val transition = rememberInfiniteTransition(label = "kkTime")
    val value by transition.animateFloat(
        0f, periodSeconds,
        infiniteRepeatable(tween((periodSeconds * 1000).roundToInt(), easing = LinearEasing), RepeatMode.Restart),
        label = "kkTimeValue",
    )
    return value
}

/**
 * Info (!) button (`.info`): 24 dp, focusable, announces [text] through its content description,
 * shows the tooltip on hover or focus and toggles it on tap/click. The tooltip is a bone slip
 * (270 dp, body 14 px) in a popup placed by [placement], flipped/clamped to the window.
 */
@Composable
fun KkInfoButton(
    text: String,
    modifier: Modifier = Modifier,
    placement: KkTooltipPlacement = KkTooltipPlacement.ABOVE,
    textScale: Float = 1f,
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    var toggled by remember { mutableStateOf(false) }
    // A tap on the (!) while the tooltip is open first dismisses it as an outside click; that
    // same tap must not reopen it.
    var dismissedAt by remember { mutableStateOf<TimeSource.Monotonic.ValueTimeMark?>(null) }
    // Keyboard focus shows the tooltip; focus that comes from a press (tap/click) does not, so a
    // tap can toggle it closed again.
    var pointerFocus by remember { mutableStateOf(false) }
    val open = hovered || toggled || (focused && !pointerFocus)
    val measurer = rememberKkCanvasMeasurer(textScale)
    Box(
        modifier
            .size(24.dp)
            .semantics(mergeDescendants = true) { contentDescription = text }
            .onFocusChanged { if (!it.isFocused) pointerFocus = false }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) pointerFocus = true
                    }
                }
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                val dismissed = dismissedAt
                dismissedAt = null
                if (dismissed == null || dismissed.elapsedNow() > 400.milliseconds) toggled = !toggled
            }
            .drawBehind { drawKkInfoButton(measurer, Rect(Offset.Zero, size), open) },
    ) {
        if (open) {
            KkTooltipPopup(text, measurer, placement, onDismiss = {
                if (toggled) dismissedAt = TimeSource.Monotonic.markNow()
                toggled = false
            })
        }
    }
}

@Composable
private fun KkTooltipPopup(
    text: String,
    measurer: CanvasTextMeasurer,
    placement: KkTooltipPlacement,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current.density
    val body = remember(measurer, text, density) { measureKkTooltip(measurer, text, density) }
    val provider = remember(placement, density) { KkTooltipPositionProvider(placement, density) }
    Popup(popupPositionProvider = provider, onDismissRequest = onDismiss, properties = PopupProperties(focusable = false)) {
        Box(
            Modifier
                .size(270.dp, (body.kkBoxHeight / density + 22f).dp)
                .drawBehind {
                    drawPath(KkPathMemo.chamferTopRight(Rect(Offset.Zero, size), 10.dp.toPx()), Kk.Bone)
                    drawKkText(body, 13.dp.toPx(), 11.dp.toPx(), Kk.Ink)
                },
        )
    }
}

private class KkTooltipPositionProvider(
    private val placement: KkTooltipPlacement,
    private val density: Float,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val anchor = Rect(anchorBounds.left.toFloat(), anchorBounds.top.toFloat(), anchorBounds.right.toFloat(), anchorBounds.bottom.toFloat())
        val window = Rect(0f, 0f, windowSize.width.toFloat(), windowSize.height.toFloat())
        val rect = kkTooltipRect(anchor, popupContentSize.height - 22f * density, density, window, placement,
            popupContentSize.width / density)
        return IntOffset(rect.left.roundToInt(), rect.top.roundToInt())
    }
}

/**
 * Background slab (`.slab` family) for Compose layouts, with an optional [echo] slab (when
 * specified) offset by [echoOffset] (e.g. selected = bone face + `you` echo). Paths are rebuilt only on size change.
 */
fun Modifier.kkSlab(
    color: Color,
    cut: Dp = 14.dp,
    kind: KkSlabKind = KkSlabKind.SLAB,
    echo: Color = Color.Unspecified,
    echoOffset: Dp = 7.dp,
): Modifier = drawWithCache {
    val face = Path().kkSlab(Rect(Offset.Zero, size), cut.toPx(), kind)
    val offset = echoOffset.toPx()
    val echoPath = if (echo.isSpecified) Path().kkSlab(Rect(Offset(offset, offset), size), cut.toPx(), kind) else null
    onDrawBehind {
        if (echoPath != null) drawPath(echoPath, echo)
        drawPath(face, color)
    }
}

/** Hatch fill behind the content (`.hatch`, locked/unavailable), optionally clipped to a slab. */
fun Modifier.kkHatch(color: Color = KkHatchColor, cut: Dp = 0.dp): Modifier = drawWithCache {
    val path = Path().kkSlab(Rect(Offset.Zero, size), cut.toPx())
    onDrawBehind { drawKkHatch(path, color) }
}

/** Halftone dots behind the content (`.halftone`), optionally faded radially (`.fade-rad`). */
fun Modifier.kkHalftone(color: Color, large: Boolean = false, radialFade: Boolean = false): Modifier = drawBehind {
    val rect = Rect(Offset.Zero, size)
    if (radialFade) {
        drawKkRadialFade(rect) { drawKkHalftone(rect, color, large) }
    } else {
        drawKkHalftone(rect, color, large)
    }
}
