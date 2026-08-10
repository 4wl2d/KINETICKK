// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.api

import kinetickk.ball.content.api.CoreShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileApiContractTest {
    @Test
    fun localIdentityAndCommandSourcesHaveStableCanonicalValues() {
        assertEquals("local-player", LocalPlayerId.LOCAL_PLAYER.stableValue)
        assertEquals("kinetickk.local/Profile/local-player", LOCAL_PROFILE_INSTANCE_ID.canonicalValue)
        assertEquals(
            "kinetickk.local/AppSession/local-session",
            ProfileCommandSource.LocalSession.canonicalValue,
        )
        assertEquals(
            "kinetickk.local/GameplayRun/7",
            ProfileCommandSource.GameplayRun(7L).canonicalValue,
        )
    }

    @Test
    fun revisionsAndCorrelationOrdinalsRejectNegativeValues() {
        assertFailsWith<IllegalArgumentException> { ProfileRevision(-1L) }
        assertFailsWith<IllegalArgumentException> {
            ProfileCommandRef(
                sourceInstance = ProfileCommandSource.LocalSession,
                targetInstance = LOCAL_PROFILE_INSTANCE_ID,
                sourceRevision = -1L,
                ordinal = 0,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ProfileEffectRef(ProfileRevision.ZERO, ordinal = -1)
        }
    }

    @Test
    fun sessionCoreSelectionCommandRetainsExactCorrelationPayloadAndOutcome() {
        val ref = ProfileCommandRef(
            sourceInstance = ProfileCommandSource.LocalSession,
            targetInstance = LOCAL_PROFILE_INSTANCE_ID,
            sourceRevision = 19L,
            ordinal = 4,
        )
        val pulse = ProfilePulse.SelectCoreShape(CoreShape.SHARD)
        val command = ProfileCommand(ref, pulse)
        val admission = ProfileCommandAdmission(ref)
        val outcome = ProfileCommandOutcome.CoreShapeSelected(CoreShape.SHARD)

        assertEquals(ref, command.ref)
        assertEquals(pulse, command.pulse)
        assertEquals(ref, admission.commandRef)
        assertEquals(CoreShape.SHARD, outcome.shape)
    }

    @Test
    fun legacyKeyInventoryIsClosedAndUnionsWithoutInventingKeys() {
        val progress = ProfileLegacyKeys(progressV2 = true, matter = false)
        val matter = ProfileLegacyKeys(progressV2 = false, matter = true)

        assertTrue(ProfileLegacyKeys.NONE.isEmpty)
        assertFalse(ProfileLegacyKeys.ALL.isEmpty)
        assertEquals(ProfileLegacyKeys.ALL, progress union matter)
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
