// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kinetickk.foundation.design.generated.resources.Res
import kinetickk.foundation.design.generated.resources.kk_body_bold
import kinetickk.foundation.design.generated.resources.kk_body_medium
import kinetickk.foundation.design.generated.resources.kk_body_regular
import kinetickk.foundation.design.generated.resources.kk_cond_black
import kinetickk.foundation.design.generated.resources.kk_cond_black_italic
import kinetickk.foundation.design.generated.resources.kk_cond_extrabold
import kinetickk.foundation.design.generated.resources.kk_mono_bold
import kinetickk.foundation.design.generated.resources.kk_mono_medium
import kinetickk.foundation.design.generated.resources.kk_wide_black
import kinetickk.foundation.design.generated.resources.kk_wide_bold
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield
import org.jetbrains.compose.resources.FontResource

/** Captures the current window's resolver; keep the returned typography with the app owner. */
@Composable
fun rememberInterfaceTypographyLoader(): InterfaceTypographyLoader {
    val fonts = rememberPlatformInterfaceFontLoader()
    val resolver = LocalFontFamilyResolver.current
    return remember(fonts, resolver) { InterfaceTypographyLoader(fonts, resolver) }
}

class InterfaceTypographyLoader internal constructor(
    private val fonts: InterfaceFontLoader,
    private val resolver: FontFamily.Resolver,
) {
    /** Retain with loaded fonts when a desktop window is recreated for display preferences. */
    val fontFamilyResolver: FontFamily.Resolver get() = resolver

    /** Completes only after every bundled face and legacy alias can be used without resource I/O. */
    suspend fun load(onProgress: (Int, Int) -> Unit = { _, _ -> }): InterfaceTypography {
        val total = InterfaceFontResources.all.size + 5
        var completed = 0
        val loaded = fonts.load { onProgress(++completed, total) }
        val typography = interfaceTypography(loaded)
        for (family in typography.fontFamilies()) {
            resolver.preload(family)
            onProgress(++completed, total)
            yield()
        }
        return typography
    }

    /** A recreated desktop window resolves the retained fonts without rereading their resources. */
    suspend fun resolve(typography: InterfaceTypography) {
        for (family in typography.fontFamilies()) {
            resolver.preload(family)
            yield()
        }
    }
}

internal fun InterfaceTypography.fontFamilies(): List<FontFamily> = listOf(body, display, wide, cond, mono)

internal data class InterfaceFontResource(
    val name: String,
    val resource: FontResource,
    val weight: FontWeight,
    val style: FontStyle = FontStyle.Normal,
)

/** Both startup and standalone previews use this manifest, including the two legacy aliases. */
internal object InterfaceFontResources {
    val all = listOf(
        InterfaceFontResource("kk_wide_black", Res.font.kk_wide_black, FontWeight.Black),
        InterfaceFontResource("kk_wide_bold", Res.font.kk_wide_bold, FontWeight.Bold),
        InterfaceFontResource("kk_cond_black_italic", Res.font.kk_cond_black_italic, FontWeight.Black, FontStyle.Italic),
        InterfaceFontResource("kk_cond_black", Res.font.kk_cond_black, FontWeight.Black),
        InterfaceFontResource("kk_cond_extrabold", Res.font.kk_cond_extrabold, FontWeight.ExtraBold),
        InterfaceFontResource("kk_body_regular", Res.font.kk_body_regular, FontWeight.Normal),
        InterfaceFontResource("kk_body_medium", Res.font.kk_body_medium, FontWeight.Medium),
        InterfaceFontResource("kk_body_bold", Res.font.kk_body_bold, FontWeight.Bold),
        InterfaceFontResource("kk_mono_medium", Res.font.kk_mono_medium, FontWeight.Medium),
        InterfaceFontResource("kk_mono_bold", Res.font.kk_mono_bold, FontWeight.Bold),
        InterfaceFontResource("kk_cond_black_italic", Res.font.kk_cond_black_italic, FontWeight.Bold),
        InterfaceFontResource("kk_cond_extrabold", Res.font.kk_cond_extrabold, FontWeight.Normal),
    )
}

internal fun interfaceTypography(fonts: List<Font>): InterfaceTypography {
    require(fonts.size == InterfaceFontResources.all.size)
    val cond = FontFamily(fonts[2], fonts[3], fonts[4])
    return InterfaceTypography(
        wide = FontFamily(fonts[0], fonts[1]),
        cond = cond,
        label = cond,
        body = FontFamily(fonts[5], fonts[6], fonts[7]),
        mono = FontFamily(fonts[8], fonts[9]),
        display = FontFamily(fonts[10], fonts[11]),
    )
}

internal fun interfaceFontIdentity(font: InterfaceFontResource): String =
    "kinetickk:${font.name}:${font.weight.weight}:${font.style}"

internal fun interfaceFontsFromBytes(
    read: suspend (FontResource) -> ByteArray,
    validate: (InterfaceFontResource, ByteArray) -> Unit,
    create: (InterfaceFontResource, ByteArray) -> Font,
): InterfaceFontLoader = InterfaceFontLoader { onFontLoaded ->
    // The fixed manifest bounds concurrency to ten reads; aliases share exactly the same bytes.
    val bytes = coroutineScope {
        InterfaceFontResources.all.map { it.resource }.distinct().map { resource ->
            async { resource to read(resource) }
        }.awaitAll().toMap()
    }
    // Compose's Blocking font resolver substitutes a system font when native decoding throws.
    // Validate every file before entering that cache, so a broken response is retryable.
    for (font in InterfaceFontResources.all.distinctBy { it.resource }) {
        yield()
        validate(font, bytes.getValue(font.resource))
    }
    InterfaceFontResources.all.map { font ->
        yield()
        create(font, bytes.getValue(font.resource)).also { onFontLoaded() }
    }
}

internal fun interface InterfaceFontLoader {
    suspend fun load(onFontLoaded: () -> Unit): List<Font>
}

@Composable
internal expect fun rememberPlatformInterfaceFontLoader(): InterfaceFontLoader
