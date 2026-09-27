// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.api

import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.WeaponId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ProfileApiContractTest {
    @Test
    fun localIdentityHasStableCanonicalValues() {
        assertEquals("local-player", LocalPlayerId.LOCAL_PLAYER.stableValue)
        assertEquals("kinetickk.local/Profile/local-player", LOCAL_PROFILE_INSTANCE_ID.canonicalValue)
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
            ProfilePulse.PurchaseMetaUpgrade(MetaUpgradeId.CORE_INTEGRITY),
            ProfilePulse.PurchaseOrEquipWeapon(WeaponId.MORNINGSTAR),
        )

        assertEquals(3, intents.size)
        assertIs<ProfilePulse.AdjustPreference>(intents[0])
        assertIs<ProfilePulse.PurchaseMetaUpgrade>(intents[1])
        assertIs<ProfilePulse.PurchaseOrEquipWeapon>(intents[2])
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
        val progress = GameplayProgressUpdate(discoveredItemIds = discoveries)
        val collection = PlayerCollection(discoveries)

        discoveries += 3

        assertEquals(setOf(1, 2), progress.discoveredItemIds)
        assertEquals(setOf(1, 2), collection.discoveredItemIds)
    }
}
