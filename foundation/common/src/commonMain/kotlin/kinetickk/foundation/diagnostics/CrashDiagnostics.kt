// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.diagnostics

/** Mechanical, best-effort telemetry. Implementations must not throw or change game decisions. */
interface CrashDiagnostics {
    /** Capture immutable values only; never query an owner from this deferred formatter. */
    fun context(key: String, describe: () -> String)
    fun event(category: String, message: String, highFrequency: Boolean = false)

    data object None : CrashDiagnostics {
        override fun context(key: String, describe: () -> String) = Unit
        override fun event(category: String, message: String, highFrequency: Boolean) = Unit
    }
}
