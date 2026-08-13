// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource.performance

import java.security.MessageDigest
import kinetickk.ball.content.api.ContentBounds
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
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileSnapshot
import kinetickk.ball.profile.api.ProfileSnapshotReadResult
import kinetickk.ball.profile.api.ProfileSnapshotRejection
import kinetickk.ball.profile.api.ProfileWriteResult
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.SIMULATION_SPEED_OPTIONS
import kinetickk.ball.profile.resource.ExactProfilePersistence
import kinetickk.ball.profile.resource.MAX_PROFILE_PAYLOAD_BYTES
import kinetickk.ball.profile.resource.ProfileCodec
import kinetickk.ball.profile.resource.ProfileDecodeResult
import kinetickk.ball.profile.resource.ProfileEncodeResult
import kinetickk.ball.profile.resource.ProfileProviderMutationResult
import kinetickk.ball.profile.resource.ProfileProviderReadResult
import kinetickk.ball.profile.resource.createProfileResource
import kinetickk.performance.BenchmarkScenario
import kinetickk.performance.BenchmarkSuiteIdentity
import kinetickk.performance.BenchmarkValidation
import kinetickk.performance.BenchmarkValidationContext
import kinetickk.performance.runBenchmarkSuite

internal const val PROFILE_PERSISTENCE_BENCHMARK_SUITE_VERSION =
    "profile-persistence-current-schema-v2"

fun main() {
    runBenchmarkSuite(
        identity = BenchmarkSuiteIdentity(
            suiteVersion = PROFILE_PERSISTENCE_BENCHMARK_SUITE_VERSION,
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
    private val defaultSnapshot = ProfileSnapshot(
        revision = ProfileRevision.ZERO,
        profile = PlayerProfile(),
    )
    private val defaultPayload = encodeOrFail(defaultSnapshot)
    private val maximumSnapshot = maximumBusinessSnapshot()
    private val maximumPayload = encodeOrFail(maximumSnapshot)
    private val malformedPayload = maximumPayload.dropLast(1)
    private val oversizePayload = "x".repeat(MAX_PROFILE_PAYLOAD_BYTES + 1)
    private val unknownFieldPayload = defaultPayload.replace(
        "\"revision\":\"0\",",
        "\"revision\":\"0\",\"unknown\":0,",
    )
    private val nonCanonicalPayload = "$defaultPayload\n"
    private val invalidUtf8Payload = "\uD800"
    private val defaultMetadata = payloadMetadata("default", defaultSnapshot, defaultPayload)
    private val maximumMetadata = payloadMetadata("maximum", maximumSnapshot, maximumPayload)
    private val emptyReadResource = resource(null)
    private val defaultReadResource = resource(defaultPayload)
    private val maximumReadResource = resource(maximumPayload)
    private val malformedReadResource = resource(malformedPayload)
    private val defaultWriteResource = resource(null)
    private val maximumWriteResource = resource(null)
    private var controlCounter = 1L

    fun scenarios(): List<BenchmarkScenario> = listOf(
        scenario(
            name = "profile_harness_control",
            category = "harness",
            description = "Lambda, loop, timer, counter and blackhole floor for this profile suite.",
            maximumOperations = 5_000_000,
        ) {
            controlCounter = nextControlValue(controlCounter)
            controlCounter
        },
        scenario(
            name = "profile_encode_default",
            category = "codec",
            description = "Validate and canonically encode the default current profile.",
            metadata = defaultMetadata,
            maximumOperations = 100_000,
        ) { payloadSignature(encodeOrFail(defaultSnapshot)) },
        scenario(
            name = "profile_decode_default",
            category = "codec",
            description = "Strictly decode and canonically re-encode the default current payload.",
            metadata = defaultMetadata,
            maximumOperations = 100_000,
        ) { snapshotSignature(decodeOrFail(defaultPayload)) },
        scenario(
            name = "profile_roundtrip_default",
            category = "codec",
            description = "Encode then strictly decode the default current profile.",
            metadata = defaultMetadata,
            maximumOperations = 50_000,
        ) {
            val payload = encodeOrFail(defaultSnapshot)
            snapshotSignature(decodeOrFail(payload)) xor payloadSignature(payload)
        },
        scenario(
            name = "profile_encode_business_maximum",
            category = "codec",
            description = "Encode every current-schema business collection and value at its maximum.",
            metadata = maximumMetadata,
            maximumOperations = 10_000,
        ) { payloadSignature(encodeOrFail(maximumSnapshot)) },
        scenario(
            name = "profile_decode_business_maximum",
            category = "codec",
            description = "Strictly decode the current-schema business-maximum payload.",
            metadata = maximumMetadata,
            maximumOperations = 10_000,
        ) { snapshotSignature(decodeOrFail(maximumPayload)) },
        scenario(
            name = "profile_roundtrip_business_maximum",
            category = "codec",
            description = "Encode then strictly decode the current-schema business maximum.",
            metadata = maximumMetadata,
            maximumOperations = 5_000,
        ) {
            val payload = encodeOrFail(maximumSnapshot)
            snapshotSignature(decodeOrFail(payload)) xor payloadSignature(payload)
        },
        rejectionScenario(
            "profile_decode_malformed_rejection",
            "Reject malformed JSON after traversing the maximum business payload.",
            malformedPayload,
            ProfileSnapshotRejection.MALFORMED_JSON,
            5_000,
        ),
        rejectionScenario(
            "profile_decode_oversize_rejection",
            "Reject the first payload byte beyond the 65,536-byte boundary before JSON parsing.",
            oversizePayload,
            ProfileSnapshotRejection.PAYLOAD_TOO_LARGE,
            10_000,
        ),
        rejectionScenario(
            "profile_decode_unknown_field_rejection",
            "Reject an unknown JSON field under the strict current schema.",
            unknownFieldPayload,
            ProfileSnapshotRejection.MALFORMED_JSON,
            50_000,
        ),
        rejectionScenario(
            "profile_decode_noncanonical_rejection",
            "Reject a semantically valid but non-canonical current payload.",
            nonCanonicalPayload,
            ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD,
            50_000,
        ),
        rejectionScenario(
            "profile_decode_invalid_utf8_rejection",
            "Reject an unpaired UTF-16 surrogate before JSON parsing.",
            invalidUtf8Payload,
            ProfileSnapshotRejection.INVALID_UTF8,
            200_000,
        ),
        scenario(
            name = "profile_resource_read_empty",
            category = "resource",
            description = "Read the exact in-memory provider when the current snapshot is absent.",
            metadata = resourceMetadata(emptyMap(), "empty"),
            maximumOperations = 500_000,
        ) { readSignature(emptyReadResource.readSnapshot()) },
        scenario(
            name = "profile_resource_read_default",
            category = "resource",
            description = "Read and strictly decode the default current snapshot.",
            metadata = resourceMetadata(defaultMetadata, "observed"),
            maximumOperations = 100_000,
        ) { readSignature(defaultReadResource.readSnapshot()) },
        scenario(
            name = "profile_resource_read_business_maximum",
            category = "resource",
            description = "Read and strictly decode the maximum current business snapshot.",
            metadata = resourceMetadata(maximumMetadata, "observed"),
            maximumOperations = 10_000,
        ) { readSignature(maximumReadResource.readSnapshot()) },
        scenario(
            name = "profile_resource_read_malformed_rejection",
            category = "resource",
            description = "Map a malformed current payload to the typed Resource rejection.",
            metadata = resourceMetadata(
                rejectionMetadata(malformedPayload, ProfileSnapshotRejection.MALFORMED_JSON),
                "rejected",
            ),
            maximumOperations = 5_000,
        ) { readSignature(malformedReadResource.readSnapshot()) },
        scenario(
            name = "profile_resource_write_readback_default",
            category = "resource",
            description = "Encode, write and confirm the default snapshot by exact readback.",
            metadata = resourceMetadata(defaultMetadata, "write-readback"),
            maximumOperations = 100_000,
        ) { writeSignature(defaultWriteResource.writeSnapshot(defaultSnapshot)) },
        scenario(
            name = "profile_resource_write_readback_business_maximum",
            category = "resource",
            description = "Encode, write and confirm the maximum business snapshot by exact readback.",
            metadata = resourceMetadata(maximumMetadata, "write-readback"),
            maximumOperations = 10_000,
        ) { writeSignature(maximumWriteResource.writeSnapshot(maximumSnapshot)) },
    ).map { benchmark ->
        val expected = expectedTimedResult(benchmark.name)
        benchmark.copy(
            metadata = benchmark.metadata + ("outcomeFingerprint" to expected.toString()),
            validation = BenchmarkValidation(
                expectedTimedResult = expected,
                expectedOutcomeWitness = expected,
                prepareProbe = {
                    if (benchmark.category == "harness") controlCounter = 1L
                },
            ),
        )
    }

    private fun scenario(
        name: String,
        category: String,
        description: String,
        metadata: Map<String, String> = emptyMap(),
        maximumOperations: Int,
        measure: BenchmarkValidationContext.() -> Long,
    ): BenchmarkScenario = BenchmarkScenario(
        name = name,
        category = category,
        description = description,
        metadata = metadata,
        maximumOperations = maximumOperations,
    ) { validation -> validation.validated(validation.measure()) }

    private fun rejectionScenario(
        name: String,
        description: String,
        payload: String,
        rejection: ProfileSnapshotRejection,
        maximumOperations: Int,
    ): BenchmarkScenario = scenario(
        name = name,
        category = "codec_rejection",
        description = description,
        metadata = rejectionMetadata(payload, rejection),
        maximumOperations = maximumOperations,
    ) { rejectionSignature(payload, rejection) }

    private fun expectedTimedResult(name: String): Long = when (name) {
        "profile_harness_control" -> nextControlValue(1L)
        "profile_encode_default" -> payloadSignature(encodeOrFail(defaultSnapshot))
        "profile_decode_default" -> snapshotSignature(decodeOrFail(defaultPayload))
        "profile_roundtrip_default" -> roundtripSignature(defaultSnapshot)
        "profile_encode_business_maximum" -> payloadSignature(encodeOrFail(maximumSnapshot))
        "profile_decode_business_maximum" -> snapshotSignature(decodeOrFail(maximumPayload))
        "profile_roundtrip_business_maximum" -> roundtripSignature(maximumSnapshot)
        "profile_decode_malformed_rejection" ->
            rejectionSignature(malformedPayload, ProfileSnapshotRejection.MALFORMED_JSON)
        "profile_decode_oversize_rejection" ->
            rejectionSignature(oversizePayload, ProfileSnapshotRejection.PAYLOAD_TOO_LARGE)
        "profile_decode_unknown_field_rejection" ->
            rejectionSignature(unknownFieldPayload, ProfileSnapshotRejection.MALFORMED_JSON)
        "profile_decode_noncanonical_rejection" ->
            rejectionSignature(nonCanonicalPayload, ProfileSnapshotRejection.NON_CANONICAL_PAYLOAD)
        "profile_decode_invalid_utf8_rejection" ->
            rejectionSignature(invalidUtf8Payload, ProfileSnapshotRejection.INVALID_UTF8)
        "profile_resource_read_empty" -> readSignature(resource(null).readSnapshot())
        "profile_resource_read_default" -> readSignature(resource(defaultPayload).readSnapshot())
        "profile_resource_read_business_maximum" ->
            readSignature(resource(maximumPayload).readSnapshot())
        "profile_resource_read_malformed_rejection" ->
            readSignature(resource(malformedPayload).readSnapshot())
        "profile_resource_write_readback_default" ->
            writeSignature(resource(null).writeSnapshot(defaultSnapshot))
        "profile_resource_write_readback_business_maximum" ->
            writeSignature(resource(null).writeSnapshot(maximumSnapshot))
        else -> error("Missing expected timed result for profile benchmark scenario: $name")
    }

    private fun resource(payload: String?) =
        createProfileResource(InMemoryExactProfilePersistence(payload))

    private fun roundtripSignature(snapshot: ProfileSnapshot): Long {
        val payload = encodeOrFail(snapshot)
        return snapshotSignature(decodeOrFail(payload)) xor payloadSignature(payload)
    }

    private fun nextControlValue(value: Long): Long =
        value * 2_862_933_555_777_941_757L + 3_037_000_493L
}

private fun maximumBusinessSnapshot(): ProfileSnapshot = ProfileSnapshot(
    revision = ProfileRevision(Long.MAX_VALUE),
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
        economy = PlayerEconomy(Long.MAX_VALUE - 1L, Long.MAX_VALUE),
        loadout = PlayerLoadout(
            coreShape = CoreShape.SHARD,
            selectedWeapon = WeaponId.entries.last(),
            unlockedWeapons = WeaponId.entries.reversed().toSet(),
        ),
        labProgress = LabProgress(List(MetaUpgradeId.entries.size) { Int.MAX_VALUE }),
        collection = PlayerCollection((0 until ContentBounds.MAX_ITEMS).reversed().toSet()),
        rebirthProgress = RebirthProgress(
            level = ContentBounds.MAX_REBIRTH_LEVEL,
            highestCleared = ContentBounds.MAX_REBIRTH_LEVEL,
        ),
    ),
)

private class InMemoryExactProfilePersistence(
    initialPayload: String?,
) : ExactProfilePersistence {
    private var payload: String? = initialPayload

    override fun readSnapshot(): ProfileProviderReadResult =
        ProfileProviderReadResult.Observed(payload)

    override fun writeSnapshot(payload: String): ProfileProviderMutationResult {
        this.payload = payload
        return ProfileProviderMutationResult.COMPLETED
    }
}

private fun payloadMetadata(
    logicalShape: String,
    snapshot: ProfileSnapshot,
    payload: String,
): Map<String, String> = mapOf(
    "comparisonContract" to "current-schema-logical-profile",
    "logicalShape" to logicalShape,
    "wireFormat" to "strict-current",
    "payloadBytes" to payload.encodeToByteArray().size.toString(),
    "payloadSha256" to payload.sha256(),
    "unlockedWeapons" to snapshot.profile.loadout.unlockedWeapons.size.toString(),
    "labRanks" to snapshot.profile.labProgress.ranks.size.toString(),
    "discoveries" to snapshot.profile.collection.discoveredItemIds.size.toString(),
)

private fun rejectionMetadata(
    payload: String,
    rejection: ProfileSnapshotRejection,
): Map<String, String> = mapOf(
    "payloadBytes" to payload.encodeToByteArray().size.toString(),
    "payloadSha256" to payload.sha256(),
    "expectedRejection" to rejection.name,
)

private fun resourceMetadata(
    payload: Map<String, String>,
    outcome: String,
): Map<String, String> = payload + mapOf(
    "provider" to "exact-in-memory",
    "expectedOutcome" to outcome,
)

private fun encodeOrFail(snapshot: ProfileSnapshot): String =
    when (val result = ProfileCodec.encode(snapshot)) {
        is ProfileEncodeResult.Encoded -> result.payload
        is ProfileEncodeResult.Rejected -> error("Benchmark fixture encode rejected: ${result.reason}")
    }

private fun decodeOrFail(payload: String): ProfileSnapshot =
    when (val result = ProfileCodec.decode(payload)) {
        is ProfileDecodeResult.Decoded -> result.snapshot
        is ProfileDecodeResult.Rejected -> error("Benchmark fixture decode rejected: ${result.reason}")
    }

private fun rejectionSignature(
    payload: String,
    expected: ProfileSnapshotRejection,
): Long = when (val result = ProfileCodec.decode(payload)) {
    is ProfileDecodeResult.Decoded -> error("Benchmark rejection unexpectedly decoded")
    is ProfileDecodeResult.Rejected -> {
        check(result.reason == expected) { "Expected $expected, got ${result.reason}" }
        result.reason.ordinal.toLong() + 1L
    }
}

private fun readSignature(result: ProfileSnapshotReadResult): Long = when (result) {
    is ProfileSnapshotReadResult.Observed -> result.snapshot?.let(::snapshotSignature) ?: 1L
    is ProfileSnapshotReadResult.Rejected -> 10_000L + result.reason.ordinal
    is ProfileSnapshotReadResult.ResourceFailure ->
        error("In-memory benchmark provider unexpectedly failed: ${result.reason}")
}

private fun writeSignature(result: ProfileWriteResult): Long = when (result) {
    is ProfileWriteResult.Written -> result.revision.value + 1L
    is ProfileWriteResult.Rejected -> error("Benchmark write rejected: ${result.reason}")
    is ProfileWriteResult.ResourceFailure -> error("In-memory benchmark write failed: ${result.reason}")
    is ProfileWriteResult.OutcomeUnknown -> error("In-memory benchmark write was uncertain: ${result.reason}")
}

private fun BenchmarkValidationContext.validated(result: Long): Long {
    observeOutcome { result }
    return result
}

private fun snapshotSignature(snapshot: ProfileSnapshot): Long {
    val profile = snapshot.profile
    var result = snapshot.revision.value
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
