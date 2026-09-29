// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

/**
 * "New!" discovery stamp (`.stamp`): `you` face, ink cond 900 italic label, hard ink offset
 * shadow, rotated −6°. Presentation only; the caller owns whether the discovery is new.
 */
@Composable
fun DiscoveryBadge(label: String, scale: Float, modifier: Modifier = Modifier) {
    val typography = rememberInterfaceTypography()
    val face = LocalKkRolePalette.current.you
    val size = 17f * scale.coerceIn(0.75f, 1.25f)
    BasicText(
        label.uppercase(),
        modifier
            .rotate(-6f)
            .drawBehind {
                val shadow = 3.dp.toPx()
                drawRect(Kk.Ink, Offset(shadow, shadow), this.size)
                drawRect(face, Offset.Zero, this.size)
            }
            .padding(start = 11.dp, end = 11.dp, top = 5.dp, bottom = 4.dp),
        style = typography.condStyle(size, trackingEm = 0.06f, lineHeightEm = 1f, color = Kk.Ink),
    )
}
