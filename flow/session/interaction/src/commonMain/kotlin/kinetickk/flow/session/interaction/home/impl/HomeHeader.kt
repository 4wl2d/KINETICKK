// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import kinetickk.flow.session.interaction.home.api.HomeUiModel
import kinetickk.flow.session.interaction.localization.SessionRedesignText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkMutatorSigil
import kinetickk.foundation.design.KkMutatorSigilPaths
import kinetickk.foundation.design.KkPacifierSigilPaths
import kinetickk.foundation.design.KkStampVariant
import kinetickk.foundation.design.KkVAlign
import kinetickk.foundation.design.condStyle
import kinetickk.foundation.design.drawKkGem
import kinetickk.foundation.design.drawKkStamp
import kinetickk.foundation.design.drawKkText
import kinetickk.foundation.design.kkStampSize
import kinetickk.foundation.design.kkStroke
import kinetickk.foundation.design.measureKkText
import kinetickk.foundation.design.monoStyle
import kinetickk.foundation.design.wideStyle

internal fun DrawScope.drawHeader(
    model: HomeUiModel,
    text: CanvasTextMeasurer,
    layout: HomeLayoutGeometry,
    state: HomeDrawState,
    accent: Color,
) {
    val scene = layout.scene
    // Logo lockup (Title board): bone planet cut by an ink slash and an accent slash.
    val mark = scene.logoMark
    val unit = mark / 64f
    val origin = Offset(scene.logo.x, scene.logo.y - mark * 0.5f)
    drawCircle(Kk.Bone, 21f * unit, Offset(origin.x + 32f * unit, origin.y + 32f * unit))
    drawLine(Kk.Ink, Offset(origin.x + 4f * unit, origin.y + 46.6f * unit), Offset(origin.x + 60f * unit, origin.y + 17.4f * unit), 7f * unit)
    drawLine(accent, Offset(origin.x - 2f * unit, origin.y + 49.7f * unit), Offset(origin.x + 66f * unit, origin.y + 14.3f * unit), 3.5f * unit)
    val wordmark = drawKkText(
        text, "KINETICKK", text.typography.wideStyle(displaySize(text, scene.wordmarkSize), lineHeightEm = 1f),
        origin.x + mark + 12f * unit * 64f / 30f, scene.logo.y, Kk.Bone, valign = KkVAlign.CENTER,
    )
    val headerLeft = origin.x + mark + 12f * unit * 64f / 30f + wordmark.size.width + 16f * density
    // Chips from real Home data: Matter and the Rebirth rank (the Codex and the Workshop facts hold the
    // counts). The least important chip (the right end) is dropped when the row would reach the wordmark.
    val language = text.language
    val compact = scene.compactChips
    var count = scene.chipCount
    while (count > 0 && chipsWidth(model, text, scene, count, language) > scene.chipsRight - headerLeft) count--
    var right = scene.chipsRight
    for (index in count - 1 downTo 0) {
        // The chips keep their own meaning whatever item is selected: Matter is yours (volt), Rebirth gold.
        val chipAccent = if (index == 1) Kk.Gold else text.roles.you
        right -= drawChip(text, right, scene, homeChip(model, language, compact, index), chipAccent) + 8f * density
    }
    drawRunModeToggle(text, layout.runModeToggle.bounds, state.easy, state.runModeActive)
}

/**
 * The Baby mode toggle: a chip-shaped plate with the pacifier, filled with the soft role while on (ink pacifier) and
 * ink-2 while off (a mute pacifier, bone when hovered or focused); hovered or focused it is outlined.
 */
private fun DrawScope.drawRunModeToggle(text: CanvasTextMeasurer, bounds: Rect, easy: Boolean, active: Boolean) {
    val path = chipPlate(bounds.left, bounds.top, bounds.right, bounds.bottom, 8f * density)
    drawPath(path, if (easy) text.roles.soft else Kk.Ink2)
    if (active) drawPath(path, if (easy) Kk.Ink else Kk.Bone, style = kkStroke(1.5f * density))
    val pacifier = KkPacifierSigilPaths.of(bounds.height * 0.34f)
    translate(bounds.center.x, bounds.center.y) {
        drawPath(pacifier, if (easy) Kk.Ink else if (active) Kk.Bone else Kk.Mute)
    }
}

/** The stamp's size (px before the measurer's scale): 17 at the reference hero, never under 11. */
internal fun homeBabyStampSize(scene: HomeScene, density: Float): Float = (17f * scene.heroRadius / density / 260f).coerceIn(11f, 17f)

/**
 * The unrotated Baby mode stamp under the hero's singularity (px): centred on the hero, its top 0.34 hero radii below the
 * centre, under the dashed 0.27 R ring.
 */
internal fun homeBabyStampRect(text: CanvasTextMeasurer, scene: HomeScene, density: Float): Rect {
    val size = kkStampSize(text, text.language.text(SessionRedesignText.BABY_MODE), density, homeBabyStampSize(scene, density))
    val left = scene.heroCenter.x - size.width * 0.5f
    val top = scene.heroCenter.y + 0.34f * scene.heroRadius
    return Rect(left, top, left + size.width, top + size.height)
}

/** The soft "Baby mode" stamp under the hero while the next runs are Baby mode runs. */
internal fun DrawScope.drawHomeBabyStamp(text: CanvasTextMeasurer, scene: HomeScene) {
    val rect = homeBabyStampRect(text, scene, density)
    drawKkStamp(text, text.language.text(SessionRedesignText.BABY_MODE), rect.topLeft, KkStampVariant.YOU,
        fontSize = homeBabyStampSize(scene, density), color = text.roles.soft)
}

/** A header chip; [crown] draws the gold Ascendant crown before the label. */
private class HomeChip(val gem: Boolean, val value: String, val label: String?, val labelFirst: Boolean, val crown: Boolean = false)

/**
 * The header chips. The gold Rebirth chip is the rank badge: "Rank 5" (the selected tier is in the
 * Rebirth facts), or "Ascendant 10" under the gold crown once every tier is cleared.
 */
private fun homeChip(model: HomeUiModel, language: AppLanguage, compact: Boolean, index: Int): HomeChip = when (index) {
    0 -> HomeChip(true, homeNumber(model.totalMatter, language), if (compact) null else language.text(SessionRedesignText.MATTER), false)
    else -> HomeChip(false, model.rebirthRank.toString(),
        language.text(if (model.ascendant) SessionRedesignText.ASCENDANT else SessionRedesignText.RANK), true, crown = model.ascendant)
}

private fun DrawScope.chipsWidth(model: HomeUiModel, text: CanvasTextMeasurer, scene: HomeScene, count: Int, language: AppLanguage): Float {
    var total = 0f
    for (index in 0 until count) total += chipWidth(text, scene, homeChip(model, language, scene.compactChips, index)) + 8f * density
    return total
}

private fun chipValueStyle(text: CanvasTextMeasurer, scene: HomeScene) =
    text.typography.condStyle(if (scene.compactChips) 16f else 21f, trackingEm = 0.02f, tabular = true, lineHeightEm = 1f)

private fun chipLabelStyle(text: CanvasTextMeasurer) = text.typography.monoStyle(9f, trackingEm = 0.06f)

private fun DrawScope.chipWidth(text: CanvasTextMeasurer, scene: HomeScene, chip: HomeChip): Float {
    val value = measureKkText(text, chip.value, chipValueStyle(text, scene), uppercase = true)
    val label = chip.label?.let { measureKkText(text, it, chipLabelStyle(text), uppercase = true) }
    val gap = 8f * density
    val gemSize = if (scene.compactChips) 10f else 14f
    return 10f * density + (if (chip.gem) gemSize * density + gap else 0f) + (if (chip.crown) gemSize * density + gap else 0f) +
        value.size.width + (if (label != null) gap + label.size.width else 0f) + 14f * density
}

/** Header chip (`.chip`) right-aligned at [right]; returns its width. */
private fun DrawScope.drawChip(text: CanvasTextMeasurer, right: Float, scene: HomeScene, chip: HomeChip, accent: Color): Float {
    val valueLayout = measureKkText(text, chip.value, chipValueStyle(text, scene), uppercase = true)
    val labelLayout = chip.label?.let { measureKkText(text, it, chipLabelStyle(text), uppercase = true) }
    val gap = 8f * density
    val gemSize = if (scene.compactChips) 10f else 14f
    val width = chipWidth(text, scene, chip)
    val left = right - width
    val top = scene.chipsTop
    val bottom = top + scene.chipHeight
    drawPath(chipPlate(left, top, right, bottom, 8f * density), Kk.Ink2)
    var x = left + 10f * density
    val cy = (top + bottom) * 0.5f
    if (chip.gem) {
        drawKkGem(Offset(x + gemSize * density * 0.5f, cy), accent, gemSize)
        x += gemSize * density + gap
    }
    if (chip.crown) {
        val radius = gemSize * density * 0.5f
        translate(x + radius, cy) { drawPath(KkMutatorSigilPaths.of(KkMutatorSigil.ASCENDANT, radius), accent) }
        x += gemSize * density + gap
    }
    if (chip.labelFirst && labelLayout != null) {
        drawKkText(labelLayout, x, cy, Kk.Mute, valign = KkVAlign.CENTER)
        x += labelLayout.size.width + gap
        drawKkText(valueLayout, x, cy, accent, valign = KkVAlign.CENTER)
    } else {
        drawKkText(valueLayout, x, cy, Kk.Bone, valign = KkVAlign.CENTER)
        x += valueLayout.size.width + gap
        if (labelLayout != null) drawKkText(labelLayout, x, cy, Kk.Mute, valign = KkVAlign.CENTER)
    }
    return width
}

/** The header chip's slanted plate (one reused path), drawn by every chip and the Baby mode toggle. */
private fun chipPlate(left: Float, top: Float, right: Float, bottom: Float, cut: Float): Path {
    val path = chipPath
    path.rewind()
    path.moveTo(left + cut, top)
    path.lineTo(right, top)
    path.lineTo(right - cut, bottom)
    path.lineTo(left, bottom)
    path.close()
    return path
}

private val chipPath = Path()
