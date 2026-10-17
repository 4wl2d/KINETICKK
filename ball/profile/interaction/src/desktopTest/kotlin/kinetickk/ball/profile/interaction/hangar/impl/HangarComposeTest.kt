// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.hangar.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.profile.interaction.CatalogRebirthRelics
import kinetickk.ball.profile.interaction.CatalogWeapons
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.content.api.defaultCoreShapeDefinitions
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.HangarProjection
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.LoadoutProfileSnapshot
import kinetickk.ball.profile.api.LoadoutProjection
import kinetickk.ball.profile.api.MatrixProgressProjection
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.ball.profile.interaction.hangar.api.HangarOutput
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Hangar wired to a Profile port: pointer, keyboard and the Select button reach Profile as typed pulses. */
@OptIn(ExperimentalTestApi::class)
class HangarComposeTest {
    private fun tag(shape: CoreShape) = "profile-hangar-tile-$shape"

    private fun run(
        port: HangarTestPort,
        width: Float = 1440f,
        height: Float = 810f,
        language: AppLanguage = AppLanguage.English,
        outputs: MutableList<HangarOutput> = mutableListOf(),
        withWeapons: Boolean = false,
        test: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) = runSkikoComposeUiTest(Size(width, height), Density(1f)) {
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides language) {
                Box(Modifier.requiredSize(width.dp, height.dp)) {
                    val feature = if (withWeapons) {
                        DefaultHangarFeature(port, defaultCoreShapeDefinitions(), HangarSilentAudio, CatalogWeapons, CatalogRebirthRelics)
                    } else {
                        DefaultHangarFeature(port, defaultCoreShapeDefinitions(), HangarSilentAudio)
                    }
                    feature.Content(0L) { outputs += it }
                }
            }
        }
        waitForIdle()
        test()
    }

    @Test
    fun aSecondPressOnAnUnlockedTileSelectsItThroughTheProfile() {
        val port = HangarTestPort()
        run(port) {
            onNodeWithTag(tag(CoreShape.ORB)).assertIsSelected()
            onNodeWithTag(tag(CoreShape.PRISM)).performClick()
            waitForIdle()
            assertTrue(port.pulses.isEmpty(), "the first press only inspects")
            onNodeWithTag("profile-hangar-select").assertIsEnabled()
            onNodeWithTag(tag(CoreShape.PRISM)).performClick()
            waitForIdle()
            assertEquals(listOf<ProfilePulse.Business>(ProfilePulse.SelectCoreShape(CoreShape.PRISM)), port.pulses)
            onNodeWithTag(tag(CoreShape.PRISM)).assertIsSelected()
            onNodeWithTag("profile-hangar-select").assertIsNotEnabled()
            onNodeWithTag("profile-hangar-select").assert(SemanticsMatcher("names the selection") { node ->
                node.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }?.joinToString()?.startsWith("Selected") == true
            })
        }
    }

    @Test
    fun lockedCharactersShowTheirProgressAndCannotBeSelected() {
        val port = HangarTestPort()
        run(port) {
            onNodeWithTag(tag(CoreShape.STAR)).assert(stateContains("700/1500"))
            onNodeWithTag(tag(CoreShape.STAR)).performClick()
            waitForIdle()
            onNodeWithTag("profile-hangar-select").assertIsNotEnabled()
            onNodeWithTag("profile-hangar-select").assert(stateContains("Destroy 1500 enemies across runs: 700/1500"))
            onNodeWithTag(tag(CoreShape.STAR)).performClick()
            waitForIdle()
            assertTrue(port.pulses.isEmpty(), "a locked character is never sent to the Profile")
        }
    }

    @Test
    fun arrowKeysMoveTheInspectionAndEnterSelectsTheFocusedCharacter() {
        val port = HangarTestPort()
        run(port) {
            onNodeWithTag(tag(CoreShape.ORB)).requestFocus()
            waitForIdle()
            onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
            waitForIdle()
            onNode(isFocused()).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Square")))
            onNode(isFocused()).performKeyInput { pressKey(Key.Enter) }
            waitForIdle()
            assertEquals(listOf<ProfilePulse.Business>(ProfilePulse.SelectCoreShape(CoreShape.PRISM)), port.pulses)
            onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
            waitForIdle()
            onNode(isFocused()).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Tesseract")))
        }
    }

    @Test
    fun backLeavesAndEveryLayoutShowsTheWholeRoster() {
        val outputs = mutableListOf<HangarOutput>()
        run(HangarTestPort(), outputs = outputs) {
            onNodeWithTag("profile-hangar-back").performClick()
            waitForIdle()
            assertEquals(listOf<HangarOutput>(HangarOutput.Back), outputs)
        }
        listOf(844f to 390f, 390f to 844f).forEach { (width, height) ->
            AppLanguage.entries.forEach { language ->
                run(HangarTestPort(), width = width, height = height, language = language) {
                    CoreShape.entries.forEach { shape -> onNodeWithTag(tag(shape)).assertExists() }
                    onNodeWithTag("profile-hangar-select").assertExists()
                    onNodeWithTag("profile-hangar-detail").assertExists()
                }
            }
        }
    }

    @Test
    fun theSignatureRowShowsTheInspectedCharactersTwoWeaponsAndTheirHarmony() {
        run(HangarTestPort(heirlooms = listOf(RelicId.STORMHEART_CAPACITOR)), withWeapons = true) {
            // The Circle's signature weapons: Flux Wake (the starter) and Gyre Disc (120 Matter).
            onNodeWithTag("profile-hangar-signature-FLUX_WAKE").assert(stateContains("Starter"))
            onNodeWithTag("profile-hangar-signature-GYRE_DISC").assert(stateContains("120 Matter"))
            onNodeWithTag("profile-hangar-signature-ION_SWARM").assertDoesNotExist()
            // The Hexagon's: Ion Swarm (Charge, carried by the heirloom) and Hunter Salvo (Reborn 4).
            onNodeWithTag(tag(CoreShape.HEXAGON)).performClick()
            waitForIdle()
            onNodeWithTag("profile-hangar-signature-ION_SWARM").assert(stateContains("Harmonizes with Stormheart Capacitor"))
            onNodeWithTag("profile-hangar-signature-HUNTER_SALVO").assert(stateContains("Reborn 4"))
            onNodeWithTag("profile-hangar-signature-FLUX_WAKE").assertDoesNotExist()
        }
    }

    private fun stateContains(text: String) = SemanticsMatcher("state contains '$text'") { node ->
        node.config.getOrElseNullable(SemanticsProperties.StateDescription) { null }?.contains(text) == true
    }
}

/** An in-memory Profile port that applies character selections with the Hangar's unlock state. */
private class HangarTestPort(
    private val unlocked: Set<CoreShape> = setOf(CoreShape.ORB, CoreShape.PRISM, CoreShape.SPIRAL),
    private var selected: CoreShape = CoreShape.ORB,
    private val heirlooms: List<RelicId> = emptyList(),
) : ProfilePort {
    private var revision = ProfileRevision(1L)
    val pulses = mutableListOf<ProfilePulse.Business>()

    override val instanceId = LOCAL_PROFILE_INSTANCE_ID

    override fun accept(pulse: ProfilePulse.Business): ProfileAcceptance {
        pulses += pulse
        val shape = (pulse as ProfilePulse.SelectCoreShape).shape
        val reason = when {
            shape !in unlocked -> ProfileRejection.CoreShapeLocked
            shape == selected -> ProfileRejection.NoChange
            else -> null
        }
        if (reason != null) return ProfileAcceptance.Rejected(instanceId, revision, reason)
        selected = shape
        revision = ProfileRevision(revision.value + 1)
        return ProfileAcceptance.Accepted(instanceId, revision)
    }

    override fun query(query: ProfileQuery.GetHangar): HangarProjection = HangarProjection(
        instanceId, revision,
        defaultCoreShapeDefinitions().map { definition ->
            val target = definition.unlockTarget.toLong()
            kinetickk.ball.profile.api.HangarShapeProgress(definition.id, definition.id in unlocked,
                if (definition.id in unlocked) target else minOf(target, 700L), target)
        }.toImmutableList(),
        selected,
    )

    override fun query(query: ProfileQuery.GetLoadout): LoadoutProjection =
        LoadoutProjection(instanceId, revision, LoadoutProfileSnapshot(PlayerEconomy(1_284L), PlayerLoadout(coreShape = selected)))

    override fun query(query: ProfileQuery.GetPreferences): PreferencesProjection =
        PreferencesProjection(instanceId, revision, PlayerPreferences(textScale = 1.25f))

    override fun query(query: ProfileQuery.GetMatrixProgress): MatrixProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection = error("unused")
    override fun query(query: ProfileQuery.GetHomeProgress): HomeProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetCollection): CollectionProjection =
        CollectionProjection(instanceId, revision, PlayerCollection(heirloomRelicIds = heirlooms.toImmutableList()))
    override fun query(query: ProfileQuery.GetRebirthProgress): RebirthProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetPersistenceStatus): PersistenceStatusProjection = error("unused")
}

private object HangarSilentAudio : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
