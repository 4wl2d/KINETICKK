// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource.performance

import java.security.MessageDigest
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.ContentVersion
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.DamageNumberSize
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileBootstrapResourceResult
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileV4Rejection
import kinetickk.ball.profile.api.ProfileV4Snapshot
import kinetickk.ball.profile.api.ProfileV4WriteResult
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.SIMULATION_SPEED_OPTIONS
import kinetickk.ball.profile.resource.ExactProfilePersistence
import kinetickk.ball.profile.resource.MAX_PROFILE_PAYLOAD_BYTES
import kinetickk.ball.profile.resource.PROFILE_SCHEMA_VERSION
import kinetickk.ball.profile.resource.ProfileProviderMutationResult
import kinetickk.ball.profile.resource.ProfileProviderReadResult
import kinetickk.ball.profile.resource.ProfileV4Codec
import kinetickk.ball.profile.resource.ProfileV4DecodeResult
import kinetickk.ball.profile.resource.ProfileV4EncodeResult
import kinetickk.ball.profile.resource.createProfileResource
import kinetickk.performance.BenchmarkScenario
import kinetickk.performance.BenchmarkSuiteIdentity
import kinetickk.performance.BenchmarkValidation
import kinetickk.performance.BenchmarkValidationContext
import kinetickk.performance.runBenchmarkSuite

private const val SUITE_VERSION = "profile-persistence-v2"
private const val DEFAULT_CONTENT_VERSION = "benchmark-content"

fun main() {
    runBenchmarkSuite(
        identity = BenchmarkSuiteIdentity(
            suiteVersion = SUITE_VERSION,
            adapter = "feature-pokeball-full-refactor",
            label = System.getProperty(
                "kinetickk.benchmark.label",
                "feature/pokeball-full-refactor",
            ),
            revision = System.getProperty("kinetickk.benchmark.revision", "unknown"),
            dirty = System.getProperty("kinetickk.benchmark.dirty", "false").toBoolean(),
        ),
        scenarios = profileBenchmarkScenarios(),
    )
}

internal fun profileBenchmarkScenarios(): List<BenchmarkScenario> =
    ProfileBenchmarkFixtures().scenarios()

private class ProfileBenchmarkFixtures {
    private val defaultSnapshot = ProfileV4Snapshot(
        contentVersion = ContentVersion(DEFAULT_CONTENT_VERSION),
        revision = ProfileRevision(0L),
        legacyResetConfirmed = false,
        profile = PlayerProfile(),
    )
    private val defaultPayload = encodeOrFail(defaultSnapshot)
    private val logicalMaximumSnapshot = schemaMaximumSnapshot(
        ContentVersion(DEFAULT_CONTENT_VERSION),
    )
    private val logicalMaximumPayload = encodeOrFail(logicalMaximumSnapshot)
    private val maximumSnapshot = exactMaximumPayloadSnapshot()
    private val maximumPayload = encodeOrFail(maximumSnapshot).also { payload ->
        check(payload.encodeToByteArray().size == MAX_PROFILE_PAYLOAD_BYTES)
    }
    private val malformedPayload = maximumPayload.dropLast(1)
    private val oversizePayload = "x".repeat(MAX_PROFILE_PAYLOAD_BYTES + 1)
    private val unknownFieldPayload = defaultPayload.replace(
        "\"schemaVersion\":$PROFILE_SCHEMA_VERSION,",
        "\"schemaVersion\":$PROFILE_SCHEMA_VERSION,\"unknown\":0,",
    )
    private val nonCanonicalPayload = "$defaultPayload\n"
    private val invalidUtf8Payload = "\uD800"
    private val defaultMetadata = logicalPayloadMetadata(
        logicalShape = "default",
        snapshot = defaultSnapshot,
        payload = defaultPayload,
    )
    private val logicalMaximumMetadata = logicalPayloadMetadata(
        logicalShape = "maximum",
        snapshot = logicalMaximumSnapshot,
        payload = logicalMaximumPayload,
    )
    private val maximumMetadata = boundaryPayloadMetadata(maximumSnapshot, maximumPayload)
    private val defaultReadResource = createProfileResource(
        InMemoryExactProfilePersistence(defaultPayload),
    )
    private val maximumReadResource = createProfileResource(
        InMemoryExactProfilePersistence(maximumPayload),
    )
    private val malformedReadResource = createProfileResource(
        InMemoryExactProfilePersistence(malformedPayload),
    )
    private val emptyReadResource = createProfileResource(
        InMemoryExactProfilePersistence(null),
    )
    private val defaultWriteResource = createProfileResource(InMemoryExactProfilePersistence(null))
    private val maximumWriteResource = createProfileResource(InMemoryExactProfilePersistence(null))
    private var controlCounter = 1L

    fun scenarios(): List<BenchmarkScenario> = listOf(
        BenchmarkScenario(
            name = "profile_harness_control",
            category = "harness",
            description = "Lambda, loop, timer, counter and blackhole floor for this profile suite.",
            maximumOperations = 5_000_000,
        ) { validation ->
            controlCounter = controlCounter * 2_862_933_555_777_941_757L + 3_037_000_493L
            validation.validated(controlCounter)
        },
        BenchmarkScenario(
            name = "profile_encode_default",
            category = "codec",
            description = "Validate and canonically encode the default v4 profile.",
            metadata = defaultMetadata,
            maximumOperations = 100_000,
        ) { validation ->
            validation.validated(payloadSignature(encodeOrFail(defaultSnapshot)))
        },
        BenchmarkScenario(
            name = "profile_decode_default",
            category = "codec",
            description = "Strictly decode and canonically re-encode the default v4 payload.",
            metadata = defaultMetadata,
            maximumOperations = 100_000,
        ) { validation ->
            validation.validated(snapshotSignature(decodeOrFail(defaultPayload)))
        },
        BenchmarkScenario(
            name = "profile_roundtrip_default",
            category = "codec",
            description = "Encode then strictly decode the default v4 profile.",
            metadata = defaultMetadata,
            maximumOperations = 50_000,
        ) { validation ->
            val payload = encodeOrFail(defaultSnapshot)
            validation.validated(
                snapshotSignature(decodeOrFail(payload)) xor payloadSignature(payload),
            )
        },
        BenchmarkScenario(
            name = "profile_encode_logical_maximum",
            category = "codec",
            description = "Encode every branch-native strict-v4 logical collection and value at its maximum.",
            metadata = logicalMaximumMetadata,
            maximumOperations = 10_000,
        ) { validation ->
            validation.validated(payloadSignature(encodeOrFail(logicalMaximumSnapshot)))
        },
        BenchmarkScenario(
            name = "profile_decode_logical_maximum",
            category = "codec",
            description = "Strictly decode the branch-native v4 logical-maximum payload without padding.",
            metadata = logicalMaximumMetadata,
            maximumOperations = 10_000,
        ) { validation ->
            validation.validated(snapshotSignature(decodeOrFail(logicalMaximumPayload)))
        },
        BenchmarkScenario(
            name = "profile_roundtrip_logical_maximum",
            category = "codec",
            description = "Encode then strictly decode the branch-native v4 logical-maximum profile.",
            metadata = logicalMaximumMetadata,
            maximumOperations = 5_000,
        ) { validation ->
            val payload = encodeOrFail(logicalMaximumSnapshot)
            validation.validated(
                snapshotSignature(decodeOrFail(payload)) xor payloadSignature(payload),
            )
        },
        BenchmarkScenario(
            name = "profile_encode_maximum",
            category = "codec",
            description = "Validate and encode the synthetic strict-v4 65,536-byte payload boundary fixture.",
            metadata = maximumMetadata,
            maximumOperations = 5_000,
        ) { validation ->
            validation.validated(payloadSignature(encodeOrFail(maximumSnapshot)))
        },
        BenchmarkScenario(
            name = "profile_decode_maximum",
            category = "codec",
            description = "Strictly decode and canonically re-encode the synthetic v4 payload boundary fixture.",
            metadata = maximumMetadata,
            maximumOperations = 2_000,
        ) { validation ->
            validation.validated(snapshotSignature(decodeOrFail(maximumPayload)))
        },
        BenchmarkScenario(
            name = "profile_roundtrip_maximum",
            category = "codec",
            description = "Roundtrip the synthetic exact-limit v4 payload boundary fixture.",
            metadata = maximumMetadata,
            maximumOperations = 1_000,
        ) { validation ->
            val payload = encodeOrFail(maximumSnapshot)
            validation.validated(
                snapshotSignature(decodeOrFail(payload)) xor payloadSignature(payload),
            )
        },
        BenchmarkScenario(
            name = "profile_decode_malformed_rejection",
            category = "codec_rejection",
            description = "Reject malformed JSON only after traversing an almost-maximum payload.",
            metadata = rejectionMetadata(malformedPayload, ProfileV4Rejection.MALFORMED_JSON),
            maximumOperations = 5_000,
        ) { validation ->
            validation.validated(
                rejectionSignature(malformedPayload, ProfileV4Rejection.MALFORMED_JSON),
            )
        },
        BenchmarkScenario(
            name = "profile_decode_oversize_rejection",
            category = "codec_rejection",
            description = "Reject the first payload byte beyond the 65,536-byte boundary before JSON parsing.",
            metadata = rejectionMetadata(oversizePayload, ProfileV4Rejection.PAYLOAD_TOO_LARGE),
            maximumOperations = 10_000,
        ) { validation ->
            validation.validated(
                rejectionSignature(oversizePayload, ProfileV4Rejection.PAYLOAD_TOO_LARGE),
            )
        },
        BenchmarkScenario(
            name = "profile_decode_unknown_field_rejection",
            category = "codec_rejection",
            description = "Reject an unknown JSON field under the strict v4 schema.",
            metadata = rejectionMetadata(unknownFieldPayload, ProfileV4Rejection.MALFORMED_JSON),
            maximumOperations = 50_000,
        ) { validation ->
            validation.validated(
                rejectionSignature(unknownFieldPayload, ProfileV4Rejection.MALFORMED_JSON),
            )
        },
        BenchmarkScenario(
            name = "profile_decode_noncanonical_rejection",
            category = "codec_rejection",
            description = "Decode and re-encode before rejecting a semantically valid but non-canonical payload.",
            metadata = rejectionMetadata(nonCanonicalPayload, ProfileV4Rejection.NON_CANONICAL_PAYLOAD),
            maximumOperations = 50_000,
        ) { validation ->
            validation.validated(
                rejectionSignature(
                    nonCanonicalPayload,
                    ProfileV4Rejection.NON_CANONICAL_PAYLOAD,
                ),
            )
        },
        BenchmarkScenario(
            name = "profile_decode_invalid_utf8_rejection",
            category = "codec_rejection",
            description = "Reject an unpaired UTF-16 surrogate before JSON parsing.",
            metadata = rejectionMetadata(invalidUtf8Payload, ProfileV4Rejection.INVALID_UTF8),
            maximumOperations = 200_000,
        ) { validation ->
            validation.validated(
                rejectionSignature(invalidUtf8Payload, ProfileV4Rejection.INVALID_UTF8),
            )
        },
        BenchmarkScenario(
            name = "profile_resource_read_empty",
            category = "resource",
            description = "Read the exact in-memory provider when v4 and both legacy keys are absent.",
            metadata = resourceMetadata(emptyMap(), "empty"),
            maximumOperations = 500_000,
        ) { validation ->
            validation.validated(bootstrapSignature(emptyReadResource.readBootstrap()))
        },
        BenchmarkScenario(
            name = "profile_resource_read_default",
            category = "resource",
            description = "Read and strictly decode the default payload from an exact in-memory provider.",
            metadata = resourceMetadata(defaultMetadata, "observed"),
            maximumOperations = 100_000,
        ) { validation ->
            validation.validated(bootstrapSignature(defaultReadResource.readBootstrap()))
        },
        BenchmarkScenario(
            name = "profile_resource_read_maximum",
            category = "resource",
            description = "Read and strictly decode the exact-limit payload from an exact in-memory provider.",
            metadata = resourceMetadata(maximumMetadata, "observed"),
            maximumOperations = 2_000,
        ) { validation ->
            validation.validated(bootstrapSignature(maximumReadResource.readBootstrap()))
        },
        BenchmarkScenario(
            name = "profile_resource_read_malformed_rejection",
            category = "resource",
            description = "Read an almost-maximum malformed payload and map its typed Resource rejection.",
            metadata = resourceMetadata(
                rejectionMetadata(malformedPayload, ProfileV4Rejection.MALFORMED_JSON),
                "rejected",
            ),
            maximumOperations = 5_000,
        ) { validation ->
            validation.validated(bootstrapSignature(malformedReadResource.readBootstrap()))
        },
        BenchmarkScenario(
            name = "profile_resource_write_readback_default",
            category = "resource",
            description = "Encode, write and confirm an exact readback using only an in-memory provider.",
            metadata = resourceMetadata(defaultMetadata, "write-readback"),
            maximumOperations = 100_000,
        ) { validation ->
            validation.validated(writeSignature(defaultWriteResource.writeV4(defaultSnapshot)))
        },
        BenchmarkScenario(
            name = "profile_resource_write_readback_maximum",
            category = "resource",
            description = "Encode, write and confirm an exact readback for the 65,536-byte payload in memory.",
            metadata = resourceMetadata(maximumMetadata, "write-readback"),
            maximumOperations = 5_000,
        ) { validation ->
            validation.validated(writeSignature(maximumWriteResource.writeV4(maximumSnapshot)))
        },
    ).map { scenario ->
        val expectedResult = expectedTimedResult(scenario.name)
        scenario.copy(
            metadata = scenario.metadata + ("outcomeFingerprint" to expectedResult.toString()),
            validation = BenchmarkValidation(
                expectedTimedResult = expectedResult,
                expectedOutcomeWitness = expectedResult,
                prepareProbe = {
                    if (scenario.category == "harness") controlCounter = 1L
                },
            ),
        )
    }

    private fun expectedTimedResult(name: String): Long = when (name) {
        "profile_harness_control" -> nextControlValue(1L)
        "profile_encode_default" -> payloadSignature(encodeOrFail(defaultSnapshot))
        "profile_decode_default" -> snapshotSignature(decodeOrFail(defaultPayload))
        "profile_roundtrip_default" -> encodeOrFail(defaultSnapshot).let { payload ->
            snapshotSignature(decodeOrFail(payload)) xor payloadSignature(payload)
        }
        "profile_encode_logical_maximum" ->
            payloadSignature(encodeOrFail(logicalMaximumSnapshot))
        "profile_decode_logical_maximum" ->
            snapshotSignature(decodeOrFail(logicalMaximumPayload))
        "profile_roundtrip_logical_maximum" -> encodeOrFail(logicalMaximumSnapshot).let { payload ->
            snapshotSignature(decodeOrFail(payload)) xor payloadSignature(payload)
        }
        "profile_encode_maximum" -> payloadSignature(encodeOrFail(maximumSnapshot))
        "profile_decode_maximum" -> snapshotSignature(decodeOrFail(maximumPayload))
        "profile_roundtrip_maximum" -> encodeOrFail(maximumSnapshot).let { payload ->
            snapshotSignature(decodeOrFail(payload)) xor payloadSignature(payload)
        }
        "profile_decode_malformed_rejection" ->
            rejectionSignature(malformedPayload, ProfileV4Rejection.MALFORMED_JSON)
        "profile_decode_oversize_rejection" ->
            rejectionSignature(oversizePayload, ProfileV4Rejection.PAYLOAD_TOO_LARGE)
        "profile_decode_unknown_field_rejection" ->
            rejectionSignature(unknownFieldPayload, ProfileV4Rejection.MALFORMED_JSON)
        "profile_decode_noncanonical_rejection" ->
            rejectionSignature(nonCanonicalPayload, ProfileV4Rejection.NON_CANONICAL_PAYLOAD)
        "profile_decode_invalid_utf8_rejection" ->
            rejectionSignature(invalidUtf8Payload, ProfileV4Rejection.INVALID_UTF8)
        "profile_resource_read_empty" -> bootstrapSignature(
            createProfileResource(InMemoryExactProfilePersistence(null)).readBootstrap(),
        )
        "profile_resource_read_default" -> bootstrapSignature(
            createProfileResource(InMemoryExactProfilePersistence(defaultPayload)).readBootstrap(),
        )
        "profile_resource_read_maximum" -> bootstrapSignature(
            createProfileResource(InMemoryExactProfilePersistence(maximumPayload)).readBootstrap(),
        )
        "profile_resource_read_malformed_rejection" -> bootstrapSignature(
            createProfileResource(InMemoryExactProfilePersistence(malformedPayload)).readBootstrap(),
        )
        "profile_resource_write_readback_default" -> writeSignature(
            createProfileResource(InMemoryExactProfilePersistence(null)).writeV4(defaultSnapshot),
        )
        "profile_resource_write_readback_maximum" -> writeSignature(
            createProfileResource(InMemoryExactProfilePersistence(null)).writeV4(maximumSnapshot),
        )
        else -> error("Missing expected timed result for profile benchmark scenario: $name")
    }

    private fun nextControlValue(value: Long): Long =
        value * 2_862_933_555_777_941_757L + 3_037_000_493L

    private fun exactMaximumPayloadSnapshot(): ProfileV4Snapshot {
        val base = schemaMaximumSnapshot(ContentVersion(DEFAULT_CONTENT_VERSION))
        val basePayloadBytes = encodeOrFail(base).encodeToByteArray().size
        val bytesWithoutVersion = basePayloadBytes - DEFAULT_CONTENT_VERSION.encodeToByteArray().size
        val exactVersionBytes = MAX_PROFILE_PAYLOAD_BYTES - bytesWithoutVersion
        check(exactVersionBytes > 0)
        return schemaMaximumSnapshot(ContentVersion("x".repeat(exactVersionBytes)))
    }

    private fun schemaMaximumSnapshot(contentVersion: ContentVersion): ProfileV4Snapshot =
        ProfileV4Snapshot(
            contentVersion = contentVersion,
            revision = ProfileRevision(Long.MAX_VALUE),
            legacyResetConfirmed = true,
            profile = PlayerProfile(
                preferences = PlayerPreferences(
                    soundEnabled = false,
                    musicEnabled = true,
                    masterVolume = 1f,
                    simulationSpeed = SIMULATION_SPEED_OPTIONS.last(),
                    textScale = 1.75f,
                    screenShake = false,
                    particleDensity = ParticleDensity.HIGH,
                    damageNumbers = false,
                    damageNumberSize = DamageNumberSize.HUGE,
                    damageNumberFormat = DamageNumberFormat.FULL,
                    damageNumberTierThreshold = DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.last(),
                ),
                economy = PlayerEconomy(
                    matter = Long.MAX_VALUE - 1L,
                    lifetimeMatter = Long.MAX_VALUE,
                ),
                loadout = PlayerLoadout(
                    coreShape = CoreShape.SHARD,
                    selectedWeapon = WeaponId.entries.last(),
                    unlockedWeapons = WeaponId.entries.reversed().toSet(),
                ),
                labProgress = LabProgress(List(MetaUpgradeId.entries.size) { Int.MAX_VALUE }),
                collection = PlayerCollection(
                    (0 until ContentBounds.MAX_ITEMS).reversed().toSet(),
                ),
                rebirthProgress = RebirthProgress(
                    level = ContentBounds.MAX_REBIRTH_LEVEL,
                    highestCleared = ContentBounds.MAX_REBIRTH_LEVEL,
                ),
            ),
        )
}

private class InMemoryExactProfilePersistence(
    initialPayload: String?,
) : ExactProfilePersistence {
    private var payload: String? = initialPayload

    override fun readV4(): ProfileProviderReadResult =
        ProfileProviderReadResult.Observed(payload)

    override fun writeV4(payload: String): ProfileProviderMutationResult {
        this.payload = payload
        return ProfileProviderMutationResult.COMPLETED
    }

    override fun readLegacyProgressV2(): ProfileProviderReadResult =
        ProfileProviderReadResult.Observed(null)

    override fun readLegacyMatter(): ProfileProviderReadResult =
        ProfileProviderReadResult.Observed(null)

    override fun removeLegacyProgressV2(): ProfileProviderMutationResult =
        ProfileProviderMutationResult.COMPLETED

    override fun removeLegacyMatter(): ProfileProviderMutationResult =
        ProfileProviderMutationResult.COMPLETED
}

private fun logicalPayloadMetadata(
    logicalShape: String,
    snapshot: ProfileV4Snapshot,
    payload: String,
): Map<String, String> = basePayloadMetadata(snapshot, payload) + mapOf(
    "comparisonContract" to "branch-native-logical-profile",
    "logicalShape" to logicalShape,
    "wireFormat" to "strict-v4",
)

private fun boundaryPayloadMetadata(
    snapshot: ProfileV4Snapshot,
    payload: String,
): Map<String, String> = basePayloadMetadata(snapshot, payload) + mapOf(
    "comparisonContract" to "strict-v4-payload-boundary",
    "logicalShape" to "maximum-with-content-version-padding",
    "wireFormat" to "strict-v4",
)

private fun basePayloadMetadata(
    snapshot: ProfileV4Snapshot,
    payload: String,
): Map<String, String> = mapOf(
    "schemaVersion" to PROFILE_SCHEMA_VERSION.toString(),
    "payloadBytes" to payload.encodeToByteArray().size.toString(),
    "payloadSha256" to payload.sha256(),
    "contentVersionBytes" to snapshot.contentVersion.value.encodeToByteArray().size.toString(),
    "unlockedWeapons" to snapshot.profile.loadout.unlockedWeapons.size.toString(),
    "labRanks" to snapshot.profile.labProgress.ranks.size.toString(),
    "discoveries" to snapshot.profile.collection.discoveredItemIds.size.toString(),
)

private fun rejectionMetadata(
    payload: String,
    rejection: ProfileV4Rejection,
): Map<String, String> = mapOf(
    "schemaVersion" to PROFILE_SCHEMA_VERSION.toString(),
    "payloadBytes" to payload.encodeToByteArray().size.toString(),
    "payloadSha256" to payload.sha256(),
    "expectedRejection" to rejection.name,
)

private fun resourceMetadata(
    payload: Map<String, String>,
    outcome: String,
): Map<String, String> = payload + mapOf(
    "provider" to "exact-in-memory",
    "providerLegacyKeys" to "absent",
    "expectedOutcome" to outcome,
)

private fun encodeOrFail(snapshot: ProfileV4Snapshot): String =
    when (val result = ProfileV4Codec.encode(snapshot)) {
        is ProfileV4EncodeResult.Encoded -> result.payload
        is ProfileV4EncodeResult.Rejected -> error("Benchmark fixture encode rejected: ${result.reason}")
    }

private fun decodeOrFail(payload: String): ProfileV4Snapshot =
    when (val result = ProfileV4Codec.decode(payload)) {
        is ProfileV4DecodeResult.Decoded -> result.snapshot
        is ProfileV4DecodeResult.Rejected -> error("Benchmark fixture decode rejected: ${result.reason}")
    }

private fun rejectionSignature(
    payload: String,
    expected: ProfileV4Rejection,
): Long = when (val result = ProfileV4Codec.decode(payload)) {
    is ProfileV4DecodeResult.Decoded -> error("Benchmark rejection unexpectedly decoded")
    is ProfileV4DecodeResult.Rejected -> {
        check(result.reason == expected) {
            "Expected $expected, got ${result.reason}"
        }
        result.reason.ordinal.toLong() + 1L
    }
}

private fun bootstrapSignature(result: ProfileBootstrapResourceResult): Long = when (result) {
    is ProfileBootstrapResourceResult.Observed ->
        result.snapshot?.let(::snapshotSignature) ?: 1L
    is ProfileBootstrapResourceResult.Rejected ->
        10_000L + result.reason.ordinal
    is ProfileBootstrapResourceResult.ResourceFailure ->
        error("In-memory benchmark provider unexpectedly failed: ${result.reason}")
}

private fun writeSignature(result: ProfileV4WriteResult): Long = when (result) {
    is ProfileV4WriteResult.Written -> result.revision.value + 1L
    is ProfileV4WriteResult.Rejected -> error("Benchmark write rejected: ${result.reason}")
    is ProfileV4WriteResult.ResourceFailure -> error("In-memory benchmark write failed: ${result.reason}")
    is ProfileV4WriteResult.OutcomeUnknown -> error("In-memory benchmark write was uncertain: ${result.reason}")
}

private inline fun BenchmarkValidationContext.validated(result: Long): Long {
    observeOutcome { result }
    return result
}

private fun snapshotSignature(snapshot: ProfileV4Snapshot): Long {
    val profile = snapshot.profile
    var result = snapshot.revision.value xor snapshot.contentVersion.value.hashCode().toLong()
    result = result * 31L + profile.loadout.unlockedWeapons.size
    result = result * 31L + profile.labProgress.ranks.sumOf(Int::toLong)
    result = result * 31L + profile.collection.discoveredItemIds.sumOf(Int::toLong)
    result = result * 31L + profile.economy.matter
    result = result * 31L + profile.economy.lifetimeMatter
    return result
}

private fun payloadSignature(payload: String): Long =
    payload.hashCode().toLong() * 31L + payload.length

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(encodeToByteArray())
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
