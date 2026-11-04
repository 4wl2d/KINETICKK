// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import kinetickk.foundation.design.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class)
@Composable
internal actual fun rememberPlatformInterfaceFontLoader(): InterfaceFontLoader {
    val assets = LocalContext.current.applicationContext.assets
    return remember(assets) {
        InterfaceFontLoader { onFontLoaded ->
            InterfaceFontResources.all.map { font ->
                // Android's asset Font retains a native Typeface, but does I/O in its constructor.
                // Keep that constructor inside the retryable loader, away from composition.
                val loaded = withContext(Dispatchers.IO) {
                    val uri = Res.getUri("font/${font.name}.ttf")
                    val prefix = "file:///android_asset/"
                    check(uri.startsWith(prefix)) { "Bundled font is not an Android asset: ${font.name}" }
                    Font(uri.removePrefix(prefix), assets, font.weight, font.style)
                }
                onFontLoaded()
                loaded
            }
        }
    }
}
