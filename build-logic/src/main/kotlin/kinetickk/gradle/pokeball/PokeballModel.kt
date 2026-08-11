// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle.pokeball

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.SortedSet

internal data class ProjectEdge(
    val source: String,
    val configuration: String,
    val target: String,
) {
    val encoded: String
        get() = "$source\t$configuration\t$target"

    val isTest: Boolean
        get() = configuration.contains("test", ignoreCase = true)

    companion object {
        fun decode(encoded: String): ProjectEdge {
            val parts = encoded.split('\t', limit = 3)
            require(parts.size == 3) { "Malformed project dependency edge: $encoded" }
            return ProjectEdge(parts[0], parts[1], parts[2])
        }
    }
}

internal data class SourceDocument(
    val relativePath: String,
    val text: String,
)

internal data class BoundProjection(
    val id: String,
    val value: String,
    val sourcePath: String,
    val requiredToken: String,
    val evidencePath: String? = null,
    val evidenceToken: String? = null,
    val additionalEvidenceToken: String? = null,
    val additionalRequiredTokens: List<String> = emptyList(),
    val additionalSourceAnchors: List<BoundAnchor> = emptyList(),
    val additionalEvidenceAnchors: List<BoundAnchor> = emptyList(),
) {
    init {
        require((evidencePath == null) == (evidenceToken == null)) {
            "Bound $id must provide both an evidence path and token, or neither"
        }
    }

    val sourceAnchors: List<BoundAnchor>
        get() = listOf(
            BoundAnchor(sourcePath, listOf(requiredToken) + additionalRequiredTokens),
        ) + additionalSourceAnchors

    val evidenceAnchors: List<BoundAnchor>
        get() = listOfNotNull(
            evidencePath?.let { path ->
                BoundAnchor(path, listOfNotNull(evidenceToken, additionalEvidenceToken))
            },
        ) + additionalEvidenceAnchors
}

internal data class BoundAnchor(
    val path: String,
    val tokens: List<String>,
) {
    constructor(path: String, token: String) : this(path, listOf(token))
}

internal data class ReadRouteProjection(
    val id: String,
    val sourceAuthority: String,
    val targetAuthority: String,
    val ownerPath: String,
    val queryToken: String,
    val resultToken: String,
    val usagePath: String,
)

internal data class CommandRouteProjection(
    val id: String,
    val sourceAuthority: String,
    val targetAuthority: String,
    val ownerPath: String,
    val operationToken: String,
    val outcomeToken: String,
    val sourcePath: String,
    val acceptedCarrierToken: String,
    val rejectedCarrierToken: String,
    val usagePath: String,
    val additionalOutcomeTokens: List<String> = emptyList(),
) {
    val outcomeTokens: List<String>
        get() = listOf(outcomeToken) + additionalOutcomeTokens
}

internal data class CommandOutcomeFamily(
    val targetAuthority: String,
    val ownerPath: String,
    val declaration: String,
    val supertype: String,
)

internal fun <T, K> requireUniqueKeys(
    label: String,
    values: List<T>,
    key: (T) -> K,
) {
    val duplicates = values.groupingBy(key).eachCount().filterValues { count -> count > 1 }.keys
    require(duplicates.isEmpty()) { "$label contains duplicate keys: ${duplicates.joinToString()}" }
}

internal fun <K, V> uniqueLinkedMap(
    label: String,
    entries: List<Pair<K, V>>,
): LinkedHashMap<K, V> {
    requireUniqueKeys(label, entries, Pair<K, V>::first)
    return LinkedHashMap<K, V>().apply { entries.forEach { (key, value) -> put(key, value) } }
}

internal object PokeballBaseline {
    const val CORE_COMMIT = "de9ef7384795680c836d5e6c2c9b394286058670"
    const val CORE_VERSION = "1.4.0-draft"
    const val CORE_STATUS = "canonical draft"
    const val CORE_FILE_COUNT = 25
    const val CORE_BYTES = 725_281L
    const val CORE_SHA256 = "d7792cb6adfaf9d7e3cf0c59bcc40b1158200bfcd0496661d3293035917f352c"
    const val AGENT_PACK_REVISION = 12
    const val AGENT_PACK_FILE_COUNT = 25
    const val AGENT_PACK_SHA256 = "1332fbc4ccbc55112ea87fa902437e6bb27f043a67663ebbe6e11f3e4239089d"
}

internal const val PROFILE_PROTOCOL_PATH =
    "ball/profile/api/src/commonMain/kotlin/kinetickk/ball/profile/api/ProfileProtocol.kt"
internal const val PROFILE_QUERY_PATH =
    "ball/profile/api/src/commonMain/kotlin/kinetickk/ball/profile/api/ProfileQueries.kt"
internal const val GAMEPLAY_PROTOCOL_PATH =
    "ball/gameplay/api/src/commonMain/kotlin/kinetickk/ball/gameplay/api/GameplayProtocol.kt"
internal const val GAMEPLAY_QUERY_PATH =
    "ball/gameplay/api/src/commonMain/kotlin/kinetickk/ball/gameplay/api/GameplayQueries.kt"
private const val SESSION_PROTOCOL_PATH =
    "flow/session/api/src/commonMain/kotlin/kinetickk/flow/session/api/SessionProtocol.kt"
internal const val CONTENT_SURFACE_PATH =
    "ball/content/api/src/commonMain/kotlin/kinetickk/ball/content/api/ContentSnapshots.kt"
private const val APP_COMPOSITION_PATH =
    "app/shared/src/commonMain/kotlin/kinetickk/app/shared/AppComposition.kt"
private const val SESSION_IMPL_PATH =
    "flow/session/impl/src/commonMain/kotlin/kinetickk/flow/session/impl/DefaultAppSessionComponent.kt"
private const val SESSION_NUCLEUS_PATH =
    "flow/session/nucleus/src/commonMain/kotlin/kinetickk/flow/session/nucleus/AppSessionNucleus.kt"
private const val PROFILE_NUCLEUS_PATH =
    "ball/profile/nucleus/src/commonMain/kotlin/kinetickk/ball/profile/nucleus/ProfileNucleus.kt"
private const val GAMEPLAY_NUCLEUS_PATH =
    "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/GameplayNucleus.kt"
private const val SESSION_CONTENT_PATH =
    "flow/session/interaction/src/commonMain/kotlin/kinetickk/flow/session/interaction/AppSessionContent.kt"
private const val SESSION_HOME_PATH =
    "flow/session/interaction/src/commonMain/kotlin/kinetickk/flow/session/interaction/home/impl/DefaultHomeFeature.kt"
private const val SESSION_CODEX_PATH =
    "flow/session/interaction/src/commonMain/kotlin/kinetickk/flow/session/interaction/codex/impl/DefaultCodexFeature.kt"
private const val CONTENT_BOUNDS_TEST_PATH =
    "ball/content/impl/src/commonTest/kotlin/kinetickk/ball/content/impl/ContentBootstrapValidationTest.kt"
private const val PROFILE_IMPL_PATH =
    "ball/profile/impl/src/commonMain/kotlin/kinetickk/ball/profile/impl/DefaultProfileComponent.kt"
private const val PROFILE_IMPL_TEST_PATH =
    "ball/profile/impl/src/commonTest/kotlin/kinetickk/ball/profile/impl/DefaultProfileComponentTest.kt"
private const val PROFILE_PLAYER_PATH =
    "ball/profile/api/src/commonMain/kotlin/kinetickk/ball/profile/api/PlayerProfile.kt"
private const val PROFILE_NUCLEUS_TEST_PATH =
    "ball/profile/nucleus/src/commonTest/kotlin/kinetickk/ball/profile/nucleus/ProfileNucleusTest.kt"
private const val PROFILE_CODEC_PATH =
    "ball/profile/resource/src/commonMain/kotlin/kinetickk/ball/profile/resource/ProfileCodec.kt"
private const val PROFILE_CODEC_TEST_PATH =
    "ball/profile/resource/src/commonTest/kotlin/kinetickk/ball/profile/resource/ProfileCodecTest.kt"
private const val GAMEPLAY_IMPL_PATH =
    "ball/gameplay/impl/src/commonMain/kotlin/kinetickk/ball/gameplay/impl/GameComponent.kt"
private const val GAMEPLAY_STATE_PATH =
    "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/simulation/MutableGameState.kt"
private const val GAMEPLAY_REDUCER_PATH =
    "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/reducer/GameReducer.kt"
private const val GAMEPLAY_LOOP_PATH =
    "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/simulation/GameLoop.kt"
private const val GAMEPLAY_WEAPON_SYSTEM_PATH =
    "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/simulation/WeaponSystem.kt"
private const val GAMEPLAY_PROGRESSION_SYSTEM_PATH =
    "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/simulation/ProgressionSystem.kt"
private const val GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH =
    "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/simulation/GameplayCollectionBoundsTest.kt"
private const val GAMEPLAY_ARCHITECTURE_BOUNDS_TEST_PATH =
    "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/characterization/GameplayArchitectureBoundsTest.kt"
private const val GAMEPLAY_INTERACTION_PATH =
    "ball/gameplay/interaction/src/commonMain/kotlin/kinetickk/ball/gameplay/interaction/GameInteraction.kt"
private const val GAMEPLAY_INGRESS_PATH =
    "ball/gameplay/interaction/src/commonMain/kotlin/kinetickk/ball/gameplay/interaction/input/InteractionValidation.kt"
private const val GAMEPLAY_INGRESS_TEST_PATH =
    "ball/gameplay/interaction/src/commonTest/kotlin/kinetickk/ball/gameplay/interaction/input/GameInteractionValidationTest.kt"
private const val GAMEPLAY_FX_PATH =
    "ball/gameplay/interaction/src/commonMain/kotlin/kinetickk/ball/gameplay/interaction/fx/InteractionFxReducer.kt"
private const val GAMEPLAY_FX_TEST_PATH =
    "ball/gameplay/interaction/src/commonTest/kotlin/kinetickk/ball/gameplay/interaction/fx/InteractionFxReducerTest.kt"
private const val SESSION_CODEX_STATE_PATH =
    "flow/session/interaction/src/commonMain/kotlin/kinetickk/flow/session/interaction/codex/impl/CodexState.kt"
private const val SESSION_CODEX_TEST_PATH =
    "flow/session/interaction/src/commonTest/kotlin/kinetickk/flow/session/interaction/codex/impl/CodexReducerTest.kt"
private const val SESSION_HOME_TEST_PATH =
    "flow/session/interaction/src/commonTest/kotlin/kinetickk/flow/session/interaction/home/impl/HomeReducerTest.kt"
private const val PROFILE_ARMORY_STATE_PATH =
    "ball/profile/interaction/src/commonMain/kotlin/kinetickk/ball/profile/interaction/armory/impl/ArmoryState.kt"
private const val PROFILE_ARMORY_FEATURE_PATH =
    "ball/profile/interaction/src/commonMain/kotlin/kinetickk/ball/profile/interaction/armory/impl/DefaultArmoryFeature.kt"
private const val PROFILE_ARMORY_TEST_PATH =
    "ball/profile/interaction/src/commonTest/kotlin/kinetickk/ball/profile/interaction/armory/impl/ArmoryReducerTest.kt"
private const val SESSION_STATE_PATH =
    "flow/session/nucleus/src/commonMain/kotlin/kinetickk/flow/session/nucleus/AppSessionState.kt"
private const val SESSION_IMPL_TEST_PATH =
    "flow/session/impl/src/commonTest/kotlin/kinetickk/flow/session/impl/DefaultAppSessionComponentTest.kt"
private const val AUDIO_SERVICE_PATH =
    "resource/audio/impl/src/commonMain/kotlin/kinetickk/resource/audio/impl/DefaultAudioService.kt"
private const val AUDIO_SERVICE_TEST_PATH =
    "resource/audio/impl/src/commonTest/kotlin/kinetickk/resource/audio/impl/DefaultAudioServiceTest.kt"
private const val DESKTOP_AUDIO_PATH =
    "resource/audio/impl/src/desktopMain/kotlin/kinetickk/resource/audio/impl/PlatformTonePlayer.desktop.kt"
private const val DESKTOP_AUDIO_TEST_PATH =
    "resource/audio/impl/src/desktopTest/kotlin/kinetickk/resource/audio/impl/DesktopAudioExecutionPolicyTest.kt"

internal val expectedLeafProjects = sortedSetOf(
    ":app:desktop",
    ":app:shared",
    ":app:web",
    ":foundation:common",
    ":foundation:design",
    ":resource:audio:api",
    ":resource:audio:impl",
    ":ball:content:api",
    ":ball:content:impl",
    ":ball:profile:api",
    ":ball:profile:nucleus",
    ":ball:profile:resource",
    ":ball:profile:interaction",
    ":ball:profile:impl",
    ":ball:gameplay:api",
    ":ball:gameplay:nucleus",
    ":ball:gameplay:interaction",
    ":ball:gameplay:impl",
    ":flow:session:api",
    ":flow:session:nucleus",
    ":flow:session:interaction",
    ":flow:session:impl",
)

internal val expectedCommandRoutes = sortedSetOf(
    "gameplay-profile-progress",
    "session-gameplay-exit",
    "session-gameplay-pause",
    "session-gameplay-preferences",
    "session-gameplay-start",
    "session-profile-core-shape",
    "session-profile-mute",
    "session-profile-rebirth",
    "session-profile-reset-confirm",
    "session-profile-reset-retry",
)

internal val commandRouteProjections = listOf(
    CommandRouteProjection(
        "session-profile-core-shape", "AppSession", "Profile",
        PROFILE_PROTOCOL_PATH, "ProfilePulse.SelectCoreShape", "ProfileCommandOutcome.CoreShapeSelected",
        SESSION_PROTOCOL_PATH, "SessionControlPulse.ProfileCommandCompleted",
        "SessionControlPulse.ProfileCommandRejectedBeforeAcceptance", SESSION_NUCLEUS_PATH,
    ),
    CommandRouteProjection(
        "session-profile-mute", "AppSession", "Profile",
        PROFILE_PROTOCOL_PATH, "ProfilePulse.ToggleMute", "ProfileCommandOutcome.PreferencesChanged",
        SESSION_PROTOCOL_PATH, "SessionControlPulse.ProfileCommandCompleted",
        "SessionControlPulse.ProfileCommandRejectedBeforeAcceptance", SESSION_NUCLEUS_PATH,
    ),
    CommandRouteProjection(
        "session-profile-rebirth", "AppSession", "Profile",
        PROFILE_PROTOCOL_PATH, "ProfilePulse.AdvanceRebirth", "ProfileCommandOutcome.RebirthAdvanced",
        SESSION_PROTOCOL_PATH, "SessionControlPulse.ProfileCommandCompleted",
        "SessionControlPulse.ProfileCommandRejectedBeforeAcceptance", SESSION_NUCLEUS_PATH,
    ),
    CommandRouteProjection(
        "session-profile-reset-confirm", "AppSession", "Profile",
        PROFILE_PROTOCOL_PATH, "ProfilePulse.ConfirmLegacyReset", "ProfileCommandOutcome.ResetCompleted",
        SESSION_PROTOCOL_PATH, "SessionControlPulse.ProfileCommandCompleted",
        "SessionControlPulse.ProfileCommandRejectedBeforeAcceptance", SESSION_NUCLEUS_PATH,
        listOf(
            "ProfileCommandOutcome.ResetWriteRejected",
            "ProfileCommandOutcome.ResetWriteOutcomeUnknown",
            "ProfileCommandOutcome.ResetNeedsAttention",
        ),
    ),
    CommandRouteProjection(
        "session-profile-reset-retry", "AppSession", "Profile",
        PROFILE_PROTOCOL_PATH, "ProfilePulse.RetryLegacyPurge", "ProfileCommandOutcome.ResetCompleted",
        SESSION_PROTOCOL_PATH, "SessionControlPulse.ProfileCommandCompleted",
        "SessionControlPulse.ProfileCommandRejectedBeforeAcceptance", SESSION_NUCLEUS_PATH,
        listOf("ProfileCommandOutcome.ResetNeedsAttention"),
    ),
    CommandRouteProjection(
        "session-gameplay-start", "AppSession", "GameplayRun",
        GAMEPLAY_PROTOCOL_PATH, "GameplaySessionPulse.StartRun", "GameplayCommandOutcome.RunStarted",
        SESSION_PROTOCOL_PATH, "SessionControlPulse.GameplayCommandCompleted",
        "SessionControlPulse.GameplayCommandRejectedBeforeAcceptance", SESSION_NUCLEUS_PATH,
    ),
    CommandRouteProjection(
        "session-gameplay-pause", "AppSession", "GameplayRun",
        GAMEPLAY_PROTOCOL_PATH, "GameplaySessionPulse.PauseForOverlay", "GameplayCommandOutcome.OverlayPaused",
        SESSION_PROTOCOL_PATH, "SessionControlPulse.GameplayCommandCompleted",
        "SessionControlPulse.GameplayCommandRejectedBeforeAcceptance", SESSION_NUCLEUS_PATH,
    ),
    CommandRouteProjection(
        "session-gameplay-preferences", "AppSession", "GameplayRun",
        GAMEPLAY_PROTOCOL_PATH, "GameplaySessionPulse.ApplyPreferences", "GameplayCommandOutcome.PreferencesApplied",
        SESSION_PROTOCOL_PATH, "SessionControlPulse.GameplayCommandCompleted",
        "SessionControlPulse.GameplayCommandRejectedBeforeAcceptance", SESSION_NUCLEUS_PATH,
    ),
    CommandRouteProjection(
        "session-gameplay-exit", "AppSession", "GameplayRun",
        GAMEPLAY_PROTOCOL_PATH, "GameplaySessionPulse.ExitRun", "GameplayCommandOutcome.RunExited",
        SESSION_PROTOCOL_PATH, "SessionControlPulse.GameplayCommandCompleted",
        "SessionControlPulse.GameplayCommandRejectedBeforeAcceptance", SESSION_NUCLEUS_PATH,
    ),
    CommandRouteProjection(
        "gameplay-profile-progress", "GameplayRun", "Profile",
        PROFILE_PROTOCOL_PATH, "ProfilePulse.ApplyGameplayProgress", "ProfileCommandOutcome.GameplayProgressApplied",
        GAMEPLAY_PROTOCOL_PATH, "GameplayControlPulse.ProfileCommandCompleted",
        "GameplayControlPulse.ProfileCommandRejectedBeforeAcceptance", GAMEPLAY_NUCLEUS_PATH,
    ),
).sortedBy(CommandRouteProjection::id).also { routes ->
    requireUniqueKeys("commandRouteProjections", routes, CommandRouteProjection::id)
}

internal val commandOutcomeFamilies = listOf(
    CommandOutcomeFamily(
        targetAuthority = "Profile",
        ownerPath = PROFILE_PROTOCOL_PATH,
        declaration = "sealed interface ProfileCommandOutcome",
        supertype = "ProfileCommandOutcome",
    ),
    CommandOutcomeFamily(
        targetAuthority = "GameplayRun",
        ownerPath = GAMEPLAY_PROTOCOL_PATH,
        declaration = "sealed interface GameplayCommandOutcome",
        supertype = "GameplayCommandOutcome",
    ),
).also { families ->
    requireUniqueKeys("commandOutcomeFamilies", families, CommandOutcomeFamily::targetAuthority)
    requireUniqueKeys("commandOutcomeFamilies", families, CommandOutcomeFamily::supertype)
}

internal val expectedReadRoutes = sortedSetOf(
    "gameplay-content-bootstrap",
    "profile-content-policy",
    "session-content-ui",
    "session-gameplay-codex",
    "session-gameplay-status",
    "session-gameplay-weapon",
    "session-profile-collection",
    "session-profile-home",
    "session-profile-persistence",
    "session-profile-preferences",
    "session-profile-rebirth-progress",
    "session-profile-run-bootstrap",
)

internal val readRouteProjections = listOf(
    ReadRouteProjection(
        "profile-content-policy", "Profile", "ContentCatalog", CONTENT_SURFACE_PATH,
        "ContentCatalog.profilePolicy", "ProfilePolicySnapshot", APP_COMPOSITION_PATH,
    ),
    ReadRouteProjection(
        "gameplay-content-bootstrap", "GameplayRun", "ContentCatalog", CONTENT_SURFACE_PATH,
        "ContentCatalog.gameplayContent", "GameplayContentSnapshot", APP_COMPOSITION_PATH,
    ),
    ReadRouteProjection(
        "session-content-ui", "AppSession", "ContentCatalog", CONTENT_SURFACE_PATH,
        "ContentCatalog.uiCatalog", "UiCatalogSnapshot", APP_COMPOSITION_PATH,
    ),
    ReadRouteProjection(
        "session-profile-run-bootstrap", "AppSession", "Profile", PROFILE_QUERY_PATH,
        "ProfileQuery.GetRunBootstrap", "RunBootstrapProjection", SESSION_IMPL_PATH,
    ),
    ReadRouteProjection(
        "session-profile-preferences", "AppSession", "Profile", PROFILE_QUERY_PATH,
        "ProfileQuery.GetPreferences", "PreferencesProjection", SESSION_IMPL_PATH,
    ),
    ReadRouteProjection(
        "session-profile-home", "AppSession", "Profile", PROFILE_QUERY_PATH,
        "ProfileQuery.GetHomeProgress", "HomeProgressProjection", SESSION_HOME_PATH,
    ),
    ReadRouteProjection(
        "session-profile-collection", "AppSession", "Profile", PROFILE_QUERY_PATH,
        "ProfileQuery.GetCollection", "CollectionProjection", SESSION_CODEX_PATH,
    ),
    ReadRouteProjection(
        "session-profile-rebirth-progress", "AppSession", "Profile", PROFILE_QUERY_PATH,
        "ProfileQuery.GetRebirthProgress", "RebirthProgressProjection", SESSION_IMPL_PATH,
    ),
    ReadRouteProjection(
        "session-profile-persistence", "AppSession", "Profile", PROFILE_QUERY_PATH,
        "ProfileQuery.GetPersistenceStatus", "PersistenceStatusProjection", SESSION_IMPL_PATH,
    ),
    ReadRouteProjection(
        "session-gameplay-status", "AppSession", "GameplayRun", GAMEPLAY_QUERY_PATH,
        "GameplayQuery.GetRunStatus", "GameplayRunStatusProjection", SESSION_IMPL_PATH,
    ),
    ReadRouteProjection(
        "session-gameplay-weapon", "AppSession", "GameplayRun", GAMEPLAY_QUERY_PATH,
        "GameplayQuery.GetActiveWeapon", "GameplayActiveWeaponProjection", SESSION_CONTENT_PATH,
    ),
    ReadRouteProjection(
        "session-gameplay-codex", "AppSession", "GameplayRun", GAMEPLAY_QUERY_PATH,
        "GameplayQuery.GetCodexStacks", "GameplayCodexStacksProjection", SESSION_CONTENT_PATH,
    ),
).sortedBy(ReadRouteProjection::id).also { routes ->
    requireUniqueKeys("readRouteProjections", routes, ReadRouteProjection::id)
}

internal val expectedSemanticDirectControlEdges: SortedSet<String> = buildSet {
    readRouteProjections.forEach { add("${it.sourceAuthority} -> ${it.targetAuthority}") }
    commandRouteProjections.forEach { add("${it.sourceAuthority} -> ${it.targetAuthority}") }
}.toSortedSet()

internal val expectedRouteInventory = listOf(
    "Home",
    "Gameplay",
    "Settings",
    "Lab",
    "Armory",
    "Rebirth",
    "Codex",
).also { routes -> requireUniqueKeys("expectedRouteInventory", routes) { it } }

internal val expectedBounds = listOf(
    BoundProjection(
        "profile.outputs-per-decision", "2",
        "ball/profile/nucleus/src/commonMain/kotlin/kinetickk/ball/profile/nucleus/ProfileDecision.kt",
        "MAX_PROFILE_OUTPUTS_PER_DECISION: Int = 2",
        "ball/profile/nucleus/src/commonTest/kotlin/kinetickk/ball/profile/nucleus/ProfileNucleusTest.kt",
        "profileAcceptedFrameAcceptsTwoAndRejectsThirdOutput",
    ),
    BoundProjection(
        "gameplay.outputs-per-decision", "3",
        "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/GameplayDecision.kt",
        "MAX_GAMEPLAY_OUTPUTS_PER_DECISION: Int = 3",
        "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/GameplayNucleusTest.kt",
        "acceptedFrameAllowsThreeSemanticOutputsButRejectsFourth",
    ),
    BoundProjection(
        "session.outputs-per-decision", "3",
        "flow/session/nucleus/src/commonMain/kotlin/kinetickk/flow/session/nucleus/AppSessionDecision.kt",
        "MAX_SESSION_OUTPUTS_PER_DECISION: Int = 3",
        "flow/session/nucleus/src/commonTest/kotlin/kinetickk/flow/session/nucleus/AppSessionNucleusTest.kt",
        "acceptedFrameEnforcesOutputBoundAndEnsureParticipantFeedbackOrder",
    ),
    BoundProjection(
        "profile.resource-effects-per-decision", "1", PROFILE_IMPL_PATH,
        "effectCount in 0..1", PROFILE_IMPL_TEST_PATH,
        "acceptorCausalDepthAndResourceEffectBoundsAcceptNAndRefuseNPlusOne",
    ),
    BoundProjection(
        "session.participant-command-fanout", "1",
        "flow/session/nucleus/src/commonMain/kotlin/kinetickk/flow/session/nucleus/AppSessionDecision.kt",
        "outputs.count(AppSessionOutput::isParticipantCommand) <= 1",
        "flow/session/nucleus/src/commonTest/kotlin/kinetickk/flow/session/nucleus/AppSessionNucleusTest.kt",
        "acceptedFrameEnforcesOutputBoundAndEnsureParticipantFeedbackOrder",
    ),
    BoundProjection(
        "session.ensure-gameplay-run-fanout", "1",
        "flow/session/nucleus/src/commonMain/kotlin/kinetickk/flow/session/nucleus/AppSessionDecision.kt",
        "ensures.size <= 1",
        "flow/session/nucleus/src/commonTest/kotlin/kinetickk/flow/session/nucleus/AppSessionNucleusTest.kt",
        "acceptedFrameEnforcesOutputBoundAndEnsureParticipantFeedbackOrder",
    ),
    BoundProjection(
        "gameplay.profile-command-fanout", "1", GAMEPLAY_IMPL_PATH,
        "profileCommandCount in 0..1",
        "ball/gameplay/impl/src/commonTest/kotlin/kinetickk/ball/gameplay/impl/GameComponentTest.kt",
        "acceptorCausalDepthAndProfileOutputFanoutAcceptNAndRefuseNPlusOne",
    ),
    BoundProjection(
        "same-stack.causal-depth", "8",
        "flow/session/impl/src/commonMain/kotlin/kinetickk/flow/session/impl/DefaultAppSessionComponent.kt",
        "MAX_SESSION_CAUSAL_DEPTH: Int = 8",
        PROFILE_IMPL_TEST_PATH,
        "acceptorCausalDepthAndResourceEffectBoundsAcceptNAndRefuseNPlusOne",
        additionalRequiredTokens = listOf("causalDepth < MAX_SESSION_CAUSAL_DEPTH"),
        additionalSourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_IMPL_PATH,
                listOf("MAX_GAMEPLAY_CAUSAL_DEPTH: Int = 8", "causalDepth < MAX_GAMEPLAY_CAUSAL_DEPTH"),
            ),
            BoundAnchor(
                PROFILE_IMPL_PATH,
                listOf("MAX_PROFILE_CAUSAL_DEPTH: Int = 8", "causalDepth < MAX_PROFILE_CAUSAL_DEPTH"),
            ),
        ),
        additionalEvidenceAnchors = listOf(
            BoundAnchor(
                "ball/gameplay/impl/src/commonTest/kotlin/kinetickk/ball/gameplay/impl/GameComponentTest.kt",
                "acceptorCausalDepthAndProfileOutputFanoutAcceptNAndRefuseNPlusOne",
            ),
            BoundAnchor(
                SESSION_IMPL_TEST_PATH,
                "acceptorCausalDepthAndOutputFanoutAcceptNAndRefuseNPlusOne",
            ),
        ),
    ),
    BoundProjection(
        "profile.completion-deque-capacity", "8", PROFILE_IMPL_PATH,
        "PROFILE_COMPLETION_CAPACITY: Int = 8", PROFILE_IMPL_TEST_PATH,
        "deployedCompletionQueueAcceptsEightAndRefusesNinthWithoutTruncation",
        additionalRequiredTokens = listOf("BoundedCompletionDeque(PROFILE_COMPLETION_CAPACITY)"),
    ),
    BoundProjection(
        "gameplay.completion-deque-capacity", "8", GAMEPLAY_IMPL_PATH,
        "GAMEPLAY_COMPLETION_CAPACITY: Int = 8",
        "ball/gameplay/impl/src/commonTest/kotlin/kinetickk/ball/gameplay/impl/GameComponentTest.kt",
        "deployedCompletionQueueAcceptsEightAndRefusesNinthWithoutTruncation",
        additionalRequiredTokens = listOf("BoundedCompletionDeque(GAMEPLAY_COMPLETION_CAPACITY)"),
    ),
    BoundProjection(
        "session.completion-deque-capacity", "8",
        "flow/session/impl/src/commonMain/kotlin/kinetickk/flow/session/impl/DefaultAppSessionComponent.kt",
        "SESSION_COMPLETION_CAPACITY: Int = 8", SESSION_IMPL_TEST_PATH,
        "deployedCompletionQueueAcceptsEightAndRefusesNinthWithoutTruncation",
        additionalRequiredTokens = listOf("BoundedCompletionDeque(SESSION_COMPLETION_CAPACITY)"),
    ),
    BoundProjection(
        "session.pending-participant-commands", "1", SESSION_STATE_PATH,
        "val pendingWorkflow: PendingWorkflow?",
        "flow/session/nucleus/src/commonTest/kotlin/kinetickk/flow/session/nucleus/AppSessionNucleusTest.kt",
        "pendingWorkflowAcceptsOneParticipantCommandAndRejectsTheSecond",
        additionalSourceAnchors = listOf(
            BoundAnchor(SESSION_NUCLEUS_PATH, "if (state.pendingWorkflow != null)"),
        ),
    ),
    BoundProjection(
        "session.participant-authorities", "2", SESSION_STATE_PATH,
        "sealed interface PendingParticipantCommand",
        additionalRequiredTokens = listOf(
            "data class Profile(val command: ProfileCommand)",
            "data class Gameplay(val command: GameplayCommand)",
        ),
    ),
    BoundProjection(
        "gameplay.pending-profile-commands", "1",
        "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/GameplayState.kt",
        "val pendingProfileCommand: PendingProfileCommand?",
        "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/GameplayNucleusTest.kt",
        "atMostOneProfileCommandCanBePending",
    ),
    BoundProjection(
        "gameplay.active-runs", "1",
        "ball/gameplay/impl/src/commonMain/kotlin/kinetickk/ball/gameplay/impl/DefaultGameplayFeature.kt",
        "private var componentValue by mutableStateOf<GameComponent?>(null)",
        "ball/gameplay/impl/src/commonTest/kotlin/kinetickk/ball/gameplay/impl/GameComponentTest.kt",
        "defaultFeatureEnforcesActiveRunTerminalPendingAndMonotonicReplacementRules",
    ),
    BoundProjection(
        "gameplay.fixed-steps-per-render-frame", "48",
        GAMEPLAY_LOOP_PATH,
        "MAX_FIXED_STEPS_PER_FRAME: Int = 48",
        GAMEPLAY_ARCHITECTURE_BOUNDS_TEST_PATH,
        "fixedStepWorkAcceptsFortyEightAndDefersFortyNinth",
    ),
    BoundProjection(
        "gameplay.simulation-raw-delta-seconds", "0.1 maximum", GAMEPLAY_LOOP_PATH,
        "MAX_SIMULATION_RAW_DELTA_SECONDS: Float = 0.1f",
        GAMEPLAY_ARCHITECTURE_BOUNDS_TEST_PATH,
        "simulationDeltaAndAccumulatorClampFirstNPlusOneToExactCaps",
        additionalRequiredTokens = listOf(
            "rawDeltaSeconds.coerceIn(0f, MAX_SIMULATION_RAW_DELTA_SECONDS)",
            "accumulator = nextSimulationAccumulator(",
        ),
    ),
    BoundProjection(
        "gameplay.simulation-accumulator-seconds", "0.3 maximum", GAMEPLAY_LOOP_PATH,
        "MAX_SIMULATION_ACCUMULATOR_SECONDS: Float = 0.3f",
        GAMEPLAY_ARCHITECTURE_BOUNDS_TEST_PATH,
        "simulationDeltaAndAccumulatorClampFirstNPlusOneToExactCaps",
        additionalRequiredTokens = listOf(
            "nextSimulationAccumulator(",
            "accumulator = nextSimulationAccumulator(",
        ),
    ),
    BoundProjection(
        "gameplay.enemies", "120",
        "ball/content/impl/src/commonMain/kotlin/kinetickk/ball/content/impl/DefaultRebirthData.kt",
        "DEFAULT_MAX_ACTIVE_ENEMIES: Int = 120",
        "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/characterization/GameplayBaselineCharacterizationTest.kt",
        "authoritativeCollectionsEnforceTheCurrentNPlusOneCaps",
        additionalEvidenceAnchors = listOf(
            BoundAnchor(
                "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/characterization/TotemEnemySystemsTest.kt",
                "splitterFragmentsAcceptTheDynamicEnemyCapAndRejectTheNextCandidate",
            ),
        ),
    ),
    BoundProjection(
        "gameplay.projectiles", "650", GAMEPLAY_STATE_PATH, "MAX_PROJECTILES = 650",
        "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/characterization/GameplayBaselineCharacterizationTest.kt",
        "authoritativeCollectionsEnforceTheCurrentNPlusOneCaps",
    ),
    BoundProjection(
        "gameplay.pickups", "420", GAMEPLAY_STATE_PATH, "MAX_PICKUPS = 420",
        "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/characterization/GameplayBaselineCharacterizationTest.kt",
        "authoritativeCollectionsEnforceTheCurrentNPlusOneCaps",
    ),
    BoundProjection(
        "gameplay.trail-points", "110", GAMEPLAY_STATE_PATH, "MAX_TRAIL_POINTS = 110",
        "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/characterization/GameplayBaselineCharacterizationTest.kt",
        "authoritativeCollectionsEnforceTheCurrentNPlusOneCaps",
    ),
    BoundProjection(
        "gameplay.delayed-relic-hits", "256", GAMEPLAY_STATE_PATH, "MAX_DELAYED_RELIC_HITS = 256",
        "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/characterization/GameplayArchitectureBoundsTest.kt",
        "delayedRelicHitBoundAcceptsNAndRejectsNPlusOneCandidate",
    ),
    BoundProjection(
        "gameplay.projectile-hit-history", "120",
        "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/model/GameEntities.kt",
        "MAX_HIT_ENEMY_IDS = 120", GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH,
        "projectileHitHistoryAcceptsOneHundredTwentyRejectsNextThenReclaimsDeadEntry",
    ),
    BoundProjection(
        "gameplay.sound-cues", "32", GAMEPLAY_STATE_PATH, "MAX_GAMEPLAY_SOUND_CUES = 32",
        GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH, "soundCuesAcceptThirtyTwoAndRejectThirtyThird",
    ),
    BoundProjection(
        "gameplay.weapon-nodes", "8", GAMEPLAY_STATE_PATH, "MAX_WEAPON_NODES = 8",
        GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH, "weaponNodesAcceptEightAndRejectNinth",
    ),
    BoundProjection(
        "gameplay.weapon-orbitals", "8", GAMEPLAY_STATE_PATH, "MAX_WEAPON_ORBITALS = 8",
        GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH, "weaponOrbitalsAcceptEightAndRejectNinthRequested",
    ),
    BoundProjection(
        "gameplay.choice-options", "4", GAMEPLAY_STATE_PATH, "MAX_CHOICES = 4",
        GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH, "choiceInventoryAcceptsFourAndRejectsFifthAtomically",
    ),
    BoundProjection(
        "gameplay.generated-reward-choices", "3", GAMEPLAY_PROGRESSION_SYSTEM_PATH,
        "MAX_GENERATED_REWARD_CHOICES: Int = 3", GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH,
        "rewardChoiceGeneratorsSelectThreeFromLargerCandidatePools",
        additionalRequiredTokens = listOf(
            "repeat(MAX_GENERATED_REWARD_CHOICES)",
            ".take(MAX_GENERATED_REWARD_CHOICES)",
        ),
    ),
    BoundProjection(
        "gameplay.arc-coil-targets", "6", GAMEPLAY_WEAPON_SYSTEM_PATH,
        "MAX_ARC_COIL_TARGETS: Int = 6", GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH,
        "arcCoilTargetsSixNearestAndLeavesSeventhUntouched",
        additionalRequiredTokens = listOf(
            ".take(min(MAX_ARC_COIL_TARGETS, 3 + weaponLevel / 3))",
        ),
    ),
    BoundProjection(
        "gameplay.trail-samples-per-update", "32", GAMEPLAY_STATE_PATH,
        "MAX_TRAIL_SAMPLES_PER_UPDATE = 32", GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH,
        "trailSamplerProcessesThirtyTwoAndDropsThirtyThirdSample",
    ),
    BoundProjection(
        "gameplay.visual-fx-cues", "2048",
        "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/protocol/VisualFxProtocol.kt",
        "MAX_CUES_PER_PROJECTION = 2_048",
        "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/protocol/BoundedVisualFxCueAccumulatorTest.kt",
        "outputCapTwoThousandFortyEightReportsAttemptedTwoThousandFortyNinthWithoutGrowth",
    ),
    BoundProjection(
        "gameplay.interaction-particles", "700", GAMEPLAY_FX_PATH, "MAX_PARTICLES = 700",
        GAMEPLAY_FX_TEST_PATH, "particlesAcceptSevenHundredAndRejectSevenHundredFirst",
        "directionalParticlesAcceptSevenHundredAndRejectSevenHundredFirst",
    ),
    BoundProjection(
        "gameplay.interaction-motion-echoes", "36", GAMEPLAY_FX_PATH, "MAX_MOTION_ECHOES = 36",
        GAMEPLAY_FX_TEST_PATH, "motionEchoesAcceptThirtySixAndTrimOldestOnThirtySeventh",
    ),
    BoundProjection(
        "gameplay.interaction-shockwaves", "48", GAMEPLAY_FX_PATH, "MAX_SHOCKWAVES = 48",
        GAMEPLAY_FX_TEST_PATH, "shockwavesAcceptFortyEightAndTrimOldestOnFortyNinth",
    ),
    BoundProjection(
        "gameplay.interaction-damage-numbers", "140", GAMEPLAY_FX_PATH, "MAX_DAMAGE_NUMBERS = 140",
        GAMEPLAY_FX_TEST_PATH, "damageNumbersAcceptOneHundredFortyAndRejectOneHundredFortyFirst",
    ),
    BoundProjection(
        "gameplay.interaction-weapon-arcs", "128", GAMEPLAY_FX_PATH, "MAX_WEAPON_ARCS = 128",
        GAMEPLAY_FX_TEST_PATH, "weaponArcsAcceptOneHundredTwentyEightAndTrimOldestOnOneHundredTwentyNinth",
    ),
    BoundProjection(
        "gameplay.presentation-frame-delta-seconds", "0.1 maximum", GAMEPLAY_INTERACTION_PATH,
        "MAX_GAMEPLAY_PRESENTATION_DELTA_SECONDS: Float = 0.1f", GAMEPLAY_INGRESS_TEST_PATH,
        "presentationDeltaAcceptsPointOneAndClampsFirstNPlusOne",
        additionalRequiredTokens = listOf(
            "selectGameplayPresentationDelta(",
            "renderTimeSecondsValue += selectGameplayPresentationDelta(",
        ),
    ),
    BoundProjection(
        "home.presentation-frame-delta-seconds", "0.1 maximum", SESSION_HOME_PATH,
        "MAX_HOME_PRESENTATION_FRAME_DELTA_SECONDS: Float = 0.1f", SESSION_HOME_TEST_PATH,
        "presentationClockAcceptsMaximumAndClampsNextRepresentableDelta",
        additionalRequiredTokens = listOf(
            "selectHomePresentationFrameDeltaSeconds(",
            "renderTimeSecondsValue += selectHomePresentationFrameDeltaSeconds(",
        ),
    ),
    BoundProjection(
        "armory.presentation-frame-delta-seconds", "0.1 maximum", PROFILE_ARMORY_FEATURE_PATH,
        "MAX_ARMORY_PRESENTATION_FRAME_DELTA_SECONDS: Float = 0.1f", PROFILE_ARMORY_TEST_PATH,
        "presentationClockAcceptsMaximumAndClampsNextRepresentableDelta",
        additionalRequiredTokens = listOf(
            "selectArmoryPresentationFrameDeltaSeconds(",
            "renderTimeSecondsValue += selectArmoryPresentationFrameDeltaSeconds(",
        ),
    ),
    BoundProjection(
        "codex.page-slice-items", "10", SESSION_CODEX_STATE_PATH,
        "CODEX_PAGE_SIZE = 10", SESSION_CODEX_TEST_PATH,
        "pageSliceReturnsTenForExactAndFirstNPlusOneInputs",
        additionalRequiredTokens = listOf("codexPageSlice"),
        additionalSourceAnchors = listOf(
            BoundAnchor(SESSION_CODEX_PATH, "codexPageSlice(engine.items, page)"),
        ),
    ),
    BoundProjection(
        "armory.page-slice-weapons", "3", PROFILE_ARMORY_STATE_PATH,
        "ARMORY_PAGE_SIZE = 3", PROFILE_ARMORY_TEST_PATH,
        "pageSliceReturnsThreeForExactAndFirstNPlusOneInputs",
        additionalRequiredTokens = listOf("armoryPageSlice"),
        additionalSourceAnchors = listOf(
            BoundAnchor(PROFILE_ARMORY_FEATURE_PATH, "armoryPageSlice(weapons, page)"),
        ),
    ),
    BoundProjection(
        "gameplay.interaction-frame-delta", "0..1", GAMEPLAY_INGRESS_PATH,
        "MIN_FRAME_DELTA_SECONDS = 0f", GAMEPLAY_INGRESS_TEST_PATH,
        "frameDeltaAcceptsExactBoundsAndRejectsTheNextRepresentableValues",
        additionalRequiredTokens = listOf("MAX_FRAME_DELTA_SECONDS = 1f"),
    ),
    BoundProjection(
        "gameplay.interaction-viewport", "1..32768", GAMEPLAY_INGRESS_PATH,
        "MIN_VIEWPORT_DIMENSION_PX = 1f", GAMEPLAY_INGRESS_TEST_PATH,
        "viewportAcceptsNAndRejectsNPlusOneForEveryBoundedField",
        additionalRequiredTokens = listOf("MAX_VIEWPORT_DIMENSION_PX = 32_768f"),
    ),
    BoundProjection(
        "gameplay.interaction-density", "0.5..8", GAMEPLAY_INGRESS_PATH,
        "MIN_DENSITY = 0.5f", GAMEPLAY_INGRESS_TEST_PATH,
        "viewportAcceptsNAndRejectsNPlusOneForEveryBoundedField",
        additionalRequiredTokens = listOf("MAX_DENSITY = 8f"),
    ),
    BoundProjection(
        "gameplay.interaction-pointer", "0..viewport", GAMEPLAY_INGRESS_PATH,
        "field = InteractionInputField.POINTER_X_PX", GAMEPLAY_INGRESS_TEST_PATH,
        "pointerCoordinatesRequireAViewportAndUseStrictInclusiveBounds",
        additionalRequiredTokens = listOf("minimum = 0f", "maximum = viewport.width", "maximum = viewport.height"),
    ),
    BoundProjection(
        "gameplay.authoritative-frame-delta", "0..1", GAMEPLAY_REDUCER_PATH,
        "MIN_FRAME_DELTA_SECONDS = 0f", GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH,
        "authoritativeIngressAcceptsExactBoundsAndRejectsNextRepresentableValues",
        additionalRequiredTokens = listOf(
            "MAX_FRAME_DELTA_SECONDS = 1f",
            "minimum = MIN_FRAME_DELTA_SECONDS",
            "maximum = MAX_FRAME_DELTA_SECONDS",
        ),
        additionalSourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_INGRESS_PATH,
                listOf("MIN_FRAME_DELTA_SECONDS = 0f", "MAX_FRAME_DELTA_SECONDS = 1f"),
            ),
        ),
        additionalEvidenceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_INGRESS_TEST_PATH,
                "frameDeltaAcceptsExactBoundsAndRejectsTheNextRepresentableValues",
            ),
        ),
    ),
    BoundProjection(
        "gameplay.authoritative-viewport", "1..32768", GAMEPLAY_REDUCER_PATH,
        "MIN_VIEWPORT_DIMENSION = 1f", GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH,
        "authoritativeIngressAcceptsExactBoundsAndRejectsNextRepresentableValues",
        additionalRequiredTokens = listOf("MAX_VIEWPORT_DIMENSION = 32_768f"),
        additionalSourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_INGRESS_PATH,
                listOf("MIN_VIEWPORT_DIMENSION_PX = 1f", "MAX_VIEWPORT_DIMENSION_PX = 32_768f"),
            ),
        ),
        additionalEvidenceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_INGRESS_TEST_PATH,
                "viewportAcceptsNAndRejectsNPlusOneForEveryBoundedField",
            ),
        ),
    ),
    BoundProjection(
        "gameplay.authoritative-density", "0.5..8", GAMEPLAY_REDUCER_PATH,
        "MIN_DENSITY = 0.5f", GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH,
        "authoritativeIngressAcceptsExactBoundsAndRejectsNextRepresentableValues",
        additionalRequiredTokens = listOf("MAX_DENSITY = 8f"),
        additionalSourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_INGRESS_PATH,
                listOf("MIN_DENSITY = 0.5f", "MAX_DENSITY = 8f"),
            ),
        ),
        additionalEvidenceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_INGRESS_TEST_PATH,
                "viewportAcceptsNAndRejectsNPlusOneForEveryBoundedField",
            ),
        ),
    ),
    BoundProjection(
        "gameplay.authoritative-pointer", "0..viewport", GAMEPLAY_REDUCER_PATH,
        "bounded(GameplayInputField.POINTER_X, intent.x, 0f, state.model.screenWidth)",
        GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH,
        "authoritativeIngressAcceptsExactBoundsAndRejectsNextRepresentableValues",
        additionalRequiredTokens = listOf(
            "bounded(GameplayInputField.POINTER_Y, intent.y, 0f, state.model.screenHeight)",
        ),
        additionalSourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_INGRESS_PATH,
                listOf("minimum = 0f", "maximum = viewport.width", "maximum = viewport.height"),
            ),
        ),
        additionalEvidenceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_INGRESS_TEST_PATH,
                "pointerCoordinatesRequireAViewportAndUseStrictInclusiveBounds",
            ),
        ),
    ),
    BoundProjection(
        "gameplay.choice-index", "0..3",
        GAMEPLAY_REDUCER_PATH,
        "intent.index < 0", GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH,
        "choiceIndexThreeIsAcceptedAndFourIsRejected",
        additionalRequiredTokens = listOf("intent.index >= MutableGameState.MAX_CHOICES"),
        additionalSourceAnchors = listOf(
            BoundAnchor(GAMEPLAY_STATE_PATH, "MAX_CHOICES = 4"),
        ),
    ),
    BoundProjection(
        "audio.accepted-caller-effect-requests", "32", AUDIO_SERVICE_PATH,
        "MAX_ACCEPTED_EFFECT_REQUESTS_PER_ADVANCE = 32",
        AUDIO_SERVICE_TEST_PATH, "callerEffectIngressAcceptsThirtyTwoAndRejectsThirtyThird",
    ),
    BoundProjection(
        "audio.selected-caller-effect-requests", "3", AUDIO_SERVICE_PATH,
        "MAX_SELECTED_EFFECT_REQUESTS_PER_ADVANCE = 3",
        AUDIO_SERVICE_TEST_PATH, "callerEffectRequestSelectionAcceptsThreeAndDropsFourth",
    ),
    BoundProjection(
        "audio.music-advance-delta-seconds", "0.1 maximum", AUDIO_SERVICE_PATH,
        "MAX_MUSIC_ADVANCE_DELTA_SECONDS: Float = 0.1f", AUDIO_SERVICE_TEST_PATH,
        "musicAdvanceDeltaAcceptsMaximumAndClampsNextRepresentableValue",
        additionalRequiredTokens = listOf(
            "selectMusicAdvanceDeltaSeconds(",
            "musicClock -= selectMusicAdvanceDeltaSeconds(realDeltaSeconds)",
        ),
    ),
    BoundProjection(
        "audio.tone-frequency-hz", "20..20000",
        "resource/audio/api/src/commonMain/kotlin/kinetickk/resource/audio/api/AudioService.kt",
        "MIN_FREQUENCY_HZ: Float = 20f", AUDIO_SERVICE_TEST_PATH,
        "toneRequestIngressAcceptsInclusiveBoundsAndRejectsNextRepresentableValues",
        additionalRequiredTokens = listOf("MAX_FREQUENCY_HZ: Float = 20_000f"),
    ),
    BoundProjection(
        "audio.tone-duration-seconds", "0.001..1",
        "resource/audio/api/src/commonMain/kotlin/kinetickk/resource/audio/api/AudioService.kt",
        "MIN_DURATION_SECONDS: Float = 0.001f", AUDIO_SERVICE_TEST_PATH,
        "toneRequestIngressAcceptsInclusiveBoundsAndRejectsNextRepresentableValues",
        additionalRequiredTokens = listOf("MAX_DURATION_SECONDS: Float = 1f"),
    ),
    BoundProjection(
        "audio.tone-gain", "0..1",
        "resource/audio/api/src/commonMain/kotlin/kinetickk/resource/audio/api/AudioService.kt",
        "MIN_GAIN: Float = 0f", AUDIO_SERVICE_TEST_PATH,
        "toneRequestIngressAcceptsInclusiveBoundsAndRejectsNextRepresentableValues",
        additionalRequiredTokens = listOf("MAX_GAIN: Float = 1f"),
    ),
    BoundProjection(
        "audio.desktop-workers", "1", DESKTOP_AUDIO_PATH, "WORKER_COUNT = 1",
        DESKTOP_AUDIO_TEST_PATH, "workerAndDiscardOldestQueueEnforceOneAndTwentyFour",
    ),
    BoundProjection(
        "audio.desktop-queue-capacity", "24", DESKTOP_AUDIO_PATH, "QUEUE_CAPACITY = 24",
        DESKTOP_AUDIO_TEST_PATH, "workerAndDiscardOldestQueueEnforceOneAndTwentyFour",
        additionalRequiredTokens = listOf("ThreadPoolExecutor.DiscardOldestPolicy()"),
    ),
    BoundProjection(
        "audio.desktop-synthesis-samples", "22050", DESKTOP_AUDIO_PATH,
        "MAX_SAMPLE_COUNT = SAMPLE_RATE", DESKTOP_AUDIO_TEST_PATH,
        "synthesisBufferAcceptsMaximumDurationAndRejectsNext",
        additionalRequiredTokens = listOf("SAMPLE_RATE = 22_050"),
    ),
    BoundProjection(
        "audio.desktop-synthesis-bytes", "44100", DESKTOP_AUDIO_PATH,
        "MAX_PCM_BYTES = MAX_SAMPLE_COUNT * BYTES_PER_SAMPLE", DESKTOP_AUDIO_TEST_PATH,
        "synthesisBufferAcceptsMaximumDurationAndRejectsNext",
        additionalRequiredTokens = listOf("BYTES_PER_SAMPLE = 2"),
    ),
    BoundProjection(
        "content.items", "400",
        "ball/content/api/src/commonMain/kotlin/kinetickk/ball/content/api/ContentVersion.kt",
        "MAX_ITEMS: Int = 400", CONTENT_BOUNDS_TEST_PATH,
        "itemBoundRejectsNPlusOne", "exactCatalogBoundsAreAccepted",
    ),
    BoundProjection(
        "content.weapons", "12",
        "ball/content/api/src/commonMain/kotlin/kinetickk/ball/content/api/ContentVersion.kt",
        "MAX_WEAPONS: Int = 12", CONTENT_BOUNDS_TEST_PATH,
        "weaponBoundRejectsNPlusOne", "exactCatalogBoundsAreAccepted",
    ),
    BoundProjection(
        "content.meta-upgrades", "8",
        "ball/content/api/src/commonMain/kotlin/kinetickk/ball/content/api/ContentVersion.kt",
        "MAX_META_UPGRADES: Int = 8", CONTENT_BOUNDS_TEST_PATH,
        "metaUpgradeBoundRejectsNPlusOne", "exactCatalogBoundsAreAccepted",
    ),
    BoundProjection(
        "content.relics", "40",
        "ball/content/api/src/commonMain/kotlin/kinetickk/ball/content/api/ContentVersion.kt",
        "MAX_RELICS: Int = 40", CONTENT_BOUNDS_TEST_PATH,
        "relicBoundRejectsNPlusOne", "exactCatalogBoundsAreAccepted",
    ),
    BoundProjection(
        "content.rebirth-level", "0..10",
        "ball/content/api/src/commonMain/kotlin/kinetickk/ball/content/api/ContentVersion.kt",
        "MAX_REBIRTH_LEVEL: Int = 10", CONTENT_BOUNDS_TEST_PATH,
        "rebirthBoundRejectsLevelEleven", "exactCatalogBoundsAreAccepted",
    ),
    BoundProjection(
        "content.relic-slots", "4",
        "ball/content/impl/src/commonMain/kotlin/kinetickk/ball/content/impl/DefaultContentCatalog.kt",
        "RelicPolicy(maxSlots = 4, maxRank = 5)",
        "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/characterization/RelicSystemTest.kt",
        "relicMatrixStopsAtFourSlotsAndDuplicateRanksStopAtFive",
    ),
    BoundProjection(
        "content.relic-rank", "1..5",
        "ball/content/impl/src/commonMain/kotlin/kinetickk/ball/content/impl/DefaultContentCatalog.kt",
        "RelicPolicy(maxSlots = 4, maxRank = 5)",
        "ball/content/impl/src/commonTest/kotlin/kinetickk/ball/content/impl/RelicCatalogTest.kt",
        "relicCapacityAndEquippedRankBoundariesAreCapturedPolicy",
        additionalSourceAnchors = listOf(
            BoundAnchor(
                "ball/content/api/src/commonMain/kotlin/kinetickk/ball/content/api/RelicContent.kt",
                "rank in 1..maxRank",
            ),
        ),
        additionalEvidenceAnchors = listOf(
            BoundAnchor(
                "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/characterization/RelicSystemTest.kt",
                listOf(
                    "relicMatrixStopsAtFourSlotsAndDuplicateRanksStopAtFive",
                    "meldRelicAcceptsRankFiveAndSalvagesTheFirstRankSixCandidate",
                ),
            ),
        ),
    ),
    BoundProjection(
        "profile.retained-lab-ranks", "policy.metaUpgrades.size (<=8)",
        PROFILE_NUCLEUS_PATH, "profile.labProgress.ranks.size != policy.metaUpgrades.size",
        PROFILE_NUCLEUS_TEST_PATH,
        "bootstrapRetainsSchemaMaximumUnlockedWeaponsLabRanksAndDiscoveries",
        "bootstrapRejectsFirstExtraLabRankRankOverflowAndFirstExtraDiscovery",
        additionalSourceAnchors = listOf(
            BoundAnchor(
                PROFILE_CODEC_PATH,
                listOf(
                    "profile.labProgress.ranks.size == MetaUpgradeId.entries.size",
                    "profile.labProgress.ranks.size == ContentBounds.MAX_META_UPGRADES",
                ),
            ),
        ),
        additionalEvidenceAnchors = listOf(
            BoundAnchor(
                PROFILE_CODEC_TEST_PATH,
                listOf(
                    "schemaMaximumUnlockedWeaponsLabRanksAndDiscoveriesRoundTripWithoutLoss",
                    "outboundLabRanksAndDiscoveriesRejectFirstNPlusOne",
                ),
            ),
        ),
    ),
    BoundProjection(
        "profile.retained-meta-upgrade-rank", "0..captured maxRanks",
        PROFILE_NUCLEUS_PATH,
        "profile.labProgress.rank(definition.id) !in 0..definition.maxRanks",
        PROFILE_NUCLEUS_TEST_PATH,
        "bootstrapRetainsSchemaMaximumUnlockedWeaponsLabRanksAndDiscoveries",
        "bootstrapRejectsFirstExtraLabRankRankOverflowAndFirstExtraDiscovery",
    ),
    BoundProjection(
        "profile.retained-discoveries", "policy.itemCount (<=400)",
        PROFILE_NUCLEUS_PATH,
        "profile.collection.discoveredItemIds.size > policy.itemCount",
        PROFILE_NUCLEUS_TEST_PATH,
        "bootstrapRetainsSchemaMaximumUnlockedWeaponsLabRanksAndDiscoveries",
        "bootstrapRejectsFirstExtraLabRankRankOverflowAndFirstExtraDiscovery",
        additionalRequiredTokens = listOf(
            "profile.collection.discoveredItemIds.any { !policy.containsItem(it) }",
        ),
        additionalSourceAnchors = listOf(
            BoundAnchor(
                PROFILE_CODEC_PATH,
                listOf(
                    "profile.collection.discoveredItemIds.size <= ContentBounds.MAX_ITEMS",
                    "profile.collection.discoveredItemIds.all { it >= 0 }",
                ),
            ),
        ),
        additionalEvidenceAnchors = listOf(
            BoundAnchor(
                PROFILE_CODEC_TEST_PATH,
                listOf(
                    "schemaMaximumUnlockedWeaponsLabRanksAndDiscoveriesRoundTripWithoutLoss",
                    "outboundLabRanksAndDiscoveriesRejectFirstNPlusOne",
                ),
            ),
        ),
    ),
    BoundProjection(
        "profile.preference-master-volume", "0..1", PROFILE_PLAYER_PATH,
        "masterVolume = masterVolume.coerceIn(0f, 1f)", PROFILE_NUCLEUS_TEST_PATH,
        "bootstrapPreferencesAcceptExactMaximaAndRejectOverflowOrOutOfSchemaValues",
        additionalSourceAnchors = listOf(
            BoundAnchor(PROFILE_NUCLEUS_PATH, "preferences != preferences.normalized()"),
            BoundAnchor(PROFILE_CODEC_PATH, "preferences.masterVolume in 0f..1f"),
            BoundAnchor(GAMEPLAY_NUCLEUS_PATH, "preferences == preferences.normalized()"),
        ),
        additionalEvidenceAnchors = listOf(
            BoundAnchor(
                PROFILE_CODEC_TEST_PATH,
                "preferenceConfigurationAcceptsExactMaximaAndRejectsAdjacentOverflowOrNonMembers",
            ),
        ),
    ),
    BoundProjection(
        "profile.preference-simulation-speed",
        "declared options 0.75,1,1.15,1.35,1.6,2", PROFILE_PLAYER_PATH,
        "SIMULATION_SPEED_OPTIONS: ImmutableList<Float> = immutableListOf(0.75f, 1f, 1.15f, 1.35f, 1.6f, 2f)",
        PROFILE_NUCLEUS_TEST_PATH,
        "bootstrapPreferencesAcceptExactMaximaAndRejectOverflowOrOutOfSchemaValues",
        additionalSourceAnchors = listOf(
            BoundAnchor(PROFILE_NUCLEUS_PATH, "preferences.simulationSpeed !in SIMULATION_SPEED_OPTIONS"),
            BoundAnchor(PROFILE_CODEC_PATH, "preferences.simulationSpeed in SIMULATION_SPEED_OPTIONS"),
            BoundAnchor(GAMEPLAY_NUCLEUS_PATH, "preferences.simulationSpeed in SIMULATION_SPEED_OPTIONS"),
        ),
        additionalEvidenceAnchors = listOf(
            BoundAnchor(
                PROFILE_CODEC_TEST_PATH,
                listOf(
                    "preferenceConfigurationAcceptsExactMaximaAndRejectsAdjacentOverflowOrNonMembers",
                    "preferenceIngressAcceptsMembersAndRejectsAdjacentInRangeNonMembers",
                ),
            ),
        ),
    ),
    BoundProjection(
        "profile.preference-text-scale", "1..1.75", PROFILE_PLAYER_PATH,
        "textScale = textScale.coerceIn(1f, 1.75f)", PROFILE_NUCLEUS_TEST_PATH,
        "bootstrapPreferencesAcceptExactMaximaAndRejectOverflowOrOutOfSchemaValues",
        additionalSourceAnchors = listOf(
            BoundAnchor(PROFILE_NUCLEUS_PATH, "preferences != preferences.normalized()"),
            BoundAnchor(PROFILE_CODEC_PATH, "preferences.textScale in 1f..1.75f"),
            BoundAnchor(GAMEPLAY_NUCLEUS_PATH, "preferences == preferences.normalized()"),
        ),
        additionalEvidenceAnchors = listOf(
            BoundAnchor(
                PROFILE_CODEC_TEST_PATH,
                "preferenceConfigurationAcceptsExactMaximaAndRejectsAdjacentOverflowOrNonMembers",
            ),
        ),
    ),
    BoundProjection(
        "profile.preference-damage-tier-threshold", "declared threshold option set",
        PROFILE_PLAYER_PATH,
        "DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS: ImmutableList<Int> = immutableListOf(",
        PROFILE_NUCLEUS_TEST_PATH,
        "bootstrapPreferencesAcceptExactMaximaAndRejectOverflowOrOutOfSchemaValues",
        additionalSourceAnchors = listOf(
            BoundAnchor(
                PROFILE_NUCLEUS_PATH,
                "preferences.damageNumberTierThreshold !in DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS",
            ),
            BoundAnchor(
                PROFILE_CODEC_PATH,
                "preferences.damageNumberTierThreshold in DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS",
            ),
            BoundAnchor(
                GAMEPLAY_NUCLEUS_PATH,
                "preferences.damageNumberTierThreshold in DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS",
            ),
        ),
        additionalEvidenceAnchors = listOf(
            BoundAnchor(
                PROFILE_CODEC_TEST_PATH,
                listOf(
                    "preferenceConfigurationAcceptsExactMaximaAndRejectsAdjacentOverflowOrNonMembers",
                    "preferenceIngressAcceptsMembersAndRejectsAdjacentInRangeNonMembers",
                ),
            ),
        ),
    ),
    BoundProjection(
        "profile.gameplay-discoveries", "0..itemCount (<=400)",
        "ball/profile/nucleus/src/commonMain/kotlin/kinetickk/ball/profile/nucleus/ProfileNucleus.kt",
        "update.discoveredItemIds.size > state.policy.itemCount",
        "ball/profile/nucleus/src/commonTest/kotlin/kinetickk/ball/profile/nucleus/ProfileNucleusTest.kt",
        "gameplayDiscoveryIngressAcceptsItemCountAndRejectsFirstNPlusOne",
    ),
    BoundProjection(
        "profile.v4-utf8-bytes", "65536",
        "ball/profile/resource/src/commonMain/kotlin/kinetickk/ball/profile/resource/ProfileCodec.kt",
        "MAX_PROFILE_PAYLOAD_BYTES: Int = 65_536",
        "ball/profile/resource/src/commonTest/kotlin/kinetickk/ball/profile/resource/ProfileCodecTest.kt",
        "byteLimitAndUtf8AreCheckedBeforeJsonDecode",
        "encodedByteLimitAcceptsExactlyNAndRejectsFirstNPlusOne",
    ),
).also { bounds -> requireUniqueKeys("expectedBounds", bounds, BoundProjection::id) }

internal val authorityModules = uniqueLinkedMap("authorityModules", listOf(
    "AppAssembly" to listOf(":app:shared", ":app:desktop", ":app:web"),
    "AppSession" to listOf(
        ":flow:session:api",
        ":flow:session:nucleus",
        ":flow:session:interaction",
        ":flow:session:impl",
    ),
    "GameplayRun" to listOf(
        ":ball:gameplay:api",
        ":ball:gameplay:nucleus",
        ":ball:gameplay:interaction",
        ":ball:gameplay:impl",
    ),
    "Profile" to listOf(
        ":ball:profile:api",
        ":ball:profile:nucleus",
        ":ball:profile:resource",
        ":ball:profile:interaction",
        ":ball:profile:impl",
    ),
    "ContentCatalog" to listOf(":ball:content:api", ":ball:content:impl"),
    "AudioResource" to listOf(":resource:audio:api", ":resource:audio:impl"),
    "Foundation" to listOf(":foundation:common", ":foundation:design"),
))

internal val authorityWriters = uniqueLinkedMap("authorityWriters", listOf(
    "Profile" to "ball/profile/impl/src/commonMain/kotlin/kinetickk/ball/profile/impl/DefaultProfileComponent.kt",
    "GameplayRun" to "ball/gameplay/impl/src/commonMain/kotlin/kinetickk/ball/gameplay/impl/GameComponent.kt",
    "AppSession" to "flow/session/impl/src/commonMain/kotlin/kinetickk/flow/session/impl/DefaultAppSessionComponent.kt",
    "ContentCatalog" to "immutable-bootstrap-only",
))

internal val applicationSurfaces = uniqueLinkedMap("applicationSurfaces", listOf(
    "ContentCatalog" to ":ball:content:api",
    "Profile" to ":ball:profile:api",
    "GameplayRun" to ":ball:gameplay:api",
    "AppSession" to ":flow:session:api",
))

internal val internalProjectPackages = uniqueLinkedMap("internalProjectPackages", listOf(
    ":ball:content:impl" to "kinetickk.ball.content.impl",
    ":ball:gameplay:nucleus" to "kinetickk.ball.gameplay.nucleus",
    ":ball:gameplay:interaction" to "kinetickk.ball.gameplay.interaction",
    ":ball:gameplay:impl" to "kinetickk.ball.gameplay.impl",
    ":ball:profile:nucleus" to "kinetickk.ball.profile.nucleus",
    ":ball:profile:resource" to "kinetickk.ball.profile.resource",
    ":ball:profile:interaction" to "kinetickk.ball.profile.interaction",
    ":ball:profile:impl" to "kinetickk.ball.profile.impl",
    ":flow:session:nucleus" to "kinetickk.flow.session.nucleus",
    ":flow:session:interaction" to "kinetickk.flow.session.interaction",
    ":flow:session:impl" to "kinetickk.flow.session.impl",
    ":resource:audio:impl" to "kinetickk.resource.audio.impl",
))

internal val allowedForeignInternalProjectEdges = setOf(
    ProjectEdge(":app:shared", "commonMainImplementation", ":ball:content:impl"),
    ProjectEdge(":app:shared", "commonMainImplementation", ":ball:gameplay:impl"),
    ProjectEdge(":app:shared", "commonMainImplementation", ":ball:gameplay:interaction"),
    ProjectEdge(":app:shared", "commonMainImplementation", ":ball:profile:impl"),
    ProjectEdge(":app:shared", "commonMainImplementation", ":ball:profile:interaction"),
    ProjectEdge(":app:shared", "commonMainImplementation", ":flow:session:impl"),
    ProjectEdge(":app:shared", "commonMainImplementation", ":flow:session:interaction"),
    ProjectEdge(":app:shared", "commonMainImplementation", ":resource:audio:impl"),
    ProjectEdge(":flow:session:impl", "commonMainApi", ":ball:gameplay:interaction"),
    ProjectEdge(":flow:session:interaction", "commonMainApi", ":ball:gameplay:interaction"),
    ProjectEdge(":flow:session:interaction", "commonMainApi", ":ball:profile:interaction"),
    ProjectEdge(":ball:gameplay:nucleus", "commonTestImplementation", ":ball:content:impl"),
)

internal fun authorityFor(projectPath: String): String = when {
    projectPath.startsWith(":app:") -> "AppAssembly"
    projectPath.startsWith(":flow:session:") -> "AppSession"
    projectPath.startsWith(":ball:gameplay:") -> "GameplayRun"
    projectPath.startsWith(":ball:profile:") -> "Profile"
    projectPath.startsWith(":ball:content:") -> "ContentCatalog"
    projectPath.startsWith(":resource:audio:") -> "AudioResource"
    projectPath.startsWith(":foundation:") -> "Foundation"
    else -> error("Unknown project authority: $projectPath")
}

internal fun semanticDirectControlEdges(): SortedSet<String> =
    expectedSemanticDirectControlEdges.toSortedSet()

internal fun findCycle(edges: Collection<Pair<String, String>>): List<String>? {
    val adjacency = edges.groupBy({ it.first }, { it.second }).mapValues { it.value.toSortedSet() }
    val visiting = linkedSetOf<String>()
    val visited = mutableSetOf<String>()

    fun visit(node: String): List<String>? {
        if (node in visiting) {
            val path = visiting.toList()
            return path.dropWhile { it != node } + node
        }
        if (!visited.add(node)) return null
        visiting += node
        adjacency[node].orEmpty().forEach { next ->
            visit(next)?.let { return it }
        }
        visiting -= node
        return null
    }

    return edges.flatMap { listOf(it.first, it.second) }.toSortedSet().firstNotNullOfOrNull(::visit)
}

internal fun parseTableIds(markdown: String, heading: String): SortedSet<String> {
    return parseArchitectureTableRows(markdown, heading)
        .asSequence()
        .map(ArchitectureTableRow::id)
        .toSortedSet()
}

internal fun resolvedManifestJson(
    leafProjects: Collection<String>,
    edges: Collection<ProjectEdge>,
    readRoutes: Collection<String>,
    commandRoutes: Collection<String>,
): String {
    val sortedEdges = edges.sortedWith(compareBy(ProjectEdge::source, ProjectEdge::configuration, ProjectEdge::target))
    val directEdges = semanticDirectControlEdges()
    return buildString {
        appendLine("{")
        appendLine("  \"schema\": \"kinetickk-pokeball-resolved/v1\",")
        appendLine("  \"authority\": \"non-authoritative generated projection; typed Kotlin source remains authoritative\",")
        appendLine("  \"pokeball\": {")
        appendLine("    \"agentPackRevision\": ${PokeballBaseline.AGENT_PACK_REVISION},")
        appendLine("    \"agentPackSha256\": \"${PokeballBaseline.AGENT_PACK_SHA256}\",")
        appendLine("    \"coreCommit\": \"${PokeballBaseline.CORE_COMMIT}\",")
        appendLine("    \"coreSha256\": \"${PokeballBaseline.CORE_SHA256}\",")
        appendLine("    \"coreVersion\": \"${PokeballBaseline.CORE_VERSION}\"")
        appendLine("  },")
        appendJsonStringArray("modules", leafProjects.sorted(), trailingComma = true)
        appendLine("  \"authorities\": [")
        authorityModules.entries.forEachIndexed { index, (authority, modules) ->
            appendLine("    {")
            appendLine("      \"id\": \"${jsonEscape(authority)}\",")
            append("      \"modules\": ")
            appendInlineJsonStringArray(modules.sorted())
            appendLine(",")
            appendLine("      \"writer\": \"${jsonEscape(authorityWriters[authority] ?: "none-mechanical-only")}\"")
            append("    }")
            appendLine(if (index == authorityModules.size - 1) "" else ",")
        }
        appendLine("  ],")
        appendLine("  \"applicationSurfaces\": [")
        applicationSurfaces.entries.forEachIndexed { index, (authority, module) ->
            append("    {\"authority\": \"${jsonEscape(authority)}\", \"module\": \"${jsonEscape(module)}\"}")
            appendLine(if (index == applicationSurfaces.size - 1) "" else ",")
        }
        appendLine("  ],")
        appendLine("  \"compileEdges\": [")
        sortedEdges.forEachIndexed { index, edge ->
            append(
                "    {\"configuration\": \"${jsonEscape(edge.configuration)}\", " +
                    "\"source\": \"${jsonEscape(edge.source)}\", " +
                    "\"target\": \"${jsonEscape(edge.target)}\"}",
            )
            appendLine(if (index == sortedEdges.lastIndex) "" else ",")
        }
        appendLine("  ],")
        appendJsonStringArray("directControlEdges", directEdges, trailingComma = true)
        appendJsonStringArray("readRoutes", readRoutes.sorted(), trailingComma = true)
        appendJsonStringArray("commandRoutes", commandRoutes.sorted(), trailingComma = true)
        appendJsonStringArray(
            "flowParticipations",
            listOf("AppSession -> GameplayRun", "AppSession -> Profile"),
            trailingComma = true,
        )
        appendLine("  \"bounds\": [")
        expectedBounds.sortedBy(BoundProjection::id).forEachIndexed { index, bound ->
            append("    {\"evidence\": ")
            appendInlineJsonStringArray(bound.evidenceAnchors.map(BoundAnchor::path).distinct().sorted())
            append(", \"id\": \"${jsonEscape(bound.id)}\", \"sources\": ")
            appendInlineJsonStringArray(bound.sourceAnchors.map(BoundAnchor::path).distinct().sorted())
            append(", \"value\": \"${jsonEscape(bound.value)}\"}")
            appendLine(if (index == expectedBounds.lastIndex) "" else ",")
        }
        appendLine("  ],")
        appendJsonStringArray("routeInventory", expectedRouteInventory, trailingComma = false)
        appendLine("}")
    }
}

private fun StringBuilder.appendJsonStringArray(
    name: String,
    values: Collection<String>,
    trailingComma: Boolean,
) {
    appendLine("  \"${jsonEscape(name)}\": [")
    values.forEachIndexed { index, value ->
        append("    \"${jsonEscape(value)}\"")
        appendLine(if (index == values.size - 1) "" else ",")
    }
    append("  ]")
    appendLine(if (trailingComma) "," else "")
}

private fun StringBuilder.appendInlineJsonStringArray(values: Collection<String>) {
    append(values.joinToString(prefix = "[", postfix = "]") { "\"${jsonEscape(it)}\"" })
}

internal fun jsonEscape(value: String): String = buildString {
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character.code < 0x20) {
                append("\\u${character.code.toString(16).padStart(4, '0')}")
            } else {
                append(character)
            }
        }
    }
}

internal fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it) }

internal fun digestPathAndBytes(entries: List<Pair<String, ByteArray>>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    entries.sortedBy { it.first }.forEach { (relativePath, bytes) ->
        digest.update(relativePath.toByteArray(StandardCharsets.UTF_8))
        digest.update(0.toByte())
        digest.update(bytes)
        digest.update(0.toByte())
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal fun readUtf8(path: Path): String = Files.readString(path, StandardCharsets.UTF_8)

internal fun String.countOccurrences(needle: String): Int {
    if (needle.isEmpty()) return 0
    var count = 0
    var index = 0
    while (true) {
        index = indexOf(needle, index)
        if (index < 0) return count
        count++
        index += needle.length
    }
}
