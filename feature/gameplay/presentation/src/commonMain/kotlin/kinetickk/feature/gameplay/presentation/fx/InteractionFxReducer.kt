// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.presentation.fx

import kinetickk.core.collections.toImmutableList
import kinetickk.core.random.CloneableXorWowRandom
import kinetickk.feature.gameplay.domain.model.clamp
import kinetickk.core.profile.api.DamageNumberFormat
import kinetickk.feature.gameplay.domain.model.formatDamageNumber
import kinetickk.core.profile.api.ParticleDensity
import kinetickk.feature.gameplay.domain.renderModel.DamageNumberProjection
import kinetickk.feature.gameplay.domain.renderModel.MotionEchoProjection
import kinetickk.feature.gameplay.domain.renderModel.ParticleProjection
import kinetickk.feature.gameplay.domain.renderModel.ShockwaveProjection
import kinetickk.feature.gameplay.domain.renderModel.VisualFxProjection
import kinetickk.feature.gameplay.domain.renderModel.WeaponArcProjection
import kinetickk.feature.gameplay.domain.protocol.VisualFxCue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Interaction-owned reducer for drop-eligible visual state.
 *
 * Its RNG and mutable collections are intentionally outside simulation state:
 * visual evolution cannot influence a domain decision or a persisted value.
 */
class InteractionFxReducer(seed: Int) {
    private var random = CloneableXorWowRandom(seed xor FX_SEED_MASK)
    private var motionEchoClock = 0f
    private val particles = mutableListOf<Particle>()
    private val motionEchoes = mutableListOf<MotionEcho>()
    private val shockwaves = mutableListOf<Shockwave>()
    private val damageNumbers = mutableListOf<DamageNumber>()
    private val weaponArcs = mutableListOf<WeaponArc>()

    fun apply(cues: Iterable<VisualFxCue>) {
        cues.forEach(::apply)
    }

    fun snapshot(): VisualFxProjection = VisualFxProjection(
        particles = particles.map { value ->
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
        }.toImmutableList(),
        motionEchoes = motionEchoes.map { value ->
            MotionEchoProjection(value.x, value.y, value.life, value.maxLife, value.intensity)
        }.toImmutableList(),
        shockwaves = shockwaves.map { value ->
            ShockwaveProjection(
                value.x,
                value.y,
                value.life,
                value.maxLife,
                value.maxRadius,
                value.colorIndex,
            )
        }.toImmutableList(),
        damageNumbers = damageNumbers.map { value ->
            DamageNumberProjection(
                x = value.x,
                y = value.y,
                amount = value.amount,
                critical = value.critical,
                life = value.life,
                compactAmount = value.compactAmount,
                fullAmount = value.fullAmount,
            )
        }.toImmutableList(),
        weaponArcs = weaponArcs.map { value ->
            WeaponArcProjection(value.fromX, value.fromY, value.toX, value.toY, value.life)
        }.toImmutableList(),
    )

    fun boundedCollectionSizes(): List<Int> = listOf(
        particles.size,
        motionEchoes.size,
        shockwaves.size,
        damageNumbers.size,
        weaponArcs.size,
    )

    private fun apply(cue: VisualFxCue) {
        when (cue) {
            VisualFxCue.ClearAll -> clearAll()
            VisualFxCue.ClearWeaponArcs -> weaponArcs.clear()
            is VisualFxCue.MotionSample -> sampleMotionEcho(cue)
            is VisualFxCue.EffectsAdvanced -> advanceEffects(cue.deltaSeconds)
            is VisualFxCue.WeaponArcsAdvanced -> advanceWeaponArcs(cue.deltaSeconds)
            is VisualFxCue.Burst -> burst(cue)
            is VisualFxCue.DirectionalBurst -> directionalBurst(cue)
            is VisualFxCue.ShockwaveAdded -> {
                shockwaves += Shockwave(cue.x, cue.y, cue.life, cue.life, cue.maxRadius, cue.colorIndex)
                trimFront(shockwaves, MAX_SHOCKWAVES)
            }
            is VisualFxCue.DamageNumberAdded -> {
                if (damageNumbers.size < MAX_DAMAGE_NUMBERS) {
                    damageNumbers += DamageNumber(cue.x, cue.y, cue.amount, cue.critical)
                }
            }
            is VisualFxCue.WeaponArcAdded -> {
                weaponArcs += WeaponArc(cue.fromX, cue.fromY, cue.toX, cue.toY, cue.life)
                trimFront(weaponArcs, MAX_WEAPON_ARCS)
            }
            is VisualFxCue.WorldRebased -> {
                particles.forEach { value ->
                    value.x -= cue.shiftX
                    value.y -= cue.shiftY
                }
                damageNumbers.forEach { value ->
                    value.x -= cue.shiftX
                    value.y -= cue.shiftY
                }
            }
            is VisualFxCue.VisualCuesDropped -> Unit
        }
    }

    private fun clearAll() {
        particles.clear()
        motionEchoes.clear()
        shockwaves.clear()
        damageNumbers.clear()
        weaponArcs.clear()
        motionEchoClock = 0f
    }

    private fun sampleMotionEcho(cue: VisualFxCue.MotionSample) {
        val intensity = clamp((cue.speed - 260f) / 1_500f, 0f, 1f)
        if (intensity <= 0f && cue.dashPhaseTime <= 0f) {
            motionEchoClock = 0f
            return
        }
        motionEchoClock -= cue.deltaSeconds
        if (motionEchoClock > 0f) return
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
        trimFront(motionEchoes, MAX_MOTION_ECHOES)
    }

    private fun advanceEffects(delta: Float) {
        particles.forEach { value ->
            value.x += value.vx * delta
            value.y += value.vy * delta
            value.vx *= exp(-2.2f * delta)
            value.vy *= exp(-2.2f * delta)
            value.life -= delta
        }
        particles.removeAll { it.life <= 0f }
        motionEchoes.forEach { it.life -= delta }
        motionEchoes.removeAll { it.life <= 0f }
        shockwaves.forEach { it.life -= delta }
        shockwaves.removeAll { it.life <= 0f }
        damageNumbers.forEach { value ->
            value.y -= 34f * delta
            value.life -= delta
        }
        damageNumbers.removeAll { it.life <= 0f }
    }

    private fun advanceWeaponArcs(delta: Float) {
        weaponArcs.forEach { it.life -= delta }
        weaponArcs.removeAll { it.life <= 0f }
    }

    private fun burst(cue: VisualFxCue.Burst) {
        repeat(particleCount(cue.requestedCount, cue.density)) {
            if (particles.size >= MAX_PARTICLES) return@repeat
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
                1.5f + random.nextFloat() * 3.5f,
            )
        }
    }

    private fun directionalBurst(cue: VisualFxCue.DirectionalBurst) {
        val baseAngle = atan2(cue.directionY, cue.directionX)
        repeat(particleCount(cue.requestedCount, cue.density)) {
            if (particles.size >= MAX_PARTICLES) return@repeat
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
                1.8f + random.nextFloat() * 4.2f,
            )
        }
    }

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
        var life: Float = 0.65f,
        val compactAmount: String = formatDamageNumber(amount, DamageNumberFormat.COMPACT),
        val fullAmount: String = formatDamageNumber(amount, DamageNumberFormat.FULL),
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
        const val MAX_PARTICLES = 700
        const val MAX_MOTION_ECHOES = 36
        const val MAX_SHOCKWAVES = 48
        const val MAX_DAMAGE_NUMBERS = 140
        const val MAX_WEAPON_ARCS = 128
    }
}
