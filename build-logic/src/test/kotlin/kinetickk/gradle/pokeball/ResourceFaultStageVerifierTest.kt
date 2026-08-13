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

                    fun mapWrite(result: ProfileProviderMutationResult): ProfileWriteResult =
                        when (result) {
                            ProfileProviderMutationResult.COMPLETED -> written()
                            ProfileProviderMutationResult.FAILED_BEFORE_EXECUTION ->
                                ProfileWriteResult.ResourceFailure(PROVIDER_WRITE_FAILED_BEFORE_EXECUTION)
                            ProfileProviderMutationResult.POSSIBLE_EXECUTION ->
                                ProfileWriteResult.OutcomeUnknown(PROVIDER_WRITE_MAY_HAVE_EXECUTED)
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

        val violations = resourceFaultStageFixtureViolations(sources)

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

            val violations = resourceFaultStageFixtureViolations(listOf(source))

            assertViolation(violations, "broad `$catchType` catch")
            assertViolation(violations, "ResourceFailure")
        }
    }

    @Test
    fun profileImplCannotTurnWholeResourceFaultIntoOutcomeUnknown() {
        val source = SourceDocument(
            PROFILE_IMPL_PATH,
            """
                fun execute(): ProfileWriteResult = try {
                    resource.writeSnapshot(snapshot)
                } catch (_: Throwable) {
                    ProfileWriteResult.OutcomeUnknown(PROVIDER_WRITE_MAY_HAVE_EXECUTED)
                }
            """.trimIndent(),
        )

        val violations = resourceFaultStageFixtureViolations(listOf(source))

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

        val violations = resourceFaultStageFixtureViolations(listOf(source))

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

        val violations = resourceFaultStageFixtureViolations(listOf(source))

        assertViolation(violations, "classify the exact DOM storage failure")
        assertViolation(violations, "rethrow all other JavaScript/programming faults")
    }

    @Test
    fun inlineWebStorageCatchesWhitelistExactDomFailuresAndRethrowEverythingElse() {
        val source = SourceDocument(WEB_PLATFORM_PATH, validInlineWebStorageFaultFixture())

        val violations = resourceFaultStageFixtureViolations(listOf(source))

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

        assertViolation(resourceFaultStageFixtureViolations(listOf(catchAll)), "rethrow every unclassified")
        assertViolation(resourceFaultStageFixtureViolations(listOf(expandedWhitelist)), "must classify exactly")
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
                fun map(result: ProfileProviderMutationResult): ProfileWriteResult = when (result) {
                    ProfileProviderMutationResult.COMPLETED -> written()
                    ProfileProviderMutationResult.FAILED_BEFORE_EXECUTION ->
                        ProfileWriteResult.OutcomeUnknown(PROVIDER_WRITE_MAY_HAVE_EXECUTED)
                    ProfileProviderMutationResult.POSSIBLE_EXECUTION ->
                        ProfileWriteResult.OutcomeUnknown(PROVIDER_WRITE_MAY_HAVE_EXECUTED)
                }
            """.trimIndent(),
        )

        val violations = resourceFaultStageFixtureViolations(listOf(desktop, resource))

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

        val violations = resourceFaultStageFixtureViolations(listOf(source))

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

        val violations = resourceFaultStageFixtureViolations(listOf(source))

        assertViolation(violations, "`runCatching`")
        assertViolation(violations, "audited Resource boundary")
    }

    @Test
    fun sameStackFaultPreservationMayRethrowTheOriginalFault() {
        val sources = listOf(
            PROFILE_RESOURCE_PATH,
            PROFILE_IMPL_PATH,
        ).map { path ->
            SourceDocument(
                path,
                """
                    fun dispatch() {
                        try {
                            target.accept()
                        } catch (failure: Throwable) {
                            throw failure
                        }
                    }
                """.trimIndent(),
            )
        }

        sources.forEach { source ->
            val violations = resourceFaultStageFixtureViolations(listOf(source))

            assertTrue(violations.isEmpty(), violations.joinToString("\n"))
        }
    }

    @Test
    fun sameStackFaultPreservationMayDeferTheFirstFaultUntilAfterDrain() {
        val source = SourceDocument(
            PROFILE_IMPL_PATH,
            """
                fun dispatchLocal() = guard.dispatch {
                    var deferredFault: Throwable? = null
                    while (!completions.isEmpty) {
                        val item = checkNotNull(completions.removeFirstOrNull())
                        val before = committedState
                        when (val decision = ProfileNucleus.decide(before, item.pulse)) {
                            is ProfileDecision.Rejected -> Unit
                            is ProfileDecision.Accepted -> {
                                for (output in decision.frame.outputs) {
                                    try {
                                        this.execute(output, item)
                                    } catch (failure: Throwable) {
                                        if (deferredFault == null) deferredFault = failure
                                    }
                                }
                            }
                        }
                        root = false
                    }
                    val failure = deferredFault
                    if (failure != null) throw failure
                }
            """.trimIndent(),
        )

        val violations = resourceFaultStageFixtureViolations(listOf(source))

        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    @Test
    fun acceptedBranchPreludePinsPreflightBeforeCommitAndRejectsExtraWork() {
        val local = """
            preflight(before, item, decision.frame)
            committedState = decision.frame.nextState
            if (root) {
                rootAcceptance = ProfileAcceptance.Accepted(
                    instanceId = committedState.instanceId,
                    revision = committedState.revision,
                )
            }
        """.trimIndent()
        val command = """
            if (root && deepestReservedLevel(item, decision.frame) >= MAX_PROFILE_CAUSAL_DEPTH) {
                activeCommandRoute = null
                return@dispatch refused(
                    commandSource = pulse.commandSource,
                    effectiveProtocolIdentity = pulse.effectiveProtocolIdentity,
                    response = causalBudgetFailure(pulse.commandSource),
                )
            }
            preflight(before, item, decision.frame)
            committedState = decision.frame.nextState
            if (root) acceptedTargetRevision = committedState.revision
        """.trimIndent()

        assertTrue(exactProfileAcceptedBranchPrelude("dispatchLocal", local))
        assertTrue(exactProfileAcceptedBranchPrelude("dispatchCommand", command))
        assertTrue(
            !exactProfileAcceptedBranchPrelude(
                "dispatchLocal",
                local.replace(
                    "preflight(before, item, decision.frame)\n" +
                        "committedState = decision.frame.nextState",
                    "committedState = decision.frame.nextState\n" +
                        "preflight(before, item, decision.frame)",
                ),
            ),
        )
        assertTrue(
            !exactProfileAcceptedBranchPrelude(
                "dispatchLocal",
                local.replace(
                    "committedState = decision.frame.nextState",
                    "committedState = decision.frame.nextState\nreplay(decision, item)",
                ),
            ),
        )
    }

    @Test
    fun deferredFaultMustRemainFirstAndReachRethrowAfterDrain() {
        val valid = """
            fun dispatchLocal() = guard.dispatch {
                var deferredFault: Throwable? = null
                while (!completions.isEmpty) {
                    val item = checkNotNull(completions.removeFirstOrNull())
                        val before = committedState
                    when (val decision = ProfileNucleus.decide(before, item.pulse)) {
                        is ProfileDecision.Rejected -> Unit
                        is ProfileDecision.Accepted -> {
                            for (output in decision.frame.outputs) {
                                try {
                                    this.execute(output, item)
                                } catch (failure: Throwable) {
                                    if (deferredFault == null) deferredFault = failure
                                }
                            }
                        }
                    }
                    root = false
                }
                val failure = deferredFault
                if (failure != null) throw failure
            }
        """.trimIndent()
        val rethrow = "val failure = deferredFault\n    if (failure != null) throw failure"
        val invalidSources = listOf(
            valid.replace(rethrow, "consume(deferredFault)"),
            valid.replace(
                "if (deferredFault == null) deferredFault = failure",
                "deferredFault = failure",
            ),
            valid.replace(
                "if (deferredFault == null) deferredFault = failure",
                "throw failure",
            ),
            valid.replace(
                rethrow,
                "if (skipFault) return@dispatch\n    $rethrow",
            ),
            valid.replace(
                rethrow,
                "val neverInvoked = { $rethrow }",
            ),
            valid.replace(
                rethrow,
                "if (false) { $rethrow }",
            ),
            valid.replace(
                rethrow,
                "\"${'$'}{run { throw IllegalStateException() }}\"\n    $rethrow",
            ),
            valid.replace(
                Regex(
                    """val\s+item\s*=\s*checkNotNull\s*\(\s*completions\.removeFirstOrNull\s*""" +
                        """\(\s*\)\s*\)\s*val\s+before\s*=\s*committedState""",
                ),
                "val neverInvoked = { completions.removeFirstOrNull() }\n" +
                    "        val item = checkNotNull(completions.removeFirstOrNull())\n" +
                    "        val before = committedState",
            ),
            valid.replace(
                "for (output in decision.frame.outputs) {",
                "if (shouldAbort()) return@dispatch\n" +
                    "        for (output in decision.frame.outputs) {",
            ),
            """
                fun dispatchLocal() = guard.dispatch {
                    var deferredFault: Throwable? = null
                    val failure = deferredFault
                    if (failure != null) throw failure
                    while (!completions.isEmpty) {
                        val item = checkNotNull(completions.removeFirstOrNull())
                        val before = committedState
                        for (output in decision.frame.outputs) {
                            try {
                                this.execute(output, item)
                            } catch (failure: Throwable) {
                                if (deferredFault == null) deferredFault = failure
                            }
                        }
                    }
                }
            """.trimIndent(),
            """
                fun dispatchLocal() = guard.dispatch {
                    var deferredFault: Throwable? = null
                    drain@ while (!completions.isEmpty) {
                        val item = checkNotNull(completions.removeFirstOrNull())
                        val before = committedState
                        for (output in decision.frame.outputs) {
                            try {
                                this.execute(output, item)
                            } catch (failure: Throwable) {
                                if (deferredFault == null) deferredFault = failure
                            }
                            continue@drain
                        }
                    }
                    val failure = deferredFault
                    if (failure != null) throw failure
                }
            """.trimIndent(),
        )

        invalidSources.forEachIndexed { index, code ->
            val violations = resourceFaultStageFixtureViolations(
                listOf(SourceDocument(PROFILE_IMPL_PATH, code)),
            )
            assertTrue(
                violations.any { violation -> "broad `Throwable` catch" in violation },
                "case $index unexpectedly passed:\n$code\n${violations.joinToString("\n")}",
            )
        }
    }

    @Test
    fun deferredCommandAdmissionReturnsMustBelongToTheirExecutedBranches() {
        val valid = """
            fun dispatchCommand() = guard.dispatch {
                var deferredFault: Throwable? = null
                while (!completions.isEmpty) {
                    val item = checkNotNull(completions.removeFirstOrNull())
                        val before = committedState
                    when (val decision = ProfileNucleus.decide(before, item.pulse)) {
                        is ProfileDecision.Rejected -> {
                            check(root) { "rejected: " + decision.reason }
                            activeCommandRoute = null
                            return@dispatch refused(
                                response = ProfileCommandBoundaryResponse.DecisionRejected(reason),
                            )
                        }
                        is ProfileDecision.Accepted -> {
                            if (root && deepestReservedLevel(item, decision.frame) >= MAX_PROFILE_CAUSAL_DEPTH) {
                                activeCommandRoute = null
                                return@dispatch refused(
                                    response = causalBudgetFailure(source),
                                )
                            }
                            for (output in decision.frame.outputs) {
                                try {
                                    this.execute(output, item)
                                } catch (failure: Throwable) {
                                    if (deferredFault == null) deferredFault = failure
                                }
                            }
                        }
                    }
                    root = false
                }
                val failure = deferredFault
                if (failure != null) throw failure
            }
        """.trimIndent()
        val validViolations = resourceFaultStageFixtureViolations(
            listOf(SourceDocument(PROFILE_IMPL_PATH, valid)),
        )
        assertTrue(validViolations.isEmpty(), validViolations.joinToString("\n"))

        val drifted = valid
            .replace("return@dispatch refused(", "refused(")
            .replace(
                "for (output in decision.frame.outputs) {",
                """
                    val fakeRejected = dispatch@ {
                        return@dispatch refused(
                            response = ProfileCommandBoundaryResponse.DecisionRejected(reason),
                        )
                    }
                    val fakeBudget = dispatch@ {
                        return@dispatch refused(response = causalBudgetFailure(source))
                    }
                    for (output in decision.frame.outputs) {
                """.trimIndent(),
            )
        val violations = resourceFaultStageFixtureViolations(
            listOf(SourceDocument(PROFILE_IMPL_PATH, drifted)),
        )

        assertViolation(violations, "broad `Throwable` catch")

        val conditional = valid.replace(
            "return@dispatch refused(",
            "if (false) return@dispatch refused(",
        )
        val conditionalViolations = resourceFaultStageFixtureViolations(
            listOf(SourceDocument(PROFILE_IMPL_PATH, conditional)),
        )
        assertViolation(conditionalViolations, "broad `Throwable` catch")

        val unreachable = valid.replace(
            "activeCommandRoute = null",
            "check(false)\n                activeCommandRoute = null",
        )
        val unreachableViolations = resourceFaultStageFixtureViolations(
            listOf(SourceDocument(PROFILE_IMPL_PATH, unreachable)),
        )
        assertViolation(unreachableViolations, "broad `Throwable` catch")
    }

    @Test
    fun profileDispatchesRequireTheCanonicalDeferredAcceptedOutputDrain() {
        val missingDispatches = """
            internal class RenamedProfileComponent {
                fun drainLocal() {
                    for (output in decision.frame.outputs) { this.execute(output, item) }
                }
            }
        """.trimIndent()
        val missingViolations = resourceFaultStageViolations(
            listOf(SourceDocument(PROFILE_IMPL_PATH, missingDispatches)),
        )
        assertViolation(missingViolations, "canonical accepted-output drain is missing")

        val canonical = """
            fun dispatchLocal() = guard.dispatch {
                var deferredFault: Throwable? = null
                while (!completions.isEmpty) {
                    val item = checkNotNull(completions.removeFirstOrNull())
                        val before = committedState
                    when (val decision = ProfileNucleus.decide(before, item.pulse)) {
                        is ProfileDecision.Rejected -> Unit
                        is ProfileDecision.Accepted -> {
                            for (output in decision.frame.outputs) {
                                try {
                                    this.execute(output, item)
                                } catch (failure: Throwable) {
                                    if (deferredFault == null) deferredFault = failure
                                }
                            }
                        }
                    }
                    root = false
                }
                val failure = deferredFault
                if (failure != null) throw failure
            }
        """.trimIndent()
        val drifts = listOf(
            canonical.replace(
                Regex(
                    """try\s*\{\s*this\.execute\s*\(\s*output\s*,\s*item\s*\)\s*\}\s*""" +
                        """catch\s*\(\s*failure\s*:\s*Throwable\s*\)\s*\{\s*""" +
                        """if\s*\(\s*deferredFault\s*==\s*null\s*\)\s*""" +
                        """deferredFault\s*=\s*failure\s*\}""",
                ),
                "this.execute(output, item)",
            ),
            canonical
                .replace(
                    Regex(
                        """val\s+item\s*=\s*checkNotNull\s*\(\s*completions\.removeFirstOrNull\s*""" +
                            """\(\s*\)\s*\)\s*val\s+before\s*=\s*committedState""",
                    ),
                    "val item = checkNotNull(completions.removeFirstOrNull())\n" +
                        "        val before = committedState\n" +
                        "        val acceptedOutputs = decision.frame.outputs",
                )
                .replace(
                    "for (output in decision.frame.outputs)",
                    "for (output in acceptedOutputs)",
                ),
            canonical.replace(
                "try {",
                "val `}` = Unit\n            try {",
            ).replace(
                "if (deferredFault == null) deferredFault = failure",
                "throw failure",
            ),
            canonical.replace(
                "for (output in decision.frame.outputs) {",
                "for (extra in decision.frame.outputs) this.execute(extra, item)\n" +
                    "            for (output in decision.frame.outputs) {",
            ),
            canonical.replace(
                "for (output in decision.frame.outputs) {",
                "if (false)\n                for (output in decision.frame.outputs) {",
            ),
            canonical.replace(
                "for (output in decision.frame.outputs) {",
                "if (false)\n                drain@ for (output in decision.frame.outputs) {",
            ),
            canonical.replace(
                "for (output in decision.frame.outputs) {",
                "if (false) Unit else for (output in decision.frame.outputs) {",
            ),
            canonical.replace(
                "for (output in decision.frame.outputs) {",
                "if (false)\n                @Suppress(\"UNUSED_VARIABLE\") " +
                    "for (output in decision.frame.outputs) {",
            ),
            canonical.replace(
                "while (!completions.isEmpty) {",
                "if (false)\n        while (!completions.isEmpty) {",
            ),
            canonical.replace(
                "while (!completions.isEmpty) {",
                "if (false)\n        drain@ while (!completions.isEmpty) {",
            ),
            canonical.replace(
                "while (!completions.isEmpty) {",
                "if (false) Unit else while (!completions.isEmpty) {",
            ),
            canonical.replace(
                "while (!completions.isEmpty) {",
                "if (false)\n        @Suppress(\"UNUSED_VARIABLE\") " +
                    "while (!completions.isEmpty) {",
            ),
            canonical.replace(
                "when (val decision = ProfileNucleus.decide(before, item.pulse)) {",
                "if (false) when (val decision = ProfileNucleus.decide(before, item.pulse)) {",
            ),
            canonical.replace(
                "for (output in decision.frame.outputs) {",
                "val (_, extraOutputs) = decision.frame\n" +
                    "            val executor = this::execute\n" +
                    "            for (extra in extraOutputs) executor(extra, item)\n" +
                    "            for (output in decision.frame.outputs) {",
            ),
            canonical.replace("root = false", ""),
            """
                fun dispatchLocal() = guard.dispatch {
                    var deferredFault: Throwable? = null
                    while (!completions.isEmpty) {
                        val item = checkNotNull(completions.removeFirstOrNull())
                        val before = committedState
                        if (shouldDispatchOutputs()) {
                            for (output in decision.frame.outputs) {
                                try {
                                    this.execute(output, item)
                                } catch (failure: Throwable) {
                                    if (deferredFault == null) deferredFault = failure
                                }
                            }
                        }
                    }
                    val failure = deferredFault
                    if (failure != null) throw failure
                }
            """.trimIndent(),
        )

        drifts.forEachIndexed { index, code ->
            val violations = resourceFaultStageFixtureViolations(
                listOf(SourceDocument(PROFILE_IMPL_PATH, code)),
            )
            assertTrue(
                violations.any { violation -> "canonical accepted-output" in violation },
                "case $index unexpectedly passed:\n$code\n${violations.joinToString("\n")}",
            )
        }
    }

    @Test
    fun profileCriticalAcceptedOutputFunctionsAreFailClosed() {
        val source = SourceDocument(
            PROFILE_IMPL_PATH,
            """
                fun dispatchLocal() {
                    return
                }

                fun dispatchCommand() = Unit

                fun preflight(frame: ProfileAcceptedFrame) {
                    frame.outputs.forEach { output -> replayBeforeCommit(output) }
                }
            """.trimIndent(),
        )

        val violations = resourceFaultStageViolations(listOf(source))

        assertViolation(violations, "semantic source changed")
    }

    @Test
    fun deferredDrainExceptionIsLimitedToTheProfileComponentDispatches() {
        val source = SourceDocument(
            PROFILE_RESOURCE_PATH,
            """
                fun dispatchLocal() = guard.dispatch {
                    var deferredFault: Throwable? = null
                    while (!completions.isEmpty) {
                        val item = checkNotNull(completions.removeFirstOrNull())
                        val before = committedState
                        for (output in decision.frame.outputs) {
                            try {
                                this.execute(output, item)
                            } catch (failure: Throwable) {
                                if (deferredFault == null) deferredFault = failure
                            }
                        }
                    }
                    val failure = deferredFault
                    if (failure != null) throw failure
                }
            """.trimIndent(),
        )

        val violations = resourceFaultStageFixtureViolations(listOf(source))

        assertViolation(violations, "broad `Throwable` catch")
    }

    @Test
    fun directRethrowMustBeTheEntireCatchBody() {
        listOf(
            "if (false) throw failure",
            "val neverInvoked = { throw failure }",
            "val decoy = \"throw failure\"",
        ).forEach { catchBody ->
            val source = SourceDocument(
                PROFILE_RESOURCE_PATH,
                """
                    fun dispatch() {
                        try {
                            target.accept()
                        } catch (failure: Throwable) {
                            $catchBody
                        }
                    }
                """.trimIndent(),
            )

            val violations = resourceFaultStageFixtureViolations(listOf(source))

            assertViolation(violations, "broad `Throwable` catch")
        }
    }

    @Test
    fun broadRuntimeFaultAliasesAndUnparsedCatchSignaturesFailClosed() {
        val sources = listOf(
            SourceDocument(
                PROFILE_RESOURCE_PATH,
                """
                    import kotlin.Throwable as ProviderFault

                    fun dispatch() = try {
                        target.accept()
                    } catch (_: ProviderFault) {
                        ProfileWriteResult.ResourceFailure(PROVIDER_FAILED)
                    }
                """.trimIndent(),
            ),
            SourceDocument(
                PROFILE_RESOURCE_PATH,
                """
                    import kotlin.
                        Throwable as ProviderFault

                    fun dispatch() = try {
                        target.accept()
                    } catch (_: ProviderFault) {
                        ProfileWriteResult.ResourceFailure(PROVIDER_FAILED)
                    }
                """.trimIndent(),
            ),
            SourceDocument(
                PROFILE_RESOURCE_PATH,
                """
                    import kotlin.Throwable as ProviderFault;

                    fun dispatch() = try {
                        target.accept()
                    } catch (_: ProviderFault) {
                        ProfileWriteResult.ResourceFailure(PROVIDER_FAILED)
                    }
                """.trimIndent(),
            ),
            SourceDocument(
                PROFILE_RESOURCE_PATH,
                """
                    @Suppress("unused") typealias ProviderFault = Throwable

                    fun dispatch() = try {
                        target.accept()
                    } catch (_: ProviderFault) {
                        ProfileWriteResult.ResourceFailure(PROVIDER_FAILED)
                    }
                """.trimIndent(),
            ),
            SourceDocument(
                PROFILE_RESOURCE_PATH,
                """
                    fun dispatch() = try {
                        target.accept()
                    } catch (`failure`: Throwable) {
                        throw `failure`
                    }
                """.trimIndent(),
            ),
            SourceDocument(
                "ball/gameplay/impl/src/commonMain/kotlin/fixture/GameplayResourceBoundary.kt",
                """
                    fun dispatch() = try {
                        target.accept()
                    } catch (`failure`: Throwable) {
                        GameplayResourceResult.OutcomeUnknown(PROVIDER_MAY_HAVE_EXECUTED)
                    }
                """.trimIndent(),
            ),
            SourceDocument(
                WEB_PLATFORM_PATH,
                """
                    private fun extra() {
                        try {
                            target.accept()
                        } catch (`failure`: Throwable) {
                            throw `failure`
                        }
                    }
                """.trimIndent(),
            ),
            SourceDocument(
                PROFILE_RESOURCE_PATH,
                """
                    val diagnostic = "${'$'}{try {
                        target.accept()
                    } catch (_: Throwable) {
                        0
                    }}"
                """.trimIndent(),
            ),
            SourceDocument(
                PROFILE_RESOURCE_PATH,
                """
                    import `kotlin`.`Throwable` as ProviderFault

                    fun dispatch() = try {
                        target.accept()
                    } catch (_: ProviderFault) {
                        ProfileWriteResult.ResourceFailure(PROVIDER_FAILED)
                    }
                """.trimIndent(),
            ),
            SourceDocument(
                PROFILE_RESOURCE_PATH,
                """
                    fun dispatch() = try {
                        target.accept()
                    } catch (@Suppress(")") failure: Throwable) {
                        throw failure
                    }
                """.trimIndent(),
            ),
            SourceDocument(
                "resource/example/impl/src/main/kotlin/fixture/ConventionalResource.kt",
                """
                    fun dispatch() = try {
                        target.accept()
                    } catch (_: Throwable) {
                        consumeFailure()
                    }
                """.trimIndent(),
            ),
        )

        sources.forEach { source ->
            val violations = resourceFaultStageFixtureViolations(listOf(source))
            assertViolation(violations, "Core §6.13")
        }
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
