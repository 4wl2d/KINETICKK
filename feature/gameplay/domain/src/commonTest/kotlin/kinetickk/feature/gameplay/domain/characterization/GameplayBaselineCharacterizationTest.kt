// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.domain.characterization

import kinetickk.core.audio.api.AudioCue
import kinetickk.core.profile.api.PlayerPreferences
import kinetickk.feature.gameplay.domain.engine.GameDispatchResult
import kinetickk.feature.gameplay.domain.engine.GameEngine
import kinetickk.feature.gameplay.domain.model.EnemyType
import kinetickk.feature.gameplay.domain.model.Pickup
import kinetickk.feature.gameplay.domain.model.PickupType
import kinetickk.feature.gameplay.domain.model.Projectile
import kinetickk.feature.gameplay.domain.model.TrailPoint
import kinetickk.feature.gameplay.domain.protocol.BrakeSource
import kinetickk.feature.gameplay.domain.protocol.GameEffect
import kinetickk.feature.gameplay.domain.protocol.GameplayAction
import kinetickk.feature.gameplay.domain.protocol.VisualFxCue
import kinetickk.feature.gameplay.domain.reducer.EngineState
import kinetickk.feature.gameplay.domain.reducer.GameReducer
import kinetickk.feature.gameplay.domain.reducer.GameReductionResult
import kinetickk.feature.gameplay.domain.reducer.initialEngineState
import kinetickk.feature.gameplay.domain.simulation.MutableGameState
import kinetickk.feature.gameplay.domain.simulation.addPickup
import kinetickk.feature.gameplay.domain.simulation.addProjectile
import kinetickk.feature.gameplay.domain.simulation.applyPreferences
import kinetickk.feature.gameplay.domain.simulation.emitSound
import kinetickk.feature.gameplay.domain.simulation.emitVisualFx
import kinetickk.feature.gameplay.domain.simulation.sampleFluxTrail
import kinetickk.feature.gameplay.domain.simulation.setVelocityForTesting
import kinetickk.feature.gameplay.domain.simulation.spawnEnemy
import kinetickk.feature.gameplay.domain.simulation.startRun
import kinetickk.feature.gameplay.domain.simulation.takeSoundCues
import kinetickk.feature.gameplay.domain.simulation.takeVisualFxCues
import kinetickk.feature.gameplay.domain.simulation.update
import kinetickk.feature.gameplay.domain.simulation.updatePointer
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GameplayBaselineCharacterizationTest {
    @Test
    fun seededIntentTraceHasStableCheckpoints() {
        val engine = GameEngine.create(
            bootstrapProgress = null,
            seed = 0x4B1D,
            initialMatter = 17,
        )
        val actions = listOf(
            GameplayAction.ViewportChanged(width = 960f, height = 540f, density = 1.25f),
            GameplayAction.PointerMoved(x = 820f, y = 135f),
            GameplayAction.FrameElapsed(1f / 60f),
            GameplayAction.DashRequested,
            GameplayAction.FrameElapsed(1f / 30f),
            GameplayAction.BrakeChanged(BrakeSource.KEYBOARD, active = true),
            GameplayAction.FrameElapsed(0.05f),
            GameplayAction.PointerMoved(x = 180f, y = 430f),
            GameplayAction.BrakeChanged(BrakeSource.KEYBOARD, active = false),
            GameplayAction.FrameElapsed(0.1f),
            GameplayAction.FrameElapsed(0.075f),
            GameplayAction.FrameElapsed(0.1f),
            GameplayAction.FrameElapsed(0.1f),
            GameplayAction.FrameElapsed(0.1f),
        )

        val checkpoints = actions.mapNotNull { action ->
            val committed = assertIs<GameDispatchResult.Committed>(engine.dispatch(action))
            if (action is GameplayAction.FrameElapsed) committed.toTraceCheckpoint() else null
        }

        assertEquals(
            listOf(
                TraceCheckpoint(
                    revision = 3uL,
                    elapsedSteps = 2,
                    coreXCentipixels = 35,
                    coreYCentipixels = -14,
                    velocityXCentipixelsPerSecond = 2_760,
                    velocityYCentipixelsPerSecond = -1_096,
                    heatCentipercent = 0,
                    hpCentipercent = 10_000,
                    enemyCount = 5,
                    firstEnemy = "1:DRIFTER:69088:48002:-220:-153:3000",
                    lastEnemy = "5:DRIFTER:-21851:-48606:110:245:3000",
                    projectileCount = 0,
                    pickupCount = 0,
                    trailCount = 0,
                    effectOrder = "FX>AUDIO",
                    visualCueCount = 6,
                    audioCues = "",
                ),
                TraceCheckpoint(
                    revision = 5uL,
                    elapsedSteps = 6,
                    coreXCentipixels = 2_055,
                    coreYCentipixels = -816,
                    velocityXCentipixelsPerSecond = 62_361,
                    velocityYCentipixelsPerSecond = -24_761,
                    heatCentipercent = 3_553,
                    hpCentipercent = 10_000,
                    enemyCount = 5,
                    firstEnemy = "1:DRIFTER:69072:47991:-634:-450:3000",
                    lastEnemy = "5:DRIFTER:-21843:-48589:331:703:3000",
                    projectileCount = 0,
                    pickupCount = 0,
                    trailCount = 0,
                    effectOrder = "FX>AUDIO",
                    visualCueCount = 14,
                    audioCues = "DASH",
                ),
                TraceCheckpoint(
                    revision = 7uL,
                    elapsedSteps = 13,
                    coreXCentipixels = 5_411,
                    coreYCentipixels = -2_148,
                    velocityXCentipixelsPerSecond = 54_139,
                    velocityYCentipixelsPerSecond = -21_496,
                    heatCentipercent = 3_375,
                    hpCentipercent = 10_000,
                    enemyCount = 5,
                    firstEnemy = "1:DRIFTER:69013:47948:-1276:-943:3000",
                    lastEnemy = "5:DRIFTER:-21810:-48524:727:1410:3000",
                    projectileCount = 0,
                    pickupCount = 0,
                    trailCount = 2,
                    effectOrder = "FX>AUDIO",
                    visualCueCount = 21,
                    audioCues = "",
                ),
                TraceCheckpoint(
                    revision = 10uL,
                    elapsedSteps = 27,
                    coreXCentipixels = 10_433,
                    coreYCentipixels = -4_002,
                    velocityXCentipixelsPerSecond = 33_664,
                    velocityYCentipixelsPerSecond = -11_169,
                    heatCentipercent = 3_154,
                    hpCentipercent = 10_000,
                    enemyCount = 6,
                    firstEnemy = "1:DRIFTER:68797:47781:-2293:-1832:3000",
                    lastEnemy = "6:DRIFTER:1700:45077:68:-393:3001",
                    projectileCount = 0,
                    pickupCount = 0,
                    trailCount = 5,
                    effectOrder = "FX>AUDIO",
                    visualCueCount = 42,
                    audioCues = "",
                ),
                TraceCheckpoint(
                    revision = 11uL,
                    elapsedSteps = 37,
                    coreXCentipixels = 12_615,
                    coreYCentipixels = -4_621,
                    velocityXCentipixelsPerSecond = 20_247,
                    velocityYCentipixelsPerSecond = -4_457,
                    heatCentipercent = 2_995,
                    hpCentipercent = 10_000,
                    enemyCount = 6,
                    firstEnemy = "1:DRIFTER:68580:47603:-2849:-2384:3000",
                    lastEnemy = "6:DRIFTER:1717:44990:306:-1557:3001",
                    projectileCount = 0,
                    pickupCount = 0,
                    trailCount = 6,
                    effectOrder = "FX>AUDIO",
                    visualCueCount = 30,
                    audioCues = "",
                ),
                TraceCheckpoint(
                    revision = 12uL,
                    elapsedSteps = 51,
                    coreXCentipixels = 13_912,
                    coreYCentipixels = -4_610,
                    velocityXCentipixelsPerSecond = 3_587,
                    velocityYCentipixelsPerSecond = 3_839,
                    heatCentipercent = 2_774,
                    hpCentipercent = 10_000,
                    enemyCount = 6,
                    firstEnemy = "1:DRIFTER:68208:47283:-3459:-3033:3000",
                    lastEnemy = "6:DRIFTER:1773:44723:631:-2868:3001",
                    projectileCount = 0,
                    pickupCount = 0,
                    trailCount = 6,
                    effectOrder = "FX>AUDIO",
                    visualCueCount = 42,
                    audioCues = "",
                ),
                TraceCheckpoint(
                    revision = 13uL,
                    elapsedSteps = 65,
                    coreXCentipixels = 13_420,
                    coreYCentipixels = -3_710,
                    velocityXCentipixelsPerSecond = -10_610,
                    velocityYCentipixelsPerSecond = 10_881,
                    heatCentipercent = 2_552,
                    hpCentipercent = 10_000,
                    enemyCount = 6,
                    firstEnemy = "1:DRIFTER:67773:46896:-3939:-3538:3000",
                    lastEnemy = "6:DRIFTER:1864:44322:898:-3891:3001",
                    projectileCount = 0,
                    pickupCount = 0,
                    trailCount = 7,
                    effectOrder = "FX>AUDIO",
                    visualCueCount = 42,
                    audioCues = "",
                ),
                TraceCheckpoint(
                    revision = 14uL,
                    elapsedSteps = 79,
                    coreXCentipixels = 11_414,
                    coreYCentipixels = -2_061,
                    velocityXCentipixelsPerSecond = -22_565,
                    velocityYCentipixelsPerSecond = 16_791,
                    heatCentipercent = 2_330,
                    hpCentipercent = 10_000,
                    enemyCount = 6,
                    firstEnemy = "1:DRIFTER:67288:46459:-4343:-3902:3000",
                    lastEnemy = "6:DRIFTER:1981:43816:1072:-4699:3001",
                    projectileCount = 0,
                    pickupCount = 0,
                    trailCount = 8,
                    effectOrder = "FX>AUDIO",
                    visualCueCount = 42,
                    audioCues = "",
                ),
            ),
            checkpoints,
        )
    }

    @Test
    fun fixedStepsRunAt120HzAndPublishPerStepVisualConsequencesInOrder() {
        val state = MutableGameState(seed = 700, initialMatter = 0)
        state.startRun()
        state.takeSoundCues()
        state.takeVisualFxCues()
        state.enemies.clear()
        state.updatePointer(640f, 360f)
        state.applyPreferences(PlayerPreferences(simulationSpeed = 1f))

        state.update(MutableGameState.FIXED_STEP * 3f)

        assertEquals(120f, 1f / MutableGameState.FIXED_STEP, 0.001f)
        assertEquals(3, state.lastTransitionSteps)
        assertEquals(MutableGameState.FIXED_STEP * 3f, state.elapsed)
        assertEquals("MAEMAEMAE", state.takeVisualFxCues().joinToString(separator = "", transform = ::cueToken))
    }

    @Test
    fun authoritativeCollectionsEnforceTheCurrentNPlusOneCaps() {
        assertEquals(120, MutableGameState.MAX_ENEMIES)
        assertEquals(650, MutableGameState.MAX_PROJECTILES)
        assertEquals(420, MutableGameState.MAX_PICKUPS)
        assertEquals(110, MutableGameState.MAX_TRAIL_POINTS)

        val enemyState = MutableGameState(seed = 701, initialMatter = 0)
        val enemyResults = List(MutableGameState.MAX_ENEMIES + 1) {
            enemyState.spawnEnemy(EnemyType.DRIFTER)
        }
        assertTrue(enemyResults.take(MutableGameState.MAX_ENEMIES).all { it })
        assertFalse(enemyResults.last())
        assertEquals(MutableGameState.MAX_ENEMIES, enemyState.enemies.size)

        val projectileState = MutableGameState(seed = 702, initialMatter = 0)
        repeat(MutableGameState.MAX_PROJECTILES + 1) { index ->
            projectileState.addProjectile(
                Projectile(index.toFloat(), 0f, 0f, 0f, radius = 1f, life = 1f),
            )
        }
        assertEquals(MutableGameState.MAX_PROJECTILES, projectileState.projectiles.size)
        assertEquals((MutableGameState.MAX_PROJECTILES - 1).toFloat(), projectileState.projectiles.last().x)

        val pickupState = MutableGameState(seed = 703, initialMatter = 0)
        repeat(MutableGameState.MAX_PICKUPS + 1) { index ->
            pickupState.addPickup(Pickup(PickupType.DATA, index.toFloat(), 0f))
        }
        assertEquals(MutableGameState.MAX_PICKUPS, pickupState.pickups.size)
        assertEquals((MutableGameState.MAX_PICKUPS - 1).toFloat(), pickupState.pickups.last().x)

        val trailState = MutableGameState(seed = 704, initialMatter = 0)
        repeat(MutableGameState.MAX_TRAIL_POINTS) { index ->
            trailState.trail += TrailPoint(index.toFloat(), 0f)
        }
        trailState.trailLastX = 0f
        trailState.trailLastY = 0f
        trailState.coreX = 22f
        trailState.setVelocityForTesting(100f, 0f)
        trailState.sampleFluxTrail()

        assertEquals(MutableGameState.MAX_TRAIL_POINTS, trailState.trail.size)
        assertEquals(1f, trailState.trail.first().x)
        assertEquals(22f, trailState.trail.last().x)
    }

    @Test
    fun fullSemanticOutputBatchIsBoundedToThreeAndKeepsItsOrdering() {
        val state = initialEngineState(
            seed = 705,
            bootstrapProgress = null,
            initialMatter = 0,
        ).model
        state.pendingBankedMatter = 9L
        state.emitVisualFx(VisualFxCue.ShockwaveAdded(1f, 2f, 0.3f, 40f, 2))
        state.emitSound(AudioCue.DASH)

        val reduction = assertIs<GameReductionResult.Accepted>(
            GameReducer().reduce(EngineState(state), GameplayAction.FrameElapsed(0f)),
        ).value

        assertEquals(3, reduction.effects.size)
        assertIs<GameEffect.EmitVisualFx>(reduction.effects[0])
        assertIs<GameEffect.PublishProgress>(reduction.effects[1])
        assertIs<GameEffect.AdvanceAudio>(reduction.effects[2])
    }
}

private data class TraceCheckpoint(
    val revision: ULong,
    val elapsedSteps: Int,
    val coreXCentipixels: Int,
    val coreYCentipixels: Int,
    val velocityXCentipixelsPerSecond: Int,
    val velocityYCentipixelsPerSecond: Int,
    val heatCentipercent: Int,
    val hpCentipercent: Int,
    val enemyCount: Int,
    val firstEnemy: String,
    val lastEnemy: String,
    val projectileCount: Int,
    val pickupCount: Int,
    val trailCount: Int,
    val effectOrder: String,
    val visualCueCount: Int,
    val audioCues: String,
)

private fun GameDispatchResult.Committed.toTraceCheckpoint(): TraceCheckpoint {
    val model = snapshot.renderModel
    return TraceCheckpoint(
        revision = snapshot.revision,
        elapsedSteps = (model.elapsed / MutableGameState.FIXED_STEP).roundToInt(),
        coreXCentipixels = model.coreX.centi(),
        coreYCentipixels = model.coreY.centi(),
        velocityXCentipixelsPerSecond = model.velocityX.centi(),
        velocityYCentipixelsPerSecond = model.velocityY.centi(),
        heatCentipercent = model.heat.centi(),
        hpCentipercent = model.hp.centi(),
        enemyCount = model.enemies.size,
        firstEnemy = model.enemies.first().traceToken(),
        lastEnemy = model.enemies.last().traceToken(),
        projectileCount = model.projectiles.size,
        pickupCount = model.pickups.size,
        trailCount = model.trail.size,
        effectOrder = effects.joinToString(separator = ">") { effect ->
            when (effect) {
                is GameEffect.EmitVisualFx -> "FX"
                is GameEffect.PublishProgress -> "PROGRESS"
                is GameEffect.AdvanceAudio -> "AUDIO"
                GameEffect.EnsureAudioUnlocked -> "UNLOCK_AUDIO"
            }
        },
        visualCueCount = effects.filterIsInstance<GameEffect.EmitVisualFx>().sumOf { it.cues.size },
        audioCues = effects.filterIsInstance<GameEffect.AdvanceAudio>()
            .flatMap { it.cues }
            .joinToString(separator = ",") { it.name },
    )
}

private fun kinetickk.feature.gameplay.domain.renderModel.EnemyProjection.traceToken(): String =
    "$id:${type.name}:${x.centi()}:${y.centi()}:${vx.centi()}:${vy.centi()}:${hp.centi()}"

private fun Float.centi(): Int = (this * 100f).roundToInt()

private fun cueToken(cue: VisualFxCue): String = when (cue) {
    VisualFxCue.ClearAll -> "Q"
    VisualFxCue.ClearWeaponArcs -> "C"
    is VisualFxCue.MotionSample -> "M"
    is VisualFxCue.EffectsAdvanced -> "E"
    is VisualFxCue.WeaponArcsAdvanced -> "A"
    is VisualFxCue.Burst -> "B"
    is VisualFxCue.DirectionalBurst -> "D"
    is VisualFxCue.ShockwaveAdded -> "S"
    is VisualFxCue.DamageNumberAdded -> "N"
    is VisualFxCue.WeaponArcAdded -> "W"
    is VisualFxCue.WorldRebased -> "R"
    is VisualFxCue.VisualCuesDropped -> "X"
}
