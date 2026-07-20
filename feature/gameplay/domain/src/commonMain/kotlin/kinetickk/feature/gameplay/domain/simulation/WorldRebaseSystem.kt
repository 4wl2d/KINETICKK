// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.domain.simulation

import kinetickk.feature.gameplay.domain.protocol.VisualFxCue

internal fun MutableGameState.rebaseWorldIfNeeded() {
    if (kotlin.math.abs(coreX) < 250_000f && kotlin.math.abs(coreY) < 250_000f) return
    val shiftX = coreX
    val shiftY = coreY
    coreX -= shiftX
    coreY -= shiftY
    previousCoreX -= shiftX
    previousCoreY -= shiftY
    previousSingularityX -= shiftX
    previousSingularityY -= shiftY
    cameraX -= shiftX
    cameraY -= shiftY
    trailLastX -= shiftX
    trailLastY -= shiftY
    enemies.forEach { it.x -= shiftX; it.y -= shiftY; it.previousX -= shiftX; it.previousY -= shiftY }
    projectiles.forEach { it.x -= shiftX; it.y -= shiftY; it.previousX -= shiftX; it.previousY -= shiftY }
    pickups.forEach { it.x -= shiftX; it.y -= shiftY }
    trail.forEach { it.x -= shiftX; it.y -= shiftY }
    emitVisualFx(VisualFxCue.WorldRebased(shiftX, shiftY))
    weaponNodes.forEach { it.x -= shiftX; it.y -= shiftY }
    weaponOrbitals.forEach { it.x -= shiftX; it.y -= shiftY }
    totem?.let { it.x -= shiftX; it.y -= shiftY }
    morningstarX -= shiftX
    morningstarY -= shiftY
    weaponBeamStartX -= shiftX
    weaponBeamStartY -= shiftY
    weaponBeamEndX -= shiftX
    weaponBeamEndY -= shiftY
}
