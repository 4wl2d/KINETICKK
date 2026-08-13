// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import kinetickk.ball.profile.impl.ProfilePersistenceCapability
import kinetickk.ball.profile.impl.ProfilePersistenceContract
import kinetickk.ball.profile.impl.ProfilePersistenceMutationResult
import kinetickk.ball.profile.impl.ProfilePersistenceReadResult
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneWave
import kinetickk.resource.audio.impl.TonePlaybackCapability
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

internal actual fun createPlatformProfilePersistenceCapability(): ProfilePersistenceCapability =
    WebProfilePersistenceCapability()

private class WebProfilePersistenceCapability : ProfilePersistenceCapability {
    override fun readSnapshot(): ProfilePersistenceReadResult = readWebProfileSnapshot()

    override fun writeSnapshot(payload: String): ProfilePersistenceMutationResult =
        writeWebProfileSnapshot(payload)
}

@OptIn(ExperimentalWasmJsInterop::class)
private external interface WebStorageReadCall : JsAny {
    val status: String
    val payload: String?
}

private fun readWebProfileSnapshot(): ProfilePersistenceReadResult =
    webStorageRead(ProfilePersistenceContract.WEB_SNAPSHOT).toPersistenceResult()

private fun writeWebProfileSnapshot(payload: String): ProfilePersistenceMutationResult =
    webStorageWrite(ProfilePersistenceContract.WEB_SNAPSHOT, payload).toPersistenceMutationResult()

private fun WebStorageReadCall.toPersistenceResult(): ProfilePersistenceReadResult = when (status) {
    WEB_STORAGE_OBSERVED -> ProfilePersistenceReadResult.Observed(payload)
    WEB_STORAGE_FAILED_BEFORE_EXECUTION -> ProfilePersistenceReadResult.Failed
    else -> error("Web Storage read returned an unknown provider status")
}

private fun String.toPersistenceMutationResult(): ProfilePersistenceMutationResult = when (this) {
    WEB_STORAGE_COMPLETED -> ProfilePersistenceMutationResult.COMPLETED
    WEB_STORAGE_FAILED_BEFORE_EXECUTION -> ProfilePersistenceMutationResult.FAILED_BEFORE_EXECUTION
    else -> error("Web Storage mutation returned an unknown provider status")
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun webStorageRead(key: String): WebStorageReadCall = js(
    """{
        try {
            const exactStorage = globalThis.localStorage;
            return { status: 'observed', payload: exactStorage.getItem(key) };
        } catch (failure) {
            if (
                typeof DOMException !== 'undefined' &&
                failure instanceof DOMException &&
                failure.name === 'SecurityError'
            ) {
                return { status: 'failed-before-execution', payload: null };
            }
            throw failure;
        }
    }""",
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun webStorageWrite(key: String, payload: String): String = js(
    """{
        try {
            const exactStorage = globalThis.localStorage;
            exactStorage.setItem(key, payload);
            return 'completed';
        } catch (failure) {
            if (
                typeof DOMException !== 'undefined' &&
                failure instanceof DOMException &&
                (failure.name === 'SecurityError' || failure.name === 'QuotaExceededError')
            ) {
                return 'failed-before-execution';
            }
            throw failure;
        }
    }""",
)

private const val WEB_STORAGE_OBSERVED: String = "observed"
private const val WEB_STORAGE_COMPLETED: String = "completed"
private const val WEB_STORAGE_FAILED_BEFORE_EXECUTION: String = "failed-before-execution"

internal actual fun createPlatformTonePlaybackCapability(): TonePlaybackCapability =
    WebTonePlaybackCapability()

/** Owns one AudioContext instance without publishing it through a JavaScript global cache. */
@OptIn(ExperimentalWasmJsInterop::class)
private class WebTonePlaybackCapability : TonePlaybackCapability {
    private var context: JsAny? = null

    override fun unlock() {
        context = unlockWebAudio(context)
    }

    override fun play(request: ToneRequest) {
        context = playWebTone(
            context,
            request.frequencyHz.toDouble(),
            request.durationSeconds.toDouble(),
            request.gain.toDouble(),
            webToneWaveValue(request.wave),
        )
    }

    override fun close() {
        closeWebAudio(context)
        context = null
    }
}

internal fun webToneWaveValue(wave: ToneWave): String = when (wave) {
    ToneWave.SINE -> "sine"
    ToneWave.SQUARE -> "square"
    ToneWave.SAW -> "sawtooth"
    ToneWave.TRIANGLE -> "triangle"
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun unlockWebAudio(current: JsAny?): JsAny? = js(
    """{
        const AudioContext = globalThis.AudioContext || globalThis.webkitAudioContext;
        if (!AudioContext) return null;
        const context = current || new AudioContext();
        if (context.state === 'suspended') {
            const resume = context.resume();
            if (resume && typeof resume.catch === 'function') resume.catch(() => undefined);
        }
        return context;
    }""",
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun playWebTone(
    current: JsAny?,
    frequency: Double,
    duration: Double,
    volume: Double,
    wave: String,
): JsAny? = js(
    """{
        const AudioContext = globalThis.AudioContext || globalThis.webkitAudioContext;
        if (!AudioContext) return null;
        const context = current || new AudioContext();
        if (context.state === 'suspended') {
            const resume = context.resume();
            if (resume && typeof resume.catch === 'function') resume.catch(() => undefined);
        }
        const oscillator = context.createOscillator();
        const gain = context.createGain();
        oscillator.type = wave;
        oscillator.frequency.setValueAtTime(frequency, context.currentTime);
        gain.gain.setValueAtTime(0.0001, context.currentTime);
        gain.gain.exponentialRampToValueAtTime(Math.max(0.0001, volume), context.currentTime + 0.008);
        gain.gain.exponentialRampToValueAtTime(0.0001, context.currentTime + duration);
        oscillator.connect(gain);
        gain.connect(context.destination);
        oscillator.start();
        oscillator.stop(context.currentTime + duration + 0.015);
        return context;
    }""",
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun closeWebAudio(current: JsAny?): Unit = js(
    """{
        if (current) {
            const close = current.close();
            if (close && typeof close.catch === 'function') close.catch(() => undefined);
        }
    }""",
)
