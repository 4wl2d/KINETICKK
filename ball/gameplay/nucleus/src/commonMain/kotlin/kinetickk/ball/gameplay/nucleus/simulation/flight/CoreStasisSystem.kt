// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.flight

import kinetickk.ball.content.api.CoreStasisNotices
import kinetickk.ball.content.api.CoreStasisTuning
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.lerp
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState
import kinetickk.ball.gameplay.nucleus.simulation.burst
import kinetickk.ball.gameplay.nucleus.simulation.combat.IncomingDamage
import kinetickk.ball.gameplay.nucleus.simulation.combat.takeDamage
import kinetickk.ball.gameplay.nucleus.simulation.shockwave
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Core Stasis, once per running step after the Core's position is final and before the weapons fire. A Core inside
 * the anchor radius whose speed average stays below the still speed gathers still seconds; a Core that leaves the
 * radius drags the anchor along, and a drag at the travel speed or faster is travel. The clock decays at the decay
 * rate while not still. Past the grace the stasis level slows weapon activation; held at full stasis past the fracture
 * delay, the Core takes self damage each tick (never in Easy mode) and a lethal tick ends the run as
 * [CoreStasisNotices.FROZEN]. The Nadir is exempt, phases other than RUNNING freeze the clocks, and nothing here draws
 * from a random stream.
 */
internal fun MutableGameState.updateCoreStasis(delta: Float) {
    val tuning = content.coreStasis
    if (!tuning.enabled || phase != GamePhase.RUNNING) return
    val state = coreStasis
    state.speedEma = lerp(state.speedEma, speed, 1f - exp(-delta / tuning.speedEmaSeconds))
    val dx = coreX - state.anchorX
    val dy = coreY - state.anchorY
    val distanceSquared = dx * dx + dy * dy
    val dragged = distanceSquared > tuning.anchorRadius * tuning.anchorRadius
    if (dragged) {
        val distance = sqrt(distanceSquared)
        val pull = (distance - tuning.anchorRadius) / distance
        state.anchorX += dx * pull
        state.anchorY += dy * pull
    }
    // An edge-strain stall crawls a few u/s and eventually drags the leash; below the travel speed it stays still.
    val travelling = dragged && state.speedEma >= tuning.travelSpeed
    // The Nadir's scorch already punishes slow flight there.
    val still = worldLayer != WorldLayer.NADIR && !travelling && state.speedEma < tuning.stillSpeed
    val before = state.stillSeconds
    val full = tuning.fullStasisSeconds
    state.stillSeconds = if (still) min(full, before + delta) else max(0f, before - tuning.decayRate * delta)
    // Measure-only: the frozen and fracture clocks stay 0, no FX and the level reads 0.
    if (!tuning.applyEffects) return
    emitCoreStasisThaw(coreStasisLevelOf(before, tuning), coreStasisLevelOf(state.stillSeconds, tuning))
    updateCoreStasisFracture(delta, tuning, atFull = state.stillSeconds >= full)
}

/** Holds full stasis past the delay, then ticks fracture damage on its own cadence (never in Easy mode). */
private fun MutableGameState.updateCoreStasisFracture(delta: Float, tuning: CoreStasisTuning, atFull: Boolean) {
    val state = coreStasis
    val freezing = atFull && !easyMode
    state.frozenSeconds = if (freezing) min(tuning.fractureDelaySeconds, state.frozenSeconds + delta) else 0f
    if (!freezing || state.frozenSeconds < tuning.fractureDelaySeconds) {
        state.fractureClock = 0f
        return
    }
    state.fractureClock += delta
    if (state.fractureClock < tuning.fractureTickSeconds) return
    state.fractureClock -= tuning.fractureTickSeconds
    // The scorch path: invulnerability blocks it, a revival answers a lethal tick, the Rebirth tier scales it.
    takeDamage(
        maxHp * tuning.fractureShare,
        ignoreReduction = true,
        kind = IncomingDamage.SELF,
        lethalReason = CoreStasisNotices.FROZEN,
    )
}

/** Each downward crossing of a quarter of the level sheds frost; the last one also rings a cryo shockwave. */
private fun MutableGameState.emitCoreStasisThaw(levelBefore: Float, levelAfter: Float) {
    if (ceil(levelAfter * 4f) >= ceil(levelBefore * 4f)) return
    val cryo = FxColorCode.elementTone(StatusElement.CRYO)
    burst(coreX, coreY, CORE_STASIS_THAW_BURST, cryo)
    if (levelAfter <= 0f) shockwave(coreX, coreY, 0.35f, 90f, cryo)
}

/** The 0..1 stasis level of [stillSeconds] under [tuning]. */
private fun coreStasisLevelOf(stillSeconds: Float, tuning: CoreStasisTuning): Float =
    ((stillSeconds - tuning.graceSeconds) / tuning.rampSeconds).coerceIn(0f, 1f)

/** The stasis level the run feels: 0 when the system is off, measure-only or thawed. */
internal fun MutableGameState.coreStasisLevel(): Float {
    val tuning = content.coreStasis
    return if (!tuning.enabled || !tuning.applyEffects) 0f else coreStasisLevelOf(coreStasis.stillSeconds, tuning)
}

/** Weapon activation factor 1 − maxWeaponSlow · level, in [1 − maxWeaponSlow, 1]; exactly 1 below the grace. */
internal fun MutableGameState.coreStasisActivationMultiplier(): Float {
    val level = coreStasisLevel()
    return if (level <= 0f) 1f else 1f - content.coreStasis.maxWeaponSlow * level
}

/** True while fracture ticks run: full stasis held past the delay, never in Easy mode. */
internal fun MutableGameState.coreStasisFracturing(): Boolean {
    val tuning = content.coreStasis
    return tuning.enabled && tuning.applyEffects && !easyMode &&
        coreStasis.stillSeconds >= tuning.fullStasisSeconds && coreStasis.frozenSeconds >= tuning.fractureDelaySeconds
}

/** Frost particles per thaw quarter (the FX reducer scales them by the particle density). */
private const val CORE_STASIS_THAW_BURST = 8
