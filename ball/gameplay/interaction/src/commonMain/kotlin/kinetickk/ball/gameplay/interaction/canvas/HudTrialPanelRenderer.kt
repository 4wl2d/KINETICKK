// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import kinetickk.ball.content.api.SiteFamily
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_HUD_TOP_DP
import kinetickk.ball.gameplay.interaction.layout.PORTRAIT_PANEL_ROW_DP
import kinetickk.ball.gameplay.interaction.layout.RUNNING_CONTROL_MIN_DP
import kinetickk.ball.gameplay.interaction.layout.compactHudFactor
import kinetickk.ball.gameplay.interaction.layout.RunningControlMask
import kinetickk.ball.gameplay.interaction.layout.forEachRunningControlBounds
import kinetickk.ball.gameplay.interaction.layout.gameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.regularHudUnit
import kinetickk.ball.gameplay.interaction.layout.runningHudMargin
import kinetickk.ball.gameplay.interaction.localization.HudRedesignText
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.TrialClock
import kinetickk.ball.gameplay.nucleus.render.TrialProjection
import kinetickk.ball.gameplay.nucleus.render.TrialReward
import kinetickk.ball.gameplay.nucleus.render.TrialSource
import kinetickk.ball.gameplay.nucleus.render.TrialValue
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.TextResource
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The engagement the trial panel shows, if any (the game runs at most one at a time). */
internal fun GameplayRenderModel.activeTrial(): TrialProjection? = trial

/** The trial's rules for its (!) info button: the content instruction, or a dungeon's own rules, in the player's language. */
@Suppress("UnusedReceiverParameter")
internal fun GameplayRenderModel.trialRules(trial: TrialProjection, language: AppLanguage): String =
    if (trial.source == TrialSource.STRUCTURE) structureTrialRules(language) else trial.rules.localizedContent(language)

/**
 * Geometry of the top-left trial panel (`Anomaly` board), shared by the renderer, the focusable
 * semantics node of its (!) info button and the world keep-out. All values are canvas px; [update]
 * rewrites them. Its text is UI text: rows grow with the text size relative to the board
 * ([hudUiScale]); above the default size the reward takes a row of its own so it is never cut.
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

    /** Whether the reward sits on its own row under the progress value (text above the default size). */
    var rewardRow = false
        private set
    var rowRewardCenter = 0f
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
        val text = hudUiScale(textScale)
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
        // Below the level badge and its Data bar (their numeral is display type, a fixed size).
        top = when (mode) {
            GameplayLayoutMode.REGULAR -> 118f * unit
            GameplayLayoutMode.COMPACT_LANDSCAPE -> 48f * unit
            // Below the chip row and the reserved boss row, so an elite never covers the panel.
            GameplayLayoutMode.COMPACT_PORTRAIT -> (PORTRAIT_HUD_TOP_DP + PORTRAIT_PANEL_ROW_DP) * unit
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
        rewardRow = text > 1f
        rowRewardCenter = if (rewardRow) rowValueCenter + valueRow + 4f * unit else rowValueCenter
        bottom = rowRewardCenter + valueRow * 0.5f + (if (compact) 12f else 18f) * unit
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
    val progress = HudKeyedText()
    val reward = HudKeyedText()
    val rules = HudKeyedText()
    val secondsTail = HudKeyedText()
    val raceTail = HudKeyedText()
}

/**
 * Top-left trial panel: the family's glyph and label with the clock (a countdown, a race's tenths or none), the title
 * with its (!), the segmented progress, the value (count, seconds or none) and the reward (a race's medal with its
 * pips). Every text is cached per input, so a steady panel draws without allocating.
 */
internal fun DrawScope.drawTrialPanel(
    engine: GameplayRenderModel,
    measurer: TextMeasurer,
    renderTime: Float,
    infoOpen: Boolean,
) {
    val trial = engine.activeTrial() ?: return
    val roles = measurer.roles
    val language = measurer.language
    val layout = TrialScratch.layout.update(size.width, size.height, density, measurer.scale)
    // Every line is UI text (board sizes through the text-size measurer), fitted, never cut.
    val text = HudMeasurers.ui(measurer)
    val unit = layout.unit
    val panel = HudDrawCache.rect(HudRect.TRIAL_ANCHOR, layout.left, layout.top, layout.right, layout.bottom)
    val cut = (if (layout.compact) 10f else 14f) * unit
    drawPath(HudDrawCache.paths.chamfer(HudPath.TRIAL_PANEL.ordinal, panel, cut), Kk.Ink2.copy(alpha = 0.92f))
    drawRect(roles.you, Offset(panel.left, panel.top), Size(panel.width - cut, 3f * unit))

    val innerLeft = panel.left + layout.padding
    val innerRight = panel.right - layout.padding
    // Row 1: family glyph + label, clock.
    val iconSize = (if (layout.compact) 13f else 16f) * unit
    drawSiteGlyph(trialGlyph(trial), Offset(innerLeft + iconSize * 0.5f, layout.rowLabelCenter), iconSize, roles.you)
    val label = trialLabel(trial)
    val labelText = TrialScratch.label.of(label, language.ordinal.toLong()) { language.text(label) }
    val clockWidth = when (trial.clock) {
        TrialClock.COUNTDOWN -> {
            // The remaining time as m:ss, the same string as the ring and point timers in the world.
            val clockLayout = HudDrawCache.layout(HudText.TRIAL_CLOCK, text, trialClockText(trial),
                measurer.typography.monoStyle(layout.monoSize))
            drawKkText(clockLayout, innerRight, layout.rowLabelCenter, Kk.Bone, KkAlign.END, KkVAlign.CENTER)
            recordLine(HudBlock.TRIAL_CLOCK, innerRight - clockLayout.size.width, layout.rowLabelCenter, clockLayout)
            clockLayout.size.width.toFloat()
        }
        TrialClock.ELAPSED_TENTHS -> drawRaceClock(trial, text, innerRight, layout.rowLabelCenter, layout.monoSize)
        TrialClock.NONE -> 0f
    }
    val labelLeft = innerLeft + iconSize + 8f * unit
    val labelRoom = innerRight - (if (clockWidth > 0f) clockWidth + 8f * unit else 0f) - labelLeft
    val labelLayout = HudDrawCache.fitted(HudText.TRIAL_LABEL, text, labelText, layout.labelSize, labelRoom, uppercase = true) { size ->
        measurer.typography.labelStyle(size)
    }
    drawKkText(labelLayout, labelLeft, layout.rowLabelCenter, roles.you, valign = KkVAlign.CENTER)
    recordLine(HudBlock.TRIAL_LABEL, labelLeft, layout.rowLabelCenter, labelLayout)

    // Row 2: trial name + (!) info. Long (Russian) names shrink to fit.
    val nameText = TrialScratch.name.of(trial.title, language.ordinal.toLong()) { trial.title.localizedContent(language) }
    val nameLayout = HudDrawCache.fitted(HudText.TRIAL_NAME, text, nameText, layout.nameSize, layout.infoLeft - 10f * unit - innerLeft,
        uppercase = true, minSize = layout.nameSize * 0.4f) { size -> measurer.typography.condStyle(size) }
    drawKkText(nameLayout, innerLeft, layout.rowNameCenter, Kk.Bone, valign = KkVAlign.CENTER)
    recordLine(HudBlock.TRIAL_NAME, innerLeft, layout.rowNameCenter, nameLayout)
    val info = HudDrawCache.rect(HudRect.TRIAL_INFO, layout.infoLeft, layout.infoTop, layout.infoLeft + layout.infoSize,
        layout.infoTop + layout.infoSize)
    drawKkInfoButton(text, info, active = infoOpen)
    HudLayoutProbe.record(HudBlock.TRIAL_PANEL, panel.left, panel.top, panel.right, panel.bottom)

    // Row 3: segmented progress.
    val meter = HudDrawCache.rect(HudRect.TRIAL_METER, innerLeft, layout.meterTop, innerRight, layout.meterTop + layout.meterHeight)
    drawKkMeter(meter, trial.progress, roles.you, roles, segments = trial.segments)
    if (trial.source == TrialSource.STRUCTURE) drawSealedRoomSegment(engine, trial, meter, roles)

    // Row 4: progress value, reward. Seconds change every frame while earned, so they are drawn from cached digit
    // layouts; counted goals change rarely and use one cached line; a duel shows no value.
    val progressWidth = when (trial.value) {
        TrialValue.SECONDS -> drawSecondsProgress(trial, text, innerLeft, layout.rowValueCenter, layout.monoSize)
        TrialValue.COUNT -> {
            val progressLayout = HudDrawCache.layout(HudText.TRIAL_PROGRESS, text, trialProgressText(trial, language),
                measurer.typography.monoStyle(layout.monoSize), uppercase = true)
            drawKkText(progressLayout, innerLeft, layout.rowValueCenter, Kk.Bone, valign = KkVAlign.CENTER)
            progressLayout.size.width.toFloat()
        }
        TrialValue.NONE -> 0f
    }
    if (trial.value != TrialValue.NONE) {
        val valueHalf = layout.monoSize * 1.35f * text.scale * density * 0.5f
        HudLayoutProbe.record(HudBlock.TRIAL_PROGRESS, innerLeft, layout.rowValueCenter - valueHalf, innerLeft + progressWidth,
            layout.rowValueCenter + valueHalf)
    }
    val reward = trialRewardText(trial)
    val rewardText = if (trial.reward == TrialReward.STRUCTURE_REWARDS) structureTrialRewards(engine, trial, language)
        else TrialScratch.reward.of(language, reward.ordinal.toLong()) { language.text(reward) }
    // A race's medal pips sit left of its medal label.
    val pipSize = (if (layout.compact) 6f else 7f) * unit
    val pipGap = 3f * unit
    val pipsWidth = if (trial.reward == TrialReward.MEDAL) MEDAL_PIPS * pipSize + (MEDAL_PIPS - 1) * pipGap + 8f * unit else 0f
    // Beside the value at the board's sizes; on its own row when the text is larger.
    val rewardRoom = (if (layout.rewardRow || progressWidth <= 0f) innerRight - innerLeft else innerRight - innerLeft - progressWidth - 12f * unit) -
        pipsWidth
    val rewardLayout = HudDrawCache.fitted(HudText.TRIAL_REWARD, text, rewardText, layout.monoSize, rewardRoom, uppercase = true) { size ->
        measurer.typography.monoStyle(size)
    }
    drawKkText(rewardLayout, innerRight, layout.rowRewardCenter, roles.you, KkAlign.END, KkVAlign.CENTER)
    recordLine(HudBlock.TRIAL_REWARD, innerRight - rewardLayout.size.width, layout.rowRewardCenter, rewardLayout)
    if (pipsWidth > 0f) {
        val pipsLeft = innerRight - rewardLayout.size.width - pipsWidth
        drawKkPips(Offset(pipsLeft, layout.rowRewardCenter - pipSize * 0.5f), MEDAL_PIPS, trial.medal.coerceIn(0, MEDAL_PIPS), roles.you,
            sizeDp = pipSize / density, gapDp = pipGap / density, emptyOutlined = true, emptyColor = roles.you)
    }
}

/** A race's medal pips: gold, silver and bronze as three, two and one filled. */
private const val MEDAL_PIPS = 3

/** The panel's first-row glyph: a dungeon or a dimension objective by its source, a site by its family. */
internal fun trialGlyph(trial: TrialProjection): SiteGlyph = when (trial.source) {
    TrialSource.SITE -> siteFamilyGlyph(trial.family)
    TrialSource.STRUCTURE -> SiteGlyph.STRUCTURE
    TrialSource.DIMENSION -> SiteGlyph.DIMENSION
}

/** The panel's family label; a dungeon names itself by its source. */
internal fun trialLabel(trial: TrialProjection): TextResource = if (trial.source == TrialSource.STRUCTURE) structureTrialLabel() else when (trial.family) {
    SiteFamily.ANOMALY -> HudRedesignText.AnomalyTrial
    SiteFamily.RACE -> HudRedesignText.RaceTrial
    SiteFamily.CHALLENGE -> HudRedesignText.ChallengeTrial
    SiteFamily.BOUNTY -> HudRedesignText.BountyTrial
    SiteFamily.SECRET -> HudRedesignText.SigilTrial
    // No site of these families runs a trial; a dungeon or a dimension objective names itself by its source.
    SiteFamily.CACHE, SiteFamily.PORTAL, SiteFamily.SIGNAL -> HudRedesignText.AnomalyTrial
}

/** The reward label; a race names the medal its clock still allows. */
internal fun trialRewardText(trial: TrialProjection): HudRedesignText = when (trial.reward) {
    TrialReward.WEAPON -> HudRedesignText.RewardWeapon
    TrialReward.RELIC -> HudRedesignText.RewardRelic
    TrialReward.ITEM_AND_REPAIR -> HudRedesignText.RewardItemAndRepair
    TrialReward.ITEM -> HudRedesignText.RewardItem
    TrialReward.KEY_AND_RELIC -> HudRedesignText.RewardKeyAndRelic
    TrialReward.MATTER -> HudRedesignText.RewardMatter
    TrialReward.MEDAL -> when (trial.medal) {
        3 -> HudRedesignText.RewardGold
        2 -> HudRedesignText.RewardSilver
        1 -> HudRedesignText.RewardBronze
        else -> HudRedesignText.RewardNoMedal
    }
    // The dungeon and dimension rewards are labelled by their lanes' sources.
    TrialReward.UNKNOWN, TrialReward.STRUCTURE_REWARDS, TrialReward.BLESSING, TrialReward.PACT, TrialReward.TROVE ->
        HudRedesignText.RewardUnknown
}

/** The trial's remaining time as `m:ss` ("0:15"), shared with the world's ring and point timers. */
internal fun trialClockText(trial: TrialProjection): String = WorldStrings.timer(trial.seconds)

/** Records a vertically centered one-line [layout] starting at [left] (layout tests). */
private fun recordLine(block: HudBlock, left: Float, centerY: Float, layout: androidx.compose.ui.text.TextLayoutResult) {
    val half = layout.kkBoxHeight * 0.5f
    HudLayoutProbe.record(block, left, centerY - half, left + layout.size.width, centerY + half)
}

/**
 * The open (!) of the trial panel: the rules on a bone slip below the whole panel, so the panel's
 * name, progress and reward stay readable. Drawn after the feed so nothing covers it. Placed by
 * [TrialRulesPlacement]: above the bottom clusters and, on phones, clear of the Core.
 */
internal fun DrawScope.drawTrialTooltip(engine: GameplayRenderModel, measurer: TextMeasurer, open: Boolean) {
    if (!open) return
    val trial = engine.activeTrial() ?: return
    val language = measurer.language
    val layout = TrialScratch.layout.update(size.width, size.height, density, measurer.scale)
    val panel = HudDrawCache.rect(HudRect.TRIAL_ANCHOR, layout.left, layout.top, layout.right, layout.bottom)
    val rules = TrialScratch.rules.of(trial.rules, language.ordinal.toLong()) { engine.trialRules(trial, language) }
    val place = TrialRulesPlacement.update(measurer, rules, panel, size.width, size.height, density)
    val slip = HudDrawCache.rect(HudRect.TRIAL_TOOLTIP, place.left, place.top, place.right, place.bottom)
    drawKkSlip(slip, cutDp = 10f)
    drawKkText(place.body, slip.left + 13f * density, slip.top + 11f * density, Kk.Ink)
    HudLayoutProbe.record(HudBlock.TRIAL_TOOLTIP, slip.left, slip.top, slip.right, slip.bottom)
}

/**
 * Where the open trial rules go (px) and their body, the foundation tooltip's geometry (`.tipbox`).
 * The slip must end above the bottom clusters and, on phones, keep the Core's zone clear like the
 * feed's plates ([CORE_CLEARANCE_DP] around the screen center, where the camera holds the Core).
 * It hangs under the panel at the panel's width. Large text that does not fit there takes a wider
 * slip (up to [TOOLTIP_WIDE_DP]; the panel's full width on portrait phones) or steps down toward
 * the smallest text setting; on portrait phones each step also tries the column left of the Core's
 * zone (away from the feed's toasts on the right), and the band below the zone comes last. On
 * landscape phones the slip stays in the panel's column (stepping its text down) and widens toward
 * the Core's zone only when no step fits there. Where no place clears the bottom clusters (short
 * phones at large text), the slip may cover them but still not the Core's zone or the touch
 * controls. Memoized per input, so an open slip measures nothing per frame; draw-thread scratch.
 */
internal object TrialRulesPlacement {
    var left = 0f
        private set
    var top = 0f
        private set
    var right = 0f
        private set
    var bottom = 0f
        private set
    lateinit var body: TextLayoutResult
        private set

    private var keyMeasurer: TextMeasurer? = null
    private var keyText: String? = null
    private val keys = FloatArray(7) { Float.NaN }

    fun update(measurer: TextMeasurer, text: String, panel: Rect, width: Float, height: Float, density: Float): TrialRulesPlacement {
        if (keyMeasurer === measurer && keyText == text && keys[0] == width && keys[1] == height && keys[2] == density &&
            keys[3] == panel.left && keys[4] == panel.top && keys[5] == panel.right && keys[6] == panel.bottom
        ) return this
        place(measurer, text, panel, width, height, density)
        keyMeasurer = measurer
        keyText = text
        keys[0] = width
        keys[1] = height
        keys[2] = density
        keys[3] = panel.left
        keys[4] = panel.top
        keys[5] = panel.right
        keys[6] = panel.bottom
        return this
    }

    /** Runs only when an input changes (it may allocate). */
    private fun place(measurer: TextMeasurer, text: String, panel: Rect, width: Float, height: Float, density: Float) {
        val frame = HudScratch.frame.update(width, height, density)
        val gap = 12f * density
        val margin = 8f * density
        val floor = frame.bottomClustersTop - gap
        val phone = !frame.regular
        val clearance = frame.u(CORE_CLEARANCE_DP)
        val core = Rect(width * 0.5f - clearance, height * 0.5f - clearance, width * 0.5f + clearance, height * 0.5f + clearance)
        val below = panel.bottom + gap
        val belowCore = max(below, core.bottom + gap)
        // The panel's width hangs centered under it; a wider slip (or the column) starts at its left edge.
        val narrow = min(270f * density, min(panel.width, width - 16f * density))
        val wideRight = when (frame.mode) {
            GameplayLayoutMode.REGULAR -> width - margin
            GameplayLayoutMode.COMPACT_LANDSCAPE -> min(width - margin, core.left)
            GameplayLayoutMode.COMPACT_PORTRAIT -> panel.right
        }
        val wide = min(TOOLTIP_WIDE_DP * density, wideRight - panel.left)
        val widens = wide > narrow + 0.5f
        val centered = panel.center.x - narrow * 0.5f
        val column = if (frame.portrait) core.left - panel.left else 0f
        // Text steps from the setting down to the smallest one.
        val steps = ArrayList<Float>()
        var scale = measurer.scale
        while (true) {
            steps += scale
            if (scale <= SMALLEST_TEXT_SCALE + 0.001f) break
            scale = max(SMALLEST_TEXT_SCALE, scale - SHRINK_STEP)
        }
        fun attempt(step: Float, slipWidth: Float, slipLeft: Float, slipTop: Float, strict: Boolean, fallback: Boolean = false): Boolean {
            // The rules are UI text: the body follows the text size relative to the board.
            val stepMeasurer = if (step == measurer.scale) HudMeasurers.ui(measurer)
            else CanvasTextMeasurer(measurer.delegate, hudUiScale(step), measurer.language, measurer.typography, measurer.roles)
            val measured = measureKkTooltip(stepMeasurer, text, density, slipWidth / density)
            val x = slipLeft.coerceIn(margin, max(margin, width - margin - slipWidth))
            val slip = Rect(x, slipTop, x + slipWidth, slipTop + measured.kkBoxHeight + 22f * density)
            val fits = measured.fitsWithoutCuts() && !(phone && slip.overlaps(core)) &&
                if (strict) slip.bottom <= floor else slip.bottom <= height - margin && !meetsControl(slip, width, height, density)
            if (fits || fallback) set(slip, measured)
            return fits
        }
        fun search(strict: Boolean): Boolean {
            if (frame.mode == GameplayLayoutMode.COMPACT_LANDSCAPE) {
                // Beside the Core: in the panel's column first, then wider up to the Core's zone.
                for (step in steps) if (attempt(step, narrow, centered, below, strict)) return true
                if (widens) for (step in steps) if (attempt(step, wide, panel.left, below, strict)) return true
                return false
            }
            // Under the panel (on portrait phones above the Core's zone, else in the column left of it).
            val columns = column >= NARROW_COLUMN_MIN_DP * density
            for (step in steps) {
                if (attempt(step, narrow, centered, below, strict)) return true
                if (widens && attempt(step, wide, panel.left, below, strict)) return true
                if (columns && attempt(step, column, panel.left, below, strict)) return true
            }
            if (!frame.portrait) return false
            // Portrait: the band below the Core's zone.
            if (belowCore > below) for (step in steps) {
                if (attempt(step, narrow, centered, belowCore, strict)) return true
                if (widens && attempt(step, wide, panel.left, belowCore, strict)) return true
            }
            return false
        }
        // Short phones with large text: rather than cover the Core, the rules may cover the bottom
        // clusters (never the touch controls). Should even that fail, the widest slip below the panel.
        if (search(strict = true) || phone && search(strict = false)) return
        attempt(steps.last(), if (widens) wide else narrow, if (widens) panel.left else centered, below, strict = true, fallback = true)
    }

    private fun meetsControl(slip: Rect, width: Float, height: Float, density: Float): Boolean {
        var meets = false
        forEachRunningControlBounds(width, height, density, RunningControlMask.ALL) { _, left, top, right, bottom ->
            if (slip.left < right && left < slip.right && slip.top < bottom && top < slip.bottom) meets = true
        }
        return meets
    }

    private fun set(slip: Rect, measured: TextLayoutResult) {
        left = slip.left
        top = slip.top
        right = slip.right
        bottom = slip.bottom
        body = measured
    }
}

/** Widest trial-rules slip, for large text on short screens (dp). */
private const val TOOLTIP_WIDE_DP = 380f

/** Draws "5.2 / 8.0 s" (seconds earned of the required) at ([x], [centerY]) from cached digits; returns its width. */
private fun DrawScope.drawSecondsProgress(
    trial: TrialProjection,
    measurer: TextMeasurer,
    x: Float,
    centerY: Float,
    monoSize: Float,
): Float {
    val language = measurer.language
    val requiredTenths = (trial.required * 10f).roundToInt()
    val tenths = (trial.progress.coerceIn(0f, 1f) * trial.required * 10f).roundToInt().toLong()
    val separator = if (language == AppLanguage.Russian) "," else "."
    // A line height of its own: these Bone digits never share a paragraph with other numbers drawn
    // digit by digit in another color (edge-marker distances), which would reshape them every frame.
    val style = measurer.typography.monoStyle(monoSize, lineHeightEm = TRIAL_DIGIT_LINE_HEIGHT_EM)
    val tail = TrialScratch.secondsTail.of(language, requiredTenths.toLong()) {
        language.text(HudRedesignText.TrialSeconds, "", tenthsText(requiredTenths, language)).uppercase()
    }
    var width = drawKkTabularNumber(measurer, tenths / 10L, style, x, centerY, Kk.Bone, valign = KkVAlign.CENTER, suffix = separator)
    width += drawKkTabularNumber(measurer, tenths % 10L, style, x + width, centerY, Kk.Bone, valign = KkVAlign.CENTER, suffix = tail)
    return width
}

/**
 * Draws a race's clock ("5.2 S", tenths since the start) ending at [right] from cached digits, like the seconds
 * value; returns its width.
 */
private fun DrawScope.drawRaceClock(
    trial: TrialProjection,
    measurer: TextMeasurer,
    right: Float,
    centerY: Float,
    monoSize: Float,
): Float {
    val language = measurer.language
    val tenths = (trial.seconds.coerceAtLeast(0f) * 10f).toLong().coerceAtMost(MAX_RACE_TENTHS)
    val separator = if (language == AppLanguage.Russian) "," else "."
    val style = measurer.typography.monoStyle(monoSize, lineHeightEm = TRIAL_DIGIT_LINE_HEIGHT_EM)
    val tail = TrialScratch.raceTail.of(language, 0L) { language.text(HudRedesignText.RaceSeconds, "").uppercase() }
    val wholeWidth = kkTabularNumberWidth(measurer, tenths / 10L, style, suffix = separator)
    val width = wholeWidth + kkTabularNumberWidth(measurer, tenths % 10L, style, suffix = tail)
    val left = right - width
    drawKkTabularNumber(measurer, tenths / 10L, style, left, centerY, Kk.Bone, valign = KkVAlign.CENTER, suffix = separator)
    drawKkTabularNumber(measurer, tenths % 10L, style, left + wholeWidth, centerY, Kk.Bone, valign = KkVAlign.CENTER, suffix = tail)
    val half = monoSize * TRIAL_DIGIT_LINE_HEIGHT_EM * measurer.scale * density * 0.5f
    HudLayoutProbe.record(HudBlock.TRIAL_CLOCK, left, centerY - half, right, centerY + half)
    return width
}

/** A race clock never shows more than 999.9 s (a race lasts at most the 25 s trial). */
private const val MAX_RACE_TENTHS = 9_999L

/** Line height of the trial's own digits (mono's own 1.35 is left to the other mono numbers). */
private const val TRIAL_DIGIT_LINE_HEIGHT_EM = 1.345f

/** The trial's progress as values: "5.2 / 8.0 s" for earned seconds, "2 / 3" for counted goals, empty for a duel. */
internal fun trialProgressText(trial: TrialProjection, language: AppLanguage): String {
    val progress = trial.progress.coerceIn(0f, 1f)
    return when (trial.value) {
        TrialValue.SECONDS -> {
            val requiredTenths = (trial.required * 10f).roundToInt()
            val tenths = (progress * trial.required * 10f).roundToInt()
            TrialScratch.progress.of(language, tenths.toLong() * 1_000L + requiredTenths) {
                language.text(HudRedesignText.TrialSeconds, tenthsText(tenths, language), tenthsText(requiredTenths, language))
            }
        }
        TrialValue.COUNT -> {
            val done = trial.done.roundToInt()
            val required = trial.required.roundToInt()
            TrialScratch.progress.of(language, -(done.toLong() * 1_000L + required) - 1L) {
                language.text(HudRedesignText.TrialCount, done, required)
            }
        }
        TrialValue.NONE -> ""
    }
}

private fun tenthsText(tenths: Int, language: AppLanguage): String {
    val separator = if (language == AppLanguage.Russian) ',' else '.'
    return "${tenths / 10}$separator${abs(tenths % 10)}"
}
