// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.resource.audio.api.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ColorVisionPaletteComposeTest {
    @Test fun everyPreferenceMapsToItsOwnRolePalette() {
        val palettes = ColorVision.entries.map { it.rolePalette() }
        assertEquals(palettes.size, palettes.toSet().size)
        assertEquals(KkRolePalette.Default, ColorVision.DEFAULT.rolePalette())
        assertEquals(true, ColorVision.MONO.rolePalette().hatchThreats)
    }

    @Test fun persistedColorVisionThemesTheShellFromTheFirstFrame() = runComposeUiTest {
        val profile = PaletteProfilePort(ColorVision.PROTAN)
        setContent { PaletteShell(remember { KeyboardSessionPort().apply { overlay = null } }, profile) }
        onNodeWithTag("palette-probe").assertTextEquals(KkRolePalette.Protan.probeText())
    }

    @Test fun choosingColorVisionInSettingsRethemesTheWholeShell() = runComposeUiTest {
        val profile = PaletteProfilePort(ColorVision.DEFAULT)
        val session = KeyboardSessionPort().apply { overlay = AppDestination.Settings }
        setContent { PaletteShell(session, profile) }
        onNodeWithTag("palette-probe").assertTextEquals(KkRolePalette.Default.probeText())
        onNodeWithTag("kinetickk.settings.group.graphics").performClick()
        onNodeWithTag("kinetickk.settings.colorvision.mono").performClick()
        onNodeWithTag("palette-probe").assertTextEquals(KkRolePalette.Mono.probeText())
        runOnIdle { assertEquals(ColorVision.MONO, profile.preferences.colorVision) }
        onNodeWithTag("kinetickk.settings.colorvision.tritan").performClick()
        onNodeWithTag("palette-probe").assertTextEquals(KkRolePalette.Tritan.probeText())
    }
}

private fun KkRolePalette.probeText(): String = "${you.value}/${threat.value}/$hatchThreats"

@Composable
private fun PaletteShell(session: AppSessionPort, profile: PaletteProfilePort) {
    Box(Modifier.requiredSize(1000.dp, 700.dp)) {
        AppSessionContent(
            sessionPort = session,
            audioExecutor = remember { SessionAudioExecutor(PaletteSilentAudio) },
            profileReadPort = profile,
            settingsFeature = remember(profile) { DefaultSettingsFeature(profile, PaletteSilentAudio) },
            homeFeature = object : HomeFeature {
                @Composable override fun Content(inputEnabled: Boolean, onOutput: (HomeOutput) -> Unit) {
                    BasicText(LocalKkRolePalette.current.probeText(), Modifier.testTag("palette-probe"))
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

/** Accepts only Color vision changes, like the Profile owner, and publishes them through its preferences read. */
private class PaletteProfilePort(colorVision: ColorVision) : ProfilePort {
    override val instanceId = LOCAL_PROFILE_INSTANCE_ID
    var preferences = PlayerPreferences(language = AppLanguage.English, colorVision = colorVision)
    private var revision = ProfileRevision.ZERO
    override fun accept(pulse: ProfilePulse.Business): ProfileAcceptance {
        val adjustment = (pulse as ProfilePulse.AdjustPreference).adjustment
        val colorVision = (adjustment as? ProfilePreferenceAdjustment.SetColorVision)?.colorVision
            ?: return ProfileAcceptance.Rejected(instanceId, revision, ProfileRejection.NoChange)
        preferences = preferences.copy(colorVision = colorVision)
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

private object PaletteSilentAudio : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
