// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle.pokeball

private const val PROFILE_RESOURCE_PATH =
    "ball/profile/resource/src/commonMain/kotlin/kinetickk/ball/profile/resource/ProfileStorage.kt"
private const val PROFILE_FACTORY_PATH =
    "ball/profile/impl/src/commonMain/kotlin/kinetickk/ball/profile/impl/ProfileComponentFactory.kt"
private const val AUDIO_RESOURCE_PATH =
    "resource/audio/impl/src/commonMain/kotlin/kinetickk/resource/audio/impl/DefaultAudioService.kt"
private const val APP_PLATFORM_EXPECT_PATH =
    "app/shared/src/commonMain/kotlin/kinetickk/app/shared/AppComposition.kt"
private const val DESKTOP_PLATFORM_BROKER_PATH =
    "app/shared/src/desktopMain/kotlin/kinetickk/app/shared/PlatformCapabilities.desktop.kt"
private const val WEB_PLATFORM_BROKER_PATH =
    "app/shared/src/wasmJsMain/kotlin/kinetickk/app/shared/PlatformCapabilities.wasm.kt"
private const val APP_ASSEMBLY_PATH =
    "app/shared/src/commonMain/kotlin/kinetickk/app/shared/AppComposition.kt"
private const val PROFILE_COMPONENT_PATH =
    "ball/profile/impl/src/commonMain/kotlin/kinetickk/ball/profile/impl/ProfileComponentFactory.kt"
private const val PROFILE_COMPONENT_IMPL_PATH =
    "ball/profile/impl/src/commonMain/kotlin/kinetickk/ball/profile/impl/DefaultProfileComponent.kt"
/**
 * Fail-closed proof for the accepted-output critical path. The digest covers the whole
 * comment-stripped, whitespace-canonical source so imports, literals, helper bodies, and name
 * resolution cannot drift around the structural dispatch checks without explicit re-review.
 */
private const val CANONICAL_PROFILE_COMPONENT_SEMANTIC_DIGEST =
    "d7c533766c44ade756069fc354c7bc607b3b7c74504d52cf8d0f1d4c5ec6306f"
private const val GAMEPLAY_COMPONENT_PATH =
    "ball/gameplay/impl/src/commonMain/kotlin/kinetickk/ball/gameplay/impl/GameplayCompositionComponent.kt"
private const val GAMEPLAY_COMPONENT_IMPL_PATH =
    "ball/gameplay/impl/src/commonMain/kotlin/kinetickk/ball/gameplay/impl/DefaultGameplayFeature.kt"
private const val GAMEPLAY_RUN_IMPL_PATH =
    "ball/gameplay/impl/src/commonMain/kotlin/kinetickk/ball/gameplay/impl/GameComponent.kt"
private const val SESSION_COMPONENT_PATH =
    "flow/session/impl/src/commonMain/kotlin/kinetickk/flow/session/impl/AppSessionComponent.kt"
private const val SESSION_COMPONENT_IMPL_PATH =
    "flow/session/impl/src/commonMain/kotlin/kinetickk/flow/session/impl/DefaultAppSessionComponent.kt"
private const val GAMEPLAY_FEATURE_PATH =
    "ball/gameplay/interaction/src/commonMain/kotlin/kinetickk/ball/gameplay/interaction/GameplayFeature.kt"
private const val SESSION_CONTENT_PATH =
    "flow/session/interaction/src/commonMain/kotlin/kinetickk/flow/session/interaction/AppSessionContent.kt"
private const val PROFILE_QUERY_SURFACE_PATH =
    "ball/profile/api/src/commonMain/kotlin/kinetickk/ball/profile/api/ProfileQueries.kt"
private const val GAMEPLAY_QUERY_SURFACE_PATH =
    "ball/gameplay/api/src/commonMain/kotlin/kinetickk/ball/gameplay/api/GameplayQueries.kt"

private data class RestrictedProductionType(
    val typeName: String,
    val allowedExactPaths: Set<String> = emptySet(),
    val allowedPathPrefixes: Set<String> = emptySet(),
)

private val restrictedProductionTypes = listOf(
    RestrictedProductionType(
        "ProfileComponent",
        setOf(PROFILE_COMPONENT_PATH, PROFILE_COMPONENT_IMPL_PATH, APP_ASSEMBLY_PATH),
    ),
    RestrictedProductionType(
        "GameplayCompositionComponent",
        setOf(GAMEPLAY_COMPONENT_PATH, GAMEPLAY_COMPONENT_IMPL_PATH, APP_ASSEMBLY_PATH),
    ),
    RestrictedProductionType(
        "ProfilePort",
        setOf(PROFILE_QUERY_SURFACE_PATH, PROFILE_COMPONENT_PATH, PROFILE_COMPONENT_IMPL_PATH, APP_ASSEMBLY_PATH),
        setOf("ball/profile/interaction/"),
    ),
    RestrictedProductionType(
        "ProfileReadPort",
        setOf(PROFILE_QUERY_SURFACE_PATH),
        setOf("flow/session/interaction/"),
    ),
    RestrictedProductionType(
        "SessionProfileRoute",
        setOf(
            PROFILE_QUERY_SURFACE_PATH,
            PROFILE_COMPONENT_PATH,
            PROFILE_COMPONENT_IMPL_PATH,
            SESSION_COMPONENT_PATH,
            SESSION_COMPONENT_IMPL_PATH,
        ),
    ),
    RestrictedProductionType(
        "GameplayProfileRoute",
        setOf(
            PROFILE_QUERY_SURFACE_PATH,
            PROFILE_COMPONENT_PATH,
            PROFILE_COMPONENT_IMPL_PATH,
            GAMEPLAY_COMPONENT_IMPL_PATH,
            GAMEPLAY_RUN_IMPL_PATH,
        ),
    ),
    RestrictedProductionType(
        "GameplaySessionRunPort",
        setOf(
            GAMEPLAY_QUERY_SURFACE_PATH,
            GAMEPLAY_FEATURE_PATH,
            GAMEPLAY_COMPONENT_IMPL_PATH,
            GAMEPLAY_RUN_IMPL_PATH,
            SESSION_COMPONENT_PATH,
            SESSION_COMPONENT_IMPL_PATH,
        ),
    ),
    RestrictedProductionType(
        "GameplaySessionHost",
        setOf(
            GAMEPLAY_FEATURE_PATH,
            GAMEPLAY_COMPONENT_PATH,
            GAMEPLAY_COMPONENT_IMPL_PATH,
            SESSION_COMPONENT_PATH,
            SESSION_COMPONENT_IMPL_PATH,
            APP_ASSEMBLY_PATH,
        ),
    ),
    RestrictedProductionType(
        "GameplayPresentationPort",
        setOf(
            GAMEPLAY_QUERY_SURFACE_PATH,
            GAMEPLAY_FEATURE_PATH,
            GAMEPLAY_COMPONENT_IMPL_PATH,
            GAMEPLAY_RUN_IMPL_PATH,
            SESSION_CONTENT_PATH,
        ),
    ),
    RestrictedProductionType(
        "GameplayPresentation",
        setOf(
            GAMEPLAY_FEATURE_PATH,
            GAMEPLAY_COMPONENT_PATH,
            GAMEPLAY_COMPONENT_IMPL_PATH,
            SESSION_CONTENT_PATH,
            APP_ASSEMBLY_PATH,
        ),
    ),
)

private data class TrustedNucleusCallsite(
    val token: String,
    val expectedCountsByPath: Map<String, Int>,
)

private val trustedNucleusCallsites = listOf(
    TrustedNucleusCallsite(
        "profileModuleResultPulse",
        mapOf(SESSION_DECISION_PATH to 1, SESSION_COMPONENT_IMPL_PATH to 1),
    ),
    TrustedNucleusCallsite(
        "gameplayModuleResultPulse",
        mapOf(SESSION_DECISION_PATH to 1, SESSION_COMPONENT_IMPL_PATH to 1),
    ),
    TrustedNucleusCallsite(
        "profileCommandRejectedBeforeAcceptance",
        mapOf(SESSION_DECISION_PATH to 1, SESSION_COMPONENT_IMPL_PATH to 1),
    ),
    TrustedNucleusCallsite(
        "gameplayCommandRejectedBeforeAcceptance",
        mapOf(SESSION_DECISION_PATH to 1, SESSION_COMPONENT_IMPL_PATH to 1),
    ),
    TrustedNucleusCallsite(
        "GameplayNucleusPulse.ProfileModuleResultPulse",
        mapOf(GAMEPLAY_RUN_IMPL_PATH to 1),
    ),
    TrustedNucleusCallsite(
        "GameplayNucleusPulse.ProfileCommandRejectedBeforeAcceptance",
        mapOf(GAMEPLAY_RUN_IMPL_PATH to 1),
    ),
)

private val platformBrokerPaths = setOf(
    DESKTOP_PLATFORM_BROKER_PATH,
    WEB_PLATFORM_BROKER_PATH,
)

private val profileResourceFaultBoundaryPaths = setOf(
    PROFILE_RESOURCE_PATH,
    PROFILE_COMPONENT_IMPL_PATH,
)

private fun String.isAuditedResourceBoundaryPath(): Boolean =
    contains("/resource/src/") ||
        startsWith("resource/") && contains("/impl/src/")

private val kotlinCatchBlock = Regex(
    "\\bcatch\\s*\\(\\s*([_A-Za-z][_A-Za-z0-9]*)\\s*:\\s*" +
        "([A-Za-z_][A-Za-z0-9_.]*)" +
        "\\s*\\)\\s*\\{",
)

private val broadRuntimeFaultTypes = setOf("Throwable", "Exception", "RuntimeException", "Error")

private val semanticProviderEvidenceConstruction = Regex(
    "\\b(?:[A-Za-z_][A-Za-z0-9_]*\\.)*(ResourceFailure|OutcomeUnknown)\\s*\\(",
)

private val broadPlatformAuthorityTokens = setOf(
    "java.util.prefs.Preferences",
    "org.w3c.dom.Storage",
    "kotlinx.browser.localStorage",
    "kotlinx.browser.sessionStorage",
    "kotlin.js.JsAny",
    "globalThis",
    "javax.sound.sampled.AudioSystem",
    "java.util.concurrent.ThreadPoolExecutor",
)

private val broadBrokerSignatureTypes = setOf(
    "Preferences",
    "Storage",
    "JsAny",
    "AudioSystem",
    "ThreadPoolExecutor",
)

private val exactPersistenceOperations = setOf(
    "readV4",
    "writeV4",
    "readLegacyProgressV2",
    "readLegacyMatter",
    "removeLegacyProgressV2",
    "removeLegacyMatter",
)

private val kotlinControlKeywords = setOf(
    "catch",
    "do",
    "else",
    "finally",
    "for",
    "if",
    "return",
    "throw",
    "try",
    "when",
    "while",
)

private val exactProfilePersistenceConstants = linkedMapOf(
    "DESKTOP_PROFILE_NODE" to "kinetickk/profile",
    "DESKTOP_SNAPSHOT_V4" to "snapshot_v4",
    "DESKTOP_LEGACY_NODE" to "kinetickk/progression",
    "DESKTOP_LEGACY_PROGRESS_V2" to "progress_v2",
    "DESKTOP_LEGACY_MATTER" to "kinetickk_matter",
    "WEB_SNAPSHOT_V4" to "kinetickk_profile_v4",
    "WEB_LEGACY_PROGRESS_V2" to "kinetickk_progress_v2",
    "WEB_LEGACY_MATTER" to "kinetickk_matter",
)

internal fun platformCapabilityBoundaryViolations(
    sources: Collection<SourceDocument>,
): List<String> = buildList {
    val production = sources.filter { source ->
        source.relativePath.endsWith(".kt") &&
            Regex("/src/[^/]+Main/").containsMatchIn(source.relativePath)
    }
    val codeByPath = production.associate { source ->
        source.relativePath to source.text.withoutKotlinComments()
    }
    production.forEach { source ->
        val code = codeByPath.getValue(source.relativePath)
        if (source.relativePath !in platformBrokerPaths) {
            broadPlatformAuthorityTokens.filter(code::contains).forEach { token ->
                add(
                    "Broad platform authority `$token` is forbidden outside the exact app " +
                        "platform broker: ${source.relativePath}",
                )
            }
            if (Regex("\\b(?:window|globalThis)\\s*(?:\\.|\\[)\\s*(?:localStorage|sessionStorage|AudioContext|webkitAudioContext)")
                    .containsMatchIn(code)
            ) {
                add("Ambient browser authority is forbidden outside the exact app platform broker: ${source.relativePath}")
            }
        } else {
            addAll(platformBrokerSourceViolations(source.relativePath, code))
        }
    }

    fun requireTokens(path: String, tokens: List<String>) {
        val text = codeByPath[path]
        tokens.filter { token -> text == null || token !in text }.forEach { token ->
            add("Restricted platform capability boundary is missing `$token` in $path")
        }
    }

    requireTokens(
        PROFILE_RESOURCE_PATH,
        listOf(
            "interface ExactProfilePersistence",
            "fun readV4(): ProfileProviderReadResult",
            "fun writeV4(payload: String): ProfileProviderMutationResult",
            "fun readLegacyProgressV2(): ProfileProviderReadResult",
            "fun readLegacyMatter(): ProfileProviderReadResult",
            "fun removeLegacyProgressV2(): ProfileProviderMutationResult",
            "fun removeLegacyMatter(): ProfileProviderMutationResult",
            "sealed interface ProfileProviderReadResult",
            "data class Observed(val payload: String?) : ProfileProviderReadResult",
            "data object Failed : ProfileProviderReadResult",
            "enum class ProfileProviderMutationResult",
            "COMPLETED",
            "FAILED_BEFORE_EXECUTION",
            "POSSIBLE_EXECUTION",
            "fun createProfileResource(",
            "persistence: ExactProfilePersistence",
            "private class FixedKeyProfileResource",
        ),
    )
    requireTokens(
        PROFILE_FACTORY_PATH,
        listOf(
            "interface ProfilePersistenceCapability",
            "fun readV4(): ProfilePersistenceReadResult",
            "fun writeV4(payload: String): ProfilePersistenceMutationResult",
            "fun readLegacyProgressV2(): ProfilePersistenceReadResult",
            "fun readLegacyMatter(): ProfilePersistenceReadResult",
            "fun removeLegacyProgressV2(): ProfilePersistenceMutationResult",
            "fun removeLegacyMatter(): ProfilePersistenceMutationResult",
            "sealed interface ProfilePersistenceReadResult",
            "data class Observed(val payload: String?) : ProfilePersistenceReadResult",
            "data object Failed : ProfilePersistenceReadResult",
            "enum class ProfilePersistenceMutationResult",
            "COMPLETED",
            "FAILED_BEFORE_EXECUTION",
            "POSSIBLE_EXECUTION",
            "private class ProfilePersistenceAdapter",
            ") : ExactProfilePersistence",
        ),
    )
    requireTokens(
        AUDIO_RESOURCE_PATH,
        listOf(
            "interface TonePlaybackCapability",
            "fun unlock()",
            "fun play(request: ToneRequest)",
            "fun close()",
            "class DefaultAudioService(",
            "private val platform: TonePlaybackCapability",
        ),
    )
    requireTokens(
        APP_PLATFORM_EXPECT_PATH,
        listOf(
            "expect fun createPlatformProfilePersistenceCapability(): ProfilePersistenceCapability",
            "expect fun createPlatformTonePlaybackCapability(): TonePlaybackCapability",
        ),
    )
    requireTokens(
        DESKTOP_PLATFORM_BROKER_PATH,
        listOf(
            "actual fun createPlatformProfilePersistenceCapability(): ProfilePersistenceCapability",
            "actual fun createPlatformTonePlaybackCapability(): TonePlaybackCapability",
            "Preferences.userRoot().node(ProfilePersistenceContract.DESKTOP_PROFILE_NODE)",
            "Preferences.userRoot().node(ProfilePersistenceContract.DESKTOP_LEGACY_NODE)",
            "AudioSystem.getSourceDataLine(format).use { line ->",
            "private class DesktopProfilePersistenceCapability",
            "private class DesktopTonePlaybackCapability",
        ),
    )
    requireTokens(
        WEB_PLATFORM_BROKER_PATH,
        listOf(
            "actual fun createPlatformProfilePersistenceCapability(): ProfilePersistenceCapability",
            "actual fun createPlatformTonePlaybackCapability(): TonePlaybackCapability",
            "WebProfilePersistenceCapability()",
            "private external interface WebStorageReadCall : JsAny",
            "webStorageRead(ProfilePersistenceContract.WEB_SNAPSHOT_V4).toPersistenceResult()",
            "webStorageWrite(ProfilePersistenceContract.WEB_SNAPSHOT_V4, payload)",
            "webStorageRead(ProfilePersistenceContract.WEB_LEGACY_PROGRESS_V2).toPersistenceResult()",
            "webStorageRead(ProfilePersistenceContract.WEB_LEGACY_MATTER).toPersistenceResult()",
            "webStorageRemove(ProfilePersistenceContract.WEB_LEGACY_PROGRESS_V2)",
            "webStorageRemove(ProfilePersistenceContract.WEB_LEGACY_MATTER)",
            "const exactStorage = globalThis.localStorage;",
            "private var context: JsAny? = null",
            "private class WebProfilePersistenceCapability",
            "private class WebTonePlaybackCapability",
            "webToneWaveValue(request.wave)",
            "internal fun webToneWaveValue(wave: ToneWave): String = when (wave)",
            "ToneWave.SINE -> \"sine\"",
            "ToneWave.SQUARE -> \"square\"",
            "ToneWave.SAW -> \"sawtooth\"",
            "ToneWave.TRIANGLE -> \"triangle\"",
            "oscillator.type = wave;",
        ),
    )

    addAll(exactProfilePersistenceContractViolations(codeByPath[PROFILE_FACTORY_PATH]))
    addAll(
        exactInterfaceOperationViolations(
            path = PROFILE_FACTORY_PATH,
            code = codeByPath[PROFILE_FACTORY_PATH],
            declaration = "interface ProfilePersistenceCapability",
            expectedOperations = exactPersistenceOperations,
        ),
    )
    addAll(
        exactInterfaceOperationViolations(
            path = PROFILE_RESOURCE_PATH,
            code = codeByPath[PROFILE_RESOURCE_PATH],
            declaration = "interface ExactProfilePersistence",
            expectedOperations = exactPersistenceOperations,
        ),
    )
    addAll(
        exactInterfaceOperationViolations(
            path = AUDIO_RESOURCE_PATH,
            code = codeByPath[AUDIO_RESOURCE_PATH],
            declaration = "interface TonePlaybackCapability",
            expectedOperations = setOf("unlock", "play", "close"),
        ),
    )
    addAll(
        exactEnumEntriesViolations(
            path = PROFILE_FACTORY_PATH,
            code = codeByPath[PROFILE_FACTORY_PATH],
            declaration = "enum class ProfilePersistenceMutationResult",
            expectedEntries = listOf("COMPLETED", "FAILED_BEFORE_EXECUTION", "POSSIBLE_EXECUTION"),
        ),
    )
    addAll(
        exactEnumEntriesViolations(
            path = PROFILE_RESOURCE_PATH,
            code = codeByPath[PROFILE_RESOURCE_PATH],
            declaration = "enum class ProfileProviderMutationResult",
            expectedEntries = listOf("COMPLETED", "FAILED_BEFORE_EXECUTION", "POSSIBLE_EXECUTION"),
        ),
    )
    addAll(platformFactoryCallsiteViolations(production, codeByPath))
    addAll(closedCapabilityTypeInventoryViolations(production, codeByPath))
    addAll(desktopPersistenceSeamCallsiteViolations(production, codeByPath))

    production.filter { source ->
        source.relativePath.startsWith("ball/profile/resource/") ||
            source.relativePath.startsWith("resource/audio/impl/")
    }.forEach { source ->
        listOf("createPlatformProfileResource", "createPlatformTonePlayer", " expect fun ")
            .filter(codeByPath.getValue(source.relativePath)::contains)
            .forEach { token ->
                add("Resource may not acquire a platform capability via `$token`: ${source.relativePath}")
            }
    }
}.distinct().sorted()

/** Enforces Core §6.13 fault-stage separation at audited production boundaries. */
internal fun resourceFaultStageViolations(
    sources: Collection<SourceDocument>,
): List<String> = resourceFaultStageViolations(sources, requireCanonicalProfileDispatches = true)

internal fun resourceFaultStageFixtureViolations(
    sources: Collection<SourceDocument>,
): List<String> = resourceFaultStageViolations(sources, requireCanonicalProfileDispatches = false)

private fun resourceFaultStageViolations(
    sources: Collection<SourceDocument>,
    requireCanonicalProfileDispatches: Boolean,
): List<String> = buildList {
    sources.asSequence()
        .filter { source ->
            source.relativePath.endsWith(".kt") &&
                source.relativePath.substringAfter("/src/", missingDelimiterValue = "")
                    .substringBefore('/')
                    .let { sourceSet -> sourceSet == "main" || sourceSet.endsWith("Main") }
        }
        .forEach { source ->
            val code = source.text.withoutKotlinComments()
            val kotlinCode = source.text.maskKotlinNonCode()
            val structuralCode = kotlinCode.maskEscapedIdentifierBodies()
            val auditedResourceBoundary =
                source.relativePath in profileResourceFaultBoundaryPaths ||
                    source.relativePath.isAuditedResourceBoundaryPath()
            val protectedBoundary =
                auditedResourceBoundary ||
                    source.relativePath in platformBrokerPaths
            val parsedCatches = kotlinCatchBlocks(kotlinCode)
            if (typedCatchSignatureCount(code, kotlinCode) != parsedCatches.size) {
                add(
                    "Core §6.13 fault-stage violation in ${source.relativePath}: every Kotlin catch " +
                        "at an audited boundary must use an explicit unescaped parameter and type",
                )
            }
            runtimeFaultAliasViolations(kotlinCode).forEach { alias ->
                add(
                    "Core §6.13 fault-stage violation in ${source.relativePath}: broad runtime " +
                        "fault type alias `$alias` is forbidden at production boundaries",
                )
            }
            if (source.relativePath == PROFILE_COMPONENT_IMPL_PATH) {
                profileDeferredOutputDrainViolations(
                    code = structuralCode,
                    rawCode = code,
                    requireCanonicalFunctions = requireCanonicalProfileDispatches,
                ).forEach(::add)
            }
            broadRuntimeFaultCatchBlocks(kotlinCode).forEach { caught ->
                val evidence = semanticProviderEvidenceConstruction.find(caught.body)?.groupValues?.get(1)
                val requiresDeferredDrain = source.relativePath == PROFILE_COMPONENT_IMPL_PATH &&
                    enclosingAcceptedOutputBatch(structuralCode, caught.declarationStart) != null
                val rethrowsSameFault = caught.parameter != "_" && if (requiresDeferredDrain) {
                    preservesDeferredFaultUntilRethrow(structuralCode, code, caught)
                } else {
                    directlyRethrowsCaughtFault(caught)
                }
                if (evidence != null || protectedBoundary && !rethrowsSameFault) {
                    val reason = if (evidence == null) {
                        "swallows or reclassifies the runtime fault"
                    } else {
                        "constructs semantic provider evidence `$evidence`"
                    }
                    add(
                        "Core §6.13 fault-stage violation at ${source.relativePath}:${caught.line}: " +
                            "broad `${caught.type}` catch $reason; use explicit closed provider outcomes " +
                            "at the platform capability boundary and rethrow programming faults",
                    )
                }
            }
            if (auditedResourceBoundary && Regex("\\brunCatching\\s*\\{").containsMatchIn(code)) {
                add(
                    "Core §6.13 fault-stage violation in ${source.relativePath}: `runCatching` " +
                        "implicitly catches every runtime fault at an audited Resource boundary; " +
                        "consume explicit closed provider outcomes instead",
                )
            }
            if (source.relativePath == WEB_PLATFORM_BROKER_PATH) {
                parsedCatches
                    .filter { caught -> caught.type.substringAfterLast('.') == "JsException" }
                    .forEach { caught ->
                        val rethrowsSameFault = caught.parameter != "_" &&
                            Regex("\\bthrow\\s+${Regex.escape(caught.parameter)}\\b")
                                .containsMatchIn(caught.body)
                        val typedReadFailure = "ProfilePersistenceReadResult.Failed" in caught.body
                        val typedMutationFailure =
                            "ProfilePersistenceMutationResult." in caught.body
                        val classifiesRead = caught.parameter != "_" &&
                            "isWebStorageReadFailure(${caught.parameter}.thrownValue)" in caught.body
                        val classifiesMutation = caught.parameter != "_" &&
                            "isWebStorageMutationFailure(${caught.parameter}.thrownValue)" in caught.body
                        if (!rethrowsSameFault || typedReadFailure && !classifiesRead ||
                            typedMutationFailure && !classifiesMutation
                        ) {
                            add(
                                "Core §6.13 fault-stage violation at ${source.relativePath}:${caught.line}: " +
                                    "`JsException` must classify the exact DOM storage failure and rethrow " +
                                    "all other JavaScript/programming faults",
                            )
                        }
                    }
                if ("webStorageRead" in code || "globalThis.localStorage" in code) {
                    addAll(webInlineStorageFaultStageViolations(code))
                }
            }
            if (source.relativePath == DESKTOP_PLATFORM_BROKER_PATH &&
                "ProfilePersistenceMutationResult" in code
            ) {
                val knownPreExecutionCatches = parsedCatches.filter { caught ->
                    caught.type.substringAfterLast('.') == "IllegalArgumentException"
                }
                if (knownPreExecutionCatches.isEmpty()) {
                    add(
                        "Core §6.13 fault-stage violation in ${source.relativePath}: desktop persistence " +
                            "must classify `IllegalArgumentException` as known before execution",
                    )
                }
                knownPreExecutionCatches.forEach { caught ->
                    if ("ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION" !in caught.body ||
                        "ProfilePersistenceMutationResult.POSSIBLE_EXECUTION" in caught.body
                    ) {
                        add(
                            "Core §6.13 fault-stage violation at ${source.relativePath}:${caught.line}: " +
                                "`IllegalArgumentException` is known before provider execution and must map " +
                                "only to `FAILED_BEFORE_EXECUTION`",
                        )
                    }
                }
            }
            if (source.relativePath == PROFILE_RESOURCE_PATH) {
                knownPreExecutionMutationBranches(code).forEach { branch ->
                    if ("OutcomeUnknown" in branch || "writeOutcomeUnknown" in branch) {
                        add(
                            "Core §6.13 fault-stage violation in ${source.relativePath}: " +
                                "`FAILED_BEFORE_EXECUTION` cannot map to `OutcomeUnknown`",
                        )
                    }
                }
            }
        }
}.distinct().sorted()

/** Keeps Core §9.13 live Audio projection faults on runtime-fault policy at every deployed layer. */
internal fun audioRuntimeFaultStageViolations(
    sources: Collection<SourceDocument>,
): List<String> = buildList {
    val production = sources.filter { source ->
        source.relativePath.endsWith(".kt") &&
            Regex("/src/[^/]+Main/").containsMatchIn(source.relativePath)
    }
    val codeByPath = production.associate { source ->
        source.relativePath to source.text.withoutKotlinComments()
    }
    val requiredPaths = listOf(
        AUDIO_RESOURCE_PATH,
        GAMEPLAY_RUN_IMPL_PATH,
        DESKTOP_PLATFORM_BROKER_PATH,
        WEB_PLATFORM_BROKER_PATH,
    )
    requiredPaths.filterNot(codeByPath::containsKey).forEach { path ->
        add("Core §9.13 Audio live-Projection fault-stage source is missing $path")
    }

    val forbiddenSemanticAudioType = Regex(
        "\\b(?:Audio|Tone|Playback)[A-Za-z0-9_]*(?:Fact|Result|Status)\\b|" +
            "\\b(?:Fact|Result|Status)[A-Za-z0-9_]*(?:Audio|Tone|Playback)\\b",
    )
    production.forEach { source ->
        forbiddenSemanticAudioType.findAll(source.text.withoutKotlinComments())
            .map { match -> match.value }
            .distinct()
            .forEach { typeName ->
                add(
                    "Core §9.13 Audio is a live mechanical Projection and may not introduce " +
                        "typed Fact/result/status `$typeName` in ${source.relativePath}",
                )
            }
    }

    fun verifyDirectScope(path: String, label: String, scope: String?, requiredTokens: List<String>) {
        if (scope == null) {
            add("Core §9.13 Audio live-Projection fault-stage scope `$label` is missing in $path")
            return
        }
        val normalized = scope.squashWhitespace()
        requiredTokens.filterNot(normalized::contains).forEach { token ->
            add(
                "Core §9.13 Audio live-Projection fault-stage scope `$label` in $path " +
                    "is missing direct call `$token`",
            )
        }
        if (Regex("\\brunCatching\\s*\\{").containsMatchIn(scope)) {
            add(
                "Core §9.13 Audio live-Projection fault-stage violation in $path `$label`: `runCatching` " +
                    "must not swallow synchronous runtime/provider faults",
            )
        }
        kotlinCatchBlocks(scope).forEach { caught ->
            add(
                "Core §9.13 Audio live-Projection fault-stage violation at $path:${caught.line} `$label`: " +
                    "synchronous `${caught.type}` catch must not replace runtime-fault propagation",
            )
        }
    }

    codeByPath[AUDIO_RESOURCE_PATH]?.let { code ->
        verifyDirectScope(
            AUDIO_RESOURCE_PATH,
            "Audio Resource capability calls",
            code,
            listOf(
                "if (!closed) platform.unlock()",
                "platform.close()",
                "platform.playIfAllowed(request.copy(gain = request.gain * volume))",
                "play(request)",
            ),
        )
    }

    codeByPath[GAMEPLAY_RUN_IMPL_PATH]?.let { code ->
        verifyDirectScope(
            GAMEPLAY_RUN_IMPL_PATH,
            "Gameplay output audio branches",
            sameIndentFunctionSlice(code, "private fun execute(output: GameplayOutput"),
            listOf(
                "is GameplayOutput.AdvanceAudio -> " +
                    "audioExecutor.advance(output.realDeltaSeconds, output.cues)",
                "GameplayOutput.EnsureAudioUnlocked -> audioExecutor.ensureUnlocked()",
            ),
        )
    }

    codeByPath[DESKTOP_PLATFORM_BROKER_PATH]?.let { code ->
        verifyDirectScope(
            DESKTOP_PLATFORM_BROKER_PATH,
            "Desktop Tone submission/synthesis/close",
            declarationBodyForCapability(code, "private class DesktopTonePlaybackCapability"),
            listOf(
                "executor.execute { synthesize(request) }",
                "executor.shutdownNow()",
                "AudioSystem.getSourceDataLine(format).use { line ->",
                "line.write(bytes, 0, bytes.size)",
            ),
        )
    }

    codeByPath[WEB_PLATFORM_BROKER_PATH]?.let { code ->
        verifyDirectScope(
            WEB_PLATFORM_BROKER_PATH,
            "Web Tone Kotlin wrappers",
            declarationBodyForCapability(code, "private class WebTonePlaybackCapability"),
            listOf(
                "context = unlockWebAudio(context)",
                "context = playWebTone(",
                "closeWebAudio(context)",
                "context = null",
            ),
        )
        val helperNames = listOf("unlockWebAudio", "playWebTone", "closeWebAudio")
        val helperBodies = helperNames.associateWith { name ->
            sameIndentFunctionSlice(code, "private fun $name")
        }
        helperBodies.forEach { (name, body) ->
            if (body == null) {
                add("Core §9.13 Web Audio helper `$name` is missing in $WEB_PLATFORM_BROKER_PATH")
            } else if (Regex("(?<!\\.)\\bcatch\\s*(?:\\([^)]*\\))?\\s*\\{").containsMatchIn(body)) {
                add(
                    "Core §9.13 Web Audio helper `$name` must let synchronous JavaScript faults " +
                        "propagate; only detached Promise rejection sinks are allowed",
                )
            }
        }
        val requiredHelperTokens = mapOf(
            "unlockWebAudio" to listOf(
                "const context = current || new AudioContext();",
                "const resume = context.resume();",
                "resume.catch(() => undefined);",
            ),
            "playWebTone" to listOf(
                "const context = current || new AudioContext();",
                "const resume = context.resume();",
                "const oscillator = context.createOscillator();",
                "const gain = context.createGain();",
                "oscillator.connect(gain);",
                "gain.connect(context.destination);",
                "oscillator.start();",
                "oscillator.stop(context.currentTime + duration + 0.015);",
                "resume.catch(() => undefined);",
            ),
            "closeWebAudio" to listOf(
                "const close = current.close();",
                "close.catch(() => undefined);",
            ),
        )
        requiredHelperTokens.forEach { (name, tokens) ->
            val helper = helperBodies[name].orEmpty()
            tokens.filterNot(helper::contains).forEach { token ->
                add(
                    "Core §9.13 Web Audio helper `$name` is missing exact synchronous-call or " +
                        "Promise-sink token `$token`",
                )
            }
        }
        val helperText = helperBodies.values.filterNotNull().joinToString("\n")
        val promiseSink = ".catch(() => undefined);"
        val sinkCount = helperText.windowed(promiseSink.length).count { candidate -> candidate == promiseSink }
        if (sinkCount != 3 ||
            "resume.catch(() => undefined);" !in helperText ||
            "close.catch(() => undefined);" !in helperText
        ) {
            add(
                "Web Audio detached resume/close Promise rejections must use exactly three " +
                    "mechanical best-effort sinks; found $sinkCount",
            )
        }
    }

    val evidenceByPath = sources.associate { source -> source.relativePath to source.text }
    audioRuntimeFaultEvidenceAnchors.forEach { anchor ->
        val evidence = evidenceByPath[anchor.path]
        if (evidence == null) {
            add("Core §9.13 Audio live-Projection evidence is missing ${anchor.path}")
        } else {
            anchor.tokens.filterNot(evidence::contains).forEach { token ->
                add("Core §9.13 Audio live-Projection evidence ${anchor.path} is missing `$token`")
            }
        }
    }
}.distinct().sorted()

private val audioRuntimeFaultEvidenceAnchors = listOf(
    BoundAnchor(
        path = "resource/audio/impl/src/commonTest/kotlin/kinetickk/resource/audio/impl/DefaultAudioServiceTest.kt",
        tokens = listOf("capabilityFaultsPropagateForUnlockPlayAndCloseWithoutInventingClosedState"),
    ),
    BoundAnchor(
        path = "ball/gameplay/impl/src/commonTest/kotlin/kinetickk/ball/gameplay/impl/GameComponentTest.kt",
        tokens = listOf("audioFaultsPropagateAfterAcceptedFramesCommitAndDrainExactResults"),
    ),
    BoundAnchor(
        path = "app/shared/src/desktopTest/kotlin/kinetickk/app/shared/PlatformCapabilitiesDesktopTest.kt",
        tokens = listOf(
            "audioBrokerIsInstanceOwnedAndCloseIsIdempotent",
            "workerAndDiscardOldestQueueEnforceOneAndTwentyFour",
            "synthesisBufferAcceptsMaximumDurationAndRejectsNext",
        ),
    ),
    BoundAnchor(
        path = "app/shared/src/wasmJsTest/kotlin/kinetickk/app/shared/PlatformCapabilitiesWebTest.kt",
        tokens = listOf("webAudioSynchronousProviderFaultsPropagateWithoutFabricatingClosedState"),
    ),
)

internal fun audioProjectionPolicyViolations(
    policy: String,
    applicability: String,
): List<String> = buildList {
    listOf(
        "Core §9.13 live mechanical Projection",
        "Audio produces no typed Fact, result, or status",
        "Synchronous Audio Resource and platform calls propagate under runtime-fault policy",
        "a synthesis fault escapes that `Runnable` to the runtime",
        "no caller-propagation claim",
        "`.catch(() => undefined)`",
        "post-acceptance mechanical projection loss",
        "synchronous JavaScript invocation and graph faults still propagate",
    ).filterNot(policy::contains).forEach { token ->
        add("Core §9.13 Audio projection policy is missing exact contract `$token`")
    }
    listOf(
        "live mechanical Audio Projection (Core §9.13)",
        "no typed Audio Fact/result/status",
        "Desktop worker faults escape the detached `Runnable` to runtime",
        "Web native `resume()`/`close()` Promise rejections",
        "`.catch(() => undefined)`",
        "Synchronous JavaScript invocation/graph faults propagate",
    ).filterNot(applicability::contains).forEach { token ->
        add("Core §9.13 Audio applicability inventory is missing exact contract `$token`")
    }
}.distinct().sorted()

internal fun leastAuthorityCompositionViolations(
    sources: Collection<SourceDocument>,
): List<String> = buildList {
    val production = sources.filter { source ->
        source.relativePath.endsWith(".kt") &&
            Regex("/src/[^/]+Main/").containsMatchIn(source.relativePath)
    }
    restrictedProductionTypes.forEach { restriction ->
        val token = Regex("\\b${Regex.escape(restriction.typeName)}\\b")
        production.filter { source -> token.containsMatchIn(source.text.withoutKotlinComments()) }
            .filterNot { source ->
                source.relativePath in restriction.allowedExactPaths ||
                    restriction.allowedPathPrefixes.any(source.relativePath::startsWith)
            }
            .forEach { source ->
                add(
                    "Least-authority type `${restriction.typeName}` leaks into production source " +
                        source.relativePath,
                )
            }
    }

    val assembly = production.singleOrNull { source -> source.relativePath == APP_ASSEMBLY_PATH }
    if (assembly == null) {
        add("Least-authority composition is missing static Assembly $APP_ASSEMBLY_PATH")
    } else {
        val code = assembly.text.withoutKotlinComments()
        listOf(
            "import kinetickk.ball.profile.impl.ProfileComponent",
            "profileComponent: ProfileComponent? = null",
            "private val profileComponent: ProfileComponent =",
            "import kinetickk.ball.gameplay.impl.GameplayCompositionComponent",
            "gameplayComponent: GameplayCompositionComponent? = null",
            "private val gameplayComponent: GameplayCompositionComponent =",
            "profileRoute = this.profileComponent",
            "gameplaySessionHost = this.gameplayComponent",
            "gameplayPresentation = gameplayComponent",
        ).filterNot(code::contains).forEach { token ->
            add("Static Assembly is missing least-authority binding `$token`")
        }
    }
}.distinct().sorted()

internal fun trustedNucleusInputCallsiteViolations(
    sources: Collection<SourceDocument>,
): List<String> = buildList {
    val production = sources.filter { source ->
        source.relativePath.endsWith(".kt") &&
            Regex("/src/[^/]+Main/").containsMatchIn(source.relativePath)
    }
    trustedNucleusCallsites.forEach { callsite ->
        val call = Regex("\\b${Regex.escape(callsite.token)}\\s*\\(")
        production.forEach { source ->
            val actual = call.findAll(source.text.withoutKotlinComments()).count()
            val expected = callsite.expectedCountsByPath[source.relativePath] ?: 0
            if (actual != expected) {
                add(
                    "Trusted Nucleus input `${callsite.token}` must occur exactly $expected times in " +
                        "${source.relativePath}; found $actual",
                )
            }
        }
        callsite.expectedCountsByPath.keys.filter { expectedPath ->
            production.none { source -> source.relativePath == expectedPath }
        }.forEach { missingPath ->
            add("Trusted Nucleus input `${callsite.token}` is missing expected source $missingPath")
        }
    }
}.distinct().sorted()

private fun platformBrokerSourceViolations(path: String, code: String): List<String> = buildList {
    declarationHeaders(code).forEach { header ->
        val broadTypes = broadBrokerSignatureTypes.filter { type ->
            Regex("\\b${Regex.escape(type)}\\b").containsMatchIn(header)
        }
        if (broadTypes.isNotEmpty() && !Regex("\\bprivate\\b").containsMatchIn(header)) {
            add(
                "Broad platform handle ${broadTypes.sorted().joinToString()} must remain in a private broker " +
                    "declaration in $path: `${header.squashWhitespace()}`",
            )
        }
    }

    listOf("clear", "removeNode", "childrenNames", "systemRoot").forEach { operation ->
        if (Regex("(?:\\.|\\bPreferences\\.)\\s*${Regex.escape(operation)}\\s*\\(").containsMatchIn(code)) {
            add("Broad platform operation `$operation` is forbidden in the exact app broker: $path")
        }
    }
    if (path != DESKTOP_PLATFORM_BROKER_PATH &&
        Regex("(?:\\.|\\bPreferences\\.)\\s*keys\\s*\\(").containsMatchIn(code)
    ) {
        add("Broad platform operation `keys` is forbidden in the exact app broker: $path")
    }

    when (path) {
        DESKTOP_PLATFORM_BROKER_PATH -> addAll(desktopBrokerSourceViolations(code))
        WEB_PLATFORM_BROKER_PATH -> addAll(webBrokerSourceViolations(code))
        else -> add("Unrecognized platform broker path $path")
    }
}

private fun desktopBrokerSourceViolations(code: String): List<String> = buildList {
    requireWordCount(code, "Preferences", 6, DESKTOP_PLATFORM_BROKER_PATH, this)
    requireWordCount(code, "AudioSystem", 2, DESKTOP_PLATFORM_BROKER_PATH, this)
    requireWordCount(code, "ThreadPoolExecutor", 3, DESKTOP_PLATFORM_BROKER_PATH, this)
    requireRegexCount(
        code,
        Regex(
            "Preferences\\.userRoot\\(\\)\\.node\\(\\s*" +
                "ProfilePersistenceContract\\.DESKTOP_PROFILE_NODE\\s*\\)",
        ),
        1,
        "fixed desktop profile node acquisition",
        DESKTOP_PLATFORM_BROKER_PATH,
        this,
    )
    requireRegexCount(
        code,
        Regex(
            "actual\\s+fun\\s+createPlatformProfilePersistenceCapability\\s*\\(\\s*\\)\\s*:\\s*" +
                "ProfilePersistenceCapability\\s*=\\s*DesktopProfilePersistenceCapability\\s*\\(",
        ),
        1,
        "direct desktop profile broker construction",
        DESKTOP_PLATFORM_BROKER_PATH,
        this,
    )
    requireRegexCount(
        code,
        Regex(
            "actual\\s+fun\\s+createPlatformTonePlaybackCapability\\s*\\(\\s*\\)\\s*:\\s*" +
                "TonePlaybackCapability\\s*=\\s*DesktopTonePlaybackCapability\\s*\\(\\s*\\)",
        ),
        1,
        "direct desktop audio broker construction",
        DESKTOP_PLATFORM_BROKER_PATH,
        this,
    )
    requireRegexCount(
        code,
        Regex(
            "Preferences\\.userRoot\\(\\)\\.node\\(\\s*" +
                "ProfilePersistenceContract\\.DESKTOP_LEGACY_NODE\\s*\\)",
        ),
        1,
        "fixed desktop legacy node acquisition",
        DESKTOP_PLATFORM_BROKER_PATH,
        this,
    )
    requireRegexCount(
        code,
        Regex("AudioSystem\\.getSourceDataLine\\(format\\)\\.use\\s*\\{\\s*line\\s*->"),
        1,
        "private desktop audio-line acquisition",
        DESKTOP_PLATFORM_BROKER_PATH,
        this,
    )
    requireRegexCount(
        code,
        Regex("\\bThreadPoolExecutor\\s*\\("),
        1,
        "private desktop executor construction",
        DESKTOP_PLATFORM_BROKER_PATH,
        this,
    )
    if (Regex("\\bfun\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\([^)]*\\)\\s*:\\s*ThreadPoolExecutor\\b")
            .containsMatchIn(code)
    ) {
        add("Desktop broker may not return a broad ThreadPoolExecutor handle")
    }
    addAll(
        closedPersistenceBrokerViolations(
            path = DESKTOP_PLATFORM_BROKER_PATH,
            code = code,
            declaration = "private class DesktopProfilePersistenceCapability",
            allowedCalls = exactPersistenceOperations +
                setOf(
                    "profileNode",
                    "legacyNode",
                    "desktopProfileReadCall",
                    "desktopProfileMutationCall",
                    "desktopProfilePayloadAdmission",
                    "let",
                    "get",
                    "put",
                    "remove",
                ),
            exactCallCounts = mapOf(
                "profileNode" to 2,
                "legacyNode" to 4,
                "desktopProfileReadCall" to 3,
                "desktopProfileMutationCall" to 3,
                "desktopProfilePayloadAdmission" to 1,
                "let" to 1,
                "get" to 3,
                "put" to 1,
                "remove" to 2,
            ),
            exactIdentifierCounts = mapOf(
                "profileNode" to 2,
                "legacyNode" to 4,
                "keys" to 3,
                "flush" to 6,
            ),
            requiredExpressions = listOf(
                "desktopProfilePayloadAdmission(payload.length)?.let { return it }",
                "exactKey = ProfilePersistenceContract.DESKTOP_SNAPSHOT_V4, " +
                    "loadKeyNames = node::keys, loadExactValue = { " +
                    "node.get(ProfilePersistenceContract.DESKTOP_SNAPSHOT_V4, null) },",
                "exactKey = ProfilePersistenceContract.DESKTOP_LEGACY_PROGRESS_V2, " +
                    "loadKeyNames = node::keys, loadExactValue = { " +
                    "node.get(ProfilePersistenceContract.DESKTOP_LEGACY_PROGRESS_V2, null) },",
                "exactKey = ProfilePersistenceContract.DESKTOP_LEGACY_MATTER, " +
                    "loadKeyNames = node::keys, loadExactValue = { " +
                    "node.get(ProfilePersistenceContract.DESKTOP_LEGACY_MATTER, null) },",
                "mutate = { node.put(ProfilePersistenceContract.DESKTOP_SNAPSHOT_V4, payload) }, " +
                    "flush = node::flush,",
                "mutate = { node.remove(ProfilePersistenceContract.DESKTOP_LEGACY_PROGRESS_V2) }, " +
                    "flush = node::flush,",
                "mutate = { node.remove(ProfilePersistenceContract.DESKTOP_LEGACY_MATTER) }, " +
                    "flush = node::flush,",
            ),
        ),
    )
    addAll(desktopExactReadHelperViolations(code))
    addAll(desktopMutationStageViolations(code))
    addAll(desktopValueAdmissionViolations(code))
    val audioBody = declarationBodyForCapability(code, "private class DesktopTonePlaybackCapability")
    if (audioBody == null) {
        add("Private desktop audio broker is missing")
    } else {
        requireWordCount(audioBody, "executor", 4, DESKTOP_PLATFORM_BROKER_PATH, this)
        requireWordCount(audioBody, "line", 5, DESKTOP_PLATFORM_BROKER_PATH, this)
        if (Regex("\\bthis\\b").containsMatchIn(audioBody)) {
            add("Desktop audio broker may not publish its capability instance via `this`")
        }
    }
}

private fun desktopExactReadHelperViolations(code: String): List<String> = buildList {
    val declaration = "internal fun desktopProfileReadCall"
    val declarationIndex = code.indexOf(declaration)
    if (declarationIndex < 0) {
        add("Narrow Desktop profile read seam is missing")
        return@buildList
    }
    val nextDeclaration = Regex(
        "(?m)^(?:private|internal|public)\\s+(?:class|object|interface|fun|val|var)\\b",
    ).find(code, declarationIndex + declaration.length)?.range?.first ?: code.length
    val body = code.substring(declarationIndex, nextDeclaration)
    listOf(
        "exactKey: String",
        "loadKeyNames: () -> Array<String>",
        "loadExactValue: () -> String?",
        "): ProfilePersistenceReadResult",
        "val storedKeys = try",
        "loadKeyNames()",
        "desktopPreferenceKeyCountAdmission(storedKeys.size)",
        "storedKeys.any { storedKey -> storedKey == exactKey }",
        "loadExactValue()",
        "ProfilePersistenceReadResult.Observed(null)",
        "ProfilePersistenceReadResult.Failed",
    ).filterNot(code::contains).forEach { token ->
        add("Narrow Desktop profile read seam is missing `$token`")
    }
    val keysIndex = body.indexOf("loadKeyNames()")
    val admissionIndex = body.indexOf("desktopPreferenceKeyCountAdmission(storedKeys.size)")
    val iterationIndex = body.indexOf("storedKeys.any { storedKey -> storedKey == exactKey }")
    val loadExactValueIndex = body.indexOf("loadExactValue()")
    if (keysIndex < 0 || admissionIndex <= keysIndex || iterationIndex <= admissionIndex) {
        add(
            "Desktop preference key results must be admitted immediately after callback acquisition " +
                "and before project-owned membership iteration",
        )
    }
    if (loadExactValueIndex <= iterationIndex) {
        add("Desktop exact value load must occur only after admitted exact-key membership")
    }
    val allKeyReferences = Regex("\\bnode::keys\\b").findAll(code).count()
    if (allKeyReferences != 3 || "node::keys" in body) {
        add(
            "Desktop Preferences key enumeration must enter only through the three private fixed-key " +
                "broker bindings; found $allKeyReferences `node::keys` references",
        )
    }
    val directKeyCalls = Regex("\\.keys\\s*\\(").findAll(code).count()
    if (directKeyCalls != 0) {
        add(
            "Direct Desktop Preferences `keys()` calls are forbidden outside the three " +
                "private fixed-key callback bindings; found $directKeyCalls",
        )
    }
    if (Regex("\\breturn\\s+(?:loadKeyNames|storedKeys)\\b").containsMatchIn(body)) {
        add("Desktop preference key enumeration may not escape the narrow read seam")
    }
    val catches = kotlinCatchBlocks(body)
    val expectedCatchCounts = mapOf(
        "BackingStoreException" to 1,
        "SecurityException" to 2,
        "IllegalStateException" to 2,
    )
    val actualCatchCounts = catches.groupingBy { caught -> caught.type.substringAfterLast('.') }.eachCount()
    if (actualCatchCounts != expectedCatchCounts) {
        add(
            "Desktop narrow read seam catch inventory must be exactly " +
                "${expectedCatchCounts.toSortedMap()}; found ${actualCatchCounts.toSortedMap()}",
        )
    }
    catches.filter { caught -> "ProfilePersistenceReadResult.Failed" !in caught.body }
        .forEach { caught ->
            add("Desktop narrow read seam `${caught.type}` catch must return only the closed Failed outcome")
        }
}

private fun desktopMutationStageViolations(code: String): List<String> = buildList {
    val body = topLevelDeclarationSlice(code, "internal fun desktopProfileMutationCall")
    if (body == null) {
        add("Narrow Desktop profile mutation seam is missing")
        return@buildList
    }
    listOf(
        "mutate: () -> Unit",
        "flush: () -> Unit",
        "mutate()",
        "flush()",
        "ProfilePersistenceMutationResult.COMPLETED",
        "ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION",
        "ProfilePersistenceMutationResult.POSSIBLE_EXECUTION",
    ).filterNot(body::contains).forEach { token ->
        add("Narrow Desktop profile mutation seam is missing `$token`")
    }
    val mutateIndex = body.indexOf("mutate()")
    val flushIndex = body.indexOf("flush()")
    val completedIndex = body.indexOf("ProfilePersistenceMutationResult.COMPLETED")
    if (mutateIndex < 0 || flushIndex <= mutateIndex || completedIndex <= flushIndex) {
        add("Desktop mutation seam must mutate, then flush, then report COMPLETED")
    }
    val catches = kotlinCatchBlocks(body)
    val expectedCatchCounts = mapOf(
        "IllegalStateException" to 1,
        "IllegalArgumentException" to 1,
        "BackingStoreException" to 1,
    )
    val actualCatchCounts = catches.groupingBy { caught -> caught.type.substringAfterLast('.') }.eachCount()
    if (actualCatchCounts != expectedCatchCounts) {
        add(
            "Desktop mutation seam catch inventory must be exactly " +
                "${expectedCatchCounts.toSortedMap()}; found ${actualCatchCounts.toSortedMap()}",
        )
    }
    catches.forEach { caught ->
        val expectedOutcome = when (caught.type.substringAfterLast('.')) {
            "BackingStoreException" -> "ProfilePersistenceMutationResult.POSSIBLE_EXECUTION"
            else -> "ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION"
        }
        if (expectedOutcome !in caught.body) {
            add("Desktop mutation seam `${caught.type}` catch must return only `$expectedOutcome`")
        }
    }
}

private fun desktopValueAdmissionViolations(code: String): List<String> = buildList {
    listOf(
        "internal fun desktopProfilePayloadAdmission(valueLength: Int): " +
            "ProfilePersistenceMutationResult?",
        "require(valueLength >= 0)",
        "valueLength <= Preferences.MAX_VALUE_LENGTH",
        "ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION",
        "desktopProfilePayloadAdmission(payload.length)?.let { return it }",
    ).filterNot(code::contains).forEach { token ->
        add("Desktop profile value admission is missing `$token`")
    }
    val brokerBody = declarationBodyForCapability(code, "private class DesktopProfilePersistenceCapability")
    val writeBody = brokerBody?.let { body -> memberFunctionSlice(body, "writeV4") }
    if (writeBody == null ||
        writeBody.indexOf("desktopProfilePayloadAdmission(payload.length)") !in
        0 until writeBody.indexOf("profileNode()")
    ) {
        add("Desktop value length must be admitted before profile-node/provider acquisition")
    }
}

private fun webBrokerSourceViolations(code: String): List<String> = buildList {
    requireWordCount(code, "localStorage", 3, WEB_PLATFORM_BROKER_PATH, this)
    requireWordCount(code, "JsAny", 8, WEB_PLATFORM_BROKER_PATH, this)
    requireWordCount(code, "globalThis", 7, WEB_PLATFORM_BROKER_PATH, this)
    listOf("kotlinx.browser.localStorage", "org.w3c.dom.Storage", "WebProfilePersistenceKeys")
        .filter(code::contains)
        .forEach { token ->
            add("Web persistence broker may not inject arbitrary storage/key authority via `$token`")
        }
    if ("request.wave.ordinal" in code ||
        Regex("\\[[^]]*(?:sine|square|sawtooth|triangle)[^]]*]\\s*\\[\\s*wave\\s*]")
            .containsMatchIn(code)
    ) {
        add("Web tone mapping may not depend on ToneWave ordinal or enum cardinality")
    }
    requireRegexCount(
        code,
        Regex(
            "actual\\s+fun\\s+createPlatformProfilePersistenceCapability\\s*\\(\\s*\\)\\s*:\\s*" +
                "ProfilePersistenceCapability\\s*=\\s*WebProfilePersistenceCapability\\s*\\(\\s*\\)",
        ),
        1,
        "direct web profile broker construction",
        WEB_PLATFORM_BROKER_PATH,
        this,
    )
    requireRegexCount(
        code,
        Regex(
            "actual\\s+fun\\s+createPlatformTonePlaybackCapability\\s*\\(\\s*\\)\\s*:\\s*" +
                "TonePlaybackCapability\\s*=\\s*WebTonePlaybackCapability\\s*\\(\\s*\\)",
        ),
        1,
        "direct web audio broker construction",
        WEB_PLATFORM_BROKER_PATH,
        this,
    )
    requireRegexCount(
        code,
        Regex("private\\s+var\\s+context\\s*:\\s*JsAny\\?\\s*=\\s*null"),
        1,
        "instance-owned web audio context",
        WEB_PLATFORM_BROKER_PATH,
        this,
    )
    val storageGlobalRead = "const exactStorage = globalThis.localStorage;"
    val audioGlobalRead =
        "const AudioContext = globalThis.AudioContext || globalThis.webkitAudioContext;"
    val globalLines = code.lineSequence().filter { "globalThis" in it }.map(String::trim).toList()
    val expectedGlobalLines = listOf(
        storageGlobalRead,
        storageGlobalRead,
        storageGlobalRead,
        audioGlobalRead,
        audioGlobalRead,
    )
    if (globalLines != expectedGlobalLines) {
        add(
            "Web broker globalThis access must be exactly three private fixed-key localStorage " +
                "acquisitions plus two private AudioContext constructor reads; " +
                "found ${globalLines.joinToString()}",
        )
    }
    if (Regex("\\b(?:window|self)\\s*(?:\\.|\\[)").containsMatchIn(code)) {
        add("Web broker may not use an alternate ambient global object")
    }
    val jsAnyProperties = declarationHeaders(code).filter { header ->
        Regex("\\b(?:val|var)\\b").containsMatchIn(header) &&
            Regex("\\bJsAny\\b").containsMatchIn(header)
    }
    if (jsAnyProperties.size != 1 ||
        "private var context: JsAny?" !in jsAnyProperties.singleOrNull().orEmpty().squashWhitespace()
    ) {
        add("Web AudioContext authority must be one private instance field and never a top-level/object cache")
    }
    if ("private class WebProfilePersistenceCapability : ProfilePersistenceCapability" !in code) {
        add("Web profile persistence broker must be private and retain no injected Storage/key handle")
    }
    addAll(
        closedPersistenceBrokerViolations(
            path = WEB_PLATFORM_BROKER_PATH,
            code = code,
            declaration = "private class WebProfilePersistenceCapability",
            allowedCalls = exactPersistenceOperations + setOf(
                "readWebProfileV4",
                "writeWebProfileV4",
                "readWebLegacyProgressV2",
                "readWebLegacyMatter",
                "removeWebLegacyProgressV2",
                "removeWebLegacyMatter",
            ),
            exactCallCounts = mapOf(
                "readWebProfileV4" to 1,
                "writeWebProfileV4" to 1,
                "readWebLegacyProgressV2" to 1,
                "readWebLegacyMatter" to 1,
                "removeWebLegacyProgressV2" to 1,
                "removeWebLegacyMatter" to 1,
            ),
            exactIdentifierCounts = emptyMap(),
            requiredExpressions = listOf(
                "override fun readV4(): ProfilePersistenceReadResult = readWebProfileV4()",
                "override fun writeV4(payload: String): ProfilePersistenceMutationResult = " +
                    "writeWebProfileV4(payload)",
                "override fun readLegacyProgressV2(): ProfilePersistenceReadResult = " +
                    "readWebLegacyProgressV2()",
                "override fun readLegacyMatter(): ProfilePersistenceReadResult = readWebLegacyMatter()",
                "override fun removeLegacyProgressV2(): ProfilePersistenceMutationResult = " +
                    "removeWebLegacyProgressV2()",
                "override fun removeLegacyMatter(): ProfilePersistenceMutationResult = " +
                    "removeWebLegacyMatter()",
            ),
        ),
    )
    addAll(webExactPersistenceHelperViolations(code))
    val audioBody = declarationBodyForCapability(code, "private class WebTonePlaybackCapability")
    if (audioBody == null) {
        add("Private web audio broker is missing")
    } else {
        requireWordCount(audioBody, "context", 7, WEB_PLATFORM_BROKER_PATH, this)
        if (Regex("\\bthis\\b").containsMatchIn(audioBody)) {
            add("Web audio broker may not publish its capability instance via `this`")
        }
    }
}

private fun webExactPersistenceHelperViolations(code: String): List<String> = buildList {
    val normalizedCode = code.squashWhitespace()
    listOf(
        "private external interface WebStorageReadCall : JsAny",
        "webStorageRead(ProfilePersistenceContract.WEB_SNAPSHOT_V4).toPersistenceResult()",
        "webStorageWrite(ProfilePersistenceContract.WEB_SNAPSHOT_V4, payload)" +
            ".toPersistenceMutationResult()",
        "webStorageRead(ProfilePersistenceContract.WEB_LEGACY_PROGRESS_V2).toPersistenceResult()",
        "webStorageRead(ProfilePersistenceContract.WEB_LEGACY_MATTER).toPersistenceResult()",
        "webStorageRemove(ProfilePersistenceContract.WEB_LEGACY_PROGRESS_V2)" +
            ".toPersistenceMutationResult()",
        "webStorageRemove(ProfilePersistenceContract.WEB_LEGACY_MATTER)" +
            ".toPersistenceMutationResult()",
        "WEB_STORAGE_OBSERVED -> ProfilePersistenceReadResult.Observed(payload)",
        "WEB_STORAGE_FAILED_BEFORE_EXECUTION -> ProfilePersistenceReadResult.Failed",
        "WEB_STORAGE_COMPLETED -> ProfilePersistenceMutationResult.COMPLETED",
        "WEB_STORAGE_FAILED_BEFORE_EXECUTION -> " +
            "ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION",
        "else -> error(\"Web Storage read returned an unknown provider status\")",
        "else -> error(\"Web Storage mutation returned an unknown provider status\")",
        "private fun webStorageRead(key: String): WebStorageReadCall = js(",
        "private fun webStorageWrite(key: String, payload: String): String = js(",
        "private fun webStorageRemove(key: String): String = js(",
        "exactStorage.getItem(key)",
        "exactStorage.setItem(key, payload)",
        "exactStorage.removeItem(key)",
    ).filterNot(normalizedCode::contains).forEach { token ->
        add("Private fixed-key Web persistence helpers are missing `$token`")
    }
    mapOf(
        "webStorageRead" to 4,
        "webStorageWrite" to 2,
        "webStorageRemove" to 3,
    ).forEach { (call, expected) ->
        val actual = Regex("\\b${Regex.escape(call)}\\s*\\(").findAll(code).count()
        if (actual != expected) {
            add("Private Web persistence helper `$call` must occur exactly $expected times; found $actual")
        }
    }
    mapOf(
        "exactStorage.getItem(key)" to 1,
        "exactStorage.setItem(key, payload)" to 1,
        "exactStorage.removeItem(key)" to 1,
    ).forEach { (operation, expected) ->
        val actual = code.windowed(operation.length).count { candidate -> candidate == operation }
        if (actual != expected) {
            add("Private Web persistence operation `$operation` must occur exactly $expected times; found $actual")
        }
    }
    if ("ProfilePersistenceMutationResult.POSSIBLE_EXECUTION" in code) {
        add("Synchronous Web Storage broker may not invent a possible-execution provider status")
    }
}

private fun closedPersistenceBrokerViolations(
    path: String,
    code: String,
    declaration: String,
    allowedCalls: Set<String>,
    exactCallCounts: Map<String, Int>,
    exactIdentifierCounts: Map<String, Int>,
    requiredExpressions: List<String>,
): List<String> = buildList {
    val body = declarationBodyForCapability(code, declaration)
    if (body == null) {
        add("Restricted persistence broker `$declaration` is missing in $path")
        return@buildList
    }
    val methods = Regex("\\boverride\\s+fun\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(")
        .findAll(body)
        .map { match -> match.groupValues[1] }
        .toList()
    if (methods.size != exactPersistenceOperations.size || methods.toSet() != exactPersistenceOperations) {
        add(
            "Persistence broker $path must implement exactly ${exactPersistenceOperations.sorted().joinToString()}; " +
                "found ${methods.joinToString()}",
        )
    }
    val parenthesizedCalls = Regex("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(")
        .findAll(body)
        .map { match -> match.groupValues[1] }
        .filterNot(kotlinControlKeywords::contains)
        .toList()
    val trailingLambdaCalls = Regex("\\.([A-Za-z_][A-Za-z0-9_]*)\\s*\\{")
        .findAll(body)
        .map { match -> match.groupValues[1] }
        .toList()
    val calls = parenthesizedCalls + trailingLambdaCalls
    val unexpectedCalls = calls.toSet() - allowedCalls
    if (unexpectedCalls.isNotEmpty()) {
        add("Persistence broker $path contains non-contract calls: ${unexpectedCalls.sorted().joinToString()}")
    }
    exactCallCounts.forEach { (call, expected) ->
        val actual = calls.count { it == call }
        if (actual != expected) {
            add("Persistence broker $path must call `$call` exactly $expected times; found $actual")
        }
    }
    exactIdentifierCounts.forEach { (identifier, expected) ->
        val actual = Regex("\\b${Regex.escape(identifier)}\\b").findAll(body).count()
        if (actual != expected) {
            add("Persistence broker $path must contain `$identifier` exactly $expected times; found $actual")
        }
    }
    val normalizedBody = body.squashWhitespace()
    requiredExpressions.filterNot { expression -> expression in normalizedBody }.forEach { expression ->
        add("Persistence broker $path is missing exact operation `$expression`")
    }
    if (Regex("\\bthis\\b").containsMatchIn(body)) {
        add("Persistence broker $path may not publish or retain its broad receiver via `this`")
    }
}

private fun exactProfilePersistenceContractViolations(code: String?): List<String> = buildList {
    if (code == null) {
        add("Closed physical profile persistence contract is missing $PROFILE_FACTORY_PATH")
        return@buildList
    }
    val body = declarationBodyForCapability(code, "object ProfilePersistenceContract")
    if (body == null) {
        add("Closed physical profile persistence contract is missing `ProfilePersistenceContract`")
        return@buildList
    }
    val actual = Regex(
        "\\bconst\\s+val\\s+([A-Z][A-Z0-9_]*)\\s*:\\s*String\\s*=\\s*\"([^\"]*)\"",
    ).findAll(body).associate { match -> match.groupValues[1] to match.groupValues[2] }
    if (actual != exactProfilePersistenceConstants) {
        add(
            "Physical profile persistence constants must be exactly $exactProfilePersistenceConstants; " +
                "found $actual",
        )
    }
}

private fun exactInterfaceOperationViolations(
    path: String,
    code: String?,
    declaration: String,
    expectedOperations: Set<String>,
): List<String> = buildList {
    if (code == null) {
        add("Restricted capability interface `$declaration` is missing $path")
        return@buildList
    }
    val body = declarationBodyForCapability(code, declaration)
    if (body == null) {
        add("Restricted capability interface `$declaration` is missing in $path")
        return@buildList
    }
    val operations = Regex("\\bfun\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(")
        .findAll(body)
        .map { match -> match.groupValues[1] }
        .toList()
    if (operations.size != expectedOperations.size || operations.toSet() != expectedOperations) {
        add(
            "Restricted capability `$declaration` in $path must expose exactly " +
                "${expectedOperations.sorted().joinToString()}; found ${operations.joinToString()}",
        )
    }
}

private fun exactEnumEntriesViolations(
    path: String,
    code: String?,
    declaration: String,
    expectedEntries: List<String>,
): List<String> = buildList {
    if (code == null) {
        add("Closed provider result `$declaration` is missing $path")
        return@buildList
    }
    val body = declarationBodyForCapability(code, declaration)
    if (body == null) {
        add("Closed provider result `$declaration` is missing in $path")
        return@buildList
    }
    val actual = Regex("\\b[A-Z][A-Z0-9_]*\\b")
        .findAll(body.substringBefore(';'))
        .map { match -> match.value }
        .toList()
    if (actual != expectedEntries) {
        add(
            "Closed provider result `$declaration` in $path must contain exactly " +
                "${expectedEntries.joinToString()}; found ${actual.joinToString()}",
        )
    }
}

private fun platformFactoryCallsiteViolations(
    production: List<SourceDocument>,
    codeByPath: Map<String, String>,
): List<String> = buildList {
    val expectedCountsByPath = mapOf(
        APP_PLATFORM_EXPECT_PATH to 2,
        DESKTOP_PLATFORM_BROKER_PATH to 1,
        WEB_PLATFORM_BROKER_PATH to 1,
    )
    listOf(
        "createPlatformProfilePersistenceCapability",
        "createPlatformTonePlaybackCapability",
    ).forEach { factory ->
        production.forEach { source ->
            val actual = Regex("\\b${Regex.escape(factory)}\\s*\\(")
                .findAll(codeByPath.getValue(source.relativePath))
                .count()
            val expected = expectedCountsByPath[source.relativePath] ?: 0
            if (actual != expected) {
                add(
                    "Platform capability factory `$factory` must occur exactly $expected times in " +
                        "${source.relativePath}; found $actual",
                )
            }
        }
    }
    val assembly = codeByPath[APP_PLATFORM_EXPECT_PATH]?.squashWhitespace().orEmpty()
    listOf(
        "persistence = createPlatformProfilePersistenceCapability(),",
        "DefaultAudioService( createPlatformTonePlaybackCapability(), )",
    ).filterNot(assembly::contains).forEach { binding ->
        add("Static Assembly is missing exact platform capability binding `$binding`")
    }
}

private fun desktopPersistenceSeamCallsiteViolations(
    production: List<SourceDocument>,
    codeByPath: Map<String, String>,
): List<String> = buildList {
    val expectedCalls = mapOf(
        "desktopProfileReadCall" to 3,
        "desktopProfileMutationCall" to 3,
        "desktopPreferenceKeyCountAdmission" to 1,
        "desktopProfilePayloadAdmission" to 1,
    )
    expectedCalls.forEach { (seam, expectedInBroker) ->
        production.forEach { source ->
            val code = codeByPath.getValue(source.relativePath)
            val calls = Regex("\\b${Regex.escape(seam)}\\s*\\(").findAll(code).count()
            val declarations = Regex("\\bfun\\s+${Regex.escape(seam)}\\s*\\(").findAll(code).count()
            val actual = calls - declarations
            val expected = if (source.relativePath == DESKTOP_PLATFORM_BROKER_PATH) expectedInBroker else 0
            if (actual != expected) {
                add(
                    "Desktop persistence seam `$seam` must have exactly $expected production calls in " +
                        "${source.relativePath}; found $actual",
                )
            }
        }
    }
}

private fun closedCapabilityTypeInventoryViolations(
    production: List<SourceDocument>,
    codeByPath: Map<String, String>,
): List<String> = buildList {
    val expectedProfileCounts = mapOf(
        PROFILE_FACTORY_PATH to 3,
        APP_PLATFORM_EXPECT_PATH to 2,
        DESKTOP_PLATFORM_BROKER_PATH to 3,
        WEB_PLATFORM_BROKER_PATH to 3,
    )
    val expectedAudioCounts = mapOf(
        AUDIO_RESOURCE_PATH to 3,
        APP_PLATFORM_EXPECT_PATH to 2,
        DESKTOP_PLATFORM_BROKER_PATH to 3,
        WEB_PLATFORM_BROKER_PATH to 3,
    )
    listOf(
        "ProfilePersistenceCapability" to expectedProfileCounts,
        "TonePlaybackCapability" to expectedAudioCounts,
    ).forEach { (capability, expectedByPath) ->
        production.forEach { source ->
            val actual = Regex("\\b${Regex.escape(capability)}\\b")
                .findAll(codeByPath.getValue(source.relativePath))
                .count()
            val expected = expectedByPath[source.relativePath] ?: 0
            if (actual != expected) {
                add(
                    "Capability type `$capability` must occur exactly $expected times in " +
                        "${source.relativePath}; found $actual (authority cache, alias, or forwarding seam)",
                )
            }
        }
    }
}

private fun requireWordCount(
    code: String,
    word: String,
    expected: Int,
    path: String,
    violations: MutableList<String>,
) {
    val actual = Regex("\\b${Regex.escape(word)}\\b").findAll(code).count()
    if (actual != expected) {
        violations += "Restricted broker $path must contain `$word` exactly $expected times; found $actual"
    }
}

private fun requireRegexCount(
    code: String,
    pattern: Regex,
    expected: Int,
    label: String,
    path: String,
    violations: MutableList<String>,
) {
    val actual = pattern.findAll(code).count()
    if (actual != expected) {
        violations += "Restricted broker $path must contain $label exactly $expected times; found $actual"
    }
}

private fun declarationHeaders(code: String): List<String> {
    val declarationStart = Regex(
        "(?m)^[ \\t]*(?:(?:public|private|internal|protected|expect|actual|data|sealed|open|abstract|" +
            "final|inline|suspend|tailrec|operator|infix|external|const|lateinit|override)\\s+)*" +
            "(?:class|interface|object|fun|val|var|typealias)\\b",
    )
    return declarationStart.findAll(code).map { match ->
        declarationHeaderAt(code, match.range.first)
    }.toList()
}

private fun declarationHeaderAt(code: String, start: Int): String {
    var roundDepth = 0
    var squareDepth = 0
    var index = start
    while (index < code.length) {
        when (code[index]) {
            '(' -> roundDepth += 1
            ')' -> if (roundDepth > 0) roundDepth -= 1
            '[' -> squareDepth += 1
            ']' -> if (squareDepth > 0) squareDepth -= 1
            '{', '=' -> if (roundDepth == 0 && squareDepth == 0) {
                return code.substring(start, index)
            }
        }
        index += 1
    }
    return code.substring(start)
}

private fun primaryConstructorPropertyNames(code: String, typeName: String): List<String> {
    val declaration = Regex("\\bdata\\s+class\\s+${Regex.escape(typeName)}\\s*\\(").find(code)
        ?: return emptyList()
    val open = code.indexOf('(', declaration.range.first)
    val close = closingDelimiter(code, open, '(', ')') ?: return emptyList()
    return Regex("\\bval\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*:")
        .findAll(code.substring(open + 1, close))
        .map { match -> match.groupValues[1] }
        .toList()
}

private fun declarationBodyForCapability(code: String, declaration: String): String? {
    val declarationIndex = code.indexOf(declaration)
    if (declarationIndex < 0) return null
    val open = code.indexOf('{', declarationIndex + declaration.length)
    if (open < 0) return null
    val close = closingDelimiter(code, open, '{', '}') ?: return null
    return code.substring(open + 1, close)
}

private fun memberFunctionSlice(body: String, functionName: String): String? {
    val declaration = Regex("\\boverride\\s+fun\\s+${Regex.escape(functionName)}\\s*\\(").find(body)
        ?: return null
    val next = Regex("\\boverride\\s+fun\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\(")
        .find(body, declaration.range.last + 1)
        ?.range
        ?.first ?: body.length
    return body.substring(declaration.range.first, next)
}

private fun sameIndentFunctionSlice(code: String, declaration: String): String? {
    val start = code.indexOf(declaration)
    if (start < 0) return null
    val lineStart = code.lastIndexOf('\n', start).let { index -> if (index < 0) 0 else index + 1 }
    val indent = code.substring(lineStart, start).takeWhile(Char::isWhitespace)
    val next = Regex(
        "(?m)^${Regex.escape(indent)}(?:private|internal|public|override)\\s+" +
            "(?:actual\\s+)?fun\\s+",
    ).find(code, start + declaration.length)?.range?.first ?: code.length
    return code.substring(start, next)
}

private data class KotlinCatchBlock(
    val parameter: String,
    val type: String,
    val body: String,
    val line: Int,
    val declarationStart: Int,
    val bodyStart: Int,
    val bodyEnd: Int,
)

private fun kotlinCatchBlocks(code: String): List<KotlinCatchBlock> =
    kotlinCatchBlock.findAll(code).mapNotNull { match ->
        val open = match.range.last
        val close = closingDelimiter(code, open, '{', '}') ?: return@mapNotNull null
        KotlinCatchBlock(
            parameter = match.groupValues[1],
            type = match.groupValues[2],
            body = code.substring(open + 1, close),
            line = code.substring(0, match.range.first).count { character -> character == '\n' } + 1,
            declarationStart = match.range.first,
            bodyStart = open + 1,
            bodyEnd = close,
        )
    }.toList()

private fun broadRuntimeFaultCatchBlocks(code: String): List<KotlinCatchBlock> =
    kotlinCatchBlocks(code).filter { caught ->
        caught.type.substringAfterLast('.') in broadRuntimeFaultTypes
    }

private fun directlyRethrowsCaughtFault(caught: KotlinCatchBlock): Boolean =
    Regex("""\s*throw\s+${Regex.escape(caught.parameter)}\s*;?\s*""").matches(caught.body)

private fun typedCatchSignatureCount(rawCode: String, maskedCode: String): Int {
    val catchDeclaration = Regex("""\bcatch\s*\(""")
    val visible = catchDeclaration.findAll(maskedCode).count { declaration ->
        val open = maskedCode.indexOf('(', declaration.range.first)
        val close = closingDelimiter(maskedCode, open, '(', ')') ?: return@count true
        ':' in maskedCode.substring(open + 1, close)
    }
    val hidden = catchDeclaration.findAll(rawCode).count { declaration ->
        if (maskedCode.regionMatches(declaration.range.first, "catch", 0, "catch".length)) {
            return@count false
        }
        val open = rawCode.indexOf('(', declaration.range.first)
        val close = closingDelimiterIgnoringQuotedText(rawCode, open, '(', ')')
            ?: return@count true
        ':' in rawCode.substring(open + 1, close)
    }
    return visible + hidden
}

private fun runtimeFaultAliasViolations(code: String): List<String> {
    val identifier = "(?:`[^`]+`|[A-Za-z_][A-Za-z0-9_]*)"
    val qualifiedIdentifier = "$identifier(?:\\s*\\.\\s*$identifier)*"
    val importAliases = Regex(
        "(?m)\\bimport\\s+($qualifiedIdentifier)\\s+as\\s+($identifier)\\s*(?=;|$)",
    ).findAll(code).mapNotNull { match ->
        match.groupValues[2].takeIf {
            directTypeName(match.groupValues[1]) in broadRuntimeFaultTypes
        }
    }
    val typeAliases = typeAliasDeclarations(code).asSequence()
        .filter { (_, target) -> directTypeName(target) in broadRuntimeFaultTypes }
        .map(Pair<String, String>::first)
    return (importAliases + typeAliases).map { alias -> alias.removeSurrounding("`") }.toList()
}

private fun profileDeferredOutputDrainViolations(
    code: String,
    rawCode: String,
    requireCanonicalFunctions: Boolean,
): List<String> = buildList {
    if (requireCanonicalFunctions) {
        val actualDigest = canonicalCodeDigest(rawCode)
        if (actualDigest != CANONICAL_PROFILE_COMPONENT_SEMANTIC_DIGEST) {
            add(
                "Core §6.13 fault-stage violation in $PROFILE_COMPONENT_IMPL_PATH: " +
                    "Profile component semantic source changed; expected " +
                    "$CANONICAL_PROFILE_COMPONENT_SEMANTIC_DIGEST but found $actualDigest",
            )
        }
    }
    val exactCanonicalFileInventory = !requireCanonicalFunctions ||
        (Regex("""\bexecute\b""").findAll(code).count() == 3 &&
            Regex("""\bdecision\.frame\b""").findAll(code).count() == 7)
    setOf("dispatchLocal", "dispatchCommand").forEach { functionName ->
        val functions = functionBlocks(code).filter { function -> function.name == functionName }
        if (functions.isEmpty()) {
            if (requireCanonicalFunctions) {
                add(
                    "Core §6.13 fault-stage violation in $PROFILE_COMPONENT_IMPL_PATH: " +
                        "Profile `$functionName` canonical accepted-output drain is missing",
                )
            }
            return@forEach
        }
        val function = functions.singleOrNull()
        if (function == null) {
            add(
                "Core §6.13 fault-stage violation in $PROFILE_COMPONENT_IMPL_PATH: " +
                    "Profile `$functionName` must have one canonical accepted-output drain",
            )
            return@forEach
        }
        val batches = acceptedOutputBatches(code).filter { batch ->
            batch.open in (function.open + 1) until function.close && batch.close < function.close
        }
        val batch = batches.singleOrNull()
        val drain = batch?.let { acceptedBatch ->
            enclosingCompletionDrain(code, acceptedBatch.open)
        }
        val acceptedBranchOpen = batch?.let { acceptedBatch ->
            drain?.let { completionDrain ->
                nearestEnclosingCurlyOpen(code, completionDrain.open, acceptedBatch.open)
            }
        }
        val acceptedBranchClose = acceptedBranchOpen?.let { open ->
            closingDelimiter(code, open, '{', '}')
        }
        val directlyInsideAcceptedBranch = acceptedBranchOpen?.let { open ->
            Regex("""is\s+ProfileDecision\.Accepted\s*->\s*\{\s*$""")
                .containsMatchIn(code.substring(drain!!.open + 1, open + 1))
        } == true
        val unconditionallyExecutedBatch = batch != null && acceptedBranchOpen != null &&
            !isOwnedByUnbracedControl(code, batch.declarationStart, acceptedBranchOpen + 1)
        val catches = batch?.let { acceptedBatch ->
            broadRuntimeFaultCatchBlocks(code).filter { caught ->
                caught.declarationStart in (acceptedBatch.open + 1) until acceptedBatch.close
            }
        }.orEmpty()
        val executeCalls = Regex("""\bexecute\s*\(""").findAll(code, function.open + 1)
            .takeWhile { match -> match.range.first < function.close }
            .count()
        val executeIdentifiers = Regex("""\bexecute\b""").findAll(code, function.open + 1)
            .takeWhile { match -> match.range.first < function.close }
            .count()
        val acceptedOutputReferences = Regex("""\bdecision\.frame\.outputs\b""")
            .findAll(code, function.open + 1)
            .takeWhile { match -> match.range.first < function.close }
            .count()
        val decisionFrameReferences = Regex("""\bdecision\.frame\b""")
            .findAll(code, function.open + 1)
            .takeWhile { match -> match.range.first < function.close }
            .count()
        val exactProductionReferences = !requireCanonicalFunctions || when (functionName) {
            "dispatchLocal" -> executeIdentifiers == 1 && decisionFrameReferences == 3
            "dispatchCommand" -> executeIdentifiers == 1 && decisionFrameReferences == 4
            else -> false
        }
        val exactAcceptedBranch = !requireCanonicalFunctions ||
            (batch != null && acceptedBranchOpen != null && acceptedBranchClose != null &&
                exactProfileAcceptedBranchPrelude(
                    functionName,
                    code.substring(acceptedBranchOpen + 1, batch.declarationStart),
                ) &&
                code.substring(batch.close + 1, acceptedBranchClose).isBlank())
        if (batch == null || drain == null ||
            curlyDepthBetween(code, drain.open + 1, batch.open) != 2 ||
            !directlyInsideAcceptedBranch ||
            !unconditionallyExecutedBatch ||
            executeCalls != 1 ||
            executeIdentifiers != 1 ||
            acceptedOutputReferences != 1 ||
            !exactProductionReferences ||
            !exactCanonicalFileInventory ||
            !exactAcceptedBranch ||
            catches.size != 1 ||
            !preservesDeferredFaultUntilRethrow(code, rawCode, catches.single())
        ) {
            add(
                "Core §6.13 fault-stage violation in $PROFILE_COMPONENT_IMPL_PATH: " +
                    "Profile `$functionName` must preserve the first runtime fault until its " +
                    "canonical accepted-output/completion drain finishes",
            )
        }
    }
}

private fun canonicalCodeDigest(code: String): String {
    val canonical = code.replace(Regex("""\s+"""), " ").trim()
    return java.security.MessageDigest.getInstance("SHA-256")
        .digest(canonical.encodeToByteArray())
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

internal fun exactProfileAcceptedBranchPrelude(functionName: String, code: String): Boolean {
    val preflightAndCommit =
        """preflight\s*\(\s*before\s*,\s*item\s*,\s*decision\.frame\s*\)\s*""" +
            """committedState\s*=\s*decision\.frame\.nextState\s*"""
    val pattern = when (functionName) {
        "dispatchLocal" ->
            preflightAndCommit +
                """if\s*\(\s*root\s*\)\s*\{\s*""" +
                """rootAcceptance\s*=\s*ProfileAcceptance\.Accepted\s*\(\s*""" +
                """instanceId\s*=\s*committedState\.instanceId\s*,\s*""" +
                """revision\s*=\s*committedState\.revision\s*,\s*\)\s*\}\s*"""
        "dispatchCommand" ->
            """if\s*\(\s*root\s*&&\s*deepestReservedLevel\s*\(\s*item\s*,\s*""" +
                """decision\.frame\s*\)\s*>=\s*MAX_PROFILE_CAUSAL_DEPTH\s*\)\s*\{\s*""" +
                """activeCommandRoute\s*=\s*null\s*""" +
                """return@dispatch\s+refused\s*\(\s*""" +
                """commandSource\s*=\s*pulse\.commandSource\s*,\s*""" +
                """effectiveProtocolIdentity\s*=\s*pulse\.effectiveProtocolIdentity\s*,\s*""" +
                """response\s*=\s*causalBudgetFailure\s*\(\s*pulse\.commandSource\s*\)\s*,\s*""" +
                """\)\s*\}\s*""" +
                preflightAndCommit +
                """if\s*\(\s*root\s*\)\s*acceptedTargetRevision\s*=\s*""" +
                """committedState\.revision\s*"""
        else -> return false
    }
    return Regex("""\s*(?:$pattern)""").matches(code)
}

private fun preservesDeferredFaultUntilRethrow(
    code: String,
    rawCode: String,
    caught: KotlinCatchBlock,
): Boolean {
    val parameter = caught.parameter.takeUnless { it == "_" } ?: return false
    val assignment = Regex(
        """\s*if\s*\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*==\s*null\s*\)\s*""" +
            """\1\s*=\s*${Regex.escape(parameter)}\s*;?\s*""",
    ).matchEntire(caught.body) ?: return false
    val deferred = assignment.groupValues[1]
    val function = enclosingFunctionBlock(code, caught.declarationStart) ?: return false
    if (function.name !in setOf("dispatchLocal", "dispatchCommand")) return false
    val declaration = Regex(
        """\bvar\s+${Regex.escape(deferred)}\s*:\s*(?:kotlin\.)?Throwable\s*\?\s*=\s*null\b""",
    )
    val declarations = declaration.findAll(code, function.open + 1)
        .takeWhile { match -> match.range.first < function.close }
        .toList()
    if (declarations.size != 1 ||
        curlyDepthBetween(code, function.open + 1, declarations.single().range.first) != 0
    ) {
        return false
    }

    val rethrows = Regex(
        """\bval\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*${Regex.escape(deferred)}\s*""" +
            """if\s*\(\s*\1\s*!=\s*null\s*\)\s*throw\s+\1\b""",
    ).findAll(code, caught.bodyEnd)
        .takeWhile { match -> match.range.first < function.close }
        .toList()
    if (rethrows.size != 1) return false
    val rethrow = rethrows.single()
    if (curlyDepthBetween(code, function.open + 1, rethrow.range.first) != 0) return false

    val drain = enclosingCompletionDrain(code, caught.declarationStart) ?: return false
    val outputBatch = enclosingAcceptedOutputBatch(code, caught.declarationStart) ?: return false
    val drainBody = code.substring(drain.open + 1, drain.close)
    val rawFunctionBody = rawCode.substring(function.open + 1, function.close)
    val removals = Regex(
        """\bval\s+item\s*=\s*checkNotNull\s*\(\s*completions\.removeFirstOrNull\s*""" +
            """\(\s*\)\s*\)""",
    ).findAll(code, drain.open + 1)
        .takeWhile { match -> match.range.first < drain.close }
        .toList()
    val decisions = Regex(
        """\bwhen\s*\(\s*val\s+decision\s*=\s*ProfileNucleus\.decide\s*""" +
            """\(\s*before\s*,\s*item\.pulse\s*\)\s*\)\s*\{""",
    ).findAll(code, drain.open + 1)
        .takeWhile { match -> match.range.first < drain.close }
        .toList()
    val exactOutputBatch = Regex(
        """\s*try\s*\{\s*this\.execute\s*\(\s*output\s*,\s*item\s*\)\s*\}\s*""" +
            """catch\s*\(\s*${Regex.escape(parameter)}\s*:\s*Throwable\s*\)\s*\{\s*""" +
            """if\s*\(\s*${Regex.escape(deferred)}\s*==\s*null\s*\)\s*""" +
            """${Regex.escape(deferred)}\s*=\s*${Regex.escape(parameter)}\s*\}\s*""",
    ).matches(code.substring(outputBatch.open + 1, outputBatch.close))
    val remainingDrain = code.substring(caught.bodyEnd, drain.close)
    val removalToOutput = removals.singleOrNull()?.let { removal ->
        code.substring(removal.range.last + 1, outputBatch.open)
    }.orEmpty()
    val removalToDecision = if (removals.size == 1 && decisions.size == 1) {
        code.substring(removals.single().range.last + 1, decisions.single().range.first)
    } else {
        ""
    }
    val hasExactAdmissionReturns = hasExactDispatchAdmissionReturns(
        code = code,
        drain = drain,
        functionName = function.name,
        admissionCode = removalToOutput,
        admissionStart = removals.singleOrNull()?.range?.last?.plus(1) ?: 0,
    )
    if (drain.queue != "completions" ||
        isOwnedByUnbracedControl(code, drain.declarationStart, function.open + 1) ||
        outputBatch.open !in (drain.open + 1) until drain.close ||
        outputBatch.close >= drain.close ||
        drain.close >= rethrow.range.first ||
        code.substring(drain.close + 1, rethrow.range.first).isNotBlank() ||
        removals.size != 1 ||
        decisions.size != 1 ||
        code.substring(drain.open + 1, removals.single().range.first).isNotBlank() ||
        removals.single().range.first >= outputBatch.open ||
        curlyDepthBetween(code, drain.open + 1, removals.single().range.first) != 0 ||
        decisions.single().range.first >= outputBatch.open ||
        curlyDepthBetween(code, drain.open + 1, decisions.single().range.first) != 0 ||
        !Regex("""\s*val\s+before\s*=\s*committedState\s*""").matches(removalToDecision) ||
        Regex("""\bcompletions\.removeFirstOrNull\s*\(""").findAll(drainBody).count() != 1 ||
        '$' in rawFunctionBody ||
        Regex("""\b${Regex.escape(deferred)}\b""")
            .findAll(code, function.open + 1)
            .takeWhile { match -> match.range.first < function.close }
            .count() != 4 ||
        Regex(
            """\b(?:throw|break|continue|""" +
                """error\s*\(|TODO\s*\()""",
        ).containsMatchIn(removalToOutput) ||
        !hasExactAdmissionReturns ||
        !exactOutputBatch ||
        !Regex("""(?:\s*\}\s*)+root\s*=\s*false\s*""").matches(remainingDrain)
    ) {
        return false
    }
    val beforeRethrow = code.substring(caught.bodyEnd, rethrow.range.first)
    if (Regex("""\breturn(?:@[A-Za-z_][A-Za-z0-9_]*)?\b""").containsMatchIn(beforeRethrow)) {
        return false
    }

    val assignments = Regex(
        """(?<![=!<>])\b${Regex.escape(deferred)}\s*=(?!=)""",
    ).findAll(code, function.open + 1).takeWhile { match -> match.range.first < function.close }.toList()
    val catches = broadRuntimeFaultCatchBlocks(code).filter { other ->
        other.declarationStart in (function.open + 1) until function.close
    }
    return assignments.isNotEmpty() && assignments.all { write ->
        catches.any { other ->
            write.range.first in other.bodyStart until other.bodyEnd &&
                Regex(
                    """\s*if\s*\(\s*${Regex.escape(deferred)}\s*==\s*null\s*\)\s*""" +
                        """${Regex.escape(deferred)}\s*=\s*${Regex.escape(other.parameter)}\s*;?\s*""",
                ).matches(other.body)
        }
    }
}

private fun hasExactDispatchAdmissionReturns(
    code: String,
    drain: CompletionDrainBlock,
    functionName: String?,
    admissionCode: String,
    admissionStart: Int,
): Boolean {
    val returns = Regex("""\breturn(?:@[A-Za-z_][A-Za-z0-9_]*)?\b""")
        .findAll(admissionCode)
        .map { match ->
            val absoluteStart = admissionStart + match.range.first
            match to absoluteStart
        }
        .toList()
    if (functionName == "dispatchLocal") return returns.isEmpty()
    if (functionName != "dispatchCommand" || returns.size != 2) return false

    val branches = returns.mapNotNull { (_, absoluteStart) ->
        if (!Regex("""return@dispatch\s+refused\s*\(""").matchesAt(code, absoluteStart)) {
            return@mapNotNull null
        }
        val open = nearestEnclosingCurlyOpen(code, drain.open, absoluteStart)
            ?: return@mapNotNull null
        if (curlyDepthBetween(code, open + 1, absoluteStart) != 0) return@mapNotNull null
        val close = closingDelimiter(code, open, '{', '}') ?: return@mapNotNull null
        val prefix = code.substring(drain.open + 1, open + 1)
        val beforeReturn = code.substring(open + 1, absoluteStart)
        val body = code.substring(open + 1, close)
        val routeClear = Regex("""activeCommandRoute\s*=\s*null\s*$""")
            .find(beforeReturn) ?: return@mapNotNull null
        val beforeRouteClear = beforeReturn.substring(0, routeClear.range.first)
        when {
            Regex("""is\s+ProfileDecision\.Rejected\s*->\s*\{\s*$""")
                .containsMatchIn(prefix) &&
                Regex(
                    """\s*check\s*\(\s*root\s*\)\s*\{\s*\+\s*decision\.reason\s*\}\s*""",
                ).matches(beforeRouteClear) &&
                "ProfileCommandBoundaryResponse.DecisionRejected" in body -> "rejected"
            Regex(
                """if\s*\(\s*root\s*&&\s*deepestReservedLevel\s*\(\s*item\s*,\s*""" +
                    """decision\.frame\s*\)\s*>=\s*MAX_PROFILE_CAUSAL_DEPTH\s*\)\s*\{\s*$""",
            ).containsMatchIn(prefix) && beforeRouteClear.isBlank() &&
                "causalBudgetFailure" in body -> "causal-budget"
            else -> null
        }
    }
    return branches.toSet() == setOf("rejected", "causal-budget")
}

private fun nearestEnclosingCurlyOpen(code: String, start: Int, position: Int): Int? {
    val stack = ArrayDeque<Int>()
    for (index in start until position) {
        when (code[index]) {
            '{' -> stack.addLast(index)
            '}' -> if (stack.isNotEmpty()) stack.removeLast()
        }
    }
    return stack.lastOrNull()
}

private fun String.maskEscapedIdentifierBodies(): String = buildString(length) {
    var inside = false
    this@maskEscapedIdentifierBodies.forEach { character ->
        when {
            character == '`' -> {
                append(' ')
                inside = !inside
            }
            inside -> append(if (character == '\n') '\n' else ' ')
            else -> append(character)
        }
    }
}

private fun closingDelimiterIgnoringQuotedText(
    text: String,
    open: Int,
    opening: Char,
    closing: Char,
): Int? {
    var depth = 0
    var index = open
    var quote: Char? = null
    var tripleQuoted = false
    var escaped = false
    var backticked = false
    while (index < text.length) {
        val character = text[index]
        when {
            backticked -> if (character == '`') backticked = false
            character == '`' && quote == null -> backticked = true
            quote != null && tripleQuoted &&
                text.substring(index, minOf(text.length, index + 3)) == "\"\"\"" -> {
                quote = null
                tripleQuoted = false
                index += 2
            }
            quote != null && !tripleQuoted && escaped -> escaped = false
            quote != null && !tripleQuoted && character == '\\' -> escaped = true
            quote != null && !tripleQuoted && character == quote -> quote = null
            quote == null && text.substring(index, minOf(text.length, index + 3)) == "\"\"\"" -> {
                quote = '"'
                tripleQuoted = true
                index += 2
            }
            quote == null && (character == '"' || character == '\'') -> quote = character
            quote == null && character == opening -> depth += 1
            quote == null && character == closing -> {
                depth -= 1
                if (depth == 0) return index
            }
        }
        index += 1
    }
    return null
}

private data class FunctionBlock(
    val open: Int,
    val close: Int,
    val name: String? = null,
    val declarationStart: Int = open,
)

private data class CompletionDrainBlock(
    val queue: String,
    val open: Int,
    val close: Int,
    val declarationStart: Int,
)

private fun enclosingFunctionBlock(code: String, position: Int): FunctionBlock? {
    return functionBlocks(code)
        .filter { function -> position in (function.open + 1) until function.close }
        .maxByOrNull(FunctionBlock::open)
}

private fun functionBlocks(code: String): List<FunctionBlock> {
    val declarations = Regex("""\bfun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
        .findAll(code)
        .toList()
    return declarations.mapIndexedNotNull { index, declaration ->
        val nextDeclaration = declarations.getOrNull(index + 1)?.range?.first ?: code.length
        val open = code.indexOf('{', declaration.range.last + 1).takeIf { it >= 0 }
            ?: return@mapIndexedNotNull null
        if (open >= nextDeclaration) return@mapIndexedNotNull null
        val close = closingDelimiter(code, open, '{', '}') ?: return@mapIndexedNotNull null
        FunctionBlock(open, close, declaration.groupValues[1], declaration.range.first)
    }
}

private fun enclosingCompletionDrain(code: String, position: Int): CompletionDrainBlock? =
    Regex(
        """\bwhile\s*\(\s*!\s*([A-Za-z_][A-Za-z0-9_]*)\.isEmpty\s*\)\s*\{""",
    ).findAll(code).mapNotNull { declaration ->
        val open = declaration.range.last
        val close = closingDelimiter(code, open, '{', '}') ?: return@mapNotNull null
        CompletionDrainBlock(declaration.groupValues[1], open, close, declaration.range.first)
            .takeIf { position in (open + 1) until close }
    }.maxByOrNull(CompletionDrainBlock::open)

private fun enclosingAcceptedOutputBatch(code: String, position: Int): FunctionBlock? =
    acceptedOutputBatches(code).filter { batch ->
        position in (batch.open + 1) until batch.close
    }.maxByOrNull(FunctionBlock::open)

private fun acceptedOutputBatches(code: String): List<FunctionBlock> =
    Regex(
        """\bfor\s*\(\s*output\s+in\s+decision\.frame\.outputs\s*\)\s*\{""",
    ).findAll(code).mapNotNull { declaration ->
        val open = declaration.range.last
        val close = closingDelimiter(code, open, '{', '}') ?: return@mapNotNull null
        FunctionBlock(open, close, declarationStart = declaration.range.first)
    }.toList()

private fun isOwnedByUnbracedControl(code: String, statementStart: Int, lowerBound: Int): Boolean {
    var index = statementStart - 1
    while (index >= lowerBound && code[index].isWhitespace()) index -= 1
    while (index >= lowerBound) {
        when {
            code[index] == '@' -> {
                index -= 1
                while (index >= lowerBound &&
                    (code[index].isLetterOrDigit() || code[index] == '_')
                ) {
                    index -= 1
                }
            }
            else -> {
                val annotationStart = annotationStartEndingAt(code, index, lowerBound) ?: break
                index = annotationStart - 1
            }
        }
        while (index >= lowerBound && code[index].isWhitespace()) index -= 1
    }
    val tail = index
    if (index >= lowerBound && (code[index].isLetterOrDigit() || code[index] == '_')) {
        val end = index + 1
        while (index >= lowerBound && (code[index].isLetterOrDigit() || code[index] == '_')) index -= 1
        if (code.substring(index + 1, end) == "else") return true
        index = tail
    }
    if (index < lowerBound || code[index] != ')') return false
    var depth = 0
    var open = -1
    while (index >= lowerBound) {
        when (code[index]) {
            ')' -> depth += 1
            '(' -> {
                depth -= 1
                if (depth == 0) {
                    open = index
                    break
                }
            }
        }
        index -= 1
    }
    if (open < 0) return true
    index = open - 1
    while (index >= lowerBound && code[index].isWhitespace()) index -= 1
    val end = index + 1
    while (index >= lowerBound && (code[index].isLetterOrDigit() || code[index] == '_')) index -= 1
    return code.substring(index + 1, end) in setOf("if", "while", "for", "when")
}

private fun annotationStartEndingAt(code: String, endInclusive: Int, lowerBound: Int): Int? {
    var index = endInclusive
    if (code[index] == ')') {
        index = matchingOpenDelimiter(code, index, '(', ')', lowerBound) ?: return null
        index -= 1
        while (index >= lowerBound && code[index].isWhitespace()) index -= 1
    } else if (code[index] == ']') {
        index = matchingOpenDelimiter(code, index, '[', ']', lowerBound) ?: return null
        index -= 1
        while (index >= lowerBound && code[index].isWhitespace()) index -= 1
        return index.takeIf { it >= lowerBound && code[it] == '@' }
    }

    if (index >= lowerBound && code[index] == '@') return index
    while (index >= lowerBound) {
        if (!(code[index].isLetterOrDigit() || code[index] == '_')) return null
        while (index >= lowerBound &&
            (code[index].isLetterOrDigit() || code[index] == '_')
        ) {
            index -= 1
        }
        while (index >= lowerBound && code[index].isWhitespace()) index -= 1
        when {
            index >= lowerBound && code[index] == '.' -> {
                index -= 1
                while (index >= lowerBound && code[index].isWhitespace()) index -= 1
            }
            index >= lowerBound && code[index] == ':' -> {
                index -= 1
                while (index >= lowerBound && code[index].isWhitespace()) index -= 1
            }
            else -> return index.takeIf { it >= lowerBound && code[it] == '@' }
        }
    }
    return null
}

private fun matchingOpenDelimiter(
    code: String,
    close: Int,
    opening: Char,
    closing: Char,
    lowerBound: Int,
): Int? {
    var depth = 0
    for (index in close downTo lowerBound) {
        when (code[index]) {
            closing -> depth += 1
            opening -> {
                depth -= 1
                if (depth == 0) return index
            }
        }
    }
    return null
}

private fun curlyDepthBetween(code: String, start: Int, endExclusive: Int): Int {
    var depth = 0
    for (index in start until endExclusive) {
        when (code[index]) {
            '{' -> depth += 1
            '}' -> depth -= 1
        }
    }
    return depth
}

private fun knownPreExecutionMutationBranches(code: String): List<String> {
    val marker = "ProfileProviderMutationResult.FAILED_BEFORE_EXECUTION"
    return Regex("${Regex.escape(marker)}\\s*->").findAll(code).map { match ->
        val branchStart = match.range.last + 1
        val contentStart = code.indexOfFirstFrom(branchStart) { character -> !character.isWhitespace() }
        if (contentStart < 0) {
            ""
        } else if (code[contentStart] == '{') {
            val close = closingDelimiter(code, contentStart, '{', '}') ?: code.lastIndex
            code.substring(contentStart + 1, close)
        } else {
            code.substring(contentStart, code.indexOf('\n', contentStart).let { end ->
                if (end < 0) code.length else end
            })
        }
    }.toList()
}

private fun webInlineStorageFaultStageViolations(code: String): List<String> = buildList {
    val expectedFailureNames = linkedMapOf(
        "webStorageRead" to listOf("SecurityError"),
        "webStorageWrite" to listOf("SecurityError", "QuotaExceededError"),
        "webStorageRemove" to listOf("SecurityError"),
    )
    expectedFailureNames.forEach { (functionName, expectedNames) ->
        val body = topLevelDeclarationSlice(code, "private fun $functionName")
        if (body == null) {
            add("Core §6.13 Web fault-stage violation: private `$functionName` helper is missing")
            return@forEach
        }
        listOf(
            "try {",
            "catch (failure) {",
            "typeof DOMException !== 'undefined'",
            "failure instanceof DOMException",
            "return 'failed-before-execution'",
            "throw failure;",
        ).filterNot { token ->
            token in body ||
                token == "return 'failed-before-execution'" &&
                "return { status: 'failed-before-execution', payload: null };" in body
        }.forEach { token ->
            add("Core §6.13 Web fault-stage violation: `$functionName` is missing `$token`")
        }
        val actualNames = Regex("failure\\.name\\s*===\\s*'([^']+)'")
            .findAll(body)
            .map { match -> match.groupValues[1] }
            .toList()
        if (actualNames != expectedNames) {
            add(
                "Core §6.13 Web fault-stage violation: `$functionName` must classify exactly " +
                    "${expectedNames.joinToString()}; found ${actualNames.joinToString()}",
            )
        }
        val classifierIndex = body.lastIndexOf("failure.name ===")
        val rethrowIndex = body.indexOf("throw failure;")
        if (classifierIndex < 0 || rethrowIndex <= classifierIndex) {
            add(
                "Core §6.13 Web fault-stage violation: `$functionName` must rethrow every " +
                    "unclassified JavaScript/programming fault after exact DOM classification",
            )
        }
        val catchCount = Regex("\\bcatch\\s*\\(failure\\)\\s*\\{").findAll(body).count()
        if (catchCount != 1) {
            add(
                "Core §6.13 Web fault-stage violation: `$functionName` must contain exactly one " +
                    "classified provider catch; found $catchCount",
            )
        }
    }
}

private fun topLevelDeclarationSlice(code: String, declaration: String): String? {
    val start = code.indexOf(declaration)
    if (start < 0) return null
    val next = Regex(
        "(?m)^(?:private|internal|public)\\s+(?:actual\\s+)?" +
            "(?:class|object|interface|external\\s+interface|fun|const\\s+val|val|var)\\b",
    ).find(code, start + declaration.length)?.range?.first ?: code.length
    return code.substring(start, next)
}

private fun String.indexOfFirstFrom(startIndex: Int, predicate: (Char) -> Boolean): Int {
    for (index in startIndex until length) {
        if (predicate(this[index])) return index
    }
    return -1
}

private fun closingDelimiter(text: String, open: Int, opening: Char, closing: Char): Int? {
    var depth = 0
    for (index in open until text.length) {
        when (text[index]) {
            opening -> depth += 1
            closing -> {
                depth -= 1
                if (depth == 0) return index
            }
        }
    }
    return null
}

private fun String.squashWhitespace(): String = trim().replace(Regex("\\s+"), " ")

private fun String.withoutKotlinComments(): String {
    val output = StringBuilder(length)
    var index = 0
    var blockDepth = 0
    var inLineComment = false
    var quote: Char? = null
    var tripleQuoted = false
    var escaped = false
    while (index < length) {
        val character = this[index]
        val next = getOrNull(index + 1)
        when {
            inLineComment -> {
                if (character == '\n') {
                    inLineComment = false
                    output.append(character)
                } else {
                    output.append(' ')
                }
            }
            blockDepth > 0 -> when {
                character == '/' && next == '*' -> {
                    blockDepth += 1
                    output.append("  ")
                    index += 1
                }
                character == '*' && next == '/' -> {
                    blockDepth -= 1
                    output.append("  ")
                    index += 1
                }
                character == '\n' -> output.append(character)
                else -> output.append(' ')
            }
            quote != null -> {
                output.append(character)
                if (tripleQuoted) {
                    if (character == quote && substring(index, minOf(length, index + 3)) == "$quote$quote$quote") {
                        output.append("$quote$quote")
                        index += 2
                        quote = null
                        tripleQuoted = false
                    }
                } else if (escaped) {
                    escaped = false
                } else if (character == '\\') {
                    escaped = true
                } else if (character == quote) {
                    quote = null
                }
            }
            character == '/' && next == '/' -> {
                inLineComment = true
                output.append("  ")
                index += 1
            }
            character == '/' && next == '*' -> {
                blockDepth = 1
                output.append("  ")
                index += 1
            }
            character == '"' || character == '\'' -> {
                quote = character
                tripleQuoted = character == '"' && substring(index, minOf(length, index + 3)) == "\"\"\""
                output.append(character)
                if (tripleQuoted) {
                    output.append("\"\"")
                    index += 2
                }
            }
            else -> output.append(character)
        }
        index += 1
    }
    return output.toString()
}

private val outputFamilySources = linkedMapOf(
    "ProfileOutput" to
        "ball/profile/nucleus/src/commonMain/kotlin/kinetickk/ball/profile/nucleus/ProfileDecision.kt",
    "GameplayOutput" to
        "ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/GameplayDecision.kt",
    "AppSessionOutput" to
        "flow/session/nucleus/src/commonMain/kotlin/kinetickk/flow/session/nucleus/AppSessionDecision.kt",
)

internal fun compositionLimitViolations(
    sources: Map<String, SourceDocument>,
    policy: String,
    assembly: String,
): List<String> = buildList {
    val calculated = staticCumulativeFanoutCeiling(
        SameStackCumulativeFanoutPolicy.MAX_OUTPUTS_PER_ACCEPTED_DECISION,
        SameStackCumulativeFanoutPolicy.MAX_CONSUMERS_PER_OUTPUT,
        SameStackCumulativeFanoutPolicy.acceptedCausalDepths,
    )
    if (calculated != SameStackCumulativeFanoutPolicy.MAX_CUMULATIVE_FANOUT) {
        add(
            "Static cumulative fan-out proof drift: calculated $calculated, declared " +
                SameStackCumulativeFanoutPolicy.MAX_CUMULATIVE_FANOUT,
        )
    }
    if (SameStackCumulativeFanoutPolicy.HAS_ASYNC_HANDOFF) {
        add("Same-stack cumulative fan-out policy must declare no asynchronous handoff")
    }

    listOf(
        "| cumulative fan-out per accepted root causal scope | 9840 |",
        "`maxCumulativeFanout=9840`",
        "accepted causal depths `0..7`",
        "`3^1 + 3^2 + ... + 3^8 = 9840`",
        "No runtime fan-out meter",
        "No asynchronous semantic handoff exists",
    ).filterNot(policy::contains).forEach { token ->
        add("Cumulative fan-out policy is missing exact contract `$token`")
    }

    listOf(
        "one accepted root causal scope",
        "complete accepted source tuple",
        "effective route and consumer/executor",
        "Terminal branches count",
        "co-reachable branches",
        "converging",
        "Mutually exclusive alternatives",
        "Retry or redelivery of the same source tuple",
        "independent root",
        "No asynchronous semantic handoff exists",
    ).filterNot(assembly::contains).forEach { token ->
        add("Assembly cumulative fan-out contract is missing `$token`")
    }

    val rows = parseArchitectureTableRows(assembly, "## Closed semantic output executors")
    val rowsById = rows.groupBy(ArchitectureTableRow::id)
    val expectedIds = outputExecutorInventory.map(OutputExecutorProjection::id).toSet()
    if (rows.map(ArchitectureTableRow::id).toSet() != expectedIds) {
        add("Assembly output/executor table must equal the closed output variant inventory")
    }
    rowsById.filterValues { matches -> matches.size != 1 }.forEach { (id, matches) ->
        add("Assembly output/executor row `$id` appears ${matches.size} times")
    }
    outputExecutorInventory.forEach { projection ->
        val executorText = sources[projection.executorPath]?.text
        projection.requiredTokens.filter { token -> executorText == null || token !in executorText }
            .forEach { token ->
                add(
                    "Output `${projection.outputVariant}` executor is missing `$token` in " +
                        projection.executorPath,
                )
            }
        val row = rowsById[projection.id]?.singleOrNull()
        if (row != null) {
            listOf(projection.outputVariant, projection.effectiveRoute, projection.consumerOrExecutor)
                .filterNot(row.text::contains)
                .forEach { token ->
                    add("Assembly output/executor row `${projection.id}` is missing `$token`")
                }
        }
    }

    outputExecutorInventory.groupBy(OutputExecutorProjection::outputVariant)
        .forEach { (variant, projections) ->
            val resolvedMaximumConsumers = projections
                .groupBy { projection -> projection.mutualExclusionGroup ?: "always:${projection.id}" }
                .values
                .sumOf { reservation ->
                    if (reservation.singleOrNull()?.mutualExclusionGroup == null) {
                        1
                    } else {
                        reservation.groupingBy(OutputExecutorProjection::alternative)
                            .eachCount()
                            .values
                            .maxOrNull() ?: 0
                    }
                }
            if (resolvedMaximumConsumers != SameStackCumulativeFanoutPolicy.MAX_CONSUMERS_PER_OUTPUT) {
                add(
                    "Output `$variant` resolves $resolvedMaximumConsumers conditional consumers; " +
                        "expected exactly one",
                )
            }
            if (projections.size == 1) {
                val projection = projections.single()
                if (projection.mutualExclusionGroup != null || projection.alternative != null) {
                    add("Single-consumer output `$variant` must not invent a mutual-exclusion reservation")
                }
            } else {
                val groups = projections.map(OutputExecutorProjection::mutualExclusionGroup).toSet()
                val alternatives = projections.map(OutputExecutorProjection::alternative)
                if (null in groups || groups.size != 1 || null in alternatives || alternatives.toSet().size != projections.size) {
                    add(
                        "Multi-route output `$variant` must declare one mutual-exclusion group and " +
                            "one distinct alternative per conditional effective consumer",
                    )
                }
            }
        }

    outputFamilySources.forEach { (family, path) ->
        val declared = sources[path]?.text?.let { declaredOutputVariants(it, family) }.orEmpty()
        val inventoried = outputExecutorInventory.asSequence()
            .map(OutputExecutorProjection::outputVariant)
            .filter { variant -> variant.startsWith("$family.") }
            .map { variant -> variant.substringAfter('.') }
            .toSet()
        if (declared != inventoried) {
            add(
                "$family output/executor closure drift: declared ${declared.sorted().joinToString()}, " +
                    "inventoried ${inventoried.sorted().joinToString()}",
            )
        }
    }
}.distinct().sorted()

private fun declaredOutputVariants(text: String, family: String): Set<String> {
    val body = text.substringAfter("sealed interface $family", missingDelimiterValue = "")
    if (body.isEmpty()) return emptySet()
    return Regex("(?m)^\\s{4}(?:data\\s+class|data\\s+object)\\s+(\\w+)")
        .findAll(body)
        .map { match -> match.groupValues[1] }
        .toSet()
}
