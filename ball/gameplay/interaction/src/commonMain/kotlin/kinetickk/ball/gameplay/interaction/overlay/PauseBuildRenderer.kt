// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.overlay

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import kinetickk.ball.gameplay.interaction.canvas.*
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.ball.gameplay.interaction.layout.GameplayLayoutMode
import kinetickk.ball.gameplay.interaction.layout.PauseLayoutGeometry
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.*

internal fun DrawScope.drawPauseBuild(
    engine: GameplayRenderModel,
    measurer: CanvasTextMeasurer,
    layout: PauseLayoutGeometry,
    time: Float,
    touch: Boolean = false,
) {
    val overview = PauseBuildMemo.of(engine, measurer.language, touch)
    val ui = PauseMeasurers.ui(measurer, engine.settings.textScale)
    val display = PauseMeasurers.display(measurer, engine.settings.textScale)
    when (layout.mode) {
        GameplayLayoutMode.REGULAR -> drawRegularBuild(overview, ui, display, layout.build, layout.unit, time)
        GameplayLayoutMode.COMPACT_LANDSCAPE -> drawCompactBuild(overview, ui, display, layout.build, layout.unit, time, portrait = false)
        GameplayLayoutMode.COMPACT_PORTRAIT -> drawCompactBuild(overview, ui, display, layout.build, layout.unit, time, portrait = true)
    }
}

/**
 * Pause measurers derived from the host's: UI text follows the text-size setting relative to the
 * default (board size at 125 %); display type (title, frozen clock, menu items, level numerals)
 * ignores it. Rebuilt only when the host measurer or the setting changes.
 */
internal object PauseMeasurers {
    private var base: CanvasTextMeasurer? = null
    private var setting = Float.NaN
    private var ui: CanvasTextMeasurer? = null
    private var display: CanvasTextMeasurer? = null

    fun ui(base: CanvasTextMeasurer, setting: Float): CanvasTextMeasurer = update(base, setting).let { requireNotNull(ui) }

    fun display(base: CanvasTextMeasurer, setting: Float): CanvasTextMeasurer = update(base, setting).let { requireNotNull(display) }

    private fun update(base: CanvasTextMeasurer, setting: Float) {
        if (this.base === base && this.setting == setting) return
        this.base = base
        this.setting = setting
        ui = CanvasTextMeasurer(base.delegate, overlayUiTextScale(setting), base.language, base.typography, base.roles)
        display = CanvasTextMeasurer(base.delegate, 1f, base.language, base.typography, base.roles)
    }
}

/** What a pause text is, for the layout probe. */
internal enum class PauseTextKind {
    TITLE, TIMER, HEADING, TAG, LABEL, SYNERGY_NAME, MULTIPLIER, COST, TAG_NAME, ELEMENT_NAME, REACTION, EMPTY,
    STAT_LABEL, STAT_VALUE, RUN_LABEL, RUN_VALUE, MORE, SYNERGY, KEY,
    /** The run Conditions: an entry's caption, its name, what it does or costs. */
    CONDITION_LABEL, CONDITION, CONDITION_TEXT,
}

/** One laid-out pause text: where it was drawn ([rect], px) and the width it had to fit ([box]). */
internal class PauseTextRecord(val kind: PauseTextKind, val layout: TextLayoutResult, val rect: Rect, val box: Float)

/** Test probe: while [records] is set, every pause text is recorded as it is drawn. */
internal object PauseTextProbe {
    var records: MutableList<PauseTextRecord>? = null
}

/** Draws a laid-out pause text and reports it to the probe with the width it had to fit. */
internal fun DrawScope.drawPauseText(
    kind: PauseTextKind,
    layout: TextLayoutResult,
    x: Float,
    y: Float,
    color: Color,
    box: Float,
    align: KkAlign = KkAlign.START,
    valign: KkVAlign = KkVAlign.TOP,
) {
    drawKkText(layout, x, y, color, align, valign)
    val records = PauseTextProbe.records ?: return
    val left = when (align) {
        KkAlign.START -> x
        KkAlign.CENTER -> x - layout.size.width * 0.5f
        KkAlign.END -> x - layout.size.width
    }
    val top = when (valign) {
        KkVAlign.TOP -> y - layout.kkBoxTop
        KkVAlign.CENTER -> y - (layout.kkBoxTop + layout.kkBoxBottom) * 0.5f
        KkVAlign.BASELINE -> y - layout.firstBaseline
    }
    records += PauseTextRecord(kind, layout, Rect(left, top, left + layout.size.width, top + layout.size.height), box)
}

internal fun runColor(tone: PauseValueTone, roles: KkRolePalette): Color = when (tone) {
    PauseValueTone.BONE -> Kk.Bone
    PauseValueTone.YOU -> roles.you
    PauseValueTone.THREAT -> roles.threat
}
