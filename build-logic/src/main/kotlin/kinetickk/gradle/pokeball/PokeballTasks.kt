// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle.pokeball

import kinetickk.gradle.loadArchitectureEdges
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

@CacheableTask
abstract class GeneratePokeballResolvedManifestTask : DefaultTask() {
    @get:Input
    abstract val leafProjectPaths: SetProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val architectureEdgeReportFiles: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assemblyRecord: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val assembly = assemblyRecord.get().asFile.readText(StandardCharsets.UTF_8)
        val reads = parseTableIds(assembly, "## Read dependencies")
        val commands = parseTableIds(assembly, "## Command/result routes")
        val edges = loadArchitectureEdges(
            reportFiles = architectureEdgeReportFiles.files,
            expectedSourceProjectPaths = leafProjectPaths.get(),
        )
        val json = resolvedManifestJson(
            leafProjects = leafProjectPaths.get(),
            edges = edges.map(ProjectEdge::decode),
            readRoutes = reads,
            commandRoutes = commands,
        )
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(json, StandardCharsets.UTF_8)
        logger.lifecycle("Generated non-authoritative Pokeball projection: ${output.invariantSeparatorsPath}")
    }
}

abstract class VerifyPokeballManifestDriftTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedManifest: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val checkedManifest: RegularFileProperty

    @TaskAction
    fun verify() {
        val generated = generatedManifest.get().asFile.readBytes()
        val checked = checkedManifest.get().asFile.readBytes()
        if (!generated.contentEquals(checked)) {
            throw GradleException(
                "Generated Pokeball manifest drifted from " +
                    "${checkedManifest.get().asFile.invariantSeparatorsPath}. " +
                    "Run updatePokeballResolvedManifest after reviewing typed-source changes.",
            )
        }
        logger.lifecycle("Pokeball resolved-manifest projection is byte-for-byte current.")
    }
}

abstract class VerifyPokeballSnapshotTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val snapshotDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val baselineRecord: RegularFileProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun verify() {
        val snapshotRoot = snapshotDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        val violations = mutableListOf<String>()
        val worktreeEntries = runCatching { gitWorktreeEntries(snapshotRoot) }.getOrElse { failure ->
            violations += "Could not inspect the Pokeball worktree: ${failure.message}"
            emptyList()
        }
        if (worktreeEntries.isNotEmpty()) {
            violations += "Pokeball worktree is not clean: ${worktreeEntries.joinToString()}"
        }

        violations += projectBaselinePinViolations(
            baselineRecord.get().asFile.readText(StandardCharsets.UTF_8),
        )

        val result = if (worktreeEntries.isEmpty()) {
            runCatching { verifySnapshot(snapshotRoot) }.getOrElse { failure ->
                val message = failure.message.orEmpty().replace(snapshotRoot.toString(), "<snapshot>")
                violations += "Snapshot verification failed: $message"
                null
            }
        } else {
            null
        }
        val output = reportFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(snapshotIntegrityReport(result, violations), StandardCharsets.UTF_8)

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Pinned Pokeball snapshot verification failed:")
                    violations.distinct().sorted().forEach { appendLine(" - $it") }
                    append("Report: ${output.invariantSeparatorsPath}")
                },
            )
        }
        checkNotNull(result)
        logger.lifecycle(
            "Pinned Pokeball snapshot verified: Core ${result.coreFiles} files/" +
                "${result.coreBytes} bytes/${result.coreSha256}; " +
                "Agent Pack ${result.agentPackFiles} files/${result.agentPackSha256}.",
        )
    }
}

abstract class VerifyPokeballArchitectureTask : DefaultTask() {
    @get:Input
    abstract val leafProjectPaths: SetProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val architectureEdgeReportFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val productionSourceFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val architectureRecordFiles: ConfigurableFileCollection

    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val root = repositoryRoot.get().asFile.toPath().toAbsolutePath().normalize()
        val sources = productionSourceFiles.files
            .filter { it.isFile }
            .map { file ->
                SourceDocument(
                    relativePath = root.relativize(file.toPath().toAbsolutePath().normalize())
                        .toString()
                        .replace('\\', '/'),
                    text = file.readText(StandardCharsets.UTF_8),
                )
            }
            .sortedBy(SourceDocument::relativePath)
        val records = architectureRecordFiles.files
            .filter { it.isFile }
            .associate { it.name to it.readText(StandardCharsets.UTF_8) }
        val declaredProjectDependencies = loadArchitectureEdges(
            reportFiles = architectureEdgeReportFiles.files,
            expectedSourceProjectPaths = leafProjectPaths.get(),
        )
        val violations = resolveArchitectureViolations(
            leafProjects = leafProjectPaths.get(),
            edges = declaredProjectDependencies.map(ProjectEdge::decode).toSet(),
            sources = sources,
            architectureRecords = records,
        )

        val report = buildString {
            appendLine("{")
            appendLine("  \"schema\": \"kinetickk-pokeball-architecture-report/v1\",")
            appendLine("  \"status\": \"${if (violations.isEmpty()) "PASS" else "FAIL"}\",")
            appendLine("  \"leafModules\": ${leafProjectPaths.get().size},")
            appendLine("  \"declaredProjectEdges\": ${declaredProjectDependencies.size},")
            appendLine("  \"violations\": [")
            violations.forEachIndexed { index, violation ->
                append("    \"${jsonEscape(violation)}\"")
                appendLine(if (index == violations.lastIndex) "" else ",")
            }
            appendLine("  ]")
            appendLine("}")
        }
        val output = reportFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(report, StandardCharsets.UTF_8)

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Pokeball architecture verification failed with ${violations.size} violation(s):")
                    violations.forEach { appendLine(" - $it") }
                    append("Report: ${output.invariantSeparatorsPath}")
                },
            )
        }
        logger.lifecycle(
            "Pokeball architecture verified: ${leafProjectPaths.get().size} modules, " +
                "${declaredProjectDependencies.size} declared edges, " +
                "${expectedReadRoutes.size} read routes, ${expectedCommandRoutes.size} command mappings.",
        )
    }
}

abstract class VerifyPokeballConformanceTask : DefaultTask() {
    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun verify() {
        val root = repositoryRoot.get().asFile.toPath().toAbsolutePath().normalize()
        val head = runGit(root, "rev-parse", "HEAD").trim()
        val currentTreeDigest = gitTreeDigest(root, head)
        val output = reportFile.get().asFile
        output.parentFile.mkdirs()
        val recordBytes = committedFileBytesOrNull(root, head, CONFORMANCE_RECORD_PATH)
        val proofBytes = committedFileBytesOrNull(root, head, TRIGGER_ABSENCE_PROOFS_PATH)
        val repositoryViolations = gitWorktreeEntries(root).map {
            "Repository worktree is not clean: $it"
        }

        if (recordBytes == null) {
            val violations = buildList {
                addAll(repositoryViolations)
                addAll(validateFrozenAuditPolicy(root, head))
                if (proofBytes != null) {
                    add("Trigger-absence proofs are committed without a conformance record")
                }
            }
            output.writeText(
                prerequisiteReport(head, currentTreeDigest, violations),
                StandardCharsets.UTF_8,
            )
            if (violations.isNotEmpty()) {
                throw conformanceFailure(output.toPath(), violations)
            }
            logger.lifecycle(
                "Pokeball conformance prerequisites verified at $head; no formal claim record is present. " +
                    "Freeze tree SHA-256 candidate: $currentTreeDigest",
            )
            return
        }

        val violations = repositoryViolations.toMutableList()
        violations += committedWorkspaceFileViolations(root, CONFORMANCE_RECORD_PATH, recordBytes)
        val recordText = runCatching { decodeStrictUtf8(recordBytes, CONFORMANCE_RECORD_PATH) }
            .getOrElse { failure ->
                violations += failure.message.orEmpty()
                ""
            }
        val proofText = if (proofBytes == null) {
            violations += "Strict claim requires committed $TRIGGER_ABSENCE_PROOFS_PATH"
            ""
        } else {
            violations += committedWorkspaceFileViolations(root, TRIGGER_ABSENCE_PROOFS_PATH, proofBytes)
            runCatching { decodeStrictUtf8(proofBytes, TRIGGER_ABSENCE_PROOFS_PATH) }
                .getOrElse { failure ->
                    violations += failure.message.orEmpty()
                    ""
                }
        }

        val metadata = runCatching { parseStrictConformanceMetadata(recordText) }
            .getOrElse { failure ->
                violations += "Malformed Pokeball conformance metadata: ${failure.message}"
                emptyMap()
            }
        val proofs = runCatching { parseStrictTriggerAbsenceProofs(proofText) }
            .getOrElse { failure ->
                violations += "Malformed TriggerAbsenceProof metadata: ${failure.message}"
                emptyList()
            }
        if (metadata.isNotEmpty() && proofs.isNotEmpty()) {
            violations += validateStrictAttestation(
                root = root,
                head = head,
                recordText = recordText,
                proofBytes = proofBytes,
                metadata = metadata,
                proofs = proofs,
            )
        }
        val normalizedViolations = violations.filter(String::isNotBlank).distinct().sorted()
        output.writeText(
            strictReport(
                head = head,
                freeze = metadata["implementationFreezeSha"].orEmpty(),
                treeDigest = metadata["productTreeSha256"].orEmpty(),
                recordDigest = sha256(recordBytes),
                proofsDigest = proofBytes?.let(::sha256).orEmpty(),
                policyDigest = metadata["policySha256"].orEmpty(),
                assemblyDigest = metadata["assemblySha256"].orEmpty(),
                violations = normalizedViolations,
            ),
            StandardCharsets.UTF_8,
        )
        if (normalizedViolations.isNotEmpty()) {
            throw conformanceFailure(output.toPath(), normalizedViolations)
        }
        logger.lifecycle(
            "Pokeball self-attestation verified at $head for implementation freeze " +
                "${metadata.getValue("implementationFreezeSha")}; RG-01..RG-10 are PASS.",
        )
    }
}

internal const val CONFORMANCE_RECORD_PATH = "docs/architecture/pokeball/conformance-record.md"
internal const val TRIGGER_ABSENCE_PROOFS_PATH = "docs/architecture/pokeball/trigger-absence-proofs.md"
internal const val POKEBALL_BASELINE_PATH = "docs/architecture/pokeball/baseline.md"
internal const val POKEBALL_POLICY_PATH = "docs/architecture/pokeball/policy.md"
internal const val POKEBALL_ASSEMBLY_PATH = "docs/architecture/pokeball/assembly.md"
internal const val POKEBALL_APPLICABILITY_PATH = "docs/architecture/pokeball/applicability.md"

internal val docsOnlyAttestationAllowlist = sortedSetOf(
    "docs/architecture/pokeball/README.md",
    CONFORMANCE_RECORD_PATH,
    TRIGGER_ABSENCE_PROOFS_PATH,
)

internal data class RequiredTriggerAbsenceProof(
    val id: String,
    val triggerClass: String,
    val triggerAnchor: String,
    val inventoryChoice: String,
    val inventoryPath: String,
    val predicateTokens: List<String>,
) {
    val inventoryEvidence: String
        get() = "$inventoryChoice:$inventoryPath"
}

internal val requiredTriggerAbsenceProofs = listOf(
    RequiredTriggerAbsenceProof(
        "TA-01-actors",
        "risk-triggered",
        "Core §11.2 / PBA-44",
        "RiskInventoryRef",
        "docs/architecture/pokeball/applicability.md",
        listOf("actor"),
    ),
    RequiredTriggerAbsenceProof(
        "TA-02-auth-grants-secrets",
        "risk-triggered",
        "Core §11.2–§11.3, §11.7, §11.9 / PBA-33, PBA-36, PBA-44",
        "RiskInventoryRef",
        "docs/architecture/pokeball/applicability.md",
        listOf("authentication", "grant", "secret"),
    ),
    RequiredTriggerAbsenceProof(
        "TA-03-network-remote",
        "path-triggered",
        "Core §10.2, §12.7–§12.8",
        "PathInventoryRef",
        POKEBALL_ASSEMBLY_PATH,
        listOf("network", "remote", "ipc"),
    ),
    RequiredTriggerAbsenceProof(
        "TA-04-detached-async",
        "path-triggered",
        "Core §9.1–§9.2, §9.12–§9.13, §12.2–§12.3",
        "PathInventoryRef",
        POKEBALL_ASSEMBLY_PATH,
        listOf("detached", "asynchronous", "semantic"),
    ),
    RequiredTriggerAbsenceProof(
        "TA-06-dynamic-registry",
        "path-triggered",
        "Core §10.4, §10.7 / PBA-27",
        "PathInventoryRef",
        "docs/architecture/pokeball/resolved-manifest.json",
        listOf("dynamic", "registry", "wildcard"),
    ),
    RequiredTriggerAbsenceProof(
        "TA-07-isolation",
        "path-triggered",
        "Core §11.8, §12.7–§12.9",
        "DenialByConstructionRef",
        POKEBALL_POLICY_PATH,
        listOf("process", "security", "isolation"),
    ),
    RequiredTriggerAbsenceProof(
        "TA-08-durable-outbox",
        "path-triggered",
        "Core §9.11, §9.13, §12.4–§12.6 / PBA-42",
        "DenialByConstructionRef",
        POKEBALL_POLICY_PATH,
        listOf("durable", "outbox", "journal", "status"),
    ),
).also { proofs -> requireUniqueKeys("requiredTriggerAbsenceProofs", proofs, RequiredTriggerAbsenceProof::id) }

internal val requiredTriggerAbsenceProofIds = requiredTriggerAbsenceProofs.map(RequiredTriggerAbsenceProof::id)
private val requiredTriggerAbsenceProofById = requiredTriggerAbsenceProofs.associateBy(RequiredTriggerAbsenceProof::id)

private const val AUDIT_POLICY_MARKER = "<!-- pokeball-audit-policy"
private val requiredAuditPolicyKeys = listOf(
    "profileAuthorities",
    "effectiveProfile",
    "contentMutationPath",
    "semanticRetry",
)
private val expectedAuditPolicy = linkedMapOf(
    "profileAuthorities" to "ContentCatalog|Profile|GameplayRun|AppSession",
    "effectiveProfile" to "Inline+Transient+InProcess+Standard+Static",
    "contentMutationPath" to "NONE",
    "semanticRetry" to "ABSENT",
)

private val requiredConformanceMetadataKeys = buildList {
    add("schemaVersion")
    add("attestationStatus")
    add("implementationFreezeSha")
    add("productTreeSha256")
    add("coreCommit")
    add("coreSha256")
    add("agentPackRevision")
    add("agentPackSha256")
    add("policySha256")
    add("assemblySha256")
    add("triggerAbsenceProofsSha256")
    add("claimWording")
    add("claimBoundary")
    add("claimScope")
    add("claimBinding")
    add("claimMechanism")
    add("claimAssumptions")
    add("claimRetention")
    add("claimEvidence")
    add("claimEnvironment")
    add("claimNonGuarantees")
    add("waiverEffects")
    add("failedOrPartialUnits")
    (1..10).forEach { add("rg${it.toString().padStart(2, '0')}") }
}

private val requiredTriggerAbsenceProofKeys = listOf(
    "schemaVersion",
    "id",
    "triggerClass",
    "triggerAnchor",
    "exactScopeAndEffectiveProfile",
    "inventoryEvidence",
    "inventoryRevisionsOrDigests",
    "evaluatedPredicate",
    "conclusion",
    "evidenceOwner",
    "invalidationConditions",
)

private const val CONFORMANCE_METADATA_MARKER = "<!-- pokeball-conformance"
private const val TRIGGER_ABSENCE_PROOF_MARKER = "<!-- pokeball-trigger-absence-proof"
private const val EFFECTIVE_PROFILE = "Inline+Transient+InProcess+Standard+Static"
private const val PROOF_SCOPE =
    "KINETICKK four-authority Android/Desktop/Web application | " +
        "Inline+Transient+InProcess+Standard+Static"
private const val PROOF_OWNER = "KINETICKK project"
private const val PROOF_INVALIDATION_CONDITIONS =
    "scope-change|profile-change|version-change|inventory-change|digest-change|" +
        "unresolved-reference|conflicting-evidence"
private val fullShaRegex = Regex("[0-9a-f]{40}")
private val sha256Regex = Regex("[0-9a-f]{64}")

internal data class TriggerAbsenceProof(
    val values: Map<String, String>,
) {
    val id: String
        get() = values.getValue("id")
}

private fun prerequisiteReport(
    head: String,
    digest: String,
    violations: List<String>,
): String = buildString {
    appendLine("{")
    appendLine("  \"schema\": \"kinetickk-pokeball-conformance-report/v1\",")
    appendLine("  \"mode\": \"prerequisite-no-claim\",")
    appendLine("  \"implementationFreezeCandidate\": \"${jsonEscape(head)}\",")
    appendLine("  \"productTreeSha256\": \"${jsonEscape(digest)}\",")
    appendLine("  \"formalClaimIssued\": false,")
    appendLine("  \"docsOnlyAttestationAllowlist\": [")
    docsOnlyAttestationAllowlist.forEachIndexed { index, path ->
        append("    \"${jsonEscape(path)}\"")
        appendLine(if (index == docsOnlyAttestationAllowlist.size - 1) "" else ",")
    }
    appendLine("  ],")
    appendLine("  \"status\": \"${if (violations.isEmpty()) "PASS" else "FAIL"}\",")
    appendViolations(violations)
    appendLine("}")
}

private fun strictReport(
    head: String,
    freeze: String,
    treeDigest: String,
    recordDigest: String,
    proofsDigest: String,
    policyDigest: String,
    assemblyDigest: String,
    violations: List<String>,
): String = buildString {
    appendLine("{")
    appendLine("  \"schema\": \"kinetickk-pokeball-conformance-report/v1\",")
    appendLine("  \"mode\": \"strict-attestation\",")
    appendLine("  \"attestationCommit\": \"${jsonEscape(head)}\",")
    appendLine("  \"implementationFreezeSha\": \"${jsonEscape(freeze)}\",")
    appendLine("  \"productTreeSha256\": \"${jsonEscape(treeDigest)}\",")
    appendLine("  \"conformanceRecordSha256\": \"${jsonEscape(recordDigest)}\",")
    appendLine("  \"triggerAbsenceProofsSha256\": \"${jsonEscape(proofsDigest)}\",")
    appendLine("  \"policySha256\": \"${jsonEscape(policyDigest)}\",")
    appendLine("  \"assemblySha256\": \"${jsonEscape(assemblyDigest)}\",")
    appendLine("  \"docsOnlyAttestationAllowlist\": [")
    docsOnlyAttestationAllowlist.forEachIndexed { index, path ->
        append("    \"${jsonEscape(path)}\"")
        appendLine(if (index == docsOnlyAttestationAllowlist.size - 1) "" else ",")
    }
    appendLine("  ],")
    appendLine("  \"status\": \"${if (violations.isEmpty()) "PASS" else "FAIL"}\",")
    appendViolations(violations)
    appendLine("}")
}

private fun StringBuilder.appendViolations(violations: List<String>) {
    val normalized = violations.distinct().sorted()
    appendLine("  \"violations\": [")
    normalized.forEachIndexed { index, violation ->
        append("    \"${jsonEscape(violation)}\"")
        appendLine(if (index == normalized.lastIndex) "" else ",")
    }
    appendLine("  ]")
}

internal fun validateStrictAttestation(
    root: Path,
    head: String,
    recordText: String,
    proofBytes: ByteArray?,
    metadata: Map<String, String>,
    proofs: List<TriggerAbsenceProof>,
): List<String> = buildList {
    val freeze = metadata.getValue("implementationFreezeSha")
    addAll(validateConformanceMetadata(metadata, freeze))
    addAll(validateTriggerAbsenceProofs(root, freeze, proofs))

    if (fullShaRegex.matches(freeze)) {
        val recordCommit = lastCommitForPath(root, CONFORMANCE_RECORD_PATH)
        val proofCommit = lastCommitForPath(root, TRIGGER_ABSENCE_PROOFS_PATH)
        if (recordCommit != head) add("HEAD $head is not the committed conformance-record attestation $recordCommit")
        if (proofCommit != head) add("HEAD $head is not the committed trigger-proof attestation $proofCommit")

        val parent = runCatching { runGit(root, "rev-parse", "$head^").trim() }.getOrNull()
        if (parent != freeze) {
            add("Attestation parent $parent is not recorded implementation freeze $freeze")
        }
        addAll(validateAttestationDiff(root, freeze, head))

        val actualTreeDigest = runCatching { gitTreeDigest(root, freeze) }.getOrElse { failure ->
            add("Could not compute freeze product tree digest: ${failure.message}")
            ""
        }
        if (actualTreeDigest != metadata.getValue("productTreeSha256")) {
            add(
                "Freeze product tree digest mismatch: expected ${metadata.getValue("productTreeSha256")}, " +
                    "found $actualTreeDigest",
            )
        }
        addAll(validateFreezeDocumentDigests(root, freeze, metadata))
        val baselineText = runCatching {
            decodeStrictUtf8(gitBlob(root, freeze, POKEBALL_BASELINE_PATH), POKEBALL_BASELINE_PATH)
        }.getOrElse { failure ->
            add("Could not read frozen baseline: ${failure.message}")
            ""
        }
        projectBaselinePinViolations(baselineText).forEach { add("Frozen baseline: $it") }
        addAll(validateFrozenAuditPolicy(root, freeze))
    }

    if (metadata.getValue("coreCommit") != PokeballBaseline.CORE_COMMIT) {
        add("Claim Core commit does not match the pinned snapshot")
    }
    if (metadata.getValue("coreSha256") != PokeballBaseline.CORE_SHA256) {
        add("Claim Core digest does not match the pinned snapshot")
    }
    if (metadata.getValue("agentPackRevision") != PokeballBaseline.AGENT_PACK_REVISION.toString()) {
        add("Claim Agent Pack revision does not match the pinned snapshot")
    }
    if (metadata.getValue("agentPackSha256") != PokeballBaseline.AGENT_PACK_SHA256) {
        add("Claim Agent Pack digest does not match the pinned snapshot")
    }
    val actualProofDigest = proofBytes?.let(::sha256).orEmpty()
    if (metadata.getValue("triggerAbsenceProofsSha256") != actualProofDigest) {
        add(
            "Trigger-absence proof digest mismatch: expected " +
                "${metadata.getValue("triggerAbsenceProofsSha256")}, found $actualProofDigest",
        )
    }
    val exactClaim = "KINETICKK on implementation SHA $freeze conforms within the declared scope to " +
        "Pokeball Core 1.4.0-draft at ${PokeballBaseline.CORE_COMMIT}"
    if (metadata.getValue("claimWording") != exactClaim) {
        add("Claim wording is not the exact bounded project claim")
    }
    (1..10).forEach { number ->
        val key = "rg${number.toString().padStart(2, '0')}"
        if (metadata.getValue(key) != "PASS") add("$key must be exactly PASS")
    }

    listOf(
        "project self-attestation",
        "not external certification",
        "crash-atomic persistence",
        "exactly-once",
        "eventual delivery",
        "cross-device storage",
        "external audio/storage providers",
    ).filterNot(recordText::contains).forEach { phrase ->
        add("Conformance record is missing required boundary/non-guarantee phrase `$phrase`")
    }

    val commitMessage = runCatching { runGit(root, "show", "-s", "--format=%B", head) }.getOrDefault("")
    if (commitMessage.lineSequence().firstOrNull() != "docs(architecture): attest Pokeball conformance") {
        add("Attestation commit title must be exactly `docs(architecture): attest Pokeball conformance`")
    }
}.distinct().sorted()

internal fun parseStrictConformanceMetadata(record: String): Map<String, String> = parseSingleMetadataBlock(
    document = record,
    marker = CONFORMANCE_METADATA_MARKER,
    requiredKeys = requiredConformanceMetadataKeys,
    context = "conformance metadata",
)

internal fun parseStrictAuditPolicy(policy: String): Map<String, String> = parseSingleMetadataBlock(
    document = policy,
    marker = AUDIT_POLICY_MARKER,
    requiredKeys = requiredAuditPolicyKeys,
    context = "Pokeball audit policy",
)

internal fun auditPolicyViolations(
    policy: String,
    applicability: String,
    @Suppress("UNUSED_PARAMETER") evidenceByPath: Map<String, String>,
): List<String> = buildList {
    val auditPolicy = runCatching { parseStrictAuditPolicy(policy) }
        .getOrElse { failure ->
            add("Malformed Pokeball audit policy: ${failure.message}")
            emptyMap()
    }
    if (auditPolicy.isNotEmpty()) {
        expectedAuditPolicy.forEach { (key, expected) ->
            val actual = auditPolicy.getValue(key)
            if (actual != expected) {
                add("Pokeball audit policy $key must be exactly `$expected`; found `$actual`")
            }
        }
    }

    if ("no runtime mutation profile" in policy) {
        add("ContentCatalog cannot be exempted from effective-profile resolution merely because it has no mutation path")
    }

    val presentTriggers = applicability
        .substringAfter("## Present triggers", missingDelimiterValue = "")
        .substringBefore("## Absent trigger scopes", missingDelimiterValue = "")
    listOf(
        "explicit user semantic retry",
        "`SessionInteractionPulse.ResetRetryRequested`",
        "`ProfileModuleCommand.RetryLegacyPurge`",
        "`SessionInteractionPulse.ResetConfirmed`",
        "`ProfileModuleCommand.ConfirmLegacyReset`",
        "`ProfileModuleResult.ResetWriteRejected`",
        "`ProfileModuleResult.ResetWriteResourceFailure`",
        "`ProfileModuleResult.ResetWriteOutcomeUnknown`",
        "`PBA-24`",
    ).filter(presentTriggers::contains).forEach { token ->
        add("Applicability present-trigger inventory retains removed semantic-retry contract `$token`")
    }

}.distinct().sorted()

internal fun parseStrictTriggerAbsenceProofs(document: String): List<TriggerAbsenceProof> {
    val blocks = mutableListOf<TriggerAbsenceProof>()
    var cursor = 0
    while (true) {
        val start = document.indexOf(TRIGGER_ABSENCE_PROOF_MARKER, cursor)
        if (start < 0) break
        val contentStart = start + TRIGGER_ABSENCE_PROOF_MARKER.length
        val end = document.indexOf("-->", contentStart)
        require(end >= 0) { "Unclosed TriggerAbsenceProof block at offset $start" }
        val values = parseMetadataLines(
            block = document.substring(contentStart, end),
            requiredKeys = requiredTriggerAbsenceProofKeys,
            context = "TriggerAbsenceProof #${blocks.size + 1}",
        )
        blocks += TriggerAbsenceProof(values)
        cursor = end + 3
    }
    require(blocks.size == requiredTriggerAbsenceProofIds.size) {
        "Expected ${requiredTriggerAbsenceProofIds.size} TriggerAbsenceProof blocks, found ${blocks.size}"
    }
    val ids = blocks.map(TriggerAbsenceProof::id)
    require(ids.distinct().size == ids.size) { "Duplicate TriggerAbsenceProof ID" }
    require(ids == requiredTriggerAbsenceProofIds) {
        "TriggerAbsenceProof order/IDs mismatch: expected ${requiredTriggerAbsenceProofIds.joinToString()}, " +
            "found ${ids.joinToString()}"
    }
    return blocks
}

private fun parseSingleMetadataBlock(
    document: String,
    marker: String,
    requiredKeys: List<String>,
    context: String,
): Map<String, String> {
    val first = document.indexOf(marker)
    require(first >= 0) { "Missing $context block" }
    require(document.indexOf(marker, first + marker.length) < 0) { "Duplicate $context block" }
    val contentStart = first + marker.length
    val end = document.indexOf("-->", contentStart)
    require(end >= 0) { "Unclosed $context block" }
    return parseMetadataLines(document.substring(contentStart, end), requiredKeys, context)
}

private fun parseMetadataLines(
    block: String,
    requiredKeys: List<String>,
    context: String,
): Map<String, String> {
    val values = linkedMapOf<String, String>()
    block.lineSequence().forEachIndexed { index, rawLine ->
        val line = rawLine.trim()
        if (line.isBlank()) return@forEachIndexed
        require(line.count { it == '=' } == 1) {
            "Malformed $context line ${index + 1}: $line"
        }
        val key = line.substringBefore('=').trim()
        val value = line.substringAfter('=').trim()
        require(Regex("[A-Za-z][A-Za-z0-9]*").matches(key) && value.isNotBlank()) {
            "Malformed $context line ${index + 1}: $line"
        }
        require(key in requiredKeys) { "Unknown $context key $key" }
        require(values.put(key, value) == null) { "Duplicate $context key $key" }
    }
    val missing = requiredKeys.filterNot(values::containsKey)
    require(missing.isEmpty()) { "Missing $context keys: ${missing.joinToString()}" }
    return values
}

private fun validateConformanceMetadata(
    metadata: Map<String, String>,
    freeze: String,
): List<String> = buildList {
    if (metadata.getValue("schemaVersion") != "1") add("schemaVersion must be exactly 1")
    if (metadata.getValue("attestationStatus") != "PASS") add("attestationStatus must be exactly PASS")
    if (!fullShaRegex.matches(freeze)) add("implementationFreezeSha is not a full Git SHA")
    if (!sha256Regex.matches(metadata.getValue("productTreeSha256"))) {
        add("productTreeSha256 is not a lowercase SHA-256")
    }
    listOf("coreSha256", "agentPackSha256", "policySha256", "assemblySha256", "triggerAbsenceProofsSha256")
        .filterNot { sha256Regex.matches(metadata.getValue(it)) }
        .forEach { add("$it is not a lowercase SHA-256") }
    if (metadata.getValue("agentPackRevision").toIntOrNull() == null) {
        add("agentPackRevision must be a decimal integer")
    }
    if (metadata.getValue("claimBinding") != EFFECTIVE_PROFILE) {
        add("claimBinding must be exactly $EFFECTIVE_PROFILE")
    }
    validateSubstantiveField(metadata, "claimBoundary")?.let(::add)
    if (freeze !in metadata.getValue("claimBoundary") ||
        PokeballBaseline.CORE_COMMIT !in metadata.getValue("claimBoundary")
    ) {
        add("claimBoundary must bind the full implementation-freeze and Core commits")
    }
    validateSubstantiveField(metadata, "claimScope")?.let(::add)
    listOf("Profile", "GameplayRun", "AppSession", "ContentCatalog", "Android", "Desktop", "Web")
        .filterNot(metadata.getValue("claimScope")::contains)
        .forEach { add("claimScope is missing $it") }
    listOf("claimMechanism", "claimAssumptions", "claimRetention").forEach { key ->
        validateSubstantiveField(metadata, key)?.let(::add)
    }
    val evidence = metadata.getValue("claimEvidence")
    validateSubstantiveField(metadata, "claimEvidence")?.let(::add)
    listOf(
        "RG-01..RG-10",
        "verifyArchitecture",
        "verifyPokeballArchitecture",
        "desktopTest",
        "wasmJsBrowserTest",
        "wasmJsBrowserDistribution",
    ).filterNot(evidence::contains).forEach { add("claimEvidence is missing $it") }
    addAll(validateEnvironment(metadata.getValue("claimEnvironment")))
    val nonGuarantees = metadata.getValue("claimNonGuarantees")
    listOf(
        "crash-atomic persistence",
        "exactly-once",
        "eventual delivery",
        "cross-device storage",
        "external audio/storage providers",
    ).filterNot(nonGuarantees::contains).forEach { add("claimNonGuarantees is missing $it") }
    if (metadata.getValue("waiverEffects") != "NONE") add("waiverEffects must be exactly NONE")
    if (metadata.getValue("failedOrPartialUnits") != "NONE") {
        add("failedOrPartialUnits must be exactly NONE")
    }
}

private fun validateSubstantiveField(metadata: Map<String, String>, key: String): String? {
    val value = metadata.getValue(key)
    val placeholder = listOf("TBD", "TODO", "UNKNOWN", "N/A", "<", ">").any {
        it in value.uppercase()
    }
    return if (value.length < 16 || placeholder) "$key is incomplete or contains a placeholder" else null
}

private fun validateEnvironment(value: String): List<String> = buildList {
    val expectedKeys = listOf("JDK", "Gradle", "Kotlin", "Node", "Chromium")
    val entries = value.split(';').map(String::trim)
    val parsedKeys = entries.map { it.substringBefore(':', missingDelimiterValue = "") }
    if (parsedKeys != expectedKeys) {
        add("claimEnvironment must use ordered JDK;Gradle;Kotlin;Node;Chromium entries")
    }
    entries.forEachIndexed { index, entry ->
        val expectedKey = expectedKeys.getOrNull(index).orEmpty()
        val actualValue = entry.substringAfter(':', missingDelimiterValue = "").trim()
        if (expectedKey.isNotEmpty() &&
            (actualValue.isBlank() || actualValue.contains('<') || actualValue.contains('>'))
        ) {
            add("claimEnvironment $expectedKey value is missing or a placeholder")
        }
    }
}

private fun validateAttestationDiff(root: Path, freeze: String, head: String): List<String> {
    val diff = runCatching {
        runGit(root, "diff", "--name-status", "--no-renames", "$freeze..$head")
    }.getOrElse {
        return listOf("Could not inspect attestation diff for recorded freeze $freeze")
    }
    return buildList {
        val entries = diff
            .lineSequence()
            .filter(String::isNotBlank)
            .map { line ->
                val parts = line.split('\t')
                parts.first() to parts.drop(1).singleOrNull().orEmpty()
            }
            .toList()
        entries.forEach { (status, path) ->
            if (path !in docsOnlyAttestationAllowlist) add("Non-attestation path changed after freeze: $path")
            if (status !in setOf("A", "M")) add("Attestation path has forbidden Git status $status: $path")
        }
        val changedPaths = entries.map { it.second }.toSet()
        listOf(CONFORMANCE_RECORD_PATH, TRIGGER_ABSENCE_PROOFS_PATH).filterNot(changedPaths::contains).forEach {
            add("Required attestation path did not change after freeze: $it")
        }
    }
}

private fun validateFreezeDocumentDigests(
    root: Path,
    freeze: String,
    metadata: Map<String, String>,
): List<String> = buildList {
    listOf(
        "policySha256" to POKEBALL_POLICY_PATH,
        "assemblySha256" to POKEBALL_ASSEMBLY_PATH,
    ).forEach { (key, path) ->
        val actual = runCatching { sha256(gitBlob(root, freeze, path)) }.getOrElse { failure ->
            add("Could not read frozen $path: ${failure.message}")
            ""
        }
        if (metadata.getValue(key) != actual) {
            add("$key mismatch for frozen $path: expected ${metadata.getValue(key)}, found $actual")
        }
    }
}

private fun validateFrozenAuditPolicy(root: Path, revision: String): List<String> = buildList {
    fun readFrozenText(path: String): String? = runCatching {
        decodeStrictUtf8(gitBlob(root, revision, path), path)
    }.getOrElse { failure ->
        add("Could not read frozen $path: ${failure.message}")
        null
    }

    val policy = readFrozenText(POKEBALL_POLICY_PATH)
    val applicability = readFrozenText(POKEBALL_APPLICABILITY_PATH)
    if (policy != null && applicability != null) {
        addAll(auditPolicyViolations(policy, applicability, emptyMap()))
    }
}

private fun validateTriggerAbsenceProofs(
    root: Path,
    freeze: String,
    proofs: List<TriggerAbsenceProof>,
): List<String> = buildList {
    proofs.forEach { proof ->
        val values = proof.values
        val prefix = "TriggerAbsenceProof ${proof.id}"
        val required = requiredTriggerAbsenceProofById.getValue(proof.id)
        if (values.getValue("schemaVersion") != "1") add("$prefix schemaVersion must be exactly 1")
        val triggerClass = values.getValue("triggerClass")
        if (triggerClass != required.triggerClass) {
            add("$prefix triggerClass must be exactly `${required.triggerClass}`")
        }
        val anchor = values.getValue("triggerAnchor")
        if (anchor != required.triggerAnchor) {
            add("$prefix triggerAnchor must be exactly `${required.triggerAnchor}`")
        }
        if (values.getValue("exactScopeAndEffectiveProfile") != PROOF_SCOPE) {
            add("$prefix exactScopeAndEffectiveProfile must be exactly `$PROOF_SCOPE`")
        }
        val inventory = values.getValue("inventoryEvidence")
        val inventoryPath = inventory.substringAfter(':', missingDelimiterValue = "")
        if (inventory != required.inventoryEvidence) {
            add("$prefix inventoryEvidence must be exactly `${required.inventoryEvidence}`")
        }
        if (!isCanonicalRepositoryPath(inventoryPath)) {
            add("$prefix inventoryEvidence path is not a canonical repository-relative path: $inventoryPath")
        }
        val revisionAndDigest = Regex("git:([0-9a-f]{40});sha256:([0-9a-f]{64})")
            .matchEntire(values.getValue("inventoryRevisionsOrDigests"))
        if (revisionAndDigest == null) {
            add("$prefix inventoryRevisionsOrDigests must be git:<sha>;sha256:<digest>")
        } else {
            val revision = revisionAndDigest.groupValues[1]
            val expectedDigest = revisionAndDigest.groupValues[2]
            if (revision != freeze) add("$prefix inventory revision does not equal the implementation freeze")
            if (isCanonicalRepositoryPath(inventoryPath)) {
                val actualDigest = runCatching { sha256(gitBlob(root, freeze, inventoryPath)) }.getOrElse { failure ->
                    add("$prefix inventory cannot be read at the freeze: ${failure.message}")
                    ""
                }
                if (actualDigest != expectedDigest) {
                    add("$prefix inventory digest mismatch: expected $expectedDigest, found $actualDigest")
                }
            }
        }
        val predicate = values.getValue("evaluatedPredicate")
        if (!predicate.startsWith("Absent iff ") || predicate.length < 24 || hasPlaceholder(predicate)) {
            add("$prefix evaluatedPredicate must be a concrete `Absent iff ...` predicate")
        }
        required.predicateTokens
            .filterNot { it in predicate.lowercase() }
            .forEach { add("$prefix evaluatedPredicate is missing category token `$it`") }
        if (values.getValue("conclusion") != "Absent") add("$prefix conclusion must be exactly Absent")
        if (values.getValue("evidenceOwner") != PROOF_OWNER) {
            add("$prefix evidenceOwner must be exactly `$PROOF_OWNER`")
        }
        if (values.getValue("invalidationConditions") != PROOF_INVALIDATION_CONDITIONS) {
            add("$prefix invalidationConditions must be exactly `$PROOF_INVALIDATION_CONDITIONS`")
        }
    }
}

private fun hasPlaceholder(value: String): Boolean = listOf("TBD", "TODO", "UNKNOWN", "N/A", "<", ">")
    .any { it in value.uppercase() }

private fun isCanonicalRepositoryPath(value: String): Boolean {
    if (value.isBlank() || value.startsWith('/') || '\\' in value || ':' in value) return false
    if (!Regex("[A-Za-z0-9._/-]+").matches(value)) return false
    val segments = value.split('/')
    return segments.none { it.isBlank() || it == "." || it == ".." }
}

internal fun projectBaselinePinViolations(baseline: String): List<String> {
    val immutableSection = baseline.substringAfter("## Immutable inputs", missingDelimiterValue = "")
        .substringBefore("\n## ")
    if (immutableSection.isBlank()) return listOf("baseline.md is missing the Immutable inputs section")
    val normalized = immutableSection.replace("`", "").replace(Regex("\\s+"), " ").trim()
    val markers = listOf(
        "repository/commit" to
            "Pokeball repository: git@github.com:4wl2d/Pokeball.git at ${PokeballBaseline.CORE_COMMIT}.",
        "entrypoint/version/status" to
            "Core entrypoint: spec/pokeball-architecture-core.md, declared version " +
            "${PokeballBaseline.CORE_VERSION}, status ${PokeballBaseline.CORE_STATUS}.",
        "Core order/count/bytes/digest" to
            "Ordered Core set: ${PokeballBaseline.CORE_FILE_COUNT} files, " +
            "${PokeballBaseline.CORE_BYTES} exact bytes, " +
            "SHA-256 ${PokeballBaseline.CORE_SHA256}.",
        "Agent Pack revision/count/digest" to
            "Agent Pack: revision ${PokeballBaseline.AGENT_PACK_REVISION}, " +
            "${PokeballBaseline.AGENT_PACK_FILE_COUNT} Markdown files including BASELINE.md; its digest covers the " +
            "other ${PokeballBaseline.AGENT_PACK_FILE_COUNT - 1} files and is SHA-256 " +
            "${PokeballBaseline.AGENT_PACK_SHA256}.",
    )
    val indexes = markers.map { (label, marker) ->
        val index = normalized.indexOf(marker)
        label to index
    }
    return buildList {
        indexes.filter { it.second < 0 }.forEach { (label) -> add("baseline.md snapshot pin mismatch: $label") }
        markers.forEach { (label, marker) ->
            if (normalized.indexOf(marker) >= 0 && normalized.indexOf(marker) != normalized.lastIndexOf(marker)) {
                add("baseline.md snapshot pin is duplicated: $label")
            }
        }
        val presentIndexes = indexes.map { it.second }
        if (presentIndexes.all { it >= 0 } && presentIndexes != presentIndexes.sorted()) {
            add("baseline.md snapshot pins are out of order")
        }
    }
}

private fun snapshotIntegrityReport(
    result: SnapshotVerification?,
    violations: List<String>,
): String = buildString {
    appendLine("{")
    appendLine("  \"schema\": \"kinetickk-pokeball-snapshot-integrity-report/v1\",")
    appendLine("  \"status\": \"${if (violations.isEmpty()) "PASS" else "FAIL"}\",")
    appendLine("  \"coreCommit\": \"${PokeballBaseline.CORE_COMMIT}\",")
    appendLine("  \"coreVersion\": \"${PokeballBaseline.CORE_VERSION}\",")
    appendLine("  \"coreStatus\": \"${PokeballBaseline.CORE_STATUS}\",")
    appendLine("  \"coreFiles\": ${result?.coreFiles ?: "null"},")
    appendLine("  \"coreBytes\": ${result?.coreBytes ?: "null"},")
    appendLine("  \"coreSha256\": \"${result?.coreSha256.orEmpty()}\",")
    appendLine("  \"agentPackRevision\": ${PokeballBaseline.AGENT_PACK_REVISION},")
    appendLine("  \"agentPackFiles\": ${result?.agentPackFiles ?: "null"},")
    appendLine("  \"agentPackSha256\": \"${result?.agentPackSha256.orEmpty()}\",")
    appendViolations(violations)
    appendLine("}")
}

internal fun gitWorktreeEntries(root: Path): List<String> = runGit(
    root,
    "status",
    "--porcelain=v1",
    "--untracked-files=all",
    "--ignore-submodules=none",
).lineSequence().filter(String::isNotBlank).sorted().toList()

private fun committedFileBytesOrNull(root: Path, revision: String, path: String): ByteArray? =
    runCatching { gitBlob(root, revision, path) }.getOrNull()

private fun committedWorkspaceFileViolations(
    root: Path,
    path: String,
    committedBytes: ByteArray,
): List<String> {
    val workspacePath = root.resolve(path)
    if (!Files.isRegularFile(workspacePath) || Files.isSymbolicLink(workspacePath)) {
        return listOf("Committed attestation path is not a regular worktree file: $path")
    }
    return if (Files.readAllBytes(workspacePath).contentEquals(committedBytes)) {
        emptyList()
    } else {
        listOf("Worktree bytes differ from committed attestation bytes: $path")
    }
}

private fun decodeStrictUtf8(bytes: ByteArray, path: String): String = try {
    StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
} catch (failure: Throwable) {
    throw IllegalArgumentException("$path is not strict UTF-8", failure)
}

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it) }

private fun lastCommitForPath(root: Path, path: String): String = runCatching {
    runGit(root, "log", "-1", "--format=%H", "--", path).trim()
}.getOrDefault("")

private fun conformanceFailure(report: Path, violations: List<String>): GradleException = GradleException(
    buildString {
        appendLine("Pokeball conformance attestation verification failed:")
        violations.distinct().sorted().forEach { appendLine(" - $it") }
        append("Report: ${report.toFile().invariantSeparatorsPath}")
    },
)
