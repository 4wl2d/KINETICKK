// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.ball.gameplay.impl.DefaultGameplayFeature
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.impl.ProfilePersistenceCapability
import kinetickk.ball.profile.impl.ProfilePersistenceMutationResult
import kinetickk.ball.profile.impl.ProfilePersistenceReadResult
import kinetickk.ball.profile.impl.createProfileComponent
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test

/**
 * Local: the same app-level captures on the base and the redesign branch, written to `KK_BA_DIR` at three sizes
 * in both languages. Without it the run is a smoke of the same flow (1440 x 810, English, nothing written): the
 * composed app opens every screen by its key, starts a run and pauses it without failing.
 */
@OptIn(ExperimentalTestApi::class)
class RedesignBeforeAfterShotsTest {
    @Test
    fun captureAppScreens() {
        val directory = System.getenv("KK_BA_DIR")
        enableKinetickkComposeRuntimeOptimizations()
        val sizes = listOf(1440 to 810, 844 to 390, 390 to 844).let { if (directory != null) it else it.take(1) }
        val languages = listOf(AppLanguage.English, AppLanguage.Russian).let { if (directory != null) it else it.take(1) }
        for ((width, height) in sizes) {
            for (language in languages) runDesktopComposeUiTest(width, height) {
                mainClock.autoAdvance = false
                val catalog = createContentCatalog()
                val profile = createProfileComponent(BaPersistence(), catalog.profilePolicy())
                profile.accept(ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.SetLanguage(language)))
                val audio = BaSilentAudio()
                val gameplay = DefaultGameplayFeature(catalog.gameplayContent(), profile, profile, audio)
                val owner = AppCompositionOwner(
                    contentCatalog = catalog,
                    profileComponent = profile,
                    audioService = audio,
                    gameplayComponent = gameplay,
                )
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f)) {
                        Box(Modifier.requiredSize(width.dp, height.dp).testTag("ba")) { owner.Content() }
                    }
                }
                fun shot(name: String, advance: Long = 900) {
                    mainClock.advanceTimeBy(advance)
                    waitForIdle()
                    val capture = onNodeWithTag("ba").captureToImage()
                    if (directory == null) return
                    val output = File(directory, "$name-${width}x$height-${language.code}.png")
                    output.parentFile.mkdirs()
                    Image.makeFromBitmap(capture.asSkiaBitmap()).use { image ->
                        image.encodeToData()!!.use { output.writeBytes(it.bytes) }
                    }
                }
                fun key(key: Key) {
                    onRoot().performKeyInput { pressKey(key) }
                    mainClock.advanceTimeBy(100)
                }
                shot("home")
                key(Key.S); shot("settings"); key(Key.Escape)
                key(Key.W); shot("workshop")
                key(Key.L); shot("workshop-matrix")
                key(Key.A); shot("workshop-weapons"); key(Key.Escape)
                key(Key.B); shot("rebirth"); key(Key.Escape)
                key(Key.C); shot("codex"); key(Key.Escape); key(Key.Escape)
                shot("home-after-menus", 300)
                key(Key.Enter); shot("run", 3_000)
                key(Key.P); shot("pause")
            }
        }
    }
}

private class BaPersistence : ProfilePersistenceCapability {
    var payload: String? = null
    override fun readSnapshot() = ProfilePersistenceReadResult.Observed(payload)
    override fun writeSnapshot(payload: String): ProfilePersistenceMutationResult {
        this.payload = payload
        return ProfilePersistenceMutationResult.COMPLETED
    }
}

private class BaSilentAudio : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
