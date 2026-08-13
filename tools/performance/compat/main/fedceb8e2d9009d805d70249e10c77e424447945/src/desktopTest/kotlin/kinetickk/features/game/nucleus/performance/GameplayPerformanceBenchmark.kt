// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.game.nucleus.performance

import kinetickk.application.runtime.Accepted
import kinetickk.application.runtime.DecisionResult
import kinetickk.features.game.nucleus.EnemyType
import kinetickk.features.game.nucleus.GamePhase
import kinetickk.features.game.nucleus.ItemCatalog
import kinetickk.features.game.nucleus.MutableGameState
import kinetickk.features.game.nucleus.Pickup
import kinetickk.features.game.nucleus.PickupType
import kinetickk.features.game.nucleus.Projectile
import kinetickk.features.game.nucleus.TrailPoint
import kinetickk.features.game.nucleus.protocol.GameDecisionContext
import kinetickk.features.game.nucleus.protocol.EffectRequest
import kinetickk.features.game.nucleus.protocol.GameEffect
import kinetickk.features.game.nucleus.protocol.GameIntent
import kinetickk.features.game.nucleus.protocol.GameProjectionPayload
import kinetickk.features.game.nucleus.protocol.GamePulse
import kinetickk.features.game.nucleus.protocol.OperationId
import kinetickk.features.game.nucleus.protocol.ProjectionOutput
import kinetickk.features.game.nucleus.protocol.SemanticOutput
import kinetickk.features.game.nucleus.transition.GameBallState
import kinetickk.features.game.nucleus.transition.GameNucleus
import kinetickk.features.game.nucleus.projection.GameProjection
import kinetickk.performance.BenchmarkScenario
import kinetickk.performance.BenchmarkSuiteIdentity
import kinetickk.performance.runBenchmarkSuite

private const val SUITE_VERSION = "gameplay-core-v1"
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
            adapter = "main-fedceb8-compat",
            label = System.getProperty("kinetickk.benchmark.label", "main"),
            revision = System.getProperty(
                "kinetickk.benchmark.revision",
                "fedceb8e2d9009d805d70249e10c77e424447945",
            ),
            dirty = System.getProperty("kinetickk.benchmark.dirty", "false").toBoolean(),
        ),
        scenarios = fixtures.scenarios(),
    )
}

private class GameplayBenchmarkFixtures(private val seed: Int) {
    private val frame60 = GameIntent.FrameElapsed(1f / 60f)
    private val frame100Millis = GameIntent.FrameElapsed(0.1f)
    private val pointerMove = GameIntent.PointerMoved(1_024f, 288f)
    private val viewportChange = GameIntent.ViewportChanged(1_920f, 1_080f, 2f)
    private val nucleus = GameNucleus()
    private val idle = newRunningState(seed)
    private val capacity = capacityState(seed + 1)
    private val collisionMiss = collisionState(seed + 2, hit = false)
    private val collisionHit = collisionState(seed + 3, hit = true)
    private val paused = newRunningState(seed + 4).apply { togglePause() }
    private val idleAuthority = GameBallState(idle)
    private val capacityAuthority = GameBallState(capacity)
    private val pausedAuthority = GameBallState(paused)
    private var controlCounter = 1L
    private val traceFingerprint = deterministicTraceFingerprint(seed + 9)

    fun scenarios(): List<BenchmarkScenario> = listOf(
        BenchmarkScenario(
            name = "harness_control",
            category = "harness",
            description = "Lambda, loop, timer, counter and blackhole floor for this harness.",
            maximumOperations = 5_000_000,
        ) {
            controlCounter = controlCounter * 2_862_933_555_777_941_757L + 3_037_000_493L
            controlCounter
        },
        BenchmarkScenario(
            name = "state_initialization",
            category = "lifecycle",
            description = "Create deterministic mutable gameplay state with captured canonical content.",
            metadata = baseMetadata(seed, "entities" to "opening"),
            maximumOperations = 200_000,
        ) {
            stateSignature(MutableGameState(seed = seed))
        },
        BenchmarkScenario(
            name = "run_start",
            category = "lifecycle",
            description = "Create and fully reset/start one deterministic run.",
            metadata = baseMetadata(seed, "entities" to "opening"),
            maximumOperations = 100_000,
        ) {
            val state = MutableGameState(seed = seed)
            state.startRun()
            stateSignature(state)
        },
        BenchmarkScenario(
            name = "copy_idle",
            category = "state",
            description = "Isolated transaction copy of an opening-run state.",
            metadata = stateCardinality(idle, seed),
            maximumOperations = 100_000,
        ) {
            stateSignature(idle.copyForDecision())
        },
        BenchmarkScenario(
            name = "copy_capacity",
            category = "state",
            description = "Isolated transaction copy at all bounded entity collection capacities.",
            metadata = stateCardinality(capacity, seed),
            maximumOperations = 20_000,
        ) {
            stateSignature(capacity.copyForDecision())
        },
        BenchmarkScenario(
            name = "render_model_idle",
            category = "projection",
            description = "Build immutable render model for an opening-run state.",
            metadata = stateCardinality(idle, seed),
            maximumOperations = 100_000,
        ) {
            renderSignature(idle)
        },
        BenchmarkScenario(
            name = "render_model_capacity",
            category = "projection",
            description = "Build immutable render model at all bounded entity collection capacities.",
            metadata = stateCardinality(capacity, seed),
            maximumOperations = 20_000,
        ) {
            renderSignature(capacity)
        },
        BenchmarkScenario(
            name = "reducer_frame_60hz_idle",
            category = "reducer",
            description = "Copy, simulate two fixed steps and map reducer outputs for one 60 Hz frame.",
            metadata = stateCardinality(idle, seed) + baseMetadata(seed, "fixedSteps" to "2"),
            maximumOperations = 50_000,
        ) {
            reducerSignature(idle, frame60)
        },
        BenchmarkScenario(
            name = "reducer_frame_100ms_idle",
            category = "reducer",
            description = "Copy, simulate twelve fixed steps and map outputs for a 100 ms spike frame.",
            metadata = stateCardinality(idle, seed) + baseMetadata(seed, "fixedSteps" to "12"),
            maximumOperations = 10_000,
        ) {
            reducerSignature(idle, frame100Millis)
        },
        BenchmarkScenario(
            name = "fixed_step_collision_miss",
            category = "simulation",
            description = "Copy and execute one fixed step with 90 enemies and 325 projectiles that never hit.",
            metadata = stateCardinality(collisionMiss, seed) + baseMetadata(seed, "collisionMode" to "miss"),
            maximumOperations = 10_000,
        ) {
            val candidate = collisionMiss.copyForDecision()
            candidate.update(MutableGameState.FIXED_STEP)
            check(candidate.lastTransitionSteps == 1) { "Expected exactly one fixed step" }
            stateSignature(candidate)
        },
        BenchmarkScenario(
            name = "fixed_step_collision_hit",
            category = "simulation",
            description = "Copy and execute one fixed step with a dense projectile/enemy hit matrix.",
            metadata = stateCardinality(collisionHit, seed) + baseMetadata(seed, "collisionMode" to "hit"),
            maximumOperations = 5_000,
        ) {
            val candidate = collisionHit.copyForDecision()
            candidate.update(MutableGameState.FIXED_STEP)
            check(candidate.lastTransitionSteps == 1) { "Expected exactly one fixed step" }
            stateSignature(candidate)
        },
        BenchmarkScenario(
            name = "nucleus_frame_60hz_idle",
            category = "nucleus",
            description = "Full Gameplay Nucleus frame decision, output mapping and render snapshot.",
            metadata = stateCardinality(idle, seed) + baseMetadata(seed, "fixedSteps" to "2"),
            maximumOperations = 50_000,
        ) {
            authoritySignature(idleAuthority, frame60, includeRenderSnapshot = true)
        },
        BenchmarkScenario(
            name = "nucleus_frame_60hz_capacity",
            category = "nucleus",
            description = "Full frame decision plus render snapshot at collection capacities.",
            metadata = stateCardinality(capacity, seed) + baseMetadata(seed, "fixedSteps" to "2"),
            maximumOperations = 5_000,
        ) {
            authoritySignature(capacityAuthority, frame60, includeRenderSnapshot = true)
        },
        BenchmarkScenario(
            name = "nucleus_pointer_move_idle",
            category = "input",
            description = "Validated pointer pulse through copy, decision, outputs and render snapshot.",
            metadata = stateCardinality(idle, seed) + baseMetadata(seed, "input" to "pointer"),
            maximumOperations = 100_000,
        ) {
            authoritySignature(idleAuthority, pointerMove, includeRenderSnapshot = true)
        },
        BenchmarkScenario(
            name = "nucleus_viewport_change_idle",
            category = "input",
            description = "Validated resize/density pulse through decision and render publication.",
            metadata = stateCardinality(idle, seed) + baseMetadata(seed, "viewport" to "1920x1080@2"),
            maximumOperations = 100_000,
        ) {
            authoritySignature(idleAuthority, viewportChange, includeRenderSnapshot = true)
        },
        BenchmarkScenario(
            name = "nucleus_frame_paused",
            category = "phase",
            description = "Frame pulse, transaction copy and render publication while paused.",
            metadata = stateCardinality(paused, seed) + baseMetadata(seed, "phase" to "PAUSED"),
            maximumOperations = 100_000,
        ) {
            authoritySignature(pausedAuthority, frame60, includeRenderSnapshot = true)
        },
        BenchmarkScenario(
            name = "published_frame_60hz_idle",
            category = "publication_pipeline",
            description = "Model branch-specific projection work for one accepted 60 Hz UI frame.",
            metadata = stateCardinality(idle, seed) + baseMetadata(seed, "fixedSteps" to "2"),
            maximumOperations = 50_000,
        ) {
            publishedPipelineSignature(idleAuthority, frame60)
        },
        BenchmarkScenario(
            name = "published_pointer_move_idle",
            category = "publication_pipeline",
            description = "Model branch-specific query, preflight and publication work for one pointer move.",
            metadata = stateCardinality(idle, seed) + baseMetadata(seed, "input" to "pointer"),
            maximumOperations = 50_000,
        ) {
            publishedPipelineSignature(idleAuthority, pointerMove)
        },
        BenchmarkScenario(
            name = "published_frame_paused",
            category = "publication_pipeline",
            description = "Model branch-specific projection work for one accepted paused UI frame.",
            metadata = stateCardinality(paused, seed) + baseMetadata(seed, "phase" to "PAUSED"),
            maximumOperations = 50_000,
        ) {
            publishedPipelineSignature(pausedAuthority, frame60)
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
        ) {
            deterministicTraceFingerprint(seed + 9)
        },
    ).map { scenario ->
        if (scenario.category == "harness") {
            scenario
        } else {
            scenario.copy(
                metadata = scenario.metadata +
                    ("outcomeFingerprint" to outcomeFingerprint(scenario.name).toString()),
            )
        }
    }

    private fun outcomeFingerprint(name: String): Long = when (name) {
        "state_initialization" -> canonicalStateFingerprint(MutableGameState(seed = seed))
        "run_start" -> canonicalStateFingerprint(
            MutableGameState(seed = seed).apply { startRun() },
        )
        "copy_idle" -> canonicalStateFingerprint(idle.copyForDecision())
        "copy_capacity" -> canonicalStateFingerprint(capacity.copyForDecision())
        "render_model_idle" -> canonicalRenderFingerprint(idle)
        "render_model_capacity" -> canonicalRenderFingerprint(capacity)
        "reducer_frame_60hz_idle" ->
            decisionOutcomeFingerprint(idleAuthority, frame60, includeRender = false)
        "reducer_frame_100ms_idle" ->
            decisionOutcomeFingerprint(idleAuthority, frame100Millis, includeRender = false)
        "fixed_step_collision_miss" -> fixedStepOutcomeFingerprint(collisionMiss)
        "fixed_step_collision_hit" -> fixedStepOutcomeFingerprint(collisionHit)
        "nucleus_frame_60hz_idle", "published_frame_60hz_idle" ->
            decisionOutcomeFingerprint(idleAuthority, frame60, includeRender = true)
        "nucleus_frame_60hz_capacity" ->
            decisionOutcomeFingerprint(capacityAuthority, frame60, includeRender = true)
        "nucleus_pointer_move_idle", "published_pointer_move_idle" ->
            decisionOutcomeFingerprint(idleAuthority, pointerMove, includeRender = true)
        "nucleus_viewport_change_idle" ->
            decisionOutcomeFingerprint(idleAuthority, viewportChange, includeRender = true)
        "nucleus_frame_paused", "published_frame_paused" ->
            decisionOutcomeFingerprint(pausedAuthority, frame60, includeRender = true)
        "trace_2s_60hz" -> traceFingerprint
        else -> error("Missing semantic outcome fingerprint for benchmark scenario: $name")
    }

    private fun fixedStepOutcomeFingerprint(source: MutableGameState): Long =
        source.copyForDecision().run {
            update(MutableGameState.FIXED_STEP)
            check(lastTransitionSteps == 1) { "Expected exactly one fixed step" }
            canonicalStateFingerprint(this)
        }

    private fun decisionOutcomeFingerprint(
        state: GameBallState,
        pulse: GamePulse,
        includeRender: Boolean,
    ): Long {
        val decision = acceptedDecision(
            nucleus.decide(state, pulse, decisionContext(operationId = 1uL)),
        )
        return canonicalDecisionOutcomeFingerprint(
            state = decision.nextState.model,
            outputFingerprint = semanticOutputFingerprint(decision.outputs),
            includeRender = includeRender,
        )
    }

    private fun reducerSignature(model: MutableGameState, pulse: GameIntent): Long =
        authoritySignature(GameBallState(model), pulse, includeRenderSnapshot = false)

    private fun authoritySignature(
        state: GameBallState,
        pulse: GamePulse,
        includeRenderSnapshot: Boolean,
    ): Long {
        val decision = acceptedDecision(
            nucleus.decide(state, pulse, decisionContext(operationId = 1uL)),
        )
        val model = decision.nextState.model
        var signature = stateSignature(model) xor decision.outputs.size.toLong()
        if (includeRenderSnapshot) {
            val render = model.toProjection()
            signature = mix(signature, render.enemies.size)
            signature = mix(signature, render.projectiles.size)
            signature = mix(signature, render.elapsed.toRawBits())
        }
        return signature
    }

    private fun publishedPipelineSignature(
        state: GameBallState,
        pulse: GamePulse,
    ): Long {
        val decision = acceptedDecision(
            nucleus.decide(state, pulse, decisionContext(operationId = 1uL)),
        )
        // GameFeatureBall publishes one fresh projection after a commit. Its pointer query reads
        // the revision-keyed cached projection, so it does not build an additional projection.
        val render = decision.nextState.model.toProjection()
        var signature = stateSignature(decision.nextState.model)
        signature = mix(signature, render.enemies.size)
        signature = mix(signature, render.projectiles.size)
        signature = mix(signature, render.pickups.size)
        signature = mix(signature, render.trail.size)
        return signature
    }

    private fun deterministicTraceFingerprint(traceSeed: Int): Long {
        var state = GameBallState(newRunningState(traceSeed))
        var operationId = 1uL
        repeat(TRACE_FRAMES) { frameIndex ->
            val x = 640f + ((frameIndex * 37) % 560)
            val y = 360f + ((frameIndex * 19) % 240) - 120f
            state = acceptedDecision(
                nucleus.decide(
                    state,
                    GameIntent.PointerMoved(x, y),
                    decisionContext(operationId++),
                ),
            ).nextState
            state = acceptedDecision(
                nucleus.decide(state, frame60, decisionContext(operationId++)),
            ).nextState
            state.model.toProjection()
        }
        return canonicalStateFingerprint(state.model)
    }
}

private fun newRunningState(seed: Int): MutableGameState = MutableGameState(seed = seed).apply {
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

private fun decisionContext(operationId: ULong): GameDecisionContext =
    GameDecisionContext(operationId = OperationId(operationId))

private fun acceptedDecision(
    result: DecisionResult<GameBallState, SemanticOutput>,
) = (result as? Accepted)?.decision ?: error("Benchmark Nucleus pulse was rejected: $result")

private fun renderSignature(state: MutableGameState): Long {
    val render = state.toProjection()
    var signature = render.elapsed.toRawBits().toLong()
    signature = mix(signature, render.enemies.size)
    signature = mix(signature, render.projectiles.size)
    signature = mix(signature, render.pickups.size)
    signature = mix(signature, render.trail.size)
    signature = mix(signature, ItemCatalog.ITEM_COUNT)
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
    val render = state.toProjection()
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
    repeat(ItemCatalog.ITEM_COUNT) { itemId ->
        signature = mix(signature, render.itemStack(itemId))
    }
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

private fun semanticOutputFingerprint(outputs: Iterable<SemanticOutput>): Long {
    var signature = SEMANTIC_OUTPUT_SEED
    outputs.forEach { output ->
        signature = when (output) {
            is ProjectionOutput -> when (val payload = output.payload) {
                is GameProjectionPayload.GameProjectionChanged -> if (payload.visualFxCues.isEmpty()) {
                    signature
                } else {
                    appendSemanticBatch(signature, SEMANTIC_VISUAL_FX, payload.visualFxCues)
                }
            }
            is EffectRequest -> when (val effect = output.payload) {
                is GameEffect.AdvanceAudio -> appendSemanticAudio(
                    signature,
                    effect.realDeltaSeconds,
                    effect.cues,
                )
                GameEffect.EnsureAudioUnlocked -> mix(signature, SEMANTIC_AUDIO_UNLOCK)
                is GameEffect.PersistProgress -> mix(signature, SEMANTIC_PROFILE_UPDATE)
            }
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
    val render = state.toProjection()
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
