// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.projection

import kinetickk.ball.content.api.CoreShape
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableListOfSize
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*

/** The same runtime or equal content keeps the previous projection. */
internal fun MutableGameState.characterAbilityProjection(
    reusable: CharacterAbilityProjection?,
    identityRuntime: CharacterRuntime?,
): CharacterAbilityProjection {
    val runtime = characterRuntime
    if (reusable != null && (runtime === identityRuntime || reusable.matches(this, runtime))) return reusable
    val shape = coreShape
    return CharacterAbilityProjection(
        charge = runtime.charge,
        ringRadius = runtime.haloRadius,
        parryWindow = runtime.parryWindow,
        lattice = if (shape == CoreShape.TESSERACT) {
            immutableListOfSize(runtime.latticeCount) { CharacterLatticePoint(runtime.latticeX(it), runtime.latticeY(it)) }
        } else {
            immutableListOf()
        },
        facets = if (shape == CoreShape.PRISM) runtime.facets else 0,
        facetProgress = facetProgress(runtime),
        edge = if (shape == CoreShape.DIAMOND) runtime.edge else 0,
        stellarMass = if (shape == CoreShape.STAR) runtime.stellarMass else 0,
        novaFlash = runtime.novaFlash,
        haloBurstFlash = runtime.haloBurstFlash,
        polarity = if (shape == CoreShape.DIPOLE) runtime.polarity.ordinal else 0,
        polarityFlash = runtime.polarityFlash,
        beatPeriod = if (shape == CoreShape.PENTAGON) BEAT_PERIOD else 0f,
        beatWindow = if (shape == CoreShape.PENTAGON) BEAT_WINDOW else 0f,
        beatStreak = if (shape == CoreShape.PENTAGON) runtime.beatStreak else 0,
        blinkCharges = if (shape == CoreShape.CRESCENT) runtime.blinkCharges else 0,
        blinkRecharge = if (shape == CoreShape.CRESCENT) runtime.blinkRecharge / blinkRechargeSeconds() else 0f,
        scars = if (shape == CoreShape.CRESCENT) {
            immutableListOfSize(runtime.scarCount) { index -> scarProjection(runtime, index) }
        } else {
            immutableListOf()
        },
        drones = if (shape == CoreShape.HEXAGON) {
            immutableListOfSize(runtime.droneCount) { index ->
                CharacterDroneProjection(
                    x = runtime.droneValue(index, CharacterRuntime.DRONE_X),
                    y = runtime.droneValue(index, CharacterRuntime.DRONE_Y),
                    previousX = runtime.droneValue(index, CharacterRuntime.DRONE_PREVIOUS_X),
                    previousY = runtime.droneValue(index, CharacterRuntime.DRONE_PREVIOUS_Y),
                    darting = runtime.droneValue(index, CharacterRuntime.DRONE_DART) > 0f,
                )
            }
        } else {
            immutableListOf()
        },
        ghostActive = ghostActive,
        ghostX = if (ghostActive) ghostX else 0f,
        ghostY = if (ghostActive) ghostY else 0f,
        previousGhostX = if (ghostActive) previousGhostX else 0f,
        previousGhostY = if (ghostActive) previousGhostY else 0f,
        secondGhostActive = secondGhostActive,
        secondGhostX = if (secondGhostActive) secondGhostX else 0f,
        secondGhostY = if (secondGhostActive) secondGhostY else 0f,
    )
}

private fun MutableGameState.facetProgress(runtime: CharacterRuntime): Float =
    if (coreShape == CoreShape.PRISM && runtime.facets < CharacterRuntime.MAX_FACETS) {
        (runtime.facetTimer / facetRegrowthSeconds()).coerceIn(0f, 1f)
    } else {
        0f
    }

private fun MutableGameState.scarProjection(runtime: CharacterRuntime, index: Int): CharacterScarProjection =
    CharacterScarProjection(
        x0 = runtime.scarValue(index, CharacterRuntime.SCAR_X0),
        y0 = runtime.scarValue(index, CharacterRuntime.SCAR_Y0),
        x1 = runtime.scarValue(index, CharacterRuntime.SCAR_X1),
        y1 = runtime.scarValue(index, CharacterRuntime.SCAR_Y1),
        fuse = scarFuse(runtime, index),
        fade = scarFade(runtime, index),
    )

/** Fuse progress of scar [index]: 0 when laid, 1 from its detonation on. */
private fun MutableGameState.scarFuse(runtime: CharacterRuntime, index: Int): Float {
    if (runtime.scarValue(index, CharacterRuntime.SCAR_AGE) >= 0f) return 1f
    return (1f - (runtime.scarValue(index, CharacterRuntime.SCAR_DETONATE_AT) - elapsed) / SCAR_FUSE_SECONDS).coerceIn(0f, 1f)
}

/** Seconds since scar [index] detonated (0 before). */
private fun scarFade(runtime: CharacterRuntime, index: Int): Float =
    runtime.scarValue(index, CharacterRuntime.SCAR_AGE).coerceAtLeast(0f)

/** Compares every projected field bit for bit against what [characterAbilityProjection] would build now. */
private fun CharacterAbilityProjection.matches(state: MutableGameState, runtime: CharacterRuntime): Boolean {
    val shape = state.coreShape
    if (!charge.sameBitsAs(runtime.charge) || !ringRadius.sameBitsAs(runtime.haloRadius) ||
        !parryWindow.sameBitsAs(runtime.parryWindow) ||
        facets != (if (shape == CoreShape.PRISM) runtime.facets else 0) ||
        !facetProgress.sameBitsAs(state.facetProgress(runtime)) ||
        edge != (if (shape == CoreShape.DIAMOND) runtime.edge else 0) ||
        stellarMass != (if (shape == CoreShape.STAR) runtime.stellarMass else 0) ||
        !novaFlash.sameBitsAs(runtime.novaFlash) || !haloBurstFlash.sameBitsAs(runtime.haloBurstFlash) ||
        polarity != (if (shape == CoreShape.DIPOLE) runtime.polarity.ordinal else 0) ||
        !polarityFlash.sameBitsAs(runtime.polarityFlash) ||
        !beatPeriod.sameBitsAs(if (shape == CoreShape.PENTAGON) BEAT_PERIOD else 0f) ||
        !beatWindow.sameBitsAs(if (shape == CoreShape.PENTAGON) BEAT_WINDOW else 0f) ||
        beatStreak != (if (shape == CoreShape.PENTAGON) runtime.beatStreak else 0) ||
        blinkCharges != (if (shape == CoreShape.CRESCENT) runtime.blinkCharges else 0) ||
        !blinkRecharge.sameBitsAs(if (shape == CoreShape.CRESCENT) runtime.blinkRecharge / state.blinkRechargeSeconds() else 0f) ||
        ghostActive != state.ghostActive || secondGhostActive != state.secondGhostActive
    ) {
        return false
    }
    if (ghostActive && (!ghostX.sameBitsAs(state.ghostX) || !ghostY.sameBitsAs(state.ghostY) ||
            !previousGhostX.sameBitsAs(state.previousGhostX) || !previousGhostY.sameBitsAs(state.previousGhostY))
    ) {
        return false
    }
    if (secondGhostActive && (!secondGhostX.sameBitsAs(state.secondGhostX) || !secondGhostY.sameBitsAs(state.secondGhostY))) {
        return false
    }
    val latticeCount = if (shape == CoreShape.TESSERACT) runtime.latticeCount else 0
    if (lattice.size != latticeCount) return false
    for (index in lattice.indices) {
        val point = lattice[index]
        if (!point.x.sameBitsAs(runtime.latticeX(index)) || !point.y.sameBitsAs(runtime.latticeY(index))) return false
    }
    val scarCount = if (shape == CoreShape.CRESCENT) runtime.scarCount else 0
    if (scars.size != scarCount) return false
    for (index in scars.indices) {
        val scar = scars[index]
        // Field by field from the runtime: the check builds no projection.
        if (!scar.x0.sameBitsAs(runtime.scarValue(index, CharacterRuntime.SCAR_X0)) ||
            !scar.y0.sameBitsAs(runtime.scarValue(index, CharacterRuntime.SCAR_Y0)) ||
            !scar.x1.sameBitsAs(runtime.scarValue(index, CharacterRuntime.SCAR_X1)) ||
            !scar.y1.sameBitsAs(runtime.scarValue(index, CharacterRuntime.SCAR_Y1)) ||
            !scar.fuse.sameBitsAs(state.scarFuse(runtime, index)) || !scar.fade.sameBitsAs(scarFade(runtime, index))
        ) {
            return false
        }
    }
    val droneCount = if (shape == CoreShape.HEXAGON) runtime.droneCount else 0
    if (drones.size != droneCount) return false
    for (index in drones.indices) {
        val drone = drones[index]
        if (!drone.x.sameBitsAs(runtime.droneValue(index, CharacterRuntime.DRONE_X)) ||
            !drone.y.sameBitsAs(runtime.droneValue(index, CharacterRuntime.DRONE_Y)) ||
            !drone.previousX.sameBitsAs(runtime.droneValue(index, CharacterRuntime.DRONE_PREVIOUS_X)) ||
            !drone.previousY.sameBitsAs(runtime.droneValue(index, CharacterRuntime.DRONE_PREVIOUS_Y)) ||
            drone.darting != (runtime.droneValue(index, CharacterRuntime.DRONE_DART) > 0f)
        ) {
            return false
        }
    }
    return true
}
