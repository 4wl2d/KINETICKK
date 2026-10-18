// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.armory.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.content.api.defaultCoreShapeDefinitions
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.HangarProjection
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.LoadoutProfileSnapshot
import kinetickk.ball.profile.api.LoadoutProjection
import kinetickk.ball.profile.api.MatrixProgressProjection
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerCollection
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
import kinetickk.ball.profile.interaction.CatalogRebirthRelics
import kinetickk.ball.profile.interaction.CatalogWeapons
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.resource.audio.api.AudioPreferences
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.api.ToneRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The 24-weapon Armory wired to a Profile port: the grid scrolls the inspected weapon into view,
 * a locked unlock names its reason instead of doing nothing, a Profile refusal is shown, and the
 * detail panel reads the selected character's signature and the heirloom harmony.
 */
@OptIn(ExperimentalTestApi::class)
class ArmoryComposeTest {
    private fun tile(id: WeaponId) = "profile-armory-equip-$id"

    private fun run(
        port: ArmoryTestPort,
        width: Float = 390f,
        height: Float = 844f,
        relics: List<RelicDefinition> = CatalogRebirthRelics,
        test: ComposeUiTest.() -> Unit,
    ) = runSkikoComposeUiTest(Size(width, height), Density(1f)) {
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.English) {
                Box(Modifier.requiredSize(width.dp, height.dp)) {
                    DefaultArmoryFeature(port, CatalogWeapons, WeaponMastery.entries.toImmutableList(), ArmorySilentAudio,
                        defaultCoreShapeDefinitions(), relics.toImmutableList()).Content(activeRunWeapon = null) {}
                }
            }
        }
        waitForIdle()
        test()
    }

    @Test
    fun theStarterOnALaterPageIsScrolledIntoViewWhenTheArmoryOpens() {
        val last = CatalogWeapons.last().id
        run(ArmoryTestPort(selected = last, unlocked = setOf(WeaponId.FLUX_WAKE, last))) {
            onNodeWithTag(tile(last)).assertIsSelected().assertIsDisplayed()
            onNodeWithTag(tile(WeaponId.FLUX_WAKE)).assertIsNotDisplayed()
        }
    }

    @Test
    fun keyboardFocusOnAFarTileInspectsItAndBringsItIntoView() {
        val far = CatalogWeapons[CatalogWeapons.lastIndex - 1].id
        run(ArmoryTestPort()) {
            onNodeWithTag(tile(far)).assertIsNotDisplayed()
            onNodeWithTag(tile(far)).requestFocus()
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            onNodeWithTag(tile(far)).assertIsSelected().assertIsDisplayed()
        }
    }

    @Test
    fun aLockedUnlockNamesItsReasonAndNeverReachesTheProfile() {
        val port = ArmoryTestPort(matter = 10L, rank = 0)
        run(port, width = 1440f, height = 810f) {
            // Glacier Bell is gated at Reborn 1: its tile names the rank.
            onNodeWithTag(tile(WeaponId.GLACIER_BELL)).assert(stateContains("Reborn 1"))
            onNodeWithTag(tile(WeaponId.GLACIER_BELL)).performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("profile-armory-action").assert(stateContains("Reborn 1")).performClick()
            waitForIdle()
            onNodeWithTag("profile-armory-notice").assert(descriptionContains("Locked until Rebirth rank 1"))
            // Gyre Disc costs 120 Matter: 110 more are needed.
            onNodeWithTag(tile(WeaponId.GYRE_DISC)).performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("profile-armory-notice").assertDoesNotExist()
            onNodeWithTag("profile-armory-action").performClick()
            waitForIdle()
            onNodeWithTag("profile-armory-notice").assert(descriptionContains("Not enough Matter: 110 more needed"))
            assertTrue(port.pulses.isEmpty(), "a locked unlock is never sent: ${port.pulses}")
        }
    }

    @Test
    fun aProfileRefusalOfAnAffordableUnlockIsShown() {
        // The screen believes 500 Matter is enough; the Profile (moved on) refuses for want of Matter.
        val port = ArmoryTestPort(matter = 500L, refuse = ProfileRejection.InsufficientMatter)
        run(port, width = 1440f, height = 810f) {
            onNodeWithTag(tile(WeaponId.GYRE_DISC)).performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("profile-armory-action").performClick()
            waitForIdle()
            assertEquals(listOf<ProfilePulse.Business>(ProfilePulse.PurchaseOrEquipWeapon(WeaponId.GYRE_DISC)), port.pulses)
            onNodeWithTag("profile-armory-notice").assertExists()
        }
    }

    @Test
    fun theDetailPanelReadsTheSignatureCharacterAndTheHeirloomHarmony() {
        // Ion Swarm: Hexagon's signature, key Charge, carried by two heirloom candidates (Stormheart Capacitor, Aegis Inversion).
        val port = ArmoryTestPort(character = CoreShape.HEXAGON,
            heirlooms = listOf(RelicId.STORMHEART_CAPACITOR, RelicId.FURNACE_COVENANT, RelicId.AEGIS_INVERSION))
        val aegis = RelicDefinition(RelicId.AEGIS_INVERSION, "Aegis Inversion", RelicAspect.ION, immutableListOf(RelicKey.CHARGE, RelicKey.LIFE),
            immutableListOf(), "Aegis Inversion upside", "Aegis Inversion liability", immutableListOf())
        run(port, width = 1440f, height = 810f, relics = CatalogRebirthRelics + aegis) {
            onNodeWithTag(tile(WeaponId.ION_SWARM)).assert(stateContains("Signature of your character"))
            onNodeWithTag(tile(WeaponId.FLUX_WAKE)).assert(SemanticsMatcher("no signature mark") { node ->
                node.config.getOrNull(SemanticsProperties.StateDescription)?.contains("Signature") != true
            })
            onNodeWithTag(tile(WeaponId.ION_SWARM)).performClick()
            waitForIdle()
            val detail = onNodeWithTag("profile-armory-detail")
            detail.assert(descendantDescriptionContains("Signature of Hexagon"))
            detail.assert(descendantDescriptionContains("Active with your character"))
            detail.assert(descendantDescriptionContains("Stormheart Capacitor, Aegis Inversion"))
            detail.assert(descendantDescriptionContains("Charge"))
            // Only one heirloom is chosen per run: two carriers still grant the weapon its 20% once.
            detail.assert(descendantDescriptionContains("Choosing one adds 20% weapon power"))
            detail.assert(SemanticsMatcher("no per-Relic weapon power") { node ->
                fun visit(current: androidx.compose.ui.semantics.SemanticsNode): Boolean =
                    current.config.getOrNull(SemanticsProperties.ContentDescription)
                        ?.any { text -> text.split('.', ';').any { "20%" in it && it.contains("each", ignoreCase = true) } } == true ||
                        current.children.any(::visit)
                !visit(node)
            })
            detail.assert(descendantDescriptionContains("Each point in Arsenal or Chain adds 5% weapon power"))
            // Another character's weapon names who it belongs to and how to get the bonus.
            onNodeWithTag(tile(WeaponId.MORNINGSTAR)).performClick()
            waitForIdle()
            onNodeWithTag("profile-armory-detail").assert(descendantDescriptionContains("Choose Square in the Hangar"))
            onNodeWithTag("profile-armory-detail").assert(descendantDescriptionContains("No heirloom candidate carries it"))
        }
    }

    private fun stateContains(text: String) = SemanticsMatcher("state contains '$text'") { node ->
        node.config.getOrNull(SemanticsProperties.StateDescription)?.contains(text) == true
    }

    private fun descriptionContains(text: String) = SemanticsMatcher("description contains '$text'") { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { text in it } == true
    }

    private fun descendantDescriptionContains(text: String): SemanticsMatcher = SemanticsMatcher("a descendant describes '$text'") { node ->
        fun visit(current: androidx.compose.ui.semantics.SemanticsNode): Boolean =
            current.config.getOrNull(SemanticsProperties.ContentDescription)?.any { text in it } == true || current.children.any(::visit)
        visit(node)
    }
}

/** An in-memory Profile port holding one loadout; [refuse] makes it reject every purchase with that reason. */
private class ArmoryTestPort(
    private val matter: Long = 10_000L,
    private val selected: WeaponId = WeaponId.FLUX_WAKE,
    private val unlocked: Set<WeaponId> = setOf(WeaponId.FLUX_WAKE),
    private val rank: Int = 0,
    private val character: CoreShape = CoreShape.ORB,
    private val heirlooms: List<RelicId> = emptyList(),
    private val refuse: ProfileRejection? = null,
) : ProfilePort {
    private val revision = ProfileRevision(1L)
    val pulses = mutableListOf<ProfilePulse.Business>()

    override val instanceId = LOCAL_PROFILE_INSTANCE_ID

    override fun accept(pulse: ProfilePulse.Business): ProfileAcceptance {
        pulses += pulse
        return ProfileAcceptance.Rejected(instanceId, revision, refuse ?: ProfileRejection.NoChange)
    }

    override fun query(query: ProfileQuery.GetLoadout): LoadoutProjection = LoadoutProjection(instanceId, revision,
        LoadoutProfileSnapshot(PlayerEconomy(matter), PlayerLoadout(character, selected, unlocked.toImmutableSet()), rebirthRank = rank))

    override fun query(query: ProfileQuery.GetCollection): CollectionProjection =
        CollectionProjection(instanceId, revision, PlayerCollection(heirloomRelicIds = heirlooms.toImmutableList()))

    override fun query(query: ProfileQuery.GetPreferences): PreferencesProjection =
        PreferencesProjection(instanceId, revision, PlayerPreferences(textScale = 1.25f))

    override fun query(query: ProfileQuery.GetHangar): HangarProjection = error("unused")
    override fun query(query: ProfileQuery.GetMatrixProgress): MatrixProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection = error("unused")
    override fun query(query: ProfileQuery.GetHomeProgress): HomeProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetRebirthProgress): RebirthProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetPersistenceStatus): PersistenceStatusProjection = error("unused")
}

private object ArmorySilentAudio : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}
