// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.layout

import androidx.compose.ui.geometry.Rect
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.TrialProjection
import kotlin.math.max

/** The engagement the trial panel shows, if any (the game runs at most one at a time). */
internal fun GameplayRenderModel.activeTrial(): TrialProjection? = trial

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
