// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package kinetickk.app.shared.diagnostics

import androidx.compose.ui.platform.ClipEntry

actual fun createDebugClipEntry(text: String): ClipEntry {
    check(browserClipboardWriteAvailable()) { "Clipboard requires a secure context and a supported browser API" }
    return ClipEntry.withPlainText(text)
}

private fun browserClipboardWriteAvailable(): Boolean = js(
    """Boolean(window.isSecureContext && window.navigator.clipboard &&
        (typeof window.navigator.clipboard.writeText === 'function' ||
        (typeof window.navigator.clipboard.write === 'function' &&
         typeof window.navigator.clipboard.read === 'function' && typeof ClipboardItem !== 'undefined')))""",
)
