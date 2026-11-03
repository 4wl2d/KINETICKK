// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.characterization

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.gameplay.api.RunMode
import kinetickk.flow.session.interaction.ShotCatalog
import kinetickk.flow.session.interaction.ShotCodexModel
import kinetickk.flow.session.interaction.ShotProfile
import kinetickk.flow.session.interaction.ShotProgress
import kinetickk.flow.session.interaction.codex.impl.CodexContent
import kinetickk.flow.session.interaction.home.impl.DefaultHomeFeature
import kinetickk.flow.session.interaction.profile.impl.DefaultProfileUnavailableFeature
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.test.Test

/**
 * Characterizes rendered session screens through the session shot harness's capture path: Home with its start and
 * Workshop rows focused, the Codex (default and with a run's build) and the Profile-unavailable screen, at 1440×810 in
 * English and Russian on a fixed clock. Each scene renders in two fresh compositions and the SHA-256 of the second
 * capture is printed.
 */
@OptIn(ExperimentalTestApi::class)
class SessionPixelCharacterizationTest {
    @Test
    fun homeScreensHashStably() {
        for (target in listOf("start", "workshop")) for ((language, code) in SESSION_LANGUAGES) {
            fun render(): ImageBitmap {
                val feature = DefaultHomeFeature(ShotProfile(), ShotCatalog, SessionPixelSilentAudio, quitAvailable = false)
                return captureSessionScene(language, interact = {
                    mainClock.advanceTimeBy(2_000)
                    onNodeWithTag("kinetickk.home.$target").performSemanticsAction(SemanticsActions.RequestFocus)
                    mainClock.advanceTimeBy(900)
                }) {
                    feature.Content(inputEnabled = true, runMode = RunMode.STANDARD) { }
                }
            }
            render()
            printSessionPixelHash("home-$target", code, render())
        }
    }

    @Test
    fun codexAndProfileUnavailableScreensHashStably() {
        val scenes = listOf<Triple<String, Boolean, ComposeUiTest.() -> Unit>>(
            Triple("codex-default", false) { },
            Triple("codex-build", true) { onNodeWithTag("codex-tab-0").performSemanticsAction(SemanticsActions.OnClick) },
        )
        for ((name, run, interact) in scenes) for ((language, code) in SESSION_LANGUAGES) {
            fun render(): ImageBitmap = captureSessionScene(language, interact = {
                mainClock.advanceTimeBy(200)
                interact()
                mainClock.advanceTimeBy(600)
            }) {
                CompositionLocalProvider(LocalKkRolePalette provides KkRolePalette.Default) {
                    CodexContent(ShotCatalog, ShotCodexModel(run), ShotProgress(lifetimeMatter = 9_640L), 1.25f) { }
                }
            }
            render()
            printSessionPixelHash(name, code, render())
        }
        for ((language, code) in SESSION_LANGUAGES) {
            fun render(): ImageBitmap = captureSessionScene(language, interact = {}) { DefaultProfileUnavailableFeature().Content() }
            render()
            printSessionPixelHash("profile-unavailable", code, render())
        }
    }
}

private const val SESSION_WIDTH = 1440
private const val SESSION_HEIGHT = 810

private val SESSION_LANGUAGES = listOf(AppLanguage.English to "en", AppLanguage.Russian to "ru")

/** The session shot harness's capture: a fresh desktop composition on a manual clock, then [interact], then the root. */
@OptIn(ExperimentalTestApi::class)
private fun captureSessionScene(language: AppLanguage, interact: ComposeUiTest.() -> Unit, content: @Composable () -> Unit): ImageBitmap {
    var image: ImageBitmap? = null
    runDesktopComposeUiTest(SESSION_WIDTH, SESSION_HEIGHT) {
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f), LocalAppLanguage provides language) {
                Box(Modifier.requiredSize(SESSION_WIDTH.dp, SESSION_HEIGHT.dp)) { content() }
            }
        }
        mainClock.advanceTimeByFrame()
        interact()
        mainClock.advanceTimeByFrame()
        image = onRoot().captureToImage()
    }
    return checkNotNull(image)
}

private fun printSessionPixelHash(scene: String, language: String, bitmap: ImageBitmap) {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.readPixels(pixels)
    val bytes = ByteBuffer.allocate(pixels.size * 4)
    bytes.asIntBuffer().put(pixels)
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes.array()).joinToString("") { "%02x".format(it) }
    println("scene=$scene lang=$language sha256=$digest")
}

private object SessionPixelSilentAudio : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
