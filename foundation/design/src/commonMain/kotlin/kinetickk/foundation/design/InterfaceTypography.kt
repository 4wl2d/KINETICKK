// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.Font
import kinetickk.foundation.design.generated.resources.Res
import kinetickk.foundation.design.generated.resources.onest
import kinetickk.foundation.design.generated.resources.oswald

data class InterfaceTypography(
    val body: FontFamily = FontFamily.SansSerif,
    val display: FontFamily = FontFamily.SansSerif,
)

@Composable
fun rememberInterfaceTypography(): InterfaceTypography {
    val body = Font(Res.font.onest)
    val display = Font(Res.font.oswald, weight = FontWeight.Bold)
    return remember(body, display) { InterfaceTypography(FontFamily(body), FontFamily(display)) }
}

@Composable
fun interfaceTextStyle(size: Float, color: Color = White, weight: FontWeight = FontWeight.Normal, display: Boolean = false): androidx.compose.ui.text.TextStyle {
    val typography = rememberInterfaceTypography()
    return textStyle(size, color, if (display) FontWeight.Bold else weight, if (display) typography.display else typography.body)
}
