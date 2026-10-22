// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.workshop.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.UpgradePolicy
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.content.api.defaultCoreShapeDefinitions
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.HangarProjection
import kinetickk.ball.profile.api.HangarShapeProgress
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.LoadoutProfileSnapshot
import kinetickk.ball.profile.api.LoadoutProjection
import kinetickk.ball.profile.api.MatrixProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.MatrixProgressProjection
import kinetickk.ball.profile.api.PermanentUpgrades
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
import kinetickk.ball.profile.interaction.CatalogMatrixNodes
import kinetickk.ball.profile.interaction.CatalogWeapons
import kinetickk.ball.profile.interaction.workshop.api.WorkshopOutput
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTab
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTabRequest
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
 * The Workshop wired to a Profile port: the tab survives closing and reopening, a shell request
 * applies once per serial, digits and arrows move between tabs and segments, Back leaves from every
 * tab, and the shown tab is announced politely.
 */
@OptIn(ExperimentalTestApi::class)
class WorkshopComposeTest {
    private class Opening(val routeToken: Long, val request: WorkshopTabRequest?)

    private fun ComposeUiTest.workshop(
        port: WorkshopTestPort,
        feature: DefaultWorkshopFeature,
        opening: () -> Opening,
        outputs: MutableList<WorkshopOutput> = mutableListOf(),
        language: AppLanguage = AppLanguage.English,
    ) {
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides language) {
                Box(Modifier.requiredSize(1440.dp, 810.dp)) {
                    val shown = opening()
                    androidx.compose.runtime.key(shown.routeToken) {
                        feature.Content(shown.routeToken, shown.request, characterSelectionAvailable = true, activeRunWeapon = null) {
                            outputs += it
                        }
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun feature(port: WorkshopTestPort) = DefaultWorkshopFeature(port, CatalogMatrixNodes, CatalogWeapons,
        WeaponMastery.entries.toImmutableList(), defaultCoreShapeDefinitions(), WorkshopSilentAudio)

    private fun tab(tab: WorkshopTab) = tab.tag()

    private fun description(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(text))

    @Test
    fun theTabShownLastSurvivesReopeningAndOnlyANewSerialReplacesIt() = runSkikoComposeUiTest(Size(1440f, 810f), Density(1f)) {
        val port = WorkshopTestPort()
        val feature = feature(port)
        var openingValue by mutableStateOf(Opening(1L, null))
        workshop(port, feature, { openingValue })
        // A first opening shows Characters.
        onNodeWithTag(tab(WorkshopTab.CHARACTERS)).assertIsSelected()
        onNodeWithTag("profile-hangar-roster").assertExists()
        onNodeWithTag(tab(WorkshopTab.WEAPONS)).performClick()
        waitForIdle()
        onNodeWithTag(tab(WorkshopTab.WEAPONS)).assertIsSelected()
        onNodeWithTag("profile-armory-scroll").assertExists()
        // Reopened without a request (W): the Weapons tab again.
        openingValue = Opening(2L, null)
        waitForIdle()
        onNodeWithTag(tab(WorkshopTab.WEAPONS)).assertIsSelected()
        // A request (L) shows its tab.
        openingValue = Opening(3L, WorkshopTabRequest(WorkshopTab.MATRIX, 1))
        waitForIdle()
        onNodeWithTag(tab(WorkshopTab.MATRIX)).assertIsSelected()
        onNodeWithTag("profile-matrix-scroll").assertExists()
        onNodeWithTag(tab(WorkshopTab.CHARACTERS)).performClick()
        waitForIdle()
        // The same request seen at a later opening was applied already: the tab shown last stays.
        openingValue = Opening(4L, WorkshopTabRequest(WorkshopTab.MATRIX, 1))
        waitForIdle()
        onNodeWithTag(tab(WorkshopTab.CHARACTERS)).assertIsSelected()
        onNodeWithTag(tab(WorkshopTab.MATRIX)).assertIsNotSelected()
        // A new serial applies even while the Workshop stays open.
        openingValue = Opening(4L, WorkshopTabRequest(WorkshopTab.WEAPONS, 2))
        waitForIdle()
        onNodeWithTag(tab(WorkshopTab.WEAPONS)).assertIsSelected()
        // Tabs and segments never reach the Profile.
        assertTrue(port.pulses.isEmpty(), "${port.pulses}")
    }

    @Test
    fun digitsPickATabAndMoveFocusToItsInspectedTile() = runSkikoComposeUiTest(Size(1440f, 810f), Density(1f)) {
        val port = WorkshopTestPort()
        workshop(port, feature(port), { Opening(1L, null) })
        onRoot().performKeyInput { pressKey(Key.Two) }
        waitForIdle()
        onNodeWithTag(tab(WorkshopTab.WEAPONS)).assertIsSelected()
        onNodeWithTag("profile-armory-equip-${WeaponId.FLUX_WAKE}").assertIsFocused()
        onRoot().performKeyInput { pressKey(Key.NumPad3) }
        waitForIdle()
        onNodeWithTag(tab(WorkshopTab.MATRIX)).assertIsSelected()
        onNodeWithTag("profile-matrix-node-${CatalogMatrixNodes.first().id}").assertIsFocused()
        // Arrows act at once on the focused grid.
        onRoot().performKeyInput { pressKey(Key.One) }
        waitForIdle()
        onNodeWithTag("profile-hangar-tile-${CoreShape.ORB}").assertIsFocused()
        onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        onNodeWithTag("profile-hangar-tile-${CoreShape.PRISM}").assertIsFocused()
    }

    @Test
    fun leftAndRightOnAFocusedTabWrapAndKeepFocusOnTheStrip() = runSkikoComposeUiTest(Size(1440f, 810f), Density(1f)) {
        val port = WorkshopTestPort()
        workshop(port, feature(port), { Opening(1L, null) })
        onNodeWithTag(tab(WorkshopTab.CHARACTERS)).requestFocus()
        waitForIdle()
        onNodeWithTag(tab(WorkshopTab.CHARACTERS)).performKeyInput { pressKey(Key.DirectionLeft) }
        waitForIdle()
        onNodeWithTag(tab(WorkshopTab.MATRIX)).assertIsSelected().assertIsFocused()
        onNodeWithTag(tab(WorkshopTab.MATRIX)).performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        onNodeWithTag(tab(WorkshopTab.CHARACTERS)).assertIsSelected().assertIsFocused()
        // Every tab is a selectable Tab node; the pane names the Workshop.
        WorkshopTab.entries.forEach { entry ->
            onNodeWithTag(tab(entry)).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        }
        onNodeWithTag(WORKSHOP_TAG).assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Workshop"))
    }

    @Test
    fun segmentsSwitchByPressAndByArrowsAndKeepTheirChoicePerTab() = runSkikoComposeUiTest(Size(1440f, 810f), Density(1f)) {
        val port = WorkshopTestPort()
        workshop(port, feature(port), { Opening(1L, WorkshopTabRequest(WorkshopTab.WEAPONS, 1)) })
        val upgrade = WorkshopSegment.UPGRADE.tag()
        val mastery = WorkshopSegment.MASTERY.tag()
        val details = WorkshopSegment.DETAILS.tag()
        onNodeWithTag(upgrade).assertIsSelected().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        onNodeWithTag("profile-armory-mastery-info").assertDoesNotExist()
        onNodeWithTag(mastery).performClick()
        waitForIdle()
        onNodeWithTag(mastery).assertIsSelected()
        onNodeWithTag("profile-armory-mastery-info").assertExists()
        onNodeWithTag(mastery).requestFocus()
        onNodeWithTag(mastery).performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        onNodeWithTag(details).assertIsSelected().assertIsFocused()
        onNodeWithTag(details).performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        onNodeWithTag(upgrade).assertIsSelected()
        onNodeWithTag(details).performClick()
        waitForIdle()
        // The Characters tab keeps its own segment (Upgrade); the Weapons tab keeps Details for this opening.
        onNodeWithTag(tab(WorkshopTab.CHARACTERS)).performClick()
        waitForIdle()
        onNodeWithTag(upgrade).assertIsSelected()
        onNodeWithTag(WorkshopSegment.TRAITS.tag()).assertIsNotSelected()
        onNodeWithTag(tab(WorkshopTab.WEAPONS)).performClick()
        waitForIdle()
        onNodeWithTag(details).assertIsSelected()
        assertTrue(port.pulses.isEmpty(), "${port.pulses}")
    }

    @Test
    fun backLeavesFromEveryTabAndTheShownTabIsAnnounced() = runSkikoComposeUiTest(Size(1440f, 810f), Density(1f)) {
        val port = WorkshopTestPort()
        val outputs = mutableListOf<WorkshopOutput>()
        workshop(port, feature(port), { Opening(1L, null) }, outputs)
        // Announcements clear after four seconds of presentation time: the clock moves only on request.
        mainClock.autoAdvance = false
        for ((index, entry) in WorkshopTab.entries.withIndex()) {
            onNodeWithTag(tab(entry)).performClick()
            mainClock.advanceTimeBy(64)
            onNodeWithTag("$WORKSHOP_TAG-back").performClick()
            mainClock.advanceTimeBy(64)
            assertEquals(List<WorkshopOutput>(index + 1) { WorkshopOutput.Back }, outputs, "$entry")
        }
        // The last tab switch was announced: the tab, then what is owned of it.
        onNodeWithTag(tab(WorkshopTab.WEAPONS)).performClick()
        mainClock.advanceTimeBy(64)
        onNodeWithTag(WORKSHOP_ANNOUNCE_TAG).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription,
            listOf("Weapons, 2 of ${CatalogWeapons.size}")))
        onNodeWithTag(tab(WorkshopTab.CHARACTERS)).performClick()
        mainClock.advanceTimeBy(64)
        onNodeWithTag(WORKSHOP_ANNOUNCE_TAG).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription,
            listOf("Characters, 3 of 12")))
    }

    @Test
    fun anUnlockIsAnnouncedAndTheMatterChipFollowsThePurchase() = runSkikoComposeUiTest(Size(1440f, 810f), Density(1f)) {
        val port = WorkshopTestPort(matter = 1_000L)
        workshop(port, feature(port), { Opening(1L, WorkshopTabRequest(WorkshopTab.WEAPONS, 1)) })
        mainClock.autoAdvance = false
        val gyre = "profile-armory-equip-${WeaponId.GYRE_DISC}"
        onNodeWithTag(gyre).performClick()
        mainClock.advanceTimeBy(64)
        onNodeWithTag("profile-armory-action").performClick()
        mainClock.advanceTimeBy(64)
        assertEquals(listOf<ProfilePulse.Business>(ProfilePulse.PurchaseOrEquipWeapon(WeaponId.GYRE_DISC)), port.pulses)
        onNodeWithTag(WORKSHOP_ANNOUNCE_TAG).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription,
            listOf("Gyre Disc unlocked and set as starter")))
        onNodeWithTag("$WORKSHOP_TAG-matter").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription,
            listOf("880 Matter")))
    }

    @Test
    fun aRefitLevelIsBoughtThroughTheProfileAndARefusalIsNamed() = runSkikoComposeUiTest(Size(1440f, 810f), Density(1f)) {
        val port = WorkshopTestPort()
        workshop(port, feature(port), { Opening(1L, null) })
        mainClock.autoAdvance = false
        // Circle (selected and inspected) shows its Refit block on the default Upgrade segment.
        onNodeWithTag(WORKSHOP_UPGRADE_PIPS_TAG).assert(description("Lvl 0 of 10"))
        // Its third line is the character's signature parameter (economy §7), between the lines and the action.
        onNodeWithTag(WORKSHOP_UPGRADE_SIGNATURE_TAG).assert(description("Orbit charges on a 2% shorter arc per level"))
        assertTrue(onNodeWithTag(WORKSHOP_UPGRADE_SIGNATURE_TAG).fetchSemanticsNode().boundsInRoot.bottom <=
            onNodeWithTag(WORKSHOP_UPGRADE_BUY_TAG).fetchSemanticsNode().boundsInRoot.top)
        onNodeWithTag(WORKSHOP_UPGRADE_BUY_TAG).assert(description("Upgrade Circle to Lvl 1 for 50 Matter")).assertIsEnabled()
        onNodeWithTag(WORKSHOP_UPGRADE_BUY_TAG).performClick()
        mainClock.advanceTimeBy(64)
        assertEquals(listOf<ProfilePulse.Business>(ProfilePulse.PurchaseCharacterUpgrade(CoreShape.ORB)), port.pulses)
        onNodeWithTag(WORKSHOP_ANNOUNCE_TAG).assert(description("Circle upgraded to Lvl 1. 1,234 Matter left"))
        onNodeWithTag(WORKSHOP_UPGRADE_PIPS_TAG).assert(description("Lvl 1 of 10"))
        onNodeWithTag("$WORKSHOP_TAG-matter").assert(description("1,234 Matter"))
        onNodeWithTag("profile-hangar-tile-${CoreShape.ORB}").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Selected, Lvl 1"))
        // A Profile that moved on refuses: the refusal is named in the assertive region, nothing changes.
        port.refuseNext = ProfileRejection.UpgradeMaxLevel
        onNodeWithTag(WORKSHOP_UPGRADE_BUY_TAG).performClick()
        mainClock.advanceTimeBy(64)
        assertEquals(ProfilePulse.PurchaseCharacterUpgrade(CoreShape.ORB), port.pulses.last())
        onNodeWithTag(WORKSHOP_ALERT_TAG).assert(description("Already at level 10"))
        onNodeWithTag(WORKSHOP_UPGRADE_PIPS_TAG).assert(description("Lvl 1 of 10"))
    }

    @Test
    fun honingIsSoldOnlyForOwnedWeaponsAndAShortBalanceDisablesIt() = runSkikoComposeUiTest(Size(1440f, 810f), Density(1f)) {
        val port = WorkshopTestPort(matter = 40L)
        workshop(port, feature(port), { Opening(1L, WorkshopTabRequest(WorkshopTab.WEAPONS, 1)) })
        mainClock.autoAdvance = false
        // Flux Wake (the starter, base 35) is affordable once.
        // Honing has no signature line.
        onNodeWithTag(WORKSHOP_UPGRADE_SIGNATURE_TAG).assertDoesNotExist()
        onNodeWithTag(WORKSHOP_UPGRADE_BUY_TAG).assert(description("Upgrade Flux Wake to Lvl 1 for 35 Matter")).performClick()
        mainClock.advanceTimeBy(64)
        assertEquals(listOf<ProfilePulse.Business>(ProfilePulse.PurchaseWeaponUpgrade(WeaponId.FLUX_WAKE)), port.pulses)
        // Level 2 costs 70 with 5 Matter left: disabled, and the missing Matter is stated.
        onNodeWithTag(WORKSHOP_UPGRADE_BUY_TAG).assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Lvl 1 of 10, Need 65 more matter"))
        onNodeWithTag("profile-armory-equip-${WeaponId.FLUX_WAKE}").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Starter, Lvl 1, Signature of your character"))
        // A weapon that is not owned shows the locked action and its first-level preview.
        onNodeWithTag("profile-armory-equip-${WeaponId.GYRE_DISC}").performClick()
        mainClock.advanceTimeBy(64)
        onNodeWithTag(WORKSHOP_UPGRADE_BUY_TAG).assertIsNotEnabled().assert(description("Locked"))
        assertEquals(1, port.pulses.size)
    }
}

/**
 * A Profile port for the three tabs: Circle, Square and Spiral unlocked, Flux Wake and Morningstar
 * owned, no Matrix ranks; a weapon unlock and a Refit or Honing level (the content price rule) are
 * applied when the Matter covers them.
 */
private class WorkshopTestPort(private var matter: Long = 1_284L) : ProfilePort {
    private var revision = ProfileRevision(1L)
    private var owned = setOf(WeaponId.FLUX_WAKE, WeaponId.MORNINGSTAR)
    private var starter = WeaponId.FLUX_WAKE
    private val characterLevels = IntArray(CoreShape.entries.size)
    private val weaponLevels = IntArray(WeaponId.entries.size)
    val pulses = mutableListOf<ProfilePulse.Business>()

    /** The next purchase is refused for this reason (a screen older than the Profile). */
    var refuseNext: ProfileRejection? = null

    override val instanceId = LOCAL_PROFILE_INSTANCE_ID

    override fun accept(pulse: ProfilePulse.Business): ProfileAcceptance {
        pulses += pulse
        refuseNext?.let { reason ->
            refuseNext = null
            return ProfileAcceptance.Rejected(instanceId, revision, reason)
        }
        val policy = UpgradePolicy()
        when (pulse) {
            is ProfilePulse.PurchaseCharacterUpgrade -> {
                val base = defaultCoreShapeDefinitions().first { it.id == pulse.shape }.upgradeBaseCost
                return buyLevel(characterLevels, pulse.shape.ordinal, policy.nextCost(base, characterLevels[pulse.shape.ordinal]))
            }
            is ProfilePulse.PurchaseWeaponUpgrade -> {
                if (pulse.id !in owned) return ProfileAcceptance.Rejected(instanceId, revision, ProfileRejection.WeaponNotOwned)
                val base = CatalogWeapons.first { it.id == pulse.id }.upgradeBaseCost
                return buyLevel(weaponLevels, pulse.id.ordinal, policy.nextCost(base, weaponLevels[pulse.id.ordinal]))
            }
            else -> Unit
        }
        val weapon = (pulse as? ProfilePulse.PurchaseOrEquipWeapon)?.id
            ?: return ProfileAcceptance.Rejected(instanceId, revision, ProfileRejection.NoChange)
        val cost = CatalogWeapons.first { it.id == weapon }.permanentUnlockCost.toLong()
        if (weapon !in owned) {
            if (matter < cost) return ProfileAcceptance.Rejected(instanceId, revision, ProfileRejection.InsufficientMatter)
            matter -= cost
            owned = owned + weapon
        }
        starter = weapon
        revision = ProfileRevision(revision.value + 1)
        return ProfileAcceptance.Accepted(instanceId, revision)
    }

    private fun buyLevel(levels: IntArray, index: Int, cost: Long): ProfileAcceptance {
        if (levels[index] >= UpgradePolicy().maxLevel) return ProfileAcceptance.Rejected(instanceId, revision, ProfileRejection.UpgradeMaxLevel)
        if (matter < cost) return ProfileAcceptance.Rejected(instanceId, revision, ProfileRejection.InsufficientMatter)
        matter -= cost
        levels[index] += 1
        revision = ProfileRevision(revision.value + 1)
        return ProfileAcceptance.Accepted(instanceId, revision)
    }

    override fun query(query: ProfileQuery.GetLoadout): LoadoutProjection = LoadoutProjection(instanceId, revision,
        LoadoutProfileSnapshot(PlayerEconomy(matter), PlayerLoadout(CoreShape.ORB, starter, owned.toImmutableSet()),
            upgrades = PermanentUpgrades(characterLevels.toList(), weaponLevels.toList())))

    override fun query(query: ProfileQuery.GetHangar): HangarProjection = HangarProjection(
        instanceId, revision,
        defaultCoreShapeDefinitions().map { definition ->
            val unlocked = definition.id in setOf(CoreShape.ORB, CoreShape.PRISM, CoreShape.SPIRAL)
            HangarShapeProgress(definition.id, unlocked, 0L, definition.unlockTarget.toLong())
        }.toImmutableList(),
        CoreShape.ORB,
    )

    override fun query(query: ProfileQuery.GetMatrixProgress): MatrixProgressProjection = MatrixProgressProjection(
        instanceId, revision, MatrixProfileSnapshot(PlayerEconomy(matter), MatrixProgress(MatrixNodeId.entries.map { 0 }), rebirthRank = 0),
    )

    override fun query(query: ProfileQuery.GetCollection): CollectionProjection = CollectionProjection(instanceId, revision, PlayerCollection())

    override fun query(query: ProfileQuery.GetPreferences): PreferencesProjection =
        PreferencesProjection(instanceId, revision, PlayerPreferences(textScale = 1.25f))

    override fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection = error("unused")
    override fun query(query: ProfileQuery.GetHomeProgress): HomeProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetRebirthProgress): RebirthProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetPersistenceStatus): PersistenceStatusProjection = error("unused")
}

private object WorkshopSilentAudio : AudioService {
    override fun updatePreferences(preferences: AudioPreferences) = Unit
    override fun advance(realDeltaSeconds: Float, requests: List<ToneRequest>) = Unit
    override fun ensureUnlocked() = Unit
    override fun close() = Unit
}