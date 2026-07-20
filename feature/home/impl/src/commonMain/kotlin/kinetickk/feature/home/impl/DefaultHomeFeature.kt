// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.home.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.input.pointer.pointerInput
import kinetickk.core.audio.api.AudioCue
import kinetickk.core.content.CoreShape
import kinetickk.core.content.WeaponCatalog
import kinetickk.core.design.*
import kinetickk.core.profile.api.CollectionCapability
import kinetickk.core.profile.api.LoadoutCapability
import kinetickk.core.profile.api.RebirthCapability
import kinetickk.core.profile.api.PreferencesReader
import kinetickk.feature.home.api.HomeFeature
import kinetickk.feature.home.api.HomeOutput
import kinetickk.feature.home.api.HomeUiModel
import kinetickk.feature.home.api.unlockMatter
import kotlin.math.PI
import kotlin.math.min

class DefaultHomeFeature(
    loadoutCapability: LoadoutCapability,
    collectionCapability: CollectionCapability,
    rebirthCapability: RebirthCapability,
    private val preferencesReader: PreferencesReader,
) : HomeFeature {
    private val reducer = HomeReducer(loadoutCapability, collectionCapability, rebirthCapability)

    @Composable
    override fun Content(inputEnabled: Boolean, onOutput: (HomeOutput) -> Unit) {
        val density = LocalDensity.current.density
        val composeTextMeasurer = rememberTextMeasurer(cacheSize = 64)
        var revisionValue by remember { mutableIntStateOf(0) }
        var viewportValue by remember { mutableStateOf(HomeViewport(1f, 1f, density)) }
        var renderTimeSecondsValue by remember { mutableFloatStateOf(0f) }
        @Suppress("UNUSED_EXPRESSION")
        revisionValue
        val uiModel = reducer.uiModel()
        val textMeasurer = CanvasTextMeasurer(
            delegate = composeTextMeasurer,
            scale = preferencesReader.preferences().textScale,
        )

        fun dispatch(action: HomeAction) {
            val output = reducer.reduce(action)
            revisionValue++
            onOutput(HomeOutput.Cue(AudioCue.UI_CLICK))
            if (output != null) onOutput(output)
        }

        LaunchedEffect(Unit) {
            var previousFrame = withFrameNanos { it }
            while (true) {
                val frame = withFrameNanos { it }
                renderTimeSecondsValue += ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.1f)
                previousFrame = frame
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(SpaceBlack)
                .onSizeChanged { size ->
                    viewportValue = HomeViewport(size.width.toFloat(), size.height.toFloat(), density)
                }
                .pointerInput(inputEnabled, viewportValue) {
                    if (!inputEnabled) return@pointerInput
                    detectTapGestures { position ->
                        resolveHomePress(viewportValue, position.x, position.y)?.let(::dispatch)
                    }
                },
        ) {
            drawRect(SpaceBlack)
            drawHome(uiModel, textMeasurer, renderTimeSecondsValue)
        }
    }
}

private val MenuNavLabels = listOf("LAB [L]", "ARMORY [A]", "REBIRTH [B]", "CODEX [C]", "SETTINGS [S]")

private fun DrawScope.drawHome(engine: HomeUiModel, textMeasurer: TextMeasurer, renderTime: Float) {
    val narrow = size.width / density < 700f
    val titleSize = if (narrow) 37f else 62f
    val titleCenter = Offset(size.width * 0.5f, size.height * 0.245f)
    val orbitRadius = min(d(if (narrow) 118f else 175f), size.width * 0.29f)
    val orbitAngle = renderTime * 0.42f
    val orbitPoint = polar(titleCenter, orbitRadius, orbitAngle)
    val counterPoint = polar(titleCenter, orbitRadius * 0.72f, orbitAngle + PI.toFloat())
    drawCircle(Violet.copy(alpha = 0.055f), orbitRadius, titleCenter)
    drawCircle(Cyan.copy(alpha = 0.24f), orbitRadius, titleCenter, style = Stroke(1f, pathEffect = dashEffect))
    drawLine(Violet.copy(alpha = 0.16f), orbitPoint, counterPoint, 1f, pathEffect = dashEffect)
    drawCircle(Magenta.copy(alpha = 0.13f), d(24f), orbitPoint)
    drawCircle(SpaceBlack, d(8f), orbitPoint)
    drawCircle(Magenta, d(11f), orbitPoint, style = Stroke(d(1.5f)))
    drawCircle(Cyan.copy(alpha = 0.12f), d(22f), counterPoint)
    drawCircle(White, d(8f), counterPoint)
    drawLabel(textMeasurer, "KINETICKK", size.width * 0.5f, size.height * 0.205f, titleSize, Cyan, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "YOUR MOVEMENT IS THE WEAPON. YOUR CURSOR IS THE THREAT.", size.width * 0.5f, size.height * 0.37f, if (narrow) 9f else 12f, Muted, centered = true)
    drawLine(Violet.copy(alpha = 0.35f), Offset(size.width * 0.25f, size.height * 0.41f), Offset(size.width * 0.75f, size.height * 0.41f), 1f, pathEffect = dashEffect)
    drawLabel(textMeasurer, "LEAD THE CORE  //  BUILD MOMENTUM  //  NEVER TOUCH THE SINGULARITY", size.width * 0.5f, size.height * 0.45f, if (narrow) 8f else 10f, White, centered = true)
    drawLabel(textMeasurer, "SELECT CORE", size.width * 0.5f, size.height * 0.51f, 10f, Muted, centered = true)

    val centers = listOf(size.width * 0.5f - d(130f), size.width * 0.5f, size.width * 0.5f + d(130f))
    CoreShape.entries.forEachIndexed { index, shape ->
        val cardCenter = Offset(centers[index], size.height * 0.62f)
        val selected = engine.coreShape == shape
        val unlocked = engine.isCoreShapeUnlocked(shape)
        drawRect(if (selected) CyanSoft else Color(0x88101225), Offset(cardCenter.x - d(60f), cardCenter.y - d(55f)), Size(d(120f), d(110f)))
        drawRect(if (selected) Cyan else DarkLine, Offset(cardCenter.x - d(60f), cardCenter.y - d(55f)), Size(d(120f), d(110f)), style = Stroke(d(if (selected) 2f else 1f)))
        when (shape) {
            CoreShape.ORB -> drawCircle(if (unlocked) Cyan else Muted, d(13f), Offset(cardCenter.x, cardCenter.y - d(12f)))
            CoreShape.PRISM -> drawPolygon(Offset(cardCenter.x, cardCenter.y - d(12f)), d(17f), 4, (PI / 4).toFloat(), if (unlocked) Violet else Muted, Fill)
            CoreShape.SHARD -> drawPolygon(Offset(cardCenter.x, cardCenter.y - d(12f)), d(18f), 3, -(PI / 2).toFloat(), if (unlocked) Magenta else Muted, Fill)
        }
        drawLabel(textMeasurer, shape.name, cardCenter.x, cardCenter.y + d(17f), 9f, if (selected) White else Muted, centered = true, weight = FontWeight.Bold)
        if (!unlocked) drawLabel(textMeasurer, "${formatCompact(shape.unlockMatter)} LIFETIME", cardCenter.x, cardCenter.y + d(34f), 7f, Orange, centered = true)
    }

    val buttonY = size.height * 0.78f
    drawRect(Cyan.copy(alpha = 0.12f), Offset(size.width * 0.5f - d(150f), buttonY - d(31f)), Size(d(300f), d(62f)))
    drawRect(Cyan, Offset(size.width * 0.5f - d(150f), buttonY - d(31f)), Size(d(300f), d(62f)), style = Stroke(d(2f)))
    drawLabel(textMeasurer, "START RUN", size.width * 0.5f, buttonY - d(12f), 15f, White, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "CLICK / TAP / ENTER", size.width * 0.5f, buttonY + d(14f), 8f, Cyan, centered = true)
    val navY = size.height * 0.9f
    val spacing = min(d(132f), size.width * 0.19f)
    val navStart = size.width * 0.5f - spacing * (MenuNavLabels.lastIndex * 0.5f)
    MenuNavLabels.forEachIndexed { index, label ->
        val centerX = navStart + spacing * index
        val accent = when (index) {
            0 -> Violet
            2 -> if (engine.canRebirth) Acid else Orange
            else -> DarkLine
        }
        val labelColor = when (index) {
            0 -> Violet
            2 -> if (engine.canRebirth) Acid else Orange
            else -> Muted
        }
        drawRect(Color(0x99101225), Offset(centerX - spacing * 0.44f, navY - d(20f)), Size(spacing * 0.88f, d(40f)))
        drawRect(accent, Offset(centerX - spacing * 0.44f, navY - d(20f)), Size(spacing * 0.88f, d(40f)), style = Stroke(d(1f)))
        drawLabel(textMeasurer, label, centerX, navY - d(5f), if (narrow) 6f else 8f, labelColor, centered = true, weight = FontWeight.Bold)
    }
    drawLabel(textMeasurer, "KINETIC MATTER ${formatCompact(engine.totalMatter)} // REBIRTH ${engine.rebirthLevel}", d(20f), d(20f), 9f, Acid)
    drawLabel(textMeasurer, "DISCOVERED ${engine.discoveredItemCount}/400  //  WEAPONS ${engine.unlockedWeaponCount}/${WeaponCatalog.all.size}", d(20f), d(39f), 7f, Muted)
    drawLabel(textMeasurer, "DIRECTIVE ${engine.rebirthProfile.directive.displayName.uppercase()}", d(20f), d(56f), 7f, Orange)
    drawLabel(textMeasurer, "KINETICKK 0.1.0 // COPYRIGHT (C) 2026 VLADISLAV TOMILOV // GNU GPL V3+", size.width * 0.5f, size.height - d(24f), if (narrow) 5f else 6f, Muted, centered = true)
    drawLabel(textMeasurer, "YOU MAY REDISTRIBUTE UNDER GPL V3+ // NO WARRANTY", size.width * 0.5f, size.height - d(14f), if (narrow) 4f else 5f, Muted, centered = true)
    drawLabel(textMeasurer, "SOURCE + LICENSE: GITHUB.COM/4WL2D/KINETICKK", size.width * 0.5f, size.height - d(6f), if (narrow) 4f else 5f, Muted, centered = true)
}
