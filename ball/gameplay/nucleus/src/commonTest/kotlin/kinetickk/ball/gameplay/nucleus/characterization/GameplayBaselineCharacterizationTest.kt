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
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
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
import kotlin.test.assertNull
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
            // The production start offers the omen at the end of the first fixed step; the trace takes No omen (card 3).
            GameplayInteractionPulse.ChoiceSelected.fromValidated(3),
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
            if (action is GameplayInteractionPulse.ChoiceSelected) {
                val offer = GameplayNucleus.renderSnapshot(state).renderModel!!
                assertEquals(ChoiceType.OMEN, offer.choiceType)
                assertNull(offer.choices[action.index].omenId)
            }
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
                    elapsedSteps = 1,
                    coreXCentipixels = 18,
                    coreYCentipixels = -7,
                    velocityXCentipixelsPerSecond = 2203,
                    velocityYCentipixelsPerSecond = -875,
                    heatCentipercent = 0,
                    hpCentipercent = 10000,
                    enemyCount = 5,
                    firstEnemy = "1:DRIFTER:85695:-40244:-122:58:2800",
                    lastEnemy = "5:DRIFTER:24811:-56169:-55:124:2800",
                    projectileCount = 0,
                    pickupCount = 0,
                    trailCount = 0,
                    effectOrder = "FX>AUDIO",
                    visualCueCount = 3,
                    audioCues = "",
                ),
                TraceCheckpoint(
                    revision = 7,
                    elapsedSteps = 6,
                    coreXCentipixels = 2616,
                    coreYCentipixels = -1039,
                    velocityXCentipixelsPerSecond = 65776,
                    velocityYCentipixelsPerSecond = -26117,
                    heatCentipercent = 3537,
                    hpCentipercent = 10000,
                    enemyCount = 5,
                    firstEnemy = "1:DRIFTER:85675:-40234:-703:331:2800",
                    lastEnemy = "5:DRIFTER:24803:-56149:-302:716:2800",
                    projectileCount = 0,
                    pickupCount = 0,
                    trailCount = 1,
                    effectOrder = "FX>AUDIO",
                    visualCueCount = 17,
                    audioCues = "DASH",
                ),
                TraceCheckpoint(
                    revision = 9,
                    elapsedSteps = 13,
                    coreXCentipixels = 6155,
                    coreYCentipixels = -2444,
                    velocityXCentipixelsPerSecond = 57131,
                    velocityYCentipixelsPerSecond = -22684,
                    heatCentipercent = 3359,
                    hpCentipercent = 10000,
                    enemyCount = 5,
                    firstEnemy = "1:DRIFTER:85609:-40203:-1435:678:2800",
                    lastEnemy = "5:DRIFTER:24776:-56081:-579:1477:2800",
                    projectileCount = 0,
                    pickupCount = 0,
                    trailCount = 3,
                    effectOrder = "FX>AUDIO",
                    visualCueCount = 21,
                    audioCues = "",
                ),
                TraceCheckpoint(
                    revision = 12,
                    elapsedSteps = 27,
                    coreXCentipixels = 11560,
                    coreYCentipixels = -4391,
                    velocityXCentipixelsPerSecond = 37120,
                    velocityYCentipixelsPerSecond = -11549,
                    heatCentipercent = 3138,
                    hpCentipercent = 10000,
                    enemyCount = 5,
                    firstEnemy = "1:DRIFTER:85363:-40086:-2651:1264:2800",
                    lastEnemy = "5:DRIFTER:24684:-55825:-931:2782:2800",
                    projectileCount = 0,
                    pickupCount = 0,
                    trailCount = 5,
                    effectOrder = "FX>AUDIO",
                    visualCueCount = 42,
                    audioCues = "",
                ),
                TraceCheckpoint(
                    revision = 13,
                    elapsedSteps = 37,
                    coreXCentipixels = 14045,
                    coreYCentipixels = -5002,
                    velocityXCentipixelsPerSecond = 24103,
                    velocityYCentipixelsPerSecond = -3933,
                    heatCentipercent = 2979,
                    hpCentipercent = 10000,
                    enemyCount = 5,
                    firstEnemy = "1:DRIFTER:85109:-39965:-3351:1610:2800",
                    lastEnemy = "5:DRIFTER:24600:-55557:-1059:3556:2800",
                    projectileCount = 0,
                    pickupCount = 0,
                    trailCount = 6,
                    effectOrder = "FX>AUDIO",
                    visualCueCount = 30,
                    audioCues = "",
                ),
                TraceCheckpoint(
                    revision = 14,
                    elapsedSteps = 51,
                    coreXCentipixels = 15981,
                    coreYCentipixels = -4756,
                    velocityXCentipixelsPerSecond = 11525,
                    velocityYCentipixelsPerSecond = 8420,
                    heatCentipercent = 2758,
                    hpCentipercent = 10000,
                    enemyCount = 5,
                    firstEnemy = "1:DRIFTER:84667:-39751:-4138:2019:2800",
                    lastEnemy = "5:DRIFTER:24470:-55084:-1137:4450:2800",
                    projectileCount = 0,
                    pickupCount = 0,
                    trailCount = 7,
                    effectOrder = "FX>AUDIO",
                    visualCueCount = 42,
                    audioCues = "",
                ),
                TraceCheckpoint(
                    revision = 15,
                    elapsedSteps = 65,
                    coreXCentipixels = 16397,
                    coreYCentipixels = -2563,
                    velocityXCentipixelsPerSecond = -5562,
                    velocityYCentipixelsPerSecond = 27991,
                    heatCentipercent = 2536,
                    hpCentipercent = 10000,
                    enemyCount = 5,
                    firstEnemy = "1:DRIFTER:84145:-39493:-4738:2371:2800",
                    lastEnemy = "5:DRIFTER:24337:-54519:-1153:5157:2800",
                    projectileCount = 0,
                    pickupCount = 0,
                    trailCount = 8,
                    effectOrder = "FX>AUDIO",
                    visualCueCount = 42,
                    audioCues = "",
                ),
                TraceCheckpoint(
                    revision = 16,
                    elapsedSteps = 79,
                    coreXCentipixels = 14219,
                    coreYCentipixels = 1823,
                    velocityXCentipixelsPerSecond = -30324,
                    velocityYCentipixelsPerSecond = 44983,
                    heatCentipercent = 2314,
                    hpCentipercent = 10000,
                    enemyCount = 5,
                    firstEnemy = "1:DRIFTER:83563:-39195:-5181:2696:2800",
                    lastEnemy = "5:DRIFTER:24201:-53881:-1176:5709:2800",
                    projectileCount = 0,
                    pickupCount = 0,
                    trailCount = 10,
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
    matrix,
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
    is VisualFxCue.StatusBroken -> "K"
    is VisualFxCue.ReactionTriggered -> "T"
    is VisualFxCue.WeaponArcAdded -> "W"
    is VisualFxCue.SkillCast -> "Z"
    is VisualFxCue.ApexActivated -> "V"
    is VisualFxCue.WorldRebased -> "R"
    is VisualFxCue.VisualCuesDropped -> "X"
}
