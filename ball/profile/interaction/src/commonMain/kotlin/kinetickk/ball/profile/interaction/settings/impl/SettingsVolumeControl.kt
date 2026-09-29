// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import kinetickk.ball.profile.interaction.localization.ProfileText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.drawKkSlider
import kinetickk.foundation.design.drawKkStepButton
import kotlin.math.roundToInt

/**
 * The master volume strip of the Sound tab (`.sld` with −/+ steppers and the value): a draggable,
 * keyboard-adjustable slider and an exact numeric editor, placed on the row's layout rects.
 */
@Composable
internal fun SettingsVolumeControl(
    percent: Int,
    routeToken: Long,
    row: SettingsRowLayout,
    measurers: SettingsMeasurers,
    onPercentChange: (Int) -> Unit,
    onStep: (Int) -> Unit,
    onFocusChanged: (SettingsTarget, Boolean) -> Unit,
    onEditingFinished: () -> Unit,
) {
    val decrease = row.decrease ?: return
    val increase = row.increase ?: return
    val track = row.track ?: return
    val output = row.output ?: return
    val language = LocalAppLanguage.current
    val label = language.text(ProfileText.MasterVolume)
    val touch = row.bounds.height.coerceAtLeast(track.height)
    val centerY = track.center.y
    VolumeStepButton(decrease, plus = false, enabled = percent > 0, tag = "kinetickk.settings.master_volume.decrease",
        description = "$label −", target = SettingsTarget.Step(SettingsRow.MASTER_VOLUME, -1), onFocusChanged) { onStep(-1) }
    VolumeStepButton(increase, plus = true, enabled = percent < 100, tag = "kinetickk.settings.master_volume.increase",
        description = "$label +", target = SettingsTarget.Step(SettingsRow.MASTER_VOLUME, 1), onFocusChanged) { onStep(1) }
    VolumeSlider(
        percent, label, measurers,
        Rect(track.left, centerY - touch * 0.5f, track.right, centerY + touch * 0.5f),
        onPercentChange,
    )
    VolumeInput(percent, routeToken, measurers, output, onPercentChange, onEditingFinished)
}

@Composable
private fun VolumeStepButton(
    bounds: Rect,
    plus: Boolean,
    enabled: Boolean,
    tag: String,
    description: String,
    target: SettingsTarget,
    onFocusChanged: (SettingsTarget, Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val touch = bounds.settingsTouch(LocalDensity.current.density)
    PlacedBox(touch, Modifier
        .testTag(tag)
        .semantics { contentDescription = description }
        .onFocusChanged { onFocusChanged(target, it.isFocused) }
        .hoverable(interactions)
        .clickable(interactionSource = interactions, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .drawBehind {
            val face = Rect(bounds.left - touch.left, bounds.top - touch.top, bounds.right - touch.left, bounds.bottom - touch.top)
            drawKkStepButton(face, plus, hovered = hovered, enabled = enabled)
            if (focused) drawSettingsFocus(face)
        })
}

@Composable
private fun VolumeSlider(
    percent: Int,
    label: String,
    measurers: SettingsMeasurers,
    bounds: Rect,
    onPercentChange: (Int) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var widthValue by remember { mutableIntStateOf(0) }
    var dragXValue by remember { mutableFloatStateOf(0f) }
    var focusedValue by remember { mutableStateOf(false) }
    // The thumb's half width at each end maps to 0 and 100 %, so the ends are easy to reach.
    val inset = 7f * LocalDensity.current.density
    fun changeAt(x: Float) {
        val span = widthValue - inset * 2f
        if (span > 0f) onPercentChange(((x - inset) / span * 100f).roundToInt().coerceIn(0, 100))
    }
    val dragState = rememberDraggableState { delta ->
        dragXValue = (dragXValue + delta).coerceIn(0f, widthValue.toFloat())
        changeAt(dragXValue)
    }
    PlacedBox(bounds, Modifier) {
        Canvas(Modifier
            .fillMaxSize()
            .testTag("kinetickk.settings.volume.slider")
            .focusRequester(focusRequester)
            .onSizeChanged { widthValue = it.width }
            .semantics {
                contentDescription = label
                stateDescription = "$percent%"
                progressBarRangeInfo = ProgressBarRangeInfo(percent.toFloat(), 0f..100f, steps = 99)
                setProgress { target ->
                    if (!target.isFinite()) false else {
                        val next = target.roundToInt().coerceIn(0, 100)
                        if (next == percent) false else { onPercentChange(next); true }
                    }
                }
            }
            .onFocusChanged { focusedValue = it.isFocused }
            .onKeyEvent { event ->
                val next = when (event.key) {
                    Key.DirectionLeft, Key.DirectionDown -> percent - 1
                    Key.DirectionRight, Key.DirectionUp -> percent + 1
                    Key.MoveHome -> 0
                    Key.MoveEnd -> 100
                    else -> return@onKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown) onPercentChange(next.coerceIn(0, 100))
                true
            }
            .focusable()
            .draggable(dragState, Orientation.Horizontal, startDragImmediately = true,
                onDragStarted = { start ->
                    focusRequester.requestFocus()
                    dragXValue = start.x.coerceIn(0f, widthValue.toFloat())
                    changeAt(dragXValue)
                }),
        ) {
            val cy = size.height * 0.5f
            val rail = Rect(inset, cy - 13f * density, size.width - inset, cy + 13f * density)
            drawKkSlider(rail, percent / 100f, measurers.roles)
            if (focusedValue) drawSettingsFocus(rail)
        }
    }
}

@Composable
private fun VolumeInput(
    percent: Int,
    routeToken: Long,
    measurers: SettingsMeasurers,
    bounds: Rect,
    onPercentChange: (Int) -> Unit,
    onEditingFinished: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val focusManager = LocalFocusManager.current
    var inputValue by rememberSaveable(routeToken, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(percent.toString()))
    }
    var focusedValue by remember { mutableStateOf(false) }
    LaunchedEffect(percent) {
        if (inputValue.text.toIntOrNull() != percent) inputValue = TextFieldValue(percent.toString())
    }
    fun finishInput() {
        inputValue = TextFieldValue(percent.toString())
        focusManager.clearFocus()
        onEditingFinished()
    }
    val you = measurers.roles.you
    val style = measurers.typography.condStyle(22f * measurers.uiScale, tabular = true, lineHeightEm = 1f, color = Kk.Bone)
    val touch = bounds.settingsTouch(LocalDensity.current.density)
    PlacedBox(Rect(bounds.left, touch.top, bounds.right, touch.bottom), Modifier) {
        BasicTextField(
            value = inputValue,
            onValueChange = { next ->
                if (next.text.length <= 3 && next.text.all { it in '0'..'9' } &&
                    (next.text.isEmpty() || next.text.toInt() in 0..100)
                ) {
                    inputValue = next
                    next.text.toIntOrNull()?.let(onPercentChange)
                }
            },
            singleLine = true,
            textStyle = style.copy(textAlign = TextAlign.End),
            cursorBrush = SolidColor(you),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { finishInput() }),
            modifier = Modifier.fillMaxSize()
                .testTag("kinetickk.settings.volume.input")
                .semantics { contentDescription = language.text(ProfileText.VolumePercent) }
                .onFocusChanged {
                    focusedValue = it.isFocused
                    if (!it.isFocused) inputValue = TextFieldValue(percent.toString())
                }
                .onPreviewKeyEvent {
                    if (it.key == Key.Enter && it.type == KeyEventType.KeyDown) {
                        finishInput()
                        true
                    } else false
                }
                // Keep unhandled text keystrokes out of the session shortcuts.
                .onKeyEvent { it.key != Key.Tab && it.key != Key.Escape }
                .drawBehind {
                    // The editor reads as the plain value; focus adds a `you` rule under it.
                    if (focusedValue) {
                        drawRect(you, Offset(0f, size.height * 0.5f + 14f * density), Size(size.width, 2f * density))
                    }
                },
            decorationBox = { inner ->
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { inner() }
                    BasicText("%", modifier = Modifier.clearAndSetSemantics { }, style = style)
                }
            },
        )
    }
}

/** A box placed at [bounds] (px) inside the Settings overlay. */
@Composable
internal fun PlacedBox(bounds: Rect, modifier: Modifier, content: @Composable () -> Unit = {}) {
    val density = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
            .requiredSize(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
            .then(modifier),
    ) { content() }
}
