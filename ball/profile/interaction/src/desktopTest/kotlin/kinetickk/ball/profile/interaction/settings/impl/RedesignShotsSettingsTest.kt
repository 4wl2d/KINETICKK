// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.ColorVision
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.MatrixProgressProjection
import kinetickk.ball.profile.api.LoadoutProjection
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test

/**
 * Visual QA harness: renders every Settings tab and state to PNG.
 *
 * With `KK_SHOTS=1` every state renders at every size in both languages into build/redesign-shots/settings.
 * Otherwise the run is a smoke: each state renders once (at the first size and language it is requested in) with
 * its presses and nothing is written, so a state that fails to compose, lay out or draw still fails.
 */
@OptIn(ExperimentalTestApi::class)
class RedesignShotsSettingsTest {
    private val fullShots = System.getenv("KK_SHOTS") == "1"
    private val smokeStates = HashSet<String>()
    private val sizes = listOf(1440 to 810, 844 to 390, 390 to 844)

    @Test
    fun renderSettingsStates() {
        val output = File("build/redesign-shots/settings").apply { if (fullShots) mkdirs() }
        for ((width, height) in sizes) for (language in AppLanguage.entries) {
            shot(output, width, height, language, "game", "game", PlayerPreferences(language = language))
            shot(output, width, height, language, "sound", "sound", PlayerPreferences(language = language, masterVolume = 0.8f))
            shot(output, width, height, language, "graphics", "graphics", PlayerPreferences(language = language))
            shot(output, width, height, language, "graphics", "graphics-mono",
                PlayerPreferences(language = language, colorVision = ColorVision.MONO))
            shot(output, width, height, language, "graphics", "graphics-protan",
                PlayerPreferences(language = language, colorVision = ColorVision.PROTAN))
            shot(output, width, height, language, "graphics", "graphics-deutan",
                PlayerPreferences(language = language, colorVision = ColorVision.DEUTAN))
            shot(output, width, height, language, "graphics", "graphics-tritan",
                PlayerPreferences(language = language, colorVision = ColorVision.TRITAN))
            shot(output, width, height, language, "interface", "interface", PlayerPreferences(language = language))
            shot(output, width, height, language, "graphics", "graphics-info", PlayerPreferences(language = language),
                focusTag = "kinetickk.settings.colorvision.info")
        }
        for (language in AppLanguage.entries) {
            shot(output, 1440, 810, language, "graphics", "graphics-text175",
                PlayerPreferences(language = language, textScale = 1.75f))
            shot(output, 390, 844, language, "graphics", "graphics-text175",
                PlayerPreferences(language = language, textScale = 1.75f))
            shot(output, 844, 390, language, "graphics", "graphics-text175",
                PlayerPreferences(language = language, textScale = 1.75f))
            for ((width, height) in sizes + listOf(390 to 720, 720 to 360, 1000 to 700)) {
                shot(output, width, height, language, "graphics", "graphics-extremes",
                    PlayerPreferences(language = language, textScale = 1.75f, damageNumberTierThreshold = 100_000_000,
                        damageNumberFormat = kinetickk.ball.profile.api.DamageNumberFormat.FULL,
                        damageNumberSize = kinetickk.ball.profile.api.DamageNumberSize.HUGE,
                        particleDensity = kinetickk.ball.profile.api.ParticleDensity.HIGH))
            }
            shot(output, 1000, 700, language, "game", "game-1000", PlayerPreferences(language = language))
            shot(output, 1000, 700, language, "graphics", "graphics-1000", PlayerPreferences(language = language))
            for ((width, height) in sizes) {
                shot(output, width, height, language, "interface", "interface-text175",
                    PlayerPreferences(language = language, textScale = 1.75f, runStatisticsOnLeft = true))
                shot(output, width, height, language, "sound", "sound-muted",
                    PlayerPreferences(language = language, musicEnabled = false, masterVolume = 0.4f))
            }
        }
    }

    /** (!) explanations opened by a press (touch), including those near the Compose volume strip. */
    @Test
    fun renderInfoStates() {
        val output = File("build/redesign-shots/settings").apply { if (fullShots) mkdirs() }
        for ((width, height) in sizes) for (language in AppLanguage.entries) for (scale in listOf(1.25f, 1.75f)) {
            val suffix = if (scale == 1.25f) "" else "-text175"
            val prefs = PlayerPreferences(language = language, masterVolume = 0.8f, textScale = scale)
            for (row in listOf("colorvision", "screen_shake")) {
                shot(output, width, height, language, "graphics", "graphics-tapinfo-$row$suffix", prefs,
                    pressTag = "kinetickk.settings.$row.info")
            }
            // A tap on the open slip closes it and leaves the covered choice unchanged.
            shot(output, width, height, language, "graphics", "graphics-tapslip-colorvision$suffix", prefs,
                pressTag = "kinetickk.settings.colorvision.info", thenPressTag = "kinetickk.settings.colorvision.slip")
            for (row in listOf("sfx", "music", "master_volume")) {
                shot(output, width, height, language, "sound", "sound-tapinfo-$row$suffix", prefs,
                    pressTag = "kinetickk.settings.$row.info")
            }
        }
    }

    private fun shot(
        output: File,
        width: Int,
        height: Int,
        language: AppLanguage,
        tab: String,
        state: String,
        preferences: PlayerPreferences,
        focusTag: String? = null,
        pressTag: String? = null,
        thenPressTag: String? = null,
    ) {
        if (!fullShots && !smokeStates.add(state)) return
        runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat()), density = Density(1f)) {
            mainClock.autoAdvance = false
            val port = ShotProfilePort(preferences)
            val feature = DefaultSettingsFeature(port, ShotSilentAudio)
            setContent {
                CompositionLocalProvider(
                    LocalAppLanguage provides language,
                    LocalKkRolePalette provides preferences.colorVision.previewPalette(),
                ) {
                    Box(Modifier.requiredSize(width.dp, height.dp)) {
                        feature.Content(routeToken = 1L) { }
                    }
                }
            }
            mainClock.advanceTimeBy(100)
            if (tab != "game") {
                onNodeWithTag("kinetickk.settings.group.$tab").performClick()
                mainClock.advanceTimeBy(100)
            }
            if (focusTag != null) {
                onNodeWithTag(focusTag).performSemanticsAction(SemanticsActions.RequestFocus)
            }
            if (pressTag != null) {
                if (onAllNodesWithTag(pressTag).fetchSemanticsNodes().isEmpty()) return@runSkikoComposeUiTest
                onNodeWithTag(pressTag).performTouchInput { click() }
                mainClock.advanceTimeBy(300)
                if (thenPressTag != null) onNodeWithTag(thenPressTag).performTouchInput { click() }
            }
            mainClock.advanceTimeBy(1_100)
            val image = onRoot().captureToImage()
            if (!fullShots) return@runSkikoComposeUiTest
            val name = "settings-$state-${width}x$height-${language.code}.png"
            Image.makeFromBitmap(image.asSkiaBitmap()).use { skia ->
                skia.encodeToData()!!.use { File(output, name).writeBytes(it.bytes) }
            }
        }
    }
}

private class ShotProfilePort(initial: PlayerPreferences) : ProfilePort {
    override val instanceId = LOCAL_PROFILE_INSTANCE_ID
    var preferences = initial
    private var revision = ProfileRevision.ZERO

    override fun accept(pulse: ProfilePulse.Business): ProfileAcceptance {
        val adjustment = (pulse as ProfilePulse.AdjustPreference).adjustment
        preferences = when (adjustment) {
            is ProfilePreferenceAdjustment.SetColorVision -> preferences.copy(colorVision = adjustment.colorVision)
            is ProfilePreferenceAdjustment.SetLanguage -> preferences.copy(language = adjustment.language)
            else -> preferences
        }
        revision = ProfileRevision(revision.value + 1)
        return ProfileAcceptance.Accepted(instanceId, revision)
    }

    override fun query(query: ProfileQuery.GetPreferences) = PreferencesProjection(instanceId, revision, preferences)
    override fun query(query: ProfileQuery.GetHomeProgress): HomeProgressProjection = error("Unused")
    override fun query(query: ProfileQuery.GetCollection): CollectionProjection = error("Unused")
    override fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection = error("Unused")
    override fun query(query: ProfileQuery.GetMatrixProgress): MatrixProgressProjection = error("Unused")
    override fun query(query: ProfileQuery.GetHangar): kinetickk.ball.profile.api.HangarProjection = error("unused")
    override fun query(query: ProfileQuery.GetLoadout): LoadoutProjection = error("Unused")
    override fun query(query: ProfileQuery.GetRebirthProgress): RebirthProgressProjection = error("Unused")
    override fun query(query: ProfileQuery.GetPersistenceStatus): PersistenceStatusProjection = error("Unused")
}

private object ShotSilentAudio : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
