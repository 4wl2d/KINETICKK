// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kinetickk.ball.profile.interaction.localization.ProfileText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.roundToInt

@Composable
internal fun SettingsVolumeControl(
    percent: Int,
    routeToken: Long,
    textScale: Float,
    onPercentChange: (Int) -> Unit,
    onEditingFinished: () -> Unit,
    modifier: Modifier = Modifier,
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
    Column(modifier.background(Color(0x66101225)).border(1.dp, DarkLine).padding(horizontal = 10.dp, vertical = 4.dp)) {
        BasicText(language.text(ProfileText.MasterVolume), style = textStyle(9f * textScale, White, FontWeight.Bold))
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VolumeSlider(percent, language.text(ProfileText.MasterVolume), onPercentChange,
                Modifier.weight(1f).fillMaxHeight())
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
                textStyle = textStyle(12f * textScale, Cyan, FontWeight.Bold),
                cursorBrush = SolidColor(Cyan),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { finishInput() }),
                modifier = Modifier.width(88.dp).heightIn(max = 40.dp).fillMaxHeight()
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
                    .background(Violet.copy(alpha = 0.08f))
                    .border(1.dp, if (focusedValue) Cyan else DarkLine),
                decorationBox = { inner ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { inner() }
                        BasicText("%", modifier = Modifier.clearAndSetSemantics { }, style = textStyle(12f * textScale, Muted))
                    }
                },
            )
        }
    }
}

@Composable
private fun VolumeSlider(percent: Int, label: String, onPercentChange: (Int) -> Unit, modifier: Modifier) {
    val focusRequester = remember { FocusRequester() }
    val inset = with(LocalDensity.current) { 10.dp.toPx() }
    var widthValue by remember { mutableIntStateOf(0) }
    var dragXValue by remember { mutableFloatStateOf(0f) }
    var focusedValue by remember { mutableStateOf(false) }
    fun changeAt(x: Float) {
        if (widthValue > inset * 2f) {
            onPercentChange(((x - inset) / (widthValue - inset * 2f) * 100f).roundToInt().coerceIn(0, 100))
        }
    }
    val dragState = rememberDraggableState { delta ->
        dragXValue = (dragXValue + delta).coerceIn(inset, maxOf(inset, widthValue - inset))
        changeAt(dragXValue)
    }
    Canvas(modifier
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
                dragXValue = start.x.coerceIn(inset, maxOf(inset, widthValue - inset))
                changeAt(dragXValue)
            })
    ) {
        val left = Offset(inset, center.y)
        val right = Offset(maxOf(inset, size.width - inset), center.y)
        val thumb = Offset(left.x + (right.x - left.x) * percent / 100f, center.y)
        drawLine(DarkLine, left, right, 6.dp.toPx(), StrokeCap.Round)
        drawLine(Violet, left, thumb, 6.dp.toPx(), StrokeCap.Round)
        if (focusedValue) drawCircle(Cyan.copy(alpha = 0.25f), 11.dp.toPx(), thumb)
        drawCircle(Cyan, 7.dp.toPx(), thumb)
        drawCircle(White, 3.dp.toPx(), thumb)
    }
}
