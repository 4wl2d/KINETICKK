// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.KkSlam
import kinetickk.foundation.design.KkTime
import kotlin.math.max

/**
 * Presentation-only memory of the running HUD: what the previous drawn frame showed, so the HUD can
 * animate changes (level-badge slam, hit ghosts, polarity draining, banner entrances) without new
 * domain state. It never feeds a decision; a fresh instance simply starts without transitions.
 * Draw-thread confined; one instance per gameplay host.
 */
internal class HudPresentationMemory {
    private var observedAt = Float.NaN

    private var lastLevel = -1
    private var levelChangedAt = Float.NEGATIVE_INFINITY

    private var lastIntegrity = Float.NaN
    private var integrityGhost = 0f
    private var integrityGhostAt = Float.NEGATIVE_INFINITY

    private var bossId = -1
    private var lastBossIntegrity = Float.NaN
    private var bossGhost = 0f
    private var bossGhostAt = Float.NEGATIVE_INFINITY

    private var lastPolarity = Float.NaN
    private var polarityDrainingUntil = Float.NEGATIVE_INFINITY

    private var overdriveFullTime = 0f

    private var lastMessage: String? = null
    private var lastMessageTime = 0f
    private var messageShownAt = Float.NEGATIVE_INFINITY

    /** Records [engine] as shown at [renderTime]; repeated draws of one frame change nothing. */
    fun observe(engine: GameplayRenderModel, renderTime: Float, boss: BossTarget) {
        val first = observedAt.isNaN()
        observedAt = renderTime

        if (lastLevel >= 0 && engine.level > lastLevel) levelChangedAt = renderTime
        lastLevel = engine.level

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

        if (!lastPolarity.isNaN() && engine.polarityStability < lastPolarity - POLARITY_EPSILON) {
            polarityDrainingUntil = renderTime + POLARITY_DRAIN_HOLD_SECONDS
        }
        lastPolarity = engine.polarityStability

        overdriveFullTime = if (engine.overdriveTime > 0f) max(overdriveFullTime, engine.overdriveTime) else 0f

        val message = if (engine.messageTime > 0f) engine.message else null
        if (message != null && (message != lastMessage || engine.messageTime > lastMessageTime + MESSAGE_RESTART_EPSILON)) {
            messageShownAt = if (first) Float.NEGATIVE_INFINITY else renderTime
        }
        lastMessage = message
        lastMessageTime = engine.messageTime
    }

    /** Level-badge slam progress 0..1 (1 = settled) at [renderTime]. */
    fun levelSlam(renderTime: Float): Float =
        ((renderTime - levelChangedAt) * 1000f / KkSlam.DURATION_MS).coerceIn(0f, 1f)

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

    private companion object {
        const val POLARITY_EPSILON = 0.0005f
        const val POLARITY_DRAIN_HOLD_SECONDS = 0.35f
        const val MESSAGE_RESTART_EPSILON = 0.05f
    }
}

/** The boss the HUD tracks top-center: the Architect first, else the first living elite. */
internal class BossTarget {
    var id: Int = -1
        private set
    var type: EnemyType? = null
        private set
    var integrity: Float = 0f
        private set

    fun select(engine: GameplayRenderModel): BossTarget {
        id = -1
        type = null
        integrity = 0f
        val enemies = engine.enemies
        for (index in enemies.indices) {
            val enemy = enemies[index]
            if (enemy.dead || enemy.hp <= 0f) continue
            if (enemy.type == EnemyType.ARCHITECT || (enemy.type == EnemyType.ELITE && id < 0)) {
                id = enemy.id
                type = enemy.type
                integrity = (enemy.hp / enemy.maxHp.coerceAtLeast(1f)).coerceIn(0f, 1f)
                if (enemy.type == EnemyType.ARCHITECT) break
            }
        }
        return this
    }
}
