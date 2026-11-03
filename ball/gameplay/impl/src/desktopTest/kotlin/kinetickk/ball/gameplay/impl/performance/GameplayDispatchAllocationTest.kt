// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl.performance

import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplayRunStarted
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.api.RunMode
import kinetickk.ball.gameplay.impl.GameComponent
import kinetickk.ball.gameplay.impl.GameplayAudioExecutor
import kinetickk.ball.gameplay.impl.GameplayCommandTestCaller
import kinetickk.ball.gameplay.impl.SyntheticGameplayContent
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.gameplay.interaction.fx.InteractionFxReducer
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.ball.profile.api.HangarProjection
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.LoadoutProjection
import kinetickk.ball.profile.api.MatrixProgressProjection
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileProgress
import kinetickk.ball.profile.api.ProfileProgressApplied
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileReadPort
import kinetickk.ball.profile.api.ProfileRefusal
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.dispatch.InlineReply
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Bytes the owner allocates per dispatched gameplay frame, and per FX snapshot, measured before the in-place tick and
 * the FX snapshot work optimise them. Each ceiling is the measured value plus a quarter; a ceiling may only go down.
 * With `KK_ALLOCATION_OUT=<file>` the measured values are appended to that file (never printed).
 */
class GameplayDispatchAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    private val frame = GameplayInteractionPulse.FrameElapsed.fromValidated(1f / 60f)

    @Test
    fun idleRunningFrameDispatchStaysUnderItsCeiling() {
        val bytes = bestBytesPerFrame(SyntheticGameplayContent, settleFrames = IDLE_SETTLE_FRAMES)
        record("idle-running-frame", bytes)
        assertTrue(bytes <= IDLE_FRAME_CEILING_BYTES, "An idle running frame allocates $bytes bytes, above $IDLE_FRAME_CEILING_BYTES")
    }

    @Test
    fun capacityFrameDispatchStaysUnderItsCeiling() {
        val bytes = bestBytesPerFrame(AllocationCapacityContent, settleFrames = CAPACITY_SETTLE_FRAMES) { component ->
            check(checkNotNull(component.renderSnapshot().renderModel).enemies.size >= CAPACITY_MIN_ENEMIES) {
                "The capacity scene holds only ${component.renderSnapshot().renderModel?.enemies?.size} enemies"
            }
        }
        record("capacity-frame", bytes)
        assertTrue(bytes <= CAPACITY_FRAME_CEILING_BYTES, "A capacity frame allocates $bytes bytes, above $CAPACITY_FRAME_CEILING_BYTES")
    }

    @Test
    fun visualFxSnapshotWithFullParticlesStaysUnderItsCeiling() {
        val thread = Thread.currentThread().id
        val advance = immutableListOf<VisualFxCue>(VisualFxCue.EffectsAdvanced(FX_ADVANCE_SECONDS))
        val burst = immutableListOf<VisualFxCue>(VisualFxCue.Burst(0f, 0f, 64, 1, ParticleDensity.HIGH))
        var best = Long.MAX_VALUE
        repeat(FX_PASSES) { pass ->
            val reducer = InteractionFxReducer(FX_SEED + pass)
            var fills = 0
            while (reducer.snapshot().particles.size < FULL_PARTICLES) {
                check(fills++ < 100) { "The FX reducer never filled its particle budget" }
                reducer.apply(burst)
            }
            repeat(FX_WARM_SNAPSHOTS) {
                reducer.apply(advance)
                reducer.snapshot()
            }
            var total = 0L
            var empty = 0L
            repeat(FX_MEASURED_SNAPSHOTS) {
                reducer.apply(advance)
                val start = threads.getThreadAllocatedBytes(thread)
                val middle = threads.getThreadAllocatedBytes(thread)
                reducer.snapshot()
                val end = threads.getThreadAllocatedBytes(thread)
                empty += middle - start
                total += end - middle
            }
            assertEquals(FULL_PARTICLES, reducer.snapshot().particles.size)
            best = minOf(best, (total - empty) / FX_MEASURED_SNAPSHOTS)
        }
        record("visual-fx-snapshot-700", best)
        assertTrue(
            best <= FX_SNAPSHOT_CEILING_BYTES,
            "An FX snapshot of $FULL_PARTICLES particles allocates $best bytes, above $FX_SNAPSHOT_CEILING_BYTES",
        )
    }

    /** The fewest bytes per accepted 60 Hz frame over fresh runs, after warm-up runs and [settleFrames] per run. */
    private fun bestBytesPerFrame(
        content: GameplayContentSnapshot,
        settleFrames: Int,
        verify: (GameComponent) -> Unit = {},
    ): Long {
        val thread = Thread.currentThread().id
        repeat(WARM_RUNS) {
            val warm = runningComponent(content)
            repeat(settleFrames + MEASURED_FRAMES) { accept(warm) }
        }
        var best = Long.MAX_VALUE
        repeat(MEASURED_PASSES) {
            val component = runningComponent(content)
            repeat(settleFrames) { accept(component) }
            verify(component)
            val empty = threads.getThreadAllocatedBytes(thread).let { threads.getThreadAllocatedBytes(thread) - it }
            val start = threads.getThreadAllocatedBytes(thread)
            repeat(MEASURED_FRAMES) { component.accept(frame) }
            val total = threads.getThreadAllocatedBytes(thread) - start - empty
            check(component.stateSnapshot().phase == GameplayRunPhase.RUNNING) {
                "The measured run left RUNNING (${component.stateSnapshot().phase})"
            }
            best = minOf(best, total / MEASURED_FRAMES)
        }
        return best
    }

    private fun accept(component: GameComponent) {
        check(component.accept(frame) is GameplayAcceptance.Accepted)
    }

    /** Started, its run Omen declined, running with no other input. */
    private fun runningComponent(content: GameplayContentSnapshot): GameComponent {
        val component = GameComponent.create(
            runId = RunId(51),
            content = content,
            profilePort = AllocationProfilePort,
            profileProgress = AllocationProfilePort,
            audioExecutor = AllocationAudioExecutor,
            seed = ALLOCATION_SEED,
        )
        val start = GameplayCommandTestCaller<GameplayRunStarted>()
        start.call { reply -> component.startRun(RunMode.STANDARD, reply) }
        check(start.applied.size == 1)
        accept(component)
        val choices = checkNotNull(component.renderSnapshot().renderModel).choices
        check(component.stateSnapshot().phase == GameplayRunPhase.CHOICE && choices.last().omenId == null) {
            "The first frame did not open the run Omen choice"
        }
        check(component.accept(GameplayInteractionPulse.ChoiceSelected.fromValidated(choices.lastIndex)) is GameplayAcceptance.Accepted)
        check(component.stateSnapshot().phase == GameplayRunPhase.RUNNING)
        return component
    }

    private fun record(name: String, bytes: Long) {
        val out = System.getenv("KK_ALLOCATION_OUT") ?: return
        File(out).appendText("$name bytes=$bytes\n")
    }
}

private const val ALLOCATION_SEED = 731_991
private const val WARM_RUNS = 2
private const val MEASURED_PASSES = 3
private const val MEASURED_FRAMES = 60
private const val IDLE_SETTLE_FRAMES = 240
private const val CAPACITY_SETTLE_FRAMES = 360
private const val CAPACITY_MIN_ENEMIES = 100
private const val FULL_PARTICLES = 700
private const val FX_SEED = 0x4B1D
private const val FX_PASSES = 3
private const val FX_WARM_SNAPSHOTS = 400
private const val FX_MEASURED_SNAPSHOTS = 100
private const val FX_ADVANCE_SECONDS = 0.0001f

// Measured at bdc061ce (14,136, 49,210 and 36,520 bytes), each times 1.25. Lower them when an optimisation lands; never
// raise them.
private const val IDLE_FRAME_CEILING_BYTES = 17_670L
private const val CAPACITY_FRAME_CEILING_BYTES = 61_513L
private const val FX_SNAPSHOT_CEILING_BYTES = 45_650L

/** A crowd that fills the enemy cap and survives: durable, slow enemies around a Core that takes no damage. */
private val AllocationCapacityContent by lazy {
    val rebirth = SyntheticGameplayContent.rebirth
    SyntheticGameplayContent.copy(
        rebirth = rebirth.copy(
            profiles = rebirth.profiles.map { profile ->
                profile.copy(
                    openingEnemyCount = 120,
                    enemyCapMultiplier = 10f,
                    spawnRateMultiplier = 10f,
                    enemyHealthMultiplier = 1_000f,
                    enemySpeedMultiplier = 0.25f,
                    incomingDamageMultiplier = 0f,
                    maximumActiveEnemies = 120,
                    minimumSpawnIntervalSeconds = 0.09f,
                )
            }.toImmutableList(),
        ),
    )
}

private object AllocationAudioExecutor : GameplayAudioExecutor {
    override fun play(cues: ImmutableList<GameplayAudioCue>) = Unit
    override fun tick(realDeltaSeconds: Float) = Unit
    override fun ensureUnlocked() = Unit
}

private object AllocationProfilePort : ProfileReadPort, ProfileProgress {
    override val instanceId = LOCAL_PROFILE_INSTANCE_ID
    private val revision = ProfileRevision(10)
    private val snapshot = PlayerProfile().let {
        GameplayProfileSnapshot(it.preferences, it.economy, it.loadout, it.matrix, it.collection, it.rebirthProgress)
    }

    override fun applyGameplayProgress(update: GameplayProgressUpdate, reply: InlineReply<ProfileProgressApplied, ProfileRefusal>) =
        reply.accepted(ProfileProgressApplied(revision))

    override fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection =
        RunBootstrapProjection(instanceId, revision, ProfileRunBootstrapResult.Ready(snapshot))

    override fun query(query: ProfileQuery.GetPreferences): PreferencesProjection =
        PreferencesProjection(instanceId, revision, snapshot.preferences)

    override fun query(query: ProfileQuery.GetHomeProgress): HomeProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetCollection): CollectionProjection = error("unused")
    override fun query(query: ProfileQuery.GetMatrixProgress): MatrixProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetHangar): HangarProjection = error("unused")
    override fun query(query: ProfileQuery.GetLoadout): LoadoutProjection = error("unused")
    override fun query(query: ProfileQuery.GetRebirthProgress): RebirthProgressProjection = error("unused")
    override fun query(query: ProfileQuery.GetPersistenceStatus): PersistenceStatusProjection = error("unused")
}
