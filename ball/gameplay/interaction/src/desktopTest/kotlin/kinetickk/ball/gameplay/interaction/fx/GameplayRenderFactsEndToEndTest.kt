// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.DraftDeckPolicy
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.defaultLevelRequirements
import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.nucleus.GameplayContext
import kinetickk.ball.gameplay.nucleus.GameplayDecision
import kinetickk.ball.gameplay.nucleus.GameplayNucleus
import kinetickk.ball.gameplay.nucleus.GameplayNucleusPulse
import kinetickk.ball.gameplay.nucleus.GameplayOutput
import kinetickk.ball.gameplay.nucleus.GameplayStartContext
import kinetickk.ball.gameplay.nucleus.GameplayStartInputs
import kinetickk.ball.gameplay.nucleus.GameplayState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kinetickk.ball.gameplay.interaction.canvas.BossTarget
import kinetickk.ball.gameplay.interaction.canvas.FxDrawBudget
import kinetickk.ball.gameplay.interaction.canvas.FxFrame
import kinetickk.ball.gameplay.interaction.canvas.FxPool
import kinetickk.ball.gameplay.interaction.canvas.HudBlock
import kinetickk.ball.gameplay.interaction.canvas.HudLayoutProbe
import kinetickk.ball.gameplay.interaction.canvas.HudPresentationMemory
import kinetickk.ball.gameplay.interaction.canvas.HudTestFonts
import kinetickk.ball.gameplay.interaction.canvas.drawGameplay
import kinetickk.ball.gameplay.interaction.canvas.with
import kinetickk.ball.gameplay.nucleus.protocol.FxColorCode
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.protocol.ReactionCueFlags
import kinetickk.ball.gameplay.nucleus.protocol.SkillCueCodes
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.CombatFieldSource
import kinetickk.ball.gameplay.nucleus.render.CoreStateProjection
import kinetickk.ball.gameplay.nucleus.render.EnemyControlBits
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PermanentUpgrades
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileProgressApplied
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ReactionEffectsLevel
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.CanvasTextMeasurer
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runs the real simulation through the public Nucleus contract and hands every published cue to the real
 * [InteractionFxReducer], as the gameplay component does, then draws what the runs published: the render facts
 * of hud-fx §1 that gameplay emits reach the reducer, the HUD's presentation memory and the world and HUD
 * renderers. A short content level curve (6 levels, every active item unlocked from level 1, the whole catalog dealt and
 * actives weighted up) lets a few minutes of bot play take three active items and use the Apex; several seeds together
 * cover the facts that depend on which actives the offers dealt.
 *
 * Covered elsewhere with targeted state: reaction headings (`ReactionBehaviourTest`), Skill Charges'
 * `maxCharges` 2 (`RuleVocabularyTest`) and every link style and colour index's look (`ReactionFxSpecTest`).
 */
class GameplayRenderFactsEndToEndTest {
    private val runs: List<FactRun> get() = FactRuns.all

    @Test
    fun everyPublishedCueReachesTheInteractionReducer() {
        runs.forEach { run -> println("RENDER-FACTS seed=${run.seed} ${run.report()}") }
        val casts = runs.flatMap { it.skillCasts }.toSet()
        val projected = runs.flatMap { it.projectedSkillPhases }.toSet()
        assertTrue(casts.any { it.second == SkillCueCodes.PHASE_CAST }, "no skill cast")
        assertTrue(casts.any { it.second == SkillCueCodes.PHASE_IMPACT }, "no delayed impact (Meteor, Void Step, Null Shroud, Gravity Well)")
        // Every skill cue the runs published became a skill look in the reducer's projection.
        assertEquals(casts.map { it.first.ordinal to it.second }.toSet(), projected)
        assertTrue(runs.sumOf { it.apexActivations } > 0, "no Apex activation")
        runs.forEach { run -> if (run.apexActivations > 0) assertTrue(run.projectedApex > 0, "seed ${run.seed}: Apex look missing") }
        // Reaction cues carry their flags into the reducer's flashes (the verb branch and cascades fire in play).
        val flags = runs.fold(0) { bits, run -> bits or run.reactionFlags }
        assertTrue(flags and ReactionCueFlags.VERB != 0, "no reaction verb flag")
        runs.forEach { run -> assertEquals(run.reactionFlags, run.projectedReactionFlags, "seed ${run.seed}: reaction flags lost") }
        // Element tones, Apex gold and styled links arrive as published.
        val waves = runs.flatMap { it.shockwaveColors }.toSet()
        assertTrue(waves.any { it in FxColorCode.ELEMENT_TONE_BASE until FxColorCode.APEX_GOLD }, "no element-tone ring: $waves")
        assertTrue(FxColorCode.APEX_GOLD in runs.flatMap { it.burstColors + it.shockwaveColors }, "no Apex gold")
        runs.forEach { run ->
            val published = run.shockwaveColors.filter { it >= FxColorCode.ELEMENT_TONE_BASE }
            assertTrue(run.projectedShockwaveColors.containsAll(published), "seed ${run.seed}: rings $published -> ${run.projectedShockwaveColors}")
            assertTrue(run.projectedLinkStyles.containsAll(run.linkStyles), "seed ${run.seed}: link styles ${run.linkStyles} -> ${run.projectedLinkStyles}")
        }
        assertTrue(runs.flatMap { it.linkStyles }.any { it != LinkStyleCode.WEAPON }, "no styled link")
    }

    @Test
    fun theRunsProjectionsCarryEveryHudFact() {
        assertTrue(runs.all { it.modelMaxLevel == SHORT_MAXIMUM_LEVEL }, "maxLevel is the content cap")
        assertTrue(runs.any { it.maxSkillsOwned == 3 }, "three slots filled")
        assertTrue(runs.any { it.refusals > 0 }, "a refused request shows on its slot")
        assertTrue(runs.any { it.skillRefunded > 0f }, "a refund shows on its slot")
        assertTrue(runs.any { it.skillPending > 0 }, "a delayed skill shows its pending impact")
        assertTrue(runs.any { it.skillActive > 0 }, "an active skill shows its remaining time")
        assertTrue(runs.all { it.maxChargesSeen <= 1 }, "one charge without Skill Charges")
        val apexRuns = runs.filter { it.apexUnlocked }
        assertTrue(apexRuns.isNotEmpty(), "no run unlocked the Apex")
        assertTrue(apexRuns.any { it.apexReadySeen && it.apexActiveSeen && it.apexCooling }, "Apex ready, active and cooling")
        assertTrue(apexRuns.any { it.apexRefunded > 0f }, "Data after the cap refunds the Apex")
        assertTrue(apexRuns.any { it.apexStrikesSeen > 0 }, "an activation carries its strikes")
        val sources = runs.flatMap { it.fieldSources }.toSet()
        assertTrue(CombatFieldSource.SKILL in sources && CombatFieldSource.REACTION in sources, "fields from skills and reactions: $sources")
        val bits = runs.fold(0) { all, run -> all or run.controlBits }
        assertTrue(bits != 0, "no enemy control bits")
        assertTrue(bits and EnemyControlBits.APEX_STRIKE_TARGET != 0, "no Apex strike target: ${Integer.toBinaryString(bits)}")
        assertTrue(runs.any { it.coreStates > 0 }, "no Core state")
    }

    @Test
    fun theHudAndTheWorldDrawWhatTheRunsPublished() {
        // The presentation memory saw the one-shots the projections drive.
        assertTrue(runs.any { it.skillAcquiredShown }, "active acquired one-shot")
        assertTrue(runs.any { it.apexUnlockShown }, "Apex unlock one-shot")
        assertTrue(runs.any { it.apexCastShown }, "Apex cast one-shot")
        assertTrue(runs.any { it.apexRefundShown }, "Apex refund flash")
        val samples = runs.flatMap { it.samples.entries }.groupBy({ it.key }, { it.value })
        fun drawn(kind: String, pool: FxPool) {
            val (model, fx) = samples[kind]?.first() ?: error("no $kind frame")
            drawFrame(model, fx)
            assertTrue(FxDrawBudget.used(pool) > 0, "$kind drew nothing in $pool")
        }
        drawn("fields", FxPool.FIELDS)
        drawn("control-bits", FxPool.GLYPHS)
        drawn("core-state", FxPool.CORE_STATES)
        drawn("skill-burst", FxPool.TRANSIENT)
        drawn("apex-burst", FxPool.TRANSIENT)
        drawn("styled-link", FxPool.LINKS)
        val (model, fx) = samples["skill-bar"]?.first() ?: error("no skill bar frame")
        drawFrame(model, fx)
        assertNotNull(HudLayoutProbe.rect(HudBlock.SKILLS), "the skill row was not drawn")
    }

    /**
     * Reaction effects (Settings → Profile → run → FX): the level a running run receives through the Nucleus is the
     * one the world FX layer draws with; the same frame of reaction flashes costs less at each lower level.
     */
    @Test
    fun theReactionEffectsLevelTheRunReceivedShapesTheFxLayer() {
        val (frame, fx) = runs.flatMap { it.samples.entries }.first { it.key == "reactions" }.value
        val used = ReactionEffectsLevel.entries.associateWith { level ->
            val settings = runWithReactionEffects(level).settings
            assertEquals(level, settings.reactionEffects, "the run applied $level")
            drawFrame(frame.with("settings" to settings), fx)
            assertEquals(level, FxFrame.level, "the FX layer drew at $level")
            FxDrawBudget.used(FxPool.TRANSIENT)
        }
        assertTrue(used.getValue(ReactionEffectsLevel.FULL) > used.getValue(ReactionEffectsLevel.REDUCED), "$used")
        assertTrue(used.getValue(ReactionEffectsLevel.REDUCED) >= used.getValue(ReactionEffectsLevel.MINIMAL), "$used")
        assertTrue(used.getValue(ReactionEffectsLevel.MINIMAL) > 0, "$used")
    }

    /** A started run's model after the Nucleus applied preferences with [level] (as Session does when Settings closes). */
    private fun runWithReactionEffects(level: ReactionEffectsLevel): GameplayRenderModel {
        val content = shortCurveContent()
        val profile = PlayerProfile(preferences = PlayerPreferences(language = AppLanguage.English))
        val started = (GameplayNucleus.decide(
            GameplayState.initial(RunId(2), content),
            GameplayNucleusPulse.StartRun,
            GameplayContext(start = GameplayStartContext.Ready(GameplayStartInputs(content,
                GameplayProfileSnapshot(profile.preferences, profile.economy, profile.loadout, profile.matrix, profile.collection,
                    profile.rebirthProgress), seed = 7))),
        ) as GameplayDecision.Accepted).frame.nextState
        val applied = (GameplayNucleus.decide(started, GameplayNucleusPulse.ApplyPreferences(profile.preferences.copy(reactionEffects = level)))
            as GameplayDecision.Accepted).frame.nextState
        return requireNotNull(GameplayNucleus.renderSnapshot(applied).renderModel)
    }

    private fun drawFrame(model: GameplayRenderModel, fx: VisualFxProjection) {
        val measurer = CanvasTextMeasurer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr), 1f,
            AppLanguage.English, HudTestFonts.typography)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(1_440, 810)), Size(1_440f, 810f)) {
            drawGameplay(model, fx, measurer, model.elapsed, pauseLayout = null, hudMemory = HudPresentationMemory())
        }
    }
}

/** The bot runs, played once for the class (a few seconds of simulation in all). */
internal object FactRuns {
    val all: List<FactRun> by lazy { SEEDS.map { seed -> FactRun(seed).also { it.play() } } }
}

/**
 * The shipped catalog with a short level curve and active items offered from the first offer (unlocked at level 1, the
 * whole catalog dealt, a high active weight), so a minute of play fills three slots and unlocks the Apex.
 */
internal fun shortCurveContent(): GameplayContentSnapshot {
    val shipped = createContentCatalog().gameplayContent()
    val maximum = SHORT_MAXIMUM_LEVEL
    val policy = shipped.build.policy
    return shipped.copy(
        items = shipped.items.map { if (it.isActive) it.copy(unlockLevel = 1) else it }.toImmutableList(),
        build = shipped.build.copy(
            policy = policy.copy(
                draftDeck = DraftDeckPolicy.FULL,
                // Flat lane and owned factors: the bot's draft preference, not the shipped lane tuning, picks the actives.
                offerLanes = policy.offerLanes.copy(
                    activeWeight = SHORT_ACTIVE_WEIGHT, characterActiveWeight = 1f, ownedActiveWeight = 1f,
                ),
            ),
        ),
        tempo = shipped.tempo.copy(
            maximumLevel = maximum,
            levelRequirements = defaultLevelRequirements(maximumLevel = maximum),
            firstEliteAtSeconds = 20f,
            // The Architect (boss-class: the Apex strikes it) arrives while the Apex is in play.
            bossAtSeconds = ARCHITECT_AT_SECONDS,
        ),
        // Seeded frames sample skill and reaction facts: realspace keeps the asteroid field without the obstacle kind mix.
        obstacles = shipped.obstacles.copy(kinds = shipped.obstacles.kinds.copy(space = kinetickk.ball.content.api.ObstacleMix())),
    )
}

private const val SHORT_MAXIMUM_LEVEL = 6
/** Inside a rolled tier an active item outweighs every passive one, so most offers carry one. */
private const val SHORT_ACTIVE_WEIGHT = 1_000f
private const val ARCHITECT_AT_SECONDS = 110f
private val SEEDS = listOf(2026, 4711, 90_210, 31_337, 77_003, 123_456, 555, 8_080, 42_424, 271_828, 314_159, 1_618)

/**
 * Active effects the bot takes, in preference: delayed impacts first, then lingering fields, then the rest; actives that
 * move the Core (Void Step, Arc Hook, Slingshot, Mass Driver, Full Stop) are left out, so the bot keeps clear of the
 * singularity.
 */
private val DRAFT_PREFERENCE = listOf(
    SkillId.METEOR, SkillId.STORM_COIL, SkillId.NULL_SHROUD, SkillId.GRAVITY_WELL, SkillId.FROST_LINE, SkillId.RESONATOR,
    SkillId.SOLAR_FLARE, SkillId.FROST_SNAP, SkillId.DECAY_SPORES, SkillId.ENTROPIC_TIDE, SkillId.SONIC_BOOM, SkillId.CATALYZE,
    SkillId.DUAL_INFUSION,
)

/**
 * One bot-driven run through the public Nucleus contract: steers at the nearest enemy, takes active items, casts them
 * (and, now and then, a cooling one) and the Apex, applies earned progress, and records the cues it published, what
 * the reducer projected from them and the render-model facts it showed.
 */
internal class FactRun(val seed: Int, private val seconds: Float = 240f) {
    val content = shortCurveContent()
    val reducer = InteractionFxReducer(seed)
    private lateinit var state: GameplayState

    // Cues as gameplay published them.
    val skillCasts = HashSet<Pair<SkillId, Int>>()
    var apexActivations = 0
    var reactionFlags = 0
    var reactionDirections = 0
    /** Reaction cue radii seen, per reaction ordinal: (smallest, largest). */
    val reactionRadii = HashMap<Int, Pair<Float, Float>>()
    val linkStyles = HashSet<Int>()
    val shockwaveColors = HashSet<Int>()
    val burstColors = HashSet<Int>()

    // What the interaction reducer projected from them.
    val projectedSkillPhases = HashSet<Pair<Int, Int>>()
    var projectedApex = 0
    var projectedReactionFlags = 0
    var projectedDirections = 0
    val projectedLinkStyles = HashSet<Int>()
    val projectedShockwaveColors = HashSet<Int>()

    // Render-model facts.
    var maxSkillsOwned = 0
    var refusals = 0
    var skillRefunded = 0f
    var skillPending = 0
    var skillActive = 0
    var apexUnlocked = false
    var apexUnlockedAt = -1f
    var apexReadySeen = false
    var apexActiveSeen = false
    var apexCooling = false
    var apexRefunded = 0f
    var apexStrikesSeen = 0
    val fieldSources = HashSet<CombatFieldSource>()
    var controlBits = 0
    var coreStates = 0
    var maxLevelSeen = 0
    var modelMaxLevel = 0
    var maxChargesSeen = 0

    // The HUD's presentation memory, fed every frame as the HUD feeds it.
    private val memory = HudPresentationMemory()
    private val boss = BossTarget()
    var skillAcquiredShown = false
    var apexUnlockShown = false
    var apexCastShown = false
    var apexRefundShown = false

    /** Models worth drawing (one per kind of fact), kept for the renderer checks. */
    val samples = LinkedHashMap<String, Pair<GameplayRenderModel, VisualFxProjection>>()

    private fun start(): GameplayState {
        // A seasoned profile (the full Core Matrix, Refit and Honing at their maximum) so the bot lives to the Apex.
        val profile = PlayerProfile(
            preferences = PlayerPreferences(language = AppLanguage.English),
            matrix = MatrixProgress(content.matrixNodes.map { it.maxRanks }),
        )
        val upgrades = PermanentUpgrades(
            characterLevels = CoreShape.entries.map { if (it == profile.loadout.coreShape) content.upgrades.maxLevel else 0 },
            weaponLevels = WeaponId.entries.map { if (it == profile.loadout.selectedWeapon) content.upgrades.maxLevel else 0 },
        )
        val started = (GameplayNucleus.decide(
            GameplayState.initial(RunId(1), content),
            GameplayNucleusPulse.StartRun,
            GameplayContext(start = GameplayStartContext.Ready(GameplayStartInputs(
                content,
                GameplayProfileSnapshot(profile.preferences, profile.economy, profile.loadout, profile.matrix, profile.collection,
                    profile.rebirthProgress, upgrades),
                seed = seed,
            ))),
        ) as GameplayDecision.Accepted).frame.nextState
        return started
    }

    val model: GameplayRenderModel get() = requireNotNull(GameplayNucleus.renderSnapshot(state).renderModel)

    private fun accept(pulse: GameplayInteractionPulse) = decide(GameplayNucleusPulse.Intent(pulse))

    /** One accepted frame: its cues go to the reducer; earned progress is applied at once (as the Profile would). */
    private fun decide(pulse: GameplayNucleusPulse) {
        val decision = GameplayNucleus.decide(state, pulse)
        if (decision !is GameplayDecision.Accepted) return
        state = decision.frame.nextState
        var progress = false
        for (output in decision.frame.outputs) {
            when (output) {
                is GameplayOutput.EmitVisualFx -> {
                    record(output.cues)
                    // Cue by cue, as apply walks a batch: a later flash of the same kind inside the merge distance
                    // joins an earlier one and its flags win, so each reaction cue's flags are read as it lands.
                    for (cue in output.cues) {
                        reducer.apply(listOf(cue))
                        if (cue is VisualFxCue.ReactionTriggered) {
                            for (burst in reducer.snapshot().elementBursts) projectedReactionFlags = projectedReactionFlags or burst.flags
                        }
                    }
                }
                is GameplayOutput.SendProfileCommand -> progress = true
                else -> Unit
            }
        }
        if (progress) decide(GameplayNucleusPulse.ProgressApplied(ProfileProgressApplied(ProfileRevision(++profileRevision))))
    }

    private var profileRevision = 0L

    fun play() {
        state = start()
        accept(GameplayInteractionPulse.ViewportChanged.fromValidated(WIDTH, HEIGHT, 1f))
        var time = 0f
        var nextRefusalProbe = 3f
        var apexReadySince = -1f
        var decisions = 0
        while (model.elapsed < seconds && decisions++ < MAX_DECISIONS) {
            val current = model
            time = current.elapsed
            when (current.phase) {
                GamePhase.CHOICE -> accept(GameplayInteractionPulse.ChoiceSelected.fromValidated(choose(current)))
                GamePhase.RUNNING -> {
                    steer(current, time)
                    val slots = current.skillBar.slots
                    for (slot in 0 until slots.size) {
                        if (slots[slot].skill >= 0 && slots[slot].ready) accept(GameplayInteractionPulse.SkillRequested.fromValidated(slot))
                    }
                    if (time >= nextRefusalProbe) {
                        // A request on a cooling slot is refused (the slot shows why).
                        nextRefusalProbe = time + 5f
                        for (slot in 0 until slots.size) {
                            if (slots[slot].skill >= 0 && !slots[slot].ready) accept(GameplayInteractionPulse.SkillRequested.fromValidated(slot))
                        }
                    }
                    // The Apex goes off with a boss on the field (its strikes), or after waiting long without one.
                    if (current.apex.unlocked && current.apex.ready) {
                        if (apexReadySince < 0f) apexReadySince = time
                        val bossPresent = current.enemies.any { !it.dead && it.type in BOSS_TYPES }
                        if (bossPresent || time - apexReadySince > APEX_PATIENCE) {
                            accept(GameplayInteractionPulse.ApexRequested)
                            apexReadySince = -1f
                        }
                    }
                    accept(GameplayInteractionPulse.FrameElapsed.fromValidated(FRAME))
                }
                else -> return
            }
            observe(model, reducer.snapshot())
        }
    }

    private fun choose(model: GameplayRenderModel): Int {
        val choices = model.choices
        // The run never replaces an active: the slot prompt keeps the loadout.
        if (model.choiceType == ChoiceType.ACTIVE_SLOT) return choices.indexOfFirst { it.activeSlot == null }.coerceAtLeast(0)
        // A focus card only narrows the offer; take a card that resolves it.
        var best = (0 until minOf(choices.size, 4)).firstOrNull { choices[it].rewardFocus == null } ?: 0
        var bestRank = Int.MAX_VALUE
        for (index in 0 until minOf(choices.size, 4)) {
            val choice = choices[index]
            // An active item card: a new active while a slot is free, or a rank-up.
            val skill = choice.skillId ?: continue
            if (choice.type != ChoiceType.ITEM || choice.replacesActive) continue
            val rank = DRAFT_PREFERENCE.indexOf(skill).takeIf { it >= 0 } ?: continue
            if (rank < bestRank) {
                bestRank = rank
                best = index
            }
        }
        return best
    }

    private var cursorAngle = 0f

    /**
     * Holds the singularity a fixed tether from the Core, turning it a little each frame toward the nearest
     * enemy (back toward the middle when the Core drifts off it): the Core chases enemies and the
     * singularity sweeps around it, never across it.
     */
    private fun steer(model: GameplayRenderModel, time: Float) {
        var desired = time * 0.7f
        var nearest = Float.MAX_VALUE
        for (enemy in model.enemies) {
            if (enemy.dead) continue
            val dx = enemy.x - model.coreX
            val dy = enemy.y - model.coreY
            val distance = dx * dx + dy * dy
            if (distance < nearest) {
                nearest = distance
                desired = atan2(dy, dx)
            }
        }
        val offsetX = model.coreX - model.cameraX
        val offsetY = model.coreY - model.cameraY
        val calmX = model.screenWidth * 0.5f * content.tempo.fatigue.edgeStrainStart - TETHER - 20f
        val calmY = model.screenHeight * 0.5f * content.tempo.fatigue.edgeStrainStart - TETHER - 20f
        if (abs(offsetX) > calmX || abs(offsetY) > calmY) desired = atan2(-offsetY, -offsetX)
        val turn = atan2(sin(desired - cursorAngle), cos(desired - cursorAngle))
        cursorAngle += turn.coerceIn(-MAX_TURN, MAX_TURN)
        accept(GameplayInteractionPulse.PointerMoved.fromValidated(
            (model.screenWidth * 0.5f + offsetX + cos(cursorAngle) * TETHER).coerceIn(0f, model.screenWidth),
            (model.screenHeight * 0.5f + offsetY + sin(cursorAngle) * TETHER).coerceIn(0f, model.screenHeight),
        ))
    }

    private fun record(cues: List<VisualFxCue>) {
        for (cue in cues) when (cue) {
            is VisualFxCue.SkillCast -> skillCasts += cue.skill to cue.phase
            is VisualFxCue.ApexActivated -> apexActivations++
            is VisualFxCue.ReactionTriggered -> {
                reactionFlags = reactionFlags or cue.flags
                if (!cue.direction.isNaN()) reactionDirections++
                val seen = reactionRadii[cue.reaction]
                reactionRadii[cue.reaction] = if (seen == null) cue.radius to cue.radius else minOf(seen.first, cue.radius) to maxOf(seen.second, cue.radius)
            }
            is VisualFxCue.WeaponArcAdded -> linkStyles += cue.style
            is VisualFxCue.ShockwaveAdded -> shockwaveColors += cue.colorIndex
            is VisualFxCue.Burst -> burstColors += cue.colorIndex
            else -> Unit
        }
    }

    private fun observe(model: GameplayRenderModel, fx: VisualFxProjection) {
        val time = model.elapsed
        memory.observe(model, time, boss.select(model))
        for (slot in 0 until 3) if (memory.skillAcquired(slot, time) < 1f) skillAcquiredShown = true
        if (memory.apexUnlock(time) < 1f) apexUnlockShown = true
        if (memory.apexCast(time) < 1f) apexCastShown = true
        if (memory.apexRefund(time) < 1f) apexRefundShown = true
        maxLevelSeen = maxOf(maxLevelSeen, model.level)
        modelMaxLevel = model.maxLevel
        for (burst in fx.skillBursts) projectedSkillPhases += burst.skill to burst.phase
        if (fx.apexBursts.isNotEmpty()) {
            projectedApex++
            samples.getOrPut("apex-burst") { model to fx }
        }
        for (burst in fx.elementBursts) {
            projectedReactionFlags = projectedReactionFlags or burst.flags
            if (!burst.direction.isNaN()) projectedDirections++
        }
        for (arc in fx.weaponArcs) projectedLinkStyles += arc.style
        for (wave in fx.shockwaves) projectedShockwaveColors += wave.colorIndex
        if (fx.skillBursts.isNotEmpty()) samples.getOrPut("skill-burst") { model to fx }
        // Drawn samples keep their fact inside the viewport: the renderer culls flashes and glyphs off screen.
        if (fx.elementBursts.count { it.reaction >= 0 && model.shows(it.x, it.y) } >= 3) samples.getOrPut("reactions") { model to fx }
        if (fx.weaponArcs.any { it.style != LinkStyleCode.WEAPON }) samples.getOrPut("styled-link") { model to fx }

        val slots = model.skillBar.slots
        var owned = 0
        for (slot in 0 until slots.size) {
            val value = slots[slot]
            if (value.skill < 0) continue
            owned++
            if (value.refusal != 0 && value.refusedAt >= 0f) refusals++
            skillRefunded = maxOf(skillRefunded, value.refunded)
            if (value.pending01 > 0f) skillPending++
            if (value.active01 > 0f) skillActive++
            maxChargesSeen = maxOf(maxChargesSeen, value.maxCharges)
        }
        if (owned > 0) samples.getOrPut("skill-bar") { model to fx }
        maxSkillsOwned = maxOf(maxSkillsOwned, owned)
        val apex = model.apex
        if (apex.unlocked && !apexUnlocked) apexUnlockedAt = model.elapsed
        if (apex.unlocked) apexUnlocked = true
        if (apex.unlocked && apex.ready) apexReadySeen = true
        if (apex.active01 > 0f) {
            apexActiveSeen = true
            samples.getOrPut("apex-active") { model to fx }
        }
        if (apex.unlocked && !apex.ready && apex.cooldown01 > 0f) apexCooling = true
        apexRefunded = maxOf(apexRefunded, apex.refunded)
        apexStrikesSeen = maxOf(apexStrikesSeen, apex.strikesLeft)
        for (field in model.combatFields) fieldSources += field.source
        if (model.combatFields.isNotEmpty()) samples.getOrPut("fields") { model to fx }
        var bits = 0
        var shownBits = 0
        for (enemy in model.enemies) {
            bits = bits or enemy.controlBits
            if (!enemy.dead && model.shows(enemy.x, enemy.y)) shownBits = shownBits or enemy.controlBits
        }
        if (shownBits != 0) samples.getOrPut("control-bits") { model to fx }
        if (bits and EnemyControlBits.APEX_STRIKE_TARGET != 0) samples.getOrPut("apex-strike-target") { model to fx }
        controlBits = controlBits or bits
        if (model.coreState !== CoreStateProjection.NEUTRAL) {
            coreStates++
            samples.getOrPut("core-state") { model to fx }
        }
    }

    /** True when the world point ([x], [y]) lies inside this frame's viewport. */
    private fun GameplayRenderModel.shows(x: Float, y: Float): Boolean =
        abs(x - cameraX) < screenWidth * 0.5f && abs(y - cameraY) < screenHeight * 0.5f

    fun report(): String = buildString {
        append("level=$maxLevelSeen/$modelMaxLevel skills=$maxSkillsOwned casts=${skillCasts.sortedBy { it.first }} ")
        append("projectedSkills=${projectedSkillPhases.size} delayed=${skillCasts.count { it.second == SkillCueCodes.PHASE_IMPACT }} ")
        append("apex=$apexActivations/$projectedApex unlocked=$apexUnlocked@$apexUnlockedAt ready=$apexReadySeen active=$apexActiveSeen cooling=$apexCooling ")
        append("apexRefunded=$apexRefunded strikes=$apexStrikesSeen refusals=$refusals skillRefunded=$skillRefunded pending=$skillPending ")
        append("skillActive=$skillActive reactionFlags=$reactionFlags/$projectedReactionFlags directions=$reactionDirections/$projectedDirections ")
        append("links=${linkStyles.sorted()}/${projectedLinkStyles.sorted()} waves=${shockwaveColors.sorted()}/${projectedShockwaveColors.sorted()} ")
        append("bursts=${burstColors.sorted()} fields=$fieldSources controlBits=${Integer.toBinaryString(controlBits)} coreStates=$coreStates ")
        append("samples=${samples.keys} ")
        append("radii=${reactionRadii.entries.sortedBy { it.key }.joinToString { "${it.key}:${it.value.first.toInt()}-${it.value.second.toInt()}" }}")
        val end = model
        append(" end=${end.phase} elapsed=${end.elapsed} hp=${end.hp} enemies=${end.enemies.size} data=${end.data}/${end.nextLevelData} kills=${end.kills}")
    }

    private companion object {
        const val WIDTH = 1_440f
        const val HEIGHT = 810f
        const val FRAME = 1f / 60f
        const val TETHER = 150f
        const val MAX_TURN = 0.12f
        const val MAX_DECISIONS = 80_000
        const val APEX_PATIENCE = 40f
        val BOSS_TYPES = setOf(EnemyType.HERALD, EnemyType.ARCHITECT)
    }
}
