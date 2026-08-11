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
        val arbitraryWebOriginAndKeys = mutate(WEB_PATH) { source ->
            source.replace(
                "private data class WebProfilePersistenceKeys",
                "internal data class WebProfilePersistenceKeys",
            ) + "\n" +
                """

                internal fun mintCapability(
                    storage: Storage,
                    keys: WebProfilePersistenceKeys,
                ): ProfilePersistenceCapability = WebProfilePersistenceCapability({ storage }, keys)
                """.trimIndent()
        }

        val outsideViolations = platformCapabilityBoundaryViolations(validCapabilitySources() + outsideBroker)
        assertViolation(outsideViolations, "java.util.prefs.Preferences")
        assertViolation(outsideViolations, "javax.sound.sampled.AudioSystem")
        assertViolation(platformCapabilityBoundaryViolations(arbitraryDesktopNodes), "Broad platform handle")
        assertViolation(platformCapabilityBoundaryViolations(arbitraryWebOriginAndKeys), "private immutable")
        assertViolation(platformCapabilityBoundaryViolations(arbitraryWebOriginAndKeys), "Broad platform handle")
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
                "storage().removeItem(keys.legacyMatter)",
                "storage().clear()",
            )
        }
        val extraNarrowOperation = mutate(PROFILE_FACTORY_PATH_FIXTURE) { source ->
            source.replace(
                "fun removeLegacyMatter()",
                "fun removeLegacyMatter()\n    fun clearAll()",
            )
        }

        assertViolation(platformCapabilityBoundaryViolations(changedPhysicalKey), "Physical profile persistence constants")
        assertViolation(platformCapabilityBoundaryViolations(bulkClear), "Broad platform operation `clear`")
        assertViolation(platformCapabilityBoundaryViolations(extraNarrowOperation), "must expose exactly")
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
                interface ExactProfilePersistence {
                    fun readV4(): String?
                    fun writeV4(payload: String)
                    fun readLegacyProgressV2(): String?
                    fun readLegacyMatter(): String?
                    fun removeLegacyProgressV2()
                    fun removeLegacyMatter()
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
                interface ProfilePersistenceCapability {
                    fun readV4(): String?
                    fun writeV4(payload: String)
                    fun readLegacyProgressV2(): String?
                    fun readLegacyMatter(): String?
                    fun removeLegacyProgressV2()
                    fun removeLegacyMatter()
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

                private fun TonePlaybackCapability.playSafely(request: ToneRequest) = play(request)
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
                override fun readV4(): String? =
                    profileNode().get(ProfilePersistenceContract.DESKTOP_SNAPSHOT_V4, null)

                override fun writeV4(payload: String) {
                    profileNode().apply {
                        put(ProfilePersistenceContract.DESKTOP_SNAPSHOT_V4, payload)
                        flush()
                    }
                }

                override fun readLegacyProgressV2(): String? =
                    legacyNode().get(ProfilePersistenceContract.DESKTOP_LEGACY_PROGRESS_V2, null)

                override fun readLegacyMatter(): String? =
                    legacyNode().get(ProfilePersistenceContract.DESKTOP_LEGACY_MATTER, null)

                override fun removeLegacyProgressV2() {
                    legacyNode().apply {
                        remove(ProfilePersistenceContract.DESKTOP_LEGACY_PROGRESS_V2)
                        flush()
                    }
                }

                override fun removeLegacyMatter() {
                    legacyNode().apply {
                        remove(ProfilePersistenceContract.DESKTOP_LEGACY_MATTER)
                        flush()
                    }
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
            import kotlinx.browser.localStorage
            import org.w3c.dom.Storage
            import kotlin.js.JsAny
            import kinetickk.ball.profile.impl.ProfilePersistenceCapability
            import kinetickk.resource.audio.impl.TonePlaybackCapability

            internal actual fun createPlatformProfilePersistenceCapability(): ProfilePersistenceCapability =
                WebProfilePersistenceCapability(
                    storage = { localStorage },
                    keys = WEB_PROFILE_PERSISTENCE_KEYS,
                )

            private data class WebProfilePersistenceKeys(
                val snapshotV4: String,
                val legacyProgressV2: String,
                val legacyMatter: String,
            )

            private val WEB_PROFILE_PERSISTENCE_KEYS = WebProfilePersistenceKeys(
                snapshotV4 = ProfilePersistenceContract.WEB_SNAPSHOT_V4,
                legacyProgressV2 = ProfilePersistenceContract.WEB_LEGACY_PROGRESS_V2,
                legacyMatter = ProfilePersistenceContract.WEB_LEGACY_MATTER,
            )

            private class WebProfilePersistenceCapability(
                private val storage: () -> Storage,
                private val keys: WebProfilePersistenceKeys,
            ) : ProfilePersistenceCapability {
                override fun readV4(): String? = storage().getItem(keys.snapshotV4)

                override fun writeV4(payload: String) {
                    storage().setItem(keys.snapshotV4, payload)
                }

                override fun readLegacyProgressV2(): String? =
                    storage().getItem(keys.legacyProgressV2)

                override fun readLegacyMatter(): String? =
                    storage().getItem(keys.legacyMatter)

                override fun removeLegacyProgressV2() {
                    storage().removeItem(keys.legacyProgressV2)
                }

                override fun removeLegacyMatter() {
                    storage().removeItem(keys.legacyMatter)
                }
            }

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
