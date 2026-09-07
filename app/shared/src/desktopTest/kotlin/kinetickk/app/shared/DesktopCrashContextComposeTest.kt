// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.ball.gameplay.impl.DefaultGameplayFeature
import kinetickk.ball.profile.api.PreferenceAdjustmentDirection
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.impl.ProfilePersistenceCapability
import kinetickk.ball.profile.impl.ProfilePersistenceMutationResult
import kinetickk.ball.profile.impl.ProfilePersistenceReadResult
import kinetickk.ball.profile.impl.createProfileComponent
import kinetickk.foundation.design.LocalCrashDiagnostics
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DesktopCrashContextComposeTest {
    @Test fun realGameplayAtDoubleSpeedPublishesSeedRevisionInputAndEntities() {
        val directory = Files.createTempDirectory("kinetickk-compose-crash-test-")
        val reporter = DesktopCrashReporter(directory)
        try {
            runComposeUiTest {
                mainClock.autoAdvance = false
                val catalog = createContentCatalog()
                val profile = createProfileComponent(object : ProfilePersistenceCapability {
                    override fun readSnapshot() = ProfilePersistenceReadResult.Observed(null)
                    override fun writeSnapshot(payload: String) = ProfilePersistenceMutationResult.COMPLETED
                }, catalog.profilePolicy())
                repeat(40) {
                    if (profile.query(ProfileQuery.GetPreferences).preferences.simulationSpeed < 2f) {
                        profile.accept(ProfilePulse.AdjustPreference(
                            ProfilePreferenceAdjustment.StepSimulationSpeed(PreferenceAdjustmentDirection.INCREASE),
                        ))
                    }
                }
                assertEquals(2f, profile.query(ProfileQuery.GetPreferences).preferences.simulationSpeed)
                val audio = object : AudioService {
                    override fun updatePreferences(preferences: AudioPreferences) = Unit
                    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
                    override fun ensureUnlocked() = Unit
                    override fun close() = Unit
                }
                val gameplay = DefaultGameplayFeature(catalog.gameplayContent(), profile, profile, audio, reporter)
                val owner = AppCompositionOwner(contentCatalog = catalog, profileComponent = profile,
                    gameplayComponent = gameplay, audioService = audio, diagnostics = reporter)
                try {
                    setContent {
                        CompositionLocalProvider(LocalCrashDiagnostics provides reporter) {
                            Box(Modifier.requiredSize(1000.dp, 700.dp)) { owner.Content() }
                        }
                    }
                    repeat(3) { mainClock.advanceTimeByFrame() }
                    waitForIdle()
                    onRoot().performKeyInput { pressKey(Key.Enter) }
                    repeat(12) { mainClock.advanceTimeByFrame() }
                    waitForIdle()
                    onRoot().performKeyInput { pressKey(Key.Spacebar) }
                    repeat(3) { mainClock.advanceTimeByFrame() }
                    waitForIdle()
                    val report = reporter.capture(IllegalStateException("deliberate test after real 2x frames"),
                        "integration-test", Thread.currentThread())
                    val context = Files.readString(report.parent.resolve("context.txt"))
                    assertContains(context, "seed=731991")
                    assertContains(context, "speed=2.0")
                    assertContains(context, "revision=GameplayRevision")
                    assertContains(context, "FrameElapsed(realDeltaSeconds=")
                    assertContains(context, "enemies(")
                    assertContains(Files.readString(report.parent.resolve("events.txt")), "DashRequested")
                } finally { owner.close() }
            }
        } finally {
            reporter.close()
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
