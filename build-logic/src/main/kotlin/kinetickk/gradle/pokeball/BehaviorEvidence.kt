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
        ":ball:content:impl", "kinetickk.ball.content.impl.ContentBootstrapValidationTest",
        setOf(
            "exactCatalogBoundsAreAccepted",
            "itemBoundRejectsNPlusOne",
            "metaUpgradeBoundRejectsNPlusOne",
            "rebirthBoundRejectsLevelEleven",
            "relicBoundRejectsNPlusOne",
            "synergyBoundRejectsNPlusOne",
            "weaponBoundRejectsNPlusOne",
        ),
    ),
    BehaviorEvidence(
        ":ball:content:impl", "kinetickk.ball.content.impl.ContentCatalogTest",
        setOf(
            "coreShapeUnlockPolicyIsCapturedInStableIdOrder",
            "publishedCatalogCollectionsExposeNoMutationAuthorityAndCopyBootstrapInputs",
            "weaponCatalogAndCapturedMasteryPolicyAreOrderedAndQueryable",
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
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.GameplayNucleusTest",
        setOf(
            "acceptedFrameEnforcesOutputBoundOrderAndFinalCompletion",
            "atMostOneProfileCommandCanBePending",
            "configurationValidatorCoversEveryClosedBoundaryReason",
            "pointerViewportMembershipAcceptsInclusiveEdgesAndRejectsAllFourAdjacentCoordinates",
            "retainedRenderAndQueryCollectionsStayImmutable",
            "reusableRenderSnapshotMustMatchItsExactCommittedSource",
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
            "delayedRelicHitBoundAcceptsNAndRejectsNPlusOneCandidate",
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
            "meldRelicAcceptsRankFiveAndSalvagesTheFirstRankSixCandidate",
            "relicMatrixStopsAtFourSlotsAndDuplicateRanksStopAtFive",
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
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.GameplayCollectionBoundsTest",
        setOf(
            "arcCoilTargetsSixNearestAndLeavesSeventhUntouched",
            "choiceIndexThreeIsAcceptedAndFourIsRejected",
            "choiceInventoryAcceptsFourAndRejectsFifthAtomically",
            "collisionCompactionRetainsOrderAcrossConsecutiveProjectileAndPickupHits",
            "fixedSimulationOutputBatchPreservesCanonicalOrderAndOwnedPayloads",
            "fixedStepStableCompactionRetainsOrderAcrossConsecutiveRemovals",
            "projectileCopyOwnsAnIndependentCompactHitHistory",
            "projectileHitHistoryAcceptsOneHundredTwentyRejectsNextThenReclaimsDeadEntry",
            "projectileHitHistoryMembershipIsCorrectAtLinearAndBinarySearchBoundaries",
            "projectileHitHistoryRetentionMergesSortedLiveIdsAndHonorsTheirLogicalCount",
            "relicChainWorkAcceptsFiveAndRejectsSixthIteration",
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
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.RunFeatureBoundsTest",
        setOf(
            "activationClosesAlternativeAndNeverRetainsTwoActiveTrials",
            "fourthSeparatedDashCollapsesInsteadOfGrowingRetainedLattice",
            "sixthRewardFitsAndSeventhCannotPublishIntoSourceSnapshot",
        ),
    ),
    BehaviorEvidence(
        ":ball:gameplay:nucleus", "kinetickk.ball.gameplay.nucleus.simulation.SynergySystemTest",
        setOf(
            "effectBoundAndDependencyRemovalPreserveIsolatedSnapshots",
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
            "pageSliceReturnsThreeForExactAndFirstNPlusOneInputs",
            "presentationClockAcceptsMaximumAndClampsNextRepresentableDelta",
        ),
    ),
    BehaviorEvidence(
        ":ball:profile:interaction", "kinetickk.ball.profile.interaction.lab.impl.LabReducerTest",
        setOf(
            "snapshotMapsEightOrderedUpgradesAndExactNextCost",
        ),
    ),
    BehaviorEvidence(
        ":ball:profile:nucleus", "kinetickk.ball.profile.nucleus.ProfileNucleusTest",
        setOf(
            "achievementCountersSaturateWithoutOverflowAndTheDecisionIsDeterministic",
            "achievementThresholdsAccumulateAcrossRunsAndUnlockAllSixShapes",
            "gameplayProgressValidationUsesClosedRejectionReasons",
            "targetOwnedCommandOrdersPersistenceBeforeCorrelatedCompletion",
        ),
    ),
    BehaviorEvidence(
        ":ball:profile:resource", "kinetickk.ball.profile.resource.ProfileCodecTest",
        setOf(
            "byteLimitAndUtf8AreCheckedBeforeJsonDecode",
            "currentSchemaMaximumUnlockedWeaponsLabRanksAndDiscoveriesRoundTripWithoutLoss",
            "everyCharacterAndCumulativeAchievementRoundTripsAndRejectsInvalidProgress",
            "outboundLabRanksAndDiscoveriesRejectFirstNPlusOne",
            "preferenceConfigurationAcceptsExactMaximaAndRejectsAdjacentOverflowOrNonMembers",
            "preferenceIngressAcceptsMembersAndRejectsAdjacentInRangeNonMembers",
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
            "shellProjectionRetainsExactlySevenRoutesAndOnlySessionOwnedWorkflowState",
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
        ":flow:session:interaction", "kinetickk.flow.session.interaction.codex.impl.CodexReducerTest",
        setOf(
            "catalogAcceptsFourHundredAndRejectsFourHundredOne",
            "searchAccepts128AndClamps129",
        ),
    ),
    BehaviorEvidence(
        ":flow:session:interaction", "kinetickk.flow.session.interaction.home.impl.HomeReducerTest",
        setOf(
            "presentationClockAcceptsMaximumAndClampsNextRepresentableDelta",
        ),
    ),
    BehaviorEvidence(
        ":flow:session:nucleus", "kinetickk.flow.session.nucleus.AppSessionNucleusTest",
        setOf(
            "acceptedFrameOutputBoundAcceptsThreeAndRejectsFour",
            "initialStateOwnsOnlySessionWorkflowAndPublishesNarrowHomeShell",
            "pendingAndRunNamespaceGatesRejectWithoutOutputs",
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
