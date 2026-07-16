// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.application.assembly

import kinetickk.features.game.GameRunStartExecution
import kinetickk.features.game.nucleus.protocol.GameRunConfigurationReference
import kinetickk.features.game.nucleus.protocol.GameRunStartCausalScope
import kinetickk.features.game.nucleus.protocol.GameRunStartCommandSource
import kinetickk.features.game.nucleus.protocol.GameRunStartContract
import kinetickk.features.game.nucleus.protocol.GameRunStartModuleCommand
import kinetickk.features.game.nucleus.protocol.GameRunStartModuleResult
import kinetickk.features.game.nucleus.protocol.GameRunStartRejectionReason
import kinetickk.features.profile.nucleus.projection.ProfileProjection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RebirthGameStartRouterTest {
    @Test
    fun oneExactRedeliveryStartsGameAndClearsTheSlot() {
        val delivered = mutableListOf<GameRunStartModuleCommand>()
        val command = gameStartCommand(operationId = 17uL)
        val router = RebirthGameStartRouter(
            execute = { received ->
                delivered += received
                if (delivered.size == 1) admissionRejected(received) else started(received)
            },
        )

        assertNull(router.deliver(command))
        val retained = assertIs<RebirthGameStartRoutingStatus.Retained>(router.status())
        assertEquals(0, retained.retriesUsed)
        assertEquals(1, retained.maxRetries)
        assertTrue(router.hasPending())

        val execution = assertIs<GameRunStartExecution>(router.resume())
        assertIs<GameRunStartModuleResult.Started>(execution.moduleResult)
        assertEquals(listOf(command, command), delivered)
        assertIs<RebirthGameStartRoutingStatus.Started>(router.status())
        assertFalse(router.hasPending())
    }

    @Test
    fun repeatedAdmissionFailureStopsAtTheFiniteLimitAndBackpressuresNewWork() {
        val delivered = mutableListOf<GameRunStartModuleCommand>()
        val command = gameStartCommand(operationId = 23uL)
        val router = RebirthGameStartRouter(
            execute = { received ->
                delivered += received
                admissionRejected(received)
            },
        )

        assertNull(router.deliver(command))
        assertNull(router.resume())

        val stopped = assertIs<RebirthGameStartRoutingStatus.DispatchStopped>(router.status())
        assertEquals(1, stopped.retriesUsed)
        assertEquals(1, stopped.maxRetries)
        assertNull(router.resume())
        assertEquals(listOf(command, command), delivered)
        assertEquals(1, router.backpressure().pending)
        assertEquals(1, router.backpressure().capacity)
        assertFailsWith<IllegalStateException> {
            router.deliver(gameStartCommand(operationId = 24uL))
        }
    }

    @Test
    fun terminalTargetRejectionIsDeliveredToFlowAndDoesNotOccupyTheRetrySlot() {
        val command = gameStartCommand(operationId = 29uL)
        val router = RebirthGameStartRouter(
            execute = { received ->
                rejected(received, GameRunStartRejectionReason.PROFILE_REFERENCE_NOT_CURRENT)
            },
        )

        val execution = assertIs<GameRunStartExecution>(router.deliver(command))

        assertIs<GameRunStartModuleResult.Rejected>(execution.moduleResult)
        val status = assertIs<RebirthGameStartRoutingStatus.Rejected>(router.status())
        assertEquals(
            GameRunStartRejectionReason.PROFILE_REFERENCE_NOT_CURRENT,
            status.result.reason,
        )
        assertFalse(router.hasPending())
    }
}

private fun gameStartCommand(operationId: ULong) = GameRunStartModuleCommand(
    commandSource = GameRunStartCommandSource(
        sourceBallInstanceId = GameRunStartContract.SOURCE_BALL_INSTANCE_ID,
        sourceCommitRevision = 7uL,
        sourceOrdinal = 0u,
        sourceOperationId = operationId,
        sourceOutputKind = GameRunStartContract.SOURCE_OUTPUT_KIND,
        sourceLocalOrdinalOrName = GameRunStartContract.SOURCE_LOCAL_ORDINAL_OR_NAME,
    ),
    causalBudgetScope = GameRunStartCausalScope(
        ownerBallInstanceId = GameRunStartContract.CAUSAL_SCOPE_OWNER_BALL_INSTANCE_ID,
        operationId = operationId,
    ),
    causalDepth = GameRunStartContract.COMMAND_CAUSAL_DEPTH,
    runConfigurationReference = GameRunConfigurationReference(
        profileBallInstanceId = ProfileProjection.BALL_INSTANCE_ID,
        profileCommitRevision = 11uL,
        profileStateSchemaVersion = ProfileProjection.STATE_SCHEMA_VERSION,
        rebirthLevel = 1,
    ),
)

private fun admissionRejected(
    command: GameRunStartModuleCommand,
): GameRunStartExecution = rejected(
    command,
    GameRunStartRejectionReason.TARGET_ADMISSION_REJECTED,
)

private fun rejected(
    command: GameRunStartModuleCommand,
    reason: GameRunStartRejectionReason,
) = GameRunStartExecution(
    moduleResult = GameRunStartModuleResult.Rejected(
        commandSource = command.commandSource,
        causalBudgetScope = command.causalBudgetScope,
        causalDepth = GameRunStartContract.RESULT_CAUSAL_DEPTH,
        provenance = GameRunStartContract.RESULT_PROVENANCE,
        reason = reason,
    ),
    committed = null,
)

private fun started(command: GameRunStartModuleCommand) = GameRunStartExecution(
    moduleResult = GameRunStartModuleResult.Started(
        commandSource = command.commandSource,
        causalBudgetScope = command.causalBudgetScope,
        causalDepth = GameRunStartContract.RESULT_CAUSAL_DEPTH,
        provenance = GameRunStartContract.RESULT_PROVENANCE,
        gameCommitRevision = 19uL,
    ),
    committed = null,
)
