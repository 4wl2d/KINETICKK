// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.hud

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import kinetickk.ball.gameplay.interaction.localization.HudRedesignText
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.max
import kotlin.math.min
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.ball.gameplay.interaction.layout.*

/** The ready Apex meters' foil breathes between 75 % and full. */
private fun apexReadyAlpha(renderTime: Float): Float = 0.75f + 0.25f * kkPulse(renderTime, APEX_PULSE_SECONDS)

private const val APEX_PULSE_SECONDS = 1.2f

/** The tier change's bone overlay on the Data line and the Apex unlock's gold sweep (ms). */
private const val METER_FLASH_MS = 420f

/** Width of the bone segment that flashes at the meters' fill head (Apex ping and refund), dp. */
private const val HEAD_FLASH_DP = 16f

/** Data line along the top edge (all modes; at the cap the Apex meter, 4 dp instead of 3). */
internal fun DrawScope.drawDataLine(
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
internal fun DrawScope.drawLevelCluster(
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

internal const val PHONE_BADGE_LABEL_SP = 9f
internal const val PHONE_BADGE_NUMBER_SP = 19f

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
