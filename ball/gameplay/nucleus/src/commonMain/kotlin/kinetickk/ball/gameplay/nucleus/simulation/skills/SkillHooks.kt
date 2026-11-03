// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.skills

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.LinkStyleCode
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.builds.*
import kinetickk.ball.gameplay.nucleus.simulation.characters.*
import kinetickk.ball.gameplay.nucleus.simulation.combat.*
import kinetickk.ball.gameplay.nucleus.simulation.elements.*
import kinetickk.ball.gameplay.nucleus.simulation.progression.*
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Hooks other systems call.

/** Mass multiplier while Mass Driver runs. */
internal fun MutableGameState.skillMassMultiplier(): Float {
    val slot = activeSkillSlot(SkillId.MASS_DRIVER)
    return if (slot < 0) 1f else skillValues(slot)[MASS_DRIVER_MASS]
}

/** The slot holding [id] while its effect runs (ACTIVE_UNTIL ahead), or -1. */
private fun MutableGameState.activeSkillSlot(id: SkillId): Int {
    val slot = skillLoadout.slotOf(id)
    return if (slot >= 0 && skillRuntime[slot, SkillCell.ACTIVE_UNTIL] > elapsed) slot else -1
}

private fun MutableGameState.skillValues(slot: Int): List<Float> {
    val id = checkNotNull(skillLoadout.skill(slot)) { "slot $slot is empty" }
    return content.skill(id).rank(skillLoadout.rank(slot)).values
}

/**
 * A Core impact on [enemy] at [coreImpactSpeed] dealing [damage]: Arc Hook's window turns the first one into a
 * Dash hit with arcs, Mass Driver throws, Slingshot hurls and rank III Dual Infusion infuses.
 */
internal fun MutableGameState.onSkillCoreImpact(enemy: Enemy, coreImpactSpeed: Float, damage: Float, dashContact: Boolean) {
    val loadout = skillLoadout
    if (loadout.ownedCount == 0) return
    val runtime = skillRuntime
    val hookSlot = loadout.slotOf(SkillId.ARC_HOOK)
    if (hookSlot >= 0 && runtime[hookSlot, SkillCell.TARGET_UNTIL] > elapsed) {
        runtime[hookSlot, SkillCell.TARGET_UNTIL] = 0f
        if (!dashContact) onDashHit(enemy, damage)
        markForPuncture(enemy)
        val values = skillValues(hookSlot)
        val linkDamage = values[ARC_HOOK_LINK_DAMAGE] * skillPower(SkillId.ARC_HOOK)
        withSkillEffect(hookSlot) {
            val element = attuned(StatusElement.ION)
            chain(enemy.x, enemy.y, enemy.id, values[ARC_HOOK_LINKS].toInt(), ARC_HOOK_LINK_RANGE, LinkStyleCode.skill(SkillId.ARC_HOOK)) { link ->
                skillHit(link, linkDamage, element)
                if (link.alive) applyStatus(link, element, 1, 0)
            }
        }
    }
    if (!enemy.alive) return
    val driver = activeSkillSlot(SkillId.MASS_DRIVER)
    if (driver >= 0) {
        val values = skillValues(driver)
        if (coreImpactSpeed >= values[MASS_DRIVER_THRESHOLD]) {
            hurl(coreX, coreY, enemy, values[MASS_DRIVER_THROW])
            applyStatus(enemy, attuned(StatusElement.GRAVITIC), MASS_DRIVER_IMPACT_MASS, 0)
        }
    }
    if (!enemy.alive) return
    val sling = activeSkillSlot(SkillId.SLINGSHOT)
    if (sling >= 0) hurl(coreX, coreY, enemy, skillValues(sling)[SLING_HURL])
    val infusion = activeSkillSlot(SkillId.DUAL_INFUSION)
    if (infusion >= 0 && loadout.rank(infusion) >= ContentBounds.MAX_SKILL_RANK && enemy.alive) {
        infuse(infusion, enemy, infusionDominant())
        if (enemy.alive) infuse(infusion, enemy, infusionSecondary())
    }
}

/** Rank III Slingshot: impacts during the swing keep the Core's momentum. */
internal fun MutableGameState.slingshotKeepsMomentum(): Boolean {
    val slot = activeSkillSlot(SkillId.SLINGSHOT)
    return slot >= 0 && skillLoadout.rank(slot) >= ContentBounds.MAX_SKILL_RANK
}

/** A weapon hit on [enemy] while Dual Infusion runs: the dominant element, and every n-th hit the secondary. */
internal fun MutableGameState.onSkillWeaponHit(enemy: Enemy) {
    if (skillLoadout.ownedCount == 0) return
    val slot = activeSkillSlot(SkillId.DUAL_INFUSION)
    if (slot < 0 || !enemy.alive) return
    val runtime = skillRuntime
    val hits = runtime[slot, SkillCell.STORED] + 1f
    runtime[slot, SkillCell.STORED] = hits
    infuse(slot, enemy, infusionDominant())
    val every = skillValues(slot)[INFUSION_HITS_PER_SECONDARY].toInt().coerceAtLeast(1)
    if (enemy.alive && hits.toInt() % every == 0) infuse(slot, enemy, infusionSecondary())
}

private fun MutableGameState.infusionDominant(): StatusElement = build.dominant ?: StatusElement.THERMAL

private fun MutableGameState.infusionSecondary(): StatusElement = build.secondary ?: StatusElement.CRYO

/** One infused stack, at most the per-second cap of [slot]'s Dual Infusion. */
private fun MutableGameState.infuse(slot: Int, enemy: Enemy, element: StatusElement) {
    val runtime = skillRuntime
    if (elapsed - runtime[slot, SkillCell.AUX_AT] >= 1f) {
        runtime[slot, SkillCell.AUX_AT] = elapsed
        runtime[slot, SkillCell.AUX_COUNT] = 0f
    }
    val applied = runtime[slot, SkillCell.AUX_COUNT]
    if (applied >= skillValues(slot)[INFUSION_STACK_CAP]) return
    runtime[slot, SkillCell.AUX_COUNT] = applied + 1f
    applyStatus(enemy, element, 1, 0)
}

/** A landed slam on [enemy]: a ringing (BOOMED) enemy Shatters, and rank III Mass Driver adds Mass. */
internal fun MutableGameState.onSkillSlam(enemy: Enemy) {
    if (!enemy.alive) return
    if (consumeMark(enemy, MarkKind.BOOMED)) {
        forceElementBreak(enemy, StatusElement.SONIC)
        onMarkCashed(enemy, null, StatusElement.SONIC)
    }
    val driver = activeSkillSlot(SkillId.MASS_DRIVER)
    if (driver >= 0 && skillLoadout.rank(driver) >= ContentBounds.MAX_SKILL_RANK && enemy.alive) {
        applyStatus(enemy, attuned(StatusElement.GRAVITIC), 1, 0)
    }
}

/** True while a Frost Snap has coiled the next Dash. */
internal fun MutableGameState.dashCoiled(): Boolean {
    val slot = skillLoadout.slotOf(SkillId.FROST_SNAP)
    return slot >= 0 && skillRuntime[slot, SkillCell.AUX_UNTIL] > elapsed
}

/** Spends a coiled Dash and returns its push scale (1 when none is coiled). */
internal fun MutableGameState.consumeCoiledDash(): Float {
    if (!dashCoiled()) return 1f
    val slot = skillLoadout.slotOf(SkillId.FROST_SNAP)
    skillRuntime[slot, SkillCell.AUX_UNTIL] = 0f
    return skillValues(slot)[FROST_SNAP_COILED_PUSH]
}

/** Bolts Null Shroud holds right now (0 when it is not running). */
internal fun MutableGameState.shroudStored(): Int {
    val slot = activeSkillSlot(SkillId.NULL_SHROUD)
    return if (slot < 0) 0 else skillRuntime[slot, SkillCell.STORED].toInt()
}

/**
 * A death: a Spored enemy spreads Decay to its neighbours and drops Data. A Decay Spores seed drops it within its
 * cast's budget, and rank III Decay Spores refunds every skill for it. An item's `ApplyMark` mark, whose clause rate
 * already bounds it, always drops one Data pickup and never draws on a cast's Data or refund budget.
 */
internal fun MutableGameState.onSkillCarrierDied(enemy: Enemy) {
    val seeded = enemy.status[StatusCell.MARK_CAST] > 0f
    if (!consumeMark(enemy, MarkKind.SPORED)) return
    for (index in enemies.indices) {
        val target = enemies[index]
        if (target.id == enemy.id || !target.alive) continue
        if (distanceSquared(enemy.x, enemy.y, target.x, target.y) <= square(SPORES_SPREAD_RADIUS + target.radius)) {
            applyStatus(target, StatusElement.ENTROPY, SPORES_SPREAD_DECAY, 1)
        }
    }
    val slot = skillLoadout.slotOf(SkillId.DECAY_SPORES)
    if (!seeded || slot < 0) {
        dropSporedData(enemy)
        return
    }
    val runtime = skillRuntime
    val values = skillValues(slot)
    val dropped = runtime[slot, SkillCell.STORED]
    if (dropped < values[SPORES_DATA_CAP]) {
        runtime[slot, SkillCell.STORED] = dropped + 1f
        dropSporedData(enemy)
    }
    // Rank III: every skill recharges for each Spored death, up to the cast's cap per skill.
    if (skillLoadout.rank(slot) >= ContentBounds.MAX_SKILL_RANK) {
        val offered = runtime[slot, SkillCell.AUX_COUNT]
        val seconds = min(SPORES_REFUND_SECONDS, SPORES_REFUND_CAP - offered)
        if (seconds > 0f) {
            runtime[slot, SkillCell.AUX_COUNT] = offered + seconds
            refundAllSkills(seconds)
        }
    }
}

/** One Data pickup at a Spored corpse, scattered at a golden angle from the enemy id: no draw from any random stream. */
private fun MutableGameState.dropSporedData(enemy: Enemy) {
    val angle = enemy.id * SPORES_SCATTER_ANGLE
    addPickup(Pickup(PickupType.DATA, enemy.x, enemy.y, cos(angle) * SPORES_DATA_SPEED, sin(angle) * SPORES_DATA_SPEED))
}

/**
 * True when a hit from [source] is caused by an active effect: a skill hit, anything dealt while a cast resolves (its
 * forced reactions and shockwaves), or the Core's impact or Dash hit while a skill moves it (Slingshot's swing, Mass
 * Driver's charge, Arc Hook's flight and Dash window). Utility skills deliver their damage this way.
 */
internal fun MutableGameState.skillCausedHit(source: HitSource?): Boolean {
    if (source == HitSource.SKILL || castSlot >= 0) return true
    if (source != HitSource.IMPACT && source != HitSource.DASH || skillLoadout.ownedCount == 0) return false
    if (activeSkillSlot(SkillId.SLINGSHOT) >= 0 || activeSkillSlot(SkillId.MASS_DRIVER) >= 0) return true
    val hook = skillLoadout.slotOf(SkillId.ARC_HOOK)
    return hook >= 0 && skillRuntime[hook, SkillCell.TARGET_UNTIL] > elapsed
}
