// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DirectedReward
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RewardFocus
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.model.counterElement
import kinetickk.ball.gameplay.nucleus.model.weaknessElement
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.EnemyAffinity
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Sonic (Resonance / Shatter), Void (Hollow / Unmake), their Relics and their reactions. */
class SonicVoidElementTest {
    @Test
    fun statusLayoutFollowsTheElementCountWithoutOverlap() {
        assertEquals(StatusElement.entries.size, StatusCell.ELEMENTS)
        assertEquals(ReactionId.entries.size, StatusCell.PAIRS)
        val blocks = listOf(StatusCell.STACKS, StatusCell.DECAY_AT, StatusCell.GENERATION, StatusCell.REARM_AT)
        blocks.zipWithNext().forEach { (a, b) -> assertEquals(StatusCell.ELEMENTS, b - a) }
        assertEquals(StatusCell.REARM_AT + StatusCell.ELEMENTS, StatusCell.FROZEN_UNTIL)
        assertEquals(StatusCell.REACTION_PAIR_READY_AT + StatusCell.PAIRS, StatusCell.CRYO_IMMUNE_CARRY)
        assertEquals(StatusCell.BLACKFIRE_DPS + 1, StatusCell.MARK)
        assertEquals(StatusCell.MARK + 1, StatusCell.MARK_UNTIL)
        assertEquals(StatusCell.MARK_UNTIL + 1, StatusCell.MARK_CAST)
        assertEquals(StatusCell.MARK_CAST + 1, StatusCell.COUNT)
        assertEquals(74, StatusCell.COUNT)
    }

    @Test
    fun statusBitsRoundTripSevenStackCountsBesideTheirFlags() {
        val state = buildTestState()
        val enemy = state.dummy()
        StatusElement.entries.forEach { element ->
            enemy.status[StatusCell.STACKS + element.ordinal] = (element.ordinal + 1).toFloat()
        }
        val plain = state.statusBits(enemy)
        StatusElement.entries.forEach { element ->
            assertEquals(element.ordinal + 1, (plain ushr (element.ordinal * 4)) and 0xF, "$element stacks")
        }
        assertEquals(0, plain and STATUS_FROZEN_BIT)
        assertEquals(28, STATUS_STACK_BITS)

        enemy.status[StatusCell.FROZEN_UNTIL] = state.elapsed + 1f
        enemy.status[StatusCell.BRITTLE] = 1f
        enemy.status[StatusCell.EMBRITTLED_UNTIL] = state.elapsed + 1f
        // Void one stack short of its Break sets the primed flag in the sign bit.
        enemy.status[StatusCell.STACKS + StatusElement.VOID.ordinal] = (state.breakThreshold(StatusElement.VOID) - 1).toFloat()
        val flagged = state.statusBits(enemy)
        assertEquals(STATUS_FROZEN_BIT or STATUS_BRITTLE_BIT or STATUS_EMBRITTLED_BIT or STATUS_PRIMED_BIT, flagged and (0xF shl 28))
        assertEquals(1 shl 28, STATUS_FROZEN_BIT)
        assertEquals(Int.MIN_VALUE, STATUS_PRIMED_BIT)
        assertEquals(state.breakThreshold(StatusElement.VOID) - 1, (flagged ushr (StatusElement.VOID.ordinal * 4)) and 0xF)
        assertEquals(StatusElement.THERMAL.ordinal + 1, flagged and 0xF)

        // Counts above fifteen saturate inside their own field.
        enemy.status[StatusCell.STACKS + StatusElement.SONIC.ordinal] = 40f
        assertEquals(15, (state.statusBits(enemy) ushr (StatusElement.SONIC.ordinal * 4)) and 0xF)
    }

    @Test
    fun resonanceRaisesKnockbackAndImpactDamageTakenButNeverMovesImmovableFrames() {
        val state = buildTestState()
        val enemy = state.dummy()
        assertEquals(1f, state.knockbackTaken(enemy))
        assertEquals(0f, state.vulnerability(enemy, HitSource.IMPACT))

        enemy.status[StatusCell.STACKS + StatusElement.SONIC.ordinal] = 5f
        assertEquals(1.2f, state.knockbackTaken(enemy), 0.0001f)
        assertEquals(0.15f, state.vulnerability(enemy, HitSource.IMPACT), 0.0001f)
        assertEquals(0.15f, state.vulnerability(enemy, HitSource.DASH), 0.0001f)
        assertEquals(0f, state.vulnerability(enemy, HitSource.WEAPON), "Resonance only softens collisions")

        val charger = state.dummy(type = EnemyType.CHARGER)
        charger.status[StatusCell.STACKS + StatusElement.SONIC.ordinal] = 5f
        assertEquals(0f, state.knockbackTaken(charger))

        // Knockback taken feeds every push the build makes.
        enemy.x = 100f
        enemy.y = 0f
        enemy.vx = 0f
        state.pushEnemies(0f, 0f, 300f, 100f, inward = false)
        val pushed = enemy.vx
        val plain = state.dummy(x = 100f, y = 0f)
        plain.vx = 0f
        state.pushEnemies(0f, 0f, 300f, 100f, inward = false)
        assertEquals(1.2f, pushed / plain.vx, 0.0001f)
    }

    @Test
    fun shatterHurtsAndThrowsEverythingAroundTheBrokenEnemy() {
        val state = buildTestState()
        val enemy = state.dummy(x = 400f)
        val neighbor = state.dummy(x = 500f)
        val far = state.dummy(x = 900f)
        val sonic = state.content.build.element(StatusElement.SONIC)

        state.applyStatus(enemy, StatusElement.SONIC, sonic.breakThreshold, generation = 0)

        assertEquals(0, enemy.stacksOf(StatusElement.SONIC), "Shatter consumes every stack")
        val damage = sonic.breakPower * sonic.breakThreshold * state.runScale()
        assertEquals(enemy.maxHp - damage, enemy.hp, 0.05f)
        assertEquals(neighbor.maxHp - damage, neighbor.hp, 0.05f)
        assertTrue(neighbor.vx > 0f, "The neighbor is thrown away from the Shatter")
        assertTrue(enemy.vx > 0f, "The shattered enemy is thrown away from the Core")
        assertEquals(far.maxHp, far.hp)
        assertEquals(0f, far.vx)
        assertTrue(state.takeVisualFxCues().any { it is VisualFxCue.StatusBroken && it.element == StatusElement.SONIC.ordinal })
    }

    @Test
    fun thunderChoirChainsShatterToTheNearestResonantEnemiesAndRaisesKnockback() {
        val state = buildTestState(RelicId.THUNDER_CHOIR)
        val enemy = state.dummy(x = 400f)
        val near = state.dummy(x = 620f).resonant()
        val second = state.dummy(x = 400f, y = 240f).resonant()
        val third = state.dummy(x = 400f, y = -250f).resonant()
        val silent = state.dummy(x = 400f + 200f * 0.7071f, y = 200f * 0.7071f)
        val outside = state.dummy(x = 700f).resonant()
        val sonic = state.content.build.element(StatusElement.SONIC)

        state.applyStatus(enemy, StatusElement.SONIC, sonic.breakThreshold, generation = 0)

        val shatter = sonic.breakPower * sonic.breakThreshold * state.runScale()
        // Rank one: two links at 60 % of the Shatter's damage, nearest first.
        assertEquals(near.maxHp - shatter * 0.6f, near.hp, 0.05f)
        assertEquals(second.maxHp - shatter * 0.6f, second.hp, 0.05f)
        assertEquals(third.maxHp, third.hp, "Only the two nearest Resonant enemies are reached")
        assertEquals(silent.maxHp, silent.hp, "Enemies without Resonance are skipped")
        assertEquals(outside.maxHp, outside.hp, "Resonant enemies beyond 260 are out of reach")

        assertEquals(1.2f, state.knockbackTaken(state.dummy()), 0.0001f)
        assertEquals(1.15f, state.impactRecoilScale(), 0.0001f)
        assertEquals(1f, buildTestState().impactRecoilScale())
    }

    @Test
    fun hollowWeakensHostileDamageDownToItsFloor() {
        val state = buildTestState()
        val enemy = state.dummy()
        assertEquals(1f, state.hostileOutputFactor(enemy))
        assertEquals(1f, state.hostileOutputFactor(null))

        enemy.status[StatusCell.STACKS + StatusElement.VOID.ordinal] = 4f
        assertEquals(0.88f, state.hostileOutputFactor(enemy), 0.0001f)
        assertEquals(10f * 0.88f, state.hostileDamage(enemy, 10f), 0.0001f)

        enemy.status[StatusCell.STACKS + StatusElement.VOID.ordinal] = 30f
        assertEquals(HOLLOW_OUTPUT_FLOOR, state.hostileOutputFactor(enemy))
    }

    @Test
    fun unmakeReleasesRememberedDamageAfterADelayAndRespectsItsCaps() {
        val state = buildTestState()
        val enemy = state.dummy(hp = 10_000f)
        state.applyStatus(enemy, StatusElement.VOID, 3, generation = 0)
        state.damageEnemy(enemy, 1_000f)
        assertEquals(60f, enemy.status[StatusCell.HOLLOW_MEMORY], 0.001f, "Each Hollow stack remembers 2 %")

        state.applyStatus(enemy, StatusElement.VOID, 5, generation = 0)
        assertEquals(0, enemy.stacksOf(StatusElement.VOID))
        assertEquals(0f, enemy.status[StatusCell.HOLLOW_MEMORY], "Unmake spends the memory")
        assertEquals(9_000f, enemy.hp, 0.001f, "The strike waits")
        assertEquals(state.elapsed + UNMAKE_DELAY_SECONDS, enemy.status[StatusCell.UNMAKE_AT], 0.0001f)

        val strikeAt = enemy.status[StatusCell.UNMAKE_AT]
        state.elapsed = strikeAt - 0.1f
        state.updateStatuses(MutableGameState.FIXED_STEP)
        assertEquals(9_000f, enemy.hp, 0.001f)
        state.elapsed = strikeAt
        state.updateStatuses(MutableGameState.FIXED_STEP)
        assertEquals(9_000f - 60f * 1.6f, enemy.hp, 0.01f, "Remembered damage ×1.6")
        assertEquals(0f, enemy.status[StatusCell.UNMAKE_AT])

        // An ordinary enemy loses at most 40 % of its maximum integrity; a boss its weighted share.
        val capped = state.dummy(hp = 1_000f)
        capped.status[StatusCell.HOLLOW_MEMORY] = 900f
        state.applyStatus(capped, StatusElement.VOID, 8, generation = 0)
        assertEquals(400f, capped.status[StatusCell.UNMAKE_DAMAGE], 0.001f)
        state.bossSpawned = true
        val boss = state.addEnemyForTesting(1_200f, 0f, hp = 100_000f, radius = 74f, type = EnemyType.ARCHITECT)
        boss.status[StatusCell.HOLLOW_MEMORY] = 50_000f
        state.applyStatus(boss, StatusElement.VOID, 8, generation = 0)
        assertEquals(100_000f * UNMAKE_INTEGRITY_CAP * victimFactor(boss), boss.status[StatusCell.UNMAKE_DAMAGE], 0.01f)

        // Memory fades with the last Hollow stack.
        val faded = state.dummy(hp = 10_000f)
        state.applyStatus(faded, StatusElement.VOID, 1, generation = 0)
        state.damageEnemy(faded, 500f)
        assertTrue(faded.status[StatusCell.HOLLOW_MEMORY] > 0f)
        state.elapsed += 3.1f
        state.updateStatuses(MutableGameState.FIXED_STEP)
        assertEquals(0, faded.stacksOf(StatusElement.VOID))
        assertEquals(0f, faded.status[StatusCell.HOLLOW_MEMORY])
    }

    @Test
    fun nullCovenantUnmakesAtOnceRemembersMoreKeepsHollowOnElitesAndCostsIntegrity() {
        val state = buildTestState()
        assertEquals(100f, state.maxHp)
        state.acquireRelic(RelicId.NULL_COVENANT)
        assertEquals(90f, state.maxHp, 0.001f, "Maximum integrity −10 %")
        assertTrue(state.hp <= state.maxHp)

        val enemy = state.dummy(hp = 10_000f)
        state.applyStatus(enemy, StatusElement.VOID, 3, generation = 0)
        state.damageEnemy(enemy, 1_000f)
        assertEquals(84f, enemy.status[StatusCell.HOLLOW_MEMORY], 0.001f, "Rank one remembers 40 % more")
        state.applyStatus(enemy, StatusElement.VOID, 5, generation = 0)
        assertEquals(9_000f - 84f * 1.6f, enemy.hp, 0.01f, "Unmake strikes immediately")
        assertEquals(0f, enemy.status[StatusCell.UNMAKE_AT])

        val elite = state.dummy(type = EnemyType.ELITE)
        val ordinary = state.dummy(x = -400f)
        state.applyStatus(elite, StatusElement.VOID, 2, generation = 0)
        state.applyStatus(ordinary, StatusElement.VOID, 2, generation = 0)
        state.elapsed += 3.05f
        state.updateStatuses(MutableGameState.FIXED_STEP)
        assertEquals(2, elite.stacksOf(StatusElement.VOID), "Hollow never decays on Elites")
        assertEquals(1, ordinary.stacksOf(StatusElement.VOID))

        // The liability lingers through severance and then returns the ceiling without healing.
        state.hp = 50f
        state.replaceRelic(0, RelicId.PERPETUAL_VECTOR)
        assertEquals(90f, state.maxHp, 0.001f)
        state.elapsed += state.content.build.policy.severanceSeconds + 1f
        state.updateRelicRuntime(MutableGameState.FIXED_STEP)
        assertEquals(100f, state.maxHp, 0.001f)
        assertEquals(50f, state.hp)
        assertEquals(0f, state.integrityLiabilityCut)
    }

    @Test
    fun hollowMemoryEndsWhenAReactionOrAnImmuneAspectTakesTheLastVoidStack() {
        val state = buildTestState()
        // Hollow as the older element: Blackfire spends every Void stack.
        val older = state.dummy(hp = 10_000f)
        state.applyStatus(older, StatusElement.VOID, 3, generation = 0)
        state.damageEnemy(older, 1_000f)
        assertTrue(older.status[StatusCell.HOLLOW_MEMORY] > 0f)
        state.applyStatus(older, StatusElement.THERMAL, 1, generation = 0)
        assertEquals(ReactionId.BLACKFIRE.ordinal + 1, older.status[StatusCell.LAST_REACTION].toInt())
        assertEquals(0, older.stacksOf(StatusElement.VOID))
        assertEquals(0f, older.status[StatusCell.HOLLOW_MEMORY], "Blackfire spends the Hollow and what it remembered")

        // Hollow as the incoming element: the reaction takes the remembered stack together with the new one.
        val incoming = state.dummy(x = -400f, hp = 10_000f)
        state.applyStatus(incoming, StatusElement.VOID, 1, generation = 0)
        state.damageEnemy(incoming, 1_000f)
        state.applyStatus(incoming, StatusElement.THERMAL, 3, generation = 0)
        assertTrue(incoming.status[StatusCell.HOLLOW_MEMORY] > 0f)
        state.applyStatus(incoming, StatusElement.VOID, 1, generation = 0)
        assertEquals(ReactionId.BLACKFIRE.ordinal + 1, incoming.status[StatusCell.LAST_REACTION].toInt())
        assertEquals(0, incoming.stacksOf(StatusElement.VOID))
        assertEquals(0f, incoming.status[StatusCell.HOLLOW_MEMORY])

        // A later Hollow window starts empty, so its Unmake releases only what that window remembered.
        state.applyStatus(older, StatusElement.VOID, 3, generation = 0)
        state.damageEnemy(older, 100f)
        assertEquals(100f * 3 * HOLLOW_MEMORY_PER_STACK, older.status[StatusCell.HOLLOW_MEMORY], 0.001f)

        // An immune Void aspect strips the stacks and the memory with them.
        val elite = state.dummy(x = 900f, hp = 10_000f, type = EnemyType.ELITE)
        state.applyStatus(elite, StatusElement.VOID, 3, generation = 0)
        state.damageEnemy(elite, 1_000f)
        assertTrue(elite.status[StatusCell.HOLLOW_MEMORY] > 0f)
        assignAspect(elite, StatusElement.VOID.ordinal, immune = true)
        assertEquals(0f, elite.status[StatusCell.HOLLOW_MEMORY])
    }

    @Test
    fun nullCovenantKeepsTakingATenthOfARisingCeiling() {
        val state = buildTestState()
        state.acquireRelic(RelicId.NULL_COVENANT)
        assertEquals(90f, state.maxHp, 0.001f)
        state.hp = state.maxHp

        state.applyStatDelta(ItemEffect.MAX_INTEGRITY, 100f)
        assertEquals(0.9f * 200f, state.maxHp, 0.01f, "the cut stays 10 % of the uncut 200")
        assertEquals(20f, state.integrityLiabilityCut, 0.01f)
        assertEquals(state.maxHp, state.hp, 0.01f, "integrity rises by the ceiling actually gained")

        state.onLevelUp()
        assertEquals(0.9f * (200f + state.content.tempo.levelIntegrity), state.maxHp, 0.01f)

        // Ending the liability returns the whole cut and heals nothing.
        val integrity = state.hp
        state.replaceRelic(0, RelicId.PERPETUAL_VECTOR)
        state.elapsed += state.content.build.policy.severanceSeconds + 1f
        state.updateRelicRuntime(MutableGameState.FIXED_STEP)
        assertEquals(200f + state.content.tempo.levelIntegrity, state.maxHp, 0.01f)
        assertEquals(0f, state.integrityLiabilityCut)
        assertEquals(integrity, state.hp)

        // Without the relic every raise reaches the ceiling whole.
        val plain = buildTestState()
        plain.applyStatDelta(ItemEffect.MAX_INTEGRITY, 100f)
        assertEquals(200f, plain.maxHp)
    }

    @Test
    fun seismicAndStasisStopOrdinaryEnemiesWhileElitesAndBossesShrugThemOff() {
        for (reaction in listOf(ReactionId.SEISMIC, ReactionId.STASIS)) {
            val state = buildTestState()
            // The Core sits at the origin; the target drifts across its line to the Core, not along it.
            val target = state.dummy(x = 400f, type = EnemyType.SHOOTER).apply { actionTimer = 1f }
            val elite = state.dummy(x = 460f, type = EnemyType.ELITE)
            state.bossSpawned = true
            val boss = state.addEnemyForTesting(400f, 60f, hp = 100_000f, radius = 74f, type = EnemyType.ARCHITECT)
            target.vy = 50f
            state.applyStatus(target, reaction.first, 3, generation = 0)
            state.applyStatus(target, reaction.second, 1, generation = 0)

            assertFalse(state.canAct(target), "$reaction stops the ordinary target")
            // No steering and no attack countdown; any momentum left only bleeds off.
            val (x, y, vy) = Triple(target.x, target.y, target.vy)
            state.updateEnemies(MutableGameState.FIXED_STEP)
            assertEquals(0f, target.vx, "$reaction: a disabled enemy never steers toward the Core")
            assertEquals(1f, target.actionTimer, "$reaction: a disabled enemy counts down no attack")
            if (reaction == ReactionId.STASIS) {
                assertEquals(x, target.x, "Stasis stops the target dead")
                assertEquals(y, target.y, "Stasis stops the target dead")
            } else {
                assertTrue(target.vy > 0f && target.vy < vy, "A Seismic stun lets momentum bleed off: ${target.vy}")
                assertTrue(target.y > y, "The stunned target still drifts on its momentum")
            }
            assertTrue(state.canAct(elite), "$reaction never stops an Elite")
            assertTrue(state.canAct(boss), "$reaction never stops the Architect")
            val seconds = if (reaction == ReactionId.SEISMIC) SEISMIC_STUN_SECONDS else STASIS_STOP_SECONDS
            state.elapsed += seconds
            assertTrue(state.canAct(target), "The stop ends on time")
        }
    }

    @Test
    fun stasisDeletesHostileShotsInsideItsRadiusOnly() {
        val state = buildTestState()
        val target = state.dummy(x = 400f)
        val radius = state.content.build.reaction(ReactionId.STASIS).radius
        state.addProjectile(Projectile(x = 450f, y = 0f, vx = 0f, vy = 0f, radius = 5f, life = 3f, hostile = true))
        state.addProjectile(Projectile(x = 400f + radius + 40f, y = 0f, vx = 0f, vy = 0f, radius = 5f, life = 3f, hostile = true))
        state.addProjectile(Projectile(x = 420f, y = 0f, vx = 0f, vy = 0f, radius = 5f, life = 3f, hostile = false))

        state.applyStatus(target, StatusElement.CRYO, 3, generation = 0)
        state.applyStatus(target, StatusElement.VOID, 1, generation = 0)

        assertEquals(1, state.projectiles.count { it.hostile && !it.spent }, "Only the shot outside the radius survives")
        assertEquals(1, state.projectiles.count { !it.hostile && !it.spent }, "Player shots are untouched")
        state.updateProjectiles(MutableGameState.FIXED_STEP)
        assertEquals(2, state.projectiles.size, "Spent shots leave with the next projectile update")
    }

    @Test
    fun stasisFromAWeaponShotSpendsNearbyHostileShotsWhileProjectileHitsResolve() {
        val state = buildTestState().apply { own("Null Rounds") }
        val target = state.dummy(x = state.coreX + 400f, y = state.coreY)
        state.applyStatus(target, StatusElement.CRYO, 3, generation = 0)
        // One weapon hit winds Null Rounds up, so the shot below lands its second hit and the Void.
        repeat(1) { state.fireClauses(EffectTrigger.WEAPON_HIT, target, hit = 20f, source = HitSource.WEAPON) }
        val before = hostileShot(target.x + 40f, target.y)
        val shot = weaponShot(state, target.x, target.y)
        val bystander = weaponShot(state, state.coreX - 600f, state.coreY)
        val after = hostileShot(target.x, target.y + 60f)
        listOf(before, shot, bystander, after).forEach(state::addProjectile)

        state.resolveCollisions(MutableGameState.FIXED_STEP)

        assertEquals(ReactionId.STASIS.ordinal + 1, target.status[StatusCell.LAST_REACTION].toInt())
        assertEquals(listOf(bystander), state.projectiles, "The spent shot is consumed and both nearby hostile shots are gone")
    }

    @Test
    fun stasisFromACoreHitSpendsNearbyHostileShotsWhileProjectileHitsResolve() {
        val state = buildTestState().apply { own("Frost Plating") }
        val hollow = state.dummy(x = state.coreX + 140f, y = state.coreY)
        state.applyStatus(hollow, StatusElement.VOID, 2, generation = 0)
        val striking = hostileShot(state.coreX, state.coreY)
        val near = hostileShot(hollow.x + 40f, hollow.y + 20f)
        val friendly = weaponShot(state, state.coreX - 600f, state.coreY)
        val distant = hostileShot(state.coreX - 500f, state.coreY + 300f)
        listOf(striking, near, friendly, distant).forEach(state::addProjectile)
        val integrity = state.hp

        state.resolveCollisions(MutableGameState.FIXED_STEP)

        assertTrue(state.hp < integrity, "The striking shot hurts the Core")
        assertEquals(ReactionId.STASIS.ordinal + 1, hollow.status[StatusCell.LAST_REACTION].toInt(), "Frost Plating's Cryo meets the Hollow")
        assertEquals(listOf(friendly, distant), state.projectiles, "Only shots outside the Stasis remain")
    }

    @Test
    fun silenceStopsAttacksForTwoSecondsAndBossesOnlyBriefly() {
        val state = buildTestState()
        val target = state.dummy(x = 400f)
        state.bossSpawned = true
        val boss = state.addEnemyForTesting(450f, 0f, hp = 100_000f, radius = 74f, type = EnemyType.ARCHITECT)
        state.applyStatus(target, StatusElement.SONIC, 3, generation = 0)
        state.applyStatus(target, StatusElement.VOID, 1, generation = 0)

        assertTrue(state.canAct(target))
        assertFalse(state.canAttack(target))
        assertEquals(state.elapsed + SILENCE_SECONDS, target.silencedUntil, 0.0001f)
        assertEquals(state.elapsed + SILENCE_BOSS_SECONDS, boss.silencedUntil, 0.0001f)
    }

    @Test
    fun chainedSilencesFromManyReactorsStillLeaveTheArchitectAttackWindows() {
        val state = buildTestState()
        state.bossSpawned = true
        val boss = state.addEnemyForTesting(400f, 0f, hp = 100_000f, radius = 74f, type = EnemyType.ARCHITECT)
        val reactors = List(4) { index ->
            val angle = index * TAU / 4f
            state.dummy(x = 400f + 120f * cos(angle), y = 120f * sin(angle))
        }
        val start = state.elapsed
        var nextSilenceAt = start
        var fired = 0
        var steps = 0
        var quietSteps = 0
        var quietRun = 0
        var longestQuiet = 0
        // Each reactor respects its own cooldowns, but together they set off a Silence every 0.6 s.
        while (state.elapsed < start + 12f) {
            if (state.elapsed >= nextSilenceAt) {
                val source = reactors[fired % reactors.size]
                state.applyStatus(source, StatusElement.SONIC, 3, generation = 0)
                state.applyStatus(source, StatusElement.VOID, 1, generation = 0)
                assertEquals(ReactionId.SILENCE.ordinal + 1, source.status[StatusCell.LAST_REACTION].toInt())
                assertEquals(state.elapsed, source.status[StatusCell.LAST_REACTION_AT], "Silence $fired fires")
                fired++
                nextSilenceAt += 0.6f
            }
            if (state.canAttack(boss)) {
                quietRun = 0
            } else {
                quietSteps++
                quietRun++
                longestQuiet = max(longestQuiet, quietRun)
            }
            steps++
            state.elapsed += MutableGameState.FIXED_STEP
        }
        assertTrue(quietSteps > 0, "Silence still reaches the Architect")
        assertTrue(longestQuiet * MutableGameState.FIXED_STEP <= SILENCE_BOSS_SECONDS + 0.02f, "no Silence is extended")
        assertTrue(quietSteps < steps * 0.4f, "the Architect attacks most of the time ($quietSteps of $steps steps silent)")
    }

    @Test
    fun chainedHushesFromManyReactorsStillLeaveTheArchitectAttackWindows() {
        val state = buildTestState()
        state.bossSpawned = true
        val boss = state.addEnemyForTesting(400f, 0f, hp = 100_000f, radius = 74f, type = EnemyType.ARCHITECT)
        val reactors = List(4) { index ->
            val angle = index * TAU / 4f
            state.dummy(x = 400f + 120f * cos(angle), y = 120f * sin(angle))
        }
        val start = state.elapsed
        var nextSilenceAt = start
        var fired = 0
        var steps = 0
        var quietSteps = 0
        var quietRun = 0
        var longestQuiet = 0
        // The same chain of Silences, now each leaving a Hush that keeps ticking over the Architect.
        while (state.elapsed < start + 12f) {
            if (state.elapsed >= nextSilenceAt) {
                val source = reactors[fired % reactors.size]
                state.applyStatus(source, StatusElement.SONIC, 3, generation = 0)
                state.applyStatus(source, StatusElement.VOID, 1, generation = 0)
                fired++
                nextSilenceAt += 0.6f
            }
            state.elapsed += MutableGameState.FIXED_STEP
            state.updateCombatFields(MutableGameState.FIXED_STEP)
            if (state.canAttack(boss)) {
                quietRun = 0
            } else {
                quietSteps++
                quietRun++
                longestQuiet = max(longestQuiet, quietRun)
            }
            steps++
        }
        assertTrue(state.fieldRecords.isNotEmpty(), "Hushes linger over the Architect")
        assertTrue(quietSteps > 0, "Silence still reaches the Architect")
        assertTrue(longestQuiet * MutableGameState.FIXED_STEP <= SILENCE_BOSS_SECONDS + 0.02f, "no Silence is extended")
        assertTrue(quietSteps < steps * 0.4f, "the Architect attacks most of the time ($quietSteps of $steps steps silent)")
    }

    @Test
    fun blackfireBurnsThroughImmunityForThreeSeconds() {
        val state = buildTestState()
        val target = state.dummy(x = 400f, hp = 100_000f)
        state.applyStatus(target, StatusElement.THERMAL, 3, generation = 0)
        state.applyStatus(target, StatusElement.VOID, 1, generation = 0)
        val rate = target.status[StatusCell.BLACKFIRE_DPS]
        assertEquals(state.content.build.reaction(ReactionId.BLACKFIRE).power * 4f * state.runScale(), rate, 0.01f)

        // Even an aspect immune to Thermal keeps burning.
        assignAspect(target, StatusElement.THERMAL.ordinal, immune = true)
        val before = target.hp
        val start = state.elapsed
        while (state.elapsed < start + 1f) {
            state.elapsed += MutableGameState.FIXED_STEP
            state.updateStatuses(MutableGameState.FIXED_STEP)
        }
        assertEquals(rate, before - target.hp, rate * 0.3f)
        while (state.elapsed < start + BLACKFIRE_SECONDS + 0.1f) {
            state.elapsed += MutableGameState.FIXED_STEP
            state.updateStatuses(MutableGameState.FIXED_STEP)
        }
        assertEquals(0f, target.status[StatusCell.BLACKFIRE_UNTIL])
        val after = target.hp
        state.elapsed += 1f
        state.updateStatuses(MutableGameState.FIXED_STEP)
        assertEquals(after, target.hp, "The burn has ended")
    }

    @Test
    fun resonantArcReachesFartherTheMoreResonanceItConsumes() {
        fun reaches(older: StatusElement, incoming: StatusElement, olderStacks: Int): Boolean {
            val state = buildTestState()
            val target = state.dummy(x = 400f)
            val far = state.dummy(x = 400f + 320f)
            state.applyStatus(target, older, olderStacks, generation = 0)
            state.applyStatus(target, incoming, 1, generation = 0)
            assertTrue(target.hp < target.maxHp, "Resonant Arc strikes its target")
            return far.hp < far.maxHp
        }
        // Base reach 220 + 25 per consumed Resonance stack: one stack reaches 245, five reach 345.
        assertFalse(reaches(StatusElement.ION, StatusElement.SONIC, 5), "one Resonance stack falls short of 320")
        assertTrue(reaches(StatusElement.SONIC, StatusElement.ION, 5), "five Resonance stacks reach 320")
    }

    @Test
    fun annihilateAlsoStrikesTheToughestEnemyWithinReach() {
        val state = buildTestState()
        val target = state.dummy(x = 400f)
        val weaker = state.dummy(x = 600f, hp = 5_000f)
        val tougher = state.dummy(x = 400f, y = 250f, hp = 9_000f)
        val distant = state.dummy(x = 900f, hp = 1_000_000f)
        state.applyStatus(target, StatusElement.ION, 3, generation = 0)
        state.applyStatus(target, StatusElement.VOID, 1, generation = 0)

        assertTrue(target.hp < target.maxHp)
        assertTrue(tougher.hp < tougher.maxHp, "The highest-integrity enemy within 300 is struck")
        assertEquals(weaker.maxHp, weaker.hp)
        assertEquals(distant.maxHp, distant.hp)
    }

    @Test
    fun nullWellStripsAWeightedShareOfMaximumIntegrity() {
        val state = buildTestState()
        val target = state.dummy(x = 400f, hp = 10_000f)
        val elite = state.dummy(x = 460f, hp = 10_000f, type = EnemyType.ELITE)
        state.applyStatus(target, StatusElement.GRAVITIC, 3, generation = 0)
        state.applyStatus(target, StatusElement.VOID, 1, generation = 0)
        val targetBefore = target.hp
        val eliteBefore = elite.hp
        state.updateCombatFields(0.4f)
        val share = state.content.build.reaction(ReactionId.NULL_WELL).power * 0.4f
        assertEquals(10_000f * share, targetBefore - target.hp, 0.5f)
        assertEquals(10_000f * share * victimFactor(elite), eliteBefore - elite.hp, 0.5f)
    }

    @Test
    fun oblivionExecutesWeakenedOrdinaryEnemiesAndElitesAtHalfTheLineButNeverBosses() {
        fun react(type: EnemyType, fraction: Float): Enemy {
            val state = buildTestState()
            val target = state.dummy(x = 400f, hp = 10_000f, type = type)
            target.hp = 10_000f * fraction
            state.applyStatus(target, StatusElement.ENTROPY, 3, generation = 0)
            state.applyStatus(target, StatusElement.VOID, 1, generation = 0)
            return target
        }
        assertFalse(react(EnemyType.DRIFTER, 0.11f).alive, "Ordinary enemies below 12 % are erased")
        assertTrue(react(EnemyType.DRIFTER, 0.13f).alive)
        assertTrue(react(EnemyType.ELITE, 0.08f).alive, "Elites hold until 6 %")
        assertFalse(react(EnemyType.ELITE, 0.05f).alive)

        val state = buildTestState()
        val boss = state.finalPhaseArchitectForTesting(400f, 0f)
        state.applyStatus(boss, StatusElement.ENTROPY, 3, generation = 0)
        state.applyStatus(boss, StatusElement.VOID, 1, generation = 0)
        assertTrue(boss.alive, "The Architect is never executed")
        assertTrue(boss.hp < 40f, "It takes the burst instead")
    }

    @Test
    fun sonicAndVoidCounterEachOtherAndEncodeInTheAffinityField() {
        assertEquals(StatusElement.VOID.ordinal, counterElement(StatusElement.SONIC.ordinal))
        assertEquals(StatusElement.SONIC.ordinal, counterElement(StatusElement.VOID.ordinal))
        val state = buildTestState()
        val elite = state.dummy(type = EnemyType.ELITE)
        assignAspect(elite, StatusElement.VOID.ordinal, immune = true)
        assertEquals(StatusElement.SONIC.ordinal, elite.weaknessElement)
        val bits = state.affinityBits(elite)
        assertEquals(StatusElement.VOID.ordinal, EnemyAffinity.aspect(bits))
        assertEquals(StatusElement.SONIC.ordinal, EnemyAffinity.weakness(bits))
        assertTrue(EnemyAffinity.immune(bits))

        state.applyStatus(elite, StatusElement.VOID, 3, generation = 0)
        assertEquals(0, elite.stacksOf(StatusElement.VOID), "An immune Void aspect refuses Hollow")
    }

    @Test
    fun sonicAndVoidArchitectsCallThreeChargersOrThreeWeavers() {
        for ((items, escort) in listOf(
            listOf("Tuning Spike", "Subwoofer Rounds") to EnemyType.CHARGER,
            listOf("Hollow Point", "Null Rounds") to EnemyType.WEAVER,
        )) {
            val state = buildTestState().apply { items.forEach { own(it) } }
            state.bossSpawned = true
            val boss = state.addEnemyForTesting(400f, 0f, hp = 3_000f, radius = 74f, type = EnemyType.ARCHITECT)
            boss.hp = 2_010f
            state.damageEnemy(boss, 50f)
            assertEquals(1, boss.phase)
            state.updateBossEncounter()
            assertEquals(List(3) { escort }, state.enemies.filterNot { it.isBoss }.map { it.type }, "$items escorts")
        }
    }

    @Test
    fun relicDirectionsOfferSonicAndVoidAspects() {
        for ((focus, relic) in listOf(RewardFocus.SONIC to RelicId.THUNDER_CHOIR, RewardFocus.VOID to RelicId.NULL_COVENANT)) {
            val state = buildTestState()
            state.openDirectedReward(DirectedReward.RELIC)
            state.selectRewardFocus(focus)
            assertEquals(relic, state.choices.first().relicId, "$focus leads with its aspect Relic")
        }
    }

    @Test
    fun everySonicAndVoidItemDoesWhatItsCardSays() {
        fun fresh(item: String) = buildTestState().apply { own(item) }

        // Clauses on a struck enemy apply their element to it, every-Nth ones on the Nth event only.
        for ((item, trigger, element, stacks, every) in listOf(
            ItemCase("Tuning Spike", EffectTrigger.IMPACT, StatusElement.SONIC, 2, 1),
            ItemCase("Subwoofer Rounds", EffectTrigger.WEAPON_HIT, StatusElement.SONIC, 1, 2),
            ItemCase("Hollow Point", EffectTrigger.CRIT, StatusElement.VOID, 3, 1),
            ItemCase("Null Rounds", EffectTrigger.WEAPON_HIT, StatusElement.VOID, 2, 2),
        )) {
            val state = fresh(item)
            val target = state.dummy()
            repeat(every - 1) { state.fireClauses(trigger, target, hit = 20f, source = HitSource.WEAPON) }
            assertEquals(0, target.stacksOf(element), "$item waits for event $every")
            state.fireClauses(trigger, target, hit = 20f, source = HitSource.WEAPON)
            assertEquals(stacks, target.stacksOf(element), "$item applies $stacks $element")
        }

        // Clauses the Core sets off spread their element around the Core.
        for ((item, trigger, element, stacks) in listOf(
            ItemCase("Shockwave Hull", EffectTrigger.HURT, StatusElement.SONIC, 2, 1),
            ItemCase("Sonic Wake", EffectTrigger.DASH, StatusElement.SONIC, 2, 1),
            ItemCase("Event Veil", EffectTrigger.SHIELD_BREAK, StatusElement.VOID, 3, 1),
        )) {
            val state = fresh(item)
            val near = state.dummy(x = state.coreX + 120f, y = state.coreY)
            val far = state.dummy(x = state.coreX + 900f, y = state.coreY)
            state.fireClauses(trigger)
            assertEquals(stacks, near.stacksOf(element), "$item reaches an enemy beside the Core")
            assertEquals(0, far.stacksOf(element), "$item stays within its radius")
            if (item == "Shockwave Hull") assertTrue(near.vx > 0f, "Shockwave Hull pushes enemies away from the Core")
        }

        // Echo Hammer answers Shatter, and only Shatter, with a burst around the broken enemy.
        val hammer = fresh("Echo Hammer")
        val broken = hammer.dummy()
        val bystander = hammer.dummy(x = broken.x + 90f)
        hammer.fireClauses(EffectTrigger.BREAK, broken, element = StatusElement.THERMAL)
        assertEquals(bystander.maxHp, bystander.hp, "Echo Hammer ignores other Breaks")
        hammer.fireClauses(EffectTrigger.BREAK, broken, element = StatusElement.SONIC)
        assertTrue(bystander.hp < bystander.maxHp, "Echo Hammer rings a Shatter out again")

        // Quiet Engine spreads Hollow from a fallen Hollow enemy, never from a clean kill.
        val engine = fresh("Quiet Engine")
        val clean = engine.dummy()
        val witness = engine.dummy(x = clean.x + 80f)
        engine.fireClauses(EffectTrigger.KILL, clean)
        assertEquals(0, witness.stacksOf(StatusElement.VOID))
        val hollow = engine.dummy(x = 1_400f).apply { status[StatusCell.STACKS + StatusElement.VOID.ordinal] = 1f }
        val neighbor = engine.dummy(x = hollow.x + 80f)
        engine.fireClauses(EffectTrigger.KILL, hollow)
        assertEquals(4, neighbor.stacksOf(StatusElement.VOID), "Quiet Engine spreads four Hollow")
        assertTrue(neighbor.hp < neighbor.maxHp, "and the fallen collapses on its neighbors")

        // Abyssal Lens: +15 % damage against an enemy with at least three Hollow.
        val lens = fresh("Abyssal Lens")
        val plain = lens.dummy()
        val deep = lens.dummy(x = 900f).apply { status[StatusCell.STACKS + StatusElement.VOID.ordinal] = 3f }
        val shallow = lens.dummy(x = 1_400f).apply { status[StatusCell.STACKS + StatusElement.VOID.ordinal] = 2f }
        lens.dealHit(plain, 100f, HitSource.WEAPON, canCrit = false)
        lens.dealHit(deep, 100f, HitSource.WEAPON, canCrit = false)
        lens.dealHit(shallow, 100f, HitSource.WEAPON, canCrit = false)
        val base = plain.maxHp - plain.hp
        assertEquals(base * 1.15f, deep.maxHp - deep.hp, base * 0.001f)
        assertEquals(base, shallow.maxHp - shallow.hp, base * 0.001f)
    }

    private data class ItemCase(val item: String, val trigger: EffectTrigger, val element: StatusElement, val stacks: Int, val every: Int)

    @Test
    fun theHeraldIsABossForSilenceStopsExecutesAndUnmake() {
        // Silence: a brief lockout, then the Herald ignores Silence like the Architect.
        val silenced = buildTestState()
        val reactor = silenced.dummy(x = 400f)
        val herald = silenced.dummy(x = 450f, hp = 100_000f, type = EnemyType.HERALD)
        silenced.applyStatus(reactor, StatusElement.SONIC, 3, generation = 0)
        silenced.applyStatus(reactor, StatusElement.VOID, 1, generation = 0)
        assertEquals(silenced.elapsed + SILENCE_BOSS_SECONDS, herald.silencedUntil, 0.0001f)

        // Stasis neither stops nor stuns it.
        val stopped = buildTestState()
        val target = stopped.dummy(x = 400f)
        val moving = stopped.dummy(x = 460f, hp = 100_000f, type = EnemyType.HERALD).apply { vx = 50f }
        stopped.applyStatus(target, StatusElement.CRYO, 3, generation = 0)
        stopped.applyStatus(target, StatusElement.VOID, 1, generation = 0)
        assertEquals(50f, moving.vx, "Stasis leaves the Herald's momentum")
        assertTrue(stopped.canAct(moving))

        // Oblivion never executes it; it takes the burst instead.
        val executed = buildTestState()
        val weakened = executed.dummy(x = 400f, hp = 10_000f, type = EnemyType.HERALD).apply { hp = 300f }
        executed.applyStatus(weakened, StatusElement.ENTROPY, 3, generation = 0)
        executed.applyStatus(weakened, StatusElement.VOID, 1, generation = 0)
        assertTrue(weakened.alive, "The Herald is never executed")
        assertTrue(weakened.hp < 300f)

        // Unmake meets its weighted integrity.
        val unmade = buildTestState()
        val boss = unmade.dummy(x = 400f, hp = 100_000f, type = EnemyType.HERALD)
        boss.status[StatusCell.HOLLOW_MEMORY] = 50_000f
        unmade.applyStatus(boss, StatusElement.VOID, 8, generation = 0)
        assertEquals(100_000f * UNMAKE_INTEGRITY_CAP * victimFactor(boss), boss.status[StatusCell.UNMAKE_DAMAGE], 0.01f)
    }

    @Test
    fun oblivionExecutesThroughAnAegisTether() {
        val state = buildTestState()
        val aegis = state.dummy(x = 900f, type = EnemyType.AEGIS)
        val target = state.dummy(x = 400f, hp = 10_000f).apply { hp = 1_000f }
        target.linkedBy = aegis.id
        target.linkedUntil = state.elapsed + 1f
        assertTrue(state.isLinked(target))
        state.applyStatus(target, StatusElement.ENTROPY, 3, generation = 0)
        state.applyStatus(target, StatusElement.VOID, 1, generation = 0)
        assertFalse(target.alive, "A tether cannot soften Oblivion")
    }

    private fun Enemy.resonant(): Enemy = apply { status[StatusCell.STACKS + StatusElement.SONIC.ordinal] = 1f }

    // Hostile shots carry the damage their source computed when it fired.
    private fun hostileShot(x: Float, y: Float) =
        Projectile(x = x, y = y, vx = 0f, vy = 0f, radius = 5f, life = 3f, hostile = true, damage = 12f)

    private fun weaponShot(state: MutableGameState, x: Float, y: Float) = Projectile(
        x = x, y = y, vx = 0f, vy = 0f, radius = 5f, life = 3f, hostile = false, damage = 10f, sourceWeapon = state.weapon,
    )
}
