// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The applied interface scale (1 = 100 %). Menus and overlays receive it already folded into their
 * `LocalDensity`, so they lay out at the scaled logical size; the run keeps the raw density (the
 * visible arena never changes) and scales its own controls and readouts by this factor.
 */
val LocalKkInterfaceScale = staticCompositionLocalOf { 1f }
