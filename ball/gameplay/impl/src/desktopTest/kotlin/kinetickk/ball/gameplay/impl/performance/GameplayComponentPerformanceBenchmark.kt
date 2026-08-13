// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl.performance

import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayCommandIngressResult
import kinetickk.ball.gameplay.api.GameplayCommandSource
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayModuleCommand
import kinetickk.ball.gameplay.api.GameplayModuleCommandRequest
import kinetickk.ball.gameplay.api.GameplayModuleResultDelivery
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.GameplaySemanticHandle
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.impl.GameComponent
import kinetickk.ball.gameplay.impl.GameplayAudioExecutor
import kinetickk.ball.gameplay.impl.SyntheticGameplayContent
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderSnapshot
import kinetickk.ball.profile.api.GameplayProfileRoute
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileCommandIngressResult
import kinetickk.ball.profile.api.ProfileModuleCommandRequest
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.foundation.collections.ImmutableList
import kinetickk.performance.BenchmarkScenario
import kinetickk.performance.BenchmarkSuiteIdentity
import kinetickk.performance.BenchmarkValidation
import kinetickk.performance.BenchmarkValidationContext
import kinetickk.performance.runBenchmarkSuite

private const val SUITE_VERSION = "gameplay-component-v2"
private const val DEFAULT_SEED = 731_991
private const val OPERATIONS_PER_ITERATION = 128
private val BENCHMARK_RUN_ID = RunId(31)
private val BENCHMARK_PROFILE_REVISION = ProfileRevision(10)

fun main() {
    val seed = System.getProperty("kinetickk.benchmark.seed")?.toIntOrNull() ?: DEFAULT_SEED
    runBenchmarkSuite(
        identity = BenchmarkSuiteIdentity(
            suiteVersion = SUITE_VERSION,
            adapter = "feature-pokeball-game-component",
            label = System.getProperty(
                "kinetickk.benchmark.label",
                "feature/pokeball-full-refactor",
            ),
            revision = System.getProperty("kinetickk.benchmark.revision", "unknown"),
            dirty = System.getProperty("kinetickk.benchmark.dirty", "false").toBoolean(),
        ),
        scenarios = GameplayComponentBenchmarkFixtures(seed).scenarios(),
    )
}

private class GameplayComponentBenchmarkFixtures(private val seed: Int) {
    private val frame60 = GameplayInteractionPulse.FrameElapsed.fromValidated(1f / 60f)
    private val pointerMove = GameplayInteractionPulse.PointerMoved.fromValidated(1_024f, 288f)

    fun scenarios(): List<BenchmarkScenario> = listOf(
        componentScenario(
            name = "component_frame_60hz_running",
            description =
                "Real GameComponent accept -> renderSnapshot -> visualFxSnapshot path for one " +
                    "running 60 Hz frame; a fresh pre-started fixture is consumed per operation.",
            pulse = frame60,
            pauseBeforeProbe = false,
        ),
        componentScenario(
            name = "component_pointer_move_running",
            description =
                "Real GameComponent accept -> renderSnapshot -> visualFxSnapshot path for one " +
                    "validated pointer move; a fresh pre-started fixture is consumed per operation.",
            pulse = pointerMove,
            pauseBeforeProbe = false,
        ),
        componentScenario(
            name = "component_frame_60hz_paused",
            description =
                "Real GameComponent accept -> renderSnapshot -> visualFxSnapshot path for one " +
                    "paused 60 Hz frame; a fresh pre-paused fixture is consumed per operation.",
            pulse = frame60,
            pauseBeforeProbe = true,
        ),
    )

    private fun componentScenario(
        name: String,
        description: String,
        pulse: GameplayInteractionPulse,
        pauseBeforeProbe: Boolean,
    ): BenchmarkScenario {
        val probe = ComponentPipelineProbe(seed, pulse, pauseBeforeProbe)
        val expected = probe.expectedContract()
        return BenchmarkScenario(
            name = name,
            category = "component_publication_pipeline",
            description = description,
            metadata = mapOf(
                "seed" to seed.toString(),
                "fixture" to "synthetic-gameplay-test",
                "initialPhase" to expected.initialPhase.name,
                "acceptedPhase" to expected.acceptedPhase.name,
                "initialRevision" to expected.initialRevision.toString(),
                "acceptedRevision" to expected.acceptedRevision.toString(),
                "operationsPerIteration" to OPERATIONS_PER_ITERATION.toString(),
                "preparedFixtureCount" to requiredPreparedOperationCount().toString(),
                "setupExcluded" to "create,start,pause-if-needed,baseline-snapshot",
                "measurementPath" to
                    "GameComponent.accept->renderSnapshot->visualFxSnapshot",
                "outcomeFingerprint" to expected.outcomeWitness.toString(),
            ),
            minimumOperations = OPERATIONS_PER_ITERATION,
            maximumOperations = OPERATIONS_PER_ITERATION,
            validation = BenchmarkValidation(
                expectedTimedResult = expected.timedResult,
                expectedOutcomeWitness = expected.outcomeWitness,
                prepareProbe = { probe.prepare() },
            ),
        ) { validation ->
            probe.execute(validation)
        }
    }
}

/**
 * Owns setup state, but never creates or starts a component inside the measured operation.
 * The fixed operation count makes the exact pool requirement derivable from the harness profile.
 */
private class ComponentPipelineProbe(
    private val seed: Int,
    private val pulse: GameplayInteractionPulse,
    private val pauseBeforeProbe: Boolean,
) {
    private val expectedInitialRevision: Long = if (pauseBeforeProbe) 2L else 1L
    private var pool: Array<PreparedPipeline?> = emptyArray()
    private var nextFixtureIndex: Int = 0

    fun expectedContract(): ExpectedPipelineContract {
        val prepared = newPreparedPipeline(seed, pauseBeforeProbe)
        val initialState = prepared.component.stateSnapshot()
        val acceptance = prepared.component.accept(pulse) as GameplayAcceptance.Accepted
        val snapshot = prepared.component.renderSnapshot()
        val render = checkNotNull(snapshot.renderModel)
        val visualFx = prepared.component.visualFxSnapshot()
        return ExpectedPipelineContract(
            initialRevision = initialState.revision.value,
            initialPhase = initialState.phase,
            acceptedRevision = acceptance.revision.value,
            acceptedPhase = prepared.component.stateSnapshot().phase,
            timedResult = timedPipelineSignature(acceptance, snapshot, render, visualFx, prepared.audio),
            outcomeWitness = canonicalPipelineFingerprint(
                component = prepared.component,
                acceptance = acceptance,
                snapshot = snapshot,
                render = render,
                visualFx = visualFx,
                audio = prepared.audio,
                expectedPriorRevision = initialState.revision.value,
            ),
        )
    }

    fun prepare() {
        pool = arrayOfNulls(requiredPreparedOperationCount())
        pool.indices.forEach { index ->
            pool[index] = newPreparedPipeline(seed, pauseBeforeProbe)
        }
        nextFixtureIndex = 0
    }

    fun execute(validation: BenchmarkValidationContext): Long {
        val prepared = takePreparedPipeline()
        val acceptance = prepared.component.accept(pulse) as GameplayAcceptance.Accepted
        val snapshot = prepared.component.renderSnapshot()
        val render = checkNotNull(snapshot.renderModel)
        val visualFx = prepared.component.visualFxSnapshot()
        val timedResult = timedPipelineSignature(
            acceptance,
            snapshot,
            render,
            visualFx,
            prepared.audio,
        )
        validation.observeOutcome {
            canonicalPipelineFingerprint(
                component = prepared.component,
                acceptance = acceptance,
                snapshot = snapshot,
                render = render,
                visualFx = visualFx,
                audio = prepared.audio,
                expectedPriorRevision = expectedInitialRevision,
            )
        }
        return timedResult
    }

    private fun takePreparedPipeline(): PreparedPipeline {
        check(nextFixtureIndex < pool.size) {
            "GameComponent benchmark fixture pool exhausted at operation $nextFixtureIndex"
        }
        val index = nextFixtureIndex++
        return checkNotNull(pool[index]).also { pool[index] = null }
    }
}

private data class ExpectedPipelineContract(
    val initialRevision: Long,
    val initialPhase: GameplayRunPhase,
    val acceptedRevision: Long,
    val acceptedPhase: GameplayRunPhase,
    val timedResult: Long,
    val outcomeWitness: Long,
)

private data class PreparedPipeline(
    val component: GameComponent,
    val audio: BenchmarkAudioExecutor,
)

private fun newPreparedPipeline(seed: Int, pauseBeforeProbe: Boolean): PreparedPipeline {
    val audio = BenchmarkAudioExecutor()
    val component = GameComponent.create(
        runId = BENCHMARK_RUN_ID,
        content = SyntheticGameplayContent,
        profilePort = BenchmarkProfilePort,
        audioExecutor = audio,
        commandResultSink = ::ignoreGameplayCommandResult,
        seed = seed,
    )
    val startRequest = GameplayModuleCommandRequest(
        semanticHandle = GameplaySemanticHandle(
            sourceInstance = GameplayCommandSource.LocalSession,
            sourceRevision = 0L,
            sourceOrdinal = 0,
        ),
        sourceOrdinal = 0,
        targetInstance = component.instanceId,
        command = GameplayModuleCommand.StartRun,
    )
    check(
        component.acceptFromSession(
            request = startRequest,
            causalScope = 1L,
            causalDepth = 0,
        ) is GameplayCommandIngressResult.Accepted,
    ) { "Benchmark GameComponent fixture could not start" }
    check(component.stateSnapshot().revision.value == 1L)
    check(component.stateSnapshot().phase == GameplayRunPhase.RUNNING)
    if (pauseBeforeProbe) {
        check(component.accept(GameplayInteractionPulse.PauseToggled) is GameplayAcceptance.Accepted) {
            "Benchmark GameComponent fixture could not pause"
        }
        check(component.stateSnapshot().revision.value == 2L)
        check(component.stateSnapshot().phase == GameplayRunPhase.PAUSED)
    }
    component.renderSnapshot()
    component.visualFxSnapshot()
    audio.reset()
    return PreparedPipeline(component, audio)
}

private fun ignoreGameplayCommandResult(@Suppress("UNUSED_PARAMETER") delivery: GameplayModuleResultDelivery) = Unit

private object BenchmarkProfilePort : GameplayProfileRoute {
    private val profile = PlayerProfile()
    private val snapshot = GameplayProfileSnapshot(
        preferences = profile.preferences,
        economy = profile.economy,
        loadout = profile.loadout,
        labProgress = profile.labProgress,
        collection = profile.collection,
        rebirthProgress = profile.rebirthProgress,
    )

    override val instanceId = LOCAL_PROFILE_INSTANCE_ID

    override fun acceptFromGameplay(
        request: ProfileModuleCommandRequest,
        causalScope: Long,
        causalDepth: Int,
    ): ProfileCommandIngressResult = error(
        "Opening-frame GameComponent benchmark unexpectedly attempted a Profile command: " +
            "$request scope=$causalScope depth=$causalDepth",
    )

    override fun query(query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection =
        RunBootstrapProjection(
            instanceId = instanceId,
            revision = BENCHMARK_PROFILE_REVISION,
            result = ProfileRunBootstrapResult.Ready(snapshot),
        )

    override fun query(query: ProfileQuery.GetPreferences): PreferencesProjection =
        PreferencesProjection(
            instanceId = instanceId,
            revision = BENCHMARK_PROFILE_REVISION,
            preferences = snapshot.preferences,
        )
}

private class BenchmarkAudioExecutor : GameplayAudioExecutor {
    var advanceCount: Int = 0
        private set
    var unlockCount: Int = 0
        private set
    var lastDeltaBits: Int = 0
        private set
    var lastCues: ImmutableList<GameplayAudioCue>? = null
        private set

    override fun advance(realDeltaSeconds: Float, cues: ImmutableList<GameplayAudioCue>) {
        advanceCount += 1
        lastDeltaBits = realDeltaSeconds.toRawBits()
        lastCues = cues
    }

    override fun ensureUnlocked() {
        unlockCount += 1
    }

    fun reset() {
        advanceCount = 0
        unlockCount = 0
        lastDeltaBits = 0
        lastCues = null
    }
}

private fun timedPipelineSignature(
    acceptance: GameplayAcceptance.Accepted,
    snapshot: GameplayRenderSnapshot,
    render: GameplayRenderModel,
    visualFx: VisualFxProjection,
    audio: BenchmarkAudioExecutor,
): Long {
    var signature = acceptance.revision.value
    signature = mixLong(signature, snapshot.revision.value)
    signature = mix(signature, render.phase.ordinal)
    signature = mix(signature, render.elapsed.toRawBits())
    signature = mix(signature, render.pointerX.toRawBits())
    signature = mix(signature, render.pointerY.toRawBits())
    signature = mix(signature, render.enemies.size)
    signature = mix(signature, render.projectiles.size)
    signature = mix(signature, render.pickups.size)
    signature = mix(signature, visualFx.particles.size)
    signature = mix(signature, visualFx.motionEchoes.size)
    signature = mix(signature, visualFx.shockwaves.size)
    signature = mix(signature, visualFx.damageNumbers.size)
    signature = mix(signature, visualFx.weaponArcs.size)
    signature = mix(signature, audio.advanceCount)
    signature = mix(signature, audio.lastDeltaBits)
    return mix(signature, audio.lastCues?.size ?: 0)
}

private fun canonicalPipelineFingerprint(
    component: GameComponent,
    acceptance: GameplayAcceptance.Accepted,
    snapshot: GameplayRenderSnapshot,
    render: GameplayRenderModel,
    visualFx: VisualFxProjection,
    audio: BenchmarkAudioExecutor,
    expectedPriorRevision: Long,
): Long {
    val state = component.stateSnapshot()
    check(state.instanceId == acceptance.instanceId)
    check(state.instanceId == snapshot.instanceId)
    check(state.revision == acceptance.revision)
    check(state.revision == snapshot.revision)
    check(state.revision.value == expectedPriorRevision + 1L)
    check(snapshot.renderModel === render)
    check(state.content === render.content)
    check(state.pendingProfileCommand == null)
    check(render.phase == state.phase.toRenderPhase())

    var signature = -3_750_763_034_362_895_579L
    signature = mixLong(signature, state.instanceId.runId.value)
    signature = mixLong(signature, state.revision.value)
    signature = mix(signature, state.phase.ordinal)
    signature = mixLong(signature, acceptance.revision.value)
    signature = mixLong(signature, snapshot.revision.value)
    signature = mixLong(signature, canonicalRenderFingerprint(render))
    signature = mixLong(signature, canonicalVisualFxFingerprint(visualFx))
    signature = mix(signature, audio.advanceCount)
    signature = mix(signature, audio.unlockCount)
    signature = mix(signature, audio.lastDeltaBits)
    val cues = audio.lastCues
    signature = mix(signature, cues?.size ?: 0)
    cues?.forEach { cue -> signature = mix(signature, cue.ordinal) }
    return signature
}

private fun canonicalRenderFingerprint(render: GameplayRenderModel): Long {
    var signature = -6_248_656_297_887_476_405L
    signature = mix(signature, render.content.version.value.hashCode())
    signature = mix(signature, render.phase.ordinal)
    signature = mix(signature, render.settings.hashCode())
    signature = mix(signature, render.rebirthLevel)
    signature = mix(signature, render.screenWidth.toRawBits())
    signature = mix(signature, render.screenHeight.toRawBits())
    signature = mix(signature, render.uiScale.toRawBits())
    signature = mix(signature, render.coreX.toRawBits())
    signature = mix(signature, render.coreY.toRawBits())
    signature = mix(signature, render.velocityX.toRawBits())
    signature = mix(signature, render.velocityY.toRawBits())
    signature = mix(signature, render.cameraX.toRawBits())
    signature = mix(signature, render.cameraY.toRawBits())
    signature = mix(signature, render.pointerX.toRawBits())
    signature = mix(signature, render.pointerY.toRawBits())
    signature = mix(signature, render.pointerActive.asInt())
    signature = mix(signature, render.braking.asInt())
    signature = mix(signature, render.elapsed.toRawBits())
    signature = mix(signature, render.heat.toRawBits())
    signature = mix(signature, render.overheated.asInt())
    signature = mix(signature, render.dashPhaseTime.toRawBits())
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
    signature = mix(signature, render.lastImpact.toRawBits())
    signature = mix(signature, render.lastImpactTime.toRawBits())
    signature = mix(signature, render.damageFlash.toRawBits())
    signature = mix(signature, render.runGrace.toRawBits())
    signature = mix(signature, render.screenShake.toRawBits())
    signature = mix(signature, render.message.hashCode())
    signature = mix(signature, render.messageTime.toRawBits())
    signature = mix(signature, render.mass.toRawBits())
    signature = mix(signature, render.damageMultiplier.toRawBits())
    signature = mix(signature, render.weaponPower.toRawBits())
    signature = mix(signature, render.coolingRate.toRawBits())
    signature = mix(signature, render.magnetStrength.toRawBits())
    signature = mix(signature, render.dashImpulse.toRawBits())
    signature = mix(signature, render.dashHeatCost.toRawBits())
    signature = mix(signature, render.regenPerSecond.toRawBits())
    signature = mix(signature, render.critChance.toRawBits())
    signature = mix(signature, render.critMultiplier.toRawBits())
    signature = mix(signature, render.pickupRadius.toRawBits())
    signature = mix(signature, render.luck.toRawBits())
    signature = mix(signature, render.dataGain.toRawBits())
    signature = mix(signature, render.matterGain.toRawBits())
    signature = mix(signature, render.attackSpeed.toRawBits())
    signature = mix(signature, render.damageReduction.toRawBits())
    signature = mix(signature, render.comboWindow.toRawBits())
    signature = mix(signature, render.overdriveGain.toRawBits())
    signature = mix(signature, render.dragCoefficient.toRawBits())
    signature = mix(signature, render.polarityStability.toRawBits())
    signature = mix(signature, render.weapon.ordinal)
    signature = mix(signature, render.weaponLevel)
    signature = mix(signature, render.overdriveCharge.toRawBits())
    signature = mix(signature, render.overdriveTime.toRawBits())
    signature = mix(signature, render.rerollsRemaining)
    signature = mix(signature, render.acquiredItemCount)
    signature = mix(signature, render.recentItem?.id ?: -1)
    signature = mix(signature, render.equippedRelics.size)
    render.equippedRelics.forEach { relic -> signature = mix(signature, relic.hashCode()) }
    signature = mix(signature, render.morningstarAngle.toRawBits())
    signature = mix(signature, render.morningstarX.toRawBits())
    signature = mix(signature, render.morningstarY.toRawBits())
    signature = mix(signature, render.weaponBeamTime.toRawBits())
    signature = mix(signature, render.weaponBeamStartX.toRawBits())
    signature = mix(signature, render.weaponBeamStartY.toRawBits())
    signature = mix(signature, render.weaponBeamEndX.toRawBits())
    signature = mix(signature, render.weaponBeamEndY.toRawBits())
    signature = mix(signature, render.coreShape.ordinal)
    signature = mix(signature, render.choiceType.ordinal)
    signature = mix(signature, render.pendingRelicChoiceCount)
    signature = mix(signature, render.choices.size)
    render.choices.forEach { choice -> signature = mix(signature, choice.hashCode()) }
    signature = mix(signature, render.itemStacksSnapshot.size)
    render.itemStacksSnapshot.forEach { stack -> signature = mix(signature, stack) }
    signature = mix(signature, render.discoveredItemCount)
    RelicId.entries.forEach { relic -> signature = mix(signature, render.relicRank(relic)) }
    return appendRenderCollections(signature, render)
}

private fun appendRenderCollections(initial: Long, render: GameplayRenderModel): Long {
    var signature = mix(initial, render.enemies.size)
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
        signature = mix(signature, enemy.dead.asInt())
    }
    signature = mix(signature, render.projectiles.size)
    render.projectiles.forEach { projectile ->
        signature = mix(signature, projectile.x.toRawBits())
        signature = mix(signature, projectile.y.toRawBits())
        signature = mix(signature, projectile.vx.toRawBits())
        signature = mix(signature, projectile.vy.toRawBits())
        signature = mix(signature, projectile.radius.toRawBits())
        signature = mix(signature, projectile.life.toRawBits())
        signature = mix(signature, projectile.hostile.asInt())
        signature = mix(signature, projectile.damage.toRawBits())
        signature = mix(signature, projectile.pierce)
        signature = mix(signature, projectile.colorIndex)
        signature = mix(signature, projectile.sourceWeapon?.ordinal ?: -1)
        signature = mix(signature, projectile.previousX.toRawBits())
        signature = mix(signature, projectile.previousY.toRawBits())
    }
    signature = mix(signature, render.pickups.size)
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
    signature = mix(signature, render.trail.size)
    render.trail.forEach { point ->
        signature = mix(signature, point.x.toRawBits())
        signature = mix(signature, point.y.toRawBits())
        signature = mix(signature, point.age.toRawBits())
    }
    signature = mix(signature, render.weaponNodes.size)
    render.weaponNodes.forEach { node ->
        signature = mix(signature, node.type.ordinal)
        signature = mix(signature, node.x.toRawBits())
        signature = mix(signature, node.y.toRawBits())
        signature = mix(signature, node.life.toRawBits())
        signature = mix(signature, node.maxLife.toRawBits())
        signature = mix(signature, node.radius.toRawBits())
    }
    signature = mix(signature, render.weaponOrbitals.size)
    render.weaponOrbitals.forEach { orbital ->
        signature = mix(signature, orbital.index)
        signature = mix(signature, orbital.x.toRawBits())
        signature = mix(signature, orbital.y.toRawBits())
        signature = mix(signature, orbital.radius.toRawBits())
    }
    val totem = render.totem
    signature = mix(signature, if (totem == null) 0 else 1)
    if (totem != null) {
        signature = mix(signature, totem.x.toRawBits())
        signature = mix(signature, totem.y.toRawBits())
        signature = mix(signature, totem.pulse.toRawBits())
    }
    return signature
}

private fun canonicalVisualFxFingerprint(visualFx: VisualFxProjection): Long {
    var signature = 1_469_598_103_934_665_603L
    signature = mix(signature, visualFx.particles.size)
    visualFx.particles.forEach { particle ->
        signature = mix(signature, particle.x.toRawBits())
        signature = mix(signature, particle.y.toRawBits())
        signature = mix(signature, particle.vx.toRawBits())
        signature = mix(signature, particle.vy.toRawBits())
        signature = mix(signature, particle.life.toRawBits())
        signature = mix(signature, particle.maxLife.toRawBits())
        signature = mix(signature, particle.colorIndex)
        signature = mix(signature, particle.size.toRawBits())
    }
    signature = mix(signature, visualFx.motionEchoes.size)
    visualFx.motionEchoes.forEach { echo ->
        signature = mix(signature, echo.x.toRawBits())
        signature = mix(signature, echo.y.toRawBits())
        signature = mix(signature, echo.life.toRawBits())
        signature = mix(signature, echo.maxLife.toRawBits())
        signature = mix(signature, echo.intensity.toRawBits())
    }
    signature = mix(signature, visualFx.shockwaves.size)
    visualFx.shockwaves.forEach { shockwave ->
        signature = mix(signature, shockwave.x.toRawBits())
        signature = mix(signature, shockwave.y.toRawBits())
        signature = mix(signature, shockwave.life.toRawBits())
        signature = mix(signature, shockwave.maxLife.toRawBits())
        signature = mix(signature, shockwave.maxRadius.toRawBits())
        signature = mix(signature, shockwave.colorIndex)
    }
    signature = mix(signature, visualFx.damageNumbers.size)
    visualFx.damageNumbers.forEach { damage ->
        signature = mix(signature, damage.x.toRawBits())
        signature = mix(signature, damage.y.toRawBits())
        signature = mixLong(signature, damage.amount)
        signature = mix(signature, damage.critical.asInt())
        signature = mix(signature, damage.life.toRawBits())
        signature = mix(signature, damage.compactAmount.hashCode())
        signature = mix(signature, damage.fullAmount.hashCode())
    }
    signature = mix(signature, visualFx.weaponArcs.size)
    visualFx.weaponArcs.forEach { arc ->
        signature = mix(signature, arc.fromX.toRawBits())
        signature = mix(signature, arc.fromY.toRawBits())
        signature = mix(signature, arc.toX.toRawBits())
        signature = mix(signature, arc.toY.toRawBits())
        signature = mix(signature, arc.life.toRawBits())
    }
    return signature
}

private fun GameplayRunPhase.toRenderPhase(): GamePhase = when (this) {
    GameplayRunPhase.RUNNING -> GamePhase.RUNNING
    GameplayRunPhase.PAUSED -> GamePhase.PAUSED
    GameplayRunPhase.CHOICE -> GamePhase.CHOICE
    GameplayRunPhase.GAME_OVER -> GamePhase.GAME_OVER
    GameplayRunPhase.VICTORY -> GamePhase.VICTORY
    GameplayRunPhase.EXITED -> GamePhase.PAUSED
    GameplayRunPhase.CREATED -> error("Created benchmark component exposed a render model")
}

private fun requiredPreparedOperationCount(): Int {
    val defaults = when (System.getProperty("kinetickk.benchmark.profile", "standard").lowercase()) {
        "smoke" -> 2 to 3
        "standard" -> 5 to 10
        "deep" -> 10 to 20
        else -> 5 to 10
    }
    val warmups = positiveProperty("kinetickk.benchmark.warmups", defaults.first)
    val measurements = positiveProperty("kinetickk.benchmark.measurements", defaults.second)
    // One semantic validation operation plus one fixed-size calibration batch and all samples.
    return 1 + OPERATIONS_PER_ITERATION * (1 + warmups + measurements)
}

private fun positiveProperty(name: String, default: Int): Int =
    System.getProperty(name)?.toIntOrNull()?.takeIf { it > 0 } ?: default

private fun Boolean.asInt(): Int = if (this) 1 else 0

private fun mix(current: Long, value: Int): Long =
    (current xor value.toLong()) * -7046029254386353131L

private fun mixLong(current: Long, value: Long): Long =
    (current xor value) * -7046029254386353131L
