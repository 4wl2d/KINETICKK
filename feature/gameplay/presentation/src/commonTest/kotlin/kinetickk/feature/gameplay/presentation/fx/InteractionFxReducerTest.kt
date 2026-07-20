// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.presentation.fx

import kinetickk.core.profile.api.ParticleDensity
import kinetickk.feature.gameplay.domain.protocol.VisualFxCue
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
    fun everyVisualCollectionHasAnExactFiniteCap() {
        val reducer = InteractionFxReducer(seed = 48)
        reducer.apply(
            buildList {
                repeat(160) { index ->
                    add(VisualFxCue.ShockwaveAdded(index.toFloat(), 0f, 1f, 10f, 1))
                    add(VisualFxCue.DamageNumberAdded(0f, index.toFloat(), index.toLong(), false))
                    add(VisualFxCue.WeaponArcAdded(0f, 0f, index.toFloat(), 1f, 10f))
                }
                add(VisualFxCue.Burst(0f, 0f, 2_000, 1, ParticleDensity.HIGH))
            },
        )

        val snapshot = reducer.snapshot()
        assertEquals(700, snapshot.particles.size)
        assertEquals(48, snapshot.shockwaves.size)
        assertEquals(140, snapshot.damageNumbers.size)
        assertEquals(128, snapshot.weaponArcs.size)
    }

    @Test
    fun motionEchoesAndCueOrderingPreserveTheirFiniteLifecycle() {
        val reducer = InteractionFxReducer(seed = 50)
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
        val reducer = InteractionFxReducer(seed = 49)
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

    private fun kinetickk.feature.gameplay.domain.renderModel.VisualFxProjection.boundedSizes() = listOf(
        particles.size,
        motionEchoes.size,
        shockwaves.size,
        damageNumbers.size,
        weaponArcs.size,
    )
}
