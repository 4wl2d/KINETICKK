// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import kinetickk.foundation.diagnostics.CrashDiagnostics

import kinetickk.ball.profile.impl.ProfilePersistenceCapability
import kinetickk.ball.profile.impl.ProfilePersistenceContract
import kinetickk.ball.profile.impl.ProfilePersistenceMutationResult
import kinetickk.ball.profile.impl.ProfilePersistenceReadResult
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneWave
import kinetickk.resource.audio.impl.TonePlaybackCapability
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

internal actual fun createPlatformProfilePersistenceCapability(diagnostics: CrashDiagnostics): ProfilePersistenceCapability =
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

/** Owns one AudioContext instance and its master bus without publishing them through a JavaScript global cache. */
@OptIn(ExperimentalWasmJsInterop::class)
private class WebTonePlaybackCapability : TonePlaybackCapability {
    /** `{ context, master }`: the AudioContext and its unity-gain master bus, made with the first tone. */
    private var audio: JsAny? = null
    private var closed = false
    private var gestureFeedbackPending = false

    override suspend fun prepare(requests: List<ToneRequest>) {
        if (!closed) audio = prepareWebAudio(audio)
    }

    override fun unlock() {
        if (closed) return
        audio = unlockWebAudio(audio)
        gestureFeedbackPending = true
    }

    override fun play(request: ToneRequest) {
        if (closed) return
        audio = playWebTone(
            audio,
            request.frequencyHz.toDouble(),
            request.durationSeconds.toDouble(),
            request.gain.toDouble(),
            webToneWaveValue(request.wave),
            gestureFeedbackPending,
        )
        gestureFeedbackPending = false
    }

    override fun close() {
        if (closed) return
        closeWebAudio(audio)
        audio = null
        closed = true
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun prepareWebAudio(current: JsAny?): JsAny? = js(
    """{
        const AudioContext = globalThis.AudioContext || globalThis.webkitAudioContext;
        try {
            return current || (AudioContext ? { context: new AudioContext(), master: null } : null);
        } catch (failure) {
            // Audio is optional: a browser that refuses a context leaves the game silent.
            return null;
        }
    }""",
)

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
        const audio = current || { context: new AudioContext(), master: null };
        const context = audio.context;
        if (context.state === 'suspended') {
            const resume = context.resume();
            if (resume && typeof resume.catch === 'function') resume.catch(() => undefined);
        }
        return audio;
    }""",
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun playWebTone(
    current: JsAny?,
    frequency: Double,
    duration: Double,
    volume: Double,
    wave: String,
    allowSuspended: Boolean,
): JsAny? = js(
    """{
        const AudioContext = globalThis.AudioContext || globalThis.webkitAudioContext;
        if (!AudioContext) return null;
        const audio = current || { context: new AudioContext(), master: null };
        const context = audio.context;
        // resume() resolves asynchronously. The gesture's first cue can already be scheduled;
        // automatic frames before a gesture must not enqueue sounds into a suspended context.
        if (context.state === 'suspended' && !allowSuspended) return audio;
        const oscillator = context.createOscillator();
        const gain = context.createGain();
        if (!audio.master) {
            // One unity-gain bus per context: every tone reaches the destination through it.
            audio.master = context.createGain();
            audio.master.gain.value = 1;
            audio.master.connect(context.destination);
        }
        oscillator.type = wave;
        oscillator.frequency.setValueAtTime(frequency, context.currentTime);
        gain.gain.setValueAtTime(0.0001, context.currentTime);
        gain.gain.exponentialRampToValueAtTime(Math.max(0.0001, volume), context.currentTime + 0.008);
        gain.gain.exponentialRampToValueAtTime(0.0001, context.currentTime + duration);
        oscillator.connect(gain);
        gain.connect(audio.master);
        oscillator.start();
        oscillator.stop(context.currentTime + duration + 0.015);
        return audio;
    }""",
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun closeWebAudio(current: JsAny?): Unit = js(
    """{
        if (current) {
            const close = current.context.close();
            if (close && typeof close.catch === 'function') close.catch(() => undefined);
        }
    }""",
)
