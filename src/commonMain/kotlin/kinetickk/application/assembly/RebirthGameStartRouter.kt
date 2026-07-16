// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.application.assembly

import kinetickk.application.runtime.AdmissionFailure
import kinetickk.features.game.GameRunStartExecution
import kinetickk.features.game.nucleus.protocol.GameRunStartModuleCommand
import kinetickk.features.game.nucleus.protocol.GameRunStartModuleResult
import kinetickk.features.game.nucleus.protocol.GameRunStartRejectionReason

/** Bounded, inspectable delivery status for Rebirth's exact Game-start command. */
sealed interface RebirthGameStartRoutingStatus {
    data object Idle : RebirthGameStartRoutingStatus

    data class Started(
        val result: GameRunStartModuleResult.Started,
    ) : RebirthGameStartRoutingStatus

    data class Retained(
        val command: GameRunStartModuleCommand,
        val retriesUsed: Int,
        val maxRetries: Int,
        val lastResult: GameRunStartModuleResult.Rejected,
    ) : RebirthGameStartRoutingStatus

    data class DispatchStopped(
        val command: GameRunStartModuleCommand,
        val retriesUsed: Int,
        val maxRetries: Int,
        val lastResult: GameRunStartModuleResult.Rejected,
    ) : RebirthGameStartRoutingStatus

    data class Rejected(
        val result: GameRunStartModuleResult.Rejected,
    ) : RebirthGameStartRoutingStatus
}

/**
 * One-slot inline delivery policy for the Profile-advanced -> Game-start boundary.
 *
 * A target admission rejection is not a terminal Rebirth outcome: the exact target-owned command
 * is retained for one later dispatch. This policy is transient and makes no crash-durability or
 * eventual-delivery guarantee.
 */
internal class RebirthGameStartRouter(
    private val execute: (GameRunStartModuleCommand) -> GameRunStartExecution,
    private val maxRetries: Int = 1,
) {
    private data class RetainedGameStart(
        val command: GameRunStartModuleCommand,
        val retriesUsed: Int,
        val lastResult: GameRunStartModuleResult.Rejected,
    )

    private var retained: RetainedGameStart? = null
    private var routingStatus: RebirthGameStartRoutingStatus =
        RebirthGameStartRoutingStatus.Idle

    init {
        require(maxRetries >= 0) { "maxRetries must be non-negative" }
    }

    /** Returns an execution only when its result is safe to deliver to Rebirth Flow. */
    fun deliver(command: GameRunStartModuleCommand): GameRunStartExecution? {
        check(retained == null) {
            "Rebirth Game-start delivery slot cannot be overwritten by another accepted output"
        }
        return execute(command = command, retriesUsed = 0)
    }

    /** Redelivers the exact retained command at most [maxRetries] times. */
    fun resume(): GameRunStartExecution? {
        val pending = retained ?: return null
        if (pending.retriesUsed >= maxRetries) return null
        return execute(
            command = pending.command,
            retriesUsed = pending.retriesUsed + 1,
        )
    }

    fun hasPending(): Boolean = retained != null

    fun status(): RebirthGameStartRoutingStatus = routingStatus

    fun backpressure(): AdmissionFailure.DeliveryBackpressure =
        AdmissionFailure.DeliveryBackpressure(
            scope = CAUSAL_SCOPE,
            pending = if (retained == null) 0 else 1,
            capacity = CAPACITY,
        )

    private fun execute(
        command: GameRunStartModuleCommand,
        retriesUsed: Int,
    ): GameRunStartExecution? {
        val execution = execute(command)
        return when (val result = execution.moduleResult) {
            is GameRunStartModuleResult.Started -> {
                retained = null
                routingStatus = RebirthGameStartRoutingStatus.Started(result)
                execution
            }
            is GameRunStartModuleResult.Rejected -> {
                if (result.reason == GameRunStartRejectionReason.TARGET_ADMISSION_REJECTED) {
                    retained = RetainedGameStart(
                        command = command,
                        retriesUsed = retriesUsed,
                        lastResult = result,
                    )
                    routingStatus = if (retriesUsed >= maxRetries) {
                        RebirthGameStartRoutingStatus.DispatchStopped(
                            command = command,
                            retriesUsed = retriesUsed,
                            maxRetries = maxRetries,
                            lastResult = result,
                        )
                    } else {
                        RebirthGameStartRoutingStatus.Retained(
                            command = command,
                            retriesUsed = retriesUsed,
                            maxRetries = maxRetries,
                            lastResult = result,
                        )
                    }
                    null
                } else {
                    retained = null
                    routingStatus = RebirthGameStartRoutingStatus.Rejected(result)
                    execution
                }
            }
        }
    }

    private companion object {
        const val CAPACITY = 1
        const val CAUSAL_SCOPE = "kinetickk.local/GameAssembly/rebirth-game-start"
    }
}
