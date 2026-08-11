// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class InteractionFxReducerTest {
    @Test
    fun identicalSeedsAndCuesProduceIdenticalVisualSnapshots() {
        val cues = listOf(
            VisualFxCue.Burst(10f, 20f, 12, 3, ParticleDensity.HIGH),
            VisualFxCue.DirectionalBurst(5f, 6f, 7, 2, 1f, -1f, ParticleDensity.NORMAL),
            VisualFxCue.EffectsAdvanced(1f / 120f),
        )
        val first = InteractionFxReducer(seed = 47).applyAndSnapshot(cues)
        val second = InteractionFxReducer(seed = 47).applyAndSnapshot(cues)

        assertEquals(first, second)
    }

    @Test
    fun particlesAcceptSevenHundredAndRejectSevenHundredFirst() {
        val reducer = InteractionFxReducer(seed = 48)
        reducer.apply(
            listOf(
                VisualFxCue.Burst(
                    x = 0f,
                    y = 0f,
                    requestedCount = InteractionFxLimits.MAX_PARTICLES,
                    colorIndex = 1,
                    density = ParticleDensity.NORMAL,
                ),
            ),
        )
        val accepted = reducer.snapshot().particles

        reducer.apply(
            listOf(VisualFxCue.Burst(1f, 1f, 1, 2, ParticleDensity.NORMAL)),
        )

        assertEquals(InteractionFxLimits.MAX_PARTICLES, accepted.size)
        assertEquals(accepted, reducer.snapshot().particles)
    }

    @Test
    fun directionalParticlesAcceptSevenHundredAndRejectSevenHundredFirst() {
        val reducer = InteractionFxReducer(seed = 49)
        reducer.apply(
            listOf(
                VisualFxCue.DirectionalBurst(
                    x = 0f,
                    y = 0f,
                    requestedCount = InteractionFxLimits.MAX_PARTICLES,
                    colorIndex = 1,
                    directionX = 1f,
                    directionY = 0f,
                    density = ParticleDensity.NORMAL,
                ),
            ),
        )
        val accepted = reducer.snapshot().particles

        reducer.apply(
            listOf(
                VisualFxCue.DirectionalBurst(
                    x = 1f,
                    y = 1f,
                    requestedCount = 1,
                    colorIndex = 2,
                    directionX = 1f,
                    directionY = 0f,
                    density = ParticleDensity.NORMAL,
                ),
            ),
        )

        assertEquals(InteractionFxLimits.MAX_PARTICLES, accepted.size)
        assertEquals(accepted, reducer.snapshot().particles)
    }

    @Test
    fun motionEchoesAcceptThirtySixAndTrimOldestOnThirtySeventh() {
        val reducer = InteractionFxReducer(seed = 50)
        repeat(InteractionFxLimits.MAX_MOTION_ECHOES) { index ->
            reducer.apply(listOf(motionSample(index)))
        }

        val accepted = reducer.snapshot().motionEchoes
        assertEquals(InteractionFxLimits.MAX_MOTION_ECHOES, accepted.size)
        assertEquals(0f, accepted.first().x)

        reducer.apply(listOf(motionSample(InteractionFxLimits.MAX_MOTION_ECHOES)))

        val overflow = reducer.snapshot().motionEchoes
        assertEquals(InteractionFxLimits.MAX_MOTION_ECHOES, overflow.size)
        assertEquals(1f, overflow.first().x)
        assertEquals(InteractionFxLimits.MAX_MOTION_ECHOES.toFloat(), overflow.last().x)
    }

    @Test
    fun shockwavesAcceptFortyEightAndTrimOldestOnFortyNinth() {
        val reducer = InteractionFxReducer(seed = 51)
        repeat(InteractionFxLimits.MAX_SHOCKWAVES) { index ->
            reducer.apply(listOf(shockwave(index)))
        }

        val accepted = reducer.snapshot().shockwaves
        assertEquals(InteractionFxLimits.MAX_SHOCKWAVES, accepted.size)
        assertEquals(0f, accepted.first().x)

        reducer.apply(listOf(shockwave(InteractionFxLimits.MAX_SHOCKWAVES)))

        val overflow = reducer.snapshot().shockwaves
        assertEquals(InteractionFxLimits.MAX_SHOCKWAVES, overflow.size)
        assertEquals(1f, overflow.first().x)
        assertEquals(InteractionFxLimits.MAX_SHOCKWAVES.toFloat(), overflow.last().x)
    }

    @Test
    fun damageNumbersAcceptOneHundredFortyAndRejectOneHundredFortyFirst() {
        val reducer = InteractionFxReducer(seed = 52)
        repeat(InteractionFxLimits.MAX_DAMAGE_NUMBERS) { index ->
            reducer.apply(listOf(damageNumber(index)))
        }
        val accepted = reducer.snapshot().damageNumbers

        reducer.apply(listOf(damageNumber(InteractionFxLimits.MAX_DAMAGE_NUMBERS)))

        assertEquals(InteractionFxLimits.MAX_DAMAGE_NUMBERS, accepted.size)
        assertEquals(accepted, reducer.snapshot().damageNumbers)
    }

    @Test
    fun weaponArcsAcceptOneHundredTwentyEightAndTrimOldestOnOneHundredTwentyNinth() {
        val reducer = InteractionFxReducer(seed = 53)
        repeat(InteractionFxLimits.MAX_WEAPON_ARCS) { index ->
            reducer.apply(listOf(weaponArc(index)))
        }

        val accepted = reducer.snapshot().weaponArcs
        assertEquals(InteractionFxLimits.MAX_WEAPON_ARCS, accepted.size)
        assertEquals(0f, accepted.first().toX)

        reducer.apply(listOf(weaponArc(InteractionFxLimits.MAX_WEAPON_ARCS)))

        val overflow = reducer.snapshot().weaponArcs
        assertEquals(InteractionFxLimits.MAX_WEAPON_ARCS, overflow.size)
        assertEquals(1f, overflow.first().toX)
        assertEquals(InteractionFxLimits.MAX_WEAPON_ARCS.toFloat(), overflow.last().toX)
    }

    @Test
    fun motionEchoesAndCueOrderingPreserveTheirFiniteLifecycle() {
        val reducer = InteractionFxReducer(seed = 54)
        repeat(100) { index ->
            reducer.apply(
                listOf(
                    VisualFxCue.MotionSample(
                        deltaSeconds = 0.02f,
                        previousCoreX = index.toFloat(),
                        previousCoreY = 0f,
                        speed = 2_000f,
                        dashPhaseTime = 0.1f,
                    ),
                ),
            )
        }
        assertEquals(36, reducer.snapshot().motionEchoes.size)

        reducer.apply(
            listOf(
                VisualFxCue.WeaponArcAdded(0f, 0f, 1f, 1f, 0.14f),
                VisualFxCue.WeaponArcsAdvanced(0.04f),
                VisualFxCue.WeaponArcAdded(0f, 0f, 2f, 2f, 0.14f),
            ),
        )
        val arcs = reducer.snapshot().weaponArcs
        assertEquals(0.10f, arcs.first().life, absoluteTolerance = 0.000_001f)
        assertEquals(0.14f, arcs.last().life)
    }

    @Test
    fun clearAndWorldRebaseFollowDeclaredPresentationPolicy() {
        val reducer = InteractionFxReducer(seed = 55)
        reducer.apply(
            listOf(
                VisualFxCue.Burst(100f, 200f, 1, 1, ParticleDensity.NORMAL),
                VisualFxCue.DamageNumberAdded(100f, 200f, 5, false),
                VisualFxCue.ShockwaveAdded(100f, 200f, 1f, 20f, 2),
                VisualFxCue.MotionSample(0.02f, 100f, 200f, 2_000f, 0.1f),
                VisualFxCue.WeaponArcAdded(100f, 200f, 300f, 400f, 1f),
                VisualFxCue.WorldRebased(40f, 50f),
            ),
        )
        val rebased = reducer.snapshot()
        assertEquals(60f, rebased.particles.single().x)
        assertEquals(150f, rebased.particles.single().y)
        assertEquals(60f, rebased.damageNumbers.single().x)
        assertEquals(150f, rebased.damageNumbers.single().y)
        assertEquals(100f, rebased.shockwaves.single().x)
        assertEquals(100f, rebased.motionEchoes.single().x)
        assertEquals(100f, rebased.weaponArcs.single().fromX)

        reducer.apply(listOf(VisualFxCue.ClearAll))
        val cleared = reducer.snapshot()
        assertTrue(cleared.boundedSizes().all { it == 0 })
    }

    private fun InteractionFxReducer.applyAndSnapshot(cues: Iterable<VisualFxCue>) =
        apply(cues).let { snapshot() }

    private fun kinetickk.ball.gameplay.interaction.fx.VisualFxProjection.boundedSizes() = listOf(
        particles.size,
        motionEchoes.size,
        shockwaves.size,
        damageNumbers.size,
        weaponArcs.size,
    )

    private fun motionSample(index: Int) = VisualFxCue.MotionSample(
        deltaSeconds = 0.02f,
        previousCoreX = index.toFloat(),
        previousCoreY = 0f,
        speed = 2_000f,
        dashPhaseTime = 0.1f,
    )

    private fun shockwave(index: Int) = VisualFxCue.ShockwaveAdded(
        x = index.toFloat(),
        y = 0f,
        life = 10f,
        maxRadius = 10f,
        colorIndex = 1,
    )

    private fun damageNumber(index: Int) = VisualFxCue.DamageNumberAdded(
        x = index.toFloat(),
        y = 0f,
        amount = index.toLong(),
        critical = false,
    )

    private fun weaponArc(index: Int) = VisualFxCue.WeaponArcAdded(
        fromX = 0f,
        fromY = 0f,
        toX = index.toFloat(),
        toY = 1f,
        life = 10f,
    )
}
