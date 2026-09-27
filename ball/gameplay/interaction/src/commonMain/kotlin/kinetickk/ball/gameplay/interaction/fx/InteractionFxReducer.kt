// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.foundation.collections.mapToImmutableList
import kinetickk.foundation.random.CloneableXorWowRandom
import kinetickk.ball.gameplay.nucleus.model.clamp
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.gameplay.nucleus.model.formatDamageNumber
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

internal object InteractionFxLimits {
    const val MAX_PARTICLES = 700
    const val MAX_MOTION_ECHOES = 36
    const val MAX_SHOCKWAVES = 48
    const val MAX_DAMAGE_NUMBERS = 140
    const val MAX_WEAPON_ARCS = 128

    /** Damage numbers pop, settle and drift away from the Core over 600 ms (`KkTime.Drift`). */
    const val DAMAGE_NUMBER_LIFE_SECONDS = 0.6f

    /** The first particles of every burst are large kill shards; the rest are small sparks. */
    const val SHARDS_PER_BURST = 4

    /** Particles at least this large are drawn as shards. */
    const val SHARD_MIN_SIZE = 4.5f
}

/**
 * Interaction-owned reducer for drop-eligible visual state.
 *
 * Its RNG and mutable collections are intentionally outside simulation state:
 * visual evolution cannot influence a domain decision or a persisted value.
 */
class InteractionFxReducer(seed: Int) {
    private var random = CloneableXorWowRandom(seed xor FX_SEED_MASK)
    private var motionEchoClock = 0f
    // Last sampled Core position; damage numbers drift away from it. Presentation only.
    private var coreX = Float.NaN
    private var coreY = Float.NaN
    private val particles = mutableListOf<Particle>()
    private val motionEchoes = mutableListOf<MotionEcho>()
    private val shockwaves = mutableListOf<Shockwave>()
    private val damageNumbers = mutableListOf<DamageNumber>()
    private val weaponArcs = mutableListOf<WeaponArc>()
    private val buildNotifications = mutableListOf<BuildNotificationProjection>()
    private var projectionDirty = false
    private var cachedProjection = VisualFxProjection.EMPTY
    private var particlesDirty = false
    private var motionEchoesDirty = false
    private var shockwavesDirty = false
    private var damageNumbersDirty = false
    private var weaponArcsDirty = false

    fun apply(cues: Iterable<VisualFxCue>) {
        if (cues is List<*>) {
            @Suppress("UNCHECKED_CAST")
            val indexedCues = cues as List<VisualFxCue>
            for (index in indexedCues.indices) {
                if (applyCue(indexedCues[index])) projectionDirty = true
            }
        } else {
            for (cue in cues) {
                if (applyCue(cue)) projectionDirty = true
            }
        }
    }

    fun snapshot(): VisualFxProjection {
        if (!projectionDirty) return cachedProjection
        cachedProjection = VisualFxProjection(
            buildNotifications = buildNotifications.mapToImmutableList { it },
            particles = if (particlesDirty) particles.mapToImmutableList { value ->
                ParticleProjection(
                    value.x,
                    value.y,
                    value.vx,
                    value.vy,
                    value.life,
                    value.maxLife,
                    value.colorIndex,
                    value.size,
                )
            } else cachedProjection.particles,
            motionEchoes = if (motionEchoesDirty) motionEchoes.mapToImmutableList { value ->
                MotionEchoProjection(value.x, value.y, value.life, value.maxLife, value.intensity)
            } else cachedProjection.motionEchoes,
            shockwaves = if (shockwavesDirty) shockwaves.mapToImmutableList { value ->
                ShockwaveProjection(
                    value.x,
                    value.y,
                    value.life,
                    value.maxLife,
                    value.maxRadius,
                    value.colorIndex,
                )
            } else cachedProjection.shockwaves,
            damageNumbers = if (damageNumbersDirty) damageNumbers.mapToImmutableList { value ->
                DamageNumberProjection(
                    x = value.x,
                    y = value.y,
                    amount = value.amount,
                    critical = value.critical,
                    life = value.life,
                    compactAmount = value.compactAmount,
                    fullAmount = value.fullAmount,
                    russianCompactAmount = value.russianCompactAmount,
                    driftX = value.driftX,
                    driftY = value.driftY,
                )
            } else cachedProjection.damageNumbers,
            weaponArcs = if (weaponArcsDirty) weaponArcs.mapToImmutableList { value ->
                WeaponArcProjection(value.fromX, value.fromY, value.toX, value.toY, value.life)
            } else cachedProjection.weaponArcs,
        )
        projectionDirty = false
        particlesDirty = false
        motionEchoesDirty = false
        shockwavesDirty = false
        damageNumbersDirty = false
        weaponArcsDirty = false
        return cachedProjection
    }

    /** Returns whether the externally visible projection changed. */
    private fun applyCue(cue: VisualFxCue): Boolean =
        when (cue) {
            is VisualFxCue.BuildChanged -> {
                buildNotifications += BuildNotificationProjection(cue.title, cue.details, 6f)
                trimFront(buildNotifications, 3)
                true
            }
            VisualFxCue.ClearAll -> clearAll()
            VisualFxCue.ClearWeaponArcs -> if (weaponArcs.isEmpty()) {
                false
            } else {
                weaponArcs.clear()
                weaponArcsDirty = true
                true
            }
            is VisualFxCue.MotionSample -> sampleMotionEcho(cue)
            is VisualFxCue.EffectsAdvanced -> advanceEffects(cue.deltaSeconds)
            is VisualFxCue.WeaponArcsAdvanced -> advanceWeaponArcs(cue.deltaSeconds)
            is VisualFxCue.Burst -> burst(cue)
            is VisualFxCue.DirectionalBurst -> directionalBurst(cue)
            is VisualFxCue.ShockwaveAdded -> {
                shockwaves += Shockwave(cue.x, cue.y, cue.life, cue.life, cue.maxRadius, cue.colorIndex)
                trimFront(shockwaves, InteractionFxLimits.MAX_SHOCKWAVES)
                shockwavesDirty = true
                true
            }
            is VisualFxCue.DamageNumberAdded -> {
                if (damageNumbers.size < InteractionFxLimits.MAX_DAMAGE_NUMBERS) {
                    val dx = if (coreX.isNaN()) 0f else cue.x - coreX
                    val dy = if (coreY.isNaN()) -1f else cue.y - coreY
                    val length = kotlin.math.sqrt(dx * dx + dy * dy)
                    damageNumbers += if (length > 0.001f) {
                        DamageNumber(cue.x, cue.y, cue.amount, cue.critical, driftX = dx / length, driftY = dy / length)
                    } else {
                        DamageNumber(cue.x, cue.y, cue.amount, cue.critical)
                    }
                    damageNumbersDirty = true
                    true
                } else {
                    false
                }
            }
            is VisualFxCue.WeaponArcAdded -> {
                weaponArcs += WeaponArc(cue.fromX, cue.fromY, cue.toX, cue.toY, cue.life)
                trimFront(weaponArcs, InteractionFxLimits.MAX_WEAPON_ARCS)
                weaponArcsDirty = true
                true
            }
            is VisualFxCue.WorldRebased -> {
                coreX -= cue.shiftX
                coreY -= cue.shiftY
                val changed = particles.isNotEmpty() || damageNumbers.isNotEmpty()
                for (index in particles.indices) {
                    val value = particles[index]
                    value.x -= cue.shiftX
                    value.y -= cue.shiftY
                }
                for (index in damageNumbers.indices) {
                    val value = damageNumbers[index]
                    value.x -= cue.shiftX
                    value.y -= cue.shiftY
                }
                if (particles.isNotEmpty()) particlesDirty = true
                if (damageNumbers.isNotEmpty()) damageNumbersDirty = true
                changed
            }
            is VisualFxCue.VisualCuesDropped -> false
        }

    private fun clearAll(): Boolean {
        val changed = buildNotifications.isNotEmpty() || particles.isNotEmpty() ||
            motionEchoes.isNotEmpty() ||
            shockwaves.isNotEmpty() ||
            damageNumbers.isNotEmpty() ||
            weaponArcs.isNotEmpty()
        buildNotifications.clear()
        particles.clear()
        motionEchoes.clear()
        shockwaves.clear()
        damageNumbers.clear()
        weaponArcs.clear()
        motionEchoClock = 0f
        coreX = Float.NaN
        coreY = Float.NaN
        if (changed) {
            particlesDirty = true
            motionEchoesDirty = true
            shockwavesDirty = true
            damageNumbersDirty = true
            weaponArcsDirty = true
        }
        return changed
    }

    private fun sampleMotionEcho(cue: VisualFxCue.MotionSample): Boolean {
        coreX = cue.previousCoreX
        coreY = cue.previousCoreY
        val intensity = clamp((cue.speed - 260f) / 1_500f, 0f, 1f)
        if (intensity <= 0f && cue.dashPhaseTime <= 0f) {
            motionEchoClock = 0f
            return false
        }
        motionEchoClock -= cue.deltaSeconds
        if (motionEchoClock > 0f) return false
        val dashIntensity = if (cue.dashPhaseTime > 0f) 1f else intensity
        motionEchoClock = if (cue.dashPhaseTime > 0f) 0.018f else 0.075f - intensity * 0.04f
        val maxLife = 0.2f + dashIntensity * 0.16f
        motionEchoes += MotionEcho(
            cue.previousCoreX,
            cue.previousCoreY,
            maxLife,
            maxLife,
            dashIntensity,
        )
        trimFront(motionEchoes, InteractionFxLimits.MAX_MOTION_ECHOES)
        motionEchoesDirty = true
        return true
    }

    private fun advanceEffects(delta: Float): Boolean {
        if (
            buildNotifications.isEmpty() &&
            particles.isEmpty() &&
            motionEchoes.isEmpty() &&
            shockwaves.isEmpty() &&
            damageNumbers.isEmpty()
        ) return false
        for (index in buildNotifications.indices) {
            val value = buildNotifications[index]
            buildNotifications[index] = value.copy(life = value.life - delta)
        }
        removeExpired(buildNotifications) { it.life > 0f }
        for (index in particles.indices) {
            val value = particles[index]
            value.x += value.vx * delta
            value.y += value.vy * delta
            value.vx *= exp(-2.2f * delta)
            value.vy *= exp(-2.2f * delta)
            value.life -= delta
        }
        removeExpired(particles) { value -> !(value.life <= 0f) }
        for (index in motionEchoes.indices) motionEchoes[index].life -= delta
        removeExpired(motionEchoes) { value -> !(value.life <= 0f) }
        for (index in shockwaves.indices) shockwaves[index].life -= delta
        removeExpired(shockwaves) { value -> !(value.life <= 0f) }
        // Numbers keep their anchor; the renderer eases the drift from life (see DamageNumberProjection).
        for (index in damageNumbers.indices) damageNumbers[index].life -= delta
        removeExpired(damageNumbers) { value -> !(value.life <= 0f) }
        if (particles.isNotEmpty() || cachedProjection.particles.isNotEmpty()) particlesDirty = true
        if (motionEchoes.isNotEmpty() || cachedProjection.motionEchoes.isNotEmpty()) motionEchoesDirty = true
        if (shockwaves.isNotEmpty() || cachedProjection.shockwaves.isNotEmpty()) shockwavesDirty = true
        if (damageNumbers.isNotEmpty() || cachedProjection.damageNumbers.isNotEmpty()) damageNumbersDirty = true
        return true
    }

    private fun advanceWeaponArcs(delta: Float): Boolean {
        if (weaponArcs.isEmpty()) return false
        for (index in weaponArcs.indices) weaponArcs[index].life -= delta
        removeExpired(weaponArcs) { value -> !(value.life <= 0f) }
        weaponArcsDirty = true
        return true
    }

    private fun burst(cue: VisualFxCue.Burst): Boolean {
        val sizeBefore = particles.size
        repeat(particleCount(cue.requestedCount, cue.density)) { index ->
            if (particles.size >= InteractionFxLimits.MAX_PARTICLES) return@repeat
            val angle = random.nextFloat() * TAU
            val speed = 35f + random.nextFloat() * 185f
            val life = 0.25f + random.nextFloat() * 0.55f
            particles += Particle(
                cue.x,
                cue.y,
                cos(angle) * speed,
                sin(angle) * speed,
                life,
                life,
                cue.colorIndex,
                particleSize(index, random.nextFloat()),
            )
        }
        return (particles.size != sizeBefore).also { changed ->
            if (changed) particlesDirty = true
        }
    }

    private fun directionalBurst(cue: VisualFxCue.DirectionalBurst): Boolean {
        val sizeBefore = particles.size
        val baseAngle = atan2(cue.directionY, cue.directionX)
        repeat(particleCount(cue.requestedCount, cue.density)) { index ->
            if (particles.size >= InteractionFxLimits.MAX_PARTICLES) return@repeat
            val angle = baseAngle + (random.nextFloat() - 0.5f) * 1.15f
            val speed = 90f + random.nextFloat() * 310f
            val life = 0.22f + random.nextFloat() * 0.42f
            particles += Particle(
                cue.x,
                cue.y,
                cos(angle) * speed,
                sin(angle) * speed,
                life,
                life,
                cue.colorIndex,
                particleSize(index, random.nextFloat()),
            )
        }
        return (particles.size != sizeBefore).also { changed ->
            if (changed) particlesDirty = true
        }
    }

    /** The first [InteractionFxLimits.SHARDS_PER_BURST] particles are shards, the rest small sparks. */
    private fun particleSize(index: Int, roll: Float): Float =
        if (index < InteractionFxLimits.SHARDS_PER_BURST) InteractionFxLimits.SHARD_MIN_SIZE + roll * 3.5f else 1.2f + roll * 1.8f

    private fun particleCount(requestedCount: Int, density: ParticleDensity): Int {
        val multiplier = when (density) {
            ParticleDensity.LOW -> 0.45f
            ParticleDensity.NORMAL -> 1f
            ParticleDensity.HIGH -> 1.4f
        }
        return (requestedCount * multiplier).toInt().coerceAtLeast(1)
    }

    private fun <Element> trimFront(values: MutableList<Element>, maximum: Int) {
        while (values.size > maximum) values.removeAt(0)
    }

    /** Stable in-place compaction avoids iterator allocation and repeated ArrayList shifts. */
    private inline fun <Element> removeExpired(
        values: MutableList<Element>,
        isAlive: (Element) -> Boolean,
    ) {
        var retainedCount = 0
        for (index in values.indices) {
            val value = values[index]
            if (isAlive(value)) {
                if (retainedCount != index) values[retainedCount] = value
                retainedCount++
            }
        }
        var index = values.lastIndex
        while (index >= retainedCount) {
            values.removeAt(index)
            index--
        }
    }

    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float,
        val maxLife: Float,
        val colorIndex: Int,
        val size: Float,
    )

    private data class MotionEcho(
        val x: Float,
        val y: Float,
        var life: Float,
        val maxLife: Float,
        val intensity: Float,
    )

    private data class Shockwave(
        val x: Float,
        val y: Float,
        var life: Float,
        val maxLife: Float,
        val maxRadius: Float,
        val colorIndex: Int,
    )

    private data class DamageNumber(
        var x: Float,
        var y: Float,
        val amount: Long,
        val critical: Boolean,
        var life: Float = InteractionFxLimits.DAMAGE_NUMBER_LIFE_SECONDS,
        val compactAmount: String = formatDamageNumber(amount, DamageNumberFormat.COMPACT),
        val fullAmount: String = formatDamageNumber(amount, DamageNumberFormat.FULL),
        val russianCompactAmount: String = compactAmount.russianDamageNumber(),
        val driftX: Float = 0f,
        val driftY: Float = -1f,
    )

    private data class WeaponArc(
        val fromX: Float,
        val fromY: Float,
        val toX: Float,
        val toY: Float,
        var life: Float,
    )

    private companion object {
        const val FX_SEED_MASK = 0x5EED_C0DE
        const val TAU = 6.283185307179586f
    }
}
