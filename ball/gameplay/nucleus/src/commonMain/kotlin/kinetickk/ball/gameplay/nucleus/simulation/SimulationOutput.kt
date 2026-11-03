// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.foundation.collections.toImmutableList
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode

internal fun MutableGameState.takeSoundCues(): ImmutableList<GameplayAudioCue> {
    if (soundCues.isEmpty()) return immutableListOf()
    val result = soundCues.toImmutableList()
    soundCues.clear()
    return result
}

internal fun MutableGameState.takeVisualFxCues(): ImmutableList<VisualFxCue> = visualFxCues.drain()

internal fun MutableGameState.emitSound(cue: GameplayAudioCue) {
    if (soundCues.size < MutableGameState.MAX_GAMEPLAY_SOUND_CUES) soundCues += cue
}

internal fun MutableGameState.emitVisualFx(cue: VisualFxCue) {
    visualFxCues.record(cue)
}

internal fun MutableGameState.burst(x: Float, y: Float, requestedCount: Int, colorIndex: Int) {
    emitVisualFx(
        VisualFxCue.Burst(
            x = x,
            y = y,
            requestedCount = requestedCount,
            colorIndex = colorIndex,
            density = settings.particleDensity,
        ),
    )
}

internal fun MutableGameState.directionalBurst(
    x: Float,
    y: Float,
    requestedCount: Int,
    colorIndex: Int,
    directionX: Float,
    directionY: Float,
) {
    emitVisualFx(
        VisualFxCue.DirectionalBurst(
            x = x,
            y = y,
            requestedCount = requestedCount,
            colorIndex = colorIndex,
            directionX = directionX,
            directionY = directionY,
            density = settings.particleDensity,
        ),
    )
}

internal fun MutableGameState.shockwave(x: Float, y: Float, life: Float, maxRadius: Float, colorIndex: Int) {
    emitVisualFx(
        VisualFxCue.ShockwaveAdded(
            x = x,
            y = y,
            life = life,
            maxRadius = maxRadius,
            colorIndex = colorIndex,
        ),
    )
}

internal fun MutableGameState.addWeaponArc(
    fromX: Float,
    fromY: Float,
    toX: Float,
    toY: Float,
    life: Float = 0.14f,
    style: Int = LinkStyleCode.WEAPON,
) {
    emitVisualFx(
        VisualFxCue.WeaponArcAdded(
            fromX = fromX,
            fromY = fromY,
            toX = toX,
            toY = toY,
            life = life,
            style = style,
        ),
    )
}
