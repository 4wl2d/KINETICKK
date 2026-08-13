// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.performance

import kinetickk.ball.gameplay.api.GameplayInstanceId
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
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.TrailPoint
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
import kinetickk.ball.gameplay.nucleus.reducer.GameReducer
import kinetickk.ball.gameplay.nucleus.reducer.GameReductionResult
import kinetickk.ball.gameplay.nucleus.protocol.SimulationOutput
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.simulation.MutableGameState
import kinetickk.ball.gameplay.nucleus.simulation.addEnemyForTesting
import kinetickk.ball.gameplay.nucleus.simulation.consumeFixedStepBudget
import kinetickk.ball.gameplay.nucleus.simulation.copyForReduction
import kinetickk.ball.gameplay.nucleus.simulation.simulateStep
import kinetickk.ball.gameplay.nucleus.simulation.startRun
import kinetickk.ball.gameplay.nucleus.simulation.takeSoundCues
import kinetickk.ball.gameplay.nucleus.simulation.takeVisualFxCues
import kinetickk.ball.gameplay.nucleus.simulation.toRenderModel
import kinetickk.ball.gameplay.nucleus.simulation.togglePause
import kinetickk.ball.gameplay.nucleus.simulation.update
import kinetickk.ball.gameplay.nucleus.simulation.updatePointer
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.performance.BenchmarkScenario
import kinetickk.performance.BenchmarkSuiteIdentity
import kinetickk.performance.BenchmarkValidation
import kinetickk.performance.runBenchmarkSuite

private const val SUITE_VERSION = "gameplay-core-v2"
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
    val fixtures = GameplayBenchmarkFixtures(seed)
    runBenchmarkSuite(
        identity = BenchmarkSuiteIdentity(
            suiteVersion = SUITE_VERSION,
            adapter = "feature-pokeball-full-refactor",
            label = System.getProperty("kinetickk.benchmark.label", "feature/pokeball-full-refactor"),
            revision = System.getProperty("kinetickk.benchmark.revision", "unknown"),
            dirty = System.getProperty("kinetickk.benchmark.dirty", "false").toBoolean(),
        ),
        scenarios = fixtures.scenarios(),
    )
}

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

private fun authorityState(model: MutableGameState): GameplayState = GameplayState(
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
    pendingProfileCommand = null,
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
    return signature
}

private fun canonicalStateFingerprint(state: MutableGameState): Long {
    var signature = stateSignature(state)
    signature = mix(signature, state.level)
    signature = mix(signature, state.data)
    signature = mix(signature, state.kills)
    signature = mix(signature, state.heat.toRawBits())
    signature = mix(signature, state.screenWidth.toRawBits())
    signature = mix(signature, state.screenHeight.toRawBits())
    signature = mix(signature, state.pointerX.toRawBits())
    signature = mix(signature, state.pointerY.toRawBits())
    return mixLong(signature, fixtureFingerprint(state))
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
    render.itemStacksSnapshot.forEach { stack -> signature = mix(signature, stack) }
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
            is GameplayOutput.CompleteCommand -> mix(signature, SEMANTIC_COMMAND_COMPLETION)
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
    }
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
    putAll(values)
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
