// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.content.api.localizedContent

import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.foundation.common.localization.text

import kinetickk.foundation.design.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.interaction.layout.forEachRunningControlBounds
import kinetickk.ball.gameplay.interaction.layout.gameplayLayoutMode
import kinetickk.ball.gameplay.nucleus.model.clamp
import kinetickk.ball.gameplay.nucleus.model.formatRunTime
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal fun DrawScope.drawHud(engine: GameplayRenderModel, textMeasurer: TextMeasurer) {
    val layoutMode = gameplayLayoutMode(size.width, size.height, density)
    drawCharacterAbilityHud(engine, textMeasurer, layoutMode)
    if (layoutMode != GameplayLayoutMode.REGULAR) {
        drawCompactHud(engine, textMeasurer, layoutMode)
        return
    }
    val narrow = size.width / density < 700f
    val panelX = d(if (narrow) 10f else 18f)
    val panelY = d(if (narrow) 10f else 18f)
    val panelWidth = d(if (narrow) 185f else 250f)
    val panelHeight = d(if (narrow) 119f else 137f)
    val contentX = panelX + d(if (narrow) 10f else 13f)
    val barWidth = d(if (narrow) 165f else 220f)
    drawRect(Color(0xAA080A17), topLeft = Offset(panelX, panelY), size = Size(panelWidth, panelHeight))
    drawRect(DarkLine, topLeft = Offset(panelX, panelY), size = Size(panelWidth, panelHeight), style = Stroke(d(1f)))
    drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.Core, engine.content.coreShape(engine.coreShape).displayName.localizedContent(textMeasurer.language).uppercase()), contentX, panelY + d(10f), if (narrow) 9f else 11f, Cyan, weight = FontWeight.Bold)
    drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.LevelData, engine.level, engine.data, engine.nextLevelData), contentX, panelY + d(29f), if (narrow) 8f else 10f, Muted)
    drawBar(contentX, panelY + d(52f), barWidth, d(7f), engine.hp / engine.maxHp, Cyan, DarkLine)
    drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.Integrity, engine.hp.toInt()), contentX, panelY + d(63f), if (narrow) 7f else 9f, White)
    if (engine.maxShield > 0f) {
        drawBar(contentX, panelY + d(82f), barWidth, d(5f), engine.shield / engine.maxShield, Violet, DarkLine)
        drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.Shield, engine.shield.toInt(), engine.maxShield.toInt()), contentX, panelY + d(89f), 7f, Violet)
    }
    drawBar(contentX, panelY + d(111f), barWidth, d(6f), engine.heat / GameplayRenderModel.MAX_HEAT, if (engine.overheated) Red else Orange, DarkLine)
    drawLabel(textMeasurer, if (engine.overheated) textMeasurer.language.text(GameplayText.HeatOffline) else textMeasurer.language.text(GameplayText.Heat), contentX + barWidth, panelY + d(91f), if (narrow) 7f else 9f, if (engine.overheated) Red else Muted, alignRight = true)

    val timeY = d(if (narrow) 145f else 25f)
    val phaseY = d(if (narrow) 174f else 61f)
    val progressY = d(if (narrow) 193f else 83f)
    drawLabel(textMeasurer, formatRunTime(engine.elapsed), size.width * 0.5f, timeY, if (narrow) 20f else 25f, White, centered = true, weight = FontWeight.Bold)
    val phaseLabel = if (narrow) {
        textMeasurer.language.text(GameplayText.PhaseCompact, engine.rebirthLevel)
    } else {
        textMeasurer.language.text(GameplayText.Phase, engine.rebirthLevel)
    }
    drawLabel(textMeasurer, phaseLabel, size.width * 0.5f, phaseY, if (narrow) 7f else 9f, Muted, centered = true)
    val halfProgress = min(d(240f), size.width * 0.22f)
    drawBar(size.width * 0.5f - halfProgress, progressY, halfProgress * 2f, d(4f), engine.runProgress, Violet, DarkLine)
    val overdriveWidth = min(d(180f), size.width * 0.17f)
    drawBar(size.width * 0.5f - overdriveWidth, progressY + d(14f), overdriveWidth * 2f, d(5f), engine.overdriveCharge / 100f, if (engine.overdriveTime > 0f) Acid else Magenta, DarkLine)
    drawLabel(textMeasurer, if (engine.overdriveTime > 0f) textMeasurer.language.text(GameplayText.OverdriveActive) else textMeasurer.language.text(GameplayText.Overdrive, engine.overdriveCharge.toInt()), size.width * 0.5f, progressY + d(22f), 7f, if (engine.overdriveTime > 0f) Acid else Muted, centered = true)
    val polarityColor = when {
        engine.polarityStability < 0.22f -> Red
        engine.polarityStability < 0.58f -> Orange
        else -> Cyan
    }
    val polarityWidth = min(d(130f), size.width * 0.13f)
    drawBar(size.width * 0.5f - polarityWidth, progressY + d(38f), polarityWidth * 2f, d(4f), engine.polarityStability, polarityColor, DarkLine)
    val polarityLabel = if (engine.polarityStability < 0.58f) {
        textMeasurer.language.text(GameplayText.PolarityWarning, (engine.polarityStability * 100f).toInt())
    } else {
        textMeasurer.language.text(GameplayText.Polarity, (engine.polarityStability * 100f).toInt())
    }
    drawLabel(textMeasurer, polarityLabel, size.width * 0.5f, progressY + d(45f), 7f, polarityColor, centered = true)

    val right = size.width - d(if (narrow) 10f else 22f)
    val weaponColor = weaponColor(engine.weapon)
    drawLabel(textMeasurer, engine.currentWeaponDefinition.name.localizedContent(textMeasurer.language).uppercase(), right, d(if (narrow) 14f else 22f), if (narrow) 8f else 11f, weaponColor, alignRight = true, weight = FontWeight.Bold)
    val nextMastery = engine.nextWeaponMastery
    val masteryLabel = if (nextMastery == null) {
        textMeasurer.language.text(GameplayText.WeaponMastery, engine.weaponLevel, engine.currentWeaponMastery.displayLabel.localizedContent(textMeasurer.language).uppercase())
    } else {
        textMeasurer.language.text(GameplayText.NextWeaponMastery, engine.weaponLevel, engine.currentWeaponMastery.displayLabel.localizedContent(textMeasurer.language).uppercase(), nextMastery.displayLabel.localizedContent(textMeasurer.language).uppercase(), nextMastery.minimumLevel)
    }
    drawLabel(textMeasurer, masteryLabel, right, d(if (narrow) 34f else 44f), if (narrow) 6f else 8f, White, alignRight = true)
    val masteryWidth = d(if (narrow) 120f else 170f)
    drawBar(right - masteryWidth, d(if (narrow) 53f else 63f), masteryWidth, d(4f), engine.weaponMasteryProgress, weaponColor, DarkLine)
    drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.Velocity, VelocityNames[engine.velocityTier.coerceIn(VelocityNames.indices)].localizedContent(textMeasurer.language), formatCompact(engine.speed.toLong(), textMeasurer.language)), right, d(if (narrow) 64f else 77f), if (narrow) 8f else 10f, White, alignRight = true)
    drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.MassCombo, formatOneDecimal(engine.mass, textMeasurer.language), max(1, engine.combo)), right, d(if (narrow) 83f else 98f), if (narrow) 7f else 9f, Muted, alignRight = true)
    drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.PowerItemsMatter, formatOneDecimal(engine.effectiveWeaponPower, textMeasurer.language), engine.acquiredItemCount, formatCompact(engine.runMatter, textMeasurer.language)), right, d(if (narrow) 101f else 119f), if (narrow) 7f else 9f, Acid, alignRight = true)
    engine.recentItem?.let { item ->
        val recentY = d(if (narrow) 119f else 140f)
        val accent = rarityColor(item.rarity)
        drawLabel(textMeasurer, "+ ${item.name.localizedContent(textMeasurer.language).uppercase()}", right - d(20f), recentY, 7f, accent, alignRight = true)
        drawItemIcon(
            item = item,
            center = Offset(right - d(8f), recentY + d(7f)),
            radius = d(7f),
            accent = accent,
            stack = engine.itemStack(item.id),
        )
    }
    drawRelicMatrix(engine, textMeasurer, narrow, right)

    if (engine.combo >= 2 && engine.comboTime > 0f) {
        val comboProgress = clamp(engine.comboTime / max(0.001f, engine.comboWindow), 0f, 1f)
        val comboPulse = (sin(engine.elapsed * 12f) + 1f) * 0.5f
        val comboY = size.height - d(if (narrow) 112f else 88f)
        val comboWidth = d(118f)
        drawLabel(
            textMeasurer,
            textMeasurer.language.text(GameplayText.Chain, engine.combo),
            size.width * 0.5f,
            comboY,
            13f + min(5f, engine.combo * 0.12f) + comboPulse,
            if (engine.velocityTier >= 2) Acid else Cyan,
            centered = true,
            weight = FontWeight.Bold,
        )
        drawBar(size.width * 0.5f - comboWidth * 0.5f, comboY + d(25f), comboWidth, d(3f), comboProgress, Magenta, DarkLine)
    }

    val danger = engine.tetherDistance < 75f
    if (danger && engine.runGrace <= 0f) {
        drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.SingularityProximity), size.width * 0.5f, size.height - d(102f), 12f, Red, centered = true, weight = FontWeight.Bold)
    }
    if (engine.messageTime > 0f) {
        val alpha = clamp(engine.messageTime / 0.45f, 0f, 1f)
        val messageY = size.height * if (narrow) 0.34f else 0.23f
        drawLabel(textMeasurer, engine.message.localizedContent(textMeasurer.language), size.width * 0.5f, messageY, 19f, if (engine.message == "OVERHEAT") Red else White, centered = true, weight = FontWeight.Bold, alpha = alpha)
    }
    if (engine.lastImpactTime > 0f && engine.phase == GamePhase.RUNNING) {
        val alpha = clamp(engine.lastImpactTime / 0.22f, 0f, 1f)
        drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.Impact, engine.lastImpact.toInt()), d(28f), size.height - d(45f), 12f, Acid, weight = FontWeight.Bold, alpha = alpha)
        drawRect(Acid.copy(alpha = alpha * 0.65f), Offset(d(28f), size.height - d(21f)), Size(d(105f) * alpha, d(2f)))
    }
    drawControls(engine, textMeasurer)
}

private fun DrawScope.drawCompactHud(
    engine: GameplayRenderModel,
    textMeasurer: TextMeasurer,
    layoutMode: GameplayLayoutMode,
) {
    val portrait = layoutMode == GameplayLayoutMode.COMPACT_PORTRAIT
    val panelLeft = d(12f)
    val panelTop = d(if (portrait) 70f else 10f)
    val panelWidth = if (portrait) size.width - d(24f) else min(d(220f), size.width * 0.31f)
    val panelHeight = d(92f)
    val contentLeft = panelLeft + d(10f)
    val barWidth = panelWidth - d(20f)
    drawRect(Color(0xC4080A17), Offset(panelLeft, panelTop), Size(panelWidth, panelHeight))
    drawRect(DarkLine, Offset(panelLeft, panelTop), Size(panelWidth, panelHeight), style = Stroke(d(1f)))
    drawLabel(
        textMeasurer,
        textMeasurer.language.text(GameplayText.WeaponMastery, engine.level, engine.currentWeaponDefinition.name.localizedContent(textMeasurer.language).uppercase()),
        contentLeft,
        panelTop + d(9f),
        8f,
        weaponColor(engine.weapon),
        weight = FontWeight.Bold,
        maxWidth = barWidth,
    )
    drawBar(contentLeft, panelTop + d(31f), barWidth, d(7f), engine.hp / engine.maxHp, Cyan, DarkLine)
    drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.Hp, engine.hp.toInt(), engine.maxHp.toInt()), contentLeft, panelTop + d(41f), 7f, White)
    val secondaryValue = if (engine.maxShield > 0f) engine.shield / engine.maxShield else engine.heat / GameplayRenderModel.MAX_HEAT
    val secondaryColor = if (engine.maxShield > 0f) Violet else if (engine.overheated) Red else Orange
    drawBar(contentLeft, panelTop + d(61f), barWidth, d(5f), secondaryValue, secondaryColor, DarkLine)
    drawLabel(
        textMeasurer,
        if (engine.maxShield > 0f) textMeasurer.language.text(GameplayText.ShieldHeat, engine.shield.toInt(), engine.heat.toInt()) else textMeasurer.language.text(GameplayText.HeatPowerRelics, engine.heat.toInt(), formatOneDecimal(engine.effectiveWeaponPower, textMeasurer.language), engine.equippedRelics.size),
        contentLeft,
        panelTop + d(69f),
        6f,
        secondaryColor,
        maxWidth = barWidth,
    )

    if (portrait) {
        drawLabel(textMeasurer, formatRunTime(engine.elapsed), d(12f), d(14f), 18f, White, weight = FontWeight.Bold)
        drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.RebirthMatter, engine.rebirthLevel, formatCompact(engine.runMatter, textMeasurer.language)), d(12f), d(42f), 7f, Acid)
    } else {
        drawLabel(textMeasurer, formatRunTime(engine.elapsed), size.width * 0.5f, d(13f), 18f, White, centered = true, weight = FontWeight.Bold)
        drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.RebirthMatter, engine.rebirthLevel, formatCompact(engine.runMatter, textMeasurer.language)), size.width * 0.5f, d(42f), 7f, Acid, centered = true)
        val progressWidth = min(d(220f), size.width * 0.28f)
        drawBar(size.width * 0.5f - progressWidth * 0.5f, d(62f), progressWidth, d(4f), engine.runProgress, Violet, DarkLine)
    }

    forEachRunningControlBounds(
        size.width,
        size.height,
        density,
    ) { target, left, top, right, bottom ->
        val center = Offset((left + right) * 0.5f, (top + bottom) * 0.5f)
        when (target) {
            RunningControlTarget.BRAKE -> {
                drawCircle(Violet.copy(alpha = if (engine.braking) 0.28f else 0.12f), d(31f), center)
                drawCircle(Violet, d(30f), center, style = Stroke(d(1.4f)))
                drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.Brake), center.x, center.y - d(6f), 8f, Violet, centered = true, weight = FontWeight.Bold)
                drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.Hold), center.x, center.y + d(11f), 6f, White, centered = true)
            }
            RunningControlTarget.DASH -> {
                val accent = if (engine.overheated) Red else Cyan
                drawCircle(accent.copy(alpha = if (engine.dashReady) 0.18f else 0.08f), d(31f), center)
                drawCircle(accent, d(30f), center, style = Stroke(d(1.4f)))
                drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.Dash), center.x, center.y - d(6f), 8f, accent, centered = true, weight = FontWeight.Bold)
                drawLabel(textMeasurer, if (engine.overheated) textMeasurer.language.text(GameplayText.Off) else if (engine.dashReady) textMeasurer.language.text(GameplayText.Ready) else textMeasurer.language.text(GameplayText.Wait), center.x, center.y + d(11f), 6f, White, centered = true)
            }
            RunningControlTarget.PERFORMANCE -> {
                val topLeft = Offset(left, top)
                val controlSize = Size(right - left, bottom - top)
                drawRect(Color(0xC4080A17), topLeft, controlSize)
                drawRect(Violet, topLeft, controlSize, style = Stroke(d(1f)))
                drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.PerformanceShort), center.x, center.y - d(5f), 7f, Violet, centered = true, weight = FontWeight.Bold)
                drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.Metrics), center.x, center.y + d(9f), 5f, Muted, centered = true)
            }
            RunningControlTarget.PAUSE -> {
                val topLeft = Offset(left, top)
                val controlSize = Size(right - left, bottom - top)
                drawRect(Color(0xC4080A17), topLeft, controlSize)
                drawRect(Cyan, topLeft, controlSize, style = Stroke(d(1f)))
                drawLabel(textMeasurer, "Ⅱ", center.x, center.y - d(9f), 15f, Cyan, centered = true, weight = FontWeight.Bold)
                drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.Pause), center.x, center.y + d(10f), 5f, White, centered = true)
            }
        }
    }

    if (engine.combo >= 2 && engine.comboTime > 0f) {
        drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.Chain, engine.combo), size.width * 0.5f, size.height - d(51f), 11f, Acid, centered = true, weight = FontWeight.Bold)
    }
    if (engine.tetherDistance < 75f && engine.runGrace <= 0f) {
        drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.SingularityClose), size.width * 0.5f, size.height - d(102f), 8f, Red, centered = true, weight = FontWeight.Bold)
    }
    if (engine.messageTime > 0f) {
        val alpha = clamp(engine.messageTime / 0.45f, 0f, 1f)
        drawLabel(textMeasurer, engine.message.localizedContent(textMeasurer.language), size.width * 0.5f, size.height * 0.44f, 15f, if (engine.message == "OVERHEAT") Red else White, centered = true, weight = FontWeight.Bold, alpha = alpha)
    }
}

internal fun DrawScope.drawRelicMatrix(engine: GameplayRenderModel, textMeasurer: TextMeasurer, narrow: Boolean, right: Float) {
    val relicPolicy = engine.content.relicPolicy
    val slotSize = d(if (narrow) 31f else 36f)
    val gap = d(6f)
    val totalWidth = slotSize * relicPolicy.maxSlots + gap * (relicPolicy.maxSlots - 1)
    val startX = right - totalWidth
    val top = d(if (narrow) 156f else 174f)
    drawLabel(
        textMeasurer,
        textMeasurer.language.text(GameplayText.RelicMatrix, engine.equippedRelics.size, relicPolicy.maxSlots),
        right,
        top - d(15f),
        7f,
        if (engine.equippedRelics.isEmpty()) Muted else Gold,
        alignRight = true,
        weight = FontWeight.Bold,
    )
    repeat(relicPolicy.maxSlots) { index ->
        val left = startX + index * (slotSize + gap)
        val center = Offset(left + slotSize * 0.5f, top + slotSize * 0.5f)
        val equipped = engine.equippedRelics.getOrNull(index)
        val accent = equipped?.let { relicAspectColor(engine.content.relic(it.id).aspect) } ?: DarkLine
        drawRect(Color(0xB00B0D1D), Offset(left, top), Size(slotSize, slotSize))
        drawRect(accent.copy(alpha = if (equipped == null) 0.7f else 0.95f), Offset(left, top), Size(slotSize, slotSize), style = Stroke(d(1f)))
        drawLabel(textMeasurer, "${index + 1}", left + d(3f), top + d(2f), 5f, if (equipped == null) Muted else accent, weight = FontWeight.Bold)
        if (equipped == null) {
            drawCircle(DarkLine.copy(alpha = 0.46f), slotSize * 0.18f, center, style = Stroke(d(1f)))
            drawLine(DarkLine, Offset(center.x - slotSize * 0.09f, center.y), Offset(center.x + slotSize * 0.09f, center.y), d(1f))
        } else {
            drawRelicIcon(
                definition = engine.content.relic(equipped.id),
                policy = relicPolicy,
                center = center,
                radius = slotSize * 0.31f,
                rank = equipped.rank,
                time = engine.elapsed,
            )
        }
    }
}

internal fun DrawScope.drawControls(engine: GameplayRenderModel, textMeasurer: TextMeasurer) {
    val dash = Offset(size.width - d(82f), size.height - d(88f))
    val brake = Offset(size.width - d(190f), size.height - d(67f))
    val dashColor = if (engine.overheated) Red else Cyan
    val dashPulse = (sin(engine.elapsed * 8f) + 1f) * 0.5f
    if (engine.dashReady) drawCircle(Cyan.copy(alpha = 0.045f + dashPulse * 0.05f), d(58f + dashPulse * 4f), dash)
    drawCircle(dashColor.copy(alpha = if (engine.overheated) 0.06f else 0.13f + dashPulse * 0.03f), d(49f), dash)
    drawCircle(dashColor.copy(alpha = 0.75f), d(48f), dash, style = Stroke(d(1.5f)))
    drawArc(
        dashColor.copy(alpha = 0.9f),
        -90f,
        360f * clamp(1f - engine.heat / GameplayRenderModel.MAX_HEAT, 0f, 1f),
        false,
        Offset(dash.x - d(54f), dash.y - d(54f)),
        Size(d(108f), d(108f)),
        style = Stroke(d(2f), cap = StrokeCap.Round),
    )
    drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.Dash), dash.x, dash.y - d(12f), 12f, dashColor, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, if (engine.overheated) textMeasurer.language.text(GameplayText.Offline) else if (engine.dashReady) textMeasurer.language.text(GameplayText.Ready) else textMeasurer.language.text(GameplayText.Cooling), dash.x, dash.y + d(11f), 7f, if (engine.dashReady) White else Muted, centered = true)
    drawCircle(Violet.copy(alpha = if (engine.braking) 0.22f else 0.08f), d(39f), brake)
    drawCircle(Violet.copy(alpha = 0.65f), d(38f), brake, style = Stroke(d(1.2f)))
    drawLabel(textMeasurer, textMeasurer.language.text(GameplayText.Brake), brake.x, brake.y - d(10f), 9f, Violet, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "SHIFT", brake.x, brake.y + d(9f), 7f, Muted, centered = true)
}


private fun DrawScope.drawCharacterAbilityHud(
    engine: GameplayRenderModel,
    textMeasurer: TextMeasurer,
    mode: GameplayLayoutMode,
) {
    val left = d(22f)
    val top = d(when (mode) {
        GameplayLayoutMode.REGULAR -> 164f
        GameplayLayoutMode.COMPACT_PORTRAIT -> 171f
        GameplayLayoutMode.COMPACT_LANDSCAPE -> 111f
    })
    val ability = engine.characterAbility
    val status = when (engine.coreShape) {
        kinetickk.ball.content.api.CoreShape.ORB -> textMeasurer.language.text(GameplayText.Wave, (ability.charge * 100f).toInt())
        kinetickk.ball.content.api.CoreShape.PRISM -> textMeasurer.language.text(GameplayText.BarrierRam, ability.barrier.toInt(), (ability.charge * 100f).toInt())
        kinetickk.ball.content.api.CoreShape.SHARD -> textMeasurer.language.text(GameplayText.DashMarkHit)
        kinetickk.ball.content.api.CoreShape.RING -> textMeasurer.language.text(GameplayText.RingDeadZone, ability.ringRadius.toInt())
        kinetickk.ball.content.api.CoreShape.DIAMOND -> if (ability.parryWindow > 0f) textMeasurer.language.text(GameplayText.ParryWindow) else textMeasurer.language.text(GameplayText.Parry, (ability.charge * 100f).toInt())
        kinetickk.ball.content.api.CoreShape.TESSERACT -> textMeasurer.language.text(GameplayText.Lattice, ability.lattice.size)
    }
    val width = min(d(215f), size.width * 0.5f)
    drawLabel(textMeasurer, status, left, top, 7f, Cyan, maxWidth = width)
    drawBar(left, top + d(16f), width, d(3f), ability.charge, Cyan, DarkLine)
}
