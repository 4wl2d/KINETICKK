// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared.diagnostics

import com.sun.management.OperatingSystemMXBean
import java.lang.management.GarbageCollectorMXBean
import java.lang.management.ManagementFactory

/** Cached VM handles; no heap walk, stack sampling, forced collection or external process. */
private object DesktopDebugRuntime {
    val runtime: Runtime = Runtime.getRuntime()
    val collectors: List<GarbageCollectorMXBean> =
        runCatching { ManagementFactory.getGarbageCollectorMXBeans() }.getOrDefault(emptyList())
    val operatingSystem: OperatingSystemMXBean? =
        runCatching { ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean }.getOrNull()
}

actual fun collectDebugRuntimeSample(): DebugRuntimeSample = runCatching {
    val runtime = DesktopDebugRuntime.runtime
    val committed = runtime.totalMemory()
    var count = 0L
    var time = 0L
    var countSupported = false
    var timeSupported = false
    for (collector in DesktopDebugRuntime.collectors) {
        val collectorCount = collector.collectionCount
        if (collectorCount >= 0L) {
            count += collectorCount
            countSupported = true
        }
        val collectorTime = collector.collectionTime
        if (collectorTime >= 0L) {
            time += collectorTime
            timeSupported = true
        }
    }
    DebugRuntimeSample(
        heapUsedBytes = (committed - runtime.freeMemory()).coerceAtLeast(0L),
        heapCommittedBytes = committed,
        gcCount = if (countSupported) count else null,
        gcTimeMillis = if (timeSupported) time else null,
        processCpuNanos = DesktopDebugRuntime.operatingSystem?.processCpuTime?.takeIf { it >= 0L },
    )
}.getOrElse { DebugRuntimeSample() }

actual fun debugRuntimeDescription(): String = runCatching {
    "Desktop ${System.getProperty("os.name")} ${System.getProperty("os.version")} " +
        "${System.getProperty("os.arch")}; JVM ${System.getProperty("java.vm.name")} " +
        "${System.getProperty("java.version")}; processors=${Runtime.getRuntime().availableProcessors()}; " +
        "heap=managed JVM only; GC totals approximate; process CPU includes all process threads"
}.getOrDefault("Desktop; runtime metrics unavailable")
