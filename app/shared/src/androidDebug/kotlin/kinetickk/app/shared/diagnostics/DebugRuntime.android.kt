// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared.diagnostics

import android.os.Build
import android.os.Debug
import android.os.Process

actual fun collectDebugRuntimeSample(): DebugRuntimeSample = runCatching {
    val runtime = Runtime.getRuntime()
    val committed = runtime.totalMemory()
    DebugRuntimeSample(
        heapUsedBytes = (committed - runtime.freeMemory()).coerceAtLeast(0L),
        heapCommittedBytes = committed,
        // ART exposes cumulative milliseconds, not GC pause duration for the sampled frame.
        gcCount = Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull()?.takeIf { it >= 0L },
        gcTimeMillis = Debug.getRuntimeStat("art.gc.gc-time")?.toLongOrNull()?.takeIf { it >= 0L },
        processCpuNanos = Process.getElapsedCpuTime().coerceAtLeast(0L) * 1_000_000L,
    )
}.getOrElse { DebugRuntimeSample() }

actual fun debugRuntimeDescription(): String = runCatching {
    "Android ${Build.VERSION.RELEASE} API ${Build.VERSION.SDK_INT}; " +
        "${Build.MANUFACTURER} ${Build.MODEL}; processors=${Runtime.getRuntime().availableProcessors()}; " +
        "heap=managed ART only; GC totals approximate; process CPU includes all process threads"
}.getOrDefault("Android; runtime metrics unavailable")
