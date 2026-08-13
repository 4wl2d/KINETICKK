// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue

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
    for (index in enemies.indices) {
        val enemy = enemies[index]
        enemy.x -= shiftX
        enemy.y -= shiftY
        enemy.previousX -= shiftX
        enemy.previousY -= shiftY
    }
    for (index in projectiles.indices) {
        val projectile = projectiles[index]
        projectile.x -= shiftX
        projectile.y -= shiftY
        projectile.previousX -= shiftX
        projectile.previousY -= shiftY
    }
    for (index in pickups.indices) {
        val pickup = pickups[index]
        pickup.x -= shiftX
        pickup.y -= shiftY
    }
    for (index in trail.indices) {
        val point = trail[index]
        point.x -= shiftX
        point.y -= shiftY
    }
    emitVisualFx(VisualFxCue.WorldRebased(shiftX, shiftY))
    for (index in weaponNodes.indices) {
        val node = weaponNodes[index]
        node.x -= shiftX
        node.y -= shiftY
    }
    for (index in weaponOrbitals.indices) {
        val orbital = weaponOrbitals[index]
        orbital.x -= shiftX
        orbital.y -= shiftY
    }
    totem?.let { it.x -= shiftX; it.y -= shiftY }
    morningstarX -= shiftX
    morningstarY -= shiftY
    weaponBeamStartX -= shiftX
    weaponBeamStartY -= shiftY
    weaponBeamEndX -= shiftX
    weaponBeamEndY -= shiftY
}
