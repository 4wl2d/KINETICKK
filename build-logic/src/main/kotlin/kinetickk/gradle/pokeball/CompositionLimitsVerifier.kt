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
            "fun readV4(): String?",
            "fun writeV4(payload: String)",
            "fun readLegacyProgressV2(): String?",
            "fun readLegacyMatter(): String?",
            "fun removeLegacyProgressV2()",
            "fun removeLegacyMatter()",
            "fun createProfileResource(",
            "persistence: ExactProfilePersistence",
            "private class FixedKeyProfileResource",
        ),
    )
    requireTokens(
        PROFILE_FACTORY_PATH,
        listOf(
            "interface ProfilePersistenceCapability",
            "fun readV4(): String?",
            "fun writeV4(payload: String)",
            "fun readLegacyProgressV2(): String?",
            "fun readLegacyMatter(): String?",
            "fun removeLegacyProgressV2()",
            "fun removeLegacyMatter()",
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
            "private data class WebProfilePersistenceKeys",
            "storage = { localStorage }",
            "keys = WEB_PROFILE_PERSISTENCE_KEYS",
            "private val WEB_PROFILE_PERSISTENCE_KEYS",
            "snapshotV4 = ProfilePersistenceContract.WEB_SNAPSHOT_V4",
            "legacyProgressV2 = ProfilePersistenceContract.WEB_LEGACY_PROGRESS_V2",
            "legacyMatter = ProfilePersistenceContract.WEB_LEGACY_MATTER",
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
    addAll(platformFactoryCallsiteViolations(production, codeByPath))
    addAll(closedCapabilityTypeInventoryViolations(production, codeByPath))

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

    listOf("clear", "removeNode", "keys", "childrenNames", "systemRoot").forEach { operation ->
        if (Regex("(?:\\.|\\bPreferences\\.)\\s*${Regex.escape(operation)}\\s*\\(").containsMatchIn(code)) {
            add("Broad platform operation `$operation` is forbidden in the exact app broker: $path")
        }
    }

    when (path) {
        DESKTOP_PLATFORM_BROKER_PATH -> addAll(desktopBrokerSourceViolations(code))
        WEB_PLATFORM_BROKER_PATH -> addAll(webBrokerSourceViolations(code))
        else -> add("Unrecognized platform broker path $path")
    }
}

private fun desktopBrokerSourceViolations(code: String): List<String> = buildList {
    requireWordCount(code, "Preferences", 5, DESKTOP_PLATFORM_BROKER_PATH, this)
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
                setOf("profileNode", "legacyNode", "get", "put", "remove", "flush", "apply"),
            exactCallCounts = mapOf(
                "profileNode" to 2,
                "legacyNode" to 4,
                "get" to 3,
                "put" to 1,
                "remove" to 2,
                "flush" to 3,
                "apply" to 3,
            ),
            exactIdentifierCounts = mapOf(
                "profileNode" to 2,
                "legacyNode" to 4,
            ),
            requiredExpressions = listOf(
                "profileNode().get(ProfilePersistenceContract.DESKTOP_SNAPSHOT_V4, null)",
                "put(ProfilePersistenceContract.DESKTOP_SNAPSHOT_V4, payload)",
                "legacyNode().get(ProfilePersistenceContract.DESKTOP_LEGACY_PROGRESS_V2, null)",
                "legacyNode().get(ProfilePersistenceContract.DESKTOP_LEGACY_MATTER, null)",
                "remove(ProfilePersistenceContract.DESKTOP_LEGACY_PROGRESS_V2)",
                "remove(ProfilePersistenceContract.DESKTOP_LEGACY_MATTER)",
            ),
        ),
    )
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

private fun webBrokerSourceViolations(code: String): List<String> = buildList {
    requireWordCount(code, "localStorage", 2, WEB_PLATFORM_BROKER_PATH, this)
    requireWordCount(code, "Storage", 2, WEB_PLATFORM_BROKER_PATH, this)
    requireWordCount(code, "JsAny", 7, WEB_PLATFORM_BROKER_PATH, this)
    requireWordCount(code, "globalThis", 4, WEB_PLATFORM_BROKER_PATH, this)
    if ("request.wave.ordinal" in code ||
        Regex("\\[[^]]*(?:sine|square|sawtooth|triangle)[^]]*]\\s*\\[\\s*wave\\s*]")
            .containsMatchIn(code)
    ) {
        add("Web tone mapping may not depend on ToneWave ordinal or enum cardinality")
    }
    requireRegexCount(
        code,
        Regex("storage\\s*=\\s*\\{\\s*localStorage\\s*}"),
        1,
        "fixed web storage acquisition",
        WEB_PLATFORM_BROKER_PATH,
        this,
    )
    requireRegexCount(
        code,
        Regex(
            "actual\\s+fun\\s+createPlatformProfilePersistenceCapability\\s*\\(\\s*\\)\\s*:\\s*" +
                "ProfilePersistenceCapability\\s*=\\s*WebProfilePersistenceCapability\\s*\\(",
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
    val allowedGlobalRead =
        "const AudioContext = globalThis.AudioContext || globalThis.webkitAudioContext;"
    val globalLines = code.lineSequence().filter { "globalThis" in it }.map(String::trim).toList()
    if (globalLines != listOf(allowedGlobalRead, allowedGlobalRead)) {
        add(
            "Web broker globalThis access must be exactly two private AudioContext constructor reads; " +
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
    val keyFields = primaryConstructorPropertyNames(code, "WebProfilePersistenceKeys")
    if (keyFields != listOf("snapshotV4", "legacyProgressV2", "legacyMatter")) {
        add(
            "Web profile persistence key inventory must be exactly snapshotV4, legacyProgressV2, " +
                "legacyMatter; found ${keyFields.joinToString()}",
        )
    }
    if ("private data class WebProfilePersistenceKeys" !in code) {
        add("Web profile persistence keys must be a private immutable production inventory")
    }
    addAll(
        closedPersistenceBrokerViolations(
            path = WEB_PLATFORM_BROKER_PATH,
            code = code,
            declaration = "private class WebProfilePersistenceCapability",
            allowedCalls = exactPersistenceOperations + setOf("storage", "getItem", "setItem", "removeItem"),
            exactCallCounts = mapOf(
                "storage" to 6,
                "getItem" to 3,
                "setItem" to 1,
                "removeItem" to 2,
            ),
            exactIdentifierCounts = mapOf(
                "storage" to 6,
                "keys" to 6,
            ),
            requiredExpressions = listOf(
                "storage().getItem(keys.snapshotV4)",
                "storage().setItem(keys.snapshotV4, payload)",
                "storage().getItem(keys.legacyProgressV2)",
                "storage().getItem(keys.legacyMatter)",
                "storage().removeItem(keys.legacyProgressV2)",
                "storage().removeItem(keys.legacyMatter)",
            ),
        ),
    )
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
    val calls = Regex("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\(|\\{)")
        .findAll(body)
        .map { match -> match.groupValues[1] }
        .toList()
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
