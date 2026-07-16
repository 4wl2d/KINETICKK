// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.settings

import kinetickk.application.runtime.AdmissionFailure
import kinetickk.application.runtime.MandatoryDecisionLimit
import kinetickk.features.settings.nucleus.domain.SettingsValues
import kinetickk.features.settings.nucleus.protocol.SettingsDecisionContext
import kinetickk.features.settings.nucleus.protocol.SettingsIntent
import kinetickk.features.settings.nucleus.protocol.SettingsOperationId
import kinetickk.features.settings.nucleus.protocol.SettingsProjectionPayload
import kinetickk.features.settings.nucleus.protocol.SettingsProtocol
import kinetickk.features.settings.nucleus.protocol.SettingsQuery
import kinetickk.features.settings.nucleus.protocol.SettingsQueryResult
import kinetickk.features.settings.nucleus.protocol.SettingsRejection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SettingsFeatureBallTest {
    @Test
    fun queryReturnsTheExactCommittedStampWithoutAdvancingRevision() {
        val ball = SettingsFeatureBall()

        val first = ball.settingsRead()
        val second = ball.settingsRead()

        assertEquals(SettingsProtocol.BALL_INSTANCE_ID, first.consistencyStamp.ballInstanceId)
        assertEquals(0uL, first.consistencyStamp.commitRevision)
        assertEquals(SettingsProtocol.STATE_SCHEMA_VERSION, first.consistencyStamp.stateSchemaVersion)
        assertEquals(first, second)
    }

    @Test
    fun committedDispatchPublishesSettingsAndOneVersionedProjection() {
        val ball = SettingsFeatureBall()

        val committed = assertIs<SettingsDispatchResult.Committed>(
            ball.dispatch(SettingsIntent.MuteToggled),
        )

        assertEquals(1uL, committed.sourceCommitRevision)
        assertEquals(1uL, committed.settingsRead.consistencyStamp.commitRevision)
        assertFalse(committed.settingsRead.payload.soundEnabled)
        assertFalse(committed.settingsRead.payload.musicEnabled)
        val output = committed.projections.single()
        assertEquals(SettingsOperationId(1uL), output.semanticHandle.operationId)
        assertEquals(0u, output.sourceOrdinal)
        val changed = assertIs<SettingsProjectionPayload.SettingsChanged>(output.payload)
        assertEquals(committed.settingsRead.payload, changed.settings)

        val second = assertIs<SettingsDispatchResult.Committed>(
            ball.dispatch(SettingsIntent.SoundToggled),
        )
        assertEquals(2uL, second.sourceCommitRevision)
        assertEquals(SettingsOperationId(2uL), second.projections.single().semanticHandle.operationId)
    }

    @Test
    fun initialAuthorityIsNormalizedBeforeRevisionZero() {
        val ball = SettingsFeatureBall(
            SettingsValues(
                masterVolume = -5f,
                simulationSpeed = 99f,
                textScale = Float.NaN,
            ),
        )

        val read = ball.settingsRead()
        assertEquals(0f, read.payload.masterVolume)
        assertEquals(2f, read.payload.simulationSpeed)
        assertEquals(SettingsValues.DEFAULT_TEXT_SCALE, read.payload.textScale)
        assertEquals(0uL, read.consistencyStamp.commitRevision)
    }

    @Test
    fun finiteLimitsRejectOversizedInputCausalDepthAndRetry() {
        val limits = SettingsFeatureBall.LIMITS
        assertTrue(limits.maxInputBytes > 0)
        assertTrue(limits.maxStateBytes > 0)
        assertEquals(1, limits.maxCollectionItems)
        assertEquals(1, limits.maxOutputsPerDecision)
        assertEquals(0, limits.maxEffectsPerDecision)
        assertEquals(0, limits.maxCommandsPerDecision)
        assertEquals(1, limits.maxCausalDepth)
        assertEquals(0, limits.maxRetriesPerOperation)
        assertEquals(1, limits.maxTransitionSteps)

        val oversized = SettingsFeatureBall().dispatch(
            SettingsIntent.SoundToggled,
            context().copy(transitionArtifact = "x".repeat(65)),
        )
        assertLimit(oversized, MandatoryDecisionLimit.INPUT_BYTES)

        val tooDeep = SettingsFeatureBall().dispatch(
            SettingsIntent.SoundToggled,
            context().copy(causalDepth = 2),
        )
        assertLimit(tooDeep, MandatoryDecisionLimit.CAUSAL_DEPTH)

        val retry = SettingsFeatureBall().dispatch(
            SettingsIntent.SoundToggled,
            context().copy(retryCount = 1),
        )
        assertLimit(retry, MandatoryDecisionLimit.RETRIES_PER_OPERATION)
    }

    @Test
    fun wellSizedButUnsupportedContextIsADecisionRejection() {
        val result = SettingsFeatureBall().dispatch(
            SettingsIntent.SoundToggled,
            context().copy(transitionArtifact = "settings-v0"),
        )

        val rejected = assertIs<SettingsDispatchResult.DecisionRejected>(result)
        assertEquals(
            SettingsRejection.InvalidContext("transitionArtifact", "unsupported artifact"),
            rejected.reason,
        )
    }

    private fun SettingsFeatureBall.settingsRead() =
        assertIs<SettingsQueryResult.Settings>(query(SettingsQuery.GetSettings)).value

    private fun context() = SettingsDecisionContext(
        operationId = SettingsOperationId(1uL),
    )

    private fun assertLimit(
        result: SettingsDispatchResult,
        expected: MandatoryDecisionLimit,
    ) {
        val rejected = assertIs<SettingsDispatchResult.AdmissionRejected>(result)
        val limit = assertIs<AdmissionFailure.LimitExceeded>(rejected.reason)
        assertEquals(expected, limit.limit)
    }
}
