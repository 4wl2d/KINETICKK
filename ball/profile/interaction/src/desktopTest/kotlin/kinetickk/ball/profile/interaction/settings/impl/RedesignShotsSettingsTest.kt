// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.settings.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.profile.api.ColorVision
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.profile.api.KeyBindings
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.WindowMode
import kinetickk.ball.profile.api.WindowSizePreset
import kinetickk.ball.profile.interaction.settings.api.SettingsDisplayPort
import kinetickk.ball.profile.interaction.settings.api.SettingsLiveWindow
import kinetickk.ball.profile.interaction.settings.api.appliedInterfaceScalePercent
import kinetickk.ball.profile.interaction.settings.api.offeredInterfaceScales
import kinetickk.foundation.common.input.BindableKey
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalKkInterfaceScale
import kinetickk.foundation.design.LocalKkRolePalette
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

    /**
     * Display for the three hosts' support descriptors and Controls idle, capturing, swapped and
     * reset-armed, at every size in both languages; plus text 1.75 and a 130 % interface scale.
     */
    @Test
    fun renderDisplayAndControlsStates() {
        val output = File("build/redesign-shots/settings").apply { if (fullShots) mkdirs() }
        val allSizes = sizes + listOf(390 to 720, 720 to 360, 1000 to 700)
        val rebound = KeyBindings.DEFAULT.with(GameAction.DASH, BindableKey.F).with(GameAction.APEX, BindableKey.E)
        for ((width, height) in allSizes) for (language in AppLanguage.entries) for (scale in listOf(1.25f, 1.75f)) {
            val suffix = if (scale == 1.25f) "" else "-text175"
            val prefs = PlayerPreferences(language = language, textScale = scale)
            shot(output, width, height, language, "display", "display-desktop$suffix", prefs, display = SettingsTestDisplayPort(DESKTOP_SUPPORT))
            shot(output, width, height, language, "display", "display-android$suffix", prefs, display = SettingsTestDisplayPort(ANDROID_SUPPORT))
            shot(output, width, height, language, "display", "display-web$suffix", prefs, display = SettingsTestDisplayPort(WEB_SUPPORT))
            shot(output, width, height, language, "controls", "controls$suffix", prefs.copy(keyBindings = rebound))
            shot(output, width, height, language, "controls", "controls-capturing$suffix", prefs,
                pressTag = "kinetickk.settings.bind.dash.key")
            shot(output, width, height, language, "controls", "controls-swapped$suffix", prefs,
                pressTag = "kinetickk.settings.bind.dash.key", keys = listOf(Key.Z))
            shot(output, width, height, language, "controls", "controls-rejected$suffix", prefs,
                pressTag = "kinetickk.settings.bind.apex.key", keys = listOf(Key.Tab), settleMillis = 60)
            shot(output, width, height, language, "controls", "controls-resetarmed$suffix", prefs.copy(keyBindings = rebound),
                pressTag = "kinetickk.settings.resetbindings.action")
        }
        for (language in AppLanguage.entries) for ((width, height) in allSizes) {
            // The largest interface scale as the shell applies it: the whole screen at the scaled
            // density, clamped to the scales this window offers (115 % on a 390 dp phone).
            val applied = appliedInterfaceScalePercent(130, offeredInterfaceScales(width.toFloat(), height.toFloat(), touchPrimary = false))
            shot(output, width, height, language, "display", "display-scale130",
                PlayerPreferences(language = language, interfaceScalePercent = 130), display = SettingsTestDisplayPort(DESKTOP_SUPPORT),
                interfaceScale = applied / 100f)
            // Stored fullscreen while the system shows a resized window: outlined live mode, live size in mute.
            shot(output, width, height, language, "display", "display-live",
                PlayerPreferences(language = language, windowMode = WindowMode.WINDOWED, windowSize = WindowSizePreset.W1440X900, frameRateCap = 60,
                    vsync = false, showFps = true),
                display = SettingsTestDisplayPort(DESKTOP_SUPPORT, live = SettingsLiveWindow(WindowMode.FULLSCREEN, 1507, 911)))
            shot(output, width, height, language, "display", "display-borderless",
                PlayerPreferences(language = language, windowMode = WindowMode.BORDERLESS, frameRateCap = 30),
                display = SettingsTestDisplayPort(DESKTOP_SUPPORT))
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
        display: SettingsDisplayPort = SettingsDisplayPort.None,
        keys: List<Key> = emptyList(),
        settleMillis: Long = 1_100,
        interfaceScale: Float = 1f,
    ) {
        if (!fullShots && !smokeStates.add(state)) return
        runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat()), density = Density(1f)) {
            mainClock.autoAdvance = false
            val port = SettingsTestProfilePort(preferences)
            val feature = DefaultSettingsFeature(port, SettingsTestSilentAudio, display)
            setContent {
                CompositionLocalProvider(
                    LocalAppLanguage provides language,
                    LocalKkRolePalette provides preferences.colorVision.previewPalette(),
                    LocalDensity provides Density(interfaceScale),
                    LocalKkInterfaceScale provides interfaceScale,
                ) {
                    Box(Modifier.requiredSize((width / interfaceScale).dp, (height / interfaceScale).dp)) {
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
                // A control on a later page: turn the pages until it shows.
                var page = 1
                while (onAllNodesWithTag(pressTag).fetchSemanticsNodes().isEmpty() &&
                    onAllNodesWithTag("kinetickk.settings.page.$page").fetchSemanticsNodes().isNotEmpty()
                ) {
                    onNodeWithTag("kinetickk.settings.page.$page").performTouchInput { click() }
                    mainClock.advanceTimeBy(100)
                    page++
                }
                if (onAllNodesWithTag(pressTag).fetchSemanticsNodes().isEmpty()) return@runSkikoComposeUiTest
                onNodeWithTag(pressTag).performTouchInput { click() }
                mainClock.advanceTimeBy(300)
                if (thenPressTag != null) onNodeWithTag(thenPressTag).performTouchInput { click() }
            }
            keys.forEach { key ->
                onRoot().performKeyInput { pressKey(key) }
                mainClock.advanceTimeBy(16)
            }
            mainClock.advanceTimeBy(settleMillis)
            val image = onRoot().captureToImage()
            if (!fullShots) return@runSkikoComposeUiTest
            val name = "settings-$state-${width}x$height-${language.code}.png"
            Image.makeFromBitmap(image.asSkiaBitmap()).use { skia ->
                skia.encodeToData()!!.use { File(output, name).writeBytes(it.bytes) }
            }
        }
    }
}
