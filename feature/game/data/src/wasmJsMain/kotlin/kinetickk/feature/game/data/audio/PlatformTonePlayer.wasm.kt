// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.data.audio

import kotlin.js.ExperimentalWasmJsInterop

internal actual fun createPlatformTonePlayer(): NumericTonePlayer = WebTonePlayer()

private class WebTonePlayer : NumericTonePlayer {
    override fun unlock() {
        runCatching { unlockWebAudio() }
    }

    override fun play(frequency: Float, durationSeconds: Float, volume: Float, wave: Int) {
        if (!isToneRequestAllowed(frequency, durationSeconds, volume, wave)) return
        runCatching { playWebTone(frequency.toDouble(), durationSeconds.toDouble(), volume.toDouble(), wave) }
    }

    override fun close() {
        runCatching { closeWebAudio() }
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun unlockWebAudio(): Unit = js(
    """{
        const AudioContext = globalThis.AudioContext || globalThis.webkitAudioContext;
        if (!AudioContext) return;
        const ctx = globalThis.__kinetickkAudio || (globalThis.__kinetickkAudio = new AudioContext());
        if (ctx.state === 'suspended') ctx.resume();
    }""",
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun playWebTone(frequency: Double, duration: Double, volume: Double, wave: Int): Unit = js(
    """{
        const AudioContext = globalThis.AudioContext || globalThis.webkitAudioContext;
        if (!AudioContext) return;
        const ctx = globalThis.__kinetickkAudio || (globalThis.__kinetickkAudio = new AudioContext());
        if (ctx.state === 'suspended') ctx.resume();
        const oscillator = ctx.createOscillator();
        const gain = ctx.createGain();
        oscillator.type = ['sine', 'square', 'sawtooth', 'triangle'][wave] || 'sine';
        oscillator.frequency.setValueAtTime(frequency, ctx.currentTime);
        gain.gain.setValueAtTime(0.0001, ctx.currentTime);
        gain.gain.exponentialRampToValueAtTime(Math.max(0.0001, volume), ctx.currentTime + 0.008);
        gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + duration);
        oscillator.connect(gain);
        gain.connect(ctx.destination);
        oscillator.start();
        oscillator.stop(ctx.currentTime + duration + 0.015);
    }""",
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun closeWebAudio(): Unit = js(
    """{
        const ctx = globalThis.__kinetickkAudio;
        if (ctx) {
            ctx.close();
            globalThis.__kinetickkAudio = null;
        }
    }""",
)
