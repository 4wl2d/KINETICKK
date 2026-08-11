// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle.pokeball

import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformCapabilityBoundaryVerifierTest {
    @Test
    fun exactPrivateBrokersAndAssemblyOnlyBindingsPass() {
        val violations = platformCapabilityBoundaryViolations(validCapabilitySources())

        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    @Test
    fun broadAuthorityOutsideBrokerAndInternalConstructionSeamsFail() {
        val outsideBroker = SourceDocument(
            "ball/profile/resource/src/desktopMain/kotlin/fixture/BroadDesktopAuthority.kt",
            """
                import java.util.prefs.Preferences
                import javax.sound.sampled.AudioSystem

                fun createProfileResource(preferences: Preferences) = AudioSystem to preferences
            """.trimIndent(),
        )
        val arbitraryDesktopNodes = mutate(DESKTOP_PATH) { source ->
            source + "\n" +
                """

                internal fun mintCapability(
                    profileNode: Preferences,
                    legacyNode: Preferences,
                ): ProfilePersistenceCapability = DesktopProfilePersistenceCapability(
                    profileNode = { profileNode },
                    legacyNode = { legacyNode },
                )
                """.trimIndent()
        }
        val arbitraryWebOrigin = mutate(WEB_PATH) { source ->
            source + "\n" +
                """

                internal fun mintCapability(
                    storage: Storage,
                ): ProfilePersistenceCapability = WebProfilePersistenceCapability()
                """.trimIndent()
        }

        val outsideViolations = platformCapabilityBoundaryViolations(validCapabilitySources() + outsideBroker)
        assertViolation(outsideViolations, "java.util.prefs.Preferences")
        assertViolation(outsideViolations, "javax.sound.sampled.AudioSystem")
        assertViolation(platformCapabilityBoundaryViolations(arbitraryDesktopNodes), "Broad platform handle")
        assertViolation(platformCapabilityBoundaryViolations(arbitraryWebOrigin), "Broad platform handle")
    }

    @Test
    fun broadHandleReturnsAndApplicationSurfaceForwardingFail() {
        val executorEscape = mutate(DESKTOP_PATH) { source ->
            source + "\ninternal fun leakedExecutor(): ThreadPoolExecutor = ThreadPoolExecutor()"
        }
        val storageEscape = mutate(WEB_PATH) { source ->
            source + "\ninternal fun leakedStorage(storage: Storage): Storage = storage"
        }
        val resourceForwarding = SourceDocument(
            "resource/audio/impl/src/wasmJsMain/kotlin/fixture/ForwardedStorage.kt",
            """
                import org.w3c.dom.Storage

                class ForwardedStorage(private val storage: Storage)
            """.trimIndent(),
        )

        assertViolation(platformCapabilityBoundaryViolations(executorEscape), "ThreadPoolExecutor handle")
        assertViolation(platformCapabilityBoundaryViolations(storageEscape), "Broad platform handle")
        assertViolation(
            platformCapabilityBoundaryViolations(validCapabilitySources() + resourceForwarding),
            "org.w3c.dom.Storage",
        )
    }

    @Test
    fun arbitraryKeysBulkOperationsAndExtraCapabilityOperationsFail() {
        val changedPhysicalKey = mutate(PROFILE_FACTORY_PATH_FIXTURE) { source ->
            source.replace("kinetickk_profile_v4", "caller_selected_profile")
        }
        val bulkClear = mutate(WEB_PATH) { source ->
            source.replace(
                "exactStorage.removeItem(key);",
                "exactStorage.clear();",
            )
        }
        val extraNarrowOperation = mutate(PROFILE_FACTORY_PATH_FIXTURE) { source ->
            source.replace(
                "fun removeLegacyMatter(): ProfilePersistenceMutationResult",
                "fun removeLegacyMatter(): ProfilePersistenceMutationResult\n    fun clearAll()",
            )
        }

        assertViolation(platformCapabilityBoundaryViolations(changedPhysicalKey), "Physical profile persistence constants")
        assertViolation(platformCapabilityBoundaryViolations(bulkClear), "Broad platform operation `clear`")
        assertViolation(platformCapabilityBoundaryViolations(extraNarrowOperation), "must expose exactly")
    }

    @Test
    fun mutationOutcomeInventoriesAreClosedAndRetainKnownPreExecutionFailure() {
        val missingCapabilityOutcome = mutate(PROFILE_FACTORY_PATH_FIXTURE) { source ->
            source.replace("    FAILED_BEFORE_EXECUTION,\n", "")
        }
        val missingProviderOutcome = mutate(PROFILE_RESOURCE_PATH_FIXTURE) { source ->
            source.replace("    FAILED_BEFORE_EXECUTION,\n", "")
        }

        assertViolation(
            platformCapabilityBoundaryViolations(missingCapabilityOutcome),
            "ProfilePersistenceMutationResult",
        )
        assertViolation(
            platformCapabilityBoundaryViolations(missingProviderOutcome),
            "ProfileProviderMutationResult",
        )
    }

    @Test
    fun desktopKeyEnumerationIsBoundedInsideOnlyTheExactReadHelper() {
        val extraEnumeration = mutate(DESKTOP_PATH) { source ->
            source + "\nprivate fun leakedKeys(node: Preferences): Array<String> = node.keys()"
        }
        val movedAdmissionAfterIteration = mutate(DESKTOP_PATH) { source ->
            source.replace(
                "desktopPreferenceKeyCountAdmission(storedKeys.size)?.let { return it }\n" +
                    "    val keyIsPresent = storedKeys.any { storedKey -> storedKey == exactKey }",
                "val keyIsPresent = storedKeys.any { storedKey -> storedKey == exactKey }\n" +
                    "    desktopPreferenceKeyCountAdmission(storedKeys.size)?.let { return it }",
            )
        }

        assertViolation(
            platformCapabilityBoundaryViolations(extraEnumeration),
            "Direct Desktop Preferences `keys()` calls",
        )
        assertViolation(
            platformCapabilityBoundaryViolations(movedAdmissionAfterIteration),
            "before project-owned membership iteration",
        )
    }

    @Test
    fun desktopCallbackSeamsRemainStageExactAndHaveNoOtherProductionCallers() {
        val collapsedFlushFailure = mutate(DESKTOP_PATH) { source ->
            source.replace(
                "} catch (_: BackingStoreException) {\n" +
                    "        ProfilePersistenceMutationResult.POSSIBLE_EXECUTION",
                "} catch (_: BackingStoreException) {\n" +
                    "        ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION",
            )
        }
        val movedValueAdmission = mutate(DESKTOP_PATH) { source ->
            source.replace(
                "desktopProfilePayloadAdmission(payload.length)?.let { return it }",
                "profileNode().also { desktopProfilePayloadAdmission(payload.length) }",
            )
        }
        val extraProductionCaller = SourceDocument(
            "app/shared/src/desktopMain/kotlin/fixture/ExtraPersistenceSeamCaller.kt",
            "fun extra() = desktopProfileReadCall(exactKey, loadKeys, loadValue)",
        )

        assertViolation(
            platformCapabilityBoundaryViolations(collapsedFlushFailure),
            "BackingStoreException",
        )
        assertViolation(
            platformCapabilityBoundaryViolations(movedValueAdmission),
            "before profile-node/provider acquisition",
        )
        assertViolation(
            platformCapabilityBoundaryViolations(validCapabilitySources() + extraProductionCaller),
            "must have exactly 0 production calls",
        )
    }

    @Test
    fun alternateGlobalCachesAndNonPrivateJsHandlesFail() {
        val namedGlobalCache = mutate(WEB_PATH) { source ->
            source.replace(
                "const AudioContext = globalThis.AudioContext || globalThis.webkitAudioContext;",
                "globalThis.audioAuthorityCache = current;",
            )
        }
        val kotlinObjectCache = mutate(WEB_PATH) { source ->
            source + "\n" +
                """

                private object AudioAuthorityRegistry {
                    private var cached: JsAny? = null
                }
                """.trimIndent()
        }
        val alternateGlobal = mutate(WEB_PATH) { source ->
            source.replace("globalThis.AudioContext", "window.AudioContext")
        }

        assertViolation(platformCapabilityBoundaryViolations(namedGlobalCache), "globalThis access")
        assertViolation(platformCapabilityBoundaryViolations(kotlinObjectCache), "never a top-level/object cache")
        assertViolation(platformCapabilityBoundaryViolations(alternateGlobal), "alternate ambient global")
    }

    @Test
    fun webToneMappingMayNotDependOnEnumOrdinal() {
        val ordinalMapping = mutate(WEB_PATH) { source ->
            source.replace("webToneWaveValue(request.wave)", "request.wave.ordinal")
        }

        assertViolation(platformCapabilityBoundaryViolations(ordinalMapping), "enum cardinality")
    }

    @Test
    fun platformFactoryUseOutsideStaticAssemblyFailsEvenWhenTheReturnTypeIsNarrow() {
        val hiddenLocator = SourceDocument(
            "app/shared/src/commonMain/kotlin/kinetickk/app/shared/HiddenCapabilityLocator.kt",
            """
                package kinetickk.app.shared

                internal object HiddenCapabilityLocator {
                    val profile = createPlatformProfilePersistenceCapability()
                    val audio = createPlatformTonePlaybackCapability()
                }
            """.trimIndent(),
        )
        val cachedNarrowAuthority = SourceDocument(
            "app/shared/src/commonMain/kotlin/kinetickk/app/shared/HiddenCapabilityCache.kt",
            """
                package kinetickk.app.shared

                internal object HiddenCapabilityCache {
                    lateinit var audio: TonePlaybackCapability
                }
            """.trimIndent(),
        )
        val violations = platformCapabilityBoundaryViolations(
            validCapabilitySources() + hiddenLocator + cachedNarrowAuthority,
        )

        assertViolation(violations, "must occur exactly 0 times")
        assertViolation(violations, "authority cache, alias, or forwarding seam")
    }

    private fun mutate(path: String, transform: (String) -> String): List<SourceDocument> =
        validCapabilitySources().map { source ->
            if (source.relativePath == path) source.copy(text = transform(source.text)) else source
        }

    private fun assertViolation(violations: List<String>, token: String) {
        assertTrue(violations.any { token in it }, violations.joinToString("\n"))
    }

    private fun validCapabilitySources(): List<SourceDocument> = listOf(
        SourceDocument(
            PROFILE_RESOURCE_PATH_FIXTURE,
            """
                sealed interface ProfileProviderReadResult {
                    data class Observed(val payload: String?) : ProfileProviderReadResult
                    data object Failed : ProfileProviderReadResult
                }

                enum class ProfileProviderMutationResult {
                    COMPLETED,
                    FAILED_BEFORE_EXECUTION,
                    POSSIBLE_EXECUTION,
                }

                interface ExactProfilePersistence {
                    fun readV4(): ProfileProviderReadResult
                    fun writeV4(payload: String): ProfileProviderMutationResult
                    fun readLegacyProgressV2(): ProfileProviderReadResult
                    fun readLegacyMatter(): ProfileProviderReadResult
                    fun removeLegacyProgressV2(): ProfileProviderMutationResult
                    fun removeLegacyMatter(): ProfileProviderMutationResult
                }

                fun createProfileResource(
                    persistence: ExactProfilePersistence,
                ) = FixedKeyProfileResource(persistence)

                private class FixedKeyProfileResource(
                    persistence: ExactProfilePersistence,
                )
            """.trimIndent(),
        ),
        SourceDocument(
            PROFILE_FACTORY_PATH_FIXTURE,
            """
                sealed interface ProfilePersistenceReadResult {
                    data class Observed(val payload: String?) : ProfilePersistenceReadResult
                    data object Failed : ProfilePersistenceReadResult
                }

                enum class ProfilePersistenceMutationResult {
                    COMPLETED,
                    FAILED_BEFORE_EXECUTION,
                    POSSIBLE_EXECUTION,
                }

                interface ProfilePersistenceCapability {
                    fun readV4(): ProfilePersistenceReadResult
                    fun writeV4(payload: String): ProfilePersistenceMutationResult
                    fun readLegacyProgressV2(): ProfilePersistenceReadResult
                    fun readLegacyMatter(): ProfilePersistenceReadResult
                    fun removeLegacyProgressV2(): ProfilePersistenceMutationResult
                    fun removeLegacyMatter(): ProfilePersistenceMutationResult
                }

                fun createProfileComponent(persistence: ProfilePersistenceCapability) = persistence

                object ProfilePersistenceContract {
                    const val DESKTOP_PROFILE_NODE: String = "kinetickk/profile"
                    const val DESKTOP_SNAPSHOT_V4: String = "snapshot_v4"
                    const val DESKTOP_LEGACY_NODE: String = "kinetickk/progression"
                    const val DESKTOP_LEGACY_PROGRESS_V2: String = "progress_v2"
                    const val DESKTOP_LEGACY_MATTER: String = "kinetickk_matter"
                    const val WEB_SNAPSHOT_V4: String = "kinetickk_profile_v4"
                    const val WEB_LEGACY_PROGRESS_V2: String = "kinetickk_progress_v2"
                    const val WEB_LEGACY_MATTER: String = "kinetickk_matter"
                }

                private class ProfilePersistenceAdapter(
                    capability: ProfilePersistenceCapability,
                ) : ExactProfilePersistence
            """.trimIndent(),
        ),
        SourceDocument(
            AUDIO_RESOURCE_PATH_FIXTURE,
            """
                interface TonePlaybackCapability {
                    fun unlock()
                    fun play(request: ToneRequest)
                    fun close()
                }

                class DefaultAudioService(
                    private val platform: TonePlaybackCapability,
                )

                private fun TonePlaybackCapability.playIfAllowed(request: ToneRequest) = play(request)
            """.trimIndent(),
        ),
        SourceDocument(
            APP_COMPOSITION_PATH,
            """
                import kinetickk.ball.profile.impl.ProfilePersistenceCapability
                import kinetickk.resource.audio.impl.TonePlaybackCapability

                internal class AppCompositionOwner {
                    private val profile = createProfileComponent(
                        persistence = createPlatformProfilePersistenceCapability(),
                    )
                    private val audio = DefaultAudioService(
                        createPlatformTonePlaybackCapability(),
                    )
                }

                internal expect fun createPlatformProfilePersistenceCapability(): ProfilePersistenceCapability
                internal expect fun createPlatformTonePlaybackCapability(): TonePlaybackCapability
            """.trimIndent(),
        ),
        SourceDocument(DESKTOP_PATH, desktopBrokerFixture()),
        SourceDocument(WEB_PATH, webBrokerFixture()),
    )

    private fun desktopBrokerFixture(): String =
        """
            import java.util.concurrent.ThreadPoolExecutor
            import java.util.prefs.Preferences
            import javax.sound.sampled.AudioSystem
            import kinetickk.ball.profile.impl.ProfilePersistenceCapability
            import kinetickk.ball.profile.impl.ProfilePersistenceMutationResult
            import kinetickk.ball.profile.impl.ProfilePersistenceReadResult
            import kinetickk.resource.audio.impl.TonePlaybackCapability

            internal actual fun createPlatformProfilePersistenceCapability(): ProfilePersistenceCapability =
                DesktopProfilePersistenceCapability(
                    profileNode = {
                        Preferences.userRoot().node(ProfilePersistenceContract.DESKTOP_PROFILE_NODE)
                    },
                    legacyNode = {
                        Preferences.userRoot().node(ProfilePersistenceContract.DESKTOP_LEGACY_NODE)
                    },
                )

            private class DesktopProfilePersistenceCapability(
                private val profileNode: () -> Preferences,
                private val legacyNode: () -> Preferences,
            ) : ProfilePersistenceCapability {
                override fun readV4(): ProfilePersistenceReadResult {
                    val node = try {
                        profileNode()
                    } catch (_: SecurityException) {
                        return ProfilePersistenceReadResult.Failed
                    } catch (_: IllegalStateException) {
                        return ProfilePersistenceReadResult.Failed
                    }
                    return desktopProfileReadCall(
                        exactKey = ProfilePersistenceContract.DESKTOP_SNAPSHOT_V4,
                        loadKeyNames = node::keys,
                        loadExactValue = {
                            node.get(ProfilePersistenceContract.DESKTOP_SNAPSHOT_V4, null)
                        },
                    )
                }

                override fun writeV4(payload: String): ProfilePersistenceMutationResult {
                    desktopProfilePayloadAdmission(payload.length)?.let { return it }
                    val node = try {
                        profileNode()
                    } catch (_: SecurityException) {
                        return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
                    } catch (_: IllegalStateException) {
                        return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
                    }
                    return desktopProfileMutationCall(
                        mutate = {
                            node.put(ProfilePersistenceContract.DESKTOP_SNAPSHOT_V4, payload)
                        },
                        flush = node::flush,
                    )
                }

                override fun readLegacyProgressV2(): ProfilePersistenceReadResult {
                    val node = try {
                        legacyNode()
                    } catch (_: SecurityException) {
                        return ProfilePersistenceReadResult.Failed
                    } catch (_: IllegalStateException) {
                        return ProfilePersistenceReadResult.Failed
                    }
                    return desktopProfileReadCall(
                        exactKey = ProfilePersistenceContract.DESKTOP_LEGACY_PROGRESS_V2,
                        loadKeyNames = node::keys,
                        loadExactValue = {
                            node.get(ProfilePersistenceContract.DESKTOP_LEGACY_PROGRESS_V2, null)
                        },
                    )
                }

                override fun readLegacyMatter(): ProfilePersistenceReadResult {
                    val node = try {
                        legacyNode()
                    } catch (_: SecurityException) {
                        return ProfilePersistenceReadResult.Failed
                    } catch (_: IllegalStateException) {
                        return ProfilePersistenceReadResult.Failed
                    }
                    return desktopProfileReadCall(
                        exactKey = ProfilePersistenceContract.DESKTOP_LEGACY_MATTER,
                        loadKeyNames = node::keys,
                        loadExactValue = {
                            node.get(ProfilePersistenceContract.DESKTOP_LEGACY_MATTER, null)
                        },
                    )
                }

                override fun removeLegacyProgressV2(): ProfilePersistenceMutationResult {
                    val node = try {
                        legacyNode()
                    } catch (_: SecurityException) {
                        return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
                    } catch (_: IllegalStateException) {
                        return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
                    }
                    return desktopProfileMutationCall(
                        mutate = {
                            node.remove(ProfilePersistenceContract.DESKTOP_LEGACY_PROGRESS_V2)
                        },
                        flush = node::flush,
                    )
                }

                override fun removeLegacyMatter(): ProfilePersistenceMutationResult {
                    val node = try {
                        legacyNode()
                    } catch (_: SecurityException) {
                        return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
                    } catch (_: IllegalStateException) {
                        return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
                    }
                    return desktopProfileMutationCall(
                        mutate = {
                            node.remove(ProfilePersistenceContract.DESKTOP_LEGACY_MATTER)
                        },
                        flush = node::flush,
                    )
                }
            }

            internal fun desktopProfileReadCall(
                exactKey: String,
                loadKeyNames: () -> Array<String>,
                loadExactValue: () -> String?,
            ): ProfilePersistenceReadResult {
                val storedKeys = try {
                    loadKeyNames()
                } catch (_: BackingStoreException) {
                    return ProfilePersistenceReadResult.Failed
                } catch (_: SecurityException) {
                    return ProfilePersistenceReadResult.Failed
                } catch (_: IllegalStateException) {
                    return ProfilePersistenceReadResult.Failed
                }
                desktopPreferenceKeyCountAdmission(storedKeys.size)?.let { return it }
                val keyIsPresent = storedKeys.any { storedKey -> storedKey == exactKey }
                if (!keyIsPresent) {
                    return ProfilePersistenceReadResult.Observed(null)
                }
                val payload = try {
                    loadExactValue()
                } catch (_: SecurityException) {
                    return ProfilePersistenceReadResult.Failed
                } catch (_: IllegalStateException) {
                    return ProfilePersistenceReadResult.Failed
                }
                return if (payload == null) {
                    ProfilePersistenceReadResult.Failed
                } else {
                    ProfilePersistenceReadResult.Observed(payload)
                }
            }

            internal fun desktopProfileMutationCall(
                mutate: () -> Unit,
                flush: () -> Unit,
            ): ProfilePersistenceMutationResult {
                try {
                    mutate()
                } catch (_: IllegalStateException) {
                    return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
                } catch (_: IllegalArgumentException) {
                    return ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
                }
                return try {
                    flush()
                    ProfilePersistenceMutationResult.COMPLETED
                } catch (_: BackingStoreException) {
                    ProfilePersistenceMutationResult.POSSIBLE_EXECUTION
                }
            }

            internal const val MAX_DESKTOP_PREFERENCE_KEYS_PER_NODE: Int = 64

            internal fun desktopPreferenceKeyCountAdmission(keyCount: Int): ProfilePersistenceReadResult? =
                if (keyCount <= MAX_DESKTOP_PREFERENCE_KEYS_PER_NODE) null else ProfilePersistenceReadResult.Failed

            internal fun desktopProfilePayloadAdmission(valueLength: Int): ProfilePersistenceMutationResult? {
                require(valueLength >= 0)
                return if (valueLength <= Preferences.MAX_VALUE_LENGTH) {
                    null
                } else {
                    ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
                }
            }

            internal actual fun createPlatformTonePlaybackCapability(): TonePlaybackCapability =
                DesktopTonePlaybackCapability()

            private class DesktopTonePlaybackCapability : TonePlaybackCapability {
                private val executor = ThreadPoolExecutor(
                    ThreadPoolExecutor.DiscardOldestPolicy(),
                )

                override fun unlock() = Unit

                override fun play(request: ToneRequest) {
                    if (executor.isShutdown) return
                    executor.execute { }
                }

                override fun close() {
                    executor.shutdownNow()
                }

                private fun synthesize(format: AudioFormat) {
                    AudioSystem.getSourceDataLine(format).use { line ->
                        line.open()
                        line.start()
                        line.write()
                        line.drain()
                    }
                }
            }
        """.trimIndent()

    private fun webBrokerFixture(): String =
        """
            import kotlin.js.JsAny
            import kinetickk.ball.profile.impl.ProfilePersistenceCapability
            import kinetickk.ball.profile.impl.ProfilePersistenceMutationResult
            import kinetickk.ball.profile.impl.ProfilePersistenceReadResult
            import kinetickk.resource.audio.impl.TonePlaybackCapability

            internal actual fun createPlatformProfilePersistenceCapability(): ProfilePersistenceCapability =
                WebProfilePersistenceCapability()

            private class WebProfilePersistenceCapability : ProfilePersistenceCapability {
                override fun readV4(): ProfilePersistenceReadResult = readWebProfileV4()
                override fun writeV4(payload: String): ProfilePersistenceMutationResult =
                    writeWebProfileV4(payload)
                override fun readLegacyProgressV2(): ProfilePersistenceReadResult = readWebLegacyProgressV2()
                override fun readLegacyMatter(): ProfilePersistenceReadResult = readWebLegacyMatter()
                override fun removeLegacyProgressV2(): ProfilePersistenceMutationResult =
                    removeWebLegacyProgressV2()
                override fun removeLegacyMatter(): ProfilePersistenceMutationResult = removeWebLegacyMatter()
            }

            private external interface WebStorageReadCall : JsAny {
                val status: String
                val payload: String?
            }

            private fun readWebProfileV4(): ProfilePersistenceReadResult =
                webStorageRead(ProfilePersistenceContract.WEB_SNAPSHOT_V4).toPersistenceResult()

            private fun readWebLegacyProgressV2(): ProfilePersistenceReadResult =
                webStorageRead(ProfilePersistenceContract.WEB_LEGACY_PROGRESS_V2).toPersistenceResult()

            private fun readWebLegacyMatter(): ProfilePersistenceReadResult =
                webStorageRead(ProfilePersistenceContract.WEB_LEGACY_MATTER).toPersistenceResult()

            private fun writeWebProfileV4(payload: String): ProfilePersistenceMutationResult =
                webStorageWrite(ProfilePersistenceContract.WEB_SNAPSHOT_V4, payload).toPersistenceMutationResult()

            private fun removeWebLegacyProgressV2(): ProfilePersistenceMutationResult =
                webStorageRemove(ProfilePersistenceContract.WEB_LEGACY_PROGRESS_V2).toPersistenceMutationResult()

            private fun removeWebLegacyMatter(): ProfilePersistenceMutationResult =
                webStorageRemove(ProfilePersistenceContract.WEB_LEGACY_MATTER).toPersistenceMutationResult()

            private fun WebStorageReadCall.toPersistenceResult(): ProfilePersistenceReadResult = when (status) {
                WEB_STORAGE_OBSERVED -> ProfilePersistenceReadResult.Observed(payload)
                WEB_STORAGE_FAILED_BEFORE_EXECUTION -> ProfilePersistenceReadResult.Failed
                else -> error("Web Storage read returned an unknown provider status")
            }

            private fun String.toPersistenceMutationResult(): ProfilePersistenceMutationResult = when (this) {
                WEB_STORAGE_COMPLETED -> ProfilePersistenceMutationResult.COMPLETED
                WEB_STORAGE_FAILED_BEFORE_EXECUTION ->
                    ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
                else -> error("Web Storage mutation returned an unknown provider status")
            }

            private fun webStorageRead(key: String): WebStorageReadCall = js(
                ${"\"\"\""}{
                    try {
                        const exactStorage = globalThis.localStorage;
                        return { status: 'observed', payload: exactStorage.getItem(key) };
                    } catch (failure) {
                        if (
                            typeof DOMException !== 'undefined' &&
                            failure instanceof DOMException &&
                            failure.name === 'SecurityError'
                        ) {
                            return { status: 'failed-before-execution', payload: null };
                        }
                        throw failure;
                    }
                }${"\"\"\""},
            )

            private fun webStorageWrite(key: String, payload: String): String = js(
                ${"\"\"\""}{
                    try {
                        const exactStorage = globalThis.localStorage;
                        exactStorage.setItem(key, payload);
                        return 'completed';
                    } catch (failure) {
                        if (
                            typeof DOMException !== 'undefined' &&
                            failure instanceof DOMException &&
                            (failure.name === 'SecurityError' || failure.name === 'QuotaExceededError')
                        ) {
                            return 'failed-before-execution';
                        }
                        throw failure;
                    }
                }${"\"\"\""},
            )

            private fun webStorageRemove(key: String): String = js(
                ${"\"\"\""}{
                    try {
                        const exactStorage = globalThis.localStorage;
                        exactStorage.removeItem(key);
                        return 'completed';
                    } catch (failure) {
                        if (
                            typeof DOMException !== 'undefined' &&
                            failure instanceof DOMException &&
                            failure.name === 'SecurityError'
                        ) {
                            return 'failed-before-execution';
                        }
                        throw failure;
                    }
                }${"\"\"\""},
            )

            private const val WEB_STORAGE_OBSERVED: String = "observed"
            private const val WEB_STORAGE_COMPLETED: String = "completed"
            private const val WEB_STORAGE_FAILED_BEFORE_EXECUTION: String = "failed-before-execution"

            internal actual fun createPlatformTonePlaybackCapability(): TonePlaybackCapability =
                WebTonePlaybackCapability()

            private class WebTonePlaybackCapability : TonePlaybackCapability {
                private var context: JsAny? = null

                override fun unlock() {
                    context = unlockWebAudio(context)
                }

                override fun play(request: ToneRequest) {
                    context = playWebTone(context, webToneWaveValue(request.wave))
                }

                override fun close() {
                    closeWebAudio(context)
                    context = null
                }
            }

            internal fun webToneWaveValue(wave: ToneWave): String = when (wave) {
                ToneWave.SINE -> "sine"
                ToneWave.SQUARE -> "square"
                ToneWave.SAW -> "sawtooth"
                ToneWave.TRIANGLE -> "triangle"
            }

            private fun unlockWebAudio(current: JsAny?): JsAny? {
                const AudioContext = globalThis.AudioContext || globalThis.webkitAudioContext;
            }

            private fun playWebTone(current: JsAny?): JsAny? {
                const AudioContext = globalThis.AudioContext || globalThis.webkitAudioContext;
                oscillator.type = wave;
            }

            private fun closeWebAudio(current: JsAny?): Unit = Unit
        """.trimIndent()

    private companion object {
        const val PROFILE_RESOURCE_PATH_FIXTURE =
            "ball/profile/resource/src/commonMain/kotlin/kinetickk/ball/profile/resource/ProfileStorage.kt"
        const val PROFILE_FACTORY_PATH_FIXTURE =
            "ball/profile/impl/src/commonMain/kotlin/kinetickk/ball/profile/impl/ProfileComponentFactory.kt"
        const val AUDIO_RESOURCE_PATH_FIXTURE =
            "resource/audio/impl/src/commonMain/kotlin/kinetickk/resource/audio/impl/DefaultAudioService.kt"
        const val APP_COMPOSITION_PATH =
            "app/shared/src/commonMain/kotlin/kinetickk/app/shared/AppComposition.kt"
        const val DESKTOP_PATH =
            "app/shared/src/desktopMain/kotlin/kinetickk/app/shared/PlatformCapabilities.desktop.kt"
        const val WEB_PATH =
            "app/shared/src/wasmJsMain/kotlin/kinetickk/app/shared/PlatformCapabilities.wasm.kt"
    }
}
