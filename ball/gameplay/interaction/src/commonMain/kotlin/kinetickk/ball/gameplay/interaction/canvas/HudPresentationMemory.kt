// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.EnemyRank
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.KkLevelTier
import kinetickk.foundation.design.KkSlam
import kinetickk.foundation.design.KkTime
import kotlin.math.max

/**
 * Presentation-only memory of the running HUD: what the previous drawn frame showed, so the HUD can
 * animate changes (level-badge slam, tier and Apex one-shots, skill-slot one-shots, hit ghosts,
 * polarity draining, banner entrances) without new domain state. It never feeds a decision; a fresh
 * instance simply starts without transitions. The first observed frame and the first frame of a new
 * run (its clock went back) only record what they show; later frames fire one-shots on their edges.
 * Draw-thread confined; one instance per gameplay host; no allocation per frame.
 */
internal class HudPresentationMemory {
    private var observedAt = Float.NaN

    private var lastLevel = -1
    private var levelChangedAt = Float.NEGATIVE_INFINITY

    private var lastElapsed = Float.NaN
    private var lastTier = KkLevelTier.T1
    private var tierChangedAt = Float.NEGATIVE_INFINITY

    /** The tier the last tier change reached (its ring burst's accent). */
    var tierTo: KkLevelTier = KkLevelTier.T1
        private set
    private var apexUnlockedAt = Float.NEGATIVE_INFINITY
    private var lastApexReady = false
    private var apexReadyAt = Float.NEGATIVE_INFINITY
    private var lastApexActive = false
    private var apexCastAt = Float.NEGATIVE_INFINITY
    private var lastApexRefunded = 0f
    private var apexRefundAt = Float.NEGATIVE_INFINITY

    private val lastSkill = IntArray(SKILL_SLOTS) { -1 }
    private val acquiredAt = FloatArray(SKILL_SLOTS) { Float.NEGATIVE_INFINITY }
    private val lastRank = IntArray(SKILL_SLOTS)
    private val rankedAt = FloatArray(SKILL_SLOTS) { Float.NEGATIVE_INFINITY }
    private val lastReady = BooleanArray(SKILL_SLOTS)
    private val readyAt = FloatArray(SKILL_SLOTS) { Float.NEGATIVE_INFINITY }
    private val lastRefunded = FloatArray(SKILL_SLOTS)
    private val refundAt = FloatArray(SKILL_SLOTS) { Float.NEGATIVE_INFINITY }
    private var skillRowShownAt = Float.NEGATIVE_INFINITY

    private var lastIntegrity = Float.NaN
    private var integrityGhost = 0f
    private var integrityGhostAt = Float.NEGATIVE_INFINITY

    private var bossId = -1
    private var lastBossIntegrity = Float.NaN
    private var bossGhost = 0f
    private var bossGhostAt = Float.NEGATIVE_INFINITY

    private var lastPolarity = Float.NaN
    private var lastPolarityElapsed = Float.NaN
    private var polarityDrainingUntil = Float.NEGATIVE_INFINITY

    private var overdriveFullTime = 0f

    private var lastMessage: String? = null
    private var lastMessageTime = 0f
    private var messageShownAt = Float.NEGATIVE_INFINITY
    val dimensionShutter = DimensionShutterMemory() // the world layer's edges, for the dimension shutter

    /** Records [engine] as shown at [renderTime]; repeated draws of one frame change nothing. */
    fun observe(engine: GameplayRenderModel, renderTime: Float, boss: BossTarget) {
        val first = observedAt.isNaN()
        observedAt = renderTime

        if (lastLevel >= 0 && engine.level > lastLevel) levelChangedAt = renderTime
        lastLevel = engine.level

        // One-shots fire on edges between frames of one run: the first frame and a new run's first
        // frame (its clock went back) only record, and a new run drops what the last one was playing.
        val restarted = !first && engine.elapsed < lastElapsed
        val armed = !first && !restarted
        lastElapsed = engine.elapsed
        if (restarted) forgetOneShots()
        observeLevelTier(engine, renderTime, armed)
        observeSkills(engine, renderTime, armed)
        dimensionShutter.observe(engine.dimension.layer, renderTime, armed)

        val integrity = engine.hp / engine.maxHp.coerceAtLeast(1f)
        if (!first && !lastIntegrity.isNaN() && integrity < lastIntegrity) {
            integrityGhost = (integrityGhost(renderTime) + lastIntegrity - integrity).coerceAtMost(1f)
            integrityGhostAt = renderTime
        }
        lastIntegrity = integrity

        if (boss.id != bossId) {
            bossId = boss.id
            lastBossIntegrity = boss.integrity
            bossGhost = 0f
        } else if (boss.id >= 0 && boss.integrity < lastBossIntegrity) {
            bossGhost = (bossGhost(renderTime) + lastBossIntegrity - boss.integrity).coerceAtMost(1f)
            bossGhostAt = renderTime
            lastBossIntegrity = boss.integrity
        } else {
            lastBossIntegrity = boss.integrity
        }

        // Polarity changes only on simulation steps, so the drain is measured per simulated second:
        // frames between steps (or while paused) compare nothing, and the cue shows at the same
        // drain rate whatever the frame rate. A clock that went back is a new run: just re-arm.
        val simulated = engine.elapsed - lastPolarityElapsed
        if (simulated > 0f && lastPolarity - engine.polarityStability > POLARITY_DRAIN_PER_SECOND * simulated) {
            polarityDrainingUntil = renderTime + POLARITY_DRAIN_HOLD_SECONDS
        }
        if (simulated != 0f) {
            lastPolarity = engine.polarityStability
            lastPolarityElapsed = engine.elapsed
        }

        overdriveFullTime = if (engine.overdriveTime > 0f) max(overdriveFullTime, engine.overdriveTime) else 0f

        val message = if (engine.messageTime > 0f) engine.message else null
        if (message != null && (message != lastMessage || engine.messageTime > lastMessageTime + MESSAGE_RESTART_EPSILON)) {
            messageShownAt = if (first) Float.NEGATIVE_INFINITY else renderTime
        }
        lastMessage = message
        lastMessageTime = engine.messageTime
    }

    private fun observeLevelTier(engine: GameplayRenderModel, renderTime: Float, armed: Boolean) {
        val tier = KkLevelTier.of(engine.level, engine.maxLevel)
        if (armed && tier > lastTier) {
            if (tier == KkLevelTier.T6) {
                apexUnlockedAt = renderTime
            } else {
                tierChangedAt = renderTime
                tierTo = tier
            }
        }
        lastTier = tier

        val apex = engine.apex
        val active = apex.active01 > 0f
        // The unlock already plays the Apex coming online: its ready edge pings nothing.
        if (armed && apex.ready && !lastApexReady && apexUnlock(renderTime) >= 1f) apexReadyAt = renderTime
        if (armed && active && !lastApexActive) apexCastAt = renderTime
        if (armed && apex.refunded > lastApexRefunded) apexRefundAt = renderTime
        lastApexReady = apex.ready
        lastApexActive = active
        lastApexRefunded = apex.refunded
    }

    private fun observeSkills(engine: GameplayRenderModel, renderTime: Float, armed: Boolean) {
        val slots = engine.skillBar.slots
        var shownBefore = false
        var shownNow = false
        for (index in 0 until SKILL_SLOTS) {
            val slot = slots[index]
            if (lastSkill[index] >= 0) shownBefore = true
            if (slot.skill >= 0) shownNow = true
            if (armed && slot.skill != lastSkill[index]) {
                // A new effect in the slot (an active item bound or replaced): its slam, not a rank, ready or refund flash.
                if (slot.skill >= 0) acquiredAt[index] = renderTime
            } else if (armed && slot.skill >= 0) {
                if (slot.rank > lastRank[index]) rankedAt[index] = renderTime
                if (slot.ready && !lastReady[index]) readyAt[index] = renderTime
                if (slot.refunded > lastRefunded[index]) refundAt[index] = renderTime
            }
            lastSkill[index] = slot.skill
            lastRank[index] = slot.rank
            lastReady[index] = slot.ready
            lastRefunded[index] = slot.refunded
        }
        if (armed && shownNow && !shownBefore) skillRowShownAt = renderTime
    }

    private fun forgetOneShots() {
        tierChangedAt = Float.NEGATIVE_INFINITY
        apexUnlockedAt = Float.NEGATIVE_INFINITY
        apexReadyAt = Float.NEGATIVE_INFINITY
        apexCastAt = Float.NEGATIVE_INFINITY
        apexRefundAt = Float.NEGATIVE_INFINITY
        acquiredAt.fill(Float.NEGATIVE_INFINITY)
        rankedAt.fill(Float.NEGATIVE_INFINITY)
        readyAt.fill(Float.NEGATIVE_INFINITY)
        refundAt.fill(Float.NEGATIVE_INFINITY)
        skillRowShownAt = Float.NEGATIVE_INFINITY
        dimensionShutter.forget()
    }

    /** Level-badge slam progress 0..1 (1 = settled) at [renderTime]. */
    fun levelSlam(renderTime: Float): Float =
        ((renderTime - levelChangedAt) * 1000f / KkSlam.DURATION_MS).coerceIn(0f, 1f)

    /** Tier change (T2..T5) progress over [KkTime.TierBurst]; the tier reached is [tierTo]. */
    fun tierBurst(renderTime: Float): Float = progress(tierChangedAt, KkTime.TierBurst, renderTime)

    /** Reaching the cap (T6) progress over [KkTime.ApexUnlock]. */
    fun apexUnlock(renderTime: Float): Float = progress(apexUnlockedAt, KkTime.ApexUnlock, renderTime)

    /** The Apex turning ready again, over [KkTime.ApexPing]. */
    fun apexPing(renderTime: Float): Float = progress(apexReadyAt, KkTime.ApexPing, renderTime)

    /** The Apex cast (its activation starting), over [APEX_CAST_MS]. */
    fun apexCast(renderTime: Float): Float = progress(apexCastAt, APEX_CAST_MS, renderTime)

    /** Data refunding Apex cooldown, over [REFUND_MS]. */
    fun apexRefund(renderTime: Float): Float = progress(apexRefundAt, REFUND_MS, renderTime)

    /** A new active effect in [slot] (bound or replaced), over [SKILL_ACQUIRE_MS]. */
    fun skillAcquired(slot: Int, renderTime: Float): Float = progress(acquiredAt[slot], SKILL_ACQUIRE_MS, renderTime)

    /** Skill [slot] ranked up, over [SKILL_RANK_MS]. */
    fun skillRanked(slot: Int, renderTime: Float): Float = progress(rankedAt[slot], SKILL_RANK_MS, renderTime)

    /** Skill [slot] turning ready, over [SKILL_READY_MS]. */
    fun skillReady(slot: Int, renderTime: Float): Float = progress(readyAt[slot], SKILL_READY_MS, renderTime)

    /** Cooldown refunded to skill [slot], over [REFUND_MS]. */
    fun skillRefund(slot: Int, renderTime: Float): Float = progress(refundAt[slot], REFUND_MS, renderTime)

    /** The active row sliding in with the run's first active item, over [SKILL_ROW_MS]. */
    fun skillRow(renderTime: Float): Float = progress(skillRowShownAt, SKILL_ROW_MS, renderTime)

    private fun progress(at: Float, durationMs: Int, renderTime: Float): Float =
        ((renderTime - at) * 1000f / durationMs).coerceIn(0f, 1f)

    /** Remaining integrity hit ghost as a fraction of max integrity. */
    fun integrityGhost(renderTime: Float): Float = drain(integrityGhost, integrityGhostAt, renderTime)

    /** Remaining boss hit ghost as a fraction of the boss's max integrity. */
    fun bossGhost(renderTime: Float): Float = drain(bossGhost, bossGhostAt, renderTime)

    fun polarityDraining(renderTime: Float): Boolean = renderTime < polarityDrainingUntil

    /** Overdrive fill while it fires: remaining time over the longest time seen in this burst. */
    fun overdriveDrain(engine: GameplayRenderModel): Float =
        if (engine.overdriveTime <= 0f || overdriveFullTime <= 0f) 0f else engine.overdriveTime / overdriveFullTime

    /** Seconds since the current message appeared (large when it was already showing). */
    fun messageAge(renderTime: Float): Float = renderTime - messageShownAt

    private fun drain(amount: Float, at: Float, renderTime: Float): Float {
        if (amount <= 0f) return 0f
        val t = (renderTime - at) * 1000f / KkTime.GhostDrain
        return if (t >= 1f) 0f else amount * (1f - t.coerceAtLeast(0f))
    }

    companion object {
        const val SKILL_SLOTS = ContentBounds.SKILL_SLOTS

        /** Apex cast: bone flash, ring burst and badge kick. */
        const val APEX_CAST_MS = KkTime.Drift

        /** Refund flashes (Apex meters, skill sweeps). */
        const val REFUND_MS = KkTime.Pull

        /** A new active effect in a slot: slot slam and ring burst. */
        const val SKILL_ACQUIRE_MS = KkTime.Shutter

        /** Skill rank-up: pip fill and ring burst. */
        const val SKILL_RANK_MS = KkTime.Drift

        /** Skill ready: face flash and ring burst. */
        const val SKILL_READY_MS = KkTime.Shutter

        /** The skill row sliding in. */
        const val SKILL_ROW_MS = KkTime.Pull

        /** Slower drains (the first few percent of edge strain) do not pulse the polarity cue. */
        private const val POLARITY_DRAIN_PER_SECOND = 0.03f
        private const val POLARITY_DRAIN_HOLD_SECONDS = 0.35f
        private const val MESSAGE_RESTART_EPSILON = 0.05f
    }
}

/** The boss the HUD tracks top-center: the living enemy of the highest content rank above ordinary, the first of it. */
internal class BossTarget {
    var id: Int = -1
        private set
    var type: EnemyType? = null
        private set

    /** The tracked boss's content rank; null while none is tracked. */
    var rank: EnemyRank? = null
        private set
    var integrity: Float = 0f
        private set

    /** The boss's packed statuses ([EnemyStatusBits]). */
    var statusBits: Int = 0
        private set

    /** The boss's aspect, weakness and shift ([kinetickk.ball.gameplay.nucleus.render.EnemyAffinity]). */
    var affinityBits: Int = 0
        private set

    fun select(engine: GameplayRenderModel): BossTarget {
        id = -1
        type = null
        rank = null
        integrity = 0f
        statusBits = 0
        affinityBits = 0
        val enemies = engine.enemies
        // By content rank ([bossTargetPriority]): the Architect outranks a secret boss, a secret boss a mini-boss, a
        // mini-boss an Elite; the first of a rank stays tracked.
        var priority = 0
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (enemy.dead || enemy.hp <= 0f) continue
            val enemyRank = engine.content.enemy(enemy.type).rank
            val candidate = bossTargetPriority(enemyRank)
            if (candidate > priority) {
                priority = candidate
                id = enemy.id
                type = enemy.type
                rank = enemyRank
                integrity = (enemy.hp / enemy.maxHp.coerceAtLeast(1f)).coerceIn(0f, 1f)
                statusBits = enemy.statusBits
                affinityBits = enemy.affinityBits
                if (enemyRank == EnemyRank.BOSS) break
            }
        }
        return this
    }
}
