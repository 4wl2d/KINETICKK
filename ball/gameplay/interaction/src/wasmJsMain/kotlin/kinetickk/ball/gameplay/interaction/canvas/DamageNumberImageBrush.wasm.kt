// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.SamplingMode

internal actual fun damageNumberImageBrush(bitmap: ImageBitmap): Brush = ShaderBrush(
    bitmap.asSkiaBitmap().makeShader(FilterTileMode.DECAL, FilterTileMode.DECAL, SamplingMode.LINEAR).asComposeShader(),
)
