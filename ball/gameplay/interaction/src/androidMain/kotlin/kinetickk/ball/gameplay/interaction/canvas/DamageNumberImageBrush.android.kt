// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap

// Android's BitmapShader uses CanvasDrawScope's FilterQuality.Low (filtered Paint).
internal actual fun damageNumberImageBrush(bitmap: ImageBitmap): Brush = imageBrush(bitmap)
