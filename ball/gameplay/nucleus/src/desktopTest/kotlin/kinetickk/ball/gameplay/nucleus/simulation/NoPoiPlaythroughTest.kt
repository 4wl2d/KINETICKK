// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.*
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

/**
 * Real input-only runs of a fresh profile. The controller can inspect current threats, but cannot alter simulation
 * state.
 *
 * Both rules are population gates (lead Addendum C5, 0.5.0 active items) over [PopulationSeeds]: on the 05885fa9 base
 * the reference seed 2026 alone won as the reference does with probability 0.53, and the ten further probe seeds
 * reached five POI-free wins with probability 0.70, so both re-rolled with every change to the reward stream. Each
 * threshold is the base share minus two standard errors, rounded down. The reference seed is still flown twice to show
 * the run is deterministic, and every choice of every run keeps the loadout invariant.
 */
class NoPoiPlaythroughTest {
    /**
     * A fresh run wins as the reference did: VICTORY within 720-900 s with the Architect arriving at 720 s, every POI
     * skipped, no completed orbit pending, the foundation at 180 s (weapon level 3, three items, a Relic slot) and the
     * opportunity at 360 s (four Elites seen, four Relic slots), and weapon level 10 with four Relics, two of them
     * ranked, at the end. The base share over the population was 158 of 300 (0.527, SE 0.029; 44 base wins touched a
     * POI); at least [MIN_REFERENCE_SHARE] must win so.
     */
    @Test fun freshProfilesWinNoPoiRunsWithinTwelveToFifteenActiveMinutes() {
        java.io.File(System.getProperty("java.io.tmpdir"), "kinetickk-nopoi-evidence.txt").writeText("")
        val replays = List(2) { attempt -> fly(REFERENCE_SEED, reportAttempt = attempt + 1) }
        assertEquals(replays[0], replays[1], "the reference seed replays identically")
        val share = population.shareOf { it.winsLikeTheReference }
        report("NOPOI REFERENCE population ${population.count { it.winsLikeTheReference }} of ${population.size} (${"%.3f".format(share)}) " +
            "reference seed ${replays[0].summary()}")
        assertTrue(share >= MIN_REFERENCE_SHARE,
            "only ${"%.3f".format(share)} of ${population.size} fresh runs won as the reference does; expected at least $MIN_REFERENCE_SHARE")
    }

    /**
     * The reference win is not a knife-edge seed: other fresh-profile runs win without a POI. Only a victory that
     * skipped every POI is a no-POI win (the bot's avoidance is a heuristic), and each one lasts 720-900 s and ends
     * with four Relics. The base share over the population was 160 of 300 (0.533, SE 0.029); at least
     * [MIN_NO_POI_WIN_SHARE] must win so. Earlier, on the ten fixed probe seeds: 6 wins after wave A, 7 after the
     * second wave, all 10 after the final weapon balance (floor 5).
     */
    @Test fun freshProfileAlsoWinsFurtherReferenceNoPoiSeeds() {
        population.filter { it.noPoiWin }.forEach { run ->
            assertTrue(run.seconds in 720f..900f, "seed=${run.seed} activeSeconds=${run.seconds}")
            assertEquals(4, run.relics.size, "seed=${run.seed}")
        }
        val share = population.shareOf { it.noPoiWin }
        report("NOPOI FURTHER population ${population.count { it.noPoiWin }} of ${population.size} (${"%.3f".format(share)})")
        assertTrue(share >= MIN_NO_POI_WIN_SHARE,
            "only ${"%.3f".format(share)} of ${population.size} fresh runs won without a POI; expected at least $MIN_NO_POI_WIN_SHARE")
    }

    private companion object {
        const val REFERENCE_SEED = 2026

        /** The least share of [PopulationSeeds] runs that win as the reference does (base 0.527, SE 0.029). */
        const val MIN_REFERENCE_SHARE = 0.46

        /** The least share of [PopulationSeeds] runs that win without a POI (base 0.533, SE 0.029). */
        const val MIN_NO_POI_WIN_SHARE = 0.47

        /** One fresh run per [PopulationSeeds] seed, shared by both rules. */
        val population: List<NoPoiRun> by lazy { populationOf(PopulationSeeds) { seed -> fly(seed) } }

        /**
         * One fresh run on [seed]; [reportAttempt] (the reference replays) also writes its minutes to the evidence
         * file.
         */
        fun fly(seed: Int, reportAttempt: Int? = null): NoPoiRun {
            if (reportAttempt != null) report("REFERENCE attempt=$reportAttempt seed=$seed simulationSpeed=1.0 inputHz=50 freshLab=true")
            val state = MutableGameState(canonicalGameplayContent, seed = seed)
            state.unlockWeaponsForTesting(ReferenceArmory)
            state.applyPreferences(state.settings.copy(simulationSpeed = 1f))
            state.startRun()
            assertEquals(1f, state.settings.simulationSpeed)
            assertEquals(CoreShape.ORB, state.coreShape)
            assertEquals(WeaponId.FLUX_WAKE, state.weapon)
            assertTrue((0 until state.matrixRanks.size).all { state.matrixRanks[it] == 0 })
            assertEquals(0, state.rebirthLevel)
            val bot = NoPoiBot()
            var minute = 0
            var bossObservedAt: Float? = null
            var foundation: Milestone? = null
            var fourSlotOpportunity: Milestone? = null
            val observedEliteIds = mutableSetOf<Int>()
            var poiEngaged = false
            var previousHealth = state.hp
            var damage = 0f
            while (state.elapsed < 900f && (state.phase == GamePhase.RUNNING || state.phase == GamePhase.CHOICE)) {
                if (state.phase == GamePhase.CHOICE) {
                    bot.choose(state)
                    // Active items are the only writers of the slots: every slot's rank is its item's stacks.
                    assertNull(state.activeLoadoutMismatch(), "seed=$seed sec=${state.elapsed}")
                } else {
                    bot.control(state)
                    state.update(0.02f)
                }
                state.enemies.filter { it.type == EnemyType.ELITE }.forEach { observedEliteIds += it.id }
                if (state.bossSpawned && bossObservedAt == null) bossObservedAt = state.elapsed
                if (reportAttempt != null && !poiEngaged && state.pointsOfInterest.any { it.active }) {
                    report("POI ACTIVATED sec=${state.elapsed} core=${state.coreX.toInt()},${state.coreY.toInt()} speed=${state.speed.toInt()} points=${state.pointsOfInterest.map { "${it.kind}@${it.x.toInt()},${it.y.toInt()}" }} relics=${state.equippedRelics}")
                }
                poiEngaged = poiEngaged || state.pointsOfInterest.any { it.active } ||
                    state.directedReward != null || state.pendingDirectedRewards.isNotEmpty()
                if (state.elapsed >= 180f && foundation == null) foundation = milestone(state, observedEliteIds.size)
                if (state.elapsed >= 360f && fourSlotOpportunity == null) fourSlotOpportunity = milestone(state, observedEliteIds.size)
                damage += max(0f, previousHealth - state.hp)
                previousHealth = state.hp
                state.takeSoundCues()
                state.takeVisualFxCues()
                if (reportAttempt != null && state.elapsed >= minute * 60f) {
                    report("NOPOI seed=$seed sec=${state.elapsed.toInt()} hp=${state.hp.toInt()}/${state.maxHp.toInt()} shield=${state.shield.toInt()} speed=${state.speed.toInt()} level=${state.level} weapon=${state.weapon}/${state.weaponLevel} kills=${state.kills} items=${state.acquiredItemCount} actives=${state.skillLoadout} relics=${state.equippedRelics} boss=${state.enemies.firstOrNull { it.type == EnemyType.ARCHITECT }?.hp?.toInt()} damage=${damage.toInt()}")
                    minute++
                }
            }
            val run = NoPoiRun(seed, state.phase, state.elapsed, state.message, state.hp, state.level, state.weapon, state.weaponLevel,
                state.kills, state.itemStacks.indices.map { state.itemStacks[it] }, state.equippedRelics.toList(), bossObservedAt,
                poiEngaged, state.pendingCompletedOrbits, foundation, fourSlotOpportunity)
            if (reportAttempt != null) report("NOPOI FINAL seed=$seed sec=${state.elapsed} phase=${state.phase} reason=${state.message} hp=${state.hp} kills=${state.kills} level=${state.level} weapon=${state.weapon}/${state.weaponLevel} damage=$damage bossAt=$bossObservedAt completedOrbits=${state.pendingCompletedOrbits} poi=$poiEngaged foundation=$foundation sixMinutes=$fourSlotOpportunity")
            return run
        }
    }
}

/** One fresh no-POI run as the rules read it: its end, the Architect's arrival, POI contact and the two milestones. */
private data class NoPoiRun(
    val seed: Int, val phase: GamePhase, val seconds: Float, val reason: String, val remainingHp: Float, val level: Int,
    val weapon: WeaponId, val weaponLevel: Int, val kills: Int, val itemStacks: List<Int>, val relics: List<EquippedRelic>,
    val bossObservedAt: Float?, val poiEngaged: Boolean, val pendingCompletedOrbits: Int,
    val foundation: Milestone?, val sixMinutes: Milestone?,
) {
    /** A victory that skipped every POI. */
    val noPoiWin: Boolean get() = phase == GamePhase.VICTORY && !poiEngaged

    /** The reference rule: a POI-free victory in 720-900 s that reached both milestones and the late build. */
    val winsLikeTheReference: Boolean get() = noPoiWin && seconds in 720f..900f && bossObservedAt?.let { it in 720f..720.05f } == true &&
        pendingCompletedOrbits == 0 &&
        foundation?.let { it.weaponLevel >= 3 && it.items >= 3 && it.relicSlots >= 1 } == true &&
        sixMinutes?.let { it.eliteOpportunities >= 4 && it.relicSlots == 4 } == true &&
        weaponLevel >= 10 && relics.size == 4 && relics.count { it.rank > 1 } >= 2

    fun summary(): String = "$seed:$phase@${seconds.toInt()}s(L$level $reason)"
}

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
/** How far the bot keeps the momentum-projected Core from a skipped POI's center. */
private const val POI_CLEARANCE = 360f

/** The bot looks at its skills this often (simulated seconds). */
private const val SKILL_CHECK_SECONDS = 0.1f

/** The bot's Apex rule: a crowd of this many enemies within this radius, or integrity below this share. */
private const val APEX_CROWD = 8
private const val APEX_CROWD_RADIUS = 400f
private const val APEX_LOW_INTEGRITY = 0.4f
/** Caps of margin above the Architect's phase gate, beyond the activation's strikes, the bot wants before casting. */
private const val APEX_GATE_MARGIN_STRIKES = 2
/** Mirror Stance: the bot brakes while a hostile shot, or an ordinary enemy, is this close. */
private const val MIRROR_SHOT_REACH = 160f
private const val MIRROR_CONTACT_REACH = 120f

internal class NoPoiBot(
    private val archetypeTag: BuildTag = BuildTag.RAM,
    private val element: StatusElement? = StatusElement.ENTROPY,
    private val weaponOrder: List<WeaponId> = listOf(WeaponId.ARC_COIL, WeaponId.ION_SWARM, WeaponId.ENTROPY_FIELD, WeaponId.PRISM_RELAY),
    /** An impact-driven player avoids liabilities that cut impacts, healing, integrity or steering. */
    private val relicTaste: Map<RelicId, Float> = mapOf(
        RelicId.ECHO_CHAMBER to -40f,
        RelicId.GLASS_COMET to -20f,
        RelicId.VERDANT_ROT to -20f,
        RelicId.PERPETUAL_VECTOR to -20f,
        RelicId.AEGIS_INVERSION to -20f,
        // Its arc weapons cannot crit, so the Prism would only cost 20 %.
        RelicId.EXECUTIONERS_PRISM to -20f,
        // It takes steady chip damage, which Stormheart turns into self-discharges.
        RelicId.STORMHEART_CAPACITOR to -20f,
        // It never manages heat, so the Covenant's cooling cut keeps it above 80 heat, bleeding integrity.
        RelicId.FURNACE_COVENANT to -20f,
    ),
    /** How the bot treats Data-gain cards (economy §2.1); [DataPolicy.NEUTRAL] plays exactly as the reference bot. */
    private val dataPolicy: DataPolicy = DataPolicy.NEUTRAL,
    /**
     * A setup measured without key harmony: the bot leaves a Relic slot hollow rather than bind a Relic that carries
     * the equipped weapon's key. Off, the bot plays exactly as the reference bot.
     */
    private val avoidWeaponHarmony: Boolean = false,
) {
    private var cursorAngle = 0f
    private var braking = false
    private var decisions = 0
    private var nextSkillCheckAt = 0f

    /** An independent bot in this bot's current state (a forked run continues with it). */
    fun copy(): NoPoiBot = NoPoiBot(archetypeTag, element, weaponOrder, relicTaste, dataPolicy, avoidWeaponHarmony).also { copy ->
        copy.cursorAngle = cursorAngle
        copy.braking = braking
        copy.decisions = decisions
        copy.nextSkillCheckAt = nextSkillCheckAt
        copy.avoidBreaches = avoidBreaches
    }

    /** [DataPolicy.AVOID] only: offers of nothing but Data-gain cards with no reroll left, so one had to be taken. */
    var avoidBreaches = 0
        private set

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
            // Steer from where momentum carries the Core, so low-drag builds still clear the circle.
            val px = s.coreX + s.velocityX * 0.35f - point.x
            val py = s.coreY + s.velocityY * 0.35f - point.y
            val pd = max(1f, length(px, py))
            if (pd < POI_CLEARANCE) {
                desiredX += px / pd * (POI_CLEARANCE - pd) * 8f
                desiredY += py / pd * (POI_CLEARANCE - pd) * 8f
            }
        }
        // Steer around obstacles ahead, from where momentum carries the Core (a player reading the minimap).
        for (obstacle in s.obstacleField.obstacles) {
            val px = s.coreX + s.velocityX * 0.3f - obstacle.x
            val py = s.coreY + s.velocityY * 0.3f - obstacle.y
            val clearance = obstacle.radius + s.coreRadius + 110f
            val pd = max(1f, length(px, py))
            if (pd < clearance) {
                desiredX += px / pd * (clearance - pd) * 6f
                desiredY += py / pd * (clearance - pd) * 6f
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
        // Mirror Stance parries only while the Core brakes: while it runs, the bot brakes as hostile shots or enemies close in.
        val mirrorBrake = s.coreShape == CoreShape.DIAMOND && s.apexActive() && (s.projectiles.any {
            it.hostile && !it.spent && distanceSquared(it.x, it.y, s.coreX, s.coreY) <= MIRROR_SHOT_REACH * MIRROR_SHOT_REACH
        } || s.enemies.any {
            it.alive && !it.isBossClass && distanceSquared(it.x, it.y, s.coreX, s.coreY) <= MIRROR_CONTACT_REACH * MIRROR_CONTACT_REACH
        })
        s.setBrake(braking || mirrorBrake)
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
        // A Blink teleports along the cursor, so never Dash when the landing would touch a skipped POI.
        val blinkLandsOnPoi = s.relicRank(RelicId.BLINK_DRIVE) > 0 && s.pointsOfInterest.any { point ->
            segmentCircleIntersects(s.coreX, s.coreY, s.coreX + ux * 320f, s.coreY + uy * 320f, point.x, point.y, 150f)
        }
        // Vivisect's free Dashes and Total Eclipse's fast Blinks are those Apexes: while one runs, the bot Dashes when it can.
        val apexDashes = s.apexActive() && (s.coreShape == CoreShape.SHARD || s.coreShape == CoreShape.CRESCENT)
        if ((threat || apexDashes) && s.dashReady && s.heat < 60f && !blinkLandsOnPoi) s.requestDash()
        castSkills(s)
    }

    /**
     * The opportunistic caster of combat-final §4.10: every 0.1 s of simulated time each ready skill is cast when
     * its situation holds, and a ready Apex when the Architect is in the open, a crowd presses or integrity runs
     * low. The bot never moves its pointer to aim; area skills land where it already points.
     */
    private fun castSkills(s: MutableGameState) {
        if (s.elapsed < nextSkillCheckAt) return
        nextSkillCheckAt = s.elapsed + SKILL_CHECK_SECONDS
        for (slot in 0 until SkillRuntime.SLOTS) {
            val id = s.skillLoadout.skill(slot) ?: continue
            if (s.elapsed < s.skillRuntime[slot, SkillCell.READY_AT]) continue
            if (wantsCast(s, id, s.content.skill(id).rank(s.skillLoadout.rank(slot)).values)) s.requestSkill(slot)
        }
        if (wantsApex(s)) s.requestApex()
    }

    private fun wantsApex(s: MutableGameState): Boolean {
        if (!s.apexReady()) return false
        val low = s.hp < s.maxHp * APEX_LOW_INTEGRITY
        val architect = s.enemies.firstOrNull { it.type == EnemyType.ARCHITECT && it.alive }
        if (architect != null) return low || apexLands(s, architect)
        val crowd = s.enemies.count { it.alive && distanceSquared(s.coreX, s.coreY, it.x, it.y) <= APEX_CROWD_RADIUS * APEX_CROWD_RADIUS }
        return crowd >= APEX_CROWD || low
    }

    /**
     * A cast at the Architect lands its boss channel (lead addendum B1): the Architect is out of its shift, within the
     * Apex reach of the Core (where it just arrived it is still far off), and in its last phase or far enough above
     * its phase gate that the next shift does not cut the activation's strikes short.
     */
    private fun apexLands(s: MutableGameState, architect: Enemy): Boolean {
        if (s.isShifting(architect)) return false
        val reach = APEX_BOSS_REACH + architect.radius
        if (distanceSquared(s.coreX, s.coreY, architect.x, architect.y) > reach * reach) return false
        if (architect.phase >= s.architectFinalPhase()) return true
        val strikes = s.apexDefinition.bossStrikes + APEX_GATE_MARGIN_STRIKES
        return architect.hp - s.architectGate(architect) >= strikes * s.architectHitCap(architect)
    }

    private fun wantsCast(s: MutableGameState, id: SkillId, values: List<Float>): Boolean {
        val singularityX = s.cameraX + s.pointerX - s.screenWidth * 0.5f
        val singularityY = s.cameraY + s.pointerY - s.screenHeight * 0.5f
        val toPointer = length(singularityX - s.coreX, singularityY - s.coreY)
        val aimX = if (toPointer > 24f) (singularityX - s.coreX) / toPointer else s.lastAimDirectionX
        val aimY = if (toPointer > 24f) (singularityY - s.coreY) / toPointer else s.lastAimDirectionY
        val definition = s.content.skill(id)
        val reach = min(toPointer, definition.reach)
        val anchorX = if (toPointer > 24f) s.coreX + aimX * reach else s.coreX
        val anchorY = if (toPointer > 24f) s.coreY + aimY * reach else s.coreY
        fun enemiesWithin(x: Float, y: Float, radius: Float) =
            s.enemies.count { it.alive && distanceSquared(x, y, it.x, it.y) <= (radius + it.radius) * (radius + it.radius) }
        fun boltsWithin(radius: Float) = s.projectiles.count { it.hostile && !it.spent && distanceSquared(s.coreX, s.coreY, it.x, it.y) <= radius * radius }
        fun inCone(x: Float, y: Float, range: Float): Boolean {
            val dx = x - s.coreX
            val dy = y - s.coreY
            val distance = length(dx, dy)
            return distance <= range && (distance < 1f || (dx * aimX + dy * aimY) / distance >= cos(35f * PI.toFloat() / 180f))
        }
        val boss = s.enemies.firstOrNull { it.type == EnemyType.ARCHITECT && it.alive }
        return when (id) {
            SkillId.SOLAR_FLARE -> s.heat >= 40f && enemiesWithin(s.coreX, s.coreY, values[1]) >= 3
            SkillId.FROST_SNAP, SkillId.FULL_STOP -> enemiesWithin(s.coreX, s.coreY, values[0]) >= 3 ||
                boss != null && length(boss.x - s.coreX, boss.y - s.coreY) <= values[0] + 100f
            SkillId.ENTROPIC_TIDE -> s.hp < s.maxHp * 0.5f || s.enemies.filter {
                it.alive && length(it.x - s.coreX, it.y - s.coreY) <= values[0] + it.radius
            }.sumOf { it.stacksOf(StatusElement.ENTROPY) } >= 12
            SkillId.NULL_SHROUD -> boltsWithin(250f) >= 4
            SkillId.MASS_DRIVER, SkillId.DUAL_INFUSION -> enemiesWithin(s.coreX, s.coreY, 400f) >= 3
            SkillId.METEOR, SkillId.STORM_COIL, SkillId.GRAVITY_WELL, SkillId.DECAY_SPORES, SkillId.RESONATOR ->
                enemiesWithin(anchorX, anchorY, values[0]) >= 2
            // Eligible as the cast reads it: two elements react, one element takes the partner's stacks.
            SkillId.CATALYZE -> s.enemies.count { enemy ->
                enemy.alive && length(enemy.x - anchorX, enemy.y - anchorY) <= values[0] + enemy.radius &&
                    StatusElement.entries.any { enemy.stacksOf(it) > 0 }
            } >= 3
            SkillId.FROST_LINE -> {
                val half = values[0] * 0.5f
                val centreX = s.coreX + aimX * 160f
                val centreY = s.coreY + aimY * 160f
                val x0 = centreX - aimY * half
                val y0 = centreY + aimX * half
                val x1 = centreX + aimY * half
                val y1 = centreY - aimX * half
                s.enemies.count { it.alive && segmentCircleIntersects(x0, y0, x1, y1, it.x, it.y, it.radius + values[4]) } >= 2 ||
                    s.projectiles.count { it.hostile && !it.spent && segmentCircleIntersects(x0, y0, x1, y1, it.x, it.y, values[4]) } >= 4
            }
            SkillId.SONIC_BOOM -> s.enemies.count { it.alive && inCone(it.x, it.y, values[0] + it.radius) } >= 2 ||
                s.projectiles.count { it.hostile && !it.spent && inCone(it.x, it.y, values[0]) } >= 4
            SkillId.VOID_STEP -> s.projectiles.any { shot ->
                shot.hostile && !shot.spent && length(shot.x - s.coreX, shot.y - s.coreY) <= 140f &&
                    (shot.vx - s.velocityX) * (s.coreX - shot.x) + (shot.vy - s.velocityY) * (s.coreY - shot.y) > 0f
            }
            // The hook throws the Core at its target, so (like a Blink) never along a line that touches a skipped POI.
            SkillId.ARC_HOOK -> s.nearestSkillTarget(singularityX, singularityY, values[0])?.let { target ->
                s.pointsOfInterest.none { point ->
                    segmentCircleIntersects(s.coreX, s.coreY, target.x, target.y, point.x, point.y, 150f)
                }
            } == true
            SkillId.SLINGSHOT -> s.speed >= 500f && (s.velocityX * aimX + s.velocityY * aimY) / s.speed <= 0f
        }
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
        val choices = s.choices
        if (s.choiceType == ChoiceType.ACTIVE_SLOT) {
            decisions++
            s.choose(slotChoice(s))
            return
        }
        // SEEK takes a Data active first (economy §2.1); every policy takes a rank-up of an owned active next.
        val index = seekActiveChoice(s) ?: activeRankUpChoice(s) ?: run {
            when (dataPolicy) {
                DataPolicy.NEUTRAL -> Unit
                // An item offer without a Data card is rerolled while a rank-0 Data item can still stack; the
                // bot decides again on the next tick.
                DataPolicy.SEEK -> if (
                    s.choiceType == ChoiceType.ITEM && choices.none { isDataGainCard(s, it) } && s.choicesCanReroll &&
                    s.content.items.any { it.minimumRebirth == 0 && it.isDataEconomy && s.itemStacks[it.id] < it.maxStacks }
                ) {
                    s.rerollChoices()
                    return
                }
                DataPolicy.AVOID -> if (choices.isNotEmpty() && choices.all { isDataGainCard(s, it) }) {
                    if (s.choicesCanReroll) {
                        s.rerollChoices()
                        return
                    }
                    // Every card gains Data: take the one that gains the least.
                    avoidBreaches++
                    val least = choices.indices.minBy { dataWeight(s, choices[it]) }
                    decisions++
                    s.choose(least)
                    return
                }
            }
            // SEEK's Data cards outrank a new active; under the other policies a new active comes first.
            val dataCard = dataPolicy == DataPolicy.SEEK && choices.any { isDataGainCard(s, it) }
            (if (dataCard) null else newActiveChoice(s)) ?: choices.indices.maxByOrNull { policyScore(s, choices[it], it) }
        } ?: return
        decisions++
        s.choose(index)
    }

    /** SEEK ranks a Data-gain card by its Data weight above every other card; AVOID scores it −5 000. */
    private fun policyScore(s: MutableGameState, c: ChoiceOption, index: Int): Float = when {
        // An active the bot would not take (it would only salvage, or AVOID's Decay Spores) is a last resort.
        activeEffect(s, c) != null && activeEffect(s, c) !in wantedActives(s) -> -10_000f
        dataPolicy == DataPolicy.NEUTRAL || !isDataGainCard(s, c) -> score(s, c, index)
        dataPolicy == DataPolicy.SEEK -> dataWeight(s, c)
        else -> -5_000f
    }

    /** `5 000 + 1 000 × ΣDATA_GAIN + 500 × [has GainData]` for a Data-gain item card (economy §2.1). */
    private fun dataWeight(s: MutableGameState, c: ChoiceOption): Float {
        val item = c.itemId?.let(s.content::item) ?: return 0f
        val dataGain = item.stats.filter { it.effect == ItemEffect.DATA_GAIN && it.amount > 0f }.sumOf { it.amount.toDouble() }
        val gainsData = item.clauses.any { clause -> clause.actions.any { it is EffectAction.GainData } }
        return 5_000f + 1_000f * dataGain.toFloat() + if (gainsData) 500f else 0f
    }

    private fun isDataGainCard(s: MutableGameState, c: ChoiceOption): Boolean =
        c.itemId?.let(s.content::item)?.isDataEconomy == true

    /*
     * Active items (combat-final §4.10, R1.8): a rank-up of an owned active is always taken; a new active is taken while
     * a slot is free; with every slot full only a character-lane active is taken, to replace a rank-I active outside the
     * lane, and any other new active is passed over for the best other card. SEEK takes Decay Spores (Spored deaths drop
     * Data), else Gravity Well (it pulls Data in), whether to bind, rank or replace the lowest-rank non-Data active, before
     * any Data card; AVOID never takes Decay Spores.
     */

    private fun activeEffect(s: MutableGameState, c: ChoiceOption): SkillId? =
        if (c.type == ChoiceType.ITEM) c.itemId?.let(s.content::item)?.active else null

    /** The actives this bot takes now: owned ones (rank-ups), any while a slot is free, else lane actives with a slot to replace. */
    private fun wantedActives(s: MutableGameState): Set<SkillId> = SkillId.entries.filterTo(HashSet()) { effect ->
        when {
            dataPolicy == DataPolicy.AVOID && effect == SkillId.DECAY_SPORES -> false
            dataPolicy == DataPolicy.SEEK && seekSkillWeight(effect) > 0f -> true
            s.skillLoadout.owns(effect) -> true
            s.skillLoadout.lowestEmptySlot() >= 0 -> true
            else -> effect in lane(s) && laneReplacementSlot(s) >= 0
        }
    }

    /** SEEK: the offered Data active with the highest SEEK weight, or null. */
    private fun seekActiveChoice(s: MutableGameState): Int? {
        if (dataPolicy != DataPolicy.SEEK) return null
        val seek = s.choices.indices.filter { seekSkillWeight(activeEffect(s, s.choices[it])) > 0f }
        return seek.maxByOrNull { seekSkillWeight(activeEffect(s, s.choices[it])) }
    }

    /** A card ranking up an owned active (AVOID still never Decay Spores), or null. */
    private fun activeRankUpChoice(s: MutableGameState): Int? = s.choices.indices.firstOrNull { index ->
        val effect = activeEffect(s, s.choices[index]) ?: return@firstOrNull false
        s.skillLoadout.owns(effect) && effect in wantedActives(s)
    }

    /** A card with a new active the bot takes now (a free slot, or a lane active replacing a rank-I one), or null. */
    private fun newActiveChoice(s: MutableGameState): Int? = s.choices.indices.firstOrNull { index ->
        val effect = activeEffect(s, s.choices[index]) ?: return@firstOrNull false
        !s.skillLoadout.owns(effect) && effect in wantedActives(s)
    }

    /** The slot prompt: SEEK replaces the lowest-rank non-Data active for a Data active; a lane active replaces a rank-I one outside the lane; else keep. */
    private fun slotChoice(s: MutableGameState): Int {
        val keep = s.choices.indexOfFirst { it.activeSlot == null }
        val incoming = s.pendingActiveItemId?.let(s.content::item)?.active
        val slot = if (dataPolicy == DataPolicy.SEEK && seekSkillWeight(incoming) > 0f) {
            (0 until SkillRuntime.SLOTS).filter { seekSkillWeight(s.skillLoadout.skill(it)) <= 0f }
                .minByOrNull { s.skillLoadout.rank(it) } ?: -1
        } else if (incoming != null && incoming in lane(s)) {
            laneReplacementSlot(s)
        } else -1
        return s.choices.indexOfFirst { it.activeSlot == slot }.takeIf { slot >= 0 && it >= 0 } ?: keep
    }

    private fun lane(s: MutableGameState): List<SkillId> = s.content.coreShape(s.coreShape).activeLane

    /** The first slot holding a rank-I active outside the character's lane, or -1. */
    private fun laneReplacementSlot(s: MutableGameState): Int = (0 until SkillRuntime.SLOTS).firstOrNull { slot ->
        val held = s.skillLoadout.skill(slot)
        held != null && held !in lane(s) && s.skillLoadout.rank(slot) == 1
    } ?: -1

    /** SEEK's active weights (economy §2.1): Decay Spores 5 000, Gravity Well 4 000, any other active 0. */
    private fun seekSkillWeight(id: SkillId?): Float = when (id) {
        SkillId.DECAY_SPORES -> 5_000f
        SkillId.GRAVITY_WELL -> 4_000f
        else -> 0f
    }

    /**
     * A committed Ram and Entropy build: archetype and element items first, survival stats second,
     * then whatever already has points. Relics fill empty slots, then rank up through duplicates and
     * Meld. A small deterministic spread stands in for a player's taste between equal cards.
     */
    private fun score(s: MutableGameState, c: ChoiceOption, index: Int): Float {
        val full = s.equippedRelics.size >= s.content.relicPolicy.maxSlots
        val spread = ((decisions * 31 + index * 7) % 7).toFloat()
        if (c.relicAction == RelicChoiceAction.DECLINE) return -50f
        if (c.relicAction == RelicChoiceAction.MELD) return if (full) 40f else -40f
        if (c.relicAction == RelicChoiceAction.MELD_TARGET) {
            val relic = s.equippedRelics[c.relicSlot ?: return -1000f]
            return if (s.canMeld(relic.id)) 60f - relic.rank * 10f + spread else -1000f
        }
        if (c.relicAction == RelicChoiceAction.REPLACE) return -10f - (s.equippedRelics.getOrNull(c.relicSlot ?: 0)?.rank ?: 0)
        if (c.totemAction == TotemAction.CHANGE_WEAPON) return if (s.weapon !in weaponOrder) 1_000f else -1f
        if (c.totemAction == TotemAction.AMPLIFY_CURRENT) return 100f
        c.weaponId?.let { weapon ->
            val rank = weaponOrder.indexOf(weapon)
            return if (rank >= 0) 1_000f - rank * 100f else 10f
        }
        c.itemId?.let { id ->
            val item = s.content.item(id) ?: return 0f
            // The reference flight steers around obstacles and seldom throws enemies into them: ricochet and slam
            // items would sit nearly idle in this build.
            if (item.clauses.any { it.trigger == EffectTrigger.RICOCHET || it.trigger == EffectTrigger.SLAM }) return -100f
            // Nor does it cash marks, seek out fields or mark and throw on purpose (items.md §6.2).
            val first = item.clauses.firstOrNull()
            if (first != null && (first.trigger == EffectTrigger.MARK_CONSUMED || first.trigger == EffectTrigger.FIELD_ENTER ||
                    first.actions.first().let { it is EffectAction.ApplyMark || it is EffectAction.Throw })) return -100f
            val archetype = (if (archetypeTag in item.tags) 50f else 0f) + (if (element != null && element in item.elements) 50f else 0f)
            val survival = item.stats.sumOf { stat ->
                when (stat.effect) {
                    ItemEffect.REGEN -> stat.amount * 30f
                    // A first shield matters most; the larger pool offers fewer of them.
                    ItemEffect.SHIELD_CAPACITY -> stat.amount * if (s.shieldCapacity() <= 0f) 2f else 1f
                    ItemEffect.MAX_INTEGRITY -> stat.amount * 0.6f
                    else -> 0f
                }.toDouble()
            }.toFloat()
            val committed = item.tags.sumOf { tag -> s.build.tagPoints(tag) * 2 }
            return archetype + item.rarity.rank * 3f + spread + survival + committed
        }
        c.relicId?.let { id ->
            if (avoidWeaponHarmony && s.currentWeaponDefinition.key in s.content.relic(id).keys) return -100f
            return when {
                s.relicRank(id) > 0 -> if (full) 50f else 15f + spread
                full -> -20f
                else -> 40f + spread + (relicTaste[id] ?: 0f)
            }
        }
        return 0f
    }
}

/** How a bot treats Data-gain cards: as any other card, first ([SEEK], with rerolls) or last ([AVOID]). */
internal enum class DataPolicy { NEUTRAL, SEEK, AVOID }

/**
 * The reference run owns the original twelve weapons, so its Totem recalibrations deal exactly the
 * offers they did before weapon offers were limited to owned weapons (a profile that owns only the
 * starter can amplify but never recalibrate).
 */
internal val ReferenceArmory: List<WeaponId> = WeaponId.entries.take(12)
