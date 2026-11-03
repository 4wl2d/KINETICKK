// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import kinetickk.foundation.design.*
import kotlin.math.roundToInt

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
    /** A press on an enabled but [locked] button (one that explains why it is locked); null keeps it inert. */
    onLockedClick: (() -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    var focusedValue by remember { mutableStateOf(false) }
    val pressed by source.collectIsPressedAsState()
    // A click keeps keyboard routing without pinning the pointer highlight after it leaves.
    var pointerFocusValue by remember { mutableStateOf(false) }
    val keyboardFocused = focusedValue && !pointerFocusValue
    val interactive = enabled && !locked
    val clickableEnabled = interactive || (locked && enabled && onLockedClick != null)
    val emphasis = if ((hovered || keyboardFocused) && interactive) 1f else 0f
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
            .onFocusChanged {
                focusedValue = it.isFocused
                if (!it.isFocused) pointerFocusValue = false
            }
            .onPreviewKeyEvent { pointerFocusValue = false; false }
            .pointerInput(clickableEnabled) {
                if (!clickableEnabled) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) {
                            pointerFocusValue = true
                            awaitPointerEvent(PointerEventPass.Final)
                            if (!focusedValue) pointerFocusValue = false
                        }
                    }
                }
            }
            .clickable(interactionSource = source, indication = null, enabled = clickableEnabled,
                role = Role.Button) {
                panelFocus?.requestFocus()
                if (interactive) onClick() else onLockedClick?.invoke()
            }
            .then(if (locked && enabled && onLockedClick == null) Modifier.focusable(interactionSource = source) else Modifier)
            .drawBehind {
                scale(k, k, Offset.Zero) {
                    val bounds = Rect(0f, 0f, this.size.width / k, this.size.height / k)
                    drawKkButton(measurer, bounds, "", variant, size, emphasis, keyboardFocused && enabled, pressed && interactive,
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
    // Label and cost shrink together until both fit the face (large text never truncates).
    val room = (bounds.width - d(size.paddingDp) * 2f - if (locked) d(size.iconDp + 12f) else 0f).coerceAtLeast(d(10f))
    val factor = profileButtonContentFactor(measurer, label, cost, fontSize, costScale, room, density)
    val font = fontSize * factor
    val costLayout = cost?.let { measureKkText(measurer, it, profileButtonStyle(measurer, font * costScale, tabular = true), uppercase = true) }
    val costRoom = if (costLayout == null) 0f else d(12f) + d(font * 0.36f) + d(6f) + costLayout.size.width
    val labelLayout = fitKkText(measurer, label, font, (room - costRoom).coerceAtLeast(d(10f)), minFactor = 0.8f) {
        profileButtonStyle(measurer, it)
    }
    val gemSize = font * 0.36f * (costScale / 0.76f).coerceAtMost(1f)
    val costWidth = if (costLayout == null) 0f else d(gemSize) + d(6f) + costLayout.size.width
    val iconSize = if (locked) d(size.iconDp) else 0f
    val iconGap = if (locked) d(12f) else 0f
    val cy = bounds.center.y
    if (spread && costLayout != null) {
        var x = bounds.left + d(size.paddingDp)
        drawProfileText(labelLayout, x, cy, color, "button.label", valign = KkVAlign.CENTER)
        x = bounds.right - d(size.paddingDp) - costWidth
        drawKkGem(Offset(x + d(gemSize) * 0.5f, cy), color, gemSize)
        drawProfileText(costLayout, x + d(gemSize) + d(6f), cy, color, "button.cost", valign = KkVAlign.CENTER)
        return
    }
    val total = iconSize + iconGap + labelLayout.size.width + (if (costLayout != null) d(12f) + costWidth else 0f)
    var x = bounds.center.x - total * 0.5f
    if (locked) {
        drawKkIcon(KkIcon.SYSTEM_LOCKED, Offset(x + iconSize * 0.5f, cy), iconSize, color)
        x += iconSize + iconGap
    }
    drawProfileText(labelLayout, x, cy, color, "button.label", valign = KkVAlign.CENTER)
    x += labelLayout.size.width
    if (costLayout != null) {
        x += d(12f)
        drawKkGem(Offset(x + d(gemSize) * 0.5f, cy), color, gemSize)
        drawProfileText(costLayout, x + d(gemSize) + d(6f), cy, color, "button.cost", valign = KkVAlign.CENTER)
    }
}

/** The size factor (0.3..1) at which a button's label and gem cost fit [room] px side by side. */
internal fun profileButtonContentFactor(
    measurer: CanvasTextMeasurer,
    label: String,
    cost: String?,
    fontSize: Float,
    costScale: Float,
    room: Float,
    density: Float,
): Float {
    var factor = 1f
    while (factor > 0.3f) {
        val font = fontSize * factor
        val labelWidth = measureKkText(measurer, label, profileButtonStyle(measurer, font), uppercase = true).size.width
        val costWidth = if (cost == null) 0f else density * (12f + font * 0.36f + 6f) +
            measureKkText(measurer, cost, profileButtonStyle(measurer, font * costScale, tabular = true), uppercase = true).size.width
        if (labelWidth + costWidth <= room) return factor
        factor -= 0.05f
    }
    return 0.3f
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
