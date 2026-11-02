// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Density
import java.io.File
import kinetickk.foundation.common.localization.AppLanguage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.LocalResourceReader
import org.jetbrains.compose.resources.ResourceReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalResourceApi::class)
class InterfaceTypographyLoaderTest {
    @Test
    fun manifestIncludesEveryBundledFont() {
        val files = requireNotNull(fontDirectory.listFiles()).filter { it.extension == "ttf" }
        assertEquals(files.map { it.nameWithoutExtension }.toSet(), InterfaceFontResources.all.map { it.name }.toSet())
        assertEquals(10, files.size)
    }

    @Test
    fun filesLoadConcurrentlyOnceAndRetainedFontsResolveWithoutFurtherReads() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val reads = mutableListOf<String>()
        val source = interfaceFontsFromBytes(
            read = { resource ->
                val name = InterfaceFontResources.all.first { it.resource == resource }.name
                reads += name
                release.await()
                File(fontDirectory, "$name.ttf").readBytes()
            },
            validate = ::validateInterfaceFontBytes,
            create = { font, bytes -> Font(interfaceFontIdentity(font), bytes, font.weight, font.style) },
        )
        val loader = InterfaceTypographyLoader(source, createFontFamilyResolver())
        val progress = mutableListOf<Pair<Int, Int>>()
        val loading = async { loader.load { done, total -> progress += done to total } }
        // Each child starts and waits for the same barrier; serial loading cannot get past one read.
        withTimeout(5_000) { while (reads.size < 10) yield() }
        assertEquals(10, reads.distinct().size)
        release.complete(Unit)
        val typography = loading.await()
        assertEquals(17 to 17, progress.last())
        assertEquals((1..17).toList(), progress.map { it.first })
        typography.fontFamilies().forEach { assertNotEquals(FontFamily.SansSerif, it) }
        assertSame(typography.cond, typography.label)
        val resolver = createFontFamilyResolver()
        InterfaceTypographyLoader(source, resolver).resolve(typography)
        // Both legacy upright style requests resolve before any menu can use them.
        resolver.resolve(typography.display, FontWeight.Bold, FontStyle.Normal).value
        resolver.resolve(typography.display, FontWeight.Normal, FontStyle.Normal).value
        assertEquals(10, reads.size, "New window resolver must reuse loaded font bytes")
        val measurer = kkTestMeasurer(typography = typography)
        val reference = kkTestMeasurer()
        for (text in listOf("MOMENTUM", "ДВИЖЕНИЕ", "0123456789")) {
            assertEquals(
                measureKkText(reference, text, reference.typography.wideStyle(30f)).size,
                measureKkText(measurer, text, typography.wideStyle(30f)).size,
                "Preloaded wide font must match the bundled file for $text",
            )
            assertEquals(
                measureKkText(reference, text, reference.typography.condStyle(30f)).size,
                measureKkText(measurer, text, typography.condStyle(30f)).size,
                "Preloaded condensed font must match the bundled file for $text",
            )
        }
    }

    @Test
    fun failedResourceLoadCanBeRetriedWithoutPublishingFallbackFonts() = runBlocking {
        var fail = true
        val source = interfaceFontsFromBytes(
            read = { resource ->
                if (fail) error("missing font")
                val name = InterfaceFontResources.all.first { it.resource == resource }.name
                File(fontDirectory, "$name.ttf").readBytes()
            },
            validate = ::validateInterfaceFontBytes,
            create = { font, bytes -> Font(interfaceFontIdentity(font), bytes, font.weight, font.style) },
        )
        val loader = InterfaceTypographyLoader(source, createFontFamilyResolver())
        assertFailsWith<IllegalStateException> { loader.load() }
        fail = false
        assertNotEquals(FontFamily.SansSerif, loader.load().body)
    }

    @Test
    fun corruptFontResponseFailsBeforeResolverCacheAndRetryUsesBundledFont() = runBlocking {
        var corrupt = true
        var created = 0
        val source = interfaceFontsFromBytes(
            read = { resource ->
                val name = InterfaceFontResources.all.first { it.resource == resource }.name
                if (corrupt && name == "kk_wide_black") {
                    "<!doctype html><html>Temporary server error</html>".encodeToByteArray()
                } else {
                    File(fontDirectory, "$name.ttf").readBytes()
                }
            },
            validate = ::validateInterfaceFontBytes,
            create = { font, bytes ->
                created++
                Font(interfaceFontIdentity(font), bytes, font.weight, font.style)
            },
        )
        val loader = InterfaceTypographyLoader(source, createFontFamilyResolver())
        assertFailsWith<IllegalStateException> { loader.load() }
        assertEquals(0, created, "A corrupt file must fail before any font enters the resolver cache")
        corrupt = false
        val loaded = loader.load()
        val measurer = kkTestMeasurer(typography = loaded)
        val reference = kkTestMeasurer()
        val text = "ДВИЖЕНИЕ MOMENTUM"
        assertEquals(
            measureKkText(reference, text, reference.typography.wideStyle(30f)).size,
            measureKkText(measurer, text, loaded.wideStyle(30f)).size,
            "Retry with the same font identity must render the bundled face, not cached fallback",
        )
    }

    @Test
    fun appConsumersReusePreparedFamiliesAcrossLanguageChanges() = runSkikoComposeUiTest(Size(200f, 100f), Density(1f)) {
        val language = mutableStateOf(AppLanguage.English)
        val prepared = KkTestFonts.typography
        var current: InterfaceTypography? = null
        val forbiddenReader = object : ResourceReader {
            override suspend fun read(path: String): ByteArray = error("Unexpected resource read: $path")
            override suspend fun readPart(path: String, offset: Long, size: Long): ByteArray = error("Unexpected resource read: $path")
            override fun getUri(path: String): String = error("Unexpected resource URI: $path")
        }
        setContent {
            CompositionLocalProvider(
                LocalInterfaceTypography provides prepared,
                LocalAppLanguage provides language.value,
                LocalResourceReader provides forbiddenReader,
            ) {
                current = rememberInterfaceTypography()
            }
        }
        waitForIdle()
        assertSame(prepared.wide, requireNotNull(current).wide)
        language.value = AppLanguage.Russian
        waitForIdle()
        assertSame(prepared.body, requireNotNull(current).body)
        assertEquals(AppLanguage.Russian.localeList(), requireNotNull(current).localeList)
    }

    private val fontDirectory = File("src/commonMain/composeResources/font")
}
