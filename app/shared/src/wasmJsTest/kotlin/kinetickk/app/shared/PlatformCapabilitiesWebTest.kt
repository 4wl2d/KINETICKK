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
    fun persistenceCapabilityUsesOnlySnapshotAndPreservesUnrelatedData() {
        val prefix = "kinetickk_app_platform_capability_${Random.nextLong()}"
        val snapshotKey = "${prefix}_profile"
        val legacyProgressKey = "${prefix}_progress_v2"
        val legacyMatterKey = "${prefix}_matter"
        val unrelated = "${prefix}_unrelated"
        try {
            val capability = TestWebProfilePersistenceCapability(localStorage, snapshotKey)
            localStorage.setItem(legacyProgressKey, "legacy")
            localStorage.setItem(legacyMatterKey, "1")
            localStorage.setItem(unrelated, "preserve-me")

            assertEquals(
                ProfilePersistenceMutationResult.COMPLETED,
                capability.writeSnapshot("strict-current-payload"),
            )
            assertEquals(
                ProfilePersistenceReadResult.Observed("strict-current-payload"),
                capability.readSnapshot(),
            )
            assertEquals("legacy", localStorage.getItem(legacyProgressKey))
            assertEquals("1", localStorage.getItem(legacyMatterKey))
            assertEquals("preserve-me", localStorage.getItem(unrelated))
        } finally {
            listOf(snapshotKey, legacyProgressKey, legacyMatterKey, unrelated).forEach {
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
                createPlatformProfilePersistenceCapability().readSnapshot(),
            )
        }
        withWebStorageMethodFailure("setItem", "QuotaExceededError", programmingFault = false) {
            assertEquals(
                ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION,
                createPlatformProfilePersistenceCapability().writeSnapshot("payload"),
            )
        }
        withWebStorageMethodFailure("getItem", "ignored", programmingFault = true) {
            assertFailsWith<JsException> {
                createPlatformProfilePersistenceCapability().readSnapshot()
            }
        }
        withWebStorageMethodFailure("setItem", "ignored", programmingFault = true) {
            assertFailsWith<JsException> {
                createPlatformProfilePersistenceCapability().writeSnapshot("payload")
            }
        }
        withWebStorageMethodFailure("getItem", "InvalidStateError", programmingFault = false) {
            assertFailsWith<JsException> {
                createPlatformProfilePersistenceCapability().readSnapshot()
            }
        }
    }
}

private class TestWebProfilePersistenceCapability(
    private val storage: Storage,
    private val snapshotKey: String,
) : ProfilePersistenceCapability {
    override fun readSnapshot(): ProfilePersistenceReadResult =
        ProfilePersistenceReadResult.Observed(storage.getItem(snapshotKey))

    override fun writeSnapshot(payload: String): ProfilePersistenceMutationResult {
        storage.setItem(snapshotKey, payload)
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
