// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.game.resources.progress.performance

import java.security.MessageDigest
import kinetickk.features.game.nucleus.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
import kinetickk.features.game.nucleus.DamageNumberFormat
import kinetickk.features.game.nucleus.DamageNumberSize
import kinetickk.features.game.nucleus.GameSettings
import kinetickk.features.game.nucleus.ItemCatalog
import kinetickk.features.game.nucleus.MetaUpgradeId
import kinetickk.features.game.nucleus.ParticleDensity
import kinetickk.features.game.nucleus.RebirthProgression
import kinetickk.features.game.nucleus.StoredProgress
import kinetickk.features.game.nucleus.WeaponId
import kinetickk.features.game.resources.progress.ProgressCodec
import kinetickk.performance.BenchmarkScenario
import kinetickk.performance.BenchmarkSuiteIdentity
import kinetickk.performance.runBenchmarkSuite

private const val SUITE_VERSION = "profile-persistence-v1"
private const val LEGACY_SCHEMA_VERSION = 3

fun main() {
    runBenchmarkSuite(
        identity = BenchmarkSuiteIdentity(
            suiteVersion = SUITE_VERSION,
            adapter = "main-fedceb8-legacy-v3",
            label = System.getProperty("kinetickk.benchmark.label", "main"),
            revision = System.getProperty(
                "kinetickk.benchmark.revision",
                "fedceb8e2d9009d805d70249e10c77e424447945",
            ),
            dirty = System.getProperty("kinetickk.benchmark.dirty", "false").toBoolean(),
        ),
        scenarios = ProfileBenchmarkFixtures().scenarios(),
    )
}

private class ProfileBenchmarkFixtures {
    private val defaultProfile = StoredProgress()
    private val maximumProfile = logicalMaximumProfile()
    private val defaultPayload = ProgressCodec.encode(defaultProfile)
    private val maximumPayload = ProgressCodec.encode(maximumProfile)
    private val defaultMetadata = payloadMetadata("default", defaultProfile, defaultPayload)
    private val maximumMetadata = payloadMetadata("maximum", maximumProfile, maximumPayload)

    fun scenarios(): List<BenchmarkScenario> = listOf(
        BenchmarkScenario(
            name = "profile_encode_default",
            category = "codec",
            description = "Validate and encode the branch-native default legacy-v3 profile.",
            metadata = defaultMetadata,
            maximumOperations = 100_000,
        ) {
            payloadSignature(ProgressCodec.encode(defaultProfile))
        },
        BenchmarkScenario(
            name = "profile_decode_default",
            category = "codec",
            description = "Decode the branch-native default legacy-v3 payload.",
            metadata = defaultMetadata,
            maximumOperations = 100_000,
        ) {
            profileSignature(decodeOrFail(defaultPayload))
        },
        BenchmarkScenario(
            name = "profile_roundtrip_default",
            category = "codec",
            description = "Encode then decode the branch-native default legacy-v3 profile.",
            metadata = defaultMetadata,
            maximumOperations = 50_000,
        ) {
            val payload = ProgressCodec.encode(defaultProfile)
            profileSignature(decodeOrFail(payload)) xor payloadSignature(payload)
        },
        BenchmarkScenario(
            name = "profile_encode_logical_maximum",
            category = "codec",
            description = "Encode every branch-native legacy-v3 logical collection and value at its maximum.",
            metadata = maximumMetadata,
            maximumOperations = 10_000,
        ) {
            payloadSignature(ProgressCodec.encode(maximumProfile))
        },
        BenchmarkScenario(
            name = "profile_decode_logical_maximum",
            category = "codec",
            description = "Decode the branch-native legacy-v3 logical-maximum payload without padding.",
            metadata = maximumMetadata,
            maximumOperations = 10_000,
        ) {
            profileSignature(decodeOrFail(maximumPayload))
        },
        BenchmarkScenario(
            name = "profile_roundtrip_logical_maximum",
            category = "codec",
            description = "Encode then decode the branch-native legacy-v3 logical-maximum profile.",
            metadata = maximumMetadata,
            maximumOperations = 5_000,
        ) {
            val payload = ProgressCodec.encode(maximumProfile)
            profileSignature(decodeOrFail(payload)) xor payloadSignature(payload)
        },
    )
}

private fun logicalMaximumProfile(): StoredProgress = StoredProgress(
    matter = Long.MAX_VALUE - 1L,
    lifetimeMatter = Long.MAX_VALUE,
    coreShapeIndex = 2,
    selectedWeaponIndex = WeaponId.entries.lastIndex,
    unlockedWeaponIndices = WeaponId.entries.indices.toSet(),
    metaLevels = List(MetaUpgradeId.entries.size) { Int.MAX_VALUE },
    discoveredItemIds = (0 until ItemCatalog.ITEM_COUNT).toSet(),
    settings = GameSettings(
        soundEnabled = false,
        musicEnabled = true,
        masterVolume = 1f,
        simulationSpeed = 2f,
        textScale = 1.75f,
        screenShake = false,
        particleDensity = ParticleDensity.HIGH,
        damageNumbers = false,
        damageNumberSize = DamageNumberSize.HUGE,
        damageNumberFormat = DamageNumberFormat.FULL,
        damageNumberTierThreshold = DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.last(),
    ),
    rebirthLevel = RebirthProgression.MAX_LEVEL,
    highestClearedRebirth = RebirthProgression.MAX_LEVEL,
)

private fun payloadMetadata(
    logicalShape: String,
    profile: StoredProgress,
    payload: String,
): Map<String, String> = mapOf(
    "comparisonContract" to "branch-native-logical-profile",
    "logicalShape" to logicalShape,
    "wireFormat" to "legacy-v3",
    "schemaVersion" to LEGACY_SCHEMA_VERSION.toString(),
    "payloadBytes" to payload.encodeToByteArray().size.toString(),
    "payloadSha256" to payload.sha256(),
    "unlockedWeapons" to profile.unlockedWeaponIndices.size.toString(),
    "labRanks" to profile.metaLevels.size.toString(),
    "discoveries" to profile.discoveredItemIds.size.toString(),
)

private fun decodeOrFail(payload: String): StoredProgress =
    checkNotNull(ProgressCodec.decode(payload)) { "Benchmark fixture decode rejected" }

private fun profileSignature(profile: StoredProgress): Long {
    var result = profile.matter xor profile.lifetimeMatter
    result = result * 31L + profile.unlockedWeaponIndices.size
    result = result * 31L + profile.metaLevels.sumOf(Int::toLong)
    result = result * 31L + profile.discoveredItemIds.sumOf(Int::toLong)
    result = result * 31L + profile.rebirthLevel
    result = result * 31L + profile.highestClearedRebirth
    return result
}

private fun payloadSignature(payload: String): Long =
    payload.hashCode().toLong() * 31L + payload.encodeToByteArray().size

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(encodeToByteArray())
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
