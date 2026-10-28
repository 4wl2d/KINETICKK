// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.foundation.collections.mapToImmutableList
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.random.CloneableXorWowRandom
import kinetickk.ball.gameplay.nucleus.model.clamp
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.gameplay.nucleus.model.formatDamageNumber
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

internal object InteractionFxLimits {
    const val MAX_PARTICLES = 700
    const val MAX_MOTION_ECHOES = 36
    const val MAX_SHOCKWAVES = 48
    const val MAX_DAMAGE_NUMBERS = 140
    const val MAX_WEAPON_ARCS = 128

    /** Samples of the Core ribbon ring (presentation memory of the Core's path); the oldest is trimmed on overflow. */
    const val MAX_CORE_RIBBON_SAMPLES = 64

    /** Damage numbers pop, settle and drift away from the Core over 600 ms (`KkTime.Drift`). */
    const val DAMAGE_NUMBER_LIFE_SECONDS = 0.6f

    /** The first particles of every burst are large kill shards; the rest are small sparks. */
    const val SHARDS_PER_BURST = 4

    /** Particles at least this large are drawn as shards. */
    const val SHARD_MIN_SIZE = 4.5f

    /** Build toasts kept at once; a lower-priority toast never evicts a higher one. */
    const val MAX_BUILD_NOTIFICATIONS = 3

    /**
     * Toasts of one mergeable kind and title opened this recently (seconds) take the next lines
     * instead; the window runs from the toast's opening, so a chain of finds never keeps one open.
     */
    const val NOTICE_MERGE_SECONDS = 1.5f

    /** Name lines a merged toast holds; the next find opens a toast of its own. */
    const val NOTICE_MERGED_LINES = 3

    /** Concurrent Break and reaction flashes (the budget admits far more events than read as one). */
    const val MAX_ELEMENT_BURSTS = 24

    /** Flashes of the same kind closer than this (world units) merge into one heavier flash. */
    const val BURST_MERGE_DISTANCE = 80f

    /** Events a merged flash can carry; each adds to its reach and brightness. */
    const val MAX_BURST_WEIGHT = 4

    /**
     * Live flashes of one reaction: a fifth one outside the merge distance replaces that reaction's
     * oldest, so one chain-happy reaction never takes every slot.
     */
    const val MAX_BURSTS_PER_REACTION = 4

    /** Concurrent skill cast and impact looks (three casts and three impacts a step, by construction). */
    const val MAX_SKILL_BURSTS = 8

    /** Concurrent Apex activation looks. */
    const val MAX_APEX_BURSTS = 2

    const val BREAK_BURST_SECONDS = 0.5f

    /** The longest reaction look ([ReactionFxSpecs]); each reaction lives its own spec's life. */
    val REACTION_BURST_SECONDS: Float = ReactionFxSpecs.maxLife

    /** The once-per-profile reaction name stamp (`cond`, −6°, 900 ms). */
    const val REACTION_STAMP_SECONDS = 0.9f
    const val MAX_REACTION_STAMPS = 3
}

/** Toast titles the nucleus emits for discoveries (see [BuildNoticeKind]); the feed localizes them. */
internal object BuildNoticeTitles {
    const val REACTION_DISCOVERED = "REACTION DISCOVERED"
    const val PAIRING_DISCOVERED = "PAIRING DISCOVERED"

    /** A bound relic newly disables an owned item (warning tone). */
    const val INCOMPATIBILITY_DISCOVERED = "INCOMPATIBILITY DISCOVERED"

    /** Interaction's own note for the first Break of an element in a run. */
    const val BREAK = "BREAK"

    fun kind(title: String): BuildNoticeKind = when (title) {
        REACTION_DISCOVERED, PAIRING_DISCOVERED -> BuildNoticeKind.DISCOVERY
        INCOMPATIBILITY_DISCOVERED -> BuildNoticeKind.WARNING
        BREAK -> BuildNoticeKind.BREAK
        else -> BuildNoticeKind.CHANGE
    }
}

/**
 * Visual reach of each element's Break flash (world units), by status element ordinal: Shatter's
 * flash spans its 160-unit shockwave, Unmake stays on its target.
 */
internal val BreakBurstRadii = floatArrayOf(90f, 64f, 120f, 150f, 72f, 160f, 80f)

/**
 * Interaction-owned reducer for drop-eligible visual state.
 *
 * Its RNG and mutable collections are intentionally outside simulation state:
 * visual evolution cannot influence a domain decision or a persisted value.
 */
class InteractionFxReducer(seed: Int) {
    private var random = CloneableXorWowRandom(seed xor FX_SEED_MASK)
    private var motionEchoClock = 0f
    // Last sampled Core position; damage numbers drift away from it. Presentation only.
    private var coreX = Float.NaN
    private var coreY = Float.NaN
    private val particles = mutableListOf<Particle>()
    private val motionEchoes = mutableListOf<MotionEcho>()
    private val shockwaves = mutableListOf<Shockwave>()
    private val damageNumbers = mutableListOf<DamageNumber>()
    private val weaponArcs = mutableListOf<WeaponArc>()
    private val buildNotifications = mutableListOf<BuildNotificationProjection>()
    private val elementBursts = mutableListOf<ElementBurst>()
    private val reactionStamps = mutableListOf<ReactionStamp>()
    private val skillBursts = mutableListOf<SkillBurst>()
    private val apexBursts = mutableListOf<ApexBurst>()
    private var runReactionMask = 0
    private var runDiscoveryMask = 0
    private var runBreakMask = 0
    private var projectionDirty = false
    private var cachedProjection = VisualFxProjection.EMPTY
    private var particlesDirty = false
    private var motionEchoesDirty = false
    private var shockwavesDirty = false
    private var damageNumbersDirty = false
    private var weaponArcsDirty = false
    private var elementBurstsDirty = false
    private var reactionStampsDirty = false
    private var skillBurstsDirty = false
    private var apexBurstsDirty = false

    // The Core ribbon ring: run-local positions at the start of past fixed steps, their age in simulation seconds and
    // their CoreRibbonProjection flags. Presentation memory only, like every other field here.
    private val ribbonPositions = FloatArray(InteractionFxLimits.MAX_CORE_RIBBON_SAMPLES * 2)
    private val ribbonAges = FloatArray(InteractionFxLimits.MAX_CORE_RIBBON_SAMPLES)
    private val ribbonFlags = IntArray(InteractionFxLimits.MAX_CORE_RIBBON_SAMPLES)
    private var ribbonHead = -1 // ring index of the newest sample
    private var ribbonCount = 0
    // The last MotionSample's position and speed (every step, sampled or not): a step's jump is measured from it.
    private var ribbonStepX = Float.NaN
    private var ribbonStepY = Float.NaN
    private var ribbonStepSpeed = 0f
    private var coreRibbonDirty = false
    // Newest-first staging for a published ribbon, which copies only the live prefix.
    private val ribbonStagedPositions = FloatArray(InteractionFxLimits.MAX_CORE_RIBBON_SAMPLES * 2)
    private val ribbonStagedAges = FloatArray(InteractionFxLimits.MAX_CORE_RIBBON_SAMPLES)
    private val ribbonStagedFlags = IntArray(InteractionFxLimits.MAX_CORE_RIBBON_SAMPLES)

    fun apply(cues: Iterable<VisualFxCue>) {
        if (cues is List<*>) {
            @Suppress("UNCHECKED_CAST")
            val indexedCues = cues as List<VisualFxCue>
            for (index in indexedCues.indices) {
                if (applyCue(indexedCues[index])) projectionDirty = true
            }
        } else {
            for (cue in cues) {
                if (applyCue(cue)) projectionDirty = true
            }
        }
    }

    fun snapshot(): VisualFxProjection {
        if (!projectionDirty) return cachedProjection
        cachedProjection = VisualFxProjection(
            buildNotifications = buildNotifications.mapToImmutableList { it },
            particles = if (particlesDirty) particles.mapToImmutableList { value ->
                ParticleProjection(
                    value.x,
                    value.y,
                    value.vx,
                    value.vy,
                    value.life,
                    value.maxLife,
                    value.colorIndex,
                    value.size,
                )
            } else cachedProjection.particles,
            motionEchoes = if (motionEchoesDirty) motionEchoes.mapToImmutableList { value ->
                MotionEchoProjection(value.x, value.y, value.life, value.maxLife, value.intensity)
            } else cachedProjection.motionEchoes,
            shockwaves = if (shockwavesDirty) shockwaves.mapToImmutableList { value ->
                ShockwaveProjection(
                    value.x,
                    value.y,
                    value.life,
                    value.maxLife,
                    value.maxRadius,
                    value.colorIndex,
                )
            } else cachedProjection.shockwaves,
            damageNumbers = if (damageNumbersDirty) damageNumbers.mapToImmutableList { value ->
                DamageNumberProjection(
                    x = value.x,
                    y = value.y,
                    amount = value.amount,
                    critical = value.critical,
                    life = value.life,
                    compactAmount = value.compactAmount,
                    fullAmount = value.fullAmount,
                    russianCompactAmount = value.russianCompactAmount,
                    driftX = value.driftX,
                    driftY = value.driftY,
                    tone = value.tone,
                )
            } else cachedProjection.damageNumbers,
            weaponArcs = if (weaponArcsDirty) weaponArcs.mapToImmutableList { value ->
                WeaponArcProjection(value.fromX, value.fromY, value.toX, value.toY, value.life, value.style, value.maxLife)
            } else cachedProjection.weaponArcs,
            elementBursts = if (elementBurstsDirty) elementBursts.mapToImmutableList { value ->
                ElementBurstProjection(value.x, value.y, value.primary, value.secondary, value.reaction, value.radius, value.life,
                    value.maxLife, value.weight, value.direction, value.flags, value.seed)
            } else cachedProjection.elementBursts,
            skillBursts = if (skillBurstsDirty) skillBursts.mapToImmutableList { value ->
                SkillBurstProjection(value.skill, value.phase, value.x, value.y, value.dirX, value.dirY, value.radius, value.rank,
                    value.flags, value.seed, value.life, value.maxLife)
            } else cachedProjection.skillBursts,
            apexBursts = if (apexBurstsDirty) apexBursts.mapToImmutableList { value ->
                ApexBurstProjection(value.shape, value.x, value.y, value.seed, value.life, value.maxLife)
            } else cachedProjection.apexBursts,
            reactionStamps = if (reactionStampsDirty) reactionStamps.mapToImmutableList { value ->
                ReactionStampProjection(value.x, value.y, value.reaction, value.life, value.maxLife)
            } else cachedProjection.reactionStamps,
            runReactionMask = runReactionMask,
            runDiscoveryMask = runDiscoveryMask,
            coreRibbon = if (coreRibbonDirty) buildCoreRibbon() else cachedProjection.coreRibbon,
        )
        projectionDirty = false
        particlesDirty = false
        motionEchoesDirty = false
        shockwavesDirty = false
        damageNumbersDirty = false
        weaponArcsDirty = false
        elementBurstsDirty = false
        reactionStampsDirty = false
        skillBurstsDirty = false
        apexBurstsDirty = false
        coreRibbonDirty = false
        return cachedProjection
    }

    /** Returns whether the externally visible projection changed. */
    private fun applyCue(cue: VisualFxCue): Boolean =
        when (cue) {
            is VisualFxCue.BuildChanged -> addNotice(cue.title, cue.details, BuildNoticeTitles.kind(cue.title)) != NOTICE_DROPPED
            VisualFxCue.ClearAll -> clearAll()
            VisualFxCue.ClearWeaponArcs -> if (weaponArcs.isEmpty()) {
                false
            } else {
                weaponArcs.clear()
                weaponArcsDirty = true
                true
            }
            is VisualFxCue.MotionSample -> {
                val echo = sampleMotionEcho(cue)
                val ribbon = sampleCoreRibbon(cue)
                echo || ribbon
            }
            is VisualFxCue.EffectsAdvanced -> advanceEffects(cue.deltaSeconds)
            is VisualFxCue.WeaponArcsAdvanced -> advanceWeaponArcs(cue.deltaSeconds)
            is VisualFxCue.Burst -> burst(cue)
            is VisualFxCue.DirectionalBurst -> directionalBurst(cue)
            is VisualFxCue.ShockwaveAdded -> {
                shockwaves += Shockwave(cue.x, cue.y, cue.life, cue.life, cue.maxRadius, cue.colorIndex)
                trimFront(shockwaves, InteractionFxLimits.MAX_SHOCKWAVES)
                shockwavesDirty = true
                true
            }
            is VisualFxCue.DamageNumberAdded -> {
                if (damageNumbers.size < InteractionFxLimits.MAX_DAMAGE_NUMBERS) {
                    val dx = if (coreX.isNaN()) 0f else cue.x - coreX
                    val dy = if (coreY.isNaN()) -1f else cue.y - coreY
                    val length = kotlin.math.sqrt(dx * dx + dy * dy)
                    damageNumbers += if (length > 0.001f) {
                        DamageNumber(cue.x, cue.y, cue.amount, cue.critical, driftX = dx / length, driftY = dy / length, tone = cue.tone)
                    } else {
                        DamageNumber(cue.x, cue.y, cue.amount, cue.critical, tone = cue.tone)
                    }
                    damageNumbersDirty = true
                    true
                } else {
                    false
                }
            }
            is VisualFxCue.StatusBroken -> statusBroken(cue)
            is VisualFxCue.ReactionTriggered -> reactionTriggered(cue)
            is VisualFxCue.WeaponArcAdded -> {
                // A styled link stays up for its look ([LinkLooks]); the weapon look keeps the cue's life.
                val life = if (cue.style == 0) cue.life else kotlin.math.max(cue.life, LinkLooks[cue.style].life)
                weaponArcs += WeaponArc(cue.fromX, cue.fromY, cue.toX, cue.toY, life, cue.style, life)
                trimFront(weaponArcs, InteractionFxLimits.MAX_WEAPON_ARCS)
                weaponArcsDirty = true
                true
            }
            is VisualFxCue.WorldRebased -> {
                coreX -= cue.shiftX
                coreY -= cue.shiftY
                val changed = particles.isNotEmpty() || damageNumbers.isNotEmpty()
                for (index in particles.indices) {
                    val value = particles[index]
                    value.x -= cue.shiftX
                    value.y -= cue.shiftY
                }
                for (index in damageNumbers.indices) {
                    val value = damageNumbers[index]
                    value.x -= cue.shiftX
                    value.y -= cue.shiftY
                }
                // Break and reaction flashes and the reaction name stamps mark the enemy that set them
                // off, so they move with the world like damage numbers (shockwaves keep the baseline
                // policy and end in place).
                for (index in elementBursts.indices) {
                    val value = elementBursts[index]
                    value.x -= cue.shiftX
                    value.y -= cue.shiftY
                }
                for (index in reactionStamps.indices) {
                    val value = reactionStamps[index]
                    value.x -= cue.shiftX
                    value.y -= cue.shiftY
                }
                // Skill and Apex looks mark where the cast landed in the world.
                for (index in skillBursts.indices) {
                    val value = skillBursts[index]
                    value.x -= cue.shiftX
                    value.y -= cue.shiftY
                }
                for (index in apexBursts.indices) {
                    val value = apexBursts[index]
                    value.x -= cue.shiftX
                    value.y -= cue.shiftY
                }
                if (particles.isNotEmpty()) particlesDirty = true
                if (damageNumbers.isNotEmpty()) damageNumbersDirty = true
                if (elementBursts.isNotEmpty()) elementBurstsDirty = true
                if (reactionStamps.isNotEmpty()) reactionStampsDirty = true
                if (skillBursts.isNotEmpty()) skillBurstsDirty = true
                if (apexBursts.isNotEmpty()) apexBurstsDirty = true
                val ribbonMoved = rebaseCoreRibbon(cue.shiftX, cue.shiftY)
                changed || elementBursts.isNotEmpty() || reactionStamps.isNotEmpty() || skillBursts.isNotEmpty() || apexBursts.isNotEmpty() ||
                    ribbonMoved
            }
            is VisualFxCue.VisualCuesDropped -> false
            is VisualFxCue.SkillCast -> skillCast(cue)
            is VisualFxCue.ApexActivated -> {
                apexBursts += ApexBurst(cue.shape.ordinal, cue.x, cue.y, random.nextInt(), ApexFxSpec.activation.life,
                    ApexFxSpec.activation.life)
                trimFront(apexBursts, InteractionFxLimits.MAX_APEX_BURSTS)
                apexBurstsDirty = true
                true
            }
        }

    private fun clearAll(): Boolean {
        val changed = buildNotifications.isNotEmpty() || particles.isNotEmpty() ||
            motionEchoes.isNotEmpty() ||
            shockwaves.isNotEmpty() ||
            damageNumbers.isNotEmpty() ||
            weaponArcs.isNotEmpty() ||
            elementBursts.isNotEmpty() ||
            reactionStamps.isNotEmpty() ||
            skillBursts.isNotEmpty() ||
            apexBursts.isNotEmpty() ||
            ribbonCount > 0 ||
            runReactionMask != 0 || runDiscoveryMask != 0
        elementBursts.clear()
        reactionStamps.clear()
        skillBursts.clear()
        apexBursts.clear()
        runReactionMask = 0
        runDiscoveryMask = 0
        runBreakMask = 0
        buildNotifications.clear()
        particles.clear()
        motionEchoes.clear()
        shockwaves.clear()
        damageNumbers.clear()
        weaponArcs.clear()
        motionEchoClock = 0f
        coreX = Float.NaN
        coreY = Float.NaN
        ribbonCount = 0
        ribbonHead = -1
        ribbonStepX = Float.NaN
        ribbonStepY = Float.NaN
        ribbonStepSpeed = 0f
        if (changed) {
            coreRibbonDirty = true
            particlesDirty = true
            motionEchoesDirty = true
            shockwavesDirty = true
            damageNumbersDirty = true
            weaponArcsDirty = true
            elementBurstsDirty = true
            reactionStampsDirty = true
            skillBurstsDirty = true
            apexBurstsDirty = true
        }
        return changed
    }

    /**
     * Adds a build toast by priority and returns whether its lines show ([NOTICE_MERGED],
     * [NOTICE_OPENED]) or the feed dropped it ([NOTICE_DROPPED]). Mergeable kinds join a toast of
     * the same title opened less than [InteractionFxLimits.NOTICE_MERGE_SECONDS] ago while it has
     * a free line; otherwise the toast goes after every toast of its priority or lower, and the
     * oldest lowest one leaves when the feed is over its bound (a new toast below a full feed of
     * higher ones is dropped at once).
     */
    private fun addNotice(title: String, details: List<String>, kind: BuildNoticeKind): Int {
        if (kind.merges) {
            for (index in buildNotifications.indices.reversed()) {
                val notice = buildNotifications[index]
                if (notice.kind != kind || notice.title != title) continue
                if (notice.age >= InteractionFxLimits.NOTICE_MERGE_SECONDS) break
                var fresh = 0
                for (line in details) if (line !in notice.details) fresh++
                if (fresh == 0) return NOTICE_MERGED
                if (notice.details.size + fresh > InteractionFxLimits.NOTICE_MERGED_LINES) break
                val merged = ArrayList<String>(notice.details.size + fresh)
                merged += notice.details
                for (line in details) if (line !in merged) merged += line
                buildNotifications[index] = notice.copy(details = merged.toImmutableList(), life = notice.maxLife)
                return NOTICE_MERGED
            }
        }
        val notice = BuildNotificationProjection(title, details.toImmutableList(), kind.lifeSeconds, kind)
        var at = buildNotifications.size
        while (at > 0 && buildNotifications[at - 1].kind.priority > kind.priority) at--
        buildNotifications.add(at, notice)
        trimFront(buildNotifications, InteractionFxLimits.MAX_BUILD_NOTIFICATIONS)
        for (index in buildNotifications.indices) if (buildNotifications[index] === notice) return NOTICE_OPENED
        return NOTICE_DROPPED
    }

    private fun statusBroken(cue: VisualFxCue.StatusBroken): Boolean {
        val element = cue.element
        if (element !in BreakBurstRadii.indices) return false
        addBurst(cue.x, cue.y, element, -1, -1, BreakBurstRadii[element], InteractionFxLimits.BREAK_BURST_SECONDS, Float.NaN, 0)
        val bit = 1 shl element
        // The run remembers an element's first Break only once its note shows: a note the full feed
        // drops waits for the element's next Break.
        if (runBreakMask and bit == 0 &&
            addNotice(BuildNoticeTitles.BREAK, listOf("+ " + StatusElement.entries[element].breakLabel), BuildNoticeKind.BREAK) != NOTICE_DROPPED
        ) {
            runBreakMask = runBreakMask or bit
        }
        return true
    }

    private fun reactionTriggered(cue: VisualFxCue.ReactionTriggered): Boolean {
        val reaction = ReactionId.entries.getOrNull(cue.reaction) ?: return false
        val spec = ReactionFxSpecs[reaction]
        addBurst(cue.x, cue.y, reaction.first.ordinal, reaction.second.ordinal, cue.reaction, spec.reachOf(cue.radius), spec.life,
            cue.direction, cue.flags)
        val bit = 1 shl cue.reaction
        runReactionMask = runReactionMask or bit
        if (cue.firstDiscovery) {
            runDiscoveryMask = runDiscoveryMask or bit
            reactionStamps += ReactionStamp(cue.x, cue.y, cue.reaction, InteractionFxLimits.REACTION_STAMP_SECONDS,
                InteractionFxLimits.REACTION_STAMP_SECONDS)
            trimFront(reactionStamps, InteractionFxLimits.MAX_REACTION_STAMPS)
            reactionStampsDirty = true
        }
        return true
    }

    /**
     * A flash of one kind joins a live flash of the same kind within the merge distance (the newest
     * direction and flags win); otherwise a reaction already showing
     * [InteractionFxLimits.MAX_BURSTS_PER_REACTION] flashes gives up its oldest first.
     */
    private fun addBurst(
        x: Float,
        y: Float,
        primary: Int,
        secondary: Int,
        reaction: Int,
        radius: Float,
        life: Float,
        direction: Float,
        flags: Int,
    ) {
        val reach = InteractionFxLimits.BURST_MERGE_DISTANCE
        var sameReaction = 0
        var oldestOfReaction = -1
        for (index in elementBursts.indices) {
            val burst = elementBursts[index]
            if (burst.primary != primary || burst.secondary != secondary || burst.reaction != reaction) continue
            val dx = burst.x - x
            val dy = burst.y - y
            if (dx * dx + dy * dy > reach * reach) {
                if (oldestOfReaction < 0) oldestOfReaction = index
                sameReaction++
                continue
            }
            burst.weight = (burst.weight + 1).coerceAtMost(InteractionFxLimits.MAX_BURST_WEIGHT)
            burst.life = burst.maxLife
            burst.radius = kotlin.math.max(burst.radius, radius)
            burst.x = (burst.x + x) * 0.5f
            burst.y = (burst.y + y) * 0.5f
            burst.direction = direction
            burst.flags = flags
            elementBurstsDirty = true
            return
        }
        if (reaction >= 0 && sameReaction >= InteractionFxLimits.MAX_BURSTS_PER_REACTION) elementBursts.removeAt(oldestOfReaction)
        // Only reaction looks hash a seed (Break figures are fixed), so Breaks leave the particle stream as it was.
        val seed = if (reaction >= 0) random.nextInt() else 0
        elementBursts += ElementBurst(x, y, primary, secondary, reaction, radius, life, life, 1, direction, flags, seed)
        trimFront(elementBursts, InteractionFxLimits.MAX_ELEMENT_BURSTS)
        elementBurstsDirty = true
    }

    /** A skill look with a spec for its phase; the oldest gives way past [InteractionFxLimits.MAX_SKILL_BURSTS]. */
    private fun skillCast(cue: VisualFxCue.SkillCast): Boolean {
        val spec = SkillFxSpecs.of(cue.skill, cue.phase) ?: return false
        skillBursts += SkillBurst(cue.skill.ordinal, cue.phase, cue.x, cue.y, cue.dirX, cue.dirY, spec.reachOf(cue.radius),
            cue.rank.coerceIn(1, 3), cue.flags, random.nextInt(), spec.life, spec.life)
        trimFront(skillBursts, InteractionFxLimits.MAX_SKILL_BURSTS)
        skillBurstsDirty = true
        return true
    }

    private fun sampleMotionEcho(cue: VisualFxCue.MotionSample): Boolean {
        coreX = cue.previousCoreX
        coreY = cue.previousCoreY
        val intensity = clamp((cue.speed - 260f) / 1_500f, 0f, 1f)
        if (intensity <= 0f && cue.dashPhaseTime <= 0f) {
            motionEchoClock = 0f
            return false
        }
        motionEchoClock -= cue.deltaSeconds
        if (motionEchoClock > 0f) return false
        val dashIntensity = if (cue.dashPhaseTime > 0f) 1f else intensity
        motionEchoClock = if (cue.dashPhaseTime > 0f) 0.018f else 0.075f - intensity * 0.04f
        val maxLife = 0.2f + dashIntensity * 0.16f
        motionEchoes += MotionEcho(
            cue.previousCoreX,
            cue.previousCoreY,
            maxLife,
            maxLife,
            dashIntensity,
        )
        trimFront(motionEchoes, InteractionFxLimits.MAX_MOTION_ECHOES)
        motionEchoesDirty = true
        return true
    }

    /**
     * Records the Core's position at the start of the step on the ribbon once it lies a spacing ([ribbonSpacing]) from
     * the newest sample; a Dash start always samples. A Core at or below [RIBBON_MIN_SPEED] outside a Dash stores
     * nothing. A jump beyond the step's reach (a blink) starts a new chain, so no segment crosses it. Returns whether
     * the ribbon changed: tracking the step alone is not a visible change.
     */
    private fun sampleCoreRibbon(cue: VisualFxCue.MotionSample): Boolean {
        val x = cue.previousCoreX
        val y = cue.previousCoreY
        if (!x.isFinite() || !y.isFinite()) return false
        val lastX = ribbonStepX
        val lastY = ribbonStepY
        val lastSpeed = ribbonStepSpeed
        ribbonStepX = x
        ribbonStepY = y
        ribbonStepSpeed = cue.speed
        val dash = cue.dashPhaseTime > 0f
        if (cue.speed <= RIBBON_MIN_SPEED && !dash) return false
        val dashFlag = if (dash) CoreRibbonProjection.DASH else 0
        val reach = max(lastSpeed, cue.speed) * cue.deltaSeconds * RIBBON_TELEPORT_FACTOR + RIBBON_TELEPORT_SLACK
        val jumped = !lastX.isNaN() && distanceSquared(lastX, lastY, x, y) > reach * reach
        if (ribbonCount == 0 || jumped) {
            appendRibbon(x, y, CoreRibbonProjection.START or dashFlag)
            return true
        }
        val dashStarts = dash && (ribbonFlags[ribbonHead] and CoreRibbonProjection.DASH) == 0
        val spacing = ribbonSpacing(cue.speed)
        val fromNewest = distanceSquared(ribbonPositions[ribbonHead * 2], ribbonPositions[ribbonHead * 2 + 1], x, y)
        if (!dashStarts && fromNewest < spacing * spacing) return false
        appendRibbon(x, y, dashFlag)
        return true
    }

    /** Writes the newest sample at age 0; once the ring is full it takes the oldest sample's slot. */
    private fun appendRibbon(x: Float, y: Float, flags: Int) {
        val index = (ribbonHead + 1) % InteractionFxLimits.MAX_CORE_RIBBON_SAMPLES
        ribbonHead = index
        ribbonPositions[index * 2] = x
        ribbonPositions[index * 2 + 1] = y
        ribbonAges[index] = 0f
        ribbonFlags[index] = flags
        if (ribbonCount < InteractionFxLimits.MAX_CORE_RIBBON_SAMPLES) ribbonCount++
        coreRibbonDirty = true
    }

    /** Ring index of the live sample [newer] places behind the newest (0 is the newest). */
    private fun ribbonIndex(newer: Int): Int =
        (ribbonHead - newer + InteractionFxLimits.MAX_CORE_RIBBON_SAMPLES) % InteractionFxLimits.MAX_CORE_RIBBON_SAMPLES

    /** Ages the live samples in simulation time and drops the oldest past [RIBBON_MAX_AGE]; a pause freezes the ribbon. */
    private fun ageCoreRibbon(delta: Float) {
        if (ribbonCount == 0) return
        for (newer in 0 until ribbonCount) ribbonAges[ribbonIndex(newer)] += delta
        while (ribbonCount > 0 && ribbonAges[ribbonIndex(ribbonCount - 1)] > RIBBON_MAX_AGE) ribbonCount--
        coreRibbonDirty = true
    }

    /**
     * The ribbon and the tracked step move with the world, so a rebase draws no long segment and breaks no chain.
     * Returns whether a live sample moved.
     */
    private fun rebaseCoreRibbon(shiftX: Float, shiftY: Float): Boolean {
        ribbonStepX -= shiftX
        ribbonStepY -= shiftY
        if (ribbonCount == 0) return false
        for (newer in 0 until ribbonCount) {
            val index = ribbonIndex(newer)
            ribbonPositions[index * 2] -= shiftX
            ribbonPositions[index * 2 + 1] -= shiftY
        }
        coreRibbonDirty = true
        return true
    }

    /** Publishes the live samples newest first; the projection copies them out of the staging arrays. */
    private fun buildCoreRibbon(): CoreRibbonProjection {
        for (newer in 0 until ribbonCount) {
            val index = ribbonIndex(newer)
            ribbonStagedPositions[newer * 2] = ribbonPositions[index * 2]
            ribbonStagedPositions[newer * 2 + 1] = ribbonPositions[index * 2 + 1]
            ribbonStagedAges[newer] = ribbonAges[index]
            ribbonStagedFlags[newer] = ribbonFlags[index]
        }
        return CoreRibbonProjection.of(ribbonStagedPositions, ribbonStagedAges, ribbonStagedFlags, ribbonCount)
    }

    private fun distanceSquared(fromX: Float, fromY: Float, toX: Float, toY: Float): Float {
        val dx = toX - fromX
        val dy = toY - fromY
        return dx * dx + dy * dy
    }

    private fun advanceEffects(delta: Float): Boolean {
        if (
            buildNotifications.isEmpty() &&
            particles.isEmpty() &&
            motionEchoes.isEmpty() &&
            shockwaves.isEmpty() &&
            damageNumbers.isEmpty() &&
            elementBursts.isEmpty() &&
            reactionStamps.isEmpty() &&
            skillBursts.isEmpty() &&
            apexBursts.isEmpty() &&
            ribbonCount == 0
        ) return false
        ageCoreRibbon(delta)
        for (index in buildNotifications.indices) {
            val value = buildNotifications[index]
            buildNotifications[index] = value.copy(life = value.life - delta, age = value.age + delta)
        }
        removeExpired(buildNotifications) { it.life > 0f }
        val particleDamping = exp(-2.2f * delta)
        for (index in particles.indices) {
            val value = particles[index]
            value.x += value.vx * delta
            value.y += value.vy * delta
            value.vx *= particleDamping
            value.vy *= particleDamping
            value.life -= delta
        }
        removeExpired(particles) { value -> !(value.life <= 0f) }
        for (index in motionEchoes.indices) motionEchoes[index].life -= delta
        removeExpired(motionEchoes) { value -> !(value.life <= 0f) }
        for (index in shockwaves.indices) shockwaves[index].life -= delta
        removeExpired(shockwaves) { value -> !(value.life <= 0f) }
        // Numbers keep their anchor; the renderer eases the drift from life (see DamageNumberProjection).
        for (index in damageNumbers.indices) damageNumbers[index].life -= delta
        removeExpired(damageNumbers) { value -> !(value.life <= 0f) }
        if (particles.isNotEmpty() || cachedProjection.particles.isNotEmpty()) particlesDirty = true
        if (motionEchoes.isNotEmpty() || cachedProjection.motionEchoes.isNotEmpty()) motionEchoesDirty = true
        if (shockwaves.isNotEmpty() || cachedProjection.shockwaves.isNotEmpty()) shockwavesDirty = true
        if (damageNumbers.isNotEmpty() || cachedProjection.damageNumbers.isNotEmpty()) damageNumbersDirty = true
        for (index in elementBursts.indices) elementBursts[index].life -= delta
        removeExpired(elementBursts) { value -> !(value.life <= 0f) }
        for (index in reactionStamps.indices) reactionStamps[index].life -= delta
        removeExpired(reactionStamps) { value -> !(value.life <= 0f) }
        if (elementBursts.isNotEmpty() || cachedProjection.elementBursts.isNotEmpty()) elementBurstsDirty = true
        if (reactionStamps.isNotEmpty() || cachedProjection.reactionStamps.isNotEmpty()) reactionStampsDirty = true
        for (index in skillBursts.indices) skillBursts[index].life -= delta
        removeExpired(skillBursts) { value -> !(value.life <= 0f) }
        for (index in apexBursts.indices) apexBursts[index].life -= delta
        removeExpired(apexBursts) { value -> !(value.life <= 0f) }
        if (skillBursts.isNotEmpty() || cachedProjection.skillBursts.isNotEmpty()) skillBurstsDirty = true
        if (apexBursts.isNotEmpty() || cachedProjection.apexBursts.isNotEmpty()) apexBurstsDirty = true
        return true
    }

    private fun advanceWeaponArcs(delta: Float): Boolean {
        if (weaponArcs.isEmpty()) return false
        for (index in weaponArcs.indices) weaponArcs[index].life -= delta
        removeExpired(weaponArcs) { value -> !(value.life <= 0f) }
        weaponArcsDirty = true
        return true
    }

    private fun burst(cue: VisualFxCue.Burst): Boolean {
        val sizeBefore = particles.size
        repeat(particleCount(cue.requestedCount, cue.density)) { index ->
            if (particles.size >= InteractionFxLimits.MAX_PARTICLES) return@repeat
            val angle = random.nextFloat() * TAU
            val speed = 35f + random.nextFloat() * 185f
            val life = 0.25f + random.nextFloat() * 0.55f
            particles += Particle(
                cue.x,
                cue.y,
                cos(angle) * speed,
                sin(angle) * speed,
                life,
                life,
                cue.colorIndex,
                particleSize(index, random.nextFloat()),
            )
        }
        return (particles.size != sizeBefore).also { changed ->
            if (changed) particlesDirty = true
        }
    }

    private fun directionalBurst(cue: VisualFxCue.DirectionalBurst): Boolean {
        val sizeBefore = particles.size
        val baseAngle = atan2(cue.directionY, cue.directionX)
        repeat(particleCount(cue.requestedCount, cue.density)) { index ->
            if (particles.size >= InteractionFxLimits.MAX_PARTICLES) return@repeat
            val angle = baseAngle + (random.nextFloat() - 0.5f) * 1.15f
            val speed = 90f + random.nextFloat() * 310f
            val life = 0.22f + random.nextFloat() * 0.42f
            particles += Particle(
                cue.x,
                cue.y,
                cos(angle) * speed,
                sin(angle) * speed,
                life,
                life,
                cue.colorIndex,
                particleSize(index, random.nextFloat()),
            )
        }
        return (particles.size != sizeBefore).also { changed ->
            if (changed) particlesDirty = true
        }
    }

    /** The first [InteractionFxLimits.SHARDS_PER_BURST] particles are shards, the rest small sparks. */
    private fun particleSize(index: Int, roll: Float): Float =
        if (index < InteractionFxLimits.SHARDS_PER_BURST) InteractionFxLimits.SHARD_MIN_SIZE + roll * 3.5f else 1.2f + roll * 1.8f

    private fun particleCount(requestedCount: Int, density: ParticleDensity): Int {
        val multiplier = when (density) {
            ParticleDensity.LOW -> 0.45f
            ParticleDensity.NORMAL -> 1f
            ParticleDensity.HIGH -> 1.4f
        }
        return (requestedCount * multiplier).toInt().coerceAtLeast(1)
    }

    private fun <Element> trimFront(values: MutableList<Element>, maximum: Int) {
        while (values.size > maximum) values.removeAt(0)
    }

    /** Stable in-place compaction avoids iterator allocation and repeated ArrayList shifts. */
    private inline fun <Element> removeExpired(
        values: MutableList<Element>,
        isAlive: (Element) -> Boolean,
    ) {
        var retainedCount = 0
        for (index in values.indices) {
            val value = values[index]
            if (isAlive(value)) {
                if (retainedCount != index) values[retainedCount] = value
                retainedCount++
            }
        }
        var index = values.lastIndex
        while (index >= retainedCount) {
            values.removeAt(index)
            index--
        }
    }

    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float,
        val maxLife: Float,
        val colorIndex: Int,
        val size: Float,
    )

    private data class MotionEcho(
        val x: Float,
        val y: Float,
        var life: Float,
        val maxLife: Float,
        val intensity: Float,
    )

    private data class Shockwave(
        val x: Float,
        val y: Float,
        var life: Float,
        val maxLife: Float,
        val maxRadius: Float,
        val colorIndex: Int,
    )

    private data class DamageNumber(
        var x: Float,
        var y: Float,
        val amount: Long,
        val critical: Boolean,
        var life: Float = InteractionFxLimits.DAMAGE_NUMBER_LIFE_SECONDS,
        val compactAmount: String = formatDamageNumber(amount, DamageNumberFormat.COMPACT),
        val fullAmount: String = formatDamageNumber(amount, DamageNumberFormat.FULL),
        val russianCompactAmount: String = compactAmount.russianDamageNumber(),
        val driftX: Float = 0f,
        val driftY: Float = -1f,
        val tone: Int = 0,
    )

    private data class ElementBurst(
        var x: Float,
        var y: Float,
        val primary: Int,
        val secondary: Int,
        val reaction: Int,
        var radius: Float,
        var life: Float,
        val maxLife: Float,
        var weight: Int,
        var direction: Float,
        var flags: Int,
        val seed: Int,
    )

    private data class SkillBurst(
        val skill: Int,
        val phase: Int,
        var x: Float,
        var y: Float,
        val dirX: Float,
        val dirY: Float,
        val radius: Float,
        val rank: Int,
        val flags: Int,
        val seed: Int,
        var life: Float,
        val maxLife: Float,
    )

    private data class ApexBurst(
        val shape: Int,
        var x: Float,
        var y: Float,
        val seed: Int,
        var life: Float,
        val maxLife: Float,
    )

    private data class ReactionStamp(
        var x: Float,
        var y: Float,
        val reaction: Int,
        var life: Float,
        val maxLife: Float,
    )

    private data class WeaponArc(
        val fromX: Float,
        val fromY: Float,
        val toX: Float,
        val toY: Float,
        var life: Float,
        val style: Int,
        val maxLife: Float,
    )

    private companion object {
        const val NOTICE_DROPPED = 0
        const val NOTICE_MERGED = 1
        const val NOTICE_OPENED = 2
        const val FX_SEED_MASK = 0x5EED_C0DE
        const val TAU = 6.283185307179586f
    }
}
