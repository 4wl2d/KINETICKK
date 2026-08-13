// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.api

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.WeaponId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

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
    fun revisionsSemanticHandlesAndCorrelationOrdinalsRejectNegativeValues() {
        assertFailsWith<IllegalArgumentException> { ProfileRevision(-1L) }
        assertFailsWith<IllegalArgumentException> {
            ProfileSemanticHandle(
                sourceInstance = ProfileCommandSource.LocalSession,
                sourceRevision = -1L,
                sourceOrdinal = 0,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ProfileSemanticHandle(
                sourceInstance = ProfileCommandSource.LocalSession,
                sourceRevision = 0L,
                sourceOrdinal = -1,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ProfileEffectRef(ProfileRevision.ZERO, ordinal = -1)
        }
    }

    @Test
    fun targetBoundaryRequestRetainsExactAcceptedSourceEvidenceAndCommand() {
        val handle = ProfileSemanticHandle(
            sourceInstance = ProfileCommandSource.LocalSession,
            sourceRevision = 19L,
            sourceOrdinal = 4,
        )
        val command = ProfileModuleCommand.SelectCoreShape(CoreShape.SHARD)
        val request = ProfileModuleCommandRequest(
            semanticHandle = handle,
            sourceOrdinal = handle.sourceOrdinal,
            targetInstance = LOCAL_PROFILE_INSTANCE_ID,
            command = command,
        )

        assertEquals(handle, request.semanticHandle)
        assertEquals(4, request.sourceOrdinal)
        assertEquals(LOCAL_PROFILE_INSTANCE_ID, request.targetInstance)
        assertEquals(command, request.command)
        assertFailsWith<IllegalArgumentException> {
            request.copy(sourceOrdinal = handle.sourceOrdinal + 1)
        }
    }

    @Test
    fun localIntentInventoryIsClosedAndDoesNotAliasModuleCommands() {
        val intents: List<ProfilePulse.Business> = listOf(
            ProfilePulse.AdjustPreference(ProfilePreferenceAdjustment.ToggleSoundEffects),
            ProfilePulse.PurchaseMetaUpgrade(MetaUpgradeId.CORE_INTEGRITY),
            ProfilePulse.PurchaseOrEquipWeapon(WeaponId.MORNINGSTAR),
        )

        assertEquals(3, intents.size)
        assertIs<ProfilePulse.AdjustPreference>(intents[0])
        assertIs<ProfilePulse.PurchaseMetaUpgrade>(intents[1])
        assertIs<ProfilePulse.PurchaseOrEquipWeapon>(intents[2])
        val moduleCommand: Any = ProfileModuleCommand.ToggleMute
        assertFalse(moduleCommand is ProfilePulse)
    }

    @Test
    fun acceptedResultDeliveryCarriesTheExactCommandAndTargetFrameEvidence() {
        val handle = ProfileSemanticHandle(ProfileCommandSource.LocalSession, 5L, 2)
        val commandSource = ProfileCommandSourceToken(
            semanticHandle = handle,
            targetInstance = LOCAL_PROFILE_INSTANCE_ID,
            causalScope = 91L,
            causalDepth = 3,
        )
        val result = ProfileModuleResult.CoreShapeSelected(CoreShape.SHARD)
        val delivery = ProfileModuleResultDelivery(
            commandSource = commandSource,
            resultSource = ProfileResultSourceToken(
                semanticHandle = handle,
                targetInstance = LOCAL_PROFILE_INSTANCE_ID,
                targetRevision = ProfileRevision(12L),
                sourceOrdinal = 1,
                causalScope = 91L,
                causalDepth = 4,
            ),
            effectiveProtocolIdentity = ProfileEffectiveProtocolIdentity.SESSION_CORE_SHAPE,
            result = result,
            issuerProvenance = ProfileResultIssuerProvenance.LOCAL_PROFILE_STATIC_BINDING,
        )

        assertEquals(commandSource, delivery.commandSource)
        assertEquals(handle, delivery.resultSource.semanticHandle)
        assertEquals(ProfileRevision(12L), delivery.resultSource.targetRevision)
        assertEquals(91L, delivery.resultSource.causalScope)
        assertEquals(4, delivery.resultSource.causalDepth)
        assertEquals(ProfileEffectiveProtocolIdentity.SESSION_CORE_SHAPE, delivery.effectiveProtocolIdentity)
        assertEquals(result, delivery.result)
        assertEquals(ProfileResultIssuerProvenance.LOCAL_PROFILE_STATIC_BINDING, delivery.issuerProvenance)
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
