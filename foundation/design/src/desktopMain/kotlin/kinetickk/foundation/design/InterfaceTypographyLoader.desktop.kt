// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.platform.Font
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getFontResourceBytes
import org.jetbrains.compose.resources.rememberResourceEnvironment
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr

@Composable
internal actual fun rememberPlatformInterfaceFontLoader(): InterfaceFontLoader {
    val environment = rememberResourceEnvironment()
    return remember(environment) {
        interfaceFontsFromBytes(
            read = { resource -> withContext(Dispatchers.IO) { getFontResourceBytes(environment, resource) } },
            validate = ::validateInterfaceFontBytes,
            create = { font, bytes -> Font(interfaceFontIdentity(font), bytes, font.weight, font.style) },
        )
    }
}

internal fun validateInterfaceFontBytes(font: InterfaceFontResource, bytes: ByteArray) {
    val data = Data.makeFromBytes(bytes)
    try {
        val typeface = checkNotNull(FontMgr.default.makeFromData(data)) { "Invalid bundled font: ${font.name}" }
        typeface.close()
    } finally {
        data.close()
    }
}
