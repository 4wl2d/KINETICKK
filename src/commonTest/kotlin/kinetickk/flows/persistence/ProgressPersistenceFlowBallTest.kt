// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flows.persistence

import kinetickk.application.runtime.AdmissionFailure
import kinetickk.application.runtime.ConsistencyStamp
import kinetickk.application.runtime.MandatoryDecisionLimit
import kinetickk.application.runtime.ReadContext
import kinetickk.features.profile.nucleus.projection.ProfileProjection
import kinetickk.features.settings.nucleus.protocol.SettingsProtocol
import kinetickk.flows.persistence.model.PersistedProgress
import kinetickk.flows.persistence.model.PersistedSettings
import kinetickk.flows.persistence.resources.ProgressLoadResult
import kinetickk.flows.persistence.resources.ProgressPersistResult
import kinetickk.flows.persistence.resources.ProgressResourceFailure
import kinetickk.flows.persistence.resources.ProgressStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProgressPersistenceFlowBallTest {
    @Test
    fun acceptedSnapshotIsVisibleAsPendingBeforeTheResourceAndCompletesWithTheExactHandle() {
        lateinit var flow: ProgressPersistenceFlowBall
        val observations = mutableListOf<ProgressPersistenceStatus>()
        val observationRevisions = mutableListOf<ULong>()
        val store = RecordingProgressStore().apply {
            onPersist = {
                val read = flow.status()
                observations += read.payload
                observationRevisions += read.consistencyStamp.commitRevision
            }
        }
        flow = ProgressPersistenceFlowBall.create(store)
        val snapshot = PersistedProgress(
            matter = 73L,
            lifetimeMatter = 120L,
            discoveredItemIds = setOf(3, 7, 11),
        )

        val result = assertIs<ProgressPersistenceDispatchResult.Committed>(flow.persist(snapshot))

        assertSame(snapshot, store.persisted.single())
        val pending = assertIs<ProgressPersistenceStatus.Pending>(observations.single())
        val persisted = assertIs<ProgressPersistenceStatus.Persisted>(result.statusRead.payload)
        assertEquals(ProgressPersistenceHandle(ProgressOperationId(1uL), 1L), pending.handle)
        assertEquals(pending.handle, persisted.handle)
        assertEquals(1uL, result.sourceCommitRevision)
        assertEquals(listOf(1uL), observationRevisions)
        assertEquals(2uL, result.statusRead.consistencyStamp.commitRevision)
        assertEquals(ProgressPersistenceContinuationStatus.Idle, result.continuationStatus)
        assertEquals(ProgressPersistenceContinuationStatus.Idle, flow.completionStatus())
    }

    @Test
    fun successfulRequestsAdvanceOperationAndGenerationCorrelationMonotonically() {
        val store = RecordingProgressStore()
        val flow = ProgressPersistenceFlowBall.create(store)

        val first = assertIs<ProgressPersistenceDispatchResult.Committed>(
            flow.persist(PersistedProgress(matter = 1L)),
        )
        val second = assertIs<ProgressPersistenceDispatchResult.Committed>(
            flow.persist(PersistedProgress(matter = 2L)),
        )

        val firstHandle = assertIs<ProgressPersistenceStatus.Persisted>(first.statusRead.payload).handle
        val secondHandle = assertIs<ProgressPersistenceStatus.Persisted>(second.statusRead.payload).handle
        assertEquals(ProgressPersistenceHandle(ProgressOperationId(1uL), 1L), firstHandle)
        assertEquals(ProgressPersistenceHandle(ProgressOperationId(2uL), 2L), secondHandle)
        assertEquals(1uL, first.sourceCommitRevision)
        assertEquals(3uL, second.sourceCommitRevision)
        assertEquals(4uL, second.statusRead.consistencyStamp.commitRevision)
        assertEquals(listOf(1L, 2L), store.persisted.map(PersistedProgress::matter))
    }

    @Test
    fun everyTypedResourceUnknownReasonIsRetainedInTheFinalStatus() {
        val mappings = listOf(
            ProgressResourceFailure.PROVIDER_READ_FAILED to
                ProgressPersistenceUnknownReason.PROVIDER_READ_FAILED,
            ProgressResourceFailure.ENCODING_FAILED to
                ProgressPersistenceUnknownReason.ENCODING_FAILED,
            ProgressResourceFailure.PAYLOAD_LIMIT_EXCEEDED to
                ProgressPersistenceUnknownReason.PAYLOAD_LIMIT_EXCEEDED,
            ProgressResourceFailure.PROVIDER_WRITE_MAY_HAVE_EXECUTED to
                ProgressPersistenceUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
        )

        mappings.forEach { (resourceReason, expectedReason) ->
            val store = RecordingProgressStore(
                persistResult = ProgressPersistResult.OutcomeUnknown(resourceReason),
            )
            val flow = ProgressPersistenceFlowBall.create(store)

            val result = assertIs<ProgressPersistenceDispatchResult.Committed>(
                flow.persist(PersistedProgress(matter = 9L)),
            )

            val unknown = assertIs<ProgressPersistenceStatus.OutcomeUnknown>(result.statusRead.payload)
            assertEquals(ProgressPersistenceHandle(ProgressOperationId(1uL), 1L), unknown.handle)
            assertEquals(expectedReason, unknown.reason)
            assertEquals(2uL, result.statusRead.consistencyStamp.commitRevision)
            assertEquals(ProgressPersistenceContinuationStatus.Idle, result.continuationStatus)
        }
    }

    @Test
    fun thrownWriteFailuresAreQuarantinedWithoutEscaping() {
        val writeFailureStore = RecordingProgressStore(throwOnPersist = true)
        val writeFlow = ProgressPersistenceFlowBall.create(writeFailureStore)
        val writeResult = assertIs<ProgressPersistenceDispatchResult.Committed>(
            writeFlow.persist(PersistedProgress()),
        )
        val writeUnknown = assertIs<ProgressPersistenceStatus.OutcomeUnknown>(
            writeResult.statusRead.payload,
        )
        assertEquals(
            ProgressPersistenceUnknownReason.PROVIDER_WRITE_MAY_HAVE_EXECUTED,
            writeUnknown.reason,
        )
        assertEquals(1, writeFailureStore.persisted.size)
    }

    @Test
    fun statusReadIsStampedAndDoesNotMutateWhileWrongProtocolFailsClosed() {
        val flow = ProgressPersistenceFlowBall.create(RecordingProgressStore())

        val first = flow.status()
        val second = flow.status()

        assertEquals(ProgressPersistenceStatus.NeverRequested, first.payload)
        assertEquals(first, second)
        assertEquals(ProgressPersistenceFlowBall.BALL_INSTANCE_ID, first.consistencyStamp.ballInstanceId)
        assertEquals(0uL, first.consistencyStamp.commitRevision)
        assertEquals(
            ProgressPersistenceFlowBall.STATE_SCHEMA_VERSION,
            first.consistencyStamp.stateSchemaVersion,
        )
        assertFailsWith<IllegalArgumentException> {
            flow.status(ReadContext(protocolVersion = "0.0.0"))
        }
    }

    @Test
    fun capturedAuthorityProvenanceMustMatchSupportedProfileAndSettingsSchemas() {
        val store = RecordingProgressStore()
        val flow = ProgressPersistenceFlowBall.create(store)

        val result = flow.persist(
            snapshot = PersistedProgress(),
            provenance = ProgressCaptureProvenance(
                profileSnapshot = ConsistencyStamp("wrong/Profile", 1uL, 1),
                settingsSnapshot = ConsistencyStamp("wrong/Settings", 1uL, 1),
            ),
        )

        val rejected = assertIs<ProgressPersistenceDispatchResult.DecisionRejected>(result)
        val reason = assertIs<ProgressPersistenceRejection.InvalidInput>(rejected.reason)
        assertEquals("provenance.profileSnapshot", reason.field)

        val settingsResult = flow.persist(
            snapshot = PersistedProgress(),
            provenance = ProgressCaptureProvenance(
                profileSnapshot = ConsistencyStamp(
                    ProfileProjection.BALL_INSTANCE_ID,
                    1uL,
                    ProfileProjection.STATE_SCHEMA_VERSION,
                ),
                settingsSnapshot = ConsistencyStamp("wrong/Settings", 1uL, 1),
            ),
        )
        val settingsRejected =
            assertIs<ProgressPersistenceDispatchResult.DecisionRejected>(settingsResult)
        val settingsReason =
            assertIs<ProgressPersistenceRejection.InvalidInput>(settingsRejected.reason)
        assertEquals("provenance.settingsSnapshot", settingsReason.field)
        assertTrue(store.persisted.isEmpty())
        assertEquals(ProgressPersistenceStatus.NeverRequested, flow.status().payload)
    }

    @Test
    fun nonCanonicalSnapshotsAreRejectedBeforeResourceExecution() {
        val cases = listOf(
            PersistedProgress(
                discoveredItemIds = (0..ProgressPersistenceSchema.ITEM_ID_COUNT).toSet(),
            ) to
                "snapshot.discoveredItemIds",
            PersistedProgress(
                metaLevels = List(ProgressPersistenceSchema.META_UPGRADE_CODE_COUNT - 1) { 0 },
            ) to "snapshot.metaLevels",
            PersistedProgress(
                settings = PersistedSettings(masterVolume = 0.123f),
            ) to "snapshot.settings",
            PersistedProgress(
                settings = PersistedSettings(particleDensityCode = Int.MAX_VALUE),
            ) to "snapshot.settings.particleDensityCode",
            PersistedProgress(
                settings = PersistedSettings(damageNumberSizeCode = -1),
            ) to "snapshot.settings.damageNumberSizeCode",
            PersistedProgress(
                settings = PersistedSettings(damageNumberFormatCode = Int.MAX_VALUE),
            ) to "snapshot.settings.damageNumberFormatCode",
            PersistedProgress(
                selectedWeaponIndex = 1,
                unlockedWeaponIndices = setOf(ProgressPersistenceSchema.BASELINE_WEAPON_CODE),
            ) to "snapshot.selectedWeaponIndex",
        )

        cases.forEach { (snapshot, expectedField) ->
            val store = RecordingProgressStore()
            val flow = ProgressPersistenceFlowBall.create(store)
            val result = flow.persist(snapshot)
            val rejected = assertIs<ProgressPersistenceDispatchResult.DecisionRejected>(result)
            val reason = assertIs<ProgressPersistenceRejection.InvalidInput>(rejected.reason)
            assertEquals(expectedField, reason.field)
            assertTrue(store.persisted.isEmpty())
            assertEquals(ProgressPersistenceStatus.NeverRequested, flow.status().payload)
        }
    }

    @Test
    fun reentrantPersistenceIsRejectedWithoutChangingTheAcceptedRootCorrelation() {
        lateinit var flow: ProgressPersistenceFlowBall
        var nestedResult: ProgressPersistenceDispatchResult? = null
        val store = RecordingProgressStore().apply {
            onPersist = {
                nestedResult = flow.persist(PersistedProgress(matter = 999L))
            }
        }
        flow = ProgressPersistenceFlowBall.create(store)

        val outer = assertIs<ProgressPersistenceDispatchResult.Committed>(
            flow.persist(PersistedProgress(matter = 7L)),
        )

        val nested = assertIs<ProgressPersistenceDispatchResult.AdmissionRejected>(nestedResult)
        assertEquals(AdmissionFailure.ReentrantSubmission, nested.reason)
        val persisted = assertIs<ProgressPersistenceStatus.Persisted>(outer.statusRead.payload)
        assertEquals(ProgressPersistenceHandle(ProgressOperationId(1uL), 1L), persisted.handle)
        assertEquals(listOf(7L), store.persisted.map(PersistedProgress::matter))
    }

    @Test
    fun oversizedSemanticCollectionsRejectTheWholeSnapshotBeforeResourceExecution() {
        val collectionStore = RecordingProgressStore()
        val collectionFlow = ProgressPersistenceFlowBall.create(collectionStore)
        val tooManyDiscoveries = (0..ProgressPersistenceFlowBall.LIMITS.maxCollectionItems).toSet()

        val collectionResult = collectionFlow.persist(
            PersistedProgress(discoveredItemIds = tooManyDiscoveries),
        )
        val collectionRejection =
            assertIs<ProgressPersistenceDispatchResult.DecisionRejected>(collectionResult)
        assertEquals(
            "snapshot.discoveredItemIds",
            assertIs<ProgressPersistenceRejection.InvalidInput>(collectionRejection.reason).field,
        )
        assertTrue(collectionStore.persisted.isEmpty())
        assertEquals(ProgressPersistenceStatus.NeverRequested, collectionFlow.status().payload)
        assertEquals(0uL, collectionFlow.status().consistencyStamp.commitRevision)

        val inputStore = RecordingProgressStore()
        val inputFlow = ProgressPersistenceFlowBall.create(inputStore)
        val oversizedInput = inputFlow.persist(
            PersistedProgress(discoveredItemIds = (0..10_000).toSet()),
        )
        val inputRejection = assertIs<ProgressPersistenceDispatchResult.AdmissionRejected>(
            oversizedInput,
        )
        assertEquals(
            MandatoryDecisionLimit.INPUT_BYTES,
            assertIs<AdmissionFailure.LimitExceeded>(inputRejection.reason).limit,
        )
        assertTrue(inputStore.persisted.isEmpty())
        assertEquals(0uL, inputFlow.status().consistencyStamp.commitRevision)
    }

}

private fun ProgressPersistenceFlowBall.persist(
    snapshot: PersistedProgress,
): ProgressPersistenceDispatchResult = persist(snapshot, validCaptureProvenance())

private fun validCaptureProvenance() = ProgressCaptureProvenance(
    profileSnapshot = ConsistencyStamp(
        ProfileProjection.BALL_INSTANCE_ID,
        1uL,
        ProfileProjection.STATE_SCHEMA_VERSION,
    ),
    settingsSnapshot = ConsistencyStamp(
        SettingsProtocol.BALL_INSTANCE_ID,
        1uL,
        SettingsProtocol.STATE_SCHEMA_VERSION,
    ),
)

private class RecordingProgressStore(
    private val persistResult: ProgressPersistResult = ProgressPersistResult.Persisted,
    private val throwOnPersist: Boolean = false,
) : ProgressStore {
    val persisted = mutableListOf<PersistedProgress>()
    var onPersist: (PersistedProgress) -> Unit = {}

    override fun load(): ProgressLoadResult = ProgressLoadResult.NotFound

    override fun persist(progress: PersistedProgress): ProgressPersistResult {
        persisted += progress
        onPersist(progress)
        if (throwOnPersist) error("provider write may have executed")
        return persistResult
    }
}
