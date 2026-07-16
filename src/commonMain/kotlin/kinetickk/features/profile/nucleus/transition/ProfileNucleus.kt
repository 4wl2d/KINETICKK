// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.profile.nucleus.transition

import kinetickk.application.runtime.Accepted
import kinetickk.application.runtime.Decider
import kinetickk.application.runtime.Decision
import kinetickk.application.runtime.DecisionResult
import kinetickk.application.runtime.Rejected
import kinetickk.features.profile.nucleus.domain.CoreShapeProgression
import kinetickk.features.profile.nucleus.domain.ItemCatalogFacts
import kinetickk.features.profile.nucleus.domain.MetaUpgradeCatalog
import kinetickk.features.profile.nucleus.domain.RebirthProgression
import kinetickk.features.profile.nucleus.domain.WeaponCatalog
import kinetickk.features.profile.nucleus.protocol.ProfileAdvanceRebirthModuleCommand
import kinetickk.features.profile.nucleus.protocol.ProfileApplyRunOutcomeModuleCommand
import kinetickk.features.profile.nucleus.protocol.ProfileDecisionContext
import kinetickk.features.profile.nucleus.protocol.ProfileIntent
import kinetickk.features.profile.nucleus.protocol.ProfilePulse
import kinetickk.features.profile.nucleus.protocol.ProfileRejection
import kinetickk.features.profile.nucleus.protocol.ProfileSemanticOutput
import kinetickk.features.profile.nucleus.protocol.RunSettlementId
import kinetickk.features.profile.nucleus.protocol.envelopeViolation
import kinetickk.features.profile.nucleus.state.AppliedRunSettlement
import kinetickk.features.profile.nucleus.state.PlayerProfileValues
import kinetickk.features.profile.nucleus.state.ProfileState

internal fun initialProfileState(
    values: PlayerProfileValues,
): ProfileState = ProfileState(values = values)

internal class ProfileNucleus : Decider<
    ProfileState,
    ProfilePulse,
    ProfileDecisionContext,
    ProfileSemanticOutput,
> {
    override fun decide(
        state: ProfileState,
        pulse: ProfilePulse,
        context: ProfileDecisionContext,
    ): DecisionResult<ProfileState, ProfileSemanticOutput> {
        if (context.transitionArtifact != TRANSITION_ARTIFACT) {
            return Rejected(ProfileRejection.InvalidContext("transitionArtifact", "unsupported artifact"))
        }
        if (context.operationId.value == 0uL) {
            return Rejected(ProfileRejection.InvalidContext("operationId", "must be reserved"))
        }
        return when (pulse) {
            is ProfileIntent.PurchaseMetaUpgrade -> purchaseMetaUpgrade(state, pulse)
            is ProfileIntent.PurchaseOrSelectWeapon -> purchaseOrSelectWeapon(state, pulse)
            is ProfileIntent.SelectCoreShape -> selectCoreShape(state, pulse)
            is ProfileIntent.RecordItemDiscoveries -> recordItemDiscoveries(state, pulse)
            is ProfileIntent.ApplyRunOutcome -> applyRunOutcome(state, pulse)
            is ProfileIntent.AdvanceRebirth -> advanceRebirth(state, pulse)
            is ProfileAdvanceRebirthModuleCommand -> {
                validateModuleCommandContext(pulse, context)?.let { return Rejected(it) }
                advanceRebirth(
                    state,
                    ProfileIntent.AdvanceRebirth(expectedLevel = pulse.expectedLevel),
                )
            }
            is ProfileApplyRunOutcomeModuleCommand -> {
                validateModuleCommandContext(pulse, context)?.let { return Rejected(it) }
                applyRunOutcome(
                    state,
                    ProfileIntent.ApplyRunOutcome(
                        settlementId = RunSettlementId(pulse.commandSource.sourceOperationId),
                        matterEarned = pulse.matterEarned,
                        clearedRebirthLevel = pulse.clearedRebirthLevel,
                    ),
                )
            }
        }
    }

    private fun validateModuleCommandContext(
        command: ProfileAdvanceRebirthModuleCommand,
        context: ProfileDecisionContext,
    ): ProfileRejection? = command.envelopeViolation()
        ?: validateImportedCausalContext(
            sourceOperationId = command.commandSource.sourceOperationId,
            scopeOwner = command.causalBudgetScope.ownerBallInstanceId,
            scopeOperationId = command.causalBudgetScope.operationId,
            commandCausalDepth = command.causalDepth,
            context = context,
        )

    private fun validateModuleCommandContext(
        command: ProfileApplyRunOutcomeModuleCommand,
        context: ProfileDecisionContext,
    ): ProfileRejection? = command.envelopeViolation()
        ?: validateImportedCausalContext(
            sourceOperationId = command.commandSource.sourceOperationId,
            scopeOwner = command.causalBudgetScope.ownerBallInstanceId,
            scopeOperationId = command.causalBudgetScope.operationId,
            commandCausalDepth = command.causalDepth,
            context = context,
        )

    private fun validateImportedCausalContext(
        sourceOperationId: ULong,
        scopeOwner: String,
        scopeOperationId: ULong,
        commandCausalDepth: Int,
        context: ProfileDecisionContext,
    ): ProfileRejection? {
        if (context.operationId.value != sourceOperationId) {
            return ProfileRejection.InvalidModuleCommandCausalContext(
                field = "operationId",
                reason = "must match the source operation",
            )
        }
        if (
            context.causalBudgetScopeOwnerBallInstanceId != scopeOwner ||
            context.causalBudgetScopeOperationId != scopeOperationId
        ) {
            return ProfileRejection.InvalidModuleCommandCausalContext(
                field = "causalBudgetScope",
                reason = "must preserve the imported causal scope",
            )
        }
        if (context.causalDepth != commandCausalDepth) {
            return ProfileRejection.InvalidModuleCommandCausalContext(
                field = "causalDepth",
                reason = "must preserve the command depth",
            )
        }
        return null
    }

    private fun purchaseMetaUpgrade(
        state: ProfileState,
        intent: ProfileIntent.PurchaseMetaUpgrade,
    ): DecisionResult<ProfileState, ProfileSemanticOutput> {
        val definition = MetaUpgradeCatalog.byId(intent.upgrade)
        val currentRank = state.values.metaRanks[intent.upgrade.ordinal]
        if (currentRank >= definition.maxRanks) {
            return Rejected(ProfileRejection.MetaUpgradeMaxed(intent.upgrade))
        }
        val cost = definition.cost(currentRank).toLong()
        if (state.values.matter < cost) {
            return Rejected(ProfileRejection.InsufficientMatter(cost, state.values.matter))
        }
        val nextRanks = state.values.metaRanks.mapIndexed { index, rank ->
            if (index == intent.upgrade.ordinal) rank + 1 else rank
        }
        return accepted(
            state = state,
            values = state.values.updated(
                matter = state.values.matter - cost,
                metaRanks = nextRanks,
            ),
        )
    }

    private fun purchaseOrSelectWeapon(
        state: ProfileState,
        intent: ProfileIntent.PurchaseOrSelectWeapon,
    ): DecisionResult<ProfileState, ProfileSemanticOutput> {
        val alreadyUnlocked = intent.weapon in state.values.unlockedWeapons
        val cost = if (alreadyUnlocked) 0L else WeaponCatalog.byId(intent.weapon).permanentUnlockCost.toLong()
        if (state.values.matter < cost) {
            return Rejected(ProfileRejection.InsufficientMatter(cost, state.values.matter))
        }
        val nextUnlocked = if (alreadyUnlocked) {
            state.values.unlockedWeapons
        } else {
            state.values.unlockedWeapons + intent.weapon
        }
        return accepted(
            state = state,
            values = state.values.updated(
                matter = state.values.matter - cost,
                selectedWeapon = intent.weapon,
                unlockedWeapons = nextUnlocked,
            ),
        )
    }

    private fun selectCoreShape(
        state: ProfileState,
        intent: ProfileIntent.SelectCoreShape,
    ): DecisionResult<ProfileState, ProfileSemanticOutput> {
        val requiredLifetime = CoreShapeProgression.requiredLifetimeMatter(intent.shape)
        if (state.values.lifetimeMatter < requiredLifetime) {
            return Rejected(
                ProfileRejection.CoreShapeLocked(
                    shape = intent.shape,
                    requiredLifetimeMatter = requiredLifetime,
                    actualLifetimeMatter = state.values.lifetimeMatter,
                ),
            )
        }
        return accepted(
            state = state,
            values = state.values.updated(selectedCoreShape = intent.shape),
        )
    }

    private fun recordItemDiscoveries(
        state: ProfileState,
        intent: ProfileIntent.RecordItemDiscoveries,
    ): DecisionResult<ProfileState, ProfileSemanticOutput> {
        intent.itemIds.firstOrNull { it !in 0 until ItemCatalogFacts.ITEM_COUNT }?.let { invalidId ->
            return Rejected(ProfileRejection.InvalidDiscoveryId(invalidId))
        }
        return accepted(
            state = state,
            values = state.values.updated(
                discoveredItemIds = state.values.discoveredItemIds + intent.itemIds,
            ),
        )
    }

    private fun applyRunOutcome(
        state: ProfileState,
        intent: ProfileIntent.ApplyRunOutcome,
    ): DecisionResult<ProfileState, ProfileSemanticOutput> {
        if (intent.settlementId.value == 0uL) {
            return Rejected(ProfileRejection.InvalidRunSettlementId(intent.settlementId))
        }
        val proposed = AppliedRunSettlement(
            settlementId = intent.settlementId,
            matterEarned = intent.matterEarned,
            clearedRebirthLevel = intent.clearedRebirthLevel,
        )
        state.lastRunSettlement?.let { latest ->
            if (intent.settlementId == latest.settlementId) {
                return if (proposed == latest) {
                    accepted(state = state, values = state.values, settlement = latest)
                } else {
                    Rejected(ProfileRejection.ConflictingRunSettlement(intent.settlementId))
                }
            }
            if (intent.settlementId.value < latest.settlementId.value) {
                return Rejected(
                    ProfileRejection.StaleRunSettlement(
                        received = intent.settlementId,
                        latest = latest.settlementId,
                    ),
                )
            }
        }
        if (intent.matterEarned < 0L) {
            return Rejected(ProfileRejection.InvalidRunMatter(intent.matterEarned))
        }
        intent.clearedRebirthLevel?.let { clearedLevel ->
            if (clearedLevel !in 0..state.values.rebirthLevel) {
                return Rejected(
                    ProfileRejection.InvalidClearedRebirthLevel(
                        received = clearedLevel,
                        currentRebirthLevel = state.values.rebirthLevel,
                    ),
                )
            }
        }
        val nextHighestCleared = intent.clearedRebirthLevel?.let { clearedLevel ->
            maxOf(state.values.highestClearedRebirth, clearedLevel)
        } ?: state.values.highestClearedRebirth
        return accepted(
            state = state,
            values = state.values.updated(
                matter = saturatedAdd(state.values.matter, intent.matterEarned),
                lifetimeMatter = saturatedAdd(state.values.lifetimeMatter, intent.matterEarned),
                highestClearedRebirth = nextHighestCleared,
            ),
            settlement = proposed,
        )
    }

    private fun advanceRebirth(
        state: ProfileState,
        intent: ProfileIntent.AdvanceRebirth,
    ): DecisionResult<ProfileState, ProfileSemanticOutput> {
        if (intent.expectedLevel != state.values.rebirthLevel) {
            return Rejected(
                ProfileRejection.RebirthLevelMismatch(
                    expected = intent.expectedLevel,
                    actual = state.values.rebirthLevel,
                ),
            )
        }
        if (state.values.rebirthLevel >= RebirthProgression.MAX_LEVEL) {
            return Rejected(ProfileRejection.RebirthMaximumReached(state.values.rebirthLevel))
        }
        if (state.values.highestClearedRebirth < state.values.rebirthLevel) {
            return Rejected(
                ProfileRejection.RebirthNotCleared(
                    level = state.values.rebirthLevel,
                    highestCleared = state.values.highestClearedRebirth,
                ),
            )
        }
        return accepted(
            state = state,
            values = state.values.updated(rebirthLevel = state.values.rebirthLevel + 1),
        )
    }

    private fun accepted(
        state: ProfileState,
        values: PlayerProfileValues,
        settlement: AppliedRunSettlement? = state.lastRunSettlement,
    ): Accepted<ProfileState, ProfileSemanticOutput> = Accepted(
        Decision(
            nextState = ProfileState(
                values = values,
                lastRunSettlement = settlement,
                transitionSteps = 1,
            ),
            outputs = emptyList(),
        ),
    )

    private companion object {
        const val TRANSITION_ARTIFACT = "profile-v1"
    }
}

private fun saturatedAdd(left: Long, right: Long): Long =
    if (right > Long.MAX_VALUE - left) Long.MAX_VALUE else left + right
