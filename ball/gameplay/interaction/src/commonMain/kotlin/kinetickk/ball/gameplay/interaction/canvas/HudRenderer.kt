// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_BOSS_ROW_DP
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_CHIP_ROW_DP
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_CHIP_ROW_HEIGHT_DP
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_HUD_TOP_DP
import kinetickk.ball.gameplay.interaction.layout.REGULAR_HUD_BOTTOM_DP
import kinetickk.ball.gameplay.interaction.layout.RUNNING_CONTROL_MIN_DP
import kinetickk.ball.gameplay.interaction.layout.RunningControlTarget
import kinetickk.ball.gameplay.interaction.layout.compactHudFactor
import kinetickk.ball.gameplay.interaction.layout.forEachRunningControlBounds
import kinetickk.ball.gameplay.interaction.layout.gameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.regularHudUnit
import kinetickk.ball.gameplay.interaction.layout.runningHudMargin
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.localization.HudRedesignText
import kinetickk.ball.gameplay.nucleus.model.formatRunTime
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The running HUD (SPEC section 7.1): level badge + Data bar top-left, timer (and boss bar) top-center,
 * economy and pause top-right, chain on the right edge, integrity/shield/dash/heat bottom-left,
 * speed gauge bottom-center, relics and weapon bottom-right, world-anchored halos and state
 * effects. Phones use the same modules at the Mobile board sizes. Draws no text warnings.
 */
internal fun DrawScope.drawHud(
    engine: GameplayRenderModel,
    textMeasurer: TextMeasurer,
    renderTime: Float = 0f,
    shakeX: Float = 0f,
    shakeY: Float = 0f,
    memory: HudPresentationMemory? = null,
    trialInfoOpen: Boolean = false,
) {
    val frame = HudScratch.frame.update(size.width, size.height, density)
    HudLayoutProbe.begin()
    val boss = HudScratch.boss.select(engine)
    memory?.observe(engine, renderTime, boss)
    val critical = isIntegrityCritical(engine.hp, engine.maxHp)

    drawHudWorldAnchors(engine, textMeasurer, renderTime, shakeX, shakeY, memory, critical)
    drawHudScreenStates(engine, textMeasurer.roles, renderTime, memory, critical)

    drawDataLine(engine, frame)
    val badgeBottom = drawLevelCluster(engine, textMeasurer, frame, renderTime, memory)
    drawClockAndBoss(engine, textMeasurer, frame, boss, renderTime, memory)
    drawEconomy(engine, textMeasurer, frame, badgeBottom)
    drawChain(engine, textMeasurer, frame)
    drawCoreStatus(engine, textMeasurer, frame, renderTime, memory, critical)
    drawKineticGauge(engine, textMeasurer, frame, renderTime, memory)
    drawLoadout(engine, textMeasurer, frame)
    drawTrialPanel(engine, textMeasurer, renderTime, trialInfoOpen)
    drawControls(engine, textMeasurer)
}

/** Integrity reads critical at or below a fifth of its maximum (`HUD-Critical` board). */
internal fun isIntegrityCritical(hp: Float, maxHp: Float): Boolean = hp <= maxHp.coerceAtLeast(1f) * 0.2f

/** Integrity cells of 20 points each (SPEC Meter component), bounded for very large pools. */
internal fun integritySegments(maxHp: Float): Int = ceil(maxHp / 20f).toInt().coerceIn(1, 40)

/** Shield cells of 20 points each, at most six. */
internal fun shieldCells(maxShield: Float): Int = ceil(maxShield / 20f).toInt().coerceIn(1, 6)

internal fun filledShieldCells(shield: Float, maxShield: Float, cells: Int): Int =
    if (maxShield <= 0f || shield <= 0f) 0 else ceil(shield / maxShield * cells - 0.001f).toInt().coerceIn(0, cells)

/** Dash charge pips: the heat headroom counted in dash costs, rounded (a fresh core's chain length). */
internal fun dashChargeCapacity(dashHeatCost: Float): Int =
    floor(GameplayRenderModel.MAX_HEAT / dashHeatCost.coerceAtLeast(1f) + 0.5f).toInt().coerceIn(1, 8)

/** Dashes the core can chain from [heat] before overheating (0 while overheated). */
internal fun dashChargesReady(heat: Float, dashHeatCost: Float, overheated: Boolean): Int {
    if (overheated) return 0
    val ready = floor((GameplayRenderModel.MAX_HEAT - heat) / dashHeatCost.coerceAtLeast(1f) + 0.5f).toInt()
    return ready.coerceIn(0, dashChargeCapacity(dashHeatCost))
}

/** Lit ticks of the velocity ladder for [speed] on the shared logarithmic speed scale. */
internal fun velocityLadderLit(speed: Float, count: Int): Int = (speedVisualRatio(speed) * count).roundToInt().coerceIn(0, count)

/** Horizontal stretch of the speed number by velocity tier (1 at rest, 1.34 at the top tier). */
internal fun speedStretch(velocityTier: Int): Float = 1f + velocityTier.coerceIn(0, 4) / 4f * 0.34f

/** Draw-thread scratch objects reused every frame. */
internal object HudScratch {
    val frame = HudFrame()
    val boss = BossTarget()
    val timer = HudNumberText { formatRunTime(it.toFloat()) }
    val matter = HudKeyedText()
    val keys = HudNumberText { it.toString() }
    val weaponLevel = HudKeyedText()
    val bossName = HudKeyedText()
    val label = HudKeyedText()
    val dashLabel = HudKeyedText()
    val brakeLabel = HudKeyedText()
    val overheat = HudKeyedText()
    var aspectContent: GameplayContentSnapshot? = null
    var linkRelics: List<EquippedRelic>? = null
    var linkContent: GameplayContentSnapshot? = null
    var links: List<OverlaySynergyLink> = emptyList()
    val brackets = HudBracketLevels()
    val aspects = arrayOfNulls<RelicAspect>(RelicId.entries.size)
}

/** Per-frame layout scalars of the running HUD (rewritten every frame, draw-thread confined). */
internal class HudFrame {
    var mode: GameplayLayoutMode = GameplayLayoutMode.REGULAR
        private set
    var width = 0f
        private set
    var height = 0f
        private set
    var density = 1f
        private set

    /** px per reference dp of the board this mode follows. */
    var unit = 1f
        private set

    /** Multiplier for board font sizes (sp): 1 on phones and full-size desktop windows. */
    var textFactor = 1f
        private set
    var margin = 0f
        private set

    /** Horizontal factor of the phone layouts relative to their boards. */
    var factor = 1f
        private set

    /** Top inset of the HUD (portrait status bar). */
    var top = 0f
        private set

    val regular: Boolean get() = mode == GameplayLayoutMode.REGULAR
    val portrait: Boolean get() = mode == GameplayLayoutMode.COMPACT_PORTRAIT

    fun update(width: Float, height: Float, density: Float): HudFrame {
        this.width = width
        this.height = height
        this.density = density.coerceAtLeast(1f)
        mode = gameplayLayoutMode(width, height, density)
        unit = if (mode == GameplayLayoutMode.REGULAR) regularHudUnit(width, density) else density.coerceAtLeast(1f)
        textFactor = unit / density.coerceAtLeast(1f)
        margin = runningHudMargin(width, height, density)
        factor = when (mode) {
            GameplayLayoutMode.REGULAR -> 1f
            GameplayLayoutMode.COMPACT_LANDSCAPE -> compactHudFactor(width, density, portrait = false)
            GameplayLayoutMode.COMPACT_PORTRAIT -> compactHudFactor(width, density, portrait = true)
        }
        top = if (mode == GameplayLayoutMode.COMPACT_PORTRAIT) PORTRAIT_HUD_TOP_DP * unit else 0f
        return this
    }

    fun u(value: Float): Float = value * unit
    fun t(size: Float): Float = size * textFactor

    /**
     * Top of the bottom clusters' reserve (integrity, speed, loadout; on portrait phones also the
     * touch buttons): the floor for transient HUD overlays such as the trial rules.
     */
    val bottomClustersTop: Float
        get() = height - u(
            when (mode) {
                GameplayLayoutMode.REGULAR -> 136f
                GameplayLayoutMode.COMPACT_LANDSCAPE -> 84f
                GameplayLayoutMode.COMPACT_PORTRAIT -> 252f
            },
        )

    /** Top of the chain counter on the right edge (REGULAR). */
    val chainTop: Float get() = max(u(76f), min(u(160f), height * 0.2f))

    /** Center of the pause button visual. */
    val pauseCenterX: Float get() = width - margin - if (regular) u(20f) else u(22f)

    /** Center of the Build button (Compose) beside pause on desktop and landscape phones. */
    val buildCenterX: Float
        get() = if (regular) pauseCenterX - max(u(48f), RUNNING_CONTROL_MIN_DP * density) else pauseCenterX - u(104f)
}

private fun DrawScope.drawDataLine(engine: GameplayRenderModel, frame: HudFrame) {
    val fraction = (engine.data.toFloat() / engine.nextLevelData.coerceAtLeast(1)).coerceIn(0f, 1f)
    val height = d(3f)
    drawRect(Kk.Bone.copy(alpha = 0.08f), Offset(0f, frame.top), Size(frame.width, height))
    if (fraction > 0f) drawRect(Kk.Bone, Offset(0f, frame.top), Size(frame.width * fraction, height))
}

/** Level badge (+ thin Data bar on desktop); returns the bottom of the cluster. */
private fun DrawScope.drawLevelCluster(
    engine: GameplayRenderModel,
    measurer: TextMeasurer,
    frame: HudFrame,
    renderTime: Float,
    memory: HudPresentationMemory?,
): Float {
    val language = measurer.language
    val label = HudScratch.label.of(language, 0L) { language.text(HudRedesignText.LevelLabel) }
    val slam = if (memory != null) memory.levelSlam(renderTime) else 1f
    val level = engine.level.coerceAtLeast(0)
    // The numeral is display type (fixed size); the "Lvl" label follows the text size.
    val display = HudMeasurers.display(measurer)
    val growth = hudUiScale(measurer.scale)
    if (frame.regular) {
        val badge = drawKkLevelBadge(display, Offset(frame.margin, frame.u(26f)), level, label, renderTime, slam,
            labelSize = frame.t(12f) * growth, numberSize = frame.t(30f))
        val fraction = (engine.data.toFloat() / engine.nextLevelData.coerceAtLeast(1)).coerceIn(0f, 1f)
        val top = badge.bottom + frame.u(10f)
        val bar = HudDrawCache.rect(HudRect.DATA_BAR, badge.left + frame.u(4f), top, badge.left + frame.u(122f), top + frame.u(4f))
        drawKkMeter(bar, fraction, Kk.Bone, measurer.roles, background = Kk.Bone.copy(alpha = 0.12f))
        HudLayoutProbe.record(HudBlock.BADGE, badge.left, badge.top, max(badge.right, bar.right), bar.bottom)
        return bar.bottom
    }
    val badgeSize = kkLevelBadgeSize(display, level, density, label, PHONE_BADGE_LABEL_SP * growth, PHONE_BADGE_NUMBER_SP)
    val top = if (frame.portrait) portraitRowCenter(frame) - badgeSize.height * 0.5f else frame.u(12f)
    val badge = drawKkLevelBadge(display, Offset(frame.margin, top), level, label, renderTime, slam,
        labelSize = PHONE_BADGE_LABEL_SP * growth, numberSize = PHONE_BADGE_NUMBER_SP)
    HudLayoutProbe.record(HudBlock.BADGE, badge.left, badge.top, badge.right, badge.bottom)
    return badge.bottom
}

private const val PHONE_BADGE_LABEL_SP = 9f
private const val PHONE_BADGE_NUMBER_SP = 19f

private fun portraitRowCenter(frame: HudFrame): Float = frame.top + frame.u(11f + 20f)

private fun DrawScope.drawClockAndBoss(
    engine: GameplayRenderModel,
    measurer: TextMeasurer,
    frame: HudFrame,
    boss: BossTarget,
    renderTime: Float,
    memory: HudPresentationMemory?,
) {
    val roles = measurer.roles
    val architect = boss.type == EnemyType.ARCHITECT
    val text = HudScratch.timer.of(engine.elapsed.toLong())
    // Display type: the same size at every text-size setting.
    val layout = HudDrawCache.layout(HudText.TIMER, HudMeasurers.display(measurer), text,
        measurer.typography.wideStyle(HudTopGeometry.clockSize(frame), tabular = true, lineHeightEm = WIDE_LINE_HEIGHT_EM))
    val color = if (architect) roles.threat else Kk.Bone
    val x = frame.width * 0.5f
    val clockTop = HudTopGeometry.clockTop(frame)
    drawKkText(layout, x, clockTop, color, KkAlign.CENTER)
    HudLayoutProbe.record(HudBlock.CLOCK, x - layout.size.width * 0.5f, clockTop, x + layout.size.width * 0.5f, clockTop + layout.kkBoxHeight)
    if (boss.id < 0) return
    val ghost = if (memory != null) memory.bossGhost(renderTime) else 0f
    drawBossBar(measurer, frame, boss, HudTopGeometry.bossTop(frame), HudTopGeometry.bossBarWidth(frame, architect), ghost)
}

/** Line height of the wide display type (`.hud-num`, `.t-wide`). */
internal const val WIDE_LINE_HEIGHT_EM = 0.9f

/** Line height of the condensed type (`.t-cond`). */
internal const val COND_LINE_HEIGHT_EM = 0.86f

/**
 * Geometry of the HUD's top-center column (clock, then the elite / Architect block under it),
 * shared by the renderer and the world keep-out ([WorldHudKeepOut]) so world labels and edge
 * markers follow the HUD at every text size. px for the [HudFrame]'s viewport.
 */
internal object HudTopGeometry {
    /** Clock size in sp (display type: never scaled by the text-size setting). */
    fun clockSize(frame: HudFrame): Float = when {
        frame.regular -> frame.t(44f)
        frame.portrait -> 28f
        else -> 26f
    }

    /** Height of the clock's line box in px. */
    fun clockHeight(frame: HudFrame): Float = clockSize(frame) * WIDE_LINE_HEIGHT_EM * frame.density

    fun clockTop(frame: HudFrame): Float = when {
        frame.portrait -> portraitRowCenter(frame) - clockHeight(frame) * 0.5f
        frame.regular -> frame.u(18f)
        else -> frame.u(8f)
    }

    /** Top of the boss block: under the clock, or its own reserved row on portrait phones. */
    fun bossTop(frame: HudFrame): Float = when {
        frame.regular -> clockTop(frame) + clockHeight(frame) + frame.u(20f)
        // Portrait: its own row under the chips and chain (the chips share the row below the clock).
        frame.portrait -> frame.top + frame.u(PORTRAIT_BOSS_ROW_DP)
        else -> clockTop(frame) + clockHeight(frame) + frame.u(6f)
    }

    fun bossBarWidth(frame: HudFrame, architect: Boolean): Float = when {
        frame.regular -> min(frame.u(if (architect) 700f else 320f), frame.width - frame.margin * 2f - frame.u(660f))
        frame.portrait -> min(frame.width - frame.margin * 2f, frame.u(320f))
        else -> min(frame.u(260f) * frame.factor, frame.width * 0.3f)
    }.coerceAtLeast(frame.u(140f))

    /** Board size (sp) of the boss name; UI text, so it grows with the text-size setting. */
    fun bossNameSize(frame: HudFrame, architect: Boolean): Float = when {
        frame.regular -> frame.t(if (architect) 20f else 22f)
        else -> if (architect) 14f else 16f
    }

    fun bossNameLineHeight(architect: Boolean): Float = if (architect) WIDE_LINE_HEIGHT_EM else COND_LINE_HEIGHT_EM

    fun bossNameGap(frame: HudFrame): Float = frame.u(if (frame.regular) 9f else 5f)

    fun bossBarHeight(frame: HudFrame, architect: Boolean): Float =
        frame.u(if (architect) (if (frame.regular) 14f else 8f) else (if (frame.regular) 8f else 6f))

    /** Bottom of the boss block at the text size [textScale] (the name is the largest it gets). */
    fun bossBottom(frame: HudFrame, architect: Boolean, textScale: Float): Float {
        val name = bossNameSize(frame, architect) * bossNameLineHeight(architect) * frame.density * hudUiScale(textScale)
        return bossTop(frame) + name + bossNameGap(frame) + bossBarHeight(frame, architect)
    }
}

private fun DrawScope.drawBossBar(
    measurer: TextMeasurer,
    frame: HudFrame,
    boss: BossTarget,
    top: Float,
    width: Float,
    ghost: Float,
) {
    val roles = measurer.roles
    val language = measurer.language
    val architect = boss.type == EnemyType.ARCHITECT
    val center = frame.width * 0.5f
    val name = HudScratch.bossName.of(language, if (architect) 1L else 0L) {
        if (architect) language.text(HudRedesignText.ArchitectTitle) else bossDisplayName(boss.type, language)
    }
    val nameSize = HudTopGeometry.bossNameSize(frame, architect)
    val lineHeight = HudTopGeometry.bossNameLineHeight(architect)
    val iconSize = if (architect) 0f else frame.u(if (frame.regular) 18f else 14f)
    val gap = if (architect) 0f else frame.u(8f)
    // UI text: grows with the text size, shrinking to fit the bar's width rather than being cut.
    val layout = HudDrawCache.fitted(HudText.BOSS_NAME, HudMeasurers.ui(measurer), name, nameSize, width - iconSize - gap,
        uppercase = true) { size ->
        if (architect) measurer.typography.wideStyle(size, lineHeightEm = lineHeight) else measurer.typography.condStyle(size, lineHeightEm = lineHeight)
    }
    val rowWidth = iconSize + gap + layout.size.width
    val rowLeft = center - rowWidth * 0.5f
    val rowCenter = top + layout.kkBoxHeight * 0.5f
    if (!architect) drawKkIcon(KkIcon.SYSTEM_ELITE, Offset(rowLeft + iconSize * 0.5f, rowCenter), iconSize, roles.threat)
    drawKkText(layout, rowLeft + iconSize + gap, rowCenter, Kk.Bone, valign = KkVAlign.CENTER)
    val barTop = top + layout.kkBoxHeight + HudTopGeometry.bossNameGap(frame)
    val left = center - width * 0.5f
    val barHeight = HudTopGeometry.bossBarHeight(frame, architect)
    HudLayoutProbe.record(HudBlock.BOSS, min(left, rowLeft), top, max(left + width, rowLeft + rowWidth), barTop + barHeight)
    if (!architect) {
        val height = barHeight
        val bar = HudDrawCache.rect(HudRect.BOSS_0, left, barTop, left + width, barTop + height)
        drawKkMeter(bar, boss.integrity, roles.threat, roles, background = roles.threat.copy(alpha = 0.18f),
            segments = 16, ghost = ghost, ghostColor = Kk.Bone, threat = true)
        return
    }
    // The Architect: three equal parts that empty from the left.
    val height = barHeight
    val partGap = frame.u(6f)
    val partWidth = (width - partGap * 2f) / 3f
    for (part in 0 until 3) {
        val partLeft = left + part * (partWidth + partGap)
        val fill = (boss.integrity * 3f - (2 - part)).coerceIn(0f, 1f)
        val partGhost = ((boss.integrity + ghost) * 3f - (2 - part)).coerceIn(0f, 1f) - fill
        val slot = when (part) {
            0 -> HudRect.BOSS_0
            1 -> HudRect.BOSS_1
            else -> HudRect.BOSS_2
        }
        val bar = HudDrawCache.rect(slot, partLeft, barTop, partLeft + partWidth, barTop + height)
        drawKkMeter(bar, fill, roles.threat, roles, background = roles.threat.copy(alpha = 0.18f),
            ghost = partGhost, ghostColor = Kk.Bone, threat = true)
    }
}

/** Display name of a tracked elite: the content phrase for its enemy type ("Elite"). */
private fun bossDisplayName(type: EnemyType?, language: AppLanguage): String =
    (if (type == EnemyType.ELITE) "Elite" else "Architect").localizedContent(language)

/** Matter and key chips (and their phone forms). */
private fun DrawScope.drawEconomy(engine: GameplayRenderModel, hostMeasurer: TextMeasurer, frame: HudFrame, badgeBottom: Float) {
    val roles = hostMeasurer.roles
    val language = hostMeasurer.language
    // Chip values are UI text: they follow the text size (board size at the default).
    val measurer = HudMeasurers.ui(hostMeasurer)
    val matter = HudScratch.matter.of(language, engine.runMatter) { "+" + formatCompact(engine.runMatter, language) }
    val keys = if (engine.keys > 0) HudScratch.keys.of(engine.keys.toLong()) else null
    when (frame.mode) {
        GameplayLayoutMode.REGULAR -> {
            val height = frame.u(36f)
            val top = frame.u(24f)
            var right = frame.buildCenterX - frame.u(20f + 10f)
            if (keys != null) right = drawHudChip(measurer, HudPath.KEY_CHIP, HudText.KEYS, keys, right, top, height, frame.t(21f), KkIcon.SYSTEM_KEY, roles) - frame.u(8f)
            drawHudChip(measurer, HudPath.MATTER_CHIP, HudText.MATTER, matter, right, top, height, frame.t(21f), null, roles)
        }
        GameplayLayoutMode.COMPACT_LANDSCAPE -> {
            // Inline with the badge: gem + value, key icon + count.
            val centerY = frame.u(12f) + (badgeBottom - frame.u(12f)) * 0.5f
            var x = frame.margin + kkLevelBadgeSize(HudMeasurers.display(hostMeasurer), engine.level.coerceAtLeast(0), density,
                HudScratch.label.current(), PHONE_BADGE_LABEL_SP * hudUiScale(hostMeasurer.scale), PHONE_BADGE_NUMBER_SP).width + frame.u(10f)
            drawKkGem(Offset(x + frame.u(5f), centerY), roles.you, 10f)
            x += frame.u(10f + 8f)
            val layout = HudDrawCache.layout(HudText.MATTER, measurer, matter, measurer.typography.condStyle(17f, tabular = true, lineHeightEm = 1f))
            drawKkText(layout, x, centerY, Kk.Bone, valign = KkVAlign.CENTER)
            HudLayoutProbe.record(HudBlock.MATTER_CHIP, x - frame.u(18f), centerY - layout.kkBoxHeight * 0.5f, x + layout.size.width,
                centerY + layout.kkBoxHeight * 0.5f)
            x += layout.size.width + frame.u(12f)
            if (keys != null) {
                drawKkIcon(KkIcon.SYSTEM_KEY, Offset(x + frame.u(7f), centerY), frame.u(14f), Kk.Bone)
                val keyLayout = HudDrawCache.layout(HudText.KEYS, measurer, keys, measurer.typography.condStyle(17f, tabular = true, lineHeightEm = 1f))
                drawKkText(keyLayout, x + frame.u(19f), centerY, Kk.Bone, valign = KkVAlign.CENTER)
                HudLayoutProbe.record(HudBlock.KEY_CHIP, x, centerY - keyLayout.kkBoxHeight * 0.5f, x + frame.u(19f) + keyLayout.size.width,
                    centerY + keyLayout.kkBoxHeight * 0.5f)
            }
        }
        GameplayLayoutMode.COMPACT_PORTRAIT -> {
            val top = frame.top + frame.u(PORTRAIT_CHIP_ROW_DP)
            val height = frame.u(PORTRAIT_CHIP_ROW_HEIGHT_DP)
            var left = frame.margin
            left = drawHudChipFromLeft(measurer, HudPath.MATTER_CHIP, HudText.MATTER, matter, left, top, height, 15f, null, roles) + frame.u(6f)
            if (keys != null) drawHudChipFromLeft(measurer, HudPath.KEY_CHIP, HudText.KEYS, keys, left, top, height, 15f, KkIcon.SYSTEM_KEY, roles)
        }
    }
}

/** Header chip (`.chip`) right-aligned at [right]; returns its left edge. */
private fun DrawScope.drawHudChip(
    measurer: TextMeasurer,
    path: HudPath,
    text: HudText,
    value: String,
    right: Float,
    top: Float,
    height: Float,
    fontSize: Float,
    icon: KkIcon?,
    roles: KkRolePalette,
): Float {
    val width = hudChipWidth(measurer, text, value, height, fontSize, icon)
    drawHudChipAt(measurer, path, text, value, right - width, top, width, height, fontSize, icon, roles)
    return right - width
}

private fun DrawScope.drawHudChipFromLeft(
    measurer: TextMeasurer,
    path: HudPath,
    text: HudText,
    value: String,
    left: Float,
    top: Float,
    height: Float,
    fontSize: Float,
    icon: KkIcon?,
    roles: KkRolePalette,
): Float {
    val width = hudChipWidth(measurer, text, value, height, fontSize, icon)
    drawHudChipAt(measurer, path, text, value, left, top, width, height, fontSize, icon, roles)
    return left + width
}

private fun DrawScope.hudChipWidth(measurer: TextMeasurer, text: HudText, value: String, height: Float, fontSize: Float, icon: KkIcon?): Float {
    val layout = HudDrawCache.layout(text, measurer, value, chipStyle(measurer, fontSize), uppercase = true)
    val k = height / d(34f)
    val lead = (if (icon == null) d(14f) else d(16f)) * k
    return d(10f) * k + lead + d(8f) * k + layout.size.width + d(14f) * k
}

private fun chipStyle(measurer: TextMeasurer, fontSize: Float) =
    measurer.typography.condStyle(fontSize, trackingEm = 0.02f, tabular = true, lineHeightEm = 1f)

private fun DrawScope.drawHudChipAt(
    measurer: TextMeasurer,
    path: HudPath,
    text: HudText,
    value: String,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    fontSize: Float,
    icon: KkIcon?,
    roles: KkRolePalette,
) {
    val k = height / d(34f)
    HudLayoutProbe.record(if (path == HudPath.KEY_CHIP) HudBlock.KEY_CHIP else HudBlock.MATTER_CHIP, left, top, left + width, top + height)
    drawPath(HudDrawCache.paths.slab(path.ordinal, left, top, left + width, top + height, d(8f) * k), Kk.Ink2.copy(alpha = 0.85f))
    val lead = (if (icon == null) d(14f) else d(16f)) * k
    val cy = top + height * 0.5f
    val leadX = left + d(10f) * k + lead * 0.5f
    if (icon == null) drawKkGem(Offset(leadX, cy), roles.you, 14f * k) else drawKkIcon(icon, Offset(leadX, cy), lead, Kk.Bone)
    val layout = HudDrawCache.layout(text, measurer, value, chipStyle(measurer, fontSize), uppercase = true)
    drawKkText(layout, left + d(10f) * k + lead + d(8f) * k, cy, Kk.Bone, valign = KkVAlign.CENTER)
}

/** `×N` chain counter with its draining window bar; hidden without a live combo. */
private fun DrawScope.drawChain(engine: GameplayRenderModel, hostMeasurer: TextMeasurer, frame: HudFrame) {
    if (engine.combo < 2 || engine.comboTime <= 0f) return
    // Display type: the same size at every text-size setting.
    val measurer = HudMeasurers.display(hostMeasurer)
    val hot = engine.combo >= 20
    val color = if (hot) measurer.roles.you else Kk.Bone
    val tracking = CHAIN_TRACKING + colorTracking(if (hot) 1 else 0)
    val value = engine.combo.toLong()
    val window = engine.comboTime / engine.comboWindow.coerceAtLeast(0.001f)
    when (frame.mode) {
        GameplayLayoutMode.REGULAR -> {
            val style = measurer.typography.condStyle(frame.t(60f), tabular = true, trackingEm = tracking, lineHeightEm = COND_LINE_HEIGHT_EM)
            val right = frame.width - frame.margin
            val top = frame.chainTop
            val width = drawKkTabularNumber(measurer, value, style, right, top, color, KkAlign.END, prefix = CHAIN_PREFIX)
            val barTop = top + digitBoxHeight(measurer, style) + frame.u(6f)
            val bar = HudDrawCache.rect(HudRect.CHAIN, right - frame.u(120f), barTop, right, barTop + frame.u(4f))
            drawKkMeter(bar, window, color, measurer.roles)
            HudLayoutProbe.record(HudBlock.CHAIN, min(right - width, bar.left), top, right, bar.bottom)
        }
        GameplayLayoutMode.COMPACT_LANDSCAPE -> {
            val style = measurer.typography.condStyle(26f, tabular = true, trackingEm = tracking, lineHeightEm = COND_LINE_HEIGHT_EM)
            val right = frame.buildCenterX - frame.u(24f + 10f)
            val center = frame.u(30f)
            val width = drawKkTabularNumber(measurer, value, style, right, center, color, KkAlign.END, KkVAlign.CENTER, prefix = CHAIN_PREFIX)
            val barTop = center + digitBoxHeight(measurer, style) * 0.5f + frame.u(3f)
            val bar = HudDrawCache.rect(HudRect.CHAIN, right - width, barTop, right, barTop + frame.u(2f))
            drawKkMeter(bar, window, color, measurer.roles)
            HudLayoutProbe.record(HudBlock.CHAIN, right - width, center - digitBoxHeight(measurer, style) * 0.5f, right, bar.bottom)
        }
        GameplayLayoutMode.COMPACT_PORTRAIT -> {
            val style = measurer.typography.condStyle(30f, tabular = true, trackingEm = tracking, lineHeightEm = COND_LINE_HEIGHT_EM)
            val right = frame.width - frame.margin
            val center = frame.top + frame.u(PORTRAIT_CHIP_ROW_DP + PORTRAIT_CHIP_ROW_HEIGHT_DP * 0.5f)
            val width = drawKkTabularNumber(measurer, value, style, right, center, color, KkAlign.END, KkVAlign.CENTER, prefix = CHAIN_PREFIX)
            val barTop = center + digitBoxHeight(measurer, style) * 0.5f + frame.u(3f)
            val bar = HudDrawCache.rect(HudRect.CHAIN, right - width, barTop, right, barTop + frame.u(2f))
            drawKkMeter(bar, window, color, measurer.roles)
            HudLayoutProbe.record(HudBlock.CHAIN, right - width, center - digitBoxHeight(measurer, style) * 0.5f, right, bar.bottom)
        }
    }
}

private const val CHAIN_PREFIX = "×"
private const val CHAIN_TRACKING = 0.005f

/**
 * A negligible extra tracking per color [variant] for numbers drawn in more than one color. The
 * text measurer shares one paragraph between styles that differ only in color, and painting a
 * paragraph in another color than last time reshapes it (allocating every frame when a value
 * flips between colors); a distinct style per color keeps each digit layout in one color.
 */
internal fun colorTracking(variant: Int): Float = variant * 0.0005f

/** CSS line-box height of a digit in [style] (numbers drawn with [drawKkTabularNumber]). */
private fun digitBoxHeight(measurer: TextMeasurer, style: androidx.compose.ui.text.TextStyle): Float =
    measureKkText(measurer, "0", style).kkBoxHeight

/** Bottom-left: integrity number, segmented integrity bar, shield cells, dash pips, heat, ability. */
private fun DrawScope.drawCoreStatus(
    engine: GameplayRenderModel,
    hostMeasurer: TextMeasurer,
    frame: HudFrame,
    renderTime: Float,
    memory: HudPresentationMemory?,
    critical: Boolean,
) {
    // The integrity number is display type: the same size at every text-size setting.
    val measurer = HudMeasurers.display(hostMeasurer)
    val roles = measurer.roles
    val left = frame.margin
    val width: Float
    val bottom: Float
    val numberSize: Float
    val barHeight: Float
    val pipWidth: Float
    val pipHeight: Float
    val heatHeight: Float
    val gapRow: Float
    when (frame.mode) {
        GameplayLayoutMode.REGULAR -> {
            width = min(frame.u(340f), (frame.width - frame.margin * 2f) * 0.3f)
            bottom = frame.height - frame.u(REGULAR_HUD_BOTTOM_DP)
            numberSize = frame.t(44f)
            barHeight = frame.u(12f)
            pipWidth = 16f * frame.textFactor
            pipHeight = 10f * frame.textFactor
            heatHeight = frame.u(6f)
            gapRow = frame.u(10f)
        }
        GameplayLayoutMode.COMPACT_LANDSCAPE -> {
            width = frame.u(200f) * frame.factor
            bottom = frame.height - frame.u(14f)
            numberSize = 28f
            barHeight = frame.u(9f)
            pipWidth = 12f
            pipHeight = 8f
            heatHeight = frame.u(5f)
            gapRow = frame.u(7f)
        }
        GameplayLayoutMode.COMPACT_PORTRAIT -> {
            width = frame.u(170f) * frame.factor
            bottom = frame.height - frame.u(36f + 80f + 18f)
            numberSize = 28f
            barHeight = frame.u(9f)
            pipWidth = 12f
            pipHeight = 8f
            heatHeight = frame.u(4f)
            gapRow = frame.u(5f)
        }
    }
    // Dash pips + heat bar row.
    val capacity = dashChargeCapacity(engine.dashHeatCost)
    val ready = dashChargesReady(engine.heat, engine.dashHeatCost, engine.overheated)
    val rowHeight = max(d(pipHeight), heatHeight)
    val rowTop = bottom - rowHeight
    val pipsWidth = drawKkPips(Offset(left, rowTop + (rowHeight - d(pipHeight)) * 0.5f), capacity, ready,
        if (engine.overheated) Kk.Ink4 else roles.you, sizeDp = pipHeight, widthDp = pipWidth, gapDp = 3f, emptyColor = Kk.Ink4)
    if (engine.dashPhaseTime > 0f && ready < capacity) {
        // The pip a dash just spent flashes out.
        val spend = (engine.dashPhaseTime / DASH_PHASE_SECONDS).coerceIn(0f, 1f)
        val x = left + ready * (d(pipWidth) + d(3f))
        drawPath(HudDrawCache.paths.sheared(HudPath.DASH_SPEND.ordinal, x, rowTop + (rowHeight - d(pipHeight)) * 0.5f,
            x + d(pipWidth), rowTop + (rowHeight + d(pipHeight)) * 0.5f), roles.you, alpha = spend)
    }
    val heatLeft = left + pipsWidth + frame.u(10f)
    val heat = HudDrawCache.rect(HudRect.HEAT, heatLeft, rowTop + (rowHeight - heatHeight) * 0.5f, left + width,
        rowTop + (rowHeight + heatHeight) * 0.5f)
    drawKkMeter(heat, engine.heat / GameplayRenderModel.MAX_HEAT, roles.heat, roles,
        stripes = if (engine.overheated) KkMeterStripes.THREAT else KkMeterStripes.NONE, time = renderTime,
        threat = engine.overheated)

    // Segmented integrity bar with hit ghost; pulses at heart rate when critical.
    val barBottom = rowTop - gapRow
    val barTop = barBottom - barHeight
    val integrity = (engine.hp / engine.maxHp.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val ghost = if (memory != null) memory.integrityGhost(renderTime) else 0f
    val pulse = if (critical) heartbeat(renderTime) else 1f
    val barColor = if (critical) roles.threat.copy(alpha = 0.45f + 0.55f * pulse) else Kk.Bone
    val bar = HudDrawCache.rect(HudRect.INTEGRITY, left, barTop, left + width, barBottom)
    drawKkMeter(bar, integrity, barColor, roles, segments = integritySegments(engine.maxHp), ghost = ghost,
        ghostColor = roles.threat, threat = critical)

    // Number row: integrity number (split channels while a hit ghost drains), shield cells, ability.
    val numberBottom = barTop - frame.u(if (frame.regular) 8f else 5f)
    val value = engine.hp.coerceAtLeast(0f).toLong()
    val style = measurer.typography.wideStyle(numberSize, tabular = true, trackingEm = colorTracking(if (critical) 3 else 0))
    val numberTop = numberBottom - digitBoxHeight(measurer, style)
    val numberColor = if (critical) roles.threat else Kk.Bone
    if (ghost > 0f) {
        // Split channels while the hit ghost drains, each copy with its own digit layouts.
        val split = d(2f) * (ghost * 4f).coerceAtMost(1f)
        drawKkTabularNumber(measurer, value, measurer.typography.wideStyle(numberSize, tabular = true, trackingEm = colorTracking(1)),
            left - split, numberTop, roles.threat, alpha = 0.7f)
        drawKkTabularNumber(measurer, value, measurer.typography.wideStyle(numberSize, tabular = true, trackingEm = colorTracking(2)),
            left + split, numberTop, roles.shield, alpha = 0.55f)
    }
    val numberWidth = drawKkTabularNumber(measurer, value, style, left, numberTop, numberColor)
    HudLayoutProbe.record(HudBlock.INTEGRITY, left, numberTop, max(left + width, left + numberWidth), bottom)

    val right = left + width
    var anchorBottom = numberBottom - frame.u(if (frame.regular) 6f else 4f)
    if (engine.maxShield > 0f) {
        val cells = shieldCells(engine.maxShield)
        val filled = filledShieldCells(engine.shield, engine.maxShield, cells)
        val cellWidth = if (frame.regular) 24f * frame.textFactor else 16f
        val cellHeight = if (frame.regular) 9f * frame.textFactor else 7f
        val gap = if (frame.regular) 3f else 2f
        val total = cells * d(cellWidth) + (cells - 1) * d(gap)
        drawKkPips(Offset(right - total, anchorBottom - d(cellHeight)), cells, filled, roles.shield, sizeDp = cellHeight,
            widthDp = cellWidth, gapDp = gap, emptyColor = roles.shield, emptyOutlined = true)
        anchorBottom -= d(cellHeight) + frame.u(7f)
    }
    drawAbilityMeter(engine, frame, roles, right, anchorBottom)
}

private const val DASH_PHASE_SECONDS = 0.24f

/** `kk-heartbeat` opacity over one second: 0.45, 1, 0.55, 0.95, back to 0.45. */
internal fun heartbeat(time: Float): Float {
    val t = kkLoop(time, 1f)
    return when {
        t < 0.12f -> kkLerp(0.45f, 1f, t / 0.12f)
        t < 0.24f -> kkLerp(1f, 0.55f, (t - 0.12f) / 0.12f)
        t < 0.36f -> kkLerp(0.55f, 0.95f, (t - 0.24f) / 0.12f)
        else -> kkLerp(0.95f, 0.45f, (t - 0.36f) / 0.64f)
    }
}

/** Character ability as a small sheared meter without a label; SHARD has no gauge to show. */
private fun DrawScope.drawAbilityMeter(engine: GameplayRenderModel, frame: HudFrame, roles: KkRolePalette, right: Float, bottom: Float) {
    if (engine.coreShape == CoreShape.SHARD) return
    val ability = engine.characterAbility
    val width = frame.u(if (frame.regular) 64f else 44f)
    val height = frame.u(if (frame.regular) 5f else 4f)
    val armed = ability.charge >= 1f || ability.parryWindow > 0f
    val rect = HudDrawCache.rect(HudRect.ABILITY, right - width, bottom - height, right, bottom)
    drawKkMeter(rect, ability.charge, if (armed) roles.you else Kk.Bone2, roles,
        segments = if (engine.coreShape == CoreShape.TESSERACT) 4 else 0)
}

/** Bottom-center: speed number stretched by velocity tier, velocity ladder, overdrive bar. */
private fun DrawScope.drawKineticGauge(
    engine: GameplayRenderModel,
    hostMeasurer: TextMeasurer,
    frame: HudFrame,
    renderTime: Float,
    memory: HudPresentationMemory?,
) {
    // The speed number is display type: the same size at every text-size setting.
    val measurer = HudMeasurers.display(hostMeasurer)
    val roles = measurer.roles
    val tier = engine.velocityTier
    val ticks = if (frame.regular) 30 else 20
    val lit = velocityLadderLit(engine.speed, ticks)
    val hotFrom = ticks * 2 / 3
    val maxFrom = if (tier >= 4) (lit - ticks / 10).coerceAtLeast(hotFrom) else ticks
    val speedRole = when {
        tier >= 4 -> 2
        lit >= hotFrom -> 1
        else -> 0
    }
    val speedColor = when (speedRole) {
        2 -> roles.threat
        1 -> roles.you
        else -> Kk.Bone
    }
    val overdriveActive = engine.overdriveTime > 0f
    val drain = if (overdriveActive && memory != null) memory.overdriveDrain(engine) else 0f
    val overdrive = if (drain > 0f) drain else engine.overdriveCharge / 100f
    val stripes = if (overdriveActive) KkMeterStripes.YOU else KkMeterStripes.NONE
    val value = engine.speed.toLong()
    val stretch = speedStretch(tier)
    when (frame.mode) {
        GameplayLayoutMode.REGULAR -> {
            val width = min(frame.u(360f), (frame.width - frame.margin * 2f) * 0.3f)
            val left = frame.width * 0.5f - width * 0.5f
            val bottom = frame.height - frame.u(24f)
            val od = HudDrawCache.rect(HudRect.OVERDRIVE, left, bottom - frame.u(5f), left + width, bottom)
            drawKkMeter(od, overdrive, roles.you, roles, stripes = stripes, time = renderTime)
            val ladderBottom = od.top - frame.u(8f)
            val ladder = HudDrawCache.rect(HudRect.LADDER, left, ladderBottom - frame.u(16f), left + width, ladderBottom)
            drawKkTickLadder(ladder, roles, ticks, lit, hotFrom, maxFrom)
            drawSpeedNumber(measurer, value, measurer.typography.wideStyle(frame.t(56f), tabular = true, trackingEm = colorTracking(speedRole)), frame.width * 0.5f,
                ladder.top - frame.u(10f), KkAlign.CENTER, stretch, speedColor, od.bottom)
        }
        GameplayLayoutMode.COMPACT_LANDSCAPE -> {
            val width = frame.u(230f) * frame.factor
            val left = frame.margin + frame.u(248f) * frame.factor
            val bottom = frame.height - frame.u(12f)
            val od = HudDrawCache.rect(HudRect.OVERDRIVE, left, bottom - frame.u(4f), left + width, bottom)
            drawKkMeter(od, overdrive, roles.you, roles, stripes = stripes, time = renderTime)
            val ladderBottom = od.top - frame.u(6f)
            val ladder = HudDrawCache.rect(HudRect.LADDER, left, ladderBottom - frame.u(12f), left + width, ladderBottom)
            drawKkTickLadder(ladder, roles, ticks, lit, hotFrom, maxFrom)
            drawSpeedNumber(measurer, value, measurer.typography.wideStyle(30f, tabular = true, trackingEm = colorTracking(speedRole)), left, ladder.top - frame.u(5f),
                KkAlign.START, stretch, speedColor, od.bottom)
        }
        GameplayLayoutMode.COMPACT_PORTRAIT -> {
            val width = frame.u(160f) * frame.factor
            val right = frame.width - frame.margin
            val bottom = frame.height - frame.u(36f + 80f + 18f)
            val od = HudDrawCache.rect(HudRect.OVERDRIVE, right - width, bottom - frame.u(4f), right, bottom)
            drawKkMeter(od, overdrive, roles.you, roles, stripes = stripes, time = renderTime)
            drawSpeedNumber(measurer, value, measurer.typography.wideStyle(30f, tabular = true, trackingEm = colorTracking(speedRole)), right, od.top - frame.u(7f),
                KkAlign.END, stretch, speedColor, od.bottom)
        }
    }
}

/**
 * Speed number anchored at its bottom edge, stretched horizontally and leaning −8°. It changes
 * every frame, so it is drawn from cached digit layouts.
 */
private fun DrawScope.drawSpeedNumber(
    measurer: TextMeasurer,
    value: Long,
    style: androidx.compose.ui.text.TextStyle,
    x: Float,
    bottom: Float,
    align: KkAlign,
    stretch: Float,
    color: Color,
    gaugeBottom: Float,
) {
    val top = bottom - digitBoxHeight(measurer, style)
    withTransform({
        scale(stretch, 1f, Offset(x, bottom))
        kkShear(bottom, -8f)
    }) {
        drawKkTabularNumber(measurer, value, style, x, top, color, align)
    }
    // The stretched number with its lean (the top leans right by tan 8° of its height), over the gauge.
    val width = kkTabularNumberWidth(measurer, value, style) * stretch
    val lean = (bottom - top) * SPEED_LEAN
    val left = when (align) {
        KkAlign.START -> x
        KkAlign.CENTER -> x - width * 0.5f
        KkAlign.END -> x - width
    }
    HudLayoutProbe.record(HudBlock.SPEED, left - lean, top, left + width + lean, gaugeBottom)
}

/** tan(8°): the speed number's lean. */
private const val SPEED_LEAN = 0.1405f

/** Bottom-right (desktop) or top-right (landscape) relic diamonds and the active weapon slot. */
private fun DrawScope.drawLoadout(engine: GameplayRenderModel, hostMeasurer: TextMeasurer, frame: HudFrame) {
    // "Lvl N" is UI text: it follows the text size and shrinks to fit its slot.
    val measurer = HudMeasurers.ui(hostMeasurer)
    val roles = measurer.roles
    val content = engine.content
    val slots = content.relicPolicy.maxSlots.coerceIn(0, 8)
    val language = measurer.language
    val levelText = HudScratch.weaponLevel.of(language, engine.weaponLevel.toLong()) {
        language.text(HudRedesignText.WeaponLevel, engine.weaponLevel)
    }
    val icon = weaponIcon(engine.weapon)
    val maxLevel = weaponMasteriesReached(content, engine.weaponLevel) >= content.weaponMasteries.size
    when (frame.mode) {
        GameplayLayoutMode.REGULAR -> {
            val right = frame.width - frame.margin
            val bottom = frame.height - frame.u(REGULAR_HUD_BOTTOM_DP)
            val pipsTop = bottom - frame.u(4f)
            val slotSize = frame.u(54f)
            val slotBottom = pipsTop - frame.u(5f)
            val slot = HudDrawCache.rect(HudRect.WEAPON, right - slotSize, slotBottom - slotSize, right, slotBottom)
            // Drawn end-aligned 9 dp in from the slot's right edge; the slab's left edge meets the bottom at its left.
            val levelLayout = HudDrawCache.fitted(HudText.WEAPON_LEVEL, measurer, levelText, 10f * frame.textFactor,
                slotSize - d(9f) - d(4f)) { size -> weaponLevelStyle(measurer, size) }
            drawKkWeaponSlot(measurer, slot, icon, maxLevel = maxLevel, levelLayout = levelLayout, iconSizeDp = 28f * frame.textFactor)
            drawMasteryPips(engine, frame, roles, slot.center.x, pipsTop)
            val relicSize = frame.u(36f)
            val relicCenter = slot.top - frame.u(12f) - relicSize * 0.5f
            drawRelicRow(engine, content, slots, right, relicCenter, relicSize, frame)
            HudLayoutProbe.record(HudBlock.LOADOUT, min(slot.left, right - relicRowWidth(slots, relicSize, frame)),
                relicCenter - relicSize * 0.5f - relicBracketReach(frame), right, bottom)
        }
        GameplayLayoutMode.COMPACT_LANDSCAPE -> {
            val right = frame.width - frame.margin
            val top = frame.u(62f)
            val slotSize = frame.u(34f)
            val slot = HudDrawCache.rect(HudRect.WEAPON, right - slotSize, top, right, top + slotSize)
            drawKkWeaponSlot(measurer, slot, icon, maxLevel = maxLevel, iconSizeDp = 20f)
            // Centered under the slot, never past the screen's right margin.
            val levelLayout = HudDrawCache.fitted(HudText.WEAPON_LEVEL, measurer, levelText, 9f,
                slotSize + frame.margin) { size -> weaponLevelStyle(measurer, size) }
            val levelX = min(slot.center.x, right + frame.margin * 0.5f - levelLayout.size.width * 0.5f)
            drawKkText(levelLayout, levelX, slot.bottom + frame.u(4f), if (maxLevel) Kk.RLegend else Kk.Bone, KkAlign.CENTER)
            val relicSize = frame.u(22f)
            drawRelicRow(engine, content, slots, slot.left - frame.u(8f), slot.center.y, relicSize, frame)
            HudLayoutProbe.record(HudBlock.LOADOUT, slot.left - frame.u(8f) - relicRowWidth(slots, relicSize, frame),
                slot.center.y - relicSize * 0.5f - relicBracketReach(frame), max(right, levelX + levelLayout.size.width * 0.5f),
                slot.bottom + frame.u(4f) + levelLayout.kkBoxHeight)
        }
        GameplayLayoutMode.COMPACT_PORTRAIT -> {
            val top = frame.height - frame.u(238f)
            val slotSize = frame.u(34f)
            val slot = HudDrawCache.rect(HudRect.WEAPON, frame.margin, top, frame.margin + slotSize, top + slotSize)
            drawKkWeaponSlot(measurer, slot, icon, maxLevel = maxLevel, iconSizeDp = 20f)
            val relicSize = frame.u(22f)
            val relicLeft = frame.width - frame.margin - relicRowWidth(slots, relicSize, frame)
            val levelLayout = HudDrawCache.fitted(HudText.WEAPON_LEVEL, measurer, levelText, 11f,
                relicLeft - frame.u(12f) - slot.right - frame.u(8f)) { size -> weaponLevelStyle(measurer, size) }
            drawKkText(levelLayout, slot.right + frame.u(8f), slot.center.y, if (maxLevel) Kk.RLegend else Kk.Bone, valign = KkVAlign.CENTER)
            drawRelicRow(engine, content, slots, frame.width - frame.margin, slot.center.y, relicSize, frame)
            HudLayoutProbe.record(HudBlock.LOADOUT, slot.left, min(slot.top, slot.center.y - relicSize * 0.5f - relicBracketReach(frame)),
                frame.width - frame.margin, slot.bottom)
        }
    }
}

private fun weaponLevelStyle(measurer: TextMeasurer, size: Float) =
    measurer.typography.monoStyle(size, weight = FontWeight.Bold, trackingEm = 0f, lineHeightEm = 1f)

/** Width of a relic row of [slots] diamonds of [size] px on the HUD's pitch. */
private fun relicRowWidth(slots: Int, size: Float, frame: HudFrame): Float =
    if (slots <= 0) 0f else slots * size + (slots - 1) * frame.u(if (frame.regular) 10f else 6f)

/** Height above the relic row that the current synergy brackets reach. */
private fun DrawScope.relicBracketReach(frame: HudFrame): Float {
    val highest = HudScratch.brackets.maxLevel()
    return if (highest < 0) 0f else frame.u(4f) + highest * bracketLevelStep(frame) + d(1f)
}

/** Width of the landscape loadout row (relic diamonds, gap, weapon slot) in px. */
internal fun compactLoadoutWidth(slots: Int, unit: Float): Float =
    unit * (34f + 8f + slots * 22f + (slots - 1).coerceAtLeast(0) * 6f)

/** Mastery milestones reached at [level] (1 for the first milestone), without iterators. */
internal fun weaponMasteriesReached(content: GameplayContentSnapshot, level: Int): Int {
    val masteries = content.weaponMasteries
    var reached = 0
    for (index in masteries.indices) if (level >= masteries[index].minimumLevel) reached = index + 1
    return reached
}

/** Mastery pips under the weapon slot: `you`, then bone, then the legendary top milestone. */
private fun DrawScope.drawMasteryPips(engine: GameplayRenderModel, frame: HudFrame, roles: KkRolePalette, centerX: Float, top: Float) {
    val count = engine.content.weaponMasteries.size.coerceIn(1, 6)
    val reached = weaponMasteriesReached(engine.content, engine.weaponLevel).coerceIn(0, count)
    val pipWidth = 8f * frame.textFactor
    val pipHeight = 4f * frame.textFactor
    val total = count * d(pipWidth) + (count - 1) * d(2f)
    var x = centerX - total * 0.5f
    for (index in 0 until count) {
        val color = when {
            index >= reached -> Kk.Ink4
            index == count - 1 -> Kk.RLegend
            index == count - 2 -> Kk.Bone
            else -> roles.you
        }
        drawKkPips(Offset(x, top), 1, 1, color, sizeDp = pipHeight, widthDp = pipWidth)
        x += d(pipWidth) + d(2f)
    }
}

/**
 * Relic diamonds right-aligned at [right] on an even pitch. Synergies come from
 * [overlaySynergyLinks] (the same reading as pause and the relic matrix, mirroring the game's rule:
 * any two different relics of an aspect, in any slots, or a named pair): neighbouring members are
 * joined by a link bar, members further apart by a bracket above the row. Brackets whose slot spans
 * overlap or touch stack on separate levels (as the relic matrix does), each level's legs ending
 * above the level below, so interleaved synergies never merge into one line.
 */
private fun DrawScope.drawRelicRow(
    engine: GameplayRenderModel,
    content: GameplayContentSnapshot,
    slots: Int,
    right: Float,
    centerY: Float,
    size: Float,
    frame: HudFrame,
) {
    if (slots <= 0) return
    val relics = engine.equippedRelics
    val gap = frame.u(if (frame.regular) 10f else 6f)
    val pitch = size + gap
    val firstCenter = right - slots * size - (slots - 1) * gap + size * 0.5f
    val half = size * 0.5f
    val links = hudRelicLinks(content, relics)
    for (linkIndex in links.indices) {
        val link = links[linkIndex]
        val members = link.slots
        for (step in 0 until members.size - 1) {
            val a = members[step]
            val b = members[step + 1]
            if (b != a + 1 || b >= slots) continue
            val ax = firstCenter + a * pitch + half
            val bx = firstCenter + b * pitch - half
            drawKkSynergyLink(Offset(ax, centerY), Offset(bx, centerY), link.definition.overlayColor())
            HudLayoutProbe.recordLink(false, a, b, ax, centerY - d(4.5f), bx, centerY + d(4.5f))
        }
    }
    val brackets = HudScratch.brackets
    val baseY = centerY - half - frame.u(4f)
    val step = bracketLevelStep(frame)
    val stroke = d(2f)
    for (index in 0 until brackets.count) {
        val a = brackets.from[index]
        val b = brackets.to[index]
        if (b >= slots) continue
        val level = brackets.level[index]
        val color = links[brackets.link[index]].definition.overlayColor()
        val ax = firstCenter + a * pitch
        val bx = firstCenter + b * pitch
        val y = baseY - level * step
        // The lowest level's legs reach the diamonds (`.bracket`, 8 dp); higher legs stop above the level below.
        val leg = if (level == 0) d(8f) else step - frame.u(3f)
        drawLine(color, Offset(ax - stroke * 0.5f, y), Offset(bx + stroke * 0.5f, y), stroke)
        drawLine(color, Offset(ax, y), Offset(ax, y + leg), stroke)
        drawLine(color, Offset(bx, y), Offset(bx, y + leg), stroke)
        HudLayoutProbe.recordLink(true, a, b, ax - stroke * 0.5f, y - stroke * 0.5f, bx + stroke * 0.5f, y + leg)
    }
    val sizeDp = size / density
    for (index in 0 until slots) {
        val relic = relics.getOrNull(index)
        val center = Offset(firstCenter + index * pitch, centerY)
        if (relic == null) {
            drawKkRelicSlot(center, Color.Unspecified, sizeDp = sizeDp)
        } else {
            val aspect = relicAspect(content, relic.id)
            drawKkRelicSlot(center, Kk.aspect(aspect.ordinal), KkIcon.Aspects[aspect.ordinal], sizeDp = sizeDp)
        }
    }
}

/** Vertical distance between stacked synergy bracket levels, in px. */
private fun bracketLevelStep(frame: HudFrame): Float = frame.u(if (frame.regular) 8f else 6f)

/**
 * Synergy links of the equipped [relics] (slot order), as [overlaySynergyLinks] reads them, and the
 * stacked levels of their brackets; recomputed only when the relic list or the content changes.
 */
internal fun hudRelicLinks(content: GameplayContentSnapshot, relics: List<EquippedRelic>): List<OverlaySynergyLink> {
    val cache = HudScratch
    if (cache.linkRelics !== relics || cache.linkContent !== content) {
        cache.linkRelics = relics
        cache.linkContent = content
        cache.links = overlaySynergyLinks(relics.map { it.id }, content)
        cache.brackets.place(cache.links)
    }
    return cache.links
}

/**
 * Brackets of the relic row: one per pair of consecutive members of a synergy that are not
 * neighbours, each on the lowest level where no bracket over an overlapping or touching slot span
 * sits (the relic matrix's `relicBrackets` rule).
 */
internal class HudBracketLevels {
    var count = 0
        private set
    val from = IntArray(MAX)
    val to = IntArray(MAX)
    val level = IntArray(MAX)
    val link = IntArray(MAX)

    fun place(links: List<OverlaySynergyLink>) {
        count = 0
        for (linkIndex in links.indices) {
            val members = links[linkIndex].slots
            for (step in 0 until members.size - 1) {
                val a = members[step]
                val b = members[step + 1]
                if (b == a + 1 || count >= MAX) continue
                var candidate = 0
                while (occupied(candidate, a, b)) candidate++
                from[count] = a
                to[count] = b
                level[count] = candidate
                link[count] = linkIndex
                count++
            }
        }
    }

    /** Highest level in use, -1 without brackets. */
    fun maxLevel(): Int {
        var highest = -1
        for (index in 0 until count) highest = max(highest, level[index])
        return highest
    }

    private fun occupied(candidate: Int, a: Int, b: Int): Boolean {
        for (index in 0 until count) {
            if (level[index] == candidate && from[index] <= b && a <= to[index]) return true
        }
        return false
    }

    private companion object {
        const val MAX = 16
    }
}

/** Aspect of a relic, indexed once per content snapshot (content lookups iterate the catalog). */
internal fun relicAspect(content: GameplayContentSnapshot, id: RelicId): RelicAspect {
    val cache = HudScratch
    if (cache.aspectContent !== content) {
        cache.aspectContent = content
        cache.aspects.fill(null)
    }
    return cache.aspects[id.ordinal] ?: content.relic(id).aspect.also { cache.aspects[id.ordinal] = it }
}

/** Running controls: Dash and Brake slabs, pause and performance icon buttons. */
internal fun DrawScope.drawControls(engine: GameplayRenderModel, textMeasurer: TextMeasurer) {
    val roles = textMeasurer.roles
    val frame = HudScratch.frame.update(size.width, size.height, density)
    val regular = frame.regular
    forEachRunningControlBounds(size.width, size.height, density) { target, left, top, right, bottom ->
        when (target) {
            RunningControlTarget.DASH -> drawTouchButton(
                textMeasurer, frame, HudPath.DASH, HudText.DASH_LABEL, left, top, right, bottom, KkIcon.SYSTEM_DASH,
                dashLabel(textMeasurer.language), regular,
                face = when {
                    engine.dashPhaseTime > DASH_PHASE_SECONDS - 0.05f -> Kk.Bone
                    engine.overheated -> Kk.Ink3
                    else -> roles.you
                },
                foreground = if (engine.overheated && engine.dashPhaseTime <= 0f) Kk.Mute2 else Kk.Ink,
                outline = Color.Unspecified,
                heat = engine.heat / GameplayRenderModel.MAX_HEAT,
                heatColor = if (engine.overheated) roles.threat else roles.heat,
            )
            RunningControlTarget.BRAKE -> drawTouchButton(
                textMeasurer, frame, HudPath.BRAKE, HudText.BRAKE_LABEL, left, top, right, bottom, KkIcon.SYSTEM_BRAKE,
                brakeLabel(textMeasurer.language), regular,
                face = if (engine.braking) Kk.Bone else Kk.Ink3,
                foreground = if (engine.braking) Kk.Ink else Kk.Bone,
                outline = if (engine.braking) Color.Unspecified else Kk.Line2,
                heat = -1f,
                heatColor = Color.Unspecified,
            )
            RunningControlTarget.PAUSE -> drawHudIconButton(frame, HudPath.PAUSE, left, top, right, bottom, regular) { center, glyph ->
                drawPauseGlyph(center, glyph)
            }
            RunningControlTarget.PERFORMANCE -> drawHudIconButton(frame, HudPath.PERFORMANCE, left, top, right, bottom, regular) { center, glyph ->
                drawPerformanceGlyph(center, glyph)
            }
        }
    }
}

private fun dashLabel(language: AppLanguage): String =
    HudScratch.dashLabel.of(language, 0L) { language.text(GameplayText.DashDescription) }

private fun brakeLabel(language: AppLanguage): String =
    HudScratch.brakeLabel.of(language, 0L) { language.text(GameplayText.BrakeDescription) }

/** Slab touch button: icon beside the label on desktop, icon above the label on phones. */
private fun DrawScope.drawTouchButton(
    measurer: TextMeasurer,
    frame: HudFrame,
    path: HudPath,
    text: HudText,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    icon: KkIcon,
    label: String,
    regular: Boolean,
    face: Color,
    foreground: Color,
    outline: Color,
    heat: Float,
    heatColor: Color,
) {
    val cut = if (regular) frame.u(12f) else d(14f)
    drawPath(HudDrawCache.paths.slab(path.ordinal, left, top, right, bottom, cut), face)
    if (outline != Color.Unspecified) {
        val inset = d(0.75f)
        val outlinePath = if (path == HudPath.BRAKE) HudPath.BRAKE_OUTLINE else HudPath.DASH_OUTLINE
        drawPath(HudDrawCache.paths.slab(outlinePath.ordinal, left + inset, top + inset, right - inset, bottom - inset, cut), outline,
            style = kkStroke(d(1.5f)))
    }
    val width = right - left
    val height = bottom - top
    val fontSize = if (regular) frame.t(22f) else if (path == HudPath.DASH) 20f else 17f
    val iconSize = if (regular) frame.u(20f) else d(if (path == HudPath.DASH) 24f else 20f)
    val iconGap = frame.u(8f)
    // The label is UI text: it grows with the text size and shrinks to fit inside the slab (a
    // parallelogram: `width - 2 cut` clears both slanted edges at any height) with padding.
    val room = width - cut * 2f - d(TOUCH_LABEL_PADDING_DP) * 2f - if (regular) iconSize + iconGap else 0f
    val layout = HudDrawCache.fitted(text, HudMeasurers.ui(measurer), label, fontSize, room, uppercase = true) { size ->
        measurer.typography.condStyle(size, lineHeightEm = 1f)
    }
    val block = if (path == HudPath.DASH) HudBlock.DASH_LABEL else HudBlock.BRAKE_LABEL
    if (regular) {
        val contentWidth = iconSize + iconGap + layout.size.width
        val x = left + (width - contentWidth) * 0.5f
        val cy = top + height * 0.5f - if (heat >= 0f) frame.u(2f) else 0f
        drawKkIcon(icon, Offset(x + iconSize * 0.5f, cy), iconSize, foreground)
        drawKkText(layout, x + iconSize + iconGap, cy, foreground, valign = KkVAlign.CENTER)
        HudLayoutProbe.record(block, x + iconSize + iconGap, cy - layout.kkBoxHeight * 0.5f, x + contentWidth,
            cy + layout.kkBoxHeight * 0.5f)
    } else {
        val gap = d(2f)
        val contentHeight = iconSize + gap + layout.kkBoxHeight
        val y = top + (height - contentHeight) * 0.5f - if (heat >= 0f) d(3f) else 0f
        drawKkIcon(icon, Offset(left + width * 0.5f, y + iconSize * 0.5f), iconSize, foreground)
        drawKkText(layout, left + width * 0.5f, y + iconSize + gap, foreground, KkAlign.CENTER)
        HudLayoutProbe.record(block, left + (width - layout.size.width) * 0.5f, y + iconSize + gap,
            left + (width + layout.size.width) * 0.5f, y + contentHeight)
    }
    if (heat >= 0f) {
        val barLeft = left + cut
        val barRight = right - cut
        val barBottom = bottom - d(6f)
        val barHeight = d(4f)
        drawRect(Kk.Ink.copy(alpha = 0.25f), Offset(barLeft, barBottom - barHeight), Size(barRight - barLeft, barHeight))
        val fill = heat.coerceIn(0f, 1f)
        if (fill > 0f) drawRect(heatColor, Offset(barLeft, barBottom - barHeight), Size((barRight - barLeft) * fill, barHeight))
    }
}

/** Inner padding between a touch button's label and its slab edges, in dp. */
internal const val TOUCH_LABEL_PADDING_DP = 2f

/** `.ibtn` visual centered in its (larger) touch target. */
private inline fun DrawScope.drawHudIconButton(
    frame: HudFrame,
    path: HudPath,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    regular: Boolean,
    glyph: DrawScope.(center: Offset, size: Float) -> Unit,
) {
    val w = if (regular) frame.u(40f) else d(44f)
    val h = if (regular) frame.u(36f) else d(40f)
    val cx = (left + right) * 0.5f
    val cy = (top + bottom) * 0.5f
    drawPath(HudDrawCache.paths.slab(path.ordinal, cx - w * 0.5f, cy - h * 0.5f, cx + w * 0.5f, cy + h * 0.5f, d(8f)), Kk.Ink3)
    glyph(Offset(cx, cy), if (regular) frame.u(18f) else d(20f))
}

/** Pause mark (two bars, `M8 4v16M16 4v16`) without an arrow-shaped play/pause glyph. */
internal fun DrawScope.drawPauseGlyph(center: Offset, size: Float, color: Color = Kk.Bone) {
    val unit = size / 24f
    val stroke = 2f * unit
    drawLine(color, Offset(center.x - 4f * unit, center.y - 8f * unit), Offset(center.x - 4f * unit, center.y + 8f * unit), stroke, StrokeCap.Square)
    drawLine(color, Offset(center.x + 4f * unit, center.y - 8f * unit), Offset(center.x + 4f * unit, center.y + 8f * unit), stroke, StrokeCap.Square)
}

/** Performance mark: three rising bars. */
internal fun DrawScope.drawPerformanceGlyph(center: Offset, size: Float, color: Color = Kk.Bone) {
    val unit = size / 24f
    val stroke = 2f * unit
    val base = center.y + 7f * unit
    drawLine(color, Offset(center.x - 6f * unit, base), Offset(center.x - 6f * unit, base - 6f * unit), stroke, StrokeCap.Square)
    drawLine(color, Offset(center.x, base), Offset(center.x, base - 11f * unit), stroke, StrokeCap.Square)
    drawLine(color, Offset(center.x + 6f * unit, base), Offset(center.x + 6f * unit, base - 15f * unit), stroke, StrokeCap.Square)
}
