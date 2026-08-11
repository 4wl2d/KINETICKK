// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import kinetickk.ball.profile.impl.ProfilePersistenceCapability
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneWave
import kotlinx.browser.localStorage
import org.w3c.dom.Storage
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull

class PlatformCapabilitiesWebTest {
    @Test
    fun everyToneWaveMapsExhaustivelyToItsClosedWebValue() {
        assertEquals(
            mapOf(
                ToneWave.SINE to "sine",
                ToneWave.SQUARE to "square",
                ToneWave.SAW to "sawtooth",
                ToneWave.TRIANGLE to "triangle",
            ),
            ToneWave.entries.associateWith(::webToneWaveValue),
        )
    }

    @Test
    fun audioBrokerIsInstanceOwnedAndAcceptsOnlyTypedToneRequests() {
        val first = createPlatformTonePlaybackCapability()
        val second = createPlatformTonePlaybackCapability()
        assertNotSame(first, second)

        first.play(ToneRequest(440f, 0.001f, 0f, ToneWave.SINE))
        first.close()
        second.close()
    }

    @Test
    fun testPersistenceCapabilityUsesOnlyExactKeysAndPreservesUnrelatedData() {
        val prefix = "kinetickk_app_platform_capability_${Random.nextLong()}"
        val keys = TestWebProfilePersistenceKeys(
            snapshotV4 = "${prefix}_v4",
            legacyProgressV2 = "${prefix}_v2",
            legacyMatter = "${prefix}_matter",
        )
        val unrelated = "${prefix}_unrelated"
        try {
            val capability = TestWebProfilePersistenceCapability(localStorage, keys)
            localStorage.setItem(keys.legacyProgressV2, "legacy")
            localStorage.setItem(keys.legacyMatter, "1")
            localStorage.setItem(unrelated, "preserve-me")

            capability.writeV4("strict-v4-payload")
            assertEquals("strict-v4-payload", capability.readV4())
            assertEquals("legacy", capability.readLegacyProgressV2())
            assertEquals("1", capability.readLegacyMatter())

            capability.removeLegacyProgressV2()
            capability.removeLegacyMatter()
            assertNull(localStorage.getItem(keys.legacyProgressV2))
            assertNull(localStorage.getItem(keys.legacyMatter))
            assertEquals("preserve-me", localStorage.getItem(unrelated))
        } finally {
            listOf(keys.snapshotV4, keys.legacyProgressV2, keys.legacyMatter, unrelated).forEach {
                localStorage.removeItem(it)
            }
        }
    }
}

private data class TestWebProfilePersistenceKeys(
    val snapshotV4: String,
    val legacyProgressV2: String,
    val legacyMatter: String,
)

private class TestWebProfilePersistenceCapability(
    private val storage: Storage,
    private val keys: TestWebProfilePersistenceKeys,
) : ProfilePersistenceCapability {
    override fun readV4(): String? = storage.getItem(keys.snapshotV4)

    override fun writeV4(payload: String) {
        storage.setItem(keys.snapshotV4, payload)
    }

    override fun readLegacyProgressV2(): String? = storage.getItem(keys.legacyProgressV2)

    override fun readLegacyMatter(): String? = storage.getItem(keys.legacyMatter)

    override fun removeLegacyProgressV2() {
        storage.removeItem(keys.legacyProgressV2)
    }

    override fun removeLegacyMatter() {
        storage.removeItem(keys.legacyMatter)
    }
}
