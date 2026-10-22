// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.nucleus.model.CombatField
import kinetickk.ball.gameplay.nucleus.model.CombatFieldKind
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.FieldCell
import kinetickk.ball.gameplay.nucleus.model.FieldSource
import kinetickk.ball.gameplay.nucleus.model.MarkKind
import kinetickk.ball.gameplay.nucleus.model.ObstacleField
import kinetickk.ball.gameplay.nucleus.model.SkillCell
import kinetickk.ball.gameplay.nucleus.model.SkillLoadout
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.impactTaken
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.reducer.EngineState
import kinetickk.ball.gameplay.nucleus.reducer.GameReducer
import kinetickk.ball.gameplay.nucleus.reducer.GameReductionResult
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.SkillBarProjection
import kinetickk.ball.gameplay.nucleus.render.SkillSlotProjection
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SkillSystemTest {
    @Test
    fun everySkillAndRankCastsThroughOnePipelineAndSpendsItsRankCooldown() {
        for (id in SkillId.entries) for (rank in 1..ContentBounds.MAX_SKILL_RANK) {
            val state = arena()
            val slot = state.learn(id, rank)
            if (id == SkillId.ARC_HOOK) state.dummy(x = state.coreX + 200f, y = state.coreY)
            val cooldown = max(MIN_SKILL_RECAST_SECONDS, state.content.skill(id).rank(rank).cooldownSeconds)

            assertTrue(state.castSkill(slot), "$id $rank")
            val runtime = state.skillRuntime
            assertEquals(cooldown, runtime[slot, SkillCell.LAST_COOLDOWN], "$id $rank cooldown")
            assertEquals(state.elapsed + cooldown, runtime[slot, SkillCell.READY_AT], "$id $rank ready")
            assertEquals(1f, runtime[slot, SkillCell.CAST_COUNT])
            val cue = state.takeVisualFxCues().filterIsInstance<VisualFxCue.SkillCast>().first()
            assertEquals(id, cue.skill)
            assertEquals(rank, cue.rank)
            assertTrue(GameplayAudioCue.SKILL in state.takeSoundCues(), "$id $rank sound")

            // A second press while cooling casts nothing and spends nothing; the slot shows why.
            assertFalse(state.castSkill(slot))
            assertEquals(state.elapsed + cooldown, runtime[slot, SkillCell.READY_AT])
            assertEquals(SkillSlotProjection.REFUSAL_COOLING, runtime[slot, SkillCell.REFUSAL].toInt())
        }
    }

    @Test
    fun everySkillAppliesItsRankNumbers() {
        for (rank in 1..ContentBounds.MAX_SKILL_RANK) {
            solarFlare(rank)
            meteor(rank)
            frostLine(rank)
            frostSnap(rank)
            arcHook(rank)
            stormCoil(rank)
            gravityWell(rank)
            massDriver(rank)
            decaySpores(rank)
            entropicTide(rank)
            sonicBoom(rank)
            resonator(rank)
            voidStep(rank)
            nullShroud(rank)
            slingshot(rank)
            fullStop(rank)
            catalyze(rank)
            dualInfusion(rank)
        }
    }

    private fun solarFlare(rank: Int) {
        val state = arena()
        val slot = state.learn(SkillId.SOLAR_FLARE, rank)
        val values = state.valuesOf(SkillId.SOLAR_FLARE)
        state.heat = 60f
        val near = state.dummy(x = state.coreX + 100f, y = state.coreY)
        val far = state.dummy(x = state.coreX + 700f, y = state.coreY)
        val power = state.skillPower()
        state.castSkill(slot)
        val vented = min(60f, values[0])
        assertEquals(60f - vented, state.heat, 1e-4f, "rank $rank vented heat")
        assertEquals((values[2] + values[3] * vented) * power, near.maxHp - near.hp, DAMAGE_TOLERANCE, "rank $rank flare damage")
        assertEquals(min(6, values[4].toInt() + floor(vented / 15f).toInt()), near.stacksOf(StatusElement.THERMAL), "rank $rank Scorch")
        assertEquals(far.maxHp, far.hp)
    }

    private fun meteor(rank: Int) {
        val state = arena()
        val slot = state.learn(SkillId.METEOR, rank)
        val values = state.valuesOf(SkillId.METEOR)
        state.aimAt(state.coreX + 300f, state.coreY)
        val target = state.dummy(x = state.coreX + 300f, y = state.coreY).apply { disabledUntil = 1_000f }
        state.castSkill(slot)
        val runtime = state.skillRuntime
        assertEquals(state.elapsed + values[4], runtime[slot, SkillCell.PENDING_AT], 1e-5f)
        assertEquals(state.coreX + 300f, runtime[slot, SkillCell.ANCHOR_X], 1e-3f)
        assertEquals(target.maxHp, target.hp, "nothing lands before the delay")
        state.elapsed = runtime[slot, SkillCell.PENDING_AT]
        val power = state.skillPower()
        state.updateSkills(0f)
        assertEquals(values[1] * power, target.maxHp - target.hp, DAMAGE_TOLERANCE, "rank $rank landing damage")
        assertEquals(values[2].toInt(), target.stacksOf(StatusElement.THERMAL))
        assertTrue(target.thrownUntil > state.elapsed, "the landing throws")
        assertEquals(-1f, runtime[slot, SkillCell.PENDING_AT])
        assertEquals(0f, state.speed, "a Core outside the blast does not ride it")
    }

    private fun frostLine(rank: Int) {
        val state = arena()
        val slot = state.learn(SkillId.FROST_LINE, rank)
        val values = state.valuesOf(SkillId.FROST_LINE)
        state.aimAt(state.coreX + 400f, state.coreY)
        state.castSkill(slot)
        val field = state.fieldRecords.single()
        assertEquals(CombatFieldKind.FROST_LINE, field.kind)
        assertEquals(FieldSource.SKILL, field.source)
        assertEquals(slot, field.owner)
        assertEquals(values[4], field.radius)
        assertEquals(values[1], field.remaining)
        assertEquals(values[0], length(field.x2 - field.x, field.y2 - field.y), 1e-2f, "rank $rank length")
        assertEquals(state.coreX + FROST_LINE_AHEAD, (field.x + field.x2) * 0.5f, 1e-3f, "centred ahead of the Core")
        assertEquals(field.x, field.x2, 1e-3f, "perpendicular to the aim")
        assertEquals(values[2], field.power, "bolt cap")
        val onLine = state.dummy(x = state.coreX + FROST_LINE_AHEAD, y = state.coreY + 30f).apply { disabledUntil = 1_000f }
        state.updateCombatFields(CombatField.TICK_SECONDS)
        assertEquals(values[3].toInt(), onLine.stacksOf(StatusElement.CRYO), "rank $rank Chill per tick")
        assertEquals(rank == 3, onLine.status[StatusCell.EMBRITTLED_UNTIL] > state.elapsed, "rank III embrittles")
    }

    private fun frostSnap(rank: Int) {
        val state = arena()
        val slot = state.learn(SkillId.FROST_SNAP, rank)
        val values = state.valuesOf(SkillId.FROST_SNAP)
        val brittle = state.dummy(x = state.coreX + 60f, y = state.coreY).apply { status[StatusCell.BRITTLE] = 1f }
        val plain = state.dummy(x = state.coreX - 60f, y = state.coreY)
        val shatter = state.impactPower(600f) * values[2]
        state.castSkill(slot)
        assertEquals(shatter * brittle.impactTaken, brittle.maxHp - brittle.hp, shatter * 1e-3f, "rank $rank shatter")
        // The shatter consumes Brittle; rank III's Freeze then leaves the target Brittle again.
        if (rank == 3) assertTrue(state.isFrozen(brittle)) else assertEquals(0f, brittle.status[StatusCell.BRITTLE], "the shatter consumes Brittle")
        assertEquals(plain.maxHp, plain.hp, "an enemy neither Frozen nor Brittle takes no shatter")
        // Rank III Freezes what the Chill brings within two stacks of the threshold (4 Chill against 6).
        if (rank == 3) assertTrue(state.isFrozen(plain)) else assertEquals(values[1].toInt(), plain.stacksOf(StatusElement.CRYO))
        assertEquals(state.elapsed + values[4], state.skillRuntime[slot, SkillCell.AUX_UNTIL], "coiled Dash window")
        assertTrue(state.dashCoiled())
        assertEquals(values[3], state.consumeCoiledDash(), "rank $rank coiled push")
        assertFalse(state.dashCoiled(), "one Dash spends the coil")
    }

    private fun arcHook(rank: Int) {
        val state = arena()
        val slot = state.learn(SkillId.ARC_HOOK, rank)
        val values = state.valuesOf(SkillId.ARC_HOOK)
        state.aimAt(state.coreX + 300f, state.coreY)
        val target = state.dummy(x = state.coreX + 200f, y = state.coreY)
        val linked = state.dummy(x = state.coreX + 300f, y = state.coreY + 120f)
        state.castSkill(slot)
        assertEquals(values[2], state.velocityX, 1e-2f, "rank $rank hook speed toward the target")
        assertEquals(values[1].toInt(), target.stacksOf(StatusElement.ION), "rank $rank Charge")
        assertEquals(state.elapsed + values[5], state.skillRuntime[slot, SkillCell.TARGET_UNTIL], 1e-5f)
        assertEquals(if (rank == 3) state.elapsed + 1.5f else 0f, state.skillRuntime[slot, SkillCell.AUX_UNTIL], 1e-5f)
        val power = state.skillPower()
        state.onSkillCoreImpact(target, 900f, 50f, dashContact = false)
        assertEquals(values[4] * power, linked.maxHp - linked.hp, DAMAGE_TOLERANCE, "rank $rank arc link damage")
        assertEquals(1, linked.stacksOf(StatusElement.ION))
        assertEquals(0f, state.skillRuntime[slot, SkillCell.TARGET_UNTIL], "the first impact spends the window")
    }

    private fun stormCoil(rank: Int) {
        val state = arena()
        val slot = state.learn(SkillId.STORM_COIL, rank)
        val values = state.valuesOf(SkillId.STORM_COIL)
        state.aimAt(state.coreX + 300f, state.coreY)
        val power = state.skillPower()
        state.castSkill(slot)
        val field = state.fieldRecords.single()
        assertEquals(CombatFieldKind.STORM_COIL, field.kind)
        assertEquals(values[0], field.radius)
        assertEquals(values[1], field.remaining)
        val inside = listOf(10f, 30f, 50f).map { offset ->
            state.dummy(x = field.x + offset, y = field.y).apply { disabledUntil = 1_000f }
        }
        state.updateCombatFields(STORM_COIL_ZAP_SECONDS)
        val zapped = inside.count { it.hp < it.maxHp }
        assertEquals(values[2].toInt(), zapped, "rank $rank zaps the nearest")
        inside.take(zapped).forEach { enemy ->
            assertEquals(values[3] * power, enemy.maxHp - enemy.hp, DAMAGE_TOLERANCE, "rank $rank zap damage")
            assertEquals(1, enemy.stacksOf(StatusElement.ION))
        }
    }

    private fun gravityWell(rank: Int) {
        val state = arena()
        val slot = state.learn(SkillId.GRAVITY_WELL, rank)
        val values = state.valuesOf(SkillId.GRAVITY_WELL)
        state.aimAt(state.coreX + 300f, state.coreY)
        val power = state.skillPower()
        state.castSkill(slot)
        val field = state.fieldRecords.single()
        assertEquals(CombatFieldKind.GRAVITY_WELL, field.kind)
        assertEquals(values[0], field.radius)
        assertEquals(values[1], field.remaining)
        assertEquals(values[2], field.pull)
        assertEquals(values[3] * power, field.damagePerSecond, 1e-4f)
        val inside = state.dummy(x = field.x + 40f, y = field.y).apply { disabledUntil = 1_000f }
        state.updateCombatFields(CombatField.TICK_SECONDS)
        assertEquals(values[3] * power * CombatField.TICK_SECONDS, inside.maxHp - inside.hp, DAMAGE_TOLERANCE, "rank $rank well tick")
        assertEquals(1, inside.stacksOf(StatusElement.GRAVITIC))
    }

    private fun massDriver(rank: Int) {
        val state = arena()
        val slot = state.learn(SkillId.MASS_DRIVER, rank)
        val values = state.valuesOf(SkillId.MASS_DRIVER)
        val before = state.effectiveMass()
        state.castSkill(slot)
        assertEquals(state.elapsed + values[0], state.skillRuntime[slot, SkillCell.ACTIVE_UNTIL])
        assertEquals(before * values[1], state.effectiveMass(), 1e-4f, "rank $rank mass")
        assertEquals(-values[2], state.buffAmount(BuffStat.DAMAGE_TAKEN), 1e-6f, "rank $rank damage taken")
        val struck = state.dummy(x = state.coreX + 30f, y = state.coreY)
        state.onSkillCoreImpact(struck, values[4], 10f, dashContact = false)
        assertEquals(2, struck.stacksOf(StatusElement.GRAVITIC), "a fast impact adds Mass")
        assertTrue(struck.thrownUntil > state.elapsed, "and throws")
        val slow = state.dummy(x = state.coreX - 30f, y = state.coreY)
        state.onSkillCoreImpact(slow, values[4] - 1f, 10f, dashContact = false)
        assertEquals(0, slow.stacksOf(StatusElement.GRAVITIC), "a slow impact does not")
    }

    private fun decaySpores(rank: Int) {
        val state = arena()
        val slot = state.learn(SkillId.DECAY_SPORES, rank)
        val values = state.valuesOf(SkillId.DECAY_SPORES)
        state.aimAt(state.coreX + 300f, state.coreY)
        val seeded = state.dummy(x = state.coreX + 300f, y = state.coreY)
        state.castSkill(slot)
        assertEquals(values[1].toInt(), seeded.stacksOf(StatusElement.ENTROPY), "rank $rank Decay")
        assertEquals(MarkKind.SPORED, state.markOf(seeded))
        // A Spored death spreads two Decay to a neighbour and drops one Data pickup.
        val neighbour = state.dummy(x = seeded.x + 60f, y = seeded.y)
        val pickups = state.pickups.size
        state.onSkillCarrierDied(seeded)
        assertEquals(2, neighbour.stacksOf(StatusElement.ENTROPY))
        assertEquals(pickups + 1, state.pickups.size)
        assertEquals(null, state.markOf(seeded), "the mark is cashed once")
    }

    private fun entropicTide(rank: Int) {
        val state = arena()
        val slot = state.learn(SkillId.ENTROPIC_TIDE, rank)
        val values = state.valuesOf(SkillId.ENTROPIC_TIDE)
        val target = state.dummy(x = state.coreX + 100f, y = state.coreY)
        state.applyStatus(target, StatusElement.ENTROPY, 5, 0)
        val amount = state.detonateDecayAmount(target, StatusElement.ENTROPY) * values[1]
        state.hp = state.maxHp - 50f
        state.heat = 30f
        state.castSkill(slot)
        assertEquals(0, target.stacksOf(StatusElement.ENTROPY), "the Tide consumes the Decay")
        assertEquals(amount, target.maxHp - target.hp, amount * 1e-3f, "rank $rank Tide damage")
        assertEquals(state.maxHp - 50f + 5 * values[2], state.hp, 1e-3f, "rank $rank mending")
        assertEquals(25f, state.heat, 1e-4f, "one heat vented per stack")
    }

    private fun sonicBoom(rank: Int) {
        val state = arena()
        val slot = state.learn(SkillId.SONIC_BOOM, rank)
        val values = state.valuesOf(SkillId.SONIC_BOOM)
        state.aimAt(state.coreX + 400f, state.coreY)
        val inCone = state.dummy(x = state.coreX + 150f, y = state.coreY + 20f)
        val behind = state.dummy(x = state.coreX - 150f, y = state.coreY)
        val bolt = state.hostileBolt(state.coreX + 120f, state.coreY - 10f)
        val power = state.skillPower()
        state.castSkill(slot)
        assertEquals(values[1] * power, inCone.maxHp - inCone.hp, DAMAGE_TOLERANCE, "rank $rank boom damage")
        assertEquals(values[2].toInt(), inCone.stacksOf(StatusElement.SONIC), "rank $rank Resonance")
        assertTrue(inCone.thrownUntil > state.elapsed)
        assertEquals(rank == 3, state.markOf(inCone) == MarkKind.BOOMED, "rank III rings the thrown")
        assertEquals(behind.maxHp, behind.hp, "the cone faces the aim")
        assertTrue(bolt.spent, "the boom erases shots in its cone")
        assertEquals(-values[5], state.velocityX, 1e-3f, "the recoil hops the Core back")
    }

    private fun resonator(rank: Int) {
        val state = arena()
        val slot = state.learn(SkillId.RESONATOR, rank)
        val values = state.valuesOf(SkillId.RESONATOR)
        state.aimAt(state.coreX + 300f, state.coreY)
        state.elapsed = 1.2f
        state.castSkill(slot)
        val field = state.fieldRecords.single()
        assertEquals(CombatFieldKind.ECHO_RING, field.kind)
        assertEquals(values[0], field.radius)
        assertEquals(values[1], field.remaining)
        assertEquals(values[2], field.power, "echo scale")
        assertEquals(values[3].toInt(), field.stacksPerTick, "echo cap")
        // The first pulse lands on the next global beat (1.5 s).
        assertEquals(BEAT_PERIOD - (1.5f - 1.2f), field.tickClock, 1e-5f)
    }

    private fun voidStep(rank: Int) {
        val state = arena()
        val slot = state.learn(SkillId.VOID_STEP, rank)
        val values = state.valuesOf(SkillId.VOID_STEP)
        state.aimAt(state.coreX + 600f, state.coreY)
        val startX = state.coreX
        state.castSkill(slot)
        assertEquals(startX + values[0], state.coreX, 1e-3f, "rank $rank step")
        assertEquals(state.elapsed + values[4], state.phaseUntil, 1e-5f)
        assertEquals(startX, state.skillRuntime[slot, SkillCell.ANCHOR_X])
        val inRift = state.dummy(x = startX + 20f, y = state.coreY)
        state.elapsed = state.skillRuntime[slot, SkillCell.PENDING_AT]
        val power = state.skillPower()
        state.updateSkills(0f)
        assertEquals(values[1] * power, inRift.maxHp - inRift.hp, DAMAGE_TOLERANCE, "rank $rank rift damage")
        assertEquals(values[2].toInt(), inRift.stacksOf(StatusElement.VOID), "rank $rank Hollow")
    }

    private fun nullShroud(rank: Int) {
        val state = arena()
        val slot = state.learn(SkillId.NULL_SHROUD, rank)
        val values = state.valuesOf(SkillId.NULL_SHROUD)
        val bolt = state.hostileBolt(state.coreX + state.coreRadius + 30f, state.coreY)
        state.castSkill(slot)
        assertTrue(bolt.spent)
        assertEquals(1f, state.skillRuntime[slot, SkillCell.STORED])
        assertEquals(1, state.shroudStored())
        assertEquals(state.elapsed + values[0], state.skillRuntime[slot, SkillCell.ACTIVE_UNTIL])
        // The store is bounded: a volley larger than it is only partly swallowed.
        repeat(30) { index -> state.hostileBolt(state.coreX + 20f, state.coreY + index * 0.5f) }
        state.updateSkills(0f)
        assertEquals(values[1], state.skillRuntime[slot, SkillCell.STORED], "rank $rank store cap")
        val target = state.dummy(x = state.coreX + 300f, y = state.coreY)
        val shots = state.projectiles.size
        state.elapsed = state.skillRuntime[slot, SkillCell.ACTIVE_UNTIL]
        state.updateSkills(0f)
        assertEquals(shots + values[1].toInt(), state.projectiles.size, "every stored bolt flies back")
        assertTrue(state.projectiles.takeLast(values[1].toInt()).all {
            !it.hostile && it.procKey == kinetickk.ball.gameplay.nucleus.model.WeaponRuntime.PROC_SKILL &&
                it.pierce == (if (rank == 3) 1 else 0)
        })
        assertTrue(target.alive)
    }

    private fun slingshot(rank: Int) {
        val state = arena()
        val slot = state.learn(SkillId.SLINGSHOT, rank)
        val values = state.valuesOf(SkillId.SLINGSHOT)
        state.aimAt(state.coreX, state.coreY - 400f)
        state.setVelocityForTesting(800f, 0f)
        state.castSkill(slot)
        val expected = (800f * values[0]).coerceIn(values[1], values[2])
        assertEquals(0f, state.velocityX, 1e-3f)
        assertEquals(-expected, state.velocityY, 1e-2f, "rank $rank swing onto the aim")
        assertEquals(state.elapsed + values[3], state.skillRuntime[slot, SkillCell.ACTIVE_UNTIL])
        assertEquals(rank == 3, state.slingshotKeepsMomentum())
    }

    private fun fullStop(rank: Int) {
        val state = arena()
        val slot = state.learn(SkillId.FULL_STOP, rank)
        val values = state.valuesOf(SkillId.FULL_STOP)
        state.setVelocityForTesting(800f, 0f)
        val target = state.dummy(x = state.coreX + 60f, y = state.coreY)
        val impact = state.impactPower(800f) * values[1]
        state.castSkill(slot)
        assertEquals(0f, state.speed)
        assertEquals(impact * target.impactTaken, target.maxHp - target.hp, impact * 1e-3f, "rank $rank shockwave")
    }

    private fun catalyze(rank: Int) {
        val state = arena()
        val slot = state.learn(SkillId.CATALYZE, rank)
        val values = state.valuesOf(SkillId.CATALYZE)
        state.aimAt(state.coreX + 300f, state.coreY)
        // The primed enemy's own reaction cooldown runs: only a forced reaction goes through it.
        val primed = state.dummy(x = state.coreX + 300f, y = state.coreY)
        primed.status[StatusCell.REACTION_READY_AT] = state.elapsed + 10f
        state.applyStatus(primed, StatusElement.THERMAL, 3, 0)
        state.applyStatus(primed, StatusElement.ENTROPY, 1, 0)
        assertEquals(3, primed.stacksOf(StatusElement.THERMAL))
        state.castSkill(slot)
        assertEquals(0, primed.stacksOf(StatusElement.THERMAL), "rank $rank forces the reaction through its cooldown")
        // An enemy with one element takes its partner instead (Cryo for Thermal when the build has no element).
        val lone = arena()
        val loneSlot = lone.learn(SkillId.CATALYZE, rank)
        val single = lone.dummy(x = lone.coreX + 300f, y = lone.coreY)
        lone.applyStatus(single, StatusElement.THERMAL, 1, 0)
        lone.castSkill(loneSlot)
        assertEquals(values[3].toInt(), single.stacksOf(StatusElement.CRYO), "a single element gets its partner")
    }

    private fun dualInfusion(rank: Int) {
        val state = arena()
        val slot = state.learn(SkillId.DUAL_INFUSION, rank)
        val values = state.valuesOf(SkillId.DUAL_INFUSION)
        state.castSkill(slot)
        assertEquals(state.elapsed + values[0], state.skillRuntime[slot, SkillCell.ACTIVE_UNTIL])
        // A held reaction cooldown keeps the infused stacks from reacting away.
        val target = state.dummy().apply { status[StatusCell.REACTION_READY_AT] = 1_000f }
        state.onSkillWeaponHit(target)
        state.onSkillWeaponHit(target)
        assertEquals(2, target.stacksOf(StatusElement.THERMAL), "the dominant element (Thermal fallback) on every hit")
        assertEquals(if (rank == 3) 2 else 1, target.stacksOf(StatusElement.CRYO), "rank $rank secondary cadence")
    }

    @Test
    fun aNearestSkillWithNoTargetSpendsNothingAndWaitsInTheBuffer() {
        val state = arena()
        val slot = state.learn(SkillId.ARC_HOOK)
        state.requestSkill(slot)
        state.updateSkills(MutableGameState.FIXED_STEP)
        assertEquals(0f, state.skillRuntime[slot, SkillCell.CAST_COUNT])
        assertEquals(0f, state.skillRuntime[slot, SkillCell.LAST_COOLDOWN])
        assertEquals(SkillSlotProjection.REFUSAL_NO_TARGET, state.skillRuntime[slot, SkillCell.REFUSAL].toInt())
        assertEquals(1 shl slot, state.skillBufferMask, "the request stays buffered")
        // An enemy arriving inside the buffer is hooked by the same request.
        state.aimAt(state.coreX + 300f, state.coreY)
        state.dummy(x = state.coreX + 200f, y = state.coreY)
        state.updateSkills(MutableGameState.FIXED_STEP)
        assertEquals(1f, state.skillRuntime[slot, SkillCell.CAST_COUNT])
        assertEquals(0, state.skillBufferMask)
        // A request that finds nothing for the whole buffer lapses.
        val idle = arena()
        val idleSlot = idle.learn(SkillId.ARC_HOOK)
        idle.requestSkill(idleSlot)
        repeat(20) { idle.updateSkills(MutableGameState.FIXED_STEP) }
        assertEquals(0, idle.skillBufferMask)
        assertEquals(0f, idle.skillBufferTime)
        // A hook never picks an enemy at the singularity.
        val crowded = arena()
        val crowdedSlot = crowded.learn(SkillId.ARC_HOOK)
        crowded.aimAt(crowded.coreX + 200f, crowded.coreY)
        crowded.dummy(x = crowded.coreX + 200f + HOOK_SINGULARITY_GAP - 1f, y = crowded.coreY)
        assertFalse(crowded.castSkill(crowdedSlot))
    }

    @Test
    fun refundsNeverMoreThanDoubleTheCastRate() {
        val state = arena()
        val slot = state.learn(SkillId.SOLAR_FLARE)
        val cooldown = state.content.skill(SkillId.SOLAR_FLARE).rank(1).cooldownSeconds
        state.castSkill(slot)
        val castAt = state.elapsed
        // A ready or empty slot takes nothing; the bank returns at most half the cooldown.
        assertEquals(0f, state.refundSkill(1, 5f), "empty slot")
        assertEquals(cooldown * MAX_REFUND_SHARE, state.refundSkill(slot, 100f), 1e-4f)
        assertEquals(0f, state.refundSkill(slot, 1f), "the bank is spent for this cast")
        assertEquals(castAt + cooldown * MAX_REFUND_SHARE, state.skillRuntime[slot, SkillCell.READY_AT], 1e-4f)
        state.elapsed = state.skillRuntime[slot, SkillCell.READY_AT]
        assertEquals(0f, state.refundSkill(slot, 1f), "a ready slot takes nothing")

        // Pumped every step with refunds from every source, no skill casts more than twice as often nor sooner
        // than the recast floor after its cast.
        for (id in SkillId.entries) {
            val pumped = arena()
            val pumpedSlot = pumped.learn(id)
            if (id == SkillId.ARC_HOOK) pumped.dummy(x = pumped.coreX + 200f, y = pumped.coreY)
            pumped.aimAt(pumped.coreX + 300f, pumped.coreY)
            val base = pumped.content.skill(id).rank(1).cooldownSeconds
            var casts = 0
            var lastCast = -1f
            var shortest = Float.MAX_VALUE
            val horizon = 60f
            while (pumped.elapsed < horizon) {
                pumped.elapsed += MutableGameState.FIXED_STEP
                pumped.refundAllSkills(1f)
                pumped.catalyze(StatusElement.THERMAL, StatusElement.CRYO)
                if (pumped.castSkill(pumpedSlot)) {
                    if (lastCast >= 0f) shortest = min(shortest, pumped.elapsed - lastCast)
                    lastCast = pumped.elapsed
                    casts++
                }
                pumped.takeVisualFxCues()
                pumped.takeSoundCues()
                pumped.projectiles.clear()
                pumped.enemies.removeAll { !it.alive }
                if (id == SkillId.ARC_HOOK && pumped.enemies.isEmpty()) pumped.dummy(x = pumped.coreX + 200f, y = pumped.coreY)
            }
            assertTrue(shortest >= MIN_SKILL_RECAST_SECONDS - 1e-3f, "$id recast after $shortest s")
            assertTrue(casts <= floor(horizon / (base * MAX_REFUND_SHARE)).toInt() + 1, "$id cast $casts times in $horizon s")
            assertTrue(shortest >= base * MAX_REFUND_SHARE - MutableGameState.FIXED_STEP * 2f, "$id more than doubled: $shortest s")
        }
    }

    @Test
    fun aCastDealsTheArchitectOneCappedInstance() {
        val state = arena()
        val slot = state.learn(SkillId.FULL_STOP, 3)
        state.setVelocityForTesting(2_400f, 0f)
        state.elapsed = 700f
        val architect = state.addEnemyForTesting(state.coreX + 60f, state.coreY, hp = 60_000f, radius = 40f, type = EnemyType.ARCHITECT)
        architect.phase = 0
        val cap = state.architectHitCap(architect)
        val before = architect.hp
        state.castSkill(slot)
        val lost = before - architect.hp
        assertTrue(lost > 0f, "the shockwave reaches the Architect")
        assertTrue(lost <= cap + 1e-3f, "one cast is one capped instance: $lost > $cap")
        assertEquals(-1, state.castBossHitFirst, "the cast scratch is idle again")
    }

    @Test
    fun castingDrawsNoRandomness() {
        for (id in SkillId.entries) {
            val state = arena()
            val slot = state.learn(id, 3)
            state.aimAt(state.coreX + 300f, state.coreY)
            repeat(5) { index -> state.dummy(x = state.coreX + 60f + index * 60f, y = state.coreY + (index % 2) * 30f) }
            state.enemies.forEach { state.applyStatus(it, StatusElement.THERMAL, 3, 0); state.applyStatus(it, StatusElement.ENTROPY, 2, 0) }
            state.heat = 50f
            state.setVelocityForTesting(700f, 0f)
            val gameplay = state.gameplayRandom.snapshot()
            val reward = state.rewardRandom.snapshot()
            val spawn = state.spawnRandom.snapshot()
            state.castSkill(slot)
            state.elapsed += 2f
            state.updateSkills(MutableGameState.FIXED_STEP)
            state.updateCombatFields(1f)
            assertEquals(gameplay, state.gameplayRandom.snapshot(), "$id drew gameplay randomness")
            assertEquals(reward, state.rewardRandom.snapshot(), "$id drew reward randomness")
            assertEquals(spawn, state.spawnRandom.snapshot(), "$id drew spawn randomness")
        }
    }

    @Test
    fun requestsBufferOnlyForFilledSlotsWhileRunningAndRepeatsChangeNothing() {
        val reducer = GameReducer()
        val model = arena()
        val slot = model.learn(SkillId.SOLAR_FLARE)
        model.takeVisualFxCues()
        model.takeSoundCues()
        val state = EngineState(model)
        val request = GameplayInteractionPulse.SkillRequested.fromValidated(slot)

        val buffered = assertIs<GameReductionResult.Accepted>(reducer.reduce(state, request)).state
        assertNotSame(state, buffered)
        assertEquals(1 shl slot, buffered.model.skillBufferMask)
        assertEquals(MutableGameState.SKILL_INPUT_BUFFER_SECONDS, buffered.model.skillBufferTime)
        assertEquals(0, model.skillBufferMask, "the committed state is untouched")
        assertSame(model.skillRuntime, buffered.model.skillRuntime, "a request shares the skill runtime")

        assertSame(buffered, assertIs<GameReductionResult.Accepted>(reducer.reduce(buffered, request)).state, "a repeat")
        val empty = GameplayInteractionPulse.SkillRequested.fromValidated(slot + 1)
        assertSame(state, assertIs<GameReductionResult.Accepted>(reducer.reduce(state, empty)).state, "an empty slot")
        val paused = EngineState(arena().apply { learn(SkillId.SOLAR_FLARE); togglePause(); takeVisualFxCues(); takeSoundCues() })
        assertSame(paused, assertIs<GameReductionResult.Accepted>(reducer.reduce(paused, request)).state, "a paused run")
    }

    @Test
    fun pausingAndExitingDropBufferedSkillRequests() {
        val state = arena()
        val slot = state.learn(SkillId.SOLAR_FLARE)
        state.requestSkill(slot)
        state.togglePause()
        assertEquals(0, state.skillBufferMask)
        assertEquals(0f, state.skillBufferTime)
        state.togglePause()
        state.requestSkill(slot)
        state.pauseForOverlay()
        assertEquals(0, state.skillBufferMask)
        state.togglePause()
        state.requestSkill(slot)
        state.exitRun()
        assertEquals(0, state.skillBufferMask)
    }

    @Test
    fun aForkIsolatesTheSkillRuntimeAndANewRunClearsIt() {
        val state = arena()
        val slot = state.learn(SkillId.METEOR)
        state.aimAt(state.coreX + 300f, state.coreY)
        state.castSkill(slot)
        val committed = state.skillRuntime.toString()
        val loadout = state.skillLoadout
        val candidate = state.copyForReduction()
        assertTrue(candidate.skillRuntime.sharesStorageWith(state.skillRuntime), "a fork shares until it writes")
        candidate.elapsed = candidate.skillRuntime[slot, SkillCell.PENDING_AT]
        candidate.updateSkills(0f)
        assertFalse(candidate.skillRuntime.sharesStorageWith(state.skillRuntime))
        assertEquals(committed, state.skillRuntime.toString(), "the committed runtime is untouched")
        assertSame(loadout, candidate.skillLoadout, "the immutable loadout is shared")
        candidate.startRun()
        assertSame(SkillLoadout.EMPTY, candidate.skillLoadout)
        assertEquals(0.0, candidate.skillRuntime.skillDamage)
        assertEquals(loadout, state.skillLoadout)
    }

    @Test
    fun aWorldRebaseMovesPendingSkillAnchors() {
        val state = arena()
        val slot = state.learn(SkillId.METEOR)
        state.aimAt(state.coreX + 300f, state.coreY)
        state.castSkill(slot)
        val x = state.skillRuntime[slot, SkillCell.ANCHOR_X]
        val y = state.skillRuntime[slot, SkillCell.ANCHOR_Y]
        state.skillRuntime.rebase(1_000f, -500f)
        assertEquals(x - 1_000f, state.skillRuntime[slot, SkillCell.ANCHOR_X])
        assertEquals(y + 500f, state.skillRuntime[slot, SkillCell.ANCHOR_Y])
    }

    @Test
    fun catalysisRechargesTheReactingElementsSkillsAndLessTheElementlessOnes() {
        val state = arena()
        val thermal = state.learn(SkillId.SOLAR_FLARE)
        val ion = state.learn(SkillId.ARC_HOOK)
        val kinetic = state.learn(SkillId.SLINGSHOT)
        state.dummy(x = state.coreX + 200f, y = state.coreY)
        state.aimAt(state.coreX + 300f, state.coreY)
        listOf(thermal, ion, kinetic).forEach { assertTrue(state.castSkill(it)) }
        val ready = listOf(thermal, ion, kinetic).map { state.skillRuntime[it, SkillCell.READY_AT] }
        val target = state.dummy(x = state.coreX + 600f, y = state.coreY)
        state.setOff(ReactionId.THERMAL_SHOCK, target)
        assertEquals(ready[0] - CATALYSIS_SECONDS, state.skillRuntime[thermal, SkillCell.READY_AT], 1e-4f, "Thermal skill")
        assertEquals(ready[1], state.skillRuntime[ion, SkillCell.READY_AT], "an Ion skill is not of this reaction")
        assertEquals(ready[2] - CATALYSIS_ELEMENTLESS_SECONDS, state.skillRuntime[kinetic, SkillCell.READY_AT], 1e-4f, "element-less")
        // An echo replays the reaction without Catalysis.
        val echoed = state.skillRuntime[thermal, SkillCell.READY_AT]
        state.reactionTokens = MutableGameState.REACTION_BURST
        state.echoReaction(state.dummy(x = state.coreX - 600f, y = state.coreY), ReactionId.THERMAL_SHOCK)
        assertEquals(echoed, state.skillRuntime[thermal, SkillCell.READY_AT])
    }

    @Test
    fun draftsFillTheLowestSlotReadyAndRankUpsMakeTheSkillReadyAndCountForTheBuild() {
        val state = arena()
        state.acquireSkill(SkillId.FROST_SNAP)
        state.acquireSkill(SkillId.SOLAR_FLARE)
        assertEquals(SkillId.FROST_SNAP, state.skillLoadout.skill(0))
        assertEquals(SkillId.SOLAR_FLARE, state.skillLoadout.skill(1))
        assertEquals(1, state.skillLoadout.rank(0))
        assertEquals(state.elapsed, state.skillRuntime[0, SkillCell.READY_AT], "a drafted skill is ready at once")
        // Each skill counts a point in both tags and its element.
        assertEquals(1, state.build.affinity(StatusElement.CRYO))
        assertEquals(1, state.build.affinity(StatusElement.THERMAL))
        state.castSkill(0)
        state.rankUpSkill(SkillId.FROST_SNAP)
        assertEquals(2, state.skillLoadout.rank(0))
        assertEquals(state.elapsed, state.skillRuntime[0, SkillCell.READY_AT], "a rank-up makes the skill ready")
        state.rankUpSkill(SkillId.FROST_SNAP)
        state.rankUpSkill(SkillId.FROST_SNAP)
        assertEquals(3, state.skillLoadout.rank(0), "rank III is the top")
        assertEquals(2, state.build.affinity(StatusElement.CRYO), "rank III adds its element again")
        // An element-less skill at rank III adds its first tag again instead.
        val kinetic = arena()
        val tag = kinetic.content.skill(SkillId.SLINGSHOT).tags.first()
        val base = kinetic.build.tagPoints(tag)
        kinetic.learn(SkillId.SLINGSHOT, 3)
        assertEquals(base + 2, kinetic.build.tagPoints(tag))
    }

    @Test
    fun voidStepPhasesContactsAndShotsButNotSelfDamage() {
        val state = arena()
        state.phaseUntil = state.elapsed + 0.3f
        val hp = state.hp
        state.takeDamage(10f, kind = IncomingDamage.CONTACT)
        state.takeDamage(10f, kind = IncomingDamage.PROJECTILE)
        assertEquals(hp, state.hp)
        state.takeDamage(10f, kind = IncomingDamage.SELF)
        assertTrue(state.hp < hp)
    }

    @Test
    fun dualInfusionStopsAtItsStacksPerSecond() {
        val state = arena()
        val slot = state.learn(SkillId.DUAL_INFUSION)
        val cap = state.valuesOf(SkillId.DUAL_INFUSION)[2].toInt()
        state.castSkill(slot)
        val targets = List(cap) { state.dummy(x = 400f + it * 50f).apply { status[StatusCell.REACTION_READY_AT] = 1_000f } }
        repeat(cap) { index -> state.onSkillWeaponHit(targets[index % targets.size]) }
        val applied = { targets.sumOf { it.stacksOf(StatusElement.THERMAL) + it.stacksOf(StatusElement.CRYO) } }
        assertEquals(cap, applied(), "the cap is reached within one second")
        state.onSkillWeaponHit(targets[0])
        assertEquals(cap, applied(), "the next stack waits for the next second")
        state.elapsed += 1f
        state.onSkillWeaponHit(targets[0])
        assertTrue(applied() > cap)
    }

    @Test
    fun charactersShapeTheirSkillCasts() {
        // Square: every facet up casts at a shorter cooldown.
        val prism = arena(CoreShape.PRISM)
        val prismSlot = prism.learn(SkillId.SOLAR_FLARE)
        prism.castSkill(prismSlot)
        assertEquals(10f * PRISM_SKILL_COOLDOWN, prism.skillRuntime[prismSlot, SkillCell.LAST_COOLDOWN], 1e-4f)
        // Pentagon: an on-beat cast refunds a fifth of its cooldown and counts as an on-beat action.
        val pentagon = arena(CoreShape.PENTAGON)
        val beatSlot = pentagon.learn(SkillId.SOLAR_FLARE)
        pentagon.elapsed = BEAT_PERIOD * 4f
        pentagon.castSkill(beatSlot)
        assertEquals(pentagon.elapsed + 10f * (1f - PENTAGON_SKILL_REFUND), pentagon.skillRuntime[beatSlot, SkillCell.READY_AT], 1e-4f)
        assertEquals(1, pentagon.characterRuntime.beatStreak)
        // Crescent: a cast advances the Blink recharge.
        val crescent = arena(CoreShape.CRESCENT)
        val crescentSlot = crescent.learn(SkillId.SOLAR_FLARE)
        crescent.characterRuntime.blinkCharges = 0
        crescent.castSkill(crescentSlot)
        assertEquals(CRESCENT_SKILL_BLINK_SECONDS, crescent.characterRuntime.blinkRecharge, 1e-5f)
        // Dipole: a cast flips at once.
        val dipole = arena(CoreShape.DIPOLE)
        val dipoleSlot = dipole.learn(SkillId.SOLAR_FLARE)
        val polarity = dipole.characterRuntime.polarity
        dipole.castSkill(dipoleSlot)
        assertTrue(dipole.characterRuntime.polarity != polarity)
        // Tesseract: a cast records a lattice node at the Core.
        val tesseract = arena(CoreShape.TESSERACT)
        val nodeSlot = tesseract.learn(SkillId.SOLAR_FLARE)
        tesseract.castSkill(nodeSlot)
        assertEquals(1, tesseract.characterRuntime.latticeCount)
        // Diamond: a parry recharges every skill.
        val diamond = arena(CoreShape.DIAMOND)
        val parrySlot = diamond.learn(SkillId.SOLAR_FLARE)
        diamond.castSkill(parrySlot)
        val parryReady = diamond.skillRuntime[parrySlot, SkillCell.READY_AT]
        diamond.characterRuntime.parryWindow = PARRY_WINDOW_SECONDS
        assertTrue(diamond.tryCharacterParry(diamond.coreX + 100f, diamond.coreY, -400f, 0f))
        assertEquals(parryReady - DIAMOND_PARRY_SKILL_REFUND, diamond.skillRuntime[parrySlot, SkillCell.READY_AT], 1e-4f)
        // Triangle: the cast's skill hits are marked for Puncture.
        val shard = arena(CoreShape.SHARD)
        val shardSlot = shard.learn(SkillId.SOLAR_FLARE)
        val marked = shard.dummy(x = shard.coreX + 60f, y = shard.coreY)
        shard.castSkill(shardSlot)
        assertTrue(shard.isMarked(marked))
        // Ring: a wide Halo widens the self-centred skills.
        val ring = arena(CoreShape.RING)
        val ringSlot = ring.learn(SkillId.SOLAR_FLARE)
        ring.characterRuntime.haloRadius = 165f
        val reach = ring.valuesOf(SkillId.SOLAR_FLARE)[1] * 1.5f
        val far = ring.dummy(x = ring.coreX + reach - 5f, y = ring.coreY)
        ring.castSkill(ringSlot)
        assertTrue(far.hp < far.maxHp, "the Halo carried the flare to $reach")
        // Star: a skill kill gathers one more Stellar Mass than another kill.
        val star = arena(CoreShape.STAR)
        val starSlot = star.learn(SkillId.SOLAR_FLARE)
        star.dummy(x = star.coreX + 60f, y = star.coreY, hp = 1f)
        star.castSkill(starSlot)
        star.sweepKills()
        assertEquals(1 + STAR_SKILL_KILL_MASS, star.characterRuntime.stellarMass)
        // Hexagon: every idle drone darts at the cast's first target.
        val hexagon = arena(CoreShape.HEXAGON)
        val hiveSlot = hexagon.learn(SkillId.SOLAR_FLARE)
        repeat(2) { hexagon.onCharacterDataPickupForcingDrone() }
        val hit = hexagon.dummy(x = hexagon.coreX + 60f, y = hexagon.coreY)
        hexagon.castSkill(hiveSlot)
        assertTrue((0 until hexagon.characterRuntime.droneCount).all {
            hexagon.characterRuntime.droneValue(it, kinetickk.ball.gameplay.nucleus.model.CharacterRuntime.DRONE_DART) > 0f
        },
            "every drone darts")
        assertTrue(hit.hp < hit.maxHp)
        // Circle: a charged Orbit releases at the cast's anchor.
        val orb = arena(CoreShape.ORB)
        val orbSlot = orb.learn(SkillId.METEOR)
        orb.aimAt(orb.coreX + 400f, orb.coreY)
        val atAnchor = orb.dummy(x = orb.coreX + 400f, y = orb.coreY)
        orb.characterRuntime.charge = 1f
        orb.castSkill(orbSlot)
        assertEquals(0f, orb.characterRuntime.charge)
        assertTrue(atAnchor.hp < atAnchor.maxHp, "the Orbit ring struck at the anchor")
        // Spiral: the ghost repeats a self-centred damage skill a second later at a share of its damage.
        val spiral = arena(CoreShape.SPIRAL)
        val spiralSlot = spiral.learn(SkillId.SOLAR_FLARE)
        spiral.castSkill(spiralSlot)
        assertEquals(spiral.elapsed + GHOST_REPEAT_DELAY, spiral.skillRuntime[spiralSlot, SkillCell.GHOST_AT])
    }

    @Test
    fun theSkillBarProjectsTheSlotsAndKeepsAnUnchangedBar() {
        val state = arena()
        assertSame(SkillBarProjection.EMPTY, state.toRenderModel().skillBar)
        val slot = state.learn(SkillId.SOLAR_FLARE, 2)
        val ready = state.toRenderModel()
        val bar = ready.skillBar
        assertEquals(SkillId.SOLAR_FLARE.ordinal, bar.slot(slot).skill)
        assertEquals(2, bar.slot(slot).rank)
        assertTrue(bar.slot(slot).ready)
        assertTrue(bar.slot(1).isEmpty && bar.slot(2).isEmpty)
        assertSame(bar, state.toRenderModel(reusableCollections = ready).skillBar, "an unchanged bar is kept")
        state.castSkill(slot)
        val cooling = state.toRenderModel(reusableCollections = ready).skillBar.slot(slot)
        assertFalse(cooling.ready)
        assertEquals(1f, cooling.cooldown01)
        state.elapsed += state.skillRuntime[slot, SkillCell.LAST_COOLDOWN] * 0.25f
        assertEquals(0.75f, state.toRenderModel().skillBar.slot(slot).cooldown01, 1e-4f)
    }

    /** Dummies carry a million integrity: one Float step there is 1/16, so damage compares within that. */
    private val DAMAGE_TOLERANCE = 0.07f

    private fun arena(shape: CoreShape = CoreShape.ORB): MutableGameState = buildTestState(shape = shape).apply {
        obstacleField = ObstacleField.EMPTY
        aimAt(coreX + 300f, coreY)
    }

    private fun MutableGameState.learn(id: SkillId, rank: Int = 1): Int {
        acquireSkill(id)
        repeat(rank - 1) { rankUpSkill(id) }
        takeVisualFxCues()
        takeSoundCues()
        return skillLoadout.slotOf(id)
    }

    private fun MutableGameState.valuesOf(id: SkillId): List<Float> =
        content.skill(id).rank(skillLoadout.rank(skillLoadout.slotOf(id))).values

    /** Builds Hive drones until one more exists (the Hive's own pickup rule guarantees one within a few pickups). */
    private fun MutableGameState.onCharacterDataPickupForcingDrone() {
        val before = characterRuntime.droneCount
        while (characterRuntime.droneCount == before) {
            pickupsCollected++
            onCharacterDataPickup()
        }
    }
}
