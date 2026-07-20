// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.presentation.canvas

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextMeasurer as ComposeTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import kinetickk.feature.game.domain.model.GamePhase
import kinetickk.feature.game.domain.model.UiScreen
import kinetickk.feature.game.domain.model.WeaponMastery
import kinetickk.feature.game.domain.projection.GameProjection
import kinetickk.feature.game.domain.projection.VisualFxProjection
import kotlin.math.cos
import kotlin.math.sin

internal val SpaceBlack = Color(0xFF050610)
internal val OverlayPanel = Color(0xE60B0D1D)
internal val GridBlue = Color(0xFF151B38)
internal val Cyan = Color(0xFF42F5E9)
internal val CyanSoft = Color(0x5542F5E9)
internal val Violet = Color(0xFFA96CFF)
internal val VioletSoft = Color(0x55A96CFF)
internal val Magenta = Color(0xFFFF4DC4)
internal val Acid = Color(0xFFB6FF5B)
internal val Orange = Color(0xFFFFA14B)
internal val Blue = Color(0xFF73A6FF)
internal val Gold = Color(0xFFFFD45B)
internal val Red = Color(0xFFFF426D)
internal val DamagePale = Color(0xFFFFF2C2)
internal val White = Color(0xFFF4F6FF)
internal val Muted = Color(0xFF8F98B5)
internal val DarkLine = Color(0xFF252C4F)
internal val dashEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 9f))
internal val mono = FontFamily.Monospace
internal val ParticleColors = listOf(Cyan, Violet, Magenta, Acid, Red)
internal val RarityColors = listOf(Muted, Cyan, Violet, Magenta, Acid)
internal val WeaponColors = listOf(Cyan, Violet, Magenta, Acid, Orange, Cyan, Magenta, Violet, Orange, Red, White, Color(0xFF73A6FF))
internal val DamageNumberColors = listOf(DamagePale, Gold, Orange, Red)
internal val DamageNumberTierScales = floatArrayOf(0.95f, 1.03f, 1.12f, 1.25f)
internal val VelocityNames = listOf("DRIFT", "SURGE", "HYPER", "OVERDRIVE", "TRANSCENDENT")
internal val SettingsLabels = listOf(
    "SFX",
    "MUSIC",
    "MASTER VOLUME",
    "SIMULATION SPEED",
    "TEXT SIZE",
    "SCREEN SHAKE",
    "PARTICLES",
    "DAMAGE NUMBERS",
    "DAMAGE NUMBER SIZE",
    "DAMAGE NUMBER FORMAT",
    "DAMAGE COLOR TIERS",
)
internal val MenuNavLabels = listOf("LAB [L]", "ARMORY [A]", "REBIRTH [B]", "CODEX [C]", "SETTINGS [S]")
internal val WeaponMasteryProgressionLabel = WeaponMastery.entries.drop(1).joinToString("  ") {
    "L${it.minimumLevel} ${it.displayLabel.uppercase()}"
}

internal fun textStyle(size: Float, color: Color = White, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = mono, fontSize = size.sp, color = color, fontWeight = weight)

class GameTextMeasurer(
    val delegate: ComposeTextMeasurer,
    val scale: Float,
) {
}

internal typealias TextMeasurer = GameTextMeasurer

fun DrawScope.drawKinetickk(
    engine: GameProjection,
    visualFx: VisualFxProjection,
    textMeasurer: TextMeasurer,
    renderTime: Float,
) {
    drawRect(SpaceBlack)
    val shake = if (engine.settings.screenShake) engine.screenShake else 0f
    val shakeX = if (shake > 0f) sin(engine.elapsed * 91f) * shake else 0f
    val shakeY = if (shake > 0f) cos(engine.elapsed * 77f) * shake else 0f
    drawBackdrop(engine, shakeX, shakeY, renderTime)

    if (engine.phase != GamePhase.MENU) {
        drawWorld(engine, visualFx, shakeX, shakeY, textMeasurer)
        drawScreenFx(engine, renderTime)
        drawHud(engine, textMeasurer)
    }

    if (engine.screen == UiScreen.GAME) {
        when (engine.phase) {
            GamePhase.MENU -> drawMenu(engine, textMeasurer, renderTime)
            GamePhase.PAUSED -> drawPause(textMeasurer)
            GamePhase.CHOICE -> drawChoice(engine, textMeasurer, renderTime)
            GamePhase.GAME_OVER -> drawEnd(engine, textMeasurer, victory = false)
            GamePhase.VICTORY -> drawEnd(engine, textMeasurer, victory = true)
            GamePhase.RUNNING -> Unit
        }
    } else {
        if (engine.phase == GamePhase.MENU) drawMenu(engine, textMeasurer, renderTime)
        when (engine.screen) {
            UiScreen.SETTINGS -> drawSettings(engine, textMeasurer)
            UiScreen.LAB -> drawLab(engine, textMeasurer)
            UiScreen.ARMORY -> drawArmory(engine, textMeasurer, renderTime)
            UiScreen.REBIRTH -> drawRebirth(engine, textMeasurer)
            UiScreen.CODEX -> drawCodex(engine, textMeasurer)
            UiScreen.GAME -> Unit
        }
    }
}
