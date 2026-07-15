package void.kinetic.audio

import kotlin.js.ExperimentalWasmJsInterop

internal actual class PlatformTonePlayer actual constructor() {
    actual fun unlock() {
        unlockWebAudio()
    }

    actual fun play(frequency: Float, durationSeconds: Float, volume: Float, wave: Int) {
        playWebTone(frequency.toDouble(), durationSeconds.toDouble(), volume.toDouble(), wave)
    }

    actual fun close() {
        closeWebAudio()
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun unlockWebAudio(): Unit = js(
    """{
        const AudioContext = globalThis.AudioContext || globalThis.webkitAudioContext;
        if (!AudioContext) return;
        const ctx = globalThis.__kineticVoidAudio || (globalThis.__kineticVoidAudio = new AudioContext());
        if (ctx.state === 'suspended') ctx.resume();
    }""",
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun playWebTone(frequency: Double, duration: Double, volume: Double, wave: Int): Unit = js(
    """{
        const AudioContext = globalThis.AudioContext || globalThis.webkitAudioContext;
        if (!AudioContext) return;
        const ctx = globalThis.__kineticVoidAudio || (globalThis.__kineticVoidAudio = new AudioContext());
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
        const ctx = globalThis.__kineticVoidAudio;
        if (ctx) {
            ctx.close();
            globalThis.__kineticVoidAudio = null;
        }
    }""",
)
