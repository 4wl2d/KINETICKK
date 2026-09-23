// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
    val healthColor = if (engine.hp / engine.maxHp < 0.3f) Red else KineticAccent
    val healthSize = if (compact) 28f else 38f
    drawLabel(textMeasurer, engine.hp.toInt().toString(), left, top - d(10f), healthSize, White, display = true)
    drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.LevelShort, engine.level), left + width,
        top + d(16f * spacingScale), 11f, White, alignRight = true, display = true)
    val healthY = top + d((if (compact) 36f else 48f) * spacingScale)
    val segments = 8
    val segmentWidth = (width - d(3f) * (segments - 1)) / segments
    repeat(segments) { index ->
        val fill = (engine.hp / engine.maxHp * segments - index).coerceIn(0f, 1f)
        val x = left + index * (segmentWidth + d(3f))
        drawKineticRibbon(Rect(x, healthY, x + segmentWidth, healthY + d(8f)), DarkLine, d(2f))
        if (fill > 0f) drawKineticRibbon(Rect(x, healthY, x + segmentWidth * fill, healthY + d(8f)), healthColor, d(2f))
    }
    drawBar(left, healthY + d(15f), width, d(2f), engine.data.toFloat() / engine.nextLevelData.coerceAtLeast(1), White, DarkLine)
    var abilityY = healthY + d(30f)
    if (engine.maxShield > 0f) {
        drawInterfaceGlyph(InterfaceGlyph.SHIELD, Offset(left + d(5f), abilityY + d(3f)), d(6f), Violet)
        drawBar(left + d(22f), abilityY, width - d(58f), d(4f), engine.shield / engine.maxShield, Violet, DarkLine)
        drawLabel(textMeasurer, engine.shield.toInt().toString(), left + width, abilityY - d(8f), 11f, Violet, alignRight = true)
        abilityY += d(23f)
    }
    drawCharacterAbilityHud(engine, textMeasurer, left, abilityY, width)
    val meterY = abilityY + d(29f * spacingScale)
    drawMeter(InterfaceGlyph.BOLT, left, meterY, width * 0.43f, engine.overdriveCharge / 100f, if (engine.overdriveTime > 0f) KineticAccent else Magenta)
    val polarityColor = when {
        engine.polarityStability < 0.22f -> Red
        engine.polarityStability < 0.58f -> Orange
        else -> White
    }
    drawMeter(InterfaceGlyph.RING, left + width * 0.55f, meterY, width * 0.43f, engine.polarityStability, polarityColor)
    if (engine.overdriveTime > 0f || engine.polarityStability < 0.58f) {
        val warning = if (engine.polarityStability < 0.58f) {
            textMeasurer.language.text(GameplayText.PolarityWarning, (engine.polarityStability * 100f).toInt())
        } else textMeasurer.language.text(GameplayText.OverdriveActive)
        drawLabel(textMeasurer, warning, left, meterY + d(13f), 10f,
            if (engine.polarityStability < 0.58f) polarityColor else KineticAccent,
            maxWidth = width, maxLines = 2)
    }

    val timerWidth = min(d(if (compact) 156f else 200f) * textMeasurer.scale.coerceAtMost(1.25f), size.width * if (portrait) 0.38f else 0.26f)
    val timerLeft = if (portrait) d(12f) else (size.width - timerWidth) * 0.5f
    val timerTop = d(if (portrait) 12f else 18f)
    val timerHeight = d(if (compact) 48f else 60f) * textMeasurer.scale.coerceAtMost(1.25f)
    drawKineticRibbon(Rect(timerLeft, timerTop, timerLeft + timerWidth, timerTop + timerHeight), White, d(10f))
    drawLabel(textMeasurer, formatRunTime(engine.elapsed), timerLeft + timerWidth * 0.5f, timerTop + d(1f),
        (if (compact) 29f else 38f) / textMeasurer.scale.coerceAtLeast(1.25f) * 1.25f,
        SpaceBlack, centered = true, display = true, maxWidth = timerWidth - d(20f))
    drawBar(timerLeft + d(8f), timerTop + timerHeight + d(10f), timerWidth - d(16f), d(2f), engine.runProgress, KineticAccent, DarkLine)

    val right = size.width - d(22f)
    val infoTop = if (compact) d(82f) else d(24f)
    if (!compact) {
        drawSystemGlyph(weaponGlyphStyle(engine.weapon), Offset(right - d(10f), infoTop + d(15f)), d(12f), 0f, weaponColor(engine.weapon))
        drawLabel(textMeasurer, engine.currentWeaponDefinition.name.localizedContent(textMeasurer.language).uppercase(), right - d(30f), infoTop,
            15f, White, alignRight = true, display = true, maxWidth = min(d(250f), size.width * 0.26f))
        drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.LevelShort, engine.weaponLevel), right, infoTop + d(25f * spacingScale), 11f, Muted, alignRight = true, display = true)
        drawBar(right - d(124f), infoTop + d(45f * spacingScale), d(124f), d(2f), engine.weaponMasteryProgress, weaponColor(engine.weapon), DarkLine)
    }
    val statY = infoTop + d((if (compact) 0f else 63f) * spacingScale)
    drawInterfaceGlyph(InterfaceGlyph.GAUGE, Offset(right - d(85f), statY + d(15f)), d(9f), Muted)
    drawLabel(textMeasurer, formatCompact(engine.speed.toLong(), textMeasurer.language), right, statY - d(5f), 22f, White, alignRight = true, display = true)
    drawInterfaceGlyph(InterfaceGlyph.DIAMOND, Offset(right - d(85f), statY + d(48f * spacingScale)), d(9f), KineticAccent)
    drawLabel(textMeasurer, formatCompact(engine.runMatter, textMeasurer.language), right, statY + d(34f * spacingScale), 22f, White, alignRight = true, display = true)
    drawRelics(engine, textMeasurer, right, statY + d(80f * spacingScale))

    if (engine.combo >= 2 && engine.comboTime > 0f) {
        val comboY = size.height - d(75f)
        drawLabel(textMeasurer, "×${engine.combo}", size.width * 0.5f, comboY, 30f, KineticAccent, centered = true, display = true)
        drawBar(size.width * 0.5f - d(28f), comboY + d(42f), d(56f), d(2f), engine.comboTime / engine.comboWindow.coerceAtLeast(0.001f), Cyan, DarkLine)
    }
    if (engine.tetherDistance < 75f && engine.runGrace <= 0f) {
        drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.SingularityClose), size.width * 0.5f,
            size.height - d(114f), 10f, Red, centered = true, weight = FontWeight.Bold)
    }
    if (engine.messageTime > 0f) {
        drawLabel(textMeasurer, engine.message.localizedContent(textMeasurer.language), size.width * 0.5f,
            maxOf(size.height * (if (compact || narrow) 0.44f else 0.22f), timerTop + timerHeight + d(28f)), if (compact) 16f else 21f,
            if (engine.message == "OVERHEAT") Red else White, centered = true,
            alpha = (engine.messageTime / 0.45f).coerceIn(0f, 1f), maxWidth = size.width - d(40f), maxLines = 2, display = true)
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
        if (regular) {
            drawKineticRibbon(Rect(left, top, right, bottom), if (active) accent else OverlayPanel, d(8f))
            val ink = if (active) SpaceBlack else accent
            val glyphCenter = Offset(left + d(25f), center.y - d(2f))
            drawInterfaceGlyph(if (target == RunningControlTarget.DASH) InterfaceGlyph.BOLT else InterfaceGlyph.SHIELD,
                glyphCenter, d(13f), ink)
            drawLabel(textMeasurer, textMeasurer.language.text(if (target == RunningControlTarget.DASH) GameplayText.Dash else GameplayText.Brake).uppercase(),
                left + d(49f), top + d(6f), 15f, if (active) SpaceBlack else White, display = true, maxWidth = right - left - d(57f))
            drawLabel(textMeasurer, if (target == RunningControlTarget.DASH) "SPACE" else "SHIFT", left + d(49f), bottom - d(24f), 9f, ink)
            if (target == RunningControlTarget.DASH) drawBar(left + d(12f), bottom - d(5f), right - left - d(26f), d(2f),
                1f - engine.heat / GameplayRenderModel.MAX_HEAT, accent, DarkLine)
        } else {
            drawKineticRibbon(Rect(left, top, right, bottom), if (active) accent.copy(alpha = 0.22f) else OverlayPanel, d(7f))
            val glyph = when (target) {
                RunningControlTarget.DASH -> InterfaceGlyph.BOLT
                RunningControlTarget.BRAKE -> InterfaceGlyph.SHIELD
                RunningControlTarget.PAUSE -> InterfaceGlyph.PAUSE
                RunningControlTarget.PERFORMANCE -> InterfaceGlyph.CHART
            }
            drawInterfaceGlyph(glyph, center, d(13f), accent)
            if (target == RunningControlTarget.DASH) drawArc(accent, -90f,
                360f * (1f - engine.heat / GameplayRenderModel.MAX_HEAT).coerceIn(0f, 1f), false,
                center - Offset(radius * 0.8f, radius * 0.8f), Size(radius * 1.6f, radius * 1.6f), style = Stroke(d(2f)))
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
