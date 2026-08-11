// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle.pokeball

internal object SameStackCumulativeFanoutPolicy {
    const val MAX_OUTPUTS_PER_ACCEPTED_DECISION: Int = 3
    const val MAX_CONSUMERS_PER_OUTPUT: Int = 1
    const val FIRST_ACCEPTED_CAUSAL_DEPTH: Int = 0
    const val LAST_ACCEPTED_CAUSAL_DEPTH: Int = 7
    const val MAX_CUMULATIVE_FANOUT: Int = 9_840
    const val HAS_ASYNC_HANDOFF: Boolean = false

    val acceptedCausalDepths: IntRange =
        FIRST_ACCEPTED_CAUSAL_DEPTH..LAST_ACCEPTED_CAUSAL_DEPTH
}

/**
 * Complete source tuple for one accepted SemanticOutput. This is verifier-only policy data, never
 * a runtime counter or a Decision input.
 */
internal data class AcceptedOutputSourceTuple(
    val authority: String,
    val instanceId: String,
    val commitRevision: Long,
    val materializedOutputId: String? = null,
    val semanticHandle: String? = null,
    val sourceOrdinal: Int,
    val outputVariant: String,
) {
    init {
        require(authority.isNotBlank())
        require(instanceId.isNotBlank())
        require(commitRevision >= 0L)
        require(materializedOutputId == null || materializedOutputId.isNotBlank())
        require(semanticHandle == null || semanticHandle.isNotBlank())
        require(sourceOrdinal >= 0)
        require(outputVariant.isNotBlank())
    }
}

/** One canonical cumulative-fanout unit within exactly one accepted root causal scope. */
internal data class CumulativeFanoutBranchIdentity(
    val rootScope: String,
    val source: AcceptedOutputSourceTuple,
    val effectiveRoute: String,
    val consumerOrExecutor: String,
) {
    init {
        require(rootScope.isNotBlank())
        require(effectiveRoute.isNotBlank())
        require(consumerOrExecutor.isNotBlank())
    }
}

/**
 * Mutually exclusive alternatives reserve their maximum rather than their sum. The alternative
 * label covers the entire reachable branch set selected by that alternative.
 */
internal data class CumulativeFanoutBranch(
    val identity: CumulativeFanoutBranchIdentity,
    val acceptedCausalDepth: Int = 0,
    val terminal: Boolean = false,
    val mutualExclusionGroup: String? = null,
    val alternative: String? = null,
) {
    init {
        require(acceptedCausalDepth in SameStackCumulativeFanoutPolicy.acceptedCausalDepths)
        require((mutualExclusionGroup == null) == (alternative == null)) {
            "Mutual-exclusion group and alternative must be declared together"
        }
        require(mutualExclusionGroup == null || mutualExclusionGroup.isNotBlank())
        require(alternative == null || alternative.isNotBlank())
    }
}

/**
 * Resolves a static accepted-branch inventory for one root scope. Terminal and co-reachable
 * branches count; converging routes remain distinct; exact redelivery duplicates do not count;
 * mutually exclusive alternatives reserve their maximum. An independent root is resolved by a
 * separate call and therefore starts with a fresh ceiling.
 */
internal fun resolveCumulativeFanout(
    rootScope: String,
    branches: Collection<CumulativeFanoutBranch>,
): Int {
    require(rootScope.isNotBlank())
    val inScope = branches.filter { branch -> branch.identity.rootScope == rootScope }
    inScope.groupBy(CumulativeFanoutBranch::identity).forEach { (identity, duplicates) ->
        val reservations = duplicates.map { branch ->
            branch.mutualExclusionGroup to branch.alternative
        }.toSet()
        require(reservations.size == 1) {
            "Duplicate branch identity $identity cannot change mutual-exclusion reservation"
        }
    }

    val coReachable = inScope
        .filter { branch -> branch.mutualExclusionGroup == null }
        .distinctBy(CumulativeFanoutBranch::identity)
        .size

    val exclusiveReservations = inScope
        .filter { branch -> branch.mutualExclusionGroup != null }
        .groupBy { branch -> checkNotNull(branch.mutualExclusionGroup) }
        .values
        .sumOf { group ->
            group.groupBy { branch -> checkNotNull(branch.alternative) }
                .values
                .maxOfOrNull { alternative ->
                    alternative.distinctBy(CumulativeFanoutBranch::identity).size
                }
                ?: 0
        }

    return coReachable + exclusiveReservations
}

internal fun staticCumulativeFanoutCeiling(
    maxOutputsPerAcceptedDecision: Int,
    maxConsumersPerOutput: Int,
    acceptedCausalDepths: IntRange,
): Int {
    require(maxOutputsPerAcceptedDecision >= 0)
    require(maxConsumersPerOutput >= 0)
    require(acceptedCausalDepths.first == 0)
    val branchFactor = Math.multiplyExact(maxOutputsPerAcceptedDecision, maxConsumersPerOutput)
    var frontier = 1
    var cumulative = 0
    acceptedCausalDepths.forEach { expectedDepth ->
        require(expectedDepth >= 0)
        frontier = Math.multiplyExact(frontier, branchFactor)
        cumulative = Math.addExact(cumulative, frontier)
    }
    return cumulative
}

internal fun cumulativeFanoutLimitViolation(resolvedFanout: Int): String? {
    require(resolvedFanout >= 0)
    return if (resolvedFanout <= SameStackCumulativeFanoutPolicy.MAX_CUMULATIVE_FANOUT) {
        null
    } else {
        "Cumulative fan-out $resolvedFanout exceeds static maxCumulativeFanout=" +
            SameStackCumulativeFanoutPolicy.MAX_CUMULATIVE_FANOUT
    }
}

internal data class OutputExecutorProjection(
    val id: String,
    val outputVariant: String,
    val effectiveRoute: String,
    val consumerOrExecutor: String,
    val executorPath: String,
    val requiredTokens: List<String>,
    val mutualExclusionGroup: String? = null,
    val alternative: String? = null,
) {
    init {
        require(id.isNotBlank())
        require(outputVariant.isNotBlank())
        require(effectiveRoute.isNotBlank())
        require(consumerOrExecutor.isNotBlank())
        require(executorPath.isNotBlank())
        require(requiredTokens.isNotEmpty() && requiredTokens.none(String::isBlank))
        require((mutualExclusionGroup == null) == (alternative == null))
    }
}

private const val PROFILE_OUTPUT_EXECUTOR_PATH =
    "ball/profile/impl/src/commonMain/kotlin/kinetickk/ball/profile/impl/DefaultProfileComponent.kt"
private const val GAMEPLAY_OUTPUT_EXECUTOR_PATH =
    "ball/gameplay/impl/src/commonMain/kotlin/kinetickk/ball/gameplay/impl/GameComponent.kt"
private const val SESSION_OUTPUT_EXECUTOR_PATH =
    "flow/session/impl/src/commonMain/kotlin/kinetickk/flow/session/impl/DefaultAppSessionComponent.kt"

internal val outputExecutorInventory = listOf(
    OutputExecutorProjection(
        "ProfileOutput.PersistV4Snapshot",
        "ProfileOutput.PersistV4Snapshot",
        "profile-resource-write-v4",
        "DefaultProfileComponent.execute -> ProfileResource.writeV4",
        PROFILE_OUTPUT_EXECUTOR_PATH,
        listOf("is ProfileOutput.PersistV4Snapshot", "resource.writeV4(output.snapshot)"),
    ),
    OutputExecutorProjection(
        "ProfileOutput.PurgeLegacy",
        "ProfileOutput.PurgeLegacy",
        "profile-resource-purge-legacy",
        "DefaultProfileComponent.execute -> ProfileResource.purgeLegacy",
        PROFILE_OUTPUT_EXECUTOR_PATH,
        listOf("is ProfileOutput.PurgeLegacy", "resource.purgeLegacy()"),
    ),
    OutputExecutorProjection(
        "ProfileOutput.CompleteCommand@app-session",
        "ProfileOutput.CompleteCommand",
        "profile-result-to-app-session",
        "AppSession Nucleus",
        PROFILE_OUTPUT_EXECUTOR_PATH,
        listOf(
            "is ProfileOutput.CompleteCommand",
            "dispatchCommandResult(output.result, item)",
            "commandResultSink(",
        ),
        mutualExclusionGroup = "profile-complete-consumer",
        alternative = "app-session-command-source",
    ),
    OutputExecutorProjection(
        "ProfileOutput.CompleteCommand@gameplay-run",
        "ProfileOutput.CompleteCommand",
        "profile-result-to-gameplay-run",
        "GameplayRun Nucleus",
        PROFILE_OUTPUT_EXECUTOR_PATH,
        listOf(
            "is ProfileOutput.CompleteCommand",
            "dispatchCommandResult(output.result, item)",
            "commandResultSink(",
        ),
        mutualExclusionGroup = "profile-complete-consumer",
        alternative = "gameplay-run-command-source",
    ),
    OutputExecutorProjection(
        "GameplayOutput.EmitVisualFx",
        "GameplayOutput.EmitVisualFx",
        "gameplay-visual-fx",
        "InteractionFxReducer.apply",
        GAMEPLAY_OUTPUT_EXECUTOR_PATH,
        listOf("is GameplayOutput.EmitVisualFx", "interactionFxReducer).apply(output.cues)"),
    ),
    OutputExecutorProjection(
        "GameplayOutput.SendProfileCommand",
        "GameplayOutput.SendProfileCommand",
        "gameplay-profile-progress",
        "GameComponent.executeProfileCommand -> GameplayProfileRoute.acceptFromGameplay",
        GAMEPLAY_OUTPUT_EXECUTOR_PATH,
        listOf(
            "is GameplayOutput.SendProfileCommand",
            "executeProfileCommand(output, item)",
            "profilePort.acceptFromGameplay(",
        ),
    ),
    OutputExecutorProjection(
        "GameplayOutput.AdvanceAudio",
        "GameplayOutput.AdvanceAudio",
        "gameplay-audio-advance",
        "GameComponent.execute -> GameplayAudioExecutor.advance",
        GAMEPLAY_OUTPUT_EXECUTOR_PATH,
        listOf("is GameplayOutput.AdvanceAudio", "audioExecutor.advance(output.realDeltaSeconds, output.cues)"),
    ),
    OutputExecutorProjection(
        "GameplayOutput.EnsureAudioUnlocked",
        "GameplayOutput.EnsureAudioUnlocked",
        "gameplay-audio-unlock",
        "GameComponent.execute -> GameplayAudioExecutor.ensureUnlocked",
        GAMEPLAY_OUTPUT_EXECUTOR_PATH,
        listOf("GameplayOutput.EnsureAudioUnlocked", "audioExecutor.ensureUnlocked()"),
    ),
    OutputExecutorProjection(
        "GameplayOutput.CompleteCommand",
        "GameplayOutput.CompleteCommand",
        "gameplay-result-to-app-session",
        "AppSession Nucleus",
        GAMEPLAY_OUTPUT_EXECUTOR_PATH,
        listOf(
            "is GameplayOutput.CompleteCommand",
            "dispatchCommandResult(output.result, item)",
            "commandResultSink(",
        ),
    ),
    OutputExecutorProjection(
        "AppSessionOutput.EnsureGameplayRun",
        "AppSessionOutput.EnsureGameplayRun",
        "session-ensure-gameplay-run",
        "DefaultAppSessionComponent.ensureGameplayRun -> GameplaySessionHost.createRun",
        SESSION_OUTPUT_EXECUTOR_PATH,
        listOf(
            "is AppSessionOutput.EnsureGameplayRun",
            "ensureGameplayRun(output)",
            "gameplaySessionHost.createRun(",
        ),
    ),
    OutputExecutorProjection(
        "AppSessionOutput.SendProfileCommand",
        "AppSessionOutput.SendProfileCommand",
        "session-profile-command-closed-route",
        "DefaultAppSessionComponent.executeProfileCommand -> SessionProfileRoute.acceptFromSession",
        SESSION_OUTPUT_EXECUTOR_PATH,
        listOf(
            "is AppSessionOutput.SendProfileCommand",
            "executeProfileCommand(output, item)",
            "profileRoute.acceptFromSession(",
        ),
    ),
    OutputExecutorProjection(
        "AppSessionOutput.SendGameplayCommand",
        "AppSessionOutput.SendGameplayCommand",
        "session-gameplay-command-closed-route",
        "DefaultAppSessionComponent.executeGameplayCommand -> GameplaySessionRunPort.acceptFromSession",
        SESSION_OUTPUT_EXECUTOR_PATH,
        listOf(
            "is AppSessionOutput.SendGameplayCommand",
            "executeGameplayCommand(output, item)",
            "target.acceptFromSession(",
        ),
    ),
    OutputExecutorProjection(
        "AppSessionOutput.SynchronizeAudioPreferences",
        "AppSessionOutput.SynchronizeAudioPreferences",
        "session-audio-preferences",
        "DefaultAppSessionComponent.updateAudioPreferences",
        SESSION_OUTPUT_EXECUTOR_PATH,
        listOf("is AppSessionOutput.SynchronizeAudioPreferences", "updateAudioPreferences(output.preferences)"),
    ),
    OutputExecutorProjection(
        "AppSessionOutput.PlayMuteFeedback",
        "AppSessionOutput.PlayMuteFeedback",
        "session-audio-mute-feedback",
        "DefaultAppSessionComponent.playMuteFeedback",
        SESSION_OUTPUT_EXECUTOR_PATH,
        listOf("AppSessionOutput.PlayMuteFeedback", "playMuteFeedback()"),
    ),
    OutputExecutorProjection(
        "AppSessionOutput.PlayRebirthAcceptedFeedback",
        "AppSessionOutput.PlayRebirthAcceptedFeedback",
        "session-audio-rebirth-feedback",
        "DefaultAppSessionComponent.playRebirthAcceptedFeedback",
        SESSION_OUTPUT_EXECUTOR_PATH,
        listOf("AppSessionOutput.PlayRebirthAcceptedFeedback", "playRebirthAcceptedFeedback()"),
    ),
).also { inventory ->
    requireUniqueKeys("outputExecutorInventory", inventory, OutputExecutorProjection::id)
}
