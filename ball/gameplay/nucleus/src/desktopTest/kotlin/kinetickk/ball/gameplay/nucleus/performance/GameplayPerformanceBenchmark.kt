// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.performance

import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.nucleus.GameplayDecision
import kinetickk.ball.gameplay.nucleus.GameplayNucleus
import kinetickk.ball.gameplay.nucleus.GameplayNucleusPulse
import kinetickk.ball.gameplay.nucleus.GameplayOutput
import kinetickk.ball.gameplay.nucleus.GameplayState
import kinetickk.ball.gameplay.nucleus.characterization.gameScenario
import kinetickk.ball.gameplay.nucleus.model.Obstacle
import kinetickk.ball.gameplay.nucleus.model.CombatField
import kinetickk.ball.gameplay.nucleus.model.CombatFieldKind
import kinetickk.ball.gameplay.nucleus.model.FieldSource
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.TrailPoint
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
import kinetickk.ball.gameplay.nucleus.reducer.GameReducer
import kinetickk.ball.gameplay.nucleus.reducer.GameReductionResult
import kinetickk.ball.gameplay.nucleus.protocol.SimulationOutput
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderSnapshot
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kinetickk.ball.gameplay.nucleus.render.ObstacleProjection
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState
import kinetickk.ball.gameplay.nucleus.simulation.ION_SWARM_DAMAGE
import kinetickk.ball.gameplay.nucleus.simulation.ION_SWARM_PIERCE
import kinetickk.ball.gameplay.nucleus.simulation.addField
import kinetickk.ball.gameplay.nucleus.simulation.beginWeaponActivation
import kinetickk.ball.gameplay.nucleus.simulation.buildTestState
import kinetickk.ball.gameplay.nucleus.simulation.effectiveWeaponPower
import kinetickk.ball.gameplay.nucleus.simulation.firePlayerProjectile
import kinetickk.ball.gameplay.nucleus.simulation.ionOrbitalCount
import kinetickk.ball.gameplay.nucleus.simulation.own
import kinetickk.ball.gameplay.nucleus.simulation.runScale
import kinetickk.ball.gameplay.nucleus.simulation.takeProgressUpdate
import kinetickk.ball.gameplay.nucleus.simulation.addEnemyForTesting
import kinetickk.ball.gameplay.nucleus.simulation.consumeFixedStepBudget
import kinetickk.ball.gameplay.nucleus.simulation.copyForReduction
import kinetickk.ball.gameplay.nucleus.simulation.simulateStep
import kinetickk.ball.gameplay.nucleus.simulation.startRun
import kinetickk.ball.gameplay.nucleus.simulation.takeSoundCues
import kinetickk.ball.gameplay.nucleus.simulation.takeVisualFxCues
import kinetickk.ball.gameplay.nucleus.simulation.toRenderModel
import kinetickk.ball.gameplay.nucleus.simulation.togglePause
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.performance.BenchmarkScenario
import kinetickk.performance.BenchmarkSuiteIdentity
import kinetickk.performance.BenchmarkValidation
import kinetickk.performance.BenchmarkValidationContext
import kinetickk.performance.runBenchmarkSuite
import java.lang.reflect.Modifier

internal const val GAMEPLAY_BENCHMARK_SUITE_VERSION = "gameplay-core-v4"
private const val DEFAULT_SEED = 731_991
private const val MAX_ENEMIES = 120
private const val MAX_PROJECTILES = 650
private const val MAX_PICKUPS = 420
private const val MAX_TRAIL = 110
private const val COLLISION_ENEMIES = 90
private const val COLLISION_PROJECTILES = 325
private const val TRACE_FRAMES = 120
private const val SEMANTIC_OUTPUT_SEED = 1_469_598_103_934_665_603L
private const val SEMANTIC_VISUAL_FX = 1
private const val SEMANTIC_AUDIO = 2
private const val SEMANTIC_PROFILE_UPDATE = 3
private const val SEMANTIC_AUDIO_UNLOCK = 4
private const val SEMANTIC_COMMAND_COMPLETION = 5

fun main() {
    val seed = System.getProperty("kinetickk.benchmark.seed")?.toIntOrNull() ?: DEFAULT_SEED
    runBenchmarkSuite(
        identity = BenchmarkSuiteIdentity(
            suiteVersion = GAMEPLAY_BENCHMARK_SUITE_VERSION,
            adapter = "kinetickk-0.2.0",
            label = System.getProperty("kinetickk.benchmark.label", "current"),
            revision = System.getProperty("kinetickk.benchmark.revision", "unknown"),
            dirty = System.getProperty("kinetickk.benchmark.dirty", "false").toBoolean(),
        ),
        scenarios = gameplayBenchmarkScenarios(seed),
    )
}

internal fun gameplayBenchmarkScenarios(seed: Int = DEFAULT_SEED): List<BenchmarkScenario> =
    GameplayBenchmarkFixtures(seed).scenarios()

private class GameplayBenchmarkFixtures(private val seed: Int) {
    private val frame60 = GameplayInteractionPulse.FrameElapsed.fromValidated(1f / 60f)
    private val frame100Millis = GameplayInteractionPulse.FrameElapsed.fromValidated(0.1f)
    private val pointerMove = GameplayInteractionPulse.PointerMoved.fromValidated(1_024f, 288f)
    private val viewportChange = GameplayInteractionPulse.ViewportChanged.fromValidated(1_920f, 1_080f, 2f)
    private val reducer = GameReducer()
    private val idle = newRunningState(seed)
    private val capacity = capacityState(seed + 1)
    private val collisionMiss = collisionState(seed + 2, hit = false)
    private val collisionHit = collisionState(seed + 3, hit = true)
    private val paused = newRunningState(seed + 4).apply { togglePause() }
    private val idleAuthority = authorityState(idle)
    private val capacityAuthority = authorityState(capacity)
    private val pausedAuthority = authorityState(paused)
    private val reactions = reactionPerformanceState(seed + 10)
    private val fastReactions = reactionPerformanceState(seed + 10, simulationSpeed = 2f)
    private val reactionsAuthority = authorityState(reactions)
    private val fastReactionsAuthority = authorityState(fastReactions)
    private val reactionsSnapshot = GameplayNucleus.renderSnapshot(reactionsAuthority)
    private val fastReactionsSnapshot = GameplayNucleus.renderSnapshot(fastReactionsAuthority)
    private var controlCounter = 1L
    private val traceFingerprint = deterministicTraceFingerprint(seed + 9)

    fun scenarios(): List<BenchmarkScenario> = listOf(
        BenchmarkScenario(
            name = "harness_control",
            category = "harness",
            description = "Lambda, loop, timer, counter and blackhole floor for this harness.",
            maximumOperations = 5_000_000,
        ) { validation ->
            controlCounter = controlCounter * 2_862_933_555_777_941_757L + 3_037_000_493L
            validation.observeOutcome { controlCounter }
            controlCounter
        },
        BenchmarkScenario(
            name = "state_initialization",
            category = "lifecycle",
            description = "Create deterministic mutable gameplay state with captured canonical content.",
            metadata = baseMetadata(seed, "entities" to "opening"),
            maximumOperations = 200_000,
        ) { validation ->
            val state = gameScenario(seed = seed)
            validation.observeOutcome { canonicalStateFingerprint(state) }
            stateSignature(state)
        },
        BenchmarkScenario(
            name = "run_start",
            category = "lifecycle",
            description = "Create and fully reset/start one deterministic run.",
            metadata = baseMetadata(seed, "entities" to "opening"),
            maximumOperations = 100_000,
        ) { validation ->
            val state = gameScenario(seed = seed)
            state.startRun()
            validation.observeOutcome { canonicalStateFingerprint(state) }
            stateSignature(state)
        },
        BenchmarkScenario(
            name = "copy_idle",
            category = "state",
            description = "Isolated transaction copy of an opening-run state.",
            metadata = stateCardinality(idle, seed),
            maximumOperations = 100_000,
        ) { validation ->
            val state = idle.copyForReduction()
            validation.observeOutcome { canonicalStateFingerprint(state) }
            stateSignature(state)
        },
        BenchmarkScenario(
            name = "copy_capacity",
            category = "state",
            description = "Isolated transaction copy at all bounded entity collection capacities.",
            metadata = stateCardinality(capacity, seed),
            maximumOperations = 20_000,
        ) { validation ->
            val state = capacity.copyForReduction()
            validation.observeOutcome { canonicalStateFingerprint(state) }
            stateSignature(state)
        },
        BenchmarkScenario(
            name = "render_model_idle",
            category = "projection",
            description = "Build immutable render model for an opening-run state.",
            metadata = stateCardinality(idle, seed),
            maximumOperations = 100_000,
        ) { validation ->
            val result = renderSignature(idle)
            validation.observeOutcome { canonicalRenderFingerprint(idle) }
            result
        },
        BenchmarkScenario(
            name = "render_model_capacity",
            category = "projection",
            description = "Build immutable render model at all bounded entity collection capacities.",
            metadata = stateCardinality(capacity, seed),
            maximumOperations = 20_000,
        ) { validation ->
            val result = renderSignature(capacity)
            validation.observeOutcome { canonicalRenderFingerprint(capacity) }
            result
        },
        BenchmarkScenario(
            name = "reducer_frame_60hz_idle",
            category = "reducer",
            description = "Copy, simulate two fixed steps and map reducer outputs for one 60 Hz frame.",
            metadata = stateCardinality(idle, seed) + baseMetadata(seed, "fixedSteps" to "2"),
            maximumOperations = 50_000,
        ) { validation ->
            val result = reducerSignature(idle, frame60)
            validation.observeOutcome { reducerOutcomeFingerprint(idle, frame60) }
            result
        },
        BenchmarkScenario(
            name = "reducer_frame_100ms_idle",
            category = "reducer",
            description = "Copy, simulate twelve fixed steps and map outputs for a 100 ms spike frame.",
            metadata = stateCardinality(idle, seed) + baseMetadata(seed, "fixedSteps" to "12"),
            maximumOperations = 10_000,
        ) { validation ->
            val result = reducerSignature(idle, frame100Millis)
            validation.observeOutcome { reducerOutcomeFingerprint(idle, frame100Millis) }
            result
        },
        BenchmarkScenario(
            name = "fixed_step_collision_miss",
            category = "simulation",
            description = "Copy and execute one fixed step with 90 enemies and 325 projectiles that never hit.",
            metadata = stateCardinality(collisionMiss, seed) + baseMetadata(seed, "collisionMode" to "miss"),
            maximumOperations = 10_000,
        ) { validation ->
            val candidate = collisionMiss.copyForReduction()
            candidate.simulateStep(MutableGameState.FIXED_STEP)
            validation.observeOutcome { canonicalStateFingerprint(candidate) }
            stateSignature(candidate)
        },
        BenchmarkScenario(
            name = "fixed_step_collision_hit",
            category = "simulation",
            description = "Copy and execute one fixed step with a dense projectile/enemy hit matrix.",
            metadata = stateCardinality(collisionHit, seed) + baseMetadata(seed, "collisionMode" to "hit"),
            maximumOperations = 5_000,
        ) { validation ->
            val candidate = collisionHit.copyForReduction()
            candidate.simulateStep(MutableGameState.FIXED_STEP)
            validation.observeOutcome { canonicalStateFingerprint(candidate) }
            stateSignature(candidate)
        },
        BenchmarkScenario(
            name = "fixed_step_budget_48_idle",
            category = "simulation",
            description = "Copy and drain the explicit maximum 48-step accumulator budget.",
            metadata = stateCardinality(idle, seed) + baseMetadata(seed, "fixedSteps" to "48"),
            maximumOperations = 2_000,
        ) { validation ->
            val candidate = idle.copyForReduction()
            candidate.accumulator = MutableGameState.FIXED_STEP * 48f
            candidate.consumeFixedStepBudget()
            validation.observeOutcome { canonicalStateFingerprint(candidate) }
            stateSignature(candidate)
        },
        BenchmarkScenario(
            name = "nucleus_frame_60hz_idle",
            category = "nucleus",
            description = "Full Gameplay Nucleus frame decision, output mapping and render snapshot.",
            metadata = stateCardinality(idle, seed) + baseMetadata(seed, "fixedSteps" to "2"),
            maximumOperations = 50_000,
        ) { validation ->
            val result = authoritySignature(idleAuthority, frame60, includeRenderSnapshot = true)
            validation.observeOutcome { authorityOutcomeFingerprint(idleAuthority, frame60) }
            result
        },
        BenchmarkScenario(
            name = "nucleus_frame_60hz_capacity",
            category = "nucleus",
            description = "Full frame decision plus render snapshot at collection capacities.",
            metadata = stateCardinality(capacity, seed) + baseMetadata(seed, "fixedSteps" to "2"),
            maximumOperations = 5_000,
        ) { validation ->
            val result = authoritySignature(capacityAuthority, frame60, includeRenderSnapshot = true)
            validation.observeOutcome { authorityOutcomeFingerprint(capacityAuthority, frame60) }
            result
        },
        BenchmarkScenario(
            name = "nucleus_pointer_move_idle",
            category = "input",
            description = "Validated pointer pulse through copy, decision, outputs and render snapshot.",
            metadata = stateCardinality(idle, seed) + baseMetadata(seed, "input" to "pointer"),
            maximumOperations = 100_000,
        ) { validation ->
            val result = authoritySignature(idleAuthority, pointerMove, includeRenderSnapshot = true)
            validation.observeOutcome { authorityOutcomeFingerprint(idleAuthority, pointerMove) }
            result
        },
        BenchmarkScenario(
            name = "nucleus_viewport_change_idle",
            category = "input",
            description = "Validated resize/density pulse through decision and render publication.",
            metadata = stateCardinality(idle, seed) + baseMetadata(seed, "viewport" to "1920x1080@2"),
            maximumOperations = 100_000,
        ) { validation ->
            val result = authoritySignature(idleAuthority, viewportChange, includeRenderSnapshot = true)
            validation.observeOutcome { authorityOutcomeFingerprint(idleAuthority, viewportChange) }
            result
        },
        BenchmarkScenario(
            name = "nucleus_frame_paused",
            category = "phase",
            description = "Frame pulse, transaction copy and render publication while paused.",
            metadata = stateCardinality(paused, seed) + baseMetadata(seed, "phase" to "PAUSED"),
            maximumOperations = 100_000,
        ) { validation ->
            val result = authoritySignature(pausedAuthority, frame60, includeRenderSnapshot = true)
            validation.observeOutcome { authorityOutcomeFingerprint(pausedAuthority, frame60) }
            result
        },
        BenchmarkScenario(
            name = "published_frame_60hz_idle",
            category = "publication_pipeline",
            description = "Model branch-specific projection work for one accepted 60 Hz UI frame.",
            metadata = stateCardinality(idle, seed) + baseMetadata(seed, "fixedSteps" to "2"),
            maximumOperations = 50_000,
        ) { validation ->
            val result = publishedPipelineSignature(idleAuthority, frame60)
            validation.observeOutcome { authorityOutcomeFingerprint(idleAuthority, frame60) }
            result
        },
        BenchmarkScenario(
            name = "published_pointer_move_idle",
            category = "publication_pipeline",
            description = "Model branch-specific preflight and publication work for one pointer move.",
            metadata = stateCardinality(idle, seed) + baseMetadata(seed, "input" to "pointer"),
            maximumOperations = 50_000,
        ) { validation ->
            val result = publishedPipelineSignature(idleAuthority, pointerMove)
            validation.observeOutcome { authorityOutcomeFingerprint(idleAuthority, pointerMove) }
            result
        },
        BenchmarkScenario(
            name = "published_frame_paused",
            category = "publication_pipeline",
            description = "Model branch-specific projection work for one accepted paused UI frame.",
            metadata = stateCardinality(paused, seed) + baseMetadata(seed, "phase" to "PAUSED"),
            maximumOperations = 50_000,
        ) { validation ->
            val result = publishedPipelineSignature(pausedAuthority, frame60)
            validation.observeOutcome { authorityOutcomeFingerprint(pausedAuthority, frame60) }
            result
        },
        BenchmarkScenario(
            name = "published_reaction_frame_60hz_1x",
            category = "publication_pipeline",
            description = "Copy, simulate and publish a crowded elemental build at 1x speed with the preceding render snapshot.",
            metadata = reactionMetadata(reactions, fixedSteps = 2),
            maximumOperations = 5_000,
        ) { validation ->
            publishedReactionSignature(reactionsAuthority, reactionsSnapshot, frame60, validation)
        },
        BenchmarkScenario(
            name = "published_reaction_frame_60hz_2x",
            category = "publication_pipeline",
            description = "Copy, simulate and publish the same elemental build at the supported 2x speed.",
            metadata = reactionMetadata(fastReactions, fixedSteps = 4),
            maximumOperations = 5_000,
        ) { validation ->
            publishedReactionSignature(fastReactionsAuthority, fastReactionsSnapshot, frame60, validation)
        },
        BenchmarkScenario(
            name = "published_reaction_burst_100ms_2x",
            category = "publication_pipeline",
            description = "Catch up a 100 ms frame at 2x speed with active reactions, fields and high weapon activation.",
            // The Float accumulator executes 23 steps here; its remainder carries into the next frame.
            metadata = reactionMetadata(fastReactions, fixedSteps = 23),
            maximumOperations = 2_000,
        ) { validation ->
            publishedReactionSignature(fastReactionsAuthority, fastReactionsSnapshot, frame100Millis, validation)
        },
        BenchmarkScenario(
            name = "trace_2s_60hz",
            category = "trace",
            description = "Deterministic 120-frame replay including pointer movement and render snapshots.",
            metadata = baseMetadata(
                seed,
                "frames" to TRACE_FRAMES.toString(),
                "stateFingerprint" to traceFingerprint.toString(),
            ),
            maximumOperations = 500,
        ) { validation ->
            val result = deterministicTraceFingerprint(seed + 9)
            validation.observeOutcome { result }
            result
        },
    ).map { scenario ->
        val expectedOutcomeWitness = outcomeFingerprint(scenario.name)
        scenario.copy(
            metadata = scenario.metadata +
                ("outcomeFingerprint" to expectedOutcomeWitness.toString()),
            validation = BenchmarkValidation(
                expectedTimedResult = expectedTimedResult(scenario.name),
                expectedOutcomeWitness = expectedOutcomeWitness,
                prepareProbe = {
                    if (scenario.category == "harness") controlCounter = 1L
                },
            ),
        )
    }

    private fun outcomeFingerprint(name: String): Long = when (name) {
        "harness_control" -> nextControlValue(1L)
        "state_initialization" -> canonicalStateFingerprint(gameScenario(seed = seed))
        "run_start" -> canonicalStateFingerprint(
            gameScenario(seed = seed).apply { startRun() },
        )
        "copy_idle" -> canonicalStateFingerprint(idle.copyForReduction())
        "copy_capacity" -> canonicalStateFingerprint(capacity.copyForReduction())
        "render_model_idle" -> canonicalRenderFingerprint(idle)
        "render_model_capacity" -> canonicalRenderFingerprint(capacity)
        "reducer_frame_60hz_idle" -> reducerOutcomeFingerprint(idle, frame60)
        "reducer_frame_100ms_idle" -> reducerOutcomeFingerprint(idle, frame100Millis)
        "fixed_step_collision_miss" -> fixedStepOutcomeFingerprint(collisionMiss)
        "fixed_step_collision_hit" -> fixedStepOutcomeFingerprint(collisionHit)
        "fixed_step_budget_48_idle" -> idle.copyForReduction().run {
            accumulator = MutableGameState.FIXED_STEP * 48f
            consumeFixedStepBudget()
            canonicalStateFingerprint(this)
        }
        "nucleus_frame_60hz_idle", "published_frame_60hz_idle" ->
            authorityOutcomeFingerprint(idleAuthority, frame60)
        "nucleus_frame_60hz_capacity" ->
            authorityOutcomeFingerprint(capacityAuthority, frame60)
        "published_reaction_frame_60hz_1x" ->
            authorityOutcomeFingerprint(reactionsAuthority, frame60)
        "published_reaction_frame_60hz_2x" ->
            authorityOutcomeFingerprint(fastReactionsAuthority, frame60)
        "published_reaction_burst_100ms_2x" ->
            authorityOutcomeFingerprint(fastReactionsAuthority, frame100Millis)
        "nucleus_pointer_move_idle", "published_pointer_move_idle" ->
            authorityOutcomeFingerprint(idleAuthority, pointerMove)
        "nucleus_viewport_change_idle" ->
            authorityOutcomeFingerprint(idleAuthority, viewportChange)
        "nucleus_frame_paused", "published_frame_paused" ->
            authorityOutcomeFingerprint(pausedAuthority, frame60)
        "trace_2s_60hz" -> traceFingerprint
        else -> error("Missing semantic outcome fingerprint for benchmark scenario: $name")
    }

    private fun expectedTimedResult(name: String): Long = when (name) {
        "harness_control" -> nextControlValue(1L)
        "state_initialization" -> stateSignature(gameScenario(seed = seed))
        "run_start" -> stateSignature(gameScenario(seed = seed).apply { startRun() })
        "copy_idle" -> stateSignature(idle.copyForReduction())
        "copy_capacity" -> stateSignature(capacity.copyForReduction())
        "render_model_idle" -> renderSignature(idle)
        "render_model_capacity" -> renderSignature(capacity)
        "reducer_frame_60hz_idle" -> reducerSignature(idle, frame60)
        "reducer_frame_100ms_idle" -> reducerSignature(idle, frame100Millis)
        "fixed_step_collision_miss" -> collisionMiss.copyForReduction().run {
            simulateStep(MutableGameState.FIXED_STEP)
            stateSignature(this)
        }
        "fixed_step_collision_hit" -> collisionHit.copyForReduction().run {
            simulateStep(MutableGameState.FIXED_STEP)
            stateSignature(this)
        }
        "fixed_step_budget_48_idle" -> idle.copyForReduction().run {
            accumulator = MutableGameState.FIXED_STEP * 48f
            consumeFixedStepBudget()
            stateSignature(this)
        }
        "nucleus_frame_60hz_idle" ->
            authoritySignature(idleAuthority, frame60, includeRenderSnapshot = true)
        "nucleus_frame_60hz_capacity" ->
            authoritySignature(capacityAuthority, frame60, includeRenderSnapshot = true)
        "nucleus_pointer_move_idle" ->
            authoritySignature(idleAuthority, pointerMove, includeRenderSnapshot = true)
        "nucleus_viewport_change_idle" ->
            authoritySignature(idleAuthority, viewportChange, includeRenderSnapshot = true)
        "nucleus_frame_paused" ->
            authoritySignature(pausedAuthority, frame60, includeRenderSnapshot = true)
        "published_frame_60hz_idle" -> publishedPipelineSignature(idleAuthority, frame60)
        "published_pointer_move_idle" -> publishedPipelineSignature(idleAuthority, pointerMove)
        "published_frame_paused" -> publishedPipelineSignature(pausedAuthority, frame60)
        "published_reaction_frame_60hz_1x" ->
            publishedReactionSignature(reactionsAuthority, reactionsSnapshot, frame60)
        "published_reaction_frame_60hz_2x" ->
            publishedReactionSignature(fastReactionsAuthority, fastReactionsSnapshot, frame60)
        "published_reaction_burst_100ms_2x" ->
            publishedReactionSignature(fastReactionsAuthority, fastReactionsSnapshot, frame100Millis)
        "trace_2s_60hz" -> deterministicTraceFingerprint(seed + 9)
        else -> error("Missing expected timed result for benchmark scenario: $name")
    }

    private fun nextControlValue(value: Long): Long =
        value * 2_862_933_555_777_941_757L + 3_037_000_493L

    private fun fixedStepOutcomeFingerprint(source: MutableGameState): Long =
        source.copyForReduction().run {
            simulateStep(MutableGameState.FIXED_STEP)
            canonicalStateFingerprint(this)
        }

    private fun reducerOutcomeFingerprint(
        model: MutableGameState,
        pulse: GameplayInteractionPulse,
    ): Long = when (val result = reducer.reduce(EngineState(model), pulse)) {
        is GameReductionResult.Accepted -> canonicalDecisionOutcomeFingerprint(
            state = result.state.model,
            outputFingerprint = simulationOutputFingerprint(result.outputs),
            includeRender = false,
        )
        is GameReductionResult.Rejected ->
            error("Benchmark reducer outcome pulse was rejected: ${result.reason}")
    }

    private fun authorityOutcomeFingerprint(
        state: GameplayState,
        pulse: GameplayInteractionPulse,
    ): Long {
        val decision = GameplayNucleus.decide(state, GameplayNucleusPulse.Intent(pulse))
        val frame = (decision as? GameplayDecision.Accepted)?.frame
            ?: error("Benchmark Nucleus outcome pulse was rejected: $decision")
        return canonicalDecisionOutcomeFingerprint(
            state = checkNotNull(frame.nextState.engine).model,
            outputFingerprint = gameplayOutputFingerprint(frame.outputs),
            includeRender = true,
        )
    }

    private fun reducerSignature(
        model: MutableGameState,
        pulse: GameplayInteractionPulse,
    ): Long = when (val result = reducer.reduce(EngineState(model), pulse)) {
        is GameReductionResult.Accepted ->
            stateSignature(result.state.model) xor result.outputs.size.toLong()
        is GameReductionResult.Rejected -> error("Benchmark reducer pulse was rejected: ${result.reason}")
    }

    private fun authoritySignature(
        state: GameplayState,
        pulse: GameplayInteractionPulse,
        includeRenderSnapshot: Boolean,
    ): Long {
        val decision = GameplayNucleus.decide(state, GameplayNucleusPulse.Intent(pulse))
        val frame = (decision as? GameplayDecision.Accepted)?.frame
            ?: error("Benchmark Nucleus pulse was rejected: $decision")
        val model = checkNotNull(frame.nextState.engine).model
        var signature = stateSignature(model) xor frame.outputs.size.toLong()
        if (includeRenderSnapshot) {
            val render = checkNotNull(GameplayNucleus.renderSnapshot(frame.nextState).renderModel)
            signature = mix(signature, render.enemies.size)
            signature = mix(signature, render.projectiles.size)
            signature = mix(signature, render.elapsed.toRawBits())
        }
        return signature
    }

    private fun publishedPipelineSignature(
        state: GameplayState,
        pulse: GameplayInteractionPulse,
    ): Long {
        val decision = GameplayNucleus.decide(state, GameplayNucleusPulse.Intent(pulse))
        val frame = (decision as? GameplayDecision.Accepted)?.frame
            ?: error("Benchmark Nucleus pulse was rejected: $decision")
        // GameComponent preflight builds the accepted revision's projection once and publishes
        // that exact snapshot. Interaction reuses it, including for pointer hit testing.
        val signature = renderSnapshotSignature(GameplayNucleus.renderSnapshot(frame.nextState))
        return mixLong(signature, stateSignature(checkNotNull(frame.nextState.engine).model))
    }

    private fun reactionMetadata(model: MutableGameState, fixedSteps: Int): Map<String, String> =
        stateCardinality(model, seed + 10) + mapOf(
            "workloadVersion" to "reaction-high-activation-v2",
            "fixedSteps" to fixedSteps.toString(),
            "simulationSpeed" to model.settings.simulationSpeed.toString(),
            "weapon" to model.weapon.name,
            "weaponLevel" to model.weaponLevel.toString(),
            "attackSpeed" to model.attackSpeed.toString(),
            "ownedItems" to model.build.ownedItemCount.toString(),
            "combatFields" to model.combatFieldTable.count.toString(),
            "stateFingerprint" to canonicalStateFingerprint(model).toString(),
            "renderReuse" to "committed-predecessor",
        )

    private fun publishedReactionSignature(
        source: GameplayState,
        reusableSnapshot: GameplayRenderSnapshot,
        pulse: GameplayInteractionPulse,
        validation: BenchmarkValidationContext? = null,
    ): Long {
        val decision = GameplayNucleus.decide(source, GameplayNucleusPulse.Intent(pulse))
        val frame = (decision as? GameplayDecision.Accepted)?.frame
            ?: error("Reaction benchmark pulse was rejected: $decision")
        val model = checkNotNull(frame.nextState.engine).model
        val snapshot = GameplayNucleus.renderSnapshot(frame.nextState, source, reusableSnapshot)
        validation?.observeOutcome {
            check(
                exactRenderValueFacts(checkNotNull(snapshot.renderModel)) ==
                    exactRenderValueFacts(checkNotNull(GameplayNucleus.renderSnapshot(frame.nextState).renderModel)),
            ) {
                "Reaction benchmark reused projection differs from a fresh projection"
            }
            canonicalDecisionOutcomeFingerprint(model, gameplayOutputFingerprint(frame.outputs), includeRender = true)
        }
        return mixLong(renderSnapshotSignature(snapshot), stateSignature(model))
    }

    private fun deterministicTraceFingerprint(traceSeed: Int): Long {
        var state = authorityState(newRunningState(traceSeed))
        repeat(TRACE_FRAMES) { frameIndex ->
            val x = 640f + ((frameIndex * 37) % 560)
            val y = 360f + ((frameIndex * 19) % 240) - 120f
            state = acceptedState(
                GameplayNucleus.decide(
                    state,
                    GameplayNucleusPulse.Intent(
                        GameplayInteractionPulse.PointerMoved.fromValidated(x, y),
                    ),
                ),
            )
            state = acceptedState(
                GameplayNucleus.decide(state, GameplayNucleusPulse.Intent(frame60)),
            )
            GameplayNucleus.renderSnapshot(state)
        }
        return canonicalStateFingerprint(checkNotNull(state.engine).model)
    }
}

private fun newRunningState(seed: Int): MutableGameState = gameScenario(seed = seed).apply {
    resize(1_280f, 720f, 1f)
    startRun()
    takeSoundCues()
    takeVisualFxCues()
}

private fun capacityState(seed: Int): MutableGameState = newRunningState(seed).apply {
    enemies.clear()
    projectiles.clear()
    pickups.clear()
    trail.clear()
    repeat(MAX_ENEMIES) { index ->
        addEnemyForTesting(
            x = 300f + (index % 12) * 60f,
            y = -270f + (index / 12) * 60f,
            hp = 1_000_000f,
            radius = 14f,
            type = EnemyType.entries[index % EnemyType.entries.size],
        )
    }
    repeat(MAX_PROJECTILES) { index ->
        projectiles += Projectile(
            x = -300f - (index % 25) * 40f,
            y = -260f + (index / 25) * 20f,
            vx = 0f,
            vy = 0f,
            radius = 2f,
            life = 20f,
            hostile = false,
            damage = 1f,
            pierce = 2,
        )
    }
    repeat(MAX_PICKUPS) { index ->
        pickups += Pickup(
            type = PickupType.entries[index % PickupType.entries.size],
            x = -1_200f + (index % 30) * 18f,
            y = 500f + (index / 30) * 12f,
        )
    }
    repeat(MAX_TRAIL) { index -> trail += TrailPoint(-1_300f + index * 2f, -700f, 0.5f) }
}

/**
 * A repeatable late-run combat snapshot using canonical item, weapon and field rules. Its enemy
 * integrity is raised so copies of the same crowded instant measure combat rather than an emptied
 * arena. Pre-existing statuses, fields and sixteen in-flight volleys model an active elemental build;
 * no cooldown, reaction budget, generation limit or activation rule is changed by the workload.
 */
internal fun reactionPerformanceState(seed: Int, simulationSpeed: Float = 1f): MutableGameState = buildTestState(
    RelicId.STORMHEART_CAPACITOR,
    RelicId.ECHO_CHAMBER,
    RelicId.EVENT_HORIZON,
    RelicId.THUNDER_CHOIR,
    seed = seed,
    rebirth = 4,
    shape = CoreShape.HEXAGON,
    weapon = WeaponId.ION_SWARM,
).apply {
    settings = settings.copy(simulationSpeed = simulationSpeed).normalized()
    elapsed = 300f
    level = 40
    weaponLevel = 10
    for (name in listOf(
        "Servo Trigger", "Gyro Stabilizer", "Hyperfeed Rails", "Thermal Regulator",
        "Incendiary Rounds", "Rime Rounds", "Arc Relay", "Graviton Seed", "Rust Spores",
        "Subwoofer Rounds", "Napalm Feed", "Cryo Feed", "Blight Cartridge",
        "Catalyst Matrix", "Resonance Fork", "Storm Lattice", "Dark Star",
    )) {
        val item = content.items.single { it.name == name }
        own(name, stacks = item.maxStacks)
    }
    heat = 60f
    overdriveTime = 5f
    stormCharge = 0.25f
    velocityX = 900f
    velocityY = 0f
    pointerX = 1_000f
    pointerY = 360f
    weaponClock = 0f

    repeat(content.rebirth.maxActiveEnemies) { index ->
        val enemy = addEnemyForTesting(
            x = 210f + (index % 12) * 20f,
            y = -90f + (index / 12) * 20f,
            hp = 1_000_000f,
            radius = 14f,
        )
        // Stagger the older element, so incoming Ion and Thermal exercise different reaction paths.
        val older = StatusElement.entries[index % StatusElement.entries.size]
        enemy.status[StatusCell.STACKS + older.ordinal] = 3f
        enemy.status[StatusCell.DECAY_AT + older.ordinal] = elapsed + 2f
        enemy.status[StatusCell.GENERATION + older.ordinal] = 0f
    }
    val fusion = content.build.reaction(ReactionId.FUSION)
    val potency = (build.potency(StatusElement.THERMAL) + build.potency(StatusElement.GRAVITIC)) * 0.5f
    // Saturate the field bound: the reaction and item pool in mixed kinds (each kind below its cap), then one
    // field per skill slot, all with the Fusion's pull, damage and Thermal ticks.
    val policy = content.build.policy
    val pool = policy.maximumFields - policy.maximumSkillFields
    val shared = listOf(
        CombatFieldKind.FUSION to FieldSource.REACTION,
        CombatFieldKind.ROT_WELL to FieldSource.REACTION,
        CombatFieldKind.DARK_STAR to FieldSource.ITEM,
    )
    repeat(policy.maximumFields) { index ->
        val (kind, source) = if (index < pool) shared[index % shared.size] else CombatFieldKind.ECHO_RING to FieldSource.SKILL
        addField(
            kind = kind,
            source = source,
            owner = if (index < pool) 0 else index - pool,
            x = 190f + (index % 4) * 90f,
            y = -100f + (index / 4) * 100f,
            radius = fusion.radius,
            seconds = 1.2f,
            damagePerSecond = fusion.power * 4f * potency * build.reactionPower * runScale(),
            element = StatusElement.THERMAL,
            stacksPerTick = 1,
            generation = 1,
            pull = 220f,
            tickClock = CombatField.TICK_SECONDS - MutableGameState.FIXED_STEP * 0.5f,
        )
    }
    val volleySize = ionOrbitalCount()
    repeat(16) { volley ->
        val serial = beginWeaponActivation()
        repeat(volleySize) { shot ->
            val enemy = enemies[(volley * volleySize + shot) % enemies.size]
            firePlayerProjectile(
                x = enemy.x - 8f,
                y = enemy.y,
                angle = 0f,
                projectileSpeed = 620f,
                damage = ION_SWARM_DAMAGE * effectiveWeaponPower(),
                pierce = ION_SWARM_PIERCE + 1,
                radius = 4f,
                colorIndex = 1,
                procKey = serial,
            )
        }
    }
    takeSoundCues()
    takeVisualFxCues()
    takeProgressUpdate()
}

private fun collisionState(seed: Int, hit: Boolean): MutableGameState = newRunningState(seed).apply {
    enemies.clear()
    projectiles.clear()
    pickups.clear()
    trail.clear()
    repeat(COLLISION_ENEMIES) { index ->
        val x = 400f + (index % 15) * 45f
        val y = -120f + (index / 15) * 45f
        addEnemyForTesting(x, y, hp = 1_000_000f, radius = 18f)
    }
    repeat(COLLISION_PROJECTILES) { index ->
        val enemyIndex = index % COLLISION_ENEMIES
        val hitX = 400f + (enemyIndex % 15) * 45f
        val hitY = -120f + (enemyIndex / 15) * 45f
        projectiles += Projectile(
            x = if (hit) hitX else -900f - (index % 50) * 5f,
            y = if (hit) hitY else 500f + (index / 50) * 8f,
            vx = 0f,
            vy = 0f,
            radius = 5f,
            life = 20f,
            hostile = false,
            damage = 1f,
            pierce = 120,
        )
    }
    repeat(MAX_TRAIL) { index -> trail += TrailPoint(-1_300f + index, -700f, 0.5f) }
}

internal fun authorityState(model: MutableGameState): GameplayState = GameplayState(
    instanceId = GameplayInstanceId(RunId(1L)),
    revision = GameplayRevision.ZERO,
    phase = when (model.phase) {
        GamePhase.RUNNING -> GameplayRunPhase.RUNNING
        GamePhase.PAUSED -> GameplayRunPhase.PAUSED
        GamePhase.CHOICE -> GameplayRunPhase.CHOICE
        GamePhase.GAME_OVER -> GameplayRunPhase.GAME_OVER
        GamePhase.VICTORY -> GameplayRunPhase.VICTORY
    },
    content = canonicalGameplayContent,
    engine = EngineState(model),
    progressPending = false,
)

private fun acceptedState(decision: GameplayDecision): GameplayState =
    (decision as? GameplayDecision.Accepted)?.frame?.nextState
        ?: error("Benchmark trace pulse was rejected: $decision")

private fun renderSignature(state: MutableGameState): Long {
    val render = state.toRenderModel()
    var signature = render.elapsed.toRawBits().toLong()
    signature = mix(signature, render.enemies.size)
    signature = mix(signature, render.projectiles.size)
    signature = mix(signature, render.pickups.size)
    signature = mix(signature, render.trail.size)
    signature = mix(signature, render.itemStacksSnapshot.size)
    return signature
}

private fun renderSnapshotSignature(
    snapshot: kinetickk.ball.gameplay.nucleus.render.GameplayRenderSnapshot,
): Long {
    val render = checkNotNull(snapshot.renderModel)
    var signature = render.elapsed.toRawBits().toLong()
    signature = mix(signature, render.enemies.size)
    signature = mix(signature, render.projectiles.size)
    signature = mix(signature, render.pickups.size)
    signature = mix(signature, render.trail.size)
    return signature
}

/**
 * Untimed desktop verification of every stored render fact, including discovery masks behind public
 * reads. The outer model deliberately has reference equality; its immutable collections and value
 * objects have value equality. Reflection also covers newly added fields without a second field list.
 */
internal fun exactRenderValueFacts(model: GameplayRenderModel): List<Pair<String, Any?>> =
    GameplayRenderModel::class.java.declaredFields
        .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
        .sortedBy { it.name }
        .map { field ->
            field.isAccessible = true
            val value = field.get(model)
            field.name to when (value) {
                is Float -> value.toRawBits()
                is Double -> value.toRawBits()
                else -> value
            }
        }

private fun stateSignature(state: MutableGameState): Long {
    var signature = state.elapsed.toRawBits().toLong()
    signature = mix(signature, state.phase.name.hashCode())
    signature = mix(signature, state.coreX.toRawBits())
    signature = mix(signature, state.coreY.toRawBits())
    signature = mix(signature, state.velocityX.toRawBits())
    signature = mix(signature, state.velocityY.toRawBits())
    signature = mix(signature, state.hp.toRawBits())
    signature = mix(signature, state.enemies.size)
    signature = mix(signature, state.projectiles.size)
    signature = mix(signature, state.pickups.size)
    signature = mix(signature, state.trail.size)
    // 0.6.0 Baby mode
    signature = mix(signature, if (state.easyMode) 1 else 0)
    return signature
}

internal fun canonicalStateFingerprint(state: MutableGameState): Long {
    var signature = stateSignature(state)
    signature = mix(signature, state.level)
    signature = mix(signature, state.data)
    signature = mix(signature, state.kills)
    signature = mix(signature, state.heat.toRawBits())
    signature = mix(signature, state.screenWidth.toRawBits())
    signature = mix(signature, state.screenHeight.toRawBits())
    signature = mix(signature, state.pointerX.toRawBits())
    signature = mix(signature, state.pointerY.toRawBits())
    // Release 0.2.0 changed accepted mechanics. Include their retained facts in the witness;
    // a matching adapter file alone does not make the old gameplay outcome comparable.
    signature = mix(signature, state.coreShape.ordinal)
    signature = mix(signature, state.coreRadius.toRawBits())
    signature = mix(signature, state.characterRuntime.toString().hashCode())
    // 0.4.0 weapons: the weapon runtime (entities, proc gates, counters, orbit phase) in its deterministic text.
    signature = mix(signature, state.weaponRuntime.toString().hashCode())
    signature = mix(signature, state.nextEliteAt.toRawBits())
    signature = mix(signature, state.elitesSpawned)
    // 0.4.0 spawn director: its own stream, pacing phase, budget, planned event and cooldowns.
    signature = mix(signature, state.spawnRandom.snapshot().hashCode())
    signature = mix(signature, state.heraldsSpawned)
    signature = mix(signature, state.directorPhase)
    signature = mix(signature, state.directorPhaseUntil.toRawBits())
    signature = mix(signature, state.spawnBudget.toRawBits())
    signature = mix(signature, state.pendingEventKind)
    signature = mix(signature, state.pendingEventSize)
    signature = mix(signature, state.pendingEventType)
    signature = mixLong(signature, state.recentEventTypes)
    signature = mixLong(signature, state.previousEventTypes)
    signature = mix(signature, state.ambushReadyAt.toRawBits())
    signature = mix(signature, state.encircleReadyAt.toRawBits())
    signature = mix(signature, state.wallReadyAt.toRawBits())
    signature = mix(signature, state.capOverflowReadyAt.toRawBits())
    signature = mix(signature, state.ambushSpeedSeconds.toRawBits())
    signature = mix(signature, state.spawnEventCount)
    signature = mix(signature, state.lastSpawnEventKind)
    signature = mix(signature, state.lastSpawnEventPlanned)
    signature = mix(signature, state.lastSpawnEventSpawned)
    signature = mix(signature, state.nextLevelData)
    signature = mix(signature, state.smoothedVelocityX.toRawBits())
    signature = mix(signature, state.smoothedVelocityY.toRawBits())
    signature = mix(signature, if (state.turnHeadingEstablished) 1 else 0)
    signature = mix(signature, state.turnHoldTime.toRawBits())
    signature = mix(signature, state.turnRecoveryCooldown.toRawBits())
    signature = mix(signature, state.turnDirection)
    signature = mix(signature, state.pendingEliteKills)
    signature = mix(signature, state.pendingDashHits)
    signature = mix(signature, state.pendingCompletedOrbits)
    signature = mix(signature, state.pendingArchitectDefeatedWith?.ordinal ?: -1)
    // Core Matrix: folded run modifiers, per-run Matrix runtime and the achievement counters.
    signature = mix(signature, state.matrix.hashCode())
    state.matrixRanks.forEach { signature = mix(signature, it) }
    signature = mix(signature, state.itemOffersOpened)
    signature = mix(signature, if (state.secondWindSpent) 1 else 0)
    signature = mix(signature, state.matrixFreeDashReadyAt.toRawBits())
    signature = mix(signature, state.pendingTotalKills)
    signature = mix(signature, state.pendingRicochets)
    signature = mix(signature, state.pendingDataPickups)
    signature = mix(signature, if (state.achievementFlushDue) 1 else 0)
    signature = mix(signature, state.sites.toString().hashCode())
    signature = mix(signature, state.nextSiteWindowIndex)
    signature = mix(signature, state.pendingDirectedRewards.toString().hashCode())
    signature = mix(signature, state.directedReward?.ordinal ?: -1)
    signature = mix(signature, state.selectedRewardFocus?.ordinal ?: -1)
    // Build runtime: the composable effect system's retained facts.
    state.rebornItems.forEach { signature = mix(signature, it) }
    signature = mix(signature, state.acquisitionOrder.hashCode())
    signature = mix(signature, state.build.upsideMask)
    signature = mix(signature, state.build.liabilityMask)
    state.clauseReadyAt.forEach { signature = mix(signature, it.toRawBits()) }
    state.clauseCounters.forEach { signature = mix(signature, it) }
    state.statusAccumulators.forEach { signature = mix(signature, it.toRawBits()) }
    signature = mix(signature, state.buffs.toString().hashCode())
    signature = mix(signature, state.combatFieldTable.toString().hashCode())
    signature = mix(signature, state.echoHits.toString().hashCode())
    signature = mix(signature, state.severedRelics.toString().hashCode())
    signature = mix(signature, state.reactionTokens.toRawBits())
    signature = mix(signature, state.breakTokens.toRawBits())
    signature = mix(signature, state.discoveredReactionMask)
    signature = mix(signature, state.discoveredPairingMask)
    signature = mix(signature, state.stormCharge.toRawBits())
    signature = mix(signature, state.stasisMeter.toRawBits())
    // 0.4.0 frame pacing: render interpolation sources and the accepted viewport density.
    signature = mix(signature, state.accumulator.toRawBits())
    signature = mix(signature, state.previousCameraX.toRawBits())
    signature = mix(signature, state.previousCameraY.toRawBits())
    signature = mix(signature, state.previousMorningstarX.toRawBits())
    signature = mix(signature, state.previousMorningstarY.toRawBits())
    signature = mix(signature, state.viewportDensity.toRawBits())
    signature = mix(signature, if (state.interpolationPrimed) 1 else 0)
    signature = mix(signature, state.stasisBank)
    signature = mix(signature, state.overdriveBurnoutUntil.toRawBits())
    signature = mix(signature, state.invulnerableUntil.toRawBits())
    signature = mix(signature, if (state.ouroborosSpent) 1 else 0)
    signature = mix(signature, state.offerPity)
    // 0.4.0 Null Covenant: the maximum integrity its liability withholds.
    signature = mix(signature, state.integrityLiabilityCut.toRawBits())
    signature = mix(signature, state.rewardRandom.snapshot().hashCode())
    // 0.4.0 world obstacles: the run seed, the rebase origin, the generated window and collision cooldowns.
    signature = mix(signature, state.runSeed)
    signature = mixLong(signature, state.worldOriginX.toRawBits())
    signature = mixLong(signature, state.worldOriginY.toRawBits())
    signature = mix(signature, state.obstacleField.centerChunkX)
    signature = mix(signature, state.obstacleField.centerChunkY)
    signature = obstacleFieldFingerprint(signature, state.obstacleField.obstacles)
    signature = mix(signature, state.destroyedObstacleIds.hashCode())
    signature = mix(signature, state.obstacleHitReadyAt.toRawBits())
    signature = mix(signature, state.nearMissReadyAt.toRawBits())
    signature = mix(signature, state.reviveInvulnerableUntil.toRawBits())
    // 0.4.0 Rebirth rework: the unlock rank and the pending Volatile Remains bursts.
    signature = mix(signature, state.rebirthRank)
    signature = mix(signature, state.volatileBursts.toString().hashCode())
    state.pickups.forEach { signature = mix(signature, if (it.attracted) 1 else 0) }
    // 0.5.0 combat foundations: the field table's Core mask, the Core reaction state and the skill input buffers.
    signature = mix(signature, state.coreFieldMask)
    signature = mix(signature, state.fieldEnterReadyAt.toRawBits())
    for (value in floatArrayOf(
        state.ablazeUntil, state.frictionlessUntil, state.blastJumpUntil, state.weaveUntil, state.weaveDamage,
        state.weaveRange, state.guardUntil, state.guardDamage, state.voidAnchorUntil, state.overcurrentReadyAt,
        state.flameRefundReadyAt, state.coreImpulseReadyAt, state.primedDashUntil, state.phaseUntil,
        state.skillBufferTime, state.apexBufferTime, state.dashHeatBank,
    )) signature = mix(signature, value.toRawBits())
    signature = mix(signature, state.shotsErased.hashCode())
    for (value in intArrayOf(
        state.guardShards, state.voidAnchorId, state.erasureLatchId, state.shotErasedDispatches, state.markDispatches,
        state.pendingActiveItemId ?: -1, if (state.pendingActiveReborn) 1 else 0, state.skillBufferMask,
    )) signature = mix(signature, value)
    state.enemies.forEach {
        signature = mix(signature, it.characterMarkTime.toRawBits())
        signature = mix(signature, it.lastCharacterDash)
        signature = mix(signature, it.aspect)
        signature = mix(signature, if (it.aspectImmune) 1 else 0)
        signature = mix(signature, it.phase)
        signature = mix(signature, it.shiftUntil.toRawBits())
        signature = mix(signature, it.escortsPending)
        signature = mix(signature, it.lastDamagedAt.toRawBits())
        signature = mix(signature, it.disabledUntil.toRawBits())
        signature = mix(signature, it.silencedUntil.toRawBits())
        signature = mix(signature, it.mode)
        signature = mix(signature, it.modeUntil.toRawBits())
        signature = mix(signature, it.facing.toRawBits())
        signature = mix(signature, if (it.guarded) 1 else 0)
        signature = mix(signature, it.linkedBy)
        signature = mix(signature, it.linkedUntil.toRawBits())
        signature = mix(signature, it.shieldUntil.toRawBits())
        signature = mix(signature, it.abilityAt.toRawBits())
        signature = mix(signature, it.thrownUntil.toRawBits())
        signature = mix(signature, it.slamReadyAt.toRawBits())
        signature = mix(signature, if (it.armored) 1 else 0)
        signature = mix(signature, if (it.armorCracked) 1 else 0)
        // 0.6.0: encounter tag, kind counter, pending phase start, Elite affix and champion flag.
        signature = mixLong(signature, it.ownerTag)
        signature = mix(signature, it.charges)
        signature = mix(signature, if (it.phaseStartPending) 1 else 0)
        signature = mix(signature, it.affix?.ordinal ?: -1)
        signature = mix(signature, if (it.champion) 1 else 0)
        it.status.forEach { cell -> signature = mix(signature, cell.toRawBits()) }
    }
    signature = worldContractFingerprint(signature, state)
    return mixLong(signature, fixtureFingerprint(state))
}

/** 0.6.0 shared world contracts: terrain, sites, run variety, enemies, structures and dimensions. */
private fun worldContractFingerprint(current: Long, state: MutableGameState): Long {
    var signature = current
    signature = mix(signature, state.coreStraightness.toRawBits())
    signature = mix(signature, state.gripStrength.toRawBits())
    signature = mix(signature, state.slingStrength.toRawBits())
    signature = mixLong(signature, state.obstacleHitSurfaceId)
    signature = mix(signature, state.coreObstacleGap.toRawBits())
    signature = mix(signature, state.grazeReadyAt.toRawBits())
    signature = mixLong(signature, state.slingWellId)
    signature = mix(signature, state.slingEntryHeading.toRawBits())
    signature = mix(signature, state.siteRandom.snapshot().hashCode())
    signature = mix(signature, state.ambientSites.toString().hashCode())
    signature = mix(signature, state.secretSigil.toString().hashCode())
    signature = mix(signature, state.openedSiteIds.toString().hashCode())
    signature = mix(signature, state.runSecretFlags)
    signature = mix(signature, state.worldDiscoveryMask)
    signature = mix(signature, state.pendingWorldDiscoveryMask)
    signature = mix(signature, state.pendingFloorOffers.toString().hashCode())
    signature = mix(signature, state.goldenDrifterId)
    signature = mix(signature, state.goldenDrifterUntil.toRawBits())
    signature = mix(signature, state.spinBuckets.size)
    state.spinBuckets.forEach { signature = mix(signature, it.toRawBits()) }
    signature = mix(signature, state.spinCurrent.toRawBits())
    signature = mix(signature, state.spinClock.toRawBits())
    signature = mix(signature, state.spinLastAngle.toRawBits())
    signature = mix(signature, if (state.spinPrimed) 1 else 0)
    signature = mix(signature, if (state.omenPending) 1 else 0)
    signature = mix(signature, state.runOmen?.ordinal ?: -1)
    signature = mix(signature, state.runMutatorMask)
    signature = mix(signature, state.directorPersonality?.ordinal ?: -1)
    signature = mix(signature, state.featuredAspect)
    signature = mix(signature, state.surgeIndex)
    signature = mix(signature, state.surgeThemeType)
    signature = mix(signature, state.varietyAnnouncement)
    signature = mix(signature, state.keynoteTag?.ordinal ?: -1)
    signature = mix(signature, state.keynoteElement?.ordinal ?: -1)
    signature = mix(signature, state.worldEventPlans.toString().hashCode())
    signature = mix(signature, state.nextWorldEvent)
    signature = mix(signature, state.worldEvent.toString().hashCode())
    signature = mix(signature, state.worldEventPulse)
    signature = mix(signature, state.worldEventNextPulseAt.toRawBits())
    signature = mix(signature, state.eventSignals.toString().hashCode())
    signature = mix(signature, state.nextSignalSlot)
    signature = mix(signature, state.signalsSpawned)
    signature = mix(signature, state.signalsEngaged)
    signature = mix(signature, state.pendingSignalPrompts.toString().hashCode())
    signature = mix(signature, state.offerRarityFloor?.ordinal ?: -1)
    signature = mixLong(signature, state.secretBossMask)
    signature = mix(signature, state.encounters.toString().hashCode())
    val structures = state.structureField
    signature = mix(signature, listOf(structures.centerChunkX, structures.centerChunkY, structures.layer).toString().hashCode())
    signature = mix(signature, if (structures.bossCollapsed) 1 else 0)
    signature = mix(signature, structures.structures.toString().hashCode())
    signature = mix(signature, structures.walls.toString().hashCode())
    signature = mix(signature, structures.signals.toString().hashCode())
    signature = mix(signature, state.dungeon.toString().hashCode())
    signature = mix(signature, state.spentStructureIds.toString().hashCode())
    signature = mixLong(signature, state.lastAnnouncedStructureId)
    signature = mix(signature, state.worldLayer.ordinal)
    signature = mix(signature, state.worldKeepOut.toString().hashCode())
    signature = mix(signature, state.runBoons.toString().hashCode())
    signature = mix(signature, state.dimensionRun.toString().hashCode())
    signature = mix(signature, state.dimensionSlowSeconds.toRawBits())
    signature = mix(signature, state.dimensionScorchClock.toRawBits())
    signature = mix(signature, state.dimensionLocalClock.toRawBits())
    signature = mix(signature, state.dimensionRewardLayer?.ordinal ?: -1)
    return signature
}

private fun canonicalRenderFingerprint(state: MutableGameState): Long {
    val render = state.toRenderModel()
    var signature = -6_248_656_297_887_476_405L
    signature = mix(signature, render.phase.name.hashCode())
    signature = mix(signature, render.screenWidth.toRawBits())
    signature = mix(signature, render.screenHeight.toRawBits())
    signature = mix(signature, render.coreX.toRawBits())
    signature = mix(signature, render.coreY.toRawBits())
    signature = mix(signature, render.velocityX.toRawBits())
    signature = mix(signature, render.velocityY.toRawBits())
    signature = mix(signature, render.cameraX.toRawBits())
    signature = mix(signature, render.cameraY.toRawBits())
    signature = mix(signature, render.pointerX.toRawBits())
    signature = mix(signature, render.pointerY.toRawBits())
    signature = mix(signature, if (render.pointerActive) 1 else 0)
    signature = mix(signature, if (render.braking) 1 else 0)
    signature = mix(signature, render.elapsed.toRawBits())
    signature = mix(signature, render.heat.toRawBits())
    signature = mix(signature, if (render.overheated) 1 else 0)
    signature = mix(signature, render.hp.toRawBits())
    signature = mix(signature, render.maxHp.toRawBits())
    signature = mix(signature, render.shield.toRawBits())
    signature = mix(signature, render.maxShield.toRawBits())
    signature = mix(signature, render.level)
    signature = mix(signature, render.data)
    signature = mix(signature, render.nextLevelData)
    signature = mix(signature, render.keys)
    signature = mix(signature, render.kills)
    signature = mix(signature, render.combo)
    signature = mix(signature, render.comboTime.toRawBits())
    signature = mixLong(signature, render.runMatter)
    signature = mixLong(signature, render.totalMatter)
    signature = mix(signature, render.damageFlash.toRawBits())
    signature = mix(signature, render.screenShake.toRawBits())
    signature = mix(signature, render.message.hashCode())
    signature = mix(signature, render.messageTime.toRawBits())
    signature = mix(signature, render.weapon.ordinal)
    signature = mix(signature, render.weaponLevel)
    signature = mix(signature, render.overdriveCharge.toRawBits())
    signature = mix(signature, render.overdriveTime.toRawBits())
    signature = mix(signature, render.rerollsRemaining)
    signature = mix(signature, render.acquiredItemCount)
    signature = mix(signature, render.coreShape.ordinal)
    signature = mix(signature, render.coreRadius.toRawBits())
    signature = mix(signature, render.characterAbility.toString().hashCode())
    signature = mix(signature, render.sites.toString().hashCode())
    signature = mix(signature, if (render.directedChoice) 1 else 0)
    signature = mix(signature, render.effectiveWeaponPower.toRawBits())
    signature = mix(signature, render.abilityPower.toRawBits())
    signature = mix(signature, render.runProgress.toRawBits())
    signature = mix(signature, render.interpolationAlpha.toRawBits())
    signature = mix(signature, render.previousCoreX.toRawBits())
    signature = mix(signature, render.previousCoreY.toRawBits())
    signature = mix(signature, render.previousCameraX.toRawBits())
    signature = mix(signature, render.previousCameraY.toRawBits())
    signature = mix(signature, render.previousMorningstarX.toRawBits())
    signature = mix(signature, render.previousMorningstarY.toRawBits())
    signature = mix(signature, render.viewportDensity.toRawBits())
    signature = obstacleProjectionFingerprint(signature, render.obstacles)
    signature = mix(signature, render.minimapRange.toRawBits())
    signature = mix(signature, render.rebirthRank)
    signature = mix(signature, render.volatileBursts.toString().hashCode())
    render.enemies.forEach { signature = mix(signature, if (it.armored) 1 else 0) }
    render.itemStacksSnapshot.forEach { stack -> signature = mix(signature, stack) }
    render.enemies.forEach { signature = mix(signature, it.charges) }
    // 0.6.0 world facts: data-class text keeps enum names (stable across processes) where a hash code would not.
    signature = mix(signature, render.coreStraightness.toRawBits())
    signature = mix(signature, if (render.easyMode) 1 else 0)
    signature = mix(signature, render.ambientSites.toString().hashCode())
    signature = mix(signature, render.trial.toString().hashCode())
    signature = mix(signature, render.secretFlags)
    signature = mix(signature, render.goldenDrifterId)
    signature = mix(signature, render.structures.toString().hashCode())
    signature = mix(signature, render.dimension.toString().hashCode())
    render.runBoons.forEach { boon -> signature = mix(signature, boon) }
    signature = mix(signature, render.runOmen?.ordinal ?: -1)
    signature = mix(signature, render.runMutatorMask)
    signature = mix(signature, render.directorPersonality?.ordinal ?: -1)
    signature = mix(signature, render.featuredAspect)
    signature = mix(signature, render.keynoteTag?.ordinal ?: -1)
    signature = mix(signature, render.keynoteElement?.ordinal ?: -1)
    signature = mix(signature, render.worldEvent.toString().hashCode())
    signature = mix(signature, render.eventSignals.toString().hashCode())
    return mixLong(signature, fixtureFingerprint(state))
}

private fun canonicalDecisionOutcomeFingerprint(
    state: MutableGameState,
    outputFingerprint: Long,
    includeRender: Boolean,
): Long {
    var signature = mixLong(canonicalStateFingerprint(state), outputFingerprint)
    if (includeRender) {
        signature = mixLong(signature, canonicalRenderFingerprint(state))
    }
    return signature
}

private fun simulationOutputFingerprint(outputs: Iterable<SimulationOutput>): Long {
    var signature = SEMANTIC_OUTPUT_SEED
    outputs.forEach { output ->
        signature = when (output) {
            is SimulationOutput.EmitVisualFx ->
                appendSemanticBatch(signature, SEMANTIC_VISUAL_FX, output.cues)
            is SimulationOutput.AdvanceAudio -> appendSemanticAudio(
                signature,
                output.realDeltaSeconds,
                output.cues,
            )
            is SimulationOutput.PublishProgress -> mix(signature, SEMANTIC_PROFILE_UPDATE)
            SimulationOutput.EnsureAudioUnlocked -> mix(signature, SEMANTIC_AUDIO_UNLOCK)
        }
    }
    return signature
}

private fun gameplayOutputFingerprint(outputs: Iterable<GameplayOutput>): Long {
    var signature = SEMANTIC_OUTPUT_SEED
    outputs.forEach { output ->
        signature = when (output) {
            is GameplayOutput.EmitVisualFx ->
                appendSemanticBatch(signature, SEMANTIC_VISUAL_FX, output.cues)
            is GameplayOutput.AdvanceAudio -> appendSemanticAudio(
                signature,
                output.realDeltaSeconds,
                output.cues,
            )
            is GameplayOutput.SendProfileCommand -> mix(signature, SEMANTIC_PROFILE_UPDATE)
            GameplayOutput.EnsureAudioUnlocked -> mix(signature, SEMANTIC_AUDIO_UNLOCK)
            // This suite submits interaction inputs only; it must not silently add a
            // command-completion workload when the application's command API changes.
            else -> error("Unexpected command completion in an interaction benchmark")
        }
    }
    return signature
}

private fun stateCardinality(state: MutableGameState, seed: Int): Map<String, String> = baseMetadata(
    seed,
    "enemies" to state.enemies.size.toString(),
    "projectiles" to state.projectiles.size.toString(),
    "pickups" to state.pickups.size.toString(),
    "trail" to state.trail.size.toString(),
    "fixtureFingerprint" to fixtureFingerprint(state).toString(),
)

private fun fixtureFingerprint(state: MutableGameState): Long {
    val render = state.toRenderModel()
    var signature = -3_750_763_034_362_895_579L
    render.enemies.forEach { enemy ->
        signature = mix(signature, enemy.id)
        signature = mix(signature, enemy.type.ordinal)
        signature = mix(signature, enemy.x.toRawBits())
        signature = mix(signature, enemy.y.toRawBits())
        signature = mix(signature, enemy.vx.toRawBits())
        signature = mix(signature, enemy.vy.toRawBits())
        signature = mix(signature, enemy.hp.toRawBits())
        signature = mix(signature, enemy.maxHp.toRawBits())
        signature = mix(signature, enemy.radius.toRawBits())
        signature = mix(signature, enemy.actionTimer.toRawBits())
        signature = mix(signature, enemy.flash.toRawBits())
        signature = mix(signature, enemy.contactCooldown.toRawBits())
        signature = mix(signature, enemy.weaponCooldown.toRawBits())
        signature = mix(signature, enemy.previousX.toRawBits())
        signature = mix(signature, enemy.previousY.toRawBits())
        signature = mix(signature, if (enemy.dead) 1 else 0)
        signature = mix(signature, enemy.behavior)
        signature = mix(signature, enemy.behaviorRemaining.toRawBits())
        signature = mix(signature, enemy.facing.toRawBits())
        signature = mix(signature, enemy.linkedTo)
        signature = mix(signature, if (enemy.shielded) 1 else 0)
    }
    render.projectiles.forEach { projectile ->
        signature = mix(signature, projectile.x.toRawBits())
        signature = mix(signature, projectile.y.toRawBits())
        signature = mix(signature, projectile.vx.toRawBits())
        signature = mix(signature, projectile.vy.toRawBits())
        signature = mix(signature, projectile.radius.toRawBits())
        signature = mix(signature, projectile.life.toRawBits())
        signature = mix(signature, if (projectile.hostile) 1 else 0)
        signature = mix(signature, projectile.damage.toRawBits())
        signature = mix(signature, projectile.pierce)
        signature = mix(signature, projectile.colorIndex)
        signature = mix(signature, projectile.sourceWeapon?.ordinal ?: -1)
        signature = mix(signature, projectile.previousX.toRawBits())
        signature = mix(signature, projectile.previousY.toRawBits())
        signature = mix(signature, projectile.kind.ordinal)
    }
    render.pickups.forEach { pickup ->
        signature = mix(signature, pickup.type.ordinal)
        signature = mix(signature, pickup.x.toRawBits())
        signature = mix(signature, pickup.y.toRawBits())
        signature = mix(signature, pickup.vx.toRawBits())
        signature = mix(signature, pickup.vy.toRawBits())
        signature = mix(signature, pickup.life.toRawBits())
        signature = mix(signature, pickup.previousX.toRawBits())
        signature = mix(signature, pickup.previousY.toRawBits())
    }
    render.trail.forEach { point ->
        signature = mix(signature, point.x.toRawBits())
        signature = mix(signature, point.y.toRawBits())
        signature = mix(signature, point.age.toRawBits())
        signature = mix(signature, point.life.toRawBits())
        signature = mix(signature, point.scale.toRawBits())
    }
    render.weaponNodes.forEach { node ->
        signature = mix(signature, node.type.ordinal)
        signature = mix(signature, node.x.toRawBits())
        signature = mix(signature, node.y.toRawBits())
        signature = mix(signature, node.life.toRawBits())
        signature = mix(signature, node.maxLife.toRawBits())
        signature = mix(signature, node.radius.toRawBits())
    }
    render.weaponOrbitals.forEach { orbital ->
        signature = mix(signature, orbital.index)
        signature = mix(signature, orbital.x.toRawBits())
        signature = mix(signature, orbital.y.toRawBits())
        signature = mix(signature, orbital.radius.toRawBits())
        signature = mix(signature, orbital.previousX.toRawBits())
        signature = mix(signature, orbital.previousY.toRawBits())
    }
    signature = mix(signature, render.weaponEntities.size)
    render.weaponEntities.forEach { entity ->
        signature = mix(signature, entity.kind.ordinal)
        signature = mix(signature, entity.x.toRawBits())
        signature = mix(signature, entity.y.toRawBits())
        signature = mix(signature, entity.previousX.toRawBits())
        signature = mix(signature, entity.previousY.toRawBits())
        signature = mix(signature, entity.endX.toRawBits())
        signature = mix(signature, entity.endY.toRawBits())
        signature = mix(signature, entity.previousEndX.toRawBits())
        signature = mix(signature, entity.previousEndY.toRawBits())
        signature = mix(signature, entity.angle.toRawBits())
        signature = mix(signature, entity.previousAngle.toRawBits())
        signature = mix(signature, entity.radius.toRawBits())
        signature = mix(signature, entity.extent.toRawBits())
        signature = mix(signature, entity.life.toRawBits())
        signature = mix(signature, entity.maxLife.toRawBits())
        signature = mix(signature, entity.phase)
    }
    val synergy = render.weaponSynergy
    signature = mix(signature, synergy.tagPoints)
    signature = mix(signature, synergy.tagBonus.toRawBits())
    signature = mix(signature, if (synergy.signatureActive) 1 else 0)
    signature = mix(signature, synergy.harmonizingRelic?.ordinal ?: -1)
    signature = mix(signature, synergy.infusionElement?.ordinal ?: -1)
    signature = mix(signature, synergy.scalingMultiplier.toRawBits())
    signature = mix(signature, synergy.masteryForms)
    signature = mix(signature, synergy.activeMasteryForm?.hashCode() ?: 0)
    render.totem?.let { totem ->
        signature = mix(signature, totem.x.toRawBits())
        signature = mix(signature, totem.y.toRawBits())
        signature = mix(signature, totem.pulse.toRawBits())
    }
    return signature
}

private fun baseMetadata(seed: Int, vararg values: Pair<String, String>): Map<String, String> = buildMap {
    put("seed", seed.toString())
    put("viewport", "1280x720@1")
    put("simulationHz", "120")
    put("gameplayContract", "release-0.2.0")
    putAll(values)
}

/**
 * The obstacle window field by field. `hashCode()` of an [Obstacle] mixes its [ObstacleKind] identity hash,
 * which differs between JVM processes, so the published outcome would not compare across forks; the kind
 * contributes its ordinal instead.
 */
internal fun obstacleFieldFingerprint(current: Long, obstacles: List<Obstacle>): Long {
    var signature = mix(current, obstacles.size)
    obstacles.forEach { obstacle ->
        signature = mixLong(signature, obstacle.id)
        signature = mix(signature, obstacle.kind.ordinal)
        signature = mix(signature, obstacle.x.toRawBits())
        signature = mix(signature, obstacle.y.toRawBits())
        signature = mix(signature, obstacle.radius.toRawBits())
        signature = mix(signature, obstacle.shapeSeed)
        signature = mix(signature, obstacle.ricochetReadyAt.toRawBits())
        signature = mix(signature, obstacle.fuseAt.toRawBits())
        signature = mix(signature, if (obstacle.repels) 1 else 0)
    }
    return signature
}

/** Projected obstacles field by field, for the same reason as [obstacleFieldFingerprint]. */
internal fun obstacleProjectionFingerprint(current: Long, obstacles: List<ObstacleProjection>): Long {
    var signature = mix(current, obstacles.size)
    obstacles.forEach { obstacle ->
        signature = mixLong(signature, obstacle.id)
        signature = mix(signature, obstacle.kind.ordinal)
        signature = mix(signature, obstacle.x.toRawBits())
        signature = mix(signature, obstacle.y.toRawBits())
        signature = mix(signature, obstacle.radius.toRawBits())
        signature = mix(signature, obstacle.shapeSeed)
        signature = mix(signature, obstacle.fuseAt.toRawBits())
        signature = mix(signature, if (obstacle.repels) 1 else 0)
    }
    return signature
}

private fun mix(current: Long, value: Int): Long =
    (current xor value.toLong()) * -7046029254386353131L

private fun mixLong(current: Long, value: Long): Long =
    (current xor value) * -7046029254386353131L

private fun appendSemanticBatch(
    current: Long,
    tag: Int,
    values: Iterable<*>,
): Long {
    val materialized = values.toList()
    var signature = mix(current, tag)
    signature = mix(signature, materialized.size)
    materialized.forEach { value -> signature = mix(signature, value.toString().hashCode()) }
    return signature
}

private fun appendSemanticAudio(
    current: Long,
    deltaSeconds: Float,
    cues: Iterable<*>,
): Long = appendSemanticBatch(
    mix(mix(current, SEMANTIC_AUDIO), deltaSeconds.toRawBits()),
    tag = 0,
    values = cues,
)
