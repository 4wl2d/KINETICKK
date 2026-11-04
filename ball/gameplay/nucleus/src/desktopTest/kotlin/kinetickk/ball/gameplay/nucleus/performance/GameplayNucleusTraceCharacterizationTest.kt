// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.performance

import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.nucleus.GameplayDecision
import kinetickk.ball.gameplay.nucleus.GameplayNucleus
import kinetickk.ball.gameplay.nucleus.GameplayNucleusPulse
import kinetickk.ball.gameplay.nucleus.GameplayOutput
import kinetickk.ball.gameplay.nucleus.GameplayState
import kinetickk.ball.gameplay.nucleus.GameplayTickOutputs
import kinetickk.ball.gameplay.nucleus.GameplayTickResult
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderSnapshot
import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState
import kinetickk.ball.gameplay.nucleus.simulation.deepCopy
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.ball.profile.api.ProfileProgressApplied
import kinetickk.ball.profile.api.ProfileRevision
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Characterizes the nucleus decision path by state fingerprints: [GameplayNucleus.decide] over a scripted tape of
 * pointer sweeps, brakes, dashes, skills, Apex, choices, a pause, a viewport change and a 144 Hz sub-step segment, for
 * three seeds. Every 60 frames it prints the revision, the canonical state fingerprint, a render digest and rolling
 * hashes of the FX and audio outputs; a fork deep-copied at frame 900 must replay the same 300 frames identically.
 * A parallel copy runs every pulse through the production [GameplayNucleus.tick] and its snapshot rule; after every
 * pulse its outputs and revision, and after every frame its state fingerprint and whole render model, must equal the
 * pure path's (every stored render fact, as [exactRenderValueFacts] reads them).
 */
class GameplayNucleusTraceCharacterizationTest {
    @Test
    fun nucleusDecisionTraceIsStable() {
        for (seed in NUCLEUS_TRACE_SEEDS) {
            val main = NucleusTraceRunner(seed, authorityState(newRunningState(seed)))
            var fork: NucleusTraceRunner? = null
            var forkEqual = true
            for (frame in 0 until NUCLEUS_TRACE_FRAMES) {
                if (frame == FORK_FRAME) fork = main.fork()
                main.step(frame)
                fork?.let { copy ->
                    if (frame < FORK_FRAME + FORK_FRAMES) {
                        copy.step(frame)
                        forkEqual = forkEqual && copy.witness() == main.witness()
                    }
                }
                if ((frame + 1) % PRINT_INTERVAL == 0) println("seed=$seed f=${frame + 1} ${main.summary()}")
            }
            println("seed=$seed fork-equal=$forkEqual")
            println("seed=$seed final ${main.summary()} choices=${main.choices} rerolls=${main.rerolls} rejected=${main.rejected}")
            assertTrue(forkEqual, "A deep-copied fork diverged from its source for seed $seed")
            assertTrue(main.choices > 0, "Seed $seed never selected a choice")
        }
    }
}

private val NUCLEUS_TRACE_SEEDS = intArrayOf(0x4B1D, 731_991, 17)
private const val NUCLEUS_TRACE_FRAMES = 1_800
private const val PRINT_INTERVAL = 60
private const val FORK_FRAME = 900
private const val FORK_FRAMES = 300

private class NucleusTraceRunner(
    private val seed: Int,
    private var state: GameplayState,
) {
    private var fxHash = NUCLEUS_FNV_OFFSET
    private var audioHash = NUCLEUS_FNV_OFFSET
    private var progress = 0
    private var choiceFrames = 0
    private var rerolled = false
    private var cursorAngle = (seed and 7).toFloat()
    private var ticked = state.copy(engine = EngineState(checkNotNull(state.engine).model.deepCopy()))
    private var tickedSnapshot: GameplayRenderSnapshot = GameplayNucleus.renderSnapshot(ticked)
    private val tickOutputs = GameplayTickOutputs()
    var choices = 0
        private set
    var rerolls = 0
        private set
    var rejected = 0
        private set

    /** An independent runner on a deep copy of this one's committed state and counters. */
    fun fork(): NucleusTraceRunner {
        val engine = checkNotNull(state.engine)
        return NucleusTraceRunner(seed, state.copy(engine = EngineState(engine.model.deepCopy()))).also { copy ->
            copy.fxHash = fxHash
            copy.audioHash = audioHash
            copy.progress = progress
            copy.choiceFrames = choiceFrames
            copy.rerolled = rerolled
            copy.cursorAngle = cursorAngle
            copy.choices = choices
            copy.rerolls = rerolls
            copy.rejected = rejected
        }
    }

    /** The scripted pulses of [frame], ending with its FrameElapsed. */
    fun step(frame: Int) {
        val model = checkNotNull(state.engine).model
        if (model.phase == GamePhase.CHOICE) {
            choiceFrames++
            if (choiceFrames == 3 && !rerolled) {
                rerolled = true
                rerolls++
                decide(GameplayInteractionPulse.ChoicesRerolled)
            } else if (choiceFrames >= 5) {
                choiceFrames = 0
                choices++
                decide(GameplayInteractionPulse.ChoiceSelected.fromValidated(0))
            }
        } else {
            choiceFrames = 0
        }
        when (frame % 300) {
            100 -> decide(GameplayInteractionPulse.BrakeChanged(BrakeSource.KEYBOARD, true))
            130 -> decide(GameplayInteractionPulse.BrakeChanged(BrakeSource.KEYBOARD, false))
            200 -> decide(GameplayInteractionPulse.BrakeChanged(BrakeSource.SECONDARY_POINTER, true))
            210 -> decide(GameplayInteractionPulse.BrakeChanged(BrakeSource.SECONDARY_POINTER, false))
        }
        if (frame % 97 == 50) decide(GameplayInteractionPulse.DashRequested)
        when (frame % 240) {
            40 -> decide(GameplayInteractionPulse.SkillRequested.fromValidated(0))
            41 -> decide(GameplayInteractionPulse.SkillRequested.fromValidated(1))
            42 -> decide(GameplayInteractionPulse.SkillRequested.fromValidated(2))
        }
        if (frame % 600 == 300) decide(GameplayInteractionPulse.ApexRequested)
        if (frame == 450 || frame == 480) decide(GameplayInteractionPulse.PauseToggled)
        if (frame == 600) decide(GameplayInteractionPulse.ViewportChanged.fromValidated(1_440f, 810f, 1.25f))
        val sweep = checkNotNull(state.engine).model
        val target = steeringTarget(sweep)
        cursorAngle += if (target == null) {
            0.05f
        } else {
            val desired = atan2(target.second - sweep.coreY, target.first - sweep.coreX)
            atan2(sin(desired - cursorAngle), cos(desired - cursorAngle)).coerceIn(-0.25f, 0.25f)
        }
        val reach = minOf(sweep.screenWidth, sweep.screenHeight) * 0.4f
        decide(
            GameplayInteractionPulse.PointerMoved.fromValidated(
                (sweep.coreX - sweep.cameraX + sweep.screenWidth * 0.5f + cos(cursorAngle) * reach).coerceIn(0f, sweep.screenWidth),
                (sweep.coreY - sweep.cameraY + sweep.screenHeight * 0.5f + sin(cursorAngle) * reach).coerceIn(0f, sweep.screenHeight),
            ),
        )
        val delta = if (frame in SUB_STEP_FRAMES) 1f / 144f else 1f / 60f
        decide(GameplayInteractionPulse.FrameElapsed.fromValidated(delta))
        assertTickParity(frame)
    }

    private fun decide(pulse: GameplayInteractionPulse) {
        val decision = GameplayNucleus.decide(state, GameplayNucleusPulse.Intent(pulse))
        val tick = GameplayNucleus.tick(ticked, pulse, tickOutputs)
        when (decision) {
            is GameplayDecision.Rejected -> {
                rejected++
                fxHash = nucleusFnvString(fxHash, "R:${decision.reason}")
                assertEquals(decision.reason, assertIs<GameplayTickResult.Rejected>(tick, "seed=$seed $pulse").reason)
            }
            is GameplayDecision.Accepted -> {
                state = decision.frame.nextState
                var sentProgress = false
                var visualFx = emptyList<VisualFxCue>()
                var audio = emptyList<GameplayAudioCue>()
                var audioDeltaBits: Int? = null
                var unlock = false
                var update: GameplayProgressUpdate? = null
                decision.frame.outputs.forEach { output ->
                    when (output) {
                        is GameplayOutput.EmitVisualFx -> {
                            visualFx = output.cues
                            output.cues.forEach { cue -> fxHash = nucleusFnvString(fxHash, cue.toString()) }
                        }
                        is GameplayOutput.AdvanceAudio -> {
                            audio = output.cues
                            audioDeltaBits = output.realDeltaSeconds.toRawBits()
                            audioHash = nucleusFnvInt(audioHash, output.realDeltaSeconds.toRawBits())
                            output.cues.forEach { cue -> audioHash = nucleusFnvString(audioHash, cue.name) }
                        }
                        GameplayOutput.EnsureAudioUnlocked -> {
                            unlock = true
                            audioHash = nucleusFnvString(audioHash, "unlock")
                        }
                        is GameplayOutput.SendProfileCommand -> {
                            update = output.update
                            sentProgress = true
                        }
                        else -> error("Unexpected interaction output $output")
                    }
                }
                acceptTick(pulse, tick)
                assertEquals(visualFx, tickOutputs.visualFxCues.toList(), "seed=$seed $pulse fx")
                assertEquals(audio, tickOutputs.audioCues.toList(), "seed=$seed $pulse audio")
                assertEquals(unlock, tickOutputs.ensuresAudioUnlocked, "seed=$seed $pulse unlock")
                assertEquals(update, tickOutputs.progressUpdate, "seed=$seed $pulse progress")
                val frameDeltaBits = if (tickOutputs.frameAccepted) tickOutputs.frameRealDeltaSeconds.toRawBits() else null
                if (pulse is GameplayInteractionPulse.FrameElapsed) {
                    assertEquals(audioDeltaBits, frameDeltaBits, "seed=$seed $pulse music")
                } else {
                    assertEquals(null, frameDeltaBits, "seed=$seed $pulse music")
                }
                if (sentProgress) {
                    progress++
                    val completed = GameplayNucleusPulse.ProgressApplied(ProfileProgressApplied(ProfileRevision(progress.toLong())))
                    state = (GameplayNucleus.decide(state, completed) as GameplayDecision.Accepted).frame.nextState
                    val before = ticked
                    ticked = (GameplayNucleus.decide(ticked, completed) as GameplayDecision.Accepted).frame.nextState
                    tickedSnapshot = GameplayNucleus.reuseRenderSnapshot(ticked, before, tickedSnapshot)
                }
                assertEquals(state.revision, ticked.revision, "seed=$seed $pulse revision")
                assertEquals(state.phase, ticked.phase, "seed=$seed $pulse phase")
                assertEquals(state.progressPending, ticked.progressPending, "seed=$seed $pulse pending")
            }
        }
    }

    /** Commits an accepted tick the way the owner does: restamp a render-neutral one, project the rest against the last. */
    private fun acceptTick(pulse: GameplayInteractionPulse, tick: GameplayTickResult) {
        val before = ticked
        ticked = assertIs<GameplayTickResult.Accepted>(tick, "seed=$seed $pulse").nextState
        tickedSnapshot = if (tickOutputs.renderNeutral) {
            GameplayNucleus.reuseRenderSnapshot(ticked, before, tickedSnapshot)
        } else {
            GameplayNucleus.renderSnapshot(ticked, tickedSnapshot)
        }
    }

    /** The ticked copy matches the pure path: state fingerprint, and every field of the published render model. */
    private fun assertTickParity(frame: Int) {
        val pure = checkNotNull(state.engine).model
        assertEquals(
            canonicalStateFingerprint(pure),
            canonicalStateFingerprint(checkNotNull(ticked.engine).model),
            "seed=$seed f=$frame state fingerprint",
        )
        val pureRender = checkNotNull(GameplayNucleus.renderSnapshot(state).renderModel)
        val tickRender = checkNotNull(tickedSnapshot.renderModel)
        assertEquals(nucleusRenderDigest(pureRender), nucleusRenderDigest(tickRender), "seed=$seed f=$frame render digest")
        val expected = exactRenderValueFacts(pureRender)
        val actual = exactRenderValueFacts(tickRender)
        if (expected != actual) {
            val (want, got) = expected.zip(actual).first { (want, got) -> want != got }
            fail("seed=$seed f=$frame render ${want.first}: expected ${want.second}, ticked ${got.second}")
        }
    }

    /** Every printed fact of the current state, for the fork comparison. */
    fun witness(): String = summary()

    fun summary(): String {
        val model = checkNotNull(state.engine).model
        val render = checkNotNull(GameplayNucleus.renderSnapshot(state).renderModel)
        return "rev=${state.revision.value} phase=${state.phase} state=${canonicalStateFingerprint(model).nucleusHex()} " +
            "render=${nucleusRenderDigest(render)} fx=${fxHash.nucleusHex()} audio=${audioHash.nucleusHex()} progress=$progress"
    }
}

private val SUB_STEP_FRAMES = 1_200 until 1_380

/** Aim through the nearest pickup, else through the nearest enemy to ram it, as a hand on a mouse would. */
private fun steeringTarget(model: MutableGameState): Pair<Float, Float>? {
    val pickup = model.pickups.minByOrNull { (it.x - model.coreX).let { dx -> dx * dx } + (it.y - model.coreY).let { dy -> dy * dy } }
    val enemy = model.enemies.minByOrNull { (it.x - model.coreX).let { dx -> dx * dx } + (it.y - model.coreY).let { dy -> dy * dy } }
    val (x, y) = pickup?.let { it.x to it.y } ?: enemy?.let { it.x to it.y } ?: return null
    val towardX = x - model.coreX
    val towardY = y - model.coreY
    val length = sqrt(towardX * towardX + towardY * towardY).coerceAtLeast(1f)
    return x + towardX / length * 300f to y + towardY / length * 300f
}

/** FNV-1a 64 over the raw bits of a fixed list of render facts. */
private fun nucleusRenderDigest(render: GameplayRenderModel): String {
    var hash = NUCLEUS_FNV_OFFSET
    hash = nucleusFnvInt(hash, render.coreX.toRawBits())
    hash = nucleusFnvInt(hash, render.coreY.toRawBits())
    hash = nucleusFnvInt(hash, render.velocityX.toRawBits())
    hash = nucleusFnvInt(hash, render.velocityY.toRawBits())
    hash = nucleusFnvInt(hash, render.hp.toRawBits())
    hash = nucleusFnvInt(hash, render.heat.toRawBits())
    hash = nucleusFnvInt(hash, render.level)
    hash = nucleusFnvInt(hash, render.data)
    hash = nucleusFnvInt(hash, render.keys)
    hash = nucleusFnvInt(hash, render.elapsed.toRawBits())
    hash = nucleusFnvInt(hash, render.phase.ordinal)
    hash = nucleusFnvInt(hash, render.enemies.size)
    hash = nucleusFnvInt(hash, render.projectiles.size)
    hash = nucleusFnvInt(hash, render.pickups.size)
    hash = nucleusFnvInt(hash, render.sites.size)
    render.enemies.forEach { enemy ->
        hash = nucleusFnvInt(hash, enemy.id)
        hash = nucleusFnvInt(hash, enemy.x.toRawBits())
        hash = nucleusFnvInt(hash, enemy.y.toRawBits())
    }
    return hash.nucleusHex()
}

private const val NUCLEUS_FNV_OFFSET = -3750763034362895579L
private const val NUCLEUS_FNV_PRIME = 1099511628211L

private fun nucleusFnvInt(hash: Long, value: Int): Long {
    var result = hash
    for (shift in 0 until 32 step 8) result = (result xor ((value ushr shift) and 0xFF).toLong()) * NUCLEUS_FNV_PRIME
    return result
}

private fun nucleusFnvString(hash: Long, value: String): Long {
    var result = nucleusFnvInt(hash, value.length)
    for (char in value) result = nucleusFnvInt(result, char.code)
    return result
}

private fun Long.nucleusHex(): String = toULong().toString(16).padStart(16, '0')
