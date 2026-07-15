package kinetickk

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import kinetickk.model.GameEngine
import kinetickk.model.GamePhase
import kinetickk.model.UiScreen
import kinetickk.ui.drawKinetickk
import kinetickk.ui.GameTextMeasurer
import kinetickk.audio.GameAudio

@Composable
fun KinetickkApp() {
    val engine = remember { GameEngine() }
    val audio = remember { GameAudio() }
    val focusRequester = remember { FocusRequester() }
    val composeTextMeasurer = rememberTextMeasurer(cacheSize = 64)
    val textMeasurer = remember(engine, composeTextMeasurer) {
        GameTextMeasurer(composeTextMeasurer, engine)
    }
    var renderTimeSecondsValue by remember { mutableFloatStateOf(0f) }

    DisposableEffect(audio) {
        onDispose { audio.close() }
    }

    LaunchedEffect(engine, audio) {
        focusRequester.requestFocus()
        var previousFrame = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val delta = (frame - previousFrame) / 1_000_000_000f
            previousFrame = frame
            renderTimeSecondsValue += delta.coerceIn(0f, 0.1f)
            engine.update(delta)
            audio.update(engine.settings, delta, engine.drainSoundCues())
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050610))
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) audio.unlock()
                when (event.key) {
                    Key.Spacebar -> {
                        if (event.type == KeyEventType.KeyDown) engine.requestDash()
                        true
                    }
                    Key.ShiftLeft, Key.ShiftRight -> {
                        engine.setBrake(event.type == KeyEventType.KeyDown)
                        true
                    }
                    Key.P, Key.Escape -> {
                        if (event.type == KeyEventType.KeyDown) {
                            if (event.key == Key.Escape) engine.handleEscape() else engine.togglePause()
                        }
                        true
                    }
                    Key.S -> {
                        if (event.type == KeyEventType.KeyDown) engine.openSettings()
                        true
                    }
                    Key.L -> {
                        if (event.type == KeyEventType.KeyDown) engine.openLab()
                        true
                    }
                    Key.A -> {
                        if (event.type == KeyEventType.KeyDown) engine.openArmory()
                        true
                    }
                    Key.B -> {
                        if (event.type == KeyEventType.KeyDown) engine.openRebirth()
                        true
                    }
                    Key.C -> {
                        if (event.type == KeyEventType.KeyDown) engine.openCodex()
                        true
                    }
                    Key.M -> {
                        if (event.type == KeyEventType.KeyDown) engine.toggleMute()
                        true
                    }
                    Key.Q -> {
                        if (event.type == KeyEventType.KeyDown) engine.rerollChoices()
                        true
                    }
                    Key.Enter -> {
                        if (event.type == KeyEventType.KeyDown) engine.handleEnter()
                        true
                    }
                    Key.One -> {
                        if (event.type == KeyEventType.KeyDown) engine.choose(0)
                        true
                    }
                    Key.Two -> {
                        if (event.type == KeyEventType.KeyDown) engine.choose(1)
                        true
                    }
                    Key.Three -> {
                        if (event.type == KeyEventType.KeyDown) engine.choose(2)
                        true
                    }
                    Key.Four -> {
                        if (event.type == KeyEventType.KeyDown) engine.choose(3)
                        true
                    }
                    Key.R -> {
                        if (event.type == KeyEventType.KeyDown && engine.phase in listOf(GamePhase.GAME_OVER, GamePhase.VICTORY)) {
                            engine.startRun()
                        }
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(engine) {
                awaitPointerEventScope {
                    var wasPressed = false
                    var hudGestureActive = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val position = event.changes.firstOrNull()?.position
                        val pressed = event.changes.any { it.pressed }
                        if (pressed && !wasPressed && position != null) {
                            hudGestureActive = engine.isHudControlPosition(position.x, position.y)
                        }
                        if (
                            position != null &&
                            engine.phase == GamePhase.RUNNING &&
                            engine.screen == UiScreen.GAME &&
                            !hudGestureActive &&
                            !engine.isHudControlPosition(position.x, position.y)
                        ) {
                            engine.updatePointer(position.x, position.y)
                        }
                        if (pressed && !wasPressed && position != null) {
                            audio.unlock()
                            engine.pointerPressed(position.x, position.y)
                        }
                        if (!pressed && wasPressed) {
                            engine.pointerReleased()
                            hudGestureActive = false
                        }
                        engine.setSecondaryBrake(event.buttons.isSecondaryPressed)
                        event.changes.forEach { change ->
                            if (change.pressed) change.consume()
                        }
                        wasPressed = pressed
                    }
                }
            },
    ) {
        engine.resize(size.width, size.height, density)
        drawKinetickk(engine, textMeasurer, renderTimeSecondsValue)
    }
}
