// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ApexDefinition
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The character's Apex (combat-final §5). It unlocks, ready, the first time the run reaches its level cap; its
 * buffered request casts after the skill slots; Data credited at the cap recharges it through its own refund bank,
 * which never brings it below the definition's minimum cooldown after a cast. Every Apex obeys one boss contract:
 * it never amplifies a mechanic against boss-class enemies, it hits them only through [apexStrike] (at most the
 * definition's strikes per activation, each through the Architect's gate) and it erases hostile bolts only within
 * its bolt budget. The per-character forms live in CharacterSystem.kt. No gameplay or spawn randomness is drawn.
 */

/** The run character's Apex. */
internal val MutableGameState.apexDefinition: ApexDefinition get() = content.coreShape(coreShape).apex

/** True once the run reached its level cap. */
internal val MutableGameState.apexUnlocked: Boolean get() = skillRuntime.apex(ApexCell.UNLOCKED).toRawBits() != 0

/** True while an activation runs. */
internal fun MutableGameState.apexActive(): Boolean = skillRuntime.apex(ApexCell.ACTIVE_UNTIL) > elapsed

/** True when a request would cast now. */
internal fun MutableGameState.apexReady(): Boolean = apexUnlocked && elapsed >= skillRuntime.apex(ApexCell.READY_AT)

/** The running Apex amplifies its mechanic on [enemy]: never on boss-class enemies (combat-final §5.2 rule 1). */
internal fun MutableGameState.apexAmplifies(enemy: Enemy): Boolean = !enemy.isBossClass && apexActive()

/** Value [index] of the run character's Apex (the per-shape order of `defaultApexDefinition`). */
internal fun MutableGameState.apexValue(index: Int): Float = apexDefinition.values[index]

/** Boss strikes left in the current activation. */
internal val MutableGameState.apexStrikesLeft: Int get() = skillRuntime.apex(ApexCell.STRIKES_LEFT).toInt()

/**
 * The run reached its level cap: the Apex unlocks ready, with its banner and sound. Later calls change nothing.
 */
internal fun MutableGameState.unlockApex() {
    if (apexUnlocked) return
    val runtime = skillRuntime
    runtime.setApex(ApexCell.UNLOCKED, 1f)
    runtime.setApex(ApexCell.READY_AT, elapsed)
    message = APEX_ONLINE_MESSAGE
    messageTime = APEX_MESSAGE_SECONDS
    emitSound(GameplayAudioCue.APEX)
}

/**
 * [wholeData] Data was credited at the level cap: each point takes [APEX_SECONDS_PER_DATA] off the Apex cooldown
 * through [refundApex]. Nothing happens before the unlock or while the Apex is ready.
 */
internal fun MutableGameState.chargeApexWithData(wholeData: Int) {
    if (wholeData <= 0) return
    refundApex(wholeData * APEX_SECONDS_PER_DATA)
}

/**
 * The Apex refund bank: takes up to [seconds] off the running cooldown, never bringing it below the definition's
 * minimum cooldown after the last cast (so every refund of a cycle together stays within cooldown − minimum).
 * Returns the seconds refunded; a locked, ready or floored Apex takes nothing.
 */
internal fun MutableGameState.refundApex(seconds: Float): Float {
    if (!(seconds > 0f) || !apexUnlocked) return 0f
    val runtime = skillRuntime
    val readyAt = runtime.apex(ApexCell.READY_AT)
    if (readyAt <= elapsed) return 0f
    val floor = runtime.apex(ApexCell.LAST_CAST) + apexDefinition.minimumCooldownSeconds
    val refund = min(seconds, readyAt - max(elapsed, floor))
    if (!(refund > 0f)) return 0f
    runtime.setApex(ApexCell.READY_AT, readyAt - refund)
    runtime.setApex(ApexCell.REFUNDED, runtime.apex(ApexCell.REFUNDED) + refund)
    return refund
}

/**
 * Ends a finished activation, then casts a buffered request when the Apex is ready. Called by the skill step
 * after the slots; allocation-free.
 */
internal fun MutableGameState.updateApex(delta: Float) {
    if (!apexUnlocked) {
        apexBufferTime = 0f
        return
    }
    val runtime = skillRuntime
    val until = runtime.apex(ApexCell.ACTIVE_UNTIL)
    if (until > 0f && elapsed >= until) {
        runtime.setApex(ApexCell.ACTIVE_UNTIL, 0f)
        endApexForm()
    }
    if (apexBufferTime.toRawBits() == 0) return
    apexBufferTime = max(0f, apexBufferTime - delta)
    if (apexBufferTime <= 0f || !apexReady()) return
    apexBufferTime = 0f
    castApex()
}

/**
 * Casts the Apex: it runs for its duration and recharges over its cooldown (cut by the build, never below the
 * minimum), with a fresh strike and bolt budget; the character's form begins and the cue and sound follow.
 */
private fun MutableGameState.castApex() {
    val runtime = skillRuntime
    val definition = apexDefinition
    val cut = build.apexCooldownCut.coerceIn(0f, MAX_APEX_COOLDOWN_CUT)
    runtime.setApex(ApexCell.ACTIVE_UNTIL, elapsed + definition.durationSeconds)
    runtime.setApex(ApexCell.READY_AT, elapsed + max(definition.minimumCooldownSeconds, definition.cooldownSeconds * (1f - cut)))
    runtime.setApex(ApexCell.LAST_CAST, elapsed)
    runtime.setApex(ApexCell.REFUNDED, 0f)
    runtime.setApex(ApexCell.STRIKES_LEFT, definition.bossStrikes.toFloat())
    runtime.setApex(ApexCell.BOLTS_LEFT, definition.boltBudget.toFloat())
    runtime.setApex(ApexCell.CAST_COUNT, runtime.apex(ApexCell.CAST_COUNT) + 1f)
    if (listens(kinetickk.ball.content.api.EffectTrigger.APEX)) {
        fireClauses(kinetickk.ball.content.api.EffectTrigger.APEX, element = build.dominant)
    }
    beginApexForm()
    emitVisualFx(VisualFxCue.ApexActivated(coreShape, coreX, coreY))
    emitSound(GameplayAudioCue.APEX)
}

/**
 * The boss channel (combat-final §5.2 rule 2): a strike on boss-class [enemy] deals at least
 * [APEX_STRIKE_CAP_SHARE] of its hit cap through the character damage path (so the Architect's gate still caps it
 * and holds every phase gate; a Herald, which has no gate, is held to the same cap) and spends one strike when
 * something landed. A shifting enemy, an ordinary one or an empty strike budget takes nothing and spends nothing.
 * Returns the integrity removed.
 */
internal fun MutableGameState.apexStrike(enemy: Enemy, amount: Float): Float {
    if (!enemy.isBossClass || !enemy.alive || isShifting(enemy)) return 0f
    val runtime = skillRuntime
    val left = runtime.apex(ApexCell.STRIKES_LEFT)
    if (left < 1f) return 0f
    val integrity = enemy.hp
    val cap = architectHitCap(enemy)
    val requested = max(amount, APEX_STRIKE_CAP_SHARE * cap)
    val result = if (enemy.isBoss) {
        dealCharacterDamage(enemy, requested, edge = false)
    } else {
        val scaled = min(requested * matrix.damageMultiplier * enemy.weaponTaken, cap)
        damageEnemy(enemy, scaled, unmitigated = scaled).also { recordAbilityDamage(integrity, it) }
    }
    if (result.amount <= 0f) return 0f
    runtime.setApex(ApexCell.STRIKES_LEFT, left - 1f)
    runtime.apexBossDamage += min(result.amount, integrity.coerceAtLeast(0f)).toDouble()
    shockwave(enemy.x, enemy.y, APEX_STRIKE_RING_SECONDS, APEX_STRIKE_RING_RADIUS, FxColorCode.APEX_GOLD)
    return result.amount
}

/** Spends hostile bolt [shot] from the Apex's bolt budget (see [eraseHostileShot]); true when it was spent. */
internal fun MutableGameState.eraseApexShot(shot: Projectile, x: Float, y: Float): Boolean =
    eraseHostileShot(shot, EraseCause.APEX, x, y)

/**
 * Where an Apex shot from ([x], [y]) flies: a live, non-shifting boss-class enemy within [range] while strikes
 * remain, else the nearest ordinary enemy within [range]; null when neither exists.
 */
internal fun MutableGameState.apexShotTarget(x: Float, y: Float, range: Float): Enemy? {
    if (skillRuntime.apex(ApexCell.STRIKES_LEFT) >= 1f) {
        val rangeSquared = range * range
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (enemy.isBossClass && enemy.alive && !isShifting(enemy) && distanceSquared(x, y, enemy.x, enemy.y) <= rangeSquared) {
                return enemy
            }
        }
    }
    return nearestOrdinaryEnemy(x, y, range)
}

/** The nearest live enemy that is not boss-class within [range] of ([x], [y]), other than [excludedId]. */
internal fun MutableGameState.nearestOrdinaryEnemy(x: Float, y: Float, range: Float, excludedId: Int = -1): Enemy? {
    var nearest: Enemy? = null
    var nearestDistanceSquared = range * range
    for (index in enemies.indices) {
        val enemy = enemies[index]
        if (enemy.id == excludedId || enemy.isBossClass || !enemy.alive || isShifting(enemy)) continue
        val distance = distanceSquared(x, y, enemy.x, enemy.y)
        if (distance <= nearestDistanceSquared && (nearest == null || distance < nearestDistanceSquared)) {
            nearest = enemy
            nearestDistanceSquared = distance
        }
    }
    return nearest
}

/** Fires an Apex shot from ([x], [y]) at [target]; an [element] lands one stack on an ordinary enemy it hits. */
internal fun MutableGameState.fireApexShot(x: Float, y: Float, target: Enemy, damage: Float, element: StatusElement? = null) {
    val angle = atan2(target.y - y, target.x - x)
    fireApexShotAlong(x, y, cos(angle), sin(angle), damage, element)
}

/** Fires an Apex shot from ([x], [y]) along the unit direction ([directionX], [directionY]). */
internal fun MutableGameState.fireApexShotAlong(
    x: Float,
    y: Float,
    directionX: Float,
    directionY: Float,
    damage: Float,
    element: StatusElement? = null,
) {
    addProjectile(Projectile(
        x = x,
        y = y,
        vx = directionX * APEX_SHOT_SPEED,
        vy = directionY * APEX_SHOT_SPEED,
        radius = APEX_SHOT_RADIUS,
        life = APEX_SHOT_LIFE,
        hostile = false,
        damage = damage,
        pierce = 0,
        colorIndex = FxColorCode.APEX_GOLD,
        sourceWeapon = null,
        statusElement = element?.ordinal ?: -1,
        procKey = WeaponRuntime.PROC_APEX,
    ))
}

/** An Apex shot meets boss-class [enemy] with no strike left: it passes through without spending itself. */
internal fun MutableGameState.apexShotPassesThrough(enemy: Enemy): Boolean =
    enemy.isBossClass && skillRuntime.apex(ApexCell.STRIKES_LEFT) < 1f

/**
 * An Apex shot of [damage] hit [enemy]: a strike on boss-class enemies, character damage (its number is final, so
 * no Edge applies again) and one stack of [statusElement] (ordinal, or -1) on the rest.
 */
internal fun MutableGameState.apexShotHit(enemy: Enemy, damage: Float, statusElement: Int) {
    if (enemy.isBossClass) {
        apexStrike(enemy, damage)
        return
    }
    dealCharacterDamage(enemy, damage, edge = false)
    if (statusElement >= 0 && enemy.alive) applyStatus(enemy, StatusElement.entries[statusElement], 1, 0)
}

/** Banner shown when the Apex unlocks. */
internal const val APEX_ONLINE_MESSAGE: String = "APEX ONLINE"
private const val APEX_MESSAGE_SECONDS: Float = 2.5f
/** Cooldown seconds each whole Data credited at the level cap takes off the Apex (combat-final §5.1). */
internal const val APEX_SECONDS_PER_DATA: Float = 0.03f
/** The most the build may cut the Apex's base cooldown (combat-final §6.4 ApexCooldown). */
internal const val MAX_APEX_COOLDOWN_CUT: Float = 0.3f
/** A strike deals at least this share of the boss's hit cap. */
internal const val APEX_STRIKE_CAP_SHARE: Float = 0.75f
private const val APEX_STRIKE_RING_SECONDS: Float = 0.3f
private const val APEX_STRIKE_RING_RADIUS: Float = 50f
internal const val APEX_SHOT_SPEED: Float = 720f
private const val APEX_SHOT_RADIUS: Float = 5f
private const val APEX_SHOT_LIFE: Float = 2f
/** How far an Apex looks for the target of a shot it returns (refraction, parry, held shot). */
internal const val APEX_SHOT_RANGE: Float = 800f
