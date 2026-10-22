// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.SkillFamily
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.gameplay.nucleus.model.CombatFieldKind
import kinetickk.ball.gameplay.nucleus.model.FieldCell
import kinetickk.ball.gameplay.nucleus.model.FieldSource
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.SkillCell
import kinetickk.ball.gameplay.nucleus.model.profile
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Skill balance bands (combat-final §10 Skills), on the character-balance fight: the Orb, the pointer tracing a
 * figure eight, a Dash every 1.5 s meeting packs of three Drifters set ahead of the aim, a Sniper bolt every
 * 3.75 s answered by a short brake, the Core kept alive and its level frozen. One skill sits in slot Z and is
 * requested every frame, so it casts whenever it can. Each skill and rank is the mean of 24 seeded 60 s fights;
 * a no-skill fight on each seed is the baseline for the catalyst and guard families.
 *
 * Family metrics: STRIKE damage per cooldown-second (skill damage over casts × cooldown); MOTION distance the Core
 * covers at 700 u/s or more beyond the no-skill fight's, plus skill damage; ZONE enemy-seconds inside the skill's field plus bolts it erased;
 * CATALYST reactions above the baseline; GUARD the share of the baseline's integrity loss the skill prevents.
 */
class SkillBalanceTest {
    private class Fight(val damage: Double, val casts: Int, val fastDistance: Double, val enemySeconds: Double,
        val erased: Int, val reactions: Int, val hpLost: Double)

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
        val fights = SEEDS.map { seed -> fight(seed, skill, rank) }
        val baselines = SEEDS.map(baselineFights::getValue)
        val cooldown = definition.rank(rank).cooldownSeconds.toDouble().coerceAtLeast(MIN_SKILL_RECAST_SECONDS.toDouble())
        when (definition.family) {
            SkillFamily.STRIKE -> fights.sumOf { it.damage } / fights.sumOf { it.casts * cooldown }.coerceAtLeast(1.0)
            SkillFamily.MOTION -> (fights.sumOf { it.fastDistance + it.damage } - baselines.sumOf { it.fastDistance }) / fights.size
            SkillFamily.ZONE -> fights.sumOf { it.enemySeconds + it.erased } / fights.size
            SkillFamily.CATALYST -> (fights.sumOf { it.reactions } - baselines.sumOf { it.reactions }).toDouble() / fights.size
            SkillFamily.GUARD -> 1.0 - fights.sumOf { it.hpLost } / baselines.sumOf { it.hpLost }.coerceAtLeast(1e-6)
        }
    }

    private companion object {
        val SEEDS = 4_241..4_264
        const val SECONDS = 60f
        const val KEPT_INTEGRITY = 1_000f
        const val RANK_THREE_GAIN = 1.35
        val GUARD_CUT = 0.15..0.60
        const val FAST_SPEED = 700f
        val content = canonicalGameplayContent.copy(tempo = canonicalGameplayContent.tempo.copy(bossAtSeconds = 1_000_000f))
        val metrics = HashMap<Pair<SkillId, Int>, Double>()
        val baselineFights: Map<Int, Fight> by lazy { SEEDS.associateWith { seed -> fight(seed, null, 1) } }

        fun median(values: Collection<Double>): Double {
            val sorted = values.sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
        }

        fun fight(seed: Int, skill: SkillId?, rank: Int): Fight {
            val state = MutableGameState(content, seed = seed)
            state.applyPreferences(state.settings.copy(simulationSpeed = 1f))
            state.resize(1_280f, 720f)
            state.coreShape = CoreShape.ORB
            state.startRun()
            state.enemies.clear()
            state.nextEliteAt = Float.MAX_VALUE
            state.nextLevelData = Int.MAX_VALUE
            state.maxHp = KEPT_INTEGRITY
            state.hp = state.maxHp
            if (skill != null) {
                state.acquireSkill(skill)
                repeat(rank - 1) { state.rankUpSkill(skill) }
            }
            val script = Script(seed)
            var hpLost = 0.0
            var fastDistance = 0.0
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
                val before = state.hp
                val x = state.coreX
                val y = state.coreY
                val charge = fieldCharge(state)
                state.update(1f / 60f)
                hpLost += (before - state.hp).coerceAtLeast(0f)
                state.hp = state.maxHp
                // A frame's travel at 700 u/s or more counts, a teleport (Void Step) included.
                val moved = hypot(state.coreX - x, state.coreY - y)
                if (moved * 60f >= FAST_SPEED) fastDistance += moved
                erased += (fieldCharge(state) - charge).coerceAtLeast(0)
                enemySeconds += enemiesInField(state) / 60.0
                reactions += state.takeVisualFxCues().count { it is VisualFxCue.ReactionTriggered }
                state.takeSoundCues()
                state.takeProgressUpdate()
                frame++
            }
            val runtime = state.skillRuntime
            return Fight(
                damage = if (skill != null) runtime[0, SkillCell.DAMAGE].toDouble() else 0.0,
                casts = if (skill != null) runtime[0, SkillCell.CAST_COUNT].toInt() else 0,
                fastDistance = fastDistance,
                enemySeconds = enemySeconds,
                erased = erased,
                reactions = reactions,
                hpLost = hpLost,
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

        /** Enemies inside slot Z's field right now. */
        fun enemiesInField(state: MutableGameState): Int {
            val table = state.combatFieldTable
            for (index in 0 until table.count) {
                if (table.source(index) != FieldSource.SKILL || table.owner(index) != 0) continue
                return state.enemies.count { it.alive && state.fieldContains(index, it.x, it.y, it.radius) }
            }
            return 0
        }

        const val DASH_FRAMES = 90
        const val SHOT_FRAMES = 225
        const val SHOT_SPEED = 400f
        const val PACK_BEARING_SPREAD = 1f
        const val PACK_NEAR = 140f
        const val PACK_FAR = 280f
    }

    /** The character-balance script with exact timing: figure-eight pointer, Dashes into packs, Sniper bolts. */
    private class Script(seed: Int) : (MutableGameState, Int) -> Unit {
        private val random = Random(seed)
        private var nextDash = DASH_FRAMES
        private var nextShot = SHOT_FRAMES / 2
        private var brakeFrom = Int.MIN_VALUE
        private var brakeUntil = Int.MIN_VALUE

        override fun invoke(state: MutableGameState, frame: Int) {
            val angle = frame / 60f * 1.4f
            state.updatePointer(state.screenWidth * 0.5f + cos(angle) * 300f, state.screenHeight * 0.5f + sin(angle * 2f) * 220f)
            if (frame == nextDash) {
                nextDash += DASH_FRAMES
                state.requestDash()
                val aim = atan2(state.cameraY + state.pointerY - state.screenHeight * 0.5f - state.coreY,
                    state.cameraX + state.pointerX - state.screenWidth * 0.5f - state.coreX)
                val bearing = aim + (random.nextFloat() * 2f - 1f) * PACK_BEARING_SPREAD
                val distance = PACK_NEAR + random.nextFloat() * (PACK_FAR - PACK_NEAR)
                repeat(3) { index ->
                    val direction = bearing + (index - 1) * 0.35f
                    state.addEnemyForTesting(state.coreX + cos(direction) * distance, state.coreY + sin(direction) * distance,
                        hp = if (index == 1) 240f else 60f)
                }
                while (state.enemies.size > 12) state.enemies.removeAt(0)
            }
            if (frame == nextShot) {
                nextShot += SHOT_FRAMES
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
    }
}
