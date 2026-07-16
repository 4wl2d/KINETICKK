// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.audio.resources.tone

internal interface NumericTonePlayer {
    fun unlock()
    fun play(frequency: Float, durationSeconds: Float, volume: Float, wave: Int)
    fun close()
}

internal expect class PlatformTonePlayer() : NumericTonePlayer {
    override fun unlock()
    override fun play(frequency: Float, durationSeconds: Float, volume: Float, wave: Int)
    override fun close()
}

internal fun NumericTonePlayer.playSafely(
    frequency: Float,
    durationSeconds: Float,
    volume: Float,
    wave: Int,
) {
    if (!isToneRequestAllowed(frequency, durationSeconds, volume, wave)) return
    runCatching { play(frequency, durationSeconds, volume, wave) }
}

internal fun isToneRequestAllowed(
    frequency: Float,
    durationSeconds: Float,
    volume: Float,
    wave: Int,
): Boolean = frequency.isFinite() && frequency in 20f..20_000f &&
    durationSeconds.isFinite() && durationSeconds in 0.001f..1f &&
    volume.isFinite() && volume in 0f..1f && wave in 0..3
