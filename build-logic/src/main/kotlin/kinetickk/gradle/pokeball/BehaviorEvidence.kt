// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle.pokeball

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Executed property evidence for checks that used to prescribe implementation text. */
internal data class BehaviorEvidence(
    val projectPath: String,
    val className: String,
    val methods: Set<String>,
) {
    val taskPath: String get() = "$projectPath:desktopTest"
    val reportPath: String
        get() = projectPath.removePrefix(":").replace(':', '/') +
            "/build/test-results/desktopTest/TEST-$className.xml"
}

internal val runtimeBehaviorEvidence = listOf(
    BehaviorEvidence(
        ":app:shared", "kinetickk.app.shared.DesktopDisplayHostTest",
        setOf(
            "anUnrelatedChangeKeepsAWindowThePlayerResizedAndForceRestoresTheStoredOne",
            "onlyDecorationAndVsyncChangesOpenTheWindowAgain",
        ),
    ),
    BehaviorEvidence(
        ":app:shared", "kinetickk.app.shared.DesktopFramePacingTest",
        setOf(
            "theStoredPreferenceDecidesOnlyWithoutALaunchOverride",
        ),
    ),
    BehaviorEvidence(
        ":app:shared", "kinetickk.app.shared.DisplayApplicationComposeTest",
        setOf(
            "aWindowOpenedAgainKeepsTheSessionTheRunAndTheOpenSettingsTab",
            "acceptedDisplayPreferencesReachTheHostAndAStoredModePressReappliesIt",
            "menusLayOutAtTheAppliedInterfaceScaleWhileTheRunKeepsTheRawDensity",
        ),
    ),
    BehaviorEvidence(
        ":app:shared", "kinetickk.app.shared.InlineSessionLifecycleTest",
        setOf(
            "acceptedExitProgressFinishesBeforeReplacementAndPreservesTheOldSnapshot",
            "busyRealProfileRefusesExitProgressAndSessionKeepsItsFailureVisible",
            "saveFaultAfterAcceptedExitProgressStillFinishesSessionBeforeEscaping",
            "settingsClosureAppliesPreferencesToThePausedRunBeforeAudio",
        ),
    ),
    BehaviorEvidence(
        ":app:shared", "kinetickk.app.shared.InlineMuteWorkflowTest",
        setOf(
            "aSaveProgrammingFaultStillDeliversTheAcceptedPreferencesBeforeEscaping",
            "aWrapperFaultAfterTheRealTargetResultDrainsWorkflowAndKeepsTheFirstFault",
            "mutePublishesProfileThenGameplayAndOnlyThenCompletesSession",
            "participantRefusalPublishesNeitherProfileNorGameplayChanges",
            "persistenceFailureAndUnknownOutcomeKeepTheAcceptedMuteAcrossAllOwners",
        ),
    ),
    BehaviorEvidence(
        ":app:shared", "kinetickk.app.shared.PlatformCapabilitiesDesktopTest",
        setOf(
            "audioBrokerIsInstanceOwnedAndCloseIsIdempotent",
            "desktopPreferenceKeyCountAccepts64AndRejects65BeforeIteration",
            "desktopPreferencesValueLengthAccepts8192AndRejects8193BeforeExecution",
            "synthesisBufferAcceptsMaximumDurationAndRejectsNext",
            "workerAndDiscardOldestQueueEnforceOneAndTwentyFour",
        ),
    ),
    BehaviorEvidence(
        ":ball:content:api", "kinetickk.ball.content.api.RebirthDataRequirementScaleTest",
        setOf(
            "theFirstScaledLevelIsAContentKnobFromTheWholeTableToNone",
        ),
    ),
    BehaviorEvidence(
        ":ball:content:api", "kinetickk.ball.content.api.RuleVocabularyTest",
        setOf(
            "conditionsHoldOnlyOnTriggersThatCarryTheirFacts",
            "frequentTriggersRateLimitTheNewActions",
            "keystonesAreOnePerItemOnEpicOrBetterItems",
            "newActionsAcceptTheirBoundsAndRejectTheFirstValueOutside",
            "newRulesAcceptTheirBoundsAndRejectTheFirstValueOutside",
        ),
    ),
    BehaviorEvidence(
        ":ball:content:api", "kinetickk.ball.content.api.RunTempoCurveTest",
        setOf(
            "levelRequirementsMustBePositiveAndNonDecreasing",
            "maximumLevelAcceptsNinetyNineAndRejectsOneHundred",
        ),
    ),
    BehaviorEvidence(
        ":ball:content:api", "kinetickk.ball.content.api.RunTempoLevelTableTest",
        setOf(
            "maximumLevelAcceptsItsBoundsAndRejectsTheFirstValueOutside",
        ),
    ),
    BehaviorEvidence(
        ":ball:content:api", "kinetickk.ball.content.api.SkillApexUpgradeContentTest",
        setOf(
            "apexBoundsAcceptTheMaximumAndRejectOneMore",
            "skillFieldReserveStaysBelowTheFieldBound",
            "skillRankValuesAcceptTheBoundAndRejectOneMore",
            "upgradePolicyBoundsAndDoublingPricesSaturate",
        ),
    ),
    BehaviorEvidence(
        ":ball:content:api", "kinetickk.ball.content.api.WorldContentTest",
        setOf(
            "obstacleTuningRejectsAWindowPastTheBoundAndInvertedReaches",
        ),
    ),
    BehaviorEvidence(
        ":ball:content:impl", "kinetickk.ball.content.impl.ContentBootstrapValidationTest",
        setOf(
            "exactCatalogBoundsAreAccepted",
            "itemBoundRejectsNPlusOne",
            "matrixNodeBoundRejectsNPlusOne",
            "rebirthBoundRejectsLevelEleven",
            "relicBoundRejectsNPlusOne",
            "synergyBoundRejectsNPlusOne",
            "weaponBoundRejectsNPlusOne",
            "keystonesAndExclusiveSkillRulesStayOnePerCatalog",
            "skillPoolFollowsTheSkillIdOrderAndRejectsTheFirstSkillPastTheBound",
        ),
    ),
    BehaviorEvidence(
        ":ball:content:impl", "kinetickk.ball.content.impl.ContentCatalogTest",
        setOf(
            "coreShapeUnlockPolicyIsCapturedInStableIdOrder",
            "publishedCatalogCollectionsExposeNoMutationAuthorityAndCopyBootstrapInputs",
            "weaponCatalogAndCapturedMasteryPolicyAreOrderedAndQueryable",
            "everyReactionHasExactlyOneKeystoneItemOfEpicRarityOrBetterThatDeclaresItsPair",
        ),
    ),
    BehaviorEvidence(
        ":ball:content:impl", "kinetickk.ball.content.impl.RelicCatalogTest",
        setOf(
            "relicCapacityAndEquippedRankBoundariesAreCapturedPolicy",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:api", "kinetickk.ball.gameplay.api.GameplayApiContractTest",
        setOf(
            "fixedInteractionRepresentationsRequireValidatedFactories",
            "runIdentityAndRevisionAreStable",
            "skillRequestsAcceptTheThreeSlotsAndRejectTheFirstOutside",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:impl", "kinetickk.ball.gameplay.impl.GameComponentTest",
        setOf(
            "audioFaultsPropagateAfterAcceptedFramesCommitAndDrainExactResults",
            "closedSettingsScopeIsRejectedBeforeAnotherStateAndRenderPublication",
            "completionDequeAndStaticBoundsRefuseNPlusOneWithoutTruncation",
            "busyCallRefusalIsTypedAndPublishesNothing",
            "dashCachedModelMatchesAnIndependentFreshProjectionExactly",
            "featureAcceptsOneActiveRunAndRefusesEveryFirstNPlusOneReplacement",
            "duplicateProfileReplyKeepsTheFirstAcceptedProgress",
            "localProfileCompletionDrainsAfterLaterAudioFaultInExactCausalOrder",
            "localProfileDeliverThenThrowStillExecutesAudioAndDrainsItsCompletion",
            "previousProfileScopeCannotCompleteTheCurrentProgress",
            "rejectedLocalRootPublishesNothingAndLeavesTheNextDispatchAdmissible",
            "renderModelIsReusedWhenOnlyTheStampedRevisionChanges",
            "renderSnapshotIsBuiltOncePerCommittedRevision",
            "scalarInputPublicationStructurallySharesUnchangedCollections",
            "settingsRefusalBeforeRunStartPreservesTheCreatedRun",
            "startReadsProfileAtBoundaryThenDeliversCanonicalResultAfterPublication",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:interaction", "kinetickk.ball.gameplay.interaction.GameplayWindowPreferencesTest",
        setOf(
            "losingWindowFocusPausesOnlyARunningRunAndOnlyWithThePreferenceOn",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:interaction", "kinetickk.ball.gameplay.interaction.canvas.FxBudgetTest",
        setOf(
            "aSaturatedTransientPoolSkipsSecondaryLayersAndDrawsEverySignature",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:interaction", "kinetickk.ball.gameplay.interaction.canvas.GameplayKeyBindingTest",
        setOf(
            "whilePausedOnlyPauseAndBrakeReleaseAreConsumedSoCOpensTheCodex",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:interaction", "kinetickk.ball.gameplay.interaction.canvas.HudHotPathAllocationTest",
        setOf(
            "levelClusterAtT5AndEveryApexStateAllocatesNothing",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:interaction", "kinetickk.ball.gameplay.interaction.canvas.HudPresentationMemoryTest",
        setOf(
            "aNewRunReArmsTheOneShotsWithoutFiringThem",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:interaction", "kinetickk.ball.gameplay.interaction.fx.InteractionFxReducerTest",
        setOf(
            "buildNotificationsAreGroupedBoundedDetachedAndExpireOnlyWhenEffectsAdvance",
            "categorySnapshotsAreSharedAndPreviouslyPublishedValuesRemainImmutable",
            "damageNumbersAcceptOneHundredFortyAndRejectOneHundredFortyFirst",
            "directionalParticlesAcceptSevenHundredAndRejectSevenHundredFirst",
            "motionEchoesAcceptThirtySixAndTrimOldestOnThirtySeventh",
            "particlesAcceptSevenHundredAndRejectSevenHundredFirst",
            "shockwavesAcceptFortyEightAndTrimOldestOnFortyNinth",
            "weaponArcsAcceptOneHundredTwentyEightAndTrimOldestOnOneHundredTwentyNinth",
            "apexBurstsAcceptTwoAndTrimTheOldestOnTheThird",
            "reactionFxAcceptFourOfAKindAndTrimTheOldestOnTheFifth",
            "skillBurstsAcceptEightAndTrimTheOldestOnTheNinth",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:interaction", "kinetickk.ball.gameplay.interaction.input.GameInteractionValidationTest",
        setOf(
            "choiceIndexValidationAcceptsZeroThroughThreeAndRejectsBothAdjacentValues",
            "frameDeltaAcceptsExactBoundsAndRejectsTheNextRepresentableValues",
            "pointerValidationAcceptsFiniteCoordinatesAndRejectsNonfiniteXAndY",
            "presentationDeltaAcceptsPointOneAndClampsFirstNPlusOne",
            "viewportAcceptsNAndRejectsNPlusOneForEveryBoundedField",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:interaction", "kinetickk.ball.gameplay.interaction.input.GameplayInputMappingTest",
        setOf(
            "anEmptySkillSlotIsAHudPositionThatDispatchesNothing",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:interaction", "kinetickk.ball.gameplay.interaction.input.RunningTouchTrackerTest",
        setOf(
            "touchTrackerAcceptsFivePointersAndIgnoresTheSixth",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.GameplayNucleusTest",
        setOf(
            "acceptedFrameEnforcesOutputBoundOrderAndFinalCompletion",
            "atMostOneProfileCommandCanBePending",
            "configurationValidatorCoversEveryClosedBoundaryReason",
            "pointerViewportMembershipAcceptsInclusiveEdgesAndRejectsAllFourAdjacentCoordinates",
            "retainedRenderAndQueryCollectionsStayImmutable",
            "reusableRenderSnapshotMustMatchItsExactCommittedSource",
            "startRejectsUpgradeLevelsOutsideTheContentRange",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.PreferencesProjectionValidationTest",
        setOf(
            "displayAndControlPreferencesAreValidOnlyWithinTheirOptionSets",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.characterization.GameSystemsTest",
        setOf(
            "everyCatalogItemCanBeAcquiredAndDiscovered",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.characterization.GameplayArchitectureBoundsTest",
        setOf(
            "echoHitBoundAcceptsNAndRejectsNPlusOneCandidate",
            "fixedStepWorkAcceptsFortyEightAndDefersFortyNinth",
            "simulationDeltaAndAccumulatorClampFirstNPlusOneToExactCaps",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.characterization.GameplayBaselineCharacterizationTest",
        setOf(
            "authoritativeCollectionsEnforceTheCurrentNPlusOneCaps",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.characterization.RelicSystemTest",
        setOf(
            "meldRaisesToRankTwoSalvagesBeyondItAndDuplicatesReachRankThree",
            "relicMatrixStopsAtFourSlotsAndDuplicateRanksStopAtThree",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.characterization.TotemEnemySystemsTest",
        setOf(
            "splitterFragmentsAcceptTheDynamicEnemyCapAndRejectTheNextCandidate",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.model.CopyOnWriteStorageTest",
        setOf(
            "floatArrayUsesRawBitsForSignedZeroAndNanPayloadDetachDecisions",
            "immutableSetReuseRequiresTheSameStableIterationOrder",
            "intArrayMultiForkWritesDetachInBothDirectionsWithoutCrossBranchMutation",
            "mutableIteratorRejectsRemoveBeforeNextAndRepeatedRemoveOnEveryOwnershipPath",
            "sameIntValueAndFillRemainSharedUntilContentActuallyChanges",
            "setNoOpMutatorsStaySharedAndIteratorRemoveIsForkSafe",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.protocol.BoundedVisualFxCueAccumulatorTest",
        setOf(
            "outputCapTwoThousandFortyEightReportsAttemptedTwoThousandFortyNinthWithoutGrowth",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.AcquiredItemReportTest",
        setOf(
            "theBufferKeepsTheNewestSixtyFourAcquisitions",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.ApexSystemTest",
        setOf(
            "apexStrikesDealTheFullCapStopAtTheBudgetAndNeverPassAPhaseGate",
            "dataAtTheCapTakesThreeHundredthsOfASecondPerPointAndNeverBringsTheApexBelowThirtySeconds",
            "requestsBufferOnlyForAnUnlockedApexWhileRunningAndRepeatsChangeNothing",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.ArchitectZoneTest",
        setOf(
            "aHushOnTheArchitectStillLeavesItsAttackWindows",
            "timeBubbleMawAndAttractorLeaveTheArchitectsVolleysAndTimersAlone",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.CombatFieldAllocationTest",
        setOf(
            "sixHundredStepsOverShotEatingBendingAndPickupFieldsAllocateNothingBeyondTheEmptyArena",
            "sixHundredStepsOverTheSkillFieldKindsAllocateNothingBeyondTheEmptyArena",
            "sixHundredStepsOverTwelveMixedFieldsAllocateNothingBeyondTheEmptyArena",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.CombatFieldTableTest",
        setOf(
            "combatFieldsAcceptTwelveAndRejectTheThirteenthReactionField",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.CombatFoundationsTest",
        setOf(
            "statusLayoutHoldsSeventyThreeCells",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.DraftDeckTest",
        setOf(
            "dealingDrawsFromItsOwnStreamSoRewardDrawsStayPut",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.GameplayCollectionBoundsTest",
        setOf(
            "arcCoilChainsAtMostFourTargetsAndLeavesFifthUntouched",
            "choiceIndexThreeIsAcceptedAndFourIsRejected",
            "choiceInventoryAcceptsFourAndRejectsFifthAtomically",
            "collisionCompactionRetainsOrderAcrossConsecutiveProjectileAndPickupHits",
            "fixedSimulationOutputBatchPreservesCanonicalOrderAndOwnedPayloads",
            "fixedStepStableCompactionRetainsOrderAcrossConsecutiveRemovals",
            "projectileCopyOwnsAnIndependentCompactHitHistory",
            "projectileHitHistoryAcceptsOneHundredTwentyRejectsNextThenReclaimsDeadEntry",
            "projectileHitHistoryMembershipIsCorrectAtLinearAndBinarySearchBoundaries",
            "projectileHitHistoryRetentionMergesSortedLiveIdsAndHonorsTheirLogicalCount",
            "chainWorkAcceptsSixTargetsAndRejectsTheSeventh",
            "rewardChoiceGeneratorsAcceptThreeCandidatesAndDeferFirstNPlusOne",
            "soundCuesAcceptThirtyTwoAndRejectThirtyThird",
            "trailSamplerProcessesThirtyTwoAndDropsThirtyThirdSample",
            "validatedFactoriesOwnFixedBoundsAndReducerOwnsPointerMembership",
            "weaponNodesAcceptEightAndRejectNinth",
            "weaponOrbitalsAcceptEightAndRejectNinthRequested",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.GameplayReductionIsolationTest",
        setOf(
            "candidateForksPreserveSelectedLoadoutAndPendingChoiceWithoutApplyingBootstrapDefaults",
            "copyOnWriteProductionTraceMatchesIndependentEagerMutableReference",
            "everyScalarCowIntentLeavesTheCompleteCommittedSourceFingerprintUnchanged",
            "fullReductionAfterScalarCowDoesNotMutateSourceOrSiblingBranch",
            "pausedFrameDrainsAchievementOnlyProgressWithoutMutatingSource",
            "scalarReductionDrainsCopiedPendingOutputsWithoutMutatingSourceOrSiblings",
            "threeProductionCollisionSiblingsDetachEnemyRelicBuffersWithoutTouchingSource",
            "threeProductionFrameSiblingsDetachOnlyActiveRelicTimersAndRemainDeterministic",
            "threeProductionItemChoiceSiblingsDetachProgressionStorageIndependently",
            "unchangedPointerDrainsAchievementOnlyProgressWithoutMutatingSource",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.GameplayRenderModelMapperTest",
        setOf(
            "equalNonEmptyEntitiesAreSharedButMutationsReprojectWithoutChangingRetainedSnapshot",
            "fullReductionRebuildsOnlyTheChangedProjectionFamily",
            "identityMatchedScalarSourceReusesEveryProjectionWithoutReadingStableLists",
            "identityMismatchFallsBackToRawFloatBitsRatherThanNumericEquality",
            "mismatchedIdentitySourceUsesExactFallbackAndCannotReuseChangedStorage",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.LevelCapTest",
        setOf(
            "dataAfterTheMaximumLevelChargesTheApexAndNeverLevels",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.ObstacleSystemTest",
        setOf(
            "destroyedObstaclesNeverRegenerateAndTheMemoryIsBounded",
            "theWindowIsDeterministicPerSeedBoundedGappedAndClearOfTheOrigin",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.PermanentUpgradeRunTest",
        setOf(
            "honingScalesTheCurrentWeaponOnlyAndFollowsAMidRunSwitch",
            "levelsAreCapturedOnceClampedToTheContentMaximum",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.PickupVacuumTest",
        setOf(
            "keysAndRelicsNeverExpireWhileDataFades",
            "theCapDropsNewDataButEvictsTheOldestDataForKeysAndRelics",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.PendingOutputBufferIsolationTest",
        setOf(
            "drainingOneForkAndRecordingLaterCannotMutateSourceOrSiblingOutputs",
            "emptyReductionCopyKeepsEveryOutputStorageUnmaterialized",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.PointOfInterestTest",
        setOf(
            "defendersReserveThreeExistingEnemySlotsAndRequireTheirDeaths",
            "scheduleOffersTwoWorldFixedPointsAndNeverReplacesSkippedOffer",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.ReactionDamageNonIncreaseTest",
        setOf(
            "noReactionHitsHarderThanItDidBeforeItsVerb",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.RuleVocabularyRuntimeTest",
        setOf(
            "cashedMarksDispatchWithTheReactionThatLeftThemAtMostFourTimesPerStep",
            "catalysisApexAndLinkRulesStopAtTheirCaps",
            "eachSkillChargeRechargesOnItsOwnClock",
            "enteringAFieldDispatchesOncePerEntryAndAtMostEveryHalfSecond",
            "erasedShotsDispatchAtMostFourTimesPerStepAsCascades",
            "fieldTuningScalesEveryFieldAndReactionFieldsStayWithinTheirProduct",
            "keystoneItemsSwitchTheirReactionAndTuningStaysWithinItsBounds",
            "skillChargesHoldTwoCastsAndKeepTheRecastFloor",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.RunFeatureBoundsTest",
        setOf(
            "activationClosesAlternativeAndNeverRetainsTwoActiveTrials",
            "fourthSeparatedDashCollapsesInsteadOfGrowingRetainedLattice",
            "sixthRewardFitsAndSeventhCannotPublishIntoSourceSnapshot",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.SkillOfferTest",
        setOf(
            "aDraftCostsExactlyThreeRewardDrawsAndARerollThreeMore",
            "rankUpCardsTakeTheExperimentalPlaceWithoutAnExtraDrawAndRankTheSkill",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.SkillSystemTest",
        setOf(
            "movementSkillsKeepTheCoreClearOfObstacles",
            "refundsNeverMoreThanDoubleTheCastRate",
            "requestsBufferOnlyForFilledSlotsWhileRunningAndRepeatsChangeNothing",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.SonicVoidElementTest",
        setOf(
            "chainedHushesFromManyReactorsStillLeaveTheArchitectAttackWindows",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.SynergySystemTest",
        setOf(
            "combatFieldBoundAndRelicRemovalPreserveIsolatedSnapshots",
        ),
    ),
    BehaviorEvidence(
        ":ball:profile:api", "kinetickk.ball.profile.api.PlayerSettingsContractTest",
        setOf(
            "bindingsRequireOneDistinctKeyPerAction",
            "displayAdjustmentsAdmitOnlyListedOptionsAndTheRefreshRange",
        ),
    ),
    BehaviorEvidence(
        ":ball:profile:api", "kinetickk.ball.profile.api.ProfileApiContractTest",
        setOf(
            "publicCollectionPayloadsDefensivelyOwnTheirStorage",
            "revisionsAndPersistenceEffectOrdinalsRejectNegativeValues",
        ),
    ),
    BehaviorEvidence(
        ":ball:profile:impl", "kinetickk.ball.profile.impl.DefaultProfileComponentTest",
        setOf(
            "activeLocalDispatchRefusesReentrantProgressWithoutAnotherMutation",
            "deployedCompletionAndStaticAcceptorBoundsAcceptNAndRefuseNPlusOne",
            "localMutationPublishesBeforeOneResourceFactAndAdvancesBothRevisions",
            "muteAcceptedResultSurvivesSaveFaultWithoutRollback",
            "muteCapabilityPublishesBeforeSaveAndCompletesThroughTheSharedBinding",
            "muteReentrantInvocationReturnsBusyAndCannotAcceptAnotherMutation",
            "muteScopeRejectsLateOrRepeatedUseBeforeAnotherProfileAcceptance",
            "muteTypedRefusalPreservesStateAndDoesNotRunPersistence",
            "rejectedAndUnknownWritesDoNotRollbackOrRetryAcceptedMutation",
            "gameplayProgressAcceptedResultSurvivesSaveFaultWithoutRollback",
            "gameplayProgressCapabilityPublishesEveryCapturedFieldBeforeSaveAndReply",
            "rebirthAcceptedResultSurvivesSaveFaultWithoutRollback",
            "rebirthCapabilityPublishesAndSavesBeforeCompletingItsOwnedResult",
            "callerWrapperFaultPreservesAcceptedWriteAndAllowsTheNextCommand",
        ),
    ),
    BehaviorEvidence(
        ":ball:profile:impl", "kinetickk.ball.profile.impl.ProfileComponentCharacterizationTest",
        setOf(
            "capturedCustomPolicyControlsItemsCostsUnlocksAndRebirthBounds",
            "everyRejectionLeavesStateRevisionAndEffectsUntouched",
        ),
    ),
    BehaviorEvidence(
        ":ball:profile:interaction", "kinetickk.ball.profile.interaction.armory.impl.ArmoryReducerTest",
        setOf(
            "pageStepsAndBackAreLocalPresentationEffects",
            "primaryActionEmitsOnlyTheTypedProfileIntentWhenAvailable",
        ),
    ),
    BehaviorEvidence(
        ":ball:profile:interaction", "kinetickk.ball.profile.interaction.matrix.impl.MatrixReducerTest",
        setOf(
            "snapshotMapsEveryOrderedNodeWithItsStatusAndExactNextCost",
        ),
    ),
    BehaviorEvidence(
        ":ball:profile:interaction", "kinetickk.ball.profile.interaction.settings.impl.SettingsDisplayControlsComposeTest",
        setOf(
            "keysOfACaptureNeverReachTheShellEvenWhileHeld",
        ),
    ),
    BehaviorEvidence(
        ":ball:profile:interaction", "kinetickk.ball.profile.interaction.settings.impl.SettingsReducerTest",
        setOf(
            "aKeyCellCapturesTheNextBindableKeyAndSwapsWithItsHolder",
            "interfaceScalesAreOfferedWhileTheScaledWindowKeepsItsMinimumSides",
            "resetKeysNeedsTwoPressesAndAnyOtherActionDisarmsIt",
            "whileCapturingAnyOtherPressOnlyEndsTheCapture",
            "windowSizeStepsThroughOfferedPresetsOnlyInWindowedMode",
        ),
    ),
    BehaviorEvidence(
        ":ball:profile:interaction", "kinetickk.ball.profile.interaction.workshop.impl.WorkshopComposeTest",
        setOf(
            "aRefitLevelIsBoughtThroughTheProfileAndARefusalIsNamed",
        ),
    ),
    BehaviorEvidence(
        ":ball:profile:interaction", "kinetickk.ball.profile.interaction.workshop.impl.WorkshopReducerTest",
        setOf(
            "aTabRequestAppliesOncePerSerial",
            "buyAsksForTheTypedUpgradeOnlyForAnOwnedAffordableTrackBelowItsMaximum",
            "selectAsksForTheCharacterOnlyWhenSelectionIsAvailable",
            "tabsAndSegmentsAreLocalPresentationEffects",
        ),
    ),
    BehaviorEvidence(
        ":ball:profile:interaction", "kinetickk.ball.profile.interaction.workshop.impl.WorkshopUpgradeBlockTest",
        setOf(
            "aShortBalanceNamesTheMissingMatterAndOneMoreMatterBuys",
            "theMaximumHasNoPriceNoNextValueAndClampsLevelsAboveIt",
        ),
    ),
    BehaviorEvidence(
        ":ball:profile:nucleus", "kinetickk.ball.profile.nucleus.ProfileDisplayPreferencesTest",
        setOf(
            "bindingAKeyAnotherActionHoldsSwapsTheTwoAndResetRestoresTheDefaults",
        ),
    ),
    BehaviorEvidence(
        ":ball:profile:nucleus", "kinetickk.ball.profile.nucleus.ProfileNucleusTest",
        setOf(
            "achievementCountersSaturateWithoutOverflowAndTheDecisionIsDeterministic",
            "achievementThresholdsAccumulateAcrossRunsAndUnlockAllTwelveShapes",
            "gameplayProgressValidationUsesClosedRejectionReasons",
            "targetOwnedCommandOrdersPersistenceBeforeCorrelatedCompletion",
            "upgradePriceDoublesAndSaturates",
        ),
    ),
    BehaviorEvidence(
        ":ball:profile:nucleus", "kinetickk.ball.profile.nucleus.ProfileRecentItemsTest",
        setOf(
            "sixtyFourAcquisitionsAreAcceptedAndSixtyFiveRejectWithoutChangingTheProfile",
        ),
    ),
    BehaviorEvidence(
        ":ball:profile:nucleus", "kinetickk.ball.profile.nucleus.ProfileUpgradeTest",
        setOf(
            "loadingClampsLevelsAboveALoweredMaximumAndRefundsTheirPrice",
            "ownershipIsCheckedBeforeTheMaximumAndTheMaximumBeforeMatter",
        ),
    ),
    BehaviorEvidence(
        ":ball:profile:resource", "kinetickk.ball.profile.resource.ProfileCodecTest",
        setOf(
            "byteLimitAndUtf8AreCheckedBeforeJsonDecode",
            "currentSchemaMaximumUnlockedWeaponsMatrixRanksAndDiscoveriesRoundTripWithoutLoss",
            "everyCharacterAndCumulativeAchievementRoundTripsAndRejectsInvalidProgress",
            "outboundMatrixRanksAndDiscoveriesRejectFirstNPlusOne",
            "preferenceConfigurationAcceptsExactMaximaAndRejectsAdjacentOverflowOrNonMembers",
            "preferenceIngressAcceptsMembersAndRejectsAdjacentInRangeNonMembers",
            "displayPreferenceIngressRejectsExplicitDefaultsUnknownIdsAndNonMembers",
            "keyBindingRecordsRejectUnsortedDuplicateUnknownDefaultAndConflictingForms",
            "recentItemIdsAcceptSixtyFourInRangeIdsAndRejectOneMoreOrAnIdOutsideTheBounds",
            "upgradeDigitsKeepTheirFrozenSlotPositions",
            "upgradeLevelsAcceptTenAndRejectEleven",
        ),
    ),
    BehaviorEvidence(
        ":ball:profile:resource", "kinetickk.ball.profile.resource.ProfilePayloadBudgetTest",
        setOf(
            "aWorstCaseProfileFitsOneDesktopPreferenceValue",
        ),
    ),
    BehaviorEvidence(
        ":ball:profile:resource", "kinetickk.ball.profile.resource.ProfileStorageTest",
        setOf(
            "invalidOutboundSnapshotIsRejectedBeforeProviderWrite",
            "malformedSnapshotIsRejectedWithoutFallback",
            "missingSnapshotIsObservedWithoutWriting",
            "possibleWriteExecutionOrUnconfirmedReadBackIsOutcomeUnknown",
            "providerProgrammingFaultPropagatesWithoutFabricatedEvidence",
            "readObservesTheCurrentSnapshotWithoutConsultingAnyOtherKey",
            "successfulWriteIsConfirmedByExactReadBack",
            "typedProviderReadFailureIsAConfirmedNondestructiveResourceFailure",
            "writeFailureBeforeExecutionIsKnownResourceFailure",
        ),
    ),
    BehaviorEvidence(
        ":flow:session:api", "kinetickk.flow.session.api.AppSessionApiContractTest",
        setOf(
            "shellProjectionRetainsExactlySixRoutesAndOnlySessionOwnedWorkflowState",
        ),
    ),
    BehaviorEvidence(
        ":flow:session:impl", "kinetickk.flow.session.impl.BrowserRuntimeQaTest",
        setOf(
            "characterSelectionUnderTheWorkshopRunsTheProfileCommandOnlyBetweenRuns",
        ),
    ),
    BehaviorEvidence(
        ":flow:session:impl", "kinetickk.flow.session.impl.DefaultAppSessionComponentTest",
        setOf(
            "acceptedWithoutResultAndResultPlusRejectionAreFaults",
            "deployedQueueDepthFanoutAndCapacityAcceptNRejectNPlusOne",
            "duplicateAndLateMuteRepliesCannotReplaceTheAcceptedResultOrFinishAnotherCall",
            "gameplayThrowAfterValidatedResultStillDrainsThenRethrows",
            "muteAcceptedProfileAndGameplayResultsFinishBeforeTheFirstTargetFaultEscapes",
            "mutePreservesOneScopeAcrossProfileResultAndNestedGameplayCommand",
            "muteTypedParticipantRefusalsFinishWithoutRewritingAcceptedProfileSettings",
            "muteWithoutAReplyFaultsWithoutSynthesizingParticipantRefusal",
            "nestedExitDeliveryCompletesAfterTheTypedTargetReturns",
            "pauseCompletesItsOverlayBeforeAValidatedTargetFaultEscapes",
            "pauseRefusalKeepsTheRunningDestinationAndClearsItsWorkflow",
            "profileThrowAfterValidatedResultStillDrainsThenRethrows",
            "rebirthFinishesItsNewRunBeforeAProfileFaultEscapes",
            "rebirthRefusalLeavesTheConfirmationDisarmedWithoutCreatingARun",
            "settingsResultForAnotherRunIsRetainedAndCannotCompleteTheCurrentWorkflow",
            "startPublishesBeforeEnsureAndExactTargetCommandThenDrainsResult",
        ),
    ),
    BehaviorEvidence(
        ":flow:session:interaction", "kinetickk.flow.session.interaction.AppSessionKeyboardTest",
        setOf(
            "boundGameplayKeysReachGameplayFirstOnlyWhileRunning",
        ),
    ),
    BehaviorEvidence(
        ":flow:session:interaction", "kinetickk.flow.session.interaction.codex.impl.CodexEntryFactsTest",
        setOf(
            "aDormantItemSaysItsEffectsWaitForASkillOrAnotherWeapon",
            "anItemTheDeckLeftOutSaysItIsNotInThisRun",
        ),
    ),
    BehaviorEvidence(
        ":flow:session:interaction", "kinetickk.flow.session.interaction.codex.impl.CodexReducerTest",
        setOf(
            "catalogAcceptsTheContentBoundAndRejectsOneMore",
            "searchAccepts128AndClamps129",
        ),
    ),
    BehaviorEvidence(
        ":flow:session:interaction", "kinetickk.flow.session.interaction.home.impl.HomeReducerTest",
        setOf(
            "presentationClockAcceptsMaximumAndClampsNextRepresentableDelta",
            "theWorkshopStampIsTheProfilesAffordabilityRead",
        ),
    ),
    BehaviorEvidence(
        ":flow:session:nucleus", "kinetickk.flow.session.nucleus.AppSessionNucleusTest",
        setOf(
            "acceptedFrameOutputBoundAcceptsThreeAndRejectsFour",
            "initialStateOwnsOnlySessionWorkflowAndPublishesNarrowHomeShell",
            "pendingAndRunNamespaceGatesRejectWithoutOutputs",
            "characterSelectionIsAcceptedOnlyOverHomeIncludingTheWorkshop",
        ),
    ),
    BehaviorEvidence(
        ":foundation:common", "kinetickk.foundation.collections.ImmutableCollectionsTest",
        setOf(
            "factoriesCopySourceStorage",
            "setIterationOrderAndSetEqualityAreStable",
        ),
    ),
    BehaviorEvidence(
        ":foundation:common", "kinetickk.foundation.dispatch.BoundedCompletionDequeTest",
        setOf(
            "acceptsExactlyNAndRefusesFirstNPlusOneWithoutTruncating",
            "architectureDepthAcceptsEightAndRefusesNinthWithoutTruncating",
            "freedCapacityCanBeReusedAndOrderRemainsFifo",
            "preparationFailureRetainsTheHeadAndDoesNotReorderReadyItems",
        ),
    ),
    BehaviorEvidence(
        ":foundation:common", "kinetickk.foundation.dispatch.InlineAcceptanceTest",
        setOf(
            "aCompletionRemainsQueuedUntilItsDecisionAndEntireFrameAreAccepted",
            "aSmallOwnerReusesAcceptanceAndReturnsTheRootRevisionAfterDraining",
            "completionOutsideTheCurrentCallIsRejected",
            "exactCompletionCapacityRetainsEveryItemAndRefusesFirstOverflow",
            "firstExecutionFaultEscapesOnlyAfterAllOutputsAndAcceptedCompletions",
            "preflightFaultPublishesNoStateOrOutputAndReleasesTheWriter",
            "publicationPrecedesOutputsAndFifoCompletionsKeepTheirOwnInputAndCurrentState",
            "recursiveIngressCannotDecideAndCompletionRunsAfterTheOutputReturns",
            "unavailableOutputBatchCannotPublishTheCandidate",
        ),
    ),
    BehaviorEvidence(
        ":foundation:common", "kinetickk.foundation.dispatch.InlineReplyTest",
        setOf(
            "aClosedReplyCannotCompleteALaterOrForeignInvocation",
            "aFailingHeadRetainsLaterAcceptedRepliesWithoutProcessingThemOutOfOrder",
            "acceptedCompletionCanReuseItsOneSlotForTheNextCommand",
            "acceptedResultSurvivesTargetFaultAndSourceCompletesBeforeItEscapes",
            "anInputAdaptationFaultCannotReplaceAnEarlierTargetFault",
            "callProtectsItsCompletionSlotUntilTheTargetReturns",
            "callingOutsideAcceptedOutputExecutionNeverInvokesTheTarget",
            "conflictingRefusalCannotReplaceAnAcceptedResult",
            "duplicateCompletionPreservesFirstResultAndDrainsItOnce",
            "faultWithoutAnOutcomeEscapesUnchangedWithoutSynthesizingARefusal",
            "inputAdaptationFaultRetainsAcceptedReplyAndBlocksNewRootsWithoutARefusal",
            "inputsAreConstructedAfterTheTargetReturnsAndFifoKeepsEachContext",
            "missingCompletionCapacityStopsTheTargetWithoutDroppingEarlierWork",
            "normalReturnWithoutAnOutcomeIsAFaultAndDoesNotInventARefusal",
            "refusalEntersTheSerializedSourceWithoutAnAcceptedTargetMutation",
        ),
    ),
    BehaviorEvidence(
        ":foundation:design", "kinetickk.foundation.design.KkFramePacingTest",
        setOf(
            "aCapWaitsOutTheRestOfItsIntervalHalfAMillisecondEarlyInWholeMilliseconds",
        ),
    ),
    BehaviorEvidence(
        ":foundation:design", "kinetickk.foundation.design.KkTokensTest",
        setOf(
            "levelBadgeTierReachesApexExactlyAtTheCap",
        ),
    ),
    BehaviorEvidence(
        ":resource:audio:impl", "kinetickk.resource.audio.impl.DefaultAudioServiceTest",
        setOf(
            "preferencesAreAppliedWithoutLettingInvalidVolumeReachThePlayer",
            "closeIsIdempotentAndTerminal",
            "servicePreservesRequestOrderAndMusicSequence",
            "callerEffectIngressAcceptsThirtyTwoAndRejectsThirtyThird",
            "callerEffectRequestSelectionAcceptsThreeAndDropsFourth",
            "capabilityFaultsPropagateForUnlockPlayAndCloseWithoutInventingClosedState",
            "musicAdvanceDeltaAcceptsMaximumAndClampsNextRepresentableValue",
            "toneRequestIngressAcceptsInclusiveBoundsAndRejectsNextRepresentableValues",
        ),
    ),
)

internal fun behaviorEvidenceViolations(
    reports: Map<String, String>,
    required: List<BehaviorEvidence> = runtimeBehaviorEvidence,
): List<String> = buildList {
    val factory = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }
    required.forEach { evidence ->
        val xml = reports[File(evidence.reportPath).name]
        if (xml == null) {
            add("Missing executed behavior evidence ${evidence.reportPath}")
            return@forEach
        }
        val document = runCatching {
            xml.byteInputStream().use { factory.newDocumentBuilder().parse(it) }
        }.getOrElse {
            add("Invalid behavior evidence ${evidence.reportPath}: ${it.message}")
            return@forEach
        }
        val cases = document.getElementsByTagName("testcase")
        val passed = buildSet {
            for (index in 0 until cases.length) {
                val case = cases.item(index) as org.w3c.dom.Element
                if (case.getAttribute("classname") != evidence.className) continue
                if (listOf("failure", "error", "skipped").any { case.getElementsByTagName(it).length != 0 }) continue
                add(case.getAttribute("name").removeSuffix("[desktop]"))
            }
        }
        (evidence.methods - passed).sorted().forEach { method ->
            add("Behavior evidence did not pass: ${evidence.className}.$method (${evidence.taskPath})")
        }
    }
}.distinct().sorted()
