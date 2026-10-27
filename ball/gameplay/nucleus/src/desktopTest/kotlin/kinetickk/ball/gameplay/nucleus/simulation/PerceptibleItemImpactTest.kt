// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.ConversionKind
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ElementPick
import kinetickk.ball.content.api.EnemyType
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.MarkPick
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatDelta
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponFamily
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.isLiability
import kinetickk.ball.gameplay.nucleus.model.ApexCell
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.MarkKind
import kinetickk.ball.gameplay.nucleus.model.Pickup
import kinetickk.ball.gameplay.nucleus.model.Projectile
import kinetickk.ball.gameplay.nucleus.model.SkillCell
import kinetickk.ball.gameplay.nucleus.model.SkillRuntime
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every item matters at one stack: in scripted encounters chosen by its triggers, conditions, stats and
 * rules (fixed seeds, 40 s each), owning one copy moves at least one measured output (damage dealt, damage
 * taken, integrity left, time survived, kills, Data, Matter, Overdrive time, Dashes or offer quality) by at
 * least 5 % in the item's favor, against the same encounter without it. Each encounter is flown five times from
 * different seeds and orbit phases; an item whose best gain lands within 2.5 points of the floor is judged again
 * over fifteen, so a single lucky or unlucky draw of a chaotic encounter decides nothing. A Core that falls keeps
 * no integrity and still takes the scripted hits it would have met, so falling sooner or by less never reads as a gain;
 * [aLiabilityOrAnInertStatAloneScoresNoGain] holds the measure to that. Both runs hold the same supporting items (the
 * sources of the item's elements, a crit source for crit items, a shield for shield items) at full stacks.
 * Encounters run in dp at simulation speed 1, so each is reproducible; [JUSTIFIED_EXCEPTIONS] would list an
 * item no encounter can show, with the reason, and is empty. Armed encounters are flown with an Ion Swarm and
 * with a Sentry Grid: under the weapon proc rule (one on-hit trigger per activation, or per persistent source
 * every 0.25 s) a weapon's cadence decides how often WEAPON_HIT effects fire.
 *
 * Ricochets and slams are dispatched directly every 0.6 s; the collisions that fire them belong to the
 * obstacle field, which the scripted paths never reach (they stay inside the run origin's safe area).
 *
 * The 0.5.0 vocabulary has its own encounters: skills cast on cooldown (SKILLS), reactions forced next to the Core
 * that leave pools, hushes, bubbles and maws on its path while bolts fly at it (FIELDS), bolt volleys with one
 * erased every 0.6 s (SHOTS), pre-marked packs rammed and braked on (MARKS) and the Apex unlocked and cast whenever
 * ready (APEX); they also measure bolts erased, refunded seconds, control seconds, distance flown at 900 u/s and Apex
 * recharge progress. Protection that holds only at speed is flown through fast passes while hits land (RUSH), a keystone
 * that strips defenses also against Elites immune to the weapon's element, and a Spored mark where marked targets die.
 * A 0.5.0 action whose trigger its encounter never fires is flown there again with the trigger scripted in (a Brake,
 * ricochets, fragile targets that fall), and an IntegrityBelow item is flown wounded below its threshold. A skill stat that
 * holds only at speed is flown through the fast passes with the skill loadout (SPRINT), and a thrower's targets fly their
 * throws out before they are anchored again, its slams counted (SLAMS) as Dashes are for Dash items. An item whose skill
 * condition (a skill or a skill tag) the skill loadout cannot meet is flown with the first such skill in the loadout's
 * last slot, so the clause can fire where it is measured.
 * [everyNewVocabularyKindMovesItsEncounter] holds one probe per new kind to those encounters.
 *
 * Active items (R1.13): an active item is measured in the skill arena with only itself in the loadout, acquired as the
 * item at one stack, its own passive included. The skill arenas' loadout is support, not the subject: since every active
 * item carries a passive, the loadout's (Solar Flare's and Arc Hook's element potency, Catalyze's reaction power) would
 * lift both arms and hide what a skill-support item adds, which no loadout did before skills became items. A test-only
 * content transform ([withInertLoadoutPassives]) therefore gives each loadout active an inert passive (0.01 % less
 * damage taken while braking, which no skill arena output measures) wherever a loadout is held, so those arenas keep the
 * conditions they had before active items; the item under test is never transformed.
 */
class PerceptibleItemImpactTest {
    @Test
    fun aLiabilityOrAnInertStatAloneScoresNoGain() {
        val ledger = canonicalGameplayContent.items.single { it.name == "Gambler's Ledger" }
        val controls = listOf(
            "Gambler's Ledger's liability alone" to ledger.copy(rules = immutableListOf(), risk = false),
            // MAX_INTEGRITY +0.001: a stat no encounter can feel.
            "an inert stat" to ledger.copy(stats = immutableListOf(StatDelta(ItemEffect.MAX_INTEGRITY, 0.001f)), rules = immutableListOf(),
                risk = false),
        )
        for ((label, control) in controls) {
            val content = canonicalGameplayContent.copy(items = canonicalGameplayContent.items.map { item ->
                if (item.id == control.id) control else item
            }.toImmutableList())
            // Every combat encounter: an owned card only leaves the offer pool, which says nothing about the measure.
            for (arena in Arena.entries.filter { it != Arena.OFFERS }) {
                for (weapon in arena.weapons()) {
                    val setup = Setup(arena, emptyList(), elite = false, weapon = weapon)
                    val (output, share) = bestGain(setup, run(setup, null, content), run(setup, control, content))
                    assertTrue(share < MIN_GAIN, "$label scores $arena/$weapon $output ${"%+.1f".format(share * 100f)} %")
                }
            }
        }
    }

    @Test
    fun everyItemAtOneStackMovesAMeasuredOutputByAtLeastFivePercent() {
        val baselines = HashMap<Setup, Outcome>()
        val report = ArrayList<String>()
        val misses = ArrayList<String>()
        val confirmedBaselines = HashMap<Setup, Outcome>()
        println("IMPACT range ${ImpactRange.first}..${minOf(ImpactRange.last, canonicalGameplayContent.items.lastIndex)}")
        for (item in canonicalGameplayContent.items.filter { it.id in ImpactRange }) {
            val measured = setupsFor(item).map { setup ->
                val baseline = baselines.getOrPut(setup) { run(setup, null) }
                val (output, share) = bestGain(setup, baseline, run(setup, item))
                Triple(setup, output, share)
            }
            if (ImpactEverySetup) measured.forEach { (s, o, g) -> println("SETUP ${item.id} ${s.label()} $o ${"%+.1f".format(g * 100f)} %") }
            var best = measured.maxBy { it.third }
            var fights = VARIANTS
            if (best.third in MIN_GAIN - CONFIRM_BAND..MIN_GAIN + CONFIRM_BAND) {
                // A best gain near the floor is one draw of a chaotic measure: every encounter that came near is
                // flown again from more seeds and orbit phases, and the item is judged on the larger sample.
                fights = CONFIRM_VARIANTS
                best = measured.filter { it.third >= MIN_GAIN - CONFIRM_BAND }.map { (setup, _, _) ->
                    val baseline = confirmedBaselines.getOrPut(setup) { run(setup, null, variants = CONFIRM_VARIANTS) }
                    val (output, share) = bestGain(setup, baseline, run(setup, item, variants = CONFIRM_VARIANTS))
                    Triple(setup, output, share)
                }.maxBy { it.third }
            }
            val (setup, output, share) = best
            val line = "${item.id} ${item.name}: ${setup.label()} " +
                "${setup.support} $output ${"%+.1f".format(share * 100f)} % ($fights fights)"
            report += line
            if (share < MIN_GAIN && item.name !in JUSTIFIED_EXCEPTIONS) misses += line
        }
        report.forEach { println("IMPACT $it") }
        baselines.forEach { (setup, outcome) ->
            println("BASELINE ${setup.label()} ${setup.support} $outcome")
        }
        assertTrue(JUSTIFIED_EXCEPTIONS.keys.all { name -> canonicalGameplayContent.items.any { it.name == name } }, "Every exception names an item")
        assertTrue(misses.isEmpty(), "Items that move no measured output by 5 %:\n" + misses.joinToString("\n"))
    }

    /**
     * The 0.5.0 vocabulary before any item uses it: one probe item per new trigger, condition, action, rule,
     * conversion and buff (each at a strength its validation allows) must move a measured output of the encounters
     * [setupsFor] picks for it by at least [MIN_GAIN], under the same measure as the catalog. Kinds no scripted
     * encounter can show are listed in [UNPROBED_VOCABULARY] with the reason.
     */
    @Test
    fun everyNewVocabularyKindMovesItsEncounter() {
        val baselines = HashMap<Setup, Outcome>()
        val misses = ArrayList<String>()
        for ((label, draft) in vocabularyProbes()) {
            val probe = draft.copy(id = canonicalGameplayContent.items.size)
            val content = canonicalGameplayContent.copy(items = (canonicalGameplayContent.items + probe).toImmutableList())
            val (setup, output, share) = setupsFor(probe).map { setup ->
                val baseline = baselines.getOrPut(setup) { run(setup, null) }
                val (output, share) = bestGain(setup, baseline, run(setup, probe, content))
                Triple(setup, output, share)
            }.maxBy { it.third }
            val line = "$label: ${setup.arena}/${setup.weapon} $output ${"%+.1f".format(share * 100f)} %"
            println("VOCABULARY $line")
            if (share < MIN_GAIN) misses += line
        }
        assertTrue(misses.isEmpty(), "Vocabulary probes that move no measured output by 5 %:\n" + misses.joinToString("\n"))
    }

    /** One probe item per new vocabulary kind, labelled by the kind it shows. */
    private fun vocabularyProbes(): List<Pair<String, ItemDefinition>> {
        fun on(trigger: EffectTrigger, vararg actions: EffectAction, given: List<EffectCondition> = emptyList(), cooldown: Float = 0f, every: Int = 1) =
            kinetickk.ball.content.api.EffectClause(trigger, given.toImmutableList(), actions.toList().toImmutableList(), cooldown, every)
        fun probe(
            clauses: List<kinetickk.ball.content.api.EffectClause> = emptyList(),
            rules: List<ItemRule> = emptyList(),
            elements: List<StatusElement> = emptyList(),
            rarity: kinetickk.ball.content.api.ItemRarity = kinetickk.ball.content.api.ItemRarity.RARE,
        ) = ItemDefinition(
            id = 0, name = "Vocabulary Probe", description = "Shows one piece of the 0.5.0 vocabulary.", rarity = rarity,
            tags = immutableListOf(kinetickk.ball.content.api.BuildTag.CHAIN), elements = elements.toImmutableList(),
            clauses = clauses.toImmutableList(), rules = rules.toImmutableList(), maxStacks = 1, unlockLevel = 1,
            risk = rules.any { it.isLiability },
        )
        val weaponBurst = EffectAction.Burst(200f, kinetickk.ball.content.api.DamageBasis.WEAPON, 1f)
        return listOf(
            "SKILL_CAST" to probe(listOf(on(EffectTrigger.SKILL_CAST, weaponBurst))),
            "SKILL_HIT" to probe(listOf(on(EffectTrigger.SKILL_HIT, EffectAction.Strike(kinetickk.ball.content.api.DamageBasis.HIT, 0.5f)))),
            "APEX" to probe(listOf(on(EffectTrigger.APEX, EffectAction.Buff(BuffStat.DAMAGE, 0.5f, 20f)))),
            "FIELD_ENTER" to probe(listOf(on(EffectTrigger.FIELD_ENTER, weaponBurst))),
            "SHOT_ERASED" to probe(listOf(on(EffectTrigger.SHOT_ERASED, EffectAction.Shield(4f), cooldown = 0.5f))),
            "MARK_CONSUMED" to probe(listOf(on(EffectTrigger.MARK_CONSUMED, EffectAction.Burst(160f, kinetickk.ball.content.api.DamageBasis.IMPACT, 0.6f)))),
            "ReactionIs" to probe(
                listOf(on(EffectTrigger.REACTION, weaponBurst, given = listOf(EffectCondition.ReactionIs(ReactionId.PLASMA_BURST)))),
                elements = listOf(StatusElement.THERMAL, StatusElement.ION),
            ),
            "SkillIs" to probe(listOf(on(EffectTrigger.SKILL_CAST, EffectAction.Burst(300f, kinetickk.ball.content.api.DamageBasis.WEAPON, 3f),
                given = listOf(EffectCondition.SkillIs(SkillId.SOLAR_FLARE))))),
            "SkillTagged" to probe(listOf(on(EffectTrigger.SKILL_HIT, EffectAction.Strike(kinetickk.ball.content.api.DamageBasis.HIT, 1f),
                given = listOf(EffectCondition.SkillTagged(kinetickk.ball.content.api.BuildTag.OVERCLOCK))))),
            "CoreInField" to probe(rules = listOf(ItemRule.WhileModifier(EffectCondition.CoreInField, BuffStat.DAMAGE, 0.5f))),
            "TargetMarked" to probe(rules = listOf(ItemRule.DamageVersus(EffectCondition.TargetMarked, 0.5f))),
            "TargetControlled" to probe(rules = listOf(ItemRule.DamageVersus(EffectCondition.TargetControlled, 0.3f))),
            "TargetThrown" to probe(listOf(on(EffectTrigger.IMPACT, EffectAction.Strike(kinetickk.ball.content.api.DamageBasis.IMPACT, 1f),
                given = listOf(EffectCondition.TargetThrown)))),
            "ApexActive" to probe(rules = listOf(ItemRule.WhileModifier(EffectCondition.ApexActive, BuffStat.DAMAGE, 0.5f))),
            "SkillsCharging" to probe(rules = listOf(ItemRule.WhileModifier(EffectCondition.SkillsCharging(2), BuffStat.DAMAGE, 0.3f))),
            "WeaponFamilyIs" to probe(rules = listOf(ItemRule.WhileModifier(EffectCondition.WeaponFamilyIs(WeaponFamily.PULSE), BuffStat.WEAPON, 0.4f))),
            "RefundSkills" to probe(listOf(on(EffectTrigger.KILL, EffectAction.RefundSkills(1f), cooldown = 0.5f))),
            "ChargeApex" to probe(listOf(on(EffectTrigger.KILL, EffectAction.ChargeApex(3f), cooldown = 0.5f))),
            "ForceReaction" to probe(
                listOf(on(EffectTrigger.WEAPON_HIT, EffectAction.ForceReaction(200f, 3), cooldown = 1f)),
                elements = listOf(StatusElement.THERMAL, StatusElement.CRYO),
            ),
            "Zone" to probe(listOf(on(EffectTrigger.DASH, EffectAction.Zone(kinetickk.ball.content.api.ZoneKind.TIME_BUBBLE, 200f, 3f), cooldown = 2f))),
            "EraseShots" to probe(listOf(on(EffectTrigger.HURT, EffectAction.EraseShots(250f, 6), cooldown = 1f))),
            "Throw" to probe(listOf(on(EffectTrigger.DASH_HIT, EffectAction.Throw(600f), cooldown = 0.3f))),
            "CoreImpulse" to probe(listOf(on(EffectTrigger.DASH,
                EffectAction.CoreImpulse(600f, kinetickk.ball.content.api.ImpulseDirection.FORWARD), cooldown = 0.5f))),
            "ApplyMark" to probe(listOf(on(EffectTrigger.IMPACT, EffectAction.ApplyMark(kinetickk.ball.content.api.MarkPick.CRACKED, 4f), cooldown = 0.3f))),
            "PrimeDash" to probe(listOf(on(EffectTrigger.DASH, EffectAction.PrimeDash, every = 2))),
            "ReactionKeystone" to probe(rules = listOf(ItemRule.ReactionKeystone(ReactionId.PLASMA_BURST)),
                elements = listOf(StatusElement.THERMAL, StatusElement.ION), rarity = kinetickk.ball.content.api.ItemRarity.EPIC),
            "ReactionTuning" to probe(rules = listOf(ItemRule.ReactionTuning(null, kinetickk.ball.content.api.ReactionAspect.RADIUS, 0.5f))),
            "FieldTuning" to probe(rules = listOf(ItemRule.FieldTuning(kinetickk.ball.content.api.FieldAspect.DURATION, 0.5f))),
            "SkillCooldown" to probe(rules = listOf(ItemRule.SkillCooldown(null, 0.25f))),
            "SkillPower" to probe(rules = listOf(ItemRule.SkillPower(null, 0.5f))),
            "SkillCharges" to probe(rules = listOf(ItemRule.SkillCharges)),
            "CatalysisRate" to probe(rules = listOf(ItemRule.CatalysisRate(1f))),
            "ApexCooldown" to probe(rules = listOf(ItemRule.ApexCooldown(0.2f))),
            "DASH_HEAT_TO_SKILLS" to probe(rules = listOf(ItemRule.Convert(ConversionKind.DASH_HEAT_TO_SKILLS, 1f))),
            "DATA_TO_SKILLS" to probe(rules = listOf(ItemRule.Convert(ConversionKind.DATA_TO_SKILLS, 0.5f))),
            "SKILLS_COST_HEAT" to probe(rules = listOf(ItemRule.Convert(ConversionKind.SKILLS_COST_HEAT, 10f))),
            "KNOCKBACK_TO_DAMAGE" to probe(rules = listOf(ItemRule.Convert(ConversionKind.KNOCKBACK_TO_DAMAGE, 1f))),
            "SKILL_POWER buff" to probe(listOf(on(EffectTrigger.SKILL_CAST, EffectAction.Buff(BuffStat.SKILL_POWER, 0.5f, 6f)))),
            "SKILL_RECHARGE buff" to probe(listOf(on(EffectTrigger.KILL, EffectAction.Buff(BuffStat.SKILL_RECHARGE, 1f, 5f), cooldown = 1f))),
        )
    }

    /**
     * One scripted encounter and the items held in it with and without the item under test. An armed arena is
     * flown with each of [ARENA_WEAPONS]: the proc rule lets one hit per activation fire WEAPON_HIT effects, so how
     * often they fire depends on the weapon's cadence (a discrete volley or a persistent source).
     */
    private data class Setup(
        val arena: Arena,
        val support: List<String>,
        val elite: Boolean,
        val weapon: WeaponId?,
        /** The pilot keeps the Core inside the start area ([leash]): the vocabulary encounters, and any item that launches it. */
        val leashed: Boolean = arena.vocabulary,
        /** Every target is an Elite whose aspect is immune to this element (null: plain targets). */
        val immuneTo: StatusElement? = null,
        /**
         * A trigger the arena's own script never fires, scripted in for an item whose 0.5.0 action is measured there: a
         * held Brake for BRAKE_RELEASE, ricochets and slams for RICOCHET and SLAM, targets as fragile as the crowd's for KILL.
         */
        val trigger: EffectTrigger? = null,
        /**
         * Above 0, a deep-pool arena starts the Core at this share of its integrity, so an IntegrityBelow condition holds
         * where the attackers' damage is measured (the pool keeps it from ever falling there on its own).
         */
        val wounded: Float = 0f,
        /** A target the item throws flies its throw out (slamming whatever it meets) before it is anchored again. */
        val loose: Boolean = false,
        /**
         * The skill loadout of a skill arena, acquired as active items at rank II: [ArenaSkills], or with the skill an item
         * names (by id or tag) in its place, or none for an active item, whose own effect is the whole loadout.
         */
        val skills: List<SkillId> = ArenaSkills,
    )

    private fun Arena.weapons(): List<WeaponId?> = if (armed) ARENA_WEAPONS else listOf(null)

    /** The arena and weapon, and what the setup scripts beyond the arena's own encounter. */
    private fun Setup.label(): String = "$arena/$weapon" + immuneTo?.let { " vs $it-immune" }.orEmpty() +
        trigger?.let { " with $it scripted" }.orEmpty() + (if (wounded > 0f) " wounded to $wounded" else "") +
        (if (loose) " loose" else "") + (if (skills != ArenaSkills) " skills $skills" else "")

    private enum class Arena(
        /** False silences the weapon so only impacts and procs deal damage. */
        val armed: Boolean,
        val motion: Motion,
        val targets: Int,
        val integrity: Float,
        /** Seconds between Dash attempts (0 = none); a Dash is only requested while one is ready. */
        val dashEvery: Float = 0f,
        val brake: Boolean = false,
        /** Bursts of four hits of this size land every 6 s (0 = none). */
        val hits: Float = 0f,
        /** A ricochet and a slam on the nearest enemy every 0.6 s. */
        val obstacles: Boolean = false,
        /** Overdrive is forced every 10 s. */
        val forcedOverdrive: Boolean = false,
        /** Every target presses a steady attack on the Core, scaled by what its statuses leave of it. */
        val pressure: Boolean = false,
        /** Every target fires a bolt at the Core each second and one bolt near the Core is erased every 0.6 s. */
        val shots: Boolean = false,
        /**
         * The Orb holds the Solar Flare, Arc Hook and Catalyze active items at rank II (an active item: itself at rank I)
         * and casts each the moment it is ready.
         */
        val skills: Boolean = false,
        /** Every 1.2 s the target nearest the Core is made to react: a Plasma Burst, Silence, Stasis or Null Well. */
        val fields: Boolean = false,
        /** Every 2 s the targets are marked again, half Spalled and half Cracked. */
        val marks: Boolean = false,
        /** The Apex is unlocked at the start and cast whenever it is ready. */
        val apex: Boolean = false,
        val seconds: Float = SECONDS,
    ) {
        /** Impacts only: the weapon is silenced; the Core orbits through a ring and dashes into it. */
        IMPACT(false, Motion.ORBIT, 10, 600f, dashEvery = 0.8f),
        /** Impacts at high speed: straight passes through a column, dashing as often as heat allows. */
        FAST(false, Motion.PASS, 8, 600f, dashEvery = 0.5f),
        /** The weapon only: the Core circles slowly outside a cluster it never touches. */
        WEAPON(true, Motion.HOVER, 8, 900f),
        /** Weapon hits and impacts together on sturdy targets: statuses, Breaks and reactions. */
        STATUS(true, Motion.ORBIT, 10, 1_500f, dashEvery = 2f),
        /**
         * The status ring with weapon hits and impacts reduced to taps: they still apply statuses, but the damage is
         * almost all statuses, Breaks and reactions.
         */
        ALCHEMY(true, Motion.ORBIT, 10, 1_500f, dashEvery = 2f),
        /** Fragile targets that keep coming back: kills, Data, Matter and combos. */
        CROWD(true, Motion.ORBIT, 12, 260f, dashEvery = 2f),
        /**
         * Fragile targets felled by the weapon alone while the Core circles outside them, every other one a sturdy
         * neighbor that outlives them: kills without collisions, and something left standing to spread onto.
         */
        CULL(true, Motion.HOVER, 8, 140f),
        /** The crowd with levels counting: level-ups (and the weapon amplifications they bring) happen. */
        LEVELS(true, Motion.ORBIT, 12, 260f, dashEvery = 2f),
        /** Sturdier targets that fall seconds apart. */
        SPARSE(true, Motion.ORBIT, 10, 600f, dashEvery = 2f),
        /** The weapon circle while a straggler falls every 3 s: the combo window decides whether the chain survives. */
        COMBO(true, Motion.HOVER, 8, 900f),
        /** A slow circle close around a cluster while bursts of hits land every 6 s (shields recharge between). */
        DEFENSE(true, Motion.CLOSE_HOVER, 8, 1_500f, hits = 4.5f),
        /** The defense circle with a held Brake most of the time. */
        BRACE(true, Motion.CLOSE_HOVER, 8, 1_500f, brake = true, hits = 4.5f),
        /**
         * A ring of attackers around the Core circling slowly inside it: each presses a steady attack, which Chill
         * slows, a Freeze stops, Silence mutes and Hollow weakens, so their statuses show as damage not taken.
         */
        SIEGE(true, Motion.INNER_HOVER, 10, 1_500f, pressure = true),
        /** The status ring with a held Brake every 1.8 s. */
        BRAKE(true, Motion.ORBIT, 10, 1_500f, brake = true),
        /** A Dash every 0.7 s whenever one is ready: heat outruns cooling, so the Core overheats again and again. */
        HEAT(true, Motion.ORBIT, 10, 1_500f, dashEvery = 0.7f),
        /** The status ring with Overdrive forced every 10 s. */
        OVERDRIVE(true, Motion.ORBIT, 10, 1_500f, dashEvery = 2f, forcedOverdrive = true),
        /** The status ring with a ricochet and a slam every 0.6 s and Dashes. */
        OBSTACLE(true, Motion.ORBIT, 10, 1_500f, dashEvery = 0.6f, obstacles = true),
        /** Ricochets and slams while bursts of hits land, circling close outside a cluster. */
        SHELTER(true, Motion.CLOSE_HOVER, 8, 1_500f, hits = 4.5f, obstacles = true),
        /** Loose Data just beyond the build's own pickup radius: only a wider reach collects it. */
        REACH(true, Motion.HOVER, 8, 900f),
        /** No combat: thirty item offers at Reborn 10. */
        OFFERS(false, Motion.HOVER, 0, 0f),
        /** Skills cast on cooldown around a ring that falls and returns: skill damage, refunds and the Data they bring. */
        SKILLS(true, Motion.ORBIT, 10, 600f, dashEvery = 2f, skills = true),
        /** Reaction fields on the Core's path while every target presses and fires at it. */
        FIELDS(true, Motion.ORBIT, 10, 1_500f, dashEvery = 2f, pressure = true, shots = true, fields = true),
        /** Bolt volleys at a slow circle close around a cluster, one bolt erased every 0.6 s. */
        SHOTS(true, Motion.CLOSE_HOVER, 8, 1_500f, shots = true),
        /** Pre-marked targets rammed, dashed through and braked on: Spall throws and Cracked detonations. */
        MARKS(true, Motion.ORBIT, 10, 1_500f, dashEvery = 1f, brake = true, marks = true),
        /** The Apex from the start: cast at once and whenever ready, over a fight long enough for a cooldown cut to tell. */
        APEX(true, Motion.ORBIT, 10, 1_500f, dashEvery = 2f, apex = true, seconds = 100f),
        /** The fast passes while bursts of hits land every 6 s: protection that holds only at speed. */
        RUSH(false, Motion.PASS, 8, 600f, dashEvery = 0.5f, hits = 4.5f),
        /** The fast passes with the skill loadout cast on cooldown: a skill clock that runs only at speed. */
        SPRINT(false, Motion.PASS, 8, 600f, dashEvery = 0.5f, skills = true),
        ;

        /** Only these arenas count integrity: elsewhere glancing contacts are topped up and integrity is ignored. */
        val defended: Boolean get() = hits > 0f || pressure || shots

        /** The 0.5.0 vocabulary encounters, whose hooks and launches the pilot keeps inside the start area. */
        val vocabulary: Boolean get() = skills || fields || marks || apex || shots
    }

    private enum class Motion { ORBIT, PASS, HOVER, CLOSE_HOVER, INNER_HOVER }

    /** The measured outputs; each arena reads the ones it is built to show. */
    private enum class Output {
        DAMAGE, TAKEN, INTEGRITY, SURVIVAL, KILLS, DATA, MATTER, DATA_PER_KILL, MATTER_PER_KILL, OVERDRIVE, DASHES, OFFERS,
        /** Hostile bolts erased. */
        ERASED,
        /** Skill and Apex cooldown seconds refunded. */
        REFUNDED,
        /** Target-seconds silenced or Frozen. */
        CONTROL,
        /** Distance the Core flew at 900 u/s or more. */
        FAST_DISTANCE,
        /** Seconds the Apex ran. */
        APEX,
        /**
         * Apex recharges completed after its first cast, the one under way counting by the share already done: a
         * cooldown cut shows even when the window holds no extra cast (60 s → 51 s: 1.67 → 1.96 over 100 s).
         */
        APEX_RECHARGE,
        /** Enemy slams: a thrown enemy meeting another (or a rock) hard enough, the resource slam builds spend. */
        SLAMS,
    }

    /** The arena's outputs; a thrower's setup also counts the slams its throws cause. */
    private fun Setup.outputs(): List<Output> = if (loose) arena.outputs() + Output.SLAMS else arena.outputs()

    private fun Arena.outputs(): List<Output> = when (this) {
        Arena.IMPACT, Arena.FAST, Arena.HEAT -> listOf(Output.DAMAGE, Output.DASHES)
        Arena.WEAPON, Arena.STATUS, Arena.ALCHEMY, Arena.BRAKE, Arena.OVERDRIVE -> listOf(Output.DAMAGE)
        Arena.CROWD, Arena.SPARSE, Arena.LEVELS, Arena.CULL, Arena.COMBO -> listOf(
            Output.DAMAGE, Output.KILLS, Output.DATA, Output.MATTER, Output.DATA_PER_KILL, Output.MATTER_PER_KILL, Output.OVERDRIVE,
        )
        Arena.DEFENSE, Arena.BRACE -> listOf(Output.DAMAGE, Output.TAKEN, Output.INTEGRITY, Output.SURVIVAL)
        Arena.SIEGE -> listOf(Output.DAMAGE, Output.TAKEN)
        Arena.OBSTACLE -> listOf(Output.DAMAGE, Output.DASHES, Output.OVERDRIVE, Output.DATA)
        Arena.SHELTER -> listOf(Output.DAMAGE, Output.TAKEN, Output.INTEGRITY, Output.SURVIVAL, Output.OVERDRIVE)
        Arena.REACH -> listOf(Output.DATA)
        Arena.OFFERS -> listOf(Output.OFFERS)
        Arena.SKILLS -> listOf(Output.DAMAGE, Output.REFUNDED, Output.KILLS, Output.DATA)
        Arena.FIELDS -> listOf(Output.DAMAGE, Output.TAKEN, Output.CONTROL, Output.ERASED, Output.FAST_DISTANCE)
        Arena.SHOTS -> listOf(Output.DAMAGE, Output.TAKEN, Output.INTEGRITY, Output.ERASED)
        Arena.MARKS -> listOf(Output.DAMAGE, Output.DASHES, Output.FAST_DISTANCE)
        Arena.APEX -> listOf(Output.DAMAGE, Output.REFUNDED, Output.APEX, Output.APEX_RECHARGE)
        Arena.RUSH -> listOf(Output.DAMAGE, Output.TAKEN, Output.INTEGRITY, Output.SURVIVAL)
        Arena.SPRINT -> listOf(Output.DAMAGE, Output.REFUNDED, Output.DASHES)
    }

    /** What an encounter measured. Higher is better except for damage taken. */
    private data class Outcome(
        val damage: Double,
        val taken: Double,
        val integrity: Float,
        /** Seconds the Core stayed in the fight. */
        val survival: Float,
        val kills: Int,
        val data: Long,
        val matter: Long,
        val overdrive: Float,
        val dashes: Int,
        val offers: Float,
        val erased: Long = 0L,
        val refunded: Float = 0f,
        val control: Float = 0f,
        val fastDistance: Float = 0f,
        val apex: Float = 0f,
        val apexRecharge: Float = 0f,
        val slams: Int = 0,
    ) {
        fun value(output: Output): Double = when (output) {
            Output.DAMAGE -> damage
            Output.TAKEN -> -taken
            Output.INTEGRITY -> integrity.toDouble()
            Output.SURVIVAL -> survival.toDouble()
            Output.KILLS -> kills.toDouble()
            Output.DATA -> data.toDouble()
            Output.MATTER -> matter.toDouble()
            Output.DATA_PER_KILL -> data.toDouble() / max(1, kills)
            Output.MATTER_PER_KILL -> matter.toDouble() / max(1, kills)
            Output.OVERDRIVE -> overdrive.toDouble()
            Output.DASHES -> dashes.toDouble()
            Output.OFFERS -> offers.toDouble()
            Output.ERASED -> erased.toDouble()
            Output.REFUNDED -> refunded.toDouble()
            Output.CONTROL -> control.toDouble()
            Output.FAST_DISTANCE -> fastDistance.toDouble()
            Output.APEX -> apex.toDouble()
            Output.APEX_RECHARGE -> apexRecharge.toDouble()
            Output.SLAMS -> slams.toDouble()
        }

        operator fun plus(other: Outcome): Outcome = Outcome(
            damage + other.damage, taken + other.taken, integrity + other.integrity, survival + other.survival,
            kills + other.kills, data + other.data, matter + other.matter, overdrive + other.overdrive, dashes + other.dashes,
            offers + other.offers, erased + other.erased, refunded + other.refunded, control + other.control,
            fastDistance + other.fastDistance, apex + other.apex, apexRecharge + other.apexRecharge, slams + other.slams,
        )
    }

    /** The output [owned] improves most over [baseline], as a share of the baseline (never of a near-zero one). */
    private fun bestGain(setup: Setup, baseline: Outcome, owned: Outcome): Pair<String, Float> = setup.outputs().map { output ->
        val before = baseline.value(output)
        val after = owned.value(output)
        output.name.lowercase() to ((after - before) / max(abs(before), FLOORS.getValue(output))).toFloat()
    }.maxBy { it.second }

    /** Fights are flown [variants] times from different seeds and orbit phases and summed, which damps the chaos. */
    private fun run(setup: Setup, item: ItemDefinition?, content: GameplayContentSnapshot = canonicalGameplayContent,
        variants: Int = VARIANTS): Outcome =
        if (setup.arena == Arena.OFFERS) offers(setup, item, content)
        else {
            val arenaContent = if (setup.arena.skills) withInertLoadoutPassives(content, setup.skills) else content
            (0 until variants).map { variant -> fight(setup, item, variant, arenaContent) }.reduce(Outcome::plus)
        }

    /**
     * [content] in which the active items carrying [loadout]'s effects hold [InertLoadoutPassive] instead of their own
     * passive (test-only): a skill arena's loadout casts its effects but brings no passive bonus to either arm.
     */
    private fun withInertLoadoutPassives(content: GameplayContentSnapshot, loadout: List<SkillId>): GameplayContentSnapshot =
        if (loadout.isEmpty()) content else content.copy(items = content.items.map { item ->
            if (item.active != null && item.active in loadout) item.copy(rules = immutableListOf(InertLoadoutPassive)) else item
        }.toImmutableList())

    /** Thirty consecutive item offers at Reborn 10 with every item unlocked: the summed rarity of the cards. */
    private fun offers(setup: Setup, item: ItemDefinition?, content: GameplayContentSnapshot): Outcome {
        val game = MutableGameState(content, seed = SEED, initialMatter = 0, initialRebirthLevel = 10).apply {
            startRun()
            lifetimeMatter = 4_000L
            level = 20
        }
        setup.support.forEach { name -> game.acquireItem(game.itemNamed(name).id) }
        item?.let { game.acquireItem(it.id) }
        var ranks = 0
        repeat(OFFER_COUNT) {
            game.buildItemChoices()
            ranks += game.choices.sumOf { choice -> content.items[requireNotNull(choice.itemId)].rarity.rank }
        }
        return Outcome(0.0, 0.0, 0f, 0f, 0, 0L, 0L, 0f, 0, ranks.toFloat() / OFFER_COUNT + game.rerollsRemaining)
    }

    private fun fight(setup: Setup, item: ItemDefinition?, variant: Int, content: GameplayContentSnapshot): Outcome {
        val arena = setup.arena
        val phase = variant * 0.7f
        val game = MutableGameState(content, seed = SEED + variant, initialMatter = 0).apply {
            coreShape = CoreShape.ORB
            startingWeapon = setup.weapon ?: WeaponId.FLUX_WAKE
            unlockWeaponsForTesting(listOf(startingWeapon))
            resize(1_280f, 720f)
            startRun()
            settings = settings.copy(simulationSpeed = 1f)
            enemies.clear()
            projectiles.clear()
            pickups.clear()
            spawnClock = Float.MAX_VALUE
            nextEliteAt = Float.MAX_VALUE
            runGrace = 0f
            nextPointOfferIndex = Int.MAX_VALUE / 2
        }
        setup.support.forEach { name -> game.own(name, stacks = game.itemNamed(name).maxStacks) }
        val reach = game.effectivePickupRadius() + 30f
        if (arena.skills) setup.skills.forEach { skill -> game.acquireActive(skill, 2) }
        if (arena.apex) game.unlockApex()
        item?.let { game.acquireItem(it.id) }
        if (arena.pressure || arena.shots) {
            // A deep integrity pool: the attackers' damage is measured, never fatal.
            game.maxHp = 10_000f
            game.hp = if (setup.wounded > 0f) 10_000f * setup.wounded else 10_000f
        }
        game.shield = game.shieldCapacity()
        val slots = slotsFor(arena.motion, arena.targets)
        val targets = arrayOfNulls<Enemy>(slots.size)
        val path = PathFor.getValue(arena.motion)
        game.coreX = path.startX
        game.coreY = path.startY
        game.previousCoreX = game.coreX
        game.previousCoreY = game.coreY
        game.cameraX = game.coreX
        game.cameraY = game.coreY
        // The singularity starts ahead of the Core, so its first sweep never crosses it.
        var heading = if (arena.motion == Motion.PASS) 0f else TAU / 4f
        game.updatePointer(game.screenWidth / 2f + cos(heading) * 120f, game.screenHeight / 2f + sin(heading) * 120f)
        game.previousSingularityX = game.coreX + cos(heading) * 120f
        game.previousSingularityY = game.coreY + sin(heading) * 120f
        var dashes = 0
        var overdrive = 0f
        var control = 0f
        var fastDistance = 0f
        var apexSeconds = 0f
        val refunds = RefundLedger()
        // A slam restarts the slammed enemy's slam cooldown: each new cooldown end is one slam.
        val slamReadyAt = HashMap<Int, Float>()
        var slams = 0
        var nearObstacle = false
        val frames = (arena.seconds * FPS).toInt()
        // The frame the Core fell on, or [frames] while it stands.
        var fellAt = frames
        for (frame in 0 until frames) {
            if (game.phase == GamePhase.CHOICE) dismissChoice(game)
            if (game.phase != GamePhase.RUNNING) {
                fellAt = frame
                break
            }
            val t = frame / FPS + phase
            // Anchored, idle targets that come back the moment they fall.
            for (index in slots.indices) {
                val current = targets[index]
                val (x, y) = slots[index]
                // A loose target flies its throw out as a live enemy would (the arena's hold is lifted, so its momentum is
                // not bled off as a stopped enemy's); it is anchored and held again once the throw ends.
                if (setup.loose && current != null && current.alive && current in game.enemies && current.thrownUntil > game.elapsed) {
                    if (current.disabledUntil == Float.MAX_VALUE) current.disabledUntil = 0f
                    continue
                }
                val target = if (current == null || current.dead || current.hp <= 0f || current !in game.enemies) {
                    // Only an instance aspect (an Elite's) can be immune.
                    val type = if (setup.immuneTo != null || setup.elite && index == 0) EnemyType.ELITE else EnemyType.DRIFTER
                    val integrity = when {
                        arena == Arena.CULL && index % 2 == 1 -> STURDY
                        setup.trigger == EffectTrigger.KILL -> minOf(arena.integrity, Arena.CROWD.integrity)
                        else -> arena.integrity
                    }
                    game.addEnemyForTesting(x, y, hp = integrity, type = type).also { spawned ->
                        val immuneTo = setup.immuneTo
                        if (immuneTo != null) assignAspect(spawned, immuneTo.ordinal, immune = true)
                        else if (type == EnemyType.ELITE) assignAspect(spawned, -1, immune = false)
                        targets[index] = spawned
                    }
                } else current
                target.x = x
                target.y = y
                target.previousX = x
                target.previousY = y
                target.vx = 0f
                target.vy = 0f
                target.disabledUntil = Float.MAX_VALUE
            }
            if (setup.weapon == null) game.weaponPower = 0f
            if (arena == Arena.ALCHEMY) {
                game.weaponPower = TAP
                game.damageMultiplier = TAP
            }
            // Level-ups amplify the weapon and open choices: outside their arena they would only add chaos.
            if (arena != Arena.LEVELS) game.nextLevelData = Int.MAX_VALUE / 2
            if (!arena.defended) game.hp = game.maxHp
            heading = if (arena.motion == Motion.PASS) {
                // One-way passes: past the far edge the Core wraps back to the near one with its velocity.
                if (game.coreX > PASS_TURN) {
                    game.coreX -= 2f * PASS_TURN
                    game.previousCoreX = game.coreX
                    game.cameraX -= 2f * PASS_TURN
                    game.previousCameraX = game.cameraX
                    game.previousSingularityX -= 2f * PASS_TURN
                }
                // A pilot who holds about 1,500 u/s instead of flying flat out.
                if (game.speed > PASS_SPEED) {
                    game.velocityX *= PASS_SPEED / game.speed
                    game.velocityY *= PASS_SPEED / game.speed
                }
                val angle = atan2(-game.coreY * 0.6f, PASS_PULL)
                point(game, angle, length(PASS_PULL, game.coreY * 0.6f))
                angle
            } else {
                steer(game, t, path.radius, path.angular, heading)
            }
            val braking = arena.brake && (if (arena.hits > 0f) t % 2f < 1.6f else t % 1.8f in 1.2f..1.7f)
            game.setBrake(braking || setup.trigger == EffectTrigger.BRAKE_RELEASE && t % 1.8f in 1.2f..1.7f)
            // Heat-limited Dashes count only when one is ready; the heat arena presses Dash regardless, to overheat.
            if (arena.dashEvery > 0f && frame % (arena.dashEvery * FPS).toInt() == 0 && game.buildDashReady()) {
                // Dash along the travel direction, into whatever lies ahead. For that one frame the singularity
                // sits beyond the Dash's reach (touching it ends the run); then steering takes the pointer back. A
                // leashed pilot Dashes along its heading instead: a hook or a launch can turn the Core's travel away
                // from the pointer, and a jump to the far side would sweep the singularity across the Core.
                if (setup.leashed) point(game, heading, 480f)
                else if (game.speed > 1f) point(game, atan2(game.velocityY, game.velocityX), 480f)
                game.requestDash()
            }
            if (arena == Arena.CROWD || arena == Arena.SPARSE || arena == Arena.LEVELS || arena == Arena.CULL || arena == Arena.COMBO) {
                // Every drop is collected: Data measures what the kills and the build are worth, not the flight path.
                for (index in game.pickups.indices) {
                    val pickup = game.pickups[index]
                    if (pickup.type == PickupType.DATA) {
                        pickup.x = game.coreX
                        pickup.y = game.coreY
                    }
                }
            }
            if (arena == Arena.COMBO && frame % (3 * FPS).toInt() == 0) {
                // A straggler falls to the weapon every 3 s, a little longer than the base combo window.
                val straggler = game.addEnemyForTesting(game.coreX + 120f, game.coreY, hp = 1f)
                game.damageEnemy(straggler, 2f, killEligible = true, source = HitSource.WEAPON)
                game.onEnemyKilled(straggler)
                game.enemies.remove(straggler)
            }
            if (arena == Arena.REACH && frame % 30 == 0) {
                val angle = frame * 2.399f
                game.addPickup(Pickup(PickupType.DATA, game.coreX + cos(angle) * reach, game.coreY + sin(angle) * reach))
            }
            if (arena.pressure && frame % 30 == 15) {
                var pressed = 0f
                for (enemy in game.enemies) {
                    if (!enemy.alive || game.elapsed < enemy.silencedUntil) continue
                    pressed += game.hostileDamage(enemy, PRESSURE) * game.enemyMotionScale(enemy)
                }
                if (pressed > 0f) game.takeDamage(pressed)
            }
            if (arena.hits > 0f && burstHit(frame)) game.takeDamage(arena.hits)
            if (arena.shots) volley(game, frame)
            if (arena.fields && frame % FIELD_FRAMES == 0) reactBesideCore(game, frame / FIELD_FRAMES)
            if (arena.marks && frame % MARK_FRAMES == 0) markTargets(game)
            if (arena.skills) {
                // Every slot asks to cast each frame: it casts the moment it is ready, through the real buffer.
                game.skillBufferMask = (1 shl SkillRuntime.SLOTS) - 1
                game.skillBufferTime = MutableGameState.SKILL_INPUT_BUFFER_SECONDS
            }
            if (arena.apex && game.apexReady()) game.apexBufferTime = MutableGameState.SKILL_INPUT_BUFFER_SECONDS
            if (arena.forcedOverdrive && frame % (10 * FPS).toInt() == 60 && game.overdriveTime <= 0f) game.overdriveCharge = 100f
            if ((arena.obstacles || setup.trigger == EffectTrigger.RICOCHET || setup.trigger == EffectTrigger.SLAM) && frame % 36 == 0) {
                game.fireClauses(EffectTrigger.RICOCHET)
                val slammed = game.enemies.filter { it.alive }.minByOrNull { distanceSquared(it.x, it.y, game.coreX, game.coreY) }
                if (slammed != null) game.fireClauses(EffectTrigger.SLAM, slammed, hit = 20f)
            }
            if (setup.leashed) leash(game)
            val dashSequence = game.characterRuntime.dashSequence
            game.update(1f / FPS)
            // The scripted paths stay inside the obstacle-free start area: a collision never enters a measurement.
            game.forEachObstacleNear(game.coreX, game.coreY, OBSTACLE_MARGIN) { _, obstacle ->
                if (length(obstacle.x - game.coreX, obstacle.y - game.coreY) < obstacle.radius + game.coreRadius + OBSTACLE_MARGIN) {
                    nearObstacle = true
                }
            }
            dashes += game.characterRuntime.dashSequence - dashSequence
            for (enemy in game.enemies) {
                if (enemy.slamReadyAt > 0f && slamReadyAt.put(enemy.id, enemy.slamReadyAt) != enemy.slamReadyAt) slams++
            }
            refunds.record(game)
            for (enemy in game.enemies) {
                if (enemy.alive && (game.elapsed < enemy.silencedUntil || game.isFrozen(enemy))) control += 1f / FPS
            }
            if (game.speed >= FAST_SPEED) fastDistance += game.speed / FPS
            if (game.apexActive()) apexSeconds += 1f / FPS
            game.takeVisualFxCues()
            game.takeSoundCues()
            game.takeProgressUpdate()
            if (game.overdriveTime > 0f) overdrive += 1f / FPS
        }
        assertFalse(nearObstacle, "${arena} came within $OBSTACLE_MARGIN of an obstacle")
        // A fallen Core keeps nothing and still meets every scripted hit it did not live to take.
        val integrity = if (arena.defended) max(0f, game.hp + game.shield) else 0f
        var taken = if (arena.defended) game.damageTaken else 0.0
        if (arena.hits > 0f) for (frame in fellAt until frames) if (burstHit(frame)) taken += arena.hits
        return Outcome(game.damageDealt, taken, integrity, fellAt / FPS, game.kills, game.dataCollected, game.runMatter, overdrive,
            dashes, 0f, game.shotsErased, refunds.total, control, fastDistance, apexSeconds, apexRecharges(game), slams)
    }

    /** [Output.APEX_RECHARGE] at the end of a fight: recharges finished since the first cast plus the share of the current one. */
    private fun apexRecharges(game: MutableGameState): Float {
        val casts = game.skillRuntime.apex(ApexCell.CAST_COUNT).toInt()
        if (casts == 0) return 0f
        val lastCast = game.skillRuntime.apex(ApexCell.LAST_CAST)
        val readyAt = game.skillRuntime.apex(ApexCell.READY_AT)
        val share = if (readyAt <= game.elapsed || readyAt <= lastCast) 1f else (game.elapsed - lastCast) / (readyAt - lastCast)
        return casts - 1 + share.coerceIn(0f, 1f)
    }

    /** Sums the cooldown seconds refunded to the skills and the Apex from frame to frame (a cast restarts a count). */
    private class RefundLedger {
        private val casts = IntArray(MAX_SLOTS + 1)
        private val refunded = FloatArray(MAX_SLOTS + 1)
        var total = 0f
            private set

        fun record(game: MutableGameState) {
            for (slot in 0 until MAX_SLOTS) {
                track(slot, game.skillRuntime[slot, SkillCell.CAST_COUNT].toInt(), game.skillRuntime[slot, SkillCell.REFUNDED])
            }
            track(MAX_SLOTS, game.skillRuntime.apex(ApexCell.CAST_COUNT).toInt(), game.skillRuntime.apex(ApexCell.REFUNDED))
        }

        private fun track(index: Int, castCount: Int, now: Float) {
            total += if (castCount != casts[index]) now else max(0f, now - refunded[index])
            casts[index] = castCount
            refunded[index] = now
        }
    }

    /**
     * A pilot who never leaves the start area: past [LEASH] from the origin the Core loses the outward part of its
     * velocity (a hook or a launch still carries it along the circle), so no collision enters a measurement.
     */
    private fun leash(game: MutableGameState) {
        val distance = length(game.coreX, game.coreY)
        if (distance <= LEASH) return
        val outward = (game.velocityX * game.coreX + game.velocityY * game.coreY) / distance
        if (outward <= 0f) return
        game.velocityX -= game.coreX / distance * outward
        game.velocityY -= game.coreY / distance * outward
    }

    /**
     * Each live target fires a bolt once a second, aimed where the Core will be when it arrives; every 0.6 s the bolt
     * nearest the Core is erased.
     */
    private fun volley(game: MutableGameState, frame: Int) {
        if (frame % BOLT_FRAMES == 0) {
            for (enemy in game.enemies) {
                if (!enemy.alive) continue
                val lead = length(game.coreX - enemy.x, game.coreY - enemy.y) / BOLT_SPEED
                val angle = atan2(game.coreY + game.velocityY * lead - enemy.y, game.coreX + game.velocityX * lead - enemy.x)
                game.addProjectile(Projectile(enemy.x, enemy.y, cos(angle) * BOLT_SPEED, sin(angle) * BOLT_SPEED, radius = 6f,
                    life = 2.5f, hostile = true, damage = BOLT_DAMAGE))
            }
        }
        if (frame % ERASE_FRAMES == ERASE_FRAMES / 2) {
            val bolt = game.projectiles.filter { it.hostile && !it.spent }.minByOrNull { distanceSquared(it.x, it.y, game.coreX, game.coreY) }
            if (bolt != null) game.eraseHostileShot(bolt, EraseCause.REACTION, bolt.x, bolt.y)
        }
    }

    /**
     * The target nearest the Core takes three stacks of both elements of the next field reaction (Plasma Burst,
     * Silence, Stasis, Null Well in turn) and is made to react, so its field opens on the Core's path.
     */
    private fun reactBesideCore(game: MutableGameState, round: Int) {
        val reaction = FieldReactions[round % FieldReactions.size]
        val target = game.enemies.filter { it.alive }.minByOrNull { distanceSquared(it.x, it.y, game.coreX, game.coreY) } ?: return
        target.status[StatusCell.STACKS + reaction.first.ordinal] = 3f
        target.status[StatusCell.STACKS + reaction.second.ordinal] = 3f
        target.status[StatusCell.REACTION_PAIR_READY_AT + reaction.ordinal] = 0f
        game.forceReaction(target, 0)
    }

    /** Marks every live target again, alternating Spall and Cracked. */
    private fun markTargets(game: MutableGameState) {
        game.enemies.filter { it.alive }.forEachIndexed { index, enemy ->
            game.setMark(enemy, if (index % 2 == 0) MarkKind.SPALL else MarkKind.CRACKED, MARK_SECONDS)
        }
    }

    /** Four hits 0.4 s apart every 6 s: shields recharge between the bursts. */
    private fun burstHit(frame: Int): Boolean {
        val burstFrame = frame % (6 * FPS).toInt()
        return burstFrame in 90..90 + 72 && (burstFrame - 90) % 24 == 0
    }

    /** Where the Core starts and the circle it flies: through the ring, or slowly around a cluster. */
    private class Path(val startX: Float, val startY: Float, val radius: Float, val angular: Float)

    /** Level-ups and pickups may open choices; the encounter declines them so both builds stay equal. */
    private fun dismissChoice(game: MutableGameState) {
        game.choices = emptyList()
        game.pendingLevelChoices = 0
        game.pendingRelicChoices = 0
        game.directedReward = null
        game.phase = GamePhase.RUNNING
    }

    private fun slotsFor(motion: Motion, count: Int): List<Pair<Float, Float>> = (0 until count).map { index ->
        val angle = index * TAU / count
        when (motion) {
            Motion.ORBIT -> cos(angle) * RING to sin(angle) * RING
            Motion.PASS -> 0f to (index - (count - 1) / 2f) * 30f
            Motion.HOVER -> cos(angle) * 60f to sin(angle) * 60f
            Motion.CLOSE_HOVER -> cos(angle) * 40f to sin(angle) * 40f
            Motion.INNER_HOVER -> cos(angle) * 260f to sin(angle) * 260f
        }
    }

    /** A pointer that flies a circle of [radius] around the origin through the real steering field. */
    private fun steer(game: MutableGameState, t: Float, radius: Float, angular: Float, heading: Float): Float =
        steerToward(
            game, heading,
            cos(t * angular) * radius, sin(t * angular) * radius,
            -sin(t * angular) * radius * angular, cos(t * angular) * radius * angular,
            -cos(t * angular) * radius * angular * angular, -sin(t * angular) * radius * angular * angular,
        )

    /**
     * Turns the pointer toward the acceleration that brings the Core to the desired point and velocity. The
     * heading slews a little per frame, so the singularity swings around the Core and never sweeps across it.
     */
    private fun steerToward(
        game: MutableGameState,
        heading: Float,
        desiredX: Float,
        desiredY: Float,
        desiredVx: Float,
        desiredVy: Float,
        feedX: Float,
        feedY: Float,
    ): Float {
        val accelerationX = (desiredX - game.coreX) * 5f + (desiredVx - game.velocityX) * 3f + feedX
        val accelerationY = (desiredY - game.coreY) * 5f + (desiredVy - game.velocityY) * 3f + feedY
        val desiredHeading = atan2(accelerationY, accelerationX)
        val next = heading + atan2(sin(desiredHeading - heading), cos(desiredHeading - heading)).coerceIn(-0.12f, 0.12f)
        // Never closer than 65: the singularity ends the run on contact.
        val distance = ((length(accelerationX, accelerationY) - 92f) / game.magnetStrength).coerceIn(65f, 480f)
        point(game, next, distance)
        return next
    }

    /** Places the pointer [distance] from the Core along [angle], inside the screen's calm area. */
    private fun point(game: MutableGameState, angle: Float, distance: Float) {
        val edgeMargin = (1f - game.content.tempo.fatigue.edgeStrainStart) * 0.5f
        game.updatePointer(
            (game.coreX + cos(angle) * distance - game.cameraX + game.screenWidth / 2f)
                .coerceIn(game.screenWidth * edgeMargin, game.screenWidth * (1f - edgeMargin)),
            (game.coreY + sin(angle) * distance - game.cameraY + game.screenHeight / 2f)
                .coerceIn(game.screenHeight * edgeMargin, game.screenHeight * (1f - edgeMargin)),
        )
    }

    /** The encounters an item's triggers, conditions, stats and rules call for, each with its supporting items. */
    private fun setupsFor(item: ItemDefinition): List<Setup> {
        val arenas = LinkedHashSet<Arena>()
        val conditions = item.clauses.flatMap { it.conditions } +
            item.rules.mapNotNull { rule ->
                when (rule) {
                    is ItemRule.WhileModifier -> rule.condition
                    is ItemRule.AlwaysCrit -> rule.condition
                    is ItemRule.CritDamageVersus -> rule.condition
                    is ItemRule.DamageVersus -> rule.condition
                    else -> null
                }
            }
        item.clauses.forEach { clause ->
            arenas += when (clause.trigger) {
                EffectTrigger.IMPACT -> listOf(Arena.IMPACT, Arena.STATUS)
                EffectTrigger.WEAPON_HIT -> listOf(Arena.WEAPON, Arena.STATUS)
                EffectTrigger.CRIT -> listOf(Arena.WEAPON, Arena.IMPACT)
                EffectTrigger.KILL -> listOf(Arena.CROWD, Arena.CULL)
                EffectTrigger.DASH -> listOf(Arena.IMPACT, Arena.HEAT)
                EffectTrigger.DASH_HIT -> listOf(Arena.IMPACT, Arena.FAST, Arena.STATUS)
                EffectTrigger.BRAKE_RELEASE -> listOf(Arena.BRAKE)
                EffectTrigger.TURN -> listOf(Arena.IMPACT, Arena.STATUS)
                EffectTrigger.HURT, EffectTrigger.SHIELD_BREAK -> listOf(Arena.DEFENSE)
                EffectTrigger.OVERDRIVE -> listOf(Arena.OVERDRIVE)
                EffectTrigger.OVERHEAT -> listOf(Arena.HEAT)
                EffectTrigger.REACTION, EffectTrigger.BREAK -> listOf(Arena.ALCHEMY, Arena.STATUS, Arena.CROWD, Arena.WEAPON)
                EffectTrigger.LEVEL_UP -> listOf(Arena.LEVELS)
                EffectTrigger.PICKUP -> listOf(Arena.CROWD)
                EffectTrigger.RICOCHET, EffectTrigger.SLAM -> listOf(Arena.OBSTACLE, Arena.SHELTER)
                EffectTrigger.SKILL_CAST, EffectTrigger.SKILL_HIT -> listOf(Arena.SKILLS)
                EffectTrigger.APEX -> listOf(Arena.APEX)
                EffectTrigger.FIELD_ENTER -> listOf(Arena.FIELDS)
                EffectTrigger.SHOT_ERASED -> listOf(Arena.SHOTS, Arena.FIELDS)
                EffectTrigger.MARK_CONSUMED -> listOf(Arena.MARKS)
            }
            clause.actions.forEach { action -> arenas += actionArenas(action) }
        }
        item.stats.forEach { stat ->
            arenas += when (stat.effect) {
                ItemEffect.IMPACT_DAMAGE, ItemEffect.MASS, ItemEffect.MAGNETISM, ItemEffect.DASH_POWER -> listOf(Arena.IMPACT, Arena.FAST)
                ItemEffect.WEAPON_POWER, ItemEffect.ATTACK_SPEED -> listOf(Arena.WEAPON)
                ItemEffect.COOLING, ItemEffect.DASH_EFFICIENCY -> listOf(Arena.HEAT)
                ItemEffect.MAX_INTEGRITY, ItemEffect.REGEN, ItemEffect.SHIELD_CAPACITY, ItemEffect.DAMAGE_REDUCTION -> listOf(Arena.DEFENSE)
                ItemEffect.CRIT_CHANCE, ItemEffect.CRIT_DAMAGE -> listOf(Arena.WEAPON, Arena.IMPACT)
                ItemEffect.PICKUP_RADIUS -> listOf(Arena.REACH, Arena.CROWD)
                ItemEffect.DATA_GAIN, ItemEffect.MATTER_GAIN -> listOf(Arena.CROWD)
                ItemEffect.COMBO_WINDOW -> listOf(Arena.COMBO, Arena.SPARSE)
                ItemEffect.LUCK -> listOf(Arena.OFFERS, Arena.CROWD)
                ItemEffect.OVERDRIVE_GAIN -> listOf(Arena.CROWD, Arena.OBSTACLE)
            }
        }
        item.rules.forEach { rule ->
            arenas += when (rule) {
                is ItemRule.WhileModifier -> conditionArenas(rule.condition) + when {
                    rule.stat == BuffStat.DAMAGE_TAKEN -> listOf(Arena.DEFENSE)
                    // Protection under a condition is measured where the condition holds while hits land.
                    rule.stat == BuffStat.DAMAGE_REDUCTION ->
                        listOf(if (rule.condition is EffectCondition.SpeedAtLeast) Arena.RUSH else Arena.DEFENSE)
                    // A skill stat under a condition is measured where skills are cast while the condition holds.
                    rule.stat == BuffStat.SKILL_RECHARGE || rule.stat == BuffStat.SKILL_POWER ->
                        listOf(if (rule.condition is EffectCondition.SpeedAtLeast) Arena.SPRINT else Arena.SKILLS)
                    else -> emptyList()
                }
                is ItemRule.ElementPotency, is ItemRule.StatusDuration, is ItemRule.BreakThreshold, is ItemRule.BreakPower,
                is ItemRule.BreakSpread, is ItemRule.ReactionPower, is ItemRule.ReactionResidue, is ItemRule.ReactionRecoil ->
                    listOf(Arena.ALCHEMY, Arena.STATUS, Arena.CROWD)
                is ItemRule.AlwaysCrit, is ItemRule.CritDamageVersus, is ItemRule.DamageVersus, is ItemRule.NonCriticalDamage,
                is ItemRule.GuaranteedCritAfter -> listOf(Arena.WEAPON, Arena.IMPACT, Arena.STATUS)
                is ItemRule.ComboDamage -> listOf(Arena.CROWD, Arena.COMBO)
                is ItemRule.BonusRerolls, is ItemRule.OfferCards -> listOf(Arena.OFFERS)
                is ItemRule.FreeDash -> listOf(Arena.HEAT)
                is ItemRule.Convert -> when (rule.kind) {
                    ConversionKind.OVERHEAL_TO_SHIELD, ConversionKind.REGENERATION_TO_DECAY, ConversionKind.CRIT_CHANCE_TO_DAMAGE ->
                        listOf(Arena.DEFENSE, Arena.STATUS, Arena.WEAPON)
                    ConversionKind.DASH_HEAT_TO_SKILLS, ConversionKind.DATA_TO_SKILLS, ConversionKind.SKILLS_COST_HEAT,
                    ConversionKind.SKILL_FIELDS_FOLLOW -> listOf(Arena.SKILLS)
                    ConversionKind.KNOCKBACK_TO_DAMAGE -> listOf(Arena.MARKS, Arena.OBSTACLE)
                }
                is ItemRule.ReactionKeystone, is ItemRule.CoreLinkRadius -> listOf(Arena.ALCHEMY, Arena.STATUS, Arena.CROWD, Arena.MARKS)
                is ItemRule.ReactionTuning -> listOf(Arena.ALCHEMY, Arena.STATUS, Arena.CROWD, Arena.FIELDS)
                is ItemRule.FieldTuning -> listOf(Arena.FIELDS, Arena.SKILLS)
                is ItemRule.SkillCooldown, is ItemRule.SkillPower, ItemRule.SkillCharges, is ItemRule.SkillAttunement,
                is ItemRule.CatalysisRate -> listOf(Arena.SKILLS)
                is ItemRule.ApexCooldown -> listOf(Arena.APEX)
            }
        }
        conditions.forEach { arenas += conditionArenas(it) }
        // An active item is measured casting its own effect.
        if (item.isActive) arenas += Arena.SKILLS
        // Slows, freezes and Hollow show as damage not taken from attacking enemies.
        if (StatusElement.CRYO in item.elements || StatusElement.VOID in item.elements) arenas += Arena.SIEGE
        // Immovable frames (an Elite takes 0.35 knockback) are what the knockback conversion needs.
        val elite = conditions.any { it == EffectCondition.TargetElite } ||
            item.rules.any { it is ItemRule.Convert && it.kind == ConversionKind.KNOCKBACK_TO_DAMAGE }
        val support = supportFor(item, conditions)
        // A launch of the Core needs the pilot's leash wherever it is flown.
        val launches = item.clauses.any { clause -> clause.actions.any { it is EffectAction.CoreImpulse } }
        // A family-gated item is flown with every weapon of its family instead of the two arena weapons: it names the
        // family, and the family's weapons act on it differently (a contact weapon's refresh is a damage surge).
        val familyWeapons = conditions.firstNotNullOfOrNull { (it as? EffectCondition.WeaponFamilyIs)?.family }?.let { family ->
            canonicalGameplayContent.weapons.filter { it.family == family }.map { it.id }
        }
        // Against the siege an applier starts from nothing: other sources of its own element would already hold
        // the attackers at the status it adds.
        val applied = item.clauses.flatMap { it.actions }.mapNotNull { (it as? EffectAction.ApplyStatus)?.pick?.element }.toSet()
        val siegeSupport = support.filter { name -> applied.none { element -> name in ElementSources.getValue(element) } }
        // A refresh advances a fired weapon's clocks but surges a continuous one (a damage buff), so a refreshing item is also
        // flown with the starter Flux Wake, where its refresh is the surge.
        val refreshes = item.clauses.any { clause -> clause.actions.any { it is EffectAction.RefreshWeapon } }
        val setups = arenas.flatMap { arena ->
            (if (familyWeapons != null && arena.armed) familyWeapons
            else if (refreshes && arena.armed) arena.weapons() + WeaponId.FLUX_WAKE
            else arena.weapons()).map { weapon ->
                Setup(arena, when (arena) {
                    Arena.OFFERS -> emptyList()
                    Arena.SIEGE -> siegeSupport
                    else -> support
                }, elite, weapon, leashed = arena.vocabulary || launches)
            }
        }
        // A 0.5.0 action is measured in an encounter its trigger may never fire in (a hush planted on a Brake release among
        // shooters, bolts erased off a rock, a maw left by kills): it is flown there again with that trigger scripted in.
        val carried = item.clauses.filter { it.trigger in ScriptedTriggers }.flatMap { clause ->
            clause.actions.filter(::isVocabularyAction).flatMap(::actionArenas).map { it to clause.trigger }
        }.distinct().flatMap { (arena, trigger) ->
            (if (familyWeapons != null && arena.armed) familyWeapons else arena.weapons()).map { weapon ->
                Setup(arena, support, elite, weapon, leashed = arena.vocabulary || launches, trigger = trigger)
            }
        }
        // An IntegrityBelow item is flown wounded below its threshold where the deep pool would otherwise never let it hold.
        val wound = conditions.filterIsInstance<EffectCondition.IntegrityBelow>().minOfOrNull { it.fraction }
        // A thrown target only shows once it may fly: an item that throws (or Spalls, whose next ram throws) is flown with its
        // throws left to play out.
        val throws = item.clauses.any { clause ->
            clause.actions.any { it is EffectAction.Throw || it is EffectAction.ApplyMark && it.mark == MarkPick.SPALL }
        }
        // A skill condition the arena loadout cannot meet (a skill or tag none of its skills has) swaps the last arena skill
        // for the first skill that meets it, so the clause can fire where it is measured.
        val loadout = conditions.fold(ArenaSkills) { skills, condition ->
            val wanted = when (condition) {
                is EffectCondition.SkillIs -> condition.skill.takeIf { it !in skills }
                is EffectCondition.SkillTagged -> SkillId.entries.firstOrNull { condition.tag in canonicalGameplayContent.skill(it).tags }
                    ?.takeIf { skills.none { skill -> condition.tag in canonicalGameplayContent.skill(skill).tags } }
                else -> null
            }
            if (wanted == null) skills else skills.dropLast(1) + wanted
        }.takeIf { !item.isActive } ?: emptyList()
        val flown = (setups + carried).map { setup ->
            val wounded = if (wound != null && setup.arena.vocabulary && (setup.arena.pressure || setup.arena.shots)) wound * 0.8f else 0f
            setup.copy(wounded = wounded, loose = throws, skills = if (setup.arena.skills) loadout else ArenaSkills)
        }
        // A keystone that strips defenses (Crevasse) is also flown against Elites immune to the weapon's own element.
        val armored = if (item.keystone in DefenseBreakingKeystones) {
            listOf(Setup(Arena.STATUS, support, elite, WeaponId.ION_SWARM, leashed = launches, immuneTo = StatusElement.ION))
        } else emptyList()
        return flown + armored
    }

    /** The 0.5.0 actions, whose encounters may need their trigger scripted in. */
    private fun isVocabularyAction(action: EffectAction): Boolean = when (action) {
        is EffectAction.RefundSkills, is EffectAction.ChargeApex, is EffectAction.ForceReaction, is EffectAction.Zone,
        is EffectAction.EraseShots, is EffectAction.CoreImpulse, is EffectAction.ApplyMark, is EffectAction.Throw,
        EffectAction.PrimeDash -> true
        else -> false
    }

    private fun conditionArenas(condition: EffectCondition): List<Arena> = when (condition) {
        is EffectCondition.SpeedAtLeast -> listOf(Arena.FAST, Arena.IMPACT)
        is EffectCondition.SpeedBelow -> listOf(Arena.WEAPON, Arena.BRAKE)
        is EffectCondition.IntegrityBelow -> listOf(Arena.DEFENSE)
        is EffectCondition.HeatAtLeast -> listOf(Arena.HEAT)
        EffectCondition.ShieldUp -> listOf(Arena.DEFENSE)
        EffectCondition.OverdriveActive -> listOf(Arena.OVERDRIVE)
        EffectCondition.Braking -> listOf(Arena.BRAKE, Arena.BRACE)
        is EffectCondition.ComboAtLeast -> listOf(Arena.CROWD, Arena.COMBO)
        is EffectCondition.TargetBelow -> listOf(Arena.CROWD)
        is EffectCondition.SkillIs, is EffectCondition.SkillTagged, is EffectCondition.SkillsCharging -> listOf(Arena.SKILLS)
        EffectCondition.CoreInField -> listOf(Arena.FIELDS)
        EffectCondition.TargetMarked, EffectCondition.TargetThrown -> listOf(Arena.MARKS)
        EffectCondition.TargetControlled -> listOf(Arena.WEAPON, Arena.SIEGE)
        EffectCondition.ApexActive -> listOf(Arena.APEX)
        is EffectCondition.WeaponFamilyIs -> listOf(Arena.WEAPON, Arena.STATUS)
        else -> emptyList()
    }

    /** Encounters an action needs beyond its trigger's: where its effect becomes a measured output. */
    private fun actionArenas(action: EffectAction): List<Arena> = when (action) {
        is EffectAction.ApplyStatus -> listOf(Arena.ALCHEMY)
        is EffectAction.RefundSkills -> listOf(Arena.SKILLS)
        is EffectAction.ChargeApex -> listOf(Arena.APEX, Arena.SKILLS)
        is EffectAction.ForceReaction -> listOf(Arena.ALCHEMY, Arena.STATUS)
        is EffectAction.Zone -> listOf(Arena.FIELDS, Arena.SHOTS, Arena.SIEGE)
        // Bolts fly in both: SHOTS circles its cluster, FIELDS rams through its ring (an impact-cadence weapon hits only there).
        is EffectAction.EraseShots -> listOf(Arena.SHOTS, Arena.FIELDS)
        is EffectAction.CoreImpulse -> listOf(Arena.MARKS, Arena.FIELDS)
        // A Spored mark pays on death (Decay spread and a Data drop): it is measured where marked targets die.
        is EffectAction.ApplyMark -> if (action.mark == MarkPick.SPORED) listOf(Arena.MARKS, Arena.CROWD) else listOf(Arena.MARKS)
        is EffectAction.Throw -> listOf(Arena.MARKS)
        EffectAction.PrimeDash -> listOf(Arena.HEAT, Arena.IMPACT)
        is EffectAction.Buff -> if (action.stat == BuffStat.SKILL_RECHARGE || action.stat == BuffStat.SKILL_POWER) listOf(Arena.SKILLS) else emptyList()
        else -> emptyList()
    }

    /** Items every encounter of [item] holds in both runs: its elements' sources, a crit source, a shield. */
    private fun supportFor(item: ItemDefinition, conditions: List<EffectCondition>): List<String> {
        val elements = LinkedHashSet<StatusElement>(item.elements)
        item.rules.forEach { rule ->
            when (rule) {
                is ItemRule.ElementPotency -> elements += rule.element
                is ItemRule.StatusDuration -> elements += rule.element
                is ItemRule.BreakThreshold -> elements += rule.element
                is ItemRule.BreakPower -> elements += rule.element
                is ItemRule.BreakSpread -> elements += rule.element
                else -> Unit
            }
        }
        conditions.forEach { condition ->
            when (condition) {
                is EffectCondition.TargetHas -> elements += condition.element ?: StatusElement.THERMAL
                is EffectCondition.ElementIs -> elements += condition.element
                EffectCondition.TargetFrozen -> elements += StatusElement.CRYO
                else -> Unit
            }
        }
        val actions = item.clauses.flatMap { it.actions }
        val detonates = actions.filterIsInstance<EffectAction.Detonate>()
        if (detonates.isNotEmpty()) {
            // A detonation needs its first element alone: two of them on one target react away first.
            elements.clear()
            elements += detonates.first().elements.first()
        }
        val reactive = item.rules.any {
            it is ItemRule.ReactionPower || it is ItemRule.ReactionResidue || it is ItemRule.ReactionRecoil ||
                it is ItemRule.ReactionTuning || it is ItemRule.CoreLinkRadius
        } || item.clauses.any { clause -> clause.trigger == EffectTrigger.REACTION || clause.actions.any { it is EffectAction.ForceReaction } }
        // A keystone or a reaction condition needs its own reaction's two elements.
        item.keystone?.let { elements += it.first; elements += it.second }
        conditions.forEach { condition ->
            if (condition is EffectCondition.ReactionIs) {
                elements += condition.reaction.first
                elements += condition.reaction.second
            }
        }
        if (reactive && elements.size < 2) {
            elements += StatusElement.THERMAL
            elements += StatusElement.ENTROPY
        }
        val picks = actions.mapNotNull {
            when (it) {
                is EffectAction.ApplyStatus -> it.pick
                is EffectAction.Chain -> it.pick
                is EffectAction.Shards -> it.pick
                is EffectAction.Well -> it.pick
                else -> null
            }
        }
        val support = ArrayList<String>()
        // Catalyze reacts what carries two elements and Dual Infusion applies the build's two: both get the two sources.
        val twoElementActive = item.active == SkillId.CATALYZE || item.active == SkillId.DUAL_INFUSION
        if (ElementPick.DOMINANT in picks || ElementPick.SECONDARY in picks || twoElementActive) {
            support += "Kindling"
            if (ElementPick.SECONDARY in picks || twoElementActive) support += "Necrotic Lattice"
        }
        elements.forEach { element -> support += ElementSources.getValue(element) }
        val crits = item.clauses.any { it.trigger == EffectTrigger.CRIT } ||
            item.rules.any { it is ItemRule.CritDamageVersus || it is ItemRule.Convert && it.kind == ConversionKind.CRIT_CHANCE_TO_DAMAGE } ||
            item.stats.any { it.effect == ItemEffect.CRIT_DAMAGE }
        if (crits) support += "Focus Lens"
        if (item.rules.any { it is ItemRule.Convert && it.kind == ConversionKind.REGENERATION_TO_DECAY }) support += "Nanite Mesh"
        val shields = actions.any { it is EffectAction.Shield } || item.clauses.any { it.trigger == EffectTrigger.SHIELD_BREAK } ||
            conditions.any { it == EffectCondition.ShieldUp } ||
            item.rules.any { it is ItemRule.Convert && it.kind == ConversionKind.OVERHEAL_TO_SHIELD }
        if (shields && item.stats.none { it.effect == ItemEffect.SHIELD_CAPACITY }) support += "Shield Cell"
        return support.distinct().filter { it != item.name }
    }

    private companion object {
        const val SEED = 90_173
        const val FPS = 60f
        const val SECONDS = 40f
        /** The sturdy neighbors in the cull arena. */
        const val STURDY = 1_500f
        /** Damage each siege attacker presses every half second at full strength. */
        const val PRESSURE = 0.6f
        const val RING = 220f
        const val PASS_TURN = 520f
        const val PASS_PULL = 240f
        const val PASS_SPEED = 1_500f
        /** The clearance every scripted path keeps from obstacles (the run origin's safe area holds them all). */
        const val OBSTACLE_MARGIN = 60f

        val PathFor: Map<Motion, Path> = mapOf(
            Motion.ORBIT to Path(RING, 0f, RING, 2.9f),
            Motion.PASS to Path(-PASS_TURN, 0f, 0f, 0f),
            Motion.HOVER to Path(330f, 0f, 330f, 0.8f),
            Motion.CLOSE_HOVER to Path(170f, 0f, 170f, 1.2f),
            Motion.INNER_HOVER to Path(90f, 0f, 90f, 0.8f),
        )
        const val OFFER_COUNT = 30
        const val MAX_SLOTS = 3
        /** The vocabulary encounters' cadences, in 60 fps frames: a volley, an erasure, a forced field, a re-mark. */
        const val BOLT_FRAMES = 60
        const val ERASE_FRAMES = 36
        const val FIELD_FRAMES = 72
        const val MARK_FRAMES = 120
        const val BOLT_SPEED = 380f
        const val BOLT_DAMAGE = 1f
        const val MARK_SECONDS = 2.5f
        const val FAST_SPEED = 900f
        /** How far from the origin the vocabulary encounters let the Core fly (the start area is obstacle-free to 900). */
        const val LEASH = 560f
        const val VARIANTS = 5
        /** Weapon power and impact power in the alchemy arena: hits that land, with almost no damage of their own. */
        const val TAP = 0.02f
        const val MIN_GAIN = 0.05f
        /** A best gain this close to [MIN_GAIN] (either side) is confirmed over [CONFIRM_VARIANTS] fights. */
        const val CONFIRM_BAND = 0.025f
        const val CONFIRM_VARIANTS = 15

        /** The smallest baseline a gain is measured against, so a handful of kills is never "+200 %". */
        val FLOORS: Map<Output, Double> = mapOf(
            Output.DAMAGE to 200.0, Output.TAKEN to 10.0, Output.INTEGRITY to 10.0, Output.SURVIVAL to 10.0, Output.KILLS to 10.0,
            Output.DATA to 30.0,
            Output.MATTER to 30.0, Output.DATA_PER_KILL to 1.0, Output.MATTER_PER_KILL to 1.0, Output.OVERDRIVE to 3.0,
            Output.DASHES to 10.0, Output.OFFERS to 1.0, Output.ERASED to 10.0, Output.REFUNDED to 2.0, Output.CONTROL to 5.0,
            Output.FAST_DISTANCE to 500.0, Output.APEX to 5.0, Output.APEX_RECHARGE to 0.5, Output.SLAMS to 10.0,
        )

        /** The SKILLS arena's loadout (each at rank II). */
        val ArenaSkills: List<SkillId> = listOf(SkillId.SOLAR_FLARE, SkillId.ARC_HOOK, SkillId.CATALYZE)

        /**
         * The passive a skill arena's loadout actives hold instead of their own ([withInertLoadoutPassives]): 0.01 % less
         * damage taken while braking, which none of the skill arenas' outputs (damage, refunds, kills, Data, Dashes)
         * measures.
         */
        val InertLoadoutPassive: ItemRule = ItemRule.WhileModifier(EffectCondition.Braking, BuffStat.DAMAGE_TAKEN, -0.0001f)

        /** The reactions the FIELDS arena forces beside the Core, in turn: a pool, a hush, a bubble, a maw. */
        val FieldReactions: List<ReactionId> = listOf(ReactionId.PLASMA_BURST, ReactionId.SILENCE, ReactionId.STASIS, ReactionId.NULL_WELL)

        /** Two sources of each element: the first on weapon hits, the second on impacts (or a Dash or crit where none exists). */
        val ElementSources: Map<StatusElement, List<String>> = mapOf(
            StatusElement.THERMAL to listOf("Incendiary Rounds", "Igniter"),
            StatusElement.CRYO to listOf("Rime Rounds", "Cold Snap"),
            StatusElement.ION to listOf("Arc Relay", "Static Brush"),
            StatusElement.GRAVITIC to listOf("Graviton Seed", "Lodestone"),
            StatusElement.ENTROPY to listOf("Rust Spores", "Corrosive Touch"),
            StatusElement.SONIC to listOf("Subwoofer Rounds", "Tuning Spike"),
            // Hollow Point needs crits to add its Void; Focus Lens supplies them.
            StatusElement.VOID to listOf("Null Rounds", "Hollow Point", "Focus Lens"),
        )

        /**
         * The item ids [everyItemAtOneStackMovesAMeasuredOutputByAtLeastFivePercent] judges: the whole catalog, or the
         * `KK_IMPACT_RANGE` environment variable's `first..last` so CI can shard the catalog (for example `0..172`,
         * `173..268` and the active items `269..286`). A malformed value fails the test rather than silently judging nothing.
         */
        val ImpactRange: IntRange = System.getenv("KK_IMPACT_RANGE")?.let { value ->
            val bounds = value.split("..").map { it.trim().toIntOrNull() }
            val first = bounds.getOrNull(0)
            val last = bounds.getOrNull(1)
            require(bounds.size == 2 && first != null && last != null && first in 0..last) { "KK_IMPACT_RANGE '$value' is not first..last" }
            first..last
        } ?: 0..Int.MAX_VALUE

        /** `KK_IMPACT_SETUPS` set: every setup's best gain is printed, not only the item's best (for retuning a miss). */
        val ImpactEverySetup: Boolean = System.getenv("KK_IMPACT_SETUPS") != null

        /**
         * Keystones whose form strips enemy defenses (Crevasse: no aspect immunity, resistance or plates), which only
         * defended targets can show.
         */
        val DefenseBreakingKeystones: Set<ReactionId> = setOf(ReactionId.FRACTURE)

        /** Triggers a setup can script into an encounter whose own script never fires them ([Setup.trigger]). */
        val ScriptedTriggers: Set<EffectTrigger> =
            setOf(EffectTrigger.BRAKE_RELEASE, EffectTrigger.RICOCHET, EffectTrigger.SLAM, EffectTrigger.KILL)

        /** Items whose effect no scripted encounter can show, by name, with the reason. None today. */
        val JUSTIFIED_EXCEPTIONS: Map<String, String> = emptyMap()

        /** Vocabulary kinds [everyNewVocabularyKindMovesItsEncounter] holds no probe for, with the reason. */
        val UNPROBED_VOCABULARY: Map<String, String> = mapOf(
            "SkillAttunement" to "the skill arena's own elements already react; changing them is a trade, not a gain",
            "CoreLinkRadius" to "every scripted reaction already lands within the Core link",
            "SKILL_FIELDS_FOLLOW" to "the skill arena's loadout makes no skill field",
        )
    }
}

/** The weapons each armed arena is flown with: an Ion Swarm volley and a Sentry Grid (a persistent source). */
private val ARENA_WEAPONS: List<WeaponId?> = listOf(WeaponId.ION_SWARM, WeaponId.SENTRY_GRID)
