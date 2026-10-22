// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

import kinetickk.ball.gameplay.nucleus.render.WeaponEntityKind

/**
 * A weapon-owned world object: a Gyre disc, a sentry, a lens or its well, a rift, an Aegis shard, a
 * missile, a toll or pulse ring, a tether or a solar beam. Positions are world positions; the previous
 * ones are the positions at the start of the last fixed step (render interpolation and swept hits).
 * [endX]/[endY] is the second point of a segment (rift, beam tip, tether anchor). [procKey] says which
 * activation or persistent source its hits belong to (see [WeaponRuntime]).
 */
internal class WeaponEntity(
    val kind: WeaponEntityKind,
    var x: Float,
    var y: Float,
    var radius: Float,
    var life: Float,
    val maxLife: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var damage: Float = 0f,
    var endX: Float = x,
    var endY: Float = y,
    var angle: Float = 0f,
    /** Length of a beam or the reach of a disc's outward pass. */
    var extent: Float = 0f,
    /** Kind-specific countdown: a sentry's next shot, a rift's fuse, a well's next status tick. */
    var timer: Float = 0f,
    /** Kind-specific step: a disc returning, a shard launched, a ring's variant, a tether's slot. */
    var phase: Int = 0,
    var procKey: Int = WeaponRuntime.PROC_ALWAYS,
    /** Id of the enemy a missile, shard or tether holds, or -1. */
    var targetId: Int = -1,
) {
    var previousX: Float = x
    var previousY: Float = y
    var previousEndX: Float = endX
    var previousEndY: Float = endY
    var previousAngle: Float = angle
    private val hitIds = IntArray(MAX_ENTITY_HITS)
    private var hitCount = 0

    /** Starts a fixed step: the current positions become the previous ones. */
    fun capturePrevious() {
        previousX = x
        previousY = y
        previousEndX = endX
        previousEndY = endY
        previousAngle = angle
    }

    /** A teleport or a fresh placement: no travelled path to interpolate or sweep. */
    fun settle() {
        capturePrevious()
    }

    fun hasHit(enemyId: Int): Boolean {
        for (index in 0 until hitCount) if (hitIds[index] == enemyId) return true
        return false
    }

    /** Remembers [enemyId] as already hit on this pass; the oldest entry gives way when full. */
    fun recordHit(enemyId: Int) {
        if (hasHit(enemyId)) return
        if (hitCount == MAX_ENTITY_HITS) {
            for (index in 1 until MAX_ENTITY_HITS) hitIds[index - 1] = hitIds[index]
            hitCount--
        }
        hitIds[hitCount++] = enemyId
    }

    fun clearHits() {
        hitCount = 0
    }

    fun shift(shiftX: Float, shiftY: Float) {
        x -= shiftX
        y -= shiftY
        previousX -= shiftX
        previousY -= shiftY
        endX -= shiftX
        endY -= shiftY
        previousEndX -= shiftX
        previousEndY -= shiftY
    }

    fun copy(): WeaponEntity = WeaponEntity(
        kind = kind, x = x, y = y, radius = radius, life = life, maxLife = maxLife, vx = vx, vy = vy,
        damage = damage, endX = endX, endY = endY, angle = angle, extent = extent, timer = timer,
        phase = phase, procKey = procKey, targetId = targetId,
    ).also { copy ->
        copy.previousX = previousX
        copy.previousY = previousY
        copy.previousEndX = previousEndX
        copy.previousEndY = previousEndY
        copy.previousAngle = previousAngle
        hitIds.copyInto(copy.hitIds, 0, 0, hitCount)
        copy.hitCount = hitCount
    }

    override fun toString(): String = buildString {
        append(kind).append('(')
        append(x).append(',').append(y).append(',').append(previousX).append(',').append(previousY).append(',')
        append(radius).append(',').append(life).append(',').append(maxLife).append(',').append(vx).append(',')
        append(vy).append(',').append(damage).append(',').append(endX).append(',').append(endY).append(',')
        append(previousEndX).append(',').append(previousEndY).append(',').append(angle).append(',')
        append(previousAngle).append(',').append(extent).append(',').append(timer).append(',').append(phase).append(',')
        append(procKey).append(',').append(targetId).append(";hits=")
        for (index in 0 until hitCount) append(hitIds[index]).append(' ')
        append(')')
    }

    companion object {
        /** Enemies one pass remembers; a disc meets each enemy once per pass. */
        const val MAX_ENTITY_HITS: Int = 24
    }
}

/**
 * Owned state of the equipped weapon beyond its clocks: world entities, the proc gates, element and
 * infusion cadences and the inputs the scaling stats read. A full reduction forks it (entities are
 * copied, at most [MAX_WEAPON_ENTITIES]); a scalar-input reduction shares it, since no scalar input
 * writes it.
 *
 * Proc rule: a discrete activation takes the next [activationSerial]; only its first enemy hit qualifies
 * ([procMask] bit k marks activation `serial − k` as spent). Persistent sources (continuous, contact,
 * sentry and orbital emitters) qualify at most once per [SOURCE_INTERVAL] per source slot.
 */
internal class WeaponRuntime private constructor(
    val entities: MutableList<WeaponEntity>,
    private val sourceReadyAt: FloatArray,
    private val shedBuckets: FloatArray,
) {
    constructor() : this(ArrayList(), FloatArray(SOURCE_SLOTS), FloatArray(SHED_BUCKETS))

    var activationSerial: Int = 0
    var procMask: Long = 0L
    /** Hits counted toward a weapon's every-Nth element (Ion Swarm Charge, Rift Blades Hollow). */
    var elementHits: Int = 0
    /** Qualifying hits counted toward a kinetic weapon's adopted element (every third). */
    var infusionHits: Int = 0
    /** Qualifying weapon hits (each fired WEAPON_HIT once); telemetry for the proc bound. */
    var qualifiedHits: Int = 0
    var lastDashAt: Float = NEVER
    var seenDashSequence: Int = 0
    var previousSpeed: Float = 0f
    /** Velocity heading of the last step (Flux Wake's turning trail). */
    var previousHeading: Float = 0f
    var shedBucketIndex: Int = 0
    var shedBucketClock: Float = 0f
    /** Seconds the Brake has been held without a break (Glacier Bell's release toll). */
    var brakeHeld: Float = 0f
    /** A weapon's passive clock (Scatter volley, Hollow passive rift, Glacier pulse). */
    var passiveClock: Float = 0f
    /** When a delayed follow-up fires (Glacier's second toll, the Drum's double beat), or [NEVER]. */
    var followUpAt: Float = NEVER
    var followUpX: Float = 0f
    var followUpY: Float = 0f
    var readyAt: Float = 0f
    var signatureReadyAt: Float = 0f
    var tetherTargetId: Int = -1
    /** The tether's damage multiplier: 1 at the base, rising while a target is held. */
    var tetherRamp: Float = 1f
    var secondTetherId: Int = -1
    var secondTetherRamp: Float = 1f
    var sweepAngle: Float = 0f
    var shieldWasUp: Boolean = false
    var shieldRestoreReadyAt: Float = 0f
    /** The Drum waits for the beat once its cooldown is ready. */
    var drumArmed: Boolean = false
    /** Accumulated orbit phase of the weapon orbitals: a change of angular speed bends the orbit, never jumps it. */
    var orbitalPhase: Float = 0f
    /**
     * True while an entity pass walks [entities] by index: an entity created then joins the end of the list and
     * one that makes room for it finishes instead of leaving, so no index shifts inside the pass.
     */
    var entityPassActive: Boolean = false

    /** Claims the next activation serial (bounded window, see [procMask]). */
    fun beginActivation(): Int {
        activationSerial = (activationSerial + 1) and ACTIVATION_MASK
        procMask = procMask shl 1
        return activationSerial
    }

    /** True when [procKey]'s source may fire on-hit effects now; nothing is spent. */
    fun canQualify(procKey: Int, elapsed: Float): Boolean = when {
        procKey == PROC_ALWAYS -> true
        procKey == PROC_NEVER || procKey == PROC_SKILL -> false
        procKey >= 0 -> {
            val age = (activationSerial - procKey) and ACTIVATION_MASK
            age < ACTIVATION_WINDOW && procMask and (1L shl age) == 0L
        }
        else -> elapsed >= sourceReadyAt[sourceSlot(procKey)]
    }

    /** Spends [procKey]'s qualification after a hit that landed. */
    fun spendQualification(procKey: Int, elapsed: Float) {
        when {
            procKey == PROC_ALWAYS || procKey == PROC_NEVER || procKey == PROC_SKILL -> Unit
            procKey >= 0 -> {
                val age = (activationSerial - procKey) and ACTIVATION_MASK
                if (age < ACTIVATION_WINDOW) procMask = procMask or (1L shl age)
            }
            else -> sourceReadyAt[sourceSlot(procKey)] = elapsed + SOURCE_INTERVAL
        }
        qualifiedHits++
    }

    /** Adds a step's Brake speed loss and returns the speed shed in about the last half second. */
    fun recordShed(shed: Float, delta: Float): Float {
        shedBucketClock += delta
        while (shedBucketClock >= SHED_BUCKET_SECONDS) {
            shedBucketClock -= SHED_BUCKET_SECONDS
            shedBucketIndex = (shedBucketIndex + 1) % SHED_BUCKETS
            shedBuckets[shedBucketIndex] = 0f
        }
        shedBuckets[shedBucketIndex] += shed
        return recentShed()
    }

    fun recentShed(): Float {
        var total = 0f
        for (value in shedBuckets) total += value
        return total
    }

    /** Clears everything a weapon change or a new run must not carry over. */
    fun reset() {
        entities.clear()
        sourceReadyAt.fill(0f)
        procMask = 0L
        elementHits = 0
        infusionHits = 0
        passiveClock = 0f
        followUpAt = NEVER
        readyAt = 0f
        signatureReadyAt = 0f
        tetherTargetId = -1
        tetherRamp = 1f
        secondTetherId = -1
        secondTetherRamp = 1f
        sweepAngle = 0f
        // A shield that broke under another weapon is no Aegis break.
        shieldWasUp = false
        shieldRestoreReadyAt = 0f
        drumArmed = false
        orbitalPhase = 0f
    }

    fun rebase(shiftX: Float, shiftY: Float) {
        for (index in entities.indices) entities[index].shift(shiftX, shiftY)
        followUpX -= shiftX
        followUpY -= shiftY
    }

    fun fork(): WeaponRuntime = WeaponRuntime(
        entities = entities.mapTo(ArrayList(entities.size), WeaponEntity::copy),
        sourceReadyAt = sourceReadyAt.copyOf(),
        shedBuckets = shedBuckets.copyOf(),
    ).also { copy ->
        copy.activationSerial = activationSerial
        copy.procMask = procMask
        copy.elementHits = elementHits
        copy.infusionHits = infusionHits
        copy.qualifiedHits = qualifiedHits
        copy.lastDashAt = lastDashAt
        copy.seenDashSequence = seenDashSequence
        copy.previousSpeed = previousSpeed
        copy.previousHeading = previousHeading
        copy.shedBucketIndex = shedBucketIndex
        copy.shedBucketClock = shedBucketClock
        copy.brakeHeld = brakeHeld
        copy.passiveClock = passiveClock
        copy.followUpAt = followUpAt
        copy.followUpX = followUpX
        copy.followUpY = followUpY
        copy.readyAt = readyAt
        copy.signatureReadyAt = signatureReadyAt
        copy.tetherTargetId = tetherTargetId
        copy.tetherRamp = tetherRamp
        copy.secondTetherId = secondTetherId
        copy.secondTetherRamp = secondTetherRamp
        copy.sweepAngle = sweepAngle
        copy.shieldWasUp = shieldWasUp
        copy.shieldRestoreReadyAt = shieldRestoreReadyAt
        copy.drumArmed = drumArmed
        copy.orbitalPhase = orbitalPhase
        copy.entityPassActive = entityPassActive
    }

    /** Deterministic text of every field (the reduction isolation fingerprint reads it). */
    override fun toString(): String = buildString {
        append("WeaponRuntime(")
        append(activationSerial).append(',').append(procMask).append(',').append(elementHits).append(',')
        append(infusionHits).append(',').append(qualifiedHits).append(',').append(lastDashAt).append(',')
        append(seenDashSequence).append(',').append(previousSpeed).append(',').append(previousHeading).append(',')
        append(shedBucketIndex).append(',')
        append(shedBucketClock).append(',').append(brakeHeld).append(',').append(passiveClock).append(',')
        append(followUpAt).append(',').append(followUpX).append(',').append(followUpY).append(',')
        append(readyAt).append(',').append(signatureReadyAt).append(',').append(tetherTargetId).append(',')
        append(tetherRamp).append(',').append(secondTetherId).append(',').append(secondTetherRamp).append(',')
        append(sweepAngle).append(',').append(shieldWasUp).append(',').append(shieldRestoreReadyAt).append(',')
        append(drumArmed).append(',').append(orbitalPhase).append(',').append(entityPassActive)
        append(";sources=").append(sourceReadyAt.joinToString(","))
        append(";shed=").append(shedBuckets.joinToString(","))
        append(";entities=").append(entities.joinToString("|"))
        append(')')
    }

    companion object {
        /** Hits that always fire on-hit effects (relic shots and test fixtures). */
        const val PROC_ALWAYS: Int = Int.MIN_VALUE
        /** Hits that never do (derived bursts of an already counted hit). */
        const val PROC_NEVER: Int = Int.MIN_VALUE + 1
        /** Shots a skill fired (Null Shroud's returned bolts): skill damage, never an on-hit effect. */
        const val PROC_SKILL: Int = Int.MIN_VALUE + 3
        const val SOURCE_SLOTS: Int = 4
        const val SOURCE_INTERVAL: Float = 0.25f
        const val ACTIVATION_WINDOW: Int = 63
        const val ACTIVATION_MASK: Int = 0x3FFF_FFFF
        const val SHED_BUCKETS: Int = 5
        const val SHED_BUCKET_SECONDS: Float = 0.1f
        const val NEVER: Float = -1_000_000f
        const val MAX_WEAPON_ENTITIES: Int = 32

        /** The proc key of persistent source [slot] (0 until [SOURCE_SLOTS]). */
        fun sourceKey(slot: Int): Int = -1 - slot

        private fun sourceSlot(procKey: Int): Int = (-1 - procKey).coerceIn(0, SOURCE_SLOTS - 1)
    }
}
