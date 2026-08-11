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

internal fun SourceDocument.isProductionKotlinSource(): Boolean {
    if (!relativePath.endsWith(".kt")) return false
    val pathSegments = relativePath.split('/')
    if (pathSegments.firstOrNull() !in setOf("app", "ball", "flow", "foundation", "resource")) {
        return false
    }
    val sourceRootIndex = pathSegments.indexOf("src")
    if (sourceRootIndex <= 0) return false
    if (pathSegments.take(sourceRootIndex).any { it == "build" || it == ".gradle" || it == "generated" }) {
        return false
    }
    val sourceSet = pathSegments.getOrNull(sourceRootIndex + 1) ?: return false
    return sourceSet == "main" || sourceSet.endsWith("Main")
}

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

/**
 * A finite collection whose cardinality is inherited mechanically from an already validated
 * source, a closed schema, or an invariant-preserving copy rather than admitted by a new
 * independently executable growth boundary.
 */
internal data class MechanicallyDerivedBoundProjection(
    val id: String,
    val value: String,
    val derivation: String,
    val sourceAnchors: List<BoundAnchor>,
    val evidenceAnchors: List<BoundAnchor> = emptyList(),
    val closedEnumInventories: List<ClosedEnumInventory> = emptyList(),
    val policyRow: String,
)

internal data class ClosedEnumInventory(
    val path: String,
    val declaration: String,
    val expectedEntries: List<String>,
)

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

internal data class ClosedForeignOperationUseException(
    val sourceAuthority: String,
    val targetAuthority: String,
    val operationToken: String,
    val requiredGuardTokensByPath: Map<String, List<String>>,
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
internal const val PROFILE_IDENTITY_PATH =
    "ball/profile/api/src/commonMain/kotlin/kinetickk/ball/profile/api/ProfileIdentity.kt"
private const val PROFILE_RESOURCE_PROTOCOL_PATH =
    "ball/profile/api/src/commonMain/kotlin/kinetickk/ball/profile/api/ProfileResourceProtocol.kt"
internal const val GAMEPLAY_PROTOCOL_PATH =
    "ball/gameplay/api/src/commonMain/kotlin/kinetickk/ball/gameplay/api/GameplayProtocol.kt"
internal const val GAMEPLAY_QUERY_PATH =
    "ball/gameplay/api/src/commonMain/kotlin/kinetickk/ball/gameplay/api/GameplayQueries.kt"
internal const val GAMEPLAY_IDENTITY_PATH =
    "ball/gameplay/api/src/commonMain/kotlin/kinetickk/ball/gameplay/api/GameplayIdentity.kt"
internal const val CONTENT_SURFACE_PATH =
    "ball/content/api/src/commonMain/kotlin/kinetickk/ball/content/api/ContentSnapshots.kt"
private const val APP_COMPOSITION_PATH =
    "app/shared/src/commonMain/kotlin/kinetickk/app/shared/AppComposition.kt"
private const val SESSION_IMPL_PATH =
    "flow/session/impl/src/commonMain/kotlin/kinetickk/flow/session/impl/DefaultAppSessionComponent.kt"
private const val SESSION_NUCLEUS_PATH =
    "flow/session/nucleus/src/commonMain/kotlin/kinetickk/flow/session/nucleus/AppSessionNucleus.kt"
internal const val SESSION_DECISION_PATH =
    "flow/session/nucleus/src/commonMain/kotlin/kinetickk/flow/session/nucleus/AppSessionDecision.kt"
private const val PROFILE_NUCLEUS_PATH =
    "ball/profile/nucleus/src/commonMain/kotlin/kinetickk/ball/profile/nucleus/ProfileNucleus.kt"
internal const val PROFILE_DECISION_PATH =
    "ball/profile/nucleus/src/commonMain/kotlin/kinetickk/ball/profile/nucleus/ProfileDecision.kt"
private const val PROFILE_STATE_PATH =
    "ball/profile/nucleus/src/commonMain/kotlin/kinetickk/ball/profile/nucleus/ProfileState.kt"
private const val GAMEPLAY_NUCLEUS_PATH =
    "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/GameplayNucleus.kt"
internal const val GAMEPLAY_DECISION_PATH =
    "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/GameplayDecision.kt"
private const val SESSION_CONTENT_PATH =
    "flow/session/interaction/src/commonMain/kotlin/kinetickk/flow/session/interaction/AppSessionContent.kt"
private const val SESSION_HOME_PATH =
    "flow/session/interaction/src/commonMain/kotlin/kinetickk/flow/session/interaction/home/impl/DefaultHomeFeature.kt"
private const val SESSION_CODEX_PATH =
    "flow/session/interaction/src/commonMain/kotlin/kinetickk/flow/session/interaction/codex/impl/DefaultCodexFeature.kt"
private const val SESSION_QUERIES_PATH =
    "flow/session/api/src/commonMain/kotlin/kinetickk/flow/session/api/SessionQueries.kt"
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
private const val GAMEPLAY_FEATURE_PATH =
    "ball/gameplay/impl/src/commonMain/kotlin/kinetickk/ball/gameplay/impl/DefaultGameplayFeature.kt"
private const val GAMEPLAY_STATE_PATH =
    "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/simulation/MutableGameState.kt"
private const val GAMEPLAY_ENTITIES_PATH =
    "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/model/GameEntities.kt"
private const val GAMEPLAY_REDUCER_PATH =
    "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/reducer/GameReducer.kt"
private const val GAMEPLAY_LOOP_PATH =
    "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/simulation/GameLoop.kt"
private const val GAMEPLAY_ENEMY_SYSTEM_PATH =
    "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/simulation/EnemySystem.kt"
private const val GAMEPLAY_COLLISION_SYSTEM_PATH =
    "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/simulation/CollisionSystem.kt"
private const val GAMEPLAY_REWARD_SYSTEM_PATH =
    "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/simulation/RewardSystem.kt"
private const val GAMEPLAY_PROJECTILE_EFFECTS_PATH =
    "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/simulation/ProjectileEffects.kt"
private const val GAMEPLAY_WEAPON_SYSTEM_PATH =
    "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/simulation/WeaponSystem.kt"
private const val GAMEPLAY_PROGRESSION_SYSTEM_PATH =
    "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/simulation/ProgressionSystem.kt"
private const val GAMEPLAY_RELIC_COMBAT_SYSTEM_PATH =
    "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/simulation/RelicCombatSystem.kt"
private const val GAMEPLAY_RENDER_MODEL_MAPPER_PATH =
    "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/simulation/GameplayRenderModelMapper.kt"
private const val GAMEPLAY_VISUAL_FX_PROTOCOL_PATH =
    "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/protocol/VisualFxProtocol.kt"
private const val GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH =
    "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/simulation/GameplayCollectionBoundsTest.kt"
private const val GAMEPLAY_ARCHITECTURE_BOUNDS_TEST_PATH =
    "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/characterization/GameplayArchitectureBoundsTest.kt"
private const val GAMEPLAY_BASELINE_TEST_PATH =
    "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/characterization/" +
        "GameplayBaselineCharacterizationTest.kt"
private const val GAMEPLAY_SYSTEMS_TEST_PATH =
    "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/characterization/" +
        "GameSystemsTest.kt"
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
private const val PROFILE_LAB_STATE_PATH =
    "ball/profile/interaction/src/commonMain/kotlin/kinetickk/ball/profile/interaction/lab/impl/LabState.kt"
private const val PROFILE_LAB_TEST_PATH =
    "ball/profile/interaction/src/commonTest/kotlin/kinetickk/ball/profile/interaction/lab/impl/LabReducerTest.kt"
internal const val SESSION_STATE_PATH =
    "flow/session/nucleus/src/commonMain/kotlin/kinetickk/flow/session/nucleus/AppSessionState.kt"
private const val SESSION_IMPL_TEST_PATH =
    "flow/session/impl/src/commonTest/kotlin/kinetickk/flow/session/impl/DefaultAppSessionComponentTest.kt"
private const val AUDIO_SERVICE_PATH =
    "resource/audio/impl/src/commonMain/kotlin/kinetickk/resource/audio/impl/DefaultAudioService.kt"
private const val AUDIO_SERVICE_TEST_PATH =
    "resource/audio/impl/src/commonTest/kotlin/kinetickk/resource/audio/impl/DefaultAudioServiceTest.kt"
private const val DESKTOP_AUDIO_PATH =
    "app/shared/src/desktopMain/kotlin/kinetickk/app/shared/PlatformCapabilities.desktop.kt"
private const val DESKTOP_AUDIO_TEST_PATH =
    "app/shared/src/desktopTest/kotlin/kinetickk/app/shared/PlatformCapabilitiesDesktopTest.kt"
private const val FOUNDATION_COMPLETION_DEQUE_PATH =
    "foundation/common/src/commonMain/kotlin/kinetickk/foundation/dispatch/BoundedCompletionDeque.kt"
private const val CONTENT_CATALOG_PATH =
    "ball/content/impl/src/commonMain/kotlin/kinetickk/ball/content/impl/DefaultContentCatalog.kt"
private const val CONTENT_CATALOG_TEST_PATH =
    "ball/content/impl/src/commonTest/kotlin/kinetickk/ball/content/impl/ContentCatalogTest.kt"
private const val CONTENT_IDS_PATH =
    "ball/content/api/src/commonMain/kotlin/kinetickk/ball/content/api/ContentIds.kt"
private const val CONTENT_DEFINITIONS_PATH =
    "ball/content/api/src/commonMain/kotlin/kinetickk/ball/content/api/ContentDefinitions.kt"
private const val FOUNDATION_COLLECTIONS_PATH =
    "foundation/common/src/commonMain/kotlin/kinetickk/foundation/collections/ImmutableCollections.kt"
private const val FOUNDATION_COLLECTIONS_TEST_PATH =
    "foundation/common/src/commonTest/kotlin/kinetickk/foundation/collections/ImmutableCollectionsTest.kt"
private const val CUMULATIVE_FANOUT_POLICY_PATH =
    "build-logic/src/main/kotlin/kinetickk/gradle/pokeball/CumulativeFanoutPolicy.kt"
private const val ARCHITECTURE_VERIFIER_TEST_PATH =
    "build-logic/src/test/kotlin/kinetickk/gradle/pokeball/PokeballArchitectureVerifierTest.kt"

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
        PROFILE_PROTOCOL_PATH, "ProfileModuleCommand.SelectCoreShape", "ProfileModuleResult.CoreShapeSelected",
        SESSION_DECISION_PATH, "ProfileModuleResultPulse",
        "ProfileCommandRejectedBeforeAcceptance", SESSION_NUCLEUS_PATH,
    ),
    CommandRouteProjection(
        "session-profile-mute", "AppSession", "Profile",
        PROFILE_PROTOCOL_PATH, "ProfileModuleCommand.ToggleMute", "ProfileModuleResult.PreferencesChanged",
        SESSION_DECISION_PATH, "ProfileModuleResultPulse",
        "ProfileCommandRejectedBeforeAcceptance", SESSION_NUCLEUS_PATH,
    ),
    CommandRouteProjection(
        "session-profile-rebirth", "AppSession", "Profile",
        PROFILE_PROTOCOL_PATH, "ProfileModuleCommand.AdvanceRebirth", "ProfileModuleResult.RebirthAdvanced",
        SESSION_DECISION_PATH, "ProfileModuleResultPulse",
        "ProfileCommandRejectedBeforeAcceptance", SESSION_NUCLEUS_PATH,
    ),
    CommandRouteProjection(
        "session-profile-reset-confirm", "AppSession", "Profile",
        PROFILE_PROTOCOL_PATH, "ProfileModuleCommand.ConfirmLegacyReset", "ProfileModuleResult.ResetCompleted",
        SESSION_DECISION_PATH, "ProfileModuleResultPulse",
        "ProfileCommandRejectedBeforeAcceptance", SESSION_NUCLEUS_PATH,
        listOf(
            "ProfileModuleResult.ResetWriteRejected",
            "ProfileModuleResult.ResetWriteResourceFailure",
            "ProfileModuleResult.ResetWriteOutcomeUnknown",
            "ProfileModuleResult.ResetNeedsAttention",
        ),
    ),
    CommandRouteProjection(
        "session-profile-reset-retry", "AppSession", "Profile",
        PROFILE_PROTOCOL_PATH, "ProfileModuleCommand.RetryLegacyPurge", "ProfileModuleResult.ResetCompleted",
        SESSION_DECISION_PATH, "ProfileModuleResultPulse",
        "ProfileCommandRejectedBeforeAcceptance", SESSION_NUCLEUS_PATH,
        listOf("ProfileModuleResult.ResetNeedsAttention"),
    ),
    CommandRouteProjection(
        "session-gameplay-start", "AppSession", "GameplayRun",
        GAMEPLAY_PROTOCOL_PATH, "GameplayModuleCommand.StartRun", "GameplayModuleResult.RunStarted",
        SESSION_DECISION_PATH, "GameplayModuleResultPulse",
        "GameplayCommandRejectedBeforeAcceptance", SESSION_NUCLEUS_PATH,
    ),
    CommandRouteProjection(
        "session-gameplay-pause", "AppSession", "GameplayRun",
        GAMEPLAY_PROTOCOL_PATH, "GameplayModuleCommand.PauseForOverlay", "GameplayModuleResult.OverlayPaused",
        SESSION_DECISION_PATH, "GameplayModuleResultPulse",
        "GameplayCommandRejectedBeforeAcceptance", SESSION_NUCLEUS_PATH,
    ),
    CommandRouteProjection(
        "session-gameplay-preferences", "AppSession", "GameplayRun",
        GAMEPLAY_PROTOCOL_PATH, "GameplayModuleCommand.ApplyPreferences", "GameplayModuleResult.PreferencesApplied",
        SESSION_DECISION_PATH, "GameplayModuleResultPulse",
        "GameplayCommandRejectedBeforeAcceptance", SESSION_NUCLEUS_PATH,
    ),
    CommandRouteProjection(
        "session-gameplay-exit", "AppSession", "GameplayRun",
        GAMEPLAY_PROTOCOL_PATH, "GameplayModuleCommand.ExitRun", "GameplayModuleResult.RunExited",
        SESSION_DECISION_PATH, "GameplayModuleResultPulse",
        "GameplayCommandRejectedBeforeAcceptance", SESSION_NUCLEUS_PATH,
    ),
    CommandRouteProjection(
        "gameplay-profile-progress", "GameplayRun", "Profile",
        PROFILE_PROTOCOL_PATH, "ProfileModuleCommand.ApplyGameplayProgress", "ProfileModuleResult.GameplayProgressApplied",
        GAMEPLAY_DECISION_PATH, "GameplayNucleusPulse.ProfileModuleResultPulse",
        "GameplayNucleusPulse.ProfileCommandRejectedBeforeAcceptance", GAMEPLAY_NUCLEUS_PATH,
    ),
).sortedBy(CommandRouteProjection::id).also { routes ->
    requireUniqueKeys("commandRouteProjections", routes, CommandRouteProjection::id)
}

internal val commandOutcomeFamilies = listOf(
    CommandOutcomeFamily(
        targetAuthority = "Profile",
        ownerPath = PROFILE_PROTOCOL_PATH,
        declaration = "sealed interface ProfileModuleResult",
        supertype = "ProfileModuleResult",
    ),
    CommandOutcomeFamily(
        targetAuthority = "GameplayRun",
        ownerPath = GAMEPLAY_PROTOCOL_PATH,
        declaration = "sealed interface GameplayModuleResult",
        supertype = "GameplayModuleResult",
    ),
).also { families ->
    requireUniqueKeys("commandOutcomeFamilies", families, CommandOutcomeFamily::targetAuthority)
    requireUniqueKeys("commandOutcomeFamilies", families, CommandOutcomeFamily::supertype)
}

internal val canonicalProtocolDataClassShapes = listOf(
    CanonicalDataClassShape(
        PROFILE_DECISION_PATH,
        "ProfileAcceptedFrame",
        listOf(
            CanonicalFieldShape("nextState", "ProfileState"),
            CanonicalFieldShape("outputs", "ImmutableList<ProfileOutput>"),
        ),
        forbidFieldDefaults = true,
        forbidFieldModifiers = true,
        requireExplicitPublicVisibility = true,
        requireDirectPrimaryConstructorSyntax = true,
        forbidClassHeaderSuffix = true,
        forbidBodyProperties = true,
        reserveExtraComponentSpellings = true,
        requireDirectPrivateForExtensionProperties = true,
        forbidTypeAliases = true,
    ),
    CanonicalDataClassShape(
        GAMEPLAY_DECISION_PATH,
        "GameplayAcceptedFrame",
        listOf(
            CanonicalFieldShape("nextState", "GameplayState"),
            CanonicalFieldShape("outputs", "ImmutableList<GameplayOutput>"),
        ),
        forbidFieldDefaults = true,
        forbidFieldModifiers = true,
        requireExplicitPublicVisibility = true,
        requireDirectPrimaryConstructorSyntax = true,
        forbidClassHeaderSuffix = true,
        forbidBodyProperties = true,
        reserveExtraComponentSpellings = true,
        requireDirectPrivateForExtensionProperties = true,
        forbidTypeAliases = true,
    ),
    CanonicalDataClassShape(
        SESSION_DECISION_PATH,
        "AppSessionAcceptedFrame",
        listOf(
            CanonicalFieldShape("nextState", "AppSessionState"),
            CanonicalFieldShape("outputs", "ImmutableList<AppSessionOutput>"),
        ),
        forbidFieldDefaults = true,
        forbidFieldModifiers = true,
        requireExplicitPublicVisibility = true,
        requireDirectPrimaryConstructorSyntax = true,
        forbidClassHeaderSuffix = true,
        forbidBodyProperties = true,
        reserveExtraComponentSpellings = true,
        requireDirectPrivateForExtensionProperties = true,
        forbidTypeAliases = true,
    ),
    CanonicalDataClassShape(
        PROFILE_IDENTITY_PATH,
        "ProfileSemanticHandle",
        listOf(
            CanonicalFieldShape("sourceInstance", "ProfileCommandSource"),
            CanonicalFieldShape("sourceRevision", "Long"),
            CanonicalFieldShape("sourceOrdinal", "Int"),
        ),
    ),
    CanonicalDataClassShape(
        PROFILE_IDENTITY_PATH,
        "ProfileCommandSourceToken",
        listOf(
            CanonicalFieldShape("semanticHandle", "ProfileSemanticHandle"),
            CanonicalFieldShape("targetInstance", "ProfileInstanceId"),
            CanonicalFieldShape("causalScope", "Long"),
            CanonicalFieldShape("causalDepth", "Int"),
        ),
    ),
    CanonicalDataClassShape(
        PROFILE_IDENTITY_PATH,
        "ProfileResultSourceToken",
        listOf(
            CanonicalFieldShape("semanticHandle", "ProfileSemanticHandle"),
            CanonicalFieldShape("targetInstance", "ProfileInstanceId"),
            CanonicalFieldShape("targetRevision", "ProfileRevision"),
            CanonicalFieldShape("sourceOrdinal", "Int"),
            CanonicalFieldShape("causalScope", "Long"),
            CanonicalFieldShape("causalDepth", "Int"),
        ),
    ),
    CanonicalDataClassShape(
        PROFILE_IDENTITY_PATH,
        "ProfileTargetBoundaryProvenance",
        listOf(
            CanonicalFieldShape("targetInstance", "ProfileInstanceId"),
            CanonicalFieldShape("effectiveProtocolIdentity", "ProfileEffectiveProtocolIdentity"),
        ),
    ),
    CanonicalDataClassShape(
        PROFILE_PROTOCOL_PATH,
        "ProfileModuleCommandRequest",
        listOf(
            CanonicalFieldShape("semanticHandle", "ProfileSemanticHandle"),
            CanonicalFieldShape("sourceOrdinal", "Int"),
            CanonicalFieldShape("targetInstance", "ProfileInstanceId"),
            CanonicalFieldShape("command", "ProfileModuleCommand"),
        ),
    ),
    CanonicalDataClassShape(
        PROFILE_PROTOCOL_PATH,
        "ProfileModuleCommandPulse",
        listOf(
            CanonicalFieldShape("commandSource", "ProfileCommandSourceToken"),
            CanonicalFieldShape("effectiveProtocolIdentity", "ProfileEffectiveProtocolIdentity"),
            CanonicalFieldShape("command", "ProfileModuleCommand"),
            CanonicalFieldShape("issuerProvenance", "ProfileCommandIssuerProvenance"),
        ),
    ),
    CanonicalDataClassShape(
        PROFILE_PROTOCOL_PATH,
        "ResetWriteResourceFailure",
        listOf(CanonicalFieldShape("reason", "ProfileWriteFailure")),
        withinDeclaration = "sealed interface ProfileModuleResult",
    ),
    CanonicalDataClassShape(
        PROFILE_RESOURCE_PROTOCOL_PATH,
        "ResourceFailure",
        listOf(CanonicalFieldShape("reason", "ProfileWriteFailure")),
        withinDeclaration = "sealed interface ProfileV4WriteResult",
    ),
    CanonicalDataClassShape(
        PROFILE_PROTOCOL_PATH,
        "ResourceFailure",
        listOf(
            CanonicalFieldShape("snapshotRevision", "ProfileRevision"),
            CanonicalFieldShape("reason", "ProfileWriteFailure"),
        ),
        withinDeclaration = "sealed interface ProfilePersistenceStatus",
    ),
    CanonicalDataClassShape(
        PROFILE_PROTOCOL_PATH,
        "ProfileModuleResultOutput",
        listOf(
            CanonicalFieldShape("semanticHandle", "ProfileSemanticHandle"),
            CanonicalFieldShape("sourceOrdinal", "Int"),
            CanonicalFieldShape("commandSource", "ProfileCommandSourceToken"),
            CanonicalFieldShape("result", "ProfileModuleResult"),
        ),
    ),
    CanonicalDataClassShape(
        PROFILE_PROTOCOL_PATH,
        "ProfileModuleResultDelivery",
        listOf(
            CanonicalFieldShape("commandSource", "ProfileCommandSourceToken"),
            CanonicalFieldShape("resultSource", "ProfileResultSourceToken"),
            CanonicalFieldShape("effectiveProtocolIdentity", "ProfileEffectiveProtocolIdentity"),
            CanonicalFieldShape("result", "ProfileModuleResult"),
            CanonicalFieldShape("issuerProvenance", "ProfileResultIssuerProvenance"),
        ),
    ),
    CanonicalDataClassShape(
        PROFILE_PROTOCOL_PATH,
        "ProfileCommandRefusalEvidence",
        listOf(
            CanonicalFieldShape("commandSource", "ProfileCommandSourceToken"),
            CanonicalFieldShape("effectiveProtocolIdentity", "ProfileEffectiveProtocolIdentity"),
            CanonicalFieldShape("boundaryResponse", "ProfileCommandBoundaryResponse"),
            CanonicalFieldShape("targetBoundaryProvenance", "ProfileTargetBoundaryProvenance"),
        ),
    ),
    CanonicalDataClassShape(
        GAMEPLAY_IDENTITY_PATH,
        "GameplaySemanticHandle",
        listOf(
            CanonicalFieldShape("sourceInstance", "GameplayCommandSource"),
            CanonicalFieldShape("sourceRevision", "Long"),
            CanonicalFieldShape("sourceOrdinal", "Int"),
        ),
    ),
    CanonicalDataClassShape(
        GAMEPLAY_IDENTITY_PATH,
        "GameplayCommandSourceToken",
        listOf(
            CanonicalFieldShape("semanticHandle", "GameplaySemanticHandle"),
            CanonicalFieldShape("targetInstance", "GameplayInstanceId"),
            CanonicalFieldShape("causalScope", "Long"),
            CanonicalFieldShape("causalDepth", "Int"),
        ),
    ),
    CanonicalDataClassShape(
        GAMEPLAY_IDENTITY_PATH,
        "GameplayResultSourceToken",
        listOf(
            CanonicalFieldShape("semanticHandle", "GameplaySemanticHandle"),
            CanonicalFieldShape("targetInstance", "GameplayInstanceId"),
            CanonicalFieldShape("targetRevision", "GameplayRevision"),
            CanonicalFieldShape("sourceOrdinal", "Int"),
            CanonicalFieldShape("causalScope", "Long"),
            CanonicalFieldShape("causalDepth", "Int"),
        ),
    ),
    CanonicalDataClassShape(
        GAMEPLAY_IDENTITY_PATH,
        "GameplayTargetBoundaryProvenance",
        listOf(
            CanonicalFieldShape("targetInstance", "GameplayInstanceId"),
            CanonicalFieldShape("effectiveProtocolIdentity", "GameplayEffectiveProtocolIdentity"),
        ),
    ),
    CanonicalDataClassShape(
        GAMEPLAY_PROTOCOL_PATH,
        "GameplayModuleCommandRequest",
        listOf(
            CanonicalFieldShape("semanticHandle", "GameplaySemanticHandle"),
            CanonicalFieldShape("sourceOrdinal", "Int"),
            CanonicalFieldShape("targetInstance", "GameplayInstanceId"),
            CanonicalFieldShape("command", "GameplayModuleCommand"),
        ),
    ),
    CanonicalDataClassShape(
        GAMEPLAY_PROTOCOL_PATH,
        "GameplayModuleCommandPulse",
        listOf(
            CanonicalFieldShape("commandSource", "GameplayCommandSourceToken"),
            CanonicalFieldShape("effectiveProtocolIdentity", "GameplayEffectiveProtocolIdentity"),
            CanonicalFieldShape("command", "GameplayModuleCommand"),
            CanonicalFieldShape("issuerProvenance", "GameplayCommandIssuerProvenance"),
        ),
    ),
    CanonicalDataClassShape(
        GAMEPLAY_PROTOCOL_PATH,
        "GameplayModuleResultOutput",
        listOf(
            CanonicalFieldShape("semanticHandle", "GameplaySemanticHandle"),
            CanonicalFieldShape("sourceOrdinal", "Int"),
            CanonicalFieldShape("commandSource", "GameplayCommandSourceToken"),
            CanonicalFieldShape("result", "GameplayModuleResult"),
        ),
    ),
    CanonicalDataClassShape(
        GAMEPLAY_PROTOCOL_PATH,
        "GameplayModuleResultDelivery",
        listOf(
            CanonicalFieldShape("commandSource", "GameplayCommandSourceToken"),
            CanonicalFieldShape("resultSource", "GameplayResultSourceToken"),
            CanonicalFieldShape("effectiveProtocolIdentity", "GameplayEffectiveProtocolIdentity"),
            CanonicalFieldShape("result", "GameplayModuleResult"),
            CanonicalFieldShape("issuerProvenance", "GameplayResultIssuerProvenance"),
        ),
    ),
    CanonicalDataClassShape(
        GAMEPLAY_PROTOCOL_PATH,
        "GameplayCommandRefusalEvidence",
        listOf(
            CanonicalFieldShape("commandSource", "GameplayCommandSourceToken"),
            CanonicalFieldShape("effectiveProtocolIdentity", "GameplayEffectiveProtocolIdentity"),
            CanonicalFieldShape("boundaryResponse", "GameplayCommandBoundaryResponse"),
            CanonicalFieldShape("targetBoundaryProvenance", "GameplayTargetBoundaryProvenance"),
        ),
    ),
    CanonicalDataClassShape(
        SESSION_DECISION_PATH,
        "ProfileModuleResultPulse",
        listOf(
            CanonicalFieldShape("commandSource", "ProfileCommandSourceToken"),
            CanonicalFieldShape("resultSource", "ProfileResultSourceToken"),
            CanonicalFieldShape("effectiveProtocolIdentity", "ProfileEffectiveProtocolIdentity"),
            CanonicalFieldShape("result", "ProfileModuleResult"),
            CanonicalFieldShape("issuerProvenance", "ProfileResultIssuerProvenance"),
        ),
    ),
    CanonicalDataClassShape(
        SESSION_DECISION_PATH,
        "GameplayModuleResultPulse",
        listOf(
            CanonicalFieldShape("commandSource", "GameplayCommandSourceToken"),
            CanonicalFieldShape("resultSource", "GameplayResultSourceToken"),
            CanonicalFieldShape("effectiveProtocolIdentity", "GameplayEffectiveProtocolIdentity"),
            CanonicalFieldShape("result", "GameplayModuleResult"),
            CanonicalFieldShape("issuerProvenance", "GameplayResultIssuerProvenance"),
        ),
    ),
    CanonicalDataClassShape(
        SESSION_DECISION_PATH,
        "ProfileCommandRejectedBeforeAcceptance",
        listOf(
            CanonicalFieldShape("commandSource", "ProfileCommandSourceToken"),
            CanonicalFieldShape("effectiveProtocolIdentity", "ProfileEffectiveProtocolIdentity"),
            CanonicalFieldShape("boundaryResponse", "ProfileCommandBoundaryResponse"),
            CanonicalFieldShape("targetBoundaryProvenance", "ProfileTargetBoundaryProvenance"),
        ),
    ),
    CanonicalDataClassShape(
        SESSION_DECISION_PATH,
        "GameplayCommandRejectedBeforeAcceptance",
        listOf(
            CanonicalFieldShape("commandSource", "GameplayCommandSourceToken"),
            CanonicalFieldShape("effectiveProtocolIdentity", "GameplayEffectiveProtocolIdentity"),
            CanonicalFieldShape("boundaryResponse", "GameplayCommandBoundaryResponse"),
            CanonicalFieldShape("targetBoundaryProvenance", "GameplayTargetBoundaryProvenance"),
        ),
    ),
    CanonicalDataClassShape(
        GAMEPLAY_DECISION_PATH,
        "ProfileModuleResultPulse",
        listOf(
            CanonicalFieldShape("commandSource", "ProfileCommandSourceToken"),
            CanonicalFieldShape("resultSource", "ProfileResultSourceToken"),
            CanonicalFieldShape("effectiveProtocolIdentity", "ProfileEffectiveProtocolIdentity"),
            CanonicalFieldShape("result", "ProfileModuleResult"),
            CanonicalFieldShape("issuerProvenance", "ProfileResultIssuerProvenance"),
        ),
        withinDeclaration = "sealed interface GameplayNucleusPulse",
    ),
    CanonicalDataClassShape(
        GAMEPLAY_DECISION_PATH,
        "ProfileCommandRejectedBeforeAcceptance",
        listOf(
            CanonicalFieldShape("commandSource", "ProfileCommandSourceToken"),
            CanonicalFieldShape("effectiveProtocolIdentity", "ProfileEffectiveProtocolIdentity"),
            CanonicalFieldShape("boundaryResponse", "ProfileCommandBoundaryResponse"),
            CanonicalFieldShape("targetBoundaryProvenance", "ProfileTargetBoundaryProvenance"),
        ),
        withinDeclaration = "sealed interface GameplayNucleusPulse",
    ),
).also { shapes ->
    requireUniqueKeys(
        "canonicalProtocolDataClassShapes",
        shapes,
    ) { shape -> Triple(shape.path, shape.withinDeclaration, shape.typeName) }
}

internal val canonicalProtocolEnumInventories = listOf(
    CanonicalEnumInventory(
        PROFILE_IDENTITY_PATH,
        "ProfileEffectiveProtocolIdentity",
        listOf(
            "SESSION_CORE_SHAPE",
            "SESSION_MUTE",
            "SESSION_REBIRTH",
            "SESSION_RESET_CONFIRM",
            "SESSION_RESET_RETRY",
            "GAMEPLAY_PROGRESS",
        ),
    ),
    CanonicalEnumInventory(
        PROFILE_IDENTITY_PATH,
        "ProfileCommandIssuerProvenance",
        listOf("LOCAL_SESSION_STATIC_BINDING", "GAMEPLAY_RUN_STATIC_BINDING"),
    ),
    CanonicalEnumInventory(
        PROFILE_IDENTITY_PATH,
        "ProfileResultIssuerProvenance",
        listOf("LOCAL_PROFILE_STATIC_BINDING"),
    ),
    CanonicalEnumInventory(
        PROFILE_RESOURCE_PROTOCOL_PATH,
        "ProfileWriteFailure",
        listOf("PROVIDER_WRITE_FAILED_BEFORE_EXECUTION"),
    ),
    CanonicalEnumInventory(
        GAMEPLAY_IDENTITY_PATH,
        "GameplayEffectiveProtocolIdentity",
        listOf("SESSION_START", "SESSION_PAUSE", "SESSION_PREFERENCES", "SESSION_EXIT"),
    ),
    CanonicalEnumInventory(
        GAMEPLAY_IDENTITY_PATH,
        "GameplayCommandIssuerProvenance",
        listOf("LOCAL_SESSION_STATIC_BINDING"),
    ),
    CanonicalEnumInventory(
        GAMEPLAY_IDENTITY_PATH,
        "GameplayResultIssuerProvenance",
        listOf("GAMEPLAY_RUN_STATIC_BINDING"),
    ),
).also { inventories ->
    requireUniqueKeys("canonicalProtocolEnumInventories", inventories) { inventory ->
        inventory.path to inventory.typeName
    }
}

internal val canonicalClosedProtocolInventories = listOf(
    ClosedDirectSubtypeInventory(
        PROFILE_PROTOCOL_PATH,
        "sealed interface ProfilePulse",
        "Business",
        setOf("AdjustPreference", "PurchaseMetaUpgrade", "PurchaseOrEquipWeapon"),
    ),
    ClosedDirectSubtypeInventory(
        PROFILE_PROTOCOL_PATH,
        "sealed interface ProfileModuleCommand",
        "ProfileModuleCommand",
        setOf(
            "SelectCoreShape",
            "ToggleMute",
            "AdvanceRebirth",
            "ConfirmLegacyReset",
            "RetryLegacyPurge",
            "ApplyGameplayProgress",
        ),
    ),
    ClosedDirectSubtypeInventory(
        PROFILE_PROTOCOL_PATH,
        "sealed interface ProfileModuleResult",
        "ProfileModuleResult",
        setOf(
            "PreferencesChanged",
            "CoreShapeSelected",
            "RebirthAdvanced",
            "GameplayProgressApplied",
            "ResetCompleted",
            "ResetWriteRejected",
            "ResetWriteResourceFailure",
            "ResetWriteOutcomeUnknown",
            "ResetNeedsAttention",
        ),
    ),
    ClosedDirectSubtypeInventory(
        PROFILE_RESOURCE_PROTOCOL_PATH,
        "sealed interface ProfileV4WriteResult",
        "ProfileV4WriteResult",
        setOf("Written", "Rejected", "ResourceFailure", "OutcomeUnknown"),
    ),
    ClosedDirectSubtypeInventory(
        GAMEPLAY_PROTOCOL_PATH,
        "sealed interface GameplayModuleCommand",
        "GameplayModuleCommand",
        setOf("StartRun", "PauseForOverlay", "ApplyPreferences", "ExitRun"),
    ),
    ClosedDirectSubtypeInventory(
        GAMEPLAY_PROTOCOL_PATH,
        "sealed interface GameplayModuleResult",
        "GameplayModuleResult",
        setOf("RunStarted", "OverlayPaused", "PreferencesApplied", "RunExited"),
    ),
).also { inventories ->
    requireUniqueKeys("canonicalClosedProtocolInventories", inventories) { inventory ->
        inventory.path to inventory.declaration
    }
}

internal val foreignApplicationSurfacePolicies = listOf(
    ForeignApplicationSurfacePolicy(
        sourceRoot = "ball/profile/api/",
        ownPackage = "kinetickk.ball.profile.api",
        allowedForeignImports = setOf(
            "kinetickk.ball.content.api.ContentVersion",
            "kinetickk.ball.content.api.CoreShape",
            "kinetickk.ball.content.api.MetaUpgradeId",
            "kinetickk.ball.content.api.WeaponId",
        ),
    ),
    ForeignApplicationSurfacePolicy(
        sourceRoot = "ball/gameplay/api/",
        ownPackage = "kinetickk.ball.gameplay.api",
        allowedForeignImports = setOf("kinetickk.ball.content.api.WeaponId"),
    ),
    ForeignApplicationSurfacePolicy(
        sourceRoot = "flow/session/api/",
        ownPackage = "kinetickk.flow.session.api",
        allowedForeignImports = setOf(
            "kinetickk.ball.content.api.CoreShape",
            "kinetickk.ball.gameplay.api.RunId",
        ),
    ),
)

internal val forbiddenCompatibilityProtocolSymbols = mapOf(
    PROFILE_PROTOCOL_PATH to setOf(
        "typealias ProfileCommand",
        "typealias ProfileCommandResult",
        "sealed interface ProfileCommandOutcome",
    ),
    GAMEPLAY_PROTOCOL_PATH to setOf(
        "typealias GameplayCommand",
        "typealias GameplayCommandResult",
        "sealed interface GameplayCommandOutcome",
        "sealed interface GameplaySessionPulse",
    ),
)

internal val closedForeignOperationUseExceptions = listOf(
    ClosedForeignOperationUseException(
        sourceAuthority = "AppSession",
        targetAuthority = "Profile",
        operationToken = "ProfileModuleCommand.ApplyGameplayProgress",
        requiredGuardTokensByPath = mapOf(
            SESSION_NUCLEUS_PATH to listOf(
                "is ProfileModuleCommand.ApplyGameplayProgress",
                "error(\"Gameplay progress is not a Session mapping\")",
            ),
            SESSION_IMPL_PATH to listOf(
                "is ProfileModuleCommand.ApplyGameplayProgress",
                "error(\"Gameplay progress cannot enter Profile through Session\")",
                "error(\"Gameplay progress is not a Session command mapping\")",
                "is ProfileModuleCommand.ApplyGameplayProgress -> false",
            ),
        ),
    ),
).also { exceptions ->
    requireUniqueKeys("closedForeignOperationUseExceptions", exceptions) { exception ->
        Triple(exception.sourceAuthority, exception.targetAuthority, exception.operationToken)
    }
}

internal val canonicalProtocolEvidenceAnchors = listOf(
    BoundAnchor(
        PROFILE_PROTOCOL_PATH,
        listOf(
            "require(sourceOrdinal == semanticHandle.sourceOrdinal)",
            "require(semanticHandle == commandSource.semanticHandle)",
        ),
    ),
    BoundAnchor(
        GAMEPLAY_PROTOCOL_PATH,
        listOf(
            "require(sourceOrdinal == semanticHandle.sourceOrdinal)",
            "require(semanticHandle == commandSource.semanticHandle)",
        ),
    ),
) + closedForeignOperationUseExceptions.flatMap { exception ->
    exception.requiredGuardTokensByPath.map { (path, tokens) ->
        BoundAnchor(path, listOf(exception.operationToken) + tokens)
    }
}

internal val expectedReadRoutes = sortedSetOf(
    "gameplay-content-bootstrap",
    "gameplay-profile-preferences",
    "gameplay-profile-run-bootstrap",
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
        "gameplay-profile-run-bootstrap", "GameplayRun", "Profile", PROFILE_QUERY_PATH,
        "ProfileQuery.GetRunBootstrap", "RunBootstrapProjection", GAMEPLAY_IMPL_PATH,
    ),
    ReadRouteProjection(
        "gameplay-profile-preferences", "GameplayRun", "Profile", PROFILE_QUERY_PATH,
        "ProfileQuery.GetPreferences", "PreferencesProjection", GAMEPLAY_IMPL_PATH,
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
        "profileAcceptedFrameAcceptsTwoAndRejectsFirstNPlusOneOutput",
        additionalRequiredTokens = listOf(
            "require(outputs.size <= MAX_PROFILE_OUTPUTS_PER_DECISION)",
        ),
    ),
    BoundProjection(
        "gameplay.outputs-per-decision", "3",
        "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/GameplayDecision.kt",
        "MAX_GAMEPLAY_OUTPUTS_PER_DECISION: Int = 3",
        "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/GameplayNucleusTest.kt",
        "acceptedFrameEnforcesOutputBoundOrderAndFinalCompletion",
        additionalRequiredTokens = listOf(
            "require(outputs.size <= MAX_GAMEPLAY_OUTPUTS_PER_DECISION)",
        ),
    ),
    BoundProjection(
        "session.outputs-per-decision", "3",
        "flow/session/nucleus/src/commonMain/kotlin/kinetickk/flow/session/nucleus/AppSessionDecision.kt",
        "MAX_SESSION_OUTPUTS_PER_DECISION: Int = 3",
        "flow/session/nucleus/src/commonTest/kotlin/kinetickk/flow/session/nucleus/AppSessionNucleusTest.kt",
        "acceptedFrameOutputBoundAcceptsThreeAndRejectsFour",
        additionalRequiredTokens = listOf(
            "require(outputs.size <= MAX_SESSION_OUTPUTS_PER_DECISION)",
        ),
    ),
    BoundProjection(
        "profile.resource-effects-per-decision", "1", PROFILE_IMPL_PATH,
        "effectCount in 0..1", PROFILE_IMPL_TEST_PATH,
        "deployedCompletionAndStaticAcceptorBoundsAcceptNAndRefuseNPlusOne",
    ),
    BoundProjection(
        "session.participant-command-fanout", "1",
        "flow/session/nucleus/src/commonMain/kotlin/kinetickk/flow/session/nucleus/AppSessionDecision.kt",
        "outputs.count(AppSessionOutput::isParticipantCommand) <= 1",
        SESSION_IMPL_TEST_PATH,
        "deployedQueueDepthFanoutAndCapacityAcceptNRejectNPlusOne",
    ),
    BoundProjection(
        "session.ensure-gameplay-run-fanout", "1",
        "flow/session/nucleus/src/commonMain/kotlin/kinetickk/flow/session/nucleus/AppSessionDecision.kt",
        "ensures.size <= 1",
        SESSION_IMPL_TEST_PATH,
        "deployedQueueDepthFanoutAndCapacityAcceptNRejectNPlusOne",
    ),
    BoundProjection(
        "gameplay.profile-command-fanout", "1", GAMEPLAY_IMPL_PATH,
        "profileCommandCount in 0..1",
        "ball/gameplay/impl/src/commonTest/kotlin/kinetickk/ball/gameplay/impl/GameComponentTest.kt",
        "completionDequeAndStaticBoundsRefuseNPlusOneWithoutTruncation",
    ),
    BoundProjection(
        "same-stack.causal-depth", "8",
        "flow/session/impl/src/commonMain/kotlin/kinetickk/flow/session/impl/DefaultAppSessionComponent.kt",
        "MAX_SESSION_CAUSAL_DEPTH: Int = 8",
        PROFILE_IMPL_TEST_PATH,
        "deployedCompletionAndStaticAcceptorBoundsAcceptNAndRefuseNPlusOne",
        additionalRequiredTokens = listOf("causalDepth in 0 until MAX_SESSION_CAUSAL_DEPTH"),
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
                "completionDequeAndStaticBoundsRefuseNPlusOneWithoutTruncation",
            ),
            BoundAnchor(
                SESSION_IMPL_TEST_PATH,
                "deployedQueueDepthFanoutAndCapacityAcceptNRejectNPlusOne",
            ),
        ),
    ),
    BoundProjection(
        "same-stack.cumulative-fanout", "9840",
        CUMULATIVE_FANOUT_POLICY_PATH,
        "MAX_CUMULATIVE_FANOUT: Int = 9_840",
        ARCHITECTURE_VERIFIER_TEST_PATH,
        "staticCumulativeFanoutCeilingAcceptsExact9840AndRejects9841",
        additionalRequiredTokens = listOf(
            "MAX_OUTPUTS_PER_ACCEPTED_DECISION: Int = 3",
            "MAX_CONSUMERS_PER_OUTPUT: Int = 1",
            "FIRST_ACCEPTED_CAUSAL_DEPTH: Int = 0",
            "LAST_ACCEPTED_CAUSAL_DEPTH: Int = 7",
            "HAS_ASYNC_HANDOFF: Boolean = false",
        ),
        additionalEvidenceAnchors = listOf(
            BoundAnchor(
                ARCHITECTURE_VERIFIER_TEST_PATH,
                listOf(
                    "cumulativeFanoutResolverCountsClosedBranchRules",
                    "outputExecutorInventoryIsClosedAgainstAllDeclaredVariants",
                ),
            ),
        ),
    ),
    BoundProjection(
        "profile.completion-deque-capacity", "8", PROFILE_IMPL_PATH,
        "PROFILE_COMPLETION_CAPACITY: Int = 8", PROFILE_IMPL_TEST_PATH,
        "deployedCompletionAndStaticAcceptorBoundsAcceptNAndRefuseNPlusOne",
        additionalRequiredTokens = listOf("BoundedCompletionDeque(PROFILE_COMPLETION_CAPACITY)"),
        additionalSourceAnchors = listOf(
            BoundAnchor(
                FOUNDATION_COMPLETION_DEQUE_PATH,
                listOf(
                    "fun tryAddLast(value: T): Boolean",
                    "if (values.size == capacity) return false",
                    "values.addLast(value)",
                ),
            ),
        ),
    ),
    BoundProjection(
        "gameplay.completion-deque-capacity", "8", GAMEPLAY_IMPL_PATH,
        "GAMEPLAY_COMPLETION_CAPACITY: Int = 8",
        "ball/gameplay/impl/src/commonTest/kotlin/kinetickk/ball/gameplay/impl/GameComponentTest.kt",
        "completionDequeAndStaticBoundsRefuseNPlusOneWithoutTruncation",
        additionalRequiredTokens = listOf("BoundedCompletionDeque(GAMEPLAY_COMPLETION_CAPACITY)"),
        additionalSourceAnchors = listOf(
            BoundAnchor(
                FOUNDATION_COMPLETION_DEQUE_PATH,
                listOf(
                    "fun tryAddLast(value: T): Boolean",
                    "if (values.size == capacity) return false",
                    "values.addLast(value)",
                ),
            ),
        ),
    ),
    BoundProjection(
        "session.completion-deque-capacity", "8",
        "flow/session/impl/src/commonMain/kotlin/kinetickk/flow/session/impl/DefaultAppSessionComponent.kt",
        "SESSION_COMPLETION_CAPACITY: Int = 8", SESSION_IMPL_TEST_PATH,
        "deployedQueueDepthFanoutAndCapacityAcceptNRejectNPlusOne",
        additionalRequiredTokens = listOf("BoundedCompletionDeque(SESSION_COMPLETION_CAPACITY)"),
        additionalSourceAnchors = listOf(
            BoundAnchor(
                FOUNDATION_COMPLETION_DEQUE_PATH,
                listOf(
                    "fun tryAddLast(value: T): Boolean",
                    "if (values.size == capacity) return false",
                    "values.addLast(value)",
                ),
            ),
        ),
    ),
    BoundProjection(
        "session.pending-participant-commands", "1", SESSION_STATE_PATH,
        "val pendingWorkflow: PendingWorkflow?",
        "flow/session/nucleus/src/commonTest/kotlin/kinetickk/flow/session/nucleus/AppSessionNucleusTest.kt",
        "pendingAndRunNamespaceGatesRejectWithoutOutputs",
        additionalSourceAnchors = listOf(
            BoundAnchor(SESSION_NUCLEUS_PATH, "if (state.pendingWorkflow != null)"),
        ),
    ),
    BoundProjection(
        "session.participant-authorities", "2", SESSION_STATE_PATH,
        "sealed interface PendingParticipantCommand",
        ARCHITECTURE_VERIFIER_TEST_PATH,
        "participantAuthorityInventoryRejectsThirdSubtypeAndFlowMismatch",
        additionalRequiredTokens = listOf(
            "val request: ProfileModuleCommandRequest",
            "val request: GameplayModuleCommandRequest",
        ),
    ),
    BoundProjection(
        "gameplay.pending-profile-commands", "1",
        "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/GameplayState.kt",
        "val pendingProfileCommand: PendingProfileCommand?",
        "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/GameplayNucleusTest.kt",
        "atMostOneProfileCommandCanBePending",
        additionalSourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_NUCLEUS_PATH,
                listOf(
                    "var pending: PendingProfileCommand? = null",
                    "check(pending == null) { \"A Gameplay Decision may emit at most one Profile command\" }",
                    "pending = PendingProfileCommand(request, exitCompletion)",
                    "if (state.pendingProfileCommand != null)",
                    "if (mapped.pending != null && state.pendingProfileCommand != null)",
                    "pendingProfileCommand = mapped.pending ?: state.pendingProfileCommand",
                    "prepared.copy(pendingProfileCommand = mapped.pending)",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_IMPL_PATH,
                listOf(
                    "requireGameplayProfileOutputFanoutBound(profileOutputs.size)",
                    "check(before.pendingProfileCommand == null)",
                    "val pending = checkNotNull(next.pendingProfileCommand)",
                    "check(profileCommandCount in 0..1)",
                ),
            ),
        ),
    ),
    BoundProjection(
        "gameplay.active-runs", "1",
        GAMEPLAY_FEATURE_PATH,
        "private fun ensureReplacementAllowed(runId: RunId)",
        "ball/gameplay/impl/src/commonTest/kotlin/kinetickk/ball/gameplay/impl/GameComponentTest.kt",
        "featureAcceptsOneActiveRunAndRefusesEveryFirstNPlusOneReplacement",
        additionalRequiredTokens = listOf(
            "private var componentValue by mutableStateOf<GameComponent?>(null)",
            ").also { componentValue = it }",
            "val active = componentValue ?: return",
            "check(!status.profileCommandPending)",
            "error(\"Cannot replace a non-terminal GameplayRun\")",
            "require(runId.value > active.instanceId.runId.value)",
        ),
    ),
    BoundProjection(
        "gameplay.fixed-steps-per-render-frame", "48",
        GAMEPLAY_LOOP_PATH,
        "MAX_FIXED_STEPS_PER_FRAME: Int = 48",
        GAMEPLAY_ARCHITECTURE_BOUNDS_TEST_PATH,
        "fixedStepWorkAcceptsFortyEightAndDefersFortyNinth",
        additionalRequiredTokens = listOf("steps < MAX_FIXED_STEPS_PER_FRAME"),
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
        additionalSourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_ENEMY_SYSTEM_PATH,
                listOf(
                    "if (enemies.size >= content.rebirth.maxActiveEnemies) return false",
                    "enemies += Enemy(",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_REWARD_SYSTEM_PATH,
                listOf(
                    "if (enemies.size >= content.rebirth.maxActiveEnemies) return@repeat",
                    "enemies += Enemy(",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_LOOP_PATH,
                listOf(
                    "if (enemies.size >= content.rebirth.maxActiveEnemies) enemies.removeAt(0)",
                    "spawnEnemy(EnemyType.ARCHITECT)",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_RENDER_MODEL_MAPPER_PATH,
                listOf(
                    "target.enemies.clear()",
                    "target.enemies.addAll(enemies.map { enemy ->",
                    "enemies = enemies.map { value ->",
                    "    }.toImmutableList(),\n    projectiles = projectiles.map { value ->",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_COLLISION_SYSTEM_PATH,
                listOf(
                    "val liveEnemyIds = enemies.asSequence()",
                    ".mapTo(mutableSetOf(), Enemy::id)",
                ),
            ),
        ),
    ),
    BoundProjection(
        "gameplay.projectiles", "650", GAMEPLAY_STATE_PATH, "MAX_PROJECTILES = 650",
        "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/characterization/GameplayBaselineCharacterizationTest.kt",
        "authoritativeCollectionsEnforceTheCurrentNPlusOneCaps",
        additionalSourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_PROJECTILE_EFFECTS_PATH,
                "if (projectiles.size < MutableGameState.MAX_PROJECTILES) projectiles += projectile",
            ),
            BoundAnchor(
                GAMEPLAY_ENEMY_SYSTEM_PATH,
                "while (projectiles.size > MutableGameState.MAX_PROJECTILES) projectiles.removeAt(0)",
            ),
            BoundAnchor(
                GAMEPLAY_RENDER_MODEL_MAPPER_PATH,
                listOf(
                    "target.projectiles.clear()",
                    "target.projectiles.addAll(projectiles.map(Projectile::isolatedCopy))",
                    "projectiles = projectiles.map { value ->",
                    "    }.toImmutableList(),\n    pickups = pickups.map { value ->",
                ),
            ),
        ),
    ),
    BoundProjection(
        "gameplay.pickups", "420", GAMEPLAY_STATE_PATH, "MAX_PICKUPS = 420",
        "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/characterization/GameplayBaselineCharacterizationTest.kt",
        "authoritativeCollectionsEnforceTheCurrentNPlusOneCaps",
        additionalSourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_PROJECTILE_EFFECTS_PATH,
                "if (pickups.size < MutableGameState.MAX_PICKUPS) pickups += pickup",
            ),
            BoundAnchor(
                GAMEPLAY_ENEMY_SYSTEM_PATH,
                "while (pickups.size > MutableGameState.MAX_PICKUPS) pickups.removeAt(0)",
            ),
            BoundAnchor(
                GAMEPLAY_RENDER_MODEL_MAPPER_PATH,
                listOf(
                    "target.pickups.clear()",
                    "target.pickups.addAll(pickups.map(Pickup::copy))",
                    "pickups = pickups.map { value ->",
                    "    }.toImmutableList(),\n    trail = trail.map { value ->",
                ),
            ),
        ),
    ),
    BoundProjection(
        "gameplay.trail-points", "110", GAMEPLAY_STATE_PATH, "MAX_TRAIL_POINTS = 110",
        "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/characterization/GameplayBaselineCharacterizationTest.kt",
        "authoritativeCollectionsEnforceTheCurrentNPlusOneCaps",
        additionalSourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_WEAPON_SYSTEM_PATH,
                listOf(
                    "trail += TrailPoint(",
                    "while (trail.size > MutableGameState.MAX_TRAIL_POINTS) trail.removeAt(0)",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_RENDER_MODEL_MAPPER_PATH,
                listOf(
                    "target.trail.clear()",
                    "target.trail.addAll(trail.map(TrailPoint::copy))",
                    "trail = trail.map { value -> " +
                        "TrailPointProjection(value.x, value.y, value.age) }.toImmutableList()",
                ),
            ),
        ),
    ),
    BoundProjection(
        "gameplay.delayed-relic-hits", "256", GAMEPLAY_STATE_PATH, "MAX_DELAYED_RELIC_HITS = 256",
        "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/characterization/GameplayArchitectureBoundsTest.kt",
        "delayedRelicHitBoundAcceptsNAndRejectsNPlusOneCandidate",
        additionalSourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_RELIC_COMBAT_SYSTEM_PATH,
                listOf(
                    "delayedRelicHits.size < MutableGameState.MAX_DELAYED_RELIC_HITS",
                    "delayedRelicHits += DelayedRelicHit(",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_RENDER_MODEL_MAPPER_PATH,
                listOf(
                    "target.delayedRelicHits.clear()",
                    "target.delayedRelicHits.addAll(delayedRelicHits.map(DelayedRelicHit::copy))",
                ),
            ),
        ),
    ),
    BoundProjection(
        "gameplay.relic-chain-work", "5 iterations / 6 visited IDs",
        GAMEPLAY_RELIC_COMBAT_SYSTEM_PATH,
        "require(count in 0..content.relicPolicy.maxRank)",
        GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH,
        "relicChainWorkAcceptsFiveAndRejectsSixthIteration",
        additionalRequiredTokens = listOf(
            "val used = mutableSetOf(origin.id)",
            "repeat(count)",
            "used += target.id",
        ),
        additionalSourceAnchors = listOf(
            BoundAnchor(CONTENT_CATALOG_PATH, "RelicPolicy(maxSlots = 4, maxRank = 5)"),
        ),
    ),
    BoundProjection(
        "gameplay.projectile-hit-history", "120",
        GAMEPLAY_ENTITIES_PATH,
        "MAX_HIT_ENEMY_IDS = 120", GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH,
        "projectileHitHistoryAcceptsOneHundredTwentyRejectsNextThenReclaimsDeadEntry",
        additionalRequiredTokens = listOf(
            "require(recordedHitEnemyIds.size <= MAX_HIT_ENEMY_IDS)",
            "if (recordedHitEnemyIds.size >= MAX_HIT_ENEMY_IDS) return false",
            "recordedHitEnemyIds += enemyId",
            "recordedHitEnemyIds = recordedHitEnemyIds.toMutableSet()",
        ),
    ),
    BoundProjection(
        "gameplay.sound-cues", "32", GAMEPLAY_STATE_PATH, "MAX_GAMEPLAY_SOUND_CUES = 32",
        GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH, "soundCuesAcceptThirtyTwoAndRejectThirtyThird",
        additionalSourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_PROGRESSION_SYSTEM_PATH,
                listOf(
                    "if (soundCues.size < MutableGameState.MAX_GAMEPLAY_SOUND_CUES) soundCues += cue",
                    "val result = soundCues.toList()",
                    "soundCues.clear()",
                    "return result",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_RENDER_MODEL_MAPPER_PATH,
                listOf(
                    "target.soundCues.clear()",
                    "target.soundCues.addAll(soundCues)",
                ),
            ),
        ),
    ),
    BoundProjection(
        "gameplay.weapon-nodes", "8", GAMEPLAY_STATE_PATH, "MAX_WEAPON_NODES = 8",
        GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH, "weaponNodesAcceptEightAndRejectNinth",
        additionalSourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_WEAPON_SYSTEM_PATH,
                listOf(
                    "if (weaponNodes.size >= MutableGameState.MAX_WEAPON_NODES) return false",
                    "weaponNodes += node",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_RENDER_MODEL_MAPPER_PATH,
                listOf(
                    "target.weaponNodes.clear()",
                    "target.weaponNodes.addAll(weaponNodes.map(WeaponNode::copy))",
                    "weaponNodes = weaponNodes.map { value ->",
                    "    }.toImmutableList(),\n    weaponOrbitals = weaponOrbitals.map { value ->",
                ),
            ),
        ),
    ),
    BoundProjection(
        "gameplay.weapon-orbitals", "8", GAMEPLAY_STATE_PATH, "MAX_WEAPON_ORBITALS = 8",
        GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH, "weaponOrbitalsAcceptEightAndRejectNinthRequested",
        additionalSourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_WEAPON_SYSTEM_PATH,
                listOf(
                    "require(count in 0..MutableGameState.MAX_WEAPON_ORBITALS)",
                    "weaponOrbitals.clear()",
                    "repeat(count) { weaponOrbitals += WeaponOrbital(",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_RENDER_MODEL_MAPPER_PATH,
                listOf(
                    "target.weaponOrbitals.clear()",
                    "target.weaponOrbitals.addAll(weaponOrbitals.map(WeaponOrbital::copy))",
                    "weaponOrbitals = weaponOrbitals.map { value ->",
                    "    }.toImmutableList(),\n    choices = choices.toImmutableList()",
                ),
            ),
        ),
    ),
    BoundProjection(
        "gameplay.choice-options", "4", GAMEPLAY_STATE_PATH, "MAX_CHOICES = 4",
        GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH, "choiceInventoryAcceptsFourAndRejectsFifthAtomically",
        additionalRequiredTokens = listOf(
            "require(value.size <= MAX_CHOICES)",
            "field = value",
        ),
        additionalSourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_RENDER_MODEL_MAPPER_PATH,
                listOf(
                    "target.choices = choices.toList()",
                    "choices = choices.toImmutableList()",
                ),
            ),
        ),
    ),
    BoundProjection(
        "gameplay.generated-reward-choices", "3", GAMEPLAY_PROGRESSION_SYSTEM_PATH,
        "MAX_GENERATED_REWARD_CHOICES: Int = 3", GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH,
        "rewardChoiceGeneratorsAcceptThreeCandidatesAndDeferFirstNPlusOne",
        additionalRequiredTokens = listOf(
            "repeat(MAX_GENERATED_REWARD_CHOICES) itemChoiceLoop@{",
            "repeat(MAX_GENERATED_REWARD_CHOICES) relicChoiceLoop@{",
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
        additionalSourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_WEAPON_SYSTEM_PATH,
                listOf(
                    "samples < MutableGameState.MAX_TRAIL_SAMPLES_PER_UPDATE",
                    "trail += TrailPoint(",
                ),
            ),
        ),
    ),
    BoundProjection(
        "gameplay.visual-fx-cues", "2048",
        GAMEPLAY_VISUAL_FX_PROTOCOL_PATH,
        "MAX_CUES_PER_PROJECTION = 2_048",
        "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/protocol/BoundedVisualFxCueAccumulatorTest.kt",
        "outputCapTwoThousandFortyEightReportsAttemptedTwoThousandFortyNinthWithoutGrowth",
        additionalRequiredTokens = listOf(
            "MAX_RETAINED_CUES = VisualFxCueLimits.MAX_CUES_PER_PROJECTION - 1",
            "if (cues.size < MAX_RETAINED_CUES)",
            "cues.removeAt(replaceIndex)",
            "add(VisualFxCue.VisualCuesDropped(droppedVisualCueCount))",
            "fun drain(): ImmutableList<VisualFxCue>",
            "val result = buildList {",
            "addAll(cues)",
            "}.toImmutableList()\n        cues.clear()",
            "fun copy(): BoundedVisualFxCueAccumulator = BoundedVisualFxCueAccumulator(",
            "cues = cues.toMutableList()",
        ),
        additionalSourceAnchors = listOf(
            BoundAnchor(GAMEPLAY_PROGRESSION_SYSTEM_PATH, "visualFxCues.record(cue)"),
            BoundAnchor(
                GAMEPLAY_RENDER_MODEL_MAPPER_PATH,
                "target.visualFxCues = visualFxCues.copy()",
            ),
        ),
    ),
    BoundProjection(
        "gameplay.interaction-particles", "700", GAMEPLAY_FX_PATH, "MAX_PARTICLES = 700",
        GAMEPLAY_FX_TEST_PATH, "particlesAcceptSevenHundredAndRejectSevenHundredFirst",
        "directionalParticlesAcceptSevenHundredAndRejectSevenHundredFirst",
        additionalRequiredTokens = listOf(
            "if (particles.size >= InteractionFxLimits.MAX_PARTICLES) return@repeat\n" +
                "            val angle = random.nextFloat() * TAU",
            "val life = 0.25f + random.nextFloat() * 0.55f\n            particles += Particle(",
            "if (particles.size >= InteractionFxLimits.MAX_PARTICLES) return@repeat\n" +
                "            val angle = baseAngle + (random.nextFloat() - 0.5f) * 1.15f",
            "val life = 0.22f + random.nextFloat() * 0.42f\n            particles += Particle(",
            "particles = particles.map { value ->",
            "        }.toImmutableList(),\n        motionEchoes = motionEchoes.map { value ->",
        ),
    ),
    BoundProjection(
        "gameplay.interaction-motion-echoes", "36", GAMEPLAY_FX_PATH, "MAX_MOTION_ECHOES = 36",
        GAMEPLAY_FX_TEST_PATH, "motionEchoesAcceptThirtySixAndTrimOldestOnThirtySeventh",
        additionalRequiredTokens = listOf(
            "motionEchoes += MotionEcho(",
            "trimFront(motionEchoes, InteractionFxLimits.MAX_MOTION_ECHOES)",
            "while (values.size > maximum) values.removeAt(0)",
            "motionEchoes = motionEchoes.map { value ->",
            "        }.toImmutableList(),\n        shockwaves = shockwaves.map { value ->",
        ),
    ),
    BoundProjection(
        "gameplay.interaction-shockwaves", "48", GAMEPLAY_FX_PATH, "MAX_SHOCKWAVES = 48",
        GAMEPLAY_FX_TEST_PATH, "shockwavesAcceptFortyEightAndTrimOldestOnFortyNinth",
        additionalRequiredTokens = listOf(
            "shockwaves += Shockwave(",
            "trimFront(shockwaves, InteractionFxLimits.MAX_SHOCKWAVES)",
            "while (values.size > maximum) values.removeAt(0)",
            "shockwaves = shockwaves.map { value ->",
            "        }.toImmutableList(),\n        damageNumbers = damageNumbers.map { value ->",
        ),
    ),
    BoundProjection(
        "gameplay.interaction-damage-numbers", "140", GAMEPLAY_FX_PATH, "MAX_DAMAGE_NUMBERS = 140",
        GAMEPLAY_FX_TEST_PATH, "damageNumbersAcceptOneHundredFortyAndRejectOneHundredFortyFirst",
        additionalRequiredTokens = listOf(
            "if (damageNumbers.size < InteractionFxLimits.MAX_DAMAGE_NUMBERS)",
            "damageNumbers += DamageNumber(",
            "damageNumbers = damageNumbers.map { value ->",
            "        }.toImmutableList(),\n        weaponArcs = weaponArcs.map { value ->",
        ),
    ),
    BoundProjection(
        "gameplay.interaction-weapon-arcs", "128", GAMEPLAY_FX_PATH, "MAX_WEAPON_ARCS = 128",
        GAMEPLAY_FX_TEST_PATH, "weaponArcsAcceptOneHundredTwentyEightAndTrimOldestOnOneHundredTwentyNinth",
        additionalRequiredTokens = listOf(
            "weaponArcs += WeaponArc(",
            "trimFront(weaponArcs, InteractionFxLimits.MAX_WEAPON_ARCS)",
            "while (values.size > maximum) values.removeAt(0)",
            "weaponArcs = weaponArcs.map { value ->\n" +
                "            WeaponArcProjection(value.fromX, value.fromY, value.toX, value.toY, value.life)\n" +
                "        }.toImmutableList()",
        ),
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
        "gameplay.interaction-frame-delta", "0..1", GAMEPLAY_PROTOCOL_PATH,
        "const val MIN_FRAME_DELTA_SECONDS: Float = 0f", GAMEPLAY_INGRESS_TEST_PATH,
        "frameDeltaAcceptsExactBoundsAndRejectsTheNextRepresentableValues",
        additionalRequiredTokens = listOf("const val MAX_FRAME_DELTA_SECONDS: Float = 1f"),
        additionalSourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_INGRESS_PATH,
                listOf(
                    "const val MIN_FRAME_DELTA_SECONDS = GameplayInteractionLimits.MIN_FRAME_DELTA_SECONDS",
                    "const val MAX_FRAME_DELTA_SECONDS = GameplayInteractionLimits.MAX_FRAME_DELTA_SECONDS",
                ),
            ),
        ),
    ),
    BoundProjection(
        "gameplay.interaction-viewport", "1..32768", GAMEPLAY_PROTOCOL_PATH,
        "const val MIN_VIEWPORT_DIMENSION_PX: Float = 1f", GAMEPLAY_INGRESS_TEST_PATH,
        "viewportAcceptsNAndRejectsNPlusOneForEveryBoundedField",
        additionalRequiredTokens = listOf("const val MAX_VIEWPORT_DIMENSION_PX: Float = 32_768f"),
        additionalSourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_INGRESS_PATH,
                listOf(
                    "const val MIN_VIEWPORT_DIMENSION_PX = GameplayInteractionLimits.MIN_VIEWPORT_DIMENSION_PX",
                    "const val MAX_VIEWPORT_DIMENSION_PX = GameplayInteractionLimits.MAX_VIEWPORT_DIMENSION_PX",
                ),
            ),
        ),
    ),
    BoundProjection(
        "gameplay.interaction-density", "0.5..8", GAMEPLAY_PROTOCOL_PATH,
        "const val MIN_DENSITY: Float = 0.5f", GAMEPLAY_INGRESS_TEST_PATH,
        "viewportAcceptsNAndRejectsNPlusOneForEveryBoundedField",
        additionalRequiredTokens = listOf("const val MAX_DENSITY: Float = 8f"),
        additionalSourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_INGRESS_PATH,
                listOf(
                    "const val MIN_DENSITY = GameplayInteractionLimits.MIN_DENSITY",
                    "const val MAX_DENSITY = GameplayInteractionLimits.MAX_DENSITY",
                ),
            ),
        ),
    ),
    BoundProjection(
        "gameplay.interaction-pointer", "finite representation", GAMEPLAY_INGRESS_PATH,
        "finiteFailure(rawXpx, InteractionInputField.POINTER_X_PX)", GAMEPLAY_INGRESS_TEST_PATH,
        "pointerValidationAcceptsFiniteCoordinatesAndRejectsNonfiniteXAndY",
        additionalRequiredTokens = listOf("finiteFailure(rawYpx, InteractionInputField.POINTER_Y_PX)"),
        additionalSourceAnchors = listOf(
            BoundAnchor(GAMEPLAY_PROTOCOL_PATH, "require(x.isFinite() && y.isFinite())"),
        ),
    ),
    BoundProjection(
        "gameplay.authoritative-frame-delta", "0..1", GAMEPLAY_PROTOCOL_PATH,
        "const val MIN_FRAME_DELTA_SECONDS: Float = 0f", GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH,
        "validatedFactoriesOwnFixedBoundsAndReducerOwnsPointerMembership",
        additionalRequiredTokens = listOf(
            "const val MAX_FRAME_DELTA_SECONDS: Float = 1f",
            "realDeltaSeconds in",
            "GameplayInteractionLimits.MIN_FRAME_DELTA_SECONDS..",
            "GameplayInteractionLimits.MAX_FRAME_DELTA_SECONDS",
        ),
        additionalSourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_INGRESS_PATH,
                listOf(
                    "minimum = InteractionIngressLimits.MIN_FRAME_DELTA_SECONDS",
                    "maximum = InteractionIngressLimits.MAX_FRAME_DELTA_SECONDS",
                ),
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
        "gameplay.authoritative-viewport", "1..32768", GAMEPLAY_PROTOCOL_PATH,
        "const val MIN_VIEWPORT_DIMENSION_PX: Float = 1f", GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH,
        "validatedFactoriesOwnFixedBoundsAndReducerOwnsPointerMembership",
        additionalRequiredTokens = listOf(
            "const val MAX_VIEWPORT_DIMENSION_PX: Float = 32_768f",
            "GameplayInteractionLimits.MIN_VIEWPORT_DIMENSION_PX..",
            "GameplayInteractionLimits.MAX_VIEWPORT_DIMENSION_PX",
        ),
        additionalSourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_INGRESS_PATH,
                listOf(
                    "minimum = InteractionIngressLimits.MIN_VIEWPORT_DIMENSION_PX",
                    "maximum = InteractionIngressLimits.MAX_VIEWPORT_DIMENSION_PX",
                ),
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
        "gameplay.authoritative-density", "0.5..8", GAMEPLAY_PROTOCOL_PATH,
        "const val MIN_DENSITY: Float = 0.5f", GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH,
        "validatedFactoriesOwnFixedBoundsAndReducerOwnsPointerMembership",
        additionalRequiredTokens = listOf(
            "const val MAX_DENSITY: Float = 8f",
            "density in GameplayInteractionLimits.MIN_DENSITY..",
            "GameplayInteractionLimits.MAX_DENSITY",
        ),
        additionalSourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_INGRESS_PATH,
                listOf(
                    "minimum = InteractionIngressLimits.MIN_DENSITY",
                    "maximum = InteractionIngressLimits.MAX_DENSITY",
                ),
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
        "intent.x < 0f || intent.x > state.model.screenWidth",
        GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH,
        "validatedFactoriesOwnFixedBoundsAndReducerOwnsPointerMembership",
        additionalRequiredTokens = listOf(
            "intent.y < 0f || intent.y > state.model.screenHeight",
        ),
        additionalSourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_PROTOCOL_PATH,
                "require(x.isFinite() && y.isFinite())",
            ),
        ),
        additionalEvidenceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_INGRESS_TEST_PATH,
                "pointerValidationAcceptsFiniteCoordinatesAndRejectsNonfiniteXAndY",
            ),
            BoundAnchor(
                "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/GameplayNucleusTest.kt",
                "pointerViewportMembershipAcceptsInclusiveEdgesAndRejectsAllFourAdjacentCoordinates",
            ),
        ),
    ),
    BoundProjection(
        "gameplay.choice-index", "0..3",
        GAMEPLAY_PROTOCOL_PATH,
        "const val MIN_CHOICE_INDEX: Int = 0", GAMEPLAY_COLLECTION_BOUNDS_TEST_PATH,
        "choiceIndexThreeIsAcceptedAndFourIsRejected",
        additionalRequiredTokens = listOf(
            "const val MAX_CHOICE_INDEX: Int = 3",
            "index in GameplayInteractionLimits.MIN_CHOICE_INDEX..",
            "GameplayInteractionLimits.MAX_CHOICE_INDEX",
        ),
        additionalSourceAnchors = listOf(
            BoundAnchor(GAMEPLAY_STATE_PATH, "const val MAX_CHOICES = 4"),
            BoundAnchor(
                GAMEPLAY_INGRESS_PATH,
                listOf(
                    "const val MIN_CHOICE_INDEX = GameplayInteractionLimits.MIN_CHOICE_INDEX",
                    "const val MAX_CHOICE_INDEX = GameplayInteractionLimits.MAX_CHOICE_INDEX",
                ),
            ),
        ),
        additionalEvidenceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_INGRESS_TEST_PATH,
                "choiceIndexValidationAcceptsZeroThroughThreeAndRejectsBothAdjacentValues",
            ),
        ),
    ),
    BoundProjection(
        "audio.accepted-caller-effect-requests", "32", AUDIO_SERVICE_PATH,
        "MAX_ACCEPTED_EFFECT_REQUESTS_PER_ADVANCE = 32",
        AUDIO_SERVICE_TEST_PATH, "callerEffectIngressAcceptsThirtyTwoAndRejectsThirtyThird",
        additionalRequiredTokens = listOf(
            "requests.size <= MAX_ACCEPTED_EFFECT_REQUESTS_PER_ADVANCE",
        ),
    ),
    BoundProjection(
        "audio.selected-caller-effect-requests", "3", AUDIO_SERVICE_PATH,
        "MAX_SELECTED_EFFECT_REQUESTS_PER_ADVANCE = 3",
        AUDIO_SERVICE_TEST_PATH, "callerEffectRequestSelectionAcceptsThreeAndDropsFourth",
        additionalRequiredTokens = listOf(
            "selectToneRequests(requests, MAX_SELECTED_EFFECT_REQUESTS_PER_ADVANCE)",
            ".distinct()",
            ".take(limit.coerceAtLeast(0))",
        ),
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
        additionalRequiredTokens = listOf(
            "MAX_FREQUENCY_HZ: Float = 20_000f",
            "frequencyHz.isFinite()",
            "frequencyHz >= ToneRequestLimits.MIN_FREQUENCY_HZ",
            "frequencyHz <= ToneRequestLimits.MAX_FREQUENCY_HZ",
        ),
    ),
    BoundProjection(
        "audio.tone-duration-seconds", "0.001..1",
        "resource/audio/api/src/commonMain/kotlin/kinetickk/resource/audio/api/AudioService.kt",
        "MIN_DURATION_SECONDS: Float = 0.001f", AUDIO_SERVICE_TEST_PATH,
        "toneRequestIngressAcceptsInclusiveBoundsAndRejectsNextRepresentableValues",
        additionalRequiredTokens = listOf(
            "MAX_DURATION_SECONDS: Float = 1f",
            "durationSeconds.isFinite()",
            "durationSeconds >= ToneRequestLimits.MIN_DURATION_SECONDS",
            "durationSeconds <= ToneRequestLimits.MAX_DURATION_SECONDS",
        ),
    ),
    BoundProjection(
        "audio.tone-gain", "0..1",
        "resource/audio/api/src/commonMain/kotlin/kinetickk/resource/audio/api/AudioService.kt",
        "MIN_GAIN: Float = 0f", AUDIO_SERVICE_TEST_PATH,
        "toneRequestIngressAcceptsInclusiveBoundsAndRejectsNextRepresentableValues",
        additionalRequiredTokens = listOf(
            "MAX_GAIN: Float = 1f",
            "require(gain.isFinite() && gain in ToneRequestLimits.MIN_GAIN..ToneRequestLimits.MAX_GAIN)",
        ),
    ),
    BoundProjection(
        "audio.desktop-workers", "1", DESKTOP_AUDIO_PATH, "WORKER_COUNT = 1",
        DESKTOP_AUDIO_TEST_PATH, "workerAndDiscardOldestQueueEnforceOneAndTwentyFour",
        additionalRequiredTokens = listOf(
            "private val executor = ThreadPoolExecutor(\n" +
                "        DesktopAudioExecutionPolicy.WORKER_COUNT,\n" +
                "        DesktopAudioExecutionPolicy.WORKER_COUNT,",
        ),
    ),
    BoundProjection(
        "audio.desktop-queue-capacity", "24", DESKTOP_AUDIO_PATH, "QUEUE_CAPACITY = 24",
        DESKTOP_AUDIO_TEST_PATH, "workerAndDiscardOldestQueueEnforceOneAndTwentyFour",
        additionalRequiredTokens = listOf(
            "ArrayBlockingQueue(DesktopAudioExecutionPolicy.QUEUE_CAPACITY)",
            "ThreadPoolExecutor.DiscardOldestPolicy()",
        ),
    ),
    BoundProjection(
        "audio.desktop-synthesis-samples", "22050", DESKTOP_AUDIO_PATH,
        "MAX_SAMPLE_COUNT = SAMPLE_RATE", DESKTOP_AUDIO_TEST_PATH,
        "synthesisBufferAcceptsMaximumDurationAndRejectsNext",
        additionalRequiredTokens = listOf(
            "SAMPLE_RATE = 22_050",
            "internal fun desktopToneBufferShape(durationSeconds: Float)",
            "check(sampleCount <= DesktopAudioExecutionPolicy.MAX_SAMPLE_COUNT)",
            "val shape = desktopToneBufferShape(request.durationSeconds)",
        ),
    ),
    BoundProjection(
        "audio.desktop-synthesis-bytes", "44100", DESKTOP_AUDIO_PATH,
        "MAX_PCM_BYTES = MAX_SAMPLE_COUNT * BYTES_PER_SAMPLE", DESKTOP_AUDIO_TEST_PATH,
        "synthesisBufferAcceptsMaximumDurationAndRejectsNext",
        additionalRequiredTokens = listOf(
            "BYTES_PER_SAMPLE = 2",
            "internal fun desktopToneBufferShape(durationSeconds: Float)",
            "check(byteCount <= DesktopAudioExecutionPolicy.MAX_PCM_BYTES)",
            "val shape = desktopToneBufferShape(request.durationSeconds)",
            "val bytes = ByteArray(shape.byteCount)",
        ),
    ),
    BoundProjection(
        "content.items", "400",
        "ball/content/api/src/commonMain/kotlin/kinetickk/ball/content/api/ContentVersion.kt",
        "MAX_ITEMS: Int = 400", CONTENT_BOUNDS_TEST_PATH,
        "itemBoundRejectsNPlusOne", "exactCatalogBoundsAreAccepted",
        additionalSourceAnchors = listOf(
            BoundAnchor(
                CONTENT_CATALOG_PATH,
                listOf(
                    "requireBound(\"items\", data.items.size, ContentBounds.MAX_ITEMS)",
                    "item.id == index",
                    "private val items = data.items.toImmutableList()",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_STATE_PATH,
                listOf(
                    "?.filterTo(mutableSetOf()) { content.item(it) != null }",
                    "internal val pendingDiscoveredItemIds = mutableSetOf<Int>()",
                    "internal val itemStacks = IntArray(content.items.size)",
                    "internal val familyStacks = IntArray(content.items.maxOfOrNull { it.id / 20 + 1 } ?: 0)",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_PROGRESSION_SYSTEM_PATH,
                listOf(
                    "val item = content.item(itemId) ?: return",
                    "if (discoveredItemIds.add(itemId)) pendingDiscoveredItemIds += itemId",
                    "discoveredItemIds = pendingDiscoveredItemIds.toImmutableSet()",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_NUCLEUS_PATH,
                "itemStacks = state.engine?.model?.itemStacks?.asIterable()?.toImmutableList()",
            ),
            BoundAnchor(
                GAMEPLAY_RENDER_MODEL_MAPPER_PATH,
                listOf(
                    "target.discoveredItemIds.addAll(discoveredItemIds)",
                    "target.pendingDiscoveredItemIds.addAll(pendingDiscoveredItemIds)",
                    "itemStacks.copyInto(target.itemStacks)",
                    "familyStacks.copyInto(target.familyStacks)",
                    "itemStacks = itemStacks.asIterable().toImmutableList()",
                    "discoveredItemIds = discoveredItemIds.toImmutableSet()",
                ),
            ),
        ),
        additionalEvidenceAnchors = listOf(
            BoundAnchor(
                CONTENT_CATALOG_TEST_PATH,
                "publishedCatalogCollectionsExposeNoMutationAuthorityAndCopyBootstrapInputs",
            ),
        ),
    ),
    BoundProjection(
        "content.weapons", "12",
        "ball/content/api/src/commonMain/kotlin/kinetickk/ball/content/api/ContentVersion.kt",
        "MAX_WEAPONS: Int = 12", CONTENT_BOUNDS_TEST_PATH,
        "weaponBoundRejectsNPlusOne", "exactCatalogBoundsAreAccepted",
        additionalSourceAnchors = listOf(
            BoundAnchor(
                CONTENT_CATALOG_PATH,
                listOf(
                    "requireBound(\"weapons\", data.weapons.size, ContentBounds.MAX_WEAPONS)",
                    "data.weapons.map(WeaponDefinition::id) == WeaponId.entries.toList()",
                    "private val weapons = data.weapons.toImmutableList()",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_STATE_PATH,
                listOf(
                    "internal val unlockedWeaponSet = mutableSetOf<WeaponId>().apply",
                    "addAll(bootstrapProgress?.loadout?.unlockedWeapons.orEmpty())",
                    "add(WeaponId.FLUX_WAKE)",
                    "internal var unlockedWeaponView: Set<WeaponId> = unlockedWeaponSet.toSet()",
                    "internal val agonyMutationCounts = IntArray(content.weapons.size)",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_RENDER_MODEL_MAPPER_PATH,
                listOf(
                    "target.unlockedWeaponSet.addAll(unlockedWeaponSet)",
                    "target.unlockedWeaponView = target.unlockedWeaponSet.toSet()",
                    "agonyMutationCounts.copyInto(target.agonyMutationCounts)",
                ),
            ),
            BoundAnchor(
                PROFILE_NUCLEUS_PATH,
                listOf(
                    "val unlocked = state.profile.loadout.unlockedWeapons.toMutableSet()",
                    "unlocked += id",
                    "unlockedWeapons = unlocked",
                ),
            ),
        ),
        additionalEvidenceAnchors = listOf(
            BoundAnchor(
                CONTENT_CATALOG_TEST_PATH,
                "publishedCatalogCollectionsExposeNoMutationAuthorityAndCopyBootstrapInputs",
            ),
        ),
    ),
    BoundProjection(
        "content.meta-upgrades", "8",
        "ball/content/api/src/commonMain/kotlin/kinetickk/ball/content/api/ContentVersion.kt",
        "MAX_META_UPGRADES: Int = 8", CONTENT_BOUNDS_TEST_PATH,
        "metaUpgradeBoundRejectsNPlusOne", "exactCatalogBoundsAreAccepted",
        additionalSourceAnchors = listOf(
            BoundAnchor(
                CONTENT_CATALOG_PATH,
                listOf(
                    "requireBound(\"meta upgrades\", data.metaUpgrades.size, ContentBounds.MAX_META_UPGRADES)",
                    "data.metaUpgrades.map(MetaUpgradeDefinition::id) == MetaUpgradeId.entries.toList()",
                    "private val metaUpgrades = data.metaUpgrades.toImmutableList()",
                ),
            ),
            BoundAnchor(GAMEPLAY_STATE_PATH, "internal val metaRanks = IntArray(content.metaUpgrades.size)"),
            BoundAnchor(GAMEPLAY_RENDER_MODEL_MAPPER_PATH, "metaRanks.copyInto(target.metaRanks)"),
        ),
        additionalEvidenceAnchors = listOf(
            BoundAnchor(
                CONTENT_CATALOG_TEST_PATH,
                "publishedCatalogCollectionsExposeNoMutationAuthorityAndCopyBootstrapInputs",
            ),
        ),
    ),
    BoundProjection(
        "content.relics", "40",
        "ball/content/api/src/commonMain/kotlin/kinetickk/ball/content/api/ContentVersion.kt",
        "MAX_RELICS: Int = 40", CONTENT_BOUNDS_TEST_PATH,
        "relicBoundRejectsNPlusOne", "exactCatalogBoundsAreAccepted",
        additionalSourceAnchors = listOf(
            BoundAnchor(
                CONTENT_CATALOG_PATH,
                listOf(
                    "requireBound(\"relics\", data.relics.size, ContentBounds.MAX_RELICS)",
                    "data.relics.map(RelicDefinition::id) == RelicId.entries.toList()",
                    "private val relics = data.relics.toImmutableList()",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_STATE_PATH,
                listOf(
                    "internal val relicRanks = IntArray(content.relics.size)",
                    "internal val relicCooldowns = FloatArray(content.relics.size)",
                    "internal val relicCounters = IntArray(content.relics.size)",
                    "internal val relicProcCounts = IntArray(content.relics.size)",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_ENEMY_SYSTEM_PATH,
                listOf(
                    "relicCounters = IntArray(content.relics.size)",
                    "relicTimers = FloatArray(content.relics.size)",
                    "relicValues = FloatArray(content.relics.size)",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_RENDER_MODEL_MAPPER_PATH,
                listOf(
                    "relicCounters = enemy.relicCounters.copyOf()",
                    "relicTimers = enemy.relicTimers.copyOf()",
                    "relicValues = enemy.relicValues.copyOf()",
                    "relicRanks = relicRanks.asIterable().toImmutableList()",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_REWARD_SYSTEM_PATH,
                listOf(
                    "relicCounters = IntArray(content.relics.size)",
                    "relicTimers = FloatArray(content.relics.size)",
                    "relicValues = FloatArray(content.relics.size)",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_RENDER_MODEL_MAPPER_PATH,
                listOf(
                    "relicRanks.copyInto(target.relicRanks)",
                    "relicCooldowns.copyInto(target.relicCooldowns)",
                    "relicCounters.copyInto(target.relicCounters)",
                    "relicProcCounts.copyInto(target.relicProcCounts)",
                ),
            ),
        ),
        additionalEvidenceAnchors = listOf(
            BoundAnchor(
                CONTENT_CATALOG_TEST_PATH,
                "publishedCatalogCollectionsExposeNoMutationAuthorityAndCopyBootstrapInputs",
            ),
        ),
    ),
    BoundProjection(
        "content.rebirth-level", "0..10",
        "ball/content/api/src/commonMain/kotlin/kinetickk/ball/content/api/ContentVersion.kt",
        "MAX_REBIRTH_LEVEL: Int = 10", CONTENT_BOUNDS_TEST_PATH,
        "rebirthBoundRejectsLevelEleven", "exactCatalogBoundsAreAccepted",
        additionalSourceAnchors = listOf(
            BoundAnchor(
                CONTENT_CATALOG_PATH,
                listOf(
                    "ContentBounds.MAX_REBIRTH_LEVEL - ContentBounds.MIN_REBIRTH_LEVEL + 1",
                    "requireBound(\"Rebirth profiles\", data.rebirthProfiles.size, maximumRebirthProfiles)",
                    "(ContentBounds.MIN_REBIRTH_LEVEL..ContentBounds.MAX_REBIRTH_LEVEL).toList()",
                    "private val rebirthProfiles = data.rebirthProfiles.toImmutableList()",
                ),
            ),
        ),
        additionalEvidenceAnchors = listOf(
            BoundAnchor(
                CONTENT_CATALOG_TEST_PATH,
                "publishedCatalogCollectionsExposeNoMutationAuthorityAndCopyBootstrapInputs",
            ),
        ),
    ),
    BoundProjection(
        "content.relic-slots", "4",
        "ball/content/impl/src/commonMain/kotlin/kinetickk/ball/content/impl/DefaultContentCatalog.kt",
        "RelicPolicy(maxSlots = 4, maxRank = 5)",
        "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/characterization/RelicSystemTest.kt",
        "relicMatrixStopsAtFourSlotsAndDuplicateRanksStopAtFive",
        additionalSourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_PROGRESSION_SYSTEM_PATH,
                listOf(
                    "if (equippedRelics.size >= content.relicPolicy.maxSlots) return",
                    "equippedRelics = equippedRelics + EquippedRelic(id, 1)",
                    "val updated = equippedRelics.toMutableList()\n" +
                        "            updated[currentIndex] = current.copy(rank = current.rank + 1)\n" +
                        "            equippedRelics = updated.toList()",
                    "val updated = equippedRelics.toMutableList()\n" +
                        "    updated[slot] = EquippedRelic(id, 1)\n" +
                        "    equippedRelics = updated.toList()",
                    "val updated = equippedRelics.toMutableList()\n" +
                        "        updated[slot] = current.copy(rank = current.rank + 1)\n" +
                        "        equippedRelics = updated.toList()",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_RENDER_MODEL_MAPPER_PATH,
                listOf(
                    "target.equippedRelics = equippedRelics.toList()",
                    "equippedRelics = equippedRelics.toImmutableList()",
                ),
            ),
        ),
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
            BoundAnchor(
                GAMEPLAY_PROGRESSION_SYSTEM_PATH,
                listOf(
                    "val current = equippedRelics[currentIndex]\n" +
                        "        if (current.rank >= content.relicPolicy.maxRank)",
                    "updated[currentIndex] = current.copy(rank = current.rank + 1)",
                    "val current = equippedRelics[slot]\n    if (current.rank >= content.relicPolicy.maxRank)",
                    "updated[slot] = current.copy(rank = current.rank + 1)",
                ),
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
        PROFILE_STATE_PATH, "profile.labProgress.ranks.size != policy.metaUpgrades.size",
        PROFILE_NUCLEUS_TEST_PATH,
        "constructionBootstrapRetainsExactBoundsAndRejectsFirstOverflow",
        additionalSourceAnchors = listOf(
            BoundAnchor(
                PROFILE_CODEC_PATH,
                listOf(
                    "profile.labProgress.ranks.size == MetaUpgradeId.entries.size",
                    "profile.labProgress.ranks.size == ContentBounds.MAX_META_UPGRADES",
                ),
            ),
            BoundAnchor(
                PROFILE_NUCLEUS_PATH,
                listOf(
                    "val ranks = state.profile.labProgress.ranks.toMutableList()",
                    "ranks[id.ordinal] = currentRank + 1",
                    "labProgress = LabProgress(ranks)",
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
        PROFILE_STATE_PATH,
        "profile.labProgress.rank(definition.id) !in 0..definition.maxRanks",
        PROFILE_NUCLEUS_TEST_PATH,
        "constructionBootstrapRetainsExactBoundsAndRejectsFirstOverflow",
        additionalSourceAnchors = listOf(
            BoundAnchor(
                PROFILE_NUCLEUS_PATH,
                listOf(
                    "if (currentRank >= definition.maxRanks)",
                    "ranks[id.ordinal] = currentRank + 1",
                ),
            ),
        ),
        additionalEvidenceAnchors = listOf(
            BoundAnchor(
                "ball/profile/impl/src/commonTest/kotlin/kinetickk/ball/profile/impl/" +
                    "ProfileComponentCharacterizationTest.kt",
                "everyRejectionLeavesStateRevisionAndEffectsUntouched",
            ),
        ),
    ),
    BoundProjection(
        "profile.retained-discoveries", "policy.itemCount (<=400)",
        PROFILE_STATE_PATH,
        "profile.collection.discoveredItemIds.size > policy.itemCount",
        PROFILE_NUCLEUS_TEST_PATH,
        "constructionBootstrapRetainsExactBoundsAndRejectsFirstOverflow",
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
            BoundAnchor(
                PROFILE_NUCLEUS_PATH,
                listOf(
                    "update.discoveredItemIds.firstOrNull { !state.policy.containsItem(it) }",
                    "val discoveries = state.profile.collection.discoveredItemIds.toMutableSet().apply",
                    "addAll(update.discoveredItemIds)",
                    "collection = PlayerCollection(discoveries)",
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
            BoundAnchor(
                PROFILE_NUCLEUS_TEST_PATH,
                "gameplayProgressValidationUsesClosedRejectionReasons",
            ),
        ),
    ),
    BoundProjection(
        "profile.preference-master-volume", "0..1", PROFILE_PLAYER_PATH,
        "masterVolume = masterVolume.coerceIn(0f, 1f)", PROFILE_NUCLEUS_TEST_PATH,
        "constructionBootstrapPreferencesAcceptExactMaximaAndRejectOverflow",
        additionalSourceAnchors = listOf(
            BoundAnchor(PROFILE_STATE_PATH, "preferences != preferences.normalized()"),
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
        "constructionBootstrapPreferencesAcceptExactMaximaAndRejectOverflow",
        additionalSourceAnchors = listOf(
            BoundAnchor(
                PROFILE_STATE_PATH,
                "preferences.simulationSpeed !in kinetickk.ball.profile.api.SIMULATION_SPEED_OPTIONS",
            ),
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
        "constructionBootstrapPreferencesAcceptExactMaximaAndRejectOverflow",
        additionalSourceAnchors = listOf(
            BoundAnchor(PROFILE_STATE_PATH, "preferences != preferences.normalized()"),
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
        "constructionBootstrapPreferencesAcceptExactMaximaAndRejectOverflow",
        additionalSourceAnchors = listOf(
            BoundAnchor(
                PROFILE_STATE_PATH,
                listOf(
                    "preferences.damageNumberTierThreshold !in",
                    "kinetickk.ball.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS",
                ),
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
        additionalEvidenceToken = "gameplayProgressValidationUsesClosedRejectionReasons",
        additionalRequiredTokens = listOf(
            "update.discoveredItemIds.firstOrNull { !state.policy.containsItem(it) }",
        ),
    ),
    BoundProjection(
        "profile.v4-utf8-bytes", "65536",
        "ball/profile/resource/src/commonMain/kotlin/kinetickk/ball/profile/resource/ProfileCodec.kt",
        "MAX_PROFILE_PAYLOAD_BYTES: Int = 65_536",
        "ball/profile/resource/src/commonTest/kotlin/kinetickk/ball/profile/resource/ProfileCodecTest.kt",
        "byteLimitAndUtf8AreCheckedBeforeJsonDecode",
        "encodedByteLimitAcceptsExactlyNAndRejectsFirstNPlusOne",
        additionalRequiredTokens = listOf(
            "return when (payload.utf8Validation())",
            "fun decode(payload: String): ProfileV4DecodeResult {\n        when (payload.utf8Validation())",
            "private fun String.utf8Validation(): Utf8Validation",
            "if (byteCount > MAX_PROFILE_PAYLOAD_BYTES - encodedBytes) return Utf8Validation.TooLarge",
        ),
    ),
    BoundProjection(
        "profile.desktop-preferences-value-length", "8192 UTF-16 code units",
        DESKTOP_AUDIO_PATH,
        "desktopProfilePayloadAdmission(payload.length)?.let { return it }",
        DESKTOP_AUDIO_TEST_PATH,
        "desktopPreferencesValueLengthAccepts8192AndRejects8193BeforeExecution",
        additionalRequiredTokens = listOf(
            "internal fun desktopProfilePayloadAdmission(valueLength: Int): " +
                "ProfilePersistenceMutationResult?",
            "valueLength <= Preferences.MAX_VALUE_LENGTH",
            "ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION",
        ),
    ),
    BoundProjection(
        "profile.desktop-preferences-keys-per-node", "64",
        DESKTOP_AUDIO_PATH,
        "MAX_DESKTOP_PREFERENCE_KEYS_PER_NODE: Int = 64",
        DESKTOP_AUDIO_TEST_PATH,
        "desktopPreferenceKeyCountAccepts64AndRejects65BeforeIteration",
        additionalRequiredTokens = listOf(
            "desktopPreferenceKeyCountAdmission(storedKeys.size)",
            "loadKeyNames = node::keys",
            "val storedKeys = try",
            "loadKeyNames()",
            "storedKeys.any { storedKey -> storedKey == exactKey }",
        ),
    ),
).also { bounds -> requireUniqueKeys("expectedBounds", bounds, BoundProjection::id) }

internal val mechanicallyDerivedBounds = listOf(
    MechanicallyDerivedBoundProjection(
        id = "gameplay.item-indexed-state",
        value = "item arrays/discoveries <=400; family stacks <=20",
        derivation = "validated contiguous Item IDs; pending discoveries are a subset; copies preserve cardinality",
        sourceAnchors = listOf(
            BoundAnchor(
                CONTENT_CATALOG_PATH,
                listOf(
                    "requireBound(\"items\", data.items.size, ContentBounds.MAX_ITEMS)",
                    "item.id == index",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_STATE_PATH,
                listOf(
                    "?.filterTo(mutableSetOf()) { content.item(it) != null }",
                    "internal val pendingDiscoveredItemIds = mutableSetOf<Int>()",
                    "internal val itemStacks = IntArray(content.items.size)",
                    "internal val familyStacks = IntArray(content.items.maxOfOrNull { it.id / 20 + 1 } ?: 0)",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_PROGRESSION_SYSTEM_PATH,
                listOf(
                    "val item = content.item(itemId) ?: return",
                    "if (discoveredItemIds.add(itemId)) pendingDiscoveredItemIds += itemId",
                    "discoveredItemIds = pendingDiscoveredItemIds.toImmutableSet()",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_NUCLEUS_PATH,
                "itemStacks = state.engine?.model?.itemStacks?.asIterable()?.toImmutableList()",
            ),
            BoundAnchor(
                GAMEPLAY_RENDER_MODEL_MAPPER_PATH,
                listOf(
                    "target.discoveredItemIds.addAll(discoveredItemIds)",
                    "target.pendingDiscoveredItemIds.addAll(pendingDiscoveredItemIds)",
                    "itemStacks.copyInto(target.itemStacks)",
                    "familyStacks.copyInto(target.familyStacks)",
                    "itemStacks = itemStacks.asIterable().toImmutableList()",
                    "discoveredItemIds = discoveredItemIds.toImmutableSet()",
                ),
            ),
        ),
        evidenceAnchors = listOf(
            BoundAnchor(
                CONTENT_BOUNDS_TEST_PATH,
                listOf("itemBoundRejectsNPlusOne", "exactCatalogBoundsAreAccepted"),
            ),
            BoundAnchor(
                GAMEPLAY_SYSTEMS_TEST_PATH,
                "everyCatalogItemCanBeAcquiredAndDiscovered",
            ),
            BoundAnchor(
                "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/GameplayNucleusTest.kt",
                "retainedRenderAndQueryCollectionsStayImmutable",
            ),
        ),
        policyRow =
            "| `gameplay.item-indexed-state` | item arrays/discoveries <=400; family stacks <=20 | " +
                "validated contiguous Item IDs; pending is a subset |",
    ),
    MechanicallyDerivedBoundProjection(
        id = "gameplay.weapon-indexed-state",
        value = "unlocked weapons/mutation counters <=12",
        derivation = "closed WeaponId bootstrap with no live growth; copy and fixed array retain captured cardinality",
        sourceAnchors = listOf(
            BoundAnchor(
                CONTENT_CATALOG_PATH,
                listOf(
                    "requireBound(\"weapons\", data.weapons.size, ContentBounds.MAX_WEAPONS)",
                    "data.weapons.map(WeaponDefinition::id) == WeaponId.entries.toList()",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_STATE_PATH,
                listOf(
                    "internal val unlockedWeaponSet = mutableSetOf<WeaponId>().apply",
                    "addAll(bootstrapProgress?.loadout?.unlockedWeapons.orEmpty())",
                    "add(WeaponId.FLUX_WAKE)",
                    "internal var unlockedWeaponView: Set<WeaponId> = unlockedWeaponSet.toSet()",
                    "internal val agonyMutationCounts = IntArray(content.weapons.size)",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_RENDER_MODEL_MAPPER_PATH,
                listOf(
                    "target.unlockedWeaponSet.addAll(unlockedWeaponSet)",
                    "target.unlockedWeaponView = target.unlockedWeaponSet.toSet()",
                    "agonyMutationCounts.copyInto(target.agonyMutationCounts)",
                ),
            ),
        ),
        evidenceAnchors = listOf(
            BoundAnchor(
                CONTENT_BOUNDS_TEST_PATH,
                listOf("weaponBoundRejectsNPlusOne", "exactCatalogBoundsAreAccepted"),
            ),
            BoundAnchor(
                PROFILE_CODEC_TEST_PATH,
                "schemaMaximumUnlockedWeaponsLabRanksAndDiscoveriesRoundTripWithoutLoss",
            ),
            BoundAnchor(
                "ball/gameplay/nucleus/src/commonTest/kotlin/kinetickk/ball/gameplay/nucleus/GameplayNucleusTest.kt",
                "configurationValidatorCoversEveryClosedBoundaryReason",
            ),
        ),
        policyRow =
            "| `gameplay.weapon-indexed-state` | unlocked weapons/mutation counters <=12 | " +
                "closed WeaponId bootstrap; invariant-preserving copy |",
    ),
    MechanicallyDerivedBoundProjection(
        id = "gameplay.meta-indexed-state",
        value = "rank slots <=8",
        derivation = "validated closed MetaUpgradeId catalog sizes the fixed array",
        sourceAnchors = listOf(
            BoundAnchor(
                CONTENT_CATALOG_PATH,
                "requireBound(\"meta upgrades\", data.metaUpgrades.size, ContentBounds.MAX_META_UPGRADES)",
            ),
            BoundAnchor(GAMEPLAY_STATE_PATH, "internal val metaRanks = IntArray(content.metaUpgrades.size)"),
            BoundAnchor(GAMEPLAY_RENDER_MODEL_MAPPER_PATH, "metaRanks.copyInto(target.metaRanks)"),
        ),
        evidenceAnchors = listOf(
            BoundAnchor(
                CONTENT_BOUNDS_TEST_PATH,
                listOf("metaUpgradeBoundRejectsNPlusOne", "exactCatalogBoundsAreAccepted"),
            ),
        ),
        policyRow =
            "| `gameplay.meta-indexed-state` | rank slots <=8 | " +
                "validated closed MetaUpgradeId catalog sizes the array |",
    ),
    MechanicallyDerivedBoundProjection(
        id = "gameplay.relic-indexed-state",
        value = "state arrays <=40; enemy relic cells <=120 x 3 x 40",
        derivation = "validated RelicId catalog sizes state and per-enemy arrays; enemy cap bounds the product",
        sourceAnchors = listOf(
            BoundAnchor(CONTENT_CATALOG_PATH, "requireBound(\"relics\", data.relics.size, ContentBounds.MAX_RELICS)"),
            BoundAnchor(
                GAMEPLAY_STATE_PATH,
                listOf(
                    "internal val relicRanks = IntArray(content.relics.size)",
                    "internal val relicCooldowns = FloatArray(content.relics.size)",
                    "internal val relicCounters = IntArray(content.relics.size)",
                    "internal val relicProcCounts = IntArray(content.relics.size)",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_ENEMY_SYSTEM_PATH,
                listOf(
                    "if (enemies.size >= content.rebirth.maxActiveEnemies) return false",
                    "relicCounters = IntArray(content.relics.size)",
                    "relicTimers = FloatArray(content.relics.size)",
                    "relicValues = FloatArray(content.relics.size)",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_REWARD_SYSTEM_PATH,
                listOf(
                    "if (enemies.size >= content.rebirth.maxActiveEnemies) return@repeat",
                    "relicCounters = IntArray(content.relics.size)",
                    "relicTimers = FloatArray(content.relics.size)",
                    "relicValues = FloatArray(content.relics.size)",
                ),
            ),
            BoundAnchor(
                GAMEPLAY_RENDER_MODEL_MAPPER_PATH,
                listOf(
                    "relicCounters = enemy.relicCounters.copyOf()",
                    "relicTimers = enemy.relicTimers.copyOf()",
                    "relicValues = enemy.relicValues.copyOf()",
                    "relicRanks = relicRanks.asIterable().toImmutableList()",
                ),
            ),
        ),
        evidenceAnchors = listOf(
            BoundAnchor(
                CONTENT_BOUNDS_TEST_PATH,
                listOf("relicBoundRejectsNPlusOne", "exactCatalogBoundsAreAccepted"),
            ),
            BoundAnchor(
                GAMEPLAY_BASELINE_TEST_PATH,
                "authoritativeCollectionsEnforceTheCurrentNPlusOneCaps",
            ),
        ),
        policyRow =
            "| `gameplay.relic-indexed-state` | state arrays <=40; enemy relic cells <=120 x 3 x 40 | " +
                "validated RelicId catalog times the enemy cap |",
    ),
    MechanicallyDerivedBoundProjection(
        id = "gameplay.collision-live-enemy-ids",
        value = "<=120",
        derivation = "one-shot filtered identity set from the already bounded enemy collection",
        sourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_COLLISION_SYSTEM_PATH,
                listOf(
                    "val liveEnemyIds = enemies.asSequence()",
                    ".filter { enemy -> !enemy.dead }",
                    ".mapTo(mutableSetOf(), Enemy::id)",
                    "projectile.retainLiveEnemyHits(liveEnemyIds)",
                ),
            ),
        ),
        policyRow = "| `gameplay.collision-live-enemy-ids` | <=120 | filtered identity set from bounded enemies |",
    ),
    MechanicallyDerivedBoundProjection(
        id = "gameplay.reducer-copy-collections",
        value = "equal to committed bounded source cardinality",
        derivation =
            "private copyForReduction clones committed state; subsequent growth still crosses " +
                "the modeled gates",
        sourceAnchors = listOf(
            BoundAnchor(
                GAMEPLAY_RENDER_MODEL_MAPPER_PATH,
                listOf(
                    "internal fun MutableGameState.copyForReduction(): MutableGameState",
                    "target.enemies.addAll(enemies.map { enemy ->",
                    "target.projectiles.addAll(projectiles.map(Projectile::isolatedCopy))",
                    "target.pickups.addAll(pickups.map(Pickup::copy))",
                    "target.trail.addAll(trail.map(TrailPoint::copy))",
                    "target.soundCues.addAll(soundCues)",
                    "target.delayedRelicHits.addAll(delayedRelicHits.map(DelayedRelicHit::copy))",
                    "target.weaponNodes.addAll(weaponNodes.map(WeaponNode::copy))",
                    "target.weaponOrbitals.addAll(weaponOrbitals.map(WeaponOrbital::copy))",
                    "target.choices = choices.toList()",
                    "target.visualFxCues = visualFxCues.copy()",
                ),
            ),
        ),
        policyRow =
            "| `gameplay.reducer-copy-collections` | equal to bounded committed source cardinality | " +
                "invariant-preserving private reduction clone |",
    ),
    MechanicallyDerivedBoundProjection(
        id = "profile.codec-temporary-collections",
        value = "unlocked <=12; ranks =8; discoveries <=400",
        derivation = "temporary lists/sets are materialized only after modeled schema count and ID validation",
        sourceAnchors = listOf(
            BoundAnchor(
                PROFILE_CODEC_PATH,
                listOf(
                    "profile.loadout.unlockedWeapons.size <= ContentBounds.MAX_WEAPONS",
                    "profile.labProgress.ranks.size == ContentBounds.MAX_META_UPGRADES",
                    "profile.collection.discoveredItemIds.size <= ContentBounds.MAX_ITEMS",
                    "val ranks = MetaUpgradeId.entries",
                    ".sortedBy(MetaUpgradeRankV4Dto::id)",
                    "unlockedWeaponIds = profile.loadout.unlockedWeapons\n" +
                        "                    .map(WeaponId::wireId)\n" +
                        "                    .sorted()",
                    "profile.collection.discoveredItemIds.sorted()",
                    "val expectedRankIds = MetaUpgradeId.entries.map { it.wireId() }.sorted()",
                    "val actualRankIds = profile.labProgress.ranks.map(MetaUpgradeRankV4Dto::id)",
                    "actualRankIds.distinct().sorted()",
                    "profile.loadout.unlockedWeaponIds.distinct().sorted()",
                    "profile.collection.discoveredItemIds.distinct().sorted()",
                    "val metaRanks = MutableList(MetaUpgradeId.entries.size) { 0 }",
                    "mapTo(mutableSetOf()) { it.weaponId() }",
                    "PlayerCollection(profile.collection.discoveredItemIds.toSet())",
                ),
            ),
        ),
        evidenceAnchors = listOf(
            BoundAnchor(
                PROFILE_CODEC_TEST_PATH,
                "schemaMaximumUnlockedWeaponsLabRanksAndDiscoveriesRoundTripWithoutLoss",
            ),
        ),
        policyRow =
            "| `profile.codec-temporary-collections` | unlocked <=12; ranks =8; discoveries <=400 | " +
                "materialized after schema validation |",
    ),
    MechanicallyDerivedBoundProjection(
        id = "session.shell-entries",
        value = "1..2",
        derivation = "one base destination plus one nullable overlay",
        sourceAnchors = listOf(
            BoundAnchor(
                SESSION_QUERIES_PATH,
                "get() = overlay?.let { immutableListOf(base, it) } ?: immutableListOf(base)",
            ),
        ),
        evidenceAnchors = listOf(
            BoundAnchor(
                "flow/session/api/src/commonTest/kotlin/kinetickk/flow/session/api/AppSessionApiContractTest.kt",
                "shellProjectionRetainsExactlySevenRoutesAndOnlySessionOwnedWorkflowState",
            ),
            BoundAnchor(
                "flow/session/nucleus/src/commonTest/kotlin/kinetickk/flow/session/nucleus/AppSessionNucleusTest.kt",
                "initialStateOwnsOnlySessionWorkflowAndPublishesNarrowHomeShell",
            ),
        ),
        policyRow = "| `session.shell-entries` | 1..2 | one base plus one nullable overlay |",
    ),
    MechanicallyDerivedBoundProjection(
        id = "content.closed-ui-catalogs",
        value = "CoreShape 3 / WeaponMastery 4",
        derivation = "Content bootstrap requires exact stable enum order",
        sourceAnchors = listOf(
            BoundAnchor(CONTENT_IDS_PATH, "enum class CoreShape { ORB, PRISM, SHARD }"),
            BoundAnchor(
                CONTENT_DEFINITIONS_PATH,
                listOf(
                    "CALIBRATED(\"Calibrated\", 1, 0f, 0f)",
                    "AMPLIFIED(\"Amplified\", 3, 0.12f, 0.08f)",
                    "RESONANT(\"Resonant\", 6, 0.25f, 0.16f)",
                    "ASCENDED(\"Ascended\", 10, 0.45f, 0.25f)",
                ),
            ),
            BoundAnchor(
                CONTENT_CATALOG_PATH,
                listOf(
                    "data.coreShapes.map(CoreShapeDefinition::id) == CoreShape.entries.toList()",
                    "data.weaponMasteries == WeaponMastery.entries.toList()",
                    "private val weaponMasteries = data.weaponMasteries.toImmutableList()",
                    "private val coreShapes = data.coreShapes.toImmutableList()",
                ),
            ),
        ),
        evidenceAnchors = listOf(
            BoundAnchor(
                CONTENT_CATALOG_TEST_PATH,
                listOf(
                    "coreShapeUnlockPolicyIsCapturedInStableIdOrder",
                    "weaponCatalogAndCapturedMasteryPolicyAreOrderedAndQueryable",
                    "publishedCatalogCollectionsExposeNoMutationAuthorityAndCopyBootstrapInputs",
                ),
            ),
        ),
        closedEnumInventories = listOf(
            ClosedEnumInventory(
                path = CONTENT_IDS_PATH,
                declaration = "enum class CoreShape",
                expectedEntries = listOf("ORB", "PRISM", "SHARD"),
            ),
            ClosedEnumInventory(
                path = CONTENT_DEFINITIONS_PATH,
                declaration = "enum class WeaponMastery",
                expectedEntries = listOf("CALIBRATED", "AMPLIFIED", "RESONANT", "ASCENDED"),
            ),
        ),
        policyRow =
            "| `content.closed-ui-catalogs` | CoreShape 3 / WeaponMastery 4 | " +
                "bootstrap requires exact stable order |",
    ),
    MechanicallyDerivedBoundProjection(
        id = "ui.catalog-backed-sources",
        value = "Codex items <=400; Armory weapons <=12; Lab upgrades <=8; mastery closed",
        derivation =
            "Assembly passes validated immutable UiCatalog collections; Profile contributes only " +
                "bounded discovery state",
        sourceAnchors = listOf(
            BoundAnchor(
                APP_COMPOSITION_PATH,
                listOf(
                    "weapons = uiCatalog.weapons",
                    "weaponMasteries = uiCatalog.weaponMasteries",
                ),
            ),
            BoundAnchor(
                SESSION_CODEX_PATH,
                listOf(
                    "private val reducer = CodexReducer(uiCatalog.items)",
                    "profilePort.query(ProfileQuery.GetCollection)",
                ),
            ),
            BoundAnchor(
                PROFILE_ARMORY_FEATURE_PATH,
                listOf(
                    "private val weapons: ImmutableList<WeaponDefinition>",
                    "weaponMasteries: ImmutableList<WeaponMastery>",
                ),
            ),
            BoundAnchor(
                SESSION_HOME_PATH,
                listOf(
                    "coreShapes = uiCatalog.coreShapes",
                    "engine.coreShapes.forEachIndexed",
                ),
            ),
            BoundAnchor(
                PROFILE_LAB_STATE_PATH,
                listOf(
                    "upgrades = metaUpgrades.map { definition ->",
                    "    }.toImmutableList(),\n)",
                ),
            ),
        ),
        evidenceAnchors = listOf(
            BoundAnchor(
                PROFILE_LAB_TEST_PATH,
                "snapshotMapsEightOrderedUpgradesAndExactNextCost",
            ),
        ),
        policyRow =
            "| `ui.catalog-backed-sources` | Codex items <=400; Armory weapons <=12; " +
                "Lab upgrades <=8; mastery closed | validated immutable Content plus bounded Profile state |",
    ),
    MechanicallyDerivedBoundProjection(
        id = "audio.music-notes",
        value = "8",
        derivation = "fixed literal array indexed only by modulo its size",
        sourceAnchors = listOf(
            BoundAnchor(
                AUDIO_SERVICE_PATH,
                listOf(
                    "val MUSIC_NOTES = floatArrayOf(110f, 146.83f, 164.81f, 220f, 196f, 164.81f, 146.83f, 123.47f)",
                    "MUSIC_NOTES[musicStep % MUSIC_NOTES.size]",
                ),
            ),
        ),
        policyRow = "| `audio.music-notes` | 8 | fixed literal array with modulo-only indexing |",
    ),
    MechanicallyDerivedBoundProjection(
        id = "foundation.immutable-set-copy",
        value = "list = input cardinality; set <= input cardinality",
        derivation = "list owns elements.toList(); set appends only first occurrences before that list copy",
        sourceAnchors = listOf(
            BoundAnchor(
                FOUNDATION_COLLECTIONS_PATH,
                listOf(
                    "fun <Element> copyOf(elements: Iterable<Element>): ImmutableList<Element> =",
                    "ImmutableList(elements.toList())",
                    "val distinctElements = mutableListOf<Element>()",
                    "if (element !in distinctElements)",
                    "distinctElements += element",
                    "return ImmutableSet(ImmutableList.copyOf(distinctElements))",
                ),
            ),
        ),
        evidenceAnchors = listOf(
            BoundAnchor(
                FOUNDATION_COLLECTIONS_TEST_PATH,
                listOf("factoriesCopySourceStorage", "setIterationOrderAndSetEqualityAreStable"),
            ),
        ),
        policyRow =
            "| `foundation.immutable-set-copy` | list = input cardinality; set <= input cardinality | " +
                "owned list copy; first-occurrence-only set copy |",
    ),
).also { projections ->
    requireUniqueKeys("mechanicallyDerivedBounds", projections, MechanicallyDerivedBoundProjection::id)
    requireUniqueKeys(
        "mechanicallyDerivedBounds policy rows",
        projections,
        MechanicallyDerivedBoundProjection::policyRow,
    )
}

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
        appendLine("  \"cumulativeFanout\": {")
        appendLine("    \"acceptedCausalDepths\": \"0..7\",")
        appendLine("    \"asyncHandoff\": false,")
        appendLine("    \"branchUnit\": \"complete accepted source tuple (OutputId/semanticHandle only when materialized/triggered) -> effective route -> consumer/executor\",")
        appendLine("    \"maxConsumersPerOutput\": 1,")
        appendLine("    \"maxCumulativeFanout\": 9840,")
        appendLine("    \"maxOutputsPerDecision\": 3,")
        appendLine("    \"runtimeMeter\": false,")
        appendLine("    \"scope\": \"one accepted root causal scope\",")
        appendLine("    \"staticProof\": \"3^1 + 3^2 + ... + 3^8 = 9840\"")
        appendLine("  },")
        appendLine("  \"outputExecutors\": [")
        outputExecutorInventory.sortedBy(OutputExecutorProjection::id)
            .forEachIndexed { index, projection ->
                append(
                    "    {\"alternative\": \"${jsonEscape(projection.alternative ?: "always")}\", " +
                        "\"consumerOrExecutor\": \"${jsonEscape(projection.consumerOrExecutor)}\", " +
                        "\"effectiveRoute\": \"${jsonEscape(projection.effectiveRoute)}\", " +
                        "\"id\": \"${jsonEscape(projection.id)}\", " +
                        "\"mutualExclusionGroup\": " +
                        "\"${jsonEscape(projection.mutualExclusionGroup ?: "none")}\", " +
                        "\"outputVariant\": \"${jsonEscape(projection.outputVariant)}\", " +
                        "\"source\": \"${jsonEscape(projection.executorPath)}\"}",
                )
                appendLine(if (index == outputExecutorInventory.lastIndex) "" else ",")
            }
        appendLine("  ],")
        appendLine("  \"derivedBounds\": [")
        mechanicallyDerivedBounds.sortedBy(MechanicallyDerivedBoundProjection::id)
            .forEachIndexed { index, projection ->
                append("    {\"derivation\": \"${jsonEscape(projection.derivation)}\", ")
                append("\"closedInventories\": ")
                appendInlineJsonStringArray(
                    projection.closedEnumInventories.map { inventory ->
                        "${inventory.declaration}=${inventory.expectedEntries.joinToString(",")}"
                    }.sorted(),
                )
                append(", ")
                append("\"evidence\": ")
                appendInlineJsonStringArray(projection.evidenceAnchors.map(BoundAnchor::path).distinct().sorted())
                append(", \"id\": \"${jsonEscape(projection.id)}\", \"sources\": ")
                appendInlineJsonStringArray(projection.sourceAnchors.map(BoundAnchor::path).distinct().sorted())
                append(", \"value\": \"${jsonEscape(projection.value)}\"}")
                appendLine(if (index == mechanicallyDerivedBounds.lastIndex) "" else ",")
            }
        appendLine("  ],")
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
