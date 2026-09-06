// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.interaction.GameplayInteractionOutput
import kinetickk.ball.gameplay.interaction.GameplayPresentation
import kinetickk.ball.profile.api.*
import kinetickk.ball.profile.interaction.armory.api.*
import kinetickk.ball.profile.interaction.lab.api.*
import kinetickk.ball.profile.interaction.rebirth.api.*
import kinetickk.ball.profile.interaction.settings.impl.DefaultSettingsFeature
import kinetickk.flow.session.api.*
import kinetickk.flow.session.interaction.audio.SessionAudioExecutor
import kinetickk.flow.session.interaction.codex.api.*
import kinetickk.flow.session.interaction.home.api.*
import kinetickk.flow.session.interaction.profile.api.ProfileUnavailableFeature
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.resource.audio.api.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SettingsLocalizationComposeTest {
    @Test fun selectionUpdatesTheEntireShellAndUsesTheAcceptedProfileValue() = runComposeUiTest {
        val profile = LanguageProfilePort(AppLanguage.Russian)
        val session = KeyboardSessionPort().apply { overlay = AppDestination.Settings }
        val reportedLanguages = mutableListOf<AppLanguage>()
        setContent { LocalizationShell(session, profile, 1000, 700) { reportedLanguages += it } }
        onNodeWithTag("language-probe").assertTextEquals("ru")
        onNodeWithTag("kinetickk.settings.language.ru").assertIsSelected()
        onNodeWithTag("kinetickk.settings.language.en").performClick().assertIsSelected()
        onNodeWithTag("language-probe").assertTextEquals("en")
        runOnIdle { assertEquals(AppLanguage.English, profile.preferences.language) }
        // Keyboard selection must be consumed by the control, without closing Settings.
        onNodeWithTag("kinetickk.settings.language.ru").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.RequestFocus,
        )
        onNodeWithTag("kinetickk.settings.language.ru").performKeyInput { pressKey(Key.Enter) }
        onNodeWithTag("kinetickk.settings.language.ru").assertIsSelected()
        onNodeWithTag("language-probe").assertTextEquals("ru")
        runOnIdle {
            assertTrue(session.pulses.isEmpty())
            assertEquals(listOf(AppLanguage.Russian, AppLanguage.English, AppLanguage.Russian), reportedLanguages.distinctUntilChangedForTest())
            profile.rejectChanges = true
        }
        onNodeWithTag("kinetickk.settings.language.en").performClick()
        onNodeWithTag("language-probe").assertTextEquals("ru")
        onNodeWithTag("kinetickk.settings.language.ru").assertIsSelected()
    }

    @Test fun persistedEnglishIsTheInitialLanguage() = runComposeUiTest {
        val profile = LanguageProfilePort(AppLanguage.English)
        setContent { LocalizationShell(remember { KeyboardSessionPort().apply { overlay = AppDestination.Settings } }, profile, 390, 720) }
        onNodeWithTag("language-probe").assertTextEquals("en")
        onNodeWithTag("kinetickk.settings.language.en").assertIsSelected()
    }

    @Test fun settingsScreenshotsInBothLanguagesAndViewportSizes() {
        for ((width, height, scale) in listOf(Triple(1000, 700, 1.25f), Triple(390, 720, 1.25f), Triple(390, 720, 1.75f))) {
            for (language in AppLanguage.entries) runComposeUiTest {
                val profile = LanguageProfilePort(language).apply {
                    preferences = preferences.copy(textScale = scale, damageNumberTierThreshold = if (scale > 1.25f) 100_000_000 else 50)
                }
                setContent { LocalizationShell(remember { KeyboardSessionPort().apply { overlay = AppDestination.Settings } }, profile, width, height) }
                waitForIdle()
                val bitmap = onRoot().captureToImage()
                val pixels = bitmap.toPixelMap()
                val image = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
                for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) image.setRGB(x, y, pixels[x, y].toArgb())
                val folder = File("/tmp/kinetickk-localization-captures").apply { mkdirs() }
                ImageIO.write(image, "png", File(folder, "settings-${language.code}-${width}x$height${if (scale > 1.25f) "-175" else ""}.png"))
            }
        }
    }
}

@Composable
private fun LocalizationShell(
    session: AppSessionPort, profile: LanguageProfilePort, width: Int, height: Int,
    onLanguageChanged: (AppLanguage) -> Unit = {},
) {
    Box(Modifier.requiredSize(width.dp, height.dp)) {
        AppSessionContent(
            sessionPort = session,
            audioExecutor = remember { SessionAudioExecutor(LanguageSilentAudio) },
            initialLanguage = profile.preferences.language,
            onLanguageChanged = onLanguageChanged,
            settingsFeature = remember(profile) { DefaultSettingsFeature(profile, LanguageSilentAudio) },
            homeFeature = object : HomeFeature {
                @Composable override fun Content(inputEnabled: Boolean, onOutput: (HomeOutput) -> Unit) {
                    BasicText(LocalAppLanguage.current.code, Modifier.testTag("language-probe"))
                }
            },
            gameplayPresentation = object : GameplayPresentation {
                override fun activePresentation() = null
                @Composable override fun Content(inputEnabled: Boolean, onOutput: (GameplayInteractionOutput) -> Unit) = Unit
            },
            labFeature = object : LabFeature {
                @Composable override fun Content(routeToken: Long, onOutput: (LabOutput) -> Unit) = Unit
            },
            armoryFeature = object : ArmoryFeature {
                @Composable override fun Content(activeRunWeapon: WeaponId?, onOutput: (ArmoryOutput) -> Unit) = Unit
            },
            rebirthFeature = object : RebirthFeature {
                override fun playAcceptedFeedback() = Unit
                @Composable override fun Content(routeToken: Long, eligible: Boolean, confirmationArmed: Boolean, onOutput: (RebirthOutput) -> Unit) = Unit
            },
            codexFeature = object : CodexFeature {
                @Composable override fun Content(runStacks: CodexRunStacks, onOutput: (CodexOutput) -> Unit) = Unit
            },
            profileUnavailableFeature = object : ProfileUnavailableFeature {
                @Composable override fun Content() = Unit
            },
        )
    }
}

private class LanguageProfilePort(language: AppLanguage) : ProfilePort {
    override val instanceId = LOCAL_PROFILE_INSTANCE_ID
    var preferences = PlayerPreferences(language = language)
    var rejectChanges = false
    private var revision = ProfileRevision.ZERO
    override fun accept(pulse: ProfilePulse.Business): ProfileAcceptance {
        if (rejectChanges) return ProfileAcceptance.Rejected(instanceId, revision, ProfileRejection.NoChange)
        val language = ((pulse as ProfilePulse.AdjustPreference).adjustment as ProfilePreferenceAdjustment.SetLanguage).language
        preferences = preferences.copy(language = language)
        revision = ProfileRevision(revision.value + 1)
        return ProfileAcceptance.Accepted(instanceId, revision)
    }
    override fun query(query: ProfileQuery.GetPreferences) = PreferencesProjection(instanceId, revision, preferences)
    override fun query(query: ProfileQuery.GetHomeProgress): HomeProgressProjection = error("Unused")
    override fun query(query: ProfileQuery.GetCollection): CollectionProjection = error("Unused")
    override fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection = error("Unused")
    override fun query(query: ProfileQuery.GetLabProgress): LabProgressProjection = error("Unused")
    override fun query(query: ProfileQuery.GetLoadout): LoadoutProjection = error("Unused")
    override fun query(query: ProfileQuery.GetRebirthProgress): RebirthProgressProjection = error("Unused")
    override fun query(query: ProfileQuery.GetPersistenceStatus): PersistenceStatusProjection = error("Unused")
}

private object LanguageSilentAudio : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}

private fun List<AppLanguage>.distinctUntilChangedForTest(): List<AppLanguage> =
    filterIndexed { index, language -> index == 0 || this[index - 1] != language }
