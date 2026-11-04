// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared.diagnostics

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

internal actual fun createDebugClipEntry(text: String): ClipEntry = ClipEntry(ClipData.newPlainText("KINETICKK diagnostics", text))
