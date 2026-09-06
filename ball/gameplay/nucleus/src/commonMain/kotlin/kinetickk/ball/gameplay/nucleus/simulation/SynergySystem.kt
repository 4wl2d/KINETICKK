// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.SynergyDefinition
import kinetickk.ball.content.api.SynergyId
import kinetickk.ball.gameplay.api.BuildSynergySummary
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

internal fun MutableGameState.hasSynergy(id: SynergyId): Boolean =
    content.synergies.firstOrNull { it.id == id }?.let(::hasSynergy) == true

internal fun MutableGameState.hasSynergy(definition: SynergyDefinition): Boolean {
    val aspect = definition.requiredAspect
    return if (aspect != null) {
        if (aspect == RelicAspect.SOVEREIGN) false else {
            var first: RelicId? = null
            for (equipped in equippedRelics) if (content.relic(equipped.id).aspect == aspect) {
                if (first == null) first = equipped.id else if (first != equipped.id) return true
            }
            false
        }
    } else definition.requiredRelics.all { relicRank(it) > 0 }
}

internal fun MutableGameState.buildSynergySummaries(): ImmutableList<BuildSynergySummary> =
    content.synergies.map { definition ->
        val active = hasSynergy(definition)
        val aspect = definition.requiredAspect
        val missing = if (active) emptyList() else if (aspect != null) {
            val count = equippedRelics.filter { content.relic(it.id).aspect == aspect }.map { it.id }.distinct().size
            listOf("${2 - count} different ${aspect.displayLabel} relic${if (count == 0) "s" else ""}")
        } else definition.requiredRelics.filter { relicRank(it) <= 0 }.map { content.relic(it).name }
        BuildSynergySummary(definition.id.name, definition.name, definition.description, active, missing.toImmutableList())
    }.toImmutableList()

internal const val MAX_SYNERGY_EFFECTS = 32

internal fun MutableGameState.resetSynergyRuntime() {
    synergyEffects = emptyList()
    synergyCooldowns.fill(0f)
    synergyManeuverCharge = 0f
    ghostDashPending = false
}

internal fun MutableGameState.clearDependentSynergyEffects(removed: RelicId) {
    val aspect = content.relic(removed).aspect
    val affected = content.synergies.filter { it.requiredAspect == aspect || removed in it.requiredRelics }.map { it.id }
    synergyEffects = synergyEffects.filterNot { it.synergy in affected }
    affected.forEach { synergyCooldowns[it.ordinal] = 0f }
    if (SynergyId.VECTOR_MANEUVER in affected) synergyManeuverCharge = 0f
    if (SynergyId.GHOST_MIRROR in affected) ghostDashPending = false
}

internal fun MutableGameState.addSynergyEffect(effect: SynergyEffect) {
    if (!hasSynergy(effect.synergy)) return
    // An enemy has one decay per source; a Dash has one edge. Other work competes for a fixed bound.
    val replace = synergyEffects.indexOfFirst {
        it.synergy == effect.synergy && it.kind == effect.kind &&
            (effect.kind == SynergyEffectKind.GHOST_EDGE ||
                effect.kind == SynergyEffectKind.DECAY && it.enemyId == effect.enemyId)
    }
    if (replace >= 0) synergyEffects = synergyEffects.mapIndexed { index, old -> if (index == replace) effect else old }
    else if (synergyEffects.size < MAX_SYNERGY_EFFECTS) synergyEffects = synergyEffects + effect
}

internal fun MutableGameState.synergyReady(id: SynergyId, seconds: Float): Boolean {
    if (!hasSynergy(id) || synergyCooldowns[id.ordinal] > 0f) return false
    synergyCooldowns[id.ordinal] = seconds
    return true
}

internal fun MutableGameState.onSynergyTurn() {
    if (hasSynergy(SynergyId.VECTOR_MANEUVER)) {
        synergyManeuverCharge = 0.35f
        heat = (heat - 4f).coerceAtLeast(0f)
    }
}

internal fun MutableGameState.onSynergyDash() {
    ghostDashPending = hasSynergy(SynergyId.GHOST_MIRROR)
    ghostDashStartX = coreX
    ghostDashStartY = coreY
}

internal fun MutableGameState.updateSynergyRuntime(delta: Float) {
    for (index in synergyCooldowns.indices) {
        if (synergyCooldowns[index] > 0f) synergyCooldowns[index] = (synergyCooldowns[index] - delta).coerceAtLeast(0f)
    }
    if (ghostDashPending && distanceSquared(coreX, coreY, ghostDashStartX, ghostDashStartY) >= square(120f)) {
        addSynergyEffect(SynergyEffect(
            SynergyId.GHOST_MIRROR, SynergyEffectKind.GHOST_EDGE,
            1.8f, x = ghostDashStartX, y = ghostDashStartY, endX = coreX, endY = coreY,
        ))
        ghostDashPending = false
    }
    if (dashPhaseTime <= 0f) ghostDashPending = false
    if (synergyEffects.isEmpty()) return
    val retained = ArrayList<SynergyEffect>(synergyEffects.size)
    for (effect in synergyEffects) {
        if (!hasSynergy(effect.synergy)) continue
        val remaining = effect.remaining - delta
        val tick = minOf(delta, effect.remaining)
        when (effect.kind) {
            SynergyEffectKind.ECHO -> if (remaining <= 0f) {
                enemies.firstOrNull { it.id == effect.enemyId && !it.dead && it.hp > 0f }?.let { damageEnemy(it, effect.damage) }
            }
            SynergyEffectKind.DECAY -> {
                enemies.firstOrNull { it.id == effect.enemyId && !it.dead && it.hp > 0f }?.let { damageEnemy(it, effect.damage * tick) }
            }
            SynergyEffectKind.ANCHOR -> {
                for (enemy in enemies) if (!enemy.dead && enemy.hp > 0f && distanceSquared(enemy.x, enemy.y, effect.x, effect.y) < square(effect.radius)) {
                    enemy.vx += (effect.x - enemy.x) * tick * 2f
                    enemy.vy += (effect.y - enemy.y) * tick * 2f
                }
                if (remaining <= 0f) areaRelicDamage(effect.x, effect.y, effect.radius, effect.damage)
            }
            SynergyEffectKind.TRAIL -> {
                for (enemy in enemies) if (!enemy.dead && enemy.hp > 0f && segmentCircleIntersects(effect.x, effect.y, effect.endX, effect.endY, enemy.x, enemy.y, effect.radius + enemy.radius)) {
                    val damping = kotlin.math.exp(-2.5f * tick)
                    enemy.vx *= damping
                    enemy.vy *= damping
                    damageEnemy(enemy, effect.damage * tick)
                }
            }
            SynergyEffectKind.GHOST_EDGE -> Unit
        }
        if (remaining > 0f) retained += effect.copy(remaining = remaining)
    }
    synergyEffects = retained
}

internal fun MutableGameState.onSynergyPrimaryHit(enemy: Enemy, result: DamageResult, brakeCharge: Float) {
    if (synergyReady(SynergyId.GRAVITIC_GROUPING, 0.55f)) {
        for (target in enemies) if (target.id != enemy.id && !target.dead && target.hp > 0f) {
            val dx = enemy.x - target.x
            val dy = enemy.y - target.y
            val distance = length(dx, dy)
            if (distance in 1f..220f) {
                target.vx += dx / distance * 110f
                target.vy += dy / distance * 110f
            }
        }
        shockwave(enemy.x, enemy.y, 0.3f, 220f, 2)
    }
    if (synergyReady(SynergyId.ION_DISCHARGE, 0.45f)) chainRelicDamage(enemy, 2, 330f, result.amount * 0.16f)
    if (synergyReady(SynergyId.RIFT_ECHO, 0.30f)) addSynergyEffect(SynergyEffect(
        SynergyId.RIFT_ECHO, SynergyEffectKind.ECHO, 0.45f, result.amount * 0.18f, enemy.id,
    ))
    if (synergyReady(SynergyId.PRISM_REFRACTION, 0.60f)) nearestOtherEnemy(enemy.x, enemy.y, enemy.id, 400f)?.let {
        damageEnemy(it, result.amount * 0.25f)
        addRelicArc(enemy.x, enemy.y, it.x, it.y)
    }
    if (hasSynergy(SynergyId.ENTROPY_DECAY)) addSynergyEffect(SynergyEffect(
        SynergyId.ENTROPY_DECAY, SynergyEffectKind.DECAY, 2f, 6f, enemy.id,
    ))
    if (brakeCharge > 0f && hasSynergy(SynergyId.BRAKE_COMPRESSION)) {
        areaRelicDamage(enemy.x, enemy.y, 150f, result.amount * brakeCharge, enemy.id)
        for (target in enemies) if (!target.dead && target.hp > 0f && distanceSquared(target.x, target.y, enemy.x, enemy.y) <= square(150f)) {
            target.vx += (enemy.x - target.x) * 2f
            target.vy += (enemy.y - target.y) * 2f
        }
    }
    if (hasSynergy(SynergyId.GHOST_MIRROR)) {
        val edge = synergyEffects.firstOrNull { it.kind == SynergyEffectKind.GHOST_EDGE }
        if (edge != null) nearestOtherEnemy(edge.endX, edge.endY, enemy.id, 420f)?.let {
            damageEnemy(it, result.amount * 0.35f)
            addRelicArc(edge.x, edge.y, edge.endX, edge.endY)
            addRelicArc(edge.endX, edge.endY, it.x, it.y)
            synergyEffects = synergyEffects.filterNot { effect -> effect === edge }
        }
    }
}

internal fun MutableGameState.onSynergyKill(enemy: Enemy) {
    val glass = RelicId.GLASS_WITNESS.ordinal
    val scar = RelicId.SCAR_TISSUE.ordinal
    if (hasSynergy(SynergyId.WITNESS_TRANSFER) && enemy.relicTimers[glass] > 0f && enemy.relicTimers[scar] > 0f) {
        nearestOtherEnemy(enemy.x, enemy.y, enemy.id, 300f)?.let { target ->
            val transferred = 3f * relicRank(RelicId.SCAR_TISSUE) * enemy.relicCounters[scar].coerceIn(1, 5) * minOf(2f, enemy.relicTimers[scar]) * 0.5f
            addSynergyEffect(SynergyEffect(SynergyId.WITNESS_TRANSFER,
                SynergyEffectKind.DECAY, 2f, transferred / 2f, target.id))
            addRelicArc(enemy.x, enemy.y, target.x, target.y)
        }
    }
    if (hasSynergy(SynergyId.CHARGED_ANCHOR)) {
        val charges = enemy.relicCounters[RelicId.ION_DEBT.ordinal].coerceIn(0, 4)
        if (charges > 0) addSynergyEffect(SynergyEffect(
            SynergyId.CHARGED_ANCHOR, SynergyEffectKind.ANCHOR, 0.65f,
            18f * relicRank(RelicId.EVENTIDE_ANCHOR) + charges * 8f,
            x = enemy.x, y = enemy.y, radius = 140f,
        ))
    }
}

internal fun MutableGameState.onSynergyFracture(fromX: Float, fromY: Float, toX: Float, toY: Float) {
    if (hasSynergy(SynergyId.FRACTURE_DECAY)) addSynergyEffect(SynergyEffect(
        SynergyId.FRACTURE_DECAY, SynergyEffectKind.TRAIL, 1.8f, 5f,
        x = fromX, y = fromY, endX = toX, endY = toY, radius = 35f,
    ))
}

internal fun MutableGameState.rebaseSynergyEffects(shiftX: Float, shiftY: Float) {
    ghostDashStartX -= shiftX
    ghostDashStartY -= shiftY
    synergyEffects = synergyEffects.map { it.copy(x = it.x - shiftX, y = it.y - shiftY, endX = it.endX - shiftX, endY = it.endY - shiftY) }
}
