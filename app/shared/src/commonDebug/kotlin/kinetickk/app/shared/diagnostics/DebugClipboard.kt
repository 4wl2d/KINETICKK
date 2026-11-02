// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared.diagnostics

import androidx.compose.ui.platform.ClipEntry

/** Platform construction also rejects unavailable browser clipboard APIs instead of a silent no-op. */
expect fun createDebugClipEntry(text: String): ClipEntry
