// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import kinetickk.ball.profile.impl.ProfilePersistenceCapability
import kinetickk.ball.profile.impl.ProfilePersistenceMutationResult
import kinetickk.ball.profile.impl.ProfilePersistenceReadResult
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneWave
import kotlinx.browser.localStorage
import org.w3c.dom.Storage
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @OptIn(ExperimentalWasmJsInterop::class)
    @Test
    fun webAudioSynchronousProviderFaultsPropagateWithoutFabricatingClosedState() {
        listOf("unlock", "play").forEach { operation ->
            withWebAudioSynchronousFault(operation) {
                val capability = createPlatformTonePlaybackCapability()
                assertFailsWith<JsException> {
                    if (operation == "unlock") {
                        capability.unlock()
                    } else {
                        capability.play(ToneRequest(440f, 0.001f, 0f, ToneWave.SINE))
                    }
                }
            }
        }

        withWebAudioSynchronousFault("close") {
            val capability = createPlatformTonePlaybackCapability()
            capability.unlock()
            assertFailsWith<JsException> { capability.close() }
            assertFailsWith<JsException> { capability.close() }
        }
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

            assertEquals(
                ProfilePersistenceMutationResult.COMPLETED,
                capability.writeV4("strict-v4-payload"),
            )
            assertEquals(
                ProfilePersistenceReadResult.Observed("strict-v4-payload"),
                capability.readV4(),
            )
            assertEquals(
                ProfilePersistenceReadResult.Observed("legacy"),
                capability.readLegacyProgressV2(),
            )
            assertEquals(ProfilePersistenceReadResult.Observed("1"), capability.readLegacyMatter())

            assertEquals(
                ProfilePersistenceMutationResult.COMPLETED,
                capability.removeLegacyProgressV2(),
            )
            assertEquals(ProfilePersistenceMutationResult.COMPLETED, capability.removeLegacyMatter())
            assertNull(localStorage.getItem(keys.legacyProgressV2))
            assertNull(localStorage.getItem(keys.legacyMatter))
            assertEquals("preserve-me", localStorage.getItem(unrelated))
        } finally {
            listOf(keys.snapshotV4, keys.legacyProgressV2, keys.legacyMatter, unrelated).forEach {
                localStorage.removeItem(it)
            }
        }
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    @Test
    fun webStorageDomFailuresAreTypedBeforeExecutionAndProgrammingErrorsPropagate() {
        withWebStorageMethodFailure("getItem", "SecurityError", programmingFault = false) {
            assertEquals(
                ProfilePersistenceReadResult.Failed,
                createPlatformProfilePersistenceCapability().readV4(),
            )
        }
        withWebStorageMethodFailure("setItem", "QuotaExceededError", programmingFault = false) {
            assertEquals(
                ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION,
                createPlatformProfilePersistenceCapability().writeV4("payload"),
            )
        }
        withWebStorageMethodFailure("removeItem", "SecurityError", programmingFault = false) {
            assertEquals(
                ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION,
                createPlatformProfilePersistenceCapability().removeLegacyProgressV2(),
            )
        }
        withWebStorageMethodFailure("getItem", "ignored", programmingFault = true) {
            assertFailsWith<JsException> {
                createPlatformProfilePersistenceCapability().readV4()
            }
        }
        withWebStorageMethodFailure("setItem", "ignored", programmingFault = true) {
            assertFailsWith<JsException> {
                createPlatformProfilePersistenceCapability().writeV4("payload")
            }
        }
        withWebStorageMethodFailure("removeItem", "ignored", programmingFault = true) {
            assertFailsWith<JsException> {
                createPlatformProfilePersistenceCapability().removeLegacyMatter()
            }
        }
        withWebStorageMethodFailure("getItem", "InvalidStateError", programmingFault = false) {
            assertFailsWith<JsException> {
                createPlatformProfilePersistenceCapability().readLegacyMatter()
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
    override fun readV4(): ProfilePersistenceReadResult =
        ProfilePersistenceReadResult.Observed(storage.getItem(keys.snapshotV4))

    override fun writeV4(payload: String): ProfilePersistenceMutationResult {
        storage.setItem(keys.snapshotV4, payload)
        return ProfilePersistenceMutationResult.COMPLETED
    }

    override fun readLegacyProgressV2(): ProfilePersistenceReadResult =
        ProfilePersistenceReadResult.Observed(storage.getItem(keys.legacyProgressV2))

    override fun readLegacyMatter(): ProfilePersistenceReadResult =
        ProfilePersistenceReadResult.Observed(storage.getItem(keys.legacyMatter))

    override fun removeLegacyProgressV2(): ProfilePersistenceMutationResult {
        storage.removeItem(keys.legacyProgressV2)
        return ProfilePersistenceMutationResult.COMPLETED
    }

    override fun removeLegacyMatter(): ProfilePersistenceMutationResult {
        storage.removeItem(keys.legacyMatter)
        return ProfilePersistenceMutationResult.COMPLETED
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private inline fun withWebStorageMethodFailure(
    methodName: String,
    exceptionName: String,
    programmingFault: Boolean,
    block: () -> Unit,
) {
    val original = installWebStorageMethodFailure(methodName, exceptionName, programmingFault)
    try {
        block()
    } finally {
        restoreWebStorageMethod(methodName, original)
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun installWebStorageMethodFailure(
    methodName: String,
    exceptionName: String,
    programmingFault: Boolean,
): JsAny = js(
    """{
        const original = Storage.prototype[methodName];
        Storage.prototype[methodName] = function() {
            if (programmingFault) throw new TypeError('programming fault');
            throw new DOMException('provider failure', exceptionName);
        };
        return original;
    }""",
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun restoreWebStorageMethod(methodName: String, original: JsAny): Unit = js(
    """{ Storage.prototype[methodName] = original; }""",
)

@OptIn(ExperimentalWasmJsInterop::class)
private inline fun withWebAudioSynchronousFault(
    operation: String,
    block: () -> Unit,
) {
    val original = installWebAudioSynchronousFault(operation)
    try {
        block()
    } finally {
        restoreWebAudioConstructors(original)
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun installWebAudioSynchronousFault(operation: String): JsAny = js(
    """{
        const original = {
            audioContext: globalThis.AudioContext,
            webkitAudioContext: globalThis.webkitAudioContext,
        };
        globalThis.webkitAudioContext = undefined;
        globalThis.AudioContext = function() {
            return {
                state: operation === 'unlock' ? 'suspended' : 'running',
                resume: function() {
                    if (operation === 'unlock') throw new TypeError('unlock fault');
                    return Promise.resolve();
                },
                createOscillator: function() {
                    if (operation === 'play') throw new TypeError('play fault');
                    return {};
                },
                close: function() {
                    if (operation === 'close') throw new TypeError('close fault');
                    return Promise.resolve();
                },
            };
        };
        return original;
    }""",
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun restoreWebAudioConstructors(original: JsAny): Unit = js(
    """{
        globalThis.AudioContext = original.audioContext;
        globalThis.webkitAudioContext = original.webkitAudioContext;
    }""",
)
