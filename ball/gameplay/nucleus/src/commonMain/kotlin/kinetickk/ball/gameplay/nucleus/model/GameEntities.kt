// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.WeaponNodeType
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId

@ConsistentCopyVisibility
internal data class Enemy private constructor(
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
    val relicCounters: CopyOnWriteIntArray,
    val relicTimers: CopyOnWriteFloatArray,
    val relicValues: CopyOnWriteFloatArray,
) {
    constructor(
        id: Int,
        type: EnemyType,
        x: Float,
        y: Float,
        vx: Float = 0f,
        vy: Float = 0f,
        hp: Float,
        maxHp: Float,
        radius: Float,
        actionTimer: Float = 0f,
        flash: Float = 0f,
        contactCooldown: Float = 0f,
        weaponCooldown: Float = 0f,
        previousX: Float = x,
        previousY: Float = y,
        dead: Boolean = false,
        relicKillProcsEligible: Boolean = false,
        relicQualificationCooldown: Float = 0f,
        relicCounters: IntArray,
        relicTimers: FloatArray,
        relicValues: FloatArray,
    ) : this(
        id = id,
        type = type,
        x = x,
        y = y,
        vx = vx,
        vy = vy,
        hp = hp,
        maxHp = maxHp,
        radius = radius,
        actionTimer = actionTimer,
        flash = flash,
        contactCooldown = contactCooldown,
        weaponCooldown = weaponCooldown,
        previousX = previousX,
        previousY = previousY,
        dead = dead,
        relicKillProcsEligible = relicKillProcsEligible,
        relicQualificationCooldown = relicQualificationCooldown,
        relicCounters = CopyOnWriteIntArray(relicCounters),
        relicTimers = CopyOnWriteFloatArray(relicTimers),
        relicValues = CopyOnWriteFloatArray(relicValues),
    )

    /** Copies mutable scalar state while sharing large relic buffers until first changed write. */
    fun isolatedCopy(): Enemy = copy(
        relicCounters = relicCounters.fork(),
        relicTimers = relicTimers.fork(),
        relicValues = relicValues.fork(),
    )
}

internal class Projectile(
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
    private var recordedHitEnemyIds: IntArray? = null,
    private var recordedHitEnemyCount: Int = 0,
) {
    init {
        require((recordedHitEnemyIds?.size ?: 0) <= MAX_HIT_ENEMY_IDS) {
            "projectile hit history storage cannot exceed $MAX_HIT_ENEMY_IDS entries"
        }
        require(recordedHitEnemyCount in 0..MAX_HIT_ENEMY_IDS) {
            "projectile hit history cannot exceed $MAX_HIT_ENEMY_IDS entries"
        }
        require(recordedHitEnemyCount <= (recordedHitEnemyIds?.size ?: 0)) {
            "projectile hit history count exceeds its storage"
        }
    }

    fun tryRecordEnemyHit(enemyId: Int): Boolean {
        val match = findRecordedEnemyHit(enemyId)
        if (match >= 0) return true
        if (recordedHitEnemyCount >= MAX_HIT_ENEMY_IDS) return false
        var storage = recordedHitEnemyIds
        if (storage == null || storage.isEmpty()) {
            storage = IntArray(INITIAL_HIT_ENEMY_CAPACITY)
            recordedHitEnemyIds = storage
        } else if (recordedHitEnemyCount == storage.size) {
            storage = storage.copyOf(
                minOf(MAX_HIT_ENEMY_IDS, storage.size * 2),
            )
            recordedHitEnemyIds = storage
        }
        val insertionIndex = -match - 1
        var index = recordedHitEnemyCount
        while (index > insertionIndex) {
            storage[index] = storage[index - 1]
            index--
        }
        storage[insertionIndex] = enemyId
        recordedHitEnemyCount++
        return true
    }

    fun hasRecordedEnemyHit(enemyId: Int): Boolean = findRecordedEnemyHit(enemyId) >= 0

    val hasRecordedEnemyHits: Boolean
        get() = recordedHitEnemyCount != 0

    fun retainLiveEnemyHits(
        liveEnemyIds: IntArray,
        liveEnemyCount: Int,
    ) {
        val storage = recordedHitEnemyIds ?: return
        var hitIndex = 0
        var liveIndex = 0
        var retainedCount = 0
        while (hitIndex < recordedHitEnemyCount && liveIndex < liveEnemyCount) {
            val hitEnemyId = storage[hitIndex]
            val liveEnemyId = liveEnemyIds[liveIndex]
            when {
                hitEnemyId < liveEnemyId -> hitIndex++
                hitEnemyId > liveEnemyId -> liveIndex++
                else -> {
                    storage[retainedCount++] = hitEnemyId
                    hitIndex++
                    liveIndex++
                }
            }
        }
        recordedHitEnemyCount = retainedCount
        if (retainedCount == 0) recordedHitEnemyIds = null
    }

    fun isolatedCopy(): Projectile = Projectile(
        x = x,
        y = y,
        vx = vx,
        vy = vy,
        radius = radius,
        life = life,
        hostile = hostile,
        damage = damage,
        pierce = pierce,
        colorIndex = colorIndex,
        sourceWeapon = sourceWeapon,
        previousX = previousX,
        previousY = previousY,
        recordedHitEnemyIds = recordedHitEnemyIds?.copyOf(recordedHitEnemyCount),
        recordedHitEnemyCount = recordedHitEnemyCount,
    )

    private fun findRecordedEnemyHit(enemyId: Int): Int {
        val storage = recordedHitEnemyIds ?: return -1
        if (recordedHitEnemyCount <= LINEAR_SEARCH_THRESHOLD) {
            for (index in 0 until recordedHitEnemyCount) {
                val recordedEnemyId = storage[index]
                if (recordedEnemyId == enemyId) return index
                if (recordedEnemyId > enemyId) return -index - 1
            }
            return -recordedHitEnemyCount - 1
        }

        var low = 0
        var high = recordedHitEnemyCount - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val recordedEnemyId = storage[middle]
            when {
                recordedEnemyId < enemyId -> low = middle + 1
                recordedEnemyId > enemyId -> high = middle - 1
                else -> return middle
            }
        }
        return -low - 1
    }

    companion object {
        const val MAX_HIT_ENEMY_IDS = 120
        private const val INITIAL_HIT_ENEMY_CAPACITY = 4
        private const val LINEAR_SEARCH_THRESHOLD = 8
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
