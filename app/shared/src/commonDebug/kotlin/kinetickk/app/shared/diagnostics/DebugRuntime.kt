// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared.diagnostics

/** Cumulative process counters, sampled sparsely; null means unavailable, never zero. */
data class DebugRuntimeSample(
    val heapUsedBytes: Long? = null,
    val heapCommittedBytes: Long? = null,
    val gcCount: Long? = null,
    val gcTimeMillis: Long? = null,
    val processCpuNanos: Long? = null,
)

expect fun collectDebugRuntimeSample(): DebugRuntimeSample
expect fun debugRuntimeDescription(): String
