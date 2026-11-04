// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.weapons

import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.render.*
import kotlin.math.abs
import kinetickk.ball.gameplay.nucleus.simulation.*

/*
 * Weapon entities: [advanceWeaponEntities] moves them and runs their fuses in the weapon step;
 * [resolveWeaponEntityHits] resolves their contacts and fields in the collision pass, after enemies
 * moved. Finished entities (life spent) leave the list only after each pass, never inside a hit.
 */

internal fun MutableGameState.advanceWeaponEntities(delta: Float) {
    val runtime = weaponRuntime
    val entities = runtime.entities
    if (entities.isEmpty()) return
    val count = entities.size
    runtime.entityPassActive = true
    try {
        for (index in 0 until count) {
            val entity = entities[index]
            if (entity.life <= 0f) continue
            when (entity.kind) {
                WeaponEntityKind.DISC -> advanceDisc(entity, delta)
                WeaponEntityKind.SENTRY -> advanceSentry(entity, delta)
                WeaponEntityKind.LENS -> advanceLens(entity, delta)
                WeaponEntityKind.WELL -> advanceWell(entity, delta)
                WeaponEntityKind.RIFT -> advanceRift(entity, delta)
                WeaponEntityKind.SHARD -> advanceShard(entity, delta)
                WeaponEntityKind.MISSILE -> advanceMissile(entity, delta)
                WeaponEntityKind.RING -> entity.life -= delta
                WeaponEntityKind.TETHER, WeaponEntityKind.BEAM -> Unit
            }
        }
    } finally {
        runtime.entityPassActive = false
    }
    entities.removeMatchingStable { it.life <= 0f }
}

internal fun MutableGameState.resolveWeaponEntityHits(delta: Float) {
    val runtime = weaponRuntime
    val entities = runtime.entities
    if (entities.isEmpty()) return
    val count = entities.size
    runtime.entityPassActive = true
    try {
        for (index in 0 until count) {
            if (phase != GamePhase.RUNNING) break
            val entity = entities[index]
            if (entity.life <= 0f) continue
            when (entity.kind) {
                WeaponEntityKind.DISC -> discHits(entity)
                WeaponEntityKind.LENS -> lensContact(entity)
                WeaponEntityKind.WELL -> wellTick(entity, delta)
                WeaponEntityKind.SHARD -> shardHits(entity)
                WeaponEntityKind.MISSILE -> missileHits(entity)
                WeaponEntityKind.TETHER -> tetherTick(entity, delta)
                WeaponEntityKind.BEAM -> beamTick(entity, delta)
                WeaponEntityKind.SENTRY, WeaponEntityKind.RIFT, WeaponEntityKind.RING -> Unit
            }
        }
    } finally {
        runtime.entityPassActive = false
    }
    entities.removeMatchingStable { it.life <= 0f }
}
