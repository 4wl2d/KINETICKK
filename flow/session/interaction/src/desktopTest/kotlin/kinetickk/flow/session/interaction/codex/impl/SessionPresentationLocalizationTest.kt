// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.profile.api.*
import kinetickk.flow.session.interaction.codex.api.CodexRenderModel
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.flow.session.interaction.home.impl.DefaultHomeFeature
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SessionPresentationLocalizationTest {
    @Test
    fun languageSwitchUpdatesPinnedCodexDetailsAndSearchUsesVisibleLanguage() = runComposeUiTest {
        var languageValue by mutableStateOf(AppLanguage.Russian)
        val catalog = codexTestCatalog()
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides languageValue) {
                Box(Modifier.requiredSize(1000.dp, 700.dp)) {
                    CodexContent(catalog, CodexRenderModel(immutableSetOf(), CodexRunStacks(), catalog.items), codexTestProgress(), 1f) { }
                }
            }
        }
        onNodeWithTag("codex-tab-1").assertTextEquals("Каталог")
        onNodeWithTag("codex-category-3").performClick()
        onNodeWithTag("codex-search").performTextInput("крУг")
        onNodeWithTag("codex-slot-shape/ORB").performClick()
        onNodeWithTag("codex-detail-title").assertTextEquals("Круг")
        saveLocalizationCapture("codex-ru")
        onNodeWithTag("codex-search").performTextClearance()
        runOnIdle { languageValue = AppLanguage.English }
        onNodeWithTag("codex-tab-1").assertTextEquals("Catalog")
        onNodeWithTag("codex-slot-shape/ORB").assertIsSelected()
        onNodeWithTag("codex-detail-title").assertTextEquals("Circle")
        onNodeWithTag("codex-search").performTextInput("CiRcLe")
        onNodeWithTag("codex-slot-shape/ORB").assertIsDisplayed()
        saveLocalizationCapture("codex-en")
    }

    @Test
    fun catalogAndSynergyTabsKeepLazyEntryTypesAligned() = runComposeUiTest {
        var languageValue by mutableStateOf(AppLanguage.Russian)
        val catalog = codexTestCatalog()
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides languageValue) {
                Box(Modifier.requiredSize(1000.dp, 700.dp)) {
                    CodexContent(catalog, CodexRenderModel(immutableSetOf(), CodexRunStacks(), catalog.items), codexTestProgress(), 1f) { }
                }
            }
        }
        for (language in AppLanguage.entries) {
            runOnIdle { languageValue = language }
            repeat(3) {
                onNodeWithTag("codex-tab-1").performClick()
                onNodeWithTag("codex-category-3").performClick()
                onNodeWithTag("codex-slot-shape/ORB").performClick()
                onNodeWithTag("codex-detail-title").assertTextEquals("Circle".localizedContent(language))
                onNodeWithTag("codex-tab-2").performClick()
                onNodeWithTag("codex-slot-synergy/VECTOR_MANEUVER").performClick()
                onNodeWithTag("codex-detail-title").assertTextEquals(catalog.synergies.first().name.localizedContent(language))
            }
        }
    }

    @Test
    fun homeLanguageSwitchUpdatesAccessibleActionsAndCanvas() = runComposeUiTest {
        mainClock.autoAdvance = false
        var languageValue by mutableStateOf(AppLanguage.Russian)
        val feature = DefaultHomeFeature(LocalizationProfilePort(), codexTestCatalog(), LocalizationSilentAudio)
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides languageValue) {
                Box(Modifier.requiredSize(1000.dp, 700.dp)) {
                    feature.Content(inputEnabled = true) { }
                }
            }
        }
        mainClock.advanceTimeByFrame()
        onNodeWithTag("kinetickk.home.start").assertContentDescriptionEquals("Начать забег")
        onNodeWithTag("kinetickk.home.core.orb").assertContentDescriptionEquals("Выбрать ядро «Круг»")
        saveLocalizationCapture("home-ru")
        runOnIdle { languageValue = AppLanguage.English }
        mainClock.advanceTimeByFrame()
        onNodeWithTag("kinetickk.home.start").assertContentDescriptionEquals("Start run")
        onNodeWithTag("kinetickk.home.core.orb").assertContentDescriptionEquals("Select Circle core")
        saveLocalizationCapture("home-en")
    }
}

private class LocalizationProfilePort : ProfileReadPort {
    override val instanceId = LOCAL_PROFILE_INSTANCE_ID
    override fun query(query: ProfileQuery.GetPreferences) = PreferencesProjection(instanceId, ProfileRevision.ZERO, PlayerPreferences())
    override fun query(query: ProfileQuery.GetHomeProgress) = codexTestProgress()
    override fun query(query: ProfileQuery.GetCollection) = CollectionProjection(instanceId, ProfileRevision.ZERO, PlayerCollection())
}

private object LocalizationSilentAudio : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.saveLocalizationCapture(name: String) {
    val bitmap = onRoot().captureToImage()
    val pixels = bitmap.toPixelMap()
    val image = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) image.setRGB(x, y, pixels[x, y].toArgb())
    val folder = File("/tmp/kinetickk-localization-captures").apply { mkdirs() }
    ImageIO.write(image, "png", File(folder, "$name.png"))
}
