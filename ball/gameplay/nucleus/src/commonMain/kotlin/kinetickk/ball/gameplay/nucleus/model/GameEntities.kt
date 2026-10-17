// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.render.ProjectileKind
import kinetickk.ball.gameplay.nucleus.render.WeaponNodeType
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
    /** True when the killing blow was a direct hit, status, Break or reaction (not item or relic proc damage). */
    var killClausesEligible: Boolean = false,
    var hitQualificationCooldown: Float = 0f,
    var characterMarkTime: Float = 0f,
    var lastCharacterDash: Int = -1,
    /** Element ordinal of an Elite's or the Architect's aspect, or -1; other types read [EnemyProfile]. */
    var aspect: Int = -1,
    var aspectImmune: Boolean = false,
    /** Architect phase (0..2); each starts when a third of its integrity is gone. */
    var phase: Int = 0,
    /** Absolute run time until which the Architect is shifting and takes no damage. */
    var shiftUntil: Float = 0f,
    /** Escorts the Architect's last shift still has to call in; consumed outside the damage pipeline. */
    var escortsPending: Int = 0,
    /** Absolute run time of the last damage the Architect took; it keeps its adds coming while engaged. */
    var lastDamagedAt: Float = Float.NEGATIVE_INFINITY,
    /** Absolute run time until which the enemy cannot act at all (stun, stop); read through canAct. */
    var disabledUntil: Float = 0f,
    /** Absolute run time until which the enemy may move but not attack; read through canAttack. */
    var silencedUntil: Float = 0f,
    /** Type-specific behavior step (approach, telegraph, strike, recovery, phased, primed); 0 is the default. */
    var mode: Int = 0,
    /** Absolute run time at which the current [mode] ends. */
    var modeUntil: Float = 0f,
    /** Aim or shield direction in radians: the Lancer's lane, the Sniper's sight line, the Reflector's shield. */
    var facing: Float = 0f,
    /** True while the Reflector's shield faces the Core; front hits meet its reduced frame. */
    var guarded: Boolean = false,
    /** Id of the Aegis tethering this enemy, or -1; the link holds while [linkedUntil] is ahead. */
    var linkedBy: Int = -1,
    var linkedUntil: Float = 0f,
    /** Absolute run time until which the Herald's shield holds; only impacts reach it meanwhile. */
    var shieldUntil: Float = 0f,
    /** Absolute run time at which the type's next ability (lance, shot, phase, brood, shield, reflection) is ready. */
    var abilityAt: Float = 0f,
    /** Elemental status cells; see [StatusCell]. Copy-on-write so reduction siblings stay isolated. */
    val status: CopyOnWriteFloatArray,
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
        killClausesEligible: Boolean = false,
        hitQualificationCooldown: Float = 0f,
        aspect: Int = -1,
        aspectImmune: Boolean = false,
        phase: Int = 0,
        shiftUntil: Float = 0f,
        escortsPending: Int = 0,
        lastDamagedAt: Float = Float.NEGATIVE_INFINITY,
        status: FloatArray = FloatArray(StatusCell.COUNT),
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
        killClausesEligible = killClausesEligible,
        hitQualificationCooldown = hitQualificationCooldown,
        aspect = aspect,
        aspectImmune = aspectImmune,
        phase = phase,
        shiftUntil = shiftUntil,
        escortsPending = escortsPending,
        lastDamagedAt = lastDamagedAt,
        status = CopyOnWriteFloatArray(status),
    )

    /** The run's final boss only: hit cap, encounter lookup and victory. */
    val isBoss: Boolean get() = type == EnemyType.ARCHITECT
    val isElite: Boolean get() = type == EnemyType.ELITE
    /** The scheduled Herald: Elite victim factor, freeze and stop immunity, leash exemption, outside the ordinary cap. */
    val isMiniBoss: Boolean get() = type == EnemyType.HERALD
    val alive: Boolean get() = !dead && hp > 0f

    /** Copies mutable scalar state while sharing the status cells until their first changed write. */
    fun isolatedCopy(): Enemy = copy(status = status.fork())
}

/**
 * Layout of [Enemy.status]. Per-element blocks hold five entries in [kinetickk.ball.content.api.StatusElement]
 * order. Timestamps are absolute run seconds, so cells are written on events rather than every step.
 */
internal object StatusCell {
    const val STACKS = 0
    const val DECAY_AT = 5
    const val GENERATION = 10
    const val REARM_AT = 15
    const val FROZEN_UNTIL = 20
    const val FREEZE_IMMUNE_UNTIL = 21
    const val EMBRITTLED_UNTIL = 22
    const val BRITTLE = 23
    const val REACTION_READY_AT = 24
    const val LAST_REACTION = 25
    const val LAST_REACTION_AT = 26
    const val ARC_READY_AT = 27
    const val CINDER_BANK = 28
    const val MAX_HP_PROC_READY_AT = 29
    const val DETONATE_READY_AT = 30
    const val MARKED_UNTIL = 31
    /** 1 + [kinetickk.ball.gameplay.nucleus.simulation.HitSource] ordinal of the last direct hit. */
    const val LAST_SOURCE = 32
    /** Ten entries in [kinetickk.ball.content.api.ReactionId] order: when each pair may react again. */
    const val REACTION_PAIR_READY_AT = 33
    /** Half-stack remainder of Cryo landing at half strength during Freeze immunity. */
    const val CRYO_IMMUNE_CARRY = 43
    /** Fractional remainder of stacks landing on a resisted aspect element. */
    const val RESIST_CARRY = 44
    /** Fractional remainder of stacks landing on the weakness element. */
    const val WEAK_CARRY = 45
    /** When the next immunity refusal may show its cue. */
    const val IMMUNE_FX_READY_AT = 46
    const val COUNT = 47
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
    /** Status element ordinal applied by a relic or item projectile on hit, or -1. */
    val statusElement: Int = -1,
    /** Hostile shots are bolts; a Minelayer's mine sits still, arms and detonates near the Core. */
    val kind: ProjectileKind = ProjectileKind.BOLT,
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
        statusElement = statusElement,
        kind = kind,
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

/**
 * How a weapon delivers hits: [DISCRETE] activations, [CONTINUOUS] per-frame damage (qualifies on a cooldown)
 * or [CONTACT] strikes gated by per-enemy re-hit timers instead of an activation cadence.
 */
internal enum class WeaponHitCadence { DISCRETE, CONTINUOUS, CONTACT }

internal data class DamageResult(
    val amount: Float,
    val critical: Boolean,
    /** The amount before the receiver's affinity and kinetic multipliers and boss gating. */
    val unmitigated: Float = amount,
)

/** A weapon hit repeated by Echo Chamber; echoes never echo. */
internal data class EchoHit(
    val enemyId: Int,
    var delay: Float,
    val damage: Float,
    val appliesDominant: Boolean,
)
