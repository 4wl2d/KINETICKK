// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.DamageBasis
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.StatDelta
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.CombatField
import kinetickk.ball.gameplay.nucleus.model.CombatFieldKind
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.model.WeaponNode
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.ball.gameplay.nucleus.render.EnemyAffinity
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.WeaponNodeType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Enemy aspects, weaknesses, kinetic frames and knockback against the item system. */
class EnemyAffinityTest {
    @Test
    fun stacksLandScaledByAffinityAndCarryTheirFractions() {
        val state = buildTestState()
        // A Shooter resists Ion and is weak to Entropy.
        val resisting = state.dummy(x = 1_000f, type = EnemyType.SHOOTER)
        state.applyStatus(resisting, StatusElement.ION, 1, generation = 0)
        assertEquals(0, resisting.stacksOf(StatusElement.ION), "A resisted stack lands at half strength")
        state.applyStatus(resisting, StatusElement.ION, 1, generation = 0)
        assertEquals(1, resisting.stacksOf(StatusElement.ION), "The half carries to the next landing")
        state.applyStatus(resisting, StatusElement.ION, 4, generation = 0)
        assertEquals(3, resisting.stacksOf(StatusElement.ION))

        val exposed = state.dummy(x = 2_000f, type = EnemyType.SHOOTER)
        state.applyStatus(exposed, StatusElement.ENTROPY, 1, generation = 0)
        assertEquals(1, exposed.stacksOf(StatusElement.ENTROPY))
        state.applyStatus(exposed, StatusElement.ENTROPY, 1, generation = 0)
        assertEquals(3, exposed.stacksOf(StatusElement.ENTROPY), "The weakness lands half again, its fraction carried")

        val unrelated = state.dummy(x = 3_000f, type = EnemyType.SHOOTER)
        state.applyStatus(unrelated, StatusElement.THERMAL, 3, generation = 0)
        assertEquals(3, unrelated.stacksOf(StatusElement.THERMAL))

        // A Warden refuses Gravitic entirely and carries nothing toward it.
        val warden = state.dummy(x = 4_000f, type = EnemyType.WARDEN)
        state.applyStatus(warden, StatusElement.GRAVITIC, 5, generation = 0)
        assertEquals(0, warden.stacksOf(StatusElement.GRAVITIC))
        assertEquals(0f, warden.status[StatusCell.RESIST_CARRY])

        // A Cryo-resistant Weaver under Freeze immunity takes quarter strength; both halves carry.
        val weaver = state.dummy(x = 5_000f, type = EnemyType.WEAVER)
        weaver.status[StatusCell.FREEZE_IMMUNE_UNTIL] = 100f
        state.applyStatus(weaver, StatusElement.CRYO, 4, generation = 0)
        assertEquals(1, weaver.stacksOf(StatusElement.CRYO))
        state.applyStatus(weaver, StatusElement.CRYO, 2, generation = 0)
        assertEquals(1, weaver.stacksOf(StatusElement.CRYO))
        state.applyStatus(weaver, StatusElement.CRYO, 2, generation = 0)
        assertEquals(2, weaver.stacksOf(StatusElement.CRYO), "Eight landed stacks become two")

        val neutral = state.dummy(x = 6_000f)
        neutral.status[StatusCell.FREEZE_IMMUNE_UNTIL] = 100f
        state.applyStatus(neutral, StatusElement.CRYO, 4, generation = 0)
        assertEquals(2, neutral.stacksOf(StatusElement.CRYO), "A neutral enemy under Freeze immunity keeps half")
    }

    @Test
    fun anImmuneAspectRefusesItsElementBeforeAnySideEffect() {
        val state = buildTestState(RelicId.FURNACE_COVENANT, RelicId.ABSOLUTE_ZERO)
        assertTrue(state.build.pairing(RelicPairingId.THERMAL_PARADOX))
        // The second Elite of a run carries an immune Cryo aspect.
        state.elitesSpawned = 1
        assertTrue(state.spawnEnemy(EnemyType.ELITE))
        val elite = state.enemies.last()
        elite.status[StatusCell.STACKS + StatusElement.ION.ordinal] = 3f
        state.heat = 50f
        state.statusCuesThisStep = 0
        state.takeVisualFxCues()

        // Absolute Zero turns the Thermal into Cryo, which the Elite refuses: no vent, no stacks, no reaction.
        state.applyStatus(elite, StatusElement.THERMAL, 4, generation = 0)
        assertEquals(50f, state.heat)
        assertEquals(0, elite.stacksOf(StatusElement.CRYO))
        assertEquals(3, elite.stacksOf(StatusElement.ION))
        assertEquals(0f, elite.status[StatusCell.LAST_REACTION])
        assertEquals(0f, elite.status[StatusCell.RESIST_CARRY])
        val feedback = state.takeVisualFxCues().filterIsInstance<VisualFxCue.Burst>()
        assertEquals(1, feedback.size, "A refusal shows one small burst")
        assertEquals(elite.x, feedback.single().x)

        state.applyStatus(elite, StatusElement.THERMAL, 4, generation = 0)
        assertTrue(state.takeVisualFxCues().none { it is VisualFxCue.Burst }, "The refusal cue is rate limited")
        state.elapsed += 0.25f
        state.applyStatus(elite, StatusElement.THERMAL, 4, generation = 0)
        assertEquals(1, state.takeVisualFxCues().count { it is VisualFxCue.Burst })

        // Gaining an immune aspect drops the stacks of that element, so nothing stale keeps reacting.
        val holder = state.dummy(x = elite.x - 3_000f, y = elite.y, type = EnemyType.ELITE)
        holder.status[StatusCell.STACKS + StatusElement.ION.ordinal] = 4f
        holder.status[StatusCell.WEAK_CARRY] = 0.5f
        assignAspect(holder, StatusElement.ION.ordinal, immune = true)
        assertEquals(0, holder.stacksOf(StatusElement.ION))
        assertEquals(0f, holder.status[StatusCell.WEAK_CARRY])

        // The same landing on an enemy that accepts it vents heat and reacts with the waiting Ion.
        val accepting = state.dummy(x = elite.x + 3_000f, y = elite.y)
        accepting.status[StatusCell.STACKS + StatusElement.ION.ordinal] = 3f
        state.applyStatus(accepting, StatusElement.THERMAL, 4, generation = 0)
        assertTrue(state.heat < 50f)
        assertEquals(0, accepting.stacksOf(StatusElement.ION))
    }

    @Test
    fun elementDamageIsRefusedOnlyByAnImmuneAspect() {
        val state = buildTestState()
        // A Warden is immune to Gravitic and weak to Ion; a Shooter resists Ion and is weak to Entropy.
        val warden = state.dummy(type = EnemyType.WARDEN)
        val shooter = state.dummy(x = -400f, type = EnemyType.SHOOTER)
        state.takeVisualFxCues()

        assertEquals(0f, state.dealStatusDamage(warden, 100f, StatusElement.GRAVITIC).amount)
        assertEquals(0f, state.dealProcDamage(warden, 100f, rescale = false, element = StatusElement.GRAVITIC).amount)
        assertEquals(warden.maxHp, warden.hp)
        val refused = state.takeVisualFxCues()
        assertTrue(refused.none { it is VisualFxCue.DamageNumberAdded }, "A refusal shows no number")
        assertEquals(1, refused.count { it is VisualFxCue.Burst })

        assertEquals(100f, state.dealStatusDamage(warden, 100f, StatusElement.ION).amount, 0.01f)
        assertEquals(100f, state.dealStatusDamage(shooter, 100f, StatusElement.ION).amount, 0.01f, "Resistance never cuts damage")
        assertEquals(100f, state.dealStatusDamage(shooter, 100f, StatusElement.ENTROPY).amount, 0.01f, "A weakness never adds damage")
        assertEquals(100f, state.dealProcDamage(shooter, 100f, rescale = false, element = StatusElement.ION).amount, 0.01f)
    }

    @Test
    fun reactionDamageIgnoresAffinity() {
        val state = buildTestState()
        val primed = state.dummy(x = 2_000f)
        val immune = state.dummy(x = 2_060f, type = EnemyType.ELITE)
        assignAspect(immune, StatusElement.ION.ordinal, immune = true)
        val neutral = state.dummy(x = 1_940f)
        primed.status[StatusCell.STACKS + StatusElement.CRYO.ordinal] = 3f

        // Superconduct arcs Ion damage to its neighbours, including the one immune to Ion.
        state.applyStatus(primed, StatusElement.ION, 2, generation = 0)
        val arc = neutral.maxHp - neutral.hp
        assertTrue(arc > 0f)
        assertEquals(arc, immune.maxHp - immune.hp, 0.01f)

        // A reaction field burns a Thermal-immune enemy; an item well of the same element does not.
        val fields = buildTestState()
        val warded = fields.dummy(type = EnemyType.ELITE)
        assignAspect(warded, StatusElement.THERMAL.ordinal, immune = true)
        fun tick(kind: CombatFieldKind): Float {
            val before = warded.hp
            fields.combatFields = emptyList()
            fields.addField(CombatField(kind, remaining = 1f, x = warded.x, y = warded.y, radius = 60f,
                damagePerSecond = 100f, element = StatusElement.THERMAL, stacksPerTick = 1))
            fields.updateCombatFields(CombatField.TICK_SECONDS)
            return before - warded.hp
        }
        assertEquals(100f * CombatField.TICK_SECONDS, tick(CombatFieldKind.FUSION), 0.01f)
        assertEquals(0f, tick(CombatFieldKind.DARK_STAR))
        assertEquals(0, warded.stacksOf(StatusElement.THERMAL), "Its stacks are still refused")
    }

    @Test
    fun kineticFramesScaleEachDamageInstanceExactlyOnce() {
        val state = buildTestState()
        // An Interceptor is a light frame (impacts land harder, shots softer); a Charger wears a ram plate.
        val light = state.dummy(type = EnemyType.INTERCEPTOR)
        val plated = state.dummy(x = -400f, type = EnemyType.CHARGER)

        val impact = state.dealHit(light, 100f, HitSource.IMPACT, canCrit = false)
        assertEquals(125f, impact.amount, 0.01f)
        assertEquals(100f, impact.unmitigated, 0.01f)
        assertEquals(125f, state.dealHit(light, 100f, HitSource.DASH, canCrit = false).amount, 0.01f)
        assertEquals(85f, state.dealWeaponDamage(light, 100f).amount, 0.01f)
        assertEquals(85f, state.dealProcDamage(light, 100f).amount, 0.01f, "Element-less procs meet the weapon frame")
        assertEquals(85f, state.dealProcDamage(light, 100f, rescale = false).amount, 0.01f)
        assertEquals(100f, state.dealProcDamage(light, 100f, element = StatusElement.CRYO).amount, 0.01f,
            "Element-picked procs meet only the aspect")
        val ability = state.dealCharacterDamage(light, 100f)
        assertEquals(85f, ability.amount, 0.01f)
        assertEquals(100f, ability.unmitigated, 0.01f)
        // An echo repeats a share of a resolved hit on the same target and is not mitigated again.
        assertEquals(40f, state.dealHit(light, 40f, HitSource.ECHO).amount, 0.01f)

        assertEquals(60f, state.dealHit(plated, 100f, HitSource.IMPACT, canCrit = false).amount, 0.01f)
        assertEquals(100f, state.dealHit(plated, 100f, HitSource.WEAPON, canCrit = false).amount, 0.01f)
        val neutral = state.dummy(x = 800f)
        assertEquals(100f, state.dealHit(neutral, 100f, HitSource.IMPACT, canCrit = false).amount, 0.01f)
        assertEquals(100f, state.dealCharacterDamage(neutral, 100f).amount, 0.01f)
    }

    @Test
    fun derivedProcsMeasureTheHitBeforeTheTargetsFrame() {
        val state = buildTestState(content = contentWithClauses("Calibrated Coil",
            EffectClause(EffectTrigger.IMPACT, actions = immutableListOf(EffectAction.Chain(1, 120f, DamageBasis.HIT, 1f)))))
        state.own("Calibrated Coil")
        val plated = state.dummy(x = 400f, type = EnemyType.CHARGER)
        val besidePlated = state.dummy(x = 480f)
        val light = state.dummy(x = -400f, type = EnemyType.INTERCEPTOR)
        val besideLight = state.dummy(x = -480f)

        assertEquals(60f, state.dealHit(plated, 100f, HitSource.IMPACT, canCrit = false).amount, 0.01f)
        assertEquals(100f, besidePlated.maxHp - besidePlated.hp, 0.01f, "Armour never shrinks a neighbour's share")
        assertEquals(125f, state.dealHit(light, 100f, HitSource.IMPACT, canCrit = false).amount, 0.01f)
        assertEquals(100f, besideLight.maxHp - besideLight.hp, 0.01f, "A light frame never inflates it")
    }

    @Test
    fun echoChamberRepeatsTheResolvedHitWithoutMitigatingItAgain() {
        val state = buildTestState(RelicId.ECHO_CHAMBER)
        val light = state.dummy(type = EnemyType.INTERCEPTOR)
        val hit = state.dealWeaponDamage(light, 100f).amount
        assertEquals(85f, hit, 0.01f)
        val echo = state.echoHits.single()
        assertEquals(hit * 0.5f, echo.damage, 0.01f)

        val beforeEcho = light.hp
        repeat(60) { state.updateRelicRuntime(MutableGameState.FIXED_STEP) }
        assertTrue(state.echoHits.isEmpty())
        assertEquals(beforeEcho - echo.damage, light.hp, 0.05f)
    }

    @Test
    fun agonyCollapseExecutesAnArmoredWardenButNeverTheArchitect() {
        val state = buildTestState(RelicId.AGONY_SCEPTER)
        state.equipWeaponForTesting(WeaponId.ENTROPY_FIELD)
        assertTrue(state.agonyPower() > 0)
        val warden = state.addEnemyForTesting(state.coreX + 100f, state.coreY, hp = 1_000f, type = EnemyType.WARDEN)
            .apply { hp = 50f }
        // Below one capped hit, so an execute would finish it.
        val architect = state.finalPhaseArchitectForTesting(state.coreX - 100f, state.coreY, hp = 70f)

        state.resolveWeaponDamage(MutableGameState.FIXED_STEP)

        assertTrue(warden.hp <= 0f, "The collapse executes past the Warden's frame")
        assertTrue(warden.killClausesEligible)
        assertTrue(architect.alive, "The Architect only takes the field")
        assertTrue(architect.hp < 70f)
    }

    @Test
    fun killProcsTakeAtMostTheShareCapFromEachOtherEnemy() {
        val state = buildTestState().apply { own("Chain Detonation") }
        val victim = state.dummy(x = 400f, hp = 10_000f).apply { hp = 1f }
        val small = state.dummy(x = 460f, hp = 1_000f)
        val plated = state.dummy(x = 340f, hp = 1_000f, type = EnemyType.CHARGER)
        val large = state.dummy(x = 400f, y = 60f, hp = 100_000f)
        state.dealHit(victim, 10f, HitSource.WEAPON, canCrit = false)
        state.sweepKills()

        // Chain Detonation bursts 30 % of the victim's 10,000; each other enemy loses at most 40 % of its weighted integrity.
        assertEquals(400f, small.maxHp - small.hp, 0.01f)
        assertEquals(280f, plated.maxHp - plated.hp, 0.01f, "A Charger's integrity counts at 0.7")
        assertEquals(3_000f, large.maxHp - large.hp, 0.01f, "The cap never raises a smaller burst")
    }

    @Test
    fun chainResonanceBurstsTakeAtMostTheShareCapFromEachOtherEnemy() {
        val state = buildTestState().apply {
            listOf("Hair Trigger", "Combo Battery", "Momentum Ledger", "Killstreak Engine", "Wrecking Momentum").forEach { own(it) }
        }
        assertTrue(state.tagTier(BuildTag.CHAIN) >= 2)
        state.comboKillCounter = 11
        val victim = state.dummy(x = 400f, hp = 10_000f).apply { hp = 1f }
        val small = state.dummy(x = 460f, hp = 1_000f)
        val large = state.dummy(x = 400f, y = 60f, hp = 100_000f)
        state.dealHit(victim, 10f, HitSource.WEAPON, canCrit = false)
        state.sweepKills()

        // CHAIN II's twelfth kill bursts 60 % of the victim's 10,000.
        assertEquals(400f, small.maxHp - small.hp, 0.01f)
        assertEquals(6_000f, large.maxHp - large.hp, 0.01f)
    }

    @Test
    fun victimShareChainsAndShardsTakeAtMostTheShareCapFromEachOtherEnemy() {
        fun killNear(state: MutableGameState): Triple<Enemy, Enemy, Enemy> {
            val small = state.dummy(x = 500f, hp = 1_000f)
            val plated = state.dummy(x = 340f, hp = 1_000f, type = EnemyType.CHARGER)
            val large = state.dummy(x = 400f, y = 100f, hp = 100_000f)
            val victim = state.dummy(x = 400f, hp = 10_000f).apply { hp = 1f }
            state.dealHit(victim, 10f, HitSource.WEAPON, canCrit = false)
            state.sweepKills()
            return Triple(small, plated, large)
        }
        // Domino Protocol chains half the victim's weighted 10,000 into five neighbours on every second fall.
        val domino = buildTestState().apply { own("Domino Protocol") }
        domino.dummy(x = 5_000f, hp = 1_000f).apply { hp = 1f }.also { domino.dealHit(it, 10f, HitSource.WEAPON, canCrit = false) }
        domino.sweepKills()
        val (small, plated, large) = killNear(domino)
        assertEquals(400f, small.maxHp - small.hp, 0.01f)
        assertEquals(280f, plated.maxHp - plated.hp, 0.01f, "A Charger's integrity counts at 0.7")
        assertEquals(5_000f, large.maxHp - large.hp, 0.01f, "The cap never raises a smaller link")

        // Four shards worth 30 % of the victim's 10,000: one along +x, one along +y at the run's start.
        val shards = buildTestState(content = contentWithClauses("Calibrated Coil",
            EffectClause(EffectTrigger.KILL, actions = immutableListOf(EffectAction.Shards(4, DamageBasis.VICTIM_MAX_HP, 0.3f)))))
        shards.own("Calibrated Coil")
        shards.elapsed = 0f
        val (shardSmall, _, shardLarge) = killNear(shards)
        assertEquals(400f, shardSmall.maxHp - shardSmall.hp, 0.01f)
        assertEquals(3_000f, shardLarge.maxHp - shardLarge.hp, 0.01f)
    }

    @Test
    fun arsenalAndRamFollowUpsMeasureTheHitBeforeTheTargetsFrame() {
        fun tagged(tag: BuildTag, tier: Int): MutableGameState {
            // Items carrying the tag reduced to a pickup-radius stat, so only the resonance follow-up deals damage.
            val content = canonicalGameplayContent.copy(items = canonicalGameplayContent.items.map { item ->
                if (tag !in item.tags) item
                else item.copy(stats = immutableListOf(StatDelta(ItemEffect.PICKUP_RADIUS, 1f)), clauses = immutableListOf(),
                    rules = immutableListOf(), risk = false)
            }.toImmutableList())
            val state = buildTestState(content = content)
            for (item in content.items.filter { tag in it.tags && it.elements.isEmpty() }) {
                if (state.tagTier(tag) >= tier) break
                state.acquireItem(item.id)
            }
            assertTrue(state.tagTier(tag) >= tier, "$tag reaches tier $tier")
            assertNull(state.build.dominant)
            return state
        }
        // ARSENAL III without a dominant element: every third weapon hit adds 20 % of the hit as a proc.
        val arsenal = tagged(BuildTag.ARSENAL, 3)
        val light = arsenal.dummy(hp = 10_000f, type = EnemyType.INTERCEPTOR)
        arsenal.arsenalCounter = 2
        val shot = arsenal.dealHit(light, 100f, HitSource.WEAPON, canCrit = false)
        assertEquals(shot.unmitigated * 0.85f, shot.amount, 0.01f)
        assertEquals(shot.unmitigated * 0.2f * 0.85f, light.maxHp - light.hp - shot.amount, 0.01f,
            "The proc measures the hit before the light frame, then meets it once")

        // RAM II above 900 u/s: impacts splash 35 % of the hit around the target.
        val ram = tagged(BuildTag.RAM, 2)
        ram.setVelocityForTesting(1_000f, 0f)
        val plated = ram.dummy(hp = 10_000f, type = EnemyType.CHARGER)
        val beside = ram.dummy(x = 460f, hp = 10_000f)
        val impact = ram.dealHit(plated, 100f, HitSource.IMPACT, canCrit = false)
        assertEquals(impact.unmitigated * 0.6f, impact.amount, 0.01f)
        assertEquals(impact.unmitigated * 0.35f, beside.maxHp - beside.hp, 0.01f, "The ram plate never shrinks the splash")
    }

    @Test
    fun knockbackScalesEveryOtherDisplacementSite() {
        // How far one site moves a target of [type] near the Core: position plus velocity change.
        fun moved(type: EnemyType, relic: RelicId?, displace: MutableGameState.(Enemy) -> Unit): Float {
            val state = if (relic != null) buildTestState(relic) else buildTestState()
            val target = state.dummy(x = state.coreX + 150f, y = state.coreY, type = type).apply { vx = 100f }
            val x = target.x
            val y = target.y
            state.displace(target)
            return abs(target.x - x) + abs(target.y - y) + abs(target.vx - 100f) + abs(target.vy)
        }
        val gravitic = StatusElement.GRAVITIC.ordinal
        val sites: List<Triple<String, RelicId?, MutableGameState.(Enemy) -> Unit>> = listOf(
            Triple("Collapse pull", null) { target ->
                val source = dummy(x = target.x - 60f, y = target.y)
                applyStatus(source, StatusElement.GRAVITIC, breakThreshold(StatusElement.GRAVITIC, source), generation = 0)
            },
            Triple("Gravitic drift", null) { target ->
                val anchor = dummy(x = target.x - 200f, y = target.y)
                anchor.status[StatusCell.STACKS + gravitic] = 5f
                anchor.status[StatusCell.DECAY_AT + gravitic] = 100f
                target.status[StatusCell.STACKS + gravitic] = 1f
                target.status[StatusCell.DECAY_AT + gravitic] = 100f
                updateStatuses(0.5f)
            },
            Triple("Magnetize", null) { target ->
                val source = dummy(x = target.x - 100f, y = target.y)
                source.status[StatusCell.STACKS + StatusElement.ION.ordinal] = 3f
                applyStatus(source, StatusElement.GRAVITIC, 1, generation = 0)
            },
            Triple("Field pull", null) { target ->
                addField(CombatField(CombatFieldKind.DARK_STAR, remaining = 1f, x = target.x - 40f, y = target.y, radius = 100f,
                    damagePerSecond = 0f, pull = 240f))
                updateCombatFields(0.1f)
            },
            Triple("Event Horizon pull", RelicId.EVENT_HORIZON) { updateRelicRuntime(0.1f) },
            Triple("Entropy Field drag", null) {
                equipWeaponForTesting(WeaponId.ENTROPY_FIELD)
                resolveWeaponDamage(0.5f)
            },
        )
        for ((site, relic, displace) in sites) {
            val drifter = moved(EnemyType.DRIFTER, relic, displace)
            assertTrue(drifter > 0f, "$site moves a Drifter")
            for (anchored in listOf(EnemyType.WARDEN, EnemyType.CHARGER, EnemyType.ARCHITECT)) {
                assertEquals(0f, moved(anchored, relic, displace), "$site never moves a $anchored")
            }
            val elite = moved(EnemyType.ELITE, relic, displace)
            assertTrue(elite > 0f && elite < drifter, "$site moves an Elite only partly ($elite of $drifter)")
        }
    }

    @Test
    fun knockbackFollowsTheEnemyFrameForPushesAndMinePulls() {
        val state = buildTestState()
        var slot = 0
        fun pushed(type: EnemyType): Float {
            val target = state.dummy(x = 1_000f * ++slot, type = type)
            state.pushEnemies(target.x - 50f, target.y, 100f, 300f, inward = false)
            return target.vx
        }
        fun mined(type: EnemyType): Float {
            val target = state.dummy(x = 1_000f * ++slot, type = type)
            state.explodeMine(WeaponNode(WeaponNodeType.GRAVITY_MINE, target.x + 40f, target.y, life = 0f, maxLife = 1f,
                radius = 120f))
            return target.vx
        }
        for (displace in listOf(::pushed, ::mined)) {
            val drifter = displace(EnemyType.DRIFTER)
            assertTrue(drifter > 0f)
            for (anchored in listOf(EnemyType.WARDEN, EnemyType.CHARGER, EnemyType.ARCHITECT)) {
                assertEquals(0f, displace(anchored), "$anchored never moves")
            }
            val elite = displace(EnemyType.ELITE)
            assertTrue(elite > 0f && elite < drifter, "An Elite moves only partly")
        }
    }

    @Test
    fun ionChargeWeakensTheWardenWell() {
        fun pull(ion: Int): Float {
            val state = buildTestState()
            state.setVelocityForTesting(0f, 0f)
            val warden = state.addEnemyForTesting(state.coreX + 300f, state.coreY, type = EnemyType.WARDEN)
            warden.status[StatusCell.STACKS + StatusElement.ION.ordinal] = ion.toFloat()
            state.updateEnemies(MutableGameState.FIXED_STEP)
            return state.velocityX
        }
        val free = pull(0)
        assertTrue(free > 0f)
        assertEquals(free * 0.4f, pull(5), free * 0.001f)
        assertEquals(free * 0.2f, pull(10), free * 0.001f, "The well never drops below a fifth")
    }

    @Test
    fun aSplitterCrushedByMassOrAFreshCollapseLeavesNoFragments() {
        fun fragmentsAfter(setup: MutableGameState.(Enemy) -> Unit): Int {
            val state = buildTestState()
            val splitter = state.addEnemyForTesting(400f, 0f, hp = 500f, radius = 27f, type = EnemyType.SPLITTER)
            state.setup(splitter)
            if (splitter.alive) state.dealHit(splitter, 1_000f, HitSource.WEAPON, canCrit = false)
            state.sweepKills()
            assertTrue(splitter !in state.enemies)
            return state.enemies.count { it.type == EnemyType.DRIFTER }
        }
        val gravitic = StatusElement.GRAVITIC.ordinal
        assertEquals(2, fragmentsAfter { })
        assertEquals(2, fragmentsAfter { it.status[StatusCell.STACKS + gravitic] = 1f })
        assertEquals(0, fragmentsAfter { it.status[StatusCell.STACKS + gravitic] = 2f })
        assertEquals(0, fragmentsAfter { it.status[StatusCell.REARM_AT + gravitic] = elapsed + 0.5f })
        assertEquals(2, fragmentsAfter { it.status[StatusCell.REARM_AT + gravitic] = elapsed })
        // A Collapse kill spends the Mass, but its fresh rearm still crushes.
        assertEquals(0, fragmentsAfter { splitter ->
            splitter.hp = 1f
            applyStatus(splitter, StatusElement.GRAVITIC, 4, generation = 0)
            assertFalse(splitter.alive)
        })
    }

    @Test
    fun eliteAspectsRotateWithTheSpawnCountWithoutRandomDraws() {
        val state = buildTestState()
        assertEquals(0, state.elitesSpawned)
        val elites = List(6) {
            // Position only: an angle and a distance, as before aspects existed.
            val expected = state.gameplayRandom.copy().apply { nextFloat(); nextFloat() }.snapshot()
            assertTrue(state.spawnEnemy(EnemyType.ELITE))
            assertEquals(expected, state.gameplayRandom.snapshot())
            state.enemies.last()
        }
        val order = listOf(StatusElement.THERMAL, StatusElement.CRYO, StatusElement.ION, StatusElement.GRAVITIC,
            StatusElement.ENTROPY, StatusElement.THERMAL)
        assertEquals(order.map { it.ordinal }, elites.map { EnemyAffinity.aspect(state.affinityBits(it)) })
        assertTrue(elites.all { EnemyAffinity.immune(state.affinityBits(it)) })
        val weaknesses = listOf(StatusElement.CRYO, StatusElement.THERMAL, StatusElement.ENTROPY, StatusElement.ION,
            StatusElement.GRAVITIC, StatusElement.CRYO)
        assertEquals(weaknesses.map { it.ordinal }, elites.map { EnemyAffinity.weakness(state.affinityBits(it)) })
        assertEquals(6, state.elitesSpawned)

        // A refused spawn skips no aspect.
        val crowded = buildTestState(content = canonicalGameplayContent.copy(
            rebirth = canonicalGameplayContent.rebirth.copy(maxActiveEnemies = 3),
        ))
        repeat(3) { index -> crowded.dummy(x = 400f * (index + 1)) }
        assertFalse(crowded.spawnEnemy(EnemyType.ELITE))
        assertEquals(0, crowded.elitesSpawned)
        crowded.enemies.clear()
        assertTrue(crowded.spawnEnemy(EnemyType.ELITE))
        assertEquals(StatusElement.THERMAL.ordinal, EnemyAffinity.aspect(crowded.affinityBits(crowded.enemies.single())))

        state.startRun()
        assertEquals(0, state.elitesSpawned, "Every run starts the rotation again")
    }

    @Test
    fun enemyIntegrityFollowsTheRunCurveFromEachTypesBase() {
        val tempo = canonicalGameplayContent.tempo
        val curve = listOf(0f to 1f, 30f to 1.2458f, 120f to 1.9833f, 300f to 3.4583f, 540f to 5.425f, 720f to 6.9f)
        for ((seconds, multiplier) in curve) assertEquals(multiplier, tempo.enemyHealthMultiplier(seconds), 0.001f, "at $seconds s")
        assertEquals(6.9f, tempo.enemyHealthMultiplier(900f), "The curve stops at the boss")

        val state = buildTestState().apply { elapsed = 0f }
        assertEquals(1f, state.rebirthProfile.enemyHealth(1f), "Rebirth zero adds no integrity")
        val bases = listOf(
            EnemyType.DRIFTER to 28f, EnemyType.SHOOTER to 60f, EnemyType.INTERCEPTOR to 68f, EnemyType.CHARGER to 82f,
            EnemyType.WEAVER to 74f, EnemyType.SPLITTER to 120f, EnemyType.WARDEN to 176f, EnemyType.ELITE to 360f,
            EnemyType.ARCHITECT to 1_960f,
        )
        for ((type, base) in bases) {
            assertTrue(state.spawnEnemy(type))
            assertEquals(base, state.enemies.last().maxHp, "$type")
        }
        state.elapsed = tempo.bossAtSeconds
        assertTrue(state.spawnEnemy(EnemyType.ARCHITECT))
        assertEquals(13_524f, state.enemies.last().maxHp, 0.5f)

        // Splitter fragments follow the same curve from their own base.
        val splitter = state.addEnemyForTesting(400f, 0f, type = EnemyType.SPLITTER)
        state.enemies.retainAll { it === splitter }
        state.spawnSplitterFragments(splitter)
        val fragments = state.enemies.filter { it !== splitter }
        assertEquals(2, fragments.size)
        for (fragment in fragments) assertEquals(21f * 6.9f, fragment.maxHp, 0.01f)
    }

    @Test
    fun affinityBitsRoundTripEveryCombinationAndNeutralIsZero() {
        val none = EnemyAffinity.NONE
        assertEquals(0, EnemyAffinity.encode(none, immune = false, weakness = none, armored = false, fragile = false,
            shifting = false, phase = 0))
        val elements = listOf(none) + StatusElement.entries.map { it.ordinal }
        val flags = listOf(false, true)
        val codes = HashSet<Int>()
        var distinct = 0
        for (aspect in elements) for (immune in flags) for (weakness in elements) for (armored in flags)
            for (fragile in flags) for (shifting in flags) for (phase in 0..2) {
                val bits = EnemyAffinity.encode(aspect, immune, weakness, armored, fragile, shifting, phase)
                assertEquals(aspect, EnemyAffinity.aspect(bits))
                assertEquals(immune && aspect != none, EnemyAffinity.immune(bits), "Immunity needs an aspect")
                assertEquals(weakness, EnemyAffinity.weakness(bits))
                assertEquals(armored, EnemyAffinity.armored(bits))
                assertEquals(fragile, EnemyAffinity.fragile(bits))
                assertEquals(shifting, EnemyAffinity.shifting(bits))
                assertEquals(phase, EnemyAffinity.phase(bits))
                if (!immune || aspect != none) {
                    distinct++
                    codes += bits
                }
            }
        assertEquals(distinct, codes.size, "Every affinity has its own code")

        val state = buildTestState()
        assertEquals(0, state.affinityBits(state.dummy()), "A Drifter stays neutral")
        val warden = state.affinityBits(state.dummy(type = EnemyType.WARDEN))
        assertEquals(StatusElement.GRAVITIC.ordinal, EnemyAffinity.aspect(warden))
        assertTrue(EnemyAffinity.immune(warden))
        assertEquals(StatusElement.ION.ordinal, EnemyAffinity.weakness(warden))
        assertTrue(EnemyAffinity.armored(warden))
        val light = state.affinityBits(state.dummy(type = EnemyType.INTERCEPTOR))
        assertTrue(EnemyAffinity.fragile(light))
        assertFalse(EnemyAffinity.immune(light))
    }
}
