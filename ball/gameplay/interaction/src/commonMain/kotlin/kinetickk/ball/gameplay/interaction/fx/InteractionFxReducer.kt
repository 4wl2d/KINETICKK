// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.fx

import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.StatusElement
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.random.CloneableXorWowRandom
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

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
    private val buildNotifications = mutableListOf<BuildNotice>()
    private val elementBursts = mutableListOf<ElementBurst>()
    private val reactionStamps = mutableListOf<ReactionStamp>()
    private val skillBursts = mutableListOf<SkillBurst>()
    private val apexBursts = mutableListOf<ApexBurst>()
    private val structures = StructureFxTracks()
    private var runReactionMask = 0
    private var runDiscoveryMask = 0
    private var runBreakMask = 0
    private var projectionDirty = false
    private var cachedProjection = VisualFxProjection.EMPTY
    private var buildNotificationsDirty = false
    private var particlesDirty = false
    private var motionEchoesDirty = false
    private var shockwavesDirty = false
    private var damageNumbersDirty = false
    private var weaponArcsDirty = false
    private var elementBurstsDirty = false
    private var reactionStampsDirty = false
    private var skillBurstsDirty = false
    private var apexBurstsDirty = false

    private val coreRibbon = CoreRibbonTrack()

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
            buildNotifications = if (buildNotificationsDirty) buildNotifications.toNotificationProjections() else cachedProjection.buildNotifications,
            particles = if (particlesDirty) particles.toParticleProjections() else cachedProjection.particles,
            motionEchoes = if (motionEchoesDirty) motionEchoes.toMotionEchoProjections() else cachedProjection.motionEchoes,
            shockwaves = if (shockwavesDirty) shockwaves.toShockwaveProjections() else cachedProjection.shockwaves,
            damageNumbers = if (damageNumbersDirty) damageNumbers.toDamageNumberProjections() else cachedProjection.damageNumbers,
            weaponArcs = if (weaponArcsDirty) weaponArcs.toWeaponArcProjections() else cachedProjection.weaponArcs,
            elementBursts = if (elementBurstsDirty) elementBursts.toElementBurstProjections() else cachedProjection.elementBursts,
            skillBursts = if (skillBurstsDirty) skillBursts.toSkillBurstProjections() else cachedProjection.skillBursts,
            apexBursts = if (apexBurstsDirty) apexBursts.toApexBurstProjections() else cachedProjection.apexBursts,
            reactionStamps = if (reactionStampsDirty) reactionStamps.toReactionStampProjections() else cachedProjection.reactionStamps,
            runReactionMask = runReactionMask,
            runDiscoveryMask = runDiscoveryMask,
            coreRibbon = coreRibbon.published(cachedProjection.coreRibbon),
            disintegrations = structures.disintegrations.published(),
            dungeonMoments = structures.moments.published(),
        )
        projectionDirty = false
        buildNotificationsDirty = false
        particlesDirty = false
        motionEchoesDirty = false
        shockwavesDirty = false
        damageNumbersDirty = false
        weaponArcsDirty = false
        elementBurstsDirty = false
        reactionStampsDirty = false
        skillBurstsDirty = false
        apexBurstsDirty = false
        return cachedProjection
    }

    /** Returns whether the externally visible projection changed. */
    private fun applyCue(cue: VisualFxCue): Boolean =
        when (cue) {
            is VisualFxCue.BuildChanged -> addNotice(cue.title, cue.details,
                if (cue.legendary) BuildNoticeKind.LEGENDARY else BuildNoticeTitles.kind(cue.title)) != NOTICE_DROPPED
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
                val ribbon = coreRibbon.sample(cue)
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
            is VisualFxCue.WorldRebased -> worldRebased(cue)
            is VisualFxCue.VisualCuesDropped -> false
            is VisualFxCue.StructureDisintegrated -> structures.disintegrations.add(cue, random.nextInt())
            is VisualFxCue.DungeonMoment -> structures.moments.add(cue, random.nextInt())
            is VisualFxCue.SkillCast -> skillCast(cue)
            is VisualFxCue.ApexActivated -> {
                apexBursts += ApexBurst(cue.shape.ordinal, cue.x, cue.y, random.nextInt(), ApexFxSpec.activation.life,
                    ApexFxSpec.activation.life)
                trimFront(apexBursts, InteractionFxLimits.MAX_APEX_BURSTS)
                apexBurstsDirty = true
                true
            }
        }

    /** Moves world-anchored looks by the rebase shift; returns whether the projection changed. */
    private fun worldRebased(cue: VisualFxCue.WorldRebased): Boolean {
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
        val ribbonMoved = coreRibbon.rebase(cue.shiftX, cue.shiftY)
        // A shattering structure's shards and a dungeon's moments mark the world like the flashes.
        val shardsMoved = structures.rebase(cue.shiftX, cue.shiftY)
        return changed || shardsMoved || elementBursts.isNotEmpty() || reactionStamps.isNotEmpty() || skillBursts.isNotEmpty() || apexBursts.isNotEmpty() ||
            ribbonMoved
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
            !coreRibbon.isEmpty ||
            runReactionMask != 0 || runDiscoveryMask != 0 ||
            !structures.isEmpty
        elementBursts.clear()
        reactionStamps.clear()
        skillBursts.clear()
        apexBursts.clear()
        structures.clear()
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
        coreRibbon.clear()
        if (changed) {
            coreRibbon.invalidate()
            buildNotificationsDirty = true
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
                notice.details = merged.toImmutableList()
                notice.life = notice.maxLife
                buildNotificationsDirty = true
                return NOTICE_MERGED
            }
        }
        val notice = BuildNotice(title, details.toImmutableList(), kind.lifeSeconds, kind)
        var at = buildNotifications.size
        while (at > 0 && buildNotifications[at - 1].kind.priority > kind.priority) at--
        buildNotifications.add(at, notice)
        trimFront(buildNotifications, InteractionFxLimits.MAX_BUILD_NOTIFICATIONS)
        for (index in buildNotifications.indices) {
            if (buildNotifications[index] === notice) {
                buildNotificationsDirty = true
                return NOTICE_OPENED
            }
        }
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
        val intensity = ((cue.speed - 260f) / 1_500f).coerceIn(0f, 1f)
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
            coreRibbon.isEmpty &&
            structures.isEmpty
        ) return false
        coreRibbon.age(delta)
        retainInPlace(buildNotifications) { value ->
            value.life -= delta
            value.age += delta
            value.life > 0f
        }
        if (buildNotifications.isNotEmpty() || cachedProjection.buildNotifications.isNotEmpty()) buildNotificationsDirty = true
        val particleDamping = exp(-2.2f * delta)
        retainInPlace(particles) { value ->
            value.x += value.vx * delta
            value.y += value.vy * delta
            value.vx *= particleDamping
            value.vy *= particleDamping
            value.life -= delta
            !(value.life <= 0f)
        }
        retainInPlace(motionEchoes) { value ->
            value.life -= delta
            !(value.life <= 0f)
        }
        retainInPlace(shockwaves) { value ->
            value.life -= delta
            !(value.life <= 0f)
        }
        // Numbers keep their anchor; the renderer eases the drift from life (see DamageNumberProjection).
        retainInPlace(damageNumbers) { value ->
            value.life -= delta
            !(value.life <= 0f)
        }
        if (particles.isNotEmpty() || cachedProjection.particles.isNotEmpty()) particlesDirty = true
        if (motionEchoes.isNotEmpty() || cachedProjection.motionEchoes.isNotEmpty()) motionEchoesDirty = true
        if (shockwaves.isNotEmpty() || cachedProjection.shockwaves.isNotEmpty()) shockwavesDirty = true
        if (damageNumbers.isNotEmpty() || cachedProjection.damageNumbers.isNotEmpty()) damageNumbersDirty = true
        retainInPlace(elementBursts) { value ->
            value.life -= delta
            !(value.life <= 0f)
        }
        retainInPlace(reactionStamps) { value ->
            value.life -= delta
            !(value.life <= 0f)
        }
        if (elementBursts.isNotEmpty() || cachedProjection.elementBursts.isNotEmpty()) elementBurstsDirty = true
        if (reactionStamps.isNotEmpty() || cachedProjection.reactionStamps.isNotEmpty()) reactionStampsDirty = true
        retainInPlace(skillBursts) { value ->
            value.life -= delta
            !(value.life <= 0f)
        }
        retainInPlace(apexBursts) { value ->
            value.life -= delta
            !(value.life <= 0f)
        }
        if (skillBursts.isNotEmpty() || cachedProjection.skillBursts.isNotEmpty()) skillBurstsDirty = true
        if (apexBursts.isNotEmpty() || cachedProjection.apexBursts.isNotEmpty()) apexBurstsDirty = true
        structures.advance(delta)
        return true
    }

    private fun advanceWeaponArcs(delta: Float): Boolean {
        if (weaponArcs.isEmpty()) return false
        retainInPlace(weaponArcs) { value ->
            value.life -= delta
            !(value.life <= 0f)
        }
        weaponArcsDirty = true
        return true
    }

    private fun burst(cue: VisualFxCue.Burst): Boolean {
        val available = InteractionFxLimits.MAX_PARTICLES - particles.size
        if (available == 0) return false
        repeat(minOf(particleCount(cue.requestedCount, cue.density), available)) { index ->
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
        particlesDirty = true
        return true
    }

    private fun directionalBurst(cue: VisualFxCue.DirectionalBurst): Boolean {
        val available = InteractionFxLimits.MAX_PARTICLES - particles.size
        if (available == 0) return false
        val baseAngle = atan2(cue.directionY, cue.directionX)
        repeat(minOf(particleCount(cue.requestedCount, cue.density), available)) { index ->
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
        particlesDirty = true
        return true
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

    private companion object {
        const val NOTICE_DROPPED = 0
        const val NOTICE_MERGED = 1
        const val NOTICE_OPENED = 2
        const val FX_SEED_MASK = 0x5EED_C0DE
        const val TAU = 6.283185307179586f
    }
}
