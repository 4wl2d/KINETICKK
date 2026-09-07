// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class NoPoiPlaythroughTest {
    /** Real input-only runs. The controller can inspect current threats, but cannot alter simulation state. */
    @Test fun freshProfileWinsReferenceNoPoiRunWithinTwelveToFifteenActiveMinutes() {
        java.io.File(System.getProperty("java.io.tmpdir"), "kinetickk-nopoi-evidence.txt").writeText("")
        var firstOutcome: RunOutcome? = null
        repeat(2) { attempt ->
            val seed = 2026
            report("REFERENCE attempt=${attempt + 1} seed=$seed simulationSpeed=1.0 inputHz=50 freshLab=true")
            val state = MutableGameState(canonicalGameplayContent, seed = seed)
            state.applyPreferences(state.settings.copy(simulationSpeed = 1f))
            state.startRun()
            assertEquals(1f, state.settings.simulationSpeed)
            assertEquals(CoreShape.ORB, state.coreShape)
            assertEquals(WeaponId.FLUX_WAKE, state.weapon)
            assertTrue(MetaUpgradeId.entries.all { state.metaLevel(it) == 0 })
            assertEquals(0, state.rebirthLevel)
            val bot = NoPoiBot()
            var minute = 0
            var bossObservedAt: Float? = null
            var foundation: Milestone? = null
            var fourSlotOpportunity: Milestone? = null
            val observedEliteIds = mutableSetOf<Int>()
            var activePoiObserved = false
            var poiRewardObserved = false
            var previousHealth = state.hp
            var damage = 0f
            while (state.elapsed < 900f && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
                if (state.phase == GamePhase.CHOICE) bot.choose(state) else {
                    bot.control(state)
                    state.update(0.02f)
                }
                state.enemies.filter { it.type == EnemyType.ELITE }.forEach { observedEliteIds += it.id }
                if (state.bossSpawned && bossObservedAt == null) bossObservedAt = state.elapsed
                activePoiObserved = activePoiObserved || state.pointsOfInterest.any { it.active }
                poiRewardObserved = poiRewardObserved || state.directedReward != null || state.pendingDirectedRewards.isNotEmpty()
                if (state.elapsed >= 180f && foundation == null) foundation = milestone(state, observedEliteIds.size)
                if (state.elapsed >= 360f && fourSlotOpportunity == null) fourSlotOpportunity = milestone(state, observedEliteIds.size)
                damage += max(0f, previousHealth - state.hp)
                previousHealth = state.hp
                state.takeSoundCues()
                state.takeVisualFxCues()
                if (state.elapsed >= minute * 60f) {
                    report("NOPOI seed=$seed sec=${state.elapsed.toInt()} hp=${state.hp.toInt()}/${state.maxHp.toInt()} shield=${state.shield.toInt()} speed=${state.speed.toInt()} level=${state.level} weapon=${state.weapon}/${state.weaponLevel} kills=${state.kills} items=${state.acquiredItemCount} relics=${state.equippedRelics} boss=${state.enemies.firstOrNull { it.type == EnemyType.ARCHITECT }?.hp?.toInt()} damage=${damage.toInt()}")
                    minute++
                }
            }
            report("NOPOI FINAL seed=$seed sec=${state.elapsed} phase=${state.phase} reason=${state.message} hp=${state.hp} kills=${state.kills} level=${state.level} weapon=${state.weapon}/${state.weaponLevel} damage=$damage bossAt=$bossObservedAt completedOrbits=${state.pendingCompletedOrbits} activePoi=$activePoiObserved poiReward=$poiRewardObserved foundation=$foundation sixMinutes=$fourSlotOpportunity")
            assertEquals(GamePhase.VICTORY, state.phase, "seed=$seed ${state.message}")
            assertTrue(state.elapsed in 720f..900f, "seed=$seed activeSeconds=${state.elapsed}")
            assertTrue(assertNotNull(bossObservedAt) in 720f..720.05f)
            assertTrue(!activePoiObserved && !poiRewardObserved, "seed=$seed must skip every POI")
            assertEquals(0, state.pendingCompletedOrbits)
            assertTrue(assertNotNull(foundation).weaponLevel >= 3 && foundation.items >= 3 && foundation.relicSlots >= 1)
            assertTrue(assertNotNull(fourSlotOpportunity).eliteOpportunities >= 4)
            assertEquals(4, fourSlotOpportunity.relicSlots)
            assertTrue(state.weaponLevel >= 10 && state.equippedRelics.size == 4)
            assertTrue(state.equippedRelics.count { it.rank > 1 } >= 2)
            val outcome = RunOutcome(state.elapsed, state.hp, state.level, state.weapon, state.weaponLevel,
                state.kills, state.itemStacks.indices.map { state.itemStacks[it] }, state.equippedRelics.toList(),
                assertNotNull(foundation), assertNotNull(fourSlotOpportunity))
            if (firstOutcome == null) firstOutcome = outcome else assertEquals(firstOutcome, outcome)
        }
    }
}

private data class RunOutcome(
    val activeSeconds: Float, val remainingHp: Float, val level: Int, val weapon: WeaponId,
    val weaponLevel: Int, val kills: Int, val itemStacks: List<Int>, val relics: List<EquippedRelic>,
    val foundation: Milestone, val sixMinutes: Milestone,
)

private data class Milestone(val level: Int, val weaponLevel: Int, val items: Int, val relicSlots: Int, val eliteOpportunities: Int)
private fun milestone(state: MutableGameState, eliteOpportunities: Int) = Milestone(
    state.level, state.weaponLevel, state.acquiredItemCount, state.equippedRelics.size, eliteOpportunities,
)

private fun report(value: String) {
    println(value)
    java.io.File(System.getProperty("java.io.tmpdir"), "kinetickk-nopoi-evidence.txt").appendText(value + "\n")
}

/**
 * Test-only controller: hunts nearby targets, picks up earned loot and uses the existing choice UI.
 * Steering uses a short approximate projectile forecast; all actual motion/damage goes through update().
 * Each 20 ms input is a normal pointer/Brake/Dash action, independent of the configured simulation speed.
 */
private class NoPoiBot {
    private var cursorAngle = 0f
    private var braking = false

    fun control(s: MutableGameState) {
        val boss = s.enemies.firstOrNull { it.type == EnemyType.ARCHITECT }
        var targetX = 0f
        var targetY = 0f
        var radius = 300f
        val targetEnemy = s.enemies.filter { !it.dead && it.hp > 0f }.minByOrNull {
            length(it.x - s.coreX, it.y - s.coreY) - if (it.type == EnemyType.ELITE) 250f else 0f
        }
        if (targetEnemy != null) {
            targetX = targetEnemy.x + targetEnemy.vx * 0.3f
            targetY = targetEnemy.y + targetEnemy.vy * 0.3f
            radius = if (s.weapon in listOf(WeaponId.ARC_COIL, WeaponId.ION_SWARM, WeaponId.PRISM_RELAY)) 280f else 0f
        }
        val totem = s.totem
        val pickup = s.pickups.filter { it.type == PickupType.KEY || it.type == PickupType.RELIC || it.type == PickupType.REPAIR && s.hp < s.maxHp - 15f || it.type == PickupType.DATA && distanceSquared(s.coreX, s.coreY, it.x, it.y) < 180f * 180f }
            .minByOrNull { distanceSquared(s.coreX, s.coreY, it.x, it.y) }
        if (boss != null) {
            targetX = boss.x; targetY = boss.y
            radius = if (s.weapon == WeaponId.FLUX_WAKE) 100f else 300f
        } else if (totem != null && s.keys > 0) {
            targetX = totem.x; targetY = totem.y; radius = 5f
        } else if (pickup != null) {
            targetX = pickup.x; targetY = pickup.y; radius = 10f
        }
        val dx = s.coreX - targetX
        val dy = s.coreY - targetY
        val dist = max(1f, length(dx, dy))
        val nx = if (dist > 2f) dx / dist else 1f
        val ny = if (dist > 2f) dy / dist else 0f
        val orbitSpeed = if (boss != null && s.weapon == WeaponId.FLUX_WAKE) 350f else 460f
        val radial = ((radius - dist) * 2.4f).coerceIn(-480f, 480f)
        var desiredX = -ny * orbitSpeed + nx * radial
        var desiredY = nx * orbitSpeed + ny * radial
        if (radius < 50f) {
            desiredX = -nx * 650f
            desiredY = -ny * 650f
        }
        // Keep voluntarily skipped POI activation circles outside the intended path.
        for (point in s.pointsOfInterest) {
            val px = s.coreX - point.x
            val py = s.coreY - point.y
            val pd = max(1f, length(px, py))
            if (pd < 220f) {
                desiredX += px / pd * (220f - pd) * 7f
                desiredY += py / pd * (220f - pd) * 7f
            }
        }
        for (shot in s.projectiles) if (shot.hostile) {
            val px = shot.x - s.coreX
            val py = shot.y - s.coreY
            val vx = shot.vx - s.velocityX
            val vy = shot.vy - s.velocityY
            val time = -(px * vx + py * vy) / max(1f, vx * vx + vy * vy)
            if (time in 0f..0.45f && length(px + vx * time, py + vy * time) < 80f) {
                val length = max(1f, length(vx, vy))
                val side = if (px * -vy + py * vx > 0f) -1f else 1f
                desiredX += -vy / length * side * 380f
                desiredY += vx / length * side * 380f
            }
        }
        val ax = (desiredX - s.velocityX) * 5f + s.velocityX * s.dragCoefficient
        val ay = (desiredY - s.velocityY) * 5f + s.velocityY * s.dragCoefficient
        val desiredAngle = atan2(ay, ax)
        val difference = atan2(sin(desiredAngle - cursorAngle), cos(desiredAngle - cursorAngle))
        val goalAngle = cursorAngle + difference.coerceIn(-0.30f, 0.30f)
        val force = length(ax, ay).coerceIn(395f, 1700f)
        var bestAngle = goalAngle
        var bestScore = Float.POSITIVE_INFINITY
        val nearbyShots = s.projectiles.filter { it.hostile && distanceSquared(it.x, it.y, s.coreX, s.coreY) < 450f * 450f }
        for (offset in listOf(0f, -0.3f, 0.3f, -0.6f, 0.6f, -1f, 1f)) {
            val option = goalAngle + offset
            val score = steeringScore(s, option, force, desiredX, desiredY, nearbyShots)
            if (score < bestScore) { bestScore = score; bestAngle = option }
        }
        val selectedDifference = atan2(sin(bestAngle - cursorAngle), cos(bestAngle - cursorAngle))
        cursorAngle += selectedDifference.coerceIn(-0.45f, 0.45f)
        val offsetX = s.coreX - s.cameraX
        val offsetY = s.coreY - s.cameraY
        val ux = cos(cursorAngle)
        val uy = sin(cursorAngle)
        // Ordinary steering now recovers polarity; keep the cursor inside that area.
        val normalHalfWidth = s.screenWidth * 0.5f * s.content.tempo.fatigue.edgeStrainStart - 10f
        val normalHalfHeight = s.screenHeight * 0.5f * s.content.tempo.fatigue.edgeStrainStart - 10f
        val availableX = if (ux > 0f) (normalHalfWidth - offsetX) / ux else if (ux < 0f) (-normalHalfWidth - offsetX) / ux else 10_000f
        val availableY = if (uy > 0f) (normalHalfHeight - offsetY) / uy else if (uy < 0f) (-normalHalfHeight - offsetY) / uy else 10_000f
        val neededTether = ((length(ax, ay) - 92f) / s.magnetStrength).coerceIn(65f, 400f)
        val tether = min(neededTether, min(availableX, availableY)).coerceAtLeast(65f)
        s.updatePointer(s.screenWidth * 0.5f + offsetX + ux * tether, s.screenHeight * 0.5f + offsetY + uy * tether)
        if (s.speed > 900f) braking = true
        if (s.speed < 600f) braking = false
        s.setBrake(braking)
        val threat = s.projectiles.any {
            if (!it.hostile) false else {
                val px = it.x - s.coreX
                val py = it.y - s.coreY
                val vx = it.vx - s.velocityX
                val vy = it.vy - s.velocityY
                val time = -(px * vx + py * vy) / max(1f, vx * vx + vy * vy)
                time in 0f..0.20f && length(px + vx * time, py + vy * time) < 32f
            }
        }
        if (threat && s.dashReady && s.heat < 60f) s.requestDash()
    }

    private fun steeringScore(s: MutableGameState, angle: Float, force: Float, desiredX: Float, desiredY: Float, shots: List<Projectile>): Float {
        var x = s.coreX
        var y = s.coreY
        var vx = s.velocityX
        var vy = s.velocityY
        var penalty = 0f
        val ux = cos(angle)
        val uy = sin(angle)
        repeat(6) { sample ->
            val speed = max(1f, length(vx, vy))
            val forward = max(0f, (ux * vx + uy * vy) / speed * force) * (1f - s.tetherAuthority)
            vx += (ux * force - vx / speed * forward) * 0.05f
            vy += (uy * force - vy / speed * forward) * 0.05f
            vx *= exp(-s.dragCoefficient * 0.05f)
            vy *= exp(-s.dragCoefficient * 0.05f)
            x += vx * 0.05f
            y += vy * 0.05f
            val time = (sample + 1) * 0.05f
            for (shot in shots) {
                val distance = length(x - shot.x - shot.vx * time, y - shot.y - shot.vy * time)
                if (distance < 90f) penalty += (90f - distance) * (90f - distance) * if (distance < 30f) 30f else 0.3f
            }
        }
        return penalty + ((vx - desiredX) * (vx - desiredX) + (vy - desiredY) * (vy - desiredY)) * 0.005f
    }

    fun choose(s: MutableGameState) {
        val index = s.choices.indices.maxByOrNull { score(s, s.choices[it]) } ?: return
        s.choose(index)
    }
    private fun score(s: MutableGameState, c: ChoiceOption): Float {
        if (c.relicAction == RelicChoiceAction.MELD) return 500f
        if (c.relicAction == RelicChoiceAction.MELD_TARGET) {
            val relic = s.equippedRelics[c.relicSlot ?: return -1000f]
            return if (relic.rank >= s.content.relicPolicy.maxRank) -1000f else 500f - relic.rank * 100f
        }
        if (c.totemAction == TotemAction.CHANGE_WEAPON) return if (s.weapon !in listOf(WeaponId.ARC_COIL, WeaponId.ION_SWARM, WeaponId.ENTROPY_FIELD, WeaponId.PRISM_RELAY)) 1_000f else -1f
        if (c.totemAction == TotemAction.AMPLIFY_CURRENT) return 100f
        c.weaponId?.let { return when(it) { WeaponId.ARC_COIL -> 1000f; WeaponId.ION_SWARM -> 900f; WeaponId.ENTROPY_FIELD -> 800f; WeaponId.PRISM_RELAY -> 700f; else -> 10f } }
        c.itemId?.let { id -> return s.content.item(id)?.let { value(it.primary) + value(it.secondary) } ?: 0f }
        c.relicId?.let { id -> return when(id) {
            RelicId.DEVOURERS_TOLL -> 100f; RelicId.CHROMA_FEEDBACK -> 90f; RelicId.STATIC_CHORUS -> 80f; RelicId.VOLTAIC_FILAMENT -> 75f; RelicId.ION_DEBT -> 70f; RelicId.ECHO_CHAMBER -> 65f; else -> 20f
        } }
        return 0f
    }
    private fun value(m: ItemModifier): Float = when(m.effect) {
        ItemEffect.REGEN -> m.amount * 120f
        ItemEffect.SHIELD_CAPACITY -> m.amount * 4f
        ItemEffect.MAX_INTEGRITY -> m.amount * 2f
        ItemEffect.DAMAGE_REDUCTION -> m.amount * 250f
        ItemEffect.WEAPON_POWER -> m.amount * 150f
        ItemEffect.ATTACK_SPEED -> m.amount * 140f
        ItemEffect.PICKUP_RADIUS -> m.amount * 0.7f
        ItemEffect.DATA_GAIN -> m.amount * 120f
        else -> m.amount * 50f
    }
}
