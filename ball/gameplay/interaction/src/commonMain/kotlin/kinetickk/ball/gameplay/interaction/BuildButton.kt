// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction

import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.drawKkSlab
import kinetickk.foundation.design.kkStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text

/** Ghost icon button (`.ibtn`) opening the build overview (Codex) during a run; no key letter. */
@Composable
internal fun BuildButton(modifier: Modifier, regularUnit: Float, onClick: () -> Unit) {
    val label = LocalAppLanguage.current.text(GameplayText.Build)
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val hovered by interactions.collectIsHoveredAsState()
    Box(
        modifier
            .testTag("kinetickk.gameplay.build")
            .semantics { contentDescription = label }
            .hoverable(interactions)
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = if (regularUnit > 0f) regularUnit else density
            val width = (if (regularUnit > 0f) 40f else 44f) * unit
            val height = (if (regularUnit > 0f) 36f else 40f) * unit
            val left = center.x - width * 0.5f
            val top = center.y - height * 0.5f
            val active = focused || hovered
            drawBuildButton(Rect(left, top, left + width, top + height), active, focused, unit)
        }
    }
}

/** Ink-3 slab (bone when hovered/focused) with a stacked-plates mark; focus adds the bone outline. */
private fun DrawScope.drawBuildButton(bounds: Rect, active: Boolean, focused: Boolean, unit: Float) {
    val cut = 8f * density
    drawKkSlab(bounds, if (active) Kk.Bone else Kk.Ink3, cut)
    val ink = if (active) Kk.Ink else Kk.Bone
    val plateWidth = 16f * unit
    val plateHeight = 3f * unit
    for (index in 0 until 3) {
        val y = bounds.center.y - 7f * unit + index * 6f * unit
        val x = bounds.center.x - plateWidth * 0.5f + (1 - index) * 2f * unit
        drawRect(ink, Offset(x, y), androidx.compose.ui.geometry.Size(plateWidth, plateHeight))
    }
    if (focused) {
        val gap = 4f * density
        drawRect(Kk.Bone, Offset(bounds.left - gap, bounds.top - gap),
            androidx.compose.ui.geometry.Size(bounds.width + gap * 2f, bounds.height + gap * 2f),
            style = kkStroke(2f * density))
    }
}
