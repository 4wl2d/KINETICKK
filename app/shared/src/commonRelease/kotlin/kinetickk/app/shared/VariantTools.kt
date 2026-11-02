// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.runtime.Composable
import kinetickk.foundation.diagnostics.CrashDiagnostics

/** Release contains no recorder, sampling coroutine, diagnostic UI or platform probe. */
@Composable
internal fun rememberDebugDiagnostics(downstream: CrashDiagnostics): CrashDiagnostics = downstream

@Composable
internal fun DebugToolsHost(
    diagnostics: CrashDiagnostics,
    displayHost: PlatformDisplayHost,
    content: @Composable () -> Unit,
) = content()
