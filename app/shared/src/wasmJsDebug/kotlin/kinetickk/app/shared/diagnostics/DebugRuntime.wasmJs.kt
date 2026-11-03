// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared.diagnostics

import kotlinx.browser.window
import kotlin.js.ExperimentalWasmJsInterop

// Browsers do not expose portable process CPU, Wasm-GC heap or collection counters.
// In particular, nonstandard performance.memory describes a JS heap, not this game's Wasm heap.
internal actual fun collectDebugRuntimeSample(): DebugRuntimeSample = DebugRuntimeSample()

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun debugRuntimeDescription(): String = runCatching {
    "Web/Wasm; ${window.navigator.userAgent.take(512)}; " +
        "heap, process CPU and GC counters unavailable in the browser"
}.getOrDefault("Web/Wasm; runtime metrics unavailable")
