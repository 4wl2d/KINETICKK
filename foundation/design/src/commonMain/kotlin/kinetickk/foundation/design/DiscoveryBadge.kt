// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp

/** Presentation only; the caller owns whether the discovery is new. */
@Composable
fun DiscoveryBadge(label: String, scale: Float, modifier: Modifier = Modifier) {
    BasicText(label, modifier.drawBehind {
        drawKineticRibbon(Rect(Offset.Zero, size), KineticAccent, 4.dp.toPx())
    }.padding(horizontal = 8.dp, vertical = 3.dp),
        style = interfaceTextStyle(12f * scale.coerceAtMost(1.25f), SpaceBlack, display = true))
}
