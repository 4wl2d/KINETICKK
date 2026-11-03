// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.flight

import kinetickk.ball.content.api.CoreStasisNotices
import kinetickk.ball.content.api.CoreStasisTuning
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.lerp
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.render.CoreStasisMotion
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState
import kinetickk.ball.gameplay.nucleus.simulation.burst
import kinetickk.ball.gameplay.nucleus.simulation.emitSound
import kinetickk.ball.gameplay.nucleus.simulation.endRun
import kinetickk.ball.gameplay.nucleus.simulation.shockwave
import kinetickk.ball.gameplay.nucleus.simulation.variety.shatterFrozenCoreSoftly
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max

/**
 * Core Freeze, once per running step after the Core's position and velocity are final and before the weapons fire.
 * The filtered speed follows the Core's scalar speed; below the freeze speed the freeze grows, at or above the thaw
 * speed it thaws, and between them it holds. A hold window or a host that takes no input holds it too. A full freeze
 * ends the run at once as [CoreStasisNotices.FROZEN] (no shield, invulnerability or revival answers it); Easy mode
 * shatters the ice and starts over. Every layer counts, phases other than RUNNING never step, and nothing here draws
 * from a random stream.
 */
internal fun MutableGameState.updateCoreStasis(delta: Float) {
    val tuning = content.coreStasis
    if (!tuning.enabled || phase != GamePhase.RUNNING) return
    val state = coreStasis
    state.speedFilter = lerp(state.speedFilter, speed, 1f - exp(-delta / tuning.speedFilterSeconds))
    if (state.holdSeconds > 0f) {
        // Snapped like the freeze: a hold of n steps holds exactly n steps whatever the rounding.
        val left = state.holdSeconds - delta
        state.holdSeconds = if (left < 0.5f * delta) 0f else left
        return
    }
    if (state.inputHeld) return
    val before = state.freeze
    state.freeze = when {
        state.speedFilter < tuning.freezeSpeed -> grownFreeze(before, delta, tuning)
        state.speedFilter >= tuning.thawSpeed -> max(0f, before - delta / tuning.thawSeconds)
        else -> before
    }
    // Measure-only: the freeze runs, but nothing shows, sounds or ends.
    if (!tuning.applyEffects) return
    emitCoreStasisThaw(before, state.freeze)
    if (state.freeze >= 1f) freezeCoreSolid()
}

/**
 * The freeze after a step below the freeze speed, snapped to 1 within half a step of it, so a Core at rest freezes on
 * the step that completes [CoreStasisTuning.freezeSeconds] whatever the rounding of the sum.
 */
private fun grownFreeze(before: Float, delta: Float, tuning: CoreStasisTuning): Float {
    val step = delta / tuning.freezeSeconds
    val grown = before + step
    return if (grown >= 1f - 0.5f * step) 1f else grown
}

/**
 * The Core froze solid: the ice cracks (sound and a cryo quake ring), then the run ends where it is, past every shield,
 * invulnerability and revival. Easy mode shatters the ice off instead and the freeze starts over.
 */
private fun MutableGameState.freezeCoreSolid() {
    emitSound(GameplayAudioCue.CORE_FROZEN)
    shockwave(coreX, coreY, CORE_FREEZE_RING_LIFE, CORE_FREEZE_RING_RADIUS, FxColorCode.quake(StatusElement.CRYO))
    if (easyMode) {
        coreStasis.freeze = 0f
        burst(coreX, coreY, CORE_FREEZE_SHATTER_BURST, FxColorCode.elementTone(StatusElement.CRYO))
        shatterFrozenCoreSoftly()
        return
    }
    endRun(CoreStasisNotices.FROZEN, burstColor = FxColorCode.elementTone(StatusElement.CRYO))
}

/** Each downward crossing of a quarter of the freeze sheds frost; the last one also rings a cryo shockwave. */
private fun MutableGameState.emitCoreStasisThaw(before: Float, after: Float) {
    if (ceil(after * 4f) >= ceil(before * 4f)) return
    val cryo = FxColorCode.elementTone(StatusElement.CRYO)
    burst(coreX, coreY, CORE_STASIS_THAW_BURST, cryo)
    if (after <= 0f) shockwave(coreX, coreY, 0.35f, 90f, cryo)
}

/** Holds the freeze where it is for at least [seconds] (a closed choice, a layer switch); nothing while off. */
internal fun MutableGameState.holdCoreStasis(seconds: Float) {
    if (!content.coreStasis.enabled) return
    coreStasis.holdSeconds = max(coreStasis.holdSeconds, seconds)
}

/** The host takes input again ([held] false) or not (a session workflow landing); the freeze holds while it does not. */
internal fun MutableGameState.setCoreStasisInputHeld(held: Boolean) {
    coreStasis.inputHeld = held
}

/** The freeze the run shows, 0..1: 0 when the system is off or measure-only. */
internal fun MutableGameState.coreStasisLevel(): Float {
    val tuning = content.coreStasis
    return if (!tuning.enabled || !tuning.applyEffects) 0f else coreStasis.freeze
}

/** Where the freeze the run shows is heading ([CoreStasisMotion]). */
internal fun MutableGameState.coreStasisMotion(): CoreStasisMotion {
    val tuning = content.coreStasis
    val state = coreStasis
    return when {
        !tuning.enabled || !tuning.applyEffects || state.freeze <= 0f -> CoreStasisMotion.CLEAR
        state.freeze >= 1f -> CoreStasisMotion.FROZEN
        state.holdSeconds > 0f || state.inputHeld -> CoreStasisMotion.HOLDING
        state.speedFilter < tuning.freezeSpeed -> CoreStasisMotion.FREEZING
        state.speedFilter >= tuning.thawSpeed -> CoreStasisMotion.THAWING
        else -> CoreStasisMotion.HOLDING
    }
}

/** Frost particles per thaw quarter (the FX reducer scales them by the particle density). */
private const val CORE_STASIS_THAW_BURST = 8

/** Ice shards a shattering freeze sheds in Easy mode. */
private const val CORE_FREEZE_SHATTER_BURST = 26

/** The jagged cryo ring of a Core freezing solid. */
private const val CORE_FREEZE_RING_LIFE = 0.55f
private const val CORE_FREEZE_RING_RADIUS = 150f
