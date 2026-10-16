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

/** Local, uncommitted: the same app-level captures on the base and the redesign branch. */
@OptIn(ExperimentalTestApi::class)
class RedesignBeforeAfterShotsTest {
    @Test
    fun captureAppScreens() {
        val directory = System.getenv("KK_BA_DIR") ?: return
        enableKinetickkComposeRuntimeOptimizations()
        for ((width, height) in listOf(1440 to 810, 844 to 390, 390 to 844)) {
            for (language in listOf(AppLanguage.English, AppLanguage.Russian)) runDesktopComposeUiTest(width, height) {
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
                    val output = File(directory, "$name-${width}x$height-${language.code}.png")
                    output.parentFile.mkdirs()
                    Image.makeFromBitmap(onNodeWithTag("ba").captureToImage().asSkiaBitmap()).use { image ->
                        image.encodeToData()!!.use { output.writeBytes(it.bytes) }
                    }
                }
                fun key(key: Key) {
                    onRoot().performKeyInput { pressKey(key) }
                    mainClock.advanceTimeBy(100)
                }
                shot("home")
                key(Key.S); shot("settings"); key(Key.Escape)
                key(Key.L); shot("lab"); key(Key.Escape)
                key(Key.A); shot("armory"); key(Key.Escape)
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
