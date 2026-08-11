// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

import kinetickk.ball.gameplay.api.EnemyType
import kinetickk.ball.gameplay.api.PickupType
import kinetickk.ball.gameplay.api.WeaponNodeType
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId

internal data class Enemy(
    val id: Int,
    val type: EnemyType,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var hp: Float,
    val maxHp: Float,
    val radius: Float,
    var actionTimer: Float = 0f,
    var flash: Float = 0f,
    var contactCooldown: Float = 0f,
    var weaponCooldown: Float = 0f,
    var previousX: Float = x,
    var previousY: Float = y,
    var dead: Boolean = false,
    var relicKillProcsEligible: Boolean = false,
    var relicQualificationCooldown: Float = 0f,
    val relicCounters: IntArray,
    val relicTimers: FloatArray,
    val relicValues: FloatArray,
)

internal data class Projectile(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    var life: Float,
    val hostile: Boolean = true,
    val damage: Float = 0f,
    var pierce: Int = 0,
    val colorIndex: Int = 0,
    val sourceWeapon: WeaponId? = null,
    var previousX: Float = x,
    var previousY: Float = y,
    private val recordedHitEnemyIds: MutableSet<Int> = mutableSetOf(),
) {
    init {
        require(recordedHitEnemyIds.size <= MAX_HIT_ENEMY_IDS) {
            "projectile hit history cannot exceed $MAX_HIT_ENEMY_IDS entries"
        }
    }

    fun tryRecordEnemyHit(enemyId: Int): Boolean {
        if (enemyId in recordedHitEnemyIds) return true
        if (recordedHitEnemyIds.size >= MAX_HIT_ENEMY_IDS) return false
        recordedHitEnemyIds += enemyId
        return true
    }

    fun hasRecordedEnemyHit(enemyId: Int): Boolean = enemyId in recordedHitEnemyIds

    fun retainLiveEnemyHits(liveEnemyIds: Set<Int>) {
        recordedHitEnemyIds.retainAll(liveEnemyIds)
    }

    fun isolatedCopy(): Projectile = copy(
        recordedHitEnemyIds = recordedHitEnemyIds.toMutableSet(),
    )

    companion object {
        const val MAX_HIT_ENEMY_IDS = 120
    }
}

internal data class Pickup(
    val type: PickupType,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var life: Float = 20f,
    var previousX: Float = x,
    var previousY: Float = y,
)

internal data class TrailPoint(var x: Float, var y: Float, var age: Float = 0f)

internal data class Totem(var x: Float, var y: Float, var pulse: Float = 0f)

internal data class WeaponNode(
    val type: WeaponNodeType,
    var x: Float,
    var y: Float,
    var life: Float,
    val maxLife: Float,
    var radius: Float,
)

internal data class WeaponOrbital(
    val index: Int,
    var x: Float,
    var y: Float,
    val radius: Float,
)

internal enum class WeaponHitCadence { DISCRETE, CONTINUOUS }

internal data class DamageResult(
    val amount: Float,
    val critical: Boolean,
)

internal data class DelayedRelicHit(
    val relicId: RelicId,
    val enemyId: Int,
    var delay: Float,
    val damage: Float,
)
