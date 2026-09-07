// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.interaction.layout.forEachRunningControlBounds
import kinetickk.ball.gameplay.interaction.layout.gameplayLayoutMode
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.nucleus.model.formatRunTime
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.min

internal fun DrawScope.drawHud(engine: GameplayRenderModel, textMeasurer: TextMeasurer) {
    val mode = gameplayLayoutMode(size.width, size.height, density)
    val spacingScale = textMeasurer.scale.coerceAtLeast(1f)
    val portrait = mode == GameplayLayoutMode.COMPACT_PORTRAIT
    val compact = mode != GameplayLayoutMode.REGULAR
    val narrow = size.width / density < 700f
    val left = d(20f)
    val top = d(if (portrait) 78f else 22f)
    val width = min(d(if (compact) 164f else 204f), size.width * if (portrait) 0.52f else 0.28f)
    val barLeft = left + d(22f)
    val healthColor = if (engine.hp / engine.maxHp < 0.3f) Red else Cyan
    drawInterfaceGlyph(InterfaceGlyph.PLUS, Offset(left + d(5f), top + d(8f * spacingScale)), d(7f), healthColor)
    drawLabel(textMeasurer, engine.hp.toInt().toString(), barLeft, top, 14f, White, weight = FontWeight.Bold)
    drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.LevelShort, engine.level), left + width, top + d(3f), 9f, Muted, alignRight = true)
    drawBar(barLeft, top + d(26f * spacingScale), width - d(22f), d(5f), engine.hp / engine.maxHp, healthColor, DarkLine)
    drawBar(barLeft, top + d(35f * spacingScale), width - d(22f), d(2f), engine.data.toFloat() / engine.nextLevelData.coerceAtLeast(1), White.copy(alpha = 0.55f), DarkLine)
    if (engine.maxShield > 0f) {
        drawInterfaceGlyph(InterfaceGlyph.SHIELD, Offset(left + d(5f), top + d(51f * spacingScale)), d(6f), Violet)
        drawBar(barLeft, top + d(49f * spacingScale), width - d(55f), d(3f), engine.shield / engine.maxShield, Violet, DarkLine)
        drawLabel(textMeasurer, engine.shield.toInt().toString(), left + width, top + d(43f * spacingScale), 9f, Violet, alignRight = true)
    }
    drawCharacterAbilityHud(engine, textMeasurer, left, top + d((if (engine.maxShield > 0f) 69f else 51f) * spacingScale), width)

    val timerX = if (portrait) d(20f) else size.width * 0.5f
    val timerY = d(if (!compact && narrow) 126f else 20f)
    drawLabel(textMeasurer, formatRunTime(engine.elapsed), timerX, timerY, if (compact) 20f else 24f,
        White, centered = !portrait, weight = FontWeight.Medium)
    val progressWidth = d(if (portrait) 94f else 132f)
    drawBar(if (portrait) timerX else timerX - progressWidth * 0.5f, timerY + d(34f * spacingScale), progressWidth, d(2f), engine.runProgress, Muted, DarkLine)
    // Charge and tether stability use distinct glyphs as well as color.
    val meterY = if (portrait) top + d(104f * spacingScale) else timerY + d(54f * spacingScale)
    val meterX = if (portrait) left else timerX - d(76f)
    drawMeter(InterfaceGlyph.BOLT, meterX, meterY, d(64f), engine.overdriveCharge / 100f, if (engine.overdriveTime > 0f) Acid else Magenta)
    val polarityColor = when {
        engine.polarityStability < 0.22f -> Red
        engine.polarityStability < 0.58f -> Orange
        else -> Muted
    }
    drawMeter(InterfaceGlyph.RING, meterX + d(88f), meterY, d(64f), engine.polarityStability, polarityColor)
    if (engine.overdriveTime > 0f || engine.polarityStability < 0.58f) {
        val warning = if (engine.polarityStability < 0.58f) {
            textMeasurer.language.text(GameplayText.PolarityWarning, (engine.polarityStability * 100f).toInt())
        } else textMeasurer.language.text(GameplayText.OverdriveActive)
        drawLabel(textMeasurer, warning, if (portrait) left else timerX, meterY + d(15f * spacingScale), 8f,
            if (engine.polarityStability < 0.58f) polarityColor else Acid, centered = !portrait)
    }

    val right = size.width - d(22f)
    val infoTop = if (compact) top else d(24f)
    if (!compact) {
        drawSystemGlyph(weaponGlyphStyle(engine.weapon), Offset(right - d(10f), infoTop + d(7f)), d(11f), 0f, weaponColor(engine.weapon))
        drawLabel(textMeasurer, engine.currentWeaponDefinition.name.localizedContent(textMeasurer.language), right - d(29f), infoTop,
            10f, White, alignRight = true, maxWidth = min(d(200f), size.width * 0.22f))
        drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.LevelShort, engine.weaponLevel), right - d(29f), infoTop + d(17f * spacingScale), 8f, Muted, alignRight = true)
        drawBar(right - d(96f), infoTop + d(34f * spacingScale), d(96f), d(2f), engine.weaponMasteryProgress, weaponColor(engine.weapon), DarkLine)
    }
    val statY = infoTop + d((if (portrait) 0f else 53f) * spacingScale)
    drawInterfaceGlyph(InterfaceGlyph.GAUGE, Offset(right - d(70f), statY + d(6f)), d(7f), Muted)
    drawLabel(textMeasurer, formatCompact(engine.speed.toLong(), textMeasurer.language), right, statY - d(2f), 12f, White, alignRight = true)
    drawInterfaceGlyph(InterfaceGlyph.DIAMOND, Offset(right - d(70f), statY + d(32f * spacingScale)), d(7f), Muted)
    drawLabel(textMeasurer, formatCompact(engine.runMatter, textMeasurer.language), right, statY + d(24f * spacingScale), 12f, White, alignRight = true)
    drawRelics(engine, textMeasurer, right, statY + d(54f * spacingScale))

    if (engine.combo >= 2 && engine.comboTime > 0f) {
        val comboY = size.height - d(75f)
        drawLabel(textMeasurer, "×${engine.combo}", size.width * 0.5f, comboY, 22f, White, centered = true, weight = FontWeight.Bold)
        drawBar(size.width * 0.5f - d(28f), comboY + d(30f), d(56f), d(2f), engine.comboTime / engine.comboWindow.coerceAtLeast(0.001f), Cyan, DarkLine)
    }
    if (engine.tetherDistance < 75f && engine.runGrace <= 0f) {
        drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.SingularityClose), size.width * 0.5f,
            size.height - d(114f), 10f, Red, centered = true, weight = FontWeight.Bold)
    }
    if (engine.messageTime > 0f) {
        drawLabel(textMeasurer, engine.message.localizedContent(textMeasurer.language), size.width * 0.5f,
            maxOf(size.height * (if (compact || narrow) 0.39f else 0.21f), meterY + d(45f * spacingScale)), if (compact) 14f else 17f,
            if (engine.message == "OVERHEAT") Red else White, centered = true,
            alpha = (engine.messageTime / 0.45f).coerceIn(0f, 1f), maxWidth = size.width - d(40f), maxLines = 2)
    }
    drawControls(engine, textMeasurer)
}

private fun DrawScope.drawMeter(glyph: InterfaceGlyph, x: Float, y: Float, width: Float, progress: Float, color: Color) {
    drawInterfaceGlyph(glyph, Offset(x + d(5f), y), d(6f), color)
    drawBar(x + d(18f), y - d(1.5f), width - d(18f), d(3f), progress, color, DarkLine)
}

private fun DrawScope.drawRelics(engine: GameplayRenderModel, textMeasurer: TextMeasurer, right: Float, top: Float) {
    val slot = d(28f)
    engine.equippedRelics.forEachIndexed { index, relic ->
        val center = Offset(right - d(12f) - slot * index, top + d(12f))
        drawRelicIcon(engine.content.relic(relic.id), engine.content.relicPolicy, center, d(9f), relic.rank, 0f)
        if (relic.rank > 1) drawLabel(textMeasurer, relic.rank.toString(), center.x + d(8f), center.y + d(5f), 6f, White)
    }
}

internal fun DrawScope.drawControls(engine: GameplayRenderModel, textMeasurer: TextMeasurer) {
    val regular = gameplayLayoutMode(size.width, size.height, density) == GameplayLayoutMode.REGULAR
    forEachRunningControlBounds(size.width, size.height, density) { target, left, top, right, bottom ->
        val center = Offset((left + right) * 0.5f, (top + bottom) * 0.5f)
        val radius = min(right - left, bottom - top) * 0.5f - d(1f)
        val accent = when (target) {
            RunningControlTarget.DASH -> if (engine.overheated) Red else if (engine.dashReady) Cyan else Muted
            RunningControlTarget.BRAKE -> if (engine.braking) Cyan else Muted
            else -> Muted
        }
        val active = (target == RunningControlTarget.BRAKE && engine.braking) || (target == RunningControlTarget.DASH && engine.dashPhaseTime > 0f)
        drawCircle(if (active) accent.copy(alpha = 0.18f) else OverlayPanel, radius, center)
        drawCircle(if (active) accent else DarkLine, radius, center, style = Stroke(d(1f)))
        val glyph = when (target) {
            RunningControlTarget.DASH -> InterfaceGlyph.BOLT
            RunningControlTarget.BRAKE -> InterfaceGlyph.SHIELD
            RunningControlTarget.PAUSE -> InterfaceGlyph.PAUSE
            RunningControlTarget.PERFORMANCE -> InterfaceGlyph.CHART
        }
        drawInterfaceGlyph(glyph, center.copy(y = center.y - d(if (regular) 5f else 0f)), d(11f), accent)
        if (target == RunningControlTarget.DASH) {
            drawArc(accent, -90f, 360f * (1f - engine.heat / GameplayRenderModel.MAX_HEAT).coerceIn(0f, 1f),
                false, center - Offset(radius, radius), Size(radius * 2f, radius * 2f), style = Stroke(d(2f), cap = StrokeCap.Round))
        }
        if (regular) {
            drawLabel(textMeasurer, if (target == RunningControlTarget.BRAKE) "Shift" else "Space",
                center.x, center.y + d(11f), 7f, Muted, centered = true)
        }
    }
}

private fun DrawScope.drawCharacterAbilityHud(engine: GameplayRenderModel, textMeasurer: TextMeasurer, left: Float, top: Float, width: Float) {
    val ability = engine.characterAbility
    if (engine.coreShape == CoreShape.SHARD) {
        drawInterfaceGlyph(InterfaceGlyph.BOLT, Offset(left + d(5f), top + d(5f)), d(6f), Muted)
        drawLabel(textMeasurer, "→", left + d(22f), top - d(2f), 9f, Muted)
        drawInterfaceGlyph(InterfaceGlyph.TARGET, Offset(left + d(44f), top + d(5f)), d(6f), Muted)
        return
    }
    val glyph = when (engine.coreShape) {
        CoreShape.ORB, CoreShape.RING -> InterfaceGlyph.RING
        CoreShape.PRISM, CoreShape.DIAMOND -> InterfaceGlyph.SHIELD
        CoreShape.SHARD -> InterfaceGlyph.BOLT
        CoreShape.TESSERACT -> InterfaceGlyph.LAYERS
    }
    val amount = when (engine.coreShape) {
        CoreShape.PRISM -> ability.barrier.toInt().toString()
        CoreShape.RING -> ability.ringRadius.toInt().toString()
        CoreShape.TESSERACT -> ability.lattice.size.toString()
        else -> "${(ability.charge * 100f).toInt()}%"
    }
    val accent = if (ability.parryWindow > 0f) Cyan else Muted
    drawInterfaceGlyph(glyph, Offset(left + d(5f), top + d(5f)), d(6f), accent)
    when (engine.coreShape) {
        CoreShape.TESSERACT -> repeat(4) { index ->
            drawCircle(if (index < ability.lattice.size) Cyan else DarkLine, d(2.5f), Offset(left + d(26f + index * 12f), top + d(5f)))
        }
        CoreShape.RING -> Unit
        else -> drawBar(left + d(22f), top + d(4f), width - d(64f), d(2f), ability.charge, accent, DarkLine)
    }
    drawLabel(textMeasurer, amount, left + width, top - d(2f), 8f, Muted, alignRight = true)
}
