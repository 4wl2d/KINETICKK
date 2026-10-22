// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.SkillFamily
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.CombatFieldKind
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.FieldCell
import kinetickk.ball.gameplay.nucleus.model.FieldSource
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.SkillCell
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Skill balance bands (combat-final §10 Skills), on the character-balance fight: the Orb, the pointer tracing a
 * figure eight, a Dash every 1.25 to 1.75 s meeting packs of three Drifters set ahead of the aim, a Sniper bolt every
 * 3.2 to 4.3 s answered by a short brake (each interval drawn from the seed, so no cooldown keeps casting at the
 * same moment of the script's cycle), the Core held 200 below its maximum integrity (so a heal lands) and its level
 * frozen. One skill sits in slot Z and is requested every frame, so it casts whenever it can. Each skill and rank is
 * the mean of 96 seeded 60 s fights; a no-skill fight on each seed is the baseline.
 *
 * A skill whose rule needs a status already on its targets meets packs that arrive carrying it, and so does its
 * baseline: Frost Snap's shatter needs Frozen or Brittle enemies (the packs arrive as a Cryo Break leaves them),
 * Entropic Tide consumes Decay (they arrive with [SETUP_STACKS] Decay), Catalyze reacts enemies that carry two
 * elements (they arrive with [SETUP_STACKS] Thermal and Cryo stacks, set without a reaction).
 *
 * Family metrics: STRIKE damage per cooldown-second (skill damage over casts × cooldown); MOTION the Core's
 * momentum travel (distance at 700 u/s or more times its mass multiplier) while the skill moves it (the cast frame,
 * Arc Hook's flight, Slingshot's swing, Mass Driver's run) beyond the baseline's mean rate over the same frames, plus
 * skill damage; ZONE enemy-seconds inside the skill's field plus bolts it erased, an enemy that dies inside counting
 * for the field's remaining life; CATALYST reactions above the baseline; GUARD the share of the baseline's net
 * integrity loss (damage taken minus healing) the skill prevents.
 */
class SkillBalanceTest {
    private class Fight(
        val damage: Double,
        val casts: Int,
        val frames: Int,
        val momentum: Double,
        val windowMomentum: Double,
        val windowFrames: Int,
        val enemySeconds: Double,
        val erased: Int,
        val reactions: Int,
        val netLoss: Double,
    )

    /** The status a skill's rule needs on its targets before it acts. */
    private enum class Setup { NONE, FROZEN, DECAY, TWO_ELEMENTS }

    @Test
    fun everyFamilySitsWithinFortyPercentOfItsMedianAtRankOneAndRankThree() {
        val outside = mutableListOf<String>()
        for (rank in listOf(1, 3)) {
            for (family in SkillFamily.entries) {
                val members = SkillId.entries.filter { canonicalGameplayContent.skill(it).family == family }
                val metrics = members.associateWith { metric(it, rank) }
                if (family == SkillFamily.GUARD) {
                    println("SKILL-BALANCE rank=$rank $family " + metrics.entries.joinToString { (skill, cut) -> "$skill=${"%.3f".format(cut)}" })
                    metrics.forEach { (skill, cut) -> if (cut !in GUARD_CUT) outside += "$skill rank $rank cut ${"%.3f".format(cut)}" }
                    continue
                }
                val median = median(metrics.values)
                println("SKILL-BALANCE rank=$rank $family median=${"%.1f".format(median)} " +
                    metrics.entries.joinToString { (skill, value) -> "$skill=${"%.1f".format(value)} (${"%.2f".format(value / median)})" })
                metrics.forEach { (skill, value) ->
                    if (value !in median * 0.6..median * 1.4) outside += "$skill rank $rank ${"%.1f".format(value)} (median ${"%.1f".format(median)})"
                }
            }
        }
        assertTrue(outside.isEmpty(), "skills outside their family band: $outside")
    }

    @Test
    fun everySkillAtRankThreeOutdoesItsRankOneByAThird() {
        val short = SkillId.entries.mapNotNull { skill ->
            val one = metric(skill, 1)
            val three = metric(skill, 3)
            println("SKILL-RANKS $skill I=${"%.2f".format(one)} III=${"%.2f".format(three)} ratio=${"%.2f".format(three / one)}")
            if (three >= one * RANK_THREE_GAIN) null else "$skill ${"%.2f".format(three / one)}"
        }
        assertTrue(short.isEmpty(), "rank III below ${RANK_THREE_GAIN}x rank I: $short")
    }

    private fun metric(skill: SkillId, rank: Int): Double = metrics.getOrPut(skill to rank) {
        val definition = canonicalGameplayContent.skill(skill)
        val setup = setupOf(skill)
        val fights = SEEDS.map { seed -> fight(seed, skill, rank, setup) }
        val baselines = SEEDS.map(baselineFights(setup)::getValue)
        val cooldown = definition.rank(rank).cooldownSeconds.toDouble().coerceAtLeast(MIN_SKILL_RECAST_SECONDS.toDouble())
        when (definition.family) {
            SkillFamily.STRIKE -> fights.sumOf { it.damage } / fights.sumOf { it.casts * cooldown }.coerceAtLeast(1.0)
            SkillFamily.MOTION -> {
                // The arena's own momentum travel per frame: what the Core covers anyway while the skill moves it.
                val rate = baselines.sumOf { it.momentum } / baselines.sumOf { it.frames }.coerceAtLeast(1)
                fights.sumOf { it.windowMomentum - it.windowFrames * rate + it.damage } / fights.size
            }
            SkillFamily.ZONE -> fights.sumOf { it.enemySeconds + it.erased } / fights.size
            SkillFamily.CATALYST -> (fights.sumOf { it.reactions } - baselines.sumOf { it.reactions }).toDouble() / fights.size
            SkillFamily.GUARD -> 1.0 - fights.sumOf { it.netLoss } / baselines.sumOf { it.netLoss }.coerceAtLeast(1e-6)
        }
    }

    private companion object {
        val SEEDS = 4_241 until 4_241 + 96
        const val SECONDS = 60f
        const val KEPT_INTEGRITY = 1_000f
        /** The Core is held this far below its maximum integrity, so a heal has room to land and counts. */
        const val HEADROOM = 200f
        const val RANK_THREE_GAIN = 1.35
        val GUARD_CUT = 0.15..0.60
        const val FAST_SPEED = 700f
        /** Stacks a setup pack arrives with (the older element of a reaction needs two). */
        const val SETUP_STACKS = 3
        val content = canonicalGameplayContent.copy(tempo = canonicalGameplayContent.tempo.copy(bossAtSeconds = 1_000_000f))
        val metrics = HashMap<Pair<SkillId, Int>, Double>()
        val baselines = HashMap<Setup, Map<Int, Fight>>()

        fun setupOf(skill: SkillId): Setup = when (skill) {
            SkillId.FROST_SNAP -> Setup.FROZEN
            SkillId.ENTROPIC_TIDE -> Setup.DECAY
            SkillId.CATALYZE -> Setup.TWO_ELEMENTS
            else -> Setup.NONE
        }

        fun baselineFights(setup: Setup): Map<Int, Fight> = baselines.getOrPut(setup) {
            SEEDS.associateWith { seed -> fight(seed, null, 1, setup) }
        }

        fun median(values: Collection<Double>): Double {
            val sorted = values.sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
        }

        fun fight(seed: Int, skill: SkillId?, rank: Int, setup: Setup): Fight {
            val state = MutableGameState(content, seed = seed)
            state.applyPreferences(state.settings.copy(simulationSpeed = 1f))
            state.resize(1_280f, 720f)
            state.coreShape = CoreShape.ORB
            state.startRun()
            state.enemies.clear()
            state.nextEliteAt = Float.MAX_VALUE
            state.nextLevelData = Int.MAX_VALUE
            state.maxHp = KEPT_INTEGRITY
            if (skill != null) {
                state.acquireSkill(skill)
                repeat(rank - 1) { state.rankUpSkill(skill) }
            }
            val script = Script(seed, setup)
            val runtime = state.skillRuntime
            var netLoss = 0.0
            var momentum = 0.0
            var windowMomentum = 0.0
            var windowFrames = 0
            var enemySeconds = 0.0
            var erased = 0
            var reactions = 0
            var frame = 0
            while (state.elapsed < SECONDS && state.phase == GamePhase.RUNNING) {
                state.spawnBudget = 0f
                state.overdriveCharge = 0f
                state.overdriveTime = 0f
                state.weaponLevel = 1
                state.runGrace = 1f
                state.pointsOfInterest = emptyList()
                script(state, frame)
                if (skill != null) state.requestSkill(0)
                state.hp = state.maxHp - HEADROOM
                val casts = runtime[0, SkillCell.CAST_COUNT]
                // The skill moves the Core while Arc Hook flies, Slingshot swings or Mass Driver runs.
                val moving = skill != null &&
                    (runtime[0, SkillCell.TARGET_UNTIL] > state.elapsed || runtime[0, SkillCell.ACTIVE_UNTIL] > state.elapsed)
                val x = state.coreX
                val y = state.coreY
                val charge = fieldCharge(state)
                val inside = enemiesInField(state)
                state.update(1f / 60f)
                netLoss += state.maxHp - HEADROOM - state.hp
                // A frame's travel at 700 u/s or more counts, a teleport (Void Step) included, times the mass multiplier.
                val moved = hypot(state.coreX - x, state.coreY - y)
                val travel = if (moved * 60f >= FAST_SPEED) moved * state.effectiveMass() / state.mass else 0f
                momentum += travel
                if (moving || runtime[0, SkillCell.CAST_COUNT] > casts) {
                    windowMomentum += travel
                    windowFrames++
                }
                erased += (fieldCharge(state) - charge).coerceAtLeast(0)
                enemySeconds += countInField(state) / 60.0
                // An occupant the field killed stays controlled for the rest of the field's life.
                if (inside.isNotEmpty()) enemySeconds += inside.count { !it.alive } * fieldRemaining(state)
                reactions += state.takeVisualFxCues().count { it is VisualFxCue.ReactionTriggered }
                state.takeSoundCues()
                state.takeProgressUpdate()
                frame++
            }
            return Fight(
                damage = if (skill != null) runtime[0, SkillCell.DAMAGE].toDouble() else 0.0,
                casts = if (skill != null) runtime[0, SkillCell.CAST_COUNT].toInt() else 0,
                frames = frame,
                momentum = momentum,
                windowMomentum = windowMomentum,
                windowFrames = windowFrames,
                enemySeconds = enemySeconds,
                erased = erased,
                reactions = reactions,
                netLoss = netLoss,
            )
        }

        /** Bolts the slot-Z field has erased so far (a Frost Line counts them). */
        fun fieldCharge(state: MutableGameState): Int {
            val table = state.combatFieldTable
            for (index in 0 until table.count) {
                if (table.source(index) == FieldSource.SKILL && table.owner(index) == 0 && table.kind(index) == CombatFieldKind.FROST_LINE) {
                    return table[index, FieldCell.CHARGE].toInt()
                }
            }
            return 0
        }

        /** The slot-Z field's index, or -1. */
        fun slotField(state: MutableGameState): Int {
            val table = state.combatFieldTable
            for (index in 0 until table.count) {
                if (table.source(index) == FieldSource.SKILL && table.owner(index) == 0) return index
            }
            return -1
        }

        /** Live enemies inside slot Z's field right now. */
        fun enemiesInField(state: MutableGameState): List<Enemy> {
            val index = slotField(state)
            if (index < 0) return emptyList()
            return state.enemies.filter { it.alive && state.fieldContains(index, it.x, it.y, it.radius) }
        }

        fun countInField(state: MutableGameState): Int {
            val index = slotField(state)
            if (index < 0) return 0
            return state.enemies.count { it.alive && state.fieldContains(index, it.x, it.y, it.radius) }
        }

        /** Seconds slot Z's field still lives (0 when it is gone). */
        fun fieldRemaining(state: MutableGameState): Float {
            val index = slotField(state)
            return if (index < 0) 0f else state.combatFieldTable[index, FieldCell.REMAINING]
        }

        val DASH_FRAMES = 75 until 106
        const val SHOT_FRAMES_FIRST = 112
        val SHOT_FRAMES = 190 until 261
        const val SHOT_SPEED = 400f
        const val PACK_BEARING_SPREAD = 1f
        const val PACK_NEAR = 140f
        const val PACK_FAR = 280f
    }

    /**
     * The character-balance script with seeded timing: figure-eight pointer, Dashes into packs (carrying the
     * skill's [setup]), Sniper bolts.
     */
    private class Script(seed: Int, private val setup: Setup) : (MutableGameState, Int) -> Unit {
        private val random = Random(seed)
        private var nextDash = 90
        private var nextShot = SHOT_FRAMES_FIRST
        private var brakeFrom = Int.MIN_VALUE
        private var brakeUntil = Int.MIN_VALUE

        override fun invoke(state: MutableGameState, frame: Int) {
            val angle = frame / 60f * 1.4f
            state.updatePointer(state.screenWidth * 0.5f + cos(angle) * 300f, state.screenHeight * 0.5f + sin(angle * 2f) * 220f)
            if (frame == nextDash) {
                nextDash += random.nextInt(DASH_FRAMES.first, DASH_FRAMES.last + 1)
                state.requestDash()
                val aim = atan2(state.cameraY + state.pointerY - state.screenHeight * 0.5f - state.coreY,
                    state.cameraX + state.pointerX - state.screenWidth * 0.5f - state.coreX)
                val bearing = aim + (random.nextFloat() * 2f - 1f) * PACK_BEARING_SPREAD
                val distance = PACK_NEAR + random.nextFloat() * (PACK_FAR - PACK_NEAR)
                repeat(3) { index ->
                    val direction = bearing + (index - 1) * 0.35f
                    val enemy = state.addEnemyForTesting(state.coreX + cos(direction) * distance, state.coreY + sin(direction) * distance,
                        hp = if (index == 1) 240f else 60f)
                    arrive(state, enemy)
                }
                while (state.enemies.size > 12) state.enemies.removeAt(0)
            }
            if (frame == nextShot) {
                nextShot += random.nextInt(SHOT_FRAMES.first, SHOT_FRAMES.last + 1)
                val speed = hypot(state.velocityX, state.velocityY)
                val heading = if (speed > 1f) atan2(state.velocityY, state.velocityX) else frame * 0.37f
                val x = state.coreX + cos(heading) * 150f
                val y = state.coreY + sin(heading) * 150f
                val damage = state.hostileDamage(null, EnemyType.SNIPER.profile.shotDamage)
                state.projectiles += Projectile(x = x, y = y, vx = -cos(heading) * SHOT_SPEED, vy = -sin(heading) * SHOT_SPEED,
                    radius = 6f, life = 2f, hostile = true, damage = damage).apply { previousX = x; previousY = y }
                brakeFrom = frame + 5
                brakeUntil = brakeFrom + 9
            }
            state.setBrake(frame in brakeFrom..brakeUntil)
        }

        /** A pack enemy arrives carrying [setup]. */
        private fun arrive(state: MutableGameState, enemy: Enemy) {
            when (setup) {
                Setup.NONE -> Unit
                // As a Cryo Break leaves it: Frozen for the Break's time, then Brittle.
                Setup.FROZEN -> state.freeze(enemy, min(2f, state.content.build.element(StatusElement.CRYO).breakPower))
                Setup.DECAY -> state.applyStatus(enemy, StatusElement.ENTROPY, SETUP_STACKS, 0)
                // Both written directly: landing one on the other would react on arrival.
                Setup.TWO_ELEMENTS -> for (element in listOf(StatusElement.THERMAL, StatusElement.CRYO)) {
                    enemy.status[StatusCell.STACKS + element.ordinal] = SETUP_STACKS.toFloat()
                    enemy.status[StatusCell.DECAY_AT + element.ordinal] = state.elapsed + state.decaySeconds(element)
                }
            }
        }
    }
}
