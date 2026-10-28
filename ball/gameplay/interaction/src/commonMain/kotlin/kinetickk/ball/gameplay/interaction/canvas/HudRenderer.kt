// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EnemyRank
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.input.RunningControlPressLog
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.RunningControlMask
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
import kinetickk.ball.gameplay.interaction.layout.regularHeaderStep
import kinetickk.ball.gameplay.interaction.layout.regularHudUnit
import kinetickk.ball.gameplay.interaction.performance.GameplayFpsCounter
import kinetickk.ball.gameplay.interaction.layout.runningHudMargin
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.ball.gameplay.interaction.localization.HudRedesignText
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.gameplay.nucleus.model.formatRunTime
import kinetickk.ball.gameplay.nucleus.render.ApexProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyAffinity
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RelicLinkKind
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
    controls: RunningControlPressLog? = null,
) {
    val frame = HudScratch.frame.update(size.width, size.height, density)
    HudLayoutProbe.begin()
    val boss = HudScratch.boss.select(engine)
    memory?.observe(engine, renderTime, boss)
    val critical = isIntegrityCritical(engine.hp, engine.maxHp)

    drawHudWorldAnchors(engine, textMeasurer, renderTime, shakeX, shakeY, memory, critical)
    drawHudScreenStates(engine, textMeasurer.roles, renderTime, memory, critical)
    drawDimensionShutter(engine, textMeasurer.roles, renderTime, memory)

    drawDataLine(engine, frame, textMeasurer.roles, renderTime, memory)
    val badgeBottom = drawLevelCluster(engine, textMeasurer, frame, renderTime, memory, controls)
    drawClockAndBoss(engine, textMeasurer, frame, boss, renderTime, memory)
    drawEconomy(engine, textMeasurer, frame, badgeBottom)
    // The chain first: the portrait Rebirth strip shares its row and stops short of it.
    drawChain(engine, textMeasurer, frame)
    drawRebirthStrip(engine, textMeasurer, frame)
    drawCoreStatus(engine, textMeasurer, frame, renderTime, memory, critical)
    drawKineticGauge(engine, textMeasurer, frame, renderTime, memory)
    drawLoadout(engine, textMeasurer, frame)
    drawMinimap(engine, textMeasurer, frame)
    drawTrialPanel(engine, textMeasurer, renderTime, trialInfoOpen)
    drawControls(engine, textMeasurer, renderTime, memory, controls)
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

    /** The Show FPS counter's value ("–" before the first measured window). */
    val fps = HudNumberText { if (it < 0L) "–" else it.toString() }
    val weaponLevel = HudKeyedText()
    val bossName = HudKeyedText()
    val label = HudKeyedText()
    val badgeShots = HudBadgeOneShots()
    val dashLabel = HudKeyedText()
    val brakeLabel = HudKeyedText()
    val overheat = HudKeyedText()

    /** The skill row's fade while it slides in with the first bound active item. */
    val skillRowPaint = androidx.compose.ui.graphics.Paint()
    var aspectContent: GameplayContentSnapshot? = null
    var linkRelics: List<EquippedRelic>? = null
    var linkContent: GameplayContentSnapshot? = null
    var links: List<OverlayRelicLink> = emptyList()
    val brackets = HudBracketLevels()
    /**
     * The level badge's row this frame, the economy's right edge on phones (inline on landscape, the
     * chip row on portrait) and the portrait chain's left edge (+inf without a chain): the Rebirth strip.
     */
    var badgeTop = 0f
    var badgeBottom = 0f
    var badgeRight = 0f
    var economyRight = 0f
    var chainLeft = Float.POSITIVE_INFINITY
    val rebirthTier = HudNumberText { it.toString() }
    val aspects = arrayOfNulls<RelicAspect>(RelicId.entries.size)
    var definitionContent: GameplayContentSnapshot? = null
    val definitions = arrayOfNulls<kinetickk.ball.content.api.RelicDefinition>(RelicId.entries.size)
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

    /** Portrait phones: the bottom of the integrity cluster and the speed gauge, above the touch buttons. */
    val portraitClustersBottom: Float get() = height - u(36f + 80f + 18f)

    /** Portrait phones: the top of the loadout row (weapon slot, level and relics) over the clusters. */
    val portraitLoadoutTop: Float get() = height - u(238f)

    /** Top of the chain counter on the right edge (REGULAR). */
    val chainTop: Float get() = max(u(76f), min(u(160f), height * 0.2f))

    /** Center of the pause button visual. */
    val pauseCenterX: Float get() = width - margin - if (regular) u(20f) else u(22f)

    /** Center of the Build button (Compose) beside pause on desktop and landscape phones. */
    val buildCenterX: Float
        get() = if (regular) pauseCenterX - max(u(48f), RUNNING_CONTROL_MIN_DP * density) else pauseCenterX - u(104f)
}

/** The level badge's tier this frame: the decade, or APEX at the content's level cap. */
internal fun GameplayRenderModel.levelTier(): KkLevelTier = KkLevelTier.of(level.coerceAtLeast(0), maxLevel)

/** The run's Apex as the level badge shows it (NONE until it unlocks at the cap). */
internal fun ApexProjection.badgeState(): KkApexState = when {
    !unlocked -> KkApexState.NONE
    active01 > 0f -> KkApexState.ACTIVE
    ready -> KkApexState.READY
    else -> KkApexState.COOLING
}

/**
 * The Data meters' fill 0..1: the level's Data share below the cap; at the cap they show the Apex
 * (the recharged share while it cools, full while ready, the remaining activation while it runs).
 */
internal fun GameplayRenderModel.dataMeterFill(tier: KkLevelTier, apexState: KkApexState): Float = when {
    tier != KkLevelTier.T6 -> (data.toFloat() / nextLevelData.coerceAtLeast(1)).coerceIn(0f, 1f)
    apexState == KkApexState.READY -> 1f
    apexState == KkApexState.ACTIVE -> apex.active01.coerceIn(0f, 1f)
    else -> (1f - apex.cooldown01).coerceIn(0f, 1f)
}

/** The ready Apex meters' foil breathes between 75 % and full. */
private fun apexReadyAlpha(renderTime: Float): Float = 0.75f + 0.25f * kkPulse(renderTime, APEX_PULSE_SECONDS)

private const val APEX_PULSE_SECONDS = 1.2f

/** The tier change's bone overlay on the Data line and the Apex unlock's gold sweep (ms). */
private const val METER_FLASH_MS = 420f

/** Width of the bone segment that flashes at the meters' fill head (Apex ping and refund), dp. */
private const val HEAD_FLASH_DP = 16f

/** Data line along the top edge (all modes; at the cap the Apex meter, 4 dp instead of 3). */
private fun DrawScope.drawDataLine(
    engine: GameplayRenderModel,
    frame: HudFrame,
    roles: KkRolePalette,
    renderTime: Float,
    memory: HudPresentationMemory?,
) {
    val tier = engine.levelTier()
    val apexState = engine.apex.badgeState()
    val apex = tier == KkLevelTier.T6
    val top = frame.top
    val width = frame.width
    val height = d(if (apex) 4f else 3f)
    val bottom = top + height
    val fillRight = width * engine.dataMeterFill(tier, apexState)
    val gold = roles.gold
    val unlockMs = (memory?.apexUnlock(renderTime) ?: 1f) * KkTime.ApexUnlock
    if (apex && unlockMs < METER_FLASH_MS) {
        // The Apex coming online: gold light sweeps across the line, then the ready foil takes over.
        drawRect(Kk.Ink3, Offset(0f, top), Size(width, height))
        drawRect(gold.light, Offset(0f, top), Size(width * KkEase.Snap.transform(unlockMs / METER_FLASH_MS), height))
        return
    }
    val line = HudDrawCache.rect(HudRect.DATA_LINE, 0f, top, width, bottom)
    when (tier) {
        KkLevelTier.T1, KkLevelTier.T2, KkLevelTier.T3 -> {
            drawRect(Kk.Bone.copy(alpha = 0.08f), Offset(0f, top), Size(width, height))
            if (fillRight > 0f) drawRect(if (tier == KkLevelTier.T3) roles.you else Kk.Bone, Offset(0f, top), Size(fillRight, height))
            if (tier == KkLevelTier.T2 && fillRight > 0f) {
                val head = min(d(12f), fillRight)
                drawRect(roles.you, Offset(fillRight - head, top), Size(head, height))
            }
        }
        KkLevelTier.T4 -> {
            drawRect(Kk.Ink3, Offset(0f, top), Size(width, height))
            if (fillRight > 0f) drawRect(kkFillBrush(KkFillKind.BADGE_STRIPES, roles.you, Kk.Ink), Offset(0f, top), Size(fillRight, height))
        }
        KkLevelTier.T5 -> {
            drawRect(gold.base.copy(alpha = 0.10f), Offset(0f, top), Size(width, height))
            if (fillRight > 0f) clipRect(0f, top, fillRight, bottom) { drawKkFoil(line, renderTime, 2.4f, gold = gold) }
        }
        KkLevelTier.T6 -> when (apexState) {
            KkApexState.READY -> drawKkFoil(line, renderTime, 2.4f, apexReadyAlpha(renderTime), gold)
            KkApexState.ACTIVE -> {
                drawRect(Kk.Ink3, Offset(0f, top), Size(width, height))
                if (fillRight > 0f) drawKkMeterStripes(0f, top, fillRight, bottom, KkMeterStripes.GOLD, roles, renderTime)
            }
            KkApexState.COOLING, KkApexState.NONE -> {
                drawRect(Kk.Ink3, Offset(0f, top), Size(width, height))
                if (fillRight > 0f) drawRect(gold.base, Offset(0f, top), Size(fillRight, height))
            }
        }
    }
    if (memory == null) return
    // A new tier: a bone overlay fades off the whole line.
    val tierMs = memory.tierBurst(renderTime) * KkTime.TierBurst
    if (tierMs < METER_FLASH_MS) drawRect(Kk.Bone, Offset(0f, top), Size(width, height), alpha = 1f - tierMs / METER_FLASH_MS)
    if (apex) {
        val flash = headFlash(memory, renderTime)
        if (flash > 0f) {
            val segment = d(HEAD_FLASH_DP)
            drawRect(Kk.Bone, Offset((fillRight - segment * 0.5f).coerceIn(0f, max(0f, width - segment)), top), Size(segment, height), flash)
        }
    }
}

/** Bone flash at the Apex meters' fill head: the ready ping (420 ms) and Data refunds (220 ms), fading out. */
private fun headFlash(memory: HudPresentationMemory, renderTime: Float): Float =
    max(1f - memory.apexPing(renderTime), 1f - memory.apexRefund(renderTime))

/** Thin Data bar under the REGULAR badge, styled by tier; at the cap the Apex meter. */
private fun DrawScope.drawDataBar(
    engine: GameplayRenderModel,
    bar: Rect,
    roles: KkRolePalette,
    renderTime: Float,
    memory: HudPresentationMemory?,
) {
    val tier = engine.levelTier()
    val apexState = engine.apex.badgeState()
    val fill = engine.dataMeterFill(tier, apexState)
    val gold = roles.gold
    val track = Kk.Bone.copy(alpha = 0.12f)
    when (tier) {
        KkLevelTier.T1 -> drawKkMeter(bar, fill, Kk.Bone, roles, background = track)
        KkLevelTier.T2 -> drawKkMeter(bar, fill, Kk.Bone, roles, background = track, edge = true, edgeColor = roles.you)
        KkLevelTier.T3 -> {
            drawKkMeter(bar, fill, roles.you, roles, background = track)
            drawDataBarSheen(bar, fill, renderTime)
        }
        KkLevelTier.T4 -> {
            drawKkMeter(bar, fill, roles.you, roles, background = Kk.Ink3, fillBrush = kkFillBrush(KkFillKind.BADGE_STRIPES, roles.you, Kk.Ink))
            drawDataBarOutline(bar, roles.you.copy(alpha = 0.6f))
        }
        KkLevelTier.T5 -> {
            drawKkMeter(bar, fill, gold.base, roles, background = gold.base.copy(alpha = 0.14f), fillBrush = kkFoilBrush(bar.width * 2.6f, gold))
            drawDataBarSheen(bar, fill, renderTime)
        }
        KkLevelTier.T6 -> when (apexState) {
            KkApexState.READY -> drawKkMeter(bar, 1f, gold.base, roles, background = Kk.Ink3,
                fillBrush = kkFoilBrush(bar.width * 2.6f, gold), fillAlpha = apexReadyAlpha(renderTime))
            KkApexState.ACTIVE -> drawKkMeter(bar, fill, gold.base, roles, background = Kk.Ink3, stripes = KkMeterStripes.GOLD, time = renderTime)
            KkApexState.COOLING, KkApexState.NONE -> {
                drawKkMeter(bar, fill, gold.base, roles, background = Kk.Ink3)
                drawDataBarOutline(bar, gold.base.copy(alpha = 0.4f))
            }
        }
    }
    if (tier == KkLevelTier.T6 && memory != null) {
        val flash = headFlash(memory, renderTime)
        if (flash > 0f) {
            val segment = d(HEAD_FLASH_DP)
            val head = (bar.left + bar.width * fill - segment * 0.5f).coerceIn(bar.left, max(bar.left, bar.right - segment))
            withKkShear(bar.center.y) { drawRect(Kk.Bone, Offset(head, bar.top), Size(segment, bar.height), flash) }
        }
    }
}

private fun DrawScope.drawDataBarSheen(bar: Rect, fill: Float, renderTime: Float) {
    if (fill <= 0f) return
    val filled = HudDrawCache.paths.sheared(HudPath.DATA_FILL.ordinal, bar.left, bar.top, bar.left + bar.width * fill, bar.bottom)
    drawKkSheen(filled, bar, renderTime, 2.6f, DataBarSheen)
}

private fun DrawScope.drawDataBarOutline(bar: Rect, color: Color) {
    drawPath(HudDrawCache.paths.sheared(HudPath.DATA_BAR.ordinal, bar), color, style = kkStroke(d(1f)))
}

private val DataBarSheen = Color.White.copy(alpha = 0.6f)

/**
 * The level badge's one-shots this frame from [HudPresentationMemory] (draw-thread scratch): the
 * level slam, or the longer Apex unlock slam; the tier change's echo growth and bone shutter; the
 * unlock and cast flashes, the unlock's growing rays and the cast's kick. Settled without a memory.
 */
internal class HudBadgeOneShots {
    var slam = 1f
        private set
    var slamFrom = 1.3f
        private set
    var slamKick = 5f
        private set
    var kick = 1f
        private set
    var echoGrow = 1f
        private set
    var shutter = 1f
        private set
    var flash = 0f
        private set
    var raysScale = 1f
        private set

    fun update(memory: HudPresentationMemory?, renderTime: Float): HudBadgeOneShots {
        slam = 1f
        slamFrom = 1.3f
        slamKick = 5f
        kick = 1f
        echoGrow = 1f
        shutter = 1f
        flash = 0f
        raysScale = 1f
        if (memory == null) return this
        val unlockMs = memory.apexUnlock(renderTime) * KkTime.ApexUnlock
        if (unlockMs < KkTime.ApexUnlock) {
            // Reaching the cap: a bigger, longer slam turning in from −8°, a bone flash, the rays growing in.
            slam = (unlockMs / APEX_SLAM_MS).coerceIn(0f, 1f)
            slamFrom = 1.6f
            slamKick = 8f
            flash = snapOut(unlockMs)
            raysScale = kkLerp(0.2f, 1f, KkEase.Out.transform((unlockMs / KkTime.Drift).coerceIn(0f, 1f)))
        } else {
            slam = memory.levelSlam(renderTime)
        }
        val tierMs = memory.tierBurst(renderTime) * KkTime.TierBurst
        if (tierMs < KkTime.TierBurst) {
            echoGrow = KkEase.Pull.transform((tierMs / KkTime.Pull).coerceIn(0f, 1f))
            if (tierMs >= KkTime.Snap && tierMs < KkTime.Snap + KkTime.Pull) shutter = (tierMs - KkTime.Snap) / KkTime.Pull
        }
        val castMs = memory.apexCast(renderTime) * HudPresentationMemory.APEX_CAST_MS
        if (castMs < HudPresentationMemory.APEX_CAST_MS) {
            flash = max(flash, snapOut(castMs))
            kick = KkSlam.scale((castMs / KkTime.Pull).coerceIn(0f, 1f), from = 0.92f)
        }
        return this
    }

    /** A bone flash fading over [KkTime.Snap] ms (Snap). */
    private fun snapOut(elapsedMs: Float): Float =
        if (elapsedMs >= KkTime.Snap) 0f else 1f - KkEase.Snap.transform(elapsedMs / KkTime.Snap)

    private companion object {
        const val APEX_SLAM_MS = 420f
    }
}

/** Ring bursts around the badge: tier change, Apex unlock (two), ready ping and cast; radii scale with the badge. */
private fun DrawScope.drawBadgeBursts(badge: Rect, k: Float, roles: KkRolePalette, renderTime: Float, memory: HudPresentationMemory?) {
    if (memory == null) return
    val center = badge.center
    val gold = roles.gold
    drawKkRingBurst(center, d(60f) * k, memory.tierBurst(renderTime),
        if (memory.tierTo == KkLevelTier.T5) gold.base else roles.you, 3f)
    val unlockMs = memory.apexUnlock(renderTime) * KkTime.ApexUnlock
    if (unlockMs < KkTime.ApexUnlock) {
        drawKkRingBurst(center, d(90f) * k, unlockMs / KkTime.Drift, gold.base, 4f)
        drawKkRingBurst(center, d(140f) * k, (unlockMs - UNLOCK_SECOND_RING_MS) / KkTime.Drift, roles.you, 3f)
    }
    drawKkRingBurst(center, d(50f) * k, memory.apexPing(renderTime), gold.base, 3f)
    drawKkRingBurst(center, d(70f) * k, memory.apexCast(renderTime), gold.light, 4f)
}

/** The Apex unlock's second (volt) ring starts this long after the first. */
private const val UNLOCK_SECOND_RING_MS = 180f

/** Level badge (+ thin Data bar on desktop); returns the bottom of the cluster. */
private fun DrawScope.drawLevelCluster(
    engine: GameplayRenderModel,
    measurer: TextMeasurer,
    frame: HudFrame,
    renderTime: Float,
    memory: HudPresentationMemory?,
    controls: RunningControlPressLog?,
): Float {
    val language = measurer.language
    val label = HudScratch.label.of(language, 0L) { language.text(HudRedesignText.LevelLabel) }
    val shots = HudScratch.badgeShots.update(memory, renderTime)
    val level = engine.level.coerceAtLeast(0)
    val apexState = engine.apex.badgeState()
    // An Apex press while it cools shakes the badge (240 ms).
    val shake = apexRefusalShake(controls, renderTime)
    // The numeral is display type (fixed size); the "Lvl" label follows the text size.
    val display = HudMeasurers.display(measurer)
    val growth = hudUiScale(measurer.scale)
    if (frame.regular) {
        val numberSize = frame.t(30f)
        val badge = drawKkLevelBadge(display, Offset(frame.margin + shake, frame.u(26f)), level, label, renderTime, shots.slam,
            labelSize = frame.t(12f) * growth, numberSize = numberSize, maxLevel = engine.maxLevel, apex = apexState,
            apexActive = engine.apex.active01, echoGrow = shots.echoGrow, slamFrom = shots.slamFrom, slamKick = shots.slamKick,
            kick = shots.kick, shutter = shots.shutter, flash = shots.flash, raysScale = shots.raysScale)
        drawBadgeBursts(badge, numberSize / 30f, measurer.roles, renderTime, memory)
        val left = badge.left - shake
        val top = badge.bottom + frame.u(10f)
        // At the cap, with the keyboard in use, the Apex key sits at the bar's left end.
        val apexKey = engine.levelTier() == KkLevelTier.T6 && controls != null && controls.keyHints && keyCapsShown(frame)
        val keyLayout = if (apexKey) apexKeyLayout(engine, measurer, frame) else null
        // The key's plate holds its label (16 u at least); the Data bar starts 4 u right of it.
        val plateWidth = keyLayout?.let { max(frame.u(APEX_KEY_PLATE_DP), it.size.width + frame.u(APEX_KEY_PADDING_DP) * 2f) } ?: 0f
        val barLeft = left + frame.u(4f) + if (keyLayout != null) plateWidth + frame.u(4f) else 0f
        val bar = HudDrawCache.rect(HudRect.DATA_BAR, barLeft, top, left + frame.u(122f), top + frame.u(4f))
        drawDataBar(engine, bar, measurer.roles, renderTime, memory)
        val keyBottom = if (keyLayout != null) drawApexKeyCap(keyLayout, measurer, frame, left, plateWidth, bar.center.y) else bar.bottom
        HudLayoutProbe.record(HudBlock.BADGE, left, badge.top, max(badge.right, bar.right), max(bar.bottom, keyBottom))
        HudScratch.badgeTop = badge.top
        HudScratch.badgeBottom = badge.bottom
        HudScratch.badgeRight = max(badge.right, bar.right)
        return max(bar.bottom, keyBottom)
    }
    val badgeSize = kkLevelBadgeSize(display, level, density, label, PHONE_BADGE_LABEL_SP * growth, PHONE_BADGE_NUMBER_SP)
    val top = if (frame.portrait) portraitRowCenter(frame) - badgeSize.height * 0.5f else frame.u(12f)
    val badge = drawKkLevelBadge(display, Offset(frame.margin + shake, top), level, label, renderTime, shots.slam,
        labelSize = PHONE_BADGE_LABEL_SP * growth, numberSize = PHONE_BADGE_NUMBER_SP, maxLevel = engine.maxLevel, apex = apexState,
        apexActive = engine.apex.active01, echoGrow = shots.echoGrow, slamFrom = shots.slamFrom, slamKick = shots.slamKick,
        kick = shots.kick, shutter = shots.shutter, flash = shots.flash, raysScale = shots.raysScale)
    drawBadgeBursts(badge, PHONE_BADGE_NUMBER_SP / 30f, measurer.roles, renderTime, memory)
    HudLayoutProbe.record(HudBlock.BADGE, badge.left, badge.top, badge.right, badge.bottom)
    HudScratch.badgeTop = badge.top
    HudScratch.badgeBottom = badge.bottom
    HudScratch.badgeRight = badge.right
    return badge.bottom
}

private const val PHONE_BADGE_LABEL_SP = 9f
private const val PHONE_BADGE_NUMBER_SP = 19f

/** The Apex key's plate: 16 u wide at least, its label 2 u in from each side (reference dp). */
private const val APEX_KEY_PLATE_DP = 16f
private const val APEX_KEY_PADDING_DP = 2f

/** The badge's shake after an Apex press found it cooling: 3 dp, 240 ms. */
private fun DrawScope.apexRefusalShake(controls: RunningControlPressLog?, renderTime: Float): Float {
    if (controls == null) return 0f
    val p = (renderTime - controls.apexRefusedAt) / APEX_REFUSAL_SHAKE_SECONDS
    if (p < 0f || p >= 1f) return 0f
    return d(3f) * kotlin.math.sin(p * 6f * kotlin.math.PI.toFloat()) * (1f - p)
}

private const val APEX_REFUSAL_SHAKE_SECONDS = 0.24f

/** The bound Apex key in Ink: mono 700, fitted to 12 u and never below 8 sp (a longer label widens its plate). */
private fun apexKeyLayout(engine: GameplayRenderModel, measurer: TextMeasurer, frame: HudFrame): TextLayoutResult {
    val label = engine.settings.keyBindings.key(GameAction.APEX).label(measurer.language)
    val display = HudMeasurers.display(measurer)
    return HudDrawCache.fitted(HudText.APEX_KEY, display, label, frame.t(KEY_CAP_TEXT_SP), frame.u(12f), uppercase = true,
        minSize = KEY_CAP_MIN_SP) { size -> kkKeyCapStyle(display, size) }
}

/**
 * The Apex key cap at the Data bar's left end: a sheared gold plate [plateWidth] wide with an Ink
 * seam and the bound key's [layout] centred on it. Returns its bottom.
 */
private fun DrawScope.drawApexKeyCap(
    layout: TextLayoutResult,
    measurer: TextMeasurer,
    frame: HudFrame,
    left: Float,
    plateWidth: Float,
    centerY: Float,
): Float {
    val plateLeft = left + frame.u(4f)
    val plate = HudDrawCache.rect(HudRect.APEX_KEY, plateLeft, centerY - frame.u(7f), plateLeft + plateWidth, centerY + frame.u(7f))
    val path = HudDrawCache.paths.sheared(HudPath.APEX_KEY.ordinal, plate)
    drawPath(path, measurer.roles.gold.base)
    drawPath(path, Kk.Ink, style = kkStroke(d(1f)))
    drawKkText(layout, plate.center.x, plate.center.y, Kk.Ink, align = KkAlign.CENTER, valign = KkVAlign.CENTER)
    HudLayoutProbe.record(HudBlock.APEX_KEY, plate.left, plate.top, plate.right, plate.bottom)
    return plate.bottom
}

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
    drawBossBar(engine, measurer, frame, boss, HudTopGeometry.bossTop(frame), HudTopGeometry.bossBarWidth(frame, wideBossBlock(boss.rank)), ghost)
}

/** Line height of the wide display type (`.hud-num`, `.t-wide`). */
internal const val WIDE_LINE_HEIGHT_EM = 0.9f

/** Line height of the condensed type (`.t-cond`). */
internal const val COND_LINE_HEIGHT_EM = 0.86f

/**
 * Geometry of the HUD's top-center column (clock, then the elite / Architect block under it),
 * shared by the renderer and the world keep-out ([WorldHudKeepOut]) so world labels and edge
 * markers follow the HUD at every text size. px for the [HudFrame]'s viewport. An `architect` flag reads as the wide
 * block ([wideBossBlock]: the Architect and the secret bosses).
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

    /** Gap between the boss bar and its status rail. */
    fun bossRailGap(frame: HudFrame): Float = frame.u(if (frame.regular) 6f else 4f)

    /** Height of the boss's status rail (an ink slab of element glyph cells with one segment per stack). */
    fun bossRailHeight(frame: HudFrame): Float = frame.u(if (frame.regular) 16f else 12f)

    /**
     * Bottom of the boss block at the text size [textScale] (the name is the largest it gets),
     * including the reserve of its status rail under the bar, so plates and labels never land where
     * it appears: always on desktop, on landscape phones while the boss carries a status ([rail]);
     * portrait phones draw the rail on the name's row. One px covers the name's layout box, which
     * rounds up to whole pixels.
     */
    fun bossBottom(frame: HudFrame, architect: Boolean, textScale: Float, rail: Boolean = true): Float {
        val name = bossNameSize(frame, architect) * bossNameLineHeight(architect) * frame.density * hudUiScale(textScale)
        val reserve = if (frame.regular || (rail && !frame.portrait)) bossRailGap(frame) + bossRailHeight(frame) else 0f
        return bossTop(frame) + name + frame.density + bossNameGap(frame) + bossBarHeight(frame, architect) + reserve
    }
}

private fun DrawScope.drawBossBar(
    engine: GameplayRenderModel,
    measurer: TextMeasurer,
    frame: HudFrame,
    boss: BossTarget,
    top: Float,
    width: Float,
    ghost: Float,
) {
    val roles = measurer.roles
    val language = measurer.language
    val type = boss.type ?: return
    val rank = boss.rank
    // The Architect and the secret bosses take the wide block; mini-bosses and Elites the narrow one.
    val wide = wideBossBlock(rank)
    val center = frame.width * 0.5f
    val name = HudScratch.bossName.of(language, type.ordinal + 1L) {
        if (type == EnemyType.ARCHITECT) language.text(HudRedesignText.ArchitectTitle) else engine.content.enemy(type).name.localizedContent(language)
    }
    val nameSize = HudTopGeometry.bossNameSize(frame, wide)
    val lineHeight = HudTopGeometry.bossNameLineHeight(wide)
    // The icon slot: a wide boss's weakness glyph while it has one, else a secret boss's anomaly glyph; the narrow
    // block always shows the Elite glyph; the Architect without a weakness shows none.
    val weakness = if (wide) EnemyAffinity.weakness(boss.affinityBits) else EnemyAffinity.NONE
    val slotIcon = when {
        weakness >= 0 -> null
        rank == EnemyRank.SECRET_BOSS -> KkIcon.SYSTEM_ANOMALY
        !wide -> KkIcon.SYSTEM_ELITE
        else -> null
    }
    val icon = weakness >= 0 || slotIcon != null
    val iconSize = if (icon) frame.u(if (frame.regular) 18f else 14f) else 0f
    val gap = if (icon) frame.u(8f) else 0f
    // The status rail runs under the bar; portrait phones keep the boss row's height, so there the
    // rail follows the name on its row.
    val rail = bossRailWidth(engine, frame, boss.statusBits)
    val inlineRail = if (frame.portrait && rail > 0f) rail + frame.u(10f) else 0f
    // UI text: grows with the text size, shrinking to fit the bar's width rather than being cut.
    val layout = HudDrawCache.fitted(HudText.BOSS_NAME, HudMeasurers.ui(measurer), name, nameSize, width - iconSize - gap - inlineRail,
        uppercase = true) { size ->
        if (wide) measurer.typography.wideStyle(size, lineHeightEm = lineHeight) else measurer.typography.condStyle(size, lineHeightEm = lineHeight)
    }
    val rowWidth = iconSize + gap + layout.size.width + inlineRail
    val rowLeft = center - rowWidth * 0.5f
    val rowCenter = top + layout.kkBoxHeight * 0.5f
    if (weakness >= 0) drawKkIcon(elementIcon(weakness), Offset(rowLeft + iconSize * 0.5f, rowCenter), iconSize, roles.element(weakness))
    else if (slotIcon != null) drawKkIcon(slotIcon, Offset(rowLeft + iconSize * 0.5f, rowCenter), iconSize, roles.threat)
    drawKkText(layout, rowLeft + iconSize + gap, rowCenter, Kk.Bone, valign = KkVAlign.CENTER)
    val barTop = top + layout.kkBoxHeight + HudTopGeometry.bossNameGap(frame)
    val left = center - width * 0.5f
    val barHeight = HudTopGeometry.bossBarHeight(frame, wide)
    drawApexStrikes(engine, frame, roles, center + width * 0.5f, barTop)
    var bottom = barTop + barHeight
    if (rail > 0f) {
        if (inlineRail > 0f) {
            drawBossStatusRail(engine, measurer, frame, boss.statusBits, rowLeft + rowWidth - rail,
                rowCenter - HudTopGeometry.bossRailHeight(frame) * 0.5f, rail)
        } else {
            val railTop = barTop + barHeight + HudTopGeometry.bossRailGap(frame)
            drawBossStatusRail(engine, measurer, frame, boss.statusBits, center - rail * 0.5f, railTop, rail)
            bottom = railTop + HudTopGeometry.bossRailHeight(frame)
        }
    }
    val railHalf = if (inlineRail > 0f) 0f else rail * 0.5f
    HudLayoutProbe.record(HudBlock.BOSS, min(min(left, rowLeft), center - railHalf), top, max(max(left + width, rowLeft + rowWidth), center + railHalf),
        bottom)
    val parts = bossBarParts(engine.content, engine.rebirthLevel, type)
    if (parts == 0) {
        // A kind without an encounter spec (the Elite, the Herald): the 16-segment meter.
        val height = barHeight
        val bar = HudDrawCache.rect(HudRect.BOSS_0, left, barTop, left + width, barTop + height)
        drawKkMeter(bar, boss.integrity, roles.threat, roles, background = roles.threat.copy(alpha = 0.18f),
            segments = 16, ghost = ghost, ghostColor = Kk.Bone, threat = true)
        return
    }
    // A boss with a spec: equal parts that empty from the left, one per phase (the Architect three, four at the
    // Singularity tier; a mini-boss three; the Antecedent four). The active part's track takes the hue of the aspect it
    // hardened into, and it stripes while the boss shifts.
    val height = barHeight
    val partGap = frame.u(6f)
    val partWidth = (width - partGap * (parts - 1)) / parts
    val active = EnemyAffinity.phase(boss.affinityBits)
    val aspect = EnemyAffinity.aspect(boss.affinityBits)
    val shifting = EnemyAffinity.shifting(boss.affinityBits)
    for (part in 0 until parts) {
        val partLeft = left + part * (partWidth + partGap)
        val fill = (boss.integrity * parts - (parts - 1 - part)).coerceIn(0f, 1f)
        val partGhost = ((boss.integrity + ghost) * parts - (parts - 1 - part)).coerceIn(0f, 1f) - fill
        val slot = when (part) {
            0 -> HudRect.BOSS_0
            1 -> HudRect.BOSS_1
            2 -> HudRect.BOSS_2
            else -> HudRect.BOSS_3
        }
        val bar = HudDrawCache.rect(slot, partLeft, barTop, partLeft + partWidth, barTop + height)
        val track = if (part == active && aspect >= 0) roles.element(aspect).copy(alpha = 0.22f) else roles.threat.copy(alpha = 0.18f)
        val stripes = part == active && shifting
        drawKkMeter(bar, fill, roles.threat, roles, background = track, ghost = partGhost, ghostColor = Kk.Bone,
            stripes = if (stripes) KkMeterStripes.THREAT else KkMeterStripes.NONE, time = engine.renderElapsed, edge = stripes, threat = true)
    }
}

/** Width of the boss's status rail ([drawBossStatusRail]) for [bits], 0 without statuses. */
private fun bossRailWidth(engine: GameplayRenderModel, frame: HudFrame, bits: Int): Float {
    if (bits == 0) return 0f
    val glyph = railGlyph(frame)
    val segment = railSegment(frame)
    val segmentGap = frame.u(2f)
    val cellGap = railCellGap(frame)
    var width = 0f
    var cells = 0
    for (element in 0 until STATUS_ELEMENT_COUNT) {
        val stacks = EnemyStatusBits.stacks(bits, element)
        if (stacks == 0) continue
        val count = max(stacks, engine.enemyBreakThresholdOf(element, bits)).coerceAtMost(MAX_RAIL_SEGMENTS)
        width += (if (cells > 0) cellGap else 0f) + glyph + frame.u(4f) + count * (segment + segmentGap) - segmentGap
        cells++
    }
    for (mark in RailMarks) if (bits and mark != 0) width += (if (width > 0f) cellGap else 0f) + glyph
    return width + railPad(frame) * 2f
}

/** The Frozen, Brittle and Embrittled marks after the element cells, in this order. */
private val RailMarks = intArrayOf(EnemyStatusBits.FROZEN, EnemyStatusBits.BRITTLE, EnemyStatusBits.EMBRITTLED)

private fun railPad(frame: HudFrame) = frame.u(if (frame.regular) 7f else 5f)

private fun railGlyph(frame: HudFrame) = HudTopGeometry.bossRailHeight(frame) * 0.68f

private fun railSegment(frame: HudFrame) = frame.u(if (frame.regular) 6f else 4.5f)

private fun railCellGap(frame: HudFrame) = frame.u(if (frame.regular) 14f else 10f)

/**
 * The boss's statuses on an ink slab [width] wide from [left], [top], so the world never shows
 * through: one cell per element it carries (its glyph, then one segment per stack with a notch at
 * the Break threshold on this enemy, the last segment before it pulsing while the element is one
 * stack short), then the Frozen, Brittle and Embrittled marks. No numbers. Allocation-free.
 */
private fun DrawScope.drawBossStatusRail(
    engine: GameplayRenderModel,
    measurer: TextMeasurer,
    frame: HudFrame,
    bits: Int,
    left: Float,
    top: Float,
    width: Float,
) {
    val roles = measurer.roles
    val height = HudTopGeometry.bossRailHeight(frame)
    drawPath(HudDrawCache.paths.slab(HudPath.RAIL_SLAB.ordinal, left, top, left + width, top + height, height * 0.35f), Kk.Ink2)
    val glyph = railGlyph(frame)
    val segment = railSegment(frame)
    val segmentGap = frame.u(2f)
    val cellGap = railCellGap(frame)
    var x = left + railPad(frame)
    val middle = top + height * 0.5f
    val segmentHeight = glyph * 0.62f
    val segmentTop = middle - segmentHeight * 0.5f
    val primed = engine.primedElement(bits, EnemyStatusBits.dominant(bits))
    val pulse = 0.35f + 0.65f * (kotlin.math.sin(engine.renderElapsed * RAIL_PRIMED_PULSE) + 1f) * 0.5f
    var first = true
    for (element in 0 until STATUS_ELEMENT_COUNT) {
        val stacks = EnemyStatusBits.stacks(bits, element)
        if (stacks == 0) continue
        if (!first) x += cellGap
        first = false
        val color = roles.element(element)
        drawKkIcon(elementIcon(element), Offset(x + glyph * 0.5f, middle), glyph, color)
        x += glyph + frame.u(4f)
        val threshold = engine.enemyBreakThresholdOf(element, bits)
        val count = max(stacks, threshold).coerceAtMost(MAX_RAIL_SEGMENTS)
        drawKkPips(Offset(x, segmentTop), count, stacks, color, sizeDp = segmentHeight / density, widthDp = segment / density,
            gapDp = segmentGap / density, emptyColor = Kk.Ink4)
        if (element == primed && stacks < count) {
            drawKkPips(Offset(x + stacks * (segment + segmentGap), segmentTop), 1, 1, color.copy(alpha = pulse),
                sizeDp = segmentHeight / density, widthDp = segment / density, gapDp = segmentGap / density)
        }
        if (threshold in 1..count) {
            val notch = x + threshold * (segment + segmentGap) - segmentGap * 0.75f
            drawRect(Kk.Bone, Offset(notch, top + height * 0.14f), Size(segmentGap * 0.5f, height * 0.72f))
        }
        x += count * (segment + segmentGap) - segmentGap
    }
    if (bits and EnemyStatusBits.FROZEN != 0) {
        if (!first) x += cellGap
        first = false
        drawPath(HudDrawCache.paths.diamond(HudPath.RAIL_FROZEN.ordinal, x + glyph * 0.5f, middle, glyph * 0.45f), roles.element(1),
            style = kkStroke(frame.u(1.5f)))
        x += glyph
    }
    if (bits and EnemyStatusBits.BRITTLE != 0) {
        if (!first) x += cellGap
        first = false
        drawLine(Kk.Bone, Offset(x, middle - glyph * 0.4f), Offset(x + glyph * 0.5f, middle), frame.u(1.5f))
        drawLine(Kk.Bone, Offset(x + glyph * 0.5f, middle), Offset(x + glyph * 0.2f, middle + glyph * 0.45f), frame.u(1.5f))
        drawLine(Kk.Bone, Offset(x + glyph * 0.5f, middle), Offset(x + glyph, middle - glyph * 0.1f), frame.u(1.5f))
        x += glyph
    }
    if (bits and EnemyStatusBits.EMBRITTLED != 0) {
        if (!first) x += cellGap
        // Embrittled: the world's dotted ring, as dots.
        val center = Offset(x + glyph * 0.5f, middle)
        for (dot in 0 until EMBRITTLED_DOTS) {
            val angle = dot * (2f * kotlin.math.PI.toFloat() / EMBRITTLED_DOTS)
            drawCircle(Kk.Bone2, frame.u(1.1f), Offset(center.x + kotlin.math.cos(angle) * glyph * 0.42f, center.y + kotlin.math.sin(angle) * glyph * 0.42f))
        }
    }
}

private const val EMBRITTLED_DOTS = 8

/** A rail segment one stack short of its Break pulses four times a second (radians per second). */
private const val RAIL_PRIMED_PULSE = (2.0 * kotlin.math.PI * 4.0).toFloat()

/** Segments a rail cell shows at most (a stack cap is the threshold plus four). */
private const val MAX_RAIL_SEGMENTS = 14

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
            // Right-aligned left of the header's leftmost button: Build, or the FPS counter while it shows.
            val headerLeft = if (engine.settings.showFps) frame.buildCenterX - regularHeaderStep(frame.width, frame.density) else frame.buildCenterX
            var right = headerLeft - frame.u(20f + 10f)
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
            // Display-cased like the chips of the other layouts ("+1,2 МЛН").
            val layout = HudDrawCache.layout(HudText.MATTER, measurer, matter, measurer.typography.condStyle(17f, tabular = true, lineHeightEm = 1f),
                uppercase = true)
            drawKkText(layout, x, centerY, Kk.Bone, valign = KkVAlign.CENTER)
            HudLayoutProbe.record(HudBlock.MATTER_CHIP, x - frame.u(18f), centerY - layout.kkBoxHeight * 0.5f, x + layout.size.width,
                centerY + layout.kkBoxHeight * 0.5f)
            x += layout.size.width + frame.u(12f)
            HudScratch.economyRight = x - frame.u(12f)
            if (keys != null) {
                drawKkIcon(KkIcon.SYSTEM_KEY, Offset(x + frame.u(7f), centerY), frame.u(14f), Kk.Bone)
                val keyLayout = HudDrawCache.layout(HudText.KEYS, measurer, keys, measurer.typography.condStyle(17f, tabular = true, lineHeightEm = 1f),
                    uppercase = true)
                drawKkText(keyLayout, x + frame.u(19f), centerY, Kk.Bone, valign = KkVAlign.CENTER)
                HudLayoutProbe.record(HudBlock.KEY_CHIP, x, centerY - keyLayout.kkBoxHeight * 0.5f, x + frame.u(19f) + keyLayout.size.width,
                    centerY + keyLayout.kkBoxHeight * 0.5f)
                HudScratch.economyRight = x + frame.u(19f) + keyLayout.size.width
            }
        }
        GameplayLayoutMode.COMPACT_PORTRAIT -> {
            val top = frame.top + frame.u(PORTRAIT_CHIP_ROW_DP)
            val height = frame.u(PORTRAIT_CHIP_ROW_HEIGHT_DP)
            var left = frame.margin
            left = drawHudChipFromLeft(measurer, HudPath.MATTER_CHIP, HudText.MATTER, matter, left, top, height, 15f, null, roles)
            HudScratch.economyRight = left
            left += frame.u(6f)
            if (keys != null) {
                HudScratch.economyRight =
                    drawHudChipFromLeft(measurer, HudPath.KEY_CHIP, HudText.KEYS, keys, left, top, height, 15f, KkIcon.SYSTEM_KEY, roles)
            }
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
    HudScratch.chainLeft = Float.POSITIVE_INFINITY
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
            HudScratch.chainLeft = right - width
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
            width = frame.u(PORTRAIT_INTEGRITY_WIDTH_DP) * frame.factor
            bottom = frame.portraitClustersBottom
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
    if (engine.build.invulnerable) {
        // A bone frame flickers around integrity while nothing can hurt it.
        val pad = frame.u(3f)
        drawPath(HudDrawCache.paths.sheared(HudPath.INVULNERABLE_BAR.ordinal, bar.left - pad, bar.top - pad, bar.right + pad, bar.bottom + pad),
            Kk.Bone, alpha = 0.5f + 0.5f * kotlin.math.sin(renderTime * 20f).let { it * it }, style = kkStroke(frame.u(1.5f)))
    }

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

/**
 * Character ability: the form's glyph ([KkFormGeometry]) and a small sheared meter of the mechanic's
 * readiness, segmented by what it counts (facets, nodes, charges, drones, streak).
 */
private fun DrawScope.drawAbilityMeter(engine: GameplayRenderModel, frame: HudFrame, roles: KkRolePalette, right: Float, bottom: Float) {
    val ability = engine.characterAbility
    val width = frame.u(if (frame.regular) 64f else 44f)
    val height = frame.u(if (frame.regular) 5f else 4f)
    val armed = ability.charge >= 1f || ability.parryWindow > 0f
    val rect = HudDrawCache.rect(HudRect.ABILITY, right - width, bottom - height, right, bottom)
    val segments = when (engine.coreShape) {
        CoreShape.PRISM, CoreShape.TESSERACT, CoreShape.HEXAGON -> 4
        CoreShape.CRESCENT -> 2
        CoreShape.PENTAGON -> 5
        else -> 0
    }
    drawKkMeter(rect, ability.charge, if (armed) roles.you else Kk.Bone2, roles, segments = segments)
    val glyph = height * 1.6f
    val center = Offset(right - width - glyph - frame.u(4f), bottom - height * 0.5f)
    val index = engine.coreShape.formIndex()
    val path = KkFormPaths.of(index, glyph)
    val color = if (armed) roles.you else Kk.Bone2
    translate(center.x, center.y) {
        if (KkFormGeometry.isStroked(index)) {
            drawPath(path, color, style = kkStroke(max(1f, KkFormGeometry.strokeWidth(index, glyph))))
        } else {
            drawPath(path, color)
        }
    }
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
            drawBurnout(engine, od, roles)
            val ladderBottom = od.top - frame.u(8f)
            val ladder = HudDrawCache.rect(HudRect.LADDER, left, ladderBottom - frame.u(16f), left + width, ladderBottom)
            drawKkTickLadder(ladder, roles, ticks, lit, hotFrom, maxFrom)
            drawSpeedNumber(measurer, value, measurer.typography.wideStyle(frame.t(56f), tabular = true, trackingEm = colorTracking(speedRole)), frame.width * 0.5f,
                ladder.top - frame.u(10f), KkAlign.CENTER, stretch, speedColor, od)
        }
        GameplayLayoutMode.COMPACT_LANDSCAPE -> {
            val width = frame.u(230f) * frame.factor
            val left = frame.margin + frame.u(248f) * frame.factor
            val bottom = frame.height - frame.u(12f)
            val od = HudDrawCache.rect(HudRect.OVERDRIVE, left, bottom - frame.u(4f), left + width, bottom)
            drawKkMeter(od, overdrive, roles.you, roles, stripes = stripes, time = renderTime)
            drawBurnout(engine, od, roles)
            val ladderBottom = od.top - frame.u(6f)
            val ladder = HudDrawCache.rect(HudRect.LADDER, left, ladderBottom - frame.u(12f), left + width, ladderBottom)
            drawKkTickLadder(ladder, roles, ticks, lit, hotFrom, maxFrom)
            drawSpeedNumber(measurer, value, measurer.typography.wideStyle(30f, tabular = true, trackingEm = colorTracking(speedRole)), left, ladder.top - frame.u(5f),
                KkAlign.START, stretch, speedColor, od)
        }
        GameplayLayoutMode.COMPACT_PORTRAIT -> {
            val right = frame.width - frame.margin
            val bottom = frame.portraitClustersBottom
            // Right of the minimap when it shows: the meter starts after it and the number fits the room beside it.
            val minimapRight = HudMinimapGeometry.portraitRight(engine, frame)
            val gap = frame.u(8f)
            var left = right - frame.u(160f) * frame.factor
            if (!minimapRight.isNaN()) left = max(left, minimapRight + gap)
            val od = HudDrawCache.rect(HudRect.OVERDRIVE, left, bottom - frame.u(4f), right, bottom)
            drawKkMeter(od, overdrive, roles.you, roles, stripes = stripes, time = renderTime)
            drawBurnout(engine, od, roles)
            drawSpeedNumber(measurer, value, measurer.typography.wideStyle(30f, tabular = true, trackingEm = colorTracking(speedRole)), right, od.top - frame.u(7f),
                KkAlign.END, stretch, speedColor, od, maxWidth = if (minimapRight.isNaN()) Float.POSITIVE_INFINITY else right - minimapRight - gap)
        }
    }
}

/**
 * Overdrive Cascade's Burnout (no charge during Overdrive and for 3 s after): the overdrive meter
 * is hatched in the threat hue while charge cannot build.
 */
private fun DrawScope.drawBurnout(engine: GameplayRenderModel, meter: androidx.compose.ui.geometry.Rect, roles: KkRolePalette) {
    if (!engine.build.overdriveBurnout) return
    drawKkHatch(meter, roles.threat.copy(alpha = BURNOUT_HATCH_ALPHA))
}

private const val BURNOUT_HATCH_ALPHA = 0.55f

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
    tierStretch: Float,
    color: Color,
    gauge: androidx.compose.ui.geometry.Rect,
    maxWidth: Float = Float.POSITIVE_INFINITY,
) {
    val top = bottom - digitBoxHeight(measurer, style)
    // Within [maxWidth] with its lean, the number gives up stretch (the tier cue) before it would run past it.
    val natural = kkTabularNumberWidth(measurer, value, style)
    val stretch = min(tierStretch, (maxWidth - (bottom - top) * SPEED_LEAN) / natural)
    withTransform({
        scale(stretch, 1f, Offset(x, bottom))
        kkShear(bottom, -8f)
    }) {
        drawKkTabularNumber(measurer, value, style, x, top, color, align)
    }
    // The stretched number with its lean (the top leans right by tan 8° of its height) and the gauge under it (the
    // ladder and the overdrive meter).
    val width = natural * stretch
    val lean = (bottom - top) * SPEED_LEAN
    val left = when (align) {
        KkAlign.START -> x
        KkAlign.CENTER -> x - width * 0.5f
        KkAlign.END -> x - width
    }
    HudLayoutProbe.record(HudBlock.SPEED, min(left - lean, gauge.left), top, max(left + width + lean, gauge.right), gauge.bottom)
}

/** tan(8°): the speed number's lean. */
private const val SPEED_LEAN = 0.1405f

/** Portrait phones: the integrity cluster's width (dp, before the narrow-phone factor) and the loadout slot's side. */
internal const val PORTRAIT_INTEGRITY_WIDTH_DP = 170f
internal const val PORTRAIT_LOADOUT_SLOT_DP = 34f

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
            val rowLeft = drawRelicRow(engine, content, slots, right, relicCenter, relicSize, frame, roles)
            HudLayoutProbe.record(HudBlock.LOADOUT, min(slot.left, rowLeft),
                relicCenter - relicSize * 0.5f - relicBracketReach(frame), right, bottom)
            HudLayoutProbe.extend(HudBlock.LOADOUT, HudBlock.SEVERED)
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
            val rowLeft = drawRelicRow(engine, content, slots, slot.left - frame.u(8f), slot.center.y, relicSize, frame, roles)
            HudLayoutProbe.record(HudBlock.LOADOUT, rowLeft,
                slot.center.y - relicSize * 0.5f - relicBracketReach(frame), max(right, levelX + levelLayout.size.width * 0.5f),
                slot.bottom + frame.u(4f) + levelLayout.kkBoxHeight)
            HudLayoutProbe.extend(HudBlock.LOADOUT, HudBlock.SEVERED)
        }
        GameplayLayoutMode.COMPACT_PORTRAIT -> {
            val top = frame.portraitLoadoutTop
            val slotSize = frame.u(PORTRAIT_LOADOUT_SLOT_DP)
            val slot = HudDrawCache.rect(HudRect.WEAPON, frame.margin, top, frame.margin + slotSize, top + slotSize)
            drawKkWeaponSlot(measurer, slot, icon, maxLevel = maxLevel, iconSizeDp = 20f)
            val relicSize = frame.u(22f)
            // The row (and the ghosts of replaced relics left of it) is drawn first: the level text takes what is left.
            val relicLeft = drawRelicRow(engine, content, slots, frame.width - frame.margin, slot.center.y, relicSize, frame, roles)
            val levelLayout = HudDrawCache.fitted(HudText.WEAPON_LEVEL, measurer, levelText, 11f,
                relicLeft - frame.u(12f) - slot.right - frame.u(8f)) { size -> weaponLevelStyle(measurer, size) }
            drawKkText(levelLayout, slot.right + frame.u(8f), slot.center.y, if (maxLevel) Kk.RLegend else Kk.Bone, valign = KkVAlign.CENTER)
            HudLayoutProbe.record(HudBlock.LOADOUT, slot.left, min(slot.top, slot.center.y - relicSize * 0.5f - relicBracketReach(frame)),
                frame.width - frame.margin, slot.bottom)
        }
    }
}

private fun weaponLevelStyle(measurer: TextMeasurer, size: Float) =
    measurer.typography.monoStyle(size, weight = FontWeight.Bold, trackingEm = 0f, lineHeightEm = 1f)

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
 * Relic diamonds right-aligned at [right] on an even pitch. Links come from [hudRelicLinks] (named
 * pairings and dissonance, in any slots, the same reading as pause and the relic matrix):
 * neighbouring members are joined by a link bar, members further apart by a bracket above the
 * row. Brackets whose slot spans overlap or touch stack on separate levels (as the relic matrix
 * does), each level's legs ending above the level below, so interleaved links never merge into one
 * line. Replaced relics whose cost lingers follow as ghosts ([drawSeveredRelics]); returns the
 * left edge of the row and its ghosts (px).
 */
private fun DrawScope.drawRelicRow(
    engine: GameplayRenderModel,
    content: GameplayContentSnapshot,
    slots: Int,
    right: Float,
    centerY: Float,
    size: Float,
    frame: HudFrame,
    roles: KkRolePalette,
): Float {
    if (slots <= 0) return right
    val relics = engine.equippedRelics
    val gap = relicRowGap(frame)
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
            drawKkSynergyLink(Offset(ax, centerY), Offset(bx, centerY), link.overlayColor())
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
        val color = links[brackets.link[index]].overlayColor()
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
    val build = engine.build
    for (index in 0 until slots) {
        val relic = relics.getOrNull(index)
        val center = Offset(firstCenter + index * pitch, centerY)
        if (relic == null) {
            drawKkRelicSlot(center, Color.Unspecified, sizeDp = sizeDp)
            continue
        }
        val definition = hudRelicDefinition(content, relic.id)
        val color = relicHue(definition, roles)
        val spent = relic.id == RelicId.OUROBOROS_SEAL && build.ouroborosSpent
        // The aspect rim carries the element; the relic's own rune sits inside it.
        drawKkRelicSlot(center, color, sizeDp = sizeDp, alpha = if (spent) 0.45f else 1f)
        drawRelicRune(definition, center, half * RUNE_IN_SLOT, color, if (spent) 0.4f else 1f)
        drawRelicGauge(engine, relic.id, center, half, color, frame, roles)
    }
    // The diamonds with the reach of their gauges (Stormheart's ring, Glass Comet's diamond, the Stasis bar).
    val reach = relicGaugeReach(frame)
    HudLayoutProbe.record(HudBlock.RELIC_ROW, firstCenter - half - reach, centerY - half - reach, right + reach,
        centerY + half + reach)
    return drawSeveredRelics(engine, content, firstCenter, centerY, size, frame, roles)
}

/** Space between the relic diamonds of the HUD row. */
private fun relicRowGap(frame: HudFrame): Float = frame.u(if (frame.regular) 10f else 6f)

/** How far a relic's gauge reaches past its diamond (Stormheart's ring with its stroke, the invulnerability diamond). */
private fun relicGaugeReach(frame: HudFrame): Float = frame.u(6f)

/** Medallion radius of a rune inside its HUD diamond, as a share of the diamond's half size. */
private const val RUNE_IN_SLOT = 0.62f

/**
 * Small state marks of the relics that keep one (all from the build projection): Stormheart's
 * charge as an arc around its diamond (a pulsing ring when full), the Stasis meter as a bar under
 * it, Glass Comet's invulnerability as a bright ring, a spent Ouroboros seal struck through.
 */
private fun DrawScope.drawRelicGauge(
    engine: GameplayRenderModel,
    id: RelicId,
    center: Offset,
    half: Float,
    color: Color,
    frame: HudFrame,
    roles: KkRolePalette,
) {
    val build = engine.build
    when (id) {
        RelicId.STORMHEART_CAPACITOR -> {
            val charge = build.stormCharge.coerceIn(0f, 1f)
            val radius = half + frame.u(4f)
            val stroke = kkStroke(frame.u(2f))
            drawCircle(color, radius, center, alpha = 0.18f, style = stroke)
            if (charge > 0f) {
                val alpha = if (charge >= 1f) 0.6f + 0.4f * kotlin.math.sin(engine.renderElapsed * 12f) else 0.95f
                drawArc(color, -90f, 360f * charge, false, Offset(center.x - radius, center.y - radius), Size(radius * 2f, radius * 2f),
                    alpha = alpha, style = stroke)
            }
        }
        RelicId.STASIS_ANCHOR -> {
            val meter = build.stasisMeter.coerceIn(0f, 1f)
            val top = center.y + half + frame.u(3f)
            val height = frame.u(3f)
            val left = center.x - half * 0.8f
            val width = half * 1.6f
            drawRect(color, Offset(left, top), Size(width, height), alpha = 0.2f)
            drawRect(color, Offset(left, top), Size(width * meter, height))
            if (build.stasisBank > 0) drawCircle(Kk.Bone, frame.u(2f), Offset(left + width + frame.u(4f), top + height * 0.5f))
        }
        RelicId.GLASS_COMET -> if (build.invulnerable) {
            drawPath(HudDrawCache.paths.diamond(HudPath.INVULNERABLE.ordinal, center.x, center.y, half + frame.u(5f)), Kk.Bone,
                alpha = 0.55f + 0.45f * kotlin.math.sin(engine.renderElapsed * 18f), style = kkStroke(frame.u(1.5f)))
        }
        RelicId.OUROBOROS_SEAL -> if (build.ouroborosSpent) {
            drawLine(roles.threat, Offset(center.x - half * 0.7f, center.y + half * 0.7f), Offset(center.x + half * 0.7f, center.y - half * 0.7f),
                frame.u(2f))
        }
        else -> Unit
    }
}

/**
 * Replaced relics whose cost still lingers ([BuildProjection.severed]): a faded rune ringed by a
 * threat arc that drains with the remaining time, clear of the row's gauges. Desktop and portrait
 * put them left of the row, nearest first; landscape phones, where the feed docks left of the
 * loadout, put them under the row from its first slot. Returns the left edge of the row with them.
 */
private fun DrawScope.drawSeveredRelics(
    engine: GameplayRenderModel,
    content: GameplayContentSnapshot,
    firstCenter: Float,
    centerY: Float,
    size: Float,
    frame: HudFrame,
    roles: KkRolePalette,
): Float {
    val half = size * 0.5f
    val rowLeft = firstCenter - half - relicGaugeReach(frame)
    val severed = engine.build.severed
    if (severed.isEmpty()) return rowLeft
    val total = content.build.policy.severanceSeconds.coerceAtLeast(1f)
    val ghostHalf = size * 0.36f
    val radius = ghostHalf * 1.25f
    val stroke = frame.u(2f)
    val outer = radius + stroke * 0.5f
    val step = outer * 2f + frame.u(4f)
    val below = frame.mode == GameplayLayoutMode.COMPACT_LANDSCAPE
    val shown = minOf(severed.size, MAX_SEVERED_SHOWN)
    val firstX = if (below) firstCenter else rowLeft - relicRowGap(frame) - outer
    val ghostY = if (below) centerY + half + relicGaugeReach(frame) + frame.u(2f) + outer else centerY
    for (index in 0 until shown) {
        val ghost = severed[index]
        val center = Offset(if (below) firstX + index * step else firstX - index * step, ghostY)
        val definition = hudRelicDefinition(content, ghost.id)
        drawRelicRune(definition, center, ghostHalf, relicHue(definition, roles), 0.38f)
        val share = (ghost.remaining / total).coerceIn(0f, 1f)
        drawArc(roles.threat, -90f, 360f * share, false, Offset(center.x - radius, center.y - radius), Size(radius * 2f, radius * 2f),
            style = kkStroke(stroke))
    }
    val left = if (below) firstX - outer else firstX - (shown - 1) * step - outer
    val right = if (below) firstX + (shown - 1) * step + outer else firstX + outer
    HudLayoutProbe.record(HudBlock.SEVERED, left, ghostY - outer, right, ghostY + outer)
    return if (below) rowLeft else left
}

private const val MAX_SEVERED_SHOWN = 2

/** Relic definitions by id, indexed once per content snapshot (content lookups iterate the catalog). */
internal fun hudRelicDefinition(content: GameplayContentSnapshot, id: RelicId): kinetickk.ball.content.api.RelicDefinition {
    val cache = HudScratch
    if (cache.definitionContent !== content) {
        cache.definitionContent = content
        cache.definitions.fill(null)
    }
    return cache.definitions[id.ordinal] ?: content.relic(id).also { cache.definitions[id.ordinal] = it }
}

/** Vertical distance between stacked synergy bracket levels, in px. */
private fun bracketLevelStep(frame: HudFrame): Float = frame.u(if (frame.regular) 8f else 6f)

/**
 * Links of the equipped [relics] (slot order) that the relic row marks, as [overlayRelicLinks]
 * reads them: named pairings and dissonance only. A shared key's harmony shows as the upside
 * multipliers in pause and on relic cards; four relics of one key would otherwise stack six
 * brackets. Recomputed (with the stacked levels of their brackets) only when the relic list or the
 * content changes. The HUD never names a link, so pairing discovery does not matter here.
 */
internal fun hudRelicLinks(content: GameplayContentSnapshot, relics: List<EquippedRelic>): List<OverlayRelicLink> {
    val cache = HudScratch
    if (cache.linkRelics !== relics || cache.linkContent !== content) {
        cache.linkRelics = relics
        cache.linkContent = content
        cache.links = overlayRelicLinks(relics.map { it.id }, content) { true }.filter { it.kind != RelicLinkKind.HARMONY }
        cache.brackets.place(cache.links)
    }
    return cache.links
}

/**
 * Brackets of the relic row: one per pair of consecutive members of a link that are not
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

    fun place(links: List<OverlayRelicLink>) {
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

/**
 * Running controls: Dash and Brake slabs, pause and performance icon buttons, and the Z/X/C skill row
 * ([drawSkillBar]). The Apex control is the level badge itself.
 */
internal fun DrawScope.drawControls(
    engine: GameplayRenderModel,
    textMeasurer: TextMeasurer,
    renderTime: Float = 0f,
    memory: HudPresentationMemory? = null,
    controls: RunningControlPressLog? = null,
) {
    val roles = textMeasurer.roles
    val frame = HudScratch.frame.update(size.width, size.height, density)
    val regular = frame.regular
    forEachRunningControlBounds(size.width, size.height, density, RunningControlMask.of(engine)) { target, left, top, right, bottom ->
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
            // Placed while Show FPS is on: the counter itself is the control (a tap toggles the full panel).
            RunningControlTarget.PERFORMANCE -> drawHudIconButton(frame, HudPath.PERFORMANCE, left, top, right, bottom, regular) { center, _ ->
                drawFpsCounter(textMeasurer, frame, center, controls?.fps ?: GameplayFpsCounter.NO_VALUE)
            }
            // The skill row draws as one (its entrance moves all three); the Apex is the level badge.
            RunningControlTarget.SKILL_1, RunningControlTarget.SKILL_2, RunningControlTarget.SKILL_3, RunningControlTarget.APEX -> Unit
        }
    }
    drawSkillBar(engine, textMeasurer, frame, renderTime, memory, controls)
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

/**
 * The Show FPS counter inside the PERFORMANCE slot: the measured value (mono 12, "–" until the first
 * window closes) over an "FPS" label (8). Cached per value; no allocation once a value was shown.
 */
private fun DrawScope.drawFpsCounter(measurer: TextMeasurer, frame: HudFrame, center: Offset, fps: Int) {
    val display = HudMeasurers.display(measurer)
    val value = HudScratch.fps.of(fps.toLong())
    val number = HudDrawCache.layout(HudText.FPS, display, value,
        measurer.typography.monoStyle(frame.t(12f), weight = FontWeight.Bold, trackingEm = 0f, lineHeightEm = 1f))
    val label = HudDrawCache.layout(HudText.FPS_LABEL, display, FPS_LABEL, measurer.typography.labelStyle(frame.t(8f)), uppercase = true)
    val gap = frame.u(1f)
    val top = center.y - (number.kkBoxHeight + gap + label.kkBoxHeight) * 0.5f
    drawKkText(number, center.x, top, Kk.Bone, KkAlign.CENTER)
    drawKkText(label, center.x, top + number.kkBoxHeight + gap, Kk.Mute, KkAlign.CENTER)
    val half = max(number.size.width, label.size.width) * 0.5f
    HudLayoutProbe.record(HudBlock.FPS, center.x - half, top, center.x + half, top + number.kkBoxHeight + gap + label.kkBoxHeight)
}

/** The counter's unit, the same in every language (like the panel's own "FPS"). */
private const val FPS_LABEL = "FPS"

/** Performance mark: three rising bars. */
internal fun DrawScope.drawPerformanceGlyph(center: Offset, size: Float, color: Color = Kk.Bone) {
    val unit = size / 24f
    val stroke = 2f * unit
    val base = center.y + 7f * unit
    drawLine(color, Offset(center.x - 6f * unit, base), Offset(center.x - 6f * unit, base - 6f * unit), stroke, StrokeCap.Square)
    drawLine(color, Offset(center.x, base), Offset(center.x, base - 11f * unit), stroke, StrokeCap.Square)
    drawLine(color, Offset(center.x + 6f * unit, base), Offset(center.x + 6f * unit, base - 15f * unit), stroke, StrokeCap.Square)
}
