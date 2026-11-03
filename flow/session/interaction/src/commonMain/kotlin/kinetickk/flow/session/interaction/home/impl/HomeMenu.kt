// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.home.impl

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextLayoutResult
import kinetickk.flow.session.interaction.home.api.HomeUiModel
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.KkVAlign
import kinetickk.foundation.design.drawKkMenuItem
import kinetickk.foundation.design.drawKkText
import kinetickk.foundation.design.measureKkText
import kinetickk.foundation.design.wideStyle
import kotlin.math.max

internal fun DrawScope.drawMenu(
    model: HomeUiModel,
    tinted: CanvasTextMeasurer,
    layout: HomeLayoutGeometry,
    motion: HomeMenuMotion,
    activeTarget: HomeLayoutTarget,
    textScale: Float,
) {
    val scene = layout.scene
    val language = tinted.language
    val menu = motion.menuMeasurer(tinted)
    HomeMenuTargets.forEachIndexed { index, target ->
        val label = language.text(HomeMenuLabels[index])
        val facts = motion.menuFacts(model, index, language, textScale)
        // One column: the item stays inside its row (see homeMenuPlacement).
        val left = if (scene.menuColumns == 1) motion.menuPlacement(menu, layout, index, label, facts, density) else layout.bounds(target).left
        val showSub = scene.menuColumns == 1 && motion.menuShowsSub(index)
        val selection = if (target == activeTarget) 1f else 0f
        drawHomeMenuItem(menu, layout, index, label, facts, left, showSub, motion.menuStampSize, selection, motion.trailTime)
    }
}

/**
 * Menu item [index] as Home draws it with the menu measurer [menu]: from [left] (one column: see
 * [homeMenuPlacement], which also decides [showSub]) at its row's center, its stamp at [stampSize]
 * (see [homeMenuStampSize]), [selection] 0..1.
 */
internal fun DrawScope.drawHomeMenuItem(
    menu: CanvasTextMeasurer,
    layout: HomeLayoutGeometry,
    index: Int,
    label: String,
    facts: HomeFacts,
    left: Float,
    showSub: Boolean,
    stampSize: Float,
    selection: Float,
    time: Float,
) {
    val scene = layout.scene
    val bounds = layout.bounds(HomeMenuTargets[index])
    if (scene.menuColumns == 1) {
        drawKkMenuItem(
            menu, left, bounds.center.y, label,
            selection = selection,
            time = time,
            fontSize = displaySize(menu, scene.menuFontSize),
            // The board keeps the stamp right after the label; the sub value joins after it when selected.
            stamp = facts.stamp,
            sub = if (showSub && selection > 0.01f) facts.sub else null,
            edgeRight = size.width,
            stampFontSize = stampSize,
        )
    } else {
        // Two-column phone menus have no room for the stamp or the sub value. The clip keeps a selected
        // item to its row and column, but leaves room left of the column for its slide and echo so the
        // slab keeps its skewed cut and its lead before the label; the speed lines, which would cross
        // the neighbouring column, stay out.
        val room = (HOME_MENU_SLIDE + HOME_MENU_ECHO_LEFT) * scene.menuFontSize / 64f + density
        clipRect(max(0f, bounds.left - room), bounds.top - 2f * density, bounds.right + 4f * density, bounds.bottom + 2f * density) {
            drawKkMenuItem(
                menu, left, bounds.center.y, label,
                selection = selection,
                time = time,
                fontSize = displaySize(menu, scene.menuFontSize),
                edgeRight = bounds.right,
            )
        }
    }
}

/**
 * The form name at its display size [sizePx], or smaller when that is wider than [room] px (a long
 * locked name on the smallest landscape screens): shrunk to fit whole, never cut.
 */
internal fun homeFormNameLayout(text: CanvasTextMeasurer, name: String, sizePx: Float, room: Float, density: Float): TextLayoutResult {
    val size = max(1f, sizePx / density / text.scale)
    val full = measureKkText(text, name, text.typography.wideStyle(size), uppercase = true)
    if (full.size.width <= room) return full
    return measureKkText(text, name, text.typography.wideStyle(size * max(0.5f, room / full.size.width) * 0.99f), uppercase = true)
}

/** The form [name] from the scene's anchor, ending before [nameRight] (see [homeFormNameRight]). */
internal fun DrawScope.drawHomeFormName(text: CanvasTextMeasurer, scene: HomeScene, name: String, nameRight: Float, color: Color) {
    drawKkText(
        homeFormNameLayout(text, name, scene.formNameSize, nameRight - scene.formNameLeft, density),
        scene.formNameLeft, scene.formNameCenterY, color, valign = KkVAlign.CENTER,
    )
}
