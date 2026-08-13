// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.resource.performance

import kinetickk.ball.profile.resource.MAX_PROFILE_PAYLOAD_BYTES
import kinetickk.performance.validateBenchmarkScenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfilePerformanceBenchmarkTest {
    @Test
    fun suiteIdentityDeclaresTheHarnessContractWithoutVersioningTheSaveSchema() {
        assertEquals(
            "profile-persistence-current-schema-v2",
            PROFILE_PERSISTENCE_BENCHMARK_SUITE_VERSION,
        )
    }

    @Test
    fun suiteCoversCodecLimitsRejectionsAndInMemoryResourcePaths() {
        val scenarios = profileBenchmarkScenarios()
        val names = scenarios.map { scenario -> scenario.name }

        assertEquals(names.distinct(), names)
        assertEquals(
            setOf(
                "profile_harness_control",
                "profile_encode_default",
                "profile_decode_default",
                "profile_roundtrip_default",
                "profile_encode_business_maximum",
                "profile_decode_business_maximum",
                "profile_roundtrip_business_maximum",
                "profile_decode_malformed_rejection",
                "profile_decode_oversize_rejection",
                "profile_decode_unknown_field_rejection",
                "profile_decode_noncanonical_rejection",
                "profile_decode_invalid_utf8_rejection",
                "profile_resource_read_empty",
                "profile_resource_read_default",
                "profile_resource_read_business_maximum",
                "profile_resource_read_malformed_rejection",
                "profile_resource_write_readback_default",
                "profile_resource_write_readback_business_maximum",
            ),
            names.toSet(),
        )

        val logicalNames = setOf(
            "profile_encode_default",
            "profile_decode_default",
            "profile_roundtrip_default",
            "profile_encode_business_maximum",
            "profile_decode_business_maximum",
            "profile_roundtrip_business_maximum",
        )
        scenarios.filter { scenario -> scenario.name in logicalNames }.forEach { scenario ->
            assertEquals(
                "current-schema-logical-profile",
                scenario.metadata["comparisonContract"],
                scenario.name,
            )
            assertEquals("strict-current", scenario.metadata["wireFormat"], scenario.name)
            assertTrue(
                requireNotNull(scenario.metadata["payloadBytes"]).toInt() < MAX_PROFILE_PAYLOAD_BYTES,
                scenario.name,
            )
        }

        scenarios.filter { scenario -> scenario.category == "resource" }.forEach { scenario ->
            assertEquals("exact-in-memory", scenario.metadata["provider"], scenario.name)
        }
        scenarios.forEach { scenario ->
            validateBenchmarkScenario(scenario)
        }
    }
}
