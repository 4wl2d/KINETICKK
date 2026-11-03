// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.workshop.armory

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.toSize
import kinetickk.ball.profile.interaction.LocalProfilePanelFocus
import kinetickk.ball.profile.interaction.ProfileTextProbe
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.ProfileLayoutMode
import kinetickk.ball.profile.interaction.dp
import kinetickk.ball.profile.interaction.drawProfileGrid
import kinetickk.ball.profile.interaction.drawProfileSidePanel
import kinetickk.ball.profile.interaction.localization.ProfileScreensRedesignText
import kinetickk.ball.profile.interaction.localization.ProfileText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.min

internal fun DrawScope.drawArmoryBackground(
    frame: ProfileFrame,
    layout: ArmoryLayout?,
    measurer: CanvasTextMeasurer,
    title: String,
) {
    drawRect(Kk.Ink)
    drawProfileGrid(frame)
    when (frame.mode) {
        ProfileLayoutMode.REGULAR -> {
            drawProfileSidePanel(frame.x(900f))
            val word = armoryBackgroundWord(measurer, frame, title)
            val topLeft = Offset(frame.x(40f), frame.d(600f) - word.kkBoxTop)
            drawText(word, Color(0xFF1F231F), topLeft, drawStyle = OutlineStroke.of(1.5f * density))
            if (ProfileTextProbe.sink != null) ProfileTextProbe.record("armory.background.word", title, word, Rect(topLeft, word.size.toSize()))
        }
        ProfileLayoutMode.COMPACT_LANDSCAPE -> if (layout != null) {
            drawProfileSidePanel(layout.detailViewport.left - size.height * KkShape.ShearRatio * 0.5f)
        }
        ProfileLayoutMode.COMPACT_PORTRAIT -> if (layout != null) {
            val top = layout.detailViewport.top
            drawRect(Kk.Ink1, Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, size.height - top))
            drawLine(Kk.Line, Offset(0f, top), Offset(size.width, top), density)
        }
    }
}

/**
 * The outlined background title: the board's 230 px word from x 40, shrunk when a longer title
 * (Russian) would pass [ARMORY_WORD_RIGHT], so it ends inside the frame like the board's word.
 */
internal fun armoryBackgroundWord(measurer: CanvasTextMeasurer, frame: ProfileFrame, title: String): TextLayoutResult {
    val room = frame.d(ARMORY_WORD_RIGHT - 40f)
    var size = 230f * frame.k
    var word = measureKkText(measurer, title, measurer.typography.wideStyle(size, lineHeightEm = 1f), uppercase = true)
    while (word.size.width > room && size > 40f * frame.k) {
        size *= min(0.98f, room / word.size.width)
        word = measureKkText(measurer, title, measurer.typography.wideStyle(size, lineHeightEm = 1f), uppercase = true)
    }
    return word
}

/** Board x the background word may reach: 20 px inside the 1440 frame, where the board's word ends. */
internal const val ARMORY_WORD_RIGHT = 1420f

/** Reused outline stroke for the big background word. */
private object OutlineStroke {
    private var width = -1f
    private var stroke: Stroke? = null

    fun of(px: Float): Stroke = stroke?.takeIf { width == px } ?: Stroke(px).also { stroke = it; width = px }
}

/**
 * Page steps for the weapon grid: shown only while the grid scrolls (phones, large text); the
 * previous/next nodes stay in the tree, disabled, when every weapon fits.
 */
/** Header room for the pager: its two steps and page count while the grid scrolls, else a 2 px stub. */
internal fun armoryPagerWidth(frame: ProfileFrame, gridScroll: ScrollState, textScale: Float): Float =
    if (gridScroll.maxValue > 0) frame.d(30f) * 2f + frame.d(8f) * 2f + frame.d(16f) + frame.d(34f) * textScale else frame.density * 2f

@Composable
internal fun RowScope.ArmoryPager(
    frame: ProfileFrame,
    gridScroll: ScrollState,
    holder: ArmoryLayoutHolder,
    textScale: Float,
    onAction: (ArmoryAction) -> Unit,
) {
    val language = LocalAppLanguage.current
    val layout = holder.layout
    val visible = gridScroll.maxValue > 0
    val (pages, page) = if (layout == null) 1 to 1 else armoryGridPages(gridScroll.value.toFloat(),
        gridScroll.maxValue.toFloat(), layout.gridViewport.height, layout.rowPitch)
    val typography = rememberInterfaceTypography()
    val previousEnabled = visible && gridScroll.value > 0
    val nextEnabled = visible && gridScroll.value < gridScroll.maxValue
    val pageText = language.text(ProfileScreensRedesignText.PageStep, page, pages)
    // When every weapon fits, the steps collapse to disabled, invisible 1 dp nodes.
    val step = if (visible) frame.d(30f) else frame.density
    Box(Modifier.alpha(if (visible) 1f else 0f)) {
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            ArmoryStepButton(frame, step, plus = false, enabled = previousEnabled, tag = "profile-armory-previous",
                description = language.text(ProfileText.PreviousPage), state = pageText) { onAction(ArmoryAction.PreviousPage) }
            if (visible) {
                Spacer(Modifier.width(frame.dp(frame.d(8f))))
                BasicText(language.text(ProfileScreensRedesignText.CountOf, page, pages),
                    style = typography.monoStyle(11f * frame.k * textScale, color = Kk.Mute, lineHeightEm = 1f), maxLines = 1, softWrap = false,
                    onTextLayout = { ProfileTextProbe.record("armory.pager", null, it, Rect(Offset.Zero, it.size.toSize())) })
                Spacer(Modifier.width(frame.dp(frame.d(8f))))
            }
            ArmoryStepButton(frame, step, plus = true, enabled = nextEnabled, tag = "profile-armory-next",
                description = language.text(ProfileText.NextPage), state = pageText) { onAction(ArmoryAction.NextPage) }
        }
    }
    if (visible) Spacer(Modifier.width(frame.dp(frame.d(16f))))
}

@Composable
private fun ArmoryStepButton(
    frame: ProfileFrame,
    size: Float,
    plus: Boolean,
    enabled: Boolean,
    tag: String,
    description: String,
    state: String,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val panelFocus = LocalProfilePanelFocus.current
    Box(
        Modifier.size(frame.dp(size))
            .testTag(tag)
            .semantics {
                contentDescription = description
                stateDescription = state
            }
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button) {
                // The last step disables this button; keep keyboard routing on the panel.
                panelFocus?.requestFocus()
                onClick()
            }
            .drawBehind { drawKkStepButton(Rect(Offset.Zero, this.size), plus, hovered, enabled) },
    )
}
