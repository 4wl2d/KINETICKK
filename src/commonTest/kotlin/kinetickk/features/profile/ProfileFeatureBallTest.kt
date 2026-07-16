// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.profile

import kinetickk.application.runtime.AdmissionFailure
import kinetickk.application.runtime.MandatoryDecisionLimit
import kinetickk.features.profile.nucleus.domain.CoreShape
import kinetickk.features.profile.nucleus.domain.CoreShapeProgression
import kinetickk.features.profile.nucleus.domain.ItemCatalogFacts
import kinetickk.features.profile.nucleus.domain.MetaUpgradeCatalog
import kinetickk.features.profile.nucleus.domain.MetaUpgradeId
import kinetickk.features.profile.nucleus.domain.RebirthDirective
import kinetickk.features.profile.nucleus.domain.RebirthProgression
import kinetickk.features.profile.nucleus.domain.WeaponCatalog
import kinetickk.features.profile.nucleus.domain.WeaponId
import kinetickk.features.profile.nucleus.projection.ProfileProjection
import kinetickk.features.profile.nucleus.protocol.ProfileAdvanceRebirthContract
import kinetickk.features.profile.nucleus.protocol.ProfileAdvanceRebirthModuleCommand
import kinetickk.features.profile.nucleus.protocol.ProfileAdvanceRebirthModuleResult
import kinetickk.features.profile.nucleus.protocol.ProfileAdvanceRebirthRejectionReason
import kinetickk.features.profile.nucleus.protocol.ProfileApplyRunOutcomeContract
import kinetickk.features.profile.nucleus.protocol.ProfileApplyRunOutcomeModuleCommand
import kinetickk.features.profile.nucleus.protocol.ProfileApplyRunOutcomeModuleResult
import kinetickk.features.profile.nucleus.protocol.ProfileApplyRunOutcomeRejectionReason
import kinetickk.features.profile.nucleus.protocol.ProfileCausalScope
import kinetickk.features.profile.nucleus.protocol.ProfileCommandSource
import kinetickk.features.profile.nucleus.protocol.ProfileIntent
import kinetickk.features.profile.nucleus.protocol.ProfileQuery
import kinetickk.features.profile.nucleus.protocol.ProfileQueryResult
import kinetickk.features.profile.nucleus.protocol.ProfileRejection
import kinetickk.features.profile.nucleus.protocol.RunSettlementId
import kinetickk.features.profile.nucleus.read.ProfileConsistencyStamp
import kinetickk.features.profile.nucleus.state.PlayerProfileValues
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProfileFeatureBallTest {
    @Test
    fun profileDomainContractsRetainStableIdentityOrderCostsAndRebirthTuning() {
        assertEquals(listOf("ORB", "PRISM", "SHARD"), CoreShape.entries.map { it.name })
        assertEquals(
            listOf(0L, 25L, 90L),
            CoreShape.entries.map(CoreShapeProgression::requiredLifetimeMatter),
        )
        assertEquals(
            listOf(
                "FLUX_WAKE",
                "MORNINGSTAR",
                "PHASE_LATTICE",
                "NULL_LANCE",
                "GRAVITY_MINES",
                "ION_SWARM",
                "RIFT_BLADES",
                "ARC_COIL",
                "QUASAR_CANNON",
                "ENTROPY_FIELD",
                "SINGULARITY_SPEAR",
                "PRISM_RELAY",
            ),
            WeaponId.entries.map { it.name },
        )
        assertEquals(
            listOf(0, 25, 55, 95, 145, 215, 305, 430, 610, 860, 1_200, 1_650),
            WeaponCatalog.all.map { it.permanentUnlockCost },
        )
        assertEquals(
            listOf(
                "CORE_INTEGRITY",
                "KINETIC_AMPLIFIER",
                "MAGNETIC_RESONANCE",
                "CRYO_VENTS",
                "DASH_CAPACITOR",
                "SALVAGE_PROTOCOL",
                "DATA_ARCHIVE",
                "ARMORY_LICENSE",
            ),
            MetaUpgradeId.entries.map { it.name },
        )
        assertEquals(listOf(10, 10, 8, 8, 8, 10, 10, 12), MetaUpgradeCatalog.all.map { it.maxRanks })
        assertEquals(listOf(18, 22, 24, 26, 30, 34, 38, 45), MetaUpgradeCatalog.all.map { it.baseCost })
        assertEquals(ItemCatalogFacts.ITEM_COUNT, 400)

        val baseline = RebirthProgression.profile(0)
        assertEquals(RebirthDirective.BASELINE, baseline.directive)
        assertEquals(5, baseline.openingEnemyCount)
        assertEquals(1f, baseline.enemyCapMultiplier)
        assertEquals(1f, baseline.playerPowerMultiplier)
        assertEquals(baseline, RebirthProgression.profile(-1))

        val maximum = RebirthProgression.profile(RebirthProgression.MAX_LEVEL)
        assertEquals(RebirthDirective.SWARM, maximum.directive)
        assertEquals(10, maximum.openingEnemyCount)
        assertEquals(1.84f, maximum.enemyCapMultiplier, 0.0001f)
        assertEquals(4.06f, maximum.enemyHealthMultiplier, 0.0001f)
        assertEquals(2.23f, maximum.matterGainMultiplier, 0.0001f)
        assertEquals(2, maximum.bonusRerolls)
        assertEquals(maximum, RebirthProgression.profile(Int.MAX_VALUE))
    }

    @Test
    fun playerProfileValuesOwnImmutableCollectionsAndEnforceConstructionInvariants() {
        val ranks = MutableList(MetaUpgradeId.entries.size) { 0 }
        val weapons = mutableSetOf(WeaponId.FLUX_WAKE)
        val discoveries = mutableSetOf(1, 2)
        val values = PlayerProfileValues(
            matter = 100L,
            lifetimeMatter = 100L,
            metaRanks = ranks,
            unlockedWeapons = weapons,
            discoveredItemIds = discoveries,
        )

        ranks[0] = 1
        weapons += WeaponId.MORNINGSTAR
        discoveries.clear()

        assertEquals(0, values.metaRanks[0])
        assertEquals(setOf(WeaponId.FLUX_WAKE), values.unlockedWeapons)
        assertEquals(setOf(1, 2), values.discoveredItemIds)
        assertIs<ImmutableList<Int>>(values.metaRanks)
        assertIs<ImmutableSet<WeaponId>>(values.unlockedWeapons)
        assertFalse((values.metaRanks as Any) is MutableList<*>)
        assertFalse((values.unlockedWeapons as Any) is MutableSet<*>)

        assertFailsWith<IllegalArgumentException> {
            PlayerProfileValues(selectedWeapon = WeaponId.MORNINGSTAR)
        }
        assertFailsWith<IllegalArgumentException> {
            PlayerProfileValues(matter = 10L, lifetimeMatter = 9L)
        }
        assertFailsWith<IllegalArgumentException> {
            PlayerProfileValues(selectedCoreShape = CoreShape.PRISM)
        }
    }

    @Test
    fun metaPurchaseChecksAffordabilityAndAddsExactlyOneBoundedRank() {
        val upgrade = MetaUpgradeId.CORE_INTEGRITY
        val definition = MetaUpgradeCatalog.byId(upgrade)
        val ball = ProfileFeatureBall.create(
            PlayerProfileValues(matter = 1_000L, lifetimeMatter = 1_000L),
        )

        val committed = assertIs<ProfileDispatchResult.Committed>(
            ball.dispatch(ProfileIntent.PurchaseMetaUpgrade(upgrade)),
        )

        assertEquals(1, committed.profileRead.payload.metaLevel(upgrade))
        assertEquals(1_000L - definition.cost(0), committed.profileRead.payload.matter)

        val poorBall = ProfileFeatureBall.create()
        val rejected = assertIs<ProfileDispatchResult.DecisionRejected>(
            poorBall.dispatch(ProfileIntent.PurchaseMetaUpgrade(upgrade)),
        )
        assertIs<ProfileRejection.InsufficientMatter>(rejected.reason)
        assertEquals(0uL, poorBall.profileRead().consistencyStamp.commitRevision)

        val maxedRanks = List(MetaUpgradeId.entries.size) { index ->
            if (index == upgrade.ordinal) definition.maxRanks else 0
        }
        val maxedBall = ProfileFeatureBall.create(
            PlayerProfileValues(
                matter = 1_000L,
                lifetimeMatter = 1_000L,
                metaRanks = maxedRanks,
            ),
        )
        val maxed = assertIs<ProfileDispatchResult.DecisionRejected>(
            maxedBall.dispatch(ProfileIntent.PurchaseMetaUpgrade(upgrade)),
        )
        assertEquals(ProfileRejection.MetaUpgradeMaxed(upgrade), maxed.reason)
    }

    @Test
    fun permanentWeaponPurchaseChargesOnceAndAlwaysSelectsTheUnlockedWeapon() {
        val weapon = WeaponId.SINGULARITY_SPEAR
        val cost = WeaponCatalog.byId(weapon).permanentUnlockCost.toLong()
        val ball = ProfileFeatureBall.create(
            PlayerProfileValues(matter = 10_000L, lifetimeMatter = 10_000L),
        )

        val first = assertIs<ProfileDispatchResult.Committed>(
            ball.dispatch(ProfileIntent.PurchaseOrSelectWeapon(weapon)),
        )
        assertEquals(10_000L - cost, first.profileRead.payload.matter)
        assertEquals(weapon, first.profileRead.payload.selectedWeapon)
        assertTrue(weapon in first.profileRead.payload.unlockedWeapons)

        val second = assertIs<ProfileDispatchResult.Committed>(
            ball.dispatch(ProfileIntent.PurchaseOrSelectWeapon(weapon)),
        )
        assertEquals(first.profileRead.payload.matter, second.profileRead.payload.matter)
        assertEquals(2uL, second.commitRevision)
    }

    @Test
    fun coreShapeSelectionUsesLifetimeMatterRatherThanSpendableMatter() {
        val ball = ProfileFeatureBall.create(
            PlayerProfileValues(matter = 0L, lifetimeMatter = 24L),
        )

        val locked = assertIs<ProfileDispatchResult.DecisionRejected>(
            ball.dispatch(ProfileIntent.SelectCoreShape(CoreShape.PRISM)),
        )
        assertIs<ProfileRejection.CoreShapeLocked>(locked.reason)

        assertIs<ProfileDispatchResult.Committed>(
            ball.dispatch(
                ProfileIntent.ApplyRunOutcome(
                    settlementId = RunSettlementId(1uL),
                    matterEarned = 1L,
                ),
            ),
        )
        val selected = assertIs<ProfileDispatchResult.Committed>(
            ball.dispatch(ProfileIntent.SelectCoreShape(CoreShape.PRISM)),
        )
        assertEquals(CoreShape.PRISM, selected.profileRead.payload.selectedCoreShape)
        assertEquals(1L, selected.profileRead.payload.matter)
        assertEquals(25L, selected.profileRead.payload.lifetimeMatter)
    }

    @Test
    fun runSettlementIsMonotonicIdempotentAndRejectsConflictAndStaleIdentity() {
        val ball = ProfileFeatureBall.create(
            PlayerProfileValues(matter = 100L, lifetimeMatter = 200L),
        )
        val firstIntent = ProfileIntent.ApplyRunOutcome(
            settlementId = RunSettlementId(1uL),
            matterEarned = 25L,
            clearedRebirthLevel = 0,
        )

        val first = assertIs<ProfileDispatchResult.Committed>(ball.dispatch(firstIntent))
        assertEquals(125L, first.profileRead.payload.matter)
        assertEquals(225L, first.profileRead.payload.lifetimeMatter)
        assertEquals(0, first.profileRead.payload.highestClearedRebirth)

        val duplicate = assertIs<ProfileDispatchResult.Committed>(ball.dispatch(firstIntent))
        assertEquals(first.profileRead.payload, duplicate.profileRead.payload)
        assertEquals(2uL, duplicate.commitRevision)

        val conflict = assertIs<ProfileDispatchResult.DecisionRejected>(
            ball.dispatch(firstIntent.copy(matterEarned = 26L)),
        )
        assertEquals(
            ProfileRejection.ConflictingRunSettlement(RunSettlementId(1uL)),
            conflict.reason,
        )
        assertEquals(2uL, ball.profileRead().consistencyStamp.commitRevision)

        assertIs<ProfileDispatchResult.Committed>(
            ball.dispatch(
                ProfileIntent.ApplyRunOutcome(
                    settlementId = RunSettlementId(3uL),
                    matterEarned = 5L,
                ),
            ),
        )
        val stale = assertIs<ProfileDispatchResult.DecisionRejected>(
            ball.dispatch(
                ProfileIntent.ApplyRunOutcome(
                    settlementId = RunSettlementId(2uL),
                    matterEarned = 10L,
                ),
            ),
        )
        assertEquals(
            ProfileRejection.StaleRunSettlement(
                received = RunSettlementId(2uL),
                latest = RunSettlementId(3uL),
            ),
            stale.reason,
        )
        assertEquals(130L, ball.profileRead().payload.matter)

        val saturatingBall = ProfileFeatureBall.create(
            PlayerProfileValues(
                matter = Long.MAX_VALUE - 5L,
                lifetimeMatter = Long.MAX_VALUE - 1L,
            ),
        )
        val saturated = assertIs<ProfileDispatchResult.Committed>(
            saturatingBall.dispatch(
                ProfileIntent.ApplyRunOutcome(
                    settlementId = RunSettlementId(1uL),
                    matterEarned = 10L,
                ),
            ),
        )
        assertEquals(Long.MAX_VALUE, saturated.profileRead.payload.matter)
        assertEquals(Long.MAX_VALUE, saturated.profileRead.payload.lifetimeMatter)
    }

    @Test
    fun rebirthRequiresExactExpectedClearedLevelAndPreservesPermanentProgress() {
        val upgrade = MetaUpgradeId.CORE_INTEGRITY
        val weapon = WeaponId.MORNINGSTAR
        val ball = ProfileFeatureBall.create(
            PlayerProfileValues(matter = 10_000L, lifetimeMatter = 10_000L),
        )
        assertIs<ProfileDispatchResult.Committed>(
            ball.dispatch(ProfileIntent.PurchaseMetaUpgrade(upgrade)),
        )
        assertIs<ProfileDispatchResult.Committed>(
            ball.dispatch(ProfileIntent.PurchaseOrSelectWeapon(weapon)),
        )

        val uncleared = assertIs<ProfileDispatchResult.DecisionRejected>(
            ball.dispatch(ProfileIntent.AdvanceRebirth(expectedLevel = 0)),
        )
        assertIs<ProfileRejection.RebirthNotCleared>(uncleared.reason)

        assertIs<ProfileDispatchResult.Committed>(
            ball.dispatch(
                ProfileIntent.ApplyRunOutcome(
                    settlementId = RunSettlementId(1uL),
                    matterEarned = 0L,
                    clearedRebirthLevel = 0,
                ),
            ),
        )
        val advanced = assertIs<ProfileDispatchResult.Committed>(
            ball.dispatch(ProfileIntent.AdvanceRebirth(expectedLevel = 0)),
        )
        assertEquals(1, advanced.profileRead.payload.rebirthLevel)
        assertEquals(0, advanced.profileRead.payload.highestClearedRebirth)
        assertEquals(1, advanced.profileRead.payload.metaLevel(upgrade))
        assertEquals(weapon, advanced.profileRead.payload.selectedWeapon)

        val repeated = assertIs<ProfileDispatchResult.DecisionRejected>(
            ball.dispatch(ProfileIntent.AdvanceRebirth(expectedLevel = 0)),
        )
        assertEquals(ProfileRejection.RebirthLevelMismatch(0, 1), repeated.reason)
    }

    @Test
    fun targetOwnedRebirthAdvanceReturnsExactProfileProvenanceAndDeduplicatesDelivery() {
        val ball = ProfileFeatureBall.create(
            PlayerProfileValues(highestClearedRebirth = 0),
        )
        val command = profileAdvanceCommand(
            sourceCommitRevision = 3uL,
            sourceOperationId = 17uL,
            expectedLevel = 0,
        )

        val first = ball.execute(command)

        val advanced = assertIs<ProfileAdvanceRebirthModuleResult.Advanced>(first.moduleResult)
        assertEquals(command.commandSource, advanced.commandSource)
        assertEquals(command.causalBudgetScope, advanced.causalBudgetScope)
        assertEquals(ProfileAdvanceRebirthContract.RESULT_CAUSAL_DEPTH, advanced.causalDepth)
        assertEquals(ProfileAdvanceRebirthContract.RESULT_PROVENANCE, advanced.provenance)
        assertEquals(1, advanced.newLevel)
        assertEquals(ProfileProjection.BALL_INSTANCE_ID, advanced.profileSnapshotReference.profileBallInstanceId)
        assertEquals(1uL, advanced.profileSnapshotReference.profileCommitRevision)
        assertEquals(ProfileProjection.STATE_SCHEMA_VERSION, advanced.profileSnapshotReference.profileStateSchemaVersion)
        assertEquals(1uL, assertIs<ProfileDispatchResult.Committed>(first.committed).commitRevision)

        val duplicate = ball.execute(command)
        assertSame(first.moduleResult, duplicate.moduleResult)
        assertEquals(null, duplicate.committed)
        assertEquals(1uL, ball.profileRead().consistencyStamp.commitRevision)

        val conflicting = ball.execute(command.copy(expectedLevel = 1))
        val conflictResult =
            assertIs<ProfileAdvanceRebirthModuleResult.Rejected>(conflicting.moduleResult)
        assertEquals(
            ProfileAdvanceRebirthRejectionReason.CONFLICTING_REDELIVERY,
            conflictResult.reason,
        )
        assertEquals(1uL, ball.profileRead().consistencyStamp.commitRevision)
    }

    @Test
    fun targetOwnedRebirthAdvanceRetainsExactDecisionAndBoundsOldDeliveryHistory() {
        val ball = ProfileFeatureBall.create()
        val firstCommand = profileAdvanceCommand(
            sourceCommitRevision = 1uL,
            sourceOperationId = 1uL,
            expectedLevel = 0,
        )
        val first = ball.execute(firstCommand)
        assertEquals(
            ProfileAdvanceRebirthRejectionReason.CURRENT_LEVEL_NOT_CLEARED,
            assertIs<ProfileAdvanceRebirthModuleResult.Rejected>(first.moduleResult).reason,
        )

        assertIs<ProfileDispatchResult.Committed>(
            ball.dispatch(
                ProfileIntent.ApplyRunOutcome(
                    settlementId = RunSettlementId(1uL),
                    matterEarned = 0L,
                    clearedRebirthLevel = 0,
                ),
            ),
        )
        val duplicate = ball.execute(firstCommand)
        assertSame(first.moduleResult, duplicate.moduleResult)
        assertEquals(null, duplicate.committed)

        val next = ball.execute(
            profileAdvanceCommand(
                sourceCommitRevision = 2uL,
                sourceOperationId = 2uL,
                expectedLevel = 0,
            ),
        )
        assertIs<ProfileAdvanceRebirthModuleResult.Advanced>(next.moduleResult)

        val stale = ball.execute(firstCommand)
        assertEquals(
            ProfileAdvanceRebirthRejectionReason.STALE_COMMAND_SOURCE,
            assertIs<ProfileAdvanceRebirthModuleResult.Rejected>(stale.moduleResult).reason,
        )
        assertEquals(1, ball.profileRead().payload.rebirthLevel)
    }

    @Test
    fun targetOwnedRebirthAdvanceRejectsMalformedSourceAndCausalScopeWithoutMutation() {
        val ball = ProfileFeatureBall.create(PlayerProfileValues(highestClearedRebirth = 0))
        val valid = profileAdvanceCommand(1uL, 1uL, 0)

        val malformedSource = ball.execute(
            valid.copy(
                commandSource = valid.commandSource.copy(
                    sourceLocalOrdinalOrName = "different-output",
                ),
            ),
        )
        assertEquals(
            ProfileAdvanceRebirthRejectionReason.INVALID_COMMAND_SOURCE,
            assertIs<ProfileAdvanceRebirthModuleResult.Rejected>(malformedSource.moduleResult).reason,
        )

        val malformedScope = ball.execute(
            valid.copy(
                causalBudgetScope = valid.causalBudgetScope.copy(operationId = 2uL),
            ),
        )
        assertEquals(
            ProfileAdvanceRebirthRejectionReason.INVALID_CAUSAL_CONTEXT,
            assertIs<ProfileAdvanceRebirthModuleResult.Rejected>(malformedScope.moduleResult).reason,
        )
        assertEquals(0uL, ball.profileRead().consistencyStamp.commitRevision)
    }

    @Test
    fun targetOwnedRunOutcomeSettlesOnceAndRejectsConflictingOrStaleRedelivery() {
        val ball = ProfileFeatureBall.create(
            PlayerProfileValues(matter = 100L, lifetimeMatter = 200L),
        )
        val command = profileRunOutcomeCommand(
            sourceCommitRevision = 10uL,
            sourceOperationId = 41uL,
            matterEarned = 25L,
            clearedRebirthLevel = 0,
        )

        val first = ball.execute(command)

        val applied = assertIs<ProfileApplyRunOutcomeModuleResult.Applied>(first.moduleResult)
        assertEquals(command.commandSource, applied.commandSource)
        assertEquals(command.causalBudgetScope, applied.causalBudgetScope)
        assertEquals(ProfileApplyRunOutcomeContract.RESULT_CAUSAL_DEPTH, applied.causalDepth)
        assertEquals(ProfileApplyRunOutcomeContract.RESULT_PROVENANCE, applied.provenance)
        assertEquals(RunSettlementId(41uL), applied.settlementId)
        assertEquals(125L, applied.resultingMatter)
        assertEquals(225L, applied.resultingLifetimeMatter)
        assertEquals(0, applied.highestClearedRebirth)
        assertEquals(1uL, applied.profileSnapshotReference.profileCommitRevision)

        val duplicate = ball.execute(command)
        assertSame(first.moduleResult, duplicate.moduleResult)
        assertEquals(null, duplicate.committed)
        assertEquals(1uL, ball.profileRead().consistencyStamp.commitRevision)

        val conflict = ball.execute(command.copy(matterEarned = 26L))
        assertEquals(
            ProfileApplyRunOutcomeRejectionReason.CONFLICTING_REDELIVERY,
            assertIs<ProfileApplyRunOutcomeModuleResult.Rejected>(conflict.moduleResult).reason,
        )
        assertEquals(125L, ball.profileRead().payload.matter)

        val next = ball.execute(
            profileRunOutcomeCommand(
                sourceCommitRevision = 11uL,
                sourceOperationId = 42uL,
                matterEarned = 5L,
            ),
        )
        assertIs<ProfileApplyRunOutcomeModuleResult.Applied>(next.moduleResult)
        assertEquals(130L, ball.profileRead().payload.matter)

        val stale = ball.execute(command)
        assertEquals(
            ProfileApplyRunOutcomeRejectionReason.STALE_COMMAND_SOURCE,
            assertIs<ProfileApplyRunOutcomeModuleResult.Rejected>(stale.moduleResult).reason,
        )
        assertEquals(2uL, ball.profileRead().consistencyStamp.commitRevision)
    }

    @Test
    fun targetOwnedRunOutcomeRejectsMalformedSourceAndCausalScopeWithoutMutation() {
        val ball = ProfileFeatureBall.create()
        val valid = profileRunOutcomeCommand(1uL, 1uL, matterEarned = 10L)

        val malformedSource = ball.execute(
            valid.copy(
                commandSource = valid.commandSource.copy(sourceOrdinal = 2u),
            ),
        )
        assertEquals(
            ProfileApplyRunOutcomeRejectionReason.INVALID_COMMAND_SOURCE,
            assertIs<ProfileApplyRunOutcomeModuleResult.Rejected>(malformedSource.moduleResult).reason,
        )

        val malformedScope = ball.execute(
            valid.copy(
                causalBudgetScope = valid.causalBudgetScope.copy(operationId = 2uL),
            ),
        )
        assertEquals(
            ProfileApplyRunOutcomeRejectionReason.INVALID_CAUSAL_CONTEXT,
            assertIs<ProfileApplyRunOutcomeModuleResult.Rejected>(malformedScope.moduleResult).reason,
        )
        assertEquals(0uL, ball.profileRead().consistencyStamp.commitRevision)
    }

    @Test
    fun targetAdmissionRejectionDoesNotConsumeTheSettlementDeliverySlot() {
        val ball = ProfileFeatureBall.create()
        val valid = profileRunOutcomeCommand(1uL, 1uL, matterEarned = 10L)
        val overDepth = valid.copy(
            causalDepth = ProfileFeatureBall.LIMITS.maxCausalDepth + 1,
        )

        val admissionRejected = ball.execute(overDepth)
        assertEquals(
            ProfileApplyRunOutcomeRejectionReason.TARGET_ADMISSION_REJECTED,
            assertIs<ProfileApplyRunOutcomeModuleResult.Rejected>(
                admissionRejected.moduleResult,
            ).reason,
        )
        assertEquals(0uL, ball.profileRead().consistencyStamp.commitRevision)

        val retried = ball.execute(valid)
        assertIs<ProfileApplyRunOutcomeModuleResult.Applied>(retried.moduleResult)
        assertEquals(10L, ball.profileRead().payload.matter)
        assertEquals(1uL, ball.profileRead().consistencyStamp.commitRevision)
    }

    @Test
    fun queriesReturnExactProfileStampAndNeverAdvanceRevision() {
        val ball = ProfileFeatureBall.create(
            PlayerProfileValues(
                matter = 100L,
                lifetimeMatter = 100L,
                unlockedWeapons = setOf(WeaponId.FLUX_WAKE, WeaponId.MORNINGSTAR),
                selectedWeapon = WeaponId.MORNINGSTAR,
            ),
        )

        val profile = ball.profileRead()
        val configuration = assertIs<ProfileQueryResult.Configuration>(
            ball.query(ProfileQuery.GetRunConfiguration),
        ).value
        val expectedStamp = ProfileConsistencyStamp(
            ballInstanceId = ProfileProjection.BALL_INSTANCE_ID,
            commitRevision = 0uL,
            stateSchemaVersion = ProfileProjection.STATE_SCHEMA_VERSION,
        )

        assertEquals(expectedStamp, profile.consistencyStamp)
        assertEquals(expectedStamp, configuration.consistencyStamp)
        assertEquals(WeaponId.MORNINGSTAR, configuration.payload.selectedWeapon)
        assertEquals(2, configuration.payload.unlockedWeaponCount)
        assertEquals(100L, configuration.payload.lifetimeMatter)
        assertEquals(
            RebirthProgression.profile(0),
            configuration.payload.rebirthProfile,
        )
        assertEquals(
            RebirthProgression.profile(1),
            configuration.payload.nextRebirthProfile,
        )
        assertEquals(0uL, ball.profileRead().consistencyStamp.commitRevision)

        val committed = assertIs<ProfileDispatchResult.Committed>(
            ball.dispatch(ProfileIntent.SelectCoreShape(CoreShape.ORB)),
        )
        assertEquals(1uL, committed.profileRead.consistencyStamp.commitRevision)
        assertEquals(1uL, ball.profileRead().consistencyStamp.commitRevision)
    }

    @Test
    fun identicalProfileTraceProducesIdenticalStateAndRunConfiguration() {
        val first = deterministicBall()
        val second = deterministicBall()
        val trace = listOf(
            ProfileIntent.PurchaseMetaUpgrade(MetaUpgradeId.CORE_INTEGRITY),
            ProfileIntent.PurchaseOrSelectWeapon(WeaponId.MORNINGSTAR),
            ProfileIntent.RecordItemDiscoveries(listOf(4, 5, 4).toImmutableList()),
            ProfileIntent.ApplyRunOutcome(RunSettlementId(1uL), 40L, 0),
            ProfileIntent.AdvanceRebirth(0),
        )

        trace.forEach { intent ->
            assertIs<ProfileDispatchResult.Committed>(first.dispatch(intent))
            assertIs<ProfileDispatchResult.Committed>(second.dispatch(intent))
        }

        assertEquals(first.profileRead(), second.profileRead())
        assertEquals(
            first.query(ProfileQuery.GetRunConfiguration),
            second.query(ProfileQuery.GetRunConfiguration),
        )
    }

    @Test
    fun discoveryCollectionsAcceptNAndRejectNPlusOne() {
        assertEquals(ItemCatalogFacts.ITEM_COUNT, ProfileFeatureBall.LIMITS.maxCollectionItems)
        assertEquals(0, ProfileFeatureBall.LIMITS.maxEffectsPerDecision)
        assertEquals(0, ProfileFeatureBall.LIMITS.maxCommandsPerDecision)
        assertEquals(ProfileAdvanceRebirthContract.COMMAND_CAUSAL_DEPTH, ProfileFeatureBall.LIMITS.maxCausalDepth)
        assertEquals(1, ProfileFeatureBall.LIMITS.maxTransitionSteps)

        val atLimit = ProfileFeatureBall.create()
        val accepted = assertIs<ProfileDispatchResult.Committed>(
            atLimit.dispatch(
                ProfileIntent.RecordItemDiscoveries(
                    (0 until ItemCatalogFacts.ITEM_COUNT).toImmutableList(),
                ),
            ),
        )
        assertEquals(ItemCatalogFacts.ITEM_COUNT, accepted.profileRead.payload.discoveredItemIds.size)

        val overLimit = ProfileFeatureBall.create()
        val rejected = assertIs<ProfileDispatchResult.AdmissionRejected>(
            overLimit.dispatch(
                ProfileIntent.RecordItemDiscoveries(
                    List(ItemCatalogFacts.ITEM_COUNT + 1) { 0 }.toImmutableList(),
                ),
            ),
        )
        val limit = assertIs<AdmissionFailure.LimitExceeded>(rejected.reason)
        assertEquals(MandatoryDecisionLimit.COLLECTION_ITEMS, limit.limit)
        assertEquals((ItemCatalogFacts.ITEM_COUNT + 1).toLong(), limit.actual)
        assertEquals(0uL, overLimit.profileRead().consistencyStamp.commitRevision)
    }

    private fun deterministicBall(): ProfileFeatureBall = ProfileFeatureBall.create(
        PlayerProfileValues(matter = 10_000L, lifetimeMatter = 10_000L),
    )
}

private fun ProfileFeatureBall.profileRead() =
    (query(ProfileQuery.GetProfileProjection) as ProfileQueryResult.Projection).value

private fun profileAdvanceCommand(
    sourceCommitRevision: ULong,
    sourceOperationId: ULong,
    expectedLevel: Int,
): ProfileAdvanceRebirthModuleCommand {
    val source = ProfileCommandSource(
        sourceBallInstanceId = ProfileAdvanceRebirthContract.SOURCE_BALL_INSTANCE_ID,
        sourceCommitRevision = sourceCommitRevision,
        sourceOrdinal = 0u,
        sourceOperationId = sourceOperationId,
        sourceOutputKind = ProfileAdvanceRebirthContract.SOURCE_OUTPUT_KIND,
        sourceLocalOrdinalOrName =
        ProfileAdvanceRebirthContract.SOURCE_LOCAL_ORDINAL_OR_NAME,
    )
    return ProfileAdvanceRebirthModuleCommand(
        commandSource = source,
        causalBudgetScope = ProfileCausalScope(
            ownerBallInstanceId =
            ProfileAdvanceRebirthContract.CAUSAL_SCOPE_OWNER_BALL_INSTANCE_ID,
            operationId = source.sourceOperationId,
        ),
        causalDepth = ProfileAdvanceRebirthContract.COMMAND_CAUSAL_DEPTH,
        expectedLevel = expectedLevel,
    )
}

private fun profileRunOutcomeCommand(
    sourceCommitRevision: ULong,
    sourceOperationId: ULong,
    matterEarned: Long,
    clearedRebirthLevel: Int? = null,
): ProfileApplyRunOutcomeModuleCommand {
    val sourceOrdinal = 1u
    val source = ProfileCommandSource(
        sourceBallInstanceId = ProfileApplyRunOutcomeContract.SOURCE_BALL_INSTANCE_ID,
        sourceCommitRevision = sourceCommitRevision,
        sourceOrdinal = sourceOrdinal,
        sourceOperationId = sourceOperationId,
        sourceOutputKind = ProfileApplyRunOutcomeContract.SOURCE_OUTPUT_KIND,
        sourceLocalOrdinalOrName =
        ProfileApplyRunOutcomeContract.SOURCE_LOCAL_ORDINAL_PREFIX +
            (sourceOrdinal - 1u),
    )
    return ProfileApplyRunOutcomeModuleCommand(
        commandSource = source,
        causalBudgetScope = ProfileCausalScope(
            ownerBallInstanceId = source.sourceBallInstanceId,
            operationId = source.sourceOperationId,
        ),
        causalDepth = ProfileApplyRunOutcomeContract.COMMAND_CAUSAL_DEPTH,
        matterEarned = matterEarned,
        clearedRebirthLevel = clearedRebirthLevel,
    )
}
