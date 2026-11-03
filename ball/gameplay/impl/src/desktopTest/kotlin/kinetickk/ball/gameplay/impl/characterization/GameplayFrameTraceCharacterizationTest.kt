// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl.characterization

import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GameplayAcceptance
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.api.GameplayRefusal
import kinetickk.ball.gameplay.api.GameplayRejection
import kinetickk.ball.gameplay.api.GameplayRunExited
import kinetickk.ball.gameplay.api.GameplayRunStarted
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.api.RunMode
import kinetickk.ball.gameplay.impl.GameComponent
import kinetickk.ball.gameplay.impl.GameplayAudioExecutor
import kinetickk.ball.gameplay.impl.GameplayCommandTestCaller
import kinetickk.ball.gameplay.impl.SyntheticGameplayContent
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.ball.profile.api.HangarProjection
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.LoadoutProjection
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.MatrixProgressProjection
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerLoadout
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
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.dispatch.InlineReply
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Characterizes the owner frame path: [GameComponent] driven through a fixed pulse script, one printed line per pulse
 * with the acceptance, the published revision and phase, the owner's FX projection, the executed audio work, the
 * Profile commands and a render digest. A second run holds its Profile reply, so the pending-progress route and its
 * `ProgressPending` rejections are traced too.
 */
class GameplayFrameTraceCharacterizationTest {
    @Test
    fun ownerFramePathTraceIsStable() {
        val trace = FrameTraceRecorder()

        val accepting = FrameTraceProfile(holdReplies = false)
        val first = trace.component(RunId(41), accepting, TRACE_SEED)
        trace.accept(first, viewport)
        trace.start(first)
        trace.accept(first, viewport)
        val firstSummary = trace.script(first, steps = FIRST_RUN_STEPS)
        trace.exit(first)
        trace.accept(first, GameplayInteractionPulse.FrameElapsed.fromValidated(1f / 60f))

        val holding = FrameTraceProfile(holdReplies = true)
        val second = trace.component(RunId(42), holding, TRACE_SEED + 1)
        trace.start(second)
        trace.accept(second, viewport)
        val secondSummary = trace.script(second, steps = SECOND_RUN_STEPS)
        trace.exit(second)

        assertTrue(firstSummary.itemPicks > 0, "The first run never reached an item choice")
        assertTrue(firstSummary.rerolls > 0 && firstSummary.picks > 1, "The first run never rerolled and picked")
        assertTrue(accepting.applied > 0, "The first run never applied Profile progress")
        assertTrue(holding.held.isNotEmpty(), "The second run never sent Profile progress")
        assertTrue(trace.progressPendingRejections > 0, "The held Profile reply never caused a ProgressPending rejection")
        assertTrue(trace.viewportRejections == 4, "Both out-of-viewport pointers of each run must be rejected")
        check(secondSummary.picks > 0)
    }
}

private const val FIRST_RUN_STEPS = 600
private const val SECOND_RUN_STEPS = 600
private const val TRACE_SEED = 0x4B1D

private val viewport = GameplayInteractionPulse.ViewportChanged.fromValidated(1_440f, 810f, 1f)

/** 60 Hz and 144 Hz frames with jitter, in a fixed order. */
private val frameJitter = floatArrayOf(
    1f / 60f, 1f / 144f, 1f / 144f, 0.0172f, 1f / 60f, 0.0069f,
    1f / 144f, 0.0161f, 1f / 60f, 0.0074f, 1f / 144f, 1f / 60f,
)

private class FrameTraceScriptSummary(var picks: Int = 0, var itemPicks: Int = 0, var rerolls: Int = 0)

private class FrameTraceRecorder {
    private var index = 0
    private var audio = FrameTraceAudio()
    private var profile = FrameTraceProfile(holdReplies = false)
    var progressPendingRejections = 0
        private set
    var viewportRejections = 0
        private set

    fun component(runId: RunId, profile: FrameTraceProfile, seed: Int): GameComponent {
        audio = FrameTraceAudio()
        this.profile = profile
        return GameComponent.create(
            runId = runId,
            content = FrameTraceGameplayContent,
            profilePort = profile,
            profileProgress = profile,
            audioExecutor = audio,
            seed = seed,
        )
    }

    fun start(component: GameComponent) {
        val caller = GameplayCommandTestCaller<GameplayRunStarted>()
        val outcome = outcomeOf { caller.call { reply -> component.startRun(RunMode.STANDARD, reply) } }
        line(component, "start", outcome ?: caller.outcome())
    }

    fun exit(component: GameComponent) {
        val caller = GameplayCommandTestCaller<GameplayRunExited>()
        val outcome = outcomeOf { caller.call(component::exitRun) }
        val applied = caller.applied.singleOrNull()
        val result = outcome ?: applied?.let { "A:${it.progress}" } ?: caller.outcome()
        line(component, "exit", result)
    }

    fun accept(component: GameComponent, pulse: GameplayInteractionPulse) {
        var acceptance: GameplayAcceptance? = null
        val fault = outcomeOf { acceptance = component.accept(pulse) }
        val result = fault ?: when (val value = checkNotNull(acceptance)) {
            is GameplayAcceptance.Accepted -> "A"
            is GameplayAcceptance.Rejected -> {
                if (value.reason == GameplayRejection.ProgressPending) progressPendingRejections++
                if (value.reason is GameplayRejection.PointerOutsideViewport) viewportRejections++
                "R:${value.reason}"
            }
        }
        line(component, pulse.traceName(), result)
    }

    /** The fixed script of one run: a frame per step, with pointer, brake, dash, skill, Apex, pause and choice pulses. */
    fun script(component: GameComponent, steps: Int): FrameTraceScriptSummary {
        val summary = FrameTraceScriptSummary()
        var cursorAngle = 0f
        var choiceWait = 0
        var rerolledKinds = 0
        for (step in 0 until steps) {
            val render = checkNotNull(component.renderSnapshot().renderModel)
            if (render.phase == GamePhase.CHOICE && render.choices.isNotEmpty()) {
                choiceWait++
                val kindBit = 1 shl render.choiceType.ordinal
                if (choiceWait == 2 && rerolledKinds and kindBit == 0) {
                    rerolledKinds = rerolledKinds or kindBit
                    summary.rerolls++
                    accept(component, GameplayInteractionPulse.ChoicesRerolled)
                } else if (choiceWait >= 4 && choiceWait % 4 == 0) {
                    val pick = if (render.choiceType == ChoiceType.OMEN) {
                        render.choices.indexOfFirst { it.omenId == null }.coerceAtLeast(0)
                    } else {
                        0
                    }
                    summary.picks++
                    if (render.choiceType == ChoiceType.ITEM) summary.itemPicks++
                    accept(component, GameplayInteractionPulse.ChoiceSelected.fromValidated(pick))
                }
            } else {
                choiceWait = 0
            }
            when (step) {
                2 -> accept(component, GameplayInteractionPulse.UserGestureObserved)
                37 -> accept(component, GameplayInteractionPulse.PointerMoved.fromValidated(-4f, 300f))
                38 -> accept(component, GameplayInteractionPulse.PointerMoved.fromValidated(500f, render.screenHeight + 6f))
                60 -> brake(component, BrakeSource.KEYBOARD, true)
                75 -> brake(component, BrakeSource.KEYBOARD, false)
                90 -> brake(component, BrakeSource.SECONDARY_POINTER, true)
                96 -> brake(component, BrakeSource.SECONDARY_POINTER, false)
                110 -> brake(component, BrakeSource.TOUCH_CONTROL, true)
                118 -> brake(component, BrakeSource.TOUCH_CONTROL, false)
                130 -> brake(component, BrakeSource.KEYBOARD, true)
                132 -> brake(component, BrakeSource.TOUCH_CONTROL, true)
                140 -> brake(component, BrakeSource.KEYBOARD, false)
                146 -> brake(component, BrakeSource.TOUCH_CONTROL, false)
                150, 400 -> accept(component, GameplayInteractionPulse.SkillRequested.fromValidated(0))
                151, 401 -> accept(component, GameplayInteractionPulse.SkillRequested.fromValidated(1))
                152, 402 -> accept(component, GameplayInteractionPulse.SkillRequested.fromValidated(2))
                160, 405 -> accept(component, GameplayInteractionPulse.ApexRequested)
                200, 215, 480, 481 -> accept(component, GameplayInteractionPulse.PauseToggled)
                300, 302 -> accept(component, GameplayInteractionPulse.ViewportChanged.fromValidated(1_280f, 720f, 1.5f))
                330 -> accept(component, viewport)
            }
            if (step % 45 == 20) accept(component, GameplayInteractionPulse.DashRequested)
            val steering = checkNotNull(component.renderSnapshot().renderModel)
            if (step % 2 == 1 && steering.phase == GamePhase.RUNNING) {
                val target = steeringTarget(steering)
                if (target != null) {
                    val desired = atan2(target.second - steering.coreY, target.first - steering.coreX)
                    val turn = atan2(sin(desired - cursorAngle), cos(desired - cursorAngle))
                    cursorAngle += turn.coerceIn(-0.25f, 0.25f)
                }
                val reach = minOf(steering.screenWidth, steering.screenHeight) * 0.4f
                val x = (steering.coreX - steering.cameraX + steering.screenWidth * 0.5f + cos(cursorAngle) * reach)
                    .coerceIn(0f, steering.screenWidth)
                val y = (steering.coreY - steering.cameraY + steering.screenHeight * 0.5f + sin(cursorAngle) * reach)
                    .coerceIn(0f, steering.screenHeight)
                accept(component, GameplayInteractionPulse.PointerMoved.fromValidated(x, y))
            }
            accept(component, GameplayInteractionPulse.FrameElapsed.fromValidated(frameJitter[step % frameJitter.size]))
        }
        return summary
    }

    private fun brake(component: GameComponent, source: BrakeSource, active: Boolean) =
        accept(component, GameplayInteractionPulse.BrakeChanged(source, active))

    private fun line(component: GameComponent, pulse: String, result: String) {
        val status = component.query(GameplayQuery.GetRunStatus)
        val render = component.renderSnapshot().renderModel
        println(
            "i=${index++} $pulse $result rev=${status.revision.value} phase=${status.phase} " +
                "pending=${if (status.progressPending) 1 else 0} fx=${component.visualFxSnapshot().traceDigest()} " +
                "audio=${audio.drain()} profile=${profile.drain()} render=${render?.let(::renderDigest) ?: "-"}",
        )
    }

    private fun <Result : Any> GameplayCommandTestCaller<Result>.outcome(): String = when {
        applied.isNotEmpty() -> "A"
        refused.isNotEmpty() -> {
            val refusal = refused.single()
            if (refusal == GameplayRefusal.DecisionRejected(GameplayRejection.ProgressPending)) progressPendingRejections++
            "R:${refusal.traceReason()}"
        }
        else -> "-"
    }
}

/** Runs [block]; a thrown fault becomes `F:<type>`, and a normal return gives null. */
private inline fun outcomeOf(block: () -> Unit): String? = try {
    block()
    null
} catch (fault: Throwable) {
    "F:${fault::class.simpleName}"
}

private fun GameplayRefusal.traceReason(): String = when (this) {
    is GameplayRefusal.DecisionRejected -> reason.toString()
    else -> toString()
}

/** Aim through the nearest pickup, else away from the nearest enemy, as a hand on a mouse would. */
private fun steeringTarget(render: GameplayRenderModel): Pair<Float, Float>? {
    val pickup = render.pickups.minByOrNull { (it.x - render.coreX).let { dx -> dx * dx } + (it.y - render.coreY).let { dy -> dy * dy } }
    if (pickup != null) {
        val towardX = pickup.x - render.coreX
        val towardY = pickup.y - render.coreY
        val length = sqrt(towardX * towardX + towardY * towardY).coerceAtLeast(1f)
        return pickup.x + towardX / length * 300f to pickup.y + towardY / length * 300f
    }
    val enemy = render.enemies.minByOrNull { (it.x - render.coreX).let { dx -> dx * dx } + (it.y - render.coreY).let { dy -> dy * dy } }
        ?: return null
    val awayX = render.coreX - enemy.x
    val awayY = render.coreY - enemy.y
    val length = sqrt(awayX * awayX + awayY * awayY).coerceAtLeast(1f)
    return render.coreX + awayX / length * 600f to render.coreY + awayY / length * 600f
}

private fun GameplayInteractionPulse.traceName(): String = when (this) {
    is GameplayInteractionPulse.FrameElapsed -> "frame=$realDeltaSeconds"
    is GameplayInteractionPulse.ViewportChanged -> "viewport=${width}x$height@$density"
    is GameplayInteractionPulse.PointerMoved -> "pointer=$x,$y"
    is GameplayInteractionPulse.BrakeChanged -> "brake=$source:${if (active) "on" else "off"}"
    GameplayInteractionPulse.DashRequested -> "dash"
    GameplayInteractionPulse.PauseToggled -> "pause"
    is GameplayInteractionPulse.ChoiceSelected -> "choice=$index"
    GameplayInteractionPulse.ChoicesRerolled -> "reroll"
    GameplayInteractionPulse.UserGestureObserved -> "gesture"
    is GameplayInteractionPulse.SkillRequested -> "skill=$slot"
    GameplayInteractionPulse.ApexRequested -> "apex"
}

/** Records the audio work the owner executes, in today's `advance(delta, cues)` shape: `play[cues] tick=<delta>`. */
private class FrameTraceAudio : GameplayAudioExecutor {
    private val calls = mutableListOf<String>()
    private var unlocks = 0

    override fun advance(realDeltaSeconds: Float, cues: ImmutableList<GameplayAudioCue>) {
        calls += "play[${cues.joinToString(",")}] tick=$realDeltaSeconds"
    }

    override fun ensureUnlocked() {
        unlocks++
    }

    fun drain(): String {
        val played = if (calls.isEmpty()) "play[] tick=-" else calls.joinToString(";")
        val text = "$played unlock=$unlocks"
        calls.clear()
        unlocks = 0
        return text
    }
}

/** A Profile owner that applies every progress update, or holds every reply unanswered. */
private class FrameTraceProfile(private val holdReplies: Boolean) : ProfileReadPort, ProfileProgress {
    override val instanceId = LOCAL_PROFILE_INSTANCE_ID
    private val snapshot = PlayerProfile(
        loadout = PlayerLoadout(
            selectedWeapon = WeaponId.PRISM_RELAY,
            unlockedWeapons = setOf(WeaponId.FLUX_WAKE, WeaponId.PRISM_RELAY),
        ),
        matrix = MatrixProgress(MatrixNodeId.entries.map { 1 }),
    ).let { GameplayProfileSnapshot(it.preferences, it.economy, it.loadout, it.matrix, it.collection, it.rebirthProgress) }
    private var revision = ProfileRevision(10)
    private val events = mutableListOf<String>()
    val held = mutableListOf<InlineReply<ProfileProgressApplied, ProfileRefusal>>()
    var applied = 0
        private set

    override fun applyGameplayProgress(update: GameplayProgressUpdate, reply: InlineReply<ProfileProgressApplied, ProfileRefusal>) {
        if (holdReplies) {
            held += reply
            events += "${update.traceSummary()}:held"
            return
        }
        revision = ProfileRevision(revision.value + 1)
        applied++
        events += "${update.traceSummary()}:applied"
        reply.accepted(ProfileProgressApplied(revision))
    }

    fun drain(): String {
        val text = if (events.isEmpty()) "-" else events.joinToString(";")
        events.clear()
        return text
    }

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

private fun GameplayProgressUpdate.traceSummary(): String =
    "m$bankedMatter" + "i${discoveredItemIds.size}r${discoveredRelicIds.size}e${eliteKills}d${dashHits}o$completedOrbits" +
        "k${totalKills}p${dataPickups}a${acquiredItemIds.size}c${clearedRebirthLevel ?: "-"}"

/** A run that reaches its first item choice within a few seconds: weak, slow enemies and generous Data. */
private val FrameTraceGameplayContent by lazy {
    val rebirth = SyntheticGameplayContent.rebirth
    SyntheticGameplayContent.copy(
        tempo = SyntheticGameplayContent.tempo.copy(dataPickupMultiplier = 20f),
        rebirth = rebirth.copy(
            profiles = rebirth.profiles.map { profile ->
                profile.copy(
                    openingEnemyCount = 1,
                    enemyCapMultiplier = 0.1f,
                    spawnRateMultiplier = 1f,
                    enemyHealthMultiplier = 0.01f,
                    enemySpeedMultiplier = 0.25f,
                    incomingDamageMultiplier = 0f,
                    playerPowerMultiplier = 20f,
                    maximumActiveEnemies = 8,
                    minimumSpawnIntervalSeconds = 0.2f,
                )
            }.toImmutableList(),
            minSpawnIntervalSeconds = 0.2f,
        ),
    )
}

/** FNV-1a 64 over the raw bits of a fixed list of render facts. */
private fun renderDigest(render: GameplayRenderModel): String {
    var hash = FNV_OFFSET
    hash = fnvInt(hash, render.coreX.toRawBits())
    hash = fnvInt(hash, render.coreY.toRawBits())
    hash = fnvInt(hash, render.velocityX.toRawBits())
    hash = fnvInt(hash, render.velocityY.toRawBits())
    hash = fnvInt(hash, render.hp.toRawBits())
    hash = fnvInt(hash, render.heat.toRawBits())
    hash = fnvInt(hash, render.level)
    hash = fnvInt(hash, render.data)
    hash = fnvInt(hash, render.keys)
    hash = fnvInt(hash, render.elapsed.toRawBits())
    hash = fnvInt(hash, render.phase.ordinal)
    hash = fnvInt(hash, render.enemies.size)
    hash = fnvInt(hash, render.projectiles.size)
    hash = fnvInt(hash, render.pickups.size)
    hash = fnvInt(hash, render.sites.size)
    render.enemies.forEach { enemy ->
        hash = fnvInt(hash, enemy.id)
        hash = fnvInt(hash, enemy.x.toRawBits())
        hash = fnvInt(hash, enemy.y.toRawBits())
    }
    return hash.toHex()
}

/** Counts of particles/shockwaves/numbers/arcs/notices and an FNV-1a 64 over every value of the FX projection. */
private fun VisualFxProjection.traceDigest(): String {
    var hash = FNV_OFFSET
    for (list in listOf(particles, motionEchoes, shockwaves, damageNumbers, weaponArcs, buildNotifications,
        elementBursts, reactionStamps, skillBursts, apexBursts)) {
        hash = fnvInt(hash, list.size)
        list.forEach { value -> hash = fnvString(hash, value.toString()) }
    }
    hash = fnvInt(hash, runReactionMask)
    hash = fnvInt(hash, runDiscoveryMask)
    hash = fnvInt(hash, coreRibbon.size)
    for (sample in 0 until coreRibbon.size) {
        hash = fnvInt(hash, coreRibbon.x(sample).toRawBits())
        hash = fnvInt(hash, coreRibbon.y(sample).toRawBits())
        hash = fnvInt(hash, coreRibbon.age(sample).toRawBits())
    }
    return "${particles.size}/${shockwaves.size}/${damageNumbers.size}/${weaponArcs.size}/${buildNotifications.size}:${hash.toHex()}"
}

private const val FNV_OFFSET = -3750763034362895579L
private const val FNV_PRIME = 1099511628211L

private fun fnvInt(hash: Long, value: Int): Long {
    var result = hash
    for (shift in 0 until 32 step 8) result = (result xor ((value ushr shift) and 0xFF).toLong()) * FNV_PRIME
    return result
}

private fun fnvString(hash: Long, value: String): Long {
    var result = fnvInt(hash, value.length)
    for (char in value) result = fnvInt(result, char.code)
    return result
}

private fun Long.toHex(): String = toULong().toString(16).padStart(16, '0')
