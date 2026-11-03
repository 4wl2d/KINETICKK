// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.characters

import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_PREVIOUS_X
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_PREVIOUS_Y
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_STRIDE
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_TARGET_X
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_TARGET_Y
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_X
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.DRONE_Y
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_STRIDE
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_X0
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_X1
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_Y0
import kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.Companion.SCAR_Y1
import kinetickk.ball.gameplay.nucleus.simulation.*

/** Shifts every stored character position after a world rebase. */
internal fun MutableGameState.rebaseCharacterRuntime(shiftX: Float, shiftY: Float) {
    val ability = characterRuntime
    ability.dashOriginX -= shiftX
    ability.dashOriginY -= shiftY
    ability.blinkFromX -= shiftX
    ability.blinkFromY -= shiftY
    ability.blinkToX -= shiftX
    ability.blinkToY -= shiftY
    for (index in 0 until ability.latticeCount) {
        ability.latticeNodes[index * 2] = ability.latticeNodes[index * 2] - shiftX
        ability.latticeNodes[index * 2 + 1] = ability.latticeNodes[index * 2 + 1] - shiftY
    }
    if (ability.ghostSamples > 0) {
        val path = ability.ghostPath
        for (index in 0 until CharacterRuntime.GHOST_SAMPLES) {
            val base = index * CharacterRuntime.GHOST_STRIDE
            path[base + CharacterRuntime.GHOST_X] = path[base + CharacterRuntime.GHOST_X] - shiftX
            path[base + CharacterRuntime.GHOST_Y] = path[base + CharacterRuntime.GHOST_Y] - shiftY
        }
    }
    val drones = ability.drones
    for (index in 0 until ability.droneCount) {
        val base = index * DRONE_STRIDE
        drones[base + DRONE_X] = drones[base + DRONE_X] - shiftX
        drones[base + DRONE_Y] = drones[base + DRONE_Y] - shiftY
        drones[base + DRONE_PREVIOUS_X] = drones[base + DRONE_PREVIOUS_X] - shiftX
        drones[base + DRONE_PREVIOUS_Y] = drones[base + DRONE_PREVIOUS_Y] - shiftY
        drones[base + DRONE_TARGET_X] = drones[base + DRONE_TARGET_X] - shiftX
        drones[base + DRONE_TARGET_Y] = drones[base + DRONE_TARGET_Y] - shiftY
    }
    for (index in 0 until ability.scarCount) {
        val base = index * SCAR_STRIDE
        ability.scars[base + SCAR_X0] = ability.scars[base + SCAR_X0] - shiftX
        ability.scars[base + SCAR_Y0] = ability.scars[base + SCAR_Y0] - shiftY
        ability.scars[base + SCAR_X1] = ability.scars[base + SCAR_X1] - shiftX
        ability.scars[base + SCAR_Y1] = ability.scars[base + SCAR_Y1] - shiftY
    }
}
