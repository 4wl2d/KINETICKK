// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.application.assembly

import kinetickk.application.runtime.AdmissionFailure
import kinetickk.features.profile.ProfileApplyRunOutcomeExecution
import kinetickk.features.profile.nucleus.protocol.ProfileApplyRunOutcomeModuleCommand
import kinetickk.features.profile.nucleus.protocol.ProfileApplyRunOutcomeModuleResult
import kinetickk.features.profile.nucleus.protocol.ProfileApplyRunOutcomeRejectionReason

/** Bounded, inspectable delivery status for Game's irreversible run settlement. */
sealed interface RunSettlementRoutingStatus {
    data object Idle : RunSettlementRoutingStatus

    data class Applied(
        val result: ProfileApplyRunOutcomeModuleResult.Applied,
    ) : RunSettlementRoutingStatus

    data class Retained(
        val command: ProfileApplyRunOutcomeModuleCommand,
        val retriesUsed: Int,
        val maxRetries: Int,
        val lastResult: ProfileApplyRunOutcomeModuleResult.Rejected,
    ) : RunSettlementRoutingStatus

    data class DispatchStopped(
        val command: ProfileApplyRunOutcomeModuleCommand,
        val retriesUsed: Int,
        val maxRetries: Int,
        val lastResult: ProfileApplyRunOutcomeModuleResult.Rejected,
    ) : RunSettlementRoutingStatus

    data class Rejected(
        val result: ProfileApplyRunOutcomeModuleResult.Rejected,
    ) : RunSettlementRoutingStatus
}

/** One-slot inline delivery policy; it makes no crash-durability or eventual-delivery guarantee. */
internal class RunSettlementRouter(
    private val execute: (
        ProfileApplyRunOutcomeModuleCommand,
    ) -> ProfileApplyRunOutcomeExecution,
    private val maxRetries: Int = 1,
) {
    private data class RetainedRunSettlement(
        val command: ProfileApplyRunOutcomeModuleCommand,
        val retriesUsed: Int,
        val lastResult: ProfileApplyRunOutcomeModuleResult.Rejected,
    )

    private var retained: RetainedRunSettlement? = null
    private var routingStatus: RunSettlementRoutingStatus = RunSettlementRoutingStatus.Idle

    init {
        require(maxRetries >= 0) { "maxRetries must be non-negative" }
    }

    fun deliver(command: ProfileApplyRunOutcomeModuleCommand): Boolean {
        check(retained == null) {
            "Run settlement delivery slot cannot be overwritten by another accepted output"
        }
        return execute(command = command, retriesUsed = 0)
    }

    fun resume(): Boolean {
        val pending = retained ?: return false
        if (pending.retriesUsed >= maxRetries) return false
        return execute(
            command = pending.command,
            retriesUsed = pending.retriesUsed + 1,
        )
    }

    fun hasPending(): Boolean = retained != null

    fun status(): RunSettlementRoutingStatus = routingStatus

    fun backpressure(): AdmissionFailure.DeliveryBackpressure =
        AdmissionFailure.DeliveryBackpressure(
            scope = CAUSAL_SCOPE,
            pending = if (retained == null) 0 else 1,
            capacity = CAPACITY,
        )

    private fun execute(
        command: ProfileApplyRunOutcomeModuleCommand,
        retriesUsed: Int,
    ): Boolean = when (val result = execute(command).moduleResult) {
        is ProfileApplyRunOutcomeModuleResult.Applied -> {
            retained = null
            routingStatus = RunSettlementRoutingStatus.Applied(result)
            true
        }
        is ProfileApplyRunOutcomeModuleResult.Rejected -> {
            if (
                result.reason ==
                ProfileApplyRunOutcomeRejectionReason.TARGET_ADMISSION_REJECTED
            ) {
                retained = RetainedRunSettlement(
                    command = command,
                    retriesUsed = retriesUsed,
                    lastResult = result,
                )
                routingStatus = if (retriesUsed >= maxRetries) {
                    RunSettlementRoutingStatus.DispatchStopped(
                        command = command,
                        retriesUsed = retriesUsed,
                        maxRetries = maxRetries,
                        lastResult = result,
                    )
                } else {
                    RunSettlementRoutingStatus.Retained(
                        command = command,
                        retriesUsed = retriesUsed,
                        maxRetries = maxRetries,
                        lastResult = result,
                    )
                }
            } else {
                retained = null
                routingStatus = RunSettlementRoutingStatus.Rejected(result)
            }
            false
        }
    }

    private companion object {
        const val CAPACITY = 1
        const val CAUSAL_SCOPE = "kinetickk.local/GameAssembly/profile-run-settlement"
    }
}
