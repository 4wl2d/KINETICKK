// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_HUD_TOP_DP
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_PANEL_ROW_DP
import kinetickk.ball.gameplay.interaction.layout.RUNNING_CONTROL_MIN_DP
import kinetickk.ball.gameplay.interaction.layout.compactHudFactor
import kinetickk.ball.gameplay.interaction.layout.gameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.regularHudUnit
import kinetickk.ball.gameplay.interaction.layout.runningHudMargin
import kinetickk.ball.gameplay.interaction.localization.HudRedesignText
import kinetickk.ball.gameplay.nucleus.model.formatRunTime
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.PointOfInterestProjection
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The active point-of-interest trial, if any (the game runs at most one at a time). */
internal fun GameplayRenderModel.activeTrial(): PointOfInterestProjection? {
    val points = pointsOfInterest
    for (index in points.indices) if (points[index].active) return points[index]
    return null
}

/** The trial's rules for its (!) info button: the content instruction in the player's language. */
internal fun GameplayRenderModel.trialRules(point: PointOfInterestProjection, language: AppLanguage): String =
    content.pointsOfInterest.definition(point.kind).instruction.localizedContent(language)

/**
 * Geometry of the top-left trial panel (`Anomaly` board), shared by the renderer and the focusable
 * semantics node of its (!) info button. All values are canvas px; [update] rewrites them.
 */
internal class HudTrialPanelLayout {
    var compact = false
        private set
    var left = 0f
        private set
    var top = 0f
        private set
    var right = 0f
        private set
    var bottom = 0f
        private set
    var unit = 1f
        private set
    var labelSize = 15f
        private set
    var monoSize = 11f
        private set
    var nameSize = 32f
        private set
    var padding = 18f
        private set
    var rowLabelCenter = 0f
        private set
    var rowNameCenter = 0f
        private set
    var meterTop = 0f
        private set
    var meterHeight = 0f
        private set
    var rowValueCenter = 0f
        private set
    var infoLeft = 0f
        private set
    var infoTop = 0f
        private set
    var infoSize = 0f
        private set

    /** Lays the panel out for a [width] × [height] canvas at [density] with the text-size [textScale]. */
    fun update(width: Float, height: Float, density: Float, textScale: Float): HudTrialPanelLayout {
        val scale = density.coerceAtLeast(1f)
        val text = textScale.coerceAtLeast(0.5f)
        val mode = gameplayLayoutMode(width, height, scale)
        val margin = runningHudMargin(width, height, scale)
        compact = mode != GameplayLayoutMode.REGULAR
        unit = if (compact) scale else regularHudUnit(width, scale)
        val k = unit / scale
        labelSize = if (compact) 12f else 15f * k
        monoSize = if (compact) 10f else 11f * k
        nameSize = if (compact) 22f else 32f * k
        padding = if (compact) 12f * unit else 18f * unit
        val topPadding = if (compact) 10f * unit else 16f * unit
        left = margin
        val panelWidth = when (mode) {
            GameplayLayoutMode.REGULAR -> 330f * unit
            GameplayLayoutMode.COMPACT_LANDSCAPE -> 240f * unit * compactHudFactor(width, scale, portrait = false)
            GameplayLayoutMode.COMPACT_PORTRAIT -> width - margin * 2f
        }
        right = left + panelWidth
        top = when (mode) {
            GameplayLayoutMode.REGULAR -> (91f + 27f * text) * unit
            GameplayLayoutMode.COMPACT_LANDSCAPE -> (31f + 17f * text) * unit
            // Below the chip row and the reserved boss row, so an elite never covers the panel.
            GameplayLayoutMode.COMPACT_PORTRAIT -> (PORTRAIT_HUD_TOP_DP + PORTRAIT_PANEL_ROW_DP + (text - 1f) * 10f) * unit
        }
        val labelRow = max(labelSize, monoSize * 1.35f) * text * scale
        val nameRow = nameSize * 0.86f * text * scale
        infoSize = (if (compact) 20f else 24f) * unit
        rowLabelCenter = top + topPadding + labelRow * 0.5f
        val nameTop = top + topPadding + labelRow + (if (compact) 6f else 10f) * unit
        val nameHeight = max(nameRow, infoSize)
        rowNameCenter = nameTop + nameHeight * 0.5f
        meterTop = nameTop + nameHeight + (if (compact) 8f else 14f) * unit
        meterHeight = (if (compact) 7f else 10f) * unit
        val valueRow = monoSize * 1.35f * text * scale
        rowValueCenter = meterTop + meterHeight + (if (compact) 6f else 8f) * unit + valueRow * 0.5f
        bottom = rowValueCenter + valueRow * 0.5f + (if (compact) 12f else 18f) * unit
        infoLeft = right - padding - infoSize
        infoTop = rowNameCenter - infoSize * 0.5f
        return this
    }

    /** Focus/touch bounds of the (!) button: at least [RUNNING_CONTROL_MIN_DP] square around it. */
    fun infoTarget(density: Float): Rect {
        val size = max(infoSize, RUNNING_CONTROL_MIN_DP * density.coerceAtLeast(1f))
        val cx = infoLeft + infoSize * 0.5f
        val cy = infoTop + infoSize * 0.5f
        return Rect(cx - size * 0.5f, cy - size * 0.5f, cx + size * 0.5f, cy + size * 0.5f)
    }

    /** Whether ([x], [y]) hits the (!) touch target (the same square as [infoTarget]); allocation-free. */
    fun infoTargetContains(x: Float, y: Float, density: Float): Boolean {
        val half = max(infoSize, RUNNING_CONTROL_MIN_DP * density.coerceAtLeast(1f)) * 0.5f
        val cx = infoLeft + infoSize * 0.5f
        val cy = infoTop + infoSize * 0.5f
        return x >= cx - half && x <= cx + half && y >= cy - half && y <= cy + half
    }
}

private object TrialScratch {
    val layout = HudTrialPanelLayout()
    val label = HudKeyedText()
    val name = HudKeyedText()
    val clock = HudNumberText { formatRunTime(it.toFloat()) }
    val progress = HudKeyedText()
    val reward = HudKeyedText()
    val rules = HudKeyedText()
    val orbitTail = HudKeyedText()
}

/** Top-left trial panel: label, clock, name + (!), segmented progress, value and reward. */
internal fun DrawScope.drawTrialPanel(
    engine: GameplayRenderModel,
    measurer: TextMeasurer,
    renderTime: Float,
    infoOpen: Boolean,
) {
    val point = engine.activeTrial() ?: return
    val roles = measurer.roles
    val language = measurer.language
    val layout = TrialScratch.layout.update(size.width, size.height, density, measurer.scale)
    val unit = layout.unit
    val panel = HudDrawCache.rect(HudRect.TRIAL_ANCHOR, layout.left, layout.top, layout.right, layout.bottom)
    val cut = (if (layout.compact) 10f else 14f) * unit
    drawPath(HudDrawCache.paths.chamfer(HudPath.TRIAL_PANEL.ordinal, panel, cut), Kk.Ink2.copy(alpha = 0.92f))
    drawRect(roles.you, Offset(panel.left, panel.top), Size(panel.width - cut, 3f * unit))

    val innerLeft = panel.left + layout.padding
    val innerRight = panel.right - layout.padding
    // Row 1: anomaly glyph + label, remaining clock.
    val iconSize = (if (layout.compact) 13f else 16f) * unit
    drawKkIcon(KkIcon.SYSTEM_ANOMALY, Offset(innerLeft + iconSize * 0.5f, layout.rowLabelCenter), iconSize, roles.you)
    val labelText = TrialScratch.label.of(language, 0L) { language.text(HudRedesignText.AnomalyTrial) }
    val clockLayout = HudDrawCache.layout(HudText.TRIAL_CLOCK, measurer, TrialScratch.clock.of(ceil(point.remaining).toLong()),
        measurer.typography.monoStyle(layout.monoSize))
    drawKkText(clockLayout, innerRight, layout.rowLabelCenter, Kk.Bone, KkAlign.END, KkVAlign.CENTER)
    val labelLeft = innerLeft + iconSize + 8f * unit
    val labelLayout = HudDrawCache.layout(HudText.TRIAL_LABEL, measurer, labelText, measurer.typography.labelStyle(layout.labelSize),
        uppercase = true, maxWidth = (innerRight - clockLayout.size.width - 8f * unit - labelLeft).coerceAtLeast(1f))
    drawKkText(labelLayout, labelLeft, layout.rowLabelCenter, roles.you, valign = KkVAlign.CENTER)

    // Row 2: trial name + (!) info.
    val nameText = TrialScratch.name.of(language, point.kind.ordinal.toLong()) { point.name.localizedContent(language) }
    val nameWidth = (layout.infoLeft - 10f * unit - innerLeft).coerceAtLeast(1f)
    var nameLayout = HudDrawCache.layout(HudText.TRIAL_NAME, measurer, nameText, measurer.typography.condStyle(layout.nameSize),
        uppercase = true)
    if (nameLayout.size.width > nameWidth) {
        // Long (Russian) names shrink to fit before they would be cut.
        val fitted = floor(layout.nameSize * nameWidth / nameLayout.size.width * 2f) / 2f
        nameLayout = HudDrawCache.layout(HudText.TRIAL_NAME_FIT, measurer, nameText,
            measurer.typography.condStyle(fitted.coerceAtLeast(layout.nameSize * 0.6f)), uppercase = true, maxWidth = nameWidth)
    }
    drawKkText(nameLayout, innerLeft, layout.rowNameCenter, Kk.Bone, valign = KkVAlign.CENTER)
    val info = HudDrawCache.rect(HudRect.TRIAL_INFO, layout.infoLeft, layout.infoTop, layout.infoLeft + layout.infoSize,
        layout.infoTop + layout.infoSize)
    drawKkInfoButton(measurer, info, active = infoOpen)
    HudLayoutProbe.record(HudBlock.TRIAL_PANEL, panel.left, panel.top, panel.right, panel.bottom)

    // Row 3: segmented progress.
    val segments = trialSegments(engine, point)
    val meter = HudDrawCache.rect(HudRect.TRIAL_METER, innerLeft, layout.meterTop, innerRight, layout.meterTop + layout.meterHeight)
    drawKkMeter(meter, point.progress, roles.you, roles, segments = segments)

    // Row 4: progress value, reward. The orbit's seconds change every frame while orbiting, so they
    // are drawn from cached digit layouts; counted goals change rarely and use one cached line.
    val progressWidth = if (point.kind == PointOfInterestKind.COLLAPSING_ORBIT) {
        drawOrbitProgress(engine, point, measurer, innerLeft, layout.rowValueCenter, layout.monoSize)
    } else {
        val progressLayout = HudDrawCache.layout(HudText.TRIAL_PROGRESS, measurer, trialProgressText(engine, point, language),
            measurer.typography.monoStyle(layout.monoSize), uppercase = true)
        drawKkText(progressLayout, innerLeft, layout.rowValueCenter, Kk.Bone, valign = KkVAlign.CENTER)
        progressLayout.size.width.toFloat()
    }
    val rewardText = TrialScratch.reward.of(language, point.kind.ordinal.toLong()) {
        language.text(rewardText(engine.content.pointsOfInterest.definition(point.kind).reward))
    }
    val rewardLayout = HudDrawCache.layout(HudText.TRIAL_REWARD, measurer, rewardText, measurer.typography.monoStyle(layout.monoSize),
        uppercase = true, maxWidth = (innerRight - innerLeft - progressWidth - 12f * unit).coerceAtLeast(1f))
    drawKkText(rewardLayout, innerRight, layout.rowValueCenter, roles.you, KkAlign.END, KkVAlign.CENTER)
}

/**
 * The open (!) of the trial panel: the rules on a bone slip below the whole panel, so the panel's
 * name, progress and reward stay readable. Drawn after the feed so nothing covers it.
 */
internal fun DrawScope.drawTrialTooltip(engine: GameplayRenderModel, measurer: TextMeasurer, open: Boolean) {
    if (!open) return
    val point = engine.activeTrial() ?: return
    val language = measurer.language
    val layout = TrialScratch.layout.update(size.width, size.height, density, measurer.scale)
    val panel = HudDrawCache.rect(HudRect.TRIAL_ANCHOR, layout.left, layout.top, layout.right, layout.bottom)
    val rules = TrialScratch.rules.of(language, point.kind.ordinal.toLong()) { engine.trialRules(point, language) }
    // The foundation tooltip's geometry (`.tipbox`), placed below the panel (above it when there is
    // no room) with cached rects so an open tooltip costs nothing per frame.
    val widthDp = min(270f, min(panel.width, size.width - 16f * density) / density)
    val body = measureKkTooltip(measurer, rules, density, widthDp)
    val width = widthDp * density
    val height = body.kkBoxHeight + 22f * density
    val gap = 12f * density
    val margin = 8f * density
    val left = (panel.center.x - width * 0.5f).coerceIn(margin, max(margin, size.width - margin - width))
    val top = if (panel.bottom + gap + height <= size.height - margin) panel.bottom + gap else max(margin, panel.top - gap - height)
    val slip = HudDrawCache.rect(HudRect.TRIAL_TOOLTIP, left, top, left + width, top + height)
    drawKkSlip(slip, cutDp = 10f)
    drawKkText(body, slip.left + 13f * density, slip.top + 11f * density, Kk.Ink)
    HudLayoutProbe.record(HudBlock.TRIAL_TOOLTIP, slip.left, slip.top, slip.right, slip.bottom)
}

/** Draws "5.2 / 8.0 s" at ([x], [centerY]) from cached digits; returns its width. */
private fun DrawScope.drawOrbitProgress(
    engine: GameplayRenderModel,
    point: PointOfInterestProjection,
    measurer: TextMeasurer,
    x: Float,
    centerY: Float,
    monoSize: Float,
): Float {
    val language = measurer.language
    val required = engine.content.pointsOfInterest.orbitRequiredSeconds
    val requiredTenths = (required * 10f).roundToInt()
    val tenths = (point.progress.coerceIn(0f, 1f) * required * 10f).roundToInt().toLong()
    val style = measurer.typography.monoStyle(monoSize)
    val separator = if (language == AppLanguage.Russian) "," else "."
    val tail = TrialScratch.orbitTail.of(language, requiredTenths.toLong()) {
        language.text(HudRedesignText.TrialSeconds, "", tenthsText(requiredTenths, language)).uppercase()
    }
    var width = drawKkTabularNumber(measurer, tenths / 10L, style, x, centerY, Kk.Bone, valign = KkVAlign.CENTER, suffix = separator)
    width += drawKkTabularNumber(measurer, tenths % 10L, style, x + width, centerY, Kk.Bone, valign = KkVAlign.CENTER, suffix = tail)
    return width
}

/** Progress cells: one per orbit second, per defender or per beacon of the circuit. */
internal fun trialSegments(engine: GameplayRenderModel, point: PointOfInterestProjection): Int = when (point.kind) {
    PointOfInterestKind.COLLAPSING_ORBIT -> ceil(engine.content.pointsOfInterest.orbitRequiredSeconds).toInt().coerceIn(1, 24)
    PointOfInterestKind.SEALED_ANOMALY -> point.defenderIds.size.coerceIn(1, 12)
    PointOfInterestKind.RESONANT_CIRCUIT -> RESONANT_CIRCUIT_BEACONS
}

/** Beacons in a resonant circuit (the projection reports progress as visited beacons over three). */
private const val RESONANT_CIRCUIT_BEACONS = 3

/** The trial's progress as values: "5.2 / 8.0 s" for the orbit, "2 / 3" for counted goals. */
internal fun trialProgressText(engine: GameplayRenderModel, point: PointOfInterestProjection, language: AppLanguage): String {
    val progress = point.progress.coerceIn(0f, 1f)
    return when (point.kind) {
        PointOfInterestKind.COLLAPSING_ORBIT -> {
            val required = engine.content.pointsOfInterest.orbitRequiredSeconds
            val tenths = (progress * required * 10f).roundToInt()
            TrialScratch.progress.of(language, tenths.toLong() * 1_000L + (required * 10f).roundToInt()) {
                language.text(HudRedesignText.TrialSeconds, tenthsText(tenths, language), tenthsText((required * 10f).roundToInt(), language))
            }
        }
        else -> {
            val total = trialSegments(engine, point)
            val done = (progress * total).roundToInt()
            TrialScratch.progress.of(language, -(done.toLong() * 1_000L + total) - 1L) { "$done / $total" }
        }
    }
}

private fun tenthsText(tenths: Int, language: AppLanguage): String {
    val separator = if (language == AppLanguage.Russian) ',' else '.'
    return "${tenths / 10}$separator${abs(tenths % 10)}"
}

private fun rewardText(reward: DirectedReward): HudRedesignText = when (reward) {
    DirectedReward.WEAPON -> HudRedesignText.RewardWeapon
    DirectedReward.RELIC -> HudRedesignText.RewardRelic
    DirectedReward.ITEM_AND_REPAIR -> HudRedesignText.RewardItemAndRepair
}
