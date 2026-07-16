// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.application.assembly

import kinetickk.features.game.nucleus.projection.GameProjection
import kinetickk.features.profile.ProfileApplyRunOutcomeExecution
import kinetickk.features.profile.nucleus.projection.ProfileProjection
import kinetickk.features.profile.nucleus.protocol.ProfileApplyRunOutcomeContract
import kinetickk.features.profile.nucleus.protocol.ProfileApplyRunOutcomeModuleCommand
import kinetickk.features.profile.nucleus.protocol.ProfileApplyRunOutcomeModuleResult
import kinetickk.features.profile.nucleus.protocol.ProfileApplyRunOutcomeRejectionReason
import kinetickk.features.profile.nucleus.protocol.ProfileCausalScope
import kinetickk.features.profile.nucleus.protocol.ProfileCommandSource
import kinetickk.features.profile.nucleus.protocol.ProfileSnapshotReference
import kinetickk.features.profile.nucleus.protocol.RunSettlementId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RunSettlementRouterTest {
    @Test
    fun oneExactRedeliveryAppliesAndClearsTheSlot() {
        val delivered = mutableListOf<ProfileApplyRunOutcomeModuleCommand>()
        val command = settlementCommand(operationId = 17uL)
        val router = RunSettlementRouter(
            execute = { received ->
                delivered += received
                if (delivered.size == 1) admissionRejected(received) else applied(received)
            },
        )

        assertFalse(router.deliver(command))
        val retained = assertIs<RunSettlementRoutingStatus.Retained>(router.status())
        assertEquals(0, retained.retriesUsed)
        assertEquals(1, retained.maxRetries)
        assertTrue(router.hasPending())

        assertTrue(router.resume())
        assertEquals(listOf(command, command), delivered)
        assertIs<RunSettlementRoutingStatus.Applied>(router.status())
        assertFalse(router.hasPending())
    }

    @Test
    fun repeatedAdmissionFailureStopsAtTheFiniteLimitAndBackpressuresNewWork() {
        val delivered = mutableListOf<ProfileApplyRunOutcomeModuleCommand>()
        val command = settlementCommand(operationId = 23uL)
        val router = RunSettlementRouter(
            execute = { received ->
                delivered += received
                admissionRejected(received)
            },
        )

        assertFalse(router.deliver(command))
        assertFalse(router.resume())

        val stopped = assertIs<RunSettlementRoutingStatus.DispatchStopped>(router.status())
        assertEquals(1, stopped.retriesUsed)
        assertEquals(1, stopped.maxRetries)
        assertFalse(router.resume())
        assertEquals(listOf(command, command), delivered)
        assertEquals(1, router.backpressure().pending)
        assertEquals(1, router.backpressure().capacity)
        assertFailsWith<IllegalStateException> {
            router.deliver(settlementCommand(operationId = 24uL))
        }
    }

    @Test
    fun terminalTargetRejectionIsHonestAndDoesNotOccupyTheRetrySlot() {
        val command = settlementCommand(operationId = 29uL)
        val router = RunSettlementRouter(
            execute = { received ->
                rejected(received, ProfileApplyRunOutcomeRejectionReason.INVALID_RUN_MATTER)
            },
        )

        assertFalse(router.deliver(command))

        val status = assertIs<RunSettlementRoutingStatus.Rejected>(router.status())
        assertEquals(ProfileApplyRunOutcomeRejectionReason.INVALID_RUN_MATTER, status.result.reason)
        assertFalse(router.hasPending())
    }
}

private fun settlementCommand(operationId: ULong) = ProfileApplyRunOutcomeModuleCommand(
    commandSource = ProfileCommandSource(
        sourceBallInstanceId = GameProjection.BALL_INSTANCE_ID,
        sourceCommitRevision = 7uL,
        sourceOrdinal = 1u,
        sourceOperationId = operationId,
        sourceOutputKind = ProfileApplyRunOutcomeContract.SOURCE_OUTPUT_KIND,
        sourceLocalOrdinalOrName = "authority-0",
    ),
    causalBudgetScope = ProfileCausalScope(
        ownerBallInstanceId = GameProjection.BALL_INSTANCE_ID,
        operationId = operationId,
    ),
    causalDepth = ProfileApplyRunOutcomeContract.COMMAND_CAUSAL_DEPTH,
    matterEarned = 31L,
    clearedRebirthLevel = 0,
)

private fun admissionRejected(
    command: ProfileApplyRunOutcomeModuleCommand,
): ProfileApplyRunOutcomeExecution = rejected(
    command,
    ProfileApplyRunOutcomeRejectionReason.TARGET_ADMISSION_REJECTED,
)

private fun rejected(
    command: ProfileApplyRunOutcomeModuleCommand,
    reason: ProfileApplyRunOutcomeRejectionReason,
) = ProfileApplyRunOutcomeExecution(
    moduleResult = ProfileApplyRunOutcomeModuleResult.Rejected(
        commandSource = command.commandSource,
        causalBudgetScope = command.causalBudgetScope,
        causalDepth = ProfileApplyRunOutcomeContract.RESULT_CAUSAL_DEPTH,
        provenance = ProfileApplyRunOutcomeContract.RESULT_PROVENANCE,
        reason = reason,
    ),
    committed = null,
)

private fun applied(
    command: ProfileApplyRunOutcomeModuleCommand,
) = ProfileApplyRunOutcomeExecution(
    moduleResult = ProfileApplyRunOutcomeModuleResult.Applied(
        commandSource = command.commandSource,
        causalBudgetScope = command.causalBudgetScope,
        causalDepth = ProfileApplyRunOutcomeContract.RESULT_CAUSAL_DEPTH,
        provenance = ProfileApplyRunOutcomeContract.RESULT_PROVENANCE,
        settlementId = RunSettlementId(command.commandSource.sourceOperationId),
        profileSnapshotReference = ProfileSnapshotReference(
            profileBallInstanceId = ProfileProjection.BALL_INSTANCE_ID,
            profileCommitRevision = 11uL,
            profileStateSchemaVersion = ProfileProjection.STATE_SCHEMA_VERSION,
        ),
        resultingMatter = 31L,
        resultingLifetimeMatter = 31L,
        highestClearedRebirth = 0,
    ),
    committed = null,
)
