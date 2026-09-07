// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.characterization

import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.nucleus.GameplayAcceptedFrame
import kinetickk.ball.gameplay.nucleus.GameplayContext
import kinetickk.ball.gameplay.nucleus.GameplayDecision
import kinetickk.ball.gameplay.nucleus.GameplayNucleus
import kinetickk.ball.gameplay.nucleus.GameplayOutput
import kinetickk.ball.gameplay.nucleus.GameplayState
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.gameplay.nucleus.GameplayNucleusPulse
import kinetickk.ball.gameplay.nucleus.GameplayStartContext
import kinetickk.ball.gameplay.nucleus.GameplayStartInputs
import kinetickk.ball.gameplay.nucleus.render.EnemyProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.TrailPoint
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState
import kinetickk.ball.gameplay.nucleus.simulation.addPickup
import kinetickk.ball.gameplay.nucleus.simulation.addProjectile
import kinetickk.ball.gameplay.nucleus.simulation.applyPreferences
import kinetickk.ball.gameplay.nucleus.simulation.sampleFluxTrail
import kinetickk.ball.gameplay.nucleus.simulation.setVelocityForTesting
import kinetickk.ball.gameplay.nucleus.simulation.spawnEnemy
import kinetickk.ball.gameplay.nucleus.simulation.startRun
import kinetickk.ball.gameplay.nucleus.simulation.takeSoundCues
import kinetickk.ball.gameplay.nucleus.simulation.takeVisualFxCues
import kinetickk.ball.gameplay.nucleus.simulation.update
import kinetickk.ball.gameplay.nucleus.simulation.updatePointer
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GameplayBaselineCharacterizationTest {
    @Test
    fun seededIntentTraceHasStableCheckpoints() {
        // Edge-based polarity golden; ordinary thrust keeps full authority throughout this trace.
        var state = startCharacterizedRun(seed = 0x4B1D, initialMatter = 17)
        val actions = listOf(
            GameplayInteractionPulse.ViewportChanged.fromValidated(960f, 540f, 1.25f),
            GameplayInteractionPulse.PointerMoved.fromValidated(820f, 135f),
            GameplayInteractionPulse.FrameElapsed.fromValidated(1f / 60f),
            GameplayInteractionPulse.DashRequested,
            GameplayInteractionPulse.FrameElapsed.fromValidated(1f / 30f),
            GameplayInteractionPulse.BrakeChanged(BrakeSource.KEYBOARD, active = true),
            GameplayInteractionPulse.FrameElapsed.fromValidated(0.05f),
            GameplayInteractionPulse.PointerMoved.fromValidated(180f, 430f),
            GameplayInteractionPulse.BrakeChanged(BrakeSource.KEYBOARD, active = false),
            GameplayInteractionPulse.FrameElapsed.fromValidated(0.1f),
            GameplayInteractionPulse.FrameElapsed.fromValidated(0.075f),
            GameplayInteractionPulse.FrameElapsed.fromValidated(0.1f),
            GameplayInteractionPulse.FrameElapsed.fromValidated(0.1f),
            GameplayInteractionPulse.FrameElapsed.fromValidated(0.1f),
        )

        val checkpoints = actions.mapNotNull { action ->
            val frame = assertIs<GameplayDecision.Accepted>(
                GameplayNucleus.decide(state, GameplayNucleusPulse.Intent(action)),
            ).frame
            state = frame.nextState
            if (action is GameplayInteractionPulse.FrameElapsed) frame.toTraceCheckpoint() else null
        }

        assertEquals(
            listOf(
                TraceCheckpoint(
                    revision = 4,
                    elapsedSteps = 2,
                    coreXCentipixels = 35,
                    coreYCentipixels = -14,
                    velocityXCentipixelsPerSecond = 2767,
                    velocityYCentipixelsPerSecond = -1099,
                    heatCentipercent = 0,
                    hpCentipercent = 10000,
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
                    revision = 6,
                    elapsedSteps = 6,
                    coreXCentipixels = 2057,
                    coreYCentipixels = -817,
                    velocityXCentipixelsPerSecond = 62462,
                    velocityYCentipixelsPerSecond = -24801,
                    heatCentipercent = 3553,
                    hpCentipercent = 10000,
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
                    revision = 8,
                    elapsedSteps = 13,
                    coreXCentipixels = 5430,
                    coreYCentipixels = -2156,
                    velocityXCentipixelsPerSecond = 54595,
                    velocityYCentipixelsPerSecond = -21677,
                    heatCentipercent = 3375,
                    hpCentipercent = 10000,
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
                    revision = 11,
                    elapsedSteps = 27,
                    coreXCentipixels = 10477,
                    coreYCentipixels = -4017,
                    velocityXCentipixelsPerSecond = 33472,
                    velocityYCentipixelsPerSecond = -11021,
                    heatCentipercent = 3154,
                    hpCentipercent = 10000,
                    enemyCount = 6,
                    firstEnemy = "1:DRIFTER:68797:47781:-2292:-1833:3000",
                    lastEnemy = "6:DRIFTER:1747:45060:68:-393:3002",
                    projectileCount = 0,
                    pickupCount = 0,
                    trailCount = 5,
                    effectOrder = "FX>AUDIO",
                    visualCueCount = 42,
                    audioCues = "",
                ),
                TraceCheckpoint(
                    revision = 12,
                    elapsedSteps = 37,
                    coreXCentipixels = 12591,
                    coreYCentipixels = -4597,
                    velocityXCentipixelsPerSecond = 18824,
                    velocityYCentipixelsPerSecond = -3683,
                    heatCentipercent = 2995,
                    hpCentipercent = 10000,
                    enemyCount = 6,
                    firstEnemy = "1:DRIFTER:68580:47603:-2848:-2384:3000",
                    lastEnemy = "6:DRIFTER:1764:44972:306:-1557:3002",
                    projectileCount = 0,
                    pickupCount = 0,
                    trailCount = 6,
                    effectOrder = "FX>AUDIO",
                    visualCueCount = 30,
                    audioCues = "",
                ),
                TraceCheckpoint(
                    revision = 13,
                    elapsedSteps = 51,
                    coreXCentipixels = 13568,
                    coreYCentipixels = -4417,
                    velocityXCentipixelsPerSecond = -434,
                    velocityYCentipixelsPerSecond = 5932,
                    heatCentipercent = 2774,
                    hpCentipercent = 10000,
                    enemyCount = 6,
                    firstEnemy = "1:DRIFTER:68208:47283:-3461:-3030:3000",
                    lastEnemy = "6:DRIFTER:1820:44706:624:-2869:3002",
                    projectileCount = 0,
                    pickupCount = 0,
                    trailCount = 6,
                    effectOrder = "FX>AUDIO",
                    visualCueCount = 42,
                    audioCues = "",
                ),
                TraceCheckpoint(
                    revision = 14,
                    elapsedSteps = 65,
                    coreXCentipixels = 12403,
                    coreYCentipixels = -3170,
                    velocityXCentipixelsPerSecond = -17988,
                    velocityYCentipixelsPerSecond = 14679,
                    heatCentipercent = 2552,
                    hpCentipercent = 10000,
                    enemyCount = 6,
                    firstEnemy = "1:DRIFTER:67772:46897:-3952:-3524:3000",
                    lastEnemy = "6:DRIFTER:1909:44304:872:-3897:3002",
                    projectileCount = 0,
                    pickupCount = 0,
                    trailCount = 7,
                    effectOrder = "FX>AUDIO",
                    visualCueCount = 42,
                    audioCues = "",
                ),
                TraceCheckpoint(
                    revision = 15,
                    elapsedSteps = 79,
                    coreXCentipixels = 9299,
                    coreYCentipixels = -957,
                    velocityXCentipixelsPerSecond = -33815,
                    velocityYCentipixelsPerSecond = 22558,
                    heatCentipercent = 2330,
                    hpCentipercent = 10000,
                    enemyCount = 6,
                    firstEnemy = "1:DRIFTER:67284:46463:-4379:-3862:3000",
                    lastEnemy = "6:DRIFTER:2020:43797:1002:-4714:3002",
                    projectileCount = 0,
                    pickupCount = 0,
                    trailCount = 9,
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
        val state = MutableGameState(content = canonicalGameplayContent, seed = 700, initialMatter = 0)
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
        val maximumActiveEnemies = canonicalGameplayContent.rebirth.maxActiveEnemies
        assertEquals(120, maximumActiveEnemies)
        assertEquals(650, MutableGameState.MAX_PROJECTILES)
        assertEquals(420, MutableGameState.MAX_PICKUPS)
        assertEquals(110, MutableGameState.MAX_TRAIL_POINTS)

        val enemyState = MutableGameState(content = canonicalGameplayContent, seed = 701, initialMatter = 0)
        val enemyResults = List(maximumActiveEnemies + 1) {
            enemyState.spawnEnemy(EnemyType.DRIFTER)
        }
        assertTrue(enemyResults.take(maximumActiveEnemies).all { it })
        assertFalse(enemyResults.last())
        assertEquals(maximumActiveEnemies, enemyState.enemies.size)

        val projectileState = MutableGameState(content = canonicalGameplayContent, seed = 702, initialMatter = 0)
        repeat(MutableGameState.MAX_PROJECTILES + 1) { index ->
            projectileState.addProjectile(
                Projectile(index.toFloat(), 0f, 0f, 0f, radius = 1f, life = 1f),
            )
        }
        assertEquals(MutableGameState.MAX_PROJECTILES, projectileState.projectiles.size)
        assertEquals((MutableGameState.MAX_PROJECTILES - 1).toFloat(), projectileState.projectiles.last().x)

        val pickupState = MutableGameState(content = canonicalGameplayContent, seed = 703, initialMatter = 0)
        repeat(MutableGameState.MAX_PICKUPS + 1) { index ->
            pickupState.addPickup(Pickup(PickupType.DATA, index.toFloat(), 0f))
        }
        assertEquals(MutableGameState.MAX_PICKUPS, pickupState.pickups.size)
        assertEquals((MutableGameState.MAX_PICKUPS - 1).toFloat(), pickupState.pickups.last().x)

        val trailState = MutableGameState(content = canonicalGameplayContent, seed = 704, initialMatter = 0)
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
    fun enemyCapComesFromTheCapturedGameplaySnapshot() {
        val capturedContent = canonicalGameplayContent.copy(
            rebirth = canonicalGameplayContent.rebirth.copy(maxActiveEnemies = 3),
        )
        val state = MutableGameState(content = capturedContent, seed = 706, initialMatter = 0)

        val spawnResults = List(4) { state.spawnEnemy(EnemyType.DRIFTER) }

        assertEquals(listOf(true, true, true, false), spawnResults)
        assertEquals(3, state.enemies.size)
    }

}

private data class TraceCheckpoint(
    val revision: Long,
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

private fun GameplayAcceptedFrame.toTraceCheckpoint(): TraceCheckpoint {
    val model = GameplayNucleus.renderSnapshot(nextState).renderModel!!
    return TraceCheckpoint(
        revision = nextState.revision.value,
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
        effectOrder = outputs.joinToString(separator = ">") { output ->
            when (output) {
                is GameplayOutput.EmitVisualFx -> "FX"
                is GameplayOutput.SendProfileCommand -> "PROGRESS"
                is GameplayOutput.AdvanceAudio -> "AUDIO"
                GameplayOutput.EnsureAudioUnlocked -> "UNLOCK_AUDIO"
                is GameplayOutput.RunExited, is GameplayOutput.SettingsApplied,
                is GameplayOutput.RunStarted, is GameplayOutput.OverlayPaused,
                -> "COMPLETE"
            }
        },
        visualCueCount = outputs.filterIsInstance<GameplayOutput.EmitVisualFx>().sumOf { it.cues.size },
        audioCues = outputs.filterIsInstance<GameplayOutput.AdvanceAudio>()
            .flatMap { it.cues }
            .joinToString(separator = ",") { it.name },
    )
}

private fun EnemyProjection.traceToken(): String =
    "$id:${type.name}:${x.centi()}:${y.centi()}:${vx.centi()}:${vy.centi()}:${hp.centi()}"

private fun startCharacterizedRun(seed: Int, initialMatter: Long): GameplayState {
    val profile = PlayerProfile(
        preferences = PlayerPreferences(simulationSpeed = 1.15f), // Fixed trace input, independent of the fresh-profile default.
        economy = PlayerEconomy(initialMatter, initialMatter),
    ).toGameplaySnapshot()
    val initial = GameplayState.initial(RunId(0), canonicalGameplayContent)
    return assertIs<GameplayDecision.Accepted>(
        GameplayNucleus.decide(
            initial,
            GameplayNucleusPulse.StartRun,
            GameplayContext(
                start = GameplayStartContext.Ready(
                    GameplayStartInputs(canonicalGameplayContent, profile, seed),
                ),
            ),
        ),
    ).frame.nextState
}

private fun PlayerProfile.toGameplaySnapshot(): GameplayProfileSnapshot = GameplayProfileSnapshot(
    preferences,
    economy,
    loadout,
    labProgress,
    collection,
    rebirthProgress,
)

private fun Float.centi(): Int = (this * 100f).roundToInt()

private fun cueToken(cue: VisualFxCue): String = when (cue) {
    is VisualFxCue.BuildChanged -> "U"
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
