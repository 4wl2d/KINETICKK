// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle.pokeball

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PokeballConformanceTaskTest {
    @Test
    fun projectBaselinePinsRejectByteOrderPathCountVersionAndStatusDrift() {
        val baseline = validProjectBaseline()
        assertTrue(projectBaselinePinViolations(baseline).isEmpty())

        val entrypoint = baseline.lineSequence().first { "Core entrypoint" in it }
        val orderedCore = baseline.lineSequence().first { "Ordered Core set" in it }
        val mutations = listOf(
            baseline.replace(PokeballBaseline.CORE_SHA256, "e" + PokeballBaseline.CORE_SHA256.drop(1)),
            baseline.replace(entrypoint, "__ENTRYPOINT__")
                .replace(orderedCore, entrypoint)
                .replace("__ENTRYPOINT__", orderedCore),
            baseline.replace("spec/pokeball-architecture-core.md", "spec/other.md"),
            baseline.replace("Ordered Core set: 25 files", "Ordered Core set: 24 files"),
            baseline.replace(PokeballBaseline.CORE_VERSION, "1.4.1-draft"),
            baseline.replace(PokeballBaseline.CORE_STATUS, "noncanonical"),
        )

        mutations.forEachIndexed { index, mutation ->
            assertTrue(
                projectBaselinePinViolations(mutation).isNotEmpty(),
                "Mutation $index unexpectedly preserved the frozen baseline pins",
            )
        }
    }

    @Test
    fun conformanceMetadataRejectsUnknownDuplicateMalformedAndMissingFields() {
        val valid = conformanceRecord(validMetadata(freeze = "1".repeat(40), proofDigest = "2".repeat(64)))
        assertEquals(33, parseStrictConformanceMetadata(valid).size)

        listOf(
            valid.replace("schemaVersion=1", "schemaVersion=1\nunknownField=value"),
            valid.replace("schemaVersion=1", "schemaVersion=1\nschemaVersion=1"),
            valid.replace("schemaVersion=1", "schemaVersion"),
            valid.replace("schemaVersion=1\n", ""),
        ).forEach { malformed ->
            assertFailsWith<IllegalArgumentException> { parseStrictConformanceMetadata(malformed) }
        }
    }

    @Test
    fun auditPolicyPinsAllFourProfilesAndPresentSemanticRetryContract() {
        val policy = validAuditPolicy()
        val applicability = validApplicability()
        val evidence = validSemanticRetryEvidence()
        assertEquals(22, parseStrictAuditPolicy(policy).size)
        val violations = auditPolicyViolations(policy, applicability, evidence)
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))

        listOf(
            policy.replace(
                "profileAuthorities=ContentCatalog|Profile|GameplayRun|AppSession",
                "profileAuthorities=ContentCatalog|Profile|GameplayRun|AppSession\nunknownPolicyKey=value",
            ),
            policy.replace(
                "semanticRetry=PRESENT",
                "semanticRetry=PRESENT\nsemanticRetry=PRESENT",
            ),
            policy.replace("contentMutationPath=NONE\n", ""),
        ).forEach { malformed ->
            assertFailsWith<IllegalArgumentException> { parseStrictAuditPolicy(malformed) }
        }

        val mutations = listOf(
            policy.replace(
                "profileAuthorities=ContentCatalog|Profile|GameplayRun|AppSession",
                "profileAuthorities=Profile|GameplayRun|AppSession",
            ) to "profileAuthorities must be exactly",
            policy.replace(
                "effectiveProfile=Inline+Transient+InProcess+Standard+Static",
                "effectiveProfile=Inline+Transient+InProcess+Standard",
            ) to "effectiveProfile must be exactly",
            policy.replace("semanticRetry=PRESENT", "semanticRetry=ABSENT") to
                "semanticRetry must be exactly",
            policy.replace(
                "semanticRetryFamilies=legacy-purge|reset-write",
                "semanticRetryFamilies=legacy-purge",
            ) to "semanticRetryFamilies must be exactly",
            policy.replace("semanticRetryPrimaryOwner=AppSession", "semanticRetryPrimaryOwner=Profile") to
                "semanticRetryPrimaryOwner must be exactly",
            policy.replace("semanticRetryAttemptsPerPulse=1", "semanticRetryAttemptsPerPulse=2") to
                "semanticRetryAttemptsPerPulse must be exactly",
        )
        mutations.forEach { (changed, expectedMessage) ->
            val mutationViolations = auditPolicyViolations(changed, applicability, evidence)
            assertTrue(
                mutationViolations.any { expectedMessage in it },
                "Missing `$expectedMessage` in $mutationViolations",
            )
        }

        assertTrue(
            auditPolicyViolations(
                policy + "\nContentCatalog has no runtime mutation profile.\n",
                applicability,
                evidence,
            ).any { "cannot be exempted from effective-profile resolution" in it },
        )

        val absentContradiction = applicability +
            "\n| retry, idempotency, cancellation | incorrectly absent |\n"
        assertTrue(
            auditPolicyViolations(policy, absentContradiction, evidence)
                .any { "contradicts present PBA-24 semantic retry" in it },
        )

        val firstAnchor = semanticRetryEvidenceAnchors.first()
        val incompleteEvidence = evidence + (firstAnchor.path to "missing required tokens")
        assertTrue(
            auditPolicyViolations(policy, applicability, incompleteEvidence)
                .any { "Semantic-retry policy evidence ${firstAnchor.path} is missing" in it },
        )
    }

    @Test
    fun triggerAbsenceProofParserRejectsCountOrderUnknownDuplicateAndMalformedFields() {
        val freeze = "1".repeat(40)
        val digest = "2".repeat(64)
        assertEquals(7, requiredTriggerAbsenceProofIds.size)
        assertTrue("TA-05-retry-idempotency-cancellation" !in requiredTriggerAbsenceProofIds)
        val blocks = requiredTriggerAbsenceProofIds.map { proofBlock(it, freeze, digest) }
        val valid = blocks.joinToString("\n\n")
        assertEquals(requiredTriggerAbsenceProofIds, parseStrictTriggerAbsenceProofs(valid).map { it.id })

        val reordered = listOf(blocks[1], blocks[0]) + blocks.drop(2)
        listOf(
            blocks.dropLast(1).joinToString("\n\n"),
            reordered.joinToString("\n\n"),
            valid.replaceFirst("schemaVersion=1", "schemaVersion=1\nunknownField=value"),
            valid.replaceFirst("schemaVersion=1", "schemaVersion=1\nschemaVersion=1"),
            valid.replaceFirst("schemaVersion=1", "schemaVersion"),
        ).forEach { malformed ->
            assertFailsWith<IllegalArgumentException> { parseStrictTriggerAbsenceProofs(malformed) }
        }
    }

    @Test
    fun gitWorktreePreflightDetectsTrackedAndUntrackedChanges() {
        val repository = createGitRepository()
        val tracked = repository.resolve("tracked.txt")
        tracked.writeText("original\n")
        commitAll(repository, "test: initial")
        assertTrue(gitWorktreeEntries(repository).isEmpty())

        tracked.writeText("changed\n")
        assertTrue(gitWorktreeEntries(repository).any { it.startsWith(" M ") })

        tracked.writeText("original\n")
        repository.resolve("untracked.txt").writeText("new\n")
        assertTrue(gitWorktreeEntries(repository).any { it.startsWith("?? ") })
    }

    @Test
    fun strictAttestationValidatesCommittedBoundaryDigestsAndAllProofFields() {
        val fixture = strictFixture()
        val violations = validateStrictAttestation(
                root = fixture.root,
                head = fixture.head,
                recordText = fixture.recordText,
                proofBytes = fixture.proofBytes,
                metadata = fixture.metadata,
                proofs = fixture.proofs,
            )
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))

        val schemaViolations = fixture.validate(metadata = fixture.metadata + ("schemaVersion" to "2"))
        assertTrue(schemaViolations.any { "schemaVersion must be exactly 1" in it })
        val statusViolations = fixture.validate(metadata = fixture.metadata + ("attestationStatus" to "FAIL"))
        assertTrue(statusViolations.any { "attestationStatus must be exactly PASS" in it })
        val policyViolations = fixture.validate(metadata = fixture.metadata + ("policySha256" to "0".repeat(64)))
        assertTrue(policyViolations.any { "policySha256 mismatch" in it })
        val scopeViolations = fixture.validate(
            metadata = fixture.metadata + ("claimScope" to "Profile GameplayRun AppSession ContentCatalog Desktop"),
        )
        assertTrue(scopeViolations.any { "claimScope is missing Web" in it })
        val environmentViolations = fixture.validate(
            metadata = fixture.metadata +
                ("claimEnvironment" to "JDK:17;Gradle:8.14.3;Kotlin:2.2.20;Chromium:150.0.7871.24"),
        )
        assertTrue(environmentViolations.any { "claimEnvironment must use ordered" in it })
    }

    @Test
    fun strictAttestationRejectsProofPathDigestPredicateConclusionOwnerAndInvalidationDrift() {
        val fixture = strictFixture()
        val first = fixture.proofs.first()
        val mutations = listOf(
            "inventoryEvidence" to "DenialByConstructionRef:../outside.md",
            "inventoryRevisionsOrDigests" to
                "git:${fixture.metadata.getValue("implementationFreezeSha")};sha256:${"0".repeat(64)}",
            "evaluatedPredicate" to "present",
            "conclusion" to "Present",
            "evidenceOwner" to "unknown",
            "invalidationConditions" to "source-change",
        )
        val expectedMessages = listOf(
            "canonical repository-relative path",
            "inventory digest mismatch",
            "evaluatedPredicate",
            "conclusion must be exactly Absent",
            "evidenceOwner must be exactly",
            "invalidationConditions must be exactly",
        )

        mutations.zip(expectedMessages).forEach { (mutation, expectedMessage) ->
            val changed = TriggerAbsenceProof(first.values + mutation)
            val violations = fixture.validate(proofs = listOf(changed) + fixture.proofs.drop(1))
            assertTrue(violations.any { expectedMessage in it }, "Missing `$expectedMessage` in $violations")
        }
    }

    @Test
    fun strictAttestationPinsProofClassAnchorAndInventoryChoiceAndPathById() {
        val fixture = strictFixture()
        val proof = fixture.proofs.first()
        val required = requiredTriggerAbsenceProofs.first()
        val mutations = listOf(
            "triggerClass" to "claim-triggered",
            "triggerAnchor" to "Core §0.2",
            "inventoryEvidence" to "PathInventoryRef:${required.inventoryPath}",
            "inventoryEvidence" to "${required.inventoryChoice}:$POKEBALL_POLICY_PATH",
        )
        val expectedMessages = listOf(
            "triggerClass must be exactly `${required.triggerClass}`",
            "triggerAnchor must be exactly `${required.triggerAnchor}`",
            "inventoryEvidence must be exactly `${required.inventoryEvidence}`",
            "inventoryEvidence must be exactly `${required.inventoryEvidence}`",
        )

        mutations.zip(expectedMessages).forEach { (mutation, expectedMessage) ->
            val changed = TriggerAbsenceProof(proof.values + mutation)
            val violations = fixture.validate(proofs = listOf(changed) + fixture.proofs.drop(1))
            assertTrue(violations.any { expectedMessage in it }, "Missing `$expectedMessage` in $violations")
        }
    }

    @Test
    fun strictAttestationRejectsAnyLaterProductOrBuildCommit() {
        val fixture = strictFixture()
        write(fixture.root, "product.txt", "changed after attestation\n")
        commitAll(fixture.root, "docs(architecture): attest Pokeball conformance")
        val laterHead = runGit(fixture.root, "rev-parse", "HEAD").trim()

        val violations = validateStrictAttestation(
            root = fixture.root,
            head = laterHead,
            recordText = fixture.recordText,
            proofBytes = fixture.proofBytes,
            metadata = fixture.metadata,
            proofs = fixture.proofs,
        )

        assertTrue(violations.any { "Non-attestation path changed after freeze: product.txt" in it })
        assertTrue(violations.any { "is not the committed conformance-record attestation" in it })
        assertTrue(violations.any { "is not recorded implementation freeze" in it })
    }

    @Test
    fun strictAttestationReportsAnUnknownFreezeWithoutEscapingValidation() {
        val fixture = strictFixture()
        val unknownFreeze = "f".repeat(40)

        val violations = fixture.validate(
            metadata = fixture.metadata + ("implementationFreezeSha" to unknownFreeze),
        )

        assertTrue(
            violations.any {
                it == "Could not inspect attestation diff for recorded freeze $unknownFreeze"
            },
        )
    }

    private fun strictFixture(): StrictFixture {
        val root = createGitRepository()
        write(root, POKEBALL_BASELINE_PATH, validProjectBaseline())
        write(root, POKEBALL_POLICY_PATH, validAuditPolicy())
        write(root, POKEBALL_ASSEMBLY_PATH, "# Assembly\n\nPinned assembly.\n")
        write(root, POKEBALL_APPLICABILITY_PATH, validApplicability())
        validSemanticRetryEvidence().forEach { (path, text) -> write(root, path, text) }
        write(
            root,
            "docs/architecture/pokeball/resolved-manifest.json",
            "{\"schema\":\"test-closed-route-inventory\"}\n",
        )
        write(root, "product.txt", "frozen product bytes\n")
        commitAll(root, "feat: implementation freeze")
        val freeze = runGit(root, "rev-parse", "HEAD").trim()
        val proofText = requiredTriggerAbsenceProofIds.joinToString("\n\n") {
            val required = requiredTriggerAbsenceProofs.single { proof -> proof.id == it }
            proofBlock(it, freeze, sha256(gitBlob(root, freeze, required.inventoryPath)))
        } + "\n"
        val proofBytes = proofText.toByteArray(StandardCharsets.UTF_8)
        val metadata = validMetadata(
            freeze = freeze,
            proofDigest = sha256(proofBytes),
            productTreeDigest = gitTreeDigest(root, freeze),
            policyDigest = sha256(gitBlob(root, freeze, POKEBALL_POLICY_PATH)),
            assemblyDigest = sha256(gitBlob(root, freeze, POKEBALL_ASSEMBLY_PATH)),
        )
        val recordText = conformanceRecord(metadata)
        write(root, CONFORMANCE_RECORD_PATH, recordText)
        write(root, TRIGGER_ABSENCE_PROOFS_PATH, proofText)
        commitAll(root, "docs(architecture): attest Pokeball conformance")
        val head = runGit(root, "rev-parse", "HEAD").trim()
        return StrictFixture(
            root = root,
            head = head,
            recordText = recordText,
            proofBytes = proofBytes,
            metadata = parseStrictConformanceMetadata(recordText),
            proofs = parseStrictTriggerAbsenceProofs(proofText),
        )
    }

    private fun validMetadata(
        freeze: String,
        proofDigest: String,
        productTreeDigest: String = "3".repeat(64),
        policyDigest: String = "4".repeat(64),
        assemblyDigest: String = "5".repeat(64),
    ): LinkedHashMap<String, String> = linkedMapOf<String, String>().apply {
        put("schemaVersion", "1")
        put("attestationStatus", "PASS")
        put("implementationFreezeSha", freeze)
        put("productTreeSha256", productTreeDigest)
        put("coreCommit", PokeballBaseline.CORE_COMMIT)
        put("coreSha256", PokeballBaseline.CORE_SHA256)
        put("agentPackRevision", PokeballBaseline.AGENT_PACK_REVISION.toString())
        put("agentPackSha256", PokeballBaseline.AGENT_PACK_SHA256)
        put("policySha256", policyDigest)
        put("assemblySha256", assemblyDigest)
        put("triggerAbsenceProofsSha256", proofDigest)
        put(
            "claimWording",
            "KINETICKK on implementation SHA $freeze conforms within the declared scope to " +
                "Pokeball Core 1.4.0-draft at ${PokeballBaseline.CORE_COMMIT}",
        )
        put("claimBoundary", "Implementation $freeze bound to Pokeball Core ${PokeballBaseline.CORE_COMMIT}")
        put("claimScope", "Profile GameplayRun AppSession ContentCatalog across Android, Desktop, and Web")
        put("claimBinding", "Inline+Transient+InProcess+Standard+Static")
        put("claimMechanism", "Pure Nucleus decisions, bounded acceptors, same-stack results, commit-before-dispatch")
        put("claimAssumptions", "Pinned same-build single-process source, policy, inventory, and browser environment")
        put("claimRetention", "Freeze and attestation commits plus CI evidence artifacts retained in project history")
        put(
            "claimEvidence",
            "RG-01..RG-10; verifyArchitecture; verifyPokeballArchitecture; desktopTest; " +
                "wasmJsBrowserTest; wasmJsBrowserDistribution",
        )
        put(
            "claimEnvironment",
            "JDK:17.0.20+8;Gradle:8.14.3;Kotlin:2.3.20;Node:25.0.0;Chromium:150.0.7871.24",
        )
        put(
            "claimNonGuarantees",
            "crash-atomic persistence; exactly-once; eventual delivery; cross-device storage; " +
                "external audio/storage providers",
        )
        put("waiverEffects", "NONE")
        put("failedOrPartialUnits", "NONE")
        (1..10).forEach { put("rg${it.toString().padStart(2, '0')}", "PASS") }
    }

    private fun conformanceRecord(metadata: Map<String, String>): String = buildString {
        appendLine("# Pokeball conformance record")
        appendLine()
        appendLine("This project self-attestation is not external certification.")
        appendLine("It does not guarantee crash-atomic persistence, exactly-once, eventual delivery,")
        appendLine("cross-device storage, or external audio/storage providers.")
        appendLine()
        appendLine("<!-- pokeball-conformance")
        metadata.forEach { (key, value) -> appendLine("$key=$value") }
        appendLine("-->")
    }

    private fun proofBlock(id: String, freeze: String, inventoryDigest: String): String {
        val required = requiredTriggerAbsenceProofs.single { proof -> proof.id == id }
        val predicate = when (id) {
            "TA-01-actors" -> "Absent iff the closed inventory contains no actor-dependent semantic path"
            "TA-02-auth-grants-secrets" ->
                "Absent iff the closed inventory contains no authentication, grant, or secret semantic path"
            "TA-03-network-remote" ->
                "Absent iff the closed inventory contains no network, remote, or IPC semantic path"
            "TA-04-detached-async" ->
                "Absent iff the closed inventory contains no detached asynchronous semantic delivery path"
            "TA-06-dynamic-registry" ->
                "Absent iff the closed inventory contains no dynamic registry or wildcard route"
            "TA-07-isolation" ->
                "Absent iff the closed inventory contains no process or security isolation boundary"
            "TA-08-durable-outbox" ->
                "Absent iff the closed inventory contains no durable outbox, journal, or status materializer"
            else -> error("Unknown proof ID $id")
        }
        return """
        <!-- pokeball-trigger-absence-proof
        schemaVersion=1
        id=$id
        triggerClass=${required.triggerClass}
        triggerAnchor=${required.triggerAnchor}
        exactScopeAndEffectiveProfile=${
            "KINETICKK four-authority Android/Desktop/Web application | " +
                "Inline+Transient+InProcess+Standard+Static"
        }
        inventoryEvidence=${required.inventoryEvidence}
        inventoryRevisionsOrDigests=git:$freeze;sha256:$inventoryDigest
        evaluatedPredicate=$predicate
        conclusion=Absent
        evidenceOwner=KINETICKK project
        invalidationConditions=${
            "scope-change|profile-change|version-change|inventory-change|digest-change|" +
                "unresolved-reference|conflicting-evidence"
        }
        -->
        """.trimIndent()
    }

    private fun validProjectBaseline(): String = """
        # Migration baseline

        ## Immutable inputs

        - Pokeball repository: `git@github.com:4wl2d/Pokeball.git` at `${PokeballBaseline.CORE_COMMIT}`.
        - Core entrypoint: ${
            "`spec/pokeball-architecture-core.md`, declared version `${PokeballBaseline.CORE_VERSION}`, " +
                "status `${PokeballBaseline.CORE_STATUS}`."
        }
        - Ordered Core set: ${
            "${PokeballBaseline.CORE_FILE_COUNT} files, ${PokeballBaseline.CORE_BYTES} exact bytes, " +
                "SHA-256 `${PokeballBaseline.CORE_SHA256}`."
        }
        - Agent Pack: ${
            "revision ${PokeballBaseline.AGENT_PACK_REVISION}, " +
                "${PokeballBaseline.AGENT_PACK_FILE_COUNT} Markdown files including `BASELINE.md`; its digest " +
                "covers the other ${PokeballBaseline.AGENT_PACK_FILE_COUNT - 1} files and is SHA-256 " +
                "`${PokeballBaseline.AGENT_PACK_SHA256}`."
        }

        ## Frozen product behavior

        Frozen.
    """.trimIndent() + "\n"

    private fun validAuditPolicy(): String = """
        # Policy

        ContentCatalog, Profile, GameplayRun, and AppSession use the exact project profile.
        ContentCatalog remains immutable/query-only after bootstrap and has no runtime mutation path.

        <!-- pokeball-audit-policy
        profileAuthorities=ContentCatalog|Profile|GameplayRun|AppSession
        effectiveProfile=Inline+Transient+InProcess+Standard+Static
        contentMutationPath=NONE
        semanticRetry=PRESENT
        semanticRetryAnchor=Core §9.9 / PBA-24
        semanticRetryFamilies=legacy-purge|reset-write
        semanticRetryPrimaryOwner=AppSession
        semanticRetryTarget=Profile
        semanticRetryAttemptsPerPulse=1
        semanticRetryDisabledLayers=transport|executor|SDK/provider|reconciliation
        semanticRetrySameIdentityResend=DISABLED
        semanticRetryLegacyPurgePulse=SessionInteractionPulse.ResetRetryRequested
        semanticRetryLegacyPurgeCommand=ProfileModuleCommand.RetryLegacyPurge
        semanticRetryLegacyPurgeEvidence=${legacyPurgeSemanticRetryEvidenceAnchors.joinToString("|") { it.path }}
        semanticRetryResetWritePulse=SessionInteractionPulse.ResetConfirmed
        semanticRetryResetWriteCommand=ProfileModuleCommand.ConfirmLegacyReset
        semanticRetryResetWriteFailureResults=ProfileModuleResult.ResetWriteRejected|ProfileModuleResult.ResetWriteResourceFailure|ProfileModuleResult.ResetWriteOutcomeUnknown
        semanticRetryResetWriteReturnLifecycle=SessionResetLifecycle.CONFIRMATION_REQUIRED
        semanticRetryResetWriteFreshIdentity=semanticHandle|effectRef|sourceRevision
        semanticRetryResetWriteResourceInvocationsPerPulse=1
        semanticRetryResetWriteProviderMutationCallsPerPulse=0..1
        semanticRetryResetWriteEvidence=${resetWriteSemanticRetryEvidenceAnchors.joinToString("|") { it.path }}
        -->
    """.trimIndent() + "\n"

    private fun validApplicability(): String = """
        # Applicability

        ## Present triggers

        | Concern | Why it is present | Required construction/evidence |
        |---|---|---|
        | explicit user semantic retry | under `PBA-24`, primary owner `AppSession` targets `Profile`: `SessionInteractionPulse.ResetRetryRequested` issues `ProfileModuleCommand.RetryLegacyPurge`; after `ProfileModuleResult.ResetWriteRejected`, `ProfileModuleResult.ResetWriteResourceFailure`, or `ProfileModuleResult.ResetWriteOutcomeUnknown`, `SessionInteractionPulse.ResetConfirmed` issues `ProfileModuleCommand.ConfirmLegacyReset` with a fresh semantic handle and no same-identity resend | one purge or reset-write attempt per explicit user Pulse; one Profile Resource write invocation per explicit user Pulse; zero provider mutation calls on local encode rejection, otherwise at most one provider mutation call |

        ## Absent trigger scopes

        | Absent scope | Denial by construction |
        |---|---|
        | dynamic registry or wildcard routing | static routes only |
    """.trimIndent() + "\n"

    private fun validSemanticRetryEvidence(): Map<String, String> =
        semanticRetryEvidenceAnchors.groupBy(AuditEvidenceAnchor::path).mapValues { (_, anchors) ->
            anchors.flatMap(AuditEvidenceAnchor::tokens).joinToString("\n", postfix = "\n")
        }

    private fun createGitRepository(): Path = createTempDirectory("pokeball-conformance-test-").also { root ->
        runGit(root, "init", "--quiet")
        runGit(root, "config", "user.name", "Verifier Test")
        runGit(root, "config", "user.email", "verifier@example.invalid")
    }

    private fun commitAll(root: Path, title: String) {
        runGit(root, "add", ".")
        runGit(
            root,
            "commit",
            "--quiet",
            "-m",
            "$title\n\nSigned-off-by: Verifier Test <verifier@example.invalid>",
        )
    }

    private fun write(root: Path, relativePath: String, text: String) {
        val target = root.resolve(relativePath)
        target.parent.createDirectories()
        Files.writeString(target, text, StandardCharsets.UTF_8)
    }

    private data class StrictFixture(
        val root: Path,
        val head: String,
        val recordText: String,
        val proofBytes: ByteArray,
        val metadata: Map<String, String>,
        val proofs: List<TriggerAbsenceProof>,
    ) {
        fun validate(
            metadata: Map<String, String> = this.metadata,
            proofs: List<TriggerAbsenceProof> = this.proofs,
        ): List<String> = validateStrictAttestation(
            root = root,
            head = head,
            recordText = recordText,
            proofBytes = proofBytes,
            metadata = metadata,
            proofs = proofs,
        )
    }
}
