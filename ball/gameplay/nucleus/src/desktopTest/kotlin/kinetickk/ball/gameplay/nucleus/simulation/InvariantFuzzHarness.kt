// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.api.BrakeSource
import kinetickk.ball.gameplay.api.GameplayExitProgressResult
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.GameplayRunPhase
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.api.RunMode
import kinetickk.ball.gameplay.nucleus.GameplayAcceptedFrame
import kinetickk.ball.gameplay.nucleus.GameplayContext
import kinetickk.ball.gameplay.nucleus.GameplayDecision
import kinetickk.ball.gameplay.nucleus.GameplayNucleus
import kinetickk.ball.gameplay.nucleus.GameplayNucleusPulse
import kinetickk.ball.gameplay.nucleus.GameplayOutput
import kinetickk.ball.gameplay.nucleus.GameplayStartContext
import kinetickk.ball.gameplay.nucleus.GameplayStartInputs
import kinetickk.ball.gameplay.nucleus.GameplayState
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.model.segmentCircleIntersects
import kinetickk.ball.gameplay.nucleus.performance.canonicalStateFingerprint
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderSnapshot
import kinetickk.ball.gameplay.nucleus.render.WallKind
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.GameplayProgressUpdate
import kinetickk.ball.profile.api.MAX_RECENT_ITEM_IDS
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.ProfileProgressApplied
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.SIMULATION_SPEED_OPTIONS
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.random.CloneableXorWowRandom
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * The invariant fuzz harness. Each seed plays one session through the real nucleus boundary (GameplayNucleus.decide:
 * StartRun, interaction pulses, ExitRun, ProgressApplied) with randomized but deterministic decisions: a test-local
 * Random seeded per run picks the run's mode, tier, character, weapon, content arm, viewport and simulation speed, and
 * the session alternates the existing pilots (the NoPoi bot, SitePilot, DungeonPilot, DimensionPilot, plus a seeker that
 * flies into whatever opt-in site is nearest) by segments, with random pointer, Brake, Dash, skill and Apex jitter on
 * top and random picks on choice screens (omens, Relics, boons, signals and rerolls included). A pilot never touches the
 * committed state: it decides on an isolated copy and its decision is read back as the pulses a player's input would
 * send (pointer, Brake, Dash, skill and Apex requests, the card it took or a reroll). Some sessions exit mid-run and start
 * again, or start the next tier after a victory (or mid-run), through the same ExitRun/StartRun path the AppSession uses.
 *
 * After every input frame and every choice action the harness checks the run's invariants ([FuzzInvariant]); every
 * [FuzzOptions.renderEvery] frames it maps the render model (fresh and through the reuse path) and every
 * [FuzzOptions.copyCheckEvery] frames it compares the committed state with its reduction copy before the frame and with
 * its own snapshot after it. The first break of each invariant in a session is recorded with its seed, run, step and
 * second; a break that leaves the session unsafe to continue (an exception, a non-finite value, a choice that cannot be
 * resolved) ends it.
 */

/** What a break violated. */
internal enum class FuzzInvariant {
    /** A nucleus decision or a render mapping threw. */
    EXCEPTION,

    /** The nucleus refused a pulse a player could send. */
    NUCLEUS_REJECTED,

    /** A position, velocity, timer or stat is NaN, or infinite where it has no "never" meaning. */
    NON_FINITE,

    /** A collection outgrew its declared bound. */
    COLLECTION_BOUND,

    /** A stat left its range (integrity above its maximum, negative shield, heat outside 0..100, time running back). */
    STAT_RANGE,

    /** An encounter outlived the site, room or visit that owns it, or a tagged enemy outlived its encounter. */
    ENGAGEMENT_OWNER,

    /** A sealed room kept an enemy it owns (or the Core) outside its walls, or stayed sealed with nothing left to fight. */
    SEALED_ROOM,

    /** The Core stayed inside a steady solid body (a rock, a bumper, a well's core, a crystal, a wall or a sealed door). */
    CORE_EMBEDDED,

    /** A live enemy stayed far beyond every leash and abandon distance, never relocated nor removed. */
    ENEMY_STRANDED,

    /** A dimension visit outlived its timer, or the run sat in a dimension without a visit. */
    DIMENSION_TIMER,

    /** A choice screen with no card, a card that does nothing although it is not locked, or no card that resolves. */
    CHOICE_UNRESOLVABLE,

    /** A queued choice never opened while the run ran, or a choice screen never closed. */
    CHOICE_QUEUE,

    /** A Baby mode run published Profile progress or broke a Baby mode rule. */
    EASY_MODE,

    /** A STANDARD run published progress the Profile cannot hold (a clear without a victory, unknown ids, negatives). */
    PROGRESS,

    /** A committed state differs from its own reduction copy, or a reduction wrote into its committed predecessor. */
    COPY_ISOLATION,

    /** The same seed played twice ended differently. */
    DETERMINISM,
}

/** One invariant break: where it happened and what was seen. */
internal data class FuzzBreak(
    val seed: Int,
    /** The session's run (0: the first run; restarts and rebirths count up). */
    val run: Int,
    /** Input frames and choice actions played in the session before the break. */
    val step: Long,
    val elapsed: Float,
    val invariant: FuzzInvariant,
    val detail: String,
    val context: String,
) {
    fun line(): String = "BREAK seed=$seed run=$run step=$step sec=${"%.2f".format(elapsed)} $invariant: $detail [$context]"
}

/** The content a session plays: shipped, every eligible window hosting a rift, or one site arm's window weights. */
internal enum class FuzzContent { SHIPPED, RIFTS, ARM }

/**
 * How a session's frames arrive: the pilots' 50 Hz input, a 60 Hz or 144 Hz display, or an uneven one (frames of
 * 5-40 ms with a rare hitch of up to 0.6 s, which the nucleus clamps).
 */
internal enum class FuzzPacing { HZ_50, HZ_60, HZ_144, UNEVEN }

/** How one seed's session is set up; every field derives from the seed alone. */
internal data class FuzzPlan(
    val seed: Int,
    val mode: RunMode,
    val tier: Int,
    val matrix: GateMatrix,
    val shape: CoreShape,
    val weapon: WeaponId,
    /** Relics the profile offers as Heirlooms (only from tier 1, as the game offers them). */
    val heirlooms: List<RelicId>,
    val contentKind: FuzzContent,
    val arm: SiteArm,
    val width: Float,
    val height: Float,
    val density: Float,
    val simulationSpeed: Float,
    val pacing: FuzzPacing,
    /** Input jitter strength 0..3. */
    val jitter: Int,
    /** The share of choice screens decided at random instead of by the pilot. */
    val randomChoiceShare: Float,
    /** The run second the session exits and starts the run again (NaN: never). */
    val restartAt: Float,
    /** At [restartAt] the session starts the next tier instead of the same one. */
    val rebirthMidway: Boolean,
    /** After a STANDARD victory the session starts the next tier and plays [followUpSeconds] of it. */
    val rebirthAfterVictory: Boolean,
    /** Simulated seconds the session plays in all (its first run ends at the latest here). */
    val capSeconds: Float,
    val followUpSeconds: Float,
) {
    fun label(): String = "mode=$mode tier=$tier matrix=$matrix shape=$shape weapon=$weapon heirlooms=${heirlooms.size} " +
        "content=$contentKind${if (contentKind == FuzzContent.ARM) "/$arm" else ""} viewport=${width.toInt()}x${height.toInt()}@$density " +
        "speed=$simulationSpeed pacing=$pacing jitter=$jitter randomChoices=${"%.2f".format(randomChoiceShare)} " +
        "restartAt=${if (restartAt.isNaN()) "-" else "%.0f".format(restartAt)}${if (rebirthMidway) "(rebirth)" else ""} " +
        "rebirthAfterVictory=$rebirthAfterVictory cap=${capSeconds.toInt()}"

    /** The content this plan plays (built once per content kind and arm). */
    fun content(): GameplayContentSnapshot = when (contentKind) {
        FuzzContent.SHIPPED -> canonicalGameplayContent
        FuzzContent.RIFTS -> RiftContent
        FuzzContent.ARM -> ArmContent.getOrPut(arm) { arm.content() }
    }

    companion object {
        private val RiftContent: GameplayContentSnapshot by lazy {
            canonicalGameplayContent.withDimensions { copy(portalWindowChance = 1f) }
        }
        private val ArmContent = ConcurrentHashMap<SiteArm, GameplayContentSnapshot>()

        /** The arms a window-weight arm may take (MIXED and SIGIL play the shipped weights anyway). */
        private val WeightArms = SiteArm.entries.filter { it != SiteArm.MIXED && it != SiteArm.SIGIL }

        private val Viewports = listOf(
            Triple(1_280f, 800f, 1f), Triple(844f, 390f, 2.75f), Triple(1_920f, 1_080f, 1.5f), Triple(800f, 1_280f, 2f),
        )

        /**
         * The plan of seed index [index] (seed `index * 7_919 + 13`). Tier and mode are stratified by the index, so any
         * twelve consecutive indices hold every tier in both modes; everything else comes from a Random of the seed.
         * [smoke] caps the session short for the always-on run.
         */
        fun of(index: Int, smoke: Boolean): FuzzPlan {
            val seed = index * 7_919 + 13
            val random = Random(seed.toLong() * 0x2545_F491_4F6C_DD1DL xor 0x0F02_2F02_2F02L)
            val tier = intArrayOf(0, 3, 10)[Math.floorMod(index, 3)]
            val mode = if (Math.floorMod(index / 3, 4) == 0) RunMode.EASY else RunMode.STANDARD
            val matrix = when (tier) {
                0 -> if (random.nextFloat() < 0.7f) GateMatrix.NONE else GateMatrix.HALF
                3 -> if (random.nextBoolean()) GateMatrix.HALF else GateMatrix.FULL
                else -> GateMatrix.FULL
            }
            // Half the tier-10 sessions fly the strong gate build (the Pentagon with Arc Coil), which lives long enough to
            // reach the late run; every other session takes any character and weapon.
            val strong = tier == 10 && random.nextBoolean()
            val shape = when {
                strong -> CoreShape.PENTAGON
                tier == 0 && random.nextBoolean() -> CoreShape.ORB
                else -> CoreShape.entries[random.nextInt(CoreShape.entries.size)]
            }
            val weapon = when {
                strong -> WeaponId.ARC_COIL
                random.nextFloat() < 0.35f -> WeaponId.FLUX_WAKE
                else -> WeaponId.entries[random.nextInt(WeaponId.entries.size)]
            }
            val heirlooms = if (tier >= 1 && random.nextFloat() < 0.5f) {
                RelicId.entries.shuffled(random).take(1 + random.nextInt(3))
            } else {
                emptyList()
            }
            val contentRoll = random.nextFloat()
            val contentKind = when {
                contentRoll < 0.55f -> FuzzContent.SHIPPED
                contentRoll < 0.75f -> FuzzContent.RIFTS
                else -> FuzzContent.ARM
            }
            val arm = WeightArms[random.nextInt(WeightArms.size)]
            val viewport = Viewports[if (random.nextFloat() < 0.5f) 0 else 1 + random.nextInt(Viewports.size - 1)]
            val speed = if (random.nextFloat() < 0.7f) 1f else SIMULATION_SPEED_OPTIONS[random.nextInt(SIMULATION_SPEED_OPTIONS.size)]
            val pacingRoll = random.nextFloat()
            val pacing = when {
                pacingRoll < 0.55f -> FuzzPacing.HZ_50
                pacingRoll < 0.75f -> FuzzPacing.HZ_60
                pacingRoll < 0.85f -> FuzzPacing.HZ_144
                else -> FuzzPacing.UNEVEN
            }
            val restart = random.nextFloat() < 0.15f
            val restartAt = if (restart) 15f + random.nextFloat() * (if (smoke) 150f else 500f) else Float.NaN
            return FuzzPlan(
                seed = seed,
                mode = mode,
                tier = tier,
                matrix = matrix,
                shape = shape,
                weapon = weapon,
                heirlooms = heirlooms,
                contentKind = contentKind,
                arm = arm,
                width = viewport.first,
                height = viewport.second,
                density = viewport.third,
                simulationSpeed = speed,
                pacing = pacing,
                jitter = random.nextInt(4),
                randomChoiceShare = 0.1f + random.nextFloat() * 0.6f,
                restartAt = restartAt,
                rebirthMidway = restart && tier < 10 && random.nextFloat() < 0.4f,
                rebirthAfterVictory = mode == RunMode.STANDARD && random.nextFloat() < 0.3f,
                capSeconds = if (smoke) 300f else 900f,
                followUpSeconds = if (smoke) 30f else 120f,
            )
        }
    }
}

/** What one session played and every break it found. */
internal data class FuzzResult(
    val plan: FuzzPlan,
    val breaks: List<FuzzBreak>,
    val steps: Long,
    val seconds: Float,
    val runs: Int,
    val endPhase: GamePhase,
    val endMessage: String,
    val visits: Int,
    /** Visits per world layer ordinal, and the layers whose objective a run of the session completed (bits). */
    val visitedLayers: List<Int>,
    val completedLayers: Int,
    val seals: Int,
    val siteTrials: Int,
    val signalPrompts: Int,
    val choiceScreens: Int,
    val renders: Int,
    /** The last committed model's canonical fingerprint (the determinism witness). */
    val fingerprint: Long,
    /** The pilot flying when the session ended. */
    val endPilot: String,
) {
    fun summary(): String = "seed=${plan.seed} steps=$steps sec=${"%.1f".format(seconds)} runs=$runs end=$endPhase($endMessage) " +
        "visits=$visits${visitedLayers.drop(1)} completed=${completedLayers.toString(2)} seals=$seals trials=$siteTrials signals=$signalPrompts choices=$choiceScreens renders=$renders " +
        "breaks=${breaks.size} pilot=$endPilot fingerprint=${fingerprint.toString(16)}"
}

/** Options of the harness itself (not of the game): how often the heavier checks run. */
internal data class FuzzOptions(
    /** Frames between deep non-finite scans of every retained value (1: every frame). */
    val scanEvery: Int = 1,
    /** Frames between render mappings (a fresh mapping and the reuse path from its predecessor). */
    val renderEvery: Int = 25,
    /** Frames between copy and isolation checks. */
    val copyCheckEvery: Int = 50,
    /** Prints every frame from this step on (minimisation). */
    val traceFrom: Long = Long.MAX_VALUE,
    val traceTo: Long = Long.MAX_VALUE,
    /** Minimisation: the plan's jitter and random-choice share replaced, its restart dropped, one pilot kind only. */
    val jitter: Int? = null,
    val randomChoiceShare: Float? = null,
    val noRestart: Boolean = false,
    val onlyPilot: String? = null,
)

/** [plan] with the minimisation overrides of [options] applied. */
internal fun FuzzPlan.minimised(options: FuzzOptions): FuzzPlan = copy(
    jitter = options.jitter ?: jitter,
    randomChoiceShare = options.randomChoiceShare ?: randomChoiceShare,
    restartAt = if (options.noRestart) Float.NaN else restartAt,
    rebirthAfterVictory = rebirthAfterVictory && !options.noRestart,
)

/** Plays one seed's session and returns its result. */
internal fun fuzzSession(plan: FuzzPlan, options: FuzzOptions = FuzzOptions()): FuzzResult =
    FuzzSession(plan.minimised(options), options).play()

/** One fuzzed session (see the file comment). */
private class FuzzSession(private val plan: FuzzPlan, private val options: FuzzOptions) {
    private val content = plan.content()
    private val random = Random(plan.seed.toLong() * 0x9E37_79B9L + 0x1F2E_3D4CL)
    private val breaks = ArrayList<FuzzBreak>()
    private val recorded = HashSet<FuzzInvariant>()
    private var fatal = false

    private var run = 0
    private var runSeed = plan.seed
    private var mode = plan.mode
    private var tier = plan.tier
    private var heirlooms = plan.heirlooms
    private var state: GameplayState = GameplayState.initial(RunId(1L), content)
    private var step = 0L
    private var sessionSeconds = 0f
    /** The run is the short follow-up of a rebirth after a victory. */
    private var followUp = false
    /** The current run ended in victory (its exit may still publish the clear). */
    private var runWon = false
    private var runStartedAtSession = 0f
    private var profileRevision = 0L

    private val bot = NoPoiBot(avoidWeaponHarmony = random.nextBoolean())
    private var pilot: FuzzPilot = BotFuzzPilot(bot)
    private var segmentEndsAt = 0f
    private var lastLayer = WorldLayer.BASE
    private var lastRift: WorldLayer? = null
    private var lastSignals = 0
    private var lastStructureSignals = 0

    // Jitter state.
    private var heldBrake = false
    private var heldBrakeFrames = 0
    private var liftedFrames = 0

    // Invariant trackers (run seconds since a condition first held; NaN while it does not).
    private var lastElapsed = 0f
    private var queuedSince = Float.NaN
    private var sealOutsideSince = Float.NaN
    private var sealEmptySince = Float.NaN
    private var coreOutsideSince = Float.NaN
    private var embeddedSince = Float.NaN
    private var embeddedDetail = ""
    private var strandedSince = Float.NaN
    private var strandedDetail = ""
    private var orphanSince = Float.NaN
    private var orphanDetail = ""
    private var choiceActions = 0
    private var choiceScreenKey: Any? = null

    // Coverage.
    private var visits = 0
    private var seals = 0
    private var siteTrials = 0
    private var signalPrompts = 0
    private var choiceScreens = 0
    private var renders = 0
    private var wasSealed = false
    private var wasVisiting = false
    private val visitedLayers = IntArray(WorldLayer.entries.size)
    private var completedLayers = 0
    private var wasTrial = false

    private val model: MutableGameState get() = state.engine!!.model

    fun play(): FuzzResult {
        try {
            startRun()
            while (!fatal) {
                val m = model
                if (m.phase == GamePhase.GAME_OVER || m.phase == GamePhase.VICTORY) {
                    if (!finishRun()) break
                    continue
                }
                if (sessionSeconds >= plan.capSeconds) break
                if (followUp && m.elapsed >= plan.followUpSeconds) break
                if (!plan.restartAt.isNaN() && run == 0 && m.elapsed >= plan.restartAt && m.phase == GamePhase.RUNNING) {
                    restart(nextTier = plan.rebirthMidway)
                    continue
                }
                if (m.phase == GamePhase.CHOICE) chooseStep() else frameStep()
                step++
            }
            if (!fatal && state.phase != GameplayRunPhase.EXITED) exitRun()
        } catch (failure: Throwable) {
            record(FuzzInvariant.EXCEPTION, describe(failure), fatalBreak = true)
        }
        val last = state.engine?.model
        return FuzzResult(
            plan, breaks, step, sessionSeconds, run + 1, last?.phase ?: GamePhase.RUNNING, last?.message.orEmpty(),
            visits, visitedLayers.toList(), completedLayers, seals, siteTrials, signalPrompts, choiceScreens, renders,
            last?.let(::canonicalStateFingerprint) ?: 0L, pilot.label,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // The session: start, exit, restart, rebirth.

    private fun profile(): GameplayProfileSnapshot {
        val base = gateProfile(plan.matrix, tier = tier, shape = plan.shape, weapon = plan.weapon, content = content)
        return base.copy(
            preferences = base.preferences.copy(simulationSpeed = plan.simulationSpeed),
            collection = PlayerCollection(heirloomRelicIds = (if (tier >= 1) heirlooms else emptyList()).toImmutableList()),
            rebirthProgress = RebirthProgress(level = tier, highestCleared = tier - 1),
        )
    }

    private fun startRun() {
        state = GameplayState.initial(RunId(run + 1L), content)
        val inputs = GameplayStartInputs(content, profile(), runSeed, mode)
        val decision = GameplayNucleus.decide(state, GameplayNucleusPulse.StartRun, GameplayContext(GameplayStartContext.Ready(inputs)))
        state = accepted(decision, "StartRun") ?: error("the run did not start: $decision")
        runWon = false
        lastElapsed = 0f
        queuedSince = Float.NaN
        sealOutsideSince = Float.NaN
        sealEmptySince = Float.NaN
        coreOutsideSince = Float.NaN
        embeddedSince = Float.NaN
        strandedSince = Float.NaN
        orphanSince = Float.NaN
        runStartedAtSession = sessionSeconds
        segmentEndsAt = 0f
        lastLayer = WorldLayer.BASE
        lastRift = null
        lastSignals = 0
        lastStructureSignals = 0
        pilot = BotFuzzPilot(bot)
        send(GameplayInteractionPulse.ViewportChanged.fromValidated(plan.width, plan.height, plan.density))
    }

    /** Exits the run through the nucleus and settles its Profile progress. */
    private fun exitRun() {
        if (state.progressPending) applyProgress()
        val decision = GameplayNucleus.decide(state, GameplayNucleusPulse.ExitRun)
        val frame = (decision as? GameplayDecision.Accepted)?.frame
        if (frame == null) {
            record(FuzzInvariant.NUCLEUS_REJECTED, "ExitRun: $decision")
            return
        }
        state = frame.nextState
        handleOutputs(frame, "ExitRun")
        if (state.progressPending) applyProgress()
    }

    private fun restart(nextTier: Boolean) {
        exitRun()
        run++
        runSeed = plan.seed xor (0x5EED_0000 + run)
        if (nextTier) tier = min(10, tier + 1)
        startRun()
    }

    /** The run ended: plays a few frames past the end, then exits, and maybe starts the next tier. False ends the session. */
    private fun finishRun(): Boolean {
        repeat(5) {
            send(GameplayInteractionPulse.FrameElapsed.fromValidated(0.02f))
            checkInvariants()
            step++
        }
        val won = model.phase == GamePhase.VICTORY
        runWon = won
        exitRun()
        if (won && plan.rebirthAfterVictory && run == 0 && mode == RunMode.STANDARD) {
            heirlooms = model.equippedRelics.map { it.id }
            run++
            runSeed = plan.seed xor (0x5EED_0000 + run)
            tier = min(10, tier + 1)
            followUp = true
            startRun()
            return true
        }
        return false
    }

    private fun applyProgress() {
        profileRevision++
        val decision = GameplayNucleus.decide(state, GameplayNucleusPulse.ProgressApplied(ProfileProgressApplied(ProfileRevision(profileRevision))))
        state = accepted(decision, "ProgressApplied") ?: return
    }

    // ---------------------------------------------------------------------------------------------
    // A running frame.

    private fun frameStep() {
        val committed = model
        maybeSwitchPilot(committed)
        val scratch = committed.copyForReduction()
        scratch.dashBufferTime = 0f
        scratch.skillBufferMask = 0
        scratch.skillBufferTime = 0f
        scratch.apexBufferTime = 0f
        syncBotCursor(bot, scratch)
        pilot.control(scratch)
        var pointerX = scratch.pointerX
        var pointerY = scratch.pointerY
        var brake = scratch.keyboardBrakeActive
        var dash = scratch.dashBufferTime > 0f
        var skills = scratch.skillBufferMask
        var apex = scratch.apexBufferTime > 0f
        var active = true
        // Jitter: a twitching hand, a held or released Brake, a stray Dash, skill or Apex, a lifted finger.
        val level = plan.jitter
        if (level > 0) {
            // The Core on screen: a twitch never lands the singularity on it or sweeps it across it.
            val coreScreenX = committed.screenWidth * 0.5f + committed.coreX - committed.cameraX
            val coreScreenY = committed.screenHeight * 0.5f + committed.coreY - committed.cameraY
            if (random.nextFloat() < 0.05f * level) {
                val twitchX = (pointerX + (random.nextFloat() - 0.5f) * 30f * level).coerceIn(0f, committed.screenWidth)
                val twitchY = (pointerY + (random.nextFloat() - 0.5f) * 30f * level).coerceIn(0f, committed.screenHeight)
                if (!segmentCircleIntersects(pointerX, pointerY, twitchX, twitchY, coreScreenX, coreScreenY, TWITCH_CORE_CLEARANCE)) {
                    pointerX = twitchX
                    pointerY = twitchY
                }
            }
            // A thrown mouse: anywhere on screen (a Baby mode run bounces off; elsewhere it is rare, since it may kill).
            if (random.nextFloat() < (if (mode == RunMode.EASY) 0.0005f else 0.00003f) * level) {
                pointerX = random.nextFloat() * committed.screenWidth
                pointerY = random.nextFloat() * committed.screenHeight
            }
            if (heldBrakeFrames == 0 && random.nextFloat() < 0.008f * level) {
                heldBrake = random.nextBoolean()
                heldBrakeFrames = 1 + random.nextInt(40)
            }
            if (heldBrakeFrames > 0) {
                brake = heldBrake
                heldBrakeFrames--
            }
            if (random.nextFloat() < 0.004f * level) dash = true
            if (random.nextFloat() < 0.003f * level) skills = skills or (1 shl random.nextInt(ContentBounds.SKILL_SLOTS))
            if (random.nextFloat() < 0.001f * level) apex = true
            if (liftedFrames == 0 && random.nextFloat() < 0.0005f * level) liftedFrames = 1 + random.nextInt(25)
            if (liftedFrames > 0) {
                active = false
                liftedFrames--
            }
        }
        pointerX = pointerX.coerceIn(0f, committed.screenWidth)
        pointerY = pointerY.coerceIn(0f, committed.screenHeight)
        // Rare session events: a pause and resume, a rotated or resized window.
        if (random.nextFloat() < 0.0003f) {
            send(GameplayInteractionPulse.PauseToggled)
            send(GameplayInteractionPulse.FrameElapsed.fromValidated(0.02f))
            send(GameplayInteractionPulse.PauseToggled)
        }
        if (random.nextFloat() < 0.0002f) {
            val (width, height) = if (random.nextBoolean()) plan.width to plan.height else plan.height to plan.width
            send(GameplayInteractionPulse.ViewportChanged.fromValidated(width, height, plan.density))
            pointerX = pointerX.coerceIn(0f, model.screenWidth)
            pointerY = pointerY.coerceIn(0f, model.screenHeight)
        }

        val copyCheck = step % options.copyCheckEvery == (plan.seed and 0x7FFF).toLong() % options.copyCheckEvery
        val predecessor = model
        val isolated = if (copyCheck) copyCheckBefore(predecessor) else null

        send(GameplayInteractionPulse.PointerMoved.fromValidated(pointerX, pointerY, active))
        send(GameplayInteractionPulse.BrakeChanged(BrakeSource.KEYBOARD, brake))
        if (dash) send(GameplayInteractionPulse.DashRequested)
        for (slot in 0 until ContentBounds.SKILL_SLOTS) if (skills and (1 shl slot) != 0) send(GameplayInteractionPulse.SkillRequested.fromValidated(slot))
        if (apex) send(GameplayInteractionPulse.ApexRequested)
        val render = step % options.renderEvery == 0L
        val beforeFrame = state
        val beforeSnapshot = if (render) renderSnapshot { GameplayNucleus.renderSnapshot(beforeFrame) } else null
        val elapsedBefore = model.elapsed
        send(GameplayInteractionPulse.FrameElapsed.fromValidated(frameDelta()))
        sessionSeconds += model.elapsed - elapsedBefore
        if (beforeSnapshot != null && state.revision.value == beforeFrame.revision.value + 1) {
            val after = state
            renderSnapshot { GameplayNucleus.renderSnapshot(after, beforeFrame, beforeSnapshot) }
            renders++
        }
        if (isolated != null) copyCheckAfter(predecessor, isolated)
        if (fatal) return
        pilot.observe(model)
        if (step in options.traceFrom..options.traceTo) trace()
        checkInvariants()
    }

    /** The real seconds of the next frame under the plan's pacing. */
    private fun frameDelta(): Float = when (plan.pacing) {
        FuzzPacing.HZ_50 -> 0.02f
        FuzzPacing.HZ_60 -> 1f / 60f
        FuzzPacing.HZ_144 -> 1f / 144f
        FuzzPacing.UNEVEN -> if (random.nextFloat() < 0.005f) 0.15f + random.nextFloat() * 0.45f else 0.005f + random.nextFloat() * 0.035f
    }

    private fun trace() {
        val m = model
        println("TRACE seed=${plan.seed} step=$step sec=${"%.3f".format(m.elapsed)} pilot=${pilot.label} phase=${m.phase} core=${m.coreX.toInt()},${m.coreY.toInt()} " +
            "v=${m.velocityX.toInt()},${m.velocityY.toInt()} hp=${m.hp} layer=${m.worldLayer} dungeon=${m.dungeon} enemies=${m.enemies.size} " +
            "encounters=${m.encounters.map { it.tag.toString(16) }} sites=${m.sites.map { "${it.kind}${if (it.active) "*" else ""}" }} msg=${m.message}")
    }

    /**
     * Picks the pilot of the next segment when the segment ends or the world layer changes, and now and then follows
     * an offer the moment it appears (a rift, an event signal, a structure signal), as a curious player would.
     */
    private fun maybeSwitchPilot(m: MutableGameState) {
        options.onlyPilot?.let { only ->
            if (pilot.label.substringBefore('/') != only) pilot = onlyPilot(only, m)
            return
        }
        val layerChanged = m.worldLayer != lastLayer
        lastLayer = m.worldLayer
        val rift = m.sites.firstOrNull { it.kind.riftTarget() != null }?.kind?.riftTarget()
            ?: if (m.dimensionRun.glitchPortal != null) WorldLayer.ARCHIVE else null
        val newRift = rift != null && rift != lastRift
        lastRift = rift
        val signals = m.eventSignals.size
        val newSignal = signals > lastSignals
        lastSignals = signals
        val structures = m.structureField.signals.size
        val newStructure = structures > lastStructureSignals
        lastStructureSignals = structures
        if (m.worldLayer == WorldLayer.BASE) {
            val follow = when {
                newRift && random.nextFloat() < 0.65f -> dimensionPilot(rift!!)
                newSignal && random.nextFloat() < 0.45f -> SeekFuzzPilot(bot)
                newStructure && m.dungeon == null && m.elapsed < content.tempo.bossAtSeconds - 150f && random.nextFloat() < 0.35f ->
                    DungeonFuzzPilot(DungeonPilot(bot, seekFrom = m.elapsed, seekLead = 150f))
                else -> null
            }
            if (follow != null) {
                pilot = follow
                segmentEndsAt = m.elapsed + 40f + random.nextFloat() * 80f
                return
            }
        }
        if (m.elapsed < segmentEndsAt && !(layerChanged && random.nextBoolean())) return
        segmentEndsAt = m.elapsed + 20f + random.nextFloat() * 100f
        val roll = random.nextFloat()
        pilot = if (m.worldLayer != WorldLayer.BASE) {
            when {
                roll < 0.6f -> dimensionPilot(m.worldLayer)
                roll < 0.85f -> BotFuzzPilot(bot)
                else -> SeekFuzzPilot(bot)
            }
        } else {
            val offeredRift = rift
            when {
                roll < 0.2f -> BotFuzzPilot(bot)
                roll < 0.42f -> SiteFuzzPilot(SitePilot(if (plan.contentKind == FuzzContent.ARM) plan.arm else armFor(m), bot))
                roll < 0.62f && m.elapsed < content.tempo.bossAtSeconds - 150f -> DungeonFuzzPilot(DungeonPilot(bot, seekFrom = m.elapsed, seekLead = 150f))
                roll < 0.82f -> dimensionPilot(offeredRift ?: WorldLayer.entries[1 + random.nextInt(WorldLayer.entries.size - 1)])
                else -> SeekFuzzPilot(bot)
            }
        }
    }

    /** The one pilot kind of a minimised session ([FuzzOptions.onlyPilot]: bot, site, dungeon, dimension or seek). */
    private fun onlyPilot(kind: String, m: MutableGameState): FuzzPilot = when (kind) {
        "bot" -> BotFuzzPilot(bot)
        "site" -> SiteFuzzPilot(SitePilot(if (plan.contentKind == FuzzContent.ARM) plan.arm else SiteArm.MIXED, bot))
        "dungeon" -> DungeonFuzzPilot(DungeonPilot(bot, seekFrom = m.elapsed, seekLead = 150f))
        "dimension" -> dimensionPilot(if (m.worldLayer != WorldLayer.BASE) m.worldLayer else WorldLayer.ZENITH)
        "seek" -> SeekFuzzPilot(bot)
        else -> error("unknown pilot kind $kind")
    }

    /** With shipped weights the mixed arm plays whatever a window offers; now and then a sigil hunt instead. */
    private fun armFor(m: MutableGameState): SiteArm =
        if (m.elapsed < m.sigilCutoffAt() && random.nextFloat() < 0.2f) SiteArm.SIGIL else SiteArm.MIXED

    private fun dimensionPilot(target: WorldLayer): FuzzPilot =
        DimensionFuzzPilot(DimensionPilot(target, bot, takeRefuse = random.nextFloat() < 0.3f).also { it.riftDue = true })

    // ---------------------------------------------------------------------------------------------
    // A choice screen.

    private fun chooseStep() {
        val committed = model
        val key = Triple(committed.activeChoiceType, committed.choices, committed.selectedRewardFocus)
        if (key != choiceScreenKey) {
            choiceScreenKey = key
            choiceScreens++
            if (committed.activeChoiceType == ChoiceType.SIGNAL) signalPrompts++
        }
        if (committed.choices.isEmpty()) {
            record(FuzzInvariant.CHOICE_UNRESOLVABLE, "a ${committed.activeChoiceType} choice screen holds no card")
            fatal = true
            return
        }
        // Every card on a copy: a card that is not locked must do something, and some card must.
        val outcomes = committed.choices.indices.map { index -> committed.copyForReduction().also { it.choose(index) } }
        var firstResolving = -1
        for (index in outcomes.indices) {
            val outcome = outcomes[index]
            val resolves = outcome.phase != GamePhase.CHOICE || outcome.choices != committed.choices ||
                outcome.activeChoiceType != committed.activeChoiceType || outcome.selectedRewardFocus != committed.selectedRewardFocus
            if (resolves && firstResolving < 0) firstResolving = index
            val card = committed.choices[index]
            if (!resolves && !card.locked) {
                record(FuzzInvariant.CHOICE_UNRESOLVABLE, "card $index of a ${committed.activeChoiceType} screen does nothing: ${card.cardLabel()}")
            }
        }
        if (firstResolving < 0) {
            record(FuzzInvariant.CHOICE_UNRESOLVABLE, "no card of the ${committed.activeChoiceType} screen resolves: ${committed.choices.map { it.cardLabel() }}")
            fatal = true
            return
        }
        // The pilot's pick read back from its copy, or a random card or reroll.
        val pulse: GameplayInteractionPulse = if (random.nextFloat() < plan.randomChoiceShare) {
            if (committed.choicesCanReroll && random.nextFloat() < 0.15f) {
                GameplayInteractionPulse.ChoicesRerolled
            } else {
                GameplayInteractionPulse.ChoiceSelected.fromValidated(random.nextInt(committed.choices.size))
            }
        } else {
            pilotChoice(committed, outcomes, firstResolving)
        }
        send(pulse)
        if (model.phase == GamePhase.CHOICE) {
            choiceActions++
            if (choiceActions > 60) {
                record(FuzzInvariant.CHOICE_QUEUE, "choice screens kept opening for 60 actions: ${model.activeChoiceType} ${model.choices.map { it.cardLabel() }}")
                fatal = true
            }
        } else {
            choiceActions = 0
        }
        checkInvariants()
    }

    private fun pilotChoice(committed: MutableGameState, outcomes: List<MutableGameState>, firstResolving: Int): GameplayInteractionPulse {
        val scratch = committed.copyForReduction()
        pilot.choose(scratch)
        if (scratch.rerollsRemaining < committed.rerollsRemaining) return GameplayInteractionPulse.ChoicesRerolled
        for (index in outcomes.indices) {
            if (FuzzStateDiff.first(outcomes[index], scratch, "state", FuzzScratchFields) == null) {
                return GameplayInteractionPulse.ChoiceSelected.fromValidated(index)
            }
        }
        // The pilot took no card (it never does on purpose): the first card that resolves.
        return GameplayInteractionPulse.ChoiceSelected.fromValidated(firstResolving)
    }

    // ---------------------------------------------------------------------------------------------
    // The nucleus boundary.

    private fun send(pulse: GameplayInteractionPulse) {
        if (fatal) return
        val decision = try {
            GameplayNucleus.decide(state, GameplayNucleusPulse.Intent(pulse))
        } catch (failure: Throwable) {
            record(FuzzInvariant.EXCEPTION, "decide(${pulse.label()}): ${describe(failure)}", fatalBreak = true)
            return
        }
        val frame = (decision as? GameplayDecision.Accepted)?.frame
        if (frame == null) {
            record(FuzzInvariant.NUCLEUS_REJECTED, "${pulse.label()}: ${(decision as GameplayDecision.Rejected).reason}")
            return
        }
        state = frame.nextState
        handleOutputs(frame, pulse.label())
        if (state.progressPending && state.phase != GameplayRunPhase.EXITED) applyProgress()
    }

    private fun handleOutputs(frame: GameplayAcceptedFrame, what: String) {
        for (output in frame.outputs) {
            when (output) {
                is GameplayOutput.SendProfileCommand -> if (mode == RunMode.EASY) {
                    record(FuzzInvariant.EASY_MODE, "a Baby mode run published Profile progress on $what: ${output.update}")
                } else {
                    checkProgress(output.update, frame.nextState.engine?.model, what)
                }
                is GameplayOutput.RunExited -> if (mode == RunMode.EASY && output.result.progress != GameplayExitProgressResult.NoProgress) {
                    record(FuzzInvariant.EASY_MODE, "a Baby mode run exited with progress ${output.result.progress}")
                }
                else -> Unit
            }
        }
    }

    /**
     * A STANDARD run's progress is what the Profile can hold: no negative Matter or counter, a Rebirth clear (with its
     * victory Relics and the Architect's defeat) only from this tier's victory, only known items, and a recent-item batch
     * within the history bound.
     */
    private fun checkProgress(update: GameplayProgressUpdate, m: MutableGameState?, what: String) {
        val victory = m?.phase == GamePhase.VICTORY || runWon
        val problem = when {
            update.bankedMatter < 0L -> "negative banked Matter ${update.bankedMatter}"
            update.eliteKills < 0 || update.dashHits < 0 || update.completedOrbits < 0 || update.totalKills < 0 ||
                update.ricochets < 0 || update.dataPickups < 0 -> "a negative counter"
            update.victoryRelicIds != null && update.clearedRebirthLevel == null -> "victory Relics without a Rebirth clear"
            update.clearedRebirthLevel != null && (update.clearedRebirthLevel != tier || !victory) ->
                "a Rebirth clear of ${update.clearedRebirthLevel} at tier $tier in phase ${m?.phase}"
            update.architectDefeatedWith != null && !victory -> "the Architect's defeat in phase ${m?.phase}"
            update.discoveredItemIds.any { content.item(it) == null } || update.acquiredItemIds.any { content.item(it) == null } ->
                "an unknown item id"
            update.acquiredItemIds.size > MAX_RECENT_ITEM_IDS -> "${update.acquiredItemIds.size} recent items"
            else -> null
        } ?: return
        record(FuzzInvariant.PROGRESS, "$problem on $what: $update")
    }

    private fun accepted(decision: GameplayDecision, what: String): GameplayState? {
        val frame = (decision as? GameplayDecision.Accepted)?.frame
        if (frame == null) {
            record(FuzzInvariant.NUCLEUS_REJECTED, "$what: $decision")
            return null
        }
        handleOutputs(frame, what)
        return frame.nextState
    }

    private inline fun renderSnapshot(map: () -> GameplayRenderSnapshot): GameplayRenderSnapshot? = try {
        map()
    } catch (failure: Throwable) {
        record(FuzzInvariant.EXCEPTION, "render mapping: ${describe(failure)}", fatalBreak = true)
        null
    }

    // ---------------------------------------------------------------------------------------------
    // Copy and isolation: a committed state equals its own reduction copy, and no reduction writes into it.

    private fun copyCheckBefore(committed: MutableGameState): MutableGameState? {
        val copy = committed.copyForReduction()
        val lost = FuzzStateDiff.first(committed, copy, "state", FuzzScratchFields)
        if (lost != null) {
            record(FuzzInvariant.COPY_ISOLATION, "copyForReduction lost a retained fact: $lost")
            return null
        }
        return copy
    }

    private fun copyCheckAfter(predecessor: MutableGameState, copy: MutableGameState) {
        val written = FuzzStateDiff.first(copy, predecessor, "state", FuzzScratchFields) ?: return
        record(FuzzInvariant.COPY_ISOLATION, "a reduction wrote into its committed predecessor: $written")
    }

    // ---------------------------------------------------------------------------------------------
    // Invariants after every frame and choice action.

    private fun checkInvariants() {
        if (fatal) return
        val m = state.engine?.model ?: return
        checkFinite(m)
        if (fatal) return
        if (step % options.scanEvery == 0L) {
            FuzzFiniteScan.first(m)?.let { record(FuzzInvariant.NON_FINITE, it, fatalBreak = true) }
            if (fatal) return
        }
        checkBounds(m)
        checkStats(m)
        checkEngagementOwners(m)
        checkSealedRoom(m)
        checkEmbedded(m)
        checkStranded(m)
        checkDimension(m)
        checkQueue(m)
        checkEasy(m)
        coverage(m)
    }

    private fun checkFinite(m: MutableGameState) {
        val values = floatArrayOf(
            m.coreX, m.coreY, m.velocityX, m.velocityY, m.cameraX, m.cameraY, m.pointerX, m.pointerY, m.hp, m.maxHp, m.shield,
            m.heat, m.elapsed, m.overdriveCharge, m.overdriveTime, m.dashPhaseTime, m.speed,
        )
        for (value in values) if (!value.isFinite()) {
            record(FuzzInvariant.NON_FINITE, "core/stat value $value (core ${m.coreX},${m.coreY} v ${m.velocityX},${m.velocityY} hp ${m.hp}/${m.maxHp} heat ${m.heat})", fatalBreak = true)
            return
        }
        if (!m.worldOriginX.isFinite() || !m.worldOriginY.isFinite()) record(FuzzInvariant.NON_FINITE, "world origin ${m.worldOriginX},${m.worldOriginY}", fatalBreak = true)
    }

    private fun checkBounds(m: MutableGameState) {
        fun bound(name: String, size: Int, limit: Int) {
            if (size > limit) record(FuzzInvariant.COLLECTION_BOUND, "$name holds $size > $limit")
        }
        bound("enemies", m.enemies.size, content.rebirth.maxActiveEnemies)
        bound("projectiles", m.projectiles.size, MutableGameState.MAX_PROJECTILES)
        bound("pickups", m.pickups.size, MutableGameState.MAX_PICKUPS)
        bound("trail", m.trail.size, MutableGameState.MAX_TRAIL_POINTS)
        bound("echoHits", m.echoHits.size, MutableGameState.MAX_ECHO_HITS)
        bound("buffs", m.buffs.size, MutableGameState.MAX_BUFFS)
        bound("weaponNodes", m.weaponNodes.size, MutableGameState.MAX_WEAPON_NODES)
        bound("weaponOrbitals", m.weaponOrbitals.size, MutableGameState.MAX_WEAPON_ORBITALS)
        bound("volatileBursts", m.volatileBursts.size, MutableGameState.MAX_VOLATILE_BURSTS)
        bound("severedRelics", m.severedRelics.size, MutableGameState.MAX_SEVERED_RELICS)
        bound("equippedRelics", m.equippedRelics.size, content.relicPolicy.maxSlots)
        bound("choices", m.choices.size, MutableGameState.MAX_CHOICES)
        bound("window sites", m.sites.size, 2)
        for (site in m.sites) {
            bound("site ${site.kind} foes", site.foeIds.size, ContentBounds.MAX_SITE_FOES)
            bound("site ${site.kind} course", site.course.size, ContentBounds.MAX_RACE_GATES)
        }
        bound("ambient sites", m.ambientSites.sites.size, ContentBounds.MAX_AMBIENT_SITES)
        bound("opened site ids", m.openedSiteIds.size, ContentBounds.MAX_OPENED_SITES)
        bound("spin buckets", m.spinBuckets.size, ContentBounds.MAX_SPIN_BUCKETS)
        bound("pending directed rewards", m.pendingDirectedRewards.size, ContentBounds.MAX_DIRECTED_REWARDS)
        bound("pending floor offers", m.pendingFloorOffers.size, ContentBounds.MAX_FLOOR_OFFERS)
        bound("world event plans", m.worldEventPlans.size, ContentBounds.MAX_WORLD_EVENTS_PER_RUN)
        bound("event signals", m.eventSignals.size, ContentBounds.MAX_SIGNAL_SLOTS)
        bound("pending signal prompts", m.pendingSignalPrompts.size, ContentBounds.MAX_PENDING_SIGNAL_PROMPTS)
        bound("encounters", m.encounters.size, MAX_ACTIVE_ENCOUNTERS)
        bound("live tagged enemies", m.enemies.count { it.alive && it.ownerTag != 0L }, MAX_ENCOUNTER_RESERVE)
        bound("obstacles", m.obstacleField.obstacles.size, ContentBounds.MAX_OBSTACLES)
        bound("destroyed obstacles", m.destroyedObstacleIds.size, ContentBounds.MAX_DESTROYED_OBSTACLES)
        bound("run boons", m.runBoons.size, ContentBounds.MAX_RUN_BOONS)
        bound("pending dimension rewards", m.dimensionRun.pendingRewards.size, ContentBounds.MAX_PENDING_DIMENSION_REWARDS)
        bound("portal offers", m.dimensionRun.portalOffers, min(content.dimensions.maxPortalOffersPerRun, ContentBounds.MAX_PORTAL_OFFERS))
        m.dimensionRun.visit?.let { visit ->
            bound("halo rings", visit.halos.size, ContentBounds.MAX_HALO_RINGS)
            bound("embers", visit.embers.size, ContentBounds.MAX_EMBERS)
        }
        bound("structures in window", m.structureField.structures.size, ContentBounds.MAX_STRUCTURES_IN_WINDOW)
        bound("structure walls", m.structureField.walls.size, ContentBounds.MAX_STRUCTURE_WALLS)
        bound("structure signals", m.structureField.signals.size, ContentBounds.MAX_STRUCTURE_SIGNALS)
        bound("spent structures", m.spentStructureIds.size, ContentBounds.MAX_SPENT_STRUCTURES)
        m.dungeon?.let { bound("garrison ids", it.garrisonIds.size, ContentBounds.MAX_STRUCTURE_GARRISON) }
        bound("combat fields", m.combatFieldTable.count, ContentBounds.MAX_COMBAT_FIELDS)
        bound("pending acquired item ids", m.pendingAcquiredItemIds.size, MAX_RECENT_ITEM_IDS)
        bound("pending sound cues", m.soundCues.size, MutableGameState.MAX_GAMEPLAY_SOUND_CUES)
    }

    private fun checkStats(m: MutableGameState) {
        if (m.elapsed < lastElapsed) record(FuzzInvariant.STAT_RANGE, "run time went back ${lastElapsed} -> ${m.elapsed}")
        lastElapsed = m.elapsed
        if (m.phase == GamePhase.RUNNING || m.phase == GamePhase.CHOICE) {
            if (m.hp > m.maxHp + 0.01f) record(FuzzInvariant.STAT_RANGE, "integrity ${m.hp} above its maximum ${m.maxHp}")
            if (m.maxHp <= 0f) record(FuzzInvariant.STAT_RANGE, "maximum integrity ${m.maxHp}")
        }
        if (m.shield < 0f) record(FuzzInvariant.STAT_RANGE, "shield ${m.shield}")
        if (m.heat < 0f || m.heat > MutableGameState.MAX_HEAT + 0.001f) record(FuzzInvariant.STAT_RANGE, "heat ${m.heat}")
        if (m.level < 1 || m.keys < 0 || m.rerollsRemaining < 0 || m.data < 0 || m.kills < 0) {
            record(FuzzInvariant.STAT_RANGE, "level ${m.level} keys ${m.keys} rerolls ${m.rerollsRemaining} data ${m.data} kills ${m.kills}")
        }
        if (m.equippedRelics.map { it.id }.toSet().size != m.equippedRelics.size) record(FuzzInvariant.STAT_RANGE, "a Relic bound twice: ${m.equippedRelics}")
        if (m.runBoons.toSet().size != m.runBoons.size) record(FuzzInvariant.STAT_RANGE, "a boon owned twice: ${m.runBoons}")
        val ids = HashSet<Int>(m.enemies.size * 2)
        for (enemy in m.enemies) if (!ids.add(enemy.id)) record(FuzzInvariant.STAT_RANGE, "enemy id ${enemy.id} twice")
        if (abs(m.coreX) > 300_000f || abs(m.coreY) > 300_000f) record(FuzzInvariant.STAT_RANGE, "Core at ${m.coreX},${m.coreY} beyond the rebase bound")
        if (m.speed > RUNAWAY_CORE_SPEED) record(FuzzInvariant.STAT_RANGE, "the Core runs away at ${m.speed.toInt()} u/s")
    }

    /**
     * Every encounter's owner is still there (the running window trial or the active sigil for a site encounter, the
     * Golden Drifter for its own, the sealed room for a room encounter, the visit for a dimension encounter), and every
     * live tagged enemy belongs to a live encounter. A step's own order may leave either for a moment, so a break needs
     * half a second.
     */
    private fun checkEngagementOwners(m: MutableGameState) {
        var problem: String? = null
        for (encounter in m.encounters) {
            val tag = encounter.tag
            val owned = when (encounterOwner(tag)) {
                EncounterOwner.SITE -> m.activeWindowSite()?.let { siteEncounterTag(it) == tag } == true ||
                    m.secretSigil?.takeIf { it.active }?.let { siteEncounterTag(it) == tag } == true ||
                    (tag == GOLDEN_DRIFTER_TAG && (m.goldenDrifterId >= 0 || m.liveEncounterMembers(tag) == 0))
                EncounterOwner.ROOM -> m.dungeon?.let { it.engagedRoom >= 0 && roomEncounterTag(it.structureId, it.engagedRoom) == tag } == true
                EncounterOwner.DIMENSION -> m.dimensionRun.visit?.let { dimensionEncounterTag(it) == tag } == true
                null -> false
            }
            if (!owned) {
                problem = "encounter ${tag.toString(16)} (${encounterOwner(tag)}, ${m.liveEncounterMembers(tag)} live, started ${encounter.startedAt}) " +
                    "has no owner: window=${m.activeWindowSite()?.kind} sigil=${m.secretSigil?.active} dungeon=${m.dungeon?.let { "room ${it.engagedRoom}" }} " +
                    "visit=${m.dimensionRun.visit?.layer}"
                break
            }
        }
        if (problem == null) {
            for (enemy in m.enemies) {
                if (!enemy.alive || enemy.ownerTag == 0L || m.encounter(enemy.ownerTag) != null) continue
                problem = "live ${enemy.type} ${enemy.id} carries tag ${enemy.ownerTag.toString(16)} of no live encounter"
                break
            }
        }
        if (problem == null && m.goldenDrifterId >= 0 && m.goldenDrifter() == null) {
            problem = "the Golden Drifter ${m.goldenDrifterId} is gone but its chase still runs"
        }
        val held = m.dungeon
        if (problem == null && held != null && held.engagedRoom >= 0 && m.structureField.structures.none { it.id == held.structureId }) {
            problem = "${held.template} room ${held.engagedRoom} is sealed while its structure left the window"
        }
        if (problem == null) {
            orphanSince = Float.NaN
            return
        }
        if (orphanSince.isNaN()) {
            orphanSince = m.elapsed
            orphanDetail = problem
        }
        if (m.elapsed - orphanSince > 0.5f) record(FuzzInvariant.ENGAGEMENT_OWNER, orphanDetail)
    }

    /**
     * A sealed room holds every live enemy its encounter owns inside its walls, and the Core with them (so it can always
     * reach and clear them), or opens within a bounded time: two seconds for an enemy outside, five for the Core shut
     * out; it never stays sealed for a second with nothing left alive.
     */
    private fun checkSealedRoom(m: MutableGameState) {
        val dungeon = m.dungeon
        if (dungeon == null || dungeon.engagedRoom < 0 || m.phase != GamePhase.RUNNING) {
            if (dungeon == null || dungeon.engagedRoom < 0) {
                sealOutsideSince = Float.NaN
                sealEmptySince = Float.NaN
                coreOutsideSince = Float.NaN
            }
            return
        }
        val coreRoom = roomAt(content.structures.template(dungeon.template), dungeon.rotation, dungeon.x, dungeon.y, m.coreX, m.coreY, 0f)
        if (coreRoom != dungeon.engagedRoom) {
            if (coreOutsideSince.isNaN()) coreOutsideSince = m.elapsed
            if (m.elapsed - coreOutsideSince > 5f) {
                record(FuzzInvariant.SEALED_ROOM, "${dungeon.template} room ${dungeon.engagedRoom} stays sealed while the Core is " +
                    "shut out of it at ${m.coreX.toInt()},${m.coreY.toInt()} (room $coreRoom, structure at ${dungeon.x.toInt()},${dungeon.y.toInt()} r${dungeon.rotation})")
            }
        } else {
            coreOutsideSince = Float.NaN
        }
        val template = content.structures.template(dungeon.template)
        val tag = roomEncounterTag(dungeon.structureId, dungeon.engagedRoom)
        var live = 0
        var outside: String? = null
        for (enemy in m.enemies) {
            if (!enemy.alive || enemy.ownerTag != tag) continue
            live++
            if (outside == null && roomAt(template, dungeon.rotation, dungeon.x, dungeon.y, enemy.x, enemy.y, 0f) != dungeon.engagedRoom) {
                outside = "${enemy.type} ${enemy.id} at ${enemy.x.toInt()},${enemy.y.toInt()} (room ${roomAt(template, dungeon.rotation, dungeon.x, dungeon.y, enemy.x, enemy.y, 0f)})"
            }
        }
        if (outside != null) {
            if (sealOutsideSince.isNaN()) sealOutsideSince = m.elapsed
            if (m.elapsed - sealOutsideSince > 2f) {
                record(FuzzInvariant.SEALED_ROOM, "${dungeon.template} room ${dungeon.engagedRoom} stays sealed while $outside is outside it")
            }
        } else {
            sealOutsideSince = Float.NaN
        }
        if (live == 0) {
            if (sealEmptySince.isNaN()) sealEmptySince = m.elapsed
            if (m.elapsed - sealEmptySince > 1f) record(FuzzInvariant.SEALED_ROOM, "${dungeon.template} room ${dungeon.engagedRoom} stays sealed with no live enemy")
        } else {
            sealEmptySince = Float.NaN
        }
    }

    /**
     * The Core's centre never stays inside a solid body: a steady solid obstacle, a solid wall or a sealed door (the
     * push-out keeps it on the surface, pushCoreOutOfObstacles and pushCoreOutOfWalls). A phase shard is no steady
     * surface: one that materialises around the Core lets it drift out unharmed by design (coreMayHit). Half a second of
     * grace covers a step's own order.
     */
    private fun checkEmbedded(m: MutableGameState) {
        if (m.phase != GamePhase.RUNNING) return
        var inside: String? = null
        for (obstacle in m.obstacleField.obstacles) {
            if (!obstacle.isSteadySurface()) continue
            if (length(m.coreX - obstacle.x, m.coreY - obstacle.y) < obstacle.radius - EMBED_DEPTH) {
                inside = "${obstacle.kind} ${obstacle.id} r=${obstacle.radius} at ${obstacle.x.toInt()},${obstacle.y.toInt()}"
                break
            }
        }
        if (inside == null) {
            for (wall in m.structureField.walls) {
                if (wall.kind == WallKind.DOOR_OPEN) continue
                if (pointSegmentDistance(m.coreX, m.coreY, wall.ax, wall.ay, wall.bx, wall.by) < wall.halfThickness - EMBED_DEPTH) {
                    inside = "${wall.kind} wall ${wall.id} (${wall.ax.toInt()},${wall.ay.toInt()})-(${wall.bx.toInt()},${wall.by.toInt()})"
                    break
                }
            }
        }
        if (inside == null) {
            embeddedSince = Float.NaN
            return
        }
        if (embeddedSince.isNaN()) {
            embeddedSince = m.elapsed
            embeddedDetail = inside
        }
        if (m.elapsed - embeddedSince > 0.5f) {
            record(FuzzInvariant.CORE_EMBEDDED, "the Core at ${m.coreX.toInt()},${m.coreY.toInt()} (speed ${m.speed.toInt()}) stays inside $embeddedDetail")
        }
    }

    /** No live enemy stays beyond [STRANDED_DISTANCE] of the Core for [STRANDED_SECONDS] (the leash relocates, encounters abandon). */
    private fun checkStranded(m: MutableGameState) {
        if (m.phase != GamePhase.RUNNING) return
        var far: String? = null
        for (enemy in m.enemies) {
            if (!enemy.alive) continue
            val distance = length(enemy.x - m.coreX, enemy.y - m.coreY)
            if (distance > STRANDED_DISTANCE) {
                far = "${enemy.type} ${enemy.id} (tag ${enemy.ownerTag.toString(16)}) ${distance.toInt()} u away"
                break
            }
        }
        if (far == null) {
            strandedSince = Float.NaN
            return
        }
        if (strandedSince.isNaN()) {
            strandedSince = m.elapsed
            strandedDetail = far
        }
        if (m.elapsed - strandedSince > STRANDED_SECONDS) record(FuzzInvariant.ENEMY_STRANDED, "$strandedDetail for $STRANDED_SECONDS s")
    }

    /** A visit ends by its timer (never past it, never past the Architect's margin), and only a visit leaves realspace. */
    private fun checkDimension(m: MutableGameState) {
        val visit = m.dimensionRun.visit
        if (visit == null) {
            if (m.worldLayer != WorldLayer.BASE) record(FuzzInvariant.DIMENSION_TIMER, "the run is in ${m.worldLayer} without a visit")
            return
        }
        if (m.worldLayer != visit.layer) record(FuzzInvariant.DIMENSION_TIMER, "a ${visit.layer} visit while the run is in ${m.worldLayer}")
        if (m.elapsed > visit.endsAt + 2f * MutableGameState.FIXED_STEP) {
            record(FuzzInvariant.DIMENSION_TIMER, "a ${visit.layer} visit runs at ${m.elapsed} past its timer ${visit.endsAt}")
        }
        val latest = content.tempo.bossAtSeconds - content.dimensions.exitMarginSeconds
        if (visit.endsAt > latest + 0.001f) record(FuzzInvariant.DIMENSION_TIMER, "a ${visit.layer} visit ends at ${visit.endsAt} after $latest")
        if (m.bossSpawned) record(FuzzInvariant.DIMENSION_TIMER, "a ${visit.layer} visit while the Architect is out")
    }

    /** A queued choice opens within three running seconds. */
    private fun checkQueue(m: MutableGameState) {
        if (m.phase != GamePhase.RUNNING) return
        val queued = buildList {
            if (m.pendingLevelChoices > 0) add("level x${m.pendingLevelChoices}")
            if (m.pendingDirectedRewards.isNotEmpty()) add("directed ${m.pendingDirectedRewards}")
            if (m.pendingFloorOffers.isNotEmpty()) add("floor ${m.pendingFloorOffers}")
            if (m.pendingRelicChoices > 0) add("relic x${m.pendingRelicChoices}")
            if (m.dimensionRun.pendingRewards.isNotEmpty()) add("dimension ${m.dimensionRun.pendingRewards}")
            if (m.pendingSignalPrompts.isNotEmpty()) add("signal ${m.pendingSignalPrompts}")
            if (m.omenPending) add("omen")
            if (m.heirloomPending) add("heirloom")
        }
        if (queued.isEmpty()) {
            queuedSince = Float.NaN
            return
        }
        if (queuedSince.isNaN()) queuedSince = m.elapsed
        if (m.elapsed - queuedSince > 3f) record(FuzzInvariant.CHOICE_QUEUE, "queued choices never open while the run runs: $queued")
    }

    private fun checkEasy(m: MutableGameState) {
        if (mode != RunMode.EASY) return
        if (!m.easyMode) record(FuzzInvariant.EASY_MODE, "a Baby mode run's state is not easy")
        if (m.phase == GamePhase.GAME_OVER && m.message == "SINGULARITY CONTACT") record(FuzzInvariant.EASY_MODE, "a Baby mode run ended on singularity contact")
        if (m.phase == GamePhase.GAME_OVER && m.message == "OBSTACLE COLLISION") record(FuzzInvariant.EASY_MODE, "a Baby mode run died to an obstacle")
        if (m.hasPublishableProgress()) record(FuzzInvariant.EASY_MODE, "a Baby mode run holds publishable progress")
        if (m.heirloomPending || m.heirloomOfferActive || m.choices.any { it.heirloom }) {
            record(FuzzInvariant.EASY_MODE, "a Baby mode run offers Heirlooms")
        }
    }

    private fun coverage(m: MutableGameState) {
        val sealed = (m.dungeon?.engagedRoom ?: -1) >= 0
        if (sealed && !wasSealed) seals++
        wasSealed = sealed
        val visiting = m.dimensionRun.visit != null
        if (visiting && !wasVisiting) {
            visits++
            visitedLayers[m.dimensionRun.visit!!.layer.ordinal]++
        }
        completedLayers = completedLayers or m.dimensionRun.completedMask
        wasVisiting = visiting
        val trial = m.activeWindowSite() != null || m.secretSigil?.active == true
        if (trial && !wasTrial) siteTrials++
        wasTrial = trial
    }

    private fun record(invariant: FuzzInvariant, detail: String, fatalBreak: Boolean = false) {
        if (fatalBreak) fatal = true
        if (!recorded.add(invariant)) return
        val m = state.engine?.model
        val context = "${plan.label()} pilot=${pilot.label} phase=${m?.phase} layer=${m?.worldLayer} runSeed=$runSeed"
        breaks += FuzzBreak(plan.seed, run, step, m?.elapsed ?: 0f, invariant, detail, context)
    }
}

/** How deep (u) inside a solid body the Core's centre must sit to count as embedded (a contact may graze the surface). */
private const val EMBED_DEPTH = 4f

/** Beyond every leash (screen-sized) and abandon distance (at most 6,000 u). */
private const val STRANDED_DISTANCE = 10_000f
private const val STRANDED_SECONDS = 20f

/** Far above any Dash, accelerator gate or slingshot the flight law allows: a runaway Core. */
private const val RUNAWAY_CORE_SPEED = 15_000f

/** Distance from ([x], [y]) to the segment ([ax], [ay])-([bx], [by]). */
private fun pointSegmentDistance(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax
    val dy = by - ay
    val lengthSquared = dx * dx + dy * dy
    val along = if (lengthSquared <= 1e-6f) 0f else (((x - ax) * dx + (y - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
    return length(x - ax - dx * along, y - ay - dy * along)
}

/** How far (dp) a jitter twitch keeps the singularity's path from the Core's centre. */
private const val TWITCH_CORE_CLEARANCE = 60f

private val BotCursorAngle: Field = NoPoiBot::class.java.getDeclaredField("cursorAngle").apply { isAccessible = true }

/**
 * Turns the bot's remembered cursor to where the pointer is: another pilot (or the jitter) may have moved it, and the
 * bot swings its cursor from what it remembers, so a stale angle would throw the singularity across the Core.
 */
private fun syncBotCursor(bot: NoPoiBot, s: MutableGameState) {
    val angle = atan2(s.pointerY - s.screenHeight * 0.5f - (s.coreY - s.cameraY), s.pointerX - s.screenWidth * 0.5f - (s.coreX - s.cameraX))
    BotCursorAngle.setFloat(bot, angle)
}

/** A short text of a throwable: its type, message and the frames of the game and the harness it passed through. */
private fun describe(failure: Throwable): String {
    val frames = failure.stackTrace.filter { it.className.startsWith("kinetickk.") }.take(8)
        .joinToString(" <- ") { "${it.fileName}:${it.lineNumber}" }
    return "${failure::class.simpleName}: ${failure.message} at $frames"
}

private fun GameplayInteractionPulse.label(): String = when (this) {
    is GameplayInteractionPulse.FrameElapsed -> "FrameElapsed($realDeltaSeconds)"
    is GameplayInteractionPulse.PointerMoved -> "PointerMoved($x,$y,$active)"
    is GameplayInteractionPulse.ViewportChanged -> "ViewportChanged($width,$height,$density)"
    is GameplayInteractionPulse.ChoiceSelected -> "ChoiceSelected($index)"
    else -> toString()
}

private fun kinetickk.ball.gameplay.nucleus.render.ChoiceOption.cardLabel(): String =
    "$type '$title' item=$itemId weapon=$weaponId totem=$totemAction relic=$relicId/$relicAction slot=$relicSlot focus=$rewardFocus " +
        "omen=$omenId signal=$signalKind/$signalOption boon=$boonId activeSlot=$activeSlot locked=$locked"

// -------------------------------------------------------------------------------------------------
// Pilots: each decides on the copy it is given; the session reads its decision back as pulses.

private interface FuzzPilot {
    val label: String
    fun control(s: MutableGameState)
    fun choose(s: MutableGameState)

    /** Sees the committed state after every frame. */
    fun observe(s: MutableGameState) = Unit
}

private class BotFuzzPilot(private val bot: NoPoiBot) : FuzzPilot {
    override val label = "bot"
    override fun control(s: MutableGameState) = bot.control(s)
    override fun choose(s: MutableGameState) = bot.choose(s)
}

private class SiteFuzzPilot(private val pilot: SitePilot) : FuzzPilot {
    override val label = "site/${pilot.arm}"
    override fun control(s: MutableGameState) = pilot.control(s)
    override fun choose(s: MutableGameState) = pilot.choose(s)
}

private class DungeonFuzzPilot(private val pilot: DungeonPilot) : FuzzPilot {
    override val label = "dungeon"
    override fun control(s: MutableGameState) = pilot.control(s)
    override fun choose(s: MutableGameState) = pilot.choose(s)
    override fun observe(s: MutableGameState) = pilot.observe(s)
}

private class DimensionFuzzPilot(private val pilot: DimensionPilot) : FuzzPilot {
    override val label = "dimension/${pilot.target}"
    override fun control(s: MutableGameState) = pilot.control(s)
    override fun choose(s: MutableGameState) = pilot.choose(s)
}

/**
 * The bot's flight and choices, with the pointer turned toward the nearest opt-in site (an event signal, an ambient
 * cache or egg, a window offer, the sigil, the glitch rift, a visit's exit rift) through the production steering
 * inverse; the cursor swings at most the bot's 0.45 rad a frame and stays where steering keeps polarity.
 */
private class SeekFuzzPilot(private val bot: NoPoiBot) : FuzzPilot {
    override val label = "seek"

    override fun choose(s: MutableGameState) = bot.choose(s)

    override fun control(s: MutableGameState) {
        bot.control(s)
        if (s.phase != GamePhase.RUNNING) return
        var bestX = 0f
        var bestY = 0f
        var best = SEEK_REACH
        fun consider(x: Float, y: Float) {
            val distance = length(x - s.coreX, y - s.coreY)
            if (distance < best) {
                best = distance
                bestX = x
                bestY = y
            }
        }
        for (signal in s.eventSignals) consider(signal.x, signal.y)
        for (site in s.ambientSites.sites) consider(site.x, site.y)
        for (site in s.sites) if (!site.active) consider(site.x, site.y)
        s.secretSigil?.let { consider(it.x, it.y) }
        s.dimensionRun.glitchPortal?.let { consider(it.x, it.y) }
        s.dimensionRun.visit?.exitPortal?.let { consider(it.x, it.y) }
        if (best >= SEEK_REACH) return
        val toX = bestX - s.coreX
        val toY = bestY - s.coreY
        val distance = max(1f, length(toX, toY))
        var desiredX = toX / distance * SEEK_SPEED
        var desiredY = toY / distance * SEEK_SPEED
        for (obstacle in s.obstacleField.obstacles) {
            val reach = s.obstacleHazardReach(obstacle)
            if (reach <= 0f) continue
            val px = s.coreX + s.velocityX * 0.3f - obstacle.x
            val py = s.coreY + s.velocityY * 0.3f - obstacle.y
            val clearance = reach + s.coreRadius + if (obstacle.kind.solid) 110f else 40f
            val pd = max(1f, length(px, py))
            if (pd < clearance) {
                desiredX += px / pd * (clearance - pd) * 6f
                desiredY += py / pd * (clearance - pd) * 6f
            }
        }
        val drag = s.steeringDrag()
        val aim = s.steeringAimFor((desiredX - s.velocityX) * 5f + s.velocityX * drag, (desiredY - s.velocityY) * 5f + s.velocityY * drag)
        val offsetX = s.coreX - s.cameraX
        val offsetY = s.coreY - s.cameraY
        val held = atan2(s.pointerY - s.screenHeight * 0.5f - offsetY, s.pointerX - s.screenWidth * 0.5f - offsetX)
        val turn = atan2(sin(aim.heading - held), cos(aim.heading - held))
        val angle = held + turn.coerceIn(-0.45f, 0.45f)
        val ux = cos(angle)
        val uy = sin(angle)
        val halfWidth = s.screenWidth * 0.5f * s.content.tempo.fatigue.edgeStrainStart - 10f
        val halfHeight = s.screenHeight * 0.5f * s.content.tempo.fatigue.edgeStrainStart - 10f
        val availableX = if (ux > 0f) (halfWidth - offsetX) / ux else if (ux < 0f) (-halfWidth - offsetX) / ux else 10_000f
        val availableY = if (uy > 0f) (halfHeight - offsetY) / uy else if (uy < 0f) (-halfHeight - offsetY) / uy else 10_000f
        val tether = min(aim.distance.coerceIn(65f, 400f), min(availableX, availableY)).coerceAtLeast(65f)
        s.updatePointer(s.screenWidth * 0.5f + offsetX + ux * tether, s.screenHeight * 0.5f + offsetY + uy * tether)
    }

    private companion object {
        const val SEEK_REACH = 6_000f
        const val SEEK_SPEED = 560f
    }
}

// -------------------------------------------------------------------------------------------------
// Reflective state checks.

/** Candidate-private scratch of [MutableGameState]: never read across steps or casts, so never copied. */
internal val FuzzScratchFields: Set<String> = setOf(
    "skillScratch", "castSlot", "castHits", "castAttunedStacks", "castPrimaryId", "castBossHitFirst", "castBossHitSecond",
    "chainScratch", "chainDepth", "shatteringEnemyId",
)

private fun isGameClass(type: Class<*>): Boolean = type.name.startsWith("kinetickk.")

private val FieldCache = ConcurrentHashMap<Class<*>, List<Field>>()

/** Every instance field of [type] and its game superclasses, accessible. */
private fun instanceFields(type: Class<*>): List<Field> = FieldCache.getOrPut(type) {
    val fields = ArrayList<Field>()
    var current: Class<*>? = type
    while (current != null && isGameClass(current)) {
        for (field in current.declaredFields) {
            if (Modifier.isStatic(field.modifiers) || field.isSynthetic) continue
            field.isAccessible = true
            fields += field
        }
        current = current.superclass
    }
    fields
}

private val ListViewCache = ConcurrentHashMap<Class<*>, java.util.Optional<java.lang.reflect.Method>>()

/** The content list of a copy-on-write array (its public `toList()`), or null for any other class. */
private fun copyOnWriteView(value: Any): List<*>? {
    if (!value.javaClass.simpleName.startsWith("CopyOnWrite")) return null
    val method = ListViewCache.getOrPut(value.javaClass) {
        java.util.Optional.ofNullable(value.javaClass.methods.firstOrNull { it.name == "toList" && it.parameterCount == 0 })
    }.orElse(null) ?: return null
    return method.invoke(value) as List<*>
}

/**
 * The first logical difference between two game values, as a path, or null when they are equal: floats by their bits,
 * copy-on-write arrays and sets by content, a hit history (a projectile's, a weapon entity's) up to its count, random
 * streams by snapshot,
 * every other game class field by field (skipping [exclude] at the top level).
 */
internal object FuzzStateDiff {
    fun first(a: Any?, b: Any?, path: String, exclude: Set<String> = emptySet(), depth: Int = 0): String? {
        if (a === b) return null
        if (a == null || b == null) return "$path: $a vs $b"
        if (a.javaClass != b.javaClass) return "$path: ${a.javaClass.simpleName} vs ${b.javaClass.simpleName}"
        if (depth > 12) return null
        when (a) {
            is Float -> return if (a.toRawBits() == (b as Float).toRawBits()) null else "$path: $a vs $b"
            is Double -> return if (a.toRawBits() == (b as Double).toRawBits()) null else "$path: $a vs $b"
            is Number, is Boolean, is Char, is String, is Enum<*> -> return if (a == b) null else "$path: $a vs $b"
            is FloatArray -> return arrayDifference(path, a.size, (b as FloatArray).size) { a[it].toRawBits() == b[it].toRawBits() }
            is IntArray -> return arrayDifference(path, a.size, (b as IntArray).size) { a[it] == b[it] }
            is LongArray -> return arrayDifference(path, a.size, (b as LongArray).size) { a[it] == b[it] }
            is BooleanArray -> return arrayDifference(path, a.size, (b as BooleanArray).size) { a[it] == b[it] }
            is DoubleArray -> return arrayDifference(path, a.size, (b as DoubleArray).size) { a[it].toRawBits() == b[it].toRawBits() }
            is Array<*> -> {
                b as Array<*>
                if (a.size != b.size) return "$path.size: ${a.size} vs ${b.size}"
                for (index in a.indices) first(a[index], b[index], "$path[$index]", depth = depth + 1)?.let { return it }
                return null
            }
            is CloneableXorWowRandom -> return if (a.snapshot() == (b as CloneableXorWowRandom).snapshot()) null else "$path: random stream"
            is Set<*> -> return if (HashSet(a) == HashSet(b as Set<*>)) null else "$path: ${a.size} vs ${b.size} elements"
            is List<*> -> {
                b as List<*>
                if (a.size != b.size) return "$path.size: ${a.size} vs ${b.size}"
                for (index in a.indices) first(a[index], b[index], "$path[$index]", depth = depth + 1)?.let { return it }
                return null
            }
            is Map<*, *> -> return if (a == b) null else "$path: map"
        }
        if (!isGameClass(a.javaClass)) return if (a == b) null else "$path: $a vs $b"
        copyOnWriteView(a)?.let { view -> return first(view, copyOnWriteView(b), path, depth = depth + 1) }
        val fields = instanceFields(a.javaClass)
        for (field in fields) {
            if (depth == 0 && field.name in exclude) continue
            val left = field.get(a)
            val right = field.get(b)
            // A hit history (`xIds` with its `xCount`) is copied up to its count: what lies past it is stale.
            val count = if (field.name.endsWith("Ids") && left is IntArray?) countField(fields, field.name) else null
            if (count != null) {
                val used = count.getInt(a)
                if (used != count.getInt(b)) return "$path.${count.name}: $used vs ${count.getInt(b)}"
                val l = left as IntArray?
                val r = right as IntArray?
                for (index in 0 until used) if (l!![index] != r!![index]) return "$path.${field.name}[$index]"
                continue
            }
            first(left, right, "$path.${field.name}", depth = depth + 1)?.let { return it }
        }
        return null
    }

    private fun countField(fields: List<Field>, name: String): Field? {
        val countName = name.removeSuffix("Ids") + "Count"
        return fields.firstOrNull { it.name == countName && it.type == Integer.TYPE }
    }

    private inline fun arrayDifference(path: String, left: Int, right: Int, equal: (Int) -> Boolean): String? {
        if (left != right) return "$path.size: $left vs $right"
        for (index in 0 until left) if (!equal(index)) return "$path[$index]"
        return null
    }
}

/**
 * A scan of every retained value of a committed state for NaN anywhere and for an infinity outside the fields whose
 * infinity means "never" or "none" ([InfiniteAllowed]). It walks the state's game classes, lists and arrays, but not
 * the content snapshot, the compiled build or the random streams.
 */
internal object FuzzFiniteScan {
    /**
     * Fields whose ±∞ is a documented sentinel: no obstacle near, no split running, no expiry, never damaged, no boss due,
     * no solar sweep yet, a mark never revealed, an age never started.
     */
    val InfiniteAllowed = setOf(
        "coreObstacleGap", "splitEndsAt", "expiresAt", "lastDamagedAt", "bossSpawnAt", "sweepStartedAt", "reveal", "age",
    )

    private val Skipped = setOf("content", "build", "itemHistory", "itemDeck", "matrix", "activeRebirthProfile", "soundCueStorage", "visualFxCueStorage")

    fun first(state: MutableGameState): String? = scan(state, "state", 0)

    private fun scan(value: Any?, path: String, depth: Int): String? {
        if (value == null || depth > 8) return null
        when (value) {
            is Float -> return if (value.isNaN()) "$path = NaN" else null
            is Double -> return if (value.isNaN()) "$path = NaN" else null
            is FloatArray -> {
                for (index in value.indices) if (value[index].isNaN()) return "$path[$index] = NaN"
                return null
            }
            is DoubleArray -> {
                for (index in value.indices) if (value[index].isNaN()) return "$path[$index] = NaN"
                return null
            }
            is List<*> -> {
                for (index in value.indices) scan(value[index], "$path[$index]", depth + 1)?.let { return it }
                return null
            }
            is Array<*> -> {
                for (index in value.indices) scan(value[index], "$path[$index]", depth + 1)?.let { return it }
                return null
            }
            is Iterable<*> -> {
                for (element in value) scan(element, "$path[]", depth + 1)?.let { return it }
                return null
            }
        }
        val type = value.javaClass
        if (!isGameClass(type) || value is Enum<*> || value is CloneableXorWowRandom) return null
        copyOnWriteView(value)?.let { view ->
            for (index in view.indices) {
                val element = view[index]
                if (element is Float && element.isNaN()) return "$path[$index] = NaN"
            }
            return null
        }
        for (field in instanceFields(type)) {
            val name = field.name
            if (depth == 0 && name in Skipped) continue
            when (field.type) {
                java.lang.Float.TYPE -> {
                    val number = field.getFloat(value)
                    if (number.isNaN() || (number.isInfinite() && name !in InfiniteAllowed)) return "$path.$name = $number"
                }
                java.lang.Double.TYPE -> {
                    val number = field.getDouble(value)
                    if (number.isNaN() || (number.isInfinite() && name !in InfiniteAllowed)) return "$path.$name = $number"
                }
                java.lang.Integer.TYPE, java.lang.Long.TYPE, java.lang.Boolean.TYPE, java.lang.Short.TYPE, java.lang.Byte.TYPE,
                java.lang.Character.TYPE, String::class.java -> Unit
                else -> when (val child = field.get(value)) {
                    is Float -> if (child.isNaN() || (child.isInfinite() && name !in InfiniteAllowed)) return "$path.$name = $child"
                    is Double -> if (child.isNaN() || (child.isInfinite() && name !in InfiniteAllowed)) return "$path.$name = $child"
                    else -> scan(child, "$path.$name", depth + 1)?.let { return it }
                }
            }
        }
        return null
    }
}
