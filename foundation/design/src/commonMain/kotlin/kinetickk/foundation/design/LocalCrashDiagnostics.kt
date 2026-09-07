// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.runtime.staticCompositionLocalOf
import kinetickk.foundation.diagnostics.CrashDiagnostics

val LocalCrashDiagnostics = staticCompositionLocalOf<CrashDiagnostics> { CrashDiagnostics.None }
