// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kotlin.math.exp
import kotlin.math.max
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.api.*
import kinetickk.ball.gameplay.nucleus.simulation.variety.*

/**
 * Pickups drift from where they dropped until they enter the magnet radius; there they latch and home on
 * the Core at `max(vacuumSpeed, coreSpeed × factor)` with no damping until collected. Keys and Relic resonances
 * never expire, and a latched pickup does not age while it homes.
 */
internal fun MutableGameState.updatePickups(delta: Float) {
    val radius = effectivePickupRadius()
    val tuning = content.pickups
    val homingSpeed = max(tuning.vacuumSpeed, speed * tuning.vacuumCoreSpeedFactor)
    val drag = exp(-1.8f * delta)
    for (pickupIndex in pickups.indices) {
        val pickup = pickups[pickupIndex]
        pickup.previousX = pickup.x
        pickup.previousY = pickup.y
        val dx = coreX - pickup.x
        val dy = coreY - pickup.y
        val distance = max(1f, length(dx, dy))
        if (!pickup.attracted && distance < radius) pickup.attracted = true
        // A latched pickup is on its way in and never fades before it arrives.
        if (pickup.expires && !pickup.attracted) pickup.life -= delta
        if (pickup.attracted) {
            pickup.vx = dx / distance * homingSpeed
            pickup.vy = dy / distance * homingSpeed
        } else {
            pickup.vx *= drag
            pickup.vy *= drag
        }
        pickup.x += pickup.vx * delta
        pickup.y += pickup.vy * delta
    }
    pickups.removeMatchingStable { pickup -> pickup.expires && pickup.life <= 0f }
    while (pickups.size > MutableGameState.MAX_PICKUPS) {
        val oldest = oldestEvictablePickup()
        pickups.removeAt(if (oldest >= 0) oldest else 0)
    }
}

/** Data and repairs fade; Keys and Relic resonances wait until they are collected. */
internal val Pickup.expires: Boolean get() = type == PickupType.DATA || type == PickupType.REPAIR

/** Index of the oldest pickup the cap may evict (Data first, then repairs), or -1. */
internal fun MutableGameState.oldestEvictablePickup(): Int {
    var repair = -1
    for (index in pickups.indices) {
        val type = pickups[index].type
        if (type == PickupType.DATA) return index
        if (repair < 0 && type == PickupType.REPAIR) repair = index
    }
    return repair
}

/**
 * A level-up, and the end of a choice, latch every Data pickup inside the viewport onto the Core (unless Event Horizon's
 * liability holds).
 */
internal fun MutableGameState.vacuumOnScreenData() {
    if (pickups.isEmpty() || build.liability(kinetickk.ball.content.api.RelicId.EVENT_HORIZON)) return
    val halfWidth = screenWidth * 0.5f
    val halfHeight = screenHeight * 0.5f
    for (index in pickups.indices) {
        val pickup = pickups[index]
        if (pickup.type != PickupType.DATA || pickup.attracted) continue
        if (kotlin.math.abs(pickup.x - cameraX) <= halfWidth && kotlin.math.abs(pickup.y - cameraY) <= halfHeight) {
            pickup.attracted = true
        }
    }
}

/** At the cap a new Data or repair is dropped; a Key or Relic resonance evicts the oldest Data instead. */
internal fun MutableGameState.addPickup(pickup: Pickup) {
    if (pickup.type == PickupType.DATA) pickup.life = blackoutDataLife(pickup.life)
    if (pickups.size >= MutableGameState.MAX_PICKUPS) {
        if (pickup.expires) return
        val oldest = oldestEvictablePickup()
        if (oldest < 0) return
        pickups.removeAt(oldest)
    }
    pickups += pickup
}
