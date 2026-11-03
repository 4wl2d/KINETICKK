// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import kotlin.math.min
import kotlinx.coroutines.flow.collectLatest

/** The smallest share of its font size a card body shrinks to at the default text size before its text scrolls. */
internal const val RewardCardMinFit = 0.72f

/**
 * The size steps a card body tries, largest first: down to [RewardCardMinFit], and at a larger
 * text setting ([textScale] above the default) further down to the size the smallest setting
 * (100 %) gives it, so a long text still shows whole on a desktop card.
 */
internal fun rewardCardFitSteps(textScale: Float): FloatArray {
    val floor = min(RewardCardMinFit, RewardCardSmallestTextScale / textScale)
    return FloatArray(((1f - floor) / RewardCardFitStep + 0.001f).toInt() + 1) { 1f - it * RewardCardFitStep }
}

private const val RewardCardFitStep = 0.07f

/** The UI text factor of the smallest text setting (100 % against the 125 % default). */
private const val RewardCardSmallestTextScale = 0.8f

internal class RewardCardFitState {
    var requested by mutableFloatStateOf(1f)
    var tailOverflows by mutableStateOf(false)
}

/**
 * Measure the actual text once. A bounded fit step follows on the next frame only if that text
 * overflows, instead of composing several invisible copies of every card at opening.
 * Costs remain pinned below the body; at the minimum size the body keeps its normal scrolling.
 */
@Composable
internal fun RewardCardFitLayout(
    steps: FloatArray,
    cap: Float,
    state: RewardCardFitState,
    scrollState: ScrollState,
    height: Dp,
    onFit: (Float) -> Unit,
    modifier: Modifier,
    tail: (@Composable (Float, Dp) -> Unit)? = null,
    tailGap: Density.(Float) -> Int = { 0 },
    content: @Composable (Float, Dp) -> Unit,
) {
    val fit = min(state.requested, cap)
    LaunchedEffect(state, state.requested, onFit) { onFit(state.requested) }
    LaunchedEffect(state, scrollState, fit, steps) {
        snapshotFlow { scrollState.maxValue in 1 until Int.MAX_VALUE || state.tailOverflows }
            .collectLatest { overflow ->
                if (overflow && fit > steps.last() + 0.001f) {
                    withFrameNanos { }
                    if (scrollState.maxValue in 1 until Int.MAX_VALUE || state.tailOverflows) {
                        state.requested = steps.firstOrNull { it < fit - 0.001f } ?: steps.last()
                    }
                }
            }
    }
    Layout(
        content = {
            Box { content(fit, height) }
            if (tail != null) Box { tail(fit, height) }
        },
        modifier = modifier.clipToBounds(),
    ) { measurables, constraints ->
        val width = Constraints.fixedWidth(constraints.maxWidth)
        val cost = measurables.getOrNull(1)?.measure(width)
        val gap = if (cost == null) 0 else tailGap(fit)
        val available = (constraints.maxHeight - (cost?.height ?: 0) - gap).coerceAtLeast(0)
        val body = measurables.first().measure(width.copy(maxHeight = available))
        state.tailOverflows = (cost?.height ?: 0) + gap > constraints.maxHeight
        val tailTop = min(body.height + 1 + gap, constraints.maxHeight - (cost?.height ?: 0)).coerceAtLeast(0)
        layout(constraints.maxWidth, constraints.maxHeight) {
            body.place(0, 0)
            cost?.place(0, tailTop)
        }
    }
}

/**
 * Fades the bottom [height] of a scrolling text column out while more text follows below. It
 * masks the text layer only, so nothing is painted over the card under it.
 */
internal fun Modifier.rewardScrollFade(scrollState: ScrollState, height: Dp): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        if (scrollState.canScrollForward) {
            val fade = height.toPx()
            drawRect(
                Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = size.height - fade, endY = size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }
