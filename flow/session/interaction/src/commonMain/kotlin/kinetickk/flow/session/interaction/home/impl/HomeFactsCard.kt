// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextLayoutResult
import kinetickk.ball.content.api.localizedContent
import kinetickk.flow.session.interaction.home.api.HomeUiModel
import kinetickk.flow.session.interaction.localization.SessionRedesignText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkAlign
import kinetickk.foundation.design.KkVAlign
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.drawKkInfoButton
import kinetickk.foundation.design.drawKkSheared
import kinetickk.foundation.design.drawKkText
import kinetickk.foundation.design.kkBoxHeight
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.labelStyle
import kinetickk.foundation.design.measureKkText
import kinetickk.foundation.design.monoStyle
import kinetickk.foundation.design.wideStyle
import kotlin.math.max
import kotlin.math.min

/** Measured facts card: the label and big value on the left, then one row per fact. */
internal class HomeFactsCardLayout(
    val label: TextLayoutResult,
    val big: TextLayoutResult,
    val left: Float,
    val columnLeft: Float,
    val columnRight: Float,
    val rows: List<Pair<TextLayoutResult, TextLayoutResult>>,
)

/**
 * Lays out the facts card inside [rect] (px, [unit] = px per board px) left of [infoLeft]. All rows
 * share one text size that shrinks (to 70 %) until the widest key/value pair fits the column, so
 * the key and value are shown whole; only content that cannot fit even then is truncated.
 */
internal fun homeFactsCardLayout(
    text: CanvasTextMeasurer,
    rect: Rect,
    facts: HomeFacts,
    unit: Float,
    infoLeft: Float,
    density: Float,
): HomeFactsCardLayout {
    fun display(px: Float) = max(1f, px / density / text.scale)
    val label = measureKkText(text, facts.label, text.typography.labelStyle(15f), uppercase = true)
    val bigSize = if (facts.big.length > 6) 24f else 40f
    val maxLeft = min(240f * unit, (infoLeft - rect.left) * 0.5f)
    val big = measureKkText(text, facts.big, text.typography.wideStyle(display(bigSize * unit), tabular = true), uppercase = true, maxWidth = maxLeft)
    val leftWidth = max(110f * unit, max(label.size.width.toFloat(), big.size.width.toFloat()))
    val left = rect.left + 22f * unit
    val columnLeft = left + leftWidth + 26f * unit
    val columnRight = infoLeft - 22f * unit
    val column = columnRight - columnLeft
    val rows = if (column <= 0f || facts.facts.isEmpty()) emptyList() else {
        val gap = 12f * density
        val widest = facts.facts.maxOf { (key, value) ->
            measureKkText(text, key, text.typography.monoStyle(11f), uppercase = true).size.width +
                measureKkText(text, value, text.typography.condStyle(21f, tabular = true), uppercase = true).size.width + gap
        }
        // A small margin keeps rounding of the scaled glyph advances from truncating a pair.
        val fit = (column * 0.97f / widest).coerceIn(0.7f, 1f)
        val keyStyle = text.typography.monoStyle(11f * fit)
        val valueStyle = text.typography.condStyle(21f * fit, tabular = true)
        facts.facts.map { (key, value) ->
            val keyWidth = measureKkText(text, key, keyStyle, uppercase = true).size.width.toFloat()
            val valueWidth = measureKkText(text, value, valueStyle, uppercase = true).size.width.toFloat()
            val keyMax = if (keyWidth + valueWidth + gap <= column + 1f) keyWidth else min(keyWidth, column * 0.45f)
            val keyLayout = measureKkText(text, key, keyStyle, uppercase = true, maxWidth = keyMax + 2f)
            val valueLayout = measureKkText(text, value, valueStyle, uppercase = true, maxWidth = column - keyLayout.size.width - gap + 2f)
            keyLayout to valueLayout
        }
    }
    return HomeFactsCardLayout(label, big, left, columnLeft, columnRight, rows)
}

internal fun DrawScope.drawFactsCard(
    text: CanvasTextMeasurer,
    rect: Rect,
    facts: HomeFacts,
    layout: HomeLayoutGeometry,
    state: HomeDrawState,
    motion: HomeMenuMotion,
    accent: Color,
) {
    val u = layout.scene.unit
    drawPath(motion.factsPath(rect, 16f * u), Kk.Ink2.copy(alpha = 0.92f))
    val info = layout.info(HomeInfoTarget.FACTS)
    val infoLeft = info?.bounds?.left ?: (rect.right - 44f * u)
    val card = homeFactsCardLayout(text, rect, facts, u, infoLeft, density)
    val blockHeight = card.label.kkBoxHeight + 8f * u + card.big.kkBoxHeight
    var y = rect.center.y - blockHeight * 0.5f
    drawKkText(card.label, card.left, y, accent)
    y += card.label.kkBoxHeight + 8f * u
    drawKkText(card.big, card.left, y, Kk.Bone)
    if (card.rows.isNotEmpty()) {
        val rowHeight = card.rows.maxOf { (key, value) -> max(key.kkBoxHeight, value.kkBoxHeight) }
        val rowGap = 6f * density
        var rowY = rect.center.y - (rowHeight * card.rows.size + rowGap * (card.rows.size - 1)) * 0.5f
        card.rows.forEach { (key, value) ->
            val center = rowY + rowHeight * 0.5f
            drawKkText(key, card.columnLeft, center, Kk.Mute, valign = KkVAlign.CENTER)
            drawKkText(value, card.columnRight, center, Kk.Bone, align = KkAlign.END, valign = KkVAlign.CENTER)
            rowY += rowHeight + rowGap
        }
    }
    if (info != null) {
        drawKkInfoButton(text, info.bounds, active = state.activeInfo == HomeInfoTarget.FACTS || state.openInfo == HomeInfoTarget.FACTS)
    }
}

/** The current character: its name and its (!) (the mechanic); the Workshop changes it. */
internal fun DrawScope.drawFormPanel(
    model: HomeUiModel,
    text: CanvasTextMeasurer,
    layout: HomeLayoutGeometry,
    state: HomeDrawState,
    motion: HomeMenuMotion,
) {
    val scene = layout.scene
    val language = text.language
    val definition = model.coreShape(model.coreShape)
    val info = layout.info(HomeInfoTarget.FORM)
    motion.formPanel(layout, density)
    drawHomeFormName(text, scene, definition.displayName.localizedContent(language), motion.formNameRight, Kk.Bone)
    if (info != null) {
        drawKkInfoButton(text, info.bounds, active = state.activeInfo == HomeInfoTarget.FORM || state.openInfo == HomeInfoTarget.FORM)
    }
}

/** The Quit button: a small sheared outline plate, filled with the threat role while hovered or focused. */
internal fun DrawScope.drawQuit(text: CanvasTextMeasurer, bounds: Rect, scene: HomeScene, active: Boolean) {
    val threat = text.roles.threat
    if (active) {
        drawKkSheared(bounds, threat)
    } else {
        val inset = 0.75f * density
        drawKkSheared(Rect(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset), Kk.Mute,
            kkStroke(1.5f * density))
    }
    val label = measureKkText(text, text.language.text(SessionRedesignText.QUIT),
        text.typography.condStyle(displaySize(text, 21f * scene.unit), trackingEm = 0.04f, lineHeightEm = 1f), uppercase = true,
        maxWidth = bounds.width - 16f * scene.unit)
    drawKkText(label, bounds.center.x, bounds.center.y, if (active) Kk.Ink else Kk.Bone, align = KkAlign.CENTER, valign = KkVAlign.CENTER)
}

internal fun DrawScope.drawVersion(text: CanvasTextMeasurer, layout: HomeLayoutGeometry) {
    val scene = layout.scene
    drawKkText(homeVersionLayout(text, scene, density), scene.versionRight, scene.versionBaseline, Kk.Mute,
        align = KkAlign.END, valign = KkVAlign.BASELINE)
}

/** The version line on Home: the build version in mono, kept at its size under every text-size setting. */
internal fun homeVersionLayout(text: CanvasTextMeasurer, scene: HomeScene, density: Float): TextLayoutResult =
    measureKkText(text, text.language.text(SessionRedesignText.VERSION),
        text.typography.monoStyle(max(1f, scene.versionSize / density / text.scale)), uppercase = true)
