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
    /** Absolute run time until which a player knockback counts as a throw (enemy-enemy slams). */
    var thrownUntil: Float = 0f,
    /** Absolute run time before which the enemy cannot slam again. */
    var slamReadyAt: Float = 0f,
    /** Spawned with an Armored Vanguard plate (Rebirth R8): weapon hits deal less until an impact cracks it. */
    var armored: Boolean = false,
    /** The plate cracked under an impact; weapons deal full damage from then on. */
    var armorCracked: Boolean = false,
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
 * Layout of [Enemy.status]. Per-element blocks hold [ELEMENTS] entries in
 * [kinetickk.ball.content.api.StatusElement] order and the pair block holds [PAIRS] entries in
 * [kinetickk.ball.content.api.ReactionId] order, so the layout follows the element count. Timestamps
 * are absolute run seconds, so cells are written on events rather than every step.
 */
internal object StatusCell {
    /** Status elements; equals `StatusElement.entries.size` (asserted by the status packing tests). */
    const val ELEMENTS = 7

    /** Unordered element pairs, one reaction each; equals `ReactionId.entries.size`. */
    const val PAIRS = ELEMENTS * (ELEMENTS - 1) / 2

    const val STACKS = 0
    const val DECAY_AT = STACKS + ELEMENTS
    const val GENERATION = DECAY_AT + ELEMENTS
    const val REARM_AT = GENERATION + ELEMENTS
    const val FROZEN_UNTIL = REARM_AT + ELEMENTS
    const val FREEZE_IMMUNE_UNTIL = FROZEN_UNTIL + 1
    const val EMBRITTLED_UNTIL = FREEZE_IMMUNE_UNTIL + 1
    const val BRITTLE = EMBRITTLED_UNTIL + 1
    const val REACTION_READY_AT = BRITTLE + 1
    const val LAST_REACTION = REACTION_READY_AT + 1
    const val LAST_REACTION_AT = LAST_REACTION + 1
    const val ARC_READY_AT = LAST_REACTION_AT + 1
    const val CINDER_BANK = ARC_READY_AT + 1
    const val MAX_HP_PROC_READY_AT = CINDER_BANK + 1
    const val DETONATE_READY_AT = MAX_HP_PROC_READY_AT + 1
    const val MARKED_UNTIL = DETONATE_READY_AT + 1
    /** 1 + [kinetickk.ball.gameplay.nucleus.simulation.HitSource] ordinal of the last direct hit. */
    const val LAST_SOURCE = MARKED_UNTIL + 1
    /** [PAIRS] entries in [kinetickk.ball.content.api.ReactionId] order: when each pair may react again. */
    const val REACTION_PAIR_READY_AT = LAST_SOURCE + 1
    /** Half-stack remainder of Cryo landing at half strength during Freeze immunity. */
    const val CRYO_IMMUNE_CARRY = REACTION_PAIR_READY_AT + PAIRS
    /** Fractional remainder of stacks landing on a resisted aspect element. */
    const val RESIST_CARRY = CRYO_IMMUNE_CARRY + 1
    /** Fractional remainder of stacks landing on the weakness element. */
    const val WEAK_CARRY = RESIST_CARRY + 1
    /** When the next immunity refusal may show its cue. */
    const val IMMUNE_FX_READY_AT = WEAK_CARRY + 1
    /** Damage the enemy remembered while Hollow (Void); an Unmake releases it. */
    const val HOLLOW_MEMORY = IMMUNE_FX_READY_AT + 1
    /** When a pending Unmake strikes (0 when none is pending). */
    const val UNMAKE_AT = HOLLOW_MEMORY + 1
    /** Damage of the pending Unmake. */
    const val UNMAKE_DAMAGE = UNMAKE_AT + 1
    /** When the Blackfire burn ends. */
    const val BLACKFIRE_UNTIL = UNMAKE_DAMAGE + 1
    /** Blackfire damage per second while it burns. */
    const val BLACKFIRE_DPS = BLACKFIRE_UNTIL + 1
    /** The enemy's one mark: [MarkKind] ordinal + 1, 0 for none. A newer mark replaces an older one. */
    const val MARK = BLACKFIRE_DPS + 1
    /** When the mark lapses. */
    const val MARK_UNTIL = MARK + 1
    /** 1 when a skill cast wrote the mark (Decay Spores' seeds), 0 for a reaction's or an item's mark. */
    const val MARK_CAST = MARK_UNTIL + 1
    const val COUNT = MARK_CAST + 1
}

/**
 * Marks a reaction or skill leaves on an enemy, cashed by a later action: [SPALL] by the next direct hit,
 * [CRACKED] by the next Brake release, [SPORED] by death, [BOOMED] by a slam; [SUPPRESSED] dampens what the
 * enemy receives. Ordinals are stored and mirrored by [kinetickk.ball.gameplay.nucleus.render.EnemyMarkVisual].
 */
internal enum class MarkKind { SPALL, CRACKED, SPORED, BOOMED, SUPPRESSED }

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
    /** Which weapon activation or persistent source a player shot belongs to (see [WeaponRuntime]). */
    val procKey: Int = WeaponRuntime.PROC_ALWAYS,
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

    /** A spent shot (its life is over, or a reaction spent it) neither hits nor is caught; it awaits removal. */
    val spent: Boolean get() = life <= 0f

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
        procKey = procKey,
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
    /** Latched by the magnet: homes on the Core until collected and is never lost. */
    var attracted: Boolean = false,
)

/**
 * A Flux Wake trail sample: it burns for [life] seconds of [age] and deals [scale] times the trail's
 * damage (a signature trail laid while turning lasts longer and burns harder).
 */
internal data class TrailPoint(
    var x: Float,
    var y: Float,
    var age: Float = 0f,
    val life: Float = TRAIL_LIFE_SECONDS,
    val scale: Float = 1f,
)

/** A Flux Wake trail point burns this long; render fades it over [TRAIL_FADE_SECONDS] more. */
internal const val TRAIL_LIFE_SECONDS: Float = 1.8f
internal const val TRAIL_FADE_SECONDS: Float = 0.45f

internal data class Totem(var x: Float, var y: Float, var pulse: Float = 0f)

internal data class WeaponNode(
    val type: WeaponNodeType,
    var x: Float,
    var y: Float,
    var life: Float,
    val maxLife: Float,
    var radius: Float,
    /** The activation that dropped it; only its first explosion hit fires on-hit effects. */
    val procKey: Int = WeaponRuntime.PROC_ALWAYS,
    /** Damage multiplier of a special mine (a parry mine detonates at double strength). */
    val scale: Float = 1f,
)

internal data class WeaponOrbital(
    val index: Int,
    var x: Float,
    var y: Float,
    val radius: Float,
    /** Position at the start of the last fixed step (render interpolation only). */
    var previousX: Float = x,
    var previousY: Float = y,
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
