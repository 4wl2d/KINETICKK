// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import kinetickk.foundation.design.InterfaceTypography
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class StartupStage { Fonts, Audio, Graphics }

internal data class StartupProgress(
    val stage: StartupStage = StartupStage.Fonts,
    val completed: Int = 0,
    val total: Int = 1,
) {
    val fraction: Float
        get() = (stage.ordinal + completed.toFloat() / total.coerceAtLeast(1)) / StartupStage.entries.size
}

internal data class ReadyStartupResources(
    val typography: InterfaceTypography,
    val fontFamilyResolver: FontFamily.Resolver,
)

/** Owned by the application, so replacing a desktop window does not reload resources. */
internal class AppStartupResources {
    private val mutex = Mutex()
    private var typography: InterfaceTypography? = null
    private var audioReady = false
    private var graphicsReady = false
    var readyValue by mutableStateOf<ReadyStartupResources?>(null)
        private set

    fun publish(fonts: InterfaceTypography, resolver: FontFamily.Resolver) {
        check(fonts === typography && audioReady && graphicsReady)
        readyValue = ReadyStartupResources(fonts, resolver)
    }

    /** A failed/cancelled stage stays unfinished; retry reuses only successfully prepared resources. */
    suspend fun prepare(
        loadFonts: suspend ((Int, Int) -> Unit) -> InterfaceTypography,
        resolveFonts: suspend (InterfaceTypography) -> Unit,
        prepareAudio: suspend () -> Unit,
        prepareGraphics: suspend () -> Unit,
        onProgress: suspend (StartupProgress) -> Unit,
        onFontProgress: (StartupProgress) -> Unit,
    ): InterfaceTypography = mutex.withLock {
        onProgress(StartupProgress(StartupStage.Fonts))
        val fonts = typography?.also { resolveFonts(it) } ?: loadFonts { done, total ->
            onFontProgress(StartupProgress(StartupStage.Fonts, done, total))
        }.also { typography = it }
        onProgress(StartupProgress(StartupStage.Audio))
        if (!audioReady) {
            prepareAudio()
            audioReady = true
        }
        onProgress(StartupProgress(StartupStage.Graphics))
        if (!graphicsReady) {
            prepareGraphics()
            graphicsReady = true
        }
        onProgress(StartupProgress(StartupStage.Graphics, 1, 1))
        fonts
    }
}
