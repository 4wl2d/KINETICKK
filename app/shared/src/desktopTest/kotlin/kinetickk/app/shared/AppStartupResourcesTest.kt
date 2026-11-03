// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.ui.text.font.createFontFamilyResolver
import kinetickk.foundation.design.InterfaceTypography
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class AppStartupResourcesTest {
    @Test
    fun repeatedPreparationReusesResourcesAndResolvesRetainedFonts() = runBlocking {
        val resources = AppStartupResources()
        val fonts = InterfaceTypography()
        val calls = mutableListOf<String>()
        suspend fun prepare() = resources.prepare(
            loadFonts = { calls += "load"; fonts },
            resolveFonts = { assertSame(fonts, it); calls += "resolve" },
            prepareAudio = { calls += "audio" },
            prepareGraphics = { calls += "graphics" },
            onProgress = {}, onFontProgress = {}, resolver = createFontFamilyResolver(),
        )
        prepare()
        assertSame(fonts, resources.readyValue?.typography)
        prepare()
        assertSame(fonts, resources.readyValue?.typography)
        assertEquals(listOf("load", "audio", "graphics", "resolve"), calls)
    }

    @Test
    fun retryResumesAtTheFailedStageWithoutRepeatingSuccessfulWork() = runBlocking {
        val resources = AppStartupResources()
        var loads = 0
        var audio = 0
        var graphics = 0
        suspend fun prepare() = resources.prepare(
            loadFonts = { loads++; InterfaceTypography() }, resolveFonts = {},
            prepareAudio = { audio++; if (audio == 1) error("output not ready") },
            prepareGraphics = { graphics++ }, onProgress = {}, onFontProgress = {},
            resolver = createFontFamilyResolver(),
        )
        assertFailsWith<IllegalStateException> { prepare() }
        assertEquals(0, graphics)
        assertNull(resources.readyValue)
        prepare()
        assertEquals(1, loads)
        assertEquals(2, audio)
        assertEquals(1, graphics)
    }

    @Test
    fun cancelledFontLoadCannotOpenTheGateOrPoisonTheNextAttempt() = runBlocking {
        val resources = AppStartupResources()
        val blocked = CompletableDeferred<InterfaceTypography>()
        var audio = 0
        val cancelled = async(start = CoroutineStart.UNDISPATCHED) {
            resources.prepare(
                loadFonts = { blocked.await() }, resolveFonts = {}, prepareAudio = { audio++ },
                prepareGraphics = {}, onProgress = {}, onFontProgress = {}, resolver = createFontFamilyResolver(),
            )
        }
        cancelled.cancelAndJoin()
        assertEquals(0, audio)
        assertNull(resources.readyValue)
        val fonts = InterfaceTypography()
        resources.prepare(
            loadFonts = { fonts }, resolveFonts = { error("cancelled fonts were retained") },
            prepareAudio = { audio++ }, prepareGraphics = {}, onProgress = {}, onFontProgress = {},
            resolver = createFontFamilyResolver(),
        )
        assertSame(fonts, resources.readyValue?.typography)
        assertEquals(1, audio)
    }
}
