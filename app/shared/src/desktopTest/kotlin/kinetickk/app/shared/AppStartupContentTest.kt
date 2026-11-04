// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.diagnostics.CrashDiagnostics
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

@OptIn(ExperimentalTestApi::class)
class AppStartupContentTest {
    @Test
    fun replacingTheWindowKeepsReadyResourcesAndTheLoadedFontResolver() = runComposeUiTest {
        val resources = AppStartupResources()
        val generation = mutableIntStateOf(0)
        var audioPreparations = 0
        var graphicsPreparations = 0
        val resolvers = mutableListOf<androidx.compose.ui.text.font.FontFamily.Resolver>()
        setContent {
            key(generation.intValue) {
                AppStartupContent(
                    resources, AppLanguage.English, CrashDiagnostics.None,
                    prepareAudio = { audioPreparations++ },
                    prepareGraphics = { graphicsPreparations++ },
                ) {
                    resolvers += LocalFontFamilyResolver.current
                    Box(Modifier.testTag("game"))
                }
            }
        }
        awaitAppStartup()
        val ready = resources.readyValue
        runOnIdle { generation.intValue++ }
        waitForIdle()
        onNodeWithTag("game").assertExists()
        onNodeWithTag("startup-loading").assertDoesNotExist()
        assertSame(ready, resources.readyValue)
        assertSame(resolvers.first(), resolvers.last())
        assertEquals(1, audioPreparations)
        assertEquals(1, graphicsPreparations)
    }

    @Test
    fun gameIsNotComposedUntilFontsAudioAndGraphicsAreReady() = runComposeUiTest {
        val audio = CompletableDeferred<Unit>()
        var preparingAudio = false
        var graphicsReady = false
        val resources = AppStartupResources()
        setContent {
            Box(Modifier.requiredSize(1000.dp, 700.dp)) {
                AppStartupContent(
                    resources, AppLanguage.Russian, CrashDiagnostics.None,
                    prepareAudio = { preparingAudio = true; audio.await() },
                    prepareGraphics = { graphicsReady = true },
                ) { Box(Modifier.testTag("game")) }
            }
        }
        waitUntil { preparingAudio }
        onNodeWithTag("startup-loading").assertExists()
        onNodeWithTag("game").assertDoesNotExist()
        assertFalse(graphicsReady)
        val capture = File("build/startup-shots/loading.png")
        capture.parentFile.mkdirs()
        Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).use { image ->
            image.encodeToData()!!.use { data -> capture.writeBytes(data.bytes) }
        }
        runOnIdle { audio.complete(Unit) }
        waitUntil { onAllNodesWithTag("game").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("startup-loading").assertDoesNotExist()
        assertEquals(true, graphicsReady)
    }

    @Test
    fun failureKeepsTheGameClosedAndOffersAWorkingRetry() = runComposeUiTest {
        var attempts = 0
        val resources = AppStartupResources()
        setContent {
            AppStartupContent(
                resources, AppLanguage.English, CrashDiagnostics.None,
                prepareAudio = { attempts++; if (attempts == 1) error("temporary failure") },
                prepareGraphics = {},
            ) { Box(Modifier.testTag("game")) }
        }
        waitUntil { onAllNodesWithTag("startup-retry").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("game").assertDoesNotExist()
        onNodeWithTag("startup-retry").performClick()
        waitUntil { onAllNodesWithTag("game").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, attempts)
    }
}
