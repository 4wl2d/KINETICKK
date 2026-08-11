// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import kinetickk.ball.profile.impl.ProfilePersistenceCapability
import kinetickk.ball.profile.impl.ProfilePersistenceContract
import kinetickk.resource.audio.api.ToneRequest
import kinetickk.resource.audio.api.ToneWave
import kinetickk.resource.audio.impl.TonePlaybackCapability
import kotlinx.browser.localStorage
import org.w3c.dom.Storage
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

internal actual fun createPlatformProfilePersistenceCapability(): ProfilePersistenceCapability =
    WebProfilePersistenceCapability(
        storage = { localStorage },
        keys = WEB_PROFILE_PERSISTENCE_KEYS,
    )

private data class WebProfilePersistenceKeys(
    val snapshotV4: String,
    val legacyProgressV2: String,
    val legacyMatter: String,
)

private val WEB_PROFILE_PERSISTENCE_KEYS = WebProfilePersistenceKeys(
    snapshotV4 = ProfilePersistenceContract.WEB_SNAPSHOT_V4,
    legacyProgressV2 = ProfilePersistenceContract.WEB_LEGACY_PROGRESS_V2,
    legacyMatter = ProfilePersistenceContract.WEB_LEGACY_MATTER,
)

private class WebProfilePersistenceCapability(
    private val storage: () -> Storage,
    private val keys: WebProfilePersistenceKeys,
) : ProfilePersistenceCapability {
    override fun readV4(): String? =
        storage().getItem(keys.snapshotV4)

    override fun writeV4(payload: String) {
        storage().setItem(keys.snapshotV4, payload)
    }

    override fun readLegacyProgressV2(): String? =
        storage().getItem(keys.legacyProgressV2)

    override fun readLegacyMatter(): String? =
        storage().getItem(keys.legacyMatter)

    override fun removeLegacyProgressV2() {
        storage().removeItem(keys.legacyProgressV2)
    }

    override fun removeLegacyMatter() {
        storage().removeItem(keys.legacyMatter)
    }
}

internal actual fun createPlatformTonePlaybackCapability(): TonePlaybackCapability =
    WebTonePlaybackCapability()

/** Owns one AudioContext instance without publishing it through a JavaScript global cache. */
@OptIn(ExperimentalWasmJsInterop::class)
private class WebTonePlaybackCapability : TonePlaybackCapability {
    private var context: JsAny? = null

    override fun unlock() {
        runCatching { context = unlockWebAudio(context) }
    }

    override fun play(request: ToneRequest) {
        runCatching {
            context = playWebTone(
                context,
                request.frequencyHz.toDouble(),
                request.durationSeconds.toDouble(),
                request.gain.toDouble(),
                webToneWaveValue(request.wave),
            )
        }
    }

    override fun close() {
        runCatching { closeWebAudio(context) }
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
        if (context.state === 'suspended') context.resume();
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
        if (context.state === 'suspended') context.resume();
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
        if (current) current.close();
    }""",
)
