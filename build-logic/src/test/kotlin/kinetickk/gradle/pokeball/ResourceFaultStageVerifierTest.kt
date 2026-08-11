// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle.pokeball

import kotlin.test.Test
import kotlin.test.assertTrue

class ResourceFaultStageVerifierTest {
    @Test
    fun explicitClosedProviderOutcomesAtPlatformBoundaryPass() {
        val sources = listOf(
            SourceDocument(
                PROFILE_RESOURCE_PATH,
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

                    fun mapRead(result: ProfileProviderReadResult): ProfileBootstrapResourceResult =
                        when (result) {
                            is ProfileProviderReadResult.Observed -> observed(result.payload)
                            ProfileProviderReadResult.Failed ->
                                ProfileBootstrapResourceResult.ResourceFailure(PROVIDER_READ_FAILED)
                        }

                    fun mapWrite(result: ProfileProviderMutationResult): ProfileV4WriteResult =
                        when (result) {
                            ProfileProviderMutationResult.COMPLETED -> written()
                            ProfileProviderMutationResult.FAILED_BEFORE_EXECUTION ->
                                ProfileV4WriteResult.ResourceFailure(PROVIDER_WRITE_FAILED_BEFORE_EXECUTION)
                            ProfileProviderMutationResult.POSSIBLE_EXECUTION ->
                                ProfileV4WriteResult.OutcomeUnknown(PROVIDER_WRITE_MAY_HAVE_EXECUTED)
                        }

                    fun mapPurge(result: ProfileProviderMutationResult): ProfileLegacyPurgeResult =
                        when (result) {
                            ProfileProviderMutationResult.COMPLETED -> ProfileLegacyPurgeResult.Purged
                            ProfileProviderMutationResult.FAILED_BEFORE_EXECUTION ->
                                ProfileLegacyPurgeResult.Partial(knownPresentKeys)
                            ProfileProviderMutationResult.POSSIBLE_EXECUTION ->
                                ProfileLegacyPurgeResult.OutcomeUnknown(remaining, unknown, reason)
                        }
                """.trimIndent(),
            ),
            SourceDocument(
                DESKTOP_PLATFORM_PATH,
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

                    fun read(): ProfilePersistenceReadResult = try {
                        ProfilePersistenceReadResult.Observed(provider.get())
                    } catch (_: SecurityException) {
                        ProfilePersistenceReadResult.Failed
                    } catch (_: IllegalStateException) {
                        ProfilePersistenceReadResult.Failed
                    }

                    fun write(): ProfilePersistenceMutationResult = try {
                        provider.put()
                        ProfilePersistenceMutationResult.COMPLETED
                    } catch (_: IllegalArgumentException) {
                        ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
                    } catch (_: BackingStoreException) {
                        ProfilePersistenceMutationResult.POSSIBLE_EXECUTION
                    }
                """.trimIndent(),
            ),
            SourceDocument(
                WEB_PLATFORM_PATH,
                """
                    fun read(): ProfilePersistenceReadResult = try {
                        ProfilePersistenceReadResult.Observed(storage.getItem(key))
                    } catch (failure: kotlin.js.JsException) {
                        if (isWebStorageReadFailure(failure.thrownValue)) {
                            ProfilePersistenceReadResult.Failed
                        } else {
                            throw failure
                        }
                    }
                """.trimIndent(),
            ),
        )

        val violations = resourceFaultStageViolations(sources)

        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    @Test
    fun broadRuntimeFaultTypesCannotBeConvertedInsideProfileResource() {
        listOf("Throwable", "Exception", "RuntimeException", "Error").forEach { catchType ->
            val source = SourceDocument(
                PROFILE_RESOURCE_PATH,
                """
                    fun readBootstrap(): ProfileBootstrapResourceResult = try {
                        provider.read()
                    } catch (_: $catchType) {
                        ProfileBootstrapResourceResult.ResourceFailure(PROVIDER_READ_FAILED)
                    }
                """.trimIndent(),
            )

            val violations = resourceFaultStageViolations(listOf(source))

            assertViolation(violations, "broad `$catchType` catch")
            assertViolation(violations, "ResourceFailure")
        }
    }

    @Test
    fun profileImplCannotTurnWholeResourceFaultIntoOutcomeUnknown() {
        val source = SourceDocument(
            PROFILE_IMPL_PATH,
            """
                fun execute(): ProfileV4WriteResult = try {
                    resource.writeV4(snapshot)
                } catch (_: Throwable) {
                    ProfileV4WriteResult.OutcomeUnknown(PROVIDER_WRITE_MAY_HAVE_EXECUTED)
                }
            """.trimIndent(),
        )

        val violations = resourceFaultStageViolations(listOf(source))

        assertViolation(violations, "Core §6.13")
        assertViolation(violations, "OutcomeUnknown")
        assertViolation(violations, "$PROFILE_IMPL_PATH:3")
    }

    @Test
    fun broadPlatformCatchCannotMasqueradeAsClosedProviderOutcome() {
        val source = SourceDocument(
            DESKTOP_PLATFORM_PATH,
            """
                fun read(): ProfilePersistenceReadResult = try {
                    ProfilePersistenceReadResult.Observed(provider.get())
                } catch (_: Throwable) {
                    ProfilePersistenceReadResult.Failed
                }
            """.trimIndent(),
        )

        val violations = resourceFaultStageViolations(listOf(source))

        assertViolation(violations, "broad `Throwable` catch")
        assertViolation(violations, "explicit closed provider outcomes")
    }

    @Test
    fun unclassifiedJavaScriptExceptionCannotBecomeProviderFailure() {
        val source = SourceDocument(
            WEB_PLATFORM_PATH,
            """
                fun read(): ProfilePersistenceReadResult = try {
                    ProfilePersistenceReadResult.Observed(storage.getItem(key))
                } catch (_: kotlin.js.JsException) {
                    ProfilePersistenceReadResult.Failed
                }
            """.trimIndent(),
        )

        val violations = resourceFaultStageViolations(listOf(source))

        assertViolation(violations, "classify the exact DOM storage failure")
        assertViolation(violations, "rethrow all other JavaScript/programming faults")
    }

    @Test
    fun inlineWebStorageCatchesWhitelistExactDomFailuresAndRethrowEverythingElse() {
        val source = SourceDocument(WEB_PLATFORM_PATH, validInlineWebStorageFaultFixture())

        val violations = resourceFaultStageViolations(listOf(source))

        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    @Test
    fun inlineWebStorageCatchAllOrExpandedWhitelistFails() {
        val catchAll = SourceDocument(
            WEB_PLATFORM_PATH,
            validInlineWebStorageFaultFixture().replace("throw failure;", "return 'failed-before-execution';"),
        )
        val expandedWhitelist = SourceDocument(
            WEB_PLATFORM_PATH,
            validInlineWebStorageFaultFixture().replace(
                "failure.name === 'SecurityError'",
                "failure.name === 'SecurityError' || failure.name === 'TypeError'",
            ),
        )

        assertViolation(resourceFaultStageViolations(listOf(catchAll)), "rethrow every unclassified")
        assertViolation(resourceFaultStageViolations(listOf(expandedWhitelist)), "must classify exactly")
    }

    @Test
    fun knownPreExecutionProviderFailureCannotCollapseToPossibleExecution() {
        val desktop = SourceDocument(
            DESKTOP_PLATFORM_PATH,
            """
                enum class ProfilePersistenceMutationResult {
                    COMPLETED,
                    FAILED_BEFORE_EXECUTION,
                    POSSIBLE_EXECUTION,
                }

                fun write(): ProfilePersistenceMutationResult = try {
                    provider.put()
                    ProfilePersistenceMutationResult.COMPLETED
                } catch (_: IllegalArgumentException) {
                    ProfilePersistenceMutationResult.POSSIBLE_EXECUTION
                }
            """.trimIndent(),
        )
        val resource = SourceDocument(
            PROFILE_RESOURCE_PATH,
            """
                fun map(result: ProfileProviderMutationResult): ProfileV4WriteResult = when (result) {
                    ProfileProviderMutationResult.COMPLETED -> written()
                    ProfileProviderMutationResult.FAILED_BEFORE_EXECUTION ->
                        ProfileV4WriteResult.OutcomeUnknown(PROVIDER_WRITE_MAY_HAVE_EXECUTED)
                    ProfileProviderMutationResult.POSSIBLE_EXECUTION ->
                        ProfileV4WriteResult.OutcomeUnknown(PROVIDER_WRITE_MAY_HAVE_EXECUTED)
                }
            """.trimIndent(),
        )

        val violations = resourceFaultStageViolations(listOf(desktop, resource))

        assertViolation(violations, "known before provider execution")
        assertViolation(violations, "`FAILED_BEFORE_EXECUTION` cannot map to `OutcomeUnknown`")
    }

    @Test
    fun providerEvidenceConversionFailsAtAnyProductionBoundary() {
        val source = SourceDocument(
            "ball/gameplay/impl/src/commonMain/kotlin/fixture/GameplayResourceBoundary.kt",
            """
                fun execute(): GameplayResourceResult = try {
                    provider.execute()
                } catch (_: Throwable) {
                    GameplayResourceResult.OutcomeUnknown(PROVIDER_MAY_HAVE_EXECUTED)
                }
            """.trimIndent(),
        )

        val violations = resourceFaultStageViolations(listOf(source))

        assertViolation(violations, "OutcomeUnknown")
    }

    @Test
    fun auditedResourceBoundaryCannotHideBroadFaultCaptureBehindRunCatching() {
        val source = SourceDocument(
            "ball/gameplay/resource/src/commonMain/kotlin/fixture/GameplayResource.kt",
            """
                fun execute(): GameplayResourceResult = runCatching {
                    provider.execute()
                }.getOrElse {
                    GameplayResourceResult.ResourceFailure(PROVIDER_FAILED)
                }
            """.trimIndent(),
        )

        val violations = resourceFaultStageViolations(listOf(source))

        assertViolation(violations, "`runCatching`")
        assertViolation(violations, "audited Resource boundary")
    }

    @Test
    fun sameStackFaultPreservationMayRethrowTheOriginalFault() {
        val source = SourceDocument(
            PROFILE_IMPL_PATH,
            """
                fun dispatch() {
                    try {
                        target.accept()
                    } catch (failure: Throwable) {
                        drainAlreadyAcceptedResult()
                        throw failure
                    }
                }
            """.trimIndent(),
        )

        val violations = resourceFaultStageViolations(listOf(source))

        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    private fun assertViolation(violations: List<String>, token: String) {
        assertTrue(violations.any { token in it }, violations.joinToString("\n"))
    }

    private fun validInlineWebStorageFaultFixture(): String =
        """
            private fun webStorageRead() {
                try {
                    globalThis.localStorage
                } catch (failure) {
                    if (typeof DOMException !== 'undefined' && failure instanceof DOMException &&
                        failure.name === 'SecurityError'
                    ) {
                        return { status: 'failed-before-execution', payload: null };
                    }
                    throw failure;
                }
            }

            private fun webStorageWrite() {
                try {
                    globalThis.localStorage
                } catch (failure) {
                    if (typeof DOMException !== 'undefined' && failure instanceof DOMException &&
                        (failure.name === 'SecurityError' || failure.name === 'QuotaExceededError')
                    ) {
                        return 'failed-before-execution';
                    }
                    throw failure;
                }
            }

            private fun webStorageRemove() {
                try {
                    globalThis.localStorage
                } catch (failure) {
                    if (typeof DOMException !== 'undefined' && failure instanceof DOMException &&
                        failure.name === 'SecurityError'
                    ) {
                        return 'failed-before-execution';
                    }
                    throw failure;
                }
            }
        """.trimIndent()

    private companion object {
        const val PROFILE_RESOURCE_PATH =
            "ball/profile/resource/src/commonMain/kotlin/kinetickk/ball/profile/resource/ProfileStorage.kt"
        const val PROFILE_IMPL_PATH =
            "ball/profile/impl/src/commonMain/kotlin/kinetickk/ball/profile/impl/DefaultProfileComponent.kt"
        const val DESKTOP_PLATFORM_PATH =
            "app/shared/src/desktopMain/kotlin/kinetickk/app/shared/PlatformCapabilities.desktop.kt"
        const val WEB_PLATFORM_PATH =
            "app/shared/src/wasmJsMain/kotlin/kinetickk/app/shared/PlatformCapabilities.wasm.kt"
    }
}
