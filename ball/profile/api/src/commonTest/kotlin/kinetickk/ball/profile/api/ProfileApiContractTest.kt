// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.api

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.WeaponId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileApiContractTest {
    @Test
    fun localIdentityHasStableCanonicalValues() {
        assertEquals("local-player", LocalPlayerId.LOCAL_PLAYER.stableValue)
        assertEquals("kinetickk.local/Profile/local-player", LOCAL_PROFILE_INSTANCE_ID.canonicalValue)
    }

    @Test
    fun rebirthRankIsOneAboveTheHighestClearedTierWithinBoundsAndAscendantAfterTheLast() {
        assertEquals(0, RebirthProgress().rank)
        assertEquals(1, RebirthProgress(level = 0, highestCleared = 0).rank)
        assertEquals(5, RebirthProgress(level = 2, highestCleared = 4).rank)
        assertEquals(ContentBounds.MAX_REBIRTH_LEVEL, RebirthProgress(highestCleared = ContentBounds.MAX_REBIRTH_LEVEL - 1).rank)
        assertEquals(ContentBounds.MAX_REBIRTH_LEVEL, RebirthProgress(highestCleared = ContentBounds.MAX_REBIRTH_LEVEL).rank)
        assertEquals(ContentBounds.MIN_REBIRTH_LEVEL, RebirthProgress(highestCleared = -5).rank)

        assertFalse(RebirthProgress().isAscendant)
        assertFalse(RebirthProgress(highestCleared = ContentBounds.MAX_REBIRTH_LEVEL - 1).isAscendant)
        assertTrue(RebirthProgress(highestCleared = ContentBounds.MAX_REBIRTH_LEVEL).isAscendant)
    }

    @Test
    fun revisionsAndPersistenceEffectOrdinalsRejectNegativeValues() {
        assertFailsWith<IllegalArgumentException> { ProfileRevision(-1L) }
        assertFailsWith<IllegalArgumentException> {
            ProfileEffectRef(ProfileRevision.ZERO, ordinal = -1)
        }
    }

    @Test
    fun localIntentPayloadsRemainTyped() {
        val intents: List<ProfilePulse.Business> = listOf(
            ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.ToggleSoundEffects),
            ProfilePulse.PurchaseMatrixNode(MatrixNodeId.IMPACT_DRIVER),
            ProfilePulse.ResetMatrix,
            ProfilePulse.PurchaseOrEquipWeapon(WeaponId.MORNINGSTAR),
            ProfilePulse.SelectCoreShape(kinetickk.ball.content.api.CoreShape.HEXAGON),
        )

        assertEquals(5, intents.size)
        assertIs<ProfilePulse.AdjustPreference>(intents[0])
        assertEquals(MatrixNodeId.IMPACT_DRIVER, assertIs<ProfilePulse.PurchaseMatrixNode>(intents[1]).id)
        assertIs<ProfilePulse.ResetMatrix>(intents[2])
        assertIs<ProfilePulse.PurchaseOrEquipWeapon>(intents[3])
        assertEquals(kinetickk.ball.content.api.CoreShape.HEXAGON, assertIs<ProfilePulse.SelectCoreShape>(intents[4]).shape)
    }

    @Test
    fun colorVisionDefaultsForEveryExistingConstructionAndKeepsItsOptionOrder() {
        assertEquals(
            listOf(ColorVision.DEFAULT, ColorVision.PROTAN, ColorVision.DEUTAN, ColorVision.TRITAN, ColorVision.MONO),
            ColorVision.entries,
        )
        assertEquals(ColorVision.DEFAULT, PlayerPreferences().colorVision)
        assertEquals(ColorVision.DEFAULT, PlayerProfile().preferences.colorVision)
        val mono = PlayerPreferences(colorVision = ColorVision.MONO)
        assertEquals(ColorVision.MONO, mono.normalized().colorVision)
        assertEquals(
            ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.SetColorVision(ColorVision.TRITAN)),
            ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.SetColorVision(ColorVision.TRITAN)),
        )
    }

    @Test
    fun directSimulationSpeedChoiceAcceptsOnlyListedOptions() {
        SIMULATION_SPEED_OPTIONS.forEach { speed ->
            assertEquals(speed, ProfilePreferenceAdjustment.SetSimulationSpeed(speed).speed)
        }
        for (unlisted in listOf(0f, 0.7f, 1.05f, 3f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { ProfilePreferenceAdjustment.SetSimulationSpeed(unlisted) }
        }
    }

    @Test
    fun resourceSnapshotCarriesOnlyRevisionAndValidatedBusinessProfile() {
        val profile = PlayerProfile()
        val snapshot = ProfileSnapshot(ProfileRevision(3L), profile)

        assertEquals(ProfileRevision(3L), snapshot.revision)
        assertEquals(profile, snapshot.profile)
    }

    @Test
    fun publicCollectionPayloadsDefensivelyOwnTheirStorage() {
        val discoveries = mutableSetOf(1, 2)
        val reactions = mutableSetOf(ReactionId.FUSION)
        val pairings = mutableSetOf(RelicPairingId.MELTDOWN)
        val victory = mutableListOf(RelicId.GLASS_COMET, RelicId.BLINK_DRIVE)
        val progress = GameplayProgressUpdate(
            discoveredItemIds = discoveries,
            clearedRebirthLevel = 0,
            discoveredReactionIds = reactions,
            discoveredPairingIds = pairings,
            victoryRelicIds = victory,
        )
        val collection = PlayerCollection(
            discoveries,
            discoveredReactionIds = reactions,
            discoveredPairingIds = pairings,
            heirloomRelicIds = victory,
        )

        discoveries += 3
        reactions += ReactionId.WILDFIRE
        pairings += RelicPairingId.FIRESTORM
        victory.reverse()

        assertEquals(setOf(1, 2), progress.discoveredItemIds)
        assertEquals(setOf(1, 2), collection.discoveredItemIds)
        assertEquals(setOf(ReactionId.FUSION), progress.discoveredReactionIds)
        assertEquals(setOf(ReactionId.FUSION), collection.discoveredReactionIds)
        assertEquals(setOf(RelicPairingId.MELTDOWN), progress.discoveredPairingIds)
        assertEquals(setOf(RelicPairingId.MELTDOWN), collection.discoveredPairingIds)
        assertEquals<List<RelicId>?>(listOf(RelicId.GLASS_COMET, RelicId.BLINK_DRIVE), progress.victoryRelicIds)
        assertEquals(listOf(RelicId.GLASS_COMET, RelicId.BLINK_DRIVE), collection.heirloomRelicIds)
    }

    @Test
    fun existingConstructionsDefaultTheBuildDiscoveriesAndHeirlooms() {
        val collection = PlayerCollection(setOf(1), setOf(1), setOf(RelicId.HOLLOW_CROWN), setOf(RelicId.HOLLOW_CROWN))
        assertTrue(collection.discoveredReactionIds.isEmpty())
        assertTrue(collection.discoveredPairingIds.isEmpty())
        assertTrue(collection.heirloomRelicIds.isEmpty())
        assertEquals(PlayerCollection(), PlayerProfile().collection)

        val legacyUpdate = GameplayProgressUpdate(bankedMatter = 3L, discoveredItemIds = setOf(1))
        assertNull(legacyUpdate.victoryRelicIds)
        assertFalse(legacyUpdate.heirloomResolved)
        assertFalse(legacyUpdate.isEmpty)
    }

    @Test
    fun progressUpdateIsEmptyOnlyWithoutAnyFactToApply() {
        assertTrue(GameplayProgressUpdate().isEmpty)
        val carriers = listOf(
            GameplayProgressUpdate(bankedMatter = 1L),
            GameplayProgressUpdate(discoveredItemIds = setOf(0)),
            GameplayProgressUpdate(clearedRebirthLevel = 0),
            GameplayProgressUpdate(eliteKills = 1),
            GameplayProgressUpdate(dashHits = 1),
            GameplayProgressUpdate(completedOrbits = 1),
            GameplayProgressUpdate(architectDefeatedWith = kinetickk.ball.content.api.CoreShape.ORB),
            GameplayProgressUpdate(discoveredItemIds = emptySet(), discoveredRelicIds = setOf(RelicId.ECHO_CHAMBER)),
            GameplayProgressUpdate(discoveredItemIds = emptySet(), discoveredReactionIds = setOf(ReactionId.ROT_WELL)),
            GameplayProgressUpdate(discoveredItemIds = emptySet(), discoveredPairingIds = setOf(RelicPairingId.HOLLOW_VOW)),
            // An empty victory list still replaces the remembered Heirlooms.
            GameplayProgressUpdate(discoveredItemIds = emptySet(), victoryRelicIds = emptyList()),
            GameplayProgressUpdate(heirloomResolved = true),
            GameplayProgressUpdate(totalKills = 1),
            GameplayProgressUpdate(ricochets = 1),
            GameplayProgressUpdate(dataPickups = 1),
        )
        carriers.forEach { update -> assertFalse(update.isEmpty, update.toString()) }
    }

    @Test
    fun matrixProgressIsIndexedByNodeOrdinalAndCountsOwnedNodes() {
        val progress = MatrixProgress()
        assertEquals(MatrixNodeId.entries.size, progress.ranks.size)
        assertEquals(0, progress.ownedNodeCount)
        val ranks = MutableList(MatrixNodeId.entries.size) { 0 }
        ranks[MatrixNodeId.SECOND_WIND.ordinal] = 1
        ranks[MatrixNodeId.IMPACT_DRIVER.ordinal] = 3
        val owned = MatrixProgress(ranks)
        assertEquals(3, owned.rank(MatrixNodeId.IMPACT_DRIVER))
        assertTrue(owned.owns(MatrixNodeId.SECOND_WIND))
        assertFalse(owned.owns(MatrixNodeId.PROSPECTOR))
        assertEquals(2, owned.ownedNodeCount)
        assertEquals(0L, CharacterAchievementProgress().totalKills + CharacterAchievementProgress().ricochets +
            CharacterAchievementProgress().dataPickups)
    }
}
