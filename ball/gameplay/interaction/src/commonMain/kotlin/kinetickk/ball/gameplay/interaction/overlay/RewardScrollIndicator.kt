// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.unit.dp
import kinetickk.ball.gameplay.interaction.localization.GameplayText
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.LocalAppLanguage
import kotlin.math.max

@Composable
internal fun RewardScrollIndicator(
    scrollState: ScrollState,
    accent: Color,
    modifier: Modifier,
    description: String = LocalAppLanguage.current.text(GameplayText.ScrollableRewards),
) {
    if (scrollState.maxValue <= 0) return
    Canvas(modifier.semantics { stateDescription = description }) {
        // The range can shrink to zero (text size change) before recomposition removes this bar.
        val max = scrollState.maxValue
        if (max <= 0) return@Canvas
        drawRect(accent.copy(alpha = 0.15f))
        val contentHeight = scrollState.viewportSize + max
        val fraction = if (contentHeight > 0) scrollState.viewportSize.toFloat() / contentHeight else 1f
        val thumbHeight = (size.height * fraction).coerceAtLeast(12.dp.toPx()).coerceAtMost(size.height)
        val top = (size.height - thumbHeight) * scrollState.value.coerceIn(0, max) / max
        drawRect(accent, Offset(0f, top), Size(size.width, thumbHeight))
    }
}
