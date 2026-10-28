// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.EnemyNotices
import kinetickk.ball.content.api.RebirthMutator
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RunModifierEffect
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.EnemyAffinity
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The generic boss phase gate: the Architect reproduced bit for bit, and the rules every other gated boss inherits. */
class BossEncounterTest {
    @Test
    fun theGenericGateReproducesTheArchitect() {
        var cases = 0
        for (tier in intArrayOf(0, 3, 7, 10)) {
            for (singularity in booleanArrayOf(false, true)) {
                val state = buildTestState(rebirth = tier).withSingularity(singularity)
                state.elapsed = 10f
                val multiplier = state.rebirthProfile.enemyHealthMultiplier
                val final = legacyFinalPhase(singularity)
                val spec = state.architectSpec()
                assertEquals(final, state.bossFinalPhase(spec), "tier $tier, Singularity $singularity")
                assertEquals(final, state.architectFinalPhase())
                // Both round integrities divide into exact parts; the tier-scaled and the odd one do not.
                for (maxHp in floatArrayOf(3_000f, 13_524f, 3_000f * multiplier, 7_777.7f)) {
                    val boss = state.addEnemyForTesting(400f, 0f, hp = maxHp, radius = 74f, type = EnemyType.ARCHITECT)
                    val cap = legacyHitCap(maxHp, multiplier, tier)
                    assertBits(cap, state.bossHitCap(boss, spec), "cap, tier $tier")
                    assertBits(cap, state.bossHitCap(boss), "default cap, tier $tier")
                    assertBits(cap, state.architectHitCap(boss), "Architect cap, tier $tier")
                    for (phase in 0..final) {
                        val gate = legacyGate(maxHp, phase, final)
                        val share = maxHp / (final + 1f)
                        val integrities = floatArrayOf(gate + 0.5f, gate + 30f, gate + share * 0.3f, gate + share * 0.6f,
                            gate + share - 1f, gate - 20f, 40f).filter { it > 0f && it <= maxHp }
                        for (hp in integrities) {
                            for (amount in floatArrayOf(1f, 150f, 10_000f)) {
                                val label = "tier $tier, Singularity $singularity, max $maxHp, phase $phase, hp $hp, amount $amount"
                                boss.phase = phase
                                boss.hp = hp
                                boss.shiftUntil = 0f
                                assertBits(gate, state.bossGate(boss, spec), "gate, $label")
                                assertBits(gate, state.architectGate(boss), "Architect gate, $label")
                                val gated = legacyGated(hp, maxHp, phase, final, cap, amount)
                                assertBits(gated, state.gateBossDamage(boss, spec, amount), "gated, $label")
                                assertBits(gated, state.gateArchitectDamage(boss, amount), "Architect gated, $label")
                                val line = legacyBelowLine(hp, maxHp, phase, 0.35f, final)
                                assertEquals(line, belowExecutionLine(boss, 0.35f, state.bossFinalPhaseOf(boss), phased = true), label)
                                assertEquals(line, belowExecutionLine(boss, 0.35f, state.architectFinalPhase()), label)

                                // The whole integrity write: amount, integrity, phase and shift, as 0.5.0 left them.
                                val applied = state.damageEnemy(boss, amount, showNumber = false).amount
                                assertBits(gated, applied, "applied, $label")
                                val expectedHp = if (gated > 0f) hp - gated else hp
                                assertBits(expectedHp, boss.hp, "integrity, $label")
                                val advances = gated > 0f && phase < final && expectedHp > 0f &&
                                    expectedHp <= gate + maxHp * 1e-5f
                                assertEquals(if (advances) phase + 1 else phase, boss.phase, "phase, $label")
                                assertBits(if (advances) state.elapsed + 8f else 0f, boss.shiftUntil, "shift, $label")
                                assertFalse(boss.phaseStartPending, "the Architect's phase start runs in the pipeline, $label")
                                // A shifting Architect takes nothing.
                                boss.hp = hp
                                boss.phase = phase
                                boss.shiftUntil = state.elapsed + 1f
                                assertBits(0f, state.gateBossDamage(boss, spec, amount), "shifting, $label")
                                cases++
                            }
                        }
                    }
                    state.enemies.remove(boss)
                }
            }
        }
        assertTrue(cases > 1_000, "covered $cases cases")
    }

    @Test
    fun theArchitectSpecEqualsTheArchitectConstants() {
        val spec = buildTestState().architectSpec()
        assertEquals(ARCHITECT_FINAL_PHASE + 1, spec.phases)
        assertEquals(ARCHITECT_HIT_CAP, spec.hitCapShare)
        assertEquals(ARCHITECT_HIT_CAP_TIER_SHRINK, spec.hitCapTierShrink)
        assertEquals(ARCHITECT_HIT_CAP_MIN_SCALE, spec.hitCapMinimumScale)
        assertEquals(ARCHITECT_SHIFT_SECONDS, spec.shiftSeconds)
        assertEquals(1, spec.enragePhases)
        assertEquals(RebirthMutator.SINGULARITY, spec.enrageMutator)
        assertEquals(ARCHITECT_ENRAGE_ATTACK_RATE, spec.enrageAttackRate)
        assertEquals(0f, spec.enrageAfterSeconds)
    }

    @Test
    fun aRunModifierThatAddsTheSingularityAlsoAddsTheEnragedPhase() {
        val state = buildTestState()
        assertEquals(ARCHITECT_FINAL_PHASE, state.architectFinalPhase())
        state.applyRunModifier(RunModifierEffect.Mutator(RebirthMutator.SINGULARITY), preview = true)
        assertEquals(ARCHITECT_FINAL_PHASE + 1, state.architectFinalPhase())
        val boss = state.testArchitect().apply { phase = ARCHITECT_FINAL_PHASE + 1 }
        assertTrue(state.architectEnraged(boss))
        assertEquals(ARCHITECT_ENRAGE_ATTACK_RATE, state.bossAttackScale(boss))
    }

    @Test
    fun aMiniBossCapsEveryDealerAtThreePercentOfItsTierZeroIntegrity() {
        val state = buildTestState()
        // Past its last gate (thirds: phase 2 is final), so only the cap holds a hit: 3 % of 3,000.
        val boss = state.addEnemyForTesting(400f, 0f, hp = 3_000f, radius = 44f, type = EnemyType.WARDEN_PRIME)
            .apply { phase = 2 }
        val dealt = state.damageDealt

        assertEquals(90f, state.dealHit(boss, 10_000f, HitSource.ECHO).amount, 0.01f)
        assertEquals(90f, state.dealCharacterDamage(boss, 10_000f).amount, 0.01f)
        assertEquals(90f, state.dealHit(boss, 10_000f, HitSource.WEAPON, canCrit = false).amount, 0.01f)
        assertEquals(90f, state.dealStatusDamage(boss, 10_000f, StatusElement.THERMAL).amount, 0.01f)
        assertEquals(90f, state.dealProcDamage(boss, 10_000f, rescale = false).amount, 0.01f)
        assertEquals(90f, state.dealSkillDamage(boss, 10_000f, element = null).amount, 0.01f)
        state.unlockNow()
        state.castNow()
        assertEquals(90f, state.apexStrike(boss), 0.01f)
        state.apexShot(boss, 10_000f)
        state.resolveProjectileHits()

        assertEquals(3_000f - 8 * 90f, boss.hp, 0.01f)
        assertEquals(720.0, state.damageDealt - dealt, 0.01)

        // The tier shrink and the integrity multiplier apply as they do to the Architect.
        val hard = buildTestState(rebirth = 10)
        val scale = hard.rebirthProfile.enemyHealthMultiplier
        val strong = hard.addEnemyForTesting(400f, 0f, hp = 3_000f * scale, radius = 44f, type = EnemyType.WARDEN_PRIME)
            .apply { phase = 2 }
        assertEquals(72f, hard.damageEnemy(strong, 100_000f).amount, 0.05f)
    }

    @Test
    fun miniBossGatesStopAtThirdsShiftTwoSecondsAndRunTheirPhaseStartOnceOutsideTheDamagePipeline() {
        val state = buildTestState()
        val boss = state.addEnemyForTesting(400f, 0f, hp = 3_000f, radius = 44f, type = EnemyType.WARDEN_PRIME)
        assertEquals(90f, state.damageEnemy(boss, 10_000f).amount, 0.01f)
        assertEquals(0, boss.phase)

        boss.hp = 2_030f
        assertEquals(30f, state.damageEnemy(boss, 80f).amount, 0.01f)
        assertEquals(2_000f, boss.hp, 0.01f)
        assertEquals(1, boss.phase)
        assertEquals(state.elapsed + 2f, boss.shiftUntil)
        assertTrue(boss.phaseStartPending, "the phase start waits for the encounter step")
        assertEquals(listOf(boss), state.enemies, "nothing spawns inside the damage pipeline")
        assertEquals(0f, state.damageEnemy(boss, 80f).amount, "a shifting mini-boss takes nothing")

        // The step runs it once, even while the mini-boss is Frozen.
        boss.status[StatusCell.FROZEN_UNTIL] = state.elapsed + 5f
        state.simulateStep(MutableGameState.FIXED_STEP)
        assertTrue(state.isFrozen(boss))
        assertFalse(boss.phaseStartPending)
        state.simulateStep(MutableGameState.FIXED_STEP)
        assertFalse(boss.phaseStartPending)

        // The second gate, then the final third has none.
        state.elapsed = boss.shiftUntil
        boss.status[StatusCell.FROZEN_UNTIL] = 0f
        boss.hp = 1_040f
        assertEquals(40f, state.damageEnemy(boss, 80f).amount, 0.01f)
        assertEquals(2, boss.phase)
        state.elapsed = boss.shiftUntil
        boss.hp = 50f
        state.damageEnemy(boss, 80f)
        assertTrue(boss.hp <= 0f)
        assertEquals(2, boss.phase)

        // Each kind shifts for its own spec's seconds: the Throne 2.5 s.
        val throne = state.addEnemyForTesting(-400f, 0f, hp = 3_000f, radius = 48f, type = EnemyType.THRONE)
        throne.hp = 2_010f
        state.damageEnemy(throne, 50f)
        assertEquals(state.elapsed + 2.5f, throne.shiftUntil)

        // The Herald keeps no gate: a hit lands in full.
        val herald = state.addEnemyForTesting(0f, 400f, hp = 3_000f, radius = 52f, type = EnemyType.HERALD)
        assertEquals(2_500f, state.damageEnemy(herald, 2_500f).amount, 0.01f)
        assertEquals(0, herald.phase)
    }

    @Test
    fun aMiniBossRunsItsFirstPhaseStartOnItsFirstStepAndEachGateRingsAndDropsTwoRepairsFromTheStep() {
        val state = buildTestState().apply {
            runGrace = Float.MAX_VALUE
            quietArenaForTesting()
        }
        val tag = encounterTag(EncounterOwner.DIMENSION, 9)
        assertTrue(state.spawnEncounter(EncounterRequest(tag, state.coreX + 600f, state.coreY, 500f, listOf(EnemyType.THRONE))))
        val throne = state.enemies.single()
        assertTrue(throne.phaseStartPending, "a gated boss spawns with its first phase start pending")
        assertEquals(0, throne.charges)
        // Kinds without a gate never wait for one; the Architect's phase starts run in the pipeline.
        assertFalse(state.spawnedEnemy(EnemyType.HERALD, 0f, 0f).phaseStartPending)
        assertFalse(state.spawnedEnemy(EnemyType.DRIFTER, 0f, 0f).phaseStartPending)
        assertFalse(state.spawnedEnemy(EnemyType.ARCHITECT, 0f, 0f).phaseStartPending)

        state.simulateStep(MutableGameState.FIXED_STEP)
        assertFalse(throne.phaseStartPending)
        assertEquals(THRONE_ARCS, throne.charges, "phase 0 grew its arcs")
        assertTrue(state.pickups.none { it.type == kinetickk.ball.gameplay.nucleus.render.PickupType.REPAIR }, "no gate yet")
        state.takeVisualFxCues()

        // A gate: the damage pipeline only advances the phase; the step then rings, drops two repairs and shatters an arc.
        throne.hp = state.bossGate(throne, checkNotNull(state.encounterSpec(throne))) + 5f
        state.damageEnemy(throne, 20f)
        assertEquals(1, throne.phase)
        assertTrue(throne.phaseStartPending)
        assertEquals(THRONE_ARCS, throne.charges)
        assertTrue(state.pickups.isEmpty())
        val x = throne.x
        val y = throne.y
        state.updateBossEncounter()
        assertFalse(throne.phaseStartPending)
        assertEquals(THRONE_ARCS - 1, throne.charges)
        val repairs = state.pickups.filter { it.type == kinetickk.ball.gameplay.nucleus.render.PickupType.REPAIR }
        assertEquals(MINI_BOSS_SHIFT_REPAIRS, repairs.size)
        repairs.forEach { assertTrue(kotlin.math.abs(it.x - x) <= 22.01f && kotlin.math.abs(it.y - y) <= 10.01f, "at the boss") }
        val rings = state.takeVisualFxCues().filterIsInstance<kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue.ShockwaveAdded>()
        assertTrue(rings.any { it.x == x && it.y == y && it.maxRadius == throne.radius * MINI_BOSS_SHIFT_RING }, "the shift rings")
        state.updateBossEncounter()
        assertEquals(MINI_BOSS_SHIFT_REPAIRS, state.pickups.size, "once per gate")
    }

    @Test
    fun bossClassRulesCoverMiniAndSecretBosses() {
        val state = buildTestState()
        val mini = state.addEnemyForTesting(300f, 0f, hp = 3_000f, radius = 44f, type = EnemyType.BROOD_QUEEN)
        val secret = state.addEnemyForTesting(300f, 60f, hp = 3_000f, radius = 54f, type = EnemyType.CARTOGRAPHER)
        val ordinary = state.addEnemyForTesting(300f, -60f)

        // Freeze: a mini-boss not at all, a secret boss half a second with the Architect's immunity.
        val now = state.elapsed
        state.freeze(mini, 3f)
        state.freeze(secret, 3f)
        state.freeze(ordinary, 3f)
        assertFalse(state.isFrozen(mini))
        assertEquals(now + 0.5f, secret.status[StatusCell.FROZEN_UNTIL])
        assertEquals(now + 0.5f + ARCHITECT_FREEZE_IMMUNITY_SECONDS, secret.status[StatusCell.FREEZE_IMMUNE_UNTIL])
        assertEquals(now + 3f, ordinary.status[StatusCell.FROZEN_UNTIL])
        state.thaw(secret)
        assertEquals(now + ARCHITECT_FREEZE_IMMUNITY_SECONDS, secret.status[StatusCell.FREEZE_IMMUNE_UNTIL])

        // Chill slows boss-class kinds half as much.
        val chilled = buildTestState()
        val chillTargets = listOf(EnemyType.DRIFTER, EnemyType.THRONE, EnemyType.ANTECEDENT).map { type ->
            chilled.addEnemyForTesting(300f, 0f, type = type).apply { status[StatusCell.STACKS + StatusElement.CRYO.ordinal] = 3f }
        }
        val slow = 1f - chilled.enemyMotionScale(chillTargets[0])
        assertTrue(slow > 0f)
        assertEquals(slow * 0.5f, 1f - chilled.enemyMotionScale(chillTargets[1]), 1e-5f)
        assertEquals(slow * 0.5f, 1f - chilled.enemyMotionScale(chillTargets[2]), 1e-5f)

        // %max-integrity procs: the mini-boss on the Elite cooldown, the secret boss on the Architect's.
        assertEquals(2f, maxHpProcCooldown(mini))
        assertEquals(4f, maxHpProcCooldown(secret))
        assertEquals(1f, maxHpProcCooldown(ordinary))

        // Stop and stun never hold them; Elite conditions hold; neither counts as ordinary.
        mini.disabledUntil = state.elapsed + 3f
        secret.disabledUntil = state.elapsed + 3f
        ordinary.disabledUntil = state.elapsed + 3f
        assertTrue(state.canAct(mini))
        assertTrue(state.canAct(secret))
        assertFalse(state.canAct(ordinary))
        assertTrue(state.holds(EffectCondition.TargetElite, mini, HitSource.WEAPON, null))
        assertTrue(state.holds(EffectCondition.TargetElite, secret, HitSource.WEAPON, null))
        assertFalse(state.holds(EffectCondition.TargetElite, ordinary, HitSource.WEAPON, null))
        assertFalse(mini.isOrdinary)
        assertFalse(secret.isOrdinary)
        assertTrue(ordinary.isOrdinary)

        // Their phase is published like the Architect's.
        secret.phase = 2
        mini.phase = 1
        assertEquals(2, EnemyAffinity.phase(state.affinityBits(secret)))
        assertEquals(1, EnemyAffinity.phase(state.affinityBits(mini)))

        // An Aegis never tethers them.
        val linked = buildTestState()
        val aegis = linked.addEnemyForTesting(600f, 0f, type = EnemyType.AEGIS)
        val allies = listOf(EnemyType.WARDEN_PRIME, EnemyType.ANTECEDENT, EnemyType.DRIFTER).mapIndexed { index, type ->
            linked.addEnemyForTesting(600f + 40f * (index + 1), 0f, type = type)
        }
        val dx = linked.coreX - aegis.x
        val dy = linked.coreY - aegis.y
        linked.updateAegis(aegis, dx, dy, kotlin.math.sqrt(dx * dx + dy * dy), MutableGameState.FIXED_STEP)
        assertFalse(linked.isLinked(allies[0]))
        assertFalse(linked.isLinked(allies[1]))
        assertTrue(linked.isLinked(allies[2]))
    }

    @Test
    fun theEncounterClockEnragesATaggedBossAndAnUntaggedOneNever() {
        val state = MutableGameState(canonicalGameplayContent).apply {
            startRun()
            enemies.clear()
            quietArenaForTesting()
        }
        val start = state.elapsed
        val tag = encounterTag(EncounterOwner.SITE, 1)
        assertTrue(state.spawnEncounter(EncounterRequest(tag, state.coreX + 600f, state.coreY, 500f, listOf(EnemyType.WARDEN_PRIME))))
        val boss = state.enemies.single { it.type == EnemyType.WARDEN_PRIME }
        val untagged = state.addEnemyForTesting(state.coreX - 600f, state.coreY, hp = 3_000f, type = EnemyType.WARDEN_PRIME)
        assertEquals(1f, state.bossAttackScale(boss))
        state.elapsed = start + 74.9f
        assertEquals(1f, state.bossAttackScale(boss))
        state.elapsed = start + 75.01f
        assertEquals(1.3f, state.bossAttackScale(boss), "75 s after the encounter started")
        assertEquals(1f, state.bossAttackScale(untagged), "no encounter, no clock")

        // Kinds without a spec never enrage; the Architect only in its Singularity phase.
        val herald = state.addEnemyForTesting(0f, 600f, type = EnemyType.HERALD)
        assertEquals(1f, state.bossAttackScale(herald))
        val singularity = buildTestState(rebirth = 10)
        val architect = singularity.testArchitect().apply { phase = ARCHITECT_FINAL_PHASE }
        assertEquals(1f, singularity.bossAttackScale(architect))
        architect.phase = ARCHITECT_FINAL_PHASE + 1
        assertEquals(ARCHITECT_ENRAGE_ATTACK_RATE, singularity.bossAttackScale(architect))
    }

    @Test
    fun anApexStrikeOnAMiniBossTakesItsCap() {
        val state = buildTestState()
        val boss = state.addEnemyForTesting(state.coreX + 300f, state.coreY, hp = 3_000f, radius = 44f, type = EnemyType.PYRE_HERALD)
            .apply { phase = 2 }
        val herald = state.addEnemyForTesting(state.coreX - 300f, state.coreY, hp = 3_000f, radius = 52f, type = EnemyType.HERALD)
        state.unlockNow()
        state.castNow()
        assertEquals(90f, state.apexStrike(boss), 0.01f)
        assertEquals(3_000f - 90f, boss.hp, 0.01f)
        // The Herald has no spec and takes the Architect's share, as in 0.5.0.
        assertEquals(state.architectHitCap(herald), state.apexStrike(herald), 0.01f)
        assertEquals(45f, 3_000f - herald.hp, 0.01f)
    }

    @Test
    fun executionersLineReadsTheAntecedentsQuarter() {
        val state = buildTestState(RelicId.EXECUTIONERS_PRISM)
        val boss = state.addEnemyForTesting(400f, 0f, hp = 4_000f, radius = 58f, type = EnemyType.ANTECEDENT)
        assertEquals(3, state.bossFinalPhaseOf(boss))
        fun below(enemy: Enemy) =
            belowExecutionLine(enemy, 0.35f, state.bossFinalPhaseOf(enemy), phased = state.encounterSpec(enemy) != null)
        // 35 % of a 1,000 quarter.
        boss.hp = 3_300f
        assertTrue(below(boss))
        boss.hp = 3_400f
        assertFalse(below(boss))
        boss.phase = 3
        boss.hp = 900f
        assertFalse(below(boss), "the final quarter is not a free auto-crit")
        assertFalse(state.dealHit(boss, 10f, HitSource.WEAPON).critical)
        boss.hp = 300f
        assertTrue(below(boss))
        assertTrue(state.dealHit(boss, 10f, HitSource.WEAPON).critical)

        // The Herald has no spec: the line reads its whole bar, as before.
        val herald = state.addEnemyForTesting(-400f, 0f, hp = 1_000f, type = EnemyType.HERALD).apply { hp = 340f }
        assertTrue(below(herald))
        herald.hp = 360f
        assertFalse(below(herald))
    }

    @Test
    fun aSecretBossNeverEndsTheRunAndPaysItsDiscovery() {
        val state = buildTestState().apply { quietArenaForTesting() }
        val tag = encounterTag(EncounterOwner.SITE, 0x00F0_0000_0000_0010L)
        assertTrue(state.spawnEncounter(EncounterRequest(tag, state.coreX + 600f, state.coreY, 900f, listOf(EnemyType.CARTOGRAPHER))))
        assertEquals("SECRET SIGNAL // CARTOGRAPHER", state.message)
        val boss = state.enemies.single { it.type == EnemyType.CARTOGRAPHER }
        state.takeVisualFxCues()
        val eliteKills = state.eliteKills

        // Its final third, out of its shift: one capped hit finishes it.
        boss.phase = state.bossFinalPhaseOf(boss)
        boss.hp = 1f
        state.damageEnemy(boss, 10f, killEligible = true)
        state.sweepKills()
        assertEquals(GamePhase.RUNNING, state.phase, "a secret boss never ends the run")
        assertTrue(state.enemies.none { it.type == EnemyType.CARTOGRAPHER })
        assertEquals(1, assertNotNull(state.encounter(tag)).killed)
        assertEquals(eliteKills + 1, state.eliteKills, "it pays like an Elite")
        // Two Keys, a Relic and four repairs in the Architect's shift pattern (its own luck roll may add a fifth).
        assertEquals(2, state.pickups.count { it.type == PickupType.KEY })
        assertEquals(1, state.pickups.count { it.type == PickupType.RELIC })
        val repairs = state.pickups.filter { it.type == PickupType.REPAIR }
        for (index in 0 until SECRET_BOSS_DEFEAT_REPAIRS) {
            val side = if (index % 2 == 0) 1f else -1f
            assertTrue(repairs.any { it.x == boss.x + side * 22f && it.y == boss.y - 10f * index }, "repair $index")
        }
        assertTrue(repairs.size in SECRET_BOSS_DEFEAT_REPAIRS..SECRET_BOSS_DEFEAT_REPAIRS + 1)
        // The discovery that reveals it in the Codex waits for the Profile, with the defeat notice.
        assertEquals(WorldDiscovery.CARTOGRAPHER.bit, state.pendingWorldDiscoveryMask)
        assertEquals("SECRET BOSS DEFEATED // CARTOGRAPHER", state.message)
        assertEquals(SECRET_BOSS_DEFEAT_NOTICE_SECONDS, state.messageTime)
        val cues = state.takeVisualFxCues()
        assertTrue(cues.filterIsInstance<VisualFxCue.ShockwaveAdded>().any { it.life == 0.8f && it.maxRadius == boss.radius * 3.5f })
        assertTrue(cues.filterIsInstance<VisualFxCue.Burst>().any { it.requestedCount == 50 })

        // The Antecedent pays its own discovery, untagged too; a repeated discovery records nothing new.
        val antecedent = state.addEnemyForTesting(state.coreX - 400f, state.coreY, hp = 4_000f, radius = 58f, type = EnemyType.ANTECEDENT)
        antecedent.phase = state.bossFinalPhaseOf(antecedent)
        antecedent.hp = 1f
        state.damageEnemy(antecedent, 10f, killEligible = true)
        state.sweepKills()
        assertEquals(GamePhase.RUNNING, state.phase)
        assertEquals("SECRET BOSS DEFEATED // ANTECEDENT", state.message)
        val both = WorldDiscovery.CARTOGRAPHER.bit or WorldDiscovery.ANTECEDENT.bit
        assertEquals(both, state.pendingWorldDiscoveryMask)
        state.discoverWorld(WorldDiscovery.CARTOGRAPHER)
        assertEquals(both, state.pendingWorldDiscoveryMask)
        assertEquals(4, state.pickups.count { it.type == PickupType.KEY })
    }

    @Test
    fun enrageAfterTheEncounterTimeRaisesTheCadenceOnce() {
        for ((type, notice) in listOf(
            EnemyType.CARTOGRAPHER to EnemyNotices.CARTOGRAPHER_ENRAGES,
            EnemyType.ANTECEDENT to EnemyNotices.ANTECEDENT_ENRAGES,
        )) {
            val state = buildTestState().apply { quietArenaForTesting() }
            val tag = encounterTag(EncounterOwner.DIMENSION, type.ordinal.toLong())
            assertTrue(state.spawnEncounter(EncounterRequest(tag, state.coreX + 600f, state.coreY, 900f, listOf(type),
                abandonDistance = 6_000f)))
            val boss = state.enemies.single { it.type == type }
            val untagged = state.addEnemyForTesting(state.coreX - 600f, state.coreY, hp = 3_000f, radius = 54f, type = type)
            val start = assertNotNull(state.encounter(tag)).startedAt
            val after = assertNotNull(state.encounterSpec(boss)).enrageAfterSeconds
            assertEquals(if (type == EnemyType.CARTOGRAPHER) 120f else 150f, after)
            state.elapsed = start + after - 0.25f
            assertEquals(1f, state.bossAttackScale(boss))
            // Frozen across the crossing: the notice still comes from the step after enemy movement.
            state.freeze(boss, 3f)
            assertTrue(state.isFrozen(boss))
            var notices = 0
            var noticedAt = Float.NaN
            repeat(240) {
                state.message = ""
                state.elapsed += MutableGameState.FIXED_STEP
                state.updateEnemies(MutableGameState.FIXED_STEP)
                state.updateBossEncounter()
                if (state.message == notice) {
                    notices++
                    noticedAt = state.elapsed
                }
            }
            assertEquals(1, notices, "$type announces its enrage once")
            assertTrue(noticedAt - start >= after && noticedAt - MutableGameState.FIXED_STEP - start < after, "on the crossing step")
            assertEquals(1.35f, state.bossAttackScale(boss), "and stays 1.35 times faster")
            assertEquals(1f, state.bossAttackScale(untagged), "no encounter, no clock and no notice")
        }
    }

    private fun MutableGameState.withSingularity(on: Boolean): MutableGameState = apply {
        val others = activeRebirthProfile.mutators.filter { it != RebirthMutator.SINGULARITY }
        activeRebirthProfile = activeRebirthProfile.copy(
            mutators = (if (on) others + RebirthMutator.SINGULARITY else others).toImmutableList(),
        )
    }

    private fun assertBits(expected: Float, actual: Float, label: String) =
        assertEquals(expected.toRawBits(), actual.toRawBits(), "$label: expected $expected, was $actual")

    // The 0.5.0 Architect formulas, copied with their literals so the generic gate is compared against them.
    private fun legacyFinalPhase(singularity: Boolean): Int = if (singularity) 2 + 1 else 2

    private fun legacyHitCap(maxHp: Float, healthMultiplier: Float, tier: Int): Float =
        0.015f * maxHp / healthMultiplier.coerceAtLeast(1f) * (1f - 0.02f * tier).coerceIn(0.5f, 1f)

    private fun legacyGate(maxHp: Float, phase: Int, finalPhase: Int): Float =
        maxHp * (finalPhase - phase) / (finalPhase + 1f)

    private fun legacyGated(hp: Float, maxHp: Float, phase: Int, finalPhase: Int, cap: Float, amount: Float): Float {
        var gated = min(amount, cap)
        if (phase < finalPhase) {
            val gate = legacyGate(maxHp, phase, finalPhase)
            if (hp > gate) gated = min(gated, hp - gate)
        }
        return gated
    }

    private fun legacyBelowLine(hp: Float, maxHp: Float, phase: Int, fraction: Float, finalPhase: Int): Boolean {
        val share = maxHp / (finalPhase + 1f)
        val floor = share * (finalPhase - phase.coerceIn(0, finalPhase))
        return hp - floor < share * fraction
    }
}
